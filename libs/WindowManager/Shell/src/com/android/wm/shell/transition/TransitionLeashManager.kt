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

package com.android.wm.shell.transition

import android.os.IBinder
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.window.TransitionInfo
import com.android.wm.shell.shared.TransitionUtil.createLeash
import com.android.wm.shell.shared.TransitionUtil.setUpSurface
import com.android.wm.shell.shared.TransitionUtil.skipReparenting

class TransitionLeashManager {
    /** Bookkeeping used to restore the original surfaces to transition participants. */
    private val surfaces: MutableMap<IBinder, Array<SurfaceControl?>> = mutableMapOf()

    /** Bookkeeping used to release leashes when the transition is done. */
    private val leashes: MutableMap<IBinder, MutableSet<SurfaceControl>> = mutableMapOf()

    /**
     * Removes internal references to a transition's surfaces and leashes. Must be called at the end
     * of a transition if leashes have been created using [TransitionLeashManager.setUpLeashes].
     */
    fun cleanUp(token: IBinder?) {
        if (token == null) return
        surfaces.remove(token)
        val leashes = leashes.remove(token) ?: return
        leashes.forEach { if (it.isValid) it.release() }
    }

    /** Removes any leashes previously added to a transition's participants. */
    fun detachLeashes(token: IBinder, info: TransitionInfo, transaction: Transaction) {
        val surfaces = surfaces.remove(token) ?: return

        // The first pass is necessary to update the participants so that we don't mistakenly
        // reparent anything to their parent's leash instead of the actual surface.
        for (i in info.changes.size - 1 downTo 0) {
            info.changes[i].leash = surfaces[i] ?: continue
        }

        // Then we reparent the participant surfaces to their original parents (either other
        // participants or the transition root).
        for (i in info.changes.size - 1 downTo 0) {
            val change = info.changes[i]
            val surface = surfaces[i] ?: continue

            val parent = change.parent
            if (parent != null) {
                val parentChange = info.getChange(parent)
                if (parentChange != null) {
                    transaction.reparent(surface, parentChange.leash)
                }
            }

            setUpSurface(change, info, i, transaction)
        }
    }

    /**
     * Wraps each participant of a transition in a leash. This new [SurfaceControl] is designed to
     * provide the same level of control to the next handler, while being easy to swap out for a new
     * one should the animation be handed over to a new handler.
     *
     * Participants can be explicitly excluded from leash creation by providing them as part of the
     * optional [excluded] set.
     */
    @JvmOverloads
    fun setUpLeashes(
        token: IBinder,
        info: TransitionInfo,
        transaction: Transaction,
        excluded: Set<TransitionInfo.Change>? = null,
    ) {
        val originalSurfaces = arrayOfNulls<SurfaceControl?>(info.changes.size)
        val leashes =
            leashes[token]
                ?: run {
                    val leashes = mutableSetOf<SurfaceControl>()
                    this.leashes[token] = leashes
                    leashes
                }

        for (i in info.changes.size - 1 downTo 0) {
            val change = info.changes[i]
            if (excluded?.contains(change) == true) continue

            originalSurfaces[i] = change.leash

            // The default check does not reparent dependent changes, but for leashes we need
            // dependent tasks to be wrapped as well, as they might be animated directly anyway.
            if (skipReparenting(change, info) && !isNestedTaskChange(change, info)) continue

            val leash = createLeash(info, change, i, transaction)
            // Only add the new leash to the set if one was created, otherwise we risk releasing the
            // participant's original surface on cleanup.
            if (leash != change.leash) leashes.add(leash)
            change.leash = leash
        }

        surfaces[token] = originalSurfaces
    }

    /**
     * Checks whether a [TransitionInfo] participant is a task _and_ is dependent on another task
     * participant.
     */
    private fun isNestedTaskChange(change: TransitionInfo.Change, info: TransitionInfo): Boolean {
        if (change.taskInfo?.taskId == null) return false
        val parent = change.parent ?: return true
        return info.getChange(parent)?.taskInfo == null
    }
}
