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

package com.android.server.input;

import static android.content.PermissionChecker.PERMISSION_GRANTED;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.graphics.PointF;
import android.hardware.display.DisplayManagerInternal;
import android.hardware.input.IInputDevicesChangedListener;
import android.hardware.input.IVirtualDpad;
import android.hardware.input.IVirtualKeyboard;
import android.hardware.input.IVirtualMouse;
import android.hardware.input.IVirtualTouchscreen;
import android.hardware.input.InputManagerGlobal;
import android.hardware.input.ViewBehaviorConfig;
import android.hardware.input.VirtualKeyEvent;
import android.hardware.input.VirtualMouseButtonEvent;
import android.hardware.input.VirtualMouseRelativeEvent;
import android.hardware.input.VirtualMouseScrollEvent;
import android.hardware.input.VirtualTouchEvent;
import android.os.Binder;
import android.os.Handler;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.testing.TestableContext;
import android.testing.TestableLooper;
import android.util.SparseArray;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.android.server.LocalServices;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

@Presubmit
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class VirtualInputDeviceControllerTest {
    private static final Binder TOKEN_1 = new Binder("deviceToken1");
    private static final Binder TOKEN_2 = new Binder("deviceToken2");
    private static final int DISPLAY_ID_1 = 1;
    private static final int DISPLAY_ID_2 = 2;
    private static final int PRODUCT_ID = 3;
    private static final int VENDOR_ID = 4;
    private static final long PTR = 5;
    private static final String LANGUAGE_TAG = "en-US";
    private static final String LAYOUT_TYPE = "qwerty";
    private static final String NAME = "testInputDeviceName";
    private static final String NAME_2 = "testInputDeviceName2";
    private static final long EVENT_TIMESTAMP = 5000L;

    private VirtualInputDeviceController mInputController;
    private TestableLooper mTestableLooper;
    private InputManagerGlobal.TestSession mInputSession;
    private final List<InputDevice> mDevices = new ArrayList<>();
    private IInputDevicesChangedListener mDevicesChangedListener;
    // deviceId -> phys
    private final SparseArray<String> mPhysByDeviceId = new SparseArray<>();
    // uniqueId -> displayId
    private final Map<String, Integer> mDisplayIdMapping = new HashMap<>();
    // phys -> uniqueId
    private final Map<String, String> mUniqueIdAssociationByPort = new HashMap<>();
    private final Set<String> mVirtualDevices = new HashSet<>();

    @Rule
    public final TestableContext mContext = spy(
            new TestableContext(getInstrumentation().getContext()));

    @Mock
    private DisplayManagerInternal mDisplayManagerInternalMock;
    @Mock
    private VirtualInputDeviceController.NativeWrapper mNativeWrapperMock;
    @Mock
    private InputManagerService mInputManagerService;

    @Captor
    private ArgumentCaptor<InputManagerService.ConfigurationOverride> mConfigurationOverrideCaptor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mTestableLooper = TestableLooper.get(this);

        doAnswer(this::handleNativeOpenInputDevice).when(mNativeWrapperMock)
                .openUinputMouse(anyString(), anyInt(), anyInt(), anyString());
        doAnswer(this::handleNativeOpenInputDevice).when(mNativeWrapperMock)
                .openUinputDpad(anyString(), anyInt(), anyInt(), anyString());
        doAnswer(this::handleNativeOpenInputDevice).when(mNativeWrapperMock)
                .openUinputKeyboard(anyString(), anyInt(), anyInt(), anyString());
        doAnswer(this::handleNativeOpenInputDevice).when(mNativeWrapperMock)
                .openUinputTouchscreen(anyString(), anyInt(), anyInt(), anyString(), anyInt(),
                        anyInt());
        doAnswer(this::handleNativeOpenInputDevice).when(mNativeWrapperMock)
                .openUinputStylus(anyString(), anyInt(), anyInt(), anyString(), anyInt(),
                        anyInt());
        doAnswer(this::handleNativeOpenInputDevice).when(mNativeWrapperMock)
                .openUinputRotaryEncoder(anyString(), anyInt(), anyInt(), anyString());

        doAnswer(inv -> mDevicesChangedListener = inv.getArgument(0))
                .when(mInputManagerService).registerInputDevicesChangedListener(notNull());
        doAnswer(inv -> mDevices.stream().mapToInt(InputDevice::getId).toArray())
                .when(mInputManagerService).getInputDeviceIds();
        doAnswer(inv -> mDevices.get(inv.getArgument(0)))
                .when(mInputManagerService).getInputDevice(anyInt());
        doAnswer(inv -> mUniqueIdAssociationByPort.put(inv.getArgument(0), inv.getArgument(1)))
                .when(mInputManagerService).addUniqueIdAssociationByPort(anyString(), anyString());
        doAnswer(inv -> mUniqueIdAssociationByPort.remove(inv.getArgument(0)))
                .when(mInputManagerService).removeUniqueIdAssociationByPort(anyString());
        doAnswer(inv -> mVirtualDevices.add(inv.getArgument(0)))
                .when(mInputManagerService).addVirtualDevice(anyString());
        doAnswer(inv -> mVirtualDevices.remove(inv.getArgument(0)))
                .when(mInputManagerService).removeVirtualDevice(anyString());
        doAnswer(inv -> mPhysByDeviceId.get(inv.getArgument(0)))
                .when(mInputManagerService).getPhysicalLocationPath(anyInt());

        // Set a new instance of InputManager for testing that uses the IInputManager mock as the
        // interface to the server.
        mInputSession = InputManagerGlobal.createTestSession(mInputManagerService);

        setUpDisplay(DISPLAY_ID_1);
        setUpDisplay(DISPLAY_ID_2);
        LocalServices.removeServiceForTest(DisplayManagerInternal.class);
        LocalServices.addService(DisplayManagerInternal.class, mDisplayManagerInternalMock);

        // Allow virtual devices to be created on the looper thread for testing.
        final VirtualInputDeviceController.DeviceCreationThreadVerifier threadVerifier = () -> true;
        mInputController = new VirtualInputDeviceController(mContext, mNativeWrapperMock,
                new Handler(TestableLooper.get(this).getLooper()), mInputManagerService,
                threadVerifier);
    }

    @After
    public void tearDown() {
        mInputSession.close();
    }

    private void setUpDisplay(int displayId) {
        final String uniqueId = "uniqueId:" + displayId;
        doAnswer((inv) -> {
            final DisplayInfo displayInfo = new DisplayInfo();
            displayInfo.uniqueId = uniqueId;
            return displayInfo;
        }).when(mDisplayManagerInternalMock).getDisplayInfo(eq(displayId));
        mDisplayIdMapping.put(uniqueId, displayId);
    }

    private long handleNativeOpenInputDevice(InvocationOnMock inv) {
        final String phys = inv.getArgument(3);
        final int displayId = mUniqueIdAssociationByPort.containsKey(phys)
                ? mDisplayIdMapping.get(mUniqueIdAssociationByPort.get(phys)) :
                Display.INVALID_DISPLAY;
        final InputDevice device = new InputDevice.Builder()
                .setId(mDevices.size())
                .setName(inv.getArgument(0))
                .setVendorId(inv.getArgument(1))
                .setProductId(inv.getArgument(2))
                .setDescriptor(phys)
                .setExternal(true)
                .setIsVirtualDevice(mVirtualDevices.contains(phys))
                .setAssociatedDisplayId(displayId)
                .build();
        mDevices.add(device);
        mPhysByDeviceId.put(device.getId(), phys);
        try {
            mDevicesChangedListener.onInputDevicesChanged(
                    mDevices.stream().flatMapToInt(
                            d -> IntStream.of(d.getId(), d.getGeneration())).toArray());
        } catch (RemoteException ignored) {
        }
        // Process the device added notification.
        mTestableLooper.processAllMessages();
        // Return a placeholder pointer to the native input device.
        return PTR;
    }

    @Test
    public void createInputDevice_openUinput() {
        mInputController.createMouse(NAME, VENDOR_ID, PRODUCT_ID, TOKEN_1, DISPLAY_ID_1,
                /* viewBehaviorConfig= */ null);
        verify(mNativeWrapperMock)
                .openUinputMouse(eq(NAME), eq(VENDOR_ID), eq(PRODUCT_ID), anyString());
    }

    @Test
    public void createStylus_opensUinput() {
        final int height = 50;
        final int width = 60;
        mInputController.createStylus(NAME, VENDOR_ID, PRODUCT_ID, TOKEN_1, DISPLAY_ID_1,
                height, width, /* viewBehaviorConfig= */ null);
        verify(mNativeWrapperMock)
                .openUinputStylus(eq(NAME), eq(VENDOR_ID), eq(PRODUCT_ID), anyString(), eq(height),
                        eq(width));
    }

    @Test
    public void createRotaryEncoder_opensUinput() {
        mInputController.createRotaryEncoder(
                NAME, VENDOR_ID, PRODUCT_ID, TOKEN_1, DISPLAY_ID_1, /* viewBehaviorConfig= */ null);
        verify(mNativeWrapperMock)
                .openUinputRotaryEncoder(eq(NAME), eq(VENDOR_ID), eq(PRODUCT_ID), anyString());
    }

    @Test
    public void createNavigationTouchpad_setsDeviceConfigurationAssociation() {
        ViewBehaviorConfig viewBehaviorConfig = createViewBehaviorConfig();
        mInputController.createNavigationTouchpad(NAME, VENDOR_ID, PRODUCT_ID, TOKEN_1,
                DISPLAY_ID_1, /* touchpadHeight= */ 50, /* touchpadWidth= */ 50,
                viewBehaviorConfig);
        verify(mInputManagerService).setConfigurationOverride(
                startsWith("virtualNavigationTouchpad:"), mConfigurationOverrideCaptor.capture());
        assertThat(mConfigurationOverrideCaptor.getValue().getDeviceType()).isEqualTo(
                "touchNavigation");
        assertViewBehaviorConfigsEqual(
                viewBehaviorConfig,
                mConfigurationOverrideCaptor.getValue().getViewBehaviorConfig());

        mInputController.unregisterInputDevice(TOKEN_1);
        verify(mInputManagerService).unsetConfigurationOverride(
                startsWith("virtualNavigationTouchpad:"));
    }

    @Test
    public void createTouchscreen_setsDeviceConfigurationAssociation() {
        ViewBehaviorConfig viewBehaviorConfig = createViewBehaviorConfig();
        mInputController.createTouchscreen(NAME, VENDOR_ID, PRODUCT_ID, TOKEN_1, DISPLAY_ID_1,
                /* height= */ 50, /* width= */ 50, viewBehaviorConfig);
        verify(mInputManagerService).setConfigurationOverride(
                startsWith("virtualTouchscreen:"), mConfigurationOverrideCaptor.capture());
        assertThat(mConfigurationOverrideCaptor.getValue().getDeviceType()).isNull();
        assertViewBehaviorConfigsEqual(
                viewBehaviorConfig,
                mConfigurationOverrideCaptor.getValue().getViewBehaviorConfig());

        mInputController.unregisterInputDevice(TOKEN_1);
        verify(mInputManagerService).unsetConfigurationOverride(
                startsWith("virtualTouchscreen:"));
    }

    @Test
    public void createKeyboard_addsAndRemovesKeyboardLayoutAssociation() {
        mInputController.createKeyboard(NAME, VENDOR_ID, PRODUCT_ID, TOKEN_1, DISPLAY_ID_1,
                LANGUAGE_TAG, LAYOUT_TYPE, /* viewBehaviorConfig= */ null);
        verify(mInputManagerService).addKeyboardLayoutAssociation(
                startsWith("virtualKeyboard:"), eq(LANGUAGE_TAG), eq(LAYOUT_TYPE));

        mInputController.unregisterInputDevice(TOKEN_1);
        verify(mInputManagerService).removeKeyboardLayoutAssociation(
                startsWith("virtualKeyboard:"));
    }

    @Test
    public void createInputDevice_duplicateNamesAreNotAllowed() {
        mInputController.createDpad(NAME, VENDOR_ID, PRODUCT_ID, TOKEN_1, DISPLAY_ID_1,
                /* viewBehaviorConfig= */ null);
        assertThrows("Device names need to be unique",
                IllegalArgumentException.class,
                () -> mInputController.createDpad(
                        NAME, VENDOR_ID, PRODUCT_ID, TOKEN_2, DISPLAY_ID_2,
                        /* viewBehaviorConfig= */ null));
    }

    @Test
    public void createInputDevice_duplicateTokensAreNotAllowed() {
        mInputController.createDpad(NAME, VENDOR_ID, PRODUCT_ID, TOKEN_1, DISPLAY_ID_1,
                /* viewBehaviorConfig= */ null);
        assertThrows("Device tokens need to be unique",
                IllegalArgumentException.class,
                () -> mInputController.createDpad(
                        NAME_2, VENDOR_ID, PRODUCT_ID, TOKEN_1, DISPLAY_ID_2,
                        /* viewBehaviorConfig= */ null));
    }

    @Test
    public void createInputDevice_differentDevices_haveUniquePhys() throws RemoteException {
        final int d1 = mInputController.createDpad(NAME, VENDOR_ID, PRODUCT_ID, TOKEN_1,
                DISPLAY_ID_1, /* viewBehaviorConfig= */ null).getInputDeviceId();
        final int d2 = mInputController.createDpad(NAME_2, VENDOR_ID, PRODUCT_ID, TOKEN_2,
                DISPLAY_ID_1, /* viewBehaviorConfig= */ null).getInputDeviceId();

        final String phys1 = mPhysByDeviceId.get(d1);
        final String phys2 = mPhysByDeviceId.get(d2);
        assertThat(phys1).isNotEmpty();
        assertThat(phys2).isNotEmpty();
        assertThat(phys1).isNotEqualTo(phys2);
    }

    @Test
    public void getCursorPosition_returnsPositionFromService() throws Exception {
        IVirtualMouse mouse = mInputController.createMouse(
                NAME, VENDOR_ID, PRODUCT_ID, TOKEN_1, DISPLAY_ID_1, /* viewBehaviorConfig= */ null);
        final PointF physicalPoint = new PointF(10.0f, 20.0f);
        when(mouse.getCursorPositionInPhysicalDisplay()).thenReturn(physicalPoint);
        final PointF logicalPoint = new PointF(30.0f, 40.0f);
        when(mouse.getCursorPositionInLogicalDisplay()).thenReturn(logicalPoint);

        assertThat(mouse.getCursorPositionInPhysicalDisplay()).isEqualTo(physicalPoint);
        verify(mInputManagerService).getCursorPositionInPhysicalDisplay(DISPLAY_ID_1);

        assertThat(mouse.getCursorPositionInLogicalDisplay()).isEqualTo(logicalPoint);
        verify(mInputManagerService).getCursorPositionInLogicalDisplay(DISPLAY_ID_1);
    }

    @Test
    public void sendDpadKeyEvent_writesEvent() throws Exception {
        IVirtualDpad dpad = mInputController.createDpad(
                NAME, VENDOR_ID, PRODUCT_ID, TOKEN_1, DISPLAY_ID_1, /* viewBehaviorConfig= */ null);
        when(mNativeWrapperMock.writeDpadKeyEvent(
                PTR, KeyEvent.KEYCODE_BACK, VirtualKeyEvent.ACTION_UP, EVENT_TIMESTAMP))
                .thenReturn(true);

        assertThat(dpad.sendDpadKeyEvent(
                new VirtualKeyEvent.Builder()
                        .setKeyCode(KeyEvent.KEYCODE_BACK)
                        .setAction(VirtualKeyEvent.ACTION_UP)
                        .setEventTimeNanos(EVENT_TIMESTAMP)
                        .build()))
                .isTrue();
        verify(mNativeWrapperMock).writeDpadKeyEvent(
                PTR, KeyEvent.KEYCODE_BACK, VirtualKeyEvent.ACTION_UP, EVENT_TIMESTAMP);
    }

    @Test
    public void sendKeyEvent_writesEvent() throws Exception {
        IVirtualKeyboard keyboard = mInputController.createKeyboard(NAME, VENDOR_ID, PRODUCT_ID,
                TOKEN_1, DISPLAY_ID_1, LANGUAGE_TAG, LAYOUT_TYPE, /* viewBehaviorConfig= */ null);
        when(mNativeWrapperMock.writeKeyEvent(
                PTR, KeyEvent.KEYCODE_A, VirtualKeyEvent.ACTION_UP, EVENT_TIMESTAMP))
                .thenReturn(true);

        assertThat(keyboard.sendKeyEvent(
                new VirtualKeyEvent.Builder()
                        .setKeyCode(KeyEvent.KEYCODE_A)
                        .setAction(VirtualKeyEvent.ACTION_UP)
                        .setEventTimeNanos(EVENT_TIMESTAMP)
                        .build()))
                .isTrue();
        verify(mNativeWrapperMock).writeKeyEvent(
                PTR, KeyEvent.KEYCODE_A, VirtualKeyEvent.ACTION_UP, EVENT_TIMESTAMP);
    }

    @Test
    public void sendMouseButtonEvent_writesEvent() throws Exception {
        IVirtualMouse mouse = mInputController.createMouse(
                NAME, VENDOR_ID, PRODUCT_ID, TOKEN_1, DISPLAY_ID_1, /* viewBehaviorConfig= */ null);
        when(mNativeWrapperMock.writeButtonEvent(
                PTR, VirtualMouseButtonEvent.BUTTON_BACK,
                VirtualMouseButtonEvent.ACTION_BUTTON_PRESS, EVENT_TIMESTAMP))
                .thenReturn(true);

        assertThat(mouse.sendMouseButtonEvent(
                new VirtualMouseButtonEvent.Builder()
                        .setButtonCode(VirtualMouseButtonEvent.BUTTON_BACK)
                        .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_PRESS)
                        .setEventTimeNanos(EVENT_TIMESTAMP)
                        .build()))
                .isTrue();
        verify(mNativeWrapperMock).writeButtonEvent(PTR, VirtualMouseButtonEvent.BUTTON_BACK,
                VirtualMouseButtonEvent.ACTION_BUTTON_PRESS, EVENT_TIMESTAMP);
    }

    @Test
    public void sendMouseRelativeEvent_writesEvent() throws Exception {
        IVirtualMouse mouse = mInputController.createMouse(
                NAME, VENDOR_ID, PRODUCT_ID, TOKEN_1, DISPLAY_ID_1, /* viewBehaviorConfig= */ null);
        final float x = -0.2f;
        final float y = 0.7f;
        when(mNativeWrapperMock.writeRelativeEvent(PTR, x, y, EVENT_TIMESTAMP)).thenReturn(true);

        assertThat(mouse.sendMouseRelativeEvent(
                new VirtualMouseRelativeEvent.Builder()
                        .setRelativeX(x)
                        .setRelativeY(y)
                        .setEventTimeNanos(EVENT_TIMESTAMP)
                        .build()))
                .isTrue();
        verify(mNativeWrapperMock).writeRelativeEvent(PTR, x, y, EVENT_TIMESTAMP);
    }

    @Test
    public void sendMouseScrollEvent_writesEvent() throws Exception {
        IVirtualMouse mouse = mInputController.createMouse(
                NAME, VENDOR_ID, PRODUCT_ID, TOKEN_1, DISPLAY_ID_1, /* viewBehaviorConfig= */ null);
        final float x = 0.5f;
        final float y = 1f;
        when(mNativeWrapperMock.writeScrollEvent(PTR, x, y, EVENT_TIMESTAMP)).thenReturn(true);

        assertThat(mouse.sendMouseScrollEvent(
                new VirtualMouseScrollEvent.Builder()
                        .setXAxisMovement(x)
                        .setYAxisMovement(y)
                        .setEventTimeNanos(EVENT_TIMESTAMP)
                        .build()))
                .isTrue();
        verify(mNativeWrapperMock).writeScrollEvent(PTR, x, y, EVENT_TIMESTAMP);
    }

    @Test
    public void sendTouchEvent_writesEvent() throws Exception {
        IVirtualTouchscreen touchscreen = mInputController.createTouchscreen(NAME, VENDOR_ID,
                PRODUCT_ID, TOKEN_1, DISPLAY_ID_1, /* height= */ 50, /* width= */ 50,
                /* viewBehaviorConfig= */ null);
        final int pointerId = 5;
        final float x = 100.5f;
        final float y = 200.5f;
        final float pressure = 1.0f;
        final float majorAxisSize = 10.0f;
        when(mNativeWrapperMock.writeTouchEvent(PTR, pointerId, VirtualTouchEvent.TOOL_TYPE_FINGER,
                VirtualTouchEvent.ACTION_UP, x, y, pressure, majorAxisSize, EVENT_TIMESTAMP))
                .thenReturn(true);

        assertThat(touchscreen.sendTouchEvent(
                new VirtualTouchEvent.Builder()
                        .setX(x)
                        .setY(y)
                        .setAction(VirtualTouchEvent.ACTION_UP)
                        .setPointerId(pointerId)
                        .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                        .setPressure(pressure)
                        .setMajorAxisSize(majorAxisSize)
                        .setEventTimeNanos(EVENT_TIMESTAMP)
                        .build()))
                .isTrue();
        verify(mNativeWrapperMock).writeTouchEvent(PTR, pointerId,
                VirtualTouchEvent.TOOL_TYPE_FINGER, VirtualTouchEvent.ACTION_UP, x, y, pressure,
                majorAxisSize, EVENT_TIMESTAMP);
    }

    @Test
    public void sendTouchEvent_withoutPressureOrMajorAxisSize_writesEvent() throws Exception {
        IVirtualTouchscreen touchscreen = mInputController.createTouchscreen(NAME, VENDOR_ID,
                PRODUCT_ID, TOKEN_1, DISPLAY_ID_1, /* height= */ 50, /* width= */ 50,
                /* viewBehaviorConfig= */ null);
        final int pointerId = 5;
        final float x = 100.5f;
        final float y = 200.5f;
        when(mNativeWrapperMock.writeTouchEvent(PTR, pointerId, VirtualTouchEvent.TOOL_TYPE_FINGER,
                VirtualTouchEvent.ACTION_UP, x, y, Float.NaN, Float.NaN, EVENT_TIMESTAMP))
                .thenReturn(true);

        assertThat(touchscreen.sendTouchEvent(
                new VirtualTouchEvent.Builder()
                        .setX(x)
                        .setY(y)
                        .setAction(VirtualTouchEvent.ACTION_UP)
                        .setPointerId(pointerId)
                        .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                        .setEventTimeNanos(EVENT_TIMESTAMP)
                        .build()))
                .isTrue();
        verify(mNativeWrapperMock).writeTouchEvent(PTR, pointerId,
                VirtualTouchEvent.TOOL_TYPE_FINGER, VirtualTouchEvent.ACTION_UP, x, y, Float.NaN,
                Float.NaN, EVENT_TIMESTAMP);
    }

    @Test
    public void createKeyboard_noDisplayAssociation_injectKeyEventsPermission() {
        mContext.getTestablePermissions().setPermission(
                android.Manifest.permission.INJECT_KEY_EVENTS,
                PERMISSION_GRANTED);

        mInputController.createKeyboard(NAME, VENDOR_ID, PRODUCT_ID, TOKEN_1,
                Display.INVALID_DISPLAY, LANGUAGE_TAG, LAYOUT_TYPE, /* viewBehaviorConfig= */ null);

        verify(mInputManagerService).addKeyboardLayoutAssociation(
                startsWith("virtualKeyboard:"), eq(LANGUAGE_TAG), eq(LAYOUT_TYPE));
        mInputController.unregisterInputDevice(TOKEN_1);
        verify(mInputManagerService).removeKeyboardLayoutAssociation(
                startsWith("virtualKeyboard:"));
    }

    @Test
    public void createKeyboard_noDisplayAssociation_injectEventsPermission() {
        mContext.getTestablePermissions().setPermission(
                android.Manifest.permission.INJECT_EVENTS,
                PERMISSION_GRANTED);

        mInputController.createKeyboard(NAME, VENDOR_ID, PRODUCT_ID, TOKEN_1,
                Display.INVALID_DISPLAY, LANGUAGE_TAG, LAYOUT_TYPE, /* viewBehaviorConfig= */ null);

        verify(mInputManagerService).addKeyboardLayoutAssociation(
                startsWith("virtualKeyboard:"), eq(LANGUAGE_TAG), eq(LAYOUT_TYPE));
        mInputController.unregisterInputDevice(TOKEN_1);
        verify(mInputManagerService).removeKeyboardLayoutAssociation(
                startsWith("virtualKeyboard:"));
    }

    private static void assertViewBehaviorConfigsEqual(
            ViewBehaviorConfig expected, ViewBehaviorConfig actual) {
        assertThat(actual.getPrimaryDirectionalMotionAxis()).isEqualTo(
                expected.getPrimaryDirectionalMotionAxis());
        assertThat(actual.shouldSmoothScroll()).isEqualTo(expected.shouldSmoothScroll());
    }

    private static ViewBehaviorConfig createViewBehaviorConfig() {
        return new ViewBehaviorConfig.Builder()
                .setShouldSmoothScroll(true)
                .setPrimaryDirectionalMotionAxis(MotionEvent.AXIS_X)
                .build();
    }
}
