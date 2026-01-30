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

import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;

import java.io.IOException;

public record RemoteTaskRemovedMessage(int taskId) implements TaskContinuityMessage {

    public static RemoteTaskRemovedMessage readFromProto(ProtoInputStream pis) throws IOException {
        int taskId = 0;
        while (pis.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
            switch (pis.getFieldNumber()) {
                case (int) android.companion.RemoteTaskRemovedMessage.TASK_ID:
                    taskId = pis.readInt(
                        android.companion.RemoteTaskRemovedMessage.TASK_ID
                    );
                    break;
            }
        }

        return new RemoteTaskRemovedMessage(taskId);
    }

    @Override
    public long getFieldNumber() {
        return android.companion.TaskContinuityMessage.REMOTE_TASK_REMOVED;
    }

    @Override
    public void writeToProto(ProtoOutputStream pos) throws IOException {
        pos.write(
            android.companion.RemoteTaskRemovedMessage.TASK_ID,
            taskId());
    }
}