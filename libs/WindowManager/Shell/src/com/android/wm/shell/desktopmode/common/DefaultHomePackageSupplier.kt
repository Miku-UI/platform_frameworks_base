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

package com.android.wm.shell.desktopmode.common

import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.UserHandle
import android.util.SparseArray
import com.android.window.flags.Flags
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import java.util.function.Supplier

/**
 * This supplies the package name of default home in an efficient way. The query to package manager
 * only executes on initialization and when the preferred activity (e.g. default home) is changed.
 * Note that this returns null package name if the default home is setup wizard.
 */
class DefaultHomePackageSupplier(
    private val context: Context,
    shellInit: ShellInit,
    private val shellController: ShellController,
    @ShellMainThread private val mainHandler: Handler,
) : BroadcastReceiver(), Supplier<String?> {

    private val defaultHomePackageUserMap: SparseArray<String> = SparseArray()

    init {
        shellInit.addInitCallback({ this::onInit }, this)
    }

    private fun onInit() {
        context.registerReceiverForAllUsers(
            this,
            IntentFilter(Intent.ACTION_PREFERRED_ACTIVITY_CHANGED),
            null, /* broadcastPermission */
            mainHandler,
        )
    }

    private fun updateDefaultHomePackage(): String? {
        val currentUserId = shellController.currentUserId

        var defaultHomePackage: String?
        if (Flags.homePackageWindowingExemptionsBugFix()) {
            val defaultHomeInfo =
                context.packageManager.resolveActivityAsUser(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                    0,
                    currentUserId,
                )
            defaultHomePackage =
                defaultHomeInfo?.takeIf { it.priority >= 0 }?.activityInfo?.packageName
        } else {
            defaultHomePackage =
                context
                    .getSystemService(RoleManager::class.java)
                    .getRoleHoldersAsUser(RoleManager.ROLE_HOME, UserHandle.of(currentUserId))
                    .firstOrNull()
        }

        var flags = PackageManager.MATCH_SYSTEM_ONLY
        if (Flags.homePackageWindowingExemptionsBugFix()) {
            flags = flags or PackageManager.MATCH_DISABLED_COMPONENTS
        }
        val isSetupWizard =
            defaultHomePackage != null &&
                context.packageManager.resolveActivityAsUser(
                    Intent()
                        .setPackage(defaultHomePackage)
                        .addCategory(Intent.CATEGORY_SETUP_WIZARD),
                    flags,
                    currentUserId,
                ) != null
        if (isSetupWizard) {
            defaultHomePackage = null
        }

        defaultHomePackageUserMap.put(currentUserId, defaultHomePackage)
        return defaultHomePackage
    }

    override fun onReceive(contxt: Context?, intent: Intent?) {
        updateDefaultHomePackage()
    }

    override fun get(): String? {
        val currentUserId = shellController.currentUserId
        return defaultHomePackageUserMap.get(currentUserId) ?: updateDefaultHomePackage()
    }
}
