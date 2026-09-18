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

package com.android.systemui.statusbar.quickactions.ime.domain.interactor

import com.android.systemui.deviceentry.domain.interactor.deviceEntryInteractor
import com.android.systemui.inputmethod.data.repository.fakeInputMethodRepository
import com.android.systemui.inputmethod.domain.interactor.inputMethodInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testScope
import com.android.systemui.statusbar.policy.domain.interactor.deviceProvisioningInteractor
import com.android.systemui.statusbar.policy.domain.interactor.userSetupInteractor
import com.android.systemui.user.data.repository.fakeUserRepository
import com.android.systemui.user.domain.interactor.userLogoutInteractor
import com.android.systemui.util.settings.fakeSettings

val Kosmos.imeIndicatorChipInteractor: ImeIndicatorChipInteractor by
    Kosmos.Fixture {
        ImeIndicatorChipInteractor(
            scope = testScope.backgroundScope,
            inputMethodInteractor = inputMethodInteractor,
            inputMethodRepository = fakeInputMethodRepository,
            userRepository = fakeUserRepository,
            secureSettings = fakeSettings,
            userSetupInteractor = userSetupInteractor,
            userLogoutInteractor = userLogoutInteractor,
            deviceProvisioningInteractor = deviceProvisioningInteractor,
            deviceEntryInteractor = deviceEntryInteractor,
        )
    }
