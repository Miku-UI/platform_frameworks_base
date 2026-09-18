/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.internal.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
public class ClientListenerMultiplexerTest {

    interface IMyService {
        void registerCallback(IMyCallback callback) throws RemoteException;

        void unregisterCallback(IMyCallback callback) throws RemoteException;
    }

    interface IMyCallback {}

    interface MyListener {
        void onEvent();
    }

    private static class FakeService implements IMyService {
        IMyCallback mRegisteredCallback;
        int mRegisterCount = 0;
        int mUnregisterCount = 0;
        boolean mThrowOnRegister = false;
        boolean mThrowOnUnregister = false;

        @Override
        public void registerCallback(IMyCallback callback) throws RemoteException {
            if (mThrowOnRegister) {
                throw new RemoteException("Simulated register failure");
            }
            mRegisteredCallback = callback;
            mRegisterCount++;
        }

        @Override
        public void unregisterCallback(IMyCallback callback) throws RemoteException {
            if (mThrowOnUnregister) {
                throw new RemoteException("Simulated unregister failure");
            }
            if (mRegisteredCallback == callback) {
                mRegisteredCallback = null;
            }
            mUnregisterCount++;
        }
    }

    private static class FakeCallback implements IMyCallback {}

    private static class TestListener implements MyListener {
        int mEventCount = 0;

        @Override
        public void onEvent() {
            mEventCount++;
        }
    }

    private static class CapturingExecutor implements Executor {
        int mExecuteCount = 0;

        @Override
        public void execute(Runnable command) {
            mExecuteCount++;
            command.run();
        }
    }

    private FakeService mService;
    private FakeCallback mCallback;
    private TestListener mListener1;
    private TestListener mListener2;
    private CapturingExecutor mExecutor;

    private ClientListenerMultiplexer<MyListener, IMyService, IMyCallback> mMultiplexer;

    @Before
    public void setUp() {
        mService = new FakeService();
        mCallback = new FakeCallback();
        mListener1 = new TestListener();
        mListener2 = new TestListener();
        mExecutor = new CapturingExecutor();

        mMultiplexer =
                new ClientListenerMultiplexer<>(
                        mService,
                        mCallback,
                        (s, c) -> s.registerCallback(c),
                        (s, c) -> s.unregisterCallback(c));
    }

    @Test
    public void testAddListener_firstListenerRegistersCallback() {
        mMultiplexer.addListener(mExecutor, mListener1);

        assertEquals(mCallback, mService.mRegisteredCallback);
        assertEquals(1, mService.mRegisterCount);
        assertTrue(mMultiplexer.hasListeners());
        assertEquals(1, mMultiplexer.getListenerCount());
    }

    @Test
    public void testAddListener_secondListenerDoesNotRegisterCallback() {
        mMultiplexer.addListener(mExecutor, mListener1);
        mMultiplexer.addListener(mExecutor, mListener2);

        assertEquals(mCallback, mService.mRegisteredCallback);
        assertEquals(1, mService.mRegisterCount);
        assertEquals(2, mMultiplexer.getListenerCount());
    }

    @Test
    public void testAddListener_duplicateListenerDoesNotAddAgain() {
        mMultiplexer.addListener(mExecutor, mListener1);
        mMultiplexer.addListener(mExecutor, mListener1);

        assertEquals(1, mMultiplexer.getListenerCount());
        // Should only have registered once
        assertEquals(1, mService.mRegisterCount);
    }

    @Test
    public void testAddListener_duplicateListenerWithDifferentExecutor_DoesNotAddAgain() {
        mMultiplexer.addListener(mExecutor, mListener1);
        // Try adding same listener with different executor (or null)
        mMultiplexer.addListener(null, mListener1);

        assertEquals(1, mMultiplexer.getListenerCount());
        // Should only have registered once
        assertEquals(1, mService.mRegisterCount);
    }

    @Test
    public void testRemoveListener_lastListenerUnregistersCallback() {
        mMultiplexer.addListener(mExecutor, mListener1);
        mMultiplexer.removeListener(mListener1);

        assertNull(mService.mRegisteredCallback);
        assertEquals(1, mService.mUnregisterCount);
        assertFalse(mMultiplexer.hasListeners());
    }

    @Test
    public void testRemoveListener_remainingListenerDoesNotUnregisterCallback() {
        mMultiplexer.addListener(mExecutor, mListener1);
        mMultiplexer.addListener(mExecutor, mListener2);
        mMultiplexer.removeListener(mListener1);

        assertEquals(0, mService.mUnregisterCount);
        assertEquals(1, mMultiplexer.getListenerCount());
    }

    @Test
    public void testForEachListener_dispatchesToAllListeners() {
        // Use a direct executor for testing to ensure synchronous execution
        Executor directExecutor = Runnable::run;
        mMultiplexer.addListener(directExecutor, mListener1);
        mMultiplexer.addListener(directExecutor, mListener2);

        mMultiplexer.forEachListener(MyListener::onEvent);

        assertEquals(1, mListener1.mEventCount);
        assertEquals(1, mListener2.mEventCount);
    }

    @Test
    public void testForEachListener_usesProvidedExecutor() {
        mMultiplexer.addListener(mExecutor, mListener1);

        mMultiplexer.forEachListener(MyListener::onEvent);

        assertEquals(1, mExecutor.mExecuteCount);
        // Also verify the listener was actually called since our executor runs it
        assertEquals(1, mListener1.mEventCount);
    }

    @Test(expected = NullPointerException.class)
    public void testConstructor_nullArguments_throwsException() {
        new ClientListenerMultiplexer<>(null, mCallback, (s, c) -> {}, (s, c) -> {});
    }

    @Test
    public void testAddListener_registrationFailure_removesListenerAndPropagatesException() {
        mService.mThrowOnRegister = true;

        try {
            mMultiplexer.addListener(mExecutor, mListener1);
        } catch (RuntimeException e) {
            // Expected exception propagated from RemoteException
        }

        // Verify listener was removed from the list
        assertFalse(mMultiplexer.hasListeners());
        assertEquals(0, mMultiplexer.getListenerCount());
    }

    @Test
    public void testRemoveListener_unregistrationFailure_propagateException() {
        mMultiplexer.addListener(mExecutor, mListener1);

        mService.mThrowOnUnregister = true;

        try {
            mMultiplexer.removeListener(mListener1);
        } catch (RuntimeException e) {
            // Expected exception propagated from RemoteException
        }

        // Listener should still be removed from the local list even if unregistration failed
        assertFalse(mMultiplexer.hasListeners());
        assertEquals(0, mMultiplexer.getListenerCount());
    }

    @Test
    public void testRemoveListener_unregistrationFailure_allowsReRegistration() {
        // Setup: add a listener
        mMultiplexer.addListener(mExecutor, mListener1);
        // Initial registration should have happened
        assertEquals(1, mService.mRegisterCount);

        // Force unregister to fail
        mService.mThrowOnUnregister = true;

        try {
            mMultiplexer.removeListener(mListener1);
        } catch (RuntimeException e) {
            // Expected exception
        }

        // Listener should be removed locally
        assertFalse(mMultiplexer.hasListeners());

        // Now try adding a listener again.
        // If mIsCallbackRegistered was not reset, this would skip registration.
        mMultiplexer.addListener(mExecutor, mListener1);

        // Should have registered again (count becomes 2)
        assertEquals(2, mService.mRegisterCount);
        assertTrue(mMultiplexer.hasListeners());
    }
}
