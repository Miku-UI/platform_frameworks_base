/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.systemui.statusbar.phone

import android.app.Dialog
import android.content.res.Configuration
import android.testing.TestableLooper.RunWithLooper
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.model.sysUiState
import com.android.systemui.model.sysuiStateInteractor
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_DIALOG_SHOWING
import com.android.systemui.statusbar.policy.ConfigurationController
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.any
import com.android.systemui.util.mockito.argumentCaptor
import com.android.systemui.util.mockito.capture
import com.android.systemui.util.mockito.mock
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.kotlin.eq

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper(setAsMainLooper = true)
class SystemUIBottomSheetDialogTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val configurationController = mock<ConfigurationController>()
    private val config = mock<Configuration>()
    private val delegate = mock<DialogDelegate<Dialog>>()
    private val dialogManager = kosmos.mockSystemUIDialogManager
    private val defaultDisplaySysuiState = kosmos.sysUiState

    private lateinit var dialog: SystemUIBottomSheetDialog

    @Before
    fun setup() {
        dialog =
            with(kosmos) {
                SystemUIBottomSheetDialog(
                    context,
                    testScope.backgroundScope,
                    configurationController,
                    delegate,
                    TestLayout(),
                    0,
                    dialogManager,
                    sysuiStateInteractor,
                )
            }
    }

    @Test
    fun onStart_registersConfigCallback() {
        kosmos.runTest {
            dialog.show()
            verify(configurationController).addCallback(any())
        }
    }

    @Test
    fun onStop_unregisterConfigCallback() {
        kosmos.runTest {
            dialog.show()
            dialog.dismiss()

            verify(configurationController).removeCallback(any())
        }
    }

    @Test
    fun onStop_unregistersThenUnregistersWithDialogManager() {
        kosmos.runTest {
            dialog.show()

            verify(dialogManager).setShowing(eq(dialog), eq(true))

            dialog.dismiss()

            verify(dialogManager).setShowing(eq(dialog), eq(false))
        }
    }

    @Test
    fun onStart_setsSysUIFlagsCorrectly() {
        kosmos.runTest {
            assertThat(defaultDisplaySysuiState.isFlagEnabled(SYSUI_STATE_DIALOG_SHOWING)).isFalse()

            dialog.show()

            assertThat(defaultDisplaySysuiState.isFlagEnabled(SYSUI_STATE_DIALOG_SHOWING)).isTrue()

            dialog.dismiss()

            assertThat(defaultDisplaySysuiState.isFlagEnabled(SYSUI_STATE_DIALOG_SHOWING)).isFalse()
        }
    }

    @Test
    fun onConfigurationChanged_calledInDelegate() {
        kosmos.runTest {
            dialog.show()
            val captor = argumentCaptor<ConfigurationController.ConfigurationListener>()
            verify(configurationController).addCallback(capture(captor))

            captor.value.onConfigChanged(config)

            verify(delegate).onConfigurationChanged(any(), any())
        }
    }

    private class TestLayout : SystemUIBottomSheetDialog.WindowLayout {
        override fun calculate(): Flow<SystemUIBottomSheetDialog.WindowLayout.Layout> {
            return flowOf(
                SystemUIBottomSheetDialog.WindowLayout.Layout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                )
            )
        }
    }
}
