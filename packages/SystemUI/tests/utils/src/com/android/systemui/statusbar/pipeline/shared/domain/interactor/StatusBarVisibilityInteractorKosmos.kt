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

package com.android.systemui.statusbar.pipeline.shared.domain.interactor

import android.content.testableContext
import com.android.systemui.desktop.domain.interactor.desktopInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardOcclusionInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardTransitionInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.log.table.tableLogBufferFactory
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.shade.domain.interactor.shadeDisplaysInteractor
import com.android.systemui.shade.domain.interactor.shadeInteractor

val Kosmos.statusBarVisibilityInteractor: StatusBarVisibilityInteractor by
    Kosmos.Fixture {
        StatusBarVisibilityInteractor(
            thisDisplayId = testableContext.displayId,
            homeStatusBarInteractor = homeStatusBarInteractor,
            bgDisplayScope = backgroundScope,
            keyguardInteractor = keyguardInteractor,
            keyguardTransitionInteractor = keyguardTransitionInteractor,
            shadeInteractor = shadeInteractor,
            shadeDisplaysInteractor = { shadeDisplaysInteractor },
            sceneInteractor = sceneInteractor,
            occlusionInteractor = keyguardOcclusionInteractor,
            desktopInteractor = desktopInteractor,
            bgDispatcher = testDispatcher,
            tableLoggerFactory = tableLogBufferFactory,
        )
    }
