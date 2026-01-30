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
package com.android.systemui.shared.rotation

import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.testing.TestableLooper.RunWithLooper
import android.view.Display
import android.view.Surface
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManagerPolicyConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.rotation.RotationPolicyWrapper
import com.android.systemui.shared.rotation.RotationButton.RotationButtonUpdatesCallback
import com.android.window.flags.Flags
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
@SmallTest
@RunWithLooper
class RotationButtonControllerTest : SysuiTestCase() {
    @get:Rule
    val setFlagsRule: SetFlagsRule = SetFlagsRule()
    @get:Rule
    val mocks = MockitoJUnit.rule()

    private lateinit var mController: RotationButtonController
    @Mock private lateinit var rotationPolicyWrapper: RotationPolicyWrapper
    @Mock private lateinit var rotationButton: RotationButton
    @Mock private lateinit var view: View
    @Captor private lateinit var onRotateSuggestionClick: ArgumentCaptor<View.OnClickListener>

    @Before
    fun setUp() {
        mController =
            RotationButtonController(
                rotationPolicyWrapper,
                mContext,
                /* lightIconColor = */ 0,
                /* darkIconColor = */ 0,
                /* iconCcwStart0ResId = */ 0,
                /* iconCcwStart90ResId = */ 0,
                /* iconCwStart0ResId = */ 0,
                /* iconCwStart90ResId = */ 0,
            ) {
                0
            }
    }

    @Test
    fun ifGestural_showRotationSuggestion() {
        mController.onNavigationBarWindowVisibilityChange(/* showing= */ false)
        mController.onBehaviorChanged(
            Display.DEFAULT_DISPLAY,
            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
        )
        mController.onNavigationModeChanged(WindowManagerPolicyConstants.NAV_BAR_MODE_3BUTTON)
        mController.onTaskbarStateChange(/* visible= */ false, /* stashed= */ false)
        assertThat(mController.canShowRotationButton()).isFalse()

        mController.onNavigationModeChanged(WindowManagerPolicyConstants.NAV_BAR_MODE_GESTURAL)

        assertThat(mController.canShowRotationButton()).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DEVICE_STATE_AUTO_ROTATE_SETTING_REFACTOR)
    fun onRotateSuggestionClick_rotationButtonClicked_setRotationAtAngleIfAllowedIsCalled() {
        clickRotationSuggestionButton()

        verify(rotationPolicyWrapper, times(1)).setRotationAtAngleIfAllowed(any(), any())
        verify(rotationPolicyWrapper, never()).setRotationLockAtAngle(any(), any(), any())
    }

    @Test
    fun setRotationAtAngle_forceSetRotationAtAngle_setRotationAtAngleIsCalled() {
        mController.setRotationAtAngle(
            /* isLocked= */ true,
            /* rotationSuggestion = */ Surface.ROTATION_270,
            /* caller= */ "",
        )

        verify(rotationPolicyWrapper, never()).setRotationAtAngleIfAllowed(any(), any())
        verify(rotationPolicyWrapper, times(1))
            .setRotationLockAtAngle(eq(true), eq(Surface.ROTATION_270), eq(""))
    }

    private fun clickRotationSuggestionButton() {
        mController.setRotationButton(
            rotationButton,
            object : RotationButtonUpdatesCallback {
                override fun onVisibilityChanged(isVisible: Boolean) {}

                override fun onPositionChanged() {}
            },
        )

        verify(rotationButton, times(1)).setOnClickListener(onRotateSuggestionClick.capture())
        onRotateSuggestionClick.value.onClick(view)
    }
}
