/*
 * Copyright 2025 The Android Open Source Project
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

package android.service.chooser;

import android.service.chooser.ITapToShareCallback;

/**
 * @hide
 */
oneway interface ITapToShareService {
    /** Registers a callback to receive events from the service. */
    void registerCallback(in ITapToShareCallback callback);

    /** Unregisters a callback from the service. */
    void unregisterCallback(in ITapToShareCallback callback);
}