/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.systemui.ambientcue.domain.backup

import android.app.backup.SharedPreferencesBackupHelper
import android.content.Context
import com.android.systemui.ambientcue.domain.interactor.AmbientCueInteractor
import com.android.systemui.settings.UserFileManagerImpl

/** Helper to backup & restore the shared preferences in Ambient Cue for the current user. */
class AmbientCuePrefsBackupHelper(context: Context, userId: Int) :
    SharedPreferencesBackupHelper(
        context,
        UserFileManagerImpl.createFile(
                userId = userId,
                fileName = AmbientCueInteractor.SHARED_PREFERENCES_FILE_NAME,
            )
            .path,
    )
