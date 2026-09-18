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

package com.android.wm.shell.packageupdate

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import com.android.wm.shell.R
import com.google.android.material.progressindicator.CircularProgressIndicator

/**
 * Placeholder activity used during package updates. This activity will get launched in a task going
 * through a package update if the task is visible.
 */
class PackageUpdateActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.package_update_activity)
        setupStaticProgressIndicator()
        createIcon(intent)
        setWindowTitle(intent)
    }

    private fun setupStaticProgressIndicator() {
        val animatorScale =
            Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f)

        if (animatorScale != 0f) return

        val progressIndicator =
            requireViewById<CircularProgressIndicator>(R.id.circular_progress_indicator)
        progressIndicator.isIndeterminate = false
        progressIndicator.progress = 70
    }

    private fun createIcon(intent: Intent) {
        val bitmap = intent.getParcelableExtra(ICON, Bitmap::class.java)
        if (bitmap != null) {
            val iconView = requireViewById<ImageView>(R.id.veil_application_icon)
            iconView.setImageBitmap(bitmap)
            iconView.contentDescription = null
        }
    }

    private fun setWindowTitle(intent: Intent) {
        val updatingAppName = intent.getParcelableExtra(UPDATING_APP, CharSequence::class.java)
        title = "${updatingAppName ?: "SystemUI"}${getString(R.string.package_update_text)}"
    }

    companion object {
        private const val ICON = "icon"
        private const val UPDATING_APP = "updating app"
        private const val SYSTEM_UI_PACKAGE_NAME = "com.android.systemui"

        /** Creates an intent to launch [PackageUpdateActivity]. */
        fun createIntent(
            userContext: Context,
            userId: Int,
            taskId: Int,
            icon: Bitmap?,
            updatingAppName: CharSequence?,
        ): Intent {
            val intent = Intent(userContext, PackageUpdateActivity::class.java)

            // Add a unique data URI to distinguish PendingIntents for different tasks.
            intent.setData("packageupdate://task/$taskId".toUri())
            intent.putExtra(Intent.EXTRA_USER_HANDLE, userId)
            intent.putExtra(ICON, icon)
            intent.putExtra(UPDATING_APP, updatingAppName)

            return intent
        }

        @JvmStatic
        val activityComponent =
            ComponentName(SYSTEM_UI_PACKAGE_NAME, PackageUpdateActivity::class.java.name)

        @JvmStatic
        fun isPackageUpdateActivityComponent(component: ComponentName?) =
            component == activityComponent
    }
}
