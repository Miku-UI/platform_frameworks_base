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

package com.android.extensions.computercontrol.input;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_UP;

import android.annotation.NonNull;
import android.os.SystemClock;

import com.android.extensions.computercontrol.ComputerControlSession;

/**
 * A key input event that can be injected into computer control sessions via
 * {@link ComputerControlSession#sendKeyEvent(KeyEvent)}.
 */
public class KeyEvent {
    private final int mKeyCode;
    private final int mAction;
    private final long mEventTimeNanos;

    private KeyEvent(int keyCode, int action, long eventTimeNanos) {
        mKeyCode = keyCode;
        mAction = action;
        mEventTimeNanos = eventTimeNanos;
    }

    /**
     * Returns the key code of the key event.
     *
     * @see KeyEvent.Builder#setKeyCode(int)
     */
    public int getKeyCode() {
        return mKeyCode;
    }

    /**
     * Returns the action of the key event.
     *
     * @see KeyEvent.Builder#setAction(int)
     */
    public int getAction() {
        return mAction;
    }

    /**
     * Returns the event time of the key event.
     *
     * @see KeyEvent.Builder#setEventTimeNanos(long)
     */
    public long getEventTimeNanos() {
        return mEventTimeNanos;
    }

    /**
     * Builder for {@link KeyEvent}.
     */
    public static final class Builder {
        private int mAction = -1;
        private int mKeyCode = -1;
        private long mEventTimeNanos = 0L;

        /**
         * Sets the Android key code of the event.
         *
         * @return this builder, to allow for chaining of calls
         */
        @NonNull
        public Builder setKeyCode(int keyCode) {
            mKeyCode = keyCode;
            return this;
        }

        /**
         * Sets the action of the event.
         *
         * @return this builder, to allow for chaining of calls
         */
        @NonNull
        public Builder setAction(int action) {
            if (action != ACTION_DOWN && action != ACTION_UP) {
                throw new IllegalArgumentException("Unsupported action type");
            }
            mAction = action;
            return this;
        }

        /**
         * Sets the time (in nanoseconds) when this specific event was generated. This may be
         * obtained from {@link SystemClock#uptimeMillis()} (with nanosecond precision instead of
         * millisecond), but can be different depending on the use case.
         * This field is optional and can be omitted.
         * <p>
         * If this field is unset, then the time at which this event is sent to the framework would
         * be considered as the event time (even though
         * {@link KeyEvent#getEventTimeNanos()}) would return {@code 0L}).
         *
         * @return this builder, to allow for chaining of calls
         */
        @NonNull
        public Builder setEventTimeNanos(long eventTimeNanos) {
            if (eventTimeNanos < 0L) {
                throw new IllegalArgumentException("Event time cannot be negative");
            }
            mEventTimeNanos = eventTimeNanos;
            return this;
        }

        /**
         * Creates a {@link KeyEvent} object with the current builder configuration.
         */
        @NonNull
        public KeyEvent build() {
            if (mAction == -1 || mKeyCode == -1) {
                throw new IllegalArgumentException("Cannot build key event with unset fields");
            }
            return new KeyEvent(mKeyCode, mAction, mEventTimeNanos);
        }
    }
}
