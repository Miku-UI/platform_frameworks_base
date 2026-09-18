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

import android.annotation.NonNull;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** A batched message format that groups multiple messages and multiple ACKs together. */
public record BatchedMessage(
        @NonNull List<Message> messages, @NonNull List<Long> ackIds, long senderInstanceId) {
    private static final String TAG = "BatchedMessage";

    /** Serializes this {@link BatchedMessage} into a byte array. */
    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ProtoOutputStream proto = new ProtoOutputStream(baos);
        for (Message m : messages) {
            m.writeTo(proto, BatchedMessageProto.MESSAGES);
        }
        for (long ackId : ackIds) {
            proto.write(BatchedMessageProto.ACK_IDS, ackId);
        }
        proto.write(BatchedMessageProto.SENDER_INSTANCE_ID, senderInstanceId);
        proto.flush();
        return baos.toByteArray();
    }

    /** Deserializes a {@link BatchedMessage} record from a byte array. */
    public static BatchedMessage parseFrom(byte[] data) throws IOException {
        ProtoInputStream proto = new ProtoInputStream(data);
        List<Message> messages = new ArrayList<>();
        List<Long> ackIds = new ArrayList<>();
        long senderInstanceId = 0;

        try {
            while (proto.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
                switch (proto.getFieldNumber()) {
                    case (int) BatchedMessageProto.MESSAGES -> {
                        long token = proto.start(BatchedMessageProto.MESSAGES);
                        messages.add(Message.parseFrom(proto));
                        proto.end(token);
                    }
                    case (int) BatchedMessageProto.ACK_IDS ->
                            ackIds.add(proto.readLong(BatchedMessageProto.ACK_IDS));
                    case (int) BatchedMessageProto.SENDER_INSTANCE_ID ->
                            senderInstanceId =
                                    proto.readLong(BatchedMessageProto.SENDER_INSTANCE_ID);
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse BatchedMessage from proto", e);
        }
        return new BatchedMessage(messages, ackIds, senderInstanceId);
    }
}
