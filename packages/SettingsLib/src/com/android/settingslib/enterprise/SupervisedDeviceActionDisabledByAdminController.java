/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settingslib.enterprise;

import android.app.admin.EnforcingAdmin;
import android.app.admin.SystemAuthority;
import android.app.supervision.SupervisionManager;
import android.app.supervision.flags.Flags;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.settingslib.RestrictedLockUtils;

final class SupervisedDeviceActionDisabledByAdminController
        extends BaseActionDisabledByAdminController {
    private static final String TAG = "SupervisedDeviceActionDisabledByAdminController";
    private final String mRestriction;

    SupervisedDeviceActionDisabledByAdminController(
            DeviceAdminStringProvider stringProvider, String restriction) {
        super(stringProvider);
        mRestriction = restriction;
    }

    @Override
    public void setupLearnMoreButton(Context context) {

    }

    @Override
    public String getAdminSupportTitle(@Nullable String restriction) {
        if (Flags.enableSupervisionSettingsScreen()) {
            return mStringProvider.getDisabledByParentalControlsTitle();
        }
        return mStringProvider.getDisabledBiometricsParentConsentTitle();
    }

    @Override
    public CharSequence getAdminSupportContentString(Context context,
            @Nullable CharSequence supportMessage) {
        return mStringProvider.getDisabledByParentContent();
    }

    @Nullable
    @Override
    public DialogInterface.OnClickListener getPositiveButtonListener(@NonNull Context context,
            @NonNull RestrictedLockUtils.EnforcedAdmin enforcedAdmin) {
        if (enforcedAdmin.component == null
                || TextUtils.isEmpty(enforcedAdmin.component.getPackageName())) {
            return null;
        }
        return getPositiveButtonListener(context, enforcedAdmin.component.getPackageName());
    }

    @Nullable
    @Override
    public DialogInterface.OnClickListener getPositiveButtonListener(@NonNull Context context,
            @Nullable EnforcingAdmin enforcingAdmin) {
        if (enforcingAdmin == null) {
            return null;
        }
        if (Flags.supervisionManagerApis()
                && enforcingAdmin.getAuthority() instanceof SystemAuthority authority
                && authority
                        .getSystemEntity()
                        .equals(SupervisionManager.SUPERVISION_SYSTEM_ENTITY)) {
            return startBypassRestrictionActivity(context);

        }
        if (TextUtils.isEmpty(enforcingAdmin.getPackageName())) {
            return null;
        }
        return getPositiveButtonListener(context, enforcingAdmin.getPackageName());
    }

    @Nullable
    private DialogInterface.OnClickListener getPositiveButtonListener(@NonNull Context context,
            @NonNull String packageName) {
        final Intent intent = new Intent(Settings.ACTION_MANAGE_SUPERVISOR_RESTRICTED_SETTING)
                .setData(new Uri.Builder()
                        .scheme("policy")
                        .appendPath("user_restrictions")
                        .appendPath(mRestriction)
                        .build())
                .setPackage(packageName);
        ComponentName resolvedSupervisionActivity =
                intent.resolveActivity(context.getPackageManager());
        if (resolvedSupervisionActivity == null) {
            return null;
        }
        return (dialog, which) -> {
            context.startActivity(intent);
        };
    }

    @Nullable
    private DialogInterface.OnClickListener startBypassRestrictionActivity(
            @NonNull Context context) {
        final Intent intent =
                new Intent(Settings.ACTION_BYPASS_SUPERVISION_RESTRICTION)
                        .putExtra(Settings.EXTRA_SUPERVISION_RESTRICTION, mRestriction);
        ComponentName resolvedSupervisionActivity =
                intent.resolveActivity(context.getPackageManager());
        if (resolvedSupervisionActivity == null) {
            return null;
        }
        return (dialog, which) -> {
            context.startActivity(intent);
        };
    }
}
