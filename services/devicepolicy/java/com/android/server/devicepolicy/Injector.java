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

package com.android.server.devicepolicy;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest.permission;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.ActivityTaskManager;
import android.app.AlarmManager;
import android.app.AppGlobals;
import android.app.AppOpsManager;
import android.app.IActivityManager;
import android.app.IActivityTaskManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicySafetyChecker;
import android.app.admin.SecurityLog;
import android.app.backup.IBackupManager;
import android.app.compat.CompatChanges;
import android.app.role.RoleManager;
import android.app.supervision.SupervisionManagerInternal;
import android.app.trust.TrustManager;
import android.app.usage.UsageStatsManagerInternal;
import android.content.Context;
import android.content.Intent;
import android.content.pm.CrossProfileApps;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageManagerInternal;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.hardware.usb.UsbManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.IIpConnectivityMetrics;
import android.net.Uri;
import android.net.VpnManager;
import android.net.metrics.IpConnectivityLog;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.StorageManager;
import android.permission.IPermissionManager;
import android.permission.PermissionControllerManager;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.security.KeyChain;
import android.security.KeyChain.KeyChainConnection;
import android.telephony.TelephonyManager;
import android.view.IWindowManager;
import android.view.accessibility.IAccessibilityManager;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.FunctionalUtils.ThrowingRunnable;
import com.android.internal.util.FunctionalUtils.ThrowingSupplier;
import com.android.internal.widget.LockPatternUtils;
import com.android.server.AccessibilityManagerInternal;
import com.android.server.AlarmManagerInternal;
import com.android.server.LocalManagerRegistry;
import com.android.server.LocalServices;
import com.android.server.SystemServerInitThreadPool;
import com.android.server.locksettings.LockSettingsInternal;
import com.android.server.net.NetworkPolicyManagerInternal;
import com.android.server.pdb.PersistentDataBlockManagerInternal;
import com.android.server.pm.PackageManagerLocal;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wm.ActivityTaskManagerInternal;
import java.io.IOException;
import java.util.List;

/** Unit test will subclass it to inject mocks. */
@VisibleForTesting
class Injector {

    private static final String LOG_TAG = DevicePolicyManagerService.LOG_TAG;

    public final Context mContext;

    @Nullable private DevicePolicySafetyChecker mSafetyChecker;

    Injector(Context context) {
        mContext = context;
    }

    public boolean hasFeature() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN);
    }

    Context createContextAsUser(UserHandle user) throws NameNotFoundException {
        final String packageName = mContext.getPackageName();
        return mContext.createPackageContextAsUser(packageName, 0, user);
    }

    Resources getResources() {
        return mContext.getResources();
    }

    UserManager getUserManager() {
        return UserManager.get(mContext);
    }

    UserManagerInternal getUserManagerInternal() {
        return LocalServices.getService(UserManagerInternal.class);
    }

    PackageManagerInternal getPackageManagerInternal() {
        return LocalServices.getService(PackageManagerInternal.class);
    }

    IAccessibilityManager getIAccessibilityManager() {
        final IBinder iBinder = ServiceManager.getService(Context.ACCESSIBILITY_SERVICE);
        return iBinder == null ? null : IAccessibilityManager.Stub.asInterface(iBinder);
    }

    PackageManagerLocal getPackageManagerLocal() {
        return LocalManagerRegistry.getManager(PackageManagerLocal.class);
    }

    ActivityTaskManagerInternal getActivityTaskManagerInternal() {
        return LocalServices.getService(ActivityTaskManagerInternal.class);
    }

    @NonNull
    PermissionControllerManager getPermissionControllerManager(@NonNull UserHandle user) {
        if (user.equals(mContext.getUser())) {
            return mContext.getSystemService(PermissionControllerManager.class);
        } else {
            try {
                return mContext.createPackageContextAsUser(mContext.getPackageName(), 0, user)
                        .getSystemService(PermissionControllerManager.class);
            } catch (NameNotFoundException notPossible) {
                // not possible
                throw new IllegalStateException(notPossible);
            }
        }
    }

    UsageStatsManagerInternal getUsageStatsManagerInternal() {
        return LocalServices.getService(UsageStatsManagerInternal.class);
    }

    NetworkPolicyManagerInternal getNetworkPolicyManagerInternal() {
        return LocalServices.getService(NetworkPolicyManagerInternal.class);
    }

    NotificationManager getNotificationManager() {
        return mContext.getSystemService(NotificationManager.class);
    }

    IIpConnectivityMetrics getIIpConnectivityMetrics() {
        return (IIpConnectivityMetrics)
                IIpConnectivityMetrics.Stub.asInterface(
                        ServiceManager.getService(IpConnectivityLog.SERVICE_NAME));
    }

    PackageManager getPackageManager() {
        return mContext.getPackageManager();
    }

    PackageManager getPackageManager(int userId) {
        try {
            return createContextAsUser(UserHandle.of(userId)).getPackageManager();
        } catch (NameNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    PowerManagerInternal getPowerManagerInternal() {
        return LocalServices.getService(PowerManagerInternal.class);
    }

    TelephonyManager getTelephonyManager() {
        return mContext.getSystemService(TelephonyManager.class);
    }

    RoleManager getRoleManager() {
        return mContext.getSystemService(RoleManager.class);
    }

    TrustManager getTrustManager() {
        return (TrustManager) mContext.getSystemService(Context.TRUST_SERVICE);
    }

    AlarmManager getAlarmManager() {
        return mContext.getSystemService(AlarmManager.class);
    }

    AlarmManagerInternal getAlarmManagerInternal() {
        return LocalServices.getService(AlarmManagerInternal.class);
    }

    ConnectivityManager getConnectivityManager() {
        return mContext.getSystemService(ConnectivityManager.class);
    }

    @Nullable
    VpnManager getVpnManager() {
        return mContext.getSystemService(VpnManager.class);
    }

    LocationManager getLocationManager() {
        return mContext.getSystemService(LocationManager.class);
    }

    IWindowManager getIWindowManager() {
        return IWindowManager.Stub.asInterface(ServiceManager.getService(Context.WINDOW_SERVICE));
    }

    IActivityManager getIActivityManager() {
        return ActivityManager.getService();
    }

    IActivityTaskManager getIActivityTaskManager() {
        return ActivityTaskManager.getService();
    }

    ActivityManagerInternal getActivityManagerInternal() {
        return LocalServices.getService(ActivityManagerInternal.class);
    }

    IPackageManager getIPackageManager() {
        return AppGlobals.getPackageManager();
    }

    IPermissionManager getIPermissionManager() {
        return AppGlobals.getPermissionManager();
    }

    IBackupManager getIBackupManager() {
        return IBackupManager.Stub.asInterface(ServiceManager.getService(Context.BACKUP_SERVICE));
    }

    AccessibilityManagerInternal getAccessibilityManagerInternal() {
        return LocalServices.getService(AccessibilityManagerInternal.class);
    }

    PersistentDataBlockManagerInternal getPersistentDataBlockManagerInternal() {
        return LocalServices.getService(PersistentDataBlockManagerInternal.class);
    }

    AppOpsManager getAppOpsManager() {
        return mContext.getSystemService(AppOpsManager.class);
    }

    LockSettingsInternal getLockSettingsInternal() {
        return LocalServices.getService(LockSettingsInternal.class);
    }

    CrossProfileApps getCrossProfileApps(@UserIdInt int userId) {
        return mContext.createContextAsUser(UserHandle.of(userId), /* flags= */ 0)
                .getSystemService(CrossProfileApps.class);
    }

    boolean hasUserSetupCompleted(DevicePolicyData userData) {
        return userData.mUserSetupComplete;
    }

    boolean isBuildDebuggable() {
        return Build.IS_DEBUGGABLE;
    }

    LockPatternUtils newLockPatternUtils() {
        return new LockPatternUtils(mContext);
    }

    EnterpriseSpecificIdCalculator newEnterpriseSpecificIdCalculator() {
        return new EnterpriseSpecificIdCalculator(mContext);
    }

    boolean storageManagerIsFileBasedEncryptionEnabled() {
        return StorageManager.isFileEncrypted();
    }

    Looper getMyLooper() {
        return Looper.myLooper();
    }

    WifiManager getWifiManager() {
        return mContext.getSystemService(WifiManager.class);
    }

    UsbManager getUsbManager() {
        return mContext.getSystemService(UsbManager.class);
    }

    @SuppressWarnings("ResultOfClearIdentityCallNotStoredInVariable")
    long binderClearCallingIdentity() {
        return Binder.clearCallingIdentity();
    }

    void binderRestoreCallingIdentity(long token) {
        Binder.restoreCallingIdentity(token);
    }

    int binderGetCallingUid() {
        return Binder.getCallingUid();
    }

    int binderGetCallingPid() {
        return Binder.getCallingPid();
    }

    UserHandle binderGetCallingUserHandle() {
        return Binder.getCallingUserHandle();
    }

    boolean binderIsCallingUidMyUid() {
        return Binder.getCallingUid() == Process.myUid();
    }

    void binderWithCleanCallingIdentity(@NonNull ThrowingRunnable action) {
        Binder.withCleanCallingIdentity(action);
    }

    final <T> T binderWithCleanCallingIdentity(@NonNull ThrowingSupplier<T> action) {
        return Binder.withCleanCallingIdentity(action);
    }

    final int userHandleGetCallingUserId() {
        return UserHandle.getUserId(binderGetCallingUid());
    }

    void powerManagerGoToSleep(long time, int reason, int flags) {
        mContext.getSystemService(PowerManager.class).goToSleep(time, reason, flags);
    }

    void powerManagerReboot(String reason) {
        mContext.getSystemService(PowerManager.class).reboot(reason);
    }

    boolean recoverySystemRebootWipeUserData(
            boolean shutdown,
            String reason,
            boolean force,
            boolean wipeEuicc,
            boolean wipeExtRequested,
            boolean wipeResetProtectionData)
            throws IOException {
        return FactoryResetter.newBuilder(mContext)
                .setSafetyChecker(mSafetyChecker)
                .setReason(reason)
                .setShutdown(shutdown)
                .setForce(force)
                .setWipeEuicc(wipeEuicc)
                .setWipeAdoptableStorage(wipeExtRequested)
                .setWipeFactoryResetProtection(wipeResetProtectionData)
                .build()
                .factoryReset();
    }

    boolean systemPropertiesGetBoolean(String key, boolean def) {
        return SystemProperties.getBoolean(key, def);
    }

    long systemPropertiesGetLong(String key, long def) {
        return SystemProperties.getLong(key, def);
    }

    String systemPropertiesGet(String key, String def) {
        return SystemProperties.get(key, def);
    }

    String systemPropertiesGet(String key) {
        return SystemProperties.get(key);
    }

    void systemPropertiesSet(String key, String value) {
        SystemProperties.set(key, value);
    }

    boolean userManagerIsHeadlessSystemUserMode() {
        return UserManager.isHeadlessSystemUserMode();
    }

    List<String> roleManagerGetRoleHoldersAsUser(String role, UserHandle userHandle) {
        return getRoleManager().getRoleHoldersAsUser(role, userHandle);
    }

    @SuppressWarnings("AndroidFrameworkPendingIntentMutability")
    PendingIntent pendingIntentGetActivityAsUser(
            Context context,
            int requestCode,
            @NonNull Intent intent,
            int flags,
            Bundle options,
            UserHandle user) {
        return PendingIntent.getActivityAsUser(context, requestCode, intent, flags, options, user);
    }

    @SuppressWarnings("AndroidFrameworkPendingIntentMutability")
    PendingIntent pendingIntentGetBroadcast(
            Context context, int requestCode, Intent intent, int flags) {
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }

    void registerContentObserver(
            Uri uri, boolean notifyForDescendents, ContentObserver observer, int userHandle) {
        mContext.getContentResolver()
                .registerContentObserver(uri, notifyForDescendents, observer, userHandle);
    }

    int settingsSecureGetIntForUser(String name, int def, int userHandle) {
        return Settings.Secure.getIntForUser(mContext.getContentResolver(), name, def, userHandle);
    }

    String settingsSecureGetStringForUser(String name, int userHandle) {
        return Settings.Secure.getStringForUser(mContext.getContentResolver(), name, userHandle);
    }

    void settingsSecurePutIntForUser(String name, int value, int userHandle) {
        Settings.Secure.putIntForUser(mContext.getContentResolver(), name, value, userHandle);
    }

    void settingsSecurePutStringForUser(String name, String value, int userHandle) {
        Settings.Secure.putStringForUser(mContext.getContentResolver(), name, value, userHandle);
    }

    void settingsGlobalPutStringForUser(String name, String value, int userHandle) {
        Global.putStringForUser(mContext.getContentResolver(), name, value, userHandle);
    }

    int settingsGlobalGetInt(String name, int def) {
        return Global.getInt(mContext.getContentResolver(), name, def);
    }

    @Nullable
    String settingsGlobalGetString(String name) {
        return Global.getString(mContext.getContentResolver(), name);
    }

    void settingsGlobalPutInt(String name, int value) {
        Global.putInt(mContext.getContentResolver(), name, value);
    }

    void settingsGlobalPutString(String name, String value) {
        Global.putString(mContext.getContentResolver(), name, value);
    }

    void settingsSystemPutStringForUser(String name, String value, int userId) {
        Settings.System.putStringForUser(mContext.getContentResolver(), name, value, userId);
    }

    void securityLogSetLoggingEnabledProperty(boolean enabled) {
        SecurityLog.setLoggingEnabledProperty(enabled);
    }

    boolean securityLogGetLoggingEnabledProperty() {
        return SecurityLog.getLoggingEnabledProperty();
    }

    boolean securityLogIsLoggingEnabled() {
        return SecurityLog.isLoggingEnabled();
    }

    KeyChainConnection keyChainBind() throws InterruptedException {
        return KeyChain.bind(mContext);
    }

    KeyChainConnection keyChainBindAsUser(UserHandle user) throws InterruptedException {
        return KeyChain.bindAsUser(mContext, user);
    }

    void postOnSystemServerInitThreadPool(Runnable runnable) {
        SystemServerInitThreadPool.submit(runnable, LOG_TAG);
    }

    public TransferOwnershipMetadataManager newTransferOwnershipMetadataManager() {
        return new TransferOwnershipMetadataManager();
    }

    public void runCryptoSelfTest() {
        CryptoTestHelper.runAndLogSelfTest();
    }

    public long systemCurrentTimeMillis() {
        return System.currentTimeMillis();
    }

    public boolean isChangeEnabled(long changeId, String packageName, int userId) {
        return CompatChanges.isChangeEnabled(changeId, packageName, UserHandle.of(userId));
    }

    void setDevicePolicySafetyChecker(DevicePolicySafetyChecker safetyChecker) {
        mSafetyChecker = safetyChecker;
    }

    DeviceManagementResourcesProvider getDeviceManagementResourcesProvider() {
        return new DeviceManagementResourcesProvider();
    }

    boolean isAdminInstalledCaCertAutoApproved() {
        return false;
    }

    @Nullable
    SupervisionManagerInternal getSupervisionManager() {
        return LocalServices.getService(SupervisionManagerInternal.class);
    }

    boolean hasPermission(String permission, int pid, int uid) {
        return mContext.checkPermission(permission, pid, uid) == PERMISSION_GRANTED;
    }
}
