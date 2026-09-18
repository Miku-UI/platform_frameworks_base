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

import android.graphics.Rect
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureRegion
import com.android.systemui.screencapture.record.largescreen.shared.model.ScreenCaptureType
import com.android.systemui.shared.settings.data.repository.SecureSettingsRepository
import com.android.systemui.user.data.repository.UserRepository
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@SysUISingleton
class LargeScreenCaptureParametersRepository
@Inject
constructor(
    private val secureSettingsRepository: SecureSettingsRepository,
    userRepository: UserRepository,
) {

    /** [Flow] that observes the custom save location URI string for the current user */
    @OptIn(ExperimentalCoroutinesApi::class)
    val customSaveLocationUriString: Flow<String> =
        userRepository.selectedUserInfo
            .flatMapLatest { userInfo ->
                secureSettingsRepository.stringSetting(CUSTOM_SAVE_LOCATION_URI_KEY_NAME).map {
                    it.orEmpty()
                }
            }
            .distinctUntilChanged()

    /** [Flow] that observes the custom save location active status for the current user */
    @OptIn(ExperimentalCoroutinesApi::class)
    val isCustomSaveLocationActive: Flow<Boolean> =
        userRepository.selectedUserInfo
            .flatMapLatest {
                secureSettingsRepository.boolSetting(CUSTOM_SAVE_LOCATION_IS_ACTIVE_KEY_NAME)
            }
            .distinctUntilChanged()

    /**
     * Updates the custom save location URI string.
     * - If [uri] is null or corresponds to the default screenshots folder, the custom save location
     *   is deactivated (this means we will use the default folder).
     * - Otherwise, the custom save location is activated, and the [uri] string is saved to the data
     *   store.
     *
     * @param uri The [Uri] of the custom save location, or null to deactivate it.
     */
    suspend fun updateCustomSaveLocationUriString(uri: Uri) {
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        val path = documentId?.substringAfter("primary:")

        if (path == DEFAULT_SCREENSHOTS_FOLDER) {
            updateIsCustomSaveLocationActive(false)
        } else {
            val uriString = uri.toString()
            updateIsCustomSaveLocationActive(true)
            secureSettingsRepository.setString(CUSTOM_SAVE_LOCATION_URI_KEY_NAME, uriString)
        }
    }

    /**
     * Updates whether the custom save location is active. This is ran by System User (user 0),
     * performing action on behalf of the specific user.
     *
     * If active, it means we should be using the custom save location If inactive, it means we
     * should use the default save location
     *
     * @param isActive Whether the custom save location is active.
     */
    suspend fun updateIsCustomSaveLocationActive(isActive: Boolean) {
        secureSettingsRepository.setBoolean(CUSTOM_SAVE_LOCATION_IS_ACTIVE_KEY_NAME, isActive)
    }

    /**
     * Gets user's previously selected capture type. Returns screenshot type by default. If the time
     * of selected type exceeds the retention period, the default type will be returned.
     */
    suspend fun getSelectedCaptureType(): ScreenCaptureType {
        if (isSavedTimeExpired(SELECTED_SCREEN_CAPTURE_TYPE_REGION_TIME_NAME)) {
            return DEFAULT_SCREEN_CAPTURE_TYPE
        }

        val typeString = secureSettingsRepository.getString(SELECTED_SCREEN_CAPTURE_TYPE_NAME)
        return when (typeString) {
            TYPE_SCREENSHOT -> ScreenCaptureType.SCREENSHOT
            TYPE_RECORDING -> ScreenCaptureType.RECORDING
            else -> DEFAULT_SCREEN_CAPTURE_TYPE
        }
    }

    /**
     * Gets user's previously selected capture region. Returns partial region by default. If the
     * time of selected region exceeds the retention period, the default region will be returned.
     */
    suspend fun getSelectedCaptureRegion(): ScreenCaptureRegion {
        if (isSavedTimeExpired(SELECTED_SCREEN_CAPTURE_TYPE_REGION_TIME_NAME)) {
            return DEFAULT_SCREEN_CAPTURE_REGION
        }

        val regionString = secureSettingsRepository.getString(SELECTED_SCREEN_CAPTURE_REGION_NAME)
        return when (regionString) {
            REGION_FULLSCREEN -> ScreenCaptureRegion.FULLSCREEN
            REGION_PARTIAL -> ScreenCaptureRegion.PARTIAL
            REGION_APP_WINDOW -> ScreenCaptureRegion.APP_WINDOW
            else -> DEFAULT_SCREEN_CAPTURE_REGION
        }
    }

    /** Gets user's previously selected region box for partial screen capture. */
    suspend fun getSelectedCaptureRegionBox(): Rect? {
        if (isSavedTimeExpired(SELECTED_SCREEN_CAPTURE_REGION_BOX_TIME_NAME)) {
            return null
        }

        return Rect.unflattenFromString(
            secureSettingsRepository.getString(SELECTED_SCREEN_CAPTURE_REGION_BOX_NAME)
        )
    }

    /**
     * Updates the user's selected capture type.
     *
     * @param type Currently selected capture type.
     */
    suspend fun updateSelectedCaptureTypeString(type: ScreenCaptureType) {
        val typeString =
            when (type) {
                ScreenCaptureType.SCREENSHOT -> TYPE_SCREENSHOT
                ScreenCaptureType.RECORDING -> TYPE_RECORDING
            }
        secureSettingsRepository.setString(SELECTED_SCREEN_CAPTURE_TYPE_NAME, typeString)
        saveSelectedCaptureTypeRegionTime(Instant.now())
    }

    /**
     * Updates the user's selected capture region.
     *
     * @param region Currently selected capture region.
     */
    suspend fun updateSelectedCaptureRegionString(region: ScreenCaptureRegion) {
        val regionString =
            when (region) {
                ScreenCaptureRegion.FULLSCREEN -> REGION_FULLSCREEN
                ScreenCaptureRegion.PARTIAL -> REGION_PARTIAL
                ScreenCaptureRegion.APP_WINDOW -> REGION_APP_WINDOW
            }
        secureSettingsRepository.setString(SELECTED_SCREEN_CAPTURE_REGION_NAME, regionString)
        saveSelectedCaptureTypeRegionTime(Instant.now())
    }

    /**
     * Updates the user's selected region box for partial screen capture.
     *
     * @param regionBox Currently selected capture region box.
     */
    suspend fun updateSelectedCaptureRegionBoxString(regionBox: Rect?) {
        secureSettingsRepository.setString(
            SELECTED_SCREEN_CAPTURE_REGION_BOX_NAME,
            regionBox?.flattenToString(),
        )
        saveSelectedCaptureRegionBoxTime(Instant.now())
    }

    /**
     * Save the time when the user selects a capture type or region.
     *
     * @param time Currently the time when the user selects capture type or region.
     */
    suspend fun saveSelectedCaptureTypeRegionTime(time: Instant) {
        secureSettingsRepository.setString(
            SELECTED_SCREEN_CAPTURE_TYPE_REGION_TIME_NAME,
            time.toString(),
        )
    }

    /**
     * Save the time when the user updates the partial screen capture region box.
     *
     * @param time Currently the time when the user updates the partial screen capture region box.
     */
    suspend fun saveSelectedCaptureRegionBoxTime(time: Instant) {
        secureSettingsRepository.setString(
            SELECTED_SCREEN_CAPTURE_REGION_BOX_TIME_NAME,
            time.toString(),
        )
    }

    /**
     * Gets if a saved time expires.
     *
     * @param timeName The name of the capture parameter's save time.
     */
    private suspend fun isSavedTimeExpired(timeName: String): Boolean {
        val timestampString = secureSettingsRepository.getString(timeName) ?: return true
        return try {
            val selectedTime = Instant.parse(timestampString)
            val duration = Duration.between(selectedTime, Instant.now())
            duration.toMinutes() > VALID_OPTION_DURATION_MINUTES
        } catch (e: DateTimeParseException) {
            true
        }
    }

    companion object {
        private const val CUSTOM_SAVE_LOCATION_URI_KEY_NAME = "custom_save_location_uri"
        private const val CUSTOM_SAVE_LOCATION_IS_ACTIVE_KEY_NAME = "custom_save_location_is_active"
        private val DEFAULT_SCREENSHOTS_FOLDER =
            Environment.DIRECTORY_PICTURES + File.separator + Environment.DIRECTORY_SCREENSHOTS
        private const val SELECTED_SCREEN_CAPTURE_TYPE_NAME = "selected_screen_capture_type"
        private const val SELECTED_SCREEN_CAPTURE_REGION_NAME = "selected_screen_capture_region"
        private const val SELECTED_SCREEN_CAPTURE_TYPE_REGION_TIME_NAME =
            "selected_screen_capture_type_region_time"
        private const val SELECTED_SCREEN_CAPTURE_REGION_BOX_NAME =
            "selected_screen_capture_region_box"
        private const val SELECTED_SCREEN_CAPTURE_REGION_BOX_TIME_NAME =
            "selected_screen_capture_region_box_time"
        private const val TYPE_SCREENSHOT = "screenshot"
        private const val TYPE_RECORDING = "recording"
        private const val REGION_FULLSCREEN = "fullscreen"
        private const val REGION_PARTIAL = "partial"
        private const val REGION_APP_WINDOW = "app_window"
        private const val VALID_OPTION_DURATION_MINUTES = 10
        private val DEFAULT_SCREEN_CAPTURE_TYPE = ScreenCaptureType.SCREENSHOT
        private val DEFAULT_SCREEN_CAPTURE_REGION = ScreenCaptureRegion.PARTIAL
    }
}
