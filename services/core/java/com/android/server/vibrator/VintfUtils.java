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

package com.android.server.vibrator;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Helpers for interacting with VINTF objects. */
class VintfUtils {

    /** {@link Supplier} that takes a VINTF object and throw {@link RemoteException} */
    @FunctionalInterface
    interface VintfGetter<I, R> {
        @Nullable
        R get(I hal) throws RemoteException;
    }

    /** {@link Runnable} that takes a VINTF object and throw {@link RemoteException} */
    @FunctionalInterface
    interface VintfRunnable<I> {
        void run(I hal) throws RemoteException;
    }

    /** Cached {@link Supplier} for remote VINTF objects that resets on dead object. */
    abstract static class VintfSupplier<I> implements Supplier<I>, IBinder.DeathRecipient {
        private static final String TAG = "VintfSupplier";

        @GuardedBy("this")
        private I mInstance = null;

        @Nullable
        abstract IBinder connectToService();

        @NonNull
        abstract I castService(@NonNull IBinder binder);

        @Nullable
        @Override
        public synchronized I get() {
            if (mInstance == null) {
                IBinder binder = connectToService();
                if (binder != null) {
                    mInstance = castService(binder);
                    try {
                        binder.linkToDeath(this, 0);
                    } catch (RemoteException e) {
                        Slog.e(TAG, "Unable to register DeathRecipient for " + mInstance);
                    }
                }
            }
            return mInstance;
        }

        @Override
        public synchronized void binderDied() {
            clear();
        }

        public synchronized void clear() {
            mInstance = null;
        }
    }

    /**
     * Runs getter on VINTF object provided by supplier, if any.
     *
     * <p>This automatically clears the cached object in given {@code supplier} if a
     * {@link DeadObjectException} is thrown by the remote method call, so future interactions can
     * load a new instance.
     *
     * @throws RuntimeException if supplier returns null or there is a {@link RemoteException} or
     * {@link RuntimeException} from the remote method call.
     */
    @Nullable
    static <I, T> T get(VintfSupplier<I> supplier, VintfGetter<I, T> getter) {
        I hal = supplier.get();
        if (hal == null) {
            throw new RuntimeException("Missing HAL service");
        }
        try {
            return getter.get(hal);
        } catch (RemoteException e) {
            if (e instanceof DeadObjectException) {
                supplier.clear();
            }
            throw e.rethrowAsRuntimeException();
        }
    }

    /**
     * Same as {@link #get(VintfSupplier, VintfGetter)}, but throws no exception and
     * returns default on error.
     */
    @Nullable
    static <I, T> T getOrDefault(VintfSupplier<I> supplier, VintfGetter<I, T> getter,
            @Nullable T defaultValue, Consumer<Throwable> errorHandler) {
        try {
            return get(supplier, getter);
        } catch (RuntimeException e) {
            errorHandler.accept(e);
        }
        return defaultValue;
    }

    /** Same as {@link #get(VintfSupplier, VintfGetter)}, but throws no exception. */
    static <I, T> void getNoThrow(VintfSupplier<I> supplier, VintfGetter<I, T> getter,
            Consumer<T> resultHandler, Consumer<Throwable> errorHandler) {
        try {
            resultHandler.accept(get(supplier, getter));
        } catch (RuntimeException e) {
            errorHandler.accept(e);
        }
    }

    /**
     * Runs runnable on VINTF object provided by supplier, if any.
     *
     * <p>This automatically clears the cached object in given {@code supplier} if a
     * {@link DeadObjectException} is thrown by the remote method call, so future interactions can
     * load a new instance.
     *
     * @throws RuntimeException if supplier returns null or there is a {@link RemoteException} or
     * {@link RuntimeException} from the remote method call.
     */
    static <I> void run(VintfSupplier<I> supplier, VintfRunnable<I> runnable) {
        I hal = supplier.get();
        if (hal == null) {
            throw new RuntimeException("Missing HAL service");
        }
        try {
            runnable.run(hal);
        } catch (RemoteException e) {
            if (e instanceof DeadObjectException) {
                supplier.clear();
            }
            throw e.rethrowAsRuntimeException();
        }
    }

    /** Same as {@link #run(VintfSupplier, VintfRunnable)}, but throws no exception. */
    static <I> boolean runNoThrow(VintfSupplier<I> supplier, VintfRunnable<I> runnable,
            Consumer<Throwable> errorHandler) {
        try {
            run(supplier, runnable);
            return true;
        } catch (RuntimeException e) {
            errorHandler.accept(e);
        }
        return false;
    }

    // Non-instantiable helper class.
    private VintfUtils() {
    }
}
