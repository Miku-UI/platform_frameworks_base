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

import android.annotation.Nullable;
import android.content.Context;
import android.os.RemoteException;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.Gefingerpoken;
import com.android.systemui.scene.shared.flag.SceneContainerFlag;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;
import com.android.systemui.statusbar.notification.row.ExpandableNotificationRow;
import com.android.systemui.statusbar.notification.row.ExpandableView;

/**
 * A helper class to handle touches on the heads-up views.
 */
public class HeadsUpTouchHelper implements Gefingerpoken {

    public static final String TAG = "HeadsUpTouchHelper";
    private final HeadsUpManager mHeadsUpManager;
    private final IStatusBarService mStatusBarService;
    private final Callback mCallback;
    private int mTrackingPointer;
    private final float mTouchSlop;
    private float mInitialTouchX;
    private float mInitialTouchY;
    private boolean mTouchingHeadsUpView;
    private boolean mTrackingHeadsUp;
    private boolean mCollapseSnoozes;
    private final HeadsUpNotificationViewController mPanel;
    private ExpandableNotificationRow mPickedChild;
    private String mPickedReason;

    public static final Boolean DEBUG = false;

    public static void debugLog(String s) {
        if (DEBUG) {
            android.util.Log.i(TAG, s);
        }
    }

    public void setPickedChild(
            ExpandableNotificationRow pickedChild, String reason) {
        debugLog( reason + " => setPicked");
        mPickedChild = pickedChild;
        mPickedReason = reason;
    }

    public HeadsUpTouchHelper(HeadsUpManager headsUpManager,
            IStatusBarService statusBarService,
            Callback callback,
            HeadsUpNotificationViewController notificationPanelView) {
        mHeadsUpManager = headsUpManager;
        mStatusBarService = statusBarService;
        mCallback = callback;
        mPanel = notificationPanelView;
        Context context = mCallback.getContext();
        final ViewConfiguration configuration = ViewConfiguration.get(context);
        mTouchSlop = configuration.getScaledTouchSlop();
    }

    public boolean isTrackingHeadsUp() {
        return mTrackingHeadsUp;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!mTouchingHeadsUpView && event.getActionMasked() != MotionEvent.ACTION_DOWN) {
            return false;
        }
        int pointerIndex = event.findPointerIndex(mTrackingPointer);
        if (pointerIndex < 0) {
            pointerIndex = 0;
            mTrackingPointer = event.getPointerId(pointerIndex);
        }
        final float x = event.getX(pointerIndex);
        final float y = event.getY(pointerIndex);
        String eventStr = "";
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                eventStr = "ACTION_DOWN";
                mInitialTouchY = y;
                mInitialTouchX = x;

                setTrackingHeadsUp(false, "Intercept DOWN ");
                ExpandableView child = mCallback.getChildAtRawPosition(x, y);
                mTouchingHeadsUpView = false;
                if (child instanceof ExpandableNotificationRow) {
                    ExpandableNotificationRow pickedChild = (ExpandableNotificationRow) child;
                    mTouchingHeadsUpView = !mCallback.isExpanded()
                            && pickedChild.isHeadsUp() && pickedChild.isPinned();
                    if (mTouchingHeadsUpView) {
                        setPickedChild(pickedChild, "Intercept DOWN on HUN");
                        eventStr += " on HUN";
                    }
                } else if (child == null && !mCallback.isExpanded()) {
                    // Over home screen:
                    // Touch was outside visible HUN but we still want to capture it.
                    NotificationEntry topEntry = mHeadsUpManager.getTopEntry();
                    if (topEntry != null && topEntry.isRowPinned()) {
                        setPickedChild(topEntry.getRow(), "Intercept DOWN outside HUN");
                        mTouchingHeadsUpView = true;
                        eventStr += " outside HUN";
                    }
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                eventStr = "ACTION_POINTER_UP";

                final int upPointer = event.getPointerId(event.getActionIndex());
                if (mTrackingPointer == upPointer) {
                    // gesture is ongoing, find a new pointer to track
                    final int newIndex = event.getPointerId(0) != upPointer ? 0 : 1;
                    mTrackingPointer = event.getPointerId(newIndex);
                    mInitialTouchX = event.getX(newIndex);
                    mInitialTouchY = event.getY(newIndex);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                final float h = y - mInitialTouchY;
                final boolean movedEnough = Math.abs(h) > mTouchSlop;
                final boolean downward = Math.abs(h) > Math.abs(x - mInitialTouchX);
                eventStr = "ACTION_MOVE touching:" + mTouchingHeadsUpView
                        + " movedEnough:" + movedEnough
                        + " downward:" + downward;
                if (mTouchingHeadsUpView && movedEnough && downward) {
                    if (!SceneContainerFlag.isEnabled()) {
                        setTrackingHeadsUp(true, "Intercept MOVE");
                        mCollapseSnoozes = h < 0;
                        mInitialTouchX = x;
                        mInitialTouchY = y;
                        int startHeight = (int) (mPickedChild.getActualHeight()
                                + mPickedChild.getTranslationY());
                        mPanel.setHeadsUpDraggingStartingHeight(startHeight);
                        mPanel.startExpand(x, y, true /* startTracking */, startHeight);

                        // This call needs to be after the expansion start otherwise we will get a
                        // flicker of one frame as it's not expanded yet.
                        mHeadsUpManager.unpinAll(true, "HeadsUpTouchHelper.onInterceptTouchEvent");

                        clearNotificationEffects();
                        endMotion();
                    }
                    debugLog("Intercept => " + eventStr + " => TRUE");
                    return true;
                }
                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    eventStr = "ACTION_CANCEL";
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    eventStr = "ACTION_UP";
                }
                if (mPickedChild != null && mTouchingHeadsUpView) {
                    // We may swallow this click if the heads up just came in.
                    if (mHeadsUpManager.shouldSwallowClick(
                            mPickedChild.getKey())) {
                        endMotion();
                        debugLog("Intercept UP just arrived => ");
                        return true;
                    }
                }
                endMotion();
                break;
        }
        debugLog("Intercept => " + eventStr + " => FALSE");
        return false;
    }

    private void setTrackingHeadsUp(boolean tracking, String reason) {
        mTrackingHeadsUp = tracking;
        mHeadsUpManager.setTrackingHeadsUp(tracking);
        mPanel.setTrackedHeadsUp(tracking ? mPickedChild : null, reason
                + " lastPickedBecause: " + mPickedReason);
    }

    public void notifyFling(boolean collapse) {
        if (collapse && mCollapseSnoozes) {
            mHeadsUpManager.snooze();
        }
        mCollapseSnoozes = false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        String eventStr = "";
        if (SceneContainerFlag.isEnabled()) {
            int pointerIndex = event.findPointerIndex(mTrackingPointer);
            if (pointerIndex < 0) {
                pointerIndex = 0;
                mTrackingPointer = event.getPointerId(pointerIndex);
            }
            final float x = event.getX(pointerIndex);
            final float y = event.getY(pointerIndex);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    eventStr = "ACTION_DOWN";
                    if (mTouchingHeadsUpView) {
                        debugLog("Touch => " + eventStr + " => TRUE");
                        // the HUN would like to keep listening to the gesture flow
                        return true;
                    }
                    break;

                case MotionEvent.ACTION_POINTER_UP:
                    eventStr = "ACTION_POINTER_UP";
                    final int upPointer = event.getPointerId(event.getActionIndex());
                    if (mTrackingPointer == upPointer) {
                        // gesture is ongoing, find a new pointer to track
                        final int newIndex = event.getPointerId(0) != upPointer ? 0 : 1;
                        mTrackingPointer = event.getPointerId(newIndex);
                        mInitialTouchX = event.getX(newIndex);
                        mInitialTouchY = event.getY(newIndex);
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    final float h = y - mInitialTouchY;
                    final boolean movedEnough = Math.abs(h) > mTouchSlop;
                    final boolean downward = Math.abs(h) > Math.abs(x - mInitialTouchX);
                    eventStr = "ACTION_MOVE touching:" + mTouchingHeadsUpView
                            + " movedEnough:" + movedEnough
                            + " downward:" + downward;
                    if (mTouchingHeadsUpView && movedEnough && downward) {
                        setTrackingHeadsUp(true, "Touch MOVE");
                        mCollapseSnoozes = h < 0;
                        mInitialTouchX = x;
                        mInitialTouchY = y;
                        int startHeight = (int) (mPickedChild.getActualHeight()
                                + mPickedChild.getTranslationY());
                        mPanel.setHeadsUpDraggingStartingHeight(startHeight);
                        mPanel.startExpand(x, y, true /* startTracking */, startHeight);

                        clearNotificationEffects();
                        endMotion();
                        // threshold is met, the HUN system claims the gesture and sets the NSSL to
                        // being dragged on HUN, the NSSL will dispatch the
                        mCallback.startDraggingOnHun();
                        debugLog("Touch => " + eventStr + " => TRUE => startDraggingOnHun");
                        return true;
                    }
                    break;

                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                        eventStr = "ACTION_CANCEL";
                    } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                        eventStr = "ACTION_UP";
                    }
                    if (mPickedChild != null && mTouchingHeadsUpView) {
                        // We may swallow this click if the heads up just came in.
                        if (mHeadsUpManager.shouldSwallowClick(
                                mPickedChild.getKey())) {
                            endMotion();
                            setTrackingHeadsUp(false, "Touch UP ignore click");
                            debugLog("Touch => " + eventStr + " => TRUE");
                            return true;
                        }
                    }
                    endMotion();
                    setTrackingHeadsUp(false," Touch UP complete");
                    debugLog("Touch => " + eventStr + " => FALSE");
                    return false;
            }
            debugLog("Touch => " + eventStr + " => FALSE");
            return false;
        } else {
            if (!mTrackingHeadsUp) {
                debugLog("Touch => notTrackingHun => FALSE");
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                        eventStr = "ACTION_CANCEL";
                    } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                        eventStr = "ACTION_UP";
                    }
                    endMotion();
                    setTrackingHeadsUp(false," Touch UP swipe complete");
                    break;
            }
            debugLog("Touch => " + eventStr + " => TRUE");
            return true;
        }
    }

    private void endMotion() {
        mTrackingPointer = -1;
        setPickedChild(null, "endMotion");
        mTouchingHeadsUpView = false;
    }

    private void clearNotificationEffects() {
        try {
            mStatusBarService.clearNotificationEffects();
        } catch (RemoteException e) {
            // Won't fail unless the world has ended.
        }
    }

    public interface Callback {
        ExpandableView getChildAtRawPosition(float touchX, float touchY);
        boolean isExpanded();
        Context getContext();

        /**
         * Sets the NotificationStackScrollLayout is being dragged by HUN.
         * There's no need to stopDraggingOnHun because the touchEvent is supposed to be dispatched
         * by the NSSL before it reaches here, and the NSSL will reset its own states once an UP or
         * CANCEL event is detected in NSSL.dispatchTouchEvent(MotionEvent ev).
         * <p>
         * Only used when scene container is enabled, mark that the NSSL is being dragged so start
         * dispatching the rest of the gesture to scene container.
         */
        void startDraggingOnHun();
    }

    /** The controller for a view that houses heads up notifications. */
    public interface HeadsUpNotificationViewController {
        /** Called when a HUN is dragged to indicate the starting height for shade motion. */
        void setHeadsUpDraggingStartingHeight(int startHeight);

        /** Sets notification that is being expanded. */
        void setTrackedHeadsUp(@Nullable ExpandableNotificationRow expandableNotificationRow,
                String reason);

        /** Called when a MotionEvent is about to trigger expansion. */
        void startExpand(float newX, float newY, boolean startTracking, float expandedHeight);
    }
}
