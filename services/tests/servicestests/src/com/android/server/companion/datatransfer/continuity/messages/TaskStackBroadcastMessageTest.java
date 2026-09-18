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

import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class TaskStackBroadcastMessageTest extends ProtoTest<TaskStackBroadcastMessage> {

    @Override
    protected Proto.Builder<TaskStackBroadcastMessage> newBuilder() {
        return new TaskStackBroadcastMessage.Builder();
    }

    @Test
    public void testRoundTrip_notEmpty_works() throws Exception {
        verifyRoundTrip(
                new TaskStackBroadcastMessage(
                        Arrays.asList(
                                new RemoteTaskInfo(
                                        1,
                                        "package_name",
                                        true,
                                        50,
                                        new HandoffOptions(true, true)))));
    }
}
