/*
 * Copyright (C) 2017 Axel Müller <axel.mueller@avanux.de>
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

package de.avanux.smartapplianceenabler.control;

import de.avanux.smartapplianceenabler.appliance.ApplianceIdConsumer;
import de.avanux.smartapplianceenabler.notification.NotificationHandler;
import de.avanux.smartapplianceenabler.notification.NotificationProvider;
import de.avanux.smartapplianceenabler.notification.Notifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import java.time.LocalDateTime;

import java.util.Timer;

/**
 * A switch which is always switched on.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class AlwaysOnSwitch implements Control, ApplianceIdConsumer, NotificationProvider {

    private transient Logger logger = LoggerFactory.getLogger(AlwaysOnSwitch.class);
    @XmlElement(name = "Notifications")
    private Notifications notifications;
    private transient String applianceId;
    private transient NotificationHandler notificationHandler;

    @Override
    public void setApplianceId(String applianceId) {
        this.applianceId = applianceId;
    }

    @Override
    public void setNotificationHandler(NotificationHandler notificationHandler) {
        this.notificationHandler = notificationHandler;
        if(this.notificationHandler != null) {
            this.notificationHandler.addRequestedNotifications(notifications);
        }
    }

    @Override
    public Notifications getNotifications() {
        return notifications;
    }

    @Override
    public void init() {
    }

    @Override
    public void start(LocalDateTime now, Timer timer) {
    }

    @Override
    public void stop(LocalDateTime now) {
    }

    @Override
    public boolean on(LocalDateTime now, boolean switchOn) {
        return true;
    }

    @Override
    public boolean isOn() {
        return true;
    }

    @Override
    public boolean isControllable() {
        return false;
    }

    @Override
    public void addControlStateChangedListener(ControlStateChangedListener listener) {
    }

    @Override
    public void removeControlStateChangedListener(ControlStateChangedListener listener) {
    }
}
