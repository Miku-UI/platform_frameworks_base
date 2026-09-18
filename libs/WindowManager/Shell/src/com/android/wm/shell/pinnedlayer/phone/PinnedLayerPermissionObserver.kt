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

package com.android.wm.shell.pinnedlayer.phone

import android.app.AppOpsManager
import android.app.TaskInfo
import android.content.Context
import android.content.pm.PackageManager
import android.util.SparseArray
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerLogs.logD
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerLogs.logW

/**
 * Observes permission changes for pinned tasks and closes them when new permission state does not
 * allow to run the task on pinned layer.
 */
class PinnedLayerPermissionObserver(
    private val context: Context,
    private val mainShellExecutor: ShellExecutor,
    private val pinnedLayerController: PinnedLayerController,
) : PinnedLayerController.PinnedTasksListener {

    private val appOpsManager: AppOpsManager =
        checkNotNull(context.getSystemService(AppOpsManager::class.java))

    // Stores registered listeners, per 'taskId'.
    private val appOpsListeners = SparseArray<AppOpsManager.OnOpChangedListener>()

    init {
        pinnedLayerController.addPinnedTasksListener(this)
    }

    override fun onPinnedTasksAdded(taskInfo: TaskInfo) {
        if (appOpsListeners.contains(taskInfo.taskId)) {
            return
        }
        val appOpsCallback =
            object : AppOpsManager.OnOpChangedListener {
                override fun onOpChanged(op: String, packageName: String) {
                    if (!isPipAllowed(taskInfo)) {
                        logD(
                            "onOpChanged: PiP is not allowed for taskInfo=%s, closing pinned task",
                            taskInfo,
                        )
                        mainShellExecutor.execute { pinnedLayerController.closeTask(taskInfo) }
                    }
                }
            }
        appOpsListeners.put(taskInfo.taskId, appOpsCallback)
        appOpsManager.startWatchingMode(
            AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
            taskInfo.getPackageName(),
            appOpsCallback,
        )
    }

    override fun onPinnedTasksRemoved(taskInfo: TaskInfo) {
        val appOpsCallback = appOpsListeners.removeReturnOld(taskInfo.taskId)
        if (appOpsCallback != null) {
            appOpsManager.stopWatchingMode(appOpsCallback)
        }
    }

    private fun isPipAllowed(taskInfo: TaskInfo): Boolean {
        val packageName = taskInfo.getPackageName() ?: return false
        val packageManager = context.packageManager
        try {
            val appInfo =
                packageManager.getApplicationInfoAsUser(
                    packageName,
                    /* flags= */ 0,
                    taskInfo.userId,
                )
            val mode =
                appOpsManager.checkOpNoThrow(
                    AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                    appInfo.uid,
                    packageName,
                )
            return mode == AppOpsManager.MODE_ALLOWED
        } catch (_: PackageManager.NameNotFoundException) {
            logW(
                "isPipAllowed: Failed to find applicationInfo for packageName=%s " +
                    "and userId=%d",
                packageName,
                taskInfo.userId,
            )
        }
        return false
    }

    private fun TaskInfo.getPackageName(): String? {
        val packageName =
            baseActivity?.packageName
                ?: topActivity?.packageName
                ?: origActivity?.packageName
                ?: realActivity?.packageName
                ?: return null
        if (packageName.isEmpty()) {
            logW("Failed to find package name for taskInfo=%s", this)
        }
        return packageName
    }
}
