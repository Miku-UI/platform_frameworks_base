/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.plugins

import android.content.Context
import android.content.pm.PackageManager
import com.android.systemui.plugins.annotations.ProtectedBaseInterface
import com.android.systemui.plugins.annotations.ProtectedInterface
import com.android.systemui.plugins.annotations.ProtectedReturn

/**
 * Plugins are separate APKs that are expected to implement interfaces provided by the host process
 * (usually SystemUI). Their code is dynamically loaded into the SysUI process which can allow for
 * multiple prototypes to be created and run on a single android build.
 *
 * Plugin lifecycle methods:
 * 1) [onCreate] is called before any other calls to the plugin are made. Here we provide both the
 *    host and plugin contexts to the plugin class. It has the opportunity at this point opportunity
 *    to do any context related initialization it may need to do before other method calls are made
 *    to the plugin interface.
 * 2) [onDestroy] is called to so that the plugin can perform any cleanup to ensure that it's not
 *    leaking into the SysUI process. This isn't usually necessary, but will be for certain kinds of
 *    resources like native memory allocations.
 *
 * See [PluginListener] for an explanation of how listener lifecycle calls relate to these.
 *
 * Any time a plugin APK is updated the plugin is destroyed and recreated to load the new code /
 * resources. It can also be destroyed and recreated at the request of the [PluginListener] within
 * the host process in some cases.
 *
 * Creating a Plugin interface:
 *
 * To create a plugin hook, first create an interface in frameworks/base/packages/SystemUI/plugin
 * that extends Plugin. Include in it any hooks you want to be able to call into from the host and
 * create interface methods for data that you may need to pass into, or query from the plugin.
 *
 * Once an interface has been defined, implementing code may simply add a [PluginListener] to
 * receive plugin lifecycle events whenever new plugins are installed, updated, or enabled via
 * PackageManager / adb.
 *
 * Any time changes to the plugin interface are made, the version of that interface should be
 * changed to ensure old plugins aren't loaded, as interface mismatch at runtime will crash the host
 * process. Note that any changes to classes used by the interface should also increment this
 * version number, as they are implicitly part of the set of classes provided by the host process to
 * the plugin. For additional safety, you may want to annotate the plugin interface with
 * [ProtectedInterface]. This will wrap plugins within a proxy implementation and help prevent
 * version mismatch issues from propagating to the host process when they occur.
 *
 * Default method implementations can be used by plugin interfaces, and will be provided to the
 * plugin by the host when they are defined. However, doing this may cause incompatibility issues
 * when the plugin apk is built with gradle and the host apk is built with soong (or vice versa).
 *
 * Implementing a Plugin:
 *
 * See the ExamplePlugin for an example Android.mk on how to compile a plugin. Note that
 * SystemUIPluginLib and any interface dependencies should not be statically included for plugins.
 * These classes can instead be omitted from the plugin apk (reducing apk size) as they will be
 * provided by the host process's [ClassLoader] when the plugin is loaded at runtime.
 *
 * Plugin security is based around a signature permission, so plugins must hold the following
 * permission in their manifest. The plugin and host also should be signed by the same key.
 *
 * ```
 * <uses-permission android:name="com.android.systemui.permission.PLUGIN" />
 * ```
 *
 * A plugin is found by querying [PackageManager] for a plugin service with the action name
 * associated with the plugin interface. For an implementation to be discovered then, the manifest
 * should include a service pointing at your implementation specifying the related name. The
 * specified class need not actually implement a service, only the related plugin interface.
 *
 * ```
 * <service android:name=".TestOverlayPlugin">
 *     <intent-filter>
 *         <action android:name="com.android.systemui.action.PLUGIN_COMPONENT" />
 *     </intent-filter>
 * </service>
 * ```
 */
@ProtectedBaseInterface
interface Plugin {
    @Deprecated(
        "Prefer declaring versions using annotations",
        ReplaceWith("@ProvidesInterface(action = ACTION, version = VERSION)"),
    )
    @get:ProtectedReturn(statement = "return -1;")
    val version: Int
        get() = -1

    fun onCreate(hostContext: Context, pluginContext: Context) {}

    fun onDestroy() {}
}
