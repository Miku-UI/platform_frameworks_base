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

package com.android.systemui.statusbar.pipeline.mobile.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.systemui.Flags
import com.android.systemui.common.ui.compose.load
import com.android.systemui.res.R
import com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.MobileIconViewModelCommon

/** Composable for displaying a single mobile icon. */
@Composable
fun MobileIcon(viewModel: MobileIconViewModelCommon, modifier: Modifier = Modifier) {
    val isVisible by viewModel.isVisible.collectAsStateWithLifecycle()

    if (!isVisible) return

    val icon by viewModel.icon.collectAsStateWithLifecycle(initialValue = SignalIconModel.DEFAULT)
    if (icon !is SignalIconModel.Cellular) return

    val contentDescription by
        viewModel.contentDescription.collectAsStateWithLifecycle(initialValue = null)
    val networkTypeIcon by
        viewModel.networkTypeIcon.collectAsStateWithLifecycle(initialValue = null)
    val roaming by viewModel.roaming.collectAsStateWithLifecycle(initialValue = false)
    val activityInVisible by
        viewModel.activityInVisible.collectAsStateWithLifecycle(initialValue = false)
    val activityOutVisible by
        viewModel.activityOutVisible.collectAsStateWithLifecycle(initialValue = false)
    val activityContainerVisible by
        viewModel.activityContainerVisible.collectAsStateWithLifecycle(initialValue = false)
    val context = LocalContext.current
    val contentColor = LocalContentColor.current
    val spacing = with(LocalDensity.current) { MobileIconDimensions.IconSpacingSp.toDp() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier.semantics {
                contentDescription?.let {
                    this.contentDescription = it.loadContentDescription(context)
                }
            },
    ) {
        if (activityContainerVisible) {
            Column {
                ActivityIndicators(
                    activityInVisible = activityInVisible,
                    activityOutVisible = activityOutVisible,
                    color = contentColor,
                )
            }
        }

        networkTypeIcon?.let { networkIcon ->
            val height = with(LocalDensity.current) { MobileIconDimensions.IconHeightSp.toDp() }
            Box(modifier = Modifier.height(height), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(networkIcon.resId),
                    contentDescription = networkIcon.contentDescription?.load(),
                    modifier = Modifier.height(height),
                    colorFilter = ColorFilter.tint(contentColor, BlendMode.SrcIn),
                    contentScale = ContentScale.FillHeight,
                )
            }
        }

        Spacer(Modifier.size(spacing))

        MobileSignalIcon(viewModel = icon as SignalIconModel.Cellular, color = contentColor)

        Spacer(Modifier.size(spacing))

        if (roaming) {
            val height =
                with(LocalDensity.current) { MobileIconDimensions.RoamingIconHeightSp.toDp() }
            val paddingTop =
                with(LocalDensity.current) { MobileIconDimensions.RoamingIconPaddingTopSp.toDp() }
            Image(
                painter = painterResource(R.drawable.stat_sys_roaming_updated),
                contentDescription = stringResource(R.string.data_connection_roaming),
                modifier = Modifier.height(height).offset(y = paddingTop),
                colorFilter = ColorFilter.tint(contentColor, BlendMode.SrcIn),
                contentScale = ContentScale.FillHeight,
            )
        }
    }
}

/** Composable for activity indicators (data in/out arrows) */
@Composable
fun ActivityIndicators(
    activityInVisible: Boolean,
    activityOutVisible: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val useStaticIndicators = Flags.statusBarStaticInoutIndicators()
    val activityIndicatorSize =
        with(LocalDensity.current) { MobileIconDimensions.ActivityIndicatorSizeSp.toDp() }
    Box(modifier = modifier.height(activityIndicatorSize + 8.dp).padding(bottom = 4.dp)) {
        Image(
            painter = painterResource(id = R.drawable.ic_activity_up),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color, BlendMode.SrcIn),
            contentScale = ContentScale.None,
            alignment = Alignment.TopEnd,
            alpha =
                if (useStaticIndicators) (if (activityInVisible) 1f else 0.3f)
                else if (activityInVisible) 1f else 0f,
            modifier =
                if (!useStaticIndicators && !activityInVisible) Modifier.size(0.dp) else Modifier,
        )
        Image(
            painter = painterResource(id = R.drawable.ic_activity_down),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color, BlendMode.SrcIn),
            contentScale = ContentScale.None,
            alignment = Alignment.BottomEnd,
            alpha =
                if (useStaticIndicators) (if (activityOutVisible) 1f else 0.3f)
                else if (activityOutVisible) 1f else 0f,
            modifier =
                if (!useStaticIndicators && !activityOutVisible) Modifier.size(0.dp) else Modifier,
        )
    }
}

/** Composable for rendering the mobile signal strength */
@Composable
private fun MobileSignalIcon(
    viewModel: SignalIconModel.Cellular,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val height = with(LocalDensity.current) { MobileIconDimensions.IconHeightSp.toDp() }

    val numberOfBars = viewModel.numberOfLevels - 1
    val dimensions =
        if (numberOfBars == 5) mobileSignalFiveBarsDimensions else mobileSignalFourBarsDimensions
    val width = with(LocalDensity.current) { dimensions.totalWidth.toDp() }

    Canvas(
        modifier.width(width).height(height).graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        }
    ) {
        val rtl = layoutDirection == LayoutDirection.Rtl
        scale(if (rtl) -1f else 1f, 1f) {
            val horizontalPaddingPx = dimensions.barsHorizontalPadding.roundToPx()
            val totalPaddingWidthPx = horizontalPaddingPx * (numberOfBars - 1)
            val barWidthPx = (size.width - totalPaddingWidthPx) / numberOfBars
            val baseBarHeightPx = dimensions.barBaseHeight.toPx()
            val levelIncrementPx = dimensions.barsLevelIncrement.toPx()

            var xOffsetPx = 0f
            for (bar in 1..numberOfBars) {
                val barHeightPx = baseBarHeightPx + (levelIncrementPx * (bar - 1))
                val barYOffsetPx = size.height - barHeightPx

                drawMobileSignalBar(
                    level = viewModel.level,
                    bar = bar,
                    topLeft = Offset(xOffsetPx, barYOffsetPx),
                    size = Size(barWidthPx, barHeightPx),
                    activeColor = color,
                )

                xOffsetPx += barWidthPx + horizontalPaddingPx
            }

            // Draw exclamation mark if needed
            if (viewModel.showExclamationMark) {
                drawSignalExclamationCutout(color)
            }
        }
    }
}

private fun DrawScope.drawMobileSignalBar(
    level: Int,
    bar: Int,
    topLeft: Offset,
    size: Size,
    activeColor: Color,
    inactiveColor: Color = activeColor.copy(alpha = .3f),
    cornerRadius: CornerRadius = CornerRadius(size.width / 2),
) {
    drawRoundRect(
        color = if (level >= bar) activeColor else inactiveColor,
        topLeft = topLeft,
        size = size,
        cornerRadius = cornerRadius,
    )
}

private fun DrawScope.drawSignalExclamationCutout(color: Color) {
    // Exclamation mark dimensions
    val exclamationDiameterPx = MobileSignalDimensions.ExclamationDiameterSp.toPx()
    val exclamationRadiusPx = exclamationDiameterPx / 2
    val exclamationHeightPx = MobileSignalDimensions.ExclamationHeightSp.toPx()
    val exclamationVerticalSpacingPx = MobileSignalDimensions.ExclamationVerticalSpacing.toPx()
    val exclamationTotalHeight =
        exclamationHeightPx + exclamationVerticalSpacingPx + exclamationDiameterPx
    val exclamationHorizontalOffsetPx = MobileSignalDimensions.ExclamationHorizontalOffset.toPx()

    // Position exclamation mark bottom-aligned with canvas
    val exclamationDotCenter =
        Offset(size.width - exclamationHorizontalOffsetPx, size.height - exclamationRadiusPx)
    val exclamationMarkTopLeft =
        Offset(exclamationDotCenter.x - exclamationRadiusPx, size.height - exclamationTotalHeight)
    val exclamationCornerRadius = CornerRadius(exclamationRadiusPx)
    val cutoutCenter = Offset(exclamationDotCenter.x, size.height - (exclamationTotalHeight / 2))

    // Transparent cutout
    drawCircle(
        color = Color.Transparent,
        radius = MobileSignalDimensions.ExclamationCutoutRadiusSp.toPx(),
        center = cutoutCenter,
        blendMode = BlendMode.SrcIn,
    )

    // Top bar for the exclamation mark
    drawRoundRect(
        color = color,
        topLeft = exclamationMarkTopLeft,
        size = Size(exclamationDiameterPx, exclamationHeightPx),
        cornerRadius = exclamationCornerRadius,
    )

    // Bottom circle for the exclamation mark
    drawCircle(color = color, center = exclamationDotCenter, radius = exclamationRadiusPx)
}

// Dimension class for mobile signal icon
private data class MobileSignalBarsDimensions(
    val totalWidth: TextUnit,
    val barsHorizontalPadding: TextUnit,
    val barBaseHeight: TextUnit,
    val barsLevelIncrement: TextUnit,
)

private val mobileSignalFourBarsDimensions =
    MobileSignalBarsDimensions(
        totalWidth = 17.sp,
        barsHorizontalPadding = 2.sp,
        barBaseHeight = 6.sp,
        barsLevelIncrement = 2.sp,
    )

private val mobileSignalFiveBarsDimensions =
    MobileSignalBarsDimensions(
        totalWidth = 18.5.sp,
        barsHorizontalPadding = 1.5.sp,
        barBaseHeight = 4.5.sp,
        barsLevelIncrement = 1.75.sp,
    )

private object MobileSignalDimensions {
    val ExclamationCutoutRadiusSp = 5.sp
    val ExclamationDiameterSp = 1.5.sp
    val ExclamationHeightSp = 4.5.sp
    val ExclamationVerticalSpacing = 1.sp
    val ExclamationHorizontalOffset = 1.sp
}

private object MobileIconDimensions {
    val IconHeightSp = 12.sp
    val IconSpacingSp = 2.sp
    val RoamingIconHeightSp = 10.sp
    val RoamingIconPaddingTopSp = 1.sp
    val ActivityIndicatorSizeSp = 12.sp
}
