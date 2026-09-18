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
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.google.common.truth.Truth.assertThat;

import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.ColorDrawable;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.security.Flags;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.DisableAnimationRule;
import com.android.internal.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Instrumentation tests for {@link AppLockPermissionReviewActivity}. This file tests the UI and
 * lifecycle behavior.
 *
 * <p>We can't test {@link AppLockPermissionReviewActivity} directly since it launches in a
 * separate process and {@link ActivityScenario} only supports testing in the main process, so
 * we're using {@link AppLockPermissionReviewActivityTest} that extends
 * {@link AppLockPermissionReviewActivity} for testing.
 */
@RunWith(AndroidJUnit4.class)
@EnableFlags({Flags.FLAG_APP_LOCK_CORE})
public class AppLockPermissionReviewActivityTest {

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Rule
    public final DisableAnimationRule mDisableAnimationRule = new DisableAnimationRule();
    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    private static final String TEST_PACKAGE_NAME = "com.android.test";
    private static final String TEST_APP_LABEL = "Test App";
    private static final String PACKAGE_TO_LOCK = "com.android.target_package";

    private static final String[] PHOTO_PERMISSIONS = new String[] {
        android.Manifest.permission.READ_MEDIA_IMAGES,
        android.Manifest.permission.READ_MEDIA_VIDEO,
        android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        android.Manifest.permission.MANAGE_EXTERNAL_STORAGE,
    };
    private static final String[] FILE_PERMISSIONS = new String[] {
            android.Manifest.permission.MANAGE_EXTERNAL_STORAGE,
    };

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final Intent mDefaultIntent = createTestAppLockPermissionReviewIntent(
            PACKAGE_TO_LOCK, AppLockPermissionReviewActivity.REVIEW_TYPE_PHOTOS);

    @Mock
    private PackageManager mPackageManager;
    @Mock
    private ApplicationInfo mApplicationInfo;

    @Before
    public void setUp() throws Exception {
        TestAppLockPermissionReviewActivity.sPackageManager = mPackageManager;

        when(mApplicationInfo.loadLabel(any(PackageManager.class))).thenReturn(TEST_APP_LABEL);
        when(mApplicationInfo.loadIcon(any(PackageManager.class))).thenReturn(new ColorDrawable());
        when(mPackageManager.getApplicationInfo(anyString(), anyInt()))
                .thenReturn(mApplicationInfo);
        when(mPackageManager.getApplicationLabel(any(ApplicationInfo.class)))
                .thenReturn(TEST_APP_LABEL);
    }

    @After
    public void tearDown() {
        TestAppLockPermissionReviewActivity.sPackageManager = null;
    }

    @Test
    public void launchActivity_withNoAppsWithPermission_finishesActivity() {
        when(mPackageManager.getPackagesHoldingPermissions(any(), anyInt()))
                .thenReturn(new ArrayList<>());

        try (ActivityScenario<TestAppLockPermissionReviewActivity> scenario =
                ActivityScenario.launch(mDefaultIntent)) {
            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @Test
    public void launchActivity_withInvalidReviewType_finishesActivity() {
        setupMockPackages(/* count= */ 1, /* extraPackageName= */ null);
        final Intent invalidReviewTypeIntent = createTestAppLockPermissionReviewIntent(
                PACKAGE_TO_LOCK, /* reviewType= */ -1);

        try (ActivityScenario<AppLockPermissionReviewActivity> scenario =
                ActivityScenario.launch(invalidReviewTypeIntent)) {
            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @Test
    public void launchActivity_withPhotosReviewType_showsPhotosSpecificStrings() {
        setupMockPackages(/* count= */ 1, /* extraPackageName= */ null);
        final Intent photosPermissionReviewIntent = createTestAppLockPermissionReviewIntent(
                PACKAGE_TO_LOCK, AppLockPermissionReviewActivity.REVIEW_TYPE_PHOTOS);

        try (ActivityScenario<TestAppLockPermissionReviewActivity> scenario =
                ActivityScenario.launch(photosPermissionReviewIntent)) {
            onView(withText(R.string.app_lock_permission_review_dialog_title_photos))
                    .check(matches(isDisplayed()));
            onView(withText(R.string.app_lock_permission_review_dialog_subtitle_photos))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void launchActivity_withFilesReviewType_showsFilesSpecificStrings() {
        setupMockPackages(/* count= */ 1, /* extraPackageName= */ null);
        final Intent filesPermissionReviewIntent = createTestAppLockPermissionReviewIntent(
                PACKAGE_TO_LOCK, AppLockPermissionReviewActivity.REVIEW_TYPE_FILES);

        try (ActivityScenario<TestAppLockPermissionReviewActivity> scenario =
                ActivityScenario.launch(filesPermissionReviewIntent)) {
            onView(withText(R.string.app_lock_permission_review_dialog_title_files))
                    .check(matches(isDisplayed()));
            onView(withText(R.string.app_lock_permission_review_dialog_subtitle_files))
                    .check(matches(isDisplayed()));
        }
    }

    @Test
    public void launchActivity_withAppsWithPermission_showsPermissionReviewSheet() {
        setupMockPackages(/* count= */ 1, /* extraPackageName= */ null);

        try (ActivityScenario<TestAppLockPermissionReviewActivity> scenario =
                ActivityScenario.launch(mDefaultIntent)) {
            onView(withId(R.id.app_lock_permission_review_app_list_recycler_view))
                    .check(matches(isDisplayed()));
            onView(withText(TEST_APP_LABEL)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void clickContinueButton_returnsResultOk() {
        setupMockPackages(/* count= */ 1, /* extraPackageName= */ null);

        try (ActivityScenario<TestAppLockPermissionReviewActivity> scenario =
                ActivityScenario.launchActivityForResult(mDefaultIntent)) {
            onView(withId(R.id.app_lock_permission_review_btn_continue)).perform(click());

            assertThat(scenario.getResult().getResultCode()).isEqualTo(Activity.RESULT_OK);
        }
    }

    @Test
    public void clickCancelButton_finishesActivity() {
        setupMockPackages(/* count= */ 1, /* extraPackageName= */ null);

        try (ActivityScenario<TestAppLockPermissionReviewActivity> scenario =
                ActivityScenario.launch(mDefaultIntent)) {
            onView(withId(R.id.app_lock_permission_review_btn_cancel)).perform(click());

            assertThat(scenario.getState()).isEqualTo(Lifecycle.State.DESTROYED);
        }
    }

    @Test
    public void withMoreThanThreeApps_showsViewAllButtonAndExpands() {
        setupMockPackages(/* count= */ 5, /* extraPackageName= */ null);

        try (ActivityScenario<TestAppLockPermissionReviewActivity> scenario =
                ActivityScenario.launch(mDefaultIntent)) {
            onView(withId(R.id.app_lock_permission_review_btn_view_all))
                    .check(matches(isDisplayed()));
            onView(withId(R.id.app_lock_permission_review_btn_view_all)).perform(click());
            onView(withId(R.id.app_lock_permission_review_btn_view_all))
                    .check(matches(not(isDisplayed())));
        }
    }

    @Test
    public void packageToLock_isFilteredOutFromList() {
        setupMockPackages(/* count= */ 1, /* extraPackageName= */ PACKAGE_TO_LOCK);

        try (ActivityScenario<TestAppLockPermissionReviewActivity> scenario =
                ActivityScenario.launch(mDefaultIntent)) {
            onView(withText(TEST_APP_LABEL)).check(matches(isDisplayed()));
            onView(withText(PACKAGE_TO_LOCK)).check(doesNotExist());
        }
    }

    @Test
    public void launchPhotosReview_filtersOutAppsWithOnlyFilesPermissions() {
        setupMockPackagesForPermissions(PHOTO_PERMISSIONS, Collections.singletonList("App_Photos"));
        setupMockPackagesForPermissions(FILE_PERMISSIONS, Collections.singletonList("App_Files"));

        final Intent photosPermissionReviewIntent = createTestAppLockPermissionReviewIntent(
                PACKAGE_TO_LOCK, AppLockPermissionReviewActivity.REVIEW_TYPE_PHOTOS);

        try (ActivityScenario<TestAppLockPermissionReviewActivity> scenario =
                ActivityScenario.launch(photosPermissionReviewIntent)) {
            onView(withText("App_Photos")).check(matches(isDisplayed()));
            onView(withText("App_Files")).check(doesNotExist());
        }
    }

    @Test
    public void launchFilesReview_filtersOutAppsWithOnlyPhotosPermissions() {
        setupMockPackagesForPermissions(PHOTO_PERMISSIONS, Collections.singletonList("App_Photos"));
        setupMockPackagesForPermissions(FILE_PERMISSIONS, Collections.singletonList("App_Files"));

        final Intent filesPermissionReviewIntent = createTestAppLockPermissionReviewIntent(
                PACKAGE_TO_LOCK, AppLockPermissionReviewActivity.REVIEW_TYPE_FILES);

        try (ActivityScenario<TestAppLockPermissionReviewActivity> scenario =
                ActivityScenario.launch(filesPermissionReviewIntent)) {
            onView(withText("App_Files")).check(matches(isDisplayed()));
            onView(withText("App_Photos")).check(doesNotExist());
        }
    }

    private PackageInfo createPackageInfo(String packageName) {
        final PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = packageName;
        packageInfo.applicationInfo = mApplicationInfo;
        return packageInfo;
    }

    private Intent createTestAppLockPermissionReviewIntent(String packageToLock, int reviewType) {
        final Intent intent = new Intent(mContext, TestAppLockPermissionReviewActivity.class);
        intent.putExtra(Intent.EXTRA_PACKAGE_NAME, packageToLock);
        intent.putExtra(AppLockPermissionReviewActivity.EXTRA_PERMISSION_REVIEW_TYPE, reviewType);
        return intent;
    }

    private void setupMockPackages(int count, String extraPackageName) {
        final List<PackageInfo> packages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            packages.add(createPackageInfo(TEST_PACKAGE_NAME + i));
        }
        if (extraPackageName != null) {
            packages.add(createPackageInfo(extraPackageName));
        }

        when(mPackageManager.getPackagesHoldingPermissions(any(), anyInt())).thenReturn(packages);
        when(mPackageManager.checkPermission(anyString(), anyString()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mPackageManager.queryIntentActivities(any(Intent.class), anyInt()))
                .thenReturn(Collections.singletonList(new ResolveInfo()));
    }

    private void setupMockPackagesForPermissions(String[] permissions, List<String> packageNames) {
        List<PackageInfo> packages = new ArrayList<>();
        for (String pkg : packageNames) {
            final PackageInfo packageInfo = new PackageInfo();
            packageInfo.packageName = pkg;
            packageInfo.applicationInfo = new ApplicationInfo();
            packageInfo.applicationInfo.packageName = pkg;
            packages.add(packageInfo);

            when(mPackageManager.getApplicationLabel(argThat(info -> info != null
                    && pkg.equals(info.packageName))))
                    .thenReturn(pkg);
        }
        when(mPackageManager.getPackagesHoldingPermissions(eq(permissions), anyInt()))
                .thenReturn(packages);
        when(mPackageManager.queryIntentActivities(argThat(intent ->
                intent != null && packageNames.contains(intent.getPackage())), anyInt()))
                .thenReturn(Collections.singletonList(new ResolveInfo()));
    }

    /**
     * A test-specific subclass of {@link AppLockPermissionReviewActivity}.
     *
     * <p><b>Visibility:</b> This class must be declared {@code public} so that the Android testing
     * framework can instantiate it.
     */
    public static class TestAppLockPermissionReviewActivity
            extends AppLockPermissionReviewActivity {
        static PackageManager sPackageManager;

        @Override
        public PackageManager getPackageManager() {
            return sPackageManager;
        }

        @Override
        protected void populateAppsList(
                AppLockPermissionReviewAdapter appLockPermissionReviewAdapter,
                        ReviewTypeConfig config) {
            final List<AppWithPermissionInfo> appWithPermissionInfo =
                    getAppsWithPermissions(config);
            updateUiWithAppList(appWithPermissionInfo, appLockPermissionReviewAdapter);
        }
    }
}
