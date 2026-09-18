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

package com.android.systemui.statusbar.notification.stack.ui.viewmodel

import android.annotation.SuppressLint
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.Scale
import com.android.systemui.Flags
import com.android.systemui.dump.DumpManager
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.notifications.ui.NotificationPlaceholderStateStorage
import com.android.systemui.notifications.ui.YSpace
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.statusbar.domain.interactor.RemoteInputInteractor
import com.android.systemui.statusbar.notification.domain.interactor.HeadsUpNotificationInteractor
import com.android.systemui.statusbar.notification.stack.domain.interactor.NotificationStackAppearanceInteractor
import com.android.systemui.statusbar.notification.stack.shared.model.AccessibilityScrollEvent
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimBounds
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimRounding
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrollState
import com.android.systemui.util.kotlin.ActivatableFlowDumper
import com.android.systemui.util.kotlin.ActivatableFlowDumperImpl
import com.android.systemui.wallpapers.domain.interactor.WallpaperFocalAreaInteractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.function.Consumer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * ViewModel used by the Notification placeholders inside the scene container to update the
 * [NotificationStackAppearanceInteractor], and by extension control the NSSL.
 */
class NotificationsPlaceholderViewModel
@AssistedInject
constructor(
    @param:Assisted private val contentKey: ContentKey,
    private val placeholderStateStorage: NotificationPlaceholderStateStorage,
    private val interactor: NotificationStackAppearanceInteractor,
    private val sceneInteractor: SceneInteractor,
    shadeInteractor: ShadeInteractor,
    shadeModeInteractor: ShadeModeInteractor,
    private val headsUpNotificationInteractor: HeadsUpNotificationInteractor,
    remoteInputInteractor: RemoteInputInteractor,
    dumpManager: DumpManager,
    private val wallpaperFocalAreaInteractor: WallpaperFocalAreaInteractor,
) :
    HydratedActivatable(),
    ActivatableFlowDumper by ActivatableFlowDumperImpl(
        dumpManager = dumpManager,
        tag = "NotificationsPlaceholderViewModel",
    ) {

    fun setStackScrollTop(value: Float) {
        placeholderStateStorage.setStackScrollTop(contentKey, value)
    }

    fun resetStackScrollTop() {
        placeholderStateStorage.resetStackScrollTop(contentKey)
    }

    fun setStackBounds(space: YSpace) {
        placeholderStateStorage.setStackBounds(contentKey, space)
    }

    fun resetStackBounds() {
        placeholderStateStorage.resetStackBounds(contentKey)
    }

    fun setHeadsUpBounds(space: YSpace) {
        placeholderStateStorage.setHunBounds(contentKey, space)
    }

    fun resetHeadsUpBounds() {
        placeholderStateStorage.resetHunBounds(contentKey)
    }

    fun setStackAlpha(value: Float?) {
        placeholderStateStorage.setStackAlpha(contentKey, value)
    }

    fun resetStackAlpha() {
        placeholderStateStorage.resetStackAlpha(contentKey)
    }

    fun setStackScale(value: Scale?) {
        placeholderStateStorage.setStackScale(contentKey, value)
    }

    fun resetStackScale() {
        placeholderStateStorage.resetStackScale(contentKey)
    }

    /** The content key to use for the notification shade. */
    val notificationsShadeContentKey: ContentKey by
        shadeModeInteractor.shadeMode
            .map { getNotificationsShadeContentKey(it) }
            .hydratedStateOf(
                initialValue = getNotificationsShadeContentKey(shadeModeInteractor.shadeMode.value)
            )

    /** @see NotificationStackAppearanceInteractor.notificationStackHorizontalAlignment */
    val horizontalAlignment: Alignment.Horizontal by
        shadeModeInteractor.notificationStackHorizontalAlignment.hydratedStateOf()

    /**
     * Whether the current gesture is expanding a Notification. If true, the NSSL has already
     * consumed the swipe amount to increase the Notification's size.
     */
    val isCurrentGestureExpandingNotification: Boolean by
        interactor.isCurrentGestureExpandingNotif.hydratedStateOf(initialValue = false)

    /** DEBUG: whether the placeholder should be made slightly visible for positional debugging. */
    val isVisualDebuggingEnabled: Boolean = Flags.notificationDebugDrawing()

    /** DEBUG: whether the debug logging should be output. */
    val isDebugLoggingEnabled: Boolean = Flags.notificationDeveloperLogging()

    override suspend fun onActivated() {
        coroutineScope {
            launch { activateFlowDumper() }

            launch {
                sceneInteractor.transitionStateFlow
                    .filter { it is ObservableTransitionState.Idle }
                    .collect { headsUpNotificationInteractor.onTransitionIdle() }
            }
        }
    }

    /** Notifies that the bounds of the notification scrim have changed. */
    fun onScrimBoundsChanged(bounds: ShadeScrimBounds?) {
        interactor.setNotificationShadeScrimBounds(bounds)
    }

    /** Sets the available space */
    fun onConstrainedAvailableSpaceChanged(height: Int) {
        interactor.setConstrainedAvailableSpace(height)
    }

    /** Sets the content alpha for the current state of the brightness mirror */
    fun setAlphaForBrightnessMirror(alpha: Float) {
        interactor.setAlphaForBrightnessMirror(alpha)
    }

    /** True when a HUN is pinned or animating away. */
    val isHeadsUpOrAnimatingAway: Flow<Boolean> =
        headsUpNotificationInteractor.isHeadsUpOrAnimatingAway

    /** Corner rounding of the stack */
    val shadeScrimRounding: Flow<ShadeScrimRounding> =
        interactor.shadeScrimRounding.dumpWhileCollecting("shadeScrimRounding")

    /**
     * The amount [0-1] that the shade or quick settings has been opened. At 0, the shade is closed;
     * at 1, either the shade or quick settings is open.
     */
    val expandFraction: Flow<Float> = shadeInteractor.anyExpansion.dumpValue("expandFraction")

    /**
     * The amount [0-1] that quick settings has been opened. At 0, the shade may be open or closed;
     * at 1, the quick settings are open.
     */
    val shadeToQsFraction: Flow<Float> = shadeInteractor.qsExpansion.dumpValue("shadeToQsFraction")

    /**
     * TODO(b/412986215) fix and wire in syntheticScroll updates
     *
     * The amount in px that the notification stack should scroll due to internal expansion. This
     * should only happen when a notification expansion hits the bottom of the screen, so it is
     * necessary to scroll up to keep expanding the notification.
     */
    @Suppress("unused")
    @SuppressLint("FlowExposedFromViewModel")
    val syntheticScroll: Flow<Float> =
        interactor.syntheticScroll.dumpWhileCollecting("syntheticScroll")

    /** Whether remote input is currently active for any notification. */
    val isRemoteInputActive = remoteInputInteractor.isRemoteInputActive

    /** The bottom bound of the currently focused remote input notification row. */
    val remoteInputRowBottomBound = remoteInputInteractor.remoteInputRowBottomBound

    /** Updates the current scroll state of the notification shade. */
    fun setScrollState(scrollState: ShadeScrollState) {
        interactor.setScrollState(scrollState)
    }

    /** Sets whether the heads up notification is animating away. */
    fun setHeadsUpAnimatingAway(animatingAway: Boolean) {
        headsUpNotificationInteractor.setHeadsUpAnimatingAway(animatingAway)
    }

    /** Snooze the currently pinned HUN. */
    fun snoozeHun() {
        headsUpNotificationInteractor.snooze()
    }

    /** Set a consumer for accessibility events to be handled by the placeholder. */
    fun setAccessibilityScrollEventConsumer(consumer: Consumer<AccessibilityScrollEvent>?) {
        interactor.setAccessibilityScrollEventConsumer(consumer)
    }

    fun onLockScreenStackBottomChanged(bottom: Float) {
        wallpaperFocalAreaInteractor.setNotificationStackAbsoluteBottom(bottom)
    }

    private fun getNotificationsShadeContentKey(shadeMode: ShadeMode): ContentKey {
        return if (shadeMode is ShadeMode.Dual) Overlays.NotificationsShade else Scenes.Shade
    }

    @AssistedFactory
    interface Factory {
        fun create(contentKey: ContentKey): NotificationsPlaceholderViewModel
    }
}

// Expansion fraction thresholds (between 0-1f) at which the corresponding value should be
// at its maximum, given they are at their minimum value at expansion = 0f.
object NotificationTransitionThresholds {
    const val EXPANSION_FOR_MAX_CORNER_RADIUS = 0.1f
    const val EXPANSION_FOR_MAX_SCRIM_ALPHA = 0.3f
    const val EXPANSION_FOR_DELAYED_STACK_FADE_IN = 0.5f
}
