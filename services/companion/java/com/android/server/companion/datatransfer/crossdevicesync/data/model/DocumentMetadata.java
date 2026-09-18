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

import android.annotation.NonNull;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Metadata of a document and its records. */
public record DocumentMetadata(
        int schemaVersion, @NonNull List<RecordMetadata> recordMetadataList, byte[] docVersion) {
    private static final String TAG = "DocumentMetadata";

    /** Metadata of a data record in a document. */
    public record RecordMetadata(@NonNull String path, boolean lastModifiedByLocalDevice) {
        /** Writes this {@link RecordMetadata} to a {@link ProtoOutputStream}. */
        public void writeTo(ProtoOutputStream proto, long fieldId) {
            long token = proto.start(fieldId);
            proto.write(DocumentMetadataProto.RecordMetadataProto.PATH, path);
            proto.write(
                    DocumentMetadataProto.RecordMetadataProto.LAST_MODIFIED_BY_LOCAL_DEVICE,
                    lastModifiedByLocalDevice);
            proto.end(token);
        }

        /** Deserializes a {@link RecordMetadata} record from a {@link ProtoInputStream}. */
        public static RecordMetadata parseFrom(ProtoInputStream proto) throws IOException {
            String path = "";
            boolean lastModifiedByLocalDevice = false;

            try {
                while (proto.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
                    switch (proto.getFieldNumber()) {
                        case (int) DocumentMetadataProto.RecordMetadataProto.PATH ->
                                path =
                                        proto.readString(
                                                DocumentMetadataProto.RecordMetadataProto.PATH);
                        case (int)
                                        DocumentMetadataProto.RecordMetadataProto
                                                .LAST_MODIFIED_BY_LOCAL_DEVICE ->
                                lastModifiedByLocalDevice =
                                        proto.readBoolean(
                                                DocumentMetadataProto.RecordMetadataProto
                                                        .LAST_MODIFIED_BY_LOCAL_DEVICE);
                    }
                }
            } catch (Exception e) {
                throw new IOException("Failed to parse RecordMetadata from proto", e);
            }
            return new RecordMetadata(path, lastModifiedByLocalDevice);
        }
    }

    /** Serializes this {@link DocumentMetadata} into a byte array. */
    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ProtoOutputStream proto = new ProtoOutputStream(baos);
        proto.write(DocumentMetadataProto.SCHEMA_VERSION, schemaVersion);
        for (RecordMetadata metadata : recordMetadataList) {
            metadata.writeTo(proto, DocumentMetadataProto.RECORD_METADATA_LIST);
        }
        proto.write(DocumentMetadataProto.DOC_VERSION, docVersion);
        proto.flush();
        return baos.toByteArray();
    }

    /** Deserializes a {@link DocumentMetadata} record from a byte array. */
    public static DocumentMetadata parseFrom(byte[] data) throws IOException {
        ProtoInputStream proto = new ProtoInputStream(data);
        int schemaVersion = 0;
        List<RecordMetadata> recordMetadataList = new ArrayList<>();
        byte[] docVersion = new byte[0];

        try {
            while (proto.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
                switch (proto.getFieldNumber()) {
                    case (int) DocumentMetadataProto.SCHEMA_VERSION ->
                            schemaVersion = proto.readInt(DocumentMetadataProto.SCHEMA_VERSION);
                    case (int) DocumentMetadataProto.RECORD_METADATA_LIST -> {
                        long token = proto.start(DocumentMetadataProto.RECORD_METADATA_LIST);
                        recordMetadataList.add(RecordMetadata.parseFrom(proto));
                        proto.end(token);
                    }
                    case (int) DocumentMetadataProto.DOC_VERSION ->
                            docVersion = proto.readBytes(DocumentMetadataProto.DOC_VERSION);
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse DocumentMetadata from proto", e);
        }
        return new DocumentMetadata(schemaVersion, recordMetadataList, docVersion);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DocumentMetadata that)) {
            return false;
        }
        return schemaVersion == that.schemaVersion
                && Objects.equals(recordMetadataList, that.recordMetadataList)
                && Arrays.equals(docVersion, that.docVersion);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(schemaVersion, recordMetadataList);
        result = 31 * result + Arrays.hashCode(docVersion);
        return result;
    }
}
