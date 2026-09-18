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

package android.security.trusttoken;

import android.security.trusttoken.TrustToken;
import android.security.trusttoken.TrustTokenIdentitySet;
import android.security.trusttoken.TrustTokenWithChallenge;

/**
 * Interface for the TrustTokenManagerService.
 * @hide
 */
interface ITrustTokenManager {
    // TODO(b/418280383): Replace with correct permissions
    @EnforcePermission(allOf = {"ACQUIRE_VERIFIED_DEVICE_TOKEN", "SIGN_WITH_TRUST_TOKEN"})
    TrustTokenWithChallenge acquireVerifiedDeviceToken(in byte[] challenge);
    TrustTokenIdentitySet acquirePreparedIdentitySet(in byte[] challenge);
    @EnforcePermission("ACQUIRE_VERIFIED_DEVICE_TOKEN")
    int verifyTrustTokenAndChallenge(in TrustToken token, in byte[] remoteResponse,
            in byte[] expectedChallenge);
    int[] verifyIdentityTokens(in TrustToken verifiedDeviceToken, in TrustToken[] identityTokens);
    void updatePreparedIdentities(in List<String> identities);
}
