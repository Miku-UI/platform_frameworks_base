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

import android.media.tv.extension.scan.ITkgsInfoListener;
import android.os.Bundle;

/**
 * @hide
 */
interface ITkgsInfo {
    /**
    * TKGS operator sets prefer service list, the service list is selected by End-User.
    *
    * @param prefServiceList the service list selected by end-user.
    * @return @ScanConstants.OpResult.RESULT_SUCCESS if successfully sets else RESULT_FAILED.
    */
    int setPrefServiceList(String prefServiceList);
    /**
    * Set Tkgs info listener.
    *
    * @param listener ITkgsInfoListener.
    * @return @ScanConstants.OpResult.RESULT_SUCCESS if successfully sets else RESULT_FAILED.
    */
    int setTkgsInfoListener(in ITkgsInfoListener listener);
}
