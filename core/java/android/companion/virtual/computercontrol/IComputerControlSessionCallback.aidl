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

package android.companion.virtual.computercontrol;

import android.app.PendingIntent;
import android.companion.virtual.computercontrol.IComputerControlSession;
import android.hardware.display.IVirtualDisplayCallback;

/**
 * Callback for computer control session events.
 *
 * @hide
 */
oneway interface IComputerControlSessionCallback {

    /** Called when the session request needs to approved by the user. */
    void onSessionPending(in PendingIntent pendingIntent);

    /** Called when the session has been successfully created. */
    void onSessionCreated(int displayId, in IVirtualDisplayCallback displayToken,
            in IComputerControlSession session);

    /** Called when the session failed to be created. */
    void onSessionCreationFailed(int errorCode);

    /** Called when the session has been closed. */
    void onSessionClosed();
}
