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

package com.android.server.personalcontext.component.client;

import android.annotation.RequiresNoPermission;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.os.UserHandle;
import android.permission.PermissionManager;
import android.service.personalcontext.IOpCallback;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;

import com.android.server.personalcontext.AccessController;
import com.android.server.personalcontext.OperatingModeProvider;
import com.android.server.personalcontext.component.Component;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Base class for component service clients.
 *
 * @param <C> type of client interface
 * @hide
 */
public abstract class BaseServiceClientComponent<C> implements Component {
    // TODO(b/484984634): Determine the optimal timeout value for binder connections.
    private static final long BINDER_CONNECTION_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(30);
    protected static final String TAG = "PersonalContextClient";
    private final Executor mExecutor;

    private final UserHandle mUserHandle;

    protected final Context mContext;
    private final AccessController mAccessController;
    private final ServiceInfo mServiceInfo;
    private final UUID mComponentId;
    private final Intent mServiceIntent;
    private final ComponentName mComponentName;
    private final List<RunWithBinderCallback<C>> mPendingCalls = new ArrayList<>();
    private final List<RunWithScopedBinderCallback<C>> mScopedPendingCalls = new ArrayList<>();
    private boolean mServiceStarted = false;
    private C mClient;

    private final Handler mHandler;

    private final List<IOpCallback> mActiveScopedCallbacks = new ArrayList<>();

    private final OperatingModeProvider mOperatingModeProvider;

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, BaseServiceClientComponent.this + " is online");
            }

            try {
                C client = getServiceWrapper(binder);
                initializeClient(client);
                onStarted(client);
            } catch (Exception e) {
                Slog.w(TAG, BaseServiceClientComponent.this + " failed", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, BaseServiceClientComponent.this + " is offline");
            }
            mExecutor.execute(() -> {
                mServiceStarted = false;
                mClient = null;
            });
        }
    };

    private static class OpCallback extends IOpCallback.Stub {
        private final WeakReference<BaseServiceClientComponent> mComponent;

        private final Runnable mRunnable = () -> {
            try {
                signalCompletion();
            } catch (RemoteException e) {
                Log.d(TAG, "could not complete signal");
            }
        };

        OpCallback(BaseServiceClientComponent component) {
            mComponent = new WeakReference(component);

        }

        private Runnable getCompletionRunnable() {
            return mRunnable;
        }

        @RequiresNoPermission
        @Override
        public void signalCompletion() throws RemoteException {
            final BaseServiceClientComponent serviceClient = mComponent.get();
            if (serviceClient != null) {
                serviceClient.completeCall(this);
            }
        }
    }

    /**
     * Creates tracking for a call. Note that this must be called from within the executor.
     */
    private IOpCallback initiateCall() {
        final OpCallback callback = new OpCallback(this);
        mHandler.postDelayed(callback.getCompletionRunnable(), BINDER_CONNECTION_TIMEOUT_MS);
        mActiveScopedCallbacks.add(callback);

        return callback;
    }

    private void completeCall(OpCallback callback) {
        mExecutor.execute(() -> {
            if (!mActiveScopedCallbacks.contains(callback)) {
                // It's already been cleaned up
                return;
            }
            mHandler.removeCallbacks(callback.getCompletionRunnable());
            mActiveScopedCallbacks.remove(callback);

            // Attempt to cleanup.
            stop();
        });
    }

    public BaseServiceClientComponent(
            Context context,
            AccessController accessController,
            UUID componentId,
            ServiceInfo serviceInfo,
            UserHandle userHandle,
            OperatingModeProvider operatingModeProvider) {
        this(
                context,
                accessController,
                componentId,
                serviceInfo,
                userHandle,
                Executors.newSingleThreadExecutor(),
                new Handler(Looper.getMainLooper()),
                operatingModeProvider);
    }
    protected BaseServiceClientComponent(
            Context context,
            AccessController accessController,
            UUID componentId,
            ServiceInfo serviceInfo,
            UserHandle userHandle,
            Executor executor,
            Handler handler,
            OperatingModeProvider operatingModeProvider) {
        mExecutor = executor;
        mContext = context;
        mAccessController = accessController;
        mServiceInfo = serviceInfo;
        mUserHandle = userHandle;
        mComponentId = componentId;
        mComponentName = new ComponentName(serviceInfo.packageName, serviceInfo.name);
        mServiceIntent = new Intent();
        mServiceIntent.setComponent(mComponentName);
        mHandler = handler;
        mOperatingModeProvider = operatingModeProvider;
    }

    @Override
    public UUID getComponentId() {
        return mComponentId;
    }

    public ParcelUuid getParcelComponentId() {
        return new ParcelUuid(mComponentId);
    }

    public ComponentName getComponentName() {
        return mComponentName;
    }

    public ServiceInfo getServiceInfo() {
        return mServiceInfo;
    }

    @Override
    public String toString() {
        return TextUtils.formatSimple(
                "%s{%s} -> %s",
                getClass().getSimpleName(),
                mComponentId,
                mComponentName.flattenToShortString());
    }

    protected boolean shouldCheckPermissions() {
        return mOperatingModeProvider.hasProperties(
                OperatingModeProvider.OPERATING_PROPERTY_FLAG_ENFORCE_PERMISSIONS);
    }

    protected void enforcePermissions(int pid, int uid, @AccessController.Access int accessFlags) {
        mAccessController.enforcePermissions(pid, uid,
                mOperatingModeProvider.filterAccessFlags(accessFlags));
    }

    /** Returns true if this service client has the given permission. */
    protected boolean checkPermission(String permission) {
        if (!shouldCheckPermissions()) {
            return true;
        }

        return mContext.getSystemService(PermissionManager.class)
                        .checkPackageNamePermission(
                                permission,
                                getComponentName().getPackageName(),
                                Context.DEVICE_ID_DEFAULT,
                                mUserHandle.getIdentifier())
                == PackageManager.PERMISSION_GRANTED;
    }

    protected void runWithScopedBinder(RunWithScopedBinderCallback<C> callback) {
        mExecutor.execute(() -> {
            start();

            if (mClient != null) {
                final IOpCallback opCallback = initiateCall();
                callback.run(mClient, opCallback);
            } else {
                mScopedPendingCalls.add(callback);
            }
        });
    }

    protected final void start() {
        mExecutor.execute(() -> {
            if (mServiceStarted) return;
            mServiceStarted = true;
            mClient = null;

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, this + " service is starting");
            }

            mContext.bindServiceAsUser(
                    mServiceIntent,
                    mServiceConnection,
                    Context.BIND_AUTO_CREATE | Context.BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS,
                    mUserHandle);
        });
    }

    protected final void onStarted(C client) throws RemoteException {
        mExecutor.execute(() -> {
            if (!mServiceStarted) return;
            mClient = client;

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, BaseServiceClientComponent.this + " is available");
            }

            for (RunWithBinderCallback<C> callback : mPendingCalls) {
                callback.run(client);
            }
            mPendingCalls.clear();

            for (RunWithScopedBinderCallback<C> callback : mScopedPendingCalls) {
                final IOpCallback opCallback = initiateCall();
                callback.run(client, opCallback);
            }

            mScopedPendingCalls.clear();
            stop();
        });
    }

    protected final void stop() {
        mExecutor.execute(() -> {
            if (!mActiveScopedCallbacks.isEmpty() || mClient == null) {
                return;
            }

            mServiceStarted = false;
            mClient = null;

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, this + " service is stopping");
            }
            mContext.unbindService(mServiceConnection);
        });
    }

    protected abstract C getServiceWrapper(IBinder binder);

    protected abstract void initializeClient(C client) throws RemoteException;

    protected final boolean isAllowed(int accessFlags) {
        return mAccessController.isClientAllowed(this,
                mOperatingModeProvider.filterAccessFlags(accessFlags));
    }

    /**
     * Callback interface for calls made on a client.
     *
     * @param <C> type of client interface
     * @hide
     */
    public interface RunWithBinderCallback<C> {
        /** Runs the callback with a connected client. */
        void run(C client);
    }

    /**
     * Callback interface for calls made on a client.
     *
     * @param <C> type of client interface
     * @hide
     */
    public interface RunWithScopedBinderCallback<C> {
        /** Runs the callback with a connected client. */
        void run(C client, IOpCallback opCallback);
    }
}
