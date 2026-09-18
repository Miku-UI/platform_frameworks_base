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

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.view.InsetsSource.FLAG_INSETS_ROUNDED_CORNER;
import static android.view.WindowManager.LayoutParams.TYPE_BASE_APPLICATION;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.view.InsetsSource;
import android.view.InsetsState;
import android.view.RoundedCorner;
import android.view.RoundedCorners;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;

import com.android.internal.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.function.Consumer;

/**
 * Test class for {@link AppCompatLetterboxPolicy}.
 *
 * Build/Install/Run:
 * atest WmTests:AppCompatLetterboxPolicyTest
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class AppCompatLetterboxPolicyTest extends WindowTestsBase {

    @Test
    public void testGetCropBoundsIfNeeded_handleCropForTransparentActivityBasedOnOpaqueBounds() {
        runTestScenario((robot) -> {
            robot.configureWindowStateWithTaskBar(/* hasTaskBarInsetsRoundedCorners */ true);
            robot.activity().createActivityWithComponent();
            robot.activity().setTopActivityVisible(/* isVisible */ true);
            robot.setIsLetterboxedForFixedOrientationAndAspectRatio(/* inLetterbox */ true);
            robot.conf().setLetterboxActivityCornersRounded(/* rounded */ true);
            robot.resources().configureGetDimensionPixelSize(R.dimen.taskbar_frame_height, 20);
            robot.setTopActivityTransparentPolicyRunning(/* running */ true);
            robot.activity().configureTopActivityBounds(new Rect(0, 0, 500, 300));

            robot.resizeMainWindow(/* newWidth */ 499, /* newHeight */ 299);
            robot.checkWindowStateHasCropBounds(/* expected */ false);

            robot.resizeMainWindow(/* newWidth */ 500, /* newHeight */ 300);
            robot.checkWindowStateHasCropBounds(/* expected */ true);
        });
    }

    @Test
    public void testGetCropBoundsIfNeeded_noCrop() {
        runTestScenario((robot) -> {
            robot.configureWindowStateWithTaskBar(/* hasTaskBarInsetsRoundedCorners */ false);
            robot.activity().createActivityWithComponent();
            robot.activity().setTopActivityVisible(/* isVisible */ true);
            robot.setIsLetterboxedForFixedOrientationAndAspectRatio(/* inLetterbox */ true);
            robot.conf().setLetterboxActivityCornersRounded(/* rounded */ true);
            robot.resources().configureGetDimensionPixelSize(R.dimen.taskbar_frame_height, 20);

            // Do not apply crop if taskbar is collapsed
            robot.collapseTaskBar();
            robot.checkTaskBarIsExpanded(/* expected */ false);

            robot.activity().configureTopActivityBounds(new Rect(50, 25, 150, 75));
            robot.checkWindowStateHasCropBounds(/* expected */ true);
            // Expected the same size of the activity.
            robot.validateWindowStateCropBounds(0, 0, 100, 50);
        });
    }

    @Test
    public void testGetCropBoundsIfNeeded_appliesCrop() {
        runTestScenario((robot) -> {
            robot.configureWindowStateWithTaskBar(/* hasTaskBarInsetsRoundedCorners */ true);
            robot.activity().createActivityWithComponent();
            robot.activity().setTopActivityVisible(/* isVisible */ true);
            robot.setIsLetterboxedForFixedOrientationAndAspectRatio(/* inLetterbox */ true);
            robot.conf().setLetterboxActivityCornersRounded(/* rounded */ true);
            robot.resources().configureGetDimensionPixelSize(R.dimen.taskbar_frame_height, 20);

            // Apply crop if taskbar is expanded.
            robot.expandTaskBar();
            robot.checkTaskBarIsExpanded(/* expected */ true);

            robot.activity().configureTopActivityBounds(new Rect(50, 0, 150, 100));
            robot.checkWindowStateHasCropBounds(/* expected */ true);
            // The task bar expanded height is removed from the crop height.
            robot.validateWindowStateCropBounds(0, 0, 100, 80);
        });
    }

    @Test
    public void testGetCropBoundsIfNeeded_appliesCropWithSizeCompatScaling() {
        runTestScenario((robot) -> {
            robot.configureWindowStateWithTaskBar(/* hasTaskBarInsetsRoundedCorners */ true);
            robot.activity().createActivityWithComponent();
            robot.activity().setTopActivityVisible(/* isVisible */ true);
            robot.setIsLetterboxedForFixedOrientationAndAspectRatio(/* inLetterbox */ true);
            robot.conf().setLetterboxActivityCornersRounded(/* rounded */ true);
            robot.resources().configureGetDimensionPixelSize(R.dimen.taskbar_frame_height, 20);

            // Apply crop if taskbar is expanded.
            robot.expandTaskBar();
            robot.checkTaskBarIsExpanded(/* expected */ true);
            robot.activity().setTopActivityInSizeCompatMode(/* isScm */ true);
            robot.setInvCompatState(/* scale */ 2.0f);

            robot.activity().configureTopActivityBounds(new Rect(50, 0, 150, 100));

            robot.checkWindowStateHasCropBounds(/* expected */ true);
            // The width and height, considering task bar, are scaled by 2.
            robot.validateWindowStateCropBounds(0, 0, 200, 160);
        });
    }

    @Test
    public void testGetCropBoundsIfNeeded_appliesCropWithSurfaceInsets() {
        runTestScenario((robot) -> {
            robot.configureWindowStateWithSurfaceInsets(new Rect(78, 78, 78, 78));
            robot.activity().createActivityWithComponent();
            robot.activity().setTopActivityVisible(/* isVisible */ true);
            robot.setIsLetterboxedForFixedOrientationAndAspectRatio(/* inLetterbox */ true);
            robot.conf().setLetterboxActivityCornersRounded(/* rounded */ true);

            robot.activity().configureTopActivityBounds(new Rect(50, 0, 150, 100));
            robot.checkWindowStateHasCropBoundsWithSurfaceInsets();
        });
    }

    @Test
    public void testGetRoundedCornersRadius_withRoundedCornersFromInsets() {
        runTestScenario((robot) -> {
            robot.conf().setLetterboxActivityCornersRadius(-1);
            robot.configureWindowState();
            robot.activity().createActivityWithComponent();
            robot.activity().setTopActivityVisible(/* isVisible */ true);
            robot.setIsLetterboxedForFixedOrientationAndAspectRatio(/* inLetterbox */ true);
            robot.conf().setLetterboxActivityCornersRounded(/* rounded */ true);
            robot.resources().configureGetDimensionPixelSize(R.dimen.taskbar_frame_height, 20);

            robot.setInvCompatState(/* scale */ 0.5f);
            robot.configureInsetsRoundedCorners(new RoundedCorners(
                    /*topLeft=*/ null,
                    /*topRight=*/ null,
                    /*bottomRight=*/ new RoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT,
                    /* configurationRadius */ 15, /*centerX=*/ 1, /*centerY=*/ 1),
                    /*bottomLeft=*/ new RoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT,
                    30 /*2 is to test selection of the min radius*/,
                    /*centerX=*/ 1, /*centerY=*/ 1)
            ));
            robot.checkWindowStateRoundedCornersRadius(/* expected */ 7);
        });
    }


    @Test
    public void testGetRoundedCornersRadius_withLetterboxActivityCornersRadius() {
        runTestScenario((robot) -> {
            robot.conf().setLetterboxActivityCornersRadius(15);
            robot.configureWindowState();
            robot.activity().createActivityWithComponent();
            robot.activity().setTopActivityVisible(/* isVisible */ true);
            robot.setIsLetterboxedForFixedOrientationAndAspectRatio(/* inLetterbox */ true);
            robot.conf().setLetterboxActivityCornersRounded(/* rounded */ true);
            robot.resources().configureGetDimensionPixelSize(R.dimen.taskbar_frame_height, 20);
            robot.setInvCompatState(/* scale */ 0.5f);

            robot.checkWindowStateRoundedCornersRadius(/* expected */ 7);

            robot.activity().setTopActivityVisibleRequested(/* isVisibleRequested */ false);
            robot.activity().setTopActivityVisible(/* isVisible */ false);
            robot.checkWindowStateRoundedCornersRadius(/* expected */ 0);
        });
    }

    @Test
    public void testGetRoundedCornersRadius_noScalingApplied() {
        runTestScenario((robot) -> {
            robot.conf().setLetterboxActivityCornersRadius(15);
            robot.configureWindowState();
            robot.activity().createActivityWithComponent();
            robot.activity().setTopActivityVisible(/* isVisible */ true);
            robot.setIsLetterboxedForFixedOrientationAndAspectRatio(/* inLetterbox */ true);
            robot.conf().setLetterboxActivityCornersRounded(/* rounded */ true);
            robot.resources().configureGetDimensionPixelSize(R.dimen.taskbar_frame_height, 20);

            robot.setInvCompatState(/* scale */ -1f);
            robot.checkWindowStateRoundedCornersRadius(/* expected */ 15);

            robot.setInvCompatState(/* scale */ 0f);
            robot.checkWindowStateRoundedCornersRadius(/* expected */ 15);

            robot.setInvCompatState(/* scale */ 1f);
            robot.checkWindowStateRoundedCornersRadius(/* expected */ 15);
        });
    }

    @Test
    public void testGetRoundedCornersRadius_letterboxBoundsMatchHeightInFreeform_notRounded() {
        runTestScenario((robot) -> {
            robot.conf().setLetterboxActivityCornersRadius(15);
            robot.configureWindowState();
            robot.activity().createActivityWithComponent();
            robot.activity().setTopActivityVisible(/* isVisible */ true);
            robot.setIsLetterboxedForFixedOrientationAndAspectRatio(/* inLetterbox */ true);
            robot.conf().setLetterboxActivityCornersRounded(/* rounded */ true);
            robot.resources().configureGetDimensionPixelSize(R.dimen.taskbar_frame_height, 20);

            robot.activity().setTaskWindowingMode(WINDOWING_MODE_FREEFORM);
            final Rect appBounds = new Rect(0, 0, 100, 500);
            robot.configureWindowStateFrame(appBounds);
            robot.activity().configureTaskAppBounds(appBounds);

            robot.startLetterbox();

            robot.checkWindowStateRoundedCornersRadius(/* expected */ 0);
        });
    }

    @Test
    public void testGetRoundedCornersRadius_letterboxBoundsNotMatchHeightInFreeform_rounded() {
        runTestScenario((robot) -> {
            robot.conf().setLetterboxActivityCornersRadius(15);
            robot.configureWindowState();
            robot.activity().createActivityWithComponent();
            robot.activity().setTopActivityVisible(/* isVisible */ true);
            robot.setIsLetterboxedForFixedOrientationAndAspectRatio(/* inLetterbox */ true);
            robot.conf().setLetterboxActivityCornersRounded(/* rounded */ true);
            robot.resources().configureGetDimensionPixelSize(R.dimen.taskbar_frame_height, 20);

            robot.activity().setTaskWindowingMode(WINDOWING_MODE_FREEFORM);
            robot.configureWindowStateFrame(new Rect(0, 0, 500, 200));
            robot.activity().configureTaskAppBounds(new Rect(0, 0, 500, 500));

            robot.startLetterbox();

            robot.checkWindowStateRoundedCornersRadius(/* expected */ 15);
        });
    }

    @Test
    public void testGetRoundedCornersRadius_isCaptionInsetsExcluded_notRounded() {
        runTestScenario((robot) -> {
            robot.conf().setLetterboxActivityCornersRadius(15);
            robot.configureWindowState();
            robot.activity().createActivityWithComponent();
            robot.activity().setTopActivityVisible(/* isVisible */ true);
            robot.setIsLetterboxedForFixedOrientationAndAspectRatio(/* inLetterbox */ true);
            robot.conf().setLetterboxActivityCornersRounded(/* rounded */ true);
            robot.resources().configureGetDimensionPixelSize(R.dimen.taskbar_frame_height, 20);

            robot.activity().setTaskWindowingMode(WINDOWING_MODE_FREEFORM);
            robot.configureWindowStateFrame(new Rect(0, 0, 500, 200));
            robot.activity().configureTaskAppBounds(new Rect(0, 0, 500, 500));
            robot.setTaskIsCaptionInsetsExcluded(true);

            robot.startLetterbox();

            robot.checkWindowStateRoundedCornersRadius(/* expected */ 0);
        });
    }

    @Test
    public void testHide_clearsLetterboxInsets_shellPolicy() {
        runTestScenario((robot) -> {
            // Setup: configure window state and create a visible activity.
            robot.configureWindowState();
            robot.activity().createActivityWithComponent();
            robot.activity().setTopActivityVisible(true);

            final Rect windowFrame = new Rect(robot.activity().top().getTask().getBounds());
            final Rect expectedInsets =
                    new Rect(/* left */10, /* top */20, /* right */10, /* bottom */20);
            windowFrame.inset(expectedInsets);
            robot.configureWindowStateFrame(windowFrame);

            // Trigger letterbox layout. This should calculate and store the letterbox bounds.
            robot.startLetterbox();

            // Verify initial state: letterbox is active and insets are non-empty.
            robot.checkLetterboxInsets(expectedInsets);

            // Hide the letterbox. This should clear the stored bounds.
            robot.hideLetterbox();

            // Verify final state: letterbox is hidden and insets are now empty.
            robot.checkLetterboxInsets(new Rect());
        });
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    void runTestScenario(@NonNull Consumer<LetterboxPolicyRobotTest> consumer) {
        final LetterboxPolicyRobotTest robot = new LetterboxPolicyRobotTest(this);
        consumer.accept(robot);
    }

    private static class LetterboxPolicyRobotTest extends AppCompatRobotBase {

        static final int TASKBAR_COLLAPSED_HEIGHT = 10;
        static final int TASKBAR_EXPANDED_HEIGHT = 20;
        private static final int SCREEN_WIDTH = 200;
        private static final int SCREEN_HEIGHT = 100;
        static final Rect TASKBAR_COLLAPSED_BOUNDS = new Rect(0,
                SCREEN_HEIGHT - TASKBAR_COLLAPSED_HEIGHT, SCREEN_WIDTH, SCREEN_HEIGHT);
        static final Rect TASKBAR_EXPANDED_BOUNDS = new Rect(0,
                SCREEN_HEIGHT - TASKBAR_EXPANDED_HEIGHT, SCREEN_WIDTH, SCREEN_HEIGHT);

        @NonNull
        private final WindowState mWindowState;
        @Nullable
        private InsetsSource mTaskbar;
        @Nullable
        private InsetsState mInsetsState;

        LetterboxPolicyRobotTest(@NonNull WindowTestsBase windowTestBase) {
            super(windowTestBase);
            mWindowState = mock(WindowState.class);
        }

        @Override
        void onPostActivityCreation(@NonNull ActivityRecord activity) {
            super.onPostActivityCreation(activity);
            spyOn(getAspectRatioPolicy());
            spyOn(getTransparentPolicy());
        }

        void startLetterbox() {
            getAppCompatLetterboxPolicy().start(mWindowState);
        }

        void hideLetterbox() {
            getAppCompatLetterboxPolicy().mLetterboxPolicyState.hide();
        }

        void checkLetterboxInsets(@NonNull Rect expected) {
            assertEquals(expected, getAppCompatLetterboxPolicy().getLetterboxInsets());
        }

        private void configureWindowStateWithSurfaceInsets(@NonNull Rect surfaceInsets) {
            configureWindowState();
            mWindowState.mAttrs.surfaceInsets.set(surfaceInsets);
        }

        void configureWindowStateWithTaskBar(boolean hasInsetsRoundedCorners) {
            configureWindowState(/* withTaskBar */ true, hasInsetsRoundedCorners);
        }

        void configureWindowState() {
            configureWindowState(/* withTaskBar */ false, /* hasInsetsRoundedCorners */ false);
        }

        void configureWindowStateFrame(@NonNull Rect frame) {
            doReturn(frame).when(mWindowState).getFrame();
        }

        void configureInsetsRoundedCorners(@NonNull RoundedCorners roundedCorners) {
            mInsetsState.setRoundedCorners(roundedCorners);
        }

        private void configureWindowState(boolean withTaskBar, boolean hasInsetsRoundedCorners) {
            mInsetsState = new InsetsState();
            if (withTaskBar) {
                mTaskbar = new InsetsSource(/*id=*/ 0,
                        WindowInsets.Type.navigationBars());
                if (hasInsetsRoundedCorners) {
                    mTaskbar.setFlags(FLAG_INSETS_ROUNDED_CORNER, FLAG_INSETS_ROUNDED_CORNER);
                }
                mTaskbar.setVisible(true);
                mInsetsState.addSource(mTaskbar);
            }
            mWindowState.mInvGlobalScale = 1f;
            final WindowManager.LayoutParams attrs = new WindowManager.LayoutParams();
            attrs.type = TYPE_BASE_APPLICATION;
            setFieldValue(mWindowState, "mAttrs", attrs);
            doReturn(mInsetsState).when(mWindowState).getInsetsState();
            doReturn(true).when(mWindowState).isDrawn();
            doReturn(true).when(mWindowState).isOnScreen();
            doReturn(false).when(mWindowState).isLetterboxedForDisplayCutout();
            doReturn(true).when(mWindowState).areAppWindowBoundsLetterboxed();
        }

        void setInvCompatState(float scale) {
            mWindowState.mInvGlobalScale = scale;
        }

        void setTopActivityTransparentPolicyRunning(boolean running) {
            doReturn(running).when(getTransparentPolicy()).isRunning();
        }

        void setIsLetterboxedForFixedOrientationAndAspectRatio(boolean isLetterboxed) {
            doReturn(isLetterboxed).when(getAspectRatioPolicy())
                    .isLetterboxedForFixedOrientationAndAspectRatio();
        }

        void setTaskIsCaptionInsetsExcluded(boolean excluded) {
            final Task task = activity().top().getTask();
            doReturn(excluded).when(task).getIsCaptionInsetsExcluded();
        }

        void resizeMainWindow(int newWidth, int newHeight) {
            mWindowState.mRequestedWidth = newWidth;
            mWindowState.mRequestedHeight = newHeight;
        }

        void collapseTaskBar() {
            mTaskbar.setFrame(TASKBAR_COLLAPSED_BOUNDS);
        }

        void expandTaskBar() {
            mTaskbar.setFrame(TASKBAR_EXPANDED_BOUNDS);
        }

        void checkWindowStateHasCropBounds(boolean expected) {
            final Rect cropBounds = getAppCompatLetterboxPolicy().getCropBoundsIfNeeded(
                    mWindowState);
            if (expected) {
                assertNotNull(cropBounds);
            } else {
                assertNull(cropBounds);
            }
        }

        void checkWindowStateHasCropBoundsWithSurfaceInsets() {
            final Rect expected = Rect.copyOrNull(activity().top().getBounds());
            AppCompatUtils.adjustCropBoundsForSurfaceInsets(expected, mWindowState);
            final Rect actual = getAppCompatLetterboxPolicy().getCropBoundsIfNeeded(
                    mWindowState);
            assertNotNull(actual);
            assertEquals(expected.width(), actual.width());
            assertEquals(expected.height(), actual.height());
        }

        void checkTaskBarIsExpanded(boolean expected) {
            final InsetsSource expandedTaskBar = AppCompatUtils.getExpandedTaskbarOrNull(
                    mWindowState);
            if (expected) {
                assertNotNull(expandedTaskBar);
            } else {
                assertNull(expandedTaskBar);
            }
        }

        void checkWindowStateRoundedCornersRadius(int expected) {
            assertEquals(expected, getAppCompatLetterboxPolicy()
                    .getRoundedCornersRadius(mWindowState));
        }

        void validateWindowStateCropBounds(int left, int top, int right, int bottom) {
            final Rect cropBounds = getAppCompatLetterboxPolicy().getCropBoundsIfNeeded(
                    mWindowState);
            assertEquals(left, cropBounds.left);
            assertEquals(top, cropBounds.top);
            assertEquals(right, cropBounds.right);
            assertEquals(bottom, cropBounds.bottom);
        }

        @NonNull
        private AppCompatAspectRatioPolicy getAspectRatioPolicy() {
            return activity().top().mAppCompatController.getAspectRatioPolicy();
        }

        @NonNull
        private TransparentPolicy getTransparentPolicy() {
            return activity().top().mAppCompatController.getTransparentPolicy();
        }

        @NonNull
        private AppCompatLetterboxPolicy getAppCompatLetterboxPolicy() {
            return activity().top().mAppCompatController.getLetterboxPolicy();
        }

    }

}
