/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.topui

import android.app.IActivityManager
import android.os.RemoteException
import android.util.Log
import com.android.systemui.Dumpable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dump.DumpManager
import java.io.PrintWriter
import java.util.Collections
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Implementation of [TopUiController] responsible for managing requests to boost SystemUI process
 * priority using Kotlin Coroutines for background tasks.
 *
 * This class tracks requests from various components using unique tags. It calls
 * [IActivityManager.setHasTopUi] only when the overall state changes (i.e., when the first
 * component requests top UI, or the last component releases it). Calls to IActivityManager are
 * performed on a background coroutine context.
 */
@SysUISingleton
class TopUiControllerImpl
@Inject
constructor(
    private val activityManager: IActivityManager,
    @Background private val bgContext: CoroutineContext,
    dumpManager: DumpManager,
) : TopUiController, Dumpable {

    private val scope = CoroutineScope(bgContext)

    // Set to store the tags of components currently requesting top UI priority.
    private val componentsRequestingTopUi = Collections.synchronizedSet(mutableSetOf<String>())

    // Tracks the last state sent to ActivityManager.setHasTopUi
    private var isTopUiCurrentlyRequested: Boolean = false

    init {
        dumpManager.registerDumpable(this)
    }

    @Synchronized
    override fun setRequestTopUi(requestTopUi: Boolean, componentTag: String) {
        val changed: Boolean =
            if (requestTopUi) {
                componentsRequestingTopUi.add(componentTag)
            } else {
                componentsRequestingTopUi.remove(componentTag)
            }

        if (changed) {
            val shouldRequestTopUi = componentsRequestingTopUi.isNotEmpty()
            if (isTopUiCurrentlyRequested != shouldRequestTopUi) {
                isTopUiCurrentlyRequested = shouldRequestTopUi
                scope.launch {
                    try {
                        activityManager.setHasTopUi(shouldRequestTopUi)
                    } catch (e: RemoteException) {
                        Log.e(TAG, "Failed to call setHasTopUi($shouldRequestTopUi)", e)
                        isTopUiCurrentlyRequested = !shouldRequestTopUi
                    }
                }
            }
        }
    }

    override fun dump(pw: PrintWriter, args: Array<String>) {
        pw.println("$TAG state:")
        pw.println("  isTopUiCurrentlyRequested=$isTopUiCurrentlyRequested")
        val currentRequesters = componentsRequestingTopUi.toList()
        pw.println("  componentsRequestingTopUi (${currentRequesters.size}):")
        currentRequesters.forEach { component -> pw.println("    - $component") }
    }

    companion object {
        private const val TAG = "TopUiControllerImpl"
    }
}
