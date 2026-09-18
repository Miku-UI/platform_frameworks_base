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

import static com.android.server.companion.datatransfer.crossdevicesync.common.Utils.isFutureFailed;
import static com.android.server.companion.datatransfer.crossdevicesync.common.Utils.isFutureSucceeded;

import static com.google.common.truth.Truth.assertThat;

import android.companion.ActionRequest;
import android.companion.AssociationInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.infra.AndroidFuture;
import com.android.server.companion.datatransfer.crossdevicesync.services.SyncServiceTestBase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CompanionActionControllerImplTest extends SyncServiceTestBase {
    private static int ASSOCIATION1_ID = 1;
    private static int ASSOCIATION2_ID = 2;

    @Before
    public void setUp() {
        mCompanionActionController.init();
        mFakeCompanionDeviceManagerProxy.addAssociation(createAssociationInfo(ASSOCIATION1_ID));
        mFakeCompanionDeviceManagerProxy.addAssociation(createAssociationInfo(ASSOCIATION2_ID));
    }

    @Test
    public void startScanning() {
        AndroidFuture<?> future = mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);

        assertThat(future.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_SCANNING))
                .isTrue();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void startScanning_duplicateCall() {
        AndroidFuture<?> future1 = mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        AndroidFuture<?> future2 = mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);

        assertThat(future1).isSameInstanceAs(future2);
        assertThat(future1.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_SCANNING))
                .isTrue();
    }

    @Test
    public void startScanning_alreadyScanning() {
        AndroidFuture<?> future = mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        // Another scanning request should succeed immediately.
        future = mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void startScanning_cancel() {
        AndroidFuture<?> future = mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);

        future.cancel(true);

        assertThat(future.isCancelled()).isTrue();
        // Cancel the future won't cancel a request that's already sent to CDM.
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_SCANNING))
                .isTrue();

        // Timeout should not matter now.
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);
        assertThat(future.isCancelled()).isTrue();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_SCANNING))
                .isTrue();

        // Result comes after cancel. Future remain cancelled.
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);
        assertThat(future.isCancelled()).isTrue();

        // Next request should succeed immediately.
        future = mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void startScanning_timeout() {
        AndroidFuture<?> future = mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);

        // Timeout
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);

        // Future is failed, but the CDM request remains active.
        assertThat(isFutureFailed(future)).isTrue();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_SCANNING))
                .isTrue();
    }

    @Test
    public void startScanning_alreadyTimeout() {
        AndroidFuture<?> future = mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        // Timeout
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);
        assertThat(isFutureFailed(future)).isTrue();

        // Scan again.
        future = mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();

        // Receive result now.
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);

        // Future receives result.
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void startScanning_fail() {
        AndroidFuture<?> future = mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);

        // Fail
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, false);

        assertThat(isFutureFailed(future)).isTrue();
    }

    @Test
    public void startScanning_alreadyFailed() {
        AndroidFuture<?> future = mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, false);
        assertThat(isFutureFailed(future)).isTrue();

        // Scanning again.
        future = mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void stopScanning() {
        AndroidFuture<?> future = mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        future = mCompanionActionController.stopNearbyScanning(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_SCANNING))
                .isFalse();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, false);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void stopScanning_alreadyStopped() {
        AndroidFuture<?> future = mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        // Fail later.
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, false);

        // Stop should succeed immediately.
        future = mCompanionActionController.stopNearbyScanning(ASSOCIATION1_ID);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void stopScanning_alreadyCancelled() {
        AndroidFuture<?> future = mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        future = mCompanionActionController.stopNearbyScanning(ASSOCIATION1_ID);

        // Cancel the future.
        future.cancel(true);
        assertThat(future.isCancelled()).isTrue();

        // Stop again and can still stop scanning.
        future = mCompanionActionController.stopNearbyScanning(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();

        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, false);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void stopScanning_duplicateCall() {
        AndroidFuture<?> future = mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        AndroidFuture<?> future1 = mCompanionActionController.stopNearbyScanning(ASSOCIATION1_ID);
        AndroidFuture<?> future2 = mCompanionActionController.stopNearbyScanning(ASSOCIATION1_ID);

        assertThat(future1).isSameInstanceAs(future2);
        assertThat(future1.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_SCANNING))
                .isFalse();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, false);
        assertThat(isFutureSucceeded(future1)).isTrue();
    }

    @Test
    public void stopScanning_notStarted() {
        AndroidFuture<?> future = mCompanionActionController.stopNearbyScanning(ASSOCIATION1_ID);

        // Succeed immediately.
        assertThat(isFutureSucceeded(future)).isTrue();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_SCANNING))
                .isFalse();
    }

    @Test
    public void stopScanning_cancel() {
        AndroidFuture<?> future = mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        future = mCompanionActionController.stopNearbyScanning(ASSOCIATION1_ID);

        // Cancel the future.
        future.cancel(true);
        assertThat(future.isCancelled()).isTrue();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_SCANNING))
                .isFalse();

        // Timeout doesn't matter now.
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);
        assertThat(future.isCancelled()).isTrue();
    }

    @Test
    public void stopScanning_timeout() {
        AndroidFuture<?> future = mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);
        assertThat(isFutureSucceeded(future)).isTrue();
        future = mCompanionActionController.stopNearbyScanning(ASSOCIATION1_ID);

        // Timeout
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);

        // Future is failed.
        assertThat(isFutureFailed(future)).isTrue();
    }

    @Test
    public void stopScanning_alreadyTimeout() {
        AndroidFuture<?> future = mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);
        assertThat(isFutureSucceeded(future)).isTrue();
        future = mCompanionActionController.stopNearbyScanning(ASSOCIATION1_ID);
        // Timeout
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);
        assertThat(isFutureFailed(future)).isTrue();

        // Stop again.
        future = mCompanionActionController.stopNearbyScanning(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, false);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void startAdvertising() {
        AndroidFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);

        assertThat(future.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isTrue();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void startAdvertising_duplicateCall() {
        AndroidFuture<?> future1 =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        AndroidFuture<?> future2 =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);

        assertThat(future1).isSameInstanceAs(future2);
        assertThat(future1.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isTrue();
    }

    @Test
    public void startAdvertising_alreadyAdvertising() {
        AndroidFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        // Another advertising request should succeed immediately.
        future = mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void startAdvertising_cancel() {
        AndroidFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);

        future.cancel(true);

        assertThat(future.isCancelled()).isTrue();
        // Cancel the future won't cancel a request that's already sent to CDM.
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isTrue();

        // Timeout should not matter now.
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);
        assertThat(future.isCancelled()).isTrue();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isTrue();

        // Result comes after cancel. Future remain cancelled.
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);
        assertThat(future.isCancelled()).isTrue();

        // Next request should succeed immediately.
        future = mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void startAdvertising_timeout() {
        AndroidFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);

        // Timeout
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);

        // Future is failed, but the CDM request remains active.
        assertThat(isFutureFailed(future)).isTrue();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isTrue();
    }

    @Test
    public void startAdvertising_alreadyTimeout() {
        AndroidFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        // Timeout
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);
        assertThat(isFutureFailed(future)).isTrue();

        // Scan again.
        future = mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();

        // Receive result now.
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);

        // Future receives result.
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void startAdvertising_fail() {
        AndroidFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);

        // Fail
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, false);

        assertThat(isFutureFailed(future)).isTrue();
    }

    @Test
    public void startAdvertising_alreadyFailed() {
        AndroidFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, false);
        assertThat(isFutureFailed(future)).isTrue();

        // Advertising again.
        future = mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void stopAdvertising() {
        AndroidFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        future = mCompanionActionController.stopNearbyAdvertising(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isFalse();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, false);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void stopAdvertising_alreadyStopped() {
        AndroidFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        // Fail later.
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, false);

        // Stop should succeed immediately.
        future = mCompanionActionController.stopNearbyAdvertising(ASSOCIATION1_ID);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void stopAdvertising_alreadyCancelled() {
        AndroidFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        future = mCompanionActionController.stopNearbyAdvertising(ASSOCIATION1_ID);

        // Cancel the future.
        future.cancel(true);
        assertThat(future.isCancelled()).isTrue();

        // Stop again and can still stop advertising.
        future = mCompanionActionController.stopNearbyAdvertising(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();

        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, false);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void stopAdvertising_duplicateCall() {
        AndroidFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        AndroidFuture<?> future1 =
                mCompanionActionController.stopNearbyAdvertising(ASSOCIATION1_ID);
        AndroidFuture<?> future2 =
                mCompanionActionController.stopNearbyAdvertising(ASSOCIATION1_ID);

        assertThat(future1).isSameInstanceAs(future2);
        assertThat(future1.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isFalse();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, false);
        assertThat(isFutureSucceeded(future1)).isTrue();
    }

    @Test
    public void stopAdvertising_notStarted() {
        AndroidFuture<?> future = mCompanionActionController.stopNearbyAdvertising(ASSOCIATION1_ID);

        // Succeed immediately.
        assertThat(isFutureSucceeded(future)).isTrue();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isFalse();
    }

    @Test
    public void stopAdvertising_cancel() {
        AndroidFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        future = mCompanionActionController.stopNearbyAdvertising(ASSOCIATION1_ID);

        // Cancel the future.
        future.cancel(true);
        assertThat(future.isCancelled()).isTrue();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isFalse();

        // Timeout doesn't matter now.
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);
        assertThat(future.isCancelled()).isTrue();
    }

    @Test
    public void stopAdvertising_timeout() {
        AndroidFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);
        assertThat(isFutureSucceeded(future)).isTrue();
        future = mCompanionActionController.stopNearbyAdvertising(ASSOCIATION1_ID);

        // Timeout
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);

        // Future is failed.
        assertThat(isFutureFailed(future)).isTrue();
    }

    @Test
    public void stopAdvertising_alreadyTimeout() {
        AndroidFuture<?> future =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);
        assertThat(isFutureSucceeded(future)).isTrue();
        future = mCompanionActionController.stopNearbyAdvertising(ASSOCIATION1_ID);
        // Timeout
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);
        assertThat(isFutureFailed(future)).isTrue();

        // Stop again.
        future = mCompanionActionController.stopNearbyAdvertising(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, false);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void attachTransport() {
        AndroidFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);

        assertThat(future.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isTrue();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void attachTransport_duplicateCall() {
        AndroidFuture<?> future1 = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        AndroidFuture<?> future2 = mCompanionActionController.attachTransport(ASSOCIATION1_ID);

        assertThat(future1).isSameInstanceAs(future2);
        assertThat(future1.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isTrue();
    }

    @Test
    public void attachTransport_alreadyAdvertising() {
        AndroidFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        // Another advertising request should succeed immediately.
        future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void attachTransport_cancel() {
        AndroidFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);

        future.cancel(true);

        assertThat(future.isCancelled()).isTrue();
        // Cancel the future won't cancel a request that's already sent to CDM.
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isTrue();

        // Timeout should not matter now.
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);
        assertThat(future.isCancelled()).isTrue();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isTrue();

        // Result comes after cancel. Future remain cancelled.
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);
        assertThat(future.isCancelled()).isTrue();

        // Next request should succeed immediately.
        future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void attachTransport_timeout() {
        AndroidFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);

        // Timeout
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);

        // Future is failed, but the CDM request remains active.
        assertThat(isFutureFailed(future)).isTrue();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isTrue();
    }

    @Test
    public void attachTransport_alreadyTimeout() {
        AndroidFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        // Timeout
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);
        assertThat(isFutureFailed(future)).isTrue();

        // Scan again.
        future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();

        // Receive result now.
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);

        // Future receives result.
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void attachTransport_fail() {
        AndroidFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);

        // Fail
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, false);

        assertThat(isFutureFailed(future)).isTrue();
    }

    @Test
    public void attachTransport_alreadyFailed() {
        AndroidFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, false);
        assertThat(isFutureFailed(future)).isTrue();

        // Advertising again.
        future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void detachTransport() {
        AndroidFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        future = mCompanionActionController.detachTransport(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isFalse();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, false);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void detachTransport_alreadyStopped() {
        AndroidFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        // Fail later.
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, false);

        // Stop should succeed immediately.
        future = mCompanionActionController.detachTransport(ASSOCIATION1_ID);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void detachTransport_alreadyCancelled() {
        AndroidFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        future = mCompanionActionController.detachTransport(ASSOCIATION1_ID);

        // Cancel the future.
        future.cancel(true);
        assertThat(future.isCancelled()).isTrue();

        // Stop again and can still stop advertising.
        future = mCompanionActionController.detachTransport(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();

        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, false);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void detachTransport_duplicateCall() {
        AndroidFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        AndroidFuture<?> future1 = mCompanionActionController.detachTransport(ASSOCIATION1_ID);
        AndroidFuture<?> future2 = mCompanionActionController.detachTransport(ASSOCIATION1_ID);

        assertThat(future1).isSameInstanceAs(future2);
        assertThat(future1.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isFalse();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, false);
        assertThat(isFutureSucceeded(future1)).isTrue();
    }

    @Test
    public void detachTransport_notStarted() {
        AndroidFuture<?> future = mCompanionActionController.detachTransport(ASSOCIATION1_ID);

        // Succeed immediately.
        assertThat(isFutureSucceeded(future)).isTrue();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isFalse();
    }

    @Test
    public void detachTransport_cancel() {
        AndroidFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);
        assertThat(isFutureSucceeded(future)).isTrue();

        future = mCompanionActionController.detachTransport(ASSOCIATION1_ID);

        // Cancel the future.
        future.cancel(true);
        assertThat(future.isCancelled()).isTrue();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isFalse();

        // Timeout doesn't matter now.
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);
        assertThat(future.isCancelled()).isTrue();
    }

    @Test
    public void detachTransport_timeout() {
        AndroidFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);
        assertThat(isFutureSucceeded(future)).isTrue();
        future = mCompanionActionController.detachTransport(ASSOCIATION1_ID);

        // Timeout
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);

        // Future is failed.
        assertThat(isFutureFailed(future)).isTrue();
    }

    @Test
    public void detachTransport_alreadyTimeout() {
        AndroidFuture<?> future = mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);
        assertThat(isFutureSucceeded(future)).isTrue();
        future = mCompanionActionController.detachTransport(ASSOCIATION1_ID);
        // Timeout
        mFakeClock.advanceTime(CompanionActionControllerImpl.WAITING_TIMEOUT_MS);
        assertThat(isFutureFailed(future)).isTrue();

        // Stop again.
        future = mCompanionActionController.detachTransport(ASSOCIATION1_ID);
        assertThat(future.isDone()).isFalse();

        // Success
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, false);
        assertThat(isFutureSucceeded(future)).isTrue();
    }

    @Test
    public void concurrentRequestsAcrossAssociations() {
        // Start scanning on ASSOCIATION1_ID
        AndroidFuture<?> scanFuture1 =
                mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        assertThat(scanFuture1.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_SCANNING))
                .isTrue();

        // Start advertising on ASSOCIATION2_ID
        AndroidFuture<?> advertiseFuture2 =
                mCompanionActionController.startNearbyAdvertising(ASSOCIATION2_ID);
        assertThat(advertiseFuture2.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION2_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isTrue();

        // Attach transport on ASSOCIATION1_ID
        AndroidFuture<?> transportFuture1 =
                mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        assertThat(transportFuture1.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isTrue();

        // Simulate success for scanning on ASSOCIATION1_ID
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_NEARBY_SCANNING, true);
        assertThat(isFutureSucceeded(scanFuture1)).isTrue();
        assertThat(advertiseFuture2.isDone()).isFalse(); // Other futures should be unaffected
        assertThat(transportFuture1.isDone()).isFalse(); // Other futures should be unaffected

        // Simulate success for advertising on ASSOCIATION2_ID
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION2_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, true);
        assertThat(isFutureSucceeded(advertiseFuture2)).isTrue();
        assertThat(transportFuture1.isDone()).isFalse(); // Other futures should be unaffected

        // Simulate success for transport on ASSOCIATION1_ID
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, true);
        assertThat(isFutureSucceeded(transportFuture1)).isTrue();

        // Now stop some actions concurrently
        AndroidFuture<?> stopAdvertiseFuture2 =
                mCompanionActionController.stopNearbyAdvertising(ASSOCIATION2_ID);
        assertThat(stopAdvertiseFuture2.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION2_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isFalse();

        AndroidFuture<?> detachTransportFuture1 =
                mCompanionActionController.detachTransport(ASSOCIATION1_ID);
        assertThat(detachTransportFuture1.isDone()).isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isFalse();

        // Simulate success for stopping advertising on ASSOCIATION2_ID
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION2_ID, ActionRequest.REQUEST_NEARBY_ADVERTISING, false);
        assertThat(isFutureSucceeded(stopAdvertiseFuture2)).isTrue();
        assertThat(detachTransportFuture1.isDone()).isFalse(); // Other futures unaffected

        // Simulate success for detaching transport on ASSOCIATION1_ID
        mFakeCompanionDeviceManagerProxy.simulateActionResult(
                ASSOCIATION1_ID, ActionRequest.REQUEST_TRANSPORT, false);
        assertThat(isFutureSucceeded(detachTransportFuture1)).isTrue();
    }

    @Test
    public void destroy_deactivateAllRequests() {
        // Start scanning on ASSOCIATION1_ID
        mCompanionActionController.startNearbyScanning(ASSOCIATION1_ID);
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_SCANNING))
                .isTrue();
        // Start advertising on ASSOCIATION2_ID
        mCompanionActionController.startNearbyAdvertising(ASSOCIATION2_ID);
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION2_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isTrue();
        // Attach transport on ASSOCIATION1_ID
        mCompanionActionController.attachTransport(ASSOCIATION1_ID);
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isTrue();

        // Destroy
        mCompanionActionController.destroy();

        // No more requests should be active
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_NEARBY_SCANNING))
                .isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION2_ID,
                                ActionRequest.REQUEST_NEARBY_ADVERTISING))
                .isFalse();
        assertThat(
                        mFakeCompanionDeviceManagerProxy.hasActionRequest(
                                CompanionActionControllerImpl.SERVICE_NAME,
                                ASSOCIATION1_ID,
                                ActionRequest.REQUEST_TRANSPORT))
                .isFalse();
    }

    private AssociationInfo createAssociationInfo(int id) {
        return new AssociationInfo.Builder(id, mContext.getUserId(), mContext.getPackageName())
                .setDisplayName("displayName" + id)
                .build();
    }
}
