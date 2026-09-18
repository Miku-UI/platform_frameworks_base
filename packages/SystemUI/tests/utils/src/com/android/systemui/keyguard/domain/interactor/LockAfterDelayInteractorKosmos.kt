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

package com.android.systemui.keyguard.domain.interactor

import android.app.admin.alarmManager
import android.content.mockedContext
import com.android.systemui.authentication.domain.interactor.authenticationInteractor
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.util.fakePendingIntentCreator
import com.android.systemui.util.settings.data.repository.userAwareSecureSettingsRepository
import com.android.systemui.util.settings.data.repository.userAwareSystemSettingsRepository
import com.android.systemui.util.time.systemClock

val Kosmos.lockAfterDelayInteractor: LockAfterDelayInteractor by
    Kosmos.Fixture {
        LockAfterDelayInteractor(
            applicationCoroutineScope,
            mockedContext,
            fakePendingIntentCreator,
            fakeKeyguardRepository,
            authenticationInteractor,
            keyguardInteractor,
            systemClock,
            alarmManager,
            userAwareSecureSettingsRepository,
            userAwareSystemSettingsRepository,
            powerInteractor,
        )
    }
