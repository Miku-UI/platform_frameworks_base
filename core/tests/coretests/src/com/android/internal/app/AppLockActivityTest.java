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
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.ApplicationInfoFlags;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.security.Flags;
import android.widget.Toast;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.android.internal.R;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Unit tests for {@link AppLockActivity}.
 *
 * <p>We can't test {@link AppLockActivity} directly since it launches in a separate process and
 * {@link ActivityScenario} only supports testing in the main process, so we're using
 * {@link TestAppLockActivity} that extends {@link AppLockActivity} for testing.
 */
@RunWith(TestParameterInjector.class)
public class AppLockActivityTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private static final String SYSTEM_PACKAGE_NAME = "android";
    private static final String TEST_PACKAGE_NAME = "com.android.test";
    private static final String TEST_APP_LABEL = "Test App";
    private static final int ADD_SCREEN_LOCK_BUTTON_RES_ID =
            R.id.app_lock_dialog_add_screen_lock_button;
    private static final int CANCEL_BUTTON_RES_ID = R.id.app_lock_dialog_cancel_button;
    private static final int EXPECTED_RESULT_TOAST_DURATION = Toast.LENGTH_SHORT;
    private static final int INVALID_TOAST_DURATION = -1;
    private static final int REQUEST_CODE_SET_SCREEN_LOCK = 1;
    private static final int REQUEST_CODE_USER_EDUCATION_DIALOG = 2;
    private static final int REQUEST_CODE_APP_PERMISSION_REVIEW_DIALOG = 3;

    private final Context mContext = ApplicationProvider.getApplicationContext();

    private ApplicationInfo mApplicationInfo;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private KeyguardManager mKeyguardManager;
    @Mock
    private AppLockActivity.Injector mInjector;
    @Mock
    private BiometricPrompt.Builder mBiometricPromptBuilder;
    @Mock
    private BiometricPrompt mBiometricPrompt;

    private AutoCloseable mMockCloseable;

    @Before
    public void setUp() throws Exception {
        mMockCloseable = MockitoAnnotations.openMocks(this);

        mApplicationInfo = spy(new ApplicationInfo());

        when(mPackageManager.getApplicationInfo(eq(TEST_PACKAGE_NAME), argThat(flags ->
                flags.getValue() == PackageManager.GET_APP_LOCK_INFO || flags.getValue() == 0)))
                .thenReturn(mApplicationInfo);
        when(mApplicationInfo.loadLabel(mPackageManager)).thenReturn(TEST_APP_LABEL);

        AppLockActivity.setInjectorForTesting(mInjector);

        when(mInjector.getBiometricPromptBuilder(any())).thenReturn(mBiometricPromptBuilder);
        when(mBiometricPromptBuilder.setTitle(any())).thenReturn(mBiometricPromptBuilder);
        when(mBiometricPromptBuilder.setSubtitle(any())).thenReturn(mBiometricPromptBuilder);
        when(mBiometricPromptBuilder.setLogoDescription(any())).thenReturn(mBiometricPromptBuilder);
        when(mBiometricPromptBuilder.setAllowedAuthenticators(anyInt())).thenReturn(
                mBiometricPromptBuilder);
        when(mBiometricPromptBuilder.setLogoBitmap(any())).thenReturn(mBiometricPromptBuilder);
        when(mBiometricPromptBuilder.build()).thenReturn(mBiometricPrompt);

        TestAppLockActivity.sPackageManager = mPackageManager;
        TestAppLockActivity.sKeyguardManager = mKeyguardManager;
        TestAppLockActivity.sShownToastText = null;
        TestAppLockActivity.sShownToastDuration = INVALID_TOAST_DURATION;
    }

    @After
    public void tearDown() throws Exception {
        mMockCloseable.close();
    }

    @Test
    public void createIntent_withNullPackageName_throws(@TestParameter boolean newAppLockEnabled) {
        assertThrows(NullPointerException.class,
                () -> AppLockActivity.createAppLockActivityIntent(null, newAppLockEnabled));
    }

    @Test
    public void createIntent_withAppLockState_returnsCorrectlyConfiguredAppLockActivityIntent(
            @TestParameter boolean newAppLockEnabled) {
        Intent intent = AppLockActivity.createAppLockActivityIntent(TEST_PACKAGE_NAME,
                newAppLockEnabled);
        ComponentName expectedComponentName = new ComponentName(SYSTEM_PACKAGE_NAME,
                AppLockActivity.class.getName());

        assertThat(intent).isNotNull();
        assertThat(intent.getComponent()).isEqualTo(expectedComponentName);
        assertThat(intent.getAction()).isEqualTo(PackageManager.ACTION_SET_APP_LOCK);
        assertThat(intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME)).isEqualTo(TEST_PACKAGE_NAME);
        assertThat(intent.hasExtra(PackageManager.EXTRA_APP_LOCK_NEW_STATE)).isTrue();
        assertThat(intent.getBooleanExtra(PackageManager.EXTRA_APP_LOCK_NEW_STATE, false))
                .isEqualTo(newAppLockEnabled);
        assertThat(intent.getFlags()).isEqualTo(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_withoutIntentExtraPackageName_finishes() {
        Intent intent = createTestAppLockActivityIntentWithoutExtras();
        intent.putExtra(PackageManager.EXTRA_APP_LOCK_NEW_STATE, true);

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_withoutIntentExtraAppLockNewState_finishes() {
        Intent intent = createTestAppLockActivityIntentWithoutExtras();
        intent.putExtra(Intent.EXTRA_PACKAGE_NAME, TEST_PACKAGE_NAME);

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_whenPackageManagerThrowsNameNotFound_finishes(
            @TestParameter boolean newAppLockEnabled) throws PackageManager.NameNotFoundException {
        Intent intent = createTestAppLockActivityIntent(newAppLockEnabled);
        doThrow(new PackageManager.NameNotFoundException()).when(mPackageManager)
                .getApplicationInfo(anyString(), any(ApplicationInfoFlags.class));

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_whenAppLockIsNotSupported_finishes(
            @TestParameter boolean newAppLockEnabled) {
        Intent intent = createTestAppLockActivityIntent(newAppLockEnabled);
        mApplicationInfo.isAppLockSupported = false;
        mApplicationInfo.isAppLockEnabled = !newAppLockEnabled;

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_whenAppLockStateIsUnchanged_finishes(
            @TestParameter boolean newAppLockEnabled) {
        Intent intent = createTestAppLockActivityIntent(newAppLockEnabled);
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = newAppLockEnabled;

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @DisableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_withValidIntent_appLockFlagsDisabled_finishes(
            @TestParameter boolean newAppLockEnabled) {
        Intent intent = createTestAppLockActivityIntent(newAppLockEnabled);
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = !newAppLockEnabled;

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_changingAppLockState_deviceSecure_showsBiometricPrompt(
            @TestParameter boolean newAppLockEnabled) {
        final Intent intent = createTestAppLockActivityIntent(newAppLockEnabled);
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = !newAppLockEnabled;
        when(mKeyguardManager.isDeviceSecure(anyInt())).thenReturn(true);

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                if (newAppLockEnabled) {
                    activity.onActivityResult(REQUEST_CODE_USER_EDUCATION_DIALOG,
                            Activity.RESULT_OK, null);
                }
                assertThat(activity.mBiometricPromptShown).isTrue();
                verifyBiometricPromptDisplayed(newAppLockEnabled);
            });
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_disablingAppLock_deviceInsecure_finishes() {
        final Intent intent = createTestAppLockActivityIntent(/* newAppLockEnabled= */ false);
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = true;
        when(mKeyguardManager.isDeviceSecure(anyInt())).thenReturn(false);

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_enablingAppLock_deviceInsecure_showsAddScreenLockDialog() {
        final Intent intent = createTestAppLockActivityIntent(/* newAppLockEnabled= */ true);
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = false;
        when(mKeyguardManager.isDeviceSecure(anyInt())).thenReturn(false);

        final String expectedDialogTitle = mContext.getString(
                R.string.app_lock_add_screen_lock_title, TEST_APP_LABEL);
        final String expectedDialogMessage = mContext.getString(
                R.string.app_lock_add_screen_lock_message, TEST_APP_LABEL);

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withText(expectedDialogTitle)).inRoot(isDialog()).check(matches(isDisplayed()));
            onView(withText(expectedDialogMessage)).inRoot(isDialog())
                    .check(matches(isDisplayed()));
            onView(withId(ADD_SCREEN_LOCK_BUTTON_RES_ID)).inRoot(isDialog())
                    .check(matches(isDisplayed()));
            onView(withId(CANCEL_BUTTON_RES_ID)).inRoot(isDialog()).check(matches(isDisplayed()));
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void clickAddScreenLockButtonOnAddScreenLockDialog_startsSetPasswordIntent() {
        final Intent intent = createTestAppLockActivityIntent(/* newAppLockEnabled= */ true);
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = false;
        when(mKeyguardManager.isDeviceSecure(anyInt())).thenReturn(false);

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(ADD_SCREEN_LOCK_BUTTON_RES_ID)).inRoot(isDialog()).perform(click());

            scenario.onActivity(activity -> {
                assertThat(activity.mStartedIntentForResult).isNotNull();
                assertThat(activity.mStartedIntentForResult.getAction()).isEqualTo(
                        DevicePolicyManager.ACTION_SET_NEW_PASSWORD);
            });
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void clickCancelButtonOnAddScreenLockDialog_finishesActivity() {
        final Intent intent = createTestAppLockActivityIntent(/* newAppLockEnabled= */ true);
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = false;
        when(mKeyguardManager.isDeviceSecure(anyInt())).thenReturn(false);

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(CANCEL_BUTTON_RES_ID)).inRoot(isDialog()).perform(click());

            scenario.onActivity(activity -> assertThat(activity.isFinishing()).isTrue());
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_screenLockSetupFailed_deviceStillInsecure_finishesActivity() {
        final Intent intent = createTestAppLockActivityIntent(/* newAppLockEnabled= */ true);
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = false;
        when(mKeyguardManager.isDeviceSecure(anyInt())).thenReturn(false);

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                activity.onActivityResult(REQUEST_CODE_SET_SCREEN_LOCK, Activity.RESULT_CANCELED,
                        null);

                assertThat(activity.isFinishing()).isTrue();
                assertThat(activity.mBiometricPromptShown).isFalse();
            });
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void launchActivity_screenLockSetupSuccessful_startsUserEducation() {
        final Intent intent = createTestAppLockActivityIntent(/* newAppLockEnabled= */ true);
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = false;
        when(mKeyguardManager.isDeviceSecure(anyInt())).thenReturn(false);

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                when(mKeyguardManager.isDeviceSecure(anyInt())).thenReturn(true);
                activity.onActivityResult(REQUEST_CODE_SET_SCREEN_LOCK, Activity.RESULT_OK, null);

                assertThat(activity.mLastRequestCode).isEqualTo(REQUEST_CODE_USER_EDUCATION_DIALOG);
                assertThat(activity.mStartedIntentForResult.getComponent().getClassName())
                        .isEqualTo(AppLockUserEducationActivity.class.getName());
            });
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void testBiometricPrompt_hasAppSpecificLogo() throws Exception {
        final Intent intent = createTestAppLockActivityIntent(/* newAppLockEnabled= */ false);
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = true;
        final Drawable mockIcon = mock(Drawable.class);
        when(mPackageManager.getApplicationIcon(TEST_PACKAGE_NAME)).thenReturn(mockIcon);
        when(mKeyguardManager.isDeviceSecure(anyInt())).thenReturn(true);

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                assertThat(activity.mBiometricPromptShown).isTrue();
                final ArgumentCaptor<Bitmap> bitmapCaptor = ArgumentCaptor.forClass(Bitmap.class);
                verify(mBiometricPromptBuilder).setLogoBitmap(bitmapCaptor.capture());
                final Bitmap capturedBitmap = bitmapCaptor.getValue();
                final Bitmap expectedBitmap = LockedAppActivity.convertDrawableToBitmap(mockIcon);
                assertThat(capturedBitmap.sameAs(expectedBitmap)).isTrue();
            });
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void testAuthenticationSuccess_changesAppLockState_showsSuccessToastAndFinishes(
            @TestParameter boolean newAppLockEnabled) {
        final Intent intent = createTestAppLockActivityIntent(newAppLockEnabled);
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = !newAppLockEnabled;
        when(mPackageManager.setPackageAppLockEnabled(TEST_PACKAGE_NAME, newAppLockEnabled))
                .thenReturn(true);
        when(mKeyguardManager.isDeviceSecure(anyInt())).thenReturn(true);
        final int expectedToastResId = newAppLockEnabled
                ? R.string.enable_app_lock_success_toast_message
                : R.string.disable_app_lock_success_toast_message;
        final String expectedSuccessToastText = mContext.getString(expectedToastResId,
                TEST_APP_LABEL);

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                if (newAppLockEnabled) {
                    when(mKeyguardManager.isDeviceSecure(anyInt())).thenReturn(true);
                    activity.onActivityResult(REQUEST_CODE_SET_SCREEN_LOCK, Activity.RESULT_OK,
                            null);
                    activity.onActivityResult(REQUEST_CODE_USER_EDUCATION_DIALOG,
                            Activity.RESULT_OK, null);
                }
                captureAuthenticationCallback().onAuthenticationSucceeded(mock(
                        BiometricPrompt.AuthenticationResult.class));

                verify(mPackageManager).setPackageAppLockEnabled(TEST_PACKAGE_NAME,
                        newAppLockEnabled);
                assertThat(TestAppLockActivity.sShownToastText.toString()).isEqualTo(
                        expectedSuccessToastText);
                assertThat(TestAppLockActivity.sShownToastDuration).isEqualTo(
                        EXPECTED_RESULT_TOAST_DURATION);
                assertThat(activity.isFinishing()).isTrue();
            });
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void testAuthenticationError_enablingAppLock_showsFailureToastAndFinishes() {
        final Intent intent = createTestAppLockActivityIntent(/* newAppLockEnabled= */ true);
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = false;
        when(mKeyguardManager.isDeviceSecure(anyInt())).thenReturn(false);
        final String expectedFailureToastText = mContext.getString(
                R.string.enable_app_lock_failure_toast_message, TEST_APP_LABEL);

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                when(mKeyguardManager.isDeviceSecure(anyInt())).thenReturn(true);
                activity.onActivityResult(REQUEST_CODE_SET_SCREEN_LOCK, Activity.RESULT_OK, null);
                activity.onActivityResult(REQUEST_CODE_USER_EDUCATION_DIALOG, Activity.RESULT_OK,
                        null);
                captureAuthenticationCallback().onAuthenticationError(
                        BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED, "User canceled");

                verify(mPackageManager, never()).setPackageAppLockEnabled(
                        anyString(), anyBoolean());
                assertThat(TestAppLockActivity.sShownToastText.toString()).isEqualTo(
                        expectedFailureToastText);
                assertThat(TestAppLockActivity.sShownToastDuration).isEqualTo(
                        EXPECTED_RESULT_TOAST_DURATION);
                assertThat(activity.isFinishing()).isTrue();
            });
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void testAuthenticationError_disablingAppLock_finishes() {
        final Intent intent = createTestAppLockActivityIntent(/* newAppLockEnabled= */ false);
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = true;
        when(mKeyguardManager.isDeviceSecure(anyInt())).thenReturn(true);

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                captureAuthenticationCallback().onAuthenticationError(
                        BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED, "User canceled");

                verify(mPackageManager, never()).setPackageAppLockEnabled(
                        anyString(), anyBoolean());
                assertThat(activity.isFinishing()).isTrue();
            });
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void testLaunchesUserEducation_enablingAppLock_whenDeviceIsSecure() {
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = false;
        when(mKeyguardManager.isDeviceSecure(anyInt())).thenReturn(true);
        Intent intent = createTestAppLockActivityIntent(true);

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                assertThat(activity.mLastRequestCode).isEqualTo(
                        REQUEST_CODE_USER_EDUCATION_DIALOG);
                assertThat(activity.mStartedIntentForResult.getComponent().getClassName())
                        .isEqualTo(AppLockUserEducationActivity.class.getName());
            });
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void onActivityResult_userEducationOk_isPhotoApp_startsPermissionReview() {
        final Intent intent = createTestAppLockActivityIntent(/* newAppLockEnabled= */ true);
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = false;

        final List<ResolveInfo> resolveInfos = new ArrayList<>();
        resolveInfos.add(new ResolveInfo());
        when(mPackageManager.queryIntentActivities(argThat((Intent argIntent) ->
                Intent.ACTION_VIEW.equals(argIntent.getAction())
                        && TEST_PACKAGE_NAME.equals(argIntent.getPackage())
                        && "image/*".equals(argIntent.getType())), anyInt()))
                                .thenReturn(resolveInfos);

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                activity.onActivityResult(REQUEST_CODE_USER_EDUCATION_DIALOG, Activity.RESULT_OK,
                        /* data= */ null);

                assertThat(activity.mLastRequestCode).isEqualTo(
                        REQUEST_CODE_APP_PERMISSION_REVIEW_DIALOG);
                assertThat(activity.mStartedIntentForResult).isNotNull();
                assertThat(activity.mStartedIntentForResult.getComponent().getClassName())
                        .contains("AppLockPermissionReviewActivity");
            });
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void onActivityResult_userEducationOk_isFilesApp_startsPermissionReview() {
        final Intent intent = createTestAppLockActivityIntent(/* newAppLockEnabled= */ true);
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = false;

        final ResolveInfo resolveInfo = new ResolveInfo();
        when(mPackageManager.resolveActivity(argThat((Intent argIntent) ->
                DownloadManager.ACTION_VIEW_DOWNLOADS.equals(argIntent.getAction())
                        && TEST_PACKAGE_NAME.equals(argIntent.getPackage())),
                                any(PackageManager.ResolveInfoFlags.class)))
                                        .thenReturn(resolveInfo);

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                activity.onActivityResult(REQUEST_CODE_USER_EDUCATION_DIALOG, Activity.RESULT_OK,
                        /* data= */ null);

                assertThat(activity.mLastRequestCode).isEqualTo(
                        REQUEST_CODE_APP_PERMISSION_REVIEW_DIALOG);
                assertThat(activity.mStartedIntentForResult).isNotNull();
                assertThat(activity.mStartedIntentForResult.getComponent().getClassName())
                        .contains("AppLockPermissionReviewActivity");
            });
        }
    }

    @EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
    @Test
    public void onActivityResult_userEducationOk_notReviewableApp_doesNotStartPermissionReview() {
        final Intent intent = createTestAppLockActivityIntent(true);
        mApplicationInfo.isAppLockSupported = true;
        mApplicationInfo.isAppLockEnabled = false;
        when(mPackageManager.queryIntentActivities(any(Intent.class), anyInt())).thenReturn(
                Collections.emptyList());

        try (ActivityScenario<TestAppLockActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                activity.onActivityResult(REQUEST_CODE_USER_EDUCATION_DIALOG, Activity.RESULT_OK,
                        /* data= */ null);

                assertThat(activity.mLastRequestCode).isNotEqualTo(
                        REQUEST_CODE_APP_PERMISSION_REVIEW_DIALOG);
            });
        }
    }

    private Intent createTestAppLockActivityIntentWithoutExtras() {
        return new Intent(mContext, TestAppLockActivity.class).setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    }

    private Intent createTestAppLockActivityIntent(boolean newAppLockEnabled) {
        return createTestAppLockActivityIntentWithoutExtras()
                .putExtra(Intent.EXTRA_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(PackageManager.EXTRA_APP_LOCK_NEW_STATE, newAppLockEnabled);
    }

    private void verifyBiometricPromptDisplayed(boolean newAppLockEnabled) {
        final String expectedTitle = mContext.getString(R.string.biometric_dialog_default_title);
        final String expectedSubtitle = newAppLockEnabled
                ? mContext.getString(R.string.enable_app_lock_biometric_prompt_subtitle,
                        TEST_APP_LABEL)
                : mContext.getString(R.string.disable_app_lock_biometric_prompt_subtitle,
                        TEST_APP_LABEL);

        verify(mBiometricPromptBuilder).setTitle(expectedTitle);
        verify(mBiometricPromptBuilder).setSubtitle(expectedSubtitle);
        verify(mBiometricPromptBuilder).setLogoDescription(TEST_APP_LABEL);
        verify(mBiometricPromptBuilder).setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        verify(mBiometricPromptBuilder).build();
        verify(mBiometricPrompt).authenticate(any(), any(), any());
    }

    private BiometricPrompt.AuthenticationCallback captureAuthenticationCallback() {
        ArgumentCaptor<BiometricPrompt.AuthenticationCallback> callbackCaptor =
                ArgumentCaptor.forClass(BiometricPrompt.AuthenticationCallback.class);
        verify(mBiometricPrompt).authenticate(any(), any(), callbackCaptor.capture());
        return callbackCaptor.getValue();
    }

    /**
     * A test-specific subclass of {@link AppLockActivity}.
     *
     * <p><b>Visibility:</b> This class must be declared {@code public} so that the Android testing
     * framework can instantiate it.
     */
    public static class TestAppLockActivity extends AppLockActivity {
        // These variables are defined in the setUp() method.
        static KeyguardManager sKeyguardManager;
        static PackageManager sPackageManager;
        static CharSequence sShownToastText;
        static int sShownToastDuration;

        int mLastRequestCode = -1;
        boolean mBiometricPromptShown = false;
        Intent mStartedIntentForResult;

        @Override
        public PackageManager getPackageManager() {
            return sPackageManager;
        }

        @Override
        public Object getSystemService(String name) {
            if (Context.KEYGUARD_SERVICE.equals(name)) {
                return sKeyguardManager;
            }
            return super.getSystemService(name);
        }

        @Override
        protected void showToast(CharSequence toastText, int duration) {
            sShownToastText = toastText;
            sShownToastDuration = duration;
        }

        @Override
        public void startActivityForResult(Intent intent, int requestCode) {
            mStartedIntentForResult = intent;
            mLastRequestCode = requestCode;
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
        }

        @Override
        protected void showBiometricPrompt(String packageName, CharSequence packageLabel,
                boolean newAppLockEnabled) {
            mBiometricPromptShown = true;
            super.showBiometricPrompt(packageName, packageLabel, newAppLockEnabled);
        }
    }
}
