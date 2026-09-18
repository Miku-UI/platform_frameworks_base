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

package com.android.systemui.statusbar.notification.row

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.display.data.repository.createFakeDisplaySubcomponent
import com.android.systemui.display.data.repository.displaySubcomponentPerDisplayRepository
import com.android.systemui.statusbar.data.repository.FakeStatusBarModePerDisplayRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class HeadsUpStyleProviderImplTest : SysuiTestCase() {

    private val primaryDisplayId = 0
    private val secondaryDisplayId = 1

    private val defaultDisplayRepository = FakeStatusBarModePerDisplayRepository()
    private val secondaryDisplayRepository = FakeStatusBarModePerDisplayRepository()
    private val kosmos =
        testKosmos().apply {
            displaySubcomponentPerDisplayRepository.add(
                primaryDisplayId,
                createFakeDisplaySubcomponent(statusBarModeRepo = { defaultDisplayRepository }),
            )
            displaySubcomponentPerDisplayRepository.add(
                secondaryDisplayId,
                createFakeDisplaySubcomponent(statusBarModeRepo = { secondaryDisplayRepository }),
            )
        }

    private val headsUpStyleProvider =
        HeadsUpStyleProviderImpl(kosmos.displaySubcomponentPerDisplayRepository)

    @Test
    fun shouldApplyCompactStyle_primaryDisplayInImmersiveMode_returnsTrue() {
        defaultDisplayRepository.isInFullscreenMode.value = true

        val result = headsUpStyleProvider.shouldApplyCompactStyle(primaryDisplayId)

        assertThat(result).isTrue()
    }

    @Test
    fun shouldApplyCompactStyle_primaryDisplayNotInImmersiveMode_returnsFalse() {
        defaultDisplayRepository.isInFullscreenMode.value = false

        val result = headsUpStyleProvider.shouldApplyCompactStyle(primaryDisplayId)

        assertThat(result).isFalse()
    }

    @Test
    fun shouldApplyCompactStyle_secondaryDisplayInImmersiveMode_returnsTrue() {
        secondaryDisplayRepository.isInFullscreenMode.value = true

        val result = headsUpStyleProvider.shouldApplyCompactStyle(secondaryDisplayId)

        assertThat(result).isTrue()
    }

    @Test
    fun shouldApplyCompactStyle_secondaryDisplayNotInImmersiveMode_returnsFalse() {
        secondaryDisplayRepository.isInFullscreenMode.value = false

        val result = headsUpStyleProvider.shouldApplyCompactStyle(secondaryDisplayId)

        assertThat(result).isFalse()
    }
}
