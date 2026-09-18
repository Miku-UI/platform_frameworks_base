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

package com.android.server.theming;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.app.ActivityManagerInternal;
import android.app.KeyguardManager;
import android.app.WallpaperColors;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.testing.TestableContext;
import android.testing.TestableResources;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.LocalServices;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wallpaper.WallpaperManagerInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

@RunWith(AndroidJUnit4.class)
public class ThemeInitializationObserverTest {

    private static final int USER_ID = 10;

    private Context mContext;
    private ThemeManagerImpl mMockImpl;
    private ThemeWallpaperManager mMockWallpaperManager;
    private UserManagerInternal mMockUserManager;
    private ActivityManagerInternal mMockActivityManager;
    private KeyguardManager mMockKeyguardManager;

    private ThemeEnvironment mEnvironment;
    private ThemeInitializationObserver mEvents;
    private WallpaperManagerInternal.ColorsChangedCallbackInternal mWallpaperListener;

    @Before
    public void setup() {
        mMockImpl = mock(ThemeManagerImpl.class);
        mMockWallpaperManager = mock(ThemeWallpaperManager.class);
        mMockUserManager = mock(UserManagerInternal.class);
        mMockActivityManager = mock(ActivityManagerInternal.class);
        mMockKeyguardManager = mock(KeyguardManager.class);

        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.addService(UserManagerInternal.class, mMockUserManager);
        LocalServices.removeServiceForTest(ActivityManagerInternal.class);
        LocalServices.addService(ActivityManagerInternal.class, mMockActivityManager);

        TestableContext context = new TestableContext(InstrumentationRegistry.getTargetContext());
        mContext = spy(context);

        // Provide empty resources to avoid NPE in ThemeEnvironment$Config
        TestableResources resources = context.getOrCreateTestableResources();
        resources.addOverride(com.android.internal.R.array.theming_legacy_overlays, new String[0]);
        resources.addOverride(com.android.internal.R.array.theming_defaults, new String[0]);

        // Stub registerReceiver to return a dummy Intent
        doReturn(new Intent()).when(mContext).registerReceiver(any(), any(), any(), any());

        // Default behavior: tests assume we need wallpaper colors
        doReturn(true).when(mMockImpl).requiresWallpaperForInitialization(anyInt());

        mEnvironment = new ThemeEnvironment(mContext, (key, def) -> def);
        mEnvironment.onServicesReady(mMockKeyguardManager);

        doReturn(USER_ID).when(mMockActivityManager).getCurrentUserId();

        mEvents = new ThemeInitializationObserver(mContext, mMockImpl, mMockWallpaperManager,
                mEnvironment);

        doAnswer(invocation -> {
            mWallpaperListener = invocation.getArgument(0);
            return null;
        }).when(mMockWallpaperManager).addOnColorsChangedListener(any(), any());
    }

    @After
    public void tearDown() {
        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.removeServiceForTest(ActivityManagerInternal.class);
    }

    @Test
    public void testRegisterListeners_registersBoth() {
        doReturn(true).when(mMockWallpaperManager).isWallpaperManagerAvailable();
        mEvents.registerListeners();
        verify(mMockWallpaperManager).addOnColorsChangedListener(any(), any());
        verify(mContext).registerReceiver(any(), any(), any(), any());
    }

    @Test
    public void testRegisterListeners_noWallpaperService_triggersInit() {
        doReturn(false).when(mMockWallpaperManager).isWallpaperManagerAvailable();
        mEvents.registerListeners();

        verify(mMockImpl).initializeThemingSystem();
    }

    @Test
    public void testWallpaperChanged_triggersInit() {
        doReturn(true).when(mMockWallpaperManager).isWallpaperManagerAvailable();
        doReturn(true).when(mMockImpl).initializeThemingSystem();
        mEvents.registerListeners();

        WallpaperColors colors = new WallpaperColors(Color.valueOf(Color.RED), null, null);
        mWallpaperListener.onColorsChanged(colors, 0, android.view.Display.DEFAULT_DISPLAY, USER_ID,
                true);

        verify(mMockImpl).initializeThemingSystem();
    }

    @Test
    public void testWallpaperChanged_notBooting_ignored() {
        doReturn(true).when(mMockWallpaperManager).isWallpaperManagerAvailable();
        mEnvironment.setBootingComplete(null);
        mEvents.registerListeners();

        WallpaperColors colors = new WallpaperColors(Color.valueOf(Color.RED), null, null);
        mWallpaperListener.onColorsChanged(colors, 0, android.view.Display.DEFAULT_DISPLAY, USER_ID,
                true);

        verify(mMockImpl, never()).initializeThemingSystem();
    }

    @Test
    public void testWallpaperChanged_noUpdateNeeded_signalsReadyAndFinishes() {
        doReturn(true).when(mMockWallpaperManager).isWallpaperManagerAvailable();
        doReturn(false).when(mMockImpl).initializeThemingSystem();
        mEvents.registerListeners();

        WallpaperColors colors = new WallpaperColors(Color.valueOf(Color.RED), null, null);
        mWallpaperListener.onColorsChanged(colors, 0, android.view.Display.DEFAULT_DISPLAY, USER_ID,
                true);

        verify(mMockImpl).onThemingSystemReady();
        verify(mMockWallpaperManager).removeOnColorsChangedListener(any());
        verify(mContext).unregisterReceiver(any());
    }

    @Test
    public void testOverlayChanged_correctUser_signalsReadyAndFinishes() {
        doReturn(true).when(mMockWallpaperManager).isWallpaperManagerAvailable();
        doReturn(true).when(mMockImpl).initializeThemingSystem();
        doReturn(USER_ID).when(mMockActivityManager).getCurrentUserId();

        mEvents.registerListeners();

        // 1. Trigger wallpaper change to enable overlay processing
        WallpaperColors colors = new WallpaperColors(Color.valueOf(Color.RED), null, null);
        mWallpaperListener.onColorsChanged(colors, 0, android.view.Display.DEFAULT_DISPLAY, USER_ID,
                true);

        ArgumentCaptor<BroadcastReceiver> receiverCaptor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiverCaptor.capture(), any(), any(), any());

        // 2. Simulate overlay changed broadcast
        Intent intent = new Intent(Intent.ACTION_OVERLAY_CHANGED);
        intent.setData(Uri.parse("package:android"));
        intent.putExtra(Intent.EXTRA_USER_ID, USER_ID);

        doReturn(true).when(mMockImpl).checkAndSignalReady(USER_ID);

        receiverCaptor.getValue().onReceive(mContext, intent);

        // Called once during wallpaper change (init) and once during overlay change
        verify(mMockImpl, org.mockito.Mockito.times(2)).checkAndSignalReady(USER_ID);
        verify(mContext, atLeastOnce()).unregisterReceiver(any());
    }

    @Test
    public void testOverlayChanged_wrongUser_ignored() {
        doReturn(true).when(mMockWallpaperManager).isWallpaperManagerAvailable();
        doReturn(true).when(mMockImpl).initializeThemingSystem();
        doReturn(USER_ID).when(mMockActivityManager).getCurrentUserId();
        mEvents.registerListeners();

        // 1. Trigger wallpaper change
        WallpaperColors colors = new WallpaperColors(Color.valueOf(Color.RED), null, null);
        mWallpaperListener.onColorsChanged(colors, 0, android.view.Display.DEFAULT_DISPLAY, USER_ID,
                true);

        ArgumentCaptor<BroadcastReceiver> receiverCaptor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiverCaptor.capture(), any(), any(), any());

        // 2. Simulate overlay changed broadcast for different user
        Intent intent = new Intent(Intent.ACTION_OVERLAY_CHANGED);
        intent.setData(Uri.parse("package:android"));
        intent.putExtra(Intent.EXTRA_USER_ID, USER_ID + 1);

        receiverCaptor.getValue().onReceive(mContext, intent);

        // Called once during wallpaper change, but NOT during overlay change (wrong user)
        verify(mMockImpl, org.mockito.Mockito.times(1)).checkAndSignalReady(anyInt());
    }

    @Test
    public void testWallpaperChanged_multipleEvents_triggersOnce() {
        doReturn(true).when(mMockWallpaperManager).isWallpaperManagerAvailable();
        doReturn(true).when(mMockImpl).initializeThemingSystem();
        mEvents.registerListeners();

        WallpaperColors colors = new WallpaperColors(Color.valueOf(Color.RED), null, null);

        // First event
        mWallpaperListener.onColorsChanged(colors, 0, android.view.Display.DEFAULT_DISPLAY, USER_ID,
                true);
        // Second event
        mWallpaperListener.onColorsChanged(colors, 0, android.view.Display.DEFAULT_DISPLAY, USER_ID,
                true);

        // Should only call initialization once
        verify(mMockImpl).initializeThemingSystem();
    }

    @Test
    public void testFinish_removesListeners() {
        doReturn(true).when(mMockWallpaperManager).isWallpaperManagerAvailable();
        doReturn(false).when(
                mMockImpl).initializeThemingSystem(); // Triggers signal ready and finish
        mEvents.registerListeners();

        WallpaperColors colors = new WallpaperColors(Color.valueOf(Color.RED), null, null);
        mWallpaperListener.onColorsChanged(colors, 0, android.view.Display.DEFAULT_DISPLAY, USER_ID,
                true);

        verify(mMockWallpaperManager).removeOnColorsChangedListener(any());
        verify(mContext, atLeastOnce()).unregisterReceiver(any());
    }
}
