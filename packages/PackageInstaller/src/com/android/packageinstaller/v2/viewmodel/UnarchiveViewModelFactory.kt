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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.android.packageinstaller.v2.model.UnarchiveRepository

class UnarchiveViewModelFactory(val application: Application, val repository: UnarchiveRepository) :
    ViewModelProvider.AndroidViewModelFactory(application) {

    // Passing `application` to the super class' constructor ensures that `create` method is called
    // correctly and the right constructor of UnarchiveViewModel is used. If we don't pass this,
    // AndroidViewModelFactory will call a different constructor viz.
    // UninstallViewModel(application) and repository won't be initialized in the viewmodel
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return UnarchiveViewModel(application, repository) as T
    }
}
