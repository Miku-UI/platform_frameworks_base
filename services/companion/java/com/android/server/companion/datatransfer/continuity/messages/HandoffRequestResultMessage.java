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

/**
 * Deserialized version of the HandoffRequestResultMessage proto. Contains a status code and a list
 * of activities for handoff.
 */
public record HandoffRequestResultMessage(
        int taskId, int statusCode, @NonNull List<HandoffActivityDataMessage> activities)
        implements Proto {

    public HandoffRequestResultMessage {
        Objects.requireNonNull(activities);
    }

    public static class Builder extends Proto.Builder<HandoffRequestResultMessage> {
        private int taskId = 0;
        private int statusCode = 0;
        private List<HandoffActivityDataMessage> activities = new ArrayList<>();

        @NonNull
        public Builder setTaskId(int taskId) {
            this.taskId = taskId;
            return this;
        }

        @NonNull
        public Builder setStatusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        @NonNull
        public Builder addActivity(@NonNull HandoffActivityDataMessage activity) {
            this.activities.add(Objects.requireNonNull(activity));
            return this;
        }

        @Override
        protected void processField(@NonNull ProtoInputStream pis, int fieldNumber)
                throws IOException {
            switch (fieldNumber) {
                case (int) android.companion.HandoffRequestResultMessage.STATUS_CODE ->
                        setStatusCode(
                                pis.readInt(
                                        android.companion.HandoffRequestResultMessage.STATUS_CODE));
                case (int) android.companion.HandoffRequestResultMessage.TASK_ID ->
                        setTaskId(
                                pis.readInt(android.companion.HandoffRequestResultMessage.TASK_ID));
                case (int) android.companion.HandoffRequestResultMessage.ACTIVITIES ->
                        addActivity(
                                new HandoffActivityDataMessage.Builder()
                                        .readFromField(
                                                pis,
                                                android.companion.HandoffRequestResultMessage
                                                        .ACTIVITIES)
                                        .build());
            }
        }

        @Override
        public HandoffRequestResultMessage build() {
            return new HandoffRequestResultMessage(taskId, statusCode, activities);
        }
    }

    @Override
    public void write(@NonNull ProtoOutputStream pos) throws IOException {
        Objects.requireNonNull(pos);

        pos.write(android.companion.HandoffRequestResultMessage.STATUS_CODE, statusCode);
        pos.write(android.companion.HandoffRequestResultMessage.TASK_ID, taskId);

        for (HandoffActivityDataMessage activity : activities) {
            Proto.writeField(
                    pos, android.companion.HandoffRequestResultMessage.ACTIVITIES, activity);
        }
    }
}
