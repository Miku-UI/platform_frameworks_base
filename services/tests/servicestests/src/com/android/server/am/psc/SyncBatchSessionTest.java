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

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;

import org.junit.Test;

import java.util.ArrayList;

@Presubmit
public class SyncBatchSessionTest {
    @Test
    public void syncBatchSession() {
        ArrayList<String> updates = new ArrayList<>();
        SyncBatchSession session = new SyncBatchSession(
                (reason) -> updates.add("FULL_UPDATE:" + reason),
                (reason) -> updates.add("PARTIAL_UPDATE:" + reason));

        // Start with arbitrary reason.
        session.start(OOM_ADJ_REASON_BACKUP);

        assertThat(session.isActive()).isTrue();
        assertThat(updates).isEmpty();

        session.close();

        assertThat(session.isActive()).isFalse();
        assertThat(updates).containsExactly("PARTIAL_UPDATE:" + OOM_ADJ_REASON_BACKUP);
    }

    @Test
    public void syncBatchSession_full() {
        ArrayList<String> updates = new ArrayList<>();
        SyncBatchSession session = new SyncBatchSession(
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
    public void syncBatchSession_nested() {
        ArrayList<String> updates = new ArrayList<>();
        SyncBatchSession session = new SyncBatchSession(
                (reason) -> updates.add("FULL_UPDATE:" + reason),
                (reason) -> updates.add("PARTIAL_UPDATE:" + reason));

        // Start with arbitrary reason.
        session.start(OOM_ADJ_REASON_ACTIVITY);

        // Nested start
        session.start(OOM_ADJ_REASON_REMOVE_TASK);

        assertThat(session.isActive()).isTrue();
        assertThat(updates).isEmpty();

        session.close();

        assertThat(session.isActive()).isTrue();
        assertThat(updates).isEmpty();

        session.close();

        assertThat(session.isActive()).isFalse();
        assertThat(updates).containsExactly("PARTIAL_UPDATE:" + OOM_ADJ_REASON_ACTIVITY);
    }
}
