/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.emptyshade.ui.view

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class EmptyShadeIconViewTest : SysuiTestCase() {

    private lateinit var emptyShadeIconView: EmptyShadeIconView

    @Before
    fun setUp() {
        emptyShadeIconView = EmptyShadeIconView(context, null)
    }

    @Test
    fun testApplyToView_correctType_setContentVisibleAnimatedCalled() {
        val mockView = mock(EmptyShadeIconView::class.java)
        whenever(mockView.isVisible).thenReturn(true)
        val viewState = emptyShadeIconView.EmptyShadeViewState()

        viewState.applyToView(mockView)

        verify(mockView).setContentVisibleAnimated(true)
    }

    @Test
    fun testApplyToView_incorrectType_setContentVisibleAnimatedNotCalled() {
        val mockView = mock(EmptyShadeView::class.java)
        whenever(mockView.isVisible).thenReturn(true)
        val viewState = emptyShadeIconView.EmptyShadeViewState()

        viewState.applyToView(mockView)

        verify(mockView, never()).setContentVisibleAnimated(true)
    }
}
