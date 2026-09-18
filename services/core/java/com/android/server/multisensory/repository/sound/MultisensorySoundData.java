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

package com.android.server.multisensory.repository.sound;

import android.os.multisensory.MultisensoryManager;
import android.provider.Settings;
import android.util.SparseArray;

/**
 * A static container of sound data in the {@link
 * com.android.server.multisensory.repository.MultisensoryRepository}
 *
 * @hide
 */
public class MultisensorySoundData {

    public static final SparseArray<String> TOKEN_SOUND_NAMES = new SparseArray<>();

    // TODO (b/462748962): We only have LOCK and UNLOCK MSDS sounds in the system right now.
    static {
        TOKEN_SOUND_NAMES.put(MultisensoryManager.TOKEN_FAILURE_HIGH_EMPHASIS, null);
        TOKEN_SOUND_NAMES.put(MultisensoryManager.TOKEN_FAILURE, null);
        TOKEN_SOUND_NAMES.put(MultisensoryManager.TOKEN_SUCCESS, null);
        TOKEN_SOUND_NAMES.put(MultisensoryManager.TOKEN_START, null);
        TOKEN_SOUND_NAMES.put(MultisensoryManager.TOKEN_PAUSE, null);
        TOKEN_SOUND_NAMES.put(MultisensoryManager.TOKEN_STOP, null);
        TOKEN_SOUND_NAMES.put(MultisensoryManager.TOKEN_CANCEL, null);
        TOKEN_SOUND_NAMES.put(MultisensoryManager.TOKEN_SWITCH_ON, null);
        TOKEN_SOUND_NAMES.put(MultisensoryManager.TOKEN_SWITCH_OFF, null);
        TOKEN_SOUND_NAMES.put(MultisensoryManager.TOKEN_UNLOCK, Settings.Global.UNLOCK_SOUND);
        TOKEN_SOUND_NAMES.put(MultisensoryManager.TOKEN_LOCK, Settings.Global.LOCK_SOUND);
        TOKEN_SOUND_NAMES.put(MultisensoryManager.TOKEN_LONG_PRESS, null);
        TOKEN_SOUND_NAMES.put(MultisensoryManager.TOKEN_SWIPE_INDICATOR_THRESHOLD_LIMIT, null);
        TOKEN_SOUND_NAMES.put(MultisensoryManager.TOKEN_TAP_HIGH_EMPHASIS, null);
        TOKEN_SOUND_NAMES.put(MultisensoryManager.TOKEN_TAP_MEDIUM_EMPHASIS, null);
        TOKEN_SOUND_NAMES.put(MultisensoryManager.TOKEN_DRAG_INDICATOR_THRESHOLD_LIMIT, null);
        TOKEN_SOUND_NAMES.put(MultisensoryManager.TOKEN_DRAG_INDICATOR_CONTINUOUS, null);
        TOKEN_SOUND_NAMES.put(MultisensoryManager.TOKEN_DRAG_INDICATOR_DISCRETE, null);
        TOKEN_SOUND_NAMES.put(MultisensoryManager.TOKEN_TAP_LOW_EMPHASIS, null);
        TOKEN_SOUND_NAMES.put(MultisensoryManager.TOKEN_KEYPRESS_STANDARD, null);
        TOKEN_SOUND_NAMES.put(MultisensoryManager.TOKEN_KEYPRESS_SPACEBAR, null);
        TOKEN_SOUND_NAMES.put(MultisensoryManager.TOKEN_KEYPRESS_RETURN, null);
        TOKEN_SOUND_NAMES.put(MultisensoryManager.TOKEN_KEYPRESS_DELETE, null);
    }
}
