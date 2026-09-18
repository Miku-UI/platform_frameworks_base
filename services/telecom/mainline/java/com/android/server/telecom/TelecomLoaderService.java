/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.telecom;

import android.app.AppOpsManager;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManagerInternal;
import android.os.Binder;
import android.os.IBinder;
import android.os.PackageTagsList;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.util.IntArray;
import android.util.Slog;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.telephony.SmsApplication;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.pm.UserManagerService;
import com.android.server.pm.permission.LegacyPermissionManagerInternal;

import java.util.ArrayList;
import java.util.List;

/**
 * Starts the telecom component by binding to its ITelecomService implementation. Telecom is setup
 * to run in the system-server process so once it is loaded into memory it will stay running.
 * @hide
 */
public class TelecomLoaderService extends SystemService {
    private static final String TAG = "TelecomLoaderService";

    private static class TelecomMainlineInit {
        static void init(Context context) {
            PackageManagerInternal packageManagerInternal =
                    LocalServices.getService(PackageManagerInternal.class);
            var initializer = new android.telecom.service.TelecomServiceInitializer(
                    packageManagerInternal.getSystemUiServiceComponent().getPackageName());
            android.telecom.TelecomServiceInitializerRepository.setInitializer(initializer);
        }
    }

    private class LoaderServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // Normally, we would listen for death here, but since telecom runs in the same process
            // as this loader (process="system") that's redundant here.
            SmsApplication.getDefaultMmsApplication(mContext, false);
            ServiceManager.addService(Context.TELECOM_SERVICE, service);

            synchronized (mLock) {
                final LegacyPermissionManagerInternal permissionManager =
                        LocalServices.getService(LegacyPermissionManagerInternal.class);
                if (mDefaultSimCallManagerRequests != null) {
                    TelecomManager telecomManager =
                            (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
                    PhoneAccountHandle phoneAccount = telecomManager.getSimCallManager();
                    if (phoneAccount != null) {
                        final int requestCount = mDefaultSimCallManagerRequests.size();
                        final String packageName =
                                phoneAccount.getComponentName().getPackageName();
                        for (int i = requestCount - 1; i >= 0; i--) {
                            final int userId = mDefaultSimCallManagerRequests.get(i);
                            mDefaultSimCallManagerRequests.remove(i);
                            permissionManager
                                    .grantDefaultPermissionsToDefaultSimCallManager(
                                            packageName, userId);
                        }
                    }
                }
                restrictPhoneCallOps();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            connectToTelecom();
        }
    }
    private static final String PACKAGE_NAME = "com.android.server.telecom";

    private static final String SERVICE_ACTION = "android.telecom.TelecomService";

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private IntArray mDefaultSimCallManagerRequests;

    private final Context mContext;

    @GuardedBy("mLock")
    private LoaderServiceConnection mServiceConnection;

    @GuardedBy("mLock")
    private final AppOpsManager mAppOpsManager;

    @GuardedBy("mLock")
    private final IBinder mToken;

    @GuardedBy("mLock")
    private final BroadcastReceiver mUserAddedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_USER_ADDED.equals(intent.getAction())) {
                restrictPhoneCallOps();
            }
        }
    };

    public TelecomLoaderService(Context context) {
        super(context);
        mContext = context;
        registerDefaultAppProviders();
        mAppOpsManager = mContext.getSystemService(AppOpsManager.class);
        mToken = new Binder();
        IntentFilter userAddedFilter = new IntentFilter(Intent.ACTION_USER_ADDED);
        userAddedFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mContext.registerReceiver(mUserAddedReceiver, userAddedFilter);
    }

    @Override
    public void onStart() {
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase == PHASE_THIRD_PARTY_APPS_CAN_START) {
            registerDefaultAppNotifier();
            registerCarrierConfigChangedReceiver();
            // core services will have already been loaded.
            connectToTelecom();
        }
    }

    private void connectToTelecom() {
        synchronized (mLock) {
            if (mServiceConnection != null) {
                // TODO: Is unbinding worth doing or wait for system to rebind?
                mContext.unbindService(mServiceConnection);
                mServiceConnection = null;
            }

            LoaderServiceConnection serviceConnection = new LoaderServiceConnection();
            Intent intent = new Intent(SERVICE_ACTION);
            intent.setPackage(PACKAGE_NAME);
            int flags = Context.BIND_IMPORTANT | Context.BIND_FOREGROUND_SERVICE
                    | Context.BIND_AUTO_CREATE;

            TelecomMainlineInit.init(mContext);
            // Bind to Telecom and register the service
            Slog.i(TAG, "Binding to TelecomService...");
            if (mContext.bindServiceAsUser(intent, serviceConnection, flags, UserHandle.SYSTEM)) {
                Slog.i(TAG, "Bound to TelecomService!");
                mServiceConnection = serviceConnection;
            }
        }
    }

    private void registerDefaultAppProviders() {
        final LegacyPermissionManagerInternal permissionManager =
                LocalServices.getService(LegacyPermissionManagerInternal.class);

        // Set a callback for the permission grant policy to query the default sms app.
        permissionManager.setSmsAppPackagesProvider(userId -> {
            synchronized (mLock) {
                if (mServiceConnection == null) {
                    return null;
                }
            }
            ComponentName smsComponent = SmsApplication.getDefaultSmsApplication(
                    mContext, true);
            if (smsComponent != null) {
                return new String[]{smsComponent.getPackageName()};
            }
            return null;
        });

        // Set a callback for the permission grant policy to query the default dialer app.
        permissionManager.setDialerAppPackagesProvider(userId -> {
            synchronized (mLock) {
                if (mServiceConnection == null) {
                    return null;
                }
            }
            TelecomManager telecomManager = mContext.getSystemService(TelecomManager.class);
            String packageName = telecomManager.getDefaultDialerPackage();
            if (packageName != null) {
                return new String[]{packageName};
            }
            return null;
        });

        // Set a callback for the permission grant policy to query the default sim call manager.
        permissionManager.setSimCallManagerPackagesProvider(userId -> {
            synchronized (mLock) {
                if (mServiceConnection == null) {
                    if (mDefaultSimCallManagerRequests == null) {
                        mDefaultSimCallManagerRequests = new IntArray();
                    }
                    mDefaultSimCallManagerRequests.add(userId);
                    return null;
                }
            }
            SubscriptionManager subscriptionManager =
                    mContext.getSystemService(SubscriptionManager.class);
            if (subscriptionManager == null) {
                return null;
            }
            TelecomManager telecomManager =
                    (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
            List<String> packages = new ArrayList<>();
            int[] subIds = subscriptionManager.getActiveSubscriptionIdList();
            for (int subId : subIds) {
                PhoneAccountHandle phoneAccount =
                        telecomManager.getSimCallManagerForSubscription(subId);
                if (phoneAccount != null) {
                    packages.add(phoneAccount.getComponentName().getPackageName());
                }
            }
            return packages.toArray(new String[] {});
        });
    }

    private void registerDefaultAppNotifier() {
        // Notify the package manager on default app changes
        final RoleManager roleManager = mContext.getSystemService(RoleManager.class);
        roleManager.addOnRoleHoldersChangedListenerAsUser(mContext.getMainExecutor(),
                (roleName, user) -> updateSimCallManagerPermissions(user.getIdentifier()),
                UserHandle.ALL);
    }


    private void registerCarrierConfigChangedReceiver() {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED)) {
                    for (int userId : UserManagerService.getInstance().getUserIds()) {
                        updateSimCallManagerPermissions(userId);
                    }
                }
            }
        };

        mContext.registerReceiverAsUser(receiver, UserHandle.ALL,
            new IntentFilter(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED), null, null);
    }

    private void updateSimCallManagerPermissions(int userId) {
        final LegacyPermissionManagerInternal permissionManager =
                LocalServices.getService(LegacyPermissionManagerInternal.class);
        Context userContext = mContext.createContextAsUser(UserHandle.of(userId), 0);
        TelecomManager telecomManager =
                (TelecomManager) userContext.getSystemService(Context.TELECOM_SERVICE);
        PhoneAccountHandle phoneAccount = telecomManager.getSimCallManager();
        if (phoneAccount != null) {
            Slog.i(TAG, "updating sim call manager permissions for userId:" + userId);
            String packageName = phoneAccount.getComponentName().getPackageName();
            permissionManager.grantDefaultPermissionsToDefaultSimCallManager(packageName,
                    userId);
        }
    }

    private void restrictPhoneCallOps() {
        PackageTagsList packageRestriction = new PackageTagsList.Builder()
                .add(PACKAGE_NAME)
                .build();
        if (mAppOpsManager != null) {
            mAppOpsManager.setUserRestrictionForUser(AppOpsManager.OP_PHONE_CALL_MICROPHONE, true,
                    mToken, packageRestriction, UserHandle.USER_ALL);
            mAppOpsManager.setUserRestrictionForUser(AppOpsManager.OP_PHONE_CALL_CAMERA, true,
                    mToken, packageRestriction, UserHandle.USER_ALL);
        }
    }
}
