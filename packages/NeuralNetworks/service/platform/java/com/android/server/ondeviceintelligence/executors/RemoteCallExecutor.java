/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.ondeviceintelligence.executors;

import android.os.IInterface;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.infra.AndroidFuture;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.Executors;

/**
 * Base class for executing remote calls with failure handling.
 *
 * @param <S> The type of the service interface.
 */
public abstract class RemoteCallExecutor<S extends IInterface> {
    /** Enum for failure types. */
    public enum FailureType {
        SERVICE_UNAVAILABLE,
        REMOTE_FAILURE,
        TIMEOUT
    }

    protected final FailureConsumer mFailureConsumer;
    private final Executor mRemoteCallExecutor;

    protected RemoteCallExecutor(Builder<S, ?> builder) {
        this.mFailureConsumer = builder.mFailureConsumer;
        this.mRemoteCallExecutor = Executors.newSingleThreadExecutor(
                builder.getThreadFactory("odi-remote-call-executor"));
    }

    /** Executes the remote call. */
    public abstract AndroidFuture<?> execute(RemoteCallRunner<S> remoteCall);

    /** Executes a runnable on the remote call executor. */
    protected void executeOnRemoteExecutor(Runnable runnable) {
        mRemoteCallExecutor.execute(runnable);
    }

    /** Base builder for {@link RemoteCallExecutor}. */
    public abstract static class Builder<S extends IInterface, T extends Builder<S, T>> {
        private static final java.util.concurrent.atomic.AtomicInteger threadCount =
                new java.util.concurrent.atomic.AtomicInteger(0);
        private FailureConsumer mFailureConsumer = (type) -> {
        };

        @SuppressWarnings("unchecked")
        public T onFailure(FailureConsumer consumer) {
            this.mFailureConsumer = consumer;
            return (T) this;
        }

        public abstract RemoteCallExecutor<S> build();

        /**
         * Returns a {@link ThreadFactory} that creates threads with a given prefix.
         * The threads created by this factory will have an {@link Thread.UncaughtExceptionHandler}
         * that logs and swallows uncaught exceptions to prevent the system server from crashing.
         * @param threadNamePrefix The prefix for the thread names.
         */
        protected static ThreadFactory getThreadFactory(String threadNamePrefix) {
            return r -> {
                Thread thread = new Thread(r,
                        threadNamePrefix + threadCount.getAndIncrement());
                thread.setUncaughtExceptionHandler(
                        (t, e) -> {
                            Log.e(threadNamePrefix,
                                    "Uncaught exception in thread: " + t.getName(), e);
                            // The exception is logged and swallowed here to
                            // avoid the system server crashing.
                        });
                return thread;
            };
        }
    }

    @FunctionalInterface
    public interface RemoteCallRunner<S extends IInterface> {
        AndroidFuture<?> run(S service) throws RemoteException;
    }

    @FunctionalInterface
    public interface FailureConsumer {
        void accept(FailureType type) throws RemoteException;
    }
}
