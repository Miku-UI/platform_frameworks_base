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

package com.android.systemui.doze;

import android.hardware.display.AmbientDisplayConfiguration;
import android.util.Log;

import com.android.systemui.minmode.MinModeManager;
import com.android.systemui.doze.DozeMachine.State;
import com.android.systemui.doze.dagger.DozeScope;
import com.android.systemui.settings.UserTracker;

import java.io.PrintWriter;
import java.util.Optional;

import javax.inject.Inject;

/** Handles minmode events for ambient state changes. */
@DozeScope
public class DozeMinMode implements DozeMachine.Part {

    private static final String TAG = "DozeMinMode";
    private static final boolean DEBUG = DozeService.DEBUG;

    private final AmbientDisplayConfiguration mConfig;
    private DozeMachine mMachine;
    private final Optional<MinModeManager> mMinModeManager;
    private final UserTracker mUserTracker;
    private final MinModeEventListener mMinModeEventListener;

    private int mMinModeState = MinModeManager.STATE_NONE;

    @Inject
    DozeMinMode(
            AmbientDisplayConfiguration config,
            Optional<MinModeManager> minModeManager,
            UserTracker userTracker) {
        mConfig = config;
        mMinModeManager = minModeManager;
        mUserTracker = userTracker;
        mMinModeEventListener = new MinModeEventListener();
    }

    @Override
    public void setDozeMachine(DozeMachine dozeMachine) {
        mMachine = dozeMachine;
    }

    @Override
    public void transitionTo(DozeMachine.State oldState, DozeMachine.State newState) {
        switch (newState) {
            case INITIALIZED:
                mMinModeEventListener.register();
                break;
            case FINISH:
                mMinModeEventListener.unregister();
                break;
            default:
                // no-op
        }
    }

    @Override
    public void dump(PrintWriter pw) {
        pw.println("DozeMinMode:");
        pw.println(" minModeState=" + mMinModeState);
    }

    private class MinModeEventListener implements MinModeManager.MinModeEventListener {
        private boolean mRegistered;

        @Override
        public void onEvent(int minModeState) {
            if (DEBUG) Log.d(TAG, "minmode event = " + minModeState);

            // Only act upon state changes, otherwise we might overwrite other transitions,
            // like proximity sensor initialization.
            if (mMinModeState == minModeState) {
                return;
            }

            mMinModeState = minModeState;
            if (mMachine.isExecutingTransition() || isPulsing()) {
                // If the device is in the middle of executing a transition or is pulsing,
                // exit early instead of requesting a new state. DozeMachine
                // will check the minmode state and resolveIntermediateState in the next
                // transition after pulse done.
                return;
            }

            DozeMachine.State nextState;
            switch (mMinModeState) {
                case MinModeManager.STATE_MINMODE_ENABLED:
                case MinModeManager.STATE_MINMODE_ACTIVE:
                    nextState = State.DOZE_AOD_MINMODE;
                    break;
                case MinModeManager.STATE_NONE:
                    nextState =
                            mConfig.alwaysOnEnabled(mUserTracker.getUserId())
                                    ? State.DOZE_AOD
                                    : State.DOZE;
                    break;
                default:
                    return;
            }
            mMachine.requestState(nextState);
        }

        private boolean isPulsing() {
            DozeMachine.State state = mMachine.getState();
            return state == State.DOZE_REQUEST_PULSE
                    || state == State.DOZE_PULSING
                    || state == State.DOZE_PULSING_BRIGHT;
        }

        void register() {
            if (mRegistered) {
                return;
            }
            if (mMinModeManager.isPresent()) {
                mMinModeManager.get().addListener(this);
            }
            mRegistered = true;
        }

        void unregister() {
            if (!mRegistered) {
                return;
            }
            if (mMinModeManager.isPresent()) {
                mMinModeManager.get().removeListener(this);
            }
            mRegistered = false;
        }
    }
}
