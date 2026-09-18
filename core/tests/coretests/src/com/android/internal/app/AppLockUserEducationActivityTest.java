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

package com.android.internal.app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.face.FaceManager;
import android.hardware.fingerprint.FingerprintManager;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.security.Flags;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.intent.Intents;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.DisableAnimationRule;
import com.android.internal.R;
import com.android.internal.widget.LockPatternUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link AppLockUserEducationActivity}.
 *
 * <p>We can't test {@link AppLockUserEducationActivity} directly since it may launch in a separate
 * process and {@link ActivityScenario} only supports testing in the main process, so we're using
 * {@link TestAppLockUserEducationActivity} that extends {@link AppLockUserEducationActivity} for
 * testing.
 */
@RunWith(AndroidJUnit4.class)
@EnableFlags({Flags.FLAG_APP_LOCK_CORE})
public class AppLockUserEducationActivityTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Rule
    public final DisableAnimationRule mDisableAnimationRule = new DisableAnimationRule();

    private static final String TEST_PACKAGE_NAME = "com.android.test";
    private static final String TEST_APP_LABEL = "Test App";

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final Intent mDefaultIntent = createTestAppLockUserEducationIntent(TEST_PACKAGE_NAME,
            TEST_APP_LABEL);

    @Mock
    private AppLockUserEducationActivity.Injector mMockInjector;
    @Mock
    private LockPatternUtils mMockLockPatternUtils;
    @Mock
    private FingerprintManager mMockFingerprintManager;
    @Mock
    private FaceManager mMockFaceManager;

    private AutoCloseable mMockCloseable;

    @Before
    public void setUp() throws Exception {
        mMockCloseable = MockitoAnnotations.openMocks(this);
        Intents.init();

        when(mMockInjector.getLockPatternUtils(any())).thenReturn(mMockLockPatternUtils);
        when(mMockInjector.getFingerprintManager(any())).thenReturn(mMockFingerprintManager);
        when(mMockInjector.getFaceManager(any())).thenReturn(mMockFaceManager);
        when(mMockLockPatternUtils.getCredentialTypeForUser(anyInt())).thenReturn(
                LockPatternUtils.CREDENTIAL_TYPE_PIN);

        AppLockUserEducationActivity.setInjectorForTesting(mMockInjector);
    }

    @After
    public void tearDown() throws Exception {
        AppLockUserEducationActivity.setInjectorForTesting(null);
        Intents.release();
        mMockCloseable.close();
    }

    @Test
    public void launchActivity_withMissingPackageName_finishes() {
        final Intent intent = createTestAppLockUserEducationIntent(/* packageName= */ null,
                TEST_APP_LABEL);

        try (ActivityScenario<TestAppLockUserEducationActivity> scenario =
                ActivityScenario.launch(intent)) {
            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @Test
    public void launchActivity_withMissingPackageLabel_finishes() {
        final Intent intent = createTestAppLockUserEducationIntent(TEST_PACKAGE_NAME,
                /* packageLabel= */ null);

        try (ActivityScenario<TestAppLockUserEducationActivity> scenario =
                ActivityScenario.launch(intent)) {
            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @Test
    public void launchActivity_showsAppLockContent() {
        try (ActivityScenario<TestAppLockUserEducationActivity> scenario =
                ActivityScenario.launch(mDefaultIntent)) {
            onView(withId(R.id.app_lock_edu_dialog)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void dialogTitle_displaysCorrectPackageLabel() {
        try (ActivityScenario<TestAppLockUserEducationActivity> scenario =
                ActivityScenario.launch(mDefaultIntent)) {
            final String expectedTitle = mContext.getString(
                    R.string.app_lock_edu_dialog_enable_app_lock_title, TEST_APP_LABEL);

            onView(withId(R.id.app_lock_edu_dialog_title)).check(matches(withText(expectedTitle)));
        }
    }

    @Test
    public void aiDisclaimer_displaysCorrectText() {
        try (ActivityScenario<TestAppLockUserEducationActivity> scenario =
                ActivityScenario.launch(mDefaultIntent)) {
            final String expectedDisclaimer = mContext.getString(
                    R.string.app_lock_edu_dialog_info_ai_disclaimer, TEST_APP_LABEL);

            onView(withId(R.id.app_lock_edu_dialog_info_ai_text))
                    .check(matches(isDisplayed()))
                    .check(matches(withText(expectedDisclaimer)));
        }
    }

    @Test
    public void launchActivity_withPinCredentialOnly_displaysPinDescription() {
        when(mMockLockPatternUtils.getCredentialTypeForUser(anyInt())).thenReturn(
                LockPatternUtils.CREDENTIAL_TYPE_PIN);
        when(mMockFingerprintManager.hasEnrolledFingerprints(anyInt())).thenReturn(false);
        when(mMockFaceManager.hasEnrolledTemplates(anyInt())).thenReturn(false);

        try (ActivityScenario<TestAppLockUserEducationActivity> scenario =
                ActivityScenario.launch(mDefaultIntent)) {
            final String pinName = mContext.getString(
                    R.string.app_lock_edu_dialog_lockscreen_pin_name);
            final String expectedDescription = mContext.getString(
                    R.string.app_lock_edu_dialog_description_template_no_biometrics, pinName);

            onView(withId(R.id.app_lock_edu_dialog_desc)).check(matches(withText(
                    expectedDescription)));
        }
    }

    @Test
    public void launchActivity_withPatternCredentialOnly_displaysPatternDescription() {
        when(mMockLockPatternUtils.getCredentialTypeForUser(anyInt())).thenReturn(
                LockPatternUtils.CREDENTIAL_TYPE_PATTERN);
        when(mMockFingerprintManager.hasEnrolledFingerprints(anyInt())).thenReturn(false);
        when(mMockFaceManager.hasEnrolledTemplates(anyInt())).thenReturn(false);

        try (ActivityScenario<TestAppLockUserEducationActivity> scenario =
                ActivityScenario.launch(mDefaultIntent)) {
            final String patternName = mContext.getString(
                    R.string.app_lock_edu_dialog_lockscreen_pattern_name);
            final String expectedDescription = mContext.getString(
                    R.string.app_lock_edu_dialog_description_template_no_biometrics, patternName);

            onView(withId(R.id.app_lock_edu_dialog_desc)).check(matches(withText(
                    expectedDescription)));
        }
    }

    @Test
    public void launchActivity_withPasswordCredentialOnly_displaysPasswordDescription() {
        when(mMockLockPatternUtils.getCredentialTypeForUser(anyInt())).thenReturn(
                LockPatternUtils.CREDENTIAL_TYPE_PASSWORD);
        when(mMockFingerprintManager.hasEnrolledFingerprints(anyInt())).thenReturn(false);
        when(mMockFaceManager.hasEnrolledTemplates(anyInt())).thenReturn(false);

        try (ActivityScenario<TestAppLockUserEducationActivity> scenario =
                ActivityScenario.launch(mDefaultIntent)) {
            final String passwordName = mContext.getString(
                    R.string.app_lock_edu_dialog_lockscreen_password_name);
            final String expectedDescription = mContext.getString(
                    R.string.app_lock_edu_dialog_description_template_no_biometrics, passwordName);

            onView(withId(R.id.app_lock_edu_dialog_desc)).check(matches(withText(
                    expectedDescription)));
        }
    }

    @Test
    public void launchActivity_withFingerprintOnly_displaysFingerprintDescription() {
        when(mMockLockPatternUtils.getCredentialTypeForUser(anyInt())).thenReturn(
                LockPatternUtils.CREDENTIAL_TYPE_PIN);
        when(mMockFingerprintManager.hasEnrolledFingerprints(anyInt())).thenReturn(true);
        when(mMockFaceManager.hasEnrolledTemplates(anyInt())).thenReturn(false);

        try (ActivityScenario<TestAppLockUserEducationActivity> scenario =
                ActivityScenario.launch(mDefaultIntent)) {
            final String fingerprintDesc = mContext.getString(
                    R.string.app_lock_edu_dialog_biometric_description_has_fingerprint);
            final String pinName = mContext.getString(
                    R.string.app_lock_edu_dialog_lockscreen_pin_name);
            final String expectedDescription = mContext.getString(
                    R.string.app_lock_edu_dialog_description_template, fingerprintDesc, pinName);

            onView(withId(R.id.app_lock_edu_dialog_desc)).check(matches(withText(
                    expectedDescription)));
        }
    }

    @Test
    public void launchActivity_withFaceOnly_displaysFaceDescription() {
        when(mMockLockPatternUtils.getCredentialTypeForUser(anyInt())).thenReturn(
                LockPatternUtils.CREDENTIAL_TYPE_PIN);
        when(mMockFingerprintManager.hasEnrolledFingerprints(anyInt())).thenReturn(false);
        when(mMockFaceManager.hasEnrolledTemplates(anyInt())).thenReturn(true);

        try (ActivityScenario<TestAppLockUserEducationActivity> scenario =
                ActivityScenario.launch(mDefaultIntent)) {
            final String faceDesc = mContext.getString(
                    R.string.app_lock_edu_dialog_biometric_description_has_face);
            final String pinName = mContext.getString(
                    R.string.app_lock_edu_dialog_lockscreen_pin_name);
            final String expectedDescription = mContext.getString(
                    R.string.app_lock_edu_dialog_description_template, faceDesc, pinName);

            onView(withId(R.id.app_lock_edu_dialog_desc)).check(matches(withText(
                    expectedDescription)));
        }
    }

    @Test
    public void launchActivity_withFingerprintAndFace_displaysFingerprintAndFaceDescription() {
        when(mMockLockPatternUtils.getCredentialTypeForUser(anyInt())).thenReturn(
                LockPatternUtils.CREDENTIAL_TYPE_PIN);
        when(mMockFingerprintManager.hasEnrolledFingerprints(anyInt())).thenReturn(true);
        when(mMockFaceManager.hasEnrolledTemplates(anyInt())).thenReturn(true);

        try (ActivityScenario<TestAppLockUserEducationActivity> scenario =
                ActivityScenario.launch(mDefaultIntent)) {
            final String biometrics = mContext.getString(
                    R.string.app_lock_edu_dialog_biometric_description_has_fingerprint_and_face);
            final String pinName = mContext.getString(
                    R.string.app_lock_edu_dialog_lockscreen_pin_name);
            final String expectedDescription = mContext.getString(
                    R.string.app_lock_edu_dialog_description_template, biometrics, pinName);

            onView(withId(R.id.app_lock_edu_dialog_desc)).check(matches(withText(
                    expectedDescription)));
        }
    }

    @Test
    public void clickLockAppButton_returnsResultOk() {
        try (ActivityScenario<TestAppLockUserEducationActivity> scenario =
                ActivityScenario.launchActivityForResult(mDefaultIntent)) {
            onView(withId(R.id.app_lock_edu_dialog_btn_lock_app)).perform(click());

            assertThat(scenario.getResult().getResultCode()).isEqualTo(Activity.RESULT_OK);
        }
    }

    @Test
    public void clickCancelButton_onUserEducationScreen_finishesActivity() {
        try (ActivityScenario<TestAppLockUserEducationActivity> scenario =
                ActivityScenario.launch(mDefaultIntent)) {
            onView(withId(R.id.app_lock_edu_dialog_btn_cancel)).perform(click());

            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    private Intent createTestAppLockUserEducationIntent(String packageName,
            CharSequence packageLabel) {
        final Intent intent = new Intent(mContext, TestAppLockUserEducationActivity.class);
        intent.putExtra(Intent.EXTRA_PACKAGE_NAME, packageName);
        intent.putExtra(Intent.EXTRA_TITLE, packageLabel);
        return intent;
    }

    /**
     * A test-specific subclass of {@link AppLockUserEducationActivity}.
     *
     * <p><b>Visibility:</b> This class must be declared {@code public} so that the Android testing
     * framework can instantiate it.
     */
    public static class TestAppLockUserEducationActivity extends AppLockUserEducationActivity {
    }
}
