/*
* Copyright 2025 The Android Open Source Project
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

package android.view.input

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.platform.test.annotations.Presubmit
import android.view.MotionEvent
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for [StylusButtonCompatibility].
 *
 * Build/Install/Run:
 * atest FrameworksCoreTests:StylusButtonCompatibilityTest
 */
@SmallTest
@Presubmit
class StylusButtonCompatibilityTest {
    private lateinit var stylusButtonCompatibility: StylusButtonCompatibility
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        stylusButtonCompatibility = StylusButtonCompatibility(context, null)
    }

    @Test
    fun targetSdkMCompatibilityNotNeeded() {
        context.getApplicationInfo().targetSdkVersion = Build.VERSION_CODES.M

        assertFalse(StylusButtonCompatibility.isCompatibilityNeeded(context))
    }

    @Test
    fun targetSdkLCompatibilityNotNeeded() {
        context.getApplicationInfo().targetSdkVersion = Build.VERSION_CODES.LOLLIPOP_MR1

        assertTrue(StylusButtonCompatibility.isCompatibilityNeeded(context))
    }

    @Test
    fun primaryStylusButtonAddsSecondaryButton() {
        val event = MotionEvent.obtain(
            SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
            MotionEvent.ACTION_BUTTON_PRESS, /* x= */ 100f, /* y= */ 200f, /* metaState= */ 0
        )
        event.buttonState = MotionEvent.BUTTON_STYLUS_PRIMARY

        val result = stylusButtonCompatibility.processInputEventForCompatibility(event)

        assertThat(result).hasSize(1)
        assertEquals(
            MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_STYLUS_PRIMARY,
            (result!![0] as MotionEvent).buttonState
        )
    }

    @Test
    fun secondaryStylusButtonAddsTertiaryButton() {
        val event = MotionEvent.obtain(
            SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
            MotionEvent.ACTION_BUTTON_PRESS, /* x= */ 100f, /* y= */ 200f, /* metaState= */ 0
        )
        event.buttonState = MotionEvent.BUTTON_STYLUS_SECONDARY

        val result = stylusButtonCompatibility.processInputEventForCompatibility(event)

        assertThat(result).hasSize(1)
        assertEquals(
            MotionEvent.BUTTON_TERTIARY or MotionEvent.BUTTON_STYLUS_SECONDARY,
            (result!![0] as MotionEvent).buttonState
        )
    }
}
