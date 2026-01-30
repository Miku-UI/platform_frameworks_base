/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.statusbar.pipeline.mobile.ui

import android.view.View
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.LogLevel
import com.android.systemui.statusbar.pipeline.dagger.VerboseMobileViewLog
import com.android.systemui.statusbar.pipeline.mobile.domain.model.SignalIconModel
import com.android.systemui.statusbar.pipeline.mobile.ui.MobileViewLogger.Companion.getIdForLogging
import javax.inject.Inject

/**
 * Logs for **verbose** changes with the new mobile views.
 *
 * This is a hopefully temporary log until we resolve some open bugs (b/267236367, b/269565345,
 * b/270300839).
 */
@SysUISingleton
class VerboseMobileViewLogger
@Inject
constructor(@VerboseMobileViewLog private val buffer: LogBuffer) {
    fun logBinderReceivedVisibility(parentView: View, subId: Int, visibility: Boolean) {
        buffer.log(
            TAG,
            LogLevel.VERBOSE,
            {
                str1 = parentView.getIdForLogging()
                int1 = subId
                bool1 = visibility
            },
            { "Binder[subId=$int1, viewId=$str1] received visibility: $bool1" },
        )
    }

    fun logBinderSignalIconResult(parentView: View, subId: Int, unpackedLevel: Int) {
        buffer.log(
            TAG,
            LogLevel.VERBOSE,
            {
                str1 = parentView.getIdForLogging()
                int1 = subId
                int2 = unpackedLevel
            },
            { "Binder[subId=$int1, viewId=$str1] SignalDrawable used $int2 for drawable level" },
        )
    }

    fun logBinderReceivedSignalCellularIcon(
        parentView: View,
        subId: Int,
        icon: SignalIconModel.Cellular,
        packedSignalDrawableState: Int,
        shouldRequestLayout: Boolean,
    ) {
        buffer.log(
            TAG,
            LogLevel.VERBOSE,
            {
                str1 = parentView.getIdForLogging()
                int1 = subId
                int2 = icon.level
                long1 = icon.numberOfLevels.toLong()
                // See SettingsLib/res/drawable/ic_mobile_level_list.xml for how the
                // SignalDrawableState level is used for rendering.
                long2 = packedSignalDrawableState.toLong()
                bool1 = icon.showExclamationMark
                bool2 = shouldRequestLayout
            },
            {
                "Binder[subId=$int1, viewId=$str1] received new signal icon (cellular): " +
                    "level=$int2 numLevels=$long1 showExclamation=$bool1 " +
                    "packedDrawableLevel=$long2 shouldRequestLayout=$bool2"
            },
        )
    }

    fun logBinderReceivedSignalSatelliteIcon(
        parentView: View,
        subId: Int,
        icon: SignalIconModel.Satellite,
    ) {
        buffer.log(
            TAG,
            LogLevel.VERBOSE,
            {
                str1 = parentView.getIdForLogging()
                int1 = subId
                int2 = icon.level
            },
            {
                "Binder[subId=$int1, viewId=$str1] received new signal icon (satellite): " +
                    "level=$int2"
            },
        )
    }

    fun logBinderReceivedNetworkTypeIcon(parentView: View, subId: Int, icon: Icon.Resource?) {
        buffer.log(
            TAG,
            LogLevel.VERBOSE,
            {
                str1 = parentView.getIdForLogging()
                int1 = subId
                bool1 = icon != null
                int2 = icon?.resId ?: -1
            },
            {
                "Binder[subId=$int1, viewId=$str1] received new network type icon: " +
                    if (bool1) "resId=$int2" else "null"
            },
        )
    }
}

private const val TAG = "VerboseMobileViewLogger"
