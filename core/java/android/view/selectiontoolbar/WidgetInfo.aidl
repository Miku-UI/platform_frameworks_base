/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.view.selectiontoolbar;

import android.graphics.Rect;
import android.view.SurfaceControlViewHost;

/**
 * @hide
 */
parcelable WidgetInfo {

    /**
     * A unique sequence number for the showToolbar request.
     */
    int sequenceNumber;

    /**
     * A Rect that defines the size and positioning of the remote view with respect to
     * its host window.
     */
    Rect contentRect;

    /**
     * The SurfacePackage pointing to the remote view.
     */
    SurfaceControlViewHost.SurfacePackage surfacePackage;
}
