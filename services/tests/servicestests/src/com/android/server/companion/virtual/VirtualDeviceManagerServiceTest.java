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

package com.android.server.companion.virtual;

import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_DEFAULT;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_INVALID;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_ACTIVITY;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_RECENTS;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_SENSORS;
import static android.content.Context.DEVICE_ID_DEFAULT;
import static android.content.Context.DEVICE_ID_INVALID;
import static android.content.Intent.ACTION_VIEW;
import static android.content.pm.ActivityInfo.FLAG_CAN_DISPLAY_ON_REMOTE_DEVICES;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.app.WindowConfiguration;
import android.app.admin.DevicePolicyManager;
import android.companion.AssociationInfo;
import android.companion.AssociationRequest;
import android.companion.virtual.IVirtualDeviceActivityListener;
import android.companion.virtual.IVirtualDeviceIntentInterceptor;
import android.companion.virtual.IVirtualDeviceSoundEffectListener;
import android.companion.virtual.ViewConfigurationParams;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.audio.IAudioConfigChangedCallback;
import android.companion.virtual.audio.IAudioRoutingCallback;
import android.companion.virtual.sensor.VirtualSensor;
import android.companion.virtual.sensor.VirtualSensorCallback;
import android.companion.virtual.sensor.VirtualSensorConfig;
import android.companion.virtualdevice.flags.Flags;
import android.content.AttributionSource;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerGlobal;
import android.hardware.display.DisplayManagerInternal;
import android.hardware.display.IDisplayManager;
import android.hardware.display.IVirtualDisplayCallback;
import android.hardware.display.VirtualDisplayConfig;
import android.hardware.input.IVirtualInputDevice;
import android.hardware.input.VirtualDpadConfig;
import android.hardware.input.VirtualKeyboardConfig;
import android.hardware.input.VirtualMouseConfig;
import android.hardware.input.VirtualNavigationTouchpadConfig;
import android.hardware.input.VirtualTouchscreenConfig;
import android.media.AudioManager;
import android.net.MacAddress;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.IPowerManager;
import android.os.IThermalService;
import android.os.LocaleList;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.WorkSource;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.util.ArraySet;
import android.util.Pair;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.WindowManager;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.app.BlockedAppStreamingActivity;
import com.android.internal.os.BackgroundThread;
import com.android.server.LocalServices;
import com.android.server.UiModeManagerInternal;
import com.android.server.companion.virtual.camera.VirtualCameraController;
import com.android.server.input.InputManagerInternal;
import com.android.server.sensors.SensorManagerInternal;

import com.google.android.collect.Sets;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@Presubmit
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class VirtualDeviceManagerServiceTest {

    private static final String NONBLOCKED_APP_PACKAGE_NAME = "com.nonblocked.app";
    private static final String BLOCKED_APP_PACKAGE_NAME = "com.blocked.app";
    private static final String VIRTUAL_DEVICE_OWNER_PACKAGE = "com.android.virtualdevice.test";
    private static final String DEVICE_NAME_1 = "device name 1";
    private static final String DEVICE_NAME_2 = "device name 2";
    private static final String DEVICE_NAME_3 = "device name 3";
    private static final int DISPLAY_ID_1 = 2;
    private static final int DISPLAY_ID_2 = 3;
    private static final int NON_EXISTENT_DISPLAY_ID = 42;
    private static final int DEVICE_OWNER_UID_1 = Process.myUid();
    private static final int DEVICE_OWNER_UID_2 = DEVICE_OWNER_UID_1 + 1;
    private static final int UID_1 = 0;
    private static final int UID_2 = 10;
    private static final String PACKAGE_1 = "com.foo";
    private static final String PACKAGE_2 = "com.bar";
    private static final int PRODUCT_ID = 10;
    private static final int VENDOR_ID = 5;
    private static final String UNIQUE_ID = "uniqueid";
    private static final int INPUT_DEVICE_ID = 53;
    private static final int HEIGHT = 1800;
    private static final int WIDTH = 900;
    private static final int SENSOR_HANDLE = 64;
    private static final Binder BINDER = new Binder("binder");
    private static final int FLAG_CANNOT_DISPLAY_ON_REMOTE_DEVICES = 0x00000;
    private static final int VIRTUAL_DEVICE_ID_1 = 42;
    private static final int VIRTUAL_DEVICE_ID_2 = 43;

    private static final VirtualDpadConfig DPAD_CONFIG =
            new VirtualDpadConfig.Builder()
                    .setVendorId(VENDOR_ID)
                    .setProductId(PRODUCT_ID)
                    .setInputDeviceName(DEVICE_NAME_1)
                    .setAssociatedDisplayId(DISPLAY_ID_1)
                    .build();
    private static final VirtualKeyboardConfig KEYBOARD_CONFIG =
            new VirtualKeyboardConfig.Builder()
                    .setVendorId(VENDOR_ID)
                    .setProductId(PRODUCT_ID)
                    .setInputDeviceName(DEVICE_NAME_1)
                    .setAssociatedDisplayId(DISPLAY_ID_1)
                    .setLanguageTag(VirtualKeyboardConfig.DEFAULT_LANGUAGE_TAG)
                    .setLayoutType(VirtualKeyboardConfig.DEFAULT_LAYOUT_TYPE)
                    .build();
    private static final VirtualMouseConfig MOUSE_CONFIG =
            new VirtualMouseConfig.Builder()
                    .setVendorId(VENDOR_ID)
                    .setProductId(PRODUCT_ID)
                    .setInputDeviceName(DEVICE_NAME_1)
                    .setAssociatedDisplayId(DISPLAY_ID_1)
                    .build();
    private static final VirtualTouchscreenConfig TOUCHSCREEN_CONFIG =
            new VirtualTouchscreenConfig.Builder(WIDTH, HEIGHT)
                    .setVendorId(VENDOR_ID)
                    .setProductId(PRODUCT_ID)
                    .setInputDeviceName(DEVICE_NAME_1)
                    .setAssociatedDisplayId(DISPLAY_ID_1)
                    .build();
    private static final VirtualNavigationTouchpadConfig NAVIGATION_TOUCHPAD_CONFIG =
            new VirtualNavigationTouchpadConfig.Builder(WIDTH, HEIGHT)
                    .setVendorId(VENDOR_ID)
                    .setProductId(PRODUCT_ID)
                    .setInputDeviceName(DEVICE_NAME_1)
                    .setAssociatedDisplayId(DISPLAY_ID_1)
                    .build();

    private static final Set<ComponentName> BLOCKED_ACTIVITIES = Set.of(
            new ComponentName(BLOCKED_APP_PACKAGE_NAME, BLOCKED_APP_PACKAGE_NAME));

    private static final String TEST_SITE = "http://test";

    @Rule
    public SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule
    public VirtualDeviceRule mVirtualDeviceRule = VirtualDeviceRule.createDefault();

    private Context mContext;
    private VirtualDeviceImpl mDeviceImpl;
    private InputController mInputController;
    private SensorController mSensorController;
    private CameraAccessController mCameraAccessController;
    private AssociationInfo mAssociationInfo;
    private VirtualDeviceManagerService mVdms;
    private VirtualDeviceManagerInternal mLocalService;
    private VirtualDeviceManagerService.VirtualDeviceManagerImpl mVdm;
    private VirtualDeviceManagerService.VirtualDeviceManagerNativeImpl mVdmNative;
    private VirtualDeviceLog mVirtualDeviceLog;
    @Mock
    private DisplayManagerInternal mDisplayManagerInternalMock;
    @Mock
    private IDisplayManager mIDisplayManager;
    @Mock
    private IVirtualInputDevice mIVirtualInputDevice;
    @Mock
    private WindowManager mWindowManager;
    @Mock
    private VirtualDeviceImpl.PendingTrampolineCallback mPendingTrampolineCallback;
    @Mock
    private DevicePolicyManager mDevicePolicyManagerMock;
    @Mock
    private InputManagerInternal mInputManagerInternalMock;
    @Mock
    private SensorManagerInternal mSensorManagerInternalMock;
    @Mock
    private UiModeManagerInternal mUiModeManagerInternalMock;
    @Mock
    private VirtualSensorCallback mSensorCallback;
    @Mock
    private IVirtualDeviceActivityListener mActivityListener;
    @Mock
    private IVirtualDeviceSoundEffectListener mSoundEffectListener;
    @Mock
    private IVirtualDisplayCallback mVirtualDisplayCallback;
    @Mock
    private VirtualDeviceManagerInternal.AppsOnVirtualDeviceListener mAppsOnVirtualDeviceListener;
    @Mock
    private Consumer<String> mPersistentDeviceIdRemovedListener;
    @Mock
    IPowerManager mIPowerManagerMock;
    @Mock
    IThermalService mIThermalServiceMock;
    @Mock
    private IAudioRoutingCallback mRoutingCallback;
    @Mock
    private IAudioConfigChangedCallback mConfigChangedCallback;
    @Mock
    private CameraAccessController.CameraAccessBlockedCallback mCameraAccessBlockedCallback;
    @Mock
    private ApplicationInfo mApplicationInfoMock;
    @Mock
    private ViewConfigurationController mViewConfigurationControllerMock;

    private Intent createRestrictedActivityBlockedIntent(Set<String> displayCategories,
            String targetDisplayCategory) {
        when(mDisplayManagerInternalMock.createVirtualDisplay(any(), any(), any(), any(),
                eq(VIRTUAL_DEVICE_OWNER_PACKAGE), eq(DEVICE_OWNER_UID_1)))
                .thenAnswer(inv -> {
                    mLocalService.onVirtualDisplayCreated(
                            mDeviceImpl, DISPLAY_ID_1, inv.getArgument(1), inv.getArgument(3));
                    return DISPLAY_ID_1;
                });
        VirtualDisplayConfig config = new VirtualDisplayConfig.Builder("display", 640, 480,
                420).setDisplayCategories(displayCategories).build();
        mDeviceImpl.createVirtualDisplay(config, mVirtualDisplayCallback);
        GenericWindowPolicyController gwpc = mDeviceImpl.getDisplayWindowPolicyControllerForTest(
                DISPLAY_ID_1);
        doNothing().when(mContext).startActivityAsUser(any(), any(), any());

        ActivityInfo activityInfo = getActivityInfo(
                NONBLOCKED_APP_PACKAGE_NAME,
                NONBLOCKED_APP_PACKAGE_NAME,
                /* displayOnRemoteDevices= */ true,
                targetDisplayCategory);
        Intent blockedAppIntent = BlockedAppStreamingActivity.createIntent(
                activityInfo, mAssociationInfo.getDisplayName());
        gwpc.canActivityBeLaunched(activityInfo, blockedAppIntent,
                WindowConfiguration.WINDOWING_MODE_FULLSCREEN, DISPLAY_ID_1, /* isNewTask= */ false,
                /* isResultExpected = */ false, /* intentSender= */ null);
        return blockedAppIntent;
    }

    private ActivityInfo getActivityInfo(
            String packageName, String name, boolean displayOnRemoteDevices,
            String requiredDisplayCategory) {
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = packageName;
        activityInfo.name = name;
        activityInfo.flags = displayOnRemoteDevices
                ? FLAG_CAN_DISPLAY_ON_REMOTE_DEVICES : FLAG_CANNOT_DISPLAY_ON_REMOTE_DEVICES;
        activityInfo.applicationInfo = mApplicationInfoMock;
        activityInfo.requiredDisplayCategory = requiredDisplayCategory;
        return activityInfo;
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mInputManagerInternalMock.createVirtualTouchscreen(any(), any()))
                .thenReturn(mIVirtualInputDevice);
        when(mInputManagerInternalMock.createVirtualKeyboard(any(), any()))
                .thenReturn(mIVirtualInputDevice);
        doNothing().when(mInputManagerInternalMock).setPointerIconVisible(anyBoolean(), anyInt());
        LocalServices.removeServiceForTest(InputManagerInternal.class);
        LocalServices.addService(InputManagerInternal.class, mInputManagerInternalMock);

        LocalServices.removeServiceForTest(SensorManagerInternal.class);
        LocalServices.addService(SensorManagerInternal.class, mSensorManagerInternalMock);

        LocalServices.removeServiceForTest(UiModeManagerInternal.class);
        LocalServices.addService(UiModeManagerInternal.class, mUiModeManagerInternalMock);

        final DisplayInfo displayInfo = new DisplayInfo();
        displayInfo.uniqueId = UNIQUE_ID;
        doReturn(displayInfo).when(mDisplayManagerInternalMock).getDisplayInfo(anyInt());
        doReturn(Display.INVALID_DISPLAY).when(mDisplayManagerInternalMock)
                .getDisplayIdToMirror(anyInt());
        LocalServices.removeServiceForTest(DisplayManagerInternal.class);
        LocalServices.addService(DisplayManagerInternal.class, mDisplayManagerInternalMock);

        mContext = Mockito.spy(new ContextWrapper(
                InstrumentationRegistry.getInstrumentation().getTargetContext()));
        doReturn(mContext).when(mContext).createContextAsUser(any(), anyInt());
        doNothing().when(mContext).sendBroadcastAsUser(any(), any());
        when(mContext.getSystemService(Context.DEVICE_POLICY_SERVICE)).thenReturn(
                mDevicePolicyManagerMock);
        when(mContext.getSystemService(Context.WINDOW_SERVICE)).thenReturn(mWindowManager);

        PowerManager powerManager = new PowerManager(mContext, mIPowerManagerMock,
                mIThermalServiceMock,
                new Handler(TestableLooper.get(this).getLooper()));
        when(mContext.getSystemService(Context.POWER_SERVICE)).thenReturn(powerManager);
        mInputController = new InputController(mContext, AttributionSource.myAttributionSource());
        mCameraAccessController =
                new CameraAccessController(mContext, mLocalService, mCameraAccessBlockedCallback);

        mAssociationInfo = createAssociationInfo(
                /* associationId= */ 1, AssociationRequest.DEVICE_PROFILE_APP_STREAMING);

        mVdms = new VirtualDeviceManagerService(mContext);
        mLocalService = mVdms.getLocalServiceInstance();
        mVdm = mVdms.new VirtualDeviceManagerImpl();
        mVdmNative = mVdms.new VirtualDeviceManagerNativeImpl();
        mVirtualDeviceLog = new VirtualDeviceLog(mContext);
        mDeviceImpl = createVirtualDevice(VIRTUAL_DEVICE_ID_1, DEVICE_OWNER_UID_1);
        mSensorController = mDeviceImpl.getSensorControllerForTest();
    }

    @After
    public void tearDown() {
        mDeviceImpl.close();
    }

    @Test
    public void getDeviceIdForDisplayId_invalidDisplayId_returnsDefault() {
        assertThat(mVdm.getDeviceIdForDisplayId(Display.INVALID_DISPLAY))
                .isEqualTo(DEVICE_ID_DEFAULT);
        assertThat(mLocalService.getDeviceIdForDisplayId(Display.INVALID_DISPLAY))
                .isEqualTo(DEVICE_ID_DEFAULT);
    }

    @Test
    public void getDeviceIdForDisplayId_defaultDisplayId_returnsDefault() {
        assertThat(mVdm.getDeviceIdForDisplayId(Display.DEFAULT_DISPLAY))
                .isEqualTo(DEVICE_ID_DEFAULT);
        assertThat(mLocalService.getDeviceIdForDisplayId(Display.DEFAULT_DISPLAY))
                .isEqualTo(DEVICE_ID_DEFAULT);
    }

    @Test
    public void getDeviceIdForDisplayId_nonExistentDisplayId_returnsDefault() {
        assertThat(mVdm.getDeviceIdForDisplayId(NON_EXISTENT_DISPLAY_ID))
                .isEqualTo(DEVICE_ID_DEFAULT);
        assertThat(mLocalService.getDeviceIdForDisplayId(NON_EXISTENT_DISPLAY_ID))
                .isEqualTo(DEVICE_ID_DEFAULT);
    }

    @Test
    public void getDeviceIdForDisplayId_withValidVirtualDisplayId_returnsDeviceId() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);

        assertThat(mVdm.getDeviceIdForDisplayId(DISPLAY_ID_1))
                .isEqualTo(mDeviceImpl.getDeviceId());
        assertThat(mLocalService.getDeviceIdForDisplayId(DISPLAY_ID_1))
                .isEqualTo(mDeviceImpl.getDeviceId());
    }

    @Test
    public void isDeviceIdValid_invalidDeviceId_returnsFalse() {
        assertThat(mVdm.isValidVirtualDeviceId(DEVICE_ID_INVALID)).isFalse();
        assertThat(mLocalService.isValidVirtualDeviceId(DEVICE_ID_INVALID)).isFalse();
    }

    @Test
    public void isDeviceIdValid_defaultDeviceId_returnsFalse() {
        assertThat(mVdm.isValidVirtualDeviceId(DEVICE_ID_DEFAULT)).isFalse();
        assertThat(mLocalService.isValidVirtualDeviceId(DEVICE_ID_DEFAULT)).isFalse();
    }

    @Test
    public void isDeviceIdValid_validVirtualDeviceId_returnsTrue() {
        assertThat(mVdm.isValidVirtualDeviceId(mDeviceImpl.getDeviceId())).isTrue();
        assertThat(mLocalService.isValidVirtualDeviceId(mDeviceImpl.getDeviceId())).isTrue();
    }

    @Test
    public void isDeviceIdValid_nonExistentDeviceId_returnsFalse() {
        assertThat(mVdm.isValidVirtualDeviceId(mDeviceImpl.getDeviceId() + 1)).isFalse();
        assertThat(mLocalService.isValidVirtualDeviceId(mDeviceImpl.getDeviceId() + 1)).isFalse();
    }

    @Test
    @DisableFlags(Flags.FLAG_HANDLE_INVALID_DEVICE_ID)
    public void getDevicePolicy_invalidDeviceId_returnsDefault() {
        assertThat(mVdm.getDevicePolicy(DEVICE_ID_INVALID, POLICY_TYPE_SENSORS))
                .isEqualTo(DEVICE_POLICY_DEFAULT);
        assertThat(mVdmNative.getDevicePolicy(DEVICE_ID_INVALID, POLICY_TYPE_SENSORS))
                .isEqualTo(DEVICE_POLICY_DEFAULT);
    }

    @Test
    @EnableFlags(Flags.FLAG_HANDLE_INVALID_DEVICE_ID)
    public void getDevicePolicy_invalidDeviceId_returnsInvalid() {
        assertThat(mVdm.getDevicePolicy(DEVICE_ID_INVALID, POLICY_TYPE_SENSORS))
                .isEqualTo(DEVICE_POLICY_INVALID);
        assertThat(mVdmNative.getDevicePolicy(DEVICE_ID_INVALID, POLICY_TYPE_SENSORS))
                .isEqualTo(DEVICE_POLICY_INVALID);
    }

    @Test
    public void getDevicePolicy_defaultDeviceId_returnsDefault() {
        assertThat(mVdm.getDevicePolicy(DEVICE_ID_DEFAULT, POLICY_TYPE_SENSORS))
                .isEqualTo(DEVICE_POLICY_DEFAULT);
        assertThat(mVdmNative.getDevicePolicy(DEVICE_ID_DEFAULT, POLICY_TYPE_SENSORS))
                .isEqualTo(DEVICE_POLICY_DEFAULT);
    }

    @Test
    @DisableFlags(Flags.FLAG_HANDLE_INVALID_DEVICE_ID)
    public void getDevicePolicy_nonExistentDeviceId_returnsDefault() {
        assertThat(mVdm.getDevicePolicy(mDeviceImpl.getDeviceId() + 1, POLICY_TYPE_SENSORS))
                .isEqualTo(DEVICE_POLICY_DEFAULT);
        assertThat(mVdmNative.getDevicePolicy(mDeviceImpl.getDeviceId() + 1, POLICY_TYPE_SENSORS))
                .isEqualTo(DEVICE_POLICY_DEFAULT);
    }

    @Test
    @EnableFlags(Flags.FLAG_HANDLE_INVALID_DEVICE_ID)
    public void getDevicePolicy_nonExistentDeviceId_returnsInvalid() {
        assertThat(mVdm.getDevicePolicy(mDeviceImpl.getDeviceId() + 1, POLICY_TYPE_SENSORS))
                .isEqualTo(DEVICE_POLICY_INVALID);
        assertThat(mVdmNative.getDevicePolicy(mDeviceImpl.getDeviceId() + 1, POLICY_TYPE_SENSORS))
                .isEqualTo(DEVICE_POLICY_INVALID);
    }

    @Test
    public void getDevicePolicy_unspecifiedPolicy_returnsDefault() {
        assertThat(mVdm.getDevicePolicy(mDeviceImpl.getDeviceId(), POLICY_TYPE_SENSORS))
                .isEqualTo(DEVICE_POLICY_DEFAULT);
        assertThat(mVdmNative.getDevicePolicy(mDeviceImpl.getDeviceId(), POLICY_TYPE_SENSORS))
                .isEqualTo(DEVICE_POLICY_DEFAULT);
    }

    @Test
    public void getDevicePolicy_returnsCustom() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_CUSTOM)
                .build();
        mDeviceImpl.close();
        mDeviceImpl = createVirtualDevice(VIRTUAL_DEVICE_ID_1, DEVICE_OWNER_UID_1, params);

        assertThat(mVdm.getDevicePolicy(mDeviceImpl.getDeviceId(), POLICY_TYPE_SENSORS))
                .isEqualTo(DEVICE_POLICY_CUSTOM);
        assertThat(mVdmNative.getDevicePolicy(mDeviceImpl.getDeviceId(), POLICY_TYPE_SENSORS))
                .isEqualTo(DEVICE_POLICY_CUSTOM);
    }

    @Test
    public void getDevicePolicy_defaultRecentsPolicy_gwpcCanShowRecentsOnHostDevice() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder().build();
        mDeviceImpl.close();
        mDeviceImpl = createVirtualDevice(VIRTUAL_DEVICE_ID_1, DEVICE_OWNER_UID_1, params);
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);

        GenericWindowPolicyController gwpc =
                mDeviceImpl.getDisplayWindowPolicyControllerForTest(DISPLAY_ID_1);
        assertThat(gwpc.canShowTasksInHostDeviceRecents()).isTrue();
    }

    @Test
    public void getDevicePolicy_customRecentsPolicy_gwpcCannotShowRecentsOnHostDevice() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_RECENTS, DEVICE_POLICY_CUSTOM)
                .build();
        mDeviceImpl.close();
        mDeviceImpl = createVirtualDevice(VIRTUAL_DEVICE_ID_1, DEVICE_OWNER_UID_1, params);
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1, Display.FLAG_TRUSTED);

        GenericWindowPolicyController gwpc =
                mDeviceImpl.getDisplayWindowPolicyControllerForTest(DISPLAY_ID_1);
        assertThat(gwpc.canShowTasksInHostDeviceRecents()).isFalse();
    }

    @Test
    public void getDevicePolicy_customRecentsPolicy_untrustedDisplaygwpcShowsRecentsOnHostDevice() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_RECENTS, DEVICE_POLICY_CUSTOM)
                .build();
        mDeviceImpl.close();
        mDeviceImpl = createVirtualDevice(VIRTUAL_DEVICE_ID_1, DEVICE_OWNER_UID_1, params);
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);

        GenericWindowPolicyController gwpc =
                mDeviceImpl.getDisplayWindowPolicyControllerForTest(DISPLAY_ID_1);
        assertThat(gwpc.canShowTasksInHostDeviceRecents()).isTrue();
    }

    @Test
    public void getDevicePolicyForDisplayId() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_RECENTS, DEVICE_POLICY_CUSTOM)
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM)
                .setDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_CUSTOM)
                .build();
        mDeviceImpl.close();
        mDeviceImpl = createVirtualDevice(VIRTUAL_DEVICE_ID_1, DEVICE_OWNER_UID_1, params);
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1, Display.FLAG_TRUSTED);

        assertThat(mVdm.getDevicePolicyForDisplayId(DISPLAY_ID_1, POLICY_TYPE_RECENTS))
                .isEqualTo(DEVICE_POLICY_CUSTOM);
        assertThat(mVdm.getDevicePolicyForDisplayId(DISPLAY_ID_1, POLICY_TYPE_ACTIVITY))
                .isEqualTo(DEVICE_POLICY_CUSTOM);
        assertThat(mVdm.getDevicePolicyForDisplayId(DISPLAY_ID_1, POLICY_TYPE_SENSORS))
                .isEqualTo(DEVICE_POLICY_CUSTOM);

        mDeviceImpl.setDevicePolicyForDisplay(
            DISPLAY_ID_1, POLICY_TYPE_RECENTS, DEVICE_POLICY_DEFAULT);
        mDeviceImpl.setDevicePolicyForDisplay(
            DISPLAY_ID_1, POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT);

        // Device-level policy is unchanged.
        assertThat(mVdm.getDevicePolicy(mDeviceImpl.getDeviceId(), POLICY_TYPE_RECENTS))
                .isEqualTo(DEVICE_POLICY_CUSTOM);
        assertThat(mVdm.getDevicePolicy(mDeviceImpl.getDeviceId(), POLICY_TYPE_ACTIVITY))
                .isEqualTo(DEVICE_POLICY_CUSTOM);

        // Display-level policy is changed.
        assertThat(mVdm.getDevicePolicyForDisplayId(DISPLAY_ID_1, POLICY_TYPE_RECENTS))
                .isEqualTo(DEVICE_POLICY_DEFAULT);
        assertThat(mVdm.getDevicePolicyForDisplayId(DISPLAY_ID_1, POLICY_TYPE_ACTIVITY))
                .isEqualTo(DEVICE_POLICY_DEFAULT);
        assertThat(mVdm.getDevicePolicyForDisplayId(DISPLAY_ID_1, POLICY_TYPE_SENSORS))
                .isEqualTo(DEVICE_POLICY_CUSTOM);
    }

    @Test
    public void getDevicePolicyForDisplayId_unownedDisplay() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_RECENTS, DEVICE_POLICY_CUSTOM)
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM)
                .setDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_CUSTOM)
                .build();
        mDeviceImpl.close();
        mDeviceImpl = createVirtualDevice(VIRTUAL_DEVICE_ID_1, DEVICE_OWNER_UID_1, params);
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1, Display.FLAG_TRUSTED);

        assertThat(mVdm.getDevicePolicyForDisplayId(Display.DEFAULT_DISPLAY, POLICY_TYPE_RECENTS))
                .isEqualTo(DEVICE_POLICY_DEFAULT);
        assertThat(mVdm.getDevicePolicyForDisplayId(Display.DEFAULT_DISPLAY, POLICY_TYPE_ACTIVITY))
                .isEqualTo(DEVICE_POLICY_DEFAULT);
        assertThat(mVdm.getDevicePolicyForDisplayId(Display.DEFAULT_DISPLAY, POLICY_TYPE_SENSORS))
                .isEqualTo(DEVICE_POLICY_DEFAULT);

        // Non-existent display.
        assertThat(mVdm.getDevicePolicyForDisplayId(DISPLAY_ID_2, POLICY_TYPE_RECENTS))
                .isEqualTo(DEVICE_POLICY_DEFAULT);
        assertThat(mVdm.getDevicePolicyForDisplayId(DISPLAY_ID_2, POLICY_TYPE_ACTIVITY))
                .isEqualTo(DEVICE_POLICY_DEFAULT);
        assertThat(mVdm.getDevicePolicyForDisplayId(DISPLAY_ID_2, POLICY_TYPE_SENSORS))
                .isEqualTo(DEVICE_POLICY_DEFAULT);
    }

    @Test
    public void deviceOwner_cannotMessWithAnotherDeviceTheyDoNotOwn() {
        VirtualDeviceImpl unownedDevice =
                createVirtualDevice(VIRTUAL_DEVICE_ID_2, DEVICE_OWNER_UID_2);

        // The arguments don't matter, the owner uid check is always the first statement.
        assertThrows(SecurityException.class, () -> unownedDevice.goToSleep());
        assertThrows(SecurityException.class, () -> unownedDevice.wakeUp());

        assertThrows(SecurityException.class,
                () -> unownedDevice.launchPendingIntent(0, null, null));
        assertThrows(SecurityException.class,
                () -> unownedDevice.registerIntentInterceptor(null, null));
        assertThrows(SecurityException.class,
                () -> unownedDevice.unregisterIntentInterceptor(null));

        assertThrows(SecurityException.class,
                () -> unownedDevice.addActivityPolicyExemption(null));
        assertThrows(SecurityException.class,
                () -> unownedDevice.removeActivityPolicyExemption(null));
        assertThrows(SecurityException.class, () -> unownedDevice.setDevicePolicy(0, 0));
        assertThrows(SecurityException.class,
                () -> unownedDevice.setDevicePolicyForDisplay(0, 0, 0));
        assertThrows(SecurityException.class, () -> unownedDevice.setDisplayImePolicy(0, 0));

        assertThrows(SecurityException.class, () -> unownedDevice.registerVirtualCamera(null));
        assertThrows(SecurityException.class, () -> unownedDevice.unregisterVirtualCamera(null));

        assertThrows(SecurityException.class,
                () -> unownedDevice.onAudioSessionStarting(0, null, null));
        assertThrows(SecurityException.class, () -> unownedDevice.onAudioSessionEnded());

        assertThrows(SecurityException.class, () -> unownedDevice.createVirtualDisplay(null, null));
        assertThrows(SecurityException.class, () -> unownedDevice.createVirtualDpad(null, null));
        assertThrows(SecurityException.class, () -> unownedDevice.createVirtualMouse(null, null));
        assertThrows(SecurityException.class,
                () -> unownedDevice.createVirtualTouchscreen(null, null));
        assertThrows(SecurityException.class,
                () -> unownedDevice.createVirtualNavigationTouchpad(null, null));
        assertThrows(SecurityException.class, () -> unownedDevice.createVirtualStylus(null, null));
        assertThrows(SecurityException.class,
                () -> unownedDevice.createVirtualRotaryEncoder(null, null));
        assertThrows(SecurityException.class, () -> unownedDevice.setShowPointerIcon(true));

        assertThrows(SecurityException.class, () -> unownedDevice.getVirtualSensorList());
        assertThrows(SecurityException.class, () -> unownedDevice.sendSensorEvent(null, null));
    }

    @Test
    public void getDeviceOwnerUid_oneDevice_returnsCorrectId() {
        int ownerUid = mLocalService.getDeviceOwnerUid(mDeviceImpl.getDeviceId());
        assertThat(ownerUid).isEqualTo(mDeviceImpl.getOwnerUid());
    }

    @Test
    public void getDeviceOwnerUid_twoDevices_returnsCorrectId() {
        createVirtualDevice(VIRTUAL_DEVICE_ID_2, DEVICE_OWNER_UID_2);

        int secondDeviceOwner = mLocalService.getDeviceOwnerUid(VIRTUAL_DEVICE_ID_2);
        assertThat(secondDeviceOwner).isEqualTo(DEVICE_OWNER_UID_2);

        int firstDeviceOwner = mLocalService.getDeviceOwnerUid(VIRTUAL_DEVICE_ID_1);
        assertThat(firstDeviceOwner).isEqualTo(DEVICE_OWNER_UID_1);
    }

    @Test
    public void getDeviceOwnerUid_nonExistentDevice_returnsInvalidUid() {
        int nonExistentDeviceId = DEVICE_ID_DEFAULT;
        int ownerUid = mLocalService.getDeviceOwnerUid(nonExistentDeviceId);
        assertThat(ownerUid).isEqualTo(Process.INVALID_UID);
    }

    @Test
    public void getVirtualSensor_defaultDeviceId_returnsNull() {
        assertThat(mLocalService.getVirtualSensor(DEVICE_ID_DEFAULT, SENSOR_HANDLE)).isNull();
    }

    @Test
    public void getVirtualSensor_invalidDeviceId_returnsNull() {
        assertThat(mLocalService.getVirtualSensor(DEVICE_ID_INVALID, SENSOR_HANDLE)).isNull();
    }

    @Test
    public void getVirtualSensor_noSensors_returnsNull() {
        assertThat(mLocalService.getVirtualSensor(VIRTUAL_DEVICE_ID_1, SENSOR_HANDLE)).isNull();
    }

    @Test
    public void getVirtualSensor_returnsCorrectSensor() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_CUSTOM)
                .addVirtualSensorConfig(
                        new VirtualSensorConfig.Builder(Sensor.TYPE_ACCELEROMETER, DEVICE_NAME_1)
                                .build())
                .setVirtualSensorCallback(BackgroundThread.getExecutor(), mSensorCallback)
                .build();

        doReturn(SENSOR_HANDLE).when(mSensorManagerInternalMock).createRuntimeSensor(
                anyInt(), anyInt(), anyString(), anyString(), anyFloat(), anyFloat(), anyFloat(),
                anyInt(), anyInt(), anyInt(), any());
        mDeviceImpl.close();
        mDeviceImpl = createVirtualDevice(VIRTUAL_DEVICE_ID_1, DEVICE_OWNER_UID_1, params);

        VirtualSensor sensor = mLocalService.getVirtualSensor(VIRTUAL_DEVICE_ID_1, SENSOR_HANDLE);
        assertThat(sensor).isNotNull();
        assertThat(sensor.getDeviceId()).isEqualTo(VIRTUAL_DEVICE_ID_1);
        assertThat(sensor.getHandle()).isEqualTo(SENSOR_HANDLE);
        assertThat(sensor.getType()).isEqualTo(Sensor.TYPE_ACCELEROMETER);
    }

    @Test
    public void testIsInputDeviceOwnedByVirtualDevice() throws RemoteException {
        when(mIVirtualInputDevice.getInputDeviceId()).thenReturn(INPUT_DEVICE_ID);

        assertThat(mLocalService.isInputDeviceOwnedByVirtualDevice(INPUT_DEVICE_ID)).isFalse();

        mInputController.addDevice(BINDER, mIVirtualInputDevice);
        assertThat(mLocalService.isInputDeviceOwnedByVirtualDevice(INPUT_DEVICE_ID)).isTrue();

        mInputController.removeDeviceForTesting(BINDER);
        assertThat(mLocalService.isInputDeviceOwnedByVirtualDevice(INPUT_DEVICE_ID)).isFalse();
    }

    @Test
    public void getDeviceIdsForUid_noRunningApps_returnsNull() {
        assertThat(mLocalService.getDeviceIdsForUid(UID_1)).isEmpty();
        assertThat(mVdmNative.getDeviceIdsForUid(UID_1)).isEmpty();
    }

    @Test
    public void getDeviceIdsForUid_differentUidOnDevice_returnsNull() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);
        mDeviceImpl.getDisplayWindowPolicyControllerForTest(DISPLAY_ID_1).onRunningAppsChanged(
                Sets.newArraySet(new Pair<>(UID_2, PACKAGE_2)));

        assertThat(mLocalService.getDeviceIdsForUid(UID_1)).isEmpty();
        assertThat(mVdmNative.getDeviceIdsForUid(UID_1)).isEmpty();
    }

    @Test
    public void getDeviceIdsForUid_oneUidOnDevice_returnsCorrectId() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);
        mDeviceImpl.getDisplayWindowPolicyControllerForTest(DISPLAY_ID_1).onRunningAppsChanged(
                Sets.newArraySet(new Pair<>(UID_1, PACKAGE_1)));

        int deviceId = mDeviceImpl.getDeviceId();
        assertThat(mLocalService.getDeviceIdsForUid(UID_1)).containsExactly(deviceId);
        assertThat(mVdmNative.getDeviceIdsForUid(UID_1)).asList().containsExactly(deviceId);
    }

    @Test
    public void getDeviceIdsForUid_twoUidsOnDevice_returnsCorrectId() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);

        mDeviceImpl.getDisplayWindowPolicyControllerForTest(DISPLAY_ID_1).onRunningAppsChanged(
                Sets.newArraySet(new Pair<>(UID_1, PACKAGE_1), new Pair<>(UID_2, PACKAGE_2)));

        int deviceId = mDeviceImpl.getDeviceId();
        assertThat(mLocalService.getDeviceIdsForUid(UID_1)).containsExactly(deviceId);
        assertThat(mVdmNative.getDeviceIdsForUid(UID_1)).asList().containsExactly(deviceId);
    }

    @Test
    public void getDeviceIdsForUid_twoDevicesUidOnOne_returnsCorrectId() {
        VirtualDeviceImpl secondDevice = createVirtualDevice(VIRTUAL_DEVICE_ID_2,
                DEVICE_OWNER_UID_1);
        addVirtualDisplay(secondDevice, DISPLAY_ID_2);

        secondDevice.getDisplayWindowPolicyControllerForTest(DISPLAY_ID_2).onRunningAppsChanged(
                Sets.newArraySet(new Pair<>(UID_1, PACKAGE_1)));

        int deviceId = secondDevice.getDeviceId();
        assertThat(mLocalService.getDeviceIdsForUid(UID_1)).containsExactly(deviceId);
        assertThat(mVdmNative.getDeviceIdsForUid(UID_1)).asList().containsExactly(deviceId);
    }

    @Test
    public void getDeviceIdsForUid_twoDevicesUidOnBoth_returnsCorrectId() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);
        VirtualDeviceImpl secondDevice = createVirtualDevice(VIRTUAL_DEVICE_ID_2,
                DEVICE_OWNER_UID_1);
        addVirtualDisplay(secondDevice, DISPLAY_ID_2);


        mDeviceImpl.getDisplayWindowPolicyControllerForTest(DISPLAY_ID_1).onRunningAppsChanged(
                Sets.newArraySet(new Pair<>(UID_1, PACKAGE_1)));
        secondDevice.getDisplayWindowPolicyControllerForTest(DISPLAY_ID_2).onRunningAppsChanged(
                Sets.newArraySet(new Pair<>(UID_1, PACKAGE_1), new Pair<>(UID_2, PACKAGE_2)));

        assertThat(mLocalService.getDeviceIdsForUid(UID_1)).containsExactly(
                mDeviceImpl.getDeviceId(), secondDevice.getDeviceId());
        assertThat(mVdmNative.getDeviceIdsForUid(UID_1)).asList().containsExactly(
                mDeviceImpl.getDeviceId(), secondDevice.getDeviceId());
    }

    @Test
    public void getPreferredLocaleListForApp_keyboardAttached_returnLocaleHints() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1, Display.FLAG_TRUSTED);
        mDeviceImpl.createVirtualKeyboard(KEYBOARD_CONFIG, BINDER);

        mDeviceImpl.getDisplayWindowPolicyControllerForTest(DISPLAY_ID_1).onRunningAppsChanged(
                Sets.newArraySet(new Pair<>(UID_1, PACKAGE_1)));

        LocaleList localeList = mLocalService.getPreferredLocaleListForUid(UID_1);
        assertThat(localeList).isEqualTo(
                LocaleList.forLanguageTags(KEYBOARD_CONFIG.getLanguageTag()));
    }

    @Test
    public void getPreferredLocaleListForApp_noKeyboardAttached_nullLocaleHints() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1, Display.FLAG_TRUSTED);
        mDeviceImpl.getDisplayWindowPolicyControllerForTest(DISPLAY_ID_1).onRunningAppsChanged(
                Sets.newArraySet(new Pair<>(UID_1, PACKAGE_1)));

        // no preceding call to createVirtualKeyboard()
        assertThat(mLocalService.getPreferredLocaleListForUid(UID_1)).isNull();
    }

    @Test
    public void getPreferredLocaleListForApp_appOnMultipleVD_localeOnFirstVDReturned() {
        VirtualDeviceImpl secondDevice = createVirtualDevice(VIRTUAL_DEVICE_ID_2,
                DEVICE_OWNER_UID_1);
        Binder secondBinder = new Binder("secondBinder");
        VirtualKeyboardConfig firstKeyboardConfig =
                new VirtualKeyboardConfig.Builder()
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(DEVICE_NAME_1)
                        .setAssociatedDisplayId(DISPLAY_ID_1)
                        .setLanguageTag("zh-CN")
                        .build();
        VirtualKeyboardConfig secondKeyboardConfig =
                new VirtualKeyboardConfig.Builder()
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(DEVICE_NAME_2)
                        .setAssociatedDisplayId(DISPLAY_ID_2)
                        .setLanguageTag("fr-FR")
                        .build();

        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1, Display.FLAG_TRUSTED);
        addVirtualDisplay(secondDevice, DISPLAY_ID_2, Display.FLAG_TRUSTED);

        mDeviceImpl.createVirtualKeyboard(firstKeyboardConfig, BINDER);
        secondDevice.createVirtualKeyboard(secondKeyboardConfig, secondBinder);

        mDeviceImpl.getDisplayWindowPolicyControllerForTest(DISPLAY_ID_1).onRunningAppsChanged(
                Sets.newArraySet(new Pair<>(UID_1, PACKAGE_1)));
        secondDevice.getDisplayWindowPolicyControllerForTest(DISPLAY_ID_2).onRunningAppsChanged(
                Sets.newArraySet(new Pair<>(UID_1, PACKAGE_1)));

        LocaleList localeList = mLocalService.getPreferredLocaleListForUid(UID_1);
        assertThat(localeList).isEqualTo(
                LocaleList.forLanguageTags(firstKeyboardConfig.getLanguageTag()));
    }

    @Test
    public void cameraAccessController_observerCountUpdated() {
        assertThat(mCameraAccessController.getObserverCount()).isEqualTo(1);

        VirtualDeviceImpl secondDevice =
                createVirtualDevice(VIRTUAL_DEVICE_ID_2, DEVICE_OWNER_UID_1);
        assertThat(mCameraAccessController.getObserverCount()).isEqualTo(2);

        mDeviceImpl.close();
        assertThat(mCameraAccessController.getObserverCount()).isEqualTo(1);

        secondDevice.close();
        assertThat(mCameraAccessController.getObserverCount()).isEqualTo(0);
    }

    @Test
    public void onVirtualDisplayRemovedLocked_doesNotThrowException() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);
        // This call should not throw any exceptions.
        mDeviceImpl.onVirtualDisplayRemoved(DISPLAY_ID_1);
    }

    @Test
    public void onPersistentDeviceIdsRemoved_listenersNotified() {
        mLocalService.registerPersistentDeviceIdRemovedListener(mPersistentDeviceIdRemovedListener);
        mVdms.onPersistentDeviceIdsRemoved(Set.of(mDeviceImpl.getPersistentDeviceId()));
        TestableLooper.get(this).processAllMessages();

        verify(mPersistentDeviceIdRemovedListener).accept(mDeviceImpl.getPersistentDeviceId());
    }

    @Test
    public void onCdmAssociationsChanged_persistentDeviceIdRemovedListenersNotified() {
        mLocalService.registerPersistentDeviceIdRemovedListener(mPersistentDeviceIdRemovedListener);
        mVdms.onCdmAssociationsChanged(List.of(mAssociationInfo));
        TestableLooper.get(this).processAllMessages();

        mVdms.onCdmAssociationsChanged(List.of(
                createAssociationInfo(2, AssociationRequest.DEVICE_PROFILE_APP_STREAMING),
                createAssociationInfo(3, AssociationRequest.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION),
                createAssociationInfo(4, AssociationRequest.DEVICE_PROFILE_WATCH)));
        TestableLooper.get(this).processAllMessages();

        verify(mPersistentDeviceIdRemovedListener).accept(mDeviceImpl.getPersistentDeviceId());

        mVdms.onCdmAssociationsChanged(Collections.emptyList());
        TestableLooper.get(this).processAllMessages();

        verify(mPersistentDeviceIdRemovedListener)
                .accept(VirtualDeviceImpl.createPersistentDeviceId(2));
        verify(mPersistentDeviceIdRemovedListener)
                .accept(VirtualDeviceImpl.createPersistentDeviceId(3));
        verifyNoMoreInteractions(mPersistentDeviceIdRemovedListener);
    }

    @Test
    public void getAllPersistentDeviceIds_respectsCurrentAssociations() {
        mVdms.onCdmAssociationsChanged(List.of(mAssociationInfo));
        TestableLooper.get(this).processAllMessages();

        assertThat(mLocalService.getAllPersistentDeviceIds())
                .containsExactly(mDeviceImpl.getPersistentDeviceId());

        mVdms.onCdmAssociationsChanged(List.of(
                createAssociationInfo(2, AssociationRequest.DEVICE_PROFILE_APP_STREAMING),
                createAssociationInfo(3, AssociationRequest.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION),
                createAssociationInfo(4, AssociationRequest.DEVICE_PROFILE_WATCH)));
        TestableLooper.get(this).processAllMessages();

        assertThat(mLocalService.getAllPersistentDeviceIds()).containsExactly(
                VirtualDeviceImpl.createPersistentDeviceId(2),
                VirtualDeviceImpl.createPersistentDeviceId(3));

        mVdms.onCdmAssociationsChanged(Collections.emptyList());
        TestableLooper.get(this).processAllMessages();

        assertThat(mLocalService.getAllPersistentDeviceIds()).isEmpty();
    }

    @Test
    public void getDisplayNameForPersistentDeviceId_nonExistentPeristentId_returnsNull() {
        assertThat(mVdm.getDisplayNameForPersistentDeviceId("nonExistentPersistentId")).isNull();
    }

    @Test
    public void getDisplayNameForPersistentDeviceId_defaultDevicePeristentId_returnsNull() {
        assertThat(mVdm.getDisplayNameForPersistentDeviceId(
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT))
                .isNull();
    }

    @Test
    public void getDisplayNameForPersistentDeviceId_validVirtualDevice_returnsCorrectId() {
        mVdms.onCdmAssociationsChanged(List.of(mAssociationInfo));
        CharSequence persistentIdDisplayName =
                mVdm.getDisplayNameForPersistentDeviceId(mDeviceImpl.getPersistentDeviceId());
        assertThat(persistentIdDisplayName.toString())
                .isEqualTo(mAssociationInfo.getDisplayName().toString());
    }

    @Test
    public void getDisplayNameForPersistentDeviceId_noVirtualDevice_returnsCorrectId() {
        CharSequence displayName = "New display name for the new association";
        mVdms.onCdmAssociationsChanged(List.of(
                createAssociationInfo(2, AssociationRequest.DEVICE_PROFILE_APP_STREAMING,
                        displayName)));

        CharSequence persistentIdDisplayName =
                mVdm.getDisplayNameForPersistentDeviceId(
                        VirtualDeviceImpl.createPersistentDeviceId(2));
        assertThat(persistentIdDisplayName.toString()).isEqualTo(displayName.toString());
    }

    @Test
    public void onAppsOnVirtualDeviceChanged_singleVirtualDevice_listenersNotified() {
        ArraySet<Integer> uids = new ArraySet<>(Arrays.asList(UID_1, UID_2));
        ArraySet<Pair<Integer, String>> packageUids = new ArraySet<>(Arrays.asList(
                new Pair<>(UID_1, PACKAGE_1), new Pair<>(UID_2, PACKAGE_2)));
        mLocalService.registerAppsOnVirtualDeviceListener(mAppsOnVirtualDeviceListener);

        mVdms.onRunningAppsChanged(
                mDeviceImpl.getDeviceId(), VIRTUAL_DEVICE_OWNER_PACKAGE, uids, packageUids);
        TestableLooper.get(this).processAllMessages();

        verify(mAppsOnVirtualDeviceListener).onAppsRunningOnVirtualDeviceChanged(
                mDeviceImpl.getDeviceId(), uids);
    }

    @Test
    public void onVirtualDisplayCreatedLocked_notTrustedDisplay_noWakeLockIsAcquired()
            throws RemoteException {
        verify(mIPowerManagerMock, never()).acquireWakeLock(any(Binder.class), anyInt(),
                nullable(String.class), nullable(String.class), nullable(WorkSource.class),
                nullable(String.class), anyInt(), eq(null));
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);
        TestableLooper.get(this).processAllMessages();
        verify(mIPowerManagerMock, never()).acquireWakeLock(any(Binder.class), anyInt(),
                nullable(String.class), nullable(String.class), nullable(WorkSource.class),
                nullable(String.class), anyInt(), eq(null));
    }

    @DisableFlags(Flags.FLAG_DEVICE_AWARE_DISPLAY_POWER)
    @Test
    public void onVirtualDisplayCreatedLocked_wakeLockIsAcquired() throws RemoteException {
        verify(mIPowerManagerMock, never()).acquireWakeLock(any(Binder.class), anyInt(),
                nullable(String.class), nullable(String.class), nullable(WorkSource.class),
                nullable(String.class), anyInt(), eq(null));
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1, Display.FLAG_TRUSTED);
        verify(mIPowerManagerMock).acquireWakeLock(any(Binder.class), anyInt(),
                nullable(String.class), nullable(String.class), nullable(WorkSource.class),
                nullable(String.class), eq(DISPLAY_ID_1), eq(null));
    }

    @DisableFlags(Flags.FLAG_DEVICE_AWARE_DISPLAY_POWER)
    @Test
    public void onVirtualDisplayCreatedLocked_duplicateCalls_onlyOneWakeLockIsAcquired()
            throws RemoteException {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1, Display.FLAG_TRUSTED);
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1, Display.FLAG_TRUSTED);
        TestableLooper.get(this).processAllMessages();
        verify(mIPowerManagerMock).acquireWakeLock(any(Binder.class), anyInt(),
                nullable(String.class), nullable(String.class), nullable(WorkSource.class),
                nullable(String.class), eq(DISPLAY_ID_1), eq(null));
    }

    @DisableFlags(Flags.FLAG_DEVICE_AWARE_DISPLAY_POWER)
    @Test
    public void onVirtualDisplayRemovedLocked_wakeLockIsReleased() throws RemoteException {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1, Display.FLAG_TRUSTED);
        ArgumentCaptor<IBinder> wakeLockCaptor = ArgumentCaptor.forClass(IBinder.class);
        TestableLooper.get(this).processAllMessages();
        verify(mIPowerManagerMock).acquireWakeLock(wakeLockCaptor.capture(),
                anyInt(),
                nullable(String.class), nullable(String.class), nullable(WorkSource.class),
                nullable(String.class), eq(DISPLAY_ID_1), eq(null));

        IBinder wakeLock = wakeLockCaptor.getValue();
        mDeviceImpl.onVirtualDisplayRemoved(DISPLAY_ID_1);
        verify(mIPowerManagerMock).releaseWakeLock(eq(wakeLock), anyInt());
    }

    @DisableFlags(Flags.FLAG_DEVICE_AWARE_DISPLAY_POWER)
    @Test
    public void addVirtualDisplay_displayNotReleased_wakeLockIsReleased() throws RemoteException {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1, Display.FLAG_TRUSTED);
        ArgumentCaptor<IBinder> wakeLockCaptor = ArgumentCaptor.forClass(IBinder.class);
        TestableLooper.get(this).processAllMessages();
        verify(mIPowerManagerMock).acquireWakeLock(wakeLockCaptor.capture(),
                anyInt(),
                nullable(String.class), nullable(String.class), nullable(WorkSource.class),
                nullable(String.class), eq(DISPLAY_ID_1), eq(null));
        IBinder wakeLock = wakeLockCaptor.getValue();

        // Close the VirtualDevice without first notifying it of the VirtualDisplay removal.
        mDeviceImpl.close();
        verify(mIPowerManagerMock).releaseWakeLock(eq(wakeLock), anyInt());
    }

    @Test
    public void createVirtualDpad_noDisplay_failsSecurityException() {
        assertThrows(SecurityException.class,
                () -> mDeviceImpl.createVirtualDpad(DPAD_CONFIG, BINDER));
    }

    @Test
    public void createVirtualDpad_untrustedDisplay_failsSecurityException() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);
        assertThrows(SecurityException.class,
                () -> mDeviceImpl.createVirtualDpad(DPAD_CONFIG, BINDER));
    }

    @Test
    public void createVirtualKeyboard_noDisplay_failsSecurityException() {
        assertThrows(SecurityException.class,
                () -> mDeviceImpl.createVirtualKeyboard(KEYBOARD_CONFIG, BINDER));
    }

    @Test
    public void createVirtualKeyboard_untrustedDisplay_failsSecurityException() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);
        assertThrows(SecurityException.class,
                () -> mDeviceImpl.createVirtualKeyboard(KEYBOARD_CONFIG, BINDER));
    }

    @Test
    public void createVirtualMouse_noDisplay_failsSecurityException() {
        assertThrows(SecurityException.class,
                () -> mDeviceImpl.createVirtualMouse(MOUSE_CONFIG, BINDER));
    }

    @Test
    public void createVirtualMouse_untrustedDisplay_failsSecurityException() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);
        assertThrows(SecurityException.class,
                () -> mDeviceImpl.createVirtualMouse(MOUSE_CONFIG, BINDER));
    }

    @Test
    public void createVirtualTouchscreen_noDisplay_failsSecurityException() {
        assertThrows(SecurityException.class,
                () -> mDeviceImpl.createVirtualTouchscreen(TOUCHSCREEN_CONFIG, BINDER));
    }

    @Test
    public void createVirtualTouchscreen_untrustedDisplay_failsSecurityException() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);
        assertThrows(SecurityException.class,
                () -> mDeviceImpl.createVirtualTouchscreen(TOUCHSCREEN_CONFIG, BINDER));
    }

    @Test
    public void createVirtualNavigationTouchpad_noDisplay_failsSecurityException() {
        assertThrows(SecurityException.class,
                () -> mDeviceImpl.createVirtualNavigationTouchpad(NAVIGATION_TOUCHPAD_CONFIG,
                        BINDER));
    }

    @Test
    public void createVirtualNavigationTouchpad_untrustedDisplay_failsSecurityException() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);
        assertThrows(SecurityException.class,
                () -> mDeviceImpl.createVirtualNavigationTouchpad(NAVIGATION_TOUCHPAD_CONFIG,
                        BINDER));
    }

    @Test
    public void onAudioSessionStarting_noDisplay_failsSecurityException() {
        assertThrows(SecurityException.class,
                () -> mDeviceImpl.onAudioSessionStarting(
                        DISPLAY_ID_1, mRoutingCallback, mConfigChangedCallback));
    }

    @Test
    public void createVirtualKeyboard_keyboardWithoutExplicitLayoutInfo_localeUpdatedWithDefault() {
        VirtualKeyboardConfig configWithoutExplicitLayoutInfo =
                new VirtualKeyboardConfig.Builder()
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(DEVICE_NAME_1)
                        .setAssociatedDisplayId(DISPLAY_ID_1)
                        .build();

        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1, Display.FLAG_TRUSTED);
        mDeviceImpl.createVirtualKeyboard(configWithoutExplicitLayoutInfo, BINDER);
        assertThat(mDeviceImpl.getDeviceLocaleList()).isEqualTo(
                LocaleList.forLanguageTags(VirtualKeyboardConfig.DEFAULT_LANGUAGE_TAG));
    }

    @Test
    public void virtualDeviceWithoutKeyboard_noLocaleUpdate() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);

        // no preceding call to createVirtualKeyboard()
        assertThat(mDeviceImpl.getDeviceLocaleList()).isNull();
    }

    @Test
    public void onAudioSessionStarting_hasVirtualAudioController() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);

        mDeviceImpl.onAudioSessionStarting(DISPLAY_ID_1, mRoutingCallback, mConfigChangedCallback);

        assertThat(mDeviceImpl.getVirtualAudioControllerForTesting()).isNotNull();
    }

    @Test
    public void onAudioSessionEnded_noVirtualAudioController() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);
        mDeviceImpl.onAudioSessionStarting(DISPLAY_ID_1, mRoutingCallback, mConfigChangedCallback);

        mDeviceImpl.onAudioSessionEnded();

        assertThat(mDeviceImpl.getVirtualAudioControllerForTesting()).isNull();
    }

    @Test
    public void close_cleanVirtualAudioController() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);
        mDeviceImpl.onAudioSessionStarting(DISPLAY_ID_1, mRoutingCallback, mConfigChangedCallback);

        mDeviceImpl.close();

        assertThat(mDeviceImpl.getVirtualAudioControllerForTesting()).isNull();
    }

    @Test
    public void close_cleanSensorController() {
        mSensorController.addSensorForTesting(
                BINDER, SENSOR_HANDLE, Sensor.TYPE_ACCELEROMETER, DEVICE_NAME_1);

        mDeviceImpl.close();

        assertThat(mSensorController.getSensorDescriptors()).isEmpty();
        verify(mSensorManagerInternalMock).removeRuntimeSensor(SENSOR_HANDLE);
    }

    @Test
    public void closedDevice_emptyRunningApps_sent() {
        mLocalService.registerAppsOnVirtualDeviceListener(mAppsOnVirtualDeviceListener);
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);
        int deviceId = mDeviceImpl.getDeviceId();
        mDeviceImpl.getDisplayWindowPolicyControllerForTest(DISPLAY_ID_1).onRunningAppsChanged(
                Sets.newArraySet(new Pair<>(UID_2, PACKAGE_2)));
        TestableLooper.get(this).processAllMessages();
        verify(mAppsOnVirtualDeviceListener)
                .onAppsRunningOnVirtualDeviceChanged(deviceId, Sets.newArraySet(UID_2));

        mDeviceImpl.close();
        TestableLooper.get(this).processAllMessages();
        verify(mAppsOnVirtualDeviceListener)
                .onAppsRunningOnVirtualDeviceChanged(deviceId, new ArraySet<>());
    }

    @Test
    public void setShowPointerIcon_setsValueForAllDisplays() {
        addVirtualDisplay(mDeviceImpl, 1, Display.FLAG_TRUSTED);
        addVirtualDisplay(mDeviceImpl, 2, Display.FLAG_TRUSTED);
        addVirtualDisplay(mDeviceImpl, 3, Display.FLAG_TRUSTED);
        VirtualMouseConfig config1 = new VirtualMouseConfig.Builder()
                .setAssociatedDisplayId(1)
                .setInputDeviceName(DEVICE_NAME_1)
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .build();
        VirtualMouseConfig config2 = new VirtualMouseConfig.Builder()
                .setAssociatedDisplayId(2)
                .setInputDeviceName(DEVICE_NAME_2)
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .build();
        VirtualMouseConfig config3 = new VirtualMouseConfig.Builder()
                .setAssociatedDisplayId(3)
                .setInputDeviceName(DEVICE_NAME_3)
                .setVendorId(VENDOR_ID)
                .setProductId(PRODUCT_ID)
                .build();

        mDeviceImpl.createVirtualMouse(config1, BINDER);
        mDeviceImpl.createVirtualMouse(config2, BINDER);
        mDeviceImpl.createVirtualMouse(config3, BINDER);
        clearInvocations(mInputManagerInternalMock);
        mDeviceImpl.setShowPointerIcon(false);

        verify(mInputManagerInternalMock, times(3)).setPointerIconVisible(eq(false), anyInt());
        verify(mInputManagerInternalMock, never()).setPointerIconVisible(eq(true), anyInt());
        mDeviceImpl.setShowPointerIcon(true);
        verify(mInputManagerInternalMock, times(3)).setPointerIconVisible(eq(true), anyInt());
    }

    @Test
    public void setShowPointerIcon_untrustedDisplay_pointerIconIsAlwaysShown() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);
        clearInvocations(mInputManagerInternalMock);
        mDeviceImpl.setShowPointerIcon(false);
        verify(mInputManagerInternalMock, times(0)).setPointerIconVisible(eq(false), anyInt());
    }

    @EnableFlags(Flags.FLAG_DEVICE_AWARE_UI_MODE)
    @Test
    public void setDisplayUiMode_untrustedDisplay_throws() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);
        assertThrows(SecurityException.class, () -> mDeviceImpl.setDisplayUiMode(
                DISPLAY_ID_1, Configuration.UI_MODE_NIGHT_YES));
    }

    @EnableFlags(Flags.FLAG_DEVICE_AWARE_UI_MODE)
    @Test
    public void setDisplayUiMode_unownedDisplay_throws() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);
        assertThrows(SecurityException.class, () -> mDeviceImpl.setDisplayUiMode(
                Display.DEFAULT_DISPLAY, Configuration.UI_MODE_NIGHT_YES));
    }

    @EnableFlags(Flags.FLAG_DEVICE_AWARE_UI_MODE)
    @Test
    public void setDisplayUiMode_displayReleased_resetUiMode() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1, Display.FLAG_TRUSTED);
        mDeviceImpl.setDisplayUiMode(DISPLAY_ID_1, Configuration.UI_MODE_NIGHT_YES);
        verify(mUiModeManagerInternalMock).setDisplayUiMode(
                DISPLAY_ID_1, Configuration.UI_MODE_NIGHT_YES);

        mDeviceImpl.onVirtualDisplayRemoved(DISPLAY_ID_1);
        verify(mUiModeManagerInternalMock).setDisplayUiMode(
                DISPLAY_ID_1, Configuration.UI_MODE_TYPE_UNDEFINED);
    }

    @EnableFlags(Flags.FLAG_DEVICE_AWARE_UI_MODE)
    @Test
    public void setDisplayUiMode_deviceClosed_resetUiMode() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1, Display.FLAG_TRUSTED);
        mDeviceImpl.setDisplayUiMode(DISPLAY_ID_1, Configuration.UI_MODE_NIGHT_YES);
        verify(mUiModeManagerInternalMock).setDisplayUiMode(
                DISPLAY_ID_1, Configuration.UI_MODE_NIGHT_YES);

        mDeviceImpl.close();
        verify(mUiModeManagerInternalMock).setDisplayUiMode(
                DISPLAY_ID_1, Configuration.UI_MODE_TYPE_UNDEFINED);
    }

    @Test
    public void openNonBlockedAppOnVirtualDisplay_succeeds() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);
        GenericWindowPolicyController gwpc = mDeviceImpl.getDisplayWindowPolicyControllerForTest(
                DISPLAY_ID_1);
        doNothing().when(mContext).startActivityAsUser(any(), any(), any());

        ActivityInfo activityInfo = getActivityInfo(
                NONBLOCKED_APP_PACKAGE_NAME,
                NONBLOCKED_APP_PACKAGE_NAME,
                /* displayOnRemoteDevices */ true,
                /* targetDisplayCategory */ null);
        Intent blockedAppIntent = BlockedAppStreamingActivity.createIntent(
                activityInfo, mAssociationInfo.getDisplayName());
        gwpc.canActivityBeLaunched(activityInfo, blockedAppIntent,
                WindowConfiguration.WINDOWING_MODE_FULLSCREEN, DISPLAY_ID_1, /* isNewTask= */ false,
                /* isResultExpected = */ false, /* intentSender= */ null);

        verify(mContext, never()).startActivityAsUser(argThat(intent ->
                intent.filterEquals(blockedAppIntent)), any(), any());
    }

    @Test
    public void openNonBlockedAppOnVirtualDisplay_cannotDisplayOnRemoteDevices_blocked() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);
        GenericWindowPolicyController gwpc = mDeviceImpl.getDisplayWindowPolicyControllerForTest(
                DISPLAY_ID_1);
        doNothing().when(mContext).startActivityAsUser(any(), any(), any());

        ActivityInfo activityInfo = getActivityInfo(
                NONBLOCKED_APP_PACKAGE_NAME,
                NONBLOCKED_APP_PACKAGE_NAME,
                /* displayOnRemoteDevices */ false,
                /* targetDisplayCategory */ null);
        Intent blockedAppIntent = BlockedAppStreamingActivity.createIntent(
                activityInfo, mAssociationInfo.getDisplayName());
        gwpc.canActivityBeLaunched(activityInfo, blockedAppIntent,
                WindowConfiguration.WINDOWING_MODE_FULLSCREEN, DISPLAY_ID_1, /* isNewTask= */ false,
                /* isResultExpected = */ false, /* intentSender= */ null);

        verify(mContext).startActivityAsUser(argThat(intent ->
                intent.filterEquals(blockedAppIntent)), any(), any());
    }

    @Test
    public void openBlockedAppOnVirtualDisplay_blocked() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);
        GenericWindowPolicyController gwpc = mDeviceImpl.getDisplayWindowPolicyControllerForTest(
                DISPLAY_ID_1);
        doNothing().when(mContext).startActivityAsUser(any(), any(), any());

        ActivityInfo activityInfo = getActivityInfo(
                BLOCKED_APP_PACKAGE_NAME,
                BLOCKED_APP_PACKAGE_NAME,
                /* displayOnRemoteDevices */ true,
                /* targetDisplayCategory */ null);
        Intent blockedAppIntent = BlockedAppStreamingActivity.createIntent(
                activityInfo, mAssociationInfo.getDisplayName());
        gwpc.canActivityBeLaunched(activityInfo, blockedAppIntent,
                WindowConfiguration.WINDOWING_MODE_FULLSCREEN, DISPLAY_ID_1, /* isNewTask= */ false,
                /* isResultExpected = */ false, /* intentSender= */ null);

        verify(mContext).startActivityAsUser(argThat(intent ->
                intent.filterEquals(blockedAppIntent)), any(), any());
    }

    @Test
    public void canActivityBeLaunched_activityCanLaunch() {
        Intent intent = new Intent(ACTION_VIEW, Uri.parse(TEST_SITE));
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);
        GenericWindowPolicyController gwpc = mDeviceImpl.getDisplayWindowPolicyControllerForTest(
                DISPLAY_ID_1);
        ActivityInfo activityInfo = getActivityInfo(
                NONBLOCKED_APP_PACKAGE_NAME,
                NONBLOCKED_APP_PACKAGE_NAME,
                /* displayOnRemoteDevices */ true,
                /* targetDisplayCategory */ null);
        assertThat(gwpc.canActivityBeLaunched(activityInfo, intent,
                WindowConfiguration.WINDOWING_MODE_FULLSCREEN, DISPLAY_ID_1, /* isNewTask= */ false,
                /* isResultExpected = */ false, /* intentSender= */ null))
                .isTrue();
    }

    @Test
    public void canActivityBeLaunched_intentInterceptedWhenRegistered_activityNoLaunch()
            throws RemoteException {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(TEST_SITE));

        IVirtualDeviceIntentInterceptor.Stub interceptor =
                mock(IVirtualDeviceIntentInterceptor.Stub.class);
        doNothing().when(interceptor).onIntentIntercepted(any());
        doReturn(interceptor).when(interceptor).asBinder();
        doReturn(interceptor).when(interceptor).queryLocalInterface(anyString());

        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);
        GenericWindowPolicyController gwpc = mDeviceImpl.getDisplayWindowPolicyControllerForTest(
                DISPLAY_ID_1);
        ActivityInfo activityInfo = getActivityInfo(
                NONBLOCKED_APP_PACKAGE_NAME,
                NONBLOCKED_APP_PACKAGE_NAME,
                /* displayOnRemoteDevices */ true,
                /* targetDisplayCategory */ null);

        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_VIEW);
        intentFilter.addDataScheme(IntentFilter.SCHEME_HTTP);
        intentFilter.addDataScheme(IntentFilter.SCHEME_HTTPS);

        // register interceptor and intercept intent
        mDeviceImpl.registerIntentInterceptor(interceptor, intentFilter);
        assertThat(gwpc.canActivityBeLaunched(activityInfo, intent,
                WindowConfiguration.WINDOWING_MODE_FULLSCREEN, DISPLAY_ID_1, /* isNewTask= */ false,
                /* isResultExpected = */ false, /* intentSender= */ null))
                .isFalse();
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(interceptor).onIntentIntercepted(intentCaptor.capture());
        Intent cIntent = intentCaptor.getValue();
        assertThat(cIntent).isNotNull();
        assertThat(cIntent.getAction()).isEqualTo(Intent.ACTION_VIEW);
        assertThat(cIntent.getData().toString()).isEqualTo(TEST_SITE);

        // unregister interceptor and launch activity
        mDeviceImpl.unregisterIntentInterceptor(interceptor);
        assertThat(gwpc.canActivityBeLaunched(activityInfo, intent,
                WindowConfiguration.WINDOWING_MODE_FULLSCREEN, DISPLAY_ID_1, /* isNewTask= */ false,
                /* isResultExpected = */ false, /* intentSender= */ null))
                .isTrue();
    }

    @Test
    public void canActivityBeLaunched_noMatchIntentFilter_activityLaunches()
            throws RemoteException {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("testing"));

        IVirtualDeviceIntentInterceptor.Stub interceptor =
                mock(IVirtualDeviceIntentInterceptor.Stub.class);
        doNothing().when(interceptor).onIntentIntercepted(any());
        doReturn(interceptor).when(interceptor).asBinder();
        doReturn(interceptor).when(interceptor).queryLocalInterface(anyString());

        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);
        GenericWindowPolicyController gwpc = mDeviceImpl.getDisplayWindowPolicyControllerForTest(
                DISPLAY_ID_1);
        ActivityInfo activityInfo = getActivityInfo(
                NONBLOCKED_APP_PACKAGE_NAME,
                NONBLOCKED_APP_PACKAGE_NAME,
                /* displayOnRemoteDevices */ true,
                /* targetDisplayCategory */ null);

        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_VIEW);
        intentFilter.addDataScheme("mailto");

        // register interceptor with different filter
        mDeviceImpl.registerIntentInterceptor(interceptor, intentFilter);

        assertThat(gwpc.canActivityBeLaunched(activityInfo, intent,
                WindowConfiguration.WINDOWING_MODE_FULLSCREEN, DISPLAY_ID_1, /* isNewTask= */ false,
                /* isResultExpected = */ false, /* intentSender= */ null))
                .isTrue();
    }

    @Test
    public void nonRestrictedActivityOnRestrictedVirtualDisplay_startBlockedAlertActivity() {
        Intent blockedAppIntent = createRestrictedActivityBlockedIntent(Set.of("abc"),
                /* targetDisplayCategory= */ null);
        verify(mContext).startActivityAsUser(argThat(intent ->
                intent.filterEquals(blockedAppIntent)), any(), any());
    }

    @Test
    public void restrictedActivityOnRestrictedVirtualDisplay_doesNotStartBlockedAlertActivity() {
        Intent blockedAppIntent = createRestrictedActivityBlockedIntent(Set.of("abc"), "abc");
        verify(mContext, never()).startActivityAsUser(argThat(intent ->
                intent.filterEquals(blockedAppIntent)), any(), any());
    }

    @Test
    public void restrictedActivityOnNonRestrictedVirtualDisplay_startBlockedAlertActivity() {
        Intent blockedAppIntent = createRestrictedActivityBlockedIntent(
                /* displayCategories= */ Set.of(), "abc");
        verify(mContext).startActivityAsUser(argThat(intent ->
                intent.filterEquals(blockedAppIntent)), any(), any());
    }

    @Test
    public void restrictedActivityNonMatchingRestrictedVirtualDisplay_startBlockedAlertActivity() {
        Intent blockedAppIntent = createRestrictedActivityBlockedIntent(Set.of("abc"), "def");
        verify(mContext).startActivityAsUser(argThat(intent ->
                intent.filterEquals(blockedAppIntent)), any(), any());
    }

    @Test
    public void playSoundEffect_callsSoundEffectListener() throws Exception {
        mVdm.playSoundEffect(mDeviceImpl.getDeviceId(), AudioManager.FX_KEY_CLICK);

        verify(mSoundEffectListener).onPlaySoundEffect(AudioManager.FX_KEY_CLICK);
    }

    @Test
    public void getDisplayIdsForDevice_invalidDeviceId_emptyResult() {
        ArraySet<Integer> displayIds = mLocalService.getDisplayIdsForDevice(VIRTUAL_DEVICE_ID_2);
        assertThat(displayIds).isEmpty();
    }

    @Test
    public void getDisplayIdsForDevice_noDisplays_emptyResult() {
        ArraySet<Integer> displayIds = mLocalService.getDisplayIdsForDevice(VIRTUAL_DEVICE_ID_1);
        assertThat(displayIds).isEmpty();
    }

    @Test
    public void getDisplayIdsForDevice_oneDisplay_resultContainsCorrectDisplayId() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);
        ArraySet<Integer> displayIds = mLocalService.getDisplayIdsForDevice(VIRTUAL_DEVICE_ID_1);
        assertThat(displayIds).containsExactly(DISPLAY_ID_1);
    }

    @Test
    public void getDisplayIdsForDevice_twoDisplays_resultContainsCorrectDisplayIds() {
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_1);
        addVirtualDisplay(mDeviceImpl, DISPLAY_ID_2);
        ArraySet<Integer> displayIds = mLocalService.getDisplayIdsForDevice(VIRTUAL_DEVICE_ID_1);
        assertThat(displayIds).containsExactly(DISPLAY_ID_1, DISPLAY_ID_2);
    }

    @Test
    public void getPersistentIdForDevice_invalidDeviceId_returnsNull() {
        assertThat(mLocalService.getPersistentIdForDevice(DEVICE_ID_INVALID)).isNull();
        assertThat(mLocalService.getPersistentIdForDevice(VIRTUAL_DEVICE_ID_2)).isNull();
    }

    @Test
    public void getPersistentIdForDevice_defaultDeviceId() {
        assertThat(mLocalService.getPersistentIdForDevice(DEVICE_ID_DEFAULT)).isEqualTo(
                VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT);
    }

    @Test
    public void getPersistentIdForDevice_returnsCorrectId() {
        assertThat(mLocalService.getPersistentIdForDevice(VIRTUAL_DEVICE_ID_1))
                .isEqualTo(mDeviceImpl.getPersistentDeviceId());
    }

    @Test
    @EnableFlags(Flags.FLAG_VIEWCONFIGURATION_APIS)
    public void applyViewConfigurationParams_appliesParams() {
        ViewConfigurationParams viewConfigurationParams = new ViewConfigurationParams.Builder()
                .setTapTimeoutDuration(Duration.ofMillis(10L))
                .setDoubleTapTimeoutDuration(Duration.ofMillis(10L))
                .setDoubleTapMinTimeDuration(Duration.ofMillis(10L))
                .setScrollFriction(10f)
                .setMinimumFlingVelocityPixelsPerSecond(10)
                .setMaximumFlingVelocityPixelsPerSecond(10)
                .setTouchSlopPixels(10)
                .build();

        VirtualDeviceImpl virtualDevice = createVirtualDevice(VIRTUAL_DEVICE_ID_1,
                DEVICE_OWNER_UID_1);
        virtualDevice.applyViewConfigurationParams(viewConfigurationParams);

        verify(mViewConfigurationControllerMock).applyViewConfigurationParams(
                eq(VIRTUAL_DEVICE_ID_1), eq(viewConfigurationParams));
    }

    @Test
    public void closeVirtualDevice_closesViewConfigurationController() {
        mDeviceImpl.close();
        verify(mViewConfigurationControllerMock).close();
    }

    private VirtualDeviceImpl createVirtualDevice(int virtualDeviceId, int ownerUid) {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setBlockedActivities(BLOCKED_ACTIVITIES)
                .build();
        return createVirtualDevice(virtualDeviceId, ownerUid, params);
    }

    private VirtualDeviceImpl createVirtualDevice(int virtualDeviceId, int ownerUid,
            VirtualDeviceParams params) {
        VirtualDeviceImpl virtualDeviceImpl =
                new VirtualDeviceImpl(
                        mContext,
                        mAssociationInfo,
                        mVdms,
                        mVirtualDeviceLog,
                        new Binder(),
                        new AttributionSource(
                                ownerUid, VIRTUAL_DEVICE_OWNER_PACKAGE, "virtualdevice"),
                        virtualDeviceId,
                        mInputController,
                        mCameraAccessController,
                        mPendingTrampolineCallback,
                        mActivityListener,
                        mSoundEffectListener,
                        params,
                        new DisplayManagerGlobal(mIDisplayManager),
                        new VirtualCameraController(DEVICE_POLICY_DEFAULT, virtualDeviceId),
                        mViewConfigurationControllerMock);
        mVdms.addVirtualDevice(virtualDeviceImpl);
        assertThat(virtualDeviceImpl.getAssociationId()).isEqualTo(mAssociationInfo.getId());
        assertThat(virtualDeviceImpl.getPersistentDeviceId())
                .isEqualTo("companion:" + mAssociationInfo.getId());
        return virtualDeviceImpl;
    }

    private void addVirtualDisplay(VirtualDeviceImpl virtualDevice, int displayId) {
        addVirtualDisplay(virtualDevice, displayId, /* flags= */ 0);
    }

    private void addVirtualDisplay(VirtualDeviceImpl virtualDevice, int displayId, int flags) {
        final String uniqueId = UNIQUE_ID + displayId;
        doAnswer(inv -> {
            final DisplayInfo displayInfo = new DisplayInfo();
            displayInfo.uniqueId = uniqueId;
            displayInfo.flags = flags;
            return displayInfo;
        }).when(mDisplayManagerInternalMock).getDisplayInfo(eq(displayId));

        when(mDisplayManagerInternalMock.createVirtualDisplay(any(), eq(mVirtualDisplayCallback),
                eq(virtualDevice), any(), any(), anyInt())).thenAnswer(inv -> {
                    mLocalService.onVirtualDisplayCreated(
                            virtualDevice, displayId, mVirtualDisplayCallback, inv.getArgument(3));
                    return displayId;
                });

        final int virtualDisplayFlags = (flags & Display.FLAG_TRUSTED) == 0
                ? 0
                : DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED;
        VirtualDisplayConfig virtualDisplayConfig =
                new VirtualDisplayConfig.Builder("virtual_display", 640, 480, 400)
                        .setFlags(virtualDisplayFlags)
                        .build();
        virtualDevice.createVirtualDisplay(virtualDisplayConfig, mVirtualDisplayCallback);
    }

    private AssociationInfo createAssociationInfo(int associationId, String deviceProfile) {
        return createAssociationInfo(
                associationId, deviceProfile, /* displayName= */ deviceProfile);
    }

    private AssociationInfo createAssociationInfo(
            int associationId, String deviceProfile, CharSequence displayName) {
        return new AssociationInfo.Builder(associationId, /* userId= */ 0, /* packageName= */ null)
                .setDeviceMacAddress(MacAddress.BROADCAST_ADDRESS)
                .setDisplayName(displayName)
                .setDeviceProfile(deviceProfile)
                .setSelfManaged(true)
                .setTimeApproved(0)
                .setLastTimeConnected(0)
                .build();
    }
}
