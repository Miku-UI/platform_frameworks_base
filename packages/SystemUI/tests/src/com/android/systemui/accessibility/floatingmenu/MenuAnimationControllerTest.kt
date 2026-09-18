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

import android.graphics.PointF
import android.testing.TestableLooper
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.settingslib.bluetooth.HearingAidDeviceManager
import com.android.systemui.Prefs
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.Magnification
import com.android.systemui.accessibility.utils.TestUtils
import com.android.systemui.inputdevice.data.repository.FakePointerDeviceRepository
import com.android.systemui.keyboard.data.repository.FakeKeyboardRepository
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionInteractor
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

/** Tests for [MenuAnimationController]. */
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
class MenuAnimationControllerTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()
    private var lastIsMoveToTucked = false
    private lateinit var viewPropertyAnimator: ViewPropertyAnimator
    private lateinit var menuView: MenuView
    private lateinit var menuAnimationController: TestMenuAnimationController

    private val accessibilityManager = mock<AccessibilityManager>()
    private val hearingAidDeviceManager = mock<HearingAidDeviceManager>()

    @Before
    fun setUp() {
        val keyboardRepository = FakeKeyboardRepository()
        val pointerDeviceRepository = FakePointerDeviceRepository()

        val stubWindowManager = mContext.getSystemService(WindowManager::class.java)
        val stubMenuViewAppearance = MenuViewAppearance(mContext, stubWindowManager)
        val secureSettings = TestUtils.mockSecureSettings(mContext)
        val stubMenuViewModel =
            MenuViewModel(
                mContext,
                accessibilityManager,
                secureSettings,
                hearingAidDeviceManager,
                keyboardRepository,
                pointerDeviceRepository,
                kosmos.keyguardTransitionInteractor,
                kosmos.sceneInteractor,
            )
        menuView =
            spy(
                MenuView(
                    mContext,
                    stubMenuViewModel,
                    stubMenuViewAppearance,
                    secureSettings,
                    mock<Magnification>(),
                )
            )
        viewPropertyAnimator = spy(menuView.animate())
        whenever(menuView.animate()).thenReturn(viewPropertyAnimator)

        menuAnimationController = TestMenuAnimationController(menuView, stubMenuViewAppearance)
        lastIsMoveToTucked =
            Prefs.getBoolean(
                mContext,
                Prefs.Key.HAS_ACCESSIBILITY_FLOATING_MENU_TUCKED,
                /* defaultValue= */ false,
            )
    }

    @After
    fun tearDown() {
        Prefs.putBoolean(
            mContext,
            Prefs.Key.HAS_ACCESSIBILITY_FLOATING_MENU_TUCKED,
            lastIsMoveToTucked,
        )
        menuAnimationController.mPositionAnimations.values.forEach { it!!.cancel() }
    }

    @Test
    fun moveToPosition_matchPosition() {
        val destination = PointF(50f, 60f)

        menuAnimationController.moveToPosition(destination)

        Truth.assertThat(menuView.translationX).isEqualTo(50)
        Truth.assertThat(menuView.translationY).isEqualTo(60)
    }

    @Test
    fun startShrinkAnimation_verifyAnimationEndAction() {
        menuAnimationController.startShrinkAnimation { menuView.visibility = View.VISIBLE }

        verify(viewPropertyAnimator).withEndAction(any())
    }

    @Test
    fun startGrowAnimation_menuCompletelyOpaque() {
        menuAnimationController.startShrinkAnimation(/* endAction= */ null)

        menuAnimationController.startGrowAnimation()

        Truth.assertThat(menuView.alpha).isEqualTo(/* completelyOpaque */ 1.0f)
    }

    @Test
    fun moveToEdgeAndHide_untucked_expectedSharedPreferenceValue() {
        Prefs.putBoolean(
            mContext,
            Prefs.Key.HAS_ACCESSIBILITY_FLOATING_MENU_TUCKED,
            /* value= */ false,
        )

        menuAnimationController.moveToEdgeAndHide()
        val isMoveToTucked =
            Prefs.getBoolean(
                mContext,
                Prefs.Key.HAS_ACCESSIBILITY_FLOATING_MENU_TUCKED,
                /* defaultValue= */ false,
            )

        Truth.assertThat(isMoveToTucked).isTrue()
    }

    @Test
    fun moveOutEdgeAndShow_tucked_expectedSharedPreferenceValue() {
        Prefs.putBoolean(
            mContext,
            Prefs.Key.HAS_ACCESSIBILITY_FLOATING_MENU_TUCKED,
            /* value= */ true,
        )

        menuAnimationController.moveOutEdgeAndShow()
        val isMoveToTucked =
            Prefs.getBoolean(
                mContext,
                Prefs.Key.HAS_ACCESSIBILITY_FLOATING_MENU_TUCKED,
                /* defaultValue= */ true,
            )

        Truth.assertThat(isMoveToTucked).isFalse()
    }

    @Test
    fun startTuckedAnimationPreview_hasAnimation() {
        menuAnimationController.startTuckedAnimationPreview()

        verify(menuView).clearAnimation()
        verify(menuView).startAnimation(any())
    }

    @Test
    fun startSpringAnimationsAndEndOneAnimation_notTriggerEndAction() {
        val onSpringAnimationsEndCallback = mock<Runnable>()
        menuAnimationController.setSpringAnimationsEndAction(onSpringAnimationsEndCallback)

        setupAndRunSpringAnimations()
        menuAnimationController.mPositionAnimations.values.first().let { skipAnimationToEnd(it) }

        verifyNoMoreInteractions(onSpringAnimationsEndCallback)
    }

    @Test
    fun startAndEndSpringAnimations_triggerEndAction() {
        val onSpringAnimationsEndCallback = mock<Runnable>()
        menuAnimationController.setSpringAnimationsEndAction(onSpringAnimationsEndCallback)

        setupAndRunSpringAnimations()
        menuAnimationController.mPositionAnimations.values.forEach { skipAnimationToEnd(it) }

        verify(onSpringAnimationsEndCallback).run()
    }

    @Test
    fun flingThenSpringAnimationsAreEnded_triggerEndAction() {
        val onSpringAnimationsEndCallback = mock<Runnable>()
        menuAnimationController.setSpringAnimationsEndAction(onSpringAnimationsEndCallback)

        menuAnimationController.flingMenuThenSpringToEdge(
            PointF(),
            /* velocityX= */ 100f,
            /* velocityY= */ 100f,
        )
        val endListenerCaptor = argumentCaptor<DynamicAnimation.OnAnimationEndListener>()
        menuAnimationController.mPositionAnimations.values.forEach {
            verify(it).addEndListener(endListenerCaptor.capture())
        }
        endListenerCaptor.allValues.forEach {
            it.onAnimationEnd(
                mock<FlingAnimation>(),
                /* canceled */ false,
                /* endValue */ 0f,
                /* endVelocity */ 0f,
            )
        }
        menuAnimationController.mPositionAnimations.values.forEach { skipAnimationToEnd(it) }

        verify(onSpringAnimationsEndCallback).run()
    }

    @Test
    fun existFlingIsRunningAndTheOtherAreEnd_notTriggerEndAction() {
        val onSpringAnimationsEndCallback = mock<Runnable>()
        menuAnimationController.setSpringAnimationsEndAction(onSpringAnimationsEndCallback)

        menuAnimationController.flingMenuThenSpringToEdge(
            PointF(),
            /* velocityX= */ 200f,
            /* velocityY= */ 200f,
        )
        val endListenerCaptor = argumentCaptor<DynamicAnimation.OnAnimationEndListener>()
        menuAnimationController.mPositionAnimations.values.forEach {
            verify(it).addEndListener(endListenerCaptor.capture())
        }
        endListenerCaptor.firstValue.onAnimationEnd(
            mock<FlingAnimation>(),
            /* canceled */ false,
            /* endValue */ 0f,
            /* endVelocity */ 0f,
        )
        menuAnimationController.mPositionAnimations.values
            .stream()
            .filter { it is SpringAnimation }
            .forEach { this.skipAnimationToEnd(it) }

        verifyNoMoreInteractions(onSpringAnimationsEndCallback)
    }

    @Test
    fun tuck_animates() {
        menuAnimationController.cancelAnimations()
        menuAnimationController.moveToEdgeAndHide()
        Truth.assertThat(
                menuAnimationController.getAnimation(DynamicAnimation.TRANSLATION_X).isRunning
            )
            .isTrue()
    }

    @Test
    fun untuck_animates() {
        menuAnimationController.cancelAnimations()
        menuAnimationController.moveOutEdgeAndShow()
        Truth.assertThat(
                menuAnimationController.getAnimation(DynamicAnimation.TRANSLATION_X).isRunning
            )
            .isTrue()
    }

    private fun setupAndRunSpringAnimations() {
        val stiffness = 700f
        val dampingRatio = 0.85f
        val velocity = 100f
        val finalPosition = 300f

        menuAnimationController.springMenuWith(
            DynamicAnimation.TRANSLATION_X,
            SpringForce().setStiffness(stiffness).setDampingRatio(dampingRatio),
            velocity,
            finalPosition,
            /* writeToPosition= */ true,
        )
        menuAnimationController.springMenuWith(
            DynamicAnimation.TRANSLATION_Y,
            SpringForce().setStiffness(stiffness).setDampingRatio(dampingRatio),
            velocity,
            finalPosition,
            /* writeToPosition= */ true,
        )
    }

    private fun skipAnimationToEnd(animation: DynamicAnimation<*>?) {
        val springAnimation = (animation as SpringAnimation)
        // The doAnimationFrame function is used for skipping animation to the end.
        springAnimation.doAnimationFrame(100)
        springAnimation.skipToEnd()
        springAnimation.doAnimationFrame(200)
    }

    /** Wrapper class for testing. */
    private class TestMenuAnimationController(
        menuView: MenuView,
        menuViewAppearance: MenuViewAppearance,
    ) : MenuAnimationController(menuView, menuViewAppearance) {
        override fun createFlingAnimation(
            menuView: MenuView,
            menuPositionProperty: MenuPositionProperty,
        ): FlingAnimation {
            return spy(super.createFlingAnimation(menuView, menuPositionProperty))
        }
    }
}
