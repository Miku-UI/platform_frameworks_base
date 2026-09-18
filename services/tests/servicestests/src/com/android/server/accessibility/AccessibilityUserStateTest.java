/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.accessibility;

import static android.accessibilityservice.AccessibilityService.SHOW_MODE_AUTO;
import static android.accessibilityservice.AccessibilityService.SHOW_MODE_HARD_KEYBOARD_ORIGINAL_VALUE;
import static android.accessibilityservice.AccessibilityService.SHOW_MODE_HARD_KEYBOARD_OVERRIDDEN;
import static android.accessibilityservice.AccessibilityService.SHOW_MODE_HIDDEN;
import static android.accessibilityservice.AccessibilityService.SHOW_MODE_IGNORE_HARD_KEYBOARD;
import static android.content.pm.PackageManager.FEATURE_WINDOW_MAGNIFICATION;
import static android.provider.Settings.Secure.ACCESSIBILITY_MAGNIFICATION_CURSOR_FOLLOWING_MODE_CENTER;
import static android.provider.Settings.Secure.ACCESSIBILITY_MAGNIFICATION_CURSOR_FOLLOWING_MODE_CONTINUOUS;
import static android.provider.Settings.Secure.ACCESSIBILITY_MAGNIFICATION_CURSOR_FOLLOWING_MODE_EDGE;
import static android.provider.Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE_FULLSCREEN;
import static android.provider.Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE_WINDOW;
import static android.view.accessibility.AccessibilityManager.STATE_FLAG_ACCESSIBILITY_ENABLED;
import static android.view.accessibility.AccessibilityManager.STATE_FLAG_HIGH_TEXT_CONTRAST_ENABLED;
import static android.view.accessibility.AccessibilityManager.STATE_FLAG_TOUCH_EXPLORATION_ENABLED;

import static com.android.internal.accessibility.AccessibilityShortcutController.MAGNIFICATION_CONTROLLER_NAME;
import static com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType.ALL;
import static com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType.GESTURE;
import static com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType.HARDWARE;
import static com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType.KEY_GESTURE;
import static com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType.QUICK_SETTINGS;
import static com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType.SOFTWARE;
import static com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType.TOP_ROW_KEY;
import static com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType.TRIPLETAP;
import static com.android.server.accessibility.AccessibilityUserState.doesShortcutTargetsStringContain;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.graphics.Color;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.test.mock.MockContentResolver;
import android.testing.DexmakerShareClassLoaderRule;
import android.util.ArraySet;
import android.view.Display;

import androidx.test.InstrumentationRegistry;

import com.android.internal.R;
import com.android.internal.accessibility.AccessibilityShortcutController;
import com.android.internal.accessibility.common.ShortcutConstants;
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType;
import com.android.internal.accessibility.util.ShortcutUtils;
import com.android.internal.util.test.FakeSettingsProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tests for AccessibilityUserState */
public class AccessibilityUserStateTest {

    private static final ComponentName COMPONENT_NAME =
            new ComponentName("com.android.server.accessibility", "AccessibilityUserStateTest");
    private static final ComponentName COMPONENT_NAME1 =
            new ComponentName("com.android.server.accessibility",
                    "com.android.server.accessibility.AccessibilityUserStateTest1");
    private static final ComponentName COMPONENT_NAME2 =
            new ComponentName("com.android.server.accessibility",
                    "com.android.server.accessibility.AccessibilityUserStateTest2");

    // Values of setting key SHOW_IME_WITH_HARD_KEYBOARD
    private static final int STATE_HIDE_IME = 0;
    private static final int STATE_SHOW_IME = 1;

    private static final int USER_ID = 42;

    private static final int TEST_DISPLAY = Display.DEFAULT_DISPLAY + 1;

    // Mock package-private class AccessibilityServiceConnection
    @Rule public final DexmakerShareClassLoaderRule mDexmakerShareClassLoaderRule =
            new DexmakerShareClassLoaderRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock private AccessibilityServiceInfo mMockServiceInfo;

    @Mock private AccessibilityServiceConnection mMockConnection;

    @Mock private AccessibilityUserState.ServiceInfoChangeListener mMockListener;

    @Mock private PackageManager mMockPackageManager;

    @Mock private Context mMockContext;

    @Mock private DevicePolicyManager mMockDevicePolicyManager;

    private MockContentResolver mMockResolver;

    private AccessibilityUserState mUserState;

    private int mFocusStrokeWidthDefaultValue;
    private int mFocusColorDefaultValue;

    @Before
    public void setUp() {
        final Resources resources = InstrumentationRegistry.getContext().getResources();

        MockitoAnnotations.initMocks(this);
        FakeSettingsProvider.clearSettingsProvider();
        mMockResolver = new MockContentResolver();
        mMockResolver.addProvider(Settings.AUTHORITY, new FakeSettingsProvider());
        when(mMockContext.getContentResolver()).thenReturn(mMockResolver);
        when(mMockServiceInfo.getComponentName()).thenReturn(COMPONENT_NAME);
        when(mMockConnection.getServiceInfo()).thenReturn(mMockServiceInfo);
        when(mMockContext.getResources()).thenReturn(resources);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockPackageManager.hasSystemFeature(FEATURE_WINDOW_MAGNIFICATION)).thenReturn(true);
        when(mMockServiceInfo.isAccessibilityTool()).thenReturn(false);
        when(mMockContext.getSystemService(Context.DEVICE_POLICY_SERVICE)).thenReturn(
                mMockDevicePolicyManager);
        when(mMockContext.getSystemServiceName(DevicePolicyManager.class)).thenReturn(
                Context.DEVICE_POLICY_SERVICE);
        mFocusStrokeWidthDefaultValue =
                resources.getDimensionPixelSize(R.dimen.accessibility_focus_highlight_stroke_width);
        mFocusColorDefaultValue = resources.getColor(R.color.accessibility_focus_highlight_color);

        mUserState = new AccessibilityUserState(USER_ID, mMockContext, mMockListener);
    }

    @After
    public void tearDown() {
        FakeSettingsProvider.clearSettingsProvider();
    }

    @Test
    public void onSwitchToAnotherUser_userStateClearedNonDefaultValues() {
        String componentNameString = COMPONENT_NAME.flattenToString();
        mUserState.getBoundServicesLocked().add(mMockConnection);
        mUserState.getBindingServicesLocked().add(COMPONENT_NAME);
        mUserState.getServiceConnections().add(mMockConnection);
        mUserState.setLastSentClientStateLocked(
                STATE_FLAG_ACCESSIBILITY_ENABLED
                        | STATE_FLAG_TOUCH_EXPLORATION_ENABLED
                        | STATE_FLAG_HIGH_TEXT_CONTRAST_ENABLED);
        mUserState.setNonInteractiveUiTimeoutLocked(30);
        mUserState.setInteractiveUiTimeoutLocked(30);
        mUserState.mEnabledServices.add(COMPONENT_NAME);
        mUserState.mTouchExplorationGrantedServices.add(COMPONENT_NAME);
        mUserState.updateShortcutTargetsLocked(Set.of(componentNameString), HARDWARE);
        mUserState.updateShortcutTargetsLocked(Set.of(componentNameString), SOFTWARE);
        mUserState.updateShortcutTargetsLocked(Set.of(componentNameString), GESTURE);
        mUserState.updateShortcutTargetsLocked(Set.of(componentNameString), QUICK_SETTINGS);
        mUserState.updateShortcutTargetsLocked(Set.of(componentNameString), KEY_GESTURE);
        mUserState.updateShortcutTargetsLocked(Set.of(componentNameString), TOP_ROW_KEY);
        mUserState.updateA11yTilesInQsPanelLocked(
                Set.of(AccessibilityShortcutController.COLOR_INVERSION_TILE_COMPONENT_NAME));
        mUserState.setTargetAssignedToAccessibilityButton(componentNameString);
        mUserState.setTouchExplorationEnabledLocked(true);
        mUserState.setMagnificationSingleFingerTripleTapEnabledLocked(true);
        mUserState.setAutoclickEnabledLocked(true);
        mUserState.setUserNonInteractiveUiTimeoutLocked(30);
        mUserState.setUserInteractiveUiTimeoutLocked(30);
        mUserState.setMagnificationModeLocked(TEST_DISPLAY,
                ACCESSIBILITY_MAGNIFICATION_MODE_WINDOW);
        mUserState.setFocusAppearanceLocked(20, Color.BLUE);

        mUserState.onSwitchToAnotherUserLocked();

        verify(mMockConnection).unbindLocked();
        assertThat(mUserState.getBoundServicesLocked()).isEmpty();
        assertThat(mUserState.getBindingServicesLocked()).isEmpty();
        assertThat(mUserState.getLastSentClientStateLocked()).isEqualTo(-1);
        assertThat(mUserState.getNonInteractiveUiTimeoutLocked()).isEqualTo(0);
        assertThat(mUserState.getInteractiveUiTimeoutLocked()).isEqualTo(0);
        assertThat(mUserState.mEnabledServices).isEmpty();
        assertThat(mUserState.mTouchExplorationGrantedServices).isEmpty();
        assertThat(mUserState.getShortcutTargetsLocked(HARDWARE)).isEmpty();
        assertThat(mUserState.getShortcutTargetsLocked(SOFTWARE)).isEmpty();
        assertThat(mUserState.getShortcutTargetsLocked(GESTURE)).isEmpty();
        assertThat(mUserState.getShortcutTargetsLocked(QUICK_SETTINGS)).isEmpty();
        assertThat(mUserState.getShortcutTargetsLocked(KEY_GESTURE)).isEmpty();
        assertThat(mUserState.getShortcutTargetsLocked(TOP_ROW_KEY)).isEmpty();
        assertThat(mUserState.getA11yQsTilesInQsPanel()).isEmpty();
        assertThat(mUserState.getTargetAssignedToAccessibilityButton()).isNull();
        assertThat(mUserState.isTouchExplorationEnabledLocked()).isFalse();
        assertThat(mUserState.isMagnificationSingleFingerTripleTapEnabledLocked()).isFalse();
        assertThat(mUserState.isAutoclickEnabledLocked()).isFalse();
        assertThat(mUserState.getUserNonInteractiveUiTimeoutLocked()).isEqualTo(0);
        assertThat(mUserState.getUserInteractiveUiTimeoutLocked()).isEqualTo(0);
        assertThat(mUserState.getMagnificationModeLocked(TEST_DISPLAY))
                .isEqualTo(ACCESSIBILITY_MAGNIFICATION_MODE_FULLSCREEN);
        assertThat(mUserState.getFocusStrokeWidthLocked())
                .isEqualTo(mFocusStrokeWidthDefaultValue);
        assertThat(mUserState.getFocusColorLocked()).isEqualTo(mFocusColorDefaultValue);
        assertThat(mUserState.isMagnificationFollowTypingEnabled()).isTrue();
        assertThat(mUserState.isAlwaysOnMagnificationEnabled()).isFalse();
    }

    @Test
    public void addService_connectionAlreadyAdded_notAddAgain() {
        mUserState.getBoundServicesLocked().add(mMockConnection);

        mUserState.addServiceLocked(mMockConnection);

        verify(mMockListener, never()).onServiceInfoChangedLocked(any());
    }

    @Test
    public void addService_connectionNotYetAddedToBoundService_addAndNotifyServices() {
        when(mMockConnection.getComponentName()).thenReturn(COMPONENT_NAME);

        mUserState.addServiceLocked(mMockConnection);

        assertThat(mUserState.getBoundServicesLocked()).contains(mMockConnection);
        assertThat(mUserState.mComponentNameToServiceMap.get(COMPONENT_NAME))
                .isEqualTo(mMockConnection);
        verify(mMockListener).onServiceInfoChangedLocked(eq(mUserState));
    }

    @Test
    public void reconcileSoftKeyboardMode_whenStateNotMatchSettings_setBothDefault() {
        // When soft kb show mode is hidden in settings and is auto in state.
        putSecureIntForUser(Settings.Secure.ACCESSIBILITY_SOFT_KEYBOARD_MODE,
                SHOW_MODE_HIDDEN, USER_ID);

        mUserState.reconcileSoftKeyboardModeWithSettingsLocked();

        assertThat(mUserState.getSoftKeyboardShowModeLocked()).isEqualTo(SHOW_MODE_AUTO);
        assertThat(getSecureIntForUser(
                Settings.Secure.ACCESSIBILITY_SOFT_KEYBOARD_MODE, USER_ID))
                .isEqualTo(SHOW_MODE_AUTO);
        assertThat(mUserState.getServiceChangingSoftKeyboardModeLocked()).isNull();
    }

    @Test
    public void
            reconcileSoftKeyboardMode_stateIgnoreHardKb_settingsShowImeHardKb_setAutoOverride() {
        // When show mode is ignore hard kb without original hard kb value
        // and show ime with hard kb is hide
        putSecureIntForUser(Settings.Secure.ACCESSIBILITY_SOFT_KEYBOARD_MODE,
                SHOW_MODE_IGNORE_HARD_KEYBOARD, USER_ID);
        mUserState.setSoftKeyboardModeLocked(SHOW_MODE_IGNORE_HARD_KEYBOARD, COMPONENT_NAME);
        putSecureIntForUser(Settings.Secure.SHOW_IME_WITH_HARD_KEYBOARD,
                STATE_HIDE_IME, USER_ID);

        mUserState.reconcileSoftKeyboardModeWithSettingsLocked();

        assertThat(getSecureIntForUser(Settings.Secure.ACCESSIBILITY_SOFT_KEYBOARD_MODE, USER_ID))
                .isEqualTo(SHOW_MODE_AUTO | SHOW_MODE_HARD_KEYBOARD_OVERRIDDEN);
        assertThat(mUserState.getServiceChangingSoftKeyboardModeLocked()).isNull();
    }

    @Test
    public void removeService_serviceChangingSoftKeyboardMode_removeAndSetSoftKbModeAuto() {
        mUserState.setServiceChangingSoftKeyboardModeLocked(COMPONENT_NAME);
        mUserState.mComponentNameToServiceMap.put(COMPONENT_NAME, mMockConnection);
        mUserState.setSoftKeyboardModeLocked(SHOW_MODE_HIDDEN, COMPONENT_NAME);

        mUserState.removeServiceLocked(mMockConnection);

        assertThat(mUserState.getBoundServicesLocked()).doesNotContain(mMockConnection);
        verify(mMockConnection).onRemoved();
        assertThat(mUserState.getSoftKeyboardShowModeLocked()).isEqualTo(SHOW_MODE_AUTO);
        assertThat(mUserState.mComponentNameToServiceMap.get(COMPONENT_NAME)).isNull();
        verify(mMockListener).onServiceInfoChangedLocked(eq(mUserState));
    }

    @Test
    public void serviceDisconnected_removeServiceAndAddToCrashed() {
        when(mMockConnection.getComponentName()).thenReturn(COMPONENT_NAME);
        mUserState.addServiceLocked(mMockConnection);

        mUserState.serviceDisconnectedLocked(mMockConnection);

        assertThat(mUserState.getBoundServicesLocked()).doesNotContain(mMockConnection);
        assertThat(mUserState.getCrashedServicesLocked()).contains(COMPONENT_NAME);
    }

    @Test
    public void setSoftKeyboardMode_withInvalidShowMode_shouldKeepDefaultAuto() {
        final int invalidShowMode = SHOW_MODE_HIDDEN | SHOW_MODE_HARD_KEYBOARD_ORIGINAL_VALUE;

        assertThat(mUserState.setSoftKeyboardModeLocked(invalidShowMode, null)).isFalse();

        assertThat(mUserState.getSoftKeyboardShowModeLocked()).isEqualTo(SHOW_MODE_AUTO);
    }

    @Test
    public void setSoftKeyboardMode_newModeSameWithCurrentState_returnTrue() {
        when(mMockConnection.getComponentName()).thenReturn(COMPONENT_NAME);
        mUserState.addServiceLocked(mMockConnection);

        assertThat(mUserState.setSoftKeyboardModeLocked(SHOW_MODE_AUTO, null)).isTrue();
    }

    @Test
    public void setSoftKeyboardMode_withIgnoreHardKb_whenHardKbOverridden_returnFalseAdNoChange() {
        putSecureIntForUser(Settings.Secure.ACCESSIBILITY_SOFT_KEYBOARD_MODE,
                SHOW_MODE_AUTO | SHOW_MODE_HARD_KEYBOARD_OVERRIDDEN, USER_ID);

        assertThat(mUserState.setSoftKeyboardModeLocked(SHOW_MODE_IGNORE_HARD_KEYBOARD, null))
                .isFalse();

        assertThat(mUserState.getSoftKeyboardShowModeLocked()).isEqualTo(SHOW_MODE_AUTO);
    }

    @Test
    public void
            setSoftKeyboardMode_withIgnoreHardKb_whenShowImeWithHardKb_setOriginalHardKbValue() {
        putSecureIntForUser(Settings.Secure.SHOW_IME_WITH_HARD_KEYBOARD, STATE_SHOW_IME, USER_ID);

        assertThat(mUserState.setSoftKeyboardModeLocked(SHOW_MODE_IGNORE_HARD_KEYBOARD, null))
                .isTrue();

        assertThat(getSecureIntForUser(Settings.Secure.ACCESSIBILITY_SOFT_KEYBOARD_MODE, USER_ID))
                .isEqualTo(SHOW_MODE_IGNORE_HARD_KEYBOARD | SHOW_MODE_HARD_KEYBOARD_ORIGINAL_VALUE);
    }

    @Test
    public void setSoftKeyboardMode_whenCurrentIgnoreHardKb_shouldSetShowImeWithHardKbValue() {
        mUserState.setSoftKeyboardModeLocked(SHOW_MODE_IGNORE_HARD_KEYBOARD, COMPONENT_NAME);
        putSecureIntForUser(Settings.Secure.SHOW_IME_WITH_HARD_KEYBOARD, STATE_HIDE_IME, USER_ID);
        putSecureIntForUser(Settings.Secure.ACCESSIBILITY_SOFT_KEYBOARD_MODE,
                SHOW_MODE_IGNORE_HARD_KEYBOARD | SHOW_MODE_HARD_KEYBOARD_ORIGINAL_VALUE, USER_ID);

        assertThat(mUserState.setSoftKeyboardModeLocked(SHOW_MODE_AUTO, null)).isTrue();

        assertThat(getSecureIntForUser(
                Settings.Secure.SHOW_IME_WITH_HARD_KEYBOARD, USER_ID)).isEqualTo(STATE_SHOW_IME);
    }

    @Test
    public void setSoftKeyboardMode_withRequester_shouldUpdateInternalStateAndSettingsAsIs() {
        assertThat(mUserState.setSoftKeyboardModeLocked(SHOW_MODE_HIDDEN, COMPONENT_NAME))
                .isTrue();

        assertThat(mUserState.getSoftKeyboardShowModeLocked()).isEqualTo(SHOW_MODE_HIDDEN);
        assertThat(getSecureIntForUser(
                Settings.Secure.ACCESSIBILITY_SOFT_KEYBOARD_MODE, USER_ID))
                .isEqualTo(SHOW_MODE_HIDDEN);
        assertThat(mUserState.getServiceChangingSoftKeyboardModeLocked())
                .isEqualTo(COMPONENT_NAME);
    }

    @Test
    public void setSoftKeyboardMode_shouldNotifyBoundService() {
        mUserState.addServiceLocked(mMockConnection);

        assertThat(mUserState.setSoftKeyboardModeLocked(SHOW_MODE_HIDDEN, COMPONENT_NAME))
                .isTrue();

        verify(mMockConnection).notifySoftKeyboardShowModeChangedLocked(eq(SHOW_MODE_HIDDEN));
    }

    @Test
    public void doesShortcutTargetsStringContain_returnFalse() {
        assertThat(doesShortcutTargetsStringContain(null, null)).isFalse();
        assertThat(doesShortcutTargetsStringContain(null,
                COMPONENT_NAME.flattenToShortString())).isFalse();
        assertThat(doesShortcutTargetsStringContain(new ArraySet<>(), null)).isFalse();

        final ArraySet<String> shortcutTargets = new ArraySet<>();
        shortcutTargets.add(COMPONENT_NAME.flattenToString());
        assertThat(doesShortcutTargetsStringContain(shortcutTargets,
                COMPONENT_NAME1.flattenToString())).isFalse();
    }

    @Test
    public void isAssignedToShortcutLocked_withDifferentTypeComponentString_returnTrue() {
        final ArraySet<String> shortcutTargets = new ArraySet<>();
        shortcutTargets.add(COMPONENT_NAME1.flattenToShortString());
        shortcutTargets.add(COMPONENT_NAME2.flattenToString());

        assertThat(doesShortcutTargetsStringContain(shortcutTargets,
                COMPONENT_NAME1.flattenToString())).isTrue();
        assertThat(doesShortcutTargetsStringContain(shortcutTargets,
                COMPONENT_NAME2.flattenToShortString())).isTrue();
    }

    @Test
    public void isShortcutTargetInstalledLocked_returnTrue() {
        List<AccessibilityServiceInfo> installedServices = new ArrayList<>(
                mUserState.getInstalledServices());
        installedServices.add(mMockServiceInfo);
        mUserState.buildInstalledServicesMapLocked(installedServices);
        assertThat(mUserState.isShortcutTargetInstalledLocked(COMPONENT_NAME.flattenToString()))
                .isTrue();
    }

    @Test
    @EnableFlags(android.security.Flags.FLAG_EXTEND_AAPM_TO_A11Y_SERVICES)
    public void isAccessibilityFeaturePermittedLocked_apmOff_returnTrue() {
        List<AccessibilityServiceInfo> installedServices = new ArrayList<>(
                mUserState.getInstalledServices());
        installedServices.add(mMockServiceInfo);
        mUserState.buildInstalledServicesMapLocked(installedServices);
        mUserState.setPermittedAccessibilityServicesLocked(null);

        boolean isFeaturePermitted = mUserState.isAccessibilityFeaturePermittedLocked(
                COMPONENT_NAME.flattenToString());

        assertThat(isFeaturePermitted).isTrue();
    }

    @Test
    @EnableFlags(android.security.Flags.FLAG_EXTEND_AAPM_TO_A11Y_SERVICES)
    public void isAccessibilityFeaturePermittedLocked_apmOn_returnFalse() {
        List<AccessibilityServiceInfo> installedServices = new ArrayList<>(
                mUserState.getInstalledServices());
        installedServices.add(mMockServiceInfo);
        mUserState.buildInstalledServicesMapLocked(installedServices);
        mUserState.setPermittedAccessibilityServicesLocked(new HashSet<>());

        boolean isFeaturePermitted = mUserState.isAccessibilityFeaturePermittedLocked(
                COMPONENT_NAME.flattenToString());

        assertThat(isFeaturePermitted).isFalse();
    }

    @Test
    @EnableFlags(android.security.Flags.FLAG_EXTEND_AAPM_TO_A11Y_SERVICES)
    public void isAccessibilityFeaturePermittedLocked_apmOn_permittedService_returnTrue() {
        // Setup: Add an installed service to the user state.
        List<AccessibilityServiceInfo> installedServices = new ArrayList<>(
                mUserState.getInstalledServices());
        installedServices.add(mMockServiceInfo);
        mUserState.buildInstalledServicesMapLocked(installedServices);

        Set<String> permitted = new HashSet<>();
        permitted.add(COMPONENT_NAME.getPackageName());
        mUserState.setPermittedAccessibilityServicesLocked(permitted);

        // Action: Check if the service is permitted as a shortcut target.
        boolean isFeaturePermitted = mUserState.isAccessibilityFeaturePermittedLocked(
                COMPONENT_NAME.flattenToString());

        // Assertion: Should be true.
        assertThat(isFeaturePermitted).isTrue();
    }

    @Test
    public void isAccessibilityFeaturePermittedLocked_builtInFeature_returnsTrue() {
        // Block everything
        mUserState.setPermittedAccessibilityServicesLocked(new HashSet<>());

        // Magnification controller is a built-in feature and should be permitted
        assertThat(mUserState.isAccessibilityFeaturePermittedLocked(MAGNIFICATION_CONTROLLER_NAME))
                .isTrue();
    }


    @Test
    public void isShortcutTargetInstalledLocked_invalidTarget_returnFalse() {
        final ComponentName invalidTarget =
                new ComponentName("com.android.server.accessibility", "InvalidTarget");
        assertThat(
                mUserState.isShortcutTargetInstalledLocked(invalidTarget.flattenToString()))
                .isFalse();
    }

    @Test
    public void isAccessibilityFeaturePermittedLocked_spoofedFrameworkFeature_returnFalse() {
        ComponentName spoofedComponent =
                AccessibilityShortcutController.COLOR_INVERSION_COMPONENT_NAME;
        AccessibilityServiceInfo spoofedServiceInfo = mock(AccessibilityServiceInfo.class);
        when(spoofedServiceInfo.getComponentName()).thenReturn(spoofedComponent);

        List<AccessibilityServiceInfo> installedServices = new ArrayList<>();
        installedServices.add(spoofedServiceInfo);
        mUserState.buildInstalledServicesMapLocked(installedServices);

        assertThat(mUserState.isAccessibilityFeaturePermittedLocked(
                spoofedComponent.flattenToString())).isFalse();
    }

    @Test
    public void setWindowMagnificationMode_returnExpectedMagnificationMode() {
        assertThat(mUserState.getMagnificationModeLocked(TEST_DISPLAY))
                .isEqualTo(ACCESSIBILITY_MAGNIFICATION_MODE_FULLSCREEN);

        mUserState.setMagnificationModeLocked(TEST_DISPLAY,
                ACCESSIBILITY_MAGNIFICATION_MODE_WINDOW);

        assertThat(mUserState.getMagnificationModeLocked(TEST_DISPLAY))
                .isEqualTo(ACCESSIBILITY_MAGNIFICATION_MODE_WINDOW);
    }

    @Test
    public void getMagnificationModeLocked_setOnDefaultDisplay_returnExpectedMagnificationMode() {
        mUserState.setMagnificationModeLocked(
                Display.DEFAULT_DISPLAY, ACCESSIBILITY_MAGNIFICATION_MODE_WINDOW);

        // If there is no cached magnification mode on TEST_DISPLAY, then it will retrieve the
        // cached mode on default display.
        assertThat(mUserState.getMagnificationModeLocked(TEST_DISPLAY))
                .isEqualTo(ACCESSIBILITY_MAGNIFICATION_MODE_WINDOW);
    }

    @Test
    public void getMagnificationModeLocked_returnFullScreenMagnificationModeByDefault() {
        // If there is no cached magnification mode on TEST_DISPLAY and on default display, then it
        // will return full screen mode.
        assertThat(mUserState.getMagnificationModeLocked(TEST_DISPLAY))
                .isEqualTo(ACCESSIBILITY_MAGNIFICATION_MODE_FULLSCREEN);
    }

    @Test
    public void setCursorFollowingMode_returnExpectedCursorFollowingMode() {
        assertThat(mUserState.getMagnificationCursorFollowingMode())
                .isEqualTo(ACCESSIBILITY_MAGNIFICATION_CURSOR_FOLLOWING_MODE_CONTINUOUS);

        mUserState.setMagnificationCursorFollowingMode(
                ACCESSIBILITY_MAGNIFICATION_CURSOR_FOLLOWING_MODE_CENTER);

        assertThat(mUserState.getMagnificationCursorFollowingMode())
                .isEqualTo(ACCESSIBILITY_MAGNIFICATION_CURSOR_FOLLOWING_MODE_CENTER);

        mUserState.setMagnificationCursorFollowingMode(
                ACCESSIBILITY_MAGNIFICATION_CURSOR_FOLLOWING_MODE_EDGE);

        assertThat(mUserState.getMagnificationCursorFollowingMode())
                .isEqualTo(ACCESSIBILITY_MAGNIFICATION_CURSOR_FOLLOWING_MODE_EDGE);
    }

    @Test
    public void setMagnificationFollowTypingEnabled_defaultTrueAndThenDisable_returnFalse() {
        assertThat(mUserState.isMagnificationFollowTypingEnabled()).isTrue();

        mUserState.setMagnificationFollowTypingEnabled(false);

        assertThat(mUserState.isMagnificationFollowTypingEnabled()).isFalse();
    }

    @Test
    public void setAlwaysOnMagnificationEnabled_defaultFalseAndSetTrue_returnTrue() {
        assertThat(mUserState.isAlwaysOnMagnificationEnabled()).isFalse();

        mUserState.setAlwaysOnMagnificationEnabled(true);

        assertThat(mUserState.isAlwaysOnMagnificationEnabled()).isTrue();
    }

    @Test
    public void setFocusAppearanceData_returnExpectedFocusAppearanceData() {
        final int focusStrokeWidthValue = 100;
        final int focusColorValue = Color.BLUE;

        assertThat(mUserState.getFocusStrokeWidthLocked())
                .isEqualTo(mFocusStrokeWidthDefaultValue);
        assertThat(mUserState.getFocusColorLocked()).isEqualTo(mFocusColorDefaultValue);

        mUserState.setFocusAppearanceLocked(focusStrokeWidthValue, focusColorValue);

        assertThat(mUserState.getFocusStrokeWidthLocked()).isEqualTo(focusStrokeWidthValue);
        assertThat(mUserState.getFocusColorLocked()).isEqualTo(focusColorValue);
    }

    @Test
    public void updateShortcutTargetsLocked_quickSettings_valueUpdated() {
        Set<String> newTargets = Set.of(
                AccessibilityShortcutController.DALTONIZER_COMPONENT_NAME.flattenToString(),
                AccessibilityShortcutController.COLOR_INVERSION_COMPONENT_NAME.flattenToString()
        );

        mUserState.updateShortcutTargetsLocked(newTargets, QUICK_SETTINGS);

        assertThat(mUserState.getShortcutTargetsLocked(QUICK_SETTINGS)).isEqualTo(newTargets);
    }

    @Test
    public void updateA11yTilesInQsPanelLocked_valueUpdated() {
        Set<ComponentName> newTargets = Set.of(
                AccessibilityShortcutController.DALTONIZER_TILE_COMPONENT_NAME,
                AccessibilityShortcutController.COLOR_INVERSION_TILE_COMPONENT_NAME
        );

        mUserState.updateA11yTilesInQsPanelLocked(newTargets);

        assertThat(mUserState.getA11yQsTilesInQsPanel()).isEqualTo(newTargets);
    }

    @Test
    public void getA11yQsTilesInQsPanel_returnsCopiedData() {
        updateA11yTilesInQsPanelLocked_valueUpdated();

        Set<ComponentName> targets = mUserState.getA11yQsTilesInQsPanel();
        targets.clear();

        assertThat(mUserState.getA11yQsTilesInQsPanel()).isNotEmpty();
    }

    @Test
    public void getTileServiceToA11yServiceInfoMapLocked() {
        final ComponentName tileComponent =
                new ComponentName(COMPONENT_NAME.getPackageName(), "FakeTileService");
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.packageName = tileComponent.getPackageName();
        serviceInfo.name = COMPONENT_NAME.getClassName();
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = serviceInfo;
        when(mMockServiceInfo.getTileServiceName()).thenReturn(tileComponent.getClassName());
        when(mMockServiceInfo.getResolveInfo()).thenReturn(resolveInfo);
        List<AccessibilityServiceInfo> installedServices = new ArrayList<>(
                mUserState.getInstalledServices());
        installedServices.add(mMockServiceInfo);
        mUserState.buildInstalledServicesMapLocked(installedServices);
        mUserState.updateTileServiceMapForAccessibilityServiceLocked(Set.of(tileComponent));

        Map<ComponentName, AccessibilityServiceInfo> actual =
                mUserState.getTileServiceToA11yServiceInfoMapLocked();

        assertThat(actual).containsExactly(tileComponent, mMockServiceInfo);
    }

    @Test
    public void isShortcutMagnificationEnabledLocked_anyShortcutType_returnsTrue() {
        // Clear every shortcut
        for (int shortcutType : ShortcutConstants.USER_SHORTCUT_TYPES) {
            setMagnificationForShortcutType(shortcutType, false);
        }
        // Check each shortcut individually
        for (int shortcutType : ShortcutConstants.USER_SHORTCUT_TYPES) {
            // Setup
            setMagnificationForShortcutType(shortcutType, true);

            // Checking
            assertThat(mUserState.getShortcutTargetsLocked(shortcutType))
                    .containsExactly(MAGNIFICATION_CONTROLLER_NAME);
            assertThat(mUserState.isShortcutMagnificationEnabledLocked()).isTrue();

            // Cleanup
            setMagnificationForShortcutType(shortcutType, false);
        }
    }

    @Test
    public void isShortcutMagnificationEnabledLocked_noShortcutTypes_returnsFalse() {
        // Clear every shortcut
        for (int shortcutType : ShortcutConstants.USER_SHORTCUT_TYPES) {
            setMagnificationForShortcutType(shortcutType, false);
        }
        assertThat(mUserState.isShortcutMagnificationEnabledLocked()).isFalse();
    }

    @Test
    public void getShortcutTargetsLocked_returnsCorrectTargets() {
        for (int shortcutType : ShortcutConstants.USER_SHORTCUT_TYPES) {
            if ((TRIPLETAP & shortcutType) == shortcutType) {
                continue;
            }
            Set<String> expectedSet = Set.of(ShortcutUtils.convertToKey(shortcutType));
            mUserState.updateShortcutTargetsLocked(expectedSet, shortcutType);

            assertThat(mUserState.getShortcutTargetsLocked(shortcutType))
                    .containsExactlyElementsIn(expectedSet);
        }
    }

    @Test
    public void getShortcutTargetsLocked_returnsCopiedData() {
        Set<String> set = Set.of("FOO", "BAR");
        mUserState.updateShortcutTargetsLocked(set, SOFTWARE);

        Set<String> targets = mUserState.getShortcutTargetsLocked(ALL);
        targets.clear();

        assertThat(mUserState.getShortcutTargetsLocked(ALL)).isNotEmpty();
    }

    @Test
    public void buildInstalledServicesMapLocked_returnCorrectMap() {
        List<AccessibilityServiceInfo> installedServices = new ArrayList<>(
                mUserState.getInstalledServices());
        installedServices.add(mMockServiceInfo);
        mUserState.buildInstalledServicesMapLocked(installedServices);

        assertThat(mUserState.mInstalledServicesMap.size()).isEqualTo(1);
        assertThat(mUserState.mInstalledServicesMap).containsKey(COMPONENT_NAME);

        assertThat(mUserState.mInstalledServicesMap.get(COMPONENT_NAME))
                .isEqualTo(mMockServiceInfo);
    }

    private int getSecureIntForUser(String key, int userId) {
        return Settings.Secure.getIntForUser(mMockResolver, key, -1, userId);
    }

    private void putSecureIntForUser(String key, int value, int userId) {
        Settings.Secure.putIntForUser(mMockResolver, key, value, userId);
    }

    private void setMagnificationForShortcutType(
            @UserShortcutType int shortcutType, boolean enabled) {
        if (shortcutType == TRIPLETAP) {
            mUserState.setMagnificationSingleFingerTripleTapEnabledLocked(enabled);
        } else {
            mUserState.updateShortcutTargetsLocked(
                    enabled ? Set.of(MAGNIFICATION_CONTROLLER_NAME) : Set.of(), shortcutType);
        }
    }
}
