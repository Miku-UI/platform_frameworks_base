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

package com.android.server.display;

import static android.hardware.devicestate.DeviceStateManager.INVALID_DEVICE_STATE;
import static android.hardware.devicestate.feature.flags.Flags.FLAG_DEVICE_STATE_PROPERTY_MIGRATION;
import static android.os.PowerManager.GO_TO_SLEEP_REASON_DEVICE_FOLD;
import static android.os.PowerManager.GO_TO_SLEEP_REASON_DISPLAY_GROUP_REMOVED;
import static android.os.PowerManager.GO_TO_SLEEP_REASON_LID_SWITCH;
import static android.os.PowerManager.GO_TO_SLEEP_REASON_UNKNOWN;
import static android.os.PowerManager.WAKE_REASON_LID;
import static android.os.PowerManager.WAKE_REASON_UNFOLD_DEVICE;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.DEFAULT_DISPLAY_GROUP;
import static android.view.Display.FLAG_REAR;
import static android.view.Display.STATE_OFF;
import static android.view.Display.STATE_ON;
import static android.view.Display.TYPE_EXTERNAL;
import static android.view.Display.TYPE_INTERNAL;
import static android.view.Display.TYPE_OVERLAY;
import static android.view.Display.TYPE_VIRTUAL;

import static com.android.server.display.DeviceStateToLayoutMap.STATE_DEFAULT;
import static com.android.server.display.DisplayAdapter.DISPLAY_DEVICE_EVENT_ADDED;
import static com.android.server.display.DisplayAdapter.DISPLAY_DEVICE_EVENT_CHANGED;
import static com.android.server.display.DisplayAdapter.DISPLAY_DEVICE_EVENT_REMOVED;
import static com.android.server.display.DisplayDeviceInfo.DIFF_EVERYTHING;
import static com.android.server.display.DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY;
import static com.android.server.display.DisplayDeviceInfo.FLAG_ALLOWS_CONTENT_MODE_SWITCH;
import static com.android.server.display.LogicalDisplayMapper.LOGICAL_DISPLAY_EVENT_ADDED;
import static com.android.server.display.LogicalDisplayMapper.LOGICAL_DISPLAY_EVENT_BASIC_CHANGED;
import static com.android.server.display.LogicalDisplayMapper.LOGICAL_DISPLAY_EVENT_COMMITTED_STATE_CHANGED;
import static com.android.server.display.LogicalDisplayMapper.LOGICAL_DISPLAY_EVENT_CONNECTED;
import static com.android.server.display.LogicalDisplayMapper.LOGICAL_DISPLAY_EVENT_DISCONNECTED;
import static com.android.server.display.LogicalDisplayMapper.LOGICAL_DISPLAY_EVENT_REFRESH_RATE_CHANGED;
import static com.android.server.display.LogicalDisplayMapper.LOGICAL_DISPLAY_EVENT_REMOVED;
import static com.android.server.display.LogicalDisplayMapper.LOGICAL_DISPLAY_EVENT_STATE_CHANGED;
import static com.android.server.display.TestUtilsKt.createTestDisplayAddress;
import static com.android.server.display.feature.flags.Flags.FLAG_CHANGE_DEFAULT_DISPLAY_LID_CLOSED;
import static com.android.server.display.layout.Layout.Display.POSITION_REAR;
import static com.android.server.display.layout.Layout.Display.POSITION_UNKNOWN;
import static com.android.server.display.persistence.PersistentDataStoreTestUtilsKt.createInMemoryPersistentDataStore;
import static com.android.server.utils.FoldSettingProvider.SETTING_VALUE_SELECTIVE_STAY_AWAKE;
import static com.android.server.utils.FoldSettingProvider.SETTING_VALUE_SLEEP_ON_FOLD;
import static com.android.server.utils.FoldSettingProvider.SETTING_VALUE_STAY_AWAKE_ON_FOLD;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.PropertyInvalidatedCache;
import android.content.Context;
import android.content.res.Resources;
import android.hardware.devicestate.DeviceState;
import android.os.Handler;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.test.TestLooper;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.CopyOnWriteSparseArray;
import android.view.Display;
import android.view.DisplayAddress;
import android.view.DisplayInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.util.test.LocalServiceKeeperRule;
import com.android.server.display.feature.DisplayManagerFlags;
import com.android.server.display.feature.flags.Flags;
import com.android.server.display.layout.DisplayIdProducer;
import com.android.server.display.layout.Layout;
import com.android.server.policy.WindowManagerPolicy;
import com.android.server.utils.FoldSettingProvider;
import com.android.server.wm.DesktopModeHelper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class LogicalDisplayMapperTest {
    private static int sUniqueTestDisplayId = 0;
    private static final int TIMEOUT_STATE_TRANSITION_MILLIS = 500;
    private static final int STATE_TRANSITION_DELAY = 1000;

    private static final DeviceState DEVICE_STATE_FOLDABLE_CLOSED = createDeviceState(0,
            "Foldable closed", Set.of(DeviceState.PROPERTY_POWER_CONFIGURATION_TRIGGER_SLEEP,
                    DeviceState.PROPERTY_FOLDABLE_HARDWARE_CONFIGURATION_FOLD_IN_CLOSED,
                    DeviceState.PROPERTY_FOLDABLE_DISPLAY_CONFIGURATION_OUTER_PRIMARY),
            Collections.emptySet());
    private static final DeviceState DEVICE_STATE_FOLDABLE_HALF_OPEN = createDeviceState(1,
            "Foldable half-open", Set.of(DeviceState.PROPERTY_POWER_CONFIGURATION_TRIGGER_WAKE,
                    DeviceState.PROPERTY_FOLDABLE_HARDWARE_CONFIGURATION_FOLD_IN_HALF_OPEN,
                    DeviceState.PROPERTY_FOLDABLE_DISPLAY_CONFIGURATION_INNER_PRIMARY),
            Collections.emptySet());
    private static final DeviceState DEVICE_STATE_FOLDABLE_OPEN = createDeviceState(2,
            "Foldable open", Set.of(DeviceState.PROPERTY_POWER_CONFIGURATION_TRIGGER_WAKE,
                    DeviceState.PROPERTY_FOLDABLE_HARDWARE_CONFIGURATION_FOLD_IN_OPEN,
                    DeviceState.PROPERTY_FOLDABLE_DISPLAY_CONFIGURATION_INNER_PRIMARY),
            Collections.emptySet());
    private static final DeviceState DEVICE_STATE_EMULATED = createDeviceState(3, "Emulated",
            Set.of(DeviceState.PROPERTY_EMULATED_ONLY), Collections.emptySet());
    private static final DeviceState DEVICE_STATE_DOCKED = createDeviceState(4, "Docked",
            Set.of(DeviceState.PROPERTY_LAPTOP_HARDWARE_CONFIGURATION_DOCKED),
            Collections.emptySet());

    private static final DeviceState DEVICE_STATE_LID_OPEN = createDeviceState(5, "Lid open",
            Set.of(DeviceState.PROPERTY_POWER_CONFIGURATION_TRIGGER_WAKE,
                    DeviceState.PROPERTY_LAPTOP_HARDWARE_CONFIGURATION_LID_OPEN),
            Collections.emptySet());
    private static final DeviceState DEVICE_STATE_LID_CLOSED = createDeviceState(6, "Lid closed",
            Set.of(DeviceState.PROPERTY_POWER_CONFIGURATION_TRIGGER_SLEEP,
                    DeviceState.PROPERTY_LAPTOP_HARDWARE_CONFIGURATION_LID_CLOSED),
            Collections.emptySet());

    private static int sNextNonDefaultDisplayId = DEFAULT_DISPLAY + 1;
    private static final File NON_EXISTING_FILE = new File("/non_existing_folder/should_not_exist");

    private DisplayDeviceRepository mDisplayDeviceRepo;
    private LogicalDisplayMapper mLogicalDisplayMapper;
    private TestLooper mLooper;
    private Handler mHandler;

    private final DisplayIdProducer mIdProducer = (isDefault) ->
            isDefault ? DEFAULT_DISPLAY : sNextNonDefaultDisplayId++;

    private DeviceStateToLayoutMap mDeviceStateToLayoutMapSpy;
    private DisplayGroupAllocator mDisplayGroupAllocatorSpy;

    @Rule
    public LocalServiceKeeperRule mLocalServiceKeeperRule = new LocalServiceKeeperRule();
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock LogicalDisplayMapper.Listener mListenerMock;
    @Mock Context mContextMock;
    @Mock FoldSettingProvider mFoldSettingProviderMock;
    @Mock Resources mResourcesMock;
    @Mock PowerManager mPowerManagerMock;
    @Mock DisplayManagerFlags mFlagsMock;
    @Mock DisplayAdapter mDisplayAdapterMock;
    @Mock WindowManagerPolicy mWindowManagerPolicy;
    @Mock
    Predicate<DisplayInfo> mIsDisplayAllowedInTopologyMock;
    @Mock
    private ModeRequestManager mModeRequestManager;
    @Mock
    CopyOnWriteSparseArray<LogicalDisplay.CachedDisplayInfo> mDisplayInfoCacheMock;

    @Captor ArgumentCaptor<LogicalDisplay> mDisplayCaptor;
    @Captor ArgumentCaptor<Integer> mDisplayEventCaptor;

    @Before
    public void setUp() throws RemoteException {
        // Share classloader to allow package private access.
        System.setProperty("dexmaker.share_classloader", "true");
        MockitoAnnotations.initMocks(this);

        mLocalServiceKeeperRule.overrideLocalService(WindowManagerPolicy.class,
                mWindowManagerPolicy);

        mDeviceStateToLayoutMapSpy =
                spy(new DeviceStateToLayoutMap(mIdProducer, NON_EXISTING_FILE, true));

        mDisplayDeviceRepo = new DisplayDeviceRepository(new DisplayManagerService.SyncRoot(),
                createInMemoryPersistentDataStore(), /* stableEdidsFlag= */ true);

        // Disable binder caches in this process.
        PropertyInvalidatedCache.disableForTestMode();

        when(mContextMock.getSystemServiceName(PowerManager.class))
                .thenReturn(Context.POWER_SERVICE);
        setFoldLockBehaviorSettingValue(SETTING_VALUE_SELECTIVE_STAY_AWAKE);
        when(mPowerManagerMock.isInteractive()).thenReturn(true);
        when(mContextMock.getSystemService(PowerManager.class)).thenReturn(mPowerManagerMock);
        when(mContextMock.getResources()).thenReturn(mResourcesMock);
        when(mResourcesMock.getBoolean(
                com.android.internal.R.bool.config_supportsConcurrentInternalDisplays))
                .thenReturn(true);
        when(mResourcesMock.getIntArray(
                com.android.internal.R.array.config_deviceStatesOnWhichToWakeUp))
                .thenReturn(new int[]{1, 2});
        when(mResourcesMock.getIntArray(
                com.android.internal.R.array.config_deviceStatesOnWhichToSleep))
                .thenReturn(new int[]{0});

        mLooper = new TestLooper();
        mHandler = new Handler(mLooper.getLooper());

        setDisplayGroupAllocator(true);
    }

    /////////////////
    // Test Methods
    /////////////////

    @Test
    public void testDisplayDeviceAddAndRemove_Internal() {
        initLogicalDisplayMapper();
        DisplayDevice device = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);

        // add
        LogicalDisplay displayAdded = add(device);
        assertEquals(info(displayAdded).address, info(device).address);
        assertEquals(DEFAULT_DISPLAY, id(displayAdded));

        // remove
        mDisplayDeviceRepo.onDisplayDeviceEvent(device, DISPLAY_DEVICE_EVENT_REMOVED);
        verify(mListenerMock).onLogicalDisplayEventLocked(
                mDisplayCaptor.capture(), eq(LOGICAL_DISPLAY_EVENT_REMOVED));
        LogicalDisplay displayRemoved = mDisplayCaptor.getValue();
        assertEquals(DEFAULT_DISPLAY, id(displayRemoved));
        assertEquals(displayAdded, displayRemoved);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_COMMITTED_STATE_SEPARATE_EVENT)
    public void test_committedStateChanged_eventEmitted() {
        when(mFlagsMock.isCommittedStateSeparateEventEnabled()).thenReturn(true);
        initLogicalDisplayMapper();
        TestDisplayDevice device = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);

        // add
        LogicalDisplay displayAdded = add(device);
        assertEquals(info(displayAdded).address, info(device).address);
        assertEquals(DEFAULT_DISPLAY, id(displayAdded));

        device.getSourceInfo().committedState = STATE_ON;
        mDisplayDeviceRepo.onDisplayDeviceEvent(device, DISPLAY_DEVICE_EVENT_CHANGED);
        verify(mListenerMock).onLogicalDisplayEventLocked(
                mDisplayCaptor.capture(), eq(LOGICAL_DISPLAY_EVENT_COMMITTED_STATE_CHANGED));
    }

    @Test
    public void testDisplayDeviceAddAndRemove_NonInternalTypes_displayInfoCacheEnabled() {
        boolean infoCacheEnabled = true;
        initLogicalDisplayMapper();
        testDisplayDeviceAddAndRemove_NonInternal(TYPE_EXTERNAL, infoCacheEnabled);
        testDisplayDeviceAddAndRemove_NonInternal(Display.TYPE_WIFI, infoCacheEnabled);
        testDisplayDeviceAddAndRemove_NonInternal(TYPE_OVERLAY, infoCacheEnabled);
        testDisplayDeviceAddAndRemove_NonInternal(TYPE_VIRTUAL, infoCacheEnabled);
        testDisplayDeviceAddAndRemove_NonInternal(Display.TYPE_UNKNOWN, infoCacheEnabled);

        // Call the internal test again, just to verify that adding non-internal displays
        // doesn't affect the ability for an internal display to become the default display.
        testDisplayDeviceAddAndRemove_Internal_Helper(infoCacheEnabled);
    }

    @Test
    public void testDisplayDeviceAdd_TwoInternalOneDefault() {
        initLogicalDisplayMapper();
        DisplayDevice device1 = createDisplayDevice(TYPE_INTERNAL, 600, 800, 0);
        DisplayDevice device2 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);

        LogicalDisplay display1 = add(device1);
        assertEquals(info(display1).address, info(device1).address);
        assertNotEquals(DEFAULT_DISPLAY, id(display1));

        LogicalDisplay display2 = add(device2);
        assertEquals(info(display2).address, info(device2).address);
        assertEquals(DEFAULT_DISPLAY, id(display2));
    }

    @Test
    public void testDisplayDeviceAdd_TwoInternalBothDefault() {
        initLogicalDisplayMapper();
        DisplayDevice device1 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        DisplayDevice device2 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);

        LogicalDisplay display1 = add(device1);
        assertEquals(info(display1).address, info(device1).address);
        assertEquals(DEFAULT_DISPLAY, id(display1));

        LogicalDisplay display2 = add(device2);
        assertEquals(info(display2).address, info(device2).address);
        // Despite the flags, we can only have one default display
        assertNotEquals(DEFAULT_DISPLAY, id(display2));
    }

    @Test
    public void testDisplayDeviceAddAndRemove_OneExternalDefault() {
        initLogicalDisplayMapper();
        DisplayDevice device = createDisplayDevice(TYPE_EXTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);

        // add
        LogicalDisplay displayAdded = add(device);
        assertEquals(info(displayAdded).address, info(device).address);
        assertEquals(Display.DEFAULT_DISPLAY, id(displayAdded));

        // remove
        mDisplayDeviceRepo.onDisplayDeviceEvent(device, DISPLAY_DEVICE_EVENT_REMOVED);
        verify(mListenerMock).onLogicalDisplayEventLocked(
                mDisplayCaptor.capture(), eq(LOGICAL_DISPLAY_EVENT_REMOVED));
        LogicalDisplay displayRemoved = mDisplayCaptor.getValue();
        assertEquals(DEFAULT_DISPLAY, id(displayRemoved));
        assertEquals(displayAdded, displayRemoved);
    }

    @Test
    public void testDisplayDeviceAddAndRemove_SwitchDefault() {
        initLogicalDisplayMapper();
        DisplayDevice device1 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        DisplayDevice device2 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);

        LogicalDisplay display1 = add(device1);
        assertEquals(info(display1).address, info(device1).address);
        assertEquals(DEFAULT_DISPLAY, id(display1));

        LogicalDisplay display2 = add(device2);
        assertEquals(info(display2).address, info(device2).address);
        // We can only have one default display
        assertEquals(DEFAULT_DISPLAY, id(display1));

        // remove
        mDisplayDeviceRepo.onDisplayDeviceEvent(device1, DISPLAY_DEVICE_EVENT_REMOVED);

        verify(mListenerMock).onLogicalDisplayEventLocked(
                mDisplayCaptor.capture(), eq(LOGICAL_DISPLAY_EVENT_REMOVED));
        LogicalDisplay displayRemoved = mDisplayCaptor.getValue();
        // Display 1 is still the default logical display
        assertEquals(DEFAULT_DISPLAY, id(display1));
        // The logical displays had their devices swapped and Display 2 was removed
        assertEquals(display2, displayRemoved);
        assertEquals(info(display1).address, info(device2).address);
    }

    @Test
    @EnableFlags(FLAG_CHANGE_DEFAULT_DISPLAY_LID_CLOSED)
    public void testRemoveDefaultSecondaryDisplay_SwitchDefaultToOtherSecondaryDisplay() {
        initLogicalDisplayMapper();
        when(mIsDisplayAllowedInTopologyMock.test(any(DisplayInfo.class))).thenReturn(true);
        DisplayDevice device1 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        DisplayDevice device2 = createDisplayDevice(TYPE_EXTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        DisplayDevice device3 = createDisplayDevice(TYPE_EXTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);

        LogicalDisplay display1 = add(device1);
        display1.setCanHostTasksLocked(true);
        assertEquals(info(display1).address, info(device1).address);
        assertEquals(DEFAULT_DISPLAY, id(display1));

        LogicalDisplay display2 = add(device2);
        display2.setCanHostTasksLocked(true);
        assertEquals(info(display2).address, info(device2).address);
        // We can only have one default display
        assertEquals(DEFAULT_DISPLAY, id(display1));

        LogicalDisplay display3 = add(device3);
        display3.setCanHostTasksLocked(true);
        assertEquals(info(display3).address, info(device3).address);
        // We can only have one default display
        assertEquals(DEFAULT_DISPLAY, id(display1));

        mLogicalDisplayMapper.onBootCompleted();
        mLogicalDisplayMapper.setDeviceState(DEVICE_STATE_DOCKED);
        advanceTime(1000);
        assertEquals(device2, display1.getPrimaryDisplayDeviceLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device1).isEnabledLocked());
        assertTrue(mLogicalDisplayMapper.getDisplayLocked(device2).isEnabledLocked());

        // remove
        mDisplayDeviceRepo.onDisplayDeviceEvent(device2, DISPLAY_DEVICE_EVENT_REMOVED);

        // Display 1 is still the default logical display
        assertEquals(DEFAULT_DISPLAY, id(display1));
        // Device 3 is now associated with the default display
        assertEquals(device3, display1.getPrimaryDisplayDeviceLocked());
        assertNull(mLogicalDisplayMapper.getDisplayLocked(device2));
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device1).isEnabledLocked());
        assertTrue(mLogicalDisplayMapper.getDisplayLocked(device3).isEnabledLocked());

        verify(mPowerManagerMock, never()).wakeUp(anyLong(), anyInt(), any());
        verify(mPowerManagerMock, never()).goToSleep(anyLong(), anyInt(), anyInt());
    }

    @Test
    @EnableFlags(FLAG_CHANGE_DEFAULT_DISPLAY_LID_CLOSED)
    public void testRemoveDefaultSecondaryDisplay_SwitchDefaultToInternalDisplay() {
        initLogicalDisplayMapper();
        when(mIsDisplayAllowedInTopologyMock.test(any(DisplayInfo.class))).thenReturn(true);
        DisplayDevice device1 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        DisplayDevice device2 = createDisplayDevice(TYPE_EXTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);

        LogicalDisplay display1 = add(device1);
        display1.setCanHostTasksLocked(true);
        assertEquals(info(display1).address, info(device1).address);
        assertEquals(DEFAULT_DISPLAY, id(display1));

        LogicalDisplay display2 = add(device2);
        display2.setCanHostTasksLocked(true);
        assertEquals(info(display2).address, info(device2).address);
        // We can only have one default display
        assertEquals(DEFAULT_DISPLAY, id(display1));

        mLogicalDisplayMapper.onBootCompleted();
        mLogicalDisplayMapper.setDeviceState(DEVICE_STATE_DOCKED);
        advanceTime(1000);
        assertEquals(device2, display1.getPrimaryDisplayDeviceLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device1).isEnabledLocked());
        assertTrue(mLogicalDisplayMapper.getDisplayLocked(device2).isEnabledLocked());

        // remove
        mDisplayDeviceRepo.onDisplayDeviceEvent(device2, DISPLAY_DEVICE_EVENT_REMOVED);
        advanceTime(1000);

        // Display 1 is still the default logical display
        assertEquals(DEFAULT_DISPLAY, id(display1));
        // Device 1 is now associated with the default display
        assertEquals(device1, display1.getPrimaryDisplayDeviceLocked());
        assertNull(mLogicalDisplayMapper.getDisplayLocked(device2));
        assertTrue(mLogicalDisplayMapper.getDisplayLocked(device1).isEnabledLocked());

        // The device should go to sleep
        verify(mPowerManagerMock, never()).wakeUp(anyLong(), anyInt(), any());
        verify(mPowerManagerMock).goToSleep(anyLong(), eq(GO_TO_SLEEP_REASON_DISPLAY_GROUP_REMOVED),
                /* flags= */ eq(0));
    }

    @Test
    public void testDisplayDeviceAddAndRemove() {
        initLogicalDisplayMapper();
        DisplayDevice device = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);

        // add
        mDisplayDeviceRepo.onDisplayDeviceEvent(device, DISPLAY_DEVICE_EVENT_ADDED);

        verify(mListenerMock, times(2)).onLogicalDisplayEventLocked(
                mDisplayCaptor.capture(), mDisplayEventCaptor.capture());
        LogicalDisplay added = mDisplayCaptor.getAllValues().get(0);
        assertThat(mDisplayCaptor.getAllValues().get(1)).isEqualTo(added);
        LogicalDisplay displayAdded = add(device);
        assertThat(info(displayAdded).address).isEqualTo(info(device).address);
        assertThat(id(displayAdded)).isEqualTo(DEFAULT_DISPLAY);
        assertThat(mDisplayEventCaptor.getAllValues()).containsExactly(
                LOGICAL_DISPLAY_EVENT_CONNECTED, LOGICAL_DISPLAY_EVENT_ADDED).inOrder();
        clearInvocations(mListenerMock);

        // remove
        mDisplayDeviceRepo.onDisplayDeviceEvent(device, DISPLAY_DEVICE_EVENT_REMOVED);
        verify(mListenerMock, times(2)).onLogicalDisplayEventLocked(
                mDisplayCaptor.capture(), mDisplayEventCaptor.capture());
        List<Integer> allEvents = mDisplayEventCaptor.getAllValues();
        int numEvents = allEvents.size();
        // Only extract the last two events
        List<Integer> events = new ArrayList(2);
        events.add(allEvents.get(numEvents - 2));
        events.add(allEvents.get(numEvents - 1));
        assertThat(events).containsExactly(
                LOGICAL_DISPLAY_EVENT_REMOVED, LOGICAL_DISPLAY_EVENT_DISCONNECTED).inOrder();
        List<LogicalDisplay> displays = mDisplayCaptor.getAllValues();
        LogicalDisplay displayRemoved = displays.get(numEvents - 2);
        assertThat(displays.get(numEvents - 1)).isEqualTo(displayRemoved);
        assertThat(id(displayRemoved)).isEqualTo(DEFAULT_DISPLAY);
        assertThat(displayRemoved).isEqualTo(displayAdded);
    }

    @Test
    public void testDisplayDisableEnable() {
        initLogicalDisplayMapper();
        DisplayDevice device = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        LogicalDisplay displayAdded = add(device);
        assertThat(displayAdded.isEnabledLocked()).isTrue();

        // Disable device
        mLogicalDisplayMapper.setDisplayEnabledLocked(
                displayAdded, /* isEnabled= */ false);
        verify(mListenerMock).onLogicalDisplayEventLocked(mDisplayCaptor.capture(),
                eq(LOGICAL_DISPLAY_EVENT_REMOVED));
        clearInvocations(mListenerMock);

        // Enable device
        mLogicalDisplayMapper.setDisplayEnabledLocked(
                displayAdded, /* isEnabled= */ true);
        verify(mListenerMock).onLogicalDisplayEventLocked(mDisplayCaptor.capture(),
                eq(LOGICAL_DISPLAY_EVENT_ADDED));
    }

    @Test
    public void testGetDisplayIdsLocked() {
        initLogicalDisplayMapper();
        add(createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY));
        add(createDisplayDevice(TYPE_EXTERNAL, 600, 800, 0));
        add(createDisplayDevice(TYPE_VIRTUAL, 600, 800, 0));

        int [] ids = mLogicalDisplayMapper.getDisplayIdsLocked(Process.SYSTEM_UID,
                /* includeDisabled= */ true);
        assertEquals(3, ids.length);
        Arrays.sort(ids);
        assertEquals(DEFAULT_DISPLAY, ids[0]);
    }

    @Test
    public void testGetDisplayInfoForStateLocked_defaultLayout() {
        initLogicalDisplayMapper();
        final DisplayDevice device1 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        final DisplayDevice device2 = createDisplayDevice(TYPE_INTERNAL, 200, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);

        add(device1);
        add(device2);

        Layout layout1 = new Layout();
        createDefaultDisplay(layout1, device1);
        createNonDefaultDisplay(layout1, device2, /* enabled= */ true, /* group= */ null);
        when(mDeviceStateToLayoutMapSpy.get(STATE_DEFAULT)).thenReturn(layout1);
        assertThat(layout1.size()).isEqualTo(2);
        final int logicalId2 = layout1.getByAddress(info(device2).address).getLogicalDisplayId();

        final DisplayInfo displayInfoDefault = mLogicalDisplayMapper.getDisplayInfoForStateLocked(
                STATE_DEFAULT, DEFAULT_DISPLAY);
        assertThat(displayInfoDefault.displayId).isEqualTo(DEFAULT_DISPLAY);
        assertThat(displayInfoDefault.logicalWidth).isEqualTo(width(device1));
        assertThat(displayInfoDefault.logicalHeight).isEqualTo(height(device1));

        final DisplayInfo displayInfoOther = mLogicalDisplayMapper.getDisplayInfoForStateLocked(
                STATE_DEFAULT, logicalId2);
        assertThat(displayInfoOther).isNotNull();
        assertThat(displayInfoOther.displayId).isEqualTo(logicalId2);
        assertThat(displayInfoOther.logicalWidth).isEqualTo(width(device2));
        assertThat(displayInfoOther.logicalHeight).isEqualTo(height(device2));
    }

    @Test
    public void testGetDisplayInfoForStateLocked_multipleLayouts() {
        initLogicalDisplayMapper();
        final DisplayDevice device1 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        final DisplayDevice device2 = createDisplayDevice(TYPE_INTERNAL, 200, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        final DisplayDevice device3 = createDisplayDevice(TYPE_VIRTUAL, 700, 800,
                DisplayDeviceInfo.FLAG_OWN_DISPLAY_GROUP);

        add(device1);
        add(device2);
        add(device3);

        Layout layout1 = new Layout();
        createDefaultDisplay(layout1, device1);
        when(mDeviceStateToLayoutMapSpy.get(STATE_DEFAULT)).thenReturn(layout1);

        final int layoutState2 = 2;
        Layout layout2 = new Layout();
        createNonDefaultDisplay(layout2, device2, /* enabled= */ true, /* group= */ null);
        // Device3 is the default display.
        createDefaultDisplay(layout2, device3);
        when(mDeviceStateToLayoutMapSpy.get(layoutState2)).thenReturn(layout2);
        assertThat(layout2.size()).isEqualTo(2);
        final int logicalId2 = layout2.getByAddress(info(device2).address).getLogicalDisplayId();

        // Default layout.
        final DisplayInfo displayInfoLayout1Default =
                mLogicalDisplayMapper.getDisplayInfoForStateLocked(
                        STATE_DEFAULT, DEFAULT_DISPLAY);
        assertThat(displayInfoLayout1Default.displayId).isEqualTo(DEFAULT_DISPLAY);
        assertThat(displayInfoLayout1Default.logicalWidth).isEqualTo(width(device1));
        assertThat(displayInfoLayout1Default.logicalHeight).isEqualTo(height(device1));

        // Second layout, where device3 is the default display.
        final DisplayInfo displayInfoLayout2Default =
                mLogicalDisplayMapper.getDisplayInfoForStateLocked(
                        layoutState2, DEFAULT_DISPLAY);
        assertThat(displayInfoLayout2Default.displayId).isEqualTo(DEFAULT_DISPLAY);
        assertThat(displayInfoLayout2Default.logicalWidth).isEqualTo(width(device3));
        assertThat(displayInfoLayout2Default.logicalHeight).isEqualTo(height(device3));

        final DisplayInfo displayInfoLayout2Other =
                mLogicalDisplayMapper.getDisplayInfoForStateLocked(
                        layoutState2, logicalId2);
        assertThat(displayInfoLayout2Other).isNotNull();
        assertThat(displayInfoLayout2Other.displayId).isEqualTo(logicalId2);
        assertThat(displayInfoLayout2Other.logicalWidth).isEqualTo(width(device2));
        assertThat(displayInfoLayout2Other.logicalHeight).isEqualTo(height(device2));
    }

    @Test
    public void testGetDisplayInfoForStateLocked_multipleDisplayGroups() {
        initLogicalDisplayMapper();
        DisplayDevice device1 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        DisplayDevice device2 = createDisplayDevice(TYPE_INTERNAL, 200, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        DisplayDevice device3 = createDisplayDevice(TYPE_INTERNAL, 700, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        DisplayDevice device4 = createDisplayDevice(TYPE_INTERNAL, 400, 600,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);

        Layout layout = new Layout();
        createDefaultDisplay(layout, device1);
        createNonDefaultDisplay(layout, device2, /* enabled= */  true, /* group= */ "group1");
        createNonDefaultDisplay(layout, device3, /* enabled= */  true, /* group= */ "group1");
        createNonDefaultDisplay(layout, device4, /* enabled= */  true, /* group= */ "group2");

        when(mDeviceStateToLayoutMapSpy.get(STATE_DEFAULT)).thenReturn(layout);

        LogicalDisplay display1 = add(device1);
        LogicalDisplay display2 = add(device2);
        LogicalDisplay display3 = add(device3);
        LogicalDisplay display4 = add(device4);

        int displayGroupId1 =
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(id(display1));
        int displayGroupId2 =
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(id(display2));
        int displayGroupId3 =
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(id(display3));
        int displayGroupId4 =
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(id(display4));
        assertThat(displayGroupId1).isEqualTo(DEFAULT_DISPLAY_GROUP);
        assertThat(displayGroupId2).isNotEqualTo(DEFAULT_DISPLAY_GROUP);
        assertThat(displayGroupId2).isEqualTo(displayGroupId3);
        assertThat(displayGroupId3).isNotEqualTo(DEFAULT_DISPLAY_GROUP);
        assertThat(displayGroupId2).isNotEqualTo(displayGroupId4);
    }

    @Test
    public void testSingleDisplayGroup() {
        initLogicalDisplayMapper();
        LogicalDisplay display1 = add(createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY));
        LogicalDisplay display2 = add(createDisplayDevice(TYPE_INTERNAL, 600, 800, 0));
        LogicalDisplay display3 = add(createDisplayDevice(TYPE_VIRTUAL, 600, 800, 0));

        assertEquals(DEFAULT_DISPLAY_GROUP,
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(id(display1)));
        assertEquals(DEFAULT_DISPLAY_GROUP,
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(id(display2)));
        assertEquals(DEFAULT_DISPLAY_GROUP,
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(id(display3)));
    }

    @Test
    public void testOverlayDisplayInDefaultGroup() {
        initLogicalDisplayMapper();
        LogicalDisplay display1 = add(createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY));
        LogicalDisplay display2 = add(createDisplayDevice(TYPE_OVERLAY, 600, 800, 0));

        assertEquals(DEFAULT_DISPLAY_GROUP,
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(id(display1)));
        assertEquals(DEFAULT_DISPLAY_GROUP,
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(id(display2)));
    }

    @Test
    public void testMultipleDisplayGroups() {
        initLogicalDisplayMapper();
        LogicalDisplay display1 = add(createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY));
        LogicalDisplay display2 = add(createDisplayDevice(TYPE_INTERNAL, 600, 800, 0));


        TestDisplayDevice device3 = createDisplayDevice(TYPE_VIRTUAL, 600, 800,
                DisplayDeviceInfo.FLAG_OWN_DISPLAY_GROUP);
        LogicalDisplay display3 = add(device3);

        assertEquals(DEFAULT_DISPLAY_GROUP,
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(id(display1)));
        assertEquals(DEFAULT_DISPLAY_GROUP,
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(id(display2)));
        assertNotEquals(DEFAULT_DISPLAY_GROUP,
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(id(display3)));

        // Now switch it back to the default group by removing the flag and issuing an update
        DisplayDeviceInfo info = device3.getSourceInfo();
        info.flags = info.flags & ~DisplayDeviceInfo.FLAG_OWN_DISPLAY_GROUP;
        mDisplayDeviceRepo.onDisplayDeviceEvent(device3, DISPLAY_DEVICE_EVENT_CHANGED);

        // Verify the new group is correct.
        assertEquals(DEFAULT_DISPLAY_GROUP,
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(id(display3)));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_SEPARATE_TIMEOUTS)
    public void testProjectedModeDifferentDisplayGroups() {
        assumeTrue(DesktopModeHelper.canEnterDesktopMode(mContextMock));

        doReturn(true).when(mFlagsMock).isSeparateTimeoutsEnabled();
        setDisplayGroupAllocator(false);
        initLogicalDisplayMapper();


        DisplayDevice device1 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        LogicalDisplay logicalDisplay1 = add(device1);
        TestDisplayDevice device2 = createDisplayDevice(TYPE_EXTERNAL, 200, 800,
                /* flags= */ 0);
        LogicalDisplay logicalDisplay2 = add(device2);

        doReturn(true).when(mFlagsMock).isSeparateTimeoutsEnabled();
        doReturn("secondary_mode")
                .when(mDisplayGroupAllocatorSpy)
                .decideRequiredGroupTypeLocked(logicalDisplay2, TYPE_EXTERNAL);

        assertEquals(DEFAULT_DISPLAY_GROUP,
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(id(logicalDisplay1)));
        assertNotEquals(DEFAULT_DISPLAY_GROUP,
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(id(logicalDisplay2)));
    }

    @Test
    public void testExtendedModeSameDisplayGroups() {
        reset(mDisplayGroupAllocatorSpy);
        setDisplayGroupAllocator(true);
        initLogicalDisplayMapper();
    }

    @Test
    public void testDevicesAreAddedToDeviceDisplayGroups() {
        initLogicalDisplayMapper();
        // Create the default internal display of the device.
        LogicalDisplay defaultDisplay =
                add(
                        createDisplayDevice(
                                Display.TYPE_INTERNAL,
                                600,
                                800,
                                DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY));

        // Create 3 virtual displays associated with a first virtual device.
        int deviceId1 = 1;
        TestDisplayDevice display1 =
                createDisplayDevice(Display.TYPE_VIRTUAL, "virtualDevice1Display1", 600, 800, 0);
        mLogicalDisplayMapper.associateDisplayDeviceWithVirtualDevice(display1, deviceId1);
        LogicalDisplay virtualDevice1Display1 = add(display1);

        TestDisplayDevice display2 =
                createDisplayDevice(Display.TYPE_VIRTUAL, "virtualDevice1Display2", 600, 800, 0);
        mLogicalDisplayMapper.associateDisplayDeviceWithVirtualDevice(display2, deviceId1);
        LogicalDisplay virtualDevice1Display2 = add(display2);

        TestDisplayDevice display3 =
                createDisplayDevice(Display.TYPE_VIRTUAL, "virtualDevice1Display3", 600, 800, 0);
        mLogicalDisplayMapper.associateDisplayDeviceWithVirtualDevice(display3, deviceId1);
        LogicalDisplay virtualDevice1Display3 = add(display3);

        // Create another 3 virtual displays associated with a second virtual device.
        int deviceId2 = 2;
        TestDisplayDevice display4 =
                createDisplayDevice(Display.TYPE_VIRTUAL, "virtualDevice2Display1", 600, 800, 0);
        mLogicalDisplayMapper.associateDisplayDeviceWithVirtualDevice(display4, deviceId2);
        LogicalDisplay virtualDevice2Display1 = add(display4);

        TestDisplayDevice display5 =
                createDisplayDevice(Display.TYPE_VIRTUAL, "virtualDevice2Display2", 600, 800, 0);
        mLogicalDisplayMapper.associateDisplayDeviceWithVirtualDevice(display5, deviceId2);
        LogicalDisplay virtualDevice2Display2 = add(display5);

        // The final display is created with FLAG_OWN_DISPLAY_GROUP set.
        TestDisplayDevice display6 =
                createDisplayDevice(
                        Display.TYPE_VIRTUAL,
                        "virtualDevice2Display3",
                        600,
                        800,
                        DisplayDeviceInfo.FLAG_OWN_DISPLAY_GROUP);
        mLogicalDisplayMapper.associateDisplayDeviceWithVirtualDevice(display6, deviceId2);
        LogicalDisplay virtualDevice2Display3 = add(display6);

        // Verify that the internal display is in the default display group.
        assertEquals(
                DEFAULT_DISPLAY_GROUP,
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(id(defaultDisplay)));

        // Verify that all the displays for virtual device 1 are in the same (non-default) display
        // group.
        int virtualDevice1DisplayGroupId =
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(
                        id(virtualDevice1Display1));
        assertNotEquals(DEFAULT_DISPLAY_GROUP, virtualDevice1DisplayGroupId);
        assertEquals(
                virtualDevice1DisplayGroupId,
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(
                        id(virtualDevice1Display2)));
        assertEquals(
                virtualDevice1DisplayGroupId,
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(
                        id(virtualDevice1Display3)));

        // The first 2 displays for virtual device 2 should be in the same non-default group.
        int virtualDevice2DisplayGroupId =
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(
                        id(virtualDevice2Display1));
        assertNotEquals(DEFAULT_DISPLAY_GROUP, virtualDevice2DisplayGroupId);
        assertEquals(
                virtualDevice2DisplayGroupId,
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(
                        id(virtualDevice2Display2)));
        // virtualDevice2Display3 was created with FLAG_OWN_DISPLAY_GROUP and shouldn't be grouped
        // with other displays of this device or be in the default display group.
        assertNotEquals(
                virtualDevice2DisplayGroupId,
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(
                        id(virtualDevice2Display3)));
        assertNotEquals(
                DEFAULT_DISPLAY_GROUP,
                mLogicalDisplayMapper.getDisplayGroupIdFromDisplayIdLocked(
                        id(virtualDevice2Display3)));
    }

    @Test
    @EnableFlags({FLAG_DEVICE_STATE_PROPERTY_MIGRATION, FLAG_CHANGE_DEFAULT_DISPLAY_LID_CLOSED})
    public void testUnfoldDevice_ShouldWake() {
        when(mPowerManagerMock.isInteractive()).thenReturn(false);
        initLogicalDisplayMapper();

        finishBootAndTransitionBetweenStates(DEVICE_STATE_FOLDABLE_CLOSED,
                DEVICE_STATE_FOLDABLE_OPEN);

        verify(mPowerManagerMock).wakeUp(anyLong(), eq(WAKE_REASON_UNFOLD_DEVICE),
                eq("server.display:unfold"));
        verify(mPowerManagerMock, never()).goToSleep(anyLong(), anyInt(), anyInt());
    }

    @Test
    @EnableFlags({FLAG_DEVICE_STATE_PROPERTY_MIGRATION, FLAG_CHANGE_DEFAULT_DISPLAY_LID_CLOSED})
    public void testFoldDevice_ShouldNotSleepWhenStayAwakeSettingTrue() {
        when(mPowerManagerMock.isInteractive()).thenReturn(true);
        initLogicalDisplayMapper();
        setFoldLockBehaviorSettingValue(SETTING_VALUE_STAY_AWAKE_ON_FOLD);

        finishBootAndTransitionBetweenStates(DEVICE_STATE_FOLDABLE_OPEN,
                DEVICE_STATE_FOLDABLE_CLOSED);

        verify(mPowerManagerMock, never()).goToSleep(anyLong(), anyInt(), anyInt());
    }

    @Test
    @EnableFlags({FLAG_DEVICE_STATE_PROPERTY_MIGRATION, FLAG_CHANGE_DEFAULT_DISPLAY_LID_CLOSED})
    public void testFoldDevice_SleepSettingTrue() {
        when(mPowerManagerMock.isInteractive()).thenReturn(true);
        initLogicalDisplayMapper();
        setFoldLockBehaviorSettingValue(SETTING_VALUE_SLEEP_ON_FOLD);

        finishBootAndTransitionBetweenStates(DEVICE_STATE_FOLDABLE_OPEN,
                DEVICE_STATE_FOLDABLE_CLOSED);

        verify(mPowerManagerMock, never()).wakeUp(anyLong(), anyInt(), any());
        verify(mPowerManagerMock).goToSleep(anyLong(), eq(GO_TO_SLEEP_REASON_DEVICE_FOLD),
                /* flags= */ eq(0));
    }

    @Test
    @EnableFlags({FLAG_DEVICE_STATE_PROPERTY_MIGRATION, FLAG_CHANGE_DEFAULT_DISPLAY_LID_CLOSED})
    public void testFoldDevice_ShouldSleepWhenFoldSettingSelective() {
        when(mPowerManagerMock.isInteractive()).thenReturn(true);
        initLogicalDisplayMapper();
        setFoldLockBehaviorSettingValue(SETTING_VALUE_SELECTIVE_STAY_AWAKE);

        finishBootAndTransitionBetweenStates(DEVICE_STATE_FOLDABLE_OPEN,
                DEVICE_STATE_FOLDABLE_CLOSED);

        verify(mPowerManagerMock, never()).wakeUp(anyLong(), anyInt(), any());
        verify(mPowerManagerMock).goToSleep(anyLong(), eq(GO_TO_SLEEP_REASON_DEVICE_FOLD),
                eq(PowerManager.GO_TO_SLEEP_FLAG_SOFT_SLEEP));
    }

    @Test
    @EnableFlags({FLAG_DEVICE_STATE_PROPERTY_MIGRATION, FLAG_CHANGE_DEFAULT_DISPLAY_LID_CLOSED})
    public void testStateChange_TriggersSleepWithUnknownReason() {
        DeviceState triggerSleepState = createDeviceState(0,
                "Trigger sleep", Set.of(DeviceState.PROPERTY_POWER_CONFIGURATION_TRIGGER_SLEEP),
                Collections.emptySet());
        when(mPowerManagerMock.isInteractive()).thenReturn(true);
        initLogicalDisplayMapper();
        setFoldLockBehaviorSettingValue(SETTING_VALUE_SELECTIVE_STAY_AWAKE);

        finishBootAndTransitionBetweenStates(DEVICE_STATE_FOLDABLE_OPEN,
                triggerSleepState);

        verify(mPowerManagerMock, never()).wakeUp(anyLong(), anyInt(), any());
        verify(mPowerManagerMock).goToSleep(anyLong(), eq(GO_TO_SLEEP_REASON_UNKNOWN),
                eq(0));
    }

    @Test
    @EnableFlags(FLAG_DEVICE_STATE_PROPERTY_MIGRATION)
    public void testDeviceShouldNotBeWokenWhenExitingEmulatedState() {
        initLogicalDisplayMapper();
        finishBootAndTransitionBetweenStates(DEVICE_STATE_EMULATED, DEVICE_STATE_FOLDABLE_OPEN);
        verify(mPowerManagerMock, never()).wakeUp(anyLong(), anyInt(), any());
    }

    @Test
    @EnableFlags({FLAG_DEVICE_STATE_PROPERTY_MIGRATION})
    public void testInitialStateInvalid_ShouldNotWake() {
        initLogicalDisplayMapper();
        finishBootAndTransitionBetweenStates(INVALID_DEVICE_STATE, DEVICE_STATE_FOLDABLE_HALF_OPEN);
        verify(mPowerManagerMock, never()).wakeUp(anyLong(), anyInt(), any());
    }

    @Test
    @EnableFlags({FLAG_DEVICE_STATE_PROPERTY_MIGRATION})
    public void testInitialStateInvalid_ShouldNotSleep() {
        initLogicalDisplayMapper();
        finishBootAndTransitionBetweenStates(INVALID_DEVICE_STATE, DEVICE_STATE_FOLDABLE_CLOSED);
        verify(mPowerManagerMock, never()).goToSleep(anyLong(), anyInt(), anyInt());
    }

    @Test
    @EnableFlags({FLAG_DEVICE_STATE_PROPERTY_MIGRATION, FLAG_CHANGE_DEFAULT_DISPLAY_LID_CLOSED})
    public void testOpenLid_ShouldWake() {
        when(mPowerManagerMock.isInteractive()).thenReturn(false);
        initLogicalDisplayMapper();

        finishBootAndTransitionBetweenStates(DEVICE_STATE_LID_CLOSED, DEVICE_STATE_LID_OPEN);

        verify(mPowerManagerMock).wakeUp(anyLong(), eq(WAKE_REASON_LID),
                eq("server.display:lid_open"));
        verify(mPowerManagerMock, never()).goToSleep(anyLong(), anyInt(), anyInt());
    }

    @Test
    @EnableFlags({FLAG_DEVICE_STATE_PROPERTY_MIGRATION, FLAG_CHANGE_DEFAULT_DISPLAY_LID_CLOSED})
    public void testCloseLid_ShouldSleep() {
        initLogicalDisplayMapper();

        finishBootAndTransitionBetweenStates(DEVICE_STATE_LID_OPEN, DEVICE_STATE_LID_CLOSED);

        verify(mPowerManagerMock, never()).wakeUp(anyLong(), anyInt(), any());
        verify(mPowerManagerMock).goToSleep(anyLong(), eq(GO_TO_SLEEP_REASON_LID_SWITCH),
                /* flags= */ eq(0));
    }

    @Test
    @EnableFlags({FLAG_DEVICE_STATE_PROPERTY_MIGRATION, FLAG_CHANGE_DEFAULT_DISPLAY_LID_CLOSED})
    public void testOpenLid_ShouldNotWakeIfInteractive() {
        when(mPowerManagerMock.isInteractive()).thenReturn(true);
        initLogicalDisplayMapper();

        finishBootAndTransitionBetweenStates(DEVICE_STATE_LID_CLOSED, DEVICE_STATE_LID_OPEN);

        verify(mPowerManagerMock, never()).wakeUp(anyLong(), anyInt(), any());
        verify(mPowerManagerMock, never()).goToSleep(anyLong(), anyInt(), anyInt());
    }

    @Test
    @EnableFlags({FLAG_DEVICE_STATE_PROPERTY_MIGRATION, FLAG_CHANGE_DEFAULT_DISPLAY_LID_CLOSED})
    public void testCloseLid_ShouldNotSleepIfNotInteractive() {
        when(mPowerManagerMock.isInteractive()).thenReturn(false);
        initLogicalDisplayMapper();

        finishBootAndTransitionBetweenStates(DEVICE_STATE_LID_OPEN, DEVICE_STATE_LID_CLOSED);

        verify(mPowerManagerMock, never()).wakeUp(anyLong(), anyInt(), any());
        verify(mPowerManagerMock, never()).goToSleep(anyLong(), anyInt(), anyInt());
    }

    @Test
    @EnableFlags({FLAG_DEVICE_STATE_PROPERTY_MIGRATION, FLAG_CHANGE_DEFAULT_DISPLAY_LID_CLOSED})
    public void testOpenLid_ShouldNotWakeIfBootNotCompleted() {
        when(mPowerManagerMock.isInteractive()).thenReturn(false);
        initLogicalDisplayMapper();

        mLogicalDisplayMapper.setDeviceState(DEVICE_STATE_LID_CLOSED);
        advanceTime(STATE_TRANSITION_DELAY);
        mLogicalDisplayMapper.setDeviceState(DEVICE_STATE_LID_OPEN);
        advanceTime(STATE_TRANSITION_DELAY);

        verify(mPowerManagerMock, never()).wakeUp(anyLong(), anyInt(), any());
        verify(mPowerManagerMock, never()).goToSleep(anyLong(), anyInt(), anyInt());
    }

    @Test
    @EnableFlags({FLAG_DEVICE_STATE_PROPERTY_MIGRATION, FLAG_CHANGE_DEFAULT_DISPLAY_LID_CLOSED})
    public void testCloseLid_ShouldNotSleepIfBootNotCompleted() {
        initLogicalDisplayMapper();

        mLogicalDisplayMapper.setDeviceState(DEVICE_STATE_LID_OPEN);
        advanceTime(STATE_TRANSITION_DELAY);
        mLogicalDisplayMapper.setDeviceState(DEVICE_STATE_LID_CLOSED);
        advanceTime(STATE_TRANSITION_DELAY);

        verify(mPowerManagerMock, never()).wakeUp(anyLong(), anyInt(), any());
        verify(mPowerManagerMock, never()).goToSleep(anyLong(), anyInt(), anyInt());
    }

    @Test
    public void testDisplaySwappedAfterDeviceStateChange_windowManagerIsNotified() {
        initLogicalDisplayMapper();
        FoldableDisplayDevices foldableDisplayDevices = createFoldableDeviceStateToLayoutMap();
        mLogicalDisplayMapper.setDeviceState(DEVICE_STATE_FOLDABLE_OPEN);
        mLogicalDisplayMapper.onEarlyInteractivityChange(true);
        mLogicalDisplayMapper.onBootCompleted();
        advanceTime(1000);
        clearInvocations(mWindowManagerPolicy);

        // Switch from 'inner' to 'outer' display (fold a foldable device)
        mLogicalDisplayMapper.setDeviceState(DEVICE_STATE_FOLDABLE_CLOSED);
        // Continue folding device state transition by turning off the inner display
        foldableDisplayDevices.mInner.setState(STATE_OFF);
        notifyDisplayChanges(foldableDisplayDevices.mOuter);
        advanceTime(TIMEOUT_STATE_TRANSITION_MILLIS);

        verify(mWindowManagerPolicy).onDisplaySwitchStart(DEFAULT_DISPLAY);
    }

    @Test
    public void testCreateNewLogicalDisplay_windowManagerIsNotNotifiedAboutSwitch() {
        initLogicalDisplayMapper();
        DisplayDevice device1 = createDisplayDevice(TYPE_EXTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        when(mDeviceStateToLayoutMapSpy.size()).thenReturn(1);
        LogicalDisplay display1 = add(device1);

        assertTrue(display1.isEnabledLocked());

        DisplayDevice device2 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        when(mDeviceStateToLayoutMapSpy.size()).thenReturn(2);
        add(device2);

        // As it is not a display switch but adding a new display, we should not notify
        // about display switch start to window manager
        verify(mWindowManagerPolicy, never()).onDisplaySwitchStart(anyInt());
    }

    @Test
    public void testDoNotWaitForSleepWhenFoldSettingSelectiveStayAwake() {
        initLogicalDisplayMapper();
        // Test device should be marked ready for transition immediately when 'Continue using app
        // on fold' set to 'Swipe up to continue'
        setFoldLockBehaviorSettingValue(SETTING_VALUE_SELECTIVE_STAY_AWAKE);
        FoldableDisplayDevices foldableDisplayDevices = createFoldableDeviceStateToLayoutMap();

        finishBootAndTransitionBetweenStates(DEVICE_STATE_FOLDABLE_OPEN,
                DEVICE_STATE_FOLDABLE_CLOSED);
        foldableDisplayDevices.mInner.setState(STATE_OFF);
        notifyDisplayChanges(foldableDisplayDevices.mOuter);

        assertDisplayEnabled(foldableDisplayDevices.mOuter);
        assertDisplayDisabled(foldableDisplayDevices.mInner);
    }

    @Test
    public void testDeviceStateLocked() {
        initLogicalDisplayMapper();
        DisplayDevice device1 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        DisplayDevice device2 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);

        Layout layout = new Layout();
        layout.createDisplayLocked(device1.getDisplayDeviceInfoLocked().address,
                /* isDefault= */ true, /* isEnabled= */ true, /* displayGroup= */ null,
                mIdProducer, POSITION_UNKNOWN,
                /* leadDisplayAddress= */ null,
                /* brightnessThrottlingMapId= */ "concurrent",
                /* refreshRateZoneId= */ null, /* refreshRateThermalThrottlingMapId= */ null,
                /* powerThrottlingMapId= */ "concurrent");
        layout.createDisplayLocked(device2.getDisplayDeviceInfoLocked().address,
                /* isDefault= */ false, /* isEnabled= */ true, /* displayGroup= */ null,
                mIdProducer, POSITION_UNKNOWN,
                /* leadDisplayAddress= */ null,
                /* brightnessThrottlingMapId= */ "concurrent",
                /* refreshRateZoneId= */ null, /* refreshRateThermalThrottlingMapId= */ null,
                /* powerThrottlingMapId= */ "concurrent");
        when(mDeviceStateToLayoutMapSpy.get(0)).thenReturn(layout);

        layout = new Layout();
        createNonDefaultDisplay(layout, device1, /* enabled= */ false, /* group= */ null);
        createDefaultDisplay(layout, device2);
        when(mDeviceStateToLayoutMapSpy.get(1)).thenReturn(layout);
        when(mDeviceStateToLayoutMapSpy.get(2)).thenReturn(layout);

        when(mDeviceStateToLayoutMapSpy.size()).thenReturn(4);

        LogicalDisplay display1 = add(device1);
        assertEquals(info(display1).address, info(device1).address);
        assertEquals(DEFAULT_DISPLAY, id(display1));

        LogicalDisplay display2 = add(device2);
        assertEquals(info(display2).address, info(device2).address);
        // We can only have one default display
        assertEquals(DEFAULT_DISPLAY, id(display1));

        mLogicalDisplayMapper.setDeviceState(DEVICE_STATE_FOLDABLE_CLOSED);
        advanceTime(1000);
        // The new state is not applied until the boot is completed
        assertTrue(mLogicalDisplayMapper.getDisplayLocked(device1).isEnabledLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device2).isEnabledLocked());

        mLogicalDisplayMapper.onBootCompleted();
        advanceTime(1000);
        assertTrue(mLogicalDisplayMapper.getDisplayLocked(device1).isEnabledLocked());
        assertTrue(mLogicalDisplayMapper.getDisplayLocked(device2).isEnabledLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device1).isInTransitionLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device2).isInTransitionLocked());
        assertEquals(-1, mLogicalDisplayMapper.getDisplayLocked(device1)
                .getLeadDisplayIdLocked());
        assertEquals(-1, mLogicalDisplayMapper.getDisplayLocked(device2)
                .getLeadDisplayIdLocked());
        assertEquals("concurrent", mLogicalDisplayMapper.getDisplayLocked(device1)
                .getDisplayInfoLocked().thermalBrightnessThrottlingDataId);
        assertEquals("concurrent", mLogicalDisplayMapper.getDisplayLocked(device2)
                .getDisplayInfoLocked().thermalBrightnessThrottlingDataId);

        mLogicalDisplayMapper.setDeviceState(DEVICE_STATE_FOLDABLE_HALF_OPEN);
        advanceTime(1000);
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device1).isEnabledLocked());
        assertTrue(mLogicalDisplayMapper.getDisplayLocked(device2).isEnabledLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device1).isInTransitionLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device2).isInTransitionLocked());
        assertEquals(DisplayDeviceConfig.DEFAULT_ID,
                mLogicalDisplayMapper.getDisplayLocked(device1)
                        .getDisplayInfoLocked().thermalBrightnessThrottlingDataId);
        assertEquals(DisplayDeviceConfig.DEFAULT_ID,
                mLogicalDisplayMapper.getDisplayLocked(device2)
                        .getDisplayInfoLocked().thermalBrightnessThrottlingDataId);

        mLogicalDisplayMapper.setDeviceState(DEVICE_STATE_FOLDABLE_OPEN);
        advanceTime(1000);
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device1).isEnabledLocked());
        assertTrue(mLogicalDisplayMapper.getDisplayLocked(device2).isEnabledLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device1).isInTransitionLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device2).isInTransitionLocked());
        assertEquals(DisplayDeviceConfig.DEFAULT_ID,
                mLogicalDisplayMapper.getDisplayLocked(device1)
                        .getDisplayInfoLocked().thermalBrightnessThrottlingDataId);
        assertEquals(DisplayDeviceConfig.DEFAULT_ID,
                mLogicalDisplayMapper.getDisplayLocked(device2)
                        .getDisplayInfoLocked().thermalBrightnessThrottlingDataId);
    }

    @Test
    @EnableFlags(FLAG_CHANGE_DEFAULT_DISPLAY_LID_CLOSED)
    public void testDeviceState_LaptopLidClosed() {
        initLogicalDisplayMapper();
        when(mIsDisplayAllowedInTopologyMock.test(any(DisplayInfo.class))).thenReturn(true);
        DisplayDevice device1 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        DisplayDevice device2 = createDisplayDevice(TYPE_EXTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);

        LogicalDisplay display1 = add(device1);
        display1.setCanHostTasksLocked(true);
        assertEquals(info(display1).address, info(device1).address);
        assertEquals(DEFAULT_DISPLAY, id(display1));

        LogicalDisplay display2 = add(device2);
        display2.setCanHostTasksLocked(true);
        assertEquals(info(display2).address, info(device2).address);
        // We can only have one default display
        assertEquals(DEFAULT_DISPLAY, id(display1));

        finishBootAndTransitionBetweenStates(DEVICE_STATE_LID_OPEN, DEVICE_STATE_DOCKED);

        assertEquals(DEFAULT_DISPLAY, id(display1));
        // Device 2 is now associated with the default display
        assertEquals(device2, display1.getPrimaryDisplayDeviceLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device1).isEnabledLocked());
        assertTrue(mLogicalDisplayMapper.getDisplayLocked(device2).isEnabledLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device1).isInTransitionLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device2).isInTransitionLocked());

        verify(mPowerManagerMock, never()).wakeUp(anyLong(), anyInt(), any());
        verify(mPowerManagerMock, never()).goToSleep(anyLong(), anyInt(), anyInt());
    }

    @Test
    @EnableFlags(FLAG_CHANGE_DEFAULT_DISPLAY_LID_CLOSED)
    public void testDeviceState_LaptopLidClosed_InternalDisplaysOnly() {
        initLogicalDisplayMapper();
        when(mIsDisplayAllowedInTopologyMock.test(any(DisplayInfo.class))).thenReturn(true);
        DisplayDevice device1 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        DisplayDevice device2 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);

        LogicalDisplay display1 = add(device1);
        display1.setCanHostTasksLocked(true);
        assertEquals(info(display1).address, info(device1).address);
        assertEquals(DEFAULT_DISPLAY, id(display1));

        LogicalDisplay display2 = add(device2);
        display2.setCanHostTasksLocked(true);
        assertEquals(info(display2).address, info(device2).address);
        // We can only have one default display
        assertEquals(DEFAULT_DISPLAY, id(display1));

        finishBootAndTransitionBetweenStates(DEVICE_STATE_LID_OPEN, DEVICE_STATE_DOCKED);

        assertEquals(DEFAULT_DISPLAY, id(display1));
        // The device of the default display should not have changed
        assertEquals(device1, display1.getPrimaryDisplayDeviceLocked());
        assertTrue(mLogicalDisplayMapper.getDisplayLocked(device1).isEnabledLocked());
        assertTrue(mLogicalDisplayMapper.getDisplayLocked(device2).isEnabledLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device1).isInTransitionLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device2).isInTransitionLocked());
        verify(mWindowManagerPolicy, never()).onDisplaySwitchStart(DEFAULT_DISPLAY);

        // The device should go to sleep
        verify(mPowerManagerMock, never()).wakeUp(anyLong(), anyInt(), any());
        verify(mPowerManagerMock).goToSleep(anyLong(), eq(GO_TO_SLEEP_REASON_LID_SWITCH),
                /* flags= */ eq(0));
    }

    @Test
    @EnableFlags(FLAG_CHANGE_DEFAULT_DISPLAY_LID_CLOSED)
    public void testDeviceState_LaptopLidClosed_SecondaryDisplayDisabled() {
        initLogicalDisplayMapper();
        when(mIsDisplayAllowedInTopologyMock.test(any(DisplayInfo.class))).thenReturn(true);
        DisplayDevice device1 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        DisplayDevice device2 = createDisplayDevice(TYPE_EXTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);

        LogicalDisplay display1 = add(device1);
        display1.setCanHostTasksLocked(true);
        assertEquals(info(display1).address, info(device1).address);
        assertEquals(DEFAULT_DISPLAY, id(display1));

        LogicalDisplay display2 = add(device2);
        display2.setCanHostTasksLocked(true);
        display2.setEnabledLocked(false);
        assertEquals(info(display2).address, info(device2).address);
        // We can only have one default display
        assertEquals(DEFAULT_DISPLAY, id(display1));

        mLogicalDisplayMapper.onBootCompleted();
        mLogicalDisplayMapper.setDeviceState(DEVICE_STATE_DOCKED);
        advanceTime(1000);

        assertEquals(DEFAULT_DISPLAY, id(display1));
        // The device of the default display should not have changed
        assertEquals(device1, display1.getPrimaryDisplayDeviceLocked());
        assertTrue(mLogicalDisplayMapper.getDisplayLocked(device1).isEnabledLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device2).isEnabledLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device1).isInTransitionLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device2).isInTransitionLocked());
        verify(mWindowManagerPolicy, never()).onDisplaySwitchStart(DEFAULT_DISPLAY);
    }

    @Test
    @EnableFlags(FLAG_CHANGE_DEFAULT_DISPLAY_LID_CLOSED)
    public void testDeviceState_LaptopLidClosed_SecondaryDisplayCannotHostTasks() {
        initLogicalDisplayMapper();
        when(mIsDisplayAllowedInTopologyMock.test(any(DisplayInfo.class))).thenReturn(true);
        DisplayDevice device1 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        DisplayDevice device2 = createDisplayDevice(TYPE_EXTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY | FLAG_ALLOWS_CONTENT_MODE_SWITCH);

        LogicalDisplay display1 = add(device1);
        display1.setCanHostTasksLocked(true);
        assertEquals(info(display1).address, info(device1).address);
        assertEquals(DEFAULT_DISPLAY, id(display1));

        LogicalDisplay display2 = add(device2);
        display2.setCanHostTasksLocked(false);
        assertEquals(info(display2).address, info(device2).address);
        // We can only have one default display
        assertEquals(DEFAULT_DISPLAY, id(display1));

        finishBootAndTransitionBetweenStates(DEVICE_STATE_LID_OPEN, DEVICE_STATE_DOCKED);

        assertEquals(DEFAULT_DISPLAY, id(display1));
        // Device 2 is now associated with the default display. It cannot host tasks but can
        // still enter docked mode.
        assertEquals(device2, display1.getPrimaryDisplayDeviceLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device1).isEnabledLocked());
        assertTrue(mLogicalDisplayMapper.getDisplayLocked(device2).isEnabledLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device1).isInTransitionLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device2).isInTransitionLocked());

        verify(mPowerManagerMock, never()).wakeUp(anyLong(), anyInt(), any());
        verify(mPowerManagerMock, never()).goToSleep(anyLong(), anyInt(), anyInt());
    }

    @Test
    @EnableFlags(FLAG_CHANGE_DEFAULT_DISPLAY_LID_CLOSED)
    public void testDeviceState_LaptopLidClosed_SecondaryDisplayNotAllowedInTopology() {
        initLogicalDisplayMapper();
        DisplayDevice device1 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        DisplayDevice device2 = createDisplayDevice(TYPE_EXTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);

        LogicalDisplay display1 = add(device1);
        display1.setCanHostTasksLocked(true);
        when(mIsDisplayAllowedInTopologyMock.test(display1.getDisplayInfoLocked())).thenReturn(
                true);
        assertEquals(info(display1).address, info(device1).address);
        assertEquals(DEFAULT_DISPLAY, id(display1));

        LogicalDisplay display2 = add(device2);
        display2.setCanHostTasksLocked(true);
        when(mIsDisplayAllowedInTopologyMock.test(display2.getDisplayInfoLocked())).thenReturn(
                false);
        assertEquals(info(display2).address, info(device2).address);
        // We can only have one default display
        assertEquals(DEFAULT_DISPLAY, id(display1));

        mLogicalDisplayMapper.onBootCompleted();
        mLogicalDisplayMapper.setDeviceState(DEVICE_STATE_DOCKED);
        advanceTime(1000);

        assertEquals(DEFAULT_DISPLAY, id(display1));
        // The device of the default display should not have changed
        assertEquals(device1, display1.getPrimaryDisplayDeviceLocked());
        assertTrue(mLogicalDisplayMapper.getDisplayLocked(device1).isEnabledLocked());
        assertTrue(mLogicalDisplayMapper.getDisplayLocked(device2).isEnabledLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device1).isInTransitionLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device2).isInTransitionLocked());
        verify(mWindowManagerPolicy, never()).onDisplaySwitchStart(DEFAULT_DISPLAY);
    }

    @Test
    @EnableFlags(FLAG_CHANGE_DEFAULT_DISPLAY_LID_CLOSED)
    public void testDeviceState_LaptopLidClosed_SecondaryDisplayNoFlag() {
        initLogicalDisplayMapper();
        when(mIsDisplayAllowedInTopologyMock.test(any(DisplayInfo.class))).thenReturn(true);
        DisplayDevice device1 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        DisplayDevice device2 = createDisplayDevice(TYPE_EXTERNAL, 600, 800,
                /* flags= */ 0);

        LogicalDisplay display1 = add(device1);
        display1.setCanHostTasksLocked(true);
        assertEquals(info(display1).address, info(device1).address);
        assertEquals(DEFAULT_DISPLAY, id(display1));

        LogicalDisplay display2 = add(device2);
        display2.setCanHostTasksLocked(true);
        assertEquals(info(display2).address, info(device2).address);
        // We can only have one default display
        assertEquals(DEFAULT_DISPLAY, id(display1));

        mLogicalDisplayMapper.onBootCompleted();
        mLogicalDisplayMapper.setDeviceState(DEVICE_STATE_DOCKED);
        advanceTime(1000);

        assertEquals(DEFAULT_DISPLAY, id(display1));
        // The device of the default display should not have changed
        assertEquals(device1, display1.getPrimaryDisplayDeviceLocked());
        assertTrue(mLogicalDisplayMapper.getDisplayLocked(device1).isEnabledLocked());
        assertTrue(mLogicalDisplayMapper.getDisplayLocked(device2).isEnabledLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device1).isInTransitionLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device2).isInTransitionLocked());
        verify(mWindowManagerPolicy, never()).onDisplaySwitchStart(DEFAULT_DISPLAY);
    }

    @Test
    @EnableFlags(FLAG_CHANGE_DEFAULT_DISPLAY_LID_CLOSED)
    public void testDisplayDeviceAddAndRemove_GoBackToDockedState() {
        initLogicalDisplayMapper();
        when(mIsDisplayAllowedInTopologyMock.test(any(DisplayInfo.class))).thenReturn(true);
        DisplayDevice device1 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        DisplayDevice device2 = createDisplayDevice(TYPE_EXTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        Layout layout = new Layout();
        createDefaultDisplay(layout, device1);
        when(mDeviceStateToLayoutMapSpy.get(DEVICE_STATE_LID_OPEN.getIdentifier())).thenReturn(
                layout);

        LogicalDisplay display1 = add(device1);
        display1.setCanHostTasksLocked(true);
        assertEquals(info(display1).address, info(device1).address);
        assertEquals(DEFAULT_DISPLAY, id(display1));

        LogicalDisplay display2 = add(device2);
        display2.setCanHostTasksLocked(true);
        assertEquals(info(display2).address, info(device2).address);
        // We can only have one default display
        assertEquals(DEFAULT_DISPLAY, id(display1));

        mLogicalDisplayMapper.onBootCompleted();
        mLogicalDisplayMapper.setDeviceState(DEVICE_STATE_DOCKED);
        advanceTime(1000);
        assertEquals(device2, display1.getPrimaryDisplayDeviceLocked());
        assertFalse(mLogicalDisplayMapper.getDisplayLocked(device1).isEnabledLocked());
        assertTrue(mLogicalDisplayMapper.getDisplayLocked(device2).isEnabledLocked());

        // Device 1 becomes the default display again
        mLogicalDisplayMapper.setDeviceState(DEVICE_STATE_LID_OPEN);
        advanceTime(1000);
        assertEquals(device1, display1.getPrimaryDisplayDeviceLocked());
        // Both displays should be enabled
        assertTrue(mLogicalDisplayMapper.getDisplayLocked(device1).isEnabledLocked());
        assertTrue(mLogicalDisplayMapper.getDisplayLocked(device2).isEnabledLocked());

        // remove
        mDisplayDeviceRepo.onDisplayDeviceEvent(device2, DISPLAY_DEVICE_EVENT_REMOVED);
        advanceTime(1000);
        assertNull(mLogicalDisplayMapper.getDisplayLocked(device2));

        // Go back to the docked state
        mLogicalDisplayMapper.setDeviceState(DEVICE_STATE_DOCKED);
        advanceTime(1000);

        // Display 1 is still the default logical display
        assertEquals(DEFAULT_DISPLAY, id(display1));
        assertEquals(device1, display1.getPrimaryDisplayDeviceLocked());
        assertTrue(mLogicalDisplayMapper.getDisplayLocked(device1).isEnabledLocked());
    }

    @Test
    public void testDisplayIsEnabledInLayout() {
        initLogicalDisplayMapper();
        DisplayAddress displayAddressOne = createTestDisplayAddress();
        DisplayAddress displayAddressTwo = createTestDisplayAddress();
        TestDisplayDevice device1 = createDisplayDevice(displayAddressOne, "one",
                TYPE_INTERNAL, 600, 800, DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        TestDisplayDevice device2 = createDisplayDevice(displayAddressTwo, "two",
                TYPE_EXTERNAL, 1920, 1080, DisplayDeviceInfo.FLAG_OWN_DISPLAY_GROUP);
        Layout twoDevicesEnabledLayout = new Layout();
        createDefaultDisplay(twoDevicesEnabledLayout, displayAddressOne);
        createNonDefaultDisplay(twoDevicesEnabledLayout, displayAddressTwo,
                /* enabled= */ true, /* group= */ null);
        when(mDeviceStateToLayoutMapSpy.get(STATE_DEFAULT)).thenReturn(twoDevicesEnabledLayout);

        LogicalDisplay display1 = add(device1);
        LogicalDisplay display2 = add(device2);

        assertThat(mLogicalDisplayMapper.isEnabledInLayoutLocked(display1)).isTrue();
        assertThat(mLogicalDisplayMapper.isEnabledInLayoutLocked(display2)).isTrue();
    }

    @Test
    public void testDisplayIsDisabledInLayout() {
        initLogicalDisplayMapper();
        DisplayAddress displayAddressOne = createTestDisplayAddress();
        DisplayAddress displayAddressTwo = createTestDisplayAddress();
        TestDisplayDevice device1 = createDisplayDevice(displayAddressOne, "one",
                TYPE_INTERNAL, 600, 800, DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        TestDisplayDevice device2 = createDisplayDevice(displayAddressTwo, "two",
                TYPE_EXTERNAL, 1920, 1080, DisplayDeviceInfo.FLAG_OWN_DISPLAY_GROUP);
        Layout oneDeviceEnabledLayout = new Layout();
        createDefaultDisplay(oneDeviceEnabledLayout, displayAddressOne);
        createNonDefaultDisplay(oneDeviceEnabledLayout, displayAddressTwo,
                /* enabled= */ false, /* group= */ null);
        when(mDeviceStateToLayoutMapSpy.get(STATE_DEFAULT)).thenReturn(oneDeviceEnabledLayout);

        LogicalDisplay display1 = add(device1);
        LogicalDisplay display2 = add(device2);

        assertThat(mLogicalDisplayMapper.isEnabledInLayoutLocked(display1)).isTrue();
        assertThat(mLogicalDisplayMapper.isEnabledInLayoutLocked(display2)).isFalse();
    }

    @Test
    public void testDisplayIsDisabledNotInLayout() {
        initLogicalDisplayMapper();
        DisplayAddress displayAddressOne = createTestDisplayAddress();
        DisplayAddress displayAddressTwo = createTestDisplayAddress();
        TestDisplayDevice device1 = createDisplayDevice(displayAddressOne, "one",
                TYPE_INTERNAL, 600, 800, DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        TestDisplayDevice device2 = createDisplayDevice(displayAddressTwo, "two",
                TYPE_EXTERNAL, 1920, 1080, DisplayDeviceInfo.FLAG_OWN_DISPLAY_GROUP);
        Layout oneDeviceLayout = new Layout();
        createDefaultDisplay(oneDeviceLayout, displayAddressOne);
        when(mDeviceStateToLayoutMapSpy.get(STATE_DEFAULT)).thenReturn(oneDeviceLayout);

        LogicalDisplay display1 = add(device1);
        LogicalDisplay display2 = add(device2);

        assertThat(mLogicalDisplayMapper.isEnabledInLayoutLocked(display1)).isTrue();
        assertThat(mLogicalDisplayMapper.isEnabledInLayoutLocked(display2)).isFalse();
    }

    @Test
    public void testEnabledAndDisabledDisplays() {
        initLogicalDisplayMapper();
        DisplayAddress displayAddressOne = createTestDisplayAddress();
        DisplayAddress displayAddressTwo = createTestDisplayAddress();
        DisplayAddress displayAddressThree = createTestDisplayAddress();

        TestDisplayDevice device1 = createDisplayDevice(displayAddressOne, "one",
                TYPE_INTERNAL, 600, 800, DisplayDeviceInfo.FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        TestDisplayDevice device2 = createDisplayDevice(displayAddressTwo, "two",
                TYPE_INTERNAL, 200, 800, DisplayDeviceInfo.FLAG_OWN_DISPLAY_GROUP);
        TestDisplayDevice device3 = createDisplayDevice(displayAddressThree, "three",
                TYPE_INTERNAL, 600, 900, DisplayDeviceInfo.FLAG_OWN_DISPLAY_GROUP);

        Layout threeDevicesEnabledLayout = new Layout();
        createDefaultDisplay(threeDevicesEnabledLayout, displayAddressOne);
        createNonDefaultDisplay(threeDevicesEnabledLayout, displayAddressTwo,
                /* enabled= */ true, /* group= */ null);
        createNonDefaultDisplay(threeDevicesEnabledLayout, displayAddressThree,
                /* enabled= */ true, /* group= */ null);

        when(mDeviceStateToLayoutMapSpy.get(STATE_DEFAULT))
                .thenReturn(threeDevicesEnabledLayout);

        LogicalDisplay display1 = add(device1);
        LogicalDisplay display2 = add(device2);
        LogicalDisplay display3 = add(device3);

        // ensure 3 displays are returned
        int [] ids = mLogicalDisplayMapper.getDisplayIdsLocked(Process.SYSTEM_UID, false);
        assertEquals(3, ids.length);
        Arrays.sort(ids);
        assertEquals(DEFAULT_DISPLAY, ids[0]);
        assertNotNull(mLogicalDisplayMapper.getDisplayLocked(device1,
                /* includeDisabled= */ false));
        assertNotNull(mLogicalDisplayMapper.getDisplayLocked(device2,
                /* includeDisabled= */ false));
        assertNotNull(mLogicalDisplayMapper.getDisplayLocked(device3,
                /* includeDisabled= */ false));
        assertNotNull(mLogicalDisplayMapper.getDisplayLocked(
                threeDevicesEnabledLayout.getByAddress(displayAddressOne).getLogicalDisplayId(),
                /* includeDisabled= */ false));
        assertNotNull(mLogicalDisplayMapper.getDisplayLocked(
                threeDevicesEnabledLayout.getByAddress(displayAddressTwo).getLogicalDisplayId(),
                /* includeDisabled= */ false));
        assertNotNull(mLogicalDisplayMapper.getDisplayLocked(
                threeDevicesEnabledLayout.getByAddress(displayAddressThree).getLogicalDisplayId(),
                /* includeDisabled= */ false));

        Layout oneDeviceEnabledLayout = new Layout();
        createDefaultDisplay(oneDeviceEnabledLayout, displayAddressOne);
        createNonDefaultDisplay(oneDeviceEnabledLayout, displayAddressTwo,
                /* enabled= */ false, /* group= */ null);
        createNonDefaultDisplay(oneDeviceEnabledLayout, displayAddressThree,
                /* enabled= */ false, /* group= */ null);

        when(mDeviceStateToLayoutMapSpy.get(0)).thenReturn(oneDeviceEnabledLayout);
        when(mDeviceStateToLayoutMapSpy.get(1)).thenReturn(threeDevicesEnabledLayout);

        // 1) Set the new state
        // 2) Mark the displays as STATE_OFF so that it can continue with transition
        // 3) Send DISPLAY_DEVICE_EVENT_CHANGE to inform the mapper of the new display state
        // 4) Dispatch handler events.
        mLogicalDisplayMapper.onBootCompleted();
        mLogicalDisplayMapper.setDeviceState(DEVICE_STATE_FOLDABLE_CLOSED);
        mDisplayDeviceRepo.onDisplayDeviceEvent(device3, DISPLAY_DEVICE_EVENT_CHANGED);
        advanceTime(1000);
        final int[] allDisplayIds = mLogicalDisplayMapper.getDisplayIdsLocked(
                Process.SYSTEM_UID, false);
        if (allDisplayIds.length != 1) {
            throw new RuntimeException("Displays: \n"
                    + mLogicalDisplayMapper.getDisplayLocked(device1).toString()
                    + "\n" + mLogicalDisplayMapper.getDisplayLocked(device2).toString()
                    + "\n" + mLogicalDisplayMapper.getDisplayLocked(device3).toString());
        }
        // ensure only one display is returned
        assertEquals(1, allDisplayIds.length);
        assertNotNull(mLogicalDisplayMapper.getDisplayLocked(device1,
                /* includeDisabled= */ false));
        assertNull(mLogicalDisplayMapper.getDisplayLocked(device2,
                /* includeDisabled= */ false));
        assertNull(mLogicalDisplayMapper.getDisplayLocked(device3,
                /* includeDisabled= */ false));
        assertNotNull(mLogicalDisplayMapper.getDisplayLocked(
                oneDeviceEnabledLayout.getByAddress(displayAddressOne).getLogicalDisplayId(),
                /* includeDisabled= */ false));
        assertNull(mLogicalDisplayMapper.getDisplayLocked(
                oneDeviceEnabledLayout.getByAddress(displayAddressTwo).getLogicalDisplayId(),
                /* includeDisabled= */ false));
        assertNull(mLogicalDisplayMapper.getDisplayLocked(
                oneDeviceEnabledLayout.getByAddress(displayAddressThree).getLogicalDisplayId(),
                /* includeDisabled= */ false));

        // Now do it again to go back to state 1
        mLogicalDisplayMapper.setDeviceState(DEVICE_STATE_FOLDABLE_HALF_OPEN);
        mDisplayDeviceRepo.onDisplayDeviceEvent(device3, DISPLAY_DEVICE_EVENT_CHANGED);
        advanceTime(1000);
        final int[] threeDisplaysEnabled = mLogicalDisplayMapper.getDisplayIdsLocked(
                Process.SYSTEM_UID, false);

        // ensure all three displays are returned
        assertEquals(3, threeDisplaysEnabled.length);
    }

    @Test
    public void testCreateNewLogicalDisplay() {
        initLogicalDisplayMapper();
        DisplayDevice device1 = createDisplayDevice(TYPE_EXTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        when(mDeviceStateToLayoutMapSpy.size()).thenReturn(1);
        LogicalDisplay display1 = add(device1);

        assertTrue(display1.isEnabledLocked());

        DisplayDevice device2 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        when(mDeviceStateToLayoutMapSpy.size()).thenReturn(2);
        LogicalDisplay display2 = add(device2);

        assertFalse(display2.isEnabledLocked());
    }
    @Test
    public void testDisplayFlagRear() {
        initLogicalDisplayMapper();
        DisplayDevice device1 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        DisplayDevice device2 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_REAR);

        Layout layout = new Layout();
        layout.createDefaultDisplayLocked(device1.getDisplayDeviceInfoLocked().address,
                mIdProducer);
        layout.createDisplayLocked(device2.getDisplayDeviceInfoLocked().address,
                /* isDefault= */ false, /* isEnabled= */ true, /* displayGroupName= */ null,
                mIdProducer, POSITION_REAR, /* leadDisplayAddress= */ null,
                /* brightnessThrottlingMapId= */ null, /* refreshRateZoneId= */ null,
                /* refreshRateThermalThrottlingMapId= */null, /* powerThrottlingMapId= */null);
        when(mDeviceStateToLayoutMapSpy.get(0)).thenReturn(layout);

        when(mDeviceStateToLayoutMapSpy.size()).thenReturn(1);

        LogicalDisplay display1 = add(device1);
        assertEquals(info(display1).address, info(device1).address);
        assertEquals(DEFAULT_DISPLAY, id(display1));

        LogicalDisplay display2 = add(device2);
        assertEquals(info(display2).address, info(device2).address);
        // We can only have one default display
        assertEquals(DEFAULT_DISPLAY, id(display1));

        mLogicalDisplayMapper.setDeviceState(DEVICE_STATE_FOLDABLE_CLOSED);
        advanceTime(1000);
        mLogicalDisplayMapper.onBootCompleted();
        advanceTime(1000);

        assertTrue(mLogicalDisplayMapper.getDisplayLocked(device1).isEnabledLocked());
        assertTrue(mLogicalDisplayMapper.getDisplayLocked(device2).isEnabledLocked());

        assertEquals(POSITION_UNKNOWN,
                mLogicalDisplayMapper.getDisplayLocked(device1).getDevicePositionLocked());
        assertEquals(POSITION_REAR,
                mLogicalDisplayMapper.getDisplayLocked(device2).getDevicePositionLocked());
    }

    @Test
    public void updateAndGetMaskForDisplayPropertyChanges_getsPropertyChangedFlags() {
        initLogicalDisplayMapper();
        // Change the refresh rate override
        DisplayInfo newDisplayInfo = new DisplayInfo();
        newDisplayInfo.refreshRateOverride = 30;
        assertEquals(LOGICAL_DISPLAY_EVENT_REFRESH_RATE_CHANGED,
                mLogicalDisplayMapper.updateAndGetMaskForDisplayPropertyChanges(newDisplayInfo));

        newDisplayInfo = new DisplayInfo();
        newDisplayInfo.appVsyncOffsetNanos = 123;
        assertEquals(LOGICAL_DISPLAY_EVENT_REFRESH_RATE_CHANGED,
                mLogicalDisplayMapper.updateAndGetMaskForDisplayPropertyChanges(newDisplayInfo));

        newDisplayInfo = new DisplayInfo();
        newDisplayInfo.presentationDeadlineNanos = 456;
        assertEquals(LOGICAL_DISPLAY_EVENT_REFRESH_RATE_CHANGED,
                mLogicalDisplayMapper.updateAndGetMaskForDisplayPropertyChanges(newDisplayInfo));

        newDisplayInfo = new DisplayInfo();
        newDisplayInfo.supportedRefreshRates = new float[60];
        assertEquals(LOGICAL_DISPLAY_EVENT_REFRESH_RATE_CHANGED,
                mLogicalDisplayMapper.updateAndGetMaskForDisplayPropertyChanges(newDisplayInfo));

        // Change the display state
        when(mFlagsMock.isDisplayListenerPerformanceImprovementsEnabled()).thenReturn(true);
        newDisplayInfo = new DisplayInfo();
        newDisplayInfo.state = STATE_OFF;
        assertEquals(LOGICAL_DISPLAY_EVENT_STATE_CHANGED,
                mLogicalDisplayMapper.updateAndGetMaskForDisplayPropertyChanges(newDisplayInfo));

        // Change the display committed state
        when(mFlagsMock.isCommittedStateSeparateEventEnabled()).thenReturn(true);
        newDisplayInfo = new DisplayInfo();
        newDisplayInfo.committedState = STATE_OFF;
        assertEquals(LOGICAL_DISPLAY_EVENT_COMMITTED_STATE_CHANGED,
                mLogicalDisplayMapper.updateAndGetMaskForDisplayPropertyChanges(newDisplayInfo));

        // Change multiple properties
        newDisplayInfo = new DisplayInfo();
        newDisplayInfo.refreshRateOverride = 30;
        newDisplayInfo.state = STATE_OFF;
        newDisplayInfo.committedState = STATE_OFF;
        assertEquals(LOGICAL_DISPLAY_EVENT_REFRESH_RATE_CHANGED
                        | LOGICAL_DISPLAY_EVENT_STATE_CHANGED
                        | LOGICAL_DISPLAY_EVENT_COMMITTED_STATE_CHANGED,
                mLogicalDisplayMapper.updateAndGetMaskForDisplayPropertyChanges(newDisplayInfo));
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_DISPLAY_EVENT_MERGING,
            Flags.FLAG_COMMITTED_STATE_SEPARATE_EVENT,
            Flags.FLAG_DISPLAY_LISTENER_PERFORMANCE_IMPROVEMENTS})
    public void testUpdateLogicalDisplays_multiplePropertyChangesAreBatched() {
        // This test verifies that when multiple properties of a single display change in one
        // update cycle, they are batched into a single event notification with a combined mask.
        when(mFlagsMock.isDisplayListenerPerformanceImprovementsEnabled()).thenReturn(true);
        when(mFlagsMock.isCommittedStateSeparateEventEnabled()).thenReturn(true);
        initLogicalDisplayMapper();

        // 1. Add an initial display device and let it settle.
        TestDisplayDevice device = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        LogicalDisplay display = add(device);
        assertEquals(DEFAULT_DISPLAY, id(display));
        clearInvocations(mListenerMock);

        // 2. Modify multiple properties of the display device info to trigger different events.
        DisplayDeviceInfo info = device.getSourceInfo();
        info.width = 601; // Triggers BASIC_CHANGED
        info.state = STATE_OFF; // Triggers STATE_CHANGED
        info.committedState = STATE_OFF; // Triggers COMMITTED_STATE_CHANGED
        // Create a new mode with a different refresh rate.
        info.supportedModes = new Display.Mode[]{
                new Display.Mode(/* modeId= */ 1, /* width= */ 601, /* height= */ 800,
                        /* refreshRate= */ 90f)
        };
        info.modeId = 1; // Triggers REFRESH_RATE_CHANGED

        // 3. Trigger an update by signaling that the display device has changed.
        mDisplayDeviceRepo.onDisplayDeviceEvent(device, DISPLAY_DEVICE_EVENT_CHANGED);

        // 4. Verify that a single event is sent with a combined mask.
        verify(mListenerMock).onLogicalDisplayEventLocked(
                mDisplayCaptor.capture(), mDisplayEventCaptor.capture());

        // Check that the correct display was updated.
        assertThat(mDisplayCaptor.getValue()).isEqualTo(display);

        // Check that the event mask contains all the expected change flags, confirming they were
        // batched.
        int capturedMask = mDisplayEventCaptor.getValue();
        int expectedMask = LOGICAL_DISPLAY_EVENT_BASIC_CHANGED
                | LOGICAL_DISPLAY_EVENT_REFRESH_RATE_CHANGED
                | LOGICAL_DISPLAY_EVENT_STATE_CHANGED
                | LOGICAL_DISPLAY_EVENT_COMMITTED_STATE_CHANGED;
        assertThat(capturedMask).isEqualTo(expectedMask);
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_DISPLAY_EVENT_MERGING,
            Flags.FLAG_DISPLAY_LISTENER_PERFORMANCE_IMPROVEMENTS})
    public void testUpdateLogicalDisplays_multipleDisplaysChange_sendsDisplayCentricEvents() {
        // This test verifies that when two different displays change, the system sends two
        // separate, display-specific notifications.
        when(mFlagsMock.isDisplayListenerPerformanceImprovementsEnabled()).thenReturn(true);
        initLogicalDisplayMapper();

        // 1. Add two display devices and let them settle.
        TestDisplayDevice device1 = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        TestDisplayDevice device2 = createDisplayDevice(TYPE_INTERNAL, 1024, 768, 0);
        LogicalDisplay display1 = add(device1);
        LogicalDisplay display2 = add(device2);
        clearInvocations(mListenerMock);

        // 2. Modify properties on both devices.
        DisplayDeviceInfo info1 = device1.getSourceInfo();
        info1.width = 601; // Triggers BASIC_CHANGED for display1
        info1.supportedModes = new Display.Mode[]{
                new Display.Mode(/* modeId= */ 1, /* width= */ 601, /* height= */ 800,
                        /* refreshRate= */ 60f)
        };
        info1.modeId = 1;
        // Triggers STATE_CHANGED for display2, this also includes BASIC_CHANGED since STATE is part
        // of BASIC_CHANGED events.
        device2.getSourceInfo().state = STATE_OFF;

        // 3. Notify the repository of each change individually.
        mDisplayDeviceRepo.onDisplayDeviceEvent(device1, DISPLAY_DEVICE_EVENT_CHANGED);
        mDisplayDeviceRepo.onDisplayDeviceEvent(device2, DISPLAY_DEVICE_EVENT_CHANGED);

        // 4. Verify that two separate, display-centric events are sent with their appropriate masks
        verify(mListenerMock, times(2)).onLogicalDisplayEventLocked(
                mDisplayCaptor.capture(), mDisplayEventCaptor.capture());

        List<LogicalDisplay> capturedDisplays = mDisplayCaptor.getAllValues();
        List<Integer> capturedMasks = mDisplayEventCaptor.getAllValues();

        boolean display1Found = false;
        boolean display2Found = false;

        for (int i = 0; i < capturedDisplays.size(); i++) {
            LogicalDisplay capturedDisplay = capturedDisplays.get(i);
            int capturedMask = capturedMasks.get(i);
            if (capturedDisplay.getDisplayIdLocked() == display1.getDisplayIdLocked()) {
                assertThat(capturedMask).isEqualTo(LOGICAL_DISPLAY_EVENT_BASIC_CHANGED);
                display1Found = true;
            } else if (capturedDisplay.getDisplayIdLocked() == display2.getDisplayIdLocked()) {
                assertThat(capturedMask).isEqualTo(LOGICAL_DISPLAY_EVENT_STATE_CHANGED
                        | LOGICAL_DISPLAY_EVENT_BASIC_CHANGED);
                display2Found = true;
            }
        }

        assertThat(display1Found).isTrue();
        assertThat(display2Found).isTrue();
    }

    /////////////////
    // Helper Methods
    /////////////////

    private void testDisplayDeviceAddAndRemove_Internal_Helper(boolean infoCacheEnabled) {
        DisplayDevice device = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        if (infoCacheEnabled) {
            clearInvocations(mDisplayInfoCacheMock);
        }
        // add
        LogicalDisplay displayAdded = add(device);
        if (infoCacheEnabled) {
            verify(mDisplayInfoCacheMock, atLeastOnce())
                    .put(eq(displayAdded.getDisplayIdLocked()), any());
        } else {
            verify(mDisplayInfoCacheMock, never()).put(anyInt(), any());
        }
        assertEquals(info(displayAdded).address, info(device).address);
        assertEquals(DEFAULT_DISPLAY, id(displayAdded));

        // remove
        mDisplayDeviceRepo.onDisplayDeviceEvent(device, DISPLAY_DEVICE_EVENT_REMOVED);
        verify(mListenerMock).onLogicalDisplayEventLocked(
                mDisplayCaptor.capture(), eq(LOGICAL_DISPLAY_EVENT_REMOVED));
        LogicalDisplay displayRemoved = mDisplayCaptor.getValue();
        assertEquals(DEFAULT_DISPLAY, id(displayRemoved));
        assertEquals(displayAdded, displayRemoved);
    }

    /**
     * Required to be called after any setup / test-specific initialisations of params.
     */
    private void initLogicalDisplayMapper() {
        mLogicalDisplayMapper = new LogicalDisplayMapper(mContextMock, mFoldSettingProviderMock,
                mDisplayDeviceRepo,
                mListenerMock, new DisplayManagerService.SyncRoot(), mHandler,
                mDeviceStateToLayoutMapSpy, mFlagsMock,
                mDisplayGroupAllocatorSpy, mIsDisplayAllowedInTopologyMock, mDisplayInfoCacheMock,
                mModeRequestManager);
        mLogicalDisplayMapper.onWindowManagerReady();
    }

    /**
     * creates DisplayGroupAllocator, set to conditions of device - eg projected mode, extended mode
     */
    private void setDisplayGroupAllocator(boolean simulateExtendedMode) {
        mDisplayGroupAllocatorSpy =
                spy(new DisplayGroupAllocator(mContextMock, new DisplayGroupAllocator.Injector() {

                    @Override
                    boolean isDesktopModeSupportedOnInternalDisplay(Context context) {
                        return simulateExtendedMode;
                    }

                    @Override
                    boolean canDisplayHostTasksLocked(LogicalDisplay display) {
                        return true;
                    }
                }));
        mDisplayGroupAllocatorSpy.initLater(mContextMock);
    }

    private void setFoldLockBehaviorSettingValue(String foldLockBehaviorSettingValue) {
        when(mFoldSettingProviderMock.shouldSleepOnFold()).thenReturn(false);
        when(mFoldSettingProviderMock.shouldStayAwakeOnFold()).thenReturn(false);
        when(mFoldSettingProviderMock.shouldSelectiveStayAwakeOnFold()).thenReturn(false);

        switch (foldLockBehaviorSettingValue) {
            case SETTING_VALUE_STAY_AWAKE_ON_FOLD:
                when(mFoldSettingProviderMock.shouldStayAwakeOnFold()).thenReturn(true);
                break;

            case SETTING_VALUE_SLEEP_ON_FOLD:
                when(mFoldSettingProviderMock.shouldSleepOnFold()).thenReturn(true);
                break;

            default:
                when(mFoldSettingProviderMock.shouldSelectiveStayAwakeOnFold()).thenReturn(true);
                break;
        }
    }

    private FoldableDisplayDevices createFoldableDeviceStateToLayoutMap() {
        TestDisplayDevice outer = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        TestDisplayDevice inner = createDisplayDevice(TYPE_INTERNAL, 600, 800,
                FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY);
        outer.setState(STATE_OFF);
        inner.setState(STATE_ON);

        Layout layout = new Layout();
        createDefaultDisplay(layout, outer);
        createNonDefaultDisplay(layout, inner, /* enabled= */ false, /* group= */ null);
        when(mDeviceStateToLayoutMapSpy.get(
                DEVICE_STATE_FOLDABLE_CLOSED.getIdentifier())).thenReturn(layout);

        layout = new Layout();
        createNonDefaultDisplay(layout, outer, /* enabled= */ false, /* group= */ null);
        createDefaultDisplay(layout, inner);
        when(mDeviceStateToLayoutMapSpy.get(
                DEVICE_STATE_FOLDABLE_HALF_OPEN.getIdentifier())).thenReturn(layout);
        when(mDeviceStateToLayoutMapSpy.get(DEVICE_STATE_FOLDABLE_OPEN.getIdentifier())).thenReturn(
                layout);
        when(mDeviceStateToLayoutMapSpy.size()).thenReturn(4);

        add(outer);
        add(inner);

        return new FoldableDisplayDevices(outer, inner);
    }

    private void finishBootAndTransitionBetweenStates(DeviceState fromState, DeviceState toState) {
        mLogicalDisplayMapper.setDeviceState(fromState);
        advanceTime(STATE_TRANSITION_DELAY);
        mLogicalDisplayMapper.onBootCompleted();
        advanceTime(1000);
        mLogicalDisplayMapper.setDeviceState(toState);
        advanceTime(STATE_TRANSITION_DELAY);
    }

    private void notifyDisplayChanges(TestDisplayDevice displayDevice) {
        mLogicalDisplayMapper.onDisplayDeviceChangedLocked(displayDevice, DIFF_EVERYTHING);
    }

    private void assertDisplayEnabled(DisplayDevice displayDevice) {
        assertThat(
                mLogicalDisplayMapper.getDisplayLocked(displayDevice).isEnabledLocked()).isTrue();
    }

    private void assertDisplayDisabled(DisplayDevice displayDevice) {
        assertThat(
                mLogicalDisplayMapper.getDisplayLocked(displayDevice).isEnabledLocked()).isFalse();
    }

    private void createDefaultDisplay(Layout layout, DisplayDevice device) {
        createDefaultDisplay(layout, info(device).address);
    }

    private void createDefaultDisplay(Layout layout, DisplayAddress address) {
        layout.createDefaultDisplayLocked(address, mIdProducer);
    }

    private void createNonDefaultDisplay(Layout layout, DisplayDevice device, boolean enabled,
            String group) {
        createNonDefaultDisplay(layout, info(device).address, enabled, group);
    }

    private void createNonDefaultDisplay(Layout layout, DisplayAddress address, boolean enabled,
            String group) {
        layout.createDisplayLocked(address, /* isDefault= */ false, enabled, group, mIdProducer,
                Layout.Display.POSITION_UNKNOWN, /* leadDisplayAddress= */ null,
                /* brightnessThrottlingMapId= */ null, /* refreshRateZoneId= */ null,
                /* refreshRateThermalThrottlingMapId= */ null, /* powerThrottlingMapId= */ null);
    }

    private void advanceTime(long timeMs) {
        mLooper.moveTimeForward(1000);
        mLooper.dispatchAll();
    }

    private TestDisplayDevice createDisplayDevice(int type, int width, int height, int flags) {
        return createDisplayDevice(
                createTestDisplayAddress(), /*  uniqueId */ "", type, width, height, flags);
    }

    private TestDisplayDevice createDisplayDevice(
            int type, String uniqueId, int width, int height, int flags) {
        return createDisplayDevice(
                createTestDisplayAddress(), uniqueId, type, width, height, flags);
    }

    private TestDisplayDevice createDisplayDevice(
            DisplayAddress address, String uniqueId, int type, int width, int height, int flags) {
        TestDisplayDevice device = new TestDisplayDevice();
        DisplayDeviceInfo displayDeviceInfo = device.getSourceInfo();
        displayDeviceInfo.type = type;
        displayDeviceInfo.uniqueId = uniqueId;
        displayDeviceInfo.width = width;
        displayDeviceInfo.height = height;
        displayDeviceInfo.flags = flags;
        displayDeviceInfo.supportedModes = new Display.Mode[1];
        displayDeviceInfo.supportedModes[0] = new Display.Mode(1, width, height, 60f);
        displayDeviceInfo.modeId = 1;
        displayDeviceInfo.address = address;
        return device;
    }

    private DisplayDeviceInfo info(DisplayDevice device) {
        return device.getDisplayDeviceInfoLocked();
    }

    private int width(DisplayDevice device) {
        return info(device).width;
    }

    private int height(DisplayDevice device) {
        return info(device).height;
    }

    private DisplayInfo info(LogicalDisplay display) {
        return display.getDisplayInfoLocked();
    }

    private int id(LogicalDisplay display) {
        return display.getDisplayIdLocked();
    }

    private LogicalDisplay add(DisplayDevice device) {
        mDisplayDeviceRepo.onDisplayDeviceEvent(device, DISPLAY_DEVICE_EVENT_ADDED);
        ArgumentCaptor<LogicalDisplay> displayCaptor =
                ArgumentCaptor.forClass(LogicalDisplay.class);
        verify(mListenerMock).onLogicalDisplayEventLocked(
                displayCaptor.capture(), eq(LOGICAL_DISPLAY_EVENT_CONNECTED));
        LogicalDisplay display = displayCaptor.getValue();
        if (display.isEnabledLocked()) {
            verify(mListenerMock).onLogicalDisplayEventLocked(
                    eq(display), eq(LOGICAL_DISPLAY_EVENT_ADDED));
        }
        clearInvocations(mListenerMock);
        return display;
    }

    private void testDisplayDeviceAddAndRemove_NonInternal(int type, boolean infoCacheEnabled) {
        DisplayDevice device = createDisplayDevice(type, 600, 800, 0);

        if (infoCacheEnabled) {
            clearInvocations(mDisplayInfoCacheMock);
        }
        // add
        LogicalDisplay displayAdded = add(device);
        if (infoCacheEnabled) {
            verify(mDisplayInfoCacheMock, atLeastOnce())
                    .put(eq(displayAdded.getDisplayIdLocked()), any());
        } else {
            verify(mDisplayInfoCacheMock, never()).put(anyInt(), any());
        }
        assertEquals(info(displayAdded).address, info(device).address);
        assertNotEquals(DEFAULT_DISPLAY, id(displayAdded));

        // remove
        mDisplayDeviceRepo.onDisplayDeviceEvent(device, DISPLAY_DEVICE_EVENT_REMOVED);
        verify(mListenerMock).onLogicalDisplayEventLocked(
                mDisplayCaptor.capture(), eq(LOGICAL_DISPLAY_EVENT_REMOVED));
        LogicalDisplay displayRemoved = mDisplayCaptor.getValue();
        assertNotEquals(DEFAULT_DISPLAY, id(displayRemoved));
    }

    private static DeviceState createDeviceState(int identifier, @NonNull String name,
            @NonNull Set<@DeviceState.SystemDeviceStateProperties Integer> systemProperties,
            @NonNull Set<@DeviceState.PhysicalDeviceStateProperties Integer> physicalProperties) {
        DeviceState.Configuration deviceStateConfiguration = new DeviceState.Configuration.Builder(
                identifier, name).setSystemProperties(systemProperties).setPhysicalProperties(
                physicalProperties).build();
        return new DeviceState(deviceStateConfiguration);
    }

    private final static class FoldableDisplayDevices {
        final TestDisplayDevice mOuter;
        final TestDisplayDevice mInner;

        FoldableDisplayDevices(TestDisplayDevice outer, TestDisplayDevice inner) {
            this.mOuter = outer;
            this.mInner = inner;
        }
    }

    class TestDisplayDevice extends DisplayDevice {
        private DisplayDeviceInfo mInfo;
        private DisplayDeviceInfo mSentInfo;
        private int mState;

        TestDisplayDevice() {
            super(mDisplayAdapterMock, /* displayToken= */ null,
                    "test_display_" + sUniqueTestDisplayId++, mContextMock);
            mInfo = new DisplayDeviceInfo();
        }

        @Override
        public DisplayDeviceInfo getDisplayDeviceInfoLocked() {
            if (mSentInfo == null) {
                mSentInfo = new DisplayDeviceInfo();
                mSentInfo.copyFrom(mInfo);
            }
            return mSentInfo;
        }

        @Override
        public void applyPendingDisplayDeviceInfoChangesLocked() {
            mSentInfo = null;
        }

        public void setState(int state) {
            mState = state;
            if (mSentInfo == null) {
                mInfo.state = state;
            } else {
                mInfo.state = state;
                mSentInfo.state = state;
            }
        }

        @Override
        public boolean hasStableUniqueId() {
            return true;
        }

        public DisplayDeviceInfo getSourceInfo() {
            return mInfo;
        }
    }
}
