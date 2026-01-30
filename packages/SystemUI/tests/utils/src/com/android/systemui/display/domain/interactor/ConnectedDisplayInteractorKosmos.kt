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

package com.android.systemui.display.domain.interactor

import android.companion.virtual.VirtualDeviceManager
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.display.data.repository.fakeDeviceStateRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testDispatcher
import org.mockito.kotlin.mock

val Kosmos.connectedDisplayInteractor by
    Kosmos.Fixture {
        ConnectedDisplayInteractorImpl(
            keyguardRepository = fakeKeyguardRepository,
            displayRepository = displayRepository,
            deviceStateRepository = fakeDeviceStateRepository,
            backgroundCoroutineDispatcher = testDispatcher,
            virtualDeviceManager = mock<VirtualDeviceManager>(),
        )
    }
