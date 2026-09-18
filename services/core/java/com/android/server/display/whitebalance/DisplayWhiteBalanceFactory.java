/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.display.whitebalance;

import static com.android.server.display.utils.SensorUtils.NO_FALLBACK;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Handler;
import android.util.TypedValue;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.BackgroundThread;
import com.android.server.display.DisplayDeviceConfig;
import com.android.server.display.config.SensorData;
import com.android.server.display.feature.flags.Flags;
import com.android.server.display.utils.AmbientFilter;
import com.android.server.display.utils.AmbientFilterFactory;
import com.android.server.display.utils.SensorUtils;

/**
 * The DisplayWhiteBalanceFactory creates and configures an DisplayWhiteBalanceController.
 */
public class DisplayWhiteBalanceFactory {

    private static final String BRIGHTNESS_FILTER_TAG = "AmbientBrightnessFilter";
    private static final String COLOR_TEMPERATURE_FILTER_TAG = "AmbientColorTemperatureFilter";

    /**
     * Create and configure an DisplayWhiteBalanceController.
     *
     * @param handler
     *      The handler used to determine which thread to run on.
     * @param sensorManager
     *      The sensor manager used to acquire necessary sensors.
     * @param resources
     *      The resources used to configure the various components.
     *
     * @return A DisplayWhiteBalanceController.
     *
     * @throws NullPointerException
     *      - handler is null;
     *      - sensorManager is null.
     * @throws Resources.NotFoundException
     *      - Configurations are missing.
     * @throws IllegalArgumentException
     *      - Configurations are invalid.
     * @throws IllegalStateException
     *      - Cannot find the necessary sensors.
     */
    public static DisplayWhiteBalanceController create(Handler handler,
            SensorManager sensorManager, Resources resources,
            DisplayDeviceConfig displayDeviceConfig) {
        final AmbientSensor.AmbientBrightnessSensor brightnessSensor =
                createBrightnessSensor(handler, sensorManager, resources, displayDeviceConfig);
        final AmbientFilter brightnessFilter =
                AmbientFilterFactory.createBrightnessFilter(BRIGHTNESS_FILTER_TAG, resources);
        final AmbientSensor.AmbientColorTemperatureSensor colorTemperatureSensor =
                createColorTemperatureSensor(handler, sensorManager, resources,
                        displayDeviceConfig);
        final AmbientFilter colorTemperatureFilter = AmbientFilterFactory
                .createColorTemperatureFilter(COLOR_TEMPERATURE_FILTER_TAG, resources);
        final DisplayWhiteBalanceThrottler throttler = createThrottler(resources);
        final float[] displayWhiteBalanceLowLightAmbientBrightnesses = getFloatArray(resources,
                com.android.internal.R.array
                .config_displayWhiteBalanceLowLightAmbientBrightnesses);
        final float[] displayWhiteBalanceLowLightAmbientBrightnessesStrong = getFloatArray(
                resources, com.android.internal.R.array
                .config_displayWhiteBalanceLowLightAmbientBrightnessesStrong);
        final float[] displayWhiteBalanceLowLightAmbientBiases = getFloatArray(resources,
                com.android.internal.R.array
                .config_displayWhiteBalanceLowLightAmbientBiases);
        final float[] displayWhiteBalanceLowLightAmbientBiasesStrong = getFloatArray(resources,
                com.android.internal.R.array
                .config_displayWhiteBalanceLowLightAmbientBiasesStrong);
        final float lowLightAmbientColorTemperature = getFloat(resources,
                com.android.internal.R.dimen
                .config_displayWhiteBalanceLowLightAmbientColorTemperature);
        final float lowLightAmbientColorTemperatureStrong = getFloat(resources,
                com.android.internal.R.dimen
                .config_displayWhiteBalanceLowLightAmbientColorTemperatureStrong);
        final float[] displayWhiteBalanceHighLightAmbientBrightnesses = getFloatArray(resources,
                com.android.internal.R.array
                .config_displayWhiteBalanceHighLightAmbientBrightnesses);
        final float[] displayWhiteBalanceHighLightAmbientBrightnessesStrong = getFloatArray(
                resources, com.android.internal.R.array
                .config_displayWhiteBalanceHighLightAmbientBrightnessesStrong);
        final float[] displayWhiteBalanceHighLightAmbientBiases = getFloatArray(resources,
                com.android.internal.R.array
                .config_displayWhiteBalanceHighLightAmbientBiases);
        final float[] displayWhiteBalanceHighLightAmbientBiasesStrong = getFloatArray(resources,
                com.android.internal.R.array
                .config_displayWhiteBalanceHighLightAmbientBiasesStrong);
        final float highLightAmbientColorTemperature = getFloat(resources,
                com.android.internal.R.dimen
                .config_displayWhiteBalanceHighLightAmbientColorTemperature);
        final float highLightAmbientColorTemperatureStrong = getFloat(resources,
                com.android.internal.R.dimen
                .config_displayWhiteBalanceHighLightAmbientColorTemperatureStrong);
        final float[] ambientColorTemperatures = getFloatArray(resources,
                com.android.internal.R.array.config_displayWhiteBalanceAmbientColorTemperatures);
        final float[] displayColorTemperatures = getFloatArray(resources,
                com.android.internal.R.array.config_displayWhiteBalanceDisplayColorTemperatures);
        final float[] strongAmbientColorTemperatures = getFloatArray(resources,
                com.android.internal.R.array
                .config_displayWhiteBalanceStrongAmbientColorTemperatures);
        final float[] strongDisplayColorTemperatures = getFloatArray(resources,
                com.android.internal.R.array
                .config_displayWhiteBalanceStrongDisplayColorTemperatures);
        final boolean lightModeAllowed = resources.getBoolean(
                com.android.internal.R.bool.config_displayWhiteBalanceLightModeAllowed);
        final DisplayWhiteBalanceController controller = new DisplayWhiteBalanceController(
                brightnessSensor, brightnessFilter, colorTemperatureSensor, colorTemperatureFilter,
                throttler, BackgroundThread.getHandler(),
                displayWhiteBalanceLowLightAmbientBrightnesses,
                displayWhiteBalanceLowLightAmbientBrightnessesStrong,
                displayWhiteBalanceLowLightAmbientBiases,
                displayWhiteBalanceLowLightAmbientBiasesStrong, lowLightAmbientColorTemperature,
                lowLightAmbientColorTemperatureStrong,
                displayWhiteBalanceHighLightAmbientBrightnesses,
                displayWhiteBalanceHighLightAmbientBrightnessesStrong,
                displayWhiteBalanceHighLightAmbientBiases,
                displayWhiteBalanceHighLightAmbientBiasesStrong, highLightAmbientColorTemperature,
                highLightAmbientColorTemperatureStrong,
                ambientColorTemperatures, displayColorTemperatures, strongAmbientColorTemperatures,
                strongDisplayColorTemperatures, lightModeAllowed);
        brightnessSensor.setCallbacks(controller);
        colorTemperatureSensor.setCallbacks(controller);
        return controller;
    }

    // Instantiation is disabled.
    private DisplayWhiteBalanceFactory() { }

    /**
     * Creates a brightness sensor instance to redirect sensor data to callbacks.
     */
    @VisibleForTesting
    public static AmbientSensor.AmbientBrightnessSensor createBrightnessSensor(Handler handler,
            SensorManager sensorManager, Resources resources,
            DisplayDeviceConfig displayDeviceConfig) {
        Sensor sensor;
        if (Flags.whiteBalanceControllerDdcConfig()) {
            SensorData sensorData = displayDeviceConfig.getAmbientLightSensor();
            sensor = SensorUtils.findSensor(sensorManager, sensorData, Sensor.TYPE_LIGHT);
            if (sensor == null) {
                throw new IllegalStateException("cannot find light sensor: "
                        + " sensorData" + sensorData + "\"");
            }
        } else {
            sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            if (sensor == null) {
                throw new IllegalStateException("cannot find light sensor");
            }
        }
        int rate = resources.getInteger(
                com.android.internal.R.integer.config_displayWhiteBalanceBrightnessSensorRate);
        return new AmbientSensor.AmbientBrightnessSensor(handler, sensorManager, sensor, rate);
    }

    /**
     * Creates an ambient color sensor instance to redirect sensor data to callbacks.
     */
    @VisibleForTesting
    public static AmbientSensor.AmbientColorTemperatureSensor createColorTemperatureSensor(
            Handler handler, SensorManager sensorManager, Resources resources,
            DisplayDeviceConfig displayDeviceConfig) {
        Sensor sensor = null;
        if (Flags.whiteBalanceControllerDdcConfig()) {
            // with flag cleanup, move this logic to AmbientSensor
            SensorData sensorData = displayDeviceConfig.getColorSensor();
            sensor = SensorUtils.findSensor(sensorManager, sensorData, NO_FALLBACK);
            if (sensor == null) {
                throw new IllegalStateException("cannot find color temperature sensor: "
                        + " sensorData" + sensorData + "\"");
            }
        } else {
            String name = resources.getString(
                    com.android.internal.R.string
                            .config_displayWhiteBalanceColorTemperatureSensorName);
            for (Sensor s : sensorManager.getSensorList(Sensor.TYPE_ALL)) {
                if (s.getStringType().equals(name)) {
                    sensor = s;
                    break;
                }
            }
            if (sensor == null) {
                throw new IllegalStateException("cannot find sensor " + name);
            }
        }
        int rate = resources.getInteger(
                com.android.internal.R.integer
                .config_displayWhiteBalanceColorTemperatureSensorRate);
        return new AmbientSensor.AmbientColorTemperatureSensor(handler, sensorManager, sensor,
                rate);
    }
    private static DisplayWhiteBalanceThrottler createThrottler(Resources resources) {
        final int increaseDebounce = resources.getInteger(
                com.android.internal.R.integer.config_displayWhiteBalanceDecreaseDebounce);
        final int decreaseDebounce = resources.getInteger(
                com.android.internal.R.integer.config_displayWhiteBalanceIncreaseDebounce);
        final float[] baseThresholds = getFloatArray(resources,
                com.android.internal.R.array.config_displayWhiteBalanceBaseThresholds);
        final float[] increaseThresholds = getFloatArray(resources,
                com.android.internal.R.array.config_displayWhiteBalanceIncreaseThresholds);
        final float[] decreaseThresholds = getFloatArray(resources,
                com.android.internal.R.array.config_displayWhiteBalanceDecreaseThresholds);
        return new DisplayWhiteBalanceThrottler(increaseDebounce, decreaseDebounce, baseThresholds,
                increaseThresholds, decreaseThresholds);
    }

    private static float getFloat(Resources resources, int id) {
        TypedValue value = new TypedValue();
        resources.getValue(id, value, true /* resolveRefs */);
        if (value.type != TypedValue.TYPE_FLOAT) {
            return Float.NaN;
        }
        return value.getFloat();
    }

    private static float[] getFloatArray(Resources resources, int id) {
        TypedArray array = resources.obtainTypedArray(id);
        try {
            if (array.length() == 0) {
                return null;
            }
            float[] values = new float[array.length()];
            for (int i = 0; i < values.length; i++) {
                values[i] = array.getFloat(i, Float.NaN);
                if (Float.isNaN(values[i])) {
                    return null;
                }
            }
            return values;
        } finally {
            array.recycle();
        }
    }

}
