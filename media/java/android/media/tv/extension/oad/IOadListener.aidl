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

package android.media.tv.extension.oad;

import android.os.Bundle;

/**
 * Listener for OAD (Over-the-Air Download) status and progress events.
 * Replaces the generic sessionEvent mechanism.
 * @hide
 */
oneway interface IOadListener {
    /**
     * Triggered when scan is started.
     * Replace EVENT_OAD_SCAN_START.
     */
    void onScanStarted();
    /**
     * Triggered when there is an update on the current scanning progress.
     * Replace EVENT_OAD_SCAN_PROGRESS.
     * @param progress 0-100 scan percentage.
     * @param frequency The current frequency being scanned.
     */
    void onScanProgress(int progress, int frequency);
    /**
     * Triggered when a file is found.
     * Replace EVENT_OAD_FILE_FOUND.
     * @param version The software version found.
     */
    void onFileFound(int version);
    /**
     * Triggered when no file is found after detection or scan is complete.
     */
    void onFileNotFound();
    /**
     * Triggered when discovered that no update needed.
     */
    void onSystemUpToDate();
    /**
     * Triggered when download has started.
     * Replace EVENT_OAD_DOWNLOAD_START.
     */
    void onDownloadStarted();
    /**
     * Triggered when there is an update on download progress.
     * Replace EVENT_OAD_DOWNLOAD_PROGRESS.
     * @param percent Global percentage (0-100).
     * @param receivedBytes Total bytes downloaded so far.
     * @param totalBytes Total expected size.
     */
    void onDownloadProgress(int percent, long receivedBytes, long totalBytes);
    /**
     * Triggered when the download finishes successfully.
     * This prepares the client to call applyUpgrade().
     *
     * @param url The location of the downloaded file.
     * @param offset   The payload offset.
     * @param size     The payload size.
     * @param properties Optional extra properties.
     */
    void onDownloadComplete(String url, long offset, long size, in Bundle properties);
    /**
     * Triggered when download is unsuccessful.
     */
    void onDownloadFail();
    /**
     * Triggered when upgrade is finished.
     * 0 for success, 1 for fail.
     */
    void onUpgradeResult(int resultCode);
}
