/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.notifications.ui.composable

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScrollStackWithNestedScrollTest : SysuiTestCase() {

    @get:Rule val rule = createComposeRule()

    @Test
    fun scrollUp() = runTest {
        val scrimOffset = mutableStateOf(0f)
        val scrollState = ScrollState(100)
        val dispatcher = NestedScrollDispatcher()

        setTestContent(
            dispatcher = dispatcher,
            scrimOffset = scrimOffset,
            minScrimOffset = -200f,
            maxScrimOffset = 0f,
        )

        val result =
            scrollStackBy(
                delta = Offset(0f, 150f),
                nestedScrollDispatcher = dispatcher,
                scrollState = scrollState,
            )

        assertThat(result.y).isEqualTo(150f)
        assertThat(scrimOffset.value).isEqualTo(-150f)
        assertThat(scrollState.value).isEqualTo(100)
    }

    @Test
    fun scrollDown() = runTest {
        val scrimOffset = mutableStateOf(-200f)
        val scrollState = ScrollState(200)
        val dispatcher = NestedScrollDispatcher()

        setTestContent(
            dispatcher = dispatcher,
            scrimOffset = scrimOffset,
            minScrimOffset = -200f,
            maxScrimOffset = 0f,
        )

        val result =
            scrollStackBy(
                delta = Offset(0f, -150f),
                nestedScrollDispatcher = dispatcher,
                scrollState = scrollState,
            )

        assertThat(result.y).isEqualTo(-150f)
        assertThat(scrimOffset.value).isEqualTo(-200f)
        assertThat(scrollState.value).isEqualTo(50)
    }

    @Test
    fun scrollUp_scrimAlreadyCollapsed_listScrolls() = runTest {
        val scrimOffset = mutableStateOf(-200f)
        val scrollState = ScrollState(100)
        val dispatcher = NestedScrollDispatcher()

        setTestContent(
            dispatcher = dispatcher,
            scrimOffset = scrimOffset,
            minScrimOffset = -200f,
            maxScrimOffset = 0f,
        )

        val result =
            scrollStackBy(
                delta = Offset(0f, 150f),
                nestedScrollDispatcher = dispatcher,
                scrollState = scrollState,
            )

        assertThat(result.y).isEqualTo(150f)
        assertThat(scrimOffset.value).isEqualTo(-200f)
        assertThat(scrollState.value).isEqualTo(250)
    }

    @Test
    fun scrollUp_scrimCollapses_thenListScrolls() = runTest {
        val scrimOffset = mutableStateOf(-100f)
        val scrollState = ScrollState(100)
        val dispatcher = NestedScrollDispatcher()

        setTestContent(
            dispatcher = dispatcher,
            scrimOffset = scrimOffset,
            minScrimOffset = -200f,
            maxScrimOffset = 0f,
        )

        val result =
            scrollStackBy(
                delta = Offset(0f, 150f),
                nestedScrollDispatcher = dispatcher,
                scrollState = scrollState,
            )

        assertThat(result.y).isEqualTo(150f)
        assertThat(scrimOffset.value).isEqualTo(-200f)
        assertThat(scrollState.value).isEqualTo(150)
    }

    @Test
    fun scrollDown_listAtTop_scrimExpands() = runTest {
        val scrimOffset = mutableStateOf(-200f)
        val scrollState = ScrollState(0)
        val dispatcher = NestedScrollDispatcher()

        setTestContent(
            dispatcher = dispatcher,
            scrimOffset = scrimOffset,
            minScrimOffset = -200f,
            maxScrimOffset = 0f,
        )

        val result =
            scrollStackBy(
                delta = Offset(0f, -150f),
                nestedScrollDispatcher = dispatcher,
                scrollState = scrollState,
            )

        assertThat(result.y).isEqualTo(-150f)
        assertThat(scrimOffset.value).isEqualTo(-50f)
        assertThat(scrollState.value).isEqualTo(0)
    }

    @Test
    fun scrollDown_listReachesTop_thenScrimExpands() = runTest {
        val scrimOffset = mutableStateOf(-200f)
        val scrollState = ScrollState(50)
        val dispatcher = NestedScrollDispatcher()

        setTestContent(
            dispatcher = dispatcher,
            scrimOffset = scrimOffset,
            minScrimOffset = -200f,
            maxScrimOffset = 0f,
        )

        val result =
            scrollStackBy(
                delta = Offset(0f, -150f),
                nestedScrollDispatcher = dispatcher,
                scrollState = scrollState,
            )

        assertThat(result.y).isEqualTo(-150f)
        assertThat(scrimOffset.value).isEqualTo(-100f)
        assertThat(scrollState.value).isEqualTo(0)
    }

    @Suppress("SameParameterValue")
    private fun setTestContent(
        dispatcher: NestedScrollDispatcher,
        scrimOffset: MutableState<Float>,
        minScrimOffset: Float,
        maxScrimOffset: Float,
    ) {
        val flingBehavior =
            object : FlingBehavior {
                override suspend fun ScrollScope.performFling(initialVelocity: Float): Float =
                    initialVelocity
            }

        val connection =
            notificationScrimNestedScrollConnection(
                scrimOffset = { scrimOffset.value },
                snapScrimOffset = { scrimOffset.value = it },
                animateScrimOffset = { scrimOffset.value = it },
                minScrimOffset = { minScrimOffset },
                maxScrimOffset = maxScrimOffset,
                canOverscrollContent = { true },
                flingBehavior = flingBehavior,
            )

        // Parent receives nested scroll events
        rule.setContent {
            Box(Modifier.nestedScroll(connection)) {
                // Child sends nested scroll events using dispatcher
                Box(Modifier.nestedScroll(object : NestedScrollConnection {}, dispatcher))
            }
        }
    }
}
