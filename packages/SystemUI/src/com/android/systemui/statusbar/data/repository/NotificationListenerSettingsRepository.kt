/*
 * Copyright (C) 2023 The Android Open Source Project
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
 *
 */

package com.android.systemui.statusbar.data.repository

import com.android.systemui.Dumpable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dump.DumpManager
import com.android.systemui.statusbar.NotificationListener
import com.android.systemui.util.asIndenting
import com.android.systemui.util.println
import java.io.PrintWriter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

/** Exposes state pertaining to settings tracked over the [NotificationListener] boundary. */
@SysUISingleton
class NotificationListenerSettingsRepository @Inject constructor(dumpManager: DumpManager) :
    Dumpable {
    /** Should icons for silent notifications be shown in the status bar? */
    val showSilentStatusIcons = MutableStateFlow(true)

    init {
        dumpManager.registerCriticalDumpable(this)
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        pw.asIndenting().run { println("showSilentStatusIcons", showSilentStatusIcons.value) }
    }
}
