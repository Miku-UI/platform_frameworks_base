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
import static com.android.server.display.TestUtilsKt.createSensorEvent;
import static com.android.server.display.config.DisplayDeviceConfigTestUtilsKt.createSensorData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.testing.TestableContext;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.display.DisplayDeviceConfig;
import com.android.server.display.config.SensorData;
import com.android.server.display.feature.flags.Flags;
import com.android.server.display.utils.SensorUtils;
import com.android.server.display.whitebalance.AmbientSensor.AmbientBrightnessSensor;
import com.android.server.display.whitebalance.AmbientSensor.AmbientColorTemperatureSensor;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public final class AmbientSensorTest {
    private static final int AMBIENT_COLOR_TYPE = 20705;
    private static final String AMBIENT_COLOR_TYPE_STR = "colorSensor";
    private static final String LIGHT_TYPE_STR = "lightSensor";

    @Rule
    public TestableContext mTestableContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getContext());

    @Rule
    public SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private Handler mHandler = new Handler(Looper.getMainLooper());

    private SensorData mLightSensorData = createSensorData(LIGHT_TYPE_STR);
    private SensorData mColorSensorData = createSensorData(AMBIENT_COLOR_TYPE_STR);
    private Sensor mLightSensor = createSensor(Sensor.TYPE_LIGHT, LIGHT_TYPE_STR);
    private Sensor mColorSensor = createSensor(AMBIENT_COLOR_TYPE, AMBIENT_COLOR_TYPE_STR);

    private SensorManager mSensorManagerMock = mock(SensorManager.class);
    private DisplayDeviceConfig mDisplayDeviceConfigMock = mock(DisplayDeviceConfig.class);

    @Before
    public void setUp() throws Exception {
        when(mSensorManagerMock.getSensorList(Sensor.TYPE_ALL)).thenReturn(
                List.of(mLightSensor, mColorSensor));
        when(mDisplayDeviceConfigMock.getColorSensor()).thenReturn(mColorSensorData);
        when(mDisplayDeviceConfigMock.getAmbientLightSensor()).thenReturn(mLightSensorData);
    }

    @Test
    @EnableFlags(Flags.FLAG_WHITE_BALANCE_CONTROLLER_DDC_CONFIG)
    public void testAmbientBrightnessSensorCallback_NoCallbacks() {
        AmbientBrightnessSensor abs = DisplayWhiteBalanceFactory.createBrightnessSensor(
                mHandler, mSensorManagerMock, mTestableContext.getResources(),
                mDisplayDeviceConfigMock);

        abs.setCallbacks(null);
        abs.setEnabled(true);
        ArgumentCaptor<SensorEventListener> captor =
                ArgumentCaptor.forClass(SensorEventListener.class);
        verify(mSensorManagerMock).registerListener(captor.capture(), isA(Sensor.class), anyInt(),
                isA(Handler.class));

        // There should be no issues when we callback the listener, even if there is no callback
        // set.
        SensorEventListener listener = captor.getValue();
        listener.onSensorChanged(createSensorEvent(mLightSensor, 100));
    }

    @Test
    @EnableFlags(Flags.FLAG_WHITE_BALANCE_CONTROLLER_DDC_CONFIG)
    public void testAmbientBrightnessSensorCallback_CallbacksCalled() throws Exception {
        final int luxValue = 83;
        AmbientBrightnessSensor abs = DisplayWhiteBalanceFactory.createBrightnessSensor(
                mHandler, mSensorManagerMock, mTestableContext.getResources(),
                mDisplayDeviceConfigMock);

        final int[] luxReturned = new int[] { -1 };
        final CountDownLatch  changeSignal = new CountDownLatch(1);
        abs.setCallbacks(value -> {
            luxReturned[0] = (int) value;
            changeSignal.countDown();
        });

        abs.setEnabled(true);
        ArgumentCaptor<SensorEventListener> captor =
                ArgumentCaptor.forClass(SensorEventListener.class);
        verify(mSensorManagerMock).registerListener(captor.capture(), eq(mLightSensor),
                anyInt(), eq(mHandler));
        SensorEventListener listener = captor.getValue();
        listener.onSensorChanged(createSensorEvent(mLightSensor, luxValue));
        assertTrue(changeSignal.await(5, TimeUnit.SECONDS));
        assertEquals(luxValue, luxReturned[0]);
    }

    @Test
    @EnableFlags(Flags.FLAG_WHITE_BALANCE_CONTROLLER_DDC_CONFIG)
    public void testAmbientColorTemperatureSensorCallback_CallbacksCalled() throws Exception {
        final int colorTempValue = 79;
        AmbientColorTemperatureSensor abs = DisplayWhiteBalanceFactory
                .createColorTemperatureSensor(mHandler, mSensorManagerMock,
                        mTestableContext.getResources(), mDisplayDeviceConfigMock);

        final int[] colorTempReturned = new int[] { -1 };
        final CountDownLatch changeSignal = new CountDownLatch(1);
        abs.setCallbacks(value -> {
            colorTempReturned[0] = (int) value;
            changeSignal.countDown();
        });

        abs.setEnabled(true);
        ArgumentCaptor<SensorEventListener> captor =
                ArgumentCaptor.forClass(SensorEventListener.class);
        verify(mSensorManagerMock).registerListener(captor.capture(), eq(mColorSensor),
                anyInt(), eq(mHandler));
        SensorEventListener listener = captor.getValue();
        listener.onSensorChanged(createSensorEvent(mColorSensor, colorTempValue));
        assertTrue(changeSignal.await(5, TimeUnit.SECONDS));
        assertEquals(colorTempValue, colorTempReturned[0]);
    }

    @Test
    @EnableFlags(Flags.FLAG_WHITE_BALANCE_CONTROLLER_DDC_CONFIG)
    public void testSetSensorData_resubscribeSensor() {
        String otherSensorType = "otherSensorType";
        SensorData otherSensorData = createSensorData(otherSensorType);
        Sensor otherSensor = createSensor(0, otherSensorType);
        when(mSensorManagerMock.getSensorList(Sensor.TYPE_ALL)).thenReturn(
                List.of(mLightSensor, mColorSensor, otherSensor));
        AmbientBrightnessSensor abs = DisplayWhiteBalanceFactory.createBrightnessSensor(
                mHandler, mSensorManagerMock, mTestableContext.getResources(),
                mDisplayDeviceConfigMock);

        abs.setEnabled(true);
        verify(mSensorManagerMock).registerListener(any(SensorEventListener.class),
                eq(mLightSensor), anyInt(), isA(Handler.class));

        abs.setSensorData(otherSensorData, SensorUtils.NO_FALLBACK);

        InOrder inOrder = Mockito.inOrder(mSensorManagerMock);
        inOrder.verify(mSensorManagerMock).registerListener(any(SensorEventListener.class),
                eq(otherSensor), anyInt(), isA(Handler.class));
        inOrder.verify(mSensorManagerMock)
                .unregisterListener(any(SensorEventListener.class), eq(mLightSensor));
    }
}
