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

package com.android.server.appwindowlayout;

import static android.content.pm.PackageManager.USER_MIN_ASPECT_RATIO_3_2;
import static android.content.pm.PackageManager.USER_MIN_ASPECT_RATIO_FULLSCREEN;

import static com.android.server.appwindowlayout.AppWindowLayoutSettingsRestoreStorage.ASPECT_RATIO_STAGED_DATA_PREFS;
import static com.android.server.appwindowlayout.AppWindowLayoutSettingsRestoreStorage.KEY_STAGED_DATA_TIME;
import static com.android.server.appwindowlayout.AppWindowLayoutSettingsRestoreStorage.RESTORE_TIME_STAGED_DATA_PREFS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.testutils.FakeInstantSource;
import com.android.server.testutils.FakeSharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.time.Duration;

/**
 * Unit tests for the {@link AppWindowLayoutSettingsRestoreStorage}.
 *
 * To run this test: atest FrameworksServicesTests_android_server_appwindowlayout
 */
@RunWith(AndroidJUnit4.class)
public class AppWindowLayoutSettingsRestoreStorageTest {
    private static final String DEFAULT_PACKAGE_NAME = "com.android.testapp";
    private static final String OTHER_PACKAGE_NAME = "com.android.anotherapp";

    private static final int DEFAULT_USER_ID = 0;

    private AppWindowLayoutSettingsRestoreStorage
            mRestoreStorage;

    private Context mContext;
    private FakeInstantSource mInstantSource;
    private FakeSharedPreferences mFakeUserAspectRatioSharedPreferences;
    private FakeSharedPreferences mFakeRestoreTimeSharedPreferences;
    private String mTestPackageName;

    @Before
    public void setUp() throws Exception {
        mContext = spy(ApplicationProvider.getApplicationContext());
        mTestPackageName = mContext.getPackageName();
        mInstantSource = new FakeInstantSource();
        setupMockSharedPreferences();
        mRestoreStorage = new AppWindowLayoutSettingsRestoreStorage(mContext, DEFAULT_USER_ID,
                mInstantSource);
    }

    @Test
    public void testCreateRestoreStorage_userAspectRatioSharedPreferencesCreated() {
        verify(mContext, times(2)).createDeviceProtectedStorageContext();
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
        verify(mContext, times(2)).getSharedPreferences(fileCaptor.capture(),
                eq(Context.MODE_PRIVATE));
        assertFileCreatedWithName(fileCaptor, mContext.getPackageName() + "."
                + ASPECT_RATIO_STAGED_DATA_PREFS);
    }

    @Test
    public void testCreateRestoreStorage_timeOfRestoreSharedPreferencesCreated() {
        verify(mContext, times(2)).createDeviceProtectedStorageContext();
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
        verify(mContext, times(2)).getSharedPreferences(fileCaptor.capture(),
                eq(Context.MODE_PRIVATE));
        assertFileCreatedWithName(fileCaptor, mContext.getPackageName() + "."
                + RESTORE_TIME_STAGED_DATA_PREFS);
    }

    @Test
    public void testSetStagedRestoreTime_currentTimeStoredInSharedPreferences() {
        mRestoreStorage.setStagedRestoreTime();

        assertEquals(mInstantSource.millis(), mFakeRestoreTimeSharedPreferences.getLong(
                KEY_STAGED_DATA_TIME, -1));
    }

    @Test
    public void testStorePackageAndUserAspectRatio() {
        mRestoreStorage.storePackageAndUserAspectRatio(DEFAULT_PACKAGE_NAME,
                USER_MIN_ASPECT_RATIO_FULLSCREEN);

        assertEquals(USER_MIN_ASPECT_RATIO_FULLSCREEN,
                mFakeUserAspectRatioSharedPreferences.getInt(DEFAULT_PACKAGE_NAME, -1));
    }

    @Test
    public void testGetAndRemoveUserAspectRatioForPackage_dataLessThanAWeekOld_dataReturned() {
        mRestoreStorage.storePackageAndUserAspectRatio(DEFAULT_PACKAGE_NAME,
                USER_MIN_ASPECT_RATIO_FULLSCREEN);
        mRestoreStorage.setStagedRestoreTime();

        mInstantSource.advanceByMillis(Duration.ofDays(6).toMillis());

        final int aspectRatio = mRestoreStorage
                .getAndRemoveUserAspectRatioForPackage(DEFAULT_PACKAGE_NAME);
        assertEquals(USER_MIN_ASPECT_RATIO_FULLSCREEN, aspectRatio);
    }

    @Test
    public void testGetAndRemoveUserAspectRatioForPackage_weekOldDataCleanedUpAndNothingReturned() {
        mRestoreStorage.storePackageAndUserAspectRatio(DEFAULT_PACKAGE_NAME,
                USER_MIN_ASPECT_RATIO_FULLSCREEN);
        mRestoreStorage.storePackageAndUserAspectRatio(OTHER_PACKAGE_NAME,
                USER_MIN_ASPECT_RATIO_3_2);
        mRestoreStorage.setStagedRestoreTime();

        mInstantSource.advanceByMillis(Duration.ofDays(8).toMillis());

        mRestoreStorage.getAndRemoveUserAspectRatioForPackage(DEFAULT_PACKAGE_NAME);

        assertFalse(mFakeUserAspectRatioSharedPreferences.contains(DEFAULT_PACKAGE_NAME));
        assertFalse(mFakeUserAspectRatioSharedPreferences.contains(OTHER_PACKAGE_NAME));
    }

    private void setupMockSharedPreferences() {
        mFakeUserAspectRatioSharedPreferences = new FakeSharedPreferences();
        mFakeRestoreTimeSharedPreferences = new FakeSharedPreferences();
        doReturn(mContext).when(mContext).createDeviceProtectedStorageContext();
        doReturn(mFakeUserAspectRatioSharedPreferences).when(mContext).getSharedPreferences(
                eq(createFile(
                        AppWindowLayoutSettingsRestoreStorage.ASPECT_RATIO_STAGED_DATA_PREFS)),
                eq(Context.MODE_PRIVATE));
        doReturn(mFakeRestoreTimeSharedPreferences).when(mContext).getSharedPreferences(
                eq(createFile(RESTORE_TIME_STAGED_DATA_PREFS)),
                eq(Context.MODE_PRIVATE));
    }

    private File createFile(@NonNull String filename) {
        return new File(Environment.getDataSystemDeDirectory(DEFAULT_USER_ID),
                mTestPackageName + "." + filename);
    }

    private void assertFileCreatedWithName(@NonNull ArgumentCaptor<File> fileArgumentCaptor,
            @NonNull String name) {
        assertEquals(1, fileArgumentCaptor.getAllValues().stream().filter(
                file -> file.getName().equals(name)).count());
    }
}
