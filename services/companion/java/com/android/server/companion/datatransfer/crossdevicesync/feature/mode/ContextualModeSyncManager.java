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

import android.os.UserHandle;
import android.util.IndentingPrintWriter;
import android.util.Log;

import com.android.server.companion.datatransfer.crossdevicesync.common.ContextualModeManagerProxy;
import com.android.server.companion.datatransfer.crossdevicesync.feature.FeatureManager;
import com.android.server.companion.datatransfer.crossdevicesync.metadata.MetadataPublisher;
import com.android.server.companion.datatransfer.crossdevicesync.user.UserHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Executor;

/** Contextual mode sync feature manager. */
public class ContextualModeSyncManager implements FeatureManager, UserHelper.UserListener {
    private static final String TAG = "CtxModeSyncManager";
    private final ContextualModeSyncController.Factory mModeSyncControllerFactory;
    private final Map<UserHandle, ContextualModeSyncController> mSyncControllers = new HashMap<>();
    private final UserHelper mUserHelper;
    private final ContextualModeManagerProxy mContextualModeManager;
    private final Executor mMainExecutor;
    private final MetadataPublisher mMetadataPublisher;
    private boolean mModeSyncSupported;
    private boolean mInitialized;

    public ContextualModeSyncManager(
            ContextualModeSyncController.Factory modeSyncControllerFactory,
            UserHelper userHelper,
            ContextualModeManagerProxy contextualModeManager,
            Executor mainExecutor,
            MetadataPublisher metadataPublisher) {
        mModeSyncControllerFactory = modeSyncControllerFactory;
        mUserHelper = userHelper;
        mContextualModeManager = contextualModeManager;
        mMainExecutor = mainExecutor;
        mMetadataPublisher = metadataPublisher;
    }

    @Override
    public void init() {
        if (!android.service.notification.Flags.enableDndSync()) {
            return;
        }
        if (mInitialized) {
            return;
        }
        mInitialized = true;
        mModeSyncSupported = mContextualModeManager.isModeSyncSupported();
        mMetadataPublisher.putBooleanMetaData(
                UserHandle.USER_ALL,
                ModeSyncMetadata.METADATA_CONTEXTUAL_MODE_SYNC_SUPPORTED,
                mModeSyncSupported);
        if (!mModeSyncSupported) {
            return;
        }
        mMetadataPublisher.putIntMetaData(
                UserHandle.USER_ALL,
                ModeSyncMetadata.METADATA_CONTEXTUAL_MODES_DOC_SCHEMA_VERSION,
                ModeSyncDocs.CURRENT_SCHEMA_VERSION);
        maybeUpdateSyncControllers();
        mUserHelper.registerUserListener(mMainExecutor, this);
    }

    @Override
    public void onUserAdded(UserHandle user) {
        Log.i(TAG, "User " + user + " is added.");
        maybeUpdateSyncControllers();
    }

    @Override
    public void onUserRemoved(UserHandle user) {
        Log.i(TAG, "User " + user + " is removed.");
        maybeUpdateSyncControllers();
    }

    private void maybeUpdateSyncControllers() {
        Set<UserHandle> users = new HashSet<>();
        mUserHelper.getAliveUsers().forEach(info -> users.add(info.getUserHandle()));

        // Start mode sync controller for each user.
        for (UserHandle user : users) {
            if (mSyncControllers.containsKey(user)) {
                continue;
            }
            Log.i(TAG, "Starting contextual mode sync controller for user " + user + ".");
            ContextualModeSyncController controller = mModeSyncControllerFactory.create(user);
            mSyncControllers.put(user, controller);
            controller.start();
        }

        // Handle user removal case.
        Iterator<Entry<UserHandle, ContextualModeSyncController>> modeSyncControllerIterator =
                mSyncControllers.entrySet().iterator();
        while (modeSyncControllerIterator.hasNext()) {
            Entry<UserHandle, ContextualModeSyncController> entry =
                    modeSyncControllerIterator.next();
            UserHandle user = entry.getKey();
            ContextualModeSyncController syncController = entry.getValue();
            if (!users.contains(user)) {
                Log.i(TAG, "Stopping contextual mode sync controller for user " + user + ".");
                syncController.stop(/* deleteDataStore= */ true);
                modeSyncControllerIterator.remove();
            }
        }
    }

    @Override
    public void destroy() {
        if (!mInitialized) {
            return;
        }
        mInitialized = false;
        mSyncControllers.values().forEach(ContextualModeSyncController::stop);
        mSyncControllers.clear();
        mUserHelper.unregisterUserListener(this);
    }

    @Override
    public void dump(IndentingPrintWriter pw) {
        pw.println("ContextualModeSyncManager");
        pw.increaseIndent();
        pw.println("mInitialized=" + mInitialized);
        pw.println("mModeSyncSupported=" + mModeSyncSupported);
        if (mSyncControllers.isEmpty()) {
            pw.println("No active mode sync controllers!");
        } else {
            mSyncControllers.values().forEach(c -> c.dump(pw));
        }
        pw.decreaseIndent();
    }
}
