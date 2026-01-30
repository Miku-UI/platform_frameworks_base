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

package com.android.systemui.process.domain.interactor

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.process.ProcessWrapper
import com.android.systemui.user.data.repository.UserRepository
import com.android.systemui.utils.coroutines.flow.flatMapLatestConflated
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn

@SysUISingleton
class ProcessInteractor
@Inject
constructor(
    userRepository: UserRepository,
    private val processWrapper: ProcessWrapper,
    @Background val bgDispatcher: CoroutineDispatcher,
) {
    val processUserReady: Flow<Boolean> =
        userRepository.userInfos
            .flatMapLatestConflated { infos ->
                flowOf(
                    infos.count { info ->
                        !info.partial && info.userHandle == processWrapper.myUserHandle()
                    } > 0
                )
            }
            .flowOn(bgDispatcher)
}
