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

package com.android.server.wm;

import static android.Manifest.permission.REQUEST_SYSTEM_MULTITASKING_CONTROLS;

import static com.android.server.wm.ActivityTaskManagerService.enforceTaskPermission;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Slog;
import android.util.SparseArray;
import android.window.IMultitaskingController;
import android.window.IMultitaskingControllerCallback;
import android.window.IMultitaskingDelegate;

import androidx.annotation.RequiresPermission;

import com.android.server.am.ActivityManagerService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Stores a reference to the multitasking functions delegate in WM Shell and serves as a proxy
 * that applies the policy restrictions and passes the calls from the clients.
 */
class MultitaskingController extends IMultitaskingController.Stub {

    private static final String TAG = MultitaskingController.class.getSimpleName();

    private static final boolean DEBUG = false;

    // All proxies indexed by calling process id.
    private final SparseArray<MultitaskingControllerProxy> mProxies = new SparseArray<>();

    @Nullable
    private IMultitaskingDelegate mShellDelegate;

    private final DelegateCallback mDelegateCallback = new DelegateCallback();

    private final DeathRecipient mShellDelegateDeathRecipient = new ShellDeathRecipient();

    @Override
    public IMultitaskingControllerCallback setMultitaskingDelegate(
            IMultitaskingDelegate delegate) {
        if (DEBUG) {
            Slog.d(TAG, "setMultitaskingDelegate: " + delegate);
        }
        enforceTaskPermission("setMultitaskingDelegate()");
        Objects.requireNonNull(delegate);

        synchronized (this) {
            try {
                if (mShellDelegate != null) {
                    mShellDelegate.asBinder().unlinkToDeath(mShellDelegateDeathRecipient, 0);
                }
                mShellDelegate = delegate;
                mShellDelegate.asBinder().linkToDeath(mShellDelegateDeathRecipient, 0);
            } catch (RemoteException e) {
                throw new RuntimeException("Unable to set Shell delegate", e);
            }
        }
        return mDelegateCallback;
    }

    @Override
    public IMultitaskingDelegate getClientInterface(
            IMultitaskingControllerCallback callback) {
        if (DEBUG) {
            Slog.d(TAG, "getClientInterface");
        }
        enforceMultitaskingControlPermission("getClientInterface()");
        Objects.requireNonNull(callback);

        final int callingPid = Binder.getCallingPid();
        final int callingUid = Binder.getCallingUid();

        synchronized (this) {
            if (mShellDelegate == null) {
                throw new IllegalStateException("WM Shell multitasking delegate not registered.");
            }
            MultitaskingControllerProxy proxy = mProxies.get(callingPid);
            if (proxy == null) {
                proxy = new MultitaskingControllerProxy(callback, callingPid, callingUid);
                try {
                    IBinder binder = callback.asBinder();
                    binder.linkToDeath(proxy, 0);
                } catch (RemoteException ex) {
                    throw new RuntimeException(ex);
                }
                mProxies.put(callingPid, proxy);
            } else {
                if (proxy.mCallback != callback) {
                    throw new IllegalArgumentException(
                            "An existing client interface with a different callback found.");
                }
            }
            return proxy;
        }
    }

    /**
     * A proxy class that applies the policy restrictions to the calls coming from the app clients
     * and passes to the registered WM Shell delegate.
     */
    private class MultitaskingControllerProxy extends IMultitaskingDelegate.Stub
            implements DeathRecipient {
        final int mPid;
        final int mUid;
        final IMultitaskingControllerCallback mCallback;
        private final List<IBinder> mBubbleTokens = new ArrayList<>();

        MultitaskingControllerProxy(
                @NonNull IMultitaskingControllerCallback callback,
                int pid,
                int uid) {
            Objects.requireNonNull(callback);
            mCallback = callback;
            mPid = pid;
            mUid = uid;
        }

        @RequiresPermission(REQUEST_SYSTEM_MULTITASKING_CONTROLS)
        @Override
        public void createBubble(@NonNull IBinder token, @NonNull Intent intent,
                boolean collapsed) {
            if (DEBUG) {
                Slog.d(TAG, "createBubble token: " + token + " intent: " + intent
                        + " collapsed: " + collapsed);
            }
            enforceMultitaskingControlPermission("createBubble()");
            Objects.requireNonNull(token);
            if (mBubbleTokens.contains(token)) {
                throw new IllegalArgumentException("Bubble already exists for token");
            }
            Objects.requireNonNull(intent);
            final ComponentName componentName = intent.getComponent();
            if (componentName == null) {
                throw new IllegalArgumentException(
                        "Component name must be set to launch into a Bubble.");
            }

            final long origId = Binder.clearCallingIdentity();
            try {
                // TODO: sanitize the incoming intent?
                mShellDelegate.createBubble(token, intent, collapsed);
                mBubbleTokens.add(token);
            } catch (RemoteException e) {
                Slog.e(TAG, "Exception creating bubble", e);
            } finally {
                Binder.restoreCallingIdentity(origId);
            }
        }

        @RequiresPermission(REQUEST_SYSTEM_MULTITASKING_CONTROLS)
        @Override
        public void updateBubbleState(IBinder token, boolean collapsed) {
            if (DEBUG) {
                Slog.d(TAG, "updateBubbleState token: " + token + " collapsed: " + collapsed);
            }
            enforceMultitaskingControlPermission("updateBubbleState()");
            Objects.requireNonNull(token);
            if (!mBubbleTokens.contains(token)) {
                Slog.e(TAG, "Can't update Bubble state - none found for provided token");
                return;
            }

            final long origId = Binder.clearCallingIdentity();
            try {
                mShellDelegate.updateBubbleState(token, collapsed);
            } catch (RemoteException e) {
                Slog.e(TAG, "Exception updating bubble state", e);
            } finally {
                Binder.restoreCallingIdentity(origId);
            }
        }

        @RequiresPermission(REQUEST_SYSTEM_MULTITASKING_CONTROLS)
        @Override
        public void updateBubbleMessage(IBinder token, String message) {
            if (DEBUG) {
                Slog.d(TAG, "updateBubbleMessage token: " + token + " message: " + message);
            }
            enforceMultitaskingControlPermission("updateBubbleMessage()");
            Objects.requireNonNull(token);
            if (!mBubbleTokens.contains(token)) {
                Slog.e(TAG, "Can't update Bubble message - none found for provided token");
                return;
            }

            final long origId = Binder.clearCallingIdentity();
            try {
                mShellDelegate.updateBubbleMessage(token, message);
            } catch (RemoteException e) {
                Slog.e(TAG, "Exception updating bubble message", e);
            } finally {
                Binder.restoreCallingIdentity(origId);
            }
        }

        @RequiresPermission(REQUEST_SYSTEM_MULTITASKING_CONTROLS)
        @Override
        public void removeBubble(IBinder token) {
            if (DEBUG) {
                Slog.d(TAG, "removeBubble token: " + token);
            }
            enforceMultitaskingControlPermission("removeBubble()");
            Objects.requireNonNull(token);
            if (!mBubbleTokens.contains(token)) {
                Slog.e(TAG, "Can't remove Bubble - none found for provided token");
                return;
            }

            final long origId = Binder.clearCallingIdentity();
            try {
                mShellDelegate.removeBubble(token);
            } catch (RemoteException e) {
                Slog.e(TAG, "Exception removing bubble", e);
            } finally {
                Binder.restoreCallingIdentity(origId);
            }
        }

        @SuppressLint("MissingPermission") // Bubble removal is automatic in this case
        @Override
        public void binderDied() {
            if (DEBUG) {
                Slog.d(TAG, "Client binder died");
            }
            synchronized (MultitaskingController.this) {
                final long origId = Binder.clearCallingIdentity();
                try {
                    for (IBinder token : mBubbleTokens) {
                        try {
                            mShellDelegate.removeBubble(token);
                        } catch (RemoteException e) {
                            Slog.e(TAG, "Exception cleaning up bubbles for a dead binder", e);
                        }
                    }
                    mProxies.remove(mPid);
                } finally {
                    Binder.restoreCallingIdentity(origId);
                }
            }
        }
    }

    private class DelegateCallback extends IMultitaskingControllerCallback.Stub {
        @Override
        public void onBubbleRemoved(IBinder token) {
            if (DEBUG) {
                Slog.d(TAG, "Shell reported bubble removed");
            }
            synchronized (MultitaskingController.this) {
                for (int i = 0; i < mProxies.size(); i++) {
                    MultitaskingControllerProxy proxy = mProxies.valueAt(i);
                    if (proxy.mBubbleTokens.contains(token)) {
                        proxy.mBubbleTokens.remove(token);
                        try {
                            proxy.mCallback.onBubbleRemoved(token);
                        } catch (RemoteException e) {
                            Slog.e(TAG, "Exception notifying client about bubble removal", e);
                        }
                        return;
                    }
                }
            }
            Slog.e(TAG, "Shell reported bubble removed for a non-registered client");
        }
    }

    static void enforceMultitaskingControlPermission(String func) {
        if (ActivityManagerService.checkComponentPermission(
                REQUEST_SYSTEM_MULTITASKING_CONTROLS, Binder.getCallingPid(),
                Binder.getCallingUid(), PackageManager.PERMISSION_GRANTED, -1 /* owningUid */,
                true /* exported */) == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String msg = "Permission Denial: " + func + " from pid=" + Binder.getCallingPid() + ", uid="
                + Binder.getCallingUid()
                + " requires android.permission.REQUEST_SYSTEM_MULTITASKING_CONTROLS";
        Slog.w(TAG, msg);
        throw new SecurityException(msg);
    }

    private class ShellDeathRecipient implements DeathRecipient {
        @Override
        public void binderDied() {
            synchronized (this) {
                Slog.w(TAG, "Clearing MultitaskingController state - Shell binder death");
                mShellDelegate = null;
                for (int i = 0; i < mProxies.size(); i++) {
                    MultitaskingControllerProxy proxy = mProxies.valueAt(i);
                    for (IBinder token : proxy.mBubbleTokens) {
                        try {
                            proxy.mCallback.onBubbleRemoved(token);
                        } catch (RemoteException e) {
                            Slog.e(TAG, "Exception notifying client after Shell death", e);
                        }
                    }
                    proxy.mBubbleTokens.clear();
                }
            }
        }
    };
}
