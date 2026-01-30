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

import android.hardware.input.VirtualTouchEvent;

/**
 * A display, mirroring a computer control session display, and its associated touchscreen.
 *
 * @hide
 */
oneway interface IInteractiveMirrorDisplay {

    /** Resize the mirror display and updates the associated touchscreen. */
    void resize(int width, int height);

    /** Injects a touch event into the mirror display. */
    void sendTouchEvent(in VirtualTouchEvent event);

    /** Closes this mirror display and the associated touchscreen. */
    void close();
}
