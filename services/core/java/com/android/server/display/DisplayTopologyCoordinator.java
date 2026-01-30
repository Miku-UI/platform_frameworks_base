/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.annotation.Nullable;
import android.hardware.display.DisplayTopology;
import android.hardware.display.DisplayTopologyGraph;
import android.os.Trace;
import android.util.IndentingPrintWriter;
import android.util.Pair;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Display;
import android.view.DisplayInfo;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.display.feature.DisplayManagerFlags;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Manages the relative placement (topology) of extended displays. Responsible for updating and
 * persisting the topology.
 */
class DisplayTopologyCoordinator {
    private static final String TAG = "DisplayTopologyCoordinator";

    @Nullable
    private static String getUniqueId(DisplayInfo info) {
        if (info.displayId == Display.DEFAULT_DISPLAY && info.type == Display.TYPE_INTERNAL) {
            return "internal";
        }
        return info.uniqueId;
    }

    // Persistent data store for display topologies.
    private final DisplayTopologyStore mTopologyStore;

    @GuardedBy("mSyncRoot")
    private DisplayTopology mTopology;

    @GuardedBy("mSyncRoot")
    private final Map<String, Integer> mUniqueIdToDisplayIdMapping = new HashMap<>();

    @GuardedBy("mSyncRoot")
    private final SparseArray<String> mDisplayIdToUniqueIdMapping = new SparseArray<>();

    /**
     * Check if extended displays are allowed. If not, a topology is not needed.
     */
    private final BooleanSupplier mIsExtendedDisplayAllowed;

    /**
     * Check if the default display should be included in the topology when there are other displays
     * present. If not, remove the default when another display is added, and add the default
     * display back to the topology when all other displays are removed.
     */
    private final BooleanSupplier mShouldIncludeDefaultDisplayInTopology;

    /**
     * Callback used to send topology updates.
     * Should be invoked from the corresponding executor.
     * A copy of the topology should be sent that will not be modified by the system.
     */
    private final Consumer<Pair<DisplayTopology, DisplayTopologyGraph>> mOnTopologyChangedCallback;
    private final Executor mTopologyChangeExecutor;
    private final DisplayManagerService.SyncRoot mSyncRoot;
    private final Runnable mTopologySavedCallback;
    private final DisplayManagerFlags mFlags;
    private final DisplayManagerService.DisplayInfoProvider mDisplayInfoProvider;

    DisplayTopologyCoordinator(BooleanSupplier isExtendedDisplayAllowed,
            BooleanSupplier shouldIncludeDefaultDisplayInTopology,
            Consumer<Pair<DisplayTopology, DisplayTopologyGraph>> onTopologyChangedCallback,
            Executor topologyChangeExecutor, DisplayManagerService.SyncRoot syncRoot,
            Runnable topologySavedCallback, DisplayManagerFlags flags,
            DisplayManagerService.DisplayInfoProvider displayInfoProvider) {
        this(new Injector(), isExtendedDisplayAllowed, shouldIncludeDefaultDisplayInTopology,
                onTopologyChangedCallback, topologyChangeExecutor, syncRoot, topologySavedCallback,
                flags, displayInfoProvider);
    }

    @VisibleForTesting
    DisplayTopologyCoordinator(Injector injector, BooleanSupplier isExtendedDisplayAllowed,
            BooleanSupplier shouldIncludeDefaultDisplayInTopology,
            Consumer<Pair<DisplayTopology, DisplayTopologyGraph>> onTopologyChangedCallback,
            Executor topologyChangeExecutor, DisplayManagerService.SyncRoot syncRoot,
            Runnable topologySavedCallback, DisplayManagerFlags flags,
            DisplayManagerService.DisplayInfoProvider displayInfoProvider) {
        mTopology = injector.getTopology();
        mIsExtendedDisplayAllowed = isExtendedDisplayAllowed;
        mShouldIncludeDefaultDisplayInTopology = shouldIncludeDefaultDisplayInTopology;
        mOnTopologyChangedCallback = onTopologyChangedCallback;
        mTopologyChangeExecutor = topologyChangeExecutor;
        mSyncRoot = syncRoot;
        mTopologyStore = injector.createTopologyStore(
                mDisplayIdToUniqueIdMapping, mUniqueIdToDisplayIdMapping);
        mTopologySavedCallback = topologySavedCallback;
        mFlags = flags;
        mDisplayInfoProvider = displayInfoProvider;
    }

    /**
     * Add a display to the topology.
     * @param info The display info
     */
    void onDisplayAdded(DisplayInfo info) {
        if (!isDisplayAllowedInTopology(info, /* shouldLog= */ true)) {
            return;
        }
        synchronized (mSyncRoot) {
            addDisplayIdMappingLocked(info);
            mTopology.addDisplay(
                    info.displayId, info.logicalWidth, info.logicalHeight, info.logicalDensityDpi);
            Slog.i(TAG, "Display " + info.displayId + " added, new topology: " + mTopology);
            restoreTopologyLocked();
            sendTopologyUpdateLocked();
        }

        if (mFlags.isDefaultDisplayInTopologySwitchEnabled()) {
            // If the default display should not be included in the topology, then when a
            // non-default display is added, remove the default display from the topology.
            if (info.displayId != Display.DEFAULT_DISPLAY
                    && !mShouldIncludeDefaultDisplayInTopology.getAsBoolean()
                    && mTopology.hasMultipleDisplays()) {
                onDisplayRemoved(Display.DEFAULT_DISPLAY);
            }
        }
    }

    /**
     * Update the topology with display changes.
     * @param info The new display info
     */
    void onDisplayChanged(DisplayInfo info) {
        if (!isDisplayAllowedInTopology(info)) {
            return;
        }
        synchronized (mSyncRoot) {
            boolean topologyUpdated = mTopology.updateDisplay(info.displayId, info.logicalWidth,
                    info.logicalHeight, info.logicalDensityDpi);

            String uniqueId = getUniqueId(info);
            String oldUniqueId = mDisplayIdToUniqueIdMapping.get(info.displayId);
            if (uniqueId != null && oldUniqueId != null && !uniqueId.equals(oldUniqueId)) {
                addDisplayIdMappingLocked(info);

                // Restore the displays' positions by unique ID
                topologyUpdated |= restoreTopologyLocked();
            }

            if (topologyUpdated) {
                sendTopologyUpdateLocked();
            }
        }
    }

    /**
     * Remove a display from the topology.
     * @param displayId The logical display ID
     */
    void onDisplayRemoved(int displayId) {
        synchronized (mSyncRoot) {
            if (mTopology.removeDisplay(displayId)) {
                Slog.i(TAG, "Display " + displayId + " removed, new topology: " + mTopology);
                removeDisplayIdMappingLocked(displayId);
                restoreTopologyLocked();
                sendTopologyUpdateLocked();
            }
        }

        // If the default display should not be included in the topology, then when all non-default
        // displays are removed, add the default display back to the topology.
        if (mFlags.isDefaultDisplayInTopologySwitchEnabled()) {
            if (displayId != Display.DEFAULT_DISPLAY
                    && !mShouldIncludeDefaultDisplayInTopology.getAsBoolean()
                    && mTopology.isEmpty()) {
                onDisplayAdded(mDisplayInfoProvider.get(Display.DEFAULT_DISPLAY));
            }
        }
    }

    /**
     * Loads all topologies from the persistent topology store for the given userId.
     * @param userId the user id, same as returned from
     *              {@link android.app.ActivityManagerInternal#getCurrentUserId()}.
     * @param isUserSwitching whether the id of the user is currently switching.
     */
    void reloadTopologies(int userId, boolean isUserSwitching) {
        boolean isTopologySaved = false;
        synchronized (mSyncRoot) {
            mTopologyStore.reloadTopologies(userId);
            boolean isTopologyRestored = restoreTopologyLocked();
            if (isTopologyRestored) {
                sendTopologyUpdateLocked();
            }
            if (isUserSwitching && !isTopologyRestored) {
                // During user switch, if topology is not restored - last user topology is the
                // good initial guess. Save this topology for consistent use in the future.
                isTopologySaved = mTopologyStore.saveTopology(mTopology);
            }
        }

        if (isTopologySaved) {
            mTopologySavedCallback.run();
        }
    }

    /**
     * @return A deep copy of the topology.
     */
    DisplayTopology getTopology() {
        synchronized (mSyncRoot) {
            return mTopology.copy();
        }
    }

    void setTopology(DisplayTopology topology) {
        final boolean isTopologySaved;
        synchronized (mSyncRoot) {
            Trace.traceBegin(Trace.TRACE_TAG_POWER, "setTopology");
            try {
                topology.normalize();
                mTopology = topology;
                sendTopologyUpdateLocked();
                isTopologySaved = mTopologyStore.saveTopology(topology);
            } finally {
                Trace.traceEnd(Trace.TRACE_TAG_POWER);
            }
        }

        if (isTopologySaved) {
            mTopologySavedCallback.run();
        }
    }

    /**
     * Print the object's state and debug information into the given stream.
     * @param pw The stream to dump information to.
     */
    void dump(PrintWriter pw) {
        pw.println("Display Topology Coordinator:");
        pw.println("----------------------------------------");
        IndentingPrintWriter idpw = new IndentingPrintWriter(pw);
        idpw.increaseIndent();
        synchronized (mSyncRoot) {
            idpw.println("isExtendedDisplayAllowed=" + mIsExtendedDisplayAllowed.getAsBoolean());
            idpw.println("shouldIncludeDefaultDisplayInTopology="
                    + mShouldIncludeDefaultDisplayInTopology.getAsBoolean());
            mTopology.dump(idpw);
        }
    }

    @GuardedBy("mSyncRoot")
    private void removeDisplayIdMappingLocked(final int displayId) {
        final String uniqueId = mDisplayIdToUniqueIdMapping.get(displayId);
        if (null == uniqueId) {
            Slog.e(TAG, "Can't find uniqueId for displayId=" + displayId);
            return;
        }
        mDisplayIdToUniqueIdMapping.remove(displayId);
        mUniqueIdToDisplayIdMapping.remove(uniqueId);
    }

    @GuardedBy("mSyncRoot")
    private void addDisplayIdMappingLocked(DisplayInfo info) {
        final String uniqueId = getUniqueId(info);
        if (null == uniqueId) {
            Slog.e(TAG, "Can't find uniqueId for displayId=" + info.displayId);
            return;
        }
        mUniqueIdToDisplayIdMapping.put(uniqueId, info.displayId);
        mDisplayIdToUniqueIdMapping.put(info.displayId, uniqueId);
    }

    boolean isDisplayAllowedInTopology(DisplayInfo info) {
        return isDisplayAllowedInTopology(info, /* shouldLog= */ false);
    }

    private boolean isDisplayAllowedInTopology(DisplayInfo info, boolean shouldLog) {
        if (info == null) {
            return false;
        }
        if (info.type != Display.TYPE_INTERNAL && info.type != Display.TYPE_EXTERNAL
                && info.type != Display.TYPE_OVERLAY) {
            if (shouldLog) {
                Slog.d(TAG, "Display " + info.displayId + " not allowed in topology because "
                        + "type is not INTERNAL, EXTERNAL or OVERLAY");
            }
            return false;
        }
        if (info.type == Display.TYPE_INTERNAL && info.displayId != Display.DEFAULT_DISPLAY) {
            if (shouldLog) {
                Slog.d(TAG, "Display " + info.displayId + " not allowed in topology because "
                        + "it is a non-default internal display");
            }
            return false;
        }
        if ((info.type == Display.TYPE_EXTERNAL || info.type == Display.TYPE_OVERLAY)
                && !mIsExtendedDisplayAllowed.getAsBoolean()) {
            if (shouldLog) {
                Slog.d(TAG, "Display " + info.displayId + " not allowed in topology because "
                        + "type is EXTERNAL or OVERLAY and !mIsExtendedDisplayAllowed");
            }
            return false;
        }
        return true;
    }

    /**
     * Restores {@link #mTopology} from {@link #mTopologyStore}, saves it in {@link #mTopology}.
     * @return true if the topology was restored, false otherwise.
     */
    @GuardedBy("mSyncRoot")
    private boolean restoreTopologyLocked() {
        var restoredTopology = mTopologyStore.restoreTopology(mTopology);
        if (restoredTopology == null) {
            return false;
        }
        mTopology = restoredTopology;
        mTopology.normalize();
        return true;
    }

    @GuardedBy("mSyncRoot")
    private void sendTopologyUpdateLocked() {
        DisplayTopology copy = mTopology.copy();
        mTopologyChangeExecutor.execute(() -> {
            Trace.traceBegin(Trace.TRACE_TAG_POWER, "sendTopologyUpdateLocked");
            try {
                mOnTopologyChangedCallback.accept(new Pair<>(copy, copy.getGraph()));
            } finally {
                Trace.traceEnd(Trace.TRACE_TAG_POWER);
            }
        });
    }

    @VisibleForTesting
    static class Injector {
        DisplayTopology getTopology() {
            return new DisplayTopology();
        }

        DisplayTopologyStore createTopologyStore(
                SparseArray<String> displayIdToUniqueIdMapping,
                Map<String, Integer> uniqueIdToDisplayIdMapping) {
            return new DisplayTopologyXmlStore(new DisplayTopologyXmlStore.Injector() {
                @Override
                public SparseArray<String> getDisplayIdToUniqueIdMapping() {
                    return displayIdToUniqueIdMapping;
                }

                @Override
                public Map<String, Integer> getUniqueIdToDisplayIdMapping() {
                    return uniqueIdToDisplayIdMapping;
                }
            });
        }
    }
}
