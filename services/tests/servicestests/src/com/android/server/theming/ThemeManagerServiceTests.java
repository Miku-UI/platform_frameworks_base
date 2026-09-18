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

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.SuppressLint;
import android.app.ActivityManagerInternal;
import android.content.pm.UserInfo;
import android.content.theming.ThemeStyle;
import android.os.UserHandle;
import android.testing.TestableContext;
import android.testing.TestableResources;

import androidx.test.runner.AndroidJUnit4;

import com.android.internal.R;
import com.android.internal.os.BackgroundThread;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.UiModeManagerInternal;
import com.android.server.om.OverlayManagerInternal;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wallpaper.WallpaperManagerInternal;

import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
@HardwareColors(color = "", options = {"*|TONAL_SPOT|home_wallpaper"})
public class ThemeManagerServiceTests {

    @Rule
    public final HardwareColorRule mHardwareColorRule = new HardwareColorRule();

    private static final int DEFAULT_USER_ID = 11;
    private static final int TEST_USER_ID = 10;
    private static final int DEFAULT_SEED_COLOR = 0xFFFF0000; // RED
    private static final float DEFAULT_CONTRAST = 0.0f;
    private static final int DEFAULT_STYLE = ThemeStyle.TONAL_SPOT;

    @Mock
    private UserManagerInternal mUserManager;
    @Mock
    private OverlayManagerInternal mOverlayManager;

    @Mock
    private WallpaperManagerInternal mWallpaperManager;
    @Mock
    private UiModeManagerInternal mUiModeManagerInternal;
    @Mock
    private ActivityManagerInternal mActivityManagerInternal;
    @Mock
    private Executor mMainExecutor;

    @Mock
    private ThemeUserLifecycle mThemeUserLifecycle;
    @Mock
    private ThemeEventObserver mThemeEventObserver;

    @Rule
    public final TestableContext mMainContext = spy(
            new TestableContext(getInstrumentation().getContext(), null));
    @Rule
    public final TestableContext mUserContext = new TestableContext(
            getInstrumentation().getContext(), null);

    private ThemeManagerService mThemeManagerService;
    private ThemeStateManager mThemeStateManager;
    private ThemeEnvironment mEnvironment;
    private FakeScheduledExecutorService mSchedulerExecutor;

    private final HashMap<Integer, Object> mUserResourceOverrides = new HashMap<>(
            new ImmutableMap.Builder<Integer, Object>()
                    .put(R.color.system_accent1_500_light, 0xFF6476A5)
                    .put(R.color.system_accent2_500_light, 0xFF70778B)
                    .put(R.color.system_accent3_500_light, 0xFF836E99)
                    .put(R.color.system_neutral1_500_light, 0xFF76777C)
                    .put(R.color.system_neutral2_500_light, 0xFF757780)
                    .put(R.color.system_accent1_500_dark, 0xFF69769B)
                    .put(R.color.system_accent2_500_dark, 0xFF70778B)
                    .put(R.color.system_accent3_500_dark, 0xFF836E99)
                    .put(R.color.system_neutral1_500_dark, 0xFF76777C)
                    .put(R.color.system_neutral2_500_dark, 0xFF757780)
                    .put(R.color.system_outline_variant_dark, 0xFF454850)
                    .put(R.color.system_outline_variant_light, 0xFFB0B1BC)
                    .put(R.color.system_primary_container_dark, 0xFF445274)
                    .put(R.color.system_primary_container_light, 0xFFB9CBFF)
                    .build());


    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        LocalServices.removeServiceForTest(OverlayManagerInternal.class);
        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.removeServiceForTest(WallpaperManagerInternal.class);
        LocalServices.removeServiceForTest(UiModeManagerInternal.class);
        LocalServices.removeServiceForTest(ThemeManagerInternal.class);
        LocalServices.removeServiceForTest(ActivityManagerInternal.class);

        LocalServices.addService(OverlayManagerInternal.class, mOverlayManager);
        LocalServices.addService(UserManagerInternal.class, mUserManager);
        LocalServices.addService(WallpaperManagerInternal.class, mWallpaperManager);
        LocalServices.addService(UiModeManagerInternal.class, mUiModeManagerInternal);
        LocalServices.addService(ActivityManagerInternal.class, mActivityManagerInternal);

        // create main testable resources and apply overrides
        TestableResources mainResources = mMainContext.getOrCreateTestableResources();
        mainResources.addOverride(R.array.theming_defaults, mHardwareColorRule.options);
        // We're just providing an empty array for this test to avoid ResourceNotFoundException
        mainResources.addOverride(R.array.theming_legacy_overlays, new String[0]);

        // create user testable resources and apply overrides
        TestableResources userResources = mUserContext.getOrCreateTestableResources();
        mUserResourceOverrides.forEach(userResources::addOverride);
        userResources.addOverride(R.array.theming_defaults, mHardwareColorRule.options);

        doReturn(mUserContext).when(mMainContext).createContextAsUser(any(UserHandle.class),
                anyInt());
        doReturn(mMainExecutor).when(mMainContext).getMainExecutor();

        when(mUserManager.getProfileParentId(anyInt())).thenAnswer(inv -> inv.getArgument(0));
        when(mUserManager.getUserIds()).thenReturn(new int[]{DEFAULT_USER_ID});
        when(mUserManager.isHeadlessSystemUserMode()).thenReturn(false);

        mEnvironment = new ThemeEnvironment(mMainContext, mHardwareColorRule.sysPropReader);

        mSchedulerExecutor = new FakeScheduledExecutorService();
        mThemeStateManager = new ThemeStateManager(mMainContext, mSchedulerExecutor, mEnvironment);
        mThemeStateManager.onServicesReady();
    }

    @Test
    @HardwareColors(color = "", options = {"*|TONAL_SPOT|#00FF00"})
    @SuppressLint("MissingPermission")
    public void test_initialization_colorSchemeNotApplied_shouldForceUpdate() {
        mThemeManagerService = testableServiceStart();

        // creates user with seed color red, not the same as the default google_blue
        ThemeStatePair pair = startProvisionedUser();
        long originalTime = pair.getCurrentState().timeStamp();

        // Simulate boot phases.
        mThemeManagerService.onBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY);

        // Simulate boot animation dismissal using the internal local service
        ThemeManagerImpl internal = (ThemeManagerImpl) LocalServices.getService(
                ThemeManagerInternal.class);
        internal.initializeThemingSystem();

        // Manual fix: Simulate side effect of Lifecycle initialization (Mock doesn't do this)
        mEnvironment.setBootingComplete(mThemeUserLifecycle);

        // Ensure asynchronous initialization completes
        waitForBackgroundThread();
        mSchedulerExecutor.fastForwardTime(ThemeStateManager.DEBOUNCE_MS + 100L);

        assertThat(pair.getPendingState()).isNull(); // there is an update
        assertThat(pair.getCurrentState().timeStamp()).isNotEqualTo(originalTime);

        verify(mThemeEventObserver).onServicesReady(any());
        // Since initializeThemingSystem might be called twice (once by observer fallback,
        // once manually above), we verify it was called at least once.
        verify(mThemeUserLifecycle, atLeastOnce()).loadCurrentUser();
        verify(mThemeEventObserver, atLeastOnce()).registerListeners();
    }

    @Test
    @SuppressLint("MissingPermission")
    public void test_onUserStarting_delegatesToLifecycle_afterInitialization() {
        mThemeManagerService = testableServiceStart();
        mThemeManagerService.onBootPhase(SystemService.PHASE_SYSTEM_SERVICES_READY);

        // Dismiss boot animation to initialize
        ThemeManagerImpl internal = (ThemeManagerImpl) LocalServices.getService(
                ThemeManagerInternal.class);
        internal.initializeThemingSystem();

        // Manual fix: Simulate side effect of Lifecycle initialization (Mock doesn't do this)
        mEnvironment.setBootingComplete(mThemeUserLifecycle);

        // Ensure asynchronous initialization completes
        waitForBackgroundThread();

        SystemService.TargetUser user = new SystemService.TargetUser(
                new UserInfo(TEST_USER_ID, "test", 0));
        mThemeManagerService.onUserStarting(user);

        verify(mThemeUserLifecycle).onUserStarting(user);
    }

    @Test
    public void test_onUserStarting_ignored_beforeInitialization() {
        mThemeManagerService = testableServiceStart();

        SystemService.TargetUser user = new SystemService.TargetUser(
                new UserInfo(TEST_USER_ID, "test", 0));
        mThemeManagerService.onUserStarting(user);

        // Should not delegate yet
        verify(mThemeUserLifecycle, org.mockito.Mockito.never()).onUserStarting(any());
    }

    private void waitForBackgroundThread() {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        BackgroundThread.getHandler().post(() -> future.complete(null));
        try {
            future.get();
        } catch (InterruptedException | ExecutionException ignore) {
        }
    }

    private ThemeStatePair startProvisionedUser() {
        mThemeStateManager.onUserStart(UserHandle.of(DEFAULT_USER_ID), true,
                List.of(DEFAULT_SEED_COLOR), DEFAULT_CONTRAST, DEFAULT_STYLE);
        return mThemeStateManager.getState(DEFAULT_USER_ID);
    }

    private ThemeManagerService testableServiceStart() {
        // The context used here should match the one used for resource overrides.
        ThemeManagerService service = new ThemeManagerService(mMainContext,
                mHardwareColorRule.sysPropReader, mEnvironment, mThemeStateManager,
                mThemeUserLifecycle, mThemeEventObserver);

        LocalServices.addService(ThemeManagerInternal.class, service.getLocalService());

        return service;
    }
}
