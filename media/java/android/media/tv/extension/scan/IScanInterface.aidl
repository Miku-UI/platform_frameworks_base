/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.media.tv.extension.scan;

import android.media.tv.extension.scan.IScanListener;
import android.media.tv.extension.scan.IScanGlobalListener;
import android.os.Bundle;

/**
 * @hide
 */
interface IScanInterface {
    /**
     * Create scan session.
     *
     * @param broadcastType @ScanConstants.BroadcastType broadcast type, such as ATSC.
     * @param countryCode countryCode based on ISO 3166-1 alpha-3.
     * @param operator values based on @TvExtensionContract.TunerOperators.OperatorType.
     * @param listener ScanListener listens for updates.
     * @param optionalParams @ScanConstants.ScanBundleKey other optional scan parameters.
     * @return IBinder of IScanSession.
     */
    IBinder createSession(int broadcastType, String countryCode, String operator,
        in IScanListener listener, in Bundle optionalParams);
    /**
     * Get parameters, such as quick scan default parameters
     *
     * @param broadcastType @ScanConstants.BroadcastType broadcast type, such as ATSC.
     * @param countryCode countryCode based on ISO 3166-1 alpha-3.
     * @param operator values based on @TvExtensionContract.TunerOperators.OperatorType.
     * @param listener ScanListener listens for updates.
     * @param params @ScanConstants.ScanBundleKey specify the type of parameters to be acquired.
     * @return Bundle with keys the same as params.
     */
    Bundle getParameters(int broadcastType, String countryCode, String operator, in Bundle params);
    /**
     * Register a global listener that notifies the client scan events anywhere in the system.
     *
     * @param listener IScanGlobalListener to be registered.
     */
    void registerScanGlobalListener(in IScanGlobalListener listener);
    /**
     * Unregister a global listener that notifies the client scan events anywhere in the system.
     *
     * @param listener IScanGlobalListener to be unregistered.
     */
    void unregisterScanGlobalListener(in IScanGlobalListener listener);
    /**
     * Launches the system menu for channel configuration.
     * (Maps to: Settings -> Channels & Inputs -> Channels).
     */
    void showScanSettings();
    /**
     * Launches the OEM Scan UI for a specific action.
     * The behavior of the UI (e.g., starting a new scan, resolving conflicts,
     * showing background progress) is determined by the actionId.
     *
     * @param actionId @ScanConstants.ScanAction The specific UI flow to trigger.
     */
    void launchScanAction(int actionId);
}
