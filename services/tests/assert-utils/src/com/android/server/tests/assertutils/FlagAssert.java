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

package com.android.server.tests.assertutils;

import static com.google.common.truth.Truth.assertWithMessage;

import android.util.Log;

import com.google.common.truth.Truth;

/**
 * A light and fluent helper class for making assertions on flags when described using integer
 * bit-fields. Example usage:
 * <ul>
 *     <li>
 *         Test that caller has read and execute but not write permissions on a file
 *         <pre>
 *             FlagAssert.assertThat(getFilePermissions())
 *                  .hasSet(FLAG_READ)
 *                  .hasSet(FLAG_EXECUTE)
 *                  .hasNotSet(FLAG_WRITE);
 *         </pre>
 *     </li>
 *     <li>
 *         Test that caller has no permissions on a file
 *         <pre>FlagAssert.assertThat(getFilePermissions()).isEmpty();</pre>
 *     </li>
 * </ul>
 *
 */
public class FlagAssert {
    private static final String TAG = FlagAssert.class.getSimpleName();

    private final int mFlags;

    private FlagAssert(int flags) {
        mFlags = flags;
    }

    /**
     * Returns an object that can be used for chaining assertions on
     * @param flags the bit field describing the flags that subsequent assertions will be made on.
     */
    public static FlagAssert assertThat(int flags) {
        return new FlagAssert(flags);
    }

    /** Asserts that all the passed flags are set in the flags passed to {@link #assertThat}. */
    public FlagAssert hasSet(int flags) {
        if (flags == 0) {
            Log.w(TAG, "Empty flags passed to hasSet(). Did you mean to use isEmpty()?");
            return this;
        }
        assertWithMessage("Expected but missing flags 0x" + Integer.toHexString(~mFlags & flags))
                .that(mFlags & flags).isEqualTo(flags);
        return this;
    }

    /** Asserts that none of the passed flags are set in the flags passed to {@link #assertThat}. */
    public FlagAssert hasNotSet(int flags) {
        if (flags == 0) {
            Log.w(TAG, "Empty flags passed to hasNotSet(). Did you mean to use hasAnySet()?");
            return this;
        }
        assertWithMessage("Found unexpected flags set: 0x" + Integer.toHexString(mFlags & flags))
                .that(mFlags & flags).isEqualTo(0);
        return this;
    }

    /** Asserts that at least one flag is set in the flags passed to {@link #assertThat}. */
    public FlagAssert hasAnySet() {
        Truth.assertThat(mFlags).isNotEqualTo(0);
        return this;
    }

    /**
     * Asserts that all the flags passed to {@link #assertThat} are exactly the same as
     * {@code flags}
     */
    public FlagAssert isEqualTo(int flags) {
        Truth.assertThat(flags).isEqualTo(mFlags);
        return this;
    }

    /** Asserts that no flags are set in the flags passed to {@link #assertThat}. */
    public FlagAssert isEmpty() {
        return isEqualTo(0);
    }
}
