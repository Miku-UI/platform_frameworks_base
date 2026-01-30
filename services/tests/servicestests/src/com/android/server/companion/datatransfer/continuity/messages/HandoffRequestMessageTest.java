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

package com.android.server.companion.datatransfer.continuity.messages;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class HandoffRequestMessageTest {

    @Test
    public void testConstructor_fromTaskId() {
        int taskId = 1;
        HandoffRequestMessage handoffRequestMessage = new HandoffRequestMessage(taskId);
        assertThat(handoffRequestMessage.taskId()).isEqualTo(taskId);
    }

    @Test
    public void testConstructor_fromProto() throws IOException {
        int taskId = 1;
        final ProtoOutputStream pos = new ProtoOutputStream();
        pos.write(android.companion.HandoffRequestMessage.TASK_ID, taskId);
        pos.flush();

        ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        HandoffRequestMessage handoffRequestMessage = HandoffRequestMessage.readFromProto(pis);

        assertThat(handoffRequestMessage.taskId()).isEqualTo(taskId);
    }

    @Test
    public void testConstructor_fromProto_noTaskId_returnsZero() throws IOException {
        final ProtoOutputStream pos = new ProtoOutputStream();
        pos.flush();
        ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        HandoffRequestMessage handoffRequestMessage = HandoffRequestMessage.readFromProto(pis);
        assertThat(handoffRequestMessage.taskId()).isEqualTo(0);
    }

    @Test
    public void testWriteAndRead_roundTrip_works() throws IOException {
        HandoffRequestMessage expectedMessage = new HandoffRequestMessage(1);

        final ProtoOutputStream pos = new ProtoOutputStream();
        expectedMessage.writeToProto(pos);
        pos.flush();

        final ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        final HandoffRequestMessage actualMessage = HandoffRequestMessage.readFromProto(pis);

        assertThat(actualMessage).isEqualTo(expectedMessage);
    }

    @Test
    public void testGetFieldNumber_returnsCorrectValue() {
        HandoffRequestMessage handoffRequestMessage = new HandoffRequestMessage(1);
        assertThat(handoffRequestMessage.getFieldNumber())
            .isEqualTo(android.companion.TaskContinuityMessage.HANDOFF_REQUEST);
    }
}