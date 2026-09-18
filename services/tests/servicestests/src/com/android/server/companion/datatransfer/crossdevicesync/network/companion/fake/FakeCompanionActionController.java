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
package com.android.server.companion.datatransfer.crossdevicesync.network.companion.fake;

import static com.android.server.companion.datatransfer.crossdevicesync.common.Utils.isFutureFailed;

import android.util.IndentingPrintWriter;

import androidx.annotation.Nullable;

import com.android.internal.infra.AndroidFuture;
import com.android.server.companion.datatransfer.crossdevicesync.common.fake.FakeCompanionDeviceManagerProxy;
import com.android.server.companion.datatransfer.crossdevicesync.network.companion.CompanionActionController;

import java.util.HashMap;
import java.util.Map;

/** A fake implementation of {@link CompanionActionController}. */
public class FakeCompanionActionController implements CompanionActionController {
    private final FakeCompanionDeviceManagerProxy mCompanionDeviceManager;
    private final Map<Integer, AndroidFuture<?>> mTransportRequests = new HashMap<>();
    private final Map<Integer, AndroidFuture<?>> mScanningRequests = new HashMap<>();
    private final Map<Integer, AndroidFuture<?>> mAdvertisingRequests = new HashMap<>();
    private boolean mAutomaticallyAttachTransport;
    private boolean mInitialized;

    public FakeCompanionActionController(FakeCompanionDeviceManagerProxy companionDeviceManager) {
        mCompanionDeviceManager = companionDeviceManager;
    }

    public void setAutomaticallyAttachTransport(boolean enabled) {
        mAutomaticallyAttachTransport = enabled;
    }

    @Override
    public void init() {
        if (mInitialized) {
            return;
        }
        mInitialized = true;
    }

    @Override
    public void destroy() {
        if (!mInitialized) {
            return;
        }
        mInitialized = false;
        mTransportRequests
                .values()
                .forEach(f -> f.completeExceptionally(new RuntimeException("destroyed")));
        mTransportRequests.clear();
        mScanningRequests
                .values()
                .forEach(f -> f.completeExceptionally(new RuntimeException("destroyed")));
        mScanningRequests.clear();
        mAdvertisingRequests
                .values()
                .forEach(f -> f.completeExceptionally(new RuntimeException("destroyed")));
        mAdvertisingRequests.clear();
    }

    private void throwIfUninitialized() {
        if (!mInitialized) {
            throw new IllegalStateException("Not initialized!");
        }
    }

    @Override
    public AndroidFuture<?> attachTransport(int associationId) {
        throwIfUninitialized();
        AndroidFuture<?> future = newFutureIfAbsentOrFailed(mTransportRequests, associationId);
        if (mAutomaticallyAttachTransport && !future.isDone()) {
            mCompanionDeviceManager.setTransportAttachedState(associationId, true);
            future.complete(null);
        }
        return future;
    }

    @Override
    public AndroidFuture<?> detachTransport(int associationId) {
        throwIfUninitialized();
        AndroidFuture<?> future = mTransportRequests.remove(associationId);
        if (future != null) {
            future.cancel(true);
        }
        return AndroidFuture.completedFuture(true);
    }

    @Nullable
    public AndroidFuture<?> getAttachTransportFuture(int associationId) {
        throwIfUninitialized();
        return mTransportRequests.get(associationId);
    }

    @Override
    public AndroidFuture<?> startNearbyScanning(int associationId) {
        throwIfUninitialized();
        return newFutureIfAbsentOrFailed(mScanningRequests, associationId);
    }

    @Override
    public AndroidFuture<?> stopNearbyScanning(int associationId) {
        throwIfUninitialized();
        AndroidFuture<?> future = mScanningRequests.remove(associationId);
        if (future != null) {
            future.cancel(true);
        }
        return AndroidFuture.completedFuture(true);
    }

    @Nullable
    public AndroidFuture<?> getScanningFuture(int associationId) {
        throwIfUninitialized();
        return mScanningRequests.get(associationId);
    }

    @Override
    public AndroidFuture<?> startNearbyAdvertising(int associationId) {
        throwIfUninitialized();
        return newFutureIfAbsentOrFailed(mAdvertisingRequests, associationId);
    }

    @Override
    public AndroidFuture<?> stopNearbyAdvertising(int associationId) {
        throwIfUninitialized();
        AndroidFuture<?> future = mAdvertisingRequests.remove(associationId);
        if (future != null) {
            future.cancel(true);
        }
        return AndroidFuture.completedFuture(true);
    }

    @Nullable
    public AndroidFuture<?> getAdvertisingFuture(int associationId) {
        throwIfUninitialized();
        return mAdvertisingRequests.get(associationId);
    }

    private static AndroidFuture<?> newFutureIfAbsentOrFailed(
            Map<Integer, AndroidFuture<?>> map, int associationId) {
        AndroidFuture<?> future = map.get(associationId);
        if (future == null || (future.isDone() && isFutureFailed(future))) {
            future = new AndroidFuture<>();
            map.put(associationId, future);
        }
        return future;
    }

    @Override
    public void dump(IndentingPrintWriter pw) {}
}
