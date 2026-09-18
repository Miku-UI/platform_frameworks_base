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

import android.os.Bundle;

/**
 * @hide
 */
oneway interface IScanListener {
    /**
     * Notify events during scan.
     *
     * @param eventArgs event occurred, keys as defined in @ScanConstants.EventBundleKey.
     */
    void onEvent(in Bundle eventArgs);
    /**
     * Notify the scan progress.
     *
     * @param scanProgress scan progress.
     * @param scanProgressInfo bundle keys as defined in @ScanConstants.ScanProgressBundleKey.
     */
    void onScanProgress(String scanProgress, in Bundle scanProgressInfo);
    /**
     * Notify the scan completion.
     *
     * @param @ScanConstants.ScanResult.SUCCESS/FAILED/CANCEL/BUSY depending on the scan result.
     * @param optionInfo optional bundle to pass in extra information, null if not needed.
     */
    void onScanCompleted(int scanResult, in Bundle optionInfo);
    /**
     * Notify that the temporaily held channel list is stored.
     *
     * @param @ScanConstants.StoreResult.SUCCESS/FAILED/BUSY depending on the store result.
     */
    void onStoreCompleted(int storeResult);
}
