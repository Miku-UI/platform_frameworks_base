/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.companion.datatransfer.crossdevicesync.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;

import com.android.server.companion.datatransfer.crossdevicesync.common.Clock;
import com.android.server.companion.datatransfer.crossdevicesync.common.ClockImpl;
import com.android.server.companion.datatransfer.crossdevicesync.common.CompanionDeviceManagerProxy;
import com.android.server.companion.datatransfer.crossdevicesync.common.CompanionDeviceManagerProxyImpl;
import com.android.server.companion.datatransfer.crossdevicesync.common.ContextualModeManagerProxy;
import com.android.server.companion.datatransfer.crossdevicesync.common.ContextualModeManagerProxyImpl;
import com.android.server.companion.datatransfer.crossdevicesync.common.DelayedExecutor;
import com.android.server.companion.datatransfer.crossdevicesync.common.DeviceUtils;
import com.android.server.companion.datatransfer.crossdevicesync.common.DeviceUtilsImpl;
import com.android.server.companion.datatransfer.crossdevicesync.common.FrameworkStatsLogProxy;
import com.android.server.companion.datatransfer.crossdevicesync.common.FrameworkStatsLogProxyImpl;
import com.android.server.companion.datatransfer.crossdevicesync.common.HandlerDelayedExecutorImpl;
import com.android.server.companion.datatransfer.crossdevicesync.data.DefaultTimestampProvider;
import com.android.server.companion.datatransfer.crossdevicesync.data.DeviceNodeIdProviderImpl;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStoreHandle;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStoreHandleFactory;
import com.android.server.companion.datatransfer.crossdevicesync.data.StringConverter;
import com.android.server.companion.datatransfer.crossdevicesync.data.storage.IStorage;
import com.android.server.companion.datatransfer.crossdevicesync.data.storage.SqliteStorage;
import com.android.server.companion.datatransfer.crossdevicesync.feature.FeatureManager;
import com.android.server.companion.datatransfer.crossdevicesync.feature.airplanemode.AirplaneModeController;
import com.android.server.companion.datatransfer.crossdevicesync.feature.airplanemode.AirplaneModeControllerImpl;
import com.android.server.companion.datatransfer.crossdevicesync.feature.airplanemode.AirplaneModeSyncManager;
import com.android.server.companion.datatransfer.crossdevicesync.feature.mode.ContextualModeSyncController;
import com.android.server.companion.datatransfer.crossdevicesync.feature.mode.ContextualModeSyncManager;
import com.android.server.companion.datatransfer.crossdevicesync.metadata.MetadataPublisher;
import com.android.server.companion.datatransfer.crossdevicesync.metadata.MetadataPublisherImpl;
import com.android.server.companion.datatransfer.crossdevicesync.network.NetworkManager;
import com.android.server.companion.datatransfer.crossdevicesync.network.NetworkManagerImpl;
import com.android.server.companion.datatransfer.crossdevicesync.network.advertiser.Advertiser;
import com.android.server.companion.datatransfer.crossdevicesync.network.advertiser.AdvertiserImpl;
import com.android.server.companion.datatransfer.crossdevicesync.network.companion.CompanionActionController;
import com.android.server.companion.datatransfer.crossdevicesync.network.companion.CompanionActionControllerImpl;
import com.android.server.companion.datatransfer.crossdevicesync.network.messenger.Messenger;
import com.android.server.companion.datatransfer.crossdevicesync.network.messenger.MessengerImpl;
import com.android.server.companion.datatransfer.crossdevicesync.network.scanner.Scanner;
import com.android.server.companion.datatransfer.crossdevicesync.network.scanner.ScannerImpl;
import com.android.server.companion.datatransfer.crossdevicesync.notification.NotificationConfig;
import com.android.server.companion.datatransfer.crossdevicesync.notification.NotificationHelper;
import com.android.server.companion.datatransfer.crossdevicesync.notification.NotificationHelperImpl;
import com.android.server.companion.datatransfer.crossdevicesync.notification.NotificationManagerProxyImpl;
import com.android.server.companion.datatransfer.crossdevicesync.user.UserHelper;
import com.android.server.companion.datatransfer.crossdevicesync.user.UserHelperImpl;

import com.google.android.submerge.TimestampProvider;

import java.io.File;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Manual dependency injector for the CrossDeviceSync application. */
public class SyncServiceInjectorImpl implements SyncServiceInjector {
    private static final String DEFAULT_SHARED_PREFS_FILE_NAME = "cross_device_sync_prefs";
    private static final String GLOBAL_DATABASE_NAME = "cross_device_sync_global_db";

    private final IStorage mGlobalStorage;
    private final NetworkManager mNetworkManager;
    private final MetadataPublisher mMetadataPublisher;
    private final NotificationHelper mNotificationHelper;
    private final SyncServiceShellCommand mSyncServiceShellCommand;
    private final Map<String, Supplier<FeatureManager>> mFeatureManagers;

    public SyncServiceInjectorImpl(Context context) {
        Executor mainExecutor = context.getMainExecutor();
        UserHelper userHelper = new UserHelperImpl(context);
        ContextualModeManagerProxy contextualModeManagerProxy =
                new ContextualModeManagerProxyImpl(context);

        File prefsFile =
                new File(
                        Environment.getDataSystemDeDirectory(UserHandle.USER_SYSTEM),
                        DEFAULT_SHARED_PREFS_FILE_NAME);
        SharedPreferences sharedPreferences =
                context.createDeviceProtectedStorageContext()
                        .getSharedPreferences(prefsFile, Context.MODE_PRIVATE);
        DeviceUtils deviceUtils = new DeviceUtilsImpl(context, mainExecutor);

        Clock clock = new ClockImpl();
        FrameworkStatsLogProxy frameworkStatsLogProxy = new FrameworkStatsLogProxyImpl();
        TimestampProvider timestampProvider = new DefaultTimestampProvider();
        mGlobalStorage = new SqliteStorage(context, GLOBAL_DATABASE_NAME);
        StringConverter stringConverter = new StringConverter();

        SharedDataStoreHandle.Factory sharedDataStoreHandleFactory =
                new SharedDataStoreHandleFactory(
                        new DeviceNodeIdProviderImpl(sharedPreferences),
                        mGlobalStorage,
                        frameworkStatsLogProxy,
                        timestampProvider);

        Object networkLock = new Object();
        CompanionDeviceManagerProxy companionDeviceManagerProxy =
                new CompanionDeviceManagerProxyImpl(context);

        DelayedExecutor mainDelayedExecutor =
                new HandlerDelayedExecutorImpl(new Handler(Looper.getMainLooper()));

        CompanionActionController companionActionController =
                new CompanionActionControllerImpl(
                        networkLock,
                        context,
                        companionDeviceManagerProxy,
                        mainDelayedExecutor,
                        clock);
        Messenger messenger =
                new MessengerImpl(
                        networkLock,
                        companionDeviceManagerProxy,
                        mainDelayedExecutor,
                        clock,
                        companionActionController);
        Advertiser advertiser =
                new AdvertiserImpl(networkLock, companionActionController, mainExecutor);
        Scanner scanner = new ScannerImpl(networkLock, companionActionController, mainExecutor);

        mNetworkManager =
                new NetworkManagerImpl(
                        networkLock,
                        mainExecutor,
                        companionDeviceManagerProxy,
                        companionActionController,
                        messenger,
                        advertiser,
                        scanner,
                        clock,
                        context,
                        frameworkStatsLogProxy);

        mMetadataPublisher =
                new MetadataPublisherImpl(
                        sharedPreferences,
                        mNetworkManager,
                        companionDeviceManagerProxy,
                        clock,
                        mainExecutor,
                        userHelper);

        mNotificationHelper =
                new NotificationHelperImpl(
                        sharedPreferences,
                        clock,
                        new NotificationManagerProxyImpl(context),
                        () -> new NotificationConfig(context, deviceUtils),
                        mainExecutor,
                        userHelper);

        mSyncServiceShellCommand = new SyncServiceShellCommand(mNotificationHelper);

        final AirplaneModeController apmController =
                new AirplaneModeControllerImpl(context, mainExecutor);

        final ContextualModeSyncController.Factory contextualModeSyncControllerFactory =
                user ->
                        new ContextualModeSyncController(
                                mNetworkManager,
                                sharedDataStoreHandleFactory,
                                mainExecutor,
                                stringConverter,
                                contextualModeManagerProxy,
                                mMetadataPublisher,
                                mNotificationHelper,
                                user);

        mFeatureManagers =
                Map.of(
                        "ApmSyncManager",
                        () ->
                                new AirplaneModeSyncManager(
                                        context,
                                        mNetworkManager,
                                        apmController,
                                        mMetadataPublisher,
                                        mNotificationHelper,
                                        deviceUtils,
                                        mainExecutor,
                                        stringConverter,
                                        sharedDataStoreHandleFactory),
                        "CtxModeSyncManager",
                        () ->
                                new ContextualModeSyncManager(
                                        contextualModeSyncControllerFactory,
                                        userHelper,
                                        contextualModeManagerProxy,
                                        mainExecutor,
                                        mMetadataPublisher));
    }

    @Override
    public NetworkManager getNetworkManager() {
        return mNetworkManager;
    }

    @Override
    public MetadataPublisher getMetadataPublisher() {
        return mMetadataPublisher;
    }

    @Override
    public NotificationHelper getNotificationHelper() {
        return mNotificationHelper;
    }

    @Override
    public IStorage getGlobalStorage() {
        return mGlobalStorage;
    }

    @Override
    public SyncServiceShellCommand getSyncServiceShellCommand() {
        return mSyncServiceShellCommand;
    }

    @Override
    public Map<String, Supplier<FeatureManager>> getFeatureManagers() {
        return mFeatureManagers;
    }
}
