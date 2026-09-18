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

package com.android.systemui.selectiontoolbar.app.service

import android.service.selectiontoolbar.SelectionToolbarRenderService
import android.util.IndentingPrintWriter
import android.util.Log
import android.util.Slog
import android.view.selectiontoolbar.ShowInfo
import com.android.systemui.selectiontoolbar.app.ui.RemoteSelectionToolbar
import java.io.FileDescriptor
import java.io.PrintWriter

class SysUiSelectionToolbarRenderService : SelectionToolbarRenderService() {

    // Only show one toolbar, dismiss the old ones and remove from cache
    private val toolbarCache = mutableMapOf<Int, RemoteSelectionToolbar>()

    override fun onShow(uid: Int, showInfo: ShowInfo, callbackWrapper: RemoteCallbackWrapper) {
        val existingToolbar = toolbarCache[uid]
        // Only allow one package to create one toolbar
        if (existingToolbar != null) {
            verboseLog("Reshow for existing toolbar for uid: $uid")
            existingToolbar.show(showInfo)
        } else {
            verboseLog("Show new toolbar for uid: $uid")
            val toolbar =
                RemoteSelectionToolbar(
                    uid,
                    this,
                    showInfo,
                    callbackWrapper,
                    ::transferTouch,
                    ::onPasteAction,
                )
            toolbarCache[uid] = toolbar
            toolbar.show(showInfo)
        }
    }

    override fun onHide(uid: Int) {
        val toolbar = toolbarCache[uid]
        if (toolbar != null) {
            verboseLog("onHide() for uid: $uid")
            toolbar.hide(uid)
        }
    }

    override fun onDismiss(uid: Int) {
        verboseLog("onDismiss() for uid: $uid")
        removeAndDismissToolbar(uid)
    }

    override fun onUidDied(uid: Int) {
        warnLog("onUidDied for uid: $uid")
        removeAndDismissToolbar(uid)
    }

    private fun removeAndDismissToolbar(uid: Int) {
        val toolbar = toolbarCache[uid]
        if (toolbar != null) {
            toolbar.dismiss(uid)
            toolbarCache -= uid
        }
    }

    override fun dump(fd: FileDescriptor, pw: PrintWriter, args: Array<String>) {
        val ipw = IndentingPrintWriter(pw)
        ipw.print("number selectionToolbar: ")
        ipw.println(toolbarCache.size)
        ipw.increaseIndent()
        toolbarCache.forEach {
            val uid = it.key
            val toolbar = it.value
            ipw.print("uid: ")
            ipw.println(uid)
            toolbar.dump("", ipw)
            ipw.println()
        }
        ipw.decreaseIndent()
    }

    companion object {
        private const val TAG = "SysUiRemoteToolbarRenderService"

        private val VERBOSE_LOG = Log.isLoggable(TAG, Log.VERBOSE)
        private val WARN_LOG = Log.isLoggable(TAG, Log.WARN)

        private fun verboseLog(message: String) {
            if (VERBOSE_LOG) {
                Slog.v(TAG, message)
            }
        }

        private fun warnLog(message: String) {
            if (WARN_LOG) {
                Slog.w(TAG, message)
            }
        }
    }
}
