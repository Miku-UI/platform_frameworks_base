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
package com.android.wm.shell.hierarchy.modes

/**
 * Manages the mapping of modes to containers. To be implemented by each form factor.
 */
interface FormFactorModes {
    /**
     * Returns the list of available modes for a display. This list is used to notify modes when
     * displays are added/removed to allow them to prepopulate the hierarchy with necessary
     * containers for that display.
     *
     * This is intended to be overridden by subclasses.
     */
    open fun getAvailableModesForDisplay(displayId: Int): List<Mode> {
        return mutableListOf()
    }
}