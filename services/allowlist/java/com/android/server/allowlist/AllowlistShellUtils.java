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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.pm.SignedPackage;
import android.os.Bundle;
import android.os.allowlist.AllowlistManager;
import android.os.allowlist.AllowlistRequest;
import android.os.allowlist.AllowlistResponse;
import android.os.allowlist.SignedPackageMultiMap;
import android.util.ArrayMap;
import android.util.ArraySet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shell command util methods for {@link AllowlistService}.
 */
final class AllowlistShellUtils {

    /**
     * Filter the request if there is a list of allowed packages provided through Shell.
     * @param request {@link AllowlistRequest}
     * @param shellAllowlist A list of {@link SignedPackage} provided through Shell
     * @return A list of {@link SignedPackage}
     */
    @NonNull
    public static ArrayList<SignedPackage> filterShellAllowlist(@NonNull AllowlistRequest request,
            @Nullable List<SignedPackage> shellAllowlist) {
        ArrayList<SignedPackage> allowedPackages = new ArrayList<>();
        if (shellAllowlist == null) {
            return allowedPackages;
        }

        ArrayList<SignedPackage> filterPackages = request.getData().getParcelableArrayList(
                AllowlistManager.REQUEST_KEY_FILTER_PACKAGES, SignedPackage.class);

        if (filterPackages == null) {
            return allowedPackages;
        }

        if (filterPackages.isEmpty()) {
            // Return allowlist without filtering
            allowedPackages = new ArrayList<>(shellAllowlist);
        } else {
            ArraySet<SignedPackage> filterPackageSet = new ArraySet<>(filterPackages);
            for (SignedPackage signedPackage : shellAllowlist) {
                if (Objects.equals(signedPackage.getPackageName(), "*")
                        || filterPackageSet.contains(signedPackage)) {
                    allowedPackages.add(signedPackage);
                }
            }
        }
        return allowedPackages;
    }

    /**
     * Filter the request if there is an allowed package to targets mapping provided through Shell.
     * @param request {@link AllowlistRequest}
     * @param shellAllowlist A {@link SignedPackageMultiMap} provided through Shell
     * @return A {@link SignedPackageMultiMap}
     */
    @NonNull
    public static SignedPackageMultiMap filterShellAllowlist(@NonNull AllowlistRequest request,
            @Nullable SignedPackageMultiMap shellAllowlist) {
        Map<SignedPackage, List<SignedPackage>> filteredMap = new ArrayMap<>();
        if (shellAllowlist == null) {
            return new SignedPackageMultiMap(filteredMap);
        }

        ArrayList<SignedPackage> filterPackages = request.getData().getParcelableArrayList(
                AllowlistManager.REQUEST_KEY_FILTER_PACKAGES, SignedPackage.class);

        ArrayList<SignedPackage> filterTargets = request.getData().getParcelableArrayList(
                AllowlistManager.REQUEST_KEY_FILTER_TARGETS, SignedPackage.class);

        // If the request doesn't contain both FILTER_PACKAGES and FILTER_TARGETS, return empty
        // result.
        if (filterPackages == null || filterTargets == null) {
            return new SignedPackageMultiMap(filteredMap);
        }

        Map<SignedPackage, List<SignedPackage>> allowedAgentsAndTargets = shellAllowlist.getMap();

        // If REQUEST_KEY_FILTER_PACKAGES is empty, return all allowed packages.
        if (filterPackages.isEmpty()) {
            filterPackages = new ArrayList<>(allowedAgentsAndTargets.keySet());
        }

        for (Map.Entry<SignedPackage, List<SignedPackage>> entry :
                allowedAgentsAndTargets.entrySet()) {
            if (Objects.equals(entry.getKey().getPackageName(), "*") || filterPackages.contains(
                    entry.getKey())) {
                ArrayList<SignedPackage> allowedTargets = new ArrayList<>();
                // If REQUEST_KEY_FILTER_TARGETS is empty, return all allowed targets.
                if (filterTargets.isEmpty()) {
                    allowedTargets.addAll(entry.getValue());
                } else {
                    for (SignedPackage signedPackage : entry.getValue()) {
                        if (Objects.equals(signedPackage.getPackageName(), "*")
                                || filterTargets.contains(signedPackage)) {
                            allowedTargets.add(signedPackage);
                        }
                    }
                }
                filteredMap.put(entry.getKey(), allowedTargets);
            }
        }

        return new SignedPackageMultiMap(filteredMap);
    }

    /**
     * Merge the filtered Shell allowlist with the actual response from the provider in an additive
     * way. If the original AllowlistResponse status code is not
     * {@link AllowlistManager#RESPONSE_STATUS_SUCCESS}, it will be overridden to
     * {@link AllowlistManager#RESPONSE_STATUS_SUCCESS}.
     * @param baseResponse {@link AllowlistResponse}
     * @param filteredShellAllowlist A list of {@link SignedPackage}
     * @return A {@link AllowlistResponse}
     */
    @NonNull
    public static AllowlistResponse mergeFilteredAllowlistWithResponse(
            @Nullable AllowlistResponse baseResponse,
            @NonNull ArrayList<SignedPackage> filteredShellAllowlist) {
        Bundle data = baseResponse == null ? new Bundle() : baseResponse.getData();
        ArrayList<SignedPackage> allowedPackages = data.getParcelableArrayList(
                AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGES, SignedPackage.class);
        if (allowedPackages == null) {
            allowedPackages = new ArrayList<>();
        }

        ArraySet<SignedPackage> mergedSet = new ArraySet<>(allowedPackages);
        mergedSet.addAll(filteredShellAllowlist);

        Bundle newData = new Bundle(data);
        newData.putParcelableArrayList(AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGES,
                new ArrayList<>(mergedSet));
        return new AllowlistResponse(AllowlistManager.RESPONSE_STATUS_SUCCESS, newData);
    }

    /**
     * Merge the filtered Shell allowlist with the actual response from the provider in an additive
     * way. If the original AllowlistResponse status code is not
     * {@link AllowlistManager#RESPONSE_STATUS_SUCCESS}, it will be overridden to
     * {@link AllowlistManager#RESPONSE_STATUS_SUCCESS}.
     * @param baseResponse {@link AllowlistResponse}
     * @param filteredShellAllowlist A {@link SignedPackageMultiMap}
     * @return A {@link AllowlistResponse}
     */
    @NonNull
    public static AllowlistResponse mergeFilteredAllowlistWithResponse(
            @Nullable AllowlistResponse baseResponse,
            @NonNull SignedPackageMultiMap filteredShellAllowlist) {
        Bundle data = baseResponse == null ? new Bundle() : baseResponse.getData();
        SignedPackageMultiMap baseMultiMap = data.getParcelable(
                AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGE_MULTI_MAP,
                SignedPackageMultiMap.class);

        Map<SignedPackage, List<SignedPackage>> resultMap = new ArrayMap<>();
        if (baseMultiMap != null) {
            resultMap.putAll(baseMultiMap.getMap());
        }

        for (Map.Entry<SignedPackage, List<SignedPackage>> entry :
                filteredShellAllowlist.getMap().entrySet()) {
            SignedPackage signedPackage = entry.getKey();
            List<SignedPackage> targets = entry.getValue();

            resultMap.compute(signedPackage, (k, existingTargets) -> {
                if (existingTargets == null) {
                    return new ArrayList<>(targets);
                } else {
                    ArraySet<SignedPackage> mergedTargets = new ArraySet<>(existingTargets);
                    mergedTargets.addAll(targets);
                    return new ArrayList<>(mergedTargets);
                }
            });
        }

        Bundle newData = new Bundle(data);
        newData.putParcelable(AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGE_MULTI_MAP,
                new SignedPackageMultiMap(resultMap));
        return new AllowlistResponse(AllowlistManager.RESPONSE_STATUS_SUCCESS, newData);
    }
}
