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
package com.android.systemui.lowlightclock

import android.service.dreams.DreamService
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class DreamServiceDelegateImplTest : SysuiTestCase() {
    private val dreamService: DreamService = mock()
    private val mockView: View = mock()
    private val layoutInflater: LayoutInflater = mock {
        on { inflate(any<Int>(), anyOrNull<ViewGroup>()) }.thenReturn(mockView)
    }

    private val underTest = DreamServiceDelegateImpl(dreamService, layoutInflater)

    @Test
    fun testIsInteractive() {
        whenever(dreamService.isInteractive).thenReturn(true)
        assertThat(underTest.isInteractive).isTrue()
        underTest.isInteractive = false
        verify(dreamService).isInteractive = false
    }

    @Test
    fun testIsFullscreen() {
        whenever(dreamService.isFullscreen).thenReturn(true)
        assertThat(underTest.isFullscreen).isTrue()
        underTest.isFullscreen = false
        verify(dreamService).isFullscreen = false
    }

    @Test
    fun testSetContentView() {
        val layoutId = 123
        underTest.setContentView(layoutId)
        verify(layoutInflater).inflate(layoutId, null)
        verify(dreamService).setContentView(mockView)
    }

    @Test
    fun testFindViewById() {
        val id = 456
        val view: View = mock()
        whenever(dreamService.findViewById<View>(id)).thenReturn(view)
        assertThat(underTest.findViewById<View>(id)).isEqualTo(view)
    }

    @Test
    fun testSetScreenBrightness() {
        val brightness = 0.5f
        underTest.setScreenBrightness(brightness)
        verify(dreamService).setScreenBrightness(brightness)
    }

    @Test
    fun testWakeUp() {
        underTest.onWakeUp()
        verify(dreamService).onWakeUp()
    }

    @Test
    fun testFinish() {
        underTest.finish()
        verify(dreamService).finish()
    }
}
