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

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.content.pm.ActivityInfo.CONFIG_COLOR_MODE;
import static android.content.pm.ActivityInfo.CONFIG_DENSITY;
import static android.content.pm.ActivityInfo.CONFIG_RESOURCES_UNUSED;
import static android.content.pm.ActivityInfo.CONFIG_TOUCHSCREEN;
import static android.content.pm.ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS;
import static android.content.pm.ActivityInfo.OVERRIDE_AUTO_RESTART_ON_DISPLAY_MOVE;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.window.flags.Flags.FLAG_ENABLE_AUTO_RECOVERY_FROM_SELF_KILL;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.compat.testing.PlatformCompatChangeRule;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.test.filters.MediumTest;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.Assert;
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

    private enum SelfKillType {
        FINISH_ACTIVITY,
        FINISH_AND_REMOVE_TASK,
        KILL_PROCESS
    }

    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();

    @Before
    public void setUp() {
        doReturn(false).when(mDisplayContent).shouldSleep();
        mDisplayContent.wakeIfNeeded();
    }

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

            robot.activity().moveTaskBetweenDisplays();
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

            robot.activity().moveTaskBetweenDisplays();
            robot.activity().checkTopActivityRelaunched(true);
            robot.checkRestartMenuVisibility(false);
        });
    }

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

            robot.activity().moveTaskBetweenDisplays();
            robot.activity().checkTopActivityRelaunched(false);
            robot.checkRestartMenuVisibility(true);

            robot.activity().applyToTopActivity(ActivityRecord::restartProcessIfVisible);
            robot.checkRestartMenuVisibility(false);
        });
    }

    @EnableCompatChanges(OVERRIDE_AUTO_RESTART_ON_DISPLAY_MOVE)
    @Test
    public void testAutoRestartOnDisplayMove_enabled_restartsApp() {
        runTestScenario((robot) -> {
            robot.activity().createSecondaryDisplay();
            robot.activity().createActivityWithComponent();
            robot.activity().setTopActivityResumed();
            robot.activity().clearInvocationsForActivity();

            robot.activity().moveTaskBetweenDisplays();

            robot.activity().checkTopActivityProcessRestarted(true);
        });
    }

    @DisableCompatChanges(OVERRIDE_AUTO_RESTART_ON_DISPLAY_MOVE)
    @Test
    public void testAutoRestartOnDisplayMove_compatChangeDisabled_doesNotRestartApp() {
        runTestScenario((robot) -> {
            robot.activity().createSecondaryDisplay();
            robot.activity().createActivityWithComponent();
            robot.activity().setTopActivityResumed();
            robot.activity().clearInvocationsForActivity();

            robot.activity().moveTaskBetweenDisplays();

            robot.activity().checkTopActivityProcessRestarted(false);
        });
    }

    @EnableFlags(FLAG_ENABLE_AUTO_RECOVERY_FROM_SELF_KILL)
    @Test
    public void testSelfKillRecoveryOnDisplayMove_finishActivity() {
        runTestScenario((robot) -> {
            robot.useSelfKillState(s -> s.mShouldRecoverFromSelfKill = true);
            robot.selfKillOnDisplayMove();
        });
    }

    @EnableFlags(FLAG_ENABLE_AUTO_RECOVERY_FROM_SELF_KILL)
    @Test
    public void testSelfKillRecoveryOnDisplayMove_disconnectDisplay() {
        runTestScenario((robot) -> {
            robot.useSelfKillState(s -> s.mRemoveDisplay = true);
            robot.selfKillOnDisplayMove();
        });
    }

    @EnableFlags(FLAG_ENABLE_AUTO_RECOVERY_FROM_SELF_KILL)
    @Test
    public void testSelfKillRecoveryOnDisplayMove_finishAndRemoveTask() {
        runTestScenario((robot) -> {
            robot.useSelfKillState(s -> {
                s.mShouldRecoverFromSelfKill = true;
                s.mSelfKillType = SelfKillType.FINISH_AND_REMOVE_TASK;
            });
            robot.selfKillOnDisplayMove();
        });
    }

    @EnableFlags(FLAG_ENABLE_AUTO_RECOVERY_FROM_SELF_KILL)
    @Test
    public void testSelfKillRecoveryOnDisplayMove_killProcess() {
        runTestScenario((robot) -> {
            robot.useSelfKillState(s -> {
                s.mShouldRecoverFromSelfKill = true;
                s.mSelfKillType = SelfKillType.KILL_PROCESS;
            });
            robot.selfKillOnDisplayMove();
        });
    }

    @DisableFlags(FLAG_ENABLE_AUTO_RECOVERY_FROM_SELF_KILL)
    @Test
    public void testSelfKillRecoveryOnDisplayMove_flagDisabled() {
        runTestScenario(DisplayCompatRobotTest::selfKillOnDisplayMove);
    }

    @EnableFlags(FLAG_ENABLE_AUTO_RECOVERY_FROM_SELF_KILL)
    @Test
    public void testSelfKillRecoveryNotOnDisplayMove() {
        runTestScenario((robot) -> {
            robot.useSelfKillState(s -> s.mMoveDisplays = false);
            robot.selfKillOnDisplayMove();
        });
    }

    @EnableFlags(FLAG_ENABLE_AUTO_RECOVERY_FROM_SELF_KILL)
    @Test
    public void testSelfKillRecoveryOnDisplayMove_noActivityRelaunch() {
        runTestScenario((robot) -> {
            robot.useSelfKillState(s -> s.mRelaunchActivity = false);
            robot.selfKillOnDisplayMove();
        });
    }

    @EnableFlags(FLAG_ENABLE_AUTO_RECOVERY_FROM_SELF_KILL)
    @Test
    public void testSelfKillRecoveryOnDisplayMove_nonStandardTypeActivity() {
        runTestScenario((robot) -> {
            robot.useSelfKillState(s -> s.mUseNonStandardTypeActivity = true);
            robot.selfKillOnDisplayMove();
        });
    }

    @EnableFlags(FLAG_ENABLE_AUTO_RECOVERY_FROM_SELF_KILL)
    @Test
    public void testSelfKillRecoveryOnDisplayMove_excludeFromRecents() {
        runTestScenario((robot) -> {
            robot.useSelfKillState(s -> s.mSetExcludeFromRecents = true);
            robot.selfKillOnDisplayMove();
        });
    }

    @EnableFlags(FLAG_ENABLE_AUTO_RECOVERY_FROM_SELF_KILL)
    @Test
    public void testSelfKillRecoveryOnDisplayMove_multipleActivities() {
        runTestScenario((robot) -> {
            robot.useSelfKillState(s -> {
                s.mShouldRecoverFromSelfKill = true;
                s.mLaunchMultipleActivities = true;
            });
            robot.selfKillOnDisplayMove();
        });
    }

    @EnableFlags(FLAG_ENABLE_AUTO_RECOVERY_FROM_SELF_KILL)
    @Test
    public void testSelfKillRecoveryOnDisplayMove_betweenInternalDisplays_flagEnabled() {
        runTestScenario((robot) -> {
            robot.useSelfKillState(s -> {
                s.mDisplayType = Display.TYPE_INTERNAL;
                s.mShouldRecoverFromSelfKill = true;
                s.mEnableRecoveryBetweenInternalDisplays = true;
            });
            robot.selfKillOnDisplayMove();
        });
    }

    @EnableFlags(FLAG_ENABLE_AUTO_RECOVERY_FROM_SELF_KILL)
    @Test
    public void testSelfKillRecoveryOnDisplayMove_betweenInternalDisplays_flagDisabled() {
        runTestScenario((robot) -> {
            robot.useSelfKillState(s -> s.mDisplayType = Display.TYPE_INTERNAL);
            robot.selfKillOnDisplayMove();
        });
    }

    void runTestScenario(@NonNull Consumer<DisplayCompatRobotTest> consumer) {
        final DisplayCompatRobotTest robot = new DisplayCompatRobotTest(this);
        consumer.accept(robot);
    }

    private static class SelfKillState {
        boolean mShouldRecoverFromSelfKill = false;
        boolean mMoveDisplays = true;
        boolean mRemoveDisplay = false;
        boolean mRelaunchActivity = true;
        boolean mLaunchMultipleActivities = false;
        boolean mEnableRecoveryBetweenInternalDisplays = false;
        boolean mUseNonStandardTypeActivity = false;
        boolean mSetExcludeFromRecents = false;
        int mDisplayType = Display.TYPE_EXTERNAL;
        SelfKillType mSelfKillType = SelfKillType.FINISH_ACTIVITY;
    }

    private static class DisplayCompatRobotTest extends AppCompatRobotBase {
        private final SelfKillState mSelfKillState = new SelfKillState();

        DisplayCompatRobotTest(@NonNull WindowTestsBase windowTestBase) {
            super(windowTestBase);
            windowTestBase.registerTestTransitionPlayer();
        }

        @Override
        void onPostActivityCreation(@android.annotation.NonNull ActivityRecord activity) {
            super.onPostActivityCreation(activity);

            final ActivityStartController startController =
                    activity.mAtmService.getActivityStartController();
            spyOn(startController);
            doReturn(0).when(startController).startActivityInPackage(anyInt(), anyInt(), anyInt(),
                    any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), anyInt(),
                    any(), any(), anyBoolean(), any(), anyBoolean());
        }

        void checkRestartMenuVisibility(boolean enabled) {
            activity().applyToTopActivity(activity -> assertEquals(enabled,
                    activity.getTask().getTaskInfo().appCompatTaskInfo
                            .isRestartMenuEnabledForDisplayMove()));
        }

        void checkSelfKillRecoveryExecuted(boolean executed) {
            verify(activity().top().mAtmService.getActivityStartController(),
                    times(executed ? 1 : 0)).startActivityInPackage(anyInt(), anyInt(), anyInt(),
                    any(), any(), any(), any(), any(), any(), anyInt(), anyInt(), any(), anyInt(),
                    any(), any(), anyBoolean(), any(), anyBoolean());
        }

        void useSelfKillState(Consumer<SelfKillState> consumer) {
            consumer.accept(mSelfKillState);
        }

        void selfKillOnDisplayMove() {
            activity().createActivityWithComponentInSecondaryDisplay(mSelfKillState.mDisplayType);
            if (mSelfKillState.mLaunchMultipleActivities) {
                activity().createActivityWithComponent();
            }
            if (mSelfKillState.mUseNonStandardTypeActivity) {
                spyOn(activity().top());
                when(activity().top().isActivityTypeStandardOrUndefined()).thenReturn(false);
            }
            if (mSelfKillState.mSetExcludeFromRecents) {
                activity().top().intent.addFlags(FLAG_EXCLUDE_FROM_RECENTS);
            }

            activity().setTopActivityResumed();
            activity().setTopActivityConfigChanges(
                    mSelfKillState.mRelaunchActivity ? 0 : CONFIG_RESOURCES_UNUSED);
            activity().clearInvocationsForActivity();

            if (mSelfKillState.mEnableRecoveryBetweenInternalDisplays) {
                spyOn(activity().top().mWmService.mAppCompatConfiguration);
                when(activity().top().mWmService.mAppCompatConfiguration
                        .isSelfKillRecoveryBetweenInternalDisplaysEnabled())
                        .thenReturn(true);
            }

            if (mSelfKillState.mMoveDisplays) {
                if (mSelfKillState.mRemoveDisplay) {
                    completeTransition();
                    activity().top().mRootWindowContainer
                            .onDisplayRemoved(activity().top().getDisplayId());
                }
                activity().moveTaskBetweenDisplays();
            } else {
                activity().setTaskWindowingMode(WINDOWING_MODE_FREEFORM);
            }

            emulateAndVerifySelfKill(mSelfKillState.mSelfKillType,
                    mSelfKillState.mShouldRecoverFromSelfKill);
        }

        void emulateAndVerifySelfKill(SelfKillType selfKillType,
                boolean shouldRecoverFromSelfKill) {
            if (selfKillType == SelfKillType.KILL_PROCESS) {
                activity().exitAppProcess();
                completeTransition();
                Assert.assertFalse(activity().top().finishing);
            } else {
                if (selfKillType == SelfKillType.FINISH_ACTIVITY) {
                    activity().finishTopActivity();
                } else { // SelfKillType.FINISH_AND_REMOVE_TASK
                    activity().removeTopTask();
                }
                activity().top().mAppCompatController.getDisplayCompatPolicy()
                        .onActivityFinishing();
                completeTransition();
                checkSelfKillRecoveryExecuted(shouldRecoverFromSelfKill);
            }
        }

        private void completeTransition() {
            final Transition transition =
                    activity().top().mTransitionController.getCollectingTransition();
            Assert.assertNotNull(transition);
            final ActionChain chain = ActionChain.testFinish(transition);
            activity().top().mWmService.mSyncEngine.abort(transition.getSyncId());
            transition.finishTransition(chain);
            testBase().waitHandlerIdle(activity().top().mWmService.mAtmService.mH);
        }
    }
}
