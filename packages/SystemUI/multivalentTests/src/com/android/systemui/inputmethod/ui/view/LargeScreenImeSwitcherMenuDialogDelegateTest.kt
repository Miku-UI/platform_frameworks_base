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

package com.android.systemui.inputmethod.ui.view

import android.content.Context
import android.content.applicationContext
import android.platform.test.annotations.EnableFlags
import android.view.Gravity
import android.view.inputmethod.Flags.FLAG_IME_SWITCHER_MENU_LARGE_SCREEN
import android.view.inputmethod.Flags.FLAG_IME_SWITCHER_MENU_SYSTEMUI
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_SCENE_CONTAINER
import com.android.systemui.SysuiTestCase
import com.android.systemui.desktop.domain.interactor.desktopInteractor
import com.android.systemui.desktop.domain.interactor.disableDesktopStatusBar
import com.android.systemui.desktop.domain.interactor.enableUsingDesktopStatusBar
import com.android.systemui.inputmethod.ui.viewmodel.ImeSwitcherMenuViewModel
import com.android.systemui.inputmethod.ui.viewmodel.imeSwitcherMenuViewModel
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.runOnMainThreadAndWaitForIdleSync
import com.android.systemui.statusbar.core.StatusBarForDesktop
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.systemUIDialogFactory
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/** Tests for [LargeScreenImeSwitcherMenuDialogDelegate]. */
@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(
    FLAG_IME_SWITCHER_MENU_SYSTEMUI,
    FLAG_IME_SWITCHER_MENU_LARGE_SCREEN,
    StatusBarForDesktop.FLAG_NAME,
    FLAG_SCENE_CONTAINER,
)
class LargeScreenImeSwitcherMenuDialogDelegateTest : SysuiTestCase() {

    @get:Rule val composeTestRule = createComposeRule()

    private val kosmos = testKosmos()
    private val viewModelFactory: (context: Context) -> ImeSwitcherMenuViewModel = mock()

    private val dialogFactory = kosmos.largeScreenImeSwitcherMenuDialogDelegateFactory

    /** Dialog instance to dismiss at the end of the test, if present. */
    private var toDismiss: SystemUIDialog? = null

    /** Verifies that the default dialog is used when the desktop status bar is disabled. */
    @Test
    fun create_whenDesktopStatusBarDisabled_returnsDefaultDialog() {
        runTestAndDismiss {
            val useDesktopStatusBar by
                collectLastValue(kosmos.desktopInteractor.useDesktopStatusBar)
            disableDesktopStatusBar()
            runCurrent()

            val underTest =
                dialogFactory.create(
                    context,
                    InputMethodManager.IM_PICKER_ENTRY_POINT_DEFAULT,
                    viewModelFactory,
                )

            assertThat(underTest).isInstanceOf(ImeSwitcherMenuDialogDelegate::class.java)
        }
    }

    /** Verifies that the large screen dialog is used when the desktop status bar is enabled. */
    @Test
    fun create_whenDesktopStatusBarEnabled_returnsLargeScreenDialog() {
        runTestAndDismiss {
            val useDesktopStatusBar by
                collectLastValue(kosmos.desktopInteractor.useDesktopStatusBar)
            enableUsingDesktopStatusBar()
            runCurrent()

            val underTest =
                dialogFactory.create(
                    context,
                    InputMethodManager.IM_PICKER_ENTRY_POINT_DEFAULT,
                    viewModelFactory,
                )

            assertThat(underTest).isInstanceOf(LargeScreenImeSwitcherMenuDialogDelegate::class.java)
        }
    }

    /**
     * Verifies that the large screen dialog contains the large screen IME Switcher Menu contents.
     */
    @Test
    fun dialog_containsLargeScreenContent() {
        runTestAndDismiss {
            val underTest =
                LargeScreenImeSwitcherMenuDialogDelegate(
                    context = kosmos.applicationContext,
                    entryPoint = InputMethodManager.IM_PICKER_ENTRY_POINT_DEFAULT,
                    viewModelFactory = { kosmos.imeSwitcherMenuViewModel },
                    sysuiDialogFactory = kosmos.systemUIDialogFactory,
                )

            composeTestRule.runOnUiThread {
                val dialog = underTest.createDialog()
                toDismiss = dialog
                dialog.show()
            }

            composeTestRule.onNodeWithTag("large_screen_ime_switcher_menu").assertExists()
        }
    }

    /** Verifies that the dialog has BOTTOM|END gravity for the default entry point. */
    @Test
    fun dialog_hasBottomEndGravity_forDefaultEntryPoint() {
        runTestAndDismiss {
            composeTestRule.runOnUiThread {
                val dialog =
                    LargeScreenImeSwitcherMenuDialogDelegate(
                            context = kosmos.applicationContext,
                            entryPoint = InputMethodManager.IM_PICKER_ENTRY_POINT_DEFAULT,
                            viewModelFactory = { kosmos.imeSwitcherMenuViewModel },
                            sysuiDialogFactory = kosmos.systemUIDialogFactory,
                        )
                        .createDialog()
                toDismiss = dialog

                val expectedGravity =
                    Gravity.getAbsoluteGravity(
                        Gravity.BOTTOM or Gravity.END,
                        kosmos.applicationContext.resources.configuration.layoutDirection,
                    )
                assertThat(dialog.window?.attributes?.gravity).isEqualTo(expectedGravity)
            }
        }
    }

    /** Verifies that the dialog has TOP|END gravity for the status bar chip entry point. */
    @Test
    fun dialog_hasTopEndGravity_forStatusBarChipEntryPoint() {
        runTestAndDismiss {
            composeTestRule.runOnUiThread {
                val dialog =
                    LargeScreenImeSwitcherMenuDialogDelegate(
                            context = kosmos.applicationContext,
                            entryPoint = InputMethodManager.IM_PICKER_ENTRY_POINT_STATUS_BAR_CHIP,
                            viewModelFactory = { kosmos.imeSwitcherMenuViewModel },
                            sysuiDialogFactory = kosmos.systemUIDialogFactory,
                        )
                        .createDialog()
                toDismiss = dialog

                val expectedGravity =
                    Gravity.getAbsoluteGravity(
                        Gravity.TOP or Gravity.END,
                        kosmos.applicationContext.resources.configuration.layoutDirection,
                    )
                assertThat(dialog.window?.attributes?.gravity).isEqualTo(expectedGravity)
            }
        }
    }

    /** Runs the given test block and dismisses any dialog at the end. */
    private fun runTestAndDismiss(block: suspend Kosmos.() -> Unit) =
        kosmos.runTest {
            try {
                block()
            } finally {
                toDismiss?.let { runOnMainThreadAndWaitForIdleSync { it.dismiss() } }
                composeTestRule.waitForIdle()
            }
        }
}
