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

package android.window;

/**
 * Listener for changes in display engagement mode.
 *
 * @see android.view.WindowManager.DisplayEngagementModeCallback
 * @hide
 */
oneway interface IDisplayEngagementModeCallback {
    /**
     * Called when the display engagement mode changes.
     * @param displayId The ID of the display whose engagement mode changed.
     * @param engagementMode The new engagement mode.
     */
    void onEngagementModeChanged(int displayId, int engagementMode);
}
