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

package android.companion.virtual.computercontrol;

import android.graphics.Insets;
import android.hardware.input.VirtualTouchEvent;

/**
 * An interactive mirror of a computer control session display.
 *
 * @hide
 */
// TODO(b/432678187): Replace the permission check with an alternative
@RequiresNoPermission
oneway interface IInteractiveMirror {

    /** Sets whether the user can interact with the contents of the mirror. */
    void setInteractive(boolean interactive);

    /** Resizes the mirror. */
    void resize(int width, int height);

    /** Propagates insets provided in the mirror's coordinate space to the session display. */
    void updateInsets(in Insets insets);

    /** Closes this mirror display and the associated touchscreen. */
    void close();
}
