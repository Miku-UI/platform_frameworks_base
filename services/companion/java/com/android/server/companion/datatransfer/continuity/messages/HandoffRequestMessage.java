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

import android.annotation.NonNull;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;
import java.io.IOException;
import java.util.Objects;

/** Deserialized version of the HandoffRequestMessage proto. */
public record HandoffRequestMessage(int taskId) implements Proto {

    public static class Builder extends Proto.Builder<HandoffRequestMessage> {
        private int taskId = 0;

        @NonNull
        public Builder setTaskId(int taskId) {
            this.taskId = taskId;
            return this;
        }

        @Override
        protected void processField(@NonNull ProtoInputStream pis, int fieldNumber)
                throws IOException {
            switch (fieldNumber) {
                case (int) android.companion.HandoffRequestMessage.TASK_ID ->
                        setTaskId(pis.readInt(android.companion.HandoffRequestMessage.TASK_ID));
            }
        }

        @Override
        public HandoffRequestMessage build() {
            return new HandoffRequestMessage(taskId);
        }
    }

    @Override
    public void write(@NonNull ProtoOutputStream pos) throws IOException {
        Objects.requireNonNull(pos)
                .write(android.companion.HandoffRequestMessage.TASK_ID, taskId());
    }
}
