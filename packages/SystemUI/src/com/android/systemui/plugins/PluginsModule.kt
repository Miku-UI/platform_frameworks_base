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
package com.android.systemui.plugins

import android.content.Context
import android.os.SystemProperties
import com.android.systemui.dagger.PluginModule
import com.android.systemui.dagger.qualifiers.TestHarness
import com.android.systemui.res.R
import com.android.systemui.shared.plugins.PackageConfig
import com.android.systemui.shared.plugins.PluginEnabler
import com.android.systemui.shared.plugins.PluginEnvironment
import com.android.systemui.shared.plugins.PluginManagerImpl
import com.android.systemui.shared.plugins.PluginPrefs
import com.android.systemui.shared.plugins.VersionChecker
import com.android.systemui.util.concurrency.GlobalConcurrencyModule
import com.android.systemui.util.concurrency.ThreadFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import java.util.concurrent.Executor
import javax.inject.Named
import javax.inject.Singleton

/**
 * Dagger Module for code related to plugins.
 *
 * Covers code both in com.android.systemui.plugins and code in com.android.systemui.shared.plugins.
 */
@Module(includes = [GlobalConcurrencyModule::class])
abstract class PluginsModule {
    @Binds abstract fun bindsPluginEnablerImpl(impl: PluginEnablerImpl): PluginEnabler

    @Binds abstract fun bindVersionCheckerImpl(impl: VersionChecker.Impl): VersionChecker

    @Binds abstract fun providesPluginManager(impl: PluginManagerImpl): PluginManager

    companion object {
        @Provides
        @Named(PluginManagerImpl.PLUGIN_CLASSLOADER)
        fun provideClassLoader(): ClassLoader {
            return PluginModule::class.java.classLoader!!
        }

        @Provides
        @Singleton
        @Named(PluginManagerImpl.PLUGIN_THREAD)
        fun providesPluginExecutor(threadFactory: ThreadFactory): Executor {
            return threadFactory.buildExecutorOnNewThread("plugin")
        }

        @Provides
        fun providesPluginPrefs(context: Context): PluginPrefs {
            return PluginPrefs(context)
        }

        @Provides
        fun providesEnvironment(@TestHarness isTestHarness: Boolean): PluginEnvironment {
            val isDebugPropSet = SystemProperties.getBoolean("debug.sysui.plugins", false)
            return PluginEnvironment(isTestHarness = isTestHarness, isDebugPropSet = isDebugPropSet)
        }

        @Provides
        fun providesPackageConfig(context: Context): PackageConfig {
            val privilegedPlugins = context.resources.getStringArray(R.array.config_pluginAllowlist)
            return PackageConfig(*privilegedPlugins)
        }
    }
}
