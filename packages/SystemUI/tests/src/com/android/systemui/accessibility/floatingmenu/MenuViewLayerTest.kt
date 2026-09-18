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

package com.android.systemui.accessibility.floatingmenu

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Insets
import android.graphics.PointF
import android.graphics.Rect
import android.os.UserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.provider.Settings
import android.testing.TestableLooper
import android.util.ArraySet
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.WindowInsets
import android.view.WindowInsets.Type.displayCutout
import android.view.WindowInsets.Type.ime
import android.view.WindowInsets.Type.systemBars
import android.view.WindowMetrics
import android.view.accessibility.accessibilityManager
import android.view.windowManager
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.accessibility.common.ShortcutConstants
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType.SOFTWARE
import com.android.internal.accessibility.dialog.AccessibilityTarget
import com.android.internal.messages.nano.SystemMessageProto
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.android.systemui.SysuiTestableContext
import com.android.systemui.accessibility.floatingmenu.MenuNotificationFactory.ACTION_DELETE
import com.android.systemui.accessibility.floatingmenu.MenuNotificationFactory.ACTION_UNDO
import com.android.systemui.accessibility.floatingmenu.MenuViewLayer.LayerIndex
import com.android.systemui.accessibility.utils.TestUtils
import com.android.systemui.navigationbar.NavigationModeController
import com.android.systemui.res.R
import com.android.systemui.testKosmosNew
import com.android.systemui.util.settings.SecureSettings
import com.android.wm.shell.shared.magnetictarget.MagnetizedObject
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests for [MenuViewLayer]. */
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
class MenuViewLayerTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    @get:Rule val setFlagsRule = SetFlagsRule()

    private val spyContext: SysuiTestableContext = spy(context)
    private val floatingMenu = mock<IAccessibilityFloatingMenu>()
    private val mockPackageManager = mock<PackageManager>()
    private val mockMoreOptionsView = mock<View>()
    private val mockMoreOptionsPopup = mock<MoreOptionsPopup>()

    private val secureSettings: SecureSettings = TestUtils.mockSecureSettings(mContext)
    private val mockNotificationManager = mock<NotificationManager>()

    private var lastAccessibilityButtonTargets: String? = null
    private var lastEnabledAccessibilityServices: String? = null
    private lateinit var windowMetrics: WindowMetrics
    private lateinit var menuView: MenuView
    private lateinit var menuAnimationController: MenuAnimationController
    private lateinit var underTest: MenuViewLayer

    @Before
    fun setUp() {
        spyContext.addMockSystemService(Context.NOTIFICATION_SERVICE, mockNotificationManager)
        whenever(spyContext.packageManager).thenReturn(mockPackageManager)

        val displayBounds = Rect(0, 0, DISPLAY_WINDOW_WIDTH, DISPLAY_WINDOW_HEIGHT)
        windowMetrics = spy(WindowMetrics(displayBounds, fakeDisplayInsets(), 0f))
        whenever(kosmos.windowManager.currentWindowMetrics).thenReturn(windowMetrics)

        menuView = spy(kosmos.menuView) { on { incrementTexMetric(any()) } doAnswer {} }
        menuAnimationController = spy(menuView.menuAnimationController)
        doReturn(menuAnimationController).whenever(menuView).menuAnimationController

        underTest =
            spy(
                MenuViewLayer(
                    spyContext,
                    kosmos.windowManager,
                    kosmos.accessibilityManager,
                    kosmos.menuViewModel,
                    kosmos.menuViewAppearance,
                    menuView,
                    floatingMenu,
                    secureSettings,
                    mock<NavigationModeController>(),
                )
            )
        // doReturn avoids problems when trying to stub a spy via other methods
        doReturn(mockMoreOptionsPopup).whenever(underTest).createMoreOptionsPopup(any(), any())

        lastAccessibilityButtonTargets =
            Settings.Secure.getStringForUser(
                spyContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_BUTTON_TARGETS,
                UserHandle.USER_CURRENT,
            )
        lastEnabledAccessibilityServices =
            Settings.Secure.getStringForUser(
                spyContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                UserHandle.USER_CURRENT,
            )

        underTest.onAttachedToWindow()

        Settings.Secure.putStringForUser(
            spyContext.contentResolver,
            Settings.Secure.ACCESSIBILITY_BUTTON_TARGETS,
            "",
            UserHandle.USER_CURRENT,
        )
        Settings.Secure.putStringForUser(
            spyContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "",
            UserHandle.USER_CURRENT,
        )
    }

    private fun showMoreOptionsPopup(): MoreOptionsPopup.OnItemClickListener {
        clearInvocations(underTest)
        clearInvocations(mockMoreOptionsPopup)
        val onItemClickListenerCaptor = argumentCaptor<MoreOptionsPopup.OnItemClickListener>()
        underTest.onMoreOptionsClicked(mockMoreOptionsView)
        verify(underTest).createMoreOptionsPopup(any(), onItemClickListenerCaptor.capture())
        verify(mockMoreOptionsPopup).show(mockMoreOptionsView)
        return onItemClickListenerCaptor.firstValue
    }

    @After
    fun tearDown() {
        Settings.Secure.putStringForUser(
            spyContext.contentResolver,
            Settings.Secure.ACCESSIBILITY_BUTTON_TARGETS,
            lastAccessibilityButtonTargets,
            UserHandle.USER_CURRENT,
        )
        Settings.Secure.putStringForUser(
            spyContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            lastEnabledAccessibilityServices,
            UserHandle.USER_CURRENT,
        )

        menuView.updateMenuMoveToTucked(false)
        menuAnimationController.mPositionAnimations.values.forEach { it.cancel() }
        underTest.onDetachedFromWindow()
    }

    @Test
    fun onAttachedToWindow_menuIsVisible() {
        underTest.onAttachedToWindow()

        val menuView = underTest.getChildAt(LayerIndex.MENU_VIEW)
        assertThat(menuView.visibility).isEqualTo(VISIBLE)
    }

    @Test
    fun onDetachedFromWindow_menuIsGone() {
        underTest.onDetachedFromWindow()

        val menuView = underTest.getChildAt(LayerIndex.MENU_VIEW)
        assertThat(menuView.visibility).isEqualTo(GONE)
    }

    @Test
    fun triggerDismissMenuAction_hideFloatingMenu() {
        underTest.mDismissMenuAction.run()

        verify(floatingMenu).hide()
    }

    @SuppressLint("MissingPermission")
    @Test
    fun triggerDismissMenuAction_callsA11yManagerEnableShortcutsForTargets() {
        val stubShortcutTargets = arrayListOf(TEST_SELECT_TO_SPEAK_COMPONENT_NAME.flattenToString())
        whenever(kosmos.accessibilityManager.getAccessibilityShortcutTargets(SOFTWARE))
            .thenReturn(stubShortcutTargets)

        underTest.mDismissMenuAction.run()

        verify(kosmos.accessibilityManager)
            .enableShortcutsForTargets(
                false,
                SOFTWARE,
                ArraySet(stubShortcutTargets),
                secureSettings.getRealUserHandle(UserHandle.USER_CURRENT),
            )
    }

    @SuppressLint("MissingPermission")
    @Test
    fun onEditAction_startsActivity() {
        mockActivityQuery(true)
        underTest.dispatchAccessibilityAction(R.id.action_edit)

        val intentCaptor = argumentCaptor<Intent>()
        verify(spyContext).startActivityAsUser(intentCaptor.capture(), eq(UserHandle.CURRENT))
        assertThat(intentCaptor.firstValue.action).isEqualTo(underTest.intentForEditScreen.action)
    }

    @SuppressLint("MissingPermission")
    @Test
    fun onEditAction_noResolve_doesNotStart() {
        mockActivityQuery(false)
        underTest.dispatchAccessibilityAction(R.id.action_edit)

        verify(spyContext, never()).startActivity(any())
        verify(spyContext, never()).startActivityAsUser(any(), any())
    }

    @Test
    fun getIntentForEditScreen_validate() {
        val intent = underTest.intentForEditScreen
        val targets =
            intent.getBundleExtra(":settings:show_fragment_args")?.getStringArray("targets")

        assertThat(intent.action).isEqualTo(Settings.ACTION_ACCESSIBILITY_SHORTCUT_SETTINGS)
        assertThat(targets).asList().containsExactlyElementsIn(TestUtils.TEST_BUTTON_TARGETS)
    }

    @Test
    @EnableFlags(Flags.FLAG_FLOATING_MENU_DRAG_TO_HIDE)
    fun onDismissAction_hideMenuAndShowNotification() {
        underTest.dispatchAccessibilityAction(R.id.action_remove_menu)
        verify(underTest).hideMenuAndShowNotification()
    }

    @Test
    @DisableFlags(Flags.FLAG_FLOATING_MENU_DRAG_TO_HIDE)
    fun onDismissAction_hideMenuAndShowMessage() {
        underTest.dispatchAccessibilityAction(R.id.action_remove_menu)
        verify(underTest).hideMenuAndShowMessage()
    }

    @Test
    fun showingImeInsetsChange_notOverlapOnIme_menuKeepOriginalPosition() {
        val menuTop = STATUS_BAR_HEIGHT + 100f
        menuAnimationController.moveAndPersistPosition(PointF(0f, menuTop))

        dispatchImeInsets(isVisible = true)

        assertThat(menuView.translationX).isEqualTo(0f)
        assertThat(menuView.translationY).isEqualTo(menuTop)
    }

    @Test
    fun showingImeInsetsChange_overlapOnIme_menuShownAboveIme() {
        menuAnimationController.moveAndPersistPosition(PointF(0f, IME_TOP + 100f))
        val beforePosition = menuView.menuPosition

        dispatchImeInsets(isVisible = true)
        assertThat(isPositionAnimationRunning).isTrue()
        skipPositionAnimations()

        val menuBottom = menuView.translationY + menuView.menuHeight

        assertThat(menuView.translationX).isEqualTo(beforePosition.x)
        assertThat(menuBottom).isAtMost(IME_TOP.toFloat())
    }

    @Test
    fun hidingImeInsetsChange_overlapOnIme_menuBackToOriginalPosition() {
        menuAnimationController.moveAndPersistPosition(PointF(0f, IME_TOP + 200f))
        val beforePosition = menuView.menuPosition

        dispatchImeInsets(isVisible = true)
        assertThat(isPositionAnimationRunning).isTrue()
        skipPositionAnimations()

        dispatchImeInsets(isVisible = false)
        assertThat(isPositionAnimationRunning).isTrue()
        skipPositionAnimations()

        assertThat(menuView.translationX).isEqualTo(beforePosition.x)
        assertThat(menuView.translationY).isEqualTo(beforePosition.y)
    }

    @Test
    @EnableFlags(Flags.FLAG_FLOATING_MENU_DRAG_TO_HIDE)
    fun onReleasedInTarget_hideMenuAndShowNotificationWithExpectedActions() {
        dragMenuThenReleasedInTarget(R.id.action_remove_menu)

        verify(mockNotificationManager)
            .notify(eq(SystemMessageProto.SystemMessage.NOTE_A11Y_FLOATING_MENU_HIDDEN), any())
        verify(spyContext)
            .registerReceiver(
                any(),
                argThat<IntentFilter> { f ->
                    f.hasAction(ACTION_UNDO) && f.hasAction(ACTION_DELETE)
                },
                any(),
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_FLOATING_MENU_DRAG_TO_HIDE)
    fun receiveActionUndo_dismissNotificationAndMenuVisible() {
        val broadcastReceiverCaptor = argumentCaptor<BroadcastReceiver>()
        dragMenuThenReleasedInTarget(R.id.action_remove_menu)

        verify(spyContext)
            .registerReceiver(
                broadcastReceiverCaptor.capture(),
                argThat<IntentFilter> { f ->
                    f.hasAction(ACTION_UNDO) && f.hasAction(ACTION_DELETE)
                },
                any(),
            )
        broadcastReceiverCaptor.firstValue.onReceive(spyContext, Intent(ACTION_UNDO))

        verify(spyContext).unregisterReceiver(broadcastReceiverCaptor.firstValue)
        verify(mockNotificationManager)
            .cancel(SystemMessageProto.SystemMessage.NOTE_A11Y_FLOATING_MENU_HIDDEN)
        assertThat(menuView.visibility).isEqualTo(VISIBLE)
    }

    @Test
    @EnableFlags(Flags.FLAG_FLOATING_MENU_DRAG_TO_HIDE)
    fun receiveActionDelete_dismissNotificationAndHideMenu() {
        val broadcastReceiverCaptor = argumentCaptor<BroadcastReceiver>()
        dragMenuThenReleasedInTarget(R.id.action_remove_menu)

        verify(spyContext)
            .registerReceiver(
                broadcastReceiverCaptor.capture(),
                argThat<IntentFilter> { f ->
                    f.hasAction(ACTION_UNDO) && f.hasAction(ACTION_DELETE)
                },
                any(),
            )
        broadcastReceiverCaptor.firstValue.onReceive(spyContext, Intent(ACTION_DELETE))

        verify(spyContext).unregisterReceiver(broadcastReceiverCaptor.firstValue)
        verify(mockNotificationManager)
            .cancel(SystemMessageProto.SystemMessage.NOTE_A11Y_FLOATING_MENU_HIDDEN)
        verify(floatingMenu).hide()
    }

    @Test
    fun onDismissAction_incrementsTexMetricDismiss() {
        val testTargets =
            arrayListOf(
                TestAccessibilityTarget(spyContext, 1234),
                TestAccessibilityTarget(spyContext, 5678),
            )
        kosmos.menuViewModel.onTargetFeaturesChanged(testTargets as List<AccessibilityTarget>)

        underTest.dispatchAccessibilityAction(R.id.action_remove_menu)

        verify(menuView, times(1)).incrementTexMetric(eq(MenuViewLayer.TEX_METRIC_DISMISS))
    }

    @Test
    fun onEditAction_incrementsTexMetricEdit() {
        val testTargets =
            arrayListOf(
                TestAccessibilityTarget(spyContext, 1234),
                TestAccessibilityTarget(spyContext, 5678),
            )
        kosmos.menuViewModel.onTargetFeaturesChanged(testTargets as List<AccessibilityTarget>)

        underTest.dispatchAccessibilityAction(R.id.action_edit)

        verify(menuView, times(1)).incrementTexMetric(eq(MenuViewLayer.TEX_METRIC_EDIT))
    }

    @Test
    fun onMoveToTuckedChanged_updatesDockTooltipVisibility() {
        kosmos.menuViewModel.updateDockTooltipVisibility(false)

        menuView.updateMenuMoveToTucked(true)

        assertThat(kosmos.menuViewModel.dockTooltipVisibilityData.value).isTrue()
    }

    @Test
    fun onMoreOptionsClicked_edit_dispatchesEditAction() {
        showMoreOptionsPopup().onEditClicked()
        verify(underTest).gotoEditScreen()
    }

    @Test
    fun onMoreOptionsClicked_removeAll_dismissesMenu() {
        showMoreOptionsPopup().onRemoveAllClicked()
        verify(floatingMenu).hide()
    }

    @Test
    fun onMoreOptionsClicked_move_cyclesPositionInSequence() {
        clearInvocations(menuAnimationController)

        showMoreOptionsPopup().onMoveClicked()
        verify(menuAnimationController).moveToBottomLeftPosition()
        clearInvocations(menuAnimationController)

        showMoreOptionsPopup().onMoveClicked()
        verify(menuAnimationController).moveToTopLeftPosition()
        clearInvocations(menuAnimationController)

        showMoreOptionsPopup().onMoveClicked()
        verify(menuAnimationController).moveToTopRightPosition()
        clearInvocations(menuAnimationController)

        showMoreOptionsPopup().onMoveClicked()
        verify(menuAnimationController).moveToBottomRightPosition()
        clearInvocations(menuAnimationController)

        showMoreOptionsPopup().onMoveClicked()
        verify(menuAnimationController).moveToBottomLeftPosition()
    }

    @Test
    fun onMoreOptionsClicked_atBottomRight_passesBottomLeftHint() {
        val hintCaptor = argumentCaptor<String>()
        underTest.onMoreOptionsClicked(mockMoreOptionsView)
        verify(underTest).createMoreOptionsPopup(hintCaptor.capture(), any())
        assertThat(hintCaptor.firstValue)
            .isEqualTo(
                spyContext.getString(R.string.accessibility_floating_button_action_move_bottom_left)
            )
    }

    @Test
    fun onMoreOptionsClicked_atBottomLeft_passesTopLeftHint() {
        kosmos.menuViewModel.cycleMenuPosition()
        val hintCaptor = argumentCaptor<String>()
        underTest.onMoreOptionsClicked(mockMoreOptionsView)
        verify(underTest).createMoreOptionsPopup(hintCaptor.capture(), any())
        assertThat(hintCaptor.firstValue)
            .isEqualTo(
                spyContext.getString(R.string.accessibility_floating_button_action_move_top_left)
            )
    }

    @Test
    fun onMoreOptionsClicked_atTopLeft_passesTopRightHint() {
        kosmos.menuViewModel.cycleMenuPosition()
        kosmos.menuViewModel.cycleMenuPosition()
        val hintCaptor = argumentCaptor<String>()
        underTest.onMoreOptionsClicked(mockMoreOptionsView)
        verify(underTest).createMoreOptionsPopup(hintCaptor.capture(), any())
        assertThat(hintCaptor.firstValue)
            .isEqualTo(
                spyContext.getString(R.string.accessibility_floating_button_action_move_top_right)
            )
    }

    @Test
    fun onMoreOptionsClicked_atTopRight_passesBottomRightHint() {
        kosmos.menuViewModel.cycleMenuPosition()
        kosmos.menuViewModel.cycleMenuPosition()
        kosmos.menuViewModel.cycleMenuPosition()
        val hintCaptor = argumentCaptor<String>()
        underTest.onMoreOptionsClicked(mockMoreOptionsView)
        verify(underTest).createMoreOptionsPopup(hintCaptor.capture(), any())
        assertThat(hintCaptor.firstValue)
            .isEqualTo(
                spyContext.getString(
                    R.string.accessibility_floating_button_action_move_bottom_right
                )
            )
    }

    /** Simplified AccessibilityTarget for testing MenuViewLayer. */
    private class TestAccessibilityTarget(context: Context, uid: Int) :
        AccessibilityTarget(
            context,
            SOFTWARE,
            ShortcutConstants.AccessibilityFragmentType.VOLUME_SHORTCUT_TOGGLE,
            false,
            TEST_SELECT_TO_SPEAK_COMPONENT_NAME.flattenToString(),
            uid,
            null,
            null,
            null,
        )

    private fun dispatchImeInsets(isVisible: Boolean) {
        val fakeImeInsets = fakeImeInsets(isVisible)
        whenever(windowMetrics.windowInsets).doReturn(fakeImeInsets)
        underTest.dispatchApplyWindowInsets(fakeImeInsets)
    }

    private fun fakeDisplayInsets(): WindowInsets {
        return WindowInsets.Builder()
            .setVisible(systemBars() or displayCutout(), true)
            .setInsets(
                systemBars() or displayCutout(),
                Insets.of(0, STATUS_BAR_HEIGHT, 0, NAVIGATION_BAR_HEIGHT),
            )
            .build()
    }

    private fun fakeImeInsets(isImeVisible: Boolean): WindowInsets {
        val bottom = if (isImeVisible) IME_HEIGHT + NAVIGATION_BAR_HEIGHT else 0
        return WindowInsets.Builder()
            .setVisible(ime(), isImeVisible)
            .setInsets(ime(), Insets.of(0, 0, 0, bottom))
            .build()
    }

    private val isPositionAnimationRunning: Boolean
        get() = menuAnimationController.mPositionAnimations.values.any { it.isRunning }

    private fun skipPositionAnimations() {
        menuAnimationController.mPositionAnimations.values.forEach { animation ->
            val springAnimation = animation as SpringAnimation
            springAnimation.doAnimationFrame(500)
            springAnimation.skipToEnd()
            springAnimation.doAnimationFrame(500)
        }
    }

    private fun dragMenuThenReleasedInTarget(id: Int) {
        val magnetListener = underTest.dragToInteractAnimationController.getMagnetListener(id)
        val view = mock<View>()
        whenever(view.id).thenReturn(id)
        magnetListener.onReleasedInTarget(
            MagnetizedObject.MagneticTarget(view, 200),
            mock<MagnetizedObject<MenuView>>(),
        )
    }

    private fun mockActivityQuery(successfulQuery: Boolean) {
        val resolveInfos = if (successfulQuery) arrayListOf(ResolveInfo()) else arrayListOf()
        whenever(
                mockPackageManager.queryIntentActivities(
                    any(),
                    any<PackageManager.ResolveInfoFlags>(),
                )
            )
            .thenReturn(resolveInfos)
    }

    @Test
    fun onAttachedToWindow_initialState_doesNotTriggerSnapToCorner() {
        verify(menuAnimationController, never()).moveToTopLeftPosition()
        verify(menuAnimationController, never()).moveToTopRightPosition()
        verify(menuAnimationController, never()).moveToBottomLeftPosition()
        verify(menuAnimationController, never()).moveToBottomRightPosition()
    }

    @Test
    fun cycleThenDragThenAttach_staysAtDragPosition() {
        kosmos.menuViewModel.cycleMenuPosition()
        verify(menuAnimationController).moveToBottomLeftPosition()
        clearInvocations(menuAnimationController)

        val dragPosition = PointF(500f, 500f)
        menuAnimationController.moveAndPersistPosition(dragPosition)

        underTest.onDetachedFromWindow()
        underTest.onAttachedToWindow()

        verify(menuAnimationController, never()).moveToTopLeftPosition()
        verify(menuAnimationController, never()).moveToTopRightPosition()
        verify(menuAnimationController, never()).moveToBottomLeftPosition()
        verify(menuAnimationController, never()).moveToBottomRightPosition()
    }

    companion object {
        private const val SELECT_TO_SPEAK_PACKAGE_NAME = "com.google.android.marvin.talkback"
        private const val SELECT_TO_SPEAK_SERVICE_NAME =
            "com.google.android.accessibility.selecttospeak.SelectToSpeakService"
        private val TEST_SELECT_TO_SPEAK_COMPONENT_NAME =
            ComponentName(SELECT_TO_SPEAK_PACKAGE_NAME, SELECT_TO_SPEAK_SERVICE_NAME)

        private const val DISPLAY_WINDOW_WIDTH = 1080
        private const val DISPLAY_WINDOW_HEIGHT = 2340
        private const val STATUS_BAR_HEIGHT = 75
        private const val NAVIGATION_BAR_HEIGHT = 125
        private const val IME_HEIGHT = 350
        private const val IME_TOP =
            DISPLAY_WINDOW_HEIGHT - STATUS_BAR_HEIGHT - NAVIGATION_BAR_HEIGHT - IME_HEIGHT
    }
}
