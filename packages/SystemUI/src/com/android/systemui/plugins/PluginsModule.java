/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.plugins;

import static com.android.systemui.shared.plugins.PluginManagerImpl.PLUGIN_CLASSLOADER;
import static com.android.systemui.shared.plugins.PluginManagerImpl.PLUGIN_THREAD;

import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;

import com.android.systemui.dagger.PluginModule;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.res.R;
import com.android.systemui.shared.plugins.PluginActionManager;
import com.android.systemui.shared.plugins.PluginEnabler;
import com.android.systemui.shared.plugins.PluginInstance;
import com.android.systemui.shared.plugins.PluginManagerImpl;
import com.android.systemui.shared.plugins.PluginPrefs;
import com.android.systemui.shared.plugins.VersionChecker;
import com.android.systemui.shared.plugins.VersionCheckerImpl;
import com.android.systemui.util.concurrency.GlobalConcurrencyModule;
import com.android.systemui.util.concurrency.ThreadFactory;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import java.util.Arrays;
import java.util.concurrent.Executor;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Dagger Module for code related to plugins.
 *
 * Covers code both in com.android.systemui.plugins and code in
 * com.android.systemui.shared.plugins.
 */
@Module(includes = {GlobalConcurrencyModule.class})
public abstract class PluginsModule {
    @Binds
    abstract PluginEnabler bindsPluginEnablerImpl(PluginEnablerImpl impl);

    @Binds
    abstract VersionChecker bindVersionCheckerImpl(VersionCheckerImpl impl);

    @Binds
    abstract PluginManager providesPluginManager(PluginManagerImpl impl);

    @Provides
    @Named(PLUGIN_CLASSLOADER)
    static ClassLoader provideClassLoader() {
        return PluginModule.class.getClassLoader();
    }

    @Provides
    @Singleton
    static PluginActionManager.Factory providePluginInstanceManagerFactory(Context context,
            PackageManager packageManager, @Main Executor mainExecutor,
            @Named(PLUGIN_THREAD) Executor pluginExecutor,
            NotificationManager notificationManager, PluginEnabler pluginEnabler,
            PluginManager.Config pluginConfig,
            PluginInstance.Factory pluginInstanceFactory) {
        return new PluginActionManager.Factory(
                context, packageManager, mainExecutor, pluginExecutor,
                notificationManager, pluginEnabler, pluginConfig, pluginInstanceFactory);
    }

    @Provides
    @Singleton
    @Named(PLUGIN_THREAD)
    static Executor providesPluginExecutor(ThreadFactory threadFactory) {
        return threadFactory.buildExecutorOnNewThread("plugin");
    }

    @Provides
    static PluginPrefs providesPluginPrefs(Context context) {
        return new PluginPrefs(context);
    }

    @Provides
    static PluginManager.Config providesPluginConfig(Context context) {
        String[] plugins = context.getResources().getStringArray(R.array.config_pluginAllowlist);
        return new PluginManager.Config(Arrays.asList(plugins));
    }
}
