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
import android.annotation.Nullable;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;
import com.android.internal.util.FrameworkStatsLog;
import java.io.IOException;

/** Represents the "TaskContinuityMessage" proto */
public record TaskContinuityMessage(
        @Nullable TaskStackBroadcastMessage taskStackBroadcastMessage,
        @Nullable HandoffRequestMessage handoffRequestMessage,
        @Nullable HandoffRequestResultMessage handoffRequestResultMessage)
        implements Proto {

    @Override
    public void write(@NonNull ProtoOutputStream pos) throws IOException {
        Proto.writeField(
                pos,
                android.companion.TaskContinuityMessage.TASK_STACK_BROADCAST,
                taskStackBroadcastMessage());
        Proto.writeField(
                pos,
                android.companion.TaskContinuityMessage.HANDOFF_REQUEST,
                handoffRequestMessage());
        Proto.writeField(
                pos,
                android.companion.TaskContinuityMessage.HANDOFF_REQUEST_RESULT,
                handoffRequestResultMessage());
    }

    public int getTypeForMetrics() {
        if (taskStackBroadcastMessage() != null) {
            return FrameworkStatsLog
                    .TASK_CONTINUITY_MESSAGE_SENT__MESSAGE_TYPE__TASK_CONTINUITY_MESSAGE_TYPE_TASK_STACK_BROADCAST;
        } else if (handoffRequestMessage() != null) {
            return FrameworkStatsLog
                    .TASK_CONTINUITY_MESSAGE_SENT__MESSAGE_TYPE__TASK_CONTINUITY_MESSAGE_TYPE_HANDOFF_REQUEST;
        } else if (handoffRequestResultMessage() != null) {
            return FrameworkStatsLog
                    .TASK_CONTINUITY_MESSAGE_SENT__MESSAGE_TYPE__TASK_CONTINUITY_MESSAGE_TYPE_HANDOFF_REQUEST_RESULT;
        } else {
            return FrameworkStatsLog
                    .TASK_CONTINUITY_MESSAGE_SENT__MESSAGE_TYPE__TASK_CONTINUITY_MESSAGE_TYPE_UNKNOWN;
        }
    }

    public static class Builder extends Proto.Builder<TaskContinuityMessage> {
        private HandoffRequestMessage mHandoffRequestMessage;
        private HandoffRequestResultMessage mHandoffRequestResultMessage;
        private TaskStackBroadcastMessage mTaskStackBroadcastMessage;

        public Builder setHandoffRequestMessage(
                @NonNull HandoffRequestMessage handoffRequestMessage) {
            mHandoffRequestMessage = handoffRequestMessage;
            mHandoffRequestResultMessage = null;
            mTaskStackBroadcastMessage = null;
            return this;
        }

        public Builder setHandoffRequestResultMessage(
                @NonNull HandoffRequestResultMessage handoffRequestResultMessage) {
            mHandoffRequestMessage = null;
            mHandoffRequestResultMessage = handoffRequestResultMessage;
            mTaskStackBroadcastMessage = null;
            return this;
        }

        public Builder setTaskStackBroadcastMessage(
                @NonNull TaskStackBroadcastMessage taskStackBroadcastMessage) {
            mHandoffRequestMessage = null;
            mHandoffRequestResultMessage = null;
            mTaskStackBroadcastMessage = taskStackBroadcastMessage;
            return this;
        }

        @Override
        protected void processField(@NonNull ProtoInputStream pis, int fieldNumber)
                throws IOException {
            switch (fieldNumber) {
                case (int) android.companion.TaskContinuityMessage.TASK_STACK_BROADCAST ->
                        setTaskStackBroadcastMessage(
                                new TaskStackBroadcastMessage.Builder()
                                        .readFromField(
                                                pis,
                                                android.companion.TaskContinuityMessage
                                                        .TASK_STACK_BROADCAST)
                                        .build());
                case (int) android.companion.TaskContinuityMessage.HANDOFF_REQUEST ->
                        setHandoffRequestMessage(
                                new HandoffRequestMessage.Builder()
                                        .readFromField(
                                                pis,
                                                android.companion.TaskContinuityMessage
                                                        .HANDOFF_REQUEST)
                                        .build());
                case (int) android.companion.TaskContinuityMessage.HANDOFF_REQUEST_RESULT ->
                        setHandoffRequestResultMessage(
                                new HandoffRequestResultMessage.Builder()
                                        .readFromField(
                                                pis,
                                                android.companion.TaskContinuityMessage
                                                        .HANDOFF_REQUEST_RESULT)
                                        .build());
            }
        }

        @Override
        public TaskContinuityMessage build() {
            return new TaskContinuityMessage(
                    mTaskStackBroadcastMessage,
                    mHandoffRequestMessage,
                    mHandoffRequestResultMessage);
        }
    }
}
