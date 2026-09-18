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

import android.app.PendingIntent;
import android.companion.virtual.computercontrol.IComputerControlLifecycleCallback;
import android.companion.virtual.computercontrol.IInteractiveMirror;
import android.view.Surface;
import android.view.SurfaceControl;
import com.android.internal.os.IResultReceiver;

/**
 * Interface for computer control session management.
 *
 * @hide
 */
interface IComputerControlSession {

    /**
     * Initializes the computer control session by setting the callback to be notified about
     * computer control lifecycle changes, and configuring the Surface for the session.
     */
    void initialize(in IComputerControlLifecycleCallback listener, in Surface surface);

    /** Launches an application on the trusted virtual display. */
    void launchApplication(in String packageName, in String className);

    /** Hand over full control of the automation session to the user. */
    void handOverApplications();

    /* Injects a tap event into the trusted virtual display. */
    void tap(int x, int y);

    /* Injects a swipe event into the trusted virtual display. */
    void swipe(int fromX, int fromY, int toX, int toY);

    /** Injects a long press event into the trusted virtual display. */
    void longPress(int x, int y);

    /** Creates an interactive mirror of the session's virtual display. */
    IInteractiveMirror createInteractiveMirror(in IResultReceiver a11yEmbeddedConnectionReceiver,
            out SurfaceControl mirrorSurface);

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

    /** Attaches a notification to the session, to make it non-dismissable. */
    // TODO(b/483645569): Remove when agents start passing a notification as part of the
    // ComputerControlSessionParams.
    void attachNotificationInfo(int notificationId, in String notificationTag);

    /**
     * Sets the intent launched when the user wants to preview the automation, or null if none.
     *
     * <p>This overrides the intent set in {@link
     * ComputerControlSessionParams.Builder#setPreviewIntent}.
     */
    void setPreviewIntent(in @nullable PendingIntent previewIntent);

    /**
     * Request a screenshot to be taken of the virtual display, which forces a new frame to be
     * drawn during a backgrounded session. Screenshot requests should be serialized, and the client
     * should wait for the frame to be produced in the ImageReader following a successful request
     * before requesting another one.
     *
     * @return {@code true} if a screenshot request was successfully processed,
     *     {@code false} otherwise.
     */
    boolean requestScreenshot();

    /**
     * Notifies the system that a previous screenshot result has been resolved.
     */
    void notifyScreenshotResult();

    /**
     * Notifies the system that the caller is blocked and unable to perform any further
     * interactions in the session.
     */
    void notifyBlocked();

    /**
     * Attempts to exit the blocked state when the session is blocked for any reason. This should
     * be called when the user explicitly chooses to end their control of the session.
     */
    void requestUnblock();

    /** Closes this session. */
    void close();
}
