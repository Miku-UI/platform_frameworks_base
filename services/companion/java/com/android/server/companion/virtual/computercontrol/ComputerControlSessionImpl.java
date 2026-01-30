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

package com.android.server.companion.virtual.computercontrol;

import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_ACTIVITY;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_BLOCKED_ACTIVITY;

import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.app.ActivityOptions;
import android.companion.virtual.ActivityPolicyExemption;
import android.companion.virtual.IVirtualDevice;
import android.companion.virtual.IVirtualDeviceActivityListener;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.computercontrol.ComputerControlSession;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.companion.virtual.computercontrol.IComputerControlSession;
import android.companion.virtual.computercontrol.IComputerControlStabilityListener;
import android.companion.virtual.computercontrol.IInteractiveMirrorDisplay;
import android.companion.virtualdevice.flags.Flags;
import android.content.AttributionSource;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerGlobal;
import android.hardware.display.IVirtualDisplayCallback;
import android.hardware.display.VirtualDisplayConfig;
import android.hardware.input.IVirtualInputDevice;
import android.hardware.input.VirtualDpad;
import android.hardware.input.VirtualDpadConfig;
import android.hardware.input.VirtualKeyEvent;
import android.hardware.input.VirtualKeyboardConfig;
import android.hardware.input.VirtualTouchEvent;
import android.hardware.input.VirtualTouchscreenConfig;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Slog;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.inputmethod.IRemoteComputerControlInputConnection;
import com.android.internal.inputmethod.InputConnectionCommandHeader;
import com.android.server.LocalServices;
import com.android.server.inputmethod.InputMethodManagerInternal;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wm.ActivityTaskManagerInternal;
import com.android.server.wm.WindowManagerInternal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A computer control session that encapsulates a {@link IVirtualDevice}. The device is created and
 * managed by the system, but it is still owned by the caller.
 */
final class ComputerControlSessionImpl extends IComputerControlSession.Stub
        implements IBinder.DeathRecipient {

    private static final String TAG = "ComputerControlSession";

    private static final String CUSTOM_BLOCKED_APP_PACKAGE = "com.android.virtualdevicemanager";

    private static final ComponentName CUSTOM_BLOCKED_APP_ACTIVITY = new ComponentName(
            CUSTOM_BLOCKED_APP_PACKAGE,
            CUSTOM_BLOCKED_APP_PACKAGE + ".NotifyComputerControlBlockedActivity");

    // Input device names are limited to 80 bytes, so keep the prefix shorter than that.
    private static final int MAX_INPUT_DEVICE_NAME_PREFIX_LENGTH = 70;

    // Throttle swipe events to avoid misinterpreting them as a fling. Each swipe will
    // consist of a DOWN event, 10 MOVE events spread over 500ms, and an UP event.
    @VisibleForTesting
    static final int SWIPE_STEPS = 10;
    // Delay between consecutive touch events sent during a swipe or a long press gesture.
    @VisibleForTesting
    static final long TOUCH_EVENT_DELAY_MS = 50L;
    // Multiplier for the long press timeout to ensure it's registered as a long press,
    // as some applications might have slightly different thresholds.
    @VisibleForTesting
    static final float LONG_PRESS_TIMEOUT_MULTIPLIER = 1.5f;
    @VisibleForTesting
    static final long KEY_EVENT_DELAY_MS = 10L;

    // Vendor and Product IDs for Computer Control virtual input devices.
    // These values are likely unique within the VIRTUAL bus type, but they are not
    // guaranteed to be globally unique forever.
    // TODO: b/443001754 - Remove setVendorId and setProductId in all input devices below,
    //   in favor of reporting dedicated Computer Control metrics.
    private static final int VENDOR_ID = 0x0000;
    @VisibleForTesting
    static final int PRODUCT_ID_DPAD = 0xCC01;
    @VisibleForTesting
    static final int PRODUCT_ID_KEYBOARD = 0xCC02;
    @VisibleForTesting
    static final int PRODUCT_ID_TOUCHSCREEN = 0xCC03;

    private final IBinder mAppToken;
    private final ComputerControlSessionParams mParams;
    private final String mOwnerPackageName;
    private final OnClosedListener mOnClosedListener;
    private final IVirtualDevice mVirtualDevice;
    private final int mVirtualDisplayId;
    private final int mMainDisplayId;
    private final IVirtualDisplayCallback mVirtualDisplayToken;
    private final IVirtualInputDevice mVirtualTouchscreen;
    private final IVirtualInputDevice mVirtualDpad;
    private final IVirtualInputDevice mVirtualKeyboard;
    private final AtomicInteger mMirrorDisplayCounter = new AtomicInteger(0);
    private final ScheduledExecutorService mScheduler =
            Executors.newSingleThreadScheduledExecutor();
    private final Object mStabilityCalculatorLock = new Object();

    private final Injector mInjector;

    private int mDisplayWidth;
    private int mDisplayHeight;
    private ScheduledFuture<?> mSwipeFuture;
    private ScheduledFuture<?> mInsertTextFuture;

    @GuardedBy("mStabilityCalculatorLock")
    private StabilityCalculator mStabilityCalculator;

    ComputerControlSessionImpl(IBinder appToken, ComputerControlSessionParams params,
            AttributionSource attributionSource,
            ComputerControlSessionProcessor.VirtualDeviceFactory virtualDeviceFactory,
            OnClosedListener onClosedListener, Injector injector) {
        mAppToken = appToken;
        mParams = params;
        mOwnerPackageName = attributionSource.getPackageName();
        mOnClosedListener = onClosedListener;
        mInjector = injector;
        // TODO(b/440005498): Consider using the display from the app's context instead.
        mMainDisplayId = injector.getMainDisplayIdForUser(
                UserHandle.getUserId(attributionSource.getUid()));

        final VirtualDeviceParams virtualDeviceParams = new VirtualDeviceParams.Builder()
                .setName(mParams.getName())
                .setDevicePolicy(POLICY_TYPE_BLOCKED_ACTIVITY, DEVICE_POLICY_CUSTOM)
                .build();

        int displayFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_STEAL_TOP_FOCUS_DISABLED;
        if (mParams.isDisplayAlwaysUnlocked()) {
            displayFlags |= DisplayManager.VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED;
        }

        // This is used as a death detection token to release the display upon app death. We're in
        // the system process, so this won't happen, but this is OK because we already do death
        // detection in the virtual device based on the app token and closing it will also release
        // the display.
        // The same applies to the input devices. We can't reuse the app token there because it's
        // used as a map key for the virtual input devices.
        mVirtualDisplayToken = new DisplayManagerGlobal.VirtualDisplayCallback(null, null);

        // If the client didn't provide a surface, use the default display dimensions and enable
        // the screenshot API.
        // TODO(b/439774796): Do not allow client-provided surface and dimensions.
        final VirtualDisplayConfig virtualDisplayConfig;
        if (params.getDisplaySurface() == null) {
            displayFlags |= DisplayManager.VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED;
            final DisplayInfo defaultDisplayInfo = mInjector.getDisplayInfo(mMainDisplayId);
            mDisplayWidth = defaultDisplayInfo.logicalWidth;
            mDisplayHeight = defaultDisplayInfo.logicalHeight;
            virtualDisplayConfig = new VirtualDisplayConfig.Builder(
                    mParams.getName() + "-display", mDisplayWidth, mDisplayHeight,
                    defaultDisplayInfo.logicalDensityDpi)
                    .setFlags(displayFlags)
                    .build();
        } else {
            mDisplayWidth = mParams.getDisplayWidthPx();
            mDisplayHeight = mParams.getDisplayHeightPx();
            virtualDisplayConfig = new VirtualDisplayConfig.Builder(
                    mParams.getName() + "-display", mDisplayWidth, mDisplayHeight,
                    mParams.getDisplayDpi())
                    .setSurface(mParams.getDisplaySurface())
                    .setFlags(displayFlags)
                    .build();
        }

        try {
            mVirtualDevice = virtualDeviceFactory.createVirtualDevice(mAppToken, attributionSource,
                    virtualDeviceParams, new ComputerControlActivityListener());

            applyActivityPolicy();

            // Create the display with a clean identity so it can be trusted.
            mVirtualDisplayId = Binder.withCleanCallingIdentity(() -> {
                int displayId = mVirtualDevice.createVirtualDisplay(virtualDisplayConfig,
                        mVirtualDisplayToken);
                mInjector.disableAnimationsForDisplay(displayId);
                return displayId;
            });

            mVirtualDevice.setDisplayImePolicy(
                    mVirtualDisplayId, WindowManager.DISPLAY_IME_POLICY_HIDE);

            final String inputDeviceNamePrefix =
                    createInputDeviceNamePrefix(attributionSource.getPackageName());

            final String dpadName = inputDeviceNamePrefix + "-dpad";
            final VirtualDpadConfig virtualDpadConfig =
                    new VirtualDpadConfig.Builder()
                            .setAssociatedDisplayId(mVirtualDisplayId)
                            .setInputDeviceName(dpadName)
                            .setVendorId(VENDOR_ID)
                            .setProductId(PRODUCT_ID_DPAD)
                            .build();
            mVirtualDpad = mVirtualDevice.createVirtualDpad(
                    virtualDpadConfig, new Binder(dpadName));

            final String keyboardName = inputDeviceNamePrefix + "-kbrd";
            final VirtualKeyboardConfig virtualKeyboardConfig =
                    new VirtualKeyboardConfig.Builder()
                            .setAssociatedDisplayId(mVirtualDisplayId)
                            .setInputDeviceName(keyboardName)
                            .setVendorId(VENDOR_ID)
                            .setProductId(PRODUCT_ID_KEYBOARD)
                            .build();
            mVirtualKeyboard = mVirtualDevice.createVirtualKeyboard(
                    virtualKeyboardConfig, new Binder(keyboardName));

            final String touchscreenName = inputDeviceNamePrefix + "-tscr";
            final VirtualTouchscreenConfig virtualTouchscreenConfig =
                    new VirtualTouchscreenConfig.Builder(mDisplayWidth, mDisplayHeight)
                            .setAssociatedDisplayId(mVirtualDisplayId)
                            .setInputDeviceName(touchscreenName)
                            .setVendorId(VENDOR_ID)
                            .setProductId(PRODUCT_ID_TOUCHSCREEN)
                            .build();
            mVirtualTouchscreen = mVirtualDevice.createVirtualTouchscreen(
                    virtualTouchscreenConfig, new Binder(touchscreenName));

            mAppToken.linkToDeath(this, 0);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * This assumes that {@link ComputerControlSessionParams#getTargetPackageNames()} never contains
     * any packageNames that the session owner should never be able to launch. This is validated in
     * {@link ComputerControlSessionProcessor} prior to creating the session.
     */
    private void applyActivityPolicy() throws RemoteException {
        List<String> exemptedPackageNames = new ArrayList<>();
        if (Flags.computerControlActivityPolicyStrict()) {
            mVirtualDevice.setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM);

            exemptedPackageNames.addAll(mParams.getTargetPackageNames());
            exemptedPackageNames.add(CUSTOM_BLOCKED_APP_PACKAGE);
        } else {
            // TODO(b/439774796): Remove once v0 API is removed and the flag is rolled out.
            // This legacy policy allows all apps other than PermissionController to be automated.
            String permissionControllerPackage = mInjector.getPermissionControllerPackageName();
            exemptedPackageNames.add(permissionControllerPackage);
        }
        for (int i = 0; i < exemptedPackageNames.size(); i++) {
            String exemptedPackageName = exemptedPackageNames.get(i);
            mVirtualDevice.addActivityPolicyExemption(
                    new ActivityPolicyExemption.Builder()
                            .setPackageName(exemptedPackageName)
                            .build());
        }
    }

    @Override
    public int getVirtualDisplayId() {
        return mVirtualDisplayId;
    }

    int getDeviceId() {
        try {
            return mVirtualDevice.getDeviceId();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    IVirtualDisplayCallback getVirtualDisplayToken() {
        return mVirtualDisplayToken;
    }

    String getName() {
        return mParams.getName();
    }

    String getOwnerPackageName() {
        return mOwnerPackageName;
    }

    @Override
    public void launchApplication(@NonNull String packageName) throws RemoteException {
        if (Flags.computerControlActivityPolicyStrict()) {
            // TODO(b/444600407): Remove this once the consent model is per-target app. While the
            // consent is general, the caller can extend the list of target packages dynamically.
            mVirtualDevice.addActivityPolicyExemption(
                    new ActivityPolicyExemption.Builder().setPackageName(packageName).build());
        }
        final UserHandle user = Binder.getCallingUserHandle();
        Binder.withCleanCallingIdentity(() -> mInjector.launchApplicationOnDisplayAsUser(
                packageName, mVirtualDisplayId, user));
        notifyApplicationLaunchToStabilityCalculator();
    }

    @Override
    public void handOverApplications() {
        Binder.withCleanCallingIdentity(
                () -> mInjector.moveAllTasks(mVirtualDisplayId, mMainDisplayId));
    }

    @Override
    public void tap(@IntRange(from = 0) int x, @IntRange(from = 0) int y) throws RemoteException {
        cancelOngoingTouchGestures();
        mVirtualTouchscreen.sendTouchEvent(createTouchEvent(x, y, VirtualTouchEvent.ACTION_DOWN));
        mVirtualTouchscreen.sendTouchEvent(createTouchEvent(x, y, VirtualTouchEvent.ACTION_UP));
        notifyNonContinuousInputToStabilityCalculator();
    }

    @Override
    public void swipe(
            @IntRange(from = 0) int fromX, @IntRange(from = 0) int fromY,
            @IntRange(from = 0) int toX, @IntRange(from = 0) int  toY) throws RemoteException {
        cancelOngoingTouchGestures();
        mVirtualTouchscreen.sendTouchEvent(
                createTouchEvent(fromX, fromY, VirtualTouchEvent.ACTION_DOWN));
        performSwipeStep(fromX, fromY, toX, toY, /* step= */ 0, SWIPE_STEPS);
        notifyContinuousInputToStabilityCalculator();
    }

    @Override
    public void longPress(@IntRange(from = 0) int x, @IntRange(from = 0) int y)
            throws RemoteException {
        cancelOngoingTouchGestures();
        mVirtualTouchscreen.sendTouchEvent(
                createTouchEvent(x, y, VirtualTouchEvent.ACTION_DOWN));
        int longPressStepCount =
                (int) Math.ceil(
                        (double) mInjector.getLongPressTimeoutMillis() / TOUCH_EVENT_DELAY_MS);
        performSwipeStep(x, y, x, y, /* step= */ 0, longPressStepCount);
        notifyContinuousInputToStabilityCalculator();
    }

    @Override
    public void sendTouchEvent(@NonNull VirtualTouchEvent event) throws RemoteException {
        mVirtualTouchscreen.sendTouchEvent(Objects.requireNonNull(event));
    }

    @Override
    public void sendKeyEvent(@NonNull VirtualKeyEvent event) throws RemoteException {
        if (VirtualDpad.isKeyCodeSupported(Objects.requireNonNull(event).getKeyCode())) {
            mVirtualDpad.sendKeyEvent(event);
        } else {
            mVirtualKeyboard.sendKeyEvent(event);
        }
    }

    @Override
    public void performAction(@ComputerControlSession.Action int actionCode)
            throws RemoteException {
        if (actionCode == ComputerControlSession.ACTION_GO_BACK) {
            mVirtualDpad.sendKeyEvent(
                    createKeyEvent(KeyEvent.KEYCODE_BACK, VirtualKeyEvent.ACTION_DOWN));
            mVirtualDpad.sendKeyEvent(
                    createKeyEvent(KeyEvent.KEYCODE_BACK, VirtualKeyEvent.ACTION_UP));
            notifyNonContinuousInputToStabilityCalculator();
        } else {
            Slog.e(TAG, "Invalid action code for performAction: " + actionCode);
            notifyInteractionFailedToStabilityCalculator();
        }
    }

    @Override
    @Nullable
    public IInteractiveMirrorDisplay createInteractiveMirrorDisplay(
            int width, int height, @NonNull Surface surface) throws RemoteException {
        Objects.requireNonNull(surface);
        DisplayInfo displayInfo = mInjector.getDisplayInfo(mVirtualDisplayId);
        if (displayInfo == null) {
            // The display we're trying to mirror is gone; likely the session is already closed.
            return null;
        }
        String name =
                mParams.getName() + "-display-mirror-" + mMirrorDisplayCounter.getAndIncrement();
        VirtualDisplayConfig virtualDisplayConfig =
                new VirtualDisplayConfig.Builder(name, width, height, displayInfo.logicalDensityDpi)
                        .setSurface(surface)
                        .setDisplayIdToMirror(mVirtualDisplayId)
                        .setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR)
                        .build();
        return new InteractiveMirrorDisplayImpl(virtualDisplayConfig, mVirtualDevice);
    }

    @SuppressLint("WrongConstant")
    @Override
    public void insertText(@NonNull String text, boolean replaceExisting, boolean commit) {
        cancelOngoingKeyGestures();
        if (android.companion.virtualdevice.flags.Flags.computerControlTyping()) {
            IRemoteComputerControlInputConnection ic = mInjector.getInputConnection(
                    mVirtualDisplayId);
            if (ic == null) {
                Slog.e(TAG, "Unable to insert text: No input connection found!");
                notifyInteractionFailedToStabilityCalculator();
                return;
            }
            // TODO(b/422134565): Implement client invoker logic to pass the correct session id when
            //  "client text view" invalidates input while view remains focused.
            //  Currently, if we set text using A11y nodes or the application sets text into the
            //  text field outside of input connection (while text view is focused), CC session will
            //  no longer be able to insert text until the text view restarts the input connection.
            try {
                if (replaceExisting) {
                    ic.replaceText(new InputConnectionCommandHeader(0), 0 /* start */,
                            Integer.MAX_VALUE /* end */, text, 1 /* newCursorPosition */);
                } else {
                    ic.commitText(new InputConnectionCommandHeader(0), text,
                            1 /* newCursorPosition */);
                }
                // TODO(b/422134565): Use right editor action to commit text instead key enter
                if (commit) {
                    ic.sendKeyEvent(new InputConnectionCommandHeader(0),
                            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
                    ic.sendKeyEvent(new InputConnectionCommandHeader(0),
                            new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
                }
            } catch (RemoteException e) {
                Slog.e(TAG, "Unable to insert text through InputConnection", e);
            }
        } else {
            KeyCharacterMap kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
            KeyEvent[] events = kcm.getEvents(text.toCharArray());

            if (events == null) {
                Slog.e(TAG, "Couldn't generate key events from the provided text");
                return;
            }
            List<VirtualKeyEvent> keysToSend = new ArrayList<>();
            if (replaceExisting) {
                keysToSend.add(
                        createKeyEvent(KeyEvent.KEYCODE_CTRL_LEFT, VirtualKeyEvent.ACTION_DOWN));
                keysToSend.add(createKeyEvent(KeyEvent.KEYCODE_A, VirtualKeyEvent.ACTION_DOWN));
                keysToSend.add(createKeyEvent(KeyEvent.KEYCODE_A, VirtualKeyEvent.ACTION_UP));
                keysToSend.add(
                        createKeyEvent(KeyEvent.KEYCODE_CTRL_LEFT, VirtualKeyEvent.ACTION_UP));
                keysToSend.add(createKeyEvent(KeyEvent.KEYCODE_DEL, VirtualKeyEvent.ACTION_DOWN));
                keysToSend.add(createKeyEvent(KeyEvent.KEYCODE_DEL, VirtualKeyEvent.ACTION_UP));
            }

            for (KeyEvent event : events) {
                keysToSend.add(createKeyEvent(event.getKeyCode(), event.getAction()));
            }

            if (commit) {
                keysToSend.add(createKeyEvent(KeyEvent.KEYCODE_ENTER, VirtualKeyEvent.ACTION_DOWN));
                keysToSend.add(createKeyEvent(KeyEvent.KEYCODE_ENTER, VirtualKeyEvent.ACTION_UP));
            }
            performKeyStep(keysToSend, 0);
        }
        notifyNonContinuousInputToStabilityCalculator();
    }

    @Override
    public void setStabilityListener(IComputerControlStabilityListener listener) {
        synchronized (mStabilityCalculatorLock) {
            if (listener == null) {
                clearStabilityCalculatorLocked();
                return;
            }
            if (mStabilityCalculator != null) {
                throw new IllegalStateException("Stability listener already set");
            }
            mStabilityCalculator = new StabilityCalculator(listener, mScheduler);
        }
    }

    @Override
    public void close() throws RemoteException {
        clearStabilityCalculator();
        mVirtualDevice.close();
        mAppToken.unlinkToDeath(this, 0);
        mOnClosedListener.onClosed(this);
    }

    @Override
    public void binderDied() {
        try {
            close();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    VirtualTouchEvent createTouchEvent(int x, int y, @VirtualTouchEvent.Action int action) {
        return new VirtualTouchEvent.Builder()
                .setX(x)
                .setY(y)
                .setAction(action)
                .setPointerId(4)
                .setToolType(
                        action == VirtualTouchEvent.ACTION_CANCEL
                                ? VirtualTouchEvent.TOOL_TYPE_PALM
                                : VirtualTouchEvent.TOOL_TYPE_FINGER)
                .setPressure(255)
                .setMajorAxisSize(1)
                .build();
    }

    VirtualKeyEvent createKeyEvent(int keyCode, @VirtualKeyEvent.Action int action) {
        return new VirtualKeyEvent.Builder()
                .setAction(action)
                .setKeyCode(keyCode)
                .build();
    }

    private void performSwipeStep(int fromX, int fromY, int toX, int toY, int step, int stepCount) {
        final double fraction = ((double) step) / stepCount;
        // This makes the movement distance smaller towards the end.
        final double easedFraction = Math.sin(fraction * Math.PI / 2);
        final int currentX = (int) (fromX + (toX - fromX) * easedFraction);
        final int currentY = (int) (fromY + (toY - fromY) * easedFraction);
        final int nextStep = step + 1;

        try {
            mVirtualTouchscreen.sendTouchEvent(
                    createTouchEvent(currentX, currentY, VirtualTouchEvent.ACTION_MOVE));

            if (nextStep > stepCount) {
                mVirtualTouchscreen.sendTouchEvent(
                        createTouchEvent(toX, toY, VirtualTouchEvent.ACTION_UP));
                mSwipeFuture = null;
                return;
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        mSwipeFuture = mScheduler.schedule(
                () -> performSwipeStep(fromX, fromY, toX, toY, nextStep, stepCount),
                TOUCH_EVENT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void performKeyStep(List<VirtualKeyEvent> keysToSend, int currStep) {
        final int nextStep = currStep + 1;
        try {
            mVirtualKeyboard.sendKeyEvent(keysToSend.get(currStep));
            if (nextStep >= keysToSend.size()) {
                mInsertTextFuture = null;
                return;
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        mInsertTextFuture = mScheduler.schedule(
                () -> performKeyStep(keysToSend, nextStep), KEY_EVENT_DELAY_MS,
                TimeUnit.MILLISECONDS);
    }

    private void cancelOngoingKeyGestures() {
        if (mInsertTextFuture != null) {
            mInsertTextFuture.cancel(false);
            mInsertTextFuture = null;
        }
    }

    private void cancelOngoingTouchGestures() throws RemoteException {
        if (mSwipeFuture != null && mSwipeFuture.cancel(false)) {
            mVirtualTouchscreen.sendTouchEvent(
                    createTouchEvent(0, 0, VirtualTouchEvent.ACTION_CANCEL));
        }
    }

    private String createInputDeviceNamePrefix(String packageName) {
        final String prefix = packageName + ":" + mParams.getName();
        return (prefix.length() > MAX_INPUT_DEVICE_NAME_PREFIX_LENGTH)
                ? prefix.substring(prefix.length() - MAX_INPUT_DEVICE_NAME_PREFIX_LENGTH)
                : prefix;
    }

    private void clearStabilityCalculator() {
        synchronized (mStabilityCalculatorLock) {
            clearStabilityCalculatorLocked();
        }
    }

    private void clearStabilityCalculatorLocked() {
        if (mStabilityCalculator != null) {
            mStabilityCalculator.close();
            mStabilityCalculator = null;
        }
    }

    // TODO(b/428957982): Remove once we implement actual stability signals from the framework.
    private void notifyContinuousInputToStabilityCalculator() {
        synchronized (mStabilityCalculatorLock) {
            if (mStabilityCalculator != null) {
                mStabilityCalculator.onContinuousInputEvent();
            }
        }
    }

    // TODO(b/428957982): Remove once we implement actual stability signals from the framework.
    private void notifyNonContinuousInputToStabilityCalculator() {
        synchronized (mStabilityCalculatorLock) {
            if (mStabilityCalculator != null) {
                mStabilityCalculator.onNonContinuousInputEvent();
            }
        }
    }

    // TODO(b/428957982): Remove once we implement actual stability signals from the framework.
    private void notifyApplicationLaunchToStabilityCalculator() {
        synchronized (mStabilityCalculatorLock) {
            if (mStabilityCalculator != null) {
                mStabilityCalculator.onApplicationLaunch();
            }
        }
    }

    // TODO(b/428957982): Remove once we implement actual stability signals from the framework.
    private void notifyInteractionFailedToStabilityCalculator() {
        synchronized (mStabilityCalculatorLock) {
            if (mStabilityCalculator != null) {
                mStabilityCalculator.onInteractionFailed();
            }
        }
    }

    private class ComputerControlActivityListener extends IVirtualDeviceActivityListener.Stub {
        @Override
        public void onTopActivityChanged(int displayId, ComponentName topActivity,
                @UserIdInt int userId) {}

        @Override
        public void onDisplayEmpty(int displayId) {}

        @Override
        public void onActivityLaunchBlocked(int displayId, ComponentName componentName,
                UserHandle user, IntentSender intentSender) {
            Slog.d(TAG, "Blocked activity launch for " + componentName + " on session "
                    + mParams.getName());
            if (Objects.equals(CUSTOM_BLOCKED_APP_ACTIVITY, componentName)) {
                return;
            }
            Intent intent = new Intent()
                    .setComponent(CUSTOM_BLOCKED_APP_ACTIVITY)
                    .putExtra(Intent.EXTRA_COMPONENT_NAME, componentName);
            mInjector.startCustomBlockedActivityOnDisplay(intent, displayId);
        }

        @Override
        public void onSecureWindowShown(int displayId, ComponentName componentName,
                UserHandle user) {}

        @Override
        public void onSecureWindowHidden(int displayId) {}
    }

    /** Interface for listening for closing of sessions. */
    interface OnClosedListener {
        void onClosed(@NonNull ComputerControlSessionImpl session);
    }

    @VisibleForTesting
    public static class Injector {
        private final Context mContext;
        private final PackageManager mPackageManager;
        private final WindowManagerInternal mWindowManagerInternal;
        private final InputMethodManagerInternal mInputMethodManagerInternal;
        private final UserManagerInternal mUserManagerInternal;
        private final ActivityTaskManagerInternal mActivityTaskManagerInternal;

        Injector(Context context) {
            mContext = context;
            mPackageManager = mContext.getPackageManager();
            mWindowManagerInternal = LocalServices.getService(WindowManagerInternal.class);
            mInputMethodManagerInternal = LocalServices.getService(
                    InputMethodManagerInternal.class);
            mUserManagerInternal = LocalServices.getService(UserManagerInternal.class);
            mActivityTaskManagerInternal = LocalServices.getService(
                    ActivityTaskManagerInternal.class);
        }

        public String getPermissionControllerPackageName() {
            return mPackageManager.getPermissionControllerPackageName();
        }

        public void launchApplicationOnDisplayAsUser(String packageName, int displayId,
                UserHandle user) {
            Intent intent = mPackageManager.getLaunchIntentForPackage(packageName);
            if (intent == null) {
                throw new IllegalArgumentException(
                        "Package " + packageName + " does not have a launcher activity.");
            }
            mContext.startActivityAsUser(intent,
                    ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle(), user);
        }

        public void startCustomBlockedActivityOnDisplay(Intent intent, int displayId) {
            mContext.startActivityAsUser(
                    intent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                    ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle(),
                    UserHandle.SYSTEM);
        }

        public DisplayInfo getDisplayInfo(int displayId) {
            final Display display = DisplayManagerGlobal.getInstance().getRealDisplay(displayId);
            if (display == null) {
                return null;
            }
            final DisplayInfo displayInfo = new DisplayInfo();
            display.getDisplayInfo(displayInfo);
            return displayInfo;
        }

        public void disableAnimationsForDisplay(int displayId) {
            mWindowManagerInternal.setAnimationsDisabledForDisplay(displayId, /* disabled= */ true);
        }

        public long getLongPressTimeoutMillis() {
            return (long) (ViewConfiguration.getLongPressTimeout() * LONG_PRESS_TIMEOUT_MULTIPLIER);
        }

        public int getMainDisplayIdForUser(@UserIdInt int user) {
            return mUserManagerInternal.getMainDisplayAssignedToUser(user);
        }

        public IRemoteComputerControlInputConnection getInputConnection(int displayId) {
            // getUserAssignedToDisplay returns the main userId, if we want to support cross
            // profile CC interactions and typing on CC display, we need to find the right user
            // profile here for the CC input connection
            return mInputMethodManagerInternal.getComputerControlInputConnection(
                    mUserManagerInternal.getUserAssignedToDisplay(displayId), displayId);
        }

        public void moveAllTasks(int fromDisplayId, int toDisplayId) {
            mActivityTaskManagerInternal.moveAllTasks(fromDisplayId, toDisplayId);
        }
    }
}
