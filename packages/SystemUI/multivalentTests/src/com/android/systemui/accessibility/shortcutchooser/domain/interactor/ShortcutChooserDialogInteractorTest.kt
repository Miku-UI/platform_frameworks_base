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

package com.android.systemui.accessibility.shortcutchooser.domain.interactor

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.provider.Settings
import android.view.Display.DEFAULT_DISPLAY
import android.view.Display.INVALID_DISPLAY
import android.view.accessibility.Flags as AccessibilityFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.accessibility.common.ShortcutChooserDialogConstants
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType
import com.android.systemui.Flags as SystemUIFlags
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.data.repository.AccessibilityShortcutsRepository
import com.android.systemui.accessibility.shortcutchooser.shared.model.AccessibilityTargetModel
import com.android.systemui.accessibility.shortcutchooser.shared.model.DialogRequestModel
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmosNew
import com.android.systemui.user.data.repository.userRepository
import com.android.systemui.user.domain.interactor.fakeHeadlessSystemUserMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(AccessibilityFlags.FLAG_ENABLE_A11Y_TOP_ROW_SHORTCUT)
class ShortcutChooserDialogInteractorTest : SysuiTestCase() {
    private companion object {
        const val TALKBACK_TARGET_NAME = "fakeTalkBackTargetName"
        const val MAGNIFICATION_TARGET_NAME = "fakeMagnificationTargetName"
        const val HSU_EXCLUDED_TARGET_NAME = "fakeHsuExcludedTargetName"
        const val KEYGUARD_EXCLUDED_TARGET_NAME = "fakeKeyguardTargetName"
    }

    private val kosmos = testKosmosNew()

    private val mockRepository = mock<AccessibilityShortcutsRepository>()

    private val Kosmos.underTest by
        Kosmos.Fixture {
            ShortcutChooserDialogInteractor(
                context,
                mockRepository,
                displayRepository,
                userRepository,
                fakeHeadlessSystemUserMode,
                keyguardInteractor,
                broadcastDispatcher,
            )
        }

    @Before
    fun setUp() {
        setOobeCompleted(true)
    }

    @Test
    fun dialogRequest_topRowKeyType_onDefaultDisplay_flowIsValid() =
        kosmos.runTest {
            val requestModel by collectLastValue(underTest.dialogRequest)

            sendIntentBroadcast(UserShortcutType.TOP_ROW_KEY, DEFAULT_DISPLAY)

            assertThat(requestModel)
                .isEqualTo(DialogRequestModel(UserShortcutType.TOP_ROW_KEY, DEFAULT_DISPLAY))
        }

    @Test
    fun dialogRequest_topRowKeyType_onExternalDisplay_flowIsValid() =
        kosmos.runTest {
            val requestModel by collectLastValue(underTest.dialogRequest)

            sendIntentBroadcast(UserShortcutType.TOP_ROW_KEY, DEFAULT_DISPLAY + 2)

            assertThat(requestModel)
                .isEqualTo(DialogRequestModel(UserShortcutType.TOP_ROW_KEY, DEFAULT_DISPLAY + 2))
        }

    @Test
    fun dialogRequest_topRowKeyType_onInvalidDisplay_flowIsNull() =
        kosmos.runTest {
            val requestModel by collectLastValue(underTest.dialogRequest)

            sendIntentBroadcast(UserShortcutType.TOP_ROW_KEY, INVALID_DISPLAY)

            assertThat(requestModel).isNull()
        }

    @Test
    fun dialogRequest_topRowKeyType_inOOBE_emitsQuickAccessRequest() =
        kosmos.runTest {
            setOobeCompleted(false)

            val requestModels by collectValues(underTest.dialogRequest)

            sendIntentBroadcast(UserShortcutType.TOP_ROW_KEY, DEFAULT_DISPLAY)

            assertThat(requestModels)
                .isEqualTo(
                    listOf(DialogRequestModel(UserShortcutType.QUICK_ACCESS, DEFAULT_DISPLAY))
                )
        }

    @Test
    fun dialogRequest_topRowKeyType_onLoginScreen_emitsQuickAccessRequest() =
        kosmos.runTest {
            fakeHeadlessSystemUserMode.setIsHeadlessSystemUser(true)

            val requestModels by collectValues(underTest.dialogRequest)

            sendIntentBroadcast(UserShortcutType.TOP_ROW_KEY, DEFAULT_DISPLAY)

            assertThat(requestModels)
                .isEqualTo(
                    listOf(DialogRequestModel(UserShortcutType.QUICK_ACCESS, DEFAULT_DISPLAY))
                )
        }

    @Test
    fun dialogRequest_hardwareType_onDefaultDisplay_flowIsValid() =
        kosmos.runTest {
            val requestModel by collectLastValue(underTest.dialogRequest)

            sendIntentBroadcast(UserShortcutType.HARDWARE, DEFAULT_DISPLAY)

            assertThat(requestModel)
                .isEqualTo(DialogRequestModel(UserShortcutType.HARDWARE, DEFAULT_DISPLAY))
        }

    @Test
    fun dialogRequest_defaultType_onDefaultDisplay_flowIsNull() =
        kosmos.runTest {
            val requestModel by collectLastValue(underTest.dialogRequest)

            sendIntentBroadcast(UserShortcutType.DEFAULT, DEFAULT_DISPLAY)

            assertThat(requestModel).isNull()
        }

    @Test
    fun dialogRequest_softwareType_onDefaultDisplay_flowIsValid() =
        kosmos.runTest {
            val requestModel by collectLastValue(underTest.dialogRequest)

            sendIntentBroadcast(UserShortcutType.SOFTWARE, DEFAULT_DISPLAY)

            assertThat(requestModel)
                .isEqualTo(DialogRequestModel(UserShortcutType.SOFTWARE, DEFAULT_DISPLAY))
        }

    @Test
    @EnableFlags(SystemUIFlags.FLAG_LAUNCH_ACCESSIBILITY_QUICK_ACCESS_DIALOG_PERMISSION)
    fun dialogRequest_quickAccess_validDisplay_emitsRequestModel() =
        kosmos.runTest {
            val requestModel by collectLastValue(underTest.dialogRequest)

            sendIntentBroadcast(UserShortcutType.QUICK_ACCESS, DEFAULT_DISPLAY)

            assertThat(requestModel)
                .isEqualTo(DialogRequestModel(UserShortcutType.QUICK_ACCESS, DEFAULT_DISPLAY))
        }

    @Test
    @DisableFlags(SystemUIFlags.FLAG_LAUNCH_ACCESSIBILITY_QUICK_ACCESS_DIALOG_PERMISSION)
    fun dialogRequest_quickAccess_withFlagDisabled_validDisplay_emitsNull() =
        kosmos.runTest {
            val requestModel by collectLastValue(underTest.dialogRequest)

            sendIntentBroadcast(UserShortcutType.QUICK_ACCESS, DEFAULT_DISPLAY)

            assertThat(requestModel).isNull()
        }

    @Test
    @EnableFlags(SystemUIFlags.FLAG_LAUNCH_ACCESSIBILITY_QUICK_ACCESS_DIALOG_PERMISSION)
    fun dialogRequest_quickAccess_invalidDisplay_emitsNull() =
        kosmos.runTest {
            val requestModel by collectLastValue(underTest.dialogRequest)

            sendIntentBroadcast(UserShortcutType.QUICK_ACCESS, INVALID_DISPLAY)

            assertThat(requestModel).isNull()
        }

    @Test
    fun getAllAccessibilityTargets_getListForAllByType() =
        kosmos.runTest {
            val shortcutType = UserShortcutType.TOP_ROW_KEY

            underTest.getAllAccessibilityTargets(shortcutType)

            verify(mockRepository).getAllAccessibilityTargets(eq(shortcutType))
        }

    @Test
    fun getAssignedAccessibilityTargets_getListForSelectedTargetsByType() =
        kosmos.runTest {
            val shortcutType = UserShortcutType.TOP_ROW_KEY

            underTest.getAssignedAccessibilityTargets(shortcutType)

            verify(mockRepository).getSelectedAccessibilityTargets(eq(shortcutType))
        }

    @Test
    fun getAssignedAccessibilityTargets_onLockScreen_excludesTargets() =
        kosmos.runTest {
            setOobeCompleted(true)
            fakeHeadlessSystemUserMode.setIsHeadlessSystemUser(false)
            fakeKeyguardRepository.setKeyguardShowing(true)
            val shortcutType = UserShortcutType.TOP_ROW_KEY
            val targetName1 = "com.android.test/TestService1"
            val targetName2 = KEYGUARD_EXCLUDED_TARGET_NAME
            val targets =
                listOf(
                    createTargetModel(shortcutType, targetName1),
                    createTargetModel(shortcutType, targetName2),
                )
            whenever(mockRepository.getSelectedAccessibilityTargets(shortcutType))
                .thenReturn(flowOf(targets))
            whenever(mockRepository.keyguardExcludedTargets)
                .thenReturn(listOf(KEYGUARD_EXCLUDED_TARGET_NAME))

            val assignedTargets by
                collectLastValue(underTest.getAssignedAccessibilityTargets(shortcutType))

            assertThat(assignedTargets).containsExactly(targets[0])
        }

    @Test
    fun getAssignedAccessibilityTargetsCount_lockAndThenUnlock_getCorrectCount() =
        kosmos.runTest {
            setOobeCompleted(true)
            fakeHeadlessSystemUserMode.setIsHeadlessSystemUser(false)
            val shortcutType = UserShortcutType.TOP_ROW_KEY
            val targetName1 = "com.android.test/TestService1"
            val targetName2 = KEYGUARD_EXCLUDED_TARGET_NAME
            val targets =
                listOf(
                    createTargetModel(shortcutType, targetName1),
                    createTargetModel(shortcutType, targetName2),
                )
            whenever(mockRepository.getSelectedAccessibilityTargetsInfo(shortcutType))
                .thenReturn(targets)
            whenever(mockRepository.keyguardExcludedTargets)
                .thenReturn(listOf(KEYGUARD_EXCLUDED_TARGET_NAME))

            // On lock screen
            fakeKeyguardRepository.setKeyguardShowing(true)
            assertThat(underTest.getAssignedAccessibilityTargetsCount(shortcutType))
                .isEqualTo(targets.size - 1)

            // Unlock screen
            fakeKeyguardRepository.setKeyguardShowing(false)
            assertThat(underTest.getAssignedAccessibilityTargetsCount(shortcutType))
                .isEqualTo(targets.size)
        }

    @Test
    fun getAssignedAccessibilityTargetsCount_getAssignedTargetsCount() =
        kosmos.runTest {
            val shortcutType = UserShortcutType.TOP_ROW_KEY
            val targets =
                listOf(
                    createTargetModel(shortcutType, TALKBACK_TARGET_NAME),
                    createTargetModel(shortcutType, MAGNIFICATION_TARGET_NAME),
                )

            whenever(mockRepository.getSelectedAccessibilityTargetsInfo(shortcutType))
                .thenReturn(targets)

            assertThat(underTest.getAssignedAccessibilityTargetsCount(shortcutType))
                .isEqualTo(targets.size)
        }

    @Test
    fun getDialogContextByDisplayId_externalDisplayIdRequested_returnExpectedContext() =
        kosmos.runTest {
            // Mock the default application context's displayId is 0.
            val mockApplicationContext = mock<Context>()
            whenever(mockApplicationContext.displayId).thenReturn(DEFAULT_DISPLAY)
            // The dialog displayId is 2.
            val externalDisplayId = 2
            displayRepository.addDisplay(externalDisplayId)
            val externalDisplay = displayRepository.getDisplay(externalDisplayId)!!
            val mockDisplayContext = mock<Context>()
            whenever(mockApplicationContext.createDisplayContext(externalDisplay))
                .thenReturn(mockDisplayContext)

            val result =
                underTest.getDialogContextByDisplayId(externalDisplayId, mockApplicationContext)

            assertThat(result).isEqualTo(mockDisplayContext)
        }

    @Test
    fun enableShortcutForTarget_topRowKeyType_enableMagnification() =
        kosmos.runTest {
            val shortcutType = UserShortcutType.TOP_ROW_KEY
            val targetName = MAGNIFICATION_TARGET_NAME

            underTest.enableShortcutForTarget(enable = true, shortcutType, targetName)

            verify(mockRepository)
                .enableShortcutsForTargets(eq(true), eq(shortcutType), eq(setOf(targetName)))
        }

    @Test
    fun enableShortcutForTarget_topRowKeyType_disableMagnification() =
        kosmos.runTest {
            val shortcutType = UserShortcutType.TOP_ROW_KEY
            val targetName = MAGNIFICATION_TARGET_NAME

            underTest.enableShortcutForTarget(enable = false, shortcutType, targetName)

            verify(mockRepository)
                .enableShortcutsForTargets(eq(false), eq(shortcutType), eq(setOf(targetName)))
        }

    @Test
    fun enableShortcutForAllAllowedTargets_enablesUnassignedShortcuts() =
        kosmos.runTest {
            val shortcutType = UserShortcutType.QUICK_ACCESS
            val targetName1 = "com.android.test/TestService1"
            val targetName2 = "com.android.test/TestService2"
            val targets =
                listOf(
                    createTargetModel(shortcutType, targetName1, isAssigned = true),
                    createTargetModel(shortcutType, targetName2, isAssigned = false),
                )
            whenever(mockRepository.getAllAccessibilityTargetsInfo(shortcutType))
                .thenReturn(targets)

            underTest.enableShortcutForAllAllowedTargets(shortcutType)

            verify(mockRepository)
                .enableShortcutsForTargets(eq(true), eq(shortcutType), eq(setOf(targetName2)))
        }

    @Test
    fun enableShortcutForAllAllowedTargets_doesNotEnableShortcutsNeedingWarning() =
        kosmos.runTest {
            val shortcutType = UserShortcutType.QUICK_ACCESS
            val targetName1 = "com.android.test/TestService1"
            val targetName2 = "com.android.test/TestService2"
            val targets =
                listOf(
                    createTargetModel(shortcutType, targetName1, isAssigned = false),
                    createTargetModel(shortcutType, targetName2, isAssigned = false),
                )
            whenever(mockRepository.getAllAccessibilityTargetsInfo(shortcutType))
                .thenReturn(targets)
            whenever(mockRepository.isServiceWarningRequired(targets[0])).thenReturn(false)
            whenever(mockRepository.isServiceWarningRequired(targets[1])).thenReturn(true)

            underTest.enableShortcutForAllAllowedTargets(shortcutType)

            verify(mockRepository)
                .enableShortcutsForTargets(eq(true), eq(shortcutType), eq(setOf(targetName1)))
        }

    @Test
    fun enableShortcutForAllAllowedTargets_isCompletedFullUser_doesNotExcludeTargets() =
        kosmos.runTest {
            val shortcutType = UserShortcutType.QUICK_ACCESS
            val targetName1 = "com.android.test/TestService1"
            val targetName2 = HSU_EXCLUDED_TARGET_NAME
            val targets =
                listOf(
                    createTargetModel(shortcutType, targetName1, isAssigned = false),
                    createTargetModel(shortcutType, targetName2, isAssigned = false),
                )
            whenever(mockRepository.getAllAccessibilityTargetsInfo(shortcutType))
                .thenReturn(targets)
            whenever(mockRepository.hsuExcludedTargets).thenReturn(listOf(HSU_EXCLUDED_TARGET_NAME))
            fakeHeadlessSystemUserMode.setIsHeadlessSystemUser(false)
            setOobeCompleted(true)
            assertThat(underTest.isCompletedFullUser()).isTrue()

            underTest.enableShortcutForAllAllowedTargets(shortcutType)

            verify(mockRepository)
                .enableShortcutsForTargets(
                    eq(true),
                    eq(shortcutType),
                    eq(setOf(targetName1, targetName2)),
                )
        }

    @Test
    fun enableShortcutForAllAllowedTargets_notCompletedFullUser_excludesTargets() =
        kosmos.runTest {
            val shortcutType = UserShortcutType.QUICK_ACCESS
            val targetName1 = "com.android.test/TestService1"
            val targetName2 = HSU_EXCLUDED_TARGET_NAME
            val targets =
                listOf(
                    createTargetModel(shortcutType, targetName1, isAssigned = false),
                    createTargetModel(shortcutType, targetName2, isAssigned = false),
                )
            whenever(mockRepository.getAllAccessibilityTargetsInfo(shortcutType))
                .thenReturn(targets)
            whenever(mockRepository.hsuExcludedTargets).thenReturn(listOf(HSU_EXCLUDED_TARGET_NAME))
            fakeHeadlessSystemUserMode.setIsHeadlessSystemUser(true)
            setOobeCompleted(true)
            assertThat(underTest.isCompletedFullUser()).isFalse()

            underTest.enableShortcutForAllAllowedTargets(shortcutType)

            verify(mockRepository)
                .enableShortcutsForTargets(eq(true), eq(shortcutType), eq(setOf(targetName1)))
        }

    @Test
    fun performAccessibilityShortcut_topRowKeyType_toggleMagnification() =
        kosmos.runTest {
            val displayId = DEFAULT_DISPLAY
            val shortcutType = UserShortcutType.TOP_ROW_KEY
            val targetName = MAGNIFICATION_TARGET_NAME

            underTest.performAccessibilityShortcut(displayId, shortcutType, targetName)

            verify(mockRepository)
                .performAccessibilityShortcut(eq(displayId), eq(shortcutType), eq(targetName))
        }

    @Test
    fun performAccessibilityShortcut_topRowKeyType_invalidDisplay_doNothing() =
        kosmos.runTest {
            val displayId = INVALID_DISPLAY
            val shortcutType = UserShortcutType.TOP_ROW_KEY
            val targetName = MAGNIFICATION_TARGET_NAME

            underTest.performAccessibilityShortcut(displayId, shortcutType, targetName)

            verify(mockRepository, never())
                .performAccessibilityShortcut(anyInt(), anyInt(), anyString())
        }

    @Test
    fun isCompletedFullUser_notHSU_notOOBE_returnsTrue() =
        kosmos.runTest {
            fakeHeadlessSystemUserMode.setIsHeadlessSystemUser(false)
            setOobeCompleted(true)
            assertThat(underTest.isCompletedFullUser()).isTrue()
        }

    @Test
    fun isCompletedFullUser_isHSU_notOOBE_returnsFalse() =
        kosmos.runTest {
            fakeHeadlessSystemUserMode.setIsHeadlessSystemUser(true)
            setOobeCompleted(true)
            assertThat(underTest.isCompletedFullUser()).isFalse()
        }

    @Test
    fun isCompletedFullUser_notHSU_isOOBE_returnsFalse() =
        kosmos.runTest {
            fakeHeadlessSystemUserMode.setIsHeadlessSystemUser(false)
            setOobeCompleted(false)
            assertThat(underTest.isCompletedFullUser()).isFalse()
        }

    @Test
    fun isCompletedFullUser_isHSU_isOOBE_returnsFalse() =
        kosmos.runTest {
            fakeHeadlessSystemUserMode.setIsHeadlessSystemUser(true)
            setOobeCompleted(false)
            assertThat(underTest.isCompletedFullUser()).isFalse()
        }

    private fun Kosmos.sendIntentBroadcast(@UserShortcutType shortcutType: Int, displayId: Int) {
        Intent()
            .apply {
                action = ShortcutChooserDialogConstants.LAUNCH_SHORTCUT_CHOOSER_DIALOG_ACTION
                putExtra(ShortcutChooserDialogConstants.SHORTCUT_TYPE, shortcutType)
                putExtra(ShortcutChooserDialogConstants.DISPLAY_ID, displayId)
            }
            .let { broadcastDispatcher.sendIntentToMatchingReceiversOnly(context, it) }
    }

    private fun createTargetModel(
        @UserShortcutType shortcutType: Int,
        targetName: String,
        isAssigned: Boolean = true,
    ) =
        AccessibilityTargetModel(
            shortcutType = shortcutType,
            targetName = targetName,
            featureName = targetName,
            icon = ColorDrawable(Color.RED),
            isAssigned = isAssigned,
            isToggleable = true,
            isStateOn = false,
        )

    private fun setOobeCompleted(completed: Boolean) =
        Settings.Secure.putInt(
            context.contentResolver,
            Settings.Secure.USER_SETUP_COMPLETE,
            if (completed) 1 else 0,
        )
}
