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
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_DEFAULT;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_ACTIVITY;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_RECENTS;

import static com.android.server.companion.virtual.computercontrol.ComputerControlSessionImpl.KEY_EVENT_DELAY_MS;
import static com.android.server.companion.virtual.computercontrol.ComputerControlSessionImpl.SWIPE_STEPS;
import static com.android.server.companion.virtual.computercontrol.ComputerControlSessionImpl.TOUCH_EVENT_DELAY_MS;
import static com.android.server.companion.virtual.computercontrol.ComputerControlSessionImpl.PRODUCT_ID_DPAD;
import static com.android.server.companion.virtual.computercontrol.ComputerControlSessionImpl.PRODUCT_ID_KEYBOARD;
import static com.android.server.companion.virtual.computercontrol.ComputerControlSessionImpl.PRODUCT_ID_TOUCHSCREEN;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.companion.virtual.ActivityPolicyExemption;
import android.companion.virtual.IVirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.computercontrol.ComputerControlSession;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.companion.virtual.computercontrol.IComputerControlStabilityListener;
import android.companion.virtualdevice.flags.Flags;
import android.content.AttributionSource;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplayConfig;
import android.hardware.input.IVirtualInputDevice;
import android.hardware.input.VirtualDpadConfig;
import android.hardware.input.VirtualKeyEvent;
import android.hardware.input.VirtualKeyboardConfig;
import android.hardware.input.VirtualTouchEvent;
import android.hardware.input.VirtualTouchscreenConfig;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.DisplayInfo;
import android.view.KeyEvent;
import android.view.WindowManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.inputmethod.IRemoteComputerControlInputConnection;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class ComputerControlSessionTest {
    @Rule
    public SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private static final String PERMISSION_CONTROLLER_PACKAGE = "permission.controller.package";

    private static final int MAIN_DISPLAY_ID = 41;
    private static final int VIRTUAL_DISPLAY_ID = 42;
    private static final int DISPLAY_WIDTH = 600;
    private static final int DISPLAY_HEIGHT = 1000;
    private static final int DISPLAY_DPI = 480;
    private static final int LONG_PRESS_STEP_COUNT = 5;
    private static final String TARGET_PACKAGE_1 = "com.android.foo";
    private static final String TARGET_PACKAGE_2 = "com.android.bar";
    private static final long STABILITY_TIMEOUT_MS = 5000L;
    private static final List<String> TARGET_PACKAGE_NAMES =
            List.of(TARGET_PACKAGE_1, TARGET_PACKAGE_2);
    private static final String UNDECLARED_TARGET_PACKAGE = "com.android.baz";

    @Mock
    private ComputerControlSessionProcessor.VirtualDeviceFactory mVirtualDeviceFactory;
    @Mock
    private ComputerControlSessionImpl.Injector mInjector;
    @Mock
    private ComputerControlSessionImpl.OnClosedListener mOnClosedListener;
    @Mock
    private IComputerControlStabilityListener mStabilityListener;
    @Mock
    private IVirtualDevice mVirtualDevice;
    @Mock
    private IRemoteComputerControlInputConnection mRemoteComputerControlInputConnection;
    @Mock
    private IVirtualInputDevice mVirtualDpad;
    @Mock
    private IVirtualInputDevice mVirtualKeyboard;
    @Mock
    private IVirtualInputDevice mVirtualTouchscreen;
    @Captor
    private ArgumentCaptor<VirtualDeviceParams> mVirtualDeviceParamsArgumentCaptor;
    @Captor
    private ArgumentCaptor<VirtualDisplayConfig> mVirtualDisplayConfigArgumentCaptor;
    @Captor
    private ArgumentCaptor<VirtualTouchscreenConfig> mVirtualTouchscreenConfigArgumentCaptor;
    @Captor
    private ArgumentCaptor<VirtualDpadConfig> mVirtualDpadConfigArgumentCaptor;
    @Captor
    private ArgumentCaptor<VirtualKeyboardConfig> mVirtualKeyboardConfigArgumentCaptor;

    private AutoCloseable mMockitoSession;
    private final IBinder mAppToken = new Binder();
    private final ComputerControlSessionParams mDefaultParams =
            new ComputerControlSessionParams.Builder()
                    .setName(ComputerControlSessionTest.class.getSimpleName())
                    .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                    .build();
    private ComputerControlSessionImpl mSession;

    @Before
    public void setUp() throws Exception {
        mMockitoSession = MockitoAnnotations.openMocks(this);

        when(mInjector.getMainDisplayIdForUser(anyInt())).thenReturn(MAIN_DISPLAY_ID);

        DisplayInfo displayInfo = new DisplayInfo();
        displayInfo.logicalWidth = DISPLAY_WIDTH;
        displayInfo.logicalHeight = DISPLAY_HEIGHT;
        displayInfo.logicalDensityDpi = DISPLAY_DPI;
        when(mInjector.getDisplayInfo(MAIN_DISPLAY_ID)).thenReturn(displayInfo);
        when(mInjector.getDisplayInfo(VIRTUAL_DISPLAY_ID)).thenReturn(displayInfo);

        when(mInjector.getPermissionControllerPackageName())
                .thenReturn(PERMISSION_CONTROLLER_PACKAGE);
        when(mVirtualDeviceFactory.createVirtualDevice(any(), any(), any(), any()))
                .thenReturn(mVirtualDevice);
        when(mVirtualDevice.createVirtualDisplay(any(), any())).thenReturn(VIRTUAL_DISPLAY_ID);
        when(mVirtualDevice.createVirtualTouchscreen(any(), any())).thenReturn(mVirtualTouchscreen);
        when(mVirtualDevice.createVirtualKeyboard(any(), any())).thenReturn(mVirtualKeyboard);
        when(mVirtualDevice.createVirtualDpad(any(), any())).thenReturn(mVirtualDpad);
        when(mInjector.getLongPressTimeoutMillis()).thenReturn(
                LONG_PRESS_STEP_COUNT * TOUCH_EVENT_DELAY_MS);
    }

    @After
    public void tearDown() throws Exception {
        mMockitoSession.close();
    }

    @Test
    public void createSessionWithoutDisplaySurface_appliesCorrectParams() throws Exception {
        createComputerControlSession(mDefaultParams);

        verify(mVirtualDeviceFactory).createVirtualDevice(
                eq(mAppToken), any(), mVirtualDeviceParamsArgumentCaptor.capture(), any());
        assertThat(mVirtualDeviceParamsArgumentCaptor.getValue().getName())
                .isEqualTo(mDefaultParams.getName());
        assertThat(mVirtualDeviceParamsArgumentCaptor.getValue()
                .getDevicePolicy(POLICY_TYPE_RECENTS))
                .isEqualTo(DEVICE_POLICY_DEFAULT);

        verify(mVirtualDevice).createVirtualDisplay(
                mVirtualDisplayConfigArgumentCaptor.capture(), any());
        VirtualDisplayConfig virtualDisplayConfig = mVirtualDisplayConfigArgumentCaptor.getValue();
        assertThat(virtualDisplayConfig.getName()).contains(mDefaultParams.getName());

        assertThat(virtualDisplayConfig.getDensityDpi()).isEqualTo(DISPLAY_DPI);
        assertThat(virtualDisplayConfig.getHeight()).isEqualTo(DISPLAY_HEIGHT);
        assertThat(virtualDisplayConfig.getWidth()).isEqualTo(DISPLAY_WIDTH);
        assertThat(virtualDisplayConfig.getSurface()).isNull();

        int expectedDisplayFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_STEAL_TOP_FOCUS_DISABLED
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED;
        assertThat(virtualDisplayConfig.getFlags()).isEqualTo(expectedDisplayFlags);

        verify(mVirtualDevice).setDisplayImePolicy(
                VIRTUAL_DISPLAY_ID, WindowManager.DISPLAY_IME_POLICY_HIDE);

        verify(mVirtualDevice).createVirtualDpad(
                mVirtualDpadConfigArgumentCaptor.capture(), any());
        VirtualDpadConfig virtualDpadConfig = mVirtualDpadConfigArgumentCaptor.getValue();
        assertThat(virtualDpadConfig.getAssociatedDisplayId()).isEqualTo(VIRTUAL_DISPLAY_ID);
        assertThat(virtualDpadConfig.getInputDeviceName()).contains(mDefaultParams.getName());
        assertThat(virtualDpadConfig.getProductId()).isEqualTo(PRODUCT_ID_DPAD);

        verify(mVirtualDevice).createVirtualKeyboard(
                mVirtualKeyboardConfigArgumentCaptor.capture(), any());
        VirtualKeyboardConfig virtualKeyboardConfig =
                mVirtualKeyboardConfigArgumentCaptor.getValue();
        assertThat(virtualKeyboardConfig.getAssociatedDisplayId()).isEqualTo(VIRTUAL_DISPLAY_ID);
        assertThat(virtualKeyboardConfig.getInputDeviceName()).contains(mDefaultParams.getName());
        assertThat(virtualKeyboardConfig.getProductId()).isEqualTo(PRODUCT_ID_KEYBOARD);

        verify(mVirtualDevice).createVirtualTouchscreen(
                mVirtualTouchscreenConfigArgumentCaptor.capture(), any());
        VirtualTouchscreenConfig virtualTouchscreenConfig =
                mVirtualTouchscreenConfigArgumentCaptor.getValue();
        assertThat(virtualTouchscreenConfig.getAssociatedDisplayId()).isEqualTo(VIRTUAL_DISPLAY_ID);
        assertThat(virtualTouchscreenConfig.getWidth()).isEqualTo(DISPLAY_WIDTH);
        assertThat(virtualTouchscreenConfig.getHeight()).isEqualTo(DISPLAY_HEIGHT);
        assertThat(virtualTouchscreenConfig.getInputDeviceName()).contains(
                mDefaultParams.getName());
        assertThat(virtualTouchscreenConfig.getProductId()).isEqualTo(PRODUCT_ID_TOUCHSCREEN);
    }

    @Test
    @DisableFlags(Flags.FLAG_COMPUTER_CONTROL_ACTIVITY_POLICY_STRICT)
    public void createSession_noActivityPolicy() throws Exception {
        createComputerControlSession(mDefaultParams);
        verify(mVirtualDevice, never()).setDevicePolicy(eq(POLICY_TYPE_ACTIVITY), anyInt());

        verify(mVirtualDevice).addActivityPolicyExemption(
                argThat(new MatchesActivityPolicyExcemption(PERMISSION_CONTROLLER_PACKAGE)));
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_ACTIVITY_POLICY_STRICT)
    public void createSession_strictActivityPolicy() throws Exception {
        createComputerControlSession(mDefaultParams);

        verify(mVirtualDevice).setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM);

        for (String expected : TARGET_PACKAGE_NAMES) {
            verify(mVirtualDevice).addActivityPolicyExemption(
                    argThat(new MatchesActivityPolicyExcemption(expected)));
        }
    }

    @Test
    public void closeSession_closesVirtualDevice() throws Exception {
        createComputerControlSession(mDefaultParams);
        mSession.close();
        verify(mVirtualDevice).close();
        verify(mOnClosedListener).onClosed(mSession);
    }

    @Test
    public void getVirtualDisplayId_returnsCreatedDisplay() {
        createComputerControlSession(mDefaultParams);
        assertThat(mSession.getVirtualDisplayId()).isEqualTo(VIRTUAL_DISPLAY_ID);
    }

    @Test
    public void createSession_disablesAnimationsOnDisplay() {
        createComputerControlSession(mDefaultParams);
        verify(mInjector).disableAnimationsForDisplay(VIRTUAL_DISPLAY_ID);
    }

    @Test
    public void launchApplication_launchesApplication() throws RemoteException {
        createComputerControlSession(mDefaultParams);
        mSession.launchApplication(TARGET_PACKAGE_1);
        verify(mInjector).launchApplicationOnDisplayAsUser(
                eq(TARGET_PACKAGE_1), eq(VIRTUAL_DISPLAY_ID), any());
    }

    @Test
    @DisableFlags(Flags.FLAG_COMPUTER_CONTROL_ACTIVITY_POLICY_STRICT)
    public void launchApplication_noActivityPolicy_launchesApplication() throws RemoteException {
        createComputerControlSession(mDefaultParams);
        mSession.launchApplication(UNDECLARED_TARGET_PACKAGE);
        verify(mInjector).launchApplicationOnDisplayAsUser(
                eq(UNDECLARED_TARGET_PACKAGE), eq(VIRTUAL_DISPLAY_ID), any());
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_ACTIVITY_POLICY_STRICT)
    public void launchApplication_strictActivityPolicy_addsExemption() throws RemoteException {
        createComputerControlSession(mDefaultParams);
        mSession.launchApplication(UNDECLARED_TARGET_PACKAGE);
        verify(mVirtualDevice).addActivityPolicyExemption(
                argThat(new MatchesActivityPolicyExcemption(UNDECLARED_TARGET_PACKAGE)));
        verify(mInjector).launchApplicationOnDisplayAsUser(
                eq(UNDECLARED_TARGET_PACKAGE), eq(VIRTUAL_DISPLAY_ID), any());
    }

    @Test
    public void tap_sendsTouchscreenEvents() throws Exception {
        createComputerControlSession(mDefaultParams);
        mSession.tap(60, 200);
        verify(mVirtualTouchscreen).sendTouchEvent(argThat(
                new MatchesTouchEvent(60, 200, VirtualTouchEvent.ACTION_DOWN)));
        verify(mVirtualTouchscreen).sendTouchEvent(argThat(
                new MatchesTouchEvent(60, 200, VirtualTouchEvent.ACTION_UP)));
    }

    @Test
    public void swipe_sendsTouchscreenEvents() throws Exception {
        createComputerControlSession(mDefaultParams);
        mSession.swipe(60, 200, 180, 400);
        verify(mVirtualTouchscreen).sendTouchEvent(argThat(
                new MatchesTouchEvent(60, 200, VirtualTouchEvent.ACTION_DOWN)));
        verify(mVirtualTouchscreen).sendTouchEvent(argThat(
                new MatchesTouchEvent(60, 200, VirtualTouchEvent.ACTION_MOVE)));
        verify(mVirtualTouchscreen,
                timeout(TOUCH_EVENT_DELAY_MS * (SWIPE_STEPS + 1)).times(SWIPE_STEPS))
                .sendTouchEvent(argThat(new MatchesTouchEvent(VirtualTouchEvent.ACTION_MOVE)));
        verify(mVirtualTouchscreen, timeout(TOUCH_EVENT_DELAY_MS))
                .sendTouchEvent(argThat(
                        new MatchesTouchEvent(180, 400, VirtualTouchEvent.ACTION_MOVE)));
        verify(mVirtualTouchscreen).sendTouchEvent(argThat(
                new MatchesTouchEvent(180, 400, VirtualTouchEvent.ACTION_UP)));
    }

    @Test
    public void longPress_sendsTouchscreenEvents() throws Exception {
        when(mInjector.getLongPressTimeoutMillis()).thenReturn(
                LONG_PRESS_STEP_COUNT * TOUCH_EVENT_DELAY_MS);
        createComputerControlSession(mDefaultParams);

        mSession.longPress(100, 200);
        verify(mVirtualTouchscreen).sendTouchEvent(argThat(
                new MatchesTouchEvent(100, 200, VirtualTouchEvent.ACTION_DOWN)));
        verify(mVirtualTouchscreen, timeout(TOUCH_EVENT_DELAY_MS * (LONG_PRESS_STEP_COUNT + 1))
                .times(LONG_PRESS_STEP_COUNT + 1))
                .sendTouchEvent(
                        argThat(new MatchesTouchEvent(100, 200, VirtualTouchEvent.ACTION_MOVE)));
        verify(mVirtualTouchscreen).sendTouchEvent(argThat(
                new MatchesTouchEvent(100, 200, VirtualTouchEvent.ACTION_UP)));
    }

    @Test
    public void newTouchGesture_cancelsOngoingGesture() throws Exception {
        createComputerControlSession(mDefaultParams);

        mSession.swipe(100, 200, 300, 400);

        mSession.swipe(400, 300, 200, 100);
        verify(mVirtualTouchscreen, times(1)).sendTouchEvent(
                argThat(new MatchesTouchEvent(VirtualTouchEvent.ACTION_CANCEL)));

        mSession.longPress(100, 200);
        verify(mVirtualTouchscreen, times(2)).sendTouchEvent(
                argThat(new MatchesTouchEvent(VirtualTouchEvent.ACTION_CANCEL)));

        mSession.longPress(300, 400);
        verify(mVirtualTouchscreen, times(3)).sendTouchEvent(
                argThat(new MatchesTouchEvent(VirtualTouchEvent.ACTION_CANCEL)));

        mSession.tap(500, 600);
        verify(mVirtualTouchscreen, times(4)).sendTouchEvent(
                argThat(new MatchesTouchEvent(VirtualTouchEvent.ACTION_CANCEL)));
    }

    @Test
    @DisableFlags(Flags.FLAG_COMPUTER_CONTROL_TYPING)
    public void insertText_sendsCharacterKeysToVirtualKeyboard() throws RemoteException {
        createComputerControlSession(mDefaultParams);

        mSession.insertText("t", false /* replaceExisting */, false /* commit */);
        verify(mVirtualKeyboard, timeout(10 * KEY_EVENT_DELAY_MS)).sendKeyEvent(
                argThat(new MatchesVirtualKeyEvent(KeyEvent.KEYCODE_T,
                        VirtualKeyEvent.ACTION_DOWN)));
        verify(mVirtualKeyboard, timeout(10 * KEY_EVENT_DELAY_MS)).sendKeyEvent(
                argThat(new MatchesVirtualKeyEvent(KeyEvent.KEYCODE_T,
                        VirtualKeyEvent.ACTION_UP)));
    }

    @Test
    @DisableFlags(Flags.FLAG_COMPUTER_CONTROL_TYPING)
    public void insertTextWithReplaceExisting_sendsDeleteTextSequenceToVirtualKeyboard()
            throws RemoteException {
        createComputerControlSession(mDefaultParams);

        mSession.insertText("text", true /* replaceExisting */, false /* commit */);
        verify(mVirtualKeyboard, timeout(10 * KEY_EVENT_DELAY_MS)).sendKeyEvent(
                argThat(new MatchesVirtualKeyEvent(KeyEvent.KEYCODE_CTRL_LEFT,
                        VirtualKeyEvent.ACTION_DOWN)));
        verify(mVirtualKeyboard, timeout(10 * KEY_EVENT_DELAY_MS)).sendKeyEvent(
                argThat(new MatchesVirtualKeyEvent(KeyEvent.KEYCODE_A,
                        VirtualKeyEvent.ACTION_DOWN)));
        verify(mVirtualKeyboard, timeout(10 * KEY_EVENT_DELAY_MS)).sendKeyEvent(
                argThat(new MatchesVirtualKeyEvent(KeyEvent.KEYCODE_A,
                        VirtualKeyEvent.ACTION_UP)));
        verify(mVirtualKeyboard, timeout(10 * KEY_EVENT_DELAY_MS)).sendKeyEvent(
                argThat(new MatchesVirtualKeyEvent(KeyEvent.KEYCODE_CTRL_LEFT,
                        VirtualKeyEvent.ACTION_UP)));
        verify(mVirtualKeyboard, timeout(10 * KEY_EVENT_DELAY_MS)).sendKeyEvent(
                argThat(new MatchesVirtualKeyEvent(KeyEvent.KEYCODE_DEL,
                        VirtualKeyEvent.ACTION_DOWN)));
        verify(mVirtualKeyboard, timeout(10 * KEY_EVENT_DELAY_MS)).sendKeyEvent(
                argThat(new MatchesVirtualKeyEvent(KeyEvent.KEYCODE_DEL,
                        VirtualKeyEvent.ACTION_UP)));
    }

    @Test
    @DisableFlags(Flags.FLAG_COMPUTER_CONTROL_TYPING)
    public void insertTextWithCommit_sendsEnterKeyToVirtualKeyboard() throws RemoteException {
        createComputerControlSession(mDefaultParams);

        mSession.insertText("", false /* replaceExisting */, true /* commit */);
        verify(mVirtualKeyboard, timeout(10 * KEY_EVENT_DELAY_MS)).sendKeyEvent(
                argThat(new MatchesVirtualKeyEvent(KeyEvent.KEYCODE_ENTER,
                        VirtualKeyEvent.ACTION_DOWN)));
        verify(mVirtualKeyboard, timeout(10 * KEY_EVENT_DELAY_MS)).sendKeyEvent(
                argThat(new MatchesVirtualKeyEvent(KeyEvent.KEYCODE_ENTER,
                        VirtualKeyEvent.ACTION_UP)));
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_TYPING)
    public void insertText_callsCommitTextOnAvailableInputConnection() throws RemoteException {
        createComputerControlSession(mDefaultParams);
        when(mInjector.getInputConnection(VIRTUAL_DISPLAY_ID)).thenReturn(
                mRemoteComputerControlInputConnection);
        mSession.insertText("text", false /* replaceExisting */, false /* commit */);
        verify(mRemoteComputerControlInputConnection).commitText(any(), eq("text"), eq(1));
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_TYPING)
    public void insertTextWithReplaceExisting_callsReplaceTextOnAvailableInputConnection()
            throws RemoteException {
        createComputerControlSession(mDefaultParams);
        when(mInjector.getInputConnection(VIRTUAL_DISPLAY_ID)).thenReturn(
                mRemoteComputerControlInputConnection);
        mSession.insertText("text", true /* replaceExisting */, false /* commit */);
        verify(mRemoteComputerControlInputConnection).replaceText(any(), eq(0),
                eq(Integer.MAX_VALUE), eq("text"), eq(1));
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_TYPING)
    public void insertTextWithCommit_sendEnterKeyOnAvailableInputConnection()
            throws RemoteException {
        createComputerControlSession(mDefaultParams);
        when(mInjector.getInputConnection(VIRTUAL_DISPLAY_ID)).thenReturn(
                mRemoteComputerControlInputConnection);

        mSession.insertText("text", false /* replaceExisting */, true /* commit */);
        verify(mRemoteComputerControlInputConnection).commitText(any(), eq("text"), eq(1));
        verify(mRemoteComputerControlInputConnection).sendKeyEvent(any(),
                argThat(new MatchesKeyEvent(KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_DOWN)));
        verify(mRemoteComputerControlInputConnection).sendKeyEvent(any(),
                argThat(new MatchesKeyEvent(KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_UP)));
    }

    @Test
    public void performActionBack_injectsBackKey()
            throws RemoteException {
        createComputerControlSession(mDefaultParams);

        mSession.performAction(ComputerControlSession.ACTION_GO_BACK);
        verify(mVirtualDpad).sendKeyEvent(argThat(
                new MatchesVirtualKeyEvent(KeyEvent.KEYCODE_BACK, VirtualKeyEvent.ACTION_DOWN)));
        verify(mVirtualDpad).sendKeyEvent(argThat(
                new MatchesVirtualKeyEvent(KeyEvent.KEYCODE_BACK, VirtualKeyEvent.ACTION_UP)));
    }

    @Test
    public void tap_notifiesStabilityListener() throws Exception {
        createComputerControlSession(mDefaultParams);
        mSession.setStabilityListener(mStabilityListener);

        mSession.tap(60, 200);

        verify(mStabilityListener, timeout(STABILITY_TIMEOUT_MS)).onSessionStable();
    }

    @Test
    public void longPress_notifiesStabilityListener() throws Exception {
        createComputerControlSession(mDefaultParams);
        mSession.setStabilityListener(mStabilityListener);

        mSession.longPress(100, 200);

        verify(mStabilityListener, timeout(STABILITY_TIMEOUT_MS)).onSessionStable();
    }

    @Test
    public void performAction_notifiesStabilityListener() throws Exception {
        createComputerControlSession(mDefaultParams);
        mSession.setStabilityListener(mStabilityListener);

        mSession.performAction(ComputerControlSession.ACTION_GO_BACK);

        verify(mStabilityListener, timeout(STABILITY_TIMEOUT_MS)).onSessionStable();
    }

    @Test
    public void performAction_withInvalidCode_notifiesStabilityListener() throws Exception {
        createComputerControlSession(mDefaultParams);
        mSession.setStabilityListener(mStabilityListener);

        mSession.performAction(-1);

        verify(mStabilityListener).onSessionStable();
    }

    @Test
    @DisableFlags(Flags.FLAG_COMPUTER_CONTROL_TYPING)
    public void insertTextLegacy_notifiesStabilityListener() throws Exception {
        createComputerControlSession(mDefaultParams);
        mSession.setStabilityListener(mStabilityListener);

        mSession.insertText("hello", false /* replaceExisting */, true /* commit */);

        verify(mStabilityListener, timeout(STABILITY_TIMEOUT_MS)).onSessionStable();
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_TYPING)
    public void insertText_notifiesStabilityListener() throws Exception {
        createComputerControlSession(mDefaultParams);
        when(mInjector.getInputConnection(VIRTUAL_DISPLAY_ID)).thenReturn(
                mRemoteComputerControlInputConnection);
        mSession.setStabilityListener(mStabilityListener);

        mSession.insertText("hello", false /* replaceExisting */, true /* commit */);

        verify(mStabilityListener, timeout(STABILITY_TIMEOUT_MS)).onSessionStable();
    }

    @Test
    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_TYPING)
    public void insertText_withNoInputConnection_notifiesStabilityListener() throws Exception {
        createComputerControlSession(mDefaultParams);
        mSession.setStabilityListener(mStabilityListener);

        mSession.insertText("hello", false /* replaceExisting */, true /* commit */);

        verify(mStabilityListener).onSessionStable();
    }

    @Test
    public void swipe_notifiesStabilityListener() throws Exception {
        createComputerControlSession(mDefaultParams);
        mSession.setStabilityListener(mStabilityListener);

        mSession.swipe(60, 200, 180, 400);

        verify(mStabilityListener, timeout(STABILITY_TIMEOUT_MS)).onSessionStable();
    }

    @Test
    public void launchApplication_notifiesStabilityListener() throws Exception {
        createComputerControlSession(mDefaultParams);
        mSession.setStabilityListener(mStabilityListener);

        mSession.launchApplication(TARGET_PACKAGE_1);

        verify(mStabilityListener, timeout(STABILITY_TIMEOUT_MS)).onSessionStable();
    }

    @Test
    public void setStabilityListener_withStabilityListenerAlreadySet_throwsException() {
        createComputerControlSession(mDefaultParams);
        mSession.setStabilityListener(mStabilityListener);

        assertThrows(IllegalStateException.class,
                () -> mSession.setStabilityListener(mStabilityListener));
    }

    private void createComputerControlSession(ComputerControlSessionParams params) {
        mSession = new ComputerControlSessionImpl(mAppToken, params,
                AttributionSource.myAttributionSource(), mVirtualDeviceFactory, mOnClosedListener,
                mInjector);
    }

    private static class MatchesActivityPolicyExcemption implements
            ArgumentMatcher<ActivityPolicyExemption> {

        private final String mPackageName;

        MatchesActivityPolicyExcemption(String packageName) {
            mPackageName = packageName;
        }

        @Override
        public boolean matches(ActivityPolicyExemption argument) {
            return mPackageName.equals(argument.getPackageName());
        }
    }

    private static class MatchesTouchEvent implements ArgumentMatcher<VirtualTouchEvent> {

        private final int mX;
        private final int mY;
        private final int mAction;
        private final int mToolType;

        MatchesTouchEvent(int action) {
            this(-1, -1, action);
        }

        MatchesTouchEvent(int x, int y, int action) {
            mX = x;
            mY = y;
            mAction = action;
            mToolType =
                    mAction == VirtualTouchEvent.ACTION_CANCEL ? VirtualTouchEvent.TOOL_TYPE_PALM
                            : VirtualTouchEvent.TOOL_TYPE_FINGER;
        }

        @Override
        public boolean matches(VirtualTouchEvent event) {
            if (event.getMajorAxisSize() != 1
                    || event.getPointerId() != 4
                    || event.getPressure() != 255
                    || event.getAction() != mAction
                    || event.getToolType() != mToolType) {
                return false;
            }
            if (mX == -1 || mY == -1) {
                return true;
            }
            return mX == event.getX() && mY == event.getY();
        }
    }

    private static class MatchesVirtualKeyEvent implements ArgumentMatcher<VirtualKeyEvent> {

        private final int mKeyCode;
        private final int mAction;

        MatchesVirtualKeyEvent(int keyCode, int action) {
            mKeyCode = keyCode;
            mAction = action;
        }

        @Override
        public boolean matches(VirtualKeyEvent event) {
            return event.getKeyCode() == mKeyCode && event.getAction() == mAction;
        }
    }

    private static class MatchesKeyEvent implements ArgumentMatcher<KeyEvent> {

        private final int mKeyCode;

        private final int mAction;

        MatchesKeyEvent(int keyCode, int action) {
            mKeyCode = keyCode;
            mAction = action;
        }

        @Override
        public boolean matches(KeyEvent event) {
            return mKeyCode == event.getKeyCode() && mAction == event.getAction();
        }
    }
}
