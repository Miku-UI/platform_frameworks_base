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

package com.android.systemui.statusbar.pipeline.mobile.data.repository

import android.content.Context
import com.android.settingslib.SignalIcon
import com.android.settingslib.mobile.MobileMappings
import com.android.systemui.KairosActivatable
import com.android.systemui.KairosBuilder
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.Incremental
import com.android.systemui.kairos.KairosNetwork
import com.android.systemui.kairos.buildSpec
import com.android.systemui.kairos.combine
import com.android.systemui.kairos.map
import com.android.systemui.kairos.mapValues
import com.android.systemui.kairos.toColdConflatedFlow
import com.android.systemui.kairos.util.nameTag
import com.android.systemui.kairosBuilder
import com.android.systemui.statusbar.pipeline.mobile.StatusBarMobileIconKairos
import com.android.systemui.statusbar.pipeline.mobile.data.model.SubscriptionModel
import com.android.systemui.statusbar.pipeline.mobile.data.repository.prod.MobileConnectionRepositoryKairosAdapter
import com.android.systemui.statusbar.pipeline.shared.data.repository.ConnectivityRepository
import dagger.Provides
import dagger.multibindings.ElementsIntoSet
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@ExperimentalKairosApi
@SysUISingleton
class MobileConnectionsRepositoryKairosAdapter
@Inject
constructor(
    private val kairosRepo: MobileConnectionsRepositoryKairos,
    private val kairosNetwork: KairosNetwork,
    @Application scope: CoroutineScope,
    connectivityRepository: ConnectivityRepository,
    context: Context,
    carrierConfigRepo: CarrierConfigRepository,
) : MobileConnectionsRepository, KairosBuilder by kairosBuilder() {
    override val subscriptions: StateFlow<List<SubscriptionModel>> =
        kairosRepo.subscriptions
            .map { it.toList() }
            .toColdConflatedFlow(
                kairosNetwork,
                nameTag("MobileConnectionsRepositoryKairosAdapter.subscriptions"),
            )
            .stateIn(scope, SharingStarted.WhileSubscribed(), emptyList())

    override val activeMobileDataSubscriptionId: StateFlow<Int?> =
        kairosRepo.activeMobileDataSubscriptionId
            .toColdConflatedFlow(
                kairosNetwork,
                nameTag("MobileConnectionsRepositoryKairosAdapter.activeMobileDataSubscriptionId"),
            )
            .stateIn(scope, SharingStarted.WhileSubscribed(), null)

    private val reposBySubIdK: Incremental<Int, MobileConnectionRepositoryKairosAdapter> =
        buildIncremental {
            kairosRepo.mobileConnectionsBySubId
                .mapValues { (_, repo) ->
                    buildSpec { MobileConnectionRepositoryKairosAdapter(kairosRepo = repo) }
                }
                .applyLatestSpecForKey(
                    name = nameTag("MobileConnectionsRepositoryKairosAdapter.reposBySubIdK")
                )
        }

    private val reposBySubId: StateFlow<Map<Int, MobileConnectionRepositoryKairosAdapter>> =
        reposBySubIdK
            .toColdConflatedFlow(
                kairosNetwork,
                nameTag("MobileConnectionsRepositoryKairosAdapter.reposBySubId"),
            )
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    override val activeMobileDataRepository: StateFlow<MobileConnectionRepository?> =
        combine(kairosRepo.activeMobileDataSubscriptionId, reposBySubIdK) { id, repos -> repos[id] }
            .toColdConflatedFlow(
                kairosNetwork,
                nameTag("MobileConnectionsRepositoryKairosAdapter.activeMobileDataRepository"),
            )
            .stateIn(scope, SharingStarted.WhileSubscribed(), null)

    override val activeSubChangedInGroupEvent: Flow<Unit> =
        kairosRepo.activeSubChangedInGroupEvent.toColdConflatedFlow(
            kairosNetwork,
            nameTag("MobileConnectionsRepositoryKairosAdapter.activeSubChangedInGroupEvent"),
        )

    override val defaultDataSubId: StateFlow<Int?> =
        kairosRepo.defaultDataSubId
            .toColdConflatedFlow(
                kairosNetwork,
                nameTag("MobileConnectionsRepositoryKairosAdapter.defaultDataSubId"),
            )
            .stateIn(scope, SharingStarted.WhileSubscribed(), null)

    override val mobileIsDefault: StateFlow<Boolean> =
        kairosRepo.mobileIsDefault
            .toColdConflatedFlow(
                kairosNetwork,
                nameTag("MobileConnectionsRepositoryKairosAdapter.mobileIsDefault"),
            )
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(),
                connectivityRepository.defaultConnections.value.mobile.isDefault,
            )

    override val hasCarrierMergedConnection: Flow<Boolean> =
        kairosRepo.hasCarrierMergedConnection.toColdConflatedFlow(
            kairosNetwork,
            nameTag("MobileConnectionsRepositoryKairosAdapter.hasCarrierMergedConnection"),
        )

    override val defaultConnectionIsValidated: StateFlow<Boolean> =
        kairosRepo.defaultConnectionIsValidated
            .toColdConflatedFlow(
                kairosNetwork,
                nameTag("MobileConnectionsRepositoryKairosAdapter.defaultConnectionIsValidated"),
            )
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(),
                connectivityRepository.defaultConnections.value.isValidated,
            )

    override fun getRepoForSubId(subId: Int): MobileConnectionRepository =
        reposBySubId.value[subId] ?: error("Unknown subscription id: $subId")

    override val defaultDataSubRatConfig: StateFlow<MobileMappings.Config> =
        kairosRepo.defaultDataSubRatConfig
            .toColdConflatedFlow(
                kairosNetwork,
                nameTag("MobileConnectionsRepositoryKairosAdapter.defaultDataSubRatConfig"),
            )
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(),
                MobileMappings.Config.readConfig(context),
            )

    override val defaultMobileIconMapping: Flow<Map<String, SignalIcon.MobileIconGroup>> =
        kairosRepo.defaultMobileIconMapping.toColdConflatedFlow(
            kairosNetwork,
            nameTag("MobileConnectionsRepositoryKairosAdapter.defaultMobileIconMapping"),
        )

    override val defaultMobileIconGroup: Flow<SignalIcon.MobileIconGroup> =
        kairosRepo.defaultMobileIconGroup.toColdConflatedFlow(
            kairosNetwork,
            nameTag("MobileConnectionsRepositoryKairosAdapter.defaultMobileIconGroup"),
        )

    override val isDeviceEmergencyCallCapable: StateFlow<Boolean> =
        kairosRepo.isDeviceEmergencyCallCapable
            .toColdConflatedFlow(
                kairosNetwork,
                nameTag("MobileConnectionsRepositoryKairosAdapter.isDeviceEmergencyCallCapable"),
            )
            .stateIn(scope, SharingStarted.Eagerly, false)

    override val isAnySimSecure: StateFlow<Boolean> =
        kairosRepo.isAnySimSecure
            .toColdConflatedFlow(
                kairosNetwork,
                nameTag("MobileConnectionsRepositoryKairosAdapter.isAnySimSecure"),
            )
            .stateIn(scope, SharingStarted.Eagerly, false)

    override fun getIsAnySimSecure(): Boolean = isAnySimSecure.value

    override suspend fun isInEcmMode(): Boolean =
        kairosNetwork.transact { kairosRepo.isInEcmMode.sample() }

    @dagger.Module
    object Module {
        @Provides
        @ElementsIntoSet
        fun kairosActivatable(
            impl: Provider<MobileConnectionsRepositoryKairosAdapter>
        ): Set<@JvmSuppressWildcards KairosActivatable> =
            if (StatusBarMobileIconKairos.isEnabled) setOf(impl.get()) else emptySet()
    }
}
