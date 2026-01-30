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

package com.android.systemui.statusbar.featurepods.av

import com.android.systemui.Flags
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.featurepods.av.domain.interactor.AvControlsChipInteractor
import com.android.systemui.statusbar.featurepods.av.domain.interactor.AvControlsChipInteractorImpl
import com.android.systemui.statusbar.featurepods.av.domain.interactor.NoOpAvControlsChipInteractor
import dagger.Module
import dagger.Provides
import javax.inject.Provider

/** Module providing dependencies for Audio/Video controls feature pod. */
@Module
class AvControlsChipModule {

    /** Provides an [AvControlsChipInteractor] based on whether is is enabled by a flag */
    @Provides
    @SysUISingleton
    fun provideAvControlsChipInteractor(
        avControlsChipSupported: Provider<AvControlsChipInteractorImpl>,
        avControlsChipNotSupported: Provider<NoOpAvControlsChipInteractor>,
    ): AvControlsChipInteractor {
        return if (Flags.expandedPrivacyIndicatorsOnLargeScreen()) {
            avControlsChipSupported.get()
        } else {
            avControlsChipNotSupported.get()
        }
    }
}
