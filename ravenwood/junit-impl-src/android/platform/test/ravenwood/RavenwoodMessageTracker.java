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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Message;

import com.android.internal.annotations.GuardedBy;

import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Keeps track of stacktraces where a message is posted.
 */
public final class RavenwoodMessageTracker {
    public static final RavenwoodMessageTracker sInstance = new RavenwoodMessageTracker();

    public static RavenwoodMessageTracker getInstance() {
        return sInstance;
    }

    private RavenwoodMessageTracker() {
    }

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final WeakHashMap<Message, MessageWasPostedHereStackTrace>
            mMessagePosters = new WeakHashMap<>();

    /** Stop tracking {@code msg} */
    public void untrackMessage(@NonNull Message msg) {
        synchronized (mLock) {
            mMessagePosters.remove(Objects.requireNonNull(msg));
        }
    }

    /** Start tracking {@code msg} and remember the current stacktrace. */
    public void trackMessagePoster(@NonNull Message msg) {
        var here = new MessageWasPostedHereStackTrace();
        synchronized (mLock) {
            mMessagePosters.put(Objects.requireNonNull(msg), here);
        }
    }

    /** @return the remembered stacktrace for a tracked message. */
    @Nullable
    public MessageWasPostedHereStackTrace getPoster(@NonNull Message msg) {
        synchronized (mLock) {
            return mMessagePosters.get(Objects.requireNonNull(msg));
        }
    }

    /** if {@code msg} is traced, set the remembered stacktrace as a cause to {@code th}.*/
    public void injectPosterAsCause(@NonNull Throwable th, @NonNull Message msg) {
        Objects.requireNonNull(th);
        var poster = getPoster(msg);
        if (poster != null) {
            poster.injectAsCause(th);
        }
    }
}
