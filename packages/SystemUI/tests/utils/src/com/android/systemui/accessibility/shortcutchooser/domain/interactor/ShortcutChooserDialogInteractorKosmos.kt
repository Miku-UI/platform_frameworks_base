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

package com.android.systemui.accessibility.shortcutchooser.domain.interactor

import android.content.applicationContext
import com.android.systemui.accessibility.data.repository.accessibilityShortcutsRepository
import com.android.systemui.broadcast.broadcastDispatcher
import com.android.systemui.display.data.repository.displayRepository
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.user.data.repository.userRepository
import com.android.systemui.user.domain.interactor.fakeHeadlessSystemUserMode

val Kosmos.shortcutChooserDialogInteractor by Fixture {
    ShortcutChooserDialogInteractor(
        applicationContext,
        accessibilityShortcutsRepository,
        displayRepository,
        userRepository,
        fakeHeadlessSystemUserMode,
        keyguardInteractor,
        broadcastDispatcher,
    )
}
