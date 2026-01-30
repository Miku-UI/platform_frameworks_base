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

package com.android.systemui.screencapture.common.data.repository

import android.content.ComponentName
import android.content.packageManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureLabelRepositoryImplTest : SysuiTestCase() {

    private val kosmos =
        testKosmosNew().apply {
            packageManager.stub {
                on {
                    getApplicationInfoAsUser(
                        any<String>(),
                        any<PackageManager.ApplicationInfoFlags>(),
                        any<Int>(),
                    )
                } doReturn ApplicationInfo()
                on { getApplicationLabel(any()) } doReturn "TestUnbadgedLabel"
                on { getUserBadgedLabel(any(), any()) } doReturn "TestBadgedLabel"
            }
        }

    @Test
    fun loadLabel_returnsBadgedLabel() =
        kosmos.runTest {
            // Arrange
            val labelRepository =
                ScreenCaptureLabelRepositoryImpl(
                    bgContext = testDispatcher,
                    packageManager = packageManager,
                )

            // Act
            val result =
                labelRepository.loadLabel(
                    component = ComponentName("TestPackage", "TestClass"),
                    userId = 123,
                )

            // Assert
            verify(packageManager)
                .getApplicationInfoAsUser(
                    eq("TestPackage"),
                    argThat<PackageManager.ApplicationInfoFlags> { value == 0L },
                    eq(123),
                )
            verify(packageManager).getUserBadgedLabel(any(), eq(UserHandle(123)))
            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo("TestBadgedLabel")
        }

    @Test
    fun loadLabel_unbadged_returnsUnbadgedLabel() =
        kosmos.runTest {
            // Arrange
            val labelRepository =
                ScreenCaptureLabelRepositoryImpl(
                    bgContext = testDispatcher,
                    packageManager = packageManager,
                )

            // Act
            val result =
                labelRepository.loadLabel(
                    component = ComponentName("TestPackage", "TestClass"),
                    userId = 123,
                    badged = false,
                )

            // Assert
            verify(packageManager)
                .getApplicationInfoAsUser(
                    eq("TestPackage"),
                    argThat<PackageManager.ApplicationInfoFlags> { value == 0L },
                    eq(123),
                )
            verify(packageManager, never()).getUserBadgedLabel(any(), any())
            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()).isEqualTo("TestUnbadgedLabel")
        }

    @Test
    fun loadLabel_appNotFound_returnsNull() =
        kosmos.runTest {
            // Arrange
            val labelRepository =
                ScreenCaptureLabelRepositoryImpl(
                    bgContext = testDispatcher,
                    packageManager =
                        packageManager.stub {
                            on {
                                getApplicationInfoAsUser(
                                    any<String>(),
                                    any<PackageManager.ApplicationInfoFlags>(),
                                    any<Int>(),
                                )
                            } doThrow PackageManager.NameNotFoundException()
                        },
                )

            // Act
            val result =
                labelRepository.loadLabel(
                    component = ComponentName("TestPackage", "TestClass"),
                    userId = 123,
                )

            // Assert
            assertThat(result.isFailure).isTrue()
        }
}
