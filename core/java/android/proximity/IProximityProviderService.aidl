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
package android.proximity;

import android.os.ICancellationSignal;
import android.proximity.IProximityResultCallback;
import android.proximity.IProximityResultCallback.Result;
import android.proximity.ProximityResultCode;
import android.proximity.RangingParams;

/**
 * @hide
 * Interface for proximity provider service.
 */
interface IProximityProviderService {
    /**
     * Finds nearby watch paired to this phone. If the watch is not found within the
     * specified timeout, the callback is called with result set to NO_RANGING_RESULT.
     * If timeout is <= 0, it will use a default timeout of 5 seconds.
     *
     * Returns a cancellation signal associated with the watch ranging operation.
     * The cancellation signal can be null if watch ranging does not start.
     */
    ICancellationSignal anyWatchNearby(
            in RangingParams params, in IProximityResultCallback callback);

    /**
     * Returns true if this device and the paired watch supports proximity checking.
     */
    boolean isProximityCheckingSupported();

    /**
     * Returns whether proximity checking is available on both primary and associated devices.
     */
    ProximityResultCode isProximityCheckingAvailable();
}
