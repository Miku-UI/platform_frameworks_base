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

package com.android.systemui.headline.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.mechanics.GestureContext
import com.android.systemui.headline.ui.viewmodel.HeadlineItem
import com.android.systemui.headline.ui.viewmodel.fakeHeadlineItems
import kotlinx.coroutines.CompletableDeferred

@Composable
@Preview
private fun HeadlineTriggeredTransitionPreview() {
    // TODO(b/449675581): Use PlatformTheme {} once it works with previews or provide a new
    // PlatformThemeForPreviews {} composable.
    // TODO(b/449675581): The previews don't seem to 100% match what's on device and on the
    // generated screenshots for content that is faded in later.
    MaterialTheme { HeadlineTriggeredTransitionScreen() }
}

@Composable
fun HeadlineTriggeredTransitionScreen() {
    val items = remember { fakeHeadlineItems() }
    Row {
        listOf(1 to 2, 2 to 1).forEach { (fromIndex, toIndex) ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (progress in 0..100 step 10) {
                    HeadlineWithTransitionAtProgress(items, fromIndex, toIndex, progress / 100f)
                }
            }
        }
    }
}

@Composable
internal fun HeadlineWithTransitionAtProgress(
    items: List<HeadlineItem>,
    fromIndex: Int,
    toIndex: Int,
    progress: Float,
    previewProgress: Float = 0f,
    isInitiatedByUserInput: Boolean = false,
) {
    HeadlineWithTransitionAtProgress(
        items = items,
        currentItemIndex = fromIndex,
        fromScene = items[fromIndex].key.toSceneKey(),
        toScene = items[toIndex].key.toSceneKey(),
        progress = progress,
        previewProgress = previewProgress,
        isInitiatedByUserInput = isInitiatedByUserInput,
    )
}

@Composable
internal fun HeadlineWithTransitionAtProgress(
    items: List<HeadlineItem>,
    currentItemIndex: Int?,
    fromScene: SceneKey,
    toScene: SceneKey,
    progress: Float,
    previewProgress: Float = 0f,
    isInitiatedByUserInput: Boolean = false,
) {
    val viewModel = rememberViewModel(items, currentItemIndex)
    FakeHeadline(viewModel)
    LaunchedEffect(Unit) {
        viewModel.state.uiBoundState!!.startTransition(
            fakeTransition(
                fromScene = fromScene,
                toScene = toScene,
                progress = progress,
                previewProgress = previewProgress,
                isInitiatedByUserInput = isInitiatedByUserInput,
            )
        )
    }
}

// TODO(b/449675581): Move this into a new STL preview/testing utils.
internal fun fakeTransition(
    fromScene: SceneKey,
    toScene: SceneKey,
    progress: Float,
    previewProgress: Float = 0f,
    isInitiatedByUserInput: Boolean = false,
): TransitionState.Transition.ChangeScene {
    return object :
        TransitionState.Transition.ChangeScene(fromScene = fromScene, toScene = toScene) {
        override val progress: Float = progress
        override val progressVelocity: Float = 0f
        override val previewProgress: Float = previewProgress
        override val isInitiatedByUserInput: Boolean = isInitiatedByUserInput
        override val isInPreviewStage: Boolean = isInitiatedByUserInput && progress == 0f
        override val isUserInputOngoing: Boolean = isInPreviewStage
        override val currentScene: SceneKey = if (isInPreviewStage) fromScene else toScene
        override val gestureContext: GestureContext? = null

        private val completable = CompletableDeferred<Unit>()

        override suspend fun run() {
            completable.await()
        }

        override fun freezeAndAnimateToCurrentState() {
            completable.complete(Unit)
        }
    }
}
