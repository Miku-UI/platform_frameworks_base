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

package com.android.systemui.scene.shared.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.TransitionKey
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.systemui.kosmos.currentValue
import com.android.systemui.lifecycle.HydratedActivatable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope

class FakeSceneDataSource(initialSceneKey: SceneKey, val testScope: TestScope) :
    SceneDataSource, HydratedActivatable() {

    private val _currentScene = MutableStateFlow(initialSceneKey)
    override val currentScene: StateFlow<SceneKey> = _currentScene.asStateFlow()

    var currentSceneTransitionKey: TransitionKey? = null

    var isPaused: Boolean by mutableStateOf(false)

    /**
     * The actual state that is served from this data source, regardless of the value of [isPaused].
     */
    private var _transitionState: TransitionState by
        mutableStateOf(TransitionState.Idle(initialSceneKey))

    /** Accumulates state changes while [isPaused] is `true`. */
    private var pausedTransitionState: TransitionState = _transitionState

    override var transitionState: TransitionState
        set(value) {
            if (isPaused) {
                // While paused, all updates go to the other field and are not reflected back
                // through this property.
                pausedTransitionState = value
            } else {
                // While unpaused, all updates go directly to the backing property and are reflected
                // immediately.
                _transitionState = value
            }
        }
        get() = _transitionState

    // Returns the "accumulated" TransitionState, based on the value of isPaused. This will be the
    // "real" TransitionState as it's been changing, regardless of whether we're in a pause or not.
    // It's different from transitionState because that one always serves what the user/test should
    // see and not what the internal state might be, which is dependant on the state of isPaused.
    // Use this to make modifications to either the real state when not paused or the paused state
    // during a pause or your risk A BUG where you drop accumulated state during a pause by
    // modifying the wrong state.
    private val accumulatedTransitionState: TransitionState
        get() = if (isPaused) pausedTransitionState else _transitionState

    private val _currentOverlays = MutableStateFlow<Set<OverlayKey>>(emptySet())
    override val currentOverlays: StateFlow<Set<OverlayKey>> = _currentOverlays.asStateFlow()

    private var _pendingScene: SceneKey? = null
    val pendingScene
        get() = testScope.currentValue { _pendingScene }

    private var _pendingSceneTransitionKey: TransitionKey? = null

    var pendingOverlays: Set<OverlayKey>? = null
        private set

    var freezeAndAnimateToCurrentStateCallCount = 0

    override fun changeScene(toScene: SceneKey, transitionKey: TransitionKey?) {
        if (isPaused) {
            _pendingScene = toScene
            _pendingSceneTransitionKey = transitionKey
        } else {
            currentSceneTransitionKey = transitionKey
            _currentScene.value = toScene
        }

        transitionState =
            TransitionState.Idle(
                currentScene = toScene,
                currentOverlays = accumulatedTransitionState.currentOverlays,
            )
    }

    override fun showOverlay(overlay: OverlayKey, transitionKey: TransitionKey?) {
        if (isPaused) {
            pendingOverlays = (pendingOverlays ?: currentOverlays.value) + overlay
        } else {
            _currentOverlays.value += overlay
        }

        transitionState =
            TransitionState.Idle(
                currentScene = accumulatedTransitionState.currentScene,
                currentOverlays = accumulatedTransitionState.currentOverlays + overlay,
            )
    }

    override fun hideOverlay(overlay: OverlayKey, transitionKey: TransitionKey?) {
        if (isPaused) {
            pendingOverlays = (pendingOverlays ?: currentOverlays.value) - overlay
        } else {
            _currentOverlays.value -= overlay
        }

        transitionState =
            TransitionState.Idle(
                currentScene = accumulatedTransitionState.currentScene,
                currentOverlays = accumulatedTransitionState.currentOverlays - overlay,
            )
    }

    override fun replaceOverlay(from: OverlayKey, to: OverlayKey, transitionKey: TransitionKey?) {
        hideOverlay(from, transitionKey)
        showOverlay(to, transitionKey)
    }

    override fun freezeAndAnimateToCurrentState() {
        freezeAndAnimateToCurrentStateCallCount++
    }

    override fun instantlyTransitionTo(scene: SceneKey?, overlays: Set<OverlayKey>?) {
        if (isPaused) {
            _pendingScene = scene
            _pendingSceneTransitionKey = null
            pendingOverlays = overlays
        } else {
            if (scene != null) {
                _currentScene.value = scene
                currentSceneTransitionKey = null
            }
            if (overlays != null) {
                _currentOverlays.value = overlays
            }
        }

        transitionState =
            TransitionState.Idle(
                currentScene = scene ?: accumulatedTransitionState.currentScene,
                currentOverlays = overlays ?: accumulatedTransitionState.currentOverlays,
            )
    }

    override fun startTransitionImmediately(transition: TransitionState.Transition) {
        if (isPaused) {
            _pendingScene = transition.currentScene
        } else {
            _currentScene.value = transition.currentScene
        }
        transitionState = transition
    }

    /**
     * Pauses scene and overlay changes.
     *
     * Any following calls to [changeScene] or overlay changing functions will be conflated and the
     * last one will be remembered.
     */
    fun pause() {
        check(!isPaused) { "Can't pause what's already paused!" }

        pausedTransitionState = transitionState
        isPaused = true
    }

    /**
     * Unpauses scene and overlay changes.
     *
     * If there were any calls to [changeScene] since [pause] was called, the latest of the bunch
     * will be replayed.
     *
     * If there were any calls to show, hide or replace overlays since [pause] was called, they will
     * all be applied at once.
     *
     * If [force] is `true`, there will be no check that [isPaused] is true.
     *
     * If [expectedScene] is provided, will assert that it's indeed the latest called.
     *
     * If [expectedOverlays] is provided, will assert they are indeed present.
     */
    fun unpause(
        force: Boolean = false,
        expectedScene: SceneKey? = null,
        expectedOverlays: Set<OverlayKey>? = null,
    ) {
        check(force || isPaused) { "Can't unpause what's already not paused!" }

        // When unpaused, all changes that were accumulated during the pause are committed into the
        // real property and reflected back to the downstream.
        _transitionState = pausedTransitionState
        isPaused = false
        _pendingScene?.let {
            _currentScene.value = it
            currentSceneTransitionKey = _pendingSceneTransitionKey
        }

        _pendingScene = null
        _pendingSceneTransitionKey = null
        pendingOverlays?.let { _currentOverlays.value = it }
        pendingOverlays = null

        if (expectedScene != null) {
            check(expectedScene == currentScene.value) {
                "Unexpected currentScene flow value of ${currentScene.value.debugName}, expected:" +
                    " ${expectedScene.debugName}"
            }
            check(expectedScene == transitionState.currentScene) {
                "Unexpected transitionState.currentScene value of" +
                    " ${transitionState.currentScene.debugName}, expected:" +
                    " ${expectedScene.debugName}"
            }
        }

        if (expectedOverlays != null) {
            check(expectedOverlays == currentOverlays.value) {
                "Unexpected expectedOverlays flow value of" +
                    " ${currentOverlays.value.joinToString { it.debugName }}, expected:" +
                    " ${expectedOverlays.joinToString { it.debugName }}"
            }
            check(expectedOverlays == transitionState.currentOverlays) {
                "Unexpected transitionState.currentOverlays value of" +
                    " ${transitionState.currentOverlays.joinToString { it.debugName }}," +
                    " expected: ${expectedOverlays.joinToString { it.debugName }}"
            }
        }
    }
}
