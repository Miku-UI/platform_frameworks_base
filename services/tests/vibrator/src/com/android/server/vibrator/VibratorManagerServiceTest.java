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

package com.android.server.vibrator;

import static android.os.VibrationEffect.Composition.PRIMITIVE_CLICK;
import static android.os.VibrationEffect.Composition.PRIMITIVE_QUICK_FALL;
import static android.os.VibrationEffect.Composition.PRIMITIVE_QUICK_RISE;
import static android.os.VibrationEffect.Composition.PRIMITIVE_SLOW_RISE;
import static android.os.VibrationEffect.Composition.PRIMITIVE_SPIN;
import static android.os.VibrationEffect.Composition.PRIMITIVE_THUD;
import static android.os.VibrationEffect.Composition.PRIMITIVE_TICK;
import static android.os.VibrationEffect.EFFECT_CLICK;
import static android.os.VibrationEffect.EFFECT_DOUBLE_CLICK;
import static android.os.VibrationEffect.EFFECT_HEAVY_CLICK;
import static android.os.VibrationEffect.EFFECT_STRENGTH_LIGHT;
import static android.os.VibrationEffect.EFFECT_STRENGTH_MEDIUM;
import static android.os.VibrationEffect.EFFECT_STRENGTH_STRONG;
import static android.os.VibrationEffect.EFFECT_THUD;
import static android.os.VibrationEffect.EFFECT_TICK;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.res.Resources;
import android.frameworks.vibrator.ScaleParam;
import android.hardware.input.IInputManager;
import android.hardware.input.InputManager;
import android.hardware.input.InputManagerGlobal;
import android.hardware.vibrator.IVibrator;
import android.hardware.vibrator.IVibratorManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.CombinedVibration;
import android.os.ExternalVibration;
import android.os.ExternalVibrationScale;
import android.os.Handler;
import android.os.IBinder;
import android.os.IExternalVibrationController;
import android.os.IVibratorStateListener;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.PowerSaveState;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorInfo;
import android.os.test.FakeVibrator;
import android.os.test.TestLooper;
import android.os.vibrator.Flags;
import android.os.vibrator.IVibrationSession;
import android.os.vibrator.IVibrationSessionCallback;
import android.os.vibrator.PrebakedSegment;
import android.os.vibrator.PrimitiveSegment;
import android.os.vibrator.StepSegment;
import android.os.vibrator.VibrationConfig;
import android.os.vibrator.VibrationEffectSegment;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;

import androidx.test.InstrumentationRegistry;

import com.android.internal.app.IBatteryStats;
import com.android.internal.util.FrameworkStatsLog;
import com.android.internal.util.test.FakeSettingsProvider;
import com.android.internal.util.test.FakeSettingsProviderRule;
import com.android.server.LocalServices;
import com.android.server.companion.virtual.VirtualDeviceManagerInternal;
import com.android.server.pm.BackgroundUserSoundNotifier;
import com.android.server.pm.UserManagerInternal;
import com.android.server.vibrator.VibrationSession.Status;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

public class VibratorManagerServiceTest {

    private static final int TEST_TIMEOUT_MILLIS = 1_000;
    // Time to allow for a cancellation to complete and the vibrators to become idle.
    private static final int CLEANUP_TIMEOUT_MILLIS = 100;
    private static final int UID = Process.ROOT_UID;
    private static final int VIRTUAL_DEVICE_ID = 1;
    private static final String PACKAGE_NAME = "package";
    private static final PowerSaveState NORMAL_POWER_STATE = new PowerSaveState.Builder().build();
    private static final PowerSaveState LOW_POWER_STATE = new PowerSaveState.Builder()
            .setBatterySaverEnabled(true).build();
    private static final AudioAttributes AUDIO_ALARM_ATTRS =
            new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build();
    private static final AudioAttributes AUDIO_NOTIFICATION_ATTRS =
            new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build();
    private static final AudioAttributes AUDIO_HAPTIC_FEEDBACK_ATTRS =
            new AudioAttributes.Builder().setUsage(
                    AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build();
    private static final VibrationAttributes ALARM_ATTRS =
            new VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build();
    private static final VibrationAttributes HAPTIC_FEEDBACK_ATTRS =
            new VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build();
    private static final VibrationAttributes NOTIFICATION_ATTRS =
            new VibrationAttributes.Builder().setUsage(
                    VibrationAttributes.USAGE_NOTIFICATION).build();
    private static final VibrationAttributes RINGTONE_ATTRS =
            new VibrationAttributes.Builder().setUsage(
                    VibrationAttributes.USAGE_RINGTONE).build();
    private static final VibrationAttributes IME_FEEDBACK_ATTRS =
            new VibrationAttributes.Builder().setUsage(
                    VibrationAttributes.USAGE_IME_FEEDBACK).build();
    private static final VibrationAttributes UNKNOWN_ATTRS =
            new VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_UNKNOWN).build();

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    @Rule
    public FakeSettingsProviderRule mSettingsProviderRule = FakeSettingsProvider.rule();
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    private PackageManagerInternal mPackageManagerInternalMock;
    @Mock
    private PowerManagerInternal mPowerManagerInternalMock;
    @Mock
    private PowerSaveState mPowerSaveStateMock;
    @Mock
    private AppOpsManager mAppOpsManagerMock;
    @Mock
    private IInputManager mIInputManagerMock;
    @Mock
    private IBatteryStats mBatteryStatsMock;
    @Mock
    private VibratorFrameworkStatsLogger mVibratorFrameworkStatsLoggerMock;
    @Mock
    private VirtualDeviceManagerInternal mVirtualDeviceManagerInternalMock;
    @Mock
    private AudioManager mAudioManagerMock;

    private final SparseArray<VibrationEffect>  mHapticFeedbackVibrationMap = new SparseArray<>();
    private final SparseArray<VibrationEffect>  mHapticFeedbackVibrationMapSourceRotary =
            new SparseArray<>();
    private final SparseArray<VibrationEffect>  mHapticFeedbackVibrationMapSourceTouchScreen =
            new SparseArray<>();
    private final SparseArray<VibrationEffect>  mHapticFeedbackVibrationMapUsageGestureInput =
            new SparseArray<>();

    private final List<HalVibration> mPendingVibrations = new ArrayList<>();
    private final List<VendorVibrationSession> mPendingSessions = new ArrayList<>();

    private VibratorManagerService mService;
    private Context mContextSpy;
    private TestLooper mTestLooper;
    private FakeVibrator mVibrator;
    private HalVibratorManagerHelper mHalHelper;
    private FakeVibratorController mFakeVibratorController;
    private PowerManagerInternal.LowPowerModeListener mRegisteredPowerModeListener;
    private VibratorManagerService.ExternalVibratorService mExternalVibratorService;
    private VibratorControlService mVibratorControlService;
    private VibrationConfig mVibrationConfig;
    private InputManagerGlobal.TestSession mInputManagerGlobalSession;
    private InputManager mInputManager;
    private boolean mUseLegacyHalManager = false;

    @Before
    public void setUp() throws Exception {
        mTestLooper = new TestLooper();
        mContextSpy = spy(new ContextWrapper(InstrumentationRegistry.getContext()));
        mInputManagerGlobalSession = InputManagerGlobal.createTestSession(mIInputManagerMock);
        mVibrationConfig = new VibrationConfig(mContextSpy.getResources());
        mFakeVibratorController = new FakeVibratorController(mTestLooper.getLooper());
        mHalHelper = new HalVibratorManagerHelper(mTestLooper.getLooper());

        ContentResolver contentResolver = mSettingsProviderRule.mockContentResolver(mContextSpy);
        when(mContextSpy.getContentResolver()).thenReturn(contentResolver);

        mVibrator = new FakeVibrator(mContextSpy);
        when(mContextSpy.getSystemService(eq(Context.VIBRATOR_SERVICE))).thenReturn(mVibrator);

        mInputManager = new InputManager(mContextSpy);
        when(mContextSpy.getSystemService(eq(Context.INPUT_SERVICE)))
                .thenReturn(mInputManager);
        when(mContextSpy.getSystemService(Context.APP_OPS_SERVICE)).thenReturn(mAppOpsManagerMock);
        when(mContextSpy.getSystemService(eq(Context.AUDIO_SERVICE))).thenReturn(mAudioManagerMock);
        when(mIInputManagerMock.getInputDeviceIds()).thenReturn(new int[0]);
        when(mPackageManagerInternalMock.getSystemUiServiceComponent())
                .thenReturn(new ComponentName("", ""));
        when(mPowerManagerInternalMock.getLowPowerState(PowerManager.ServiceType.VIBRATION))
                .thenReturn(mPowerSaveStateMock);
        doAnswer(invocation -> {
            mRegisteredPowerModeListener = invocation.getArgument(0);
            return null;
        }).when(mPowerManagerInternalMock).registerLowPowerModeObserver(any());

        setUserSetting(Settings.System.VIBRATE_WHEN_RINGING, 1);
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_ENABLED, 1);
        setUserSetting(Settings.System.ALARM_VIBRATION_INTENSITY,
                Vibrator.VIBRATION_INTENSITY_MEDIUM);
        setUserSetting(Settings.System.NOTIFICATION_VIBRATION_INTENSITY,
                Vibrator.VIBRATION_INTENSITY_MEDIUM);
        setUserSetting(Settings.System.MEDIA_VIBRATION_INTENSITY,
                Vibrator.VIBRATION_INTENSITY_MEDIUM);
        setUserSetting(Settings.System.RING_VIBRATION_INTENSITY,
                Vibrator.VIBRATION_INTENSITY_MEDIUM);
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY,
                Vibrator.VIBRATION_INTENSITY_MEDIUM);
        setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        addLocalServiceMock(PackageManagerInternal.class, mPackageManagerInternalMock);
        addLocalServiceMock(PowerManagerInternal.class, mPowerManagerInternalMock);
        addLocalServiceMock(VirtualDeviceManagerInternal.class, mVirtualDeviceManagerInternalMock);

        mTestLooper.startAutoDispatch();
    }

    @After
    public void tearDown() throws Exception {
        if (mService != null) {
            // Make sure we have permission to cancel test vibrations, even if the test denied them.
            grantPermission(android.Manifest.permission.VIBRATE);
            // Cancel any pending vibration from tests, including external vibrations.
            cancelVibrate(mService);
            // End pending sessions.
            for (VendorVibrationSession session : mPendingSessions) {
                session.cancelSession();
            }
            // Dispatch and wait for all callbacks in test looper to be processed.
            stopAutoDispatcherAndDispatchAll();
            // Wait until pending vibrations end asynchronously.
            for (HalVibration vibration : mPendingVibrations) {
                vibration.waitForEnd();
            }
            // Wait until all vibrators have stopped vibrating, waiting for ramp-down.
            // Note: if a test is flaky here something is wrong with the vibration finalization.
            assertTrue(waitUntil(s -> {
                for (int vibratorId : mService.getVibratorIds()) {
                    if (s.isVibrating(vibratorId)) {
                        return false;
                    }
                }
                return true;
            }, mService, mVibrationConfig.getRampDownDurationMs() + CLEANUP_TIMEOUT_MILLIS));
        }

        LocalServices.removeServiceForTest(PackageManagerInternal.class);
        LocalServices.removeServiceForTest(PowerManagerInternal.class);
        LocalServices.removeServiceForTest(VirtualDeviceManagerInternal.class);
        if (mInputManagerGlobalSession != null) {
            mInputManagerGlobalSession.close();
        }
    }

    private VibratorManagerService createSystemReadyService() {
        VibratorManagerService service = createService();
        service.systemReady();
        return service;
    }

    private VibratorManagerService createService() {
        mService = new VibratorManagerService(
                mContextSpy,
                new VibratorManagerService.Injector() {
                    @Override
                    Handler createHandler(Looper looper) {
                        return new Handler(mTestLooper.getLooper());
                    }

                    @Override
                    IBatteryStats getBatteryStatsService() {
                        return mBatteryStatsMock;
                    }

                    @Override
                    VibratorFrameworkStatsLogger getFrameworkStatsLogger(Handler handler) {
                        return mVibratorFrameworkStatsLoggerMock;
                    }

                    @Override
                    HalVibratorManager createHalVibratorManager(Handler handler) {
                        if (mUseLegacyHalManager) {
                            return mHalHelper.newLegacyVibratorManager();
                        }
                        return mHalHelper.newDefaultVibratorManager();
                    }

                    @Override
                    HalVibratorManager createNativeHalVibratorManager() {
                        return mHalHelper.newNativeHalVibratorManager();
                    }

                    @Override
                    void addService(String name, IBinder service) {
                        if (service instanceof VibratorManagerService.ExternalVibratorService) {
                            mExternalVibratorService =
                                    (VibratorManagerService.ExternalVibratorService) service;
                        } else if (service instanceof VibratorControlService) {
                            mVibratorControlService = (VibratorControlService) service;
                            mFakeVibratorController.setVibratorControlService(
                                    mVibratorControlService);
                        }
                    }

                    @Override
                    HapticFeedbackVibrationProvider createHapticFeedbackVibrationProvider(
                            Resources resources, VibratorInfo vibratorInfo) {
                        return new HapticFeedbackVibrationProvider(resources, vibratorInfo,
                                new HapticFeedbackCustomization(mHapticFeedbackVibrationMap,
                                        mHapticFeedbackVibrationMapSourceRotary,
                                        mHapticFeedbackVibrationMapSourceTouchScreen,
                                        mHapticFeedbackVibrationMapUsageGestureInput));
                    }

                    @Override
                    VibratorControllerHolder createVibratorControllerHolder() {
                        VibratorControllerHolder holder = new VibratorControllerHolder();
                        holder.setVibratorController(mFakeVibratorController);
                        return holder;
                    }

                    @Override
                    boolean isServiceDeclared(String name) {
                        return true;
                    }
                });
        return mService;
    }

    @Test
    public void createService_initializesHals() {
        mHalHelper.setVibratorIds(new int[]{1, 2});
        createService();

        assertThat(mHalHelper.getConnectCount()).isEqualTo(1);
        assertThat(mHalHelper.getVibratorHelper(1).isInitialized()).isTrue();
        assertThat(mHalHelper.getVibratorHelper(2).isInitialized()).isTrue();
    }

    @Test
    public void createService_resetsVibrators() {
        mHalHelper.setVibratorIds(new int[]{1, 2});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        mHalHelper.getVibratorHelper(2).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        createService();

        assertThat(mHalHelper.getVibratorHelper(1).getOffCount()).isEqualTo(1);
        assertThat(mHalHelper.getVibratorHelper(2).getOffCount()).isEqualTo(1);
        assertThat(mHalHelper.getVibratorHelper(1).getExternalControlStates())
                .containsExactly(false).inOrder();
        assertThat(mHalHelper.getVibratorHelper(2).getExternalControlStates())
                .containsExactly(false).inOrder();
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void createService_resetsVibratorManager() {
        mHalHelper.setCapabilities(IVibratorManager.CAP_SYNC, IVibratorManager.CAP_START_SESSIONS);
        mHalHelper.setVibratorIds(new int[]{1, 2});
        createService();

        assertThat(mHalHelper.getCancelSyncedCount()).isEqualTo(1);
        assertThat(mHalHelper.getClearSessionsCount()).isEqualTo(1);
    }

    @Test
    @DisableFlags(Flags.FLAG_REMOVE_HIDL_SUPPORT)
    public void createService_withVibratorInfoAvailable_doNotCrashIfUsedBeforeSystemReady() {
        mHalHelper.setVibratorIds(new int[]{1, 2});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_ALWAYS_ON_CONTROL);
        mHalHelper.getVibratorHelper(2).setCapabilities(IVibrator.CAP_ALWAYS_ON_CONTROL);
        VibratorManagerService service = createService();

        assertNotNull(service.getVibratorIds());
        assertNotNull(service.getVibratorInfo(1));
        assertFalse(service.isVibrating(1));

        CombinedVibration effect = CombinedVibration.createParallel(
                VibrationEffect.createPredefined(EFFECT_CLICK));
        vibrate(service, effect, HAPTIC_FEEDBACK_ATTRS);
        service.cancelVibrate(VibrationAttributes.USAGE_FILTER_MATCH_ALL, service);

        assertTrue(service.setAlwaysOnEffect(UID, PACKAGE_NAME, 1, effect, ALARM_ATTRS));

        IVibratorStateListener listener = mockVibratorStateListener();
        assertTrue(service.registerVibratorStateListener(1, listener));
        assertTrue(service.unregisterVibratorStateListener(1, listener));
    }

    @Test
    @EnableFlags(Flags.FLAG_REMOVE_HIDL_SUPPORT)
    public void createService_doNotCrashIfUsedBeforeSystemReady() {
        mHalHelper.setVibratorIds(new int[]{1, 2});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_ALWAYS_ON_CONTROL);
        mHalHelper.getVibratorHelper(2).setCapabilities(IVibrator.CAP_ALWAYS_ON_CONTROL);
        VibratorManagerService service = createService();

        assertNotNull(service.getVibratorIds());
        assertNull(service.getVibratorInfo(1));
        assertFalse(service.isVibrating(1));

        CombinedVibration effect = CombinedVibration.createParallel(
                VibrationEffect.createPredefined(EFFECT_CLICK));
        vibrate(service, effect, HAPTIC_FEEDBACK_ATTRS);
        service.cancelVibrate(VibrationAttributes.USAGE_FILTER_MATCH_ALL, service);

        service.setAlwaysOnEffect(UID, PACKAGE_NAME, 1, effect, ALARM_ATTRS);

        IVibratorStateListener listener = mockVibratorStateListener();
        assertTrue(service.registerVibratorStateListener(1, listener));
        assertTrue(service.unregisterVibratorStateListener(1, listener));
    }

    @Test
    @EnableFlags({Flags.FLAG_REMOVE_HIDL_SUPPORT, Flags.FLAG_VENDOR_VIBRATION_EFFECTS})
    public void createService_withLegacyManager_initializesEmptyManager() {
        int defaultVibratorId = VintfHalVibratorManager.DEFAULT_VIBRATOR_ID;
        mUseLegacyHalManager = true;

        createService();
        assertThat(mHalHelper.getConnectCount()).isEqualTo(0);
        assertThat(mHalHelper.getCancelSyncedCount()).isEqualTo(0);
        assertThat(mHalHelper.getClearSessionsCount()).isEqualTo(0);
        assertThat(mHalHelper.getVibratorHelper(defaultVibratorId).isInitialized()).isTrue();
    }

    @Test
    public void getVibratorIds_withNullResultFromHal_returnsEmptyArray() {
        mHalHelper.setVibratorIds(null);
        assertArrayEquals(new int[0], createSystemReadyService().getVibratorIds());
    }

    @Test
    public void getVibratorIds_withNonEmptyResultFromHal_returnsSameArray() {
        mHalHelper.setVibratorIds(new int[]{2, 1});
        assertArrayEquals(new int[]{2, 1}, createSystemReadyService().getVibratorIds());
    }

    @Test
    public void getVibratorInfo_withMissingVibratorId_returnsNull() {
        mHalHelper.setVibratorIds(new int[]{1});
        assertNull(createSystemReadyService().getVibratorInfo(2));
    }

    @Test
    public void getVibratorInfo_vibratorFailedLoadBeforeSystemReady_returnsNull() {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setLoadInfoToFail();
        assertNull(createService().getVibratorInfo(1));
    }

    @Test
    public void getVibratorInfo_vibratorFailedLoadAfterSystemReady_returnsInfoForVibrator() {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_GET_RESONANT_FREQUENCY);
        mHalHelper.getVibratorHelper(1).setResonantFrequency(123.f);
        mHalHelper.getVibratorHelper(1).setLoadInfoToFail();
        VibratorInfo info = createSystemReadyService().getVibratorInfo(1);

        assertNotNull(info);
        assertEquals(1, info.getId());
        assertEquals(123.f, info.getResonantFrequencyHz(), 0.01 /*tolerance*/);
    }

    @Test
    @DisableFlags(Flags.FLAG_REMOVE_HIDL_SUPPORT)
    public void getVibratorInfo_vibratorSuccessfulLoadBeforeSystemReady_returnsInfoForVibrator() {
        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL,
                IVibrator.CAP_GET_RESONANT_FREQUENCY, IVibrator.CAP_COMPOSE_EFFECTS);
        vibratorHelper.setSupportedEffects(EFFECT_CLICK);
        vibratorHelper.setSupportedPrimitives(PRIMITIVE_CLICK);
        vibratorHelper.setResonantFrequency(123.f);
        VibratorInfo info = createService().getVibratorInfo(1);

        assertNotNull(info);
        assertEquals(1, info.getId());
        assertTrue(info.hasAmplitudeControl());
        assertTrue(info.hasCapability(IVibrator.CAP_COMPOSE_EFFECTS));
        assertFalse(info.hasCapability(IVibrator.CAP_ON_CALLBACK));
        assertEquals(Vibrator.VIBRATION_EFFECT_SUPPORT_YES, info.isEffectSupported(EFFECT_CLICK));
        assertEquals(Vibrator.VIBRATION_EFFECT_SUPPORT_NO, info.isEffectSupported(EFFECT_TICK));
        assertTrue(info.isPrimitiveSupported(PRIMITIVE_CLICK));
        assertFalse(info.isPrimitiveSupported(PRIMITIVE_TICK));
        assertEquals(123.f, info.getResonantFrequencyHz(), 0.01 /*tolerance*/);
        assertTrue(Float.isNaN(info.getQFactor()));
    }

    @Test
    @DisableFlags(Flags.FLAG_REMOVE_HIDL_SUPPORT)
    public void getVibratorInfo_vibratorFailedThenSuccessfulLoad_returnsNullThenInfo() {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_GET_RESONANT_FREQUENCY);
        mHalHelper.getVibratorHelper(1).setLoadInfoToFail();

        VibratorManagerService service = createService();
        assertNull(createService().getVibratorInfo(1));

        mHalHelper.getVibratorHelper(1).setLoadInfoToFail();
        mHalHelper.getVibratorHelper(1).setResonantFrequency(123.f);
        service.systemReady();

        VibratorInfo info = service.getVibratorInfo(1);
        assertNotNull(info);
        assertEquals(1, info.getId());
        assertEquals(123.f, info.getResonantFrequencyHz(), 0.01 /*tolerance*/);
    }

    @Test
    @EnableFlags(Flags.FLAG_REMOVE_HIDL_SUPPORT)
    public void getVibratorInfo_beforeSystemReady_returnsNull() {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        VibratorInfo info = createService().getVibratorInfo(1);
        assertNull(info);
    }

    @Test
    public void registerVibratorStateListener_callbacksAreTriggered() throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        VibratorManagerService service = createSystemReadyService();
        IVibratorStateListener listenerMock = mockVibratorStateListener();
        service.registerVibratorStateListener(1, listenerMock);

        long oneShotDuration = 20;
        vibrateAndWaitUntilFinished(service,
                VibrationEffect.createOneShot(oneShotDuration, VibrationEffect.DEFAULT_AMPLITUDE),
                ALARM_ATTRS);

        InOrder inOrderVerifier = inOrder(listenerMock);
        // First notification done when listener is registered.
        inOrderVerifier.verify(listenerMock).onVibrating(eq(false));
        // Vibrator on notification done before vibration ended, no need to wait.
        inOrderVerifier.verify(listenerMock).onVibrating(eq(true));
        // Vibrator off notification done after vibration completed, wait for notification.
        inOrderVerifier.verify(listenerMock, timeout(TEST_TIMEOUT_MILLIS)).onVibrating(eq(false));
        inOrderVerifier.verifyNoMoreInteractions();

        InOrder batteryVerifier = inOrder(mBatteryStatsMock);
        batteryVerifier.verify(mBatteryStatsMock)
                .noteVibratorOn(UID, oneShotDuration + mVibrationConfig.getRampDownDurationMs());
        batteryVerifier
                .verify(mBatteryStatsMock, timeout(TEST_TIMEOUT_MILLIS)).noteVibratorOff(UID);
    }

    @Test
    public void unregisterVibratorStateListener_callbackNotTriggeredAfter() throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        VibratorManagerService service = createSystemReadyService();
        IVibratorStateListener listenerMock = mockVibratorStateListener();
        service.registerVibratorStateListener(1, listenerMock);

        vibrate(service, VibrationEffect.createOneShot(40, 100), ALARM_ATTRS);

        // Wait until service knows vibrator is on.
        assertTrue(waitUntil(s -> s.isVibrating(1), service, TEST_TIMEOUT_MILLIS));

        service.unregisterVibratorStateListener(1, listenerMock);

        // Wait until vibrator is off.
        assertTrue(waitUntil(s -> !s.isVibrating(1), service, TEST_TIMEOUT_MILLIS));

        InOrder inOrderVerifier = inOrder(listenerMock);
        // First notification done when listener is registered.
        inOrderVerifier.verify(listenerMock).onVibrating(eq(false));
        // Vibrator on notification done before vibration ended, no need to wait.
        inOrderVerifier.verify(listenerMock).onVibrating(eq(true));
        inOrderVerifier.verify(listenerMock, atLeastOnce()).asBinder(); // unregister
        inOrderVerifier.verifyNoMoreInteractions();
    }

    @Test
    public void registerVibratorStateListener_multipleVibratorsAreTriggered() throws Exception {
        mHalHelper.setVibratorIds(new int[]{0, 1, 2});
        mHalHelper.getVibratorHelper(1).setSupportedEffects(EFFECT_CLICK);
        VibratorManagerService service = createSystemReadyService();
        IVibratorStateListener[] listeners = new IVibratorStateListener[3];
        for (int i = 0; i < 3; i++) {
            listeners[i] = mockVibratorStateListener();
            service.registerVibratorStateListener(i, listeners[i]);
        }

        vibrateAndWaitUntilFinished(service, CombinedVibration.startParallel()
                .addVibrator(0, VibrationEffect.createOneShot(40, 100))
                .addVibrator(1, VibrationEffect.get(EFFECT_CLICK))
                .combine(), ALARM_ATTRS);

        verify(listeners[0]).onVibrating(eq(true));
        verify(listeners[1]).onVibrating(eq(true));
        verify(listeners[2], never()).onVibrating(eq(true));
    }

    @Test
    public void setAlwaysOnEffect_withMono_enablesAlwaysOnEffectToAllVibratorsWithCapability() {
        mHalHelper.setVibratorIds(new int[]{1, 2, 3});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_ALWAYS_ON_CONTROL);
        mHalHelper.getVibratorHelper(3).setCapabilities(IVibrator.CAP_ALWAYS_ON_CONTROL);

        CombinedVibration effect = CombinedVibration.createParallel(
                VibrationEffect.createPredefined(EFFECT_CLICK));
        assertTrue(createSystemReadyService().setAlwaysOnEffect(
                UID, PACKAGE_NAME, 1, effect, ALARM_ATTRS));

        PrebakedSegment expected = new PrebakedSegment(EFFECT_CLICK, false, EFFECT_STRENGTH_MEDIUM);

        // Only vibrators 1 and 3 have always-on capabilities.
        assertEquals(mHalHelper.getVibratorHelper(1).getAlwaysOnEffect(1), expected);
        assertNull(mHalHelper.getVibratorHelper(2).getAlwaysOnEffect(1));
        assertEquals(mHalHelper.getVibratorHelper(3).getAlwaysOnEffect(1), expected);
    }

    @Test
    public void setAlwaysOnEffect_withStereo_enablesAlwaysOnEffectToAllVibratorsWithCapability() {
        mHalHelper.setVibratorIds(new int[]{1, 2, 3, 4});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_ALWAYS_ON_CONTROL);
        mHalHelper.getVibratorHelper(2).setCapabilities(IVibrator.CAP_ALWAYS_ON_CONTROL);
        mHalHelper.getVibratorHelper(4).setCapabilities(IVibrator.CAP_ALWAYS_ON_CONTROL);

        CombinedVibration effect = CombinedVibration.startParallel()
                .addVibrator(1, VibrationEffect.createPredefined(EFFECT_CLICK))
                .addVibrator(2, VibrationEffect.createPredefined(EFFECT_TICK))
                .addVibrator(3, VibrationEffect.createPredefined(EFFECT_CLICK))
                .combine();
        assertTrue(createSystemReadyService().setAlwaysOnEffect(
                UID, PACKAGE_NAME, 1, effect, ALARM_ATTRS));

        PrebakedSegment expectedClick = new PrebakedSegment(
                EFFECT_CLICK, false, EFFECT_STRENGTH_MEDIUM);

        PrebakedSegment expectedTick = new PrebakedSegment(
                EFFECT_TICK, false, EFFECT_STRENGTH_MEDIUM);

        // Enables click on vibrator 1 and tick on vibrator 2 only.
        assertEquals(mHalHelper.getVibratorHelper(1).getAlwaysOnEffect(1), expectedClick);
        assertEquals(mHalHelper.getVibratorHelper(2).getAlwaysOnEffect(1), expectedTick);
        assertNull(mHalHelper.getVibratorHelper(3).getAlwaysOnEffect(1));
        assertNull(mHalHelper.getVibratorHelper(4).getAlwaysOnEffect(1));
    }

    @Test
    public void setAlwaysOnEffect_withNullEffect_disablesAlwaysOnEffects() {
        mHalHelper.setVibratorIds(new int[]{1, 2, 3});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_ALWAYS_ON_CONTROL);
        mHalHelper.getVibratorHelper(3).setCapabilities(IVibrator.CAP_ALWAYS_ON_CONTROL);

        CombinedVibration effect = CombinedVibration.createParallel(
                VibrationEffect.createPredefined(EFFECT_CLICK));
        assertTrue(createSystemReadyService().setAlwaysOnEffect(
                UID, PACKAGE_NAME, 1, effect, ALARM_ATTRS));

        assertTrue(createSystemReadyService().setAlwaysOnEffect(
                UID, PACKAGE_NAME, 1, null, ALARM_ATTRS));

        assertNull(mHalHelper.getVibratorHelper(1).getAlwaysOnEffect(1));
        assertNull(mHalHelper.getVibratorHelper(2).getAlwaysOnEffect(1));
        assertNull(mHalHelper.getVibratorHelper(3).getAlwaysOnEffect(1));
    }

    @Test
    public void setAlwaysOnEffect_withNonPrebakedEffect_ignoresEffect() {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_ALWAYS_ON_CONTROL);

        CombinedVibration effect = CombinedVibration.createParallel(
                VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
        assertFalse(createSystemReadyService().setAlwaysOnEffect(
                UID, PACKAGE_NAME, 1, effect, ALARM_ATTRS));

        assertNull(mHalHelper.getVibratorHelper(1).getAlwaysOnEffect(1));
    }

    @Test
    @DisableFlags(Flags.FLAG_REMOVE_SEQUENTIAL_COMBINATION)
    public void setAlwaysOnEffect_withNonSyncedEffect_ignoresEffect() {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_ALWAYS_ON_CONTROL);

        CombinedVibration effect = CombinedVibration.startSequential()
                .addNext(0, VibrationEffect.get(EFFECT_CLICK))
                .combine();
        assertFalse(createSystemReadyService().setAlwaysOnEffect(
                UID, PACKAGE_NAME, 1, effect, ALARM_ATTRS));

        assertNull(mHalHelper.getVibratorHelper(1).getAlwaysOnEffect(1));
    }

    @Test
    public void setAlwaysOnEffect_withNoVibratorWithCapability_ignoresEffect() {
        mHalHelper.setVibratorIds(new int[]{1});
        VibratorManagerService service = createSystemReadyService();

        CombinedVibration mono = CombinedVibration.createParallel(
                VibrationEffect.createPredefined(EFFECT_CLICK));
        CombinedVibration stereo = CombinedVibration.startParallel()
                .addVibrator(0, VibrationEffect.createPredefined(EFFECT_CLICK))
                .combine();
        assertFalse(service.setAlwaysOnEffect(UID, PACKAGE_NAME, 1, mono, ALARM_ATTRS));
        assertFalse(service.setAlwaysOnEffect(UID, PACKAGE_NAME, 2, stereo, ALARM_ATTRS));

        assertNull(mHalHelper.getVibratorHelper(1).getAlwaysOnEffect(1));
    }

    @Test
    public void vibrate_withoutVibratePermission_throwsSecurityException() {
        denyPermission(android.Manifest.permission.VIBRATE);
        VibratorManagerService service = createSystemReadyService();

        assertThrows("Expected vibrating without permission to fail!",
                SecurityException.class,
                () -> vibrate(service,
                        VibrationEffect.createPredefined(EFFECT_CLICK),
                        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)));
    }

    @Test
    public void vibrate_withoutBypassFlagsPermissions_bypassFlagsNotApplied() throws Exception {
        denyPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS);
        denyPermission(android.Manifest.permission.MODIFY_PHONE_STATE);
        denyPermission(android.Manifest.permission.MODIFY_AUDIO_ROUTING);

        assertCanVibrateWithBypassFlags(false);
    }

    @Test
    public void vibrate_withSecureSettingsPermission_bypassFlagsApplied() throws Exception {
        grantPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS);
        denyPermission(android.Manifest.permission.MODIFY_PHONE_STATE);
        denyPermission(android.Manifest.permission.MODIFY_AUDIO_ROUTING);

        assertCanVibrateWithBypassFlags(true);
    }

    @Test
    public void vibrate_withModifyPhoneStatePermission_bypassFlagsApplied() throws Exception {
        denyPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS);
        grantPermission(android.Manifest.permission.MODIFY_PHONE_STATE);
        denyPermission(android.Manifest.permission.MODIFY_AUDIO_ROUTING);

        assertCanVibrateWithBypassFlags(true);
    }

    @Test
    public void vibrate_withModifyAudioRoutingPermission_bypassFlagsApplied() throws Exception {
        denyPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS);
        denyPermission(android.Manifest.permission.MODIFY_PHONE_STATE);
        grantPermission(android.Manifest.permission.MODIFY_AUDIO_ROUTING);

        assertCanVibrateWithBypassFlags(true);
    }

    @Test
    public void vibrate_withRingtone_usesRingerModeSettings() throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setSupportedEffects(
                EFFECT_CLICK, EFFECT_HEAVY_CLICK, EFFECT_DOUBLE_CLICK);

        setRingerMode(AudioManager.RINGER_MODE_SILENT);
        VibratorManagerService service = createSystemReadyService();
        vibrateAndWaitUntilFinished(service, VibrationEffect.get(EFFECT_CLICK), RINGTONE_ATTRS);

        setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        service = createSystemReadyService();
        vibrateAndWaitUntilFinished(
                service, VibrationEffect.get(EFFECT_HEAVY_CLICK), RINGTONE_ATTRS);

        setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
        service = createSystemReadyService();
        vibrateAndWaitUntilFinished(
                service, VibrationEffect.get(EFFECT_DOUBLE_CLICK), RINGTONE_ATTRS);

        assertEquals(
                Arrays.asList(expectedPrebaked(EFFECT_HEAVY_CLICK),
                        expectedPrebaked(EFFECT_DOUBLE_CLICK)),
                mHalHelper.getVibratorHelper(1).getEffectSegments());
    }

    @Test
    public void vibrate_withPowerMode_usesPowerModeState() throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setSupportedEffects(EFFECT_TICK, EFFECT_CLICK,
                EFFECT_HEAVY_CLICK, EFFECT_DOUBLE_CLICK);
        VibratorManagerService service = createSystemReadyService();

        mRegisteredPowerModeListener.onLowPowerModeChanged(LOW_POWER_STATE);
        vibrateAndWaitUntilFinished(service, VibrationEffect.get(EFFECT_TICK),
                HAPTIC_FEEDBACK_ATTRS);
        vibrateAndWaitUntilFinished(service, VibrationEffect.get(EFFECT_CLICK), RINGTONE_ATTRS);

        mRegisteredPowerModeListener.onLowPowerModeChanged(NORMAL_POWER_STATE);
        vibrateAndWaitUntilFinished(service,
                VibrationEffect.get(EFFECT_HEAVY_CLICK), /* attrs= */ null);
        vibrateAndWaitUntilFinished(service,
                VibrationEffect.get(EFFECT_DOUBLE_CLICK), NOTIFICATION_ATTRS);

        assertEquals(
                Arrays.asList(expectedPrebaked(EFFECT_CLICK),
                        expectedPrebaked(EFFECT_HEAVY_CLICK),
                        expectedPrebaked(EFFECT_DOUBLE_CLICK)),
                mHalHelper.getVibratorHelper(1).getEffectSegments());
    }

    @Test
    public void vibrate_withAudioAttributes_usesOriginalAudioUsageInAppOpsManager() {
        VibratorManagerService service = createSystemReadyService();

        VibrationEffect effect = VibrationEffect.get(EFFECT_CLICK);
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).build();
        VibrationAttributes vibrationAttributes =
                new VibrationAttributes.Builder(audioAttributes).build();

        vibrate(service, effect, vibrationAttributes);

        verify(mAppOpsManagerMock).checkAudioOpNoThrow(eq(AppOpsManager.OP_VIBRATE),
                eq(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY), anyInt(), anyString());
    }

    @Test
    public void vibrate_thenDeniedAppOps_getsCancelled() throws Throwable {
        mHalHelper.setVibratorIds(new int[]{1});
        VibratorManagerService service = createSystemReadyService();

        HalVibration vib = vibrate(service, VibrationEffect.createWaveform(
                new long[]{0, TEST_TIMEOUT_MILLIS, TEST_TIMEOUT_MILLIS}, 0), RINGTONE_ATTRS);

        assertTrue(waitUntil(s -> s.isVibrating(1), service, TEST_TIMEOUT_MILLIS));

        when(mAppOpsManagerMock.checkAudioOpNoThrow(eq(AppOpsManager.OP_VIBRATE),
                eq(AudioAttributes.USAGE_NOTIFICATION_RINGTONE), anyInt(), anyString()))
                .thenReturn(AppOpsManager.MODE_IGNORED);

        service.mAppOpsChangeListener.onOpChanged(AppOpsManager.OP_VIBRATE, null);

        assertTrue(waitUntil(s -> vib.hasEnded(), service, TEST_TIMEOUT_MILLIS));
        assertThat(vib.getStatus()).isEqualTo(Status.CANCELLED_BY_APP_OPS);
    }

    @Test
    public void vibrate_thenPowerModeChanges_getsCancelled() throws Exception {
        mHalHelper.setVibratorIds(new int[]{1, 2});
        VibratorManagerService service = createSystemReadyService();

        HalVibration vib = vibrate(service,
                CombinedVibration.startParallel()
                        .addVibrator(1, VibrationEffect.createOneShot(2 * TEST_TIMEOUT_MILLIS, 100))
                        .combine(),
                HAPTIC_FEEDBACK_ATTRS);

        // VibrationThread will start this vibration async, so wait until vibration is triggered.
        assertTrue(waitUntil(s -> s.isVibrating(1), service, TEST_TIMEOUT_MILLIS));

        mRegisteredPowerModeListener.onLowPowerModeChanged(LOW_POWER_STATE);

        assertTrue(waitUntil(s -> vib.hasEnded(), service, TEST_TIMEOUT_MILLIS));
        assertThat(vib.getStatus()).isEqualTo(Status.CANCELLED_BY_SETTINGS_UPDATE);
    }

    @Test
    public void vibrate_thenSettingsRefreshedWithoutChange_doNotCancelVibration() throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        VibratorManagerService service = createSystemReadyService();

        vibrate(service, VibrationEffect.createOneShot(2 * TEST_TIMEOUT_MILLIS, 100),
                HAPTIC_FEEDBACK_ATTRS);

        // VibrationThread will start this vibration async, so wait until vibration is triggered.
        assertTrue(waitUntil(s -> s.isVibrating(1), service, TEST_TIMEOUT_MILLIS));

        service.updateServiceState();

        // Vibration is not stopped nearly after updating service.
        assertFalse(waitUntil(s -> !s.isVibrating(1), service, 50));
    }

    @Test
    public void vibrate_thenSettingsChange_getsCancelled() throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        VibratorManagerService service = createSystemReadyService();

        HalVibration vib = vibrate(service,
                VibrationEffect.createOneShot(2 * TEST_TIMEOUT_MILLIS, 100),
                HAPTIC_FEEDBACK_ATTRS);

        // VibrationThread will start this vibration async, so wait until vibration is triggered.
        assertTrue(waitUntil(s -> s.isVibrating(1), service, TEST_TIMEOUT_MILLIS));

        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, Vibrator.VIBRATION_INTENSITY_OFF);
        service.mVibrationSettings.mSettingObserver.onChange(false);
        service.updateServiceState();

        assertTrue(waitUntil(s -> vib.hasEnded(), service, TEST_TIMEOUT_MILLIS));
        assertThat(vib.getStatus()).isEqualTo(Status.CANCELLED_BY_SETTINGS_UPDATE);
    }

    @Test
    public void vibrate_thenScreenTurnsOff_getsCancelled() throws Throwable {
        mHalHelper.setVibratorIds(new int[]{1});
        VibratorManagerService service = createSystemReadyService();

        HalVibration vib = vibrate(service, VibrationEffect.createWaveform(
                new long[]{0, TEST_TIMEOUT_MILLIS, TEST_TIMEOUT_MILLIS}, 0), ALARM_ATTRS);

        assertTrue(waitUntil(s -> s.isVibrating(1), service, TEST_TIMEOUT_MILLIS));

        service.mIntentReceiver.onReceive(mContextSpy, new Intent(Intent.ACTION_SCREEN_OFF));

        assertTrue(waitUntil(s -> vib.hasEnded(), service, TEST_TIMEOUT_MILLIS));
        assertThat(vib.getStatus()).isEqualTo(Status.CANCELLED_BY_SCREEN_OFF);
    }

    @Test
    public void vibrate_thenFgUserRequestsMute_getsCancelled() throws Throwable {
        assumeTrue(UserManagerInternal.shouldShowNotificationForBackgroundUserSounds());
        mHalHelper.setVibratorIds(new int[]{1});
        VibratorManagerService service = createSystemReadyService();

        HalVibration vib = vibrate(service, VibrationEffect.createWaveform(
                new long[]{0, TEST_TIMEOUT_MILLIS, TEST_TIMEOUT_MILLIS}, 0), ALARM_ATTRS);

        assertTrue(waitUntil(s -> s.isVibrating(1), service, TEST_TIMEOUT_MILLIS));


        service.mIntentReceiver.onReceive(mContextSpy, new Intent(
                BackgroundUserSoundNotifier.ACTION_MUTE_SOUND));

        assertTrue(waitUntil(s -> vib.hasEnded(), service, TEST_TIMEOUT_MILLIS));
        assertThat(vib.getStatus()).isEqualTo(Status.CANCELLED_BY_FOREGROUND_USER);
    }

    @Test
    public void vibrate_withVibrationAttributes_usesCorrespondingAudioUsageInAppOpsManager() {
        VibratorManagerService service = createSystemReadyService();

        vibrate(service, VibrationEffect.get(EFFECT_CLICK), ALARM_ATTRS);
        vibrate(service, VibrationEffect.get(EFFECT_TICK), NOTIFICATION_ATTRS);
        vibrate(service, VibrationEffect.get(EFFECT_CLICK), RINGTONE_ATTRS);
        vibrate(service, VibrationEffect.get(EFFECT_TICK), HAPTIC_FEEDBACK_ATTRS);
        vibrate(service, VibrationEffect.get(EFFECT_CLICK),
                new VibrationAttributes.Builder().setUsage(
                        VibrationAttributes.USAGE_COMMUNICATION_REQUEST).build());
        vibrate(service, VibrationEffect.createOneShot(2000, 200),
                new VibrationAttributes.Builder().setUsage(
                        VibrationAttributes.USAGE_UNKNOWN).build());
        vibrate(service, VibrationEffect.get(EFFECT_CLICK), IME_FEEDBACK_ATTRS);

        InOrder inOrderVerifier = inOrder(mAppOpsManagerMock);
        inOrderVerifier.verify(mAppOpsManagerMock).checkAudioOpNoThrow(eq(AppOpsManager.OP_VIBRATE),
                eq(AudioAttributes.USAGE_ALARM), anyInt(), anyString());
        inOrderVerifier.verify(mAppOpsManagerMock).checkAudioOpNoThrow(eq(AppOpsManager.OP_VIBRATE),
                eq(AudioAttributes.USAGE_NOTIFICATION), anyInt(), anyString());
        inOrderVerifier.verify(mAppOpsManagerMock).checkAudioOpNoThrow(eq(AppOpsManager.OP_VIBRATE),
                eq(AudioAttributes.USAGE_NOTIFICATION_RINGTONE), anyInt(), anyString());
        inOrderVerifier.verify(mAppOpsManagerMock).checkAudioOpNoThrow(eq(AppOpsManager.OP_VIBRATE),
                eq(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION), anyInt(), anyString());
        inOrderVerifier.verify(mAppOpsManagerMock).checkAudioOpNoThrow(eq(AppOpsManager.OP_VIBRATE),
                eq(AudioAttributes.USAGE_VOICE_COMMUNICATION),
                anyInt(), anyString());
        inOrderVerifier.verify(mAppOpsManagerMock).checkAudioOpNoThrow(eq(AppOpsManager.OP_VIBRATE),
                eq(AudioAttributes.USAGE_UNKNOWN), anyInt(), anyString());
        inOrderVerifier.verify(mAppOpsManagerMock).checkAudioOpNoThrow(eq(AppOpsManager.OP_VIBRATE),
                eq(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION), anyInt(), anyString());
    }

    @Test
    public void vibrate_withVibrationAttributesEnforceFreshSettings_refreshesVibrationSettings()
            throws Exception {
        mHalHelper.setVibratorIds(new int[]{0});
        mHalHelper.getVibratorHelper(0).setSupportedEffects(EFFECT_CLICK, EFFECT_TICK);
        setUserSetting(Settings.System.NOTIFICATION_VIBRATION_INTENSITY,
                Vibrator.VIBRATION_INTENSITY_HIGH);
        VibratorManagerService service = createSystemReadyService();

        VibrationAttributes notificationWithFreshAttrs = new VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_NOTIFICATION)
                .setFlags(VibrationAttributes.FLAG_INVALIDATE_SETTINGS_CACHE)
                .build();

        setUserSetting(Settings.System.NOTIFICATION_VIBRATION_INTENSITY,
                Vibrator.VIBRATION_INTENSITY_LOW);
        vibrateAndWaitUntilFinished(service, VibrationEffect.get(EFFECT_CLICK), NOTIFICATION_ATTRS);
        vibrateAndWaitUntilFinished(service, VibrationEffect.get(EFFECT_TICK),
                notificationWithFreshAttrs);

        assertEquals(
                Arrays.asList(expectedPrebaked(EFFECT_CLICK, EFFECT_STRENGTH_STRONG),
                        expectedPrebaked(EFFECT_TICK, EFFECT_STRENGTH_LIGHT)),
                mHalHelper.getVibratorHelper(0).getEffectSegments());
    }

    @Test
    public void vibrate_withAttributesUnknownUsage_usesEffectToIdentifyTouchUsage() {
        VibratorManagerService service = createSystemReadyService();

        VibrationAttributes unknownAttributes = VibrationAttributes.createForUsage(
                VibrationAttributes.USAGE_UNKNOWN);
        vibrate(service, VibrationEffect.get(EFFECT_CLICK), unknownAttributes);
        vibrate(service, VibrationEffect.createOneShot(200, 200), unknownAttributes);
        vibrate(service, VibrationEffect.createWaveform(
                new long[] { 100, 200, 300 }, new int[] {1, 2, 3}, -1), unknownAttributes);
        vibrate(service,
                VibrationEffect.startComposition()
                        .addPrimitive(PRIMITIVE_QUICK_RISE)
                        .addPrimitive(PRIMITIVE_QUICK_FALL)
                        .compose(),
                unknownAttributes);

        verify(mAppOpsManagerMock, times(4))
                .checkAudioOpNoThrow(eq(AppOpsManager.OP_VIBRATE),
                        eq(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION), anyInt(), anyString());
        verify(mAppOpsManagerMock, never())
                .checkAudioOpNoThrow(eq(AppOpsManager.OP_VIBRATE),
                        eq(AudioAttributes.USAGE_UNKNOWN), anyInt(), anyString());
    }

    @Test
    public void vibrate_withAttributesUnknownUsage_ignoresEffectIfNotHapticFeedbackCandidate() {
        VibratorManagerService service = createSystemReadyService();

        VibrationAttributes unknownAttributes = VibrationAttributes.createForUsage(
                VibrationAttributes.USAGE_UNKNOWN);
        vibrate(service, VibrationEffect.get(VibrationEffect.RINGTONES[0]), unknownAttributes);
        vibrate(service, VibrationEffect.createOneShot(2000, 200), unknownAttributes);
        vibrate(service, VibrationEffect.createWaveform(
                new long[] { 100, 200, 300 }, new int[] {1, 2, 3}, 0), unknownAttributes);
        vibrate(service,
                VibrationEffect.startComposition()
                        .addPrimitive(PRIMITIVE_QUICK_RISE)
                        .addPrimitive(PRIMITIVE_QUICK_FALL)
                        .addPrimitive(PRIMITIVE_SPIN)
                        .addPrimitive(PRIMITIVE_THUD)
                        .addPrimitive(PRIMITIVE_CLICK)
                        .addPrimitive(PRIMITIVE_TICK)
                        .compose(),
                unknownAttributes);

        verify(mAppOpsManagerMock, never())
                .checkAudioOpNoThrow(eq(AppOpsManager.OP_VIBRATE),
                        eq(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION), anyInt(), anyString());
        verify(mAppOpsManagerMock, times(4))
                .checkAudioOpNoThrow(eq(AppOpsManager.OP_VIBRATE),
                        eq(AudioAttributes.USAGE_UNKNOWN), anyInt(), anyString());
    }

    @Test
    public void vibrate_withOngoingRepeatingVibration_ignoresEffect() throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        VibratorManagerService service = createSystemReadyService();

        VibrationEffect repeatingEffect = VibrationEffect.createWaveform(
                new long[]{10, 10_000}, new int[]{128, 255}, 1);
        vibrate(service, repeatingEffect,
                new VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_UNKNOWN)
                        .build());

        // VibrationThread will start this vibration async.
        // Wait until second step started to ensure the noteVibratorOn was triggered.
        assertTrue(waitUntil(s -> vibratorHelper.getAmplitudes().size() == 2,
                service, TEST_TIMEOUT_MILLIS));

        vibrateAndWaitUntilFinished(service, VibrationEffect.get(EFFECT_CLICK),
                HAPTIC_FEEDBACK_ATTRS);

        // The time estimate is recorded when the vibration starts, repeating vibrations
        // are capped at BATTERY_STATS_REPEATING_VIBRATION_DURATION (=5000).
        verify(mBatteryStatsMock).noteVibratorOn(UID, 5000);
        // The second vibration shouldn't have recorded that the vibrators were turned on.
        verify(mBatteryStatsMock, times(1)).noteVibratorOn(anyInt(), anyLong());
        // No segment played is the prebaked CLICK from the second vibration.
        assertFalse(vibratorHelper.getEffectSegments().stream()
                .anyMatch(PrebakedSegment.class::isInstance));
    }

    @Test
    public void vibrate_withOngoingRepeatingVibrationBeingCancelled_playsAfterPreviousIsCancelled()
            throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setOffLatency(50); // Add latency so cancellation is slow.
        vibratorHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        vibratorHelper.setSupportedEffects(EFFECT_CLICK);
        VibratorManagerService service = createSystemReadyService();

        VibrationEffect repeatingEffect = VibrationEffect.createWaveform(
                new long[]{10, 10_000}, new int[]{128, 255}, 1);
        vibrate(service, repeatingEffect, ALARM_ATTRS);

        // VibrationThread will start this vibration async.
        // Wait until second step started to ensure the noteVibratorOn was triggered.
        assertTrue(waitUntil(s -> vibratorHelper.getAmplitudes().size() == 2, service,
                TEST_TIMEOUT_MILLIS));

        // Cancel vibration right before requesting a new one.
        // This should trigger slow IVibrator.off before setting the vibration status to cancelled.
        cancelVibrate(service);
        vibrateAndWaitUntilFinished(service, VibrationEffect.get(EFFECT_CLICK), ALARM_ATTRS);

        // The second vibration should have recorded that the vibrators were turned on.
        verify(mBatteryStatsMock, times(2)).noteVibratorOn(anyInt(), anyLong());
        // Check that second vibration was played.
        assertTrue(vibratorHelper.getEffectSegments().stream()
                .anyMatch(PrebakedSegment.class::isInstance));
    }

    @Test
    public void vibrate_withNewSameImportanceVibrationAndBothRepeating_cancelsOngoingEffect()
            throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        VibratorManagerService service = createSystemReadyService();

        VibrationEffect repeatingEffect = VibrationEffect.createWaveform(
                new long[]{10, 10_000}, new int[]{128, 255}, 1);
        vibrate(service, repeatingEffect, ALARM_ATTRS);

        // VibrationThread will start this vibration async.
        // Wait until second step started to ensure the noteVibratorOn was triggered.
        assertTrue(waitUntil(s -> vibratorHelper.getAmplitudes().size() == 2, service,
                TEST_TIMEOUT_MILLIS));

        VibrationEffect repeatingEffect2 = VibrationEffect.createWaveform(
                new long[]{10, 10_000}, new int[]{255, 128}, 1);
        vibrate(service, repeatingEffect2, ALARM_ATTRS);

        // VibrationThread will start this vibration async.
        // Wait until second step started to ensure the noteVibratorOn was triggered.
        assertTrue(waitUntil(s -> vibratorHelper.getAmplitudes().size() == 4, service,
                TEST_TIMEOUT_MILLIS));

        // The second vibration should have recorded that the vibrators were turned on.
        verify(mBatteryStatsMock, times(2)).noteVibratorOn(anyInt(), anyLong());
    }

    @Test
    public void vibrate_withNewSameImportanceVibrationButOngoingIsRepeating_ignoreNewVibration()
            throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        VibratorManagerService service = createSystemReadyService();

        VibrationEffect repeatingEffect = VibrationEffect.createWaveform(
                new long[]{10, 10_000}, new int[]{128, 255}, 1);
        vibrate(service, repeatingEffect, ALARM_ATTRS);

        // VibrationThread will start this vibration async.
        // Wait until second step started to ensure the noteVibratorOn was triggered.
        assertTrue(waitUntil(s -> vibratorHelper.getAmplitudes().size() == 2, service,
                TEST_TIMEOUT_MILLIS));

        vibrateAndWaitUntilFinished(service, VibrationEffect.get(EFFECT_CLICK), ALARM_ATTRS);

        // The second vibration shouldn't have recorded that the vibrators were turned on.
        verify(mBatteryStatsMock, times(1)).noteVibratorOn(anyInt(), anyLong());
        // The second vibration shouldn't have played any prebaked segment.
        assertFalse(vibratorHelper.getEffectSegments().stream()
                .anyMatch(PrebakedSegment.class::isInstance));
    }

    @Test
    public void vibrate_withNewUnknownUsageVibrationAndRepeating_cancelsOngoingEffect()
            throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        VibratorManagerService service = createSystemReadyService();

        VibrationEffect repeatingEffect = VibrationEffect.createWaveform(
                new long[]{10, 10_000}, new int[]{128, 255}, 1);
        vibrate(service, repeatingEffect, ALARM_ATTRS);

        // VibrationThread will start this vibration async.
        // Wait until second step started to ensure the noteVibratorOn was triggered.
        assertTrue(waitUntil(s -> vibratorHelper.getAmplitudes().size() == 2, service,
                TEST_TIMEOUT_MILLIS));

        VibrationEffect repeatingEffect2 = VibrationEffect.createWaveform(
                new long[]{10, 10_000}, new int[]{255, 128}, 1);
        vibrate(service, repeatingEffect2, UNKNOWN_ATTRS);

        // VibrationThread will start this vibration async.
        // Wait until second step started to ensure the noteVibratorOn was triggered.
        assertTrue(waitUntil(s -> vibratorHelper.getAmplitudes().size() == 4, service,
                TEST_TIMEOUT_MILLIS));

        // The second vibration should have recorded that the vibrators were turned on.
        verify(mBatteryStatsMock, times(2)).noteVibratorOn(anyInt(), anyLong());
    }

    @Test
    public void vibrate_withNewUnknownUsageVibrationAndNotRepeating_ignoreNewVibration()
            throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        VibratorManagerService service = createSystemReadyService();

        VibrationEffect alarmEffect = VibrationEffect.createWaveform(
                new long[]{10, 10_000}, new int[]{128, 255}, -1);
        vibrate(service, alarmEffect, ALARM_ATTRS);

        // VibrationThread will start this vibration async.
        // Wait until second step started to ensure the noteVibratorOn was triggered.
        assertTrue(waitUntil(s -> vibratorHelper.getAmplitudes().size() == 2, service,
                TEST_TIMEOUT_MILLIS));

        vibrateAndWaitUntilFinished(service, VibrationEffect.get(EFFECT_CLICK), UNKNOWN_ATTRS);

        // The second vibration shouldn't have recorded that the vibrators were turned on.
        verify(mBatteryStatsMock, times(1)).noteVibratorOn(anyInt(), anyLong());
        // The second vibration shouldn't have played any prebaked segment.
        assertFalse(vibratorHelper.getEffectSegments().stream()
                .anyMatch(PrebakedSegment.class::isInstance));
    }

    @Test
    public void vibrate_withOngoingHigherImportanceVibration_ignoresEffect() throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        VibratorManagerService service = createSystemReadyService();

        VibrationEffect effect = VibrationEffect.createWaveform(
                new long[]{10, 10_000}, new int[]{128, 255}, -1);
        vibrate(service, effect, ALARM_ATTRS);

        // VibrationThread will start this vibration async.
        // Wait until second step started to ensure the noteVibratorOn was triggered.
        assertTrue(waitUntil(s -> vibratorHelper.getAmplitudes().size() == 2,
                service, TEST_TIMEOUT_MILLIS));

        vibrateAndWaitUntilFinished(service, VibrationEffect.get(EFFECT_CLICK),
                HAPTIC_FEEDBACK_ATTRS);

        // The second vibration shouldn't have recorded that the vibrators were turned on.
        verify(mBatteryStatsMock, times(1)).noteVibratorOn(anyInt(), anyLong());
        // The second vibration shouldn't have played any prebaked segment.
        assertFalse(vibratorHelper.getEffectSegments().stream()
                .anyMatch(PrebakedSegment.class::isInstance));
    }

    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    @Test
    public void vibrate_withOngoingHigherImportanceVendorSession_ignoresEffect() throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        VibratorManagerService service = createSystemReadyService();
        IVibrationSessionCallback callback = mockSessionCallbacks();

        VendorVibrationSession session = startSession(service, RINGTONE_ATTRS, callback, 1);
        // Keep auto-dispatcher that can be used in the session cancellation when vibration starts.
        mTestLooper.dispatchAll();

        assertThat(session.getStatus()).isEqualTo(Status.RUNNING);
        verify(callback).onStarted(any(IVibrationSession.class));

        HalVibration vibration = vibrateAndWaitUntilFinished(service,
                VibrationEffect.get(EFFECT_CLICK), HAPTIC_FEEDBACK_ATTRS);

        // Make sure all messages are processed before asserting on the session callbacks.
        stopAutoDispatcherAndDispatchAll();

        assertThat(session.getStatus()).isEqualTo(Status.RUNNING);
        assertThat(vibration.getStatus()).isEqualTo(Status.IGNORED_FOR_HIGHER_IMPORTANCE);
        verify(callback, never()).onFinishing();
        verify(callback, never()).onFinished(anyInt());
        // The second vibration shouldn't have played any prebaked segment.
        assertFalse(vibratorHelper.getEffectSegments().stream()
                .anyMatch(PrebakedSegment.class::isInstance));
    }

    @Test
    public void vibrate_withOngoingLowerImportanceVibration_cancelsOngoingEffect()
            throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        vibratorHelper.setSupportedEffects(EFFECT_CLICK);
        VibratorManagerService service = createSystemReadyService();

        VibrationEffect effect = VibrationEffect.createWaveform(
                new long[]{10, 10_000}, new int[]{128, 255}, -1);
        vibrate(service, effect, HAPTIC_FEEDBACK_ATTRS);

        // VibrationThread will start this vibration async.
        // Wait until second step started to ensure the noteVibratorOn was triggered.
        assertTrue(waitUntil(s -> vibratorHelper.getAmplitudes().size() == 2, service,
                TEST_TIMEOUT_MILLIS));

        vibrateAndWaitUntilFinished(service, VibrationEffect.get(EFFECT_CLICK), RINGTONE_ATTRS);

        // The second vibration should have recorded that the vibrators were turned on.
        verify(mBatteryStatsMock, times(2)).noteVibratorOn(anyInt(), anyLong());
        // One segment played is the prebaked CLICK from the second vibration.
        assertEquals(1, vibratorHelper.getEffectSegments().stream()
                .filter(PrebakedSegment.class::isInstance).count());
    }

    @Test
    public void vibrate_withOngoingLowerImportanceExternalVibration_cancelsOngoingVibration()
            throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        mHalHelper.getVibratorHelper(1).setSupportedEffects(EFFECT_CLICK);
        setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        VibratorManagerService service = createSystemReadyService();

        IBinder firstToken = mock(IBinder.class);
        IExternalVibrationController externalVibratonController =
                mock(IExternalVibrationController.class);
        ExternalVibration externalVibration = new ExternalVibration(UID, PACKAGE_NAME,
                AUDIO_ALARM_ATTRS, externalVibratonController, firstToken);
        ExternalVibrationScale scale =
                mExternalVibratorService.onExternalVibrationStart(externalVibration);

        vibrateAndWaitUntilFinished(service, VibrationEffect.get(EFFECT_CLICK), RINGTONE_ATTRS);

        assertNotEquals(ExternalVibrationScale.ScaleLevel.SCALE_MUTE, scale.scaleLevel);
        // The external vibration should have been cancelled
        verify(externalVibratonController).mute();
        assertEquals(Arrays.asList(false, true, false),
                mHalHelper.getVibratorHelper(1).getExternalControlStates());
        // The new vibration should have recorded that the vibrators were turned on.
        verify(mBatteryStatsMock, times(1)).noteVibratorOn(anyInt(), anyLong());
        // One segment played is the prebaked CLICK from the new vibration.
        assertEquals(1, mHalHelper.getVibratorHelper(1).getEffectSegments().stream()
                .filter(PrebakedSegment.class::isInstance).count());
    }

    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    @Test
    public void vibrate_withOngoingLowerImportanceVendorSession_cancelsOngoingSession()
            throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setSupportedEffects(EFFECT_CLICK);
        VibratorManagerService service = createSystemReadyService();
        IVibrationSessionCallback callback = mockSessionCallbacks();

        VendorVibrationSession session = startSession(service, HAPTIC_FEEDBACK_ATTRS, callback, 1);
        // Keep auto-dispatcher that can be used in the session cancellation when vibration starts.
        mTestLooper.dispatchAll();

        assertThat(session.getStatus()).isEqualTo(Status.RUNNING);
        verify(callback).onStarted(any(IVibrationSession.class));

        HalVibration vibration = vibrateAndWaitUntilFinished(service,
                VibrationEffect.get(EFFECT_CLICK), HAPTIC_FEEDBACK_ATTRS);

        // Make sure all messages are processed before asserting on the session callbacks.
        stopAutoDispatcherAndDispatchAll();

        assertThat(session.getStatus()).isEqualTo(Status.CANCELLED_SUPERSEDED);
        assertThat(vibration.getStatus()).isEqualTo(Status.FINISHED);
        verify(callback).onFinishing();
        verify(callback).onFinished(eq(android.os.vibrator.VendorVibrationSession.STATUS_CANCELED));
        // One segment played is the prebaked CLICK from the new vibration.
        assertEquals(1, mHalHelper.getVibratorHelper(1).getEffectSegments().stream()
                .filter(PrebakedSegment.class::isInstance).count());
    }

    @Test
    public void vibrate_withOngoingSameImportancePipelinedVibration_continuesOngoingEffect()
            throws Exception {
        VibrationAttributes pipelineAttrs = new VibrationAttributes.Builder(HAPTIC_FEEDBACK_ATTRS)
                .setFlags(VibrationAttributes.FLAG_PIPELINED_EFFECT)
                .build();

        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        vibratorHelper.setSupportedEffects(EFFECT_CLICK);
        VibratorManagerService service = createSystemReadyService();

        VibrationEffect effect = VibrationEffect.createWaveform(
                new long[]{100, 100}, new int[]{128, 255}, -1);
        vibrate(service, effect, pipelineAttrs);
        // This vibration will be enqueued, but evicted by the EFFECT_CLICK.
        vibrate(service, VibrationEffect.startComposition()
                .addOffDuration(Duration.ofSeconds(10))
                .addPrimitive(PRIMITIVE_SLOW_RISE)
                .compose(), pipelineAttrs);  // This will queue and be evicted for the click.

        vibrateAndWaitUntilFinished(service, VibrationEffect.get(EFFECT_CLICK), pipelineAttrs);

        // The second vibration should have recorded that the vibrators were turned on.
        verify(mBatteryStatsMock, times(2)).noteVibratorOn(anyInt(), anyLong());
        // One step segment (with several amplitudes) and one click should have played. Notably
        // there is no primitive segment.
        List<VibrationEffectSegment> played = vibratorHelper.getEffectSegments();
        assertEquals(2, played.size());
        assertEquals(1, played.stream().filter(StepSegment.class::isInstance).count());
        assertEquals(1, played.stream().filter(PrebakedSegment.class::isInstance).count());
    }

    @Test
    public void vibrate_withPipelineMaxDurationConfigAndShortEffect_continuesOngoingEffect()
            throws Exception {
        assumeTrue(mVibrationConfig.getVibrationPipelineMaxDurationMs() > 0);

        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        vibratorHelper.setSupportedPrimitives(PRIMITIVE_CLICK, PRIMITIVE_THUD);
        vibratorHelper.setPrimitiveDuration(
                mVibrationConfig.getVibrationPipelineMaxDurationMs() - 1);
        VibratorManagerService service = createSystemReadyService();

        HalVibration firstVibration = vibrateWithUid(service, /* uid= */ 123,
                VibrationEffect.startComposition()
                        .addPrimitive(PRIMITIVE_CLICK)
                        .compose(), HAPTIC_FEEDBACK_ATTRS);
        HalVibration secondVibration = vibrateWithUid(service, /* uid= */ 456,
                VibrationEffect.startComposition()
                        .addPrimitive(PRIMITIVE_THUD)
                        .compose(), HAPTIC_FEEDBACK_ATTRS);
        secondVibration.waitForEnd();

        assertThat(vibratorHelper.getEffectSegments()).hasSize(2);
        assertThat(firstVibration.getStatus()).isEqualTo(Status.FINISHED);
        assertThat(secondVibration.getStatus()).isEqualTo(Status.FINISHED);
    }

    @Test
    public void vibrate_withInputDevices_vibratesInputDevices() throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        vibratorHelper.setSupportedEffects(EFFECT_CLICK);
        when(mIInputManagerMock.getInputDeviceIds()).thenReturn(new int[]{1});
        when(mIInputManagerMock.getVibratorIds(eq(1))).thenReturn(new int[]{1});
        when(mIInputManagerMock.getInputDevice(eq(1))).thenReturn(createInputDeviceWithVibrator(1));
        setUserSetting(Settings.System.VIBRATE_INPUT_DEVICES, 1);
        // Mock alarm intensity equals to default value to avoid scaling in this test.
        setUserSetting(Settings.System.ALARM_VIBRATION_INTENSITY,
                mVibrator.getDefaultVibrationIntensity(VibrationAttributes.USAGE_ALARM));
        VibratorManagerService service = createSystemReadyService();

        CombinedVibration effect = CombinedVibration.createParallel(
                VibrationEffect.createOneShot(10, 10));
        vibrateAndWaitUntilFinished(service, effect, ALARM_ATTRS);

        verify(mIInputManagerMock).vibrateCombined(eq(1), eq(effect), any());
        assertTrue(vibratorHelper.getEffectSegments().isEmpty());
    }

    @Test
    public void vibrate_withHalCallbackTriggered_finishesVibration() throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setSupportedEffects(EFFECT_CLICK);
        VibratorManagerService service = createSystemReadyService();
        // The HAL callback will be dispatched manually in this test.
        mTestLooper.stopAutoDispatchAndIgnoreExceptions();

        vibrate(service, VibrationEffect.get(EFFECT_CLICK), ALARM_ATTRS);

        // VibrationThread will start this vibration async, so wait before triggering callbacks.
        assertTrue(waitUntil(s -> s.isVibrating(1), service, TEST_TIMEOUT_MILLIS));

        // Trigger callbacks from vibrator.
        mTestLooper.moveTimeForward(50);
        mTestLooper.dispatchAll();

        // VibrationThread needs some time to react to HAL callbacks and stop the vibrator.
        assertTrue(waitUntil(s -> !s.isVibrating(1), service, TEST_TIMEOUT_MILLIS));
    }

    @Test
    public void vibrate_withTriggerCallback_finishesVibration() throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_SYNC, IVibratorManager.CAP_TRIGGER_CALLBACK,
                IVibratorManager.CAP_PREPARE_COMPOSE);
        mHalHelper.setVibratorIds(new int[]{1, 2});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        mHalHelper.getVibratorHelper(1).setSupportedPrimitives(PRIMITIVE_CLICK);
        mHalHelper.getVibratorHelper(2).setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        mHalHelper.getVibratorHelper(2).setSupportedPrimitives(PRIMITIVE_CLICK);
        // Mock alarm intensity equals to default value to avoid scaling in this test.
        setUserSetting(Settings.System.ALARM_VIBRATION_INTENSITY,
                mVibrator.getDefaultVibrationIntensity(VibrationAttributes.USAGE_ALARM));
        VibratorManagerService service = createSystemReadyService();

        VibrationEffect composed = VibrationEffect.startComposition()
                .addPrimitive(PRIMITIVE_CLICK, 1, 100)
                .compose();
        CombinedVibration effect = CombinedVibration.createParallel(composed);

        vibrate(service, effect, ALARM_ATTRS);
        waitUntil(s -> mHalHelper.getTriggerSyncedCount() > 0, service,
                TEST_TIMEOUT_MILLIS);
        mHalHelper.endLastSyncedVibration();

        PrimitiveSegment expected = new PrimitiveSegment(PRIMITIVE_CLICK, 1, 100);
        assertThat(mHalHelper.getPrepareSyncedCount()).isEqualTo(1);
        assertThat(mHalHelper.getTriggerSyncedCount()).isEqualTo(1);
        assertThat(mHalHelper.getVibratorHelper(1).getEffectSegments())
                .containsExactly(expected).inOrder();
        assertThat(mHalHelper.getVibratorHelper(2).getEffectSegments())
                .containsExactly(expected).inOrder();

        // VibrationThread needs some time to react to native callbacks and stop the vibrator.
        assertTrue(waitUntil(s -> !s.isVibrating(1), service, TEST_TIMEOUT_MILLIS));
    }

    @Test
    public void vibrate_withMultipleVibratorsAndCapabilities_prepareAndTriggerCalled()
            throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_SYNC, IVibratorManager.CAP_PREPARE_PERFORM,
                IVibratorManager.CAP_PREPARE_COMPOSE, IVibratorManager.CAP_MIXED_TRIGGER_PERFORM,
                IVibratorManager.CAP_MIXED_TRIGGER_COMPOSE);
        mHalHelper.setVibratorIds(new int[]{1, 2});
        HalVibratorHelper vibratorHelper1 = mHalHelper.getVibratorHelper(1);
        vibratorHelper1.setSupportedEffects(EFFECT_CLICK);
        mHalHelper.getVibratorHelper(2).setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        mHalHelper.getVibratorHelper(2).setSupportedPrimitives(PRIMITIVE_CLICK);
        VibratorManagerService service = createSystemReadyService();

        CombinedVibration effect = CombinedVibration.startParallel()
                .addVibrator(1, VibrationEffect.get(EFFECT_CLICK))
                .addVibrator(2, VibrationEffect.startComposition()
                        .addPrimitive(PRIMITIVE_CLICK)
                        .compose())
                .combine();
        vibrateAndWaitUntilFinished(service, effect, ALARM_ATTRS);

        assertThat(mHalHelper.getPrepareSyncedCount()).isEqualTo(1);
        assertThat(mHalHelper.getTriggerSyncedCount()).isEqualTo(1);
    }

    @Test
    public void vibrate_withMultipleVibratorsWithoutCapabilities_skipPrepareAndTrigger()
            throws Exception {
        // Missing CAP_MIXED_TRIGGER_ON and CAP_MIXED_TRIGGER_PERFORM.
        mHalHelper.setCapabilities(IVibratorManager.CAP_SYNC, IVibratorManager.CAP_PREPARE_ON,
                IVibratorManager.CAP_PREPARE_PERFORM);
        mHalHelper.setVibratorIds(new int[]{1, 2});
        HalVibratorHelper vibratorHelper1 = mHalHelper.getVibratorHelper(1);
        vibratorHelper1.setSupportedEffects(EFFECT_CLICK);
        VibratorManagerService service = createSystemReadyService();

        CombinedVibration effect = CombinedVibration.startParallel()
                .addVibrator(1, VibrationEffect.get(EFFECT_CLICK))
                .addVibrator(2, VibrationEffect.createOneShot(10, 100))
                .combine();
        vibrateAndWaitUntilFinished(service, effect, ALARM_ATTRS);

        assertThat(mHalHelper.getPrepareSyncedCount()).isEqualTo(0);
        assertThat(mHalHelper.getTriggerSyncedCount()).isEqualTo(0);
    }

    @Test
    @EnableFlags(Flags.FLAG_REMOVE_HIDL_SUPPORT)
    public void vibrate_withLegacyHal_skipPrepareAndTrigger() throws Exception {
        // Capabilities and IDs ignored by legacy HAL service, as it's backed by single IVibrator.
        mHalHelper.setCapabilities(IVibratorManager.CAP_SYNC, IVibratorManager.CAP_PREPARE_ON);
        mHalHelper.setVibratorIds(new int[] { 1, 2 });
        mUseLegacyHalManager = true;
        VibratorManagerService service = createSystemReadyService();

        CombinedVibration effect =
                CombinedVibration.createParallel(VibrationEffect.createOneShot(10, 100));
        vibrateAndWaitUntilFinished(service, effect, ALARM_ATTRS);

        assertThat(mHalHelper.getPrepareSyncedCount()).isEqualTo(0);
        assertThat(mHalHelper.getTriggerSyncedCount()).isEqualTo(0);
    }

    @Test
    public void vibrate_withMultipleVibratorsPrepareFailed_skipTriggerAndCancel() throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_SYNC, IVibratorManager.CAP_PREPARE_ON);
        mHalHelper.setVibratorIds(new int[]{1, 2});
        mHalHelper.setPrepareSyncedToFail();
        VibratorManagerService service = createSystemReadyService();
        int cancelCount = mHalHelper.getCancelSyncedCount();

        CombinedVibration effect = CombinedVibration.startParallel()
                .addVibrator(1, VibrationEffect.createOneShot(10, 50))
                .addVibrator(2, VibrationEffect.createOneShot(10, 100))
                .combine();
        vibrateAndWaitUntilFinished(service, effect, ALARM_ATTRS);

        assertThat(mHalHelper.getTriggerSyncedCount()).isEqualTo(0);
        assertThat(mHalHelper.getCancelSyncedCount()).isEqualTo(cancelCount);
    }

    @Test
    public void vibrate_withMultipleVibratorsTriggerFailed_cancelPreparedSynced() throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_SYNC, IVibratorManager.CAP_PREPARE_ON);
        mHalHelper.setVibratorIds(new int[]{1, 2});
        mHalHelper.setTriggerSyncedToFail();
        VibratorManagerService service = createSystemReadyService();
        int cancelCount = mHalHelper.getCancelSyncedCount();

        CombinedVibration effect = CombinedVibration.startParallel()
                .addVibrator(1, VibrationEffect.createOneShot(10, 50))
                .addVibrator(2, VibrationEffect.createOneShot(10, 100))
                .combine();
        Vibration vibration = vibrateAndWaitUntilFinished(service, effect, ALARM_ATTRS);

        assertThat(vibration.getStatus()).isEqualTo(Status.FINISHED);
        assertThat(mHalHelper.getPrepareSyncedCount()).isEqualTo(1);
        assertThat(mHalHelper.getCancelSyncedCount()).isEqualTo(cancelCount + 1);
    }

    @Test
    @EnableFlags(android.view.flags.Flags.FLAG_SCROLL_FEEDBACK_API)
    public void performHapticFeedback_doesNotRequireVibrateOrBypassPermissions() throws Exception {
        // Deny permissions that would have been required for regular vibrations, and check that
        // the vibration proceed as expected to verify that haptic feedback does not need these
        // permissions.
        denyPermission(android.Manifest.permission.VIBRATE);
        denyPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS);
        denyPermission(android.Manifest.permission.MODIFY_PHONE_STATE);
        denyPermission(android.Manifest.permission.MODIFY_AUDIO_ROUTING);
        mHapticFeedbackVibrationMap.put(
                HapticFeedbackConstants.SCROLL_TICK,
                VibrationEffect.createPredefined(EFFECT_CLICK));
        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setSupportedEffects(EFFECT_CLICK);
        VibratorManagerService service = createSystemReadyService();

        HalVibration vibration =
                performHapticFeedbackAndWaitUntilFinished(
                        service, HapticFeedbackConstants.SCROLL_TICK, /* always= */ true);

        List<VibrationEffectSegment> playedSegments = vibratorHelper.getEffectSegments();
        assertEquals(1, playedSegments.size());
        PrebakedSegment segment = (PrebakedSegment) playedSegments.get(0);
        assertEquals(EFFECT_CLICK, segment.getEffectId());
        VibrationAttributes attrs = vibration.callerInfo.attrs;
        assertTrue(attrs.isFlagSet(VibrationAttributes.FLAG_BYPASS_USER_VIBRATION_INTENSITY_OFF));
        assertTrue(attrs.isFlagSet(VibrationAttributes.FLAG_BYPASS_INTERRUPTION_POLICY));
    }

    @Test
    @EnableFlags(android.view.flags.Flags.FLAG_SCROLL_FEEDBACK_API)
    public void performHapticFeedbackForInputDevice_doesNotRequireVibrateOrBypassPermissions()
            throws Exception {
        // Deny permissions that would have been required for regular vibrations, and check that
        // the vibration proceed as expected to verify that haptic feedback does not need these
        // permissions.
        denyPermission(android.Manifest.permission.VIBRATE);
        denyPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS);
        denyPermission(android.Manifest.permission.MODIFY_PHONE_STATE);
        denyPermission(android.Manifest.permission.MODIFY_AUDIO_ROUTING);
        mHapticFeedbackVibrationMapSourceRotary.put(
                HapticFeedbackConstants.SCROLL_TICK,
                VibrationEffect.createPredefined(EFFECT_CLICK));
        mHapticFeedbackVibrationMapSourceTouchScreen.put(
                HapticFeedbackConstants.SCROLL_ITEM_FOCUS,
                VibrationEffect.createPredefined(EFFECT_THUD));
        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setSupportedEffects(EFFECT_CLICK, EFFECT_THUD);
        VibratorManagerService service = createSystemReadyService();

        HalVibration vibrationByRotary =
                performHapticFeedbackForInputDeviceAndWaitUntilFinished(
                        service, HapticFeedbackConstants.SCROLL_TICK, /* inputDeviceId= */ 0,
                        InputDevice.SOURCE_ROTARY_ENCODER, /* always= */ true);
        HalVibration vibrationByTouchScreen =
                performHapticFeedbackForInputDeviceAndWaitUntilFinished(
                        service, HapticFeedbackConstants.SCROLL_ITEM_FOCUS, /* inputDeviceId= */ 0,
                        InputDevice.SOURCE_TOUCHSCREEN, /* always= */ true);

        List<VibrationEffectSegment> playedSegments = vibratorHelper.getEffectSegments();
        // 2 haptics: 1 by rotary + 1 by touch screen
        assertEquals(2, playedSegments.size());
        // Verify feedback by rotary input
        PrebakedSegment segmentByRotary = (PrebakedSegment) playedSegments.get(0);
        assertEquals(EFFECT_CLICK, segmentByRotary.getEffectId());
        VibrationAttributes attrsByRotary = vibrationByRotary.callerInfo.attrs;
        assertTrue(attrsByRotary.isFlagSet(
                VibrationAttributes.FLAG_BYPASS_USER_VIBRATION_INTENSITY_OFF));
        assertTrue(attrsByRotary.isFlagSet(VibrationAttributes.FLAG_BYPASS_INTERRUPTION_POLICY));
        // Verify feedback by touch screen input
        PrebakedSegment segmentByTouchScreen = (PrebakedSegment) playedSegments.get(1);
        assertEquals(EFFECT_THUD, segmentByTouchScreen.getEffectId());
        VibrationAttributes attrsByTouchScreen = vibrationByTouchScreen.callerInfo.attrs;
        assertTrue(attrsByTouchScreen.isFlagSet(
                VibrationAttributes.FLAG_BYPASS_USER_VIBRATION_INTENSITY_OFF));
        assertTrue(
                attrsByTouchScreen.isFlagSet(VibrationAttributes.FLAG_BYPASS_INTERRUPTION_POLICY));
    }

    @Test
    @EnableFlags(Flags.FLAG_HAPTIC_FEEDBACK_WITH_CUSTOM_USAGE)
    public void performHapticFeedback_withValidCustomUsage_vibrates() throws Exception {
        // Deny permissions for extra check that custom usages do not require permission
        denyPermission(android.Manifest.permission.VIBRATE);
        denyPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS);
        denyPermission(android.Manifest.permission.MODIFY_PHONE_STATE);
        denyPermission(android.Manifest.permission.MODIFY_AUDIO_ROUTING);
        mHapticFeedbackVibrationMap.put(
                HapticFeedbackConstants.SCROLL_TICK,
                VibrationEffect.createPredefined(EFFECT_CLICK));
        mHapticFeedbackVibrationMapUsageGestureInput.put(
                HapticFeedbackConstants.SCROLL_TICK, VibrationEffect.createPredefined(EFFECT_TICK));
        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setSupportedEffects(EFFECT_CLICK, EFFECT_TICK);
        VibratorManagerService service = createSystemReadyService();

        HalVibration accessibilityVibration =
                performHapticFeedbackAndWaitUntilFinished(
                        service, HapticFeedbackConstants.SCROLL_TICK,
                        VibrationAttributes.USAGE_ACCESSIBILITY, /* always= */ true);
        HalVibration gestureInputVibration =
                performHapticFeedbackAndWaitUntilFinished(
                        service, HapticFeedbackConstants.SCROLL_TICK,
                        VibrationAttributes.USAGE_GESTURE_INPUT, /* always= */ true);

        List<VibrationEffectSegment> playedSegments = vibratorHelper.getEffectSegments();
        assertEquals(2, playedSegments.size());
        PrebakedSegment segment = (PrebakedSegment) playedSegments.get(0);
        assertEquals(EFFECT_CLICK, segment.getEffectId());
        VibrationAttributes attrs = accessibilityVibration.callerInfo.attrs;
        assertTrue(attrs.isFlagSet(VibrationAttributes.FLAG_BYPASS_USER_VIBRATION_INTENSITY_OFF));
        assertTrue(attrs.isFlagSet(VibrationAttributes.FLAG_BYPASS_INTERRUPTION_POLICY));
        assertEquals(VibrationAttributes.USAGE_ACCESSIBILITY, attrs.getUsage());

        segment = (PrebakedSegment) playedSegments.get(1);
        assertEquals(EFFECT_TICK, segment.getEffectId());
        attrs = gestureInputVibration.callerInfo.attrs;
        assertTrue(attrs.isFlagSet(VibrationAttributes.FLAG_BYPASS_USER_VIBRATION_INTENSITY_OFF));
        assertTrue(attrs.isFlagSet(VibrationAttributes.FLAG_BYPASS_INTERRUPTION_POLICY));
        assertEquals(VibrationAttributes.USAGE_GESTURE_INPUT, attrs.getUsage());
    }

    @Test
    @DisableFlags(Flags.FLAG_HAPTIC_FEEDBACK_WITH_CUSTOM_USAGE)
    public void performHapticFeedback_withValidCustomUsage_featureDisabled_noVibration()
            throws Exception {
        // Deny permissions for extra check that custom usages do not require permission
        denyPermission(android.Manifest.permission.VIBRATE);
        denyPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS);
        denyPermission(android.Manifest.permission.MODIFY_PHONE_STATE);
        denyPermission(android.Manifest.permission.MODIFY_AUDIO_ROUTING);
        mHapticFeedbackVibrationMap.put(
                HapticFeedbackConstants.SCROLL_TICK,
                VibrationEffect.createPredefined(EFFECT_CLICK));
        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setSupportedEffects(EFFECT_CLICK);
        VibratorManagerService service = createSystemReadyService();

        performHapticFeedbackAndWaitUntilFinished(
                service, HapticFeedbackConstants.SCROLL_TICK,
                VibrationAttributes.USAGE_ACCESSIBILITY, /* always= */ true);

        assertTrue(vibratorHelper.getEffectSegments().isEmpty());
    }

    @Test
    @EnableFlags(Flags.FLAG_HAPTIC_FEEDBACK_WITH_CUSTOM_USAGE)
    public void performHapticFeedback_withInvalidCustomUsage_noVibration() throws Exception {
        // Deny permissions for extra check that custom usages do not require permission
        denyPermission(android.Manifest.permission.VIBRATE);
        denyPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS);
        denyPermission(android.Manifest.permission.MODIFY_PHONE_STATE);
        denyPermission(android.Manifest.permission.MODIFY_AUDIO_ROUTING);
        mHapticFeedbackVibrationMap.put(
                HapticFeedbackConstants.SCROLL_TICK,
                VibrationEffect.createPredefined(EFFECT_CLICK));
        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setSupportedEffects(EFFECT_CLICK);
        VibratorManagerService service = createSystemReadyService();

        performHapticFeedbackAndWaitUntilFinished(
                service, HapticFeedbackConstants.SCROLL_TICK,
                VibrationAttributes.USAGE_ALARM, /* always= */ true);

        assertTrue(vibratorHelper.getEffectSegments().isEmpty());
    }


    @Test
    public void performHapticFeedback_restrictedConstantsWithoutPermission_doesNotVibrate()
            throws Exception {
        // Deny permission to vibrate with restricted constants
        denyPermission(android.Manifest.permission.VIBRATE_SYSTEM_CONSTANTS);
        // Public constant, no permission required
        mHapticFeedbackVibrationMap.put(
                HapticFeedbackConstants.CONFIRM,
                VibrationEffect.createPredefined(EFFECT_CLICK));
        // Hidden system-only constant, permission required
        mHapticFeedbackVibrationMap.put(
                HapticFeedbackConstants.BIOMETRIC_CONFIRM,
                VibrationEffect.createPredefined(EFFECT_HEAVY_CLICK));
        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setSupportedEffects(EFFECT_CLICK, EFFECT_HEAVY_CLICK);
        VibratorManagerService service = createSystemReadyService();

        performHapticFeedbackAndWaitUntilFinished(
                service, HapticFeedbackConstants.CONFIRM, /* always= */ false);

        performHapticFeedbackAndWaitUntilFinished(
                service, HapticFeedbackConstants.BIOMETRIC_CONFIRM, /* always= */ false);

        List<VibrationEffectSegment> playedSegments = vibratorHelper.getEffectSegments();
        assertEquals(1, playedSegments.size());
        PrebakedSegment segment = (PrebakedSegment) playedSegments.get(0);
        assertEquals(EFFECT_CLICK, segment.getEffectId());
    }

    @Test
    @EnableFlags(android.view.flags.Flags.FLAG_SCROLL_FEEDBACK_API)
    public void performHapticFeedbackForInputDevice_restrictedConstantsWithoutPermission_doesNotVibrate()
            throws Exception {
        // Deny permission to vibrate with restricted constants
        denyPermission(android.Manifest.permission.VIBRATE_SYSTEM_CONSTANTS);
        // Public constant, no permission required
        mHapticFeedbackVibrationMapSourceRotary.put(
                HapticFeedbackConstants.CONFIRM, VibrationEffect.createPredefined(EFFECT_CLICK));
        // Hidden system-only constant, permission required
        mHapticFeedbackVibrationMapSourceTouchScreen.put(
                HapticFeedbackConstants.BIOMETRIC_CONFIRM,
                VibrationEffect.createPredefined(EFFECT_HEAVY_CLICK));
        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setSupportedEffects(EFFECT_CLICK, EFFECT_HEAVY_CLICK);
        VibratorManagerService service = createSystemReadyService();

        // This vibrates.
        performHapticFeedbackForInputDeviceAndWaitUntilFinished(
                        service, HapticFeedbackConstants.CONFIRM, /* inputDeviceId= */ 0,
                InputDevice.SOURCE_ROTARY_ENCODER, /* always= */ false);
        // This doesn't.
        performHapticFeedbackForInputDeviceAndWaitUntilFinished(
                        service, HapticFeedbackConstants.BIOMETRIC_CONFIRM, /* inputDeviceId= */ 0,
                InputDevice.SOURCE_TOUCHSCREEN, /* always= */ false);

        List<VibrationEffectSegment> playedSegments = vibratorHelper.getEffectSegments();
        assertEquals(1, playedSegments.size());
        PrebakedSegment segment = (PrebakedSegment) playedSegments.get(0);
        assertEquals(EFFECT_CLICK, segment.getEffectId());
    }

    @Test
    public void performHapticFeedback_restrictedConstantsWithPermission_playsVibration()
            throws Exception {
        // Grant permission to vibrate with restricted constants
        grantPermission(android.Manifest.permission.VIBRATE_SYSTEM_CONSTANTS);
        // Public constant, no permission required
        mHapticFeedbackVibrationMap.put(
                HapticFeedbackConstants.CONFIRM, VibrationEffect.createPredefined(EFFECT_CLICK));
        // Hidden system-only constant, permission required
        mHapticFeedbackVibrationMap.put(
                HapticFeedbackConstants.BIOMETRIC_CONFIRM,
                VibrationEffect.createPredefined(EFFECT_HEAVY_CLICK));
        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setSupportedEffects(EFFECT_CLICK, EFFECT_HEAVY_CLICK);
        VibratorManagerService service = createSystemReadyService();

        performHapticFeedbackAndWaitUntilFinished(
                service, HapticFeedbackConstants.CONFIRM, /* always= */ false);

        performHapticFeedbackAndWaitUntilFinished(
                service, HapticFeedbackConstants.BIOMETRIC_CONFIRM, /* always= */ false);

        List<VibrationEffectSegment> playedSegments = vibratorHelper.getEffectSegments();
        assertEquals(2, playedSegments.size());
        assertEquals(EFFECT_CLICK, ((PrebakedSegment) playedSegments.get(0)).getEffectId());
        assertEquals(EFFECT_HEAVY_CLICK, ((PrebakedSegment) playedSegments.get(1)).getEffectId());
    }

    @Test
    public void performHapticFeedbackForInputDevice_internalConstantsWithPermission_playsVibration()
            throws Exception {
        // Grant permission to vibrate with restricted constants
        grantPermission(android.Manifest.permission.VIBRATE_SYSTEM_CONSTANTS);
        // Public constant, no permission required
        mHapticFeedbackVibrationMapSourceRotary.put(
                HapticFeedbackConstants.CONFIRM, VibrationEffect.createPredefined(EFFECT_CLICK));
        // Hidden system-only constant, permission required
        mHapticFeedbackVibrationMapSourceTouchScreen.put(
                HapticFeedbackConstants.BIOMETRIC_CONFIRM,
                VibrationEffect.createPredefined(EFFECT_HEAVY_CLICK));
        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setSupportedEffects(EFFECT_CLICK, EFFECT_HEAVY_CLICK);
        VibratorManagerService service = createSystemReadyService();

        performHapticFeedbackForInputDeviceAndWaitUntilFinished(
                service, HapticFeedbackConstants.CONFIRM, /* inputDeviceId= */ 0,
                InputDevice.SOURCE_ROTARY_ENCODER, /* always= */ false);
        performHapticFeedbackForInputDeviceAndWaitUntilFinished(
                service, HapticFeedbackConstants.BIOMETRIC_CONFIRM, /* inputDeviceId= */ 0,
                InputDevice.SOURCE_TOUCHSCREEN, /* always= */ false);

        List<VibrationEffectSegment> playedSegments = vibratorHelper.getEffectSegments();
        assertEquals(2, playedSegments.size());
        assertEquals(EFFECT_CLICK, ((PrebakedSegment) playedSegments.get(0)).getEffectId());
        assertEquals(EFFECT_HEAVY_CLICK, ((PrebakedSegment) playedSegments.get(1)).getEffectId());
    }

    @Test
    @EnableFlags(android.view.flags.Flags.FLAG_SCROLL_FEEDBACK_API)
    public void performHapticFeedback_doesNotVibrateWhenVibratorInfoNotReady() throws Exception {
        denyPermission(android.Manifest.permission.VIBRATE);
        mHapticFeedbackVibrationMap.put(
                HapticFeedbackConstants.KEYBOARD_TAP,
                VibrationEffect.createPredefined(EFFECT_CLICK));
        mHapticFeedbackVibrationMapSourceRotary.put(
                HapticFeedbackConstants.KEYBOARD_TAP,
                VibrationEffect.createPredefined(EFFECT_THUD));
        mHapticFeedbackVibrationMapSourceTouchScreen.put(
                HapticFeedbackConstants.KEYBOARD_TAP,
                VibrationEffect.createPredefined(EFFECT_TICK));
        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setLoadInfoToFail();
        vibratorHelper.setSupportedEffects(EFFECT_CLICK, EFFECT_TICK, EFFECT_THUD);
        VibratorManagerService service = createService();

        // performHapticFeedback.
        performHapticFeedbackAndWaitUntilFinished(
                service, HapticFeedbackConstants.KEYBOARD_TAP, /* always= */ true);
        // performHapticFeedbackForInputDevice.
        performHapticFeedbackForInputDeviceAndWaitUntilFinished(
                service, HapticFeedbackConstants.KEYBOARD_TAP, /* inputDeviceId= */ 0,
                InputDevice.SOURCE_ROTARY_ENCODER, /* always= */ true);
        performHapticFeedbackForInputDeviceAndWaitUntilFinished(
                service, HapticFeedbackConstants.KEYBOARD_TAP, /* inputDeviceId= */ 0,
                InputDevice.SOURCE_TOUCHSCREEN, /* always= */ true);

        assertTrue(vibratorHelper.getEffectSegments().isEmpty());
    }

    @Test
    @EnableFlags(android.view.flags.Flags.FLAG_SCROLL_FEEDBACK_API)
    public void performHapticFeedback_doesNotVibrateForInvalidConstant() throws Exception {
        denyPermission(android.Manifest.permission.VIBRATE);
        mHalHelper.setVibratorIds(new int[]{1});
        VibratorManagerService service = createSystemReadyService();

        // These are bad haptic feedback IDs, so expect no vibration played.
        // Test performHapticFeedback
        performHapticFeedbackAndWaitUntilFinished(service, /* constant= */ -1, /* always= */ false);
        performHapticFeedbackAndWaitUntilFinished(
                service, HapticFeedbackConstants.NO_HAPTICS, /* always= */ true);
        // Test performHapticFeedbackForInputDevice
        performHapticFeedbackForInputDeviceAndWaitUntilFinished(
                service, /* constant= */ -1, /* inputDeviceId= */ 0,
                InputDevice.SOURCE_ROTARY_ENCODER, /* always= */ true);
        performHapticFeedbackForInputDeviceAndWaitUntilFinished(
                service, /* constant= */ -1, /* inputDeviceId= */ 0,
                InputDevice.SOURCE_TOUCHSCREEN, /* always= */ true);

        assertTrue(mHalHelper.getVibratorHelper(1).getEffectSegments().isEmpty());
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void vibrate_vendorEffectsWithoutPermission_doesNotVibrate() throws Exception {
        // Deny permission to vibrate with vendor effects
        denyPermission(android.Manifest.permission.VIBRATE_VENDOR_EFFECTS);
        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setCapabilities(IVibrator.CAP_PERFORM_VENDOR_EFFECTS);
        vibratorHelper.setSupportedEffects(EFFECT_TICK);
        VibratorManagerService service = createSystemReadyService();

        PersistableBundle vendorData = new PersistableBundle();
        vendorData.putString("key", "value");
        VibrationEffect vendorEffect = VibrationEffect.createVendorEffect(vendorData);
        VibrationEffect tickEffect = VibrationEffect.createPredefined(EFFECT_TICK);

        vibrateAndWaitUntilFinished(service, vendorEffect, RINGTONE_ATTRS);
        vibrateAndWaitUntilFinished(service, tickEffect, RINGTONE_ATTRS);

        // No vendor effect played, but predefined TICK plays successfully.
        assertThat(vibratorHelper.getVendorEffects()).isEmpty();
        assertThat(vibratorHelper.getEffectSegments()).hasSize(1);
        assertThat(vibratorHelper.getEffectSegments().get(0))
                .isInstanceOf(PrebakedSegment.class);
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void vibrate_vendorEffectsWithPermission_successful() throws Exception {
        // Grant permission to vibrate with vendor effects
        grantPermission(android.Manifest.permission.VIBRATE_VENDOR_EFFECTS);
        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setCapabilities(IVibrator.CAP_PERFORM_VENDOR_EFFECTS);
        VibratorManagerService service = createSystemReadyService();

        PersistableBundle vendorData = new PersistableBundle();
        vendorData.putString("key", "value");
        VibrationEffect vendorEffect = VibrationEffect.createVendorEffect(vendorData);

        vibrateAndWaitUntilFinished(service, vendorEffect, RINGTONE_ATTRS);

        // Compare vendor data only, ignore scale applied by device settings in this test.
        assertThat(vibratorHelper.getVendorEffects()).hasSize(1);
        assertThat(vibratorHelper.getVendorEffects().get(0).getVendorData().keySet())
                .containsExactly("key");
        assertThat(vibratorHelper.getVendorEffects().get(0).getVendorData().getString("key"))
                .isEqualTo("value");
    }

    @Test
    public void vibrate_withIntensitySettings_appliesSettingsToScaleVibrations() throws Exception {
        int defaultNotificationIntensity =
                mVibrator.getDefaultVibrationIntensity(VibrationAttributes.USAGE_NOTIFICATION);
        // This will scale up notification vibrations.
        setUserSetting(Settings.System.NOTIFICATION_VIBRATION_INTENSITY,
                defaultNotificationIntensity < Vibrator.VIBRATION_INTENSITY_HIGH
                        ? defaultNotificationIntensity + 1
                        : defaultNotificationIntensity);

        int defaultTouchIntensity =
                mVibrator.getDefaultVibrationIntensity(VibrationAttributes.USAGE_TOUCH);
        // This will scale down touch vibrations.
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY,
                defaultTouchIntensity > Vibrator.VIBRATION_INTENSITY_LOW
                        ? defaultTouchIntensity - 1
                        : defaultTouchIntensity);

        setUserSetting(Settings.System.ALARM_VIBRATION_INTENSITY,
                mVibrator.getDefaultVibrationIntensity(VibrationAttributes.USAGE_ALARM));
        setUserSetting(Settings.System.MEDIA_VIBRATION_INTENSITY,
                Vibrator.VIBRATION_INTENSITY_HIGH);
        setUserSetting(Settings.System.RING_VIBRATION_INTENSITY, Vibrator.VIBRATION_INTENSITY_OFF);

        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL,
                IVibrator.CAP_COMPOSE_EFFECTS);
        vibratorHelper.setSupportedPrimitives(PRIMITIVE_CLICK, PRIMITIVE_TICK);
        VibratorManagerService service = createSystemReadyService();

        vibrateAndWaitUntilFinished(service, VibrationEffect.startComposition()
                .addPrimitive(PRIMITIVE_TICK, 0.5f)
                .compose(), HAPTIC_FEEDBACK_ATTRS);

        vibrateAndWaitUntilFinished(service, CombinedVibration.startParallel()
                .addVibrator(1, VibrationEffect.createOneShot(100, 125))
                .combine(), NOTIFICATION_ATTRS);

        vibrateAndWaitUntilFinished(service, VibrationEffect.startComposition()
                .addPrimitive(PRIMITIVE_CLICK, 1f)
                .compose(), ALARM_ATTRS);

        // Ring vibrations have intensity OFF and are not played.
        vibrateAndWaitUntilFinished(service, VibrationEffect.createOneShot(100, 125),
                RINGTONE_ATTRS);

        // Only 3 effects played successfully.
        assertEquals(3, vibratorHelper.getEffectSegments().size());

        // Haptic feedback vibrations will be scaled with SCALE_LOW or none if default is low.
        assertEquals(defaultTouchIntensity > Vibrator.VIBRATION_INTENSITY_LOW,
                0.5 > ((PrimitiveSegment) vibratorHelper.getEffectSegments().get(0)).getScale());

        // Notification vibrations will be scaled with SCALE_HIGH or none if default is high.
        assertEquals(defaultNotificationIntensity < Vibrator.VIBRATION_INTENSITY_HIGH,
                0.6 < vibratorHelper.getAmplitudes().get(0));

        // Alarm vibration will be scaled with SCALE_NONE.
        assertEquals(1f,
                ((PrimitiveSegment) vibratorHelper.getEffectSegments().get(2)).getScale(), 1e-5);
    }

    @Test
    public void vibrate_withAdaptiveHaptics_appliesCorrectAdaptiveScales() throws Exception {
        // Keep user settings the same as device default so only adaptive scale is applied.
        setUserSetting(Settings.System.ALARM_VIBRATION_INTENSITY,
                mVibrator.getDefaultVibrationIntensity(VibrationAttributes.USAGE_ALARM));
        setUserSetting(Settings.System.NOTIFICATION_VIBRATION_INTENSITY,
                mVibrator.getDefaultVibrationIntensity(VibrationAttributes.USAGE_NOTIFICATION));
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY,
                mVibrator.getDefaultVibrationIntensity(VibrationAttributes.USAGE_TOUCH));

        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        vibratorHelper.setSupportedPrimitives(PRIMITIVE_CLICK);
        VibratorManagerService service = createSystemReadyService();

        SparseArray<Float> vibrationScales = new SparseArray<>();
        vibrationScales.put(ScaleParam.TYPE_ALARM, 0.7f);
        vibrationScales.put(ScaleParam.TYPE_NOTIFICATION, 0.4f);

        mVibratorControlService.setVibrationParams(
                VibrationParamGenerator.generateVibrationParams(vibrationScales),
                mFakeVibratorController);

        VibrationEffect effect = VibrationEffect.startComposition()
                .addPrimitive(PRIMITIVE_CLICK)
                .compose();
        vibrateAndWaitUntilFinished(service, effect, ALARM_ATTRS);
        vibrateAndWaitUntilFinished(service, effect, NOTIFICATION_ATTRS);
        vibrateAndWaitUntilFinished(service, effect, HAPTIC_FEEDBACK_ATTRS);

        List<VibrationEffectSegment> segments = vibratorHelper.getEffectSegments();
        assertEquals(3, segments.size());
        assertEquals(0.7f, ((PrimitiveSegment) segments.get(0)).getScale(), 1e-5);
        assertEquals(0.4f, ((PrimitiveSegment) segments.get(1)).getScale(), 1e-5);
        assertEquals(1f, ((PrimitiveSegment) segments.get(2)).getScale(), 1e-5);
        verify(mVibratorFrameworkStatsLoggerMock).logVibrationAdaptiveHapticScale(UID, 0.7f);
        verify(mVibratorFrameworkStatsLoggerMock).logVibrationAdaptiveHapticScale(UID, 0.4f);
        verify(mVibratorFrameworkStatsLoggerMock,
                timeout(TEST_TIMEOUT_MILLIS)).logVibrationAdaptiveHapticScale(UID, 1f);
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void vibrate_withIntensitySettingsAndAdaptiveHaptics_appliesSettingsToVendorEffects()
            throws Exception {
        // Grant permission to vibrate with vendor effects
        grantPermission(android.Manifest.permission.VIBRATE_VENDOR_EFFECTS);

        setUserSetting(Settings.System.NOTIFICATION_VIBRATION_INTENSITY,
                Vibrator.VIBRATION_INTENSITY_LOW);

        mHalHelper.setVibratorIds(new int[]{1});
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setCapabilities(IVibrator.CAP_PERFORM_VENDOR_EFFECTS);
        VibratorManagerService service = createSystemReadyService();

        SparseArray<Float> vibrationScales = new SparseArray<>();
        vibrationScales.put(ScaleParam.TYPE_NOTIFICATION, 0.4f);

        mVibratorControlService.setVibrationParams(
                VibrationParamGenerator.generateVibrationParams(vibrationScales),
                mFakeVibratorController);

        PersistableBundle vendorData = new PersistableBundle();
        vendorData.putString("key", "value");
        VibrationEffect vendorEffect = VibrationEffect.createVendorEffect(vendorData);
        vibrateAndWaitUntilFinished(service, vendorEffect, NOTIFICATION_ATTRS);

        assertThat(vibratorHelper.getVendorEffects()).hasSize(1);
        VibrationEffect.VendorEffect scaled = vibratorHelper.getVendorEffects().get(0);
        assertThat(scaled.getEffectStrength()).isEqualTo(EFFECT_STRENGTH_LIGHT);
        assertThat(scaled.getScale()).isAtMost(1); // Scale down or none if default is LOW
        assertThat(scaled.getAdaptiveScale()).isEqualTo(0.4f);
    }

    @Test
    public void vibrate_ignoreVibrationFromVirtualDevice() throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        VibratorManagerService service = createSystemReadyService();

        vibrateWithDevice(service,
                VIRTUAL_DEVICE_ID,
                CombinedVibration.startParallel()
                        .addVibrator(1, VibrationEffect.createOneShot(1000, 100))
                        .combine(),
                HAPTIC_FEEDBACK_ATTRS);

        // Haptic feedback ignored when it's from a virtual device.
        assertFalse(waitUntil(s -> s.isVibrating(1), service, /* timeout= */ 50));

        vibrateWithDevice(service,
                Context.DEVICE_ID_DEFAULT,
                CombinedVibration.startParallel()
                        .addVibrator(1, VibrationEffect.createOneShot(1000, 100))
                        .combine(),
                HAPTIC_FEEDBACK_ATTRS);
        // Haptic feedback played normally when it's from the default device.
        assertTrue(waitUntil(s -> s.isVibrating(1), service, TEST_TIMEOUT_MILLIS));
    }

    @Test
    public void vibrate_prebakedAndComposedVibrationsWithFallbacks_playsFallbackOnlyForPredefined()
            throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        mHalHelper.getVibratorHelper(1).setSupportedEffects(EFFECT_CLICK);
        mHalHelper.getVibratorHelper(1).setSupportedPrimitives(PRIMITIVE_CLICK, PRIMITIVE_TICK);

        VibratorManagerService service = createSystemReadyService();
        vibrateAndWaitUntilFinished(service,
                VibrationEffect.startComposition()
                        .addEffect(VibrationEffect.createPredefined(EFFECT_CLICK))
                        .addPrimitive(PRIMITIVE_TICK)
                        .addEffect(VibrationEffect.createPredefined(EFFECT_TICK))
                        .addPrimitive(PRIMITIVE_CLICK)
                        .compose(),
                ALARM_ATTRS);

        List<VibrationEffectSegment> segments =
                mHalHelper.getVibratorHelper(1).getEffectSegments();
        // At least one step segment played as fallback for unsupported vibration effect
        assertTrue(segments.size() > 2);
        // 0: Supported effect played
        assertTrue(segments.get(0) instanceof PrebakedSegment);
        // 1: Supported primitive played
        assertTrue(segments.get(1) instanceof PrimitiveSegment);
        // 2: One or more intermediate step segments as fallback for unsupported effect
        for (int i = 2; i < segments.size() - 1; i++) {
            assertTrue(segments.get(i) instanceof StepSegment);
        }
        // 3: Supported primitive played
        assertTrue(segments.get(segments.size() - 1) instanceof PrimitiveSegment);
    }

    @Test
    public void cancelVibrate_withoutUsageFilter_stopsVibrating() throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        VibratorManagerService service = createSystemReadyService();

        service.cancelVibrate(VibrationAttributes.USAGE_FILTER_MATCH_ALL, service);
        assertFalse(service.isVibrating(1));

        vibrate(service, VibrationEffect.createOneShot(10 * TEST_TIMEOUT_MILLIS, 100), ALARM_ATTRS);

        // VibrationThread will start this vibration async, so wait until vibration is triggered.
        assertTrue(waitUntil(s -> s.isVibrating(1), service, TEST_TIMEOUT_MILLIS));

        service.cancelVibrate(VibrationAttributes.USAGE_FILTER_MATCH_ALL, service);

        // Alarm cancelled on filter match all.
        assertTrue(waitUntil(s -> !s.isVibrating(1), service, TEST_TIMEOUT_MILLIS));
    }

    @Test
    public void cancelVibrate_withFilter_onlyCancelsVibrationWithFilteredUsage() throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        VibratorManagerService service = createSystemReadyService();

        vibrate(service, VibrationEffect.createOneShot(10 * TEST_TIMEOUT_MILLIS, 100), ALARM_ATTRS);

        // VibrationThread will start this vibration async, so wait until vibration is triggered.
        assertTrue(waitUntil(s -> s.isVibrating(1), service, TEST_TIMEOUT_MILLIS));

        // Vibration is not cancelled with a different usage.
        service.cancelVibrate(VibrationAttributes.USAGE_RINGTONE, service);
        assertFalse(waitUntil(s -> !s.isVibrating(1), service, /* timeout= */ 50));

        // Vibration is not cancelled with a different usage class used as filter.
        service.cancelVibrate(
                VibrationAttributes.USAGE_CLASS_FEEDBACK | ~VibrationAttributes.USAGE_CLASS_MASK,
                service);
        assertFalse(waitUntil(s -> !s.isVibrating(1), service, /* timeout= */ 50));

        // Vibration is cancelled with usage class as filter.
        service.cancelVibrate(
                VibrationAttributes.USAGE_CLASS_ALARM | ~VibrationAttributes.USAGE_CLASS_MASK,
                service);
        assertTrue(waitUntil(s -> !s.isVibrating(1), service, TEST_TIMEOUT_MILLIS));
    }

    @Test
    public void cancelVibrate_withoutUnknownUsage_onlyStopsIfFilteringUnknownOrAllUsages()
            throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        VibrationAttributes attrs = new VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_UNKNOWN)
                .build();
        VibratorManagerService service = createSystemReadyService();

        vibrate(service, VibrationEffect.createOneShot(10 * TEST_TIMEOUT_MILLIS, 100), attrs);

        // VibrationThread will start this vibration async, so wait until vibration is triggered.
        assertTrue(waitUntil(s -> s.isVibrating(1), service, TEST_TIMEOUT_MILLIS));

        // Do not cancel UNKNOWN vibration when filter is being applied for other usages.
        service.cancelVibrate(VibrationAttributes.USAGE_RINGTONE, service);
        assertFalse(waitUntil(s -> !s.isVibrating(1), service, /* timeout= */ 50));

        service.cancelVibrate(
                VibrationAttributes.USAGE_CLASS_ALARM | ~VibrationAttributes.USAGE_CLASS_MASK,
                service);
        assertFalse(waitUntil(s -> !s.isVibrating(1), service, /* timeout= */ 50));

        // Cancel UNKNOWN vibration when filtered for that vibration specifically.
        service.cancelVibrate(VibrationAttributes.USAGE_UNKNOWN, service);
        assertTrue(waitUntil(s -> !s.isVibrating(1), service, TEST_TIMEOUT_MILLIS));

        vibrate(service, VibrationEffect.createOneShot(10 * TEST_TIMEOUT_MILLIS, 100), attrs);

        // VibrationThread will start this vibration async, so wait until vibration is triggered.
        assertTrue(waitUntil(s -> s.isVibrating(1), service, TEST_TIMEOUT_MILLIS));

        // Cancel UNKNOWN vibration when all vibrations are being cancelled.
        service.cancelVibrate(VibrationAttributes.USAGE_FILTER_MATCH_ALL, service);
        assertTrue(waitUntil(s -> !s.isVibrating(1), service, TEST_TIMEOUT_MILLIS));
    }

    @Test
    public void cancelVibrate_externalVibration_cancelWithDifferentToken() {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        createSystemReadyService();

        IBinder vibrationBinderToken = mock(IBinder.class);
        ExternalVibration externalVibration = new ExternalVibration(UID, PACKAGE_NAME,
                AUDIO_ALARM_ATTRS,
                mock(IExternalVibrationController.class), vibrationBinderToken);
        ExternalVibrationScale scale = mExternalVibratorService.onExternalVibrationStart(
                externalVibration);

        IBinder cancelBinderToken = mock(IBinder.class);
        mService.cancelVibrate(VibrationAttributes.USAGE_FILTER_MATCH_ALL, cancelBinderToken);

        assertNotEquals(ExternalVibrationScale.ScaleLevel.SCALE_MUTE, scale.scaleLevel);
        assertEquals(Arrays.asList(false, true, false),
                mHalHelper.getVibratorHelper(1).getExternalControlStates());
    }

    @Test
    public void onExternalVibration_ignoreVibrationFromVirtualDevices() {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        createSystemReadyService();

        IBinder binderToken = mock(IBinder.class);
        ExternalVibration externalVibration = new ExternalVibration(UID, PACKAGE_NAME,
                AUDIO_ALARM_ATTRS,
                mock(IExternalVibrationController.class), binderToken);
        ExternalVibrationScale scale = mExternalVibratorService.onExternalVibrationStart(
                externalVibration);
        assertNotEquals(ExternalVibrationScale.ScaleLevel.SCALE_MUTE, scale.scaleLevel);

        when(mVirtualDeviceManagerInternalMock.isAppRunningOnAnyVirtualDevice(UID))
                .thenReturn(true);
        scale = mExternalVibratorService.onExternalVibrationStart(externalVibration);
        assertEquals(ExternalVibrationScale.ScaleLevel.SCALE_MUTE, scale.scaleLevel);
    }

    @Test
    public void onExternalVibration_setsExternalControl() throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        createSystemReadyService();

        IBinder binderToken = mock(IBinder.class);
        ExternalVibration externalVibration = new ExternalVibration(UID, PACKAGE_NAME,
                AUDIO_ALARM_ATTRS,
                mock(IExternalVibrationController.class), binderToken);
        ExternalVibrationScale scale = mExternalVibratorService.onExternalVibrationStart(
                externalVibration);
        mExternalVibratorService.onExternalVibrationStop(externalVibration);

        assertNotEquals(ExternalVibrationScale.ScaleLevel.SCALE_MUTE, scale.scaleLevel);
        assertEquals(Arrays.asList(false, true, false),
                mHalHelper.getVibratorHelper(1).getExternalControlStates());

        verify(binderToken).linkToDeath(any(), eq(0));
        verify(binderToken).unlinkToDeath(any(), eq(0));
    }

    @Test
    public void onExternalVibration_withOngoingExternalVibration_mutesPreviousVibration()
            throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        createSystemReadyService();

        IBinder firstToken = mock(IBinder.class);
        IBinder secondToken = mock(IBinder.class);
        IExternalVibrationController firstExternalVibrationController =
                mock(IExternalVibrationController.class);
        IExternalVibrationController secondExternalVibrationController =
                mock(IExternalVibrationController.class);
        ExternalVibration firstVibration = new ExternalVibration(UID, PACKAGE_NAME,
                AUDIO_ALARM_ATTRS, firstExternalVibrationController, firstToken);
        ExternalVibrationScale firstScale =
                mExternalVibratorService.onExternalVibrationStart(firstVibration);

        AudioAttributes ringtoneAudioAttrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build();
        ExternalVibration secondVibration = new ExternalVibration(UID, PACKAGE_NAME,
                ringtoneAudioAttrs, secondExternalVibrationController, secondToken);
        ExternalVibrationScale secondScale =
                mExternalVibratorService.onExternalVibrationStart(secondVibration);

        assertNotEquals(ExternalVibrationScale.ScaleLevel.SCALE_MUTE, firstScale.scaleLevel);
        assertNotEquals(ExternalVibrationScale.ScaleLevel.SCALE_MUTE, secondScale.scaleLevel);
        verify(firstExternalVibrationController).mute();
        verify(secondExternalVibrationController, never()).mute();
        // Set external control called for each vibration independently.
        assertEquals(Arrays.asList(false, true, false, true),
                mHalHelper.getVibratorHelper(1).getExternalControlStates());

        mExternalVibratorService.onExternalVibrationStop(secondVibration);
        mExternalVibratorService.onExternalVibrationStop(firstVibration);
        assertEquals(Arrays.asList(false, true, false, true, false),
                mHalHelper.getVibratorHelper(1).getExternalControlStates());

        verify(firstToken).linkToDeath(any(), eq(0));
        verify(firstToken).unlinkToDeath(any(), eq(0));

        verify(secondToken).linkToDeath(any(), eq(0));
        verify(secondToken).unlinkToDeath(any(), eq(0));
    }

    @Test
    public void onExternalVibration_withOngoingVibration_cancelsOngoingVibrationImmediately()
            throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL,
                IVibrator.CAP_AMPLITUDE_CONTROL);
        VibratorManagerService service = createSystemReadyService();

        VibrationEffect effect = VibrationEffect.createOneShot(10 * TEST_TIMEOUT_MILLIS, 100);
        HalVibration vibration = vibrate(service, effect, HAPTIC_FEEDBACK_ATTRS);

        // VibrationThread will start this vibration async, so wait until vibration is triggered.
        assertTrue(waitUntil(s -> s.isVibrating(1), service, TEST_TIMEOUT_MILLIS));

        ExternalVibration externalVibration = new ExternalVibration(UID, PACKAGE_NAME,
                AUDIO_ALARM_ATTRS, mock(IExternalVibrationController.class));
        ExternalVibrationScale scale =
                mExternalVibratorService.onExternalVibrationStart(externalVibration);
        assertNotEquals(ExternalVibrationScale.ScaleLevel.SCALE_MUTE, scale.scaleLevel);

        // Vibration is cancelled.
        vibration.waitForEnd();
        assertThat(vibration.getStatus()).isEqualTo(Status.CANCELLED_SUPERSEDED);
        assertEquals(Arrays.asList(false, true),
                mHalHelper.getVibratorHelper(1).getExternalControlStates());
    }

    @Test
    public void onExternalVibration_withOngoingHigherImportanceVibration_ignoreNewVibration()
            throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL,
                IVibrator.CAP_AMPLITUDE_CONTROL);
        VibratorManagerService service = createSystemReadyService();

        VibrationEffect effect = VibrationEffect.createOneShot(10 * TEST_TIMEOUT_MILLIS, 100);
        vibrate(service, effect, RINGTONE_ATTRS);

        // VibrationThread will start this vibration async, so wait until vibration is triggered.
        assertTrue(waitUntil(s -> s.isVibrating(1), service, TEST_TIMEOUT_MILLIS));

        ExternalVibration externalVibration = new ExternalVibration(UID, PACKAGE_NAME,
                AUDIO_ALARM_ATTRS, mock(IExternalVibrationController.class));
        ExternalVibrationScale scale =
                mExternalVibratorService.onExternalVibrationStart(externalVibration);
        // External vibration is ignored.
        assertEquals(ExternalVibrationScale.ScaleLevel.SCALE_MUTE, scale.scaleLevel);

        // Vibration is not cancelled.
        assertEquals(Arrays.asList(false),
                mHalHelper.getVibratorHelper(1).getExternalControlStates());
    }

    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    @Test
    public void onExternalVibration_withOngoingHigherImportanceVendorSession_ignoreNewVibration()
            throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        VibratorManagerService service = createSystemReadyService();
        IVibrationSessionCallback callback = mockSessionCallbacks();

        VendorVibrationSession session = startSession(service, RINGTONE_ATTRS, callback, 1);
        // Keep auto-dispatcher that can be used in the session cancellation when vibration starts.
        mTestLooper.dispatchAll();
        verify(callback).onStarted(any(IVibrationSession.class));

        ExternalVibration externalVibration = new ExternalVibration(UID, PACKAGE_NAME,
                AUDIO_ALARM_ATTRS, mock(IExternalVibrationController.class));
        ExternalVibrationScale scale =
                mExternalVibratorService.onExternalVibrationStart(externalVibration);
        // External vibration is ignored.
        assertEquals(ExternalVibrationScale.ScaleLevel.SCALE_MUTE, scale.scaleLevel);

        // Make sure all messages are processed before asserting on the session callbacks.
        stopAutoDispatcherAndDispatchAll();

        // Session still running.
        assertThat(session.getStatus()).isEqualTo(Status.RUNNING);
        verify(callback, never()).onFinishing();
        verify(callback, never()).onFinished(anyInt());
    }

    @Test
    public void onExternalVibration_withNewSameImportanceButRepeating_cancelsOngoingVibration()
            throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL,
                IVibrator.CAP_AMPLITUDE_CONTROL);
        VibratorManagerService service = createSystemReadyService();

        VibrationEffect repeatingEffect = VibrationEffect.createWaveform(
                new long[]{100, 200, 300}, new int[]{128, 255, 255}, 1);
        HalVibration repeatingVibration = vibrate(service, repeatingEffect, ALARM_ATTRS);

        // VibrationThread will start this vibration async, so wait until vibration is triggered.
        assertTrue(waitUntil(s -> s.isVibrating(1), service, TEST_TIMEOUT_MILLIS));

        ExternalVibration externalVibration = new ExternalVibration(UID, PACKAGE_NAME,
                AUDIO_ALARM_ATTRS, mock(IExternalVibrationController.class));
        ExternalVibrationScale scale =
                mExternalVibratorService.onExternalVibrationStart(externalVibration);
        assertNotEquals(ExternalVibrationScale.ScaleLevel.SCALE_MUTE, scale.scaleLevel);

        // Vibration is cancelled.
        repeatingVibration.waitForEnd();
        assertThat(repeatingVibration.getStatus()).isEqualTo(Status.CANCELLED_SUPERSEDED);
        assertEquals(Arrays.asList(false, true),
                mHalHelper.getVibratorHelper(1).getExternalControlStates());
    }

    @Test
    public void onExternalVibration_withNewSameImportanceButOngoingIsRepeating_ignoreNewVibration()
            throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL,
                IVibrator.CAP_AMPLITUDE_CONTROL);
        VibratorManagerService service = createSystemReadyService();

        VibrationEffect repeatingEffect = VibrationEffect.createWaveform(
                new long[]{10_000, 10_000}, new int[]{128, 255}, 1);
        vibrate(service, repeatingEffect, NOTIFICATION_ATTRS);

        // VibrationThread will start this vibration async, so wait until vibration is triggered.
        assertTrue(waitUntil(s -> s.isVibrating(1), service, TEST_TIMEOUT_MILLIS));

        ExternalVibration externalVibration = new ExternalVibration(UID, PACKAGE_NAME,
                AUDIO_NOTIFICATION_ATTRS, mock(IExternalVibrationController.class));
        ExternalVibrationScale scale =
                mExternalVibratorService.onExternalVibrationStart(externalVibration);
        // New vibration is ignored.
        assertEquals(ExternalVibrationScale.ScaleLevel.SCALE_MUTE, scale.scaleLevel);

        // Vibration is not cancelled.
        assertEquals(Arrays.asList(false),
                mHalHelper.getVibratorHelper(1).getExternalControlStates());
    }

    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    @Test
    public void onExternalVibration_withOngoingLowerImportanceVendorSession_cancelsOngoingSession()
            throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        VibratorManagerService service = createSystemReadyService();
        IVibrationSessionCallback callback = mockSessionCallbacks();

        VendorVibrationSession session = startSession(service, HAPTIC_FEEDBACK_ATTRS, callback, 1);
        // Keep auto-dispatcher that can be used in the session cancellation when vibration starts.
        mTestLooper.dispatchAll();
        verify(callback).onStarted(any(IVibrationSession.class));

        ExternalVibration externalVibration = new ExternalVibration(UID, PACKAGE_NAME,
                AUDIO_ALARM_ATTRS, mock(IExternalVibrationController.class));
        ExternalVibrationScale scale =
                mExternalVibratorService.onExternalVibrationStart(externalVibration);
        assertNotEquals(ExternalVibrationScale.ScaleLevel.SCALE_MUTE, scale.scaleLevel);

        // Make sure all messages are processed before asserting on the session callbacks.
        stopAutoDispatcherAndDispatchAll();

        // Session is cancelled.
        assertThat(session.getStatus()).isEqualTo(Status.CANCELLED_SUPERSEDED);
        verify(callback).onFinishing();
        verify(callback).onFinished(eq(android.os.vibrator.VendorVibrationSession.STATUS_CANCELED));
        assertEquals(Arrays.asList(false, true),
                mHalHelper.getVibratorHelper(1).getExternalControlStates());
    }

    @Test
    public void onExternalVibration_withRingtone_usesRingerModeSettings() {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        AudioAttributes audioAttrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build();
        ExternalVibration externalVibration = new ExternalVibration(UID, PACKAGE_NAME, audioAttrs,
                mock(IExternalVibrationController.class));

        setRingerMode(AudioManager.RINGER_MODE_SILENT);
        createSystemReadyService();
        ExternalVibrationScale scale =
                mExternalVibratorService.onExternalVibrationStart(externalVibration);
        assertEquals(ExternalVibrationScale.ScaleLevel.SCALE_MUTE, scale.scaleLevel);

        setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        createSystemReadyService();
        scale = mExternalVibratorService.onExternalVibrationStart(externalVibration);
        assertNotEquals(ExternalVibrationScale.ScaleLevel.SCALE_MUTE, scale.scaleLevel);

        setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
        createSystemReadyService();
        scale = mExternalVibratorService.onExternalVibrationStart(externalVibration);
        assertNotEquals(ExternalVibrationScale.ScaleLevel.SCALE_MUTE, scale.scaleLevel);
    }

    @Test
    public void onExternalVibration_withBypassMuteAudioFlag_ignoresUserSettings() {
        // Permission needed for bypassing user settings
        grantPermission(android.Manifest.permission.MODIFY_PHONE_STATE);

        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        setUserSetting(Settings.System.ALARM_VIBRATION_INTENSITY,
                Vibrator.VIBRATION_INTENSITY_OFF);
        AudioAttributes audioAttrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build();
        AudioAttributes flaggedAudioAttrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setFlags(AudioAttributes.FLAG_BYPASS_MUTE)
                .build();
        createSystemReadyService();

        ExternalVibration vib = new ExternalVibration(UID, PACKAGE_NAME, audioAttrs,
                mock(IExternalVibrationController.class));
        ExternalVibrationScale scale = mExternalVibratorService.onExternalVibrationStart(vib);
        assertEquals(ExternalVibrationScale.ScaleLevel.SCALE_MUTE, scale.scaleLevel);

        mExternalVibratorService.onExternalVibrationStop(vib);
        scale = mExternalVibratorService.onExternalVibrationStart(
                new ExternalVibration(UID, PACKAGE_NAME, flaggedAudioAttrs,
                        mock(IExternalVibrationController.class)));
        assertNotEquals(ExternalVibrationScale.ScaleLevel.SCALE_MUTE, scale.scaleLevel);
    }

    @Test
    public void onExternalVibration_withUnknownUsage_appliesMediaSettings() {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        setUserSetting(Settings.System.MEDIA_VIBRATION_INTENSITY,
                Vibrator.VIBRATION_INTENSITY_OFF);
        AudioAttributes flaggedAudioAttrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_UNKNOWN)
                .build();
        createSystemReadyService();

        ExternalVibrationScale scale =
                mExternalVibratorService.onExternalVibrationStart(
                        new ExternalVibration(/* uid= */ 123, PACKAGE_NAME, flaggedAudioAttrs,
                                mock(IExternalVibrationController.class)));
        assertEquals(ExternalVibrationScale.ScaleLevel.SCALE_MUTE, scale.scaleLevel);
    }

    @Test
    public void onExternalVibration_withAdaptiveHaptics_returnsCorrectAdaptiveScales() {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL,
                IVibrator.CAP_AMPLITUDE_CONTROL);
        createSystemReadyService();

        SparseArray<Float> vibrationScales = new SparseArray<>();
        vibrationScales.put(ScaleParam.TYPE_ALARM, 0.7f);
        vibrationScales.put(ScaleParam.TYPE_NOTIFICATION, 0.4f);

        mVibratorControlService.setVibrationParams(
                VibrationParamGenerator.generateVibrationParams(vibrationScales),
                mFakeVibratorController);
        ExternalVibration externalVibration = new ExternalVibration(UID, PACKAGE_NAME,
                AUDIO_ALARM_ATTRS, mock(IExternalVibrationController.class));
        ExternalVibrationScale scale =
                mExternalVibratorService.onExternalVibrationStart(externalVibration);
        mExternalVibratorService.onExternalVibrationStop(externalVibration);

        assertEquals(scale.adaptiveHapticsScale, 0.7f, 0);
        verify(mVibratorFrameworkStatsLoggerMock).logVibrationAdaptiveHapticScale(UID, 0.7f);

        externalVibration = new ExternalVibration(UID, PACKAGE_NAME,
                AUDIO_NOTIFICATION_ATTRS, mock(IExternalVibrationController.class));
        scale = mExternalVibratorService.onExternalVibrationStart(externalVibration);
        mExternalVibratorService.onExternalVibrationStop(externalVibration);

        assertEquals(scale.adaptiveHapticsScale, 0.4f, 0);
        verify(mVibratorFrameworkStatsLoggerMock).logVibrationAdaptiveHapticScale(UID, 0.4f);

        AudioAttributes ringtoneAudioAttrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build();
        externalVibration = new ExternalVibration(UID, PACKAGE_NAME,
                ringtoneAudioAttrs, mock(IExternalVibrationController.class));
        scale = mExternalVibratorService.onExternalVibrationStart(externalVibration);
        mExternalVibratorService.onExternalVibrationStop(externalVibration);

        assertEquals(scale.adaptiveHapticsScale, 1f, 0);
        verify(mVibratorFrameworkStatsLoggerMock).logVibrationAdaptiveHapticScale(UID, 1f);
    }

    @Test
    public void onExternalVibration_thenDeniedAppOps_cancelVibration() throws Throwable {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        VibratorManagerService service = createSystemReadyService();

        IExternalVibrationController externalVibrationControllerMock =
                mock(IExternalVibrationController.class);
        ExternalVibration externalVibration = new ExternalVibration(UID, PACKAGE_NAME,
                AUDIO_ALARM_ATTRS, externalVibrationControllerMock, mock(IBinder.class));
        ExternalVibrationScale scale = mExternalVibratorService.onExternalVibrationStart(
                externalVibration);

        assertThat(scale.scaleLevel).isNotEqualTo(ExternalVibrationScale.ScaleLevel.SCALE_MUTE);

        when(mAppOpsManagerMock.checkAudioOpNoThrow(eq(AppOpsManager.OP_VIBRATE),
                eq(AudioAttributes.USAGE_ALARM), anyInt(), anyString()))
                .thenReturn(AppOpsManager.MODE_IGNORED);
        service.mAppOpsChangeListener.onOpChanged(AppOpsManager.OP_VIBRATE, null);

        verify(externalVibrationControllerMock).mute();
    }

    @Test
    public void onExternalVibration_thenPowerModeChanges_cancelVibration() throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        createSystemReadyService();

        IExternalVibrationController externalVibrationControllerMock =
                mock(IExternalVibrationController.class);
        ExternalVibration externalVibration = new ExternalVibration(UID, PACKAGE_NAME,
                AUDIO_HAPTIC_FEEDBACK_ATTRS, externalVibrationControllerMock, mock(IBinder.class));
        ExternalVibrationScale scale = mExternalVibratorService.onExternalVibrationStart(
                externalVibration);

        assertThat(scale.scaleLevel).isNotEqualTo(ExternalVibrationScale.ScaleLevel.SCALE_MUTE);

        mRegisteredPowerModeListener.onLowPowerModeChanged(LOW_POWER_STATE);

        verify(externalVibrationControllerMock).mute();
    }

    @Test
    public void onExternalVibration_thenSettingsChange_cancelVibration() throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        VibratorManagerService service = createSystemReadyService();

        IExternalVibrationController externalVibrationControllerMock =
                mock(IExternalVibrationController.class);
        ExternalVibration externalVibration = new ExternalVibration(UID, PACKAGE_NAME,
                AUDIO_ALARM_ATTRS, externalVibrationControllerMock, mock(IBinder.class));
        ExternalVibrationScale scale = mExternalVibratorService.onExternalVibrationStart(
                externalVibration);

        assertThat(scale.scaleLevel).isNotEqualTo(ExternalVibrationScale.ScaleLevel.SCALE_MUTE);

        setUserSetting(Settings.System.ALARM_VIBRATION_INTENSITY, Vibrator.VIBRATION_INTENSITY_OFF);
        service.mVibrationSettings.mSettingObserver.onChange(false);
        service.updateServiceState();

        verify(externalVibrationControllerMock).mute();
    }

    @Test
    public void onExternalVibration_thenScreenTurnsOff_cancelVibration() throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        VibratorManagerService service = createSystemReadyService();

        IExternalVibrationController externalVibrationControllerMock =
                mock(IExternalVibrationController.class);
        ExternalVibration externalVibration = new ExternalVibration(UID, PACKAGE_NAME,
                AUDIO_ALARM_ATTRS, externalVibrationControllerMock, mock(IBinder.class));
        ExternalVibrationScale scale = mExternalVibratorService.onExternalVibrationStart(
                externalVibration);

        assertThat(scale.scaleLevel).isNotEqualTo(ExternalVibrationScale.ScaleLevel.SCALE_MUTE);

        service.mIntentReceiver.onReceive(mContextSpy, new Intent(Intent.ACTION_SCREEN_OFF));

        verify(externalVibrationControllerMock).mute();
    }

    @Test
    public void onExternalVibration_thenFgUserRequestsMute_cancelVibration() throws Exception {
        assumeTrue(UserManagerInternal.shouldShowNotificationForBackgroundUserSounds());
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        VibratorManagerService service = createSystemReadyService();

        IExternalVibrationController externalVibrationControllerMock =
                mock(IExternalVibrationController.class);
        ExternalVibration externalVibration = new ExternalVibration(UID, PACKAGE_NAME,
                AUDIO_ALARM_ATTRS, externalVibrationControllerMock, mock(IBinder.class));
        ExternalVibrationScale scale = mExternalVibratorService.onExternalVibrationStart(
                externalVibration);

        assertThat(scale.scaleLevel).isNotEqualTo(ExternalVibrationScale.ScaleLevel.SCALE_MUTE);

        service.mIntentReceiver.onReceive(mContextSpy, new Intent(
                BackgroundUserSoundNotifier.ACTION_MUTE_SOUND));

        verify(externalVibrationControllerMock).mute();
    }

    @Test
    @DisableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void startVibrationSession_withoutFeatureFlag_throwsException() throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        int vibratorId = 1;
        mHalHelper.setVibratorIds(new int[]{vibratorId});
        VibratorManagerService service = createSystemReadyService();

        IVibrationSessionCallback callback = mockSessionCallbacks();
        assertThrows("Expected starting session without feature flag to fail!",
                UnsupportedOperationException.class,
                () -> startSession(service, RINGTONE_ATTRS, callback, vibratorId));

        // Make sure all messages are processed before asserting on the session callbacks.
        stopAutoDispatcherAndDispatchAll();

        assertThat(mHalHelper.getStartSessionCount()).isEqualTo(0);
        verify(mVibratorFrameworkStatsLoggerMock, never())
                .logVibrationVendorSessionStarted(anyInt());
        verify(mVibratorFrameworkStatsLoggerMock, never())
                .logVibrationVendorSessionVibrations(anyInt(), anyInt());
        verify(mVibratorFrameworkStatsLoggerMock, never())
                .logVibrationVendorSessionInterrupted(anyInt());
        verify(callback, never()).onStarted(any(IVibrationSession.class));
        verify(callback, never()).onFinishing();
        verify(callback, never()).onFinished(anyInt());
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void startVibrationSession_withoutCapability_doesNotStart() throws Exception {
        int vibratorId = 1;
        mHalHelper.setVibratorIds(new int[]{vibratorId});
        VibratorManagerService service = createSystemReadyService();

        IVibrationSessionCallback callback = mockSessionCallbacks();
        VendorVibrationSession session = startSession(service, RINGTONE_ATTRS,
                callback, vibratorId);

        // Make sure all messages are processed before asserting on the session callbacks.
        stopAutoDispatcherAndDispatchAll();

        assertThat(session.getStatus()).isEqualTo(Status.IGNORED_UNSUPPORTED);
        assertThat(mHalHelper.getStartSessionCount()).isEqualTo(0);
        verify(mVibratorFrameworkStatsLoggerMock, never())
                .logVibrationVendorSessionStarted(anyInt());
        verify(mVibratorFrameworkStatsLoggerMock, never())
                .logVibrationVendorSessionVibrations(anyInt(), anyInt());
        verify(mVibratorFrameworkStatsLoggerMock, never())
                .logVibrationVendorSessionInterrupted(anyInt());
        verify(callback, never()).onFinishing();
        verify(callback)
                .onFinished(eq(android.os.vibrator.VendorVibrationSession.STATUS_UNSUPPORTED));
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void startVibrationSession_withoutCallback_doesNotStart() {
        mHalHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        int vibratorId = 1;
        mHalHelper.setVibratorIds(new int[]{vibratorId});
        VibratorManagerService service = createSystemReadyService();

        VendorVibrationSession session = startSession(service, RINGTONE_ATTRS,
                /* callback= */ null, vibratorId);

        // Make sure all messages are processed before asserting on the session callbacks.
        stopAutoDispatcherAndDispatchAll();

        assertThat(session).isNull();
        assertThat(mHalHelper.getStartSessionCount()).isEqualTo(0);
        verify(mVibratorFrameworkStatsLoggerMock, never())
                .logVibrationVendorSessionStarted(anyInt());
        verify(mVibratorFrameworkStatsLoggerMock, never())
                .logVibrationVendorSessionVibrations(anyInt(), anyInt());
        verify(mVibratorFrameworkStatsLoggerMock, never())
                .logVibrationVendorSessionInterrupted(anyInt());
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void startVibrationSession_withoutVibratorIds_doesNotStart() throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHalHelper.setVibratorIds(new int[]{1});
        VibratorManagerService service = createSystemReadyService();

        int[] nullIds = null;
        IVibrationSessionCallback callback = mockSessionCallbacks();
        VendorVibrationSession session = startSession(service, RINGTONE_ATTRS, callback, nullIds);
        assertThat(session.getStatus()).isEqualTo(Status.IGNORED_UNSUPPORTED);

        int[] emptyIds = {};
        session = startSession(service, RINGTONE_ATTRS, callback, emptyIds);

        // Make sure all messages are processed before asserting on the session callbacks.
        stopAutoDispatcherAndDispatchAll();

        assertThat(session.getStatus()).isEqualTo(Status.IGNORED_UNSUPPORTED);
        assertThat(mHalHelper.getStartSessionCount()).isEqualTo(0);
        verify(mVibratorFrameworkStatsLoggerMock, never())
                .logVibrationVendorSessionStarted(anyInt());
        verify(mVibratorFrameworkStatsLoggerMock, never())
                .logVibrationVendorSessionVibrations(anyInt(), anyInt());
        verify(mVibratorFrameworkStatsLoggerMock, never())
                .logVibrationVendorSessionInterrupted(anyInt());
        verify(callback, never()).onFinishing();
        verify(callback, times(2))
                .onFinished(eq(android.os.vibrator.VendorVibrationSession.STATUS_UNSUPPORTED));
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void startVibrationSession_badVibratorId_failsToStart() throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHalHelper.setVibratorIds(new int[]{1, 2});
        VibratorManagerService service = createSystemReadyService();
        IVibrationSessionCallback callback = mockSessionCallbacks();

        VendorVibrationSession session = startSession(service, RINGTONE_ATTRS, callback, 1, 3);

        // Make sure all messages are processed before asserting on the session callbacks.
        stopAutoDispatcherAndDispatchAll();

        assertThat(session.getStatus()).isEqualTo(Status.IGNORED_UNSUPPORTED);
        assertThat(mHalHelper.getStartSessionCount()).isEqualTo(0);
        verify(mVibratorFrameworkStatsLoggerMock, never())
                .logVibrationVendorSessionStarted(anyInt());
        verify(mVibratorFrameworkStatsLoggerMock, never())
                .logVibrationVendorSessionVibrations(anyInt(), anyInt());
        verify(mVibratorFrameworkStatsLoggerMock, never())
                .logVibrationVendorSessionInterrupted(anyInt());
        verify(callback, never()).onStarted(any(IVibrationSession.class));
        verify(callback, never()).onFinishing();
        verify(callback)
                .onFinished(eq(android.os.vibrator.VendorVibrationSession.STATUS_UNSUPPORTED));
    }

    @Test
    @EnableFlags({Flags.FLAG_REMOVE_HIDL_SUPPORT, Flags.FLAG_VENDOR_VIBRATION_EFFECTS})
    public void startVibrationSession_withLegacyHal_doesNotStart() throws Exception {
        // Capabilities and IDs ignored by legacy HAL service, as it's backed by single IVibrator.
        mHalHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHalHelper.setVibratorIds(new int[] { 1, 2 });
        mUseLegacyHalManager = true;
        VibratorManagerService service = createSystemReadyService();

        IVibrationSessionCallback callback = mockSessionCallbacks();
        VendorVibrationSession session = startSession(service, RINGTONE_ATTRS,
                callback, VintfHalVibratorManager.DEFAULT_VIBRATOR_ID);

        // Make sure all messages are processed before asserting on the session callbacks.
        stopAutoDispatcherAndDispatchAll();

        assertThat(session.getStatus()).isEqualTo(Status.IGNORED_UNSUPPORTED);
        assertThat(mHalHelper.getStartSessionCount()).isEqualTo(0);
        verify(mVibratorFrameworkStatsLoggerMock, never())
                .logVibrationVendorSessionStarted(anyInt());
        verify(mVibratorFrameworkStatsLoggerMock, never())
                .logVibrationVendorSessionVibrations(anyInt(), anyInt());
        verify(mVibratorFrameworkStatsLoggerMock, never())
                .logVibrationVendorSessionInterrupted(anyInt());
        verify(callback, never()).onFinishing();
        verify(callback)
                .onFinished(eq(android.os.vibrator.VendorVibrationSession.STATUS_UNSUPPORTED));
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void startVibrationSession_thenFinish_returnsSuccessAfterCallback() throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHalHelper.setVibratorIds(new int[]{1, 2});
        int sessionFinishDelayMs = 200;
        mHalHelper.setSessionEndDelayMs(sessionFinishDelayMs);
        VibratorManagerService service = createSystemReadyService();
        IVibrationSessionCallback callback = mockSessionCallbacks();

        VendorVibrationSession session = startSession(service, RINGTONE_ATTRS, callback, 1, 2);

        // Make sure all messages are processed before asserting on the session callbacks.
        stopAutoDispatcherAndDispatchAll();

        assertThat(mHalHelper.getStartSessionCount()).isEqualTo(1);
        ArgumentCaptor<IVibrationSession> captor = ArgumentCaptor.forClass(IVibrationSession.class);
        verify(callback).onStarted(captor.capture());

        captor.getValue().finishSession();
        mTestLooper.dispatchAll();

        // Session not ended until HAL callback.
        assertThat(session.getStatus()).isEqualTo(Status.RUNNING);

        // Dispatch HAL callbacks.
        mTestLooper.moveTimeForward(sessionFinishDelayMs);
        mTestLooper.dispatchAll();

        assertThat(session.getStatus()).isEqualTo(Status.FINISHED);
        verify(callback).onFinishing();
        verify(callback).onFinished(eq(android.os.vibrator.VendorVibrationSession.STATUS_SUCCESS));

        assertThat(mHalHelper.getEndSessionCount()).isEqualTo(1);
        assertThat(mHalHelper.getAbortSessionCount()).isEqualTo(0);
        verify(mVibratorFrameworkStatsLoggerMock).logVibrationVendorSessionStarted(eq(UID));
        verify(mVibratorFrameworkStatsLoggerMock)
                .logVibrationVendorSessionVibrations(eq(UID), eq(0));
        verify(mVibratorFrameworkStatsLoggerMock, never())
                .logVibrationVendorSessionInterrupted(anyInt());
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void startVibrationSession_thenSendCancelSignal_cancelsSession() throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHalHelper.setVibratorIds(new int[]{1, 2});
        int sessionFinishDelayMs = 200;
        mHalHelper.setSessionEndDelayMs(sessionFinishDelayMs);
        VibratorManagerService service = createSystemReadyService();
        IVibrationSessionCallback callback = mockSessionCallbacks();

        VendorVibrationSession session = startSession(service, RINGTONE_ATTRS, callback, 1, 2);

        // Make sure all messages are processed before asserting on the session callbacks.
        stopAutoDispatcherAndDispatchAll();

        assertThat(mHalHelper.getStartSessionCount()).isEqualTo(1);
        ArgumentCaptor<IVibrationSession> captor = ArgumentCaptor.forClass(IVibrationSession.class);
        verify(callback).onStarted(captor.capture());

        session.getCancellationSignal().cancel();
        mTestLooper.dispatchAll();

        assertThat(session.getStatus()).isEqualTo(Status.CANCELLED_BY_USER);
        verify(callback).onFinishing();
        verify(callback).onFinished(eq(android.os.vibrator.VendorVibrationSession.STATUS_CANCELED));

        assertThat(mHalHelper.getEndSessionCount()).isEqualTo(0);
        assertThat(mHalHelper.getAbortSessionCount()).isEqualTo(1);
        verify(mVibratorFrameworkStatsLoggerMock).logVibrationVendorSessionStarted(eq(UID));
        verify(mVibratorFrameworkStatsLoggerMock)
                .logVibrationVendorSessionVibrations(eq(UID), eq(0));
        verify(mVibratorFrameworkStatsLoggerMock, never())
                .logVibrationVendorSessionInterrupted(anyInt());
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void startVibrationSession_thenCancel_returnsCancelStatus() throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHalHelper.setVibratorIds(new int[]{1, 2});
        mHalHelper.setSessionEndDelayMs(TEST_TIMEOUT_MILLIS);
        VibratorManagerService service = createSystemReadyService();
        IVibrationSessionCallback callback = mockSessionCallbacks();

        VendorVibrationSession session = startSession(service, RINGTONE_ATTRS, callback, 1, 2);

        // Make sure all messages are processed before asserting on the session callbacks.
        stopAutoDispatcherAndDispatchAll();

        assertThat(mHalHelper.getStartSessionCount()).isEqualTo(1);
        ArgumentCaptor<IVibrationSession> captor = ArgumentCaptor.forClass(IVibrationSession.class);
        verify(callback).onStarted(captor.capture());

        captor.getValue().cancelSession();
        mTestLooper.dispatchAll();

        assertThat(session.getStatus()).isEqualTo(Status.CANCELLED_BY_USER);
        verify(callback).onFinishing();
        verify(callback).onFinished(eq(android.os.vibrator.VendorVibrationSession.STATUS_CANCELED));
        assertThat(mHalHelper.getEndSessionCount()).isEqualTo(0);
        assertThat(mHalHelper.getAbortSessionCount()).isEqualTo(1);
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void startVibrationSession_finishThenCancel_returnsRightAwayWithFinishedStatus()
            throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHalHelper.setVibratorIds(new int[]{1, 2});
        mHalHelper.setSessionEndDelayMs(TEST_TIMEOUT_MILLIS);
        VibratorManagerService service = createSystemReadyService();
        IVibrationSessionCallback callback = mockSessionCallbacks();

        VendorVibrationSession session = startSession(service, RINGTONE_ATTRS, callback, 1, 2);

        // Make sure all messages are processed before asserting on the session callbacks.
        stopAutoDispatcherAndDispatchAll();

        assertThat(mHalHelper.getStartSessionCount()).isEqualTo(1);
        ArgumentCaptor<IVibrationSession> captor = ArgumentCaptor.forClass(IVibrationSession.class);
        verify(callback).onStarted(captor.capture());

        captor.getValue().finishSession();
        mTestLooper.dispatchAll();
        assertThat(session.getStatus()).isEqualTo(Status.RUNNING);

        captor.getValue().cancelSession();
        mTestLooper.dispatchAll();

        assertThat(session.getStatus()).isEqualTo(Status.FINISHED);
        verify(callback).onFinishing();
        verify(callback).onFinished(eq(android.os.vibrator.VendorVibrationSession.STATUS_SUCCESS));

        assertThat(mHalHelper.getEndSessionCount()).isEqualTo(1);
        assertThat(mHalHelper.getAbortSessionCount()).isEqualTo(1);
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void startVibrationSession_thenHalCancels_returnsCancelStatus()
            throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHalHelper.setVibratorIds(new int[]{1, 2});

        VibratorManagerService service = createSystemReadyService();
        IBinder token = mock(IBinder.class);
        IVibrationSessionCallback callback = mock(IVibrationSessionCallback.class);
        doReturn(token).when(callback).asBinder();
        VendorVibrationSession session = startSession(service, RINGTONE_ATTRS, callback, 1, 2);

        // Make sure all messages are processed before asserting on the session callbacks.
        stopAutoDispatcherAndDispatchAll();

        assertThat(mHalHelper.getStartSessionCount()).isEqualTo(1);
        verify(callback).onStarted(any(IVibrationSession.class));

        // Mock HAL ending session unexpectedly.
        mHalHelper.endLastSessionAbruptly();
        mTestLooper.dispatchAll();

        assertThat(session.getStatus()).isEqualTo(Status.CANCELLED_BY_UNKNOWN_REASON);
        verify(callback).onFinishing();
        verify(callback).onFinished(eq(android.os.vibrator.VendorVibrationSession.STATUS_CANCELED));

        assertThat(mHalHelper.getEndSessionCount()).isEqualTo(0);
        assertThat(mHalHelper.getAbortSessionCount()).isEqualTo(0);
        verify(mVibratorFrameworkStatsLoggerMock).logVibrationVendorSessionStarted(eq(UID));
        verify(mVibratorFrameworkStatsLoggerMock)
                .logVibrationVendorSessionVibrations(eq(UID), eq(0));
        verify(mVibratorFrameworkStatsLoggerMock).logVibrationVendorSessionInterrupted(anyInt());
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void startVibrationSession_withPowerMode_usesPowerModeState() throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.setSessionEndDelayMs(TEST_TIMEOUT_MILLIS);
        VibratorManagerService service = createSystemReadyService();
        IVibrationSessionCallback callback = mockSessionCallbacks();

        mRegisteredPowerModeListener.onLowPowerModeChanged(LOW_POWER_STATE);
        // Will be ignored for power mode.
        startSession(service, HAPTIC_FEEDBACK_ATTRS, callback, 1);
        startSession(service, RINGTONE_ATTRS, callback, 1);

        // Make sure all messages are processed before asserting on the session callbacks.
        stopAutoDispatcherAndDispatchAll();

        ArgumentCaptor<IVibrationSession> captor = ArgumentCaptor.forClass(IVibrationSession.class);
        verify(callback).onStarted(captor.capture());
        captor.getValue().cancelSession();
        mTestLooper.dispatchAll();

        assertThat(mHalHelper.getStartSessionCount()).isEqualTo(1);

        mRegisteredPowerModeListener.onLowPowerModeChanged(NORMAL_POWER_STATE);
        startSession(service, HAPTIC_FEEDBACK_ATTRS, callback, 1);
        mTestLooper.dispatchAll();

        assertThat(mHalHelper.getStartSessionCount()).isEqualTo(2);
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void startVibrationSession_withOngoingHigherImportanceVibration_ignoresSession()
            throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.setSessionEndDelayMs(TEST_TIMEOUT_MILLIS);
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        VibratorManagerService service = createSystemReadyService();
        IVibrationSessionCallback callback = mockSessionCallbacks();

        VibrationEffect effect = VibrationEffect.createWaveform(
                new long[]{10, 10_000}, new int[]{128, 255}, -1);
        vibrate(service, effect, ALARM_ATTRS);

        // VibrationThread will start this vibration async.
        // Wait until second step started to ensure the noteVibratorOn was triggered.
        assertTrue(waitUntil(s -> vibratorHelper.getAmplitudes().size() == 2,
                service, TEST_TIMEOUT_MILLIS));

        VendorVibrationSession session = startSession(service, HAPTIC_FEEDBACK_ATTRS, callback, 1);

        // Make sure all messages are processed before asserting on the session callbacks.
        stopAutoDispatcherAndDispatchAll();

        assertThat(mHalHelper.getStartSessionCount()).isEqualTo(0);
        assertThat(session.getStatus()).isEqualTo(Status.IGNORED_FOR_HIGHER_IMPORTANCE);
        verify(callback, never()).onFinishing();
        verify(callback).onFinished(eq(android.os.vibrator.VendorVibrationSession.STATUS_IGNORED));
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void startVibrationSession_withOngoingLowerImportanceVibration_cancelsOngoing()
            throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.setSessionEndDelayMs(TEST_TIMEOUT_MILLIS);
        HalVibratorHelper vibratorHelper = mHalHelper.getVibratorHelper(1);
        vibratorHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        vibratorHelper.setSupportedEffects(EFFECT_CLICK);
        VibratorManagerService service = createSystemReadyService();
        IVibrationSessionCallback callback = mockSessionCallbacks();

        VibrationEffect effect = VibrationEffect.createWaveform(
                new long[]{10, 10_000}, new int[]{128, 255}, -1);
        HalVibration vibration = vibrate(service, effect, HAPTIC_FEEDBACK_ATTRS);

        // VibrationThread will start this vibration async.
        // Wait until second step started to ensure the noteVibratorOn was triggered.
        assertTrue(waitUntil(s -> vibratorHelper.getAmplitudes().size() == 2, service,
                TEST_TIMEOUT_MILLIS));

        VendorVibrationSession session = startSession(service, RINGTONE_ATTRS, callback, 1);
        vibration.waitForEnd();
        assertTrue(waitUntil(s -> session.isStarted(), service, TEST_TIMEOUT_MILLIS));

        // Make sure all messages are processed before asserting on the session callbacks.
        stopAutoDispatcherAndDispatchAll();

        assertThat(vibration.getStatus()).isEqualTo(Status.CANCELLED_SUPERSEDED);
        assertThat(session.getStatus()).isEqualTo(Status.RUNNING);
        assertThat(mHalHelper.getStartSessionCount()).isEqualTo(1);
        verify(callback).onStarted(any(IVibrationSession.class));
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void startVibrationSession_withOngoingLowerImportanceExternalVibration_cancelsOngoing()
            throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.setSessionEndDelayMs(TEST_TIMEOUT_MILLIS);
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        mHalHelper.getVibratorHelper(1).setSupportedEffects(EFFECT_CLICK);
        setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        VibratorManagerService service = createSystemReadyService();
        IVibrationSessionCallback callback = mockSessionCallbacks();

        IBinder firstToken = mock(IBinder.class);
        IExternalVibrationController externalVibrationController =
                mock(IExternalVibrationController.class);
        ExternalVibration externalVibration = new ExternalVibration(UID, PACKAGE_NAME,
                AUDIO_ALARM_ATTRS, externalVibrationController, firstToken);
        ExternalVibrationScale scale =
                mExternalVibratorService.onExternalVibrationStart(externalVibration);

        startSession(service, RINGTONE_ATTRS, callback, 1);

        // Make sure all messages are processed before asserting on the session callbacks.
        stopAutoDispatcherAndDispatchAll();

        assertNotEquals(ExternalVibrationScale.ScaleLevel.SCALE_MUTE, scale.scaleLevel);
        // The external vibration should have been cancelled
        verify(externalVibrationController).mute();
        assertEquals(Arrays.asList(false, true, false),
                mHalHelper.getVibratorHelper(1).getExternalControlStates());
        assertThat(mHalHelper.getStartSessionCount()).isEqualTo(1);
        verify(callback).onStarted(any(IVibrationSession.class));
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void vibrateInSession_afterCancel_vibrationIgnored() throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHalHelper.setVibratorIds(new int[]{1, 2});
        int sessionFinishDelayMs = 200;
        mHalHelper.setSessionEndDelayMs(sessionFinishDelayMs);
        HalVibratorHelper vibratorHelper1 = mHalHelper.getVibratorHelper(1);
        vibratorHelper1.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        VibratorManagerService service = createSystemReadyService();
        IVibrationSessionCallback callback = mockSessionCallbacks();
        VendorVibrationSession session = startSession(service, RINGTONE_ATTRS, callback, 1, 2);

        // Make sure all messages are processed before asserting on the session callbacks.
        stopAutoDispatcherAndDispatchAll();

        assertThat(mHalHelper.getStartSessionCount()).isEqualTo(1);
        ArgumentCaptor<IVibrationSession> captor = ArgumentCaptor.forClass(IVibrationSession.class);
        verify(callback).onStarted(captor.capture());

        IVibrationSession startedSession = captor.getValue();
        startedSession.cancelSession();
        startedSession.vibrate(
                CombinedVibration.createParallel(VibrationEffect.createOneShot(10, 255)),
                "reason");

        // VibrationThread will never start this vibration.
        assertFalse(waitUntil(s -> !vibratorHelper1.getAmplitudes().isEmpty(), service,
                TEST_TIMEOUT_MILLIS));

        // Dispatch HAL callbacks.
        mTestLooper.moveTimeForward(sessionFinishDelayMs);
        mTestLooper.dispatchAll();

        assertThat(mHalHelper.getEndSessionCount()).isEqualTo(0);
        assertThat(mHalHelper.getAbortSessionCount()).isEqualTo(1);
        assertThat(session.getStatus()).isEqualTo(Status.CANCELLED_BY_USER);
        verify(callback).onFinishing();
        verify(callback).onFinished(eq(android.os.vibrator.VendorVibrationSession.STATUS_CANCELED));
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void vibrateInSession_afterFinish_vibrationIgnored() throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHalHelper.setVibratorIds(new int[]{1, 2});
        int sessionFinishDelayMs = 200;
        mHalHelper.setSessionEndDelayMs(sessionFinishDelayMs);
        HalVibratorHelper vibratorHelper1 = mHalHelper.getVibratorHelper(1);
        vibratorHelper1.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        VibratorManagerService service = createSystemReadyService();
        IVibrationSessionCallback callback = mockSessionCallbacks();
        VendorVibrationSession session = startSession(service, RINGTONE_ATTRS, callback, 1, 2);

        // Make sure all messages are processed before asserting on the session callbacks.
        stopAutoDispatcherAndDispatchAll();

        assertThat(mHalHelper.getStartSessionCount()).isEqualTo(1);
        ArgumentCaptor<IVibrationSession> captor = ArgumentCaptor.forClass(IVibrationSession.class);
        verify(callback).onStarted(captor.capture());

        IVibrationSession startedSession = captor.getValue();
        startedSession.finishSession();
        mTestLooper.dispatchAll();

        startedSession.vibrate(
                CombinedVibration.createParallel(VibrationEffect.createOneShot(10, 255)),
                "reason");

        // Session not ended until HAL callback.
        assertThat(session.getStatus()).isEqualTo(Status.RUNNING);

        // VibrationThread will never start this vibration.
        assertFalse(waitUntil(s -> !vibratorHelper1.getAmplitudes().isEmpty(), service,
                TEST_TIMEOUT_MILLIS));

        // Dispatch HAL callbacks.
        mTestLooper.moveTimeForward(sessionFinishDelayMs);
        mTestLooper.dispatchAll();

        assertThat(mHalHelper.getEndSessionCount()).isEqualTo(1);
        assertThat(mHalHelper.getAbortSessionCount()).isEqualTo(0);
        assertThat(session.getStatus()).isEqualTo(Status.FINISHED);
        verify(callback).onFinishing();
        verify(callback).onFinished(eq(android.os.vibrator.VendorVibrationSession.STATUS_SUCCESS));
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void vibrateInSession_repeatingVibration_vibrationIgnored() throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHalHelper.setVibratorIds(new int[]{1, 2});
        int sessionFinishDelayMs = 200;
        mHalHelper.setSessionEndDelayMs(sessionFinishDelayMs);
        HalVibratorHelper vibratorHelper1 = mHalHelper.getVibratorHelper(1);
        vibratorHelper1.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        VibratorManagerService service = createSystemReadyService();
        IVibrationSessionCallback callback = mockSessionCallbacks();
        VendorVibrationSession session = startSession(service, RINGTONE_ATTRS, callback, 1, 2);

        // Make sure all messages are processed before asserting on the session callbacks.
        stopAutoDispatcherAndDispatchAll();

        assertThat(mHalHelper.getStartSessionCount()).isEqualTo(1);
        ArgumentCaptor<IVibrationSession> captor = ArgumentCaptor.forClass(IVibrationSession.class);
        verify(callback).onStarted(captor.capture());

        IVibrationSession startedSession = captor.getValue();
        startedSession.vibrate(
                CombinedVibration.createParallel(
                        VibrationEffect.createWaveform(new long[]{ 10, 10, 10, 10}, 0)),
                "reason");

        // VibrationThread will never start this vibration.
        assertFalse(waitUntil(s -> !vibratorHelper1.getAmplitudes().isEmpty(), service,
                TEST_TIMEOUT_MILLIS));

        startedSession.finishSession();
        mTestLooper.dispatchAll();

        // Dispatch HAL callbacks.
        mTestLooper.moveTimeForward(sessionFinishDelayMs);
        mTestLooper.dispatchAll();

        assertThat(mHalHelper.getEndSessionCount()).isEqualTo(1);
        assertThat(mHalHelper.getAbortSessionCount()).isEqualTo(0);
        assertThat(session.getStatus()).isEqualTo(Status.FINISHED);
        assertThat(service.isVibrating(1)).isFalse();
        assertThat(service.isVibrating(2)).isFalse();
        verify(callback).onFinishing();
        verify(callback).onFinished(eq(android.os.vibrator.VendorVibrationSession.STATUS_SUCCESS));
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void vibrateInSession_singleVibration_playsAllVibrateCommands() throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHalHelper.setVibratorIds(new int[]{1, 2});
        int sessionFinishDelayMs = 200;
        mHalHelper.setSessionEndDelayMs(sessionFinishDelayMs);
        HalVibratorHelper vibratorHelper1 = mHalHelper.getVibratorHelper(1);
        vibratorHelper1.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        HalVibratorHelper vibratorHelper2 = mHalHelper.getVibratorHelper(1);
        vibratorHelper2.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        VibratorManagerService service = createSystemReadyService();
        IVibrationSessionCallback callback = mockSessionCallbacks();
        VendorVibrationSession session = startSession(service, RINGTONE_ATTRS, callback, 1, 2);

        // Make sure all messages are processed before asserting on the session callbacks.
        stopAutoDispatcherAndDispatchAll();

        assertThat(mHalHelper.getStartSessionCount()).isEqualTo(1);
        ArgumentCaptor<IVibrationSession> captor = ArgumentCaptor.forClass(IVibrationSession.class);
        verify(callback).onStarted(captor.capture());

        IVibrationSession startedSession = captor.getValue();
        startedSession.vibrate(
                CombinedVibration.createParallel(
                        VibrationEffect.createWaveform(new long[]{ 10, 10, 10, 10}, -1)),
                "reason");

        // VibrationThread will start this vibration async, so wait until vibration is triggered.
        // Vibrators will receive 2 requests for the waveform playback
        assertTrue(waitUntil(s -> vibratorHelper1.getAmplitudes().size() == 2, service,
                TEST_TIMEOUT_MILLIS));
        assertTrue(waitUntil(s -> vibratorHelper2.getAmplitudes().size() == 2, service,
                TEST_TIMEOUT_MILLIS));

        startedSession.finishSession();
        mTestLooper.dispatchAll();

        // Dispatch HAL callbacks.
        mTestLooper.moveTimeForward(sessionFinishDelayMs);
        mTestLooper.dispatchAll();

        assertThat(session.getStatus()).isEqualTo(Status.FINISHED);
        assertThat(service.isVibrating(1)).isFalse();
        assertThat(service.isVibrating(2)).isFalse();
        verify(callback).onFinishing();
        verify(callback).onFinished(eq(android.os.vibrator.VendorVibrationSession.STATUS_SUCCESS));

        assertThat(mHalHelper.getEndSessionCount()).isEqualTo(1);
        assertThat(mHalHelper.getAbortSessionCount()).isEqualTo(0);
        verify(mVibratorFrameworkStatsLoggerMock).logVibrationVendorSessionStarted(eq(UID));
        verify(mVibratorFrameworkStatsLoggerMock)
                .logVibrationVendorSessionVibrations(eq(UID), eq(1));
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void vibrateInSession_multipleVibrations_playsAllVibrations() throws Exception {
        mHalHelper.setCapabilities(IVibratorManager.CAP_START_SESSIONS);
        mHalHelper.setVibratorIds(new int[]{1, 2});
        int sessionFinishDelayMs = 200;
        mHalHelper.setSessionEndDelayMs(sessionFinishDelayMs);
        HalVibratorHelper vibratorHelper1 = mHalHelper.getVibratorHelper(1);
        vibratorHelper1.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        VibratorManagerService service = createSystemReadyService();
        IVibrationSessionCallback callback = mockSessionCallbacks();
        VendorVibrationSession session = startSession(service, RINGTONE_ATTRS, callback, 1, 2);

        // Make sure all messages are processed before asserting on the session callbacks.
        stopAutoDispatcherAndDispatchAll();

        assertThat(mHalHelper.getStartSessionCount()).isEqualTo(1);
        ArgumentCaptor<IVibrationSession> captor = ArgumentCaptor.forClass(IVibrationSession.class);
        verify(callback).onStarted(captor.capture());

        IVibrationSession startedSession = captor.getValue();
        startedSession.vibrate(
                CombinedVibration.createParallel(VibrationEffect.createOneShot(10, 255)),
                "reason");

        // VibrationThread will start this vibration async, so wait until vibration is completed.
        assertTrue(waitUntil(s -> vibratorHelper1.getAmplitudes().size() == 1, service,
                TEST_TIMEOUT_MILLIS));
        assertTrue(waitUntil(s -> !session.getVibrations().isEmpty(), service,
                TEST_TIMEOUT_MILLIS));

        startedSession.vibrate(
                CombinedVibration.createParallel(VibrationEffect.createOneShot(20, 255)),
                "reason");

        assertTrue(waitUntil(s -> vibratorHelper1.getAmplitudes().size() == 2, service,
                TEST_TIMEOUT_MILLIS));

        startedSession.finishSession();
        mTestLooper.dispatchAll();

        // Dispatch HAL callbacks.
        mTestLooper.moveTimeForward(sessionFinishDelayMs);
        mTestLooper.dispatchAll();

        assertThat(session.getStatus()).isEqualTo(Status.FINISHED);
        assertThat(service.isVibrating(1)).isFalse();
        assertThat(service.isVibrating(2)).isFalse();
        verify(callback).onFinishing();
        verify(callback).onFinished(eq(android.os.vibrator.VendorVibrationSession.STATUS_SUCCESS));

        assertThat(mHalHelper.getEndSessionCount()).isEqualTo(1);
        assertThat(mHalHelper.getAbortSessionCount()).isEqualTo(0);
        verify(mVibratorFrameworkStatsLoggerMock).logVibrationVendorSessionStarted(eq(UID));
        verify(mVibratorFrameworkStatsLoggerMock)
                .logVibrationVendorSessionVibrations(eq(UID), eq(2));
    }

    @Test
    public void frameworkStats_externalVibration_reportsAllMetrics() throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_EXTERNAL_CONTROL);
        createSystemReadyService();

        AudioAttributes audioAttrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build();

        ExternalVibration vib = new ExternalVibration(UID, PACKAGE_NAME, audioAttrs,
                mock(IExternalVibrationController.class));
        mExternalVibratorService.onExternalVibrationStart(vib);

        Thread.sleep(10);
        mExternalVibratorService.onExternalVibrationStop(vib);

        ArgumentCaptor<VibrationStats.StatsInfo> argumentCaptor =
                ArgumentCaptor.forClass(VibrationStats.StatsInfo.class);
        verify(mVibratorFrameworkStatsLoggerMock, timeout(TEST_TIMEOUT_MILLIS))
                .writeVibrationReportedAsync(argumentCaptor.capture());

        VibrationStats.StatsInfo statsInfo = argumentCaptor.getValue();
        assertEquals(UID, statsInfo.uid);
        assertEquals(FrameworkStatsLog.VIBRATION_REPORTED__VIBRATION_TYPE__EXTERNAL,
                statsInfo.vibrationType);
        assertEquals(VibrationAttributes.USAGE_ALARM, statsInfo.usage);
        assertEquals(Status.FINISHED.getProtoEnumValue(), statsInfo.status);
        assertTrue(statsInfo.totalDurationMillis > 0);
        assertTrue(
                "Expected vibrator ON for at least 10ms, got " + statsInfo.vibratorOnMillis + "ms",
                statsInfo.vibratorOnMillis >= 10);
        assertEquals(2, statsInfo.halSetExternalControlCount);
    }

    @Test
    public void frameworkStats_waveformVibration_reportsAllMetrics() throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);

        VibratorManagerService service = createSystemReadyService();
        vibrateAndWaitUntilFinished(service,
                VibrationEffect.createWaveform(new long[] {0, 10, 20, 10}, -1), RINGTONE_ATTRS);

        verify(mVibratorFrameworkStatsLoggerMock, timeout(TEST_TIMEOUT_MILLIS))
                .writeVibratorStateOnAsync(eq(UID), anyLong());
        verify(mVibratorFrameworkStatsLoggerMock, timeout(TEST_TIMEOUT_MILLIS))
                .writeVibratorStateOffAsync(eq(UID));

        ArgumentCaptor<VibrationStats.StatsInfo> argumentCaptor =
                ArgumentCaptor.forClass(VibrationStats.StatsInfo.class);
        verify(mVibratorFrameworkStatsLoggerMock, timeout(TEST_TIMEOUT_MILLIS))
                .writeVibrationReportedAsync(argumentCaptor.capture());

        VibrationStats.StatsInfo metrics = argumentCaptor.getValue();
        assertEquals(UID, metrics.uid);
        assertEquals(FrameworkStatsLog.VIBRATION_REPORTED__VIBRATION_TYPE__SINGLE,
                metrics.vibrationType);
        assertEquals(VibrationAttributes.USAGE_RINGTONE, metrics.usage);
        assertEquals(Status.FINISHED.getProtoEnumValue(), metrics.status);
        assertTrue("Total duration was too low, " + metrics.totalDurationMillis + "ms",
                metrics.totalDurationMillis >= 20);
        assertTrue("Vibrator ON duration was too low, " + metrics.vibratorOnMillis + "ms",
                metrics.vibratorOnMillis >= 20);

        // All unrelated metrics are empty.
        assertEquals(0, metrics.repeatCount);
        assertEquals(0, metrics.halComposeCount);
        assertEquals(0, metrics.halComposePwleCount);
        assertEquals(0, metrics.halPerformCount);
        assertEquals(0, metrics.halSetExternalControlCount);
        assertEquals(0, metrics.halCompositionSize);
        assertEquals(0, metrics.halPwleSize);
        assertNull(metrics.halSupportedCompositionPrimitivesUsed);
        assertNull(metrics.halSupportedEffectsUsed);
        assertNull(metrics.halUnsupportedCompositionPrimitivesUsed);
        assertNull(metrics.halUnsupportedEffectsUsed);

        // Accommodate for ramping off config that might add extra setAmplitudes.
        assertEquals(2, metrics.halOnCount);
        assertTrue(metrics.halOffCount > 0);
        assertTrue(metrics.halSetAmplitudeCount >= 2);
    }

    @Test
    public void frameworkStats_repeatingVibration_reportsAllMetrics() throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);

        VibratorManagerService service = createSystemReadyService();
        vibrate(service, VibrationEffect.createWaveform(new long[] {10, 100}, 1), RINGTONE_ATTRS);

        verify(mVibratorFrameworkStatsLoggerMock, timeout(TEST_TIMEOUT_MILLIS))
                .writeVibratorStateOnAsync(eq(UID), anyLong());

        // Wait for at least one loop before cancelling it.
        Thread.sleep(100);
        service.cancelVibrate(VibrationAttributes.USAGE_RINGTONE, service);

        verify(mVibratorFrameworkStatsLoggerMock, timeout(TEST_TIMEOUT_MILLIS))
                .writeVibratorStateOffAsync(eq(UID));

        ArgumentCaptor<VibrationStats.StatsInfo> argumentCaptor =
                ArgumentCaptor.forClass(VibrationStats.StatsInfo.class);
        verify(mVibratorFrameworkStatsLoggerMock, timeout(TEST_TIMEOUT_MILLIS))
                .writeVibrationReportedAsync(argumentCaptor.capture());

        VibrationStats.StatsInfo metrics = argumentCaptor.getValue();
        assertEquals(UID, metrics.uid);
        assertEquals(FrameworkStatsLog.VIBRATION_REPORTED__VIBRATION_TYPE__REPEATED,
                metrics.vibrationType);
        assertEquals(VibrationAttributes.USAGE_RINGTONE, metrics.usage);
        assertEquals(Status.CANCELLED_BY_USER.getProtoEnumValue(), metrics.status);
        assertTrue("Total duration was too low, " + metrics.totalDurationMillis + "ms",
                metrics.totalDurationMillis >= 100);
        assertTrue("Vibrator ON duration was too low, " + metrics.vibratorOnMillis + "ms",
                metrics.vibratorOnMillis >= 100);

        // All unrelated metrics are empty.
        assertTrue(metrics.repeatCount > 0);
        assertEquals(0, metrics.halComposeCount);
        assertEquals(0, metrics.halComposePwleCount);
        assertEquals(0, metrics.halPerformCount);
        assertEquals(0, metrics.halSetExternalControlCount);
        assertEquals(0, metrics.halCompositionSize);
        assertEquals(0, metrics.halPwleSize);
        assertNull(metrics.halSupportedCompositionPrimitivesUsed);
        assertNull(metrics.halSupportedEffectsUsed);
        assertNull(metrics.halUnsupportedCompositionPrimitivesUsed);
        assertNull(metrics.halUnsupportedEffectsUsed);

        // Accommodate for ramping off config that might add extra setAmplitudes.
        assertTrue(metrics.halOnCount > 0);
        assertTrue(metrics.halOffCount > 0);
        assertTrue(metrics.halSetAmplitudeCount > 0);
    }

    @Test
    public void frameworkStats_prebakedAndComposedVibrations_reportsAllMetrics() throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        mHalHelper.getVibratorHelper(1).setSupportedEffects(EFFECT_CLICK, EFFECT_TICK);
        mHalHelper.getVibratorHelper(1).setSupportedPrimitives(PRIMITIVE_TICK, PRIMITIVE_CLICK);

        VibratorManagerService service = createSystemReadyService();
        vibrateAndWaitUntilFinished(service,
                VibrationEffect.startComposition()
                        .addEffect(VibrationEffect.createPredefined(EFFECT_CLICK))
                        .addPrimitive(PRIMITIVE_TICK)
                        .addPrimitive(PRIMITIVE_TICK)
                        .addEffect(VibrationEffect.createPredefined(EFFECT_CLICK))
                        .addEffect(VibrationEffect.createPredefined(EFFECT_TICK))
                        .addPrimitive(PRIMITIVE_CLICK)
                        .addPrimitive(PRIMITIVE_CLICK)
                        .compose(),
                ALARM_ATTRS);

        verify(mVibratorFrameworkStatsLoggerMock, timeout(TEST_TIMEOUT_MILLIS))
                .writeVibratorStateOnAsync(eq(UID), anyLong());
        verify(mVibratorFrameworkStatsLoggerMock, timeout(TEST_TIMEOUT_MILLIS))
                .writeVibratorStateOffAsync(eq(UID));

        ArgumentCaptor<VibrationStats.StatsInfo> argumentCaptor =
                ArgumentCaptor.forClass(VibrationStats.StatsInfo.class);
        verify(mVibratorFrameworkStatsLoggerMock, timeout(TEST_TIMEOUT_MILLIS))
                .writeVibrationReportedAsync(argumentCaptor.capture());

        VibrationStats.StatsInfo metrics = argumentCaptor.getValue();
        assertEquals(UID, metrics.uid);
        assertEquals(FrameworkStatsLog.VIBRATION_REPORTED__VIBRATION_TYPE__SINGLE,
                metrics.vibrationType);
        assertEquals(VibrationAttributes.USAGE_ALARM, metrics.usage);
        assertEquals(Status.FINISHED.getProtoEnumValue(), metrics.status);

        // At least 4 effect/primitive played, 20ms each, plus configured fallback.
        assertTrue("Total duration was too low, " + metrics.totalDurationMillis + "ms",
                metrics.totalDurationMillis >= 80);
        assertTrue("Vibrator ON duration was too low, " + metrics.vibratorOnMillis + "ms",
                metrics.vibratorOnMillis >= 80);

        // Related metrics were collected.
        assertThat(metrics.halComposeCount).isEqualTo(2); // TICK+TICK, then CLICK+CLICK
        assertThat(metrics.halPerformCount).isEqualTo(3); // CLICK, TICK, then CLICK
        assertThat(metrics.halCompositionSize).isEqualTo(4); // 2*TICK + 2*CLICK
        assertThat(metrics.halOffCount).isGreaterThan(0);
        // No repetitions in reported effect/primitive IDs.
        assertArrayEquals(
                new int[] { PRIMITIVE_CLICK, PRIMITIVE_TICK },
                metrics.halSupportedCompositionPrimitivesUsed);
        assertArrayEquals(new int[] {EFFECT_CLICK, EFFECT_TICK}, metrics.halSupportedEffectsUsed);
        assertThat(metrics.halUnsupportedEffectsUsed).isNull();

        // All unrelated metrics are empty.
        assertThat(metrics.repeatCount).isEqualTo(0);
        assertThat(metrics.halComposePwleCount).isEqualTo(0);
        assertThat(metrics.halSetExternalControlCount).isEqualTo(0);
        assertThat(metrics.halPwleSize).isEqualTo(0);
        assertThat(metrics.halOnCount).isEqualTo(0);
        assertThat(metrics.halSetAmplitudeCount).isEqualTo(0);
    }

    @Test
    public void frameworkStats_interruptingVibrations_reportsAllMetrics() throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        VibratorManagerService service = createSystemReadyService();

        vibrate(service, VibrationEffect.createOneShot(1_000, 128), HAPTIC_FEEDBACK_ATTRS);

        // VibrationThread will start this vibration async, so wait until vibration is triggered.
        assertTrue(waitUntil(s -> !mHalHelper.getVibratorHelper(1).getEffectSegments().isEmpty(),
                service, TEST_TIMEOUT_MILLIS));

        vibrateAndWaitUntilFinished(service, VibrationEffect.createOneShot(10, 255), ALARM_ATTRS);

        ArgumentCaptor<VibrationStats.StatsInfo> argumentCaptor =
                ArgumentCaptor.forClass(VibrationStats.StatsInfo.class);
        verify(mVibratorFrameworkStatsLoggerMock, timeout(TEST_TIMEOUT_MILLIS).times(2))
                .writeVibrationReportedAsync(argumentCaptor.capture());

        VibrationStats.StatsInfo touchMetrics = argumentCaptor.getAllValues().get(0);
        assertEquals(UID, touchMetrics.uid);
        assertEquals(VibrationAttributes.USAGE_TOUCH, touchMetrics.usage);
        assertEquals(Status.CANCELLED_SUPERSEDED.getProtoEnumValue(),
                touchMetrics.status);
        assertTrue(touchMetrics.endedBySameUid);
        assertEquals(VibrationAttributes.USAGE_ALARM, touchMetrics.endedByUsage);
        assertEquals(-1, touchMetrics.interruptedUsage);

        VibrationStats.StatsInfo alarmMetrics = argumentCaptor.getAllValues().get(1);
        assertEquals(UID, alarmMetrics.uid);
        assertEquals(VibrationAttributes.USAGE_ALARM, alarmMetrics.usage);
        assertEquals(Status.FINISHED.getProtoEnumValue(), alarmMetrics.status);
        assertFalse(alarmMetrics.endedBySameUid);
        assertEquals(-1, alarmMetrics.endedByUsage);
        assertEquals(VibrationAttributes.USAGE_TOUCH, alarmMetrics.interruptedUsage);
    }

    @Test
    public void frameworkStats_ignoredVibration_reportsStatus() throws Exception {
        setUserSetting(Settings.System.RING_VIBRATION_INTENSITY,
                Vibrator.VIBRATION_INTENSITY_OFF);

        mHalHelper.setVibratorIds(new int[]{1});
        VibratorManagerService service = createSystemReadyService();
        mRegisteredPowerModeListener.onLowPowerModeChanged(LOW_POWER_STATE);

        // Haptic feedback ignored in low power state
        vibrateAndWaitUntilFinished(service, VibrationEffect.createOneShot(100, 128),
                HAPTIC_FEEDBACK_ATTRS);
        // Ringtone vibration user settings are off
        vibrateAndWaitUntilFinished(service, VibrationEffect.createOneShot(200, 128),
                RINGTONE_ATTRS);

        ArgumentCaptor<VibrationStats.StatsInfo> argumentCaptor =
                ArgumentCaptor.forClass(VibrationStats.StatsInfo.class);
        verify(mVibratorFrameworkStatsLoggerMock, timeout(TEST_TIMEOUT_MILLIS).times(2))
                .writeVibrationReportedAsync(argumentCaptor.capture());

        VibrationStats.StatsInfo touchMetrics = argumentCaptor.getAllValues().get(0);
        assertEquals(UID, touchMetrics.uid);
        assertEquals(VibrationAttributes.USAGE_TOUCH, touchMetrics.usage);
        assertEquals(Status.IGNORED_FOR_POWER.getProtoEnumValue(), touchMetrics.status);

        VibrationStats.StatsInfo ringtoneMetrics = argumentCaptor.getAllValues().get(1);
        assertEquals(UID, ringtoneMetrics.uid);
        assertEquals(VibrationAttributes.USAGE_RINGTONE, ringtoneMetrics.usage);
        assertEquals(Status.IGNORED_FOR_SETTINGS.getProtoEnumValue(),
                ringtoneMetrics.status);

        for (VibrationStats.StatsInfo metrics : argumentCaptor.getAllValues()) {
            // Latencies are empty since vibrations never started
            assertEquals(0, metrics.startLatencyMillis);
            assertEquals(0, metrics.endLatencyMillis);
            assertEquals(0, metrics.vibratorOnMillis);

            // All unrelated metrics are empty.
            assertEquals(0, metrics.repeatCount);
            assertEquals(0, metrics.halComposeCount);
            assertEquals(0, metrics.halComposePwleCount);
            assertEquals(0, metrics.halOffCount);
            assertEquals(0, metrics.halOnCount);
            assertEquals(0, metrics.halPerformCount);
            assertEquals(0, metrics.halSetExternalControlCount);
            assertEquals(0, metrics.halCompositionSize);
            assertEquals(0, metrics.halPwleSize);
            assertNull(metrics.halSupportedCompositionPrimitivesUsed);
            assertNull(metrics.halSupportedEffectsUsed);
            assertNull(metrics.halUnsupportedCompositionPrimitivesUsed);
            assertNull(metrics.halUnsupportedEffectsUsed);
        }
    }

    @Test
    public void frameworkStats_multiVibrators_reportsAllMetrics() throws Exception {
        mHalHelper.setVibratorIds(new int[]{1, 2});
        mHalHelper.getVibratorHelper(1).setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        mHalHelper.getVibratorHelper(1).setSupportedPrimitives(PRIMITIVE_TICK);
        mHalHelper.getVibratorHelper(2).setSupportedEffects(EFFECT_TICK);

        VibratorManagerService service = createSystemReadyService();
        vibrateAndWaitUntilFinished(service,
                CombinedVibration.startParallel()
                        .addVibrator(1,
                                VibrationEffect.startComposition()
                                        .addPrimitive(PRIMITIVE_TICK)
                                        .compose())
                        .addVibrator(2, VibrationEffect.createPredefined(EFFECT_TICK))
                        .combine(),
                NOTIFICATION_ATTRS);

        SparseBooleanArray expectedEffectsUsed = new SparseBooleanArray();
        expectedEffectsUsed.put(EFFECT_TICK, true);

        SparseBooleanArray expectedPrimitivesUsed = new SparseBooleanArray();
        expectedPrimitivesUsed.put(PRIMITIVE_TICK, true);

        verify(mVibratorFrameworkStatsLoggerMock, timeout(TEST_TIMEOUT_MILLIS))
                .writeVibratorStateOnAsync(eq(UID), anyLong());
        verify(mVibratorFrameworkStatsLoggerMock, timeout(TEST_TIMEOUT_MILLIS))
                .writeVibratorStateOffAsync(eq(UID));

        ArgumentCaptor<VibrationStats.StatsInfo> argumentCaptor =
                ArgumentCaptor.forClass(VibrationStats.StatsInfo.class);
        verify(mVibratorFrameworkStatsLoggerMock, timeout(TEST_TIMEOUT_MILLIS))
                .writeVibrationReportedAsync(argumentCaptor.capture());

        VibrationStats.StatsInfo metrics = argumentCaptor.getValue();
        assertEquals(UID, metrics.uid);
        assertEquals(FrameworkStatsLog.VIBRATION_REPORTED__VIBRATION_TYPE__SINGLE,
                metrics.vibrationType);
        assertEquals(VibrationAttributes.USAGE_NOTIFICATION, metrics.usage);
        assertEquals(Status.FINISHED.getProtoEnumValue(), metrics.status);
        assertTrue(metrics.totalDurationMillis >= 20);

        // vibratorOnMillis accumulates both vibrators, it's 20 for each constant.
        assertEquals(40, metrics.vibratorOnMillis);

        // Related metrics were collected.
        assertEquals(1, metrics.halComposeCount);
        assertEquals(1, metrics.halPerformCount);
        assertEquals(1, metrics.halCompositionSize);
        assertEquals(2, metrics.halOffCount);
        assertArrayEquals(new int[] {PRIMITIVE_TICK},
                metrics.halSupportedCompositionPrimitivesUsed);
        assertArrayEquals(new int[] {EFFECT_TICK}, metrics.halSupportedEffectsUsed);

        // All unrelated metrics are empty.
        assertEquals(0, metrics.repeatCount);
        assertEquals(0, metrics.halComposePwleCount);
        assertEquals(0, metrics.halOnCount);
        assertEquals(0, metrics.halSetAmplitudeCount);
        assertEquals(0, metrics.halSetExternalControlCount);
        assertEquals(0, metrics.halPwleSize);
        assertNull(metrics.halUnsupportedCompositionPrimitivesUsed);
        assertNull(metrics.halUnsupportedEffectsUsed);
    }

    private void assertCanVibrateWithBypassFlags(boolean expectedCanApplyBypassFlags)
            throws Exception {
        mHalHelper.setVibratorIds(new int[]{1});
        mHalHelper.getVibratorHelper(1).setSupportedEffects(EFFECT_CLICK);
        VibratorManagerService service = createSystemReadyService();

        HalVibration vibration = vibrateAndWaitUntilFinished(
                service,
                VibrationEffect.createPredefined(EFFECT_CLICK),
                new VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_TOUCH)
                        .setFlags(
                                VibrationAttributes.FLAG_BYPASS_USER_VIBRATION_INTENSITY_OFF
                                        | VibrationAttributes.FLAG_BYPASS_INTERRUPTION_POLICY)
                        .build());

        VibrationAttributes attrs = vibration.callerInfo.attrs;
        assertEquals(
                expectedCanApplyBypassFlags,
                attrs.isFlagSet(VibrationAttributes.FLAG_BYPASS_USER_VIBRATION_INTENSITY_OFF));
        assertEquals(
                expectedCanApplyBypassFlags,
                attrs.isFlagSet(VibrationAttributes.FLAG_BYPASS_INTERRUPTION_POLICY));
    }

    private VibrationEffectSegment expectedPrebaked(int effectId) {
        return expectedPrebaked(effectId, EFFECT_STRENGTH_MEDIUM);
    }

    private VibrationEffectSegment expectedPrebaked(int effectId, int effectStrength) {
        return new PrebakedSegment(effectId, false, effectStrength);
    }

    private IVibrationSessionCallback mockSessionCallbacks() {
        IBinder token = mock(IBinder.class);
        IVibrationSessionCallback callback = mock(IVibrationSessionCallback.class);
        doReturn(token).when(callback).asBinder();
        return callback;
    }

    private void cancelVibrate(VibratorManagerService service) {
        service.cancelVibrate(VibrationAttributes.USAGE_FILTER_MATCH_ALL, service);
    }

    private IVibratorStateListener mockVibratorStateListener() {
        IVibratorStateListener listenerMock = mock(IVibratorStateListener.class);
        IBinder binderMock = mock(IBinder.class);
        when(listenerMock.asBinder()).thenReturn(binderMock);
        return listenerMock;
    }

    private InputDevice createInputDeviceWithVibrator(int id) {
        return new InputDevice.Builder()
                .setId(id)
                .setName("Test Device " + id)
                .setHasVibrator(true)
                .build();
    }

    private static <T> void addLocalServiceMock(Class<T> clazz, T mock) {
        LocalServices.removeServiceForTest(clazz);
        LocalServices.addService(clazz, mock);
    }

    private void setRingerMode(int ringerMode) {
        when(mAudioManagerMock.getRingerModeInternal()).thenReturn(ringerMode);
    }

    private void setUserSetting(String settingName, int value) {
        Settings.System.putIntForUser(
                mContextSpy.getContentResolver(), settingName, value, UserHandle.USER_CURRENT);
    }

    private HalVibration performHapticFeedbackAndWaitUntilFinished(VibratorManagerService service,
            int constant, boolean always) throws InterruptedException {
        return performHapticFeedbackAndWaitUntilFinished(
                service, constant, VibrationAttributes.USAGE_UNKNOWN, always);
    }

    private HalVibration performHapticFeedbackAndWaitUntilFinished(VibratorManagerService service,
            int constant, int usage, boolean always) throws InterruptedException {
        HalVibration vib = service.performHapticFeedbackInternal(UID, Context.DEVICE_ID_DEFAULT,
                PACKAGE_NAME, constant, usage, "some reason", service,
                always ? HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING : 0 /* flags */,
                0 /* privFlags */);
        if (vib != null) {
            vib.waitForEnd();
        }

        return vib;
    }

    private HalVibration performHapticFeedbackForInputDeviceAndWaitUntilFinished(
            VibratorManagerService service, int constant, int inputDeviceId, int inputSource,
            boolean always) throws InterruptedException {
        HalVibration vib = service.performHapticFeedbackForInputDeviceInternal(UID,
                Context.DEVICE_ID_DEFAULT, PACKAGE_NAME, constant, inputDeviceId, inputSource,
                "some reason", service,
                always ? HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING : 0 /* flags */,
                0 /* privFlags */);
        if (vib != null) {
            vib.waitForEnd();
        }

        return vib;
    }

    private HalVibration vibrateAndWaitUntilFinished(VibratorManagerService service,
            VibrationEffect effect, VibrationAttributes attrs) throws InterruptedException {
        return vibrateAndWaitUntilFinished(
                service, CombinedVibration.createParallel(effect), attrs);
    }

    private HalVibration vibrateAndWaitUntilFinished(VibratorManagerService service,
            CombinedVibration effect, VibrationAttributes attrs) throws InterruptedException {
        HalVibration vib = vibrate(service, effect, attrs);
        if (vib != null) {
            vib.waitForEnd();
        }
        return vib;
    }

    private HalVibration vibrate(VibratorManagerService service, VibrationEffect effect,
            VibrationAttributes attrs) {
        return vibrate(service, CombinedVibration.createParallel(effect), attrs);
    }

    private HalVibration vibrate(VibratorManagerService service, CombinedVibration effect,
            VibrationAttributes attrs) {
        return vibrateWithDevice(service, Context.DEVICE_ID_DEFAULT, effect, attrs);
    }

    private HalVibration vibrateWithUid(VibratorManagerService service, int uid,
            VibrationEffect effect, VibrationAttributes attrs) {
        return vibrateWithUidAndDevice(service, uid, Context.DEVICE_ID_DEFAULT,
                CombinedVibration.createParallel(effect), attrs);
    }

    private HalVibration vibrateWithDevice(VibratorManagerService service, int deviceId,
            CombinedVibration effect, VibrationAttributes attrs) {
        return vibrateWithUidAndDevice(service, UID, deviceId, effect, attrs);
    }

    private HalVibration vibrateWithUidAndDevice(VibratorManagerService service, int uid,
            int deviceId, CombinedVibration effect, VibrationAttributes attrs) {
        HalVibration vib = service.vibrateWithPermissionCheck(uid, deviceId, PACKAGE_NAME, effect,
                attrs, "some reason", service);
        if (vib != null) {
            mPendingVibrations.add(vib);
        }
        return vib;
    }

    private VendorVibrationSession startSession(VibratorManagerService service,
            VibrationAttributes attrs, IVibrationSessionCallback callback, int... vibratorIds) {
        VendorVibrationSession session = service.startVendorVibrationSessionInternal(UID,
                Context.DEVICE_ID_DEFAULT, PACKAGE_NAME, vibratorIds, attrs, "reason", callback);
        if (session != null) {
            mPendingSessions.add(session);
        }
        return session;
    }

    private boolean waitUntil(Predicate<VibratorManagerService> predicate,
            VibratorManagerService service, long timeout) throws InterruptedException {
        long timeoutTimestamp = SystemClock.uptimeMillis() + timeout;
        boolean predicateResult = false;
        while (!predicateResult && SystemClock.uptimeMillis() < timeoutTimestamp) {
            Thread.sleep(10);
            predicateResult = predicate.test(service);
        }
        return predicateResult;
    }

    private void stopAutoDispatcherAndDispatchAll() {
        // Stop auto-dispatcher thread and wait for it to finish processing any messages.
        mTestLooper.stopAutoDispatchAndIgnoreExceptions();
        // Dispatch any pending message left.
        mTestLooper.dispatchAll();
    }

    private void grantPermission(String permission) {
        when(mContextSpy.checkCallingOrSelfPermission(permission))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        doNothing().when(mContextSpy).enforceCallingOrSelfPermission(eq(permission), anyString());
    }

    private void denyPermission(String permission) {
        when(mContextSpy.checkCallingOrSelfPermission(permission))
                .thenReturn(PackageManager.PERMISSION_DENIED);
        doThrow(new SecurityException()).when(mContextSpy)
                .enforceCallingOrSelfPermission(eq(permission), anyString());
    }
}
