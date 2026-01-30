/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.settingslib.spa.widget.scaffold

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.lerp as lerpTextStyle
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.android.settingslib.spa.framework.theme.SettingsSpace
import kotlin.math.roundToInt

private val safeDrawingWindowInsets: WindowInsets
    @Composable
    @NonRestartableComposable
    get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)

/** The morphing title LargeTopAppBar for Settings. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MorphingTitleLargeTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior,
) {
    TwoRowsTopAppBar(
        titleText = title,
        expandedTextStyle =
            MaterialTheme.typography.displaySmallEmphasized.copy(textMotion = TextMotion.Animated),
        collapsedTextStyle =
            MaterialTheme.typography.titleLargeEmphasized.copy(textMotion = TextMotion.Animated),
        titleBottomPadding = LargeTitleBottomPadding,
        expandedTitleMaxLines = 2,
        collapsedTitleMaxLines = 1,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = topAppBarColors(),
        windowInsets = safeDrawingWindowInsets,
        pinnedHeight = ContainerHeight,
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun topAppBarColors() =
    TopAppBarColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.primary,
    )

/**
 * A two-rows top app bar that is designed to be called by the Large and Medium top app bar
 * composables.
 *
 * @throws [IllegalArgumentException] if the given [MaxHeight] is equal or smaller than the
 *   [pinnedHeight]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TwoRowsTopAppBar(
    modifier: Modifier = Modifier,
    titleText: String,
    expandedTextStyle: TextStyle,
    collapsedTextStyle: TextStyle,
    titleBottomPadding: Dp,
    expandedTitleMaxLines: Int,
    collapsedTitleMaxLines: Int,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    windowInsets: WindowInsets,
    colors: TopAppBarColors,
    pinnedHeight: Dp,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    if (MaxHeight <= pinnedHeight) {
        throw IllegalArgumentException(
            "A TwoRowsTopAppBar max height should be greater than its pinned height"
        )
    }
    val density = LocalDensity.current
    val pinnedHeightPx: Float = density.run { pinnedHeight.toPx() }
    val titleBottomPaddingPx: Int = density.run { titleBottomPadding.roundToPx() }
    val maxHeightPx = density.run { MaxHeight.toPx() }
    val heightOffsetLimit = pinnedHeightPx - maxHeightPx

    LaunchedEffect(heightOffsetLimit) { scrollBehavior.state.heightOffsetLimit = heightOffsetLimit }

    var hasCollapsedInitially by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(scrollBehavior) {
        if (!hasCollapsedInitially) {
            scrollBehavior.collapse()
            hasCollapsedInitially = true
        }
    }

    val collapsedFraction = scrollBehavior.state.collapsedFraction
    val appBarContainerColor = colors.containerColor(collapsedFraction)

    val actionsRow =
        @Composable {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }

    val appBarDragModifier =
        if (!scrollBehavior.isPinned) {
            Modifier.draggable(
                orientation = Orientation.Vertical,
                state =
                    rememberDraggableState { delta -> scrollBehavior.state.heightOffset += delta },
                onDragStopped = { velocity ->
                    settleAppBar(
                        scrollBehavior.state,
                        velocity,
                        scrollBehavior.flingAnimationSpec,
                        scrollBehavior.snapAnimationSpec,
                    )
                },
            )
        } else {
            Modifier
        }

    val currentAppBarHeightPx = maxHeightPx + scrollBehavior.state.heightOffset

    // Interpolated properties for the single title
    val interpolatedTextStyle =
        lerpTextStyle(expandedTextStyle, collapsedTextStyle, collapsedFraction)
    val navigationIconPaddingStartPx = density.run { SettingsSpace.small3.toPx() }
    val navigationIconPaddingEndPx = density.run { SettingsSpace.small1.toPx() }
    val expandedTitlePaddingStartPx = density.run { SettingsSpace.small4.toPx() }
    val currentMaxLines =
        if (collapsedFraction < 0.5f) expandedTitleMaxLines else collapsedTitleMaxLines

    Box(
        modifier =
            modifier
                .then(appBarDragModifier)
                .drawBehind { drawRect(color = appBarContainerColor) }
                .semantics { isTraversalGroup = true }
                .pointerInput(Unit) {}
    ) {
        Layout(
            content = {
                Box(Modifier.layoutId("navigationIcon")) {
                    CompositionLocalProvider(
                        LocalContentColor provides colors.navigationIconContentColor,
                        content = navigationIcon,
                    )
                }

                Box(Modifier.layoutId("title")) {
                    Title(
                        text = titleText,
                        color = colors.titleContentColor,
                        maxLines = currentMaxLines,
                        textStyle = interpolatedTextStyle,
                    )
                }

                Box(Modifier.layoutId("actionIcons").padding(end = TopAppBarHorizontalPadding)) {
                    CompositionLocalProvider(
                        LocalContentColor provides colors.actionIconContentColor,
                        content = actionsRow,
                    )
                }
            },
            modifier = Modifier.windowInsetsPadding(windowInsets).clipToBounds(),
        ) { measurables, constraints ->
            val navigationIconPlaceable =
                measurables
                    .first { it.layoutId == "navigationIcon" }
                    .measure(constraints.copy(minWidth = 0))
            val actionIconsPlaceable =
                measurables
                    .first { it.layoutId == "actionIcons" }
                    .measure(constraints.copy(minWidth = 0))

            val navigationIconWidth = navigationIconPlaceable.width
            val collapsedTitlePaddingStartPx =
                if (navigationIconWidth > 0) {
                    navigationIconPaddingStartPx + navigationIconWidth + navigationIconPaddingEndPx
                } else {
                    expandedTitlePaddingStartPx
                }
            val interpolatedPaddingStartPx =
                density.run {
                    lerp(
                        start = expandedTitlePaddingStartPx,
                        stop = collapsedTitlePaddingStartPx,
                        fraction = collapsedFraction,
                    )
                }
            val titleHorizontalPaddingPx =
                density.run { interpolatedPaddingStartPx + SettingsSpace.small1.toPx() }
            val titleMaxWidth =
                (constraints.maxWidth -
                        collapsedFraction * actionIconsPlaceable.width -
                        titleHorizontalPaddingPx)
                    .roundToInt()
                    .coerceAtLeast(0)

            val titlePlaceable =
                measurables
                    .first { it.layoutId == "title" }
                    .measure(Constraints.fixedWidth(titleMaxWidth))

            val layoutWidth = constraints.maxWidth
            val layoutHeight = currentAppBarHeightPx.roundToInt().coerceAtLeast(0)

            layout(layoutWidth, layoutHeight) {
                navigationIconPlaceable.placeRelative(
                    x = navigationIconPaddingStartPx.roundToInt(),
                    y = ((pinnedHeightPx - navigationIconPlaceable.height) / 2f).roundToInt(),
                )

                actionIconsPlaceable.placeRelative(
                    x = layoutWidth - actionIconsPlaceable.width,
                    y = ((pinnedHeightPx - actionIconsPlaceable.height) / 2f).roundToInt(),
                )

                // Calculate Y position for the title
                val titleYCollapsed = (pinnedHeightPx - titlePlaceable.height) / 2f

                // For expanded Y, consider titleBottomPadding.
                // Align the bottom of the title text box (titlePlaceable.height)
                // with titleBottomPaddingPx from the overall layoutHeight when fully expanded
                // (maxHeightPx).
                val titleYExpanded = maxHeightPx - titlePlaceable.height - titleBottomPaddingPx

                val interpolatedTitleY = lerp(titleYExpanded, titleYCollapsed, collapsedFraction)

                titlePlaceable.placeRelative(
                    x = interpolatedPaddingStartPx.roundToInt(),
                    y = interpolatedTitleY.roundToInt(),
                )
            }
        }
    }
}

@Composable
private fun Title(text: String, color: Color, maxLines: Int, textStyle: TextStyle) {
    CompositionLocalProvider(localDensityDisableFontScale()) {
        Text(
            text = text,
            modifier = Modifier.padding(end = SettingsSpace.small1).semantics { heading() },
            color = color,
            overflow = TextOverflow.Ellipsis,
            maxLines = maxLines,
            style = textStyle,
        )
    }
}

private val MaxHeight = 179.dp
private val LargeTitleBottomPadding = 28.dp
private val TopAppBarHorizontalPadding = 4.dp
