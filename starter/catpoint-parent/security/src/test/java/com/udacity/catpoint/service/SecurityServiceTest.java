package com.udacity.catpoint.service;

import com.udacity.catpoint.application.StatusListener;
import com.udacity.catpoint.data.AlarmStatus;
import com.udacity.catpoint.data.ArmingStatus;
import com.udacity.catpoint.data.SecurityRepository;
import com.udacity.catpoint.data.Sensor;
import com.udacity.catpoint.data.SensorType;
import com.udacity.image.service.ImageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Isolated unit testing suite verifying the state transition engine in {@link SecurityService}.
 * Focuses entirely on business logic. All dependencies are decoupled using Mockito doubles.
 */
@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {

    @Mock
    private SecurityRepository mockRepository;

    @Mock
    private ImageService mockImageAnalyzer;

    @Mock
    private StatusListener mockStatusObserver;

    private SecurityService securityService;
    private Sensor primaryTestSensor;

    @BeforeEach
    void initTestSuite() {
        securityService = new SecurityService(mockRepository, mockImageAnalyzer);
        primaryTestSensor = new Sensor("Door Sensor Double", SensorType.DOOR);
    }

    // =========================================================================
    // Requirement 1
    // =========================================================================

    @ParameterizedTest(name = "Level: {0}")
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    @DisplayName("Req 1: Tripping inactive sensor when armed -> pending alarm")
    void testSensorActivatedWhileArmedHomeOrAwaySetsPendingAlarm(ArmingStatus level) {
        when(mockRepository.getArmingStatus()).thenReturn(level);
        when(mockRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);

        securityService.changeSensorActivationStatus(primaryTestSensor, true);

        verify(mockRepository).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    // =========================================================================
    // Requirement 2
    // =========================================================================

    @ParameterizedTest(name = "Level: {0}")
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    @DisplayName("Req 2: Tripping sensor when armed and pending -> alarm active")
    void testSensorActivatedWhileSystemPendingSetsAlarmState(ArmingStatus level) {
        when(mockRepository.getArmingStatus()).thenReturn(level);
        when(mockRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        securityService.changeSensorActivationStatus(primaryTestSensor, true);

        verify(mockRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    // =========================================================================
    // Requirement 3
    // =========================================================================

    @Test
    @DisplayName("Req 3: Clearing sensor when pending -> alarm clears")
    void testDeactivatingSensorsWhenPendingResetsAlarmToInactive() {
        primaryTestSensor.setActive(true);
        when(mockRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        securityService.changeSensorActivationStatus(primaryTestSensor, false);

        verify(mockRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    // =========================================================================
    // Requirement 4
    // =========================================================================

    @Test
    @DisplayName("Req 4: Clearing sensor while alarm is active -> no state change")
    void testSensorDeactivationIsIgnoredWhenAlarmIsAlreadyTriggered() {
        primaryTestSensor.setActive(true);
        when(mockRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);

        securityService.changeSensorActivationStatus(primaryTestSensor, false);

        verify(mockRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    @Test
    @DisplayName("Req 4: Activating sensor while alarm is active -> no state change")
    void testSensorActivationIsIgnoredWhenAlarmIsAlreadyTriggered() {
        when(mockRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        when(mockRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_AWAY);

        securityService.changeSensorActivationStatus(primaryTestSensor, true);

        verify(mockRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    // =========================================================================
    // Requirement 5
    // =========================================================================

    @Test
    @DisplayName("Req 5: Activating an already active sensor when pending -> alarm active")
    void testSensorTrippedAgainWhileActiveAndPendingTriggersAlarm() {
        primaryTestSensor.setActive(true);
        when(mockRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(mockRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        securityService.changeSensorActivationStatus(primaryTestSensor, true);

        verify(mockRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    // =========================================================================
    // Requirement 6
    // =========================================================================

    @Test
    @DisplayName("Req 6: Deactivating an already inactive sensor -> no state change")
    void testSensorDeactivatedWhileAlreadyAtRestCausesNoAlarmChange() {
        primaryTestSensor.setActive(false);

        securityService.changeSensorActivationStatus(primaryTestSensor, false);

        verify(mockRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    // =========================================================================
    // Requirement 7
    // =========================================================================

    @Test
    @DisplayName("Req 7: Feline visual match while armed home -> alarm active")
    void testFelineDetectionWhileArmedHomeEscalatesToAlarm() {
        BufferedImage cameraFrameWithCat = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        when(mockRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(mockImageAnalyzer.imageContainsCat(eq(cameraFrameWithCat), anyFloat())).thenReturn(true);

        securityService.processImage(cameraFrameWithCat);

        verify(mockRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    // =========================================================================
    // Requirement 8
    // =========================================================================

    @Test
    @DisplayName("Req 8: Feline absent and sensors idle -> alarm clears")
    void testFelineAbsenceAndSensorsAtRestClearsAlarmState() {
        BufferedImage cameraFrameWithoutCat = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        when(mockImageAnalyzer.imageContainsCat(eq(cameraFrameWithoutCat), anyFloat())).thenReturn(false);
        when(mockRepository.getSensors()).thenReturn(new HashSet<>());

        securityService.processImage(cameraFrameWithoutCat);

        verify(mockRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @Test
    @DisplayName("Req 8: Feline absent but sensors active -> alarm remains unchanged")
    void testFelineAbsenceWithActiveSensorsKeepsAlarmState() {
        BufferedImage cameraFrameWithoutCat = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        primaryTestSensor.setActive(true);
        Set<Sensor> setOfActiveSensors = new HashSet<>();
        setOfActiveSensors.add(primaryTestSensor);

        when(mockImageAnalyzer.imageContainsCat(eq(cameraFrameWithoutCat), anyFloat())).thenReturn(false);
        when(mockRepository.getSensors()).thenReturn(setOfActiveSensors);

        securityService.processImage(cameraFrameWithoutCat);

        verify(mockRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    // =========================================================================
    // Requirement 9
    // =========================================================================

    @Test
    @DisplayName("Req 9: Deactivating system -> alarm clears")
    void testDisarmingTheSystemResetsAlarmToInactiveState() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);

        verify(mockRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    // =========================================================================
    // Requirement 10
    // =========================================================================

    @ParameterizedTest(name = "Target: {0}")
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    @DisplayName("Req 10: Arming system -> resets sensors to inactive")
    void testArmingSystemForcesAllHardwareSensorsToInactive(ArmingStatus targetLevel) {
        Sensor firstHardwareSensor = new Sensor("Main Door Sensor", SensorType.DOOR);
        Sensor secondHardwareSensor = new Sensor("Living Room Sensor", SensorType.WINDOW);
        firstHardwareSensor.setActive(true);
        secondHardwareSensor.setActive(true);

        Set<Sensor> mockSensorsCollection = new HashSet<>();
        mockSensorsCollection.add(firstHardwareSensor);
        mockSensorsCollection.add(secondHardwareSensor);

        when(mockRepository.getSensors()).thenReturn(mockSensorsCollection);

        securityService.setArmingStatus(targetLevel);

        mockSensorsCollection.forEach(s -> {
            assert !s.getActive() : "Sensor " + s.getName() + " was not reset to inactive";
            verify(mockRepository).updateSensor(s);
        });
    }

    // =========================================================================
    // Requirement 11
    // =========================================================================

    @ParameterizedTest(name = "From: {0}")
    @EnumSource(value = ArmingStatus.class, names = {"DISARMED", "ARMED_AWAY"})
    @DisplayName("Req 11: Feline matched in disarmed/away -> armed home -> alarm active")
    void testFelineDetectedPreviouslyTriggersAlarmOnArmedHomeTransition(ArmingStatus fromState) {
        BufferedImage cameraFrameWithCat = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);

        when(mockRepository.getArmingStatus()).thenReturn(fromState);
        when(mockImageAnalyzer.imageContainsCat(eq(cameraFrameWithCat), anyFloat())).thenReturn(true);
        when(mockRepository.getSensors()).thenReturn(new HashSet<>());
        securityService.processImage(cameraFrameWithCat);

        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        verify(mockRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    // =========================================================================
    // Code Coverage Validation Tests
    // =========================================================================

    @Test
    @DisplayName("Coverage: Activating sensor while disarmed -> returns early")
    void testSensorActivatedWhileSystemIsDisarmedDoesNotTriggerAlarm() {
        when(mockRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);

        securityService.changeSensorActivationStatus(primaryTestSensor, true);

        verify(mockRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    @Test
    @DisplayName("Coverage: Registered listeners receive transition updates")
    void testAddStatusListenerEnablesAlarmStateNotifications() {
        securityService.addStatusListener(mockStatusObserver);

        securityService.setArmingStatus(ArmingStatus.DISARMED);

        verify(mockStatusObserver).notify(AlarmStatus.NO_ALARM);
        verify(mockStatusObserver).sensorStatusChanged();
    }

    @Test
    @DisplayName("Coverage: Registered listeners receive camera updates")
    void testAddStatusListenerEnablesFelineDetectionNotifications() {
        BufferedImage cameraFrameWithCat = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        securityService.addStatusListener(mockStatusObserver);

        when(mockRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        when(mockImageAnalyzer.imageContainsCat(eq(cameraFrameWithCat), anyFloat())).thenReturn(true);

        securityService.processImage(cameraFrameWithCat);

        verify(mockStatusObserver).catDetected(true);
    }

    @Test
    @DisplayName("Coverage: Deregistering status listeners stops notification stream")
    void testRemoveStatusListenerDeactivatesAllNotifications() {
        securityService.addStatusListener(mockStatusObserver);
        securityService.removeStatusListener(mockStatusObserver);

        securityService.setArmingStatus(ArmingStatus.DISARMED);

        verify(mockStatusObserver, never()).notify(any());
        verify(mockStatusObserver, never()).sensorStatusChanged();
    }

    @Test
    @DisplayName("Coverage: addSensor -> correct database delegation")
    void testAddSensorCorrectlyDelegatesToDatabase() {
        securityService.addSensor(primaryTestSensor);
        verify(mockRepository).addSensor(primaryTestSensor);
    }

    @Test
    @DisplayName("Coverage: removeSensor -> correct database delegation")
    void testRemoveSensorCorrectlyDelegatesToDatabase() {
        securityService.removeSensor(primaryTestSensor);
        verify(mockRepository).removeSensor(primaryTestSensor);
    }

    @Test
    @DisplayName("Coverage: getAlarmStatus -> correct database delegation")
    void testGetAlarmStatusCorrectlyDelegatesToDatabase() {
        when(mockRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        AlarmStatus resultingStatus = securityService.getAlarmStatus();
        assert resultingStatus == AlarmStatus.NO_ALARM;
        verify(mockRepository).getAlarmStatus();
    }
}
