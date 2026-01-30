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

package com.android.systemui.keyguard.ui.composable.elements

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.dimensionResource
import androidx.window.core.layout.WindowSizeClass
import com.android.compose.animation.Easings
import com.android.compose.animation.scene.ContentScope
import com.android.compose.animation.scene.ElementContentScope
import com.android.compose.animation.scene.PropertyTransformationBuilder
import com.android.compose.animation.scene.TransitionBuilder
import com.android.compose.windowsizeclass.LocalWindowSizeClass
import com.android.systemui.keyguard.shared.model.ClockSize
import com.android.systemui.keyguard.ui.viewmodel.LockscreenUpperRegionViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.Logger
import com.android.systemui.log.dagger.KeyguardBlueprintLog
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElement
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Clock
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.MediaCarousel
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Notifications
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Region
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Smartspace
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementProvider
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenSceneKeys.UpperRegion.NarrowLayout as NarrowScenes
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenSceneKeys.UpperRegion.WideLayout as WideScenes
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope.Companion.LockscreenElement
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope.Companion.NestedScenes
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.statusbar.notification.promoted.PromotedNotificationUi
import javax.inject.Inject

/** Provides a combined element for all lockscreen ui above the lock icon */
class LockscreenUpperRegionElementProvider
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    @KeyguardBlueprintLog private val blueprintLog: LogBuffer,
    private val viewModelFactory: LockscreenUpperRegionViewModel.Factory,
) : LockscreenElementProvider {
    private val logger = Logger(blueprintLog, "LockscreenUpperRegionElementProvider")
    override val elements: List<LockscreenElement> by lazy { listOf(UpperRegionElement()) }

    private inner class UpperRegionElement : LockscreenElement {
        override val key = Region.Upper
        override val context = this@LockscreenUpperRegionElementProvider.context

        @Composable
        override fun LockscreenScope<ElementContentScope>.LockscreenElement() {
            val viewModel = rememberViewModel("LockscreenUpperRegion") { viewModelFactory.create() }
            val layoutType = getLayoutType()
            val layout =
                remember(viewModel, layoutType) {
                    when (layoutType) {
                        LayoutType.WIDE -> WideLayout(viewModel)
                        LayoutType.NARROW -> NarrowLayout(viewModel)
                    }
                }

            with(layout) { Layout() }
        }
    }

    abstract inner class RegionLayout(val viewModel: LockscreenUpperRegionViewModel) {
        @Composable abstract fun LockscreenScope<ContentScope>.Layout(modifier: Modifier = Modifier)

        @Composable
        protected fun LockscreenScope<ContentScope>.Notifications(modifier: Modifier = Modifier) {
            Column(modifier = modifier.fillMaxHeight()) {
                AODNotifications()
                AnimatedVisibility(viewModel.isNotificationStackActive) {
                    LockscreenElement(Notifications.Stack)
                }
            }
        }

        @Composable
        protected fun LockscreenScope<ContentScope>.AODNotifications(
            modifier: Modifier = Modifier
        ) {
            AnimatedVisibility(viewModel.isDozing, modifier) {
                if (PromotedNotificationUi.isEnabled) {
                    LockscreenElement(Notifications.AOD.Promoted)
                }
                LockscreenElement(Notifications.AOD.IconShelf)
            }
        }

        protected fun TransitionBuilder.configureClockTransition(
            enter: PropertyTransformationBuilder.() -> Unit,
            exit: PropertyTransformationBuilder.() -> Unit,
        ) {
            spec = tween(300, easing = Easings.Emphasized)

            // Since Smartspace cards are guaranteed to be shared between the small and large clock
            // regions, it's convenient to anchor the movement of the small clock elements to it.
            anchoredTranslate(Clock.Small, anchor = Smartspace.Cards)
            anchoredTranslate(Smartspace.DWA.SmallClock.Row, anchor = Smartspace.Cards)
            anchoredTranslate(Smartspace.DWA.SmallClock.Column, anchor = Smartspace.Cards)

            timestampRange(endMillis = 133) { exit() }
            timestampRange(startMillis = 133, endMillis = 300) { enter() }
        }

        protected fun PropertyTransformationBuilder.fadeLargeClock() {
            fade(Clock.Large)
            fade(Smartspace.DWA.LargeClock.Above)
            fade(Smartspace.DWA.LargeClock.Below)
        }

        protected fun PropertyTransformationBuilder.fadeSmallClock() {
            fade(Clock.Small)
            fade(Smartspace.DWA.SmallClock.Row)
            fade(Smartspace.DWA.SmallClock.Column)
        }
    }

    /** The Narrow Layouts are intended for phones */
    inner class NarrowLayout(viewModel: LockscreenUpperRegionViewModel) : RegionLayout(viewModel) {
        @Composable
        override fun LockscreenScope<ContentScope>.Layout(modifier: Modifier) {
            val clockSize =
                viewModel.evaluateClockSize {
                    when {
                        viewModel.isNotificationStackActive -> ClockSize.SMALL
                        viewModel.isMediaActive -> ClockSize.SMALL
                        else -> ClockSize.LARGE
                    }
                }

            NestedScenes(
                sceneKey =
                    when (clockSize) {
                        ClockSize.LARGE -> NarrowScenes.LargeClock
                        ClockSize.SMALL -> NarrowScenes.SmallClock
                    },
                transitions = {
                    from(from = NarrowScenes.SmallClock, to = NarrowScenes.LargeClock) {
                        configureClockTransition(
                            enter = { fadeLargeClock() },
                            exit = { fadeSmallClock() },
                        )
                    }
                    from(from = NarrowScenes.LargeClock, to = NarrowScenes.SmallClock) {
                        configureClockTransition(
                            enter = { fadeSmallClock() },
                            exit = { fadeLargeClock() },
                        )
                    }
                },
                modifier = modifier,
            ) {
                scene(NarrowScenes.LargeClock) { LockscreenElement(Region.Clock.Large) }
                scene(NarrowScenes.SmallClock) {
                    Column {
                        LockscreenElement(Region.Clock.Small)
                        LockscreenElement(
                            MediaCarousel,
                            Modifier.padding(
                                bottom =
                                    dimensionResource(
                                        R.dimen.notification_section_divider_height_lockscreen
                                    )
                            ),
                        )
                        Notifications()
                    }
                }
            }
        }
    }

    /** The wide layouts are intended for tablets / foldables */
    inner class WideLayout(viewModel: LockscreenUpperRegionViewModel) : RegionLayout(viewModel) {
        @Composable
        override fun LockscreenScope<ContentScope>.Layout(modifier: Modifier) {
            val clockSize =
                viewModel.evaluateClockSize {
                    when {
                        viewModel.shadeMode == ShadeMode.Dual -> ClockSize.LARGE
                        viewModel.isMediaActive -> ClockSize.SMALL
                        else -> ClockSize.LARGE
                    }
                }

            val isTwoColumn =
                when {
                    clockSize == ClockSize.SMALL -> true
                    !viewModel.isDozing && viewModel.isNotificationStackActive -> true
                    viewModel.isDozing && viewModel.isHeadsUpNotificationActive -> true
                    viewModel.isDozing && viewModel.isPromotedNotificationActive -> true
                    else -> false
                }

            NestedScenes(
                sceneKey =
                    when {
                        !isTwoColumn -> WideScenes.CenteredClock
                        clockSize == ClockSize.LARGE -> WideScenes.TwoColumn.LargeClock
                        else -> WideScenes.TwoColumn.SmallClock
                    },
                transitions = {
                    from(from = WideScenes.CenteredClock, to = WideScenes.TwoColumn.LargeClock) {
                        spec = tween(ClockCenteringDurationMS, easing = Easings.Emphasized)
                    }
                    from(from = WideScenes.TwoColumn.LargeClock, to = WideScenes.CenteredClock) {
                        spec = tween(ClockCenteringDurationMS, easing = Easings.Emphasized)
                    }
                    from(from = WideScenes.CenteredClock, to = WideScenes.TwoColumn.SmallClock) {
                        configureClockTransition(
                            enter = { fadeSmallClock() },
                            exit = { fadeLargeClock() },
                        )
                    }
                    from(from = WideScenes.TwoColumn.SmallClock, to = WideScenes.CenteredClock) {
                        configureClockTransition(
                            enter = { fadeLargeClock() },
                            exit = { fadeSmallClock() },
                        )
                    }
                    from(
                        from = WideScenes.TwoColumn.LargeClock,
                        to = WideScenes.TwoColumn.SmallClock,
                    ) {
                        configureClockTransition(
                            enter = { fadeSmallClock() },
                            exit = { fadeLargeClock() },
                        )
                    }
                    from(
                        from = WideScenes.TwoColumn.SmallClock,
                        to = WideScenes.TwoColumn.LargeClock,
                    ) {
                        configureClockTransition(
                            enter = { fadeLargeClock() },
                            exit = { fadeSmallClock() },
                        )
                    }
                },
                modifier = modifier,
            ) {
                scene(WideScenes.CenteredClock) { LockscreenElement(Region.Clock.Large) }
                scene(WideScenes.TwoColumn.LargeClock) {
                    when (viewModel.shadeMode) {
                        ShadeMode.Dual -> NotificationsStartLargeClock()
                        ShadeMode.Split -> NotificationsEndLargeClock()
                        else -> logger.wtf("WideLayout state is invalid")
                    }
                }
                scene(WideScenes.TwoColumn.SmallClock) {
                    when (viewModel.shadeMode) {
                        ShadeMode.Dual -> NotificationsStartSmallClock()
                        ShadeMode.Split -> NotificationsEndSmallClock()
                        else -> logger.wtf("WideLayout state is invalid")
                    }
                }
            }
        }

        @Composable
        private fun LockscreenScope<ContentScope>.NotificationsStartLargeClock(
            modifier: Modifier = Modifier
        ) {
            TwoColumn(
                startContent = {
                    Column {
                        LockscreenElement(MediaCarousel)
                        Notifications()
                    }
                },
                endContent = { LockscreenElement(Region.Clock.Large) },
                modifier = modifier,
            )
        }

        @Composable
        private fun LockscreenScope<ContentScope>.NotificationsStartSmallClock(
            modifier: Modifier = Modifier
        ) {
            TwoColumn(
                startContent = {
                    Column {
                        LockscreenElement(Region.Clock.Small)
                        LockscreenElement(
                            MediaCarousel,
                            Modifier.padding(
                                bottom =
                                    dimensionResource(
                                        R.dimen.notification_section_divider_height_lockscreen
                                    )
                            ),
                        )
                        Notifications()
                    }
                },
                modifier = modifier,
            )
        }

        @Composable
        private fun LockscreenScope<ContentScope>.NotificationsEndLargeClock(
            modifier: Modifier = Modifier
        ) {
            TwoColumn(
                startContent = { LockscreenElement(Region.Clock.Large) },
                endContent = { Notifications() },
                modifier = modifier,
            )
        }

        @Composable
        private fun LockscreenScope<ContentScope>.NotificationsEndSmallClock(
            modifier: Modifier = Modifier
        ) {
            TwoColumn(
                startContent = {
                    Column {
                        LockscreenElement(Region.Clock.Small)
                        LockscreenElement(MediaCarousel)
                    }
                },
                endContent = { Notifications() },
                modifier = modifier,
            )
        }

        @Composable
        private fun TwoColumn(
            modifier: Modifier = Modifier,
            startContent: @Composable BoxScope.() -> Unit = {},
            endContent: @Composable BoxScope.() -> Unit = {},
        ) {
            Row(modifier) {
                Box(
                    content = startContent,
                    modifier =
                        Modifier.fillMaxWidth(0.5f).fillMaxHeight().graphicsLayer {
                            translationX = viewModel.unfoldTranslations.start
                        },
                )
                Box(
                    content = endContent,
                    modifier =
                        Modifier.fillMaxWidth(1f).fillMaxHeight().graphicsLayer {
                            translationX = viewModel.unfoldTranslations.end
                        },
                )
            }
        }
    }

    companion object {
        const val ClockCenteringDurationMS = 1000

        enum class LayoutType {
            WIDE,
            NARROW,
        }

        @Composable
        fun getLayoutType(): LayoutType {
            with(LocalWindowSizeClass.current) {
                val isWindowLarge =
                    isAtLeastBreakpoint(
                        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
                        WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND,
                    )
                return if (isWindowLarge) LayoutType.WIDE else LayoutType.NARROW
            }
        }
    }
}
