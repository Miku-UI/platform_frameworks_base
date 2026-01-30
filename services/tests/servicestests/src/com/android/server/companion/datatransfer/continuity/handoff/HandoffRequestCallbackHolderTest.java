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

import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_FAILURE_TIMEOUT;
import static android.companion.datatransfer.continuity.TaskContinuityManager.HANDOFF_REQUEST_RESULT_SUCCESS;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.companion.datatransfer.continuity.IHandoffRequestCallback;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Presubmit
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class HandoffRequestCallbackHolderTest {

    private HandoffRequestCallbackHolder mCallbackHolder;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mCallbackHolder = new HandoffRequestCallbackHolder();
    }

    @Test
    public void notifyAndRemoveCallbacks_notifiesAndRemoves() throws RemoteException {
        int associationId = 1;
        int taskId = 101;
        FakeHandoffRequestCallback callback = new FakeHandoffRequestCallback();
        mCallbackHolder.registerCallback(associationId, taskId, callback);

        mCallbackHolder.notifyAndRemoveCallbacks(
                associationId, taskId, HANDOFF_REQUEST_RESULT_SUCCESS);

        callback.verifyInvoked(associationId, taskId, HANDOFF_REQUEST_RESULT_SUCCESS);
    }

    @Test
    public void notifyAndRemoveCallbacks_notifiesCorrectCallback() throws RemoteException {
        int associationId1 = 1;
        int taskId1 = 101;
        int associationId2 = 2;
        int taskId2 = 202;
        FakeHandoffRequestCallback callback1 = new FakeHandoffRequestCallback();
        FakeHandoffRequestCallback callback2 = new FakeHandoffRequestCallback();
        mCallbackHolder.registerCallback(associationId1, taskId1, callback1);
        mCallbackHolder.registerCallback(associationId2, taskId2, callback2);

        mCallbackHolder.notifyAndRemoveCallbacks(
                associationId1, taskId1, HANDOFF_REQUEST_RESULT_SUCCESS);

        callback1.verifyInvoked(associationId1, taskId1, HANDOFF_REQUEST_RESULT_SUCCESS);
        callback2.verifyNotInvoked();
    }

    @Test
    public void notifyAndRemoveCallbacks_multipleCallbacksForSameRequest() throws RemoteException {
        int associationId = 1;
        int taskId = 101;
        FakeHandoffRequestCallback callback1 = new FakeHandoffRequestCallback();
        FakeHandoffRequestCallback callback2 = new FakeHandoffRequestCallback();
        IHandoffRequestCallback anotherCallbackForSameRequest = mock(IHandoffRequestCallback.class);
        mCallbackHolder.registerCallback(associationId, taskId, callback1);
        mCallbackHolder.registerCallback(associationId, taskId, callback2);

        mCallbackHolder.notifyAndRemoveCallbacks(
                associationId, taskId, HANDOFF_REQUEST_RESULT_SUCCESS);

        callback1.verifyInvoked(associationId, taskId, HANDOFF_REQUEST_RESULT_SUCCESS);
        callback2.verifyInvoked(associationId, taskId, HANDOFF_REQUEST_RESULT_SUCCESS);
    }

    @Test
    public void notifyAndRemoveCallbacks_noMatchingCallback() throws RemoteException {
        int associationId = 1;
        int taskId = 101;
        FakeHandoffRequestCallback callback = new FakeHandoffRequestCallback();
        mCallbackHolder.registerCallback(associationId, taskId, callback);

        mCallbackHolder.notifyAndRemoveCallbacks(
                associationId, 100, HANDOFF_REQUEST_RESULT_SUCCESS);

        callback.verifyNotInvoked();
    }
}