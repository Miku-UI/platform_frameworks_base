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

import android.service.selectiontoolbar.RemoteSelectionToolbar
import android.service.selectiontoolbar.SelectionToolbarRenderService
import android.util.IndentingPrintWriter
import android.util.Slog
import android.view.selectiontoolbar.ShowInfo
import java.io.FileDescriptor
import java.io.PrintWriter

class SysUiSelectionToolbarRenderService : SelectionToolbarRenderService() {
    // TODO(b/215497659): handle remove if the client process dies.
    // Only show one toolbar, dismiss the old ones and remove from cache
    private val toolbarCache = mutableMapOf<Int, RemoteSelectionToolbar>()

    override fun onShow(uid: Int, showInfo: ShowInfo, callbackWrapper: RemoteCallbackWrapper) {
        val existingToolbar = toolbarCache[uid]
        // Only allow one package to create one toolbar
        if (existingToolbar != null) {
            Slog.e(TAG, "Do not allow multiple toolbar for the uid : $uid")
            return
        }

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
        toolbarUiExecutor.execute { toolbar.show(showInfo) }

        Slog.v(TAG, "onShow() for uid: $uid")
    }

    override fun onHide(uid: Int) {
        val toolbar = toolbarCache[uid]
        if (toolbar != null) {
            Slog.v(TAG, "onHide() for uid: $uid")
            toolbarUiExecutor.execute { toolbar.hide(uid) }
        }
    }

    override fun onDismiss(uid: Int) {
        Slog.v(TAG, "onDismiss() for uid: $uid")
        removeAndDismissToolbar(uid)
    }

    private fun removeAndDismissToolbar(uid: Int) {
        val toolbar = toolbarCache[uid]
        if (toolbar != null) {
            toolbarUiExecutor.execute { toolbar.dismiss(uid) }
            toolbarCache -= uid
        }
    }

    override fun onUidDied(uid: Int) {
        Slog.w(TAG, "onUidDied for uid: $uid")
        removeAndDismissToolbar(uid)
    }

    override fun dump(fd: FileDescriptor, pw: PrintWriter, args: Array<String>) {
        val ipw = IndentingPrintWriter(pw)
        ipw.print("number selectionToolbar: ")
        ipw.println(toolbarCache.size)
        ipw.increaseIndent()
        toolbarCache.forEach {
            it
            val uid = it.key
            val selectionToolbar = it.value
            ipw.print("uid: ")
            ipw.println(uid)
            selectionToolbar.dump("", ipw)
            ipw.println()
        }
        ipw.decreaseIndent()
    }

    companion object {
        private const val TAG = "SysUiRemoteToolbarRenderService"
    }
}
