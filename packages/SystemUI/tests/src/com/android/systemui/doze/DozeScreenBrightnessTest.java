/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.doze;

import static com.android.systemui.doze.DozeMachine.State.DOZE;
import static com.android.systemui.doze.DozeMachine.State.DOZE_AOD;
import static com.android.systemui.doze.DozeMachine.State.DOZE_AOD_DOCKED;
import static com.android.systemui.doze.DozeMachine.State.DOZE_AOD_PAUSED;
import static com.android.systemui.doze.DozeMachine.State.DOZE_AOD_PAUSING;
import static com.android.systemui.doze.DozeMachine.State.DOZE_PULSE_DONE;
import static com.android.systemui.doze.DozeMachine.State.DOZE_PULSING;
import static com.android.systemui.doze.DozeMachine.State.DOZE_REQUEST_PULSE;
import static com.android.systemui.doze.DozeMachine.State.DOZE_SUSPEND_TRIGGERS;
import static com.android.systemui.doze.DozeMachine.State.FINISH;
import static com.android.systemui.doze.DozeMachine.State.INITIALIZED;
import static com.android.systemui.doze.DozeMachine.State.UNINITIALIZED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.PowerManager;
import android.os.UserHandle;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.Settings;
import android.view.Display;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.display.BrightnessSynchronizer;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.flags.DisableSceneContainer;
import com.android.systemui.flags.EnableSceneContainer;
import com.android.systemui.keyguard.WakefulnessLifecycle;
import com.android.systemui.kosmos.KosmosJavaAdapter;
import com.android.systemui.statusbar.phone.DozeParameters;
import com.android.systemui.statusbar.policy.DevicePostureController;
import com.android.systemui.util.concurrency.FakeExecutor;
import com.android.systemui.util.concurrency.FakeThreadFactory;
import com.android.systemui.util.sensors.AsyncSensorManager;
import com.android.systemui.util.sensors.FakeSensorManager;
import com.android.systemui.util.settings.SystemSettings;
import com.android.systemui.util.time.FakeSystemClock;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DozeScreenBrightnessTest extends SysuiTestCase {

    private static final float DEFAULT_BRIGHTNESS = 0.1f;
    private static final float DIM_BRIGHTNESS = 0.05f;
    private static final float[] SENSOR_TO_BRIGHTNESS = new float[]{-1, 0.01f, 0.05f, 0.7f, 0.1f};
    private static final int[] SENSOR_TO_BRIGHTNESS_INT = new int[]{-1, 1, 2, 3, 4};
    private static final int[] SENSOR_TO_OPACITY = new int[]{-1, 10, 0, 0, 1};
    private static final int[] SENSOR_TO_WALLPAPER_SCRIM_OPACITY = new int[]{-1, 12, 3, 2, 1};
    private static final float DELTA = BrightnessSynchronizer.EPSILON;

    private DozeServiceFake mServiceFake;
    private FakeSensorManager.FakeGenericSensor mSensor;
    private FakeSensorManager.FakeGenericSensor mSensorInner;
    private AsyncSensorManager mSensorManager;
    private AlwaysOnDisplayPolicy mAlwaysOnDisplayPolicy;
    @Mock
    DozeHost mDozeHost;
    @Mock
    WakefulnessLifecycle mWakefulnessLifecycle;
    @Mock
    DozeParameters mDozeParameters;
    @Mock
    DevicePostureController mDevicePostureController;
    @Mock
    DozeLog mDozeLog;
    @Mock
    SystemSettings mSystemSettings;
    @Mock
    DisplayManager mDisplayManager;
    private KosmosJavaAdapter mKosmos;
    private final FakeExecutor mFakeExecutor = new FakeExecutor(new FakeSystemClock());
    private final FakeThreadFactory mFakeThreadFactory = new FakeThreadFactory(mFakeExecutor);

    private DozeScreenBrightness mScreen;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mKosmos = new KosmosJavaAdapter(this);
        when(mSystemSettings.getIntForUser(eq(Settings.System.SCREEN_BRIGHTNESS), anyInt(),
                eq(UserHandle.USER_CURRENT))).thenReturn(PowerManager.BRIGHTNESS_ON);
        when(mDisplayManager.getBrightness(Display.DEFAULT_DISPLAY))
                .thenReturn(PowerManager.BRIGHTNESS_MAX);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(mDozeHost).prepareForGentleSleep(any());
        mServiceFake = new DozeServiceFake();
        FakeSensorManager fakeSensorManager = new FakeSensorManager(mContext);
        mSensorManager = new AsyncSensorManager(fakeSensorManager, mFakeThreadFactory, null);

        mAlwaysOnDisplayPolicy = new AlwaysOnDisplayPolicy(mContext);
        when(mDisplayManager.getDefaultDozeBrightness(Display.DEFAULT_DISPLAY))
                .thenReturn(DEFAULT_BRIGHTNESS);
        mAlwaysOnDisplayPolicy.screenBrightnessArray = SENSOR_TO_BRIGHTNESS_INT;
        when(mDisplayManager.getDozeBrightnessSensorValueToBrightness(Display.DEFAULT_DISPLAY))
                .thenReturn(SENSOR_TO_BRIGHTNESS);
        mAlwaysOnDisplayPolicy.dimBrightness = DIM_BRIGHTNESS;
        mAlwaysOnDisplayPolicy.dimmingScrimArray = SENSOR_TO_OPACITY;
        mAlwaysOnDisplayPolicy.wallpaperDimmingScrimArray = SENSOR_TO_WALLPAPER_SCRIM_OPACITY;
        mSensor = fakeSensorManager.getFakeLightSensor();
        mSensorInner = fakeSensorManager.getFakeLightSensor2();
        mScreen = new DozeScreenBrightness(
                mContext,
                mServiceFake,
                mSensorManager,
                new Optional[]{Optional.of(mSensor.getSensor())},
                mDozeHost,
                null /* handler */,
                mAlwaysOnDisplayPolicy,
                mWakefulnessLifecycle,
                mDozeParameters,
                mDevicePostureController,
                mDozeLog,
                mSystemSettings,
                mDisplayManager,
                mKosmos.getWallpaperInteractor(),
                mKosmos.getTestScope());
    }

    @Test
    public void testInitialize_setsScreenBrightnessToValidValue() {
        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);

        assertEquals(DEFAULT_BRIGHTNESS, mServiceFake.screenBrightness, DELTA);
        assertTrue(mServiceFake.screenBrightness >= PowerManager.BRIGHTNESS_MIN);
        assertTrue(mServiceFake.screenBrightness <= PowerManager.BRIGHTNESS_MAX);
    }

    @Test
    public void testAod_usesDebugValue() {
        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        mScreen.transitionTo(INITIALIZED, DOZE_AOD);
        waitForSensorManager();

        Intent intent = new Intent(DozeScreenBrightness.ACTION_AOD_BRIGHTNESS);
        intent.putExtra(DozeScreenBrightness.BRIGHTNESS_BUCKET, 1);
        mScreen.onReceive(mContext, intent);
        mSensor.sendSensorEvent(3);

        assertEquals(SENSOR_TO_BRIGHTNESS[1], mServiceFake.screenBrightness, DELTA);
    }

    @Test
    public void testAod_usesLightSensorRespectingUserSetting() {
        float maxBrightness = DEFAULT_BRIGHTNESS / 2;
        when(mDisplayManager.getBrightness(Display.DEFAULT_DISPLAY)).thenReturn(maxBrightness);
        when(mSystemSettings.getIntForUser(eq(Settings.System.SCREEN_BRIGHTNESS_MODE), anyInt(),
                eq(UserHandle.USER_CURRENT)))
                .thenReturn(Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        assertEquals(maxBrightness, mServiceFake.screenBrightness, DELTA);
    }

    @Test
    public void initialBrightness_clampsToAutoBrightnessValue() {
        float maxBrightnessFromAutoBrightness = DEFAULT_BRIGHTNESS / 2;
        when(mDisplayManager.getBrightness(Display.DEFAULT_DISPLAY)).thenReturn(
                maxBrightnessFromAutoBrightness
        );
        when(mSystemSettings.getIntForUser(eq(Settings.System.SCREEN_BRIGHTNESS_MODE), anyInt(),
                eq(UserHandle.USER_CURRENT)))
                .thenReturn(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);

        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        assertEquals(maxBrightnessFromAutoBrightness, mServiceFake.screenBrightness,
                DELTA);
    }

    @Test
    public void doze_doesNotUseLightSensor() {
        // GIVEN the device is DOZE and the display state changes to ON
        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        mScreen.transitionTo(INITIALIZED, DOZE);
        waitForSensorManager();

        // WHEN new sensor event sent
        mSensor.sendSensorEvent(3);

        // THEN brightness is NOT changed, it's set to the default brightness
        assertEquals(DEFAULT_BRIGHTNESS, mServiceFake.screenBrightness, DELTA);
    }

    @Test
    public void dozeSuspendTriggers_doesNotUseLightSensor() {
        // GIVEN the device is DOZE and the display state changes to ON
        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        mScreen.transitionTo(INITIALIZED, DOZE_SUSPEND_TRIGGERS);
        waitForSensorManager();

        // WHEN new sensor event sent
        mSensor.sendSensorEvent(3);

        // THEN brightness is NOT changed, it's set to the default brightness
        assertNotSame(SENSOR_TO_BRIGHTNESS[3], mServiceFake.screenBrightness);
        assertEquals(DEFAULT_BRIGHTNESS, mServiceFake.screenBrightness, DELTA);
    }

    @Test
    public void aod_usesLightSensor() {
        // GIVEN the device is DOZE_AOD and the display state changes to ON
        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        mScreen.transitionTo(INITIALIZED, DOZE_AOD);
        waitForSensorManager();

        // WHEN new sensor event sent
        mSensor.sendSensorEvent(3);

        // THEN brightness is updated
        assertEquals(SENSOR_TO_BRIGHTNESS[3], mServiceFake.screenBrightness, DELTA);
    }

    @Test
    public void lightSensorChangesInAod_doesNotClampToAutoBrightnessValue() {
        // GIVEN auto brightness reports low brightness
        float maxBrightnessFromAutoBrightness = DEFAULT_BRIGHTNESS / 2;
        when(mDisplayManager.getBrightness(Display.DEFAULT_DISPLAY))
                .thenReturn(maxBrightnessFromAutoBrightness);
        when(mSystemSettings.getIntForUser(eq(Settings.System.SCREEN_BRIGHTNESS_MODE), anyInt(),
                eq(UserHandle.USER_CURRENT)))
                .thenReturn(Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);

        // GIVEN the device is DOZE_AOD and the display state changes to ON
        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        mScreen.transitionTo(INITIALIZED, DOZE_AOD);
        waitForSensorManager();

        // WHEN new sensor event sent
        mSensor.sendSensorEvent(3);

        // THEN brightness is updated
        assertEquals(SENSOR_TO_BRIGHTNESS[3], mServiceFake.screenBrightness, DELTA);
    }

    @Test
    public void docked_usesLightSensor() {
        // GIVEN the device is docked and the display state changes to ON
        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        mScreen.transitionTo(INITIALIZED, DOZE_AOD);
        mScreen.transitionTo(DOZE_AOD, DOZE_AOD_DOCKED);
        waitForSensorManager();

        // WHEN new sensor event sent
        mSensor.sendSensorEvent(3);

        // THEN brightness is updated
        assertEquals(SENSOR_TO_BRIGHTNESS[3], mServiceFake.screenBrightness, DELTA);
    }

    @Test
    public void testPulsing_withoutLightSensor_setsAoDDimmingScrimTransparent() {
        mScreen = new DozeScreenBrightness(
                mContext,
                mServiceFake,
                mSensorManager,
                new Optional[] {Optional.empty()} /* sensor */,
                mDozeHost, null /* handler */,
                mAlwaysOnDisplayPolicy,
                mWakefulnessLifecycle,
                mDozeParameters,
                mDevicePostureController,
                mDozeLog,
                mSystemSettings,
                mDisplayManager,
                mKosmos.getWallpaperInteractor(),
                mKosmos.getTestScope()
        );
        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        mScreen.transitionTo(INITIALIZED, DOZE);
        reset(mDozeHost);

        mScreen.transitionTo(DOZE, DOZE_REQUEST_PULSE);

        verify(mDozeHost).setAodDimmingScrim(eq(0f));
    }

    @Test
    public void testScreenOffAfterPulsing_pausesLightSensor() {
        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        mScreen.transitionTo(INITIALIZED, DOZE);
        mScreen.transitionTo(DOZE, DOZE_REQUEST_PULSE);
        mScreen.transitionTo(DOZE_REQUEST_PULSE, DOZE_PULSING);
        mScreen.transitionTo(DOZE_PULSING, DOZE_PULSE_DONE);
        mScreen.transitionTo(DOZE_PULSE_DONE, DOZE);
        waitForSensorManager();

        mSensor.sendSensorEvent(1);

        assertEquals(DEFAULT_BRIGHTNESS, mServiceFake.screenBrightness, DELTA);
    }

    @Test
    public void testNullSensor() {
        mScreen = new DozeScreenBrightness(
                mContext,
                mServiceFake,
                mSensorManager,
                new Optional[]{Optional.empty()} /* sensor */,
                mDozeHost, null /* handler */,
                mAlwaysOnDisplayPolicy,
                mWakefulnessLifecycle,
                mDozeParameters,
                mDevicePostureController,
                mDozeLog,
                mSystemSettings,
                mDisplayManager,
                mKosmos.getWallpaperInteractor(),
                mKosmos.getTestScope()
        );

        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        mScreen.transitionTo(INITIALIZED, DOZE_AOD);
        mScreen.transitionTo(DOZE_AOD, DOZE_AOD_PAUSING);
        mScreen.transitionTo(DOZE_AOD_PAUSING, DOZE_AOD_PAUSED);
    }

    @Test
    public void testSensorsSupportPostures_closed() {
        // GIVEN the device is CLOSED
        when(mDevicePostureController.getDevicePosture()).thenReturn(
                DevicePostureController.DEVICE_POSTURE_CLOSED);

        // GIVEN closed and opened postures use different light sensors
        mScreen = new DozeScreenBrightness(
                mContext,
                mServiceFake,
                mSensorManager,
                new Optional[]{
                        Optional.empty() /* unknown */,
                        Optional.of(mSensor.getSensor()) /* closed */,
                        Optional.of(mSensorInner.getSensor()) /* half-opened */,
                        Optional.of(mSensorInner.getSensor()) /* opened */,
                        Optional.empty() /* flipped */
                },
                mDozeHost, null /* handler */,
                mAlwaysOnDisplayPolicy,
                mWakefulnessLifecycle,
                mDozeParameters,
                mDevicePostureController,
                mDozeLog,
                mSystemSettings,
                mDisplayManager,
                mKosmos.getWallpaperInteractor(),
                mKosmos.getTestScope()
        );

        // GIVEN the device is in AOD
        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        mScreen.transitionTo(INITIALIZED, DOZE_AOD);
        waitForSensorManager();

        // WHEN new different events are sent from the inner and outer sensors
        mSensor.sendSensorEvent(3); // CLOSED sensor
        mSensorInner.sendSensorEvent(4); // OPENED sensor

        // THEN brightness is updated according to the sensor for CLOSED
        assertEquals(SENSOR_TO_BRIGHTNESS[3], mServiceFake.screenBrightness,
                DELTA);
    }

    @Test
    public void testSensorsSupportPostures_open() {
        // GIVEN the device is OPENED
        when(mDevicePostureController.getDevicePosture()).thenReturn(
                DevicePostureController.DEVICE_POSTURE_OPENED);

        // GIVEN closed and opened postures use different light sensors
        mScreen = new DozeScreenBrightness(
                mContext,
                mServiceFake,
                mSensorManager,
                new Optional[]{
                        Optional.empty() /* unknown */,
                        Optional.of(mSensor.getSensor()) /* closed */,
                        Optional.of(mSensorInner.getSensor()) /* half-opened */,
                        Optional.of(mSensorInner.getSensor()) /* opened */,
                        Optional.empty() /* flipped */
                },
                mDozeHost, null /* handler */,
                mAlwaysOnDisplayPolicy,
                mWakefulnessLifecycle,
                mDozeParameters,
                mDevicePostureController,
                mDozeLog,
                mSystemSettings,
                mDisplayManager,
                mKosmos.getWallpaperInteractor(),
                mKosmos.getTestScope()
        );

        // GIVEN device is in AOD
        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        mScreen.transitionTo(INITIALIZED, DOZE_AOD);
        waitForSensorManager();

        // WHEN new different events are sent from the inner and outer sensors
        mSensorInner.sendSensorEvent(4); // OPENED sensor
        mSensor.sendSensorEvent(3); // CLOSED sensor

        // THEN brightness is updated according to the sensor for OPENED
        assertEquals(SENSOR_TO_BRIGHTNESS[4], mServiceFake.screenBrightness, DELTA);
    }

    @Test
    public void testSensorsSupportPostures_swapPostures() {
        ArgumentCaptor<DevicePostureController.Callback> postureCallbackCaptor =
                ArgumentCaptor.forClass(DevicePostureController.Callback.class);
        reset(mDevicePostureController);

        // GIVEN the device starts up AOD OPENED
        when(mDevicePostureController.getDevicePosture()).thenReturn(
                DevicePostureController.DEVICE_POSTURE_OPENED);

        // GIVEN closed and opened postures use different light sensors
        mScreen = new DozeScreenBrightness(
                mContext,
                mServiceFake,
                mSensorManager,
                new Optional[]{
                        Optional.empty() /* unknown */,
                        Optional.of(mSensor.getSensor()) /* closed */,
                        Optional.of(mSensorInner.getSensor()) /* half-opened */,
                        Optional.of(mSensorInner.getSensor()) /* opened */,
                        Optional.empty() /* flipped */
                },
                mDozeHost, null /* handler */,
                mAlwaysOnDisplayPolicy,
                mWakefulnessLifecycle,
                mDozeParameters,
                mDevicePostureController,
                mDozeLog,
                mSystemSettings,
                mDisplayManager,
                mKosmos.getWallpaperInteractor(),
                mKosmos.getTestScope()
        );
        verify(mDevicePostureController).addCallback(postureCallbackCaptor.capture());

        // GIVEN device is in AOD
        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        mScreen.transitionTo(INITIALIZED, DOZE_AOD);
        waitForSensorManager();

        // WHEN the posture changes to CLOSED
        postureCallbackCaptor.getValue().onPostureChanged(
                DevicePostureController.DEVICE_POSTURE_CLOSED);
        waitForSensorManager();

        // WHEN new different events are sent from the inner and outer sensors
        mSensor.sendSensorEvent(3); // CLOSED sensor
        mSensorInner.sendSensorEvent(4); // OPENED sensor

        // THEN brightness is updated according to the sensor for CLOSED
        assertEquals(SENSOR_TO_BRIGHTNESS[3], mServiceFake.screenBrightness, DELTA);
    }

    @Test
    public void testNoBrightnessDeliveredAfterFinish() {
        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        mScreen.transitionTo(INITIALIZED, DOZE_AOD);
        mScreen.transitionTo(DOZE_AOD, FINISH);
        waitForSensorManager();

        mSensor.sendSensorEvent(1);

        assertNotEquals(SENSOR_TO_BRIGHTNESS[1], mServiceFake.screenBrightness);
    }

    @Test
    public void testNonPositiveBrightness_keepsPreviousBrightnessAndScrim() {
        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        mScreen.transitionTo(INITIALIZED, DOZE_AOD);
        waitForSensorManager();

        mSensor.sendSensorEvent(1);
        mSensor.sendSensorEvent(0);

        assertEquals(SENSOR_TO_BRIGHTNESS[1], mServiceFake.screenBrightness, DELTA);
        verify(mDozeHost).setAodDimmingScrim(eq(10f / 255f));
    }

    @Test
    @DisableSceneContainer
    public void ambientAod_usesWallpaperScrimOpacity() {
        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        mScreen.transitionTo(INITIALIZED, DOZE_AOD);
        mKosmos.getWallpaperRepository().setWallpaperSupportsAmbientMode(true);
        mKosmos.getTestScope().getTestScheduler().runCurrent();
        waitForSensorManager();

        int sensorValue = 1;
        mSensor.sendSensorEvent(sensorValue);

        // uses SENSOR_TO_WALLPAPER_SCRIM_OPACITY[sensorValue] bc its greater than
        // SENSOR_TO_OPACITY[sensorValue]
        verify(mDozeHost).setAodDimmingScrim(
                eq(SENSOR_TO_WALLPAPER_SCRIM_OPACITY[sensorValue] / 255f)
        );
    }


    @Test
    @DisableSceneContainer
    public void ambientAod_usesRegularScrimOpacity() {
        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        mScreen.transitionTo(INITIALIZED, DOZE_AOD);
        mKosmos.getWallpaperRepository().setWallpaperSupportsAmbientMode(true);
        mKosmos.getTestScope().getTestScheduler().runCurrent();
        waitForSensorManager();

        int sensorValue = 4;
        mSensor.sendSensorEvent(sensorValue);

        // uses SENSOR_TO_OPACITY[sensorValue] bc its greater than
        // SENSOR_TO_WALLPAPER_SCRIM_OPACITY[sensorValue]
        verify(mDozeHost).setAodDimmingScrim(
                eq(SENSOR_TO_OPACITY[sensorValue] / 255f)
        );
    }

    @Test
    @EnableSceneContainer
    public void ambientAod_propagateWallpaperScrimOpacity() {
        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        mScreen.transitionTo(INITIALIZED, DOZE_AOD);
        mKosmos.getWallpaperRepository().setWallpaperSupportsAmbientMode(true);
        mKosmos.getTestScope().getTestScheduler().runCurrent();
        waitForSensorManager();

        int sensorValue = 1;
        mSensor.sendSensorEvent(sensorValue);

        // set SENSOR_TO_WALLPAPER_SCRIM_OPACITY[sensorValue]
        verify(mDozeHost).setAodWallpaperDimmingScrim(
                eq(SENSOR_TO_WALLPAPER_SCRIM_OPACITY[sensorValue] / 255f)
        );

        sensorValue = 4;
        mSensor.sendSensorEvent(sensorValue);

        // update SENSOR_TO_WALLPAPER_SCRIM_OPACITY[sensorValue]
        verify(mDozeHost).setAodWallpaperDimmingScrim(
                eq(SENSOR_TO_WALLPAPER_SCRIM_OPACITY[sensorValue] / 255f)
        );
    }

    @Test
    public void pausingAod_unblanksAfterSensorEvent() {
        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        mScreen.transitionTo(INITIALIZED, DOZE_AOD);
        waitForSensorManager();

        mScreen.transitionTo(DOZE_AOD, DOZE_AOD_PAUSING);
        mScreen.transitionTo(DOZE_AOD_PAUSING, DOZE_AOD_PAUSED);

        reset(mDozeHost);
        mScreen.transitionTo(DOZE_AOD_PAUSED, DOZE_AOD);
        waitForSensorManager();
        mSensor.sendSensorEvent(2);
        verify(mDozeHost).setAodDimmingScrim(eq(0f));
    }

    @Test
    public void transitionToDoze_shouldClampBrightness_afterTimeout_clampsToDim() {
        when(mWakefulnessLifecycle.getLastSleepReason()).thenReturn(
                PowerManager.GO_TO_SLEEP_REASON_TIMEOUT);
        when(mDozeParameters.shouldClampToDimBrightness()).thenReturn(true);

        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);

        // If we're dozing after a timeout, and playing the unlocked screen animation, we should
        // stay at or below dim brightness, because the screen dims just before timeout.
        assertTrue(mServiceFake.screenBrightness <= DIM_BRIGHTNESS);

        // Once we transition to Doze, use the doze brightness
        mScreen.transitionTo(INITIALIZED, DOZE);
        assertEquals(DEFAULT_BRIGHTNESS, mServiceFake.screenBrightness, DELTA);
    }

    @Test
    public void transitionToDoze_shouldClampBrightness_notAfterTimeout_doesNotClampToDim() {
        when(mWakefulnessLifecycle.getLastSleepReason()).thenReturn(
                PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON);
        when(mDozeParameters.shouldClampToDimBrightness()).thenReturn(true);

        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);

        // If we're playing the unlocked screen off animation after a power button press, we should
        // leave the brightness alone.
        assertEquals(DEFAULT_BRIGHTNESS, mServiceFake.screenBrightness, DELTA);

        mScreen.transitionTo(INITIALIZED, DOZE);
        assertEquals(DEFAULT_BRIGHTNESS, mServiceFake.screenBrightness, DELTA);
    }

    @Test
    public void transitionToDoze_noClamp_afterTimeout_noScreenOff_doesNotClampToDim() {
        when(mWakefulnessLifecycle.getLastSleepReason()).thenReturn(
                PowerManager.GO_TO_SLEEP_REASON_TIMEOUT);
        when(mDozeParameters.shouldClampToDimBrightness()).thenReturn(false);

        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        mScreen.transitionTo(INITIALIZED, DOZE);

        // If we aren't controlling the screen off animation, we should leave the brightness alone.
        assertEquals(DEFAULT_BRIGHTNESS, mServiceFake.screenBrightness, DELTA);
    }

    @Test
    public void transitionToDoze_noClampBrightness_afterTimeout_clampsToDim() {
        when(mWakefulnessLifecycle.getLastSleepReason()).thenReturn(
                PowerManager.GO_TO_SLEEP_REASON_TIMEOUT);
        when(mWakefulnessLifecycle.getWakefulness()).thenReturn(
                WakefulnessLifecycle.WAKEFULNESS_GOING_TO_SLEEP);
        when(mDozeParameters.shouldClampToDimBrightness()).thenReturn(false);

        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);

        assertTrue(mServiceFake.screenBrightness <= DIM_BRIGHTNESS);
    }

    @Test
    public void transitionToDoze_noClampBrigthness_notAfterTimeout_doesNotClampToDim() {
        when(mWakefulnessLifecycle.getLastSleepReason()).thenReturn(
                PowerManager.GO_TO_SLEEP_REASON_POWER_BUTTON);
        when(mWakefulnessLifecycle.getWakefulness()).thenReturn(
                WakefulnessLifecycle.WAKEFULNESS_GOING_TO_SLEEP);
        when(mDozeParameters.shouldClampToDimBrightness()).thenReturn(false);

        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        mScreen.transitionTo(INITIALIZED, DOZE);

        assertEquals(DEFAULT_BRIGHTNESS, mServiceFake.screenBrightness, DELTA);
    }

    @Test
    public void transitionToAodPaused_lightSensorDisabled() {
        // GIVEN AOD
        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        mScreen.transitionTo(INITIALIZED, DOZE_AOD);

        // WHEN AOD is paused
        mScreen.transitionTo(DOZE_AOD, DOZE_AOD_PAUSING);
        mScreen.transitionTo(DOZE_AOD, DOZE_AOD_PAUSED);
        waitForSensorManager();

        // THEN new light events don't update brightness since the light sensor was unregistered
        mSensor.sendSensorEvent(1);
        assertEquals(DEFAULT_BRIGHTNESS, mServiceFake.screenBrightness, DELTA);
    }

    @Test
    public void transitionFromAodPausedToAod_lightSensorEnabled() {
        // GIVEN AOD paused
        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        mScreen.transitionTo(INITIALIZED, DOZE_AOD);
        mScreen.transitionTo(DOZE_AOD, DOZE_AOD_PAUSING);
        mScreen.transitionTo(DOZE_AOD, DOZE_AOD_PAUSED);

        // WHEN device transitions back to AOD
        mScreen.transitionTo(DOZE_AOD_PAUSED, DOZE_AOD);
        waitForSensorManager();

        // WHEN there are brightness changes
        mSensor.sendSensorEvent(1);

        // THEN aod brightness is updated
        assertEquals(SENSOR_TO_BRIGHTNESS[1], mServiceFake.screenBrightness, DELTA);
    }

    @Test
    public void fallBackToIntIfFloatBrightnessUndefined() {
        when(mDisplayManager.getDozeBrightnessSensorValueToBrightness(Display.DEFAULT_DISPLAY))
                .thenReturn(null);
        mScreen = new DozeScreenBrightness(
                mContext,
                mServiceFake,
                mSensorManager,
                new Optional[]{Optional.of(mSensor.getSensor())},
                mDozeHost,
                null /* handler */,
                mAlwaysOnDisplayPolicy,
                mWakefulnessLifecycle,
                mDozeParameters,
                mDevicePostureController,
                mDozeLog,
                mSystemSettings,
                mDisplayManager,
                mKosmos.getWallpaperInteractor(),
                mKosmos.getTestScope()
        );

        // GIVEN device is in AOD
        mScreen.transitionTo(UNINITIALIZED, INITIALIZED);
        mScreen.transitionTo(INITIALIZED, DOZE_AOD);
        waitForSensorManager();

        // WHEN there are brightness changes
        mSensor.sendSensorEvent(3);

        // THEN aod brightness is updated
        assertEquals(BrightnessSynchronizer.brightnessIntToFloat(SENSOR_TO_BRIGHTNESS_INT[3]),
                mServiceFake.screenBrightness, DELTA);
    }

    private void waitForSensorManager() {
        mFakeExecutor.runAllReady();
    }
}
