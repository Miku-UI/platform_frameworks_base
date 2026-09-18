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

package com.android.compose.snapshot

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ObserveReadsTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun observeReads() {
        var state by mutableIntStateOf(0)
        var observedState = -1
        rule.setContent { ObserveReadsRoot { ObserveReads { observedState = state } } }
        assertThat(observedState).isEqualTo(0)

        state = 1
        rule.waitForIdle()
        assertThat(observedState).isEqualTo(1)

        state = 2
        rule.waitForIdle()
        assertThat(observedState).isEqualTo(2)
    }

    @Test
    fun nestedObserveReads_doesNotCrash_observedCorrectly() {
        var stateInner by mutableIntStateOf(0)
        var stateOuter by mutableIntStateOf(10)
        var observedStateInner = -1 to -1
        var observedStateOuter = -1
        rule.setContent {
            ObserveReadsRoot {
                ObserveReads {
                    observedStateOuter = stateOuter
                }
                ObserveReadsRoot {
                    ObserveReads {
                        observedStateInner = stateInner to stateOuter
                    }
                }
            }
        }
        assertThat(observedStateOuter).isEqualTo(10)
        assertThat(observedStateInner).isEqualTo(0 to 10)

        stateInner = 1
        rule.waitForIdle()
        assertThat(observedStateOuter).isEqualTo(10)
        assertThat(observedStateInner).isEqualTo(1 to 10)

        stateInner = 2
        stateOuter = 20
        rule.waitForIdle()
        assertThat(observedStateOuter).isEqualTo(20)
        assertThat(observedStateInner).isEqualTo(2 to 20)
    }
}
