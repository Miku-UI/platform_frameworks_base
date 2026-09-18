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
package android.app;

import static com.android.ravenwood.common.RavenwoodInternalUtils.TAG;

import android.util.Log;

class ApplicationPackageManager_ravenwood {
    // TODO: Support features with RavenwoodRule.
    static boolean hasSystemFeature(ApplicationPackageManager p, String name, int version) {
        Log.w(TAG, "hasSystemFeature: " + name);
        return true;
    }
}
