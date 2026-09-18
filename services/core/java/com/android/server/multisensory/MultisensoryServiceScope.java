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

import static android.os.Trace.TRACE_TAG_VIBRATOR;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresNoPermission;
import android.content.ContentResolver;
import android.media.AudioAttributes;
import android.os.Trace;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.multisensory.IMultisensoryPlayer;
import android.os.multisensory.IMultisensoryPlayerLoadCallback;
import android.os.multisensory.MultisensoryManager;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.RingBuffer;
import com.android.server.multisensory.logging.MultisensoryPlaybackRecord;
import com.android.server.multisensory.playback.MultisensoryPlayerDefault;
import com.android.server.multisensory.playback.MultisensoryRemotePlayerScope;
import com.android.server.multisensory.repository.MultisensoryRepository;

/**
 * The scope under which the {@link MultisensoryService} operates. It serves as an environment that
 * contains registered {@link IMultisensoryPlayer}s and any shared environment data for the
 * operation of the service.
 *
 * @hide
 */
public class MultisensoryServiceScope {

    private static final String TAG = "MultisensoryServiceScope";

    public static final VibrationAttributes sTouchVibrationAttributes =
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH);
    public static final VibrationAttributes sHardwareVibrationAttributes =
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK);
    private final AudioAttributes mSonificationAudioAttributes =
            (new AudioAttributes.Builder())
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final RingBuffer<MultisensoryPlaybackRecord> mRecords =
            new RingBuffer<>(() -> null, MultisensoryPlaybackRecord[]::new, 100);

    @GuardedBy("mLock")
    private final MultisensoryPlayerDefault mDefaultPlayer;

    @GuardedBy("mLock")
    private final MultisensoryRemotePlayerScope mRemotePlayerScope;

    private final MultisensoryRepository mRepository;

    private final IMultisensoryPlayerLoadCallback mRemoteLoadCallback =
            new IMultisensoryPlayerLoadCallback.Stub() {
                @Override
                @RequiresNoPermission
                public void onLoadComplete(long playerId, int tokenConstant, int loadResult) {
                    Trace.traceBegin(TRACE_TAG_VIBRATOR, "MultisensoryServiceScope#onLoad");
                    try {
                        synchronized (mLock) {
                            mRemotePlayerScope.setLoadResultForToken(
                                    playerId, tokenConstant, loadResult);
                        }
                    } finally {
                        Trace.traceEnd(TRACE_TAG_VIBRATOR);
                    }
                }
            };

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    MultisensoryServiceScope(
            @NonNull MultisensoryRepository repository,
            @NonNull MultisensoryPlayerDefault defaultPlayer,
            @NonNull Vibrator deviceVibrator,
            @NonNull ContentResolver contentResolver) {
        mRepository = repository;
        mDefaultPlayer = defaultPlayer;
        mRemotePlayerScope = new MultisensoryRemotePlayerScope(this::onRemotePlayerDropped);
        mRepository.initialize(deviceVibrator, contentResolver);
    }

    private void onRemotePlayerDropped(long droppedPlayerId) {
        synchronized (mLock) {
            if (droppedPlayerId == mRemotePlayerScope.getCurrentPlayerId()) {
                mRemotePlayerScope.clear();
                Slog.d(TAG, "The remote player with id " + droppedPlayerId + " has dropped");
            } else {
                Slog.d(TAG, "Ignoring stale drop notification for player " + droppedPlayerId);
            }
        }
    }

    /**
     * Check if a remote player has been registered.
     *
     * @return If a remote {@link IMultisensoryPlayer} is present and its {@link
     *     MultisensoryRemotePlayerScope} is not null.
     */
    public boolean isRemotePlayerRegistered() {
        synchronized (mLock) {
            return mRemotePlayerScope.isRemotePlayerConfigured();
        }
    }

    /**
     * Register a remote player in the {@link MultisensoryService}. A new player will override any
     * existing player already registered in this scope. When registered, the player will be called
     * to load necessary resources for all the tokens defined in the {@link MultisensoryRepository}
     *
     * @param player The new remote player to register.
     * @return true if the registration was successful, false otherwise.
     */
    public boolean registerRemotePlayer(IMultisensoryPlayer player) {
        long remotePlayerId;
        synchronized (mLock) {
            mDefaultPlayer.cancel();
            remotePlayerId = mRemotePlayerScope.configureForNewRemotePlayer(player);
        }

        if (remotePlayerId != MultisensoryRemotePlayerScope.INVALID_PLAYER_ID) {
            loadTokensInRemotePlayer(remotePlayerId);
        } else {
            Slog.d(TAG, "No tokens were loaded in remote player because its registration failed");
            return false;
        }
        return true;
    }

    private void loadTokensInRemotePlayer(long remotePlayerId) {
        int[] tokens = MultisensoryManager.getMultisensoryTokens();
        for (int tokenConstant : tokens) {
            VibrationEffect singleEffect =
                    mRepository.getHapticEffect(tokenConstant).createSingleVibrationEffect();
            String audioEffect = mRepository.getSoundEffect(tokenConstant);

            if (singleEffect == null) continue;

            synchronized (mLock) {
                boolean loadSuccessful =
                        mRemotePlayerScope.load(
                                tokenConstant, singleEffect, audioEffect, mRemoteLoadCallback);
                if (!loadSuccessful) {
                    mRemotePlayerScope.setLoadResultForToken(
                            remotePlayerId,
                            tokenConstant,
                            MultisensoryRemotePlayerScope.LOAD_RESULT_REMOTE_EXCEPTION);
                }
            }
        }
    }

    /**
     * Play audio-haptic feedback for a given {@link MultisensoryManager}. This method is
     * synchronized.
     *
     * <p>If a remote {@link IMultisensoryPlayer} has been registered, playback is delegated to the
     * remote player if the player successfully loaded the token resources when it was registered.
     * In any other case, playback results in haptics-only feedback via the {@link
     * MultisensoryPlayerDefault}.
     *
     * @param tokenConstant Token to play. One of {@link MultisensoryManager}.
     */
    public void playToken(@MultisensoryManager.Token int tokenConstant) {
        VibrationAttributes vibrationAttributes = getVibrationAttributesForToken(tokenConstant);
        AudioAttributes audioAttributes = getAudioAttributesForToken(tokenConstant);

        MultisensoryPlaybackRecord.PlayerType playerUsed = null;
        @MultisensoryRemotePlayerScope.LoadResult int remoteLoadResult;

        synchronized (mLock) {
            remoteLoadResult = mRemotePlayerScope.getLoadResultForToken(tokenConstant);
        }

        long playbackTimeStamp = System.currentTimeMillis();
        if (remoteLoadResult == MultisensoryRemotePlayerScope.LOAD_RESULT_SUCCESS) {
            boolean playedInRemote =
                    mRemotePlayerScope.play(tokenConstant, vibrationAttributes, audioAttributes);
            if (playedInRemote) {
                playerUsed = MultisensoryPlaybackRecord.PlayerType.REMOTE;
            }
        }

        if (playerUsed == null) {
            playerUsed = MultisensoryPlaybackRecord.PlayerType.DEFAULT;
            playTokenInDefaultPlayer(tokenConstant, vibrationAttributes);
        }

        synchronized (mLock) {
            MultisensoryPlaybackRecord record =
                    new MultisensoryPlaybackRecord(tokenConstant, playerUsed, playbackTimeStamp);
            mRecords.append(record);
        }
    }

    /**
     * Get the {@link VibrationAttributes} to play haptics for a given token constant.
     *
     * @param tokenConstant One of {@link MultisensoryManager}
     * @return the {@link VibrationAttributes} for the token.
     */
    public @NonNull VibrationAttributes getVibrationAttributesForToken(
            @MultisensoryManager.Token int tokenConstant) {
        return switch (tokenConstant) {
            case MultisensoryManager.TOKEN_UNLOCK, MultisensoryManager.TOKEN_LOCK ->
                    sHardwareVibrationAttributes;
            default -> sTouchVibrationAttributes;
        };
    }

    /**
     * Get the {@link AudioAttributes} to play audio for a given token constant.
     *
     * @param tokenConstant One of {@link MultisensoryManager}
     * @return the {@link AudioAttributes} for the token, or null if the token did not specify any
     *     audio effect to begin with.
     */
    public @Nullable AudioAttributes getAudioAttributesForToken(
            @MultisensoryManager.Token int tokenConstant) {
        String audioEffect = mRepository.getSoundEffect(tokenConstant);
        return audioEffect != null ? mSonificationAudioAttributes : null;
    }

    private void playTokenInDefaultPlayer(
            @MultisensoryManager.Token int tokenConstant,
            @NonNull VibrationAttributes vibrationAttributes) {
        VibrationEffect vibrationEffect =
                mRepository.getHapticEffect(tokenConstant).createSingleVibrationEffect();
        if (vibrationEffect != null) {
            mDefaultPlayer.play(tokenConstant, vibrationEffect, vibrationAttributes);
        }
    }

    /** Returns the list of playback records. */
    @VisibleForTesting
    MultisensoryPlaybackRecord[] getPlaybackRecords() {
        return mRecords.toArray();
    }
}
