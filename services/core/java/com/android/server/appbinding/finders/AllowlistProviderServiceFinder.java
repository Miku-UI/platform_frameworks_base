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

package com.android.server.appbinding.finders;

import android.Manifest;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;
import android.os.allowlist.AllowlistProviderService;
import android.os.allowlist.IAllowlistProviderService;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.appbinding.AppBindingConstants;

import java.util.Set;
import java.util.function.BiConsumer;

public class AllowlistProviderServiceFinder
        extends AppServiceFinder<AllowlistProviderService, IAllowlistProviderService> {

    public AllowlistProviderServiceFinder(Context context,
            BiConsumer<AppServiceFinder, Integer> listener,
            Handler callbackHandler) {
        super(context, listener, callbackHandler);
    }

    @Override
    protected boolean isEnabled(AppBindingConstants constants, int userId) {
        return android.app.appfunctions.flags.Flags.enableAppFunctionPermissionV2();
    }

    @NonNull
    @Override
    public String getAppDescription() {
        return "[Allowlist Provider app]";
    }

    @Override
    protected Class<AllowlistProviderService> getServiceClass() {
        return AllowlistProviderService.class;
    }

    @Override
    public IAllowlistProviderService asInterface(IBinder obj) {
        return IAllowlistProviderService.Stub.asInterface(obj);
    }

    @Override
    public Set<String> getTargetPackages(int userId) {
        if (userId != UserHandle.USER_SYSTEM) {
            // allowlist provider only exists for the system user
            return Set.of();
        }
        return Set.of(mContext.getResources().getString(
                com.android.internal.R.string.config_allowlistProviderServicePackage));
    }

    @NonNull
    @Override
    protected String getServiceAction() {
        return AllowlistProviderService.ACTION_ALLOWLIST_PROVIDER;
    }

    @NonNull
    @Override
    protected String getServicePermission() {
        return Manifest.permission.BIND_ALLOWLIST_PROVIDER_SERVICE;
    }

    @Override
    public int getBindFlags(AppBindingConstants constants) {
        return 0;
    }
}
