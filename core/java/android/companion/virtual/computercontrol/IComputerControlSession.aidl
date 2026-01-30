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

import android.companion.virtual.computercontrol.IComputerControlStabilityListener;
import android.companion.virtual.computercontrol.IInteractiveMirrorDisplay;
import android.hardware.input.VirtualKeyEvent;
import android.hardware.input.VirtualTouchEvent;
import android.view.Surface;

/**
 * Interface for computer control session management.
 *
 * @hide
 */
interface IComputerControlSession {

    /** Launches an application on the trusted virtual display. */
    void launchApplication(in String packageName);

    /** Hand over full control of the automation session to the user. */
    void handOverApplications();

    /* Injects a tap event into the trusted virtual display. */
    void tap(int x, int y);

    /* Injects a swipe event into the trusted virtual display. */
    void swipe(int fromX, int fromY, int toX, int toY);

    /** Injects a long press event into the trusted virtual display. */
    void longPress(int x, int y);

    /** Returns the ID of the single trusted virtual display for this session. */
    int getVirtualDisplayId();

    /** Injects a key event into the trusted virtual display. */
    void sendKeyEvent(in VirtualKeyEvent event);

    /** Injects a touch event into the trusted virtual display. */
    void sendTouchEvent(in VirtualTouchEvent event);

    /** Creates an interactive virtual display, mirroring the trusted one. */
    IInteractiveMirrorDisplay createInteractiveMirrorDisplay(
            int width, int height, in Surface surface);

    /**
     * Inserts text into the current active input connection. If there is no active input
     * connection, this method is no-op.
     *
     * @param text to be inserted
     * @param replaceExisting whether the existing text in the input field should be replaced. If
     *                        {@code false}, we will insert the text the current cursor position.
     * @param commit whether the text should be submitted after insertion
     */
    void insertText(in String text, boolean replaceExisting, boolean commit);

    /** Performs computer control action on the computer control display. */
    void performAction(int actionCode);

    /** Sets a listener to be notified when the computer control session is potentially stable. */
    void setStabilityListener(in IComputerControlStabilityListener listener);

    /** Closes this session. */
    void close();
}
