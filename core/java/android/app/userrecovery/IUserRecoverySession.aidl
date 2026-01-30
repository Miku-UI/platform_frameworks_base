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

import android.app.userrecovery.EscrowToken;
import android.app.userrecovery.RecoveryAgentResponse;
import android.app.userrecovery.CertificateBlob; // Import the new Parcelable
import java.util.List;

/**
* Interface representing a single recovery session for a user.
* An instance of this interface is obtained from IUserRecoveryManager.createRecoverySession().
* @hide
*/
interface IUserRecoverySession {
    /**
     * Provides an escrow token received from a recovery agent
     * to the service for this session.
     */
    void saveEscrowToken(in EscrowToken escrowToken) = 0;

    /**
     * Called by the recovery agent to save the key pair generated for
     * the user's recovery for this session.
     * keyBlob: The encrypted key pair.
     * certChain: The attestation certificate chain for the key pair,
     *            represented as a List of CertificateBlob objects.
     */
    void saveKeyPair(in byte[] keyBlob, in List<CertificateBlob> certChain) = 1;

    /**
     * Requests validation of a recovery attempt for this session.
     */
    boolean requestValidation(in RecoveryAgentResponse recoveryResponse) = 2;

    /**
     * Closes this recovery session, releasing any associated resources.
     * After calling close, other methods on this interface instance may fail.
     */
    void close() = 3;
}
