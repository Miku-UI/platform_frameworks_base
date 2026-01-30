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

import android.companion.datatransfer.continuity.RemoteTask;
import android.graphics.drawable.Icon;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;

import java.util.Arrays;
import java.util.Objects;
import java.io.IOException;

public record RemoteTaskInfo(
    int id,
    String label,
    long lastUsedTimeMillis,
    byte[] taskIcon,
    boolean isHandoffEnabled) {

    public static RemoteTaskInfo fromProto(ProtoInputStream protoInputStream)
        throws IOException {

        int id = 0;
        String label = "";
        long lastUsedTimeMillis = 0;
        byte[] taskIcon = new byte[0];
        boolean isHandoffEnabled = false;
        while (protoInputStream.nextField() != ProtoInputStream.NO_MORE_FIELDS) {
            switch (protoInputStream.getFieldNumber()) {
              case (int) android.companion.RemoteTaskInfo.ID:
                    id = protoInputStream.readInt(
                        android.companion.RemoteTaskInfo.ID);

                    break;
                case (int) android.companion.RemoteTaskInfo.LABEL:
                    label = protoInputStream.readString(
                        android.companion.RemoteTaskInfo.LABEL);

                    break;
                case (int) android.companion.RemoteTaskInfo.LAST_USED_TIME_MILLIS:
                    lastUsedTimeMillis = protoInputStream.readLong(
                        android.companion.RemoteTaskInfo.LAST_USED_TIME_MILLIS);
                    break;
                case (int) android.companion.RemoteTaskInfo.TASK_ICON:
                    taskIcon = protoInputStream
                        .readBytes(android.companion.RemoteTaskInfo.TASK_ICON);
                    break;
                case (int) android.companion.RemoteTaskInfo.IS_HANDOFF_ENABLED:
                    isHandoffEnabled = protoInputStream.readBoolean(
                        android.companion.RemoteTaskInfo.IS_HANDOFF_ENABLED);
                    break;
            }
       }

        return new RemoteTaskInfo(id, label, lastUsedTimeMillis, taskIcon, isHandoffEnabled);
   }

    public void writeToProto(ProtoOutputStream protoOutputStream) {
        protoOutputStream
            .writeInt32(android.companion.RemoteTaskInfo.ID, id());
        protoOutputStream
            .writeString(android.companion.RemoteTaskInfo.LABEL, label());
        protoOutputStream
            .writeInt64(
                android.companion.RemoteTaskInfo.LAST_USED_TIME_MILLIS,
                lastUsedTimeMillis());
        protoOutputStream
            .writeBytes(android.companion.RemoteTaskInfo.TASK_ICON, taskIcon());
        protoOutputStream
            .writeBool(
                android.companion.RemoteTaskInfo.IS_HANDOFF_ENABLED, isHandoffEnabled());
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof RemoteTaskInfo) {
            RemoteTaskInfo other = (RemoteTaskInfo) o;
            return id() == other.id()
                && label().equals(other.label())
                && lastUsedTimeMillis() == other.lastUsedTimeMillis()
                && Arrays.equals(taskIcon(), other.taskIcon());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            id(),
            label(),
            lastUsedTimeMillis(),
            Arrays.hashCode(taskIcon()),
            isHandoffEnabled());
    }

    public RemoteTask toRemoteTask(int deviceId, String deviceName) {
        Icon taskIcon = null;
        if (taskIcon() != null && taskIcon().length > 0) {
            taskIcon = Icon.createWithData(taskIcon(), 0, taskIcon().length);
        }

        return new RemoteTask.Builder(id())
                .setLabel(label())
                .setDeviceId(deviceId)
                .setLastUsedTimestampMillis(lastUsedTimeMillis())
                .setSourceDeviceName(deviceName)
                .setIcon(taskIcon)
                .setHandoffEnabled(isHandoffEnabled())
                .build();
    }
}