/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.dreams;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.os.Handler;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.service.dreams.DreamPlaylist;
import android.service.dreams.IDreamManagerListener;
import android.util.IndentingPrintWriter;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * Manages the dream playlist state notifications. Handles debouncing of updates to prevent listener
 * churn. Defer data caching and resolution to {@link DreamComponentsResolver}.
 */
class DreamPlaylistUpdater {
    private static final int DEFAULT_UPDATE_DEBOUNCE_DELAY_MS = 100;
    private static final String TAG = "DreamPlaylistUpdater";

    private final ResolverProvider mResolverProvider;
    private final Handler mHandler;
    private final int mDebounceDelayMs;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final SparseArray<RemoteCallbackList<IDreamManagerListener>> mListeners =
            new SparseArray<>();

    @GuardedBy("mLock")
    private final SparseArray<DreamPlaylist> mLastNotifiedPlaylist = new SparseArray<>();

    @GuardedBy("mLock")
    private final SparseArray<Runnable> mPendingUpdateTasks = new SparseArray<>();

    interface Callback {
        void onPlaylistChanged(int userId, DreamPlaylist playlist);
    }

    interface ResolverProvider {
        @Nullable
        DreamComponentsResolver getResolver(int userId);
    }

    private final Callback mCallback;

    DreamPlaylistUpdater(
            @NonNull ResolverProvider resolverProvider,
            @NonNull Handler handler,
            @Nullable Callback callback) {
        this(resolverProvider, handler, callback, DEFAULT_UPDATE_DEBOUNCE_DELAY_MS);
    }

    DreamPlaylistUpdater(
            @NonNull ResolverProvider resolverProvider,
            @NonNull Handler handler,
            @Nullable Callback callback,
            int debounceDelayMs) {
        mResolverProvider = resolverProvider;
        mHandler = handler;
        mCallback = callback;
        mDebounceDelayMs = debounceDelayMs;
    }

    void registerListener(@NonNull IDreamManagerListener listener, int userId) {
        synchronized (mLock) {
            RemoteCallbackList<IDreamManagerListener> listeners = mListeners.get(userId);
            if (listeners == null) {
                listeners = new RemoteCallbackList<>();
                mListeners.put(userId, listeners);
            }
            listeners.register(listener);
        }
    }

    void unregisterListener(@NonNull IDreamManagerListener listener, int userId) {
        synchronized (mLock) {
            final RemoteCallbackList<IDreamManagerListener> listeners = mListeners.get(userId);
            if (listeners != null) {
                listeners.unregister(listener);
            }
        }
    }

    void refresh(int userId, @Nullable ComponentName systemDreamComponent) {
        synchronized (mLock) {
            Runnable existingTask = mPendingUpdateTasks.get(userId);
            if (existingTask != null) {
                mHandler.removeCallbacks(existingTask);
            }

            Runnable newTask =
                    new Runnable() {
                        @Override
                        public void run() {
                            boolean shouldRun = false;
                            synchronized (mLock) {
                                if (mPendingUpdateTasks.get(userId) == this) {
                                    mPendingUpdateTasks.remove(userId);
                                    shouldRun = true;
                                }
                            }
                            if (shouldRun) {
                                refreshImmediately(userId, systemDreamComponent);
                            }
                        }
                    };
            mPendingUpdateTasks.put(userId, newTask);
            mHandler.postDelayed(newTask, mDebounceDelayMs);
        }
    }

    void refreshImmediately(int userId, @Nullable ComponentName systemDreamComponent) {
        DreamComponentsResolver resolver = mResolverProvider.getResolver(userId);
        if (resolver == null) {
            return;
        }
        final DreamPlaylist playlist = resolver.getDreamPlaylist(systemDreamComponent);
        final RemoteCallbackList<IDreamManagerListener> listenersForUser;

        synchronized (mLock) {
            final DreamPlaylist lastNotified = mLastNotifiedPlaylist.get(userId);
            if (Objects.equals(lastNotified, playlist)) {
                return;
            }
            mLastNotifiedPlaylist.put(userId, playlist);
            listenersForUser = mListeners.get(userId);
        }

        if (mCallback != null) {
            mCallback.onPlaylistChanged(userId, playlist);
        }

        if (listenersForUser != null) {
            notifyListeners(listenersForUser, playlist);
        }
    }

    @Nullable
    DreamPlaylist getDreamPlaylist(int userId, @Nullable ComponentName systemDreamComponent) {
        DreamComponentsResolver resolver = mResolverProvider.getResolver(userId);
        return resolver != null ? resolver.getDreamPlaylist(systemDreamComponent) : null;
    }

    void clearCache(int userId) {
        synchronized (mLock) {
            mLastNotifiedPlaylist.remove(userId);
            final RemoteCallbackList<IDreamManagerListener> listeners = mListeners.get(userId);
            if (listeners != null) {
                listeners.kill();
                mListeners.remove(userId);
            }
            Runnable pendingTask = mPendingUpdateTasks.get(userId);
            if (pendingTask != null) {
                mHandler.removeCallbacks(pendingTask);
                mPendingUpdateTasks.remove(userId);
            }
        }
    }

    private void notifyListeners(
            RemoteCallbackList<IDreamManagerListener> listeners, DreamPlaylist playlist) {
        if (listeners == null) {
            return;
        }
        Slog.i(TAG, "Notifying listeners for with playlist " + playlist);
        int n = listeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            notifyListener(listeners.getBroadcastItem(i), playlist);
        }
        listeners.finishBroadcast();
    }

    private void notifyListener(IDreamManagerListener listener, DreamPlaylist playlist) {
        try {
            listener.onPlaylistChanged(playlist);
        } catch (RemoteException e) {
            // ignore
        }
    }

    void dump(PrintWriter pw) {
        IndentingPrintWriter ipw = new IndentingPrintWriter(pw, "  ");
        synchronized (mLock) {
            ipw.println("DreamPlaylistUpdater:");
            ipw.increaseIndent();
            for (int i = 0; i < mLastNotifiedPlaylist.size(); i++) {
                int userId = mLastNotifiedPlaylist.keyAt(i);
                DreamPlaylist playlist = mLastNotifiedPlaylist.valueAt(i);
                ipw.println("User " + userId + ":");
                ipw.increaseIndent();
                ipw.println("Last Notified Playlist: " + playlist);
                RemoteCallbackList<IDreamManagerListener> listeners = mListeners.get(userId);
                ipw.println(
                        "Listeners: "
                                + (listeners != null ? listeners.getRegisteredCallbackCount() : 0));
                ipw.println("Pending Update: " + (mPendingUpdateTasks.get(userId) != null));
                ipw.decreaseIndent();
            }
            ipw.decreaseIndent();
        }
    }
}
