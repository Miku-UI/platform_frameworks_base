/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.wm;

import static android.app.WindowConfiguration.ROTATION_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.servertransaction.ActivityLifecycleItem.ON_STOP;
import static android.content.pm.ActivityInfo.OVERRIDE_CAMERA_COMPAT_DISABLE_SIMULATE_REQUESTED_ORIENTATION;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.hardware.camera2.CameraMetadata.SCALER_ROTATE_AND_CROP_180;
import static android.hardware.camera2.CameraMetadata.SCALER_ROTATE_AND_CROP_270;
import static android.hardware.camera2.CameraMetadata.SCALER_ROTATE_AND_CROP_90;
import static android.hardware.camera2.CameraMetadata.SCALER_ROTATE_AND_CROP_AUTO;
import static android.hardware.camera2.CameraMetadata.SCALER_ROTATE_AND_CROP_NONE;
import static android.view.Display.TYPE_EXTERNAL;
import static android.view.Display.TYPE_INTERNAL;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.server.wm.AppCompatCameraOverrides.REQUESTED;
import static com.android.server.wm.AppCompatConfiguration.MIN_FIXED_ORIENTATION_LETTERBOX_ASPECT_RATIO;
import static com.android.window.flags.Flags.FLAG_CAMERA_COMPAT_LANDSCAPE_CAMERA_SUPPORT;
import static com.android.window.flags.Flags.FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES;
import static com.android.window.flags.Flags.FLAG_CAMERA_COMPAT_UPDATE_TREATMENT_ON_ROTATION;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.annotation.NonNull;
import android.app.IApplicationThread;
import android.app.WindowConfiguration.WindowingMode;
import android.app.servertransaction.RefreshCallbackItem;
import android.app.servertransaction.ResumeActivityItem;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.pm.ActivityInfo.ScreenOrientation;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.content.res.Configuration.Orientation;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.RemoteException;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.view.OrientationEventListener;
import android.view.Surface;

import androidx.test.filters.SmallTest;

import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Tests for {@link AppCompatCameraSimReqOrientationPolicy}.
 *
 * <p>Build/Install/Run:
 *  atest WmTests:AppCompatCameraSimReqOrientationPolicyTests
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class AppCompatCameraSimReqOrientationPolicyTests extends WindowTestsBase {
    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    // Main activity package name needs to be the same as the process to test overrides.
    private static final String TEST_PACKAGE_1 = "com.android.frameworks.wmtests";
    private static final String TEST_PACKAGE_2 = "com.test.package.two";
    private static final String CAMERA_ID_1 = "camera-1";

    @Test
    @EnableCompatChanges({OVERRIDE_CAMERA_COMPAT_DISABLE_SIMULATE_REQUESTED_ORIENTATION})
    public void testIsCameraRunningAndWindowingModeEligible_disabledViaOverride_returnsFalse() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkIsCameraRunningAndWindowingModeEligible(false);
        });
    }

    @Test
    public void testIsCameraRunningAndWindowingModeEligible_cameraNotRunning_returnsFalse() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.checkIsCameraRunningAndWindowingModeEligible(false);
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testIsCameraRunningAndWindowingModeEligible_fullscreenAndNotAllowed_returnsFalse() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT, WINDOWING_MODE_FULLSCREEN);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkIsCameraRunningAndWindowingModeEligible(false);
        });
    }

    @Test
    public void testIsCameraRunningAndWindowingModeEligible_freeformCameraRunning_true() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkIsCameraRunningAndWindowingModeEligible(true);
        });
    }

    @Test
    @EnableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testIsCameraRunningAndWindowingModeEligible_splitScreenCameraRunning_true() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT, WINDOWING_MODE_MULTI_WINDOW);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkIsCameraRunningAndWindowingModeEligible(true);
        });
    }

    @Test
    @EnableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testIsCameraRunningAndWindowingModeEligible_fullscreenEnabled_true() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT, WINDOWING_MODE_FULLSCREEN);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkIsCameraRunningAndWindowingModeEligible(true);
        });
    }

    @Test
    @EnableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testIsCameraRunningAndWindowingModeEligible_ignoreOrientationReqFalse_false() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT, WINDOWING_MODE_FULLSCREEN);
            robot.setIgnoreOrientationRequest(false);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkIsCameraRunningAndWindowingModeEligible(false);
        });
    }

    @Test
    public void testIsFreeformLetterboxingForCameraAllowed_notOptedOut_returnsTrue() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkIsFreeformLetterboxingForCameraAllowed(true);
        });
    }

    @Test
    public void testIsFreeformLetterboxingForCameraAllowed_cameraNotRunning_returnsFalse() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.checkIsFreeformLetterboxingForCameraAllowed(false);
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testIsFreeformLetterboxingForCameraAllowed_notFreeformWindowing_returnsFalse() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT, WINDOWING_MODE_FULLSCREEN);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkIsFreeformLetterboxingForCameraAllowed(false);
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testFullscreen_flagToUnifyNotEnabled_doesNotActivateCameraCompatMode() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT, WINDOWING_MODE_FULLSCREEN);
            robot.setInFreeformWindowingMode(false);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.assertCompatibilityInfoNeverUpdated();
        });
    }

    @Test
    @EnableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testFullscreen_flagToUnifyEnabled_activatesCameraCompatMode() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT, WINDOWING_MODE_FULLSCREEN);
            robot.setInFreeformWindowingMode(false);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.assertCompatibilityInfoSentWithDisplayRotation(ROTATION_0);
            robot.assertCompatibilityInfoSentWithRotateAndCrop(ROTATION_270);
            robot.assertCompatibilityInfoSentWithLetterbox(true);
            robot.assertCompatibilityInfoSentWithSensorOverride(false);
            robot.assertCompatibilityInfoSentWithInverseTransformAllowed(false);
        });
    }

    @Test
    @EnableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testOrientationUnspecified_doesNotActivateCameraCompatMode() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_UNSPECIFIED);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.assertCompatibilityInfoNoCameraCompatMode();
            robot.assertActivityRefreshRequested(false);
            robot.assertActivityRefreshed(false);
        });
    }

    @Test
    public void testNoCameraConnection_doesNotActivateCameraCompatMode() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.assertCompatibilityInfoNeverUpdated();
        });
    }

    @Test
    public void testCameraConnected_deviceInPortrait_portraitCameraCompatMode() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            robot.rotateDisplay(ROTATION_0);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.assertCompatibilityInfoSentWithDisplayRotation(ROTATION_0);
            robot.assertCompatibilityInfoSentWithRotateAndCrop(ROTATION_0);
            robot.assertCompatibilityInfoSentWithLetterbox(true);
            robot.assertCompatibilityInfoSentWithSensorOverride(false);
            robot.assertCompatibilityInfoSentWithInverseTransformAllowed(false);
            robot.assertActivityRefreshRequested(/* refreshRequested */ true);
        });
    }

    @Test
    public void testCameraConnected_deviceInLandscape_portraitCameraCompatMode() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            robot.rotateDisplay(ROTATION_270);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.assertCompatibilityInfoSentWithDisplayRotation(ROTATION_0);
            robot.assertCompatibilityInfoSentWithRotateAndCrop(ROTATION_90);
            robot.assertCompatibilityInfoSentWithLetterbox(true);
            robot.assertCompatibilityInfoSentWithSensorOverride(false);
            robot.assertCompatibilityInfoSentWithInverseTransformAllowed(false);
            robot.assertActivityRefreshRequested(/* refreshRequested */ true);
        });
    }

    @Test
    public void testCameraConnected_deviceInPortrait_landscapeCameraCompatMode() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_LANDSCAPE);
            robot.rotateDisplay(ROTATION_0);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.assertCompatibilityInfoSentWithDisplayRotation(ROTATION_90);
            robot.assertCompatibilityInfoSentWithRotateAndCrop(ROTATION_90);
            robot.assertCompatibilityInfoSentWithLetterbox(true);
            robot.assertCompatibilityInfoSentWithSensorOverride(false);
            robot.assertCompatibilityInfoSentWithInverseTransformAllowed(false);
            robot.assertActivityRefreshRequested(/* refreshRequested */ true);
        });
    }

    @Test
    public void testCameraConnected_deviceInLandscape_landscapeCameraCompatMode() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_LANDSCAPE);
            robot.rotateDisplay(ROTATION_270);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.assertCompatibilityInfoSentWithDisplayRotation(ROTATION_90);
            robot.assertCompatibilityInfoSentWithRotateAndCrop(ROTATION_180);
            robot.assertCompatibilityInfoSentWithLetterbox(true);
            robot.assertCompatibilityInfoSentWithSensorOverride(false);
            robot.assertCompatibilityInfoSentWithInverseTransformAllowed(false);
            robot.assertActivityRefreshRequested(/* refreshRequested */ true);
        });
    }

    @Test
    public void testCameraOpenedForDifferentPackage_notInCameraCompatMode() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_2);

            robot.assertCompatibilityInfoNeverUpdated();
        });
    }

    @Test
    public void testShouldApplyCameraCompatFreeformTreatment_notOptedOut_returnsTrue() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkIsCameraCompatTreatmentActiveForTopActivity(true);
        });
    }

    @Test
    public void testGetCameraCompatAspectRatio_activityNotInCameraCompat_returnsDefaultAspRatio() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_FULL_USER);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.refreshActivityIfEnabled();

            robot.checkCameraCompatAspectRatioEquals(MIN_FIXED_ORIENTATION_LETTERBOX_ASPECT_RATIO);
        });
    }

    @Test
    public void testGetCameraCompatAspectRatio_activityInCameraCompat_returnsConfigAspectRatio() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            final float configAspectRatio = 1.5f;
            robot.applyOnConf(c -> c.setCameraCompatAspectRatio(configAspectRatio));

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.refreshActivityIfEnabled();

            robot.checkCameraCompatAspectRatioEquals(configAspectRatio);
        });
    }

    @Test
    public void testGetCameraCompatAspectRatio_inCameraCompatPerAppOverride_returnDefAspectRatio() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            robot.applyOnConf(c -> c.setCameraCompatAspectRatio(1.5f));
            robot.setOverrideMinAspectRatioEnabled(true);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.refreshActivityIfEnabled();

            robot.checkCameraCompatAspectRatioEquals(MIN_FIXED_ORIENTATION_LETTERBOX_ASPECT_RATIO);
        });
    }

    @Test
    public void testOnCameraOpened_portraitActivity_sendsDisplayRotationInCompatibilityInfo() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            robot.rotateDisplay(ROTATION_270);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            // This is a portrait rotation for a device with portrait natural orientation (most
            // common, currently the only one supported).
            robot.assertCompatibilityInfoSentWithDisplayRotation(ROTATION_0);
        });
    }

    @Test
    public void testOnCameraOpened_portraitActivity90_sendsRotateAndCrop270InCompatibilityInfo() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            robot.rotateDisplay(ROTATION_90);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            // This is a difference between the sandboxed display rotation (0) and the real display
            // rotation (90).
            robot.assertCompatibilityInfoSentWithRotateAndCrop(ROTATION_270);
        });
    }

    @Test
    public void testOnCameraOpened_portraitActivity270_sendsRotateAndCrop90InCompatibilityInfo() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            robot.rotateDisplay(ROTATION_270);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            // This is a difference between the sandboxed display rotation (0) and the real display
            // rotation (270).
            robot.assertCompatibilityInfoSentWithRotateAndCrop(ROTATION_90);
        });
    }

    @Test
    public void testOnCameraOpened_portraitActivity270_sendsShouldLetterboxInCompatibilityInfo() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            // Display is in landscape orientation.
            robot.rotateDisplay(ROTATION_270);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            // Portrait app in landscape orientation should be letterboxed for camera compat.
            robot.assertCompatibilityInfoSentWithLetterbox(true);
        });
    }

    @Test
    public void testOnCameraOpened_portraitActivity270_sendsNoSensorChangeInCompatibilityInfo() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            // Display is in landscape orientation.
            robot.rotateDisplay(ROTATION_270);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            // No sensor orientation change (sandboxing) is needed for portrait cameras, no matter
            // the device orientation.
            robot.assertCompatibilityInfoSentWithSensorOverride(false);
        });
    }

    @Test
    public void testOnCameraOpened_landscapeActivity_sandboxesDisplayRotationAndUpdatesApp() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_LANDSCAPE);
            robot.rotateDisplay(ROTATION_0);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            // This is a landscape rotation for a device with portrait natural orientation (most
            // common, currently the only one supported).
            robot.assertCompatibilityInfoSentWithDisplayRotation(ROTATION_90);
        });
    }

    @Test
    @Ignore("b/498068211")
    public void testOnCameraOpened_externalDisplayFixedOrientation_fullTreatment() {
        runTestScenario((robot) -> {
            // Setup default display.
            robot.activity().createNewDisplay();
            robot.makeCurrentDisplayDefault();
            // Setup external display and the activity on it.
            robot.configureActivityAndDisplay(SCREEN_ORIENTATION_PORTRAIT, ORIENTATION_LANDSCAPE,
                    WINDOWING_MODE_FREEFORM, TYPE_EXTERNAL);
            // Sensor rotation is continuous, and counted in the opposite direction from display
            // rotation: 360 - 100 = 260, and 260 is closest to ROTATION_270.
            robot.setSensorOrientation(100);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            // Display rotation for fixed-orientation portrait apps should always be 0.
            robot.assertCompatibilityInfoSentWithDisplayRotation(ROTATION_0);
            robot.assertCompatibilityInfoSentWithSensorOverride(false);
            robot.assertCompatibilityInfoSentWithLetterbox(true);
            // Default is true, and should be disabled (false) for camera compat.
            robot.assertCompatibilityInfoSentWithInverseTransformAllowed(false);
            // Rotate and crop value should offset the difference between the sandboxed display
            // rotation and the real display (camera) rotation: (0 - 270) % 360 = 90.
            robot.assertCompatibilityInfoSentWithRotateAndCrop(ROTATION_90);
        });
    }

    @Test
    @EnableFlags(FLAG_CAMERA_COMPAT_LANDSCAPE_CAMERA_SUPPORT)
    public void testOnCameraOpened_landscapeDisplay_sandboxedToPortrait() {
        runTestScenario((robot) -> {
            robot.configureActivityAndDisplay(SCREEN_ORIENTATION_PORTRAIT, ORIENTATION_LANDSCAPE,
                    WINDOWING_MODE_FREEFORM);
            robot.applyOnConf(c -> c.enableCameraCompatLandscapeToPortraitTreatment(true));
            robot.rotateDisplay(ROTATION_0);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            // Display rotation for fixed-orientation portrait apps should always be 0.
            robot.assertCompatibilityInfoSentWithDisplayRotation(ROTATION_0);
            // Sensor orientation should change from landscape to portrait.
            robot.assertCompatibilityInfoSentWithSensorOverride(true);
            robot.assertCompatibilityInfoSentWithLetterbox(true);
            // Default is true, and should be disabled (false) for camera compat.
            robot.assertCompatibilityInfoSentWithInverseTransformAllowed(false);
            // Rotate and crop value should offset by the change in sensor orientation:
            // (0 - 90) % 360 = 270.
            robot.assertCompatibilityInfoSentWithRotateAndCrop(ROTATION_270);
        });
    }

    @Test
    @EnableFlags({FLAG_CAMERA_COMPAT_LANDSCAPE_CAMERA_SUPPORT,
            FLAG_CAMERA_COMPAT_UPDATE_TREATMENT_ON_ROTATION})
    public void testOnCameraOpened_displayRotated_recomputesCameraCompatMode() {
        runTestScenario((robot) -> {
            robot.configureActivityAndDisplay(SCREEN_ORIENTATION_PORTRAIT, ORIENTATION_PORTRAIT,
                    WINDOWING_MODE_FREEFORM);
            robot.rotateDisplay(ROTATION_0);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            // Display rotation for fixed-orientation portrait apps should always be 0.
            robot.assertCompatibilityInfoSentWithDisplayRotation(ROTATION_0);
            robot.assertCompatibilityInfoSentWithSensorOverride(false);
            robot.assertCompatibilityInfoSentWithLetterbox(true);
            robot.assertCompatibilityInfoSentWithInverseTransformAllowed(false);
            // Rotate and crop value should be 0 since the sandboxed and real display rotation are
            // the same:
            // (0 - 0) % 360 = 0.
            robot.assertCompatibilityInfoSentWithRotateAndCrop(ROTATION_0);

            robot.rotateDisplay(ROTATION_90);

            // Display rotation for fixed-orientation portrait apps should always be 0.
            robot.assertCompatibilityInfoSentWithDisplayRotation(ROTATION_0, /* times */ 2,
                    /* order */ 1);
            robot.assertCompatibilityInfoSentWithSensorOverride(false,
                    /* times */ 2, /* order */ 1);
            robot.assertCompatibilityInfoSentWithLetterbox(true, /* times */ 2,
                    /* order */ 1);
            robot.assertCompatibilityInfoSentWithInverseTransformAllowed(false, /* times */ 2,
                    /* order */ 1);
            // Rotate and crop value should change as display rotation changed:
            // (0 - 90) % 360 = 270.
            robot.assertCompatibilityInfoSentWithRotateAndCrop(ROTATION_270, /* times */ 2,
                    /* order */ 1);
        });
    }

    @Test
    @EnableFlags(FLAG_CAMERA_COMPAT_LANDSCAPE_CAMERA_SUPPORT)
    public void testOnCameraOpened_neededRotateAndCropNotSupported_noCameraCompatMode() {
        runTestScenario((robot) -> {
            robot.configureActivityAndDisplay(SCREEN_ORIENTATION_PORTRAIT, ORIENTATION_LANDSCAPE,
                    WINDOWING_MODE_FREEFORM);
            robot.setupSupportedRotateAndCropModes(new int[]{SCALER_ROTATE_AND_CROP_NONE,
                    SCALER_ROTATE_AND_CROP_AUTO});
            robot.applyOnConf(c -> c.enableCameraCompatLandscapeToPortraitTreatment(true));
            robot.rotateDisplay(ROTATION_0);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            // Treatment are not activated as it cannot be fully executed.
            robot.assertCompatibilityInfoNoCameraCompatMode();
        });
    }

    @Test
    @Ignore("b/498068211")
    public void testOnCameraOpened_fixedOrientExtDisplRotateAndCropNotSupported_sandbDispRotOnly() {
        runTestScenario((robot) -> {
            // Setup default display.
            robot.activity().createNewDisplay();
            robot.makeCurrentDisplayDefault();
            // Setup external display and the activity on it.
            robot.configureActivityAndDisplay(SCREEN_ORIENTATION_PORTRAIT, ORIENTATION_LANDSCAPE,
                    WINDOWING_MODE_FREEFORM, TYPE_EXTERNAL);
            robot.setupSupportedRotateAndCropModes(new int[]{SCALER_ROTATE_AND_CROP_NONE,
                    SCALER_ROTATE_AND_CROP_AUTO});
            // Sensor rotation is continuous, and counted in the opposite direction from display
            // rotation: 360 - 100 = 260, and 260 is closest to ROTATION_270.
            robot.setSensorOrientation(100);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            // Display rotation should be the same as the camera rotation (see comment above), if
            // required rotate and crop is not available.
            robot.assertCompatibilityInfoSentWithDisplayRotation(ROTATION_270);
            // Default is true, and should be disabled (false) for camera compat.
            robot.assertCompatibilityInfoSentWithInverseTransformAllowed(false);
            // The other parts of the treatment are not activated.
            robot.assertCompatibilityInfoSentWithSensorOverride(false);
            robot.assertCompatibilityInfoSentWithLetterbox(false);
            robot.assertCompatibilityInfoSentWithRotateAndCrop(ROTATION_UNDEFINED);
        });
    }

    @Test
    @Ignore("b/498068211")
    public void testOnCameraOpened_externalDisplayResponsive_sandboxDisplayRotationOnly() {
        runTestScenario((robot) -> {
            // Setup default display.
            robot.activity().createNewDisplay();
            robot.makeCurrentDisplayDefault();
            // Setup external display and the activity on it.
            robot.configureActivityAndDisplay(SCREEN_ORIENTATION_FULL_USER, ORIENTATION_PORTRAIT,
                    WINDOWING_MODE_FREEFORM, TYPE_EXTERNAL);
            // Sensor rotation is continuous, and counted in the opposite direction from display
            // rotation: 360 - 100 = 260, and 260 is closest to ROTATION_270.
            robot.setSensorOrientation(100);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            // Display rotation should be the same as the camera rotation (see comment above).
            robot.assertCompatibilityInfoSentWithDisplayRotation(ROTATION_270);
            // Default is true, and should be disabled (false) for camera compat.
            robot.assertCompatibilityInfoSentWithInverseTransformAllowed(false);
            // The other parts of the treatment are not activated.
            robot.assertCompatibilityInfoSentWithSensorOverride(false);
            robot.assertCompatibilityInfoSentWithLetterbox(false);
            robot.assertCompatibilityInfoSentWithRotateAndCrop(ROTATION_UNDEFINED);
        });
    }

    @Test
    public void testCameraClosed_activityDetachedFromProcess_handlesGracefully() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            robot.rotateDisplay(ROTATION_270);
            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.refreshActivityIfEnabled();

            // This might happen at some point during teardown.
            robot.detachActivityFromProcess();

            // Make sure no errors are thrown here.
            robot.onCameraClosed(CAMERA_ID_1);
        });
    }

    @Test
    public void testShouldIgnoreReqOrientationForCameraCompat_cameraOpened_returnsTrue() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            robot.activity().rotateDisplayForTopActivity(ROTATION_270);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkShouldIgnoreReqOrientationForCameraCompat(true);
        });
    }

    @Test
    public void testShouldIgnoreReqOrientationForCameraCompat_cameraOpened_returnsFalse() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_FULL_USER);
            robot.activity().rotateDisplayForTopActivity(ROTATION_270);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.checkShouldIgnoreReqOrientationForCameraCompat(false);
        });
    }

    @Test
    public void testShouldIgnoreReqOrientationForCameraCompat_cameraClosed_returnsFalse() {
        runTestScenario((robot) -> {
            robot.configureActivity(SCREEN_ORIENTATION_PORTRAIT);
            robot.activity().rotateDisplayForTopActivity(ROTATION_270);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);
            robot.onCameraClosed(CAMERA_ID_1);

            robot.checkShouldIgnoreReqOrientationForCameraCompat(false);
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testOnCameraOpened_windowingModeChanged_recomputesCameraCompatMode() {
        runTestScenario((robot) -> {
            robot.configureActivityAndDisplay(SCREEN_ORIENTATION_PORTRAIT, ORIENTATION_PORTRAIT,
                    WINDOWING_MODE_FREEFORM);
            robot.rotateDisplay(ROTATION_90);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            // Display rotation for fixed-orientation portrait apps should always be 0.
            robot.assertCompatibilityInfoSentWithDisplayRotation(ROTATION_0);
            robot.assertCompatibilityInfoSentWithSensorOverride(false);
            robot.assertCompatibilityInfoSentWithLetterbox(true);
            robot.assertCompatibilityInfoSentWithInverseTransformAllowed(false);
            robot.assertCompatibilityInfoSentWithRotateAndCrop(ROTATION_270);

            robot.changeWindowingMode(WINDOWING_MODE_FULLSCREEN);

            robot.assertCompatibilityInfoNoCameraCompatMode(/* times */ 2, /* order */ 1);
        });
    }

    @Test
    @DisableFlags(FLAG_CAMERA_COMPAT_UNIFY_CAMERA_POLICIES)
    public void testOnCameraOpened_fullscreenToFreeform_activatesCameraCompatMode() {
        runTestScenario((robot) -> {
            robot.configureActivityAndDisplay(SCREEN_ORIENTATION_PORTRAIT, ORIENTATION_PORTRAIT,
                    WINDOWING_MODE_FULLSCREEN);
            robot.rotateDisplay(ROTATION_0);

            robot.onCameraOpened(CAMERA_ID_1, TEST_PACKAGE_1);

            robot.assertCompatibilityInfoNeverUpdated();

            robot.changeWindowingMode(WINDOWING_MODE_FREEFORM);

            robot.assertCompatibilityInfoSentWithDisplayRotation(ROTATION_0);
            robot.assertCompatibilityInfoSentWithSensorOverride(false);
            robot.assertCompatibilityInfoSentWithLetterbox(true);
            robot.assertCompatibilityInfoSentWithInverseTransformAllowed(false);
            robot.assertCompatibilityInfoSentWithRotateAndCrop(ROTATION_0);
        });
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    void runTestScenario(@NonNull Consumer<AppCompatCameraSimReqOrientationPolicyRobotTests>
            consumer) {
        final AppCompatCameraSimReqOrientationPolicyRobotTests robot =
                new AppCompatCameraSimReqOrientationPolicyRobotTests(this);
        consumer.accept(robot);
    }

    private static class AppCompatCameraSimReqOrientationPolicyRobotTests
            extends AppCompatRobotBase {
        private final WindowTestsBase mWindowTestsBase;

        private CameraManager.AvailabilityCallback mCameraAvailabilityCallback;

        private final CameraManager mMockCameraManager = mock(CameraManager.class);

        AppCompatCameraSimReqOrientationPolicyRobotTests(@NonNull WindowTestsBase windowTestsBase) {
            super(windowTestsBase);
            mWindowTestsBase = windowTestsBase;
            setupCameraManager();
            setupAppCompatConfiguration();
            reInitCameraPolicy();
            spyOnPolicy();
        }

        @Override
        void applyOnConf(@NonNull Consumer<AppCompatConfigurationRobot> consumer) {
            super.applyOnConf(consumer);
            reInitCameraPolicy();
            spyOnPolicy();
        }

        private void spyOnPolicy() {
            spyOn(mWindowTestsBase.mWm.mAppCompatCameraPolicy);
            if (mWindowTestsBase.mWm.mAppCompatCameraPolicy.mSimReqOrientationPolicy
                    != null) {
                spyOn(mWindowTestsBase.mWm.mAppCompatCameraPolicy.mSimReqOrientationPolicy);
            }
        }

        @Override
        void onPostActivityCreation(@NonNull ActivityRecord activity) {
            super.onPostActivityCreation(activity);
            setupCameraManager();
            setupHandler();
            setupMockApplicationThread();
        }

        private void setupMockApplicationThread() {
            IApplicationThread mockApplicationThread = mock(IApplicationThread.class);
            spyOn(activity().top().app);
            doReturn(mockApplicationThread).when(activity().top().app).getThread();
        }

        private Configuration createConfiguration(boolean letterbox, int rotation) {
            final Configuration configuration = createConfiguration(letterbox);
            configuration.windowConfiguration.setDisplayRotation(rotation);
            return configuration;
        }

        private Configuration createConfiguration(boolean letterbox) {
            final Configuration configuration = new Configuration();
            Rect bounds = letterbox ? new Rect(/*left*/ 300, /*top*/ 0, /*right*/ 700, /*bottom*/
                    600)
                    : new Rect(/*left*/ 0, /*top*/ 0, /*right*/ 1000, /*bottom*/ 600);
            configuration.windowConfiguration.setAppBounds(bounds);
            return configuration;
        }

        private void setupAppCompatConfiguration() {
            applyOnConf((c) -> {
                c.enableCameraCompatForceRotateTreatment(true);
                c.enableCameraCompatRefresh(true);
                c.enableCameraCompatRefreshCycleThroughStop(true);
                c.enableCameraCompatSplitScreenAspectRatio(false);
            });
        }

        private void setupCameraManager() {
            doAnswer(invocation -> {
                mCameraAvailabilityCallback = invocation.getArgument(1);
                return null;
            }).when(mMockCameraManager).registerAvailabilityCallback(
                    any(Executor.class), any(CameraManager.AvailabilityCallback.class));

            doReturn(mMockCameraManager).when(mWindowTestsBase.mWm.mContext).getSystemService(
                    CameraManager.class);

            setupSupportedRotateAndCropModes(new int[]{
                    SCALER_ROTATE_AND_CROP_NONE,
                    SCALER_ROTATE_AND_CROP_90,
                    SCALER_ROTATE_AND_CROP_180,
                    SCALER_ROTATE_AND_CROP_270,
                    SCALER_ROTATE_AND_CROP_AUTO});
        }

        private void setupSupportedRotateAndCropModes(int[] rotateAndCropModes) {
            final CameraCharacteristics cameraCharacteristics = mock(CameraCharacteristics.class);
            doReturn(rotateAndCropModes).when(cameraCharacteristics).get(
                    CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES);
            try {
                doReturn(cameraCharacteristics).when(mMockCameraManager)
                        .getCameraCharacteristics(anyString());
            } catch (Exception e) {
                throw new AssertionError("Unable to setup supported camera compat modes.", e);
            }
        }

        private void setupHandler() {
            final Handler handler = activity().top().mWmService.mH;
            spyOn(handler);

            doAnswer(invocation -> {
                ((Runnable) invocation.getArgument(0)).run();
                return null;
            }).when(handler).postDelayed(any(Runnable.class), anyLong());
        }

        private void configureActivity(@ScreenOrientation int activityOrientation) {
            configureActivity(activityOrientation, WINDOWING_MODE_FREEFORM);
        }

        private void configureActivity(@ScreenOrientation int activityOrientation,
                @WindowingMode int windowingMode) {
            configureActivityAndDisplay(activityOrientation, ORIENTATION_PORTRAIT, windowingMode);
        }

        private void configureActivityAndDisplay(@ScreenOrientation int activityOrientation,
                @Orientation int naturalOrientation, @WindowingMode int windowingMode) {
            configureActivityAndDisplay(activityOrientation, naturalOrientation, windowingMode,
                    TYPE_INTERNAL);
        }

        private void configureActivityAndDisplay(@ScreenOrientation int activityOrientation,
                @Orientation int naturalOrientation, @WindowingMode int windowingMode,
                int displayType) {
            // applyOnConf will force camera compat policies to renitialize required components.
            applyOnConf(c -> {
                dw().allowEnterDesktopMode(true);
                c.enableCameraCompatSimReqOrientationTreatment(true);
            });
            applyOnActivity(a -> {
                a.createActivityWithComponentInNewTaskAndDisplay(displayType);
                a.setIgnoreOrientationRequest(true);
                rotateDisplay(ROTATION_90);
                a.configureTopActivity(/* minAspect */ -1, /* maxAspect */ -1,
                        activityOrientation, /* isUnresizable */ false);
                a.setTaskWindowingMode(windowingMode);
                setIgnoreOrientationRequest(true);
                a.setDisplayNaturalOrientation(naturalOrientation);
                spyOn(a.top().mAppCompatController.getCameraOverrides());
                spyOn(a.top().info);
                doReturn(a.displayContent().getDisplayInfo()).when(
                        a.displayContent().mWmService.mDisplayManagerInternal).getDisplayInfo(
                        a.displayContent().mDisplayId);
            });
        }

        private void rotateDisplay(@Surface.Rotation int rotation) {
            activity().rotateDisplayForTopActivity(rotation);
            if (cameraCompatFreeformPolicy() != null) {
                cameraCompatFreeformPolicy().onDisplayRotationChanged(activity().top(), rotation);
            }
        }

        private void changeWindowingMode(@WindowingMode int windowingMode) {
            activity().setTaskWindowingMode(windowingMode);
            if (cameraCompatFreeformPolicy() != null) {
                cameraCompatFreeformPolicy().onWindowingModeChanged(activity().top(),
                        windowingMode);
            }
        }

        private void onCameraOpened(@NonNull String cameraId, @NonNull String packageName) {
            mCameraAvailabilityCallback.onCameraOpened(cameraId, packageName);
            waitHandlerIdle();
        }

        private void onCameraClosed(@NonNull String cameraId) {
            mCameraAvailabilityCallback.onCameraClosed(cameraId);
        }

        private void waitHandlerIdle() {
            mWindowTestsBase.waitHandlerIdle(testBase().mWm.mH);
        }

        void detachActivityFromProcess() {
            activity().top().detachFromProcess();
        }

        void setIgnoreOrientationRequest(boolean ignoreOrientationRequest) {
            activity().displayContent().setIgnoreOrientationRequest(ignoreOrientationRequest);
        }

        void setInFreeformWindowingMode(boolean inFreeform) {
            doReturn(inFreeform).when(activity().top()).inFreeformWindowingMode();
        }

        void checkCameraCompatPolicyNotCreated() {
            assertNull(cameraCompatFreeformPolicy());
        }

        void checkIsCameraRunningAndWindowingModeEligible(boolean expected) {
            assertEquals(expected, cameraCompatFreeformPolicy()
                    .isCameraRunningAndWindowingModeEligible(activity().top()));
        }

        void checkIsFreeformLetterboxingForCameraAllowed(boolean expected) {
            assertEquals(expected, cameraCompatFreeformPolicy()
                    .isFreeformLetterboxingForCameraAllowed(activity().top()));
        }

        void checkCameraCompatAspectRatioEquals(float aspectRatio) {
            assertEquals(aspectRatio,
                    cameraCompatFreeformPolicy().getCameraCompatAspectRatio(activity().top()),
                    /* delta= */ 0.001);
        }

        private void assertActivityRefreshRequested(boolean refreshRequested) {
            verify(activity().top().mAppCompatController.getCameraOverrides(),
                    times(refreshRequested ? 1 : 0)).setActivityRefreshState(REQUESTED);
        }

        private void assertActivityRefreshed(boolean refreshed) {
            final RefreshCallbackItem refreshCallbackItem =
                    new RefreshCallbackItem(activity().top().token, ON_STOP);
            final ResumeActivityItem resumeActivityItem = new ResumeActivityItem(
                    activity().top().token,
                    /* isForward */ false, /* shouldSendCompatFakeFocus */ false);
            verify(activity().top().mAtmService.getLifecycleManager(),
                    times(refreshed ? 1 : 0))
                    .scheduleTransactionItems(activity().top().app.getThread(),
                            refreshCallbackItem, resumeActivityItem);
        }

        private void refreshActivityIfEnabled() {
            testBase().mWm.mAppCompatCameraPolicy.mActivityRefresher.refreshActivityIfEnabled(
                    activity().top());
        }

        void checkIsCameraCompatTreatmentActiveForTopActivity(boolean active) {
            assertEquals(active,
                    cameraCompatFreeformPolicy().isCompatibilityTreatmentEnabledForActivity(
                            activity().top(), /* checkOrientation */ true));
        }

        void checkShouldIgnoreReqOrientationForCameraCompat(boolean expected) {
            assertEquals(expected, cameraCompatFreeformPolicy()
                    .shouldIgnoreReqOrientationForCameraCompat(activity().top()));
        }

        void setOverrideMinAspectRatioEnabled(boolean enabled) {
            doReturn(enabled).when(activity().top().mAppCompatController.getCameraOverrides())
                    .isOverrideMinAspectRatioForCameraEnabled();
        }

        void assertCompatibilityInfoSentWithDisplayRotation(@Surface.Rotation int
                expectedRotation) {
            assertCompatibilityInfoSentWithDisplayRotation(expectedRotation, /* times */ 1,
                    /* order */ 0);
        }
        void assertCompatibilityInfoSentWithDisplayRotation(@Surface.Rotation int
                expectedRotation, int times, int order) {
            final CompatibilityInfo compatInfo = getCompatibilityInfo(times, order);
            assertEquals(expectedRotation != ROTATION_UNDEFINED,
                    compatInfo.isOverrideCameraCompatibilityInfoRequired());
            assertEquals(expectedRotation, compatInfo.cameraCompatibilityInfo
                    .getDisplayRotationSandbox());
        }

        void assertCompatibilityInfoSentWithRotateAndCrop(@Surface.Rotation int
                expectedRotation) {
            assertCompatibilityInfoSentWithRotateAndCrop(expectedRotation, /* times */ 1,
                    /* order */ 0);
        }

        void assertCompatibilityInfoSentWithRotateAndCrop(@Surface.Rotation int
                expectedRotation, int times, int order) {
            final CompatibilityInfo compatInfo = getCompatibilityInfo(times, order);
            assertTrue(compatInfo.isOverrideCameraCompatibilityInfoRequired());
            assertEquals(expectedRotation, compatInfo.cameraCompatibilityInfo
                    .getRotateAndCropRotation());
        }

        void assertCompatibilityInfoSentWithLetterbox(boolean shouldLetterbox) {
            assertCompatibilityInfoSentWithLetterbox(shouldLetterbox, /* times */ 1,
                    /* order */ 0);
        }

        void assertCompatibilityInfoSentWithLetterbox(boolean shouldLetterbox, int times,
                int order) {
            final CompatibilityInfo compatInfo = getCompatibilityInfo(times, order);
            assertTrue(compatInfo.isOverrideCameraCompatibilityInfoRequired());
            assertEquals(shouldLetterbox,
                    compatInfo.cameraCompatibilityInfo.shouldLetterboxForCameraCompat());
        }

        void assertCompatibilityInfoSentWithSensorOverride(boolean overrideSensorOrientation) {
            assertCompatibilityInfoSentWithSensorOverride(overrideSensorOrientation, /* times */ 1,
                    /* order */ 0);
        }

        void assertCompatibilityInfoSentWithSensorOverride(boolean overrideSensorOrientation,
                int times, int order) {
            final CompatibilityInfo compatInfo = getCompatibilityInfo(times, order);
            assertTrue(compatInfo.isOverrideCameraCompatibilityInfoRequired());
            assertEquals(overrideSensorOrientation,
                    compatInfo.cameraCompatibilityInfo.shouldOverrideSensorOrientation());
        }

        void assertCompatibilityInfoSentWithInverseTransformAllowed(boolean allowed) {
            assertCompatibilityInfoSentWithInverseTransformAllowed(allowed, /* times */ 1,
                    /* order */ 0);
        }

        void assertCompatibilityInfoSentWithInverseTransformAllowed(boolean allowed, int times,
                int order) {
            final CompatibilityInfo compatInfo = getCompatibilityInfo(times, order);
            assertTrue(compatInfo.isOverrideCameraCompatibilityInfoRequired());
            assertEquals(allowed,
                    compatInfo.cameraCompatibilityInfo.shouldAllowTransformInverseDisplay());
        }

        void assertCompatibilityInfoNoCameraCompatMode() {
            assertCompatibilityInfoNoCameraCompatMode(/* times */ 1, /* order */ 0);
        }

        void assertCompatibilityInfoNoCameraCompatMode(int times, int order) {
            final CompatibilityInfo compatInfo = getCompatibilityInfo(times, order);
            assertFalse(compatInfo.isOverrideCameraCompatibilityInfoRequired());
        }

        void assertCompatibilityInfoNeverUpdated() {
            try {
                verify(activity().top().app.getThread(), never())
                        .updatePackageCompatibilityInfo(eq(activity().top().packageName), any());
            } catch (RemoteException e) {
                fail(e.getMessage());
            }
        }

        private CompatibilityInfo getCompatibilityInfo() {
            return getCompatibilityInfo(/* times */ 1, /* order */ 0);
        }

        private CompatibilityInfo getCompatibilityInfo(int times, int order) {
            final ArgumentCaptor<CompatibilityInfo> compatibilityInfoArgumentCaptor =
                    ArgumentCaptor.forClass(CompatibilityInfo.class);
            try {
                verify(activity().top().app.getThread(), times(times))
                        .updatePackageCompatibilityInfo(
                                eq(activity().top().packageName),
                                compatibilityInfoArgumentCaptor.capture());
            } catch (RemoteException e) {
                fail(e.getMessage());
            }

            return compatibilityInfoArgumentCaptor.getAllValues().get(order);
        }

        AppCompatCameraSimReqOrientationPolicy cameraCompatFreeformPolicy() {
            return testBase().mWm.mAppCompatCameraPolicy.mSimReqOrientationPolicy;
        }

        void setSensorOrientation(int orientation) {
            final OrientationEventListener orientationEventListener = cameraCompatFreeformPolicy()
                    .mCameraDisplayRotationProvider.mOrientationEventListener;
            if (orientationEventListener != null) {
                orientationEventListener.onOrientationChanged(orientation);
            }
        }

        void makeCurrentDisplayDefault() {
            doReturn(activity().displayContent()).when(testBase().mWm)
                    .getDefaultDisplayContentLocked();
        }
    }
}
