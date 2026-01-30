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

package com.android.server.companion.datatransfer.continuity.handoff;

import static com.google.common.truth.Truth.assertThat;

import android.companion.datatransfer.continuity.IHandoffRequestCallback;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.List;

public final class FakeHandoffRequestCallback extends IHandoffRequestCallback.Stub {

    final List<Integer> receivedAssociationIds = new ArrayList<>();
    final List<Integer> receivedTaskIds = new ArrayList<>();
    final List<Integer> receivedResultCodes = new ArrayList<>();

    @Override
    public void onHandoffRequestFinished(
        int associationId,
        int remoteTaskId,
        int resultCode) throws RemoteException {

        receivedAssociationIds.add(associationId);
        receivedTaskIds.add(remoteTaskId);
        receivedResultCodes.add(resultCode);
    }

    void verifyInvoked(int associationId, int taskId, int resultCode) {
        assertThat(receivedAssociationIds).containsExactly(associationId);
        assertThat(receivedTaskIds).containsExactly(taskId);
        assertThat(receivedResultCodes).containsExactly(resultCode);
    }

    void verifyNotInvoked() {
        assertThat(receivedAssociationIds).isEmpty();
        assertThat(receivedTaskIds).isEmpty();
        assertThat(receivedResultCodes).isEmpty();
    }
}
