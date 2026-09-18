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

package com.android.systemui.animation

import android.content.ComponentName
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper.RunWithLooper
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
@RunWithLooper
class ExpandableTest : SysuiTestCase() {

    private lateinit var expandable: Expandable
    private lateinit var source1: TransitionSource
    private lateinit var source2: TransitionSource

    @Before
    fun setUp() {
        source1 = mock()
        source2 = mock()
        expandable = Expandable()
    }

    @Test
    fun addSource_setsVisibilityAndCallback() {
        expandable.addSource(source1)

        verify(source1).isSourceVisible = true
        verify(source1).onSourceVisibilityChanged = any()
        assertThat(expandable.transitionSources).contains(source1)
    }

    @Test
    fun removeSource_removesFromSet() {
        expandable.addSource(source1)
        expandable.removeSource(source1)

        assertThat(expandable.transitionSources).doesNotContain(source1)
    }

    @Test
    fun updateSourceVisibility_updatesAllSources() {
        expandable.addSource(source1)
        expandable.addSource(source2)

        // Find the callback that was registered on source1 (it's the same for all sources)
        val callbackCapture = argumentCaptor<(Boolean) -> Unit>()
        verify(source1).onSourceVisibilityChanged = callbackCapture.capture()
        val callback = callbackCapture.firstValue

        // Trigger callback
        callback.invoke(false)

        verify(source1).isSourceVisible = false
        verify(source2).isSourceVisible = false
    }

    @Test
    fun dialogTransitionController_returnsWrappedController() {
        val mockController = mock<DialogTransitionAnimator.Controller>()
        whenever(source1.dialogTransitionController(anyOrNull())).thenReturn(mockController)
        expandable.addSource(source1)

        val controller = expandable.dialogTransitionController()

        assertThat(controller).isNotNull()
        assertThat(controller?.dialogIdentity).isSameInstanceAs(expandable)
    }

    @Test
    @EnableFlags(Flags.FLAG_ANIMATION_LIBRARY_DYNAMIC_TARGET_RESOLUTION)
    fun activityTransitionController_returnsControllerFromSource() {
        val mockController = mock<ActivityTransitionAnimator.Controller>()
        whenever(
                source1.activityTransitionController(
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull(),
                )
            )
            .thenReturn(mockController)
        expandable.addSource(source1)

        val controller = expandable.activityTransitionController()

        assertThat(controller).isSameInstanceAs(mockController)
    }

    @Test
    fun fromView_createsExpandableWithSource() {
        val view = View(context)
        val expandableFromView = Expandable.fromView(view)

        assertThat(expandableFromView.transitionSources).hasSize(1)
    }

    @Test
    @DisableFlags(Flags.FLAG_ANIMATION_LIBRARY_DYNAMIC_TARGET_RESOLUTION)
    fun requiredTransitionSource_throwsIfEmptyAndFlagDisabled() {
        val emptyExpandable = Expandable()

        // dialogTransitionController calls requiredTransitionSource internally
        try {
            emptyExpandable.dialogTransitionController()
            assertThat(false).isTrue() // Should have thrown
        } catch (e: IllegalStateException) {
            assertThat(e.message).contains("No TransitionSource found")
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_ANIMATION_LIBRARY_DYNAMIC_TARGET_RESOLUTION)
    fun requiredTransitionSource_returnsNullIfEmptyAndFlagEnabled() {
        val emptyExpandable = Expandable()

        assertThat(emptyExpandable.dialogTransitionController()).isNull()
    }

    @Test
    fun activityTransitionController_withParams_callsSourceWithSameParams() {
        val mockController = mock<ActivityTransitionAnimator.Controller>()
        val launchCujType = 10
        val cookie = mock<ActivityTransitionAnimator.TransitionCookie>()
        val component = ComponentName("pkg", "cls")
        val returnCujType = 20
        val isEphemeral = false

        whenever(
                source1.activityTransitionController(
                    eq(launchCujType),
                    eq(cookie),
                    eq(component),
                    eq(returnCujType),
                    eq(isEphemeral),
                )
            )
            .thenReturn(mockController)

        expandable.addSource(source1)
        val controller =
            expandable.activityTransitionController(
                launchCujType,
                cookie,
                component,
                returnCujType,
                isEphemeral,
            )

        assertThat(controller).isSameInstanceAs(mockController)
    }
}
