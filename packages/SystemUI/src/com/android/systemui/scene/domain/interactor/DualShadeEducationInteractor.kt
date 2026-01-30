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

package com.android.systemui.scene.domain.interactor

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.IntRect
import com.android.compose.animation.scene.OverlayKey
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.lifecycle.ExclusiveActivatable
import com.android.systemui.scene.data.repository.DualShadeEducationRepository
import com.android.systemui.scene.domain.model.DualShadeEducationModel
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.DualShadeEducationElement
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.user.domain.interactor.SelectedUserInteractor
import com.android.systemui.util.kotlin.logD
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@SysUISingleton
class DualShadeEducationInteractor
@Inject
constructor(
    @Background private val backgroundScope: CoroutineScope,
    private val sceneInteractor: SceneInteractor,
    private val shadeModeInteractor: ShadeModeInteractor,
    private val selectedUserInteractor: SelectedUserInteractor,
    private val repository: DualShadeEducationRepository,
) : ExclusiveActivatable(), CoreStartable {

    /** The education that's still needed, regardless of the tooltip that needs to be shown. */
    var education: DualShadeEducationModel by mutableStateOf(DualShadeEducationModel.None)
        private set

    val elementBounds: Map<DualShadeEducationElement, IntRect>
        get() = repository.elementBounds

    override fun start() {
        if (!SceneContainerFlag.isEnabled) {
            return
        }

        backgroundScope.launch { activate() }
    }

    override suspend fun onActivated(): Nothing {
        coroutineScope {
            launch { hydrateRepository() }
            launch { showTooltipsAsNeeded() }
        }
        awaitCancellation()
    }

    fun recordNotificationsShadeTooltipImpression() {
        logD(TAG) { "recording notification shade tooltip impression" }
        backgroundScope.launch { repository.setEverShownNotificationsTooltip(true) }
    }

    fun recordQuickSettingsShadeTooltipImpression() {
        logD(TAG) { "recording quick settings shade tooltip impression" }
        backgroundScope.launch { repository.setEverShownQuickSettingsTooltip(true) }
    }

    fun dismissNotificationsShadeTooltip() {
        logD(TAG) { "marking notification shade tooltip as dismissed" }
        backgroundScope.launch {
            if (education == DualShadeEducationModel.ForNotificationsShade) {
                education = DualShadeEducationModel.None
            }
        }
    }

    fun dismissQuickSettingsShadeTooltip() {
        logD(TAG) { "marking quick settings shade tooltip as dismissed" }
        backgroundScope.launch {
            if (education == DualShadeEducationModel.ForQuickSettingsShade) {
                education = DualShadeEducationModel.None
            }
        }
    }

    fun onDualShadeEducationElementBoundsChange(
        element: DualShadeEducationElement,
        bounds: IntRect,
    ) {
        repository.setElementBounds(element, bounds)
    }

    /** Keeps the repository data fresh for the selected user. */
    private suspend fun hydrateRepository() {
        repeatWhenUserSelected { selectedUserId -> repository.activateFor(selectedUserId) }
    }

    /**
     * Each time the selected user changes, kicks off a new execution of [cancellable] after
     * cancelling any previous execution of it.
     */
    private suspend fun repeatWhenUserSelected(cancellable: suspend (selectedUserId: Int) -> Unit) {
        logD(TAG) { "Monitoring selected user" }
        selectedUserInteractor.selectedUser.collectLatest { selectedUserId ->
            logD(TAG) { "Selected user changed to $selectedUserId" }
            cancellable(selectedUserId)
        }
    }

    /** Shows educational tooltips when and as needed. */
    private suspend fun showTooltipsAsNeeded() {
        repeatWhenDualShadeMode {
            coroutineScope {
                launch {
                    repeatWhenTooltipStillNeedsToBeShown(forOverlay = Overlays.NotificationsShade) {
                        repeatWhenOverlayShown(Overlays.QuickSettingsShade) {
                            repository.setEverShownQuickSettingsShade(true)
                            showTooltip(
                                shownOverlay = Overlays.QuickSettingsShade,
                                overlayToEducateAbout = Overlays.NotificationsShade,
                            )
                        }
                    }
                }

                launch {
                    repeatWhenTooltipStillNeedsToBeShown(forOverlay = Overlays.QuickSettingsShade) {
                        repeatWhenOverlayShown(Overlays.NotificationsShade) {
                            repository.setEverShownNotificationsShade(true)
                            showTooltip(
                                shownOverlay = Overlays.NotificationsShade,
                                overlayToEducateAbout = Overlays.QuickSettingsShade,
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Each time the shade mode becomes dual shade, kicks off a new execution of [cancellable]. If
     * the shade mode isn't dual shade, any previous execution of [cancellable] is cancelled.
     */
    private suspend fun repeatWhenDualShadeMode(cancellable: suspend () -> Unit) {
        logD(TAG) { "Monitoring dual shade mode" }
        shadeModeInteractor.shadeMode
            .map { it == ShadeMode.Dual }
            .distinctUntilChanged()
            .collectLatest { isDualShade ->
                if (isDualShade) {
                    logD(TAG) { "Shade mode is dual" }
                    cancellable()
                } else {
                    logD(TAG) { "Shade mode is not dual" }
                }
            }
    }

    /**
     * Each time the educational tooltip for [forOverlay] still needs to be shown, kicks off a new
     * execution of [cancellable]. If it doesn't need to be shown anymore, any previous execution of
     * [cancellable] is cancelled.
     */
    private suspend fun repeatWhenTooltipStillNeedsToBeShown(
        forOverlay: OverlayKey,
        cancellable: suspend () -> Unit,
    ) {
        logD(TAG) { "Monitoring impressions for ${forOverlay.debugName}" }
        snapshotFlow { repository.impressions }
            .map { impressions ->
                val everShownOverlay =
                    when (forOverlay) {
                        Overlays.NotificationsShade -> impressions.everShownNotificationsShade
                        Overlays.QuickSettingsShade -> impressions.everShownQuickSettingsShade
                        else -> error("Unsupported overlay \"${forOverlay.debugName}\"")
                    }
                if (everShownOverlay) {
                    logD(TAG) {
                        "Overlay ${forOverlay.debugName} already shown, no need to show tooltip"
                    }
                    return@map false
                }

                val everShownTooltip =
                    when (forOverlay) {
                        Overlays.NotificationsShade -> impressions.everShownNotificationsTooltip
                        Overlays.QuickSettingsShade -> impressions.everShownQuickSettingsTooltip
                        else -> error("Unsupported overlay \"${forOverlay.debugName}\"")
                    }
                if (everShownTooltip) {
                    logD(TAG) {
                        "Tooltip for overlay ${forOverlay.debugName} already shown, no need to show it again"
                    }
                    return@map false
                }

                logD(TAG) {
                    "Overlay ${forOverlay.debugName} never shown, still need to show tooltip for it"
                }
                return@map true
            }
            .distinctUntilChanged()
            .collectLatest { isTooltipStillNeeded ->
                if (isTooltipStillNeeded) {
                    cancellable()
                }
            }
    }

    /**
     * Each time the [overlay] becomes shown, kicks off a new execution of [cancellable]. If the
     * overlay is hidden, any previous execution of [cancellable] is cancelled.
     */
    private suspend fun repeatWhenOverlayShown(
        overlay: OverlayKey,
        cancellable: suspend () -> Unit,
    ) {
        logD(TAG) { "Waiting for overlay ${overlay.debugName} to be shown" }
        sceneInteractor.currentOverlays
            .map { it.contains(overlay) }
            .distinctUntilChanged()
            .collectLatest { isOverlayShown ->
                if (isOverlayShown) {
                    logD(TAG) { "${overlay.debugName} shown, reacting" }
                    cancellable()
                }
            }
    }

    private suspend fun showTooltip(shownOverlay: OverlayKey, overlayToEducateAbout: OverlayKey) {
        try {
            logD(TAG) {
                "${shownOverlay.debugName} shown, waiting ${TOOLTIP_APPEARANCE_DELAY_MS}ms before starting to educate about ${overlayToEducateAbout.debugName}"
            }

            delay(TOOLTIP_APPEARANCE_DELAY_MS)
            logD(TAG) {
                "Done waiting ${TOOLTIP_APPEARANCE_DELAY_MS}ms, showing tooltip for ${overlayToEducateAbout.debugName}"
            }
            education = DualShadeEducationModel.None
            education =
                when (overlayToEducateAbout) {
                    Overlays.NotificationsShade -> DualShadeEducationModel.ForNotificationsShade
                    Overlays.QuickSettingsShade -> DualShadeEducationModel.ForQuickSettingsShade
                    else -> DualShadeEducationModel.None
                }
        } catch (e: CancellationException) {
            logD(TAG) {
                "Canceled education for ${overlayToEducateAbout.debugName}, resetting state"
            }
            education = DualShadeEducationModel.None
        }
    }

    companion object {
        private const val TAG = "DualShadeEducation"
        @VisibleForTesting const val TOOLTIP_APPEARANCE_DELAY_MS = 5000L
    }
}

@Module
interface DualShadeEducationInteractorModule {
    @Binds
    @IntoMap
    @ClassKey(DualShadeEducationInteractor::class)
    fun dualShadeEducationInteractor(impl: DualShadeEducationInteractor): CoreStartable
}
