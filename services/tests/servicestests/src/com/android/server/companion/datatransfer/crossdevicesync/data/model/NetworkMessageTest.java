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

package com.android.server.companion.datatransfer.crossdevicesync.data.model;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class NetworkMessageTest {

    @Test
    public void serializationSymmetrical() throws IOException {
        NetworkMessage original =
                new NetworkMessage(
                        "doc_123",
                        "sender_node",
                        "receiver_node",
                        new byte[] {0x1, 0x2},
                        new byte[] {0x3, 0x4});

        byte[] serialized = original.toByteArray();
        NetworkMessage parsed = NetworkMessage.parseFrom(serialized);

        assertThat(parsed).isEqualTo(original);
        assertThat(parsed.docId()).isEqualTo("doc_123");
        assertThat(parsed.senderNodeId()).isEqualTo("sender_node");
        assertThat(parsed.receiverNodeId()).isEqualTo("receiver_node");
        assertThat(parsed.docVersion()).isEqualTo(new byte[] {0x1, 0x2});
        assertThat(parsed.docUpdate()).isEqualTo(new byte[] {0x3, 0x4});
    }

    @Test
    public void emptyFields_serializationSymmetrical() throws IOException {
        NetworkMessage original = new NetworkMessage("", "", "", new byte[0], new byte[0]);

        byte[] serialized = original.toByteArray();
        NetworkMessage parsed = NetworkMessage.parseFrom(serialized);

        assertThat(parsed).isEqualTo(original);
    }

    @Test
    public void parseFrom_emptyInput_returnsDefaultObject() throws IOException {
        NetworkMessage parsed = NetworkMessage.parseFrom(new byte[0]);

        assertThat(parsed.docId()).isEmpty();
        assertThat(parsed.senderNodeId()).isEmpty();
        assertThat(parsed.receiverNodeId()).isEmpty();
        assertThat(parsed.docVersion()).isEmpty();
        assertThat(parsed.docUpdate()).isEmpty();
    }

    @Test
    public void parseFrom_unknownFields_ignoresFields() throws IOException {
        // Unknown fields (e.g., field 7, wire type 0, value 123)
        byte[] unknownFields = new byte[] {0x38, 0x7B};
        NetworkMessage parsed = NetworkMessage.parseFrom(unknownFields);

        assertThat(parsed.docId()).isEmpty();
        assertThat(parsed.senderNodeId()).isEmpty();
        assertThat(parsed.receiverNodeId()).isEmpty();
        assertThat(parsed.docVersion()).isEmpty();
        assertThat(parsed.docUpdate()).isEmpty();
    }
}
