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

import android.graphics.Rect
import android.testing.TestableLooper
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerViewAccessibilityDelegate
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.bluetooth.HearingAidDeviceManager
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.Magnification
import com.android.systemui.accessibility.utils.TestUtils
import com.android.systemui.inputdevice.data.repository.pointerDeviceRepository
import com.android.systemui.keyboard.data.repository.keyboardRepository
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionInteractor
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.testKosmosNew
import com.android.systemui.util.settings.SecureSettings
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify

/** Tests for [MenuItemAccessibilityDelegate]. */
@SmallTest
@TestableLooper.RunWithLooper
@RunWith(AndroidJUnit4::class)
class MenuItemAccessibilityDelegateTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private val accessibilityManager = mock<AccessibilityManager>()
    private val hearingAidDeviceManager = mock<HearingAidDeviceManager>()
    private val secureSettings: SecureSettings = TestUtils.mockSecureSettings(mContext)
    private lateinit var stubListView: RecyclerView
    private lateinit var menuView: MenuView
    private val menuViewLayer =
        mock<MenuViewLayer> {
            on { gotoEditScreen() } doAnswer {}
            on { dispatchAccessibilityAction(any()) } doAnswer {}
        }
    private lateinit var menuItemAccessibilityDelegate: MenuItemAccessibilityDelegate
    private lateinit var menuAnimationController: MenuAnimationController
    private val draggableBounds = Rect(100, 200, 300, 400)

    @Before
    fun setUp() {
        val stubWindowManager = mContext.getSystemService(WindowManager::class.java)
        val stubMenuViewAppearance = MenuViewAppearance(mContext, stubWindowManager)
        val stubMenuViewModel =
            MenuViewModel(
                mContext,
                accessibilityManager,
                secureSettings,
                hearingAidDeviceManager,
                kosmos.keyboardRepository,
                kosmos.pointerDeviceRepository,
                kosmos.keyguardTransitionInteractor,
                kosmos.sceneInteractor,
            )

        val halfScreenHeight = stubWindowManager!!.currentWindowMetrics.bounds.height() / 2
        menuView =
            spy(
                MenuView(
                    mContext,
                    stubMenuViewModel,
                    stubMenuViewAppearance,
                    secureSettings,
                    mock<Magnification>(),
                )
            ) {
                on { menuDraggableBounds } doReturn draggableBounds
            }
        menuView.translationY = halfScreenHeight.toFloat()
        stubListView = RecyclerView(mContext)
        menuAnimationController = spy(MenuAnimationController(menuView, stubMenuViewAppearance))
        menuItemAccessibilityDelegate =
            MenuItemAccessibilityDelegate(
                RecyclerViewAccessibilityDelegate(stubListView),
                menuAnimationController,
                menuViewLayer,
            )
    }

    @Test
    fun getAccessibilityActionList_matchSize() {
        val info = AccessibilityNodeInfoCompat(AccessibilityNodeInfo())

        menuItemAccessibilityDelegate.onInitializeAccessibilityNodeInfo(stubListView, info)

        assertThat(info.getActionList().size).isEqualTo(7)
    }

    @Test
    fun performMoveTopLeftAction_matchPosition() {
        val moveTopLeftAction =
            menuItemAccessibilityDelegate.performAccessibilityAction(
                stubListView,
                R.id.action_move_top_left,
                null,
            )

        assertThat(moveTopLeftAction).isTrue()
        assertThat(menuView.translationX).isEqualTo(draggableBounds.left)
        assertThat(menuView.translationY).isEqualTo(draggableBounds.top)
    }

    @Test
    fun performMoveTopRightAction_matchPosition() {
        val moveTopRightAction =
            menuItemAccessibilityDelegate.performAccessibilityAction(
                stubListView,
                R.id.action_move_top_right,
                null,
            )

        assertThat(moveTopRightAction).isTrue()
        assertThat(menuView.translationX).isEqualTo(draggableBounds.right)
        assertThat(menuView.translationY).isEqualTo(draggableBounds.top)
    }

    @Test
    fun performMoveBottomLeftAction_matchPosition() {
        val moveBottomLeftAction =
            menuItemAccessibilityDelegate.performAccessibilityAction(
                stubListView,
                R.id.action_move_bottom_left,
                null,
            )

        assertThat(moveBottomLeftAction).isTrue()
        assertThat(menuView.translationX).isEqualTo(draggableBounds.left)
        assertThat(menuView.translationY).isEqualTo(draggableBounds.bottom)
    }

    @Test
    fun performMoveBottomRightAction_matchPosition() {
        val moveBottomRightAction =
            menuItemAccessibilityDelegate.performAccessibilityAction(
                stubListView,
                R.id.action_move_bottom_right,
                null,
            )

        assertThat(moveBottomRightAction).isTrue()
        assertThat(menuView.translationX).isEqualTo(draggableBounds.right)
        assertThat(menuView.translationY).isEqualTo(draggableBounds.bottom)
    }

    @Test
    fun performMoveToEdgeAndHideAction_success() {
        val moveToEdgeAndHideAction =
            menuItemAccessibilityDelegate.performAccessibilityAction(
                stubListView,
                R.id.action_move_to_edge_and_hide,
                null,
            )

        assertThat(moveToEdgeAndHideAction).isTrue()
        verify(menuAnimationController).moveToEdgeAndHide()
    }

    @Test
    fun performMoveOutFromEdgeAction_success() {
        val moveOutEdgeAndShowAction =
            menuItemAccessibilityDelegate.performAccessibilityAction(
                stubListView,
                R.id.action_move_out_edge_and_show,
                null,
            )

        assertThat(moveOutEdgeAndShowAction).isTrue()
        verify(menuAnimationController).moveOutEdgeAndShow()
    }

    @Test
    fun performRemoveMenuAction_success() {
        val removeMenuAction =
            menuItemAccessibilityDelegate.performAccessibilityAction(
                stubListView,
                R.id.action_remove_menu,
                null,
            )

        assertThat(removeMenuAction).isTrue()
        verify(menuViewLayer).dispatchAccessibilityAction(R.id.action_remove_menu)
    }

    @Test
    fun performEditAction_success() {
        val editAction =
            menuItemAccessibilityDelegate.performAccessibilityAction(
                stubListView,
                R.id.action_edit,
                null,
            )

        assertThat(editAction).isTrue()
        verify(menuViewLayer).dispatchAccessibilityAction(R.id.action_edit)
    }

    @Test
    fun performFocusAction_fadeIn() {
        menuItemAccessibilityDelegate.performAccessibilityAction(
            stubListView,
            AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS,
            null,
        )

        verify(menuAnimationController).fadeInNowIfEnabled()
    }

    @Test
    fun performClearFocusAction_fadeOut() {
        menuItemAccessibilityDelegate.performAccessibilityAction(
            stubListView,
            AccessibilityNodeInfoCompat.ACTION_CLEAR_ACCESSIBILITY_FOCUS,
            null,
        )

        verify(menuAnimationController).fadeOutIfEnabled()
    }
}
