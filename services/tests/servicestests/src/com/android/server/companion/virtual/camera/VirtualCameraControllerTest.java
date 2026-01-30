/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.server.companion.virtual.camera;

import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_DEFAULT;
import static android.companion.virtual.camera.VirtualCameraConfig.SENSOR_ORIENTATION_0;
import static android.companion.virtual.camera.VirtualCameraConfig.SENSOR_ORIENTATION_90;
import static android.graphics.ImageFormat.YUV_420_888;
import static android.graphics.PixelFormat.RGBA_8888;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_BACK;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_EXTERNAL;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;

import static com.android.server.companion.virtual.camera.VirtualCameraConversionUtil.getServiceCameraConfiguration;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.companion.virtual.camera.VirtualCameraCallback;
import android.companion.virtual.camera.VirtualCameraConfig;
import android.companion.virtual.camera.VirtualCameraStreamConfig;
import android.companion.virtualcamera.IVirtualCameraService;
import android.companion.virtualcamera.VirtualCameraConfiguration;
import android.companion.virtualcamera.VirtualCameraMetadata;
import android.companion.virtualdevice.flags.Flags;
import android.content.AttributionSource;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.Looper;
import android.os.RemoteException;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.testing.TestableLooper;

import com.google.common.collect.Iterables;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@Presubmit
@RunWith(JUnitParamsRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class VirtualCameraControllerTest {

    private static final int DEVICE_ID = 5;
    private static final String CAMERA_NAME_1 = "Virtual camera 1";
    private static final int CAMERA_WIDTH_1 = 100;
    private static final int CAMERA_HEIGHT_1 = 200;
    private static final int CAMERA_FORMAT_1 = YUV_420_888;
    private static final int CAMERA_MAX_FPS_1 = 30;
    private static final int CAMERA_SENSOR_ORIENTATION_1 = SENSOR_ORIENTATION_0;
    private static final int CAMERA_LENS_FACING_1 = LENS_FACING_BACK;

    private static final String CAMERA_NAME_2 = "Virtual camera 2";
    private static final int CAMERA_WIDTH_2 = 400;
    private static final int CAMERA_HEIGHT_2 = 600;
    private static final int CAMERA_FORMAT_2 = RGBA_8888;
    private static final int CAMERA_MAX_FPS_2 = 60;
    private static final int CAMERA_SENSOR_ORIENTATION_2 = SENSOR_ORIENTATION_90;
    private static final int CAMERA_LENS_FACING_2 = LENS_FACING_FRONT;

    @Mock
    private IVirtualCameraService mVirtualCameraServiceMock;
    @Mock
    private VirtualCameraCallback mVirtualCameraCallbackMock;

    private VirtualCameraController mVirtualCameraController;
    private final HandlerExecutor mCallbackHandler =
            new HandlerExecutor(new Handler(Looper.getMainLooper()));

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mVirtualCameraController = new VirtualCameraController(mVirtualCameraServiceMock,
                DEVICE_POLICY_CUSTOM, DEVICE_ID);
        when(mVirtualCameraServiceMock.registerCamera(any(), any(), anyInt())).thenReturn(true);
        when(mVirtualCameraServiceMock.getCameraId(any())).thenReturn("0");
    }

    @After
    public void tearDown() throws Exception {
        mVirtualCameraController.close();
    }

    @Parameters(method = "getAllLensFacingDirections")
    @Test
    public void registerCamera_registersCamera(int lensFacing) throws Exception {
        mVirtualCameraController.registerCamera(createVirtualCameraConfig(
                CAMERA_WIDTH_1, CAMERA_HEIGHT_1, CAMERA_FORMAT_1, CAMERA_MAX_FPS_1, CAMERA_NAME_1,
                CAMERA_SENSOR_ORIENTATION_1, lensFacing), AttributionSource.myAttributionSource());

        ArgumentCaptor<VirtualCameraConfiguration> configurationCaptor =
                ArgumentCaptor.forClass(VirtualCameraConfiguration.class);
        ArgumentCaptor<Integer> deviceIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mVirtualCameraServiceMock).registerCamera(any(), configurationCaptor.capture(),
                deviceIdCaptor.capture());
        assertThat(deviceIdCaptor.getValue()).isEqualTo(DEVICE_ID);
        VirtualCameraConfiguration virtualCameraConfiguration = configurationCaptor.getValue();
        assertThat(virtualCameraConfiguration.supportedStreamConfigs.length).isEqualTo(1);
        assertVirtualCameraConfiguration(virtualCameraConfiguration, CAMERA_WIDTH_1,
                CAMERA_HEIGHT_1, CAMERA_FORMAT_1, CAMERA_MAX_FPS_1, CAMERA_SENSOR_ORIENTATION_1,
                lensFacing);
    }

    @Test
    public void unregisterCamera_unregistersCamera() throws Exception {
        VirtualCameraConfig config = createVirtualCameraConfig(
                CAMERA_WIDTH_1, CAMERA_HEIGHT_1, CAMERA_FORMAT_1, CAMERA_MAX_FPS_1, CAMERA_NAME_1,
                CAMERA_SENSOR_ORIENTATION_1, CAMERA_LENS_FACING_1);
        mVirtualCameraController.registerCamera(config, AttributionSource.myAttributionSource());

        mVirtualCameraController.unregisterCamera(config);

        verify(mVirtualCameraServiceMock).unregisterCamera(any());
    }

    @Test
    public void close_unregistersAllCameras() throws Exception {
        mVirtualCameraController.registerCamera(createVirtualCameraConfig(
                        CAMERA_WIDTH_1, CAMERA_HEIGHT_1, CAMERA_FORMAT_1, CAMERA_MAX_FPS_1,
                        CAMERA_NAME_1,
                        CAMERA_SENSOR_ORIENTATION_1, CAMERA_LENS_FACING_1),
                AttributionSource.myAttributionSource());
        mVirtualCameraController.registerCamera(createVirtualCameraConfig(
                        CAMERA_WIDTH_2, CAMERA_HEIGHT_2, CAMERA_FORMAT_2, CAMERA_MAX_FPS_2,
                        CAMERA_NAME_2,
                        CAMERA_SENSOR_ORIENTATION_2, CAMERA_LENS_FACING_2),
                AttributionSource.myAttributionSource());

        mVirtualCameraController.close();

        ArgumentCaptor<VirtualCameraConfiguration> configurationCaptor =
                ArgumentCaptor.forClass(VirtualCameraConfiguration.class);
        ArgumentCaptor<Integer> deviceIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mVirtualCameraServiceMock, times(2)).registerCamera(any(),
                configurationCaptor.capture(), deviceIdCaptor.capture());
        List<Integer> deviceIds = deviceIdCaptor.getAllValues();
        assertThat(deviceIds).containsExactly(DEVICE_ID, DEVICE_ID);
        List<VirtualCameraConfiguration> virtualCameraConfigurations =
                configurationCaptor.getAllValues();
        assertThat(virtualCameraConfigurations).hasSize(2);
        assertVirtualCameraConfiguration(virtualCameraConfigurations.get(0), CAMERA_WIDTH_1,
                CAMERA_HEIGHT_1, CAMERA_FORMAT_1, CAMERA_MAX_FPS_1, CAMERA_SENSOR_ORIENTATION_1,
                CAMERA_LENS_FACING_1);
        assertVirtualCameraConfiguration(virtualCameraConfigurations.get(1), CAMERA_WIDTH_2,
                CAMERA_HEIGHT_2, CAMERA_FORMAT_2, CAMERA_MAX_FPS_2, CAMERA_SENSOR_ORIENTATION_2,
                CAMERA_LENS_FACING_2);
    }

    @Parameters(method = "getFixedCamerasLensFacingDirections")
    @Test
    public void registerMultipleSameLensFacingCameras_withCustomCameraPolicy_throwsException(
            int lensFacing) {
        mVirtualCameraController.registerCamera(createVirtualCameraConfig(
                CAMERA_WIDTH_1, CAMERA_HEIGHT_1, CAMERA_FORMAT_1, CAMERA_MAX_FPS_1, CAMERA_NAME_1,
                CAMERA_SENSOR_ORIENTATION_1, lensFacing), AttributionSource.myAttributionSource());
        assertThrows(IllegalArgumentException.class,
                () -> mVirtualCameraController.registerCamera(createVirtualCameraConfig(
                                CAMERA_WIDTH_2, CAMERA_HEIGHT_2, CAMERA_FORMAT_2, CAMERA_MAX_FPS_2,
                                CAMERA_NAME_2, CAMERA_SENSOR_ORIENTATION_2, lensFacing),
                        AttributionSource.myAttributionSource()));
    }

    @Test
    @EnableFlags(Flags.FLAG_EXTERNAL_VIRTUAL_CAMERAS)
    public void registerMultipleExternalCameras_withCustomCameraPolicy_succeeds() {
        mVirtualCameraController.registerCamera(
                createVirtualCameraConfig(CAMERA_WIDTH_1, CAMERA_HEIGHT_1, CAMERA_FORMAT_1,
                        CAMERA_MAX_FPS_1, CAMERA_NAME_1, CAMERA_SENSOR_ORIENTATION_1,
                        LENS_FACING_EXTERNAL), AttributionSource.myAttributionSource());

        mVirtualCameraController.registerCamera(
                createVirtualCameraConfig(CAMERA_WIDTH_2, CAMERA_HEIGHT_2, CAMERA_FORMAT_2,
                        CAMERA_MAX_FPS_2, CAMERA_NAME_2, CAMERA_SENSOR_ORIENTATION_2,
                        LENS_FACING_EXTERNAL), AttributionSource.myAttributionSource());
    }

    @Test
    @DisableFlags(Flags.FLAG_EXTERNAL_VIRTUAL_CAMERAS)
    public void registerExternalCameras_withCustomCameraPolicy_throwsException_whenNotSupported() {
        assertThrows(IllegalArgumentException.class,
                () -> mVirtualCameraController.registerCamera(
                createVirtualCameraConfig(CAMERA_WIDTH_1, CAMERA_HEIGHT_1, CAMERA_FORMAT_1,
                        CAMERA_MAX_FPS_1, CAMERA_NAME_1, CAMERA_SENSOR_ORIENTATION_1,
                        LENS_FACING_EXTERNAL), AttributionSource.myAttributionSource()));
    }

    @Parameters(method = "getFixedCamerasLensFacingDirections")
    @Test
    public void registerCamera_withDefaultCameraPolicy_throwsException(int lensFacing) {
        mVirtualCameraController.close();
        mVirtualCameraController = new VirtualCameraController(mVirtualCameraServiceMock,
                DEVICE_POLICY_DEFAULT, DEVICE_ID);

        assertThrows(IllegalArgumentException.class,
                () -> mVirtualCameraController.registerCamera(createVirtualCameraConfig(
                                CAMERA_WIDTH_1, CAMERA_HEIGHT_1, CAMERA_FORMAT_1, CAMERA_MAX_FPS_1,
                                CAMERA_NAME_1, CAMERA_SENSOR_ORIENTATION_1, lensFacing),
                        AttributionSource.myAttributionSource()));
    }

    @Test
    @EnableFlags({Flags.FLAG_EXTERNAL_VIRTUAL_CAMERAS, Flags.FLAG_EXTERNAL_CAMERA_DEFAULT_POLICY})
    public void registerCamera_withDefaultCameraPolicy_allowsMultipleExternal() {
        mVirtualCameraController.close();
        mVirtualCameraController = new VirtualCameraController(mVirtualCameraServiceMock,
                DEVICE_POLICY_DEFAULT, DEVICE_ID);

        mVirtualCameraController.registerCamera(
                createVirtualCameraConfig(CAMERA_WIDTH_1, CAMERA_HEIGHT_1, CAMERA_FORMAT_1,
                        CAMERA_MAX_FPS_1, CAMERA_NAME_1, CAMERA_SENSOR_ORIENTATION_1,
                        LENS_FACING_EXTERNAL), AttributionSource.myAttributionSource());

        mVirtualCameraController.registerCamera(
                createVirtualCameraConfig(CAMERA_WIDTH_2, CAMERA_HEIGHT_2, CAMERA_FORMAT_2,
                        CAMERA_MAX_FPS_2, CAMERA_NAME_2, CAMERA_SENSOR_ORIENTATION_2,
                        LENS_FACING_EXTERNAL), AttributionSource.myAttributionSource());
    }

    @Test
    @DisableFlags(Flags.FLAG_EXTERNAL_VIRTUAL_CAMERAS)
    public void registerCamera_withDefaultCameraPolicy_throwsException_whenExternalNotSupported() {
        verifyRegisterDefaultPolicyCameraThrowsException();
    }

    @Test
    @DisableFlags(Flags.FLAG_EXTERNAL_CAMERA_DEFAULT_POLICY)
    public void registerCamera_withDefaultCameraPolicy_throwsException_whenDefaultNotSupported() {
        verifyRegisterDefaultPolicyCameraThrowsException();
    }

    public static void assertVirtualCameraConfigFromCharacteristics(VirtualCameraConfig config,
            int width, int height, int format, int maximumFramesPerSecond,
            int characteristicSensorOrientation, int characteristicLensFacing, String name) {
        assertThat(config.getName()).isEqualTo(name);
        assertThat(config.getStreamConfigs()).hasSize(1);
        VirtualCameraStreamConfig streamConfig =
                Iterables.getOnlyElement(config.getStreamConfigs());
        assertThat(streamConfig.getWidth()).isEqualTo(width);
        assertThat(streamConfig.getHeight()).isEqualTo(height);
        assertThat(streamConfig.getFormat()).isEqualTo(format);
        assertThat(streamConfig.getMaximumFramesPerSecond()).isEqualTo(maximumFramesPerSecond);
        assertThat(config.getCameraCharacteristics().get(CameraCharacteristics.SENSOR_ORIENTATION))
                .isEqualTo(characteristicSensorOrientation);
        assertThat(config.getCameraCharacteristics().get(CameraCharacteristics.LENS_FACING))
                .isEqualTo(characteristicLensFacing);
    }

    private static void assertVirtualCameraConfigurationWithCharacteristics(
            VirtualCameraConfiguration configuration, int width, int height, int format,
            int maxFps, VirtualCameraMetadata expectedMetadata) {
        assertThat(configuration.supportedStreamConfigs[0].width).isEqualTo(width);
        assertThat(configuration.supportedStreamConfigs[0].height).isEqualTo(height);
        assertThat(configuration.supportedStreamConfigs[0].pixelFormat).isEqualTo(format);
        assertThat(configuration.supportedStreamConfigs[0].maxFps).isEqualTo(maxFps);
        assertArrayEquals(configuration.cameraCharacteristics.metadata, expectedMetadata.metadata);
    }

    private VirtualCameraConfig createVirtualCameraConfig(
            int width, int height, int format, int maximumFramesPerSecond,
            String name, int sensorOrientation, int lensFacing) {
        return new VirtualCameraConfig.Builder(name)
                .addStreamConfig(width, height, format, maximumFramesPerSecond)
                .setVirtualCameraCallback(mCallbackHandler, mVirtualCameraCallbackMock)
                .setSensorOrientation(sensorOrientation)
                .setLensFacing(lensFacing)
                .build();
    }

    private static void assertVirtualCameraConfiguration(
            VirtualCameraConfiguration configuration, int width, int height, int format,
            int maxFps, int sensorOrientation, int lensFacing) {
        assertThat(configuration.supportedStreamConfigs[0].width).isEqualTo(width);
        assertThat(configuration.supportedStreamConfigs[0].height).isEqualTo(height);
        assertThat(configuration.supportedStreamConfigs[0].pixelFormat).isEqualTo(format);
        assertThat(configuration.supportedStreamConfigs[0].maxFps).isEqualTo(maxFps);
        assertThat(configuration.sensorOrientation).isEqualTo(sensorOrientation);
        assertThat(configuration.lensFacing).isEqualTo(lensFacing);
    }

    @Parameters(method = "getAllLensFacingDirections")
    @Test
    @EnableFlags(Flags.FLAG_VIRTUAL_CAMERA_METADATA)
    public void registerCameraWithCharacteristics_registersCamera(int lensFacing) throws Exception {
        CameraCharacteristics characteristics = new CameraCharacteristics.Builder(
                VirtualCameraConfig.DEFAULT_VIRTUAL_CAMERA_CHARACTERISTICS)
                .set(CameraCharacteristics.SENSOR_ORIENTATION, CAMERA_SENSOR_ORIENTATION_1)
                .set(CameraCharacteristics.LENS_FACING, lensFacing)
                .build();

        VirtualCameraConfig config = new VirtualCameraConfig.Builder(CAMERA_NAME_1)
                .addStreamConfig(CAMERA_WIDTH_1, CAMERA_HEIGHT_1, CAMERA_FORMAT_1, CAMERA_MAX_FPS_1)
                .setVirtualCameraCallback(mCallbackHandler, mVirtualCameraCallbackMock)
                .setCameraCharacteristics(characteristics)
                .build();

        verifyRegisterCameraWithCharacteristicsConfig(config, DEVICE_ID, CAMERA_WIDTH_1,
                CAMERA_HEIGHT_1, CAMERA_FORMAT_1, CAMERA_MAX_FPS_1);
    }

    @Test
    @EnableFlags(Flags.FLAG_VIRTUAL_CAMERA_METADATA)
    public void unregisterCameraWithCharacteristics_unregistersCamera() throws Exception {
        CameraCharacteristics characteristics = new CameraCharacteristics.Builder(
                VirtualCameraConfig.DEFAULT_VIRTUAL_CAMERA_CHARACTERISTICS)
                .set(CameraCharacteristics.SENSOR_ORIENTATION, CAMERA_SENSOR_ORIENTATION_1)
                .set(CameraCharacteristics.LENS_FACING, CAMERA_LENS_FACING_1)
                .build();

        VirtualCameraConfig config = new VirtualCameraConfig.Builder(CAMERA_NAME_1)
                .addStreamConfig(CAMERA_WIDTH_1, CAMERA_HEIGHT_1, CAMERA_FORMAT_1, CAMERA_MAX_FPS_1)
                .setVirtualCameraCallback(mCallbackHandler, mVirtualCameraCallbackMock)
                .setCameraCharacteristics(characteristics)
                .build();

        mVirtualCameraController.registerCamera(config, AttributionSource.myAttributionSource());

        mVirtualCameraController.unregisterCamera(config);

        verify(mVirtualCameraServiceMock).unregisterCamera(any());
    }

    @Parameters(method = "getAllLensFacingDirections")
    @Test
    @EnableFlags(Flags.FLAG_VIRTUAL_CAMERA_METADATA)
    public void registerCameraWithPerFrameMetadata_registersCamera(int lensFacing)
            throws Exception {
        VirtualCameraConfig config = new VirtualCameraConfig.Builder(CAMERA_NAME_1)
                .addStreamConfig(CAMERA_WIDTH_1, CAMERA_HEIGHT_1, CAMERA_FORMAT_1, CAMERA_MAX_FPS_1)
                .setVirtualCameraCallback(mCallbackHandler, mVirtualCameraCallbackMock)
                .setSensorOrientation(CAMERA_SENSOR_ORIENTATION_1)
                .setLensFacing(lensFacing)
                .build();
        mVirtualCameraController.registerCamera(config, AttributionSource.myAttributionSource());

        ArgumentCaptor<VirtualCameraConfiguration> configurationCaptor =
                ArgumentCaptor.forClass(VirtualCameraConfiguration.class);
        ArgumentCaptor<Integer> deviceIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mVirtualCameraServiceMock).registerCamera(any(), configurationCaptor.capture(),
                deviceIdCaptor.capture());
        assertThat(deviceIdCaptor.getValue()).isEqualTo(DEVICE_ID);
        VirtualCameraConfiguration virtualCameraConfiguration = configurationCaptor.getValue();
        assertThat(virtualCameraConfiguration.supportedStreamConfigs.length).isEqualTo(1);
        assertVirtualCameraConfiguration(virtualCameraConfiguration, CAMERA_WIDTH_1,
                CAMERA_HEIGHT_1, CAMERA_FORMAT_1, CAMERA_MAX_FPS_1, CAMERA_SENSOR_ORIENTATION_1,
                lensFacing);
    }

    @Parameters(method = "getAllLensFacingDirections")
    @Test
    @EnableFlags(Flags.FLAG_VIRTUAL_CAMERA_METADATA)
    public void registerCameraWithCharacteristicsAndPerFrameMetadata_registersCamera(int lensFacing)
            throws Exception {
        CameraCharacteristics characteristics = new CameraCharacteristics.Builder(
                VirtualCameraConfig.DEFAULT_VIRTUAL_CAMERA_CHARACTERISTICS)
                .set(CameraCharacteristics.SENSOR_ORIENTATION, CAMERA_SENSOR_ORIENTATION_1)
                .set(CameraCharacteristics.LENS_FACING, lensFacing)
                .build();

        VirtualCameraConfig config = new VirtualCameraConfig.Builder(CAMERA_NAME_1)
                .addStreamConfig(CAMERA_WIDTH_1, CAMERA_HEIGHT_1, CAMERA_FORMAT_1, CAMERA_MAX_FPS_1)
                .setVirtualCameraCallback(mCallbackHandler, mVirtualCameraCallbackMock)
                .setCameraCharacteristics(characteristics)
                .setPerFrameCameraMetadataEnabled(true)
                .build();

        verifyRegisterCameraWithCharacteristicsConfig(config, DEVICE_ID, CAMERA_WIDTH_1,
                CAMERA_HEIGHT_1, CAMERA_FORMAT_1, CAMERA_MAX_FPS_1);
    }

    private void verifyRegisterCameraWithCharacteristicsConfig(VirtualCameraConfig config,
            int deviceId, int width, int height, int format, int maxFps) throws RemoteException {
        VirtualCameraConfiguration originalConfig = getServiceCameraConfiguration(config);

        mVirtualCameraController.registerCamera(config, AttributionSource.myAttributionSource());

        ArgumentCaptor<VirtualCameraConfiguration> configurationCaptor =
                ArgumentCaptor.forClass(VirtualCameraConfiguration.class);
        ArgumentCaptor<Integer> deviceIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mVirtualCameraServiceMock).registerCamera(any(), configurationCaptor.capture(),
                deviceIdCaptor.capture());
        assertThat(deviceIdCaptor.getValue()).isEqualTo(deviceId);
        VirtualCameraConfiguration virtualCameraConfiguration = configurationCaptor.getValue();
        assertThat(virtualCameraConfiguration.supportedStreamConfigs.length).isEqualTo(1);
        assertVirtualCameraConfigurationWithCharacteristics(virtualCameraConfiguration,
                width, height, format, maxFps, originalConfig.cameraCharacteristics);
    }

    private void verifyRegisterDefaultPolicyCameraThrowsException() {
        mVirtualCameraController.close();
        mVirtualCameraController = new VirtualCameraController(mVirtualCameraServiceMock,
                DEVICE_POLICY_DEFAULT, DEVICE_ID);

        assertThrows(IllegalArgumentException.class, () -> mVirtualCameraController.registerCamera(
                createVirtualCameraConfig(CAMERA_WIDTH_1, CAMERA_HEIGHT_1, CAMERA_FORMAT_1,
                        CAMERA_MAX_FPS_1, CAMERA_NAME_1, CAMERA_SENSOR_ORIENTATION_1,
                        LENS_FACING_EXTERNAL), AttributionSource.myAttributionSource()));

        assertThrows(IllegalArgumentException.class, () -> mVirtualCameraController.registerCamera(
                createVirtualCameraConfig(CAMERA_WIDTH_1, CAMERA_HEIGHT_1, CAMERA_FORMAT_1,
                        CAMERA_MAX_FPS_1, CAMERA_NAME_1, CAMERA_SENSOR_ORIENTATION_1,
                        LENS_FACING_FRONT), AttributionSource.myAttributionSource()));
    }


    @SuppressWarnings("unused") // Parameter for parametrized tests
    private static Integer[] getFixedCamerasLensFacingDirections() {
        return new Integer[]{
                LENS_FACING_BACK,
                LENS_FACING_FRONT,
        };
    }

    @SuppressWarnings("unused") // Parameter for parametrized tests
    private static List<Integer> getAllLensFacingDirections() {
        List<Integer> lensFacingDirections = new ArrayList<>(
                List.of(LENS_FACING_BACK, LENS_FACING_FRONT));
        if (Flags.externalVirtualCameras()) {
            lensFacingDirections.add(LENS_FACING_EXTERNAL);
        }
        return lensFacingDirections;
    }
}
