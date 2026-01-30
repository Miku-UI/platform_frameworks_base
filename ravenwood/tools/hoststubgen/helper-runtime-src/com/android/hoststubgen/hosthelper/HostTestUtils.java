/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.hoststubgen.hosthelper;

import java.lang.reflect.AnnotatedElement;

/**
 * Utilities used in the host side test environment.
 */
public class HostTestUtils {
    public static final String CLASS_INTERNAL_NAME = getInternalName(HostTestUtils.class);
    public static final  String CLASS_DESCRIPTOR = "L" + CLASS_INTERNAL_NAME + ";";

    private HostTestUtils() {
    }

    /**
     * Same as ASM's Type.getInternalName(). Copied here, to avoid having a reference to ASM
     * in this JAR.
     */
    public static String getInternalName(final Class<?> clazz) {
        return clazz.getName().replace('.', '/');
    }

    /**
     * Find any of the HostStubGenProcessedAsXxx annotations from a given element and
     * return its "reason".
     *
     * Returns null if none found or if the only reason found is "".
     */
    // Nullable
    public static String getHostStubGenAnnotationReason(/* nullable */ AnnotatedElement element) {
        if (element == null) {
            return null;
        }
        for (var annot : element.getAnnotations()) {
            String reason = switch (annot) {
                case HostStubGenProcessedAsKeep a -> a.reason();
                case HostStubGenProcessedAsIgnore a -> a.reason();
                case HostStubGenProcessedAsThrow a -> a.reason();
                case HostStubGenProcessedAsThrowButSupported a -> a.reason();
                case HostStubGenProcessedAsExperimental a -> a.reason();
                case HostStubGenProcessedAsSubstitute a -> a.reason();
                default -> null;
            };
            if (reason != null && !reason.isEmpty()) {
                return reason;
            }
        }
        return null;
    }

    public static final String ASSERT_THAT_HOOK_RETURNED_TRUE = "assertThatHookReturnedTrue";
    public static final String ASSERT_THAT_HOOK_RETURNED_FALSE = "assertThatHookReturnedFalse";

    /**
     * Used to assert a boolean method returned true.
     */
    public static void assertThatHookReturnedTrue(boolean b) {
        if (!b) {
            throw new IllegalStateException("The hook must return true for this method");
        }
    }

    /**
     * Used to assert a boolean method returned false.
     */
    public static void assertThatHookReturnedFalse(boolean b) {
        if (b) {
            throw new IllegalStateException("The hook must return false for this method");
        }
    }
}
