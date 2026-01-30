/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.companion.datatransfer.continuity.RemoteTask;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.Test;

import android.util.proto.ProtoOutputStream;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoParseException;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class RemoteTaskInfoTest {

    @Test
    public void testRemoteTaskInfo_fromProtoStream_setsToDefaultValues()
        throws Exception {

        ProtoInputStream pis = new ProtoInputStream(new byte[0]);
        RemoteTaskInfo remoteTaskInfo = RemoteTaskInfo.fromProto(pis);
        assertThat(remoteTaskInfo.id()).isEqualTo(0);
        assertThat(remoteTaskInfo.label()).isEmpty();
        assertThat(remoteTaskInfo.lastUsedTimeMillis()).isEqualTo(0);
        assertThat(remoteTaskInfo.taskIcon()).isEmpty();
        assertThat(remoteTaskInfo.isHandoffEnabled()).isFalse();
    }

    @Test
    public void testRemoteTaskInfo_fromProtoStream_works() throws Exception {
        int expectedId = 1;
        String expectedLabel = "test";
        long expectedLastActiveTime = 1;
        byte[] expectedTaskIcon = new byte[] {1, 2, 3};
        boolean expectedIsHandoffEnabled = true;

        // Setup the proto stream
        ProtoOutputStream pos = new ProtoOutputStream();
        pos.writeInt32(android.companion.RemoteTaskInfo.ID, expectedId);
        pos.writeString(android.companion.RemoteTaskInfo.LABEL, expectedLabel);
        pos.writeInt64(
            android.companion.RemoteTaskInfo.LAST_USED_TIME_MILLIS,
            expectedLastActiveTime);
        pos.writeBytes(android.companion.RemoteTaskInfo.TASK_ICON,
            expectedTaskIcon);
        pos.writeBool(android.companion.RemoteTaskInfo.IS_HANDOFF_ENABLED,
            expectedIsHandoffEnabled);

        pos.flush();

        // Create the RemoteTaskInfo from the proto stream
        ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        RemoteTaskInfo remoteTaskInfo = RemoteTaskInfo.fromProto(pis);

        // Verify the fields
        assertThat(remoteTaskInfo.id()).isEqualTo(expectedId);
        assertThat(remoteTaskInfo.label()).isEqualTo(expectedLabel);
        assertThat(remoteTaskInfo.lastUsedTimeMillis())
            .isEqualTo(expectedLastActiveTime);
        assertThat(remoteTaskInfo.taskIcon()).isEqualTo(expectedTaskIcon);
        assertThat(remoteTaskInfo.isHandoffEnabled()).isEqualTo(expectedIsHandoffEnabled);
    }

    @Test
    public void testWriteAndRead_roundTrip_works() throws Exception {
        int expectedId = 1;
        String expectedLabel = "test";
        long expectedLastActiveTime = 1;
        boolean expectedIsHandoffEnabled = true;
        RemoteTaskInfo remoteTaskInfo = new RemoteTaskInfo(
            expectedId,
            expectedLabel,
            expectedLastActiveTime,
            new byte[0],
            expectedIsHandoffEnabled);

        ProtoOutputStream pos = new ProtoOutputStream();
        remoteTaskInfo.writeToProto(pos);
        pos.flush();

        ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        RemoteTaskInfo result = RemoteTaskInfo.fromProto(pis);

        assertThat(result.id()).isEqualTo(expectedId);
        assertThat(result.label()).isEqualTo(expectedLabel);
        assertThat(result.lastUsedTimeMillis())
            .isEqualTo(expectedLastActiveTime);
        assertThat(result.isHandoffEnabled()).isEqualTo(expectedIsHandoffEnabled);
    }

    @Test
    public void testToRemoteTask_works() {
        // Setup the RemoteTaskInfo
        int expectedId = 1;
        String expectedLabel = "test";
        long expectedLastActiveTime = 100;
        String expectedDeviceName = "test_device";
        int expectedDeviceId = 2;
        boolean expectedIsHandoffEnabled = true;
        RemoteTaskInfo remoteTaskInfo = new RemoteTaskInfo(
            expectedId,
            expectedLabel,
            expectedLastActiveTime,
            new byte[0],
            expectedIsHandoffEnabled);

        // Convert to RemoteTask
        RemoteTask remoteTask = remoteTaskInfo.toRemoteTask(
            expectedDeviceId,
            expectedDeviceName);

        // Verify the fields
        assertThat(remoteTask.getId()).isEqualTo(expectedId);
        assertThat(remoteTask.getLabel()).isEqualTo(expectedLabel);
        assertThat(remoteTask.getLastUsedTimestampMillis())
            .isEqualTo(expectedLastActiveTime);
        assertThat(remoteTask.getDeviceId()).isEqualTo(expectedDeviceId);
        assertThat(remoteTask.getSourceDeviceName()).isEqualTo(expectedDeviceName);
        assertThat(remoteTask.isHandoffEnabled()).isEqualTo(expectedIsHandoffEnabled);
    }
}