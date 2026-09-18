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

package android.os.multisensory;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.SystemService;
import android.annotation.TestApi;
import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Provides capabilities to deliver audio-haptic feedback from the Multisensory Design System in
 * Android.
 */
@FlaggedApi(Flags.FLAG_ENABLE_MULTISENSORY_FEEDBACK)
@SystemService(Context.MULTISENSORY_MANAGER_SERVICE)
public class MultisensoryManager {

    /** @hide */
    @IntDef(
            prefix = {"TOKEN_"},
            value = {
                TOKEN_FAILURE_HIGH_EMPHASIS,
                TOKEN_FAILURE,
                TOKEN_SUCCESS,
                TOKEN_START,
                TOKEN_PAUSE,
                TOKEN_STOP,
                TOKEN_CANCEL,
                TOKEN_SWITCH_ON,
                TOKEN_SWITCH_OFF,
                TOKEN_UNLOCK,
                TOKEN_LOCK,
                TOKEN_LONG_PRESS,
                TOKEN_SWIPE_INDICATOR_THRESHOLD_LIMIT,
                TOKEN_TAP_HIGH_EMPHASIS,
                TOKEN_TAP_MEDIUM_EMPHASIS,
                TOKEN_TAP_LOW_EMPHASIS,
                TOKEN_KEYPRESS_STANDARD,
                TOKEN_KEYPRESS_SPACEBAR,
                TOKEN_KEYPRESS_RETURN,
                TOKEN_KEYPRESS_DELETE,
                TOKEN_DRAG_INDICATOR_CONTINUOUS,
                TOKEN_DRAG_INDICATOR_DISCRETE,
                TOKEN_DRAG_INDICATOR_THRESHOLD_LIMIT,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Token {}

    /* Inform the user with emphasis that their current action failed to complete */
    public static final int TOKEN_FAILURE_HIGH_EMPHASIS = 0;

    /* Inform the user that their current action failed to complete */
    public static final int TOKEN_FAILURE = 1;

    /* Inform the user their current action was completed successfully */
    public static final int TOKEN_SUCCESS = 2;

    /* Inform the user that their intended action has started */
    public static final int TOKEN_START = 3;

    /* Inform the user that an ongoing action has paused */
    public static final int TOKEN_PAUSE = 4;

    /* Inform the user that their previously started action has stopped */
    public static final int TOKEN_STOP = 5;

    /* Inform the user that their previously started action was canceled */
    public static final int TOKEN_CANCEL = 6;

    /* Inform the user that the state of an interactive component has been switched to on */
    public static final int TOKEN_SWITCH_ON = 7;

    /* Inform the user that the state of an interactive component has been switched to off */
    public static final int TOKEN_SWITCH_OFF = 8;

    /* Inform the user the state of their device changed to unlocked */
    public static final int TOKEN_UNLOCK = 9;

    /* Inform the user the state of their device changed to locked */
    public static final int TOKEN_LOCK = 10;

    /* Inform the user that their long-press gesture has resulted in an action */
    public static final int TOKEN_LONG_PRESS = 11;

    /**
     * Inform the user that their swipe gesture has crossed a threshold. This indicates that the
     * swipe action beyond the threshold will activate when the swipe is completed.
     */
    public static final int TOKEN_SWIPE_INDICATOR_THRESHOLD_LIMIT = 12;

    /* Played when the user taps on a high-emphasis UI element */
    public static final int TOKEN_TAP_HIGH_EMPHASIS = 13;

    /* Inform the user that their tap has resulted in a selection */
    public static final int TOKEN_TAP_MEDIUM_EMPHASIS = 14;

    /**
     * Played when a user taps on any UI element that can be interacted with but is not otherwise
     * defined.
     */
    public static final int TOKEN_TAP_LOW_EMPHASIS = 15;

    /* Played when a users drag gesture reaches the maximum or minimum value */
    public static final int TOKEN_DRAG_INDICATOR_THRESHOLD_LIMIT = 16;

    /**
     * Inform the user that their drag gesture has resulted in an incremental value change.
     *
     * <p>This token can be used to create a continuous effect affected by {@link
     * MultisensoryContinuousEffectModifier}
     */
    public static final int TOKEN_DRAG_INDICATOR_CONTINUOUS = 17;

    /**
     * Inform the user that their drag gesture has resulted in a stepped value change.
     *
     * <p>This token can be used to create a continuous effect affected by {@link
     * MultisensoryContinuousEffectModifier}
     */
    public static final int TOKEN_DRAG_INDICATOR_DISCRETE = 18;

    /* Played when the user touches a key on the keyboard that is otherwise undefined */
    public static final int TOKEN_KEYPRESS_STANDARD = 19;

    /* Played when the user touches the space key */
    public static final int TOKEN_KEYPRESS_SPACEBAR = 20;

    /* Played when the user touches the return key */
    public static final int TOKEN_KEYPRESS_RETURN = 21;

    /* Played when the user touches the delete key */
    public static final int TOKEN_KEYPRESS_DELETE = 22;

    /** @hide */
    private static final @NonNull @Token int[] MULTISENSORY_TOKENS = {
        TOKEN_FAILURE_HIGH_EMPHASIS,
        TOKEN_FAILURE,
        TOKEN_SUCCESS,
        TOKEN_START,
        TOKEN_PAUSE,
        TOKEN_STOP,
        TOKEN_CANCEL,
        TOKEN_SWITCH_ON,
        TOKEN_SWITCH_OFF,
        TOKEN_UNLOCK,
        TOKEN_LOCK,
        TOKEN_LONG_PRESS,
        TOKEN_SWIPE_INDICATOR_THRESHOLD_LIMIT,
        TOKEN_TAP_HIGH_EMPHASIS,
        TOKEN_TAP_MEDIUM_EMPHASIS,
        TOKEN_TAP_LOW_EMPHASIS,
        TOKEN_KEYPRESS_STANDARD,
        TOKEN_KEYPRESS_SPACEBAR,
        TOKEN_KEYPRESS_RETURN,
        TOKEN_KEYPRESS_DELETE,
        TOKEN_DRAG_INDICATOR_CONTINUOUS,
        TOKEN_DRAG_INDICATOR_DISCRETE,
        TOKEN_DRAG_INDICATOR_THRESHOLD_LIMIT,
    };

    /**
     * Get a list of all the Multisensory tokens supported by the Multisensory Design System.
     *
     * @hide
     */
    @TestApi
    public static @NonNull @Token int[] getMultisensoryTokens() {
        return MULTISENSORY_TOKENS.clone();
    }

    private static final String TAG = "MultisensoryManager";

    private final IMultisensoryService mService;

    /**
     * @hide to prevent subclassing from outside the framework
     */
    public MultisensoryManager() {
        mService =
                IMultisensoryService.Stub.asInterface(
                        ServiceManager.getService(Context.MULTISENSORY_MANAGER_SERVICE));
    }

    /**
     * Deliver multisensory feedback associated with the given multisensory token constant.
     *
     * <p>If the token defines audio-haptic feedback, audio and haptics will be played in synchrony
     * if the device has this capability. Otherwise, only haptic feedback is delivered.
     *
     * @param tokenConstant The token constant to play. Must be one of the constants defined by the
     *     {@link MultisensoryManager}.
     */
    public void playToken(@Token int tokenConstant) {
        if (mService == null) {
            Log.e(TAG, "Failed to play token " + tokenConstant + ". No MultisensoryService");
            return;
        }
        try {
            mService.playToken(tokenConstant);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }
}
