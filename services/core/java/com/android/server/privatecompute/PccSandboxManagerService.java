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
package com.android.server.privatecompute;

import static android.app.privatecompute.flags.Flags.enablePccFrameworkSupport;

import android.content.Context;

import com.android.server.LocalServices;
import com.android.server.SystemService;

/**
 * Service that manages interactions with components configured to run in the Private Compute Core
 * (PCC) sandbox.
 */
public class PccSandboxManagerService extends SystemService {
    private static final String NATIVE_SERVICE_NAME = "pcc_sandbox_native";
    private final PccSandboxManagerServiceImpl mServiceImpl;
    private final PccSandboxManagerInternal mInternal;
    private final Context mContext;

    public PccSandboxManagerService(Context context) {
        super(context);
        mContext = context;
        mServiceImpl = new PccSandboxManagerServiceImpl(context);
        mInternal = new PccSandboxManagerInternal(mContext, mServiceImpl);
        // The service needs a reference to the internal class to support shell commands.
        mServiceImpl.setPccSandboxManagerInternal(mInternal);
    }

    @Override
    public void onStart() {
        if (enablePccFrameworkSupport()) {
            publishBinderService(Context.PCC_SANDBOX_SERVICE, mServiceImpl);
            publishBinderService(NATIVE_SERVICE_NAME, mServiceImpl.getNativeBinder());
            LocalServices.addService(PccSandboxManagerInternal.class, mInternal);
        }
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == SystemService.PHASE_SYSTEM_SERVICES_READY) {
            mInternal.awaitPccInitialization();
        }
    }
}

