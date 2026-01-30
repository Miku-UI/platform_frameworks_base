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

import android.app.HandoffActivityData;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Deserialized version of the HandoffRequestResultMessage proto.
 * Contains a status code and a list of activities for handoff.
 */
public record HandoffRequestResultMessage(
    int taskId,
    int statusCode,
    List<HandoffActivityData> activities) implements TaskContinuityMessage {

    public static HandoffRequestResultMessage readFromProto(
        ProtoInputStream pis) throws IOException {

        int statusCode = 0;
        int taskId = 0;
        List<HandoffActivityData> activities = new ArrayList<>();

        while (pis.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
            switch (pis.getFieldNumber()) {
                case (int) android.companion.HandoffRequestResultMessage.STATUS_CODE:
                    statusCode = pis.readInt(
                        android.companion.HandoffRequestResultMessage.STATUS_CODE);

                    break;
                case (int) android.companion.HandoffRequestResultMessage.TASK_ID:
                    taskId = pis.readInt(android.companion.HandoffRequestResultMessage.TASK_ID);
                    break;
                case (int) android.companion.HandoffRequestResultMessage.ACTIVITIES:
                    long token = pis.start(
                            android.companion.HandoffRequestResultMessage.ACTIVITIES);
                    HandoffActivityData activityData =
                            HandoffActivityDataSerializer.readFromProto(pis);
                    activities.add(activityData);
                    pis.end(token);
                    break;
            }
        }

        return new HandoffRequestResultMessage(taskId, statusCode, activities);
    }

    @Override
    public long getFieldNumber() {
        return android.companion.TaskContinuityMessage.HANDOFF_REQUEST_RESULT;
    }

    @Override
    public void writeToProto(ProtoOutputStream pos) throws IOException {
        pos.write(android.companion.HandoffRequestResultMessage.STATUS_CODE, statusCode);
        pos.write(android.companion.HandoffRequestResultMessage.TASK_ID, taskId);

        for (android.app.HandoffActivityData activity : activities) {
            long token = pos.start(android.companion.HandoffRequestResultMessage.ACTIVITIES);
            HandoffActivityDataSerializer.writeToProto(activity, pos);
            pos.end(token);
        }
    }
}