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

package com.android.systemui.statusbar.notification.row;

import com.android.internal.logging.UiEvent;
import com.android.internal.logging.UiEventLogger;

enum NotificationFeedbackEvent implements UiEventLogger.UiEventEnum {
    @UiEvent(doc = "User clicked on feedback on a notification bundle")
    NOTIFICATION_FEEDBACK_BUNDLE(2280),

    @UiEvent(doc = "User clicked on feedback on a conversation notification")
    NOTIFICATION_FEEDBACK_CONVERSATION(2281);

    private final int mId;
    NotificationFeedbackEvent(int id) {
        mId = id;
    }

    @Override public int getId() {
        return mId;
    }
}
