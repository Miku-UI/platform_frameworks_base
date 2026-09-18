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
package com.android.ravenwood.common;

import android.annotation.NonNull;
import android.annotation.Nullable;


import com.android.internal.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.function.Predicate;

/**
 * Exception class specifically used for print stacktrace.
 *
 * Optionally, support cleaning up the stacktrace.
 */
public class StackTrace extends Exception {
    private final StackTraceElement[] mOriginalStackTrace;
    private StackTraceElement[] mFilteredStackTrace;

    /** Constructor */
    public StackTrace() {
        this(null, null);
    }

    /** Constructor */
    public StackTrace(@Nullable String message) {
        this(message, null);
    }

    /** Constructor with an inner exception. */
    public StackTrace(@Nullable String message, @Nullable Throwable inner) {
        super(message == null ? "[Stack trace]" : message, inner);
        mOriginalStackTrace = getStackTrace();
        mFilteredStackTrace = mOriginalStackTrace;
    }

    /** Interface for cleaning up stack trace. */
    public interface StackFrameFilter {
        StackTraceElement[] cleanUp(StackTraceElement[] original);
    }

    /** Reset the stack trace to the original one. */
    public void resetStackTrace() {
        mFilteredStackTrace = mOriginalStackTrace;
        setStackTrace(mFilteredStackTrace);
    }

    /** Filter the stack trace. This method is always additive. */
    public void filterStackTrace(@Nullable StackFrameFilter filter) {
        mFilteredStackTrace = applyStackTraceFilter(filter, mFilteredStackTrace);
        setStackTrace(mFilteredStackTrace);
    }

    @VisibleForTesting
    public static StackTraceElement[] applyStackTraceFilter(
            @Nullable StackFrameFilter filter,
            @NonNull StackTraceElement[] original) {
        if (filter != null) {
            var filtered = filter.cleanUp(original);

            // If there's no frame left, the filter is probably wrong, so let's just keep the
            // original one.
            if (filtered.length > 0) {
                return filtered;
            }
        }
        // If filter is null, or the filitered result is empty, just return the original.
        return original;
    }

    /** Remove the stack trace up to a frame that matches a predicate. */
    public void removeStackTraceUntil(
            @NonNull Predicate<StackTraceElement> untilPred,
            boolean removeMatchingFrameToo) {
        filterStackTrace(buildStackFrameFilter(untilPred, removeMatchingFrameToo));
    }

    public static Predicate<StackTraceElement> classPredicate(Class<?> clazz) {
        return (element) -> element.getClassName().equals(clazz.getName());
    }

    @VisibleForTesting
    public static StackFrameFilter buildStackFrameFilter(
            @NonNull Predicate<StackTraceElement> untilPred,
            boolean removeMatchingFrameToo) {
        if (!removeMatchingFrameToo) {
            // This is an exclusive filter, which is easier.
            return new StackFrameFilter() {
                @Override
                public StackTraceElement[] cleanUp(StackTraceElement[] original) {
                    // Find the first matching element.
                    int found = -1;
                    for (int i = 0; i < original.length; i++) {
                        if (untilPred.test(original[i])) {
                            found = i;
                            break;
                        }
                    }
                    if (found < 0) {
                        // Not found, just return the original one.
                        return original;
                    }
                    return Arrays.copyOfRange(original, found, original.length);
                };
            };
        } else {
            // This is an inclusive filter, which is a bit harder.
            return new StackFrameFilter() {
                @Override
                public StackTraceElement[] cleanUp(StackTraceElement[] original) {
                    // Find the first matching element.
                    int found = -1;
                    for (int i = 0; i < original.length; i++) {
                        if (untilPred.test(original[i])) {
                            found = i;
                            break;
                        }
                    }
                    if (found < 0) {
                        // Not found, just return the original one.
                        return original;
                    }
                    // Now, look ahead and see if there are any more matching frame
                    // We want to remove all of them.
                    for (int i = found + 1; i < original.length; i++) {
                        if (untilPred.test(original[i])) {
                            // As long as we have a matching frame, we skip it.
                            found = i;
                            continue;
                        } else {
                            break;
                        }
                    }
                    var start = found + 1;
                    if (start >= original.length) {
                        // We reached the end. We don't want to return an empty array,
                        // so just return the original one.
                        return original;
                    }
                    return Arrays.copyOfRange(original, start, original.length);
                };
            };
        }
    }
}
