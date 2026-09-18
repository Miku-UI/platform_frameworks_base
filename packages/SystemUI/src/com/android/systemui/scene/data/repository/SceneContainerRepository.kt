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

package com.android.systemui.scene.data.repository

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.scene.shared.model.SceneContainerConfig
import com.android.systemui.scene.shared.model.SceneDataSource
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
@SysUISingleton
/** Source of truth for scene framework application state. */
class SceneContainerRepository
@Inject
constructor(
    @Application applicationScope: CoroutineScope,
    config: SceneContainerConfig,
    private val dataSource: SceneDataSource,
) : SceneDataSource by dataSource {

    /**
     * The keys of all scenes and overlays in the container.
     *
     * They will be sorted in z-order such that the last one is the one that should be rendered on
     * top of all previous ones.
     */
    val allContentKeys: List<ContentKey> = config.sceneKeys + config.overlayKeys

    /**
     * Whether there's an ongoing remotely-initiated user interaction.
     *
     * For more information see the logic in `SceneInteractor` that mutates this.
     */
    var isRemoteUserInputOngoing: Boolean by mutableStateOf(false)

    /** Whether there's ongoing user input on the scene container Composable hierarchy */
    var isSceneContainerUserInputOngoing by mutableStateOf(false)

    private val defaultTransitionStateFlowValue =
        ObservableTransitionState.Idle(config.initialSceneKey)
    private val _transitionStateFlow = MutableStateFlow<Flow<ObservableTransitionState>?>(null)
    val transitionStateFlow: StateFlow<ObservableTransitionState> =
        _transitionStateFlow
            .flatMapLatest { innerFlowOrNull ->
                innerFlowOrNull ?: flowOf(defaultTransitionStateFlowValue)
            }
            .stateIn(
                scope = applicationScope,
                started = SharingStarted.Eagerly,
                initialValue = defaultTransitionStateFlowValue,
            )

    /**
     * Number of currently active transition animations. Note: this is _not_ the authoritative
     * source of truth for this state and is merely a local copy of it.
     */
    var activeTransitionAnimationCount: Int by mutableIntStateOf(0)

    /**
     * The [TransitionState.Idle] scene that's currently being composed by the UI or `null`, if none
     * is being composed or if not currently `Idle`.
     */
    var composedIdleScene: SceneKey? by mutableStateOf<SceneKey?>(null)

    /**
     * Whether the device is provisioned (setup wizard finished). Note: this is _not_ the
     * authoritative source of truth for this state and is merely a local copy of it.
     */
    var isDeviceProvisioned: Boolean by mutableStateOf(false)

    /**
     * Whether the device is unlocked. Note: this is _not_ the authoritative source of truth for
     * this state and is merely a local copy of it.
     */
    var isDeviceUnlocked: Boolean by mutableStateOf(false)

    /**
     * Whether the alternate bouncer is visible. Note: this is _not_ the authoritative source of
     * truth for this state and is merely a local copy of it.
     */
    var isAlternateBouncerVisible: Boolean by mutableStateOf(false)

    /**
     * Whether any heads-up notification (HUN) is visible. Note: this is _not_ the authoritative
     * source of truth for this state and is merely a local copy of it.
     */
    var isHeadsUpVisible: Boolean by mutableStateOf(false)

    /**
     * Whether the surface behind System UI is animating. Note: this is _not_ the authoritative
     * source of truth for this state and is merely a local copy of it.
     */
    var isSurfaceBehindAnimating: Boolean by mutableStateOf(false)

    /**
     * Binds the given flow so the system remembers it.
     *
     * Note that you must call is with `null` when the UI is done or risk a memory leak.
     */
    fun setTransitionState(transitionState: Flow<ObservableTransitionState>?) {
        _transitionStateFlow.value = transitionState
    }
}
