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

package com.android.server.security.advancedprotection;

import static android.security.advancedprotection.AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G;
import static android.security.advancedprotection.AdvancedProtectionManager.FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES;
import static android.security.advancedprotection.AdvancedProtectionManager.FEATURE_ID_DISALLOW_USB;
import static android.security.advancedprotection.AdvancedProtectionManager.FEATURE_ID_ENABLE_MTE;

import static com.android.internal.util.ConcurrentUtils.DIRECT_EXECUTOR;

import android.Manifest;
import android.annotation.EnforcePermission;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.StatsManager;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.PermissionEnforcer;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.security.advancedprotection.AdvancedProtectionFeature;
import android.security.advancedprotection.AdvancedProtectionFeature.ProvisioningMode;
import android.security.advancedprotection.AdvancedProtectionManager;
import android.security.advancedprotection.AdvancedProtectionManager.FeatureId;
import android.security.advancedprotection.AdvancedProtectionManager.SupportDialogType;
import android.security.advancedprotection.AdvancedProtectionProtoEnums;
import android.security.advancedprotection.IAdvancedProtectionCallback;
import android.security.advancedprotection.IAdvancedProtectionFeatureCallback;
import android.security.advancedprotection.IAdvancedProtectionService;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Pair;
import android.util.Slog;
import android.util.StatsEvent;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.DumpUtils;
import com.android.internal.util.FrameworkStatsLog;
import com.android.server.AccessibilityManagerInternal;
import com.android.server.FgThread;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.accessibility.AccessibilityServiceAdvancedProtectionProvider;
import com.android.server.pm.UserManagerInternal;
import com.android.server.security.advancedprotection.AdvancedProtectionConfigLoader.Injector;
import com.android.server.security.advancedprotection.features.AdvancedProtectionHook;
import com.android.server.security.advancedprotection.features.AdvancedProtectionProvider;
import com.android.server.security.advancedprotection.features.DisallowCellular2GAdvancedProtectionHook;
import com.android.server.security.advancedprotection.features.DisallowInstallUnknownSourcesAdvancedProtectionHook;
import com.android.server.security.advancedprotection.features.MemoryTaggingExtensionHook;
import com.android.server.security.advancedprotection.features.UsbDataAdvancedProtectionHook;
import com.android.server.security.advancedprotection.features.WifiManagerFeatureProvider;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** @hide */
public class AdvancedProtectionService extends IAdvancedProtectionService.Stub {
    private static final String TAG = "AdvancedProtectionService";
    private static final int MODE_CHANGED = 0;
    private static final int CALLBACK_ADDED = 1;
    private static final int FEATURE_CALLBACK_ADDED = 2;
    private static final long MILLIS_PER_HOUR = 60 * 60 * 1000;
    private static final Set<Integer> ADB_PROVISIONING_MODES =
            Set.of(
                    AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_ADB,
                    AdvancedProtectionFeature.PROVISIONING_MODE_DEPROVISIONED_BY_ADB);

    // Features which were launched before the provisioning API was introduced and are thus
    // provisioned by default
    private static final @FeatureId Set<Integer> PROVISIONED_BY_DEFAULT =
            Set.of(
                    AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G,
                    AdvancedProtectionManager.FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES,
                    AdvancedProtectionManager.FEATURE_ID_DISALLOW_USB,
                    AdvancedProtectionManager.FEATURE_ID_ENABLE_MTE);

    private final Context mContext;
    private final Handler mHandler;
    private final AdvancedProtectionStore mStore;
    private final UserManagerInternal mUserManager;
    @Nullable private final AdvancedProtectionConfigLoader mConfigLoader;

    // Features living with the service - their code will be executed when state changes
    private final ArrayList<AdvancedProtectionHook> mHooks = new ArrayList<>();
    // External features - they will be called on state change
    private final ArrayMap<IBinder, IAdvancedProtectionCallback> mCallbacks = new ArrayMap<>();
    // For tracking only - not called on state change
    private final ArrayList<AdvancedProtectionProvider> mProviders = new ArrayList<>();
    private final ArrayMap<IBinder, Pair<IAdvancedProtectionFeatureCallback, int[]>>
            mFeatureCallbacks = new ArrayMap<>();

    // Used to disable logging in tests
    private boolean mEmitLogs = true;

    private AdvancedProtectionService(@NonNull Context context) {
        super(PermissionEnforcer.fromContext(context));
        mContext = context;
        mHandler = new AdvancedProtectionHandler(FgThread.get().getLooper());
        mStore = new AdvancedProtectionStore(mContext);
        mUserManager = LocalServices.getService(UserManagerInternal.class);
        mConfigLoader =
                android.security.Flags.aapmApiV2()
                        ? new AdvancedProtectionConfigLoader(new Injector())
                        : null;
    }

    private void initFeatures(boolean enabled) {
        if (canAddHook(
                FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES, /* featureFlagEnabled= */ true)) {
            try {
                mHooks.add(
                        new DisallowInstallUnknownSourcesAdvancedProtectionHook(mContext, enabled));
            } catch (Exception e) {
                Slog.e(TAG, "Failed to add hook for DisallowInstallUnknownSources", e);
            }
        }
        if (canAddHook(FEATURE_ID_ENABLE_MTE, /* featureFlagEnabled= */ true)) {
            try {
                mHooks.add(new MemoryTaggingExtensionHook(mContext, enabled));
            } catch (Exception e) {
                Slog.e(TAG, "Failed to add hook for MemoryTaggingExtension", e);
            }
        }
        if (canAddHook(FEATURE_ID_DISALLOW_CELLULAR_2G, /* featureFlagEnabled= */ true)) {
            try {
                mHooks.add(new DisallowCellular2GAdvancedProtectionHook(mContext, enabled));
            } catch (Exception e) {
                Slog.e(TAG, "Failed to add hook for DisallowCellular2G", e);
            }
        }

        boolean isUsbDataProtectionSupported =
                SystemProperties.getBoolean(
                        "ro.usb.data_protection.disable_when_locked.supported", false);
        Boolean forcedUsbState = mStore.retrieveFeatureAdbProvisioned(FEATURE_ID_DISALLOW_USB);
        boolean enableUsb = forcedUsbState != null ? forcedUsbState : isUsbDataProtectionSupported;
        if (enableUsb) {
            try {
                mHooks.add(new UsbDataAdvancedProtectionHook(mContext, enabled, this));
            } catch (Exception e) {
                Slog.e(TAG, "Failed to add hook for UsbDataAdvancedProtectionHook", e);
            }
        }

        if (android.security.Flags.aapmFeatureDisableInsecureWifiAutojoinV2()) {
            try {
                mProviders.add(new WifiManagerFeatureProvider());
            } catch (Exception e) {
                Slog.e(TAG, "Failed to initialize WifiManagerFeatureProvider", e);
            }
        }

        if (android.security.Flags.extendAapmToA11yServices()) {
            try {
                mProviders.add(new AccessibilityServiceAdvancedProtectionProvider());
            } catch (Exception e) {
                Slog.e(TAG, "Failed to initialize extendAapmToA11yServices", e);
            }
        }
    }

    private boolean canAddHook(@FeatureId int featureId, boolean featureFlagEnabled) {
        if (!featureFlagEnabled) {
            return false;
        }
        if (android.security.Flags.aapmApiV2() && !isFeatureIdAvailableInConfig(featureId)) {
            // Features can be forced on even if they are not in the config, using adb shell.
            // This is done for testing purposes, and is not a supported public use case.
            Boolean adbProvisioned = mStore.retrieveFeatureAdbProvisioned(featureId);
            return adbProvisioned != null && adbProvisioned;
        }
        return true;
    }

    private boolean isFeatureIdAvailableInConfig(int featureId) {
        return mConfigLoader != null && mConfigLoader.isFeatureIdAvailable(featureId);
    }

    private void initLogging() {
        StatsManager statsManager = mContext.getSystemService(StatsManager.class);
        statsManager.setPullAtomCallback(
                FrameworkStatsLog.ADVANCED_PROTECTION_STATE_INFO,
                null, // use default PullAtomMetadata values
                DIRECT_EXECUTOR,
                new AdvancedProtectionStatePullAtomCallback());
    }

    // Only for tests
    @VisibleForTesting
    AdvancedProtectionService(
            @NonNull Context context,
            @NonNull AdvancedProtectionStore store,
            @NonNull UserManagerInternal userManager,
            @NonNull Looper looper,
            @NonNull PermissionEnforcer permissionEnforcer,
            @Nullable AdvancedProtectionHook hook,
            @Nullable AdvancedProtectionProvider provider,
            @NonNull Injector injector) {
        super(permissionEnforcer);
        mContext = context;
        mStore = store;
        mUserManager = userManager;
        mHandler = new AdvancedProtectionHandler(looper);
        mConfigLoader =
                android.security.Flags.aapmApiV2()
                        ? new AdvancedProtectionConfigLoader(injector)
                        : null;

        if (hook != null) {
            if (canAddHook(hook.getFeatureId(), /* featureFlagEnabled= */ true)) {
                mHooks.add(hook);
            }
        }

        // TODO (b/438957900): Allow changing availability of a subset of features via providers.
        if (provider != null) {
            mProviders.add(provider);
        }

        mEmitLogs = false;
    }

    @Override
    @EnforcePermission(Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE)
    public boolean isAdvancedProtectionEnabled() {
        isAdvancedProtectionEnabled_enforcePermission();
        final long identity = Binder.clearCallingIdentity();
        try {
            return isAdvancedProtectionEnabledInternal();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    // Without permission check
    private boolean isAdvancedProtectionEnabledInternal() {
        return mStore.retrieveAdvancedProtectionModeEnabled();
    }

    @Override
    @EnforcePermission(Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE)
    public void registerAdvancedProtectionCallback(@NonNull IAdvancedProtectionCallback callback)
            throws RemoteException {
        registerAdvancedProtectionCallback_enforcePermission();
        IBinder b = callback.asBinder();
        b.linkToDeath(new DeathRecipient(b, mCallbacks), 0);
        synchronized (mCallbacks) {
            mCallbacks.put(b, callback);
            sendCallbackAdded(isAdvancedProtectionEnabledInternal(), callback);
        }
    }

    @Override
    @EnforcePermission(Manifest.permission.QUERY_ADVANCED_PROTECTION_MODE)
    public void unregisterAdvancedProtectionCallback(
            @NonNull IAdvancedProtectionCallback callback) {
        unregisterAdvancedProtectionCallback_enforcePermission();
        synchronized (mCallbacks) {
            mCallbacks.remove(callback.asBinder());
        }
    }

    @Override
    @EnforcePermission(Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE)
    public void setAdvancedProtectionEnabled(boolean enabled) {
        setAdvancedProtectionEnabled_enforcePermission();
        final UserHandle user = Binder.getCallingUserHandle();
        final long identity = Binder.clearCallingIdentity();
        try {
            enforceAdminUser(user);
            synchronized (mCallbacks) {
                if (enabled != isAdvancedProtectionEnabledInternal()) {
                    mStore.saveAdvancedProtectionModeEnabled(enabled);
                    sendModeChanged(enabled, /* isToggle= */ true);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    @EnforcePermission(Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE)
    public List<AdvancedProtectionFeature> updateAdvancedProtectionFeaturesProvisioning(
            @Nullable @FeatureId int[] featuresToProvision,
            @Nullable @FeatureId int[] featuresToDeprovision) {
        updateAdvancedProtectionFeaturesProvisioning_enforcePermission();
        final UserHandle user = Binder.getCallingUserHandle();
        final long identity = Binder.clearCallingIdentity();
        try {
            enforceAdminUser(user);
            Set<Integer> featureIdsSet = new ArraySet<>();
            if (featuresToProvision != null) {
                for (int featureId : featuresToProvision) {
                    if (!AdvancedProtectionManager.ALL_FEATURE_IDS.contains(featureId)) {
                        throw new IllegalArgumentException(
                                "Feature " + featureId + " is not a valid feature ID.");
                    }
                    featureIdsSet.add(featureId);
                }
            }
            if (featuresToDeprovision != null) {
                for (int featureId : featuresToDeprovision) {
                    if (!AdvancedProtectionManager.ALL_FEATURE_IDS.contains(featureId)) {
                        throw new IllegalArgumentException(
                                "Feature " + featureId + " is not a valid feature ID.");
                    }
                    if (featureIdsSet.contains(featureId)) {
                        throw new IllegalArgumentException(
                                "Feature "
                                        + featureId
                                        + " cannot be both provisioned and deprovisioned");
                    }
                    featureIdsSet.add(featureId);
                }
            }

            if (featuresToProvision != null) {
                for (int featureId : featuresToProvision) {
                    mStore.saveFeatureAdminProvisioned(featureId, true);
                }
            }
            if (featuresToDeprovision != null) {
                for (int featureId : featuresToDeprovision) {
                    mStore.saveFeatureAdminProvisioned(featureId, false);
                }
            }

            List<AdvancedProtectionFeature> updatedFeatures = new ArrayList<>();
            for (int featureId : featureIdsSet) {
                updatedFeatures.add(createAdvancedProtectionFeature(featureId));
            }
            sendModeChanged(isAdvancedProtectionEnabledInternal(), /* isToggle= */ false);
            return updatedFeatures;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    /**
     * Returns the provisioning mode for a given feature.
     *
     * <p>The provisioning mode is determined by the following order of precedence:
     *
     * <ol>
     *   <li>ADB provisioning
     *   <li>Feature admin provisioning
     *   <li>Default provisioning
     * </ol>
     *
     * <p>Note: This method has a side-effect. If the provisioning is determined by the default
     * value, it will be persisted in the store to avoid repeated queries to the store.
     */
    private @ProvisioningMode int getProvisioningMode(@FeatureId int featureId) {
        Boolean isProvisionedByAdb = mStore.retrieveFeatureAdbProvisioned(featureId);
        if (isProvisionedByAdb != null) {
            return isProvisionedByAdb
                    ? AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_ADB
                    : AdvancedProtectionFeature.PROVISIONING_MODE_DEPROVISIONED_BY_ADB;
        }

        Boolean isProvisionedByFeatureAdmin = mStore.retrieveFeatureAdminProvisioned(featureId);
        if (isProvisionedByFeatureAdmin != null) {
            return isProvisionedByFeatureAdmin
                    ? AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_FEATURE_ADMIN
                    : AdvancedProtectionFeature.PROVISIONING_MODE_DEPROVISIONED_BY_FEATURE_ADMIN;
        }

        boolean isProvisionedByDefault = PROVISIONED_BY_DEFAULT.contains(featureId);
        return isProvisionedByDefault
                ? AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_DEFAULT
                : AdvancedProtectionFeature.PROVISIONING_MODE_DEPROVISIONED_BY_DEFAULT;
    }

    public void setAdbProvisioned(int featureId, boolean isProvisioned) {
        mStore.saveFeatureAdbProvisioned(featureId, isProvisioned);
        sendModeChanged(isAdvancedProtectionEnabledInternal(), /* isToggle= */ false);
    }

    public void removeAdbProvisioning(int featureId) {
        mStore.removeFeatureAdbProvisioning(featureId);
        sendModeChanged(isAdvancedProtectionEnabledInternal(), /* isToggle= */ false);
    }

    public Boolean retrieveFeatureAdbProvisioned(int featureId) {
        return mStore.retrieveFeatureAdbProvisioned(featureId);
    }

    public void setUsbDataProtectionEnabled(boolean enabled) {
        final long identity = Binder.clearCallingIdentity();
        try {
            synchronized (mCallbacks) {
                mStore.saveUsbDataProtectionEnabled(enabled);
            }
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    public boolean isUsbDataProtectionEnabled() {
        final long identity = Binder.clearCallingIdentity();
        try {
            return mStore.retrieveUsbDataProtectionEnabled();
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    @EnforcePermission(Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE)
    public void logDialogShown(
            @FeatureId int featureId, @SupportDialogType int type, boolean learnMoreClicked) {
        logDialogShown_enforcePermission();

        if (!mEmitLogs) {
            return;
        }

        int hoursSinceEnabled = hoursSinceLastChange();
        FrameworkStatsLog.write(
                FrameworkStatsLog.ADVANCED_PROTECTION_SUPPORT_DIALOG_DISPLAYED,
                /*feature_id*/ featureIdToLogEnum(featureId),
                /*dialogue_type*/ dialogueTypeToLogEnum(type),
                /*learn_more_clicked*/ learnMoreClicked,
                /*hours_since_last_change*/ hoursSinceEnabled);

        mStore.saveDialogShown(featureId, type, learnMoreClicked, hoursSinceEnabled);
    }

    private int featureIdToLogEnum(@FeatureId int featureId) {
        switch (featureId) {
            case AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G:
                return AdvancedProtectionProtoEnums.FEATURE_ID_DISALLOW_CELLULAR_2G;
            case AdvancedProtectionManager.FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES:
                return AdvancedProtectionProtoEnums.FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES;
            case AdvancedProtectionManager.FEATURE_ID_DISALLOW_USB:
                return AdvancedProtectionProtoEnums.FEATURE_ID_DISALLOW_USB;
            case AdvancedProtectionManager.FEATURE_ID_ENABLE_MTE:
                return AdvancedProtectionProtoEnums.FEATURE_ID_ENABLE_MTE;
            case AdvancedProtectionManager.FEATURE_ID_DISALLOW_INSECURE_WIFI_AUTOJOIN:
                return AdvancedProtectionProtoEnums.FEATURE_ID_DISALLOW_INSECURE_WIFI_AUTOJOIN;
            case AdvancedProtectionManager.FEATURE_ID_RESTRICT_NON_TOOL_A11Y_SERVICES:
                return AdvancedProtectionProtoEnums.FEATURE_ID_RESTRICT_NON_TOOL_A11Y_SERVICES;
            default:
                return AdvancedProtectionProtoEnums.FEATURE_ID_UNKNOWN;
        }
    }

    private int dialogueTypeToLogEnum(@SupportDialogType int type) {
        switch (type) {
            case AdvancedProtectionManager.SUPPORT_DIALOG_TYPE_UNKNOWN:
                return AdvancedProtectionProtoEnums.DIALOGUE_TYPE_UNKNOWN;
            case AdvancedProtectionManager.SUPPORT_DIALOG_TYPE_BLOCKED_INTERACTION:
                return AdvancedProtectionProtoEnums.DIALOGUE_TYPE_BLOCKED_INTERACTION;
            case AdvancedProtectionManager.SUPPORT_DIALOG_TYPE_DISABLED_SETTING:
                return AdvancedProtectionProtoEnums.DIALOGUE_TYPE_DISABLED_SETTING;
            default:
                return AdvancedProtectionProtoEnums.DIALOGUE_TYPE_UNKNOWN;
        }
    }

    private void logAdvancedProtectionEnabled(boolean enabled) {
        if (!mEmitLogs) {
            return;
        }

        Slog.i(TAG, "Advanced protection has been " + (enabled ? "enabled" : "disabled"));
        AccessibilityManagerInternal.AccessibilityFeatureRestrictedCounts a11yFeatureCounts =
                new AccessibilityManagerInternal.AccessibilityFeatureRestrictedCounts(0, 0);
        if (enabled) {
            a11yFeatureCounts =
                    AccessibilityManagerInternal.get()
                            .getA11yFeatureRestrictedCounts(ActivityManager.getCurrentUser());
        }

        FrameworkStatsLog.write(
                FrameworkStatsLog.ADVANCED_PROTECTION_STATE_CHANGED,
                /*enabled*/ enabled,
                /*hours_since_enabled*/ hoursSinceLastChange(),
                /*last_dialog_feature_id*/ featureIdToLogEnum(mStore.retrieveLastDialogFeatureId()),
                /*_type*/ dialogueTypeToLogEnum(mStore.retrieveLastDialogType()),
                /*_learn_more_clicked*/ mStore.retrieveLastDialogLearnMoreClicked(),
                /*_hours_since_enabled*/ mStore.retrieveLastDialogHoursSinceEnabled(),
                /*a11y_num_of_disabled_service*/ a11yFeatureCounts.disabledServices(),
                /*a11y_num_of_removed_shortcut_target*/ a11yFeatureCounts.removedShortcuts());
        mStore.saveEnabledChangeTime(System.currentTimeMillis());
    }

    private int hoursSinceLastChange() {
        int hoursSinceEnabled = -1;
        long lastChangeTimeMillis = mStore.retrieveEnabledChangeTime();
        if (lastChangeTimeMillis != -1) {
            hoursSinceEnabled =
                    (int) ((System.currentTimeMillis() - lastChangeTimeMillis) / MILLIS_PER_HOUR);
        }
        return hoursSinceEnabled;
    }

    private List<AdvancedProtectionFeature> getAdvancedProtectionFeaturesInternal(
            @NonNull @FeatureId int[] featureIds) {
        List<AdvancedProtectionFeature> features = new ArrayList<>();
        for (int i = 0; i < featureIds.length; i++) {
            @FeatureId int featureId = featureIds[i];
            if (!AdvancedProtectionManager.ALL_FEATURE_IDS.contains(featureId)) {
                throw new IllegalArgumentException("Invalid feature ID: " + featureId);
            }
            features.add(createAdvancedProtectionFeature(featureId));
        }
        return features;
    }

    @Override
    @EnforcePermission(Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE)
    public List<AdvancedProtectionFeature> getAdvancedProtectionFeatures(
            @Nullable @FeatureId int[] featureIds) {
        getAdvancedProtectionFeatures_enforcePermission();

        if (featureIds == null) {
            featureIds = getAvailableFeatureIds();
        }
        return getAdvancedProtectionFeaturesInternal(featureIds);
    }

    @Override
    @EnforcePermission(Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE)
    public void registerAdvancedProtectionFeatureCallback(
            @NonNull @FeatureId int[] featureIds,
            @NonNull IAdvancedProtectionFeatureCallback callback)
            throws RemoteException {
        registerAdvancedProtectionFeatureCallback_enforcePermission();

        IBinder b = callback.asBinder();
        b.linkToDeath(new DeathRecipient(b, mFeatureCallbacks), 0);
        synchronized (mFeatureCallbacks) {
            mFeatureCallbacks.put(b, new Pair<>(callback, featureIds));
            sendFeatureCallbackAdded(callback, featureIds);
        }
    }

    @Override
    @EnforcePermission(Manifest.permission.MANAGE_ADVANCED_PROTECTION_MODE)
    public void unregisterAdvancedProtectionFeatureCallback(
            @NonNull IAdvancedProtectionFeatureCallback callback) {
        unregisterAdvancedProtectionFeatureCallback_enforcePermission();

        synchronized (mFeatureCallbacks) {
            mFeatureCallbacks.remove(callback.asBinder());
        }
    }

    private AdvancedProtectionFeature createAdvancedProtectionFeature(@FeatureId int featureId) {
        if (android.security.Flags.aapmApiV2()) {
            @ProvisioningMode int provisioningMode = getProvisioningMode(featureId);
            boolean isFeatureIdAvailableInConfig = isFeatureIdAvailableInConfig(featureId);
            // TODO:Remove this once the feature is fully rolled out.
            if (featureId == AdvancedProtectionManager.FEATURE_ID_DISALLOW_USB) {
                isFeatureIdAvailableInConfig =
                        isFeatureIdAvailableInConfig
                                || SystemProperties.getBoolean(
                                        "ro.usb.data_protection.disable_when_locked.supported",
                                        false);
            }

            boolean isFeatureEnabled =
                    isAdvancedProtectionEnabledInternal()
                            && ((isFeatureIdAvailableInConfig
                                            && provisioningMode
                                                    < AdvancedProtectionFeature
                                                            .PROVISIONING_MODE_DEPROVISIONED_BY_DEFAULT)
                                    || provisioningMode
                                            == AdvancedProtectionFeature
                                                    .PROVISIONING_MODE_PROVISIONED_BY_ADB);
            return new AdvancedProtectionFeature(featureId, isFeatureEnabled, provisioningMode);

        } else {
            return new AdvancedProtectionFeature(featureId);
        }
    }

    private @FeatureId int[] getAvailableFeatureIds() {
        Set<Integer> featureIds = new LinkedHashSet<>();
        for (int i = 0; i < mProviders.size(); i++) {
            // TODO (b/438957900): Remove filtering of providers in getAdvancedProtectionFeatures
            //  once initFeatures filters mProviders.
            if (android.security.Flags.aapmApiV2()) {
                AdvancedProtectionProvider provider = mProviders.get(i);
                List<Integer> providerFeatures = provider.getFeatureIds(mContext);
                for (int j = 0; j < providerFeatures.size(); j++) {
                    @FeatureId int featureId = providerFeatures.get(j);
                    if (isFeatureIdAvailableInConfig(featureId)) {
                        featureIds.add(featureId);
                    }
                }
            } else {
                featureIds.addAll(mProviders.get(i).getFeatureIds(mContext));
            }
        }
        for (int i = 0; i < mHooks.size(); i++) {
            AdvancedProtectionHook hook = mHooks.get(i);
            if (hook.isAvailable()) {
                featureIds.add(hook.getFeatureId());
            }
        }
        if (android.security.Flags.aapmApiV2()) {
            // Add all feature IDs that has specified ADB provisioned or deprovisioned status.
            for (int featureId : AdvancedProtectionManager.ALL_FEATURE_IDS) {
                int provisioningMode = getProvisioningMode(featureId);
                if (provisioningMode
                        == AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_ADB) {
                    featureIds.add(featureId);
                } else if (provisioningMode
                        == AdvancedProtectionFeature.PROVISIONING_MODE_DEPROVISIONED_BY_ADB) {
                    featureIds.remove(featureId);
                }
            }
        }
        return featureIds.stream().mapToInt(Integer::intValue).toArray();
    }

    private boolean getHookAvailabilityOrAdbProvisionedStatus(AdvancedProtectionHook hook) {
        if (android.security.Flags.aapmApiV2()) {
            int provisioningMode = getProvisioningMode(hook.getFeatureId());
            switch (provisioningMode) {
                case AdvancedProtectionFeature.PROVISIONING_MODE_PROVISIONED_BY_ADB:
                    return true;
                case AdvancedProtectionFeature.PROVISIONING_MODE_DEPROVISIONED_BY_ADB:
                    return false;
                default:
            }
        }
        return hook.isAvailable();
    }

    private void enforceAdminUser(UserHandle user) {
        UserInfo info = mUserManager.getUserInfo(user.getIdentifier());
        if (!info.isAdmin() && !treatAsAdminAnyway(user)) {
            throw new SecurityException("Only an admin user can manage advanced protection mode");
        }
    }

    private boolean treatAsAdminAnyway(UserHandle user) {
        // TODO(b/455113492): On most devices, the SYSTEM is already an Admin. But for HSUM devices,
        //  the HSU is no longer an Admin. Currently, that breaks stuff on HSUM, so - for now -
        //  treat the SYSTEM user as if it were an Admin regardless in these otherwise-broken areas.
        //  However, this Admin requirement is likely inappropriate anyway and should be revisited.
        return android.multiuser.Flags.hsuNotAdmin()
                && !android.multiuser.Flags.hsuNotAdminNoExemptions()
                && user.isSystem();
    }

    /**
     * Handles shell commands. This method is used instead of the deprecated {@code onShellCommand}
     * to ensure that the caller is either the shell or root user, enforcing access checks for ADB
     * commands.
     */
    @Override
    public int handleShellCommand(
            @NonNull ParcelFileDescriptor in,
            @NonNull ParcelFileDescriptor out,
            @NonNull ParcelFileDescriptor err,
            @NonNull String[] args) {
        return (new AdvancedProtectionShellCommand(this))
                .exec(
                        this,
                        in.getFileDescriptor(),
                        out.getFileDescriptor(),
                        err.getFileDescriptor(),
                        args);
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (!DumpUtils.checkDumpPermission(mContext, TAG, writer)) return;
        writer.println("AdvancedProtectionService");
        writer.println("  isAdvancedProtectionEnabled: " + isAdvancedProtectionEnabledInternal());
        writer.println("  mHooks.size(): " + mHooks.size());
        writer.println("  mCallbacks.size(): " + mCallbacks.size());
        writer.println("  mProviders.size(): " + mProviders.size());

        writer.println("Hooks: ");
        mHooks.stream()
                .forEach(
                        hook -> {
                            writer.println(
                                    "    "
                                            + hook.getClass().getSimpleName()
                                            + " available: "
                                            + hook.isAvailable()
                                            + " provisioning status: "
                                            + getProvisioningMode(hook.getFeatureId()));
                        });
        writer.println("  Providers: ");
        mProviders.stream()
                .forEach(
                        provider -> {
                            writer.println("    " + provider.getClass().getSimpleName());
                            provider.getFeatureIds(mContext).stream()
                                    .forEach(
                                            feature -> {
                                                writer.println(
                                                        "      "
                                                                + AdvancedProtectionManager
                                                                        .featureIdToString(
                                                                                feature));
                                            });
                        });
        writer.println("  mSharedPreferences: " + mStore.getSharedPreferences().getAll());
        if (android.security.Flags.aapmApiV2()) {
            if (mConfigLoader == null) {
                writer.println("AdvancedProtectionConfigLoader: null");
            } else {
                mConfigLoader.dump(writer);
            }
        }
    }

    void sendModeChanged(boolean enabled, boolean isToggle) {
        if (android.security.Flags.aapmApiV2()) {
            List<AdvancedProtectionFeature> features = getAdvancedProtectionFeatures(null);
            Message.obtain(
                            mHandler,
                            MODE_CHANGED,
                            /*enabled*/ enabled ? 1 : 0,
                            /*isToggle*/ isToggle ? 1 : 0,
                            /*features*/ features)
                    .sendToTarget();
        } else {
            Message.obtain(
                            mHandler,
                            MODE_CHANGED,
                            /*enabled*/ enabled ? 1 : 0,
                            /*isToggle*/ isToggle ? 1 : 0)
                    .sendToTarget();
        }
    }

    void sendCallbackAdded(boolean enabled, IAdvancedProtectionCallback callback) {
        Message.obtain(
                        mHandler,
                        CALLBACK_ADDED,
                        /*enabled*/ enabled ? 1 : 0,
                        /*unused*/ -1,
                        /*callback*/ callback)
                .sendToTarget();
    }

    void sendFeatureCallbackAdded(
            IAdvancedProtectionFeatureCallback callback, @NonNull @FeatureId int[] featureIds) {
        List<AdvancedProtectionFeature> features =
                getAdvancedProtectionFeaturesInternal(featureIds);
        Message.obtain(
                        mHandler,
                        FEATURE_CALLBACK_ADDED,
                        /*arg1*/ 0,
                        /*arg2*/ 0,
                        new Pair<>(callback, features))
                .sendToTarget();
    }

    public static final class Lifecycle extends SystemService {
        private final AdvancedProtectionService mService;

        public Lifecycle(@NonNull Context context) {
            super(context);
            mService = new AdvancedProtectionService(context);
        }

        @Override
        public void onStart() {
            publishBinderService(Context.ADVANCED_PROTECTION_SERVICE, mService);
        }

        @Override
        public void onBootPhase(@BootPhase int phase) {
            if (phase == PHASE_SYSTEM_SERVICES_READY) {
                boolean enabled = mService.isAdvancedProtectionEnabledInternal();
                if (enabled) {
                    Slog.i(TAG, "Advanced protection is enabled");
                }
                mService.initFeatures(enabled);
                mService.initLogging();
            }
        }
    }

    private class AdvancedProtectionHandler extends Handler {
        private AdvancedProtectionHandler(@NonNull Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                // arg1 == enabled
                // arg2 == isToggle
                // obj == features
                case MODE_CHANGED:
                    if (android.security.Flags.aapmApiV2()) {
                        handleModeChanged(
                                /* enabled= */ msg.arg1 == 1,
                                /* features= */ (List<AdvancedProtectionFeature>) msg.obj,
                                /* isToggle= */ msg.arg2 == 1);
                    } else {
                        handleAllCallbacks(
                                /* enabled= */ msg.arg1 == 1, /* isToggle= */ msg.arg2 == 1);
                    }
                    break;
                // arg1 == enabled
                // obj == callback
                case CALLBACK_ADDED:
                    handleSingleCallback(msg.arg1 == 1, (IAdvancedProtectionCallback) msg.obj);
                    break;
                // obj == Pair<IAdvancedProtectionFeatureCallback, List<AdvancedProtectionFeature>>
                case FEATURE_CALLBACK_ADDED:
                    Pair<IAdvancedProtectionFeatureCallback, List<AdvancedProtectionFeature>> data =
                            (Pair<
                                            IAdvancedProtectionFeatureCallback,
                                            List<AdvancedProtectionFeature>>)
                                    msg.obj;
                    handleSingleFeatureCallback(data.first, data.second);
                    break;
            }
        }

        private void handleModeChanged(
                boolean enabled, List<AdvancedProtectionFeature> features, boolean isToggle) {
            // Logging includes counters of features that will be disabled by advanced protection.
            // Log before toggling advanced protection to grab accurate counts before they change.
            if (isToggle) {
                logAdvancedProtectionEnabled(enabled);
            }

            ArrayMap<Integer, AdvancedProtectionFeature> featureMap = new ArrayMap<>();
            for (AdvancedProtectionFeature feature : features) {
                featureMap.put(feature.getId(), feature);
            }

            for (int i = 0; i < mHooks.size(); i++) {
                AdvancedProtectionHook hook = mHooks.get(i);
                try {
                    if (getHookAvailabilityOrAdbProvisionedStatus(hook)) {
                        AdvancedProtectionFeature feature = featureMap.get(hook.getFeatureId());
                        boolean isProvisioned = feature != null && feature.isProvisioned();
                        hook.onAdvancedProtectionChanged(enabled && isProvisioned);
                    }
                } catch (Exception e) {
                    Slog.e(
                            TAG,
                            "Failed to call hook for feature " + hook.getClass().getSimpleName(),
                            e);
                }
            }

            synchronized (mFeatureCallbacks) {
                ArrayList<IBinder> deadBinders = new ArrayList<>();

                for (int i = 0; i < mFeatureCallbacks.size(); i++) {
                    Pair<IAdvancedProtectionFeatureCallback, int[]> pair =
                            mFeatureCallbacks.valueAt(i);
                    IAdvancedProtectionFeatureCallback callback = pair.first;
                    int[] featureIds = pair.second;
                    List<AdvancedProtectionFeature> featuresForCallback = new ArrayList<>();
                    if (featureIds != null) {
                        for (int featureId : featureIds) {
                            if (featureMap.containsKey(featureId)) {
                                AdvancedProtectionFeature feature = featureMap.get(featureId);
                                featuresForCallback.add(feature);
                            }
                        }
                    }
                    try {
                        callback.onFeatureEnabledChanged(featuresForCallback);
                    } catch (RemoteException e) {
                        deadBinders.add(mFeatureCallbacks.keyAt(i));
                    }
                }

                for (IBinder binder : deadBinders) {
                    mFeatureCallbacks.remove(binder);
                }
            }

            if (!isToggle) {
                return;
            }
            // Only notify callbacks if the change is a toggle.
            synchronized (mCallbacks) {
                ArrayList<IAdvancedProtectionCallback> deadObjects = new ArrayList<>();

                for (int i = 0; i < mCallbacks.size(); i++) {
                    IAdvancedProtectionCallback callback = mCallbacks.valueAt(i);
                    try {
                        callback.onAdvancedProtectionChanged(enabled);
                    } catch (RemoteException e) {
                        deadObjects.add(callback);
                    }
                }

                for (int i = 0; i < deadObjects.size(); i++) {
                    mCallbacks.remove(deadObjects.get(i).asBinder());
                }
            }
        }

        private void handleAllCallbacks(boolean enabled, boolean isToggle) {
            if (isToggle) {
                logAdvancedProtectionEnabled(enabled);
            }

            ArrayList<IAdvancedProtectionCallback> deadObjects = new ArrayList<>();

            for (int i = 0; i < mHooks.size(); i++) {
                AdvancedProtectionHook feature = mHooks.get(i);
                try {
                    if (feature.isAvailable()) {
                        feature.onAdvancedProtectionChanged(enabled);
                    }
                } catch (Exception e) {
                    Slog.e(
                            TAG,
                            "Failed to call hook for feature " + feature.getClass().getSimpleName(),
                            e);
                }
            }
            synchronized (mCallbacks) {
                for (int i = 0; i < mCallbacks.size(); i++) {
                    IAdvancedProtectionCallback callback = mCallbacks.valueAt(i);
                    try {
                        callback.onAdvancedProtectionChanged(enabled);
                    } catch (RemoteException e) {
                        deadObjects.add(callback);
                    }
                }

                for (int i = 0; i < deadObjects.size(); i++) {
                    mCallbacks.remove(deadObjects.get(i).asBinder());
                }
            }
        }

        private void handleSingleCallback(boolean enabled, IAdvancedProtectionCallback callback) {
            try {
                callback.onAdvancedProtectionChanged(enabled);
            } catch (RemoteException e) {
                synchronized (mCallbacks) {
                    mCallbacks.remove(callback.asBinder());
                }
            }
        }

        private void handleSingleFeatureCallback(
                IAdvancedProtectionFeatureCallback callback,
                List<AdvancedProtectionFeature> features) {
            try {
                callback.onFeatureEnabledChanged(features);
            } catch (RemoteException e) {
                synchronized (mFeatureCallbacks) {
                    mFeatureCallbacks.remove(callback.asBinder());
                }
            }
        }
    }

    private static final class DeathRecipient<T> implements IBinder.DeathRecipient {
        private final IBinder mBinder;
        private final ArrayMap<IBinder, T> mMap;

        DeathRecipient(IBinder binder, ArrayMap<IBinder, T> map) {
            mBinder = binder;
            mMap = map;
        }

        @Override
        public void binderDied() {
            synchronized (mMap) {
                mMap.remove(mBinder);
            }
        }
    }

    private class AdvancedProtectionStatePullAtomCallback
            implements StatsManager.StatsPullAtomCallback {

        @Override
        public int onPullAtom(int atomTag, List<StatsEvent> data) {
            if (atomTag != FrameworkStatsLog.ADVANCED_PROTECTION_STATE_INFO) {
                return StatsManager.PULL_SKIP;
            }

            data.add(
                    FrameworkStatsLog.buildStatsEvent(
                            FrameworkStatsLog.ADVANCED_PROTECTION_STATE_INFO,
                            /*enabled*/ isAdvancedProtectionEnabledInternal(),
                            /*hours_since_enabled*/ hoursSinceLastChange()));
            return StatsManager.PULL_SUCCESS;
        }
    }
}
