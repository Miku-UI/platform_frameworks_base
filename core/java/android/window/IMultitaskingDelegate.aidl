/**
 * Copyright (c) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.window;

import android.os.IBinder;
import android.content.Intent;

/**
 * System private API for requesting actions and configurations related to some multi-window
 * features and modes like Bubbles.
 * @hide
 */
interface IMultitaskingDelegate {
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.REQUEST_SYSTEM_MULTITASKING_CONTROLS)")
    oneway void createBubble(in IBinder token, in Intent intent, boolean collapsed);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.REQUEST_SYSTEM_MULTITASKING_CONTROLS)")
    oneway void updateBubbleState(in IBinder token, boolean collapsed);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.REQUEST_SYSTEM_MULTITASKING_CONTROLS)")
    oneway void updateBubbleMessage(in IBinder token, String message);

    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.REQUEST_SYSTEM_MULTITASKING_CONTROLS)")
    oneway void removeBubble(in IBinder token);
}
