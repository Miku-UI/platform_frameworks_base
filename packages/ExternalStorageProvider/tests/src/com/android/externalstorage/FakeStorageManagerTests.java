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

package com.android.externalstorage;

import static com.android.externalstorage.ExternalStorageProvider.AUTHORITY;
import static com.android.internal.content.storage.flags.Flags.FLAG_USE_FILE_SYSTEM_PROVIDER_SEARCH_LIMITS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests in this class use a fake StorageManager with a fake volume (backed by real test files).
 */
@RunWith(AndroidJUnit4.class)
public class FakeStorageManagerTests {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @NonNull
    private final File mTemporaryFilesDir = sTargetContext.getCacheDir();

    @Rule
    public final CleanupTemporaryFilesRule mCleanupTemporaryFilesRule =
            new CleanupTemporaryFilesRule(mTemporaryFilesDir);

    @NonNull
    private static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();
    @NonNull
    private static final Context sTargetContext = sInstrumentation.getTargetContext();

    private ExternalStorageProvider mExternalStorageProvider;

    final String mRootId = "fakeVolume";
    final int mFileSystemProviderMaxResultLimit = 1000;  // FileSystemProvider.MAX_RESULTS

    @Before
    public void setUp() throws IOException {
        mExternalStorageProvider = new ExternalStorageProvider();

        attachFakeStorageManagerVolume(mRootId, mFileSystemProviderMaxResultLimit + 1,
                mTemporaryFilesDir);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_USE_FILE_SYSTEM_PROVIDER_SEARCH_LIMITS})
    public void querySearchDocuments_supportsLimits() throws IOException {
        // Test that not specifying search limit uses the legacy limit from FileSystemProvider.
        final Bundle queryArgs = new Bundle();
        queryArgs.putString(DocumentsContract.QUERY_ARG_DISPLAY_NAME, "test");
        Cursor c = mExternalStorageProvider.querySearchDocuments(mRootId, null, queryArgs);
        assertEquals(23 /* FileSystemProvider.DEFAULT_SEARCH_RESULT_LIMIT */, c.getCount());

        // Test that specifying non-negative search limits works.
        queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT, 1);
        c = mExternalStorageProvider.querySearchDocuments(mRootId, null, queryArgs);
        assertEquals(1, c.getCount());

        // Test that specifying a search limit larger than the maximum is clamped.
        queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT,
                mFileSystemProviderMaxResultLimit + 1);
        c = mExternalStorageProvider.querySearchDocuments(mRootId, null, queryArgs);
        assertEquals(mFileSystemProviderMaxResultLimit, c.getCount());

        // Test that specifying zero results is honoured.
        queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT, 0);
        c = mExternalStorageProvider.querySearchDocuments(mRootId, null, queryArgs);
        assertEquals(0, c.getCount());

        // Test that specifying a negative limit uses the default.
        queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT, -1);
        c = mExternalStorageProvider.querySearchDocuments(mRootId, null, queryArgs);
        assertEquals(23 /* FileSystemProvider.DEFAULT_SEARCH_RESULT_LIMIT */, c.getCount());
    }

    @Test
    @RequiresFlagsDisabled({FLAG_USE_FILE_SYSTEM_PROVIDER_SEARCH_LIMITS})
    public void querySearchDocuments_doesNotSupportLimits() throws IOException {
        // Test that not specifying search limit uses the legacy limit from FileSystemProvider.
        final Bundle queryArgs = new Bundle();
        queryArgs.putString(DocumentsContract.QUERY_ARG_DISPLAY_NAME, "test");
        Cursor c = mExternalStorageProvider.querySearchDocuments(mRootId, null, queryArgs);
        assertEquals(23 /* FileSystemProvider.DEFAULT_SEARCH_RESULT_LIMIT */, c.getCount());

        // Test that specifying non-negative search limits is ignored.
        queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT, 1);
        c = mExternalStorageProvider.querySearchDocuments(mRootId, null, queryArgs);
        assertEquals(23, c.getCount());
    }

    /**
     * Attach a fake StorageManager to the Context and a fake volume to the StorageManager that
     * contains a deterministic set of test files.
     *
     * @param rootId for the fake Volume
     * @param testFileCount number of test files to create
     * @param temporaryFilesDir the temporary directory in which to create test files
     */
    private void attachFakeStorageManagerVolume(String rootId, int testFileCount,
            File temporaryFilesDir)
            throws IOException {
        // Set up a temporary directory in the test APK's cache directory and create a fake
        // volume to present via StorageManager.
        //
        // Mock out enough infrastructure so that ExternalStorageProvider finds our test volume.
        final Context mockContext = mock(Context.class);
        final UserManager mockUserManager = mock(UserManager.class);
        final ContentResolver mockContentResolver = mock(ContentResolver.class);
        final StorageManager spyStorageManager =
                spy((StorageManager) sTargetContext.getSystemService(Context.STORAGE_SERVICE));

        when(mockContext.getSystemService(Context.STORAGE_SERVICE))
                .thenReturn(spyStorageManager);
        when(mockContext.getSystemService(Context.USER_SERVICE))
                .thenReturn(mockUserManager);
        when(mockContext.getContentResolver())
                .thenReturn(mockContentResolver);

        // Clean up from prior tests to ensure deterministic set of test files.
        CleanupTemporaryFilesRule.removeFilesRecursively(temporaryFilesDir);

        // Create the test volume root directory and test files.
        final File rootDir = new File(temporaryFilesDir, rootId);
        assertTrue(rootDir.mkdir());

        for (int i = 0; i < testFileCount; i++) {
            File testFile = new File(rootDir.getPath(), "test" + i + ".txt");
            assertTrue(testFile.createNewFile());
        }

        // Create the fake volume that the mock StorageManager will return.
        final VolumeInfo volume = new VolumeInfo(rootId, VolumeInfo.TYPE_PUBLIC, null, null);
        volume.fsUuid = rootId;
        volume.path = rootDir.getPath();
        volume.state = VolumeInfo.STATE_MOUNTED;
        volume.mountUserId = UserHandle.myUserId();

        final List<VolumeInfo> volumes = new ArrayList<VolumeInfo>();
        volumes.add(volume);
        when(spyStorageManager.getVolumes()).thenReturn(volumes);

        // Get ExternalStorageProvider to scan for volumes and create its roots.
        final ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.authority = AUTHORITY;
        sInstrumentation.runOnMainSync(() ->
                mExternalStorageProvider.attachInfoForTesting(mockContext, providerInfo));
    }
}
