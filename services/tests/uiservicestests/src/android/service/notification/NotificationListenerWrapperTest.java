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
package android.service.notification;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public final class NotificationListenerWrapperTest {
    private static final String TAG = NotificationListenerWrapperTest.class.getSimpleName();
    private static final String TEST_PACKAGE = "test-package";
    private static final Random sRandom = new Random(547);

    @Rule
    public SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private INotificationListener mWrapper;
    private Handler mHandler;
    private volatile long mLastCompletedToken = -1;

    private final IDispatchCompletionListener mCompletionListener =
            new IDispatchCompletionListener() {
                @Override
                public void notifyDispatchComplete(long l) {
                    Log.d(TAG, "Dispatch completed for token " + l);
                    mLastCompletedToken = l;
                }

                @Override
                public IBinder asBinder() {
                    // No IPCs here.
                    return null;
                }
            };

    private static final class TestService extends NotificationListenerService {
        void initialize(Context context) {
            attachBaseContext(context);
        }
    }

    @Before
    public void setUp() {
        final TestService service = new TestService();
        service.initialize(InstrumentationRegistry.getInstrumentation().getTargetContext());
        mWrapper = INotificationListener.Stub.asInterface(service.onBind(mock(Intent.class)));
        mHandler = service.getHandler();
    }

    void waitUntilHandlerIdle() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        mHandler.post(latch::countDown);
        assertWithMessage("mHandler inside NotificationListenerService stuck for too long").that(
                latch.await(30, TimeUnit.SECONDS)).isTrue();
    }

    private static int generateToken() {
        return sRandom.nextInt(1000);
    }

    void connectAndVerifyListener() throws Exception {
        final long token = generateToken();
        mWrapper.onListenerConnected(mock(NotificationRankingUpdate.class), mCompletionListener,
                token);
        waitUntilHandlerIdle();
        assertThat(mLastCompletedToken).isEqualTo(token);
    }

    @Test
    @EnableFlags(Flags.FLAG_REPORT_NLS_START_AND_END)
    public void onListenerConnectedCallsDispatchCompletion() throws Exception {
        connectAndVerifyListener();
    }

    @Test
    @EnableFlags(Flags.FLAG_REPORT_NLS_START_AND_END)
    public void onNotificationPostedCallsDispatchCompletion() throws Exception {
        connectAndVerifyListener();
        final long token = generateToken();
        final IStatusBarNotificationHolder mockSbnHolder = mock(IStatusBarNotificationHolder.class);
        when(mockSbnHolder.get()).thenReturn(mock(StatusBarNotification.class));
        mWrapper.onNotificationPosted(mockSbnHolder, mock(NotificationRankingUpdate.class), token);
        waitUntilHandlerIdle();
        assertThat(mLastCompletedToken).isEqualTo(token);
    }

    @Test
    @EnableFlags(Flags.FLAG_REPORT_NLS_START_AND_END)
    public void onNotificationPostedFullCallsDispatchCompletion() throws Exception {
        connectAndVerifyListener();
        final long token = generateToken();
        mWrapper.onNotificationPostedFull(mock(StatusBarNotification.class),
                mock(NotificationRankingUpdate.class), token);
        waitUntilHandlerIdle();
        assertThat(mLastCompletedToken).isEqualTo(token);
    }

    @Test
    @EnableFlags(Flags.FLAG_REPORT_NLS_START_AND_END)
    public void onStatusBarIconsBehaviorChangedCallsDispatchCompletion() throws Exception {
        connectAndVerifyListener();
        final long token = generateToken();
        mWrapper.onStatusBarIconsBehaviorChanged(false, token);
        waitUntilHandlerIdle();
        assertThat(mLastCompletedToken).isEqualTo(token);
    }

    @Test
    @EnableFlags(Flags.FLAG_REPORT_NLS_START_AND_END)
    public void onNotificationRemovedCallsDispatchCompletion() throws Exception {
        connectAndVerifyListener();
        final long token = generateToken();
        final IStatusBarNotificationHolder mockSbnHolder = mock(IStatusBarNotificationHolder.class);
        when(mockSbnHolder.get()).thenReturn(mock(StatusBarNotification.class));
        mWrapper.onNotificationRemoved(mockSbnHolder, mock(NotificationRankingUpdate.class),
                mock(NotificationStats.class), 0, token);
        waitUntilHandlerIdle();
        assertThat(mLastCompletedToken).isEqualTo(token);
    }

    @Test
    @EnableFlags(Flags.FLAG_REPORT_NLS_START_AND_END)
    public void onNotificationRemovedFullCallsDispatchCompletion() throws Exception {
        connectAndVerifyListener();
        final long token = generateToken();
        mWrapper.onNotificationRemovedFull(mock(StatusBarNotification.class),
                mock(NotificationRankingUpdate.class), mock(NotificationStats.class), 0, token);
        waitUntilHandlerIdle();
        assertThat(mLastCompletedToken).isEqualTo(token);
    }

    @Test
    @EnableFlags(Flags.FLAG_REPORT_NLS_START_AND_END)
    public void onNotificationRankingUpdateCallsDispatchCompletion() throws Exception {
        connectAndVerifyListener();
        final long token = generateToken();
        mWrapper.onNotificationRankingUpdate(mock(NotificationRankingUpdate.class), token);
        waitUntilHandlerIdle();
        assertThat(mLastCompletedToken).isEqualTo(token);
    }

    @Test
    @EnableFlags(Flags.FLAG_REPORT_NLS_START_AND_END)
    public void onListenerHintsChangedCallsDispatchCompletion() throws Exception {
        connectAndVerifyListener();
        final long token = generateToken();
        mWrapper.onListenerHintsChanged(0, token);
        waitUntilHandlerIdle();
        assertThat(mLastCompletedToken).isEqualTo(token);
    }

    @Test
    @EnableFlags(Flags.FLAG_REPORT_NLS_START_AND_END)
    public void onInterruptionFilterChangedCallsDispatchCompletion() throws Exception {
        connectAndVerifyListener();
        final long token = generateToken();
        mWrapper.onInterruptionFilterChanged(0, token);
        waitUntilHandlerIdle();
        assertThat(mLastCompletedToken).isEqualTo(token);
    }

    @Test
    @EnableFlags(Flags.FLAG_REPORT_NLS_START_AND_END)
    public void onNotificationChannelModificationCallsDispatchCompletion() throws Exception {
        connectAndVerifyListener();
        final long token = generateToken();
        mWrapper.onNotificationChannelModification(TEST_PACKAGE, mock(UserHandle.class), mock(
                NotificationChannel.class), 0, token);
        waitUntilHandlerIdle();
        assertThat(mLastCompletedToken).isEqualTo(token);
    }

    @Test
    @EnableFlags(Flags.FLAG_REPORT_NLS_START_AND_END)
    public void onNotificationChannelGroupModificationCallsDispatchCompletion() throws Exception {
        connectAndVerifyListener();
        final long token = generateToken();
        mWrapper.onNotificationChannelGroupModification(TEST_PACKAGE, mock(UserHandle.class),
                mock(NotificationChannelGroup.class), 0, token);
        waitUntilHandlerIdle();
        assertThat(mLastCompletedToken).isEqualTo(token);
    }
}
