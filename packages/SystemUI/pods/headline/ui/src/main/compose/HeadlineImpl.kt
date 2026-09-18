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

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.approachLayout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrain
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.viewinterop.AndroidView
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.SceneTransitionLayout
import com.android.compose.animation.scene.SceneTransitionLayoutState
import com.android.compose.animation.scene.Swipe
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.compose.modifiers.thenIf
import com.android.systemui.common.ui.compose.Icon
import com.android.systemui.common.ui.compose.load
import com.android.systemui.headline.ui.viewmodel.HeadlineItem
import com.android.systemui.headline.ui.viewmodel.HeadlineItemContent
import com.android.systemui.headline.ui.viewmodel.HeadlineViewModel
import com.android.systemui.headline.ui.viewmodel.HeadlineViewModel.Companion.GoneScene
import com.android.systemui.headline.ui.viewmodel.toHeadlineItemKey
import com.android.systemui.statusbar.chips.ui.compose.neverDecreaseWidth
import com.android.systemui.statusbar.chips.ui.viewmodel.formatTimeRemainingData
import com.android.systemui.statusbar.chips.ui.viewmodel.rememberChronometerState
import com.android.systemui.statusbar.chips.ui.viewmodel.rememberTimeRemainingState
import com.android.systemui.statusbar.chips.ui.viewmodel.toFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Implementation of the [Headline] composer. */
public class HeadlineImpl @Inject constructor() : Headline {
    @Composable
    override fun Content(viewModel: HeadlineViewModel, modifier: Modifier) {
        Headline(viewModel, modifier = modifier.height(HeadlineHeight))
    }
}

/** The top-level composable for the Headline UI. */
@Composable
public fun Headline(viewModel: HeadlineViewModel, modifier: Modifier = Modifier) {
    CompositionLocalProvider(
        LocalContentColor provides Color.White,
        LocalTextStyle provides MaterialTheme.typography.labelMedium,
    ) {
        val items = viewModel.items
        SceneTransitionLayout(
            state = viewModel.state,
            transitions = HeadlineTransitions,
            modifier =
                modifier.onPlaced {
                    viewModel.uiBounds = Rect(it.positionInRoot(), it.size.toSize())
                },
            debugName = "Headline",
        ) {
            scene(GoneScene) { GoneScene() }

            items.forEachIndexed { i, item ->
                val previousItem = i.takeIf { it > 0 }?.let { items[it - 1] }
                val nextItem = i.takeIf { it < items.lastIndex }?.let { items[it + 1] }

                scene(
                    key = item.key.toSceneKey(),
                    userActions = userActions(previousItem, nextItem),
                ) {
                    HeadlineItemScene(
                        item,
                        previousItem,
                        nextItem,
                        viewModel::onItemClicked,
                        viewModel::iconView,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentScope.GoneScene(modifier: Modifier = Modifier) {
    // Find the last item that is animated from/to the Gone scene, so that we can tag the dark
    // circle using that item pill key and get the shared transition animation.
    val lastGoneTransition = layoutState.currentTransitions.lastOrNull { it.isGoneTransition() }
    val otherContent =
        lastGoneTransition?.toContent?.takeIf { it != GoneScene } ?: lastGoneTransition?.fromContent
    val elementKey = otherContent?.let { it as SceneKey }?.toHeadlineItemKey()?.toPillElementKey()

    // Draw the largest possible circle that is centered and fits the max parent constraints.
    Box(modifier.fillMaxHeight()) {
        Box(
            Modifier.align(Alignment.Center)
                .thenIf(elementKey != null) { Modifier.element(elementKey!!) }
                .size(GoneSceneSize)
                .drawBehind { drawCircle(Color.Black) }
        )
    }
}

@Composable
private fun ContentScope.HeadlineItemScene(
    item: HeadlineItem,
    previousItem: HeadlineItem?,
    nextItem: HeadlineItem?,
    onItemClicked: (HeadlineItem) -> Unit,
    iconViewStore: (key: String) -> ImageView?,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Box(Modifier.align(Alignment.Center).padding(horizontal = DotIndicatorOffset)) {
            // Dot indicator before the pill.
            DotIndicator(
                modifier = Modifier.align(Alignment.CenterStart).offset(-DotIndicatorOffset),
                item = previousItem,
                onClick = onItemClicked,
            )

            // Dot indicator after the pill.
            DotIndicator(
                modifier = Modifier.align(Alignment.CenterEnd).offset(DotIndicatorOffset),
                item = nextItem,
                onClick = onItemClicked,
            )

            // Pill.
            Box(
                // TODO(b/449675581): Use Modifier.expandable() instead once it is public.
                modifier =
                    Modifier.element(item.key.toPillElementKey())
                        .clip(CircleShape)
                        .clickable { onItemClicked(item) }
                        .background(color = Color.Black)
            ) {
                HeadlinePill(
                    startContent = {
                        HeadlineItemContents(
                            key = item.key.toStartContentElementKey(),
                            iconViewStore = iconViewStore,
                            contents = item.startContents,
                        )
                    },
                    endContent = {
                        HeadlineItemContents(
                            key = item.key.toEndContentElementKey(),
                            iconViewStore = iconViewStore,
                            contents = item.endContents,
                            isReversed = true,
                        )
                    },
                )
            }
        }
    }
}

private fun userActions(
    previousItem: HeadlineItem?,
    nextItem: HeadlineItem?,
): Map<UserAction, UserActionResult> {
    if (previousItem == null && nextItem == null) {
        return emptyMap()
    }

    return buildMap {
        previousItem?.let { put(Swipe.End, UserActionResult(it.key.toSceneKey())) }
        nextItem?.let { put(Swipe.Start, UserActionResult(it.key.toSceneKey())) }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ContentScope.DotIndicator(
    item: HeadlineItem?,
    onClick: (HeadlineItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun targetValue(isShown: Boolean) = if (isShown) 1f else 0f

    // Make sure that the indicator never instantly (dis)appears, even when the STL is idle. This
    // makes sure that the indicator nicely animates out when the live activity next to the current
    // one is removed.
    val isShown = item != null
    val alpha = remember { Animatable(targetValue(isShown)) }
    val scale = remember { Animatable(targetValue(isShown)) }
    val motionScheme = MaterialTheme.motionScheme
    LaunchedEffect(isShown) {
        val targetValue = targetValue(isShown)
        launch { alpha.animateTo(targetValue, motionScheme.defaultEffectsSpec()) }
        scale.animateTo(targetValue, motionScheme.defaultSpatialSpec())
    }

    Box(
        modifier
            .thenIf(item != null) {
                Modifier.element(item!!.key.toPillElementKey()).clickable { onClick(item) }
            }
            .size(DotIndicatorSize)
            .drawBehind {
                drawCircle(
                    color = Color.Black,
                    radius = size.minDimension / 2f * scale.value,
                    alpha = alpha.value,
                )
            }
    )
}

@Composable
private fun HeadlinePill(
    startContent: @Composable () -> Unit,
    endContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(modifier = modifier, contents = listOf(startContent, endContent)) {
        measurables,
        constraints ->
        check(measurables.size == 2) { "The list passed to Layout.contents should have 2 contents" }
        check(measurables[0].size == 1) { "HeadlinePill.startContent should emit exactly one node" }
        check(measurables[1].size == 1) { "HeadlinePill.endContent should emit exactly one node" }
        check(constraints.hasBoundedWidth) { "HeadlinePill should have a maxWidth" }
        check(constraints.hasBoundedHeight) { "HeadlinePill should have a maxHeight" }

        val horizontalPadding = 8.dp.roundToPx()
        val centerSize = constraints.maxHeight
        val maxChildWidth =
            ((constraints.maxWidth - centerSize) / 2 - horizontalPadding).coerceAtLeast(0)
        val minChildWidth =
            (centerSize - DotIndicatorSize.roundToPx() / 2).coerceIn(0, maxChildWidth)
        val childConstraints =
            Constraints(minWidth = minChildWidth, maxWidth = maxChildWidth, maxHeight = centerSize)
        val startPlaceable = measurables[0][0].measure(childConstraints)
        val endPlaceable = measurables[1][0].measure(childConstraints)

        val width =
            centerSize + horizontalPadding * 2 + maxOf(startPlaceable.width, endPlaceable.width) * 2

        layout(width, centerSize) {
            startPlaceable.placeRelative(
                horizontalPadding,
                (centerSize - startPlaceable.height) / 2,
            )
            endPlaceable.placeRelative(
                width - endPlaceable.width - horizontalPadding,
                (centerSize - endPlaceable.height) / 2,
            )
        }
    }
}

@Composable
private fun ContentScope.HeadlineItemContents(
    key: ElementKey,
    contents: List<HeadlineItemContent>,
    iconViewStore: (key: String) -> ImageView?,
    modifier: Modifier = Modifier,
    isReversed: Boolean = false,
) {
    Row(
        modifier = modifier.noResizeContentDuringTransitions(layoutState, isReversed).element(key),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isReversed) Arrangement.End else Arrangement.Start,
    ) {
        contents.forEachIndexed { i, content ->
            when (content) {
                is HeadlineItemContent.IconItem -> {
                    Icon(content.icon)
                }
                is HeadlineItemContent.ImageViewItem -> {
                    StatusBarIcon { iconViewStore(content.iconKey) }
                }
                is HeadlineItemContent.TextBasedContent -> {
                    TextBasedHeadlineItem(
                        content = content,
                        isReversed = isReversed,
                        isFirst = i == 0,
                        isLast = i == contents.lastIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun TextBasedHeadlineItem(
    content: HeadlineItemContent.TextBasedContent,
    isReversed: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    // Ensure that there is a minimum spacing between a Text and its siblings.
    val textModifier =
        when {
            isReversed && !isLast -> modifier.padding(end = 4.dp)
            !isReversed && !isFirst -> modifier.padding(start = 4.dp)
            else -> modifier
        }
    val density = LocalDensity.current
    val locale: Locale? = LocalConfiguration.current.locales[0]

    when (content) {
        is HeadlineItemContent.TextBasedContent.TextItem -> {
            content.text.load()?.let {
                Text(
                    modifier = textModifier,
                    text = it,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        is HeadlineItemContent.TextBasedContent.ShortTimeDelta -> {
            val timeRemainingState =
                rememberTimeRemainingState(
                    futureTimeMillis = content.time,
                    timeSource = content.timeSource,
                )
            timeRemainingState.timeRemainingData?.let {
                val text = formatTimeRemainingData(it)
                Text(
                    modifier = textModifier.neverDecreaseWidth(density, locale, text.length),
                    text = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        is HeadlineItemContent.TextBasedContent.TimerItem -> {
            val timerState =
                rememberChronometerState(
                    chronometer = content.timer.value,
                    formatter = content.timer.format.toFormatter(),
                    timeSource = content.timer.timeSource,
                )
            timerState.currentTimeText?.let {
                Text(
                    modifier = textModifier.neverDecreaseWidth(density, locale, it.length),
                    text = it,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatusBarIcon(modifier: Modifier = Modifier, iconFactory: () -> ImageView?) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { _ ->
            // Use a wrapper frame layout so that we still return a view even if the icon is null
            val wrapperFrameLayout = FrameLayout(context)

            val icon = iconFactory.invoke()
            if (icon != null) {
                // If needed, remove the icon from its old parent (views can only be attached
                // to 1 parent at a time)
                (icon.parent as? ViewGroup)?.apply {
                    this.removeView(icon)
                    this.removeTransientView(icon)
                }
                wrapperFrameLayout.addView(icon)
            }

            wrapperFrameLayout
        },
    )
}

/**
 * A modifier that ensures that the content inside the pill is not resized during transitions, to
 * avoid text and icons shrinking because the pill container is getting smaller. This is better both
 * visually and performance wise, as it avoid unnecessarily remeasuring and reflowing text.
 */
private fun Modifier.noResizeContentDuringTransitions(
    layoutState: SceneTransitionLayoutState,
    isReversed: Boolean,
): Modifier {
    return approachLayout(isMeasurementApproachInProgress = { layoutState.isTransitioning() }) {
        measurable,
        constraints ->
        if (isLookingAhead) {
            return@approachLayout measurable.measure(constraints).run {
                layout(width, height) { place(0, 0) }
            }
        }

        val placeable = measurable.measure(lookaheadConstraints)
        check(placeable.width == lookaheadSize.width) {
            "HeadlineItemContents.width != lookaheadWidth"
        }
        check(placeable.height == lookaheadSize.height) {
            "HeadlineItemContents.height != lookaheadHeight"
        }
        val size = constraints.constrain(lookaheadSize)
        layout(size.width, size.height) {
            if (isReversed) {
                placeable.placeRelative(
                    size.width - placeable.width,
                    (size.height - lookaheadSize.height) / 2,
                )
            } else {
                placeable.placeRelative(0, (size.height - lookaheadSize.height) / 2)
            }
        }
    }
}

private val HeadlineHeight: Dp = 36.dp
private val DotIndicatorSize = 8.dp
private val DotIndicatorOffset = DotIndicatorSize * 1.5f
private val GoneSceneSize = 30.dp
