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
 *
 */

package com.android.systemui.user.data.repository

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.UserManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.doAnswer

@SmallTest
@RunWith(AndroidJUnit4::class)
class UserIconProviderTest : SysuiTestCase() {
    @Mock private lateinit var userManager: UserManager
    private val scheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(scheduler)
    private val testScope = TestScope(testDispatcher)
    private lateinit var underTest: UserIconProvider

    private var iconFetchCount: Int = 0

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        doAnswer { invocation ->
                iconFetchCount += 1
                val userId = invocation.arguments[0] as Int
                when (userId) {
                    USER_ID -> return@doAnswer USER_IMAGE
                    OTHER_USER_ID -> return@doAnswer OTHER_USER_IMAGE
                    else -> return@doAnswer null
                }
            }
            .`when`(userManager)
            .getUserIcon(anyInt())
        underTest = UserIconProvider(context, userManager, testDispatcher)
    }

    @Test
    fun getUserImage_cachingForDifferentUsers() =
        testScope.runTest {
            val image = underTest.getUserImage(USER_ID, /* iconSize= */ 1)
            assertThat(iconFetchCount).isEqualTo(1)
            val cachedImage = underTest.getUserImage(USER_ID, /* iconSize= */ 1)
            assertThat(iconFetchCount).isEqualTo(1)
            assertThat((image as BitmapDrawable).bitmap)
                .isSameInstanceAs((cachedImage as BitmapDrawable).bitmap)

            underTest.getUserImage(OTHER_USER_ID, /* iconSize= */ 1)
            assertThat(iconFetchCount).isEqualTo(2)
        }

    @Test
    fun getUserImage_cachingForDifferentSizes() =
        testScope.runTest {
            val image = underTest.getUserImage(USER_ID, /* iconSize= */ 1)
            assertThat(iconFetchCount).isEqualTo(1)

            val resizedImage = underTest.getUserImage(USER_ID, /* iconSize= */ 5)
            assertThat(iconFetchCount).isEqualTo(2)
            assertThat((image as BitmapDrawable).bitmap)
                .isNotSameInstanceAs((resizedImage as BitmapDrawable).bitmap)
        }

    @Test
    fun clearCacheForUser() =
        testScope.runTest {
            val firstUserImage = underTest.getUserImage(USER_ID, /* iconSize= */ 1)
            assertThat(iconFetchCount).isEqualTo(1)

            val secondUserImage = underTest.getUserImage(OTHER_USER_ID, /* iconSize= */ 1)
            assertThat(iconFetchCount).isEqualTo(2)
            underTest.clearCacheForUser(USER_ID)
            // Cache for the first user was cleared, now we expect a different instance of the image
            val newFirstUserImage = underTest.getUserImage(USER_ID, /* iconSize= */ 1)
            assertThat(iconFetchCount).isEqualTo(3)
            assertThat(newFirstUserImage).isNotSameInstanceAs(firstUserImage)
            // Clearing the cache for the first user shouldn't have affected other users - check
            // that we can get cached image for the second user
            val secondUserCachedImage = underTest.getUserImage(OTHER_USER_ID, /* iconSize= */ 1)
            assertThat(iconFetchCount).isEqualTo(3)
            assertThat((secondUserImage as BitmapDrawable).bitmap)
                .isSameInstanceAs((secondUserCachedImage as BitmapDrawable).bitmap)
        }

    companion object {
        private const val USER_ID = 10
        private val USER_IMAGE = Bitmap.createBitmap(/* width= */ 10, /* height= */ 10, Bitmap.Config.ARGB_8888)

        private const val OTHER_USER_ID = 11
        private val OTHER_USER_IMAGE = Bitmap.createBitmap(/* width= */ 10,/* height= */ 10, Bitmap.Config.ARGB_8888)
    }
}
