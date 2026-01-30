/*
 * Copyright (C) 2024 The Android Open Source Project
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

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.android.systemui.shade

import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.SysuiTestableContext
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.data.repository.FakeShadeRepository
import com.android.systemui.shade.data.repository.ShadeRepository
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.shade.domain.interactor.ShadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert

/** Sets up shade state for tests for either value of the scene container flag. */
class ShadeTestUtil(val delegate: ShadeTestUtilDelegate) {

    /** Sets shade expansion to a value between 0-1. */
    fun setShadeExpansion(shadeExpansion: Float) {
        delegate.assertFlagValid()
        delegate.setShadeExpansion(shadeExpansion)
    }

    /** Sets QS expansion to a value between 0-1. */
    fun setQsExpansion(qsExpansion: Float) {
        delegate.assertFlagValid()
        delegate.setQsExpansion(qsExpansion)
    }

    /** Sets both shade and QS expansion. One value must be zero or values must add up to 1f. */
    fun setShadeAndQsExpansion(shadeExpansion: Float, qsExpansion: Float) {
        Assert.assertTrue(
            "One expansion must be zero or both must add up to 1",
            shadeExpansion == 0f || qsExpansion == 0f || shadeExpansion + qsExpansion == 1f,
        )
        delegate.assertFlagValid()
        delegate.setShadeAndQsExpansion(shadeExpansion, qsExpansion)
    }

    /** Sets the shade expansion on the lockscreen to the given amount from 0-1. */
    fun setLockscreenShadeExpansion(lockscreenShadeExpansion: Float) {
        delegate.assertFlagValid()
        delegate.setLockscreenShadeExpansion(lockscreenShadeExpansion)
    }

    /** Sets whether the user is moving the shade with touch input on Lockscreen. */
    fun setLockscreenShadeTracking(lockscreenShadeTracking: Boolean) {
        delegate.assertFlagValid()
        delegate.setLockscreenShadeTracking(lockscreenShadeTracking)
    }

    /** Sets whether the user is moving the shade with touch input. */
    fun setTracking(tracking: Boolean) {
        delegate.assertFlagValid()
        delegate.setTracking(tracking)
    }

    fun setQsFullscreen(qsFullscreen: Boolean) {
        delegate.assertFlagValid()
        delegate.setQsFullscreen(qsFullscreen)

        // If QS is full screen, expansion is 1 and split shade is off.
        delegate.setQsExpansion(1.0f)
    }

    fun setLegacyExpandedOrAwaitingInputTransfer(legacyExpandedOrAwaitingInputTransfer: Boolean) {
        delegate.assertFlagValid()
        delegate.setLegacyExpandedOrAwaitingInputTransfer(legacyExpandedOrAwaitingInputTransfer)
    }
}

/** Sets up shade state for tests for a specific value of the scene container flag. */
interface ShadeTestUtilDelegate {
    /** Asserts that the scene container flag matches this implementation. */
    fun assertFlagValid()

    /** Sets both shade and QS expansion. One value must be zero or values must add up to 1f. */
    fun setShadeAndQsExpansion(shadeExpansion: Float, qsExpansion: Float)

    /** Sets the shade expansion on the lockscreen to the given amount from 0-1. */
    fun setLockscreenShadeExpansion(lockscreenShadeExpansion: Float)

    /** Sets whether the user is moving the shade with touch input. */
    fun setLockscreenShadeTracking(lockscreenShadeTracking: Boolean)

    /** Sets whether the user is moving the shade with touch input. */
    fun setTracking(tracking: Boolean)

    /** Sets shade expansion to a value between 0-1. */
    fun setShadeExpansion(shadeExpansion: Float)

    /** Sets QS expansion to a value between 0-1. */
    fun setQsExpansion(qsExpansion: Float)

    /** Sets the shade to half collapsed with no touch input. */
    fun programmaticCollapseShade()

    fun setQsFullscreen(qsFullscreen: Boolean)

    fun setLegacyExpandedOrAwaitingInputTransfer(legacyExpandedOrAwaitingInputTransfer: Boolean)
}

/** Sets up shade state for tests when the scene container flag is disabled. */
class ShadeTestUtilLegacyImpl(
    val testScope: TestScope,
    val shadeRepository: FakeShadeRepository,
    val context: SysuiTestableContext,
    val shadeInteractor: ShadeInteractor,
) : ShadeTestUtilDelegate {
    override fun setShadeAndQsExpansion(shadeExpansion: Float, qsExpansion: Float) {
        shadeRepository.setLegacyShadeExpansion(shadeExpansion)
        shadeRepository.setQsExpansion(qsExpansion)
        testScope.runCurrent()
    }

    override fun setLockscreenShadeExpansion(lockscreenShadeExpansion: Float) {
        shadeRepository.setLockscreenShadeExpansion(lockscreenShadeExpansion)
    }

    override fun setLockscreenShadeTracking(lockscreenShadeTracking: Boolean) {
        shadeRepository.setLegacyLockscreenShadeTracking(lockscreenShadeTracking)
    }

    override fun setTracking(tracking: Boolean) {
        shadeRepository.setLegacyShadeTracking(tracking)
    }

    override fun assertFlagValid() {
        Assert.assertFalse(SceneContainerFlag.isEnabled)
    }

    /** Sets shade expansion to a value between 0-1. */
    override fun setShadeExpansion(shadeExpansion: Float) {
        shadeRepository.setLegacyShadeExpansion(shadeExpansion)
        processUpdate()
    }

    /** Sets QS expansion to a value between 0-1. */
    override fun setQsExpansion(qsExpansion: Float) {
        shadeRepository.setQsExpansion(qsExpansion)
        processUpdate()
    }

    override fun programmaticCollapseShade() {
        shadeRepository.setLegacyShadeExpansion(.5f)
        processUpdate()
    }

    override fun setQsFullscreen(qsFullscreen: Boolean) {
        shadeRepository.legacyQsFullscreen.value = true
    }

    override fun setLegacyExpandedOrAwaitingInputTransfer(expanded: Boolean) {
        shadeRepository.setLegacyExpandedOrAwaitingInputTransfer(expanded)
    }

    private fun processUpdate() {
        testScope.runCurrent()

        // Requesting a value will cause the stateIn to begin flowing, otherwise incorrect values
        // may not flow fast enough to the stateIn
        shadeInteractor.isAnyFullyExpanded.value
    }
}

/** Sets up shade state for tests when the scene container flag is enabled. */
class ShadeTestUtilSceneImpl(
    val testScope: TestScope,
    val sceneInteractor: SceneInteractor,
    val shadeRepository: ShadeRepository,
    val context: SysuiTestableContext,
    val shadeInteractor: ShadeInteractor,
    val shadeModeInteractor: ShadeModeInteractor,
) : ShadeTestUtilDelegate {
    val isUserInputOngoing = MutableStateFlow(true)

    private val notificationsShade: ContentKey
        get() = if (shadeModeInteractor.isDualShade) Overlays.NotificationsShade else Scenes.Shade

    private val quickSettingsShade: ContentKey
        get() {
            return when (shadeModeInteractor.shadeMode.value) {
                is ShadeMode.Dual -> Overlays.QuickSettingsShade
                is ShadeMode.Single -> Scenes.QuickSettings
                is ShadeMode.Split -> Scenes.Shade
            }
        }

    override fun setShadeAndQsExpansion(shadeExpansion: Float, qsExpansion: Float) {
        shadeRepository.setLegacyIsQsExpanded(qsExpansion > 0f)
        when {
            shadeExpansion == 1f -> setIdleContent(notificationsShade)
            qsExpansion == 1f -> setIdleContent(quickSettingsShade)
            shadeExpansion == 0f && qsExpansion == 0f -> setIdleScene(Scenes.Lockscreen)
            shadeExpansion == 0f ->
                setTransitionProgress(Scenes.Lockscreen, quickSettingsShade, qsExpansion)
            qsExpansion == 0f ->
                setTransitionProgress(Scenes.Lockscreen, notificationsShade, shadeExpansion)
            else -> setTransitionProgress(notificationsShade, quickSettingsShade, qsExpansion)
        }

        // Requesting a value will cause the stateIn to begin flowing, otherwise incorrect values
        // may not flow fast enough to the stateIn.
        shadeInteractor.isAnyFullyExpanded.value
    }

    /** Sets shade expansion to a value between 0-1. */
    override fun setShadeExpansion(shadeExpansion: Float) {
        setShadeAndQsExpansion(shadeExpansion, qsExpansion = 0f)
    }

    /** Sets QS expansion to a value between 0-1. */
    override fun setQsExpansion(qsExpansion: Float) {
        setShadeAndQsExpansion(shadeExpansion = 0f, qsExpansion)
    }

    override fun programmaticCollapseShade() {
        setTransitionProgress(notificationsShade, Scenes.Lockscreen, .5f, false)
    }

    override fun setQsFullscreen(qsFullscreen: Boolean) {
        setQsExpansion(1f)
    }

    override fun setLegacyExpandedOrAwaitingInputTransfer(
        legacyExpandedOrAwaitingInputTransfer: Boolean
    ) {
        setShadeExpansion(.1f)
    }

    override fun setLockscreenShadeExpansion(lockscreenShadeExpansion: Float) {
        when (lockscreenShadeExpansion) {
            0f -> setIdleScene(Scenes.Lockscreen)
            1f ->
                setIdleContent(contentKey = notificationsShade, backgroundScene = Scenes.Lockscreen)
            else ->
                setTransitionProgress(
                    Scenes.Lockscreen,
                    notificationsShade,
                    lockscreenShadeExpansion,
                )
        }
    }

    override fun setLockscreenShadeTracking(lockscreenShadeTracking: Boolean) {
        setTracking(lockscreenShadeTracking)
    }

    override fun setTracking(tracking: Boolean) {
        isUserInputOngoing.value = tracking
    }

    private fun setIdleContent(
        contentKey: ContentKey,
        backgroundScene: SceneKey = sceneInteractor.currentScene.value,
    ) {
        when (contentKey) {
            is SceneKey -> setIdleScene(contentKey)
            is OverlayKey -> setIdleOverlay(contentKey, backgroundScene)
        }
    }

    private fun setIdleScene(scene: SceneKey) {
        sceneInteractor.changeScene(scene, "ShadeTestUtil.setIdleScene")
        sceneInteractor.setTransitionState(flowOf(ObservableTransitionState.Idle(scene)))
        testScope.runCurrent()
    }

    private fun setIdleOverlay(overlay: OverlayKey, currentScene: SceneKey) {
        sceneInteractor.showOverlay(overlay, "ShadeTestUtil.setIdleOnOverlay")
        sceneInteractor.setTransitionState(
            flowOf(
                ObservableTransitionState.Idle(
                    currentScene = currentScene,
                    currentOverlays = setOf(overlay),
                )
            )
        )
        testScope.runCurrent()
    }

    private fun setTransitionProgress(
        from: ContentKey,
        to: ContentKey,
        progress: Float,
        isInitiatedByUserInput: Boolean = true,
    ) {
        val loggingReason = "ShadeTestUtil.setTransitionProgress"
        // Set the initial state
        when (from) {
            is SceneKey -> sceneInteractor.changeScene(from, loggingReason)
            is OverlayKey -> sceneInteractor.showOverlay(from, loggingReason)
        }

        val transitionState =
            when {
                from is SceneKey && to is SceneKey ->
                    ObservableTransitionState.Transition(
                        fromScene = from,
                        toScene = to,
                        currentScene = flowOf(to),
                        progress = flowOf(progress),
                        isInitiatedByUserInput = isInitiatedByUserInput,
                        isUserInputOngoing = isUserInputOngoing,
                    )
                from is SceneKey && to is OverlayKey ->
                    ObservableTransitionState.Transition.showOverlay(
                        overlay = to,
                        fromScene = from,
                        currentOverlays = flowOf(emptySet()),
                        progress = flowOf(progress),
                        isInitiatedByUserInput = isInitiatedByUserInput,
                        isUserInputOngoing = isUserInputOngoing,
                    )
                from is OverlayKey && to is SceneKey ->
                    ObservableTransitionState.Transition.hideOverlay(
                        overlay = from,
                        toScene = to,
                        currentOverlays = flowOf(emptySet()),
                        progress = flowOf(progress),
                        isInitiatedByUserInput = isInitiatedByUserInput,
                        isUserInputOngoing = isUserInputOngoing,
                    )
                from is OverlayKey && to is OverlayKey ->
                    ObservableTransitionState.Transition.ReplaceOverlay(
                        fromOverlay = from,
                        toOverlay = to,
                        currentScene = sceneInteractor.currentScene.value,
                        currentOverlays = flowOf(emptySet()),
                        progress = flowOf(progress),
                        isInitiatedByUserInput = isInitiatedByUserInput,
                        isUserInputOngoing = isUserInputOngoing,
                        previewProgress = flowOf(0f),
                        isInPreviewStage = flowOf(false),
                    )
                else -> error("Invalid content keys for transition: from=$from, to=$to")
            }

        sceneInteractor.setTransitionState(flowOf(transitionState))
        testScope.runCurrent()
    }

    override fun assertFlagValid() {
        Assert.assertTrue(SceneContainerFlag.isEnabled)
    }
}
