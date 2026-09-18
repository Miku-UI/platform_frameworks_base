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
package com.android.server.companion.datatransfer.crossdevicesync.network.companion;

import com.android.internal.infra.AndroidFuture;
import com.android.server.companion.datatransfer.crossdevicesync.common.Dumpable;

/** An interface for requesting companion actions. */
public interface CompanionActionController extends Dumpable {

    /** Initialize this companion action controller. */
    void init();

    /** Destroy this companion action controller. */
    void destroy();

    /** Requests to attach transport. */
    AndroidFuture<?> attachTransport(int associationId);

    /** Requests to detach transport. */
    AndroidFuture<?> detachTransport(int associationId);

    /** Requests to start scanning. */
    AndroidFuture<?> startNearbyScanning(int associationId);

    /** Requests to stop scanning. */
    AndroidFuture<?> stopNearbyScanning(int associationId);

    /** Requests to start advertising. */
    AndroidFuture<?> startNearbyAdvertising(int associationId);

    /** Requests to stop advertising. */
    AndroidFuture<?> stopNearbyAdvertising(int associationId);
}
