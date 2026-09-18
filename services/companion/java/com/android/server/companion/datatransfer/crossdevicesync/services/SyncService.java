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
package com.android.server.companion.datatransfer.crossdevicesync.services;

import android.annotation.Nullable;
import android.content.Context;
import android.os.Binder;
import android.os.Build;
import android.os.ResultReceiver;
import android.util.IndentingPrintWriter;
import android.util.Log;

import com.android.server.companion.datatransfer.crossdevicesync.data.storage.IStorage;
import com.android.server.companion.datatransfer.crossdevicesync.feature.FeatureManager;
import com.android.server.companion.datatransfer.crossdevicesync.metadata.MetadataPublisher;
import com.android.server.companion.datatransfer.crossdevicesync.network.NetworkManager;
import com.android.server.companion.datatransfer.crossdevicesync.notification.NotificationHelper;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

/** The main service hosting cross device sync features. */
public class SyncService {
    private static final String TAG = "SyncService";

    private final Context mContext;

    /** Sets the {@link SyncServiceInjector} to be used. Only used for testing. */
    @Nullable private SyncServiceInjector mInjector;

    private NetworkManager mNetworkManager;
    private MetadataPublisher mMetadataPublisher;
    private NotificationHelper mNotificationHelper;
    private IStorage mGlobalStorage;
    private SyncServiceShellCommand mSyncServiceShellCommand;
    private Map<String, Supplier<FeatureManager>> mFeatureManagerSuppliers;
    private final Set<FeatureManager> mFeatureManagers = new HashSet<>();

    @Nullable private CountDownLatch mDestroyLatch;

    public SyncService(Context context) {
        this(context, null);
    }

    SyncService(Context context, @Nullable SyncServiceInjector injector) {
        mContext = context;
        mInjector = injector;
    }

    private void initialize() {
        mInjector = mInjector != null ? mInjector : new SyncServiceInjectorImpl(mContext);
        mNetworkManager = mInjector.getNetworkManager();
        mMetadataPublisher = mInjector.getMetadataPublisher();
        mNotificationHelper = mInjector.getNotificationHelper();
        mGlobalStorage = mInjector.getGlobalStorage();
        mSyncServiceShellCommand = mInjector.getSyncServiceShellCommand();
        mFeatureManagerSuppliers = mInjector.getFeatureManagers();
    }

    public void onCreate() {
        Log.i(TAG, "onCreate()");
        initialize();
        mNetworkManager.init();
        mMetadataPublisher.init();
        mNotificationHelper.init();
        for (var entry : mFeatureManagerSuppliers.entrySet()) {
            try {
                Log.i(TAG, "Initializing " + entry.getKey() + "...");
                FeatureManager featureManager = entry.getValue().get();
                featureManager.init();
                mFeatureManagers.add(featureManager);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to initialize " + entry.getKey(), t);
            }
        }
    }

    public void onDestroy() {
        Log.i(TAG, "onDestroy()");
        mFeatureManagers.forEach(FeatureManager::destroy);
        mFeatureManagers.clear();
        mNotificationHelper.destroy();
        mMetadataPublisher.destroy();
        mNetworkManager.destroy();
        mGlobalStorage.runInIoThread(mGlobalStorage::close);
        mGlobalStorage.shutdownIoThread();
        if (mDestroyLatch != null) {
            mDestroyLatch.countDown();
            mDestroyLatch = null;
        }
    }

    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (Build.IS_DEBUGGABLE
                && mSyncServiceShellCommand.exec(
                                new Binder(), null, fd, null, args, null, new ResultReceiver(null))
                        == 0) {
            return;
        }
        IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ");
        pw.println("SyncService state:");
        pw.increaseIndent();
        mNetworkManager.dump(pw);
        mMetadataPublisher.dump(pw);
        mFeatureManagers.forEach(f -> f.dump(pw));
        pw.decreaseIndent();
    }
}
