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

package com.android.server.wm;

import static android.internal.perfetto.protos.Windowmanagerservice.IdentifierProto.HASH_CODE;
import static android.internal.perfetto.protos.Windowmanagerservice.IdentifierProto.TITLE;
import static android.internal.perfetto.protos.Windowmanagerservice.IdentifierProto.USER_ID;
import static android.os.UserHandle.USER_NULL;

import android.util.proto.ProtoOutputStream;

/**
 * An interface for objects that can be identified in Perfetto traces.
 * <p>
 * This is used to provide an unique identifier for objects that are dumped to
 * the window manager trace. This allows tools like Perfetto to correlate objects across
 * different states. For example, a window can be identified by its hash code and title,
 * and this allows tracking the same window object throughout its lifecycle in the trace.
 * </p>
 */
public interface Identifiable {
    /**
     * Writes a unique identifier of this object to a protocol buffer.
     * Protocol buffer message definition at {@link IdentifierProto}
     *
     * @param proto ProtoOutputStream to write the identifier to.
     * @param fieldId Field Id of the identifier as defined in the parent message.
     */
    default void writeIdentifierToProto(ProtoOutputStream proto, long fieldId) {
        final long token = proto.start(fieldId);
        proto.write(HASH_CODE, System.identityHashCode(this));
        proto.write(USER_ID, USER_NULL);
        proto.write(TITLE, toString());
        proto.end(token);
    }
}
