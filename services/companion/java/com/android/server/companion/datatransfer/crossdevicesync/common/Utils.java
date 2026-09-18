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
package com.android.server.companion.datatransfer.crossdevicesync.common;

import android.annotation.Nullable;

import com.android.internal.infra.AndroidFuture;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Utils class hosting common static methods. */
public class Utils {

    /** Create a failed AndroidFuture with the given exception. */
    public static <T> AndroidFuture<T> failedAndroidFuture(Throwable t) {
        AndroidFuture<T> future = new AndroidFuture<>();
        future.completeExceptionally(t);
        return future;
    }

    /** Convert a {@link CompletableFuture} to an {@link AndroidFuture} */
    @Nullable
    public static <T> AndroidFuture<T> toAndroidFuture(@Nullable CompletableFuture<T> future) {
        if (future == null) {
            return null;
        }
        if (future instanceof AndroidFuture<T> androidFuture) {
            return androidFuture;
        }
        AndroidFuture<T> res = new AndroidFuture<>();
        var ignored =
                future.whenComplete(
                        (val, t) -> {
                            if (t != null) {
                                res.completeExceptionally(unwrapException(t));
                            } else {
                                res.complete(val);
                            }
                        });
        return res;
    }

    /** If the supplied {@code future} fails, handle it via the async function {@code function}. */
    @Nullable
    public static <T> AndroidFuture<T> handleFailureAsync(
            @Nullable AndroidFuture<T> future, Function<Throwable, AndroidFuture<T>> function) {
        return handleAsync(future, (val, t) -> t == null ? future : function.apply(t));
    }

    /**
     * Regardless of if the {@code future} fails, always execute the async function {@code function}
     * and transform the result.
     */
    public static <T> AndroidFuture<T> handleAsync(
            @Nullable AndroidFuture<T> future,
            BiFunction<T, Throwable, AndroidFuture<T>> function) {
        if (future == null) {
            return null;
        }
        return toAndroidFuture(future.handle(function).thenCompose(f -> f));
    }

    /** Convert an integer collection to an integer array. */
    public static int[] toIntArray(Collection<Integer> collection) {
        int[] res = new int[collection.size()];
        Iterator<Integer> iterator = collection.iterator();
        for (int i = 0; i < res.length; i++) {
            res[i] = iterator.next();
        }
        return res;
    }

    /** Unwrap nested CompletionException and ExecutionException. */
    public static Throwable unwrapException(Throwable t) {
        Throwable cause = t;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    /** Get exception from a future if it failed. */
    @Nullable
    public static Throwable getException(Future<?> future) {
        try {
            future.get();
            return null;
        } catch (InterruptedException e) {
            return null;
        } catch (ExecutionException | CompletionException e) {
            return unwrapException(e);
        }
    }

    /** Check if a future has succeeded. */
    public static boolean isFutureSucceeded(Future<?> future) {
        if (!future.isDone()) {
            return false;
        }
        return getException(future) == null;
    }

    /** Check if a future has failed. */
    public static boolean isFutureFailed(Future<?> future) {
        if (!future.isDone()) {
            return false;
        }
        return getException(future) != null;
    }
}
