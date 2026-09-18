/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.dream.ui.composable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.android.systemui.dreams.ui.viewmodel.DreamEdgeSwipeViewModel
import com.android.systemui.res.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private object EdgeSwipeDimensions {
    val MaxTranslationX = 20.dp
    val ReleaseTranslationX = 70.dp
    val EdgeSafeMargin = 16.dp
    val PillPaddingHorizontal = 16.dp
    val PillPaddingVertical = 12.dp
    val PillItemSpacing = 8.dp
    val ChevronIconSize = 24.dp
}

private val ReleaseAlphaSpec = tween<Float>(durationMillis = 250)
private val ReleaseTransformSpec = spring<Float>(stiffness = Spring.StiffnessMediumLow)

@Composable
fun EdgeSwipeIndicator(viewModel: DreamEdgeSwipeViewModel, modifier: Modifier = Modifier) {
    val uiState = viewModel.uiState

    if (!uiState.isVisible && !uiState.isReleasing) {
        return
    }

    val isFromLeft = uiState.isFromLeft
    val isReleasing = uiState.isReleasing
    val isCommitted = uiState.isCommitted

    /**
     * We avoid using animateFloatAsState here as a performance optimization, to ensure we don't
     * recompose this entire composable when the progress changes, as the progress value can change
     * rapidly during a drag.
     */
    val translationX = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }

    val maxTransPx = with(LocalDensity.current) { EdgeSwipeDimensions.MaxTranslationX.toPx() }
    val releaseTransPx =
        with(LocalDensity.current) { EdgeSwipeDimensions.ReleaseTranslationX.toPx() }

    LaunchedEffect(isReleasing, isCommitted, isFromLeft) {
        if (!isReleasing) {
            trackDragProgress(
                viewModel = viewModel,
                isFromLeft = isFromLeft,
                maxTransPx = maxTransPx,
                translationX = translationX,
                alpha = alpha,
                scale = scale,
            )
        } else {
            animateToRestingPosition(
                isFromLeft = isFromLeft,
                isCommitted = isCommitted,
                maxTransPx = maxTransPx,
                releaseTransPx = releaseTransPx,
                velocityX = uiState.releaseVelocityX,
                translationX = translationX,
                alpha = alpha,
                scale = scale,
            )
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerHeightPx = constraints.maxHeight.toFloat()
        val edgeMarginPx = with(LocalDensity.current) { EdgeSwipeDimensions.EdgeSafeMargin.toPx() }

        val alignment =
            if (isFromLeft) {
                Alignment.TopStart
            } else {
                Alignment.TopEnd
            }

        SwipeIndicatorPill(
            isFromLeft = isFromLeft,
            title = uiState.targetDream?.title?.toString(),
            translationXProvider = { translationX.value },
            translationYProvider = { uiState.startY },
            containerHeightPx = containerHeightPx,
            edgeMarginPx = edgeMarginPx,
            alphaProvider = { alpha.value },
            scaleProvider = { scale.value },
            modifier = Modifier.align(alignment),
        )
    }
}

/**
 * Suspends and observes the high-frequency swipe progress, snapping the animation values instantly
 * to track the user's finger.
 */
private suspend fun trackDragProgress(
    viewModel: DreamEdgeSwipeViewModel,
    isFromLeft: Boolean,
    maxTransPx: Float,
    translationX: Animatable<Float, AnimationVector1D>,
    alpha: Animatable<Float, AnimationVector1D>,
    scale: Animatable<Float, AnimationVector1D>,
) {
    snapshotFlow { viewModel.swipeProgress }
        .collect { progress ->
            val currentAlpha = (progress * 1.5f).coerceIn(0f, 1f)
            val currentScale = progress.coerceIn(0.5f, 1f)
            val currentTransX =
                if (isFromLeft) {
                    progress * maxTransPx
                } else {
                    -progress * maxTransPx
                }

            alpha.snapTo(currentAlpha)
            scale.snapTo(currentScale)
            translationX.snapTo(currentTransX)
        }
}

/**
 * Animates the indicator to its final resting state (either committing the switch or cancelling the
 * gesture) concurrently.
 */
private suspend fun animateToRestingPosition(
    isFromLeft: Boolean,
    isCommitted: Boolean,
    maxTransPx: Float,
    releaseTransPx: Float,
    velocityX: Float,
    translationX: Animatable<Float, AnimationVector1D>,
    alpha: Animatable<Float, AnimationVector1D>,
    scale: Animatable<Float, AnimationVector1D>,
) = coroutineScope {
    val targetAlpha = 0f
    val targetScale = 1f
    val targetTransX =
        when {
            isCommitted ->
                if (isFromLeft) {
                    releaseTransPx
                } else {
                    -releaseTransPx
                }
            else ->
                if (isFromLeft) {
                    -maxTransPx
                } else {
                    maxTransPx
                }
        }

    launch { alpha.animateTo(targetAlpha, ReleaseAlphaSpec) }
    launch { scale.animateTo(targetScale, ReleaseTransformSpec) }
    launch {
        translationX.animateTo(
            targetValue = targetTransX,
            animationSpec = ReleaseTransformSpec,
            initialVelocity = velocityX,
        )
    }
}

@Composable
private fun SwipeIndicatorPill(
    isFromLeft: Boolean,
    title: String?,
    translationXProvider: () -> Float,
    translationYProvider: () -> Float,
    containerHeightPx: Float,
    edgeMarginPx: Float,
    alphaProvider: () -> Float,
    scaleProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .graphicsLayer {
                    translationX = translationXProvider()
                    // Show the indicator in the same vertical position where the user
                    // started the swipe, with validation to ensure the container doesn't
                    // go offscreen.
                    val idealY = translationYProvider() - (size.height / 2f)
                    val maxY = containerHeightPx - size.height - edgeMarginPx
                    translationY = idealY.coerceIn(edgeMarginPx, maxY)
                    alpha = alphaProvider()
                    val currentScale = scaleProvider()
                    scaleX = currentScale
                    scaleY = currentScale
                }
                .background(color = MaterialTheme.colorScheme.surfaceBright, shape = CircleShape)
                .padding(
                    horizontal = EdgeSwipeDimensions.PillPaddingHorizontal,
                    vertical = EdgeSwipeDimensions.PillPaddingVertical,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EdgeSwipeDimensions.PillItemSpacing),
    ) {
        val hasTitle = !title.isNullOrEmpty()

        if (!isFromLeft && hasTitle) {
            IndicatorLabel(title = title!!)
        }

        ChevronIcon(isFromLeft = isFromLeft)

        if (isFromLeft && hasTitle) {
            IndicatorLabel(title = title!!)
        }
    }
}

@Composable
private fun ChevronIcon(isFromLeft: Boolean, modifier: Modifier = Modifier) {
    val scaleXValue =
        if (isFromLeft) {
            -1f
        } else {
            1f
        }

    Icon(
        painter = painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        modifier =
            modifier
                .size(EdgeSwipeDimensions.ChevronIconSize)
                .scale(scaleX = scaleXValue, scaleY = 1f),
        tint = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun IndicatorLabel(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.labelLarge,
        modifier = modifier,
    )
}
