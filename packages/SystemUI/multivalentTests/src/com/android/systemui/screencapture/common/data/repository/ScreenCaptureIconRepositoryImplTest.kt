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

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.packageManager
import android.content.packageManagerWrapper
import android.content.pm.ActivityInfo
import android.content.pm.UserInfo
import android.content.testableContext
import android.os.UserManager
import android.os.userManager
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.FastBitmapDrawable
import com.android.launcher3.icons.IconFactory
import com.android.launcher3.util.UserIconInfo
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import javax.inject.Provider
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

@SuppressLint("MissingPermission", "VisibleForTests")
@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureIconRepositoryImplTest : SysuiTestCase() {

    private val fakeUnbadgedBitmap = createBitmap(100, 100)
    private val fakeBadgedBitmap = createBitmap(200, 200)

    private val fakeDrawable = FastBitmapDrawable(fakeUnbadgedBitmap)
    private val fakeBadgedDrawable = FastBitmapDrawable(fakeBadgedBitmap)

    private val mockBitmapInfo =
        mock<BitmapInfo> { on { newIcon(any(), any(), anyOrNull()) } doReturn fakeBadgedDrawable }

    private val mockIconFactory =
        mock<IconFactory> { on { createBadgedIconBitmap(any(), any()) } doReturn mockBitmapInfo }

    private val testIconFactoryProvider = Provider { mockIconFactory }

    private val kosmos =
        testKosmosNew().apply {
            packageManagerWrapper.stub {
                on { getActivityInfo(any(), any()) } doReturn ActivityInfo()
            }
            packageManager.stub { on { loadItemIcon(any(), anyOrNull()) } doReturn fakeDrawable }
            userManager.stub {
                on { getUserInfo(any()) } doReturn
                    UserInfo(
                        123,
                        "TestName",
                        "TestIconPath",
                        0,
                        UserManager.USER_TYPE_FULL_SECONDARY,
                    )
            }
        }

    @Test
    fun loadIcon_returnsBadgedIcon() =
        kosmos.runTest {
            // Arrange
            val iconRepository =
                ScreenCaptureIconRepositoryImpl(
                    bgContext = testDispatcher,
                    context = testableContext,
                    userManager = userManager,
                    packageManagerWrapper = packageManagerWrapper,
                    packageManager = packageManager,
                    iconFactoryProvider = testIconFactoryProvider,
                )

            // Act
            val result =
                iconRepository.loadIcon(
                    component = ComponentName("TestPackage", "TestClass"),
                    userId = 123,
                )

            // Assert
            verify(packageManagerWrapper)
                .getActivityInfo(eq(ComponentName("TestPackage", "TestClass")), eq(123))
            verify(mockIconFactory).createBadgedIconBitmap(same(fakeDrawable), any())
            verify(mockIconFactory).close()
            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()?.sameAs(fakeBadgedBitmap)).isTrue()
        }

    @Test
    fun loadIcon_unbadged_returnsUnbadgedIcon() =
        kosmos.runTest {
            // Arrange
            val iconRepository =
                ScreenCaptureIconRepositoryImpl(
                    bgContext = testDispatcher,
                    context = testableContext,
                    userManager = userManager,
                    packageManagerWrapper = packageManagerWrapper,
                    packageManager = packageManager,
                    iconFactoryProvider = testIconFactoryProvider,
                )

            // Act
            val result =
                iconRepository.loadIcon(
                    component = ComponentName("TestPackage", "TestClass"),
                    userId = 123,
                    badged = false,
                )

            // Assert
            verify(packageManagerWrapper)
                .getActivityInfo(eq(ComponentName("TestPackage", "TestClass")), eq(123))
            verifyNoInteractions(mockIconFactory)
            assertThat(result.isSuccess).isTrue()
            assertThat(result.getOrNull()?.sameAs(fakeUnbadgedBitmap)).isTrue()
        }

    @Test
    fun loadIcon_couldNotFindActivity_returnsNull() =
        kosmos.runTest {
            // Arrange
            packageManagerWrapper.stub { on { getActivityInfo(any(), any()) } doReturn null }
            val iconRepository =
                ScreenCaptureIconRepositoryImpl(
                    bgContext = testDispatcher,
                    context = testableContext,
                    userManager = userManager,
                    packageManagerWrapper = packageManagerWrapper,
                    packageManager = packageManager,
                    iconFactoryProvider = testIconFactoryProvider,
                )

            // Act
            val result =
                iconRepository.loadIcon(
                    component = ComponentName("TestPackage", "TestClass"),
                    userId = 123,
                )

            // Assert
            verify(packageManagerWrapper)
                .getActivityInfo(eq(ComponentName("TestPackage", "TestClass")), eq(123))
            verifyNoInteractions(mockIconFactory)
            assertThat(result.isFailure).isTrue()
        }

    @Test
    fun getIconTypeForUser_fullUser_returnsMainIconType() =
        kosmos.runTest {
            // Arrange
            userManager.stub {
                on { getUserInfo(any()) } doReturn
                    UserInfo(
                        123,
                        "TestName",
                        "TestIconPath",
                        0,
                        UserManager.USER_TYPE_FULL_SECONDARY,
                    )
            }
            val iconRepository =
                ScreenCaptureIconRepositoryImpl(
                    bgContext = testDispatcher,
                    context = testableContext,
                    userManager = userManager,
                    packageManagerWrapper = packageManagerWrapper,
                    packageManager = packageManager,
                    iconFactoryProvider = testIconFactoryProvider,
                )

            // Act
            val result = iconRepository.getIconTypeForUser(123)

            // Assert
            assertThat(result).isEqualTo(UserIconInfo.TYPE_MAIN)
        }

    @Test
    fun getIconTypeForUser_cloneProfile_returnsClonedIconType() =
        kosmos.runTest {
            // Arrange
            userManager.stub {
                on { getUserInfo(any()) } doReturn
                    UserInfo(
                        123,
                        "TestName",
                        "TestIconPath",
                        0,
                        UserManager.USER_TYPE_PROFILE_CLONE,
                    )
            }
            val iconRepository =
                ScreenCaptureIconRepositoryImpl(
                    bgContext = testDispatcher,
                    context = testableContext,
                    userManager = userManager,
                    packageManagerWrapper = packageManagerWrapper,
                    packageManager = packageManager,
                    iconFactoryProvider = testIconFactoryProvider,
                )

            // Act
            val result = iconRepository.getIconTypeForUser(123)

            // Assert
            assertThat(result).isEqualTo(UserIconInfo.TYPE_CLONED)
        }

    @Test
    fun getIconTypeForUser_managedProfile_returnsWorkIconType() =
        kosmos.runTest {
            // Arrange
            userManager.stub {
                on { getUserInfo(any()) } doReturn
                    UserInfo(
                        123,
                        "TestName",
                        "TestIconPath",
                        0,
                        UserManager.USER_TYPE_PROFILE_MANAGED,
                    )
            }
            val iconRepository =
                ScreenCaptureIconRepositoryImpl(
                    bgContext = testDispatcher,
                    context = testableContext,
                    userManager = userManager,
                    packageManagerWrapper = packageManagerWrapper,
                    packageManager = packageManager,
                    iconFactoryProvider = testIconFactoryProvider,
                )

            // Act
            val result = iconRepository.getIconTypeForUser(123)

            // Assert
            assertThat(result).isEqualTo(UserIconInfo.TYPE_WORK)
        }

    @Test
    fun getIconTypeForUser_privateProfile_returnsPrivateIconType() =
        kosmos.runTest {
            // Arrange
            userManager.stub {
                on { getUserInfo(any()) } doReturn
                    UserInfo(
                        123,
                        "TestName",
                        "TestIconPath",
                        0,
                        UserManager.USER_TYPE_PROFILE_PRIVATE,
                    )
            }
            val iconRepository =
                ScreenCaptureIconRepositoryImpl(
                    bgContext = testDispatcher,
                    context = testableContext,
                    userManager = userManager,
                    packageManagerWrapper = packageManagerWrapper,
                    packageManager = packageManager,
                    iconFactoryProvider = testIconFactoryProvider,
                )

            // Act
            val result = iconRepository.getIconTypeForUser(123)

            // Assert
            assertThat(result).isEqualTo(UserIconInfo.TYPE_PRIVATE)
        }
}
