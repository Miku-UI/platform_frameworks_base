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

package com.android.systemui.screencapture.record.largescreen.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureUiInteractor
import com.android.systemui.screencapture.record.largescreen.domain.interactor.LargeScreenCaptureParametersInteractor
import javax.inject.Inject
import kotlinx.coroutines.launch

class DirectoryPickerActivity
@Inject
constructor(
    private val interactor: LargeScreenCaptureParametersInteractor,
    private val screenCaptureUiInteractor: ScreenCaptureUiInteractor,
) : ComponentActivity() {

    private lateinit var directoryPickerLauncher: ActivityResultLauncher<Uri?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        directoryPickerLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
                if (uri != null) {
                    try {
                        val takeFlags: Int =
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        contentResolver.takePersistableUriPermission(uri, takeFlags)

                        lifecycleScope.launch { interactor.setCustomSaveLocation(uri) }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Failed to take persistable URI permission for $uri", e)
                    }
                }
                finish()
            }

        if (savedInstanceState == null) {
            directoryPickerLauncher.launch(null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        screenCaptureUiInteractor.show(ScreenCaptureUiParameters.Record())
    }

    companion object {
        private const val TAG = "DirectoryPickerActivity"
    }
}
