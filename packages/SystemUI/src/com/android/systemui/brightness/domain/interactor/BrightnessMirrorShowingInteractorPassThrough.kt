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

package com.android.systemui.brightness.domain.interactor

import com.android.systemui.brightness.data.repository.BrightnessMirrorShowingRepository
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject

/** This interactor is just a passthrough of the [BrightnessMirrorShowingRepository]. */
@SysUISingleton
class BrightnessMirrorShowingInteractorPassThrough
@Inject
constructor(private val brightnessMirrorShowingRepository: BrightnessMirrorShowingRepository) :
    BrightnessMirrorShowingInteractor {
    /**
     * Whether a brightness mirror is showing (either as a compose overlay or as a separate mirror).
     *
     * This can be used to determine whether other views/composables have to be hidden.
     */
    override val isShowing = brightnessMirrorShowingRepository.isShowing

    override fun setMirrorShowing(showing: Boolean) {
        brightnessMirrorShowingRepository.setMirrorShowing(showing)
    }
}
