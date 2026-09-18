/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.windowdecor;

import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.INPUT_FEATURE_NO_INPUT_CHANNEL;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;
import static android.view.WindowManager.LayoutParams.TYPE_INPUT_CONSUMER;

import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_BOTTOM;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_LEFT;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_RIGHT;
import static com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_TOP;
import static com.android.wm.shell.windowdecor.DragPositioningCallbackUtility.getInputMethodFromMotionEvent;
import static com.android.wm.shell.windowdecor.DragResizeWindowGeometry.isEdgeResizePermitted;
import static com.android.wm.shell.windowdecor.DragResizeWindowGeometry.isEventFromTouchscreen;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Region;
import android.hardware.input.InputManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.Trace;
import android.util.Size;
import android.view.Choreographer;
import android.view.IWindowSession;
import android.view.InputChannel;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.InputEventReceiver;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInputChannelParams;
import android.window.InputTransferToken;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.annotations.ShellBackgroundThread;
import com.android.wm.shell.shared.annotations.ShellMainThread;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * An input event listener registered to InputDispatcher to receive input events on task edges and
 * and corners. Converts them to drag resize requests.
 * Task edges are for resizing with a mouse.
 * Task corners are for resizing with touch input.
 */
class DragResizeInputListener implements AutoCloseable {
    private static final String TAG = "DragResizeInputListener";
    private final IWindowSession mWindowSession;
    private final Supplier<SurfaceControl.Builder> mSurfaceControlBuilderSupplier;
    private final Supplier<SurfaceControl.Transaction> mSurfaceControlTransactionSupplier;

    private final int mDisplayId;

    @VisibleForTesting
    final IBinder mClientToken;

    private final SurfaceControl mDecorationSurface;
    private TaskResizeInputEventReceiver mInputEventReceiver;

    private final Context mContext;
    private final @ShellBackgroundThread ShellExecutor mBgExecutor;
    private final RunningTaskInfo mTaskInfo;
    private final Handler mHandler;
    private final Choreographer mChoreographer;
    private SurfaceControl mInputSinkSurface;
    @VisibleForTesting
    final IBinder mSinkClientToken;
    private InputChannel mSinkInputChannel;
    private final DisplayController mDisplayController;
    private final DragPositioningCallback mDragPositioningCallback;
    private final Region mTouchRegion = new Region();
    private final List<Runnable> mOnInitializedCallbacks = new ArrayList<>();

    private final Runnable mInitInputChannels;
    private boolean mClosed = false;

    DragResizeInputListener(
            Context context,
            IWindowSession windowSession,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellBackgroundThread ShellExecutor bgExecutor,
            RunningTaskInfo taskInfo,
            Handler handler,
            Choreographer choreographer,
            int displayId,
            SurfaceControl decorationSurface,
            DragPositioningCallback callback,
            Supplier<SurfaceControl.Builder> surfaceControlBuilderSupplier,
            Supplier<SurfaceControl.Transaction> surfaceControlTransactionSupplier,
            DisplayController displayController,
            Consumer<MotionEvent> preDragEventConduit) {
        this(context, windowSession, mainExecutor, bgExecutor, taskInfo,
                handler, choreographer, displayId, decorationSurface, callback,
                surfaceControlBuilderSupplier, surfaceControlTransactionSupplier,
                displayController, preDragEventConduit,
                null /* onInputEventReceiverDisposed */, null /* onReceiverCreated */);
    }

    @VisibleForTesting
    DragResizeInputListener(
            Context context,
            IWindowSession windowSession,
            @ShellMainThread ShellExecutor mainExecutor,
            @ShellBackgroundThread ShellExecutor bgExecutor,
            RunningTaskInfo taskInfo,
            Handler handler,
            Choreographer choreographer,
            int displayId,
            SurfaceControl decorationSurface,
            DragPositioningCallback callback,
            Supplier<SurfaceControl.Builder> surfaceControlBuilderSupplier,
            Supplier<SurfaceControl.Transaction> surfaceControlTransactionSupplier,
            DisplayController displayController,
            Consumer<MotionEvent> preDragEventConduit,
            @Nullable Runnable onInputEventReceiverDisposed,
            @Nullable Consumer<InputEventReceiver> onReceiverCreated) {
        mContext = context;
        mWindowSession = windowSession;
        mBgExecutor = bgExecutor;
        mTaskInfo = taskInfo;
        mHandler = handler;
        mChoreographer = choreographer;
        mDisplayId = displayId;
        // Creates a new SurfaceControl pointing the same underlying surface with decorationSurface
        // to ensure that mDecorationSurface will not be released while it's used on the background
        // thread. Note that the empty name will be overridden by the next copyFrom call.
        mDecorationSurface = surfaceControlBuilderSupplier.get().setName("").build();
        mDecorationSurface.copyFrom(decorationSurface, "DragResizeInputListener");
        mDragPositioningCallback = callback;
        mSurfaceControlBuilderSupplier = surfaceControlBuilderSupplier;
        mSurfaceControlTransactionSupplier = surfaceControlTransactionSupplier;
        mDisplayController = displayController;
        mClientToken = new Binder();
        mSinkClientToken = new Binder();

        // Setting up input channels for both the resize listener and the input sink requires
        // multiple blocking binder calls, so it's moved to a bg thread to keep the shell.main
        // thread free.
        // The input event receiver must be created back in the shell.main thread though because
        // its geometry and util methods are updated/queried from the shell.main thread.
        mInitInputChannels = () -> {
            final InputSetUpResult result = setUpInputChannels(mDisplayId, mWindowSession,
                    mDecorationSurface, mClientToken, mSinkClientToken,
                    mSurfaceControlBuilderSupplier,
                    mSurfaceControlTransactionSupplier);
            mainExecutor.execute(() -> {
                if (mClosed) {
                    result.mInputChannel.dispose();
                    result.mSinkInputChannel.dispose();
                    mSurfaceControlTransactionSupplier.get().remove(
                            result.mInputSinkSurface).apply();
                    return;
                }
                mInputSinkSurface = result.mInputSinkSurface;
                mSinkInputChannel = result.mSinkInputChannel;
                Trace.beginSection("DragResizeInputListener#ctor-initReceiver");
                mInputEventReceiver = new TaskResizeInputEventReceiver(
                        mContext,
                        result.mInputChannel,
                        mDragPositioningCallback,
                        mHandler,
                        mChoreographer,
                        () -> {
                            final DisplayLayout layout =
                                    mDisplayController.getDisplayLayout(mDisplayId);
                            return new Size(layout.width(), layout.height());
                        },
                        this::updateSinkInputChannel,
                        preDragEventConduit,
                        onInputEventReceiverDisposed);
                mInputEventReceiver.setTouchSlop(
                        ViewConfiguration.get(mContext).getScaledTouchSlop());
                for (Runnable initCallback : mOnInitializedCallbacks) {
                    initCallback.run();
                }
                mOnInitializedCallbacks.clear();
                if (onReceiverCreated != null) {
                    onReceiverCreated.accept(mInputEventReceiver);
                }
                Trace.endSection();
            });
        };
        bgExecutor.execute(mInitInputChannels);
    }

    /**
     * Registers a callback to be invoked when the input listener has finished initializing. If
     * already finished, the callback will be invoked immediately.
     */
    void addInitializedCallback(Runnable onReady) {
        if (mInputEventReceiver != null) {
            onReady.run();
            return;
        }
        mOnInitializedCallbacks.add(onReady);
    }

    @ShellBackgroundThread
    private static InputSetUpResult setUpInputChannels(
            int displayId,
            @NonNull IWindowSession windowSession,
            @NonNull SurfaceControl decorationSurface,
            @NonNull IBinder clientToken,
            @NonNull IBinder sinkClientToken,
            @NonNull Supplier<SurfaceControl.Builder> surfaceControlBuilderSupplier,
            @NonNull Supplier<SurfaceControl.Transaction> surfaceControlTransactionSupplier) {
        Trace.beginSection("DragResizeInputListener#setUpInputChannels");
        InputChannel inputChannel = null;
        final InputTransferToken inputTransferToken = new InputTransferToken();
        try {
            final WindowInputChannelParams params = new WindowInputChannelParams();
            params.displayId = displayId;
            params.surface = decorationSurface;
            params.clientToken = clientToken;
            params.inputTransferToken = inputTransferToken;
            params.type = TYPE_APPLICATION;
            params.flags = FLAG_NOT_FOCUSABLE;
            params.privateFlags = PRIVATE_FLAG_TRUSTED_OVERLAY;
            params.inputHandleName = TAG + " of " + decorationSurface;
            inputChannel = windowSession.grantInputChannel(params);
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }

        final SurfaceControl inputSinkSurface = surfaceControlBuilderSupplier.get()
                .setName("TaskInputSink of " + decorationSurface)
                .setContainerLayer()
                .setParent(decorationSurface)
                .setCallsite("DragResizeInputListener.setUpInputChannels")
                .build();
        surfaceControlTransactionSupplier.get()
                .setLayer(inputSinkSurface, WindowDecoration.INPUT_SINK_Z_ORDER)
                .show(inputSinkSurface)
                .apply();

        InputChannel sinkInputChannel = null;
        try {
            final WindowInputChannelParams params = new WindowInputChannelParams();
            params.displayId = displayId;
            params.surface = inputSinkSurface;
            params.clientToken = sinkClientToken;
            params.inputTransferToken = inputTransferToken;
            params.type = TYPE_INPUT_CONSUMER;
            params.flags = FLAG_NOT_FOCUSABLE;
            params.inputFeatures = INPUT_FEATURE_NO_INPUT_CHANNEL;
            params.inputHandleName = "TaskInputSink of " + decorationSurface;
            sinkInputChannel = windowSession.grantInputChannel(params);
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }
        Trace.endSection();
        return new InputSetUpResult(inputSinkSurface, inputChannel, sinkInputChannel);
    }

    /**
     * Updates the geometry (the touch region) of this drag resize handler.
     *
     * @param incomingGeometry The geometry update to apply for this task's drag resize regions.
     * @param touchSlop        The distance in pixels user has to drag with touch for it to register
     *                         as a resize action.
     * @return whether the geometry has changed or not
     */
    boolean setGeometry(@NonNull DragResizeWindowGeometry incomingGeometry, int touchSlop) {
        DragResizeWindowGeometry geometry = mInputEventReceiver.getGeometry();
        if (incomingGeometry.equals(geometry)) {
            // Geometry hasn't changed size so skip all updates.
            return false;
        } else {
            geometry = incomingGeometry;
        }
        mInputEventReceiver.setTouchSlop(touchSlop);

        mTouchRegion.setEmpty();
        // Apply the geometry to the touch region.
        geometry.union(mTouchRegion);
        mInputEventReceiver.setGeometry(geometry);
        mInputEventReceiver.setTouchRegion(mTouchRegion);

        try {
            final WindowInputChannelParams params = new WindowInputChannelParams();
            params.displayId = mDisplayId;
            params.channelToken = mInputEventReceiver.getToken();
            params.surface = mDecorationSurface;
            params.flags = FLAG_NOT_FOCUSABLE;
            params.privateFlags = PRIVATE_FLAG_TRUSTED_OVERLAY;
            params.region = mTouchRegion;
            mWindowSession.updateInputChannel(params);
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }

        final Size taskSize = geometry.getTaskSize();
        mSurfaceControlTransactionSupplier.get()
                .setWindowCrop(mInputSinkSurface, taskSize.getWidth(), taskSize.getHeight())
                .apply();
        // The touch region of the TaskInputSink should be the touch region of this
        // DragResizeInputHandler minus the task bounds. Pilfering events isn't enough to prevent
        // input windows from handling down events, which will bring tasks in the back to front.
        //
        // Note not the entire touch region responds to both mouse and touchscreen events.
        // Therefore, in the region that only responds to one of them, it would be a no-op to
        // perform a gesture in the other type of events. We currently only have a mouse-only region
        // out of the task bounds, and due to the roughness of touchscreen events, it's not a severe
        // issue. However, were there touchscreen-only a region out of the task bounds, mouse
        // gestures will become no-op in that region, even though the mouse gestures may appear to
        // be performed on the input window behind the resize handle.
        mTouchRegion.op(0, 0, taskSize.getWidth(), taskSize.getHeight(), Region.Op.DIFFERENCE);
        updateSinkInputChannel(mTouchRegion);
        return true;
    }

    /**
     * Generate a Region that encapsulates all 4 corner handles and window edges.
     */
    @NonNull Region getCornersRegion() {
        return mInputEventReceiver.getCornersRegion();
    }

    private void updateSinkInputChannel(Region region) {
        try {
            final WindowInputChannelParams params = new WindowInputChannelParams();
            params.displayId = mDisplayId;
            params.channelToken = mSinkInputChannel.getToken();
            params.surface = mInputSinkSurface;
            params.flags = FLAG_NOT_FOCUSABLE;
            params.inputFeatures = INPUT_FEATURE_NO_INPUT_CHANNEL;
            params.region = region;
            mWindowSession.updateInputChannel(params);
        } catch (RemoteException ex) {
            ex.rethrowFromSystemServer();
        }
    }

    boolean shouldHandleEvent(@NonNull MotionEvent e, @NonNull Point offset) {
        return mInputEventReceiver != null && mInputEventReceiver.shouldHandleEvent(e, offset);
    }

    boolean isHandlingDragResize() {
        return mInputEventReceiver != null && mInputEventReceiver.isHandlingEvents();
    }

    @Override
    public void close() {
        mClosed = true;
        if (mInitInputChannels != null) {
            mBgExecutor.removeCallbacks(mInitInputChannels);
        }
        if (mInputEventReceiver != null) {
            mInputEventReceiver.dispose();
        }
        if (mSinkInputChannel != null) {
            mSinkInputChannel.dispose();
        }

        if (mInputSinkSurface != null) {
            mSurfaceControlTransactionSupplier.get()
                    .remove(mInputSinkSurface)
                    .apply();
        }

        mBgExecutor.execute(() -> {
            try {
                mWindowSession.remove(mClientToken);
                mWindowSession.remove(mSinkClientToken);
            } catch (RemoteException e) {
                e.rethrowFromSystemServer();
            }
            // Removing this surface on the background thread to ensure that mInitInputChannels has
            // already been finished.
            // Do not |remove| the surface, the decoration might still be needed even if
            // drag-resizing isn't.
            mDecorationSurface.release();
        });
    }

    private static class InputSetUpResult {
        final @NonNull SurfaceControl mInputSinkSurface;
        final @NonNull InputChannel mInputChannel;
        final @NonNull InputChannel mSinkInputChannel;

        InputSetUpResult(@NonNull SurfaceControl inputSinkSurface,
                @NonNull InputChannel inputChannel,
                @NonNull InputChannel sinkInputChannel) {
            mInputSinkSurface = inputSinkSurface;
            mInputChannel = inputChannel;
            mSinkInputChannel = sinkInputChannel;
        }
    }

    /**
     * An input event receiver to handle motion events on the task's corners and edges for
     * drag-resizing, as well as keeping the input sink updated.
     */
    private static class TaskResizeInputEventReceiver extends InputEventReceiver implements
            DragDetector.MotionEventHandler {
        @NonNull private final Context mContext;
        private final InputManager mInputManager;
        @NonNull private final DragPositioningCallback mCallback;
        @NonNull private final Choreographer mChoreographer;
        @NonNull private final Choreographer.VsyncCallback mConsumeBatchEventCallback;
        @NonNull private final DragDetector mDragDetector;
        @NonNull private final Supplier<Size> mDisplayLayoutSizeSupplier;
        @NonNull private final Consumer<Region> mTouchRegionConsumer;
        @NonNull private final Consumer<MotionEvent> mPreDragEventConduit;
        @Nullable private final Runnable mOnDisposed;
        private final MotionEvent.PointerProperties mTmpPointerProperties =
                new MotionEvent.PointerProperties();
        private final Rect mTmpRect = new Rect();
        private boolean mConsumeBatchEventScheduled;
        private DragResizeWindowGeometry mDragResizeWindowGeometry;
        private Region mTouchRegion;
        private boolean mShouldHandleEvents;
        private int mLastCursorType = PointerIcon.TYPE_DEFAULT;
        private Rect mDragStartTaskBounds;
        // The id of the particular pointer in a MotionEvent that we are listening to for drag
        // resize events. For example, if multiple fingers are touching the screen, then each one
        // has a separate pointer id, but we only accept drag input from one.
        private int mDragPointerId = -1;
        private boolean mDragStarted;

        private TaskResizeInputEventReceiver(@NonNull Context context,
                @NonNull InputChannel inputChannel,
                @NonNull DragPositioningCallback callback, @NonNull Handler handler,
                @NonNull Choreographer choreographer,
                @NonNull Supplier<Size> displayLayoutSizeSupplier,
                @NonNull Consumer<Region> touchRegionConsumer,
                @NonNull Consumer<MotionEvent> preDragEventConduit,
                @Nullable Runnable onDisposed) {
            super(inputChannel, handler.getLooper());
            mContext = context;
            mInputManager = context.getSystemService(InputManager.class);
            mCallback = callback;
            mChoreographer = choreographer;

            mConsumeBatchEventCallback = frameData -> {
                mConsumeBatchEventScheduled = false;
                if (consumeBatchedInputEvents(frameData.getFrameTimeNanos())) {
                    // If we consumed a batch here, we want to go ahead and schedule the
                    // consumption of batched input events on the next frame. Otherwise, we would
                    // wait until we have more input events pending and might get starved by other
                    // things occurring in the process.
                    scheduleConsumeBatchEvent();
                }
            };

            mDragDetector = new DragDetector(this, 0 /* holdToDragMinDurationMs */,
                    ViewConfiguration.get(mContext).getScaledTouchSlop());
            mDisplayLayoutSizeSupplier = displayLayoutSizeSupplier;
            mTouchRegionConsumer = touchRegionConsumer;
            mPreDragEventConduit = preDragEventConduit;

            mOnDisposed = onDisposed;
        }

        /**
         * Returns the geometry of the areas to drag resize.
         */
        DragResizeWindowGeometry getGeometry() {
            return mDragResizeWindowGeometry;
        }

        /**
         * Updates the geometry of the areas to drag resize.
         */
        void setGeometry(@NonNull DragResizeWindowGeometry dragResizeWindowGeometry) {
            mDragResizeWindowGeometry = dragResizeWindowGeometry;
        }

        /**
         * Sets how much slop to allow for touches.
         */
        void setTouchSlop(int touchSlop) {
            mDragDetector.setTouchSlop(touchSlop);
        }

        /**
         * Updates the region accepting input for drag resizing the task.
         */
        void setTouchRegion(@NonNull Region touchRegion) {
            mTouchRegion = touchRegion;
        }

        /**
         * Returns the union of all regions that can be touched for drag resizing; the corners and
         * window edges.
         */
        @NonNull Region getCornersRegion() {
            Region region = new Region();
            mDragResizeWindowGeometry.union(region);
            return region;
        }

        @Override
        public void onBatchedInputEventPending(int source) {
            scheduleConsumeBatchEvent();
        }

        private void scheduleConsumeBatchEvent() {
            if (mConsumeBatchEventScheduled) {
                return;
            }
            mChoreographer.postVsyncCallback(
                    Choreographer.CALLBACK_INPUT, mConsumeBatchEventCallback);
            mConsumeBatchEventScheduled = true;
        }

        @Override
        public void onInputEvent(InputEvent inputEvent) {
            finishInputEvent(inputEvent, handleInputEvent(inputEvent));
        }

        boolean isHandlingEvents() {
            return mShouldHandleEvents;
        }

        private boolean handleInputEvent(InputEvent inputEvent) {
            if (!(inputEvent instanceof MotionEvent motionEvent)) {
                return false;
            }
            final boolean dragHasStarted = mDragStarted;
            final boolean result = mDragDetector.onMotionEvent(motionEvent);

            // The logic below sends input events before the drag detector determines that this is a
            // drag gesture. This is necessary because after the drag resize handles aren't spy
            // windows anymore, the app header buttons can't automatically receive touches/clicks in
            // the overlapping area with the drag handles at the top two corners. This conduit
            // sends those events explicitly. See b/450722440 for more details.

            // Up and cancel conclude the gesture, so mDragStarted is reset back to false at this
            // point. We should use the state before we call the drag detector to decide if they
            // should be redispatched.
            final boolean isUpOrCancel = motionEvent.getAction() == MotionEvent.ACTION_UP
                            || motionEvent.getAction() == MotionEvent.ACTION_CANCEL;
            final boolean dragStarted = isUpOrCancel ? dragHasStarted : mDragStarted;
            if (!dragStarted) {
                mPreDragEventConduit.accept(motionEvent);
            }
            if (!dragHasStarted && mDragStarted) {
                // This is the first time when a gesture is determined to be a drag. We should send
                // a cancel event to the conduit so the caption buttons get a result of the gesture
                // and without acting upon it.
                final int oldAction = motionEvent.getAction();
                motionEvent.setAction(MotionEvent.ACTION_CANCEL);
                mPreDragEventConduit.accept(motionEvent);
                motionEvent.setAction(oldAction);
            }

            return result;
        }

        @Override
        public boolean handleMotionEvent(View v, MotionEvent e) {
            boolean result = false;

            // Check if this is a touch event vs mouse event.
            // Touch events are tracked in four corners. Other events are tracked in resize edges.
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    mShouldHandleEvents = shouldHandleEvent(e, new Point() /* offset */);
                    if (mShouldHandleEvents) {
                        // Save the id of the pointer for this drag interaction; we will use the
                        // same pointer for all subsequent MotionEvents in this interaction.
                        mDragPointerId = e.getPointerId(0);
                        float x = e.getX(0);
                        float y = e.getY(0);
                        float rawX = e.getRawX(0);
                        float rawY = e.getRawY(0);
                        final int ctrlType = mDragResizeWindowGeometry.calculateCtrlType(
                                isEventFromTouchscreen(e), isEdgeResizePermitted(e), x,
                                y);
                        ProtoLog.d(WM_SHELL_DESKTOP_MODE,
                                "%s: Handling action down, update ctrlType to %d", TAG, ctrlType);
                        mDragStartTaskBounds = mCallback.onDragPositioningStart(ctrlType,
                                e.getDisplayId(), rawX, rawY, getInputMethodFromMotionEvent(e));
                        // Increase the input sink region to cover the whole screen; this is to
                        // prevent input and focus from going to other tasks during a drag resize.
                        updateInputSinkRegionForDrag(mDragStartTaskBounds);
                        result = true;
                    } else {
                        ProtoLog.d(WM_SHELL_DESKTOP_MODE,
                                "%s: Handling action down, but ignore event", TAG);
                    }
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (!mShouldHandleEvents) {
                        break;
                    }
                    final int dragPointerIndex = e.findPointerIndex(mDragPointerId);
                    if (dragPointerIndex < 0) {
                        ProtoLog.d(WM_SHELL_DESKTOP_MODE,
                                "%s: Handling action move, but ignore event due to invalid "
                                        + "pointer index",
                                TAG);
                        break;
                    }
                    mDragStarted = true;
                    final float rawX = e.getRawX(dragPointerIndex);
                    final float rawY = e.getRawY(dragPointerIndex);
                    final Rect taskBounds = mCallback.onDragPositioningMove(e.getDisplayId(),
                            rawX, rawY);
                    updateInputSinkRegionForDrag(taskBounds);
                    result = true;
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    if (mShouldHandleEvents) {
                        final int dragPointerIndex = e.findPointerIndex(mDragPointerId);
                        if (dragPointerIndex < 0) {
                            ProtoLog.d(WM_SHELL_DESKTOP_MODE,
                                    "%s: Handling action %d, but ignore event due to invalid "
                                            + "pointer index",
                                    TAG, e.getActionMasked());
                            break;
                        }
                        final Rect taskBounds = mCallback.onDragPositioningEnd(e.getDisplayId(),
                                e.getRawX(dragPointerIndex), e.getRawY(dragPointerIndex));
                        // If taskBounds has changed, setGeometry will be called and update the
                        // sink region. Otherwise, we should revert it here.
                        if (taskBounds.equals(mDragStartTaskBounds)) {
                            mTouchRegionConsumer.accept(mTouchRegion);
                        }
                    }
                    mShouldHandleEvents = false;
                    mDragPointerId = -1;
                    mDragStarted = false;
                    result = true;
                    break;
                }
                case MotionEvent.ACTION_HOVER_ENTER:
                case MotionEvent.ACTION_HOVER_MOVE: {
                    updateCursorType(e);
                    result = true;
                    break;
                }
                case MotionEvent.ACTION_HOVER_EXIT:
                    result = true;
                    break;
            }
            return result;
        }

        private void updateInputSinkRegionForDrag(Rect taskBounds) {
            mTmpRect.set(taskBounds);
            final Size displayLayoutSize = mDisplayLayoutSizeSupplier.get();
            final Region dragTouchRegion = new Region(-taskBounds.left, -taskBounds.top,
                    -taskBounds.left + displayLayoutSize.getWidth(),
                    -taskBounds.top + displayLayoutSize.getHeight());
            // Remove the localized task bounds from the touch region.
            mTmpRect.offsetTo(0, 0);
            dragTouchRegion.op(mTmpRect, Region.Op.DIFFERENCE);
            mTouchRegionConsumer.accept(dragTouchRegion);
        }

        private void updateCursorType(MotionEvent e) {
            if ((e.getSource() & InputDevice.SOURCE_CLASS_POINTER) == 0) {
                return;
            }

            e.getPointerProperties(0 /* pointerIndex */, mTmpPointerProperties);
            if (mTmpPointerProperties.toolType != MotionEvent.TOOL_TYPE_MOUSE
                    && mTmpPointerProperties.toolType != MotionEvent.TOOL_TYPE_FINGER) {
                // We're deciding if we should update the mouse cursor, so we shouldn't respond to
                // events from styli or erasers. Fingers are in scope because they are used on
                // touchpads.
                return;
            }

            final int displayId = e.getDisplayId();
            final int deviceId = e.getDeviceId();
            final int pointerId = mTmpPointerProperties.id;
            final float x = e.getX();
            final float y = e.getY();

            // Since we are handling cursor, we know that this is not a touchscreen event, and
            // that edge resizing should always be allowed.
            @DragPositioningCallback.CtrlType int ctrlType =
                    mDragResizeWindowGeometry.calculateCtrlType(/* isTouchscreen= */ false,
                            /* isEdgeResizePermitted= */ true, x, y);

            int cursorType = PointerIcon.TYPE_DEFAULT;
            switch (ctrlType) {
                case CTRL_TYPE_LEFT:
                case CTRL_TYPE_RIGHT:
                    cursorType = PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW;
                    break;
                case CTRL_TYPE_TOP:
                case CTRL_TYPE_BOTTOM:
                    cursorType = PointerIcon.TYPE_VERTICAL_DOUBLE_ARROW;
                    break;
                case CTRL_TYPE_LEFT | CTRL_TYPE_TOP:
                case CTRL_TYPE_RIGHT | CTRL_TYPE_BOTTOM:
                    cursorType = PointerIcon.TYPE_TOP_LEFT_DIAGONAL_DOUBLE_ARROW;
                    break;
                case CTRL_TYPE_LEFT | CTRL_TYPE_BOTTOM:
                case CTRL_TYPE_RIGHT | CTRL_TYPE_TOP:
                    cursorType = PointerIcon.TYPE_TOP_RIGHT_DIAGONAL_DOUBLE_ARROW;
                    break;
            }
            // Only update the cursor type to default once so that views behind the decor container
            // layer that aren't in the active resizing regions have chances to update the cursor
            // type. We would like to enforce the cursor type by setting the cursor type multiple
            // times in active regions because we shouldn't allow the views behind to change it, as
            // we'll pilfer the gesture initiated in this area. This is necessary because 1) we
            // should allow the views behind regions only for touches to set the cursor type; and 2)
            // there is a small region out of each rounded corner that's inside the task bounds,
            // where views in the task can receive input events because we can't set touch regions
            // of input sinks to have rounded corners.
            if (mLastCursorType != cursorType || cursorType != PointerIcon.TYPE_DEFAULT) {
                ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: update pointer icon from %d to %d",
                        TAG, mLastCursorType, cursorType);
                mInputManager.setPointerIcon(PointerIcon.getSystemIcon(mContext, cursorType),
                        displayId, deviceId, pointerId, getToken());
                mLastCursorType = cursorType;
            }
        }

        private boolean shouldHandleEvent(MotionEvent e, Point offset) {
            return mDragResizeWindowGeometry.shouldHandleEvent(e, offset);
        }

        @Override
        public void dispose() {
            super.dispose();
            if (mOnDisposed != null) {
                mOnDisposed.run();
            }
        }
    }
}
