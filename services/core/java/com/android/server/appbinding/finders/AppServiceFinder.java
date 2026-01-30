/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.server.appbinding.finders;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.supervision.flags.Flags;
import android.content.Context;
import android.content.pm.IPackageManager;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.server.appbinding.AppBindingConstants;
import com.android.server.appbinding.AppBindingService;
import com.android.server.appbinding.AppBindingUtils;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Baseclss that finds "persistent" service from a type of an app.
 *
 * @param <TServiceType> Type of the target service class.
 * @param <TServiceInterfaceType> Type of the IInterface class used by TServiceType.
 */
public abstract class AppServiceFinder<TServiceType, TServiceInterfaceType extends IInterface> {
    protected static final String TAG = AppBindingService.TAG;
    protected static final boolean DEBUG = AppBindingService.DEBUG;

    protected final Context mContext;
    protected final BiConsumer<AppServiceFinder, Integer> mListener;
    protected final Handler mHandler;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    @Deprecated
    private final SparseArray<String> mTargetPackages = new SparseArray(4);

    @GuardedBy("mLock")
    @Deprecated
    private final SparseArray<ServiceInfo> mTargetServices = new SparseArray(4);

    @GuardedBy("mLock")
    @Deprecated
    private final SparseArray<String> mLastMessages = new SparseArray(4);

    @GuardedBy("mLock")
    private final SparseArray<HashMap<String, TargetServiceInfo>> mServiceInfos =
            new SparseArray(4);

    /**
     * Helper class to store the ServiceInfo and the last message related to it.
     */
    private static class TargetServiceInfo {
        @Nullable
        public final ServiceInfo serviceInfo;
        @Nullable
        public final String lastMessage;

        TargetServiceInfo(@Nullable ServiceInfo serviceInfo, @Nullable String lastMessage) {
            this.serviceInfo = serviceInfo;
            this.lastMessage = lastMessage;
        }
    }


    public AppServiceFinder(Context context,
            BiConsumer<AppServiceFinder, Integer> listener,
            Handler callbackHandler) {
        mContext = context;
        mListener = listener;
        mHandler = callbackHandler;
    }

    /** Whether this service should really be enabled. */
    protected boolean isEnabled(AppBindingConstants constants, int userId) {
        return true;
    }

    /** Human readable description of the type of apps; e.g. [Default SMS app] */
    @NonNull
    public abstract String getAppDescription();

    /** Start monitoring apps. (e.g. Start watching the default SMS app changes.) */
    public void startMonitoring() {
    }

    /** Called when a user is removed. */
    public void onUserRemoved(int userId) {
        synchronized (mLock) {
            mTargetPackages.delete(userId);
            mTargetServices.delete(userId);
            mLastMessages.delete(userId);
            mServiceInfos.delete(userId);
        }
    }

    /**
     * Find the target service from the target app on a given user.
     */
    @Nullable
    @Deprecated
    public final ServiceInfo findService(int userId, IPackageManager ipm,
            AppBindingConstants constants) {
        synchronized (mLock) {
            mTargetPackages.put(userId, null);
            mTargetServices.put(userId, null);
            mLastMessages.put(userId, null);

            if (!isEnabled(constants, userId)) {
                final String message = "feature disabled";
                mLastMessages.put(userId, message);
                Slog.i(TAG, getAppDescription() + " " + message);
                return null;
            }

            final String targetPackage = getTargetPackage(userId);
            if (DEBUG) {
                Slog.d(TAG, getAppDescription() + " package=" + targetPackage);
            }
            if (targetPackage == null) {
                final String message = "Target package not found";
                mLastMessages.put(userId, message);
                Slog.w(TAG, getAppDescription() + " u" + userId + " " + message);
                return null;
            }
            mTargetPackages.put(userId, targetPackage);

            final StringBuilder errorMessage = new StringBuilder();
            final ServiceInfo service = AppBindingUtils.findService(
                    targetPackage,
                    userId,
                    getServiceAction(),
                    getServicePermission(),
                    getServiceClass(),
                    ipm,
                    errorMessage);

            if (service == null) {
                final String message = errorMessage.toString();
                mLastMessages.put(userId, message);
                if (DEBUG) {
                    // This log is optional because findService() already did Log.e().
                    Slog.w(TAG, getAppDescription() + " package " + targetPackage + " u" + userId
                            + " " + message);
                }
                return null;
            }
            final String error = validateService(service);
            if (error != null) {
                mLastMessages.put(userId, error);
                Log.e(TAG, error);
                return null;
            }

            final String message = "Valid service found";
            mLastMessages.put(userId, message);
            mTargetServices.put(userId, service);
            return service;
        }
    }

    /**
     * Find the target service from the target app on a given user.
     */
    @Nullable
    public final ServiceInfo findService(int userId, IPackageManager ipm,
            AppBindingConstants constants, String targetPackage) {
        synchronized (mLock) {
            HashMap<String, TargetServiceInfo> currServiceInfo = mServiceInfos.get(userId);
            if (currServiceInfo == null) {
                currServiceInfo = new HashMap<>();
                mServiceInfos.put(userId, currServiceInfo);
            }
            if (DEBUG) {
                Slog.d(TAG, getAppDescription() + " package=" + targetPackage);
            }

            if (!isEnabled(constants, userId)) {
                final String message = "feature disabled";
                currServiceInfo.put(targetPackage, new TargetServiceInfo(null, message));
                Slog.i(TAG, getAppDescription() + " " + message);
                return null;
            }

            final StringBuilder errorMessage = new StringBuilder();
            final ServiceInfo service = AppBindingUtils.findService(
                    targetPackage,
                    userId,
                    getServiceAction(),
                    getServicePermission(),
                    getServiceClass(),
                    ipm,
                    errorMessage);

            if (service == null) {
                final String message = errorMessage.toString();
                currServiceInfo.put(targetPackage, new TargetServiceInfo(null, message));
                if (DEBUG) {
                    Slog.w(TAG, getAppDescription() + " package " + targetPackage + " u" + userId
                            + " " + message);
                }
                return null;
            }
            final String error = validateService(service);
            if (error != null) {
                currServiceInfo.put(targetPackage, new TargetServiceInfo(null, error));
                Log.e(TAG, error);
                return null;
            }

            currServiceInfo.put(targetPackage, new TargetServiceInfo(service,
                    "Valid service found"));
            return service;
        }
    }

    /**
     * Find the list of target service from the target apps on a given user.
     */
    @Nullable
    public final List<ServiceInfo> findServices(int userId, IPackageManager ipm,
            AppBindingConstants constants) {
        final Set<String> targetPackages = getTargetPackages(userId);
        synchronized (mLock) {
            mServiceInfos.put(userId, new HashMap<>());

            if (DEBUG) {
                Slog.d(TAG, getAppDescription() + " packages=" + targetPackages);
            }

            if (!isEnabled(constants, userId) || targetPackages.isEmpty()) {
                final String message = (!isEnabled(constants, userId)) ? "feature disabled"
                        : "Target packages not found";
                mServiceInfos.get(userId).put(null, new TargetServiceInfo(null, message));
                Slog.w(TAG, getAppDescription() + " u" + userId + " " + message);
                return new ArrayList<>();
            }
        }

        List<ServiceInfo> services = new ArrayList<>();
        for (String targetPackage: targetPackages) {
            ServiceInfo service = findService(userId, ipm, constants, targetPackage);
            if (service != null) {
                services.add(service);
            }
        }
        return services;
    }

    protected abstract Class<TServiceType> getServiceClass();

    /**
     * Convert a binder reference to a service interface type.
     */
    public abstract TServiceInterfaceType asInterface(IBinder obj);

    /**
     * @return the target package on a given user.
     */
    @Nullable
    @Deprecated
    public abstract String getTargetPackage(int userId);

    /**
     * @return the target packages on a given user.
     */
    public abstract Set<String> getTargetPackages(int userId);

    /**
     * @return the intent action that identifies the target service in the target app.
     */
    @NonNull
    protected abstract String getServiceAction();

    /**
     * @return the permission that the target service must be protected with.
     */
    @NonNull
    protected abstract String getServicePermission();

    /**
     * Subclass can implement it to decide whether to accept a service (by returning null) or not
     * (by returning an error message.)
     */
    protected String validateService(ServiceInfo service) {
        return null;
    }

    /** Return the bind flags for this service. */
    public abstract int getBindFlags(AppBindingConstants constants);

    /** Dumpsys support. */
    public void dump(String prefix, PrintWriter pw) {
        pw.print(prefix);
        pw.print("App type: ");
        pw.print(getAppDescription());
        pw.println();

        synchronized (mLock) {
            for (int i = 0; i < mServiceInfos.size(); i++) {
                final int userId = mServiceInfos.keyAt(i);
                pw.print(prefix);
                pw.print("  User: ");
                pw.print(userId);
                pw.println();
                for (String targetPackage : mServiceInfos.get(userId).keySet()) {
                    pw.print(prefix);
                    pw.print("    Package: ");
                    pw.print(targetPackage);
                    pw.println();

                    pw.print(prefix);
                    pw.print("    Service: ");
                    pw.print(mServiceInfos.get(userId).get(targetPackage).serviceInfo);
                    pw.println();

                    pw.print(prefix);
                    pw.print("    Message: ");
                    pw.print(mServiceInfos.get(userId).get(targetPackage).lastMessage);
                    pw.println();
                }
                pw.println();
            }

            for (int i = 0; i < mTargetPackages.size(); i++) {
                final int userId = mTargetPackages.keyAt(i);
                pw.print(prefix);
                pw.print("  User: ");
                pw.print(userId);
                pw.println();

                pw.print(prefix);
                pw.print("    Package: ");
                pw.print(mTargetPackages.get(userId));
                pw.println();

                pw.print(prefix);
                pw.print("    Service: ");
                pw.print(mTargetServices.get(userId));
                pw.println();

                pw.print(prefix);
                pw.print("    Message: ");
                pw.print(mLastMessages.get(userId));
                pw.println();
            }
        }
    }

    /** Dumpys support */
    public void dumpSimple(PrintWriter pw) {
        synchronized (mLock) {
            for (int i = 0; i < mServiceInfos.size(); i++) {
                final int userId = mServiceInfos.keyAt(i);
                pw.println(String.format("finder,%s,%s", getAppDescription(), userId));
                for (String targetPackage : mServiceInfos.get(userId).keySet()) {
                    pw.println(String.format("finder,%s,%s,%s,%s,%s",
                            getAppDescription(), userId, targetPackage,
                            mServiceInfos.get(userId).get(targetPackage).serviceInfo,
                            mServiceInfos.get(userId).get(targetPackage).lastMessage));
                }
            }
            for (int i = 0; i < mTargetPackages.size(); i++) {
                final int userId = mTargetPackages.keyAt(i);
                pw.print("finder,");
                pw.print(getAppDescription());
                pw.print(",");
                pw.print(userId);
                pw.print(",");
                pw.print(mTargetPackages.get(userId));
                pw.print(",");
                pw.print(mTargetServices.get(userId));
                pw.print(",");
                pw.print(mLastMessages.get(userId));
                pw.println();
            }
        }
    }
}
