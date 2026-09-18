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

package com.android.server.backup;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.job.JobParameters;
import android.os.PersistableBundle;
import android.platform.test.annotations.Presubmit;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class DelayedRestoreCleanupJobServiceTest {
    private static final String TEST_PACKAGE = "test.package";
    private static final int TEST_USER_ID = 10;

    @Mock private BackupManagerService mBackupManagerService;
    @Mock private JobParameters mJobParameters;

    private DelayedRestoreCleanupJobService mJobService;
    private PersistableBundle mExtras;
    private BackupManagerService mOriginalBackupManagerService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mJobService = new DelayedRestoreCleanupJobService();
        mExtras = new PersistableBundle();

        when(mJobParameters.getExtras()).thenReturn(mExtras);

        // Save original instance
        mOriginalBackupManagerService = BackupManagerService.sInstance;
        BackupManagerService.sInstance = mBackupManagerService;
    }

    @After
    public void tearDown() {
        BackupManagerService.sInstance = mOriginalBackupManagerService;
    }

    @Test
    public void testOnStartJob_success() {
        mExtras.putInt("userId", TEST_USER_ID);
        mExtras.putString("packageName", TEST_PACKAGE);

        boolean result = mJobService.onStartJob(mJobParameters);

        verify(mBackupManagerService)
                .onDelayedRestoreCachedDataExpiredForUser(TEST_USER_ID, TEST_PACKAGE);
        assertThat(result).isFalse();
    }

    @Test
    public void testOnStartJob_nullPackageName_doesNothing() {
        mExtras.putInt("userId", TEST_USER_ID);
        // packageName is null

        boolean result = mJobService.onStartJob(mJobParameters);

        verify(mBackupManagerService, never())
                .onDelayedRestoreCachedDataExpiredForUser(anyInt(), anyString());
        assertThat(result).isFalse();
    }

    @Test
    public void testOnStopJob_returnsFalse() {
        boolean result = mJobService.onStopJob(mJobParameters);

        assertThat(result).isFalse();
    }
}
