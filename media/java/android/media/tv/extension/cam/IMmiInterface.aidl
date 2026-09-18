/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.media.tv.extension.cam;

import android.media.tv.extension.cam.IEnterMenuErrorCallback;
import android.media.tv.extension.cam.IMmiSession;
import android.media.tv.extension.cam.IMmiStatusCallback;


/**
 * @hide
 */
interface IMmiInterface {
    /**
     * Opens a session for Man-Machine Interface (MMI) communication.
     *
     * @param slotId The ID of the CAM slot for communication.
     * @param callback The callback to receive MMI status notifications.
     * @return An {@link IMmiSession} binder interface for the session, or null if the
     *         session could not be opened.
     */
    IMmiSession openSession(int slotId, IMmiStatusCallback callback);
    /**
     * Requests to display the CI Module setup screen (Enter Menu).
     *
     * @param slotId The ID of the CAM slot for communication.
     * @param callback The IEnterMenuErrorCallback to receive notifications
     *                 if displaying the setup screen fails.
     */
    void appInfoEnterMenu(int slotId, IEnterMenuErrorCallback callback);
}
