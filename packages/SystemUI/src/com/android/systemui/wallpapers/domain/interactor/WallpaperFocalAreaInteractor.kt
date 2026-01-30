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

package com.android.systemui.wallpapers.domain.interactor

import android.content.Context
import android.content.res.Resources
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import android.view.View
import androidx.annotation.VisibleForTesting
import com.android.app.animation.MathUtils
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.systemui.CoreStartable
import com.android.systemui.customization.clocks.R as customR
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.keyguard.domain.interactor.KeyguardSmartspaceInteractor
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.Edge
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.keyguard.shared.model.TransitionState
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.wallpapers.data.repository.WallpaperFocalAreaRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@SysUISingleton
class WallpaperFocalAreaInteractor
@Inject
constructor(
    var context: Context,
    private val wallpaperFocalAreaRepository: WallpaperFocalAreaRepository,
    shadeModeInteractor: ShadeModeInteractor,
    smartspaceInteractor: KeyguardSmartspaceInteractor,
    private val keyguardTransitionInteractor: KeyguardTransitionInteractor,
    private val sceneInteractor: SceneInteractor,
    @Background private val backgroundScope: CoroutineScope,
    private val wallpaperInteractor: WallpaperInteractor,
) : CoreStartable {
    val hasFocalArea = wallpaperInteractor.hasFocalArea

    @OptIn(ExperimentalCoroutinesApi::class)
    val shouldCollectFocalArea =
        hasFocalArea.flatMapLatest { hasFocalArea ->
            if (!hasFocalArea) {
                return@flatMapLatest flowOf(false)
            }

            if (SceneContainerFlag.isEnabled) {
                sceneInteractor.transitionState.map { transitionState ->
                    transitionState.isLockscreenIdleWithoutShades() ||
                        transitionState.isTransitioningToLockscreenFromNonShade()
                }
            } else {
                merge(
                        keyguardTransitionInteractor.startedKeyguardTransitionStep
                            .map { transitionStep ->
                                transitionStep.to == KeyguardState.LOCKSCREEN &&
                                    transitionStep.from != KeyguardState.LOCKSCREEN
                            }
                            .distinctUntilChanged(),
                        // Emit bounds when finishing transition to LOCKSCREEN to avoid
                        // getWallpaperTarget() and getPrevWallpaperTarget() are null and fail
                        // to send command
                        keyguardTransitionInteractor
                            .transition(
                                edge = Edge.create(to = Scenes.Lockscreen),
                                edgeWithoutSceneContainer =
                                    Edge.create(to = KeyguardState.LOCKSCREEN),
                            )
                            .filter { it.transitionState == TransitionState.FINISHED }
                            .map { true },
                    )
                    // Enforce collecting wallpaperFocalAreaBounds after rebooting
                    .onStart { emit(true) }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val wallpaperFocalAreaBoundsOnLockscreen: Flow<RectF> =
        shouldCollectFocalArea.flatMapLatest { shouldCollectFocalArea ->
            if (shouldCollectFocalArea) {
                wallpaperFocalAreaBounds
            } else {
                emptyFlow()
            }
        }

    private val topAreaSectionBottom: Flow<Float> =
        if (SceneContainerFlag.isEnabled) {
            combine(
                    wallpaperFocalAreaRepository.smallClockViewBottom,
                    wallpaperFocalAreaRepository.smartspaceCardBottom,
                    wallpaperFocalAreaRepository.mediaPlayerBottom,
                ) { smallClockViewBottom, smartspaceCardBottom, mediaPlayerBottom ->
                    Log.d(
                        TAG,
                        "smallClockViewBottom $smallClockViewBottom smartspaceCardBottom " +
                            "$smartspaceCardBottom mediaPlayerBottom $mediaPlayerBottom",
                    )
                    MathUtils.max(
                        smallClockViewBottom,
                        MathUtils.max(smartspaceCardBottom, mediaPlayerBottom),
                    )
                }
                .filter { it != -1f }
        } else {
            /**
             * When there's no notification, we'll use max of small clock bottom and bcsmartspace,
             * and height of UMO will be calculated in notification stack height
             */
            combine(
                wallpaperFocalAreaRepository.notificationDefaultTop,
                smartspaceInteractor.bcSmartspaceVisibility,
            ) { notificationDefaultTop, bcSmartspaceVisibility ->
                when (bcSmartspaceVisibility) {
                    View.VISIBLE -> {
                        notificationDefaultTop +
                            context.resources
                                .getDimensionPixelSize(customR.dimen.enhanced_smartspace_height)
                                .toFloat()
                    }

                    else -> {
                        notificationDefaultTop
                    }
                }
            }
        }

    @VisibleForTesting
    val wallpaperFocalAreaBounds: Flow<RectF> =
        combine(
                shadeModeInteractor.isFullWidthShade,
                wallpaperFocalAreaRepository.notificationStackAbsoluteBottom,
                wallpaperFocalAreaRepository.shortcutAbsoluteTop,
                topAreaSectionBottom,
            ) {
                isFullWidthShade,
                notificationStackAbsoluteBottom,
                shortcutAbsoluteTop,
                topAreaSectionBottom ->
                // Wallpaper will be zoomed in with config_wallpaperMaxScale in lockscreen
                // so we need to give a bounds taking this scale in consideration
                val wallpaperZoomedInScale = getSystemWallpaperMaximumScale(context)

                val screenBounds =
                    RectF(
                        0F,
                        0F,
                        context.resources.displayMetrics.widthPixels.toFloat(),
                        context.resources.displayMetrics.heightPixels.toFloat(),
                    )
                val scaledBounds =
                    RectF(
                        screenBounds.centerX() - screenBounds.width() / 2F / wallpaperZoomedInScale,
                        screenBounds.centerY() -
                            screenBounds.height() / 2F / wallpaperZoomedInScale,
                        screenBounds.centerX() + screenBounds.width() / 2F / wallpaperZoomedInScale,
                        screenBounds.centerY() + screenBounds.height() / 2F / wallpaperZoomedInScale,
                    )

                val (left, right) =
                    Pair(
                        scaledBounds.centerX() - scaledBounds.width() / 2F,
                        scaledBounds.centerX() + scaledBounds.width() / 2F,
                    )
                val scaledBottomMargin =
                    (context.resources.displayMetrics.heightPixels - shortcutAbsoluteTop) /
                        wallpaperZoomedInScale

                val top =
                    when {
                        // Tablet landscape
                        context.resources.getBoolean(R.bool.center_align_focal_area_shape) ->
                            // no strict constraints for top, use bottom margin to make it symmetric
                            // vertically
                            scaledBounds.top + scaledBottomMargin

                        // Handheld / tablet portrait
                        isFullWidthShade ->
                            scaledBounds.top +
                                MathUtils.max(
                                    topAreaSectionBottom,
                                    notificationStackAbsoluteBottom,
                                ) / wallpaperZoomedInScale

                        // Unfolded foldable landscape
                        else ->
                            // For all landscape, we should use bottom of smartspace to constrain
                            scaledBounds.top + topAreaSectionBottom / wallpaperZoomedInScale
                    }
                val bottom = scaledBounds.bottom - scaledBottomMargin
                RectF(left, top, right, bottom).also { Log.d(TAG, "Focal area changes to $it") }
            }
            // Make sure a valid rec
            .filter { it.width() >= 0 && it.height() >= 0 }
            .distinctUntilChanged()

    override fun start() {
        backgroundScope.launch {
            wallpaperFocalAreaBoundsOnLockscreen.collect { bounds ->
                sendWallpaperFocalAreaBounds(bounds)
            }
        }
    }

    fun setNotificationDefaultTop(top: Float) {
        wallpaperFocalAreaRepository.notificationDefaultTop.value = top
    }

    fun setShortcutTop(top: Float) {
        wallpaperFocalAreaRepository.shortcutAbsoluteTop.value = top
    }

    fun setMediaPlayerBottom(bottom: Float) {
        wallpaperFocalAreaRepository.mediaPlayerBottom.value = bottom
    }

    fun setNotificationStackAbsoluteBottom(bottom: Float) {
        wallpaperFocalAreaRepository.notificationStackAbsoluteBottom.value = bottom
    }

    fun setSmallClockBottom(bottom: Float) {
        wallpaperFocalAreaRepository.smallClockViewBottom.value = bottom
    }

    fun setSmartspaceCardBottom(bottom: Float) {
        wallpaperFocalAreaRepository.smartspaceCardBottom.value = bottom
    }

    fun sendTapPosition(x: Float, y: Float) {
        // Focal area should only react to touch event within its bounds
        val wallpaperZoomedInScale = getSystemWallpaperMaximumScale(context)
        // Because there's a scale applied on wallpaper in lockscreen
        // we should map it to the unscaled position on wallpaper
        val screenCenterX = context.resources.displayMetrics.widthPixels / 2F
        val newX = (x - screenCenterX) / wallpaperZoomedInScale + screenCenterX
        val screenCenterY = context.resources.displayMetrics.heightPixels / 2F
        val newY = (y - screenCenterY) / wallpaperZoomedInScale + screenCenterY
        wallpaperInteractor.sendTapPosition(PointF(newX, newY))
    }

    fun sendWallpaperFocalAreaBounds(bounds: RectF) {
        wallpaperInteractor.sendWallpaperFocalAreaBounds(bounds)
    }

    companion object {
        fun getSystemWallpaperMaximumScale(context: Context): Float {
            val scale =
                context.resources.getFloat(
                    Resources.getSystem()
                        .getIdentifier(
                            /* name= */ "config_wallpaperMaxScale",
                            /* defType= */ "dimen",
                            /* defPackage= */ "android",
                        )
                )
            return if (scale == 0f) 1f else scale
        }

        private fun ObservableTransitionState.isLockscreenIdleWithoutShades(): Boolean {
            // Checking only isIdle(Scenes.Lockscreen) isn't enough for a "shade-free" idle
            // lockscreen. In dual shade mode, overlays like notification or quick settings shades
            // can still be present. The additional !isIdle checks for these overlays are crucial
            // to confirm their absence.
            return isIdle(Scenes.Lockscreen) &&
                !isIdle(Overlays.NotificationsShade) &&
                !isIdle(Overlays.QuickSettingsShade)
        }

        private fun ObservableTransitionState.isTransitioningToLockscreenFromNonShade(): Boolean {
            return isTransitioning(to = Scenes.Lockscreen) &&
                !isTransitioningSets(
                    from =
                        setOf(
                            Scenes.Shade,
                            Scenes.QuickSettings,
                            Overlays.NotificationsShade,
                            Overlays.QuickSettingsShade,
                        )
                )
        }

        private val TAG = WallpaperFocalAreaInteractor::class.simpleName
    }
}
