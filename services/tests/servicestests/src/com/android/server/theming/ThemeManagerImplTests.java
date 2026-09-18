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

import static android.content.theming.FieldColorSource.VALUE_PRESET;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManagerInternal;
import android.app.KeyguardManager;
import android.app.WallpaperColors;
import android.content.Context;
import android.content.om.FabricatedOverlay;
import android.content.theming.IThemeChangedCallback;
import android.content.theming.IThemeSettingsCallback;
import android.content.theming.ThemeInfo;
import android.content.theming.ThemeSettings;
import android.content.theming.ThemeStyle;
import android.graphics.Color;
import android.os.FabricatedOverlayInternal;
import android.os.UserHandle;
import android.provider.Settings;
import android.testing.TestableContext;
import android.testing.TestableResources;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.R;
import com.android.server.LocalServices;
import com.android.server.om.OverlayManagerInternal;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wallpaper.WallpaperManagerInternal;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
@HardwareColors(color = "", options = {"*|TONAL_SPOT|#00FF00"})
public class ThemeManagerImplTests {
    @Rule
    public final HardwareColorRule mHardwareColorRule = new HardwareColorRule();

    private final IThemeSettingsCallback mSettingsCallback = new IThemeSettingsCallback.Stub() {
        @Override
        public void onSettingsChanged(ThemeSettings oldSettings, ThemeSettings newSettings) {
        }
    };

    private final IThemeChangedCallback mThemeChangedCallback = new IThemeChangedCallback.Stub() {
        @Override
        public void onThemeChanged(ThemeInfo newTheme) {
        }
    };

    private final int mUserId = 0;
    private ThemeManagerImpl mUnderTest;
    private Context mContext; // Spy
    private TestableResources mTestableResources;
    @Mock
    private ThemeSettingsManager mThemeSettingsManager;
    private ThemeEnvironment mEnvironment;

    private static final int TEST_SEED_COLOR = Color.BLUE;
    private static final float TEST_CONTRAST = 0.5f;
    private static final int TEST_STYLE = ThemeStyle.VIBRANT;

    @Mock
    private UserManagerInternal mUserManager;
    @Mock
    private OverlayManagerInternal mOverlayManager;
    @Mock
    private WallpaperManagerInternal mWallpaperManagerInternal;
    @Mock
    private ActivityManagerInternal mActivityManagerInternal;
    @Mock
    private ThemeOverlayHelper mOverlayHelper;
    @Mock
    private ThemeUserLifecycle mUserLifecycle;
    @Mock
    private ThemeEventObserver mEventObserver;
    @Mock
    private KeyguardManager mKeyguardManager;
    @Mock
    private FabricatedOverlay mMockFabricatedOverlay;

    private FakeScheduledExecutorService mSchedulerExecutor;
    private ThemeStateManager mStateManager;
    private Map<Integer, ThemeSettings> mFakeSettingsMap;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        LocalServices.removeServiceForTest(OverlayManagerInternal.class);
        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.removeServiceForTest(WallpaperManagerInternal.class);
        LocalServices.removeServiceForTest(ActivityManagerInternal.class);

        LocalServices.addService(OverlayManagerInternal.class, mOverlayManager);
        LocalServices.addService(UserManagerInternal.class, mUserManager);
        LocalServices.addService(WallpaperManagerInternal.class, mWallpaperManagerInternal);
        LocalServices.addService(ActivityManagerInternal.class, mActivityManagerInternal);

        TestableContext testableContext = new TestableContext(
                InstrumentationRegistry.getTargetContext(), null);
        mTestableResources = testableContext.getOrCreateTestableResources();
        mTestableResources.addOverride(R.array.theming_defaults, mHardwareColorRule.options);

        testableContext.addMockSystemService(KeyguardManager.class, mKeyguardManager);

        // Create a spy of the context to handle createContextAsUser for non-existent users
        mContext = spy(testableContext);
        doReturn(mContext).when(mContext).createContextAsUser(any(), anyInt());

        // Default device state to LOCKED to allow background updates to proceed immediately
        // (ThemeStateManager logic: if !foreground && !locked -> defer. So we want locked=true).
        when(mKeyguardManager.isDeviceLocked()).thenReturn(true);

        Settings.Secure.putStringForUser(mContext.getContentResolver(),
                Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, null, mUserId);

        when(mUserManager.getProfileParentId(anyInt())).thenAnswer(
                invocation -> invocation.getArgument(0));
        when(mUserManager.getProfileIds(anyInt(), anyBoolean())).thenAnswer(invocation -> {
            int requestedUserId = invocation.getArgument(0);
            return new int[]{requestedUserId};
        });
        when(mUserLifecycle.loadUserStateAndNotifyStateManager(anyInt())).thenReturn(true);

        // Fake ThemeSettingsManager behavior
        mFakeSettingsMap = new HashMap<>();
        when(mThemeSettingsManager.createDefaultThemeSettings(anyInt())).thenAnswer(invocation -> {
            int userId = invocation.getArgument(0);
            return new ThemeSettings.Builder()
                    .setColorSource(VALUE_PRESET)
                    .setThemeStyle(TEST_STYLE)
                    .setSeedColors(Color.valueOf(TEST_SEED_COLOR))
                    .build();
        });
        when(mThemeSettingsManager.getSettings(anyInt(), any())).thenAnswer(invocation -> {
            int userId = invocation.getArgument(0);
            return mFakeSettingsMap.get(userId);
        });
        when(mThemeSettingsManager.setSettings(anyInt(), any(), any())).thenAnswer(invocation -> {
            int userId = invocation.getArgument(0);
            ThemeSettings settings = invocation.getArgument(2);
            mFakeSettingsMap.put(userId, settings);
            return true;
        });
        when(mThemeSettingsManager.getSettingsOrDefault(anyInt(), any())).thenAnswer(invocation -> {
            int userId = invocation.getArgument(0);
            ThemeSettings s = mFakeSettingsMap.get(userId);
            return s != null ? s : mThemeSettingsManager.createDefaultThemeSettings(userId);
        });

        setupUnderTest();
    }

    private void setupUnderTest() {
        mEnvironment = new ThemeEnvironment(mContext, mHardwareColorRule.sysPropReader);
        mEnvironment.setBootingComplete(mUserLifecycle);
        mEnvironment.onServicesReady(mKeyguardManager);

        ThemeWallpaperManager themeWallpaperManager = new ThemeWallpaperManager();
        mSchedulerExecutor = new FakeScheduledExecutorService();

        mStateManager = spy(new ThemeStateManager(mContext, mSchedulerExecutor, mEnvironment));
        mUnderTest = new ThemeManagerImpl(mContext, mThemeSettingsManager,
                mStateManager, mOverlayHelper, mEnvironment, themeWallpaperManager,
                mHardwareColorRule.sysPropReader, mUserLifecycle, mEventObserver);

        mStateManager.onServicesReady();
        startUser(mUserId, true, List.of(TEST_SEED_COLOR), TEST_CONTRAST, TEST_STYLE);
    }

    private void startUser(int userId, boolean isSetup, List<Integer> seedColors, float contrast,
            int style) {
        mStateManager.onUserStart(UserHandle.of(userId), isSetup, seedColors, contrast, style);
        mSchedulerExecutor.fastForwardTime(ThemeStateManager.DEBOUNCE_MS + 100L);
    }

    @Test
    public void testRegisterThemeSettingsCallback_success() {
        boolean result = mUnderTest.registerThemeSettingsCallback(mUserId, mSettingsCallback);
        assertThat(result).isTrue();
    }

    @Test
    public void testRegisterThemeSettingsCallback_nullCallback_returnsFalse() {
        boolean result = mUnderTest.registerThemeSettingsCallback(mUserId, null);
        assertThat(result).isFalse();
    }

    @Test
    public void testUnregisterThemeSettingsCallback_success() {
        boolean didRegister = mUnderTest.registerThemeSettingsCallback(mUserId, mSettingsCallback);
        assertThat(didRegister).isTrue();
        boolean result = mUnderTest.unregisterThemeSettingsCallback(mUserId, mSettingsCallback);
        assertThat(result).isTrue();
    }

    @Test
    public void testUnregisterThemeSettingsCallback_callbackNotRegistered_returnsFalse() {
        boolean result = mUnderTest.unregisterThemeSettingsCallback(mUserId, mSettingsCallback);
        assertThat(result).isFalse();
    }

    @Test
    public void testUnregisterThemeSettingsCallback_nullCallback_returnsFalse() {
        boolean result = mUnderTest.unregisterThemeSettingsCallback(mUserId, null);
        assertThat(result).isFalse();
    }

    @Test
    public void testSettingsCallback_receivesNewValue() {
        final Color testColor = Color.valueOf(Color.parseColor("#FF0000"));
        final ThemeSettings newPayload = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.VIBRANT)
                .setColorSource(VALUE_PRESET)
                .setSeedColors(testColor)
                .build();
        final ThemeSettings[] returnedOldSettings = {null};
        final ThemeSettings[] returnedNewSettings = {null};

        boolean didRegister = mUnderTest.registerThemeSettingsCallback(mUserId,
                new IThemeSettingsCallback.Stub() {
                    @Override
                    public void onSettingsChanged(ThemeSettings oldSettings,
                            ThemeSettings newSettings) {
                        returnedOldSettings[0] = oldSettings;
                        returnedNewSettings[0] = newSettings;
                    }
                });

        assertThat(didRegister).isTrue();
        // When no theme is set, oldSettings should be null.
        mUnderTest.notifySettingsChange(mUserId, null, newPayload);
        assertThat(returnedOldSettings[0]).isNull();
        assertThat(returnedNewSettings[0]).isEqualTo(newPayload);
    }

    @Test
    public void testSettingsCallback_receivesOldAndNewValue() {
        final Color oldColor = Color.valueOf(Color.BLUE);
        final ThemeSettings oldPayload = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.TONAL_SPOT)
                .setColorSource(VALUE_PRESET)
                .setSeedColors(oldColor)
                .build();

        final Color newColor = Color.valueOf(Color.RED);
        final ThemeSettings newPayload = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.VIBRANT)
                .setColorSource(VALUE_PRESET)
                .setSeedColors(newColor)
                .build();

        final ThemeSettings[] returnedOldSettings = {null};
        final ThemeSettings[] returnedNewSettings = {null};

        // Set an initial theme setting.
        mUnderTest.updateThemeSettings(mUserId, oldPayload);

        boolean didRegister = mUnderTest.registerThemeSettingsCallback(mUserId,
                new IThemeSettingsCallback.Stub() {
                    @Override
                    public void onSettingsChanged(ThemeSettings oldSettings,
                            ThemeSettings newSettings) {
                        returnedOldSettings[0] = oldSettings;
                        returnedNewSettings[0] = newSettings;
                    }
                });
        assertThat(didRegister).isTrue();

        // Notify with the new settings.
        mUnderTest.notifySettingsChange(mUserId, oldPayload, newPayload);

        // The callback should receive the new settings correctly.
        assertThat(returnedNewSettings[0]).isEqualTo(newPayload);

        // Verify the old payload field-by-field due to timestamp precision loss on save/load.
        ThemeSettings returnedOldPreset = returnedOldSettings[0];
        assertThat(returnedOldPreset).isNotNull();
        assertThat(returnedOldPreset.timeStamp().toEpochMilli()).isEqualTo(
                oldPayload.timeStamp().toEpochMilli());
        assertThat(returnedOldPreset.themeStyle()).isEqualTo(oldPayload.themeStyle());
        assertThat(returnedOldPreset.seedColors()).isEqualTo(oldPayload.seedColors());
    }

    @Test
    public void testUnregisterThemeChangedCallback_success() {
        mUnderTest.registerThemeChangedCallback(mUserId, mThemeChangedCallback);
        mUnderTest.unregisterThemeChangedCallback(mUserId, mThemeChangedCallback);
    }

    @Test
    public void testThemeChangedCallback_receivesNewValue() {
        startUser(mUserId, true, List.of(TEST_SEED_COLOR), TEST_CONTRAST, TEST_STYLE);
        final ThemeInfo[] returnedValue = {null};
        mUnderTest.registerThemeChangedCallback(mUserId, new IThemeChangedCallback.Stub() {
            @Override
            public void onThemeChanged(ThemeInfo newTheme) {
                returnedValue[0] = newTheme;
            }
        });

        mUnderTest.notifyThemeChanged(mUserId);

        assertThat(returnedValue[0]).isNotNull();
        assertThat(returnedValue[0].seedColors.getFirst().toArgb()).isEqualTo(TEST_SEED_COLOR);
        assertThat(returnedValue[0].style).isEqualTo(TEST_STYLE);
        assertThat(returnedValue[0].contrast).isEqualTo(TEST_CONTRAST);
        assertThat(returnedValue[0].specVersion).isEqualTo(
                mEnvironment.getConfig().specVersion().name());
        assertThat(returnedValue[0].platform).isEqualTo(mEnvironment.getConfig().platform().name());
    }

    @Test
    public void getThemeSettings_noSetting_returnsNull() {
        ThemeSettings settings = mUnderTest.getThemeSettings(mUserId);
        assertThat(settings).isNull();
    }

    @Test
    public void getThemeSettings_withSetting_returnsStored() {
        final Color testColor = Color.valueOf(Color.RED);
        final ThemeSettings storedSettings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.VIBRANT)
                .setColorSource(VALUE_PRESET)
                .setSeedColors(testColor)
                .build();
        mUnderTest.updateThemeSettings(mUserId, storedSettings);

        ThemeSettings settings = mUnderTest.getThemeSettings(mUserId);

        // Verify the loaded payload field-by-field due to timestamp precision loss on save/load.
        ThemeSettings returnedPreset = settings;
        assertThat(returnedPreset).isNotNull();
        assertThat(returnedPreset.timeStamp().toEpochMilli()).isEqualTo(
                storedSettings.timeStamp().toEpochMilli());
        assertThat(returnedPreset.themeStyle()).isEqualTo(storedSettings.themeStyle());
        assertThat(returnedPreset.seedColors()).isEqualTo(storedSettings.seedColors());
    }

    @Test
    public void getThemeSettingsOrDefault_noSetting_returnsDefault() {
        Color cyan = Color.valueOf(Color.CYAN);
        WallpaperColors wallpaperColors = new WallpaperColors(cyan, null, null);
        when(mWallpaperManagerInternal.getWallpaperColors(anyInt(), anyInt()))
                .thenReturn(wallpaperColors);

        ThemeSettings expectedDefault = mThemeSettingsManager.createDefaultThemeSettings(mUserId);
        ThemeSettings settings = mUnderTest.getThemeSettingsOrDefault(mUserId);

        assertThat(settings.themeStyle()).isEqualTo(expectedDefault.themeStyle());
        assertThat(settings.colorSource()).isEqualTo(expectedDefault.colorSource());
        assertThat(settings.seedColors()).isEqualTo(expectedDefault.seedColors());
        assertThat(settings.timeStamp().toEpochMilli()).isAtLeast(
                expectedDefault.timeStamp().toEpochMilli());
    }

    @Test
    public void getThemeSettingsOrDefault_withSetting_returnsStored() {
        final Color testColor = Color.valueOf(Color.RED);
        final ThemeSettings storedSettings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.VIBRANT)
                .setColorSource(VALUE_PRESET)
                .setSeedColors(testColor)
                .build();
        mUnderTest.updateThemeSettings(mUserId, storedSettings);

        ThemeSettings settings = mUnderTest.getThemeSettingsOrDefault(mUserId);

        // Verify the loaded payload field-by-field due to timestamp precision loss on save/load.
        ThemeSettings returnedPreset = settings;
        assertThat(returnedPreset).isNotNull();
        assertThat(returnedPreset.timeStamp().toEpochMilli()).isEqualTo(
                storedSettings.timeStamp().toEpochMilli());
        assertThat(returnedPreset.themeStyle()).isEqualTo(storedSettings.themeStyle());
        assertThat(returnedPreset.seedColors()).isEqualTo(storedSettings.seedColors());
    }

    @Test
    public void getUserThemeInfo_returnsCorrectInfo() {
        startUser(mUserId, true, List.of(TEST_SEED_COLOR), TEST_CONTRAST, TEST_STYLE);

        ThemeInfo info = mUnderTest.getUserThemeInfo(mUserId);

        assertThat(info).isNotNull();
        assertThat(info.seedColors.getFirst().toArgb()).isEqualTo(TEST_SEED_COLOR);
        assertThat(info.style).isEqualTo(TEST_STYLE);
        assertThat(info.contrast).isEqualTo(TEST_CONTRAST);
        assertThat(info.specVersion).isEqualTo(mEnvironment.getConfig().specVersion().name());
        assertThat(info.platform).isEqualTo(mEnvironment.getConfig().platform().name());
    }

    @Test
    public void generateDynamicColorOverlay_withAllOptions_isNotNull() {
        startUser(mUserId, true, List.of(Color.RED), 0.0f, ThemeStyle.TONAL_SPOT);
        ThemeInfo options = new ThemeInfo.Builder()
                .setSeedColors(Color.valueOf(Color.GREEN))
                .setStyle(ThemeStyle.VIBRANT)
                .setContrast(0.8f)
                .build();

        // Mock the overlay helper to return a dummy overlay
        FabricatedOverlayInternal mockOverlay = new FabricatedOverlayInternal();
        FabricatedOverlay mockFabricatedOverlay =
                mock(FabricatedOverlay.class);
        when(mockFabricatedOverlay.getInternal()).thenReturn(mockOverlay);
        doReturn(mockFabricatedOverlay).when(mOverlayHelper).createDynamicOverlay(any(), any(),
                anyInt());

        FabricatedOverlayInternal overlay = mUnderTest.generateDynamicColorOverlay(mUserId,
                options);

        assertThat(overlay).isNotNull();
    }

    @Test
    public void generateDynamicColorOverlay_withNullOptions_isNotNull() {
        startUser(mUserId, true, List.of(Color.RED), 0.0f, ThemeStyle.TONAL_SPOT);
        ThemeInfo options = new ThemeInfo.Builder().build();

        // Mock the overlay helper to return a dummy overlay
        FabricatedOverlayInternal mockOverlay = new FabricatedOverlayInternal();
        FabricatedOverlay mockFabricatedOverlay =
                mock(FabricatedOverlay.class);
        when(mockFabricatedOverlay.getInternal()).thenReturn(mockOverlay);
        doReturn(mockFabricatedOverlay).when(mOverlayHelper).createDynamicOverlay(any(), any(),
                anyInt());

        FabricatedOverlayInternal overlay = mUnderTest.generateDynamicColorOverlay(mUserId,
                options);

        assertThat(overlay).isNotNull();
    }

    @Test
    public void generateDynamicColorOverlay_withMixedOptions_isNotNull() {
        startUser(mUserId, true, List.of(Color.RED), 0.0f, ThemeStyle.TONAL_SPOT);
        ThemeInfo options = new ThemeInfo.Builder()
                .setSeedColors(Color.valueOf(Color.GREEN))
                .setContrast(0.8f)
                .build();

        // Mock the overlay helper to return a dummy overlay
        FabricatedOverlayInternal mockOverlay = new FabricatedOverlayInternal();
        FabricatedOverlay mockFabricatedOverlay =
                mock(FabricatedOverlay.class);
        when(mockFabricatedOverlay.getInternal()).thenReturn(mockOverlay);
        doReturn(mockFabricatedOverlay).when(mOverlayHelper).createDynamicOverlay(any(), any(),
                anyInt());

        FabricatedOverlayInternal overlay = mUnderTest.generateDynamicColorOverlay(mUserId,
                options);

        assertThat(overlay).isNotNull();
    }


    @Test
    public void apiCallsDuringBoot_registrationSucceeds_othersFail() {
        // Create a fresh environment that is still booting
        ThemeEnvironment bootingEnv = new ThemeEnvironment(mContext,
                mHardwareColorRule.sysPropReader);

        // Re-instantiate ThemeManagerImpl with the booting environment
        ThemeManagerImpl bootingImpl = new ThemeManagerImpl(mContext, mThemeSettingsManager,
                mStateManager, mOverlayHelper, bootingEnv, new ThemeWallpaperManager(),
                mHardwareColorRule.sysPropReader, mUserLifecycle, mEventObserver);

        assertThat(bootingImpl.getUserThemeInfo(mUserId)).isNull();
        assertThat(bootingImpl.getThemeSettings(mUserId)).isNull();
        assertThat(bootingImpl.getThemeSettingsOrDefault(mUserId)).isNull();
        assertThat(bootingImpl.generateDynamicColorOverlay(mUserId,
                new ThemeInfo.Builder().build())).isNull();

        assertThat(bootingImpl.updateThemeSettings(mUserId, new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.TONAL_SPOT)
                .setColorSource(VALUE_PRESET)
                .setSeedColors(Color.valueOf(Color.BLUE))
                .build())).isFalse();

        // Registration should succeed even during boot
        assertThat(bootingImpl.registerThemeSettingsCallback(mUserId, mSettingsCallback)).isTrue();
        assertThat(
                bootingImpl.unregisterThemeSettingsCallback(mUserId, mSettingsCallback)).isTrue();
        assertThat(
                bootingImpl.registerThemeChangedCallback(mUserId, mThemeChangedCallback)).isTrue();
        assertThat(bootingImpl.unregisterThemeChangedCallback(mUserId,
                mThemeChangedCallback)).isTrue();
    }

    @Test
    public void callbackRegisteredDuringBoot_receivesEventAfterInit() {
        // 1. Setup a booting environment
        ThemeEnvironment bootingEnv = new ThemeEnvironment(mContext,
                mHardwareColorRule.sysPropReader);
        ThemeManagerImpl bootingImpl = new ThemeManagerImpl(mContext, mThemeSettingsManager,
                mStateManager, mOverlayHelper, bootingEnv, new ThemeWallpaperManager(),
                mHardwareColorRule.sysPropReader, mUserLifecycle, mEventObserver);

        // 2. Register callback during boot (should succeed now)
        final ThemeInfo[] returnedValue = {null};
        boolean registered = bootingImpl.registerThemeChangedCallback(mUserId,
                new IThemeChangedCallback.Stub() {
                    @Override
                    public void onThemeChanged(ThemeInfo newTheme) {
                        returnedValue[0] = newTheme;
                    }
                });
        assertThat(registered).isTrue();

        // 3. Initialize system (simulate boot complete)
        bootingImpl.initializeThemingSystem();
        bootingImpl.onThemingSystemReady(); // Manually signal ready for test

        // Manual fix: Trigger the side effect that the real lifecycle would do
        bootingEnv.setBootingComplete(mUserLifecycle);
        // Force state manager to process
        mSchedulerExecutor.fastForwardTime(ThemeStateManager.DEBOUNCE_MS + 100L);

        // 4. Trigger an event
        bootingImpl.notifyThemeChanged(mUserId);

        // 5. Verify callback received
        assertThat(returnedValue[0]).isNotNull();
    }

    @Test
    public void initializeThemingSystem_initializesSystem() {
        // Create a fresh environment that is still booting
        ThemeEnvironment bootingEnv = new ThemeEnvironment(mContext,
                mHardwareColorRule.sysPropReader);
        ThemeManagerImpl bootingImpl = new ThemeManagerImpl(mContext, mThemeSettingsManager,
                mStateManager, mOverlayHelper, bootingEnv, new ThemeWallpaperManager(),
                mHardwareColorRule.sysPropReader, mUserLifecycle, mEventObserver);

        // Act
        boolean result = bootingImpl.initializeThemingSystem();

        // Verify
        assertThat(result).isTrue();
        // Since initialization posts to handler, we can't verify everything synchronously unless
        // we mock handler
        // But we can verify synchronous steps like initializeDefaults
        org.mockito.Mockito.verify(mThemeSettingsManager).initializeDefaults();
    }

    @Test
    public void onWallpaperColorsChanged_presetSource_ignored() {
        int userId = 100;
        startUser(userId, true, List.of(Color.BLUE), 0f, ThemeStyle.TONAL_SPOT);

        // Setup: Preset source
        ThemeSettings settings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.TONAL_SPOT)
                .setColorSource(VALUE_PRESET)
                .setSeedColors(Color.valueOf(Color.BLUE))
                .build();
        mUnderTest.updateThemeSettings(userId, settings);

        // Action: Wallpaper changed
        mUnderTest.onWallpaperColorsChanged(userId,
                new WallpaperColors(Color.valueOf(Color.RED), null, null), false);
        mSchedulerExecutor.fastForwardTime(ThemeStateManager.DEBOUNCE_MS + 100L);

        // Verify: State did NOT update to RED (remains BLUE from preset)
        assertThat(mStateManager.getState(userId).getCurrentState().seedColor()).isEqualTo(
                Color.BLUE);
    }

    @Test
    public void onWallpaperColorsChanged_wallpaperSource_updatesState() {
        int userId = 101;
        startUser(userId, true, List.of(Color.BLUE), 0f, ThemeStyle.TONAL_SPOT);

        // Setup: Wallpaper source
        ThemeSettings settings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.TONAL_SPOT)
                .setColorSource(android.content.theming.FieldColorSource.VALUE_HOME_WALLPAPER)
                .setSeedColors(Color.valueOf(Color.BLUE))
                .build();
        mUnderTest.updateThemeSettings(userId, settings);

        // Verify settings are correctly stored in fake
        assertThat(mThemeSettingsManager.getSettingsOrDefault(userId, null).colorSource())
                .isEqualTo(android.content.theming.FieldColorSource.VALUE_HOME_WALLPAPER);

        // Action: Wallpaper changed to GREEN
        mUnderTest.onWallpaperColorsChanged(userId,
                new WallpaperColors(Color.valueOf(Color.GREEN), null, null), false);
        mSchedulerExecutor.fastForwardTime(ThemeStateManager.DEBOUNCE_MS + 100L);

        // Verify: State UPDATED to GREEN
        assertThat(mStateManager.getState(userId).getCurrentState().seedColor()).isEqualTo(
                Color.GREEN);

        // Verify: Settings persisted
        verify(mThemeSettingsManager).setSettings(eq(userId), any(),
                org.mockito.ArgumentMatchers.argThat(settingsArgument ->
                        settingsArgument.seedColors().getFirst().toArgb() == Color.GREEN
                ));
    }

    @Test
    public void onUserStart_presetSource_usesSystemPalette() {
        int userId = 102;
        // Do NOT start user in advance.

        // Setup: Preset source in settings (simulated via SettingsManager mock behavior or pre-set)
        ThemeSettings settings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.VIBRANT)
                .setColorSource(VALUE_PRESET)
                .setSeedColors(Color.valueOf(Color.GREEN))
                .build();
        mFakeSettingsMap.put(userId, settings);

        // Action
        mUnderTest.onUserStart(userId);
        mSchedulerExecutor.fastForwardTime(ThemeStateManager.DEBOUNCE_MS + 100L);

        // Verify
        assertThat(mStateManager.getState(userId).getCurrentState().seedColor()).isEqualTo(
                Color.GREEN);
    }

    @Test
    public void onThemeSettingsChanged_reloadsAndUpdates() {
        int userId = 103;
        startUser(userId, true, List.of(Color.BLUE), 0f, ThemeStyle.TONAL_SPOT);

        // Setup: Initial state
        ThemeSettings initialSettings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.TONAL_SPOT)
                .setColorSource(VALUE_PRESET)
                .setSeedColors(Color.valueOf(Color.BLUE))
                .build();
        mUnderTest.updateThemeSettings(userId, initialSettings);

        // Verify initial state
        assertThat(mStateManager.getState(userId).getCurrentState().seedColor()).isEqualTo(
                Color.BLUE);

        // Simulate external change to settings provider (e.g. by another process)
        ThemeSettings newSettings = new ThemeSettings.Builder()
                .setThemeStyle(ThemeStyle.EXPRESSIVE)
                .setColorSource(VALUE_PRESET)
                .setSeedColors(Color.valueOf(Color.RED))
                .build();
        mFakeSettingsMap.put(userId, newSettings);

        // Action
        mUnderTest.onThemeSettingsChanged(userId);
        mSchedulerExecutor.fastForwardTime(ThemeStateManager.DEBOUNCE_MS + 100L);

        // Verify state updated
        assertThat(mStateManager.getState(userId).getCurrentState().seedColor()).isEqualTo(
                Color.RED);
        assertThat(mStateManager.getState(userId).getCurrentState().style()).isEqualTo(
                ThemeStyle.EXPRESSIVE);
    }

    // New test porting the persistence verification from ThemeUserLifecycleTest
    @Test
    public void onUserStart_persistsDefaults_ifSettingsMissing() {
        int userId = 104;
        // Ensure no settings exist for this user initially
        mFakeSettingsMap.remove(userId);

        // Action
        mUnderTest.onUserStart(userId);

        // Verify that settings were created and persisted via ThemeSettingsManager
        verify(mThemeSettingsManager).createDefaultThemeSettings(userId);
        verify(mThemeSettingsManager).setSettings(eq(userId), any(), any());

        ThemeSettings stored = mFakeSettingsMap.get(userId);
        assertThat(stored).isNotNull();
    }

    @Test
    public void onUserSwitching_toSystemUserOniHSUM_processed() {
        // Setup: Enable Headless System User Mode
        when(mUserManager.isHeadlessSystemUserMode()).thenReturn(true);
        // Setup: Enable "iHSUM" (allow switching to system user)
        mTestableResources.addOverride(
                com.android.internal.R.bool.config_canSwitchToHeadlessSystemUser, true);
        setupUnderTest();

        int fromUser = 10;
        int toUser = UserHandle.USER_SYSTEM;

        // Action: Switch to system user
        mUnderTest.onUserSwitching(fromUser, toUser);

        // Verify: StateManager received the event (meaning it wasn't ignored)
        verify(mStateManager).onUserSwitching(fromUser, toUser);
    }

    @Test
    public void onUserSwitching_toSystemUserOnHSUM_notProcessed() {
        // Setup: Enable Headless System User Mode
        when(mUserManager.isHeadlessSystemUserMode()).thenReturn(true);
        // Setup: Disable "iHSUM" (disallow switching to system user)
        mTestableResources.addOverride(
                com.android.internal.R.bool.config_canSwitchToHeadlessSystemUser, false);
        setupUnderTest();

        int fromUser = 10;
        int toUser = UserHandle.USER_SYSTEM;

        // Action: Switch to system user
        mUnderTest.onUserSwitching(fromUser, toUser);

        // Verify: StateManager didn't receive the event (ignored)
        verify(mStateManager, never()).onUserSwitching(fromUser, toUser);
    }
}
