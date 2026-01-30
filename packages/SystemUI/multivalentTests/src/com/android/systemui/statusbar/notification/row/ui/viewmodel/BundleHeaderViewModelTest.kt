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

package com.android.systemui.statusbar.notification.row.ui.viewmodel

import android.platform.test.annotations.EnableFlags
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.MutableSceneTransitionLayoutState
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.notifications.ui.composable.row.BundleHeader
import com.android.systemui.statusbar.notification.row.domain.interactor.BundleInteractor
import com.android.systemui.statusbar.notification.shared.NotificationBundleUi
import com.android.systemui.testKosmos
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@EnableFlags(NotificationBundleUi.FLAG_NAME)
class BundleHeaderViewModelTest : SysuiTestCase() {

    @get:Rule val rule: MockitoRule = MockitoJUnit.rule()
    private val kosmos = testKosmos()

    @Mock private lateinit var mockBundleInteractor: BundleInteractor
    private lateinit var underTest: BundleHeaderViewModel

    @Before
    fun setup() {
        whenever(mockBundleInteractor.previewIcons).thenReturn(MutableStateFlow(emptyList()))
        underTest = BundleHeaderViewModel(mockBundleInteractor)
        underTest.activateIn(kosmos.testScope)
    }

    @Test
    fun onHeaderClicked_toggles_expansion_state_to_expanded() {
        // Arrange
        val state =
            MutableSceneTransitionLayoutState(
                initialScene = BundleHeader.Scenes.Collapsed,
                motionScheme = MotionScheme.standard(),
            )
        underTest.state = state
        whenever(mockBundleInteractor.state).thenReturn(state)

        // Act
        underTest.onHeaderClicked()

        // Assert
        verify(mockBundleInteractor).setTargetScene(BundleHeader.Scenes.Expanded)
    }

    @Test
    fun onHeaderClicked_toggles_expansion_state_to_collapsed() {
        // Arrange
        val state =
            MutableSceneTransitionLayoutState(
                initialScene = BundleHeader.Scenes.Expanded,
                motionScheme = MotionScheme.standard(),
            )
        underTest.state = state
        whenever(mockBundleInteractor.state).thenReturn(state)

        // Act
        underTest.onHeaderClicked()

        // Assert
        verify(mockBundleInteractor).setTargetScene(BundleHeader.Scenes.Collapsed)
    }
}