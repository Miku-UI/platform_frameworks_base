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

package com.android.server.audio;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.Message;

import androidx.test.filters.SmallTest;

import com.android.server.testutils.OffsettableClock;
import com.android.server.testutils.TestHandler;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(JUnit4.class)
public class ActivityDebouncerTest {
    private static final int MSG_INACTIVITY = 1;
    private static final int DEBOUNCE_MS = 1024;
    private static final int TEST_UID = 10001;
    private static final int TEST_UID_2 = 10002;

    // Verification mock
    public interface ClientActivityListener {
        void onClientActiveChanged(int uid, boolean active);
    }

    @Mock private ClientActivityListener mMockListener;

    private TestHandler mTestHandler;
    private ActivityDebouncer mActivityDebouncer;
    private OffsettableClock mClock;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mClock = new OffsettableClock.Stopped();
        mTestHandler = new TestHandler(null, mClock) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MSG_INACTIVITY) {
                    mMockListener.onClientActiveChanged((Integer) msg.obj, false);
                }
            }
        };
        mActivityDebouncer = new ActivityDebouncer(
                mTestHandler,
                MSG_INACTIVITY,
                uid -> mMockListener.onClientActiveChanged(uid, true),
                DEBOUNCE_MS
        );
    }

    @Test
    public void update_activityTransition_notifiesActive() {
        mActivityDebouncer.update(new int[]{TEST_UID});

        verify(mMockListener).onClientActiveChanged(TEST_UID, true);
        mClock.fastForward(DEBOUNCE_MS + 1);
        mTestHandler.timeAdvance();
        verify(mMockListener, never()).onClientActiveChanged(TEST_UID, false);
    }

    @Test
    public void update_inactivityTransition_notifiesInactiveAfterDelay() {
        mActivityDebouncer.update(new int[]{TEST_UID});
        verify(mMockListener).onClientActiveChanged(TEST_UID, true);

        mActivityDebouncer.update(new int[]{});
        mClock.fastForward(DEBOUNCE_MS - 1);
        mTestHandler.timeAdvance();

        verify(mMockListener, never()).onClientActiveChanged(TEST_UID, false);

        mClock.fastForward(1);
        mTestHandler.timeAdvance();

        verify(mMockListener).onClientActiveChanged(TEST_UID, false);
    }

    @Test
    public void update_reactivationInDebounce_neverNotifiesInactive() {
        mActivityDebouncer.update(new int[]{TEST_UID});
        verify(mMockListener).onClientActiveChanged(TEST_UID, true);

        mActivityDebouncer.update(new int[]{});

        mClock.fastForward(DEBOUNCE_MS / 2);
        mTestHandler.timeAdvance();

        mActivityDebouncer.update(new int[]{TEST_UID});

        // Wait the full duration to ensure that we didn't accidentally re-schedule
        mClock.fastForward(DEBOUNCE_MS + 1);
        mTestHandler.timeAdvance();

        verify(mMockListener, never()).onClientActiveChanged(TEST_UID, false);
    }

    @Test
    @Ignore
    public void update_withMultipleUids_dispatchesCorrectInactivity() {
        mActivityDebouncer.update(new int[]{TEST_UID, TEST_UID_2});
        verify(mMockListener).onClientActiveChanged(TEST_UID, true);
        verify(mMockListener).onClientActiveChanged(TEST_UID_2, true);
        mActivityDebouncer.update(new int[]{});


        mClock.fastForward(DEBOUNCE_MS / 2);
        mTestHandler.timeAdvance();
        mActivityDebouncer.update(new int[]{TEST_UID});

        mClock.fastForward(DEBOUNCE_MS + 1);
        mTestHandler.timeAdvance();

        verify(mMockListener, never()).onClientActiveChanged(TEST_UID, false);
        verify(mMockListener).onClientActiveChanged(TEST_UID_2, false);
    }


    @Test
    public void simulateActive_forInactiveUid_schedulesInactivityMessage() {
        mActivityDebouncer.simulateActive(TEST_UID);

        verify(mMockListener, never()).onClientActiveChanged(TEST_UID, true);

        mClock.fastForward(DEBOUNCE_MS + 1);
        mTestHandler.timeAdvance();
        verify(mMockListener).onClientActiveChanged(TEST_UID, false);
    }

    @Test
    public void simulateActive_forActiveUid_doesNothing() {
        mActivityDebouncer.update(new int[]{TEST_UID});

        mActivityDebouncer.simulateActive(TEST_UID);

        mClock.fastForward(DEBOUNCE_MS + 1);
        mTestHandler.timeAdvance();
        verify(mMockListener, never()).onClientActiveChanged(TEST_UID, false);
    }

    @Test
    @Ignore
    public void simulateActive_onRecentlyInactiveUid_reschedulesInactivity() {
        mActivityDebouncer.update(new int[]{TEST_UID});
        verify(mMockListener).onClientActiveChanged(TEST_UID, true);
        mActivityDebouncer.update(new int[]{});

        mClock.fastForward(DEBOUNCE_MS / 2);
        mTestHandler.timeAdvance();

        mActivityDebouncer.simulateActive(TEST_UID);

        // The original message should not be fired
        mClock.fastForward((DEBOUNCE_MS / 2) + 1);
        mTestHandler.timeAdvance();
        verify(mMockListener, never()).onClientActiveChanged(TEST_UID, false);

        // The new message should be fired after the full debounce period
        mClock.fastForward(DEBOUNCE_MS / 2);
        mTestHandler.timeAdvance();
        verify(mMockListener).onClientActiveChanged(TEST_UID, false);
    }

    @Test
    public void simulateActive_thenUpdate_cancelsInactivityAndActivates() {
        mActivityDebouncer.simulateActive(TEST_UID);
        verify(mMockListener, never()).onClientActiveChanged(TEST_UID, true);

        mClock.fastForward(DEBOUNCE_MS / 2);
        mTestHandler.timeAdvance();

        verify(mMockListener, never()).onClientActiveChanged(TEST_UID, false);

        mActivityDebouncer.update(new int[]{TEST_UID});

        mClock.fastForward(DEBOUNCE_MS + 1);
        mTestHandler.timeAdvance();

        verify(mMockListener, never()).onClientActiveChanged(TEST_UID, false);
    }
}

