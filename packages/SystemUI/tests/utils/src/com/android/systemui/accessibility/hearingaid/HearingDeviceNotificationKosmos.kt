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

package com.android.systemui.accessibility.hearingaid

import android.app.notificationManager
import android.content.applicationContext
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.statusbar.policy.bluetooth.data.repository.bluetoothRepository
import org.mockito.kotlin.mock

var Kosmos.hearingDeviceNotificationDismissController by
    Kosmos.Fixture { mock<HearingDeviceNotificationDismissController>() }

val Kosmos.hearingDeviceNotification by
    Kosmos.Fixture {
        HearingDeviceNotification(
            context = applicationContext,
            bluetoothRepository = bluetoothRepository,
            broadcastDispatcher = broadcastDispatcher,
            notificationManager = notificationManager,
            dismissController = hearingDeviceNotificationDismissController,
            mainExecutor = fakeExecutor,
            applicationScope = applicationCoroutineScope,
        )
    }
