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

package com.android.server.power;


import static android.os.PowerManager.USER_ACTIVITY_FLAG_INDIRECT;
import static android.os.PowerManagerInternal.WAKEFULNESS_ASLEEP;
import static android.os.PowerManagerInternal.WAKEFULNESS_AWAKE;
import static android.os.PowerManagerInternal.WAKEFULNESS_DOZING;
import static android.os.PowerManagerInternal.WAKEFULNESS_DREAMING;

import static com.android.server.power.PowerManagerService.WAKE_LOCK_BUTTON_BRIGHT;
import static com.android.server.power.PowerManagerService.WAKE_LOCK_SCREEN_BRIGHT;
import static com.android.server.power.PowerManagerService.WAKE_LOCK_SCREEN_DIM;
import static com.android.server.power.PowerManagerService.WAKE_LOCK_SCREEN_TIMEOUT_OVERRIDE;
import static com.android.server.power.ScreenTimeoutOverridePolicy.RELEASE_REASON_NON_INTERACTIVE;
import static com.android.server.power.ScreenTimeoutOverridePolicy.RELEASE_REASON_SCREEN_LOCK;
import static com.android.server.power.ScreenTimeoutOverridePolicy.RELEASE_REASON_NOT_ACQUIRED;
import static com.android.server.power.ScreenTimeoutOverridePolicy.RELEASE_REASON_USER_ACTIVITY_ACCESSIBILITY;
import static com.android.server.power.ScreenTimeoutOverridePolicy.RELEASE_REASON_USER_ACTIVITY_ATTENTION;
import static com.android.server.power.ScreenTimeoutOverridePolicy.RELEASE_REASON_USER_ACTIVITY_BUTTON;
import static com.android.server.power.ScreenTimeoutOverridePolicy.RELEASE_REASON_USER_ACTIVITY_OTHER;
import static com.android.server.power.ScreenTimeoutOverridePolicy.RELEASE_REASON_USER_ACTIVITY_TOUCH;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.ContextWrapper;
import android.content.res.Resources;
import android.os.PowerManager;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link com.android.server.power.ScreenTimeoutOverridePolicy}.
 *
 * Build/Install/Run:
 *  atest PowerServiceTests:ScreenTimeoutOverridePolicyTest
 */
public class ScreenTimeoutOverridePolicyTest {
    private static final int SCREEN_TIMEOUT_OVERRIDE = 10;
    private ContextWrapper mContextSpy;
    private Resources mResourcesSpy;
    private ScreenTimeoutOverridePolicy mScreenTimeoutOverridePolicy;

    private ScreenTimeoutOverridePolicy.PolicyCallback mPolicyCallback;
    private int mReleaseReason = RELEASE_REASON_NOT_ACQUIRED;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContextSpy = spy(new ContextWrapper(ApplicationProvider.getApplicationContext()));
        mResourcesSpy = spy(mContextSpy.getResources());
        when(mContextSpy.getResources()).thenReturn(mResourcesSpy);
        when(mResourcesSpy.getInteger(com.android.internal.R.integer.config_screenTimeoutOverride))
                .thenReturn(SCREEN_TIMEOUT_OVERRIDE);

        mPolicyCallback = reason -> mReleaseReason = reason;
        mScreenTimeoutOverridePolicy =
                new ScreenTimeoutOverridePolicy(
                        mContextSpy, SCREEN_TIMEOUT_OVERRIDE, mPolicyCallback);
    }

    @Test
    public void testUserActivity() {
        mScreenTimeoutOverridePolicy.onUserActivity(WAKE_LOCK_SCREEN_TIMEOUT_OVERRIDE,
                PowerManager.USER_ACTIVITY_EVENT_ATTENTION, 0);
        verifyReason(RELEASE_REASON_USER_ACTIVITY_ATTENTION);

        mScreenTimeoutOverridePolicy.onUserActivity(WAKE_LOCK_SCREEN_TIMEOUT_OVERRIDE,
                PowerManager.USER_ACTIVITY_EVENT_OTHER, 0);
        verifyReason(RELEASE_REASON_USER_ACTIVITY_OTHER);

        mScreenTimeoutOverridePolicy.onUserActivity(WAKE_LOCK_SCREEN_TIMEOUT_OVERRIDE,
                PowerManager.USER_ACTIVITY_EVENT_BUTTON, 0);
        verifyReason(RELEASE_REASON_USER_ACTIVITY_BUTTON);

        mScreenTimeoutOverridePolicy.onUserActivity(WAKE_LOCK_SCREEN_TIMEOUT_OVERRIDE,
                PowerManager.USER_ACTIVITY_EVENT_TOUCH, 0);
        verifyReason(RELEASE_REASON_USER_ACTIVITY_TOUCH);

        mScreenTimeoutOverridePolicy.onUserActivity(WAKE_LOCK_SCREEN_TIMEOUT_OVERRIDE,
                PowerManager.USER_ACTIVITY_EVENT_ACCESSIBILITY, 0);
        verifyReason(RELEASE_REASON_USER_ACTIVITY_ACCESSIBILITY);
    }

    @Test
    public void testUserActivityIndirect() {
        mScreenTimeoutOverridePolicy.onUserActivity(WAKE_LOCK_SCREEN_TIMEOUT_OVERRIDE,
                PowerManager.USER_ACTIVITY_EVENT_ATTENTION, USER_ACTIVITY_FLAG_INDIRECT);
        verifyReason(RELEASE_REASON_NOT_ACQUIRED);

        mScreenTimeoutOverridePolicy.onUserActivity(WAKE_LOCK_SCREEN_TIMEOUT_OVERRIDE,
                PowerManager.USER_ACTIVITY_EVENT_OTHER, USER_ACTIVITY_FLAG_INDIRECT);
        verifyReason(RELEASE_REASON_NOT_ACQUIRED);

        mScreenTimeoutOverridePolicy.onUserActivity(WAKE_LOCK_SCREEN_TIMEOUT_OVERRIDE,
                PowerManager.USER_ACTIVITY_EVENT_BUTTON, USER_ACTIVITY_FLAG_INDIRECT);
        verifyReason(RELEASE_REASON_NOT_ACQUIRED);

        mScreenTimeoutOverridePolicy.onUserActivity(WAKE_LOCK_SCREEN_TIMEOUT_OVERRIDE,
                PowerManager.USER_ACTIVITY_EVENT_TOUCH, USER_ACTIVITY_FLAG_INDIRECT);
        verifyReason(RELEASE_REASON_NOT_ACQUIRED);

        mScreenTimeoutOverridePolicy.onUserActivity(WAKE_LOCK_SCREEN_TIMEOUT_OVERRIDE,
                PowerManager.USER_ACTIVITY_EVENT_ACCESSIBILITY, USER_ACTIVITY_FLAG_INDIRECT);
        verifyReason(RELEASE_REASON_NOT_ACQUIRED);
    }

    @Test
    public void testScreenWakeLock() {
        mScreenTimeoutOverridePolicy.checkScreenWakeLock(WAKE_LOCK_SCREEN_TIMEOUT_OVERRIDE);
        verifyReason(RELEASE_REASON_NOT_ACQUIRED);

        mScreenTimeoutOverridePolicy.checkScreenWakeLock(
                WAKE_LOCK_SCREEN_TIMEOUT_OVERRIDE | WAKE_LOCK_SCREEN_BRIGHT);
        verifyReason(RELEASE_REASON_SCREEN_LOCK);

        mScreenTimeoutOverridePolicy.checkScreenWakeLock(
                WAKE_LOCK_SCREEN_TIMEOUT_OVERRIDE | WAKE_LOCK_SCREEN_DIM);
        verifyReason(RELEASE_REASON_SCREEN_LOCK);

        mScreenTimeoutOverridePolicy.checkScreenWakeLock(
                WAKE_LOCK_SCREEN_TIMEOUT_OVERRIDE | WAKE_LOCK_BUTTON_BRIGHT);
        verifyReason(RELEASE_REASON_SCREEN_LOCK);
    }


    @Test
    public void testWakefulnessChange() {
        mScreenTimeoutOverridePolicy.onWakefulnessChange(
                WAKE_LOCK_SCREEN_TIMEOUT_OVERRIDE, WAKEFULNESS_AWAKE);
        verifyReason(RELEASE_REASON_NOT_ACQUIRED);

        mScreenTimeoutOverridePolicy.onWakefulnessChange(
                WAKE_LOCK_SCREEN_TIMEOUT_OVERRIDE, WAKEFULNESS_DREAMING);
        verifyReason(RELEASE_REASON_NOT_ACQUIRED);

        mScreenTimeoutOverridePolicy.onWakefulnessChange(
                WAKE_LOCK_SCREEN_TIMEOUT_OVERRIDE, WAKEFULNESS_ASLEEP);
        verifyReason(RELEASE_REASON_NON_INTERACTIVE);

        mScreenTimeoutOverridePolicy.onWakefulnessChange(
                WAKE_LOCK_SCREEN_TIMEOUT_OVERRIDE, WAKEFULNESS_DOZING);
        verifyReason(RELEASE_REASON_NON_INTERACTIVE);
    }

    private void verifyReason(int expectedReason) {
        assertThat(mReleaseReason).isEqualTo(expectedReason);
        mReleaseReason = RELEASE_REASON_NOT_ACQUIRED;
    }
}
