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

package com.android.compose.lifecycle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.awaitCancellation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EffectWithLifecycleTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun launchedEffect() {
        testEffectWithLifecycle { minActiveState, isEffectRunning ->
            LaunchedEffectWithLifecycle(Unit, minActiveState = minActiveState) {
                try {
                    isEffectRunning(true)
                    awaitCancellation()
                } finally {
                    isEffectRunning(false)
                }
            }
        }
    }

    @Test
    fun disposableEffect() {
        testEffectWithLifecycle { minActiveState, isEffectRunning ->
            DisposableEffectWithLifecycle(minActiveState = minActiveState) {
                isEffectRunning(true)
                onDispose { isEffectRunning(false) }
            }
        }
    }

    @Test
    fun produceState() {
        testEffectWithLifecycle { minActiveState, isEffectRunning ->
            produceStateWithLifecycle(initialValue = 0, minActiveState = minActiveState) {
                try {
                    isEffectRunning(true)
                    awaitCancellation()
                } finally {
                    isEffectRunning(false)
                }
            }
        }
    }

    private fun testEffectWithLifecycle(
        effect:
            @Composable
            (minActiveState: Lifecycle.State, isEffectRunning: (Boolean) -> Unit) -> Unit
    ) {
        val lifecycle =
            rule.runOnUiThread {
                object : LifecycleOwner {
                    override val lifecycle = LifecycleRegistry(this)

                    init {
                        lifecycle.currentState = Lifecycle.State.CREATED
                    }
                }
            }

        var isEffectRunning = false
        var minActiveState by mutableStateOf(Lifecycle.State.STARTED)

        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycle) {
                effect(minActiveState) { isEffectRunning = it }
            }
        }

        // currentState = CREATED / minActivateState = STARTED.
        assertThat(isEffectRunning).isFalse()

        // currentState = CREATED / minActivateState = CREATED.
        minActiveState = Lifecycle.State.CREATED
        rule.waitForIdle()
        assertThat(isEffectRunning).isTrue()

        // currentState = STARTED / minActivateState = RESUMED.
        minActiveState = Lifecycle.State.RESUMED
        rule.runOnUiThread { lifecycle.lifecycle.currentState = Lifecycle.State.STARTED }
        rule.waitForIdle()
        assertThat(isEffectRunning).isFalse()

        // currentState = RESUMED / minActivateState = RESUMED.
        rule.runOnUiThread { lifecycle.lifecycle.currentState = Lifecycle.State.RESUMED }
        rule.waitForIdle()
        assertThat(isEffectRunning).isTrue()

        // currentState = DESTROYED / minActivateState = RESUMED.
        rule.runOnUiThread { lifecycle.lifecycle.currentState = Lifecycle.State.DESTROYED }
        rule.waitForIdle()
        assertThat(isEffectRunning).isFalse()
    }
}
