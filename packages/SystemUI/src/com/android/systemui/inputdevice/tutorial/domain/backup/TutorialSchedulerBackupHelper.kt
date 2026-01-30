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

package com.android.systemui.inputdevice.tutorial.domain.backup

import android.app.QueuedWork
import android.app.backup.AbsoluteFileBackupHelper
import android.app.backup.BackupDataOutput
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.datastore.preferences.preferencesDataStoreFile
import com.android.systemui.inputdevice.tutorial.data.repository.TutorialSchedulerRepository.Companion.DATASTORE_NAME

class TutorialSchedulerBackupHelper(context: Context) :
    AbsoluteFileBackupHelper(
        context,
        context.preferencesDataStoreFile(DATASTORE_NAME).absolutePath,
    ) {
    override fun performBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?,
    ) {
        // Finish outstanding DataStore write
        QueuedWork.waitToFinish()

        super.performBackup(oldState, data, newState)
    }
}
