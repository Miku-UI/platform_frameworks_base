/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.integrity;

import static android.content.integrity.AppIntegrityManager.EXTRA_STATUS;
import static android.content.integrity.AppIntegrityManager.STATUS_SUCCESS;

import android.annotation.BinderThread;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.integrity.IAppIntegrityManager;
import android.content.integrity.Rule;
import android.content.pm.PackageManagerInternal;
import android.content.pm.ParceledListSlice;
import android.util.Slog;

import com.android.server.LocalServices;

import java.util.Collections;
import java.util.List;

/** Implementation of {@link AppIntegrityManagerService}. */
public class AppIntegrityManagerServiceImpl extends IAppIntegrityManager.Stub {

    private static final String TAG = "AppIntegrityManagerServiceImpl";

    private static final String PACKAGE_MIME_TYPE = "application/vnd.android.package-archive";

    public static final boolean DEBUG_INTEGRITY_COMPONENT = false;

    // Access to files inside mRulesDir is protected by mRulesLock;
    private final Context mContext;
    private final PackageManagerInternal mPackageManagerInternal;

    /** Create an instance of {@link AppIntegrityManagerServiceImpl}. */
    public static AppIntegrityManagerServiceImpl create(Context context) {
        return new AppIntegrityManagerServiceImpl(
                context,
                LocalServices.getService(PackageManagerInternal.class));
    }

    private AppIntegrityManagerServiceImpl(
            Context context, PackageManagerInternal packageManagerInternal) {
        mContext = context;
        mPackageManagerInternal = packageManagerInternal;
    }

    @Override
    @BinderThread
    public void updateRuleSet(
            String version, ParceledListSlice<Rule> rules, IntentSender statusReceiver) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_STATUS, STATUS_SUCCESS);
        try {
            statusReceiver.sendIntent(
                mContext,
                /* code= */ 0,
                intent,
                /* onFinished= */ null,
                /* handler= */ null);
        } catch (Exception e) {
            Slog.e(TAG, "Error sending status feedback.", e);
        }
    }

    @Override
    @BinderThread
    public String getCurrentRuleSetVersion() {
        return "";
    }

    @Override
    @BinderThread
    public String getCurrentRuleSetProvider() {
        return "";
    }

    @Override
    public ParceledListSlice<Rule> getCurrentRules() {
        return new ParceledListSlice<>(Collections.emptyList());
    }

    @Override
    public List<String> getWhitelistedRuleProviders() {
        return Collections.emptyList();
    }
}
