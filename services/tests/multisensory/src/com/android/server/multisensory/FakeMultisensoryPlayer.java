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

package com.android.server.multisensory;

import android.media.AudioAttributes;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.multisensory.IMultisensoryPlayer;
import android.os.multisensory.IMultisensoryPlayerCapabilitiesCallback;
import android.os.multisensory.IMultisensoryPlayerLoadCallback;
import android.os.multisensory.IMultisensoryPlayerSessionCallback;
import android.os.multisensory.MultisensoryContinuousEffect;
import android.util.SparseArray;

import com.android.server.multisensory.playback.MultisensoryRemotePlayerScope;

public class FakeMultisensoryPlayer extends IMultisensoryPlayer.Stub {

    private final SparseArray<Integer> mTokenLoadStatus = new SparseArray<>();
    private final SparseArray<VibrationEffect> mVibrations = new SparseArray<>();
    private IMultisensoryPlayerLoadCallback mLoadCallback;
    private IBinder.DeathRecipient mDeathRecipient;
    private boolean mLinkToDeathCalled = false;
    private boolean mUnlinkToDeathCalled = false;
    private int mLastTokenPlayed = -1;
    private long mPlayerId = -1;
    private int mCancelCounter = 0;

    public FakeMultisensoryPlayer() {
        setUp();
    }

    /** Resets the fake player status. */
    public void setUp() {
        mLastTokenPlayed = -1;
        mPlayerId = -1;
        mCancelCounter = 0;
        mVibrations.clear();
        mDeathRecipient = null;
        mLinkToDeathCalled = false;
        mUnlinkToDeathCalled = false;
    }

    @Override
    public void linkToDeath(IBinder.DeathRecipient recipient, int flags) {
        mDeathRecipient = recipient;
        mLinkToDeathCalled = true;
    }

    @Override
    public boolean unlinkToDeath(IBinder.DeathRecipient recipient, int flags) {
        if (mDeathRecipient == recipient) {
            mDeathRecipient = null;
            mUnlinkToDeathCalled = true;
            return true;
        }
        return false;
    }

    /** Returns true if the player is currently linked to death. */
    public boolean isLinkedToDeath() {
        return mDeathRecipient != null;
    }

    /** Returns true if linkToDeath was ever called for this player. */
    public boolean wasLinkToDeathCalled() {
        return mLinkToDeathCalled;
    }

    /** Returns true if unlinkToDeath was ever called for this player. */
    public boolean wasUnlinkToDeathCalled() {
        return mUnlinkToDeathCalled;
    }

    /** Triggers the binder death for this player. */
    public void triggerBinderDied() {
        if (mDeathRecipient != null) {
            mDeathRecipient.binderDied();
        }
    }

    /** Sets the load result for a given multisensory token. */
    public void setLoadResultForToken(int tokenConstant, int loadResult) {
        mTokenLoadStatus.put(tokenConstant, loadResult);
    }

    public int getLastTokenPlayed() {
        return mLastTokenPlayed;
    }

    /** Returns true if the given token was the last one played by this player. */
    public boolean isTokenPlayed(int tokenConstant) {
        return mLastTokenPlayed == tokenConstant;
    }

    /** Clears the last token played status. */
    public void clearLastPlayed() {
        mLastTokenPlayed = -1;
    }

    public VibrationEffect getLastVibrationEffectPlayed() {
        return mVibrations.get(mLastTokenPlayed, null);
    }

    @Override
    public void setPlayerId(long playerId) throws RemoteException {
        mPlayerId = playerId;
    }

    public long getPlayerId() {
        return mPlayerId;
    }

    /** Returns true if the given token was requested to be loaded. */
    public boolean isTokenLoaded(int tokenConstant) {
        return mVibrations.get(tokenConstant) != null;
    }

    @Override
    public void load(
            int tokenConstant,
            VibrationEffect vibrationEffect,
            String audioEffect,
            IMultisensoryPlayerLoadCallback callback)
            throws RemoteException {
        if (mTokenLoadStatus.get(tokenConstant, MultisensoryRemotePlayerScope.LOAD_RESULT_UNKNOWN)
                == MultisensoryRemotePlayerScope.LOAD_RESULT_REMOTE_EXCEPTION) {
            throw new RemoteException();
        } else {
            mVibrations.put(tokenConstant, vibrationEffect);
            mLoadCallback = callback;
        }
    }

    /**
     * Flush-out all the load results for tokens that are currently held in the buffer, then clear
     * the buffer.
     */
    public void flushLoadResults() throws RemoteException {
        for (int i = 0; i < mVibrations.size(); i++) {
            int token = mVibrations.keyAt(i);
            int loadResult =
                    mTokenLoadStatus.get(token, MultisensoryRemotePlayerScope.LOAD_RESULT_UNKNOWN);
            if (loadResult != MultisensoryRemotePlayerScope.LOAD_RESULT_REMOTE_EXCEPTION
                    && loadResult != MultisensoryRemotePlayerScope.LOAD_RESULT_UNKNOWN) {
                mLoadCallback.onLoadComplete(mPlayerId, token, loadResult);
            }
        }
    }

    public int getTimesCancelled() {
        return mCancelCounter;
    }

    @Override
    public void play(
            int tokenConstant,
            VibrationAttributes vibrationAttributes,
            AudioAttributes audioAttributes)
            throws RemoteException {
        if (mTokenLoadStatus.get(tokenConstant, MultisensoryRemotePlayerScope.LOAD_RESULT_UNKNOWN)
                == MultisensoryRemotePlayerScope.LOAD_RESULT_REMOTE_EXCEPTION) {
            throw new RemoteException();
        } else {
            mLastTokenPlayed = tokenConstant;
        }
    }

    @Override
    public void cancel() throws RemoteException {
        mCancelCounter++;
    }

    @Override
    public void getCapabilities(IMultisensoryPlayerCapabilitiesCallback callback)
            throws RemoteException {}

    @Override
    public void openRealtimeSession(
            int tokenConstant,
            MultisensoryContinuousEffect baseEffect,
            VibrationAttributes vibrationAttributes,
            AudioAttributes audioAttributes,
            IMultisensoryPlayerSessionCallback callback)
            throws RemoteException {}
}
