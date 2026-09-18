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

package com.android.systemui.scene.ui.composable

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.res.R
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.atMostOnce
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(JUnit4::class)
class SceneContainerPreloadedResourcesTest : SysuiTestCase() {

    @get:Rule val composeTestRule = createComposeRule()

    @Composable
    private fun ContentUnderTest(
        key: Any,
        onRecomposition: () -> Unit,
        onFullWidthChange: (Boolean) -> Unit,
    ) {
        val preloadedResources = LocalSceneContainerPreloadedResources.current
        SideEffect {
            onRecomposition()
            onFullWidthChange(preloadedResources.isFullWidthShade)
        }
    }

    @Test
    fun rememberSceneContainerPreloadedResources_doesNotRereadFromResources() {
        var key: Int by mutableStateOf(0)
        var recompositionCount = 0
        val resources = createMockResources()

        composeTestRule.setContent {
            WithTestResources(resources) {
                WithSceneContainerPreloadedResources {
                    ContentUnderTest(
                        key = key,
                        onRecomposition = { recompositionCount++ },
                        onFullWidthChange = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        assertThat(recompositionCount).isEqualTo(1)
        verify(resources, atMostOnce()).getBoolean(R.bool.config_isFullWidthShade)

        // Changing the key should cause another recomposition but the number of reads on Resources
        // should remain the same as before.
        key++
        composeTestRule.waitForIdle()
        assertThat(recompositionCount).isEqualTo(2)
        verify(resources, atMostOnce()).getBoolean(anyInt())
    }

    @Test
    fun rememberSceneContainerPreloadedResources_rereadsWhenResourcesChange() {
        var recompositionCount = 0
        var resources: Resources by mutableStateOf(createMockResources(isFullWidth = false))
        var isFullWidth: Boolean? = null

        composeTestRule.setContent {
            WithTestResources(resources) {
                WithSceneContainerPreloadedResources {
                    ContentUnderTest(
                        key = 0,
                        onRecomposition = { recompositionCount++ },
                        onFullWidthChange = { isFullWidth = it },
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        assertThat(isFullWidth).isFalse()

        // Changing the Resources should trigger another read of the resource.
        resources = createMockResources(isFullWidth = true)
        composeTestRule.waitForIdle()
        assertThat(isFullWidth).isTrue()
    }

    private fun createMockResources(isFullWidth: Boolean = false): Resources {
        val resources = mock(Resources::class.java)
        whenever(resources.getBoolean(R.bool.config_isFullWidthShade)).thenReturn(isFullWidth)
        whenever(resources.configuration).thenReturn(Configuration())
        return resources
    }

    @Composable
    private fun WithTestResources(resources: Resources, content: @Composable () -> Unit) {
        val context = mock(Context::class.java)
        whenever(context.resources).thenReturn(resources)
        whenever(context.applicationContext).thenReturn(context)

        androidx.compose.runtime.CompositionLocalProvider(
            LocalContext provides context,
            LocalResources provides resources,
            LocalInspectionMode provides false,
            content = content,
        )
    }
}
