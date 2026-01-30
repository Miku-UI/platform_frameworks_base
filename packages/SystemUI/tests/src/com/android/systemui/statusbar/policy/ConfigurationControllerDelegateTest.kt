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

package com.android.systemui.statusbar.policy

import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class ConfigurationControllerDelegateTest : SysuiTestCase() {

    private val mockDelegate =
        mock<ConfigurationController>().apply { whenever(nightModeName).thenReturn("") }
    private val mockListener = mock<ConfigurationController.ConfigurationListener>()
    private val mockConfiguration = mock<Configuration>()

    private val controllerDelegate =
        ConfigurationControllerDelegate().apply { setDelegate(mockDelegate) }

    @Test
    fun addCallback_callsDelegate() {
        controllerDelegate.addCallback(mockListener)
        verify(mockDelegate).addCallback(mockListener)
    }

    @Test
    fun removeCallback_callsDelegate() {
        controllerDelegate.removeCallback(mockListener)
        verify(mockDelegate).removeCallback(mockListener)
    }

    @Test
    fun onConfigurationChanged_callsDelegate() {
        controllerDelegate.onConfigurationChanged(mockConfiguration)
        verify(mockDelegate).onConfigurationChanged(mockConfiguration)
    }

    @Test
    fun dispatchOnMovedToDisplay_callsDelegate() {
        val testDisplayId = 1
        controllerDelegate.dispatchOnMovedToDisplay(testDisplayId, mockConfiguration)
        verify(mockDelegate).dispatchOnMovedToDisplay(testDisplayId, mockConfiguration)
    }

    @Test
    fun notifyThemeChanged_callsDelegate() {
        controllerDelegate.notifyThemeChanged()
        verify(mockDelegate).notifyThemeChanged()
    }

    @Test
    fun isLayoutRtl_callsDelegate() {
        controllerDelegate.isLayoutRtl
        verify(mockDelegate).isLayoutRtl()
    }

    @Test
    fun getNightModeName_callsDelegate() {
        controllerDelegate.nightModeName
        verify(mockDelegate).getNightModeName()
    }
}
