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

@RunWith(AndroidJUnit4.class)
public class MessageTest {

    @Test
    public void serializationSymmetrical() throws IOException {
        Message original = new Message(12345L, "test_network_id", new byte[] {1, 2, 3, 4, 5});

        byte[] serialized = original.toByteArray();
        Message parsed = Message.parseFrom(serialized);

        assertThat(parsed).isEqualTo(original);
        assertThat(parsed.handleId()).isEqualTo(12345L);
        assertThat(parsed.networkId()).isEqualTo("test_network_id");
        assertThat(parsed.payload()).isEqualTo(new byte[] {1, 2, 3, 4, 5});
    }

    @Test
    public void emptyPayload_serializationSymmetrical() throws IOException {
        Message original = new Message(0L, "", new byte[0]);

        byte[] serialized = original.toByteArray();
        Message parsed = Message.parseFrom(serialized);

        assertThat(parsed).isEqualTo(original);
    }

    @Test
    public void parseFrom_emptyInput_returnsDefaultObject() throws IOException {
        Message parsed = Message.parseFrom(new byte[0]);

        assertThat(parsed.handleId()).isEqualTo(0L);
        assertThat(parsed.networkId()).isEmpty();
        assertThat(parsed.payload()).isEmpty();
    }

    @Test
    public void parseFrom_unknownFields_ignoresFields() throws IOException {
        // Unknown fields (e.g., field 7, wire type 0, value 123)
        byte[] unknownFields = new byte[] {0x38, 0x7B};
        Message parsed = Message.parseFrom(unknownFields);

        assertThat(parsed.handleId()).isEqualTo(0L);
        assertThat(parsed.networkId()).isEmpty();
        assertThat(parsed.payload()).isEmpty();
    }
}
