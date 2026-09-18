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

package com.android.server.companion.datatransfer.continuity;

import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestMessage;
import com.android.server.companion.datatransfer.continuity.messages.HandoffRequestResultMessage;
import com.android.server.companion.datatransfer.continuity.messages.TaskStackBroadcastMessage;
import java.util.ArrayList;
import java.util.List;

class FakeFeatureController extends FeatureController {

    public int mOnEnabledCallCount = 0;
    public int mOnDisabledCallCount = 0;
    public List<TaskStackBroadcastMessage> mTaskStackBroadcastMessages = new ArrayList<>();
    public List<HandoffRequestMessage> mHandoffRequestMessages = new ArrayList<>();
    public List<HandoffRequestResultMessage> mHandoffRequestResultMessages = new ArrayList<>();

    public FakeFeatureController(int userId, TaskContinuityMessenger messenger) {
        super(userId, messenger);
    }

    @Override
    protected String getTag() {
        return "FakeFeatureController";
    }

    @Override
    protected void onEnabled() {
        mOnEnabledCallCount++;
    }

    @Override
    public void onDisabled() {
        mOnDisabledCallCount++;
    }

    @Override
    public void onTaskStackBroadcastMessageReceived(
            int associationId, TaskStackBroadcastMessage taskStackBroadcastMessage) {
        mTaskStackBroadcastMessages.add(taskStackBroadcastMessage);
    }

    @Override
    public void onHandoffRequestMessageReceived(
            int associationId, HandoffRequestMessage handoffRequestMessage) {
        mHandoffRequestMessages.add(handoffRequestMessage);
    }

    @Override
    public void onHandoffRequestResultMessageReceived(
            int associationId, HandoffRequestResultMessage handoffRequestResultMessage) {
        mHandoffRequestResultMessages.add(handoffRequestResultMessage);
    }
}
