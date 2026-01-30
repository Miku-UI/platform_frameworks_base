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

package com.android.systemui.topui

import android.app.IActivityManager

/**
 * Contract for components needing to signal that the SystemUI process requires higher priority for
 * computing resources during specific operations.
 *
 * This is primarily used during animations or transitions to ensure the SystemUI has sufficient
 * resources to render smoothly alongside the component's operations, thus preventing visual jank.
 *
 * Requests made via [setRequestTopUi] grant this temporary priority boost by calling
 * [IActivityManager.setHasTopUi]. Requests MUST be explicitly released upon completion to restore
 * normal scheduling.
 */
interface TopUiController {

    /**
     * Requests or releases higher computing resource priority for the SystemUI process.
     *
     * Call with `requestTopUi = true` before initiating an operation (e.g., animation) that might
     * cause jank if SystemUI doesn't have prioritized access to computing resources.
     *
     * **Crucially**, call again with `requestTopUi = false` using the same [componentTag]
     * immediately after the operation finishes. This releases the priority boost and allows
     * SystemUI scheduling to return to normal.
     *
     * Failure to release can leave SystemUI with elevated priority unnecessarily.
     *
     * @param requestTopUi `true` to request higher priority for SystemUI resources, `false` to
     *   release the request.
     * @param componentTag Unique caller ID. Must be identical for the `true` (request) and `false`
     *   (release) calls. Used to manage concurrent requests.
     */
    fun setRequestTopUi(requestTopUi: Boolean, componentTag: String) {}
}
