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

package com.android.systemui.statusbar.notification.stack.ui.viewbinder

import android.util.Log
import com.android.app.tracing.coroutines.flow.collectLatestTraced
import com.android.app.tracing.coroutines.flow.collectTraced
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.Flags
import com.android.systemui.common.ui.view.onLayoutChanged
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.AndroidUi
import com.android.systemui.dump.DumpManager
import com.android.systemui.lifecycle.WindowLifecycleState
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.lifecycle.viewModel
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.statusbar.notification.shared.NsslTouchDispatchFix
import com.android.systemui.statusbar.notification.stack.ui.view.NotificationScrollView
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.NotificationScrollViewModel
import com.android.systemui.util.kotlin.FlowDumperImpl
import com.android.systemui.util.kotlin.buildDisposableHandle
import com.android.systemui.util.kotlin.launchAndDispose
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter

/** Binds the [NotificationScrollView], SceneContainer only. */
@SysUISingleton
class NotificationScrollViewBinder
@Inject
constructor(
    dumpManager: DumpManager,
    @AndroidUi private val androidUiDispatcher: CoroutineContext,
    private val view: NotificationScrollView,
    private val viewModelFactory: NotificationScrollViewModel.Factory,
) : FlowDumperImpl(dumpManager) {

    private val viewLeftOffset = MutableStateFlow(0).dumpValue("viewLeftOffset")

    private fun updateViewPosition() {
        val trueView = view.asView()
        if (trueView.top != 0) {
            Log.w("NSSL", "Expected $trueView to have top==0")
        }
        viewLeftOffset.value = trueView.left
    }

    fun bindWhileAttached(): DisposableHandle {
        if (SceneContainerFlag.isUnexpectedlyInLegacyMode()) {
            return DisposableHandle {}
        }
        return view.asView().repeatWhenAttached(androidUiDispatcher) { bind() }
    }

    private suspend fun bind(): Nothing =
        view.asView().viewModel(
            traceName = "NotificationScrollViewBinder",
            minWindowLifecycleState = WindowLifecycleState.ATTACHED,
            factory = viewModelFactory::create,
        ) { viewModel ->
            launchAndDispose {
                updateViewPosition()
                view.asView().onLayoutChanged { updateViewPosition() }
            }

            launch {
                viewModel
                    .notificationScrimShape(
                        cornerRadius = viewModel.scrimClippingRadius,
                        viewLeftOffset = viewLeftOffset,
                    )
                    .collectTraced { view.setClippingShape(it) }
            }

            launch { viewModel.animationsEnabled.collectTraced { view.setAnimationsEnabled(it) } }

            launch { viewModel.maxAlpha.collectTraced { view.setMaxAlpha(it) } }
            launch { viewModel.shadeScrollState.collect { view.setScrollState(it) } }
            launch { viewModel.expandFraction.collectTraced { view.setExpandFraction(it) } }
            launch { viewModel.qsExpandFraction.collectTraced { view.setQsExpandFraction(it) } }
            if (Flags.notificationShadeBlur()) {
                launch { viewModel.blurRadius.collect(view::setBlurRadius) }
            }
            if (Flags.notificationShadeBlur() || Flags.fixNsslBlockingQs()) {
                launch { viewModel.interactive.collectTraced(view::setInteractive) }
            }
            launch {
                viewModel.lockScreenToShadeTransitionProgress.collectTraced {
                    view.setLStoShadeProgress(it)
                }
            }
            launch { viewModel.isSplitShade.collectTraced { view.setSplitShade(it) } }
            launch {
                viewModel
                    .getLockscreenDisplayConfig(view::calculateMaxNotifications)
                    .collectLatestTraced { (isOnLockscreen, maxNotifications) ->
                        view.setOnLockscreen(isOnLockscreen)
                        view.setMaxDisplayedNotifications(maxNotifications)
                    }
            }
            launch {
                viewModel.isShowingStackOnLockscreen.collectTraced {
                    view.setShowingStackOnLockscreen(it)
                }
            }
            launch {
                viewModel.alphaForLockscreenFadeIn.collectTraced {
                    view.setAlphaForLockscreenFadeIn(it)
                }
            }
            launch {
                viewModel.isCurrentSceneLockscreen.collectTraced {
                    view.setCurrentSceneLockscreen(it)
                }
            }
            launch { viewModel.isScrollable.collectTraced { view.setScrollingEnabled(it) } }
            launch { viewModel.isDozing.collectTraced { isDozing -> view.setDozing(isDozing) } }
            launch {
                viewModel.isPulsing.collectTraced { isPulsing ->
                    view.setPulsing(isPulsing, viewModel.shouldAnimatePulse.value)
                }
            }
            launch {
                viewModel.shouldCloseGuts
                    .filter { it }
                    .collectTraced { view.closeGutsOnSceneTouch() }
            }
            launch {
                viewModel.suppressHeightUpdates.collectTraced { view.suppressHeightUpdates(it) }
            }

            launch {
                viewModel.sidePaddingConfig.collectLatestTraced {
                    (baseSidePadding, alignToInnerQqsTiles) ->
                    view.setSidePaddingConfig(baseSidePadding, alignToInnerQqsTiles)
                }
            }

            launchAndDispose {
                buildDisposableHandle {
                    bind(viewModel.syntheticScrollConsumer) { view.setSyntheticScrollConsumer(it) }
                    if (!NsslTouchDispatchFix.isEnabled) {
                        bind(viewModel.currentGestureExpandingNotifConsumer) {
                            view.setCurrentGestureExpandingNotificationConsumer(it)
                        }
                    }
                    bind(viewModel.currentGestureInGutsConsumer) {
                        view.setCurrentGestureInGutsConsumer(it)
                    }
                    bind(viewModel.remoteInputRowBottomBoundConsumer) {
                        view.setRemoteInputRowBottomBoundConsumer(it)
                    }
                    bind(viewModel.accessibilityScrollEventConsumer) {
                        view.setAccessibilityScrollEventConsumer(it)
                    }
                    register(
                        viewModel.getQsScrimShape(view.observableLeft).observe { shape ->
                            view.setNegativeClippingShape(shape)
                        }
                    )
                    register(
                        viewModel.stackScrollTop.observe { scrollTop ->
                            view.setStackScrollTop(scrollTop)
                        }
                    )
                    register(
                        viewModel.stackBounds.observe { stackBounds ->
                            view.updateStackBounds(stackBounds)
                        }
                    )
                    register(
                        viewModel.headsUpBounds.observe { hunBounds ->
                            view.setHeadsUpTop(hunBounds.top)
                            view.setHeadsUpBottom(hunBounds.bottom)
                        }
                    )
                    register(
                        viewModel.stackPlaceholderAlpha.observe { alpha ->
                            view.setPlaceholderAlpha(alpha)
                        }
                    )
                }
            }
        }
}
