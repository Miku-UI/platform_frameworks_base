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

import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;

import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class RemoteTaskUpdatedMessageTest {

    @Test
    public void testConstructor_fromObjects() {
        RemoteTaskInfo expected = new RemoteTaskInfo(1, "label", 0, new byte[0], true);

        RemoteTaskUpdatedMessage actual = new RemoteTaskUpdatedMessage(expected);

        assertThat(actual.task()).isEqualTo(expected);
    }

    @Test
    public void testReadFromProto_missingTask_throwsException() throws IOException {
        final ProtoInputStream pis = new ProtoInputStream(new byte[0]);
        expectThrows(IOException.class, () -> RemoteTaskUpdatedMessage.readFromProto(pis));
    }

    @Test
    public void testWriteAndReadFromProto_roundTrip_works() throws IOException {
        final RemoteTaskUpdatedMessage expected
            = new RemoteTaskUpdatedMessage(new RemoteTaskInfo(1, "label", 0, new byte[0], true));

        final ProtoOutputStream pos = new ProtoOutputStream();
        expected.writeToProto(pos);
        pos.flush();

        final ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        final RemoteTaskUpdatedMessage actual = RemoteTaskUpdatedMessage.readFromProto(pis);

        assertThat(expected.task()).isEqualTo(actual.task());
    }

    @Test
    public void testGetFieldNumber_returnsCorrectValue() {
        RemoteTaskUpdatedMessage remoteTaskUpdatedMessage
            = new RemoteTaskUpdatedMessage(new RemoteTaskInfo(1, "label", 0, new byte[0], true));
        assertThat(remoteTaskUpdatedMessage.getFieldNumber())
            .isEqualTo(android.companion.TaskContinuityMessage.REMOTE_TASK_UPDATED);
    }
}