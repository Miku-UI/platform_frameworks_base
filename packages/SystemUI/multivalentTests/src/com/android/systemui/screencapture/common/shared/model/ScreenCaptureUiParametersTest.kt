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
package com.android.systemui.screencapture.common.shared.model

import android.os.Parcel
import android.os.UserHandle
import androidx.core.os.use
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters.Record.LargeScreenCaptureUiParameters
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureRegion
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureType
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ScreenCaptureUiParametersTest : SysuiTestCase() {

    @Test
    fun parcelAndUnparcel_record_withNullParameters_isEqual() {
        val original = ScreenCaptureUiParameters.Record(null)
        val parceled = writeAndReadParcel(original)
        assertThat(parceled).isEqualTo(original)
    }

    @Test
    fun parcelAndUnparcel_record_withParameters_isEqual() {
        val largeScreenParameters =
            LargeScreenCaptureUiParameters(
                defaultCaptureType = ScreenCaptureType.SCREENSHOT,
                defaultCaptureRegion = ScreenCaptureRegion.PARTIAL,
            )
        val original = ScreenCaptureUiParameters.Record(largeScreenParameters)
        val parceled = writeAndReadParcel(original)
        assertThat(parceled).isEqualTo(original)
    }

    @Test
    fun parcelAndUnparcel_cast_isEqual() {
        val original = ScreenCaptureUiParameters.Cast
        val parceled = writeAndReadParcel(original)
        assertThat(parceled).isEqualTo(original)
    }

    @Test
    fun parcelAndUnparcel_shareScreen_isEqual() {
        val original = ScreenCaptureUiParameters.ShareScreen(UserHandle.of(1))
        val parceled = writeAndReadParcel(original)
        assertThat(parceled).isEqualTo(original)
    }

    private fun writeAndReadParcel(
        original: ScreenCaptureUiParameters
    ): ScreenCaptureUiParameters? {
        return Parcel.obtain().use {
            original.writeToParcel(it, 0)
            it.setDataPosition(0)
            ScreenCaptureUiParameters.CREATOR.createFromParcel(it)
        }
    }
}
