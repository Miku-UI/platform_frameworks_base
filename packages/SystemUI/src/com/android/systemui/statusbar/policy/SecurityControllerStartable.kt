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

package com.android.systemui.statusbar.policy

import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.supervision.data.repository.SupervisionRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@SysUISingleton
class SecurityControllerStartable
@Inject
constructor(
    private val securityController: SecurityController,
    private val supervisionRepository: SupervisionRepository,
    @Background private val scope: CoroutineScope,
) : CoreStartable {
    override fun start() {
        if (!android.app.supervision.flags.Flags.enableSupervisionAppService()) {
            return
        }
        scope.launch {
            supervisionRepository.supervision.collectLatest { model ->
                securityController.supervisionModel = model
            }
        }
    }
}
