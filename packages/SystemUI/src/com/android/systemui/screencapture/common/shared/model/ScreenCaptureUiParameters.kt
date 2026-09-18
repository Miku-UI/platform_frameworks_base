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
import android.os.Parcelable
import android.os.UserHandle
import androidx.annotation.CallSuper
import com.android.systemui.util.getParcelable
import com.android.systemui.util.getSerializable
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureRegion as LargeScreenCaptureRegion
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureType as LargeScreenCaptureType

sealed interface ScreenCaptureUiParameters : Parcelable {

    val screenCaptureType: ScreenCaptureType

    /** Record screen content to the local device. */
    data class Record(val largeScreenParameters: LargeScreenCaptureUiParameters? = null) :
        ScreenCaptureUiParameters {

        constructor(parcel: Parcel) : this(parcel.getParcelable<LargeScreenCaptureUiParameters>())

        override val screenCaptureType: ScreenCaptureType = ScreenCaptureType.RECORD

        data class LargeScreenCaptureUiParameters(
            val defaultCaptureType: LargeScreenCaptureType? = null,
            val defaultCaptureRegion: LargeScreenCaptureRegion? = null,
        ) : Parcelable {
            override fun describeContents(): Int = 0

            override fun writeToParcel(dest: Parcel, flags: Int) =
                with(dest) {
                    writeSerializable(defaultCaptureType)
                    writeSerializable(defaultCaptureRegion)
                }

            companion object {
                @JvmField
                val CREATOR =
                    object : Parcelable.Creator<LargeScreenCaptureUiParameters> {
                        override fun createFromParcel(
                            source: Parcel
                        ): LargeScreenCaptureUiParameters {
                            return LargeScreenCaptureUiParameters(
                                defaultCaptureType = source.getSerializable(),
                                defaultCaptureRegion = source.getSerializable(),
                            )
                        }

                        override fun newArray(size: Int): Array<LargeScreenCaptureUiParameters?> =
                            arrayOfNulls(size)
                    }
            }
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeParcelable(largeScreenParameters, flags)
        }
    }

    /** Cast screen content to a remote device. */
    data object Cast : ScreenCaptureUiParameters {

        override val screenCaptureType: ScreenCaptureType = ScreenCaptureType.CAST
    }

    /** Share screen content to a local app. */
    data class ShareScreen(val hostAppUserHandle: UserHandle) : ScreenCaptureUiParameters {

        constructor(parcel: Parcel) : this(parcel.getParcelable<UserHandle>()!!)

        override val screenCaptureType: ScreenCaptureType = ScreenCaptureType.SHARE_SCREEN

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) {
            super.writeToParcel(dest, flags)
            dest.writeParcelable(hostAppUserHandle, flags)
        }
    }

    @CallSuper
    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeSerializable(screenCaptureType)
    }

    override fun describeContents(): Int = 0

    companion object {
        @JvmField
        val CREATOR =
            object : Parcelable.Creator<ScreenCaptureUiParameters> {
                override fun createFromParcel(source: Parcel): ScreenCaptureUiParameters? {
                    val type: ScreenCaptureType = source.getSerializable() ?: return null
                    return when (type) {
                        ScreenCaptureType.SHARE_SCREEN -> ShareScreen(source)
                        ScreenCaptureType.RECORD -> Record(source)
                        ScreenCaptureType.CAST -> Cast
                    }
                }

                override fun newArray(size: Int): Array<ScreenCaptureUiParameters?> =
                    arrayOfNulls(size)
            }
    }
}
