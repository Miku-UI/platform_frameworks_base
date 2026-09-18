/*
 * Copyright (C) 2026 The Android Open Source Project
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
 * Interface to listen for engagement control requests from the system.
 *
 * @hide
 */
oneway interface IEngagementControlRequestConsumer {
    /**
     * Called when an application dispatches an engagement control request.
     *
     * @param displayId The ID of the display the window is currently on.
     * @param taskId The task ID associated with the window, or -1 if not applicable.
     * @param engagementControlFlags A bitmask of requested engagement states.
     */
    void onEngagementControlRequest(int displayId, int taskId, int engagementControlFlags);

}
