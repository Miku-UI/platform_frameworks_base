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

package com.android.server.wm;

import static android.content.pm.ActivityInfo.FORCE_NON_RESIZE_APP;
import static android.content.pm.ActivityInfo.FORCE_RESIZE_APP;
import static android.content.pm.ActivityInfo.OVERRIDE_ENABLE_VIRTUAL_GAMEPAD;
import static android.internal.perfetto.protos.Windowmanagerservice.ActivityRecordProto.SHOULD_OVERRIDE_FORCE_RESIZE_APP;
import static android.view.WindowManager.PROPERTY_COMPAT_ALLOW_RESIZEABLE_ACTIVITY_OVERRIDES;
import static android.view.WindowManager.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY;
import static android.view.WindowManager.PROPERTY_COMPAT_ALLOW_VIRTUAL_GAMEPAD_OVERRIDE;

import static com.android.server.wm.AppCompatUtils.isChangeEnabled;

import android.annotation.NonNull;
import android.compat.annotation.ChangeId;
import android.compat.annotation.EnabledAfter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.proto.ProtoOutputStream;

import com.android.server.wm.utils.OptPropFactory;

import java.util.function.BooleanSupplier;

/**
 * Encapsulate app compat logic about resizability.
 */
class AppCompatResizeOverrides {

    /**
     * Disable opting out the universal resizability on large screen devices.
     * The property "android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY" will no longer
     * take effect since Android 17 (API level 37).
     */
    @ChangeId
    @EnabledAfter(targetSdkVersion = Build.VERSION_CODES.BAKLAVA)
    static final long DISABLE_OPT_OUT_UNIVERSAL_RESIZABLE_BY_DEFAULT = 447301631L;

    @NonNull
    private final ActivityRecord mActivityRecord;

    @NonNull
    private final OptPropFactory.OptProp mAllowForceResizeOverrideOptProp;

    @NonNull
    private final OptPropFactory.OptProp mAllowVirtualGamepadOverrideOptProp;

    @NonNull
    private final BooleanSupplier mAllowRestrictedResizability;

    private final BooleanSupplier mEnableSizeOverrideForVirtualGamepad;

    AppCompatResizeOverrides(@NonNull ActivityRecord activityRecord,
            @NonNull PackageManager packageManager,
            @NonNull OptPropFactory optPropBuilder) {
        mActivityRecord = activityRecord;
        mAllowForceResizeOverrideOptProp = optPropBuilder.create(
                PROPERTY_COMPAT_ALLOW_RESIZEABLE_ACTIVITY_OVERRIDES);
        mAllowVirtualGamepadOverrideOptProp = optPropBuilder.create(
                PROPERTY_COMPAT_ALLOW_VIRTUAL_GAMEPAD_OVERRIDE);
        mAllowRestrictedResizability = AppCompatUtils.asLazy(() -> {
            if (mActivityRecord.info.applicationInfo.isChangeEnabled(
                    DISABLE_OPT_OUT_UNIVERSAL_RESIZABLE_BY_DEFAULT)) {
                return false;
            }
            // Application level.
            if (allowRestrictedResizability(packageManager, mActivityRecord.info.applicationInfo,
                    true /* hasCheckedDisableOptOut */)) {
                return true;
            }
            // Activity level.
            try {
                return packageManager.getPropertyAsUser(
                        PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY,
                        mActivityRecord.mActivityComponent.getPackageName(),
                        mActivityRecord.mActivityComponent.getClassName(),
                        mActivityRecord.mUserId).getBoolean();
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
        });
        mEnableSizeOverrideForVirtualGamepad = AppCompatUtils.asLazy(() -> {
            try {
                int userOption = activityRecord.mAtmService.getPackageManager()
                        .getVirtualGamepadUserOption(
                                activityRecord.packageName, activityRecord.mUserId);
                if (userOption == PackageManager.VIRTUAL_GAMEPAD_USER_OPTION_OPT_OUT) {
                    return false;
                }
                return mAllowVirtualGamepadOverrideOptProp
                        .shouldEnableWithOptInOverrideAndOptOutProperty(
                                isChangeEnabled(activityRecord, OVERRIDE_ENABLE_VIRTUAL_GAMEPAD));
            } catch (RemoteException e) {
                return false;
            }
        });
    }

    static boolean allowRestrictedResizability(@NonNull PackageManager pm,
            @NonNull ApplicationInfo appInfo, boolean hasCheckedDisableOptOut) {
        if (!hasCheckedDisableOptOut && appInfo.isChangeEnabled(
                DISABLE_OPT_OUT_UNIVERSAL_RESIZABLE_BY_DEFAULT)) {
            return false;
        }
        try {
            return pm.getPropertyAsUser(PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY,
                    appInfo.packageName, null /* className */,
                    UserHandle.getUserId(appInfo.uid)).getBoolean();
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Whether we should apply the force resize per-app override. When this override is applied it
     * forces the packages it is applied to to be resizable. It won't change whether the app can be
     * put into multi-windowing mode, but allow the app to resize without going into size-compat
     * mode when the window container resizes, such as display size change or screen rotation.
     *
     * <p>This method returns {@code true} when the following conditions are met:
     * <ul>
     *     <li>Opt-out component property isn't enabled
     *     <li>Per-app force-resize override or virtual gamepad override is enabled
     * </ul>
     */
    boolean shouldOverrideForceResizeApp() {
        return mAllowForceResizeOverrideOptProp.shouldEnableWithOptInOverrideAndOptOutProperty(
                isChangeEnabled(mActivityRecord, FORCE_RESIZE_APP)
                        || mEnableSizeOverrideForVirtualGamepad.getAsBoolean());
    }

    /**
     * Whether we should apply the force non resize per-app override. When this override is applied
     * it forces the packages it is applied to to be non-resizable.
     *
     * <p>This method returns {@code true} when the following conditions are met:
     * <ul>
     *     <li>Opt-out component property isn't enabled
     *     <li>Per-app override is enabled
     * </ul>
     */
    boolean shouldOverrideForceNonResizeApp() {
        return mAllowForceResizeOverrideOptProp.shouldEnableWithOptInOverrideAndOptOutProperty(
                isChangeEnabled(mActivityRecord, FORCE_NON_RESIZE_APP));
    }

    /** @see android.view.WindowManager#PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY */
    boolean allowRestrictedResizability() {
        return mAllowRestrictedResizability.getAsBoolean();
    }

    public void dumpDebug(@NonNull ProtoOutputStream proto) {
        proto.write(SHOULD_OVERRIDE_FORCE_RESIZE_APP, shouldOverrideForceResizeApp());
    }
}
