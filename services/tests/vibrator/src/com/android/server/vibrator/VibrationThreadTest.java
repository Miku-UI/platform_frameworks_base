/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.os.VibrationAttributes.USAGE_RINGTONE;
import static android.os.VibrationEffect.Composition.PRIMITIVE_CLICK;
import static android.os.VibrationEffect.Composition.PRIMITIVE_SPIN;
import static android.os.VibrationEffect.Composition.PRIMITIVE_TICK;
import static android.os.VibrationEffect.EFFECT_CLICK;
import static android.os.VibrationEffect.EFFECT_TICK;
import static android.os.VibrationEffect.VibrationParameter.targetAmplitude;
import static android.os.VibrationEffect.VibrationParameter.targetFrequency;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContextWrapper;
import android.content.pm.PackageManagerInternal;
import android.hardware.vibrator.Braking;
import android.hardware.vibrator.IVibrator;
import android.hardware.vibrator.IVibratorManager;
import android.os.CombinedVibration;
import android.os.Handler;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.test.TestLooper;
import android.os.vibrator.Flags;
import android.os.vibrator.PrebakedSegment;
import android.os.vibrator.PrimitiveSegment;
import android.os.vibrator.PwlePoint;
import android.os.vibrator.RampSegment;
import android.os.vibrator.StepSegment;
import android.os.vibrator.VibrationConfig;
import android.os.vibrator.VibrationEffectSegment;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.util.SparseArray;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;

import com.android.internal.util.test.FakeSettingsProvider;
import com.android.internal.util.test.FakeSettingsProviderRule;
import com.android.server.LocalServices;
import com.android.server.vibrator.VibrationSession.CallerInfo;
import com.android.server.vibrator.VibrationSession.Status;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

public class VibrationThreadTest {

    private static final int TEST_TIMEOUT_MILLIS = 900;
    private static final int TEST_IMMEDIATE_CANCEL_TIMEOUT_MILLIS = 100;
    private static final int UID = Process.ROOT_UID;
    private static final int DEVICE_ID = 10;
    private static final int VIBRATOR_ID = 1;
    private static final String PACKAGE_NAME = "package";
    private static final VibrationAttributes ATTRS = new VibrationAttributes.Builder().build();
    private static final int TEST_RAMP_STEP_DURATION = 5;

    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Rule
    public FakeSettingsProviderRule mSettingsProviderRule = FakeSettingsProvider.rule();

    @Mock private PackageManagerInternal mPackageManagerInternalMock;
    @Mock private VibrationThread.VibratorManagerHooks mManagerHooks;
    @Mock private HalVibrator.Callbacks mHalCallbacks;
    @Mock private VibratorFrameworkStatsLogger mStatsLoggerMock;

    private ContextWrapper mContextSpy;
    private final SparseArray<HalVibratorHelper> mVibratorHelpers = new SparseArray<>();
    private final SparseArray<VibrationEffect> mFallbackEffects = new SparseArray<>();
    private TestLooper mTestLooper;
    private TestLooperAutoDispatcher mCustomTestLooperDispatcher;
    private VibrationConfig.Builder mVibrationConfigBuilder;

    private VibrationSettings mVibrationSettings;
    private VibrationScaler mVibrationScaler;
    private VibrationThread mThread;

    // Setup every time a new vibration is dispatched to the VibrationThread.
    private SparseArray<HalVibrator> mVibrators;
    private VibrationStepConductor mVibrationConductor;

    @Before
    public void setUp() throws Exception {
        mTestLooper = new TestLooper();

        when(mPackageManagerInternalMock.getSystemUiServiceComponent())
                .thenReturn(new ComponentName("", ""));
        doAnswer(answer -> {
            mVibrationConductor.notifyVibratorComplete(
                    answer.getArgument(0), answer.getArgument(2));
            return null;
        }).when(mHalCallbacks).onVibrationStepComplete(anyInt(), anyLong(), anyLong());

        LocalServices.removeServiceForTest(PackageManagerInternal.class);
        LocalServices.addService(PackageManagerInternal.class, mPackageManagerInternalMock);

        mContextSpy = spy(new ContextWrapper(InstrumentationRegistry.getContext()));
        ContentResolver contentResolver = mSettingsProviderRule.mockContentResolver(mContextSpy);
        when(mContextSpy.getContentResolver()).thenReturn(contentResolver);

        mVibrationConfigBuilder = new VibrationConfig.Builder(null); // use defaults
        mVibrationConfigBuilder.setRampStepDurationMs(TEST_RAMP_STEP_DURATION);

        mockVibrators(VIBRATOR_ID);

        createThreadAndSettings();
    }

    @After
    public void tearDown() {
        if (mCustomTestLooperDispatcher != null) {
            mCustomTestLooperDispatcher.cancel();
        }
    }

    @Test
    public void vibrate_noVibrator_ignoresVibration() {
        mVibratorHelpers.clear();
        CombinedVibration effect = CombinedVibration.createParallel(
                VibrationEffect.get(EFFECT_CLICK));
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mHalCallbacks, never())
                .onVibrationStepComplete(anyInt(), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.IGNORED_UNSUPPORTED);
    }

    @Test
    public void vibrate_missingVibrators_ignoresVibration() {
        CombinedVibration effect = CombinedVibration.startParallel()
                .addVibrator(2, VibrationEffect.get(EFFECT_CLICK))
                .addVibrator(3, VibrationEffect.get(EFFECT_TICK))
                .combine();
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mHalCallbacks, never())
                .onVibrationStepComplete(anyInt(), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.IGNORED_UNSUPPORTED);
    }

    @Test
    public void vibrate_singleVibratorOneShot_runsVibrationAndSetsAmplitude() {
        mVibratorHelpers.get(VIBRATOR_ID).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);

        VibrationEffect effect = VibrationEffect.createOneShot(10, 100);
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mManagerHooks).noteVibratorOn(eq(UID), eq(10L));
        verify(mManagerHooks).noteVibratorOff(eq(UID));
        verify(mHalCallbacks).onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.FINISHED);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();

        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getEffectSegments())
                .containsExactly(expectedOneShot(10)).inOrder();
        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getAmplitudes())
                .containsExactlyElementsIn(expectedAmplitudes(100)).inOrder();
    }

    @Test
    public void vibrate_singleVibratorOneShotFailed_doesNotSetAmplitudeAndReturnsFailure() {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        vibratorHelper.setOnToFail();

        VibrationEffect effect = VibrationEffect.createOneShot(10, 100);
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mManagerHooks, never()).noteVibratorOn(eq(UID), eq(10L));
        verify(mManagerHooks, never()).noteVibratorOff(eq(UID));
        verifyCallbacksTriggered(vibration, Status.IGNORED_ERROR_DISPATCHING);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();

        assertThat(vibratorHelper.getEffectSegments()).isEmpty();
        assertThat(vibratorHelper.getAmplitudes()).isEmpty();
    }

    @Test
    public void vibrate_oneShotWithoutAmplitudeControl_runsVibrationWithDefaultAmplitude() {
        VibrationEffect effect = VibrationEffect.createOneShot(10, 100);
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mManagerHooks).noteVibratorOn(eq(UID), eq(10L));
        verify(mManagerHooks).noteVibratorOff(eq(UID));
        verify(mHalCallbacks).onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.FINISHED);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();

        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getEffectSegments())
                .containsExactly(expectedOneShot(10)).inOrder();
        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getAmplitudes()).isEmpty();
    }

    @Test
    public void vibrate_singleVibratorWaveform_runsVibrationAndChangesAmplitudes() {
        mVibratorHelpers.get(VIBRATOR_ID).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);

        VibrationEffect effect = VibrationEffect.createWaveform(
                new long[]{5, 5, 5}, new int[]{1, 2, 3}, -1);
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mManagerHooks).noteVibratorOn(eq(UID), eq(15L));
        verify(mManagerHooks).noteVibratorOff(eq(UID));
        verify(mHalCallbacks).onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.FINISHED);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();

        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getEffectSegments())
                .containsExactly(expectedOneShot(15)).inOrder();
        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getAmplitudes())
                .containsExactlyElementsIn(expectedAmplitudes(1, 2, 3)).inOrder();
    }

    @Test
    public void vibrate_singleVibratorWaveformFailed_stopsVibrationAfterFailure() {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        vibratorHelper.setOnToFail();

        VibrationEffect effect = VibrationEffect.createWaveform(new long[]{5, 5, 5, 5}, -1);
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mManagerHooks).noteVibratorOn(eq(UID), eq(20L));
        verify(mManagerHooks).noteVibratorOff(eq(UID));
        verifyCallbacksTriggered(vibration, Status.IGNORED_ERROR_DISPATCHING);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();

        // Only first request is sent, waveform stops after failure.
        assertThat(vibratorHelper.getEffectSegments()).isEmpty();
        assertThat(vibratorHelper.getAmplitudes()).isEmpty();
    }

    @Test
    public void vibrate_singleWaveformWithAdaptiveHapticsScaling_scalesAmplitudesProperly() {
        // No user settings scale.
        setUserSetting(Settings.System.RING_VIBRATION_INTENSITY,
                Vibrator.VIBRATION_INTENSITY_MEDIUM);
        mVibratorHelpers.get(VIBRATOR_ID).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);

        VibrationEffect effect = VibrationEffect.createWaveform(
                new long[]{5, 5, 5}, new int[]{1, 1, 1}, -1);
        mVibrationScaler.updateAdaptiveHapticsScale(USAGE_RINGTONE, 0.5f);
        CompletableFuture<Void> mRequestVibrationParamsFuture = CompletableFuture.completedFuture(
                null);
        HalVibration vibration = startThreadAndDispatcher(effect, mRequestVibrationParamsFuture,
                USAGE_RINGTONE);
        waitForCompletion();

        verify(mStatsLoggerMock, never()).logVibrationParamRequestTimeout(UID);
        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getEffectSegments())
                .containsExactly(expectedOneShot(15)).inOrder();
        List<Float> amplitudes = mVibratorHelpers.get(VIBRATOR_ID).getAmplitudes();
        for (int i = 0; i < amplitudes.size(); i++) {
            assertWithMessage("For amplitude index %s", i)
                    .that(amplitudes.get(i)).isLessThan(1 / 255f);
        }
    }

    @Test
    public void vibrate_withVibrationParamsRequestStalling_timeoutRequestAndApplyNoScaling() {
        // No user settings scale.
        setUserSetting(Settings.System.RING_VIBRATION_INTENSITY,
                Vibrator.VIBRATION_INTENSITY_MEDIUM);
        mVibratorHelpers.get(VIBRATOR_ID).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        VibrationEffect effect = VibrationEffect.createWaveform(
                new long[]{5, 5, 5}, new int[]{1, 1, 1}, -1);

        CompletableFuture<Void> neverCompletingFuture = new CompletableFuture<>();
        HalVibration vibration = startThreadAndDispatcher(effect, neverCompletingFuture,
                USAGE_RINGTONE);
        waitForCompletion();

        verify(mStatsLoggerMock).logVibrationParamRequestTimeout(UID);
        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getEffectSegments())
                .containsExactly(expectedOneShot(15)).inOrder();
        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getAmplitudes())
                .containsExactlyElementsIn(expectedAmplitudes(1, 1, 1)).inOrder();
    }

    @Test
    public void vibrate_singleVibratorRepeatingWaveform_runsVibrationUntilThreadCancelled()
            throws Exception {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);

        int[] amplitudes = new int[]{1, 2, 3};
        VibrationEffect effect = VibrationEffect.createWaveform(new long[]{5, 5, 5}, amplitudes, 0);
        HalVibration vibration = startThreadAndDispatcher(effect);

        assertThat(
                waitUntil(() -> vibratorHelper.getAmplitudes().size() > 2 * amplitudes.length,
                        TEST_TIMEOUT_MILLIS)).isTrue();
        // Vibration still running after 2 cycles.
        assertThat(mThread.isRunningVibrationId(vibration.id)).isTrue();
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isTrue();

        Vibration.EndInfo cancelVibrationInfo = new Vibration.EndInfo(Status.CANCELLED_SUPERSEDED,
                new CallerInfo(VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
                        /* uid= */ 1, /* deviceId= */ -1, /* opPkg= */ null, /* reason= */ null));
        mVibrationConductor.notifyCancelled(cancelVibrationInfo, /* immediate= */ false);
        waitForCompletion();
        assertThat(mThread.isRunningVibrationId(vibration.id)).isFalse();

        verify(mManagerHooks).noteVibratorOn(eq(UID), anyLong());
        verify(mManagerHooks).noteVibratorOff(eq(UID));
        verifyCallbacksTriggered(vibration, Status.CANCELLED_SUPERSEDED);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();

        List<Float> playedAmplitudes = vibratorHelper.getAmplitudes();
        assertThat(vibratorHelper.getEffectSegments()).isNotEmpty();
        assertThat(playedAmplitudes).isNotEmpty();

        for (int i = 0; i < playedAmplitudes.size(); i++) {
            assertWithMessage("For amplitude index %s", i)
                    .that(amplitudes[i % amplitudes.length] / 255f)
                    .isWithin(1e-5f).of(playedAmplitudes.get(i));
        }
    }

    @Test
    public void vibrate_singleVibratorRepeatingShortAlwaysOnWaveform_turnsVibratorOnForLonger()
            throws Exception {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);

        int[] amplitudes = new int[]{1, 2, 3};
        VibrationEffect effect = VibrationEffect.createWaveform(
                new long[]{1, 10, 100}, amplitudes, 0);
        HalVibration vibration = startThreadAndDispatcher(effect);

        assertThat(waitUntil(() -> !vibratorHelper.getAmplitudes().isEmpty(), TEST_TIMEOUT_MILLIS))
                .isTrue();
        mVibrationConductor.notifyCancelled(
                new Vibration.EndInfo(Status.CANCELLED_BY_USER), /* immediate= */ false);
        waitForCompletion();

        verifyCallbacksTriggered(vibration, Status.CANCELLED_BY_USER);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();
        assertThat(vibratorHelper.getEffectSegments())
                .containsExactly(expectedOneShot(5000)).inOrder();
    }

    @Test
    public void vibrate_singleVibratorPatternWithZeroDurationSteps_skipsZeroDurationSteps() {
        mVibratorHelpers.get(VIBRATOR_ID).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);

        VibrationEffect effect = VibrationEffect.createWaveform(
                /* timings= */ new long[]{0, 100, 50, 100, 0, 0, 0, 50}, /* repeat= */ -1);
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mManagerHooks).noteVibratorOn(eq(UID), eq(300L));
        verify(mManagerHooks).noteVibratorOff(eq(UID));

        verifyCallbacksTriggered(vibration, Status.FINISHED);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();

        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getEffectSegments())
                .containsExactlyElementsIn(expectedOneShots(100L, 150L)).inOrder();
    }

    @Test
    public void vibrate_singleVibratorPatternWithZeroDurationAndAmplitude_skipsZeroDurationSteps() {
        mVibratorHelpers.get(VIBRATOR_ID).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);

        int[] amplitudes = new int[]{1, 2, 0, 3, 4, 5, 0, 6};
        VibrationEffect effect = VibrationEffect.createWaveform(
                /* timings= */ new long[]{0, 100, 0, 50, 50, 0, 100, 50}, amplitudes,
                /* repeat= */ -1);
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mManagerHooks).noteVibratorOn(eq(UID), eq(350L));
        verify(mManagerHooks).noteVibratorOff(eq(UID));

        verifyCallbacksTriggered(vibration, Status.FINISHED);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();

        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getEffectSegments())
                .containsExactlyElementsIn(expectedOneShots(200L, 50L)).inOrder();
    }

    @LargeTest
    @Test
    public void vibrate_singleVibratorRepeatingPatternWithZeroDurationSteps_repeatsEffectCorrectly()
            throws Exception {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);

        VibrationEffect effect = VibrationEffect.createWaveform(
                /* timings= */ new long[]{0, 200, 50, 100, 0, 50, 50, 100}, /* repeat= */ 0);
        HalVibration vibration = startThreadAndDispatcher(effect);
        // We are expect this test to repeat the vibration effect twice, which would result in 5
        // segments being played:
        // 200ms ON
        // 150ms ON (100ms + 50ms, skips 0ms)
        // 300ms ON (100ms + 200ms looping to the start and skipping first 0ms)
        // 150ms ON (100ms + 50ms, skips 0ms)
        // 300ms ON (100ms + 200ms looping to the start and skipping first 0ms)
        assertThat(waitUntil(() -> vibratorHelper.getEffectSegments().size() >= 5,
                5000L + TEST_TIMEOUT_MILLIS)).isTrue();
        mVibrationConductor.notifyCancelled(
                new Vibration.EndInfo(Status.CANCELLED_BY_USER), /* immediate= */ false);
        waitForCompletion();

        verifyCallbacksTriggered(vibration, Status.CANCELLED_BY_USER);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();

        assertThat(
                mVibratorHelpers.get(VIBRATOR_ID).getEffectSegments().subList(0, 5))
                .containsExactlyElementsIn(expectedOneShots(200L, 150L, 300L, 150L, 300L))
                .inOrder();
    }

    @Test
    public void vibrate_singleVibratorPatternWithCallbackDelay_oldCallbacksIgnored() {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setCompletionCallbackLatency(100); // 100ms delay to notify service.
        vibratorHelper.setCapabilities(IVibrator.CAP_ON_CALLBACK, IVibrator.CAP_AMPLITUDE_CONTROL);

        VibrationEffect effect = VibrationEffect.createWaveform(
                /* timings= */ new long[]{0, 200, 50, 400}, /* repeat= */ -1);
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion(800 + TEST_TIMEOUT_MILLIS); // 200 + 50 + 400 + 100ms delay

        verifyCallbacksTriggered(vibration, Status.FINISHED);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();

        verify(mHalCallbacks).onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), eq(1L));
        // Step id = 2 skipped by the 50ms OFF step after the 200ms ON step.
        verify(mHalCallbacks).onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), eq(3L));

        // First callback ignored, did not cause the vibrator to turn back on during the 400ms step.
        assertThat(vibratorHelper.getEffectSegments())
                .containsExactlyElementsIn(expectedOneShots(200L, 400L)).inOrder();
    }

    @Test
    public void vibrate_singleVibratorRepeatingPwle_generatesLargestPwles() throws Exception {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setCapabilities(IVibrator.CAP_GET_RESONANT_FREQUENCY,
                IVibrator.CAP_FREQUENCY_CONTROL, IVibrator.CAP_COMPOSE_PWLE_EFFECTS);
        vibratorHelper.setMinFrequency(100);
        vibratorHelper.setResonantFrequency(150);
        vibratorHelper.setFrequencyResolution(50);
        vibratorHelper.setMaxAmplitudes(1, 1, 1);
        vibratorHelper.setPwleSizeMax(10);

        VibrationEffect effect = VibrationEffect.startWaveform(targetAmplitude(1))
                // Very long segment so thread will be cancelled after first PWLE is triggered.
                .addTransition(Duration.ofMillis(100), targetFrequency(100))
                .build();
        VibrationEffect repeatingEffect = VibrationEffect.startComposition()
                .repeatEffectIndefinitely(effect)
                .compose();
        HalVibration vibration = startThreadAndDispatcher(repeatingEffect);

        assertThat(waitUntil(() -> !vibratorHelper.getEffectSegments().isEmpty(),
                TEST_TIMEOUT_MILLIS)).isTrue();
        mVibrationConductor.notifyCancelled(
                new Vibration.EndInfo(Status.CANCELLED_BY_USER), /* immediate= */ false);
        waitForCompletion();

        // PWLE size max was used to generate a single vibrate call with 10 segments.
        verifyCallbacksTriggered(vibration, Status.CANCELLED_BY_USER);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();
        assertThat(vibratorHelper.getEffectSegments()).hasSize(10);
    }

    @Test
    public void vibrate_singleVibratorRepeatingPrimitives_generatesLargestComposition()
            throws Exception {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        vibratorHelper.setSupportedPrimitives(PRIMITIVE_CLICK);
        vibratorHelper.setCompositionSizeMax(10);

        VibrationEffect effect = VibrationEffect.startComposition()
                // Very long delay so thread will be cancelled after first PWLE is triggered.
                .addPrimitive(PRIMITIVE_CLICK, 1f, 100)
                .compose();
        VibrationEffect repeatingEffect = VibrationEffect.startComposition()
                .repeatEffectIndefinitely(effect)
                .compose();
        HalVibration vibration = startThreadAndDispatcher(repeatingEffect);

        assertThat(waitUntil(() -> !vibratorHelper.getEffectSegments().isEmpty(),
                TEST_TIMEOUT_MILLIS)).isTrue();
        mVibrationConductor.notifyCancelled(
                new Vibration.EndInfo(Status.CANCELLED_BY_SCREEN_OFF), /* immediate= */ false);
        waitForCompletion();

        // Composition size max was used to generate a single vibrate call with 10 primitives.
        verifyCallbacksTriggered(vibration, Status.CANCELLED_BY_SCREEN_OFF);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();
        assertThat(vibratorHelper.getEffectSegments()).hasSize(10);
    }

    @Test
    public void vibrate_singleVibratorRepeatingLongAlwaysOnWaveform_turnsVibratorOnForACycle()
            throws Exception {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);

        int[] amplitudes = new int[]{1, 2, 3};
        VibrationEffect effect = VibrationEffect.createWaveform(
                new long[]{5000, 500, 50}, amplitudes, 0);
        HalVibration vibration = startThreadAndDispatcher(effect);

        assertThat(waitUntil(() -> !vibratorHelper.getAmplitudes().isEmpty(), TEST_TIMEOUT_MILLIS))
                .isTrue();
        mVibrationConductor.notifyCancelled(
                new Vibration.EndInfo(Status.CANCELLED_BY_USER), /* immediate= */ false);
        waitForCompletion();

        verifyCallbacksTriggered(vibration, Status.CANCELLED_BY_USER);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();
        assertThat(vibratorHelper.getEffectSegments())
                .containsExactly(expectedOneShot(5550)).inOrder();
    }

    @LargeTest
    @Test
    public void vibrate_singleVibratorRepeatingAlwaysOnWaveform_turnsVibratorBackOn()
            throws Exception {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        int expectedOnDuration = SetAmplitudeVibratorStep.REPEATING_EFFECT_ON_DURATION;

        VibrationEffect effect = VibrationEffect.createWaveform(
                /* timings= */ new long[]{expectedOnDuration - 100, 50},
                /* amplitudes= */ new int[]{1, 2}, /* repeat= */ 0);
        HalVibration vibration = startThreadAndDispatcher(effect);

        assertThat(waitUntil(() -> vibratorHelper.getEffectSegments().size() > 1,
                expectedOnDuration + TEST_TIMEOUT_MILLIS)).isTrue();
        mVibrationConductor.notifyCancelled(
                new Vibration.EndInfo(Status.CANCELLED_BY_USER), /* immediate= */ false);
        waitForCompletion();

        verifyCallbacksTriggered(vibration, Status.CANCELLED_BY_USER);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();
        List<VibrationEffectSegment> segments = vibratorHelper.getEffectSegments();
        // First time, turn vibrator ON for the expected fixed duration.
        assertThat(segments.get(0).getDuration()).isEqualTo(expectedOnDuration);
        // Vibrator turns off in the middle of the second execution of the first step. Expect it to
        // be turned back ON at least for the fixed duration + the remaining duration of the step.
        assertThat(segments.get(1).getDuration()).isAtLeast(expectedOnDuration);
        // Set amplitudes for a cycle {1, 2}, start second loop then turn it back on to same value.
        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getAmplitudes().subList(0, 4))
                .containsExactlyElementsIn(expectedAmplitudes(1, 2, 1, 1))
                .inOrder();
    }

    @Test
    public void vibrate_singleVibratorComposedCancel_cancelsVibrationImmediately()
            throws Exception {
        mVibratorHelpers.get(VIBRATOR_ID).setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        mVibratorHelpers.get(VIBRATOR_ID).setSupportedPrimitives(PRIMITIVE_CLICK);

        VibrationEffect effect = VibrationEffect.startComposition()
                .addPrimitive(PRIMITIVE_CLICK, 1f, 100)
                .addPrimitive(PRIMITIVE_CLICK, 1f, 100)
                .addPrimitive(PRIMITIVE_CLICK, 1f, 100)
                .compose();
        HalVibration vibration = startThreadAndDispatcher(effect);

        assertThat(waitUntil(() -> mVibrators.get(VIBRATOR_ID).isVibrating(),
                TEST_TIMEOUT_MILLIS)).isTrue();
        assertThat(mThread.isRunningVibrationId(vibration.id)).isTrue();

        // Run cancel in a separate thread so if VibrationThread.cancel blocks then this test should
        // fail at waitForCompletion(vibrationThread) if the vibration not cancelled immediately.
        Thread cancellingThread =
                new Thread(() -> mVibrationConductor.notifyCancelled(
                        new Vibration.EndInfo(Status.CANCELLED_BY_SETTINGS_UPDATE),
                        /* immediate= */ false));
        cancellingThread.start();

        waitForCompletion(TEST_IMMEDIATE_CANCEL_TIMEOUT_MILLIS);
        cancellingThread.join();

        verifyCallbacksTriggered(vibration, Status.CANCELLED_BY_SETTINGS_UPDATE);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void vibrate_singleVibratorVendorEffectCancel_cancelsVibrationImmediately()
            throws Exception {
        mVibratorHelpers.get(VIBRATOR_ID).setCapabilities(IVibrator.CAP_PERFORM_VENDOR_EFFECTS);
        // Set long vendor effect duration to check it gets cancelled quickly.
        mVibratorHelpers.get(VIBRATOR_ID).setVendorEffectDuration(10 * TEST_TIMEOUT_MILLIS);

        VibrationEffect effect = VibrationEffect.createVendorEffect(createTestVendorData());
        HalVibration vibration = startThreadAndDispatcher(effect);

        assertThat(waitUntil(() -> mVibrators.get(VIBRATOR_ID).isVibrating(),
                TEST_TIMEOUT_MILLIS)).isTrue();
        assertThat(mThread.isRunningVibrationId(vibration.id)).isTrue();

        // Run cancel in a separate thread so if VibrationThread.cancel blocks then this test should
        // fail at waitForCompletion(vibrationThread) if the vibration not cancelled immediately.
        Thread cancellingThread =
                new Thread(() -> mVibrationConductor.notifyCancelled(
                        new Vibration.EndInfo(Status.CANCELLED_BY_SETTINGS_UPDATE),
                        /* immediate= */ false));
        cancellingThread.start();

        waitForCompletion(TEST_IMMEDIATE_CANCEL_TIMEOUT_MILLIS);
        cancellingThread.join();

        verifyCallbacksTriggered(vibration, Status.CANCELLED_BY_SETTINGS_UPDATE);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();
    }

    @Test
    public void vibrate_singleVibratorWaveformCancel_cancelsVibrationImmediately()
            throws Exception {
        mVibratorHelpers.get(VIBRATOR_ID).setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);

        VibrationEffect effect = VibrationEffect.createWaveform(new long[]{100}, new int[]{100}, 0);
        HalVibration vibration = startThreadAndDispatcher(effect);

        assertThat(waitUntil(() -> mVibrators.get(VIBRATOR_ID).isVibrating(),
                TEST_TIMEOUT_MILLIS)).isTrue();
        assertThat(mThread.isRunningVibrationId(vibration.id)).isTrue();

        // Run cancel in a separate thread so if VibrationThread.cancel blocks then this test should
        // fail at waitForCompletion(vibrationThread) if the vibration not cancelled immediately.
        Thread cancellingThread =
                new Thread(() -> mVibrationConductor.notifyCancelled(
                        new Vibration.EndInfo(Status.CANCELLED_BY_SCREEN_OFF),
                        /* immediate= */ false));
        cancellingThread.start();

        waitForCompletion(TEST_IMMEDIATE_CANCEL_TIMEOUT_MILLIS);
        cancellingThread.join();

        verifyCallbacksTriggered(vibration, Status.CANCELLED_BY_SCREEN_OFF);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();
    }

    @Test
    public void vibrate_singleVibratorPrebaked_runsVibration() {
        mVibratorHelpers.get(1).setSupportedEffects(VibrationEffect.EFFECT_THUD);

        VibrationEffect effect = VibrationEffect.get(VibrationEffect.EFFECT_THUD);
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mManagerHooks).noteVibratorOn(eq(UID), eq(20L));
        verify(mManagerHooks).noteVibratorOff(eq(UID));
        verify(mHalCallbacks).onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.FINISHED);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();

        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getEffectSegments())
                .containsExactly(expectedPrebaked(VibrationEffect.EFFECT_THUD)).inOrder();
    }

    @Test
    @DisableFlags(Flags.FLAG_REMOVE_HIDL_SUPPORT)
    public void vibrate_singleVibratorPrebakedAndUnsupportedEffectWithFallback_runsFallback() {
        mVibratorHelpers.get(VIBRATOR_ID).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);

        VibrationEffect fallback = VibrationEffect.createOneShot(10, 100);
        HalVibration vibration = createVibration(CombinedVibration.createParallel(
                VibrationEffect.get(EFFECT_CLICK)));
        vibration.fillFallbacks(unused -> fallback);
        startThreadAndDispatcher(vibration);
        waitForCompletion();

        verify(mManagerHooks).noteVibratorOn(eq(UID), eq(10L));
        verify(mManagerHooks).noteVibratorOff(eq(UID));
        verify(mHalCallbacks).onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.FINISHED);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();

        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getEffectSegments())
                .containsExactly(expectedOneShot(10)).inOrder();
        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getAmplitudes())
                .containsExactlyElementsIn(expectedAmplitudes(100)).inOrder();
    }

    @Test
    @EnableFlags(Flags.FLAG_REMOVE_HIDL_SUPPORT)
    public void vibrate_singleVibratorPrebakedAndUnsupportedEffectWithFallback_runsOnlyFallback() {
        mFallbackEffects.put(EFFECT_CLICK, VibrationEffect.createOneShot(10, 100));
        mVibratorHelpers.get(VIBRATOR_ID).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);

        HalVibration vibration = createVibration(CombinedVibration.createParallel(
                VibrationEffect.get(EFFECT_CLICK)));
        startThreadAndDispatcher(vibration);
        waitForCompletion();

        verify(mManagerHooks).noteVibratorOn(eq(UID), eq(10L));
        verify(mManagerHooks).noteVibratorOff(eq(UID));
        verify(mHalCallbacks).onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.FINISHED);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();

        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getEffectSegments())
                .containsExactly(expectedOneShot(10)).inOrder();
        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getAmplitudes())
                .containsExactlyElementsIn(expectedAmplitudes(100)).inOrder();
    }

    @Test
    @EnableFlags(Flags.FLAG_REMOVE_HIDL_SUPPORT)
    public void vibrate_singleVibratorPrebakedAndUnsupportedEffectWithoutFallback_isUnsupported() {
        mFallbackEffects.put(EFFECT_CLICK, VibrationEffect.createOneShot(10, 100));
        mVibratorHelpers.get(VIBRATOR_ID).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);

        HalVibration vibration = createVibration(CombinedVibration.createParallel(
                VibrationEffect.get(EFFECT_CLICK, /* fallback= */ false)));
        startThreadAndDispatcher(vibration);
        waitForCompletion();

        verify(mManagerHooks, never()).noteVibratorOn(eq(UID), anyLong());
        verify(mManagerHooks, never()).noteVibratorOff(eq(UID));
        verifyCallbacksTriggered(vibration, Status.IGNORED_UNSUPPORTED);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();
        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getEffectSegments()).isEmpty();
    }

    @Test
    @EnableFlags(Flags.FLAG_REMOVE_HIDL_SUPPORT)
    public void vibrate_singleVibratorPrebakedAndUnsupportedEffect_ignoresVibration() {
        VibrationEffect effect = VibrationEffect.get(EFFECT_CLICK);
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mManagerHooks, never()).noteVibratorOn(eq(UID), anyLong());
        verify(mManagerHooks, never()).noteVibratorOff(eq(UID));
        verify(mHalCallbacks, never())
                .onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.IGNORED_UNSUPPORTED);
        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getEffectSegments()).isEmpty();
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void vibrate_singleVibratorVendorEffect_runsVibration() {
        mVibratorHelpers.get(VIBRATOR_ID).setCapabilities(IVibrator.CAP_PERFORM_VENDOR_EFFECTS);

        VibrationEffect effect = VibrationEffect.createVendorEffect(createTestVendorData());
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mManagerHooks).noteVibratorOn(eq(UID),
                eq(PerformVendorEffectVibratorStep.VENDOR_EFFECT_MAX_DURATION_MS));
        verify(mManagerHooks).noteVibratorOff(eq(UID));
        verify(mHalCallbacks).onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.FINISHED);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();

        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getVendorEffects())
                .containsExactly(effect).inOrder();
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void vibrate_singleVibratorVendorEffectFailed_returnsFailure() {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setCapabilities(IVibrator.CAP_PERFORM_VENDOR_EFFECTS);
        vibratorHelper.setVendorEffectsToFail();

        VibrationEffect effect = VibrationEffect.createVendorEffect(createTestVendorData());
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mManagerHooks, never()).noteVibratorOn(eq(UID),
                eq(PerformVendorEffectVibratorStep.VENDOR_EFFECT_MAX_DURATION_MS));
        verify(mManagerHooks, never()).noteVibratorOff(eq(UID));
        verifyCallbacksTriggered(vibration, Status.IGNORED_ERROR_DISPATCHING);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();

        assertThat(vibratorHelper.getVendorEffects()).isEmpty();
    }

    @Test
    public void vibrate_singleVibratorComposed_runsVibration() {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        vibratorHelper.setSupportedPrimitives(PRIMITIVE_CLICK, PRIMITIVE_TICK);

        VibrationEffect effect = VibrationEffect.startComposition()
                .addPrimitive(PRIMITIVE_CLICK, 1f)
                .addPrimitive(PRIMITIVE_TICK, 0.5f)
                .compose();
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mManagerHooks).noteVibratorOn(eq(UID), eq(40L));
        verify(mManagerHooks).noteVibratorOff(eq(UID));
        verify(mHalCallbacks).onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.FINISHED);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();
        assertThat(vibratorHelper.getEffectSegments())
                .containsExactly(
                        expectedPrimitive(PRIMITIVE_CLICK, 1, 0),
                        expectedPrimitive(PRIMITIVE_TICK, 0.5f, 0))
                .inOrder();
    }

    @Test
    @DisableFlags(Flags.FLAG_PRIMITIVE_COMPOSITION_ABSOLUTE_DELAY)
    public void vibrate_singleVibratorComposedAndNoCapability_triggersHalAndReturnsUnsupported() {
        VibrationEffect effect = VibrationEffect.startComposition()
                .addPrimitive(PRIMITIVE_CLICK, 1f)
                .compose();
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mManagerHooks).noteVibratorOn(eq(UID), eq(0L));
        verify(mManagerHooks, never()).noteVibratorOff(eq(UID));
        verify(mHalCallbacks, never())
                .onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.IGNORED_UNSUPPORTED);
        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getEffectSegments()).isEmpty();
    }

    @Test
    @EnableFlags(Flags.FLAG_PRIMITIVE_COMPOSITION_ABSOLUTE_DELAY)
    public void vibrate_singleVibratorComposedAndNoCapability_ignoresVibration() {
        VibrationEffect effect = VibrationEffect.startComposition()
                .addPrimitive(PRIMITIVE_CLICK, 1f)
                .compose();
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mManagerHooks, never()).noteVibratorOn(eq(UID), anyLong());
        verify(mManagerHooks, never()).noteVibratorOff(eq(UID));
        verify(mHalCallbacks, never())
                .onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.IGNORED_UNSUPPORTED);
        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getEffectSegments()).isEmpty();
    }

    @Test
    public void vibrate_singleVibratorComposedFailed_returnsFailureAndStopsVibration() {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        vibratorHelper.setSupportedPrimitives(PRIMITIVE_CLICK, PRIMITIVE_TICK);
        vibratorHelper.setCompositionSizeMax(1);
        vibratorHelper.setPrimitivesToFail();

        VibrationEffect effect = VibrationEffect.startComposition()
                .addPrimitive(PRIMITIVE_CLICK, 1f)
                .addPrimitive(PRIMITIVE_TICK, 0.5f)
                .compose();
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mManagerHooks, never()).noteVibratorOn(eq(UID), eq(40L));
        verify(mManagerHooks, never()).noteVibratorOff(eq(UID));
        verifyCallbacksTriggered(vibration, Status.IGNORED_ERROR_DISPATCHING);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();
        assertThat(vibratorHelper.getEffectSegments()).isEmpty();
    }

    @Test
    public void vibrate_singleVibratorLargeComposition_splitsVibratorComposeCalls() {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        vibratorHelper.setSupportedPrimitives(PRIMITIVE_CLICK, PRIMITIVE_TICK, PRIMITIVE_SPIN);
        vibratorHelper.setCompositionSizeMax(2);

        VibrationEffect effect = VibrationEffect.startComposition()
                .addPrimitive(PRIMITIVE_CLICK, 1f)
                .addPrimitive(PRIMITIVE_TICK, 0.5f)
                .addPrimitive(PRIMITIVE_SPIN, 0.8f)
                .compose();
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verifyCallbacksTriggered(vibration, Status.FINISHED);
        // Vibrator compose called twice.
        verify(mHalCallbacks, times(2))
                .onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        assertThat(vibratorHelper.getEffectSegments()).hasSize(3);
    }

    @Test
    @DisableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void vibrate_singleVibratorComposedEffects_runsDifferentVibrations() {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setSupportedEffects(EFFECT_CLICK);
        vibratorHelper.setSupportedPrimitives(PRIMITIVE_CLICK, PRIMITIVE_TICK);
        vibratorHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL,
                IVibrator.CAP_GET_RESONANT_FREQUENCY, IVibrator.CAP_FREQUENCY_CONTROL,
                IVibrator.CAP_COMPOSE_EFFECTS, IVibrator.CAP_COMPOSE_PWLE_EFFECTS);
        vibratorHelper.setMinFrequency(100);
        vibratorHelper.setResonantFrequency(150);
        vibratorHelper.setFrequencyResolution(50);
        vibratorHelper.setMaxAmplitudes(
                0.5f /* 100Hz*/, 1 /* 150Hz */, 0.6f /* 200Hz */);

        VibrationEffect effect = VibrationEffect.startComposition()
                .addEffect(VibrationEffect.createOneShot(10, 100))
                .addPrimitive(PRIMITIVE_CLICK, 1f)
                .addPrimitive(PRIMITIVE_TICK, 0.5f)
                .addEffect(VibrationEffect.get(EFFECT_CLICK))
                .addEffect(VibrationEffect.startWaveform()
                        .addTransition(Duration.ofMillis(10),
                                targetAmplitude(1), targetFrequency(100))
                        .addTransition(Duration.ofMillis(20), targetFrequency(120))
                        .build())
                .addEffect(VibrationEffect.get(EFFECT_CLICK))
                .compose();
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        // Use first duration the vibrator is turned on since we cannot estimate the clicks.
        verify(mManagerHooks).noteVibratorOn(eq(UID), eq(10L));
        verify(mManagerHooks).noteVibratorOff(eq(UID));
        verify(mHalCallbacks, times(5))
                .onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.FINISHED);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();
        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getEffectSegments())
                .containsExactly(
                        expectedOneShot(10),
                        expectedPrimitive(PRIMITIVE_CLICK, 1, 0),
                        expectedPrimitive(PRIMITIVE_TICK, 0.5f, 0),
                        expectedPrebaked(EFFECT_CLICK),
                        expectedRamp(/* startAmplitude= */ 0, /* endAmplitude= */ 0.5f,
                                /* startFrequencyHz= */ 150, /* endFrequencyHz= */ 100,
                                /* duration= */ 10),
                        expectedRamp(/* startAmplitude= */ 0.5f, /* endAmplitude= */ 0.7f,
                                /* startFrequencyHz= */ 100, /* endFrequencyHz= */ 120,
                                /* duration= */ 20),
                        expectedPrebaked(EFFECT_CLICK))
                .inOrder();
        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getAmplitudes())
                .containsExactlyElementsIn(expectedAmplitudes(100)).inOrder();
    }

    @Test
    @DisableFlags(Flags.FLAG_REMOVE_HIDL_SUPPORT)
    public void vibrate_singleVibratorComposedWithFallback_replacedInTheMiddleOfComposition() {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setSupportedEffects(EFFECT_CLICK);
        vibratorHelper.setSupportedPrimitives(PRIMITIVE_CLICK, PRIMITIVE_TICK);
        vibratorHelper.setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);

        VibrationEffect fallback = VibrationEffect.createOneShot(10, 100);
        VibrationEffect effect = VibrationEffect.startComposition()
                .addEffect(VibrationEffect.get(EFFECT_CLICK))
                .addPrimitive(PRIMITIVE_CLICK, 1f)
                .addEffect(VibrationEffect.get(EFFECT_TICK))
                .addPrimitive(PRIMITIVE_TICK, 0.5f)
                .compose();
        HalVibration vibration = createVibration(CombinedVibration.createParallel(effect));
        vibration.fillFallbacks(unused -> fallback);
        startThreadAndDispatcher(vibration);
        waitForCompletion();

        // Use first duration the vibrator is turned on since we cannot estimate the clicks.
        verify(mManagerHooks).noteVibratorOn(eq(UID), anyLong());
        verify(mManagerHooks).noteVibratorOff(eq(UID));
        verify(mHalCallbacks, times(4))
                .onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.FINISHED);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();

        List<VibrationEffectSegment> segments =
                mVibratorHelpers.get(VIBRATOR_ID).getEffectSegments();
        assertWithMessage("Wrong segments: %s", segments).that(segments.size()).isGreaterThan(3);
        assertThat(segments.get(0)).isInstanceOf(PrebakedSegment.class);
        assertThat(segments.get(1)).isInstanceOf(PrimitiveSegment.class);
        for (int i = 2; i < segments.size() - 1; i++) {
            // One or more step segments as fallback for the EFFECT_TICK.
            assertWithMessage("For segment index %s", i)
                    .that(segments.get(i)).isInstanceOf(StepSegment.class);
        }
        assertThat(segments.get(segments.size() - 1)).isInstanceOf(PrimitiveSegment.class);
    }

    @Test
    @EnableFlags(Flags.FLAG_REMOVE_HIDL_SUPPORT)
    public void vibrate_singleVibratorComposedWithFallback_playsOnlyFallbacks() {
        mFallbackEffects.put(EFFECT_CLICK, VibrationEffect.createOneShot(10, 100));
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setSupportedEffects(EFFECT_TICK);
        vibratorHelper.setSupportedPrimitives(PRIMITIVE_CLICK, PRIMITIVE_TICK);
        vibratorHelper.setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS,
                IVibrator.CAP_AMPLITUDE_CONTROL);

        VibrationEffect effect = VibrationEffect.startComposition()
                .addEffect(VibrationEffect.get(EFFECT_CLICK))
                .addPrimitive(PRIMITIVE_CLICK, 1f)
                .addEffect(VibrationEffect.get(EFFECT_TICK))
                .addPrimitive(PRIMITIVE_TICK, 0.5f)
                .compose();
        HalVibration vibration = createVibration(CombinedVibration.createParallel(effect));
        startThreadAndDispatcher(vibration);
        waitForCompletion();

        // Use first duration the vibrator is turned on since we cannot estimate the clicks.
        verify(mManagerHooks).noteVibratorOn(eq(UID), anyLong());
        verify(mManagerHooks).noteVibratorOff(eq(UID));
        verify(mHalCallbacks, times(4))
                .onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.FINISHED);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();

        List<VibrationEffectSegment> segments = vibratorHelper.getEffectSegments();
        assertWithMessage("Wrong segments: %s", segments).that(segments).hasSize(4);
        assertThat(segments.get(0)).isInstanceOf(StepSegment.class);
        assertThat(segments.get(1)).isInstanceOf(PrimitiveSegment.class);
        assertThat(segments.get(2)).isInstanceOf(PrebakedSegment.class);
        assertThat(segments.get(3)).isInstanceOf(PrimitiveSegment.class);
        assertThat(vibratorHelper.getAmplitudes())
                .containsExactlyElementsIn(expectedAmplitudes(100)).inOrder();
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void vibrate_singleVibratorPwle_runsComposePwleV2() {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setCapabilities(IVibrator.CAP_GET_RESONANT_FREQUENCY,
                IVibrator.CAP_FREQUENCY_CONTROL, IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2);
        vibratorHelper.setResonantFrequency(150);
        vibratorHelper.setFrequenciesHz(new float[]{30f, 50f, 100f, 120f, 150f});
        vibratorHelper.setOutputAccelerationsGs(new float[]{0.3f, 0.5f, 1.0f, 0.8f, 0.6f});
        vibratorHelper.setMaxEnvelopeEffectSize(10);
        vibratorHelper.setMinEnvelopeEffectControlPointDurationMillis(20);

        VibrationEffect effect = new VibrationEffect.WaveformEnvelopeBuilder()
                .addControlPoint(/*amplitude=*/ 0.1f, /*frequencyHz=*/ 60f, /*durationMillis=*/ 20)
                .addControlPoint(/*amplitude=*/ 0.3f, /*frequencyHz=*/ 100f, /*durationMillis=*/ 30)
                .addControlPoint(/*amplitude=*/ 0.4f, /*frequencyHz=*/ 120f, /*durationMillis=*/ 20)
                .addControlPoint(/*amplitude=*/ 0.0f, /*frequencyHz=*/ 120f, /*durationMillis=*/ 30)
                .build();
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mManagerHooks).noteVibratorOn(eq(UID), eq(100L));
        verify(mManagerHooks).noteVibratorOff(eq(UID));
        verify(mHalCallbacks).onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.FINISHED);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();
        assertThat(vibratorHelper.getEffectPwlePoints())
                .containsExactly(
                        expectedPwle(0.0f, 60f, 0),
                        expectedPwle(0.1f, 60f, 20),
                        expectedPwle(0.3f, 100f, 30),
                        expectedPwle(0.4f, 120f, 20),
                        expectedPwle(0.0f, 120f, 30))
                .inOrder();

    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void vibrate_singleVibratorBasicPwle_runsComposePwleV2() {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setCapabilities(IVibrator.CAP_GET_RESONANT_FREQUENCY,
                IVibrator.CAP_FREQUENCY_CONTROL, IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2);
        vibratorHelper.setResonantFrequency(150);
        vibratorHelper.setFrequenciesHz(new float[]{50f, 100f, 120f, 150f});
        vibratorHelper.setOutputAccelerationsGs(new float[]{0.05f, 1.0f, 3.0f, 2.0f});
        vibratorHelper.setMaxEnvelopeEffectSize(10);
        vibratorHelper.setMinEnvelopeEffectControlPointDurationMillis(20);

        VibrationEffect effect = new VibrationEffect.BasicEnvelopeBuilder()
                .setInitialSharpness(/*initialSharpness=*/ 1.0f)
                .addControlPoint(/*intensity=*/ 1.0f, /*sharpness=*/ 1.0f, /*durationMillis=*/ 20)
                .addControlPoint(/*intensity=*/ 1.0f, /*sharpness=*/ 1.0f, /*durationMillis=*/ 100)
                .addControlPoint(/*intensity=*/ 0.0f, /*sharpness=*/ 1.0f, /*durationMillis=*/ 100)
                .build();

        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mManagerHooks).noteVibratorOn(eq(UID), eq(220L));
        verify(mManagerHooks).noteVibratorOff(eq(UID));
        verify(mHalCallbacks).onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.FINISHED);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();
        assertThat(vibratorHelper.getEffectPwlePoints())
                .containsExactly(
                        expectedPwle(0.0f, 150f, 0),
                        expectedPwle(1.0f, 150f, 20),
                        expectedPwle(1.0f, 150f, 100),
                        expectedPwle(0.0f, 150f, 100))
                .inOrder();

    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void vibrate_singleVibratorPwle_withInitialFrequency_runsComposePwleV2() {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setCapabilities(IVibrator.CAP_GET_RESONANT_FREQUENCY,
                IVibrator.CAP_FREQUENCY_CONTROL, IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2);
        vibratorHelper.setResonantFrequency(150);
        vibratorHelper.setFrequenciesHz(new float[]{30f, 50f, 100f, 120f, 150f});
        vibratorHelper.setOutputAccelerationsGs(new float[]{0.3f, 0.5f, 1.0f, 0.8f, 0.6f});
        vibratorHelper.setMaxEnvelopeEffectSize(10);
        vibratorHelper.setMinEnvelopeEffectControlPointDurationMillis(20);

        VibrationEffect effect = new VibrationEffect.WaveformEnvelopeBuilder()
                .setInitialFrequencyHz(/*initialFrequencyHz=*/ 30)
                .addControlPoint(/*amplitude=*/ 0.1f, /*frequencyHz=*/ 60f, /*durationMillis=*/ 20)
                .addControlPoint(/*amplitude=*/ 0.3f, /*frequencyHz=*/ 100f, /*durationMillis=*/ 30)
                .addControlPoint(/*amplitude=*/ 0.4f, /*frequencyHz=*/ 120f, /*durationMillis=*/ 20)
                .addControlPoint(/*amplitude=*/ 0.0f, /*frequencyHz=*/ 120f, /*durationMillis=*/ 30)
                .build();

        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mManagerHooks).noteVibratorOn(eq(UID), eq(100L));
        verify(mManagerHooks).noteVibratorOff(eq(UID));
        verify(mHalCallbacks).onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.FINISHED);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();
        assertThat(vibratorHelper.getEffectPwlePoints())
                .containsExactly(
                        expectedPwle(0.0f, 30f, 0),
                        expectedPwle(0.1f, 60f, 20),
                        expectedPwle(0.3f, 100f, 30),
                        expectedPwle(0.4f, 120f, 20),
                        expectedPwle(0.0f, 120f, 30))
                .inOrder();
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void vibrate_singleVibratorPwle_TooManyControlPoints_splitsAndRunsComposePwleV2() {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setCapabilities(IVibrator.CAP_GET_RESONANT_FREQUENCY,
                IVibrator.CAP_FREQUENCY_CONTROL, IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2);
        vibratorHelper.setResonantFrequency(150);
        vibratorHelper.setFrequenciesHz(new float[]{30f, 50f, 100f, 120f, 150f});
        vibratorHelper.setOutputAccelerationsGs(new float[]{0.3f, 0.5f, 1.0f, 0.8f, 0.6f});
        vibratorHelper.setMaxEnvelopeEffectSize(3);
        vibratorHelper.setMinEnvelopeEffectControlPointDurationMillis(20);

        VibrationEffect effect = new VibrationEffect.WaveformEnvelopeBuilder()
                .addControlPoint(/*amplitude=*/ 0.8f, /*frequencyHz=*/ 100f, /*durationMillis=*/ 30)
                .addControlPoint(/*amplitude=*/ 0.0f, /*frequencyHz=*/ 100f, /*durationMillis=*/ 30)
                // Waveform will be split here, after vibration goes to zero amplitude
                .addControlPoint(/*amplitude=*/ 0.9f, /*frequencyHz=*/ 100f, /*durationMillis=*/ 30)
                .addControlPoint(/*amplitude=*/ 0.4f, /*frequencyHz=*/ 100f, /*durationMillis=*/ 30)
                // Waveform will be split here at lowest amplitude.
                .addControlPoint(/*amplitude=*/ 0.6f, /*frequencyHz=*/ 100f, /*durationMillis=*/ 30)
                .addControlPoint(/*amplitude=*/ 0.7f, /*frequencyHz=*/ 100f, /*durationMillis=*/ 30)
                .build();
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verifyCallbacksTriggered(vibration, Status.FINISHED);
        // Vibrator compose called 3 times with 2 segments instead of 2 times with 3 segments.
        // Using best split points instead of max-packing PWLEs.
        verify(mHalCallbacks, times(3))
                .onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();
        assertThat(vibratorHelper.getEffectPwlePoints())
                .containsExactly(
                        expectedPwle(0.0f, 100f, 0),
                        expectedPwle(0.8f, 100f, 30),
                        expectedPwle(0.0f, 100f, 30),
                        expectedPwle(0.9f, 100f, 0),
                        expectedPwle(0.4f, 100f, 30),
                        expectedPwle(0.6f, 100f, 0),
                        expectedPwle(0.7f, 100f, 30))
                .inOrder();
    }

    @Test
    @EnableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void vibrate_singleVibratorPwleFailed_returnsFailureAndStopsVibration() {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setCapabilities(IVibrator.CAP_GET_RESONANT_FREQUENCY,
                IVibrator.CAP_FREQUENCY_CONTROL, IVibrator.CAP_COMPOSE_PWLE_EFFECTS_V2);
        vibratorHelper.setResonantFrequency(150);
        vibratorHelper.setFrequenciesHz(new float[]{30f, 50f, 100f, 120f, 150f});
        vibratorHelper.setOutputAccelerationsGs(new float[]{0.3f, 0.5f, 1.0f, 0.8f, 0.6f});
        vibratorHelper.setMaxEnvelopeEffectSize(1);
        vibratorHelper.setMinEnvelopeEffectControlPointDurationMillis(20);
        vibratorHelper.setPwleV2ToFail();

        VibrationEffect effect = new VibrationEffect.WaveformEnvelopeBuilder()
                .addControlPoint(/*amplitude=*/ 0.8f, /*frequencyHz=*/ 100f, /*durationMillis=*/ 30)
                .addControlPoint(/*amplitude=*/ 0.0f, /*frequencyHz=*/ 100f, /*durationMillis=*/ 30)
                .build();
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verifyCallbacksTriggered(vibration, Status.IGNORED_ERROR_DISPATCHING);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();
        assertThat(vibratorHelper.getEffectPwlePoints()).isEmpty();
    }

    @Test
    @DisableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void vibrate_singleVibratorPwle_runsComposePwle() {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setCapabilities(IVibrator.CAP_GET_RESONANT_FREQUENCY,
                IVibrator.CAP_FREQUENCY_CONTROL, IVibrator.CAP_COMPOSE_PWLE_EFFECTS);
        vibratorHelper.setSupportedBraking(Braking.CLAB);
        vibratorHelper.setMinFrequency(100);
        vibratorHelper.setResonantFrequency(150);
        vibratorHelper.setFrequencyResolution(50);
        vibratorHelper.setMaxAmplitudes(
                0.5f /* 100Hz*/, 1 /* 150Hz */, 0.6f /* 200Hz */);

        VibrationEffect effect = VibrationEffect.startWaveform(targetAmplitude(1))
                .addSustain(Duration.ofMillis(10))
                .addTransition(Duration.ofMillis(20), targetAmplitude(0))
                .addTransition(Duration.ZERO, targetAmplitude(0.8f), targetFrequency(100))
                .addSustain(Duration.ofMillis(30))
                .addTransition(Duration.ofMillis(40), targetAmplitude(0.6f), targetFrequency(200))
                .build();
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mManagerHooks).noteVibratorOn(eq(UID), eq(100L));
        verify(mManagerHooks).noteVibratorOff(eq(UID));
        verify(mHalCallbacks).onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.FINISHED);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();
        assertThat(vibratorHelper.getEffectSegments())
                .containsExactly(
                        expectedRamp(/* amplitude= */ 1, /* frequencyHz= */ 150,
                                /* duration= */ 10),
                        expectedRamp(/* startAmplitude= */ 1, /* endAmplitude= */ 0,
                                /* startFrequencyHz= */ 150, /* endFrequencyHz= */ 150,
                                /* duration= */ 20),
                        expectedRamp(/* amplitude= */ 0.5f, /* frequencyHz= */ 100,
                                /* duration= */ 30),
                        expectedRamp(/* startAmplitude= */ 0.5f, /* endAmplitude= */ 0.6f,
                                /* startFrequencyHz= */ 100, /* endFrequencyHz= */ 200,
                                /* duration= */ 40))
                .inOrder();
    }

    @Test
    public void vibrate_singleVibratorLargePwle_splitsComposeCallWhenAmplitudeIsLowest() {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setCapabilities(IVibrator.CAP_GET_RESONANT_FREQUENCY,
                IVibrator.CAP_FREQUENCY_CONTROL, IVibrator.CAP_COMPOSE_PWLE_EFFECTS);
        vibratorHelper.setMinFrequency(100);
        vibratorHelper.setResonantFrequency(150);
        vibratorHelper.setFrequencyResolution(50);
        vibratorHelper.setMaxAmplitudes(1, 1, 1);
        vibratorHelper.setPwleSizeMax(3);

        VibrationEffect effect = VibrationEffect.startWaveform(targetAmplitude(1))
                .addSustain(Duration.ofMillis(10))
                .addTransition(Duration.ofMillis(20), targetAmplitude(0))
                // Waveform will be split here, after vibration goes to zero amplitude
                .addTransition(Duration.ZERO, targetAmplitude(0.8f), targetFrequency(100))
                .addSustain(Duration.ofMillis(30))
                .addTransition(Duration.ofMillis(40), targetAmplitude(0.6f), targetFrequency(200))
                // Waveform will be split here at lowest amplitude.
                .addTransition(Duration.ofMillis(40), targetAmplitude(0.7f), targetFrequency(200))
                .addTransition(Duration.ofMillis(40), targetAmplitude(0.6f), targetFrequency(200))
                .build();
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verifyCallbacksTriggered(vibration, Status.FINISHED);

        // Vibrator compose called 3 times with 2 segments instead of 2 times with 3 segments.
        // Using best split points instead of max-packing PWLEs.
        verify(mHalCallbacks, times(3))
                .onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        assertThat(vibratorHelper.getEffectSegments()).hasSize(6);
    }

    @Test
    public void vibrate_singleVibratorCancelled_vibratorStopped() throws Exception {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);

        VibrationEffect effect = VibrationEffect.createWaveform(new long[]{5}, new int[]{100}, 0);
        HalVibration vibration = startThreadAndDispatcher(effect);

        assertThat(waitUntil(() -> vibratorHelper.getAmplitudes().size() > 2, TEST_TIMEOUT_MILLIS))
                .isTrue();
        // Vibration still running after 2 cycles.
        assertThat(mThread.isRunningVibrationId(vibration.id)).isTrue();
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isTrue();

        mVibrationConductor.notifyCancelled(
                new Vibration.EndInfo(Status.CANCELLED_BINDER_DIED), /* immediate= */ false);
        waitForCompletion();
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();

        verifyCallbacksTriggered(vibration, Status.CANCELLED_BINDER_DIED);
    }

    @Test
    public void vibrate_singleVibrator_skipsSyncedCallbacks() {
        mVibratorHelpers.get(1).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);

        HalVibration vibration = startThreadAndDispatcher(VibrationEffect.createOneShot(10, 100));
        waitForCompletion();

        verifyCallbacksTriggered(vibration, Status.FINISHED);
        verify(mManagerHooks, never()).prepareSyncedVibration(anyLong(), any());
        verify(mManagerHooks, never()).triggerSyncedVibration(anyLong());
        verify(mManagerHooks, never()).cancelSyncedVibration();
    }

    @Test
    public void vibrate_multipleExistingAndMissingVibrators_vibratesOnlyExistingOnes() {
        mVibratorHelpers.get(1).setSupportedEffects(EFFECT_TICK);

        CombinedVibration effect = CombinedVibration.startParallel()
                .addVibrator(VIBRATOR_ID, VibrationEffect.get(EFFECT_TICK))
                .addVibrator(2, VibrationEffect.get(EFFECT_TICK))
                .combine();
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mManagerHooks).noteVibratorOn(eq(UID), eq(20L));
        verify(mManagerHooks).noteVibratorOff(eq(UID));
        verify(mHalCallbacks).onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        verify(mHalCallbacks, never()).onVibrationStepComplete(eq(2), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.FINISHED);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();

        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getEffectSegments())
                .containsExactly(expectedPrebaked(EFFECT_TICK)).inOrder();
    }

    @Test
    public void vibrate_multipleMono_runsSameEffectInAllVibrators() {
        mockVibrators(1, 2, 3);
        mVibratorHelpers.get(1).setSupportedEffects(EFFECT_CLICK);
        mVibratorHelpers.get(2).setSupportedEffects(EFFECT_CLICK);
        mVibratorHelpers.get(3).setSupportedEffects(EFFECT_CLICK);

        CombinedVibration effect = CombinedVibration.createParallel(
                VibrationEffect.get(EFFECT_CLICK));
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mManagerHooks).noteVibratorOn(eq(UID), eq(20L));
        verify(mManagerHooks).noteVibratorOff(eq(UID));
        verify(mHalCallbacks).onVibrationStepComplete(eq(1), eq(vibration.id), anyLong());
        verify(mHalCallbacks).onVibrationStepComplete(eq(2), eq(vibration.id), anyLong());
        verify(mHalCallbacks).onVibrationStepComplete(eq(3), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.FINISHED);
        assertThat(mVibrators.get(1).isVibrating()).isFalse();
        assertThat(mVibrators.get(2).isVibrating()).isFalse();
        assertThat(mVibrators.get(3).isVibrating()).isFalse();

        VibrationEffectSegment expected = expectedPrebaked(EFFECT_CLICK);
        assertThat(mVibratorHelpers.get(1).getEffectSegments())
                .containsExactly(expected).inOrder();
        assertThat(mVibratorHelpers.get(2).getEffectSegments())
                .containsExactly(expected).inOrder();
        assertThat(mVibratorHelpers.get(3).getEffectSegments())
                .containsExactly(expected).inOrder();
    }

    @Test
    public void vibrate_multipleStereo_runsVibrationOnRightVibrators() {
        mockVibrators(1, 2, 3, 4);
        mVibratorHelpers.get(1).setSupportedEffects(EFFECT_CLICK);
        mVibratorHelpers.get(2).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        mVibratorHelpers.get(3).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        mVibratorHelpers.get(4).setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        mVibratorHelpers.get(4).setSupportedPrimitives(PRIMITIVE_CLICK);

        VibrationEffect composed = VibrationEffect.startComposition()
                .addPrimitive(PRIMITIVE_CLICK)
                .compose();
        CombinedVibration effect = CombinedVibration.startParallel()
                .addVibrator(1, VibrationEffect.get(EFFECT_CLICK))
                .addVibrator(2, VibrationEffect.createOneShot(10, 100))
                .addVibrator(3, VibrationEffect.createWaveform(
                        new long[]{10, 10}, new int[]{1, 2}, -1))
                .addVibrator(4, composed)
                .combine();
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mManagerHooks).noteVibratorOn(eq(UID), eq(20L));
        verify(mManagerHooks).noteVibratorOff(eq(UID));
        verify(mHalCallbacks).onVibrationStepComplete(eq(1), eq(vibration.id), anyLong());
        verify(mHalCallbacks).onVibrationStepComplete(eq(2), eq(vibration.id), anyLong());
        verify(mHalCallbacks).onVibrationStepComplete(eq(3), eq(vibration.id), anyLong());
        verify(mHalCallbacks).onVibrationStepComplete(eq(4), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.FINISHED);
        assertThat(mVibrators.get(1).isVibrating()).isFalse();
        assertThat(mVibrators.get(2).isVibrating()).isFalse();
        assertThat(mVibrators.get(3).isVibrating()).isFalse();
        assertThat(mVibrators.get(4).isVibrating()).isFalse();

        assertThat(mVibratorHelpers.get(1).getEffectSegments())
                .containsExactly(expectedPrebaked(EFFECT_CLICK)).inOrder();
        assertThat(mVibratorHelpers.get(2).getEffectSegments())
                .containsExactly(expectedOneShot(10)).inOrder();
        assertThat(mVibratorHelpers.get(2).getAmplitudes())
                .containsExactlyElementsIn(expectedAmplitudes(100)).inOrder();
        assertThat(mVibratorHelpers.get(3).getEffectSegments())
                .containsExactly(expectedOneShot(20)).inOrder();
        assertThat(mVibratorHelpers.get(3).getAmplitudes())
                .containsExactlyElementsIn(expectedAmplitudes(1, 2)).inOrder();
        assertThat(mVibratorHelpers.get(4).getEffectSegments())
                .containsExactly(expectedPrimitive(PRIMITIVE_CLICK, 1, 0)).inOrder();
    }

    @Test
    @DisableFlags(Flags.FLAG_REMOVE_SEQUENTIAL_COMBINATION)
    public void vibrate_multipleSequential_runsVibrationInOrderWithDelays() {
        mockVibrators(1, 2, 3);
        mVibratorHelpers.get(1).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        mVibratorHelpers.get(2).setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        mVibratorHelpers.get(2).setSupportedPrimitives(PRIMITIVE_CLICK);
        mVibratorHelpers.get(3).setSupportedEffects(EFFECT_CLICK);

        VibrationEffect composed = VibrationEffect.startComposition()
                .addPrimitive(PRIMITIVE_CLICK)
                .compose();
        CombinedVibration effect = CombinedVibration.startSequential()
                .addNext(3, VibrationEffect.get(EFFECT_CLICK), /* delay= */ 50)
                .addNext(1, VibrationEffect.createOneShot(10, 100), /* delay= */ 50)
                .addNext(2, composed, /* delay= */ 50)
                .combine();
        HalVibration vibration = startThreadAndDispatcher(effect);

        waitForCompletion();
        InOrder verifier = inOrder(mHalCallbacks);
        verifier.verify(mHalCallbacks).onVibrationStepComplete(eq(3), eq(vibration.id), anyLong());
        verifier.verify(mHalCallbacks).onVibrationStepComplete(eq(1), eq(vibration.id), anyLong());
        verifier.verify(mHalCallbacks).onVibrationStepComplete(eq(2), eq(vibration.id), anyLong());

        InOrder batteryVerifier = inOrder(mManagerHooks);
        batteryVerifier.verify(mManagerHooks).noteVibratorOn(eq(UID), eq(20L));
        batteryVerifier.verify(mManagerHooks).noteVibratorOff(eq(UID));
        batteryVerifier.verify(mManagerHooks).noteVibratorOn(eq(UID), eq(10L));
        batteryVerifier.verify(mManagerHooks).noteVibratorOff(eq(UID));
        batteryVerifier.verify(mManagerHooks).noteVibratorOn(eq(UID), eq(20L));
        batteryVerifier.verify(mManagerHooks).noteVibratorOff(eq(UID));

        verifyCallbacksTriggered(vibration, Status.FINISHED);
        assertThat(mVibrators.get(1).isVibrating()).isFalse();
        assertThat(mVibrators.get(2).isVibrating()).isFalse();
        assertThat(mVibrators.get(3).isVibrating()).isFalse();

        assertThat(mVibratorHelpers.get(1).getEffectSegments())
                .containsExactly(expectedOneShot(10)).inOrder();
        assertThat(mVibratorHelpers.get(1).getAmplitudes())
                .containsExactlyElementsIn(expectedAmplitudes(100)).inOrder();
        assertThat(mVibratorHelpers.get(2).getEffectSegments())
                .containsExactly(expectedPrimitive(PRIMITIVE_CLICK, 1, 0)).inOrder();
        assertThat(mVibratorHelpers.get(3).getEffectSegments())
                .containsExactly(expectedPrebaked(EFFECT_CLICK)).inOrder();
    }

    @Test
    public void vibrate_multipleSyncedCallbackTriggered_finishSteps() throws Exception {
        int[] vibratorIds = new int[]{1, 2};
        mockVibrators(vibratorIds);
        mVibratorHelpers.get(1).setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        mVibratorHelpers.get(1).setSupportedPrimitives(PRIMITIVE_CLICK);
        mVibratorHelpers.get(2).setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        mVibratorHelpers.get(2).setSupportedPrimitives(PRIMITIVE_CLICK);
        when(mManagerHooks.prepareSyncedVibration(anyLong(), eq(vibratorIds))).thenReturn(true);

        VibrationEffect composed = VibrationEffect.startComposition()
                .addPrimitive(PRIMITIVE_CLICK, 1, 100)
                .compose();
        CombinedVibration effect = CombinedVibration.createParallel(composed);
        // We create the HalVibration here to obtain the vibration id and use it to mock the
        // required response when calling triggerSyncedVibration.
        HalVibration vibration = createVibration(effect);
        when(mManagerHooks.triggerSyncedVibration(eq(vibration.id))).thenReturn(true);
        startThreadAndDispatcher(vibration);

        assertThat(waitUntil(
                () -> !mVibratorHelpers.get(1).getEffectSegments().isEmpty()
                        && !mVibratorHelpers.get(2).getEffectSegments().isEmpty(),
                TEST_TIMEOUT_MILLIS)).isTrue();
        mVibrationConductor.notifySyncedVibrationComplete();
        waitForCompletion();

        long expectedCap = IVibratorManager.CAP_SYNC | IVibratorManager.CAP_PREPARE_COMPOSE;
        verify(mManagerHooks).prepareSyncedVibration(eq(expectedCap), eq(vibratorIds));
        verify(mManagerHooks).triggerSyncedVibration(eq(vibration.id));
        verify(mManagerHooks, never()).cancelSyncedVibration();
        verifyCallbacksTriggered(vibration, Status.FINISHED);

        VibrationEffectSegment expected = expectedPrimitive(PRIMITIVE_CLICK, 1, 100);
        assertThat(mVibratorHelpers.get(1).getEffectSegments())
                .containsExactly(expected).inOrder();
        assertThat(mVibratorHelpers.get(2).getEffectSegments())
                .containsExactly(expected).inOrder();
    }

    @Test
    public void vibrate_multipleSynced_callsPrepareAndTriggerCallbacks() {
        int[] vibratorIds = new int[]{1, 2, 3, 4};
        mockVibrators(vibratorIds);
        mVibratorHelpers.get(1).setSupportedEffects(EFFECT_CLICK);
        mVibratorHelpers.get(4).setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        mVibratorHelpers.get(4).setSupportedPrimitives(PRIMITIVE_CLICK);
        when(mManagerHooks.prepareSyncedVibration(anyLong(), any())).thenReturn(true);

        VibrationEffect composed = VibrationEffect.startComposition()
                .addPrimitive(PRIMITIVE_CLICK)
                .compose();
        CombinedVibration effect = CombinedVibration.startParallel()
                .addVibrator(1, VibrationEffect.get(EFFECT_CLICK))
                .addVibrator(2, VibrationEffect.createOneShot(10, 100))
                .addVibrator(3, VibrationEffect.createWaveform(new long[]{10}, new int[]{100}, -1))
                .addVibrator(4, composed)
                .combine();
        // We create the HalVibration here to obtain the vibration id and use it to mock the
        // required response when calling triggerSyncedVibration.
        HalVibration vibration = createVibration(effect);
        when(mManagerHooks.triggerSyncedVibration(eq(vibration.id))).thenReturn(true);
        startThreadAndDispatcher(vibration);
        waitForCompletion();

        long expectedCap = IVibratorManager.CAP_SYNC
                | IVibratorManager.CAP_PREPARE_ON
                | IVibratorManager.CAP_PREPARE_PERFORM
                | IVibratorManager.CAP_PREPARE_COMPOSE
                | IVibratorManager.CAP_MIXED_TRIGGER_ON
                | IVibratorManager.CAP_MIXED_TRIGGER_PERFORM
                | IVibratorManager.CAP_MIXED_TRIGGER_COMPOSE;
        verify(mManagerHooks).prepareSyncedVibration(eq(expectedCap), eq(vibratorIds));
        verify(mManagerHooks).triggerSyncedVibration(eq(vibration.id));
        verify(mManagerHooks, never()).cancelSyncedVibration();
        verifyCallbacksTriggered(vibration, Status.FINISHED);
    }

    @Test
    public void vibrate_multipleSyncedPrepareFailed_skipTriggerStepAndVibrates() {
        int[] vibratorIds = new int[]{1, 2};
        mockVibrators(vibratorIds);
        mVibratorHelpers.get(1).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        mVibratorHelpers.get(2).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        when(mManagerHooks.prepareSyncedVibration(anyLong(), any())).thenReturn(false);

        CombinedVibration effect = CombinedVibration.startParallel()
                .addVibrator(1, VibrationEffect.createOneShot(10, 100))
                .addVibrator(2, VibrationEffect.createWaveform(new long[]{5}, new int[]{200}, -1))
                .combine();
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        long expectedCap = IVibratorManager.CAP_SYNC | IVibratorManager.CAP_PREPARE_ON;
        verify(mManagerHooks).prepareSyncedVibration(eq(expectedCap), eq(vibratorIds));
        verify(mManagerHooks, never()).triggerSyncedVibration(eq(vibration.id));
        verify(mManagerHooks, never()).cancelSyncedVibration();

        assertThat(mVibratorHelpers.get(1).getEffectSegments())
                .containsExactly(expectedOneShot(10)).inOrder();
        assertThat(mVibratorHelpers.get(1).getAmplitudes())
                .containsExactlyElementsIn(expectedAmplitudes(100)).inOrder();
        assertThat(mVibratorHelpers.get(2).getEffectSegments())
                .containsExactly(expectedOneShot(5)).inOrder();
        assertThat(mVibratorHelpers.get(2).getAmplitudes())
                .containsExactlyElementsIn(expectedAmplitudes(200)).inOrder();
    }

    @Test
    public void vibrate_multipleSyncedTriggerFailed_cancelPreparedVibrationAndSkipSetAmplitude() {
        int[] vibratorIds = new int[]{1, 2};
        mockVibrators(vibratorIds);
        mVibratorHelpers.get(2).setSupportedEffects(EFFECT_CLICK);
        when(mManagerHooks.prepareSyncedVibration(anyLong(), any())).thenReturn(true);

        CombinedVibration effect = CombinedVibration.startParallel()
                .addVibrator(1, VibrationEffect.createOneShot(10, 100))
                .addVibrator(2, VibrationEffect.get(EFFECT_CLICK))
                .combine();
        // We create the HalVibration here to obtain the vibration id and use it to mock the
        // required response when calling triggerSyncedVibration.
        HalVibration vibration = createVibration(effect);
        when(mManagerHooks.triggerSyncedVibration(eq(vibration.id))).thenReturn(false);
        startThreadAndDispatcher(vibration);
        waitForCompletion();

        long expectedCap = IVibratorManager.CAP_SYNC
                | IVibratorManager.CAP_PREPARE_ON
                | IVibratorManager.CAP_PREPARE_PERFORM
                | IVibratorManager.CAP_MIXED_TRIGGER_ON
                | IVibratorManager.CAP_MIXED_TRIGGER_PERFORM;
        verify(mManagerHooks).prepareSyncedVibration(eq(expectedCap), eq(vibratorIds));
        verify(mManagerHooks).triggerSyncedVibration(eq(vibration.id));
        verify(mManagerHooks).cancelSyncedVibration();
        assertThat(mVibratorHelpers.get(1).getAmplitudes()).isEmpty();
    }

    @Test
    public void vibrate_multipleSyncedOneVibratorFails_returnsFailureAndStopsVibration() {
        int[] vibratorIds = new int[]{1, 2};
        mockVibrators(vibratorIds);
        mVibratorHelpers.get(1).setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        mVibratorHelpers.get(1).setSupportedPrimitives(PRIMITIVE_CLICK);
        mVibratorHelpers.get(2).setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        mVibratorHelpers.get(2).setSupportedPrimitives(PRIMITIVE_CLICK);
        when(mManagerHooks.prepareSyncedVibration(anyLong(), eq(vibratorIds))).thenReturn(true);
        mVibratorHelpers.get(2).setPrimitivesToFail();

        VibrationEffect composed = VibrationEffect.startComposition()
                .addPrimitive(PRIMITIVE_CLICK, 1, 100)
                .compose();
        CombinedVibration effect = CombinedVibration.createParallel(composed);
        // We create the HalVibration here to obtain the vibration id and use it to mock the
        // required response when calling triggerSyncedVibration.
        HalVibration vibration = createVibration(effect);
        when(mManagerHooks.triggerSyncedVibration(eq(vibration.id))).thenReturn(true);
        startThreadAndDispatcher(vibration);
        waitForCompletion();

        long expectedCap = IVibratorManager.CAP_SYNC | IVibratorManager.CAP_PREPARE_COMPOSE;
        verify(mManagerHooks).prepareSyncedVibration(eq(expectedCap), eq(vibratorIds));
        verify(mManagerHooks, never()).triggerSyncedVibration(eq(vibration.id));
        verify(mManagerHooks).cancelSyncedVibration();
        verifyCallbacksTriggered(vibration, Status.IGNORED_ERROR_DISPATCHING);

        VibrationEffectSegment expected = expectedPrimitive(PRIMITIVE_CLICK, 1, 100);
        assertThat(mVibratorHelpers.get(1).getEffectSegments())
                .containsExactly(expected).inOrder();
        assertThat(mVibratorHelpers.get(2).getEffectSegments()).isEmpty();
    }

    @Test
    public void vibrate_multipleWaveforms_playsWaveformsInParallel() throws Exception {
        mockVibrators(1, 2, 3);
        mVibratorHelpers.get(1).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        mVibratorHelpers.get(2).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        mVibratorHelpers.get(3).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);

        CombinedVibration effect = CombinedVibration.startParallel()
                .addVibrator(1, VibrationEffect.createWaveform(
                        new long[]{5, 10, 10}, new int[]{1, 2, 3}, -1))
                .addVibrator(2, VibrationEffect.createWaveform(
                        new long[]{20, 60}, new int[]{4, 5}, -1))
                .addVibrator(3, VibrationEffect.createWaveform(
                        new long[]{60}, new int[]{6}, -1))
                .combine();
        HalVibration vibration = startThreadAndDispatcher(effect);

        // All vibrators are turned on in parallel.
        assertThat(waitUntil(
                () -> mVibrators.get(1).isVibrating()
                        && mVibrators.get(2).isVibrating()
                        && mVibrators.get(3).isVibrating(),
                TEST_TIMEOUT_MILLIS)).isTrue();

        waitForCompletion();

        verify(mManagerHooks).noteVibratorOn(eq(UID), eq(80L));
        verify(mManagerHooks).noteVibratorOff(eq(UID));
        verify(mHalCallbacks).onVibrationStepComplete(eq(1), eq(vibration.id), anyLong());
        verify(mHalCallbacks).onVibrationStepComplete(eq(2), eq(vibration.id), anyLong());
        verify(mHalCallbacks).onVibrationStepComplete(eq(3), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.FINISHED);
        assertThat(mVibrators.get(1).isVibrating()).isFalse();
        assertThat(mVibrators.get(2).isVibrating()).isFalse();
        assertThat(mVibrators.get(3).isVibrating()).isFalse();

        assertThat(mVibratorHelpers.get(1).getEffectSegments())
                .containsExactly(expectedOneShot(25)).inOrder();
        assertThat(mVibratorHelpers.get(2).getEffectSegments())
                .containsExactly(expectedOneShot(80)).inOrder();
        assertThat(mVibratorHelpers.get(3).getEffectSegments())
                .containsExactly(expectedOneShot(60)).inOrder();
        assertThat(mVibratorHelpers.get(1).getAmplitudes())
                .containsExactlyElementsIn(expectedAmplitudes(1, 2, 3)).inOrder();
        assertThat(mVibratorHelpers.get(2).getAmplitudes())
                .containsExactlyElementsIn(expectedAmplitudes(4, 5)).inOrder();
        assertThat(mVibratorHelpers.get(3).getAmplitudes())
                .containsExactlyElementsIn(expectedAmplitudes(6)).inOrder();

    }

    @Test
    public void vibrate_withRampDown_vibrationFinishedAfterDurationAndBeforeRampDown()
            throws Exception {
        int expectedDuration = 100;
        int rampDownDuration = 200;

        mVibrationConfigBuilder.setRampDownDurationMs(rampDownDuration);
        createThreadAndSettings();
        mVibratorHelpers.get(VIBRATOR_ID).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);

        HalVibration vibration = createVibration(
                CombinedVibration.createParallel(
                        VibrationEffect.createOneShot(
                                expectedDuration, VibrationEffect.DEFAULT_AMPLITUDE)));

        long startTime = SystemClock.elapsedRealtime();
        startThreadAndDispatcher(vibration);

        vibration.waitForEnd();
        long vibrationEndTime = SystemClock.elapsedRealtime();

        waitForCompletion(rampDownDuration + TEST_TIMEOUT_MILLIS);
        long completionTime = SystemClock.elapsedRealtime();

        verify(mHalCallbacks).onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        // Vibration ends before ramp down, thread completed after ramp down
        assertThat(vibrationEndTime - startTime).isLessThan(expectedDuration + rampDownDuration);
        assertThat(completionTime - startTime).isAtLeast(expectedDuration + rampDownDuration);
    }

    @Test
    public void vibrate_withVibratorCallbackDelayShorterThanTimeout_vibrationFinishedAfterDelay() {
        long expectedDuration = 10;
        long callbackDelay = VibrationStepConductor.CALLBACKS_EXTRA_TIMEOUT / 2;

        mVibratorHelpers.get(VIBRATOR_ID).setCompletionCallbackLatency(callbackDelay);
        mVibratorHelpers.get(VIBRATOR_ID).setCapabilities(IVibrator.CAP_ON_CALLBACK,
                IVibrator.CAP_AMPLITUDE_CONTROL);

        HalVibration vibration = createVibration(
                CombinedVibration.createParallel(
                        VibrationEffect.createOneShot(
                                expectedDuration, VibrationEffect.DEFAULT_AMPLITUDE)));

        long startTime = SystemClock.elapsedRealtime();
        startThreadAndDispatcher(vibration);

        waitForCompletion(TEST_TIMEOUT_MILLIS);
        long vibrationEndTime = SystemClock.elapsedRealtime();

        verify(mHalCallbacks).onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        assertThat(vibrationEndTime - startTime).isAtLeast(expectedDuration + callbackDelay);
    }

    @LargeTest
    @Test
    public void vibrate_withVibratorCallbackDelayLongerThanTimeout_vibrationFinishedAfterTimeout() {
        long expectedDuration = 10;
        long callbackTimeout = VibrationStepConductor.CALLBACKS_EXTRA_TIMEOUT;
        long callbackDelay = callbackTimeout * 5;

        mVibratorHelpers.get(VIBRATOR_ID).setCompletionCallbackLatency(callbackDelay);
        mVibratorHelpers.get(VIBRATOR_ID).setCapabilities(IVibrator.CAP_ON_CALLBACK,
                IVibrator.CAP_AMPLITUDE_CONTROL);

        HalVibration vibration = createVibration(
                CombinedVibration.createParallel(
                        VibrationEffect.createOneShot(
                                expectedDuration, VibrationEffect.DEFAULT_AMPLITUDE)));

        long startTime = SystemClock.elapsedRealtime();
        startThreadAndDispatcher(vibration);

        // Vibration will be completed after VibrationStepConductor timeout.
        waitForCompletion(expectedDuration + callbackTimeout + TEST_TIMEOUT_MILLIS);
        long vibrationEndTime = SystemClock.elapsedRealtime();

        verify(mHalCallbacks, never())
                .onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        // Vibration ends and thread completes after timeout, before the HAL callback
        assertThat(vibrationEndTime - startTime).isAtLeast(expectedDuration + callbackTimeout);
        assertThat(vibrationEndTime - startTime).isLessThan(expectedDuration + callbackDelay);
    }

    @LargeTest
    @Test
    public void vibrate_withWaveform_totalVibrationTimeRespected() {
        int totalDuration = 10_000; // 10s
        int stepDuration = 25; // 25ms

        // 25% of the first waveform step will be spent on the native on() call.
        // 25% of each waveform step will be spent on the native setAmplitude() call..
        mVibratorHelpers.get(VIBRATOR_ID).setOnLatency(stepDuration / 4);
        mVibratorHelpers.get(VIBRATOR_ID).setCapabilities(IVibrator.CAP_ON_CALLBACK,
                IVibrator.CAP_AMPLITUDE_CONTROL);

        int stepCount = totalDuration / stepDuration;
        long[] timings = new long[stepCount];
        int[] amplitudes = new int[stepCount];
        Arrays.fill(timings, stepDuration);
        Arrays.fill(amplitudes, VibrationEffect.DEFAULT_AMPLITUDE);
        VibrationEffect effect = VibrationEffect.createWaveform(timings, amplitudes, -1);

        long startTime = SystemClock.elapsedRealtime();
        startThreadAndDispatcher(effect);

        waitForCompletion(totalDuration + TEST_TIMEOUT_MILLIS);
        long delay = Math.abs(SystemClock.elapsedRealtime() - startTime - totalDuration);

        // Allow some delay for thread scheduling and callback triggering.
        int maxDelay = (int) (0.05 * totalDuration); // < 5% of total duration
        assertThat(delay).isLessThan(maxDelay);
    }

    @LargeTest
    @Test
    public void vibrate_cancelSlowVibrator_cancelIsNotBlockedByVibrationThread() throws Exception {
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setSupportedEffects(EFFECT_CLICK);

        long latency = 5_000; // 5s
        vibratorHelper.setOnLatency(latency);

        VibrationEffect effect = VibrationEffect.get(EFFECT_CLICK);
        HalVibration vibration = startThreadAndDispatcher(effect);

        assertThat(waitUntil(() -> !vibratorHelper.getEffectSegments().isEmpty(),
                TEST_TIMEOUT_MILLIS)).isTrue();
        assertThat(mThread.isRunningVibrationId(vibration.id)).isTrue();

        // Run cancel in a separate thread so if VibrationThread.cancel blocks then this test should
        // fail at waitForCompletion(cancellingThread).
        Thread cancellingThread = new Thread(
                () -> mVibrationConductor.notifyCancelled(
                        new Vibration.EndInfo(Status.CANCELLED_BY_USER), /* immediate= */ false));
        cancellingThread.start();

        // Cancelling the vibration should be fast and return right away, even if the thread is
        // stuck at the slow call to the vibrator.
        cancellingThread.join(TEST_IMMEDIATE_CANCEL_TIMEOUT_MILLIS);

        // After the vibrator call ends the vibration is cancelled and the vibrator is turned off.
        waitForCompletion(/* timeout= */ latency + TEST_TIMEOUT_MILLIS);
        verifyCallbacksTriggered(vibration, Status.CANCELLED_BY_USER);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();
    }

    @Test
    public void vibrate_multipleVibratorsCancel_cancelsVibrationImmediately() throws Exception {
        mockVibrators(1, 2);
        mVibratorHelpers.get(1).setSupportedEffects(EFFECT_CLICK);
        mVibratorHelpers.get(2).setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        mVibratorHelpers.get(2).setSupportedPrimitives(PRIMITIVE_CLICK);

        CombinedVibration effect = CombinedVibration.startParallel()
                .addVibrator(1, VibrationEffect.get(EFFECT_CLICK))
                .addVibrator(2, VibrationEffect.startComposition()
                        .addPrimitive(PRIMITIVE_CLICK, 1f, 100)
                        .addPrimitive(PRIMITIVE_CLICK, 1f, 100)
                        .addPrimitive(PRIMITIVE_CLICK, 1f, 100)
                        .compose())
                .combine();
        HalVibration vibration = startThreadAndDispatcher(effect);

        assertThat(waitUntil(() -> mVibrators.get(2).isVibrating(), TEST_TIMEOUT_MILLIS))
                .isTrue();
        assertThat(mThread.isRunningVibrationId(vibration.id)).isTrue();

        // Run cancel in a separate thread so if VibrationThread.cancel blocks then this test should
        // fail at waitForCompletion(vibrationThread) if the vibration not cancelled immediately.
        Thread cancellingThread = new Thread(
                () -> mVibrationConductor.notifyCancelled(
                        new Vibration.EndInfo(Status.CANCELLED_BY_SCREEN_OFF),
                        /* immediate= */ false));
        cancellingThread.start();

        waitForCompletion(TEST_IMMEDIATE_CANCEL_TIMEOUT_MILLIS);
        cancellingThread.join();

        verifyCallbacksTriggered(vibration, Status.CANCELLED_BY_SCREEN_OFF);
        assertThat(mVibrators.get(1).isVibrating()).isFalse();
        assertThat(mVibrators.get(2).isVibrating()).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void vibrate_multipleVendorEffectCancel_cancelsVibrationImmediately() throws Exception {
        mockVibrators(1, 2);
        mVibratorHelpers.get(1).setCapabilities(IVibrator.CAP_PERFORM_VENDOR_EFFECTS);
        mVibratorHelpers.get(1).setVendorEffectDuration(10 * TEST_TIMEOUT_MILLIS);
        mVibratorHelpers.get(2).setCapabilities(IVibrator.CAP_PERFORM_VENDOR_EFFECTS);
        mVibratorHelpers.get(2).setVendorEffectDuration(10 * TEST_TIMEOUT_MILLIS);

        CombinedVibration effect = CombinedVibration.startParallel()
                .addVibrator(1, VibrationEffect.createVendorEffect(createTestVendorData()))
                .addVibrator(2, VibrationEffect.createVendorEffect(createTestVendorData()))
                .combine();
        HalVibration vibration = startThreadAndDispatcher(effect);

        assertThat(waitUntil(() -> mVibrators.get(2).isVibrating(), TEST_TIMEOUT_MILLIS))
                .isTrue();
        assertThat(mThread.isRunningVibrationId(vibration.id)).isTrue();

        // Run cancel in a separate thread so if VibrationThread.cancel blocks then this test should
        // fail at waitForCompletion(vibrationThread) if the vibration not cancelled immediately.
        Thread cancellingThread = new Thread(
                () -> mVibrationConductor.notifyCancelled(
                        new Vibration.EndInfo(Status.CANCELLED_BY_SCREEN_OFF),
                        /* immediate= */ false));
        cancellingThread.start();

        waitForCompletion(TEST_IMMEDIATE_CANCEL_TIMEOUT_MILLIS);
        cancellingThread.join();

        verifyCallbacksTriggered(vibration, Status.CANCELLED_BY_SCREEN_OFF);
        assertThat(mVibrators.get(1).isVibrating()).isFalse();
        assertThat(mVibrators.get(2).isVibrating()).isFalse();
    }

    @Test
    public void vibrate_multipleWaveformCancel_cancelsVibrationImmediately() throws Exception {
        mockVibrators(1, 2);
        mVibratorHelpers.get(1).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        mVibratorHelpers.get(2).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);

        CombinedVibration effect = CombinedVibration.startParallel()
                .addVibrator(1, VibrationEffect.createWaveform(
                        new long[]{100, 100}, new int[]{1, 2}, 0))
                .addVibrator(2, VibrationEffect.createOneShot(100, 100))
                .combine();
        HalVibration vibration = startThreadAndDispatcher(effect);

        assertThat(waitUntil(() -> mVibrators.get(1).isVibrating()
                        && mVibrators.get(2).isVibrating(),
                TEST_TIMEOUT_MILLIS)).isTrue();
        assertThat(mThread.isRunningVibrationId(vibration.id)).isTrue();

        // Run cancel in a separate thread so if VibrationThread.cancel blocks then this test should
        // fail at waitForCompletion(vibrationThread) if the vibration not cancelled immediately.
        Thread cancellingThread = new Thread(
                () -> mVibrationConductor.notifyCancelled(
                        new Vibration.EndInfo(Status.CANCELLED_BY_SCREEN_OFF),
                        /* immediate= */ false));
        cancellingThread.start();

        waitForCompletion(TEST_IMMEDIATE_CANCEL_TIMEOUT_MILLIS);
        cancellingThread.join();

        verifyCallbacksTriggered(vibration, Status.CANCELLED_BY_SCREEN_OFF);
        assertThat(mVibrators.get(1).isVibrating()).isFalse();
        assertThat(mVibrators.get(2).isVibrating()).isFalse();
    }

    @Test
    public void vibrate_binderDied_cancelsVibration() throws Exception {
        VibrationEffect effect = VibrationEffect.createWaveform(new long[]{5}, new int[]{100}, 0);
        HalVibration vibration = startThreadAndDispatcher(effect);

        assertThat(waitUntil(() -> mVibrators.get(VIBRATOR_ID).isVibrating(),
                TEST_TIMEOUT_MILLIS)).isTrue();
        assertThat(mThread.isRunningVibrationId(vibration.id)).isTrue();

        mVibrationConductor.notifyCancelled(
                new Vibration.EndInfo(Status.CANCELLED_BINDER_DIED), /* immediate= */ false);
        waitForCompletion();

        verifyCallbacksTriggered(vibration, Status.CANCELLED_BINDER_DIED);
        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getEffectSegments()).isNotEmpty();
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();
    }

    @Test
    public void vibrate_waveformWithRampDown_addsRampDownAfterVibrationCompleted() {
        mVibrationConfigBuilder.setRampDownDurationMs(15);
        createThreadAndSettings();
        mVibratorHelpers.get(VIBRATOR_ID).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);

        VibrationEffect effect = VibrationEffect.createWaveform(
                new long[]{5, 5, 5}, new int[]{60, 120, 240}, -1);
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mHalCallbacks).onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.FINISHED);

        // Duration extended for 5 + 5 + 5 + 15.
        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getEffectSegments())
                .containsExactly(expectedOneShot(30)).inOrder();
        List<Float> amplitudes = mVibratorHelpers.get(VIBRATOR_ID).getAmplitudes();
        assertThat(amplitudes.size()).isGreaterThan(3);
        assertThat(amplitudes.subList(0, 3))
                .containsExactlyElementsIn(expectedAmplitudes(60, 120, 240))
                .inOrder();
        for (int i = 3; i < amplitudes.size(); i++) {
            assertWithMessage("For amplitude index %s", i)
                    .that(amplitudes.get(i)).isLessThan(amplitudes.get(i - 1));
        }
    }

    @Test
    public void vibrate_waveformWithRampDown_triggersCallbackWhenOriginalVibrationEnds()
            throws Exception {
        mVibrationConfigBuilder.setRampDownDurationMs(10_000);
        createThreadAndSettings();
        mVibratorHelpers.get(VIBRATOR_ID).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);

        VibrationEffect effect = VibrationEffect.createOneShot(10, 200);
        HalVibration vibration = startThreadAndDispatcher(effect);

        // Vibration completed but vibrator not yet released.
        vibration.waitForEnd();
        verify(mManagerHooks, never()).onVibrationThreadReleased(anyLong());

        // Thread still running ramp down.
        assertThat(mThread.isRunningVibrationId(vibration.id)).isTrue();

        // Duration extended for 10 + 10000.
        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getEffectSegments())
                .containsExactly(expectedOneShot(10_010)).inOrder();

        // Will stop the ramp down right away.
        mVibrationConductor.notifyCancelled(
                new Vibration.EndInfo(Status.CANCELLED_BY_SETTINGS_UPDATE), /* immediate= */ true);
        waitForCompletion();

        // Does not cancel already finished vibration, but releases vibrator.
        assertThat(vibration.getStatus()).isNotEqualTo(Status.CANCELLED_BY_SETTINGS_UPDATE);
        verify(mManagerHooks).onVibrationThreadReleased(vibration.id);
    }

    @Test
    public void vibrate_waveformCancelledWithRampDown_addsRampDownAfterVibrationCancelled()
            throws Exception {
        mVibrationConfigBuilder.setRampDownDurationMs(15);
        createThreadAndSettings();
        mVibratorHelpers.get(VIBRATOR_ID).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);

        VibrationEffect effect = VibrationEffect.createOneShot(10_000, 240);
        HalVibration vibration = startThreadAndDispatcher(effect);
        assertThat(waitUntil(() -> mVibrators.get(VIBRATOR_ID).isVibrating(),
                TEST_TIMEOUT_MILLIS)).isTrue();
        mVibrationConductor.notifyCancelled(
                new Vibration.EndInfo(Status.CANCELLED_BY_USER), /* immediate= */ false);
        waitForCompletion();

        verifyCallbacksTriggered(vibration, Status.CANCELLED_BY_USER);

        // Duration extended for 10000 + 15.
        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getEffectSegments())
                .containsExactly(expectedOneShot(10_015)).inOrder();
        List<Float> amplitudes = mVibratorHelpers.get(VIBRATOR_ID).getAmplitudes();
        assertThat(amplitudes.size()).isGreaterThan(1);
        for (int i = 1; i < amplitudes.size(); i++) {
            assertWithMessage("For amplitude index %s", i)
                    .that(amplitudes.get(i)).isLessThan(amplitudes.get(i - 1));
        }
    }

    @Test
    public void vibrate_prebakedWithRampDown_doesNotAddRampDown() {
        mVibrationConfigBuilder.setRampDownDurationMs(15);
        createThreadAndSettings();
        mVibratorHelpers.get(VIBRATOR_ID).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        mVibratorHelpers.get(VIBRATOR_ID).setSupportedEffects(EFFECT_CLICK);

        VibrationEffect effect = VibrationEffect.get(EFFECT_CLICK);
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mHalCallbacks).onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.FINISHED);

        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getEffectSegments())
                .containsExactly(expectedPrebaked(EFFECT_CLICK)).inOrder();
        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getAmplitudes()).isEmpty();
    }

    @Test
    @EnableFlags(Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void vibrate_vendorEffectWithRampDown_doesNotAddRampDown() {
        mVibrationConfigBuilder.setRampDownDurationMs(15);
        createThreadAndSettings();
        mVibratorHelpers.get(VIBRATOR_ID).setCapabilities(IVibrator.CAP_PERFORM_VENDOR_EFFECTS);

        VibrationEffect effect = VibrationEffect.createVendorEffect(createTestVendorData());
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mHalCallbacks).onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.FINISHED);

        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getVendorEffects())
                .containsExactly(effect).inOrder();
        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getAmplitudes()).isEmpty();
    }

    @Test
    public void vibrate_composedWithRampDown_doesNotAddRampDown() {
        mVibrationConfigBuilder.setRampDownDurationMs(15);
        createThreadAndSettings();
        mVibratorHelpers.get(VIBRATOR_ID).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL,
                IVibrator.CAP_COMPOSE_EFFECTS);
        mVibratorHelpers.get(VIBRATOR_ID).setSupportedPrimitives(PRIMITIVE_CLICK);

        VibrationEffect effect = VibrationEffect.startComposition()
                .addPrimitive(PRIMITIVE_CLICK)
                .compose();
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mHalCallbacks).onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.FINISHED);

        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getEffectSegments())
                .containsExactly(expectedPrimitive(PRIMITIVE_CLICK, 1, 0)).inOrder();
        assertThat(mVibratorHelpers.get(VIBRATOR_ID).getAmplitudes()).isEmpty();
    }

    @Test
    @DisableFlags(Flags.FLAG_NORMALIZED_PWLE_EFFECTS)
    public void vibrate_pwleWithRampDown_doesNotAddRampDown() {
        mVibrationConfigBuilder.setRampDownDurationMs(15);
        createThreadAndSettings();
        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        vibratorHelper.setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL,
                IVibrator.CAP_GET_RESONANT_FREQUENCY, IVibrator.CAP_FREQUENCY_CONTROL,
                IVibrator.CAP_COMPOSE_PWLE_EFFECTS);
        vibratorHelper.setMinFrequency(100);
        vibratorHelper.setResonantFrequency(150);
        vibratorHelper.setFrequencyResolution(50);
        vibratorHelper.setMaxAmplitudes(1, 1, 1);
        vibratorHelper.setPwleSizeMax(2);

        VibrationEffect effect = VibrationEffect.startWaveform()
                .addTransition(Duration.ofMillis(1), targetAmplitude(1))
                .build();
        HalVibration vibration = startThreadAndDispatcher(effect);
        waitForCompletion();

        verify(mHalCallbacks).onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration.id), anyLong());
        verifyCallbacksTriggered(vibration, Status.FINISHED);

        assertThat(vibratorHelper.getEffectSegments())
                .containsExactly(expectedRamp(0, 1, 150, 150, 1)).inOrder();
        assertThat(vibratorHelper.getAmplitudes()).isEmpty();
    }

    @Test
    public void vibrate_multipleVibrations_withCancel() throws Exception {
        mVibratorHelpers.get(VIBRATOR_ID).setSupportedEffects(EFFECT_CLICK, EFFECT_TICK);
        mVibratorHelpers.get(VIBRATOR_ID).setSupportedPrimitives(PRIMITIVE_CLICK);
        mVibratorHelpers.get(VIBRATOR_ID).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL,
                IVibrator.CAP_COMPOSE_EFFECTS);

        // A simple effect, followed by a repeating effect that gets cancelled, followed by another
        // simple effect.
        VibrationEffect effect1 = VibrationEffect.get(EFFECT_CLICK);
        VibrationEffect effect2 = VibrationEffect.startComposition()
                .repeatEffectIndefinitely(VibrationEffect.get(EFFECT_TICK))
                .compose();
        VibrationEffect effect3 = VibrationEffect.startComposition()
                .addPrimitive(PRIMITIVE_CLICK)
                .compose();
        VibrationEffect effect4 = VibrationEffect.createOneShot(8000, 100);
        VibrationEffect effect5 = VibrationEffect.get(EFFECT_CLICK);

        HalVibration vibration1 = startThreadAndDispatcher(effect1);
        waitForCompletion();

        HalVibration vibration2 = startThreadAndDispatcher(effect2);
        // Effect2 won't complete on its own. Cancel it after a couple of repeats.
        Thread.sleep(150);  // More than two TICKs.
        mVibrationConductor.notifyCancelled(
                new Vibration.EndInfo(Status.CANCELLED_BY_USER), /* immediate= */ false);
        waitForCompletion();

        HalVibration vibration3 = startThreadAndDispatcher(effect3);
        waitForCompletion();

        // Effect4 is a long oneshot, but it gets cancelled as fast as possible.
        long start4 = System.currentTimeMillis();
        HalVibration vibration4 = startThreadAndDispatcher(effect4);
        mVibrationConductor.notifyCancelled(
                new Vibration.EndInfo(Status.CANCELLED_BY_SCREEN_OFF), /* immediate= */ true);
        waitForCompletion();
        long duration4 = System.currentTimeMillis() - start4;

        // Effect5 is to show that things keep going after the immediate cancel.
        HalVibration vibration5 = startThreadAndDispatcher(effect5);
        waitForCompletion();

        HalVibratorHelper vibratorHelper = mVibratorHelpers.get(VIBRATOR_ID);
        assertThat(mVibrators.get(VIBRATOR_ID).isVibrating()).isFalse();

        int nextSegment = 0;
        List<VibrationEffectSegment> actualSegments = vibratorHelper.getEffectSegments();

        // Effect1
        verify(mHalCallbacks)
                .onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration1.id), anyLong());
        verifyCallbacksTriggered(vibration1, Status.FINISHED);
        assertThat(actualSegments.get(nextSegment++)).isEqualTo(expectedPrebaked(EFFECT_CLICK));

        // Effect2: repeating, cancelled.
        verify(mHalCallbacks, atLeast(2))
                .onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration2.id), anyLong());
        verifyCallbacksTriggered(vibration2, Status.CANCELLED_BY_USER);
        // The exact count of segments might vary, so just check that there's more than 2 and
        // all elements are tick segments.
        int tickSegmentCount = 0;
        while (nextSegment < actualSegments.size()
                && actualSegments.get(nextSegment).equals(expectedPrebaked(EFFECT_TICK))) {
            tickSegmentCount++;
            nextSegment++;
        }
        assertThat(tickSegmentCount).isAtLeast(2);

        // Effect3
        verify(mHalCallbacks)
                .onVibrationStepComplete(eq(VIBRATOR_ID), eq(vibration3.id), anyLong());
        verifyCallbacksTriggered(vibration3, Status.FINISHED);
        assertThat(actualSegments.get(nextSegment++))
                .isEqualTo(expectedPrimitive(PRIMITIVE_CLICK, 1, 0));

        // Effect4: cancelled quickly.
        verifyCallbacksTriggered(vibration4, Status.CANCELLED_BY_SCREEN_OFF);
        assertThat(duration4).isLessThan(2000);
        // Cancellation might have happened before effect was requested.
        if (nextSegment < actualSegments.size()
                && actualSegments.get(nextSegment) instanceof StepSegment) {
            assertThat(actualSegments.get(nextSegment++)).isEqualTo(expectedOneShot(8000));
        }

        // Effect5: played normally after effect4, which may or may not have played.
        assertThat(actualSegments.get(nextSegment++)).isEqualTo(expectedPrebaked(EFFECT_CLICK));

        // No more segments.
        assertThat(nextSegment).isEqualTo(actualSegments.size());
    }

    @Test
    @DisableFlags(Flags.FLAG_REMOVE_SEQUENTIAL_COMBINATION)
    public void vibrate_multipleVibratorsSequentialInSession_runsInOrderWithoutDelaysAndNoOffs() {
        mockVibrators(1, 2, 3);
        mVibratorHelpers.get(1).setCapabilities(IVibrator.CAP_AMPLITUDE_CONTROL);
        mVibratorHelpers.get(2).setCapabilities(IVibrator.CAP_COMPOSE_EFFECTS);
        mVibratorHelpers.get(2).setSupportedPrimitives(PRIMITIVE_CLICK);
        mVibratorHelpers.get(3).setSupportedEffects(EFFECT_CLICK);

        CombinedVibration effect = CombinedVibration.startSequential()
                .addNext(3, VibrationEffect.get(EFFECT_CLICK), /* delay= */ TEST_TIMEOUT_MILLIS)
                .addNext(1,
                        VibrationEffect.createWaveform(
                                new long[] {TEST_TIMEOUT_MILLIS, TEST_TIMEOUT_MILLIS}, -1),
                        /* delay= */ TEST_TIMEOUT_MILLIS)
                .addNext(2,
                        VibrationEffect.startComposition()
                                .addPrimitive(PRIMITIVE_CLICK, 1, /* delay= */ TEST_TIMEOUT_MILLIS)
                                .compose(),
                        /* delay= */ TEST_TIMEOUT_MILLIS)
                .combine();
        HalVibration vibration = startThreadAndDispatcher(effect, /* isInSession= */ true);

        // Should not timeout as delays will not affect in session playback time.
        waitForCompletion();

        // Vibrating state remains ON until session resets it.
        verifyCallbacksTriggered(vibration, Status.FINISHED);
        assertThat(mVibrators.get(1).isVibrating()).isTrue();
        assertThat(mVibrators.get(2).isVibrating()).isTrue();
        assertThat(mVibrators.get(3).isVibrating()).isTrue();

        // Off only called once during initialization.
        assertThat(mVibratorHelpers.get(1).getOffCount()).isEqualTo(1);
        assertThat(mVibratorHelpers.get(2).getOffCount()).isEqualTo(1);
        assertThat(mVibratorHelpers.get(3).getOffCount()).isEqualTo(1);
        assertThat(mVibratorHelpers.get(1).getEffectSegments())
                .containsExactly(expectedOneShot(TEST_TIMEOUT_MILLIS)).inOrder();
        assertThat(mVibratorHelpers.get(1).getAmplitudes())
                .containsExactlyElementsIn(expectedAmplitudes(255)).inOrder();
        assertThat(mVibratorHelpers.get(2).getEffectSegments())
                .containsExactly(expectedPrimitive(PRIMITIVE_CLICK, 1, TEST_TIMEOUT_MILLIS))
                .inOrder();
        assertThat(mVibratorHelpers.get(3).getEffectSegments())
                .containsExactly(expectedPrebaked(EFFECT_CLICK)).inOrder();
    }

    private void mockVibrators(int... vibratorIds) {
        for (int vibratorId : vibratorIds) {
            mVibratorHelpers.put(vibratorId, new HalVibratorHelper(mTestLooper.getLooper()));
        }
    }

    private void setUserSetting(String settingName, int value) {
        Settings.System.putIntForUser(
                mContextSpy.getContentResolver(), settingName, value, UserHandle.USER_CURRENT);
        // FakeSettingsProvider doesn't support testing triggering ContentObserver yet.
        mVibrationSettings.mSettingObserver.onChange(false);
    }

    private void createThreadAndSettings() {
        mVibrationSettings = new VibrationSettings(mContextSpy,
                new Handler(mTestLooper.getLooper()), mVibrationConfigBuilder.build(),
                mFallbackEffects);
        mVibrationScaler = new VibrationScaler(mVibrationConfigBuilder.build(), mVibrationSettings);
        PowerManager.WakeLock wakeLock = mContextSpy.getSystemService(
                PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "*vibrator*");
        mThread = new VibrationThread(wakeLock, mManagerHooks);
        mThread.start();
    }

    private HalVibration startThreadAndDispatcher(VibrationEffect effect) {
        return startThreadAndDispatcher(CombinedVibration.createParallel(effect));
    }

    private HalVibration startThreadAndDispatcher(CombinedVibration effect) {
        return startThreadAndDispatcher(createVibration(effect));
    }

    private HalVibration startThreadAndDispatcher(CombinedVibration effect, boolean isInSession) {
        return startThreadAndDispatcher(createVibration(effect), isInSession,
                /* requestVibrationParamsFuture= */ null);
    }

    private HalVibration startThreadAndDispatcher(HalVibration vib) {
        return startThreadAndDispatcher(vib, /* isInSession= */ false,
                /* requestVibrationParamsFuture= */ null);
    }

    private HalVibration startThreadAndDispatcher(VibrationEffect effect,
            CompletableFuture<Void> requestVibrationParamsFuture, int usage) {
        VibrationAttributes attrs = new VibrationAttributes.Builder()
                .setUsage(usage)
                .build();
        HalVibration vib = new HalVibration(
                new CallerInfo(attrs, UID, DEVICE_ID, PACKAGE_NAME, "reason"),
                CombinedVibration.createParallel(effect));
        return startThreadAndDispatcher(vib, /* isInSession= */ false,
                requestVibrationParamsFuture);
    }

    private HalVibration startThreadAndDispatcher(HalVibration vib, boolean isInSession,
            CompletableFuture<Void> requestVibrationParamsFuture) {
        mVibrators = createVibrators();
        DeviceAdapter deviceAdapter = new DeviceAdapter(mVibrationSettings, mVibrators);
        mVibrationConductor = new VibrationStepConductor(vib, isInSession, mVibrationSettings,
                deviceAdapter, mVibrationScaler, mStatsLoggerMock, requestVibrationParamsFuture,
                mManagerHooks);
        assertThat(mThread.runVibrationOnVibrationThread(mVibrationConductor)).isTrue();
        return mVibrationConductor.getVibration();
    }

    private boolean waitUntil(BooleanSupplier predicate, long timeout)
            throws InterruptedException {
        long timeoutTimestamp = SystemClock.uptimeMillis() + timeout;
        boolean predicateResult = false;
        while (!predicateResult && SystemClock.uptimeMillis() < timeoutTimestamp) {
            Thread.sleep(10);
            predicateResult = predicate.getAsBoolean();
        }
        return predicateResult;
    }

    private void waitForCompletion() {
        waitForCompletion(TEST_TIMEOUT_MILLIS);
    }

    private void waitForCompletion(long timeout) {
        assertWithMessage("Timed out waiting for VibrationThread to become idle")
                .that(mThread.waitForThreadIdle(timeout)).isTrue();
        mTestLooper.dispatchAll();  // Flush callbacks
    }

    private HalVibration createVibration(CombinedVibration effect) {
        return new HalVibration(new CallerInfo(ATTRS, UID, DEVICE_ID, PACKAGE_NAME, "reason"),
                effect);
    }

    private SparseArray<HalVibrator> createVibrators() {
        SparseArray<HalVibrator> array = new SparseArray<>();
        for (int i = 0; i < mVibratorHelpers.size(); i++) {
            int id = mVibratorHelpers.keyAt(i);
            HalVibrator vibrator = mVibratorHelpers.valueAt(i)
                    .newInitializedHalVibrator(id, mHalCallbacks);
            array.put(id, vibrator);
        }
        // Start a looper for the vibrators, if it's not already running.
        // TestLooper.AutoDispatchThread has a fixed 1s duration. Use a custom auto-dispatcher.
        if (mCustomTestLooperDispatcher == null) {
            mCustomTestLooperDispatcher = new TestLooperAutoDispatcher(mTestLooper);
            mCustomTestLooperDispatcher.start();
        }
        return array;
    }

    private static PersistableBundle createTestVendorData() {
        PersistableBundle vendorData = new PersistableBundle();
        vendorData.putInt("id", 1);
        vendorData.putDouble("scale", 0.5);
        vendorData.putBoolean("loop", false);
        vendorData.putLongArray("amplitudes", new long[] { 0, 255, 128 });
        vendorData.putString("label", "vibration");
        return vendorData;
    }

    private VibrationEffectSegment expectedOneShot(long millis) {
        return new StepSegment(VibrationEffect.DEFAULT_AMPLITUDE,
                /* frequencyHz= */ 0, (int) millis);
    }

    private List<VibrationEffectSegment> expectedOneShots(long... millis) {
        return Arrays.stream(millis)
                .mapToObj(this::expectedOneShot)
                .collect(Collectors.toList());
    }

    private VibrationEffectSegment expectedPrebaked(int effectId) {
        return new PrebakedSegment(effectId, false, VibrationEffect.EFFECT_STRENGTH_MEDIUM);
    }

    private VibrationEffectSegment expectedPrimitive(int primitiveId, float scale, int delay) {
        return new PrimitiveSegment(primitiveId, scale, delay);
    }

    private VibrationEffectSegment expectedRamp(float amplitude, float frequencyHz, int duration) {
        return expectedRamp(amplitude, amplitude, frequencyHz, frequencyHz, duration);
    }

    private VibrationEffectSegment expectedRamp(float startAmplitude, float endAmplitude,
            float startFrequencyHz, float endFrequencyHz, int duration) {
        return new RampSegment(startAmplitude, endAmplitude, startFrequencyHz, endFrequencyHz,
                duration);
    }

    private PwlePoint expectedPwle(float amplitude, float frequencyHz, int timeMillis) {
        return new PwlePoint(amplitude, frequencyHz, timeMillis);
    }

    private List<Float> expectedAmplitudes(int... amplitudes) {
        return Arrays.stream(amplitudes)
                .mapToObj(amplitude -> amplitude / 255f)
                .collect(Collectors.toList());
    }

    private void verifyCallbacksTriggered(HalVibration vibration, Status expectedStatus) {
        assertThat(vibration.getStatus()).isEqualTo(expectedStatus);
        verify(mManagerHooks).onVibrationThreadReleased(eq(vibration.id));
    }

    private static final class TestLooperAutoDispatcher extends Thread {
        private final TestLooper mTestLooper;
        private boolean mCancelled;

        TestLooperAutoDispatcher(TestLooper testLooper) {
            mTestLooper = testLooper;
        }

        @Override
        public void run() {
            while (!mCancelled) {
                mTestLooper.dispatchAll();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }

        public void cancel() {
            mCancelled = true;
        }
    }
}
