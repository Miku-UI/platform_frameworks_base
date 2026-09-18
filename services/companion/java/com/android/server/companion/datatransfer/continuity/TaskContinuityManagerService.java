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

package com.android.server.companion.datatransfer.continuity;

import static android.Manifest.permission.MODIFY_HANDOFF_SETTINGS;
import static android.Manifest.permission.READ_HANDOFF_SETTINGS;
import static android.Manifest.permission.READ_REMOTE_TASKS;
import static android.Manifest.permission.REQUEST_TASK_HANDOFF;
import static com.android.server.companion.utils.PermissionsUtils.enforceCallerIsSystemOrCanInteractWithUserId;

import android.annotation.EnforcePermission;
import android.annotation.NonNull;
import android.companion.datatransfer.continuity.IHandoffFeatureStateListener;
import android.companion.datatransfer.continuity.IHandoffRequestCallback;
import android.companion.datatransfer.continuity.IRemoteTaskListener;
import android.companion.datatransfer.continuity.ITaskContinuityManager;
import android.content.Context;
import android.os.Binder;
import com.android.server.SystemService;
import com.android.server.companion.datatransfer.continuity.handoff.HandoffController;
import com.android.server.companion.datatransfer.continuity.handoff.HandoffControllerCache;
import com.android.server.companion.datatransfer.continuity.settings.HandoffPreferenceStore;
import com.android.server.companion.datatransfer.continuity.settings.HandoffSettingsManager;
import java.util.Objects;

/**
 * Service to handle task continuity features
 *
 * @hide
 */
public final class TaskContinuityManagerService extends SystemService {

    private static final String TAG = TaskContinuityManagerService.class.getSimpleName();

    private final MultiUserResourceCache<HandoffController> mHandoffControllerCache;
    private HandoffPreferenceStore mHandoffPreferenceStore;
    private HandoffSettingsManager mHandoffSettingsManager;
    private TaskContinuityManagerServiceImpl mTaskContinuityManagerService;

    public TaskContinuityManagerService(Context context) {
        super(context);

        mHandoffPreferenceStore = new HandoffPreferenceStore();
        mHandoffSettingsManager = new HandoffSettingsManager(mHandoffPreferenceStore);
        mHandoffControllerCache = new HandoffControllerCache(context);
    }

    @Override
    public void onUserUnlocked(TargetUser user) {
        updateHandoffEnablementForUser(user.getUserIdentifier());
    }

    @Override
    public void onStart() {
        mTaskContinuityManagerService = new TaskContinuityManagerServiceImpl();
        publishBinderService(Context.TASK_CONTINUITY_SERVICE, mTaskContinuityManagerService);
    }

    private final class TaskContinuityManagerServiceImpl extends ITaskContinuityManager.Stub {
        @Override
        @EnforcePermission(READ_REMOTE_TASKS)
        public void registerRemoteTaskListener(int userId, @NonNull IRemoteTaskListener listener) {
            registerRemoteTaskListener_enforcePermission();
            enforceCallerIsSystemOrCanInteractWithUserId(getContext(), userId);
            mHandoffControllerCache
                    .getOrCreateResource(userId)
                    .registerTaskListener(Objects.requireNonNull(listener));
        }

        @Override
        @EnforcePermission(READ_REMOTE_TASKS)
        public void unregisterRemoteTaskListener(
                int userId, @NonNull IRemoteTaskListener listener) {
            unregisterRemoteTaskListener_enforcePermission();
            enforceCallerIsSystemOrCanInteractWithUserId(getContext(), userId);
            mHandoffControllerCache
                    .getOrCreateResource(userId)
                    .unregisterTaskListener(Objects.requireNonNull(listener));
        }

        @Override
        @EnforcePermission(REQUEST_TASK_HANDOFF)
        public void requestHandoff(
                int userId,
                int associationId,
                int remoteTaskId,
                @NonNull IHandoffRequestCallback callback) {
            requestHandoff_enforcePermission();
            enforceCallerIsSystemOrCanInteractWithUserId(getContext(), userId);

            Objects.requireNonNull(callback);

            final long ident = Binder.clearCallingIdentity();
            try {
                mHandoffControllerCache
                        .getOrCreateResource(userId)
                        .requestHandoff(associationId, remoteTaskId, callback);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        @EnforcePermission(MODIFY_HANDOFF_SETTINGS)
        public void setHandoffForDeviceEnabled(int userId, boolean enabled) {
            setHandoffForDeviceEnabled_enforcePermission();
            enforceCallerIsSystemOrCanInteractWithUserId(getContext(), userId);

            final long ident = Binder.clearCallingIdentity();
            try {
                mHandoffSettingsManager.setHandoffEnabledForUser(userId, enabled);
                updateHandoffEnablementForUser(userId);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        @EnforcePermission(READ_HANDOFF_SETTINGS)
        public void registerHandoffFeatureStateListener(
                int userId, @NonNull IHandoffFeatureStateListener listener) {
            registerHandoffFeatureStateListener_enforcePermission();
            enforceCallerIsSystemOrCanInteractWithUserId(getContext(), userId);

            final long ident = Binder.clearCallingIdentity();
            try {
                mHandoffSettingsManager.registerHandoffFeatureStateListener(
                        userId, Objects.requireNonNull(listener));
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        @Override
        @EnforcePermission(READ_HANDOFF_SETTINGS)
        public void unregisterHandoffFeatureStateListener(
                int userId, @NonNull IHandoffFeatureStateListener listener) {
            unregisterHandoffFeatureStateListener_enforcePermission();
            enforceCallerIsSystemOrCanInteractWithUserId(getContext(), userId);

            final long ident = Binder.clearCallingIdentity();
            try {
                mHandoffSettingsManager.unregisterHandoffFeatureStateListener(
                        userId, Objects.requireNonNull(listener));
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    private void updateHandoffEnablementForUser(int userId) {
        if (mHandoffSettingsManager.isHandoffActiveForUser(userId)) {
            mHandoffControllerCache.getOrCreateResource(userId).enable();
        } else {
            mHandoffControllerCache.getOrCreateResource(userId).disable();
        }
    }
}
