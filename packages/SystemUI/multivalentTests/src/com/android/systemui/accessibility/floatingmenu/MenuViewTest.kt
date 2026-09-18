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
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper.RunWithLooper
import android.view.accessibility.accessibilityManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.accessibility.AccessibilityShortcutController
import com.android.internal.accessibility.AccessibilityShortcutController.MAGNIFICATION_CONTROLLER_NAME
import com.android.internal.accessibility.common.ShortcutConstants.AccessibilityFragmentType.VOLUME_SHORTCUT_TOGGLE
import com.android.internal.accessibility.common.ShortcutConstants.UserShortcutType.SOFTWARE
import com.android.internal.accessibility.dialog.AccessibilityTarget
import com.android.systemui.Flags
import com.android.systemui.Prefs
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.floatingmenu.MenuView.OnTargetFeaturesChangeListener
import com.android.systemui.inputdevice.data.repository.fake
import com.android.systemui.inputdevice.data.repository.pointerDeviceRepository
import com.android.systemui.keyboard.data.repository.keyboardRepository
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests for [MenuView]. */
@RunWith(AndroidJUnit4::class)
@RunWithLooper(setAsMainLooper = true)
@SmallTest
class MenuViewTest : SysuiTestCase() {
    private var nightMode = 0
    private lateinit var uiModeManager: UiModeManager
    private var lastPosition: String = ""
    private val shortcutTargets = mutableListOf(MAGNIFICATION_CONTROLLER_NAME)
    private val testTargetList =
        listOf(TestAccessibilityTarget(mContext, 123), TestAccessibilityTarget(mContext, 456))
    private val onTargetFeaturesChangeListener: OnTargetFeaturesChangeListener = mock {}
    private val fakeLifecycleOwner: LifecycleOwner = mock()

    private val kosmos = testKosmosNew()

    private val fakeKeyboardRepository = kosmos.keyboardRepository

    private val fakePointerDeviceRepository = kosmos.pointerDeviceRepository.fake

    @SuppressLint("MissingPermission")
    @Before
    fun setUp() {
        uiModeManager = context.getSystemService(UiModeManager::class.java)
        nightMode = uiModeManager.nightMode
        uiModeManager.nightMode = UiModeManager.MODE_NIGHT_YES

        // Programmatically update the resource's configuration to night mode to reduce flakiness
        val nightConfig = Configuration(mContext.resources.configuration)
        nightConfig.uiMode = Configuration.UI_MODE_NIGHT_YES
        context.resources.updateConfiguration(nightConfig, context.resources.displayMetrics, null)

        val lifecycleRegistry = LifecycleRegistry(fakeLifecycleOwner)
        whenever(fakeLifecycleOwner.lifecycle).thenReturn(lifecycleRegistry)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        with(kosmos) {
            whenever(accessibilityManager.getAccessibilityShortcutTargets(anyInt()))
                .thenReturn(shortcutTargets)
            menuViewModel.onGuardedSceneChanged(false)
        }

        createAndAttachMenuView()

        lastPosition =
            Prefs.getString(
                context,
                Prefs.Key.ACCESSIBILITY_FLOATING_MENU_POSITION,
                /* defaultValue= */ "",
            )
    }

    @After
    fun tearDown() {
        uiModeManager.nightMode = nightMode
        Prefs.putString(mContext, Prefs.Key.ACCESSIBILITY_FLOATING_MENU_POSITION, lastPosition)
    }

    @Test
    fun insetsOnDarkTheme_menuOnLeft_matchInsets() {
        // In dark theme, the inset is not 0 to avoid weird spacing issue between the menu and
        // the edge of the screen.
        kosmos.runTest {
            menuView.onConfigurationChanged(Configuration())
            val insetLayerDrawable = menuView.background as InstantInsetLayerDrawable
            assert(
                insetLayerDrawable.getLayerInsetLeft(INDEX_MENU_ITEM) != 0 &&
                    insetLayerDrawable.getLayerInsetRight(INDEX_MENU_ITEM) == 0
            )
        }
    }

    @Test
    fun onDraggingStart_matchInsets() {
        kosmos.runTest {
            menuView.onDraggingStart()
            val insetLayerDrawable = menuView.background as InstantInsetLayerDrawable

            assertThat(insetLayerDrawable.getLayerInsetLeft(INDEX_MENU_ITEM)).isEqualTo(0)
            assertThat(insetLayerDrawable.getLayerInsetTop(INDEX_MENU_ITEM)).isEqualTo(0)
            assertThat(insetLayerDrawable.getLayerInsetRight(INDEX_MENU_ITEM)).isEqualTo(0)
            assertThat(insetLayerDrawable.getLayerInsetBottom(INDEX_MENU_ITEM)).isEqualTo(0)
        }
    }

    @Test
    fun onAnimationend_updatePositionForSharedPreference() {
        kosmos.runTest {
            val percentageX = 0.0f
            val percentageY = 0.5f
            menuView.persistPositionAndUpdateEdge(Position(percentageX, percentageY))
            val positionString =
                Prefs.getString(
                    context,
                    Prefs.Key.ACCESSIBILITY_FLOATING_MENU_POSITION,
                    /* defaultValue= */ null,
                )
            val position = Position.fromString(positionString)

            assertThat(position.percentageX).isEqualTo(percentageX)
            assertThat(position.percentageY).isEqualTo(percentageY)
        }
    }

    @Test
    fun onEdgeChangedIfNeeded_moveToLeftEdge_matchRadii() {
        kosmos.runTest {
            val draggableBounds = menuViewAppearance.menuDraggableBounds

            menuView.translationX = draggableBounds.right.toFloat()
            menuView.translationX = draggableBounds.left.toFloat()
            menuView.onEdgeChangedIfNeeded()
            val radii = menuViewGradient.getCornerRadii()

            assertThat(radii!![0]).isEqualTo(0.0f)
            assertThat(radii[1]).isEqualTo(0.0f)
            assertThat(radii[6]).isEqualTo(0.0f)
            assertThat(radii[7]).isEqualTo(0.0f)
        }
    }

    @Test
    fun onEdgeChanged_startsRadiiAnimation() {
        kosmos.runTest {
            menuView.onEdgeChanged()
            assertThat(menuView.menuAnimationController.mRadiiAnimator.isStarted).isTrue()
        }
    }

    @Test
    fun onDraggingStart_startsRadiiAnimation() {
        kosmos.runTest {
            menuView.onDraggingStart()
            assertThat(menuView.menuAnimationController.mRadiiAnimator.isStarted).isTrue()
        }
    }

    @Test
    fun modelFeaturesChanged_targetFeaturesChanged() {
        kosmos.runTest {
            menuView.show()
            clearInvocations(onTargetFeaturesChangeListener)
            menuViewModel.onTargetFeaturesChanged(testTargetList)

            verify(onTargetFeaturesChangeListener).onChange(any())
        }
    }

    @Test
    fun modelFeaturesUnchanged_targetFeaturesUnchanged() {
        kosmos.runTest {
            menuView.show()
            menuViewModel.onTargetFeaturesChanged(testTargetList)
            clearInvocations(onTargetFeaturesChangeListener)
            menuViewModel.onTargetFeaturesChanged(testTargetList)

            verify(onTargetFeaturesChangeListener, never()).onChange(any())
        }
    }

    @Test
    fun modelFeaturesReordered_targetFeaturesChanged() {
        kosmos.runTest {
            menuView.show()
            menuViewModel.onTargetFeaturesChanged(testTargetList)
            clearInvocations(onTargetFeaturesChangeListener)
            menuViewModel.onTargetFeaturesChanged(testTargetList.reversed())

            verify(onTargetFeaturesChangeListener).onChange(any())
        }
    }

    private val menuViewInsetLayer: InstantInsetLayerDrawable
        get() = kosmos.menuView.background as InstantInsetLayerDrawable

    private val menuViewGradient: GradientDrawable
        get() = this.menuViewInsetLayer.getDrawable(INDEX_MENU_ITEM) as GradientDrawable

    @Test
    @EnableFlags(Flags.FLAG_FLOATING_MENU_MORE_OPTIONS)
    fun onTargetFeaturesChanged_withKeyboard_moreOptionsIsAdded() {
        kosmos.runTest {
            menuView.show()

            val initialTargets = listOf(TestAccessibilityTarget(context, 1))
            menuViewModel.onTargetFeaturesChanged(initialTargets)
            assertThat(menuView.targetFeaturesView.adapter!!.itemCount).isEqualTo(1)

            fakeKeyboardRepository.setIsAnyKeyboardConnected(true)
            menuViewModel.onTargetFeaturesChanged(initialTargets)

            assertThat(menuView.targetFeaturesView.adapter!!.itemCount).isEqualTo(2)
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_FLOATING_MENU_MORE_OPTIONS)
    fun onTargetFeaturesChanged_withPointer_moreOptionsIsAdded() {
        kosmos.runTest {
            menuView.show()

            val initialTargets = listOf(TestAccessibilityTarget(context, 1))
            menuViewModel.onTargetFeaturesChanged(initialTargets)
            assertThat(menuView.targetFeaturesView.adapter!!.itemCount).isEqualTo(1)

            fakePointerDeviceRepository.setIsAnyPointerConnected(true)
            menuViewModel.onTargetFeaturesChanged(initialTargets)

            assertThat(menuView.targetFeaturesView.adapter!!.itemCount).isEqualTo(2)
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_FLOATING_MENU_MORE_OPTIONS)
    fun onTargetFeaturesChanged_withPointer_guardedScene_moreOptionsIsNotAdded() {
        kosmos.runTest {
            menuView.show()

            val initialTargets = listOf(TestAccessibilityTarget(context, 1))
            menuViewModel.onTargetFeaturesChanged(initialTargets)
            assertThat(menuView.targetFeaturesView.adapter!!.itemCount).isEqualTo(1)

            fakePointerDeviceRepository.setIsAnyPointerConnected(true)
            menuViewModel.onTargetFeaturesChanged(initialTargets)
            menuViewModel.onGuardedSceneChanged(true)

            assertThat(menuView.targetFeaturesView.adapter!!.itemCount).isEqualTo(1)
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_FLOATING_MENU_MORE_OPTIONS)
    fun onTargetFeaturesChanged_noInputDevice_moreOptionsIsNotAdded() {
        kosmos.runTest {
            fakeKeyboardRepository.setIsAnyKeyboardConnected(false)
            fakePointerDeviceRepository.setIsAnyPointerConnected(false)

            menuView.show()
            val initialTargets = listOf(TestAccessibilityTarget(context, 1))
            menuViewModel.onTargetFeaturesChanged(initialTargets)

            assertThat(menuView.targetFeaturesView.adapter!!.itemCount).isEqualTo(1)
        }
    }

    @Test
    @DisableFlags(Flags.FLAG_FLOATING_MENU_MORE_OPTIONS)
    fun onTargetFeaturesChanged_flagDisabled_moreOptionsIsNotAdded() {
        kosmos.runTest {
            fakeKeyboardRepository.setIsAnyKeyboardConnected(true)
            fakePointerDeviceRepository.setIsAnyPointerConnected(true)

            menuView.show()
            val initialTargets = listOf(TestAccessibilityTarget(context, 1))
            menuViewModel.onTargetFeaturesChanged(initialTargets)

            assertThat(menuView.targetFeaturesView.adapter!!.itemCount).isEqualTo(1)
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_FLOATING_MENU_MAGNIFICATION_STATUS)
    fun onShow_registersMagnificationListener() =
        kosmos.runTest {
            clearInvocations(menuViewMagnification)

            menuView.show()

            verify(menuViewMagnification).registerActivationChangedListener(any())
        }

    @Test
    @DisableFlags(Flags.FLAG_FLOATING_MENU_MAGNIFICATION_STATUS)
    fun onShow_doesNotregisterMagnificationListener() =
        kosmos.runTest {
            clearInvocations(menuViewMagnification)

            menuView.show()

            verify(menuViewMagnification, never()).registerActivationChangedListener(any())
        }

    @Test
    fun onHide_unregistersMagnificationListener() =
        kosmos.runTest {
            clearInvocations(menuViewMagnification)

            menuView.hide()

            verify(menuViewMagnification).unregisterActivationChangedListener(any())
        }

    /** Simplified AccessibilityTarget for testing MenuView. */
    private class TestAccessibilityTarget(context: Context?, uid: Int) :
        AccessibilityTarget(
            context,
            SOFTWARE,
            VOLUME_SHORTCUT_TOGGLE,
            false,
            AccessibilityShortcutController.MAGNIFICATION_COMPONENT_NAME.flattenToString(),
            uid,
            null,
            null,
            null,
        )

    companion object {
        private const val INDEX_MENU_ITEM = 0
    }

    private fun createAndAttachMenuView() {
        with(kosmos) {
            menuView.setOnTargetFeaturesChangeListener(onTargetFeaturesChangeListener)
            menuView.setViewTreeLifecycleOwner(fakeLifecycleOwner)
            menuView.onAttachedToWindow()
        }
    }
}
