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

package com.android.systemui.sensorprivacy

import android.content.DialogInterface
import android.content.DialogInterface.BUTTON_NEGATIVE
import android.content.DialogInterface.BUTTON_POSITIVE
import android.content.res.Resources
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.android.internal.widget.DialogTitle
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.SystemUIDialog
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

class SensorUseDialogDelegate
@AssistedInject
constructor(
    private val layoutInflater: LayoutInflater,
    private val dialogFactory: SystemUIDialog.Factory,
    @Assisted val sensor: Int,
    @Assisted val clickListener: DialogInterface.OnClickListener,
    @Assisted val dismissListener: DialogInterface.OnDismissListener,
) : SystemUIDialog.Delegate {

    @AssistedFactory
    interface Factory {
        fun create(
            sensor: Int,
            clickListener: DialogInterface.OnClickListener,
            dismissListener: DialogInterface.OnDismissListener,
        ): SensorUseDialogDelegate
    }

    override fun createDialog(): SystemUIDialog? {
        val dialog = dialogFactory.create(this)
        dialog.window!!.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        dialog.window!!.addSystemFlags(
            WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS
        )

        val customTitleView = layoutInflater.inflate(R.layout.sensor_use_started_title, null)
        customTitleView
            .requireViewById<DialogTitle>(R.id.sensor_use_started_title_message)
            .setText(
                when (sensor) {
                    SensorUseStartedActivity.MICROPHONE ->
                        R.string.sensor_privacy_start_use_mic_dialog_title
                    SensorUseStartedActivity.CAMERA ->
                        R.string.sensor_privacy_start_use_camera_dialog_title
                    SensorUseStartedActivity.ALL_SENSORS ->
                        R.string.sensor_privacy_start_use_mic_camera_dialog_title
                    else -> Resources.ID_NULL
                }
            )
        customTitleView.requireViewById<ImageView>(R.id.sensor_use_microphone_icon).visibility =
            if (
                sensor == SensorUseStartedActivity.MICROPHONE ||
                    sensor == SensorUseStartedActivity.ALL_SENSORS
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }
        customTitleView.requireViewById<ImageView>(R.id.sensor_use_camera_icon).visibility =
            if (
                sensor == SensorUseStartedActivity.CAMERA ||
                    sensor == SensorUseStartedActivity.ALL_SENSORS
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }

        with(dialog) {
            setCustomTitle(customTitleView)
            setMessage(
                Html.fromHtml(
                    context.getString(
                        when (sensor) {
                            SensorUseStartedActivity.MICROPHONE ->
                                R.string.sensor_privacy_start_use_mic_dialog_content

                            SensorUseStartedActivity.CAMERA ->
                                R.string.sensor_privacy_start_use_camera_dialog_content

                            SensorUseStartedActivity.ALL_SENSORS ->
                                R.string.sensor_privacy_start_use_mic_camera_dialog_content

                            else -> Resources.ID_NULL
                        }
                    ),
                    0,
                )
            )

            setButton(
                BUTTON_POSITIVE,
                context.getString(
                    com.android.internal.R.string.sensor_privacy_start_use_dialog_turn_on_button
                ),
                clickListener,
            )
            setButton(
                BUTTON_NEGATIVE,
                context.getString(com.android.internal.R.string.cancel),
                clickListener,
            )

            setOnDismissListener(dismissListener)

            setCancelable(false)
        }

        return dialog
    }
}
