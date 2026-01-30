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

package com.android.systemui.display.data.repository

import android.util.Log
import com.android.app.displaylib.PerDisplayInstanceProviderWithSetup
import com.android.app.displaylib.PerDisplayInstanceProviderWithTeardown
import com.android.app.displaylib.PerDisplayInstanceRepositoryImpl
import com.android.app.displaylib.PerDisplayRepository
import com.android.app.tracing.ListenersTracing.forEachTraced
import com.android.app.tracing.traceSection
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent
import com.android.systemui.display.flags.DisplayComponentRepositoryFlag.isEagerInitializationEnabled
import com.android.systemui.display.shared.DisplayNotFoundException
import dagger.Module
import dagger.Provides
import javax.inject.Inject
import kotlinx.coroutines.cancel

@SysUISingleton
class DisplayComponentInstanceProvider
@Inject
constructor(private val componentFactory: SystemUIDisplaySubcomponent.Factory) :
    PerDisplayInstanceProviderWithTeardown<SystemUIDisplaySubcomponent>,
    PerDisplayInstanceProviderWithSetup<SystemUIDisplaySubcomponent> {

    override fun createInstance(displayId: Int): SystemUIDisplaySubcomponent? =
        try {
            componentFactory.create(displayId)
        } catch (e: Exception) {
            Log.e(
                TAG,
                "DisplayComponentInstanceProvider cannot create instance for display $displayId",
                e,
            )
            null
        }

    override fun destroyInstance(instance: SystemUIDisplaySubcomponent) {
        traceSection("Destroying a display component instance") {
            instance.displayCoroutineScope.cancel("Cancelling scope associated to the display.")
        }
        try {
            instance.lifecycleListeners.forEachTraced(
                "Notifying listeners of a display component destruction"
            ) {
                it.stop()
            }
        } catch (exception: DisplayNotFoundException) {
            Log.e(TAG, "Display no longer exists. Can't destroyInstance", exception)
        }
    }

    override fun setupInstance(instance: SystemUIDisplaySubcomponent) {
        try {
            instance.lifecycleListeners.forEachTraced(
                "Notifying listeners of a display component creation"
            ) {
                it.start()
            }
        } catch (exception: DisplayNotFoundException) {
            Log.e(TAG, "Display no longer exists. Can't setupInstance", exception)
        }
    }

    companion object {
        private const val TAG = "DisplayComponentInstanceProvider"
    }
}

@Module
object DisplayComponentRepository {
    @SysUISingleton
    @Provides
    fun provideDisplayComponentRepository(
        repositoryFactory: PerDisplayInstanceRepositoryImpl.Factory<SystemUIDisplaySubcomponent>,
        instanceProvider: DisplayComponentInstanceProvider,
    ): PerDisplayRepository<SystemUIDisplaySubcomponent> {
        return repositoryFactory.create(
            debugName = "DisplayComponentInstanceProvider",
            instanceProvider,
            createInstanceEagerly = isEagerInitializationEnabled(),
        )
    }
}
