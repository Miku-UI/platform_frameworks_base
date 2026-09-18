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

package com.android.systemui.screencapture.common.ui.viewmodel

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.screencapture.common.domain.model.ScreenCaptureAppContent
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class AppContentViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmosNew()

    @Test
    fun constructor_usesFieldsFromModel() =
        kosmos.runTest {
            // Arrange
            val fakeAppContent =
                ScreenCaptureAppContent(
                    packageName = "FakePackage",
                    contentId = 1,
                    label = "FakeLabel",
                    thumbnail = createBitmap(200, 100),
                    icon = null,
                )

            // Act
            val viewModel = AppContentViewModel(fakeAppContent, mContext)

            // Assert
            with(viewModel) {
                assertThat(icon).isNull()
                assertThat(label?.isSuccess).isTrue()
                assertThat(label?.getOrNull()).isEqualTo("FakeLabel")
                assertThat(thumbnail?.isSuccess).isTrue()
                assertThat(thumbnail?.getOrNull()?.sameAs(fakeAppContent.thumbnail)).isTrue()
                assertThat(backgroundColorOpaque).isEqualTo(Color.Black)
            }
        }

    @Test
    fun onActivated_loadsIcon() =
        kosmos.runTest {
            // Arrange
            val fakeIconBitmap = createBitmap(10, 10)
            val fakeAppContent =
                ScreenCaptureAppContent(
                    packageName = "FakePackage",
                    contentId = 1,
                    label = "FakeLabel",
                    thumbnail = null,
                    icon = Icon.createWithBitmap(fakeIconBitmap),
                )
            val viewModel = AppContentViewModel(fakeAppContent, mContext)

            // Act
            viewModel.activateIn(testScope)

            // Assert
            assertThat(viewModel.icon?.isSuccess).isTrue()
            assertThat(viewModel.icon?.getOrNull()?.sameAs(fakeIconBitmap)).isTrue()
        }

    @Test
    fun onActivated_noIcon_fallsBackToApplicationIcon() =
        kosmos.runTest {
            // Arrange
            val packageName = "FakePackage"
            val fakeAppIconBitmap = createBitmap(10, 10)
            val fakeAppIcon = BitmapDrawable(mContext.resources, fakeAppIconBitmap)

            val mockPackageManager = mock<android.content.pm.PackageManager>()
            whenever(mockPackageManager.getApplicationIcon(packageName)).thenReturn(fakeAppIcon)
            mContext.setMockPackageManager(mockPackageManager)

            val fakeAppContent =
                ScreenCaptureAppContent(
                    packageName = packageName,
                    contentId = 1,
                    label = "FakeLabel",
                    thumbnail = null,
                    icon = null,
                )
            val viewModel = AppContentViewModel(fakeAppContent, mContext)

            // Act
            viewModel.activateIn(testScope)

            // Assert
            assertThat(viewModel.icon?.isSuccess).isTrue()
            assertThat(viewModel.icon?.getOrNull()?.sameAs(fakeAppIconBitmap)).isTrue()
        }

    @Test
    fun onActivated_noIcon_noFallback_returnsFailure() =
        kosmos.runTest {
            // Arrange
            val fakeAppContent =
                ScreenCaptureAppContent(
                    packageName = "FakePackage",
                    contentId = 1,
                    label = "FakeLabel",
                    thumbnail = null,
                    icon = null,
                )
            val viewModel = AppContentViewModel(fakeAppContent, mContext)

            // Act
            viewModel.activateIn(testScope)

            // Assert
            assertThat(viewModel.icon?.isFailure).isTrue()
            assertThat(viewModel.icon?.exceptionOrNull())
                .isInstanceOf(IllegalStateException::class.java)
        }
}
