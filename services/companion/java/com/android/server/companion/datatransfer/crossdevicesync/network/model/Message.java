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
import java.util.Arrays;
import java.util.Objects;

/** A message to send or receive from CompanionDeviceManager. */
@SuppressWarnings("ArrayRecordComponent")
public record Message(long handleId, @NonNull String networkId, @NonNull byte[] payload) {
    private static final String TAG = "Message";

    /** Serializes this {@link Message} into a byte array. */
    public byte[] toByteArray() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ProtoOutputStream proto = new ProtoOutputStream(baos);
        writeTo(proto);
        proto.flush();
        return baos.toByteArray();
    }

    /** Deserializes a {@link Message} record from a byte array. */
    public static Message parseFrom(byte[] data) throws IOException {
        return parseFrom(new ProtoInputStream(data));
    }

    /** Deserializes a {@link Message} record from a {@link ProtoInputStream}. */
    public static Message parseFrom(ProtoInputStream proto) throws IOException {
        long handleId = 0;
        String networkId = "";
        byte[] payload = new byte[0];

        try {
            while (proto.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
                switch (proto.getFieldNumber()) {
                    case (int) MessageProto.HANDLE_ID ->
                            handleId = proto.readLong(MessageProto.HANDLE_ID);
                    case (int) MessageProto.NETWORK_ID ->
                            networkId = proto.readString(MessageProto.NETWORK_ID);
                    case (int) MessageProto.PAYLOAD ->
                            payload = proto.readBytes(MessageProto.PAYLOAD);
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse Message from proto", e);
        }
        return new Message(handleId, networkId, payload);
    }

    /** Writes this {@link Message} to a {@link ProtoOutputStream}. */
    public void writeTo(ProtoOutputStream proto) {
        proto.write(MessageProto.HANDLE_ID, handleId);
        proto.write(MessageProto.NETWORK_ID, networkId);
        proto.write(MessageProto.PAYLOAD, payload);
    }

    /** Writes this {@link Message} to a {@link ProtoOutputStream} with a field ID. */
    public void writeTo(ProtoOutputStream proto, long fieldId) {
        long token = proto.start(fieldId);
        writeTo(proto);
        proto.end(token);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message message)) return false;
        return handleId == message.handleId
                && Objects.equals(networkId, message.networkId)
                && Arrays.equals(payload, message.payload);
    }
}
