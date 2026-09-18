/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.input;

import static android.text.TextUtils.formatSimple;

import static com.android.hardware.input.Flags.disableSettingsForVirtualDevices;

import android.Manifest;
import android.annotation.EnforcePermission;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresNoPermission;
import android.annotation.StringDef;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.hardware.display.DisplayManagerInternal;
import android.hardware.input.IVirtualDpad;
import android.hardware.input.IVirtualGamepad;
import android.hardware.input.IVirtualKeyboard;
import android.hardware.input.IVirtualMouse;
import android.hardware.input.IVirtualNavigationTouchpad;
import android.hardware.input.IVirtualRotaryEncoder;
import android.hardware.input.IVirtualStylus;
import android.hardware.input.IVirtualTouchscreen;
import android.hardware.input.InputDeviceIdentifier;
import android.hardware.input.InputManager.InputDeviceListener;
import android.hardware.input.InputManagerGlobal;
import android.hardware.input.ViewBehaviorConfig;
import android.hardware.input.VirtualGamepad;
import android.hardware.input.VirtualGamepadMotionEvent;
import android.hardware.input.VirtualKeyEvent;
import android.hardware.input.VirtualMouseButtonEvent;
import android.hardware.input.VirtualMouseRelativeEvent;
import android.hardware.input.VirtualMouseScrollEvent;
import android.hardware.input.VirtualRotaryEncoderScrollEvent;
import android.hardware.input.VirtualStylusButtonEvent;
import android.hardware.input.VirtualStylusMotionEvent;
import android.hardware.input.VirtualTouchEvent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInputConstants;
import android.os.Process;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Slog;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalServices;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/** Controls virtual input devices, including device lifecycle and event dispatch. */
class VirtualInputDeviceController {

    private static final String TAG = "VirtualInputController";

    private static final AtomicLong sNextPhysId = new AtomicLong(1);

    static final String NAVIGATION_TOUCHPAD_DEVICE_TYPE = "touchNavigation";

    static final String PHYS_TYPE_DPAD = "Dpad";
    static final String PHYS_TYPE_KEYBOARD = "Keyboard";
    static final String PHYS_TYPE_GAMEPAD = "Gamepad";
    static final String PHYS_TYPE_MOUSE = "Mouse";
    static final String PHYS_TYPE_TOUCHSCREEN = "Touchscreen";
    static final String PHYS_TYPE_NAVIGATION_TOUCHPAD = "NavigationTouchpad";
    static final String PHYS_TYPE_STYLUS = "Stylus";
    static final String PHYS_TYPE_ROTARY_ENCODER = "RotaryEncoder";
    @StringDef(prefix = { "PHYS_TYPE_" }, value = {
            PHYS_TYPE_DPAD,
            PHYS_TYPE_KEYBOARD,
            PHYS_TYPE_GAMEPAD,
            PHYS_TYPE_MOUSE,
            PHYS_TYPE_TOUCHSCREEN,
            PHYS_TYPE_NAVIGATION_TOUCHPAD,
            PHYS_TYPE_STYLUS,
            PHYS_TYPE_ROTARY_ENCODER,
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface PhysType {
    }

    final Object mLock = new Object();

    /* Token -> file descriptor associations. */
    @GuardedBy("mLock")
    private final ArrayMap<IBinder, InputDeviceDescriptor> mInputDeviceDescriptors =
            new ArrayMap<>();

    private final Context mContext;
    private final Handler mHandler;
    private final NativeWrapper mNativeWrapper;
    private final InputManagerService mService;
    private final DeviceCreationThreadVerifier mThreadVerifier;

    VirtualInputDeviceController(@NonNull Context context, @NonNull Handler handler,
            @NonNull InputManagerService service) {
        this(context, new NativeWrapper(), handler, service,
                // Verify that virtual input devices are not created on the handler thread.
                () -> !handler.getLooper().isCurrentThread());
    }

    @VisibleForTesting
    VirtualInputDeviceController(@NonNull Context context, @NonNull NativeWrapper nativeWrapper,
            @NonNull Handler handler,
            @NonNull InputManagerService service,
            @NonNull DeviceCreationThreadVerifier threadVerifier) {
        mContext = context;
        mHandler = handler;
        mNativeWrapper = nativeWrapper;
        mService = service;
        mThreadVerifier = threadVerifier;
    }

    IVirtualDpad createDpad(@NonNull String deviceName, int vendorId, int productId,
            @NonNull IBinder deviceToken, int displayId,
            @Nullable ViewBehaviorConfig viewBehaviorConfig) {
        final String phys = createPhys(PHYS_TYPE_DPAD);
        try {
            final InputDeviceDescriptor device = createDeviceInternal(
                    InputDeviceDescriptor.TYPE_DPAD, deviceName, vendorId,
                    productId, deviceToken, displayId, phys, viewBehaviorConfig,
                    () -> mNativeWrapper.openUinputDpad(deviceName, vendorId, productId, phys));
            return new VirtualDpadDevice(device);
        } catch (DeviceCreationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    IVirtualKeyboard createKeyboard(@NonNull String deviceName, int vendorId, int productId,
            @NonNull IBinder deviceToken, int displayId, @NonNull String languageTag,
            @NonNull String layoutType, @Nullable ViewBehaviorConfig viewBehaviorConfig) {
        final String phys = createPhys(PHYS_TYPE_KEYBOARD);
        mService.addKeyboardLayoutAssociation(phys, languageTag, layoutType);
        try {
            final InputDeviceDescriptor device = createDeviceInternal(
                    InputDeviceDescriptor.TYPE_KEYBOARD, deviceName, vendorId,
                    productId, deviceToken, displayId, phys, viewBehaviorConfig,
                    () -> mNativeWrapper.openUinputKeyboard(deviceName, vendorId, productId, phys));
            return new VirtualKeyboardDevice(device);
        } catch (DeviceCreationException e) {
            mService.removeKeyboardLayoutAssociation(phys);
            throw new IllegalArgumentException(e);
        }
    }

    IVirtualGamepad createGamepad(@NonNull String deviceName, int vendorId, int productId,
            @NonNull IBinder deviceToken, int displayId, boolean registerTriggerAxes) {
        final String phys = createPhys(PHYS_TYPE_GAMEPAD);
        try {
            final InputDeviceDescriptor device = createDeviceInternal(
                    InputDeviceDescriptor.TYPE_GAMEPAD, deviceName, vendorId,
                    productId, deviceToken, displayId, phys, /* viewBehaviorConfig= */ null,
                    () -> mNativeWrapper.openUinputGamepad(deviceName, vendorId, productId, phys,
                            registerTriggerAxes));
            return new VirtualGamepadDevice(device, registerTriggerAxes);
        } catch (DeviceCreationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    IVirtualMouse createMouse(@NonNull String deviceName, int vendorId, int productId,
            @NonNull IBinder deviceToken, int displayId,
            @Nullable ViewBehaviorConfig viewBehaviorConfig) {
        final String phys = createPhys(PHYS_TYPE_MOUSE);
        try {
            final InputDeviceDescriptor device = createDeviceInternal(
                    InputDeviceDescriptor.TYPE_MOUSE, deviceName, vendorId,
                    productId, deviceToken, displayId, phys, viewBehaviorConfig,
                    () -> mNativeWrapper.openUinputMouse(deviceName, vendorId, productId, phys));
            return new VirtualMouseDevice(device);
        } catch (DeviceCreationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    IVirtualTouchscreen createTouchscreen(@NonNull String deviceName, int vendorId, int productId,
            @NonNull IBinder deviceToken, int displayId, int height, int width,
            @Nullable ViewBehaviorConfig viewBehaviorConfig) {
        final String phys = createPhys(PHYS_TYPE_TOUCHSCREEN);
        try {
            final InputDeviceDescriptor device = createDeviceInternal(
                    InputDeviceDescriptor.TYPE_TOUCHSCREEN, deviceName, vendorId, productId,
                    deviceToken, displayId, phys, viewBehaviorConfig,
                    () -> mNativeWrapper.openUinputTouchscreen(deviceName, vendorId, productId,
                            phys, height, width));
            return new VirtualTouchscreenDevice(device);
        } catch (DeviceCreationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    IVirtualNavigationTouchpad createNavigationTouchpad(@NonNull String deviceName, int vendorId,
            int productId, @NonNull IBinder deviceToken, int displayId, int height, int width,
            @Nullable ViewBehaviorConfig viewBehaviorConfig) {
        final String phys = createPhys(PHYS_TYPE_NAVIGATION_TOUCHPAD);
        try {
            final InputDeviceDescriptor device = createDeviceInternal(
                    InputDeviceDescriptor.TYPE_NAVIGATION_TOUCHPAD, deviceName,
                    vendorId, productId, deviceToken, displayId, phys, viewBehaviorConfig,
                    () -> mNativeWrapper.openUinputTouchscreen(deviceName, vendorId, productId,
                            phys, height, width));
            return new VirtualNavigationTouchpadDevice(device);
        } catch (DeviceCreationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    IVirtualStylus createStylus(@NonNull String deviceName, int vendorId, int productId,
            @NonNull IBinder deviceToken, int displayId, int height, int width,
            @Nullable ViewBehaviorConfig viewBehaviorConfig) {
        final String phys = createPhys(PHYS_TYPE_STYLUS);
        try {
            final InputDeviceDescriptor device = createDeviceInternal(
                    InputDeviceDescriptor.TYPE_STYLUS, deviceName, vendorId,
                    productId, deviceToken, displayId, phys, viewBehaviorConfig,
                    () -> mNativeWrapper.openUinputStylus(deviceName, vendorId, productId, phys,
                            height, width));
            return new VirtualStylusDevice(device);
        } catch (DeviceCreationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    IVirtualRotaryEncoder createRotaryEncoder(@NonNull String deviceName, int vendorId,
            int productId, @NonNull IBinder deviceToken, int displayId,
            @Nullable ViewBehaviorConfig viewBehaviorConfig) {
        final String phys = createPhys(PHYS_TYPE_ROTARY_ENCODER);
        try {
            final InputDeviceDescriptor device = createDeviceInternal(
                    InputDeviceDescriptor.TYPE_ROTARY_ENCODER, deviceName,
                    vendorId, productId, deviceToken, displayId, phys, viewBehaviorConfig,
                    () -> mNativeWrapper.openUinputRotaryEncoder(deviceName, vendorId, productId,
                            phys));
            return new VirtualRotaryEncoderDevice(device);
        } catch (DeviceCreationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    void unregisterInputDevice(@NonNull IBinder token) {
        synchronized (mLock) {
            final InputDeviceDescriptor inputDeviceDescriptor = mInputDeviceDescriptors.remove(
                    token);
            if (inputDeviceDescriptor == null) {
                Slog.w(TAG, "Could not unregister input device for given token.");
            } else {
                Binder.withCleanCallingIdentity(
                        () -> closeInputDeviceDescriptorLocked(token, inputDeviceDescriptor));
            }
        }
    }

    @GuardedBy("mLock")
    private void closeInputDeviceDescriptorLocked(IBinder token,
            InputDeviceDescriptor inputDeviceDescriptor) {
        token.unlinkToDeath(inputDeviceDescriptor.getDeathRecipient(), /* flags= */ 0);
        mNativeWrapper.closeUinput(inputDeviceDescriptor.getNativePointerLocked());
        String phys = inputDeviceDescriptor.getPhys();
        mService.removeUniqueIdAssociationByPort(phys);
        if (disableSettingsForVirtualDevices()) {
            mService.removeVirtualDevice(phys);
        }
        mService.unsetConfigurationOverride(phys);

        if (inputDeviceDescriptor.getType() == InputDeviceDescriptor.TYPE_KEYBOARD) {
            mService.removeKeyboardLayoutAssociation(phys);
        }
    }

    private void setDeviceConfiguration(@NonNull String phys, @Nullable String deviceType,
            @Nullable ViewBehaviorConfig viewBehaviorConfig) {
        if (deviceType == null && viewBehaviorConfig == null) {
            return;
        }

        InputManagerService.ConfigurationOverride configurationOverride =
                new InputManagerService.ConfigurationOverride(deviceType, viewBehaviorConfig);
        mService.setConfigurationOverride(phys, configurationOverride);
    }

    /**
     * Validates a device name by checking whether a device with the same name already exists.
     * @param deviceName The name of the device to be validated
     * @throws DeviceCreationException if {@code deviceName} is not valid.
     */
    private void validateDeviceName(String deviceName) throws DeviceCreationException {
        synchronized (mLock) {
            for (int i = 0; i < mInputDeviceDescriptors.size(); ++i) {
                if (mInputDeviceDescriptors.valueAt(i).mName.equals(deviceName)) {
                    throw new DeviceCreationException(
                            "Input device name already in use: " + deviceName);
                }
            }
        }
    }

    private static String createPhys(@PhysType String type) {
        return formatSimple("virtual%s:%d", type, sNextPhysId.getAndIncrement());
    }

    private void setUniqueIdAssociation(int displayId, String phys) {
        DisplayManagerInternal displayManagerInternal =
                LocalServices.getService(DisplayManagerInternal.class);
        final String displayUniqueId = displayManagerInternal.getDisplayInfo(displayId).uniqueId;
        mService.addUniqueIdAssociationByPort(phys, displayUniqueId);
    }

    public void dump(@NonNull PrintWriter fout) {
        fout.println("VirtualInputController: ");
        synchronized (mLock) {
            fout.println("  Active descriptors: ");
            for (int i = 0; i < mInputDeviceDescriptors.size(); ++i) {
                InputDeviceDescriptor inputDeviceDescriptor = mInputDeviceDescriptors.valueAt(i);
                fout.println("    ptr: " + inputDeviceDescriptor.getNativePointerLocked());
                fout.println("      displayId: "
                        + inputDeviceDescriptor.getAssociatedDisplayId());
                fout.println("      creationOrder: "
                        + inputDeviceDescriptor.getCreationOrderNumber());
                fout.println("      type: " + inputDeviceDescriptor.getType());
                fout.println("      phys: " + inputDeviceDescriptor.getPhys());
                fout.println("      inputDeviceId: " + inputDeviceDescriptor.getInputDeviceId());
            }
        }
    }

    private static native long nativeOpenUinputDpad(String deviceName, int vendorId, int productId,
            String phys);
    private static native long nativeOpenUinputKeyboard(String deviceName, int vendorId,
            int productId, String phys);
    private static native long nativeOpenUinputGamepad(String deviceName, int vendorId,
            int productId, String phys, boolean registerTriggerAxes);
    private static native long nativeOpenUinputMouse(String deviceName, int vendorId, int productId,
            String phys);
    private static native long nativeOpenUinputTouchscreen(String deviceName, int vendorId,
            int productId, String phys, int height, int width);
    private static native long nativeOpenUinputStylus(String deviceName, int vendorId,
            int productId, String phys, int height, int width);
    private static native long nativeOpenUinputRotaryEncoder(String deviceName, int vendorId,
            int productId, String phys);

    private static native void nativeCloseUinput(long ptr);
    private static native boolean nativeWriteDpadKeyEvent(long ptr, int androidKeyCode, int action,
            long eventTimeNanos);
    private static native boolean nativeWriteKeyEvent(long ptr, int androidKeyCode, int action,
            long eventTimeNanos);
    private static native boolean nativeWriteGamepadKeyEvent(long ptr, int androidKeyCode,
            int action, long eventTimeNanos);
    private static native boolean nativeWriteGamepadMotionEvent(long ptr, float x, float y, float z,
            float rz, float hatX, float hatY, float lTrigger, float rTrigger,
            long eventTimeNanos);
    private static native boolean nativeWriteButtonEvent(long ptr, int buttonCode, int action,
            long eventTimeNanos);
    private static native boolean nativeWriteTouchEvent(long ptr, int pointerId, int toolType,
            int action, float locationX, float locationY, float pressure, float majorAxisSize,
            long eventTimeNanos);
    private static native boolean nativeWriteRelativeEvent(long ptr, float relativeX,
            float relativeY, long eventTimeNanos);
    private static native boolean nativeWriteScrollEvent(long ptr, float xAxisMovement,
            float yAxisMovement, long eventTimeNanos);
    private static native boolean nativeWriteStylusMotionEvent(long ptr, int toolType, int action,
            int locationX, int locationY, int pressure, int tiltX, int tiltY, long eventTimeNanos);
    private static native boolean nativeWriteStylusButtonEvent(long ptr, int buttonCode, int action,
            long eventTimeNanos);
    private static native boolean nativeWriteRotaryEncoderScrollEvent(long ptr, float scrollAmount,
            long eventTimeNanos);

    /** Wrapper around the static native methods for tests. */
    @VisibleForTesting
    protected static class NativeWrapper {
        public long openUinputDpad(String deviceName, int vendorId, int productId, String phys) {
            return nativeOpenUinputDpad(deviceName, vendorId, productId, phys);
        }

        public long openUinputKeyboard(String deviceName, int vendorId, int productId,
                String phys) {
            return nativeOpenUinputKeyboard(deviceName, vendorId, productId, phys);
        }

        public long openUinputGamepad(String deviceName, int vendorId, int productId,
                String phys, boolean registerTriggerAxes) {
            return nativeOpenUinputGamepad(deviceName, vendorId, productId, phys,
                    registerTriggerAxes);
        }

        public long openUinputMouse(String deviceName, int vendorId, int productId, String phys) {
            return nativeOpenUinputMouse(deviceName, vendorId, productId, phys);
        }

        public long openUinputTouchscreen(String deviceName, int vendorId,
                int productId, String phys, int height, int width) {
            return nativeOpenUinputTouchscreen(deviceName, vendorId, productId, phys, height,
                    width);
        }

        public long openUinputStylus(String deviceName, int vendorId, int productId, String phys,
                int height, int width) {
            return nativeOpenUinputStylus(deviceName, vendorId, productId, phys, height, width);
        }

        public long openUinputRotaryEncoder(String deviceName, int vendorId, int productId,
                String phys) {
            return nativeOpenUinputRotaryEncoder(deviceName, vendorId, productId, phys);
        }

        public void closeUinput(long ptr) {
            nativeCloseUinput(ptr);
        }

        public boolean writeDpadKeyEvent(long ptr, int androidKeyCode, int action,
                long eventTimeNanos) {
            return nativeWriteDpadKeyEvent(ptr, androidKeyCode, action, eventTimeNanos);
        }

        public boolean writeKeyEvent(long ptr, int androidKeyCode, int action,
                long eventTimeNanos) {
            return nativeWriteKeyEvent(ptr, androidKeyCode, action, eventTimeNanos);
        }

        public boolean writeGamepadKeyEvent(long ptr, int androidKeyCode, int action,
                long eventTimeNanos) {
            return nativeWriteGamepadKeyEvent(ptr, androidKeyCode, action, eventTimeNanos);
        }

        public boolean writeGamepadMotionEvent(long ptr, VirtualGamepadMotionEvent event) {
            return nativeWriteGamepadMotionEvent(ptr, event.x, event.y, event.z, event.rz,
                    event.hatX, event.hatY, event.lTrigger, event.rTrigger, event.eventTimeNanos);
        }

        public boolean writeButtonEvent(long ptr, int buttonCode, int action,
                long eventTimeNanos) {
            return nativeWriteButtonEvent(ptr, buttonCode, action, eventTimeNanos);
        }

        public boolean writeTouchEvent(long ptr, int pointerId, int toolType, int action,
                float locationX, float locationY, float pressure, float majorAxisSize,
                long eventTimeNanos) {
            return nativeWriteTouchEvent(ptr, pointerId, toolType,
                    action, locationX, locationY,
                    pressure, majorAxisSize, eventTimeNanos);
        }

        public boolean writeRelativeEvent(long ptr, float relativeX, float relativeY,
                long eventTimeNanos) {
            return nativeWriteRelativeEvent(ptr, relativeX, relativeY, eventTimeNanos);
        }

        public boolean writeScrollEvent(long ptr, float xAxisMovement, float yAxisMovement,
                long eventTimeNanos) {
            return nativeWriteScrollEvent(ptr, xAxisMovement, yAxisMovement, eventTimeNanos);
        }

        public boolean writeStylusMotionEvent(long ptr, int toolType, int action, int locationX,
                int locationY, int pressure, int tiltX, int tiltY, long eventTimeNanos) {
            return nativeWriteStylusMotionEvent(ptr, toolType, action, locationX, locationY,
                    pressure, tiltX, tiltY, eventTimeNanos);
        }

        public boolean writeStylusButtonEvent(long ptr, int buttonCode, int action,
                long eventTimeNanos) {
            return nativeWriteStylusButtonEvent(ptr, buttonCode, action, eventTimeNanos);
        }

        public boolean writeRotaryEncoderScrollEvent(long ptr, float scrollAmount,
                long eventTimeNanos) {
            return nativeWriteRotaryEncoderScrollEvent(ptr, scrollAmount, eventTimeNanos);
        }
    }

    private static final class InputDeviceDescriptor {

        static final int TYPE_KEYBOARD = 1;
        static final int TYPE_MOUSE = 2;
        static final int TYPE_TOUCHSCREEN = 3;
        static final int TYPE_DPAD = 4;
        static final int TYPE_NAVIGATION_TOUCHPAD = 5;
        static final int TYPE_STYLUS = 6;
        static final int TYPE_ROTARY_ENCODER = 7;
        static final int TYPE_GAMEPAD = 8;
        @IntDef(prefix = { "TYPE_" }, value = {
                TYPE_KEYBOARD,
                TYPE_MOUSE,
                TYPE_TOUCHSCREEN,
                TYPE_DPAD,
                TYPE_NAVIGATION_TOUCHPAD,
                TYPE_STYLUS,
                TYPE_ROTARY_ENCODER,
                TYPE_GAMEPAD,
        })
        @Retention(RetentionPolicy.SOURCE)
        @interface Type {
        }

        private static final AtomicLong sNextCreationOrderNumber = new AtomicLong(1);

        private final VirtualInputDeviceController mController;

        // Pointer to the native input device object.
        @GuardedBy("mReadWriteLock")
        private long mPtr;
        private final ReadWriteLock mReadWriteLock = new ReentrantReadWriteLock(true);
        private final IBinder.DeathRecipient mDeathRecipient;
        private final @Type int mType;
        private final int mDisplayId;
        private final String mPhys;
        // The name given to this device by the client. Enforced to be unique within
        // InputController.
        private final String mName;
        // The input device id that was associated to the device by the InputReader on device
        // creation.
        private final int mInputDeviceId;
        // Monotonically increasing number; devices with lower numbers were created earlier.
        private final long mCreationOrderNumber;
        // Token from the creator of this device.
        private final IBinder mToken;

        InputDeviceDescriptor(VirtualInputDeviceController controller, long ptr,
                IBinder.DeathRecipient deathRecipient, @Type int type, int displayId, String phys,
                String name, int inputDeviceId, IBinder token) {
            mController = controller;
            mPtr = ptr;
            mDeathRecipient = deathRecipient;
            mType = type;
            mDisplayId = displayId;
            mPhys = phys;
            mName = name;
            mInputDeviceId = inputDeviceId;
            mToken = token;
            mCreationOrderNumber = sNextCreationOrderNumber.getAndIncrement();
        }

        long getNativePointerLocked() {
            return mPtr;
        }

        int getType() {
            return mType;
        }

        boolean isMouse() {
            return mType == TYPE_MOUSE;
        }

        IBinder.DeathRecipient getDeathRecipient() {
            return mDeathRecipient;
        }

        long getCreationOrderNumber() {
            return mCreationOrderNumber;
        }

        String getPhys() {
            return mPhys;
        }

        void close() {
            mReadWriteLock.writeLock().lock();
            try {
                if (isClosedLocked()) {
                    return;
                }
                mController.unregisterInputDevice(mToken);
                mPtr = 0;
            } finally {
                mReadWriteLock.writeLock().unlock();
            }
        }

        int getInputDeviceId() {
            return mInputDeviceId;
        }

        int getAssociatedDisplayId() {
            return mDisplayId;
        }

        boolean injectInput(@NonNull Supplier<Boolean> inputEventInjector) {
            mReadWriteLock.readLock().lock();
            try {
                if (isClosedLocked()) {
                    return false;
                }
                return inputEventInjector.get();
            } finally {
                mReadWriteLock.readLock().unlock();
            }
        }

        private boolean isClosedLocked() {
            return mPtr == 0;
        }

        @Override
        public String toString() {
            return "VirtualInputDevice("
                    + " name=" + mName
                    + " associatedDisplayId=" + mDisplayId
                    + " type=" + mType
                    + " phys=" + mPhys + ")";
        }
    }

    private final class BinderDeathRecipient implements IBinder.DeathRecipient {

        private final IBinder mDeviceToken;

        BinderDeathRecipient(IBinder deviceToken) {
            mDeviceToken = deviceToken;
        }

        @Override
        public void binderDied() {
            // All callers are expected to call {@link VirtualDevice#unregisterInputDevice} before
            // quitting, which removes this death recipient. If this is invoked, the remote end
            // died, or they disposed of the object without properly unregistering.
            Slog.e(TAG, "Virtual input controller binder died");
            unregisterInputDevice(mDeviceToken);
        }
    }

    /** A helper class used to wait for an input device to be registered. */
    private class WaitForDevice implements AutoCloseable {
        private final CountDownLatch mDeviceAddedLatch = new CountDownLatch(1);
        private final String mDeviceName;
        private final InputDeviceListener mListener;

        private int mInputDeviceId = IInputConstants.INVALID_INPUT_DEVICE_ID;

        WaitForDevice(@NonNull String phys, @NonNull String deviceName, int vendorId, int productId,
                int associatedDisplayId) {
            mDeviceName = deviceName;
            mListener = new InputDeviceListener() {
                @Override
                public void onInputDeviceAdded(int deviceId) {
                    onInputDeviceChanged(deviceId);
                }

                @Override
                public void onInputDeviceRemoved(int deviceId) {
                }

                @Override
                public void onInputDeviceChanged(int deviceId) {
                    if (isMatchingDevice(deviceId)) {
                        mInputDeviceId = deviceId;
                        mDeviceAddedLatch.countDown();
                    }
                }

                private boolean isMatchingDevice(int deviceId) {
                    final InputDevice device = mService.getInputDevice(deviceId);
                    if (device == null) {
                        return false;
                    }
                    if (!device.getName().equals(deviceName)) {
                        return false;
                    }
                    if (!phys.equals(mService.getPhysicalLocationPath(deviceId))) {
                        return false;
                    }
                    final InputDeviceIdentifier id = device.getIdentifier();
                    if (id.getVendorId() != vendorId || id.getProductId() != productId) {
                        return false;
                    }
                    if (device.getAssociatedDisplayId() != associatedDisplayId) {
                        return false;
                    }
                    if (disableSettingsForVirtualDevices() && device.isPhysicalDevice()) {
                        return false;
                    }
                    return true;
                }
            };
            // TODO(b/419493538): Switch to IInputDevicesChangedListener directly with mService
            InputManagerGlobal.getInstance().registerInputDeviceListener(mListener, mHandler);
        }

        /**
         * Note: This must not be called from {@link #mHandler}'s thread.
         * @throws DeviceCreationException if the device was not created successfully within the
         * timeout.
         * @return The id of the created input device.
         */
        int waitForDeviceCreation() throws DeviceCreationException {
            try {
                if (!mDeviceAddedLatch.await(1, TimeUnit.MINUTES)) {
                    throw new DeviceCreationException(
                            "Timed out waiting for virtual input device " + mDeviceName
                                    + " to be created.");
                }
            } catch (InterruptedException e) {
                throw new DeviceCreationException(
                        "Interrupted while waiting for virtual input device " + mDeviceName
                                + " to be created.", e);
            }
            if (mInputDeviceId == IInputConstants.INVALID_INPUT_DEVICE_ID) {
                throw new IllegalStateException(
                        "Virtual input device " + mDeviceName + " was created with an invalid "
                                + "id=" + mInputDeviceId);
            }
            return mInputDeviceId;
        }

        @Override
        public void close() {
            InputManagerGlobal.getInstance().unregisterInputDeviceListener(mListener);
        }
    }

    /**
     * An internal exception that is thrown to indicate an error when opening a virtual input
     * device.
     */
    static class DeviceCreationException extends Exception {
        DeviceCreationException(String message) {
            super(message);
        }
        DeviceCreationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Creates a virtual input device synchronously, and waits for the notification that the device
     * was added.
     *
     * Note: Input device creation is expected to happen on a binder thread, and the calling thread
     * will be blocked until the input device creation is successful. This should not be called on
     * the handler's thread.
     *
     * @throws DeviceCreationException Throws this exception if anything unexpected happens in the
     *                                 process of creating the device. This method will take care
     *                                 to restore the state of the system in the event of any
     *                                 unexpected behavior.
     */
    private InputDeviceDescriptor createDeviceInternal(@InputDeviceDescriptor.Type int type,
            String deviceName, int vendorId, int productId, IBinder deviceToken, int displayId,
            String phys, ViewBehaviorConfig viewBehaviorConfig,
            Supplier<Long> deviceOpener) throws DeviceCreationException {
        if (!mThreadVerifier.isValidThread()) {
            throw new IllegalStateException(
                    "Virtual input device creation should happen on an auxiliary thread (e.g. "
                            + "binder thread) and not from the handler's thread.");
        }
        validateDeviceName(deviceName);

        final long ptr;
        final BinderDeathRecipient binderDeathRecipient;

        final int inputDeviceId;

        if (displayId == Display.INVALID_DISPLAY) {
            if (!hasAnyPermission("createDeviceInternal",
                    Manifest.permission.INJECT_KEY_EVENTS, Manifest.permission.INJECT_EVENTS)) {
                throw new SecurityException(
                        "Creating a virtual keyboard without a display ID requires the caller  "
                                + "to have the INJECT_KEY_EVENTS or INJECT_EVENTS permission.");
            }
        } else {
            setUniqueIdAssociation(displayId, phys);
        }

        final String deviceType = type == InputDeviceDescriptor.TYPE_NAVIGATION_TOUCHPAD
                ? NAVIGATION_TOUCHPAD_DEVICE_TYPE : null;
        setDeviceConfiguration(phys, deviceType, viewBehaviorConfig);

        if (disableSettingsForVirtualDevices()) {
            mService.addVirtualDevice(phys);
        }
        try (WaitForDevice waiter = new WaitForDevice(phys, deviceName, vendorId, productId,
                displayId)) {
            ptr = deviceOpener.get();
            // See INVALID_PTR in libs/input/VirtualInputDevice.cpp.
            if (ptr == 0) {
                throw new DeviceCreationException(
                        "A native error occurred when creating virtual input device: "
                                + deviceName);
            }
            // The pointer to the native input device is valid from here, so ensure that all
            // failures close the device after this point.
            try {
                inputDeviceId = waiter.waitForDeviceCreation();

                binderDeathRecipient = new BinderDeathRecipient(deviceToken);
                try {
                    deviceToken.linkToDeath(binderDeathRecipient, /* flags= */ 0);
                } catch (RemoteException e) {
                    throw new DeviceCreationException(
                            "Client died before virtual input device could be created.", e);
                }
            } catch (DeviceCreationException e) {
                mNativeWrapper.closeUinput(ptr);
                throw e;
            }

            InputDeviceDescriptor device = new InputDeviceDescriptor(this, ptr,
                binderDeathRecipient, type, displayId, phys, deviceName, inputDeviceId,
                deviceToken);
            synchronized (mLock) {
                if (mInputDeviceDescriptors.containsKey(deviceToken)) {
                    throw new DeviceCreationException("Cannot create new virtual input device "
                        + "with an existing token.");
                }
                mInputDeviceDescriptors.put(deviceToken, device);
            }
            return device;
        } catch (DeviceCreationException e) {
            mService.removeUniqueIdAssociationByPort(phys);
            mService.unsetConfigurationOverride(phys);
            if (disableSettingsForVirtualDevices()) {
                mService.removeVirtualDevice(phys);
            }
            throw e;
        }
    }

    // Returns true if any of the permissions are granted by the caller.
    private boolean hasAnyPermission(String func, String...permissions) {
        if (Binder.getCallingPid() == Process.myPid()) {
            return true;
        }

        for (String permission : permissions) {
            if (mContext.checkCallingPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }

        String msg = "Permission Denial: " + func + " from pid="
                + Binder.getCallingPid()
                + ", uid=" + Binder.getCallingUid()
                + " requires one of: " + Arrays.toString(permissions);
        Slog.w(TAG, msg);
        return false;
    }

    /** A wrapper for an InputDeviceDescriptor that exposes it as an IVirtualGamepad. */
    private final class VirtualGamepadDevice extends IVirtualGamepad.Stub {
        private final InputDeviceDescriptor mDevice;
        private final boolean mRegisterTriggerAxes;

        VirtualGamepadDevice(@NonNull InputDeviceDescriptor device, boolean registerTriggerAxes) {
            mDevice = device;
            mRegisterTriggerAxes = registerTriggerAxes;
        }

        @Override
        @EnforcePermission(Manifest.permission.INJECT_EVENTS)
        public void close() {
            close_enforcePermission();
            mDevice.close();
        }

        @Override
        @EnforcePermission(Manifest.permission.INJECT_EVENTS)
        public boolean sendGamepadKeyEvent(VirtualKeyEvent event) {
            sendGamepadKeyEvent_enforcePermission();
            if (!VirtualGamepad.SUPPORTED_KEY_CODES.contains(event.getKeyCode())) {
                throw new IllegalArgumentException(
                        "Unsupported key code " + event.getKeyCode() + "("
                                + KeyEvent.keyCodeToString(event.getKeyCode()) + ")"
                                + " sent to a VirtualGamepad input device.");
            }
            return mDevice.injectInput(
                    () ->
                            mNativeWrapper.writeGamepadKeyEvent(
                                    mDevice.getNativePointerLocked(),
                                    event.getKeyCode(),
                                    event.getAction(),
                                    event.getEventTimeNanos()));
        }

        @Override
        @EnforcePermission(Manifest.permission.INJECT_EVENTS)
        public boolean sendGamepadMotionEvent(VirtualGamepadMotionEvent event) {
            sendGamepadMotionEvent_enforcePermission();
            if ((!Float.isNaN(event.lTrigger) || !Float.isNaN(event.rTrigger))
                    && !mRegisterTriggerAxes) {
                throw new IllegalArgumentException(
                        "Cannot send trigger values on a gamepad that did not register trigger"
                                + " axes");
            }
            validateAxisValue(event.x, MotionEvent.AXIS_X);
            validateAxisValue(event.y, MotionEvent.AXIS_Y);
            validateAxisValue(event.z, MotionEvent.AXIS_Z);
            validateAxisValue(event.rz, MotionEvent.AXIS_RZ);
            validateAxisValue(event.hatX, MotionEvent.AXIS_HAT_X);
            validateAxisValue(event.hatY, MotionEvent.AXIS_HAT_Y);
            validateAxisValue(event.lTrigger, MotionEvent.AXIS_LTRIGGER);
            validateAxisValue(event.rTrigger, MotionEvent.AXIS_RTRIGGER);
            return mDevice.injectInput(
                    () ->
                            mNativeWrapper.writeGamepadMotionEvent(
                                    mDevice.getNativePointerLocked(), event));
        }

        private void validateAxisValue(float value, int axis) {
            if (Float.isNaN(value)) {
                return;
            }
            final float min;
            final float max;
            switch (axis) {
                case MotionEvent.AXIS_LTRIGGER:
                case MotionEvent.AXIS_RTRIGGER:
                    min = 0.0f;
                    max = 1.0f;
                    break;
                default:
                    min = -1.0f;
                    max = 1.0f;
            }
            if (value < min || value > max) {
                throw new IllegalArgumentException("Unsupported value " + value
                        + " for axis " + MotionEvent.axisToString(axis)
                        + " sent to virtual gamepad " + mDevice.mName);
            }
        }
    }

    /** A wrapper for an InputDeviceDescriptor that exposes it as an IVirtualKeyboard. */
    private final class VirtualKeyboardDevice extends IVirtualKeyboard.Stub {
        private final InputDeviceDescriptor mDevice;

        VirtualKeyboardDevice(@NonNull InputDeviceDescriptor device) {
            mDevice = device;
        }

        @RequiresNoPermission
        @Override
        public void close() {
            mDevice.close();
        }

        @RequiresNoPermission
        @Override
        public boolean sendKeyEvent(VirtualKeyEvent event) {
            return mDevice.injectInput(
                    () ->
                            mNativeWrapper.writeKeyEvent(
                                    mDevice.getNativePointerLocked(),
                                    event.getKeyCode(),
                                    event.getAction(),
                                    event.getEventTimeNanos()));
        }

        @RequiresNoPermission
        @Override
        public int getInputDeviceId() {
            return mDevice.getInputDeviceId();
        }
    }

    /** A wrapper for an InputDeviceDescriptor that exposes it as an IVirtualTouchscreen. */
    private final class VirtualTouchscreenDevice extends IVirtualTouchscreen.Stub {
        private final InputDeviceDescriptor mDevice;

        VirtualTouchscreenDevice(@NonNull InputDeviceDescriptor device) {
            mDevice = device;
        }

        @RequiresNoPermission
        @Override
        public void close() {
            mDevice.close();
        }

        @RequiresNoPermission
        @Override
        public boolean sendTouchEvent(VirtualTouchEvent event) {
            return mDevice.injectInput(
                    () ->
                            mNativeWrapper.writeTouchEvent(
                                    mDevice.getNativePointerLocked(),
                                    event.getPointerId(),
                                    event.getToolType(),
                                    event.getAction(),
                                    event.getX(),
                                    event.getY(),
                                    event.getPressure(),
                                    event.getMajorAxisSize(),
                                    event.getEventTimeNanos()));
        }

        @RequiresNoPermission
        @Override
        public int getInputDeviceId() {
            return mDevice.getInputDeviceId();
        }
    }

    /** A wrapper for an InputDeviceDescriptor that exposes it as an IVirtualDpad. */
    private final class VirtualDpadDevice extends IVirtualDpad.Stub {
        private final InputDeviceDescriptor mDevice;

        VirtualDpadDevice(@NonNull InputDeviceDescriptor device) {
            mDevice = device;
        }

        @RequiresNoPermission
        @Override
        public void close() {
            mDevice.close();
        }

        @RequiresNoPermission
        @Override
        public boolean sendDpadKeyEvent(VirtualKeyEvent event) {
            return mDevice.injectInput(
                    () ->
                            mNativeWrapper.writeDpadKeyEvent(
                                    mDevice.getNativePointerLocked(),
                                    event.getKeyCode(),
                                    event.getAction(),
                                    event.getEventTimeNanos()));
        }

        @RequiresNoPermission
        @Override
        public int getInputDeviceId() {
            return mDevice.getInputDeviceId();
        }
    }

    /** A wrapper for an InputDeviceDescriptor that exposes it as an IVirtualNavigationTouchpad. */
    private final class VirtualNavigationTouchpadDevice extends IVirtualNavigationTouchpad.Stub {
        private final InputDeviceDescriptor mDevice;

        VirtualNavigationTouchpadDevice(@NonNull InputDeviceDescriptor device) {
            mDevice = device;
        }

        @RequiresNoPermission
        @Override
        public void close() {
            mDevice.close();
        }

        @RequiresNoPermission
        @Override
        public boolean sendTouchEvent(VirtualTouchEvent event) {
            return mDevice.injectInput(
                    () ->
                            mNativeWrapper.writeTouchEvent(
                                    mDevice.getNativePointerLocked(),
                                    event.getPointerId(),
                                    event.getToolType(),
                                    event.getAction(),
                                    event.getX(),
                                    event.getY(),
                                    event.getPressure(),
                                    event.getMajorAxisSize(),
                                    event.getEventTimeNanos()));
        }

        @RequiresNoPermission
        @Override
        public int getInputDeviceId() {
            return mDevice.getInputDeviceId();
        }
    }

    /** A wrapper for an InputDeviceDescriptor that exposes it as an IVirtualMouse. */
    private final class VirtualMouseDevice extends IVirtualMouse.Stub {
        private final InputDeviceDescriptor mDevice;

        VirtualMouseDevice(@NonNull InputDeviceDescriptor device) {
            mDevice = device;
        }

        @RequiresNoPermission
        @Override
        public void close() {
            mDevice.close();
        }

        @RequiresNoPermission
        @Override
        public boolean sendMouseButtonEvent(@NonNull VirtualMouseButtonEvent event) {
            return mDevice.injectInput(
                    () ->
                            mNativeWrapper.writeButtonEvent(
                                    mDevice.getNativePointerLocked(),
                                    event.getButtonCode(),
                                    event.getAction(),
                                    event.getEventTimeNanos()));
        }

        @RequiresNoPermission
        @Override
        public boolean sendMouseRelativeEvent(@NonNull VirtualMouseRelativeEvent event) {
            return mDevice.injectInput(
                    () ->
                            mNativeWrapper.writeRelativeEvent(
                                    mDevice.getNativePointerLocked(),
                                    event.getRelativeX(),
                                    event.getRelativeY(),
                                    event.getEventTimeNanos()));
        }

        @RequiresNoPermission
        @Override
        public boolean sendMouseScrollEvent(@NonNull VirtualMouseScrollEvent event) {
            return mDevice.injectInput(
                    () ->
                            mNativeWrapper.writeScrollEvent(
                                    mDevice.getNativePointerLocked(),
                                    event.getXAxisMovement(),
                                    event.getYAxisMovement(),
                                    event.getEventTimeNanos()));
        }

        @RequiresNoPermission
        @Override
        public PointF getCursorPositionInPhysicalDisplay() {
            return Binder.withCleanCallingIdentity(
                    () -> mService.getCursorPositionInPhysicalDisplay(
                                mDevice.getAssociatedDisplayId()));
        }

        @RequiresNoPermission
        @Override
        public PointF getCursorPositionInLogicalDisplay() {
            return Binder.withCleanCallingIdentity(() -> mService.getCursorPositionInLogicalDisplay(
                        mDevice.getAssociatedDisplayId()));
        }

        @RequiresNoPermission
        @Override
        public int getInputDeviceId() {
            return mDevice.getInputDeviceId();
        }
    }

    /** A wrapper for an InputDeviceDescriptor that exposes it as an IVirtualRotaryEncoder. */
    private final class VirtualRotaryEncoderDevice extends IVirtualRotaryEncoder.Stub {
        private final InputDeviceDescriptor mDevice;

        VirtualRotaryEncoderDevice(@NonNull InputDeviceDescriptor device) {
            mDevice = device;
        }

        @RequiresNoPermission
        @Override
        public void close() {
            mDevice.close();
        }

        @RequiresNoPermission
        @Override
        public boolean sendRotaryEncoderScrollEvent(VirtualRotaryEncoderScrollEvent event) {
            return mDevice.injectInput(
                    () ->
                            mNativeWrapper.writeRotaryEncoderScrollEvent(
                                    mDevice.getNativePointerLocked(),
                                    event.getScrollAmount(),
                                    event.getEventTimeNanos()));
        }

        @RequiresNoPermission
        @Override
        public int getInputDeviceId() {
            return mDevice.getInputDeviceId();
        }
    }

    /** A wrapper for an InputDeviceDescriptor that exposes it as an IVirtualStylus. */
    private final class VirtualStylusDevice extends IVirtualStylus.Stub {
        private final InputDeviceDescriptor mDevice;

        VirtualStylusDevice(@NonNull InputDeviceDescriptor device) {
            mDevice = device;
        }

        @RequiresNoPermission
        @Override
        public void close() {
            mDevice.close();
        }

        @RequiresNoPermission
        @Override
        public boolean sendStylusMotionEvent(VirtualStylusMotionEvent event) {
            return mDevice.injectInput(
                    () ->
                            mNativeWrapper.writeStylusMotionEvent(
                                    mDevice.getNativePointerLocked(),
                                    event.getToolType(),
                                    event.getAction(),
                                    event.getX(),
                                    event.getY(),
                                    event.getPressure(),
                                    event.getTiltX(),
                                    event.getTiltY(),
                                    event.getEventTimeNanos()));
        }

        @RequiresNoPermission
        @Override
        public boolean sendStylusButtonEvent(VirtualStylusButtonEvent event) {
            return mDevice.injectInput(
                    () ->
                            mNativeWrapper.writeStylusButtonEvent(
                                    mDevice.getNativePointerLocked(),
                                    event.getButtonCode(),
                                    event.getAction(),
                                    event.getEventTimeNanos()));
        }

        @RequiresNoPermission
        @Override
        public int getInputDeviceId() {
            return mDevice.getInputDeviceId();
        }
    }

    @VisibleForTesting
    interface DeviceCreationThreadVerifier {
        /** Returns true if the calling thread is a valid thread for device creation. */
        boolean isValidThread();
    }
}
