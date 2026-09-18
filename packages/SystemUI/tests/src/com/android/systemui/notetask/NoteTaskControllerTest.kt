/*
 * Copyright (C) 2022 The Android Open Source Project
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
@file:OptIn(InternalNoteTaskApi::class)

package com.android.systemui.notetask

import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.app.role.RoleManager.ROLE_NOTES
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_CREATE_NOTE
import android.content.Intent.ACTION_MAIN
import android.content.Intent.CATEGORY_HOME
import android.content.Intent.EXTRA_USE_STYLUS_MODE
import android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_DOCUMENT
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.pm.UserInfo
import android.content.res.Resources
import android.graphics.drawable.Icon
import android.os.UserHandle
import android.os.UserManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.truth.content.IntentSubject.assertThat
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.notetask.NoteTaskController.Companion.EXTRA_SHORTCUT_BADGE_OVERRIDE_PACKAGE
import com.android.systemui.notetask.NoteTaskController.Companion.SHORTCUT_ID
import com.android.systemui.notetask.NoteTaskEntryPoint.KEYBOARD_SHORTCUT
import com.android.systemui.notetask.NoteTaskEntryPoint.QUICK_AFFORDANCE
import com.android.systemui.notetask.NoteTaskEntryPoint.TAIL_BUTTON
import com.android.systemui.notetask.shortcut.CreateNoteTaskShortcutActivity
import com.android.systemui.notetask.shortcut.LaunchNoteTaskActivity
import com.android.systemui.res.R
import com.android.systemui.settings.FakeUserTracker
import com.android.systemui.util.mockito.mock
import com.android.wm.shell.bubbles.Bubble
import com.android.wm.shell.bubbles.Bubbles
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

/** atest SystemUITests:NoteTaskControllerTest */
@SmallTest
@RunWith(AndroidJUnit4::class)
internal class NoteTaskControllerTest : SysuiTestCase() {

    @Mock private lateinit var context: Context
    @Mock private lateinit var workProfileContext: Context
    @Mock private lateinit var packageManager: PackageManager
    @Mock private lateinit var workProfilePackageManager: PackageManager
    @Mock private lateinit var noteTaskInfoResolver: NoteTaskInfoResolver
    @Mock private lateinit var bubbles: Bubbles
    @Mock private lateinit var keyguardManager: KeyguardManager
    @Mock private lateinit var userManager: UserManager
    @Mock private lateinit var eventLogger: NoteTaskEventLogger
    @Mock private lateinit var roleManager: RoleManager
    @Mock private lateinit var shortcutManager: ShortcutManager
    @Mock private lateinit var activityManager: ActivityManager
    @Mock private lateinit var devicePolicyManager: DevicePolicyManager
    @Mock private lateinit var lockscreenNoteTakingAvailability: LockscreenNoteTakingAvailability
    @Mock private lateinit var userResolver: NoteTaskUserResolver
    private lateinit var spiedResources: Resources
    private val userTracker = FakeUserTracker()
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp(): Unit = runBlocking {
        MockitoAnnotations.initMocks(this@NoteTaskControllerTest)

        whenever(context.getString(R.string.note_task_button_label))
            .thenReturn(NOTE_TASK_SHORT_LABEL)
        whenever(context.getString(eq(R.string.note_task_shortcut_long_label), any()))
            .thenReturn(NOTE_TASK_LONG_LABEL)
        whenever(context.packageManager).thenReturn(packageManager)
        whenever(context.createContextAsUser(any(), any())).thenReturn(context)
        whenever(packageManager.getApplicationInfo(any(), any<Int>())).thenReturn(mock())
        whenever(packageManager.getApplicationLabel(any())).thenReturn(NOTE_TASK_LONG_LABEL)
        whenever(userManager.isUserUnlocked).thenReturn(true)
        whenever(userManager.isUserUnlocked(any<Int>())).thenReturn(true)
        whenever(userManager.isUserUnlocked(any<UserHandle>())).thenReturn(true)
        whenever(userResolver.getUserForHandlingNoteTaking(any()))
            .thenReturn(userTracker.userHandle)
        whenever(
                devicePolicyManager.getKeyguardDisabledFeatures(
                    /* admin= */ eq(null),
                    /* userHandle= */ any<Int>(),
                )
            )
            .thenReturn(DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE)
        whenever(roleManager.getRoleHoldersAsUser(ROLE_NOTES, userTracker.userHandle))
            .thenReturn(listOf(NOTE_TASK_PACKAGE_NAME))
        whenever(activityManager.getRunningTasks(any<Int>())).thenReturn(emptyList())
        whenever(userManager.isManagedProfile(workUserInfo.id)).thenReturn(true)
        whenever(context.resources).thenReturn(getContext().resources)

        spiedResources = spy(context.resources)
        whenever(context.resources).thenReturn(spiedResources)

        setupTestScenario(
            locked = false,
            noteTaskInfo = NOTE_TASK_INFO,
            isLockscreenNoteTakingEnabled = true,
        )
    }

    private fun createNoteTaskController(
        isEnabled: Boolean = true,
        bubbles: Bubbles? = this.bubbles,
    ): NoteTaskController =
        NoteTaskController(
            context = context,
            noteTaskInfoResolver = noteTaskInfoResolver,
            eventLogger = eventLogger,
            userManager = userManager,
            keyguardManager = keyguardManager,
            isEnabled = isEnabled,
            devicePolicyManager = devicePolicyManager,
            userTracker = userTracker,
            roleManager = roleManager,
            shortcutManager = shortcutManager,
            activityManager = activityManager,
            lockscreenNoteTakingAvailability = lockscreenNoteTakingAvailability,
            userResolver = userResolver,
            noteTaskBubblesController =
                FakeNoteTaskBubbleController(context, testDispatcher, Optional.ofNullable(bubbles)),
            applicationScope = testScope,
            bgCoroutineContext = testScope.backgroundScope.coroutineContext,
        )

    private fun setupTestScenario(
        locked: Boolean,
        noteTaskInfo: NoteTaskInfo? = NOTE_TASK_INFO,
        isLockscreenNoteTakingEnabled: Boolean = true,
    ) = runBlocking {
        whenever(keyguardManager.isKeyguardLocked).thenReturn(locked)
        whenever(noteTaskInfoResolver.resolveInfo(any(), eq(locked), any()))
            .thenReturn(noteTaskInfo)
        whenever(lockscreenNoteTakingAvailability.isLockscreenNoteTakingEnabled())
            .thenReturn(isLockscreenNoteTakingEnabled)
    }

    // region onBubbleExpandChanged
    @Test
    fun onBubbleExpandChanged_expanding_logNoteTaskOpened() {
        val expectedInfo = NOTE_TASK_INFO.copy(isKeyguardLocked = false)

        createNoteTaskController()
            .apply { infoReference.set(expectedInfo) }
            .onBubbleExpandChanged(
                isExpanding = true,
                key = Bubble.getNoteBubbleKeyForApp(expectedInfo.packageName, expectedInfo.user),
            )

        verify(eventLogger).logNoteTaskOpened(expectedInfo)
        verifyNoMoreInteractions(bubbles, keyguardManager, userManager)
    }

    @Test
    fun onBubbleExpandChanged_collapsing_logNoteTaskClosed() {
        val expectedInfo = NOTE_TASK_INFO.copy(isKeyguardLocked = false)

        createNoteTaskController()
            .apply { infoReference.set(expectedInfo) }
            .onBubbleExpandChanged(
                isExpanding = false,
                key = Bubble.getNoteBubbleKeyForApp(expectedInfo.packageName, expectedInfo.user),
            )

        verify(eventLogger).logNoteTaskClosed(expectedInfo)
        verifyNoMoreInteractions(bubbles, keyguardManager, userManager)
    }

    @Test
    fun onBubbleExpandChanged_expandingAndKeyguardLocked_shouldDoNothing() {
        val expectedInfo = NOTE_TASK_INFO.copy(isKeyguardLocked = true)

        createNoteTaskController()
            .apply { infoReference.set(expectedInfo) }
            .onBubbleExpandChanged(
                isExpanding = true,
                key = Bubble.getNoteBubbleKeyForApp(expectedInfo.packageName, expectedInfo.user),
            )

        verifyNoMoreInteractions(bubbles, keyguardManager, userManager, eventLogger)
    }

    @Test
    fun onBubbleExpandChanged_notExpandingAndKeyguardLocked_shouldDoNothing() {
        val expectedInfo = NOTE_TASK_INFO.copy(isKeyguardLocked = true)

        createNoteTaskController()
            .apply { infoReference.set(expectedInfo) }
            .onBubbleExpandChanged(
                isExpanding = false,
                key = Bubble.getNoteBubbleKeyForApp(expectedInfo.packageName, expectedInfo.user),
            )

        verifyNoMoreInteractions(bubbles, keyguardManager, userManager, eventLogger)
    }

    @Test
    fun onBubbleExpandChanged_notKeyNoteBubble_shouldDoNothing() {
        createNoteTaskController().onBubbleExpandChanged(isExpanding = true, key = "any other key")

        verifyNoMoreInteractions(bubbles, keyguardManager, userManager, eventLogger)
    }

    @Test
    fun onBubbleExpandChanged_flagDisabled_shouldDoNothing() {
        createNoteTaskController(isEnabled = false)
            .onBubbleExpandChanged(
                isExpanding = true,
                key = Bubble.getNoteBubbleKeyForApp(NOTE_TASK_INFO.packageName, NOTE_TASK_INFO.user),
            )

        verifyNoMoreInteractions(bubbles, keyguardManager, userManager, eventLogger)
    }

    // endregion

    // region showNoteTask
    @Test
    fun showNoteTaskAsUser_keyguardLocked_shouldStartActivityWithExpectedUserAndLogUiEvent() {
        val user10 = UserHandle.of(/* userId= */ 10)
        val expectedInfo =
            NOTE_TASK_INFO.copy(entryPoint = TAIL_BUTTON, isKeyguardLocked = true, user = user10)
        setupTestScenario(locked = true, noteTaskInfo = expectedInfo)

        createNoteTaskController()
            .showNoteTaskAsUser(entryPoint = expectedInfo.entryPoint!!, user = user10)

        verifyNoteTaskOpenedAsActivity(user10, expectedUseStylusMode = true)
        verify(eventLogger).logNoteTaskOpened(expectedInfo)
        verifyNoMoreInteractions(bubbles)
    }

    @Test
    fun showNoteTask_keyguardLocked_notesIsClosed_shouldStartActivityAndLogUiEvent() {
        val expectedInfo = NOTE_TASK_INFO.copy(entryPoint = TAIL_BUTTON, isKeyguardLocked = true)
        setupTestScenario(locked = true, noteTaskInfo = expectedInfo)

        createNoteTaskController().showNoteTask(entryPoint = expectedInfo.entryPoint!!)

        verifyNoteTaskOpenedAsActivity(userTracker.userHandle, expectedUseStylusMode = true)
        verify(eventLogger).logNoteTaskOpened(expectedInfo)
        verifyNoMoreInteractions(bubbles)
    }

    @Test
    fun showNoteTask_keyguardLocked_noteIsOpen_shouldCloseActivityAndLogUiEvent() {
        val expectedInfo = NOTE_TASK_INFO.copy(entryPoint = TAIL_BUTTON, isKeyguardLocked = true)
        setupTestScenario(locked = true, noteTaskInfo = expectedInfo)
        whenever(activityManager.getRunningTasks(any<Int>()))
            .thenReturn(listOf(NOTE_RUNNING_TASK_INFO))

        createNoteTaskController().showNoteTask(entryPoint = expectedInfo.entryPoint!!)

        val intentCaptor = argumentCaptor<Intent>()
        val userCaptor = argumentCaptor<UserHandle>()
        verify(context).startActivityAsUser(intentCaptor.capture(), userCaptor.capture())
        assertThat(intentCaptor.lastValue).run {
            hasAction(ACTION_MAIN)
            categories().contains(CATEGORY_HOME)
            hasFlags(FLAG_ACTIVITY_NEW_TASK)
        }
        assertThat(userCaptor.lastValue).isEqualTo(userTracker.userHandle)
        verify(eventLogger).logNoteTaskClosed(expectedInfo)
        verifyNoMoreInteractions(bubbles)
    }

    @Test
    fun showNoteTask_keyguardIsUnlocked_noteIsClosed_shouldStartBubblesWithoutLoggingUiEvent() {
        val expectedInfo = NOTE_TASK_INFO.copy(entryPoint = TAIL_BUTTON, isKeyguardLocked = false)
        setupTestScenario(locked = false, noteTaskInfo = expectedInfo)

        createNoteTaskController().showNoteTask(entryPoint = expectedInfo.entryPoint!!)

        // Context package name used to create bubble icon from drawable resource id
        verify(context, atLeastOnce()).packageName
        verifyNoteTaskOpenInBubbleInUser(userTracker.userHandle)
        verifyNoMoreInteractions(eventLogger)
    }

    @Test
    fun showNoteTask_keyguardIsUnlocked_noteIsOpen_shouldStartBubblesWithoutLoggingUiEvent() {
        val expectedInfo = NOTE_TASK_INFO.copy(entryPoint = TAIL_BUTTON, isKeyguardLocked = false)
        setupTestScenario(locked = false, noteTaskInfo = expectedInfo)
        whenever(activityManager.getRunningTasks(any<Int>()))
            .thenReturn(listOf(NOTE_RUNNING_TASK_INFO))

        createNoteTaskController().showNoteTask(entryPoint = expectedInfo.entryPoint!!)

        // Context package name used to create bubble icon from drawable resource id
        verify(context, atLeastOnce()).packageName
        verifyNoteTaskOpenInBubbleInUser(userTracker.userHandle)
        verifyNoMoreInteractions(eventLogger)
    }

    @Test
    fun showNoteTask_keyguardLocked_defaultUserSet_shouldStartActivityWithExpectedUserAndLogUiEvent() =
        runTest {
            val user10 = UserHandle.of(/* userId= */ 10)
            whenever(userResolver.getUserForHandlingNoteTaking(TAIL_BUTTON)).thenReturn(user10)

            val expectedInfo =
                NOTE_TASK_INFO.copy(
                    entryPoint = TAIL_BUTTON,
                    isKeyguardLocked = true,
                    user = user10,
                )
            setupTestScenario(locked = true, noteTaskInfo = expectedInfo)

            createNoteTaskController().showNoteTask(entryPoint = expectedInfo.entryPoint!!)
            testScope.runCurrent()

            verifyNoteTaskOpenedAsActivity(user10, expectedUseStylusMode = true)
            verify(eventLogger).logNoteTaskOpened(expectedInfo)
            verifyNoMoreInteractions(bubbles)
        }

    @Test
    fun showNoteTask_bubblesIsNull_shouldDoNothing() {
        createNoteTaskController(bubbles = null).showNoteTask(entryPoint = TAIL_BUTTON)

        verifyNoMoreInteractions(bubbles, eventLogger)
    }

    @Test
    fun showNoteTask_intentResolverReturnsNull_shouldShowToast() {
        whenever(noteTaskInfoResolver.resolveInfo(any(), any(), any())).thenReturn(null)
        val noteTaskController = spy(createNoteTaskController())
        doNothing().whenever(noteTaskController).showNoDefaultNotesAppToast()

        noteTaskController.showNoteTask(entryPoint = TAIL_BUTTON)

        verify(noteTaskController).showNoDefaultNotesAppToast()
        verifyNoMoreInteractions(bubbles, eventLogger)
    }

    @Test
    fun showNoteTask_flagDisabled_shouldDoNothing() {
        createNoteTaskController(isEnabled = false).showNoteTask(entryPoint = TAIL_BUTTON)

        verifyNoMoreInteractions(bubbles, eventLogger)
    }

    @Test
    fun showNoteTask_userIsLocked_shouldDoNothing() {
        whenever(userManager.isUserUnlocked).thenReturn(false)

        createNoteTaskController().showNoteTask(entryPoint = TAIL_BUTTON)

        verifyNoMoreInteractions(bubbles, eventLogger)
    }

    @Test
    fun showNoteTask_keyguardLocked_keyboardShortcut_shouldStartActivity() {
        val expectedInfo =
            NOTE_TASK_INFO.copy(entryPoint = KEYBOARD_SHORTCUT, isKeyguardLocked = true)
        setupTestScenario(locked = true, noteTaskInfo = expectedInfo)

        createNoteTaskController().showNoteTask(entryPoint = expectedInfo.entryPoint!!)

        verifyNoteTaskOpenedAsActivity(userTracker.userHandle, expectedUseStylusMode = false)
        verify(eventLogger).logNoteTaskOpened(expectedInfo)
        verifyNoMoreInteractions(bubbles)
    }

    @Test
    fun showNoteTask_keyguardLocked_stylusModePreferred_keyboardShortcut_shouldStartInDefaultUIMode() {
        whenever(spiedResources.getInteger(R.integer.config_preferredNotesMode)).thenReturn(1)
        val expectedInfo =
            NOTE_TASK_INFO.copy(entryPoint = KEYBOARD_SHORTCUT, isKeyguardLocked = true)
        setupTestScenario(locked = true, noteTaskInfo = expectedInfo)

        createNoteTaskController().showNoteTask(entryPoint = expectedInfo.entryPoint!!)

        verifyNoteTaskOpenedAsActivity(userTracker.userHandle, expectedUseStylusMode = false)
    }

    @Test
    fun showNoteTask_keyguardLocked_stylusModePreferred_quickAffordance_shouldStartInStylusUIMode() {
        whenever(spiedResources.getInteger(R.integer.config_preferredNotesMode)).thenReturn(1)
        val expectedInfo =
            NOTE_TASK_INFO.copy(entryPoint = QUICK_AFFORDANCE, isKeyguardLocked = true)
        setupTestScenario(locked = true, noteTaskInfo = expectedInfo)

        createNoteTaskController().showNoteTask(entryPoint = expectedInfo.entryPoint!!)

        verifyNoteTaskOpenedAsActivity(userTracker.userHandle, expectedUseStylusMode = true)
    }

    @Test
    fun showNoteTask_keyguardLocked_noUIRecommendation_quickAffordance_shouldStartInDefaultUIMode() {
        whenever(spiedResources.getInteger(R.integer.config_preferredNotesMode)).thenReturn(0)
        val expectedInfo =
            NOTE_TASK_INFO.copy(entryPoint = QUICK_AFFORDANCE, isKeyguardLocked = true)
        setupTestScenario(locked = true, noteTaskInfo = expectedInfo)

        createNoteTaskController().showNoteTask(entryPoint = expectedInfo.entryPoint!!)

        verifyNoteTaskOpenedAsActivity(userTracker.userHandle, expectedUseStylusMode = false)
    }

    @Test
    fun showNoteTask_keyguardLocked_noUIRecommendation_tailButton_shouldStartInStylusUIMode() {
        whenever(spiedResources.getInteger(R.integer.config_preferredNotesMode)).thenReturn(0)
        val expectedInfo = NOTE_TASK_INFO.copy(entryPoint = TAIL_BUTTON, isKeyguardLocked = true)
        setupTestScenario(locked = true, noteTaskInfo = expectedInfo)

        createNoteTaskController().showNoteTask(entryPoint = expectedInfo.entryPoint!!)

        verifyNoteTaskOpenedAsActivity(userTracker.userHandle, expectedUseStylusMode = true)
    }

    @Test
    fun showNoteTask_keyguardLocked_actionCorner_shouldStartInStylusUIMode() {
        val expectedInfo =
            NOTE_TASK_INFO.copy(
                entryPoint = NoteTaskEntryPoint.ACTION_CORNER,
                isKeyguardLocked = true,
            )
        setupTestScenario(locked = true, noteTaskInfo = expectedInfo)

        createNoteTaskController().showNoteTask(entryPoint = expectedInfo.entryPoint!!)

        verifyNoteTaskOpenedAsActivity(userTracker.userHandle, expectedUseStylusMode = true)
    }

    // endregion

    // region setNoteTaskShortcutEnabled
    @Test
    @Ignore("b/316332684")
    fun setNoteTaskShortcutEnabled_setTrue() {
        createNoteTaskController().setNoteTaskShortcutEnabled(value = true, userTracker.userHandle)

        val argument = argumentCaptor<ComponentName>()
        verify(context.packageManager)
            .setComponentEnabledSetting(
                argument.capture(),
                eq(COMPONENT_ENABLED_STATE_ENABLED),
                eq(PackageManager.DONT_KILL_APP),
            )

        assertThat(argument.lastValue.className)
            .isEqualTo(CreateNoteTaskShortcutActivity::class.java.name)
    }

    @Test
    @Ignore("b/316332684")
    fun setNoteTaskShortcutEnabled_setFalse() {
        createNoteTaskController().setNoteTaskShortcutEnabled(value = false, userTracker.userHandle)

        val argument = argumentCaptor<ComponentName>()
        verify(context.packageManager)
            .setComponentEnabledSetting(
                argument.capture(),
                eq(COMPONENT_ENABLED_STATE_DISABLED),
                eq(PackageManager.DONT_KILL_APP),
            )

        assertThat(argument.lastValue.className)
            .isEqualTo(CreateNoteTaskShortcutActivity::class.java.name)
    }

    @Test
    @Ignore("b/316332684")
    fun setNoteTaskShortcutEnabled_workProfileUser_setTrue() {
        whenever(context.createContextAsUser(eq(workUserInfo.userHandle), any()))
            .thenReturn(workProfileContext)
        whenever(workProfileContext.packageManager).thenReturn(workProfilePackageManager)
        userTracker.set(mainAndWorkProfileUsers, mainAndWorkProfileUsers.indexOf(mainUserInfo))

        createNoteTaskController().setNoteTaskShortcutEnabled(value = true, workUserInfo.userHandle)

        val argument = argumentCaptor<ComponentName>()
        verify(workProfilePackageManager)
            .setComponentEnabledSetting(
                argument.capture(),
                eq(COMPONENT_ENABLED_STATE_ENABLED),
                eq(PackageManager.DONT_KILL_APP),
            )

        assertThat(argument.lastValue.className)
            .isEqualTo(CreateNoteTaskShortcutActivity::class.java.name)
    }

    @Test
    @Ignore("b/316332684")
    fun setNoteTaskShortcutEnabled_workProfileUser_setFalse() {
        whenever(context.createContextAsUser(eq(workUserInfo.userHandle), any()))
            .thenReturn(workProfileContext)
        whenever(workProfileContext.packageManager).thenReturn(workProfilePackageManager)
        userTracker.set(mainAndWorkProfileUsers, mainAndWorkProfileUsers.indexOf(mainUserInfo))

        createNoteTaskController()
            .setNoteTaskShortcutEnabled(value = false, workUserInfo.userHandle)

        val argument = argumentCaptor<ComponentName>()
        verify(workProfilePackageManager)
            .setComponentEnabledSetting(
                argument.capture(),
                eq(COMPONENT_ENABLED_STATE_DISABLED),
                eq(PackageManager.DONT_KILL_APP),
            )

        assertThat(argument.lastValue.className)
            .isEqualTo(CreateNoteTaskShortcutActivity::class.java.name)
    }

    // endregion

    // region lockscreen notes
    @Test
    fun showNoteTask_keyguardLocked_lockscreenNoteTakingDisabled_shouldDoNothing() {
        setupTestScenario(locked = true, isLockscreenNoteTakingEnabled = false)

        createNoteTaskController().showNoteTask(entryPoint = QUICK_AFFORDANCE)

        verifyNoMoreInteractions(bubbles, eventLogger)
    }

    @Test
    fun showNoteTask_keyguardLocked_lockscreenNoteTakingEnabled_shouldStartActivity() {
        val expectedInfo = NOTE_TASK_INFO.copy(isKeyguardLocked = true)
        setupTestScenario(
            locked = true,
            noteTaskInfo = expectedInfo,
            isLockscreenNoteTakingEnabled = true,
        )

        createNoteTaskController().showNoteTask(entryPoint = QUICK_AFFORDANCE)

        verifyNoteTaskOpenedAsActivity(userTracker.userHandle, expectedUseStylusMode = true)
        verify(eventLogger).logNoteTaskOpened(expectedInfo)
        verifyNoMoreInteractions(bubbles)
    }

    @Test
    fun showNoteTask_keyguardUnlocked_lockscreenNoteTakingDisabled_shouldStartBubble() {
        val expectedInfo = NOTE_TASK_INFO.copy(isKeyguardLocked = false)
        setupTestScenario(
            locked = false,
            noteTaskInfo = expectedInfo,
            isLockscreenNoteTakingEnabled = false,
        )

        createNoteTaskController().showNoteTask(entryPoint = QUICK_AFFORDANCE)

        verifyNoteTaskOpenInBubbleInUser(userTracker.userHandle)
    }

    @Test
    fun showNoteTask_keyguardUnlocked_lockscreenNoteTakingEnabled_shouldStartBubble() {
        val expectedInfo = NOTE_TASK_INFO.copy(isKeyguardLocked = false)
        setupTestScenario(
            locked = false,
            noteTaskInfo = expectedInfo,
            isLockscreenNoteTakingEnabled = true,
        )

        createNoteTaskController().showNoteTask(entryPoint = QUICK_AFFORDANCE)

        verifyNoteTaskOpenInBubbleInUser(userTracker.userHandle)
    }

    // endregion

    // region showNoteTask, UserResolver integration
    @Test
    fun showNoteTask_shouldUseUserResolver() = runTest {
        val expectedUser = UserHandle.of(100)
        whenever(userResolver.getUserForHandlingNoteTaking(QUICK_AFFORDANCE))
            .thenReturn(expectedUser)

        createNoteTaskController().showNoteTask(entryPoint = QUICK_AFFORDANCE)

        verifyNoteTaskOpenInBubbleInUser(expectedUser)
    }

    // endregion

    private fun verifyNoteTaskOpenedAsActivity(user: UserHandle, expectedUseStylusMode: Boolean) {
        val intentCaptor = argumentCaptor<Intent>()
        val userCaptor = argumentCaptor<UserHandle>()
        verify(context).startActivityAsUser(intentCaptor.capture(), userCaptor.capture())
        assertThat(intentCaptor.lastValue).run {
            hasAction(ACTION_CREATE_NOTE)
            hasPackage(NOTE_TASK_PACKAGE_NAME)
            hasFlags(FLAG_ACTIVITY_NEW_TASK)
            hasFlags(FLAG_ACTIVITY_MULTIPLE_TASK)
            hasFlags(FLAG_ACTIVITY_NEW_DOCUMENT)
            extras().bool(EXTRA_USE_STYLUS_MODE).isEqualTo(expectedUseStylusMode)
        }
        assertThat(userCaptor.lastValue).isEqualTo(user)
    }

    private fun verifyNoteTaskOpenInBubbleInUser(userHandle: UserHandle) {
        val intentCaptor = argumentCaptor<Intent>()
        val iconCaptor = argumentCaptor<Icon>()
        verify(bubbles)
            .showOrHideNoteBubble(intentCaptor.capture(), eq(userHandle), iconCaptor.capture())
        assertThat(intentCaptor.lastValue).run {
            hasAction(ACTION_CREATE_NOTE)
            hasPackage(NOTE_TASK_PACKAGE_NAME)
            hasFlags(FLAG_ACTIVITY_NEW_TASK)
            extras().bool(EXTRA_USE_STYLUS_MODE).isTrue()
        }
        iconCaptor.lastValue?.let { icon ->
            assertNotNull(icon)
            assertThat(icon.resId).isEqualTo(R.drawable.ic_note_task_shortcut_widget)
        }
    }

    // region onRoleHoldersChanged
    @Test
    fun onRoleHoldersChanged_notNotesRole_doNothing() {
        val user = UserHandle.of(0)

        createNoteTaskController(isEnabled = true).onRoleHoldersChanged("NOT_NOTES", user)

        verify(context, never()).startActivityAsUser(any(), any())
    }

    @Test
    fun onRoleHoldersChanged_notesRole_shouldUpdateShortcuts() {
        val user = userTracker.userHandle
        val controller = spy(createNoteTaskController())
        doNothing().whenever(controller).updateNoteTaskAsUser(any())

        controller.onRoleHoldersChanged(ROLE_NOTES, user)

        verify(controller).updateNoteTaskAsUser(user)
    }

    // endregion

    // region updateNoteTaskAsUser
    @Test
    fun updateNoteTaskAsUser_sameUser_shouldUpdateShortcuts() {
        val user = UserHandle.CURRENT
        val controller = spy(createNoteTaskController())
        doNothing().whenever(controller).launchUpdateNoteTaskAsUser(any())
        whenever(controller.getCurrentRunningUser()).thenReturn(user)

        controller.updateNoteTaskAsUser(user)

        verify(controller).launchUpdateNoteTaskAsUser(user)
        verify(context, never()).startServiceAsUser(any(), any())
    }

    @Test
    fun updateNoteTaskAsUser_differentUser_shouldUpdateShortcutsInUserProcess() {
        val user = UserHandle.CURRENT
        val controller = spy(createNoteTaskController(isEnabled = true))
        doNothing().whenever(controller).launchUpdateNoteTaskAsUser(any())
        whenever(controller.getCurrentRunningUser()).thenReturn(UserHandle.SYSTEM)

        controller.updateNoteTaskAsUser(user)

        verify(controller, never()).launchUpdateNoteTaskAsUser(any())
        val intentCaptor = argumentCaptor<Intent>()
        verify(context).startServiceAsUser(intentCaptor.capture(), eq(user))
        assertThat(intentCaptor.lastValue)
            .hasComponentClass(NoteTaskControllerUpdateService::class.java)
    }

    // endregion

    // region internalUpdateNoteTaskAsUser
    @Test
    @Ignore("b/316332684")
    fun updateNoteTaskAsUserInternal_withNotesRole_withShortcuts_shouldUpdateShortcuts() {
        createNoteTaskController(isEnabled = true)
            .launchUpdateNoteTaskAsUser(userTracker.userHandle)
        testScope.runCurrent()

        val actualComponent = argumentCaptor<ComponentName>()
        verify(context.packageManager)
            .setComponentEnabledSetting(
                actualComponent.capture(),
                eq(COMPONENT_ENABLED_STATE_ENABLED),
                eq(PackageManager.DONT_KILL_APP),
            )
        assertThat(actualComponent.lastValue.className)
            .isEqualTo(CreateNoteTaskShortcutActivity::class.java.name)
        verify(shortcutManager, never()).disableShortcuts(any())
        verify(shortcutManager).enableShortcuts(listOf(SHORTCUT_ID))
        val shortcutInfoCaptor = argumentCaptor<List<ShortcutInfo>>()
        verify(shortcutManager).updateShortcuts(shortcutInfoCaptor.capture())
        with(shortcutInfoCaptor.lastValue.first()) {
            assertThat(id).isEqualTo(SHORTCUT_ID)
            assertThat(intent).run {
                hasComponentClass(LaunchNoteTaskActivity::class.java)
                hasAction(ACTION_CREATE_NOTE)
            }
            assertThat(shortLabel).isEqualTo(NOTE_TASK_SHORT_LABEL)
            assertThat(longLabel).isEqualTo(NOTE_TASK_LONG_LABEL)
            assertThat(isLongLived).isEqualTo(true)
            assertThat(icon?.resId).isEqualTo(R.drawable.ic_note_task_shortcut_widget)
            assertThat(extras?.getString(EXTRA_SHORTCUT_BADGE_OVERRIDE_PACKAGE))
                .isEqualTo(NOTE_TASK_PACKAGE_NAME)
        }
    }

    @Test
    @Ignore("b/316332684")
    fun updateNoteTaskAsUserInternal_noNotesRole_shouldDisableShortcuts() {
        whenever(roleManager.getRoleHoldersAsUser(ROLE_NOTES, userTracker.userHandle))
            .thenReturn(emptyList())

        createNoteTaskController(isEnabled = true)
            .launchUpdateNoteTaskAsUser(userTracker.userHandle)
        testScope.runCurrent()

        val argument = argumentCaptor<ComponentName>()
        verify(context.packageManager)
            .setComponentEnabledSetting(
                argument.capture(),
                eq(COMPONENT_ENABLED_STATE_DISABLED),
                eq(PackageManager.DONT_KILL_APP),
            )
        assertThat(argument.lastValue.className)
            .isEqualTo(CreateNoteTaskShortcutActivity::class.java.name)
        verify(shortcutManager).disableShortcuts(listOf(SHORTCUT_ID))
        verify(shortcutManager, never()).enableShortcuts(any())
        verify(shortcutManager, never()).updateShortcuts(any())
    }

    @Test
    @Ignore("b/316332684")
    fun updateNoteTaskAsUserInternal_flagDisabled_shouldDisableShortcuts() {
        createNoteTaskController(isEnabled = false)
            .launchUpdateNoteTaskAsUser(userTracker.userHandle)
        testScope.runCurrent()

        val argument = argumentCaptor<ComponentName>()
        verify(context.packageManager)
            .setComponentEnabledSetting(
                argument.capture(),
                eq(COMPONENT_ENABLED_STATE_DISABLED),
                eq(PackageManager.DONT_KILL_APP),
            )
        assertThat(argument.lastValue.className)
            .isEqualTo(CreateNoteTaskShortcutActivity::class.java.name)
        verify(shortcutManager).disableShortcuts(listOf(SHORTCUT_ID))
        verify(shortcutManager, never()).enableShortcuts(any())
        verify(shortcutManager, never()).updateShortcuts(any())
    }

    // endregion

    // startregion updateNoteTaskForAllUsers
    @Test
    fun updateNoteTaskForAllUsers_shouldRunUpdateForCurrentUserAndProfiles() {
        userTracker.set(mainAndWorkProfileUsers, mainAndWorkProfileUsers.indexOf(mainUserInfo))
        val controller = spy(createNoteTaskController())
        doNothing().whenever(controller).updateNoteTaskAsUser(any())

        controller.updateNoteTaskForCurrentUserAndManagedProfiles()

        verify(controller).updateNoteTaskAsUser(mainUserInfo.userHandle)
        verify(controller).updateNoteTaskAsUser(workUserInfo.userHandle)
    }

    // endregion

    // region startNotesRoleSetting
    @Test
    fun startNotesRoleSetting_withEntryPoint_shouldUseUserResolver() = runTest {
        val expectedUser = UserHandle.of(100)
        whenever(userResolver.getUserForHandlingNoteTaking(QUICK_AFFORDANCE))
            .thenReturn(expectedUser)

        createNoteTaskController().startNotesRoleSetting(context, QUICK_AFFORDANCE)

        val userCaptor = argumentCaptor<UserHandle>()
        verify(context).startActivityAsUser(any(), userCaptor.capture())
        assertThat(userCaptor.lastValue).isEqualTo(expectedUser)
    }

    @Test
    fun startNotesRoleSetting_withoutEntryPoint_shouldUseCurrentUser() = runTest {
        val expectedUser = userTracker.userHandle

        createNoteTaskController().startNotesRoleSetting(context, entryPoint = null)

        val userCaptor = argumentCaptor<UserHandle>()
        verify(context).startActivityAsUser(any(), userCaptor.capture())
        assertThat(userCaptor.lastValue).isEqualTo(expectedUser)
    }

    // endregion

    private companion object {
        const val NOTE_TASK_SHORT_LABEL = "Note-taking"
        const val NOTE_TASK_LONG_LABEL = "Note-taking, App"
        const val NOTE_TASK_ACTIVITY_NAME = "NoteTaskActivity"
        const val NOTE_TASK_PACKAGE_NAME = "com.android.note.app"
        const val NOTE_TASK_UID = 123456

        private val NOTE_TASK_INFO =
            NoteTaskInfo(
                packageName = NOTE_TASK_PACKAGE_NAME,
                uid = NOTE_TASK_UID,
                user = UserHandle.of(0),
            )
        private val NOTE_RUNNING_TASK_INFO =
            ActivityManager.RunningTaskInfo().apply {
                topActivity = ComponentName(NOTE_TASK_PACKAGE_NAME, NOTE_TASK_ACTIVITY_NAME)
            }

        val mainUserInfo =
            UserInfo(/* id= */ 0, /* name= */ "primary", /* flags= */ UserInfo.FLAG_MAIN)
        val workUserInfo =
            UserInfo(/* id= */ 10, /* name= */ "work", /* flags= */ UserInfo.FLAG_PROFILE)
        val mainAndWorkProfileUsers = listOf(mainUserInfo, workUserInfo)
    }
}
