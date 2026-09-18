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

package com.android.server.backup.restore;

import static com.google.common.truth.Truth.assertThat;

import android.app.backup.DelayedRestoreRequest;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Set;

@SmallTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class DelayedRestoreJournalTest {
    private static final String PACKAGE_1 = "com.test.package1";
    private static final String PACKAGE_2 = "com.test.package2";
    private static final String REQUESTER_1 = "com.test.requester1";
    private static final String REQUESTER_2 = "com.test.requester2";

    @Rule public TemporaryFolder mTemporaryFolder = new TemporaryFolder();

    private File mJournalDir;
    private DelayedRestoreJournal mJournal;

    @Before
    public void setUp() throws Exception {
        mJournalDir = mTemporaryFolder.newFolder();
        mJournal = new DelayedRestoreJournal(mJournalDir);
    }

    @Test
    public void addRequest_persistsToDisk() throws Exception {
        DelayedRestoreRequest request = new DelayedRestoreRequest.Builder(
                DelayedRestoreRequest.TYPE_APP_INSTALL)
                .setPackageName(PACKAGE_1)
                .build();

        boolean result = mJournal.addRequest(request, REQUESTER_1);
        assertThat(result).isTrue();

        // Reset memory state and reload from disk
        mJournal = new DelayedRestoreJournal(mJournalDir);

        Set<String> requesters = mJournal.getPackagesForRequest(request);
        assertThat(requesters).containsExactly(REQUESTER_1);
    }

    @Test
    public void removeRequest_updatesDisk() throws Exception {
        DelayedRestoreRequest request = new DelayedRestoreRequest.Builder(
                DelayedRestoreRequest.TYPE_APP_INSTALL)
                .setPackageName(PACKAGE_1)
                .build();
        mJournal.addRequest(request, REQUESTER_1);

        boolean result = mJournal.removeRequest(request, REQUESTER_1);
        assertThat(result).isTrue();

        // Reset memory state and reload from disk
        mJournal = new DelayedRestoreJournal(mJournalDir);

        Set<String> requesters = mJournal.getPackagesForRequest(request);
        assertThat(requesters).isEmpty();
    }

    @Test
    public void clearAllRequestsForPackage_updatesDisk() throws Exception {
        DelayedRestoreRequest request1 = new DelayedRestoreRequest.Builder(
                DelayedRestoreRequest.TYPE_APP_INSTALL)
                .setPackageName(PACKAGE_1)
                .build();

        DelayedRestoreRequest request2 = new DelayedRestoreRequest.Builder(
                DelayedRestoreRequest.TYPE_APP_INSTALL)
                .setPackageName(PACKAGE_2)
                .build();

        mJournal.addRequest(request1, REQUESTER_1);
        mJournal.addRequest(request2, REQUESTER_2);

        boolean result = mJournal.clearAllRequestsForPackage(REQUESTER_1);
        assertThat(result).isTrue();

        // Reset memory state and reload from disk
        mJournal = new DelayedRestoreJournal(mJournalDir);

        Set<String> requesters1 = mJournal.getPackagesForRequest(request1);
        assertThat(requesters1).isEmpty(); // Key should be removed as list is empty

        Set<String> requesters2 = mJournal.getPackagesForRequest(request2);
        assertThat(requesters2).containsExactly(REQUESTER_2);
    }

    @Test
    public void addRequests_persistsToDisk() throws Exception {
        DelayedRestoreRequest request1 = new DelayedRestoreRequest.Builder(
                DelayedRestoreRequest.TYPE_APP_INSTALL)
                .setPackageName(PACKAGE_1)
                .build();

        DelayedRestoreRequest request2 = new DelayedRestoreRequest.Builder(
                DelayedRestoreRequest.TYPE_APP_INSTALL)
                .setPackageName(PACKAGE_2)
                .build();

        mJournal.addRequest(request1, REQUESTER_1);
        boolean result = mJournal.addRequest(request2, REQUESTER_2);
        assertThat(result).isTrue();

        // Reset memory state and reload from disk
        mJournal = new DelayedRestoreJournal(mJournalDir);

        Set<String> requesters1 = mJournal.getPackagesForRequest(request1);
        assertThat(requesters1).containsExactly(REQUESTER_1);

        Set<String> requesters2 = mJournal.getPackagesForRequest(request2);
        assertThat(requesters2).containsExactly(REQUESTER_2);
    }

    @Test
    public void removeRequests_updatesDisk() throws Exception {
        DelayedRestoreRequest request1 = new DelayedRestoreRequest.Builder(
                DelayedRestoreRequest.TYPE_APP_INSTALL)
                .setPackageName(PACKAGE_1)
                .build();

        DelayedRestoreRequest request2 = new DelayedRestoreRequest.Builder(
                DelayedRestoreRequest.TYPE_APP_INSTALL)
                .setPackageName(PACKAGE_2)
                .build();

        mJournal.addRequest(request1, REQUESTER_1);
        mJournal.addRequest(request2, REQUESTER_2);

        mJournal.removeRequest(request1, REQUESTER_1);
        boolean result = mJournal.removeRequest(request2, REQUESTER_2);
        assertThat(result).isTrue();

        // Reset memory state and reload from disk
        mJournal = new DelayedRestoreJournal(mJournalDir);

        assertThat(mJournal.getPackagesForRequest(request1)).isEmpty();
        assertThat(mJournal.getPackagesForRequest(request2)).isEmpty();
    }

    @Test
    public void addRequest_ignoresDuplicates() throws Exception {
        DelayedRestoreRequest request = new DelayedRestoreRequest.Builder(
                DelayedRestoreRequest.TYPE_APP_INSTALL)
                .setPackageName(PACKAGE_1)
                .build();

        mJournal.addRequest(request, REQUESTER_1);
        boolean result = mJournal.addRequest(request, REQUESTER_1); // Add again
        assertThat(result).isTrue();

        // Reset memory state and reload from disk
        mJournal = new DelayedRestoreJournal(mJournalDir);

        Set<String> requesters = mJournal.getPackagesForRequest(request);
        assertThat(requesters).containsExactly(REQUESTER_1); // Should still only be one
    }

    @Test
    public void removeRequest_ignoresDuplicates() throws Exception {
        DelayedRestoreRequest request = new DelayedRestoreRequest.Builder(
                DelayedRestoreRequest.TYPE_APP_INSTALL)
                .setPackageName(PACKAGE_1)
                .build();

        mJournal.addRequest(request, REQUESTER_1);

        // Remove twice
        boolean result1 = mJournal.removeRequest(request, REQUESTER_1);
        boolean result2 = mJournal.removeRequest(request, REQUESTER_1);

        assertThat(result1).isTrue();
        assertThat(result2).isTrue();

        // Reset memory state and reload from disk
        mJournal = new DelayedRestoreJournal(mJournalDir);

        assertThat(mJournal.getPackagesForRequest(request)).isEmpty();
    }

    @Test
    public void addRequests_forSameDependency_persistsToDisk() throws Exception {
        DelayedRestoreRequest request = new DelayedRestoreRequest.Builder(
                DelayedRestoreRequest.TYPE_APP_INSTALL)
                .setPackageName(PACKAGE_1)
                .build();

        mJournal.addRequest(request, REQUESTER_1);
        boolean result = mJournal.addRequest(request, REQUESTER_2);
        assertThat(result).isTrue();

        // Reset memory state and reload from disk
        mJournal = new DelayedRestoreJournal(mJournalDir);

        Set<String> requesters = mJournal.getPackagesForRequest(request);
        assertThat(requesters).containsExactly(REQUESTER_1, REQUESTER_2);
    }
}
