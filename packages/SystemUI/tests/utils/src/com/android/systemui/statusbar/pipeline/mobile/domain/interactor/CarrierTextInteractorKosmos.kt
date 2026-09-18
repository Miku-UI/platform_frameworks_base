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

package com.android.systemui.statusbar.pipeline.mobile.domain.interactor

import android.content.Context
import android.content.res.Resources
import android.content.res.mockResources
import android.content.testableContext
import android.telephony.TelephonyManager
import android.telephony.telephonyManager
import com.android.keyguard.CarrierTextManager
import com.android.keyguard.KeyguardUpdateMonitor
import com.android.keyguard.keyguardUpdateMonitor
import com.android.keyguard.logging.CarrierTextManagerLogger
import com.android.keyguard.logging.carrierTextManagerLogger
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.keyguard.WakefulnessLifecycle
import com.android.systemui.keyguard.wakefulnessLifecycle
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.statusbar.pipeline.satellite.ui.viewmodel.DeviceBasedSatelliteViewModel
import com.android.systemui.statusbar.pipeline.satellite.ui.viewmodel.deviceBasedSatelliteViewModel
import com.android.systemui.statusbar.pipeline.wifi.data.repository.WifiRepository
import com.android.systemui.statusbar.pipeline.wifi.data.repository.wifiRepository
import com.android.systemui.telephony.TelephonyListenerManager
import com.android.systemui.telephony.domain.telephonyListenerManager
import com.android.systemui.util.kotlin.JavaAdapter
import com.android.systemui.util.kotlin.javaAdapter
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

val Kosmos.carrierTextInteractor by Fixture {
    CarrierTextInteractorImpl(
        carrierTextManagerBuilder =
            FakeCarrierTextManagerBuilder(
                context = testableContext,
                resources = mockResources,
                wifiRepository = wifiRepository,
                deviceBasedSatelliteViewModel = deviceBasedSatelliteViewModel,
                javaAdapter = javaAdapter,
                telephonyManager = telephonyManager,
                telephonyListenerManager = telephonyListenerManager,
                wakefulnessLifecycle = wakefulnessLifecycle,
                mainExecutor = fakeExecutor,
                bgExecutor = fakeExecutor,
                keyguardUpdateMonitor = keyguardUpdateMonitor,
                logger = carrierTextManagerLogger,
            ),
        scope = applicationCoroutineScope,
    )
}

val Kosmos.fakeCarrierTextInteractor by Fixture { FakeCarrierTextInteractorImpl() }

private class FakeCarrierTextManagerBuilder(
    context: Context?,
    resources: Resources?,
    wifiRepository: WifiRepository?,
    deviceBasedSatelliteViewModel: DeviceBasedSatelliteViewModel?,
    javaAdapter: JavaAdapter?,
    telephonyManager: TelephonyManager?,
    telephonyListenerManager: TelephonyListenerManager?,
    wakefulnessLifecycle: WakefulnessLifecycle?,
    mainExecutor: Executor?,
    bgExecutor: Executor?,
    keyguardUpdateMonitor: KeyguardUpdateMonitor?,
    logger: CarrierTextManagerLogger?,
) :
    CarrierTextManager.Builder(
        context,
        resources,
        wifiRepository,
        deviceBasedSatelliteViewModel,
        javaAdapter,
        telephonyManager,
        telephonyListenerManager,
        wakefulnessLifecycle,
        mainExecutor,
        bgExecutor,
        keyguardUpdateMonitor,
        logger,
    ) {

    private var showAirplaneMode: Boolean = false
    private var showMissingSim: Boolean = false
    private var debugLocationString: String? = null

    override fun setShowAirplaneMode(showAirplaneMode: Boolean): CarrierTextManager.Builder {
        this.showAirplaneMode = showAirplaneMode
        return this
    }

    override fun setShowMissingSim(showMissingSim: Boolean): CarrierTextManager.Builder {
        this.showMissingSim = showMissingSim
        return this
    }

    override fun setDebugLocationString(debugLocationString: String?): CarrierTextManager.Builder {
        this.debugLocationString = debugLocationString
        return this
    }
}

class FakeCarrierTextInteractorImpl : CarrierTextInteractor {
    override val initialValue: CharSequence? = "No service"
    override val carrierText: StateFlow<CharSequence?> = MutableStateFlow(initialValue)
}
