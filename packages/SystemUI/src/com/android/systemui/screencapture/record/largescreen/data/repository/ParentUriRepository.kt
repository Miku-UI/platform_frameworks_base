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

package com.android.systemui.screencapture.record.largescreen.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext

interface ParentUriRepository {
    suspend fun getParentDirectoryUri(mediaStoreUri: Uri): Uri?
}

/** Repository responsible for deriving the parent directory URI from a MediaStore file URI. */
class ParentUriRepositoryImpl
@Inject
constructor(
    @Application private val context: Context,
    @Background private val backgroundContext: CoroutineContext,
) : ParentUriRepository {
    /**
     * Gets the DocumentsProvider URI for the parent directory of the given MediaStore URI. This
     * method queries the MediaStore for the RELATIVE_PATH on a background thread.
     *
     * @param mediaStoreUri The MediaStore URI of the file (e.g.,
     *   content://media/external_primary/video/media/51).
     * @return The DocumentsProvider URI for the parent directory, or null if it cannot be
     *   determined.
     */
    override suspend fun getParentDirectoryUri(mediaStoreUri: Uri): Uri? {
        return withContext(backgroundContext) {
            val projection = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH)
            try {
                context.contentResolver.query(mediaStoreUri, projection, null, null, null)?.use {
                    cursor ->
                    if (cursor.moveToFirst()) {
                        val relativePathColumnIndex =
                            cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                        val relativePath = cursor.getString(relativePathColumnIndex)
                        if (relativePath != null) {
                            val cleanPath = relativePath.trimEnd('/')
                            val documentId = "primary:$cleanPath"
                            DocumentsContract.buildDocumentUri(STORAGE_AUTHORITY, documentId)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Error querying MediaStore for RELATIVE_PATH for $mediaStoreUri: ${e.message}",
                    e,
                )
                null
            }
        }
    }

    companion object {
        private const val TAG = "ParentUriRepository"
        private const val STORAGE_AUTHORITY: String = "com.android.externalstorage.documents"
    }
}
