/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.shade.domain.interactor

import android.content.mockedContext
import android.window.WindowContext
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.testScope
import com.android.systemui.log.logcatLogBuffer
import com.android.systemui.shade.ShadeDisplayChangePerformanceTracker
import com.android.systemui.shade.ShadeWindowLayoutParams
import com.android.systemui.shade.data.repository.fakeShadeDisplaysRepository
import com.android.systemui.shade.data.repository.shadeExpansionIntent
import com.android.systemui.statusbar.notification.domain.interactor.activeNotificationsInteractor
import com.android.systemui.statusbar.notification.stack.notificationStackRebindingHider
import com.android.systemui.statusbar.phone.mockSystemUIDialogManager
import com.android.systemui.statusbar.policy.configurationController
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

val Kosmos.shadeLayoutParams by Kosmos.Fixture { ShadeWindowLayoutParams.create(mockedContext) }

val Kosmos.mockedWindowContext by
    Kosmos.Fixture {
        mock<WindowContext>().apply {
            whenever(reparentToDisplay(any())).thenAnswer { displayIdParam ->
                whenever(displayId).thenReturn(displayIdParam.arguments[0] as Int)
            }
        }
    }
val Kosmos.mockedShadeDisplayChangePerformanceTracker by
    Kosmos.Fixture { mock<ShadeDisplayChangePerformanceTracker>() }
val Kosmos.shadeDisplaysInteractor by
    Kosmos.Fixture {
        ShadeDisplaysInteractorImpl(
            fakeShadeDisplaysRepository,
            mockedWindowContext,
            testScope.backgroundScope,
            testScope.backgroundScope.coroutineContext,
            mockedShadeDisplayChangePerformanceTracker,
            shadeExpandedStateInteractor,
            shadeExpansionIntent,
            activeNotificationsInteractor,
            notificationStackRebindingHider,
            configurationController,
            logcatLogBuffer("ShadeDisplaysInteractor"),
            shadeDisplaysWaitInteractor,
        )
    }

val Kosmos.shadeDisplayDialogInteractor by
    Kosmos.Fixture {
        ShadeDisplaysDialogInteractor(
            mockSystemUIDialogManager,
            fakeShadeDisplaysRepository,
            testScope.backgroundScope,
        )
    }
