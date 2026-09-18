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

package com.android.wm.shell.windowdecor

/** Interface to create, check status of, and update hover status of layout menu. */
interface LayoutMenuController {
    /** Returns whether the layout menu is currently open. */
    val isLayoutMenuActive: Boolean

    /** Updates whether the layout button is being hovered over. */
    fun onLayoutButtonHoverStateChanged()

    /** Notifies that the layout button has received a [ACTION_HOVER_ENTER] event. */
    fun onLayoutButtonHoverExit()

    /** Notifies that the layout button has received a [ACTION_HOVER_ENTER] event. */
    fun onLayoutButtonHoverEnter()

    /** Creates the layout menu if supported by caption. */
    fun createLayoutMenu()

    /** Closes the layout menu. */
    fun closeLayoutMenu()

    /** Updates on whether the maximize button is being hovered over. */
    fun setHeaderMaximizeButtonHovered(hovered: Boolean)
}
