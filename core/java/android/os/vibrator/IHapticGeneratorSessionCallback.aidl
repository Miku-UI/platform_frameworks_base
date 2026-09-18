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

package android.os.vibrator;

import android.os.vibrator.IHapticGeneratorSession;

/**
 * Callback for haptic generator session creation.
 * @hide
 */
oneway interface IHapticGeneratorSessionCallback {
    /**
     * The error is unknown.
     */
    const int ERROR_CODE_UNKNOWN = 0;
    /**
     * Haptic generator sessions are not supported.
     */
    const int ERROR_CODE_UNSUPPORTED = 1;
    /**
     * The operation could not be performed because the haptic generator session is in an
     * invalid state.
     *
     * <p>This can happen if an operation is attempted on a session that has already been closed.
     *
     */
    const int ERROR_CODE_ILLEGAL_STATE = 2;
    /**
     * The operation could not be performed because the haptic generator session received invalid
     * arguments.
     *
     * <p>This can happen if the audio format is invalid or null.
     *
     */
    const int ERROR_CODE_ILLEGAL_ARGUMENT = 3;
    /**
     * Called when the session has been successfully started.
     *
     * @param session The binder object for the newly created session.
     */
    void onSessionStarted(in IHapticGeneratorSession session);

    /**
     * Called if an error occurs during session creation.
     *
     * @param errorCode The error code indicating the reason for failure.
     */
    void onError(int errorCode);
}