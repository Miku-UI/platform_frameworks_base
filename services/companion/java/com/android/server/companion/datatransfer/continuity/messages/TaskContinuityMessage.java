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

package com.android.server.companion.datatransfer.continuity.messages;

import android.util.proto.ProtoOutputStream;

import java.io.IOException;

/**
 * Represents a possible type for the "data" field on the
 * {@link TaskContinuityMessage} proto. This interface may be implemented by
 * message subclasses to support serialization and deserialization as part of
 * {@link TaskContinuityMessage}.
 */
public interface TaskContinuityMessage {

    /**
     * Returns the proto field number for this message type.
     */
    long getFieldNumber();

    /**
     * Writes this object to a proto output stream.
     */
    void writeToProto(ProtoOutputStream pos) throws IOException;

}