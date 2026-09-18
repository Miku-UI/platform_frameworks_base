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

package com.android.systemui.statusbar.notification

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource.Companion.UserInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.notifications.ui.composable.notificationScrimNestedScrollConnection
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class NotificationScrimNestedScrollConnectionTest : SysuiTestCase() {
    private var isStarted = false
    private var wasStarted = false
    private var scrimOffset = 0f
    private var contentAllowsOverscroll = true
    private val customFlingBehavior =
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                scrollBy(initialVelocity)
                return initialVelocity / 2f
            }
        }

    private val scrollConnection =
        notificationScrimNestedScrollConnection(
            scrimOffset = { scrimOffset },
            snapScrimOffset = { _ -> },
            animateScrimOffset = { _ -> },
            minScrimOffset = { MIN_SCRIM_OFFSET },
            maxScrimOffset = MAX_SCRIM_OFFSET,
            canOverscrollContent = { contentAllowsOverscroll },
            onStart = { isStarted = true },
            onStop = {
                wasStarted = true
                isStarted = false
            },
            flingBehavior = customFlingBehavior,
        )

    @Test
    fun onScrollUp_canStartPreScroll_contentNotAllowsOverscroll_ignoreScroll() = runTest {
        contentAllowsOverscroll = false

        val offsetConsumed =
            scrollConnection.onPreScroll(available = Offset(x = 0f, y = -1f), source = UserInput)

        assertThat(offsetConsumed).isEqualTo(Offset.Zero)
        assertThat(isStarted).isEqualTo(false)
    }

    @Test
    fun onScrollUp_canStartPreScroll_zeroAvailableOffset_ignoreScroll() = runTest {
        scrimOffset = MIN_SCRIM_OFFSET

        val offsetConsumed =
            scrollConnection.onPreScroll(available = Offset(x = 0f, y = -1f), source = UserInput)

        assertThat(offsetConsumed).isEqualTo(Offset.Zero)
        assertThat(isStarted).isEqualTo(false)
    }

    @Test
    fun onScrollUp_canStartPreScroll_consumeScroll() = runTest {
        val availableOffset = Offset(x = 0f, y = -1f)
        val offsetConsumed =
            scrollConnection.onPreScroll(available = availableOffset, source = UserInput)

        assertThat(offsetConsumed).isEqualTo(availableOffset)
        assertThat(isStarted).isEqualTo(true)
    }

    @Test
    fun onScrollUp_canStartPreScroll_consumeScrollWithRemainder() = runTest {
        scrimOffset = MIN_SCRIM_OFFSET + 1

        val availableOffset = Offset(x = 0f, y = -2f)
        val consumableOffset = Offset(x = 0f, y = -1f)
        val offsetConsumed =
            scrollConnection.onPreScroll(available = availableOffset, source = UserInput)

        assertThat(offsetConsumed).isEqualTo(consumableOffset)
        assertThat(isStarted).isEqualTo(true)
    }

    @Test
    fun onScrollUp_canStartPostScroll_ignoreScroll() = runTest {
        val offsetConsumed =
            scrollConnection.onPostScroll(
                consumed = Offset.Zero,
                available = Offset(x = 0f, y = -1f),
                source = UserInput,
            )

        assertThat(offsetConsumed).isEqualTo(Offset.Zero)
        assertThat(isStarted).isEqualTo(false)
    }

    @Test
    fun onScrollDown_canStartPreScroll_ignoreScroll() = runTest {
        val offsetConsumed =
            scrollConnection.onPreScroll(available = Offset(x = 0f, y = 1f), source = UserInput)

        assertThat(offsetConsumed).isEqualTo(Offset.Zero)
        assertThat(isStarted).isEqualTo(false)
    }

    @Test
    fun onScrollDown_canStartPostScroll_consumeScroll() = runTest {
        scrimOffset = MIN_SCRIM_OFFSET

        val availableOffset = Offset(x = 0f, y = 1f)
        val offsetConsumed =
            scrollConnection.onPostScroll(
                consumed = Offset.Zero,
                available = availableOffset,
                source = UserInput,
            )

        assertThat(offsetConsumed).isEqualTo(availableOffset)
        assertThat(isStarted).isEqualTo(true)
    }

    @Test
    fun onScrollDown_canStartPostScroll_consumeScrollWithRemainder() = runTest {
        scrimOffset = MAX_SCRIM_OFFSET - 1

        val availableOffset = Offset(x = 0f, y = 2f)
        val consumableOffset = Offset(x = 0f, y = 1f)
        val offsetConsumed =
            scrollConnection.onPostScroll(
                consumed = Offset.Zero,
                available = availableOffset,
                source = UserInput,
            )

        assertThat(offsetConsumed).isEqualTo(consumableOffset)
        assertThat(isStarted).isEqualTo(true)
    }

    @Test
    fun canStartPostScroll_atMaxOffset_ignoreScroll() = runTest {
        scrimOffset = MAX_SCRIM_OFFSET

        val offsetConsumed =
            scrollConnection.onPostScroll(
                consumed = Offset.Zero,
                available = Offset(x = 0f, y = 1f),
                source = UserInput,
            )

        assertThat(offsetConsumed).isEqualTo(Offset.Zero)
        assertThat(wasStarted).isEqualTo(false)
        assertThat(isStarted).isEqualTo(false)
    }

    @Test
    fun canContinueScroll_inBetweenMinMaxOffset_true() = runTest {
        scrimOffset = (MIN_SCRIM_OFFSET + MAX_SCRIM_OFFSET) / 2f
        scrollConnection.onPreScroll(available = Offset(x = 0f, y = -1f), source = UserInput)

        assertThat(isStarted).isEqualTo(true)

        scrollConnection.onPreScroll(available = Offset(x = 0f, y = 1f), source = UserInput)

        assertThat(isStarted).isEqualTo(true)
    }

    @Test
    fun canContinueScroll_upAndDownInBetweenMinMaxOffset_true() = runTest {
        scrimOffset = (MIN_SCRIM_OFFSET + MAX_SCRIM_OFFSET) / 2f

        // Scroll Up
        scrollConnection.onPreScroll(available = Offset(x = 0f, y = -1f), source = UserInput)
        assertThat(isStarted).isEqualTo(true)

        // Scroll down
        scrollConnection.onPreScroll(available = Offset(x = 0f, y = 1f), source = UserInput)
        assertThat(isStarted).isEqualTo(true)

        // Scroll Up again
        scrollConnection.onPreScroll(available = Offset(x = 0f, y = -1f), source = UserInput)
        assertThat(isStarted).isEqualTo(true)
    }

    @Test
    fun canContinueScroll_atMaxOffset_false() = runTest {
        scrimOffset = MAX_SCRIM_OFFSET
        scrollConnection.onPreScroll(available = Offset(x = 0f, y = -1f), source = UserInput)

        assertThat(isStarted).isEqualTo(true)

        scrollConnection.onPreScroll(available = Offset(x = 0f, y = 1f), source = UserInput)

        assertThat(isStarted).isEqualTo(false)
    }

    companion object {
        const val MIN_SCRIM_OFFSET = -100f
        const val MAX_SCRIM_OFFSET = 0f
    }
}
