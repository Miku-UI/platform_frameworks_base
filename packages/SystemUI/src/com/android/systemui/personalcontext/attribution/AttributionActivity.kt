/*
 * Copyright 2026 The Android Open Source Project
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

package com.android.systemui.personalcontext.attribution

import android.os.Bundle
import android.service.personalcontext.insight.interaction.AttributionDetails
import android.util.Log
import android.view.Gravity
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.platform.ComposeView
import com.android.systemui.res.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import javax.inject.Inject

class AttributionActivity @Inject internal constructor() : ComponentActivity() {

    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val attributionDetails: AttributionDetails? =
            intent.getParcelableExtra(EXTRA_KEY_ATTRIBUTION_DETAILS, AttributionDetails::class.java)
        if (attributionDetails == null) {
            Log.e(TAG, "AttributionDetails not specified. Finishing activity")
            finish()
        }
        val view =
            ComposeView(this).apply {
                setContent { AttributionDialogComposable(attributionDetails!!) }
            }
        showDialog(view)
    }

    private fun showDialog(view: View) {
        dialog =
            // Use an AlertDialog to keep the keyboard visible when the activity is launched.
            MaterialAlertDialogBuilder(this, R.style.Theme_AttributionDialog)
                .apply {
                    setView(view)
                    setOnDismissListener { finish() }
                }
                .create()
                .apply {
                    val gravity = Gravity.CENTER
                    window?.setGravity(gravity)
                    show()
                }
    }

    companion object {
        const val EXTRA_KEY_ATTRIBUTION_DETAILS = "EXTRA_KEY_ATTRIBUTION_DETAILS"
        const val TAG = "AttributionActivity"
    }
}
