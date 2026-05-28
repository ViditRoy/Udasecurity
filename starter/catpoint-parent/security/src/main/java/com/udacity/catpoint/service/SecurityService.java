package com.udacity.catpoint.service;

import com.udacity.catpoint.application.StatusListener;
import com.udacity.catpoint.data.AlarmStatus;
import com.udacity.catpoint.data.ArmingStatus;
import com.udacity.catpoint.data.SecurityRepository;
import com.udacity.catpoint.data.Sensor;
import com.udacity.image.service.ImageService;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

/**
 * Core business coordinator managing system state transitions, sensor updates,
 * and visual scanning checks within the UdaSecurity network.
 */
public class SecurityService {

    private final ImageService visualAnalyzer;
    private final SecurityRepository systemStore;
    private final Set<StatusListener> subscribers = new HashSet<>();

    /**
     * Cache tracking whether a feline was flagged in the latest visual scan.
     * Crucial to determine if entering ARMED_HOME state warrants raising an immediate ALARM.
     */
    private boolean lastScanIdentifiedCat = false;

    public SecurityService(SecurityRepository securityRepository, ImageService imageService) {
        this.systemStore = securityRepository;
        this.visualAnalyzer = imageService;
    }

    /**
     * Reconfigures the system-wide arming level, adjusting active hardware state as needed.
     *
     * - DISARMED: Automatically forces the system into a NO_ALARM state.
     * - ARMED (HOME/AWAY): Resets all paired hardware sensors back to an inactive state.
     * - ARMED_HOME: Instantly triggers an ALARM if the last camera scan detected a cat.
     *
     * @param armingStatus the chosen arming status
     */
    public void setArmingStatus(ArmingStatus armingStatus) {
        if (armingStatus == ArmingStatus.DISARMED) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        } else {
            getSensors().forEach(device -> {
                device.setActive(false);
                systemStore.updateSensor(device);
            });

            if (armingStatus == ArmingStatus.ARMED_HOME && lastScanIdentifiedCat) {
                setAlarmStatus(AlarmStatus.ALARM);
            }
        }
        systemStore.setArmingStatus(armingStatus);
        subscribers.forEach(StatusListener::sensorStatusChanged);
    }

    /**
     * Internal processor checking camera results and applying appropriate alarm transitions.
     *
     * @param hasCat True if a feline is currently detected on feed, otherwise False.
     */
    private void catDetected(Boolean hasCat) {
        lastScanIdentifiedCat = hasCat;

        if (hasCat && getArmingStatus() == ArmingStatus.ARMED_HOME) {
            setAlarmStatus(AlarmStatus.ALARM);
        } else if (!hasCat && getSensors().stream().noneMatch(Sensor::getActive)) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        }

        subscribers.forEach(listener -> listener.catDetected(hasCat));
    }

    public void addStatusListener(StatusListener subscriber) {
        subscribers.add(subscriber);
    }

    public void removeStatusListener(StatusListener subscriber) {
        subscribers.remove(subscriber);
    }

    /**
     * Modifies the system's current alarm state and propagates the transition to all observers.
     *
     * @param status the new alarm status
     */
    public void setAlarmStatus(AlarmStatus status) {
        systemStore.setAlarmStatus(status);
        subscribers.forEach(listener -> listener.notify(status));
    }

    /**
     * Evaluates state adjustments triggered by a sensor activation.
     */
    private void handleSensorActivated() {
        if (systemStore.getArmingStatus() == ArmingStatus.DISARMED) {
            return;
        }
        switch (systemStore.getAlarmStatus()) {
            case NO_ALARM -> setAlarmStatus(AlarmStatus.PENDING_ALARM);
            case PENDING_ALARM -> setAlarmStatus(AlarmStatus.ALARM);
            default -> {}
        }
    }

    /**
     * Evaluates state adjustments triggered by a sensor deactivation.
     */
    private void handleSensorDeactivated() {
        switch (systemStore.getAlarmStatus()) {
            case PENDING_ALARM -> setAlarmStatus(AlarmStatus.NO_ALARM);
            default -> {}
        }
    }

    /**
     * Updates a hardware sensor's activation level and evaluates necessary alarm actions.
     *
     * @param sensor the sensor being altered
     * @param active the target activation state
     */
    public void changeSensorActivationStatus(Sensor sensor, Boolean active) {
        if (active) {
            handleSensorActivated();
        } else if (sensor.getActive()) {
            handleSensorDeactivated();
        }

        sensor.setActive(active);
        systemStore.updateSensor(sensor);
    }

    /**
     * Coordinates feline check on a camera feed utilizing the underlying image recognition service.
     *
     * @param currentCameraImage camera capture frame
     */
    public void processImage(BufferedImage currentCameraImage) {
        catDetected(visualAnalyzer.imageContainsCat(currentCameraImage, 50.0f));
    }

    public AlarmStatus getAlarmStatus() {
        return systemStore.getAlarmStatus();
    }

    public Set<Sensor> getSensors() {
        return systemStore.getSensors();
    }

    public void addSensor(Sensor sensor) {
        systemStore.addSensor(sensor);
    }

    public void removeSensor(Sensor sensor) {
        systemStore.removeSensor(sensor);
    }

    public ArmingStatus getArmingStatus() {
        return systemStore.getArmingStatus();
    }
}
