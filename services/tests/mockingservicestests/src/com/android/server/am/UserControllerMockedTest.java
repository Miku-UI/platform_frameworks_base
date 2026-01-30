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

package com.android.server.am;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.pm.UserManagerInternal;
import com.android.server.pm.UserManagerService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

public final class UserControllerMockedTest {
    private static final int TEST_USER_1 = 11;
    private static final int TEST_USER_2 = 12;

    @Mock private UserManagerInternal mUserManagerInternal;
    @Mock private UserManagerService mUserManagerService;
    @Mock private Handler mHandler;
    @Mock private Handler mUiHandler;
    @Mock private ActivityManagerService mActivityManagerService;
    @Mock private Context mContext;

    private UserController mUserController;
    private UserController mSpiedUserController;
    private UserController.Injector mUserControllerInjector;
    private UserController.Injector mSpiedUserControllerInjector;

    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).build();

    @Before
    public void setUp() throws Exception {
        mUserControllerInjector = new UserController.Injector(mActivityManagerService);
        mSpiedUserControllerInjector = spy(mUserControllerInjector);

        doReturn(mUserManagerService).when(mSpiedUserControllerInjector).getUserManager();
        doReturn(mUserManagerInternal).when(mSpiedUserControllerInjector).getUserManagerInternal();
        doReturn(mHandler).when(mSpiedUserControllerInjector).getHandler(any());
        doReturn(mUiHandler).when(mSpiedUserControllerInjector).getUiHandler(any());
        when(mSpiedUserControllerInjector.getContext()).thenReturn(mContext);

        mUserController = new UserController(mSpiedUserControllerInjector);
        mSpiedUserController = spy(mUserController);
        doReturn(true).when(mSpiedUserController).switchUser(anyInt());
    }

    @Test
    public void testLogoutUser_HsumAndInteractiveUser0_CanLogoutCurrentUser() {
        mockSystemUserHeadlessMode(true);
        mockCanSwitchToHeadlessSystemUser(true);
        mockCurrentUser(TEST_USER_1);

        boolean result = mSpiedUserController.logoutUser(TEST_USER_1);

        assertThat(result).isTrue();
        verify(mSpiedUserController).switchUser(UserHandle.USER_SYSTEM);
        verify(mSpiedUserController).stopUser(TEST_USER_1, false, null, null);
    }

    @Test
    public void testLogoutUser_NonHsum_CanLogoutCurrentUser() {
        mockSystemUserHeadlessMode(false);
        mockCurrentUser(TEST_USER_1);

        boolean result = mSpiedUserController.logoutUser(TEST_USER_1);

        assertThat(result).isTrue();
        verify(mSpiedUserController).switchUser(UserHandle.USER_SYSTEM);
        verify(mSpiedUserController).stopUser(TEST_USER_1, false, null, null);
    }

    @Test
    public void testLogoutUser_LogoutNonCurrentUser_HsumAndInteractiveUser0_NoSwitchUser() {
        mockSystemUserHeadlessMode(true);
        mockCanSwitchToHeadlessSystemUser(true);
        mockCurrentUser(TEST_USER_1);

        boolean result = mSpiedUserController.logoutUser(TEST_USER_2);

        assertThat(result).isTrue();
        // Logout of non-current user does not need switch user, but only stop user.
        verify(mSpiedUserController, never()).switchUser(anyInt());
        verify(mSpiedUserController).stopUser(TEST_USER_2, false, null, null);
    }

    @Test
    public void testLogoutUser_LogoutNonCurrentUser_NonHsum_NoSwitchUser() {
        mockSystemUserHeadlessMode(false);
        mockCurrentUser(TEST_USER_1);

        boolean result = mSpiedUserController.logoutUser(TEST_USER_2);

        assertThat(result).isTrue();
        // Logout of non-current user does not need switch user, but only stop user.
        verify(mSpiedUserController, never()).switchUser(anyInt());
        verify(mSpiedUserController).stopUser(TEST_USER_2, false, null, null);
    }

    @Test
    public void testLogoutUser_CannotLogoutSystemUser_HsumAndInteractiveUser0() {
        mockSystemUserHeadlessMode(true);
        mockCanSwitchToHeadlessSystemUser(true);
        mockCurrentUser(UserHandle.USER_SYSTEM);

        boolean result = mSpiedUserController.logoutUser(UserHandle.USER_SYSTEM);

        assertThat(result).isFalse();
        // No switch user should have happened.
        verify(mSpiedUserController, never()).switchUser(anyInt());
        verify(mSpiedUserController, never()).stopUser(anyInt(), anyBoolean(), any(), any());
    }

    @Test
    public void testLogoutUser_CannotLogoutSystemUser_NonHsum() {
        mockSystemUserHeadlessMode(false);
        mockCurrentUser(UserHandle.USER_SYSTEM);

        boolean result = mSpiedUserController.logoutUser(UserHandle.USER_SYSTEM);

        assertThat(result).isFalse();
        // No switch user should have happened.
        verify(mSpiedUserController, never()).switchUser(anyInt());
        verify(mSpiedUserController, never()).stopUser(anyInt(), anyBoolean(), any(), any());
    }

    private void mockCurrentUser(@UserIdInt int userId) {
        when(mSpiedUserController.getCurrentUserId()).thenReturn(userId);
    }

    private void mockCanSwitchToHeadlessSystemUser(boolean canSwitch) {
        doReturn(canSwitch).when(mUserManagerService).canSwitchToHeadlessSystemUser();
    }

    private void mockSystemUserHeadlessMode(boolean headless) {
        when(mSpiedUserControllerInjector.isHeadlessSystemUserMode()).thenReturn(headless);
        UserInfo sysInfo = new UserInfo(UserHandle.USER_SYSTEM,
                "User" + UserHandle.USER_SYSTEM, /* iconPath= */ null,
                headless ? UserInfo.FLAG_SYSTEM : UserInfo.FLAG_FULL | UserInfo.FLAG_SYSTEM,
                headless
                    ? UserManager.USER_TYPE_SYSTEM_HEADLESS : UserManager.USER_TYPE_FULL_SYSTEM);
        when(mUserManagerService.getUserInfo(eq(UserHandle.USER_SYSTEM))).thenReturn(sysInfo);
    }
}
