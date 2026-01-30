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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.StringDef;
import android.graphics.PointF;
import android.hardware.display.DisplayManagerInternal;
import android.hardware.input.IVirtualInputDevice;
import android.hardware.input.InputDeviceIdentifier;
import android.hardware.input.InputManager.InputDeviceListener;
import android.hardware.input.InputManagerGlobal;
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
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Slog;
import android.view.InputDevice;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalServices;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** Controls virtual input devices, including device lifecycle and event dispatch. */
class VirtualInputDeviceController {

    private static final String TAG = "VirtualInputController";

    private static final AtomicLong sNextPhysId = new AtomicLong(1);

    static final String NAVIGATION_TOUCHPAD_DEVICE_TYPE = "touchNavigation";

    static final String PHYS_TYPE_DPAD = "Dpad";
    static final String PHYS_TYPE_KEYBOARD = "Keyboard";
    static final String PHYS_TYPE_MOUSE = "Mouse";
    static final String PHYS_TYPE_TOUCHSCREEN = "Touchscreen";
    static final String PHYS_TYPE_NAVIGATION_TOUCHPAD = "NavigationTouchpad";
    static final String PHYS_TYPE_STYLUS = "Stylus";
    static final String PHYS_TYPE_ROTARY_ENCODER = "RotaryEncoder";
    @StringDef(prefix = { "PHYS_TYPE_" }, value = {
            PHYS_TYPE_DPAD,
            PHYS_TYPE_KEYBOARD,
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

    private final Handler mHandler;
    private final NativeWrapper mNativeWrapper;
    private final InputManagerService mService;
    private final DeviceCreationThreadVerifier mThreadVerifier;

    VirtualInputDeviceController(@NonNull Handler handler, @NonNull InputManagerService service) {
        this(new NativeWrapper(), handler, service,
                // Verify that virtual input devices are not created on the handler thread.
                () -> !handler.getLooper().isCurrentThread());
    }

    @VisibleForTesting
    VirtualInputDeviceController(@NonNull NativeWrapper nativeWrapper, @NonNull Handler handler,
            @NonNull InputManagerService service,
            @NonNull DeviceCreationThreadVerifier threadVerifier) {
        mHandler = handler;
        mNativeWrapper = nativeWrapper;
        mService = service;
        mThreadVerifier = threadVerifier;
    }

    IVirtualInputDevice createDpad(@NonNull String deviceName, int vendorId, int productId,
            @NonNull IBinder deviceToken, int displayId) {
        final String phys = createPhys(PHYS_TYPE_DPAD);
        try {
            return createDeviceInternal(InputDeviceDescriptor.TYPE_DPAD, deviceName, vendorId,
                    productId, deviceToken, displayId, phys,
                    () -> mNativeWrapper.openUinputDpad(deviceName, vendorId, productId, phys));
        } catch (DeviceCreationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    IVirtualInputDevice createKeyboard(@NonNull String deviceName, int vendorId, int productId,
            @NonNull IBinder deviceToken, int displayId, @NonNull String languageTag,
            @NonNull String layoutType) {
        final String phys = createPhys(PHYS_TYPE_KEYBOARD);
        mService.addKeyboardLayoutAssociation(phys, languageTag, layoutType);
        try {
            return createDeviceInternal(InputDeviceDescriptor.TYPE_KEYBOARD, deviceName, vendorId,
                    productId, deviceToken, displayId, phys,
                    () -> mNativeWrapper.openUinputKeyboard(deviceName, vendorId, productId, phys));
        } catch (DeviceCreationException e) {
            mService.removeKeyboardLayoutAssociation(phys);
            throw new IllegalArgumentException(e);
        }
    }

    IVirtualInputDevice createMouse(@NonNull String deviceName, int vendorId, int productId,
            @NonNull IBinder deviceToken, int displayId) {
        final String phys = createPhys(PHYS_TYPE_MOUSE);
        try {
            return createDeviceInternal(InputDeviceDescriptor.TYPE_MOUSE, deviceName, vendorId,
                    productId, deviceToken, displayId, phys,
                    () -> mNativeWrapper.openUinputMouse(deviceName, vendorId, productId, phys));
        } catch (DeviceCreationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    IVirtualInputDevice createTouchscreen(@NonNull String deviceName, int vendorId, int productId,
            @NonNull IBinder deviceToken, int displayId, int height, int width) {
        final String phys = createPhys(PHYS_TYPE_TOUCHSCREEN);
        try {
            return createDeviceInternal(InputDeviceDescriptor.TYPE_TOUCHSCREEN, deviceName,
                    vendorId, productId, deviceToken, displayId, phys,
                    () -> mNativeWrapper.openUinputTouchscreen(deviceName, vendorId, productId,
                            phys, height, width));
        } catch (DeviceCreationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    IVirtualInputDevice createNavigationTouchpad(@NonNull String deviceName, int vendorId,
            int productId, @NonNull IBinder deviceToken, int displayId, int height, int width) {
        final String phys = createPhys(PHYS_TYPE_NAVIGATION_TOUCHPAD);
        mService.setTypeAssociationInternal(phys, NAVIGATION_TOUCHPAD_DEVICE_TYPE);
        try {
            return createDeviceInternal(InputDeviceDescriptor.TYPE_NAVIGATION_TOUCHPAD, deviceName,
                    vendorId, productId, deviceToken, displayId, phys,
                    () -> mNativeWrapper.openUinputTouchscreen(deviceName, vendorId, productId,
                            phys, height, width));
        } catch (DeviceCreationException e) {
            mService.unsetTypeAssociationInternal(phys);
            throw new IllegalArgumentException(e);
        }
    }

    IVirtualInputDevice createStylus(@NonNull String deviceName, int vendorId, int productId,
            @NonNull IBinder deviceToken, int displayId, int height, int width) {
        final String phys = createPhys(PHYS_TYPE_STYLUS);
        try {
            return createDeviceInternal(InputDeviceDescriptor.TYPE_STYLUS, deviceName, vendorId,
                    productId, deviceToken, displayId, phys,
                    () -> mNativeWrapper.openUinputStylus(deviceName, vendorId, productId, phys,
                            height, width));
        } catch (DeviceCreationException e) {
            throw new IllegalArgumentException(e);
        }
    }

    IVirtualInputDevice createRotaryEncoder(@NonNull String deviceName, int vendorId, int productId,
            @NonNull IBinder deviceToken, int displayId) {
        final String phys = createPhys(PHYS_TYPE_ROTARY_ENCODER);
        try {
            return createDeviceInternal(InputDeviceDescriptor.TYPE_ROTARY_ENCODER, deviceName,
                    vendorId, productId, deviceToken, displayId, phys,
                    () -> mNativeWrapper.openUinputRotaryEncoder(deviceName, vendorId, productId,
                            phys));
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
        mNativeWrapper.closeUinput(inputDeviceDescriptor.getNativePointer());
        String phys = inputDeviceDescriptor.getPhys();
        mService.removeUniqueIdAssociationByPort(phys);
        if (disableSettingsForVirtualDevices()) {
            mService.removeVirtualDevice(phys);
        }
        // Type associations are added in the case of navigation touchpads. Those should be removed
        // once the input device gets closed.
        if (inputDeviceDescriptor.getType() == InputDeviceDescriptor.TYPE_NAVIGATION_TOUCHPAD) {
            mService.unsetTypeAssociationInternal(phys);
        }

        if (inputDeviceDescriptor.getType() == InputDeviceDescriptor.TYPE_KEYBOARD) {
            mService.removeKeyboardLayoutAssociation(phys);
        }
    }

    /**
     * @return the device id for a given token (identifiying a device)
     */
    int getInputDeviceId(IBinder token) {
        synchronized (mLock) {
            final InputDeviceDescriptor inputDeviceDescriptor = mInputDeviceDescriptors.get(token);
            if (inputDeviceDescriptor == null) {
                throw new IllegalArgumentException("Could not get device id for given token");
            }
            return inputDeviceDescriptor.getInputDeviceId();
        }
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

    boolean sendDpadKeyEvent(@NonNull IBinder token, @NonNull VirtualKeyEvent event) {
        synchronized (mLock) {
            final InputDeviceDescriptor inputDeviceDescriptor = mInputDeviceDescriptors.get(
                    token);
            if (inputDeviceDescriptor == null) {
                return false;
            }
            return mNativeWrapper.writeDpadKeyEvent(inputDeviceDescriptor.getNativePointer(),
                    event.getKeyCode(), event.getAction(), event.getEventTimeNanos());
        }
    }

    boolean sendKeyEvent(@NonNull IBinder token, @NonNull VirtualKeyEvent event) {
        synchronized (mLock) {
            final InputDeviceDescriptor inputDeviceDescriptor = mInputDeviceDescriptors.get(
                    token);
            if (inputDeviceDescriptor == null) {
                return false;
            }
            return mNativeWrapper.writeKeyEvent(inputDeviceDescriptor.getNativePointer(),
                    event.getKeyCode(), event.getAction(), event.getEventTimeNanos());
        }
    }

    boolean sendMouseButtonEvent(@NonNull IBinder token, @NonNull VirtualMouseButtonEvent event) {
        synchronized (mLock) {
            final InputDeviceDescriptor inputDeviceDescriptor = mInputDeviceDescriptors.get(
                    token);
            if (inputDeviceDescriptor == null) {
                return false;
            }
            return mNativeWrapper.writeButtonEvent(inputDeviceDescriptor.getNativePointer(),
                    event.getButtonCode(), event.getAction(), event.getEventTimeNanos());
        }
    }

    boolean sendTouchEvent(@NonNull IBinder token, @NonNull VirtualTouchEvent event) {
        synchronized (mLock) {
            final InputDeviceDescriptor inputDeviceDescriptor = mInputDeviceDescriptors.get(
                    token);
            if (inputDeviceDescriptor == null) {
                return false;
            }
            return mNativeWrapper.writeTouchEvent(inputDeviceDescriptor.getNativePointer(),
                    event.getPointerId(), event.getToolType(), event.getAction(), event.getX(),
                    event.getY(), event.getPressure(), event.getMajorAxisSize(),
                    event.getEventTimeNanos());
        }
    }

    boolean sendMouseRelativeEvent(@NonNull IBinder token,
            @NonNull VirtualMouseRelativeEvent event) {
        synchronized (mLock) {
            final InputDeviceDescriptor inputDeviceDescriptor = mInputDeviceDescriptors.get(
                    token);
            if (inputDeviceDescriptor == null) {
                return false;
            }
            return mNativeWrapper.writeRelativeEvent(inputDeviceDescriptor.getNativePointer(),
                    event.getRelativeX(), event.getRelativeY(), event.getEventTimeNanos());
        }
    }

    boolean sendMouseScrollEvent(@NonNull IBinder token, @NonNull VirtualMouseScrollEvent event) {
        synchronized (mLock) {
            final InputDeviceDescriptor inputDeviceDescriptor = mInputDeviceDescriptors.get(
                    token);
            if (inputDeviceDescriptor == null) {
                return false;
            }
            return mNativeWrapper.writeScrollEvent(inputDeviceDescriptor.getNativePointer(),
                    event.getXAxisMovement(), event.getYAxisMovement(), event.getEventTimeNanos());
        }
    }

    public PointF getCursorPositionInPhysicalDisplay(@NonNull IBinder token) {
        synchronized (mLock) {
            final InputDeviceDescriptor inputDeviceDescriptor = mInputDeviceDescriptors.get(
                    token);
            if (inputDeviceDescriptor == null) {
                throw new IllegalArgumentException(
                        "Could not get cursor position for input device for given token");
            }
            return Binder.withCleanCallingIdentity(
                    () -> mService.getCursorPositionInPhysicalDisplay(
                            inputDeviceDescriptor.getAssociatedDisplayId()));
        }
    }

    public PointF getCursorPositionInLogicalDisplay(@NonNull IBinder token) {
        synchronized (mLock) {
            final InputDeviceDescriptor inputDeviceDescriptor = mInputDeviceDescriptors.get(
                    token);
            if (inputDeviceDescriptor == null) {
                throw new IllegalArgumentException(
                        "Could not get cursor position for input device for given token");
            }
            return Binder.withCleanCallingIdentity(() -> mService.getCursorPositionInLogicalDisplay(
                    inputDeviceDescriptor.getAssociatedDisplayId()));
        }
    }

    boolean sendStylusMotionEvent(@NonNull IBinder token, @NonNull VirtualStylusMotionEvent event) {
        synchronized (mLock) {
            final InputDeviceDescriptor inputDeviceDescriptor = mInputDeviceDescriptors.get(
                    token);
            if (inputDeviceDescriptor == null) {
                return false;
            }
            return mNativeWrapper.writeStylusMotionEvent(inputDeviceDescriptor.getNativePointer(),
                    event.getToolType(), event.getAction(), event.getX(), event.getY(),
                    event.getPressure(), event.getTiltX(), event.getTiltY(),
                    event.getEventTimeNanos());
        }
    }

    boolean sendStylusButtonEvent(@NonNull IBinder token, @NonNull VirtualStylusButtonEvent event) {
        synchronized (mLock) {
            final InputDeviceDescriptor inputDeviceDescriptor = mInputDeviceDescriptors.get(
                    token);
            if (inputDeviceDescriptor == null) {
                return false;
            }
            return mNativeWrapper.writeStylusButtonEvent(inputDeviceDescriptor.getNativePointer(),
                    event.getButtonCode(), event.getAction(), event.getEventTimeNanos());
        }
    }

    boolean sendRotaryEncoderScrollEvent(@NonNull IBinder token,
            @NonNull VirtualRotaryEncoderScrollEvent event) {
        synchronized (mLock) {
            final InputDeviceDescriptor inputDeviceDescriptor = mInputDeviceDescriptors.get(
                    token);
            if (inputDeviceDescriptor == null) {
                return false;
            }
            return mNativeWrapper.writeRotaryEncoderScrollEvent(
                    inputDeviceDescriptor.getNativePointer(), event.getScrollAmount(),
                    event.getEventTimeNanos());
        }
    }

    public void dump(@NonNull PrintWriter fout) {
        fout.println("VirtualInputController: ");
        synchronized (mLock) {
            fout.println("  Active descriptors: ");
            for (int i = 0; i < mInputDeviceDescriptors.size(); ++i) {
                InputDeviceDescriptor inputDeviceDescriptor = mInputDeviceDescriptors.valueAt(i);
                fout.println("    ptr: " + inputDeviceDescriptor.getNativePointer());
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

    @VisibleForTesting
    void addDeviceForTesting(IBinder deviceToken, long ptr, int type, int displayId, String phys,
            String deviceName, int inputDeviceId) {
        synchronized (mLock) {
            mInputDeviceDescriptors.put(deviceToken, new InputDeviceDescriptor(this, ptr, () -> {},
                    type, displayId, phys, deviceName, inputDeviceId, deviceToken));
        }
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    Map<IBinder, InputDeviceDescriptor> getInputDeviceDescriptors() {
        final Map<IBinder, InputDeviceDescriptor> inputDeviceDescriptors = new ArrayMap<>();
        synchronized (mLock) {
            inputDeviceDescriptors.putAll(mInputDeviceDescriptors);
        }
        return inputDeviceDescriptors;
    }

    private static native long nativeOpenUinputDpad(String deviceName, int vendorId, int productId,
            String phys);
    private static native long nativeOpenUinputKeyboard(String deviceName, int vendorId,
            int productId, String phys);
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

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    static final class InputDeviceDescriptor extends IVirtualInputDevice.Stub {

        static final int TYPE_KEYBOARD = 1;
        static final int TYPE_MOUSE = 2;
        static final int TYPE_TOUCHSCREEN = 3;
        static final int TYPE_DPAD = 4;
        static final int TYPE_NAVIGATION_TOUCHPAD = 5;
        static final int TYPE_STYLUS = 6;
        static final int TYPE_ROTARY_ENCODER = 7;
        @IntDef(prefix = { "TYPE_" }, value = {
                TYPE_KEYBOARD,
                TYPE_MOUSE,
                TYPE_TOUCHSCREEN,
                TYPE_DPAD,
                TYPE_NAVIGATION_TOUCHPAD,
                TYPE_STYLUS,
                TYPE_ROTARY_ENCODER,
        })
        @Retention(RetentionPolicy.SOURCE)
        @interface Type {
        }

        private static final AtomicLong sNextCreationOrderNumber = new AtomicLong(1);

        private final VirtualInputDeviceController mController;

        // Pointer to the native input device object.
        private final long mPtr;
        private final DeathRecipient mDeathRecipient;
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
                DeathRecipient deathRecipient, @Type int type, int displayId, String phys,
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

        public long getNativePointer() {
            return mPtr;
        }

        public int getType() {
            return mType;
        }

        public boolean isMouse() {
            return mType == TYPE_MOUSE;
        }

        public DeathRecipient getDeathRecipient() {
            return mDeathRecipient;
        }


        public long getCreationOrderNumber() {
            return mCreationOrderNumber;
        }

        public String getPhys() {
            return mPhys;
        }

        @Override
        public void close() {
            mController.unregisterInputDevice(mToken);
        }

        @Override
        public int getInputDeviceId() {
            return mInputDeviceId;
        }

        @Override
        public int getAssociatedDisplayId() {
            return mDisplayId;
        }

        @Override
        public boolean sendDpadKeyEvent(VirtualKeyEvent event) {
            return mController.sendDpadKeyEvent(mToken, event);
        }

        @Override
        public boolean sendKeyEvent(VirtualKeyEvent event) {
            return mController.sendKeyEvent(mToken, event);
        }

        @Override
        public boolean sendMouseButtonEvent(VirtualMouseButtonEvent event) {
            return mController.sendMouseButtonEvent(mToken, event);
        }

        @Override
        public boolean sendMouseRelativeEvent(VirtualMouseRelativeEvent event) {
            return mController.sendMouseRelativeEvent(mToken, event);
        }

        @Override
        public boolean sendMouseScrollEvent(VirtualMouseScrollEvent event) {
            return mController.sendMouseScrollEvent(mToken, event);
        }

        @Override
        public boolean sendTouchEvent(VirtualTouchEvent event) {
            return mController.sendTouchEvent(mToken, event);
        }

        @Override
        public boolean sendStylusMotionEvent(VirtualStylusMotionEvent event) {
            return mController.sendStylusMotionEvent(mToken, event);
        }

        @Override
        public boolean sendStylusButtonEvent(VirtualStylusButtonEvent event) {
            return mController.sendStylusButtonEvent(mToken, event);
        }

        @Override
        public boolean sendRotaryEncoderScrollEvent(VirtualRotaryEncoderScrollEvent event) {
            return mController.sendRotaryEncoderScrollEvent(mToken, event);
        }

        @Override
        public PointF getCursorPositionInPhysicalDisplay() {
            return mController.getCursorPositionInPhysicalDisplay(mToken);
        }

        @Override
        public PointF getCursorPositionInLogicalDisplay() {
            return mController.getCursorPositionInLogicalDisplay(mToken);
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
    private IVirtualInputDevice createDeviceInternal(@InputDeviceDescriptor.Type int type,
            String deviceName, int vendorId, int productId, IBinder deviceToken, int displayId,
            String phys, Supplier<Long> deviceOpener) throws DeviceCreationException {
        if (!mThreadVerifier.isValidThread()) {
            throw new IllegalStateException(
                    "Virtual input device creation should happen on an auxiliary thread (e.g. "
                            + "binder thread) and not from the handler's thread.");
        }
        validateDeviceName(deviceName);

        final long ptr;
        final BinderDeathRecipient binderDeathRecipient;

        final int inputDeviceId;

        setUniqueIdAssociation(displayId, phys);
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
            if (disableSettingsForVirtualDevices()) {
                mService.removeVirtualDevice(phys);
            }
            throw e;
        }
    }

    @VisibleForTesting
    interface DeviceCreationThreadVerifier {
        /** Returns true if the calling thread is a valid thread for device creation. */
        boolean isValidThread();
    }
}
