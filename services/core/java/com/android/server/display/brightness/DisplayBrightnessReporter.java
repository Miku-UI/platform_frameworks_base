/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.display.brightness;

import static com.android.server.display.AutomaticBrightnessController.AUTO_BRIGHTNESS_MODE_IDLE;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.BrightnessInfo;
import android.os.Handler;
import android.os.PowerManager;

import com.android.internal.display.BrightnessSynchronizer;
import com.android.internal.os.BackgroundThread;
import com.android.internal.util.FrameworkStatsLog;
import com.android.server.display.DisplayBrightnessState;
import com.android.server.display.config.SensorData;
import com.android.server.display.utils.SensorUtils;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles logging of DISPLAY_BRIGHTNESS_CHANGED events, including asynchronous
 * sensor readings if necessary.
 */
public class DisplayBrightnessReporter {

    private static final float[] BRIGHTNESS_RANGE_BOUNDARIES = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 20, 30, 40, 50, 60, 70, 80,
            90, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1200,
            1400, 1600, 1800, 2000, 2250, 2500, 2750, 3000 };

    private static final int[] BRIGHTNESS_RANGE_INDEX = {
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_UNKNOWN,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_0_1,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_1_2,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_2_3,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_3_4,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_4_5,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_5_6,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_6_7,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_7_8,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_8_9,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_9_10,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_10_20,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_20_30,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_30_40,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_40_50,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_50_60,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_60_70,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_70_80,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_80_90,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_90_100,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_100_200,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_200_300,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_300_400,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_400_500,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_500_600,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_600_700,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_700_800,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_800_900,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_900_1000,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_1000_1200,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_1200_1400,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_1400_1600,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_1600_1800,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_1800_2000,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_2000_2250,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_2250_2500,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_2500_2750,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_2750_3000,
    };

    private static final float[] LUX_BUCKET_BOUNDARIES = {
            0.1f, 0.3f, 1f, 3f, 10f, 30f, 100f, 300f, 1000f,
            3000f, 10000f, 30000f, 100000f };

    private static final int[] LUX_RANGE_INDEX = {
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_0_01,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_01_03,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_03_1,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_1_3,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_3_10,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_10_30,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_30_100,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_100_300,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_300_1000,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_1000_3000,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_3000_10000,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_10000_30000,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_30000_100000,
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_100000_INF,
    };

    private final SensorManager mSensorManager;
    private final Sensor mLightSensor;
    private final Sensor mColorSensor;
    private volatile float mLastAmbientLuxReading = -1f;
    private volatile long mLastAmbientLuxReadingTimestamp = -1;

    private volatile float mLastColorTemperatureReading = -1f;
    private volatile long mLastColorTemperatureReadingTimestamp = -1;

    private static final int EVENT_VALIDITY_MS = 1000;
    private static final int SENSOR_READ_TIMEOUT_MS = 500;

    private PendingEvent mLastWaitingEvent = null;
    private final boolean mAsyncSensorReadingEnabled;
    private final Handler mBgHandler;
    private boolean mLightSensorRegistered = false;
    private boolean mColorSensorRegistered = false;

    public DisplayBrightnessReporter(SensorManager sensorManager,
            Sensor lightSensor, SensorData colorSensorData, boolean asyncSensorReadingEnabled) {
        mSensorManager = sensorManager;
        mLightSensor = lightSensor;
        mColorSensor = SensorUtils.findSensor(sensorManager, colorSensorData,
                SensorUtils.NO_FALLBACK);
        mAsyncSensorReadingEnabled = asyncSensorReadingEnabled;
        mBgHandler = BackgroundThread.getHandler();
    }

    public void report(BrightnessEvent event, float unmodifiedBrightness,
            DisplayBrightnessState brightnessState,
            float brightnessInNits,
            float initialBrightnessInNits,
            float appliedHbmMaxNits,
            float appliedThermalCapNits,
            boolean isDisplayInternal) {
        if (!isDisplayInternal) {
            return;
        }

        final boolean luxInvalid = isLuxInvalid(event);
        final boolean colorTempInvalid = isColorTempInvalid(event);
        final boolean isUserSet = (event.getFlags() & BrightnessEvent.FLAG_USER_SET) != 0;

        if (mAsyncSensorReadingEnabled && (luxInvalid || colorTempInvalid) && isUserSet) {
            final long now = android.os.SystemClock.uptimeMillis();
            final boolean luxRecentlyValid = !luxInvalid || (mLastAmbientLuxReadingTimestamp > 0
                    && now - mLastAmbientLuxReadingTimestamp < EVENT_VALIDITY_MS);
            final boolean colorRecentlyValid = !colorTempInvalid || (
                    mLastColorTemperatureReadingTimestamp > 0
                    && now - mLastColorTemperatureReadingTimestamp < EVENT_VALIDITY_MS);

            if (luxRecentlyValid && colorRecentlyValid) {
                if (luxInvalid) {
                    event.setLux(mLastAmbientLuxReading);
                    event.setFlags(event.getFlags() & ~BrightnessEvent.FLAG_INVALID_LUX);
                }
                if (colorTempInvalid) {
                    event.setAmbientColorTemperature(mLastColorTemperatureReading);
                }
                logBrightnessEvent(event, unmodifiedBrightness, brightnessState,
                        brightnessInNits, initialBrightnessInNits,
                        appliedHbmMaxNits, appliedThermalCapNits);
            } else {
                requestAsyncSensorReadings(event, unmodifiedBrightness, brightnessState,
                        brightnessInNits, initialBrightnessInNits,
                        appliedHbmMaxNits, appliedThermalCapNits);
            }
        } else {
            logBrightnessEvent(event, unmodifiedBrightness, brightnessState,
                    brightnessInNits, initialBrightnessInNits,
                    appliedHbmMaxNits, appliedThermalCapNits);
        }
    }

    private static boolean isLuxInvalid(BrightnessEvent event) {
        return (event.getFlags() & BrightnessEvent.FLAG_INVALID_LUX) != 0 || event.getLux() < 0;
    }

    private static boolean isColorTempInvalid(BrightnessEvent event) {
        return Float.isNaN(event.getAmbientColorTemperature()) ||
                event.getAmbientColorTemperature() < 0;
    }

    private void requestAsyncSensorReadings(BrightnessEvent event, float unmodifiedBrightness,
            DisplayBrightnessState brightnessState,
            float brightnessInNits, float initialBrightnessInNits,
            float appliedHbmMaxNits, float appliedThermalCapNits) {
        // Create a copy of the event to ensure we have the state at the time of the request
        final BrightnessEvent clonedEvent = new BrightnessEvent(event);
        final PendingEvent newEvent = new PendingEvent(clonedEvent, unmodifiedBrightness,
                brightnessState, brightnessInNits, initialBrightnessInNits,
                appliedHbmMaxNits, appliedThermalCapNits);

        final boolean luxInvalid = isLuxInvalid(event);
        final boolean colorTempInvalid = isColorTempInvalid(event);

        mBgHandler.post(() -> {
            if (mLastWaitingEvent != null) {
                logPendingEvent(mLastWaitingEvent);
                mBgHandler.removeCallbacks(mTimeoutRunnable);
            }
            mLastWaitingEvent = newEvent;

            if (luxInvalid && mLightSensor != null && !mLightSensorRegistered) {
                mSensorManager.registerListener(mSensorListener, mLightSensor,
                        SensorManager.SENSOR_DELAY_NORMAL, mBgHandler);
                mLightSensorRegistered = true;
            }
            if (colorTempInvalid && mColorSensor != null && !mColorSensorRegistered) {
                mSensorManager.registerListener(mSensorListener, mColorSensor,
                        SensorManager.SENSOR_DELAY_NORMAL, mBgHandler);
                mColorSensorRegistered = true;
            }
            mBgHandler.postDelayed(mTimeoutRunnable, SENSOR_READ_TIMEOUT_MS);
        });
    }

    private static void logBrightnessEvent(BrightnessEvent event, float unmodifiedBrightness,
            DisplayBrightnessState brightnessState,
            float brightnessInNits, float initialBrightnessInNits,
            float appliedHbmMaxNits, float appliedThermalCapNits) {
        int modifier = event.getReason().getModifier();
        int flags = event.getFlags();
        boolean brightnessIsMax = unmodifiedBrightness == event.getHbmMax();
        boolean isScreenOff = event.getReason().getReason() == BrightnessReason.REASON_SCREEN_OFF;
        float appliedLowPowerMode = event.isLowPowerModeSet() ? event.getPowerFactor() : -1f;
        int appliedRbcStrength = event.isRbcEnabled() ? event.getRbcStrength() : -1;
        int luxBucket = mapLuxToProtoEnumBucket(event.getLux());
        int brightnessAdjustmentDirection = getBrightnessAdjustmentDirection(
                brightnessInNits,
                initialBrightnessInNits);

        FrameworkStatsLog.write(
                FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED,
                initialBrightnessInNits,
                brightnessInNits,
                event.getLux(),
                event.getPhysicalDisplayId(),
                event.wasShortTermModelActive(),
                appliedLowPowerMode,
                appliedRbcStrength,
                appliedHbmMaxNits,
                appliedThermalCapNits,
                event.isAutomaticBrightnessEnabled(),
                FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__REASON__REASON_MANUAL,
                convertBrightnessReasonToStatsEnum(event.getReason().getReason()),
                isScreenOff
                        ? FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_0_MIN
                        : nitsToRangeIndex(brightnessInNits),
                brightnessIsMax,
                event.getHbmMode() == BrightnessInfo.HIGH_BRIGHTNESS_MODE_SUNLIGHT,
                event.getHbmMode() == BrightnessInfo.HIGH_BRIGHTNESS_MODE_HDR,
                (modifier & BrightnessReason.MODIFIER_LOW_POWER) > 0,
                brightnessState.getBrightnessMaxReason(),
                (modifier & BrightnessReason.MODIFIER_DIMMED) > 0,
                event.isRbcEnabled(),
                (flags & BrightnessEvent.FLAG_INVALID_LUX) > 0,
                (flags & BrightnessEvent.FLAG_DOZE_SCALE) > 0,
                (flags & BrightnessEvent.FLAG_USER_SET) > 0,
                event.getAutoBrightnessMode() == AUTO_BRIGHTNESS_MODE_IDLE,
                (flags & BrightnessEvent.FLAG_LOW_POWER_MODE) > 0,
                luxBucket,
                brightnessAdjustmentDirection,
                event.getAmbientColorTemperature(),
                event.getThermalStatus(),
                event.getDisplayStateReason(),
                convertDisplayPolicyToStatsEnum(event.getDisplayPolicy()));
    }

    public static int mapLuxToProtoEnumBucket(float lux) {
        if (lux < 0) {
            return FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_UNKNOWN;
        }

        for (int i = 0; i < LUX_BUCKET_BOUNDARIES.length; i++) {
            if (lux < LUX_BUCKET_BOUNDARIES[i]) {
                return LUX_RANGE_INDEX[i];
            }
        }

        return FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__LUX_BUCKET__LUX_RANGE_100000_INF;
    }

    public static int getBrightnessAdjustmentDirection(
            float currentBrightnessInNits,
            float lastReportedBrightnessInNits) {
        if (currentBrightnessInNits > lastReportedBrightnessInNits) {
            return FrameworkStatsLog.
                DISPLAY_BRIGHTNESS_CHANGED__BRIGHTNESS_DIRECTION__DIRECTION_INCREASE;
        } else if (currentBrightnessInNits < lastReportedBrightnessInNits) {
            return FrameworkStatsLog.
                DISPLAY_BRIGHTNESS_CHANGED__BRIGHTNESS_DIRECTION__DIRECTION_DECREASE;
        } else {
            return FrameworkStatsLog.
                DISPLAY_BRIGHTNESS_CHANGED__BRIGHTNESS_DIRECTION__DIRECTION_UNKNOWN;
        }
    }

    private static int nitsToRangeIndex(float nits) {
        for (int i = 0; i < BRIGHTNESS_RANGE_BOUNDARIES.length; i++) {
            if (nits < BRIGHTNESS_RANGE_BOUNDARIES[i]) {
                return BRIGHTNESS_RANGE_INDEX[i];
            }
        }
        return FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__BUCKET_INDEX__RANGE_3000_INF;
    }

    private static int convertDisplayPolicyToStatsEnum(int policy) {
        return switch (policy) {
            case android.hardware.display.DisplayManagerInternal.DisplayPowerRequest.POLICY_OFF ->
                FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__DISPLAY_POLICY__DISPLAY_POLICY_OFF;
            case android.hardware.display.DisplayManagerInternal.DisplayPowerRequest.POLICY_DOZE ->
                FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__DISPLAY_POLICY__DISPLAY_POLICY_DOZE;
            case android.hardware.display.DisplayManagerInternal.DisplayPowerRequest.POLICY_DIM ->
                FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__DISPLAY_POLICY__DISPLAY_POLICY_DIM;
            case android.hardware.display.DisplayManagerInternal.DisplayPowerRequest.POLICY_BRIGHT->
                FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__DISPLAY_POLICY__DISPLAY_POLICY_BRIGHT;
            default ->
            FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__DISPLAY_POLICY__DISPLAY_POLICY_UNKNOWN;
        };
    }

    private static int convertBrightnessReasonToStatsEnum(int brightnessReason) {
        return switch (brightnessReason) {
            case BrightnessReason.REASON_UNKNOWN ->
                FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__ENTIRE_REASON__REASON_UNKNOWN;
            case BrightnessReason.REASON_MANUAL ->
                FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__ENTIRE_REASON__REASON_MANUAL;
            case BrightnessReason.REASON_DOZE ->
                FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__ENTIRE_REASON__REASON_DOZE;
            case BrightnessReason.REASON_DOZE_DEFAULT ->
                FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__ENTIRE_REASON__REASON_DOZE_DEFAULT;
            case BrightnessReason.REASON_AUTOMATIC ->
                FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__ENTIRE_REASON__REASON_AUTOMATIC;
            case BrightnessReason.REASON_SCREEN_OFF ->
                FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__ENTIRE_REASON__REASON_SCREEN_OFF;
            case BrightnessReason.REASON_OVERRIDE ->
                FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__ENTIRE_REASON__REASON_OVERRIDE;
            case BrightnessReason.REASON_TEMPORARY ->
                FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__ENTIRE_REASON__REASON_TEMPORARY;
            case BrightnessReason.REASON_BOOST ->
                FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__ENTIRE_REASON__REASON_BOOST;
            case BrightnessReason.REASON_SCREEN_OFF_BRIGHTNESS_SENSOR ->
                FrameworkStatsLog.
                    DISPLAY_BRIGHTNESS_CHANGED__ENTIRE_REASON__REASON_SCREEN_OFF_BRIGHTNESS_SENSOR;
            case BrightnessReason.REASON_FOLLOWER ->
                FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__ENTIRE_REASON__REASON_FOLLOWER;
            default ->
                FrameworkStatsLog.DISPLAY_BRIGHTNESS_CHANGED__ENTIRE_REASON__REASON_UNKNOWN;
        };
    }

    public void stop() {
        mBgHandler.post(() -> {
            if (mLastWaitingEvent != null) {
                mLastWaitingEvent = null;
                mSensorManager.unregisterListener(mSensorListener);
                mLightSensorRegistered = false;
                mColorSensorRegistered = false;
                mBgHandler.removeCallbacks(mTimeoutRunnable);
            }
        });
    }

    private static class PendingEvent {
        final BrightnessEvent mEvent;
        final float mUnmodifiedBrightness;
        final DisplayBrightnessState mBrightnessState;
        final float mBrightnessInNits;
        final float mInitialBrightnessInNits;
        final float mAppliedHbmMaxNits;
        final float mAppliedThermalCapNits;

        PendingEvent(BrightnessEvent event, float unmodifiedBrightness,
                DisplayBrightnessState brightnessState, float brightnessInNits,
                float initialBrightnessInNits, float appliedHbmMaxNits,
                float appliedThermalCapNits) {
            mEvent = event;
            mUnmodifiedBrightness = unmodifiedBrightness;
            mBrightnessState = brightnessState;
            mBrightnessInNits = brightnessInNits;
            mInitialBrightnessInNits = initialBrightnessInNits;
            mAppliedHbmMaxNits = appliedHbmMaxNits;
            mAppliedThermalCapNits = appliedThermalCapNits;
        }
    }

    private final SensorEventListener mSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            final Sensor sensor = event.sensor;
            final float value = event.values[0];
            final long now = android.os.SystemClock.uptimeMillis();

            mBgHandler.post(() -> {
                if (sensor == mLightSensor) {
                    mLastAmbientLuxReading = value;
                    mLastAmbientLuxReadingTimestamp = now;
                } else if (sensor == mColorSensor) {
                    mLastColorTemperatureReading = value;
                    mLastColorTemperatureReadingTimestamp = now;
                }

                if (mLastWaitingEvent != null) {
                    boolean luxReady = !isLuxInvalid(mLastWaitingEvent.mEvent)
                            || (mLastAmbientLuxReadingTimestamp > 0
                                && now - mLastAmbientLuxReadingTimestamp < EVENT_VALIDITY_MS);
                    boolean colorReady = !isColorTempInvalid(mLastWaitingEvent.mEvent)
                            || (mLastColorTemperatureReadingTimestamp > 0
                                && now - mLastColorTemperatureReadingTimestamp < EVENT_VALIDITY_MS);

                    if (luxReady && colorReady) {
                        logAndCleanUpWaitingEvent();
                    }
                }
            });
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private final Runnable mTimeoutRunnable = this::logAndCleanUpWaitingEvent;

    private void logAndCleanUpWaitingEvent() {
        if (mLastWaitingEvent != null) {
            logPendingEvent(mLastWaitingEvent);
            mLastWaitingEvent = null;
            mSensorManager.unregisterListener(mSensorListener);
            mLightSensorRegistered = false;
            mColorSensorRegistered = false;
            mBgHandler.removeCallbacks(mTimeoutRunnable);
        }
    }

    private void logPendingEvent(PendingEvent pendingEvent) {
        BrightnessEvent event = pendingEvent.mEvent;
        final long now = android.os.SystemClock.uptimeMillis();

        if (isLuxInvalid(event) && mLastAmbientLuxReadingTimestamp > 0
                && now - mLastAmbientLuxReadingTimestamp < EVENT_VALIDITY_MS) {
            event.setLux(mLastAmbientLuxReading);
            event.setFlags(event.getFlags() & ~BrightnessEvent.FLAG_INVALID_LUX);
        }
        if (isColorTempInvalid(event) && mLastColorTemperatureReadingTimestamp > 0
                && now - mLastColorTemperatureReadingTimestamp < EVENT_VALIDITY_MS) {
            event.setAmbientColorTemperature(mLastColorTemperatureReading);
        }
        logBrightnessEvent(event, pendingEvent.mUnmodifiedBrightness,
                pendingEvent.mBrightnessState, pendingEvent.mBrightnessInNits,
                pendingEvent.mInitialBrightnessInNits, pendingEvent.mAppliedHbmMaxNits,
                pendingEvent.mAppliedThermalCapNits);
    }
}
