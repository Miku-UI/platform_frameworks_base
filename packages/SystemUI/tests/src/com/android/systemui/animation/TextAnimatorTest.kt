/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.animation.TextInterpolatorTest.Companion.makeLayout
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class TextAnimatorTest : SysuiTestCase() {
    private val typeface = Typeface.createFromFile("/system/fonts/Roboto-Regular.ttf")

    val layout = makeLayout("Hello, World", PAINT)
    val paint = spy(PAINT)
    val valueAnimator = mock<ValueAnimator>()
    val textInterpolator = mock<TextInterpolator> { whenever(mock.targetPaint).thenReturn(paint) }
    val textAnimator =
        TextAnimator(layout, TypefaceVariantCacheImpl(typeface, 20)).apply {
            textInterpolator = this@TextAnimatorTest.textInterpolator
            createAnimator = { valueAnimator }
            animator = valueAnimator
        }

    @Test
    fun testAnimationStarted() {
        textAnimator.setTextStyle(TextAnimator.Style("'wght' 400"), TextAnimator.Animation())

        // If animation is requested, the base state should be rebased and the target state should
        // be updated.
        val order = inOrder(textInterpolator)
        order.verify(textInterpolator).rebase()
        order.verify(textInterpolator).onTargetPaintModified()

        // In case of animation, should not shape the base state since the animation should start
        // from current state.
        verify(textInterpolator, never()).onBasePaintModified()

        // Then, animation should be started.
        verify(valueAnimator, times(1)).start()
    }

    @Test
    fun testAnimationNotStarted() {
        textAnimator.setTextStyle(TextAnimator.Style("'wght' 400"))

        // If animation is not requested, the progress should be 1 which is end of animation and the
        // base state is rebased to target state by calling rebase.
        val order = inOrder(textInterpolator)
        order.verify(textInterpolator).onTargetPaintModified()
        order.verify(textInterpolator).progress = 1f
        order.verify(textInterpolator).rebase()

        // Then, animation start should not be called.
        verify(valueAnimator, never()).start()
    }

    @Test
    fun testAnimationEnded() {
        val animationEndCallback = mock<Runnable>()

        textAnimator.setTextStyle(
            TextAnimator.Style("'wght' 400"),
            TextAnimator.Animation(animate = true, onAnimationEnd = animationEndCallback),
        )

        // Verify animationEnd callback has been added.
        val captor = argumentCaptor<AnimatorListenerAdapter>()
        verify(valueAnimator, times(2)).addListener(captor.capture())
        for (callback in captor.allValues) {
            callback.onAnimationEnd(valueAnimator)
        }

        // Verify animationEnd callback has been invoked and removed.
        verify(animationEndCallback).run()
    }

    @Test
    fun testCacheTypeface() {
        val animation = TextAnimator.Animation(animate = true)
        textAnimator.setTextStyle(TextAnimator.Style("'wght' 400"), animation)

        val prevTypeface = paint.typeface

        textAnimator.setTextStyle(TextAnimator.Style("'wght' 700"), animation)

        assertThat(paint.typeface).isNotSameInstanceAs(prevTypeface)

        textAnimator.setTextStyle(TextAnimator.Style("'wght' 400"), animation)

        assertThat(paint.typeface).isSameInstanceAs(prevTypeface)
    }
}
