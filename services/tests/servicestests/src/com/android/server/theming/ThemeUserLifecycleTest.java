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

package com.android.server.theming;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManagerInternal;
import android.app.KeyguardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.testing.TestableContext;
import android.testing.TestableResources;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.UiModeManagerInternal;
import com.android.server.om.OverlayManagerInternal;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wallpaper.WallpaperManagerInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
@HardwareColors(color = "", options = {"*|TONAL_SPOT|home_wallpaper"})
public class ThemeUserLifecycleTest {

    private static final int USER_ID_1 = 10;
    private static final int USER_ID_2 = 11;
    private static final int PROFILE_ID = 12;

    @Rule
    public final TestableContext mContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getContext(), null);

    @Rule
    public final HardwareColorRule mHardwareColorRule = new HardwareColorRule();

    @Mock
    private ThemeManagerImpl mThemeManagerImpl;
    @Mock
    private WallpaperManagerInternal mWallpaperManagerInternal;
    @Mock
    private UserManagerInternal mUserManagerInternal;
    @Mock
    private UiModeManagerInternal mUiModeManagerInternal;
    @Mock
    private ActivityManagerInternal mActivityManagerInternal;
    @Mock
    private OverlayManagerInternal mOverlayManagerInternal;
    @Mock
    private KeyguardManager mKeyguardManager;

    private ThemeUserLifecycle mThemeUserLifecycle;
    private ContentResolver mContentResolver;
    private ThemeEnvironment mEnvironment;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContentResolver = mContext.getContentResolver();

        TestableResources testableResources = mContext.getOrCreateTestableResources();
        testableResources.addOverride(com.android.internal.R.array.theming_defaults,
                mHardwareColorRule.options);

        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.removeServiceForTest(UiModeManagerInternal.class);
        LocalServices.removeServiceForTest(ActivityManagerInternal.class);
        LocalServices.removeServiceForTest(OverlayManagerInternal.class);
        LocalServices.removeServiceForTest(WallpaperManagerInternal.class);

        LocalServices.addService(UserManagerInternal.class, mUserManagerInternal);
        LocalServices.addService(UiModeManagerInternal.class, mUiModeManagerInternal);
        LocalServices.addService(ActivityManagerInternal.class, mActivityManagerInternal);
        LocalServices.addService(OverlayManagerInternal.class, mOverlayManagerInternal);
        LocalServices.addService(WallpaperManagerInternal.class, mWallpaperManagerInternal);

        mContext.addMockSystemService(Context.KEYGUARD_SERVICE, mKeyguardManager);

        when(mUserManagerInternal.getProfileParentId(anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mUserManagerInternal.getProfileIds(anyInt(), anyBoolean())).thenAnswer(invocation -> {
            int requestedUserId = invocation.getArgument(0);
            return new int[]{requestedUserId};
        });

        mEnvironment = new ThemeEnvironment(mContext, mHardwareColorRule.sysPropReader);

        // Constructor simplified
        mThemeUserLifecycle = new ThemeUserLifecycle(mContext, mEnvironment);
        mThemeUserLifecycle.setDispatcher(mThemeManagerImpl);
        mEnvironment.setBootingComplete(mThemeUserLifecycle);
    }

    @After
    public void tearDown() {
        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.removeServiceForTest(UiModeManagerInternal.class);
        LocalServices.removeServiceForTest(ActivityManagerInternal.class);
        LocalServices.removeServiceForTest(OverlayManagerInternal.class);
        LocalServices.removeServiceForTest(WallpaperManagerInternal.class);
    }

    @Test
    public void onUserStarting_callsImpl() {
        SystemService.TargetUser targetUser = new SystemService.TargetUser(
                new UserInfo(USER_ID_1, "test", 0));

        mThemeUserLifecycle.onUserStarting(targetUser);

        verify(mThemeManagerImpl).onUserStart(USER_ID_1);
    }

    @Test
    public void onUserSwitching_callsImpl() {
        SystemService.TargetUser fromUser = new SystemService.TargetUser(
                new UserInfo(USER_ID_1, "from", 0));
        SystemService.TargetUser toUser = new SystemService.TargetUser(
                new UserInfo(USER_ID_2, "to", 0));

        mThemeUserLifecycle.onUserSwitching(fromUser, toUser);

        verify(mThemeManagerImpl).onUserSwitching(USER_ID_1, USER_ID_2);
    }

    @Test
    public void loadUserStateAndNotifyStateManager_callsImpl() {
        mThemeUserLifecycle.loadUserStateAndNotifyStateManager(USER_ID_1);

        verify(mThemeManagerImpl).onUserStart(USER_ID_1);
    }

    @Test
    public void testProfileAdded_callsImpl() {
        when(mUserManagerInternal.getProfileParentId(PROFILE_ID)).thenReturn(USER_ID_1);

        Intent intent = new Intent(Intent.ACTION_PROFILE_ADDED);
        intent.putExtra(Intent.EXTRA_USER, UserHandle.of(PROFILE_ID));

        mThemeUserLifecycle.mUserLifecycleReceiver.onReceive(mContext, intent);

        verify(mThemeManagerImpl).onProfileAdded(USER_ID_1, PROFILE_ID);
    }
}