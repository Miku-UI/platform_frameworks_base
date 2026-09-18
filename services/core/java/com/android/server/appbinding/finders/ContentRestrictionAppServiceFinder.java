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
package com.android.server.appbinding.finders;

import android.app.contentrestriction.ContentRestrictionAppService;
import android.app.contentrestriction.IContentRestrictionAppService;
import android.app.role.OnRoleHoldersChangedListener;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.os.BackgroundThread;
import com.android.internal.util.CollectionUtils;
import com.android.server.appbinding.AppBindingConstants;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Finds the {@link ContentRestrictionAppService} implementation within the content restriction app.
 */
public class ContentRestrictionAppServiceFinder
        extends AppServiceFinder<ContentRestrictionAppService, IContentRestrictionAppService> {

    private final RoleManager mRoleManager;

    public ContentRestrictionAppServiceFinder(
            Context context,
            BiConsumer<AppServiceFinder, Integer> listener,
            Handler callbackHandler) {
        super(context, listener, callbackHandler);
        mRoleManager = context.getSystemService(RoleManager.class);
    }

    @Override
    protected boolean isEnabled(AppBindingConstants constants, int userId) {
        return constants.CONTENT_RESTRICTION_APP_SERVICE_ENABLED;
    }

    @NonNull
    @Override
    public String getAppDescription() {
        return "[Content restriction app]";
    }

    @Override
    protected Class<ContentRestrictionAppService> getServiceClass() {
        return ContentRestrictionAppService.class;
    }

    @Override
    public IContentRestrictionAppService asInterface(IBinder obj) {
        return IContentRestrictionAppService.Stub.asInterface(obj);
    }

    @Override
    public Set<String> getTargetPackages(int userId) {
        final Set<String> ret = new HashSet<>();
        ret.addAll(mRoleManager.getRoleHoldersAsUser(
                RoleManager.ROLE_CONTENT_RESTRICTION, UserHandle.of(userId)));
        return ret;
    }

    @NonNull
    @Override
    protected String getServiceAction() {
        return ContentRestrictionAppService.ACTION_CONTENT_RESTRICTION_APP_SERVICE;
    }

    @NonNull
    @Override
    protected String getServicePermission() {
        return "android.permission.BIND_CONTENT_RESTRICTION_SERVICE";
    }

    @Override
    public void startMonitoring() {
        mRoleManager.addOnRoleHoldersChangedListenerAsUser(
                BackgroundThread.getExecutor(), mRoleHolderChangedListener, UserHandle.ALL);
    }

    @Override
    protected String validateService(ServiceInfo service) {
        return null; // Null means accept this service.
    }

    @Override
    public int getBindFlags(AppBindingConstants constants) {
        return constants.CONTENT_RESTRICTION_APP_SERVICE_BIND_FLAGS;
    }

    private final OnRoleHoldersChangedListener mRoleHolderChangedListener =
            (role, user) -> {
                if (RoleManager.ROLE_CONTENT_RESTRICTION.equals(role)) {
                    mListener.accept(ContentRestrictionAppServiceFinder.this, user.getIdentifier());
                }
            };
}
