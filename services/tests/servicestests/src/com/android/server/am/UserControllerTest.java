/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.am;

import static android.Manifest.permission.INTERACT_ACROSS_PROFILES;
import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.app.ActivityManager.STOP_USER_ON_SWITCH_FALSE;
import static android.app.ActivityManager.STOP_USER_ON_SWITCH_TRUE;
import static android.app.ActivityManagerInternal.ALLOW_FULL_ONLY;
import static android.app.ActivityManagerInternal.ALLOW_NON_FULL;
import static android.app.ActivityManagerInternal.ALLOW_NON_FULL_IN_PROFILE;
import static android.app.ActivityManagerInternal.ALLOW_PROFILES_OR_NON_FULL;
import static android.app.KeyguardManager.LOCK_ON_USER_SWITCH_CALLBACK;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.UserManager.USER_TYPE_PROFILE_MANAGED;
import static android.testing.DexmakerShareClassLoaderRule.runWithDexmakerShareClassLoader;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.server.am.UserController.CLEAR_USER_JOURNEY_SESSION_MSG;
import static com.android.server.am.UserController.COMPLETE_USER_SWITCH_MSG;
import static com.android.server.am.UserController.CONTINUE_USER_SWITCH_MSG;
import static com.android.server.am.UserController.REPORT_LOCKED_BOOT_COMPLETE_MSG;
import static com.android.server.am.UserController.REPORT_USER_SWITCH_COMPLETE_MSG;
import static com.android.server.am.UserController.REPORT_USER_SWITCH_MSG;
import static com.android.server.am.UserController.SCHEDULE_JUDGE_FATE_OF_BACKGROUND_USER_MSG;
import static com.android.server.am.UserController.SCHEDULE_STOP_BACKGROUND_USER_MSG;
import static com.android.server.am.UserController.USER_COMPLETED_EVENT_MSG;
import static com.android.server.am.UserController.USER_CURRENT_MSG;
import static com.android.server.am.UserController.USER_START_MSG;
import static com.android.server.am.UserController.USER_SWITCH_TIMEOUT_MSG;
import static com.android.server.pm.UserManagerInternal.USER_START_MODE_BACKGROUND;
import static com.android.server.pm.UserManagerInternal.USER_START_MODE_FOREGROUND;

import static com.google.android.collect.Lists.newArrayList;
import static com.google.android.collect.Sets.newHashSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.annotation.SpecialUsers.CanBeNULL;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.IUserSwitchObserver;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.pm.UserInfo.UserInfoFlag;
import android.media.AudioManagerInternal;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IRemoteCallback;
import android.os.IpcDataCache;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManagerInternal;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.ArraySet;
import android.util.Log;
import android.util.StringBuilderPrinter;
import android.util.TimeUtils;
import android.view.Display;

import androidx.test.filters.SmallTest;

import com.android.internal.widget.LockPatternUtils;
import com.android.server.AlarmManagerInternal;
import com.android.server.FgThread;
import com.android.server.SystemService;
import com.android.server.am.UserController.UserAndLmkThreshold;
import com.android.server.am.UserState.KeyEvictedCallback;
import com.android.server.locksettings.LockSettingsInternal;
import com.android.server.pm.UserJourneyLogger;
import com.android.server.pm.UserManagerInternal;
import com.android.server.pm.UserManagerService;
import com.android.server.pm.UserTypeDetails;
import com.android.server.pm.UserTypeFactory;
import com.android.server.wm.WindowManagerService;

import com.google.common.collect.Range;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tests for {@link UserController}.
 *
 * Build/Install/Run:
 *  atest FrameworksServicesTests:UserControllerTest
 */
@SmallTest
@Presubmit
public class UserControllerTest {
    // Use big enough user id to avoid picking up already active user id.
    private static final int TEST_USER_ID = 100; // This user is uniquely pre-setup in setUp()
    private static final int TEST_USER_ID1 = 101;
    private static final int TEST_USER_ID2 = 102;
    private static final int TEST_USER_ID3 = 103;
    private static final int SYSTEM_USER_ID = UserHandle.SYSTEM.getIdentifier();
    private static final int NONEXIST_USER_ID = 2;
    private static final int TEST_PRE_CREATED_USER_ID = 103; // This user is pre-setup in setUp()

    private static final int DEFAULT_USER_FLAGS = UserInfo.FLAG_FULL;

    private static final String TAG = UserControllerTest.class.getSimpleName();

    private static final long HANDLER_WAIT_TIME_MS = 100;

    private UserController mUserController;

    // Used by tests that assert state when it's not ready
    private UserController mNotReadyUserController;

    private TestInjector mInjector;
    private final HashMap<Integer, UserState> mUserStates = new HashMap<>();
    private final HashMap<Integer, UserInfo> mUserInfos = new HashMap<>();

    private final KeyEvictedCallback mKeyEvictedCallback = (userId) -> { /* ignore */ };

    private static final List<String> START_FOREGROUND_USER_ACTIONS = newArrayList(
            Intent.ACTION_USER_STARTED,
            Intent.ACTION_USER_STARTING);

    private static final List<String> START_FOREGROUND_USER_DEFERRED_ACTIONS = newArrayList(
            Intent.ACTION_USER_SWITCHED);

    private static final List<String> START_BACKGROUND_USER_ACTIONS = newArrayList(
            Intent.ACTION_USER_STARTED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_USER_STARTING);

    private static final Set<Integer> START_FOREGROUND_USER_MESSAGE_CODES = newHashSet(
            REPORT_USER_SWITCH_MSG,
            USER_SWITCH_TIMEOUT_MSG,
            USER_START_MSG,
            USER_CURRENT_MSG);

    private static final Set<Integer> START_BACKGROUND_USER_MESSAGE_CODES = newHashSet(
            USER_START_MSG,
            REPORT_LOCKED_BOOT_COMPLETE_MSG);

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Before
    public void setUp() throws Exception {
        runWithDexmakerShareClassLoader(() -> {
            // Disable binder caches in this process.
            IpcDataCache.disableForTestMode();

            mInjector = spy(new TestInjector(getInstrumentation().getTargetContext()));
            doNothing().when(mInjector).clearAllLockedTasks(anyString());
            doNothing().when(mInjector).startHomeActivity(anyInt(), anyString());
            doReturn(false).when(mInjector).taskSupervisorSwitchUser(anyInt(), any());
            doNothing().when(mInjector).taskSupervisorResumeFocusedStackTopActivity();
            doNothing().when(mInjector).systemServiceManagerOnUserStopped(anyInt());
            doNothing().when(mInjector).systemServiceManagerOnUserCompletedEvent(
                    anyInt(), anyInt());
            doNothing().when(mInjector).activityManagerForceStopUserPackages(anyInt(),
                    anyString(), anyBoolean());
            doNothing().when(mInjector).activityManagerOnUserStopped(anyInt());
            doNothing().when(mInjector).clearBroadcastQueueForUser(anyInt());
            doNothing().when(mInjector).taskSupervisorRemoveUser(anyInt());
            doNothing().when(mInjector).lockDeviceNowAndWaitForKeyguardShown();
            mockIsUsersOnSecondaryDisplaysEnabled(false);
            // All UserController params are set to default.

            // Starts with a generic assumption that the user starts visible, but on tests where
            // that's not the case, the test should call mockAssignUserToMainDisplay()
            doReturn(UserManagerInternal.USER_ASSIGNMENT_RESULT_SUCCESS_VISIBLE)
                    .when(mInjector.mUserManagerInternalMock)
                    .assignUserToDisplayOnStart(anyInt(), anyInt(), anyInt(), anyInt());

            mUserController = new UserController(mInjector);
            mUserController.setAllowUserUnlocking(true);
            mUserController.onSystemReady();

            mNotReadyUserController = new UserController(mInjector);
            mNotReadyUserController.setAllowUserUnlocking(true);

            setUpUser(TEST_USER_ID, DEFAULT_USER_FLAGS);
            setUpUser(TEST_PRE_CREATED_USER_ID, DEFAULT_USER_FLAGS, /* preCreated= */ true, null);
            mInjector.mRelevantUser = null;
        });
    }

    @After
    public void tearDown() throws Exception {
        mInjector.mHandlerThread.quit();
        validateMockitoUsage();
    }

    @Test
    public void testStartUser_foreground_notReady() {
        var e = assertThrows(IllegalStateException.class,
                () -> mNotReadyUserController.startUser(TEST_USER_ID, USER_START_MODE_FOREGROUND));

        assertThat(e).hasMessageThat().isEqualTo(String.format(Locale.ENGLISH,
                UserController.EXCEPTION_TEMPLATE_CANNOT_START_USER_WHEN_NOT_READY, TEST_USER_ID));
    }


    @Test
    public void testStartUser_foreground() {
        mUserController.startUser(TEST_USER_ID, USER_START_MODE_FOREGROUND);
        verify(mInjector, never()).dismissUserSwitchingDialog(any());
        verify(mInjector.getWindowManager(), times(1)).setSwitchingUser(anyBoolean());
        verify(mInjector.getWindowManager()).setSwitchingUser(true);
        verify(mInjector).clearAllLockedTasks(anyString());
        startForegroundUserAssertions();
        verifyUserAssignedToDisplay(TEST_USER_ID, Display.DEFAULT_DISPLAY);
    }

    @Test
    public void testStartUser_background_notReady() {
        var e = assertThrows(IllegalStateException.class,
                () -> mNotReadyUserController.startUser(TEST_USER_ID, USER_START_MODE_BACKGROUND));

        assertThat(e).hasMessageThat().isEqualTo(String.format(Locale.ENGLISH,
                UserController.EXCEPTION_TEMPLATE_CANNOT_START_USER_WHEN_NOT_READY, TEST_USER_ID));

    }

    @Test
    public void testStartUser_background() {
        mUserController.setInitialConfig(/* mUserSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 3, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        boolean started = mUserController.startUser(TEST_USER_ID, USER_START_MODE_BACKGROUND);
        assertWithMessage("startUser(%s, foreground=false)", TEST_USER_ID).that(started).isTrue();
        verify(mInjector, never()).showUserSwitchingDialog(
                any(), any(), anyString(), anyString(), any());
        verify(mInjector.getWindowManager(), never()).setSwitchingUser(anyBoolean());
        verify(mInjector, never()).clearAllLockedTasks(anyString());
        startBackgroundUserAssertions();
        verifyUserAssignedToDisplay(TEST_USER_ID, Display.DEFAULT_DISPLAY);
    }

    @Test
    public void testStartUser_background_duringBootHsum() {
        mUserController.setInitialConfig(/* mUserSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 3, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);
        mockIsHeadlessSystemUserMode(true);
        mUserController.setAllowUserUnlocking(false);
        mInjector.mRelevantUser = TEST_USER_ID;

        boolean started = mUserController.startUser(TEST_USER_ID, USER_START_MODE_BACKGROUND);
        assertWithMessage("startUser(%s, foreground=false)", TEST_USER_ID).that(started).isTrue();

        // ACTION_LOCKED_BOOT_COMPLETED not sent yet
        startUserAssertions(newArrayList(Intent.ACTION_USER_STARTED, Intent.ACTION_USER_STARTING),
                START_BACKGROUND_USER_MESSAGE_CODES);

        mUserController.onBootComplete(null);

        startUserAssertions(newArrayList(Intent.ACTION_USER_STARTED, Intent.ACTION_USER_STARTING,
                        Intent.ACTION_LOCKED_BOOT_COMPLETED),
                START_BACKGROUND_USER_MESSAGE_CODES);
    }

    @Test
    public void testStartUser_sendsNoBroadcastsForSystemUserInNonHeadlessMode() {
        mockIsHeadlessSystemUserMode(false);

        mUserController.startUser(SYSTEM_USER_ID, USER_START_MODE_FOREGROUND);

        assertWithMessage("Broadcasts for starting the system user in non-headless mode")
                .that(mInjector.mSentIntents).isEmpty();
    }

    @Test
    public void testStartUser_sendsBroadcastsForSystemUserInHeadlessMode() {
        mockIsHeadlessSystemUserMode(true);

        mUserController.startUser(SYSTEM_USER_ID, USER_START_MODE_FOREGROUND);

        assertWithMessage("Broadcasts for starting the system user in headless mode")
                .that(getActions(mInjector.mSentIntents)).containsExactly(
                        Intent.ACTION_USER_STARTED, Intent.ACTION_USER_STARTING);
    }

    @Test
    public void testStartUser_displayAssignmentFailed() {
        doReturn(UserManagerInternal.USER_ASSIGNMENT_RESULT_FAILURE)
                .when(mInjector.mUserManagerInternalMock)
                .assignUserToDisplayOnStart(eq(TEST_USER_ID), anyInt(),
                        eq(USER_START_MODE_FOREGROUND), anyInt());

        boolean started = mUserController.startUser(TEST_USER_ID, USER_START_MODE_FOREGROUND);

        assertWithMessage("startUser(%s, foreground=true)", TEST_USER_ID).that(started).isFalse();
    }

    @Test
    public void testStartUserVisibleOnDisplay() {
        mUserController.setInitialConfig(/* mUserSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 3, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        boolean started = mUserController.startUserVisibleOnDisplay(TEST_USER_ID, 42,
                /* unlockProgressListener= */ null);

        assertWithMessage("startUserOnDisplay(%s, %s)", TEST_USER_ID, 42).that(started).isTrue();
        verifyUserAssignedToDisplay(TEST_USER_ID, 42);

        verify(mInjector, never()).showUserSwitchingDialog(
                any(), any(), anyString(), anyString(), any());
        verify(mInjector.getWindowManager(), never()).setSwitchingUser(anyBoolean());
        verify(mInjector, never()).clearAllLockedTasks(anyString());
        startBackgroundUserAssertions();
    }

    @Test
    public void testStartUserUIDisabled() {
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ false,
                /* maxRunningUsers= */ 3, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        mUserController.startUser(TEST_USER_ID, USER_START_MODE_FOREGROUND);
        verify(mInjector, never()).showUserSwitchingDialog(
                any(), any(), anyString(), anyString(), any());
        verify(mInjector, never()).dismissUserSwitchingDialog(any());
        verify(mInjector.getWindowManager(), never()).setSwitchingUser(anyBoolean());
        startForegroundUserAssertions();
    }

    @Test
    public void testStartPreCreatedUser_foreground() {
        assertFalse(
                mUserController.startUser(TEST_PRE_CREATED_USER_ID, USER_START_MODE_FOREGROUND));
        // Make sure no intents have been fired for pre-created users.
        assertTrue(mInjector.mSentIntents.isEmpty());

        verifyUserNeverAssignedToDisplay();
    }

    @Test
    public void testStartPreCreatedUser_background() throws Exception {
        mUserController.setInitialConfig(/* mUserSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 3, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        assertTrue(mUserController.startUser(TEST_PRE_CREATED_USER_ID, USER_START_MODE_BACKGROUND));
        // Make sure no intents have been fired for pre-created users.
        assertTrue(mInjector.mSentIntents.isEmpty());

        verify(mInjector, never()).showUserSwitchingDialog(
                any(), any(), anyString(), anyString(), any());
        verify(mInjector.getWindowManager(), never()).setSwitchingUser(anyBoolean());
        verify(mInjector, never()).clearAllLockedTasks(anyString());

        assertWithMessage("should not have received intents")
                .that(getActions(mInjector.mSentIntents)).isEmpty();
        // TODO(b/140868593): should have received a USER_UNLOCK_MSG message as well, but it doesn't
        // because StorageManager.isCeStorageUnlocked(TEST_PRE_CREATED_USER_ID) returns false - to
        // properly fix it, we'd need to move this class to FrameworksMockingServicesTests so we can
        // mock static methods (but moving this class would involve changing the presubmit tests,
        // and the cascade effect goes on...). In fact, a better approach would to not assert the
        // binder calls, but their side effects (in this case, that the user is stopped right away)
        assertWithMessage("wrong binder message calls").that(mInjector.mHandler.getMessageCodes())
                .containsExactly(USER_START_MSG);
    }

    private void startUserAssertions(
            List<String> expectedActions, Set<Integer> expectedMessageCodes) {
        assertEquals(expectedActions, getActions(mInjector.mSentIntents));
        Set<Integer> actualCodes = mInjector.mHandler.getMessageCodes();
        assertEquals("Unexpected message sent", expectedMessageCodes, actualCodes);
    }

    private void startBackgroundUserAssertions() {
        startUserAssertions(START_BACKGROUND_USER_ACTIONS, START_BACKGROUND_USER_MESSAGE_CODES);
    }

    private void startForegroundUserAssertions() {
        startUserAssertions(START_FOREGROUND_USER_ACTIONS, START_FOREGROUND_USER_MESSAGE_CODES);
        Message reportMsg = mInjector.mHandler.getMessageForCode(REPORT_USER_SWITCH_MSG);
        assertNotNull(reportMsg);
        UserState userState = (UserState) reportMsg.obj;
        assertNotNull(userState);
        assertEquals(TEST_USER_ID, userState.mHandle.getIdentifier());
        assertEquals("User must be in STATE_BOOTING", UserState.STATE_BOOTING, userState.state);
        assertEquals("Unexpected old user id", 0, reportMsg.arg1);
        assertEquals("Unexpected new user id", TEST_USER_ID, reportMsg.arg2);
        verifyUserAssignedToDisplay(TEST_USER_ID, Display.DEFAULT_DISPLAY);
    }

    @Test
    public void testFailedStartUserInForeground() {
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ false,
                /* maxRunningUsers= */ 3, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        mUserController.startUserInForeground(NONEXIST_USER_ID);
        verify(mInjector.getWindowManager(), times(1)).setSwitchingUser(anyBoolean());
        verify(mInjector.getWindowManager()).setSwitchingUser(false);

        verifyUserNeverAssignedToDisplay();
    }

    @Test
    public void testDispatchUserSwitch() throws RemoteException {
        // Prepare mock observer and register it
        IUserSwitchObserver observer = registerUserSwitchObserver(
                /* replyToOnBeforeUserSwitchingCallback= */ true,
                /* replyToOnUserSwitchingCallback= */ true);
        // Start user -- this will update state of mUserController
        mUserController.startUser(TEST_USER_ID, USER_START_MODE_FOREGROUND);
        verify(observer, times(1)).onBeforeUserSwitching(eq(TEST_USER_ID), any());
        Message reportMsg = mInjector.mHandler.getMessageForCode(REPORT_USER_SWITCH_MSG);
        assertNotNull(reportMsg);
        UserState userState = (UserState) reportMsg.obj;
        int oldUserId = reportMsg.arg1;
        int newUserId = reportMsg.arg2;
        // Call dispatchUserSwitch and verify that observer was called only once
        mInjector.mHandler.clearAllRecordedMessages();
        mUserController.dispatchUserSwitch(userState, oldUserId, newUserId);
        verify(observer, times(1)).onUserSwitching(eq(TEST_USER_ID), any());
        Set<Integer> expectedCodes = Collections.singleton(CONTINUE_USER_SWITCH_MSG);
        Set<Integer> actualCodes = mInjector.mHandler.getMessageCodes();
        assertEquals("Unexpected message sent", expectedCodes, actualCodes);
        Message conMsg = mInjector.mHandler.getMessageForCode(CONTINUE_USER_SWITCH_MSG);
        assertNotNull(conMsg);
        userState = (UserState) conMsg.obj;
        assertNotNull(userState);
        assertEquals(TEST_USER_ID, userState.mHandle.getIdentifier());
        assertEquals("User must be in STATE_BOOTING", UserState.STATE_BOOTING, userState.state);
        assertEquals("Unexpected old user id", 0, conMsg.arg1);
        assertEquals("Unexpected new user id", TEST_USER_ID, conMsg.arg2);
    }

    @Test
    public void testDispatchUserSwitchBadReceiver() throws RemoteException {
        // Prepare mock observer which doesn't notify the onUserSwitching callback and register it
        IUserSwitchObserver observer = registerUserSwitchObserver(
                /* replyToOnBeforeUserSwitchingCallback= */ true,
                /* replyToOnUserSwitchingCallback= */ false);
        // Start user -- this will update state of mUserController
        mUserController.startUser(TEST_USER_ID, USER_START_MODE_FOREGROUND);
        verify(observer, times(1)).onBeforeUserSwitching(eq(TEST_USER_ID), any());
        Message reportMsg = mInjector.mHandler.getMessageForCode(REPORT_USER_SWITCH_MSG);
        assertNotNull(reportMsg);
        UserState userState = (UserState) reportMsg.obj;
        int oldUserId = reportMsg.arg1;
        int newUserId = reportMsg.arg2;
        // Call dispatchUserSwitch and verify that observer was called only once
        mInjector.mHandler.clearAllRecordedMessages();
        mUserController.dispatchUserSwitch(userState, oldUserId, newUserId);
        verify(observer, times(1)).onUserSwitching(eq(TEST_USER_ID), any());
        // Verify that CONTINUE_USER_SWITCH_MSG is not sent (triggers timeout)
        Set<Integer> actualCodes = mInjector.mHandler.getMessageCodes();
        assertWithMessage("No messages should be sent").that(actualCodes).isEmpty();
    }

    private void continueAndCompleteUserSwitch(UserState userState, int oldUserId, int newUserId) {
        mUserController.continueUserSwitch(userState, oldUserId, newUserId);
        mInjector.mHandler.removeMessages(UserController.COMPLETE_USER_SWITCH_MSG);
        mUserController.completeUserSwitch(oldUserId, newUserId);
    }

    @Test
    public void testContinueUserSwitch() {
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 3, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);
        // Start user -- this will update state of mUserController
        mUserController.startUser(TEST_USER_ID, USER_START_MODE_FOREGROUND);
        Message reportMsg = mInjector.mHandler.getMessageForCode(REPORT_USER_SWITCH_MSG);
        assertNotNull(reportMsg);
        UserState userState = (UserState) reportMsg.obj;
        int oldUserId = reportMsg.arg1;
        int newUserId = reportMsg.arg2;
        mInjector.mHandler.clearAllRecordedMessages();
        // Verify that continueUserSwitch worked as expected
        continueAndCompleteUserSwitch(userState, oldUserId, newUserId);
        verify(mInjector, times(1)).dismissUserSwitchingDialog(any());
        continueUserSwitchAssertions(oldUserId, TEST_USER_ID, false, false);
        verifySystemUserVisibilityChangesNeverNotified();
    }

    @Test
    public void testContinueUserSwitchUIDisabled() {
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ false,
                /* maxRunningUsers= */ 3, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        // Start user -- this will update state of mUserController
        mUserController.startUser(TEST_USER_ID, USER_START_MODE_FOREGROUND);
        Message reportMsg = mInjector.mHandler.getMessageForCode(REPORT_USER_SWITCH_MSG);
        assertNotNull(reportMsg);
        UserState userState = (UserState) reportMsg.obj;
        int oldUserId = reportMsg.arg1;
        int newUserId = reportMsg.arg2;
        mInjector.mHandler.clearAllRecordedMessages();
        // Verify that continueUserSwitch worked as expected
        continueAndCompleteUserSwitch(userState, oldUserId, newUserId);
        verify(mInjector, never()).dismissUserSwitchingDialog(any());
        continueUserSwitchAssertions(oldUserId, TEST_USER_ID, false, false);
    }

    @Test
    public void testLogoutUserDuringSwitchToSameUser_nonHsum()
            throws InterruptedException {
        // TODO(b/428046912): This test doesn't actually test anything. The switch isn't sufficient,
        //  so the list of running users will never contain the test user anyway.
        mockIsHeadlessSystemUserMode(false);

        // Start user -- this will update state of mUserController
        mUserController.startUser(TEST_USER_ID1, USER_START_MODE_FOREGROUND);
        waitForHandlerToComplete(mInjector.mHandler, HANDLER_WAIT_TIME_MS);
        // When logoutUser runs, the switchUser is still in progress.
        mUserController.switchUser(TEST_USER_ID2);
        mUserController.logoutUser(TEST_USER_ID2);
        waitForHandlerToComplete(mInjector.mHandler, HANDLER_WAIT_TIME_MS);

        // Verify that TEST_USER_ID2 is not running.
        List<Integer> runningUserIds = mUserController.getRunningUsersLU();
        assertFalse(runningUserIds.contains(TEST_USER_ID2));
    }

    @Test
    public void testLogoutUserDuringSwitchToSameUser_hsumAndInteractiveSystemUser()
            throws InterruptedException {
        // TODO(b/428046912): This test doesn't actually test anything. The switch isn't sufficient,
        //  so the list of running users will never contain the test user anyway.
        mockIsSwitchableHeadlessSystemUserMode();

        // Start user -- this will update state of mUserController
        mUserController.startUser(TEST_USER_ID1, USER_START_MODE_FOREGROUND);
        waitForHandlerToComplete(mInjector.mHandler, HANDLER_WAIT_TIME_MS);
        // When logoutUser runs, the switchUser is still in progress.
        mUserController.switchUser(TEST_USER_ID2);
        mUserController.logoutUser(TEST_USER_ID2);
        waitForHandlerToComplete(mInjector.mHandler, HANDLER_WAIT_TIME_MS);

        // Verify that TEST_USER_ID2 is not running.
        List<Integer> runningUserIds = mUserController.getRunningUsersLU();
        assertFalse(runningUserIds.contains(TEST_USER_ID2));
    }

    @Test
    public void testLogoutUser_nonHsum() throws InterruptedException {
        mockIsHeadlessSystemUserMode(false);
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 3, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        // Switch to the test user.
        addForegroundUserAndContinueUserSwitch(TEST_USER_ID, SYSTEM_USER_ID, 1,
                /* expectOldUserStopping= */false,
                /* expectScheduleBackgroundUserJudgement= */ false);
        assertTrue(mUserController.getRunningUsersLU().contains(TEST_USER_ID));

        // Logout the test user.
        mUserController.logoutUser(TEST_USER_ID);
        waitForHandlerToComplete(mInjector.mHandler, HANDLER_WAIT_TIME_MS);

        // Verify that TEST_USER_ID is no longer running.
        assertFalse(mUserController.getRunningUsersLU().contains(TEST_USER_ID));
    }

    @Test
    public void testLogoutUser_hsumAndInteractiveSystemUser() throws InterruptedException {
        mockIsSwitchableHeadlessSystemUserMode();
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 3, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        // Switch to the test user.
        addForegroundUserAndContinueUserSwitch(TEST_USER_ID, SYSTEM_USER_ID, 1,
                /* expectOldUserStopping= */false,
                /* expectScheduleBackgroundUserJudgement= */ false);
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID);
        assertEquals("Unexpected current user", TEST_USER_ID, mUserController.getCurrentUserId());

        // Logout the test user.
        mUserController.logoutUser(TEST_USER_ID);
        waitForHandlerToComplete(mInjector.mHandler, HANDLER_WAIT_TIME_MS);

        // Verify that TEST_USER_ID is no longer running.
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID);
        // Current policy is that interactive HSUM should specifically log out to the system user.
        assertEquals("Logout should always be to the system user in iHSUM",
                SYSTEM_USER_ID, mUserController.getCurrentOrTargetUserId());
    }

    private void continueUserSwitchAssertions(int expectedOldUserId, int expectedNewUserId,
            boolean backgroundUserStopping, boolean expectScheduleBackgroundUserJudgement) {
        Set<Integer> expectedCodes = new LinkedHashSet<>();
        expectedCodes.add(COMPLETE_USER_SWITCH_MSG);
        expectedCodes.add(REPORT_USER_SWITCH_COMPLETE_MSG);
        if (backgroundUserStopping) {
            expectedCodes.add(CLEAR_USER_JOURNEY_SESSION_MSG);
        }
        if (expectScheduleBackgroundUserJudgement) {
            expectedCodes.add(SCHEDULE_JUDGE_FATE_OF_BACKGROUND_USER_MSG);
        }
        Set<Integer> actualCodes = mInjector.mHandler.getMessageCodes();
        assertEquals("Unexpected message sent", expectedCodes, actualCodes);
        Message msg = mInjector.mHandler.getMessageForCode(REPORT_USER_SWITCH_COMPLETE_MSG);
        assertNotNull(msg);
        assertEquals("Unexpected oldUserId", expectedOldUserId, msg.arg1);
        assertEquals("Unexpected newUserId", expectedNewUserId, msg.arg2);
    }

    @Test
    public void testDispatchUserSwitchComplete() throws RemoteException {
        // Prepare mock observer and register it
        IUserSwitchObserver observer = registerUserSwitchObserver(
                /* replyToOnBeforeUserSwitchingCallback= */ true,
                /* replyToOnUserSwitchingCallback= */ true);
        // Start user -- this will update state of mUserController
        mUserController.startUser(TEST_USER_ID, USER_START_MODE_FOREGROUND);
        Message reportMsg = mInjector.mHandler.getMessageForCode(REPORT_USER_SWITCH_MSG);
        assertNotNull(reportMsg);
        int oldUserId = reportMsg.arg1;
        int newUserId = reportMsg.arg2;
        mInjector.mHandler.clearAllRecordedMessages();
        // Mockito can't reset only interactions, so just verify that this hasn't been
        // called with 'false' until after dispatchUserSwitchComplete.
        verify(mInjector.getWindowManager(), never()).setSwitchingUser(false);
        // Call dispatchUserSwitchComplete
        mUserController.dispatchUserSwitchComplete(oldUserId, newUserId);
        verify(observer, times(1)).onUserSwitchComplete(anyInt());
        verify(observer).onUserSwitchComplete(TEST_USER_ID);
        verify(mInjector.getWindowManager(), times(1)).setSwitchingUser(false);
        startUserAssertions(Stream.concat(
                        START_FOREGROUND_USER_ACTIONS.stream(),
                        START_FOREGROUND_USER_DEFERRED_ACTIONS.stream()
                ).collect(Collectors.toList()), Collections.emptySet());
    }

    /** Test scheduling judgement of background users after a user-switch. */
    @Test
    public void testScheduleJudgementOfBackgroundUser_switch() {
        mSetFlagsRule.enableFlags(
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER,
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER_BY_DEFAULT);
        assumeFalse(UserManager.isVisibleBackgroundUsersEnabled());

        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 10, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ 2);

        setUpUser(TEST_USER_ID1, DEFAULT_USER_FLAGS);

        // Switch to TEST_USER_ID from user 0
        int numberOfUserSwitches = 0;
        addForegroundUserAndContinueUserSwitch(TEST_USER_ID, SYSTEM_USER_ID,
                ++numberOfUserSwitches,
                /* expectOldUserStopping= */false,
                /* expectScheduleBackgroundUserJudgement= */ false);
        assertRunningUsersInOrder(SYSTEM_USER_ID, TEST_USER_ID);

        // Allow the post-switch processing to complete (there should be no scheduled judgement).
        assertAndProcessBackgroundUserJudgementUntilStop(false, UserHandle.USER_NULL);
        assertRunningUsersInOrder(SYSTEM_USER_ID, TEST_USER_ID);

        // Switch to TEST_USER_ID1 from TEST_USER_ID
        addForegroundUserAndContinueUserSwitch(TEST_USER_ID1, TEST_USER_ID,
                ++numberOfUserSwitches,
                /* expectOldUserStopping= */false,
                /* expectScheduleBackgroundUserJudgement= */ true);
        assertRunningUsersInOrder(SYSTEM_USER_ID, TEST_USER_ID, TEST_USER_ID1);

        // Switch back to TEST_USER_ID from TEST_USER_ID1
        addForegroundUserAndContinueUserSwitch(TEST_USER_ID, TEST_USER_ID1,
                ++numberOfUserSwitches,
                /* expectOldUserStopping= */false,
                /* expectScheduleBackgroundUserJudgement= */ true);
        assertRunningUsersInOrder(SYSTEM_USER_ID, TEST_USER_ID1, TEST_USER_ID);

        // Allow the post-switch processing to complete.
        assertAndProcessBackgroundUserJudgementUntilStop(false, TEST_USER_ID);
        assertAndProcessBackgroundUserJudgementUntilStop(true, TEST_USER_ID1);
        assertAndProcessBackgroundUserJudgementUntilStop(false, UserHandle.USER_NULL);
        assertRunningUsersInOrder(SYSTEM_USER_ID, TEST_USER_ID);
    }

    /** Test scheduling judgement of background users that were started in the background. */
    @Test
    public void testScheduleJudgementOfBackgroundUser_startInBackground() throws Exception {
        mSetFlagsRule.enableFlags(
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER,
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER_BY_DEFAULT);
        assumeFalse(UserManager.isVisibleBackgroundUsersEnabled());

        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 10, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ 2);

        // Start two full background users (which should both get scheduled for stopping)
        // and one profile (which should not according to current policy since startProfile employs
        // USER_START_MODE_BACKGROUND_VISIBLE).
        setUpAndStartUserInBackground(TEST_USER_ID);
        setUpAndStartUserInBackground(TEST_USER_ID1);
        setUpAndStartProfileInBackground(TEST_USER_ID2, USER_TYPE_PROFILE_MANAGED);

        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID, TEST_USER_ID1, TEST_USER_ID2);

        assertAndProcessBackgroundUserJudgementUntilStop(true, TEST_USER_ID);
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID1, TEST_USER_ID2);

        assertAndProcessBackgroundUserJudgementUntilStop(true, TEST_USER_ID1);
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID2);

        assertAndProcessBackgroundUserJudgementUntilStop(false, TEST_USER_ID2);
        assertAndProcessBackgroundUserJudgementUntilStop(false, UserHandle.USER_NULL);

        // Now that we've processed the stops, let's make sure that a subsequent one will work too.
        setUpAndStartUserInBackground(TEST_USER_ID3);
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID2, TEST_USER_ID3);
        assertAndProcessBackgroundUserJudgementUntilStop(true, TEST_USER_ID3);
        assertAndProcessBackgroundUserJudgementUntilStop(false, UserHandle.USER_NULL);
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID2);
    }

    /** Test lack of scheduling judgement of background users if config has it disabled. */
    @Test
    public void testScheduleJudgementOfBackgroundUser_configOff() throws Exception {
        mSetFlagsRule.enableFlags(
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER,
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER_BY_DEFAULT);
        assumeFalse(UserManager.isVisibleBackgroundUsersEnabled());

        // Disable default background scheduled judging via a value of -1.
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 10, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        setUpAndStartUserInBackground(TEST_USER_ID);

        assertAndProcessBackgroundUserJudgementUntilStop(false, TEST_USER_ID);
    }

    /** Test no scheduling judgement for foreground profiles until they leave the foreground. */
    @Test
    public void testScheduleJudgementOfBackgroundUser_foregroundProfiles() throws Exception {
        mSetFlagsRule.enableFlags(
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER,
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER_BY_DEFAULT);
        assumeFalse(UserManager.isVisibleBackgroundUsersEnabled());

        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 10, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ 2);

        final int PARENT_ID = 300;
        final int PROFILE1_ID = 301;
        final int PROFILE2_ID = 302;

        UserInfo parent = setUpUser(PARENT_ID, 0);
        UserInfo profile1 = setUpUser(PROFILE1_ID,
                UserInfo.FLAG_PROFILE | UserInfo.FLAG_INITIALIZED,
                false, USER_TYPE_PROFILE_MANAGED);
        UserInfo profile2 = setUpUser(PROFILE2_ID,
                UserInfo.FLAG_PROFILE, // Not FLAG_INITIALIZED to prevent auto-starting
                false, USER_TYPE_PROFILE_MANAGED);

        parent.profileGroupId = profile1.profileGroupId = profile2.profileGroupId = PARENT_ID;
        mUserController.onSystemReady(); // To set the profileGroupIds in UserController.
        when(mInjector.mUserManagerMock.getProfiles(eq(PARENT_ID), anyBoolean()))
                .thenReturn(Arrays.asList(parent, profile1, profile2));

        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID);

        // Start PARENT_ID (and implicitly PROFILE1_ID) in foreground
        addForegroundUserAndContinueUserSwitch(PARENT_ID, SYSTEM_USER_ID, 1, false, false);
        mUserController.finishUserSwitch(mUserStates.get(PARENT_ID)); // Calls startProfiles()
        // PROFILE1_ID is managed and initialized, so it started automatically via startProfiles
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, PARENT_ID, PROFILE1_ID);
        assertAndProcessBackgroundUserJudgementUntilStop(false, PARENT_ID);
        assertAndProcessBackgroundUserJudgementUntilStop(false, PROFILE1_ID);

        // Start PROFILE2_ID (while parent is still in foreground)
        mUserController.startUser(PROFILE2_ID, USER_START_MODE_BACKGROUND);
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, PARENT_ID, PROFILE1_ID, PROFILE2_ID);
        assertAndProcessBackgroundUserJudgementUntilStop(false, PROFILE2_ID);

        // Move PARENT_ID to the background
        setUpUser(TEST_USER_ID3, 0);
        addForegroundUserAndContinueUserSwitch(TEST_USER_ID3, PARENT_ID, 2, false, true);
        mUserController.finishUserSwitch(mUserStates.get(TEST_USER_ID3));
        assertRunningUsersIgnoreOrder(
                SYSTEM_USER_ID, TEST_USER_ID3, PARENT_ID, PROFILE1_ID, PROFILE2_ID);

        // They are now in the background, so are eligible for judgement and stopping.
        assertAndProcessBackgroundUserJudgementUntilStop(true, PARENT_ID);

        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID3);
    }

    /** Test schedule for judging a background user is cleared when the user is stopped. */
    @Test
    public void testScheduleJudgementOfBackgroundUser_cancelWhenStopUser() throws Exception {
        mSetFlagsRule.enableFlags(
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER,
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER_BY_DEFAULT);
        assumeFalse(UserManager.isVisibleBackgroundUsersEnabled());

        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 10, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ 5);

        // TEST_USER_ID will start and stop.
        // TEST_USER_ID1 is irrelevant and just to ensure it isn't affected by TEST_USER_ID's stop.
        setUpAndStartUserInBackground(TEST_USER_ID);
        setUpAndStartUserInBackground(TEST_USER_ID1);
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID, TEST_USER_ID1);

        assertTrue(mInjector.mHandler.hasEqualMessages(
                SCHEDULE_JUDGE_FATE_OF_BACKGROUND_USER_MSG,
                new UserAndLmkThreshold(TEST_USER_ID, -300)));

        // Stop TEST_USER_ID and check that its judgement schedule is cleared.
        mUserController.stopUser(TEST_USER_ID, false, null, null);
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID1);
        assertAndProcessBackgroundUserJudgementUntilStop(false, TEST_USER_ID);

        // We've already verified that the real schedule is clear. So clear out the copy too.
        mInjector.mHandler.removeMessageCopy(mInjector.mHandler.getMessageForCode(
                SCHEDULE_JUDGE_FATE_OF_BACKGROUND_USER_MSG,
                new UserAndLmkThreshold(TEST_USER_ID, -300)));

        // Restart the user and check that its judgement schedule still works as expected.
        mUserController.startUser(TEST_USER_ID, USER_START_MODE_BACKGROUND);

        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID, TEST_USER_ID1);
        assertAndProcessBackgroundUserJudgementUntilStop(true, TEST_USER_ID);
        assertAndProcessBackgroundUserJudgementUntilStop(false, TEST_USER_ID);
        assertAndProcessBackgroundUserJudgementUntilStop(true, TEST_USER_ID1);
    }

    /** Test schedule for judging background user is not set if it only started temporarily. */
    @Test
    public void testScheduleJudgementOfBackgroundUser_skipIfTempStart() throws Exception {
        mSetFlagsRule.enableFlags(
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER,
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER_BY_DEFAULT);
        assumeFalse(UserManager.isVisibleBackgroundUsersEnabled());

        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 10, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ 5);

        setUpUser(TEST_USER_ID, DEFAULT_USER_FLAGS);
        mUserController.startUserInBackgroundTemporarily(TEST_USER_ID, 5);
        mUserStates.put(TEST_USER_ID, mUserController.getStartedUserState(TEST_USER_ID));
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID);

        assertAndProcessBackgroundUserJudgementUntilStop(false, TEST_USER_ID);
        assertAndProcessBackgroundUserJudgementUntilStop(false, UserHandle.USER_NULL);

        // Start the user again (even though it is already running), but now it's no longer
        // temporary. Now judgement is expected.
        mUserController.startUser(TEST_USER_ID, USER_START_MODE_BACKGROUND);
        assertAndProcessBackgroundUserJudgementUntilStop(true, TEST_USER_ID);
    }

    @Test
    public void testUserAndLmkThreshold_equals() {
        UserAndLmkThreshold o1 = new UserAndLmkThreshold(10, 100);
        UserAndLmkThreshold o2 = new UserAndLmkThreshold(10, 200);
        UserAndLmkThreshold o3 = new UserAndLmkThreshold(11, 100);

        assertEquals(o1, o2);
        assertEquals(o1.hashCode(), o2.hashCode());
        assertNotEquals(o1, o3);
    }

    @Test
    public void testJudgeFateOfBackgroundUser_firstJudgementSchedulesFinalJudgement()
            throws Exception {
        mSetFlagsRule.enableFlags(
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER,
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER_BY_DEFAULT);
        assumeFalse(UserManager.isVisibleBackgroundUsersEnabled());

        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 10, /* delayUserDataLocking= */ false,
                /* backgroundUserScheduledStopTimeSecs= */ 2);

        setUpAndStartUserInBackground(TEST_USER_ID);

        Message msg1 = mInjector.mHandler.getMessageForCode(
                SCHEDULE_JUDGE_FATE_OF_BACKGROUND_USER_MSG,
                new UserAndLmkThreshold(TEST_USER_ID, -300));
        assertNotNull(msg1);

        mInjector.mHandler.removeMessageCopy(msg1); // To avoid confusing later getMessageForCode
        when(mInjector.getLmkdKillCount()).thenReturn(100);
        mUserController.processJudgeFateOfBackgroundUser((UserAndLmkThreshold) msg1.obj);

        // Now make sure that the lmk = 100 is in the message. Note that it shouldn't be the -300
        // dummy value which is ignored when evaluating message object equality.
        Message msg2 = mInjector.mHandler.getMessageForCode(
                SCHEDULE_JUDGE_FATE_OF_BACKGROUND_USER_MSG,
                new UserAndLmkThreshold(TEST_USER_ID, -300));
        assertNotNull(msg2);
        assertEquals(100, ((UserAndLmkThreshold) msg2.obj).lmkCountThreshold);
    }

    @Test
    public void testJudgeFateOfBackgroundUser_allTrialPhases() throws Exception {
        mSetFlagsRule.enableFlags(
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER,
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER_BY_DEFAULT);
        assumeFalse(UserManager.isVisibleBackgroundUsersEnabled());

        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 10, /* delayUserDataLocking= */ false,
                /* backgroundUserScheduledStopTimeSecs= */ 2);

        setUpAndStartUserInBackground(TEST_USER_ID);
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID);

        // assertAndProcessBackgroundUserJudgementAndStop does all trial phases.
        assertAndProcessBackgroundUserJudgementUntilStop(true, TEST_USER_ID);
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID);
    }

    @Test
    public void testJudgeFateOfBackgroundUser_lmkNotIncreased() throws Exception {
        mSetFlagsRule.enableFlags(
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER,
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER_BY_DEFAULT);
        assumeFalse(UserManager.isVisibleBackgroundUsersEnabled());

        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 10, /* delayUserDataLocking= */ false,
                /* backgroundUserScheduledStopTimeSecs= */ 2);

        setUpAndStartUserInBackground(TEST_USER_ID);

        // Process initial judgement
        Message msg1 = mInjector.mHandler.getMessageForCode(
                SCHEDULE_JUDGE_FATE_OF_BACKGROUND_USER_MSG,
                new UserAndLmkThreshold(TEST_USER_ID, -300));
        assertNotNull("Expected initial judgement scheduled", msg1);
        mInjector.mHandler.removeMessageCopy(msg1); // To avoid confusing later getMessageForCode
        when(mInjector.getLmkdKillCount()).thenReturn(100);
        mUserController.processJudgeFateOfBackgroundUser((UserAndLmkThreshold) msg1.obj);

        // Process final judgement, but LMK count has not increased
        Message msg2 = mInjector.mHandler.getMessageForCode(
                SCHEDULE_JUDGE_FATE_OF_BACKGROUND_USER_MSG,
                new UserAndLmkThreshold(TEST_USER_ID, -300));
        assertNotNull("Expected second judgement scheduled", msg2);
        assertEquals("Wrong lmk", 100, ((UserAndLmkThreshold) msg2.obj).lmkCountThreshold);
        mInjector.mHandler.removeMessageCopy(msg2); // To avoid confusing later getMessageForCode
        when(mInjector.getLmkdKillCount()).thenReturn(100);
        mUserController.processJudgeFateOfBackgroundUser((UserAndLmkThreshold) msg2.obj);

        // Should reschedule judgement, not stop
        assertFalse("Expected no planned stopping", mInjector.mHandler.hasEqualMessages(
                SCHEDULE_STOP_BACKGROUND_USER_MSG, TEST_USER_ID));
        assertTrue("Expected continued judging", mInjector.mHandler.hasEqualMessages(
                SCHEDULE_JUDGE_FATE_OF_BACKGROUND_USER_MSG,
                new UserAndLmkThreshold(TEST_USER_ID, -300)));
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID);
    }

    @Test
    public void testJudgeFateOfBackgroundUser_cancelledByStopUser() throws Exception {
        mSetFlagsRule.enableFlags(
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER,
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER_BY_DEFAULT);
        assumeFalse(UserManager.isVisibleBackgroundUsersEnabled());

        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 10, /* delayUserDataLocking= */ false,
                /* backgroundUserScheduledStopTimeSecs= */ 2);

        setUpAndStartUserInBackground(TEST_USER_ID);

        assertTrue(mInjector.mHandler.hasEqualMessages(
                SCHEDULE_JUDGE_FATE_OF_BACKGROUND_USER_MSG,
                new UserAndLmkThreshold(TEST_USER_ID, -300)));

        mUserController.stopUser(TEST_USER_ID, false, null, null);

        assertFalse(mInjector.mHandler.hasEqualMessages(
                SCHEDULE_JUDGE_FATE_OF_BACKGROUND_USER_MSG,
                new UserAndLmkThreshold(TEST_USER_ID, -300)));
    }

    @Test
    public void testJudgeFateOfBackgroundUser_cancelledByForegroundStart() throws Exception {
        mSetFlagsRule.enableFlags(
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER,
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER_BY_DEFAULT);
        assumeFalse(UserManager.isVisibleBackgroundUsersEnabled());

        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 10, /* delayUserDataLocking= */ false,
                /* backgroundUserScheduledStopTimeSecs= */ 2);

        setUpAndStartUserInBackground(TEST_USER_ID);

        assertTrue(mInjector.mHandler.hasEqualMessages(
                SCHEDULE_JUDGE_FATE_OF_BACKGROUND_USER_MSG,
                new UserAndLmkThreshold(TEST_USER_ID, -300)));

        mUserController.startUser(TEST_USER_ID, USER_START_MODE_FOREGROUND);

        assertFalse(mInjector.mHandler.hasEqualMessages(
                SCHEDULE_JUDGE_FATE_OF_BACKGROUND_USER_MSG,
                new UserAndLmkThreshold(TEST_USER_ID, -300)));
    }

    /** Test schedule for stopping background user is cleared when the user is stopped. */
    @Test
    public void testScheduleStopOfBackgroundUser_stopUser() throws Exception {
        mSetFlagsRule.enableFlags(
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER,
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER_BY_DEFAULT);
        assumeFalse(UserManager.isVisibleBackgroundUsersEnabled());

        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 10, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ 5);

        // TEST_USER_ID will temporarily start but then later explicitly be stopped.
        setUpUser(TEST_USER_ID, DEFAULT_USER_FLAGS);
        mUserController.startUserInBackgroundTemporarily(TEST_USER_ID, 5);
        mUserStates.put(TEST_USER_ID, mUserController.getStartedUserState(TEST_USER_ID));

        // TEST_USER_ID1 is irrelevant and just to ensure it isn't affected by TEST_USER_ID's stop.
        setUpUser(TEST_USER_ID1, DEFAULT_USER_FLAGS);
        mUserController.startUserInBackgroundTemporarily(TEST_USER_ID1, 5);
        mUserStates.put(TEST_USER_ID, mUserController.getStartedUserState(TEST_USER_ID1));

        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID, TEST_USER_ID1);

        assertTrue(mInjector.mHandler.hasEqualMessages(SCHEDULE_STOP_BACKGROUND_USER_MSG,
                TEST_USER_ID));

        // Stop TEST_USER_ID and check that its stopping schedule is cleared.
        mUserController.stopUser(TEST_USER_ID, false, null, null);
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID1);
        assertAndProcessScheduledStopBackgroundUser(false, TEST_USER_ID);

        // We've already verified that the real schedule is clear. So clear out the copy too.
        mInjector.mHandler.removeMessageCopy(mInjector.mHandler.getMessageForCode(
                SCHEDULE_STOP_BACKGROUND_USER_MSG, TEST_USER_ID));

        // Restart the user and check that its stopping schedule still works as expected.
        mUserController.startUserInBackgroundTemporarily(TEST_USER_ID, 3);

        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID, TEST_USER_ID1);
        assertAndProcessScheduledStopBackgroundUser(true, TEST_USER_ID);
        assertAndProcessScheduledStopBackgroundUser(false, TEST_USER_ID);
        assertAndProcessScheduledStopBackgroundUser(true, TEST_USER_ID1);
    }

    /** Test scheduling stopping of background users via startUserInBackgroundTemporarily. */
    @Test
    public void testScheduleStopOfBackgroundUser_startInBackgroundTemporarily() throws Exception {
        mSetFlagsRule.enableFlags(
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER);
        assumeFalse(UserManager.isVisibleBackgroundUsersEnabled());

        // startUserInBackgroundTemporarily should work regardless.
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 10, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        setUpUser(TEST_USER_ID, DEFAULT_USER_FLAGS);
        mUserController.startUserInBackgroundTemporarily(TEST_USER_ID, 5);
        mUserStates.put(TEST_USER_ID, mUserController.getStartedUserState(TEST_USER_ID));

        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID);

        assertAndProcessScheduledStopBackgroundUser(true, TEST_USER_ID);
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID);

        assertAndProcessScheduledStopBackgroundUser(false, TEST_USER_ID);
    }

    /** Test scheduling stopping of background users that were started multiples times in bg. */
    @Test
    public void testScheduleStopOfBackgroundUser_multipleSchedulesObeyLast() throws Exception {
        mSetFlagsRule.enableFlags(
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER);
        assumeFalse(UserManager.isVisibleBackgroundUsersEnabled());

        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 10, /* delayUserDataLocking= */ false,
                /* backgroundUserScheduledStopTimeSecs= */ -1);

        // Set up and start the user temporarily.
        setUpUser(TEST_USER_ID, DEFAULT_USER_FLAGS);
        mUserController.startUserInBackgroundTemporarily(TEST_USER_ID, 1);
        mUserStates.put(TEST_USER_ID, mUserController.getStartedUserState(TEST_USER_ID));
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID);

        // Then start the user two more times (so a total of three times in succession).
        mUserController.startUserInBackgroundTemporarily(TEST_USER_ID, 3);
        mUserController.startUserInBackgroundTemporarily(TEST_USER_ID, 2);


        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID);

        assertAndProcessScheduledStopBackgroundUser(true, TEST_USER_ID);
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID);

        assertAndProcessScheduledStopBackgroundUser(true, TEST_USER_ID);
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID);

        assertAndProcessScheduledStopBackgroundUser(true, TEST_USER_ID);
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID);

        assertAndProcessScheduledStopBackgroundUser(false, TEST_USER_ID);
    }

    /** Test that startUserInBackgroundTemporarily has no effect if user already running forever. */
    @Test
    public void testScheduleStopOfBackgroundUser_startForeverThenStartTemp() throws Exception {
        mSetFlagsRule.enableFlags(
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER,
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER_BY_DEFAULT);
        assumeFalse(UserManager.isVisibleBackgroundUsersEnabled());

        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 10, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ 2);

        // Start in background -> no scheduled stop (but judgement process initiated)
        setUpAndStartUserInBackground(TEST_USER_ID);
        assertTrue(mInjector.mHandler.hasEqualMessages(
                SCHEDULE_JUDGE_FATE_OF_BACKGROUND_USER_MSG,
                new UserAndLmkThreshold(TEST_USER_ID, -300)));
        assertAndProcessScheduledStopBackgroundUser(false, TEST_USER_ID);

        // Now redundantly start temporarily -> still no scheduled stop (judgement still initiated)
        mUserController.startUserInBackgroundTemporarily(TEST_USER_ID, 1);
        assertTrue(mInjector.mHandler.hasEqualMessages(
                SCHEDULE_JUDGE_FATE_OF_BACKGROUND_USER_MSG,
                new UserAndLmkThreshold(TEST_USER_ID, -300)));
        assertAndProcessScheduledStopBackgroundUser(false, TEST_USER_ID);
    }

    /** Test that startUserInBackgroundTemporarily won't stop if subsequently started forever. */
    @Test
    public void testScheduleStopOfBackgroundUser_startTempThenStartForever() throws Exception {
        mSetFlagsRule.enableFlags(
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER,
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER_BY_DEFAULT);
        assumeFalse(UserManager.isVisibleBackgroundUsersEnabled());

        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 10, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        setUpUser(TEST_USER_ID, DEFAULT_USER_FLAGS);
        mUserController.startUserInBackgroundTemporarily(TEST_USER_ID, 5);
        mUserStates.put(TEST_USER_ID, mUserController.getStartedUserState(TEST_USER_ID));

        assertTrue(mInjector.mHandler.hasEqualMessages(
                SCHEDULE_STOP_BACKGROUND_USER_MSG, TEST_USER_ID));

        // Before we process the schedule, start the user in background "forever".
        mUserController.startUser(TEST_USER_ID, USER_START_MODE_BACKGROUND);

        assertAndProcessScheduledStopBackgroundUser(false, TEST_USER_ID);
    }

    /** Test scheduling stopping of background users - reschedule if current user is a guest. */
    @Test
    public void testScheduleStopOfBackgroundUser_rescheduleWhenGuest() throws Exception {
        mSetFlagsRule.enableFlags(
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER,
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER_BY_DEFAULT);
        assumeFalse(UserManager.isVisibleBackgroundUsersEnabled());

        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 10, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ 2);

        final int TEST_USER_GUEST = 902;
        setUpUser(TEST_USER_GUEST, UserInfo.FLAG_GUEST);

        setUpUser(TEST_USER_ID2, DEFAULT_USER_FLAGS);

        // Switch to TEST_USER_ID from user 0
        int numberOfUserSwitches = 0;
        addForegroundUserAndContinueUserSwitch(TEST_USER_ID, SYSTEM_USER_ID,
                ++numberOfUserSwitches, false,
                /* expectScheduleBackgroundUserStopping= */ false);
        assertRunningUsersInOrder(SYSTEM_USER_ID, TEST_USER_ID);

        // Switch to TEST_USER_GUEST from TEST_USER_ID
        addForegroundUserAndContinueUserSwitch(TEST_USER_GUEST, TEST_USER_ID,
                ++numberOfUserSwitches, false,
                /* expectScheduleBackgroundUserJudgement= */ true);
        assertRunningUsersInOrder(SYSTEM_USER_ID, TEST_USER_ID, TEST_USER_GUEST);

        // Allow post-switch processing to complete.
        // Because it has been running in the background sufficiently long to be judged,
        // TEST_USER_ID will be scheduled for stopping, but it shouldn't actually stop since the
        // current user is a Guest.
        assertAndProcessBackgroundUserJudgementUntilStop(true, TEST_USER_ID);
        // But because current user is a guest, it should have been rescheduled, not stopped.
        assertRunningUsersInOrder(SYSTEM_USER_ID, TEST_USER_ID, TEST_USER_GUEST);
        assertTrue(mInjector.mHandler.hasEqualMessages(SCHEDULE_STOP_BACKGROUND_USER_MSG,
                TEST_USER_ID));

        // Switch to TEST_USER_ID2 from TEST_USER_GUEST
        // Guests are automatically stopped in the background, so it won't be scheduled.
        addForegroundUserAndContinueUserSwitch(TEST_USER_ID2, TEST_USER_GUEST,
                ++numberOfUserSwitches, true,
                /* expectScheduleBackgroundUserJudgement= */ false);
        assertRunningUsersInOrder(SYSTEM_USER_ID, TEST_USER_ID, TEST_USER_ID2);

        // Allow the post-switch processing to complete.
        // TEST_USER_ID should *still* be scheduled for stopping, since we skipped stopping it
        // earlier.
        assertAndProcessScheduledStopBackgroundUser(true, TEST_USER_ID);
        assertAndProcessScheduledStopBackgroundUser(false, TEST_USER_GUEST);
        assertAndProcessScheduledStopBackgroundUser(false, TEST_USER_ID2);
        assertRunningUsersInOrder(SYSTEM_USER_ID, TEST_USER_ID2);
    }

    /** Test scheduling stopping of background users - reschedule if user with a scheduled alarm. */
    @Test
    public void testScheduleStopOfBackgroundUser_rescheduleIfAlarm() throws Exception {
        mSetFlagsRule.enableFlags(
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER,
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER_BY_DEFAULT);
        assumeFalse(UserManager.isVisibleBackgroundUsersEnabled());

        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 10, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ 2);

        setUpAndStartUserInBackground(TEST_USER_ID);
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID);

        // Initially, the background user has an alarm that will fire soon. So don't stop the user.
        when(mInjector.mAlarmManagerInternal.getNextAlarmTriggerTimeForUser(eq(TEST_USER_ID)))
                .thenReturn(System.currentTimeMillis() + Duration.ofMinutes(2).toMillis());

        assertAndProcessBackgroundUserJudgementUntilStop(true, TEST_USER_ID);
        // User should not be stopped, but rescheduled.
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID);
        assertTrue(mInjector.mHandler.hasEqualMessages(SCHEDULE_STOP_BACKGROUND_USER_MSG,
                TEST_USER_ID));

        // Now, that alarm is gone and the next alarm isn't for a long time. Do stop the user.
        when(mInjector.mAlarmManagerInternal.getNextAlarmTriggerTimeForUser(eq(TEST_USER_ID)))
                .thenReturn(System.currentTimeMillis() + Duration.ofDays(1).toMillis());

        assertAndProcessScheduledStopBackgroundUser(true, TEST_USER_ID);
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID);

        // No-one is scheduled to stop anymore.
        assertAndProcessScheduledStopBackgroundUser(false, null);
        assertAndProcessBackgroundUserJudgementUntilStop(false, UserHandle.USER_NULL);
        verify(mInjector.mAlarmManagerInternal, never())
                .getNextAlarmTriggerTimeForUser(eq(SYSTEM_USER_ID));
    }

    /** Test scheduling stopping of background users - reschedule if user is sounding audio. */
    @Test
    public void testScheduleStopOfBackgroundUser_rescheduleIfAudio() throws Exception {
        mSetFlagsRule.enableFlags(
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER,
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER_BY_DEFAULT);
        assumeFalse(UserManager.isVisibleBackgroundUsersEnabled());

        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 10, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ 2);

        setUpAndStartUserInBackground(TEST_USER_ID);
        setUpAndStartUserInBackground(TEST_USER_ID1);
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID, TEST_USER_ID1);

        when(mInjector.mAudioManagerInternal
                .isUserPlayingAudio(eq(TEST_USER_ID))).thenReturn(true);
        when(mInjector.mAudioManagerInternal
                .isUserPlayingAudio(eq(TEST_USER_ID1))).thenReturn(false);

        assertAndProcessBackgroundUserJudgementUntilStop(true, TEST_USER_ID);
        assertAndProcessBackgroundUserJudgementUntilStop(true, TEST_USER_ID1);

        // TEST_USER_ID1 should be stopped. But TEST_USER_ID shouldn't, since it was playing audio.
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID);
        assertTrue(mInjector.mHandler.hasEqualMessages(SCHEDULE_STOP_BACKGROUND_USER_MSG,
                TEST_USER_ID));
    }

    /** Test scheduling stopping of background users - reschedule if user has visible activity. */
    @Test
    public void testScheduleStopOfBackgroundUser_rescheduleIfVisibleActivity() throws Exception {
        mSetFlagsRule.enableFlags(
                android.multiuser.Flags.FLAG_RESCHEDULE_STOP_IF_VISIBLE_ACTIVITIES,
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER,
                android.multiuser.Flags.FLAG_SCHEDULE_STOP_OF_BACKGROUND_USER_BY_DEFAULT);
        assumeFalse(UserManager.isVisibleBackgroundUsersEnabled());

        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 10, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ 2);

        setUpAndStartUserInBackground(TEST_USER_ID);
        setUpAndStartUserInBackground(TEST_USER_ID1);
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID, TEST_USER_ID1);

        doReturn(new ArraySet(List.of(TEST_USER_ID))).when(mInjector).getVisibleActivityUsers();

        assertAndProcessBackgroundUserJudgementUntilStop(true, TEST_USER_ID);
        assertAndProcessBackgroundUserJudgementUntilStop(true, TEST_USER_ID1);

        // TEST_USER_ID1 should be stopped. But TEST_USER_ID shouldn't as it has a visible activity.
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, TEST_USER_ID);
    }

    /**
     * Process queued SCHEDULED_STOP_BACKGROUND_USER_MSG message, if expected.
     * @param userId the user we are checking to see whether it is scheduled.
     *               Can be null, when expectScheduled is false, to indicate no user should be
     *               scheduled.
     */
    private void assertAndProcessScheduledStopBackgroundUser(
            boolean expectScheduled, @Nullable Integer userId) {
        TestHandler handler = mInjector.mHandler;
        if (expectScheduled) {
            assertTrue(handler.hasEqualMessages(SCHEDULE_STOP_BACKGROUND_USER_MSG, userId));
            handler.removeSoonestMessage(SCHEDULE_STOP_BACKGROUND_USER_MSG, userId);
            mUserController.processScheduledStopOfBackgroundUser(userId);
        } else {
            assertFalse(handler.hasEqualMessages(SCHEDULE_STOP_BACKGROUND_USER_MSG, userId));
        }
    }

    /**
     * Process queued SCHEDULE_JUDGE_FATE_OF_BACKGROUND_USER_MSG message, if expected.
     * Simulates the full automatic background user stopping flow (up to and including trying to
     * stop the user) and asserts its correctness.
     * @param expectJudgement whether to expect the user to be scheduled for judgement.
     * @param userId the user we are checking to see whether it is scheduled.
     *               Can be NULL, when expectJudgement is false, to indicate no user should
     *               be scheduled.
     */
    private void assertAndProcessBackgroundUserJudgementUntilStop(
            boolean expectJudgement, @UserIdInt @CanBeNULL int userId) {
        TestHandler handler = mInjector.mHandler;

        if (!expectJudgement) {
            assertFalse("Expected no judgement for user " + userId,
                    handler.hasEqualMessages(SCHEDULE_JUDGE_FATE_OF_BACKGROUND_USER_MSG,
                    userId == UserHandle.USER_NULL ? null : new UserAndLmkThreshold(userId, -300)));
            return;
        }

        assertTrue("Expected judgement scheduled for user " + userId,
                handler.hasEqualMessages(SCHEDULE_JUDGE_FATE_OF_BACKGROUND_USER_MSG,
                        new UserAndLmkThreshold(userId, -300)));

        // Step 1: initial judgement
        Message msg1 = handler.getMessageForCode(
                SCHEDULE_JUDGE_FATE_OF_BACKGROUND_USER_MSG,
                new UserAndLmkThreshold(userId, -300));
        assertNotNull("No initial judgement message for user " + userId, msg1);
        handler.removeMessageCopy(msg1); // Remove to avoid confusing subsequent getMessageForCode
        UserAndLmkThreshold payload1 = (UserAndLmkThreshold) msg1.obj;
        assertEquals(userId, payload1.userId);
        assertEquals("Expected initial lmk threshold to be -1", -1, payload1.lmkCountThreshold);

        when(mInjector.getLmkdKillCount()).thenReturn(100);
        mUserController.processJudgeFateOfBackgroundUser(payload1);

        // Step 2: final judgement
        Message msg2 = handler.getMessageForCode(
                SCHEDULE_JUDGE_FATE_OF_BACKGROUND_USER_MSG,
                new UserAndLmkThreshold(userId, -300));
        assertNotNull("No final judgement message for user " + userId, msg2);
        handler.removeMessageCopy(msg2); // Remove to avoid confusing subsequent getMessageForCode
        UserAndLmkThreshold payload2 = (UserAndLmkThreshold) msg2.obj;
        assertEquals(userId, payload2.userId);
        assertEquals("Expected stored lmk threshold", 100, payload2.lmkCountThreshold);

        when(mInjector.getLmkdKillCount()).thenReturn(101); // lmk count increased
        mUserController.processJudgeFateOfBackgroundUser(payload2);

        // Step 3: scheduled stop (SCHEDULE_STOP_BACKGROUND_USER_MSG)
        assertAndProcessScheduledStopBackgroundUser(true, userId);
    }

    @Test
    public void testExplicitSystemUserStartInBackground() {
        setUpUser(UserHandle.USER_SYSTEM, 0);
        assertFalse(mUserController.isSystemUserStarted());
        assertTrue(mUserController.startUser(UserHandle.USER_SYSTEM, USER_START_MODE_BACKGROUND,
                null));
        assertTrue(mUserController.isSystemUserStarted());
    }

    /**
     * Test stopping of user from max running users limit.
     */
    @Test
    public void testUserLockingFromUserSwitchingForMultipleUsersNonDelayedLocking()
            throws InterruptedException, RemoteException {
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 3, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        setUpUser(TEST_USER_ID1, 0);
        setUpUser(TEST_USER_ID2, 0);
        int numberOfUserSwitches = 1;
        addForegroundUserAndContinueUserSwitch(TEST_USER_ID, SYSTEM_USER_ID,
                numberOfUserSwitches, false, false);
        // running: user 0, USER_ID
        assertTrue(mUserController.canStartMoreUsers());
        assertRunningUsersInOrder(SYSTEM_USER_ID, TEST_USER_ID);

        numberOfUserSwitches++;
        addForegroundUserAndContinueUserSwitch(TEST_USER_ID1, TEST_USER_ID,
                numberOfUserSwitches, false, false);
        // running: user 0, USER_ID, USER_ID1
        assertFalse(mUserController.canStartMoreUsers());
        assertRunningUsersInOrder(SYSTEM_USER_ID, TEST_USER_ID, TEST_USER_ID1);

        numberOfUserSwitches++;
        addForegroundUserAndContinueUserSwitch(TEST_USER_ID2, TEST_USER_ID1,
                numberOfUserSwitches, false, false);
        UserState ussUser2 = mUserStates.get(TEST_USER_ID2);
        // skip middle step and call this directly.
        mUserController.finishUserSwitch(ussUser2);
        waitForHandlerToComplete(mInjector.mHandler, HANDLER_WAIT_TIME_MS);
        // running: user 0, USER_ID1, USER_ID2
        // USER_ID should be stopped as it is least recently used non user0.
        assertFalse(mUserController.canStartMoreUsers());
        assertRunningUsersInOrder(SYSTEM_USER_ID, TEST_USER_ID1, TEST_USER_ID2);
        verifySystemUserVisibilityChangesNeverNotified();
    }

    /**
     * This test tests delayed locking mode using 4 users. As core logic of delayed locking is
     * happening in finishUserStopped call, the test also calls finishUserStopped while skipping
     * all middle steps which takes too much work to mock.
     */
    @Test
    public void testUserLockingFromUserSwitchingForMultipleUsersDelayedLockingMode()
            throws Exception {
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 3, /* delayUserDataLocking= */ true,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        setUpUser(TEST_USER_ID1, 0);
        setUpUser(TEST_USER_ID2, 0);
        int numberOfUserSwitches = 1;
        addForegroundUserAndContinueUserSwitch(TEST_USER_ID, SYSTEM_USER_ID,
                numberOfUserSwitches, false, false);
        // running: user 0, USER_ID
        assertTrue(mUserController.canStartMoreUsers());
        assertRunningUsersInOrder(SYSTEM_USER_ID, TEST_USER_ID);
        numberOfUserSwitches++;

        addForegroundUserAndContinueUserSwitch(TEST_USER_ID1, TEST_USER_ID,
                numberOfUserSwitches, true, false);
        // running: user 0, USER_ID1
        // stopped + unlocked: USER_ID
        numberOfUserSwitches++;
        assertTrue(mUserController.canStartMoreUsers());
        assertRunningUsersInOrder(SYSTEM_USER_ID, TEST_USER_ID1);
        // Skip all other steps and test unlock delaying only
        UserState uss = mUserStates.get(TEST_USER_ID);
        uss.setState(UserState.STATE_SHUTDOWN); // necessary state change from skipped part
        mUserController.finishUserStopped(uss, /* allowDelayedLocking= */ true);
        // Cannot mock FgThread handler, so confirm that there is no posted message left before
        // checking.
        waitForHandlerToComplete(FgThread.getHandler(), HANDLER_WAIT_TIME_MS);
        verify(mInjector.mLockSettingsInternalMock, times(0))
                .lockUser(anyInt());

        addForegroundUserAndContinueUserSwitch(TEST_USER_ID2, TEST_USER_ID1,
                numberOfUserSwitches, true, false);
        // running: user 0, USER_ID2
        // stopped + unlocked: USER_ID1
        // stopped + locked: USER_ID
        assertTrue(mUserController.canStartMoreUsers());
        assertRunningUsersInOrder(SYSTEM_USER_ID, TEST_USER_ID2);
        UserState ussUser1 = mUserStates.get(TEST_USER_ID1);
        ussUser1.setState(UserState.STATE_SHUTDOWN);
        mUserController.finishUserStopped(ussUser1, /* allowDelayedLocking= */ true);
        waitForHandlerToComplete(FgThread.getHandler(), HANDLER_WAIT_TIME_MS);
        verify(mInjector.mLockSettingsInternalMock, times(1))
                .lockUser(TEST_USER_ID);
    }

    /** Tests that we stop excess users when starting a background user. */
    @Test
    public void testStoppingExcessRunningUsers_onBackgroundStart() throws Exception {
        mSetFlagsRule.enableFlags(android.multiuser.Flags.FLAG_STOP_EXCESS_FOR_BACKGROUND_STARTS);
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 2, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        setUpUser(TEST_USER_ID1, 0);
        setUpUser(TEST_USER_ID2, 0);

        assertRunningUsersInOrder(SYSTEM_USER_ID);

        mUserController.startUser(TEST_USER_ID1, USER_START_MODE_BACKGROUND);
        assertRunningUsersInOrder(TEST_USER_ID1, SYSTEM_USER_ID);

        // Start a user in the background. This exceeds max. Make sure we cut down to 2 users.
        mUserController.startUser(TEST_USER_ID2, USER_START_MODE_BACKGROUND);
        assertRunningUsersInOrder(TEST_USER_ID2, SYSTEM_USER_ID);
    }

    /** Tests that we stop excess users when starting a profile. */
    @Test
    public void testStoppingExcessRunningUsers_onProfileStart() throws Exception {
        mSetFlagsRule.enableFlags(android.multiuser.Flags.FLAG_STOP_EXCESS_FOR_BACKGROUND_STARTS);
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 2, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        setUpUser(TEST_USER_ID1, 0);
        setUpUser(TEST_USER_ID2, UserInfo.FLAG_PROFILE);

        assertRunningUsersInOrder(SYSTEM_USER_ID);

        mUserController.startUser(TEST_USER_ID1, USER_START_MODE_BACKGROUND);
        assertRunningUsersInOrder(TEST_USER_ID1, SYSTEM_USER_ID);

        // Start a profile. This exceeds max. Make sure we cut down to 2 users.
        assertThat(mUserController.startProfile(TEST_USER_ID2, true, null)).isTrue();
        assertRunningUsersInOrder(TEST_USER_ID2, SYSTEM_USER_ID);
    }

    /** Tests that we stop excess users when starting a foreground user. */
    @Test
    public void testStoppingExcessRunningUsers_onForegroundStart() throws Exception {
        mSetFlagsRule.enableFlags(android.multiuser.Flags.FLAG_STOP_EXCESS_FOR_BACKGROUND_STARTS);
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 2, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        setUpUser(TEST_USER_ID1, 0);
        setUpUser(TEST_USER_ID2, 0);

        assertRunningUsersInOrder(SYSTEM_USER_ID);

        mUserController.startUser(TEST_USER_ID1, USER_START_MODE_BACKGROUND);
        assertRunningUsersInOrder(TEST_USER_ID1, SYSTEM_USER_ID);

        // Start a user in the foreground. This exceeds max. Make sure we cut down to 2 users.
        addForegroundUserAndContinueUserSwitch(TEST_USER_ID2, SYSTEM_USER_ID,
                1, false, false);
        mUserController.finishUserSwitch(mUserStates.get(TEST_USER_ID2));
        waitForHandlerToComplete(mInjector.mHandler, HANDLER_WAIT_TIME_MS);
        assertRunningUsersInOrder(SYSTEM_USER_ID, TEST_USER_ID2);
    }

    /**
     * Tests that (per current policy) we don't stop excess users when temporarily starting a
     * background user.
     */
    @Test
    public void testStoppingExcessRunningUsers_notOnTempBackgroundStart() throws Exception {
        mSetFlagsRule.enableFlags(android.multiuser.Flags.FLAG_STOP_EXCESS_FOR_BACKGROUND_STARTS);
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 2, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        setUpUser(TEST_USER_ID1, 0);
        setUpUser(TEST_USER_ID2, 0);

        assertRunningUsersInOrder(SYSTEM_USER_ID);

        mUserController.startUser(TEST_USER_ID1, USER_START_MODE_BACKGROUND);
        assertRunningUsersInOrder(TEST_USER_ID1, SYSTEM_USER_ID);

        // Start a user temporarily in the background. This exceeds max. But our current policy is
        // to allow exceeding max since it's only temporary.
        mUserController.startUserInBackgroundTemporarily(TEST_USER_ID2, 50);
        assertRunningUsersInOrder(TEST_USER_ID1, TEST_USER_ID2, SYSTEM_USER_ID);
    }

    /** Tests that starting a background user won't just automatically stop that same user if it
     * wound up exceeding maxRunningUsers.
     */
    @Test
    public void testStoppingExcessRunningUsers_doNotStopTheUserBeingStarted() throws Exception {
        mSetFlagsRule.enableFlags(android.multiuser.Flags.FLAG_STOP_EXCESS_FOR_BACKGROUND_STARTS);
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 2, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        setUpUser(TEST_USER_ID1, 0); // Foreground user
        setUpUser(TEST_USER_ID2, 0); // First background user
        setUpUser(TEST_USER_ID3, 0); // Second background user

        assertRunningUsersInOrder(SYSTEM_USER_ID);

        addForegroundUserAndContinueUserSwitch(TEST_USER_ID1, SYSTEM_USER_ID,
                1, false, false);
        mUserController.finishUserSwitch(mUserStates.get(TEST_USER_ID1));
        waitForHandlerToComplete(mInjector.mHandler, HANDLER_WAIT_TIME_MS);
        assertRunningUsersInOrder(SYSTEM_USER_ID, TEST_USER_ID1);

        // Start TEST_USER_ID2 in background. Make sure we don't stop the user we just explicitly
        // started, even though it leaves us exceeding max.
        mUserController.startUser(TEST_USER_ID2, USER_START_MODE_BACKGROUND);
        assertRunningUsersInOrder(SYSTEM_USER_ID, TEST_USER_ID2, TEST_USER_ID1);

        // Start TEST_USER_ID3 in background. Make sure we don't stop the user we just explicitly
        // started, even though it leaves us exceeding max. But still stop the old TEST_USER_ID2.
        mUserController.startUser(TEST_USER_ID3, USER_START_MODE_BACKGROUND);
        assertRunningUsersInOrder(SYSTEM_USER_ID, TEST_USER_ID3, TEST_USER_ID1);
    }

    /**
     * Tests that manually starting the current user's profiles can exceed maxRunningUsers,
     * even though unrelated background users will be stopped.
     */
    @Test
    public void testStoppingExcessRunningUsers_currentProfilesCanExceed_manual() throws Exception {
        mSetFlagsRule.enableFlags(android.multiuser.Flags.FLAG_STOP_EXCESS_FOR_BACKGROUND_STARTS);
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 3, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        final int PARENT_ID = 300;
        final int PROFILE1_ID = 301;
        final int PROFILE2_ID = 302;
        final int BG_USER_ID = 400;

        // These profiles are not specifically set up to start with parent.
        UserInfo parent = setUpUser(PARENT_ID, 0);
        UserInfo profile1 = setUpUser(PROFILE1_ID,
                UserInfo.FLAG_PROFILE,
                false, null);
        UserInfo profile2 = setUpUser(PROFILE2_ID,
                UserInfo.FLAG_PROFILE,
                false, null);
        UserInfo bgUser = setUpUser(BG_USER_ID, 0);

        parent.profileGroupId = profile1.profileGroupId = profile2.profileGroupId = PARENT_ID;
        bgUser.profileGroupId = UserInfo.NO_PROFILE_GROUP_ID;
        mUserController.onSystemReady(); // To set the profileGroupIds in UserController.
        when(mInjector.mUserManagerMock.getProfiles(eq(PARENT_ID), anyBoolean()))
                .thenReturn(Arrays.asList(parent, profile1, profile2));

        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID);

        // Start BG_USER_ID
        mUserController.startUser(BG_USER_ID, USER_START_MODE_BACKGROUND);
        assertRunningUsersIgnoreOrder(BG_USER_ID, SYSTEM_USER_ID);

        // Start PARENT_ID
        addForegroundUserAndContinueUserSwitch(PARENT_ID, SYSTEM_USER_ID, 1, false, false);
        // We call startProfiles(), but the profiles were configured to not auto-start.
        mUserController.finishUserSwitch(mUserStates.get(PARENT_ID)); // Calls startProfiles()
        waitForHandlerToComplete(mInjector.mHandler, HANDLER_WAIT_TIME_MS);
        assertRunningUsersIgnoreOrder(BG_USER_ID, SYSTEM_USER_ID, PARENT_ID);

        // Start PROFILE1_ID. This exceeds max, so BG_USER_ID should be stopped.
        mUserController.startProfile(PROFILE1_ID, true, null);
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, PROFILE1_ID, PARENT_ID);

        // Start PROFILE2_ID. This exceeds max, but they are current user profiles, so no stopping.
        mUserController.startProfile(PROFILE2_ID, true, null);
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, PROFILE1_ID, PROFILE2_ID, PARENT_ID);
    }

    /**
     * Tests that {@link UserController#startProfiles()} (called upon switching users) can exceed
     * maxRunningUsers, even though unrelated background users will be stopped.
     */
    @Test
    public void testStoppingExcessRunningUsers_currentProfilesCanExceed_auto() throws Exception {
        mSetFlagsRule.enableFlags(android.multiuser.Flags.FLAG_STOP_EXCESS_FOR_BACKGROUND_STARTS);
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 3, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        final int PARENT_ID = 300;
        final int PROFILE1_ID = 301;
        final int PROFILE2_ID = 302;
        final int BG_USER_ID = 400;

        // These profiles are managed and initialized, so they start automatically via startProfiles
        UserInfo parent = setUpUser(PARENT_ID, 0);
        UserInfo profile1 = setUpUser(PROFILE1_ID,
                UserInfo.FLAG_PROFILE | UserInfo.FLAG_INITIALIZED,
                false, USER_TYPE_PROFILE_MANAGED);
        UserInfo profile2 = setUpUser(PROFILE2_ID,
                UserInfo.FLAG_PROFILE | UserInfo.FLAG_INITIALIZED,
                false, USER_TYPE_PROFILE_MANAGED);
        UserInfo bgUser = setUpUser(BG_USER_ID, 0);

        parent.profileGroupId = profile1.profileGroupId = profile2.profileGroupId = PARENT_ID;
        bgUser.profileGroupId = UserInfo.NO_PROFILE_GROUP_ID;
        mUserController.onSystemReady(); // To set the profileGroupIds in UserController.
        when(mInjector.mUserManagerMock.getProfiles(eq(PARENT_ID), anyBoolean()))
                .thenReturn(Arrays.asList(parent, profile1, profile2));

        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID);

        // Start BG_USER_ID
        mUserController.startUser(BG_USER_ID, USER_START_MODE_BACKGROUND);
        assertRunningUsersIgnoreOrder(BG_USER_ID, SYSTEM_USER_ID);

        // Start PARENT_ID
        addForegroundUserAndContinueUserSwitch(PARENT_ID, SYSTEM_USER_ID, 1, false, false);
        // We call startProfiles(), which auto-starts the profiles.
        mUserController.finishUserSwitch(mUserStates.get(PARENT_ID)); // Calls startProfiles()
        waitForHandlerToComplete(mInjector.mHandler, HANDLER_WAIT_TIME_MS);

        // Exceeding max should stop BG_USER_ID, but not prevent the profiles from starting.
        assertRunningUsersIgnoreOrder(SYSTEM_USER_ID, PROFILE1_ID, PROFILE2_ID, PARENT_ID);
    }

    /**
     * Test that, when exceeding the maximum number of running users, a profile of the current user
     * is not stopped.
     */
    @Test
    public void testStoppingExcessRunningUsersAfterSwitch_currentProfileNotStopped()
            throws Exception {
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 5, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        final int PARENT_ID = 200;
        final int PROFILE1_ID = 201;
        final int PROFILE2_ID = 202;
        final int FG_USER_ID = 300;
        final int BG_USER_ID = 400;

        // The profiles here do not automatically start when their parent starts since they're not
        // set to shouldStartWithParent (and also because they're not initialized).
        setUpUser(PARENT_ID, 0).profileGroupId = PARENT_ID;
        setUpUser(PROFILE1_ID, UserInfo.FLAG_PROFILE).profileGroupId = PARENT_ID;
        setUpUser(PROFILE2_ID, UserInfo.FLAG_PROFILE).profileGroupId = PARENT_ID;
        setUpUser(FG_USER_ID, 0).profileGroupId = FG_USER_ID;
        setUpUser(BG_USER_ID, 0).profileGroupId = UserInfo.NO_PROFILE_GROUP_ID;
        mUserController.onSystemReady(); // To set the profileGroupIds in UserController.

        assertRunningUsersInOrder(SYSTEM_USER_ID);

        int numberOfUserSwitches = 1;
        addForegroundUserAndContinueUserSwitch(PARENT_ID, SYSTEM_USER_ID,
                numberOfUserSwitches, false, false);
        mUserController.finishUserSwitch(mUserStates.get(PARENT_ID));
        waitForHandlerToComplete(mInjector.mHandler, HANDLER_WAIT_TIME_MS);
        assertTrue(mUserController.canStartMoreUsers());
        assertRunningUsersInOrder(SYSTEM_USER_ID, PARENT_ID);

        assertThat(mUserController.startProfile(PROFILE1_ID, true, null)).isTrue();
        assertRunningUsersInOrder(SYSTEM_USER_ID, PROFILE1_ID, PARENT_ID);

        numberOfUserSwitches++;
        addForegroundUserAndContinueUserSwitch(FG_USER_ID, PARENT_ID,
                numberOfUserSwitches, false, false);
        mUserController.finishUserSwitch(mUserStates.get(FG_USER_ID));
        waitForHandlerToComplete(mInjector.mHandler, HANDLER_WAIT_TIME_MS);
        assertTrue(mUserController.canStartMoreUsers());
        assertRunningUsersInOrder(SYSTEM_USER_ID, PROFILE1_ID, PARENT_ID, FG_USER_ID);

        mUserController.startUser(BG_USER_ID, USER_START_MODE_BACKGROUND);
        assertRunningUsersInOrder(
                SYSTEM_USER_ID, PROFILE1_ID, PARENT_ID, BG_USER_ID, FG_USER_ID);

        // Now we exceed the maxRunningUsers parameter (of 5) but in a way that purposefully won't
        // trigger stopExcessRunningUsers. We'll use a temporary background stop for this purpose.
        // If that policy changes, choose some other way of starting without stopExcessRunningUsers.
        mUserController.startUserInBackgroundTemporarily(PROFILE2_ID, 2);
        assertRunningUsersInOrder(
                SYSTEM_USER_ID, PROFILE1_ID, BG_USER_ID, PROFILE2_ID, PARENT_ID, FG_USER_ID);

        numberOfUserSwitches++;
        addForegroundUserAndContinueUserSwitch(PARENT_ID, FG_USER_ID,
                numberOfUserSwitches, false, false);
        mUserController.finishUserSwitch(mUserStates.get(PARENT_ID));
        waitForHandlerToComplete(mInjector.mHandler, HANDLER_WAIT_TIME_MS);
        // We've now done a user switch and should notice that we've exceeded the maximum number of
        // users. The oldest background user should be stopped (BG_USER); even though PROFILE1 was
        // older, it should not be stopped since it's a profile of the (new) current user.
        assertFalse(mUserController.canStartMoreUsers());
        assertRunningUsersInOrder(SYSTEM_USER_ID, PROFILE1_ID, PROFILE2_ID, FG_USER_ID, PARENT_ID);
    }

    @Test
    public void testEarlyPackageKillEnabledForUserSwitch_enabled() {
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 4, /* delayUserDataLocking= */ true,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        assertTrue(mUserController
                .isEarlyPackageKillEnabledForUserSwitch(TEST_USER_ID, TEST_USER_ID1));
    }

    @Test
    public void testEarlyPackageKillEnabledForUserSwitch_withoutDelayUserDataLocking() {
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 4, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        assertFalse(mUserController
                .isEarlyPackageKillEnabledForUserSwitch(TEST_USER_ID, TEST_USER_ID1));
    }

    @Test
    public void testEarlyPackageKillEnabledForUserSwitch_withPrevSystemUser() {
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 4, /* delayUserDataLocking= */ true,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        assertFalse(mUserController
                .isEarlyPackageKillEnabledForUserSwitch(SYSTEM_USER_ID, TEST_USER_ID1));
    }

    @Test
    public void testEarlyPackageKillEnabledForUserSwitch_stopUserOnSwitchModeOn() {
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 4, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        mUserController.setStopUserOnSwitch(STOP_USER_ON_SWITCH_TRUE);

        assertTrue(mUserController
                .isEarlyPackageKillEnabledForUserSwitch(TEST_USER_ID, TEST_USER_ID1));
    }

    @Test
    public void testEarlyPackageKillEnabledForUserSwitch_stopUserOnSwitchModeOff() {
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 4, /* delayUserDataLocking= */ true,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        mUserController.setStopUserOnSwitch(STOP_USER_ON_SWITCH_FALSE);

        assertFalse(mUserController
                .isEarlyPackageKillEnabledForUserSwitch(TEST_USER_ID, TEST_USER_ID1));
    }


    /**
     * Test that, in getRunningUsersLU, parents come after their profile, even if the profile was
     * started afterwards.
     */
    @Test
    public void testRunningUsersListOrder_parentAfterProfile() {
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 7, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        final int PARENT_ID = 200;
        final int PROFILE1_ID = 201;
        final int PROFILE2_ID = 202;
        final int FG_USER_ID = 300;
        final int BG_USER_ID = 400;

        setUpUser(PARENT_ID, 0).profileGroupId = PARENT_ID;
        setUpUser(PROFILE1_ID, UserInfo.FLAG_PROFILE).profileGroupId = PARENT_ID;
        setUpUser(PROFILE2_ID, UserInfo.FLAG_PROFILE).profileGroupId = PARENT_ID;
        setUpUser(FG_USER_ID, 0).profileGroupId = FG_USER_ID;
        setUpUser(BG_USER_ID, 0).profileGroupId = UserInfo.NO_PROFILE_GROUP_ID;
        mUserController.onSystemReady(); // To set the profileGroupIds in UserController.

        assertRunningUsersInOrder(SYSTEM_USER_ID);

        int numberOfUserSwitches = 1;
        addForegroundUserAndContinueUserSwitch(PARENT_ID, SYSTEM_USER_ID,
                numberOfUserSwitches, false, false);
        assertRunningUsersInOrder(SYSTEM_USER_ID, PARENT_ID);

        assertThat(mUserController.startProfile(PROFILE1_ID, true, null)).isTrue();
        assertRunningUsersInOrder(SYSTEM_USER_ID, PROFILE1_ID, PARENT_ID);

        numberOfUserSwitches++;
        addForegroundUserAndContinueUserSwitch(FG_USER_ID, PARENT_ID,
                numberOfUserSwitches, false, false);
        assertRunningUsersInOrder(SYSTEM_USER_ID, PROFILE1_ID, PARENT_ID, FG_USER_ID);

        mUserController.startUser(BG_USER_ID, USER_START_MODE_BACKGROUND);
        assertRunningUsersInOrder(SYSTEM_USER_ID, PROFILE1_ID, PARENT_ID, BG_USER_ID, FG_USER_ID);

        assertThat(mUserController.startProfile(PROFILE2_ID, true, null)).isTrue();
        // Note for the future:
        // It is not absolutely essential that PROFILE1 come before PROFILE2,
        // nor that PROFILE1 come before BG_USER. We can change that policy later if we'd like.
        // The important thing is that PROFILE1 and PROFILE2 precede PARENT,
        // and that everything precedes OTHER.
        assertRunningUsersInOrder(
                SYSTEM_USER_ID, PROFILE1_ID, BG_USER_ID, PROFILE2_ID, PARENT_ID, FG_USER_ID);
    }

    /**
     * Test that, in getRunningUsersLU, the current user is always at the end, even if background
     * users were started subsequently.
     */
    @Test
    public void testRunningUsersListOrder_currentAtEnd() {
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 7, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        final int CURRENT_ID = 200;
        final int PROFILE_ID = 201;
        final int BG_USER_ID = 400;

        setUpUser(CURRENT_ID, 0).profileGroupId = CURRENT_ID;
        setUpUser(PROFILE_ID, UserInfo.FLAG_PROFILE).profileGroupId = CURRENT_ID;
        setUpUser(BG_USER_ID, 0).profileGroupId = BG_USER_ID;
        mUserController.onSystemReady(); // To set the profileGroupIds in UserController.

        assertRunningUsersInOrder(SYSTEM_USER_ID);

        addForegroundUserAndContinueUserSwitch(CURRENT_ID, SYSTEM_USER_ID, 1, false, false);
        assertRunningUsersInOrder(SYSTEM_USER_ID, CURRENT_ID);

        mUserController.startUser(BG_USER_ID, USER_START_MODE_BACKGROUND);
        assertRunningUsersInOrder(SYSTEM_USER_ID, BG_USER_ID, CURRENT_ID);

        assertThat(mUserController.startProfile(PROFILE_ID, true, null)).isTrue();
        assertRunningUsersInOrder(SYSTEM_USER_ID, BG_USER_ID, PROFILE_ID, CURRENT_ID);
    }

    /**
     * Test locking user with mDelayUserDataLocking false.
     */
    @Test
    public void testUserLockingWithStopUserForNonDelayedLockingMode() throws Exception {
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 3, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        setUpAndStartUserInBackground(TEST_USER_ID);
        assertUserLockedOrUnlockedAfterStopping(TEST_USER_ID, /* allowDelayedLocking= */ true,
                /* keyEvictedCallback= */ null, /* expectLocking= */ true);

        setUpAndStartUserInBackground(TEST_USER_ID1);
        assertUserLockedOrUnlockedAfterStopping(TEST_USER_ID1, /* allowDelayedLocking= */ true,
                /* keyEvictedCallback= */ mKeyEvictedCallback, /* expectLocking= */ true);

        setUpAndStartUserInBackground(TEST_USER_ID2);
        assertUserLockedOrUnlockedAfterStopping(TEST_USER_ID2, /* allowDelayedLocking= */ false,
                /* keyEvictedCallback= */ null, /* expectLocking= */ true);

        setUpAndStartUserInBackground(TEST_USER_ID3);
        assertUserLockedOrUnlockedAfterStopping(TEST_USER_ID3, /* allowDelayedLocking= */ false,
                /* keyEvictedCallback= */ mKeyEvictedCallback, /* expectLocking= */ true);
    }

    @Test
    public void testStopUser_invalidUser() {
        int userId = -1;

        assertThrows(IllegalArgumentException.class,
                () -> mUserController.stopUser(userId,
                        /* allowDelayedLocking= */ true, /* stopUserCallback= */ null,
                        /* keyEvictedCallback= */ null));
    }

    @Test
    public void testStopUser_systemUser() {
        int userId = UserHandle.USER_SYSTEM;

        int r = mUserController.stopUser(userId,
                /* allowDelayedLocking= */ true, /* stopUserCallback= */ null,
                /* keyEvictedCallback= */ null);

        assertThat(r).isEqualTo(ActivityManager.USER_OP_ERROR_IS_SYSTEM);
    }

    @Test
    public void testStopUser_currentUser() {
        setUpUser(TEST_USER_ID1, /* flags= */ 0);
        mUserController.startUser(TEST_USER_ID1, USER_START_MODE_FOREGROUND);

        int r = mUserController.stopUser(TEST_USER_ID1,
                /* allowDelayedLocking= */ true, /* stopUserCallback= */ null,
                /* keyEvictedCallback= */ null);

        assertThat(r).isEqualTo(ActivityManager.USER_OP_IS_CURRENT);
    }

    /**
     * Test conditional delayed locking with mDelayUserDataLocking true.
     */
    @Test
    public void testUserLockingForDelayedLockingMode() throws Exception {
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 3, /* delayUserDataLocking= */ true,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        // allowDelayedLocking set and no KeyEvictedCallback, so it should not lock.
        setUpAndStartUserInBackground(TEST_USER_ID);
        assertUserLockedOrUnlockedAfterStopping(TEST_USER_ID, /* allowDelayedLocking= */ true,
                /* keyEvictedCallback= */ null, /* expectLocking= */ false);

        setUpAndStartUserInBackground(TEST_USER_ID1);
        assertUserLockedOrUnlockedAfterStopping(TEST_USER_ID1, /* allowDelayedLocking= */ true,
                /* keyEvictedCallback= */ mKeyEvictedCallback, /* expectLocking= */ true);

        setUpAndStartUserInBackground(TEST_USER_ID2);
        assertUserLockedOrUnlockedAfterStopping(TEST_USER_ID2, /* allowDelayedLocking= */ false,
                /* keyEvictedCallback= */ null, /* expectLocking= */ true);

        setUpAndStartUserInBackground(TEST_USER_ID3);
        assertUserLockedOrUnlockedAfterStopping(TEST_USER_ID3, /* allowDelayedLocking= */ false,
                /* keyEvictedCallback= */ mKeyEvictedCallback, /* expectLocking= */ true);
    }

    @Test
    public void testUserNotUnlockedBeforeAllowed() throws Exception {
        mUserController.setAllowUserUnlocking(false);

        mUserController.startUser(TEST_USER_ID, USER_START_MODE_BACKGROUND);

        verify(mInjector.mLockPatternUtilsMock, never()).unlockUserKeyIfUnsecured(TEST_USER_ID);
    }

    @Test
    public void testStartProfile_fullUserFails() {
        setUpUser(TEST_USER_ID1, 0);
        assertThrows(IllegalArgumentException.class,
                () -> mUserController.startProfile(TEST_USER_ID1, /* evenWhenDisabled= */ false,
                        /* unlockListener= */ null));

        verifyUserNeverAssignedToDisplay();
    }

    @Test
    public void testStopProfile_fullUserFails() throws Exception {
        setUpAndStartUserInBackground(TEST_USER_ID1);
        assertThrows(IllegalArgumentException.class,
                () -> mUserController.stopProfile(TEST_USER_ID1));
        verifyUserUnassignedFromDisplayNeverCalled(TEST_USER_ID);
    }

    /** Test that stopping a profile doesn't also stop its parent, even if it's in background. */
    @Test
    public void testStopProfile_doesNotStopItsParent() throws Exception {
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 5, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        final Range<Integer> RUNNING_RANGE =
                Range.closed(UserState.STATE_BOOTING, UserState.STATE_RUNNING_UNLOCKED);

        final int PARENT_ID = TEST_USER_ID1;
        final int PROFILE_ID = TEST_USER_ID2;
        final int OTHER_ID = TEST_USER_ID3;

        setUpUser(PARENT_ID, 0).profileGroupId = PARENT_ID;
        setUpUser(PROFILE_ID, UserInfo.FLAG_PROFILE).profileGroupId = PARENT_ID;
        setUpUser(OTHER_ID, 0).profileGroupId = OTHER_ID;
        mUserController.onSystemReady(); // To set the profileGroupIds in UserController.

        // Start the parent in the background
        boolean started = mUserController.startUser(PARENT_ID, USER_START_MODE_BACKGROUND);
        assertWithMessage("startUser(%s)", PARENT_ID).that(started).isTrue();
        assertThat(mUserController.getStartedUserState(PARENT_ID).state).isIn(RUNNING_RANGE);

        // Start the profile
        started = mUserController.startProfile(PROFILE_ID, true, null);
        assertWithMessage("startProfile(%s)", PROFILE_ID).that(started).isTrue();
        assertThat(mUserController.getStartedUserState(PARENT_ID).state).isIn(RUNNING_RANGE);
        assertThat(mUserController.getStartedUserState(PROFILE_ID).state).isIn(RUNNING_RANGE);

        // Start an unrelated user
        started = mUserController.startUser(OTHER_ID, USER_START_MODE_FOREGROUND);
        assertWithMessage("startUser(%s)", OTHER_ID).that(started).isTrue();
        assertThat(mUserController.getStartedUserState(PARENT_ID).state).isIn(RUNNING_RANGE);
        assertThat(mUserController.getStartedUserState(PROFILE_ID).state).isIn(RUNNING_RANGE);
        assertThat(mUserController.getStartedUserState(OTHER_ID).state).isIn(RUNNING_RANGE);

        // Stop the profile and assert that its (background) parent didn't stop too
        boolean stopped = mUserController.stopProfile(PROFILE_ID);
        assertWithMessage("stopProfile(%s)", PROFILE_ID).that(stopped).isTrue();
        if (mUserController.getStartedUserState(PROFILE_ID) != null) {
            assertThat(mUserController.getStartedUserState(PROFILE_ID).state)
                    .isNotIn(RUNNING_RANGE);
        }
        assertThat(mUserController.getStartedUserState(PARENT_ID).state).isIn(RUNNING_RANGE);
    }

    @Test
    public void testStartProfile_disabledProfileFails() {
        setUpUser(TEST_USER_ID1, UserInfo.FLAG_PROFILE | UserInfo.FLAG_DISABLED, /* preCreated= */
                false, USER_TYPE_PROFILE_MANAGED);
        assertThat(mUserController.startProfile(TEST_USER_ID1, /* evenWhenDisabled=*/ false,
                /* unlockListener= */ null)).isFalse();

        verifyUserNeverAssignedToDisplay();
    }

    @Test
    public void testStartManagedProfile() throws Exception {
        mUserController.setInitialConfig(/* mUserSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 3, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        setUpAndStartProfileInBackground(TEST_USER_ID1, USER_TYPE_PROFILE_MANAGED);

        startBackgroundUserAssertions();
        verifyUserAssignedToDisplay(TEST_USER_ID1, Display.DEFAULT_DISPLAY);
    }

    @Test
    public void testStartManagedProfile_whenUsersOnSecondaryDisplaysIsEnabled() throws Exception {
        mUserController.setInitialConfig(/* mUserSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 3, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);
        mockIsUsersOnSecondaryDisplaysEnabled(true);

        setUpAndStartProfileInBackground(TEST_USER_ID1, USER_TYPE_PROFILE_MANAGED);

        startBackgroundUserAssertions();
        verifyUserAssignedToDisplay(TEST_USER_ID1, Display.DEFAULT_DISPLAY);
    }

    @Test
    public void testStopManagedProfile() throws Exception {
        setUpAndStartProfileInBackground(TEST_USER_ID1, USER_TYPE_PROFILE_MANAGED);
        assertProfileLockedOrUnlockedAfterStopping(TEST_USER_ID1, /* expectLocking= */ true);
        verifyUserUnassignedFromDisplay(TEST_USER_ID1);
    }

    @Test
    public void testStopPrivateProfile() throws Exception {
        mUserController.setInitialConfig(/* mUserSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 3, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);
        mSetFlagsRule.enableFlags(
                android.multiuser.Flags.FLAG_ENABLE_BIOMETRICS_TO_UNLOCK_PRIVATE_SPACE);
        setUpAndStartProfileInBackground(TEST_USER_ID1, UserManager.USER_TYPE_PROFILE_PRIVATE);
        assertProfileLockedOrUnlockedAfterStopping(TEST_USER_ID1, /* expectLocking= */ true);
        verifyUserUnassignedFromDisplay(TEST_USER_ID1);

        mSetFlagsRule.disableFlags(
                android.multiuser.Flags.FLAG_ENABLE_BIOMETRICS_TO_UNLOCK_PRIVATE_SPACE);
        setUpAndStartProfileInBackground(TEST_USER_ID2, UserManager.USER_TYPE_PROFILE_PRIVATE);
        assertProfileLockedOrUnlockedAfterStopping(TEST_USER_ID2, /* expectLocking= */ true);
        verifyUserUnassignedFromDisplay(TEST_USER_ID2);
    }

    @Test
    public void testStopPrivateProfileWithDelayedLocking() throws Exception {
        mUserController.setInitialConfig(/* mUserSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 3, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);
        mSetFlagsRule.enableFlags(
                android.multiuser.Flags.FLAG_ENABLE_BIOMETRICS_TO_UNLOCK_PRIVATE_SPACE);
        setUpAndStartProfileInBackground(TEST_USER_ID1, UserManager.USER_TYPE_PROFILE_PRIVATE);
        assertUserLockedOrUnlockedAfterStopping(TEST_USER_ID1, /* allowDelayedLocking= */ true,
                /* keyEvictedCallback */ null, /* expectLocking= */ false);
    }

    /** Delayed-locking users (as opposed to devices) have no limits on how many can be unlocked. */
    @Test
    public void testStopPrivateProfileWithDelayedLocking_imperviousToNumberOfRunningUsers()
            throws Exception {
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 1, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);
        mSetFlagsRule.enableFlags(
                android.multiuser.Flags.FLAG_ENABLE_BIOMETRICS_TO_UNLOCK_PRIVATE_SPACE);
        setUpAndStartProfileInBackground(TEST_USER_ID1, UserManager.USER_TYPE_PROFILE_PRIVATE);
        setUpAndStartProfileInBackground(TEST_USER_ID2, USER_TYPE_PROFILE_MANAGED);
        assertUserLockedOrUnlockedAfterStopping(TEST_USER_ID1, /* allowDelayedLocking= */ true,
                /* keyEvictedCallback */ null, /* expectLocking= */ false);
    }

    /**
     * Tests that when a device/user (managed profile) does not permit delayed locking, then
     * even if allowDelayedLocking is true, the user will still be locked.
     */
    @Test
    public void testStopManagedProfileWithDelayedLocking() throws Exception {
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 3, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);
        mSetFlagsRule.enableFlags(
                android.multiuser.Flags.FLAG_ENABLE_BIOMETRICS_TO_UNLOCK_PRIVATE_SPACE);
        setUpAndStartProfileInBackground(TEST_USER_ID1, USER_TYPE_PROFILE_MANAGED);
        assertUserLockedOrUnlockedAfterStopping(TEST_USER_ID1, /* allowDelayedLocking= */ true,
                /* keyEvictedCallback */ null, /* expectLocking= */ true);
    }

    /** Tests handleIncomingUser() for a variety of permissions and situations. */
    @Test
    public void testHandleIncomingUser() throws Exception {
        final UserInfo user1a = new UserInfo(111, "user1a", 0);
        final UserInfo user1b = new UserInfo(112, "user1b", 0);
        final UserInfo user2 = new UserInfo(113, "user2", 0);
        // user1a and user2b are in the same profile group; user2 is in a different one.
        user1a.profileGroupId = 5;
        user1b.profileGroupId = 5;
        user2.profileGroupId = 6;

        final List<UserInfo> users = Arrays.asList(user1a, user1b, user2);
        when(mInjector.mUserManagerMock.getUsers(false)).thenReturn(users);
        mUserController.onSystemReady(); // To set the profileGroupIds in UserController.


        // Has INTERACT_ACROSS_USERS_FULL.
        when(mInjector.checkComponentPermission(
                eq(INTERACT_ACROSS_USERS_FULL), anyInt(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mInjector.checkComponentPermission(
                eq(INTERACT_ACROSS_USERS), anyInt(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        when(mInjector.checkPermissionForPreflight(
                eq(INTERACT_ACROSS_PROFILES), anyInt(), anyInt(), any())).thenReturn(false);

        checkHandleIncomingUser(user1a.id, user2.id, ALLOW_NON_FULL, true);
        checkHandleIncomingUser(user1a.id, user2.id, ALLOW_NON_FULL_IN_PROFILE, true);
        checkHandleIncomingUser(user1a.id, user2.id, ALLOW_FULL_ONLY, true);
        checkHandleIncomingUser(user1a.id, user2.id, ALLOW_PROFILES_OR_NON_FULL, true);

        checkHandleIncomingUser(user1a.id, user1b.id, ALLOW_NON_FULL, true);
        checkHandleIncomingUser(user1a.id, user1b.id, ALLOW_NON_FULL_IN_PROFILE, true);
        checkHandleIncomingUser(user1a.id, user1b.id, ALLOW_FULL_ONLY, true);
        checkHandleIncomingUser(user1a.id, user1b.id, ALLOW_PROFILES_OR_NON_FULL, true);


        // Has INTERACT_ACROSS_USERS.
        when(mInjector.checkComponentPermission(
                eq(INTERACT_ACROSS_USERS_FULL), anyInt(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        when(mInjector.checkComponentPermission(
                eq(INTERACT_ACROSS_USERS), anyInt(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mInjector.checkPermissionForPreflight(
                eq(INTERACT_ACROSS_PROFILES), anyInt(), anyInt(), any())).thenReturn(false);

        checkHandleIncomingUser(user1a.id, user2.id, ALLOW_NON_FULL, true);
        checkHandleIncomingUser(user1a.id, user2.id, ALLOW_NON_FULL_IN_PROFILE, false);
        checkHandleIncomingUser(user1a.id, user2.id, ALLOW_FULL_ONLY, false);
        checkHandleIncomingUser(user1a.id, user2.id, ALLOW_PROFILES_OR_NON_FULL, true);

        checkHandleIncomingUser(user1a.id, user1b.id, ALLOW_NON_FULL, true);
        checkHandleIncomingUser(user1a.id, user1b.id, ALLOW_NON_FULL_IN_PROFILE, true);
        checkHandleIncomingUser(user1a.id, user1b.id, ALLOW_FULL_ONLY, false);
        checkHandleIncomingUser(user1a.id, user1b.id, ALLOW_PROFILES_OR_NON_FULL, true);


        // Has INTERACT_ACROSS_PROFILES.
        when(mInjector.checkComponentPermission(
                eq(INTERACT_ACROSS_USERS_FULL), anyInt(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        when(mInjector.checkComponentPermission(
                eq(INTERACT_ACROSS_USERS), anyInt(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        when(mInjector.checkPermissionForPreflight(
                eq(INTERACT_ACROSS_PROFILES), anyInt(), anyInt(), any())).thenReturn(true);

        checkHandleIncomingUser(user1a.id, user2.id, ALLOW_NON_FULL, false);
        checkHandleIncomingUser(user1a.id, user2.id, ALLOW_NON_FULL_IN_PROFILE, false);
        checkHandleIncomingUser(user1a.id, user2.id, ALLOW_FULL_ONLY, false);
        checkHandleIncomingUser(user1a.id, user2.id, ALLOW_PROFILES_OR_NON_FULL, false);

        checkHandleIncomingUser(user1a.id, user1b.id, ALLOW_NON_FULL, false);
        checkHandleIncomingUser(user1a.id, user1b.id, ALLOW_NON_FULL_IN_PROFILE, false);
        checkHandleIncomingUser(user1a.id, user1b.id, ALLOW_FULL_ONLY, false);
        checkHandleIncomingUser(user1a.id, user1b.id, ALLOW_PROFILES_OR_NON_FULL, true);
    }

    private void checkHandleIncomingUser(int fromUser, int toUser, int allowMode, boolean pass) {
        final int pid = 100;
        final int uid = fromUser * UserHandle.PER_USER_RANGE + 34567 + fromUser;
        final String name = "whatever";
        final String pkg = "some.package";
        final boolean allowAll = false;

        if (pass) {
            mUserController.handleIncomingUser(pid, uid, toUser, allowAll, allowMode, name, pkg);
        } else {
            assertThrows(SecurityException.class, () -> mUserController.handleIncomingUser(
                    pid, uid, toUser, allowAll, allowMode, name, pkg));
        }
    }

    @Test
    public void testScheduleOnUserCompletedEvent() throws Exception {
        // user1 is starting, switching, and unlocked, but not scheduled unlocked yet
        // user2 is starting and had unlocked but isn't unlocked anymore for whatever reason

        final int user1 = 101;
        final int user2 = 102;
        setUpUser(user1, 0);
        setUpUser(user2, 0);

        mUserController.startUser(user1, USER_START_MODE_FOREGROUND);
        mUserController.getStartedUserState(user1).setState(UserState.STATE_RUNNING_UNLOCKED);

        mUserController.startUser(user2, USER_START_MODE_BACKGROUND);
        mUserController.getStartedUserState(user2).setState(UserState.STATE_RUNNING_LOCKED);

        final int event1a = SystemService.UserCompletedEventType.EVENT_TYPE_USER_STARTING;
        final int event1b = SystemService.UserCompletedEventType.EVENT_TYPE_USER_SWITCHING;

        final int event2a = SystemService.UserCompletedEventType.EVENT_TYPE_USER_STARTING;
        final int event2b = SystemService.UserCompletedEventType.EVENT_TYPE_USER_UNLOCKED;


        mUserController.scheduleOnUserCompletedEvent(user1, event1a, 2000);
        assertNotNull(mInjector.mHandler.getMessageForCode(USER_COMPLETED_EVENT_MSG, user1));
        assertNull(mInjector.mHandler.getMessageForCode(USER_COMPLETED_EVENT_MSG, user2));

        mUserController.scheduleOnUserCompletedEvent(user2, event2a, 2000);
        assertNotNull(mInjector.mHandler.getMessageForCode(USER_COMPLETED_EVENT_MSG, user1));
        assertNotNull(mInjector.mHandler.getMessageForCode(USER_COMPLETED_EVENT_MSG, user2));

        mUserController.scheduleOnUserCompletedEvent(user2, event2b, 2000);
        mUserController.scheduleOnUserCompletedEvent(user1, event1b, 2000);
        mUserController.scheduleOnUserCompletedEvent(user1, 0, 2000);

        assertNotNull(mInjector.mHandler.getMessageForCode(USER_COMPLETED_EVENT_MSG, user1));
        assertNotNull(mInjector.mHandler.getMessageForCode(USER_COMPLETED_EVENT_MSG, user2));

        mUserController.reportOnUserCompletedEvent(user1);
        verify(mInjector, times(1))
                .systemServiceManagerOnUserCompletedEvent(eq(user1), eq(event1a | event1b));
        verify(mInjector, never()).systemServiceManagerOnUserCompletedEvent(eq(user2), anyInt());

        mUserController.reportOnUserCompletedEvent(user2);
        verify(mInjector, times(1))
                .systemServiceManagerOnUserCompletedEvent(eq(user2), eq(event2a));
    }

    @Test
    public void testStallUserSwitchUntilTheKeyguardIsShown() throws Exception {
        // enable user switch ui, because keyguard is only shown then
        mUserController.setInitialConfig(/* userSwitchUiEnabled= */ true,
                /* maxRunningUsers= */ 3, /* delayUserDataLocking= */ false,
                /* backgroundUserConsideredDispensableTimeSecs= */ -1);

        // mock the device to be secure in order to expect the keyguard to be shown
        when(mInjector.mKeyguardManagerMock.isDeviceSecure(anyInt())).thenReturn(true);

        // call real lockDeviceNowAndWaitForKeyguardShown method for this test
        doCallRealMethod().when(mInjector).lockDeviceNowAndWaitForKeyguardShown();

        // call startUser on a thread because we're expecting it to be blocked
        Thread threadStartUser = new Thread(()-> {
            mUserController.startUser(TEST_USER_ID, USER_START_MODE_FOREGROUND);
        });
        threadStartUser.start();

        // make sure the switch is stalled...
        Thread.sleep(2000);
        // by checking REPORT_USER_SWITCH_MSG is not sent yet
        assertNull(mInjector.mHandler.getMessageForCode(REPORT_USER_SWITCH_MSG));
        // and the thread is still alive
        assertTrue(threadStartUser.isAlive());

        // mock the binder response for the user switch completion
        ArgumentCaptor<Bundle> captor = ArgumentCaptor.forClass(Bundle.class);
        verify(mInjector.mWindowManagerMock).lockNow(captor.capture());
        IRemoteCallback.Stub.asInterface(captor.getValue().getBinder(
                LOCK_ON_USER_SWITCH_CALLBACK)).sendResult(null);

        // verify the switch now moves on...
        Thread.sleep(1000);
        // by checking REPORT_USER_SWITCH_MSG is sent
        assertNotNull(mInjector.mHandler.getMessageForCode(REPORT_USER_SWITCH_MSG));
        // and the thread is finished
        assertFalse(threadStartUser.isAlive());
    }

    private void setUpAndStartUserInBackground(int userId) throws Exception {
        setUpUser(userId, DEFAULT_USER_FLAGS);
        mUserController.startUser(userId, USER_START_MODE_BACKGROUND);
        verify(mInjector.mLockPatternUtilsMock, times(1)).unlockUserKeyIfUnsecured(userId);
        mUserStates.put(userId, mUserController.getStartedUserState(userId));
    }

    private void setUpAndStartProfileInBackground(int userId, String userType) throws Exception {
        setUpUser(userId, UserInfo.FLAG_PROFILE, false, userType);
        assertThat(mUserController.startProfile(userId, /* evenWhenDisabled=*/ false,
                /* unlockListener= */ null)).isTrue();

        verify(mInjector.mLockPatternUtilsMock, times(1)).unlockUserKeyIfUnsecured(userId);
        mUserStates.put(userId, mUserController.getStartedUserState(userId));
    }

    private void assertUserLockedOrUnlockedAfterStopping(int userId, boolean allowDelayedLocking,
            KeyEvictedCallback keyEvictedCallback, boolean expectLocking) throws Exception {
        int r = mUserController.stopUser(userId, /* allowDelayedLocking= */
                allowDelayedLocking, null, keyEvictedCallback);
        assertThat(r).isEqualTo(ActivityManager.USER_OP_SUCCESS);
        assertUserLockedOrUnlockedState(userId, allowDelayedLocking, expectLocking);
    }

    private void assertProfileLockedOrUnlockedAfterStopping(int userId, boolean expectLocking)
            throws Exception {
        boolean profileStopped = mUserController.stopProfile(userId);
        assertThat(profileStopped).isTrue();
        assertUserLockedOrUnlockedState(userId, /* allowDelayedLocking= */ false, expectLocking);
    }

    private void assertUserLockedOrUnlockedState(int userId, boolean allowDelayedLocking,
            boolean expectLocking) throws InterruptedException, RemoteException {
        // fake all interim steps
        UserState ussUser = mUserStates.get(userId);
        ussUser.setState(UserState.STATE_SHUTDOWN);
        // Passing delayedLocking invalidates incorrect internal data passing but currently there is
        // no easy way to get that information passed through lambda.
        mUserController.finishUserStopped(ussUser, allowDelayedLocking);
        waitForHandlerToComplete(FgThread.getHandler(), HANDLER_WAIT_TIME_MS);
        verify(mInjector.mLockSettingsInternalMock, times(expectLocking ? 1 : 0))
                .lockUser(eq(userId));
    }

    private void addForegroundUserAndContinueUserSwitch(int newUserId, int expectedOldUserId,
            int expectedNumberOfCalls, boolean expectOldUserStopping,
            boolean expectScheduleBackgroundUserJudgement) {
        // Start user -- this will update state of mUserController
        mUserController.startUser(newUserId, USER_START_MODE_FOREGROUND);
        Message reportMsg = mInjector.mHandler.getMessageForCode(REPORT_USER_SWITCH_MSG);
        assertNotNull(reportMsg);
        UserState userState = (UserState) reportMsg.obj;
        int oldUserId = reportMsg.arg1;
        assertEquals(expectedOldUserId, oldUserId);
        assertEquals(newUserId, reportMsg.arg2);
        mUserStates.put(newUserId, userState);
        mInjector.mHandler.clearAllRecordedMessages();
        // Verify that continueUserSwitch worked as expected
        continueAndCompleteUserSwitch(userState, oldUserId, newUserId);
        assertEquals("Wrong expected scheduled judgement", expectScheduleBackgroundUserJudgement,
                mInjector.mHandler.hasEqualMessages(
                        SCHEDULE_JUDGE_FATE_OF_BACKGROUND_USER_MSG,
                        new UserAndLmkThreshold(expectedOldUserId, -300)));
        verify(mInjector, times(expectedNumberOfCalls)).dismissUserSwitchingDialog(any());
        continueUserSwitchAssertions(oldUserId, newUserId, expectOldUserStopping,
                expectScheduleBackgroundUserJudgement);
    }

    /** Asserts that the list of running users matches the input, ignoring order. */
    private void assertRunningUsersIgnoreOrder(Integer... userIds) {
        assertEquals(new HashSet<>(Arrays.asList(userIds)),
                new HashSet<>(mUserController.getRunningUsersLU()));
    }

    /** Asserts that the list of running users matches the input, in the same order. */
    private void assertRunningUsersInOrder(Integer... userIds) {
        assertEquals(Arrays.asList(userIds), mUserController.getRunningUsersLU());
    }

    private UserInfo setUpUser(@UserIdInt int userId, @UserInfoFlag int flags) {
        return setUpUser(userId, flags, /* preCreated= */ false, /* userType */ null);
    }

    private UserInfo setUpUser(@UserIdInt int userId, @UserInfoFlag int flags, boolean preCreated,
            @Nullable String userType) {
        if (userType == null) {
            userType = UserInfo.getDefaultUserType(flags);
        }

        UserTypeDetails userTypeDetails = UserTypeFactory.getUserTypes().get(userType);
        assertThat(userTypeDetails).isNotNull();
        when(mInjector.mUserManagerInternalMock.getUserProperties(eq(userId)))
                .thenReturn(userTypeDetails.getDefaultUserPropertiesReference());

        flags |= userTypeDetails.getDefaultUserInfoFlags();

        UserInfo userInfo = new UserInfo(userId, "User" + userId, /* iconPath= */ null, flags,
                userType);
        userInfo.preCreated = preCreated;
        when(mInjector.mUserManagerMock.getUserInfo(eq(userId))).thenReturn(userInfo);
        when(mInjector.mUserManagerMock.isPreCreated(userId)).thenReturn(preCreated);

        mUserInfos.put(userId, userInfo);
        when(mInjector.mUserManagerMock.getUsers(anyBoolean()))
                .thenReturn(mUserInfos.values().stream().toList());

        return userInfo;
    }

    private static List<String> getActions(List<Intent> intents) {
        List<String> result = new ArrayList<>();
        for (Intent intent : intents) {
            result.add(intent.getAction());
        }
        return result;
    }

    private void waitForHandlerToComplete(Handler handler, long waitTimeMs)
            throws InterruptedException {
        final Object lock = new Object();
        synchronized (lock) {
            handler.post(() -> {
                synchronized (lock) {
                    lock.notify();
                }
            });
            lock.wait(waitTimeMs);
        }
    }

    /** Specify whether to mock the device as being in regular (non-switchable) HSUM. */
    private void mockIsHeadlessSystemUserMode(boolean isHsum) {
        mockIsHeadlessSystemUserMode(isHsum, false);
    }

    /** Mocks the device as being in interactive (switchable) HSUM. */
    private void mockIsSwitchableHeadlessSystemUserMode() {
        mockIsHeadlessSystemUserMode(true, true);
    }

    private void mockIsHeadlessSystemUserMode(boolean isHsum, boolean canSwitch) {
        when(mInjector.isHeadlessSystemUserMode()).thenReturn(isHsum);
        UserInfo sysInfo = setUpUser(SYSTEM_USER_ID, UserInfo.FLAG_SYSTEM, /* preCreated= */ false,
                isHsum ? UserManager.USER_TYPE_SYSTEM_HEADLESS : UserManager.USER_TYPE_FULL_SYSTEM);
        if (isHsum) {
            doReturn(canSwitch).when(mInjector.mUserManagerMock).canSwitchToHeadlessSystemUser();
            doReturn(canSwitch).when(mInjector).doesUserSupportSwitchTo(eq(sysInfo));
        }
    }

    private void mockIsUsersOnSecondaryDisplaysEnabled(boolean value) {
        when(mInjector.isUsersOnSecondaryDisplaysEnabled()).thenReturn(value);
    }

    private void verifyUserAssignedToDisplay(@UserIdInt int userId, int displayId) {
        verify(mInjector.getUserManagerInternal()).assignUserToDisplayOnStart(eq(userId), anyInt(),
                anyInt(), eq(displayId));
    }

    private void verifyUserNeverAssignedToDisplay() {
        verify(mInjector.getUserManagerInternal(), never()).assignUserToDisplayOnStart(anyInt(),
                anyInt(), anyInt(), anyInt());
    }

    private void verifyUserUnassignedFromDisplay(@UserIdInt int userId) {
        verify(mInjector.getUserManagerInternal()).unassignUserFromDisplayOnStop(userId);
    }

    private void verifyUserUnassignedFromDisplayNeverCalled(@UserIdInt int userId) {
        verify(mInjector.getUserManagerInternal(), never()).unassignUserFromDisplayOnStop(userId);
    }

    private void verifySystemUserVisibilityChangesNeverNotified() {
        verify(mInjector, never()).onSystemUserVisibilityChanged(anyBoolean());
    }

    private IUserSwitchObserver registerUserSwitchObserver(
            boolean replyToOnBeforeUserSwitchingCallback, boolean replyToOnUserSwitchingCallback)
            throws RemoteException {
        IUserSwitchObserver observer = mock(IUserSwitchObserver.class);
        when(observer.asBinder()).thenReturn(new Binder());
        if (replyToOnBeforeUserSwitchingCallback) {
            doAnswer(invocation -> {
                IRemoteCallback callback = (IRemoteCallback) invocation.getArguments()[1];
                callback.sendResult(null);
                return null;
            }).when(observer).onBeforeUserSwitching(anyInt(), any());
        }
        if (replyToOnUserSwitchingCallback) {
            doAnswer(invocation -> {
                IRemoteCallback callback = (IRemoteCallback) invocation.getArguments()[1];
                callback.sendResult(null);
                return null;
            }).when(observer).onUserSwitching(anyInt(), any());
        }
        mUserController.registerUserSwitchObserver(observer, "mock");
        return observer;
    }

    // Should be public to allow mocking
    private static class TestInjector extends UserController.Injector {
        public final TestHandler mHandler;
        public final HandlerThread mHandlerThread;
        public final UserManagerService mUserManagerMock;
        public final List<Intent> mSentIntents = new ArrayList<>();

        private final TestHandler mUiHandler;

        private final UserManagerInternal mUserManagerInternalMock;
        private final LockSettingsInternal mLockSettingsInternalMock;
        private final WindowManagerService mWindowManagerMock;
        private final PowerManagerInternal mPowerManagerInternal;
        private final AlarmManagerInternal mAlarmManagerInternal;
        private final AudioManagerInternal mAudioManagerInternal;
        private final KeyguardManager mKeyguardManagerMock;
        private final LockPatternUtils mLockPatternUtilsMock;

        private final UserJourneyLogger mUserJourneyLoggerMock;

        private final Context mCtx;

        private Integer mRelevantUser;

        TestInjector(Context ctx) {
            super(null);
            mCtx = ctx;
            mHandlerThread = new HandlerThread(TAG);
            mHandlerThread.start();
            mHandler = new TestHandler(mHandlerThread.getLooper());
            mUiHandler = new TestHandler(mHandlerThread.getLooper());
            mUserManagerMock = mock(UserManagerService.class);
            mUserManagerInternalMock = mock(UserManagerInternal.class);
            mLockSettingsInternalMock = mock(LockSettingsInternal.class);
            mWindowManagerMock = mock(WindowManagerService.class);
            mPowerManagerInternal = mock(PowerManagerInternal.class);
            mAlarmManagerInternal = mock(AlarmManagerInternal.class);
            mAudioManagerInternal = mock(AudioManagerInternal.class);
            mKeyguardManagerMock = mock(KeyguardManager.class);
            when(mKeyguardManagerMock.isDeviceSecure(anyInt())).thenReturn(true);
            mLockPatternUtilsMock = mock(LockPatternUtils.class);
            mUserJourneyLoggerMock = mock(UserJourneyLogger.class);
        }

        @Override
        protected Handler getHandler(Handler.Callback callback) {
            return mHandler;
        }

        @Override
        protected Handler getUiHandler(Handler.Callback callback) {
            return mUiHandler;
        }

        @Override
        protected UserManagerService getUserManager() {
            return mUserManagerMock;
        }

        @Override
        UserManagerInternal getUserManagerInternal() {
            return mUserManagerInternalMock;
        }

        @Override
        LockSettingsInternal getLockSettingsInternal() {
            return mLockSettingsInternalMock;
        }

        @Override
        protected Context getContext() {
            return mCtx;
        }

        @Override
        int checkCallingPermission(String permission) {
            Log.i(TAG, "checkCallingPermission " + permission);
            return PERMISSION_GRANTED;
        }

        @Override
        int checkComponentPermission(String permission, int pid, int uid, int owner, boolean exp) {
            Log.i(TAG, "checkComponentPermission " + permission);
            return PERMISSION_GRANTED;
        }

        @Override
        boolean checkPermissionForPreflight(String permission, int pid, int uid, String pkg) {
            Log.i(TAG, "checkPermissionForPreflight " + permission);
            return true;
        }

        @Override
        boolean isCallerRecents(int uid) {
            return false;
        }

        @Override
        WindowManagerService getWindowManager() {
            return mWindowManagerMock;
        }

        @Override
        PowerManagerInternal getPowerManagerInternal() {
            return mPowerManagerInternal;
        }

        @Override
        AlarmManagerInternal getAlarmManagerInternal() {
            return mAlarmManagerInternal;
        }

        @Override
        AudioManagerInternal getAudioManagerInternal() {
            return mAudioManagerInternal;
        }

        @Override
        KeyguardManager getKeyguardManager() {
            return mKeyguardManagerMock;
        }

        @Override
        void updateUserConfiguration() {
            Log.i(TAG, "updateUserConfiguration");
        }

        @Override
        protected int broadcastIntent(Intent intent, String resolvedType,
                IIntentReceiver resultTo, int resultCode, String resultData, Bundle resultExtras,
                String[] requiredPermissions, int appOp, Bundle bOptions,
                boolean sticky, int callingPid, int callingUid, int realCallingUid,
                int realCallingPid, int userId) {
            Log.i(TAG, "broadcastIntentLocked " + intent);
            if (mRelevantUser == null || mRelevantUser == userId || userId == UserHandle.USER_ALL) {
                mSentIntents.add(intent);
            }
            return 0;
        }

        @Override
        void reportGlobalUsageEvent(int event) {
        }

        @Override
        void reportCurWakefulnessUsageEvent() {
        }

        @Override
        boolean isRuntimeRestarted() {
            // to pass all metrics related calls
            return true;
        }

        @Override
        void showUserSwitchingDialog(UserInfo fromUser, UserInfo toUser,
                String switchingFromSystemUserMessage, String switchingToSystemUserMessage,
                Runnable onShown) {
            if (onShown != null) {
                onShown.run();
            }
        }

        @Override
        void dismissUserSwitchingDialog(Runnable onDismissed) {
            if (onDismissed != null) {
                onDismissed.run();
            }
        }

        @Override
        protected LockPatternUtils getLockPatternUtils() {
            return mLockPatternUtilsMock;
        }

        @Override
        void onUserStarting(@UserIdInt int userId) {
            Log.i(TAG, "onUserStarting(" + userId + ")");
        }

        @Override
        void onSystemUserVisibilityChanged(boolean visible) {
            Log.i(TAG, "onSystemUserVisibilityChanged(" + visible + ")");
        }

        @Override
        protected UserJourneyLogger getUserJourneyLogger() {
            return mUserJourneyLoggerMock;
        }

        /** A way to mock {@link UserController.Injector#getLmkdKillCount()}. */
        @Override
        int getLmkdKillCount() {
            return 0;
        }
    }

    private static class TestHandler extends Handler {
        /**
         * Keeps an accessible copy of messages that were queued for us to query.
         *
         * WARNING: queued messages get added to this, but processed/removed messages to NOT
         * automatically get removed. This can lead to confusing bugs. Maybe one day someone will
         * fix this, but in the meantime, this is your warning.
         */
        private final List<Message> mMessages = new ArrayList<>();
        private final List<Runnable> mPendingCallbacks = new ArrayList<>();

        TestHandler(Looper looper) {
            super(looper);
        }

        Set<Integer> getMessageCodes() {
            Set<Integer> result = new LinkedHashSet<>();
            for (Message msg : mMessages) {
                result.add(msg.what);
            }
            return result;
        }

        Message getMessageForCode(int what) {
            return getMessageForCode(what, null);
        }

        Message getMessageForCode(int what, Object obj) {
            for (Message msg : mMessages) {
                if (msg.what == what && (obj == null || obj.equals(msg.obj))) {
                    return msg;
                }
            }
            return null;
        }

        public void removeMessageCopy(Message msg) {
            mMessages.remove(msg);
        }

        void clearAllRecordedMessages() {
            mMessages.clear();
        }

        @Override
        public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
            if (msg.getCallback() == null) {
                Message copy = new Message();
                copy.copyFrom(msg);
                copy.when = uptimeMillis;
                mMessages.add(copy);
            } else {
                if (SystemClock.uptimeMillis() >= uptimeMillis) {
                    msg.getCallback().run();
                } else {
                    mPendingCallbacks.add(msg.getCallback());
                }
                msg.setCallback(null);
            }
            return super.sendMessageAtTime(msg, uptimeMillis);
        }

        /** Hackily removes the soonest Message (of the given what and, optionally, object). */
        public void removeSoonestMessage(int what, @Nullable Object object) {
            // Find and remove the soonest message of value what.
            Message soonestMessage = mMessages.stream()
                    .filter(m -> m.what == what && (object == null || object.equals(m.obj)))
                    .min(Comparator.comparingLong(Message::getWhen)).orElse(null);
            mMessages.remove(soonestMessage);

            // Remove all message of value what, then re-add all but the soonest.
            super.removeEqualMessages(what, object);
            mMessages.stream()
                    .filter(m -> m.what == what && (object == null || object.equals(m.obj)))
                    .forEach(m -> {
                        Message copy = new Message();
                        copy.copyFrom(m);
                        super.sendMessageAtTime(copy, m.when);
                    });
        }

        /** Dump this to a String, allowing comparison of the real Message queue with mMessages. */
        public String dumpToString() {
            final StringBuilder sb = new StringBuilder();
            dump(new StringBuilderPrinter(sb), "  ");

            final long now = SystemClock.uptimeMillis();
            sb.append("  Test-mMessages: ");
            for (int i = 0; i < mMessages.size(); i++) {
                Message m = mMessages.get(i);
                sb.append(" ").append(i).append(":{");
                sb.append(" when=");
                TimeUtils.formatDuration(m.when - now, sb);
                sb.append(" what=").append(m.what);
                sb.append(m.arg1 == 0 ? "" : " arg1=" + m.arg1);
                sb.append(m.arg2 == 0 ? "" : " arg2=" + m.arg2);
                sb.append(m.obj == null ? "" : " obj=" + m.obj);
                sb.append(" }");
            }

            return sb.toString();
        }
    }
}
