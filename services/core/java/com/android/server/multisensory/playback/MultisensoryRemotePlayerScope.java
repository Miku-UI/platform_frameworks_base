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

package com.android.server.multisensory.playback;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.media.AudioAttributes;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.multisensory.IMultisensoryPlayer;
import android.os.multisensory.IMultisensoryPlayerLoadCallback;
import android.os.multisensory.MultisensoryManager;
import android.util.ArraySet;
import android.util.Slog;
import android.util.SparseIntArray;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The scope of a remote {@link android.os.multisensory.IMultisensoryPlayer} encapsulating its state
 * and managing its remote {@link android.os.Binder} connection
 *
 * <p>This class is not thread safe and its usage must be guarded by the caller.
 *
 * @hide
 */
public final class MultisensoryRemotePlayerScope {

    private static final String TAG = "MultisensoryRemotePlayerScope";

    /** @hide */
    @IntDef(
            prefix = {"LOAD_RESULT_"},
            value = {
                LOAD_RESULT_SUCCESS,
                LOAD_RESULT_UNKNOWN,
                LOAD_RESULT_LOADING,
                LOAD_RESULT_UNKNOWN_ERROR,
                LOAD_RESULT_AUDIO_NOT_FOUND,
                LOAD_RESULT_REMOTE_EXCEPTION,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface LoadResult {}

    public static final int LOAD_RESULT_SUCCESS =
            IMultisensoryPlayerLoadCallback.LOAD_RESULT_SUCCESS;
    public static final int LOAD_RESULT_UNKNOWN =
            IMultisensoryPlayerLoadCallback.LOAD_RESULT_UNKNOWN;
    public static final int LOAD_RESULT_LOADING =
            IMultisensoryPlayerLoadCallback.LOAD_RESULT_LOADING;
    public static final int LOAD_RESULT_UNKNOWN_ERROR =
            IMultisensoryPlayerLoadCallback.LOAD_RESULT_UNKNOWN_ERROR;
    public static final int LOAD_RESULT_AUDIO_NOT_FOUND =
            IMultisensoryPlayerLoadCallback.LOAD_RESULT_AUDIO_NOT_FOUND;
    public static final int LOAD_RESULT_REMOTE_EXCEPTION =
            IMultisensoryPlayerLoadCallback.LOAD_RESULT_REMOTE_EXCEPTION;

    public static final long INVALID_PLAYER_ID = -1;

    private final SparseIntArray mTokenLoadResults = new SparseIntArray();
    private final ArraySet<Integer> mCapabilities = new ArraySet<>();
    private final AtomicLong mNextPlayerId = new AtomicLong(0);
    private final RemotePlayerListener mRemotePlayerListener;

    private long mCurrentPlayerId;
    private IMultisensoryPlayer mRemotePlayer;
    private IBinder.DeathRecipient mCurrentDeathRecipient;

    public MultisensoryRemotePlayerScope(@NonNull RemotePlayerListener remotePlayerListener) {
        mRemotePlayerListener = remotePlayerListener;
    }

    /** Sets the load result for a given multisensory token. */
    public void setLoadResultForToken(
            long playerId,
            @MultisensoryManager.Token int tokenConstant,
            @LoadResult int loadResult) {
        if (playerId == mCurrentPlayerId) {
            mTokenLoadResults.put(tokenConstant, loadResult);
        } else {
            Slog.d(
                    TAG,
                    "load result for token "
                            + tokenConstant
                            + " ignored because the current remote player ID ("
                            + mCurrentPlayerId
                            + ") disagrees with the ID from the calling player ("
                            + playerId
                            + ")");
        }
    }

    /** Returns the load result for a given multisensory token. */
    public @LoadResult int getLoadResultForToken(@MultisensoryManager.Token int tokenConstant) {
        return mTokenLoadResults.get(tokenConstant, LOAD_RESULT_UNKNOWN);
    }

    /** Get the ID of the current remote player that this status represents */
    public long getCurrentPlayerId() {
        return mCurrentPlayerId;
    }

    /** Return whether the remote player has been configured */
    public boolean isRemotePlayerConfigured() {
        return mRemotePlayer != null;
    }

    /**
     * Play audio-haptic feedback in the remote player that this scope maintains.
     *
     * <p>If the remote player is alive and non-null, it will be used to deliver the feedback. If
     * the registered player is dropped, the installed {@link RemotePlayerListener} will be
     * notified. This method must be synchronized by the caller.
     *
     * @param tokenConstant Token to play. One of {@link MultisensoryManager}.
     * @param vibrationAttributes Attributes for the haptics part of the feedback.
     * @param audioAttributes Attributes for the audio part of the feedback.
     * @return true if the registered play was used to play the feedback, false in case of failure.
     */
    public boolean play(
            @MultisensoryManager.Token int tokenConstant,
            @NonNull VibrationAttributes vibrationAttributes,
            @Nullable AudioAttributes audioAttributes) {
        try {
            if (isRemotePlayerConfigured()) {
                mRemotePlayer.play(tokenConstant, vibrationAttributes, audioAttributes);
                return true;
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Error playing token " + tokenConstant + " in remote player", e);
            if (e instanceof DeadObjectException) {
                // This call should trigger clear() and reset mRemotePlayer.
                mRemotePlayerListener.onRemotePlayerDropped(mCurrentPlayerId);
            }
        }
        return false;
    }

    /**
     * Load resources for a token with associated vibration and audio effects in the remote player
     * that this scope maintains.
     *
     * <p>If the remote player is alive and non-null, it will be used to load the resources. If the
     * registered player is dropped, the installed {@link RemotePlayerListener} will be notified.
     * This method must be synchronized by the caller.
     *
     * @param tokenConstant Token to load. One of {@link MultisensoryManager}.
     * @param vibrationEffect The haptic effect for the token.
     * @param audioEffect The audio effect for the token.
     * @param loadCallback The {@link IMultisensoryPlayerLoadCallback} for the remote player to
     *     report the results.
     * @return true if the load operation was successful, false otherwise.
     */
    public boolean load(
            @MultisensoryManager.Token int tokenConstant,
            VibrationEffect vibrationEffect,
            String audioEffect,
            IMultisensoryPlayerLoadCallback loadCallback) {
        try {
            if (isRemotePlayerConfigured()) {
                mRemotePlayer.load(tokenConstant, vibrationEffect, audioEffect, loadCallback);
                return true;
            }
        } catch (RemoteException e) {
            Slog.e(TAG, "Error loading token " + tokenConstant + " in remote player", e);
            if (e instanceof DeadObjectException) {
                // This call should trigger clear() and reset mRemotePlayer.
                mRemotePlayerListener.onRemotePlayerDropped(mCurrentPlayerId);
            }
        }
        return false;
    }

    /** Clears all status variables. */
    public void clear() {
        // Cleanup a previous player
        if (mRemotePlayer != null) {
            try {
                mRemotePlayer.cancel();
            } catch (RemoteException re) {
                Slog.e(
                        TAG,
                        "Failed to cancel() vibrations on the remote player while clearing its"
                                + " scope",
                        re);
            }
        }

        if (mCurrentDeathRecipient != null && mRemotePlayer != null) {
            try {
                mRemotePlayer.asBinder().unlinkToDeath(mCurrentDeathRecipient, 0);
            } catch (NoSuchElementException nse) {
                Slog.e(TAG, "Failed to unlinkToDeath the previous remote player", nse);
            }
        }

        mRemotePlayer = null;
        mCurrentDeathRecipient = null;
        mTokenLoadResults.clear();
        mCapabilities.clear();
    }

    /**
     * Configure the scope for a new remote player.
     *
     * @return the ID of the new player, or {@value INVALID_PLAYER_ID} if the configuration fails.
     */
    public long configureForNewRemotePlayer(IMultisensoryPlayer newRemotePlayer) {
        clear();
        mRemotePlayer = newRemotePlayer;
        mCurrentPlayerId = mNextPlayerId.getAndIncrement();

        try {
            final long playerId = mCurrentPlayerId;
            mRemotePlayer.setPlayerId(playerId);
            IBinder.DeathRecipient deathRecipient =
                    () -> {
                        mRemotePlayerListener.onRemotePlayerDropped(playerId);
                    };
            mRemotePlayer.asBinder().linkToDeath(deathRecipient, 0);
            mCurrentDeathRecipient = deathRecipient;
        } catch (RemoteException ren) {
            Slog.e(TAG, "Failed to register the new player due to remote exception", ren);
            return INVALID_PLAYER_ID;
        }
        return mCurrentPlayerId;
    }

    public interface RemotePlayerListener {

        /** Notify that the current remote player with the given ID has been dropped */
        void onRemotePlayerDropped(long droppedPlayerId);
    }
}
