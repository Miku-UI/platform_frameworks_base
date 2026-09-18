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

package android.platform.test.ravenwood;

import android.os.SystemProperties;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.annotations.VisibleForTesting.Visibility;

import libcore.util.NativeAllocationRegistry;

import java.lang.reflect.Modifier;
import java.util.function.Function;

/** Performs various integrity checks */
public class RavenwoodIntegrityChecker {
    private static final String TAG = RavenwoodDriver.TAG;

    private RavenwoodIntegrityChecker() {
    }

    /** Thrown when Ravenwood detects an integrity issue. */
    public static final class RavenwoodIntegrityException extends RuntimeException {
        /** {@inheritDoc} */
        public RavenwoodIntegrityException(String message) {
            super(message);
        }

        /** {@inheritDoc} */
        public RavenwoodIntegrityException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Do an integrity check after the framework native code is enabled. */
    static void onFrameworkNativeInitialized() {
        onFrameworkNativeInitializedInner(SystemProperties::get);
    }

    @VisibleForTesting(visibility = Visibility.PRIVATE)
    public static void onFrameworkNativeInitializedInner(Function<String, String> sysPropFetcher) {
        // Ensure system properties can be read.
        var prop = sysPropFetcher.apply("ro.is_on_ravenwood");
        if (prop == null) {
            // This can happen if, for example, a test accidentally included `libandroid_runtime`.
            throw new RavenwoodIntegrityException(
                    "SystemProperties is not initialized correctly (Reach out to g/ravenwood)");
        }
    }

    /**
     * Same as {@link #checkForNativeAllocationRegistry} but works on multiple classes.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public static void checkForNativeAllocationRegistry(Class<?>[] classes) {
        for (Class<?> clazz : classes) {
            checkForNativeAllocationRegistry(clazz);
        }
    }

    /**
     * Verify that if the target class has any native methods, it doesn't have
     * a static NativeAllocationRegistry field.
     */
    private static void checkForNativeAllocationRegistry(Class<?> clazz) {
        if (android.graphics.Bitmap.class == clazz) {
            // False-positive. Bitmap's use of NAR is okay.
            return;
        }
        // See if the class has any native methods
        var hasNative = false;
        for (var method : clazz.getDeclaredMethods()) {
            if (Modifier.isNative(method.getModifiers())) {
                hasNative = true;
                break;
            }
        }
        if (!hasNative) {
            return;
        }
        for (var field : clazz.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (field.getType() == NativeAllocationRegistry.class) {
                throw new RavenwoodIntegrityException(
                        "NativeAllocationRegistry in class " + clazz + " needs be moved to an "
                                + "inner class (See b/446716410)");
            }
        }
    }
}
