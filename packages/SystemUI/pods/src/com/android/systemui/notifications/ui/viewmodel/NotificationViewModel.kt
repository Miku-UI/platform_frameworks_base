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

package com.android.systemui.notifications.ui.viewmodel

import android.graphics.drawable.Drawable

/** ViewModel representing the content of a notification view. */
public interface NotificationViewModel {
    /** Whether the notification should show its larger (expanded) content. */
    public val isExpanded: Boolean

    /**
     * The icon associated with the app that posted the notification, shown in a circle at the start
     * of the content.
     */
    public val appIcon: Drawable
    /** The "large icon" shown on the top end of the notification beside the expander. */
    public val largeIcon: Drawable?

    // TODO: b/431222735 - Make this nullable once we implement the top line fields.
    /** The title of the notification, emphasized in the content. */
    public val title: String?
    /** The content text of the notification, shown below the title. */
    public val text: String?

    /**
     * Fields that appear in the top line of the notification, in the order that they appear (when
     * present). Note that these have a built-in priority associated with them, so if we cannot fit
     * all of them in the available space, the lower priority ones may be shrunk or even hidden.
     */
    public val appName: String
    public val headerTextSecondary: String?
    public val headerText: String?
    public val verificationText: String?

    /** How many lines of text can be displayed when the notification is expanded. */
    public val maxLinesWhenExpanded: Int
    /** The maximum height of the notification. */
    public val maxHeightDp: Int
    /**
     * The maximum aspect ratio that the large icon supports. The height of the large icon is fixed,
     * so this determines its maximum width relative to that.
     */
    public val maxLargeIconAspectRatio: Float
}
