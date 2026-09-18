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

import static android.os.allowlist.AllowlistManager.ALLOWLIST_ID_TEST;

import android.annotation.NonNull;
import android.content.pm.SignedPackage;
import android.os.Bundle;
import android.os.allowlist.AllowlistManager;
import android.os.allowlist.AllowlistProviderService;
import android.os.allowlist.AllowlistRequest;
import android.os.allowlist.AllowlistResponse;
import android.os.allowlist.SignedPackageMultiMap;
import android.util.ArrayMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An allowlist provider service used in CTS testing the Allowlist system. Only responds to requests
 * for the TEST_ALLOWLIST_ID list, will reject all others. The filtering logic is not provided in
 * this class, it just returns the requested packages or targets. When requesting for installed
 * packages, it returns an empty list.
 */
public class TestAllowlistProviderService extends AllowlistProviderService {

    @Override
    @NonNull
    public AllowlistResponse onQueryAllowlist(@NonNull AllowlistRequest request) {
        if (request.getAllowlistId() != ALLOWLIST_ID_TEST) {
            return new AllowlistResponse(AllowlistManager.RESPONSE_STATUS_ERROR_INVALID_REQUEST,
                    new Bundle());
        }

        int status = AllowlistManager.RESPONSE_STATUS_SUCCESS;
        if (request.getData().containsKey(AllowlistManager.REQUEST_KEY_TEST_RESPONSE_STATUS)) {
            status = request.getData().getInt(AllowlistManager.REQUEST_KEY_TEST_RESPONSE_STATUS);
        }

        Bundle responseData = new Bundle();

        if (request.getData().containsKey(AllowlistManager.REQUEST_KEY_FILTER_TARGETS)
                && request.getData().containsKey(AllowlistManager.REQUEST_KEY_FILTER_PACKAGES)) {
            ArrayList<SignedPackage> allowedAgents = request.getData().getParcelableArrayList(
                    AllowlistManager.REQUEST_KEY_FILTER_PACKAGES, SignedPackage.class);
            ArrayList<SignedPackage> allowedTargets = request.getData().getParcelableArrayList(
                    AllowlistManager.REQUEST_KEY_FILTER_TARGETS, SignedPackage.class);
            Map<SignedPackage, List<SignedPackage>> allowedAgentsAndTargets = new ArrayMap<>();

            if (allowedAgents != null && allowedTargets != null) {
                List<SignedPackage> allowedTargetsNoCert = new ArrayList<>();
                for (SignedPackage target : allowedTargets) {
                    allowedTargetsNoCert.add(new SignedPackage(target.getPackageName(), null));
                }

                for (SignedPackage agent : allowedAgents) {
                    allowedAgentsAndTargets.put(agent, allowedTargetsNoCert);
                }
            }

            responseData.putParcelable(AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGE_MULTI_MAP,
                    new SignedPackageMultiMap(allowedAgentsAndTargets));
        } else if (request.getData().containsKey(AllowlistManager.REQUEST_KEY_FILTER_PACKAGES)) {
            ArrayList<SignedPackage> allowedPackages = request.getData().getParcelableArrayList(
                    AllowlistManager.REQUEST_KEY_FILTER_PACKAGES, SignedPackage.class);
            if (allowedPackages != null) {
                responseData.putParcelableArrayList(AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGES,
                        allowedPackages);
            }
        } else if (request.getData().containsKey(
                AllowlistManager.REQUEST_KEY_INSTALLED_PACKAGES_ONLY)) {
            responseData.putParcelableArrayList(AllowlistManager.RESPONSE_KEY_ALLOWED_PACKAGES,
                    new ArrayList<>());
        }

        return new AllowlistResponse(status, responseData);
    }

    @Override
    public void onNotifyAllowlistChangedListenersForTestProvider(
            @NonNull List<AllowlistRequest> requests) {
        notifyAllowlistChanged(requests);
    }
}
