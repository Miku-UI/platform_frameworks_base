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

import static com.android.server.display.TestUtilsKt.createSensor;
import static com.android.server.display.config.DisplayDeviceConfigTestUtilsKt.createSensorData;
import static com.android.server.display.utils.TestUtilsKt.createLastValueAmbientFilter;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.res.TypedArray;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.testing.TestableContext;
import android.testing.TestableResources;
import android.util.TypedValue;

import com.android.internal.R;
import com.android.internal.util.test.LocalServiceKeeperRule;
import com.android.server.display.DisplayDeviceConfig;
import com.android.server.display.color.ColorDisplayService;
import com.android.server.display.config.SensorData;
import com.android.server.display.feature.flags.Flags;
import com.android.server.display.utils.AmbientFilter;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

@RunWith(JUnit4.class)
public final class AmbientLuxTest {
    private static final float ALLOWED_ERROR_DELTA = 0.001f;
    private static final int AMBIENT_COLOR_TYPE = 20705;
    private static final String AMBIENT_COLOR_TYPE_STR = "colorSensor";
    private static final String LIGHT_TYPE_STR = "lightSensor";
    private static final float LOW_LIGHT_AMBIENT_COLOR_TEMPERATURE = 5432.1f;
    private static final float LOW_LIGHT_AMBIENT_COLOR_TEMPERATURE_STRONG = 5555.5f;
    private static final float HIGH_LIGHT_AMBIENT_COLOR_TEMPERATURE = 3456.7f;
    private static final float HIGH_LIGHT_AMBIENT_COLOR_TEMPERATURE_STRONG = 3333.3f;

    private Handler mHandler = new Handler(Looper.getMainLooper());
    private SensorData mLightSensorData = createSensorData(LIGHT_TYPE_STR);
    private SensorData mColorSensorData = createSensorData(AMBIENT_COLOR_TYPE_STR);
    private Sensor mLightSensor = createSensor(Sensor.TYPE_LIGHT, LIGHT_TYPE_STR);
    private Sensor mColorSensor = createSensor(AMBIENT_COLOR_TYPE, AMBIENT_COLOR_TYPE_STR);

    private SensorManager mSensorManagerMock = mock(SensorManager.class);
    private DisplayDeviceConfig mDisplayDeviceConfigMock = mock(DisplayDeviceConfig.class);
    private TypedArray mBrightnesses = mock(TypedArray.class);
    private TypedArray mBiases = mock(TypedArray.class);
    private TypedArray mHighLightBrightnesses = mock(TypedArray.class);
    private TypedArray mHighLightBiases = mock(TypedArray.class);
    private TypedArray mBrightnessesStrong = mock(TypedArray.class);
    private TypedArray mBiasesStrong = mock(TypedArray.class);
    private TypedArray mHighLightBrightnessesStrong = mock(TypedArray.class);
    private TypedArray mHighLightBiasesStrong = mock(TypedArray.class);
    private TypedArray mAmbientColorTemperatures = mock(TypedArray.class);
    private TypedArray mDisplayColorTemperatures = mock(TypedArray.class);
    private TypedArray mStrongAmbientColorTemperatures = mock(TypedArray.class);
    private TypedArray mStrongDisplayColorTemperatures = mock(TypedArray.class);
    private ColorDisplayService.ColorDisplayServiceInternal mColorDisplayServiceInternalMock = mock(
            ColorDisplayService.ColorDisplayServiceInternal.class);

    @Rule
    public LocalServiceKeeperRule mLocalServiceKeeperRule = new LocalServiceKeeperRule();

    @Rule
    public TestableContext mTestableContext = new TestableContext(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getContext());

    @Rule
    public SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Before
    public void setUp() throws Exception {
        when(mSensorManagerMock.getSensorList(Sensor.TYPE_ALL)).thenReturn(
                List.of(mLightSensor, mColorSensor));
        when(mDisplayDeviceConfigMock.getColorSensor()).thenReturn(mColorSensorData);
        when(mDisplayDeviceConfigMock.getAmbientLightSensor()).thenReturn(mLightSensorData);

        TestableResources testableResources = mTestableContext.getOrCreateTestableResources();
        testableResources.addOverride(
                R.integer.config_displayWhiteBalanceDecreaseDebounce, 0);
        testableResources.addOverride(
                R.integer.config_displayWhiteBalanceIncreaseDebounce, 0);
        testableResources.addOverride(
                R.array.config_displayWhiteBalanceAmbientColorTemperatures,
                mAmbientColorTemperatures);
        testableResources.addOverride(
                R.array.config_displayWhiteBalanceDisplayColorTemperatures,
                mDisplayColorTemperatures);
        testableResources.addOverride(
                R.array.config_displayWhiteBalanceStrongAmbientColorTemperatures,
                mStrongAmbientColorTemperatures);
        testableResources.addOverride(
                R.array.config_displayWhiteBalanceStrongDisplayColorTemperatures,
                mStrongDisplayColorTemperatures);
        testableResources.addOverride(
                R.array.config_displayWhiteBalanceLowLightAmbientBrightnesses,
                mBrightnesses);
        testableResources.addOverride(
                R.array.config_displayWhiteBalanceLowLightAmbientBiases, mBiases);
        testableResources.addOverride(
                R.array.config_displayWhiteBalanceHighLightAmbientBrightnesses,
                mHighLightBrightnesses);
        testableResources.addOverride(
                R.array.config_displayWhiteBalanceHighLightAmbientBiases,
                mHighLightBiases);
        testableResources.addOverride(
                R.array.config_displayWhiteBalanceLowLightAmbientBrightnessesStrong,
                mBrightnessesStrong);
        testableResources.addOverride(
                R.array.config_displayWhiteBalanceLowLightAmbientBiasesStrong,
                mBiasesStrong);
        testableResources.addOverride(
                R.array.config_displayWhiteBalanceHighLightAmbientBrightnessesStrong,
                mHighLightBrightnessesStrong);
        testableResources.addOverride(
                R.array.config_displayWhiteBalanceHighLightAmbientBiasesStrong,
                mHighLightBiasesStrong);
        testableResources.addOverride(
                R.dimen.config_displayWhiteBalanceLowLightAmbientColorTemperature,
                getFloatTypedValue(LOW_LIGHT_AMBIENT_COLOR_TEMPERATURE));
        testableResources.addOverride(
                R.dimen.config_displayWhiteBalanceHighLightAmbientColorTemperature,
                getFloatTypedValue(HIGH_LIGHT_AMBIENT_COLOR_TEMPERATURE));
        testableResources.addOverride(
                R.dimen.config_displayWhiteBalanceLowLightAmbientColorTemperatureStrong,
                getFloatTypedValue(LOW_LIGHT_AMBIENT_COLOR_TEMPERATURE_STRONG));
        testableResources.addOverride(
                R.dimen.config_displayWhiteBalanceHighLightAmbientColorTemperatureStrong,
                getFloatTypedValue(HIGH_LIGHT_AMBIENT_COLOR_TEMPERATURE_STRONG));

        mockThrottler(testableResources);

        mLocalServiceKeeperRule.overrideLocalService(
                ColorDisplayService.ColorDisplayServiceInternal.class,
                mColorDisplayServiceInternalMock);
    }

    @Test
    @EnableFlags(Flags.FLAG_WHITE_BALANCE_CONTROLLER_DDC_CONFIG)
    public void testCalculateAdjustedBrightnessNits() {
        doReturn(0.9f).when(mColorDisplayServiceInternalMock).getDisplayWhiteBalanceLuminance();
        DisplayWhiteBalanceController controller =
                DisplayWhiteBalanceFactory.create(mHandler, mSensorManagerMock,
                        mTestableContext.getResources(), mDisplayDeviceConfigMock);
        final float adjustedNits = controller.calculateAdjustedBrightnessNits(500f);
        assertEquals(/* expected= */ 550f, adjustedNits, /* delta= */ 0.001);
    }

    @Test
    @EnableFlags(Flags.FLAG_WHITE_BALANCE_CONTROLLER_DDC_CONFIG)
    public void testNoSpline() throws Exception {
        setBrightnesses();
        setBiases();

        DisplayWhiteBalanceController controller =
                DisplayWhiteBalanceFactory.create(mHandler, mSensorManagerMock,
                        mTestableContext.getResources(), mDisplayDeviceConfigMock);
        final float ambientColorTemperature = 8000.0f;
        setEstimatedColorTemperature(controller, ambientColorTemperature);
        controller.mBrightnessFilter = createLastValueAmbientFilter();

        for (float luxOverride = 0.1f; luxOverride <= 10000; luxOverride *= 10) {
            setEstimatedBrightnessAndUpdate(controller, luxOverride);
            assertEquals(controller.mPendingAmbientColorTemperature,
                    ambientColorTemperature, 0.001);
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_WHITE_BALANCE_CONTROLLER_DDC_CONFIG)
    public void testSpline_OneSegment() throws Exception {
        final float lowerBrightness = 10.0f;
        final float upperBrightness = 50.0f;
        setBrightnesses(lowerBrightness, upperBrightness);
        setBiases(0.0f, 1.0f);

        DisplayWhiteBalanceController controller =
                DisplayWhiteBalanceFactory.create(mHandler, mSensorManagerMock,
                        mTestableContext.getResources(), mDisplayDeviceConfigMock);
        final float ambientColorTemperature = 8000.0f;
        setEstimatedColorTemperature(controller, ambientColorTemperature);
        controller.mBrightnessFilter = createLastValueAmbientFilter();

        for (float t = 0.0f; t <= 1.0f; t += 0.1f) {
            setEstimatedBrightnessAndUpdate(controller,
                    mix(lowerBrightness, upperBrightness, t));
            assertEquals(controller.mPendingAmbientColorTemperature,
                    mix(LOW_LIGHT_AMBIENT_COLOR_TEMPERATURE, ambientColorTemperature, t), 0.001);
        }

        setEstimatedBrightnessAndUpdate(controller, 0.0f);
        assertEquals(controller.mPendingAmbientColorTemperature,
                LOW_LIGHT_AMBIENT_COLOR_TEMPERATURE, 0.001);

        setEstimatedBrightnessAndUpdate(controller, upperBrightness + 1.0f);
        assertEquals(controller.mPendingAmbientColorTemperature, ambientColorTemperature, 0.001);
    }

    @Test
    @EnableFlags(Flags.FLAG_WHITE_BALANCE_CONTROLLER_DDC_CONFIG)
    public void testSpline_TwoSegments() throws Exception {
        final float brightness0 = 10.0f;
        final float brightness1 = 50.0f;
        final float brightness2 = 60.0f;
        setBrightnesses(brightness0, brightness1, brightness2);
        final float bias0 = 0.0f;
        final float bias1 = 0.25f;
        final float bias2 = 1.0f;
        setBiases(bias0, bias1, bias2);

        DisplayWhiteBalanceController controller =
                DisplayWhiteBalanceFactory.create(mHandler, mSensorManagerMock,
                        mTestableContext.getResources(), mDisplayDeviceConfigMock);
        final float ambientColorTemperature = 8000.0f;
        setEstimatedColorTemperature(controller, ambientColorTemperature);
        controller.mBrightnessFilter = createLastValueAmbientFilter();

        for (float t = 0.0f; t <= 1.0f; t += 0.1f) {
            float luxOverride = mix(brightness0, brightness1, t);
            setEstimatedBrightnessAndUpdate(controller, luxOverride);
            float bias = mix(bias0, bias1, t);
            assertEquals(controller.mPendingAmbientColorTemperature,
                    mix(LOW_LIGHT_AMBIENT_COLOR_TEMPERATURE, ambientColorTemperature, bias), 0.001);
        }

        for (float t = 0.0f; t <= 1.0f; t += 0.1f) {
            float luxOverride = mix(brightness1, brightness2, t);
            setEstimatedBrightnessAndUpdate(controller, luxOverride);
            float bias = mix(bias1, bias2, t);
            assertEquals(controller.mPendingAmbientColorTemperature,
                    mix(LOW_LIGHT_AMBIENT_COLOR_TEMPERATURE, ambientColorTemperature, bias), 0.001);
        }

        setEstimatedBrightnessAndUpdate(controller, 0.0f);
        assertEquals(controller.mPendingAmbientColorTemperature,
                LOW_LIGHT_AMBIENT_COLOR_TEMPERATURE, 0.001);

        setEstimatedBrightnessAndUpdate(controller, brightness2 + 1.0f);
        assertEquals(controller.mPendingAmbientColorTemperature, ambientColorTemperature, 0.001);
    }

    @Test
    @EnableFlags(Flags.FLAG_WHITE_BALANCE_CONTROLLER_DDC_CONFIG)
    public void testSpline_VerticalSegment() throws Exception {
        final float lowerBrightness = 10.0f;
        final float upperBrightness = 10.0f;
        setBrightnesses(lowerBrightness, upperBrightness);
        setBiases(0.0f, 1.0f);

        DisplayWhiteBalanceController controller =
                DisplayWhiteBalanceFactory.create(mHandler, mSensorManagerMock,
                        mTestableContext.getResources(), mDisplayDeviceConfigMock);
        final float ambientColorTemperature = 8000.0f;
        setEstimatedColorTemperature(controller, ambientColorTemperature);
        controller.mBrightnessFilter = createLastValueAmbientFilter();

        setEstimatedBrightnessAndUpdate(controller, 0.0f);
        assertEquals(controller.mPendingAmbientColorTemperature,
                LOW_LIGHT_AMBIENT_COLOR_TEMPERATURE, 0.001);

        setEstimatedBrightnessAndUpdate(controller, upperBrightness + 1.0f);
        assertEquals(controller.mPendingAmbientColorTemperature, ambientColorTemperature, 0.001);
    }

    @Test
    @EnableFlags(Flags.FLAG_WHITE_BALANCE_CONTROLLER_DDC_CONFIG)
    public void testSpline_InvalidEndBias() throws Exception {
        setBrightnesses(10.0f, 1000.0f);
        setBiases(0.0f, 2.0f);

        DisplayWhiteBalanceController controller =
                DisplayWhiteBalanceFactory.create(mHandler, mSensorManagerMock,
                        mTestableContext.getResources(), mDisplayDeviceConfigMock);
        final float ambientColorTemperature = 8000.0f;
        setEstimatedColorTemperature(controller, ambientColorTemperature);
        controller.mBrightnessFilter = createLastValueAmbientFilter();

        for (float luxOverride = 0.1f; luxOverride <= 10000; luxOverride *= 10) {
            setEstimatedBrightnessAndUpdate(controller, luxOverride);
            assertEquals(controller.mPendingAmbientColorTemperature,
                    ambientColorTemperature, 0.001);
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_WHITE_BALANCE_CONTROLLER_DDC_CONFIG)
    public void testSpline_InvalidBeginBias() throws Exception {
        setBrightnesses(10.0f, 1000.0f);
        setBiases(0.1f, 1.0f);

        DisplayWhiteBalanceController controller =
                DisplayWhiteBalanceFactory.create(mHandler, mSensorManagerMock,
                        mTestableContext.getResources(), mDisplayDeviceConfigMock);
        final float ambientColorTemperature = 8000.0f;
        setEstimatedColorTemperature(controller, ambientColorTemperature);
        controller.mBrightnessFilter = createLastValueAmbientFilter();

        for (float luxOverride = 0.1f; luxOverride <= 10000; luxOverride *= 10) {
            setEstimatedBrightnessAndUpdate(controller, luxOverride);
            assertEquals(controller.mPendingAmbientColorTemperature,
                    ambientColorTemperature, 0.001);
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_WHITE_BALANCE_CONTROLLER_DDC_CONFIG)
    public void testSpline_OneSegmentHighLight() throws Exception {
        final float lowerBrightness = 10.0f;
        final float upperBrightness = 50.0f;
        setHighLightBrightnesses(lowerBrightness, upperBrightness);
        setHighLightBiases(0.0f, 1.0f);

        DisplayWhiteBalanceController controller =
                DisplayWhiteBalanceFactory.create(mHandler, mSensorManagerMock,
                        mTestableContext.getResources(), mDisplayDeviceConfigMock);
        final float ambientColorTemperature = 8000.0f;
        setEstimatedColorTemperature(controller, ambientColorTemperature);
        controller.mBrightnessFilter = createLastValueAmbientFilter();

        for (float t = 0.0f; t <= 1.0f; t += 0.1f) {
            setEstimatedBrightnessAndUpdate(controller,
                    mix(lowerBrightness, upperBrightness, t));
            assertEquals(controller.mPendingAmbientColorTemperature,
                    mix(HIGH_LIGHT_AMBIENT_COLOR_TEMPERATURE, ambientColorTemperature, 1.0f - t),
                    0.001);
        }

        setEstimatedBrightnessAndUpdate(controller, upperBrightness + 1.0f);
        assertEquals(controller.mPendingAmbientColorTemperature,
                HIGH_LIGHT_AMBIENT_COLOR_TEMPERATURE, 0.001);

        setEstimatedBrightnessAndUpdate(controller, 0.0f);
        assertEquals(controller.mPendingAmbientColorTemperature, ambientColorTemperature, 0.001);
    }

    @Test
    @EnableFlags(Flags.FLAG_WHITE_BALANCE_CONTROLLER_DDC_CONFIG)
    public void testSpline_TwoSegmentsHighLight() throws Exception {
        final float brightness0 = 10.0f;
        final float brightness1 = 50.0f;
        final float brightness2 = 60.0f;
        setHighLightBrightnesses(brightness0, brightness1, brightness2);
        final float bias0 = 0.0f;
        final float bias1 = 0.25f;
        final float bias2 = 1.0f;
        setHighLightBiases(bias0, bias1, bias2);

        DisplayWhiteBalanceController controller =
                DisplayWhiteBalanceFactory.create(mHandler, mSensorManagerMock,
                        mTestableContext.getResources(), mDisplayDeviceConfigMock);
        final float ambientColorTemperature = 6000.0f;
        setEstimatedColorTemperature(controller, ambientColorTemperature);
        controller.mBrightnessFilter = createLastValueAmbientFilter();

        for (float t = 0.0f; t <= 1.0f; t += 0.1f) {
            float luxOverride = mix(brightness0, brightness1, t);
            setEstimatedBrightnessAndUpdate(controller, luxOverride);
            float bias = mix(bias0, bias1, t);
            assertEquals(controller.mPendingAmbientColorTemperature,
                    mix(HIGH_LIGHT_AMBIENT_COLOR_TEMPERATURE, ambientColorTemperature, 1.0f - bias),
                    0.01);
        }

        for (float t = 0.0f; t <= 1.0f; t += 0.1f) {
            float luxOverride = mix(brightness1, brightness2, t);
            setEstimatedBrightnessAndUpdate(controller, luxOverride);
            float bias = mix(bias1, bias2, t);
            assertEquals(controller.mPendingAmbientColorTemperature,
                    mix(HIGH_LIGHT_AMBIENT_COLOR_TEMPERATURE, ambientColorTemperature, 1.0f - bias),
                    0.01);
        }

        setEstimatedBrightnessAndUpdate(controller, brightness2 + 1.0f);
        assertEquals(controller.mPendingAmbientColorTemperature,
                HIGH_LIGHT_AMBIENT_COLOR_TEMPERATURE, 0.001);

        setEstimatedBrightnessAndUpdate(controller, 0.0f);
        assertEquals(controller.mPendingAmbientColorTemperature, ambientColorTemperature, 0.001);
    }

    @Test
    @EnableFlags(Flags.FLAG_WHITE_BALANCE_CONTROLLER_DDC_CONFIG)
    public void testSpline_InvalidCombinations() throws Exception {
        setBrightnesses(100.0f, 200.0f);
        setBiases(0.0f, 1.0f);
        setHighLightBrightnesses(150.0f, 250.0f);
        setHighLightBiases(0.0f, 1.0f);

        DisplayWhiteBalanceController controller =
                DisplayWhiteBalanceFactory.create(mHandler, mSensorManagerMock,
                        mTestableContext.getResources(), mDisplayDeviceConfigMock);
        final float ambientColorTemperature = 8000.0f;
        setEstimatedColorTemperature(controller, ambientColorTemperature);
        controller.mBrightnessFilter = createLastValueAmbientFilter();

        for (float luxOverride = 0.1f; luxOverride <= 10000; luxOverride *= 10) {
            setEstimatedBrightnessAndUpdate(controller, luxOverride);
            assertEquals(controller.mPendingAmbientColorTemperature,
                    ambientColorTemperature, 0.001);
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_WHITE_BALANCE_CONTROLLER_DDC_CONFIG)
    public void testStrongMode() {
        final float lowerBrightness = 10.0f;
        final float upperBrightness = 50.0f;
        setBrightnessesStrong(lowerBrightness, upperBrightness);
        setBiasesStrong(0.0f, 1.0f);
        final int ambientColorTempLow = 6000;
        final int ambientColorTempHigh = 8000;
        final int displayColorTempLow = 6400;
        final int displayColorTempHigh = 7400;
        setStrongAmbientColorTemperatures(ambientColorTempLow, ambientColorTempHigh);
        setStrongDisplayColorTemperatures(displayColorTempLow, displayColorTempHigh);

        DisplayWhiteBalanceController controller =
                DisplayWhiteBalanceFactory.create(mHandler, mSensorManagerMock,
                        mTestableContext.getResources(), mDisplayDeviceConfigMock);
        controller.setStrongModeEnabled(true);
        controller.mBrightnessFilter = createLastValueAmbientFilter();

        for (float ambientTempFraction = 0.0f; ambientTempFraction <= 1.0f;
                ambientTempFraction += 0.1f) {
            final float ambientTemp =
                    (ambientColorTempHigh - ambientColorTempLow) * ambientTempFraction
                            + ambientColorTempLow;
            setEstimatedColorTemperature(controller, ambientTemp);
            for (float brightnessFraction = 0.0f; brightnessFraction <= 1.0f;
                    brightnessFraction += 0.1f) {
                setEstimatedBrightnessAndUpdate(controller,
                        mix(lowerBrightness, upperBrightness, brightnessFraction));
                assertEquals(controller.mPendingAmbientColorTemperature,
                        mix(LOW_LIGHT_AMBIENT_COLOR_TEMPERATURE_STRONG,
                                mix(displayColorTempLow, displayColorTempHigh, ambientTempFraction),
                                brightnessFraction),
                        ALLOWED_ERROR_DELTA);
            }
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_WHITE_BALANCE_CONTROLLER_DDC_CONFIG)
    public void testLowLight_DefaultAmbient() throws Exception {
        final float lowerBrightness = 10.0f;
        final float upperBrightness = 50.0f;
        setBrightnesses(lowerBrightness, upperBrightness);
        setBiases(0.0f, 1.0f);

        DisplayWhiteBalanceController controller =
                DisplayWhiteBalanceFactory.create(mHandler, mSensorManagerMock,
                        mTestableContext.getResources(), mDisplayDeviceConfigMock);
        final float ambientColorTemperature = -1.0f;
        setEstimatedColorTemperature(controller, ambientColorTemperature);
        controller.mBrightnessFilter = createLastValueAmbientFilter();

        for (float t = 0.0f; t <= 1.0f; t += 0.1f) {
            setEstimatedBrightnessAndUpdate(controller,
                    mix(lowerBrightness, upperBrightness, t));
            assertEquals(controller.mPendingAmbientColorTemperature, ambientColorTemperature,
                        0.001);
        }

        setEstimatedBrightnessAndUpdate(controller, 0.0f);
        assertEquals(controller.mPendingAmbientColorTemperature, ambientColorTemperature, 0.001);

        setEstimatedBrightnessAndUpdate(controller, upperBrightness + 1.0f);
        assertEquals(controller.mPendingAmbientColorTemperature, ambientColorTemperature, 0.001);
    }

    @Test
    @EnableFlags(Flags.FLAG_WHITE_BALANCE_CONTROLLER_DDC_CONFIG)
    public void testWhiteBalance_updateWithEmptyFilter() throws Exception {
        setAmbientColorTemperatures(5300.0f, 6000.0f, 7000.0f, 8000.0f);
        setDisplayColorTemperatures(6300.0f, 6400.0f, 6850.0f, 7450.0f);
        DisplayWhiteBalanceController controller =
                DisplayWhiteBalanceFactory.create(mHandler, mSensorManagerMock,
                        mTestableContext.getResources(), mDisplayDeviceConfigMock);
        controller.updateAmbientColorTemperature();
        assertEquals(-1.0f, controller.mPendingAmbientColorTemperature, 0);
    }

    private void mockThrottler(TestableResources testableResources) {
        TypedArray mockTypedArray = mock(TypedArray.class);
        setFloatArrayResource(mockTypedArray, new float[]{0.0f});
        testableResources.addOverride(
                R.array.config_displayWhiteBalanceBaseThresholds, mockTypedArray);
        testableResources.addOverride(
                R.array.config_displayWhiteBalanceIncreaseThresholds, mockTypedArray);
        testableResources.addOverride(
                R.array.config_displayWhiteBalanceDecreaseThresholds, mockTypedArray);
    }

    private void setEstimatedColorTemperature(DisplayWhiteBalanceController controller,
                                              float ambientColorTemperature) {
        AmbientFilter colorTemperatureFilter = createLastValueAmbientFilter();
        controller.mColorTemperatureFilter = colorTemperatureFilter;
        colorTemperatureFilter.addValue(System.currentTimeMillis(), ambientColorTemperature);
    }

    private void setEstimatedBrightnessAndUpdate(DisplayWhiteBalanceController controller,
                                                 float brightness) {
        controller.mBrightnessFilter.addValue(System.currentTimeMillis(), brightness);
        controller.updateAmbientColorTemperature();
    }

    private void setBrightnesses(float... vals) {
        setFloatArrayResource(mBrightnesses, vals);
    }

    private void setBrightnessesStrong(float... vals) {
        setFloatArrayResource(mBrightnessesStrong, vals);
    }

    private void setBiases(float... vals) {
        setFloatArrayResource(mBiases, vals);
    }

    private void setBiasesStrong(float... vals) {
        setFloatArrayResource(mBiasesStrong, vals);
    }

    private void setHighLightBrightnesses(float... vals) {
        setFloatArrayResource(mHighLightBrightnesses, vals);
    }

    private void setHighLightBiases(float... vals) {
        setFloatArrayResource(mHighLightBiases, vals);
    }

    private void setAmbientColorTemperatures(float... vals) {
        setFloatArrayResource(mAmbientColorTemperatures, vals);
    }

    private void setDisplayColorTemperatures(float... vals) {
        setFloatArrayResource(mDisplayColorTemperatures, vals);
    }

    private void setStrongAmbientColorTemperatures(float... vals) {
        setFloatArrayResource(mStrongAmbientColorTemperatures, vals);
    }

    private void setStrongDisplayColorTemperatures(float... vals) {
        setFloatArrayResource(mStrongDisplayColorTemperatures, vals);
    }

    private void setFloatArrayResource(TypedArray array, float[] vals) {
        when(array.length()).thenReturn(vals.length);
        for (int i = 0; i < vals.length; i++) {
            when(array.getFloat(i, Float.NaN)).thenReturn(vals[i]);
        }
    }

    private TypedValue getFloatTypedValue(float value) {
        TypedValue typedValue = new TypedValue();
        typedValue.type = TypedValue.TYPE_FLOAT;
        typedValue.data = Float.floatToIntBits(value);
        return typedValue;
    }


    private static float mix(float a, float b, float t) {
        return (1.0f - t) * a + t * b;
    }
}
