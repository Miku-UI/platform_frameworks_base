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

import static android.content.pm.ActivityInfo.OVERRIDE_AUTO_RESTART_ON_DISPLAY_MOVE;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.window.flags.Flags.FLAG_ENABLE_AUTO_RESTART_ON_DISPLAY_MOVE;

import static org.junit.Assert.assertEquals;

import android.compat.testing.PlatformCompatChangeRule;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;

import androidx.annotation.NonNull;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.function.Consumer;

/**
 * Test class for {@link AppCompatDisplayOverrides}.
 * <p>
 * Build/Install/Run:
 * atest WmTests:AppCompatDisplayOverridesTest
 */
@Presubmit
@RunWith(WindowTestRunner.class)
public class AppCompatDisplayOverridesTest extends WindowTestsBase {

    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    @Test
    @EnableFlags(FLAG_ENABLE_AUTO_RESTART_ON_DISPLAY_MOVE)
    @EnableCompatChanges({OVERRIDE_AUTO_RESTART_ON_DISPLAY_MOVE})
    public void testShouldRestartOnDisplayMove_overrideEnabled_returnsTrue() {
        runTestScenario((robot) -> {
            robot.activity().createActivityWithComponent();
            robot.checkShouldRestartOnDisplayMove(true);
        });
    }

    @Test
    @EnableFlags(FLAG_ENABLE_AUTO_RESTART_ON_DISPLAY_MOVE)
    @DisableCompatChanges({OVERRIDE_AUTO_RESTART_ON_DISPLAY_MOVE})
    public void testShouldRestartOnDisplayMove_overrideDisabled_returnsFalse() {
        runTestScenario((robot) -> {
            robot.activity().createActivityWithComponent();
            robot.checkShouldRestartOnDisplayMove(false);
        });
    }

    void runTestScenario(@NonNull Consumer<DisplayOverridesRobotTest> consumer) {
        final DisplayOverridesRobotTest robot = new DisplayOverridesRobotTest(this);
        consumer.accept(robot);
    }

    private static class DisplayOverridesRobotTest extends AppCompatRobotBase {

        DisplayOverridesRobotTest(@NonNull WindowTestsBase windowTestBase) {
            super(windowTestBase);
        }

        @Override
        void onPostActivityCreation(@NonNull ActivityRecord activity) {
            super.onPostActivityCreation(activity);
            spyOn(activity.mAppCompatController.getDisplayOverrides());
        }

        void checkShouldRestartOnDisplayMove(boolean expected) {
            assertEquals(expected, activity().top().mAppCompatController.getDisplayOverrides()
                    .shouldRestartOnDisplayMove());
        }
    }
}
