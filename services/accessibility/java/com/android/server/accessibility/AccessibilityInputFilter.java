/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.server.accessibility;

import static android.view.InputDevice.SOURCE_CLASS_POINTER;
import static android.view.MotionEvent.ACTION_SCROLL;
import static android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_MAGNIFICATION_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_MAGNIFICATION_OVERLAY;

import android.accessibilityservice.AccessibilityTrace;
import android.annotation.MainThread;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.graphics.Region;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.provider.Settings.Secure.AccessibilityMagnificationCursorFollowingMode;
import android.util.Slog;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.Display;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.InputFilter;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalServices;
import com.android.server.accessibility.autoclick.AutoclickController;
import com.android.server.accessibility.gestures.TouchExplorer;
import com.android.server.accessibility.magnification.FullScreenMagnificationController;
import com.android.server.accessibility.magnification.FullScreenMagnificationGestureHandler;
import com.android.server.accessibility.magnification.FullScreenMagnificationPointerMotionEventFilter;
import com.android.server.accessibility.magnification.FullScreenMagnificationVibrationHelper;
import com.android.server.accessibility.magnification.MagnificationGestureHandler;
import com.android.server.accessibility.magnification.MagnificationKeyHandler;
import com.android.server.accessibility.magnification.WindowMagnificationGestureHandler;
import com.android.server.accessibility.magnification.WindowMagnificationPromptController;
import com.android.server.input.InputManagerInternal;
import com.android.server.policy.WindowManagerPolicy;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.StringJoiner;

/**
 * This class is an input filter for implementing accessibility features such
 * as display magnification and explore by touch.
 *
 * NOTE: This class has to be created and poked only from the main thread.
 */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public class AccessibilityInputFilter extends InputFilter implements EventStreamTransformation {

    private static final String TAG = AccessibilityInputFilter.class.getSimpleName();

    private static final boolean DEBUG = AccessibilityLogUtil.isDebugEnabled(TAG);

    /**
     * Flag for disabling all InputFilter features.
     *
     * <p>
     * This flag is used to disable all the enabled features, so it should not be used with other
     * flags.
     * <p>
     */
    private static final int FLAG_FEATURE_NONE = 0x00000000;

    /**
     * Flag for enabling the screen magnification feature.
     *
     * @see #setUserAndEnabledFeatures(int, int)
     */
    static final int FLAG_FEATURE_MAGNIFICATION_SINGLE_FINGER_TRIPLE_TAP = 0x00000001;

    /**
     * Flag for enabling the touch exploration feature.
     *
     * @see #setUserAndEnabledFeatures(int, int)
     */
    static final int FLAG_FEATURE_TOUCH_EXPLORATION = 0x00000002;

    /**
     * Flag for enabling the filtering key events feature.
     *
     * @see #setUserAndEnabledFeatures(int, int)
     */
    static final int FLAG_FEATURE_FILTER_KEY_EVENTS = 0x00000004;

    /**
     * Flag for enabling "Automatically click on mouse stop" feature.
     *
     * @see #setUserAndEnabledFeatures(int, int)
     */
    static final int FLAG_FEATURE_AUTOCLICK = 0x00000008;

    /**
     * Flag for enabling motion event injection.
     *
     * @see #setUserAndEnabledFeatures(int, int)
     */
    static final int FLAG_FEATURE_INJECT_MOTION_EVENTS = 0x00000010;

    /**
     * Flag for enabling the feature to control the screen magnifier. If
     * {@link #FLAG_FEATURE_MAGNIFICATION_SINGLE_FINGER_TRIPLE_TAP} is set this flag is ignored
     * as the screen magnifier feature performs a super set of the work
     * performed by this feature.
     *
     * @see #setUserAndEnabledFeatures(int, int)
     */
    static final int FLAG_FEATURE_CONTROL_SCREEN_MAGNIFIER = 0x00000020;

    /**
     * Flag for enabling the feature to trigger the screen magnifier
     * from another on-device interaction.
     */
    static final int FLAG_FEATURE_TRIGGERED_SCREEN_MAGNIFIER = 0x00000040;

    /**
     * Flag for dispatching double tap and double tap and hold to the service.
     *
     * @see #setUserAndEnabledFeatures(int, int)
     */
    static final int FLAG_SERVICE_HANDLES_DOUBLE_TAP = 0x00000080;

    /**
     * Flag for enabling multi-finger gestures.
     *
     * @see #setUserAndEnabledFeatures(int, int)
     */
    static final int FLAG_REQUEST_MULTI_FINGER_GESTURES = 0x00000100;

    /**
     * Flag for enabling two-finger passthrough when multi-finger gestures are enabled.
     *
     * @see #setUserAndEnabledFeatures(int, int)
     */
    static final int FLAG_REQUEST_2_FINGER_PASSTHROUGH = 0x00000200;

    /**
     * Flag for including motion events when dispatching a gesture.
     *
     * @see #setUserAndEnabledFeatures(int, int)
     */
    static final int FLAG_SEND_MOTION_EVENTS = 0x00000400;

    /** Flag for intercepting generic motion events. */
    static final int FLAG_FEATURE_INTERCEPT_GENERIC_MOTION_EVENTS = 0x00000800;

    /**
     * Flag for enabling the Accessibility mouse key events feature.
     *
     * @see #setUserAndEnabledFeatures(int, int)
     */
    static final int FLAG_FEATURE_MOUSE_KEYS = 0x00002000;

    static final int FEATURES_AFFECTING_MOTION_EVENTS =
            FLAG_FEATURE_INJECT_MOTION_EVENTS
                    | FLAG_FEATURE_AUTOCLICK
                    | FLAG_FEATURE_TOUCH_EXPLORATION
                    | FLAG_FEATURE_MAGNIFICATION_SINGLE_FINGER_TRIPLE_TAP
                    | FLAG_FEATURE_TRIGGERED_SCREEN_MAGNIFIER
                    | FLAG_SERVICE_HANDLES_DOUBLE_TAP
                    | FLAG_REQUEST_MULTI_FINGER_GESTURES
                    | FLAG_REQUEST_2_FINGER_PASSTHROUGH
                    | FLAG_FEATURE_INTERCEPT_GENERIC_MOTION_EVENTS;

    private final Context mContext;

    private final PowerManager mPm;

    private final AccessibilityManagerService mAms;

    private final SparseArray<EventStreamTransformation> mEventHandler;

    private final SparseArray<TouchExplorer> mTouchExplorer = new SparseArray<>(0);

    private final SparseArray<MagnificationGestureHandler> mMagnificationGestureHandler;

    @Nullable
    private FullScreenMagnificationPointerMotionEventFilter
            mFullScreenMagnificationPointerMotionEventFilter;

    private final SparseArray<MotionEventInjector> mMotionEventInjectors = new SparseArray<>(0);

    private AutoclickController mAutoclickController;

    private KeyboardInterceptor mKeyboardInterceptor;

    private MouseKeysInterceptor mMouseKeysInterceptor;

    private MagnificationKeyHandler mMagnificationKeyHandler;

    private boolean mInstalled;

    private int mUserId;

    private int mEnabledFeatures;

    // Display-specific features
    private SparseArray<Boolean> mServiceDetectsGestures = new SparseArray<>();
    private final SparseArray<EventStreamState> mMouseStreamStates = new SparseArray<>(0);

    private final SparseArray<EventStreamState> mTouchScreenStreamStates = new SparseArray<>(0);

    // State tracking for generic MotionEvents is display-agnostic so we only need one.
    private GenericMotionEventStreamState mGenericMotionEventStreamState;
    private int mCombinedGenericMotionEventSources = 0;
    private int mCombinedMotionEventObservedSources = 0;

    private EventStreamState mKeyboardStreamState;

    private final Handler mHandler;

    /**
     * The last MotionEvent emitted from the input device that's currently active. This is used to
     * keep track of which input device is currently active, and also to generate the cancel event
     * if a new device becomes active.
     */
    private MotionEvent mLastActiveDeviceMotionEvent = null;

    @Nullable
    private final AccessibilityInputDebugger mInputDebugger;

    private static MotionEvent cancelMotion(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL
                || event.getActionMasked() == MotionEvent.ACTION_HOVER_EXIT
                || event.getActionMasked() == MotionEvent.ACTION_UP) {
            throw new IllegalArgumentException("Can't cancel " + event);
        }
        final int action;
        if (event.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER
                || event.getActionMasked() == MotionEvent.ACTION_HOVER_MOVE) {
            action = MotionEvent.ACTION_HOVER_EXIT;
        } else {
            action = MotionEvent.ACTION_CANCEL;
        }

        AccessibilityMotionEventBuilder builder = AccessibilityMotionEventBuilder.fromBaseEvent(
                        event)
                .setAction(action);

        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
            // When cancelling a POINTER_UP event, the pointer that went up is excluded from the
            // resulting CANCEL event. This is because the cancellation applies to the gesture
            // that would have continued with the remaining pointers.
            builder.excludePointer(event.getActionIndex());
        }

        return builder.build();
    }

    AccessibilityInputFilter(Context context, AccessibilityManagerService service) {
        this(context, service, new SparseArray<>(0), new SparseArray<>(0),
                new Handler(context.getMainLooper()));
    }

    AccessibilityInputFilter(Context context, AccessibilityManagerService service,
            SparseArray<EventStreamTransformation> eventHandler,
            SparseArray<MagnificationGestureHandler> magnificationGestureHandler,
            Looper looper) {
        this(context, service, eventHandler, magnificationGestureHandler, new Handler(looper));
    }

    AccessibilityInputFilter(Context context, AccessibilityManagerService service,
            SparseArray<EventStreamTransformation> eventHandler,
            SparseArray<MagnificationGestureHandler> magnificationGestureHandler,
            Handler handler) {
        super(handler.getLooper());
        mContext = context;
        mAms = service;
        mPm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        mEventHandler = eventHandler;
        mMagnificationGestureHandler = magnificationGestureHandler;
        mHandler = handler;
        // Enable debugger only for debug builds or when debug logging is active.
        mInputDebugger = (DEBUG || Build.isDebuggable())
                ? new AccessibilityInputDebugger()
                : null;
    }

    @Override
    public void onInstalled() {
        if (DEBUG) {
            Slog.d(TAG, "Accessibility input filter installed.");
        }
        mInstalled = true;
        disableFeatures(/* featuresToBeEnabled= */ FLAG_FEATURE_NONE);
        enableFeatures();
        if (mInputDebugger != null) {
            mInputDebugger.clearCachedEvents();
        }
        mAms.onInputFilterInstalled(true);
        super.onInstalled();
    }

    @Override
    public void onUninstalled() {
        if (DEBUG) {
            Slog.d(TAG, "Accessibility input filter uninstalled.");
        }
        mInstalled = false;
        disableFeatures(/* featuresToBeEnabled= */ FLAG_FEATURE_NONE);
        if (mInputDebugger != null) {
            mInputDebugger.clearCachedEvents();
        }
        mAms.onInputFilterInstalled(false);
        super.onUninstalled();
    }

    void onDisplayAdded(@NonNull Display display) {
        enableFeaturesForDisplayIfInstalled(display);
    }

    void onDisplayRemoved(int displayId) {
        disableFeaturesForDisplayIfInstalled(displayId);
    }

    @Override
    public void onInputEvent(InputEvent event, int policyFlags) {
        if (DEBUG) {
            Slog.d(TAG, "Received event: " + event + ", policyFlags=0x"
                    + Integer.toHexString(policyFlags));
        }
        if (mAms.getTraceManager().isA11yTracingEnabledForTypes(
                AccessibilityTrace.FLAGS_INPUT_FILTER)) {
            mAms.getTraceManager().logTrace(TAG + ".onInputEvent",
                    AccessibilityTrace.FLAGS_INPUT_FILTER,
                    "event=" + event + ";policyFlags=" + policyFlags);
        }

        // Note: shouldProcessMultiDeviceEvent() calls updateLastActiveDeviceMotionEvent()
        // internally to update the mLastActiveDeviceMotionEvent which is required for
        // sending cancel motion event when resetting stream.
        if (!shouldProcessMultiDeviceEvent(event, policyFlags)) {
            return;
        }

        if (mInputDebugger != null) {
            mInputDebugger.onReceiveEvent(event);
        }
        onInputEventInternal(event, policyFlags);
    }

    @Override
    public void sendInputEvent(InputEvent event, int policyFlags) {
        if (mInputDebugger != null) {
            mInputDebugger.onSendEvent(event);
        }
        super.sendInputEvent(event, policyFlags);
    }

    @Override
    public void onSendInputEventException(Exception exception) {
        if (mInputDebugger != null) {
            mInputDebugger.onSendEventException(exception);
        }
    }

    private void onInputEventInternal(InputEvent event, int policyFlags) {
        final int displayId = event.getDisplayId();
        if (mEventHandler.size() == 0 || (Flags.ignoreInputEventsFromDisplayWithoutHandler()
                && displayId != Display.INVALID_DISPLAY && mEventHandler.get(displayId) == null)) {
            if (DEBUG) Slog.d(TAG, "No mEventHandler for event " + event);
            super.onInputEvent(event, policyFlags);
            return;
        }

        EventStreamState state = getEventStreamState(event);
        if (state == null) {
            super.onInputEvent(event, policyFlags);
            return;
        }

        final int eventSource = event.getSource();
        if ((policyFlags & WindowManagerPolicy.FLAG_PASS_TO_USER) == 0) {
            if (DEBUG) {
                Slog.d(TAG, "Not processing event " + event);
            }
            super.onInputEvent(event, policyFlags);
            return;
        }

        if (state.updateInputSource(event.getSource())) {
            clearEventStreamHandler(displayId, eventSource);
        }

        if (!state.inputSourceValid()) {
            super.onInputEvent(event, policyFlags);
            return;
        }

        if (event instanceof MotionEvent) {
            if ((mEnabledFeatures & FEATURES_AFFECTING_MOTION_EVENTS) != 0) {
                MotionEvent motionEvent = (MotionEvent) event;
                processMotionEvent(state, motionEvent, policyFlags);
                return;
            } else {
                super.onInputEvent(event, policyFlags);
            }
        } else if (event instanceof KeyEvent) {
            KeyEvent keyEvent = (KeyEvent) event;
            processKeyEvent(state, keyEvent, policyFlags);
        }
    }

    /**
     * Gets current event stream state associated with an input event.
     * @return The event stream state that should be used for the event. Null if the event should
     *     not be handled by #AccessibilityInputFilter.
     */
    private EventStreamState getEventStreamState(InputEvent event) {
        if (event instanceof MotionEvent) {
            final int displayId = event.getDisplayId();
            if (mGenericMotionEventStreamState == null) {
                mGenericMotionEventStreamState = new GenericMotionEventStreamState();
            }

            if (mGenericMotionEventStreamState.shouldProcessMotionEvent((MotionEvent) event)) {
                return mGenericMotionEventStreamState;
            }
            if (event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) {
                EventStreamState touchScreenStreamState = mTouchScreenStreamStates.get(displayId);
                if (touchScreenStreamState == null) {
                    touchScreenStreamState = new TouchScreenEventStreamState();
                    mTouchScreenStreamStates.put(displayId, touchScreenStreamState);
                }
                return touchScreenStreamState;
            }
            if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
                EventStreamState mouseStreamState = mMouseStreamStates.get(displayId);
                if (mouseStreamState == null) {
                    mouseStreamState = new MouseEventStreamState();
                    mMouseStreamStates.put(displayId, mouseStreamState);
                }
                return mouseStreamState;
            }
        } else if (event instanceof KeyEvent) {
            if (event.isFromSource(InputDevice.SOURCE_KEYBOARD)) {
                if (mKeyboardStreamState == null) {
                    mKeyboardStreamState = new KeyboardEventStreamState();
                }
                return mKeyboardStreamState;
            }
        }
        return null;
    }

    private void clearEventStreamHandler(int displayId, int eventSource) {
        final EventStreamTransformation eventHandler = mEventHandler.get(displayId);
        if (eventHandler != null) {
            eventHandler.clearEvents(eventSource);
        }
    }

    boolean shouldProcessMultiDeviceEvent(InputEvent event, int policyFlags) {
        if (event instanceof MotionEvent motion) {
            if (!motion.isFromSource(SOURCE_CLASS_POINTER) || motion.getAction() == ACTION_SCROLL) {
                // Non-pointer events are focus-dispatched and don't require special logic.
                // Scroll events are stand-alone and therefore can be considered to not be part of
                // a stream.
                return true;
            }
            // Only allow 1 device to be sending motion events at a time
            // If the event is from an active device, let it through.
            // If the event is not from an active device, only let it through if it starts a new
            // gesture like ACTION_DOWN or ACTION_HOVER_ENTER
            final boolean eventIsFromCurrentDevice = mLastActiveDeviceMotionEvent != null
                    && mLastActiveDeviceMotionEvent.getDeviceId() == motion.getDeviceId();
            final int actionMasked = motion.getActionMasked();
            switch (actionMasked) {
                case MotionEvent.ACTION_DOWN,
                     MotionEvent.ACTION_HOVER_ENTER,
                     MotionEvent.ACTION_HOVER_MOVE -> {
                    if (mLastActiveDeviceMotionEvent != null
                            && mLastActiveDeviceMotionEvent.getDeviceId() != motion.getDeviceId()) {
                        // This is a new gesture from a new device. Cancel the existing state
                        // and let this through
                        MotionEvent canceled = cancelMotion(mLastActiveDeviceMotionEvent);
                        onInputEventInternal(canceled, policyFlags);
                    }
                    updateLastActiveDeviceMotionEvent(motion);
                    return true;
                }
                case MotionEvent.ACTION_MOVE,
                     MotionEvent.ACTION_POINTER_DOWN,
                     MotionEvent.ACTION_POINTER_UP -> {
                    if (eventIsFromCurrentDevice) {
                        updateLastActiveDeviceMotionEvent(motion);
                        return true;
                    } else {
                        return false;
                    }
                }
                case MotionEvent.ACTION_UP,
                     MotionEvent.ACTION_CANCEL,
                     MotionEvent.ACTION_HOVER_EXIT -> {
                    if (eventIsFromCurrentDevice) {
                        // This is the last event of the gesture from this device.
                        updateLastActiveDeviceMotionEvent(motion);
                        return true;
                    } else {
                        // Event is from another device
                        return false;
                    }
                }
                default -> {
                    if (mLastActiveDeviceMotionEvent != null
                            && event.getDeviceId() != mLastActiveDeviceMotionEvent.getDeviceId()) {
                        // This is an event from another device, ignore it.
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Updates the tracked motion event for the active input device.
     * If a new gesture starts (e.g., ACTION_DOWN), the active device is switched to the new one.
     * If an ongoing gesture continues (e.g., ACTION_MOVE), events from other devices are ignored
     * to ensure the stream remains consistent for a single device.
     */
    void updateLastActiveDeviceMotionEvent(InputEvent event) {
        if (event instanceof MotionEvent motion) {
            if (!motion.isFromSource(InputDevice.SOURCE_CLASS_POINTER)
                    || motion.getActionMasked() == MotionEvent.ACTION_SCROLL) {
                return;
            }

            final boolean eventIsFromCurrentDevice = mLastActiveDeviceMotionEvent != null
                    && mLastActiveDeviceMotionEvent.getDeviceId() == motion.getDeviceId();

            final int actionMasked = motion.getActionMasked();
            switch (actionMasked) {
                // Start of a gesture or hover. Always switch the active device.
                case MotionEvent.ACTION_DOWN, MotionEvent.ACTION_HOVER_ENTER,
                     MotionEvent.ACTION_HOVER_MOVE -> {
                    if (mLastActiveDeviceMotionEvent != null) {
                        mLastActiveDeviceMotionEvent.recycle();
                    }
                    mLastActiveDeviceMotionEvent = MotionEvent.obtain(motion);
                }
                // Gesture in progress. Only track events from the current device.
                case MotionEvent.ACTION_MOVE, MotionEvent.ACTION_POINTER_DOWN,
                     MotionEvent.ACTION_POINTER_UP -> {
                    if (eventIsFromCurrentDevice) {
                        if (mLastActiveDeviceMotionEvent != null) {
                            mLastActiveDeviceMotionEvent.recycle();
                        }
                        mLastActiveDeviceMotionEvent = MotionEvent.obtain(motion);
                    }
                }
                // Gesture finished. Clear state if it's from the active device.
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL,
                     MotionEvent.ACTION_HOVER_EXIT -> {
                    if (eventIsFromCurrentDevice) {
                        if (mLastActiveDeviceMotionEvent != null) {
                            mLastActiveDeviceMotionEvent.recycle();
                        }
                        mLastActiveDeviceMotionEvent = null;
                    }
                }
                default -> {
                    // Do nothing. State remains unchanged.
                }
            }
        }
    }

    private void processMotionEvent(EventStreamState state, MotionEvent event, int policyFlags) {
        if (!state.shouldProcessScroll() && event.getActionMasked() == ACTION_SCROLL) {
            super.onInputEvent(event, policyFlags);
            return;
        }

        if (!state.shouldProcessMotionEvent(event)) {
            // Fix: If the filter doesn't track this event (e.g. mid-gesture enable),
            // pass it down to prevent dropping ACTION_UP and freezing the screen.
            super.onInputEvent(event, policyFlags);
            return;
        }

        handleMotionEvent(event, policyFlags);
    }

    private void processKeyEvent(EventStreamState state, KeyEvent event, int policyFlags) {
        if (!state.shouldProcessKeyEvent(event)) {
            if (DEBUG) {
                Slog.d(TAG, "processKeyEvent: not processing: " + event);
            }
            super.onInputEvent(event, policyFlags);
            return;
        }
        if (DEBUG) {
            Slog.d(TAG, "processKeyEvent: " + event);
        }
        // Since the display id of KeyEvent always would be -1 and there is only one
        // KeyboardInterceptor for all display, pass KeyEvent to the mEventHandler of
        // DEFAULT_DISPLAY to handle.
        mEventHandler.get(Display.DEFAULT_DISPLAY).onKeyEvent(event, policyFlags);
    }

    private void handleMotionEvent(MotionEvent event, int policyFlags) {
        if (DEBUG) {
            Slog.i(TAG, "Handling motion event: " + event + ", policyFlags: " + policyFlags);
        }
        mPm.userActivity(event.getEventTime(), false);
        MotionEvent transformedEvent = MotionEvent.obtain(event);
        final int displayId = event.getDisplayId();
        EventStreamTransformation eventStreamTransformation = mEventHandler.get(
                isDisplayIdValid(displayId) ? displayId : Display.DEFAULT_DISPLAY);
        if (eventStreamTransformation != null) {
            eventStreamTransformation.onMotionEvent(transformedEvent, event, policyFlags);
        }
        transformedEvent.recycle();
    }

    private boolean isDisplayIdValid(int displayId) {
        return mEventHandler.get(displayId) != null;
    }

    @Override
    public void onMotionEvent(MotionEvent transformedEvent, MotionEvent rawEvent,
            int policyFlags) {
        if (!mInstalled) {
            Slog.w(TAG, "onMotionEvent called before input filter installed!");
            return;
        }
        sendInputEvent(transformedEvent, policyFlags);
    }

    @Override
    public void onKeyEvent(KeyEvent event, int policyFlags) {
        if (!mInstalled) {
            Slog.w(TAG, "onKeyEvent called before input filter installed!");
            return;
        }
        sendInputEvent(event, policyFlags);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // TODO Implement this to inject the accessibility event
        //      into the accessibility manager service similarly
        //      to how this is done for input events.
    }

    @Override
    public void setNext(EventStreamTransformation sink) {
        /* do nothing */
    }

    @Override
    public EventStreamTransformation getNext() {
        return null;
    }

    @Override
    public void clearEvents(int inputSource) {
        /* do nothing */
    }

    void setUserAndEnabledFeatures(int userId, int enabledFeatures) {
        if (DEBUG) {
            Slog.i(TAG, "setUserAndEnabledFeatures(userId = " + userId + ", enabledFeatures = 0x"
                    + Integer.toHexString(enabledFeatures) + ")");
        }
        if (mEnabledFeatures == enabledFeatures && mUserId == userId) {
            return;
        }
        if (mInstalled) {
            disableFeatures(/* featuresToBeEnabled= */ enabledFeatures);
        }
        mUserId = userId;
        mEnabledFeatures = enabledFeatures;
        if (mInstalled) {
            enableFeatures();
        }
    }

    void notifyAccessibilityEvent(AccessibilityEvent event) {
        for (int i = 0; i < mEventHandler.size(); i++) {
            final EventStreamTransformation eventHandler = mEventHandler.valueAt(i);
            if (eventHandler != null) {
                eventHandler.onAccessibilityEvent(event);
            }
        }
    }

    /**
     * @hide
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public void notifyMagnificationShortcutTriggered(int displayId) {
        if (mMagnificationGestureHandler.size() != 0) {
            final MagnificationGestureHandler handler = mMagnificationGestureHandler.get(displayId);
            if (handler != null) {
                handler.notifyShortcutTriggered();
            }
        }
    }

    private void enableFeatures() {
        if (DEBUG) Slog.i(TAG, "enableFeatures()");

        resetAllStreamState();

        final ArrayList<Display> displaysList = mAms.getValidDisplayList();

        for (int i = displaysList.size() - 1; i >= 0; i--) {
            enableFeaturesForDisplay(displaysList.get(i));
        }
        enableDisplayIndependentFeatures();

        registerPointerMotionFilter(/* enabled= */ isAnyFullScreenMagnificationEnabled());
    }

    private void enableFeaturesForDisplay(Display display) {
        if (DEBUG) {
            Slog.i(TAG, "enableFeaturesForDisplay() : display Id = " + display.getDisplayId());
        }

        final Context displayContext = mContext.createDisplayContext(display);
        final int displayId = display.getDisplayId();
        if (mAms.isDisplayProxyed(displayId)) {
            return;
        }
        if (!mServiceDetectsGestures.contains(displayId)) {
            mServiceDetectsGestures.put(displayId, false);
        }
        if ((mEnabledFeatures & FLAG_FEATURE_AUTOCLICK) != 0) {
            if (mAutoclickController == null) {
                mAutoclickController = new AutoclickController(
                        mContext, mUserId, mAms.getTraceManager());
            }
            addFirstEventHandler(displayId, mAutoclickController);
        }

        if ((mEnabledFeatures & FLAG_FEATURE_TOUCH_EXPLORATION) != 0) {
            TouchExplorer explorer = new TouchExplorer(displayContext, mAms);
            if ((mEnabledFeatures & FLAG_SERVICE_HANDLES_DOUBLE_TAP) != 0) {
                explorer.setServiceHandlesDoubleTap(true);
            }
            if ((mEnabledFeatures & FLAG_REQUEST_MULTI_FINGER_GESTURES) != 0) {
                explorer.setMultiFingerGesturesEnabled(true);
            }
            if ((mEnabledFeatures & FLAG_REQUEST_2_FINGER_PASSTHROUGH) != 0) {
                explorer.setTwoFingerPassthroughEnabled(true);
            }
            if ((mEnabledFeatures & FLAG_SEND_MOTION_EVENTS) != 0) {
                explorer.setSendMotionEventsEnabled(true);
            }
            explorer.setServiceDetectsGestures(mServiceDetectsGestures.get(displayId));
            addFirstEventHandler(displayId, explorer);
            mTouchExplorer.put(displayId, explorer);
        }

        if ((mEnabledFeatures & FLAG_FEATURE_INTERCEPT_GENERIC_MOTION_EVENTS) != 0) {
            addFirstEventHandler(
                    displayId,
                    new BaseEventStreamTransformation() {
                        @Override
                        public void onMotionEvent(
                                MotionEvent event, MotionEvent rawEvent, int policyFlags) {
                            boolean passAlongEvent = true;
                            if (anyServiceWantsGenericMotionEvent(event)) {
                                // Some service wants this event, so try to deliver it to at least
                                // one service.
                                if (mAms.sendMotionEventToListeningServices(event)) {
                                    // A service accepted this event, so prevent it from passing
                                    // down the stream by default.
                                    passAlongEvent = false;
                                }
                                // However, if a service is observing these events instead of
                                // consuming them then ensure
                                // it is always passed along to the next stage of the event stream.
                                if (anyServiceWantsToObserveMotionEvent(event)) {
                                    passAlongEvent = true;
                                }
                            }
                            if (passAlongEvent) {
                                super.onMotionEvent(event, rawEvent, policyFlags);
                            }
                        }
                    });
        }

        if (isAnyMagnificationEnabled(mEnabledFeatures)) {
            final MagnificationGestureHandler magnificationGestureHandler =
                    createMagnificationGestureHandler(displayId, displayContext);
            addFirstEventHandler(displayId, magnificationGestureHandler);
            mMagnificationGestureHandler.put(displayId, magnificationGestureHandler);
        }

        if ((mEnabledFeatures & FLAG_FEATURE_INJECT_MOTION_EVENTS) != 0) {
            MotionEventInjector injector =
                    new MotionEventInjector(mContext.getMainLooper(), mAms.getTraceManager());
            addFirstEventHandler(displayId, injector);
            mMotionEventInjectors.put(displayId, injector);
        }
    }

    private void enableDisplayIndependentFeatures() {
        if ((mEnabledFeatures & FLAG_FEATURE_INJECT_MOTION_EVENTS) != 0) {
            mAms.setMotionEventInjectors(mMotionEventInjectors);
        }

        if ((mEnabledFeatures & FLAG_FEATURE_FILTER_KEY_EVENTS) != 0) {
            // mKeyboardInterceptor does not forward KeyEvents to other EventStreamTransformations,
            // so it must be the last EventStreamTransformation for key events in the list.
            // The KeyboardInterceptor constructor that takes a Handler is for testing.
            if (mHandler.getLooper() == Looper.getMainLooper()) {
                mKeyboardInterceptor = new KeyboardInterceptor(mAms,
                        LocalServices.getService(InputManagerInternal.class));
            } else {
                mKeyboardInterceptor = new KeyboardInterceptor(mAms,
                        LocalServices.getService(InputManagerInternal.class), mHandler);
            }
            // Since the display id of KeyEvent always would be -1 and it would be dispatched to
            // the display with input focus directly, we only need one KeyboardInterceptor for
            // default display.
            addFirstEventHandler(Display.DEFAULT_DISPLAY, mKeyboardInterceptor);
        }

        if ((mEnabledFeatures & FLAG_FEATURE_MOUSE_KEYS) != 0) {
            if (mMouseKeysInterceptor == null) {
                TimeSource systemClockTimeSource = new TimeSource() {
                    @Override
                    public long uptimeMillis() {
                        return SystemClock.uptimeMillis();
                    }
                };
                mMouseKeysInterceptor = new MouseKeysInterceptor(mAms,
                        mContext,
                        Looper.myLooper(),
                        Display.DEFAULT_DISPLAY,
                        systemClockTimeSource,
                        mUserId);
                addFirstEventHandler(Display.DEFAULT_DISPLAY, mMouseKeysInterceptor);
            }
        }

        if (isAnyMagnificationEnabled(mEnabledFeatures)) {
            mMagnificationKeyHandler = new MagnificationKeyHandler(
                    mAms.getMagnificationController(), mAms);
            addFirstEventHandler(Display.DEFAULT_DISPLAY, mMagnificationKeyHandler);
        }
    }

    /**
     * Checks if any magnification feature is enabled.
     *
     * @param enabledFeatures An integer bitmask representing all enabled accessibility features.
     * @return {@code true} if at least one magnification feature flag is set,
     *         {@code false} otherwise.
     */
    private boolean isAnyMagnificationEnabled(int enabledFeatures) {
        return (enabledFeatures & FLAG_FEATURE_CONTROL_SCREEN_MAGNIFIER) != 0
                || ((enabledFeatures & FLAG_FEATURE_MAGNIFICATION_SINGLE_FINGER_TRIPLE_TAP) != 0)
                || ((enabledFeatures & FLAG_FEATURE_TRIGGERED_SCREEN_MAGNIFIER) != 0);
    }

    private boolean isAnyFullScreenMagnificationEnabled() {
        if (!isAnyMagnificationEnabled(mEnabledFeatures)) {
            return false;
        }
        for (final Display display : mAms.getValidDisplayList()) {
            final int mode = mAms.getMagnificationMode(display.getDisplayId());
            if (mode != Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE_WINDOW) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds an event handler to the event handler chain for giving display. The handler is added at
     * the beginning of the chain.
     *
     * @param displayId The logical display id.
     * @param handler The handler to be added to the event handlers list.
     */
    private void addFirstEventHandler(int displayId, EventStreamTransformation handler) {
        EventStreamTransformation eventHandler = mEventHandler.get(displayId);
        if (eventHandler != null) {
            handler.setNext(eventHandler);
        } else {
            handler.setNext(this);
        }
        eventHandler = handler;
        mEventHandler.put(displayId, eventHandler);
    }

    /**
     * Disables accessibility features, potentially with different handling based on
     * features that will be enabled subsequently.
     *
     * @param featuresToBeEnabled Features that are expected to be enabled *after* the disabling
     *                            operation has finished.
     *                            See {@link #disableFeaturesForDisplay(int, int)}
     */
    private void disableFeatures(int featuresToBeEnabled) {
        final ArrayList<Display> displaysList = mAms.getValidDisplayList();

        for (int i = displaysList.size() - 1; i >= 0; i--) {
            disableFeaturesForDisplay(displaysList.get(i).getDisplayId(), featuresToBeEnabled);
        }
        mAms.setMotionEventInjectors(null);
        disableDisplayIndependentFeatures();

        resetAllStreamState();

        registerPointerMotionFilter(/* enabled= */ false);
    }

    /**
     * Disables accessibility features specifically for a given display.
     *
     * <p>
     * The {@code featuresToBeEnabled} parameter influences the disabling process.
     * It provides context about which features are expected to be active immediately
     * after this disabling operation completes. This allows for conditional logic during
     * disablement; for example, certain states might not be fully reset if the corresponding
     * feature is intended to remain active or be re-enabled shortly. An example is
     * not resetting magnification if the magnification feature flag is present in
     * {@code featuresToBeEnabled}, even when its gesture handler is being destroyed.
     * </p>
     *
     * @param displayId The ID of the display for which features should be disabled.
     * @param featuresToBeEnabled Features that are expected to be enabled *after* the disabling
     *                            operation has finished.
     */
    private void disableFeaturesForDisplay(int displayId, int featuresToBeEnabled) {
        if (DEBUG) {
            Slog.i(TAG, "disableFeaturesForDisplay() : display Id = " + displayId);
        }

        final MotionEventInjector injector = mMotionEventInjectors.get(displayId);
        if (injector != null) {
            injector.onDestroy();
            mMotionEventInjectors.remove(displayId);
        }

        final TouchExplorer explorer = mTouchExplorer.get(displayId);
        if (explorer != null) {
            explorer.onDestroy();
            mTouchExplorer.remove(displayId);
        }

        final MagnificationGestureHandler handler = mMagnificationGestureHandler.get(displayId);
        if (handler != null) {
            // With the given enabledFeatures parameter if the magnification feature is still
            // enabled, which means after the disabling there is a recreating coming, so the
            // magnification reset is not needed.
            handler.onDestroy(
                    /* resetMagnification= */ !isAnyMagnificationEnabled(featuresToBeEnabled));
            mMagnificationGestureHandler.remove(displayId);
        }

        final EventStreamTransformation eventStreamTransformation = mEventHandler.get(displayId);
        if (eventStreamTransformation != null) {
            mEventHandler.remove(displayId);
        }
    }

    void enableFeaturesForDisplayIfInstalled(Display display) {
        if (mInstalled) {
            resetStreamStateForDisplay(display.getDisplayId());
            enableFeaturesForDisplay(display);
        }
    }

    void disableFeaturesForDisplayIfInstalled(int displayId) {
        if (mInstalled) {
            disableFeaturesForDisplay(displayId, /* featuresToBeEnabled= */ FLAG_FEATURE_NONE);
            resetStreamStateForDisplay(displayId);
        }
    }

    private void disableDisplayIndependentFeatures() {
        if (mAutoclickController != null) {
            mAutoclickController.onDestroy();
            mAutoclickController = null;
        }

        if (mKeyboardInterceptor != null) {
            mKeyboardInterceptor.onDestroy();
            mKeyboardInterceptor = null;
        }

        if (mMouseKeysInterceptor != null) {
            mMouseKeysInterceptor.onDestroy();
            mMouseKeysInterceptor = null;
        }

        if (mMagnificationKeyHandler != null) {
            mMagnificationKeyHandler.onDestroy();
            mMagnificationKeyHandler = null;
        }
    }

    @VisibleForTesting
    @Nullable
    FullScreenMagnificationPointerMotionEventFilter
            getFullScreenMagnificationPointerMotionEventFilter() {
        return mFullScreenMagnificationPointerMotionEventFilter;
    }

    private void createFullScreenMagnificationPointerMotionEventFilter() {
        final FullScreenMagnificationController controller =
                mAms.getMagnificationController().getFullScreenMagnificationController();
        mFullScreenMagnificationPointerMotionEventFilter =
                new FullScreenMagnificationPointerMotionEventFilter(controller);
        @AccessibilityMagnificationCursorFollowingMode
        final int cursorFollowingMode = mAms.getMagnificationCursorFollowingMode();
        setCursorFollowingMode(cursorFollowingMode);
    }

    /**
     * Sets cursor following mode. No operation if the feature flag is
     * not enabled.
     *
     * @param cursorFollowingMode The cursor following mode
     */
    public void setCursorFollowingMode(
            @AccessibilityMagnificationCursorFollowingMode int cursorFollowingMode) {
        if (mFullScreenMagnificationPointerMotionEventFilter != null) {
            mFullScreenMagnificationPointerMotionEventFilter.setMode(cursorFollowingMode);
        }
    }

    @VisibleForTesting
    void registerPointerMotionFilter(boolean enabled) {
        if (enabled == (mFullScreenMagnificationPointerMotionEventFilter != null)) {
            return;
        }

        InputManagerInternal inputManager = LocalServices.getService(InputManagerInternal.class);
        if (inputManager == null) {
            return;
        }

        if (enabled) {
            createFullScreenMagnificationPointerMotionEventFilter();
        } else {
            mFullScreenMagnificationPointerMotionEventFilter = null;
        }

        // Invoke the input manager without holding the accessibility lock
        // (`AccessibilityManagerService#mLock`) to avoid a potential deadlock since the input stack
        // also invokes `AccessibilityPointerMotionFilter` APIs while holding the input lock.
        mHandler.post(() -> inputManager.registerAccessibilityPointerMotionFilter(
                mFullScreenMagnificationPointerMotionEventFilter));
    }

    private MagnificationGestureHandler createMagnificationGestureHandler(
            int displayId, Context displayContext) {
        final boolean detectControlGestures = (mEnabledFeatures
                & FLAG_FEATURE_MAGNIFICATION_SINGLE_FINGER_TRIPLE_TAP) != 0;
        final boolean triggerable = (mEnabledFeatures
                & FLAG_FEATURE_TRIGGERED_SCREEN_MAGNIFIER) != 0;
        MagnificationGestureHandler magnificationGestureHandler;
        if (mAms.getMagnificationMode(displayId)
                == Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE_WINDOW) {
            final Context uiContext = displayContext.createWindowContext(
                    TYPE_ACCESSIBILITY_MAGNIFICATION_OVERLAY, null /* options */);
            magnificationGestureHandler = new WindowMagnificationGestureHandler(uiContext,
                    mAms.getMagnificationConnectionManager(), mAms.getTraceManager(),
                    mAms.getMagnificationController(),
                    detectControlGestures,
                    triggerable, displayId);
        } else {
            final Context uiContext = displayContext.createWindowContext(
                    TYPE_MAGNIFICATION_OVERLAY, null /* options */);
            FullScreenMagnificationVibrationHelper fullScreenMagnificationVibrationHelper =
                    new FullScreenMagnificationVibrationHelper(uiContext);
            FullScreenMagnificationController controller =
                    mAms.getMagnificationController().getFullScreenMagnificationController();
            magnificationGestureHandler =
                    new FullScreenMagnificationGestureHandler(
                            uiContext,
                            controller,
                            mAms.getTraceManager(),
                            mAms.getMagnificationController(),
                            detectControlGestures,
                            triggerable,
                            new WindowMagnificationPromptController(displayContext, mUserId),
                            displayId,
                            fullScreenMagnificationVibrationHelper);
        }
        return magnificationGestureHandler;
    }

    void resetAllStreamState() {
        final ArrayList<Display> displaysList = mAms.getValidDisplayList();

        for (int i = displaysList.size() - 1; i >= 0; i--) {
            resetStreamStateForDisplay(displaysList.get(i).getDisplayId());
        }

        if (mKeyboardStreamState != null) {
            mKeyboardStreamState.reset();
        }
    }

    private void sendTouchCancelEventIfNeeded(int displayId, EventStreamState state) {
        if (!(state instanceof TouchScreenEventStreamState tsState)
                || !tsState.mTouchSequenceStarted
                || !Flags.sendA11yActionCancelOnReset()) {
            return;
        }
        if (!mInstalled) {
            Slog.w(TAG,
                    "sendTouchCancelEventIfNeeded: Filter not installed, skipping cancel event.");
            return;
        }
        if (mLastActiveDeviceMotionEvent != null
                && mLastActiveDeviceMotionEvent.getDisplayId() == displayId
                && mLastActiveDeviceMotionEvent.isFromSource(
                        InputDevice.SOURCE_TOUCHSCREEN)) {
            final MotionEvent cancelEvent = cancelMotion(mLastActiveDeviceMotionEvent);
            super.onInputEvent(cancelEvent, WindowManagerPolicy.FLAG_PASS_TO_USER);
            cancelEvent.recycle();
            if (Flags.sendA11yActionCancelOnReset()) {
                mLastActiveDeviceMotionEvent.recycle();
                mLastActiveDeviceMotionEvent = null;
            }
        }
    }
    void resetStreamStateForDisplay(int displayId) {
        final EventStreamState touchScreenStreamState = mTouchScreenStreamStates.get(displayId);
        if (touchScreenStreamState != null) {
            // Send Cancel if needed to prevent inconsistency
            sendTouchCancelEventIfNeeded(displayId, touchScreenStreamState);
            touchScreenStreamState.reset();
            mTouchScreenStreamStates.remove(displayId);
        }

        final EventStreamState mouseStreamState = mMouseStreamStates.get(displayId);
        if (mouseStreamState != null) {
            mouseStreamState.reset();
            mMouseStreamStates.remove(displayId);
        }
    }

    @Override
    public void onDestroy() {
        /* ignore */
    }

    /**
     * Called to refresh the magnification mode on the given display.
     * It's responsible for changing {@link MagnificationGestureHandler} based on the current mode.
     *
     * @param display The logical display
     */
    @MainThread
    public void refreshMagnificationMode(Display display) {
        final int displayId = display.getDisplayId();
        final MagnificationGestureHandler magnificationGestureHandler =
                mMagnificationGestureHandler.get(displayId);
        if (magnificationGestureHandler == null) {
            return;
        }
        if (magnificationGestureHandler.getMode() == mAms.getMagnificationMode(displayId)) {
            return;
        }
        magnificationGestureHandler.onDestroy();
        final MagnificationGestureHandler currentMagnificationGestureHandler =
                createMagnificationGestureHandler(displayId,
                        mContext.createDisplayContext(display));
        switchEventStreamTransformation(displayId, magnificationGestureHandler,
                currentMagnificationGestureHandler);
        mMagnificationGestureHandler.put(displayId, currentMagnificationGestureHandler);

        registerPointerMotionFilter(/* enabled= */ isAnyFullScreenMagnificationEnabled());
    }

    @MainThread
    private void switchEventStreamTransformation(int displayId,
            EventStreamTransformation oldStreamTransformation,
            EventStreamTransformation currentStreamTransformation) {
        EventStreamTransformation eventStreamTransformation = mEventHandler.get(displayId);
        if (eventStreamTransformation == null) {
            return;
        }
        if (eventStreamTransformation == oldStreamTransformation) {
            currentStreamTransformation.setNext(oldStreamTransformation.getNext());
            mEventHandler.put(displayId, currentStreamTransformation);
        } else {
            while (eventStreamTransformation != null) {
                if (eventStreamTransformation.getNext() == oldStreamTransformation) {
                    eventStreamTransformation.setNext(currentStreamTransformation);
                    currentStreamTransformation.setNext(oldStreamTransformation.getNext());
                    return;
                } else {
                    eventStreamTransformation = eventStreamTransformation.getNext();
                }
            }
        }
    }

    /**
     * Keeps state of event streams observed for an input device with a certain source.
     * Provides information about whether motion and key events should be processed by accessibility
     * #EventStreamTransformations. Base implementation describes behaviour for event sources that
     * whose events should not be handled by a11y event stream transformations.
     */
    private static class EventStreamState {
        private int mSource;

        EventStreamState() {
            mSource = -1;
        }

        /**
         * Updates the input source of the device associated with the state. If the source changes,
         * resets internal state.
         *
         * @param source Updated input source.
         * @return Whether the input source has changed.
         */
        public boolean updateInputSource(int source) {
            if (mSource == source) {
                return false;
            }
            // Reset clears internal state, so make sure it's called before |mSource| is updated.
            reset();
            mSource = source;
            return true;
        }

        /**
         * @return Whether input source is valid.
         */
        public boolean inputSourceValid() {
            return mSource >= 0;
        }

        /**
         * Resets the event stream state.
         */
        public void reset() {
            mSource = -1;
        }

        /**
         * @return Whether scroll events for device should be handled by event transformations.
         */
        public boolean shouldProcessScroll() {
            return false;
        }

        /**
         * @param event An observed motion event.
         * @return Whether the event should be handled by event transformations.
         */
        public boolean shouldProcessMotionEvent(MotionEvent event) {
            return false;
        }

        /**
         * @param event An observed key event.
         * @return Whether the event should be handled by event transformations.
         */
        public boolean shouldProcessKeyEvent(KeyEvent event) {
            return false;
        }
    }

    /**
     * Keeps state of stream of events from a mouse device.
     */
    private static class MouseEventStreamState extends EventStreamState {
        private boolean mMotionSequenceStarted;

        public MouseEventStreamState() {
            reset();
        }

        @Override
        final public void reset() {
            super.reset();
            mMotionSequenceStarted = false;
        }

        @Override
        final public boolean shouldProcessScroll() {
            return true;
        }

        @Override
        final public boolean shouldProcessMotionEvent(MotionEvent event) {
            if (mMotionSequenceStarted) {
                return true;
            }
            // Wait for down or move event to start processing mouse events.
            int action = event.getActionMasked();
            mMotionSequenceStarted =
                    action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_HOVER_MOVE;
            return mMotionSequenceStarted;
        }
    }

    /**
     * Keeps state of stream of events from a touch screen device.
     */
    private static class TouchScreenEventStreamState extends EventStreamState {
        private boolean mTouchSequenceStarted;
        private boolean mHoverSequenceStarted;

        public TouchScreenEventStreamState() {
            reset();
        }

        @Override
        final public void reset() {
            super.reset();
            resetSequenceState();
        }

        private void resetSequenceState() {
            mTouchSequenceStarted = false;
            mHoverSequenceStarted = false;
        }

        /**
         * Determines if a motion event should be processed by accessibility transformations.
         *
         * <p>This method manages two independent states: one for touch gestures and one for hover
         * gestures. A touch gesture is processed from ACTION_DOWN until ACTION_UP/ACTION_CANCEL.
         * A hover gesture is processed from ACTION_HOVER_ENTER until it's superseded by a touch
         * gesture or another event stream reset.
         */
        @Override
        final public boolean shouldProcessMotionEvent(MotionEvent event) {
            if (Flags.sendA11yActionCancelOnReset()) {
                // Allow the cancel event to pass if it is cancelling a sequence.
                if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    if (mTouchSequenceStarted || mHoverSequenceStarted) {
                        resetSequenceState();
                        return true;
                    }
                    return false;
                }
            }

            if (event.isTouchEvent()) {
                return shouldProcessTouchEvent(event);
            }

            if (event.isHoverEvent()) {
                return shouldProcessHoverEvent(event);
            }

            return false;
        }

        private boolean shouldProcessTouchEvent(MotionEvent event) {
            // Wait for a down touch event to start processing.
            if (mTouchSequenceStarted) {
                final int action = event.getActionMasked();
                if (Flags.sendA11yActionCancelOnReset()
                        && action == MotionEvent.ACTION_UP) {
                    resetSequenceState();
                }
                return true;
            }

            mTouchSequenceStarted = event.getActionMasked() == MotionEvent.ACTION_DOWN;
            return mTouchSequenceStarted;
        }

        private boolean shouldProcessHoverEvent(MotionEvent event) {
            // Wait for an enter hover event to start processing.
            if (mHoverSequenceStarted) {
                final int action = event.getActionMasked();
                // Reset on gesture completion only if the flag is enabled.
                if (Flags.sendA11yActionCancelOnReset()
                        && action == MotionEvent.ACTION_HOVER_EXIT) {
                    resetSequenceState();
                }
                return true;
            }
            mHoverSequenceStarted = event.getActionMasked() == MotionEvent.ACTION_HOVER_ENTER;
            return mHoverSequenceStarted;
        }
    }

    private class GenericMotionEventStreamState extends EventStreamState {
        @Override
        public boolean shouldProcessMotionEvent(MotionEvent event) {
            return anyServiceWantsGenericMotionEvent(event);
        }
        @Override
        public boolean shouldProcessScroll() {
            return true;
        }
    }

    private boolean anyServiceWantsGenericMotionEvent(MotionEvent event) {
        final boolean isTouchEvent = event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN);
        if (isTouchEvent && !canShareGenericTouchEvent()) {
            return false;
        }
        final int eventSourceWithoutClass = event.getSource() & ~InputDevice.SOURCE_CLASS_MASK;
        return (mCombinedGenericMotionEventSources & eventSourceWithoutClass) != 0;
    }

    private boolean anyServiceWantsToObserveMotionEvent(MotionEvent event) {
        final int eventSourceWithoutClass = event.getSource() & ~InputDevice.SOURCE_CLASS_MASK;
        return (mCombinedMotionEventObservedSources & eventSourceWithoutClass) != 0;
    }

    private boolean canShareGenericTouchEvent() {
        if ((mCombinedMotionEventObservedSources & InputDevice.SOURCE_TOUCHSCREEN) != 0) {
            // Share touch events if a MotionEvent-observing service wants them.
            return true;
        }
        if ((mEnabledFeatures & FLAG_FEATURE_TOUCH_EXPLORATION) == 0) {
            // Share touch events if touch exploration is not enabled.
            return true;
        }
        return false;
    }

    public void setCombinedGenericMotionEventSources(int sources) {
        mCombinedGenericMotionEventSources = sources;
    }

    public void setCombinedMotionEventObservedSources(int sources) {
        mCombinedMotionEventObservedSources = sources;
    }

    /**
     * Keeps state of streams of events from all keyboard devices.
     */
    private static class KeyboardEventStreamState extends EventStreamState {
        private SparseBooleanArray mEventSequenceStartedMap = new SparseBooleanArray();

        public KeyboardEventStreamState() {
            reset();
        }

        @Override
        final public void reset() {
            super.reset();
            mEventSequenceStartedMap.clear();
        }

        /*
         * Key events from different devices may be interleaved. For example, the volume up and
         * down keys can come from different input sources.
         */
        @Override
        public boolean updateInputSource(int deviceId) {
            return false;
        }

        // We manage all input source simultaneously; there is no concept of validity.
        @Override
        public boolean inputSourceValid() {
            return true;
        }

        @Override
        final public boolean shouldProcessKeyEvent(KeyEvent event) {
            // For each keyboard device, wait for a down event from a device to start processing
            int deviceId = event.getDeviceId();
            if (mEventSequenceStartedMap.get(deviceId, false)) {
                return true;
            }
            boolean shouldProcess = event.getAction() == KeyEvent.ACTION_DOWN;
            mEventSequenceStartedMap.put(deviceId, shouldProcess);
            return shouldProcess;
        }
    }

    public void setGestureDetectionPassthroughRegion(int displayId, Region region) {
        if (region != null && mTouchExplorer.contains(displayId)) {
            mTouchExplorer.get(displayId).setGestureDetectionPassthroughRegion(region);
        }
    }

    public void setTouchExplorationPassthroughRegion(int displayId, Region region) {
        if (region != null && mTouchExplorer.contains(displayId)) {
            mTouchExplorer.get(displayId).setTouchExplorationPassthroughRegion(region);
        }
    }

    public void setServiceDetectsGesturesEnabled(int displayId, boolean mode) {
        if (mTouchExplorer.contains(displayId)) {
            mTouchExplorer.get(displayId).setServiceDetectsGestures(mode);
        }
        mServiceDetectsGestures.put(displayId, mode);
    }

    public void resetServiceDetectsGestures() {
        mServiceDetectsGestures.clear();
    }

    public void requestTouchExploration(int displayId) {
        if (mTouchExplorer.contains(displayId)) {
            mTouchExplorer.get(displayId).requestTouchExploration();
        }
    }

    public void requestDragging(int displayId, int pointerId) {
        if (mTouchExplorer.contains(displayId)) {
            mTouchExplorer.get(displayId).requestDragging(pointerId);
        }
    }

    public void requestDelegating(int displayId) {
        if (mTouchExplorer.contains(displayId)) {
            mTouchExplorer.get(displayId).requestDelegating();
        }
    }

    public void onDoubleTap(int displayId) {
        if (mTouchExplorer.contains(displayId)) {
            mTouchExplorer.get(displayId).onDoubleTap();
        }
    }

    public void onDoubleTapAndHold(int displayId) {
        if (mTouchExplorer.contains(displayId)) {
            mTouchExplorer.get(displayId).onDoubleTapAndHold();
        }
    }

    /**
     * Dumps all {@link AccessibilityInputFilter}s here.
     */
    public void dump(FileDescriptor fd, final PrintWriter pw, String[] args) {
        if (mEventHandler == null) {
            return;
        }
        pw.append("A11yInputFilter Info : ");
        pw.println();

        final ArrayList<Display> displaysList = mAms.getValidDisplayList();
        for (int i = 0; i < displaysList.size(); i++) {
            final int displayId = displaysList.get(i).getDisplayId();
            EventStreamTransformation next = mEventHandler.get(displayId);
            if (next != null) {
                pw.append("Enabled features of Display [");
                pw.append(Integer.toString(displayId));
                pw.append("] = ");

                final StringJoiner joiner = new StringJoiner(",", "[", "]");

                while (next != null) {
                    if (next instanceof MagnificationGestureHandler) {
                        joiner.add("MagnificationGesture");
                    } else if (next instanceof KeyboardInterceptor) {
                        joiner.add("KeyboardInterceptor");
                    } else if (next instanceof TouchExplorer) {
                        joiner.add("TouchExplorer");
                    } else if (next instanceof AutoclickController) {
                        joiner.add("AutoclickController");
                    } else if (next instanceof MotionEventInjector) {
                        joiner.add("MotionEventInjector");
                    } else if (next instanceof MagnificationKeyHandler) {
                        joiner.add("MagnificationKeyHandler");
                    }
                    next = next.getNext();
                }
                pw.append(joiner.toString());
            }
            pw.println();
        }
    }
}

