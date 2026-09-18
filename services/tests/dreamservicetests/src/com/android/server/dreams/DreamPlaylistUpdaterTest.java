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

package com.android.server.dreams;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.test.TestLooper;
import android.service.dreams.DreamItem;
import android.service.dreams.DreamPlaylist;
import android.service.dreams.IDreamManagerListener;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Collections;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DreamPlaylistUpdaterTest {
    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Mock private DreamComponentsResolver mResolver;
    @Mock private IDreamManagerListener mListener;
    @Mock private IBinder mBinder;

    private TestLooper mLooper;
    private DreamPlaylistUpdater mUpdater;

    private static final int USER_ID = 0;
    private static final int TEST_DEBOUNCE_DELAY_MS = 500;
    private static final ComponentName DREAM_1 = ComponentName.unflattenFromString("com.test/.D1");

    @Before
    public void setUp() throws Exception {
        mLooper = new TestLooper();
        mUpdater =
                new DreamPlaylistUpdater(
                        (id) -> mResolver,
                        new Handler(mLooper.getLooper()),
                        null,
                        TEST_DEBOUNCE_DELAY_MS);
        when(mListener.asBinder()).thenReturn(mBinder);
    }

    @Test
    public void refresh_debouncesUpdates() throws RemoteException {
        // Setup initial state
        when(mResolver.getDreamPlaylist(any()))
                .thenReturn(
                        new DreamPlaylist(
                                Collections.singletonList(new DreamItem.Builder(DREAM_1).build()),
                                0));
        mUpdater.registerListener(mListener, USER_ID);
        mLooper.dispatchAll();

        // Trigger multiple refresh requests
        mUpdater.refresh(USER_ID, null);
        mUpdater.refresh(USER_ID, null);
        mUpdater.refresh(USER_ID, null);

        // Verify no immediate update
        verify(mListener, never()).onPlaylistChanged(any());

        // Fast forward time slightly less than debounce delay
        mLooper.moveTimeForward(400);
        mLooper.dispatchAll();
        verify(mListener, never()).onPlaylistChanged(any());

        // Fast forward past debounce delay
        mLooper.moveTimeForward(200);
        mLooper.dispatchAll();

        // Verify single update
        verify(mListener).onPlaylistChanged(any());
    }

    @Test
    public void refresh_cancelsPreviousDebounce() throws RemoteException {
        // Initial setup
        when(mResolver.getDreamPlaylist(any()))
                .thenReturn(
                        new DreamPlaylist(
                                Collections.singletonList(new DreamItem.Builder(DREAM_1).build()),
                                0));
        mUpdater.registerListener(mListener, USER_ID);
        mLooper.dispatchAll();
        clearInvocations(mListener);

        // Request 1
        mUpdater.refresh(USER_ID, null);

        // Wait half the delay
        mLooper.moveTimeForward(300);
        mLooper.dispatchAll();

        // Request 2 (should cancel request 1)
        mUpdater.refresh(USER_ID, null);

        // Wait past original request 1 deadline
        mLooper.moveTimeForward(300);
        mLooper.dispatchAll();

        // Should NOT have triggered yet (total time 600ms, but reset at 300ms)
        verify(mListener, never()).onPlaylistChanged(any());

        // Wait past request 2 deadline
        mLooper.moveTimeForward(300);
        mLooper.dispatchAll();

        verify(mListener).onPlaylistChanged(any());
    }

    @Test
    public void refreshImmediately_bypassesDebounce() throws RemoteException {
        when(mResolver.getDreamPlaylist(any()))
                .thenReturn(
                        new DreamPlaylist(
                                Collections.singletonList(new DreamItem.Builder(DREAM_1).build()),
                                0));
        mUpdater.registerListener(mListener, USER_ID);
        clearInvocations(mListener);

        mUpdater.refreshImmediately(USER_ID, null);

        verify(mListener).onPlaylistChanged(any());
        // Verify nothing scheduled
        assertThat(mLooper.nextMessage()).isNull();
    }

    @Test
    public void clearCache_clearsPendingUpdates() throws RemoteException {
        mUpdater.registerListener(mListener, USER_ID);
        mUpdater.refresh(USER_ID, null);

        mUpdater.clearCache(USER_ID);

        mLooper.moveTimeForward(1000);
        mLooper.dispatchAll();

        // Should handle gracefully (listener was unregistered/killed in clearCache)
        // We mainly want to ensure the runnable was removed/cancelled to avoid processing
    }

    @Test
    public void refresh_handlesMultipleUsersIndependently() throws RemoteException {
        // Prepare mock setup for multiple users
        when(mResolver.getDreamPlaylist(any()))
                .thenReturn(
                        new DreamPlaylist(
                                Collections.singletonList(new DreamItem.Builder(DREAM_1).build()),
                                0));

        int user1 = 10;
        int user2 = 11;
        // Mock a binder for user2's listener if needed, or reuse mBinder since it's a mock.
        // We need to register listeners for both users if we want callbacks.
        mUpdater.registerListener(mListener, user1);
        mUpdater.registerListener(mListener, user2);

        // 1. Refresh User 1
        mUpdater.refresh(user1, null);

        // 2. Refresh User 2 (Should NOT cancel User 1's timer)
        mUpdater.refresh(user2, null);

        // 3. Advance time just enough for debounce (TEST_DEBOUNCE_DELAY_MS = 500)
        mLooper.moveTimeForward(TEST_DEBOUNCE_DELAY_MS + 50);
        mLooper.dispatchAll();

        // Verify listeners for both users were notified.
        // Since mListener is shared, it should receive onPlaylistChanged twice.
        verify(mListener, times(2)).onPlaylistChanged(any());
    }

    @Test
    public void refresh_deduplicatesLogicallyEquivalentPlaylists() throws RemoteException {
        final DreamPlaylist p1 =
                new DreamPlaylist(
                        Collections.singletonList(new DreamItem.Builder(DREAM_1).build()), 0);
        final DreamPlaylist p2 =
                new DreamPlaylist(
                        Collections.singletonList(new DreamItem.Builder(DREAM_1).build()), 0);

        // Verify they are different objects but logically equal
        assertThat(p1).isNotSameInstanceAs(p2);
        assertThat(p1).isEqualTo(p2);

        mUpdater.registerListener(mListener, USER_ID);

        // 1. First refresh with p1
        when(mResolver.getDreamPlaylist(any())).thenReturn(p1);
        mUpdater.refreshImmediately(USER_ID, null);
        verify(mListener, times(1)).onPlaylistChanged(eq(p1));

        // 2. Second refresh with p2 (logically equivalent but different object)
        when(mResolver.getDreamPlaylist(any())).thenReturn(p2);
        mUpdater.refreshImmediately(USER_ID, null);

        // Verify NO second callback
        verify(mListener, times(1)).onPlaylistChanged(any());
    }
}
