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
* Callback to report operation son a realtime feedback session in the IMultisensoryPlayer
* @hide
*/
oneway interface IMultisensoryPlayerSessionCallback {

    /** Report the result of opening a realtime session for a token */
    void onOpenRealtimeSessionComplete(in long playerId, in int tokenConstant,
        in int openSessionResult, in IMultisensoryRealtimeSession session) = 1;

    /** Report the result of starting a realtime session for a token */
    void onStartRealtimeSessionComplete(in long playerId, in int tokenConstant,
        in int startSessionResult) = 2;

    /** Report the result of closing a realtime session for a token */
    void onCloseRealtimeSessionComplete(in long playerId, in int tokenConstant,
        in int closeSessionResult) = 3;
}

