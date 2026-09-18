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

package com.android.internal.inputmethod;

/**
 * Interface to receive callbacks from the IME Switcher Menu controller.
 */
@RequiresNoPermission
oneway interface IImeSwitcherMenuListener {

    /**
     * Called when the visibility of the IME Switcher Menu changed for the given user on the given
     * display.
     *
     * @param visible   the new visibility of the menu.
     * @param displayId the ID of the display where the menu visibility changed.
     * @param userId    the ID of the user whose menu visibility changed.
     */
    void onVisibilityChanged(boolean visible, int displayId, int userId);

    /**
     * Called when an IME and subtype was selected in the IME Switcher Menu by the given user. This
     * will switch to the IME if it is enabled and installed, and otherwise will do nothing. If the
     * subtype index is also supplied (not {@code -1}) and valid, also switches to it, otherwise the
     * system devices the most sensible default subtype to use.
     *
     * @param imeId        the ID of the selected IME.
     * @param subtypeIndex the selected subtype, as an index in the input method's array of
     *                     subtypes, or {@code -1} if the system should decide the most sensible
     *                     subtype.
     * @param userId       the ID of the user that selected the IME and subtype.
     */
    void onImeAndSubtypeSelected(in String imeId, int subtypeIndex, int userId);
}
