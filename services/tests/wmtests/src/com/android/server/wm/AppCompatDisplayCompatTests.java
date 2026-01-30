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

import static android.content.pm.ActivityInfo.CONFIG_COLOR_MODE;
import static android.content.pm.ActivityInfo.CONFIG_DENSITY;
import static android.content.pm.ActivityInfo.CONFIG_RESOURCES_UNUSED;
import static android.content.pm.ActivityInfo.CONFIG_TOUCHSCREEN;
import static android.content.pm.ActivityInfo.OVERRIDE_AUTO_RESTART_ON_DISPLAY_MOVE;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.window.flags.Flags.FLAG_ENABLE_AUTO_RESTART_ON_DISPLAY_MOVE;
import static com.android.window.flags.Flags.FLAG_ENABLE_DISPLAY_COMPAT_MODE;
import static com.android.window.flags.Flags.FLAG_ENABLE_RESTART_MENU_FOR_CONNECTED_DISPLAYS;

import static org.junit.Assert.assertEquals;

import android.compat.testing.PlatformCompatChangeRule;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;

import androidx.annotation.NonNull;
import androidx.test.filters.MediumTest;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.function.Consumer;

/**
 * Tests for display related app-compat behavior.
 *
 * Build/Install/Run:
 * atest WmTests:AppCompatDisplayCompatTests
 */
@MediumTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class AppCompatDisplayCompatTests extends WindowTestsBase {

    private static final int CONFIG_MASK_FOR_DISPLAY_MOVE =
            ~(CONFIG_DENSITY | CONFIG_TOUCHSCREEN | CONFIG_COLOR_MODE | CONFIG_RESOURCES_UNUSED);

    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    @Before
    public void setUp() {
        doReturn(false).when(mDisplayContent).shouldSleep();
        mAtm.updateSleepIfNeededLocked();
    }

    @EnableFlags({FLAG_ENABLE_DISPLAY_COMPAT_MODE, FLAG_ENABLE_RESTART_MENU_FOR_CONNECTED_DISPLAYS})
    @Test
    public void testDisplayCompatMode_gameDoesNotRestartWithDisplayMove() {
        runTestScenario((robot) -> {
            robot.activity().createSecondaryDisplay();
            robot.activity().createActivityWithComponent();
            robot.activity().setTopActivityGame(true);
            robot.activity().setTopActivityResumed();
            robot.activity().setTopActivityConfigChanges(CONFIG_MASK_FOR_DISPLAY_MOVE);
            robot.checkRestartMenuVisibility(false);
            robot.activity().clearInvocationsForActivity();

            robot.activity().moveTaskToSecondaryDisplay();
            robot.activity().checkTopActivityRelaunched(false);
            robot.checkRestartMenuVisibility(true);

            robot.activity().applyToTopActivity(ActivityRecord::restartProcessIfVisible);
            robot.checkRestartMenuVisibility(false);
        });
    }

    @Test
    public void testDisplayCompatMode_nonGameRestartsWithDisplayMove() {
        runTestScenario((robot) -> {
            robot.activity().createSecondaryDisplay();
            robot.activity().createActivityWithComponent();
            robot.activity().setTopActivityResumed();
            robot.activity().setTopActivityConfigChanges(CONFIG_MASK_FOR_DISPLAY_MOVE);
            robot.checkRestartMenuVisibility(false);
            robot.activity().clearInvocationsForActivity();

            robot.activity().moveTaskToSecondaryDisplay();
            robot.activity().checkTopActivityRelaunched(true);
            robot.checkRestartMenuVisibility(false);
        });
    }

    @EnableFlags(FLAG_ENABLE_RESTART_MENU_FOR_CONNECTED_DISPLAYS)
    @Test
    public void testSizeCompatMode_sizeCompatModeAppHasRestartMenuWithDisplayMove() {
        runTestScenario((robot) -> {
            robot.activity().createSecondaryDisplay();
            robot.activity().createActivityWithComponent();
            robot.activity().setTopActivityInSizeCompatMode(true);
            robot.activity().setShouldCreateCompatDisplayInsets(true);
            robot.activity().setTopActivityResumed();
            robot.checkRestartMenuVisibility(false);
            robot.activity().clearInvocationsForActivity();

            robot.activity().moveTaskToSecondaryDisplay();
            robot.activity().checkTopActivityRelaunched(false);
            robot.checkRestartMenuVisibility(true);

            robot.activity().applyToTopActivity(ActivityRecord::restartProcessIfVisible);
            robot.checkRestartMenuVisibility(false);
        });
    }

    @EnableFlags(FLAG_ENABLE_AUTO_RESTART_ON_DISPLAY_MOVE)
    @EnableCompatChanges(OVERRIDE_AUTO_RESTART_ON_DISPLAY_MOVE)
    @Test
    public void testAutoRestartOnDisplayMove_enabled_restartsApp() {
        runTestScenario((robot) -> {
            robot.activity().createSecondaryDisplay();
            robot.activity().createActivityWithComponent();
            robot.activity().setTopActivityResumed();
            robot.activity().clearInvocationsForActivity();

            robot.activity().moveTaskToSecondaryDisplay();

            robot.activity().checkTopActivityProcessRestarted(true);
        });
    }

    @EnableFlags(FLAG_ENABLE_AUTO_RESTART_ON_DISPLAY_MOVE)
    @DisableCompatChanges(OVERRIDE_AUTO_RESTART_ON_DISPLAY_MOVE)
    @Test
    public void testAutoRestartOnDisplayMove_compatChangeDisabled_doesNotRestartApp() {
        runTestScenario((robot) -> {
            robot.activity().createSecondaryDisplay();
            robot.activity().createActivityWithComponent();
            robot.activity().setTopActivityResumed();
            robot.activity().clearInvocationsForActivity();

            robot.activity().moveTaskToSecondaryDisplay();

            robot.activity().checkTopActivityProcessRestarted(false);
        });
    }

    void runTestScenario(@NonNull Consumer<DisplayCompatRobotTest> consumer) {
        final DisplayCompatRobotTest robot = new DisplayCompatRobotTest(this);
        consumer.accept(robot);
    }

    private static class DisplayCompatRobotTest extends AppCompatRobotBase {

        DisplayCompatRobotTest(@NonNull WindowTestsBase windowTestBase) {
            super(windowTestBase);
        }

        void checkRestartMenuVisibility(boolean enabled) {
            activity().applyToTopActivity(activity -> assertEquals(enabled,
                    activity.getTask().getTaskInfo().appCompatTaskInfo
                            .isRestartMenuEnabledForDisplayMove()));
        }
    }
}
