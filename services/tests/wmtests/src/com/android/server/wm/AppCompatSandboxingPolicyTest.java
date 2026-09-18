/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static android.app.TaskInfo.PROPERTY_VALUE_UNSET;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.content.pm.ActivityInfo.INSETS_DECOUPLED_CONFIGURATION_ENFORCED;
import static android.content.pm.ActivityInfo.OVERRIDE_EXCLUDE_CAPTION_INSETS_FROM_APP_BOUNDS;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
import static android.view.WindowManager.PROPERTY_COMPAT_ALLOW_EXCLUDE_CAPTION_INSETS;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.internal.policy.SystemBarUtils.getDesktopViewAppHeaderHeightPx;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.compat.testing.PlatformCompatChangeRule;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;

import androidx.annotation.NonNull;

import com.android.window.flags.Flags;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.function.Consumer;

/**
 * Test class for {@link AppCompatSandboxingPolicy}.
 * <p>
 * Build/Install/Run:
 * atest WmTests:AppCompatSandboxingPolicyTest
 */
@Presubmit
@RunWith(WindowTestRunner.class)
public class AppCompatSandboxingPolicyTest extends WindowTestsBase {

    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    @Test
    @EnableFlags(Flags.FLAG_REFACTOR_CAPTION_SANDBOXING_TO_CORE)
    public void testSandboxBoundsIfNeeded_notFreeform_boundsNotSandboxed() {
        runTestScenario((robot) -> {
            robot.applyOnActivity((a) -> {
                a.createActivityWithComponent();
                a.setTaskWindowingMode(WINDOWING_MODE_FULLSCREEN);
            });
            robot.setTaskIsCaptionInsetsExcluded(true);
            final Rect originalBounds = new Rect(robot.activity().top().getBounds());

            robot.recomputeConfiguration();

            assertEquals(originalBounds, robot.activity().top().getBounds());
        });
    }

    @Test
    @EnableFlags(Flags.FLAG_REFACTOR_CAPTION_SANDBOXING_TO_CORE)
    public void testSandboxBoundsIfNeeded_freeform_boundsAreSandboxed() {
        runTestScenario((robot) -> {
            robot.conf().setCanEnterDesktopMode(true);

            robot.applyOnActivity((a) -> {
                a.createActivityWithComponent();
                a.setTaskWindowingMode(WINDOWING_MODE_FREEFORM);
            });
            robot.setTaskIsCaptionInsetsExcluded(true);

            robot.recomputeConfiguration();

            robot.checkBoundsSandboxed();
        });
    }

    @Test
    public void testSandboxBoundsIfNeeded_notInDesktopMode_boundsNotSandboxed() {
        runTestScenario((robot) -> {
            robot.activity().createActivityWithComponent();
            final Rect originalBounds = new Rect(robot.activity().top().getBounds());
            robot.setTaskIsCaptionInsetsExcluded(true);

            robot.recomputeConfiguration();

            assertEquals(originalBounds, robot.activity().top().getBounds());
        });
    }

    @Test
    @EnableFlags(Flags.FLAG_REFACTOR_CAPTION_SANDBOXING_TO_CORE)
    @DisableCompatChanges({OVERRIDE_EXCLUDE_CAPTION_INSETS_FROM_APP_BOUNDS})
    public void testSandboxBoundsIfNeeded_compatChangeDisabled_boundsNotSandboxed() {
        runTestScenario((robot) -> {
            robot.conf().setCanEnterDesktopMode(true);

            robot.applyOnActivity((a) -> {
                a.createActivityWithComponent();
                a.setTaskWindowingMode(WINDOWING_MODE_FREEFORM);
            });

            final Rect originalBounds = new Rect(robot.activity().top().getBounds());

            robot.recomputeConfiguration();

            assertEquals(originalBounds, robot.activity().top().getBounds());
        });
    }

    @Test
    @EnableFlags(Flags.FLAG_REFACTOR_CAPTION_SANDBOXING_TO_CORE)
    @EnableCompatChanges({OVERRIDE_EXCLUDE_CAPTION_INSETS_FROM_APP_BOUNDS})
    public void testSandboxBoundsIfNeeded_notResizable_boundsNotSandboxed() {
        runTestScenario((robot) -> {
            robot.conf().setCanEnterDesktopMode(true);

            robot.applyOnActivity((a) -> {
                a.createActivityWithComponent();
                a.setTaskWindowingMode(WINDOWING_MODE_FREEFORM);
                a.configureUnresizableTopActivity(SCREEN_ORIENTATION_UNSPECIFIED);
            });

            final Rect originalBounds = new Rect(robot.activity().top().getBounds());

            robot.recomputeConfiguration();

            assertEquals(originalBounds, robot.activity().top().getBounds());
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_REFACTOR_CAPTION_SANDBOXING_TO_CORE)
    @EnableCompatChanges({OVERRIDE_EXCLUDE_CAPTION_INSETS_FROM_APP_BOUNDS})
    public void testIsCaptionExcludedFromAppBounds_flagDisabled_overrideAllowed() {
        runTestScenario((robot) -> {
            robot.applyOnActivity(AppCompatActivityRobot::createActivityWithComponent);

            robot.checkIsCaptionExcludedFromAppBounds(/* expected */ true, /* isResizeable */
                    true);
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_REFACTOR_CAPTION_SANDBOXING_TO_CORE)
    @EnableCompatChanges({OVERRIDE_EXCLUDE_CAPTION_INSETS_FROM_APP_BOUNDS})
    public void testIsCaptionExcludedFromAppBounds_flagDisabled_overrideNotAllowed() {
        runTestScenario((robot) -> {
            robot.applyOnActivity(AppCompatActivityRobot::createActivityWithComponent);
            robot.prop().disable(PROPERTY_COMPAT_ALLOW_EXCLUDE_CAPTION_INSETS);

            robot.checkIsCaptionExcludedFromAppBounds(/* expected */ false, /* isResizeable */
                    false);
        });
    }

    @Test
    @EnableFlags(Flags.FLAG_REFACTOR_CAPTION_SANDBOXING_TO_CORE)
    @EnableCompatChanges({OVERRIDE_EXCLUDE_CAPTION_INSETS_FROM_APP_BOUNDS})
    public void testIsCaptionExcludedFromAppBounds_compatChangeEnabled_desktopMode() {
        runTestScenario((robot) -> {
            robot.conf().setCanEnterDesktopMode(true);
            robot.applyOnActivity(AppCompatActivityRobot::createActivityWithComponent);

            robot.checkIsCaptionExcludedFromAppBounds(/* expected */ true, /* isResizeable */
                    true);
        });
    }

    @Test
    @EnableFlags(Flags.FLAG_REFACTOR_CAPTION_SANDBOXING_TO_CORE)
    @DisableCompatChanges({OVERRIDE_EXCLUDE_CAPTION_INSETS_FROM_APP_BOUNDS})
    public void testIsCaptionExcludedFromAppBounds_compatChangeDisabled_desktopMode() {
        runTestScenario((robot) -> {
            robot.conf().setCanEnterDesktopMode(true);
            robot.applyOnActivity(AppCompatActivityRobot::createActivityWithComponent);

            robot.checkIsCaptionExcludedFromAppBounds(/* expected */ false, /* isResizeable */
                    true);
        });
    }

    @Test
    @EnableFlags(Flags.FLAG_REFACTOR_CAPTION_SANDBOXING_TO_CORE)
    @EnableCompatChanges({OVERRIDE_EXCLUDE_CAPTION_INSETS_FROM_APP_BOUNDS})
    public void testIsCaptionExcludedFromAppBounds_compatChangeEnabled_notDesktopMode() {
        runTestScenario((robot) -> {
            robot.conf().setCanEnterDesktopMode(false);
            robot.applyOnActivity(AppCompatActivityRobot::createActivityWithComponent);

            robot.checkIsCaptionExcludedFromAppBounds(/* expected */ false, /* isResizeable */
                    true);
        });
    }

    @Test
    @EnableFlags(Flags.FLAG_REFACTOR_CAPTION_SANDBOXING_TO_CORE)
    @EnableCompatChanges({OVERRIDE_EXCLUDE_CAPTION_INSETS_FROM_APP_BOUNDS,
            INSETS_DECOUPLED_CONFIGURATION_ENFORCED})
    public void testIsCaptionExcludedFromAppBounds_sdk35_overridden_notDesktopMode() {
        runTestScenario((robot) -> {
            robot.conf().setCanEnterDesktopMode(false);
            robot.applyOnActivity(AppCompatActivityRobot::createActivityWithComponent);

            robot.checkIsCaptionExcludedFromAppBounds(/* expected */ false, /* isResizeable */
                    true);
        });
    }

    @Test
    @EnableFlags(Flags.FLAG_REFACTOR_CAPTION_SANDBOXING_TO_CORE)
    @EnableCompatChanges({OVERRIDE_EXCLUDE_CAPTION_INSETS_FROM_APP_BOUNDS,
            INSETS_DECOUPLED_CONFIGURATION_ENFORCED})
    public void testIsCaptionExcludedFromAppBounds_sdk35_overridden_desktopMode() {
        runTestScenario((robot) -> {
            robot.conf().setCanEnterDesktopMode(true);
            robot.applyOnActivity(AppCompatActivityRobot::createActivityWithComponent);

            robot.checkIsCaptionExcludedFromAppBounds(/* expected */ true, /* isResizeable */
                    true);
        });
    }

    @Test
    @EnableFlags(Flags.FLAG_REFACTOR_CAPTION_SANDBOXING_TO_CORE)
    @DisableCompatChanges({INSETS_DECOUPLED_CONFIGURATION_ENFORCED})
    public void testIsCaptionExcludedFromAppBounds_notResizable_desktopMode() {
        runTestScenario((robot) -> {
            robot.conf().setCanEnterDesktopMode(true);
            robot.applyOnActivity(AppCompatActivityRobot::createActivityWithComponent);

            robot.checkIsCaptionExcludedFromAppBounds(/* expected */ true, /* isResizeable */
                    false);
        });
    }

    @Test
    public void testUpdateAppCompatDisplayInsets_updatesTaskCompatAspectRatio() {
        runTestScenario((robot) -> {
            robot.applyOnActivity(AppCompatActivityRobot::createActivityWithComponent);

            robot.checkTaskCompatAspectRatioUnset();

            robot.applyOnActivity((a) -> {
                a.configureUnresizableTopActivity(SCREEN_ORIENTATION_PORTRAIT);
            });

            robot.checkTaskCompatAspectRatioNotUnset();
        });
    }

    @Test
    @EnableFlags(Flags.FLAG_REFACTOR_CAPTION_SANDBOXING_TO_CORE)
    public void testSandboxBoundsIfNeeded_freeform_shouldSandboxToAppBounds_configUpdated() {
        runTestScenario((robot) -> {
            robot.conf().setCanEnterDesktopMode(true);

            robot.applyOnActivity((a) -> {
                a.createActivityWithComponent();
                a.setTaskWindowingMode(WINDOWING_MODE_FREEFORM);
                a.configureUnresizableTopActivity(SCREEN_ORIENTATION_LANDSCAPE);
            });
            robot.assertScreenHeightDpMatchParent(true);
            robot.setTaskIsCaptionInsetsExcluded(true);

            robot.recomputeConfiguration();

            robot.assertScreenHeightDpMatchParent(false);
            robot.checkMaxBoundsSandboxed();
        });
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    void runTestScenario(@NonNull Consumer<SandboxingPolicyRobotTest> consumer) {
        final SandboxingPolicyRobotTest robot = new SandboxingPolicyRobotTest(this);
        consumer.accept(robot);
    }

    private static class SandboxingPolicyRobotTest extends AppCompatRobotBase {

        SandboxingPolicyRobotTest(@NonNull WindowTestsBase windowTestBase) {
            super(windowTestBase);
        }

        void setTaskIsCaptionInsetsExcluded(boolean excluded) {
            final Task task = activity().top().getTask();
            doReturn(excluded).when(task).getIsCaptionInsetsExcluded();
        }

        void recomputeConfiguration() {
            activity().top().recomputeConfiguration();
        }

        void checkTaskCompatAspectRatioUnset() {
            assertEquals(PROPERTY_VALUE_UNSET, activity().top().getTask().getCompatAspectRatio(),
                    FLOAT_TOLLERANCE);
        }

        void checkTaskCompatAspectRatioNotUnset() {
            assertNotEquals(PROPERTY_VALUE_UNSET, activity().top().getTask().getCompatAspectRatio(),
                    FLOAT_TOLLERANCE);
        }

        void checkBoundsSandboxed() {
            final ActivityRecord activity = activity().top();
            final Rect activityBounds = activity.getBounds();
            final Task task = activity.getTask();
            final Rect taskBounds = task.getBounds();

            final int captionHeight = getDesktopViewAppHeaderHeightPx(
                    activity().testBase().mContext);

            // Sanity check.
            assertNotEquals(0, captionHeight);

            // The top of the activity bounds should be inset by the caption height
            // relative to the task bounds.
            assertEquals(taskBounds.top + captionHeight, activityBounds.top);
            assertEquals(taskBounds.left, activityBounds.left);
            assertEquals(taskBounds.right, activityBounds.right);
            assertEquals(taskBounds.bottom, activityBounds.bottom);
        }

        void assertScreenHeightDpMatchParent(boolean expectedMatch) {
            final ActivityRecord activity = activity().top();
            final Configuration resolvedConfig = activity.getConfiguration();
            final Configuration parentConfig = activity.getTask().getConfiguration();

            if (expectedMatch) {
                assertEquals(parentConfig.screenHeightDp, resolvedConfig.screenHeightDp);
            } else {
                assertNotEquals(parentConfig.screenHeightDp, resolvedConfig.screenHeightDp);
            }
        }

        void checkMaxBoundsSandboxed() {
            final ActivityRecord activity = activity().top();

            assertTrue(activity.providesMaxBounds());

            final Configuration resolvedConfig = activity.getConfiguration();
            final Rect appBounds = resolvedConfig.windowConfiguration.getAppBounds();
            final Rect bounds = resolvedConfig.windowConfiguration.getBounds();
            final Rect maxBounds = resolvedConfig.windowConfiguration.getMaxBounds();

            assertEquals(appBounds, maxBounds);
            assertEquals(bounds, maxBounds);
        }

        void checkIsCaptionExcludedFromAppBounds(boolean expected, boolean isResizeable) {
            assertEquals(expected,
                    activity().top().mAppCompatController.getSandboxingPolicy()
                            .isCaptionExcludedFromAppBounds(isResizeable));
        }
    }
}
