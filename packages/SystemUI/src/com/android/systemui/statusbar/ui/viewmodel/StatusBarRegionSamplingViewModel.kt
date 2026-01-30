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

package com.android.systemui.statusbar.ui.viewmodel

import android.graphics.Rect
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.runtime.getValue
import com.android.internal.view.AppearanceRegion
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.kairos.awaitClose
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.lifecycle.Hydrator
import com.android.systemui.statusbar.StatusBarRegionSampling
import com.android.systemui.statusbar.domain.interactor.StatusBarRegionSamplingInteractor
import com.android.systemui.util.boundsOnScreen
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import com.android.wm.shell.shared.handles.RegionSamplingHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A view model for the status bar lightness sampling regions.
 *
 * Note: This view model is for the status bar view as a whole. It tracks two separate sampling
 * regions: one for the start side and one for the end side.
 *
 * Each sampling region consists of two [Rect]s:
 * - Sampling rect: the area of the screen where [RegionSamplingHelper] will inspect the lightness
 *   of the pixels rendered to the display.
 * - AppearanceRegion rect: the area of the screen provided to
 *   [com.android.systemui.statusbar.data.repository.StatusBarModePerDisplayRepository] that
 *   determines the color to use for all status bar icons that overlap this area.
 *
 * These regions do not necessarily overlap:
 * - The sampling region can be any part of the screen that sufficiently represents the lightness of
 *   this side of the status bar, and can be any size that is sufficiently large to not be
 *   over-influenced by small fluctuations or noise in the background.
 * - The AppearanceRegion **must** represent a Rect that encompasses the screen bounds of all icons
 *   that should have their color changed based on the sampling region.
 */
class StatusBarRegionSamplingViewModel
@AssistedInject
constructor(
    @Assisted private val displayId: Int,
    @Assisted("attachStateView") private val attachStateView: View,
    @Assisted("startSideContainerView") private val startSideContainerView: View,
    @Assisted("startSideIconView") private val startSideIconView: View,
    @Assisted("endSideContainerView") private val endSideContainerView: View,
    @Assisted("endSideIconView") private val endSideIconView: View,
    @Assisted private val regionSamplingHelperFactory: RegionSamplingHelperFactory,
    private val statusBarRegionSamplingInteractor: StatusBarRegionSamplingInteractor,
    @Main private val mainExecutor: Executor,
    @Background private val backgroundExecutor: Executor,
    @Background private val backgroundScope: CoroutineScope,
) : ExclusiveActivatable() {
    private val hydrator = Hydrator(traceName = "StatusBarRegionSamplingViewModel.hydrator")

    private val isRegionSamplingEnabled =
        statusBarRegionSamplingInteractor.isRegionSamplingEnabled.stateIn(
            scope = backgroundScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = false,
        )

    private data class Bounds(val sampling: Rect, val appearanceRegion: Rect)

    private val _startSideBounds: Flow<Bounds> =
        conflatedCallbackFlow {
                val layoutListener =
                    View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                        trySend(
                            Bounds(
                                sampling =
                                    getSamplingBounds(
                                        containerView = startSideContainerView,
                                        iconView = startSideIconView,
                                    ),
                                appearanceRegion =
                                    getAppearanceRegionBounds(
                                        containerView = startSideContainerView
                                    ),
                            )
                        )
                    }
                startSideContainerView.addOnLayoutChangeListener(layoutListener)
                awaitClose { startSideContainerView.removeOnLayoutChangeListener(layoutListener) }
            }
            .stateIn(
                backgroundScope,
                SharingStarted.WhileSubscribed(),
                initialValue = Bounds(Rect(), Rect()),
            )

    private val startSideSamplingBounds: Rect by
        hydrator.hydratedStateOf(
            traceName = "StatusBarRegionSamplingViewModel.startSideSamplingBounds",
            initialValue = Rect(),
            source = _startSideBounds.map { it.sampling },
        )

    private val startSideAppearanceRegionBounds: Rect by
        hydrator.hydratedStateOf(
            traceName = "StatusBarRegionSamplingViewModel.startSideAppearanceRegionBounds",
            initialValue = Rect(),
            source = _startSideBounds.map { it.appearanceRegion },
        )

    private val _endSideBounds: Flow<Bounds> =
        conflatedCallbackFlow {
                val layoutListener =
                    View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                        trySend(
                            Bounds(
                                sampling =
                                    getSamplingBounds(
                                        containerView = endSideContainerView,
                                        iconView = endSideIconView,
                                    ),
                                appearanceRegion =
                                    getAppearanceRegionBounds(containerView = endSideContainerView),
                            )
                        )
                    }
                endSideContainerView.addOnLayoutChangeListener(layoutListener)
                awaitClose { endSideContainerView.removeOnLayoutChangeListener(layoutListener) }
            }
            .stateIn(
                backgroundScope,
                SharingStarted.WhileSubscribed(),
                initialValue = Bounds(Rect(), Rect()),
            )

    private val endSideSamplingBounds: Rect by
        hydrator.hydratedStateOf(
            traceName = "StatusBarRegionSamplingViewModel.endSideSamplingBounds",
            initialValue = Rect(),
            source = _endSideBounds.map { it.sampling },
        )

    private val endSideAppearanceRegionBounds: Rect by
        hydrator.hydratedStateOf(
            traceName = "StatusBarRegionSamplingViewModel.endSideAppearanceRegionBounds",
            initialValue = Rect(),
            source = _endSideBounds.map { it.appearanceRegion },
        )

    private val startSideSampledAppearanceRegion: Flow<AppearanceRegion> = conflatedCallbackFlow {
        val samplingRegion =
            SamplingRegion(
                purpose = RegionSamplingHelperFactory.Purpose.START_SIDE,
                attachStateView = attachStateView,
                samplingBounds = { startSideSamplingBounds },
                appearanceRegionBounds = { startSideAppearanceRegionBounds },
            ) { appearanceRegion ->
                trySend(appearanceRegion)
            }
        samplingRegion.start()
        awaitClose { samplingRegion.stop() }
    }

    private val endSideSampledAppearanceRegion: Flow<AppearanceRegion> = conflatedCallbackFlow {
        val samplingRegion =
            SamplingRegion(
                purpose = RegionSamplingHelperFactory.Purpose.END_SIDE,
                attachStateView = attachStateView,
                samplingBounds = { endSideSamplingBounds },
                appearanceRegionBounds = { endSideAppearanceRegionBounds },
            ) { appearanceRegion ->
                trySend(appearanceRegion)
            }
        samplingRegion.start()
        awaitClose { samplingRegion.stop() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val topLevelSamplingRegions: Flow<List<AppearanceRegion>> =
        isRegionSamplingEnabled.flatMapLatest { enabled ->
            if (enabled) {
                combine(startSideSampledAppearanceRegion, endSideSampledAppearanceRegion) {
                    startSideAppearanceRegion,
                    endSideAppearanceRegion ->
                    listOf(startSideAppearanceRegion, endSideAppearanceRegion)
                }
            } else {
                // RegionSampling requires extra effort from SurfaceFlinger to inspect the rendered
                // pixels on the display, so stop flow collection as soon as sampling is disabled
                // so that we avoid unnecessary performance-intensive work.
                flowOf(listOf())
            }
        }

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            launch {
                topLevelSamplingRegions.collect { value ->
                    statusBarRegionSamplingInteractor.setSampledAppearanceRegions(displayId, value)
                }
            }
            launch { hydrator.activate() }
            awaitCancellation()
        }
    }

    /** Factory to create a [RegionSamplingHelper] for a Status Bar region. */
    interface RegionSamplingHelperFactory {
        enum class Purpose {
            START_SIDE,
            END_SIDE,
        }

        fun create(
            view: View,
            callback: RegionSamplingHelper.SamplingCallback,
            mainExecutor: Executor,
            backgroundExecutor: Executor,
            purpose: Purpose,
        ): RegionSamplingHelper
    }

    /**
     * Default [RegionSamplingHelperFactory] which invokes the standard constructor to create a real
     * instance of [RegionSamplingHelper].
     */
    private class DefaultRegionSamplingHelperFactory : RegionSamplingHelperFactory {
        override fun create(
            view: View,
            callback: RegionSamplingHelper.SamplingCallback,
            mainExecutor: Executor,
            backgroundExecutor: Executor,
            // Unused, only for identification in testing
            purpose: RegionSamplingHelperFactory.Purpose,
        ): RegionSamplingHelper {
            StatusBarRegionSampling.expectInNewMode()
            return RegionSamplingHelper(view, callback, mainExecutor, backgroundExecutor)
        }
    }

    /**
     * Represents a single region of the status bar that is sampling screen lightness to determine
     * whether the status bar icons in this region should be light or dark.
     *
     * @param purpose the identifier for this region, used to identify the region in testing
     * @param attachStateView the view that [RegionSamplingHelper] will use to listen for attach
     *   state changes and automatically pause/resume the surfaceflinger lightness sampling thread.
     *   This view is not used for any bounds calculations.
     * @param samplingBounds provider for the rect that represents the bounds where
     *   [RegionSamplingHelper] will inspect the lightness of rendered pixels on the display.
     * @param appearanceRegionBounds provider for the rect that represents the [AppearanceRegion]
     *   that will be shared with [StatusBarRegionSamplingInteractor] for this sampled region.
     * @param onAppearanceRegionChanged callback to invoke when the sampled lightness changes
     *   between light and dark.
     */
    private inner class SamplingRegion(
        private val purpose: RegionSamplingHelperFactory.Purpose,
        private val attachStateView: View,
        private val samplingBounds: () -> Rect,
        private val appearanceRegionBounds: () -> Rect,
        private val onAppearanceRegionChanged: (appearanceRegion: AppearanceRegion) -> Unit,
    ) {
        private var regionSamplingHelper: RegionSamplingHelper? = null
        private var regionIsDark: Boolean? = null

        /** Start the [RegionSamplingHelper] represented by this sampling region. */
        fun start() {
            regionSamplingHelper = createRegionSamplingHelper()
            regionSamplingHelper?.start(samplingBounds.invoke())
            regionSamplingHelper?.setWindowVisible(true)
        }

        /** Stop the [RegionSamplingHelper] represented by this sampling region. */
        fun stop() {
            regionSamplingHelper?.stop()
            regionSamplingHelper = null
            regionIsDark = null
        }

        private fun createRegionSamplingHelper(): RegionSamplingHelper =
            regionSamplingHelperFactory.create(
                attachStateView,
                object : RegionSamplingHelper.SamplingCallback {
                    override fun onRegionDarknessChanged(isRegionDark: Boolean) {
                        if (regionIsDark != isRegionDark) {
                            regionIsDark = isRegionDark
                            val appearanceRegion =
                                AppearanceRegion(
                                    if (isRegionDark) {
                                        0
                                    } else {
                                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                    },
                                    appearanceRegionBounds.invoke(),
                                )
                            onAppearanceRegionChanged(appearanceRegion)
                        }
                    }

                    override fun getSampledRegion(unused: View?): Rect {
                        return samplingBounds.invoke()
                    }
                },
                /*mainExecutor=*/ mainExecutor,
                /*backgroundExecutor=*/ backgroundExecutor,
                purpose,
            )
    }

    /**
     * Returns the region on screen where [RegionSamplingHelper] should perform pixel lightness
     * sampling. This region is chosen to be within the status bar container but excluding the
     * status bar icons area, so that sampling includes only the status bar background color and is
     * not influenced by the color of the icons themselves.
     *
     * The following region was chosen to also avoid possible influence by the screen magnification
     * border which is a bright colored outline around the edges of the entire screen.
     */
    private fun getSamplingBounds(containerView: View, iconView: View): Rect {
        val result = containerView.boundsOnScreen
        result.top = iconView.boundsOnScreen.bottom
        return result
    }

    /**
     * Returns the region on screen that represents the [AppearanceRegion] to be shared with
     * [StatusBarRegionSamplingInteractor] for a sampled region.
     *
     * The following region was chosen to include all icons in this side's container, with top=0 as
     * expected by `DarkIconDispatcher#isInArea`.
     */
    private fun getAppearanceRegionBounds(containerView: View): Rect {
        val result = containerView.boundsOnScreen
        result.top = 0
        return result
    }

    @AssistedFactory
    interface Factory {
        /**
         * Factory to create a [StatusBarRegionSamplingViewModel].
         *
         * @param displayId the display ID of the status bar represented by this view model
         * @param attachStateView the view that [RegionSamplingHelper] will use to listen for attach
         *   state changes and automatically pause/resume the surfaceflinger lightness sampling
         *   thread. This view is not used for any bounds calculations.
         * @param startSideContainerView the single view representing the start side container that
         *   holds all start side icons.
         * @param startSideIconView any view that is an icon within the start side container. This
         *   is only used for determining the y-coordinate of icons, so any icon in the container is
         *   sufficient.
         * @param endSideContainerView the single view representing the end side container that
         *   holds all end side icons.
         * @param endSideIconView any view that is an icon within the end side container. This is
         *   only used for determining the y-coordinate of icons, so any icon in the container is
         *   sufficient.
         * @param regionSamplingHelperFactory factory to create the [RegionSamplingHelper]s used in
         *   this view model. The default param value creates real [RegionSamplingHelper]s; this
         *   factory is only needed so that tests can provide mock [RegionSamplingHelper]s.
         */
        fun create(
            displayId: Int,
            @Assisted("attachStateView") attachStateView: View,
            @Assisted("startSideContainerView") startSideContainerView: View,
            @Assisted("startSideIconView") startSideIconView: View,
            @Assisted("endSideContainerView") endSideContainerView: View,
            @Assisted("endSideIconView") endSideIconView: View,
            @Assisted
            regionSamplingHelperFactory: RegionSamplingHelperFactory =
                DefaultRegionSamplingHelperFactory(),
        ): StatusBarRegionSamplingViewModel
    }
}
