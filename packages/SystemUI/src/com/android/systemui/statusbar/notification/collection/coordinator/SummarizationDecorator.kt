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

package com.android.systemui.statusbar.notification.collection.coordinator

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ImageSpan
import com.android.internal.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.coordinator.shared.NotificationSummarizationAllowAnimation
import javax.inject.Inject

class SummarizationDecorator @Inject constructor(@ShadeDisplayAware private val context: Context) {
    fun decorateSummarization(entry: NotificationEntry, existingIcon: Drawable?): Drawable? {
        val iconRes =
            if (NotificationSummarizationAllowAnimation.isEnabled)
                context.getDrawable(
                    com.android.systemui.res.R.drawable.generic_noti_summarization_animated
                )
            else context.getDrawable(com.android.internal.R.drawable.ic_notification_summarization)
        val icon = existingIcon ?: iconRes?.mutate()
        val imageSpan =
            icon?.let {
                it.setBounds(
                    /* left= */ 0,
                    /* top= */ 0,
                    icon.getIntrinsicWidth(),
                    icon.getIntrinsicHeight(),
                )
                ImageSpan(it, ImageSpan.ALIGN_CENTER)
            }
        val decoratedSummary =
            SpannableStringBuilder()
                .append("  ", imageSpan, 0)
                .append(" ")
                .append(SpannableString(entry.summarization))
        entry.sbn.notification.extras.putCharSequence(
            Notification.EXTRA_SUMMARIZED_CONTENT,
            decoratedSummary,
        )
        return icon
    }
}
