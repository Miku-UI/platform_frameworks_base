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

/**
 * A challenge issued by the UserRecoveryManagerService to a recovery agent.
 * The agent must provide a valid RecoveryAgentResponse to this challenge,
 * likely in the requestValidation() call.
 * @hide
 */
parcelable RecoveryChallenge {
    /** Version of this challenge structure. */
    int version = 1;

    /**
     * The type of challenge.
     * Constants to be defined in UserRecoveryManager.java.
     * e.g., CHALLENGE_TYPE_SIGN_NONCE
     */
    int challengeType;

    /** The actual challenge data. The format depends on the challengeType. */
    byte[] challengeData;

    /** Optional: Parameters or metadata associated with the challenge. */
    byte[] challengeMetadata;
}
