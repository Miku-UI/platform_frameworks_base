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
import java.util.Arrays;
import java.util.Objects;

/** A network message used for syncing data between devices. */
@SuppressWarnings("ArrayRecordComponent")
public record NetworkMessage(
        @NonNull String docId,
        @NonNull String senderNodeId,
        @NonNull String receiverNodeId,
        @NonNull byte[] docVersion,
        @NonNull byte[] docUpdate) {
    private static final String TAG = "NetworkMessage";

    /** Serializes this {@link NetworkMessage} into a byte array. */
    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ProtoOutputStream proto = new ProtoOutputStream(baos);
        proto.write(NetworkMessageProto.DOC_ID, docId);
        proto.write(NetworkMessageProto.SENDER_NODE_ID, senderNodeId);
        proto.write(NetworkMessageProto.RECEIVER_NODE_ID, receiverNodeId);
        proto.write(NetworkMessageProto.DOC_VERSION, docVersion);
        proto.write(NetworkMessageProto.DOC_UPDATE, docUpdate);
        proto.flush();
        return baos.toByteArray();
    }

    /** Deserializes a {@link NetworkMessage} record from a byte array. */
    public static NetworkMessage parseFrom(byte[] data) throws IOException {
        ProtoInputStream proto = new ProtoInputStream(data);
        String docId = "";
        String senderNodeId = "";
        String receiverNodeId = "";
        byte[] docVersion = new byte[0];
        byte[] docUpdate = new byte[0];

        try {
            while (proto.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
                switch (proto.getFieldNumber()) {
                    case (int) NetworkMessageProto.DOC_ID ->
                            docId = proto.readString(NetworkMessageProto.DOC_ID);
                    case (int) NetworkMessageProto.SENDER_NODE_ID ->
                            senderNodeId = proto.readString(NetworkMessageProto.SENDER_NODE_ID);
                    case (int) NetworkMessageProto.RECEIVER_NODE_ID ->
                            receiverNodeId = proto.readString(NetworkMessageProto.RECEIVER_NODE_ID);
                    case (int) NetworkMessageProto.DOC_VERSION ->
                            docVersion = proto.readBytes(NetworkMessageProto.DOC_VERSION);
                    case (int) NetworkMessageProto.DOC_UPDATE ->
                            docUpdate = proto.readBytes(NetworkMessageProto.DOC_UPDATE);
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse NetworkMessage from proto", e);
        }
        return new NetworkMessage(docId, senderNodeId, receiverNodeId, docVersion, docUpdate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NetworkMessage that)) return false;
        return Objects.equals(docId, that.docId)
                && Objects.equals(senderNodeId, that.senderNodeId)
                && Objects.equals(receiverNodeId, that.receiverNodeId)
                && Arrays.equals(docVersion, that.docVersion)
                && Arrays.equals(docUpdate, that.docUpdate);
    }
}
