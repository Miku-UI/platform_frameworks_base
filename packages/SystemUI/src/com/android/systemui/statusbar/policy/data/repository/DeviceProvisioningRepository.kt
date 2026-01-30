/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.systemui.statusbar.policy.data.repository

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.statusbar.policy.DeviceProvisionedController
import com.android.systemui.util.kotlin.pairwise
import com.android.systemui.util.time.SystemClock
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import dagger.Binds
import dagger.Module
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/** Tracks state related to device provisioning. */
interface DeviceProvisioningRepository {
    /**
     * Whether this device has been provisioned.
     *
     * @see android.provider.Settings.Global.DEVICE_PROVISIONED
     */
    val isDeviceProvisioned: Flow<Boolean>

    /**
     * Whether this device has been provisioned.
     *
     * @see android.provider.Settings.Global.DEVICE_PROVISIONED
     */
    fun isDeviceProvisioned(): Boolean

    fun getProvisionedTimestamp(): ProvisionedTimestamp

    sealed class ProvisionedTimestamp {
        /** Device is not yet provisioned. */
        object NotProvisioned : ProvisionedTimestamp()

        /**
         * Device provisioning was done, but it happened before we started recording the timestamp,
         * so we don't know when that was.
         */
        object Unknown : ProvisionedTimestamp()

        /** Device is provisioned and it happened at the specified [Instant]. */
        data class AtInstant(val instant: Instant) : ProvisionedTimestamp()
    }
}

@Module
interface DeviceProvisioningRepositoryModule {
    @Binds fun bindsImpl(impl: DeviceProvisioningRepositoryImpl): DeviceProvisioningRepository

    /** Binds [DeviceProvisioningRepository] as [CoreStartable]. */
    @Binds
    @IntoMap
    @ClassKey(DeviceProvisioningRepository::class)
    fun bindSecurityControllerCoreStartable(
        startable: DeviceProvisioningRepositoryImpl
    ): CoreStartable
}

@SysUISingleton
class DeviceProvisioningRepositoryImpl
@Inject
constructor(
    context: Context,
    @Background private val bgCoroutineScope: CoroutineScope,
    private val systemClock: SystemClock,
    private val deviceProvisionedController: DeviceProvisionedController,
) : DeviceProvisioningRepository, CoreStartable {
    override val isDeviceProvisioned: Flow<Boolean> = conflatedCallbackFlow {
        val listener =
            object : DeviceProvisionedController.DeviceProvisionedListener {
                override fun onDeviceProvisionedChanged() {
                    trySend(isDeviceProvisioned())
                }
            }
        deviceProvisionedController.addCallback(listener)
        trySend(isDeviceProvisioned())
        awaitClose { deviceProvisionedController.removeCallback(listener) }
    }

    override fun isDeviceProvisioned(): Boolean {
        return deviceProvisionedController.isDeviceProvisioned
    }

    private val prefs = context.getSharedPreferences(context.packageName, Context.MODE_PRIVATE)
    private var provisionedTimestamp: DeviceProvisioningRepository.ProvisionedTimestamp =
        DeviceProvisioningRepository.ProvisionedTimestamp.Unknown

    override fun start() {
        if (isDeviceProvisioned()) {
            val storedTimestamp = prefs.getLong(PREF_DEVICE_PROVISIONED_TIMESTAMP, 0L)
            if (storedTimestamp != 0L) {
                provisionedTimestamp =
                    DeviceProvisioningRepository.ProvisionedTimestamp.AtInstant(
                        Instant.ofEpochMilli(storedTimestamp)
                    )
            } else {
                // We don't know and never will. :(
                provisionedTimestamp = DeviceProvisioningRepository.ProvisionedTimestamp.Unknown
            }
        } else {
            provisionedTimestamp = DeviceProvisioningRepository.ProvisionedTimestamp.NotProvisioned

            // Start tracking provisioning signal if not yet provisioned. Must be done as part of
            // startup so we don't miss the transition.
            bgCoroutineScope.launch {
                isDeviceProvisioned.pairwise().firstOrNull { (previous, current) ->
                    !previous && current
                }

                val now = systemClock.currentTime()
                prefs.edit().putLong(PREF_DEVICE_PROVISIONED_TIMESTAMP, now.toEpochMilli()).apply()
                provisionedTimestamp =
                    DeviceProvisioningRepository.ProvisionedTimestamp.AtInstant(now)
            }
        }
    }

    override fun getProvisionedTimestamp(): DeviceProvisioningRepository.ProvisionedTimestamp {
        return provisionedTimestamp
    }

    companion object {
        @VisibleForTesting
        const val PREF_DEVICE_PROVISIONED_TIMESTAMP = "device_provisioned_timestamp"
    }
}
