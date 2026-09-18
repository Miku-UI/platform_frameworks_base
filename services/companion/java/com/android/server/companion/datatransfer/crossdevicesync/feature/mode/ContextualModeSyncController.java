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
package com.android.server.companion.datatransfer.crossdevicesync.feature.mode;

import static android.app.modes.ContextualMode.TYPE_MANUAL_DO_NOT_DISTURB;

import static com.android.internal.util.FrameworkStatsLog.CROSS_DEVICE_SYNC_EVENT__FEATURE__FEATURE_CONTEXTUAL_MODE_SYNC;
import static com.android.server.companion.datatransfer.crossdevicesync.feature.mode.ModeSyncDocs.CONTEXTUAL_MODES_DOC_ID;
import static com.android.server.companion.datatransfer.crossdevicesync.feature.mode.ModeSyncMetadata.METADATA_CONTEXTUAL_MODE_SYNC_ENABLED;
import static com.android.server.companion.datatransfer.crossdevicesync.feature.mode.ModeSyncMetadata.METADATA_CONTEXTUAL_MODE_SYNC_SUPPORTED;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.modes.ContextualMode;
import android.app.modes.ContextualModeManager.ContextualModeListener;
import android.app.modes.ContextualModesMutation;
import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.os.PersistableBundle;
import android.os.Trace;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.IndentingPrintWriter;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.companion.datatransfer.crossdevicesync.common.ContextualModeManagerProxy;
import com.android.server.companion.datatransfer.crossdevicesync.common.DebugConfig;
import com.android.server.companion.datatransfer.crossdevicesync.common.Dumpable;
import com.android.server.companion.datatransfer.crossdevicesync.data.Docs;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStore;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStore.MutableDocument;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStore.OnRemoteChangeListener;
import com.android.server.companion.datatransfer.crossdevicesync.data.SharedDataStoreHandle;
import com.android.server.companion.datatransfer.crossdevicesync.data.StringConverter;
import com.android.server.companion.datatransfer.crossdevicesync.metadata.MetadataPublisher;
import com.android.server.companion.datatransfer.crossdevicesync.network.NetworkManager;
import com.android.server.companion.datatransfer.crossdevicesync.network.NetworkManager.Network;
import com.android.server.companion.datatransfer.crossdevicesync.network.NetworkManager.RemoteDevice;
import com.android.server.companion.datatransfer.crossdevicesync.notification.NotificationHelper;
import com.android.server.companion.datatransfer.crossdevicesync.notification.NotificationReason;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Helper class for managing per-user contextual mode sync. */
public class ContextualModeSyncController
        implements Dumpable, OnRemoteChangeListener, ContextualModeListener {
    private static final String TAG = "CtxModeSyncController";
    private static final boolean DEBUG = DebugConfig.DEBUG_FEATURE;

    /**
     * Network ID.
     *
     * <p>Note: DO NOT CHANGE.
     */
    public static final String NETWORK_ID = "contextual_mode_sync";

    /**
     * Data store name prefix.
     *
     * <p>Note: DO NOT CHANGE.
     */
    @VisibleForTesting
    static final String DATASTORE_NAME_PREFIX = "contextual_mode_sync_datastore_";

    private static final int STATE_INACTIVE = 0;
    private static final int STATE_OPENING_DATA_STORE = 1;
    private static final int STATE_ACTIVE = 2;
    private static final int STATE_CLOSING_DATA_STORE = 3;

    private final Object mLock = new Object();
    private final NetworkManager mNetworkManager;
    private final SharedDataStoreHandle<String> mSharedDataStoreHandle;
    private final Executor mMainExecutor;
    private final ContextualModeManagerProxy mContextualModeManager;
    private final UserHandle mUser;
    private final MetadataPublisher mMetadataPublisher;
    private final NotificationHelper mNotificationHelper;
    private final Consumer<Boolean> mModeSyncEnabledListener = this::onModeSyncEnabledChanged;

    @Nullable
    @GuardedBy("mLock")
    private Network mNetwork;

    @Nullable
    @GuardedBy("mLock")
    private SharedDataStore<String> mSharedDataStore;

    /** In-memory cache for modes that we care. */
    @GuardedBy("mLock")
    private final Map<String, ContextualMode> mModesCache = new HashMap<>();

    @GuardedBy("mLock")
    private int mState = STATE_INACTIVE;

    private final AtomicBoolean mModeSyncEnabled = new AtomicBoolean();

    @GuardedBy("mLock")
    private boolean mStarted;

    public ContextualModeSyncController(
            NetworkManager networkManager,
            SharedDataStoreHandle.Factory sharedDataStoreHandleFactory,
            Executor mainExecutor,
            StringConverter converter,
            ContextualModeManagerProxy contextualModeManager,
            MetadataPublisher metadataPublisher,
            NotificationHelper notificationHelper,
            UserHandle user) {
        mNetworkManager = networkManager;
        mSharedDataStoreHandle =
                sharedDataStoreHandleFactory.create(
                        DATASTORE_NAME_PREFIX + user.getIdentifier(),
                        converter,
                        ModeSyncDocs.getSchemaProvider(this::onDataMigration));
        mMainExecutor = mainExecutor;
        mContextualModeManager = contextualModeManager;
        mMetadataPublisher = metadataPublisher;
        mNotificationHelper = notificationHelper;
        mUser = user;
    }

    /** Start this controller. */
    public void start() {
        Trace.beginSection("ContextualModeSyncController.start");
        try {
            synchronized (mLock) {
                if (mStarted) {
                    // Duplicate start();
                    return;
                }
                mStarted = true;
                updateModesCacheLocked();
                mContextualModeManager.registerModeListener(mUser, mMainExecutor, this);
                mContextualModeManager.registerModeSyncEnabledListener(
                        mUser, mMainExecutor, mModeSyncEnabledListener);
                onModeSyncEnabledChanged(mContextualModeManager.isModeSyncEnabled(mUser));
                maybeUpdateDataStoreLocked();
            }
        } finally {
            Trace.endSection();
        }
    }

    private void onModeSyncEnabledChanged(boolean enabled) {
        synchronized (mLock) {
            if (!mStarted) {
                // Already closed.
                return;
            }
            mMetadataPublisher.putBooleanMetaData(
                    mUser.getIdentifier(),
                    ModeSyncMetadata.METADATA_CONTEXTUAL_MODE_SYNC_ENABLED,
                    enabled);
            if (mModeSyncEnabled.compareAndSet(!enabled, enabled)) {
                Log.i(TAG, "User " + mUser + " changed mode sync setting to " + enabled + ".");
                mNetworkManager.invalidateNetworks();
            }
        }
    }

    @GuardedBy("mLock")
    private void maybeUpdateDataStoreLocked() {
        if (!mStarted || mModesCache.isEmpty()) {
            maybeCloseDataStoreLocked();
        } else {
            // Open data store only if controller started and user has modes to sync.
            maybeOpenDataStoreLocked();
        }
    }

    @GuardedBy("mLock")
    private void maybeOpenDataStoreLocked() {
        if (mState != STATE_INACTIVE) {
            if (DEBUG) {
                Log.v(
                        TAG,
                        "maybeOpenDataStore: ignored. Current state is " + stateToString(mState));
            }
            return;
        }
        Log.i(TAG, "Opening shared data store for user " + mUser + ".");
        mState = STATE_OPENING_DATA_STORE;
        mNetwork =
                mNetworkManager.createNetworkForUser(
                        mUser.getIdentifier(),
                        NETWORK_ID,
                        CROSS_DEVICE_SYNC_EVENT__FEATURE__FEATURE_CONTEXTUAL_MODE_SYNC,
                        this::shouldSyncWithDevice);
        var ignored =
                mSharedDataStoreHandle
                        .openDataStore(mNetwork)
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

    @GuardedBy("mLock")
    private void maybeCloseDataStoreLocked() {
        if (mState != STATE_ACTIVE) {
            if (DEBUG) {
                Log.v(
                        TAG,
                        "maybeCloseDataStore: ignored. Current state is " + stateToString(mState));
            }
            return;
        }
        Log.i(TAG, "Closing shared data store for user " + mUser + ".");
        mState = STATE_CLOSING_DATA_STORE;
        SharedDataStore<String> dataStore = requireNonNull(mSharedDataStore);
        dataStore.unregisterOnRemoteChangeListener(this);
        var ignored =
                dataStore
                        .close()
                        .whenCompleteAsync((unused, t) -> onDataStoreClosed(), mMainExecutor);
    }

    // Called in IO thread.
    private void onDataMigration(MutableDocument<String> doc) {
        synchronized (mLock) {
            if (mState != STATE_OPENING_DATA_STORE) {
                throw new IllegalArgumentException(
                        "createSharedDataStore: illegal state " + stateToString(mState));
            }
            // Put initial state to data store.
            syncToDocument(doc, /* overrideExisting= */ false);
        }
    }

    private void onDataStoreInitialized(SharedDataStore<String> dataStore) {
        synchronized (mLock) {
            if (mState != STATE_OPENING_DATA_STORE) {
                return;
            }
            Log.i(TAG, "Successfully initialized data store for user " + mUser + "!");
            mState = STATE_ACTIVE;
            mSharedDataStore = dataStore;
            mSharedDataStore.registerOnRemoteChangeListener(mMainExecutor, this);
            // Double check in case we need to close the data store due to changes in preconditions.
            maybeUpdateDataStoreLocked();
        }
    }

    private void onDataStoreFailedToInitialize(Throwable t) {
        synchronized (mLock) {
            if (mState != STATE_OPENING_DATA_STORE) {
                return;
            }
            Log.e(TAG, "Failed to initialize data store for user " + mUser + "!", t);
            mState = STATE_INACTIVE;
            requireNonNull(mNetwork).destroy();
            mNetwork = null;
        }
    }

    private void onDataStoreClosed() {
        synchronized (mLock) {
            if (mState != STATE_CLOSING_DATA_STORE) {
                return;
            }
            Log.i(TAG, "Closed data store for user " + mUser + "!");
            mState = STATE_INACTIVE;
            mSharedDataStore = null;
            requireNonNull(mNetwork).destroy();
            mNetwork = null;
            // Double check in case we need to open the data store due to changes in preconditions.
            maybeUpdateDataStoreLocked();
        }
    }

    @Override
    public void onRemoteChange(List<String> paths) {
        Trace.beginSection("ContextualModeSyncController.onRemoteChange");
        try {
            synchronized (mLock) {
                if (mState != STATE_ACTIVE) {
                    return;
                }
                Map<String, ContextualMode> modes = new ArrayMap<>();
                for (String path : paths) {
                    for (ContextualMode m : mModesCache.values()) {
                        if (path.equals(getModeStatePath(m))) {
                            modes.put(path, m);
                            break;
                        }
                    }
                }
                if (modes.isEmpty()) {
                    // No mode matches the paths.
                    return;
                }
                transactDataStoreLocked(doc -> syncFromDocument(doc, modes));
            }
        } finally {
            Trace.endSection();
        }
    }

    @Override
    public void onModesChanged(@NonNull List<ContextualMode> modes) {
        Trace.beginSection("ContextualModeSyncController.onModesChanged");
        try {
            synchronized (mLock) {
                maybeDismissDndSyncNotification(modes);
                if (!updateModesCacheLocked(modes)) {
                    // Cache unchanged.
                    return;
                }
                if (mState == STATE_ACTIVE) {
                    transactDataStoreLocked(
                            doc -> syncToDocument(doc, /* overrideExisting= */ true));
                } else {
                    // Adding a mode may lead to data store open.
                    maybeUpdateDataStoreLocked();
                }
            }
        } finally {
            Trace.endSection();
        }
    }

    private void maybeDismissDndSyncNotification(List<ContextualMode> changedModes) {
        for (ContextualMode mode : changedModes) {
            if (mode.getType() == TYPE_MANUAL_DO_NOT_DISTURB
                    && mode.getState() == ContextualMode.STATE_INACTIVE) {
                mNotificationHelper.dismissNotification(
                        NotificationReason.DO_NOT_DISTURB_SYNCED, mUser);
                break;
            }
        }
    }

    @Override
    public void onModeRemoved(@NonNull String modeId) {
        Trace.beginSection("ContextualModeSyncController.onModeRemoved");
        try {
            synchronized (mLock) {
                if (mModesCache.remove(modeId) == null) {
                    // Unchanged.
                    return;
                }
                if (mState == STATE_ACTIVE) {
                    // Removal of a mode in sync may lead to data store closed.
                    maybeUpdateDataStoreLocked();
                }
            }
        } finally {
            Trace.endSection();
        }
    }

    @GuardedBy("mLock")
    private void updateModesCacheLocked() {
        updateModesCacheLocked(mContextualModeManager.getModes(mUser));
    }

    @GuardedBy("mLock")
    private boolean updateModesCacheLocked(List<ContextualMode> modes) {
        boolean changed = false;
        for (ContextualMode mode : modes) {
            if (isSyncSupportedForMode(mode) && !mode.equals(mModesCache.get(mode.getId()))) {
                mModesCache.put(mode.getId(), mode);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean isSyncSupportedForMode(ContextualMode mode) {
        return getModeStatePath(mode) != null;
    }

    @Nullable
    private static String getModeStatePath(ContextualMode mode) {
        // Only support DND sync for now.
        if (mode.getType() == TYPE_MANUAL_DO_NOT_DISTURB) {
            return ModeSyncDocs.getManualDoNotDisturbStatePath();
        }
        return null;
    }

    // Called from IO thread.
    private void syncToDocument(MutableDocument<String> doc, boolean overrideExisting) {
        synchronized (mLock) {
            if (mState != STATE_ACTIVE && mState != STATE_OPENING_DATA_STORE) {
                return;
            }
            for (ContextualMode mode : mModesCache.values()) {
                boolean active = mode.getState() == ContextualMode.STATE_ACTIVE;
                String path = getModeStatePath(mode);
                boolean hasExistingValue = Docs.getString(doc, path) != null;
                if (!hasExistingValue
                        || (overrideExisting
                                && ModeSyncDocs.isModeStateActive(doc, path) != active)) {
                    Log.i(TAG, "Syncing mode state " + mode + " to shared data store.");
                    ModeSyncDocs.putModeStateActive(doc, path, active);
                }
            }
        }
    }

    // Called from IO thread.
    private void syncFromDocument(
            MutableDocument<String> doc, Map<String, ContextualMode> cachedModes) {
        synchronized (mLock) {
            if (mState != STATE_ACTIVE) {
                return;
            }
            ContextualModesMutation.Builder mutationBuilder = new ContextualModesMutation.Builder();
            boolean changed = false;
            for (Map.Entry<String, ContextualMode> entry : cachedModes.entrySet()) {
                String path = entry.getKey();
                ContextualMode cachedMode = entry.getValue();
                boolean active = ModeSyncDocs.isModeStateActive(doc, path);
                int remoteState =
                        active ? ContextualMode.STATE_ACTIVE : ContextualMode.STATE_INACTIVE;
                if (cachedMode.getState() == remoteState) {
                    // Unchanged.
                    continue;
                }
                changed = true;
                ContextualMode updatedMode =
                        new ContextualMode.Builder(cachedMode).setState(remoteState).build();
                mutationBuilder.addUpdatedMode(updatedMode);
                mModesCache.put(updatedMode.getId(), updatedMode);
                if (cachedMode.getType() == TYPE_MANUAL_DO_NOT_DISTURB && active) {
                    mNotificationHelper.maybeShowNotification(
                            NotificationReason.DO_NOT_DISTURB_SYNCED, mUser);
                }
                Log.i(
                        TAG,
                        "Updating mode "
                                + cachedMode
                                + " state to "
                                + ContextualMode.modeStateToString(remoteState)
                                + " due to shared data store change.");
            }
            if (changed) {
                mContextualModeManager.mutateModes(mUser, mutationBuilder.build());
            }
        }
    }

    @GuardedBy("mLock")
    private void transactDataStoreLocked(Consumer<MutableDocument<String>> consumer) {
        var ignored =
                requireNonNull(mSharedDataStore)
                        .transact(
                                CONTEXTUAL_MODES_DOC_ID,
                                doc -> {
                                    consumer.accept(doc);
                                    return true;
                                })
                        .whenComplete(
                                (result, t) -> {
                                    if (t != null) {
                                        Log.e(
                                                TAG,
                                                " Failed to commit data store transaction for user "
                                                        + mUser
                                                        + ".",
                                                t);
                                    }
                                });
    }

    private boolean shouldSyncWithDevice(RemoteDevice device) {
        if (!mModeSyncEnabled.get()) {
            return false;
        }
        AssociationInfo associationInfo = device.getAssociationInfoCache();
        PersistableBundle metadata =
                associationInfo.getMetadata(CompanionDeviceManager.FEATURE_CROSS_DEVICE_SYNC);
        boolean syncSupported =
                metadata.getBoolean(
                        METADATA_CONTEXTUAL_MODE_SYNC_SUPPORTED, /* defaultValue= */ false);
        boolean syncEnabled =
                metadata.getBoolean(
                        METADATA_CONTEXTUAL_MODE_SYNC_ENABLED, /* defaultValue= */ false);
        boolean hasModeSyncFlag =
                (associationInfo.getSystemDataSyncFlags()
                                & CompanionDeviceManager.FLAG_UNIVERSAL_MODES)
                        != 0;
        return syncSupported && syncEnabled && hasModeSyncFlag;
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

    /** Stop this controller. */
    public void stop() {
        stop(/* deleteDataStore= */ false);
    }

    /**
     * Stop this controller.
     *
     * @param deleteDataStore whether to delete the data store after closing
     */
    public void stop(boolean deleteDataStore) {
        Trace.beginSection("ContextualModeSyncController.stop");
        try {
            synchronized (mLock) {
                if (!mStarted) {
                    // Already closed or not started.
                    return;
                }
                mStarted = false;
                mContextualModeManager.unregisterModeListener(this);
                mContextualModeManager.unregisterModeSyncEnabledListener(mModeSyncEnabledListener);
                if (mSharedDataStore != null) {
                    mSharedDataStore.unregisterOnRemoteChangeListener(this);
                    mSharedDataStore = null;
                }
                if (mNetwork != null) {
                    mNetwork.destroy();
                    mNetwork = null;
                }
                mSharedDataStoreHandle.destroy(deleteDataStore);
            }
        } finally {
            Trace.endSection();
        }
    }

    @Override
    public void dump(IndentingPrintWriter pw) {
        pw.println("CtxModeSyncController:");
        pw.increaseIndent();
        synchronized (mLock) {
            pw.println("mUser=" + mUser);
            pw.println("mState=" + stateToString(mState));
            pw.println("mStarted=" + mStarted);
            pw.println("mModeSyncEnabled=" + mModeSyncEnabled.get());
            pw.println(
                    "hasSyncableRemoteDevice="
                            + (mNetwork != null && !mNetwork.getRemoteDevices().isEmpty()));
            pw.println("mModesCache=" + mModesCache.values());
            pw.println("mNetwork=" + mNetwork);
            pw.println("mDataStore=" + mSharedDataStore);
        }
        pw.decreaseIndent();
    }

    /** A factory to create a {@link ContextualModeSyncController} */
    public interface Factory {
        /** Create a {@link ContextualModeSyncController} */
        ContextualModeSyncController create(UserHandle user);
    }
}
