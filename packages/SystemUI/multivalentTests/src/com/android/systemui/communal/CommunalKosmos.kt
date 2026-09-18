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

package com.android.systemui.communal

import com.android.systemui.communal.data.repository.ContextualSetupRepository
import com.android.systemui.communal.data.repository.fake.FakeContextualSetupRepository
import com.android.systemui.communal.domain.definition.ContextualSetupDefinition
import com.android.systemui.communal.domain.definition.fake.FakeContextualSetupDefinition
import com.android.systemui.communal.domain.interactor.ContextualSetupInteractor
import com.android.systemui.communal.domain.interactor.UprightChargingInteractor
import com.android.systemui.communal.domain.interactor.fake.FakeUprightChargingInteractor
import com.android.systemui.communal.domain.preconditions.CommonSetupPreconditions
import com.android.systemui.communal.domain.preconditions.fake.FakeCommonSetupPreconditions
import com.android.systemui.dump.dumpManager
import com.android.systemui.kosmos.Kosmos

val Kosmos.commonSetupPreconditions: CommonSetupPreconditions by
    Kosmos.Fixture { FakeCommonSetupPreconditions() }
val CommonSetupPreconditions.fake: FakeCommonSetupPreconditions
    get() = this as FakeCommonSetupPreconditions

val Kosmos.uprightChargingInteractor: UprightChargingInteractor by
    Kosmos.Fixture { FakeUprightChargingInteractor() }
val UprightChargingInteractor.fake: FakeUprightChargingInteractor
    get() = this as FakeUprightChargingInteractor

val Kosmos.contextualSetupRepository: ContextualSetupRepository by
    Kosmos.Fixture { FakeContextualSetupRepository() }
val ContextualSetupRepository.fake: FakeContextualSetupRepository
    get() = this as FakeContextualSetupRepository

val Kosmos.contextualSetupDefinitionFactory: (String) -> ContextualSetupDefinition by
    Kosmos.Fixture { { id -> FakeContextualSetupDefinition(id) } }
val ContextualSetupDefinition.fake: FakeContextualSetupDefinition
    get() = this as FakeContextualSetupDefinition

val Kosmos.contextualSetupInteractorFactory:
    (Set<ContextualSetupDefinition>) -> ContextualSetupInteractor by
    Kosmos.Fixture {
        { definitions ->
            ContextualSetupInteractor(
                repository = contextualSetupRepository,
                definitions = definitions,
                dumpManager = dumpManager,
            )
        }
    }
