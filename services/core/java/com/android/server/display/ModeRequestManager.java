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

package com.android.server.display;

import static android.view.Display.Mode.INVALID_MODE_ID;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Handler;
import android.os.Looper;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Display;
import android.view.DisplayInfo;

import com.android.graphics.surfaceflinger.flags.Flags;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.display.mode.DisplayModeDirector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages the state of display mode change requests as they propagate through the system.
 */
public class ModeRequestManager {
    public enum RequestStatus {
        IDLE,
        WAITING_FOR_DEVICE_INFO,
        DEVICE_INFO_CREATED,
        WAITING_FOR_MODE_SPECS,
        MODE_SPECS_SET
    }
    private final Object mLock = new Object();
    static final String TAG = "ModeRequestManager";

    // Maps logical displayId to the current state of its mode request.
    @GuardedBy("mLock")
    private final SparseArray<RequestStatus> mStates = new SparseArray<>();

    // Modes requested per displayId
    @GuardedBy("mLock")
    private final SparseArray<Display.Mode> mModes = new SparseArray<>();

    // Store modes requested per displayId
    @GuardedBy("mLock")
    private final SparseArray<Boolean> mStoreModes = new SparseArray<>();

    @GuardedBy("mLock")
    private SparseArray<DisplayInfo> mCachedDisplayInfos = new SparseArray<>();

    @GuardedBy("mLock")
    private final Map<DisplayDevice, DisplayModeDirector.DesiredDisplayModeSpecs> mSpecs =
            new HashMap<>();

    private final HandlerWrapper mHandlerWrapper;
    private static final long MODE_REQUEST_TIMEOUT_MS = 5000;

    private final boolean mEnabled = Flags.modesetMultiDisplay();

    /**
     * Interface for the action to be taken when a mode request times out.
     */
    @FunctionalInterface
    public interface RollbackListener {
        /**
         * Called when the mode request times out or is manually rolled back.
         * @param originalData The data needed to rollback the changes.
         */
        void rollback(UserPreferredModeRequest[] originalData);
    }

    ModeRequestManager(Looper looper, RollbackListener rollbackAction) {
        mHandlerWrapper = new HandlerWrapper(new Handler(looper), rollbackAction);
    }

    @VisibleForTesting
    ModeRequestManager(Handler dmsHandler, RollbackListener rollbackAction) {
        mHandlerWrapper = new HandlerWrapper(dmsHandler, rollbackAction);
    }

    /**
     * Stores the initial statuses for the requests, their displayIds, requested modes, and original
     * infos
     * @param requests The requests to store and follow the status of
     * @param displays The logical displays os the LogicalDisplayMapper - used to retrieve original
     *                 infos
     * @return true if all the requests were successfully stored, false otherwise
     */
    public boolean onUserPreferredModesRequestedLocked(UserPreferredModeRequest[] requests,
                                                    SparseArray<LogicalDisplay> displays) {
        if (isDisabled()) {
            return false;
        }
        synchronized (mLock) {
            SparseArray<DisplayInfo> originalInfos = new SparseArray<>();
            for (UserPreferredModeRequest request : requests) {
                if (request.mDisplayId == Display.INVALID_DISPLAY) {
                    throw new IllegalArgumentException("Global display mode is not supported.");
                }
                if (isRequestInProgress(request.mDisplayId)) {
                    Slog.w(TAG, "Mode request already in progress for display "
                            + request.mDisplayId);
                    return false;
                }
                LogicalDisplay display = displays.get(request.mDisplayId);
                originalInfos.put(request.mDisplayId, display != null
                                ? display.getDisplayInfoLocked() : null);
                mStates.put(request.mDisplayId, RequestStatus.IDLE);
                mModes.put(request.mDisplayId, request.mMode);
                mStoreModes.put(request.mDisplayId, request.mStoreMode);
            }
            mHandlerWrapper.scheduleTimeout(MODE_REQUEST_TIMEOUT_MS);
            mCachedDisplayInfos = originalInfos.clone();
            return true;
        }
    }

    /**
     * Clears all requests.
     */
    public void clearRequests() {
        if (isDisabled()) {
            return;
        }
        synchronized (mLock) {
            mStates.clear();
            mModes.clear();
            mCachedDisplayInfos.clear();
            mStoreModes.clear();
            mHandlerWrapper.cancelTimeout();
        }
    }

    /**
     * Removes a display from the batch (e.g. if it was unplugged).
     */
    public void onDisplayRemoved(int displayId) {
        synchronized (mLock) {
            mStates.remove(displayId);
            mModes.remove(displayId);
            mCachedDisplayInfos.remove(displayId);
            mStoreModes.remove(displayId);
        }
    }

    /**
     * Returns the array of requests which would revert any potential changes made. Clears all
     * requests.
     */
    public UserPreferredModeRequest[] getRollbackData() {
        synchronized (mLock) {
            ArrayList<UserPreferredModeRequest> requests = new ArrayList<>();
            for (int i = 0; i < mStates.size(); i++) {
                if (mStates.valueAt(i).ordinal() >= RequestStatus.DEVICE_INFO_CREATED.ordinal()) {
                    int displayId = mStates.keyAt(i);
                    DisplayInfo originalInfo = mCachedDisplayInfos.get(displayId);
                    Display.Mode originalMode = findMode(originalInfo);
                    requests.add(new UserPreferredModeRequest(displayId,
                            originalMode, mStoreModes.get(displayId)));
                }
            }
            return requests.toArray(UserPreferredModeRequest[]::new);
        }
    }

    private Display.Mode findMode(@NonNull DisplayInfo info) {
        int modeId = info.userPreferredModeId;
        if (modeId == INVALID_MODE_ID) {
            return null;
        }
        for (var mode : info.supportedModes) {
            if (mode.getModeId() == modeId) {
                return mode;
            }
        }
        return null;
    }

    /**
     * Updates the status of a display mode request after the device info creation.
     * @param displayId The displayId of the request
     * @param displayInfo The display info containing the userPreferredModeId
     * @return true if the id in info matches the one requested and we can continue with the
     * process. Will break the flow otherwise.
     */
    public boolean infoUpdated(int displayId, DisplayInfo displayInfo) {
        if (isDisabled()) {
            return false;
        }
        synchronized (mLock) {
            Display.Mode requestedMode = mModes.get(displayId);
            int requestedModeId = requestedMode == null ? Display.Mode.INVALID_MODE_ID
                    : requestedMode.getModeId();
            if (displayInfo != null && requestedModeId == displayInfo.userPreferredModeId) {
                updateStatus(displayId, RequestStatus.DEVICE_INFO_CREATED);
                return true;
            } else {
                Slog.w(TAG, "Mismatch between info and expected mode id. Clearing the"
                        + " requests");
                return false;
            }
        }
    }

    /**
     * Updates the status of a display mode request after the votes are set.
     */
    public void votesReady(int displayId, int userPreferredModeId) {
        if (isDisabled()) {
            return;
        }
        synchronized (mLock) {
            Display.Mode requestedMode = mModes.get(displayId);
            int requestedModeId = requestedMode == null ? Display.Mode.INVALID_MODE_ID
                    : requestedMode.getModeId();
            if (requestedModeId != userPreferredModeId) {
                return;
            }
            updateStatus(displayId, RequestStatus.WAITING_FOR_MODE_SPECS);
        }
    }

    /**
     * Updates the status of a display mode request after the overlay is ready (since it does not
     * use specs to update modes)
     * @param userPreferredModeId The user preferred mode id that the overlay is setting
     */
    public void overlayReady(int userPreferredModeId) {
        if (isDisabled()) {
            return;
        }
        // since we can guarantee that modes are unique for a display, if we can find a mode id in
        // the modes in progress, we can say that display has an update in progress
        for (int i = 0; i < mModes.size(); i++) {
            Display.Mode mode = mModes.valueAt(i);
            if ((mode != null) && (mode.getModeId() == userPreferredModeId)) {
                synchronized (mLock) {
                    updateStatus(mModes.keyAt(i), RequestStatus.WAITING_FOR_MODE_SPECS);
                }
            }

        }
    }

    /**
     * Updates the status of a display mode request after the mode specs are set. This is the final
     * state.
     * @param displayId The displayId of the request
     */
    void onSpecsSet(int displayId) {
        if (isDisabled()) {
            return;
        }
        synchronized (mLock) {
            if (getStatus(displayId) == RequestStatus.WAITING_FOR_MODE_SPECS) {
                updateStatus(displayId, RequestStatus.MODE_SPECS_SET);
            }
        }
    }

    void waitingForDeviceInfo(int displayId) {
        if (isDisabled()) {
            return;
        }
        synchronized (mLock) {
            if (getStatus(displayId) == RequestStatus.IDLE) {
                updateStatus(displayId, RequestStatus.WAITING_FOR_DEVICE_INFO);
            }
        }
    }

    void storeMode(int displayId, Display.Mode mode) {
        if (isDisabled()) {
            return;
        }
        synchronized (mLock) {
            mModes.put(displayId, mode);
        }
    }

    /**
     * Updates the status for a specific display.
     */
    @VisibleForTesting
    void updateStatus(int displayId, RequestStatus newStatus) {
        if (isDisabled()) {
            return;
        }
        synchronized (mLock) {
            RequestStatus oldStatus = mStates.get(displayId);

            if (oldStatus == null) {
                if (newStatus == RequestStatus.IDLE) {
                    mStates.put(displayId, newStatus);
                }
            } else {
                if (oldStatus.ordinal() + 1 == newStatus.ordinal()) {
                    mStates.put(displayId, newStatus);
                }
            }
        }
    }

    /**
     * Returns the current status of a display mode request.
     */
    RequestStatus getStatus(int displayId) {
        if (isDisabled()) {
            return null;
        }
        synchronized (mLock) {
            RequestStatus status = mStates.get(displayId);
            return status != null ? status : RequestStatus.IDLE;
        }
    }

    /**
     * Returns true if there is an active request for the given display.
     */
    boolean isRequestInProgress(int displayId) {
        if (isDisabled()) {
            return false;
        }
        synchronized (mLock) {
            return mStates.indexOfKey(displayId) >= 0;
        }
    }

    /**
     * Returns true if there is a request with one of the modes from the list in progress.
     * Same behavior as isRequestInProgress since we can guarantee that modes are unique for
     * different displays.
     * @param modes The modes to check for
     */
    boolean isAnyModeInProgress(SparseArray<LocalDisplayAdapter.DisplayModeRecord> modes) {
        if (isDisabled()) {
            return false;
        }
        synchronized (mLock) {
            for (int i = 0; i < modes.size(); i++) {
                LocalDisplayAdapter.DisplayModeRecord mode = modes.valueAt(i);
                if (mModes.indexOfValue(mode.mMode) >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    void setSpecs(int displayId, DisplayDevice device,
                  DisplayModeDirector.DesiredDisplayModeSpecs specs) {
        if (isDisabled()) {
            return;
        }
        synchronized (mLock) {
            updateStatus(displayId, RequestStatus.MODE_SPECS_SET);
            mSpecs.put(device, specs);
        }
    }

    Map<DisplayDevice, DisplayModeDirector.DesiredDisplayModeSpecs> getSpecs() {
        if (isDisabled()) {
            return null;
        }
        synchronized (mLock) {
            return Map.copyOf(mSpecs);
        }
    }

    /**
     * Returns true if there are any active requests.
     */
    boolean hasActiveRequests() {
        if (isDisabled()) {
            return false;
        }
        synchronized (mLock) {
            return mStates.size() > 0;
        }
    }

    /**
     * Checks if all displays in a batch have reached a specific status.
     */
    boolean allDisplaysReachedStatus(RequestStatus expectedStatus) {
        if (isDisabled()) {
            return false;
        }
        synchronized (mLock) {
            if (!hasActiveRequests()) {
                return false;
            }
            for (int i = 0; i < mStates.size(); i++) {
                RequestStatus actualStatus = mStates.valueAt(i);
                if (expectedStatus != actualStatus) {
                    return false;
                }
            }
        }
        return true;
    }

    DisplayInfo getDisplayInfo(int displayId, DisplayInfo defaultDisplayInfo) {
        synchronized (mLock) {
            RequestStatus currentStatus = mStates.get(displayId);
            if (currentStatus == null || currentStatus == RequestStatus.IDLE) {
                return defaultDisplayInfo;
            } else {
                return mCachedDisplayInfos.get(displayId);
            }
        }
    }

    void cancelAndRollback() {
        synchronized (mLock) {
            if (!hasActiveRequests()) {
                return;
            }
            mHandlerWrapper.cancelTimeout();
            mHandlerWrapper.rollbackNow();
        }
    }

    boolean isDisabled() {
        return !mEnabled;
    }

    private class HandlerWrapper {
        private final Handler mDmsHandler;
        private final RollbackListener mRollbackAction;
        private static final Object TIMEOUT_TOKEN = new Object();

        // Executes the onTimeoutAction provided by DMS.
        private final Runnable mTimeoutRunner;

        HandlerWrapper(Handler dmsHandler, RollbackListener rollbackAction) {
            mDmsHandler = dmsHandler;
            mRollbackAction = rollbackAction;
            mTimeoutRunner = () -> {
                Slog.i(TAG, "DmsHandlerWrapper: Timeout triggered, executing onTimeoutAction.");
                mRollbackAction.rollback(getRollbackData());
            };
        }

        void scheduleTimeout(long delayMillis) {
            mDmsHandler.removeCallbacks(mTimeoutRunner, TIMEOUT_TOKEN);
            mDmsHandler.postDelayed(mTimeoutRunner, TIMEOUT_TOKEN, delayMillis);
        }

        void cancelTimeout() {
            mDmsHandler.removeCallbacks(mTimeoutRunner, TIMEOUT_TOKEN);
        }

        void rollbackNow() {
            Slog.i(TAG, "DmsHandlerWrapper: Manually running onTimeoutAction now.");
            mRollbackAction.rollback(getRollbackData());
        }
    }


    static class UserPreferredModeRequest {
        int mDisplayId;
        @Nullable Display.Mode mMode;
        boolean mStoreMode;

        UserPreferredModeRequest(int displayId, @Nullable Display.Mode mode, boolean storeMode) {
            mDisplayId = displayId;
            mMode = mode;
            mStoreMode = storeMode;
        }
    }
}
