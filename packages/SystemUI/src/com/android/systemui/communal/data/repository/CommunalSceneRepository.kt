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

package com.android.systemui.communal.data.repository

import android.content.res.Configuration
import androidx.annotation.MainThread
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.TransitionKey
import com.android.systemui.communal.shared.model.CommunalSceneDataSourceDelegator
import com.android.systemui.communal.shared.model.CommunalScenes
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.scene.shared.model.NoOpSceneDataSource
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** Encapsulates the state of communal mode. */
interface CommunalSceneRepository {

    /** Exposes the transition state of the communal [SceneTransitionLayout]. */
    val transitionStateFlow: StateFlow<ObservableTransitionState>

    /**
     * The current scene, as seen by the real data source in the UI layer.
     *
     * During a transition between two scenes, the original scene will still be reflected in
     * [currentScene] until a time when the UI layer decides to commit the change, which is when
     * [currentScene] will have the value of the target/new scene.
     */
    val currentScene: StateFlow<SceneKey>

    /** Current orientation of the communal container. */
    val communalContainerOrientation: StateFlow<Int>

    /** Shows the hub from a power button press. */
    @MainThread fun showHubFromPowerButton()

    /**
     * Updates the transition state of the hub [SceneTransitionLayout].
     *
     * Note that you must call is with `null` when the UI is done or risk a memory leak.
     */
    fun setTransitionState(transitionState: Flow<ObservableTransitionState>?)

    /** Set the current orientation of the communal container. */
    fun setCommunalContainerOrientation(orientation: Int)

    /**
     * Asks for an asynchronous scene switch to [toScene], which will use the corresponding
     * installed transition or the one specified by [transitionKey], if provided.
     */
    fun changeScene(toScene: SceneKey, transitionKey: TransitionKey? = null)

    /**
     * Asks for an instant switch to [scene] and [overlays].
     *
     * The change is instantaneous and not animated; it will be observable in the next frame and
     * there will be no transition animation.
     *
     * When either one of [scene] or [overlays] is `null`, their current value in the scene
     * transition layout state is used.
     */
    fun instantlyTransitionTo(scene: SceneKey? = null, overlays: Set<OverlayKey>? = null)
}

@OptIn(ExperimentalCoroutinesApi::class)
@SysUISingleton
class CommunalSceneRepositoryImpl
@Inject
constructor(
    @Background backgroundScope: CoroutineScope,
    private val delegator: CommunalSceneDataSourceDelegator,
) : CommunalSceneRepository {

    private val defaultTransitionState = ObservableTransitionState.Idle(CommunalScenes.Default)
    private val _transitionState = MutableStateFlow<Flow<ObservableTransitionState>?>(null)
    override val transitionStateFlow: StateFlow<ObservableTransitionState> =
        _transitionState
            .flatMapLatest { innerFlowOrNull -> innerFlowOrNull ?: flowOf(defaultTransitionState) }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.Lazily,
                initialValue = defaultTransitionState,
            )

    override val currentScene: StateFlow<SceneKey> =
        transitionStateFlow
            .flatMapLatestConflated { transitionState -> transitionState.currentScene() }
            .stateIn(
                scope = backgroundScope,
                started = SharingStarted.WhileSubscribed(),
                initialValue = defaultTransitionState.currentScene,
            )

    private val _communalContainerOrientation =
        MutableStateFlow(Configuration.ORIENTATION_UNDEFINED)
    override val communalContainerOrientation: StateFlow<Int> =
        _communalContainerOrientation.asStateFlow()

    override fun setCommunalContainerOrientation(orientation: Int) {
        _communalContainerOrientation.value = orientation
    }

    @MainThread
    override fun showHubFromPowerButton() {
        // If keyguard is not showing yet, the hub view is not ready and the
        // [SceneDataSourceDelegator] will still be using the default [NoOpSceneDataSource]
        // and initial key, which is Blank. This means that when the hub container loads, it
        // will default to not showing the hub. Attempting to set the scene in this state
        // is simply ignored by the [NoOpSceneDataSource]. Instead, we temporarily override
        // it with a new one that defaults to Communal. This delegate will be overwritten
        // once the [CommunalContainer] loads.
        // TODO(b/392969914): show the hub first instead of forcing the scene.
        delegator.setDelegate(NoOpSceneDataSource(CommunalScenes.Communal))
    }

    /**
     * Updates the transition state of the hub [SceneTransitionLayout].
     *
     * Note that you must call is with `null` when the UI is done or risk a memory leak.
     */
    override fun setTransitionState(transitionState: Flow<ObservableTransitionState>?) {
        _transitionState.value = transitionState
    }

    override fun changeScene(toScene: SceneKey, transitionKey: TransitionKey?) {
        delegator.changeScene(toScene, transitionKey)
    }

    override fun instantlyTransitionTo(scene: SceneKey?, overlays: Set<OverlayKey>?) {
        delegator.instantlyTransitionTo(scene, overlays)
    }
}
