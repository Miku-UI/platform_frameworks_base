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

package com.android.systemui.screencapture.common.ui.compose

import android.content.testableContext
import android.graphics.drawable.TestStubDrawable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.screencapture.common.ui.viewmodel.drawableLoaderViewModel
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class LoadIconTest : SysuiTestCase() {

    @get:Rule val composeTestRule = createComposeRule()

    private val kosmos = testKosmosNew()
    private val testDrawable1 = TestStubDrawable("1")
    private val testDrawable2 = TestStubDrawable("2")

    @Before
    fun setUp() {
        overrideResource(1, testDrawable1)
        overrideResource(2, testDrawable2)
    }

    @Test
    fun testLoadingIcon() =
        with(kosmos) {
            val loadedIcons = mutableListOf<Icon.Loaded?>()
            composeTestRule.setContent {
                CompositionLocalProvider(LocalContext provides testableContext) {
                    for (resId in listOf(1, 2)) {
                        val icon by
                            loadIcon(
                                viewModel = drawableLoaderViewModel,
                                resId = resId,
                                contentDescription = null,
                            )

                        SideEffect { loadedIcons.add(icon) }
                    }
                }
            }

            composeTestRule.waitForIdle()

            assertThat(loadedIcons.size).isEqualTo(2)
            assertThat(loadedIcons[0]!!.resId).isEqualTo(1)
            assertThat(loadedIcons[0]!!.drawable).isEqualTo(testDrawable1)
            assertThat(loadedIcons[1]!!.resId).isEqualTo(2)
            assertThat(loadedIcons[1]!!.drawable).isEqualTo(testDrawable2)
        }
}
