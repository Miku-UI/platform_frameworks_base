/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.media.controls.shared

import com.android.internal.logging.InstanceId
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.LogLevel
import com.android.systemui.log.dagger.MediaLog
import javax.inject.Inject

interface MediaLogger {
    fun logMediaNotificationEnteredPipeline(packageName: String, title: CharSequence?)

    fun logLoadingMediaDataCanceled(key: String)

    fun logMediaNotificationExitedPipeline(packageName: String, title: CharSequence?)

    fun logMediaLoaded(instanceId: InstanceId, active: Boolean, reason: String)

    fun logMediaRemoved(instanceId: InstanceId, reason: String)

    fun logMediaCarouselSize(size: Int)

    fun logDuplicateMediaNotification(key: String)

    fun logMedia3UnsupportedCommand(command: String)

    fun logCreateFailed(pkg: String, method: String)

    fun logReleaseFailed(pkg: String, cause: String)
}

/** A buffered log for media loading events. */
@SysUISingleton
class MediaLoggerImpl @Inject constructor(@MediaLog private val buffer: LogBuffer) : MediaLogger {

    override fun logMediaNotificationEnteredPipeline(packageName: String, title: CharSequence?) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                str1 = packageName
                str2 = title.toString()
            },
            { "media notification entered pipeline, packageName: $str1, title: $str2" },
        )
    }

    override fun logLoadingMediaDataCanceled(key: String) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            { str1 = key },
            { "loading media data is canceled, key: $str1" },
        )
    }

    override fun logMediaNotificationExitedPipeline(packageName: String, title: CharSequence?) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                str1 = packageName
                str2 = title.toString()
            },
            { "media notification exited pipeline, packageName: $str1, title: $str2" },
        )
    }

    override fun logMediaLoaded(instanceId: InstanceId, active: Boolean, reason: String) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                str1 = instanceId.toString()
                bool1 = active
                str2 = reason
            },
            { "add media $str1, active: $bool1, reason: $str2" },
        )
    }

    override fun logMediaRemoved(instanceId: InstanceId, reason: String) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                str1 = instanceId.toString()
                str2 = reason
            },
            { "removing media $str1, reason: $str2" },
        )
    }

    override fun logMediaCarouselSize(size: Int) {
        buffer.log(TAG, LogLevel.DEBUG, { int1 = size }, { "media carousel size: $int1 " })
    }

    override fun logDuplicateMediaNotification(key: String) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            { str1 = key },
            { "duplicate media notification $str1 posted" },
        )
    }

    override fun logMedia3UnsupportedCommand(command: String) {
        buffer.log(TAG, LogLevel.DEBUG, { str1 = command }, { "Unsupported media3 command $str1" })
    }

    override fun logCreateFailed(pkg: String, method: String) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                str1 = pkg
                str2 = method
            },
            { "Controller create failed for $str1 ($str2)" },
        )
    }

    override fun logReleaseFailed(pkg: String, cause: String) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                str1 = pkg
                str2 = cause
            },
            { "Controller release failed for $str1 ($str2)" },
        )
    }

    companion object {
        private const val TAG = "MediaLog"
    }
}
