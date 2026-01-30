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

import android.os.Bundle;
import android.os.IBinder;
import android.content.Intent;
import android.window.IMultitaskingControllerCallback;
import android.window.IMultitaskingDelegate;

/**
 * System private API for interacting with the multi-tasking controller that allows applications to
 * request actions and configurations related to some multi-window features and modes implemented
 * in WM Shell through the client interface.
 * The client interface is not meant to serve as an implementation for public APIs. This is because
 * exposing information or functions related to specific windowing modes in the public SDK limits
 * the platform's ability to evolve the UX, and hurts app compatibility with existing or future
 * OEM system UX customizations.
 * Therefore, this interface is only meant to be used for experimental purposes.
 * The supported actions are functionally equivalent to SysUI interactions, so all delegate
 * methods require REQUEST_SYSTEM_MULTITASKING_CONTROLS permission.
 * @hide
 */
interface IMultitaskingController {
    /**
     * Method used by WMShell to set itself as the delegate that can respond to the app requests.
     * @return a callback used to notify the client about the changes in the managed windows.
     */
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)")
    IMultitaskingControllerCallback setMultitaskingDelegate(in IMultitaskingDelegate delegate);

    /**
     * Returns an instance of an interface for use by applications to make requests to the system.
     */
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.REQUEST_SYSTEM_MULTITASKING_CONTROLS)")
    @nullable IMultitaskingDelegate getClientInterface(in IMultitaskingControllerCallback callback);
}
