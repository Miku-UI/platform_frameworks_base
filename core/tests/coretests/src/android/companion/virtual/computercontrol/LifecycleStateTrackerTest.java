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

package android.companion.virtual.computercontrol;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class LifecycleStateTrackerTest {

    @Mock
    private ComputerControlSession.LifecycleCallback mMockCallback;
    @Mock
    private Executor mExecutor;

    private LifecycleStateTracker mLifecycle;
    private AutoCloseable mMockitoSession;

    @Before
    public void setUp() {
        mMockitoSession = MockitoAnnotations.openMocks(this);
        mLifecycle = new LifecycleStateTracker();
        doAnswer((invocation) -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(mExecutor).execute(any());
    }

    @After
    public void tearDown() throws Exception {
        mMockitoSession.close();
    }

    @Test
    public void addCallback_startsInUninitializedState() {
        mLifecycle.addCallback(mExecutor, mMockCallback);

        verifyNoInteractions(mMockCallback);
    }

    @Test
    public void addCallback_notifiesInitialState() {
        mLifecycle.onActive();

        mLifecycle.addCallback(mExecutor, mMockCallback);

        verify(mMockCallback).onActive();
        verify(mExecutor).execute(any());
    }

    @Test
    public void onClose_notifiesCallback() {
        mLifecycle.addCallback(mExecutor, mMockCallback);

        mLifecycle.onClosed(ComputerControlSession.CLOSE_REASON_CALLER_INITIATED);

        verify(mMockCallback).onClosed(ComputerControlSession.CLOSE_REASON_CALLER_INITIATED);
        verify(mExecutor).execute(any());
    }

    @Test
    public void onClose_doesNotChangeCloseReason() {
        mLifecycle.addCallback(mExecutor, mMockCallback);

        mLifecycle.onClosed(ComputerControlSession.CLOSE_REASON_CALLER_INITIATED);
        mLifecycle.onClosed(ComputerControlSession.CLOSE_REASON_SESSION_TIMED_OUT);

        verify(mMockCallback, times(1)).onClosed(
                eq(ComputerControlSession.CLOSE_REASON_CALLER_INITIATED));
        verify(mExecutor, times(1)).execute(any());
    }

    @Test
    public void onBlocked_notifiesCallback() {
        // Add a callback to the tracker.
        mLifecycle.addCallback(mExecutor, mMockCallback);

        // Transition to the blocked state.
        mLifecycle.onBlocked(ComputerControlSession.BLOCK_REASON_SECURE_CONTENT, "test.package");

        // Verify that the callback's onBlocked method was called with the correct parameters.
        verify(mMockCallback).onBlocked(
                eq(ComputerControlSession.BLOCK_REASON_SECURE_CONTENT), eq("test.package"));
        verify(mExecutor).execute(any());
    }

    @Test
    public void onActive_afterClosed_throwsIllegalStateException() {
        // Close the session first.
        mLifecycle.onClosed(ComputerControlSession.CLOSE_REASON_CALLER_INITIATED);

        // Attempting to transition to active after being closed should throw an exception.
        assertThrows(IllegalStateException.class, () -> mLifecycle.onActive());
    }

    @Test
    public void onBlocked_afterClosed_throwsIllegalStateException() {
        // Close the session first.
        mLifecycle.onClosed(ComputerControlSession.CLOSE_REASON_CALLER_INITIATED);

        // Attempting to transition to blocked after being closed should throw an exception.
        assertThrows(IllegalStateException.class,
                () -> mLifecycle.onBlocked(ComputerControlSession.BLOCK_REASON_SECURE_CONTENT,
                        "test.package"));
    }

    @Test
    public void onActive_whenAlreadyActive_throwsIllegalStateException() {
        // Transition to active state.
        mLifecycle.onActive();

        // Attempting to transition to active again should throw an exception.
        assertThrows(IllegalStateException.class, () -> mLifecycle.onActive());
    }

    @Test
    public void onBlocked_whenAlreadyBlocked_throwsIllegalStateException() {
        // Transition to blocked state.
        mLifecycle.onBlocked(ComputerControlSession.BLOCK_REASON_SECURE_CONTENT, "test.package");

        // Attempting to transition to the same blocked state again should throw an exception.
        assertThrows(IllegalStateException.class,
                () -> mLifecycle.onBlocked(ComputerControlSession.BLOCK_REASON_SECURE_CONTENT,
                        "test.package"));
    }
}
