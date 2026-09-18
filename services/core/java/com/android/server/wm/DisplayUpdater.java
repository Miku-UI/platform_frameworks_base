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

package com.android.server.wm;

import static android.view.Display.INVALID_DISPLAY;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_FLAG_DISPLAY_LEVEL_TRANSITION;
import static android.window.DesktopExperienceFlags.ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT;
import static android.window.DesktopExperienceFlags.ENABLE_DISPLAY_DISCONNECT_INTERACTION;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Display;
import android.view.DisplayInfo;
import android.window.TransitionRequestInfo;
import android.window.TransitionRequestInfo.DisplayChange;

import java.util.ArrayList;
import java.util.List;

/**
 * Class responsible for receiving updates about changes in displays from DisplayManager
 * and applying these changes to WindowManager hierarchy
 */
class DisplayUpdater {

    // TODO: b/448471638 - disable by default after rolling out to nextfood
    // Verbose logging is currently enabled to debug potential issues during development
    private static final boolean DEBUG = true;

    private static final String TAG = "DisplayUpdater";

    private final RootWindowContainer mRootWindowContainer;
    private final DisplayUnblocker mDisplayUnblocker;

    DisplayUpdater(RootWindowContainer rootWindowContainer, DisplayUnblocker displayUnblocker) {
        mRootWindowContainer = rootWindowContainer;
        mDisplayUnblocker = displayUnblocker;
        mRootWindowContainer.mDisplayManager.registerDisplayListener(new DisplayUpdatesListener(),
                mRootWindowContainer.mService.mUiHandler);
    }

    /**
     * Requests an update of all displays, it will schedule retrieval of the latest displays state
     * whenever transition queue is idle, and apply the latest DisplayManager state to WindowManager
     * using a Shell transition
     *
     * @param onChangesApplied a callback that will be invoked after the changes are applied to
     *                         WM Core hierarchy (DisplayContents are updated)
     */
    void updateDisplays(@Nullable Runnable onChangesApplied) {
        final Transition transition = new Transition(TRANSIT_CHANGE,
                /* flags= */ TRANSIT_FLAG_DISPLAY_LEVEL_TRANSITION,
                mRootWindowContainer.mTransitionController,
                mRootWindowContainer.mTransitionController.mSyncEngine);

        mRootWindowContainer.mTransitionController.startCollectOrQueue(transition, deferred -> {
            final DisplayInfos displays = getNonOverrideDisplayInfos();

            if (DEBUG) {
                Slog.d(TAG, "updateDisplays: started collecting, received display infos = "
                        + displays.displayInfos());
            }

            mDisplayUnblocker.onCollectionStarted(transition, displays);
            mRootWindowContainer.mWmService.mAtmService.mChainTracker.start("dispChg", transition);
            mRootWindowContainer.mWmService.mAtmService.deferWindowLayout();

            try {
                final List<DisplayChange> displayChanges = applyDisplayInfos(transition,
                        displays.displayInfos());
                if (onChangesApplied != null) onChangesApplied.run();
                final boolean shouldStartTransition = !transition.mParticipants.isEmpty();
                updateContentModeIfNeeded(shouldStartTransition ? transition : null);

                if (shouldStartTransition) {
                    mDisplayUnblocker.onDisplayChangesApplied(transition);
                    mRootWindowContainer.mTransitionController.requestStartTransition(transition,
                            /* startTask= */ null, /* remoteTransition= */ null, displayChanges);
                } else {
                    if (DEBUG) {
                        Slog.d(TAG, "Transition aborted: no participants");
                    }
                    transition.abort();
                }
            } finally {
                mRootWindowContainer.mWmService.mAtmService.continueWindowLayout();
                mRootWindowContainer.mWmService.mAtmService.mChainTracker.end();
            }
        });
    }

    private void updateContentModeIfNeeded(@Nullable Transition transition) {
        if (!ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT.isTrue()) return;
        mRootWindowContainer.forAllDisplays(displayContent -> {
            final boolean displayContentInReparentTransition = transition != null
                    && transition.isDestinationForDisconnectDisplay(displayContent.mDisplayId);

            // If the display content is in a transition, make the
            // transition responsible for calling this.
            // Otherwise, do it now.
            if (!displayContentInReparentTransition) {
                displayContent.updateContentMode();
            }
        });
    }

    private List<DisplayChange> applyDisplayInfos(Transition transition,
            SparseArray<DisplayInfo> newDisplayInfos) {
        StringBuilder debugLog;
        if (DEBUG) {
            debugLog = new StringBuilder("applyDisplayInfos: applying changes for transition #"
                    + transition.getSyncId() + ":\n");
        }

        final List<DisplayChange> displayChanges = new ArrayList<>();
        for (int i = 0; i < newDisplayInfos.size(); i++) {
            final int displayId = newDisplayInfos.keyAt(i);
            final DisplayContent displayContent = mRootWindowContainer.getDisplayContent(displayId);
            final DisplayInfo newDisplayInfo = newDisplayInfos.valueAt(i);

            if (displayContent == null) {
                // Display is connected
                final DisplayContent display = mRootWindowContainer.getDisplayContentOrCreate(
                        displayId);
                if (display == null) {
                    continue;
                }

                display.onDisplayInfoUpdated(newDisplayInfo);
                mRootWindowContainer.setShouldShowSystemDecorationsForNewDisplay(display);
                mRootWindowContainer.startSystemDecorations(display, /* reason= */ "displayAdded");

                transition.collectExistenceChange(display);

                if (DEBUG) {
                    debugLog.append("- Added [id=").append(displayId).append("]:\n")
                            .append("    displayInfo=").append(newDisplayInfo)
                            .append("\n");
                }
            } else {
                // Display is changed
                final Rect startBounds = new Rect(displayContent.getBounds());
                final int fromRotation = displayContent.getRotation();
                final String fromUniqueId = displayContent.getDisplayInfo().uniqueId;
                final int disconnectReparentDisplayId = handleReparentOnChangeIfNeeded(transition,
                        displayContent, newDisplayInfo);

                displayContent.onDisplayInfoUpdated(newDisplayInfo);

                final DisplayChange displayChange = createDisplayChange(fromRotation, startBounds,
                        fromUniqueId, disconnectReparentDisplayId, displayContent);

                final boolean isInTransition = transition.isInTransition(displayContent);
                if (isInTransition) {
                    displayChanges.add(displayChange);
                }

                if (DEBUG) {
                    debugLog.append("- Changed [id=").append(displayId).append("]:\n")
                            .append("    displayInfo=").append(newDisplayInfo)
                            .append("    inTransition=").append(isInTransition)
                            .append("\n");
                }
            }

            transition.setReady(displayContent, /* ready= */ true);
            mRootWindowContainer.mWmService.mPossibleDisplayInfoMapper.removePossibleDisplayInfos(
                    displayId);
        }

        mRootWindowContainer.updateDisplayImePolicyCache();

        // Displays are removed
        mRootWindowContainer.forAllDisplays(displayContent -> {
            final int displayId = displayContent.getDisplayId();
            if (!newDisplayInfos.contains(displayId)) {
                if (displayContent.isDefaultDisplay) {
                    throw new IllegalArgumentException("Can't remove the primary display.");
                }

                if (!handleReparentOnRemoveIfNeeded(transition, displayContent, displayChanges)) {
                    transition.collectExistenceChange(displayContent);
                    removeDisplayContent(displayContent);
                    transition.setReady(displayContent, /* ready= */ true);
                }

                if (DEBUG) {
                    debugLog.append("- Removed [id=").append(displayId).append("]\n");
                }
            }
        });

        if (DEBUG && debugLog != null) {
            Slog.d(TAG, debugLog.toString());
        }

        return displayChanges;
    }

    /**
     * Updates the transition to reparent the display content to another display, if the current
     * display becomes unable to host tasks. This should be called before the new display info
     * is applied to display content.
     *
     * @return Target display ID where the content should be moved to, or INVALID_DISPLAY
     *         if no changes should be made
     */
    private int handleReparentOnChangeIfNeeded(Transition transition,
            DisplayContent displayContent, DisplayInfo newDisplayInfo) {
        final boolean willStopHostingTasks = ENABLE_DISPLAY_DISCONNECT_INTERACTION.isTrue()
                && !newDisplayInfo.canHostTasks && displayContent.getDisplayInfo().canHostTasks;
        if (!willStopHostingTasks) return INVALID_DISPLAY;

        // Collect the DisplayContent before running
        // onStartCollect so callers
        // can refer against whether or not it is a participant.
        transition.collect(displayContent);

        // If the display has become unable to host tasks,
        // identify a potential reparent display.
        final int reparentContentToDisplay = chooseDisplayToReparentTo();
        transition.addDisconnectReparentDisplay(newDisplayInfo.displayId,
                reparentContentToDisplay);
        return reparentContentToDisplay;
    }

    /**
     * Updates the transition to reparent the display content to another display, if the current
     * display is removed and the content needs to be moved to another display.
     *
     * @return true, if the transition was updated to handle reparenting on remove
     */
    private boolean handleReparentOnRemoveIfNeeded(Transition transition,
            DisplayContent displayContent, List<DisplayChange> outDisplayChanges) {
        if (!ENABLE_DISPLAY_DISCONNECT_INTERACTION.isTrue()
                || displayContent.shouldDestroyContentOnRemove()) return false;

        final int disconnectReparentDisplay = chooseDisplayToReparentTo();
        transition.addDisconnectReparentDisplay(displayContent.getDisplayId(),
                disconnectReparentDisplay);
        transition.collectExistenceChange(displayContent);
        transition.setAllReady();

        DisplayChange displayChange = new DisplayChange(displayContent.getDisplayId());
        displayChange.setDisconnectReparentDisplay(disconnectReparentDisplay);
        outDisplayChanges.add(displayChange);

        mRootWindowContainer.mTransitionController.mStateValidators.add(() -> {
            // Ensure the display content is removed even
            // if the transition does not successfully finish.
            removeDisplayContent(displayContent);
        });
        return true;
    }

    private void removeDisplayContent(DisplayContent displayContent) {
        if (displayContent.isRemoving() || displayContent.isRemoved()) {
            Slog.e(TAG, "DisplayContent already removed or removing.");
            return;
        }
        displayContent.remove();
        mRootWindowContainer.mWmService.mPossibleDisplayInfoMapper.removePossibleDisplayInfos(
                displayContent.mDisplayId);
    }

    @NonNull
    private TransitionRequestInfo.DisplayChange createDisplayChange(int fromRotation,
            @NonNull Rect startBounds, @Nullable String fromUniqueId, int disconnectReparentDisplay,
            @NonNull DisplayContent displayContent) {
        final int toRotation = displayContent.getRotation();
        final boolean physicalDisplayChanged = fromUniqueId != null
                && !fromUniqueId.equals(displayContent.getDisplayInfo().uniqueId);
        final TransitionRequestInfo.DisplayChange displayChange =
                new TransitionRequestInfo.DisplayChange(displayContent.getDisplayAreaInfo());
        displayChange.setStartAbsBounds(startBounds);
        displayChange.setStartRotation(fromRotation);
        displayChange.setDisconnectReparentDisplay(disconnectReparentDisplay);
        displayChange.setPhysicalDisplayChanged(physicalDisplayChanged);
        if (com.android.window.flags.Flags.sendNewInsetsStateWithRotation()) {
            displayChange.setEndInsetsState(displayContent.getInsetsStateForRotation(toRotation));
        }
        return displayChange;
    }

    private int chooseDisplayToReparentTo() {
        final ActivityTaskManagerService atmService = mRootWindowContainer.mWmService.mAtmService;
        final int disconnectReparentDisplay =
                atmService.getUserManagerInternal().getMainDisplayAssignedToUser(
                        atmService.getCurrentUserId());
        return disconnectReparentDisplay == INVALID_DISPLAY ? Display.DEFAULT_DISPLAY
                : disconnectReparentDisplay;
    }

    // TODO: b/448471638 - this is just a placeholder implementation of DisplayManager future API,
    //  replace with the actual calls when DisplayManager is ready
    record DisplayInfos(SparseArray<DisplayInfo> displayInfos) {}

    private DisplayInfos getNonOverrideDisplayInfos() {
        final SparseArray<DisplayInfo> displayInfos = new SparseArray<>();
        final int[] displayIds = mRootWindowContainer.mDisplayManagerInternal
                        .getDisplayIds(/* includeDisabled= */ false);
        for (int i = 0; i < displayIds.length; i++) {
            final int displayId = displayIds[i];
            final DisplayInfo displayInfo = new DisplayInfo();
            mRootWindowContainer.mDisplayManagerInternal.getNonOverrideDisplayInfo(displayId,
                    displayInfo);
            displayInfos.put(displayId, displayInfo);
        }
        return new DisplayInfos(displayInfos);
    }

    private class DisplayUpdatesListener implements DisplayManager.DisplayListener {
        @Override
        public void onDisplayAdded(int displayId) {
            synchronized (mRootWindowContainer.mService.mGlobalLock) {
                updateDisplays(null);
            }
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            synchronized (mRootWindowContainer.mService.mGlobalLock) {
                updateDisplays(null);
            }
        }

        @Override
        public void onDisplayChanged(int displayId) {
            synchronized (mRootWindowContainer.mService.mGlobalLock) {
                updateDisplays(null);
            }
        }
    }
}
