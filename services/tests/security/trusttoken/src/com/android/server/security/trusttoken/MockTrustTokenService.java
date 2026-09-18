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

package com.android.server.security.trusttoken;

import static org.mockito.Mockito.mock;

import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import android.security.trusttoken.TrustTokenRequest;
import android.security.trusttoken.TrustTokenResponse;
import android.security.trusttoken.TrustTokenService;
import android.security.trusttoken.TrustTokenServiceException;

public final class MockTrustTokenService extends TrustTokenService {
    static final TrustTokenService MOCK = mock(TrustTokenService.class);

    @Override
    public void onRequestTrustTokens(
            TrustTokenRequest request,
            CancellationSignal cancellationSignal,
            OutcomeReceiver<TrustTokenResponse, TrustTokenServiceException> callback) {
        MOCK.onRequestTrustTokens(request, cancellationSignal, callback);
    }
}
