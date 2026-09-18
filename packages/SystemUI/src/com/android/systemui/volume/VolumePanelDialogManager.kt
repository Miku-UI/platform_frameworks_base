/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.volume

import android.content.Context
import android.util.Log
import android.view.View
import com.android.settingslib.bluetooth.LocalBluetoothManager
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.phone.SystemUIDialog
import javax.inject.Inject

private const val TAG = "VolumePanelFactory"
private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)

/**
 * Factory to create [VolumePanelDialogDelegate] objects. This is the dialog that allows the user to
 * adjust multiple streams with sliders.
 */
@SysUISingleton
class VolumePanelDialogManager
@Inject
constructor(
    private val context: Context,
    private val dialogTransitionAnimator: DialogTransitionAnimator,
    private val volumPanelDialogDelegateFactory: VolumePanelDialogDelegate.Factory,
) {
    companion object {
        var dialog: SystemUIDialog? = null
    }

    /**
     * Creates a [VolumePanelDialogDelegate]. The dialog will be animated from [view] if it is not
     * null.
     */
    fun create(aboveStatusBar: Boolean, view: View? = null) {
        if (dialog?.isShowing == true) {
            return
        }

        val localBluetoothManager = LocalBluetoothManager.getInstance(context, null)
        dialog =
            volumPanelDialogDelegateFactory
                .create(localBluetoothManager?.getProfileManager(), aboveStatusBar)
                .createDialog()

        // Show the dialog.
        if (view != null) {
            dialogTransitionAnimator.showFromView(
                dialog!!,
                view,
                animateBackgroundBoundsChange = true,
            )
        } else {
            dialog?.show()
        }
    }

    /** Dismiss [VolumePanelDialogDelegate] if exist. */
    fun dismiss() {
        if (DEBUG) {
            Log.d(TAG, "dismiss dialog")
        }
        dialog?.dismiss()
        dialog = null
    }
}
