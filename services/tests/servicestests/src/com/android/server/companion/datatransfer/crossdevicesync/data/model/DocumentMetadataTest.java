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
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class DocumentMetadataTest {

    @Test
    public void serializationSymmetrical() throws IOException {
        List<DocumentMetadata.RecordMetadata> records =
                List.of(
                        new DocumentMetadata.RecordMetadata("/path/1", true),
                        new DocumentMetadata.RecordMetadata("/path/2", false));
        DocumentMetadata original = new DocumentMetadata(5, records, new byte[] {1, 2, 3});

        byte[] serialized = original.toByteArray();
        DocumentMetadata parsed = DocumentMetadata.parseFrom(serialized);

        assertThat(parsed).isEqualTo(original);
        assertThat(parsed.schemaVersion()).isEqualTo(5);
        assertThat(parsed.recordMetadataList()).hasSize(2);
        assertThat(parsed.recordMetadataList().get(0).path()).isEqualTo("/path/1");
        assertThat(parsed.recordMetadataList().get(0).lastModifiedByLocalDevice()).isTrue();
        assertThat(Arrays.equals(parsed.docVersion(), original.docVersion())).isTrue();
    }

    @Test
    public void emptyRecords_serializationSymmetrical() throws IOException {
        DocumentMetadata original = new DocumentMetadata(0, List.of(), new byte[0]);

        byte[] serialized = original.toByteArray();
        DocumentMetadata parsed = DocumentMetadata.parseFrom(serialized);

        assertThat(parsed).isEqualTo(original);
    }

    @Test
    public void parseFrom_emptyInput_returnsDefaultObject() throws IOException {
        DocumentMetadata parsed = DocumentMetadata.parseFrom(new byte[0]);

        assertThat(parsed.schemaVersion()).isEqualTo(0);
        assertThat(parsed.recordMetadataList()).isEmpty();
    }

    @Test
    public void parseFrom_unknownFields_ignoresFields() throws IOException {
        // Unknown fields (e.g., field 7, wire type 0, value 123)
        byte[] unknownFields = new byte[] {0x38, 0x7B};
        DocumentMetadata parsed = DocumentMetadata.parseFrom(unknownFields);

        assertThat(parsed.schemaVersion()).isEqualTo(0);
        assertThat(parsed.recordMetadataList()).isEmpty();
    }

    @Test
    public void hashCode_consistentWithEquals() {
        List<DocumentMetadata.RecordMetadata> records1 =
                List.of(new DocumentMetadata.RecordMetadata("/path/1", true));
        List<DocumentMetadata.RecordMetadata> records2 =
                List.of(new DocumentMetadata.RecordMetadata("/path/1", true));
        DocumentMetadata metadata1 = new DocumentMetadata(5, records1, new byte[] {1, 2, 3});
        DocumentMetadata metadata2 = new DocumentMetadata(5, records2, new byte[] {1, 2, 3});

        assertThat(metadata1).isEqualTo(metadata2);
        assertThat(metadata1.hashCode()).isEqualTo(metadata2.hashCode());
    }
}
