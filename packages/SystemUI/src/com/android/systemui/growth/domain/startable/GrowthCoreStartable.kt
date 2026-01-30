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

package com.android.systemui.growth.domain.startable

import com.android.app.tracing.coroutines.launchTraced
import com.android.systemui.CoreStartable
import com.android.systemui.Flags
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.growth.domain.interactor.GrowthInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

@SysUISingleton
class GrowthCoreStartable
@Inject
constructor(
    private val growthInteractor: GrowthInteractor,
    @Background private val backgroundScope: CoroutineScope,
) : CoreStartable {

    override fun start() {
        if (!SceneContainerFlag.isEnabled) {
            return
        }

        // TODO: b/419607402 - Wrap this with a helper object.
        if (!Flags.enableDesktopGrowth()) {
            return
        }

        backgroundScope.launchTraced("GrowthCoreStartable#start") { growthInteractor.activate() }
    }
}
