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

package com.android.wm.shell.desktopai.api

/**
 * Definition for an event source that produces [TriggerEvent]s.
 *
 * Implementations of this interface wrap specific system listeners (like ShellController or
 * InputManager) and normalize their signals into standard [TriggerEvent] objects.
 */
interface ITriggerSource {
    /**
     * Starts listening to the underlying system component.
     *
     * @param onEvent A lambda that the source must call whenever an event occurs.
     */
    fun start(onEvent: (TriggerEvent) -> Unit)
}
