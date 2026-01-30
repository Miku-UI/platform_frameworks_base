/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.view.InsetsSource.ID_IME;

import static com.android.internal.protolog.WmProtoLogGroups.WM_DEBUG_IME;
import static com.android.server.wm.ImeInsetsSourceProviderProto.INSETS_SOURCE_PROVIDER;
import static com.android.server.wm.WindowManagerService.H.UPDATE_MULTI_WINDOW_STACKS;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.graphics.Rect;
import android.os.IBinder;
import android.util.Slog;
import android.util.proto.ProtoOutputStream;
import android.view.InsetsSource;
import android.view.InsetsSourceConsumer;
import android.view.InsetsSourceControl;
import android.view.WindowInsets;
import android.view.inputmethod.Flags;
import android.view.inputmethod.ImeTracker;

import com.android.internal.inputmethod.SoftInputShowHideReason;
import com.android.internal.protolog.ProtoLog;

import java.io.PrintWriter;

/**
 * Controller for IME inset source on the server. It's called provider as it provides the
 * {@link InsetsSource} to the client that uses it in {@link InsetsSourceConsumer}.
 */
final class ImeInsetsSourceProvider extends InsetsSourceProvider {

    private static final String TAG = ImeInsetsSourceProvider.class.getSimpleName();

    /**
     * The token tracking the show/hide IME request, only used while the IME notified about it being
     * drawn and dispatching the controls. Could be overwritten by another coincident request.
     */
    @Nullable
    private ImeTracker.Token mStatsToken;
    /** @see #isImeShowing() */
    private boolean mImeShowing;
    /** The latest received insets source. */
    @NonNull
    private final InsetsSource mLastSource = new InsetsSource(ID_IME, WindowInsets.Type.ime());

    /** @see #setFrozen(boolean) */
    private boolean mFrozen;

    /**
     * The server visibility of the source provider's window. This is out of sync with
     * {@link InsetsSourceProvider#mServerVisible} while {@link #mFrozen} is {@code true}.
     *
     * @see #setServerVisible
     */
    private boolean mServerVisible;

    /**
     * The server visibility of the source provider's window before the latest
     * {@link #onPreLayout} call.
     */
    private boolean mServerVisiblePreLayout;

    /**
     * When the IME is not ready, it has givenInsetsPending. However, this could happen again,
     * after it became serverVisible. This flag indicates is used to determine if it is
     * readyForDispatching
     */
    private boolean mGivenInsetsReady = false;

    /**
     * The last drawn state of the window. This is used to reset server visibility, in case of
     * the IME (temporarily) redrawing  (e.g. during a rotation), to dispatch the control with
     * leash again after it has finished drawing.
     */
    private boolean mLastDrawn = false;

    ImeInsetsSourceProvider(@NonNull InsetsSource source,
            @NonNull InsetsStateController stateController,
            @NonNull DisplayContent displayContent) {
        super(source, stateController, displayContent);
    }

    @Override
    void onPreLayout() {
        if (!android.view.inputmethod.Flags.setServerVisibilityOnprelayout()) {
            return;
        }
        mServerVisiblePreLayout = mServerVisible;
        super.onPreLayout();

        mLastDrawn = mWin != null && mWin.isDrawn();
    }

    @Override
    boolean onPostLayout() {
        final boolean wasSourceVisible = mSource.isVisible();
        final boolean controlDispatched = super.onPostLayout();
        if (!android.view.inputmethod.Flags.setServerVisibilityOnprelayout()
                && wasSourceVisible != mSource.isVisible()) {
            // TODO(b/427863960): Remove this and set the server visibility in onPreLayout
            // If the IME visibility has changed, a traversal needs to apply.
            mDisplayContent.setLayoutNeeded();
        }

        final boolean givenInsetsPending = mWin != null && mWin.mGivenInsetsPending;
        if (!android.view.inputmethod.Flags.setServerVisibilityOnprelayout()) {
            mLastDrawn = mWin != null && mWin.isDrawn();
        }
        // isLeashReadyForDispatching (used to dispatch the leash of the control) is
        // depending on mGivenInsetsReady. Therefore, triggering notifyControlChanged here
        // again, so that the control with leash can be eventually dispatched
        if (!mGivenInsetsReady && isServerVisible() && !givenInsetsPending
                && mControlTarget != null) {
            ProtoLog.d(WM_DEBUG_IME,
                    "onPostLayout: IME control ready to be dispatched, controlTarget=%s",
                    mControlTarget);
            mGivenInsetsReady = true;
            ImeTracker.forLogging().onProgress(mStatsToken,
                    ImeTracker.PHASE_WM_POST_LAYOUT_NOTIFY_CONTROLS_CHANGED);
            if (!controlDispatched) {
                mStateController.notifyControlChanged(mControlTarget, this);
            }
            setImeShowing(true);
            return true;
        } else if (((!android.view.inputmethod.Flags.setServerVisibilityOnprelayout()
                && wasSourceVisible) || mServerVisiblePreLayout) && isServerVisible()
                && mGivenInsetsReady && givenInsetsPending) {
            // If the server visibility didn't change (still visible), and mGivenInsetsReady
            // is set, we won't call into notifyControlChanged. Therefore, we can reset the
            // statsToken, if available.
            ProtoLog.w(WM_DEBUG_IME, "onPostLayout cancel statsToken, controlTarget=%s",
                    mControlTarget);
            ImeTracker.forLogging().onCancelled(mStatsToken,
                    ImeTracker.PHASE_WM_POST_LAYOUT_NOTIFY_CONTROLS_CHANGED);
            mStatsToken = null;
        } else if (!isServerVisible()) {
            if (isImeShowing()) {
                ProtoLog.d(WM_DEBUG_IME,
                        "onPostLayout: setImeShowing(false) was: true, controlTarget=%s",
                        mControlTarget);
                setImeShowing(false);
            }
            if (android.view.inputmethod.Flags.setServerVisibilityOnprelayout()
                    && mControlTarget != null && mServerVisiblePreLayout && !controlDispatched) {
                // If the server visibility changed (not visible anymore), we need to dispatch
                // the control.
                mStateController.notifyControlChanged(mControlTarget, this);
                return true;
            }
        }
        return controlDispatched;
    }

    @Nullable
    ImeTracker.Token getAndClearStatsToken() {
        ImeTracker.Token statsToken = mStatsToken;
        mStatsToken = null;
        return statsToken;
    }

    @Override
    protected boolean isLeashReadyForDispatching() {
        // We should only dispatch the leash, if the following conditions are fulfilled:
        // 1. parent isLeashReadyForDispatching, 2. mGivenInsetsReady (means there are no
        // givenInsetsPending), 3. the IME surface is drawn, 4. either the IME is
        // serverVisible (the unfrozen state)
        final boolean isDrawn = mWin != null && mWin.isDrawn();
        return super.isLeashReadyForDispatching()
                && isServerVisible() && isDrawn && mGivenInsetsReady;
    }

    /**
     * This is used to determine the desired serverVisibility state. For the IME, just having a
     * window state that would be visible by policy is not enough.
     */
    @Override
    protected boolean isSurfaceVisible() {
        final boolean isSurfaceVisible = super.isSurfaceVisible();
        if (mControl != null) {
            final boolean isDrawn = mWin != null && mWin.isDrawn();
            if (!isServerVisible() && isSurfaceVisible) {
                // In case the IME becomes visible, we need to check if it is already drawn and
                // does not have given insets pending. If it's not yet drawn, we do not set
                // server visibility
                return isDrawn && !mWin.mGivenInsetsPending;
            } else if (mLastDrawn && !isDrawn) {
                // If the IME was drawn before, but is not drawn anymore, we need to reset
                // server visibility, which will also reset {@link
                // ImeInsetsSourceProvider#mGivenInsetsReady}. Otherwise, the new control
                // with leash won't be dispatched after the surface has redrawn.
                return false;
            }
        }
        return isSurfaceVisible;
    }

    @Override
    protected boolean isInitiallyVisible(@NonNull InsetsControlTarget target) {
        boolean initiallyVisible = super.isInitiallyVisible(target);
        // parent initiallyVisible is true, if it is client visible.
        if (initiallyVisible && mServerVisible) {
            WindowContainer<?> imeParentWindow = mDisplayContent.getImeParentWindow();
            // If the IME is attached to an app window, only consider it initially visible
            // if the parent is visible and wasn't part of a transition.
            initiallyVisible =
                    imeParentWindow != null && !imeParentWindow.inTransition()
                            && imeParentWindow.isVisible()
                            && imeParentWindow.isVisibleRequested();
        } else {
            initiallyVisible = false;
        }
        if (android.view.inputmethod.Flags.unifySkipAnimationOnceWithInitiallyVisible()
                && !initiallyVisible && target.getWindow() != null) {
            final var targetWin = target.getWindow();
            final Task task = targetWin.getTask();
            // If the control target has a starting window, and its snapshot was captured while
            // the IME was visible, skip the next IME show animation on the IME source control,
            // to gracefully restore the IME visibility.
            StartingData startingData = null;
            if (task != null) {
                startingData = targetWin.mActivityRecord.mStartingData;
                if (startingData == null) {
                    final WindowState startingWin = task.topStartingWindow();
                    if (startingWin != null) {
                        startingData = startingWin.mStartingData;
                    }
                }
                initiallyVisible = startingData != null && startingData.hasImeSurface();
                ProtoLog.d(WM_DEBUG_IME, "Overwriting initiallyVisible %s for %s",
                        initiallyVisible, targetWin);
            }
        }
        return initiallyVisible;
    }

    @Nullable
    @Override
    InsetsSourceControl getControl(@NonNull InsetsControlTarget target) {
        final InsetsSourceControl control = super.getControl(target);
        final WindowState targetWin = target.getWindow();
        if (!android.view.inputmethod.Flags.unifySkipAnimationOnceWithInitiallyVisible()
                && control != null && targetWin != null) {
            final Task task = targetWin.getTask();
            // If the control target has a starting window, and its snapshot was captured while
            // the IME was visible, skip the next IME show animation on the IME source control,
            // to gracefully restore the IME visibility.
            StartingData startingData = null;
            if (task != null) {
                startingData = targetWin.mActivityRecord.mStartingData;
                if (startingData == null) {
                    final WindowState startingWin = task.topStartingWindow();
                    if (startingWin != null) {
                        startingData = startingWin.mStartingData;
                    }
                }
            }
            control.setSkipAnimationOnce(startingData != null && startingData.hasImeSurface());
        }
        if (control != null && control.getLeash() != null) {
            ImeTracker.Token statsToken = getAndClearStatsToken();
            if (statsToken == null) {
                ProtoLog.w(WM_DEBUG_IME,
                        "IME getControl without statsToken (check previous request!). "
                                + "Start new request");
                // TODO(b/353463205) remove this later after fixing the race of two requests
                //  that cancel each other (cf. b/383466954#comment19).
                statsToken = ImeTracker.forLogging().onStart(ImeTracker.TYPE_SHOW,
                        ImeTracker.ORIGIN_SERVER, SoftInputShowHideReason.CONTROLS_CHANGED,
                        false /* fromUser */);
            }
            ImeTracker.forLogging().onProgress(statsToken,
                    ImeTracker.PHASE_WM_GET_CONTROL_WITH_LEASH);
            control.setImeStatsToken(statsToken);
        }
        return control;
    }

    @Override
    void setClientVisible(boolean clientVisible) {
        final boolean wasClientVisible = isClientVisible();
        super.setClientVisible(clientVisible);
        // The layer of ImePlaceholder needs to be updated on a higher z-order for
        // non-activity window (For activity window, IME is already on top of it).
        if (!wasClientVisible && isClientVisible()) {
            final InsetsControlTarget imeControlTarget = getControlTarget();
            if (imeControlTarget != null && imeControlTarget.getWindow() != null
                    && imeControlTarget.getWindow().mActivityRecord == null) {
                mDisplayContent.assignWindowLayers(false /* setLayoutNeeded */);
            }
        }
    }

    @Override
    void setServerVisible(boolean serverVisible) {
        if (mServerVisible != serverVisible) {
            mServerVisible = serverVisible;
            // reset the leash if the server visibility becomes hidden
            if (!serverVisible && !mFrozen) {
                mGivenInsetsReady = false;
                updateControlForTarget(mControlTarget, true /* force */, null /* statsToken */);
            }
        }
        if (!mFrozen) {
            super.setServerVisible(serverVisible);
        }
    }

    /**
     * Freeze IME insets source state when required.
     *
     * <p>When setting {@param frozen} as {@code true}, the IME insets provider will freeze the
     * current IME insets state and pending the IME insets state update until setting
     * {@param frozen} as {@code false}.</p>
     */
    void setFrozen(boolean frozen) {
        if (mFrozen == frozen) {
            return;
        }
        mFrozen = frozen;
        if (!frozen) {
            // Unfreeze and process the pending IME insets states.
            super.setServerVisible(mServerVisible);
        }
    }

    @Override
    void updateSourceFrame(@NonNull Rect frame) {
        super.updateSourceFrame(frame);
        onSourceChanged();
    }

    @Override
    protected void updateVisibility() {
        boolean oldVisibility = mSource.isVisible();
        super.updateVisibility();
        if (mSource.isVisible() && !oldVisibility && mControlTarget != null) {
            reportImeDrawnForOrganizerIfNeeded(mControlTarget);
        }
        onSourceChanged();
    }

    @Override
    void updateControlForTarget(@Nullable InsetsControlTarget target, boolean force,
            @NonNull ImeTracker.Token statsToken) {
        if (target != null && target.getWindow() != null) {
            // IME control target could be a different window.
            // Refer WindowState#getImeControlTarget().
            target = target.getWindow().getImeControlTarget();
        }
        // TODO(b/353463205) make sure that the statsToken of all callers is non-null (currently
        //  not the case)
        super.updateControlForTarget(target, force, statsToken);
        // TODO(b/353463205) investigate if we should fail the statsToken, or if it's only
        //  temporary null.
        if (target != null && target == mControlTarget) {
            // If insets target is not available (e.g. RemoteInsetsControlTarget), use current
            // IME input target to update IME request state. For example, switch from a task
            // with showing IME to a split-screen task without showing IME.
            InputTarget imeInputTarget = mDisplayContent.getImeInputTarget();
            if (imeInputTarget != target && imeInputTarget != null
                    && imeInputTarget.isRequestedVisible(WindowInsets.Type.ime())
                    != target.isRequestedVisible(WindowInsets.Type.ime())) {
                // Only update the controlTarget, if it has a different requested visibility
                // than the imeInputTarget. Otherwise, updateClientVisibility won't invoke
                // the listener, as nothing changed.
                reportImeInputTargetStateToControlTarget(imeInputTarget, target,
                        statsToken);
            } else {
                invokeOnImeRequestedChangedListener(target, statsToken);
            }
        } else {
            ProtoLog.w(WM_DEBUG_IME,
                    "Not invoking onImeRequestedChangedListener, target=%s, current "
                            + "controlTarget=%s",
                    target, mControlTarget);
        }
    }

    // TODO(b/353463205) change statsToken to be NonNull, after the flag is permanently enabled
    @Override
    protected boolean updateClientVisibility(@NonNull InsetsTarget caller,
            @Nullable ImeTracker.Token statsToken) {
        InsetsControlTarget controlTarget = getControlTarget();
        if (caller != controlTarget) {
            final var imeInputTarget = mDisplayContent.getImeInputTarget();
            if (caller == imeInputTarget) {
                reportImeInputTargetStateToControlTarget(imeInputTarget, controlTarget,
                        statsToken);
            } else {
                ProtoLog.w(WM_DEBUG_IME,
                        "Tried to update client visibility for non-IME input target %s "
                                + "(current target: %s, IME requested: %s)", caller,
                        imeInputTarget, caller.isRequestedVisible(WindowInsets.Type.ime()));
                ImeTracker.forLogging().onFailed(statsToken,
                        ImeTracker.PHASE_SERVER_UPDATE_CLIENT_VISIBILITY);
            }
            return false;
        }
        final boolean changed = super.updateClientVisibility(controlTarget, statsToken);
        if (changed) {
            ImeTracker.forLogging().onProgress(statsToken,
                    ImeTracker.PHASE_SERVER_UPDATE_CLIENT_VISIBILITY);
            invokeOnImeRequestedChangedListener(controlTarget, statsToken);
        } else {
            // TODO(b/353463205) check cancelled / failed
            ImeTracker.forLogging().onCancelled(statsToken,
                    ImeTracker.PHASE_SERVER_UPDATE_CLIENT_VISIBILITY);
        }
        return changed;
    }

    /**
     * Called when the IME input target has changed.
     *
     * @param imeInputTarget the new IME input target.
     */
    void onImeInputTargetChanged(@Nullable InputTarget imeInputTarget) {
        if (imeInputTarget != null) {
            InsetsControlTarget imeControlTarget = getControlTarget();
            if (imeInputTarget != imeControlTarget) {
                // TODO(b/353463205): check if fromUser=false is correct here
                boolean imeVisible = imeInputTarget.isRequestedVisible(WindowInsets.Type.ime());
                ImeTracker.Token statsToken = ImeTracker.forLogging().onStart(
                        imeVisible ? ImeTracker.TYPE_SHOW : ImeTracker.TYPE_HIDE,
                        ImeTracker.ORIGIN_SERVER,
                        imeVisible ? SoftInputShowHideReason.SHOW_INPUT_TARGET_CHANGED
                                : SoftInputShowHideReason.HIDE_INPUT_TARGET_CHANGED,
                        false /* fromUser */);
                boolean controlTargetRequestedVisible = imeControlTarget != null
                        && imeControlTarget.isRequestedVisible(WindowInsets.Type.ime());
                if (imeVisible == controlTargetRequestedVisible && imeControlTarget != null) {
                    // Notifying request visibility is no-op, but we need to invoke the listener.
                    invokeOnImeRequestedChangedListener(imeControlTarget, statsToken);
                } else {
                    reportImeInputTargetStateToControlTarget(imeInputTarget, imeControlTarget,
                            statsToken);
                }
            }
        }
    }

    private void reportImeInputTargetStateToControlTarget(@NonNull InsetsTarget imeInputTarget,
            InsetsControlTarget controlTarget, @NonNull ImeTracker.Token statsToken) {
        // In case of the multi window mode, update the requestedVisibleTypes from
        // the controlTarget (=RemoteInsetsControlTarget) via DisplayImeController.
        // Then, trigger onRequestedVisibleTypesChanged for the controlTarget with
        // its new requested visibility for the IME
        boolean imeVisible = imeInputTarget.isRequestedVisible(WindowInsets.Type.ime());
        if (controlTarget != null) {
            ImeTracker.forLogging().onProgress(statsToken,
                    ImeTracker.PHASE_WM_SET_REMOTE_TARGET_IME_VISIBILITY);
            controlTarget.setImeInputTargetRequestedVisibility(imeVisible, statsToken);
        } else if (imeInputTarget instanceof InsetsControlTarget) {
            // In case of a virtual display that cannot show the IME, the
            // controlTarget will be null here, as no controlTarget was set yet. In
            // that case, proceed similar to the multi window mode (fallback =
            // RemoteInsetsControlTarget of the default display)
            controlTarget = mDisplayContent.getImeHost(
                    ((InsetsControlTarget) imeInputTarget).getWindow());

            if (controlTarget != null && controlTarget != imeInputTarget) {
                ImeTracker.forLogging().onProgress(statsToken,
                        ImeTracker.PHASE_WM_SET_REMOTE_TARGET_IME_VISIBILITY);
                controlTarget.setImeInputTargetRequestedVisibility(imeVisible, statsToken);
                // not all virtual displays have an ImeInsetsSourceProvider, so it is not
                // guaranteed that the IME will be started when the control target reports its
                // requested visibility back. Thus, invoking the listener here.
                invokeOnImeRequestedChangedListener((InsetsControlTarget) imeInputTarget,
                        statsToken);
            } else {
                ImeTracker.forLogging().onFailed(statsToken,
                        ImeTracker.PHASE_WM_SET_REMOTE_TARGET_IME_VISIBILITY);
            }
        }
    }

    // TODO(b/353463205) check callers to see if we can make statsToken @NonNull
    private void invokeOnImeRequestedChangedListener(@NonNull InsetsControlTarget controlTarget,
            @Nullable ImeTracker.Token statsToken) {
        final var imeListener = mDisplayContent.mWmService.mOnImeRequestedChangedListener;
        if (imeListener != null) {
            final boolean imeAnimating = Flags.reportAnimatingInsetsTypes()
                    && (controlTarget.getAnimatingTypes() & WindowInsets.Type.ime()) != 0;
            final boolean imeVisible =
                    controlTarget.isRequestedVisible(WindowInsets.Type.ime()) || imeAnimating;
            final var finalStatsToken = statsToken != null ? statsToken
                    : ImeTracker.forLogging().onStart(
                            imeVisible ? ImeTracker.TYPE_SHOW : ImeTracker.TYPE_HIDE,
                            ImeTracker.ORIGIN_SERVER,
                            SoftInputShowHideReason.IME_REQUESTED_CHANGED_LISTENER,
                            false /* fromUser */);

            // If the RemoteInsetsControlTarget is the current controlTarget, pass the
            // windowToken of the imeInputTarget to IMMS.
            final IBinder windowToken;
            if (controlTarget.getWindowToken() != null) {
                windowToken = controlTarget.getWindowToken();
            } else if (mDisplayContent.getImeInputTarget() != null) {
                windowToken = mDisplayContent.getImeInputTarget().getWindowToken();
            } else {
                windowToken = null;
            }
            ImeTracker.forLogging().onProgress(finalStatsToken,
                    ImeTracker.PHASE_WM_POSTING_CHANGED_IME_VISIBILITY);
            mDisplayContent.mWmService.mH.post(() -> {
                ImeTracker.forLogging().onProgress(finalStatsToken,
                        ImeTracker.PHASE_WM_INVOKING_IME_REQUESTED_LISTENER);
                imeListener.onImeRequestedChanged(windowToken, imeVisible, finalStatsToken);
            });
        } else {
            ImeTracker.forLogging().onFailed(statsToken,
                    ImeTracker.PHASE_WM_DISPATCH_IME_REQUESTED_CHANGED);
        }
    }

    @Override
    void onAnimatingTypesChanged(@NonNull InsetsControlTarget caller,
            @Nullable ImeTracker.Token statsToken) {
        if (Flags.reportAnimatingInsetsTypes()) {
            final InsetsControlTarget controlTarget = getControlTarget();
            // If the IME is not being requested anymore and the animation is finished, we need to
            // invoke the listener, to let IMS eventually know
            if (caller == controlTarget && !caller.isRequestedVisible(WindowInsets.Type.ime())
                    && (caller.getAnimatingTypes() & WindowInsets.Type.ime()) == 0) {
                ImeTracker.forLogging().onFailed(statsToken,
                        ImeTracker.PHASE_WM_NOTIFY_HIDE_ANIMATION_FINISHED);
                invokeOnImeRequestedChangedListener(caller, statsToken);
            } else {
                ImeTracker.forLogging().onCancelled(statsToken,
                        ImeTracker.PHASE_WM_NOTIFY_HIDE_ANIMATION_FINISHED);
            }
        }
    }

    private void reportImeDrawnForOrganizerIfNeeded(@NonNull InsetsControlTarget caller) {
        final WindowState callerWindow = caller.getWindow();
        if (callerWindow == null) {
            return;
        }
        final WindowToken imeToken = mWin != null ? mWin.mToken : null;
        final var rotationController = mDisplayContent.getAsyncRotationController();
        if ((rotationController != null && rotationController.isTargetToken(imeToken))
                || (imeToken != null && imeToken.isSelfAnimating(0 /* flags */,
                    SurfaceAnimator.ANIMATION_TYPE_TOKEN_TRANSFORM))) {
            // Skip reporting IME drawn state when the control target is in fixed
            // rotation, AsyncRotationController will report after the animation finished.
            return;
        }
        reportImeDrawnForOrganizer(caller);
    }

    private void reportImeDrawnForOrganizer(@NonNull InsetsControlTarget caller) {
        final Task callerTask = caller.getWindow() != null ? caller.getWindow().getTask() : null;
        if (callerTask == null) {
            return;
        }
        if (callerTask.isOrganized()) {
            mWin.mWmService.mAtmService.mTaskOrganizerController.reportImeDrawnOnTask(callerTask);
        }
    }

    /** Report the IME has drawn on the current IME control target for its task organizer */
    void reportImeDrawnForOrganizer() {
        final InsetsControlTarget imeControlTarget = getControlTarget();
        if (imeControlTarget != null) {
            reportImeDrawnForOrganizer(imeControlTarget);
        }
    }

    private void onSourceChanged() {
        if (mLastSource.equals(mSource)) {
            return;
        }
        mLastSource.set(mSource);
        mDisplayContent.mWmService.mH.obtainMessage(
                UPDATE_MULTI_WINDOW_STACKS, mDisplayContent).sendToTarget();
    }

    /**
     * Sets the statsToken before the IMS was shown/hidden.
     * @param visible {@code true} to make it visible, false to hide it.
     * @param statsToken the token tracking the current IME request.
     */
    void receiveImeStatsToken(boolean visible,
            @NonNull ImeTracker.Token statsToken) {
        if (mStatsToken != null) {
            // We have an ongoing show request will be cancelled by the newly received show
            // request (cancelling the initial show) or hide request (aborting the initial show).
            logIsScheduledAndReadyToShowIme(!visible /* aborted */);
        }
        ProtoLog.d(WM_DEBUG_IME, "receiveImeStatsToken: visible=%s", visible);
        if (visible) {
            ImeTracker.forLogging().onCancelled(
                    mStatsToken, ImeTracker.PHASE_WM_ABORT_SHOW_IME_POST_LAYOUT);
            mStatsToken = statsToken;
        } else {
            ImeTracker.forLogging().onFailed(
                    mStatsToken, ImeTracker.PHASE_WM_ABORT_SHOW_IME_POST_LAYOUT);
            mStatsToken = null;
        }
    }

    /**
     * @param aborted whether the scheduled show IME request was aborted or cancelled.
     */
    private void logIsScheduledAndReadyToShowIme(boolean aborted) {
        final var imeLayeringTarget = mDisplayContent.getImeLayeringTarget();
        final var controlTarget = getControlTarget();
        final var sb = new StringBuilder();
        sb.append("showImePostLayout ").append(aborted ? "aborted" : "cancelled");
        sb.append(", serverVisible: ").append(mServerVisible);
        sb.append(", frozen: ").append(mFrozen);
        sb.append(", mWin is: ").append(mWin != null ? "non-null" : "null");
        if (mWin != null) {
            sb.append(", isDrawn: ").append(mWin.isDrawn());
            sb.append(", mGivenInsetsPending: ").append(mWin.mGivenInsetsPending);
        }
        sb.append(", imeLayeringTarget: ").append(imeLayeringTarget);
        sb.append(", controlTarget: ").append(controlTarget);
        if (imeLayeringTarget != null && controlTarget != null) {
            sb.append("\n");
            sb.append("controlTarget == DisplayContent.imeControlTarget: ");
            sb.append(controlTarget == mDisplayContent.getImeControlTarget());
            sb.append(", hasPendingControls: ");
            sb.append(mStateController.hasPendingControls(controlTarget));
            final boolean hasLeash = getLeash(controlTarget) != null;
            sb.append(", leash is: ").append(hasLeash ? "non-null" : "null");
            if (!hasLeash) {
                sb.append(", control is: ").append(mControl != null ? "non-null" : "null");
                sb.append(", mIsLeashInitialized: ").append(mIsLeashInitialized);
            }
        }
        Slog.d(TAG, sb.toString());
    }

    @Override
    public void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
        super.dump(pw, prefix);
        prefix = prefix + "  ";
        pw.print(prefix);
        pw.print("mImeShowing=");
        pw.print(mImeShowing);
        pw.print(" mLastDrawn=");
        pw.print(mLastDrawn);
        pw.println();
    }

    @Override
    void dumpDebug(@NonNull ProtoOutputStream proto, long fieldId,
            @WindowTracingLogLevel int logLevel) {
        final long token = proto.start(fieldId);
        super.dumpDebug(proto, INSETS_SOURCE_PROVIDER, logLevel);
        proto.end(token);
    }

    /**
     * Sets whether the IME is currently supposed to be showing according to
     * InputMethodManagerService.
     */
    public void setImeShowing(boolean imeShowing) {
        mImeShowing = imeShowing;
    }

    /**
     * Returns whether the IME is currently supposed to be showing according to
     * InputMethodManagerService.
     */
    public boolean isImeShowing() {
        return mImeShowing;
    }
}
