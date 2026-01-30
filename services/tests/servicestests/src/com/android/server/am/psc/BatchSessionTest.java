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
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_NONE;
import static android.app.ActivityManagerInternal.OOM_ADJ_REASON_REMOVE_TASK;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

import java.util.ArrayList;

public class BatchSessionTest {
    @Test
    public void batchSession_initialState() {
        ArrayList<String> updates = new ArrayList<>();
        BatchSession session = new BatchSession() {
            @Override
            protected void onClose() {
                updates.add("CLOSED:" + mUpdateReason);
            }
        };

        assertThat(session.isActive()).isFalse();
        assertThat(updates).isEmpty();
    }


    @Test
    public void batchSession_startClose() {
        ArrayList<String> updates = new ArrayList<>();
        BatchSession session = new BatchSession() {
            @Override
            protected void onClose() {
                updates.add("CLOSED:" + mUpdateReason);
            }
        };

        session.start(OOM_ADJ_REASON_NONE);

        assertThat(session.isActive()).isTrue();
        assertThat(updates).isEmpty();

        session.close();

        assertThat(session.isActive()).isFalse();
        assertThat(updates).containsExactly("CLOSED:" + OOM_ADJ_REASON_NONE);
    }
    @Test
    public void batchSession_nested() {
        ArrayList<String> updates = new ArrayList<>();
        BatchSession session = new BatchSession() {
            @Override
            protected void onClose() {
                updates.add("CLOSED:" + mUpdateReason);
            }
        };

        // Start with arbitrary reason.
        session.start(OOM_ADJ_REASON_ACTIVITY);

        // Nested start with a reason that will be ignored.
        session.start(OOM_ADJ_REASON_REMOVE_TASK);

        assertThat(session.isActive()).isTrue();
        assertThat(updates).isEmpty();

        session.close();

        assertThat(session.isActive()).isTrue();
        assertThat(updates).isEmpty();

        session.close();

        assertThat(session.isActive()).isFalse();
        assertThat(updates).containsExactly("CLOSED:" + OOM_ADJ_REASON_ACTIVITY);
    }
}
