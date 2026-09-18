/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.statusbar.policy

import android.app.StatusBarManager
import android.content.res.Configuration
import android.testing.TestableLooper.RunWithLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.testScope
import com.android.systemui.res.R
import com.android.systemui.shade.domain.interactor.FakeShadeModeInteractorImpl
import com.android.systemui.shade.domain.interactor.fakeShadeModeInteractor
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.statusbar.CommandQueue
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class RemoteInputQuickSettingsDisablerTest : SysuiTestCase() {

    private val kosmos = testKosmos()

    @Mock lateinit var commandQueue: CommandQueue

    private lateinit var remoteInputQuickSettingsDisabler: RemoteInputQuickSettingsDisabler
    private lateinit var configuration: Configuration
    private val fakeShadeModeInteractor: FakeShadeModeInteractorImpl =
        kosmos.fakeShadeModeInteractor
    private val testScope = kosmos.testScope

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        remoteInputQuickSettingsDisabler = createTestRemoteInputQuickSettingsDisabler()
    }

    @Test
    fun whenRemoteInputActiveAndLandscapeAndNotSplitShade_shouldDisableQs() =
        assertThat(shouldDisableQs(remoteInputQuickSettingsDisabler.adjustDisableFlags(0))).isTrue()

    @Test
    fun whenRemoteInputNotActive_shouldNotDisableQs() {
        remoteInputQuickSettingsDisabler.setRemoteInputActive(false)

        assertThat(shouldDisableQs(remoteInputQuickSettingsDisabler.adjustDisableFlags(0)))
            .isFalse()
    }

    @Test
    fun whenSplitShadeEnabled_shouldNotDisableQs() {
        fakeShadeModeInteractor.shadeMode = MutableStateFlow(ShadeMode.Split)
        remoteInputQuickSettingsDisabler = createTestRemoteInputQuickSettingsDisabler()
        mContext.orCreateTestableResources.addOverride(
            R.bool.config_use_split_notification_shade,
            /* value= */ true,
        )
        remoteInputQuickSettingsDisabler.onConfigChanged(configuration)

        assertThat(shouldDisableQs(remoteInputQuickSettingsDisabler.adjustDisableFlags(0)))
            .isFalse()
    }

    @Test
    fun whenPortrait_shouldNotDisableQs() {
        configuration.orientation = Configuration.ORIENTATION_PORTRAIT
        remoteInputQuickSettingsDisabler.onConfigChanged(configuration)

        assertThat(shouldDisableQs(remoteInputQuickSettingsDisabler.adjustDisableFlags(0)))
            .isFalse()
    }

    @Test
    fun whenRemoteInputChanges_recomputeTriggered() {
        remoteInputQuickSettingsDisabler.setRemoteInputActive(false)

        verify(commandQueue, atLeastOnce()).recomputeDisableFlags(anyInt(), anyBoolean())
    }

    @Test
    fun whenConfigChanges_recomputeTriggered() {
        configuration.orientation = Configuration.ORIENTATION_PORTRAIT
        remoteInputQuickSettingsDisabler.onConfigChanged(configuration)

        verify(commandQueue, atLeastOnce()).recomputeDisableFlags(anyInt(), anyBoolean())
    }

    private fun shouldDisableQs(state: Int): Boolean {
        return state and StatusBarManager.DISABLE2_QUICK_SETTINGS != 0
    }

    private fun createTestRemoteInputQuickSettingsDisabler(): RemoteInputQuickSettingsDisabler {
        mContext.orCreateTestableResources.addOverride(
            R.bool.config_use_split_notification_shade,
            /* value= */ false,
        )
        val remoteInputQuickSettingsDisabler =
            RemoteInputQuickSettingsDisabler(
                mContext,
                commandQueue,
                ResourcesSplitShadeStateController(),
                fakeShadeModeInteractor,
                Mockito.mock(ConfigurationController::class.java),
                testScope.backgroundScope,
            )
        // Default these conditions to what they need to be to disable QS.
        configuration = Configuration(mContext.resources.configuration)
        configuration.orientation = Configuration.ORIENTATION_LANDSCAPE
        remoteInputQuickSettingsDisabler.setRemoteInputActive(true)
        remoteInputQuickSettingsDisabler.onConfigChanged(configuration)
        return remoteInputQuickSettingsDisabler
    }
}
