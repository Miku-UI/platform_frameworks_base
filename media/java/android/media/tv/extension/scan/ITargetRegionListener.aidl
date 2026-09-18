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
oneway interface ITargetRegionListener {
    /**
     * Notify listeners when two or more regions are detected during a service scan.
     *
     * @param detectTargetRegions bundle to notify if a target regions is detected, keys as defined
     *        but not limited to @ScanConstants.TargetRegionDetectBundleKey.
     */
    void onDetectTargetRegion(in Bundle detectTargetRegions);
}
