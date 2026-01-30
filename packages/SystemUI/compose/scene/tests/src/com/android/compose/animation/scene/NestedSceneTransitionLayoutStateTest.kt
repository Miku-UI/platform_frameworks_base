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

package com.android.compose.animation.scene

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compose.animation.scene.TestScenes.SceneA
import com.android.compose.animation.scene.TestScenes.SceneB
import com.android.compose.animation.scene.TestScenes.SceneC
import com.android.compose.animation.scene.TestScenes.SceneD
import com.android.compose.animation.scene.TestScenes.SceneE
import com.android.compose.test.transition
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NestedSceneTransitionLayoutStateTest {
    @Test
    fun isIdle() = runTest {
        val ancestors =
            listOf(
                MutableSceneTransitionLayoutStateForTests(SceneA),
                MutableSceneTransitionLayoutStateForTests(SceneB),
                MutableSceneTransitionLayoutStateForTests(SceneC),
            )
        val state = MutableSceneTransitionLayoutStateForTests(SceneD)
        val nestedState = NestedSceneTransitionLayoutState(ancestors, state)

        // Idle.
        assertThat(nestedState.isIdle(SceneA)).isTrue()
        assertThat(nestedState.isIdle(SceneB)).isTrue()
        assertThat(nestedState.isIdle(SceneC)).isTrue()
        assertThat(nestedState.isIdle(SceneD)).isTrue()
        assertThat(nestedState.isIdle(SceneE)).isFalse()

        assertThat(nestedState.isTransitioning()).isFalse()

        // Transition.
        ancestors[1].startTransitionImmediately(backgroundScope, transition(SceneB, SceneE))

        assertThat(nestedState.isIdle(SceneA)).isFalse()
        assertThat(nestedState.isIdle(SceneB)).isFalse()
        assertThat(nestedState.isIdle(SceneC)).isFalse()
        assertThat(nestedState.isIdle(SceneD)).isFalse()
        assertThat(nestedState.isIdle(SceneE)).isFalse()

        assertThat(nestedState.isTransitioning()).isTrue()
        assertThat(nestedState.isTransitioningBetween(SceneB, SceneE)).isTrue()
        assertThat(nestedState.isTransitioning(from = SceneB, to = SceneE)).isTrue()
        assertThat(nestedState.isTransitioning(from = SceneE, to = SceneB)).isFalse()
        assertThat(nestedState.isTransitioningFromOrTo(SceneA)).isFalse()
        assertThat(nestedState.isTransitioningFromOrTo(SceneB)).isTrue()
        assertThat(nestedState.isTransitioningFromOrTo(SceneC)).isFalse()
        assertThat(nestedState.isTransitioningFromOrTo(SceneD)).isFalse()
        assertThat(nestedState.isTransitioningFromOrTo(SceneE)).isTrue()
    }
}
