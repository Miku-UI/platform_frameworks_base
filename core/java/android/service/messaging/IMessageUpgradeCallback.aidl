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

package android.service.messaging;

/**
 * Callback interface definition for the Message Upgrade Service to report the status of message
 * upgrade request back to the client.
 * @hide
 */
oneway interface IMessageUpgradeCallback {
    /**
     * Called when the service is ready to send the message upgrade status.
     *
     * @param status the status of the message upgrade request.
     */
    void onUpgradeStatusAvailable(in int status);
}
