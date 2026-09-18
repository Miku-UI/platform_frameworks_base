/*
 * Copyright (C) 2026 The Android Open Source Project
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

import static android.content.pm.ActivityInfo.OVERRIDE_EXCLUDE_CAPTION_INSETS_FROM_APP_BOUNDS;
import static android.view.WindowManager.PROPERTY_COMPAT_ALLOW_EXCLUDE_CAPTION_INSETS;

import android.compat.testing.PlatformCompatChangeRule;
import android.platform.test.annotations.Presubmit;

import androidx.annotation.NonNull;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.util.function.Consumer;

/**
 * Test class for {@link AppCompatSandboxOverrides}.
 * <p/>
 * Build/Install/Run:
 * atest WmTests:AppCompatSandboxOverridesTest
 */
@Presubmit
@RunWith(WindowTestRunner.class)
public class AppCompatSandboxOverridesTest extends WindowTestsBase {

    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    @Test
    @EnableCompatChanges({OVERRIDE_EXCLUDE_CAPTION_INSETS_FROM_APP_BOUNDS})
    public void testIsOverrideExcludeCaptionInsetsAllowed_overrideEnabled_returnsTrue() {
        runTestScenario((robot) -> {
            robot.activity().createActivityWithComponent();
            robot.checkIsOverrideExcludeCaptionInsetsAllowed(/* expected */ true);
        });
    }

    @Test
    @DisableCompatChanges({OVERRIDE_EXCLUDE_CAPTION_INSETS_FROM_APP_BOUNDS})
    public void testIsOverrideExcludeCaptionInsetsAllowed_overrideDisabled_returnsFalse() {
        runTestScenario((robot) -> {
            robot.activity().createActivityWithComponent();
            robot.checkIsOverrideExcludeCaptionInsetsAllowed(/* expected */ false);
        });
    }

    @Test
    @EnableCompatChanges({OVERRIDE_EXCLUDE_CAPTION_INSETS_FROM_APP_BOUNDS})
    public void testIsOverrideExcludeCaptionInsetsAllowed_propFalse_overrideEnabled_returnsFalse() {
        runTestScenario((robot) -> {
            robot.prop().disable(PROPERTY_COMPAT_ALLOW_EXCLUDE_CAPTION_INSETS);
            robot.activity().createActivityWithComponent();
            robot.checkIsOverrideExcludeCaptionInsetsAllowed(/* expected */ false);
        });
    }

    @Test
    @DisableCompatChanges({OVERRIDE_EXCLUDE_CAPTION_INSETS_FROM_APP_BOUNDS})
    public void testIsOverrideExcludeCaptionInsetsAllowed_propTrue_overrideDisabled_returnsFalse() {
        runTestScenario((robot) -> {
            robot.prop().enable(PROPERTY_COMPAT_ALLOW_EXCLUDE_CAPTION_INSETS);
            robot.activity().createActivityWithComponent();
            robot.checkIsOverrideExcludeCaptionInsetsAllowed(/* expected */ false);
        });
    }

    @Test
    @EnableCompatChanges({OVERRIDE_EXCLUDE_CAPTION_INSETS_FROM_APP_BOUNDS})
    public void testIsOverrideExcludeCaptionInsetsAllowed_propTrue_overrideEnabled_returnsTrue() {
        runTestScenario((robot) -> {
            robot.prop().enable(PROPERTY_COMPAT_ALLOW_EXCLUDE_CAPTION_INSETS);
            robot.activity().createActivityWithComponent();
            robot.checkIsOverrideExcludeCaptionInsetsAllowed(/* expected */ true);
        });
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    void runTestScenario(@NonNull Consumer<SandboxOverridesRobotTest> consumer) {
        final SandboxOverridesRobotTest robot = new SandboxOverridesRobotTest(this);
        consumer.accept(robot);
    }

    private static class SandboxOverridesRobotTest extends AppCompatRobotBase {

        SandboxOverridesRobotTest(@NonNull WindowTestsBase windowTestBase) {
            super(windowTestBase);
        }


        void checkIsOverrideExcludeCaptionInsetsAllowed(boolean expected) {
            Assert.assertEquals(expected, activity().top().mAppCompatController
                    .getSandboxOverrides().isOverrideExcludeCaptionInsetsAllowed());
        }
    }
}
