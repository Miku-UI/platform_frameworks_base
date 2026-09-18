/**
 * Copyright (c) 2026, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os.multisensory;

import android.os.multisensory.IMultisensoryRealtimeSession;

/**
* Callback to report the result of the load() operation from the IMultisensoryPlayer
* @hide
*/
oneway interface IMultisensoryPlayerLoadCallback {

    // Possible results of the onLoadComplete callback
    const int LOAD_RESULT_SUCCESS = 0x0;
    const int LOAD_RESULT_UNKNOWN = 0x1;
    const int LOAD_RESULT_LOADING = 0x2;
    const int LOAD_RESULT_UNKNOWN_ERROR = 0x3;
    const int LOAD_RESULT_AUDIO_NOT_FOUND = 0x4;
    const int LOAD_RESULT_REMOTE_EXCEPTION = 0x5;

    // Possible results of the onHapticSessionOpenComplete callback
    const int OPEN_SESSION_RESULT_SUCCESS = 0X8;
    const int OPEN_SESSION_RESULT_UNKNOWN_ERROR = 0x9;

    // Possible results of onRealtimeSessionStart callback
    const int START_SESSION_SUCCESS = 0x10;
    const int START_SESSION_UNKNOWN_ERROR = 0x11;
    const int START_SESSION_CLOSED_SESSION_ERROR = 0x12; //Attempted to start a closed session

    // Possible results of onCloseRealtimeSessionComplete callback
    const int CLOSE_SESSION_SUCCESS = 0x18;
    const int CLOSE_SESSION_UNKNOWN_ERROR = 0x19;

    /** Report the result of loading the resources for a given token constant */
    void onLoadComplete(in long playerId, in int tokenConstant, in int loadResult) = 1;
}
