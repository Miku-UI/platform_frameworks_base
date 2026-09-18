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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deserialized version of the {@link TaskStackBroadcastMessage} proto. */
public record TaskStackBroadcastMessage(@NonNull List<RemoteTaskInfo> remoteTasks)
        implements Proto {

    public TaskStackBroadcastMessage {
        Objects.requireNonNull(remoteTasks);
    }

    public static class Builder extends Proto.Builder<TaskStackBroadcastMessage> {
        private List<RemoteTaskInfo> remoteTasks = new ArrayList<>();

        @NonNull
        public Builder addRemoteTask(@NonNull RemoteTaskInfo remoteTask) {
            this.remoteTasks.add(Objects.requireNonNull(remoteTask));
            return this;
        }

        @NonNull
        public Builder clearRemoteTasks() {
            this.remoteTasks.clear();
            return this;
        }

        @Override
        protected void processField(@NonNull ProtoInputStream pis, int fieldNumber)
                throws IOException {
            switch (fieldNumber) {
                case (int) android.companion.TaskStackBroadcastMessage.IS_STACK_EMPTY ->
                        clearRemoteTasks();
                case (int) android.companion.TaskStackBroadcastMessage.REMOTE_TASKS ->
                        addRemoteTask(
                                new RemoteTaskInfo.Builder()
                                        .readFromField(
                                                pis,
                                                android.companion.TaskStackBroadcastMessage
                                                        .REMOTE_TASKS)
                                        .build());
            }
        }

        @Override
        public TaskStackBroadcastMessage build() {
            return new TaskStackBroadcastMessage(remoteTasks);
        }
    }

    /** Writes this object to a proto output stream. */
    @Override
    public void write(@NonNull ProtoOutputStream pos) throws IOException {
        if (remoteTasks().isEmpty()) {
            Objects.requireNonNull(pos)
                    .writeBool(android.companion.TaskStackBroadcastMessage.IS_STACK_EMPTY, true);
        } else {
            for (RemoteTaskInfo remoteTaskInfo : remoteTasks()) {
                Proto.writeField(
                        pos,
                        android.companion.TaskStackBroadcastMessage.REMOTE_TASKS,
                        remoteTaskInfo);
            }
        }
    }
}
