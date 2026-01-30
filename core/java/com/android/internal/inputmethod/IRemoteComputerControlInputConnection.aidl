/*
 * Copyright 2025 The Android Open Source Project
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

import android.view.KeyEvent;
import android.view.inputmethod.TextAttribute;
import com.android.internal.inputmethod.InputConnectionCommandHeader;

/**
 * Interface from computer control session to the application, allowing it to perform edits on the
 * current input field and other interactions with the application. These methods call into
 * corresponding {@link InputConnection} methods.
 */
oneway interface IRemoteComputerControlInputConnection {

    /**
     * Commit text to the text box and set the new cursor position.
     *
     * <p>This method commits the contents of the currently composing text, and then moves the
     * cursor according to {@code newCursorPosition}. If there is no composing text when this
     * method is called, the new text is inserted at the cursor position, removing text inside the
     * selection if any.
     *
     * @param text The text to commit. This may include styles.
     * @param newCursorPosition The new cursor position around the text. If > 0, this is relative to
     *                          the end of the text - 1; if <= 0, this is relative to the start
     *                          of the text. So a value of 1 will always advance the cursor to the
     *                          position after the full text being inserted.
     *
     * @see InputConnection#commitText(CharSequence, int)
     */
    void commitText(in InputConnectionCommandHeader header, in CharSequence text,
                    int newCursorPosition);

    /**
     * Replace the specific range in the editor with suggested text.
     *
     * <p>This method finishes whatever composing text is currently active and leaves the text
     * as-it, replaces the specific range of text with the passed CharSequence, and then moves the
     * cursor according to {@code newCursorPosition}.
     *
     * @param start the character index where the replacement should start.
     * @param end the character index where the replacement should end.
     * @param newCursorPosition The new cursor position around the text. If > 0, this is relative to
     *                          the end of the text - 1; if <= 0, this is relative to the start
     *                          of the text. So a value of 1 will always advance the cursor to the
     *                          position after the full text being inserted.
     * @param text the text to replace. This may include styles.
     *
     * @see InputConnection#replaceText(int, int, CharSequence, int, TextAttribute)
     */
    void replaceText(in InputConnectionCommandHeader header, int start, int end,
                     in CharSequence text, int newCursorPosition);

    /**
     * Send a key event to the process that is currently attached through this input connection.
     * The event will be dispatched like a normal key event, to the currently focused view; this
     * generally is the view that is providing this {@link InputConnection}.
     *
     * @param event The key event.
     *
     * @see KeyEvent
     * @see InputConnection#sendKeyEvent(KeyEvent)
     */
    void sendKeyEvent(in InputConnectionCommandHeader header, in KeyEvent event);
}
