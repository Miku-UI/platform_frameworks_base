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

package com.android.systemui.headline.ui.compose

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.shared.model.Text
import com.android.systemui.headline.ui.preview.R
import com.android.systemui.headline.ui.viewmodel.FakeHeadlineItem
import com.android.systemui.headline.ui.viewmodel.FakeHeadlineViewModel
import com.android.systemui.headline.ui.viewmodel.HeadlineItem
import com.android.systemui.headline.ui.viewmodel.HeadlineItemContent
import com.android.systemui.headline.ui.viewmodel.HeadlineItemContent.TextBasedContent.TextItem
import com.android.systemui.headline.ui.viewmodel.HeadlineItemContent.TextBasedContent.TimerItem
import com.android.systemui.headline.ui.viewmodel.fakeHeadlineItems
import com.android.systemui.statusbar.chips.ui.model.Chronometer
import com.android.systemui.statusbar.chips.ui.model.EventTime
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import java.time.Duration

@Composable
@Preview
private fun HeadlinePreview() {
    // TODO(b/449675581): Use PlatformTheme {} once it works with previews or provide a new
    // PlatformThemeForPreviews {} composable.
    MaterialTheme { HeadlineScreen() }
}

@Composable
fun HeadlineScreen(items: List<HeadlineItem> = remember { fakeHeadlineItems() }) {
    @Composable
    fun rememberViewModel(
        currentItemIndex: Int? = 0,
        list: List<HeadlineItem> = items,
    ): FakeHeadlineViewModel = rememberViewModel(list, currentItemIndex)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // No item selected.
        FakeHeadline(rememberViewModel(currentItemIndex = null))

        // First item selected.
        FakeHeadline(rememberViewModel())

        // First item selected (RTL).
        WithReversedLayoutDirection { FakeHeadline(rememberViewModel()) }

        // Second item selected.
        FakeHeadline(rememberViewModel(currentItemIndex = 1))

        // Last item selected.
        FakeHeadline(rememberViewModel(currentItemIndex = items.lastIndex))

        // Icons and text.
        FakeHeadline(rememberViewModel(list = rememberListWithIconAndTextItem()))

        // Icons and text (RTL).
        WithReversedLayoutDirection {
            FakeHeadline(rememberViewModel(list = rememberListWithIconAndTextItem()))
        }

        // Only icons.
        FakeHeadline(rememberViewModel(list = rememberListWithIconOnlyItem()))

        // Only ImageViews
        FakeHeadline(rememberViewModel(list = rememberListWithImageViewOnlyItem()))

        // Only text.
        FakeHeadline(rememberViewModel(list = rememberListWithTextOnlyItem()))

        // TODO(b/488457771): Support internal resources from previews needed for ShortTimeDelta
        // Only timers.
        FakeHeadline(rememberViewModel(list = rememberListWithTimerOnlyItem()))

        // Long item (has correct max size).
        FakeHeadline(rememberViewModel(list = rememberListWithLongItem()))

        // Empty item (has correct minimum size).
        val listWithEmptyItems = List(2) { FakeHeadlineItem("empty$it", emptyList(), emptyList()) }
        FakeHeadline(rememberViewModel(list = remember { listWithEmptyItems }))

        // Small item at 100% swipe distance, to make sure that the min content width is big enough
        // that we don't have a weird overlap with the camera cutout.
        HeadlineWithTransitionAtProgress(
            items = listWithEmptyItems,
            fromIndex = 0,
            toIndex = 1,
            progress = 0f,
            previewProgress = 1f,
            isInitiatedByUserInput = true,
        )
    }
}

@Composable
internal fun rememberViewModel(
    list: List<HeadlineItem>,
    currentItemIndex: Int? = 0,
): FakeHeadlineViewModel {
    val context = LocalContext.current
    val icons = remember(context, list) { mutableMapOf<String, ImageView?>() }
    val iconViewStore: (key: String) -> ImageView? = { key: String ->
        icons.getOrPut(key) {
            val resId =
                when (key) {
                    "timer" -> R.drawable.timer
                    "phone" -> R.drawable.phone
                    "directions_car" -> R.drawable.directions_car
                    "music_note" -> R.drawable.music_note
                    else -> null
                }
            resId?.let {
                ImageView(context).apply {
                    setImageDrawable(ContextCompat.getDrawable(context, it))
                }
            }
        }
    }
    return remember(list) {
        FakeHeadlineViewModel(
            items = list,
            currentItem = currentItemIndex?.let { list[it] },
            iconViewStore = iconViewStore,
        )
    }
}

@Composable
fun FakeHeadline(viewModel: FakeHeadlineViewModel, modifier: Modifier = Modifier) {
    WithHeadlineScrim(viewModel, modifier) {
        WithFakeCameraCutout(Modifier.align(Alignment.Center)) {
            Headline(viewModel, Modifier.height(36.dp))
        }
    }
}

@Composable
private fun WithHeadlineScrim(
    viewModel: FakeHeadlineViewModel,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.height(40.dp).fillMaxWidth()) {
        Box(
            Modifier.fillMaxSize()
                .drawWithHeadlineScrim(viewModel)
                .background(MaterialTheme.colorScheme.primary)
        )

        content()
    }
}

@Composable
private fun WithFakeCameraCutout(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.drawWithContent {
            drawContent()
            drawCircle(Color.White, radius = size.minDimension / 3f)
        },
        content = content,
    )
}

@Composable
private fun WithReversedLayoutDirection(content: @Composable () -> Unit) {
    val layoutDirection =
        when (LocalLayoutDirection.current) {
            LayoutDirection.Ltr -> LayoutDirection.Rtl
            LayoutDirection.Rtl -> LayoutDirection.Ltr
        }
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection, content = content)
}

@Composable
private fun rememberListWithIconAndTextItem(): List<HeadlineItem> {
    return remember {
        buildList {
            add(
                FakeHeadlineItem(
                    "icons",
                    listOf(
                        HeadlineItemContent.IconItem(
                            Icon.Resource(
                                R.drawable.directions_car,
                                ContentDescription.Loaded(null),
                            )
                        ),
                        TextItem(Text.Loaded("4:42")),
                    ),
                    listOf(
                        TextItem(Text.Loaded("3 min")),
                        HeadlineItemContent.IconItem(
                            Icon.Resource(
                                R.drawable.directions_car,
                                ContentDescription.Loaded(null),
                            )
                        ),
                    ),
                )
            )
        }
    }
}

@Composable
private fun rememberListWithIconOnlyItem(): List<HeadlineItem> {
    return remember {
        buildList {
            val icons =
                listOf(
                        R.drawable.timer,
                        R.drawable.music_note,
                        R.drawable.phone,
                        R.drawable.directions_car,
                    )
                    .map {
                        HeadlineItemContent.IconItem(
                            Icon.Resource(it, ContentDescription.Loaded(null))
                        )
                    }
            add(FakeHeadlineItem("icons", icons.take(2), icons.takeLast(2)))
        }
    }
}

@Composable
private fun rememberListWithImageViewOnlyItem(): List<HeadlineItem> {
    return remember {
        buildList {
            val icons =
                listOf("timer", "music_note", "phone", "directions_car").map {
                    HeadlineItemContent.ImageViewItem(iconKey = it)
                }
            add(FakeHeadlineItem("icons", icons.take(2), icons.takeLast(2)))
        }
    }
}

@Composable
private fun rememberListWithTextOnlyItem(): List<FakeHeadlineItem> {
    return remember {
        listOf(
            FakeHeadlineItem(
                "texts",
                listOf("1", "2", "3").map { TextItem(Text.Loaded(it)) },
                listOf("c", "b", "a").map { TextItem(Text.Loaded(it)) },
            )
        )
    }
}

@Composable
private fun rememberListWithTimerOnlyItem(): List<FakeHeadlineItem> {
    return remember {
        listOf(
            FakeHeadlineItem(
                "timers",
                listOf(
                    TimerItem(
                        timer =
                            OngoingActivityChipModel.Content.Timer(
                                value = Chronometer.Running(EventTime.ElapsedRealtime(100L)),
                                timeSource = FakeSystemClock(),
                            )
                    )
                ),
                listOf(
                    TimerItem(
                        timer =
                            OngoingActivityChipModel.Content.Timer(
                                value = Chronometer.Paused(Duration.ofSeconds(15)),
                                timeSource = FakeSystemClock(),
                            )
                    )
                ),
            )
        )
    }
}

@Composable
private fun rememberListWithLongItem(): List<HeadlineItem> {
    return remember {
        buildList {
            add(
                FakeHeadlineItem(
                    key = "long",
                    startContents =
                        listOf(TextItem(Text.Loaded("A very very very loooooong text"))),
                    endContents = emptyList(),
                )
            )

            add(FakeHeadlineItem("empty", emptyList(), emptyList()))
        }
    }
}
