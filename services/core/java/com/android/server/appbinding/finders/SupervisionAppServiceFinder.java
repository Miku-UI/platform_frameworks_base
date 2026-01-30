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

import android.app.role.OnRoleHoldersChangedListener;
import android.app.role.RoleManager;
import android.app.supervision.ISupervisionListener;
import android.app.supervision.SupervisionAppService;
import android.app.supervision.SupervisionManagerInternal;
import android.app.supervision.flags.Flags;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.os.BackgroundThread;
import com.android.internal.util.CollectionUtils;
import com.android.server.LocalServices;
import com.android.server.appbinding.AppBindingConstants;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

/** Finds the @{link SupervisionAppService} implementation within the supervision app. */
public class SupervisionAppServiceFinder
        extends AppServiceFinder<SupervisionAppService, ISupervisionListener> {

    private final RoleManager mRoleManager;

    public SupervisionAppServiceFinder(
            Context context,
            BiConsumer<AppServiceFinder, Integer> listener,
            Handler callbackHandler) {
        super(context, listener, callbackHandler);
        mRoleManager = context.getSystemService(RoleManager.class);
    }

    @Override
    protected boolean isEnabled(AppBindingConstants constants, int userId) {
        SupervisionManagerInternal smi =
                LocalServices.getService(SupervisionManagerInternal.class);
        return constants.SUPERVISION_APP_SERVICE_ENABLED && Flags.enableSupervisionAppService()
                && smi.isSupervisionEnabledForUser(userId);
    }

    @NonNull
    @Override
    public String getAppDescription() {
        return "[Supervision app]";
    }

    @Override
    protected Class<SupervisionAppService> getServiceClass() {
        return SupervisionAppService.class;
    }

    @Override
    public ISupervisionListener asInterface(IBinder obj) {
        return ISupervisionListener.Stub.asInterface(obj);
    }

    @Nullable
    @Override
    @Deprecated
    public String getTargetPackage(int userId) {
        final String ret =
                CollectionUtils.firstOrNull(
                        mRoleManager.getRoleHoldersAsUser(
                                RoleManager.ROLE_SYSTEM_SUPERVISION, UserHandle.of(userId)));
        return ret;
    }

    @Override
    public Set<String> getTargetPackages(int userId) {
        final Set<String> ret = new HashSet<>();
        ret.addAll(mRoleManager.getRoleHoldersAsUser(
                RoleManager.ROLE_SYSTEM_SUPERVISION, UserHandle.of(userId)));
        ret.addAll(mRoleManager.getRoleHoldersAsUser(
                RoleManager.ROLE_SUPERVISION, UserHandle.of(userId)));
        return ret;
    }

    @NonNull
    @Override
    protected String getServiceAction() {
        return SupervisionAppService.ACTION_SUPERVISION_APP_SERVICE;
    }

    @NonNull
    @Override
    protected String getServicePermission() {
        return "android.permission.BIND_SUPERVISION_APP_SERVICE";
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
        return constants.SUPERVISION_APP_SERVICE_BIND_FLAGS;
    }

    private final OnRoleHoldersChangedListener mRoleHolderChangedListener =
            (role, user) -> {
                if (RoleManager.ROLE_SYSTEM_SUPERVISION.equals(role)) {
                    mListener.accept(SupervisionAppServiceFinder.this, user.getIdentifier());
                }
            };
}
