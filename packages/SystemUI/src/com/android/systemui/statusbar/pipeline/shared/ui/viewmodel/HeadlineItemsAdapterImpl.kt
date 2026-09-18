/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.statusbar.pipeline.shared.ui.viewmodel

import com.android.systemui.common.shared.model.Text
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent.DisplayAware
import com.android.systemui.headline.ui.viewmodel.HeadlineItem
import com.android.systemui.headline.ui.viewmodel.HeadlineItemContent
import com.android.systemui.headline.ui.viewmodel.HeadlineItemContent.TextBasedContent
import com.android.systemui.headline.ui.viewmodel.HeadlineItemKey
import com.android.systemui.headline.ui.viewmodel.HeadlineItemsAdapter
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel.Active
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel.ChipIcon
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel.Content
import com.android.systemui.statusbar.chips.ui.viewmodel.OngoingActivityChipsViewModel
import com.android.systemui.statusbar.notification.shared.StatusBarHeadline
import com.android.systemui.statusbar.pipeline.shared.domain.interactor.StatusBarVisibilityInteractor
import com.android.systemui.statusbar.pipeline.shared.ui.model.HeadlineItemImpl
import java.text.NumberFormat
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class HeadlineItemsAdapterImpl
@Inject
constructor(
    @DisplayAware statusBarVisibilityInteractor: StatusBarVisibilityInteractor,
    @DisplayAware private val ongoingActivityChipsViewModel: OngoingActivityChipsViewModel,
) : HeadlineItemsAdapter {

    private val canShowHeadline: Flow<Boolean> =
        if (StatusBarHeadline.isEnabled) {
            statusBarVisibilityInteractor.canShowOngoingActivityChips
        } else {
            flowOf(false)
        }

    override val headlineItems: Flow<List<HeadlineItem>> =
        combine(canShowHeadline, ongoingActivityChipsViewModel.chips, ::Pair).map {
            (canShowHeadline, chips) ->
            if (canShowHeadline) {
                chips.active.map {
                    HeadlineItemImpl(
                        key = HeadlineItemKey(it.key),
                        startContents = it.startContents(),
                        endContents = it.endContents(),
                    )
                }
            } else {
                emptyList()
            }
        }

    private companion object {
        fun Active.startContents(): List<HeadlineItemContent> {
            return icon?.toHeadlineIcon()?.let { listOf(it) } ?: emptyList()
        }

        fun Active.endContents(): List<HeadlineItemContent> {
            return content.toHeadlineContent()?.let { listOf(it) } ?: emptyList()
        }

        fun ChipIcon.toHeadlineIcon(): HeadlineItemContent {
            return when (this) {
                is ChipIcon.SingleColorIcon -> {
                    HeadlineItemContent.IconItem(impl)
                }

                is ChipIcon.StatusBarNotificationIcon -> {
                    HeadlineItemContent.ImageViewItem(notificationKey)
                }
            }
        }

        fun Content.toHeadlineContent(): HeadlineItemContent? {
            return when (this) {
                is Content.Countdown -> {
                    TextBasedContent.TextItem(
                        Text.Loaded(NumberFormat.getIntegerInstance().format(secondsUntilStarted))
                    )
                }

                is Content.ShortTimeDelta -> {
                    TextBasedContent.ShortTimeDelta(time, timeSource)
                }

                is Content.Timer -> {
                    TextBasedContent.TimerItem(this)
                }

                is Content.Text -> {
                    TextBasedContent.TextItem(Text.Loaded(text))
                }

                is Content.TextVariants -> {
                    // TODO(b/491453334): Support TextVariants
                    textVariants.firstOrNull()?.let { TextBasedContent.TextItem(Text.Loaded(it)) }
                }

                Content.IconOnly -> null
            }
        }
    }
}
