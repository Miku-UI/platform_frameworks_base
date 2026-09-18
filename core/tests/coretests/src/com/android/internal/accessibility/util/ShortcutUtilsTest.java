/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.internal.accessibility.util;

import static android.provider.Settings.Secure.ACCESSIBILITY_DISPLAY_MAGNIFICATION_ENABLED;
import static android.provider.Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_MAGNIFICATION_CONTROLLER;

import static com.android.internal.accessibility.common.ShortcutConstants.SERVICES_SEPARATOR;
import static com.android.internal.accessibility.common.ShortcutConstants.USER_SHORTCUT_TYPES;
import static com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType.DEFAULT;
import static com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType.KEY_GESTURE;
import static com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType.QUICK_ACCESS;
import static com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType.SOFTWARE;
import static com.android.server.testutils.MockitoUtilsKt.eq;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.UserIdInt;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ParceledListSlice;
import android.content.res.Resources;
import android.hardware.input.KeyGestureEvent;
import android.os.Handler;
import android.os.RemoteException;
import android.provider.Settings;
import android.testing.TestableContext;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.IAccessibilityManager;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.accessibility.AccessibilityShortcutController;
import com.android.internal.accessibility.TestUtils;
import com.android.internal.accessibility.common.ShortcutConstants;

import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Unit Tests for {@link com.android.internal.accessibility.util.ShortcutUtils}
 */
@RunWith(TestParameterInjector.class)
public class ShortcutUtilsTest {
    private static final Set<String> ONE_COMPONENT = Set.of(
            new ComponentName("pkg", "serv").flattenToString());
    private static final Set<String> TWO_COMPONENTS = Set.of(
            new ComponentName("pkg", "serv").flattenToString(),
            AccessibilityShortcutController.MAGNIFICATION_CONTROLLER_NAME);
    private static final String ALWAYS_ON_SERVICE_PACKAGE_LABEL = "always on a11y service";
    private static final String ALWAYS_ON_SERVICE_COMPONENT_NAME =
            "fake.package/fake.alwayson.service.name";

    private static final String STANDARD_SERVICE_PACKAGE_LABEL = "standard a11y service";
    private static final String STANDARD_SERVICE_COMPONENT_NAME =
            "fake.package/fake.standard.service.name";
    private static final String SERVICE_NAME_SUMMARY = "Summary";
    private static final String FAKE_SCREEN_READER_TARGET_NAME = "fake.package/.FakeScreenReader";
    private static final String FAKE_SELECT_TO_SPEAK_TARGET_NAME =
            "fake.package/.FakeSelectToSpeak";
    private static final String FAKE_VOICE_ACCESS_TARGET_NAME = "fake.package/.FakeVoiceAccess";

    @Mock
    private IAccessibilityManager mAccessibilityManagerService;
    private TestableContext mContext;
    @UserIdInt
    private int mDefaultUserId;
    private Resources mMockResources;

    @Before
    public void setUp() throws RemoteException {
        MockitoAnnotations.initMocks(this);
        mContext =
                spy(new TestableContext(InstrumentationRegistry.getInstrumentation().getContext()));
        mDefaultUserId = mContext.getContentResolver().getUserId();
        mMockResources = mock(Resources.class);

        AccessibilityManager accessibilityManager =
                new AccessibilityManager(
                        mContext, mock(Handler.class),
                        mAccessibilityManagerService, mDefaultUserId,
                        /* serviceConnect= */ true);
        mContext.addMockSystemService(Context.ACCESSIBILITY_SERVICE, accessibilityManager);
        setupFakeInstalledA11yServiceInfos();
    }

    @Test
    public void getShortcutTargets_noService_emptyResult(
            @TestParameter(valuesProvider = ShortcutTypeValueProvider.class) int shortcutType) {
        Settings.Secure.putStringForUser(
                mContext.getContentResolver(),
                ShortcutUtils.convertToKey(shortcutType), "", mContext.getUserId());

        assertThat(
                ShortcutUtils.getShortcutTargetsFromSettings(
                        mContext, shortcutType, mDefaultUserId)
        ).isEmpty();
    }

    // TODO 385186274: Parameterize this test.
    @Test
    public void getShortcutTargets_softwareShortcut1Service_return1Service() {
        setupShortcutTargets(ONE_COMPONENT, Settings.Secure.ACCESSIBILITY_BUTTON_TARGETS);
        setupShortcutTargets(TWO_COMPONENTS, Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE);

        assertThat(
                ShortcutUtils.getShortcutTargetsFromSettings(
                        mContext, SOFTWARE,
                        mDefaultUserId)
        ).containsExactlyElementsIn(ONE_COMPONENT);
    }

    // TODO 385186274: Parameterize this test.
    @Test
    public void getShortcutTargets_volumeShortcut2Service_return2Service() {
        setupShortcutTargets(ONE_COMPONENT, Settings.Secure.ACCESSIBILITY_BUTTON_TARGETS);
        setupShortcutTargets(TWO_COMPONENTS, Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE);

        assertThat(
                ShortcutUtils.getShortcutTargetsFromSettings(
                        mContext, ShortcutConstants.UserShortcutType.HARDWARE,
                        mDefaultUserId)
        ).containsExactlyElementsIn(TWO_COMPONENTS);
    }

    // TODO 385186274: Parameterize this test.
    @Test
    public void getShortcutTargets_tripleTapShortcut_magnificationDisabled_emptyResult() {
        enableTripleTapShortcutForMagnification(/* enable= */ false);
        setupShortcutTargets(ONE_COMPONENT, Settings.Secure.ACCESSIBILITY_BUTTON_TARGETS);
        setupShortcutTargets(TWO_COMPONENTS, Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE);

        assertThat(
                ShortcutUtils.getShortcutTargetsFromSettings(
                        mContext, ShortcutConstants.UserShortcutType.TRIPLETAP,
                        mDefaultUserId)
        ).isEmpty();
    }

    // TODO 385186274: Parameterize this test.
    @Test
    public void getShortcutTargets_tripleTapShortcut_magnificationEnabled_returnMagnification() {
        setupShortcutTargets(ONE_COMPONENT, Settings.Secure.ACCESSIBILITY_BUTTON_TARGETS);
        setupShortcutTargets(TWO_COMPONENTS, Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE);
        enableTripleTapShortcutForMagnification(/* enable= */ true);

        assertThat(
                ShortcutUtils.getShortcutTargetsFromSettings(
                        mContext, ShortcutConstants.UserShortcutType.TRIPLETAP,
                        mDefaultUserId)
        ).containsExactly(ACCESSIBILITY_SHORTCUT_TARGET_MAGNIFICATION_CONTROLLER);
    }

    // TODO 385186274: Parameterize this test.
    @Test
    public void updateAccessibilityServiceStateIfNeeded_alwaysOnServiceOn_noShortcuts_serviceTurnedOff() {
        setupA11yServiceAndShortcutState(
                ALWAYS_ON_SERVICE_COMPONENT_NAME, /* serviceOn= */ true, /* shortcutOn= */ false);

        ShortcutUtils.updateInvisibleToggleAccessibilityServiceEnableState(
                mContext,
                Set.of(ALWAYS_ON_SERVICE_COMPONENT_NAME),
                mDefaultUserId
        );

        assertA11yServiceState(ALWAYS_ON_SERVICE_COMPONENT_NAME, /* enabled= */ false);
    }

    // TODO 385186274: Parameterize this test.
    @Test
    public void updateAccessibilityServiceStateIfNeeded_alwaysOnServiceOnForBothUsers_noShortcutsForGuestUser_serviceTurnedOffForGuestUserOnly() {
        // setup arbitrary userId by add 10 to the default user id
        final int guestUserId = mDefaultUserId + 10;
        setupA11yServiceAndShortcutStateForUser(
                ALWAYS_ON_SERVICE_COMPONENT_NAME, /* serviceOn= */ true,
                /* shortcutOn= */ true, mDefaultUserId);
        setupA11yServiceAndShortcutStateForUser(
                ALWAYS_ON_SERVICE_COMPONENT_NAME, /* serviceOn= */ true,
                /* shortcutOn= */ false, guestUserId);

        ShortcutUtils.updateInvisibleToggleAccessibilityServiceEnableState(
                mContext,
                Set.of(ALWAYS_ON_SERVICE_COMPONENT_NAME),
                guestUserId
        );

        assertA11yServiceStateForUser(
                ALWAYS_ON_SERVICE_COMPONENT_NAME, /* enabled= */ false, guestUserId);
        assertA11yServiceStateForUser(
                ALWAYS_ON_SERVICE_COMPONENT_NAME, /* enabled= */ true, mDefaultUserId);
    }

    // TODO 385186274: Parameterize this test.
    @Test
    public void updateAccessibilityServiceStateIfNeeded_alwaysOnServiceOn_hasShortcut_serviceKeepsOn() {
        setupA11yServiceAndShortcutState(
                ALWAYS_ON_SERVICE_COMPONENT_NAME, /* serviceOn= */ true, /* shortcutOn= */ true);

        ShortcutUtils.updateInvisibleToggleAccessibilityServiceEnableState(
                mContext,
                Set.of(ALWAYS_ON_SERVICE_COMPONENT_NAME),
                mDefaultUserId
        );

        assertA11yServiceState(ALWAYS_ON_SERVICE_COMPONENT_NAME, /* enabled= */ true);
    }

    // TODO 385186274: Parameterize this test.
    @Test
    public void updateAccessibilityServiceStateIfNeeded_alwaysOnServiceOff_noShortcuts_serviceKeepsOff() {
        setupA11yServiceAndShortcutState(
                ALWAYS_ON_SERVICE_COMPONENT_NAME, /* serviceOn= */ false, /* shortcutOn= */ false);

        ShortcutUtils.updateInvisibleToggleAccessibilityServiceEnableState(
                mContext,
                Set.of(ALWAYS_ON_SERVICE_COMPONENT_NAME),
                mDefaultUserId
        );

        assertA11yServiceState(ALWAYS_ON_SERVICE_COMPONENT_NAME, /* enabled= */ false);
    }

    // TODO 385186274: Parameterize this test.
    @Test
    public void updateAccessibilityServiceStateIfNeeded_alwaysOnServiceOff_hasShortcuts_serviceTurnsOn() {
        setupA11yServiceAndShortcutState(
                ALWAYS_ON_SERVICE_COMPONENT_NAME, /* serviceOn= */ false, /* shortcutOn= */ true);

        ShortcutUtils.updateInvisibleToggleAccessibilityServiceEnableState(
                mContext,
                Set.of(ALWAYS_ON_SERVICE_COMPONENT_NAME),
                mDefaultUserId
        );

        assertA11yServiceState(ALWAYS_ON_SERVICE_COMPONENT_NAME, /* enabled= */ true);
    }

    // TODO 385186274: Parameterize this test.
    @Test
    public void updateAccessibilityServiceStateIfNeeded_standardA11yServiceOn_noShortcuts_serviceKeepsOn() {
        setupA11yServiceAndShortcutState(
                STANDARD_SERVICE_COMPONENT_NAME, /* serviceOn= */ true, /* shortcutOn= */ false);

        ShortcutUtils.updateInvisibleToggleAccessibilityServiceEnableState(
                mContext,
                Set.of(STANDARD_SERVICE_COMPONENT_NAME),
                mDefaultUserId
        );

        assertA11yServiceState(STANDARD_SERVICE_COMPONENT_NAME, /* enabled= */ true);
    }

    // TODO 385186274: Parameterize this test.
    @Test
    public void updateAccessibilityServiceStateIfNeeded_standardA11yServiceOn_hasShortcuts_serviceKeepsOn() {
        setupA11yServiceAndShortcutState(
                STANDARD_SERVICE_COMPONENT_NAME, /* serviceOn= */ true, /* shortcutOn= */ true);

        ShortcutUtils.updateInvisibleToggleAccessibilityServiceEnableState(
                mContext,
                Set.of(STANDARD_SERVICE_COMPONENT_NAME),
                mDefaultUserId
        );

        assertA11yServiceState(STANDARD_SERVICE_COMPONENT_NAME, /* enabled= */ true);
    }

    // TODO 385186274: Parameterize this test.
    @Test
    public void updateAccessibilityServiceStateIfNeeded_standardA11yServiceOff_noShortcuts_serviceKeepsOff() {
        setupA11yServiceAndShortcutState(
                STANDARD_SERVICE_COMPONENT_NAME, /* serviceOn= */ false, /* shortcutOn= */ false);

        ShortcutUtils.updateInvisibleToggleAccessibilityServiceEnableState(
                mContext,
                Set.of(STANDARD_SERVICE_COMPONENT_NAME),
                mDefaultUserId
        );

        assertA11yServiceState(STANDARD_SERVICE_COMPONENT_NAME, /* enabled= */ false);
    }

    // TODO 385186274: Parameterize this test.
    @Test
    public void updateAccessibilityServiceStateIfNeeded_standardA11yServiceOff_hasShortcuts_serviceKeepsOff() {
        setupA11yServiceAndShortcutState(
                STANDARD_SERVICE_COMPONENT_NAME, /* serviceOn= */ false, /* shortcutOn= */ true);

        ShortcutUtils.updateInvisibleToggleAccessibilityServiceEnableState(
                mContext,
                Set.of(STANDARD_SERVICE_COMPONENT_NAME),
                mDefaultUserId
        );

        assertA11yServiceState(STANDARD_SERVICE_COMPONENT_NAME, /* enabled= */ false);
    }

    @Test
    public void getEnabledShortcutTypes_oneShortcut_returnsExpectedType(
            @TestParameter(valuesProvider = ShortcutTypeValueProvider.class) int shortcutType)
            throws RemoteException {
        clearMockShortcutTypes();
        assertThat(ShortcutUtils.getEnabledShortcutTypes(
                mContext, STANDARD_SERVICE_COMPONENT_NAME)).isEqualTo(DEFAULT);
        mockShortcutType(shortcutType, STANDARD_SERVICE_COMPONENT_NAME);
        assertThat(ShortcutUtils.getEnabledShortcutTypes(
                mContext, STANDARD_SERVICE_COMPONENT_NAME)).isEqualTo(shortcutType);

    }

    @Test
    public void getEnabledShortcutTypes_twoShortcuts_returnsExpectedTypes(
            @TestParameter(valuesProvider = ShortcutTypeValueProvider.class) int shortcutType1,
            @TestParameter(valuesProvider = ShortcutTypeValueProvider.class) int shortcutType2
    ) throws RemoteException {
        if (shortcutType1 == shortcutType2) {
            return;
        }
        clearMockShortcutTypes();
        assertThat(ShortcutUtils.getEnabledShortcutTypes(
                mContext, STANDARD_SERVICE_COMPONENT_NAME)).isEqualTo(DEFAULT);
        mockShortcutType(shortcutType1, STANDARD_SERVICE_COMPONENT_NAME);
        mockShortcutType(shortcutType2, STANDARD_SERVICE_COMPONENT_NAME);
        assertThat(ShortcutUtils.getEnabledShortcutTypes(
                mContext, STANDARD_SERVICE_COMPONENT_NAME)).isEqualTo(
                shortcutType1 | shortcutType2);
    }

    @Test
    public void convertToKey_doesNotThrow(
            @TestParameter(valuesProvider = ShortcutTypeValueProvider.class) int type) {
        ShortcutUtils.convertToKey(type);
    }

    @Test
    public void convertToKey_default_throws() {
        assertThrows(() -> ShortcutUtils.convertToKey(DEFAULT));
    }

    @Test
    public void convertToType_doesNotThrow(
            @TestParameter(valuesProvider = ShortcutSettingValueProvider.class) String setting) {
        ShortcutUtils.convertToType(setting);
    }

    @Test
    public void convertToType_default_throws() {
        assertThrows(() -> ShortcutUtils.convertToType("Foo"));
    }

    @Test
    public void typeToString_doesNotThrow(
            @TestParameter(valuesProvider = ShortcutTypeValueProvider.class) int shortcutType) {
        Assume.assumeFalse("Non user-facing shortcut types are excluded",
                shortcutType == KEY_GESTURE);
        Assume.assumeFalse("Quick Access shortcuts are not user facing",
                shortcutType == QUICK_ACCESS);
        ShortcutUtils.typeToString(shortcutType);
    }

    @Test
    public void typeToString_default_throws() {
        assertThrows(() -> ShortcutUtils.typeToString(DEFAULT));
    }

    @Test
    public void getLabelFromKeyCode_validKeyCode_returnsCorrectLabel() {
        assertThat(ShortcutUtils.getLabelFromKeyCode(mContext, KeyEvent.KEYCODE_M)).isEqualTo("M");
        assertThat(ShortcutUtils.getLabelFromKeyCode(mContext, KeyEvent.KEYCODE_S)).isEqualTo("S");
        assertThat(ShortcutUtils.getLabelFromKeyCode(mContext, KeyEvent.KEYCODE_T)).isEqualTo("T");
        assertThat(ShortcutUtils.getLabelFromKeyCode(mContext, KeyEvent.KEYCODE_V)).isEqualTo("V");
    }

    @Test
    public void getLabelFromKeyCode_invalidKeyCode_returnsNull() {
        assertThat(ShortcutUtils.getLabelFromKeyCode(mContext, KeyEvent.KEYCODE_A)).isNull();
    }

    @Test
    public void getLabelFromKeyCode_keyCodeI_turkishLanguage_returnsCorrectLabel() {
        setupMockedLanguage("tr");
        assertThat(ShortcutUtils.getLabelFromKeyCode(mContext,
                KeyEvent.KEYCODE_I)).isEqualTo("\u0130");
    }

    @Test
    public void getLabelFromKeyCode_keyCodeI_notTurkishLanguage_returnsCorrectLabel() {
        setupMockedLanguage("en");
        assertThat(ShortcutUtils.getLabelFromKeyCode(mContext, KeyEvent.KEYCODE_I)).isEqualTo("I");
    }

    @Test
    public void getKeyCodeLabelFromTarget_magnification_returnsCorrectLabel() {
        setupMockedLanguage("en");
        assertThat(ShortcutUtils.getKeyCodeLabelFromTarget(mContext,
                AccessibilityShortcutController.MAGNIFICATION_CONTROLLER_NAME)).isEqualTo("M");
    }

    @Test
    public void getKeyCodeLabelFromTarget_colorInversion_turkishLanguage_returnsCorrectLabel() {
        setupMockedLanguage("tr");
        assertThat(ShortcutUtils.getKeyCodeLabelFromTarget(mContext,
                AccessibilityShortcutController.COLOR_INVERSION_COMPONENT_NAME.flattenToString()))
                .isEqualTo("\u0130");
    }

    @Test
    public void getKeyCodeLabelFromTarget_screenReader_returnsCorrectLabel() {
        setupMockedLanguage("en");
        setupMockedConfigString(
                com.android.internal.R.string.config_defaultAccessibilityService,
                FAKE_SCREEN_READER_TARGET_NAME);
        final String screenReaderTarget = ShortcutUtils.getScreenReaderTargetName(mContext);
        assertThat(ShortcutUtils.getKeyCodeLabelFromTarget(mContext, screenReaderTarget))
                .isEqualTo("T");
    }

    @Test
    public void getKeyCodeLabelFromTarget_screenReaderComponentName_returnsCorrectLabel() {
        setupMockedLanguage("en");
        setupMockedConfigString(
                com.android.internal.R.string.config_defaultAccessibilityService,
                FAKE_SCREEN_READER_TARGET_NAME);
        final ComponentName componentName = ComponentName.unflattenFromString(
                ShortcutUtils.getScreenReaderTargetName(mContext));
        assertNotNull(componentName);

        final String screenReaderTarget = componentName.flattenToString();
        assertThat(ShortcutUtils.getKeyCodeLabelFromTarget(mContext, screenReaderTarget))
                .isEqualTo("T");
    }

    @Test
    public void getKeyCodeLabelFromTarget_selectToSpeak_returnsCorrectLabel() {
        setupMockedLanguage("en");
        setupMockedConfigString(
                com.android.internal.R.string.config_defaultSelectToSpeakService,
                FAKE_SELECT_TO_SPEAK_TARGET_NAME);
        final String selectToSpeakTarget = ShortcutUtils.getSelectToSpeakTargetName(mContext);
        assertThat(ShortcutUtils.getKeyCodeLabelFromTarget(mContext, selectToSpeakTarget))
                .isEqualTo("S");
    }

    @Test
    public void getKeyCodeLabelFromTarget_voiceAccess_returnsCorrectLabel() {
        setupMockedLanguage("en");
        setupMockedConfigString(
                com.android.internal.R.string.config_defaultVoiceAccessService,
                FAKE_VOICE_ACCESS_TARGET_NAME);
        final String voiceAccessTarget = ShortcutUtils.getVoiceAccessTargetName(mContext);
        assertThat(ShortcutUtils.getKeyCodeLabelFromTarget(mContext, voiceAccessTarget))
                .isEqualTo("V");
    }

    @Test
    public void getTargetFromKeyGestureEvent_magnification_returnsCorrectTarget() {
        KeyGestureEvent event = new KeyGestureEvent.Builder().setKeyGestureType(
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAGNIFICATION).setKeycodes(
                    new int[]{KeyEvent.KEYCODE_M}).build();
        assertThat(ShortcutUtils.getTargetFromKeyGestureEvent(mContext, event))
                .isEqualTo(AccessibilityShortcutController.MAGNIFICATION_CONTROLLER_NAME);
    }

    @Test
    public void getTargetFromKeyGestureEvent_screenReader_returnsCorrectTarget() {
        setupMockedConfigString(
                com.android.internal.R.string.config_defaultAccessibilityService,
                FAKE_SCREEN_READER_TARGET_NAME);
        final ComponentName componentName = ComponentName.unflattenFromString(
                ShortcutUtils.getScreenReaderTargetName(mContext));
        assertNotNull(componentName);

        final String screenReaderTarget = componentName.flattenToString();
        KeyGestureEvent event = new KeyGestureEvent.Builder().setKeyGestureType(
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_SCREEN_READER).setKeycodes(
                    new int[]{KeyEvent.KEYCODE_T}).build();
        assertThat(ShortcutUtils.getTargetFromKeyGestureEvent(mContext, event))
                .isEqualTo(screenReaderTarget);
    }

    @Test
    public void getTargetFromKeyGestureEvent_selectToSpeak_returnsCorrectTarget() {
        setupMockedConfigString(
                com.android.internal.R.string.config_defaultSelectToSpeakService,
                FAKE_SELECT_TO_SPEAK_TARGET_NAME);
        final ComponentName componentName = ComponentName.unflattenFromString(
                ShortcutUtils.getSelectToSpeakTargetName(mContext));
        assertNotNull(componentName);

        final String selectToSpeakTarget = componentName.flattenToString();
        KeyGestureEvent event = new KeyGestureEvent.Builder().setKeyGestureType(
                KeyGestureEvent.KEY_GESTURE_TYPE_ACTIVATE_SELECT_TO_SPEAK).setKeycodes(
                    new int[]{KeyEvent.KEYCODE_S}).build();
        assertThat(ShortcutUtils.getTargetFromKeyGestureEvent(mContext, event))
                .isEqualTo(selectToSpeakTarget);
    }

    @Test
    public void getTargetFromKeyGestureEvent_voiceAccess_returnsCorrectTarget() {
        setupMockedConfigString(
                com.android.internal.R.string.config_defaultVoiceAccessService,
                FAKE_VOICE_ACCESS_TARGET_NAME);
        final ComponentName componentName = ComponentName.unflattenFromString(
                ShortcutUtils.getVoiceAccessTargetName(mContext));
        assertNotNull(componentName);

        final String voiceAccessTarget = componentName.flattenToString();
        KeyGestureEvent event = new KeyGestureEvent.Builder().setKeyGestureType(
                KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_VOICE_ACCESS).setKeycodes(
                    new int[]{KeyEvent.KEYCODE_V}).build();
        assertThat(ShortcutUtils.getTargetFromKeyGestureEvent(mContext, event))
                .isEqualTo(voiceAccessTarget);
    }

    @Test
    public void getTargetFromKeyGestureEvent_unsupportedKeyCode_returnsNull() {
        KeyGestureEvent event = new KeyGestureEvent.Builder().setKeyGestureType(
                KeyGestureEvent.KEY_GESTURE_TYPE_UNSPECIFIED).build();
        assertThat(ShortcutUtils.getTargetFromKeyGestureEvent(mContext, event)).isNull();
    }

    @Test
    public void getScreenReaderTargetName_mockedContext_returnsFakeName() {
        setupMockedConfigString(
                com.android.internal.R.string.config_defaultAccessibilityService,
                FAKE_SCREEN_READER_TARGET_NAME);

        assertThat(ShortcutUtils.getScreenReaderTargetName(mContext))
                .isEqualTo(FAKE_SCREEN_READER_TARGET_NAME);
    }

    @Test
    public void getSelectToSpeakTargetName_mockedContext_returnsFakeName() {
        setupMockedConfigString(
                com.android.internal.R.string.config_defaultSelectToSpeakService,
                FAKE_SELECT_TO_SPEAK_TARGET_NAME);

        assertThat(ShortcutUtils.getSelectToSpeakTargetName(mContext))
                .isEqualTo(FAKE_SELECT_TO_SPEAK_TARGET_NAME);
    }

    @Test
    public void getVoiceAccessTargetName_mockedContext_returnsFakeName() {
        setupMockedConfigString(
                com.android.internal.R.string.config_defaultVoiceAccessService,
                FAKE_VOICE_ACCESS_TARGET_NAME);

        assertThat(ShortcutUtils.getVoiceAccessTargetName(mContext))
                .isEqualTo(FAKE_VOICE_ACCESS_TARGET_NAME);
    }

    private void setupShortcutTargets(Set<String> components, String shortcutSettingsKey) {
        final StringJoiner stringJoiner = new StringJoiner(String.valueOf(SERVICES_SEPARATOR));
        for (String target : components) {
            stringJoiner.add(target);
        }
        Settings.Secure.putStringForUser(
                mContext.getContentResolver(), shortcutSettingsKey,
                stringJoiner.toString(),
                mDefaultUserId);
    }

    private void enableTripleTapShortcutForMagnification(boolean enable) {
        Settings.Secure.putInt(
                mContext.getContentResolver(),
                ACCESSIBILITY_DISPLAY_MAGNIFICATION_ENABLED, enable ? 1 : 0);
    }

    private void setupFakeInstalledA11yServiceInfos() throws RemoteException {
        List<AccessibilityServiceInfo> serviceInfos = List.of(
                TestUtils.createFakeServiceInfo(
                        ALWAYS_ON_SERVICE_PACKAGE_LABEL,
                        ALWAYS_ON_SERVICE_COMPONENT_NAME,
                        SERVICE_NAME_SUMMARY,
                        /* isAlwaysOnService*/ true),
                TestUtils.createFakeServiceInfo(
                        STANDARD_SERVICE_PACKAGE_LABEL,
                        STANDARD_SERVICE_COMPONENT_NAME,
                        SERVICE_NAME_SUMMARY,
                        /* isAlwaysOnService*/ false)
        );
        when(mAccessibilityManagerService.getInstalledAccessibilityServiceList(anyInt()))
                .thenReturn(new ParceledListSlice<>(serviceInfos));
    }

    private void setupA11yServiceAndShortcutState(
            String a11yServiceComponentName, boolean serviceOn, boolean shortcutOn) {
        setupA11yServiceAndShortcutStateForUser(
                a11yServiceComponentName, serviceOn, shortcutOn, mDefaultUserId);
    }

    private void setupA11yServiceAndShortcutStateForUser(
            String a11yServiceComponentName, boolean serviceOn,
            boolean shortcutOn, @UserIdInt int userId) {
        enableA11yServiceForUser(a11yServiceComponentName, serviceOn, userId);
        addShortcutForA11yServiceForUser(a11yServiceComponentName, shortcutOn, userId);
    }

    private void assertA11yServiceState(String a11yServiceComponentName, boolean enabled) {
        assertA11yServiceStateForUser(a11yServiceComponentName, enabled, mDefaultUserId);
    }

    private void assertA11yServiceStateForUser(
            String a11yServiceComponentName, boolean enabled, @UserIdInt int userId) {
        if (enabled) {
            assertThat(
                    Settings.Secure.getStringForUser(
                            mContext.getContentResolver(),
                            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                            userId)
            ).contains(a11yServiceComponentName);
        } else {
            assertThat(
                    Settings.Secure.getStringForUser(
                            mContext.getContentResolver(),
                            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                            userId)
            ).doesNotContain(a11yServiceComponentName);
        }
    }

    private void enableA11yServiceForUser(
            String a11yServiceComponentName, boolean enable, @UserIdInt int userId) {
        Settings.Secure.putStringForUser(
                mContext.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                enable ? a11yServiceComponentName : "",
                userId);
    }

    private void addShortcutForA11yServiceForUser(
            String a11yServiceComponentName, boolean add, @UserIdInt int userId) {
        Settings.Secure.putStringForUser(
                mContext.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE,
                add ? a11yServiceComponentName : "",
                userId);
    }

    private void clearMockShortcutTypes() throws RemoteException {
        for (int shortcutType : ShortcutConstants.USER_SHORTCUT_TYPES) {
            when(mAccessibilityManagerService
                    .getAccessibilityShortcutTargets(
                            eq(shortcutType), anyInt())).thenReturn(List.of());
        }
    }

    private void mockShortcutType(int shortcutType, String componentName)
            throws RemoteException {
        when(mAccessibilityManagerService.getAccessibilityShortcutTargets(
                eq(shortcutType), anyInt())).thenReturn(List.of(componentName));
    }

    static final class ShortcutTypeValueProvider implements
            TestParameter.TestParameterValuesProvider {
        @Override
        public List<Integer> provideValues() {
            List<Integer> values = new ArrayList<>();
            for (int shortcutType: USER_SHORTCUT_TYPES) {
                values.add(shortcutType);
            }
            return values;
        }
    }

    static final class ShortcutSettingValueProvider implements
            TestParameter.TestParameterValuesProvider {
        @Override
        public List<String> provideValues() {
            List<String> values = new ArrayList<>();
            Collections.addAll(values, ShortcutConstants.MAGNIFICATION_SHORTCUT_SETTINGS);
            return values;
        }
    }

    private void setupMockedConfigString(int resId, String value) {
        doReturn(mMockResources).when(mContext).getResources();
        when(mMockResources.getString(eq(resId))).thenReturn(value);
    }

    private void setupMockedLanguage(String language) {
        final android.content.res.Configuration mockConfig =
                new android.content.res.Configuration();
        mockConfig.setLocales(android.os.LocaleList.forLanguageTags(language));
        doReturn(mMockResources).when(mContext).getResources();
        when(mMockResources.getConfiguration()).thenReturn(mockConfig);
    }
}
