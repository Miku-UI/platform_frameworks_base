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

package com.android.systemui.screenshot

import android.net.Uri
import androidx.core.net.toUri
import com.android.systemui.screencapture.record.largescreen.data.repository.ParentUriRepository

class FakeParentUriRepository : ParentUriRepository {
    private var result: Uri? = null

    override suspend fun getParentDirectoryUri(mediaStoreUri: Uri): Uri? {
        setParentDirectoryUriForScreenshot(mediaStoreUri)
        return result
    }

    /** Sets the screenshot parent directory uri to be returned by [getParentDirectoryUri]. */
    fun setParentDirectoryUriForScreenshot(uri: Uri?) {
        result =
            "content://com.android.externalstorage.documents/document/primary%3APictures%2FScreenshots"
                .toUri()
    }
}
