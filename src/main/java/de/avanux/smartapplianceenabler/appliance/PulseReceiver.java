/*
 * Copyright (C) 2015 Axel Müller <axel.mueller@avanux.de>
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
package de.avanux.smartapplianceenabler.appliance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashMap;
import java.util.Map;

/**
 * A PulseReceiver listens for UDP packets representing pulses received from a electricity meter.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class PulseReceiver implements Runnable {
    @XmlTransient
    private Logger logger = LoggerFactory.getLogger(PulseReceiver.class);
    @XmlAttribute
    private String id;
    @XmlAttribute
    private Integer port = 9999;
    @XmlTransient
    private Thread thread;
    @XmlTransient
    private Map<String, PulseListener> applianceIdWithListener = new HashMap<>();


    public interface PulseListener {
        void pulseReceived(long counter);
    }

    public String getId() {
        return id;
    }

    public Integer getPort() {
        return port;
    }

    public void addListener(String applianceId, PulseListener listener) {
        applianceIdWithListener.put(applianceId, listener);
    }

    public void start() {
        logger.debug("Starting ...");
        thread = new Thread(this);
        thread.start();
    }

    @Override
    public void run() {
        try {
            logger.debug("Listening on UDP port " + port);
            DatagramSocket serverSocket = new DatagramSocket(port);
            byte[] receiveData = new byte[128];
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            while(true)
            {
                serverSocket.receive(receivePacket);
                String content = new String(receivePacket.getData(), 0, receivePacket.getLength());
                logger.debug("Received UDP packet: " + content);
                processReceivedPacket(content);
            }
        } catch (IOException e) {
            logger.error("Error listening on UDP port " + port, e);
        }
    }

    private void processReceivedPacket(String content) {
        if(content.length() > 0) {
            String[] contentParts = content.split(":");
            if(contentParts.length > 1) {
                // F-00000001-000000000001-00:143
                String applianceId = contentParts[0];
                int counter = Integer.valueOf(contentParts[1]);
                PulseListener listener = applianceIdWithListener.get(applianceId);
                if(listener != null) {
                    listener.pulseReceived(counter);
                }
                else {
                    logger.warn("No listener!");
                }
            }
            else {
                logger.error("UDP packet content has wrong format!");
            }
        }
        else {
            logger.error("UDP packet content has length 0!");
        }
    }
}
