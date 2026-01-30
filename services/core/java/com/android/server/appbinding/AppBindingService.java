/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.server.appbinding;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.AppGlobals;
import android.app.supervision.SupervisionManager;
import android.app.supervision.flags.Flags;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.IPackageManager;
import android.content.pm.ServiceInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.provider.Settings.Global;
import android.text.TextUtils;
import android.util.Slog;
import android.util.SparseBooleanArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.BackgroundThread;
import com.android.internal.util.DumpUtils;
import com.android.server.SystemService;
import com.android.server.appbinding.finders.AppServiceFinder;
import com.android.server.appbinding.finders.CarrierMessagingClientServiceFinder;
import com.android.server.appbinding.finders.SupervisionAppServiceFinder;
import com.android.server.utils.Slogf;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * System server that keeps a binding to an app to keep it always running.
 *
 * <p>We only use it for the default SMS app and the Supervision App.
 *
 * Relevant tests:
 * atest CtsAppBindingHostTestCases
 *
 * TODO Maybe handle force-stop differently. Right now we just get "binder died" and re-bind
 * after a timeout. b/116813347
 */
public class AppBindingService extends Binder {
    public static final String TAG = "AppBindingService";

    public static final boolean DEBUG = false; // DO NOT SUBMIT WITH TRUE

    private final Object mLock = new Object();

    private final Injector mInjector;
    private final Context mContext;
    private final Handler mHandler;
    private final IPackageManager mIPackageManager;

    @GuardedBy("mLock")
    private AppBindingConstants mConstants;

    @GuardedBy("mLock")
    private final SparseBooleanArray mRunningUsers = new SparseBooleanArray(2);

    @GuardedBy("mLock")
    private final ArrayList<AppServiceFinder> mApps = new ArrayList<>();

    @GuardedBy("mLock")
    private final ArrayList<AppServiceConnection> mConnections = new ArrayList<>();

    static class Injector {
        public IPackageManager getIPackageManager() {
            return AppGlobals.getPackageManager();
        }

        public String getGlobalSettingString(ContentResolver resolver, String key) {
            return Settings.Global.getString(resolver, key);
        }
    }

    /**
     * {@link SystemService} for this service.
     */
    public static class Lifecycle extends SystemService {
        final AppBindingService mService;

        public Lifecycle(Context context) {
            this(context, new Injector());
        }

        Lifecycle(Context context, Injector injector) {
            super(context);
            mService = new AppBindingService(injector, context);
        }

        @Override
        public void onStart() {
            publishBinderService(Context.APP_BINDING_SERVICE, mService);
            publishLocalService(AppBindingService.class, mService);
        }

        @Override
        public void onBootPhase(int phase) {
            mService.onBootPhase(phase);
        }

        @Override
        public void onUserStarting(@NonNull TargetUser user) {
            mService.onStartUser(user.getUserIdentifier());
        }

        @Override
        public void onUserUnlocking(@NonNull TargetUser user) {
            mService.onUnlockUser(user.getUserIdentifier());
        }

        @Override
        public void onUserStopping(@NonNull TargetUser user) {
            mService.onStopUser(user.getUserIdentifier());
        }
    }

    /**
     * Get the list of services bound to a specific finder class.
     *
     * This method will block until all connections are established or a timeout occurs.
     *
     * <p><b>NOTE: This method should be called from a background thread other than
     * {@link BackgroundThread}, otherwise the {@link PersistentConnection} callbacks will not be
     * delivered until after this method returns.</b></p>
     */
    public List<AppServiceConnection> getAppServiceConnectionsBlocking(
            Class<? extends AppServiceFinder<?, ?>> appServiceFinderClass, int userId) {
        List<AppServiceConnection> serviceConnections = new ArrayList<>();
        synchronized (mLock) {
            for (int i = 0; i < mApps.size(); i++) {
                final AppServiceFinder app = mApps.get(i);
                if (app.getClass() != appServiceFinderClass) {
                    continue;
                }
                serviceConnections.addAll(getConnectionsLocked(userId, app));
            }
        }

        List<AppServiceConnection> boundConnections = new ArrayList<>();
        for (AppServiceConnection conn: serviceConnections) {
            conn.bind();
            if (conn.awaitConnection()) {
                boundConnections.add(conn);
            } else {
                Slogf.w(TAG, "Failed to establish connection for %s, user %d, package %s",
                        conn.getFinder().getAppDescription(), userId, conn.getPackageName());
            }
        }
        return boundConnections;
    }


    public <T> void dispatchAppServiceEvent(
            Class<? extends AppServiceFinder<?, ?>> finderClass,
            int userId, Consumer<AppServiceConnection> action) {
        List<AppServiceConnection> serviceConnections = new ArrayList<>();
        synchronized (mLock) {
            for (int i = 0; i < mApps.size(); i++) {
                final AppServiceFinder app = mApps.get(i);
                if (app.getClass() != finderClass) {
                    continue;
                }
                serviceConnections.addAll(getConnectionsLocked(userId, app));
            }
        }
        for (AppServiceConnection conn: serviceConnections) {
            conn.addCallback(action);
            conn.bind();
        }
    }

    /**
     * Get the connection bound to a specific finder or create one if it does not exist.
     */
    private List<AppServiceConnection> getConnectionsLocked(int userId,
            AppServiceFinder app) {
        Set<String> targetPackages = app.getTargetPackages(userId);
        List<AppServiceConnection> connections = new ArrayList<>();
        for (String targetPackage : targetPackages) {
            AppServiceConnection conn = getOrCreateConnectionLocked(userId, app, targetPackage);
            if (conn != null) {
                connections.add(conn);
            }
        }
        return connections;
    }

    private AppBindingService(Injector injector, Context context) {
        mInjector = injector;
        mContext = context;

        mIPackageManager = injector.getIPackageManager();

        mHandler = BackgroundThread.getHandler();
        mApps.add(new CarrierMessagingClientServiceFinder(context, this::onAppChanged, mHandler));
        if (Flags.enableSupervisionAppService()) {
            mApps.add(new SupervisionAppServiceFinder(context, this::onAppChanged, mHandler));
        }

        // Initialize with the default value to make it non-null.
        mConstants = AppBindingConstants.initializeFromString("");
    }

    private void forAllAppsLocked(Consumer<AppServiceFinder> consumer) {
        for (int i = 0; i < mApps.size(); i++) {
            consumer.accept(mApps.get(i));
        }
    }

    private void onBootPhase(int phase) {
        if (DEBUG) {
            Slog.d(TAG, "onBootPhase: " + phase);
        }
        switch (phase) {
            case SystemService.PHASE_ACTIVITY_MANAGER_READY:
                onPhaseActivityManagerReady();
                break;
            case SystemService.PHASE_THIRD_PARTY_APPS_CAN_START:
                onPhaseThirdPartyAppsCanStart();
                break;
            case SystemService.PHASE_SYSTEM_SERVICES_READY:
                if (Flags.enableSupervisionAppService()) {
                    registerSupervisionListener();
                }
                break;
        }
    }

    private void registerSupervisionListener() {
        SupervisionManager supervisionManager =
                mContext.getSystemService(SupervisionManager.class);
        if (supervisionManager != null) {
            SupervisionManager.SupervisionListener listener =
                    new SupervisionManager.SupervisionListener() {
                        @Override
                        public void onSupervisionDisabled(int userId) {
                            synchronized (mLock) {
                                unbindServicesLocked(userId, null, "supervision disabled");
                            }
                        }
                    };
            supervisionManager.registerSupervisionListener(listener);
        }
    }

    /**
     * Handle boot phase PHASE_ACTIVITY_MANAGER_READY.
     */
    private void onPhaseActivityManagerReady() {
        // RoleManager doesn't tell us about upgrade, so we still need to listen for app upgrades.
        // (app uninstall/disable will be notified by RoleManager.)
        final IntentFilter packageFilter = new IntentFilter();
        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        packageFilter.addDataScheme("package");

        mContext.registerReceiverAsUser(mPackageUserMonitor, UserHandle.ALL,
                packageFilter, null, mHandler);

        final IntentFilter userFilter = new IntentFilter();
        userFilter.addAction(Intent.ACTION_USER_REMOVED);
        mContext.registerReceiverAsUser(mPackageUserMonitor, UserHandle.ALL,
                userFilter, null, mHandler);

        mContext.getContentResolver().registerContentObserver(Settings.Global.getUriFor(
                Settings.Global.APP_BINDING_CONSTANTS), false, mSettingsObserver);

        refreshConstants();
    }

    private final ContentObserver mSettingsObserver = new ContentObserver(null) {
        @Override
        public void onChange(boolean selfChange) {
            refreshConstants();
        }
    };

    private void refreshConstants() {
        final String newSetting = mInjector.getGlobalSettingString(
                mContext.getContentResolver(), Global.APP_BINDING_CONSTANTS);

        synchronized (mLock) {
            if (TextUtils.equals(mConstants.sourceSettings, newSetting)) {
                return;
            }
            Slog.i(TAG, "Updating constants with: " + newSetting);
            mConstants = AppBindingConstants.initializeFromString(newSetting);

            rebindAllLocked("settings update");
        }
    }

    @VisibleForTesting
    final BroadcastReceiver mPackageUserMonitor = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) {
                Slog.d(TAG, "Broadcast received: " + intent);
            }
            final int userId  = intent.getIntExtra(Intent.EXTRA_USER_HANDLE, UserHandle.USER_NULL);
            if (userId == UserHandle.USER_NULL) {
                Slog.w(TAG, "Intent broadcast does not contain user handle: " + intent);
                return;
            }

            final String action = intent.getAction();
            if (action == null) {
                return;
            }

            if (Intent.ACTION_USER_REMOVED.equals(action)) {
                onUserRemoved(userId);
                return;
            }

            final Uri intentUri = intent.getData();
            final String packageName = (intentUri != null) ? intentUri.getSchemeSpecificPart()
                    : null;
            if (packageName == null) {
                Slog.w(TAG, "Intent broadcast does not contain package name: " + intent);
                return;
            }

            final boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);

            switch (action) {
                case Intent.ACTION_PACKAGE_ADDED:
                    if (replacing) {
                        handlePackageAddedReplacing(packageName, userId);
                    }
                    break;
                case Intent.ACTION_PACKAGE_CHANGED:
                    handlePackageAddedReplacing(packageName, userId);
                    break;
                case Intent.ACTION_PACKAGE_REMOVED:
                case Intent.ACTION_PACKAGE_FULLY_REMOVED:
                    if (!replacing) {
                        onAppRemoved(userId, packageName);
                    }
                    break;
            }
        }
    };

    /**
     * Handle boot phase PHASE_THIRD_PARTY_APPS_CAN_START.
     */
    private void onPhaseThirdPartyAppsCanStart() {
        synchronized (mLock) {
            forAllAppsLocked(AppServiceFinder::startMonitoring);
        }
    }

    /** User lifecycle callback. */
    private void onStartUser(int userId) {
        if (DEBUG) {
            Slog.d(TAG, "onStartUser: u" + userId);
        }
        synchronized (mLock) {
            mRunningUsers.append(userId, true);
            bindServicesLocked(userId, null, "user start");
        }
    }

    /** User lifecycle callback. */
    private void onUnlockUser(int userId) {
        if (DEBUG) {
            Slog.d(TAG, "onUnlockUser: u" + userId);
        }
        synchronized (mLock) {
            bindServicesLocked(userId, null, "user unlock");
        }
    }

    /** User lifecycle callback. */
    private void onStopUser(int userId) {
        if (DEBUG) {
            Slog.d(TAG, "onStopUser: u" + userId);
        }
        synchronized (mLock) {
            unbindServicesLocked(userId, null, "user stop");

            mRunningUsers.delete(userId);
        }
    }

    private void onUserRemoved(int userId) {
        if (DEBUG) {
            Slog.d(TAG, "onUserRemoved: u" + userId);
        }
        synchronized (mLock) {
            forAllAppsLocked((app) -> app.onUserRemoved(userId));

            mRunningUsers.delete(userId);
        }
    }

    private void onAppRemoved(int userId, String packageName) {
        if (!Flags.enableSupervisionAppService()) {
            return;
        }

        if (DEBUG) {
            Slogf.d(TAG, "onAppRemoved: u%s %s", userId, packageName);
        }
        synchronized (mLock) {
            final AppServiceFinder finder = findFinderLocked(userId, packageName);
            if (finder != null) {
                bindServicesLocked(userId, finder, "package removed");
            }
        }
    }

    /**
     * Called when a target package changes; e.g. when the user changes the default SMS app.
     */
    private void onAppChanged(AppServiceFinder finder, int userId) {
        if (DEBUG) {
            Slog.d(TAG, "onAppChanged: u" + userId + " " + finder.getAppDescription());
        }

        synchronized (mLock) {
            final String reason = finder.getAppDescription() + " changed";
            unbindServicesLocked(userId, finder, reason);
            bindServicesLocked(userId, finder, reason);
        }
    }

    @Nullable
    private AppServiceFinder findFinderLocked(int userId, @NonNull String packageName) {
        for (int i = 0; i < mApps.size(); i++) {
            final AppServiceFinder app = mApps.get(i);
            if (Flags.enableSupervisionAppService()) {
                if (app.getTargetPackages(userId).contains(packageName)) {
                    return app;
                }
            } else {
                if (packageName.equals(app.getTargetPackage(userId))) {
                    return app;
                }
            }
        }
        return null;
    }

    @Nullable
    private AppServiceConnection findConnectionLock(int userId, @NonNull AppServiceFinder target) {
        for (int i = 0; i < mConnections.size(); i++) {
            final AppServiceConnection conn = mConnections.get(i);
            if ((conn.getUserId() == userId) && (conn.getFinder() == target)) {
                return conn;
            }
        }
        return null;
    }

    @Nullable
    private AppServiceConnection getOrCreateConnectionLocked(
            int userId, @NonNull AppServiceFinder target, String targetPackage) {
        for (int i = 0; i < mConnections.size(); i++) {
            final AppServiceConnection conn = mConnections.get(i);
            if ((conn.getUserId() == userId)
                    && (conn.getFinder() == target)
                    && conn.getPackageName().equals(targetPackage)) {
                return conn;
            }
        }

        final ServiceInfo service =
                target.findService(userId, mIPackageManager, mConstants, targetPackage);
        if (service != null) {
            final AppServiceConnection conn  =
                    new AppServiceConnection(
                            mContext,
                            userId,
                            mConstants,
                            mHandler,
                            target,
                            targetPackage,
                            service.getComponentName());
            mConnections.add(conn);
            return conn;
        }
        return null;
    }

    private void handlePackageAddedReplacing(String packageName, int userId) {
        if (DEBUG) {
            Slog.d(TAG, "handlePackageAddedReplacing: u" + userId + " " + packageName);
        }
        synchronized (mLock) {
            final AppServiceFinder finder = findFinderLocked(userId, packageName);
            if (finder != null) {
                unbindServicesLocked(userId, finder, "package update");
                bindServicesLocked(userId, finder, "package update");
            }
        }
    }

    private void rebindAllLocked(String reason) {
        for (int i = 0; i < mRunningUsers.size(); i++) {
            if (!mRunningUsers.valueAt(i)) {
                continue;
            }
            final int userId = mRunningUsers.keyAt(i);

            unbindServicesLocked(userId, null, reason);
            bindServicesLocked(userId, null, reason);
        }
    }

    private void bindServicesLocked(int userId, @Nullable AppServiceFinder target,
            @NonNull String reasonForLog) {
        for (int i = 0; i < mApps.size(); i++) {
            final AppServiceFinder app = mApps.get(i);
            if (target != null && target != app) {
                continue;
            }
            if (!Flags.enableSupervisionAppService()) {
                bindServicesForFinderLocked(userId, target, reasonForLog, app); // old code
                continue;
            }
            // Disconnect from existing binding.
            unbindServicesLocked(userId, app, reasonForLog);

            final List<ServiceInfo> services =
                    app.findServices(userId, mIPackageManager, mConstants);
            if (services==null || services.isEmpty()) {
                continue;
            }
            for (ServiceInfo service : services) {
                if (DEBUG) {
                    Slog.d(TAG, "bindServicesLocked: u" + userId + " " + app.getAppDescription()
                            + " binding " + service.getComponentName() + " for " + reasonForLog);
                }
                if (service == null) {
                    continue;
                }
                final AppServiceConnection conn =
                        new AppServiceConnection(mContext, userId, mConstants, mHandler,
                                app, service.packageName, service.getComponentName());
                mConnections.add(conn);
                conn.bind();
            }
        }
    }

    private void bindServicesForFinderLocked(int userId, @Nullable AppServiceFinder target,
            @NonNull String reasonForLog, AppServiceFinder app) {
            // Disconnect from existing binding.
            final AppServiceConnection existingConn = findConnectionLock(userId, app);
            if (existingConn != null) {
                unbindServicesLocked(userId, target, reasonForLog);
            }

            final ServiceInfo service = app.findService(userId, mIPackageManager, mConstants);
            if (service == null) {
                return;
            }
            if (DEBUG) {
                Slog.d(TAG, "bindServicesLocked: u" + userId + " " + app.getAppDescription()
                        + " binding " + service.getComponentName() + " for " + reasonForLog);
            }
            final AppServiceConnection conn =
                    new AppServiceConnection(mContext, userId, mConstants, mHandler,
                            app, service.packageName, service.getComponentName());
            mConnections.add(conn);
            conn.bind();
    }

    private void unbindServicesLocked(int userId, @Nullable AppServiceFinder target,
            @NonNull String reasonForLog) {
        for (int i = mConnections.size() - 1; i >= 0; i--) {
            final AppServiceConnection conn = mConnections.get(i);
            if ((conn.getUserId() != userId)
                    || (target != null && conn.getFinder() != target)) {
                continue;
            }
            if (DEBUG) {
                Slog.d(TAG, "unbindServicesLocked: u" + userId
                        + " " + conn.getFinder().getAppDescription()
                        + " unbinding " + conn.getComponentName() + " for " + reasonForLog);
            }
            mConnections.remove(i);
            conn.unbind();
        }
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (!DumpUtils.checkDumpPermission(mContext, TAG, pw)) return;

        if (args.length > 0 && "-s".equals(args[0])) {
            dumpSimple(pw);
            return;
        }

        synchronized (mLock) {
            mConstants.dump("  ", pw);

            pw.println();
            pw.print("  Running users:");
            for (int i = 0; i < mRunningUsers.size(); i++) {
                if (mRunningUsers.valueAt(i)) {
                    pw.print(" ");
                    pw.print(mRunningUsers.keyAt(i));
                }
            }

            pw.println();
            pw.println("  Connections:");
            for (int i = 0; i < mConnections.size(); i++) {
                final AppServiceConnection conn = mConnections.get(i);
                pw.print("    App type: ");
                pw.print(conn.getFinder().getAppDescription());
                pw.println();

                conn.dump("      ", pw);
            }
            if (mConnections.size() == 0) {
                pw.println("    None:");
            }

            pw.println();
            pw.println("  Finders:");
            forAllAppsLocked((app) -> app.dump("    ", pw));
        }
    }

    /**
     * Print simple output for CTS.
     */
    private void dumpSimple(PrintWriter pw) {
        synchronized (mLock) {
            for (int i = 0; i < mConnections.size(); i++) {
                final AppServiceConnection conn = mConnections.get(i);

                pw.print("conn,");
                pw.print(conn.getFinder().getAppDescription());
                pw.print(",");
                pw.print(conn.getUserId());
                pw.print(",");
                pw.print(conn.getComponentName().getPackageName());
                pw.print(",");
                pw.print(conn.getComponentName().getClassName());
                pw.print(",");
                pw.print(conn.isBound() ? "bound" : "not-bound");
                pw.print(",");
                pw.print(conn.isConnected() ? "connected" : "not-connected");
                pw.print(",#con=");
                pw.print(conn.getNumConnected());
                pw.print(",#dis=");
                pw.print(conn.getNumDisconnected());
                pw.print(",#died=");
                pw.print(conn.getNumBindingDied());
                pw.print(",backoff=");
                pw.print(conn.getNextBackoffMs());
                pw.println();
            }
            forAllAppsLocked((app) -> app.dumpSimple(pw));
        }
    }

    AppBindingConstants getConstantsForTest() {
        return mConstants;
    }
}
