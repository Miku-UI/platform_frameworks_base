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

package com.android.wm.shell.common.split

import android.view.View
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.split.DividerSnapAlgorithm.SnapTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

/** Tests for {@link DividerResizeMenu}. */
@SmallTest
@RunWith(AndroidJUnit4::class)
class DividerResizeMenuTest : ShellTestCase() {
    @Mock private lateinit var mSplitLayout: SplitLayout
    @Mock private lateinit var mSnapAlgorithm: DividerSnapAlgorithm

    private lateinit var mDividerResizeMenu: DividerResizeMenu

    @Before
    @UiThreadTest
    fun setup() {
        MockitoAnnotations.initMocks(this)
        // Since mDividerSnapAlgorithm is a field, we must set it on the mock instance
        // after initialization.
        mSplitLayout.mDividerSnapAlgorithm = mSnapAlgorithm

        mDividerResizeMenu = DividerResizeMenu(mContext)
        mDividerResizeMenu.setup(mSplitLayout, null)
    }

    @Test
    @UiThreadTest
    fun testShowHide() {
        val prevTarget = mock(SnapTarget::class.java)
        val nextTarget = mock(SnapTarget::class.java)
        `when`(mSnapAlgorithm.snapToPrev(anyInt())).thenReturn(prevTarget)
        `when`(mSnapAlgorithm.snapToNext(anyInt())).thenReturn(nextTarget)
        assertFalse(mDividerResizeMenu.isShowing)

        mDividerResizeMenu.show()
        assertTrue(mDividerResizeMenu.prevButton.visibility == View.VISIBLE)
        assertTrue(mDividerResizeMenu.nextButton.visibility == View.VISIBLE)

        mDividerResizeMenu.setVisible(true) // Force flag for isShowing()
        assertTrue(mDividerResizeMenu.isShowing)

        mDividerResizeMenu.hide()
    }

    @Test
    @UiThreadTest
    fun testSnapToPrev() {
        val mockTarget = mock(SnapTarget::class.java)
        `when`(mSplitLayout.dividerPosition).thenReturn(100)
        `when`(mSnapAlgorithm.snapToPrev(anyInt())).thenReturn(mockTarget)

        mDividerResizeMenu.prevButton.performClick()

        verify(mSplitLayout).snapToTarget(100, mockTarget)
    }

    @Test
    @UiThreadTest
    fun testSnapToNext() {
        val mockTarget = mock(SnapTarget::class.java)
        `when`(mSplitLayout.dividerPosition).thenReturn(100)
        `when`(mSnapAlgorithm.snapToNext(anyInt())).thenReturn(mockTarget)

        mDividerResizeMenu.nextButton.performClick()

        verify(mSplitLayout).snapToTarget(100, mockTarget)
    }

    @Test
    @UiThreadTest
    fun testUpdateButtons_horizontalSplit() {
        `when`(mSplitLayout.isLeftRightSplit).thenReturn(false)
        `when`(mSplitLayout.dividerPosition).thenReturn(500)

        val prevTarget = mock(SnapTarget::class.java)
        val nextTarget = mock(SnapTarget::class.java)
        `when`(mSnapAlgorithm.snapToPrev(anyInt())).thenReturn(prevTarget)
        `when`(mSnapAlgorithm.snapToNext(anyInt())).thenReturn(nextTarget)

        mDividerResizeMenu.updateButtons()

        // In horizontal split, buttons should be above/below (translationY) and centered
        // (translationX = 0)
        assertEquals(0f, mDividerResizeMenu.prevButton.translationX, 0.01f)
        assertTrue(mDividerResizeMenu.prevButton.translationY < 0) // Above

        assertEquals(0f, mDividerResizeMenu.nextButton.translationX, 0.01f)
        assertTrue(mDividerResizeMenu.nextButton.translationY > 0) // Below

        // Check rotations
        assertEquals(90f, mDividerResizeMenu.prevButton.rotation, 0.01f)
        assertEquals(270f, mDividerResizeMenu.nextButton.rotation, 0.01f)
    }

    @Test
    @UiThreadTest
    fun testUpdateButtons_verticalSplit() {
        `when`(mSplitLayout.isLeftRightSplit).thenReturn(true)
        `when`(mSplitLayout.dividerPosition).thenReturn(500)

        val prevTarget = mock(SnapTarget::class.java)
        val nextTarget = mock(SnapTarget::class.java)
        `when`(mSnapAlgorithm.snapToPrev(anyInt())).thenReturn(prevTarget)
        `when`(mSnapAlgorithm.snapToNext(anyInt())).thenReturn(nextTarget)

        mDividerResizeMenu.updateButtons()

        // In vertical split, buttons should be left/right (translationX) and centered (translationY
        // = 0)
        assertTrue(mDividerResizeMenu.prevButton.translationX < 0) // Left
        assertEquals(0f, mDividerResizeMenu.prevButton.translationY, 0.01f)

        assertTrue(mDividerResizeMenu.nextButton.translationX > 0) // Right
        assertEquals(0f, mDividerResizeMenu.nextButton.translationY, 0.01f)

        // Check rotations
        assertEquals(0f, mDividerResizeMenu.prevButton.rotation, 0.01f)
        assertEquals(180f, mDividerResizeMenu.nextButton.rotation, 0.01f)
    }

    @Test
    @UiThreadTest
    fun testUpdateButtons_dismissTargets() {
        `when`(mSplitLayout.isLeftRightSplit).thenReturn(true)
        `when`(mSplitLayout.dividerPosition).thenReturn(500)

        val dismissStartTarget = mock(SnapTarget::class.java)
        val dismissEndTarget = mock(SnapTarget::class.java)
        `when`(mSnapAlgorithm.dismissStartTarget).thenReturn(dismissStartTarget)
        `when`(mSnapAlgorithm.dismissEndTarget).thenReturn(dismissEndTarget)

        // Simulate snapping to the very edge targets
        `when`(mSnapAlgorithm.snapToPrev(anyInt())).thenReturn(dismissStartTarget)
        `when`(mSnapAlgorithm.snapToNext(anyInt())).thenReturn(dismissEndTarget)

        mDividerResizeMenu.updateButtons()

        // Dismiss icons should not be rotated
        assertEquals(0f, mDividerResizeMenu.prevButton.rotation, 0.01f)
        assertEquals(0f, mDividerResizeMenu.nextButton.rotation, 0.01f)
    }
}
