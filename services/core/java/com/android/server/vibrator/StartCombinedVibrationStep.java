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

package com.android.server.vibrator;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.hardware.vibrator.IVibratorManager;
import android.os.CombinedVibration;
import android.os.SystemClock;
import android.os.Trace;
import android.os.VibrationEffect;
import android.os.vibrator.PrebakedSegment;
import android.os.vibrator.PrimitiveSegment;
import android.os.vibrator.StepSegment;
import android.os.vibrator.VibrationEffectSegment;
import android.util.Slog;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Starts a synced vibration on one or more vibrators.
 *
 * <p>If this step has successfully started playing a vibration on any vibrator, it will always
 * add a {@link FinishCombinedVibrationStep} to the queue, to be played after all vibrators
 * have finished all their individual steps.
 *
 * <p>If this step does not start any vibrator, it will return an empty queue of next steps.
 */
final class StartCombinedVibrationStep extends Step {
    public final CombinedVibration vibration;

    private long mVibratorsOnMaxDuration;

    StartCombinedVibrationStep(VibrationStepConductor conductor, CombinedVibration vibration) {
        super(conductor, SystemClock.uptimeMillis());
        this.vibration = vibration;
    }

    @Override
    public long getVibratorOnDuration() {
        return mVibratorsOnMaxDuration;
    }

    @NonNull
    @Override
    public List<Step> play() {
        Trace.traceBegin(Trace.TRACE_TAG_VIBRATOR, "StartCombinedVibrationStep");
        List<Step> nextSteps = new ArrayList<>();
        mVibratorsOnMaxDuration = -1;
        try {
            DeviceEffectMap effectMapping = createEffectToVibratorMapping(vibration);
            if (effectMapping == null) {
                // Unable to map effects to vibrators, ignore this step.
                return nextSteps;
            }

            mVibratorsOnMaxDuration = startVibrating(effectMapping, nextSteps);
        } finally {
            if (mVibratorsOnMaxDuration > 0) {
                conductor.vibratorManagerHooks.noteVibratorOn(
                        conductor.getVibration().callerInfo.uid, mVibratorsOnMaxDuration);
                // It least one vibrator was started then add a finish step to wait for all
                // active vibrators to finish their individual steps before finishing.
                nextSteps.add(new FinishCombinedVibrationStep(conductor));
            }
            Trace.traceEnd(Trace.TRACE_TAG_VIBRATOR);
        }
        return nextSteps;
    }

    @NonNull
    @Override
    public List<Step> cancel() {
        return VibrationStepConductor.EMPTY_STEP_LIST;
    }

    @Override
    public void cancelImmediately() {
    }

    /** Create a mapping of individual {@link VibrationEffect} to available vibrators. */
    @Nullable
    private DeviceEffectMap createEffectToVibratorMapping(CombinedVibration effect) {
        if (effect instanceof CombinedVibration.Mono) {
            return new DeviceEffectMap((CombinedVibration.Mono) effect);
        }
        if (effect instanceof CombinedVibration.Stereo) {
            return new DeviceEffectMap((CombinedVibration.Stereo) effect);
        }
        return null;
    }

    /**
     * Starts playing effects on designated vibrators, in sync.
     *
     * @param effectMapping The {@link CombinedVibration} mapped to this device vibrators
     * @param nextSteps     An output list to accumulate the future {@link Step
     *                      Steps} created
     *                      by this method, typically one for each vibrator that has
     *                      successfully started vibrating on this step.
     * @return The duration, in millis, of the {@link CombinedVibration}. Repeating
     * waveforms return {@link Long#MAX_VALUE}. Zero or negative values indicate the vibrators
     * have ignored all effects.
     */
    private long startVibrating(DeviceEffectMap effectMapping, List<Step> nextSteps) {
        int vibratorCount = effectMapping.size();
        if (vibratorCount == 0) {
            // No effect was mapped to any available vibrator.
            return 0;
        }

        AbstractVibratorStep[] steps = new AbstractVibratorStep[vibratorCount];
        long vibrationStartTime = SystemClock.uptimeMillis();
        for (int i = 0; i < vibratorCount; i++) {
            steps[i] = conductor.nextVibrateStep(vibrationStartTime,
                    conductor.getVibrators().get(effectMapping.vibratorIdAt(i)),
                    effectMapping.effectAt(i));
        }

        if (steps.length == 1) {
            // No need to prepare and trigger sync effects on a single vibrator.
            return startVibrating(steps[0], effectMapping.effectAt(0), nextSteps);
        }

        // This synchronization of vibrators should be executed one at a time, even if we are
        // vibrating different sets of vibrators in parallel. The manager can only prepareSynced
        // one set of vibrators at a time.
        // This property is guaranteed by there only being one thread (VibrationThread) executing
        // one Step at a time, so there's no need to hold the state lock. Callbacks will be
        // delivered asynchronously but enqueued until the step processing is finished.
        boolean hasPrepared = conductor.vibratorManagerHooks.prepareSyncedVibration(
                effectMapping.getRequiredSyncCapabilities(),
                effectMapping.getVibratorIds());
        boolean hasTriggered = false;
        boolean hasFailed = false;
        long maxDuration = 0;

        for (int i = 0; i < vibratorCount; i++) {
            long duration = startVibrating(steps[i], effectMapping.effectAt(i), nextSteps);
            if (duration < 0) {
                // One vibrator has failed, fail this entire sync attempt.
                hasFailed = true;
                break;
            }
            maxDuration = Math.max(maxDuration, duration);
        }

        // Check if sync was prepared and if any step was accepted by a vibrator,
        // otherwise there is nothing to trigger here.
        if (hasPrepared && !hasFailed && maxDuration > 0) {
            hasTriggered = conductor.vibratorManagerHooks.triggerSyncedVibration(
                    getVibration().id);
        }

        if (hasFailed) {
            // Something failed, possibly after other vibrators were activated.
            // Cancel and remove every pending step from output list.
            for (int i = nextSteps.size() - 1; i >= 0; i--) {
                nextSteps.remove(i).cancelImmediately();
            }
        }

        // Cancel the preparation if trigger failed or all
        if (hasPrepared && !hasTriggered) {
            // Trigger has failed or was skipped, so abort the synced vibration.
            conductor.vibratorManagerHooks.cancelSyncedVibration();
        }

        return hasFailed ? -1 : maxDuration;
    }

    private long startVibrating(@Nullable AbstractVibratorStep step, VibrationEffect effect,
            List<Step> nextSteps) {
        if (step == null) {
            // Failed to create a step for VibrationEffect.
            return -1;
        }
        nextSteps.addAll(step.play());
        long stepDuration = step.getVibratorOnDuration();
        if (stepDuration < 0) {
            // Step failed, so return negative duration to propagate failure.
            return stepDuration;
        }
        // Return the longest estimation for the entire effect.
        return Math.max(stepDuration, effect.getDuration());
    }

    /**
     * Map a {@link CombinedVibration} to the vibrators available on the device.
     *
     * <p>This contains the logic to find the capabilities required from {@link IVibratorManager} to
     * play all of the effects in sync.
     */
    final class DeviceEffectMap {
        private final SparseArray<VibrationEffect> mVibratorEffects;
        private final int[] mVibratorIds;
        private final long mRequiredSyncCapabilities;

        DeviceEffectMap(CombinedVibration.Mono mono) {
            SparseArray<HalVibrator> vibrators = conductor.getVibrators();
            VibrationEffect effect = mono.getEffect();
            mVibratorEffects = new SparseArray<>(vibrators.size());
            mVibratorIds = new int[vibrators.size()];

            for (int i = 0; i < vibrators.size(); i++) {
                int vibratorId = vibrators.keyAt(i);
                mVibratorEffects.put(vibratorId, effect);
                mVibratorIds[i] = vibratorId;
            }
            mRequiredSyncCapabilities = calculateRequiredSyncCapabilities(mVibratorEffects);
        }

        DeviceEffectMap(CombinedVibration.Stereo stereo) {
            SparseArray<HalVibrator> vibrators = conductor.getVibrators();
            SparseArray<VibrationEffect> stereoEffects = stereo.getEffects();
            mVibratorEffects = new SparseArray<>();
            for (int i = 0; i < stereoEffects.size(); i++) {
                int vibratorId = stereoEffects.keyAt(i);
                if (vibrators.contains(vibratorId)) {
                    mVibratorEffects.put(vibratorId, stereoEffects.valueAt(i));
                }
            }
            mVibratorIds = new int[mVibratorEffects.size()];
            for (int i = 0; i < mVibratorEffects.size(); i++) {
                mVibratorIds[i] = mVibratorEffects.keyAt(i);
            }
            mRequiredSyncCapabilities = calculateRequiredSyncCapabilities(mVibratorEffects);
        }

        /**
         * Return the number of vibrators mapped to play the {@link CombinedVibration} on this
         * device.
         */
        public int size() {
            return mVibratorIds.length;
        }

        /**
         * Return all capabilities required to play the {@link CombinedVibration} in
         * between calls to {@link IVibratorManager#prepareSynced} and
         * {@link IVibratorManager#triggerSynced}.
         */
        public long getRequiredSyncCapabilities() {
            return mRequiredSyncCapabilities;
        }

        /** Return all vibrator ids mapped to play the {@link CombinedVibration}. */
        public int[] getVibratorIds() {
            return mVibratorIds;
        }

        /** Return the id of the vibrator at given index. */
        public int vibratorIdAt(int index) {
            return mVibratorEffects.keyAt(index);
        }

        /** Return the {@link VibrationEffect} at given index. */
        public VibrationEffect effectAt(int index) {
            return mVibratorEffects.valueAt(index);
        }

        /**
         * Return all capabilities required from the {@link IVibratorManager} to prepare and
         * trigger all given effects in sync.
         *
         * @return {@link IVibratorManager#CAP_SYNC} together with all required
         * IVibratorManager.CAP_PREPARE_* and IVibratorManager.CAP_MIXED_TRIGGER_* capabilities.
         */
        private long calculateRequiredSyncCapabilities(
                SparseArray<VibrationEffect> effects) {
            long prepareCap = 0;
            for (int i = 0; i < effects.size(); i++) {
                prepareCap |= calculateRequiredSyncCapabilities(effects.valueAt(i));
            }
            int triggerCap = 0;
            if (requireMixedTriggerCapability(prepareCap, IVibratorManager.CAP_PREPARE_ON)) {
                triggerCap |= IVibratorManager.CAP_MIXED_TRIGGER_ON;
            }
            if (requireMixedTriggerCapability(prepareCap, IVibratorManager.CAP_PREPARE_PERFORM)) {
                triggerCap |= IVibratorManager.CAP_MIXED_TRIGGER_PERFORM;
            }
            if (requireMixedTriggerCapability(prepareCap, IVibratorManager.CAP_PREPARE_COMPOSE)) {
                triggerCap |= IVibratorManager.CAP_MIXED_TRIGGER_COMPOSE;
            }
            return IVibratorManager.CAP_SYNC | prepareCap | triggerCap;
        }

        private long calculateRequiredSyncCapabilities(VibrationEffect effect) {
            if (effect instanceof VibrationEffect.VendorEffect) {
                return IVibratorManager.CAP_PREPARE_PERFORM;
            }
            if (effect instanceof VibrationEffect.Composed composed) {
                VibrationEffectSegment firstSegment = composed.getSegments().get(0);
                if (firstSegment instanceof StepSegment) {
                    return IVibratorManager.CAP_PREPARE_ON;
                } else if (firstSegment instanceof PrebakedSegment) {
                    return IVibratorManager.CAP_PREPARE_PERFORM;
                } else if (firstSegment instanceof PrimitiveSegment) {
                    return IVibratorManager.CAP_PREPARE_COMPOSE;
                }
            } else {
                Slog.wtf(VibrationThread.TAG,
                        "Unable to check sync capabilities to unexpected effect: " + effect);
            }
            return 0;
        }

        /**
         * Return true if {@code prepareCapabilities} contains this {@code capability} mixed with
         * different ones, requiring a mixed trigger capability from the vibrator manager for
         * syncing all effects.
         */
        private boolean requireMixedTriggerCapability(long prepareCapabilities, long capability) {
            return (prepareCapabilities & capability) != 0
                    && (prepareCapabilities & ~capability) != 0;
        }
    }
}
