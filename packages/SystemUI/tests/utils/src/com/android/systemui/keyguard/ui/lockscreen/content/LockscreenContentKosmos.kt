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

package com.android.systemui.keyguard.ui.lockscreen.content

import com.android.compose.animation.scene.Key
import com.android.systemui.jank.interactionJankMonitor
import com.android.systemui.keyguard.domain.interactor.keyguardClockInteractorWithImpl
import com.android.systemui.keyguard.ui.composable.LockscreenContent
import com.android.systemui.keyguard.ui.composable.elements.LockscreenElementFactoryImpl
import com.android.systemui.keyguard.ui.composable.elements.LockscreenElements
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.ambientIndicationAreaProvider
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.clockRegionElementProvider
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.defaultClockFaceLayout
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.lockIconElementProvider
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.lockscreenLowerRegionElementProvider
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.lockscreenRootElementProvider
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.lockscreenUpperRegionElementProvider
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.settingsMenuElementProvider
import com.android.systemui.keyguard.ui.lockscreen.elementproviders.statusBarElementProvider
import com.android.systemui.keyguard.ui.viewmodel.keyguardClockViewModelWithImpl
import com.android.systemui.keyguard.ui.viewmodel.lockscreenBehindScrimViewModelFactory
import com.android.systemui.keyguard.ui.viewmodel.lockscreenContentViewModelFactory
import com.android.systemui.keyguard.ui.viewmodel.lockscreenFrontScrimViewModelFactory
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.LogMessageImpl
import com.android.systemui.plugins.keyguard.ui.composable.elements.BaseLockscreenElement
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

val Kosmos.lockscreenContent by
    Kosmos.Fixture {
        LockscreenContent(
            viewModelFactory = lockscreenContentViewModelFactory,
            lockscreenFrontScrimViewModelFactory = lockscreenFrontScrimViewModelFactory,
            lockscreenBehindScrimViewModelFactory = lockscreenBehindScrimViewModelFactory,
            lockscreenElements =
                LockscreenElements(
                    builder =
                        object : LockscreenElementFactoryImpl.Builder {
                            override fun create(
                                elements: Map<Key, BaseLockscreenElement>
                            ): LockscreenElementFactoryImpl {
                                return LockscreenElementFactoryImpl(
                                    elements = elements,
                                    blueprintLog = logBuffer,
                                )
                            }
                        },
                    keyguardClockViewModel = keyguardClockViewModelWithImpl,
                    elementProviders = {
                        setOf(
                            lockscreenRootElementProvider,
                            statusBarElementProvider,
                            lockscreenUpperRegionElementProvider,
                            lockIconElementProvider,
                            ambientIndicationAreaProvider,
                            lockscreenLowerRegionElementProvider,
                            settingsMenuElementProvider,
                            clockRegionElementProvider,
                            defaultClockFaceLayout,
                        )
                    },
                    blueprintLog = logBuffer,
                ),
            clockInteractor = keyguardClockInteractorWithImpl,
            interactionJankMonitor = interactionJankMonitor,
        )
    }

val Kosmos.logBuffer by Fixture {
    mock<LogBuffer>().apply {
        whenever(obtain(any(), any(), any(), anyOrNull())).thenReturn(LogMessageImpl.create())
    }
}
