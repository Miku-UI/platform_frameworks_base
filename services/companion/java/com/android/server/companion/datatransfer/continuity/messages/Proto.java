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

package com.android.server.companion.datatransfer.continuity.messages;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

/** Represents a proto message that can be serialized to a {@link ProtoOutputStream}. */
public interface Proto {

    /**
     * Writes the proto message to the given {@link ProtoOutputStream}.
     *
     * @param pos the {@link ProtoOutputStream} to write to
     * @throws IOException if an error occurs while writing
     */
    void write(@NonNull ProtoOutputStream pos) throws IOException;

    /**
     * Writes the proto message to the given {@link ProtoOutputStream} with the given field number.
     *
     * @param pos the {@link ProtoOutputStream} to write to
     * @param fieldNumber the field number of the proto message
     * @param proto the proto message to write
     * @throws IOException if an error occurs while writing
     */
    public static void writeField(
            @NonNull ProtoOutputStream pos, long fieldNumber, @Nullable Proto proto)
            throws IOException {
        if (proto == null) {
            return;
        }

        long token = Objects.requireNonNull(pos).start(fieldNumber);
        Objects.requireNonNull(proto).write(pos);
        pos.end(token);
    }

    public static byte[] toBytes(@NonNull Proto proto) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ProtoOutputStream pos = new ProtoOutputStream(baos);
        proto.write(pos);
        pos.flush();
        return baos.toByteArray();
    }

    public abstract class Builder<T extends Proto> {

        public Builder<T> readFromBytes(@NonNull byte[] bytes) throws IOException {
            ProtoInputStream pis = new ProtoInputStream(bytes);
            readFromStream(pis);
            return this;
        }

        public Builder<T> readFromField(@NonNull ProtoInputStream pis, long fieldNumber)
                throws IOException {
            Objects.requireNonNull(pis);
            long token = pis.start(fieldNumber);
            readFromStream(pis);
            pis.end(token);
            return this;
        }

        public Builder<T> readFromStream(@NonNull ProtoInputStream pis) throws IOException {
            Objects.requireNonNull(pis);
            while (pis.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
                processField(pis, pis.getFieldNumber());
            }

            return this;
        }

        /**
         * Processes the current field in the {@link ProtoInputStream}. The field number is provided
         * for convenience.
         *
         * @param pis the {@link ProtoInputStream} to read from
         * @param fieldNumber the field number of the proto message
         * @throws IOException if an error occurs while reading
         */
        protected abstract void processField(@NonNull ProtoInputStream pis, int fieldNumber)
                throws IOException;

        public abstract T build();
    }
}
