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

import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.IBinder;
import android.view.selectiontoolbar.ToolbarMenuItem;

import java.util.List;

/**
 * @hide
 */
parcelable ShowInfo {

    /**
     * A unique sequence number for the showToolbar request.
     */
    int sequenceNumber;

    /**
     * If the toolbar menu items need to be re-layout.
     */
    boolean layoutRequired;

    /**
     * The menu items to be rendered in the selection toolbar.
     */
    List<ToolbarMenuItem> menuItems;

    /**
     * A rect specifying where the selection toolbar on the screen.
     */
    Rect contentRect;

    /**
     * A recommended maximum suggested width of the selection toolbar.
     */
    int suggestedWidth;

    /**
     * The portion of the screen that is available to the selection toolbar.
     */
    Rect viewPortOnScreen;

    /**
     * The host application's input token, this allows the remote render service to transfer
     * the touch focus to the host application.
     */
    IBinder hostInputToken;

    /**
     * If the host application uses light theme.
     */
    boolean isLightTheme;

    /**
     * Configuration used by the context that created the toolbar.
     */
    Configuration configuration;
}
