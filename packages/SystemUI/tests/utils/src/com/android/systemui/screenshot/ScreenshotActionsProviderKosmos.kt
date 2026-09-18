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

package com.android.systemui.screenshot

import android.content.applicationContext
import android.content.packageManager
import com.android.internal.logging.uiEventLogger
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.screencapture.record.domain.interactor.screenCaptureRecordFeaturesInteractor
import java.util.UUID

val Kosmos.screenshotActionsProviderFactory by
    Kosmos.Fixture {
        object : ScreenshotActionsProvider.Factory {
            override fun create(
                requestId: UUID,
                request: ScreenshotData,
                actionExecutor: ActionExecutor,
                actionsCallback: ScreenshotActionsController.ActionsCallback,
            ): ScreenshotActionsProvider {
                return DefaultScreenshotActionsProvider(
                    context = applicationContext,
                    uiEventLogger = uiEventLogger,
                    actionIntentCreator = actionIntentCreator,
                    applicationScope = applicationCoroutineScope,
                    screenCaptureRecordFeaturesInteractor = screenCaptureRecordFeaturesInteractor,
                    requestId = requestId,
                    request = request,
                    actionExecutor = actionExecutor,
                    actionsCallback = actionsCallback,
                    packageManager = packageManager,
                )
            }
        }
    }
