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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.display.dagger.SystemUIDisplaySubcomponent
import com.android.systemui.testKosmos
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
@SmallTest
class DisplayComponentInstanceProviderTest : SysuiTestCase() {

    private val kosmos = testKosmos()

    private val underTest =
        DisplayComponentInstanceProvider(kosmos.fakeSysuiDisplayComponentFactory)

    @Test
    fun createInstance_doesNotNotifyLifecycleListenersWithStart() {
        val lifecycleListeners =
            (1..10).map { mock<SystemUIDisplaySubcomponent.LifecycleListener>() }

        kosmos.sysUiDefaultDisplaySubcomponentLifecycleListeners += lifecycleListeners

        underTest.createInstance(displayId = 123)

        lifecycleListeners.forEach { verify(it, never()).start() }
    }

    @Test
    fun setupInstance_notifiesLifecycleListenersWithSetupInstance() {
        val lifecycleListeners =
            (1..10).map { mock<SystemUIDisplaySubcomponent.LifecycleListener>() }

        kosmos.sysUiDefaultDisplaySubcomponentLifecycleListeners += lifecycleListeners

        val instance = underTest.createInstance(displayId = 123)!!
        underTest.setupInstance(instance)

        lifecycleListeners.forEach { verify(it).start() }
    }

    @Test
    fun destroyInstance_notifiesLifecycleListenersWithStop() {
        val lifecycleListeners =
            (1..10).map { mock<SystemUIDisplaySubcomponent.LifecycleListener>() }

        kosmos.sysUiDefaultDisplaySubcomponentLifecycleListeners += lifecycleListeners

        val subcomponent = underTest.createInstance(displayId = 123)
        underTest.destroyInstance(subcomponent!!)

        lifecycleListeners.forEach { verify(it).stop() }
    }
}
