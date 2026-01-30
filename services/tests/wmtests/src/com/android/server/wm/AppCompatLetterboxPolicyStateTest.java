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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.graphics.Rect;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;

import com.android.window.flags.Flags;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.function.Consumer;

/**
 * Test class for {@link AppCompatLetterboxPolicy}.
 *
 * Build/Install/Run:
 * atest WmTests:AppCompatLetterboxPolicyStateTest
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class AppCompatLetterboxPolicyStateTest extends WindowTestsBase {

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    public void taskInfoDispatchedIfEventIsDoubleTapOnShellImplementation() {
        runTestScenario((robot) -> {
            robot.activity().createActivityWithComponent();
            robot.configureIsDoubleTapEvent(/* isDoubleTapEvent */ true);

            robot.invokeLayoutLetterboxIfNeeded();

            robot.verifyDispatchTaskInfoChangedIfNeeded(/* expected */ true, /* withValue */ true);
        });
    }

    @Test
    @EnableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    public void taskInfoNotDispatchedIfEventIsNotDoubleTapOnShellImplementation() {
        runTestScenario((robot) -> {
            robot.activity().createActivityWithComponent();
            robot.configureIsDoubleTapEvent(/* isDoubleTapEvent */ false);

            robot.invokeLayoutLetterboxIfNeeded();

            robot.verifyDispatchTaskInfoChangedIfNeeded(/* expected */ false, /* withValue */ true);
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    public void taskInfoDispatchedIfEventIsDoubleTapOnLegacyImplementation() {
        runTestScenario((robot) -> {
            robot.activity().createActivityWithComponent();
            robot.configureIsDoubleTapEvent(/* isDoubleTapEvent */ true);

            robot.invokeLayoutLetterboxIfNeeded();

            robot.verifyDispatchTaskInfoChangedIfNeeded(/* expected */ true, /* withValue */ true);
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_APP_COMPAT_REFACTORING)
    public void taskInfoNotDispatchedIfEventIsNotDoubleTapOnLegacyImplementation() {
        runTestScenario((robot) -> {
            robot.activity().createActivityWithComponent();
            robot.configureIsDoubleTapEvent(/* isDoubleTapEvent */ false);

            robot.invokeLayoutLetterboxIfNeeded();

            robot.verifyDispatchTaskInfoChangedIfNeeded(/* expected */ false, /* withValue */ true);
        });
    }

    /**
     * Runs a test scenario providing a Robot.
     */
    void runTestScenario(@NonNull Consumer<LetterboxPolicyStateRobotTest> consumer) {
        consumer.accept(new LetterboxPolicyStateRobotTest(this));
    }

    private static class LetterboxPolicyStateRobotTest extends AppCompatRobotBase {

        private final WindowState mWindowState = Mockito.mock(WindowState.class);

        LetterboxPolicyStateRobotTest(@NonNull WindowTestsBase windowTestBase) {
            super(windowTestBase);
            doReturn(new Rect(0, 0, 1000, 2000)).when(mWindowState).getFrame();
        }

        @Override
        void onPostActivityCreation(@NonNull ActivityRecord activity) {
            super.onPostActivityCreation(activity);
            spyOn(getReachabilityOverrides());
            spyOn(activity.getTask());
        }

        void invokeLayoutLetterboxIfNeeded() {
            getLetterboxPolicyState().layoutLetterboxIfNeeded(mWindowState);
        }

        void configureIsDoubleTapEvent(boolean isDoubleTapEvent) {
            doReturn(isDoubleTapEvent).when(getReachabilityOverrides()).isDoubleTapEvent();
        }

        void verifyDispatchTaskInfoChangedIfNeeded(boolean expected, boolean withValue) {
            verify(activity().top().getTask(),
                    times(expected ? 1 : 0)).dispatchTaskInfoChangedIfNeeded(eq(withValue));
        }

        @NonNull
        private AppCompatReachabilityOverrides getReachabilityOverrides() {
            return activity().top().mAppCompatController.getReachabilityOverrides();
        }

        @NonNull
        private AppCompatLetterboxPolicyState getLetterboxPolicyState() {
            return activity().top().mAppCompatController.getLetterboxPolicy().mLetterboxPolicyState;
        }
    }
}
