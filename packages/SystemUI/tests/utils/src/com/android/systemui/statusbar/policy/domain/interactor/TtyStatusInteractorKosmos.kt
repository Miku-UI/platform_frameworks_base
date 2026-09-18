/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy.domain.interactor

import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.statusbar.policy.domain.interactor.impl.TtyStatusInteractorImpl
import com.android.telecom.telecomManager

val Kosmos.ttyStatusInteractor: TtyStatusInteractor by
    Kosmos.Fixture {
        TtyStatusInteractorImpl(
            scope = testScope.backgroundScope,
            bgDispatcher = testDispatcher,
            broadcastDispatcher = broadcastDispatcher,
            telecomManager = telecomManager,
        )
    }

val Kosmos.fakeTtyStatusInteractor: FakeTtyStatusInteractor by
    Kosmos.Fixture { FakeTtyStatusInteractor() }
