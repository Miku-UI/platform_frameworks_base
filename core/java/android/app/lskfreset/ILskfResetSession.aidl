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

package android.app.lskfreset;

import android.app.lskfreset.EscrowToken;

/**
* Interface representing a single recovery session for a user.
* An instance of this interface is obtained from ILskfResetManager.createRecoverySession().
* @hide
*/
interface ILskfResetSession {
    /**
     * Provides an escrow token received from a recovery agent
     * to the service for this session.
     */
    void saveEscrowToken(in EscrowToken escrowToken) = 0;

    /**
     * Closes this recovery session, releasing any associated resources.
     * After calling close, other methods on this interface instance may fail.
     */
    void close() = 1;
}
