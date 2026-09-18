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

package com.android.server.companion.virtual.computercontrol;

import static android.companion.virtual.computercontrol.ComputerControlSession.BLOCK_REASON_CALLER_INITIATED;
import static android.companion.virtual.computercontrol.ComputerControlSession.BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH;
import static android.companion.virtual.computercontrol.ComputerControlSession.BLOCK_REASON_SECURE_CONTENT;
import static android.companion.virtual.computercontrol.ComputerControlSession.CLOSE_REASON_CALLER_INITIATED;
import static android.companion.virtual.computercontrol.ComputerControlSession.CLOSE_REASON_SESSION_TIMED_OUT;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.companion.virtual.computercontrol.ComputerControlSession;
import android.companion.virtual.computercontrol.IComputerControlLifecycleCallback;
import android.companion.virtual.computercontrol.LifecycleState.Active;
import android.companion.virtual.computercontrol.LifecycleState.Blocked;
import android.companion.virtual.computercontrol.LifecycleState.Closed;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class SessionLifecycleTest {

    private static final String TEST_PKG = "com.test.pkg";

    @Mock
    private IComputerControlLifecycleCallback mRemoteCallback;
    @Mock
    private ComputerControlSession.LifecycleCallback mLocalCallback;

    private AutoCloseable mMockitoSession;
    private SessionLifecycle mLifecycle;

    @Before
    public void setUp() {
        mMockitoSession = MockitoAnnotations.openMocks(this);
        mLifecycle = new SessionLifecycle(Runnable::run, mLocalCallback);
    }

    @After
    public void tearDown() throws Exception {
        mMockitoSession.close();
    }

    @Test
    public void initializeLifecycle_startsInActiveState() throws Exception {
        mLifecycle.initializeWithRemoteCallback(mRemoteCallback);

        verify(mRemoteCallback).onActive();
        verify(mLocalCallback).onActive();
        assertThat(mLifecycle.getCurrentState()).isInstanceOf(Active.class);
    }

    @Test
    public void addRemoteCallbackTwice_throwsIllegalStateException() {
        mLifecycle.initializeWithRemoteCallback(mRemoteCallback);

        assertThrows(IllegalStateException.class,
                () -> mLifecycle.initializeWithRemoteCallback(mRemoteCallback));
    }

    @Test
    public void updateLifecycle_entersClosedState() throws Exception {
        initializeCallbacksAndReset();

        final var state = mLifecycle.updateLifecycleState(
                (config) -> config.mClosed = new Closed(CLOSE_REASON_CALLER_INITIATED));
        assertThat(state).isInstanceOf(Closed.class);
        assertThat(((Closed) state).reason).isEqualTo(CLOSE_REASON_CALLER_INITIATED);
        verify(mRemoteCallback).onClosed(CLOSE_REASON_CALLER_INITIATED);
        verify(mLocalCallback).onClosed(CLOSE_REASON_CALLER_INITIATED);
    }

    @Test
    public void updateLifecycle_cannotChangeCloseReason() throws Exception {
        initializeCallbacksAndReset();
        mLifecycle.updateLifecycleState(
                (config) -> config.mClosed = new Closed(CLOSE_REASON_CALLER_INITIATED));
        verify(mRemoteCallback).onClosed(CLOSE_REASON_CALLER_INITIATED);
        verify(mLocalCallback).onClosed(CLOSE_REASON_CALLER_INITIATED);
        Mockito.reset(mLocalCallback, mRemoteCallback);

        final var state = mLifecycle.updateLifecycleState(
                (config) -> config.mClosed = new Closed(CLOSE_REASON_SESSION_TIMED_OUT));

        assertThat(state).isInstanceOf(Closed.class);
        assertThat(((Closed) state).reason).isEqualTo(CLOSE_REASON_CALLER_INITIATED);
        verify(mLocalCallback, never()).onClosed(anyInt());
        verify(mRemoteCallback, never()).onClosed(anyInt());
    }

    @Test
    public void updateLifecycle_canBeCalledBeforeInitialization() {
        mLifecycle.updateLifecycleState((config) -> {});

        verify(mLocalCallback).onActive();
        assertThat(mLifecycle.getCurrentState()).isInstanceOf(Active.class);
    }

    @Test
    public void updateLifecycle_secureWindowVisibility_controlsBlockedState() throws Exception {
        initializeCallbacksAndReset();

        var state = mLifecycle.updateLifecycleState(
                (config) -> config.mSecureWindowPackage = TEST_PKG);

        assertThat(state).isInstanceOf(Blocked.class);
        assertThat(((Blocked) state).reason).isEqualTo(BLOCK_REASON_SECURE_CONTENT);
        verify(mLocalCallback).onBlocked(BLOCK_REASON_SECURE_CONTENT, TEST_PKG);
        verify(mRemoteCallback).onBlocked(BLOCK_REASON_SECURE_CONTENT, TEST_PKG);

        state = mLifecycle.updateLifecycleState((config) -> config.mSecureWindowPackage = null);
        assertThat(state).isInstanceOf(Blocked.class);
        verify(mLocalCallback, never()).onActive();
        verify(mRemoteCallback, never()).onActive();

        state = mLifecycle.exitBlockedState();
        assertThat(state).isInstanceOf(Active.class);
        verify(mLocalCallback).onActive();
        verify(mRemoteCallback).onActive();
    }

    @Test
    public void updateLifecycle_blockedActivityVisibility_controlsBlockedState() throws Exception {
        initializeCallbacksAndReset();

        var state = mLifecycle.updateLifecycleState(
                (config) -> config.mBlockingActivityPackage = TEST_PKG);

        assertThat(state).isInstanceOf(Blocked.class);
        assertThat(((Blocked) state).reason).isEqualTo(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH);
        verify(mLocalCallback).onBlocked(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH, TEST_PKG);
        verify(mRemoteCallback).onBlocked(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH, TEST_PKG);

        state = mLifecycle.updateLifecycleState((config) -> config.mBlockingActivityPackage = null);

        assertThat(state).isInstanceOf(Blocked.class);
        verify(mLocalCallback, never()).onActive();
        verify(mRemoteCallback, never()).onActive();

        state = mLifecycle.exitBlockedState();

        assertThat(state).isInstanceOf(Active.class);
        verify(mLocalCallback).onActive();
        verify(mRemoteCallback).onActive();
    }

    @Test
    public void updateLifecycle_withBlockedActivityAndSecureWindow_entersBlockedState()
            throws Exception {
        initializeCallbacksAndReset();

        var state = mLifecycle.updateLifecycleState((config) -> {
            config.mBlockingActivityPackage = TEST_PKG;
            config.mSecureWindowPackage = TEST_PKG;
        });

        // The disallowed activity block reason overrides the secure content block reason.
        assertThat(state).isInstanceOf(Blocked.class);
        assertThat(((Blocked) state).reason).isEqualTo(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH);
        verify(mLocalCallback).onBlocked(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH, TEST_PKG);
        verify(mRemoteCallback).onBlocked(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH, TEST_PKG);
    }

    @Test
    public void updateLifecycle_blockReasonCannotChange() throws Exception {
        initializeCallbacksAndReset();

        var state = mLifecycle.updateLifecycleState((config) -> {
            config.mBlockingActivityPackage = TEST_PKG;
            config.mSecureWindowPackage = null;
        });

        assertThat(state).isInstanceOf(Blocked.class);
        assertThat(((Blocked) state).reason).isEqualTo(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH);
        verify(mLocalCallback).onBlocked(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH, TEST_PKG);
        verify(mRemoteCallback).onBlocked(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH, TEST_PKG);

        Mockito.reset(mLocalCallback, mRemoteCallback);
        state = mLifecycle.updateLifecycleState((config) -> {
            config.mBlockingActivityPackage = null;
            config.mSecureWindowPackage = TEST_PKG;
        });

        assertThat(state).isInstanceOf(Blocked.class);
        // The block reason does not change until the blocked state is exited.
        assertThat(((Blocked) state).reason).isEqualTo(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH);
        verify(mLocalCallback, never()).onBlocked(anyInt(), any());
        verify(mRemoteCallback, never()).onBlocked(anyInt(), any());
    }

    @Test
    public void updateLifecycle_transitionFromBlockedToActive() throws Exception {
        initializeCallbacksAndReset();

        var state = mLifecycle.updateLifecycleState((config) -> {
            config.mBlockingActivityPackage = TEST_PKG;
        });
        assertThat(state).isInstanceOf(Blocked.class);
        verify(mLocalCallback).onBlocked(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH, TEST_PKG);

        Mockito.reset(mLocalCallback, mRemoteCallback);
        state = mLifecycle.updateLifecycleState((config) -> {
            config.mBlockingActivityPackage = null;
        });

        assertThat(state).isInstanceOf(Blocked.class);
        verify(mLocalCallback, never()).onActive();
        verify(mRemoteCallback, never()).onActive();

        state = mLifecycle.exitBlockedState();
        assertThat(state).isInstanceOf(Active.class);
        verify(mLocalCallback).onActive();
        verify(mRemoteCallback).onActive();
    }

    @Test
    public void updateLifecycle_transitionFromBlockedToClosed() throws Exception {
        initializeCallbacksAndReset();

        var state = mLifecycle.updateLifecycleState((config) -> {
            config.mBlockingActivityPackage = TEST_PKG;
        });
        assertThat(state).isInstanceOf(Blocked.class);
        verify(mLocalCallback).onBlocked(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH, TEST_PKG);
        verify(mRemoteCallback).onBlocked(BLOCK_REASON_DISALLOWED_ACTIVITY_LAUNCH, TEST_PKG);

        Mockito.reset(mLocalCallback, mRemoteCallback);
        state = mLifecycle.updateLifecycleState((config) -> {
            config.mClosed = new Closed(CLOSE_REASON_CALLER_INITIATED);
        });

        assertThat(state).isInstanceOf(Closed.class);
        assertThat(((Closed) state).reason).isEqualTo(CLOSE_REASON_CALLER_INITIATED);
        verify(mLocalCallback).onClosed(CLOSE_REASON_CALLER_INITIATED);
        verify(mRemoteCallback).onClosed(CLOSE_REASON_CALLER_INITIATED);
    }

    @Test
    public void exitBlockedState_transitionsToActive() throws Exception {
        initializeCallbacksAndReset();

        mLifecycle.updateLifecycleState((config) -> config.mCallerInitiatedBlock = true);
        verify(mLocalCallback).onBlocked(BLOCK_REASON_CALLER_INITIATED, null);

        Mockito.reset(mLocalCallback, mRemoteCallback);
        final var state = mLifecycle.exitBlockedState();

        assertThat(state).isInstanceOf(Active.class);
        verify(mLocalCallback).onActive();
        verify(mRemoteCallback).onActive();
    }

    @Test
    public void exitBlockedState_transitionsToNextBlockedState() throws Exception {
        initializeCallbacksAndReset();

        mLifecycle.updateLifecycleState((config) -> {
            config.mCallerInitiatedBlock = true;
            config.mSecureWindowPackage = TEST_PKG;
        });
        verify(mLocalCallback).onBlocked(BLOCK_REASON_CALLER_INITIATED, null);

        Mockito.reset(mLocalCallback, mRemoteCallback);
        // Exiting the caller-initiated block should transition to the next highest priority block.
        final var state = mLifecycle.exitBlockedState();

        assertThat(state).isInstanceOf(Blocked.class);
        assertThat(((Blocked) state).reason).isEqualTo(BLOCK_REASON_SECURE_CONTENT);
        verify(mLocalCallback).onBlocked(BLOCK_REASON_SECURE_CONTENT, TEST_PKG);
        verify(mRemoteCallback).onBlocked(BLOCK_REASON_SECURE_CONTENT, TEST_PKG);
    }

    @Test
    public void initializeWithRemoteCallback_onActiveThrowsRemoteException_doesNotCrash()
            throws Exception {
        doThrow(new RemoteException()).when(mRemoteCallback).onActive();

        mLifecycle.initializeWithRemoteCallback(mRemoteCallback);

        verify(mLocalCallback).onActive();
        verify(mRemoteCallback).onActive();
    }

    @Test
    public void updateLifecycle_onBlockedThrowsRemoteException_doesNotCrash() throws Exception {
        initializeCallbacksAndReset();
        doThrow(new RemoteException()).when(mRemoteCallback).onBlocked(anyInt(), any());

        mLifecycle.updateLifecycleState(config -> config.mSecureWindowPackage = TEST_PKG);

        verify(mLocalCallback).onBlocked(BLOCK_REASON_SECURE_CONTENT, TEST_PKG);
        verify(mRemoteCallback).onBlocked(BLOCK_REASON_SECURE_CONTENT, TEST_PKG);
    }

    @Test
    public void updateLifecycle_onClosedThrowsRemoteException_doesNotCrash() throws Exception {
        initializeCallbacksAndReset();
        doThrow(new RemoteException()).when(mRemoteCallback).onClosed(anyInt());

        mLifecycle.updateLifecycleState(
                config -> config.mClosed = new Closed(CLOSE_REASON_CALLER_INITIATED));

        verify(mLocalCallback).onClosed(CLOSE_REASON_CALLER_INITIATED);
        verify(mRemoteCallback).onClosed(CLOSE_REASON_CALLER_INITIATED);
    }

    private void initializeCallbacksAndReset() {
        mLifecycle.initializeWithRemoteCallback(mRemoteCallback);
        Mockito.reset(mRemoteCallback, mLocalCallback);
    }
}
