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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.statusbar.notification.row.data.repository.TEST_BUNDLE_SPEC
import com.android.systemui.statusbar.notification.shared.NotificationBundleUi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(NotificationBundleUi.FLAG_NAME)
class BundleHeaderGutsViewModelTest : SysuiTestCase() {

    @get:Rule val rule: MockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var mockDisableBundle: () -> Unit

    @Mock private lateinit var mockCloseGuts: () -> Unit

    @Mock private lateinit var mockOnDismissClicked: () -> Unit

    @Mock private lateinit var mockOnSettingsClicked: () -> Unit

    private lateinit var underTest: BundleHeaderGutsViewModel

    @Before
    fun setUp() {
        underTest =
            BundleHeaderGutsViewModel(
                titleText = TEST_BUNDLE_SPEC.titleText,
                summaryText = TEST_BUNDLE_SPEC.summaryText,
                bundleIcon = TEST_BUNDLE_SPEC.icon,
                disableBundle = mockDisableBundle,
                closeGuts = mockCloseGuts,
                onDismissClicked = mockOnDismissClicked,
                onSettingsClicked = mockOnSettingsClicked,
            )
    }

    @Test
    fun switchState_false_onDoneOrApplyClicked() {
        // Arrange
        underTest.switchState = false

        // Act
        underTest.onDoneOrApplyClicked()

        // Assert
        verify(mockDisableBundle).invoke()
        verify(mockCloseGuts).invoke()
    }

    @Test
    fun switchState_true_onDoneOrApplyClicked() {
        // Arrange
        underTest.switchState = true

        // Act
        underTest.onDoneOrApplyClicked()

        // Assert
        verify(mockCloseGuts).invoke()
        verify(mockDisableBundle, never()).invoke()
        verify(mockOnDismissClicked, never()).invoke()
    }
}
