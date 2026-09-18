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

package com.android.server.appfunctions.allowlist;

import static android.os.allowlist.AllowlistManager.RESPONSE_STATUS_ERROR_PROVIDER;
import static android.os.allowlist.AllowlistManager.RESPONSE_STATUS_ERROR_NETWORK;
import static android.os.allowlist.AllowlistManager.RESPONSE_STATUS_ERROR_INVALID_REQUEST;

import static com.android.server.appfunctions.AppFunctionExecutors.THREAD_POOL_EXECUTOR;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SignedPackage;
import android.content.pm.SigningInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.allowlist.AllowlistManager;
import android.os.allowlist.AllowlistRequest;
import android.os.allowlist.AllowlistResponse;
import android.os.allowlist.AllowlistManager.ResponseStatus;
import android.os.allowlist.SignedPackageMultiMap;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.LruCache;
import android.util.PackageUtils;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.infra.AndroidFuture;
import com.android.internal.os.BackgroundThread;
import com.android.server.LocalServices;
import com.android.server.appfunctions.ServiceConfig;
import com.android.server.appfunctions.ServiceConfigImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Implementation of {@link AppFunctionAllowlistReader} to read allowlist from system service. */
public class SystemAppFunctionAllowlistReader implements AppFunctionAllowlistReader {
    private static final String TAG = "AppFunctionAllowlistReader";
    private static final boolean DEBUG = Build.TYPE.equals("eng");
    private static final String WILDCARD_PACKAGE_NAME = "*";

    private static SystemAppFunctionAllowlistReader sInstance = null;

    private final Object mCacheLock = new Object();

    @GuardedBy("mCacheLock")
    private final LruCache<SignedPackage, ArraySet<String>> mCache;

    private final Object mListenerLock = new Object();

    @GuardedBy("mListenerLock")
    private OnAllowlistChangedListener mOnAllowlistChangedListener = null;

    private final PackageManager mPackageManager;

    private final AllowlistManager mAllowlistManager;

    private final Executor mThreadPoolExecutor;

    private final Executor mBackgroundExecutor;

    @VisibleForTesting
    public SystemAppFunctionAllowlistReader(
            @NonNull PackageManager packageManager,
            @NonNull AllowlistManager allowlistManager,
            @NonNull ServiceConfig serviceConfig,
            @NonNull Executor threadPoolExecutor,
            @NonNull Executor backgroundExecutor) {
        mPackageManager = Objects.requireNonNull(packageManager);
        mAllowlistManager = Objects.requireNonNull(allowlistManager);
        mCache = new LruCache<>(serviceConfig.getAppFunctionAllowlistCacheSize());
        mThreadPoolExecutor = Objects.requireNonNull(threadPoolExecutor);
        mBackgroundExecutor = Objects.requireNonNull(backgroundExecutor);
    }

    /**
     * Purge the cache.
     *
     * <p>This should only be used by shell command for testing.
     */
    public void purgeCache() {
        synchronized (mCacheLock) {
            mCache.evictAll();
        }
    }

    @Override
    @NonNull
    @RequiresPermission(
            allOf = {
                android.Manifest.permission.QUERY_ALLOWLIST,
                android.Manifest.permission.INTERACT_ACROSS_USERS
            })
    public CompletableFuture<Boolean> isAllowlisted(
            @NonNull String agentPackageName, @NonNull String targetPackageName, int userId) {
        if (agentPackageName.equals(targetPackageName)) {
            // Interaction with the app's own AppFunction is implicitly allowed.
            return AndroidFuture.completedFuture(true);
        }

        PackageInfo agentPackageInfo = getPackageInfo(agentPackageName, userId);
        if (agentPackageInfo == null) {
            return AndroidFuture.completedFuture(false);
        }

        byte[] agentLatestCertificate = getLatestCertificateDigest(agentPackageInfo);
        if (agentLatestCertificate == null) {
            return AndroidFuture.completedFuture(false);
        }
        SignedPackage agentSignedPackage =
                new SignedPackage(
                        agentPackageName,
                        PackageUtils.computeSha256DigestBytes(agentLatestCertificate));

        maybeStartAlowlistListener();
        return getValidTargetPackages(agentSignedPackage)
                .thenApply(
                        (allowlistTargets) -> {
                            if (DEBUG) {
                                Slog.d(
                                        TAG,
                                        "Allowlist targets for "
                                                + agentPackageName
                                                + " in user "
                                                + userId
                                                + " are: "
                                                + allowlistTargets.toString());
                            }
                            if (allowlistTargets.contains(WILDCARD_PACKAGE_NAME)) {
                                return true;
                            }
                            return allowlistTargets.contains(targetPackageName);
                        })
                .exceptionally(
                        (exception) -> {
                            Slog.w(
                                    TAG,
                                    "Fail to validate allowlist for "
                                            + agentPackageName
                                            + " to "
                                            + targetPackageName
                                            + " in user "
                                            + userId,
                                    exception);
                            if (exception instanceof AllowlistResponseException) {
                                int status = ((AllowlistResponseException) exception).getStatus();
                                if (status == RESPONSE_STATUS_ERROR_PROVIDER
                                        || status == RESPONSE_STATUS_ERROR_INVALID_REQUEST
                                        || status == RESPONSE_STATUS_ERROR_NETWORK) {
                                    return true;
                                }
                            }
                            return false;
                        });
    }

    @RequiresPermission(android.Manifest.permission.QUERY_ALLOWLIST)
    private void maybeStartAlowlistListener() {
        synchronized (mListenerLock) {
            if (mOnAllowlistChangedListener != null) {
                return;
            }
            mOnAllowlistChangedListener = new OnAllowlistChangedListener();
        }
        Bundle requestData = new Bundle();
        requestData.putBoolean(AllowlistManager.REQUEST_KEY_INSTALLED_PACKAGES_ONLY, true);
        AllowlistRequest request =
                new AllowlistRequest(AllowlistManager.ALLOWLIST_ID_APP_FUNCTION, requestData);

        if (DEBUG) {
            Slog.d(TAG, "Start allowlist listener");
        }
        mAllowlistManager.addOnAllowlistChangedListener(
                request, mBackgroundExecutor, mOnAllowlistChangedListener);
    }

    /**
     * Gets a list of valid targets for {@code agentSignedPackage}.
     *
     * <p>Lazily retrieve from the remote source if unavailable in local cache. In most cases, the
     * caller of AppFunction is very limited. Therefore, the cache miss for follow up query should
     * be less common. This can reduce the IPC latency for each request.
     */
    @NonNull
    @RequiresPermission(android.Manifest.permission.QUERY_ALLOWLIST)
    private CompletableFuture<ArraySet<String>> getValidTargetPackages(
            @NonNull SignedPackage agentSignedPackage) {
        synchronized (mCacheLock) {
            ArraySet<String> cachedAllowlistTargets = mCache.get(agentSignedPackage);
            if (cachedAllowlistTargets != null) {
                return AndroidFuture.completedFuture(cachedAllowlistTargets);
            }
        }
        ArrayList<SignedPackage> agentSignedPackages = new ArrayList<>();
        agentSignedPackages.add(agentSignedPackage);
        AllowlistRequest request = buildAllowlistRequest(agentSignedPackages);
        return queryAllowlistFuture(request)
                .thenCompose(
                        (response) -> {
                            return processAllowlistResponse(agentSignedPackage, response);
                        })
                .thenApply(
                        (allowlistTargets) -> {
                            synchronized (mCacheLock) {
                                mCache.put(agentSignedPackage, allowlistTargets);
                            }
                            return allowlistTargets;
                        });
    }

    private CompletableFuture<ArraySet<String>> processAllowlistResponse(
            @NonNull SignedPackage agentSignedPackage, @NonNull AllowlistResponse response) {
        Objects.requireNonNull(agentSignedPackage);
        Objects.requireNonNull(response);

        if (response.getStatus() != AllowlistManager.RESPONSE_STATUS_SUCCESS) {
            return AndroidFuture.failedFuture(
                    new AllowlistResponseException(
                            "Unable to resolve allowlist from remote source.",
                            response.getStatus()));
        }

        SignedPackageMultiMap allowlistMultiMap =
                response.getData()
                        .getParcelable(
                                AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGE_MULTI_MAP,
                                SignedPackageMultiMap.class);
        ArraySet<String> allowlistTargets = new ArraySet<>();
        if (allowlistMultiMap != null) {
            List<SignedPackage> allowlistSignedPackages =
                    allowlistMultiMap.getMap().getOrDefault(agentSignedPackage, List.of());
            for (SignedPackage allowlistSignedPackage : allowlistSignedPackages) {
                allowlistTargets.add(allowlistSignedPackage.getPackageName());
            }
        }
        return AndroidFuture.completedFuture(allowlistTargets);
    }

    @RequiresPermission(android.Manifest.permission.QUERY_ALLOWLIST)
    private AndroidFuture<AllowlistResponse> queryAllowlistFuture(
            @NonNull AllowlistRequest request) {
        AndroidFuture<AllowlistResponse> future = new AndroidFuture<>();
        mAllowlistManager.queryAllowlist(
                request,
                mThreadPoolExecutor,
                new Consumer<>() {
                    @Override
                    public void accept(@NonNull AllowlistResponse response) {
                        future.complete(response);
                    }
                });
        return future;
    }

    @Nullable
    @RequiresPermission(android.Manifest.permission.INTERACT_ACROSS_USERS)
    private PackageInfo getPackageInfo(@NonNull String packageName, int userId) {
        try {
            return mPackageManager.getPackageInfoAsUser(
                    packageName, /* flags= */ PackageManager.GET_SIGNING_CERTIFICATES, userId);
        } catch (PackageManager.NameNotFoundException e) {
            Slog.w(TAG, "Unable to resolve PackageInfo for " + packageName + " in user " + userId);
            return null;
        }
    }

    private final class OnAllowlistChangedListener implements Consumer<AllowlistRequest> {
        @Override
        public void accept(AllowlistRequest request) {
            Set<SignedPackage> cachedAgentPackages;
            synchronized (mCacheLock) {
                cachedAgentPackages = mCache.snapshot().keySet();
            }
            if (cachedAgentPackages.isEmpty()) {
                return;
            }
            AllowlistRequest updatedRequest =
                    buildAllowlistRequest(new ArrayList<>(cachedAgentPackages));
            if (DEBUG) {
                Slog.d(TAG, "OnAllowlistChangedListener: " + updatedRequest);
            }
            queryAllowlistFuture(updatedRequest)
                    .thenApply(
                            (response) -> {
                                if (DEBUG) {
                                    Slog.d(TAG, "OnAllowlistChangedListener response: " + response);
                                }
                                if (response.getStatus()
                                        != AllowlistManager.RESPONSE_STATUS_SUCCESS) {
                                    Slog.w(
                                            TAG,
                                            "OnAllowlistChangedListener#queryAllowlist failed with "
                                                    + "status: "
                                                    + response.getStatus());
                                    return false;
                                }
                                Bundle responseData = response.getData();
                                SignedPackageMultiMap allowlistMultiMap =
                                        responseData.getParcelable(
                                                AllowlistManager
                                                        .RESPONSE_KEY_ALLOWED_PACKAGE_MULTI_MAP,
                                                SignedPackageMultiMap.class);
                                if (allowlistMultiMap == null) return false;
                                Map<SignedPackage, List<SignedPackage>> allowlistMap =
                                        allowlistMultiMap.getMap();
                                if (DEBUG) {
                                    Slog.d(
                                            TAG,
                                            "OnAllowlistChangedListener allowlistMap: "
                                                    + allowlistMap);
                                }

                                ArraySet<SignedPackage> deletedAgents =
                                        new ArraySet<>(cachedAgentPackages);
                                deletedAgents.removeAll(allowlistMap.keySet());
                                synchronized (mCacheLock) {
                                    for (SignedPackage deletedAgent : deletedAgents) {
                                        mCache.remove(deletedAgent);
                                    }
                                }
                                for (SignedPackage signedPackage : allowlistMap.keySet()) {
                                    ArraySet<String> allowlistTargets = new ArraySet<>();
                                    List<SignedPackage> allowlistTargetSignedPackages =
                                            allowlistMap.getOrDefault(signedPackage, List.of());
                                    for (SignedPackage allowlistTargetSignedPackage :
                                            allowlistTargetSignedPackages) {
                                        allowlistTargets.add(
                                                allowlistTargetSignedPackage.getPackageName());
                                    }
                                    synchronized (mCacheLock) {
                                        mCache.put(signedPackage, allowlistTargets);
                                    }
                                }
                                return true;
                            })
                    .whenComplete(
                            (unused, exception) -> {
                                if (exception != null) {
                                    Slog.w(
                                            TAG,
                                            "OnAllowlistChangedListener failed with exception: "
                                                    + exception);
                                }
                            });
        }
    }

    @NonNull
    private AllowlistRequest buildAllowlistRequest(
            @NonNull ArrayList<SignedPackage> filterAgentPackages) {
        Bundle requestData = new Bundle();
        requestData.putParcelableArrayList(
                AllowlistManager.REQUEST_KEY_FILTER_PACKAGES, filterAgentPackages);
        // Empty filter targets means match all
        requestData.putParcelableArrayList(
                AllowlistManager.REQUEST_KEY_FILTER_TARGETS, new ArrayList<>());
        requestData.putBoolean(AllowlistManager.REQUEST_KEY_INSTALLED_PACKAGES_ONLY, true);
        return new AllowlistRequest(AllowlistManager.ALLOWLIST_ID_APP_FUNCTION, requestData);
    }

    @Nullable
    private byte[] getLatestCertificateDigest(@NonNull PackageInfo packageInfo) {
        SigningInfo signingInfo = packageInfo.signingInfo;
        if (signingInfo == null) {
            if (DEBUG) {
                Slog.d(TAG, "Unable to resolve SigningInfo from " + packageInfo.packageName);
            }
            return null;
        }
        // Multi-signer packages are not supported.
        Signature[] histories = signingInfo.getSigningCertificateHistory();
        if (histories == null || histories.length == 0) {
            if (DEBUG) {
                Slog.d(
                        TAG,
                        "Unable to resolve certificate history from " + packageInfo.packageName);
            }
            return null;
        }
        return histories[histories.length - 1].toByteArray();
    }

    private static final class AllowlistResponseException extends Exception {
        private final int mStatus;

        public AllowlistResponseException(String message, int status) {
            super(message);
            mStatus = status;
        }

        @ResponseStatus
        public int getStatus() {
            return mStatus;
        }

        @Override
        public String toString() {
            return super.toString() + " (status: " + mStatus + ")";
        }
    }

    @Override
    public void dump(@NonNull PrintWriter pw) {
        pw.println("SystemAppFunctionAllowlistReader:");

        synchronized (mCacheLock) {
            pw.println("  Cache:");
            Map<SignedPackage, ArraySet<String>> snapshot = mCache.snapshot();
            for (Map.Entry<SignedPackage, ArraySet<String>> entry : snapshot.entrySet()) {
                pw.println("    " + entry.getKey().toString() + ": " + entry.getValue());
            }
        }
    }

    /** Gets the singleton instance. */
    public static synchronized SystemAppFunctionAllowlistReader getInstance(
            @NonNull Context context) {
        Objects.requireNonNull(context);
        if (sInstance == null) {
            sInstance =
                    new SystemAppFunctionAllowlistReader(
                            context.getPackageManager(),
                            context.getSystemService(AllowlistManager.class),
                            new ServiceConfigImpl(),
                            THREAD_POOL_EXECUTOR,
                            BackgroundThread.getExecutor());
        }
        return sInstance;
    }
}
