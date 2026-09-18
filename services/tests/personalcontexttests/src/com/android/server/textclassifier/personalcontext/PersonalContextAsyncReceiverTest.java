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

package com.android.server.textclassifier.personalcontext;

import static com.android.server.textclassifier.personalcontext.PersonalContextAsyncReceiver.MAX_SESSIONS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.OutcomeReceiver;
import android.view.textclassifier.TextClassification;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.server.textclassifier.personalcontext.PersonalContextBridge.TextClassificationKey;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PersonalContextAsyncReceiverTest {

    private static final long TEST_TIMEOUT_MILLIS = 5;
    private static final String TEST_SESSION_ID = "test-session-id";
    private static final String TEST_TEXT = "test-text";
    private static final TextClassification.Request TEST_REQUEST =
            new TextClassification.Request.Builder(TEST_TEXT, 0, TEST_TEXT.length()).build();
    private static final TextClassificationKey TEST_KEY =
            new TextClassificationKey(TEST_SESSION_ID, TEST_REQUEST);
    private static final TextClassification TEST_CLASSIFICATION =
            new TextClassification.Builder().setText(TEST_TEXT).build();
    @Mock private ScheduledExecutorService mScheduledExecutorService;
    @Mock private ScheduledFuture<?> mScheduledFuture;
    @Captor private ArgumentCaptor<Runnable> mCancellationRunnableCaptor;
    private PersonalContextAsyncReceiver mRetriever;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        // Using thenAnswer as thenReturn does not work well with <?> type capture
        when(mScheduledExecutorService.schedule(
                        any(Runnable.class), eq(TEST_TIMEOUT_MILLIS), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> mScheduledFuture);

        mRetriever =
                new PersonalContextAsyncReceiver(mScheduledExecutorService, TEST_TIMEOUT_MILLIS);
    }

    @Test
    public void testGetAsync_afterPut_returnsImmediately() {
        mRetriever.put(TEST_KEY, TEST_CLASSIFICATION);

        mRetriever.getAsync(TEST_KEY, result -> assertThat(result).isEqualTo(TEST_CLASSIFICATION));
    }

    @Test
    public void testGetAsync_beforePut_returnsAfter() {
        mRetriever.getAsync(TEST_KEY, result -> assertThat(result).isEqualTo(TEST_CLASSIFICATION));

        mRetriever.put(TEST_KEY, TEST_CLASSIFICATION);
    }

    @Test
    public void testGetAsync_withoutPut_timesOut() throws Exception {
        mRetriever.getAsync(TEST_KEY, expectTimeoutRetriever());

        getTimeoutTask().run();
    }

    @Test
    public void testGetAsync_afterTimeout_returnsNothing() throws Exception {
        mRetriever.getAsync(TEST_KEY, expectTimeoutRetriever());
        getTimeoutTask().run();

        mRetriever.put(TEST_KEY, TEST_CLASSIFICATION);
    }

    @Test
    public void testGetAsync_withGetAsyncAlreadyCalled_returnsNothing() {
        mRetriever.put(TEST_KEY, TEST_CLASSIFICATION);
        mRetriever.getAsync(TEST_KEY, result -> {});

        mRetriever.getAsync(
                TEST_KEY,
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(TextClassification result) {
                        assertWithMessage("Should not receive a result.").fail();
                    }

                    @Override
                    public void onError(@NonNull TimeoutException e) {
                        assertWithMessage("Should not timeout").fail();
                    }
                });
    }

    @Test
    public void testGetAsync_withSessionsFull_timesOut() {
        mRetriever.put(TEST_KEY, TEST_CLASSIFICATION);

        for (int i = 0; i < MAX_SESSIONS; i++) {
            mRetriever.put(
                    new TextClassificationKey(TEST_SESSION_ID + i, TEST_REQUEST),
                    TEST_CLASSIFICATION);
        }

        mRetriever.getAsync(TEST_KEY, expectTimeoutRetriever());
    }

    private Runnable getTimeoutTask() {
        verify(mScheduledExecutorService)
                .schedule(
                        mCancellationRunnableCaptor.capture(),
                        eq(TEST_TIMEOUT_MILLIS),
                        eq(TimeUnit.MILLISECONDS));
        return mCancellationRunnableCaptor.getValue();
    }

    private OutcomeReceiver<TextClassification, TimeoutException> expectTimeoutRetriever() {
        return new OutcomeReceiver<>() {
            @Override
            public void onResult(TextClassification result) {
                assertWithMessage("Should not receive a result.").fail();
            }

            @Override
            public void onError(@NonNull TimeoutException e) {
                assertThat(e).isInstanceOf(TimeoutException.class);
            }
        };
    }
}
