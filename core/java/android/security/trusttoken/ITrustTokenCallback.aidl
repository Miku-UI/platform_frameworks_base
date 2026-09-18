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

package android.security.trusttoken;

import android.security.trusttoken.TrustTokenResponse;
import android.os.ICancellationSignal;

/**
 * Interface to receive trust tokens from the TrustTokenService.
 *
 * @hide
 */
oneway interface ITrustTokenCallback {
    void onSuccess(in TrustTokenResponse response);
    // This returns the remote CancellationSignal. This itself is NOT a
    // cancellation.
    void onRemoteCancellationSignal(in ICancellationSignal signal);
    void onFailure(int code);
}
