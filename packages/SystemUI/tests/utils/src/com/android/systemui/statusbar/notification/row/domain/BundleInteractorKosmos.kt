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

package com.android.systemui.statusbar.notification.row.domain

import android.content.applicationContext
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.statusbar.notification.row.data.repository.testBundleRepository
import com.android.systemui.statusbar.notification.row.domain.interactor.BundleInteractor
import com.android.systemui.statusbar.notification.row.icon.appIconProvider
import com.android.systemui.util.time.systemClock

val Kosmos.bundleInteractor by
    Kosmos.Fixture {
        BundleInteractor(
            repository = testBundleRepository,
            appIconProvider = appIconProvider,
            context = applicationContext,
            backgroundDispatcher = testDispatcher,
            systemClock = systemClock,
        )
    }
