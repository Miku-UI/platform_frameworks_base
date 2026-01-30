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
package android.app;

import static android.app.ActivityManager.AppTask.WINDOWING_LAYER_NORMAL_APP;
import static android.app.ActivityManager.AppTask.WINDOWING_LAYER_PINNED;
import static android.app.TaskWindowingLayerRequestHandler.REMOTE_CALLBACK_RESULT_KEY;
import static android.app.TaskWindowingLayerRequestHandler.RESULT_APPROVED;
import static android.app.TaskWindowingLayerRequestHandler.RESULT_FAILED_BAD_STATE;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import android.os.Bundle;
import android.os.IRemoteCallback;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.Executor;

/**
 * Test class for {@link TaskWindowingLayerRequestHandler}.
 *
 * Build/Install/Run:
 *  atest TaskWindowingLayerRequestHandlerTest
 */
@SmallTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class TaskWindowingLayerRequestHandlerTest {

    private static final Executor SAME_THREAD_EXECUTOR = Runnable::run;
    private IAppTask mAppTask;
    private OutcomeReceiver<Void, Exception> mCallback;
    private ArgumentCaptor<IRemoteCallback> mRemoteCallbackCaptor;
    private ArgumentCaptor<Exception> mExceptionCaptor;

    @Before
    public void setUp() {
        mAppTask = mock(IAppTask.class);
        mCallback = mock(OutcomeReceiver.class);
        mRemoteCallbackCaptor = ArgumentCaptor.forClass(IRemoteCallback.class);
        mExceptionCaptor = ArgumentCaptor.forClass(Exception.class);
    }

    @Test
    public void requestWindowingLayer_propagatesSuccessResult() throws RemoteException {
        final Bundle actualResult = resultWithCode(RESULT_APPROVED);

        TaskWindowingLayerRequestHandler.requestWindowingLayer(
                WINDOWING_LAYER_NORMAL_APP,
                SAME_THREAD_EXECUTOR,
                mCallback,
                mAppTask
        );
        verify(mAppTask).requestWindowingLayer(eq(WINDOWING_LAYER_NORMAL_APP),
                mRemoteCallbackCaptor.capture());
        mRemoteCallbackCaptor.getValue().sendResult(actualResult);

        verifyCallbackReceivedSuccessResultOnce();
    }

    @Test
    public void requestWindowingLayer_propagatesBadStateFailure() throws RemoteException {
        final Bundle actualResult = resultWithCode(RESULT_FAILED_BAD_STATE);

        TaskWindowingLayerRequestHandler.requestWindowingLayer(
                WINDOWING_LAYER_PINNED,
                SAME_THREAD_EXECUTOR,
                mCallback,
                mAppTask
        );
        verify(mAppTask).requestWindowingLayer(eq(WINDOWING_LAYER_PINNED),
                mRemoteCallbackCaptor.capture());
        mRemoteCallbackCaptor.getValue().sendResult(actualResult);

        verifyCallbackReceivedErrorOnce(IllegalStateException.class,
                "The current system windowing state is not appropriate to fulfill the request.");
    }

    @Test
    public void requestWindowingLayer_whenInvalidResult_propagatesError() throws RemoteException {
        final Bundle actualResult = resultWithCode(-1); // invalid code

        TaskWindowingLayerRequestHandler.requestWindowingLayer(
                WINDOWING_LAYER_PINNED,
                SAME_THREAD_EXECUTOR,
                mCallback,
                mAppTask
        );
        verify(mAppTask).requestWindowingLayer(eq(WINDOWING_LAYER_PINNED),
                mRemoteCallbackCaptor.capture());
        mRemoteCallbackCaptor.getValue().sendResult(actualResult);

        verifyCallbackReceivedErrorOnce(IllegalStateException.class, "Unknown error, code=-1");
    }

    @Test(expected = RuntimeException.class)
    public void requestWindowingLayer_whenRemoteException_isRethrown() throws RemoteException {
        doThrow(new RemoteException("foo")).when(mAppTask)
                .requestWindowingLayer(anyInt(), any());

        TaskWindowingLayerRequestHandler.requestWindowingLayer(
                WINDOWING_LAYER_PINNED,
                SAME_THREAD_EXECUTOR,
                mCallback,
                mAppTask
        );
    }

    private void verifyCallbackReceivedSuccessResultOnce() {
        verify(mCallback).onResult(any());
        verify(mCallback, never()).onError(any());
    }

    private <E extends Exception> void verifyCallbackReceivedErrorOnce(Class<E> expectedException,
            String expectedMessage) {
        verify(mCallback, never()).onResult(any());
        verify(mCallback).onError(mExceptionCaptor.capture());
        Exception captured = mExceptionCaptor.getValue();
        assertTrue(expectedException.isInstance(captured));
        assertEquals(captured.getMessage(), expectedMessage);
    }

    private static Bundle resultWithCode(int result) {
        Bundle resultData = new Bundle();
        resultData.putInt(REMOTE_CALLBACK_RESULT_KEY, result);
        return resultData;
    }
}
