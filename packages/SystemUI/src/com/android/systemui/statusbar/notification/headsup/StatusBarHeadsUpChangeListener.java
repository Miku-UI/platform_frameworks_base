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

package com.android.systemui.statusbar.notification.headsup;

import static com.android.systemui.util.kotlin.FlowKt.pairwise;
import static com.android.systemui.util.kotlin.JavaAdapterKt.collectFlow;

import androidx.annotation.Nullable;

import com.android.systemui.CoreStartable;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.dagger.qualifiers.Application;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.scene.shared.flag.SceneContainerFlag;
import com.android.systemui.shade.ShadeViewController;
import com.android.systemui.shade.domain.interactor.PanelExpansionInteractor;
import com.android.systemui.shade.domain.interactor.ShadeDisplaysInteractor;
import com.android.systemui.statusbar.NotificationRemoteInputManager;
import com.android.systemui.statusbar.NotificationShadeWindowController;
import com.android.systemui.statusbar.StatusBarState;
import com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController;
import com.android.systemui.statusbar.phone.KeyguardBypassController;
import com.android.systemui.statusbar.window.StatusBarWindowController;
import com.android.systemui.statusbar.window.StatusBarWindowControllerStore;

import kotlinx.coroutines.CoroutineScope;

import javax.inject.Inject;

/**
 * Ties the status bar to {@link HeadsUpManager}.
 */
@SysUISingleton
public class StatusBarHeadsUpChangeListener implements OnHeadsUpChangedListener, CoreStartable {
    private final NotificationShadeWindowController mNotificationShadeWindowController;
    private final StatusBarWindowControllerStore mStatusBarWindowControllerStore;
    private final ShadeViewController mShadeViewController;
    private final PanelExpansionInteractor mPanelExpansionInteractor;
    private final NotificationStackScrollLayoutController mNsslController;
    private final KeyguardBypassController mKeyguardBypassController;
    private final HeadsUpManager mHeadsUpManager;
    private final StatusBarStateController mStatusBarStateController;
    private final NotificationRemoteInputManager mNotificationRemoteInputManager;
    private final ShadeDisplaysInteractor mShadeDisplaysInteractor;
    private final CoroutineScope mApplicationScope;

    @Inject
    StatusBarHeadsUpChangeListener(
            NotificationShadeWindowController notificationShadeWindowController,
            StatusBarWindowControllerStore statusBarWindowControllerStore,
            ShadeViewController shadeViewController,
            PanelExpansionInteractor panelExpansionInteractor,
            NotificationStackScrollLayoutController nsslController,
            KeyguardBypassController keyguardBypassController,
            HeadsUpManager headsUpManager,
            StatusBarStateController statusBarStateController,
            NotificationRemoteInputManager notificationRemoteInputManager,
            ShadeDisplaysInteractor shadeDisplaysInteractor,
            @Application CoroutineScope applicationScope) {
        mNotificationShadeWindowController = notificationShadeWindowController;
        mStatusBarWindowControllerStore = statusBarWindowControllerStore;
        mShadeViewController = shadeViewController;
        mPanelExpansionInteractor = panelExpansionInteractor;
        mNsslController = nsslController;
        mKeyguardBypassController = keyguardBypassController;
        mHeadsUpManager = headsUpManager;
        mStatusBarStateController = statusBarStateController;
        mNotificationRemoteInputManager = notificationRemoteInputManager;
        mShadeDisplaysInteractor = shadeDisplaysInteractor;
        mApplicationScope = applicationScope;
    }

    @Override
    public void start() {
        mHeadsUpManager.addListener(this);
        listenForShadeDisplayChanges();
    }

    private void listenForShadeDisplayChanges() {
        collectFlow(
                mApplicationScope,
                pairwise(mShadeDisplaysInteractor.getDisplayId(), getCurrentDisplayId()),
                (displayChange) -> {
                    if (SceneContainerFlag.isEnabled()) {
                        // This behavior is not replicated in SceneContainer, but we want to remove
                        // the force-showing of StatusBar during HUNs in the same release.
                        // See b/398673309
                        return;
                    }
                    int previousDisplayId = displayChange.getPreviousValue();
                    int newDisplayId = displayChange.getNewValue();
                    if (previousDisplayId == newDisplayId) {
                        return;
                    }
                    if (!mHeadsUpManager.hasPinnedHeadsUp()) {
                        return;
                    }
                    // A HUN is active, and the display is changing. This means the HUN will move
                    // displays. We need to transfer the forced-visible state from the old display
                    // to the new one.
                    String logString = "HeadsUpChangeListener#onDisplayChanged";
                    StatusBarWindowController oldController =
                            mStatusBarWindowControllerStore.forDisplay(previousDisplayId);
                    if (oldController != null) {
                        oldController.setForceStatusBarVisible(false, logString);
                    }

                    StatusBarWindowController newController =
                            mStatusBarWindowControllerStore.forDisplay(newDisplayId);
                    if (newController != null) {
                        newController.setForceStatusBarVisible(true, logString);
                    }
                });
    }

    @Override
    public void onHeadsUpPinnedModeChanged(boolean inPinnedMode) {
        StatusBarWindowController statusBarWindowController =
                getStatusBarWindowControllerForCurrentDispplay();
        String source = "HeadsUpChangeListener#onHeadsUpPinnedModeChanged";
        if (inPinnedMode) {
            mNotificationShadeWindowController.setHeadsUpShowing(true);
            // This behavior is not replicated in SceneContainer, but we want to remove the
            // force-showing of StatusBar during HUNs in the same release. See b/398673309
            if (!SceneContainerFlag.isEnabled() && statusBarWindowController != null) {
                statusBarWindowController.setForceStatusBarVisible(true, source);
            }
            if (mPanelExpansionInteractor.isFullyCollapsed()) {
                mShadeViewController.updateTouchableRegion();
            }
        } else {
            boolean bypassKeyguard = mKeyguardBypassController.getBypassEnabled()
                    && mStatusBarStateController.getState() == StatusBarState.KEYGUARD;
            if (!mPanelExpansionInteractor.isFullyCollapsed()
                    || mPanelExpansionInteractor.isTracking()
                    || bypassKeyguard) {
                // We are currently tracking or is open and the shade doesn't need to
                //be kept
                // open artificially.
                mNotificationShadeWindowController.setHeadsUpShowing(false);
                if (bypassKeyguard) {
                    // This behavior is not replicated in SceneContainer, but we want to remove the
                    // force-showing of StatusBar during HUNs in the same release. See b/398673309
                    if (!SceneContainerFlag.isEnabled() && statusBarWindowController != null) {
                        statusBarWindowController.setForceStatusBarVisible(false, source);
                    }
                }
            } else {
                // we need to keep the panel open artificially, let's wait until the
                //animation
                // is finished.
                setHeadsAnimatingAway(true);
                mNsslController.runAfterAnimationFinished(() -> {
                    if (!mHeadsUpManager.hasPinnedHeadsUp()) {
                        mNotificationShadeWindowController.setHeadsUpShowing(false);
                        setHeadsAnimatingAway(false);
                    }
                    mNotificationRemoteInputManager.onPanelCollapsed();
                });
            }
        }
    }

    @Nullable
    private StatusBarWindowController getStatusBarWindowControllerForCurrentDispplay() {
        return mStatusBarWindowControllerStore.forDisplay(getCurrentDisplayId());
    }

    private int getCurrentDisplayId() {
        return mShadeDisplaysInteractor.getDisplayId().getValue();
    }

    private void setHeadsAnimatingAway(boolean headsUpAnimatingAway) {
        if (!SceneContainerFlag.isEnabled()) {
            mHeadsUpManager.setHeadsUpAnimatingAway(headsUpAnimatingAway);
        }
    }
}
