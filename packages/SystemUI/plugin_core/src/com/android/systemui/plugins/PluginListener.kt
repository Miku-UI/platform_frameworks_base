/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.plugins

import android.content.Context
import com.android.systemui.log.core.MessageBuffer

/**
 * Interface for listening to plugins being connected and disconnected.
 *
 * The call order for a plugin is
 * 1) [onPluginAttached] Called when a new plugin is added to the device, or an existing plugin was
 *    replaced by the package manager. Will only be called once per package manager event. If
 *    multiple non-conflicting packages which have the same plugin interface are installed on the
 *    device, then this method can be called multiple times with different instances of
 *    [PluginLifecycleManager] (as long as `allowMultiple` was set to true when the listener was
 *    registered with [PluginManager.addPluginListener]).
 * 2) [onPluginLoaded] Called whenever a new instance of the plugin object is created and ready for
 *    use. Can be called multiple times per [PluginLifecycleManager], but will always pass a newly
 *    created plugin object. [onPluginUnloaded] with the previous plugin object will be called
 *    before another call to [onPluginLoaded] is made. This method will be called once automatically
 *    after [onPluginAttached]. Besides the initial call, [onPluginLoaded] will occur due to
 *    [PluginLifecycleManager.loadPlugin].
 * 3) [onPluginUnloaded] Called when a request to unload the plugin has been received. This can be
 *    triggered from a related call to [PluginLifecycleManager.unloadPlugin] or for any reason that
 *    [onPluginDetached] would be triggered.
 * 4) [onPluginDetached] Called when the package is removed from the device, disabled, or replaced
 *    due to an external trigger. These are events from the android package manager.
 *
 * @param <T> is the target plugin type
 */
interface PluginListener<T : Plugin> {
    /** Returns a LogBuffer that PluginInstances should use to log messages. Optional. */
    val logBuffer: MessageBuffer?
        get() = null

    /**
     * Called when the plugin has been loaded and is ready to be used. This may be called multiple
     * times if multiple plugins are allowed. It may also be called in the future if the plugin
     * package changes and needs to be reloaded.
     */
    @Deprecated("Migrate to {@link #onPluginLoaded} or {@link #onPluginAttached}")
    fun onPluginConnected(plugin: T, pluginContext: Context) {
        // Optional
    }

    /**
     * Called when the plugin is first attached to the host application. [onPluginLoaded] will be
     * automatically called as well when first attached if true is returned. This may be called
     * multiple times if multiple plugins are allowed. It may also be called in the future if the
     * plugin package changes and needs to be reloaded. Each call to [onPluginAttached] will provide
     * a new or different [PluginLifecycleManager].
     *
     * @return returning true will immediately load the plugin and call onPluginLoaded with the
     *   created object. false will skip loading, but the listener can load it at any time using the
     *   provided PluginLifecycleManager. Loading plugins immediately is the default behavior.
     */
    fun onPluginAttached(manager: PluginLifecycleManager<T>): Boolean {
        // Optional
        return true
    }

    /** Called when a plugin has been uninstalled/updated and should be removed from use. */
    @Deprecated("Migrate to {@link #onPluginDetached} or {@link #onPluginUnloaded}")
    fun onPluginDisconnected(plugin: T) {
        // Optional
    }

    /**
     * Called when the plugin has been detached from the host application. Implementers should no
     * longer attempt to reload it via this [PluginLifecycleManager]. If the package was updated and
     * not removed, then [onPluginAttached] will be called again when the updated package is
     * available.
     */
    fun onPluginDetached(manager: PluginLifecycleManager<T>) {
        // Optional
    }

    /**
     * Called when the plugin is loaded into the host's process and is available for use. This can
     * happen several times if clients are using [PluginLifecycleManager] to manipulate a plugin's
     * load state. Each call to [onPluginLoaded] will have a matched call to [onPluginUnloaded] when
     * that plugin object should no longer be used.
     */
    fun onPluginLoaded(plugin: T, pluginContext: Context, manager: PluginLifecycleManager<T>) {
        // Optional, default to deprecated behavior
        onPluginConnected(plugin, pluginContext)
    }

    /**
     * Called when the plugin should no longer be used. Listeners should clean up all references to
     * the relevant plugin so that it can be garbage collected. If the plugin object is required in
     * the future a call can be made to [PluginLifecycleManager.loadPlugin] to create a new plugin
     * object and trigger [onPluginLoaded].
     */
    fun onPluginUnloaded(plugin: T, manager: PluginLifecycleManager<T>) {
        // Optional, default to deprecated behavior
        onPluginDisconnected(plugin)
    }
}
