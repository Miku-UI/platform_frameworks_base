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


import static com.google.common.truth.Truth.assertThat;
import static org.testng.Assert.expectThrows;

import android.companion.TaskContinuityMessage;

import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.util.proto.ProtoOutputStream;
import android.util.proto.ProtoInputStream;

import org.junit.runner.RunWith;
import org.junit.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class ContinuityDeviceConnectedTest {

    @Test
    public void testConstructor_fromObjects() {
        final List<RemoteTaskInfo> remoteTasks = new ArrayList<>();

        final ContinuityDeviceConnected continuityDeviceConnected
            = new ContinuityDeviceConnected(remoteTasks);

        assertThat(continuityDeviceConnected.remoteTasks()).isEqualTo(remoteTasks);
    }

    @Test
    public void testWriteAndReadToProto_roundTrip_works() throws IOException {
        int expectedTaskId = 1;
        String expectedLabel = "test";
        long expectedLastActiveTime = 0;
        ContinuityDeviceConnected expected = new ContinuityDeviceConnected(
            Arrays.asList(new RemoteTaskInfo(1, "task", 50, new byte[0], true)));

        final ProtoOutputStream pos = new ProtoOutputStream();
        expected.writeToProto(pos);
        pos.flush();

        final ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        final ContinuityDeviceConnected actual = ContinuityDeviceConnected.readFromProto(pis);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void testGetFieldNumber_returnsCorrectValue() {
        ContinuityDeviceConnected continuityDeviceConnected
            = new ContinuityDeviceConnected(new ArrayList<>());

        assertThat(continuityDeviceConnected.getFieldNumber())
            .isEqualTo(android.companion.TaskContinuityMessage.DEVICE_CONNECTED);
    }
}