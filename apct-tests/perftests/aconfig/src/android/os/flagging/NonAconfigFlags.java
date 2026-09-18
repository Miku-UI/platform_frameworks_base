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

package android.os.flagging;

/** Simple Flags-like class to provide a perf baseline relative to aconfig flags codegen. */
public final class NonAconfigFlags {
    /** Mimic a minimal cost read-only flag query. */
    public static boolean readOnlyFlagForTest() {
        return false;
    }

    /** Mimic a minimal cost read-write flag query. */
    public static boolean readWriteFlagForTest() {
        return NoPreloadHolder.sReadWriteFlagForTest;
    }

    private static final class NoPreloadHolder {
        // Non-final to preserve mutability, as afforded by aconfig read-write flags.
        static boolean sReadWriteFlagForTest;

        static {
            sReadWriteFlagForTest = com.android.aconfig_new_storage.Flags.readWriteFlagForTest();
        }
    }
}
