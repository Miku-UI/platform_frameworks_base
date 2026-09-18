/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.row.ui.viewbinder

import android.util.Log
import com.android.systemui.statusbar.notification.NmSummarizationAllFlag
import com.android.systemui.statusbar.notification.row.HybridConversationNotificationView
import com.android.systemui.statusbar.notification.row.HybridMetricNotificationView
import com.android.systemui.statusbar.notification.row.HybridNotificationView
import com.android.systemui.statusbar.notification.row.ui.viewmodel.SingleLineViewModel
import com.android.systemui.statusbar.notification.row.ui.viewmodel.SingleLineViewPayload

object SingleLineViewBinder {
    private const val TAG: String = "SingleLineViewBinder"

    @JvmStatic
    fun bind(viewModel: SingleLineViewModel?, view: HybridNotificationView?) {
        if (viewModel == null) {
            Log.wtf(TAG, "viewModel value is null.")
        }

        if (view == null) {
            Log.wtf(TAG, "view value is null.")
        }

        when (view) {
            is HybridMetricNotificationView -> {
                val metricData = (viewModel?.payload as? SingleLineViewPayload.MetricPayload)?.data
                if (viewModel != null && metricData == null) {
                    Log.wtf(
                        TAG,
                        "Payload is not for Notification.MetricStyle. " +
                            "It's ${viewModel.payload.javaClass.simpleName}",
                    )
                }

                view.bind(title = viewModel?.titleText, metric = metricData)
            }

            is HybridConversationNotificationView -> {
                val conversationData = viewModel?.payload as? SingleLineViewPayload.ConversationData

                conversationData?.avatar?.let { view.setAvatar(it) }
                if (NmSummarizationAllFlag.isEnabled) {
                    view.setText(
                        /* titleText = */ viewModel?.titleText,
                        /* contentText = */ viewModel?.contentText,
                        /* conversationSenderName = */ conversationData?.conversationSenderName,
                        /* summarization = */ viewModel?.summarization,
                    )
                } else {
                    view.setText(
                        /* titleText = */ viewModel?.titleText,
                        /* contentText = */ viewModel?.contentText,
                        /* conversationSenderName = */ conversationData?.conversationSenderName,
                        /* summarization = */ conversationData?.summarization,
                    )
                }
            }

            else -> {
                // bind the title and content text views
                view?.bind(
                    /* title = */ viewModel?.titleText,
                    /* text = */ viewModel?.contentText,
                    /* summarization = */ viewModel?.summarization,
                )
            }
        }
    }
}
