/*
 * Copyright (C) 2026 Miku UI
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

package com.android.systemui.shade.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.shared.settings.data.repository.SystemSettingsRepository
import com.android.systemui.util.settings.SystemSettings
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class QsHeaderArt(val customBitmap: Bitmap?, val alpha: Float) {
    companion object {
        val Default = QsHeaderArt(customBitmap = null, alpha = 1f)
    }
}

@SysUISingleton
class QsHeaderArtRepository
@Inject
constructor(
    @Application private val context: Context,
    private val systemSettings: SystemSettingsRepository,
    private val systemSettingsProxy: SystemSettings,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
    @Application private val applicationScope: CoroutineScope,
) {
    private val cacheContext = context.createDeviceProtectedStorageContext()
    private val cacheFile = File(cacheContext.filesDir, CACHE_NAME)
    private val cacheRevFile = File(cacheContext.filesDir, CACHE_REV_NAME)

    private val customBitmap =
        combine(
            systemSettings.intSetting(Settings.System.MIKU_QS_HEADER_CUSTOM, 0),
            systemSettings.intSetting(Settings.System.MIKU_QS_HEADER_REVISION, 0),
        ) { custom, revision ->
            if (custom == 1) {
                loadCustomBitmap(revision)
            } else {
                clearCache()
                null
            }
        }

    val art: StateFlow<QsHeaderArt> =
        combine(
                customBitmap,
                systemSettings.intSetting(Settings.System.MIKU_QS_HEADER_ALPHA, 100),
            ) { bitmap, alpha ->
                QsHeaderArt(
                    customBitmap = bitmap,
                    alpha = alpha.coerceIn(0, 100) / 100f,
                )
            }
            .flowOn(backgroundDispatcher)
            .stateIn(applicationScope, SharingStarted.Eagerly, QsHeaderArt.Default)

    private fun loadCustomBitmap(revision: Int): Bitmap? {
        val cachedRev = cacheRevFile.takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull()
        if (cachedRev == revision && cacheFile.isFile) {
            return decodeCacheOrNull()
        }
        val path =
            systemSettingsProxy.getStringForUser(
                Settings.System.MIKU_QS_HEADER_PATH,
                UserHandle.USER_CURRENT,
            )
        if (path.isNullOrEmpty()) {
            Log.w(TAG, "Custom QS header enabled but path is empty")
            return null
        }
        val src = File(path)
        if (!src.isFile) {
            Log.w(TAG, "Custom QS header missing at $path")
            return null
        }
        return try {
            src.inputStream().use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
            cacheRevFile.writeText(revision.toString())
            decodeCacheOrNull()
        } catch (e: IOException) {
            Log.w(TAG, "Failed to load custom QS header image from $path", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read custom QS header image at $path", e)
            null
        }
    }

    private fun decodeCacheOrNull(): Bitmap? {
        if (!cacheFile.isFile) {
            return null
        }
        return try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(cacheFile)) { decoder, _, _ ->
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Failed to decode cached QS header image", e)
            null
        }
    }

    private fun clearCache() {
        cacheFile.delete()
        cacheRevFile.delete()
    }

    companion object {
        private const val TAG = "QsHeaderArtRepository"
        private const val CACHE_NAME = "miku_qs_header.jpg"
        private const val CACHE_REV_NAME = "miku_qs_header.rev"
    }
}
