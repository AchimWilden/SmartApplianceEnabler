/*
 * Copyright (C) 2019 Axel Müller <axel.mueller@avanux.de>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package de.avanux.smartapplianceenabler.integration;

import de.avanux.smartapplianceenabler.TestBase;
import de.avanux.smartapplianceenabler.appliance.Appliance;
import de.avanux.smartapplianceenabler.appliance.ApplianceBuilder;
import de.avanux.smartapplianceenabler.control.ev.EVChargerControl;
import de.avanux.smartapplianceenabler.control.ev.ElectricVehicleCharger;
import de.avanux.smartapplianceenabler.control.ev.SocScript;
import de.avanux.smartapplianceenabler.meter.Meter;
import de.avanux.smartapplianceenabler.meter.MockElectricityMeter;
import de.avanux.smartapplianceenabler.meter.PollEnergyExecutor;
import de.avanux.smartapplianceenabler.schedule.OptionalEnergySocRequest;
import de.avanux.smartapplianceenabler.schedule.TimeframeInterval;
import de.avanux.smartapplianceenabler.schedule.TimeframeIntervalHandler;
import de.avanux.smartapplianceenabler.schedule.TimeframeIntervalState;
import de.avanux.smartapplianceenabler.util.DateTimeProvider;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;

public class EVChargerTest extends TestBase {

    private Logger logger = LoggerFactory.getLogger(EVChargerTest.class);
    private DateTimeProvider dateTimeProvider = Mockito.mock(DateTimeProvider.class);
    private EVChargerControl evChargerControl = Mockito.mock(EVChargerControl.class);
    private Meter meter = Mockito.mock(Meter.class);
    private MockElectricityMeter mockMeter = Mockito.spy(new MockElectricityMeter());
    private PollEnergyExecutor pollEnergyExecutor = Mockito.mock(PollEnergyExecutor.class);
    private SocScript socScript = Mockito.mock(SocScript.class);
    private String applianceId = "F-001";
    private Integer evId = 1;
    private Integer batteryCapacity = 40000;

    @Test
    public void optionalEnergyRequest() {
        LocalDateTime timeInitial = toToday(9, 50, 0);

        mockMeter.setApplianceId(applianceId);
        mockMeter.getPollEnergyMeter().setPollEnergyExecutor(pollEnergyExecutor);

        Appliance appliance = new ApplianceBuilder(applianceId)
                .withEvCharger(evChargerControl)
                .withElectricVehicle(evId, batteryCapacity)
                .withMeter(mockMeter)
                .build(true);
        TimeframeIntervalHandler timeframeIntervalHandler = appliance.getTimeframeIntervalHandler();
        ElectricVehicleCharger evCharger = (ElectricVehicleCharger) appliance.getControl();

        log("Vehicle not connected");
        tick(appliance, timeInitial, false, false);
        assertEquals(0, timeframeIntervalHandler.getQueue().size());

        log("Vehicle connected");
        LocalDateTime timeVehicleConnected = toToday(9, 55, 0);
        Interval interval = new Interval(timeVehicleConnected.toDateTime(),
                timeVehicleConnected.plusDays(TimeframeIntervalHandler.CONSIDERATION_INTERVAL_DAYS).toDateTime());
        Mockito.when(pollEnergyExecutor.pollEnergy(Mockito.any())).thenReturn(10.0f);
        tick(appliance, timeVehicleConnected, true, false);
        assertEquals(1, timeframeIntervalHandler.getQueue().size());
        assertTimeframeIntervalOptionalEnergy(interval, TimeframeIntervalState.ACTIVE,
                0, evId, 44000, true, timeframeIntervalHandler.getQueue().get(0));

        log("Start charging");
        LocalDateTime timeStartCharging = toToday(10, 0, 0);
        appliance.setApplianceState(timeStartCharging,
                true, 4000, "Switch on");
        tick(appliance, timeStartCharging, true, true);
        assertEquals(1, timeframeIntervalHandler.getQueue().size());
        assertTimeframeIntervalOptionalEnergy(interval, TimeframeIntervalState.ACTIVE,
                0, evId, 44000, true, timeframeIntervalHandler.getQueue().get(0));
        Mockito.verify(mockMeter).startEnergyMeter();
        assertEquals(0.0f, mockMeter.getEnergy(), 0.01);

        log("After start charging");
        LocalDateTime timeAfterStartCharging = toToday(11, 0, 0);
        Mockito.when(pollEnergyExecutor.pollEnergy(Mockito.any())).thenReturn(14.0f);
        tick(appliance, timeAfterStartCharging, true, true);
        assertTimeframeIntervalOptionalEnergy(interval, TimeframeIntervalState.ACTIVE,
                0, evId, 40000, true, timeframeIntervalHandler.getQueue().get(0));

        log("After interrupt charging");
        LocalDateTime timeInterruptCharging = toToday(12, 0, 0);
        Mockito.when(pollEnergyExecutor.pollEnergy(Mockito.any())).thenReturn(18.0f);
        appliance.setApplianceState(timeInterruptCharging,
                false, null, "Switch off");
        tick(appliance, timeInterruptCharging,  true, false);
        assertEquals(1, timeframeIntervalHandler.getQueue().size());
        assertTimeframeIntervalOptionalEnergy(interval, TimeframeIntervalState.ACTIVE,
                0, evId, 36000, true, timeframeIntervalHandler.getQueue().get(0));
        Mockito.verify(mockMeter).stopEnergyMeter();
        Mockito.verify(mockMeter, never()).resetEnergyMeter();
        assertEquals(8.0f, mockMeter.getEnergy(), 0.01);

        log("Start charging again");
        LocalDateTime timeStartChargingAgain = toToday(13, 0, 0);
        appliance.setApplianceState(timeStartChargingAgain,
                true, 6000, "Switch on");
        tick(appliance, timeStartChargingAgain, true, true);
        assertEquals(1, timeframeIntervalHandler.getQueue().size());
        assertTimeframeIntervalOptionalEnergy(interval, TimeframeIntervalState.ACTIVE,
                0, evId, 36000, true, timeframeIntervalHandler.getQueue().get(0));
        assertEquals(8.0f, mockMeter.getEnergy(), 0.01);

        log("After start charging again");
        LocalDateTime timeAfterStartChargingAgain = toToday(14, 0, 0);
        Mockito.when(pollEnergyExecutor.pollEnergy(Mockito.any())).thenReturn(24.0f);
        tick(appliance, timeAfterStartChargingAgain, true, true);
        assertEquals(1, timeframeIntervalHandler.getQueue().size());
        assertTimeframeIntervalOptionalEnergy(interval, TimeframeIntervalState.ACTIVE,
                0, evId, 30000, true, timeframeIntervalHandler.getQueue().get(0));
        assertEquals(14.0f, mockMeter.getEnergy(), 0.01);

        log("Charging completed");
        LocalDateTime timeManualStartChargingCompleted = toToday(15, 0, 0);
        Mockito.when(pollEnergyExecutor.pollEnergy(Mockito.any())).thenReturn(30.0f);
        tick(appliance, timeManualStartChargingCompleted, true, false);
        assertTrue(evCharger.isChargingCompleted());
        assertEquals(1, timeframeIntervalHandler.getQueue().size());
        assertTimeframeIntervalOptionalEnergy(interval, TimeframeIntervalState.ACTIVE,
                0, evId, 24000, true, timeframeIntervalHandler.getQueue().get(0));
        assertEquals(20.0f, mockMeter.getEnergy(), 0.01);
    }

    @Test
    public void optionalEnergyRequest_SocScript_MaxSocGTInitialSoc() {
        LocalDateTime timeInitial = toToday(9, 50, 0);
        Appliance appliance = new ApplianceBuilder(applianceId)
                .withEvCharger(evChargerControl)
                .withElectricVehicle(evId, batteryCapacity, 80, socScript)
                .withMeter(meter)
                .build(true);
        TimeframeIntervalHandler timeframeIntervalHandler = appliance.getTimeframeIntervalHandler();

        log("Vehicle not connected");
        tick(appliance, timeInitial, false, false);
        assertEquals(0, timeframeIntervalHandler.getQueue().size());

        log("Vehicle connected");
        LocalDateTime timeVehicleConnected = toToday(9, 55, 0);
        Interval interval = new Interval(timeVehicleConnected.toDateTime(),
                timeVehicleConnected.plusDays(TimeframeIntervalHandler.CONSIDERATION_INTERVAL_DAYS).toDateTime());
        Mockito.when(socScript.getStateOfCharge()).thenReturn(70.0f);
        tick(appliance, timeVehicleConnected, true, false);
        assertEquals(1, timeframeIntervalHandler.getQueue().size());
        assertTimeframeIntervalOptionalEnergy(interval, TimeframeIntervalState.ACTIVE,
                0, evId, 13200, true, timeframeIntervalHandler.getQueue().get(0));
    }

    @Test
    public void optionalEnergyRequest_SocScript_MaxSocLTInitialSoc() {
        LocalDateTime timeInitial = toToday(9, 50, 0);
        Appliance appliance = new ApplianceBuilder(applianceId)
                .withEvCharger(evChargerControl)
                .withElectricVehicle(evId, batteryCapacity, 80, socScript)
                .withMeter(meter)
                .build(true);
        TimeframeIntervalHandler timeframeIntervalHandler = appliance.getTimeframeIntervalHandler();

        log("Vehicle not connected");
        tick(appliance, timeInitial, false, false);
        assertEquals(0, timeframeIntervalHandler.getQueue().size());

        log("Vehicle connected");
        LocalDateTime timeVehicleConnected = toToday(9, 55, 0);
        Mockito.when(socScript.getStateOfCharge()).thenReturn(100.0f);
        tick(appliance, timeVehicleConnected, true, false);
        assertEquals(0, timeframeIntervalHandler.getQueue().size());
    }

    @Test
    public void daytimeframeSocRequest() {
        int socRequested = 60;
        mockMeter.setApplianceId(applianceId);
        mockMeter.getPollEnergyMeter().setPollEnergyExecutor(pollEnergyExecutor);

        Interval interval = new Interval(toToday(10, 0, 0).toDateTime(),
                toToday(16, 0, 0).toDateTime());
        LocalDateTime timeInitial = toToday(9, 50, 0);
        Appliance appliance = new ApplianceBuilder(applianceId)
                .withEvCharger(evChargerControl)
                .withElectricVehicle(evId, batteryCapacity, null, socScript)
                .withMeter(mockMeter)
                .withSocRequest(timeInitial, interval, evId, socRequested, true)
                .build(false);
        TimeframeIntervalHandler timeframeIntervalHandler = appliance.getTimeframeIntervalHandler();
        ElectricVehicleCharger evCharger = (ElectricVehicleCharger) appliance.getControl();

        log("Vehicle not connected");
        tick(appliance, timeInitial, false, false);
        assertTimeframeIntervalSocRequest(TimeframeIntervalState.QUEUED, interval,
                0, socRequested, evId, 26400, true, timeframeIntervalHandler.getQueue().get(0));

        log("After vehicle has been connected");
        LocalDateTime timeVehicleConnected = toToday(9, 55, 0);
        Interval optionalEnergyInterval = new Interval(timeVehicleConnected.toDateTime(),
                toToday(9, 59, 59).toDateTime());
        Mockito.when(evChargerControl.isVehicleConnected()).thenReturn(true);
        Mockito.when(socScript.getStateOfCharge()).thenReturn(50.0f);
        evCharger.updateState(timeVehicleConnected);
        tick(appliance, timeInitial, true, false);
        assertTimeframeIntervalOptionalEnergy(optionalEnergyInterval, TimeframeIntervalState.ACTIVE,
                0, evId, 22000, true, timeframeIntervalHandler.getQueue().get(0));
        assertTimeframeIntervalSocRequest(TimeframeIntervalState.QUEUED, interval,
                0, socRequested, evId, 4400, true, timeframeIntervalHandler.getQueue().get(1));

        log("Start charging");
        LocalDateTime timeStartCharging = toToday(10, 0, 0);
        optionalEnergyInterval = new Interval(timeStartCharging.toDateTime(),
                timeStartCharging.toDateTime().plusDays(2).toDateTime());
        tick(appliance, timeInitial, true, true);
        appliance.setApplianceState(timeStartCharging,
                true, 4000, "Switch on");
        tick(appliance, timeStartCharging, true, true);
        assertEquals(2, timeframeIntervalHandler.getQueue().size());
        assertTimeframeIntervalSocRequest(TimeframeIntervalState.ACTIVE, interval,
                0, socRequested, evId, 4400, true, timeframeIntervalHandler.getQueue().get(0));
        assertTimeframeIntervalOptionalEnergy(optionalEnergyInterval, TimeframeIntervalState.QUEUED,
                50, evId, 22000, true, timeframeIntervalHandler.getQueue().get(1));

        log("Requested SOC reached");
        LocalDateTime timeSOCReached = toToday(11, 0, 0);
        tick(appliance, timeSOCReached, true, true, 4.4f);
        assertEquals(1, timeframeIntervalHandler.getQueue().size());
        assertTimeframeIntervalOptionalEnergy(optionalEnergyInterval, TimeframeIntervalState.ACTIVE,
                50, evId, 17600, true, timeframeIntervalHandler.getQueue().get(0));
    }

    @Test
    public void daytimeframeSocRequest_SocScript_RequestedSocGTInitialSoc() {
        int socRequested = 60;
        int socInitial = 50;
        mockMeter.setApplianceId(applianceId);
        mockMeter.getPollEnergyMeter().setPollEnergyExecutor(pollEnergyExecutor);

        Interval interval = new Interval(toToday(10, 0, 0).toDateTime(),
                toToday(16, 0, 0).toDateTime());
        LocalDateTime timeInitial = toToday(11, 0, 0);
        Appliance appliance = new ApplianceBuilder(applianceId)
                .withEvCharger(evChargerControl)
                .withElectricVehicle(evId, batteryCapacity, null, socScript)
                .withMeter(mockMeter)
                .withSocRequest(timeInitial, interval, evId, socRequested, true)
                .build(false);
        TimeframeIntervalHandler timeframeIntervalHandler = appliance.getTimeframeIntervalHandler();
        ElectricVehicleCharger evCharger = (ElectricVehicleCharger) appliance.getControl();

        log("Vehicle not connected");
        tick(appliance, timeInitial, false, false);
        assertTimeframeIntervalSocRequest(TimeframeIntervalState.ACTIVE, interval,
                0, socRequested, evId, 26400, true, timeframeIntervalHandler.getQueue().get(0));

        log("After vehicle has been connected");
        LocalDateTime timeVehicleConnected = toToday(9, 55, 0);
        Interval optionalEnergyInterval = new Interval(timeVehicleConnected.toDateTime(),
                toToday(9, 59, 59).toDateTime());
        Mockito.when(evChargerControl.isVehicleConnected()).thenReturn(true);
        Mockito.when(socScript.getStateOfCharge()).thenReturn(Integer.valueOf(socInitial).floatValue());
        evCharger.updateState(timeVehicleConnected);
        tick(appliance, timeInitial, true, false);
        assertEquals(1, timeframeIntervalHandler.getQueue().size());
        assertTimeframeIntervalSocRequest(TimeframeIntervalState.ACTIVE, interval,
                socInitial, socRequested, evId, 4400, true, timeframeIntervalHandler.getQueue().get(0));
    }

//    @Test
//    public void daytimeframeSocRequest_SocScript_RequestedSocLTInitialSoc() {
//        mockMeter.setApplianceId(applianceId);
//        mockMeter.getPollEnergyMeter().setPollEnergyExecutor(pollEnergyExecutor);
//
//        LocalDateTime timeInitial = toToday(9, 50, 0);
//        TestBuilder builder = new TestBuilder()
//                .appliance(applianceId, dateTimeProvider, timeInitial)
//                .withEvCharger(evChargerControl)
//                .withElectricVehicle(evId, batteryCapacity, null, socScript)
//                .withMeter(mockMeter)
//                .withSchedule(10,0, 16, 0)
//                .withSocRequest(evId, 50)
//                .init();
//        Appliance appliance = builder.getAppliance();
//        ElectricVehicleCharger evCharger = (ElectricVehicleCharger) appliance.getControl();
//
//        log("Vehicle not connected");
//        List<RuntimeInterval> runtimeIntervalsNotConnected = appliance.getRuntimeIntervals(timeInitial, false);
//        assertEquals(0, runtimeIntervalsNotConnected.size());
//
//        log("After connected");
//        LocalDateTime timeVehicleConnected = toToday(9, 55, 0);
//        Mockito.when(socScript.getStateOfCharge()).thenReturn(72.7f);
//        evCharger.updateState(timeVehicleConnected);
//        List<RuntimeInterval> runtimeIntervalsConnected = appliance.getRuntimeIntervals(timeVehicleConnected, false);
//        assertEquals(0, runtimeIntervalsConnected.size());
//
//        log("Start charging");
//        LocalDateTime timeStartCharging = toToday(10, 0, 0);
//        appliance.setApplianceState(timeStartCharging,
//                true, 4000, "Switch on");
//        tick(appliance, timeInitial, true, true);
//        List<RuntimeInterval> runtimeIntervalsStartCharging = appliance.getRuntimeIntervals(timeStartCharging, false);
//        assertEquals(
//                Arrays.asList(
//                        new RuntimeInterval(0, 21600, 22000, 22000, true),
//                        new RuntimeInterval(86400, 108000, 22000, 22000, true)
//                ),
//                runtimeIntervalsStartCharging);
//        Mockito.verify(mockMeter).startEnergyMeter();
//        assertEquals(0.0f, mockMeter.getEnergy(), 0.01);
//
//        log("After start charging");
//        LocalDateTime timeAfterStartCharging = toToday(11, 0, 0);
//        Mockito.when(pollEnergyExecutor.pollEnergy(Mockito.any())).thenReturn(14.0f);
//        tick(appliance, timeInitial, true, true);
//        List<RuntimeInterval> runtimeIntervalsAfterStartCharging = appliance.getRuntimeIntervals(timeAfterStartCharging, false);
//        assertEquals(
//                Arrays.asList(
//                        new RuntimeInterval(0, 18000, 8000, 8000, true),
//                        new RuntimeInterval(82800, 104400, 22000, 22000, true),
//                        new RuntimeInterval(169200, 190800, 22000, 22000, true)
//                ),
//                runtimeIntervalsAfterStartCharging);
//
//        log("After interrupt charging");
//        LocalDateTime timeInterruptCharging = toToday(12, 0, 0);
//        Mockito.reset(mockMeter);
//        Mockito.when(pollEnergyExecutor.pollEnergy(Mockito.any())).thenReturn(18.0f);
//        appliance.setApplianceState(timeInterruptCharging,
//                false, null, "Switch off");
//        tick(appliance, timeInitial,  true, false);
//        List<RuntimeInterval> runtimeIntervalsAfterInterrupCharging = appliance.getRuntimeIntervals(timeAfterStartCharging, false);
//        assertEquals(
//                Arrays.asList(
//                        new RuntimeInterval(0, 18000, 4000, 4000, true),
//                        new RuntimeInterval(82800, 104400, 22000, 22000, true),
//                        new RuntimeInterval(169200, 190800, 22000, 22000, true)
//                ),
//                runtimeIntervalsAfterInterrupCharging);
//        Mockito.verify(mockMeter).stopEnergyMeter();
//        Mockito.verify(mockMeter, never()).resetEnergyMeter();
//        assertEquals(18.0f, mockMeter.getEnergy(), 0.01);
//    }
//
//    @Test
//    public void manualStartFollowedByDaytimeframeRuntimeRequest() {
//        LocalDateTime timeInitial = toToday(9, 50, 0);
//        TestBuilder builder = new TestBuilder()
//                .appliance(applianceId, dateTimeProvider, timeInitial)
//                .withEvCharger(evChargerControl)
//                .withElectricVehicle(evId, batteryCapacity)
//                .withMockMeter()
//                .withSchedule(16,0, 22, 0)
//                .withRuntimeRequest(5000, 5000)
//                .init();
//        Appliance appliance = builder.getAppliance();
//        ElectricVehicleCharger evCharger = (ElectricVehicleCharger) appliance.getControl();
//        RunningTimeMonitor runningTimeMonitor = appliance.getRunningTimeMonitor();
//
//        log("Vehicle not connected");
//        List<RuntimeInterval> runtimeIntervalsNotConnected = appliance.getRuntimeIntervals(timeInitial, false);
//        assertEquals(0, runtimeIntervalsNotConnected.size());
//
//        log("Vehicle connected");
//        LocalDateTime timeVehicleConnected = toToday(10, 0, 0);
//        tick(appliance, timeVehicleConnected, true, false);
//        List<RuntimeInterval> runtimeIntervalsConnected = appliance.getRuntimeIntervals(timeVehicleConnected, false);
//        assertEquals(
//                Arrays.asList(
//                        new RuntimeInterval(0, 21599, 0, 44000, true),
//                        new RuntimeInterval(21600, 43200, 5000, 5000),
//                        new RuntimeInterval(108000, 129600, 5000, 5000)
//                ),
//                runtimeIntervalsConnected);
//
//        log("Manual start");
//        LocalDateTime timeManualStart = toToday(11, 0, 0);
//        appliance.setEnergyDemand(timeManualStart, evId, 40, 50, null);
//        tick(appliance, timeManualStart, true, true);
//        LocalDateTime timeAfterManualStart = toToday(12, 0, 0);
//        tick(appliance, timeAfterManualStart, true, true);
//        List<RuntimeInterval> runtimeIntervalsManualStart = appliance.getRuntimeIntervals(timeAfterManualStart, false);
//        assertEquals(
//                Arrays.asList(
//                        new RuntimeInterval(0, 12240, 4400, 4400, true),
//                        new RuntimeInterval(14400, 36000, 5000, 5000),
//                        new RuntimeInterval(100800, 122400, 5000, 5000)
//                ),
//                runtimeIntervalsManualStart);
//
//        log("Manual start - charging completed");
//        LocalDateTime timeManualStartChargingCompleted = toToday(13, 0, 0);
//        tick(appliance, timeManualStartChargingCompleted, true, false);
//        assertTrue(evCharger.isChargingCompleted());
//        List<RuntimeInterval> runtimeIntervalsManualStartChargingCompleted
//                = appliance.getRuntimeIntervals(timeManualStartChargingCompleted, false);
//        assertEquals(0, runtimeIntervalsManualStartChargingCompleted.size());
//    }

    private void tick(Appliance appliance, LocalDateTime now, boolean connected, boolean charging) {
        tick(appliance, now, connected, charging, null);
    }

    private void tick(Appliance appliance, LocalDateTime now,
                      boolean connected, boolean charging, Float energyMetered) {
        Mockito.when(dateTimeProvider.now()).thenReturn(now);
        Mockito.when(evChargerControl.isVehicleConnected()).thenReturn(connected);
        Mockito.when(evChargerControl.isCharging()).thenReturn(charging);
        if(energyMetered != null) {
            Mockito.doReturn(energyMetered).when(mockMeter).getEnergy();
        }

        ElectricVehicleCharger evCharger = (ElectricVehicleCharger) appliance.getControl();
        evCharger.updateState(now);

        TimeframeIntervalHandler timeframeIntervalHandler = appliance.getTimeframeIntervalHandler();
        timeframeIntervalHandler.updateQueue(now);
    }

    private void log(String message) {
        logger.debug("*********** " + message);
    }
}
