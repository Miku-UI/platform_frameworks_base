/*
* Copyright (C) 2026 The Android Open Source Project
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

package com.android.systemui.communal.domain.preconditions.fake

import com.android.systemui.communal.domain.preconditions.CommonSetupPreconditions
import kotlinx.coroutines.flow.MutableStateFlow

class FakeCommonSetupPreconditions : CommonSetupPreconditions {
    private val _allMet = MutableStateFlow(false)
    override val allMet = _allMet

    fun setAllMet(value: Boolean) {
        _allMet.value = value
    }
}

val CommonSetupPreconditions.fake
    get() = this as FakeCommonSetupPreconditions
