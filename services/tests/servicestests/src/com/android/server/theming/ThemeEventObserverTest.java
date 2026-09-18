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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManagerInternal;
import android.app.KeyguardManager;
import android.app.WallpaperColors;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.testing.TestableContext;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.LocalServices;
import com.android.server.UiModeManagerInternal;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wallpaper.WallpaperManagerInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class ThemeEventObserverTest {

    private static final int USER_ID = 10;

    @Rule
    public final TestableContext mContext = new TestableContext(
            InstrumentationRegistry.getInstrumentation().getContext(), null);

    @Mock
    private ThemeStateManager mThemeStateManager;
            // Not used by Observer anymore, but maybe for setup? No.
    @Mock
    private ThemeSettingsManager mThemeSettingsManager;
    @Mock
    private ThemeUserLifecycle mThemeUserLifecycle;
    @Mock
    private ThemeWallpaperManager mThemeWallpaperManager;
    @Mock
    private UiModeManagerInternal mUiModeManagerInternal;
    @Mock
    private KeyguardManager mKeyguardManager;
    @Mock
    private UserManagerInternal mUserManagerInternal;
    @Mock
    private ActivityManagerInternal mActivityManagerInternal;

    @Mock
    private ThemeManagerImpl mThemeManagerImpl;

    @Captor
    private ArgumentCaptor<UiModeManagerInternal.ContrastListenerInternal> mContrastListenerCaptor;
    @Captor
    private ArgumentCaptor<KeyguardManager.KeyguardLockedStateListener> mKeyguardListenerCaptor;

    private ThemeEventObserver mThemeEventObserver;
    private WallpaperManagerInternal.ColorsChangedCallbackInternal mWallpaperListener;
    private ThemeEnvironment mEnvironment;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        LocalServices.removeServiceForTest(UiModeManagerInternal.class);
        LocalServices.addService(UiModeManagerInternal.class, mUiModeManagerInternal);
        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.addService(UserManagerInternal.class, mUserManagerInternal);

        mContext.addMockSystemService(Context.KEYGUARD_SERVICE, mKeyguardManager);

        org.mockito.Mockito.doAnswer(invocation -> {
            mWallpaperListener = invocation.getArgument(0);
            return null;
        }).when(mThemeWallpaperManager).addOnColorsChangedListener(any(), any());

        when(mUserManagerInternal.getProfileParentId(anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mEnvironment = new ThemeEnvironment(mContext, (key, def) -> def);
        mEnvironment.setBootingComplete(mThemeUserLifecycle);

        mThemeEventObserver = new ThemeEventObserver(mContext, mEnvironment);
        mThemeEventObserver.setDispatcher(mThemeManagerImpl);
        mThemeEventObserver.onServicesReady(mThemeWallpaperManager);
        mThemeEventObserver.registerListeners();

        // Ground truth behavior: assume user is parent (not profile) and can be loaded
        when(mThemeUserLifecycle.loadUserStateAndNotifyStateManager(USER_ID)).thenReturn(true);
    }

    @After
    public void tearDown() {
        LocalServices.removeServiceForTest(UiModeManagerInternal.class);
        LocalServices.removeServiceForTest(UserManagerInternal.class);
    }

    @Test
    public void testOverlayChanged_androidPackage_notifiesThemeChanged() {
        Intent intent = new Intent(Intent.ACTION_OVERLAY_CHANGED);
        intent.setData(Uri.parse("package:android"));
        intent.putExtra(Intent.EXTRA_USER_ID, USER_ID);

        mThemeEventObserver.mOverlayReceiver.onReceive(mContext, intent);

        verify(mThemeManagerImpl).notifyThemeChanged(USER_ID);
    }

    @Test
    public void testOverlayChanged_otherPackage_ignored() {
        Intent intent = new Intent(Intent.ACTION_OVERLAY_CHANGED);
        intent.setData(Uri.parse("package:com.other.package"));
        intent.putExtra(Intent.EXTRA_USER_ID, USER_ID);

        mThemeEventObserver.mOverlayReceiver.onReceive(mContext, intent);

        verify(mThemeManagerImpl, never()).notifyThemeChanged(anyInt());
    }

    @Test
    public void testWallpaperColorsChanged_callsImpl() {
        // Logic moved to Impl, Observer just forwards
        WallpaperColors colors = new WallpaperColors(Color.valueOf(Color.RED), null, null);
        mWallpaperListener.onColorsChanged(colors, 0, 0, USER_ID, true);

        verify(mThemeManagerImpl).onWallpaperColorsChanged(eq(USER_ID), eq(colors), anyBoolean());
    }

    @Test
    public void testContrastChanged_callsImpl() {
        verify(mUiModeManagerInternal).addContrastListener(mContrastListenerCaptor.capture(),
                any(Executor.class));

        mContrastListenerCaptor.getValue().onContrastChange(USER_ID, 0.5f);

        verify(mThemeManagerImpl).onContrastChanged(USER_ID, 0.5f);
    }

    @Test
    public void testKeyguardLockedStateChanged_callsImpl() {
        verify(mKeyguardManager).addKeyguardLockedStateListener(any(Executor.class),
                mKeyguardListenerCaptor.capture());

        mKeyguardListenerCaptor.getValue().onKeyguardLockedStateChanged(true);

        verify(mThemeManagerImpl).onDeviceLocked();
    }

    @Test
    public void testUserSetupComplete_callsImpl() {
        mThemeEventObserver.mUserSetupObserver.onChange(false, null, 0, USER_ID);

        verify(mThemeManagerImpl).onUserStart(USER_ID);
    }

    @Test
    public void testThemeCustomizationChanged_callsImpl() {
        mThemeEventObserver.handleThemeCustomizationChanged(USER_ID);

        verify(mThemeManagerImpl).onThemeSettingsChanged(USER_ID);
    }
}
