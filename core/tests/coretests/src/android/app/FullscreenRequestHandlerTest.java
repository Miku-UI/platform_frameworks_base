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

import static android.app.Activity.FULLSCREEN_MODE_REQUEST_ENTER;
import static android.app.Activity.FULLSCREEN_MODE_REQUEST_EXIT;
import static android.app.FullscreenRequestHandler.REMOTE_CALLBACK_RESULT_KEY;
import static android.app.FullscreenRequestHandler.RESULT_APPROVED;
import static android.app.FullscreenRequestHandler.RESULT_FAILED_ALREADY_FULLY_EXPANDED;
import static android.app.FullscreenRequestHandler.RESULT_FAILED_NOT_IN_FULLSCREEN_WITH_HISTORY;
import static android.app.FullscreenRequestHandler.RESULT_FAILED_NOT_SUPPORTED;
import static android.app.FullscreenRequestHandler.RESULT_FAILED_NOT_TOP_FOCUSED;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.os.Bundle;
import android.os.IBinder;
import android.os.IRemoteCallback;
import android.os.OutcomeReceiver;
import android.platform.test.annotations.Presubmit;
import android.platform.test.ravenwood.RavenwoodRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
@Presubmit
public class FullscreenRequestHandlerTest {
    private static final Executor SAME_THREAD_EXECUTOR = Runnable::run;

    @Rule
    public final RavenwoodRule mRavenwood = new RavenwoodRule();

    @Mock
    private IBinder mToken;
    @Mock
    private OutcomeReceiver<Void, Throwable> mCallback;
    @Mock
    private ActivityClient mActivityClient;

    private FullscreenRequestHandler mFullscreenRequestHandler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mFullscreenRequestHandler = new FullscreenRequestHandler(mActivityClient);
    }

    @Test
    public void testRequestExitFullscreen_whenInFullscreen_sendsRequest() {
        mFullscreenRequestHandler.requestFullscreenMode(FULLSCREEN_MODE_REQUEST_EXIT, mCallback,
                mToken, SAME_THREAD_EXECUTOR);

        verify(mCallback, never()).onError(any());
        verify(mActivityClient).requestMultiwindowFullscreen(eq(mToken),
                eq(FULLSCREEN_MODE_REQUEST_EXIT), any());
    }

    @Test
    public void testRequestEnterFullscreen_whenAllowed_sendsRequest() {
        mFullscreenRequestHandler.requestFullscreenMode(FULLSCREEN_MODE_REQUEST_ENTER, mCallback,
                mToken, SAME_THREAD_EXECUTOR);

        verify(mCallback, never()).onError(any());
        verify(mActivityClient).requestMultiwindowFullscreen(eq(mToken),
                eq(FULLSCREEN_MODE_REQUEST_ENTER), any());
    }

    @Test
    public void testRequestFullscreen_whenNoCallback_sendsRequestWithoutCallback() {
        mFullscreenRequestHandler.requestFullscreenMode(FULLSCREEN_MODE_REQUEST_EXIT, null,
                mToken, SAME_THREAD_EXECUTOR);

        verify(mActivityClient).requestMultiwindowFullscreen(eq(mToken),
                eq(FULLSCREEN_MODE_REQUEST_EXIT), eq(null));
    }

    @Test
    public void testRequestFullscreen_whenActivityClientThrows_propagatesError() {
        RuntimeException exception = new RuntimeException("Test Exception");
        doThrow(exception).when(mActivityClient).requestMultiwindowFullscreen(any(), anyInt(),
                any());

        mFullscreenRequestHandler.requestFullscreenMode(FULLSCREEN_MODE_REQUEST_EXIT, mCallback,
                mToken, SAME_THREAD_EXECUTOR);

        verify(mCallback).onError(exception);
    }

    @Test
    public void testRequestFullscreen_whenActivityClientApproves_callbackReceivesApproved() {
        doAnswer((Answer<Void>) invocation -> {
            IRemoteCallback remoteCallback = invocation.getArgument(2);
            Bundle result = new Bundle();
            result.putInt(REMOTE_CALLBACK_RESULT_KEY, RESULT_APPROVED);
            remoteCallback.sendResult(result);
            return null;
        }).when(mActivityClient).requestMultiwindowFullscreen(eq(mToken), anyInt(), any());


        mFullscreenRequestHandler.requestFullscreenMode(FULLSCREEN_MODE_REQUEST_EXIT, mCallback,
                mToken, SAME_THREAD_EXECUTOR);

        verify(mCallback).onResult(null);
    }

    @Test
    public void testRequestFullscreen_whenActivityClientReturnsNotInFullscreen_propagatesError() {
        testRequestFullscreen_whenActivityClientReturnsError_propagatesError(
                RESULT_FAILED_NOT_IN_FULLSCREEN_WITH_HISTORY, IllegalStateException.class);
    }

    @Test
    public void testRequestFullscreen_whenActivityClientReturnsNotTopFocused_propagatesError() {
        testRequestFullscreen_whenActivityClientReturnsError_propagatesError(
                RESULT_FAILED_NOT_TOP_FOCUSED, IllegalStateException.class);
    }

    @Test
    public void testRequestFullscreen_whenActivityClientReturnsAlreadyExpanded_propagatesError() {
        testRequestFullscreen_whenActivityClientReturnsError_propagatesError(
                RESULT_FAILED_ALREADY_FULLY_EXPANDED, IllegalStateException.class);
    }

    @Test
    public void testRequestFullscreen_whenActivityClientReturnsNotSupported_propagatesError() {
        testRequestFullscreen_whenActivityClientReturnsError_propagatesError(
                RESULT_FAILED_NOT_SUPPORTED, UnsupportedOperationException.class);
    }

    private void testRequestFullscreen_whenActivityClientReturnsError_propagatesError(
            int resultCode, Class<? extends Throwable> expectedException) {
        doAnswer((Answer<Void>) invocation -> {
            IRemoteCallback remoteCallback = invocation.getArgument(2);
            Bundle result = new Bundle();
            result.putInt(REMOTE_CALLBACK_RESULT_KEY, resultCode);
            remoteCallback.sendResult(result);
            return null;
        }).when(mActivityClient).requestMultiwindowFullscreen(eq(mToken), anyInt(), any());

        mFullscreenRequestHandler.requestFullscreenMode(FULLSCREEN_MODE_REQUEST_EXIT, mCallback,
                mToken, SAME_THREAD_EXECUTOR);

        verify(mCallback).onError(any(expectedException));
    }
}
