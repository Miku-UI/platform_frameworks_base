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

package com.android.server.companion.datatransfer.crossdevicesync.network.model;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class BatchedMessageTest {

    @Test
    public void serializationSymmetrical() throws IOException {
        List<Message> messages =
                List.of(
                        new Message(1L, "net1", new byte[] {0xA}),
                        new Message(2L, "net2", new byte[] {0xB, 0xC}));
        List<Long> acks = List.of(100L, 200L, 300L);
        BatchedMessage original = new BatchedMessage(messages, acks, 999L);

        byte[] serialized = original.toByteArray();
        BatchedMessage parsed = BatchedMessage.parseFrom(serialized);

        assertThat(parsed).isEqualTo(original);
        assertThat(parsed.messages()).hasSize(2);
        assertThat(parsed.ackIds()).containsExactly(100L, 200L, 300L).inOrder();
        assertThat(parsed.senderInstanceId()).isEqualTo(999L);
    }

    @Test
    public void emptyLists_serializationSymmetrical() throws IOException {
        BatchedMessage original = new BatchedMessage(List.of(), List.of(), 0L);

        byte[] serialized = original.toByteArray();
        BatchedMessage parsed = BatchedMessage.parseFrom(serialized);

        assertThat(parsed).isEqualTo(original);
    }

    @Test
    public void parseFrom_emptyInput_returnsDefaultObject() throws IOException {
        BatchedMessage parsed = BatchedMessage.parseFrom(new byte[0]);

        assertThat(parsed.messages()).isEmpty();
        assertThat(parsed.ackIds()).isEmpty();
        assertThat(parsed.senderInstanceId()).isEqualTo(0L);
    }

    @Test
    public void parseFrom_unknownFields_ignoresFields() throws IOException {
        // Unknown fields (e.g., field 7, wire type 0, value 123)
        byte[] unknownFields = new byte[] {0x38, 0x7B};
        BatchedMessage parsed = BatchedMessage.parseFrom(unknownFields);

        assertThat(parsed.messages()).isEmpty();
        assertThat(parsed.ackIds()).isEmpty();
        assertThat(parsed.senderInstanceId()).isEqualTo(0L);
    }
}
