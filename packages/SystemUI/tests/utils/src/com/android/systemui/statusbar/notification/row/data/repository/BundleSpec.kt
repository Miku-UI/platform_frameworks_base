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

package com.android.systemui.statusbar.notification.row.data.repository

import com.android.internal.R
import com.android.systemui.statusbar.notification.collection.BundleSpec
import com.android.systemui.statusbar.notification.stack.BUCKET_RECS

val TEST_BUNDLE_SPEC =
    BundleSpec(
        key = "Test Bundle",
        titleText = R.string.recs_notification_channel_label,
        summaryText = R.string.redacted_notification_action_title,
        icon = R.drawable.ic_settings,
        bucket = BUCKET_RECS,
        bundleType = 0,
    )

val TEST_BUNDLE_SPEC_2 =
    BundleSpec(
        key = "Test Bundle 2",
        titleText = R.string.recs_notification_channel_label,
        summaryText = R.string.redacted_notification_action_title,
        icon = R.drawable.ic_settings,
        bucket = BUCKET_RECS,
        bundleType = 0,
    )
