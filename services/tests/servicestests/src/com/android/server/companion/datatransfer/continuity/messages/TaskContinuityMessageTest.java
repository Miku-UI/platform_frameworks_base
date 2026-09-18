/*
 * Copyright (C) 2026 The Android Open Source Project
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
import android.content.ComponentName;
import android.net.Uri;
import android.os.PersistableBundle;
import java.util.List;
import org.junit.Test;

public class TaskContinuityMessageTest extends ProtoTest<TaskContinuityMessage> {
    @Override
    protected TaskContinuityMessage.Builder newBuilder() {
        return new TaskContinuityMessage.Builder();
    }

    @Test
    public void testRoundTrip_taskStackBroadcastMessage_works() throws Exception {
        verifyRoundTrip(
                new TaskContinuityMessage.Builder()
                        .setTaskStackBroadcastMessage(
                                new TaskStackBroadcastMessage.Builder()
                                        .addRemoteTask(
                                                new RemoteTaskInfo(
                                                        1,
                                                        "package_name",
                                                        true,
                                                        50,
                                                        new HandoffOptions(true, true)))
                                        .build())
                        .build());
    }

    @Test
    public void testRoundTrip_handoffRequestMessage_works() throws Exception {
        verifyRoundTrip(
                new TaskContinuityMessage.Builder()
                        .setHandoffRequestMessage(
                                new HandoffRequestMessage.Builder().setTaskId(1).build())
                        .build());
    }

    @Test
    public void testRoundTrip_handoffRequestResultMessage_works() throws Exception {
        PersistableBundle extras = new PersistableBundle();
        extras.putString("key", "value");
        verifyRoundTrip(
                new TaskContinuityMessage.Builder()
                        .setHandoffRequestResultMessage(
                                new HandoffRequestResultMessage.Builder()
                                        .setTaskId(1)
                                        .setStatusCode(2)
                                        .addActivity(
                                                new HandoffActivityDataMessage(
                                                        new HandoffActivityData.Builder(
                                                                        new ComponentName(
                                                                                "com.example.app",
                                                                                "com.example.app.Activity"))
                                                                .setFallbackUri(
                                                                        Uri.parse(
                                                                                "http://example.com/fallback"))
                                                                .setExtras(extras)
                                                                .build(),
                                                        List.of(new byte[] {1, 2, 3, 4})))
                                        .build())
                        .build());
    }
}
