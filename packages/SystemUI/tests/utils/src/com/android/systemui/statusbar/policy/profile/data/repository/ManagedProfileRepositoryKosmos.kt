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

package com.android.systemui.statusbar.policy.profile.data.repository

import android.os.userManager
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.mediaprojection.taskswitcher.fakeActivityTaskManager
import com.android.systemui.statusbar.mockCommandQueue
import com.android.systemui.statusbar.policy.profile.data.repository.impl.ManagedProfileRepositoryImpl
import com.android.systemui.user.data.repository.userRepository

val Kosmos.realManagedProfileRepository: ManagedProfileRepository by
    Kosmos.Fixture {
        ManagedProfileRepositoryImpl(
            backgroundScope = testScope.backgroundScope,
            backgroundDispatcher = testDispatcher,
            broadcastDispatcher = broadcastDispatcher,
            userManager = userManager,
            userRepository = userRepository,
            commandQueue = mockCommandQueue,
            activityTaskManager = fakeActivityTaskManager.activityTaskManager,
        )
    }

val Kosmos.managedProfileRepository: FakeManagedProfileRepository by
    Kosmos.Fixture { FakeManagedProfileRepository() }
