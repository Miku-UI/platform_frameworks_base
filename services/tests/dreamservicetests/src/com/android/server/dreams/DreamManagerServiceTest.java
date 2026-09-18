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
import static android.service.dreams.Flags.FLAG_DREAMS_SWITCHER;
import static android.service.dreams.Flags.FLAG_DREAMS_V2;
import static android.service.dreams.Flags.FLAG_SYSTEM_DREAM_DEATH_RECIPIENT;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.server.dreams.DreamManagerService.CHARGE_LIMIT_PERCENTAGE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.annotation.UserIdInt;
import android.app.ActivityManagerInternal;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.UserInfo;
import android.hardware.display.AmbientDisplayConfiguration;
import android.hardware.health.BatteryChargingState;
import android.os.BatteryManager;
import android.os.BatteryManagerInternal;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.test.TestLooper;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.service.dreams.DreamItem;
import android.service.dreams.DreamPlaylist;
import android.service.dreams.IDreamManagerListener;
import android.testing.TestableContext;
import android.testing.TestableResources;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.util.test.LocalServiceKeeperRule;
import com.android.server.SystemService;
import com.android.server.input.InputManagerInternal;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wm.ActivityTaskManagerInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;

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
    private PackageManagerInternal mPackageManagerInternalMock;
    @Mock
    private PowerManagerInternal mPowerManagerInternalMock;
    @Mock
    private PowerManager mPowerManagerMock;
    @Mock
    private UiModeManager mUiModeManagerMock;
    @Mock
    private UserManager mUserManagerMock;
    @Mock private UserManagerInternal mUserManagerInternalMock;
    @Mock private PowerManager.WakeLock mWakeLockMock;
    @Mock
    private AmbientDisplayConfiguration mDozeConfigMock;
    @Mock
    private DreamComponentsResolver mDreamComponentsResolver;

    @Rule
    public LocalServiceKeeperRule mLocalServiceKeeperRule = new LocalServiceKeeperRule();

    @Rule
    public final TestableContext mContext = new TestableContext(getInstrumentation().getContext());

    private TestLooper mTestLooper;
    private TestableResources mResources;
    private UserHandle mCurrentUser = UserHandle.of(0);

    @Before
    public void setUp() throws Exception {
        mTestLooper = new TestLooper();
        MockitoAnnotations.initMocks(this);

        mContext.getTestablePermissions().setPermission(
                Manifest.permission.READ_DREAM_STATE, PERMISSION_GRANTED);
        mContext.getTestablePermissions().setPermission(
                Manifest.permission.READ_PROJECTION_STATE, PERMISSION_GRANTED);

        mContextSpy = spy(mContext);
        mResources = mContext.getOrCreateTestableResources();

        mLocalServiceKeeperRule.overrideLocalService(ActivityManagerInternal.class,
                mActivityManagerInternalMock);
        mLocalServiceKeeperRule.overrideLocalService(ActivityTaskManagerInternal.class,
                mActivityTaskManagerInternalMock);
        mLocalServiceKeeperRule.overrideLocalService(BatteryManagerInternal.class,
                mBatteryManagerInternal);
        mLocalServiceKeeperRule.overrideLocalService(InputManagerInternal.class,
                mInputManagerInternal);
        mLocalServiceKeeperRule.overrideLocalService(PackageManagerInternal.class,
                mPackageManagerInternalMock);
        mLocalServiceKeeperRule.overrideLocalService(PowerManagerInternal.class,
                mPowerManagerInternalMock);
        mLocalServiceKeeperRule.overrideLocalService(
                UserManagerInternal.class, mUserManagerInternalMock);

        Settings.Secure.putInt(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP, 0);
        Settings.Secure.putInt(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_POSTURED, 0);
        Settings.Secure.putInt(
                mContextSpy.getContentResolver(), Settings.Secure.SCREENSAVER_ACTIVATE_ON_DOCK, 0);
        Settings.Secure.putInt(
                mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_RESTRICT_TO_WIRELESS_CHARGING,
                0);

        when(mPowerManagerMock.newWakeLock(anyInt(), any())).thenReturn(mWakeLockMock);
        when(mWakeLockMock.wrap(any(Runnable.class))).thenAnswer(
                invocation -> invocation.getArguments()[0]);
        when(mUserManagerInternalMock.getMainUserId()).thenReturn(mCurrentUser.getIdentifier());

        doReturn(mContextSpy).when(mContextSpy).createContextAsUser(any(), anyInt());
        doReturn(mResources.getResources()).when(mContextSpy).getResources();
        when(mContextSpy.getPackageManager()).thenReturn(mPackageManagerMock);
        when(mContextSpy.getSystemService(PowerManager.class)).thenReturn(mPowerManagerMock);
        when(mContextSpy.getSystemService(UserManager.class)).thenReturn(mUserManagerMock);
        when(mContextSpy.getSystemService(UiModeManager.class)).thenReturn(mUiModeManagerMock);
        when(mDozeConfigMock.ambientDisplayComponent())
                .thenReturn("com.android.systemui/.doze.DozeService");
    }

    @After
    public void tearDown() {
        mResources.removeOverride(
                com.android.internal.R.bool.config_supportDreamWirelessChargingRestriction);
    }

    private DreamManagerService createService() {
        return new DreamManagerService(
                new TestInjector(
                        mContextSpy,
                        new Handler(mTestLooper.getLooper()),
                        mDreamControllerMock,
                        mDozeConfigMock,
                        mDreamComponentsResolver,
                        mCurrentUser.getIdentifier()));
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
    public void testCanStartDreaming_charging() throws PackageManager.NameNotFoundException {
        enableDreaming();
        setupDreamPreconditions();
        final ComponentName dream = new ComponentName("a", "b");
        when(mDreamComponentsResolver.resolve(anyBoolean(), anyBoolean(), any()))
                .thenReturn(dream);

        // Initialize service so settings are read.
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        // Battery changed event is received.
        sendBatteryChangeEvent();

        // Can start dreaming is true.
        assertThat(service.canStartDreamingInternal(/*isScreenOn=*/ true)).isTrue();
    }

    @Test
    public void testCanStartDreaming_returnsFalseWhenNoDreamConfigured() {
        enableDreaming();
        setupDreamPreconditions();
        when(mDreamComponentsResolver.resolve(anyBoolean(), anyBoolean(), any()))
                .thenReturn(null);

        // Initialize service so settings are read.
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        // Battery changed event is received.
        sendBatteryChangeEvent();

        // Can't start dreaming because no dream is configured.
        assertThat(service.canStartDreamingInternal(true)).isFalse();
    }

    @Test
    public void testCanStartDreaming_falseWithProjectedDisplay() {
        // Enable dreaming while charging only.
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ENABLED, 1, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP, 1, UserHandle.USER_CURRENT);

        // Device is charging.
        when(mBatteryManagerInternal.isPowered(eq(BatteryManager.BATTERY_PLUGGED_ANY)))
                .thenReturn(true);

        // Connected to Android Auto.
        when(mUiModeManagerMock.getActiveProjectionTypes())
                .thenReturn(UiModeManager.PROJECTION_TYPE_AUTOMOTIVE);

        // Initialize service so settings are read.
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        // Battery changed event is received.
        sendBatteryChangeEvent();

        // Can start dreaming is true.
        assertThat(service.canStartDreamingInternal(/*isScreenOn=*/ true)).isFalse();
    }

    @Test
    public void testStopDream_sendsWakeIfDozing() throws PackageManager.NameNotFoundException {
        // Enable dreaming while charging only.
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ENABLED, 1, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP, 1, UserHandle.USER_CURRENT);

        // Set up preconditions.
        when(mUserManagerMock.isUserUnlocked(anyInt())).thenReturn(true);
        final ComponentName dream = new ComponentName("a", "b");
        when(mDreamComponentsResolver.resolve(anyBoolean(), anyBoolean(), any()))
                .thenReturn(dream);

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
        when(mBatteryManagerInternal.isPowered(eq(BatteryManager.BATTERY_PLUGGED_ANY)))
                .thenReturn(true);

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

        mResources.addOverride(
                com.android.internal.R.bool.config_dreamsOnlyEnabledForDockUser, false);
        mResources.addOverride(
                com.android.internal.R.bool.config_supportDreamWirelessChargingRestriction, true);

        // Device is charging but not wirelessly.
        when(mBatteryManagerInternal.isPowered(eq(BatteryManager.BATTERY_PLUGGED_ANY)))
                .thenReturn(true);
        when(mBatteryManagerInternal.isPowered(eq(BatteryManager.BATTERY_PLUGGED_WIRELESS)))
                .thenReturn(false);

        // Initialize service so settings are read.
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        sendBatteryChangeEvent();

        // Dream condition is not active.
        assertThat(service.dreamConditionActiveInternal()).isFalse();
    }

    @EnableFlags(FLAG_DREAMS_V2)
    @Test
    public void testDreamConditionActive_onlyWirelessCharging_trueWhenNotSupported() {
        // Enable dreaming while wireless charging only.
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP, 1, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_RESTRICT_TO_WIRELESS_CHARGING, 1,
                UserHandle.USER_CURRENT);

        // Wireless charging restriction is not supported on this device.
        mResources.addOverride(
                com.android.internal.R.bool.config_supportDreamWirelessChargingRestriction, false);

        // Device is charging but not wirelessly.
        when(mBatteryManagerInternal.isPowered(eq(BatteryManager.BATTERY_PLUGGED_ANY)))
                .thenReturn(true);
        when(mBatteryManagerInternal.isPowered(eq(BatteryManager.BATTERY_PLUGGED_WIRELESS)))
                .thenReturn(false);

        // Initialize service so settings are read.
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        sendBatteryChangeEvent();

        // Dream condition is active.
        assertThat(service.dreamConditionActiveInternal()).isTrue();
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

        mResources.addOverride(
                com.android.internal.R.bool.config_supportDreamWirelessChargingRestriction, true);

        // Device is charging wirelessly.
        when(mBatteryManagerInternal.isPowered(eq(BatteryManager.BATTERY_PLUGGED_WIRELESS)))
                .thenReturn(true);
        when(mBatteryManagerInternal.isPowered(eq(BatteryManager.BATTERY_PLUGGED_ANY)))
                .thenReturn(true);

        // Initialize service so settings are read.
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        sendBatteryChangeEvent();

        // Dream condition is active.
        assertThat(service.dreamConditionActiveInternal()).isTrue();
    }

    @Test
    public void testStartDream_startsResolvedDream() throws PackageManager.NameNotFoundException {
        enableDreaming();
        setupDreamPreconditions();

        final ComponentName resolvedDream =
                ComponentName.unflattenFromString("default.package/.DefaultDream");
        when(mDreamComponentsResolver.resolve(anyBoolean(), anyBoolean(), any()))
                .thenReturn(resolvedDream);

        // Initialize service and trigger dream.
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);
        sendBatteryChangeEvent();
        service.startDreamInternal(false, "testing");

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mWakeLockMock).wrap(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        // Verify that the resolved dream is started.
        ArgumentCaptor<ComponentName> componentNameCaptor =
                ArgumentCaptor.forClass(ComponentName.class);
        verify(mDreamControllerMock).startDream(
                any(Binder.class),
                componentNameCaptor.capture(),
                eq(false),
                eq(false),
                anyInt(),
                any(PowerManager.WakeLock.class),
                any(),
                anyString());
        assertThat(componentNameCaptor.getValue()).isEqualTo(resolvedDream);
    }

    @Test
    @EnableFlags(FLAG_SYSTEM_DREAM_DEATH_RECIPIENT)
    public void systemDreamComponent_isClearedOnBinderDeath() throws Exception {
        enableDreaming();
        setupDreamPreconditions();

        // Set up a user-configured dream to verify fallback.
        final ComponentName userDream = new ComponentName("user", "dream");
        Settings.Secure.putStringForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_COMPONENTS,
                userDream.flattenToString(),
                UserHandle.USER_CURRENT);

        when(mDreamComponentsResolver.resolve(anyBoolean(), anyBoolean(), any()))
                .thenAnswer(invocation -> {
                    ComponentName systemDreamArg = invocation.getArgument(2);
                    return systemDreamArg != null ? systemDreamArg : userDream;
                });

        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        final ComponentName systemDream = new ComponentName("system", "dream");
        final IBinder token = mock(IBinder.class);

        // Set a system dream.
        service.setSystemDreamComponentInternal(systemDream, token);

        // Capture the death recipient so we can trigger it.
        ArgumentCaptor<IBinder.DeathRecipient> deathRecipientCaptor =
                ArgumentCaptor.forClass(IBinder.DeathRecipient.class);
        verify(token).linkToDeath(deathRecipientCaptor.capture(), eq(0));
        final IBinder.DeathRecipient deathRecipient = deathRecipientCaptor.getValue();

        // Simulate the client process dying.
        deathRecipient.binderDied();

        // Verify the system service cleaned up the token.
        verify(token).unlinkToDeath(deathRecipient, 0);

        // Trigger a state update (battery change) to force a dream start evaluation.
        sendBatteryChangeEvent();

        // Start a dream, which should now be the user-configured dream.
        service.startDreamInternal(false, "testing");

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mWakeLockMock).wrap(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        ArgumentCaptor<ComponentName> componentNameCaptor =
                ArgumentCaptor.forClass(ComponentName.class);
        verify(mDreamControllerMock).startDream(
                any(Binder.class),
                componentNameCaptor.capture(),
                eq(false), /* isPreviewMode */
                eq(false), /* canDoze */
                anyInt(),
                any(PowerManager.WakeLock.class),
                any(),
                anyString());

        assertThat(componentNameCaptor.getValue()).isEqualTo(userDream);
    }

    @Test
    public void testStartDream_doesNotStartWhenNoDreamResolved()
            throws PackageManager.NameNotFoundException {
        enableDreaming();
        setupDreamPreconditions();

        when(mDreamComponentsResolver.resolve(anyBoolean(), anyBoolean(), any()))
                .thenReturn(null);

        // Initialize service and trigger dream.
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);
        sendBatteryChangeEvent();
        service.startDreamInternal(false, "testing");

        // Verify that no dream is started.
        verify(mDreamControllerMock, never()).startDream(
                any(Binder.class),
                any(ComponentName.class),
                anyBoolean(),
                anyBoolean(),
                anyInt(),
                any(PowerManager.WakeLock.class),
                any(),
                anyString());
    }

    @Test
    @EnableFlags(FLAG_DREAMS_SWITCHER)
    public void testRegisterListener_updateOnSettingsChange() throws Exception {
        // Initialize service
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        // Register the listener
        final IDreamManagerListener listener = registerListener(service);

        // Change the settings
        final ComponentName dream1 = ComponentName.unflattenFromString("com.test/.Dream1");
        setupDreamComponent(Settings.Secure.SCREENSAVER_COMPONENTS, dream1, true);

        service.refreshSettings(
                mCurrentUser.getIdentifier(),
                Settings.Secure.getUriFor(Settings.Secure.SCREENSAVER_COMPONENTS));

        when(mDreamComponentsResolver.getDreamPlaylist(any()))
                .thenReturn(
                        new DreamPlaylist(
                                Collections.singletonList(new DreamItem.Builder(dream1).build()),
                                0));

        // Flush handler
        mTestLooper.dispatchAll();
        mTestLooper.moveTimeForward(1000); // Wait for debounce
        mTestLooper.dispatchAll();

        // Verify updated playlist callback
        verify(listener)
                .onPlaylistChanged(
                        eq(
                                new DreamPlaylist(
                                        Collections.singletonList(
                                                new DreamItem.Builder(dream1).build()),
                                        0)));
    }

    @Test
    @EnableFlags(FLAG_DREAMS_SWITCHER)
    public void testRegisterListener_noUpdateOnIrrelevantSettingsChange() throws Exception {
        // Initialize service
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        // Register the listener
        final IDreamManagerListener listener = registerListener(service);

        // Change an irrelevant setting
        service.refreshSettings(
                mCurrentUser.getIdentifier(),
                Settings.Secure.getUriFor(Settings.Secure.SCREENSAVER_ENABLED));

        // Flush handler
        mTestLooper.dispatchAll();

        // Verify NO updated playlist callback
        verify(listener, never()).onPlaylistChanged(any());
    }

    @Test
    @EnableFlags(FLAG_DREAMS_SWITCHER)
    public void testUnregisterListener() throws Exception {
        // Initialize service
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        // Register the listener
        final IDreamManagerListener listener = registerListener(service);

        // Unregister the listener
        service.unregisterListener(listener, mCurrentUser.getIdentifier());

        // Change settings
        final ComponentName dream1 = ComponentName.unflattenFromString("com.test/.Dream1");
        setupDreamComponent(Settings.Secure.SCREENSAVER_COMPONENTS, dream1, true);

        service.refreshSettings(
                mCurrentUser.getIdentifier(),
                Settings.Secure.getUriFor(Settings.Secure.SCREENSAVER_COMPONENTS));

        // Flush handler
        mTestLooper.dispatchAll();
        mTestLooper.moveTimeForward(1000); // Wait for debounce
        mTestLooper.dispatchAll();

        // Verify NO updated playlist callback
        verify(listener, never()).onPlaylistChanged(any(DreamPlaylist.class));
    }

    @Test
    @EnableFlags(FLAG_DREAMS_SWITCHER)
    public void testNotifyPlaylistChanged_deduplication() throws Exception {
        // Initialize service
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        // Register the listener
        final IDreamManagerListener listener = registerListener(service);

        // Trigger change 1
        final ComponentName dream1 = ComponentName.unflattenFromString("com.test/.Dream1");
        setupDreamComponent(Settings.Secure.SCREENSAVER_COMPONENTS, dream1, true);
        when(mDreamComponentsResolver.getDreamPlaylist(any()))
                .thenReturn(
                        new DreamPlaylist(
                                Collections.singletonList(new DreamItem.Builder(dream1).build()),
                                0));

        service.refreshSettings(
                mCurrentUser.getIdentifier(),
                Settings.Secure.getUriFor(Settings.Secure.SCREENSAVER_COMPONENTS));
        mTestLooper.dispatchAll();
        mTestLooper.moveTimeForward(1000);
        mTestLooper.dispatchAll();

        verify(listener).onPlaylistChanged(any(DreamPlaylist.class));
        clearInvocations(listener);

        // Trigger change 1 again (same settings)
        service.refreshSettings(
                mCurrentUser.getIdentifier(),
                Settings.Secure.getUriFor(Settings.Secure.SCREENSAVER_COMPONENTS));
        mTestLooper.dispatchAll();
        mTestLooper.moveTimeForward(1000);
        mTestLooper.dispatchAll();

        // Verify NO callback
        verify(listener, never()).onPlaylistChanged(any(DreamPlaylist.class));
    }

    @Test
    @EnableFlags(FLAG_DREAMS_SWITCHER)
    public void testNotifyPlaylistChanged_stateChange() throws Exception {
        // Initialize service
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        // Register the listener
        final IDreamManagerListener listener = registerListener(service);

        // Trigger change 1
        final ComponentName dream1 = ComponentName.unflattenFromString("com.test/.Dream1");
        setupDreamComponent(Settings.Secure.SCREENSAVER_COMPONENTS, dream1, true);
        when(mDreamComponentsResolver.getDreamPlaylist(any()))
                .thenReturn(
                        new DreamPlaylist(
                                Collections.singletonList(new DreamItem.Builder(dream1).build()),
                                0));

        service.refreshSettings(
                mCurrentUser.getIdentifier(),
                Settings.Secure.getUriFor(Settings.Secure.SCREENSAVER_COMPONENTS));
        mTestLooper.dispatchAll();
        mTestLooper.moveTimeForward(1000);
        mTestLooper.dispatchAll();

        verify(listener).onPlaylistChanged(any(DreamPlaylist.class));
        clearInvocations(listener);

        // Trigger change 2
        final ComponentName dream2 = ComponentName.unflattenFromString("com.test/.Dream2");
        setupDreamComponent(Settings.Secure.SCREENSAVER_COMPONENTS, dream2, true);
        when(mDreamComponentsResolver.getDreamPlaylist(any()))
                .thenReturn(
                        new DreamPlaylist(
                                Collections.singletonList(new DreamItem.Builder(dream2).build()),
                                0));

        service.refreshSettings(
                mCurrentUser.getIdentifier(),
                Settings.Secure.getUriFor(Settings.Secure.SCREENSAVER_COMPONENTS));
        mTestLooper.dispatchAll();
        mTestLooper.moveTimeForward(1000);
        mTestLooper.dispatchAll();

        verify(listener).onPlaylistChanged(any(DreamPlaylist.class));
    }

    @Test
    @EnableFlags(FLAG_DREAMS_SWITCHER)
    public void testNotifyPlaylistChanged_cacheCleanupOnUserStop() throws Exception {
        // Initialize service
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        // Register the listener
        final IDreamManagerListener listener = registerListener(service);

        // Trigger change 1
        final ComponentName dream1 = ComponentName.unflattenFromString("com.test/.Dream1");
        setupDreamComponent(Settings.Secure.SCREENSAVER_COMPONENTS, dream1, true);
        when(mDreamComponentsResolver.getDreamPlaylist(any()))
                .thenReturn(
                        new DreamPlaylist(
                                Collections.singletonList(new DreamItem.Builder(dream1).build()),
                                0));
        service.refreshSettings(
                mCurrentUser.getIdentifier(),
                Settings.Secure.getUriFor(Settings.Secure.SCREENSAVER_COMPONENTS));
        mTestLooper.dispatchAll();
        mTestLooper.moveTimeForward(1000);
        mTestLooper.dispatchAll();

        verify(listener).onPlaylistChanged(any(DreamPlaylist.class));
        clearInvocations(listener);

        // Stop user
        SystemService.TargetUser user = mock(SystemService.TargetUser.class);
        when(user.getUserIdentifier()).thenReturn(mCurrentUser.getIdentifier());
        service.onUserStopping(user);

        // Re-register listener (as onUserStopping kills the listeners)
        registerListener(service, listener);

        // Trigger change 1 again. Should be broadcast because cache was cleared.
        when(mDreamComponentsResolver.getDreamPlaylist(any()))
                .thenReturn(
                        new DreamPlaylist(
                                Collections.singletonList(new DreamItem.Builder(dream1).build()),
                                0));

        service.refreshSettings(
                mCurrentUser.getIdentifier(),
                Settings.Secure.getUriFor(Settings.Secure.SCREENSAVER_COMPONENTS));
        mTestLooper.dispatchAll();
        mTestLooper.moveTimeForward(1000);
        mTestLooper.dispatchAll();

        verify(listener).onPlaylistChanged(any(DreamPlaylist.class));
    }

    @Test
    @EnableFlags(FLAG_DREAMS_SWITCHER)
    public void testSystemDreamComponentChange_notifiesPlaylistListener() throws Exception {
        // Initialize service
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        // Register the listener
        final IDreamManagerListener listener = registerListener(service);

        // Set system dream component
        final ComponentName systemDream = ComponentName.unflattenFromString("com.system/.Dream");
        when(mDreamComponentsResolver.getDreamPlaylist(any()))
                .thenReturn(
                        new DreamPlaylist(
                                Collections.singletonList(
                                        new DreamItem.Builder(systemDream).build()),
                                0));

        service.setSystemDreamComponentInternal(systemDream, mock(IBinder.class));
        mTestLooper.dispatchAll();
        mTestLooper.moveTimeForward(1000);
        mTestLooper.dispatchAll();

        // Verify updated playlist callback
        ArgumentCaptor<DreamPlaylist> playlistCaptor = ArgumentCaptor.forClass(DreamPlaylist.class);
        verify(listener).onPlaylistChanged(playlistCaptor.capture());
        assertThat(playlistCaptor.getValue().getActiveDream().componentName).isEqualTo(systemDream);
    }

    @Test
    @EnableFlags(FLAG_DREAMS_SWITCHER)
    public void testSetActiveDream_failsWhenComponentNotInPlaylist() {
        final DreamManagerService service = createService();
        final ComponentName validDream = ComponentName.unflattenFromString("com.test/.ValidDream");
        final ComponentName invalidDream =
                ComponentName.unflattenFromString("com.test/.InvalidDream");

        // Setup: Playlist only contains 'validDream'
        when(mDreamComponentsResolver.getDreamPlaylist(any()))
                .thenReturn(
                        new DreamPlaylist(
                                Collections.singletonList(
                                        new DreamItem.Builder(validDream).build()),
                                0));

        // Act: Try to set 'invalidDream' as active
        boolean result = service.setActiveDreamInternal(invalidDream, mCurrentUser.getIdentifier());

        // Assert: Should return false
        assertThat(result).isFalse();
    }

    @Test
    @EnableFlags(FLAG_DREAMS_SWITCHER)
    public void testDreamSwitcher_restartsDreamWhenActiveDreamChanges() {
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        // 1. Setup: Device is currently dreaming "Dream A"
        final ComponentName dreamA = ComponentName.unflattenFromString("com.test/.DreamA");
        final ComponentName dreamB = ComponentName.unflattenFromString("com.test/.DreamB");
        setupDreamComponent(Settings.Secure.SCREENSAVER_COMPONENTS, dreamA, true);
        when(mDreamComponentsResolver.resolve(anyBoolean(), anyBoolean(), any()))
                .thenReturn(dreamA);

        // Start dream A
        service.startDreamInternal(false, "test_start");
        mTestLooper.dispatchAll();
        verify(mDreamControllerMock)
                .startDream(
                        any(),
                        eq(dreamA),
                        anyBoolean(),
                        anyBoolean(),
                        anyInt(),
                        any(),
                        any(),
                        eq("test_start"));
        clearInvocations(mDreamControllerMock);

        // 2. Act: Trigger a playlist update where "Dream B" becomes the active dream
        DreamPlaylist newPlaylist =
                new DreamPlaylist(
                        Arrays.asList(
                                new DreamItem.Builder(dreamA).build(),
                                new DreamItem.Builder(dreamB).build()),
                        1); // 1 is index of DreamB

        // Trigger the callback directly
        service.onDreamPlaylistChanged(mCurrentUser.getIdentifier(), newPlaylist);
        mTestLooper.dispatchAll();

        // 3. Assert: Verify startDream was called for Dream B
        verify(mDreamControllerMock)
                .startDream(
                        any(),
                        eq(dreamB), // Verify it switched to B
                        anyBoolean(),
                        anyBoolean(),
                        anyInt(),
                        any(),
                        any(),
                        eq("playlist changed") // Verify the reason string
                        );
    }

    @Test
    @EnableFlags(FLAG_DREAMS_SWITCHER)
    public void testDreamSwitcher_doesNotSwitchWhenDozing() {
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        // 1. Setup: Device is currently dozing
        final ComponentName dreamA = ComponentName.unflattenFromString("com.test/.DreamA");
        final ComponentName dreamB = ComponentName.unflattenFromString("com.test/.DreamB");
        setupDreamComponent(Settings.Secure.SCREENSAVER_COMPONENTS, dreamA, true);
        when(mDreamComponentsResolver.resolve(anyBoolean(), anyBoolean(), any()))
                .thenReturn(dreamA);

        // Start dozing (startDream with doze=true)
        service.startDreamInternal(true, "test_start_doze");
        mTestLooper.dispatchAll();
        verify(mDreamControllerMock)
                .startDream(
                        any(),
                        eq(dreamA),
                        anyBoolean(),
                        eq(true), // doze=true
                        anyInt(),
                        any(),
                        any(),
                        eq("test_start_doze"));
        clearInvocations(mDreamControllerMock);

        // 2. Act: Trigger a playlist update where "Dream B" becomes the active dream
        DreamPlaylist newPlaylist =
                new DreamPlaylist(
                        Arrays.asList(
                                new DreamItem.Builder(dreamA).build(),
                                new DreamItem.Builder(dreamB).build()),
                        1);

        service.onDreamPlaylistChanged(mCurrentUser.getIdentifier(), newPlaylist);
        mTestLooper.dispatchAll();

        // 3. Assert: Verify startDream was NOT called
        verify(mDreamControllerMock, never())
                .startDream(
                        any(),
                        any(),
                        anyBoolean(),
                        anyBoolean(),
                        anyInt(),
                        any(),
                        any(),
                        anyString());
    }

    @Test
    @EnableFlags(FLAG_DREAMS_SWITCHER)
    public void testDreamSwitcher_doesNotSwitchIfActiveDreamIsUnchanged() {
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);

        // 1. Setup: Device is currently dreaming "Dream A"
        final ComponentName dreamA = ComponentName.unflattenFromString("com.test/.DreamA");
        setupDreamComponent(Settings.Secure.SCREENSAVER_COMPONENTS, dreamA, true);
        when(mDreamComponentsResolver.resolve(anyBoolean(), anyBoolean(), any()))
                .thenReturn(dreamA);

        service.startDreamInternal(false, "test_start");
        mTestLooper.dispatchAll();
        verify(mDreamControllerMock)
                .startDream(
                        any(),
                        eq(dreamA),
                        anyBoolean(),
                        anyBoolean(),
                        anyInt(),
                        any(),
                        any(),
                        eq("test_start"));
        clearInvocations(mDreamControllerMock);

        // 2. Act: Trigger a playlist update where "Dream A" is STILL the active dream
        // but maybe the list order changed or something else changed.
        DreamPlaylist newPlaylist =
                new DreamPlaylist(
                        Collections.singletonList(new DreamItem.Builder(dreamA).build()), 0);

        service.onDreamPlaylistChanged(mCurrentUser.getIdentifier(), newPlaylist);
        mTestLooper.dispatchAll();

        // 3. Assert: Verify startDream was NOT called
        verify(mDreamControllerMock, never())
                .startDream(
                        any(),
                        any(),
                        anyBoolean(),
                        anyBoolean(),
                        anyInt(),
                        any(),
                        any(),
                        anyString());
    }

    private IDreamManagerListener registerListener(DreamManagerService service) {
        final IDreamManagerListener listener = mock(IDreamManagerListener.class);
        final IBinder binder = mock(IBinder.class);
        when(listener.asBinder()).thenReturn(binder);
        registerListener(service, listener);
        return listener;
    }

    private void registerListener(DreamManagerService service, IDreamManagerListener listener) {
        service.registerListener(listener, mCurrentUser.getIdentifier());
        mTestLooper.dispatchAll();
        clearInvocations(listener);
    }

    @Test
    @EnableFlags(FLAG_DREAMS_SWITCHER)
    public void testOnUserUnlocked_refreshesPlaylist() {
        final DreamManagerService service = createService();
        service.onBootPhase(SystemService.PHASE_THIRD_PARTY_APPS_CAN_START);
        final ComponentName dream = new ComponentName("a", "b");

        // Mock resolver to return a playlist
        when(mDreamComponentsResolver.getDreamPlaylist(any()))
                .thenReturn(
                        new DreamPlaylist(
                                Collections.singletonList(new DreamItem.Builder(dream).build()),
                                0));

        // Call onUserUnlocked
        service.onUserUnlocked(
                new SystemService.TargetUser(
                        new UserInfo(mCurrentUser.getIdentifier(), "test_user", 0)));
        mTestLooper.dispatchAll();

        // Verify resolver was called (refresh happened)
        verify(mDreamComponentsResolver).getDreamPlaylist(any());
    }

    private void enableDreaming() {
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ENABLED, 1, UserHandle.USER_CURRENT);
        Settings.Secure.putIntForUser(mContextSpy.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_SLEEP, 1, UserHandle.USER_CURRENT);
    }

    private void setupDreamPreconditions() {
        when(mUserManagerMock.isUserUnlocked(anyInt())).thenReturn(true);
        when(mBatteryManagerInternal.isPowered(eq(BatteryManager.BATTERY_PLUGGED_ANY)))
                .thenReturn(true);
    }

    private void setupDreamComponent(String setting, ComponentName component, boolean valid) {
        Settings.Secure.putStringForUser(mContextSpy.getContentResolver(),
                setting,
                component.flattenToString(),
                mCurrentUser.getIdentifier());
    }

    private static final class TestInjector implements DreamManagerService.Injector {
        private final Context mContext;
        private final Handler mHandler;
        private final DreamController mDreamController;
        private final AmbientDisplayConfiguration mDozeConfig;
        private final DreamComponentsResolver mDreamComponentsResolver;
        private final int mCurrentUser;

        TestInjector(Context context, Handler handler, DreamController dreamController,
                AmbientDisplayConfiguration dozeConfig,
                DreamComponentsResolver dreamComponentsResolver,
                @UserIdInt int currentUser) {
            mContext = context;
            mHandler = handler;
            mDreamController = dreamController;
            mDozeConfig = dozeConfig;
            mDreamComponentsResolver = dreamComponentsResolver;
            mCurrentUser = currentUser;
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

        @Override
        public DreamComponentsResolver getDreamComponentsResolver(
                DreamValidator dreamValidator,
                int userId,
                AmbientDisplayConfiguration dozeConfig,
                UserManagerInternal userManagerInternal,
                boolean dreamsOnlyEnabledForDockUser,
                DreamRepository dreamRepository) {
            return mDreamComponentsResolver;
        }

        @Override
        public int getCurrentUser() {
            return mCurrentUser;
        }
    }
}
