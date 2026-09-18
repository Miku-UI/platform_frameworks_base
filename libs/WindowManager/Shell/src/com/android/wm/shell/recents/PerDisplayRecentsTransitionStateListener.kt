/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.wm.shell.recents

import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.sysui.ShellInit
import javax.inject.Inject

/** Listens and records which displays have an active recents transition. */
@WMSingleton
class PerDisplayRecentsTransitionStateListener
@Inject
constructor(shellInit: ShellInit, private val recentsTransitionHandler: RecentsTransitionHandler) :
    RecentsTransitionStateListener {
    private val animatingDisplays: HashSet<Int> = HashSet()

    init {
        shellInit.addInitCallback({ onInit() }, this)
    }

    private fun onInit() {
        recentsTransitionHandler.addTransitionStateListener(this)
    }

    override fun onTransitionStateChanged(state: Int, displayId: Int) {
        when (state) {
            RecentsTransitionStateListener.TRANSITION_STATE_REQUESTED -> {
                animatingDisplays.add(displayId)
                return
            }

            RecentsTransitionStateListener.TRANSITION_STATE_NOT_RUNNING -> {
                // No recents transition running - remove from animating displays
                animatingDisplays.remove(displayId)
                return
            }

            else -> {}
        }
    }

    /** Checks if recents animation is active for the given [displayId]. */
    fun isRecentsAnimationActive(displayId: Int): Boolean {
        return animatingDisplays.contains(displayId)
    }
}
