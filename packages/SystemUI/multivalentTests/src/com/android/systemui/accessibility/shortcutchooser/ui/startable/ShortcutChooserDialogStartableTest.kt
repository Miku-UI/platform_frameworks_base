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

package com.android.systemui.accessibility.shortcutchooser.ui.startable

import android.content.Intent
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.provider.Settings
import android.provider.Settings.Secure.USER_SETUP_COMPLETE
import android.view.Display.DEFAULT_DISPLAY
import android.view.accessibility.Flags as AccessibilityFlags
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.accessibility.common.ShortcutChooserDialogConstants
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType
import com.android.internal.accessibility.util.ShortcutUtils
import com.android.systemui.Flags as SystemUIFlags
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.data.repository.FakeAccessibilityShortcutsRepository
import com.android.systemui.accessibility.data.repository.accessibilityShortcutsRepository
import com.android.systemui.accessibility.data.repository.fakeAccessibilityShortcutsRepository
import com.android.systemui.accessibility.shortcutchooser.ui.viewmodel.ShortcutChooserDialogViewModel.DialogType
import com.android.systemui.accessibility.shortcutchooser.ui.viewmodel.shortcutChooserDialogViewModelFactory
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.runTest
import com.android.systemui.res.R
import com.android.systemui.runOnMainThreadAndWaitForIdleSync
import com.android.systemui.statusbar.phone.systemUIDialogFactory
import com.android.systemui.testKosmosNew
import com.android.systemui.user.domain.interactor.fakeHeadlessSystemUserMode
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.times

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(
    AccessibilityFlags.FLAG_ENABLE_A11Y_TOP_ROW_SHORTCUT,
    AccessibilityFlags.FLAG_QUICK_ACCESS_SHORTCUT_TYPE,
)
class ShortcutChooserDialogStartableTest : SysuiTestCase() {
    private companion object {
        const val SHORTCUT_TYPE = UserShortcutType.TOP_ROW_KEY

        const val TALKBACK_TARGET_NAME =
            FakeAccessibilityShortcutsRepository.FAKE_TALKBACK_TARGET_NAME
        const val MAGNIFICATION_TARGET_NAME =
            FakeAccessibilityShortcutsRepository.FAKE_MAGNIFICATION_TARGET_NAME
        const val UNTRUSTED_SERVICE_TARGET_NAME =
            FakeAccessibilityShortcutsRepository.FAKE_UNTRUSTED_SERVICE_TARGET_NAME
    }

    @get:Rule val composeTestRule = createEmptyComposeRule()

    private val kosmos = testKosmosNew()
    private val viewModel = kosmos.shortcutChooserDialogViewModelFactory.create()
    private val fakeRepository = kosmos.fakeAccessibilityShortcutsRepository

    private val Kosmos.underTest by
        Kosmos.Fixture {
            ShortcutChooserDialogStartable(
                shortcutChooserDialogViewModelFactory,
                systemUIDialogFactory,
                backgroundScope,
            )
        }

    @Before
    fun setUp() {
        setOobeCompleted(true)
    }

    @Test
    fun start_doesNotShowDialogByDefault() = runTestAndDismiss {
        assertCurrentDialog(DialogType.NONE)
    }

    @Test
    fun createDialog_topRowKey_noSelectedTargets_showInitialScreen_andClickCancelButton() =
        runTestAndDismiss {
            underTest.start()

            sendIntentInMainThreadWaitForIdle()

            // Verify that when there is no selected targets by default, the dialog type should be
            // tutorial dialog.
            assertCurrentDialog(DialogType.TUTORIAL)

            // Click on the composable negative button on the top row key tutorial dialog.
            composeTestRule.onCancelButton().performClick()
            composeTestRule.waitForIdle()

            // Will dismiss the tutorial dialog.
            assertCurrentDialog(DialogType.NONE)
        }

    @Test
    fun createDialog_hardware_noSelectedTargets_noDialog() = runTestAndDismiss {
        underTest.start()

        sendIntentInMainThreadWaitForIdle(UserShortcutType.HARDWARE)

        assertCurrentDialog(DialogType.NONE)
    }

    @Test
    fun createDialog_topRowKey_noSelectedTargets_andClickAddFeatureButton_showEditTargetsDialog() =
        runTestAndDismiss {
            underTest.start()

            sendIntentInMainThreadWaitForIdle()

            // Click on the composable positive button on the top row key tutorial dialog.
            composeTestRule.onAddFeaturesButton().performClick()
            composeTestRule.waitForIdle()

            // Will do the recomposition to the Edit targets dialog.
            assertCurrentDialog(DialogType.EDIT_TARGETS)
        }

    @Test
    fun createDialog_topRowKey_editTargetsDialog_selectOneTarget_dismissDialog() =
        runTestAndDismiss {
            underTest.start()

            sendIntentInMainThreadWaitForIdle()

            // Click on the composable positive button on the top row key tutorial dialog.
            composeTestRule.onAddFeaturesButton().performClick()
            composeTestRule.waitForIdle()

            // Select only one target on EditDialog.
            composeTestRule.onNodeWithTag(TALKBACK_TARGET_NAME).performClick()
            composeTestRule.waitForIdle()

            assertThat(getSelectedTargetNames()).isEqualTo(setOf(TALKBACK_TARGET_NAME))

            // Finally click on Done button.
            composeTestRule.onDoneButton().performScrollAndClick()
            composeTestRule.waitForIdle()

            // Will dismiss the dialog, because there are less than two targets selected.
            assertCurrentDialog(DialogType.NONE)
        }

    @Test
    fun createDialog_topRowKey_editTargetsDialog_selectTwoTarget_showToggleTargetsDialog() =
        runTestAndDismiss {
            underTest.start()

            sendIntentInMainThreadWaitForIdle()

            // Click on the composable positive button on the top row key tutorial dialog.
            composeTestRule.onAddFeaturesButton().performClick()
            composeTestRule.waitForIdle()

            // Select two targets on EditDialog, e.g. Talkback and Magnification.
            composeTestRule.onNodeWithTag(TALKBACK_TARGET_NAME).performClick()
            composeTestRule.waitForIdle()

            assertThat(getSelectedTargetNames()).isEqualTo(setOf(TALKBACK_TARGET_NAME))

            composeTestRule.onNodeWithTag(MAGNIFICATION_TARGET_NAME).performClick()
            composeTestRule.waitForIdle()

            assertThat(getSelectedTargetNames())
                .isEqualTo(setOf(TALKBACK_TARGET_NAME, MAGNIFICATION_TARGET_NAME))

            // Finally click on Done button.
            composeTestRule.onDoneButton().performScrollAndClick()
            composeTestRule.waitForIdle()

            // Will show Toggle targets dialog, because there are at least two targets selected.
            assertCurrentDialog(DialogType.TOGGLE_TARGETS)
        }

    @Test
    fun createDialog_hardware_oneSelectedTarget_noDialog() = runTestAndDismiss {
        // Assume there is only one feature selected before pressing the key.
        setTalkBackSelected(UserShortcutType.HARDWARE)

        underTest.start()

        sendIntentInMainThreadWaitForIdle(UserShortcutType.HARDWARE)

        assertCurrentDialog(DialogType.NONE)
    }

    @Test
    fun createDialog_topRowKey_oneSelectedTarget_noDialog() = runTestAndDismiss {
        // Assume there is only one feature selected before pressing the key.
        setTalkBackSelected()

        underTest.start()

        sendIntentInMainThreadWaitForIdle(UserShortcutType.TOP_ROW_KEY)

        assertCurrentDialog(DialogType.NONE)
    }

    @Test
    fun createDialog_topRowKey_twoSelectedTargets_showToggleScreen_andClickToggleableTargetRow_enablesFeatureAndClosesDialog() =
        runTestAndDismiss {
            setTalkbackAndMagnificationSelected()

            underTest.start()

            sendIntentInMainThreadWaitForIdle()

            // Verify when there are two selected targets, the dialog type should be Toggle targets
            // dialog.
            assertCurrentDialog(DialogType.TOGGLE_TARGETS)

            // Click on a toggleable target row, e.g. Talkback.
            composeTestRule.onNodeWithTag(TALKBACK_TARGET_NAME).performClick()
            composeTestRule.waitForIdle()

            // Will toggle Talkback feature on/off and dismiss the dialog.
            assertThat(getEnabledTargetNames()).isEqualTo(setOf(TALKBACK_TARGET_NAME))
            assertCurrentDialog(DialogType.NONE)
        }

    @Test
    fun createDialog_topRowKey_twoSelectedTargets_showToggleScreen_andClickNonToggleableTargetRow_enablesFeatureAndClosesDialog() =
        runTestAndDismiss {
            setTalkbackAndMagnificationSelected()

            underTest.start()

            sendIntentInMainThreadWaitForIdle()

            // Verify when there are two selected targets, the dialog type should be Toggle targets
            // dialog.
            assertCurrentDialog(DialogType.TOGGLE_TARGETS)

            // Click on a non-toggleable target row, e.g. Magnification.
            composeTestRule.onNodeWithTag(MAGNIFICATION_TARGET_NAME).performClick()
            composeTestRule.waitForIdle()

            // Will toggle Magnification feature on/off and dismiss the dialog.
            assertThat(getEnabledTargetNames()).isEqualTo(setOf(MAGNIFICATION_TARGET_NAME))
            assertCurrentDialog(DialogType.NONE)
        }

    @Test
    fun createDialog_topRowKey_twoSelectedTargets_clickEditButton() = runTestAndDismiss {
        setTalkbackAndMagnificationSelected()

        underTest.start()

        sendIntentInMainThreadWaitForIdle()

        // Click on the Edit button.
        composeTestRule.onEditButton().performClick()
        composeTestRule.waitForIdle()

        // Will do the recomposition to the Edit targets dialog.
        assertCurrentDialog(DialogType.EDIT_TARGETS)
    }

    @Test
    fun createDialog_topRowKey_twoSelectedTargets_clickDoneButton() = runTestAndDismiss {
        setTalkbackAndMagnificationSelected()

        underTest.start()

        sendIntentInMainThreadWaitForIdle()

        // Click on the Done button.
        composeTestRule.onDoneButton().performScrollAndClick()
        composeTestRule.waitForIdle()

        // Will dismiss the dialog.
        assertCurrentDialog(DialogType.NONE)
    }

    @Test
    fun createDialog_hardware_twoSelectedTargets_setupIncomplete_noEditButton() =
        runTestAndDismiss {
            setOobeCompleted(false)
            setTalkbackAndMagnificationSelected(UserShortcutType.HARDWARE)

            underTest.start()

            sendIntentInMainThreadWaitForIdle(UserShortcutType.HARDWARE)

            assertCurrentDialog(DialogType.TOGGLE_TARGETS)
            composeTestRule.onEditButton().assertDoesNotExist()
        }

    @Test
    fun createDialog_topRowKey_noSelectedTargets_setupIncomplete_showsQuickAccessDialog() =
        runTestAndDismiss {
            setOobeCompleted(false)

            underTest.start()

            sendIntentInMainThreadWaitForIdle()

            assertCurrentDialog(DialogType.QUICK_ACCESS)
        }

    @Test
    fun createDialog_topRowKey_twoSelectedTargets_setupIncomplete_showsQuickAccessDialog() =
        runTestAndDismiss {
            setOobeCompleted(false)
            setTalkbackAndMagnificationSelected()

            underTest.start()

            sendIntentInMainThreadWaitForIdle()

            assertCurrentDialog(DialogType.QUICK_ACCESS)
        }

    @Test
    fun createDialog_hardware_twoSelectedTargets_onLoginScreen_noEditButton() = runTestAndDismiss {
        setOobeCompleted(true)
        fakeHeadlessSystemUserMode.setIsHeadlessSystemUser(true)
        setTalkbackAndMagnificationSelected(UserShortcutType.HARDWARE)

        underTest.start()

        sendIntentInMainThreadWaitForIdle(UserShortcutType.HARDWARE)

        assertCurrentDialog(DialogType.TOGGLE_TARGETS)
        composeTestRule.onEditButton().assertDoesNotExist()
    }

    @Test
    fun createDialog_topRowKey_noSelectedTargets_onLoginScreen_showsQuickAccessDialog() =
        runTestAndDismiss {
            setOobeCompleted(true)
            fakeHeadlessSystemUserMode.setIsHeadlessSystemUser(true)

            underTest.start()

            sendIntentInMainThreadWaitForIdle()

            assertCurrentDialog(DialogType.QUICK_ACCESS)
        }

    @Test
    fun createDialog_topRowKey_twoSelectedTargets_onLoginScreen_showsQuickAccessDialog() =
        runTestAndDismiss {
            setOobeCompleted(true)
            fakeHeadlessSystemUserMode.setIsHeadlessSystemUser(true)
            setTalkbackAndMagnificationSelected()

            underTest.start()

            sendIntentInMainThreadWaitForIdle()

            assertCurrentDialog(DialogType.QUICK_ACCESS)
        }

    @Test
    fun createDialog_hardware_twoSelectedTargets_onLockScreen_noEditButton() = runTestAndDismiss {
        setOobeCompleted(true)
        fakeKeyguardRepository.setKeyguardShowing(true)
        setTalkbackAndMagnificationSelected(UserShortcutType.HARDWARE)

        underTest.start()

        sendIntentInMainThreadWaitForIdle(UserShortcutType.HARDWARE)

        assertCurrentDialog(DialogType.TOGGLE_TARGETS)
        // No edit button because of lock screen.
        composeTestRule.onEditButton().assertDoesNotExist()
    }

    @Test
    fun createDialog_topRowKey_noSelectedTargets_onLockScreen_doNothing() = runTestAndDismiss {
        setOobeCompleted(true)
        fakeKeyguardRepository.setKeyguardShowing(true)

        underTest.start()

        sendIntentInMainThreadWaitForIdle()

        assertCurrentDialog(DialogType.NONE)
    }

    @Test
    fun createDialog_topRowKey_twoSelectedTargets_onLockScreen_noEditButton() = runTestAndDismiss {
        setOobeCompleted(true)
        fakeKeyguardRepository.setKeyguardShowing(true)
        setTalkbackAndMagnificationSelected()

        underTest.start()

        sendIntentInMainThreadWaitForIdle()

        assertCurrentDialog(DialogType.TOGGLE_TARGETS)
        // No edit button because of lock screen.
        composeTestRule.onEditButton().assertDoesNotExist()
    }

    @Test
    fun createDialog_topRowKey_twoSelectedTargets_afterUnlock_showEditButton() = runTestAndDismiss {
        setOobeCompleted(true)
        fakeKeyguardRepository.setKeyguardShowing(true)
        setTalkbackAndMagnificationSelected()

        underTest.start()

        sendIntentInMainThreadWaitForIdle()

        assertCurrentDialog(DialogType.TOGGLE_TARGETS)
        // No edit button because of lock screen.
        composeTestRule.onEditButton().assertDoesNotExist()

        viewModel.dismissDialog()
        composeTestRule.waitForIdle()

        fakeKeyguardRepository.setKeyguardShowing(false)

        sendIntentInMainThreadWaitForIdle()

        assertCurrentDialog(DialogType.TOGGLE_TARGETS)
        composeTestRule.onEditButton().assertIsDisplayed()
    }

    @Test
    @EnableFlags(SystemUIFlags.FLAG_LAUNCH_ACCESSIBILITY_QUICK_ACCESS_DIALOG_PERMISSION)
    fun createDialog_quickAccess_allTrustedShortcutsEnabled() = runTestAndDismiss {
        val shortcutType = UserShortcutType.QUICK_ACCESS

        underTest.start()

        assertThat(
                accessibilityShortcutsRepository
                    .getSelectedAccessibilityTargetsInfo(shortcutType)
                    .map { it.targetName }
                    .toSet()
            )
            .isEmpty()

        sendIntentInMainThreadWaitForIdle(shortcutType)

        assertThat(
                accessibilityShortcutsRepository
                    .getSelectedAccessibilityTargetsInfo(shortcutType)
                    .map { it.targetName }
                    .toSet()
            )
            .isEqualTo(
                accessibilityShortcutsRepository
                    .getAllAccessibilityTargetsInfo(shortcutType)
                    .map { it.targetName }
                    .toSet()
                    .minus(setOf(UNTRUSTED_SERVICE_TARGET_NAME))
            )
    }

    @Test
    @EnableFlags(SystemUIFlags.FLAG_LAUNCH_ACCESSIBILITY_QUICK_ACCESS_DIALOG_PERMISSION)
    fun start_quickAccess_noDialogShownByDefault() = runTestAndDismiss {
        underTest.start()

        assertCurrentDialog(DialogType.NONE)
    }

    @Test
    @DisableFlags(SystemUIFlags.FLAG_LAUNCH_ACCESSIBILITY_QUICK_ACCESS_DIALOG_PERMISSION)
    fun createDialog_quickAccess_withFlagDisabled_doesNotShowDialog() = runTestAndDismiss {
        underTest.start()

        sendIntentInMainThreadWaitForIdle(UserShortcutType.QUICK_ACCESS)

        assertCurrentDialog(DialogType.NONE)
    }

    @Test
    @EnableFlags(SystemUIFlags.FLAG_LAUNCH_ACCESSIBILITY_QUICK_ACCESS_DIALOG_PERMISSION)
    fun createDialog_quickAccess_showsQuickAccessDialog_thenClickDoneButton_dismissesDialog() =
        runTestAndDismiss {
            underTest.start()

            sendIntentInMainThreadWaitForIdle(UserShortcutType.QUICK_ACCESS)

            assertCurrentDialog(DialogType.QUICK_ACCESS)

            composeTestRule.onDoneButton().performScrollAndClick()
            composeTestRule.waitForIdle()

            assertCurrentDialog(DialogType.NONE)
        }

    @Test
    @EnableFlags(SystemUIFlags.FLAG_LAUNCH_ACCESSIBILITY_QUICK_ACCESS_DIALOG_PERMISSION)
    fun createDialog_quickAccess_clickToggleableTarget_performsShortcut() = runTestAndDismiss {
        underTest.start()

        sendIntentInMainThreadWaitForIdle(UserShortcutType.QUICK_ACCESS)

        assertCurrentDialog(DialogType.QUICK_ACCESS)
        assertThat(fakeRepository.isTargetEnabled(TALKBACK_TARGET_NAME)).isFalse()
        val targetNode = composeTestRule.onNodeWithTag(TALKBACK_TARGET_NAME)
        targetNode.assert(isToggleable())
        targetNode.assertIsOff()

        targetNode.performClick()
        composeTestRule.waitForIdle()

        assertCurrentDialog(DialogType.NONE)
        assertThat(fakeRepository.isTargetEnabled(TALKBACK_TARGET_NAME)).isTrue()

        sendIntentInMainThreadWaitForIdle(UserShortcutType.QUICK_ACCESS)

        assertCurrentDialog(DialogType.QUICK_ACCESS)
        targetNode.assertIsOn()

        targetNode.performClick()
        composeTestRule.waitForIdle()

        assertCurrentDialog(DialogType.NONE)
        assertThat(fakeRepository.isTargetEnabled(TALKBACK_TARGET_NAME)).isFalse()

        sendIntentInMainThreadWaitForIdle(UserShortcutType.QUICK_ACCESS)

        assertCurrentDialog(DialogType.QUICK_ACCESS)
        targetNode.assertIsOff()
    }

    @Test
    @EnableFlags(SystemUIFlags.FLAG_LAUNCH_ACCESSIBILITY_QUICK_ACCESS_DIALOG_PERMISSION)
    fun createDialog_quickAccess_clickNotToggleableTarget_performsShortcutAndClosesDialog() =
        runTestAndDismiss {
            underTest.start()

            sendIntentInMainThreadWaitForIdle(UserShortcutType.QUICK_ACCESS)

            assertCurrentDialog(DialogType.QUICK_ACCESS)

            assertThat(fakeRepository.isTargetEnabled(MAGNIFICATION_TARGET_NAME)).isFalse()
            val targetNode = composeTestRule.onNodeWithTag(MAGNIFICATION_TARGET_NAME)
            targetNode.assert(isNotToggleable())

            targetNode.performClick()
            composeTestRule.waitForIdle()

            assertThat(fakeRepository.isTargetEnabled(MAGNIFICATION_TARGET_NAME)).isTrue()
            assertCurrentDialog(DialogType.NONE)
        }

    @Test
    fun createDialog_softwareShortcut_showsNavBarChooser() =
        kosmos.runTest {
            underTest.start()

            sendIntentInMainThreadWaitForIdle(UserShortcutType.SOFTWARE)

            assertCurrentDialog(DialogType.NAV_BAR_CHOOSER)
        }

    @Test
    fun createDialog_softwareShortcut_clickDoneButton_dismissesDialog() =
        kosmos.runTest {
            underTest.start()

            sendIntentInMainThreadWaitForIdle(UserShortcutType.SOFTWARE)

            assertCurrentDialog(DialogType.NAV_BAR_CHOOSER)

            composeTestRule.onDoneButton().performScrollAndClick()
            composeTestRule.waitForIdle()

            assertCurrentDialog(DialogType.NONE)
        }

    @Test
    fun navBarChooser_selectTarget_updatesSecureSettings() =
        kosmos.runTest {
            setTalkBackSelected(UserShortcutType.SOFTWARE)
            underTest.start()

            sendIntentInMainThreadWaitForIdle(UserShortcutType.SOFTWARE)
            assertCurrentDialog(DialogType.NAV_BAR_CHOOSER)

            composeTestRule.onNodeWithTag(TALKBACK_TARGET_NAME).performClick()
            composeTestRule.waitForIdle()

            assertThat(fakeRepository.accessibilityButtonTargetComponent.value)
                .isEqualTo(TALKBACK_TARGET_NAME)
        }

    @Test
    @EnableFlags(SystemUIFlags.FLAG_LAUNCH_ACCESSIBILITY_QUICK_ACCESS_DIALOG_PERMISSION)
    fun showWarningDialog_quickAccess_clickUntrustedTarget_showsWarningDialog() =
        runTestAndDismiss {
            val shortcutType = UserShortcutType.QUICK_ACCESS
            val targetName = UNTRUSTED_SERVICE_TARGET_NAME

            underTest.start()

            sendIntentInMainThreadWaitForIdle(shortcutType)

            assertCurrentDialog(DialogType.QUICK_ACCESS)

            assertThat(fakeRepository.isTargetAssigned(shortcutType, targetName)).isFalse()
            assertThat(fakeRepository.isTargetEnabled(targetName)).isFalse()
            val targetNode = composeTestRule.onNodeWithTag(targetName)
            targetNode.assertIsOff()

            targetNode.performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onWarningDialog().assertIsDisplayed()
            assertThat(fakeRepository.isTargetAssigned(shortcutType, targetName)).isFalse()
            assertThat(fakeRepository.isTargetEnabled(targetName)).isFalse()
            targetNode.assertIsOff()

            viewModel.dismissWarningDialog()
            composeTestRule.waitForIdle()

            composeTestRule.onWarningDialog().assertDoesNotExist()
            assertThat(fakeRepository.isTargetAssigned(shortcutType, targetName)).isFalse()
            assertThat(fakeRepository.isTargetEnabled(targetName)).isFalse()
            targetNode.assertIsOff()
        }

    private fun Kosmos.sendIntentInMainThreadWaitForIdle(
        @UserShortcutType shortcutType: Int = SHORTCUT_TYPE
    ) {
        // Sending broadcast to create SysUi dialog should be run in main thread.
        runOnMainThreadAndWaitForIdleSync {
            broadcastDispatcher.sendIntentToMatchingReceiversOnly(
                context,
                Intent().apply {
                    action = ShortcutChooserDialogConstants.LAUNCH_SHORTCUT_CHOOSER_DIALOG_ACTION
                    putExtra(ShortcutChooserDialogConstants.SHORTCUT_TYPE, shortcutType)
                    putExtra(ShortcutChooserDialogConstants.DISPLAY_ID, DEFAULT_DISPLAY)
                },
            )
        }
        composeTestRule.waitForIdle()
    }

    /**
     * A helper function called before launching dialog. This function is to assume we have two
     * selected targets, which are Talkback and Magnification.
     */
    private fun Kosmos.setTalkbackAndMagnificationSelected(
        @UserShortcutType shortcutType: Int = SHORTCUT_TYPE
    ) =
        accessibilityShortcutsRepository.enableShortcutsForTargets(
            enable = true,
            shortcutType = shortcutType,
            targetNames = setOf(TALKBACK_TARGET_NAME, MAGNIFICATION_TARGET_NAME),
        )

    /**
     * A helper function called before launching dialog. This function is to assume we have only one
     * selected targets, which is Talkback.
     */
    private fun Kosmos.setTalkBackSelected(@UserShortcutType shortcutType: Int = SHORTCUT_TYPE) =
        accessibilityShortcutsRepository.enableShortcutsForTargets(
            enable = true,
            shortcutType = shortcutType,
            targetNames = setOf(TALKBACK_TARGET_NAME),
        )

    private fun Kosmos.getSelectedTargetNames(
        @UserShortcutType shortcutType: Int = SHORTCUT_TYPE
    ): Set<String> =
        accessibilityShortcutsRepository
            .getSelectedAccessibilityTargetsInfo(shortcutType)
            .map { it.targetName }
            .toSet()

    private fun Kosmos.getEnabledTargetNames(
        @UserShortcutType shortcutType: Int = SHORTCUT_TYPE
    ): Set<String> =
        accessibilityShortcutsRepository
            .getAllAccessibilityTargetsInfo(shortcutType)
            .filter { it.isStateOn }
            .map { it.targetName }
            .toSet()

    private fun setOobeCompleted(completed: Boolean) =
        Settings.Secure.putInt(
            context.contentResolver,
            USER_SETUP_COMPLETE,
            if (completed) 1 else 0,
        )

    private fun Kosmos.assertCurrentDialog(
        dialogType: DialogType,
        @UserShortcutType shortcutType: Int = SHORTCUT_TYPE,
    ) {
        assertThat(viewModel.dialogType.value).isEqualTo(dialogType)

        composeTestRule
            .onTutorialDialogTitle()
            .assertDialogVisibility(dialogType, DialogType.TUTORIAL)
        composeTestRule
            .onEditorDialogTitle(shortcutType)
            .assertDialogVisibility(dialogType, DialogType.EDIT_TARGETS)
        composeTestRule
            .onPickerDialogTitle()
            .assertDialogVisibility(dialogType, DialogType.TOGGLE_TARGETS)
        composeTestRule
            .onQuickAccessDialogTitle()
            .assertDialogVisibility(dialogType, DialogType.QUICK_ACCESS)
        composeTestRule
            .onNavBarMoreOptionsDialogTitle()
            .assertDialogVisibility(dialogType, DialogType.NAV_BAR_CHOOSER)
    }

    private fun ComposeTestRule.onAddFeaturesButton() = onNodeWithTag("add_features_button")

    private fun ComposeTestRule.onCancelButton() = onNodeWithTag("cancel_button")

    private fun ComposeTestRule.onDoneButton() = onNodeWithTag("done_button")

    private fun ComposeTestRule.onEditButton() = onNodeWithTag("edit_button")

    /**
     * Click on a button, with scrolling if necessary.
     *
     * Sometimes the number of targets causes the dialog to scroll, so we need to scroll to make the
     * button visible for clicking.
     */
    private fun SemanticsNodeInteraction.performScrollAndClick() =
        performScrollTo().assertIsDisplayed().performClick()

    private fun ComposeTestRule.onTutorialDialogTitle() =
        onNodeWithText(
            context.resources.getString(
                R.string.accessibility_shortcutchooser_toprow_tutorial_dialog_title
            )
        )

    private fun ComposeTestRule.onEditorDialogTitle(
        @UserShortcutType shortcutType: Int = SHORTCUT_TYPE
    ) =
        onNodeWithText(
            context.resources.getString(
                R.string.accessibility_shortcutchooser_editor_dialog_title,
                context.resources.getString(ShortcutUtils.typeToString(shortcutType)),
            )
        )

    private fun ComposeTestRule.onPickerDialogTitle() =
        onNodeWithText(
            context.resources.getString(R.string.accessibility_shortcutchooser_picker_dialog_title)
        )

    private fun ComposeTestRule.onQuickAccessDialogTitle() =
        onNodeWithText(
            context.resources.getString(R.string.accessibility_quick_access_dialog_title)
        )

    private fun ComposeTestRule.onWarningDialog() = onNodeWithTag("service_warning_dialog")

    private fun ComposeTestRule.onNavBarMoreOptionsDialogTitle() =
        onNodeWithText(
            context.resources.getString(R.string.accessibility_nav_bar_more_options_title)
        )

    private fun SemanticsNodeInteraction.assertDialogVisibility(
        expectedDialogType: DialogType,
        assertDialogType: DialogType,
    ) {
        if (expectedDialogType == assertDialogType) {
            assertIsDisplayed()
        } else {
            assertDoesNotExist()
        }
    }

    private fun isNotToggleable() =
        SemanticsMatcher("isNotToggleable") { node -> !isToggleable().matches(node) }

    /** Runs the given test block and dismisses any dialog at the end. */
    private fun runTestAndDismiss(block: suspend Kosmos.() -> Unit) =
        kosmos.runTest {
            try {
                block()
            } finally {
                runOnMainThreadAndWaitForIdleSync {
                    viewModel.dismissDialog()
                    viewModel.dismissWarningDialog()
                }
                composeTestRule.waitForIdle()
            }
        }
}
