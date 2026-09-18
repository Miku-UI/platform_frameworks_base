/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui;

import static android.os.IBinder.FLAG_ONEWAY;

import static com.android.settingslib.utils.ThreadUtils.isMainThread;

import android.annotation.MainThread;
import android.annotation.Nullable;
import android.os.Binder;
import android.os.Binder.ProxyTransactListener;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.StrictMode;
import android.os.SystemProperties;
import android.os.Trace;
import android.util.Log;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewRootImpl;

import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.util.Assert;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
import java.util.Stack;
import java.util.function.Supplier;

/**
 * Utility class for methods used to dejank the UI.
 */
public final class DejankUtils {
    private static final String TRACK_NAME = "DejankUtils";

    public static final boolean STRICT_MODE_ENABLED = Build.IS_ENG
            || SystemProperties.getBoolean("persist.sysui.strictmode", false);
    // initially null to avoid crashing if this class is loaded in an invalid thread
    @Nullable
    private static Choreographer sChoreographer = null;

    // initialized together with sChoreographer (before use) so the main thread is up.
    @Nullable
    private static Handler sHandler = null;

    private static final ArrayList<Runnable> sPendingRunnables = new ArrayList<>();
    private static final Random sRandom = new Random();
    private static final Stack<String> sBlockingIpcs = new Stack<>();
    private static boolean sTemporarilyIgnoreStrictMode = false;
    private static final HashSet<String> sWhitelistedFrameworkClasses = new HashSet<>();
    private static final Object sLock = new Object();
    private static final ProxyTransactListener sProxy = new ProxyTransactListener() {
        @Override
        public Object onTransactStarted(IBinder binder, int transactionCode, int flags) {
            synchronized (sLock) {
                if ((flags & FLAG_ONEWAY) == FLAG_ONEWAY || sBlockingIpcs.empty()
                        || !isMainThread() || sTemporarilyIgnoreStrictMode) {
                    return null;
                }
            }

            try {
                String description = binder.getInterfaceDescriptor();
                synchronized (sLock) {
                    if (sWhitelistedFrameworkClasses.contains(description)) {
                        return null;
                    }
                }
            } catch (RemoteException e) {
                Log.e(TRACK_NAME, "Failed to retrieve interface descriptor.", e);
            }

            StrictMode.noteSlowCall("IPC detected on critical path: " + sBlockingIpcs.peek());
            return null;
        }

        @Override
        public Object onTransactStarted(IBinder binder, int transactionCode) {
            return null;
        }

        @Override
        public void onTransactEnded(Object o) {

        }
    };

    /**
     * Only for testing.
     */
    private static boolean sImmediate;

    static {
        if (STRICT_MODE_ENABLED) {
            // Common IPCs that are ok to block the main thread.
            sWhitelistedFrameworkClasses.add("android.view.IWindowSession");
            sWhitelistedFrameworkClasses.add("com.android.internal.policy.IKeyguardStateCallback");
            sWhitelistedFrameworkClasses.add("android.os.IPowerManager");
            sWhitelistedFrameworkClasses.add("com.android.internal.statusbar.IStatusBarService");

            Binder.setProxyTransactListener(sProxy);
            StrictMode.ThreadPolicy.Builder builder = new StrictMode.ThreadPolicy.Builder()
                    .detectCustomSlowCalls()
                    .penaltyFlashScreen()
                    .penaltyLog();
            StrictMode.setThreadPolicy(builder.build());
        }
    }

    private static final Runnable sAnimationCallbackRunnable = () -> {
        Objects.requireNonNull(sHandler);
        for (int i = 0; i < sPendingRunnables.size(); i++) {
            sHandler.post(sPendingRunnables.get(i));
        }
        sPendingRunnables.clear();
    };

    /**
     * Enable blocking-binder-call {@link StrictMode}.
     *
     * @param tag A key.
     */
    @MainThread
    public static void startDetectingBlockingIpcs(String tag) {
        if (STRICT_MODE_ENABLED) {
            synchronized (sLock) {
                sBlockingIpcs.push(tag);
            }
        }
    }

    /**
     * Stop IPC detection for a specific tag.
     *
     * @param tag The key.
     * @see #startDetectingBlockingIpcs(String)
     */
    @MainThread
    public static void stopDetectingBlockingIpcs(String tag) {
        if (STRICT_MODE_ENABLED) {
            synchronized (sLock) {
                sBlockingIpcs.remove(tag);
            }
        }
    }

    /**
     * Temporarily ignore blocking binder calls for contents of this {@link Runnable}.
     *
     * @param runnable Target.
     */
    @MainThread
    public static void whitelistIpcs(Runnable runnable) {
        if (STRICT_MODE_ENABLED && !sTemporarilyIgnoreStrictMode) {
            synchronized (sLock) {
                sTemporarilyIgnoreStrictMode = true;
            }
            try {
                runnable.run();
            } finally {
                synchronized (sLock) {
                    sTemporarilyIgnoreStrictMode = false;
                }
            }
        } else {
            runnable.run();
        }
    }

    /**
     * @see #whitelistIpcs(Runnable)
     */
    @MainThread
    public static <T> T whitelistIpcs(Supplier<T> supplier) {
        if (STRICT_MODE_ENABLED && !sTemporarilyIgnoreStrictMode) {
            synchronized (sLock) {
                sTemporarilyIgnoreStrictMode = true;
            }
            final T val;
            try {
                val = supplier.get();
            } finally {
                synchronized (sLock) {
                    sTemporarilyIgnoreStrictMode = false;
                }
            }
            return val;
        } else {
            return supplier.get();
        }
    }

    /**
     * Executes {@code r} after performTraversals. Use this do to CPU heavy work for which the
     * timing is not critical for animation. The work is then scheduled at the same time
     * RenderThread is doing its thing, leading to better parallelization.
     *
     * <p>Needs to be called from the main thread.
     */
    @MainThread
    public static void postAfterTraversal(Runnable r) {
        if (sImmediate) {
            r.run();
            return;
        }
        Assert.isMainThread();
        sPendingRunnables.add(r);
        postAnimationCallback();
    }

    /**
     * Removes a previously scheduled runnable.
     *
     * <p>Needs to be called from the main thread.
     */
    @MainThread
    public static void removeCallbacks(Runnable r) {
        Assert.isMainThread();
        sPendingRunnables.remove(r);
        if (sHandler != null) {
            sHandler.removeCallbacks(r);
        }
    }

    @MainThread
    private static void postAnimationCallback() {
        Assert.isMainThread();
        if (sChoreographer == null) {
            sChoreographer = Choreographer.getInstance();
        }
        if (sHandler == null) {
            sHandler = new Handler(Looper.getMainLooper());
        }
        sChoreographer.postCallback(Choreographer.CALLBACK_ANIMATION, sAnimationCallbackRunnable,
                null);
    }

    @VisibleForTesting
    public static void setImmediate(boolean immediate) {
        sImmediate = immediate;
    }

    /**
     * Calls notifyRendererOfExpensiveFrame on the ViewRootImpl after performing null checks.
     */
    public static void notifyRendererOfExpensiveFrame(View view, String reason) {
        if (view == null) return;
        notifyRendererOfExpensiveFrame(view.getViewRootImpl(), reason);
    }

    /**
     * Calls notifyRendererOfExpensiveFrame on the ViewRootImpl after performing null checks.
     */
    public static void notifyRendererOfExpensiveFrame(ViewRootImpl viewRoot, String reason) {
        if (viewRoot == null) return;
        if (Trace.isTagEnabled(Trace.TRACE_TAG_APP)) {
            int cookie = sRandom.nextInt();
            Trace.asyncTraceForTrackBegin(
                    Trace.TRACE_TAG_APP,
                    TRACK_NAME,
                    "notifyRendererOfExpensiveFrame (" + reason + ")",
                    cookie);
            DejankUtils.postAfterTraversal(
                    () -> Trace.asyncTraceForTrackEnd(Trace.TRACE_TAG_APP, TRACK_NAME, cookie));
        }
        viewRoot.notifyRendererOfExpensiveFrame();
    }
}
