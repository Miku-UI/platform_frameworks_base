/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.internal.perfetto.protos.Windowmanagerservice.InsetsSourceProviderProto.CAPTURED_LEASH;
import static android.internal.perfetto.protos.Windowmanagerservice.InsetsSourceProviderProto.CLIENT_VISIBLE;
import static android.internal.perfetto.protos.Windowmanagerservice.InsetsSourceProviderProto.CONTROL;
import static android.internal.perfetto.protos.Windowmanagerservice.InsetsSourceProviderProto.CONTROLLABLE;
import static android.internal.perfetto.protos.Windowmanagerservice.InsetsSourceProviderProto.CONTROL_TARGET_IDENTIFIER;
import static android.internal.perfetto.protos.Windowmanagerservice.InsetsSourceProviderProto.FAKE_CONTROL;
import static android.internal.perfetto.protos.Windowmanagerservice.InsetsSourceProviderProto.FAKE_CONTROL_TARGET_IDENTIFIER;
import static android.internal.perfetto.protos.Windowmanagerservice.InsetsSourceProviderProto.FRAME;
import static android.internal.perfetto.protos.Windowmanagerservice.InsetsSourceProviderProto.IS_LEASH_READY_FOR_DISPATCHING;
import static android.internal.perfetto.protos.Windowmanagerservice.InsetsSourceProviderProto.PENDING_CONTROL_TARGET_IDENTIFIER;
import static android.internal.perfetto.protos.Windowmanagerservice.InsetsSourceProviderProto.SERVER_VISIBLE;
import static android.internal.perfetto.protos.Windowmanagerservice.InsetsSourceProviderProto.SOURCE;
import static android.internal.perfetto.protos.Windowmanagerservice.InsetsSourceProviderProto.SOURCE_WINDOW_STATE_IDENTIFIER;
import static android.view.InsetsSource.FLAG_INVALID;

import static com.android.internal.protolog.WmProtoLogGroups.WM_DEBUG_WINDOW_INSETS;
import static com.android.server.wm.SurfaceAnimator.ANIMATION_TYPE_INSETS_CONTROL;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.graphics.Insets;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Slog;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;
import android.view.InsetsSource;
import android.view.InsetsSource.Flags;
import android.view.InsetsSourceControl;
import android.view.SurfaceControl;
import android.view.SurfaceControl.Transaction;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.ImeTracker;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;
import com.android.internal.util.function.TriFunction;
import com.android.server.wm.SurfaceAnimator.AnimationType;
import com.android.server.wm.SurfaceAnimator.OnAnimationFinishedCallback;

import java.io.PrintWriter;
import java.util.function.Consumer;

/**
 * Controller for a specific inset source on the server. It's called provider as it provides the
 * {@link InsetsSource} to the client that uses it in {@link android.view.InsetsSourceConsumer}.
 */
class InsetsSourceProvider {

    private static final String TAG = "InsetsSourceProvider";

    private static final Rect EMPTY_RECT = new Rect();

    @NonNull
    protected final InsetsSource mSource;
    @NonNull
    protected final DisplayContent mDisplayContent;
    @NonNull
    protected final InsetsStateController mStateController;
    @Nullable
    protected WindowState mWin;
    @Nullable
    protected InsetsSourceControl mControl;
    @Nullable
    protected InsetsControlTarget mControlTarget;
    protected boolean mIsLeashInitialized;

    @NonNull
    private final Rect mTmpRect = new Rect();
    @NonNull
    private final InsetsSourceControl mFakeControl;
    @NonNull
    private final Point mPosition = new Point();
    @NonNull
    private final Consumer<Transaction> mSetControlPositionConsumer;
    @Nullable
    private InsetsControlTarget mPendingControlTarget;
    @Nullable
    private InsetsControlTarget mFakeControlTarget;
    @Nullable
    private ControlAdapter mAdapter;
    @Nullable
    private TriFunction<DisplayFrames, WindowState, Rect, Integer> mFrameProvider;
    @Nullable
    private SparseArray<TriFunction<DisplayFrames, WindowState, Rect, Integer>>
            mOverrideFrameProviders;
    @NonNull
    private final SparseArray<Rect> mOverrideFrames = new SparseArray<>();
    @NonNull
    private final Rect mSourceFrame = new Rect();
    @NonNull
    private final Rect mLastSourceFrame = new Rect();
    @NonNull
    private Insets mInsetsHint = Insets.NONE;
    private boolean mInsetsHintStale = true;
    @Flags
    private int mFlagsFromFrameProvider;
    @Flags
    private int mFlagsFromServer;
    private boolean mHasPendingPosition;

    /** The visibility override from the current controlling window. */
    private boolean mClientVisible;

    /**
     * Whether the window is available and considered visible as in {@link WindowState#isVisible}.
     */
    private boolean mServerVisible;

    private final boolean mControllable;

    InsetsSourceProvider(@NonNull InsetsSource source,
            @NonNull InsetsStateController stateController,
            @NonNull DisplayContent displayContent) {
        mClientVisible = (WindowInsets.Type.defaultVisible() & source.getType()) != 0;
        mSource = source;
        mDisplayContent = displayContent;
        mStateController = stateController;
        mFakeControl = new InsetsSourceControl(source.getId(), source.getType(), null /* leash */,
                false /* initialVisible */, new Point(), Insets.NONE);
        mControllable = (InsetsPolicy.CONTROLLABLE_TYPES & source.getType()) != 0;
        mSetControlPositionConsumer = t -> {
            if (mControl == null || mControlTarget == null) {
                return;
            }
            boolean changed = mControl.setSurfacePosition(mPosition.x, mPosition.y);
            final SurfaceControl leash = mControl.getLeash();
            if (changed && leash != null) {
                t.setPosition(leash, mPosition.x, mPosition.y);
            }
            if (mHasPendingPosition) {
                mHasPendingPosition = false;
                if (mPendingControlTarget != mControlTarget) {
                    mStateController.notifyControlTargetChanged(mPendingControlTarget, this);
                }
            }
            changed |= updateInsetsHint(mControl);
            if (changed) {
                mStateController.notifyControlChanged(mControlTarget, this);
            }
        };
        setFlags(FLAG_INVALID, FLAG_INVALID);
    }

    private boolean updateInsetsHint(@NonNull InsetsSourceControl control) {
        final Insets insetsHint = getInsetsHint();
        if (!control.getInsetsHint().equals(insetsHint)) {
            control.setInsetsHint(insetsHint);
            return true;
        }
        return false;
    }

    @NonNull
    InsetsSource getSource() {
        return mSource;
    }

    @VisibleForTesting
    @NonNull
    Rect getSourceFrame() {
        return mSourceFrame;
    }

    /**
     * @return Whether the current flag configuration allows to control this source.
     */
    boolean isControllable() {
        return mControllable;
    }

    /**
     * @return Whether the current window has a visible surface.
     */
    protected boolean isSurfaceVisible() {
        return mWin != null && mWin.wouldBeVisibleIfPolicyIgnored() && mWin.isVisibleByPolicy();
    }

    /**
     * @param target the current control target.
     * @return whether the {@link InsetsSourceControl} should be initially visible.
     */
    protected boolean isInitiallyVisible(@NonNull InsetsControlTarget target) {
        return mClientVisible;
    }

    /**
     * Updates the window that currently backs this source.
     *
     * @param win           The window that links to this source.
     * @param frameProvider Based on display frame state and the window, calculates the resulting
     *                      frame that should be reported to clients.
     * @param overrideFrameProviders Based on display frame state and the window, calculates the
     *                               resulting frame that should be reported to given window type.
     */
    void setWindow(@Nullable WindowState win,
            @Nullable TriFunction<DisplayFrames, WindowState, Rect, Integer> frameProvider,
            @Nullable SparseArray<TriFunction<DisplayFrames, WindowState, Rect, Integer>>
                    overrideFrameProviders) {
        if (mWin != null) {
            if (mControllable) {
                mWin.setControllableInsetProvider(null);
            }
            // The window may be animating such that we can hand out the leash to the control
            // target. Revoke the leash by cancelling the animation to correct the state.
            // TODO: Ideally, we should wait for the animation to finish so previous window can
            // animate-out as new one animates-in.
            mWin.cancelAnimation();
            mWin.getInsetsSourceProviders().remove(mSource.getId());
            mHasPendingPosition = false;
        }
        ProtoLog.d(WM_DEBUG_WINDOW_INSETS, "setWin %s for type %s",
                win, WindowInsets.Type.toString(mSource.getType()));
        mWin = win;
        mFrameProvider = frameProvider;
        if (frameProvider == null) {
            // This clears mFlagsFromFrameProvider.
            mSource.setFlags(mFlagsFromServer);
        }
        mOverrideFrames.clear();
        mOverrideFrameProviders = overrideFrameProviders;
        if (win == null) {
            setServerVisible(false);
            mSource.setVisibleFrame(null);
            mSourceFrame.setEmpty();
        } else {
            win.getInsetsSourceProviders().put(mSource.getId(), this);
            if (mControllable) {
                win.setControllableInsetProvider(this);
                if (mPendingControlTarget != mControlTarget) {
                    mStateController.notifyControlTargetChanged(mPendingControlTarget, this);
                }
            }
        }
    }

    boolean setFlags(@Flags int flags, @Flags int mask) {
        mFlagsFromServer = (mFlagsFromServer & ~mask) | (flags & mask);
        @Flags
        final int mergedFlags = mFlagsFromFrameProvider | mFlagsFromServer;
        if (mSource.getFlags() != mergedFlags) {
            mSource.setFlags(mergedFlags);
            return true;
        }
        return false;
    }

    /**
     * The source frame can affect the layout of other windows, so this should be called once the
     * window gets laid out.
     */
    void updateSourceFrame(@NonNull Rect frame) {
        if (mWin == null) {
            return;
        }

        mSourceFrame.set(frame);
        if (mFrameProvider != null) {
            mFlagsFromFrameProvider = mFrameProvider.apply(
                    mWin.getDisplayContent().mDisplayFrames,
                    mWin,
                    mSourceFrame);
            mSource.setFlags(mFlagsFromFrameProvider | mFlagsFromServer);
        }
        updateSourceFrameForServerVisibility();
        if (!mLastSourceFrame.equals(mSourceFrame)) {
            mLastSourceFrame.set(mSourceFrame);
            mInsetsHintStale = true;
        }

        if (mOverrideFrameProviders != null) {
            // Not necessary to clear the mOverrideFrames here. It will be cleared every time the
            // override frame provider updates.
            for (int i = mOverrideFrameProviders.size() - 1; i >= 0; i--) {
                @WindowManager.LayoutParams.WindowType
                final int windowType = mOverrideFrameProviders.keyAt(i);
                final Rect overrideFrame;
                if (mOverrideFrames.contains(windowType)) {
                    overrideFrame = mOverrideFrames.get(windowType);
                    overrideFrame.set(frame);
                } else {
                    overrideFrame = new Rect(frame);
                }
                final var provider = mOverrideFrameProviders.get(windowType);
                if (provider != null) {
                    provider.apply(mWin.getDisplayContent().mDisplayFrames, mWin, overrideFrame);
                }
                mOverrideFrames.put(windowType, overrideFrame);
            }
        }

        if (mWin.mGivenVisibleInsets.left != 0
                || mWin.mGivenVisibleInsets.top != 0
                || mWin.mGivenVisibleInsets.right != 0
                || mWin.mGivenVisibleInsets.bottom != 0) {
            mTmpRect.set(frame);
            mTmpRect.inset(mWin.mGivenVisibleInsets);
            mSource.setVisibleFrame(mTmpRect);
        } else {
            mSource.setVisibleFrame(null);
        }
    }

    private void updateSourceFrameForServerVisibility() {
        // Make sure we set the valid source frame only when server visible is true, because the
        // frame may not yet be determined that server side doesn't think the window is ready to
        // visible. (i.e. No surface, pending insets that were given during layout, etc..)
        final Rect frame = mServerVisible ? mSourceFrame : EMPTY_RECT;
        if (mSource.getFrame().equals(frame)) {
            return;
        }
        mSource.setFrame(frame);
        if (mWin != null) {
            mSource.updateSideHint(mWin.getBounds());
        }
    }

    void onWindowBoundsChanged() {
        mInsetsHintStale = true;
    }

    @NonNull
    @VisibleForTesting
    Insets getInsetsHint() {
        if (!mServerVisible || mWin == null || mWin.mGivenInsetsPending) {
            return mInsetsHint;
        }
        if (mInsetsHintStale) {
            final Rect bounds = mWin.getBounds();
            mInsetsHint = mSource.calculateInsets(bounds, bounds, true /* ignoreVisibility */);
            mInsetsHintStale = false;
        }
        return mInsetsHint;
    }

    /** @return A new source computed by the specified window frame in the given display frames. */
    @NonNull
    InsetsSource createSimulatedSource(@NonNull DisplayFrames displayFrames, @NonNull Rect frame) {
        final var source = new InsetsSource(mSource);
        mTmpRect.set(frame);
        if (mFrameProvider != null) {
            mFrameProvider.apply(displayFrames, mWin, mTmpRect);
        }
        source.setFrame(mTmpRect);

        // Don't copy visible frame because it might not be calculated in the provided display
        // frames and it is not significant for this usage.
        source.setVisibleFrame(null);

        return source;
    }

    /**
     * Called before a layout pass will occur.
     */
    void onPreLayout() {
        if (!android.view.inputmethod.Flags.setServerVisibilityOnprelayout()) {
            return;
        }
        if (mWin == null) {
            return;
        }
        setServerVisible(isSurfaceVisible());
    }

    /**
     * Called after a layout pass has occurred.
     *
     * @return {@code true} if {@link InsetsStateController#notifyControlChanged} was called or
     * was scheduled to be called within this method, else {@code false}.
     */
    boolean onPostLayout() {
        if (mWin == null) {
            return false;
        }
        final boolean serverVisibleChanged;
        if (!android.view.inputmethod.Flags.setServerVisibilityOnprelayout()) {
            final boolean isServerVisible = isSurfaceVisible();

            serverVisibleChanged = mServerVisible != isServerVisible;
            setServerVisible(isServerVisible);
        } else {
            serverVisibleChanged = false;
        }
        if (mControl != null && mControlTarget != null) {
            final boolean positionChanged = updateInsetsControlPosition(mWin);
            if (positionChanged || mHasPendingPosition) {
                return true;
            }
            // The insets hint would be updated while changing the position. Here updates it
            // for the possible change of the bounds.
            if (updateInsetsHint(mControl) || serverVisibleChanged) {
                // Only call notifyControlChanged here when the position hasn't been or won't be
                // changed. Otherwise, it has been called or scheduled to be called during
                // updateInsetsControlPosition.
                mStateController.notifyControlChanged(mControlTarget, this);
                return true;
            }
        }
        return false;
    }

    /**
     * @param windowState the window that links to this source (i.e. {@link #mWin}).
     *
     * @return {@code true} if the surface position of the control is changed.
     */
    boolean updateInsetsControlPosition(@NonNull WindowState windowState) {
        if (mControl == null) {
            return false;
        }
        final Point position = getWindowFrameSurfacePosition(windowState);
        if (!mPosition.equals(position)) {
            mPosition.set(position);
            if (windowState.getWindowFrames().didFrameSizeChange()
                    && windowState.mWinAnimator.getShown() && windowState.okToDisplay()) {
                mHasPendingPosition = true;
                windowState.applyWithNextDraw(mSetControlPositionConsumer);
            } else {
                Transaction t = windowState.getSyncTransaction();
                // Make the buffer, token transformation, and leash position to be updated
                // together when the window is drawn for new rotation. Otherwise the window
                // may be outside the screen by the inconsistent orientations.
                final AsyncRotationController rotationController =
                        mDisplayContent.getAsyncRotationController();
                if (rotationController != null) {
                    final Transaction drawT =
                            rotationController.getDrawTransaction(windowState.mToken);
                    if (drawT != null) {
                        t = drawT;
                    }
                }
                mSetControlPositionConsumer.accept(t);
            }
            return true;
        }
        return false;
    }

    /**
     * Gets the surface position of the window that links this source.
     *
     * @param windowState the window that links to this source (i.e. {@link #mWin}).
     */
    @NonNull
    private Point getWindowFrameSurfacePosition(@NonNull WindowState windowState) {
        if (mControl != null) {
            final AsyncRotationController controller = mDisplayContent.getAsyncRotationController();
            if (controller != null && controller.shouldFreezeInsetsPosition(windowState)) {
                // Use previous position because the window still shows with old rotation.
                return mControl.getSurfacePosition();
            }
        }
        final Rect frame = windowState.getFrame();
        final Point position = new Point();
        windowState.transformFrameToSurfacePosition(frame.left, frame.top, position);
        return position;
    }

    /**
     * @see InsetsStateController#onControlTargetChanged
     */
    void updateFakeControlTarget(@Nullable InsetsControlTarget fakeTarget) {
        if (fakeTarget == mFakeControlTarget) {
            return;
        }
        mFakeControlTarget = fakeTarget;
    }

    void updateControlForTarget(@Nullable InsetsControlTarget target, boolean force,
            @Nullable ImeTracker.Token statsToken) {
        mPendingControlTarget = target;

        if (mWin != null && mWin.getSurfaceControl() == null) {
            // if window doesn't have a surface, set it null and return.
            setWindow(null /* win */, null /* frameProvider */, null /* overrideFrameProviders */);
        }
        if (mWin == null) {
            return;
        }
        if (target == mControlTarget && !force) {
            return;
        }
        if (target == null) {
            mHasPendingPosition = false;
            // Cancelling the animation will invoke onAnimationCancelled, resetting all the fields.
            mWin.cancelAnimation();
            setClientVisible((WindowInsets.Type.defaultVisible() & mSource.getType()) != 0);
            return;
        }
        if (mHasPendingPosition) {
            // Don't create a new leash while having a pending position. Otherwise, the position
            // will be changed earlier than expected, which can cause flicker.
            return;
        }
        final Point surfacePosition = getWindowFrameSurfacePosition(mWin);
        mPosition.set(surfacePosition);
        mAdapter = new ControlAdapter(surfacePosition);
        final boolean initiallyVisible = isInitiallyVisible(target);
        if (mSource.getType() == WindowInsets.Type.ime()) {
            setClientVisible(target.isRequestedVisible(WindowInsets.Type.ime()));
        }
        final Transaction t = mWin.getSyncTransaction();
        mWin.startAnimation(t, mAdapter, !initiallyVisible /* hidden */,
                ANIMATION_TYPE_INSETS_CONTROL);
        if (mAdapter == null) {
            // This can happen through startAnimation calling cancelAnimation.
            Slog.w(TAG, "Failed to create animation for: " + mSource);
            return;
        }

        // The leash was just created. We cannot dispatch it until its surface transaction is
        // committed. Otherwise, the client's operation to the leash might be overwritten by us.
        mIsLeashInitialized = false;

        final SurfaceControl leash = mAdapter.mCapturedLeash;
        mControlTarget = target;
        updateVisibility();
        mControl = new InsetsSourceControl(mSource.getId(), mSource.getType(), leash,
                initiallyVisible, surfacePosition, getInsetsHint());
        mStateController.notifySurfaceTransactionReady(this, getSurfaceTransactionId(leash), true);

        ProtoLog.d(WM_DEBUG_WINDOW_INSETS,
                "updateControl %s for target %s", mControl, mControlTarget);
    }

    private long getSurfaceTransactionId(@Nullable SurfaceControl leash) {
        // Here returns mNativeObject (long) as the ID instead of the leash itself so that
        // InsetsStateController won't keep referencing the leash unexpectedly.
        return leash != null ? leash.mNativeObject : 0;
    }

    /**
     * This is called when the surface transaction of the leash initialization has been committed.
     *
     * @param id Indicates which transaction is committed so that stale callbacks can be dropped.
     */
    void onSurfaceTransactionCommitted(long id) {
        if (mIsLeashInitialized) {
            return;
        }
        if (mControl == null) {
            return;
        }
        if (id != getSurfaceTransactionId(mControl.getLeash())) {
            return;
        }
        mIsLeashInitialized = true;
        mStateController.notifySurfaceTransactionReady(this, 0, false);
    }

    boolean updateClientVisibility(@NonNull InsetsTarget caller,
            @Nullable ImeTracker.Token statsToken) {
        final boolean requestedVisible = caller.isRequestedVisible(mSource.getType());
        if (caller != mControlTarget || requestedVisible == mClientVisible) {
            return false;
        }
        setClientVisible(requestedVisible);
        return true;
    }

    void setClientVisible(boolean clientVisible) {
        if (mClientVisible == clientVisible) {
            return;
        }
        mClientVisible = clientVisible;
        updateVisibility();
        // The visibility change needs a traversal to apply.
        mDisplayContent.setLayoutNeeded();
        mDisplayContent.mWmService.mWindowPlacerLocked.requestTraversal();
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
    void setServerVisible(boolean serverVisible) {
        if (mServerVisible != serverVisible) {
            mServerVisible = serverVisible;
            setFlags(serverVisible ? 0 : FLAG_INVALID, FLAG_INVALID);
            updateVisibility();
        }
        updateSourceFrameForServerVisibility();
    }

    protected void updateVisibility() {
        mSource.setVisible(mServerVisible && mClientVisible);
        ProtoLog.d(WM_DEBUG_WINDOW_INSETS,
                "updateVisibility for %s, serverVisible: %s clientVisible: %s",
                WindowInsets.Type.toString(mSource.getType()),
                mServerVisible, mClientVisible);
    }

    void onAnimatingTypesChanged(@NonNull InsetsControlTarget caller,
            @Nullable ImeTracker.Token statsToken) {
    }

    protected boolean isLeashReadyForDispatching() {
        return isLeashInitialized();
    }

    boolean isLeashInitialized() {
        return mIsLeashInitialized;
    }

    /**
     * Gets the source control for the given control target. If this is the provider's control
     * target, but the leash is not ready for dispatching, a new source control object with the
     * leash set to {@code null} is returned.
     *
     * @param target the control target to get the source control for.
     */
    @Nullable
    InsetsSourceControl getControl(@NonNull InsetsControlTarget target) {
        if (target == mControlTarget) {
            if (!isLeashReadyForDispatching() && mControl != null) {
                // The surface transaction of preparing leash is not applied yet. We don't send it
                // to the client in case that the client applies its transaction sooner than ours
                // that we could unexpectedly overwrite the surface state.
                return new InsetsSourceControl(mControl.getId(), mControl.getType(),
                        null /* leash */, mControl.isInitiallyVisible(),
                        mControl.getSurfacePosition(), mControl.getInsetsHint());
            }
            return mControl;
        }
        if (target == mFakeControlTarget) {
            return mFakeControl;
        }
        return null;
    }

    /**
     * Gets the leash of the source control for the given control target. If this is not the
     * provider's control target, or the leash is not ready for dispatching, this will
     * return {@code null}.
     *
     * @param target the control target to get the source control leash for.
     */
    @Nullable
    protected SurfaceControl getLeash(@NonNull InsetsControlTarget target) {
        return target == mControlTarget && mIsLeashInitialized && mControl != null
                ? mControl.getLeash() : null;
    }

    @Nullable
    InsetsControlTarget getControlTarget() {
        return mControlTarget;
    }

    @Nullable
    InsetsControlTarget getFakeControlTarget() {
        return mFakeControlTarget;
    }

    boolean isServerVisible() {
        return mServerVisible;
    }

    boolean isClientVisible() {
        return mClientVisible;
    }

    boolean overridesFrame(@WindowManager.LayoutParams.WindowType int windowType) {
        return mOverrideFrames.contains(windowType);
    }

    @Nullable
    Rect getOverriddenFrame(@WindowManager.LayoutParams.WindowType int windowType) {
        return mOverrideFrames.get(windowType);
    }

    public void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
        pw.println(prefix + getClass().getSimpleName());
        prefix = prefix + "  ";
        pw.print(prefix + "mSource="); mSource.dump("", pw);
        pw.print(prefix + "mSourceFrame=");
        pw.println(mSourceFrame);
        if (mOverrideFrames.size() > 0) {
            pw.print(prefix + "mOverrideFrames=");
            pw.println(mOverrideFrames);
        }
        if (mControl != null) {
            pw.print(prefix + "mControl=");
            mControl.dump("", pw);
        }
        if (mControllable) {
            pw.print(prefix + "mInsetsHint=");
            pw.print(mInsetsHint);
            if (mInsetsHintStale) {
                pw.print(" stale");
            }
            pw.println();
        }
        pw.print(prefix);
        pw.print("mIsLeashInitialized="); pw.print(mIsLeashInitialized);
        pw.print(" mHasPendingPosition="); pw.print(mHasPendingPosition);
        pw.println();
        if (mWin != null) {
            pw.print(prefix + "mWin=");
            pw.println(mWin);
        }
        if (mAdapter != null) {
            pw.print(prefix + "mAdapter=");
            mAdapter.dump(pw, "");
        }
        if (mControlTarget != null) {
            pw.print(prefix + "mControlTarget=");
            pw.println(mControlTarget);
        }
        if (mPendingControlTarget != mControlTarget) {
            pw.print(prefix + "mPendingControlTarget=");
            pw.println(mPendingControlTarget);
        }
        if (mFakeControlTarget != null) {
            pw.print(prefix + "mFakeControlTarget=");
            pw.println(mFakeControlTarget);
        }
    }

    void dumpDebug(@NonNull ProtoOutputStream proto, long fieldId,
            @WindowTracingLogLevel int logLevel) {
        final long token = proto.start(fieldId);
        mSource.dumpDebug(proto, SOURCE);
        mTmpRect.dumpDebug(proto, FRAME);
        mFakeControl.dumpDebug(proto, FAKE_CONTROL);
        if (mControl != null) {
            mControl.dumpDebug(proto, CONTROL);
        }
        if (mControlTarget != null && mControlTarget.getWindow() != null) {
            mControlTarget.getWindow().writeIdentifierToProto(proto, CONTROL_TARGET_IDENTIFIER);
        }
        if (mPendingControlTarget != null && mPendingControlTarget != mControlTarget
                && mPendingControlTarget.getWindow() != null) {
            mPendingControlTarget.getWindow().writeIdentifierToProto(
                    proto, PENDING_CONTROL_TARGET_IDENTIFIER);
        }
        if (mFakeControlTarget != null && mFakeControlTarget.getWindow() != null) {
            mFakeControlTarget.getWindow().writeIdentifierToProto(
                    proto, FAKE_CONTROL_TARGET_IDENTIFIER);
        }
        if (mAdapter != null && mAdapter.mCapturedLeash != null) {
            mAdapter.mCapturedLeash.dumpDebug(proto, CAPTURED_LEASH);
        }
        proto.write(IS_LEASH_READY_FOR_DISPATCHING, isLeashReadyForDispatching());
        proto.write(CLIENT_VISIBLE, mClientVisible);
        proto.write(SERVER_VISIBLE, mServerVisible);
        proto.write(CONTROLLABLE, mControllable);
        if (mWin != null) {
            mWin.writeIdentifierToProto(proto, SOURCE_WINDOW_STATE_IDENTIFIER);
        }
        proto.end(token);
    }

    private final class ControlAdapter implements AnimationAdapter {

        @NonNull
        private final Point mSurfacePosition;
        @Nullable
        private SurfaceControl mCapturedLeash;

        ControlAdapter(@NonNull Point surfacePosition) {
            mSurfacePosition = surfacePosition;
        }

        @Override
        public boolean getShowWallpaper() {
            return false;
        }

        @Override
        public void startAnimation(@NonNull SurfaceControl animationLeash, @NonNull Transaction t,
                @AnimationType int type, @NonNull OnAnimationFinishedCallback finishCallback) {
            ProtoLog.i(WM_DEBUG_WINDOW_INSETS,
                    "ControlAdapter startAnimation mSource: %s controlTarget: %s", mSource,
                    mControlTarget);

            mCapturedLeash = animationLeash;
            t.setPosition(animationLeash, mSurfacePosition.x, mSurfacePosition.y);
        }

        @Override
        public void onAnimationCancelled(@Nullable SurfaceControl animationLeash) {
            if (mAdapter == this) {
                final var controlTarget = mControlTarget;
                if (controlTarget != null) {
                    mStateController.notifyControlRevoked(controlTarget, InsetsSourceProvider.this);
                    mControlTarget = null;
                }
                mStateController.notifySurfaceTransactionReady(InsetsSourceProvider.this, 0, false);
                mControl = null;
                mAdapter = null;
                if (mCapturedLeash == animationLeash) {
                    mCapturedLeash = null;
                }
                setClientVisible((WindowInsets.Type.defaultVisible() & mSource.getType()) != 0);
                ProtoLog.i(WM_DEBUG_WINDOW_INSETS,
                        "ControlAdapter onAnimationCancelled mSource: %s mControlTarget was: %s",
                        mSource, controlTarget);
            }
        }

        @Override
        public long getDurationHint() {
            return 0;
        }

        @Override
        public long getStatusBarTransitionsStartTime() {
            return 0;
        }

        @Override
        public void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
            pw.print(prefix + "ControlAdapter mCapturedLeash=");
            pw.print(mCapturedLeash);
            pw.println();
        }

        @Override
        public void dumpDebug(@NonNull ProtoOutputStream proto) {
        }
    }
}
