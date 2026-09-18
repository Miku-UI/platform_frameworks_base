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

package com.android.server.companion.datatransfer.crossdevicesync.feature.airplanemode;

import static android.companion.AssociationRequest.DEVICE_PROFILE_WATCH;
import static android.companion.CompanionDeviceManager.FEATURE_CROSS_DEVICE_SYNC;
import static android.companion.CompanionDeviceManager.FLAG_AIRPLANE_MODE;
import static android.os.UserHandle.USER_ALL;
import static android.provider.Settings.Global.AIRPLANE_MODE_SYNC;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.Trace;
import android.os.UserHandle;
import android.util.IndentingPrintWriter;
import android.util.Log;

import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.FrameworkStatsLog;
import com.android.server.companion.datatransfer.crossdevicesync.common.DebugConfig;
import com.android.server.companion.datatransfer.crossdevicesync.common.DeviceUtils;
import com.android.server.companion.datatransfer.crossdevicesync.data.DocumentSchemaInfo;
import com.android.server.companion.datatransfer.crossdevicesync.data.RecordType;
import com.android.server.companion.datatransfer.crossdevicesync.data.SchemaProvider;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStore;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStore.MutableDocument;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStore.OnRemoteChangeListener;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStoreHandle;
import com.android.server.companion.datatransfer.crossdevicesync.data.StringConverter;
import com.android.server.companion.datatransfer.crossdevicesync.feature.FeatureManager;
import com.android.server.companion.datatransfer.crossdevicesync.metadata.MetadataPublisher;
import com.android.server.companion.datatransfer.crossdevicesync.network.NetworkManager;
import com.android.server.companion.datatransfer.crossdevicesync.notification.NotificationHelper;
import com.android.server.companion.datatransfer.crossdevicesync.notification.NotificationReason;
import com.android.server.connectivity.Flags;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Airplane mode sync feature manager. */
public class AirplaneModeSyncManager
        implements FeatureManager,
                OnRemoteChangeListener,
                AirplaneModeController.Listener,
                DeviceUtils.Listener {
    private static final String TAG = "AirplaneModeSyncManager";
    private static final boolean DEBUG = DebugConfig.DEBUG_FEATURE;

    private static final String DATA_STORE_NAME = "apm_sync_data_store"; // DO NOT CHANGE!
    public static final String DOC_ID = "apm_sync_doc";
    private static final int DOC_VERSION = 1;
    public static final String APM_ENABLED_PATH = "/apm/enabled";
    private static final int APM_ENABLED_VERSION = 1;
    private static final String AIRPLANE_MODE_ON = "1";
    private static final String AIRPLANE_MODE_OFF = "0";
    public static final String NETWORK_ID = "apm_sync_network";

    private static final int STATE_INACTIVE = 0;
    private static final int STATE_OPENING_DATA_STORE = 1;
    private static final int STATE_ACTIVE = 2;
    private static final int STATE_CLOSING_DATA_STORE = 3;

    // Shared metadata key across apps.
    public static final String APM_SYNC_SUPPORTED = "apm_sync_supported";

    public static final List<DocumentSchemaInfo> DOCUMENT_SCHEMA_INFO_LIST =
            Collections.singletonList(
                    DocumentSchemaInfo.builder()
                            .setDocId(DOC_ID)
                            .setVersion(DOC_VERSION)
                            .putPathSchema(
                                    APM_ENABLED_PATH, RecordType.TYPE_REGISTER, APM_ENABLED_VERSION)
                            .build());

    private final NetworkManager mNetworkManager;
    private final AirplaneModeController mAirplaneModeController;
    private final MetadataPublisher mMetadataPublisher;
    private final NotificationHelper mNotificationHelper;
    private final DeviceUtils mDeviceUtils;
    private final Executor mMainExecutor;
    private final SharedDataStoreHandle<String> mSharedDataStoreHandle;
    private final boolean mIsApmSyncSupported;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    @Nullable
    private SharedDataStore<String> mDataStore;

    @GuardedBy("mLock")
    @Nullable
    private NetworkManager.Network mNetwork;

    @GuardedBy("mLock")
    private int mState = STATE_INACTIVE;

    @GuardedBy("mLock")
    private boolean mStarted = false;

    @GuardedBy("mLock")
    private boolean mAirplaneModeState = false;

    @GuardedBy("mLock")
    private boolean mIsKidsWatch = false;

    private final AtomicBoolean mSyncEnabled = new AtomicBoolean(false);

    public AirplaneModeSyncManager(
            Context context,
            NetworkManager networkManager,
            AirplaneModeController airplaneModeController,
            MetadataPublisher metadataPublisher,
            NotificationHelper notificationHelper,
            DeviceUtils deviceUtils,
            Executor mainExecutor,
            StringConverter stringConverter,
            SharedDataStoreHandle.Factory sharedDataStoreHandleFactory) {
        mNetworkManager = networkManager;
        mAirplaneModeController = airplaneModeController;
        mMetadataPublisher = metadataPublisher;
        mNotificationHelper = notificationHelper;
        mDeviceUtils = deviceUtils;
        mMainExecutor = mainExecutor;
        mSharedDataStoreHandle =
                sharedDataStoreHandleFactory.create(
                        DATA_STORE_NAME, stringConverter, getSchemaProvider());
        mIsApmSyncSupported =
                Flags.syncAirplaneModeWithWatches()
                        && context.getResources().getBoolean(R.bool.config_supportAirplaneModeSync);
    }

    @Override
    public void init() {
        if (!mIsApmSyncSupported) {
            return;
        }
        Trace.beginSection("AirplaneModeSyncManager.init");
        try {
            boolean apmEnabled = mAirplaneModeController.isAirplaneModeEnabled();
            boolean syncEnabled = mAirplaneModeController.isAirplaneModeSyncEnabled();
            boolean isKidsWatch = mDeviceUtils.isKidsWatch();
            logD(
                    "Init: apmEnabled="
                            + apmEnabled
                            + ", syncEnabled="
                            + syncEnabled
                            + ", isKidsWatch="
                            + isKidsWatch);

            synchronized (mLock) {
                mAirplaneModeState = apmEnabled;
                mIsKidsWatch = isKidsWatch;
                mStarted = true;
                mMetadataPublisher.putBooleanMetaData(USER_ALL, APM_SYNC_SUPPORTED, !mIsKidsWatch);
                mAirplaneModeController.registerAirplaneModeChangedListener(this);
                mDeviceUtils.registerKidsWatchChangeListener(this);
                onAirplaneModeSyncEnabledStateChanged(syncEnabled);
                maybeUpdateDataStoreLocked();
            }
        } finally {
            Trace.endSection();
        }
    }

    @Override
    public void onRemoteChange(@NonNull List<String> paths) {
        Trace.beginSection("AirplaneModeSyncManager.onRemoteChange");
        try {
            synchronized (mLock) {
                SharedDataStore<String> localDataStore = mDataStore;
                if (mState != STATE_ACTIVE
                        || !paths.contains(APM_ENABLED_PATH)
                        || localDataStore == null) {
                    return;
                }
                logD("Remote APM changed");
                transactLocked(
                        localDataStore,
                        "Get remote state",
                        doc -> {
                            synchronized (mLock) {
                                if (mState != STATE_ACTIVE) {
                                    return;
                                }
                                var record = doc.getRecord(APM_ENABLED_PATH);
                                if (record == null) {
                                    return;
                                }
                                boolean apmEnabled = AIRPLANE_MODE_ON.equals(record.get());
                                if (mAirplaneModeState == apmEnabled) {
                                    return;
                                }
                                logD(
                                        "Remote APM state = "
                                                + apmEnabled
                                                + ". Updating local setting");
                                mAirplaneModeState = apmEnabled;
                                mAirplaneModeController.updateAirplaneModeState(apmEnabled);
                                if (apmEnabled) {
                                    mNotificationHelper.maybeShowNotification(
                                            NotificationReason.AIRPLANE_MODE_SYNCED,
                                            UserHandle.ALL);
                                }
                            }
                        });
            }
        } finally {
            Trace.endSection();
        }
    }

    @Override
    public void onAirplaneModeChanged(boolean enabled) {
        logD("Local APM state changed to " + enabled);
        Trace.beginSection("AirplaneModeSyncManager.onAirplaneModeChanged");
        try {
            synchronized (mLock) {
                SharedDataStore<String> localDataStore = mDataStore;
                if (mAirplaneModeState != enabled) {
                    mAirplaneModeState = enabled;
                    if (mState == STATE_ACTIVE && localDataStore != null) {
                        logD("Setting APM state to SharedDataStore");
                        transactLocked(
                                localDataStore,
                                "Update local state",
                                doc -> doc.putData(APM_ENABLED_PATH, toAirplaneModeState(enabled)));
                    }
                }
            }
        } finally {
            Trace.endSection();
        }
    }

    @Override
    public void onKidsWatchChanged(boolean isKidsWatch) {
        Trace.beginSection("AirplaneModeSyncManager.onKidsWatchChanged");
        try {
            synchronized (mLock) {
                mIsKidsWatch = isKidsWatch;
                mMetadataPublisher.putBooleanMetaData(USER_ALL, APM_SYNC_SUPPORTED, !mIsKidsWatch);
                maybeUpdateDataStoreLocked();
            }
        } finally {
            Trace.endSection();
        }
    }

    private void transactLocked(
            SharedDataStore<String> dataStore,
            String name,
            Consumer<MutableDocument<String>> transaction) {
        var ignored =
                dataStore
                        .transact(
                                DOC_ID,
                                document -> {
                                    transaction.accept(document);
                                    return null;
                                })
                        .whenCompleteAsync(
                                (result, t) -> {
                                    if (t != null) {
                                        Log.e(TAG, "Transaction[" + name + "] failed", t);
                                    } else {
                                        logD("Transaction[" + name + "] successful");
                                    }
                                },
                                mMainExecutor);
    }

    @Override
    public void onAirplaneModeSyncEnabledStateChanged(boolean enabled) {
        logD("Local APM sync enabled state changed to " + enabled);
        Trace.beginSection("AirplaneModeSyncManager.onAirplaneModeSyncEnabledStateChanged");
        try {
            mMetadataPublisher.putBooleanMetaData(UserHandle.USER_ALL, AIRPLANE_MODE_SYNC, enabled);
            synchronized (mLock) {
                if (mStarted && mSyncEnabled.compareAndSet(!enabled, enabled)) {
                    mNetworkManager.invalidateNetworks();
                }
            }
        } finally {
            Trace.endSection();
        }
    }

    @GuardedBy("mLock")
    private void maybeUpdateDataStoreLocked() {
        if (!mStarted || mIsKidsWatch) {
            maybeCloseDataStoreLocked();
        } else {
            maybeOpenDataStoreLocked();
        }
    }

    @GuardedBy("mLock")
    private void maybeOpenDataStoreLocked() {
        if (mState != STATE_INACTIVE) {
            logD("maybeEnableSync: ignored. Current state is " + stateToString(mState));
            return;
        }

        mState = STATE_OPENING_DATA_STORE;
        NetworkManager.Network network =
                mNetworkManager.createNetwork(
                        NETWORK_ID,
                        FrameworkStatsLog
                                .CROSS_DEVICE_SYNC_EVENT__FEATURE__FEATURE_AIRPLANE_MODE_SYNC,
                        remoteDevice -> {
                            // Check if this device is a watch, or the remote device is a watch.
                            if (!mDeviceUtils.isWatch()
                                    && !DEVICE_PROFILE_WATCH.equals(
                                            remoteDevice.getDeviceProfile())) {
                                return false;
                            }
                            var associationInfo = remoteDevice.getAssociationInfoCache();
                            var metadata = associationInfo.getMetadata(FEATURE_CROSS_DEVICE_SYNC);
                            // Whether the remote device supports sync and has the sync
                            // toggle enabled.
                            return metadata.getBoolean(APM_SYNC_SUPPORTED)
                                    && metadata.getBoolean(AIRPLANE_MODE_SYNC)
                                    && (associationInfo.getSystemDataSyncFlags()
                                                    & FLAG_AIRPLANE_MODE)
                                            != 0
                                    // Whether the sync toggle is enabled in this device
                                    && mSyncEnabled.get();
                        });
        mNetwork = network;

        var ignored =
                mSharedDataStoreHandle
                        .openDataStore(network)
                        .whenCompleteAsync(
                                (dataStore, t) -> {
                                    if (t != null) {
                                        onDataStoreFailedToInitialize(t);
                                    } else {
                                        onDataStoreInitialized(dataStore);
                                    }
                                },
                                mMainExecutor);
    }

    private void onDataStoreInitialized(SharedDataStore<String> dataStore) {
        synchronized (mLock) {
            if (mState != STATE_OPENING_DATA_STORE) {
                return;
            }
            logD("APM Data Store init success");
            mState = STATE_ACTIVE;
            mDataStore = dataStore;
            dataStore.registerOnRemoteChangeListener(mMainExecutor, this);
            maybeUpdateDataStoreLocked();
        }
    }

    private void onDataStoreFailedToInitialize(Throwable t) {
        synchronized (mLock) {
            if (mState != STATE_OPENING_DATA_STORE) {
                return;
            }
            Log.e(TAG, "APM Data Store init error", t);
            mState = STATE_INACTIVE;
            if (mNetwork != null) {
                mNetwork.destroy();
                mNetwork = null;
            }
        }
    }

    @GuardedBy("mLock")
    private void maybeCloseDataStoreLocked() {
        if (mState != STATE_ACTIVE) {
            logD("maybeDisableSync: ignored. Current state is " + stateToString(mState));
            return;
        }
        mState = STATE_CLOSING_DATA_STORE;
        if (mDataStore != null) {
            mDataStore.unregisterOnRemoteChangeListener(this);
            var ignored =
                    mDataStore
                            .close()
                            .whenCompleteAsync((unused, t) -> onDataStoreClosed(), mMainExecutor);
        }
    }

    private void onDataStoreClosed() {
        synchronized (mLock) {
            if (mState != STATE_CLOSING_DATA_STORE) {
                return;
            }
            mState = STATE_INACTIVE;
            mDataStore = null;
            if (mNetwork != null) {
                mNetwork.destroy();
                mNetwork = null;
            }
            maybeUpdateDataStoreLocked();
        }
    }

    @Override
    public void destroy() {
        if (!mIsApmSyncSupported) {
            return;
        }
        Trace.beginSection("AirplaneModeSyncManager.destroy");
        try {
            synchronized (mLock) {
                if (!mStarted) {
                    return;
                }
                mStarted = false;
                mState = STATE_INACTIVE;
                mAirplaneModeController.unregisterAirplaneModeChangedListener(this);
                mDeviceUtils.unregisterKidsWatchChangeListener(this);
                if (mDataStore != null) {
                    mDataStore.unregisterOnRemoteChangeListener(this);
                }
                mDataStore = null;
                if (mNetwork != null) {
                    mNetwork.destroy();
                }
                mNetwork = null;
                mSharedDataStoreHandle.destroy();
            }
        } finally {
            Trace.endSection();
        }
    }

    private SchemaProvider<String> getSchemaProvider() {
        return new SchemaProvider<>() {
            @NonNull
            @Override
            public List<DocumentSchemaInfo> getAllDocumentSchema() {
                return DOCUMENT_SCHEMA_INFO_LIST;
            }

            @Override
            public void migrateDocument(@NonNull MutableDocument<String> document) {
                synchronized (mLock) {
                    if (mState != STATE_OPENING_DATA_STORE) {
                        throw new IllegalStateException(
                                "migrateDocument: illegal state " + stateToString(mState));
                    }
                    document.putData(APM_ENABLED_PATH, toAirplaneModeState(mAirplaneModeState));
                }
            }
        };
    }

    @Override
    public void dump(@NonNull IndentingPrintWriter pw) {
        if (!mIsApmSyncSupported) {
            return;
        }
        synchronized (mLock) {
            pw.println("AirplaneModeSyncManager:");
            pw.increaseIndent();
            pw.println("State = " + stateToString(mState));
            pw.println("Airplane Mode State = " + mAirplaneModeState);
            pw.println("Airplane Mode Sync State = " + mSyncEnabled.get());
            pw.println("Is Kids Watch = " + mIsKidsWatch);
            if (mNetwork != null) {
                pw.println("Network : " + mNetwork);
            }
            if (mDataStore != null) {
                pw.println("Data Store : " + mDataStore);
            }
            pw.decreaseIndent();
        }
    }

    private static String stateToString(int state) {
        return switch (state) {
            case STATE_INACTIVE -> "INACTIVE";
            case STATE_OPENING_DATA_STORE -> "OPENING_DATA_STORE";
            case STATE_ACTIVE -> "ACTIVE";
            case STATE_CLOSING_DATA_STORE -> "CLOSING_DATA_STORE";
            default -> "UNKNOWN(" + state + ")";
        };
    }

    private static void logD(String log) {
        if (DEBUG) {
            Log.d(TAG, log);
        }
    }

    static String toAirplaneModeState(boolean enabled) {
        return enabled ? AIRPLANE_MODE_ON : AIRPLANE_MODE_OFF;
    }
}
