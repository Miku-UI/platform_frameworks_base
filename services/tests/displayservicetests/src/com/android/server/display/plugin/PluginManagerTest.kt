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

package com.android.server.display.plugin

import android.content.Context
import androidx.test.filters.SmallTest
import com.android.server.display.feature.DisplayManagerFlags
import com.android.server.display.plugin.PluginManager.PluginChangeListener
import com.android.server.display.plugin.PluginManager.PluginProviderDependencies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

private val TEST_PLUGIN_TYPE = PluginType(Int::class.java, "test_type")
private val DISPLAY_ID = "display_id"

@SmallTest
class PluginManagerTest {

    private val mockContext = mock<Context>()
    private val mockFlags = mock<DisplayManagerFlags>()
    private val mockListener = mock<PluginChangeListener<Int>>()
    private val mockDependency = mock<PluginProviderDependencies>()
    private val testInjector = TestInjector()

    @Test
    fun testBootCompleted_enabledPluginManager() {
        val pluginManager = createPluginManager()

        pluginManager.onBootCompleted()

        verify(testInjector.mockPlugin1).onBootCompleted()
        verify(testInjector.mockPlugin2).onBootCompleted()
    }

    @Test
    fun testSubscribe() {
        val pluginManager = createPluginManager()

        pluginManager.subscribe(TEST_PLUGIN_TYPE, DISPLAY_ID, mockListener)

        verify(testInjector.mockStorage).addListener(TEST_PLUGIN_TYPE, DISPLAY_ID, mockListener)
    }

    @Test
    fun testUnsubscribe() {
        val pluginManager = createPluginManager()

        pluginManager.unsubscribe(TEST_PLUGIN_TYPE, DISPLAY_ID, mockListener)

        verify(testInjector.mockStorage).removeListener(TEST_PLUGIN_TYPE, DISPLAY_ID, mockListener)
    }

    @Test
    fun testConstructor_passesDependenciesToInjector() {
        val mockDeps = mock<PluginProviderDependencies>()
        val injector = TestInjector()

        // The call to loadPlugins happens inside the constructor.
        PluginManager(mockContext, mockDeps, mockFlags, injector)

        // Verify that the dependencies object was captured by the injector.
        assertNotNull(injector.capturedDependencies)
        assertEquals(mockDeps, injector.capturedDependencies)
    }

    private fun createPluginManager(): PluginManager {
        return PluginManager(mockContext, mockDependency, mockFlags, testInjector)
    }

    private class TestInjector : PluginManager.Injector() {
        val mockStorage = mock<PluginStorage>()
        val mockPlugin1 = mock<Plugin>()
        val mockPlugin2 = mock<Plugin>()
        var capturedDependencies: PluginProviderDependencies? = null

        override fun getPluginStorage(enabledTypes: Set<PluginType<*>>): PluginStorage {
            return mockStorage
        }

        override fun loadPlugins(
            context: Context,
            storage: PluginStorage,
            dependencies: PluginProviderDependencies,
            enabledTypes: Set<PluginType<*>>
        ): List<Plugin> {
            capturedDependencies = dependencies
            return listOf(mockPlugin1, mockPlugin2)
        }
    }
}
