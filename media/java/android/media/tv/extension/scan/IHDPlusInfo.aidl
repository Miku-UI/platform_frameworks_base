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

/**
 * @hide
 */
interface IHDPlusInfo {
    /**
     * Specifying a HDPlusInfo and start a network scan. If an error occurs,
     * IScanListener.onScanCompleted() is invoked and SCAN_RESULT_FAILED is notified.
     *
     * @param isBlindScanContinue true if continue blind scan.
     * @param isHDMode true if it is HDMode.
     * @return @ScanConstants.OpResult.RESULT_SUCCESS if set successfully else RESULT_FAILED.
     */
    int setHDPlusInfo(boolean isBlindScanContinue, boolean isHDMode);
}
