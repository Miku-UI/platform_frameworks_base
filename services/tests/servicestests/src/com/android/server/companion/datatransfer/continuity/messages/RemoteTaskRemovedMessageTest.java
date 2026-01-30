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
import android.util.proto.ProtoParseException;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class RemoteTaskRemovedMessageTest {

    @Test
    public void testConstructor_fromTaskId() {
        final int taskId = 1234;
        final RemoteTaskRemovedMessage remoteTaskRemovedMessage
            = new RemoteTaskRemovedMessage(taskId);
        assertThat(remoteTaskRemovedMessage.taskId())
            .isEqualTo(taskId);
    }

    @Test
    public void readFromProto_readsValidProto()
        throws IOException, ProtoParseException {

        final int taskId = 1234;
        final ProtoOutputStream pos = new ProtoOutputStream();
        pos.writeInt32(
            android.companion.RemoteTaskRemovedMessage.TASK_ID,
            taskId);
        pos.flush();

        final ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        final RemoteTaskRemovedMessage remoteTaskRemovedMessage
            = RemoteTaskRemovedMessage.readFromProto(pis);

        assertThat(remoteTaskRemovedMessage.taskId())
            .isEqualTo(taskId);
    }

    @Test
    public void testConstructor_fromProto_setsToDefaultTaskId()
        throws IOException, ProtoParseException {

        RemoteTaskRemovedMessage remoteTaskRemovedMessage
            = RemoteTaskRemovedMessage.readFromProto(new ProtoInputStream(new byte[0]));

        assertThat(remoteTaskRemovedMessage.taskId())
            .isEqualTo(0);
    }

    @Test
    public void testWriteToProto_writesValidProto() throws IOException {
        int taskId = 1234;
        RemoteTaskRemovedMessage remoteTaskRemovedMessage
            = new RemoteTaskRemovedMessage(taskId);
        final ProtoOutputStream pos = new ProtoOutputStream();
        remoteTaskRemovedMessage.writeToProto(pos);
        pos.flush();

        final ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        pis.nextField();
        long taskIdFieldNumber =
            android.companion.RemoteTaskRemovedMessage.TASK_ID;
        assertThat(pis.getFieldNumber())
            .isEqualTo((int) taskIdFieldNumber);
        assertThat(pis.readInt(taskIdFieldNumber))
            .isEqualTo(taskId);
        pis.nextField();
        assertThat(pis.nextField()).isEqualTo(ProtoInputStream.NO_MORE_FIELDS);
    }

    @Test
    public void testWriteAndRead_roundTrip_works() throws IOException {
        int taskId = 1234;
        RemoteTaskRemovedMessage expected
            = new RemoteTaskRemovedMessage(taskId);
        final ProtoOutputStream pos = new ProtoOutputStream();
        expected.writeToProto(pos);
        pos.flush();

        final ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        final RemoteTaskRemovedMessage actual
            = RemoteTaskRemovedMessage.readFromProto(pis);

        assertThat(actual.taskId())
            .isEqualTo(expected.taskId());
    }

    @Test
    public void testEquals_works() {
        int taskId = 1234;
        RemoteTaskRemovedMessage expected
            = new RemoteTaskRemovedMessage(taskId);
        RemoteTaskRemovedMessage actual
            = new RemoteTaskRemovedMessage(taskId);
        assertThat(actual).isEqualTo(expected);
    }
}