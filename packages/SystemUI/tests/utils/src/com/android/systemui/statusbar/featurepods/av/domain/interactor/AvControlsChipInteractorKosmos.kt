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

package com.android.systemui.statusbar.featurepods.av.domain.interactor

import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.shade.data.repository.privacyChipRepository
import com.android.systemui.statusbar.data.repository.statusBarModeRepository
import com.android.systemui.statusbar.featurepods.av.AvControlsChipModule
import com.android.systemui.statusbar.featurepods.av.shared.model.AvControlsChipModel
import com.android.systemui.statusbar.featurepods.av.shared.model.SensorActivityModel
import javax.inject.Provider
import kotlinx.coroutines.flow.MutableStateFlow

val Kosmos.avControlsChipInteractor: AvControlsChipInteractor by
    Kosmos.Fixture {
        AvControlsChipModule()
            .provideAvControlsChipInteractor(
                avControlsChipSupported = Provider { avControlsChipInteractorImpl },
                avControlsChipNotSupported = Provider { noOpAvControlsChipInteractor },
            )
    }

val Kosmos.avControlsChipInteractorImpl: AvControlsChipInteractorImpl by
    Kosmos.Fixture {
        AvControlsChipInteractorImpl(
            backgroundScope = backgroundScope,
            privacyChipRepository = privacyChipRepository,
            statusBarModeRepositoryStore = statusBarModeRepository,
        )
    }

val Kosmos.noOpAvControlsChipInteractor: NoOpAvControlsChipInteractor by
    Kosmos.Fixture { NoOpAvControlsChipInteractor() }

val Kosmos.fakeAvControlsChipInteractor: FakeAvControlsChipInteractor by
    Kosmos.Fixture { FakeAvControlsChipInteractor() }

class FakeAvControlsChipInteractor : AvControlsChipInteractor {
    override val isEnabled: MutableStateFlow<Boolean> = MutableStateFlow(true)
    override val model: MutableStateFlow<AvControlsChipModel> =
        MutableStateFlow(AvControlsChipModel(sensorActivityModel = SensorActivityModel.Inactive))
    override val isShowingAvChip: MutableStateFlow<Boolean> = MutableStateFlow(false)
}
