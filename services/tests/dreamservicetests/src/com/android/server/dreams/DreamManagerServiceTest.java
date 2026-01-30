/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.server.dreams;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.BatteryManager.EXTRA_CHARGING_STATUS;
import static android.service.dreams.Flags.FLAG_ALLOW_DREAM_WITH_CHARGE_LIMIT;
import static android.service.dreams.Flags.FLAG_DISALLOW_DREAM_ON_AUTO_PROJECTION;
import static android.service.dreams.Flags.FLAG_DREAMS_V2;
import static android.service.dreams.Flags.FLAG_WAKE_ON_STOPPING_DOZE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.server.dreams.DreamManagerService.CHARGE_LIMIT_PERCENTAGE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.ActivityManagerInternal;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.display.AmbientDisplayConfiguration;
import android.hardware.health.BatteryChargingState;
import android.os.BatteryManager;
import android.os.BatteryManagerInternal;
import android.os.Binder;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.testing.TestableContext;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.util.test.LocalServiceKeeperRule;
import com.android.server.SystemService;
import com.android.server.input.InputManagerInternal;
import com.android.server.testutils.TestHandler;
import com.android.server.wm.ActivityTaskManagerInternal;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Test class for {@link DreamManagerService}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class DreamManagerServiceTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private ContextWrapper mContextSpy;

    @Mock
    private DreamController mDreamControllerMock;

    @Mock
    private ActivityManagerInternal mActivityManagerInternalMock;
    @Mock
    private ActivityTaskManagerInternal mActivityTaskManagerInternalMock;
    @Mock
    private BatteryManagerInternal mBatteryManagerInternal;

    @Mock
    private InputManagerInternal mInputManagerInternal;
    @Mock
    private PackageManager mPackageManagerMock;
    @Mock
    private PowerManagerInternal mPowerManagerInternalMock;
    @Mock
    private PowerManager mPowerManagerMock;
    @Mock
    private UiModeManager mUiModeManagerMock;
    @Mock
    private UserManager mUserManagerMock;
    @Mock
    private PowerManager.WakeLock mWakeLockMock;
    @Mock
    private AmbientDisplayConfiguration mDozeConfigMock;

    @Rule
    public LocalServiceKeeperRule mLocalServiceKeeperRule = new LocalServiceKeeperRule();

    @Rule
    public final TestableContext mContext = new TestableContext(getInstrumentation().getContext());

    private TestHandler mTestHandler;

    @Before
    public void setUp() throws Exception {
        mTestHandler = new TestHandler(/* callback= */ null);
        MockitoAnnotations.initMocks(this);

        mContext.getTestablePermissions().setPermission(
                Manifest.permission.READ_PROJECTION_STATE, PERMISSION_GRANTED);

        mContextSpy = spy(mContext);

        mLocalServiceKeeperRule.overrideLocalService(ActivityManagerInternal.class,
                mActivityManagerInternalMock);
        mLocalServiceKeeperRule.overrideLocalService(ActivityTaskManagerInternal.class,
                mActivityTaskManagerInternalMock);
        mLocalServiceKeeperRule.overrideLocalService(BatteryManagerInternal.class,
                mBatteryManagerInternal);
        mLocalServiceKeeperRule.overrideLocalService(InputManagerInternal.class,
                mInputManagerInternal);
        mLocalServiceKeeperRule.overrideLocalService(PowerManagerInternal.class,
                mPowerManagerInternalMock);

        Settings.Secure.putInt(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP, 0);
        Settings.Secure.putInt(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_POSTURED, 0);
        Settings.Secure.putInt(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_RESTRICT_TO_WIRELESS_CHARGING, 0);

        when(mPowerManagerMock.newWakeLock(anyInt(), any())).thenReturn(mWakeLockMock);

        doReturn(mContextSpy).when(mContextSpy).createContextAsUser(any(), anyInt());
        when(mContextSpy.getPackageManager()).thenReturn(mPackageManagerMock);
        when(mContextSpy.getSystemService(PowerManager.class)).thenReturn(mPowerManagerMock);
        when(mContextSpy.getSystemService(UserManager.class)).thenReturn(mUserManagerMock);
        when(mContextSpy.getSystemService(UiModeManager.class)).thenReturn(mUiModeManagerMock);

        when(mDozeConfigMock.ambientDisplayComponent())
                .thenReturn("test.doze.component/.TestDozeService");
    }

    private DreamManagerService createService() {
        return new DreamManagerService(
                new TestInjector(mContextSpy, mTestHandler, mDreamControllerMock, mDozeConfigMock));
    }

    /**
     * Starts dreaming and returns the dream token.
     */
    private Binder startDream(DreamManagerService service) {
        service.startDreamInternal(/*doze=*/ true, "testing");

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mWakeLockMock).wrap(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        ArgumentCaptor<Binder> dreamTokenCaptor = ArgumentCaptor.forClass(Binder.class);
        verify(mDreamControllerMock)
                .startDream(
                        dreamTokenCaptor.capture(),
                        any(),
                        anyBoolean(),
                        anyBoolean(),
                        anyInt(),
                        any(),
                        any(),
                        any());
        return dreamTokenCaptor.getValue();
    }

    /**
     * Trigger battery change event so charging state is read.
     */
    private void sendBatteryChangeEvent() {
        ArgumentCaptor<BroadcastReceiver> receiverCaptor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        verify(mContextSpy).registerReceiver(receiverCaptor.capture(),
                argThat((arg) -> arg.hasAction(Intent.ACTION_BATTERY_CHANGED)));
        receiverCaptor.getValue().onReceive(mContextSpy, new Intent());
    }

    @Test
    public void testSettingsQueryUserChange() {
        // Enable dreams.
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ENABLED, 1, UserHandle.USER_CURRENT);

        // Initialize dream service so settings are read.
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        // Dreams are enabled.
        assertThat(service.dreamsEnabled()).isTrue();

        // Disable dreams.
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ENABLED, 0, UserHandle.USER_CURRENT);

        // Switch users, dreams are disabled.
        service.onUserSwitching(null, null);
        assertThat(service.dreamsEnabled()).isFalse();
    }

    @Test
    public void testCanStartDreaming_charging() {
        // Enable dreaming while charging only.
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ENABLED, 1, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP, 1, UserHandle.USER_CURRENT);

        // Set up preconditions.
        when(mUserManagerMock.isUserUnlocked()).thenReturn(true);

        // Device is charging.
        when(mBatteryManagerInternal.isPowered(anyInt())).thenReturn(true);

        // Initialize service so settings are read.
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        // Battery changed event is received.
        sendBatteryChangeEvent();

        // Can start dreaming is true.
        assertThat(service.canStartDreamingInternal(/*isScreenOn=*/ true)).isTrue();
    }

    @EnableFlags(FLAG_DISALLOW_DREAM_ON_AUTO_PROJECTION)
    @Test
    public void testCanStartDreaming_falseWithProjectedDisplay() {
        // Enable dreaming while charging only.
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ENABLED, 1, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP, 1, UserHandle.USER_CURRENT);

        // Device is charging.
        when(mBatteryManagerInternal.isPowered(anyInt())).thenReturn(true);

        // Connected to Android Auto.
        when(mUiModeManagerMock.getActiveProjectionTypes())
                .thenReturn(UiModeManager.PROJECTION_TYPE_NONE);

        // Initialize service so settings are read.
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        // Battery changed event is received.
        sendBatteryChangeEvent();

        // Can start dreaming is true.
        assertThat(service.canStartDreamingInternal(/*isScreenOn=*/ true)).isFalse();
    }

    @EnableFlags(FLAG_WAKE_ON_STOPPING_DOZE)
    @Test
    public void testStopDream_sendsWakeIfDozing() throws PackageManager.NameNotFoundException {
        // Enable dreaming while charging only.
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ENABLED, 1, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP, 1, UserHandle.USER_CURRENT);

        // Set up preconditions.
        ServiceInfo dozeServiceInfo = new ServiceInfo();
        dozeServiceInfo.applicationInfo = new ApplicationInfo();
        when(mUserManagerMock.isUserUnlocked()).thenReturn(true);
        when(mDozeConfigMock.enabled(anyInt())).thenReturn(true);
        when(mPackageManagerMock.getServiceInfo(any(), anyInt())).thenReturn(dozeServiceInfo);

        // Device is charging.
        when(mBatteryManagerInternal.isPowered(anyInt())).thenReturn(true);

        // Initialize service so settings are read.
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        // Battery changed event is received.
        sendBatteryChangeEvent();

        // Start dream.
        final Binder dreamToken = startDream(service);

        // Start dozing.
        service.startDozingInternal(dreamToken, 0, 0, 0f, false);

        // Stop dreaming.
        service.stopDreamInternal(true, "testing");

        // wakeUp is sent.
        verify(mPowerManagerMock)
                .wakeUp(anyLong(), eq(PowerManager.WAKE_REASON_DOZE_STOPPED), any());
    }

    @Test
    public void testDreamConditionActive_onDock() {
        // Enable dreaming on dock.
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_DOCK, 1, UserHandle.USER_CURRENT);

        // Initialize service so settings are read.
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);
        assertThat(service.dreamConditionActiveInternal()).isFalse();

        // Dock event receiver is registered.
        ArgumentCaptor<BroadcastReceiver> receiverCaptor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        verify(mContextSpy).registerReceiver(receiverCaptor.capture(),
                argThat((arg) -> arg.hasAction(Intent.ACTION_DOCK_EVENT)));

        // Device is docked.
        Intent dockIntent = new Intent(Intent.ACTION_DOCK_EVENT);
        dockIntent.putExtra(Intent.EXTRA_DOCK_STATE, Intent.EXTRA_DOCK_STATE_HE_DESK);
        receiverCaptor.getValue().onReceive(null, dockIntent);

        // Dream condition is active.
        assertThat(service.dreamConditionActiveInternal()).isTrue();
    }

    @Test
    public void testDreamConditionActive_postured() {
        // Enable dreaming while postured.
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_DOCK, 0, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_POSTURED, 1, UserHandle.USER_CURRENT);

        // Initialize service so settings are read.
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);
        assertThat(service.dreamConditionActiveInternal()).isFalse();

        // Device is postured.
        service.setDevicePosturedInternal(true);

        // Dream condition is active.
        assertThat(service.dreamConditionActiveInternal()).isTrue();
    }

    @Test
    public void testDreamConditionActive_charging() {
        // Enable dreaming while charging only.
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP, 1, UserHandle.USER_CURRENT);

        // Device is charging.
        when(mBatteryManagerInternal.isPowered(anyInt())).thenReturn(true);

        // Initialize service so settings are read.
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        // Battery changed event is received.
        sendBatteryChangeEvent();

        // Dream condition is active.
        assertThat(service.dreamConditionActiveInternal()).isTrue();
    }

    @EnableFlags(FLAG_ALLOW_DREAM_WITH_CHARGE_LIMIT)
    @Test
    public void testDreamConditionActive_chargeLimitActive() {
        // Enable dreaming while charging only.
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP, 1, UserHandle.USER_CURRENT);
        // Enable charge limit setting.
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.CHARGE_OPTIMIZATION_MODE, 1, UserHandle.USER_CURRENT);

        // Device is not considered charging when charge limit is on.
        when(mBatteryManagerInternal.isPowered(anyInt())).thenReturn(false);
        when(mBatteryManagerInternal.getBatteryLevel()).thenReturn(CHARGE_LIMIT_PERCENTAGE);

        // Initialize service so settings are read.
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        // Battery changed event is received.
        ArgumentCaptor<BroadcastReceiver> receiverCaptor = ArgumentCaptor.forClass(
                BroadcastReceiver.class);
        verify(mContextSpy).registerReceiver(receiverCaptor.capture(),
                argThat((arg) -> arg.hasAction(Intent.ACTION_BATTERY_CHANGED)));
        Intent intent = new Intent();
        intent.putExtra(EXTRA_CHARGING_STATUS, BatteryChargingState.LONG_LIFE);
        receiverCaptor.getValue().onReceive(mContext, intent);

        // Dream condition is active.
        assertThat(service.dreamConditionActiveInternal()).isTrue();
    }

    @EnableFlags(FLAG_DREAMS_V2)
    @Test
    public void testDreamConditionActive_onlyWirelessCharging_falseWhenNotWirelessCharging() {
        // Enable dreaming while wireless charging only.
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP, 1, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_RESTRICT_TO_WIRELESS_CHARGING, 1,
                UserHandle.USER_CURRENT);

        // Device is charging but not wirelessly.
        when(mBatteryManagerInternal.isPowered(BatteryManager.BATTERY_PLUGGED_ANY)).thenReturn(
                true);

        // Initialize service so settings are read.
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        // Dream condition is not active.
        assertThat(service.dreamConditionActiveInternal()).isFalse();
    }

    @EnableFlags(FLAG_DREAMS_V2)
    @Test
    public void testDreamConditionActive_onlyWirelessCharging_trueWhenWirelessCharging() {
        // Enable dreaming while wireless charging only.
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP, 1, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_RESTRICT_TO_WIRELESS_CHARGING, 1,
                UserHandle.USER_CURRENT);

        // Device is charging wirelessly.
        when(mBatteryManagerInternal.isPowered(BatteryManager.BATTERY_PLUGGED_WIRELESS)).thenReturn(
                true);

        // Initialize service so settings are read.
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        // Dream condition is active.
        assertThat(service.dreamConditionActiveInternal()).isTrue();
    }

    private static final class TestInjector implements DreamManagerService.Injector {
        private final Context mContext;
        private final Handler mHandler;
        private final DreamController mDreamController;
        private final AmbientDisplayConfiguration mDozeConfig;

        TestInjector(Context context, Handler handler, DreamController dreamController,
                AmbientDisplayConfiguration dozeConfig) {
            mContext = context;
            mHandler = handler;
            mDreamController = dreamController;
            mDozeConfig = dozeConfig;
        }

        @Override
        public Context getContext() {
            return mContext;
        }

        @Override
        public Handler getHandler() {
            return mHandler;
        }

        @Override
        public AmbientDisplayConfiguration getDozeConfig() {
            return mDozeConfig;
        }

        @Override
        public DreamController getDreamController(DreamController.Listener controllerListener) {
            return mDreamController;
        }
    }
}
