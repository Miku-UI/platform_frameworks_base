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

package com.android.server.security.authenticationpolicy;

import android.annotation.NonNull;
import android.proximity.IProximityResultCallback;
import android.proximity.RangingParams;

/**
 * Local system service for {@link WatchRangingService}.
 *
 * @hide
 */
public interface WatchRangingServiceInternal {
    /**
     * This function should bind to {@link android.proximity.IProximityProviderService} and start
     * watch ranging using {@link android.proximity.IProximityProviderService#anyWatchNearby(
     * RangingParams, IProximityResultCallback)}.
     *
     * @param authenticationRequestId request id for authentication session
     * @param proximityResultCallback callback to receive watch ranging results
     */
    void startWatchRangingForIdentityCheck(long authenticationRequestId,
            @NonNull IProximityResultCallback proximityResultCallback);

    /**
     * Cancel watch ranging request if it matches the given authentication request id.
     *
     * @param authenticationRequestId request id for authentication session
     */
    void cancelWatchRangingForRequestId(long authenticationRequestId);

    /**
     * Checks if watch ranging is available or not.
     *
     * @param proximityResultCallback callback to return the result
     */
    void isWatchRangingAvailable(@NonNull IProximityResultCallback proximityResultCallback);
}
