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

import android.media.tv.extension.scan.IOperatorDetectionListener;
import android.os.Bundle;

/**
 * @hide
 */
interface IOperatorDetection {
    /**
     * Set the operator selected info for scanning.
     *
     * @param operatorSelected Bundle, must include keys in @ScanConstants.OperatorInfoBundleKey.
     * @return @ScanConstants.OpResult.RESULT_SUCCESS if set successfully else RESULT_FAILED.
     */
    int setOperatorDetection(in Bundle operatorSelected);
    /**
     * Set the listener to be invoked when one or more operator detection has been detected by
     * operator detection searches.
     *
     * @param listener IOperatorDetectionListener.
     * @return @ScanConstants.OpResult.RESULT_SUCCESS if set successfully else RESULT_FAILED.
     */
    int setListener(in IOperatorDetectionListener listener);
}
