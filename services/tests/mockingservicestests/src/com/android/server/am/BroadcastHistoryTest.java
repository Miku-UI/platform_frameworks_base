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

package com.android.server.am;

import static com.google.common.truth.Truth.assertThat;

import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Pair;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;

/**
 * Tests for {@link BroadcastHistory}.
 */
@SmallTest
@RunWith(MockitoJUnitRunner.class)
public class BroadcastHistoryTest extends BaseBroadcastQueueTest {
    private static final String TAG = "BroadcastHistoryTest";

    private static final String TEST_ACTION = "com.example.action";
    private static final String TEST_ACTION1 = "com.example.action_1";
    private static final String TEST_ACTION2 = "com.example.action_2";

    private BroadcastHistory mBroadcastHistory;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        final BroadcastConstants constants = new BroadcastConstants("bcast_constants");
        mBroadcastHistory = new BroadcastHistory(constants);
    }

    @Override
    public String getTag() {
        return TAG;
    }

    private BroadcastRecord createBroadcastRecord(String callerPackage,
            String action, long enqueueTime) {
        return createBroadcastRecord(callerPackage, callerPackage, action, enqueueTime);
    }

    private BroadcastRecord createBroadcastRecord(String callerPackage,
            String callerProcess, String action, long enqueueTime) {
        final ProcessRecord callerApp = makeProcessRecord(makeApplicationInfo(
                callerPackage, callerProcess, UserHandle.USER_SYSTEM));
        final BroadcastRecord br = new BroadcastRecordBuilder()
                .setIntentAction(action)
                .setCallerApp(callerApp)
                .setCallerPackage(callerPackage)
                .setCallingUid(callerApp.uid)
                .build();
        br.enqueueTime = enqueueTime;
        return br;
    }

    @Test
    public void testGetPendingBroadcastCountForUid() {
        mBroadcastHistory.onBroadcastEnqueuedLocked(
                createBroadcastRecord(PACKAGE_GREEN, TEST_ACTION1, 1000));
        mBroadcastHistory.onBroadcastEnqueuedLocked(
                createBroadcastRecord(PACKAGE_GREEN, TEST_ACTION2, 2000));
        mBroadcastHistory.onBroadcastEnqueuedLocked(
                createBroadcastRecord(PACKAGE_RED, TEST_ACTION1, 3000));

        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderUid(
                getUidForPackage(PACKAGE_GREEN))).isEqualTo(2);
        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderUid(
                getUidForPackage(PACKAGE_RED))).isEqualTo(1);
        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderUid(
                getUidForPackage(PACKAGE_BLUE))).isEqualTo(0);
    }

    @Test
    public void testGetPendingBroadcastCountForSenderProcess() {
        mBroadcastHistory.onBroadcastEnqueuedLocked(
                createBroadcastRecord(PACKAGE_GREEN, PROCESS_GREEN, TEST_ACTION1, 1000));
        mBroadcastHistory.onBroadcastEnqueuedLocked(
                createBroadcastRecord(PACKAGE_GREEN, PROCESS_GREEN, TEST_ACTION2, 2000));
        mBroadcastHistory.onBroadcastEnqueuedLocked(
                createBroadcastRecord(PACKAGE_GREEN, PROCESS_RED, TEST_ACTION2, 3000));
        mBroadcastHistory.onBroadcastEnqueuedLocked(
                createBroadcastRecord(PACKAGE_RED, PROCESS_GREEN, TEST_ACTION1, 4000));

        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderProcess(
                getUidForPackage(PACKAGE_GREEN), PROCESS_GREEN)).isEqualTo(2);
        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderProcess(
                getUidForPackage(PACKAGE_GREEN), PROCESS_RED)).isEqualTo(1);
        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderProcess(
                getUidForPackage(PACKAGE_GREEN), PROCESS_BLUE)).isEqualTo(0);
        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderProcess(
                getUidForPackage(PACKAGE_RED), PROCESS_GREEN)).isEqualTo(1);
    }

    @Test
    public void testGetPendingBroadcastCountSince() {
        mBroadcastHistory.onBroadcastEnqueuedLocked(
                createBroadcastRecord(PACKAGE_GREEN, PROCESS_GREEN, TEST_ACTION1, 1000));
        mBroadcastHistory.onBroadcastEnqueuedLocked(
                createBroadcastRecord(PACKAGE_GREEN, PROCESS_GREEN, TEST_ACTION2, 2000));
        mBroadcastHistory.onBroadcastEnqueuedLocked(
                createBroadcastRecord(PACKAGE_GREEN, PROCESS_GREEN, TEST_ACTION, 3000));
        mBroadcastHistory.onBroadcastEnqueuedLocked(
                createBroadcastRecord(PACKAGE_GREEN, PROCESS_RED, TEST_ACTION, 3000));

        // Verify the pending broadcast count for green process.
        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderProcessSince(
                getUidForPackage(PACKAGE_GREEN), PROCESS_GREEN, 500))
                .isEqualTo(3);
        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderProcessSince(
                getUidForPackage(PACKAGE_GREEN), PROCESS_GREEN, 1500))
                .isEqualTo(2);
        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderProcessSince(
                getUidForPackage(PACKAGE_GREEN), PROCESS_GREEN, 2500))
                .isEqualTo(1);
        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderProcessSince(
                getUidForPackage(PACKAGE_GREEN), PROCESS_GREEN, 3500))
                .isEqualTo(0);

        // Verify the pending broadcast count for red process.
        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderProcessSince(
                getUidForPackage(PACKAGE_GREEN), PROCESS_RED, 500))
                .isEqualTo(1);
        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderProcessSince(
                getUidForPackage(PACKAGE_GREEN), PROCESS_RED, 1500))
                .isEqualTo(1);
        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderProcessSince(
                getUidForPackage(PACKAGE_GREEN), PROCESS_RED, 2500))
                .isEqualTo(1);
        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderProcessSince(
                getUidForPackage(PACKAGE_GREEN), PROCESS_RED, 3500))
                .isEqualTo(0);
    }

    @Test
    public void testAppendPendingBroadcastsSummaryForUid() {
        final int intentActionCounts = 10;
        final ArrayList<Pair<String, Integer>> broadcastCounts = new ArrayList();
        for (int i = 1; i <= intentActionCounts; i++) {
            broadcastCounts.add(Pair.create(TEST_ACTION + "_" + i, i));
        }

        for (int i = 0; i < broadcastCounts.size(); i++) {
            final String action = broadcastCounts.get(i).first;
            final int count = broadcastCounts.get(i).second;
            for (int j = 0; j < count; ++j) {
                mBroadcastHistory.onBroadcastEnqueuedLocked(createBroadcastRecord(
                        PACKAGE_GREEN, action, SystemClock.uptimeMillis()));
            }
        }

        final StringBuilder sb = new StringBuilder();
        mBroadcastHistory.appendPendingBroadcastsSummaryForUid(sb, getUidForPackage(PACKAGE_GREEN));
        final String summary = sb.toString();
        final int broadcastCountsSize = broadcastCounts.size();
        for (int i = 0; i < BroadcastHistory.TOP_N_INTENTS_TO_DUMP; i++) {
            // Since the actions with the highest count will be dumped, query the expected action
            // and the corresponding count from the end of the list.
            final String expectedAction = broadcastCounts.get(broadcastCountsSize - 1 - i).first;
            final int expectedCount = broadcastCounts.get(broadcastCountsSize - 1 - i).second;
            assertThat(summary).contains(expectedAction + ": " + expectedCount);
        }
    }

    @Test
    public void testNeverBroadcasted() {
        final int uid = getUidForPackage(PACKAGE_BLUE);

        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderUid(uid))
                .isEqualTo(0);
        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderProcess(uid, PROCESS_GREEN))
                .isEqualTo(0);
        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderProcessSince(
                uid, PROCESS_GREEN, 0)).isEqualTo(0);
    }

    @Test
    public void testPendingCountGoesDownAfterFinished() {
        final BroadcastRecord record = createBroadcastRecord(PACKAGE_GREEN, PROCESS_GREEN,
                TEST_ACTION, SystemClock.uptimeMillis());

        mBroadcastHistory.onBroadcastEnqueuedLocked(record);
        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderUid(
                getUidForPackage(PACKAGE_GREEN)))
                .isEqualTo(1);
        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderProcess(
                getUidForPackage(PACKAGE_GREEN), PROCESS_GREEN))
                .isEqualTo(1);

        mBroadcastHistory.onBroadcastFinishedLocked(record);
        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderUid(
                getUidForPackage(PACKAGE_GREEN)))
                .isEqualTo(0);
        assertThat(mBroadcastHistory.getPendingBroadcastCountForSenderProcess(
                getUidForPackage(PACKAGE_GREEN), PROCESS_GREEN))
                .isEqualTo(0);
    }
}
