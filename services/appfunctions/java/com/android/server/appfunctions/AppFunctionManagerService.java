/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.appfunctions;

import android.annotation.NonNull;
import android.app.UriGrantsManager;
import android.app.appfunctions.AppFunctionAccessServiceInterface;
import android.app.appfunctions.AppFunctionManagerConfiguration;
import android.content.Context;
import android.content.pm.PackageManagerInternal;
import android.os.Environment;

import com.android.internal.os.BackgroundThread;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.uri.UriGrantsManagerInternal;

import java.io.File;

/** Service that manages app functions. */
public class AppFunctionManagerService extends SystemService {
    private static final String AGENT_ALLOWLIST_FILE_NAME = "agent_allowlist.txt";
    private static final String APP_FUNCTIONS_DIR = "appfunctions";
    private final AppFunctionManagerServiceImpl mServiceImpl;

    public AppFunctionManagerService(Context context) {
        super(context);
        mServiceImpl =
                new AppFunctionManagerServiceImpl(
                        context,
                        LocalServices.getService(PackageManagerInternal.class),
                        LocalServices.getService(AppFunctionAccessServiceInterface.class),
                        UriGrantsManager.getService(),
                        LocalServices.getService(UriGrantsManagerInternal.class),
                        new AppFunctionsLoggerWrapper(context),
                        new AppFunctionAgentAllowlistStorage(
                                new File(
                                        new File(
                                                Environment.getDataSystemDirectory(),
                                                APP_FUNCTIONS_DIR),
                                        AGENT_ALLOWLIST_FILE_NAME)),
                        MultiUserAppFunctionAccessHistory.getInstance(context),
                        BackgroundThread.getExecutor());
    }

    @Override
    public void onStart() {
        if (AppFunctionManagerConfiguration.isSupported(getContext())) {
            publishBinderService(Context.APP_FUNCTION_SERVICE, mServiceImpl);
        }
    }

    @Override
    public void onBootPhase(int phase) {
        mServiceImpl.onBootPhase(phase);
    }

    @Override
    public void onUserUnlocked(@NonNull TargetUser user) {
        mServiceImpl.onUserUnlocked(user);
    }

    @Override
    public void onUserStopping(@NonNull TargetUser user) {
        mServiceImpl.onUserStopping(user);
    }

    @Override
    public void onUserStarting(@NonNull TargetUser user) {
        mServiceImpl.onUserStarting(user);
    }
}
