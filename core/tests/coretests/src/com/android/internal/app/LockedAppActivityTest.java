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

package com.android.internal.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static com.google.common.truth.Truth.assertThat;

import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AppLockInternal;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.VersionedPackage;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.security.Flags;
import android.view.Display;
import android.view.View;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.android.internal.R;
import com.android.server.LocalServices;

import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(TestParameterInjector.class)
public class LockedAppActivityTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private enum ActivityMode {
        INTERCEPT,
        LOCKED_TASK,
        UNINSTALL
    }

    private static final String SYSTEM_PACKAGE_NAME = "android";
    private static final String TEST_PACKAGE_NAME = "com.android.test";
    private static final String TEST_APP_LABEL = "Test App";
    private static final int TEST_USER_ID = 1;

    private final Context mContext = ApplicationProvider.getApplicationContext();

    private ApplicationInfo mApplicationInfo;
    private AutoCloseable mMockCloseable;
    private ArgumentCaptor<AppLockInternal.PackageLockedStateListener> mPackageLockedListenerCaptor;
    private ArgumentCaptor<OnBackInvokedCallback> mBackInvokedCallbackCaptor;
    private TestLockedAppActivityInjector mTestInjector;

    @Mock
    private AppLockInternal mAppLockInternal;
    @Mock
    private BiometricPrompt mBiometricPrompt;
    @Mock
    private BiometricPrompt.Builder mBiometricPromptBuilder;
    @Mock
    private Drawable mDrawable;
    @Mock
    private IntentSender mIntentSender;
    @Mock
    private OnBackInvokedDispatcher mOnBackInvokedDispatcher;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private PackageInstaller mPackageInstaller;
    @Mock
    private IntentSender mUninstallStatusReceiver;

    @Before
    public void setUp() throws Exception {
        mMockCloseable = MockitoAnnotations.openMocks(this);

        mPackageLockedListenerCaptor = ArgumentCaptor.forClass(
                AppLockInternal.PackageLockedStateListener.class);
        mBackInvokedCallbackCaptor = ArgumentCaptor.forClass(OnBackInvokedCallback.class);

        mApplicationInfo = spy(new ApplicationInfo());
        when(mPackageManager.getApplicationIcon(eq(TEST_PACKAGE_NAME))).thenReturn(mDrawable);
        when(mPackageManager.getApplicationInfo(eq(TEST_PACKAGE_NAME),
                anyInt())).thenReturn(mApplicationInfo);
        when(mApplicationInfo.loadLabel(mPackageManager)).thenReturn(TEST_APP_LABEL);

        // Mock PackageInstaller for uninstall tests.
        when(mPackageManager.getPackageInstaller()).thenReturn(mPackageInstaller);

        // Default to the package being locked. Tests that need a different state can override.
        when(mAppLockInternal.isPackageLocked(TEST_PACKAGE_NAME, TEST_USER_ID)).thenReturn(true);

        // Mock the AppLockInternal service to control its behavior in tests.
        LocalServices.removeServiceForTest(AppLockInternal.class);
        LocalServices.addService(AppLockInternal.class, mAppLockInternal);

        // Mock the BiometricPrompt and its builder to prevent the actual prompt from showing.
        when(mBiometricPromptBuilder.setTitle(any())).thenReturn(mBiometricPromptBuilder);
        when(mBiometricPromptBuilder.setDescription(any())).thenReturn(mBiometricPromptBuilder);
        when(mBiometricPromptBuilder.setLogoDescription(any())).thenReturn(mBiometricPromptBuilder);
        when(mBiometricPromptBuilder.setAllowedAuthenticators(anyInt())).thenReturn(
                mBiometricPromptBuilder);
        when(mBiometricPromptBuilder.build()).thenReturn(mBiometricPrompt);

        // Set up the LockedAppActivity with the test injector.
        mTestInjector = new TestLockedAppActivityInjector();
        LockedAppActivity.setInjectorForTesting(mTestInjector);
    }

    @After
    public void tearDown() throws Exception {
        LocalServices.removeServiceForTest(AppLockInternal.class);
        LockedAppActivity.setInjectorForTesting(null);
        mMockCloseable.close();
    }

    @Test
    public void createIntent_returnsCorrectLockedAppActivityIntent() {
        Intent intent = LockedAppActivity.createLockedAppActivityIntent(TEST_PACKAGE_NAME,
                TEST_USER_ID, mIntentSender);
        ComponentName expectedComponentName = new ComponentName(SYSTEM_PACKAGE_NAME,
                LockedAppActivity.class.getName());

        assertThat(intent).isNotNull();
        assertThat(intent.getComponent()).isEqualTo(expectedComponentName);
        assertThat(intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)).isEqualTo(TEST_PACKAGE_NAME);
        assertThat(intent.hasExtra(Intent.EXTRA_USER_ID)).isTrue();
        assertThat(intent.getIntExtra(Intent.EXTRA_USER_ID, -1)).isEqualTo(TEST_USER_ID);
        assertThat(intent.getParcelableExtra(Intent.EXTRA_INTENT,
                IntentSender.class)).isEqualTo(mIntentSender);
        assertThat(intent.getFlags()).isEqualTo(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    }

    @Test
    public void createIntent_withNullPackageName_throws() {
        assertThrows(NullPointerException.class, () ->
                LockedAppActivity.createLockedAppActivityIntent(null, TEST_USER_ID, mIntentSender));
    }

    @Test
    public void createIntent_withNullIntentSender_returnsCorrectLockedAppActivityIntent() {
        Intent intent = LockedAppActivity.createLockedAppActivityIntent(TEST_PACKAGE_NAME,
                TEST_USER_ID, /* target= */ null);
        ComponentName expectedComponentName = new ComponentName(SYSTEM_PACKAGE_NAME,
                LockedAppActivity.class.getName());

        assertThat(intent).isNotNull();
        assertThat(intent.getComponent()).isEqualTo(expectedComponentName);
        assertThat(intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)).isEqualTo(TEST_PACKAGE_NAME);
        assertThat(intent.hasExtra(Intent.EXTRA_USER_ID)).isTrue();
        assertThat(intent.getIntExtra(Intent.EXTRA_USER_ID, -1)).isEqualTo(TEST_USER_ID);
        assertThat(intent.getParcelableExtra(Intent.EXTRA_INTENT, IntentSender.class)).isNull();
        assertThat(intent.getFlags()).isEqualTo(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_withoutIntentExtraPackageName_finishes(
            @TestParameter ActivityMode activityMode) {
        Intent intent = createBaseTestLockedAppActivityIntent();
        intent.putExtra(Intent.EXTRA_USER_ID, TEST_USER_ID);
        if (activityMode == ActivityMode.INTERCEPT) {
            intent.putExtra(Intent.EXTRA_INTENT, mIntentSender);
        }

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_withoutIntentExtraUserId_finishes(
            @TestParameter ActivityMode activityMode) {
        Intent intent = createBaseTestLockedAppActivityIntent();
        intent.putExtra(Intent.EXTRA_PACKAGE_NAME, TEST_PACKAGE_NAME);
        if (activityMode == ActivityMode.INTERCEPT) {
            intent.putExtra(Intent.EXTRA_INTENT, mIntentSender);
        }

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_inUninstallMode_withoutRequiredExtras_finishes() {
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.UNINSTALL);
        intent.removeExtra(LockedAppActivity.EXTRA_VERSIONED_PACKAGE);
        intent.removeExtra(LockedAppActivity.EXTRA_STATUS_RECEIVER);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
            verify(mPackageInstaller, never()).uninstall(any(VersionedPackage.class), anyInt(),
                    any());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_inTranslucentModes_setsCorrectThemeAndIsTranslucent(
            @TestParameter(value = {"INTERCEPT", "UNINSTALL"}) ActivityMode activityMode) {
        Intent intent = createTestLockedAppActivityIntent(activityMode);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            assertThat(mTestInjector.getThemeResId()).isEqualTo(
                    android.R.style.Theme_DeviceDefault_Panel);
            assertThat(mTestInjector.isTranslucent()).isTrue();
            assertThat(mTestInjector.getContentViewId()).isEqualTo(0);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void onConfigurationChanged_inTranslucentModes_retainsThemeAndTranslucency(
            @TestParameter(value = {"INTERCEPT", "UNINSTALL"}) ActivityMode activityMode) {
        Intent intent = createTestLockedAppActivityIntent(activityMode);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                activity.onConfigurationChanged(new Configuration());

                assertThat(mTestInjector.getThemeResId()).isEqualTo(
                        android.R.style.Theme_DeviceDefault_Panel);
                assertThat(mTestInjector.isTranslucent()).isTrue();
                assertThat(mTestInjector.getContentViewId()).isEqualTo(0);
            });
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_inLockedTaskMode_setsCorrectThemeAndContentView() {
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.LOCKED_TASK);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            assertThat(mTestInjector.getThemeResId()).isEqualTo(
                    android.R.style.Theme_DeviceDefault_NoActionBar);
            assertThat(mTestInjector.getContentViewId()).isEqualTo(
                    R.layout.locked_app_activity_layout);
            assertThat(mTestInjector.isTranslucent()).isFalse();
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void onConfigurationChanged_inLockedTaskMode_retainsThemeAndContentView() {
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.LOCKED_TASK);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                mTestInjector.resetSetContentViewCount();

                activity.onConfigurationChanged(new Configuration());

                assertThat(mTestInjector.getThemeResId()).isEqualTo(
                        android.R.style.Theme_DeviceDefault_NoActionBar);
                assertThat(mTestInjector.getContentViewId()).isEqualTo(
                        R.layout.locked_app_activity_layout);
                assertThat(mTestInjector.isTranslucent()).isFalse();
                assertThat(mTestInjector.getSetContentViewCount()).isEqualTo(1);
            });
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_inLockedTaskMode_onExternalDisplay_showsExternalDisplayMessage() {
        mTestInjector.setDisplayId(Display.DEFAULT_DISPLAY + 1);
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.LOCKED_TASK);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.locked_app_activity_external_display_message_id))
                    .check(matches(isDisplayed()));
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_inLockedTaskMode_onDefaultDisplay_hidesExternalDisplayMessage() {
        mTestInjector.setDisplayId(Display.DEFAULT_DISPLAY);
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.LOCKED_TASK);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.locked_app_activity_external_display_message_id))
                    .check(matches(not(isDisplayed())));
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void onConfigurationChanged_inLockedTaskMode_updatesExternalDisplayMessageVisibility() {
        mTestInjector.setDisplayId(Display.DEFAULT_DISPLAY);
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.LOCKED_TASK);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.locked_app_activity_external_display_message_id))
                    .check(matches(not(isDisplayed())));

            mTestInjector.setDisplayId(Display.DEFAULT_DISPLAY + 1);

            scenario.onActivity(activity -> activity.onConfigurationChanged(new Configuration()));

            onView(withId(R.id.locked_app_activity_external_display_message_id))
                    .check(matches(isDisplayed()));
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_withPackageUnlocked_finishes(
            @TestParameter(value = {"INTERCEPT", "LOCKED_TASK"}) ActivityMode activityMode) {
        Intent intent = createTestLockedAppActivityIntent(activityMode);
        when(mAppLockInternal.isPackageLocked(TEST_PACKAGE_NAME, TEST_USER_ID)).thenReturn(false);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_inUninstallMode_withPackageUnlocked_doesNotFinish() {
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.UNINSTALL);

        when(mAppLockInternal.isPackageLocked(TEST_PACKAGE_NAME, TEST_USER_ID)).thenReturn(false);
        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            assertThat(scenario.getState()).isNotEqualTo(Lifecycle.State.DESTROYED);
            verify(mBiometricPrompt).authenticate(any(), any(), any());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_withValidIntent_setsCorrectBiometricPromptContent(
            @TestParameter ActivityMode activityMode) {
        Intent intent = createTestLockedAppActivityIntent(activityMode);
        final String expectedDescription = mContext.getString(
                R.string.locked_app_biometric_prompt_description,
                TEST_APP_LABEL);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            verifyBiometricPromptDisplayed(expectedDescription, /* expectedLogoDescription= */
                    TEST_APP_LABEL);
            assertThat(scenario.getState()).isNotEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_whenPackageLabelIsPackageName_setsBiometricPromptWithPackageName(
            @TestParameter ActivityMode activityMode) {
        Intent intent = createTestLockedAppActivityIntent(activityMode);
        when(mApplicationInfo.loadLabel(mPackageManager)).thenReturn(TEST_PACKAGE_NAME);
        final String expectedDescription = mContext.getString(
                R.string.locked_app_biometric_prompt_description,
                TEST_PACKAGE_NAME);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            verifyBiometricPromptDisplayed(expectedDescription, /* expectedLogoDescription= */
                    TEST_PACKAGE_NAME);
            assertThat(scenario.getState()).isNotEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_withAppLogo_setsBiometricPromptLogo(
            @TestParameter ActivityMode activityMode) {
        Intent intent = createTestLockedAppActivityIntent(activityMode);
        final Bitmap expectedBitmap = LockedAppActivity.convertDrawableToBitmap(mDrawable);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            ArgumentCaptor<Bitmap> bitmapCaptor = ArgumentCaptor.forClass(Bitmap.class);
            verify(mBiometricPromptBuilder).setLogoBitmap(bitmapCaptor.capture());
            final Bitmap capturedBitmap = bitmapCaptor.getValue();
            assertThat(capturedBitmap.sameAs(expectedBitmap)).isTrue();
            verify(mBiometricPrompt).authenticate(any(), any(), any());
            assertThat(scenario.getState()).isNotEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_withoutAppLogo_doesNotSetBiometricPromptLogo(
            @TestParameter ActivityMode activityMode)
            throws PackageManager.NameNotFoundException {
        when(mPackageManager.getApplicationIcon(TEST_PACKAGE_NAME)).thenReturn(null);
        Intent intent = createTestLockedAppActivityIntent(activityMode);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            verify(mBiometricPromptBuilder, never()).setLogoBitmap(any());
            verify(mBiometricPrompt).authenticate(any(), any(), any());
            assertThat(scenario.getState()).isNotEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @DisableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_withValidIntentAndAppLockFlagsDisabled_finishes(
            @TestParameter ActivityMode activityMode) {
        Intent intent = createTestLockedAppActivityIntent(activityMode);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void authSucceeded_inInterceptMode_unlocksPackageSendsTargetIntentAndFinishes() {
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.INTERCEPT);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            // Mock successful authentication.
            captureAuthenticationCallback().onAuthenticationSucceeded(
                    mock(BiometricPrompt.AuthenticationResult.class));

            verify(mAppLockInternal).setAppLockEnabledPackageSuccessfullyAuthenticated(
                    TEST_PACKAGE_NAME, TEST_USER_ID);
            assertThat(mTestInjector.wasTargetIntentSent()).isTrue();
            scenario.onActivity(activity -> assertThat(activity.isFinishing()).isTrue());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void authSucceeded_inLockedTaskMode_finishesWithUnlockingWithoutSendingIntent() {
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.LOCKED_TASK);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            // Mock successful authentication.
            captureAuthenticationCallback().onAuthenticationSucceeded(
                    mock(BiometricPrompt.AuthenticationResult.class));

            verify(mAppLockInternal).setAppLockEnabledPackageSuccessfullyAuthenticated(
                    TEST_PACKAGE_NAME, TEST_USER_ID);
            assertThat(mTestInjector.wasTargetIntentSent()).isFalse();
            scenario.onActivity(activity -> assertThat(activity.isFinishing()).isTrue());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void authSucceeded_inUninstallMode_triggersUninstallAndFinishes() {
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.UNINSTALL);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            captureAuthenticationCallback().onAuthenticationSucceeded(
                    mock(BiometricPrompt.AuthenticationResult.class));

            verify(mPackageInstaller).uninstall(
                    eq(new VersionedPackage(TEST_PACKAGE_NAME, /* versionCode= */ 1)),
                    /* flags= */ eq(0),
                    eq(mUninstallStatusReceiver)
            );
            verify(mAppLockInternal, never()).setAppLockEnabledPackageSuccessfullyAuthenticated(
                    anyString(), anyInt());
            scenario.onActivity(activity -> assertThat(activity.isFinishing()).isTrue());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void completeUnlockAndFinish_calledMultipleTimes_sendsTargetIntentOnlyOnce() {
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.INTERCEPT);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            // Trigger authentication success.
            captureAuthenticationCallback().onAuthenticationSucceeded(
                    mock(BiometricPrompt.AuthenticationResult.class));

            // Manually trigger the package locked state listener to simulate a race condition
            // or another event that calls completeUnlockAndFinish().
            verify(mAppLockInternal).registerPackageLockedStateListener(
                    mPackageLockedListenerCaptor.capture());
            AppLockInternal.PackageLockedStateListener listener =
                    mPackageLockedListenerCaptor.getValue();
            listener.onPackageLockedStateChanged(TEST_PACKAGE_NAME, TEST_USER_ID, false);

            assertThat(mTestInjector.getTargetIntentSentCount()).isEqualTo(1);
            scenario.onActivity(activity -> assertThat(activity.isFinishing()).isTrue());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void authError_inInterceptMode_finishesWithoutUnlockingAndSendingIntent() {
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.INTERCEPT);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            // Mock an authentication error.
            captureAuthenticationCallback().onAuthenticationError(
                    BiometricPrompt.BIOMETRIC_ERROR_CANCELED, "User canceled");

            verifyPackageNotUnlockedAndDoesNotSendTargetIntent();
            scenario.onActivity(activity -> assertThat(activity.isFinishing()).isTrue());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void authError_inLockedTaskMode_doesNotFinishDoesNotUnlockAndDoesNotReTriggerPrompt() {
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.LOCKED_TASK);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            // Mock an authentication error.
            captureAuthenticationCallback().onAuthenticationError(
                    BiometricPrompt.BIOMETRIC_ERROR_CANCELED, "User canceled");

            // Verify that authenticate was only called once (during initial launch).
            verify(mBiometricPrompt, times(1)).authenticate(any(), any(), any());
            verifyPackageNotUnlockedAndDoesNotSendTargetIntent();
            scenario.onActivity(activity -> assertThat(activity.isFinishing()).isFalse());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void authError_inUninstallMode_sendsAbortAndFinishes() throws
            IntentSender.SendIntentException {
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.UNINSTALL);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            captureAuthenticationCallback().onAuthenticationError(
                    BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED, "User canceled");
            verify(mUninstallStatusReceiver).sendIntent(
                    /* context= */ any(),
                    /* code= */ eq(0),
                    argThat(result -> result.getIntExtra(PackageInstaller.EXTRA_STATUS, 0)
                            == PackageInstaller.STATUS_FAILURE_ABORTED),
                    /* onFinished= */ any(),
                    /* handler= */ any());
            scenario.onActivity(activity -> assertThat(activity.isFinishing()).isTrue());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void injector_sendTargetIntent_setsCorrectActivityOptions() throws Exception {
        final LockedAppActivity.Injector injector = new LockedAppActivity.Injector();
        final Activity mockActivity = mock(Activity.class);
        final IntentSender mockIntentSender = mock(IntentSender.class);

        injector.sendTargetIntent(mockActivity, mockIntentSender);

        ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);
        verify(mockActivity).startIntentSenderForResult(
                eq(mockIntentSender),
                /* requestCode= */ eq(-1),
                /* fillInIntent= */ eq(null),
                /* flagsMask= */ eq(0),
                /* flagsValues= */ eq(0),
                /* extraFlags= */ eq(0),
                bundleCaptor.capture());

        final ActivityOptions options = ActivityOptions.fromBundle(bundleCaptor.getValue());
        assertThat(options.getPendingIntentBackgroundActivityStartMode())
                .isEqualTo(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE);
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_withNullPackageLabel_finishes(
            @TestParameter ActivityMode activityMode) {
        Intent intent = createTestLockedAppActivityIntent(activityMode);
        when(mApplicationInfo.loadLabel(mPackageManager)).thenReturn(null);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void clickBackground_inLockedTaskMode_showsPrompt() {
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.LOCKED_TASK);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            // Mock an authentication error to clear the first prompt.
            captureAuthenticationCallback().onAuthenticationError(
                    BiometricPrompt.BIOMETRIC_ERROR_CANCELED, "User canceled");

            // Click the background.
            onView(withId(android.R.id.content)).perform(click());

            // Verify that authenticate was called a second time.
            verify(mBiometricPrompt, times(2)).authenticate(any(), any(), any());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void onResume_inLockedTaskMode_reShowsPromptAfterDismissal() {
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.LOCKED_TASK);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            // Mock an authentication error to clear the first prompt.
            captureAuthenticationCallback().onAuthenticationError(
                    BiometricPrompt.BIOMETRIC_ERROR_CANCELED, "User canceled");

            // Simulate leaving and returning to the activity.
            scenario.moveToState(Lifecycle.State.STARTED);
            scenario.moveToState(Lifecycle.State.RESUMED);

            // Verify that authenticate was called a second time.
            verify(mBiometricPrompt, times(2)).authenticate(any(), any(), any());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void onResume_inLockedTaskMode_withoutWindowFocus_doesNotShowPrompt() {
        mTestInjector.setHasWindowFocus(false);
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.LOCKED_TASK);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            // Initial launch triggers onResume, but focus is false.
            verify(mBiometricPrompt, never()).authenticate(any(), any(), any());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void onWindowFocusChanged_inLockedTaskMode_afterResumeWithoutFocus_showsPrompt() {
        mTestInjector.setHasWindowFocus(false);
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.LOCKED_TASK);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            // Verify it hasn't shown yet.
            verify(mBiometricPrompt, never()).authenticate(any(), any(), any());

            // Simulate gaining focus.
            mTestInjector.setHasWindowFocus(true);
            scenario.onActivity(activity -> activity.onWindowFocusChanged(true));

            // Now it should show.
            verify(mBiometricPrompt, times(1)).authenticate(any(), any(), any());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void onWindowFocusChanged_inLockedTaskMode_withoutRecentResume_doesNotShowPrompt() {
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.LOCKED_TASK);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            // Initial show during launch.
            verify(mBiometricPrompt, times(1)).authenticate(any(), any(), any());

            // Clear the first prompt.
            captureAuthenticationCallback().onAuthenticationError(
                    BiometricPrompt.BIOMETRIC_ERROR_CANCELED, "User canceled");

            // Simulate losing and gaining focus (like a notification shade).
            // onResume is NOT called during this.
            scenario.onActivity(activity -> {
                activity.onWindowFocusChanged(false);
                activity.onWindowFocusChanged(true);
            });

            // Should still only have been called once (the initial one).
            verify(mBiometricPrompt, times(1)).authenticate(any(), any(), any());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void onConfigurationChanged_inLockedTaskMode_retainsClickListener() {
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.LOCKED_TASK);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            // Mock an authentication error to clear the first prompt.
            captureAuthenticationCallback().onAuthenticationError(
                    BiometricPrompt.BIOMETRIC_ERROR_CANCELED, "User canceled");

            scenario.onActivity(activity -> activity.onConfigurationChanged(new Configuration()));

            // Click the background.
            onView(withId(android.R.id.content)).perform(click());

            // Verify that authenticate was called a second time.
            verify(mBiometricPrompt, times(2)).authenticate(any(), any(), any());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_inLockedTaskMode_withMissingRootView_finishes() {
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.LOCKED_TASK);
        mTestInjector.setReturnNullForRootView(true);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void onWindowFocusChanged_whenPackageUnlockedWhileNotFocused_finishes(
            @TestParameter(value = {"INTERCEPT", "LOCKED_TASK"}) ActivityMode activityMode)
            throws Exception {
        Intent intent = createTestLockedAppActivityIntent(activityMode);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            // Simulate losing focus.
            scenario.onActivity(activity -> activity.onWindowFocusChanged(false));

            // Mock that the package is now unlocked.
            when(mAppLockInternal.isPackageLocked(TEST_PACKAGE_NAME, TEST_USER_ID)).thenReturn(
                    false);

            // Simulate regaining focus.
            scenario.onActivity(activity -> activity.onWindowFocusChanged(true));

            scenario.onActivity(activity -> assertThat(activity.isFinishing()).isTrue());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void onWindowFocusChanged_afterAuthError_doesNotReTriggerPrompt() {
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.LOCKED_TASK);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            // Mock an authentication error.
            captureAuthenticationCallback().onAuthenticationError(
                    BiometricPrompt.BIOMETRIC_ERROR_CANCELED, "User canceled");

            // Simulate losing focus.
            scenario.onActivity(activity -> activity.onWindowFocusChanged(false));

            // Simulate gaining focus (e.g., after the prompt dialog disappears).
            scenario.onActivity(activity -> activity.onWindowFocusChanged(true));

            // Verify that authenticate was still only called once.
            verify(mBiometricPrompt, times(1)).authenticate(any(), any(), any());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void onWindowFocusChanged_inUninstallMode_whenPackageUnlocked_doesNotFinish() {
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.UNINSTALL);

        when(mAppLockInternal.isPackageLocked(TEST_PACKAGE_NAME, TEST_USER_ID)).thenReturn(true);
        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            // Simulate losing focus.
            scenario.onActivity(activity -> activity.onWindowFocusChanged(false));

            // Mock that the package is now unlocked.
            when(mAppLockInternal.isPackageLocked(TEST_PACKAGE_NAME, TEST_USER_ID)).thenReturn(
                    false);

            // Simulate regaining focus.
            scenario.onActivity(activity -> activity.onWindowFocusChanged(true));

            scenario.onActivity(activity -> assertThat(activity.isFinishing()).isFalse());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void
            packageUnlockedViaListener_inInterceptMode_finishesWithoutUnlockingWithSendingIntent() {
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.INTERCEPT);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            // Capture the locked state listener and mock the package being unlocked.
            verify(mAppLockInternal).registerPackageLockedStateListener(
                    mPackageLockedListenerCaptor.capture());
            AppLockInternal.PackageLockedStateListener listener =
                    mPackageLockedListenerCaptor.getValue();
            listener.onPackageLockedStateChanged(TEST_PACKAGE_NAME, TEST_USER_ID, false);

            verify(mAppLockInternal, never()).setAppLockEnabledPackageSuccessfullyAuthenticated(
                    TEST_PACKAGE_NAME, TEST_USER_ID);
            assertThat(mTestInjector.wasTargetIntentSent()).isTrue();
            scenario.onActivity(activity -> assertThat(activity.isFinishing()).isTrue());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void
            packageUnlockedViaListener_inLockedTaskMode_finishesWithoutUnlockingAndSendingIntent() {
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.LOCKED_TASK);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            // Capture the locked state listener and mock the package being unlocked.
            verify(mAppLockInternal).registerPackageLockedStateListener(
                    mPackageLockedListenerCaptor.capture());
            AppLockInternal.PackageLockedStateListener listener =
                    mPackageLockedListenerCaptor.getValue();
            listener.onPackageLockedStateChanged(TEST_PACKAGE_NAME, TEST_USER_ID, false);

            verifyPackageNotUnlockedAndDoesNotSendTargetIntent();
            scenario.onActivity(activity -> assertThat(activity.isFinishing()).isTrue());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void differentPackageUnlockedViaListener_doesNotFinish(
            @TestParameter(value = {"INTERCEPT", "LOCKED_TASK"}) ActivityMode activityMode) {
        final Intent intent = createTestLockedAppActivityIntent(activityMode);
        final String otherPackage = TEST_PACKAGE_NAME + "other";

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            // Capture the locked state listener and mock a different package being unlocked.
            verify(mAppLockInternal).registerPackageLockedStateListener(
                    mPackageLockedListenerCaptor.capture());
            AppLockInternal.PackageLockedStateListener listener =
                    mPackageLockedListenerCaptor.getValue();
            listener.onPackageLockedStateChanged(otherPackage, TEST_USER_ID, false);

            verifyPackageNotUnlockedAndDoesNotSendTargetIntent();
            scenario.onActivity(activity -> assertThat(activity.isFinishing()).isFalse());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void onBackInvoked_inLockedTaskMode_doesNotFinishActivity() {
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.LOCKED_TASK);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            verify(mOnBackInvokedDispatcher).registerOnBackInvokedCallback(
                    eq(OnBackInvokedDispatcher.PRIORITY_DEFAULT),
                    mBackInvokedCallbackCaptor.capture());

            // Manually trigger the onBackInvoked callback.
            mBackInvokedCallbackCaptor.getValue().onBackInvoked();

            scenario.onActivity(activity -> assertThat(activity.isFinishing()).isFalse());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void onBackInvoked_inTranslucentModes_callbackNotRegistered(
            @TestParameter(value = {"INTERCEPT", "UNINSTALL"}) ActivityMode activityMode) {
        Intent intent = createTestLockedAppActivityIntent(activityMode);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            verify(mOnBackInvokedDispatcher, never()).registerOnBackInvokedCallback(
                    eq(OnBackInvokedDispatcher.PRIORITY_DEFAULT),
                    mBackInvokedCallbackCaptor.capture());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void activityDestroyed_inLockedTaskMode_unregistersAllListeners() {
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.LOCKED_TASK);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            verify(mAppLockInternal).registerPackageLockedStateListener(
                    mPackageLockedListenerCaptor.capture());
            verify(mOnBackInvokedDispatcher).registerOnBackInvokedCallback(
                    eq(OnBackInvokedDispatcher.PRIORITY_DEFAULT),
                    mBackInvokedCallbackCaptor.capture());

            // Destroy the activity.
            scenario.moveToState(Lifecycle.State.DESTROYED);

            verify(mAppLockInternal).unregisterPackageLockedStateListener(
                    mPackageLockedListenerCaptor.getValue());
            verify(mOnBackInvokedDispatcher).unregisterOnBackInvokedCallback(
                    mBackInvokedCallbackCaptor.getValue());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void activityDestroyed_inInterceptMode_unregistersPackageListener() {
        Intent intent = createTestLockedAppActivityIntent(ActivityMode.INTERCEPT);

        try (ActivityScenario<LockedAppActivity> scenario = ActivityScenario.launch(intent)) {
            verify(mAppLockInternal).registerPackageLockedStateListener(
                    mPackageLockedListenerCaptor.capture());
            verify(mOnBackInvokedDispatcher, never()).registerOnBackInvokedCallback(
                    eq(OnBackInvokedDispatcher.PRIORITY_DEFAULT),
                    mBackInvokedCallbackCaptor.capture());

            // Destroy the activity.
            scenario.moveToState(Lifecycle.State.DESTROYED);

            verify(mAppLockInternal).unregisterPackageLockedStateListener(
                    mPackageLockedListenerCaptor.getValue());
            // Activity#onDestroy unregisters two OnBackInvokedCallback instances.
            verify(mOnBackInvokedDispatcher, never()).unregisterOnBackInvokedCallback(
                    mBackInvokedCallbackCaptor.capture());
        }
    }

    private void verifyPackageNotUnlockedAndDoesNotSendTargetIntent() {
        verify(mAppLockInternal, never()).setAppLockEnabledPackageSuccessfullyAuthenticated(
                eq(TEST_PACKAGE_NAME), eq(TEST_USER_ID));
        assertThat(mTestInjector.wasTargetIntentSent()).isFalse();
    }

    private BiometricPrompt.AuthenticationCallback captureAuthenticationCallback() {
        ArgumentCaptor<BiometricPrompt.AuthenticationCallback> callbackCaptor =
                ArgumentCaptor.forClass(BiometricPrompt.AuthenticationCallback.class);
        verify(mBiometricPrompt).authenticate(any(), any(), callbackCaptor.capture());
        return callbackCaptor.getValue();
    }

    private void verifyBiometricPromptDisplayed(String expectedDescription,
            String expectedLogoDescription) {
        final String expectedTitle = mContext.getString(R.string.biometric_dialog_default_title);

        verify(mBiometricPromptBuilder).setTitle(expectedTitle);
        verify(mBiometricPromptBuilder).setDescription(expectedDescription);
        verify(mBiometricPromptBuilder).setLogoDescription(expectedLogoDescription);
        verify(mBiometricPromptBuilder).setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        verify(mBiometricPromptBuilder).build();
        verify(mBiometricPrompt).authenticate(any(), any(), any());
    }

    private Intent createBaseTestLockedAppActivityIntent() {
        return new Intent(mContext, LockedAppActivity.class);
    }

    private Intent createTestLockedAppActivityIntent(ActivityMode activityMode) {
        final Intent intent = createBaseTestLockedAppActivityIntent()
                .putExtra(Intent.EXTRA_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(Intent.EXTRA_USER_ID, TEST_USER_ID)
                .setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        if (activityMode == ActivityMode.UNINSTALL) {
            intent.putExtra(LockedAppActivity.EXTRA_IS_UNINSTALL, true)
                    .putExtra(LockedAppActivity.EXTRA_VERSIONED_PACKAGE,
                            new VersionedPackage(TEST_PACKAGE_NAME, /* versionCode= */ 1))
                    .putExtra(LockedAppActivity.EXTRA_UNINSTALL_FLAGS, 0)
                    .putExtra(LockedAppActivity.EXTRA_STATUS_RECEIVER, mUninstallStatusReceiver);
        } else {
            IntentSender intentSender = (activityMode == ActivityMode.INTERCEPT) ? mIntentSender :
                    null;
            intent.putExtra(Intent.EXTRA_INTENT, intentSender);
        }
        return intent;
    }

    private class TestLockedAppActivityInjector extends LockedAppActivity.Injector {
        private int mTargetIntentSentCount = 0;
        private int mThemeResId = 0;
        private boolean mIsTranslucent = false;
        private int mSetContentViewCount = 0;
        private int mContentViewId = 0;
        private int mDisplayId = Display.INVALID_DISPLAY;
        private IntentSender mOriginalIntentSender;
        private boolean mReturnNullForRootView = false;
        private boolean mHasWindowFocus = true;

        @Override
        public void setTheme(Activity activity, int resId) {
            mThemeResId = resId;
        }

        @Override
        public void setTranslucent(Activity activity, boolean translucent) {
            mIsTranslucent = translucent;
        }

        @Override
        public void setContentView(Activity activity, int layoutResID) {
            mContentViewId = layoutResID;
            mSetContentViewCount++;
            activity.setContentView(layoutResID);
        }

        @Override
        public boolean hasWindowFocus(Activity activity) {
            return mHasWindowFocus;
        }

        @Override
        public View findViewById(Activity activity, int id) {
            if (mReturnNullForRootView && id == android.R.id.content) {
                return null;
            }
            return activity.findViewById(id);
        }

        @Override
        public int getDisplayId(Activity activity) {
            return mDisplayId;
        }

        @Override
        public PackageManager getPackageManager(Activity activity) {
            return mPackageManager;
        }

        @Override
        public OnBackInvokedDispatcher getOnBackInvokedDispatcher(Activity activity) {
            return mOnBackInvokedDispatcher;
        }

        @Override
        public BiometricPrompt.Builder getBiometricPromptBuilder(Activity activity) {
            return mBiometricPromptBuilder;
        }

        @Override
        public IntentSender getIntentSender(Intent intent) {
            try {
                mOriginalIntentSender = intent.getParcelableExtra(Intent.EXTRA_INTENT,
                        IntentSender.class);
            } catch (BadParcelableException e) {
                // If unparceling fails, it means the IntentSender was mocked.
                mOriginalIntentSender = mIntentSender;
            }
            return mOriginalIntentSender;
        }

        @Override
        public IntentSender getUninstallStatusReceiver(Intent intent) {
            return mUninstallStatusReceiver;
        }

        @Override
        public void sendTargetIntent(Activity activity, @NonNull IntentSender target) {
            if (target.equals(mOriginalIntentSender)) {
                mTargetIntentSentCount++;
            }
        }

        boolean wasTargetIntentSent() {
            return mTargetIntentSentCount > 0;
        }

        int getTargetIntentSentCount() {
            return mTargetIntentSentCount;
        }

        int getThemeResId() {
            return mThemeResId;
        }

        boolean isTranslucent() {
            return mIsTranslucent;
        }

        int getContentViewId() {
            return mContentViewId;
        }

        int getSetContentViewCount() {
            return mSetContentViewCount;
        }

        void resetSetContentViewCount() {
            mSetContentViewCount = 0;
        }

        void setDisplayId(int displayId) {
            mDisplayId = displayId;
        }

        void setReturnNullForRootView(boolean returnNull) {
            mReturnNullForRootView = returnNull;
        }

        void setHasWindowFocus(boolean hasFocus) {
            mHasWindowFocus = hasFocus;
        }
    }
}
