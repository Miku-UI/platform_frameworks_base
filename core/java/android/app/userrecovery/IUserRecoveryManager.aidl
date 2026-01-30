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

package android.app.userrecovery;

import android.app.userrecovery.IUserRecoverySession;
import android.app.userrecovery.RecoveryChallenge;

/**
* Main interface for managing user recovery operations.
* @hide
*/
interface IUserRecoveryManager {
    /**
     * Initiates a new recovery session for the given user to add recovery data.
     * Returns an IUserRecoverySession instance to manage this specific session.
     */
    IUserRecoverySession createRecoverySession(int userId) = 0;

    /**
     * Requests a Recovery Agent Registration Token (RART) for the user.
     * This token is used to register a new recovery agent.
     */
    byte[] requestRart(int userId) = 1;

    /**
     * Starts the recovery process for the user.
     * Returns a challenge to be solved by the recovery agent.
     */
    RecoveryChallenge startRecovery(int userId) = 2;
}
