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

package com.android.systemui.keyguard.shared.model

import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class KeyguardStateTest : SysuiTestCase() {

    @get:Rule val flagsRule: SetFlagsRule = SetFlagsRule()

    /**
     * This test makes sure that the result of [deviceIsAwakeInState] are equal for all the states
     * that are obsolete with scene container enabled and UNDEFINED. This means for example that if
     * GONE is transformed to UNDEFINED it makes sure that GONE and UNDEFINED need to have the same
     * value. This assumption is important as with scene container flag enabled call sites will only
     * check the result passing in UNDEFINED.
     */
    @Test
    fun assertUndefinedResultMatchesObsoleteStateResults() {
        for (state in KeyguardState.entries) {
            if (state == KeyguardState.UNDEFINED) {
                continue
            }
            flagsRule.enableFlags(Flags.FLAG_SCENE_CONTAINER)
            val isAwakeInSceneContainer =
                KeyguardState.deviceIsAwakeInState(
                    state.mapToSceneContainerState(),
                    state.mapToSceneContainerContent(),
                )

            flagsRule.disableFlags(Flags.FLAG_SCENE_CONTAINER)
            val isAwake = KeyguardState.deviceIsAwakeInState(state, null)
            assertThat(isAwakeInSceneContainer).isEqualTo(isAwake)
        }
    }
}
