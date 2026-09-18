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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.headline.ui.viewmodel.HeadlineViewModel
import com.android.systemui.headline.ui.viewmodel.fakeHeadlineItems

@Composable
@Preview
private fun HeadlineGoneTransitionPreview() {
    // TODO(b/449675581): Use PlatformTheme {} once it works with previews or provide a new
    // PlatformThemeForPreviews {} composable.
    // TODO(b/449675581): The previews don't seem to 100% match what's on device and on the
    // generated screenshots for content that is faded in later.
    MaterialTheme { HeadlineGoneTransitionScreen() }
}

@Composable
fun HeadlineGoneTransitionScreen() {
    val items = remember { fakeHeadlineItems() }
    val secondItem = items[1].key.toSceneKey()
    Row {
        listOf(
                TestValues(
                    currentItemIndex = null,
                    fromScene = HeadlineViewModel.GoneScene,
                    toScene = secondItem,
                ),
                TestValues(
                    currentItemIndex = 1,
                    fromScene = secondItem,
                    toScene = HeadlineViewModel.GoneScene,
                ),
            )
            .forEach { (currentItemIndex, fromScene, toScene) ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (progress in 0..100 step 10) {
                        HeadlineWithTransitionAtProgress(
                            items,
                            currentItemIndex,
                            fromScene,
                            toScene,
                            progress / 100f,
                        )
                    }
                }
            }
    }
}

private data class TestValues(
    val currentItemIndex: Int?,
    val fromScene: SceneKey,
    val toScene: SceneKey,
)
