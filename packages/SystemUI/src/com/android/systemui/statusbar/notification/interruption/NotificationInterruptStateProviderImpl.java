/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.interruption;

import com.android.internal.logging.UiEvent;
import com.android.internal.logging.UiEventLogger;

/**
 * Provides heads-up and pulsing state for notification entries.
 */
public abstract class NotificationInterruptStateProviderImpl {

    public enum NotificationInterruptEvent implements UiEventLogger.UiEventEnum {
        @UiEvent(doc = "FSI suppressed for suppressive GroupAlertBehavior")
        FSI_SUPPRESSED_SUPPRESSIVE_GROUP_ALERT_BEHAVIOR(1235),

        @UiEvent(doc = "FSI suppressed for suppressive BubbleMetadata")
        FSI_SUPPRESSED_SUPPRESSIVE_BUBBLE_METADATA(1353),

        @UiEvent(doc = "FSI suppressed for requiring neither HUN nor keyguard")
        FSI_SUPPRESSED_NO_HUN_OR_KEYGUARD(1236),

        @UiEvent(doc = "HUN suppressed for old when")
        HUN_SUPPRESSED_OLD_WHEN(1237),

        @UiEvent(doc = "HUN snooze bypassed for potentially suppressed FSI")
        HUN_SNOOZE_BYPASSED_POTENTIALLY_SUPPRESSED_FSI(1269);

        private final int mId;

        NotificationInterruptEvent(int id) {
            mId = id;
        }

        @Override
        public int getId() {
            return mId;
        }
    }
    public static final long MAX_HUN_WHEN_AGE_MS = 24 * 60 * 60 * 1000;
}
