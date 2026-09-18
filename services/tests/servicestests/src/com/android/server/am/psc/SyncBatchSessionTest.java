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

package com.android.server.am.psc;

import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_ACTIVITY;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_BACKUP;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_FOLLOW_UP;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_REMOVE_TASK;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_START_SERVICE;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_UI_VISIBILITY;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;

import android.platform.test.annotations.Presubmit;
import android.util.ArraySet;

import org.junit.Test;

import java.util.ArrayList;

@Presubmit
public class SyncBatchSessionTest {

    @Test
    public void partial() {
        ArrayList<String> updates = new ArrayList<>();
        SyncBatchSession session = new SyncBatchSession(
                (proc) -> {},
                (reason) -> updates.add("FULL_UPDATE:" + reason),
                (reason) -> updates.add("PARTIAL_UPDATE:" + reason));

        // Start with arbitrary reason.
        session.start(OOM_ADJ_REASON_BACKUP);

        session.maybeEnqueueProcess(mock(ProcessRecordInternal.class));
        assertThat(session.isActive()).isTrue();
        assertThat(updates).isEmpty();

        session.close();

        assertThat(session.isActive()).isFalse();
        assertThat(updates).containsExactly("PARTIAL_UPDATE:" + OOM_ADJ_REASON_BACKUP);
    }

    @Test
    public void full() {
        ArrayList<String> updates = new ArrayList<>();
        SyncBatchSession session = new SyncBatchSession(
                (proc) -> {},
                (reason) -> updates.add("FULL_UPDATE:" + reason),
                (reason) -> updates.add("PARTIAL_UPDATE:" + reason));

        // Start with arbitrary reason.
        session.start(OOM_ADJ_REASON_FOLLOW_UP);
        session.setFullUpdate();

        assertThat(session.isActive()).isTrue();
        assertThat(updates).isEmpty();

        session.close();
        assertThat(session.isActive()).isFalse();
        assertThat(updates).containsExactly("FULL_UPDATE:" + OOM_ADJ_REASON_FOLLOW_UP);
    }

    @Test
    public void nested() {
        ArrayList<String> updates = new ArrayList<>();
        SyncBatchSession session = new SyncBatchSession(
                (proc) -> {},
                (reason) -> updates.add("FULL_UPDATE:" + reason),
                (reason) -> updates.add("PARTIAL_UPDATE:" + reason));

        // Start with arbitrary reason.
        session.start(OOM_ADJ_REASON_ACTIVITY);

        // Nested start
        session.start(OOM_ADJ_REASON_REMOVE_TASK);

        session.maybeEnqueueProcess(mock(ProcessRecordInternal.class));

        assertThat(session.isActive()).isTrue();
        assertThat(updates).isEmpty();

        session.close();

        assertThat(session.isActive()).isTrue();
        assertThat(updates).isEmpty();

        session.close();

        assertThat(session.isActive()).isFalse();
        assertThat(updates).containsExactly("PARTIAL_UPDATE:" + OOM_ADJ_REASON_ACTIVITY);
    }

    @Test
    public void noEnqueue() {
        ArrayList<String> updates = new ArrayList<>();
        SyncBatchSession session = new SyncBatchSession(
                (proc) -> {},
                (reason) -> updates.add("FULL_UPDATE:" + reason),
                (reason) -> updates.add("PARTIAL_UPDATE:" + reason));

        // Start with arbitrary reason.
        session.start(OOM_ADJ_REASON_BACKUP);

        assertThat(session.isActive()).isTrue();
        assertThat(updates).isEmpty();

        session.close();

        assertThat(session.isActive()).isFalse();
        // No process was enqueued, so no update should have been run.
        assertThat(updates).isEmpty();
    }

    @Test
    public void secondSessionNoEnqueue() {
        ArrayList<String> updates = new ArrayList<>();
        SyncBatchSession session = new SyncBatchSession(
                (proc) -> {},
                (reason) -> updates.add("FULL_UPDATE:" + reason),
                (reason) -> updates.add("PARTIAL_UPDATE:" + reason));

        // Start with arbitrary reason.
        session.start(OOM_ADJ_REASON_START_SERVICE);

        session.maybeEnqueueProcess(mock(ProcessRecordInternal.class));
        assertThat(session.isActive()).isTrue();
        assertThat(updates).isEmpty();

        session.close();

        assertThat(session.isActive()).isFalse();
        assertThat(updates).containsExactly("PARTIAL_UPDATE:" + OOM_ADJ_REASON_START_SERVICE);

        updates.clear();
        // Start a second session.
        session.start(OOM_ADJ_REASON_UI_VISIBILITY);

        assertThat(session.isActive()).isTrue();
        assertThat(updates).isEmpty();

        session.close();

        assertThat(session.isActive()).isFalse();
        // No process was enqueued in the second session, so no update should have been run.
        assertThat(updates).isEmpty();
    }

    @Test
    public void skipProcDueToServiceBindPolicy() {
        ArrayList<String> updates = new ArrayList<>();
        SyncBatchSession session = new SyncBatchSession(
                (proc) -> {},
                (reason) -> updates.add("FULL_UPDATE:" + reason),
                (reason) -> updates.add("PARTIAL_UPDATE:" + reason));

        ProcessRecordInternal procToSkip = mock(ProcessRecordInternal.class);

        // Start with arbitrary reason.
        session.start(OOM_ADJ_REASON_BACKUP);

        session.skipProcDueToServiceBindPolicy(procToSkip);
        session.maybeEnqueueProcess(procToSkip);
        assertThat(session.isActive()).isTrue();
        assertThat(updates).isEmpty();

        session.close();

        assertThat(session.isActive()).isFalse();
        // No update should be triggered because the process was skipped.
        assertThat(updates).isEmpty();
    }

    @Test
    public void dontSkipOtherProcess() {
        ArrayList<String> updates = new ArrayList<>();
        ArraySet<ProcessRecordInternal> enqueuedProcs = new ArraySet<>();
        SyncBatchSession session = new SyncBatchSession(
                (proc) -> enqueuedProcs.add(proc),
                (reason) -> updates.add("FULL_UPDATE:" + reason),
                (reason) -> updates.add("PARTIAL_UPDATE:" + reason));

        ProcessRecordInternal procToSkip = mock(ProcessRecordInternal.class);
        ProcessRecordInternal otherProc = mock(ProcessRecordInternal.class);

        // Start with arbitrary reason.
        session.start(OOM_ADJ_REASON_REMOVE_TASK);

        session.skipProcDueToServiceBindPolicy(procToSkip);
        session.maybeEnqueueProcess(procToSkip);
        session.maybeEnqueueProcess(otherProc);
        assertThat(session.isActive()).isTrue();
        assertThat(updates).isEmpty();

        session.close();

        assertThat(session.isActive()).isFalse();
        assertThat(updates).containsExactly("PARTIAL_UPDATE:" + OOM_ADJ_REASON_REMOVE_TASK);
        assertThat(enqueuedProcs).containsExactly(otherProc);
    }

    @Test
    public void dontSkipOtherLevels() {
        ArrayList<String> updates = new ArrayList<>();
        ArraySet<ProcessRecordInternal> enqueuedProcs = new ArraySet<>();
        SyncBatchSession session = new SyncBatchSession(
                (proc) -> enqueuedProcs.add(proc),
                (reason) -> updates.add("FULL_UPDATE:" + reason),
                (reason) -> updates.add("PARTIAL_UPDATE:" + reason));

        ProcessRecordInternal proc = mock(ProcessRecordInternal.class);

        // Start with arbitrary reason.
        session.start(OOM_ADJ_REASON_REMOVE_TASK);
        // Mark process as skip
        session.skipProcDueToServiceBindPolicy(proc);
        // Start a second level batch session
        session.start(OOM_ADJ_REASON_REMOVE_TASK);
        // Start a third level batch session
        session.start(OOM_ADJ_REASON_REMOVE_TASK);
        // Mark process as skip
        session.skipProcDueToServiceBindPolicy(proc);
        // Close the third level session
        session.close();
        // Try to enqueue the proc. It should be unaffected by the higher and lower nested skips.
        session.maybeEnqueueProcess(proc);
        // Close the second level session
        session.close();
        // Close the original session
        session.close();

        assertThat(session.isActive()).isFalse();
        assertThat(updates).containsExactly("PARTIAL_UPDATE:" + OOM_ADJ_REASON_REMOVE_TASK);
        assertThat(enqueuedProcs).containsExactly(proc);
    }

    @Test
    public void skipMultipleProcesses() {
        ArrayList<String> updates = new ArrayList<>();
        ArraySet<ProcessRecordInternal> enqueuedProcs = new ArraySet<>();
        SyncBatchSession session = new SyncBatchSession(
                (proc) -> enqueuedProcs.add(proc),
                (reason) -> updates.add("FULL_UPDATE:" + reason),
                (reason) -> updates.add("PARTIAL_UPDATE:" + reason));

        ProcessRecordInternal proc1 = mock(ProcessRecordInternal.class);
        ProcessRecordInternal proc2 = mock(ProcessRecordInternal.class);

        // Start with arbitrary reason.
        session.start(OOM_ADJ_REASON_REMOVE_TASK);

        session.skipProcDueToServiceBindPolicy(proc1);
        session.skipProcDueToServiceBindPolicy(proc2);
        session.maybeEnqueueProcess(proc1);
        session.maybeEnqueueProcess(proc2);
        assertThat(session.isActive()).isTrue();
        assertThat(updates).isEmpty();

        session.close();

        assertThat(session.isActive()).isFalse();
        assertThat(updates).isEmpty();
        assertThat(enqueuedProcs).isEmpty();
    }

    @Test
    public void multiEnqueue() {
        ArrayList<String> updates = new ArrayList<>();
        SyncBatchSession session = new SyncBatchSession(
                (proc) -> {},
                (reason) -> updates.add("FULL_UPDATE:" + reason),
                (reason) -> updates.add("PARTIAL_UPDATE:" + reason));

        // Start with arbitrary reason.
        session.start(OOM_ADJ_REASON_ACTIVITY);

        // Enqueue twice
        session.maybeEnqueueProcess(mock(ProcessRecordInternal.class));
        session.maybeEnqueueProcess(mock(ProcessRecordInternal.class));

        assertThat(session.isActive()).isTrue();
        assertThat(updates).isEmpty();

        session.close();

        assertThat(session.isActive()).isFalse();
        // Should only result in one update.
        assertThat(updates).containsExactly("PARTIAL_UPDATE:" + OOM_ADJ_REASON_ACTIVITY);
    }

    @Test
    public void nestedMultiEnqueue() {
        ArrayList<String> updates = new ArrayList<>();
        SyncBatchSession session = new SyncBatchSession(
                (proc) -> {},
                (reason) -> updates.add("FULL_UPDATE:" + reason),
                (reason) -> updates.add("PARTIAL_UPDATE:" + reason));

        // Start with arbitrary reason.
        session.start(OOM_ADJ_REASON_ACTIVITY);

        session.maybeEnqueueProcess(mock(ProcessRecordInternal.class));

        // Nested start
        session.start(OOM_ADJ_REASON_REMOVE_TASK);

        session.maybeEnqueueProcess(mock(ProcessRecordInternal.class));

        assertThat(session.isActive()).isTrue();
        assertThat(updates).isEmpty();

        session.close();

        session.maybeEnqueueProcess(mock(ProcessRecordInternal.class));

        assertThat(session.isActive()).isTrue();
        assertThat(updates).isEmpty();

        session.close();

        assertThat(session.isActive()).isFalse();
        // Should only result in one update.
        assertThat(updates).containsExactly("PARTIAL_UPDATE:" + OOM_ADJ_REASON_ACTIVITY);
    }
}
