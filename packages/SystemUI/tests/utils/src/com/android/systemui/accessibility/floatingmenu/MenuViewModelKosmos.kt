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

package com.android.systemui.accessibility.floatingmenu

import android.content.testableContext
import android.view.accessibility.accessibilityManager
import com.android.settingslib.bluetooth.HearingAidDeviceManager
import com.android.systemui.accessibility.TestUtils
import com.android.systemui.inputdevice.data.repository.pointerDeviceRepository
import com.android.systemui.keyboard.data.repository.keyboardRepository
import com.android.systemui.keyguard.domain.interactor.KeyguardTransitionInteractor
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.scene.domain.interactor.SceneInteractor
import com.android.systemui.scene.shared.model.Scenes
import kotlinx.coroutines.flow.MutableStateFlow
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

val Kosmos.menuViewModel by Fixture {
    MenuViewModel(
        testableContext,
        accessibilityManager,
        TestUtils.mockSecureSettings(testableContext),
        mock<HearingAidDeviceManager>(),
        keyboardRepository,
        pointerDeviceRepository,
        mock<KeyguardTransitionInteractor> {
            on { currentKeyguardState } doReturn MutableStateFlow(KeyguardState.GONE)
        },
        mock<SceneInteractor> { on { currentScene } doReturn MutableStateFlow(Scenes.Gone) },
    )
}
