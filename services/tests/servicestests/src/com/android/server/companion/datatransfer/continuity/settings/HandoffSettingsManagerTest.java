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

package com.android.server.companion.datatransfer.continuity.settings;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.companion.datatransfer.continuity.IHandoffFeatureStateListener;
import android.companion.datatransfer.continuity.TaskContinuityManager;
import android.os.Bundle;
import android.os.UserManager;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import com.android.server.LocalServices;
import com.android.server.pm.UserManagerInternal;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class HandoffSettingsManagerTest {
    private static final int USER_ID = 1;

    private class FakeHandoffEnabledListener extends IHandoffFeatureStateListener.Stub {
        int mCallCount = 0;
        int mAvailability;
        boolean mEnabled;

        @Override
        public void onHandoffFeatureStateChanged(int availability, boolean enabled) {
            mCallCount++;
            mAvailability = availability;
            mEnabled = enabled;
        }
    }

    @Mock private HandoffPreferenceStore mHandoffPreferenceStore;
    @Mock private UserManagerInternal mMockUserManagerInternal;

    private HandoffSettingsManager mHandoffSettingsManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        LocalServices.addService(UserManagerInternal.class, mMockUserManagerInternal);

        mHandoffSettingsManager = new HandoffSettingsManager(mHandoffPreferenceStore);
    }

    @After
    public void tearDown() {
        LocalServices.removeServiceForTest(UserManagerInternal.class);
    }

    @Test
    public void isHandoffActiveForUser_enabledByPolicy_callsPreferenceStore() {
        when(mMockUserManagerInternal.getUserRestriction(
                        USER_ID, UserManager.DISALLOW_TASK_CONTINUITY_HANDOFF))
                .thenReturn(false);
        when(mHandoffPreferenceStore.isHandoffEnabledForUser(USER_ID)).thenReturn(true);
        assertThat(mHandoffSettingsManager.isHandoffActiveForUser(USER_ID)).isTrue();
        verify(mHandoffPreferenceStore).isHandoffEnabledForUser(USER_ID);
    }

    @Test
    public void setHandoffEnabledForUser_callsPreferenceStoreAndNotifiesListeners() {
        when(mMockUserManagerInternal.getUserRestriction(
                        USER_ID, UserManager.DISALLOW_TASK_CONTINUITY_HANDOFF))
                .thenReturn(false);
        FakeHandoffEnabledListener listener = new FakeHandoffEnabledListener();
        mHandoffSettingsManager.registerHandoffFeatureStateListener(USER_ID, listener);
        mHandoffSettingsManager.setHandoffEnabledForUser(USER_ID, false);
        verify(mHandoffPreferenceStore).setHandoffEnabledForUser(USER_ID, false);
        assertThat(listener.mCallCount).isEqualTo(2);
        assertThat(listener.mAvailability)
                .isEqualTo(TaskContinuityManager.HANDOFF_AVAILABILITY_STATUS_AVAILABLE);
        assertThat(listener.mEnabled).isFalse();
    }

    @Test
    public void isHandoffActiveForUser_disabledByPolicy_returnsFalseByDefault() {
        when(mMockUserManagerInternal.getUserRestriction(
                        USER_ID, UserManager.DISALLOW_TASK_CONTINUITY_HANDOFF))
                .thenReturn(true);
        when(mHandoffPreferenceStore.isHandoffEnabledForUser(USER_ID)).thenReturn(true);
        assertThat(mHandoffSettingsManager.isHandoffActiveForUser(USER_ID)).isFalse();
    }

    @Test
    public void
            setHandoffEnabledForUser_disabledByPolicy_doesNotActivateHandoffButNotifiesListeners() {
        FakeHandoffEnabledListener listener = new FakeHandoffEnabledListener();
        mHandoffSettingsManager.registerHandoffFeatureStateListener(USER_ID, listener);
        when(mMockUserManagerInternal.getUserRestriction(
                        USER_ID, UserManager.DISALLOW_TASK_CONTINUITY_HANDOFF))
                .thenReturn(true);
        when(mHandoffPreferenceStore.isHandoffEnabledForUser(USER_ID)).thenReturn(false);
        mHandoffSettingsManager.setHandoffEnabledForUser(USER_ID, true);
        assertThat(mHandoffSettingsManager.isHandoffActiveForUser(USER_ID)).isFalse();
        assertThat(listener.mCallCount).isEqualTo(2);
        assertThat(listener.mAvailability)
                .isEqualTo(TaskContinuityManager.HANDOFF_AVAILABILITY_STATUS_DISABLED_BY_POLICY);
        assertThat(listener.mEnabled).isFalse();
    }

    @Test
    public void onHandoffPolicyChanged_notifiesListeners() {
        FakeHandoffEnabledListener listener = new FakeHandoffEnabledListener();
        mHandoffSettingsManager.registerHandoffFeatureStateListener(USER_ID, listener);
        Bundle prevRestrictions = new Bundle();
        prevRestrictions.putBoolean(UserManager.DISALLOW_TASK_CONTINUITY_HANDOFF, false);
        Bundle newRestrictions = new Bundle();
        newRestrictions.putBoolean(UserManager.DISALLOW_TASK_CONTINUITY_HANDOFF, true);
        when(mMockUserManagerInternal.getUserRestriction(
                        USER_ID, UserManager.DISALLOW_TASK_CONTINUITY_HANDOFF))
                .thenReturn(true);
        mHandoffSettingsManager.onUserRestrictionsChanged(
                USER_ID, newRestrictions, prevRestrictions);
        assertThat(listener.mCallCount).isEqualTo(2);
        assertThat(listener.mAvailability)
                .isEqualTo(TaskContinuityManager.HANDOFF_AVAILABILITY_STATUS_DISABLED_BY_POLICY);
        assertThat(listener.mEnabled).isFalse();
    }
}
