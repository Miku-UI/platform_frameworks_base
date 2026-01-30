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

package com.android.systemui.statusbar.pipeline.wifi.data.repository.demo

import com.android.systemui.KairosActivatable
import com.android.systemui.KairosBuilder
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.kairos.Events
import com.android.systemui.kairos.ExperimentalKairosApi
import com.android.systemui.kairos.util.nameTag
import com.android.systemui.kairosBuilder
import com.android.systemui.statusbar.pipeline.mobile.StatusBarMobileIconKairos
import com.android.systemui.statusbar.pipeline.wifi.data.repository.demo.model.FakeWifiEventModel
import dagger.Provides
import dagger.multibindings.ElementsIntoSet
import javax.inject.Inject
import javax.inject.Provider

/**
 * A wrapper around [DemoModeWifiDataSource] that re-exposes Flows as Kairos [Events].
 *
 * @see DemoModeWifiDataSource
 */
@ExperimentalKairosApi
@SysUISingleton
class DemoModeWifiDataSourceKairos
private constructor(
    private val unwrapped: DemoModeWifiDataSource,
    private val kairosBuilder: KairosBuilder,
) : KairosActivatable by kairosBuilder {

    @Inject
    constructor(
        demoModeWifiDataSource: DemoModeWifiDataSource
    ) : this(demoModeWifiDataSource, kairosBuilder())

    val wifiEvents: Events<FakeWifiEventModel?> =
        kairosBuilder.buildEvents {
            unwrapped.wifiEvents
                .toEvents(nameTag("DemoModeWifiDataSourceKairos.wifiEvents"))
                // Ensure that we are always observing; this keeps the underlying SharedFlow alive
                // and ensures that events aren't missed the instant demo mode is activated.
                .apply { observeSync() }
        }

    @dagger.Module
    object Module {
        @Provides
        @ElementsIntoSet
        fun kairosActivatable(
            impl: Provider<DemoModeWifiDataSourceKairos>
        ): Set<@JvmSuppressWildcards KairosActivatable> =
            if (StatusBarMobileIconKairos.isEnabled) setOf(impl.get()) else emptySet()
    }
}
