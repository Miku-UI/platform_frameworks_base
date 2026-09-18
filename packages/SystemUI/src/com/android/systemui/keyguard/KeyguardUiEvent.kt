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

package com.android.systemui.keyguard

import com.android.internal.logging.UiEvent
import com.android.internal.logging.UiEventLogger

enum class KeyguardUiEvent(private val mId: Int) : UiEventLogger.UiEventEnum {
    @UiEvent(doc = "Secured lockscreen is opened.") LOCKSCREEN_OPEN_SECURE(405),
    @UiEvent(doc = "Lockscreen without security is opened.") LOCKSCREEN_OPEN_INSECURE(406),
    @UiEvent(doc = "Secured lockscreen is closed.") LOCKSCREEN_CLOSE_SECURE(407),
    @UiEvent(doc = "Lockscreen without security is closed.") LOCKSCREEN_CLOSE_INSECURE(408),
    @UiEvent(doc = "Secured bouncer is opened.") BOUNCER_OPEN_SECURE(409),
    @UiEvent(doc = "Bouncer without security is opened.") BOUNCER_OPEN_INSECURE(410),
    @UiEvent(doc = "Secured bouncer is closed.") BOUNCER_CLOSE_SECURE(411),
    @UiEvent(doc = "Bouncer without security is closed.") BOUNCER_CLOSE_INSECURE(412);

    override fun getId(): Int {
        return mId
    }
}
