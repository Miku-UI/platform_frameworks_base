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

package com.android.systemui.bouncer.ui.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.android.compose.animation.scene.HoistedSceneTransitionLayoutState
import com.android.compose.animation.scene.SceneTransitionLayout
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.compose.animation.scene.transitions
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.ui.composable.Overlay
import com.android.systemui.scene.ui.composable.transitions.sharedBouncerTransitions
import com.android.systemui.scene.ui.viewmodel.SceneContainerViewModel
import com.android.systemui.scene.ui.viewmodel.ToBouncerTransitionViewModel

/**
 * A [SceneTransitionLayout] that contains an empty scene and the bouncer overlay.
 *
 * This is meant as a solution for showing the bouncer overlay in its own layer (or even window)
 * above the notifications, which are themselves always on-top. This composable is pretty bare bones
 * because the majority of its logic is hoisted in the [state] that's provided to it.
 */
@Composable
fun BouncerSceneContainer(
    viewModel: SceneContainerViewModel,
    state: HoistedSceneTransitionLayoutState,
    bouncerOverlay: Overlay,
    toBouncerTransitionViewModel: ToBouncerTransitionViewModel,
    modifier: Modifier = Modifier,
) {
    var userActions by remember { mutableStateOf(emptyMap<UserAction, UserActionResult>()) }
    LaunchedEffect(viewModel, bouncerOverlay) {
        viewModel.filteredUserActions(bouncerOverlay.userActions).collect { userActions = it }
    }

    SceneTransitionLayout(
        state = state,
        transitions =
            transitions {
                sharedBouncerTransitions(
                    toBouncerTransitionViewModel = toBouncerTransitionViewModel
                )
            },
        modifier = modifier,
        debugName = "BouncerTopSTL",
    ) {
        scene(key = Scenes.Gone, userActions = emptyMap()) { Box(Modifier.fillMaxSize()) }

        overlay(key = Overlays.Bouncer, userActions = userActions) {
            with(bouncerOverlay) { this@overlay.Content(Modifier) }
        }
    }
}
