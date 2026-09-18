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

package com.android.server.audio;

import static android.media.audio.Flags.tvVolumeKeyBatchingDuringLongPress;

import android.media.AudioDeviceVolumeManager;
import android.media.AudioManager;

/**
 * Contains logic for batching non-CEC volume adjustments during long presses on TV.
 *
 * This class is designed to receive all incoming volume keys via processVolumeKey().
 * The method returns an object that prescribes whether to adjust volume, and how much to
 * scale the volume adjustment by.
 */
public class TvVolumeKeyBatchingState {

    // Configuration parameter, initialized in constructor
    private final int mKeysPerAdjustment;

    // Internal state
    private boolean mBatchingStarted = false;
    private int mNumVolumeAdjustmentsBatched = 0;

    private int mPreviousDirection = NO_PREVIOUS_DIRECTION;

    private static final int NO_PREVIOUS_DIRECTION = 999;

    private static final VolumeAdjustmentModifiers NO_VOLUME_ADJUSTMENT =
            new VolumeAdjustmentModifiers(0.0f);
    private static final VolumeAdjustmentModifiers REGULAR_VOLUME_ADJUSTMENT =
            new VolumeAdjustmentModifiers(1.0f);
    private final VolumeAdjustmentModifiers mScaledVolumeAdjustment; // Initialized in constructor

    public TvVolumeKeyBatchingState(int keysPerAdjustment, float adjustmentScaleFactor) {
        mKeysPerAdjustment = keysPerAdjustment;
        mScaledVolumeAdjustment = new VolumeAdjustmentModifiers(adjustmentScaleFactor);
    }

    /**
     * Returned after processing an incoming volume key. Determines whether to perform a volume
     * adjustment for that key, and how much to scale that volume adjustment by.
     */
    public static class VolumeAdjustmentModifiers {
        private final float mScaleFactor;
        private VolumeAdjustmentModifiers(float scaleFactor) {
            this.mScaleFactor = scaleFactor;
        }

        /**
         * Whether to make a volume adjustment.
         */
        public boolean shouldAdjustVolume() {
            return this.mScaleFactor != 0.0f;
        }

        /**
         * Scale factor for the volume adjustment.
         */
        public float getScaleFactor() {
            return mScaleFactor;
        }
    }

    /**
     * Process an incoming volume key and update internal state.
     *
     * @param direction E.g. AudioManager.ADJUST_RAISE, ADJUST_LOWER
     * @param keyEventMode E.g. AudioDeviceVolumeManager.ADJUST_MODE_START, ADJUST_MODE_END
     * @return information on volume adjustment (if any) to be applied
     */
    public synchronized VolumeAdjustmentModifiers processVolumeKey(
            int direction, int keyEventMode) {
        if (!tvVolumeKeyBatchingDuringLongPress()) {
            return REGULAR_VOLUME_ADJUSTMENT;
        }

        // Batching only applies when there are multiple consecutive keys with the same direction
        if (direction != mPreviousDirection) {
            mBatchingStarted = false;
        }
        mPreviousDirection = direction;

        // Batching only applies to volume up/down - i.e. ADJUST_RAISE or ADJUST_LOWER
        if (direction == AudioManager.ADJUST_LOWER || direction == AudioManager.ADJUST_RAISE) {
            return processBatchableVolumeKey(keyEventMode);
        }

        return REGULAR_VOLUME_ADJUSTMENT;
    }

    private VolumeAdjustmentModifiers processBatchableVolumeKey(int keyEventMode) {
        if (keyEventMode == AudioDeviceVolumeManager.ADJUST_MODE_START) {
            // ADJUST_MODE_START: could be part of a short press or long press
            if (mBatchingStarted) {
                // Not the first volume adjustment in the long press
                mNumVolumeAdjustmentsBatched++;
                if (mNumVolumeAdjustmentsBatched == mKeysPerAdjustment) {
                    // Batched enough adjustments. Apply scaled volume adjustment
                    mNumVolumeAdjustmentsBatched = 0;
                    return mScaledVolumeAdjustment;
                } else {
                    // Haven't batched enough adjustments. Skip this one
                    return NO_VOLUME_ADJUSTMENT;
                }
            } else {
                // First volume adjustment in the long press
                mBatchingStarted = true;
                mNumVolumeAdjustmentsBatched = 0;
                return REGULAR_VOLUME_ADJUSTMENT;
            }
        } else if (keyEventMode == AudioDeviceVolumeManager.ADJUST_MODE_END) {
            // ADJUST_MODE_END: end of a long press
            mBatchingStarted = false;
            return NO_VOLUME_ADJUSTMENT;
        } else {
            // ADJUST_MODE_NORMAL: a short press
            mBatchingStarted = false;
            return REGULAR_VOLUME_ADJUSTMENT;
        }
    }
}
