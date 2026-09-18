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

package com.android.server.allowlist;

import static android.Manifest.permission.DUMP;
import static android.Manifest.permission.QUERY_ALLOWLIST;

import android.annotation.EnforcePermission;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.PermissionManuallyEnforced;
import android.annotation.RequiresNoPermission;
import android.annotation.UserIdInt;
import android.app.appfunctions.flags.Flags;
import android.content.Context;
import android.content.pm.SignedPackage;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteCallback;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ShellCallback;
import android.os.UserHandle;
import android.os.allowlist.AllowlistManager;
import android.os.allowlist.AllowlistRequest;
import android.os.allowlist.AllowlistResponse;
import android.os.allowlist.IAllowlistProviderService;
import android.os.allowlist.IAllowlistService;
import android.os.allowlist.IOnAllowlistChangedListener;
import android.os.allowlist.IProviderOnAllowlistChangedListener;
import android.os.allowlist.SignedPackageMultiMap;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.IndentingPrintWriter;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.appbinding.AppBindingService;
import com.android.server.appbinding.finders.AllowlistProviderServiceFinder;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A system service which interfaces with allowlist clients via the AllowlistManager, and the
 * AllowlistProviderService, which serves requests made to the Allowlist.
 */
public final class AllowlistService extends SystemService {

    private static final String LOG_TAG = AllowlistService.class.getSimpleName();

    // Timeout for binding to the AllowlistProviderService.
    private static final int APP_BINDING_TIMEOUT_MS = 10 * 1000 * Build.HW_TIMEOUT_MULTIPLIER;

    private AppBindingService mAppBindingService;
    // Only used for testing purpose
    private volatile IAllowlistProviderService mTestProviderService;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final ArrayMap<IBinder, ListenerRecord> mListenerRecords = new ArrayMap<>();

    @GuardedBy("mLock")
    private final ArrayMap<AllowlistRequest, ArraySet<IBinder>> mRequestListeners =
            new ArrayMap<>();

    // Holds the allowlist provided through Shell. It will be merged with the actual allowlist from
    // the provider service in the queryAllowlist() call.
    @GuardedBy("mLock")
    private final SparseArray<Bundle> mShellAllowlists = new SparseArray<>();

    private final IProviderOnAllowlistChangedListener mOnProviderAllowlistsChangedListener =
            new IProviderOnAllowlistChangedListener.Stub() {
                @RequiresNoPermission
                @Override
                public void onAllowlistChanged(List<AllowlistRequest> requests) {
                    synchronized (mLock) {
                        for (AllowlistRequest request : requests) {
                            dispatchAllowlistChangedLocked(request);
                        }
                    }
                }
            };

    // When the provider service dies, re-register all listeners once it's reconnected and
    // then notify the listeners.
    private final IBinder.DeathRecipient mProviderDeathRecipient = new IBinder.DeathRecipient() {
        @Override
        public void binderDied() {
            Slog.w(LOG_TAG, "AllowlistProviderService disconnected.");

            final List<AllowlistRequest> requests;
            synchronized (mLock) {
                requests = new ArrayList<>(mRequestListeners.keySet());
            }

            dispatchAllowlistServiceEvent(getContext().getUserId(), service -> {
                if (service == null) {
                    Slog.w(LOG_TAG, "AllowlistProviderService connection is null after binderDied");
                    return;
                }
                // Link to death on the new binder object.
                try {
                    service.asBinder().linkToDeath(mProviderDeathRecipient, 0);
                } catch (RemoteException e) {
                    Slog.e(LOG_TAG, "Failed to link to death for IAllowlistProviderService");
                    return;
                }

                for (AllowlistRequest request : requests) {
                    try {
                        service.addRequestForAllowlistChange(request,
                                mOnProviderAllowlistsChangedListener);
                    } catch (RemoteException e) {
                        Slog.w(LOG_TAG, "Failed to re-register an AllowlistRequest");
                    }
                }

                synchronized (mLock) {
                    for (AllowlistRequest request : requests) {
                        dispatchAllowlistChangedLocked(request);
                    }
                }
            });
        }
    };

    public AllowlistService(@NonNull Context context) {
        super(context);
    }

    /**
     * Send a query to the allowlist through AllowlistProviderService.
     *
     * @param request A request for the allowlist
     * @param callback A callback to receive the response from the allowlist provider.
     *
     * @see AllowlistRequest
     * @see AllowlistResponse
     */
    private void queryAllowlist(@NonNull AllowlistRequest request,
            @NonNull RemoteCallback callback) {
        RemoteCallback wrapperCallback = new RemoteCallback(result -> {
            AllowlistResponse response = null;
            if (result != null && result.containsKey(AllowlistManager.KEY_ALLOWLIST_RESPONSE)) {
                response = result.getParcelable(
                        AllowlistManager.KEY_ALLOWLIST_RESPONSE, AllowlistResponse.class);
            }
            if (response == null) {
                Slog.w(LOG_TAG, "No response from allowlist provider");
            }

            // If there are allowlists provided through shell, filter the request by the shell
            // allowlists and merge the result with the actual response from the provider.
            AllowlistResponse mergedResponse = null;
            synchronized (mLock) {
                int allowlistId = request.getAllowlistId();
                Bundle shellAllowlistBundle = mShellAllowlists.get(allowlistId);

                if (shellAllowlistBundle != null) {
                    ArrayList<SignedPackage> allowedPackages =
                            shellAllowlistBundle.getParcelableArrayList(
                                    AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGES,
                                    SignedPackage.class);

                    if (allowedPackages != null) {
                        ArrayList<SignedPackage> filteredPackages =
                                AllowlistShellUtils.filterShellAllowlist(request, allowedPackages);
                        if (!filteredPackages.isEmpty()) {
                            mergedResponse = AllowlistShellUtils.mergeFilteredAllowlistWithResponse(
                                    response, filteredPackages);
                        }
                    }

                    SignedPackageMultiMap allowedPackageMultiMap =
                            shellAllowlistBundle.getParcelable(
                                    AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGE_MULTI_MAP,
                                    SignedPackageMultiMap.class);

                    if (allowedPackageMultiMap != null) {
                        SignedPackageMultiMap filteredMultiMap =
                                AllowlistShellUtils.filterShellAllowlist(request,
                                        allowedPackageMultiMap);
                        if (!filteredMultiMap.getMap().isEmpty()) {
                            if (mergedResponse == null) {
                                mergedResponse = response;
                            }
                            mergedResponse = AllowlistShellUtils.mergeFilteredAllowlistWithResponse(
                                    mergedResponse, filteredMultiMap);
                        }
                    }
                }
            }

            Bundle newResult = result;
            if (mergedResponse != null) {
                if (newResult == null) {
                    newResult = new Bundle();
                }
                newResult.putParcelable(AllowlistManager.KEY_ALLOWLIST_RESPONSE, mergedResponse);
            }
            callback.sendResult(newResult);
        });

        IAllowlistProviderService testProviderService = mTestProviderService;
        if (testProviderService != null
                && request.getAllowlistId() == AllowlistManager.ALLOWLIST_ID_TEST) {
            try {
                testProviderService.queryAllowlist(request, wrapperCallback);
            } catch (RemoteException e) {
                Slog.w(LOG_TAG, "Exception when querying test provider", e);
                wrapperCallback.sendResult(null);
            }
        } else {
            dispatchAllowlistServiceEvent(getContext().getUserId(), service -> {
                if (service == null) {
                    Slog.w(LOG_TAG, "AllowlistProviderService connection is null");
                    wrapperCallback.sendResult(null);
                    return;
                }

                try {
                    service.queryAllowlist(request, wrapperCallback);
                } catch (RemoteException e) {
                    Slog.w(LOG_TAG, "Exception when querying allowlist");
                    wrapperCallback.sendResult(null);
                }
            });
        }
    }

    /**
     * Add a listener for changes to a given allowlist.
     * Note: The system service will use its own listener to listen for allowlist changes through
     * allowlist providers.
     * @param request Specify the changes to the allowlist to listen for
     * @param listener A listener for allowlist changes
     */
    private void addOnAllowlistChangedListener(@NonNull AllowlistRequest request,
            @NonNull IOnAllowlistChangedListener listener) {
        boolean isFirstListener;

        synchronized (mLock) {
            isFirstListener = mListenerRecords.isEmpty();
            ListenerRecord record = mListenerRecords.get(listener.asBinder());
            if (record == null) {
                record = new ListenerRecord(listener);
                mListenerRecords.put(listener.asBinder(), record);
            }
            record.mRequests.add(request);
            mRequestListeners.computeIfAbsent(request, k -> new ArraySet<>()).add(
                    listener.asBinder());
        }

        IAllowlistProviderService testProviderService = mTestProviderService;
        if (testProviderService != null
                && request.getAllowlistId() == AllowlistManager.ALLOWLIST_ID_TEST) {
            try {
                testProviderService.addRequestForAllowlistChange(request,
                        mOnProviderAllowlistsChangedListener);
            } catch (RemoteException e) {
                Slog.w(LOG_TAG, "Exception when adding test provider listener", e);
            }
        } else {
            dispatchAllowlistServiceEvent(getContext().getUserId(), service -> {
                if (service == null) {
                    Slog.w(LOG_TAG, "AllowlistProviderService connection is null");
                    return;
                }

                try {
                    service.addRequestForAllowlistChange(request,
                            mOnProviderAllowlistsChangedListener);
                } catch (RemoteException e) {
                    Slog.w(LOG_TAG, "Exception when adding allowlist listener", e);
                    return;
                }
                // If this is the first listener, when receiving the IBinder, call
                // linkToDeath so when the binder dies we can re-register listeners.
                if (isFirstListener) {
                    try {
                        service.asBinder().linkToDeath(mProviderDeathRecipient, 0);
                    } catch (RemoteException e) {
                        Slog.e(LOG_TAG, "Failed to link to death for IAllowlistProviderService");
                    }
                }
            });
        }
    }

    /**
     * Remove a previously registered listener for an allowlist.
     * @param listener The listener to remove
     */
    private void removeOnAllowlistChangedListener(@NonNull IOnAllowlistChangedListener listener) {
        ArrayList<AllowlistRequest> requestsToRemove = new ArrayList<>();

        synchronized (mLock) {
            ListenerRecord listenerRecord = mListenerRecords.remove(listener.asBinder());
            if (listenerRecord == null) {
                Slog.w(LOG_TAG, "Listener does not exist");
                return;
            }
            listenerRecord.unlinkedToDeath();

            for (AllowlistRequest request : listenerRecord.mRequests) {
                ArraySet<IBinder> listeners = mRequestListeners.get(request);
                if (listeners != null) {
                    listeners.remove(listener.asBinder());
                    if (listeners.isEmpty()) {
                        mRequestListeners.remove(request);
                        requestsToRemove.add(request);
                    }
                }
            }
        }

        IAllowlistProviderService testProviderService = mTestProviderService;
        for (AllowlistRequest request : requestsToRemove) {
            if (testProviderService != null
                    && request.getAllowlistId() == AllowlistManager.ALLOWLIST_ID_TEST) {
                try {
                    testProviderService.removeRequestForAllowlistChange(request);
                } catch (RemoteException e) {
                    Slog.w(LOG_TAG, "Exception when adding test provider listener", e);
                }
            } else {
                dispatchAllowlistServiceEvent(getContext().getUserId(), service -> {
                    if (service == null) {
                        Slog.w(LOG_TAG, "AllowlistProviderService connection is null");
                        return;
                    }

                    try {
                        service.removeRequestForAllowlistChange(request);
                    } catch (RemoteException e) {
                        Slog.w(LOG_TAG, "Exception when removing allowlist listener");
                    }
                });
            }
        }
    }

    @Override
    public void onStart() {
        if (!Flags.enableAppFunctionPermissionV2()) {
            return;
        }
        publishBinderService(Context.ALLOWLIST_SERVICE, new AllowlistBinderService());
    }

    @NonNull
    private AppBindingService getAppBindingService() {
        if (mAppBindingService == null) {
            mAppBindingService = LocalServices.getService(AppBindingService.class);
        }
        return mAppBindingService;
    }

    @GuardedBy("mLock")
    private void dispatchAllowlistChangedLocked(@NonNull AllowlistRequest request) {
        ArraySet<IBinder> listeners = mRequestListeners.get(request);

        if (listeners != null && !listeners.isEmpty()) {
            for (IBinder binder : listeners) {
                try {
                    IOnAllowlistChangedListener listener =
                            IOnAllowlistChangedListener.Stub.asInterface(binder);
                    listener.onAllowlistChanged(request);
                } catch (RemoteException e) {
                    Slog.w(LOG_TAG, "Exception when triggering listener", e);
                }
            }
        }
    }

    private void dispatchAllowlistServiceEvent(@UserIdInt int userId,
            @NonNull Consumer<IAllowlistProviderService> action) {
        getAppBindingService().dispatchAppServiceEvent(AllowlistProviderServiceFinder.class, userId,
                connection -> {
                    if (connection == null) {
                        Slog.w(LOG_TAG, "AllowlistProviderService connection is null");
                        action.accept(null);
                        return;
                    }
                    IAllowlistProviderService binder =
                            (IAllowlistProviderService) connection.getServiceBinder();
                    action.accept(binder);
                }, APP_BINDING_TIMEOUT_MS);
    }

    void addPackagesToShellAllowlist(int allowlistId, @NonNull List<SignedPackage> signedPackages) {
        synchronized (mLock) {
            Bundle shellAllowlist = mShellAllowlists.get(allowlistId);
            if (shellAllowlist == null) {
                shellAllowlist = new Bundle();
                mShellAllowlists.put(allowlistId, shellAllowlist);
            }

            ArrayList<SignedPackage> allowedPackages = shellAllowlist.getParcelableArrayList(
                    AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGES, SignedPackage.class);
            if (allowedPackages == null) {
                allowedPackages = new ArrayList<>();
            }

            for (SignedPackage signedPackage : signedPackages) {
                if (!allowedPackages.contains(signedPackage)) {
                    allowedPackages.add(signedPackage);
                }
            }

            shellAllowlist.putParcelableArrayList(AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGES,
                    allowedPackages);
        }
    }

    void addPackageMultiMapToShellAllowlist(int allowlistId, @NonNull SignedPackage signedPackage,
            @NonNull List<SignedPackage> targetPackages) {
        synchronized (mLock) {
            Bundle shellAllowlist = mShellAllowlists.get(allowlistId);
            if (shellAllowlist == null) {
                shellAllowlist = new Bundle();
                mShellAllowlists.put(allowlistId, shellAllowlist);
            }

            SignedPackageMultiMap packageMultiMap = shellAllowlist.getParcelable(
                    AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGE_MULTI_MAP,
                    SignedPackageMultiMap.class);

            Map<SignedPackage, List<SignedPackage>> multiMap =
                    packageMultiMap == null ? new ArrayMap<>() : packageMultiMap.getMap();
            multiMap.computeIfAbsent(signedPackage, k -> new ArrayList<>()).addAll(
                    new ArrayList<>(targetPackages));

            shellAllowlist.putParcelable(
                    AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGE_MULTI_MAP,
                    new SignedPackageMultiMap(multiMap));
        }
    }

    void removePackageFromShellAllowlist(int allowlistId, @NonNull SignedPackage signedPackage) {
        synchronized (mLock) {
            Bundle shellAllowlist = mShellAllowlists.get(allowlistId);
            if (shellAllowlist != null) {
                ArrayList<SignedPackage> allowedPackages = shellAllowlist.getParcelableArrayList(
                        AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGES, SignedPackage.class);
                SignedPackageMultiMap allowedPackageMultiMap = shellAllowlist.getParcelable(
                        AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGE_MULTI_MAP,
                        SignedPackageMultiMap.class);

                if (allowedPackages != null) {
                    allowedPackages.removeIf(
                            allowedPackage -> Objects.equals(allowedPackage, signedPackage));
                    if (allowedPackages.isEmpty()) {
                        shellAllowlist.remove(AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGES);
                    }
                }
                if (allowedPackageMultiMap != null) {
                    allowedPackageMultiMap.getMap().entrySet().removeIf(
                            entry -> Objects.equals(entry.getKey(), signedPackage)
                    );
                    if (allowedPackageMultiMap.getMap().isEmpty()) {
                        shellAllowlist.remove(
                                AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGE_MULTI_MAP);
                    }
                }
                if (shellAllowlist.isEmpty()) {
                    mShellAllowlists.remove(allowlistId);
                }
            }
        }
    }

    void clearShellAllowlist(int allowlistId) {
        synchronized (mLock) {
            mShellAllowlists.remove(allowlistId);
        }
    }

    void dumpShellAllowlist(@NonNull PrintWriter pw, int allowlistId) {
        synchronized (mLock) {
            Bundle shellAllowlist = mShellAllowlists.get(allowlistId);
            if (shellAllowlist == null) {
                pw.println("No Shell allowlist for ID " + allowlistId);
                return;
            }

            ArrayList<SignedPackage> allowedPackages = shellAllowlist.getParcelableArrayList(
                    AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGES, SignedPackage.class);
            SignedPackageMultiMap packageMultiMap = shellAllowlist.getParcelable(
                    AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGE_MULTI_MAP,
                    SignedPackageMultiMap.class);

            if (allowedPackages != null) {
                pw.println("Allowed packages from Shell allowlist " + allowlistId + ":");
                for (SignedPackage p : allowedPackages) {
                    pw.println("  " + p);
                }
            }

            if (packageMultiMap != null) {
                pw.println("Allowed package and target apps from Shell allowlist " + allowlistId
                        + ":");
                for (Map.Entry<SignedPackage, List<SignedPackage>> entry :
                        packageMultiMap.getMap().entrySet()) {
                    pw.println("  " + entry.getKey());
                    for (SignedPackage target : entry.getValue()) {
                        pw.println("    " + target);
                    }
                }
            }
        }
    }

    private void dumpUnchecked(@NonNull IndentingPrintWriter ipw) {
        synchronized (mLock) {
            ipw.printf("mAppBindingService: %s\n", mAppBindingService);
            ipw.printf("mTestProviderService: %s\n", mTestProviderService);
            // TODO(b/461828838): dump the map contents instead
            ipw.printf("mListenerRecords: %d entries\n", mListenerRecords.size());
            ipw.printf("mRequestListeners: %d entries\n", mRequestListeners.size());
            ipw.printf("mShellAllowlists: %d entries\n", mShellAllowlists.size());
        }
    }

    private class ListenerRecord implements IBinder.DeathRecipient {
        final ArraySet<AllowlistRequest> mRequests = new ArraySet<>();
        final IOnAllowlistChangedListener mListener;
        private boolean mLinkedToDeath;

        ListenerRecord(@NonNull IOnAllowlistChangedListener listener) {
            mListener = listener;
            try {
                mListener.asBinder().linkToDeath(this, 0);
            } catch (RemoteException e) {
                Slog.w(LOG_TAG, "Exception when linking to death recipient", e);
            }
            mLinkedToDeath = true;
        }

        void unlinkedToDeath() {
            if (!mLinkedToDeath) {
                return;
            }

            try {
                mListener.asBinder().unlinkToDeath(this, 0);
            } catch (Exception e) {
                Slog.w(LOG_TAG, "Exception when unlinking to death recipient", e);
            }
            mLinkedToDeath = false;
        }

        @Override
        public void binderDied() {
            mLinkedToDeath = false;
            removeOnAllowlistChangedListener(mListener);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ListenerRecord that)) {
                return false;
            }
            return Objects.equals(mRequests, that.mRequests) && Objects.equals(mListener,
                    that.mListener);
        }

        @Override
        public int hashCode() {
            return mRequests.hashCode() ^ mListener.hashCode();
        }
    }

    private final class AllowlistBinderService extends IAllowlistService.Stub {

        @Override
        @EnforcePermission(QUERY_ALLOWLIST)
        public void addOnAllowlistChangedListener(AllowlistRequest request,
                IOnAllowlistChangedListener listener) {
            addOnAllowlistChangedListener_enforcePermission();
            AllowlistService.this.addOnAllowlistChangedListener(request, listener);
        }

        @Override
        @EnforcePermission(QUERY_ALLOWLIST)
        public void removeOnAllowlistChangedListener(IOnAllowlistChangedListener listener) {
            removeOnAllowlistChangedListener_enforcePermission();
            AllowlistService.this.removeOnAllowlistChangedListener(listener);
        }

        @Override
        @EnforcePermission(QUERY_ALLOWLIST)
        public void queryAllowlist(AllowlistRequest request, RemoteCallback callback) {
            queryAllowlist_enforcePermission();
            AllowlistService.this.queryAllowlist(request, callback);
        }

        @Override
        @PermissionManuallyEnforced
        public int handleShellCommand(@NonNull ParcelFileDescriptor in,
                @NonNull ParcelFileDescriptor out, @NonNull ParcelFileDescriptor err,
                @NonNull String[] args) {
            return new AllowlistShellCommand(AllowlistService.this).exec(this,
                    in.getFileDescriptor(), out.getFileDescriptor(), err.getFileDescriptor(), args);
        }

        @Override
        @EnforcePermission(QUERY_ALLOWLIST)
        public void setTestProviderEnabled(boolean enabled) {
            setTestProviderEnabled_enforcePermission();

            synchronized (mLock) {
                if (enabled && mTestProviderService == null) {
                    TestAllowlistProviderService testAllowlistProviderService =
                            new TestAllowlistProviderService();
                    testAllowlistProviderService.onCreate();
                    mTestProviderService = IAllowlistProviderService.Stub.asInterface(
                            testAllowlistProviderService.onBind(null));
                } else if (!enabled) {
                    mTestProviderService = null;
                }
            }
        }

        @Override
        @EnforcePermission(QUERY_ALLOWLIST)
        public void notifyAllowlistChangedListenersForTestProvider(
                List<AllowlistRequest> requests) {
            notifyAllowlistChangedListenersForTestProvider_enforcePermission();

            synchronized (mLock) {
                if (mTestProviderService == null) {
                    throw new IllegalStateException("Test provider not enabled");
                }

                try {
                    mTestProviderService.notifyAllowlistChangedListenersForTestProvider(
                            requests);
                } catch (RemoteException e) {
                    Slog.w(LOG_TAG, "Exception when triggering listeners in test "
                            + "AllowlistProvider", e);
                }
            }
        }

        @Override
        @EnforcePermission(DUMP)
        @SuppressWarnings("MissingEnforcePermissionAnnotation")
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            // NOTE: must call dump_enforcePermission() on first line to avoid one
            // MissingEnforcePermissionAnnotation violation from ErrorProne, but still need to be
            // annotated with @SuppressWarnings, because Binder.dump() itself isn't annotated
            dump_enforcePermission();
            try (IndentingPrintWriter ipw = new IndentingPrintWriter(pw)) {
                dumpUnchecked(ipw);
            }
        }

        @SuppressWarnings("AndroidFrameworkRequiresPermission")
        private void dump_enforcePermission() {
            getContext().enforceCallingPermission(DUMP,
                    "Caller does not hold android.permission.DUMP");
        }
    }
}
