/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.packageinstaller.v2.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.packageinstaller.v2.model.UnarchiveRepository
import com.android.packageinstaller.v2.model.UnarchiveStage

class UnarchiveViewModel(app: Application, val repository: UnarchiveRepository) :
    AndroidViewModel(app) {

    private val _currentUnarchiveStage = MutableLiveData<UnarchiveStage>()
    val currentUnarchiveStage: LiveData<UnarchiveStage>
        get() = _currentUnarchiveStage

    fun preprocessIntent(intent: Intent, info: UnarchiveRepository.CallerInfo) {
        val stage = repository.performPreUnarchivalChecks(intent, info)
        if (stage.stageCode == UnarchiveStage.STAGE_READY) {
            showUnarchiveConfirmation()
        } else {
            _currentUnarchiveStage.value = stage
        }
    }

    fun showUnarchiveConfirmation() {
        val stage = repository.showUnarchivalConfirmation()
        _currentUnarchiveStage.value = stage
    }

    fun beginUnarchive() {
        val stage = repository.beginUnarchive()
        _currentUnarchiveStage.value = stage
    }

    fun showUnarchiveError(intent: Intent) {
        val stage = repository.showUnarchiveError(intent)
        _currentUnarchiveStage.value = stage
    }
}