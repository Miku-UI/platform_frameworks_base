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
 *
 */

package com.android.systemui.shade.ui.composable

import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.ColorInt
import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.LowestZIndexContentPicker
import com.android.compose.animation.scene.SceneTransitionLayoutState
import com.android.compose.animation.scene.ValueKey
import com.android.compose.animation.scene.animateElementFloatAsState
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.compose.modifiers.thenIf
import com.android.systemui.Flags.groupedPrivacyChip
import com.android.systemui.common.ui.compose.byLayoutId
import com.android.systemui.common.ui.compose.windowinsets.CutoutLocation
import com.android.systemui.common.ui.compose.windowinsets.LocalDisplayCutout
import com.android.systemui.common.ui.compose.windowinsets.LocalScreenCornerRadius
import com.android.systemui.compose.modifiers.sysuiResTag
import com.android.systemui.kairos.util.nameTag
import com.android.systemui.privacy.AbstractOngoingPrivacyChip
import com.android.systemui.privacy.OngoingPrivacyChip
import com.android.systemui.privacy.PrivacyItem
import com.android.systemui.privacy.ui.view.ComposeOngoingPrivacyChip
import com.android.systemui.res.R
import com.android.systemui.scene.shared.model.DualShadeEducationElement
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.ui.composable.ShadeHeader.Values.ClockScale
import com.android.systemui.shade.ui.viewmodel.ShadeHeaderViewModel
import com.android.systemui.statusbar.phone.StatusBarLocation
import com.android.systemui.statusbar.phone.domain.interactor.IsAreaDark
import com.android.systemui.statusbar.pipeline.battery.ui.composable.BatteryWithEstimate
import com.android.systemui.statusbar.pipeline.mobile.StatusBarMobileIconKairos
import com.android.systemui.statusbar.pipeline.mobile.ui.MobileViewLogger
import com.android.systemui.statusbar.pipeline.mobile.ui.view.ModernShadeCarrierGroupMobileView
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.MobileIconViewModelKairos
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.MobileIconsViewModelKairosComposeWrapper
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.ShadeCarrierGroupMobileIconViewModel
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.ShadeCarrierGroupMobileIconViewModelKairos
import com.android.systemui.statusbar.pipeline.mobile.ui.viewmodel.composeWrapper
import com.android.systemui.statusbar.policy.Clock
import com.android.systemui.statusbar.systemstatusicons.SystemStatusIconsInCompose
import com.android.systemui.statusbar.systemstatusicons.ui.compose.SystemStatusIcons
import com.android.systemui.statusbar.systemstatusicons.ui.compose.SystemStatusIconsLegacy
import com.android.systemui.util.composable.kairos.ActivatedKairosSpec
import com.android.systemui.util.kotlin.toDp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import platform.test.motion.compose.values.MotionTestValueKey
import platform.test.motion.compose.values.motionTestValues

object ShadeHeader {
    object Elements {
        val ExpandedContent = ElementKey("ShadeHeaderExpandedContent")
        val CollapsedContentStart = ElementKey("ShadeHeaderCollapsedContentStart")
        val CollapsedContentEnd = ElementKey("ShadeHeaderCollapsedContentEnd")
        val PrivacyChip = ElementKey("PrivacyChip", contentPicker = LowestZIndexContentPicker)
        val Clock = ElementKey("ShadeHeaderClock", contentPicker = LowestZIndexContentPicker)
        val ShadeCarrierGroup = ElementKey("ShadeCarrierGroup")
    }

    enum class LayoutId {
        StartContent,
        EndContent,
    }

    object Values {
        val ClockScale = ValueKey("ShadeHeaderClockScale")
    }

    object Dimensions {
        @Deprecated(
            "Approximation of the collapsed shade header height, used in legacy shade transitions.",
            replaceWith = ReplaceWith("ShadeHeaderViewModel.statusBarHeightPx"),
        )
        val CollapsedHeightForTransitions = 48.dp
        val ExpandedHeight = 120.dp
        val ChipPaddingHorizontal = 6.dp
        val ChipPaddingVertical = 4.dp
    }

    object Colors {
        val textColor: Color
            @Composable
            @ReadOnlyComposable
            get() = if (isSystemInDarkTheme()) Color.White else Color.Black

        val inverseTextColor: Color
            @Composable
            @ReadOnlyComposable
            get() = if (isSystemInDarkTheme()) Color.Black else Color.White
    }

    object TestTags {
        const val Root = "shade_header_root"
        const val BatteryTestTag = "battery_meter_composable_view"
    }
}

/** The status bar that appears above the Shade scene */
@Composable
fun ContentScope.CollapsedShadeHeader(
    viewModel: ShadeHeaderViewModel,
    isSplitShade: Boolean,
    modifier: Modifier = Modifier,
) {
    val cutoutLocation = LocalDisplayCutout.current().location
    val horizontalPadding =
        max(LocalScreenCornerRadius.current / 2f, Shade.Dimensions.HorizontalPadding)

    val useExpandedTextFormat by
        remember(cutoutLocation) {
            derivedStateOf {
                cutoutLocation != CutoutLocation.CENTER || shouldUseExpandedFormat(layoutState)
            }
        }

    val textColor = ShadeHeader.Colors.textColor

    // This layout assumes it is globally positioned at (0, 0) and is the same size as the screen.
    CutoutAwareShadeHeader(
        statusBarHeightPx = viewModel.statusBarHeightPx,
        modifier = modifier.sysuiResTag(ShadeHeader.TestTags.Root),
        startContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier =
                    Modifier.padding(horizontal = horizontalPadding)
                        .layoutId(ShadeHeader.LayoutId.StartContent),
            ) {
                Clock(
                    onClick = viewModel::onClockClicked,
                    textColor = textColor,
                    modifier =
                        Modifier.sysuiResTag("expanded_header_clock")
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .wrapContentSize(Alignment.CenterStart),
                )
                VariableDayDate(
                    longerDateText = viewModel.longerDateText,
                    shorterDateText = viewModel.shorterDateText,
                    textColor = textColor,
                    modifier = Modifier.element(ShadeHeader.Elements.CollapsedContentStart),
                )
            }
        },
        endContent = {
            if (viewModel.isPrivacyChipVisible) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .padding(horizontal = horizontalPadding)
                            .layoutId(ShadeHeader.LayoutId.EndContent)
                ) {
                    PrivacyChip(
                        privacyList = viewModel.privacyItems,
                        onClick = viewModel::onPrivacyChipClicked,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier.element(ShadeHeader.Elements.CollapsedContentEnd)
                            .padding(horizontal = horizontalPadding)
                            .layoutId(ShadeHeader.LayoutId.EndContent),
                ) {
                    if (isSplitShade) {
                        ShadeCarrierGroup(viewModel = viewModel)
                    }
                    ShadeHighlightChip(
                        isClickable = isSplitShade,
                        onClick = viewModel::onSystemIconChipClicked,
                    ) {
                        val paddingEnd = with(LocalDensity.current) { 3.sp.toDp() }
                        StatusIcons(
                            viewModel = viewModel,
                            useExpandedFormat = useExpandedTextFormat,
                            foregroundColor = textColor.toArgb(),
                            backgroundColor = ShadeHeader.Colors.inverseTextColor.toArgb(),
                            modifier = Modifier.padding(end = paddingEnd).weight(1f, fill = false),
                        )
                        BatteryInfo(
                            viewModel = viewModel,
                            showIcon = true,
                            useExpandedFormat = useExpandedTextFormat,
                            modifier =
                                if (LocalConfiguration.current.equals(ORIENTATION_PORTRAIT))
                                    Modifier.padding(vertical = 8.dp)
                                else Modifier,
                            textColor = textColor,
                        )
                    }
                }
            }
        },
    )
}

/** The status bar that appears above the Quick Settings scene on small screens. */
@Composable
fun ContentScope.ExpandedShadeHeader(
    viewModel: ShadeHeaderViewModel,
    modifier: Modifier = Modifier,
) {
    val useExpandedFormat by remember { derivedStateOf { shouldUseExpandedFormat(layoutState) } }

    val textColor = ShadeHeader.Colors.textColor

    Box(modifier = modifier.sysuiResTag(ShadeHeader.TestTags.Root)) {
        if (viewModel.isPrivacyChipVisible) {
            Box(
                modifier =
                    Modifier.height(viewModel.statusBarHeightPx.toDp(LocalContext.current).dp)
                        .fillMaxWidth()
            ) {
                PrivacyChip(
                    privacyList = viewModel.privacyItems,
                    onClick = viewModel::onPrivacyChipClicked,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(space = 16.dp, alignment = Alignment.Bottom),
            modifier =
                Modifier.fillMaxWidth()
                    .defaultMinSize(minHeight = ShadeHeader.Dimensions.ExpandedHeight),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Clock(
                    onClick = viewModel::onClockClicked,
                    scale = 2.57f,
                    textColor = textColor,
                    modifier =
                        Modifier.sysuiResTag("expanded_header_clock")
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .wrapContentSize(Alignment.CenterStart),
                )
                if (!viewModel.isPrivacyChipVisible) {
                    Box(
                        modifier =
                            Modifier.element(ShadeHeader.Elements.ShadeCarrierGroup).fillMaxWidth()
                    ) {
                        ShadeCarrierGroup(
                            viewModel = viewModel,
                            modifier = Modifier.align(Alignment.CenterEnd).widthIn(max = 180.dp),
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.element(ShadeHeader.Elements.ExpandedContent).fillMaxWidth(),
            ) {
                VariableDayDate(
                    longerDateText = viewModel.longerDateText,
                    shorterDateText = viewModel.shorterDateText,
                    textColor = textColor,
                    modifier = Modifier.sysuiResTag("expanded_shade_header_day_date"),
                )
                ShadeHighlightChip {
                    val paddingEnd = with(LocalDensity.current) { 3.sp.toDp() }
                    StatusIcons(
                        viewModel = viewModel,
                        useExpandedFormat = useExpandedFormat,
                        foregroundColor = textColor.toArgb(),
                        backgroundColor = ShadeHeader.Colors.inverseTextColor.toArgb(),
                        modifier = Modifier.padding(end = paddingEnd).weight(1f, fill = false),
                    )
                    BatteryInfo(
                        viewModel = viewModel,
                        showIcon = true,
                        useExpandedFormat = true,
                        textColor = textColor,
                    )
                }
            }
        }
    }
}

/**
 * The status bar that appears above both the Notifications and Quick Settings shade overlays when
 * overlay shade is enabled.
 */
@Composable
fun ContentScope.OverlayShadeHeader(
    viewModel: ShadeHeaderViewModel,
    notificationsHighlight: ChipHighlightModel,
    quickSettingsHighlight: ChipHighlightModel,
    showClock: Boolean,
    modifier: Modifier = Modifier,
) {
    val horizontalPadding =
        maxOf(
            LocalScreenCornerRadius.current / 2f,
            Shade.Dimensions.HorizontalPadding,
            OverlayShade.Dimensions.PanelPaddingHorizontal,
        )

    // This layout assumes it is globally positioned at (0, 0) and is the same width as the screen.
    CutoutAwareShadeHeader(
        statusBarHeightPx = viewModel.statusBarHeightPx,
        modifier = modifier,
        startContent = {
            Box(modifier = Modifier.layoutId(ShadeHeader.LayoutId.StartContent)) {
                ShadeHighlightChip(
                    backgroundColor = notificationsHighlight.backgroundColor,
                    hoverBackgroundColor = notificationsHighlight.hoverBackgroundColor,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    onClick = viewModel::onNotificationIconChipClicked,
                    clickTargetModifier =
                        Modifier.fillMaxHeight().padding(horizontal = horizontalPadding),
                    modifier =
                        Modifier.bouncy(
                            isEnabled = viewModel.animateNotificationsChipBounce,
                            onBoundsChange = { bounds ->
                                viewModel.onDualShadeEducationElementBoundsChange(
                                    element = DualShadeEducationElement.Notifications,
                                    bounds = bounds,
                                )
                            },
                        ),
                ) {
                    if (showClock) {
                        Clock(textColor = notificationsHighlight.foregroundColor)
                    }
                    VariableDayDate(
                        longerDateText = viewModel.longerDateText,
                        shorterDateText = viewModel.shorterDateText,
                        textColor = notificationsHighlight.foregroundColor,
                    )
                }
            }
        },
        endContent = {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.layoutId(ShadeHeader.LayoutId.EndContent),
            ) {
                ShadeHighlightChip(
                    backgroundColor = quickSettingsHighlight.backgroundColor,
                    hoverBackgroundColor = quickSettingsHighlight.hoverBackgroundColor,
                    onClick = viewModel::onSystemIconChipClicked,
                    clickTargetModifier =
                        Modifier.fillMaxHeight().padding(horizontal = horizontalPadding),
                    modifier =
                        Modifier.bouncy(
                            isEnabled = viewModel.animateSystemIconChipBounce,
                            onBoundsChange = { bounds ->
                                viewModel.onDualShadeEducationElementBoundsChange(
                                    element = DualShadeEducationElement.QuickSettings,
                                    bounds = bounds,
                                )
                            },
                        ),
                ) {
                    val paddingEnd = with(LocalDensity.current) { 3.sp.toDp() }
                    StatusIcons(
                        viewModel = viewModel,
                        useExpandedFormat = false,
                        modifier = Modifier.padding(end = paddingEnd).weight(1f, fill = false),
                        foregroundColor = quickSettingsHighlight.foregroundColor.toArgb(),
                        backgroundColor = quickSettingsHighlight.backgroundColor.toArgb(),
                    )
                    BatteryInfo(
                        viewModel = viewModel,
                        showIcon = true,
                        useExpandedFormat = false,
                        chipHighlightModel = quickSettingsHighlight,
                    )
                }
                if (!groupedPrivacyChip() && viewModel.isPrivacyChipVisible) {
                    Box(
                        modifier = Modifier.fillMaxHeight().padding(horizontal = horizontalPadding)
                    ) {
                        PrivacyChip(
                            privacyList = viewModel.privacyItems,
                            onClick = viewModel::onPrivacyChipClicked,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
            }
        },
    )
}

/** The header that appears at the top of the Quick Settings shade overlay. */
@Composable
fun QuickSettingsOverlayHeader(viewModel: ShadeHeaderViewModel, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        ShadeCarrierGroup(viewModel = viewModel)
        BatteryInfo(viewModel = viewModel, showIcon = false, useExpandedFormat = true)
    }
}

@Composable
fun ContentScope.QuickSettingsOverlayPrivacyChip(
    viewModel: ShadeHeaderViewModel,
    modifier: Modifier = Modifier,
) {
    if (groupedPrivacyChip() && viewModel.isPrivacyChipVisible) {
        Box(modifier = modifier.height(48.dp).fillMaxWidth()) {
            PrivacyChip(
                privacyList = viewModel.privacyItems,
                onClick = viewModel::onPrivacyChipClicked,
                showPrivacyText = true,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/*
 * Places startContent and endContent according to the location of the display cutout.
 * Assumes it is globally positioned at (0, 0) and the same size as the screen.
 */
@Composable
private fun CutoutAwareShadeHeader(
    statusBarHeightPx: Int,
    modifier: Modifier = Modifier,
    startContent: @Composable () -> Unit,
    endContent: @Composable () -> Unit,
) {
    val cutoutProvider = LocalDisplayCutout.current
    Layout(
        modifier = modifier.sysuiResTag(ShadeHeader.TestTags.Root),
        contents = listOf(startContent, endContent),
    ) { measurables, constraints ->
        val measurableStartContent = measurables[0].byLayoutId<ShadeHeader.LayoutId>()
        val measurableEndContent = measurables[1].byLayoutId<ShadeHeader.LayoutId>()
        val cutout = cutoutProvider()

        val cutoutWidth = cutout.width
        val cutoutHeight = cutout.height
        val cutoutTop = cutout.top
        val cutoutLocation = cutout.location

        check(constraints.hasBoundedWidth)

        val screenWidth = constraints.maxWidth
        val width = max((screenWidth - cutoutWidth) / 2, 0)
        val height = max(cutoutHeight + (cutoutTop * 2), statusBarHeightPx)
        val childConstraints = Constraints.fixed(width, height)

        fun measureStart(constraints: Constraints) =
            measurableStartContent[ShadeHeader.LayoutId.StartContent]!!.measure(constraints)
        fun measureEnd(constraints: Constraints) =
            measurableEndContent[ShadeHeader.LayoutId.EndContent]!!.measure(constraints)

        layout(screenWidth, height) {
            if (cutoutLocation == CutoutLocation.RIGHT) {
                val isRightToLeftEnabled = layoutDirection == LayoutDirection.Rtl
                if (isRightToLeftEnabled) {
                    val rightCutoutChildConstraints =
                        Constraints.fixed(screenWidth - cutoutWidth, height)
                    val startPlaceable = measureStart(rightCutoutChildConstraints)
                    val endPlaceable = measureEnd(rightCutoutChildConstraints)

                    startPlaceable.place(
                        x = screenWidth - cutoutWidth - startPlaceable.width,
                        y = 0,
                    )
                    endPlaceable.place(x = 0, y = 0)
                } else {
                    val startPlaceable = measureStart(childConstraints)
                    val endPlaceable = measureEnd(childConstraints)
                    startPlaceable.placeRelative(x = 0, y = 0)
                    endPlaceable.placeRelative(x = startPlaceable.width, y = 0)
                }
            } else {
                val startPlaceable = measureStart(childConstraints)
                val endPlaceable = measureEnd(childConstraints)
                if (cutoutLocation == CutoutLocation.NONE) {
                    startPlaceable.placeRelative(x = 0, y = 0)
                    endPlaceable.placeRelative(x = startPlaceable.width, y = 0)
                } else if (cutoutLocation == CutoutLocation.CENTER) {
                    startPlaceable.placeRelative(x = 0, y = 0)
                    endPlaceable.placeRelative(x = startPlaceable.width + cutoutWidth, y = 0)
                } else {
                    // CutoutLocation.LEFT
                    startPlaceable.placeRelative(x = cutoutWidth, y = 0)
                    endPlaceable.placeRelative(x = startPlaceable.width + cutoutWidth, y = 0)
                }
            }
        }
    }
}

@VisibleForTesting
object ShadeHeaderMotionTestKeys {
    val Alpha = MotionTestValueKey<Float>("alpha")
}

@Composable
private fun ContentScope.Clock(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    scale: Float = 1f,
    textColor: Color? = null,
) {
    val layoutDirection = LocalLayoutDirection.current

    ElementWithValues(
        key = ShadeHeader.Elements.Clock,
        modifier =
            modifier.motionTestValues {
                ShadeHeader.Elements.Clock.currentAlpha()?.let { alpha ->
                    alpha exportAs ShadeHeaderMotionTestKeys.Alpha
                }
            },
    ) {
        val animatedScale by animateElementFloatAsState(scale, ClockScale, canOverflow = false)

        content {
            AndroidView(
                factory = { context ->
                    Clock(
                            ContextThemeWrapper(
                                context,
                                R.style.Theme_SystemUI_QuickSettings_Header,
                            ),
                            null,
                        )
                        .apply {
                            isSingleLine = true
                            textDirection = View.TEXT_DIRECTION_LOCALE
                            gravity = Gravity.START or Gravity.CENTER_VERTICAL
                            if (onClick != null) {
                                isClickable = true
                                isFocusable = true
                                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                                setOnClickListener { onClick.invoke() }
                                accessibilityDelegate =
                                    object : View.AccessibilityDelegate() {
                                        override fun onInitializeAccessibilityNodeInfo(
                                            host: View,
                                            info: AccessibilityNodeInfo,
                                        ) {
                                            super.onInitializeAccessibilityNodeInfo(host, info)
                                            info.className = android.widget.Button::class.java.name
                                            info.isClickable = true
                                            info.isFocusable = true
                                        }
                                    }
                            }
                        }
                },
                update = { view -> textColor?.let { view.setTextColor(it.toArgb()) } },
                modifier =
                    modifier
                        .wrapContentWidth(unbounded = true)
                        // use graphicsLayer instead of Modifier.scale to anchor transform to the
                        // (start, top) corner
                        .graphicsLayer {
                            scaleX = animatedScale
                            scaleY = animatedScale
                            transformOrigin =
                                TransformOrigin(
                                    when (layoutDirection) {
                                        LayoutDirection.Ltr -> 0f
                                        LayoutDirection.Rtl -> 1f
                                    },
                                    0.5f,
                                )
                        }
                        .thenIf(onClick != null) { Modifier.clickable { onClick?.invoke() } },
            )
        }
    }
}

@Composable
private fun BatteryInfo(
    viewModel: ShadeHeaderViewModel,
    showIcon: Boolean,
    useExpandedFormat: Boolean,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    chipHighlightModel: ChipHighlightModel? = null,
) {
    val isQuickSettingsDarkTheme = isSystemInDarkTheme()
    // `viewModel.isShadeAreaDark` does not account for when the shade is pulled down and scrim is
    // applied behind the battery. Use `isSystemInDarkTheme` for sufficient contrast against the
    // shade.
    val isDarkProvider: IsAreaDark =
        when (chipHighlightModel) {
            // null means directly on top of the shade scrim.
            null -> IsAreaDark { isQuickSettingsDarkTheme }
            ChipHighlightModel.Transparent -> viewModel.isShadeAreaDark
            ChipHighlightModel.Strong -> IsAreaDark { !isQuickSettingsDarkTheme }
            ChipHighlightModel.Weak -> IsAreaDark { isQuickSettingsDarkTheme }
        }
    BatteryWithEstimate(
        viewModelFactory = viewModel.batteryViewModelFactory,
        isDarkProvider = { isDarkProvider },
        showIcon = showIcon,
        showEstimate = useExpandedFormat,
        textColor = textColor,
        modifier = modifier.sysuiResTag(ShadeHeader.TestTags.BatteryTestTag),
    )
}

@Composable
private fun CarrierTextWithSubscriptionId(
    viewModel: ShadeHeaderViewModel,
    subId: Int,
    textColor: Color,
    inverseTextColor: Color,
) {
    AndroidView(
        factory = { context ->
            ModernShadeCarrierGroupMobileView.constructAndBind(
                    context = context,
                    logger = viewModel.mobileIconsViewModel.get().logger,
                    slot = "mobile_carrier_shade_group",
                    viewModel =
                        (viewModel.mobileIconsViewModel
                            .get()
                            .viewModelForSub(subId, StatusBarLocation.SHADE_CARRIER_GROUP)
                            as ShadeCarrierGroupMobileIconViewModel),
                )
                .also { it.setOnClickListener { viewModel.onShadeCarrierGroupClicked() } }
        },
        update = { view ->
            view.setStyleAndTint(
                R.style.TextAppearance_QS_Status,
                textColor.toArgb(),
                inverseTextColor.toArgb(),
            )
        },
    )
}

@Composable
private fun CarrierTextNoSubscriptionId(viewModel: ShadeHeaderViewModel) {
    Text(
        text = viewModel.carrierText.toString(),
        modifier = Modifier.basicMarquee(),
        color = ShadeHeader.Colors.textColor,
        style =
            TextStyle(
                fontFamily =
                    FontFamily(Font(DeviceFontFamilyName("variable-body-medium-emphasized"))),
                letterSpacing = 0.01.em,
            ),
        maxLines = 1,
    )
}

@Composable
private fun ShadeCarrierGroup(viewModel: ShadeHeaderViewModel, modifier: Modifier = Modifier) {
    if (StatusBarMobileIconKairos.isEnabled) {
        ShadeCarrierGroupKairos(viewModel, modifier)
        return
    }

    val textColor = ShadeHeader.Colors.textColor
    val inverseTextColor = ShadeHeader.Colors.inverseTextColor
    val mobileSubIds = viewModel.mobileSubIds

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (mobileSubIds.isEmpty()) {
            CarrierTextNoSubscriptionId(viewModel)
        } else {
            for (subId in mobileSubIds) {
                CarrierTextWithSubscriptionId(viewModel, subId, textColor, inverseTextColor)
            }
        }
    }
}

@Composable
private fun ShadeCarrierGroupKairos(
    viewModel: ShadeHeaderViewModel,
    modifier: Modifier = Modifier,
) {
    val textColor = ShadeHeader.Colors.textColor
    val inverseTextColor = ShadeHeader.Colors.inverseTextColor
    Row(modifier = modifier) {
        ActivatedKairosSpec(
            buildSpec = viewModel.mobileIconsViewModelKairos.get().composeWrapper(),
            kairosNetwork = viewModel.kairosNetwork,
            name = nameTag("ShadeCarrierGroupKairos"),
        ) { iconsViewModel: MobileIconsViewModelKairosComposeWrapper ->
            if (iconsViewModel.icons.isEmpty()) {
                CarrierTextNoSubscriptionId(viewModel)
            } else {
                for ((subId, icon) in iconsViewModel.icons) {
                    CarrierTextWithSubscriptionIdKairos(
                        viewModel,
                        subId,
                        icon,
                        iconsViewModel.logger,
                        textColor,
                        inverseTextColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContentScope.StatusIcons(
    viewModel: ShadeHeaderViewModel,
    useExpandedFormat: Boolean,
    @ColorInt foregroundColor: Int,
    @ColorInt backgroundColor: Int,
    modifier: Modifier = Modifier,
) {
    val statusIconContext = LocalStatusIconContext.current
    val iconContainer = statusIconContext.iconContainer(contentKey)
    val iconManager = statusIconContext.iconManager(contentKey)
    val movableContent =
        remember(statusIconContext, iconManager) { statusIconContext.movableContent(iconManager) }

    // TODO(408001821): Add support for background color like [TintedIconManager.setTint].
    if (SystemStatusIconsInCompose.isEnabled) {
        SystemStatusIcons(
            viewModelFactory = viewModel.systemStatusIconsViewModelFactory,
            systemStatusIconBlocklistInteractor = viewModel.systemStatusIconsBlockListInteractor,
            tint = Color(foregroundColor),
        )
    } else {
        val isTransitioning = layoutState.isTransitioningBetween(Scenes.Shade, Scenes.QuickSettings)

        SystemStatusIconsLegacy(
            iconContainer = iconContainer,
            iconManager = iconManager,
            statusBarIconController = viewModel.statusBarIconController,
            useExpandedFormat = useExpandedFormat,
            isTransitioning = isTransitioning,
            foregroundColor = foregroundColor,
            backgroundColor = backgroundColor,
            isSingleCarrier = viewModel.isSingleCarrier,
            isMicCameraIndicationEnabled = viewModel.isMicCameraIndicationEnabled,
            isPrivacyChipEnabled = viewModel.isPrivacyChipVisible,
            isLocationIndicationEnabled = viewModel.isLocationIndicationEnabled,
            modifier = modifier,
            content = movableContent,
        )
    }
}

@Composable
private fun CarrierTextWithSubscriptionIdKairos(
    viewModel: ShadeHeaderViewModel,
    subId: Int,
    icon: MobileIconViewModelKairos,
    logger: MobileViewLogger,
    textColor: Color,
    inverseTextColor: Color,
) {
    Spacer(modifier = Modifier.width(5.dp))
    val scope = rememberCoroutineScope()
    AndroidView(
        factory = { context ->
            ModernShadeCarrierGroupMobileView.constructAndBindKairos(
                    context = context,
                    logger = logger,
                    slot = "mobile_carrier_shade_group",
                    viewModel =
                        ShadeCarrierGroupMobileIconViewModelKairos(icon, icon.iconInteractor),
                    scope = scope,
                    subscriptionId = subId,
                    location = StatusBarLocation.SHADE_CARRIER_GROUP,
                    kairosNetwork = viewModel.kairosNetwork,
                )
                .first
                .also { it.setOnClickListener { viewModel.onShadeCarrierGroupClicked() } }
        },
        update = { view ->
            view.setStyleAndTint(
                R.style.TextAppearance_QS_Status,
                textColor.toArgb(),
                inverseTextColor.toArgb(),
            )
        },
    )
}

@Composable
private fun ContentScope.PrivacyChip(
    privacyList: List<PrivacyItem>,
    onClick: (AbstractOngoingPrivacyChip) -> Unit,
    modifier: Modifier = Modifier,
    showPrivacyText: Boolean = false,
) {
    AndroidView(
        factory = { context ->
            val view =
                if (groupedPrivacyChip()) {
                        ComposeOngoingPrivacyChip(context).apply {
                            this.showPrivacyText = showPrivacyText
                            layoutParams.apply { height = MATCH_PARENT }
                        }
                    } else {
                        OngoingPrivacyChip(context, null)
                    }
                    .also { privacyChip: AbstractOngoingPrivacyChip ->
                        privacyChip.privacyList = privacyList
                        privacyChip.setOnClickListener { onClick(privacyChip) }
                    }
            view
        },
        update = {
            it.privacyList = privacyList
            if (groupedPrivacyChip()) {
                (it as ComposeOngoingPrivacyChip).apply { this.showPrivacyText = showPrivacyText }
            }
        },
        modifier = modifier.element(ShadeHeader.Elements.PrivacyChip),
    )
}

/** Modifies the given [Modifier] such that it shows a looping vertical bounce animation. */
@Composable
private fun Modifier.bouncy(
    isEnabled: Boolean,
    onBoundsChange: (bounds: IntRect) -> Unit,
): Modifier {
    val density = LocalDensity.current
    val animatable = remember { Animatable(0f) }
    LaunchedEffect(isEnabled) {
        if (isEnabled) {
            while (true) {
                // Lifts the element up to the first peak.
                animatable.animateTo(
                    targetValue = with(density) { -(10.dp).toPx() },
                    animationSpec =
                        tween(
                            durationMillis = 200,
                            easing = CubicBezierEasing(0.15f, 0f, 0.23f, 1f),
                        ),
                )
                // Drops the element back to the ground from the first peak.
                animatable.animateTo(
                    targetValue = 0f,
                    animationSpec =
                        tween(
                            durationMillis = 167,
                            easing = CubicBezierEasing(0.74f, 0f, 0.22f, 1f),
                        ),
                )
                // Lifts the element up again, this time to the second, smaller peak.
                animatable.animateTo(
                    targetValue = with(density) { -(5.dp).toPx() },
                    animationSpec =
                        tween(
                            durationMillis = 150,
                            easing = CubicBezierEasing(0.62f, 0f, 0.35f, 1f),
                        ),
                )
                // Drops the element back to the ground from the second peak.
                animatable.animateTo(
                    targetValue = 0f,
                    animationSpec =
                        tween(
                            durationMillis = 117,
                            easing = CubicBezierEasing(0.67f, 0f, 0.51f, 1f),
                        ),
                )
                // Wait for a moment before repeating it.
                delay(1000)
            }
        } else {
            animatable.animateTo(targetValue = 0f, animationSpec = tween(durationMillis = 500))
        }
    }

    return this.thenIf(isEnabled) {
        Modifier.onGloballyPositioned { coordinates ->
                val offset = coordinates.positionInWindow()
                onBoundsChange(
                    IntRect(
                        offset = IntOffset(x = offset.x.roundToInt(), y = offset.y.roundToInt()),
                        size = coordinates.size,
                    )
                )
            }
            .offset { IntOffset(x = 0, y = animatable.value.roundToInt()) }
    }
}

private fun shouldUseExpandedFormat(state: SceneTransitionLayoutState): Boolean {
    return state.isIdle(Scenes.QuickSettings) ||
        (state is TransitionState.Transition &&
            ((state.isTransitioning(to = Scenes.QuickSettings) && state.progress >= 0.5) ||
                (state.isTransitioning(from = Scenes.QuickSettings) && state.progress <= 0.5)))
}
