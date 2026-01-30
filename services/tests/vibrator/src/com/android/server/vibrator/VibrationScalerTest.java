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

import static android.os.VibrationAttributes.USAGE_NOTIFICATION;
import static android.os.VibrationAttributes.USAGE_RINGTONE;
import static android.os.VibrationAttributes.USAGE_TOUCH;
import static android.os.Vibrator.VIBRATION_INTENSITY_HIGH;
import static android.os.Vibrator.VIBRATION_INTENSITY_LOW;
import static android.os.Vibrator.VIBRATION_INTENSITY_MEDIUM;
import static android.os.Vibrator.VIBRATION_INTENSITY_OFF;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContextWrapper;
import android.content.pm.PackageManagerInternal;
import android.os.ExternalVibrationScale;
import android.os.Handler;
import android.os.PersistableBundle;
import android.os.PowerManagerInternal;
import android.os.UserHandle;
import android.os.VibrationEffect;
import android.os.test.TestLooper;
import android.os.vibrator.Flags;
import android.os.vibrator.PrebakedSegment;
import android.os.vibrator.PrimitiveSegment;
import android.os.vibrator.StepSegment;
import android.os.vibrator.VibrationConfig;
import android.os.vibrator.VibrationEffectSegment;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;

import androidx.test.InstrumentationRegistry;

import com.android.internal.util.test.FakeSettingsProvider;
import com.android.internal.util.test.FakeSettingsProviderRule;
import com.android.server.LocalServices;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class VibrationScalerTest {
    private static final float TOLERANCE = 1e-2f;

    @Rule public MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Rule public FakeSettingsProviderRule mSettingsProviderRule = FakeSettingsProvider.rule();
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock private PowerManagerInternal mPowerManagerInternalMock;
    @Mock private PackageManagerInternal mPackageManagerInternalMock;

    private TestLooper mTestLooper;
    private ContextWrapper mContextSpy;
    private VibrationConfig.Builder mConfigBuilder;
    private VibrationSettings mVibrationSettings;

    @Before
    public void setUp() throws Exception {
        mTestLooper = new TestLooper();
        mContextSpy = spy(new ContextWrapper(InstrumentationRegistry.getContext()));

        ContentResolver contentResolver = mSettingsProviderRule.mockContentResolver(mContextSpy);
        when(mContextSpy.getContentResolver()).thenReturn(contentResolver);
        when(mPackageManagerInternalMock.getSystemUiServiceComponent())
                .thenReturn(new ComponentName("", ""));

        LocalServices.removeServiceForTest(PackageManagerInternal.class);
        LocalServices.addService(PackageManagerInternal.class, mPackageManagerInternalMock);
        LocalServices.removeServiceForTest(PowerManagerInternal.class);
        LocalServices.addService(PowerManagerInternal.class, mPowerManagerInternalMock);

        Settings.System.putInt(contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1);
        Settings.System.putInt(contentResolver, Settings.System.VIBRATE_WHEN_RINGING, 1);

        mConfigBuilder = new VibrationConfig.Builder(null); // use defaults
    }

    @After
    public void tearDown() throws Exception {
        LocalServices.removeServiceForTest(PowerManagerInternal.class);
    }

    @Test
    public void testGetScaleLevel() {
        mConfigBuilder.setDefaultHapticFeedbackIntensity(VIBRATION_INTENSITY_LOW);
        VibrationScaler scaler = createSystemReadyScaler();
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_HIGH);
        assertEquals(ExternalVibrationScale.ScaleLevel.SCALE_VERY_HIGH,
                scaler.getScaleLevel(USAGE_TOUCH));

        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_MEDIUM);
        assertEquals(ExternalVibrationScale.ScaleLevel.SCALE_HIGH,
                scaler.getScaleLevel(USAGE_TOUCH));

        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_LOW);
        assertEquals(ExternalVibrationScale.ScaleLevel.SCALE_NONE,
                scaler.getScaleLevel(USAGE_TOUCH));

        mConfigBuilder.setDefaultHapticFeedbackIntensity(VIBRATION_INTENSITY_MEDIUM);
        scaler = createSystemReadyScaler();
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_LOW);
        assertEquals(ExternalVibrationScale.ScaleLevel.SCALE_LOW,
                scaler.getScaleLevel(USAGE_TOUCH));

        mConfigBuilder.setDefaultHapticFeedbackIntensity(VIBRATION_INTENSITY_HIGH);
        scaler = createSystemReadyScaler();
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_LOW);
        assertEquals(ExternalVibrationScale.ScaleLevel.SCALE_VERY_LOW,
                scaler.getScaleLevel(USAGE_TOUCH));

        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_OFF);
        // Vibration setting being bypassed will use default setting and not scale.
        assertEquals(ExternalVibrationScale.ScaleLevel.SCALE_NONE,
                scaler.getScaleLevel(USAGE_TOUCH));
    }

    @Test
    @DisableFlags(Flags.FLAG_HAPTICS_SCALE_V2_ENABLED)
    public void testGetScaleFactor_withLegacyScaling() {
        // Default scale gain will be ignored.
        mConfigBuilder.setDefaultVibrationScaleLevelGain(1.4f);
        mConfigBuilder.setDefaultHapticFeedbackIntensity(VIBRATION_INTENSITY_LOW);
        VibrationScaler scaler = createSystemReadyScaler();
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_HIGH);
        assertEquals(1.4f, scaler.getScaleFactor(USAGE_TOUCH, false), TOLERANCE); // VERY_HIGH

        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_MEDIUM);
        assertEquals(1.2f, scaler.getScaleFactor(USAGE_TOUCH, false), TOLERANCE); // HIGH

        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_LOW);
        assertEquals(1f, scaler.getScaleFactor(USAGE_TOUCH, false), TOLERANCE); // NONE

        mConfigBuilder.setDefaultHapticFeedbackIntensity(VIBRATION_INTENSITY_MEDIUM);
        scaler = createSystemReadyScaler();
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_LOW);
        assertEquals(0.8f, scaler.getScaleFactor(USAGE_TOUCH, false), TOLERANCE); // LOW

        mConfigBuilder.setDefaultHapticFeedbackIntensity(VIBRATION_INTENSITY_HIGH);
        scaler = createSystemReadyScaler();
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_LOW);
        assertEquals(0.6f, scaler.getScaleFactor(USAGE_TOUCH, false), TOLERANCE); // VERY_LOW

        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_OFF);
        // Vibration setting being bypassed will use default setting and not scale.
        assertEquals(1f, scaler.getScaleFactor(USAGE_TOUCH, false), TOLERANCE); // NONE
    }

    @Test
    @EnableFlags(Flags.FLAG_HAPTICS_SCALE_V2_ENABLED)
    public void testGetScaleFactor_withScalingV2() {
        // Test scale factors for a default gain of 1.4
        mConfigBuilder.setDefaultVibrationScaleLevelGain(1.4f);
        mConfigBuilder.setDefaultHapticFeedbackIntensity(VIBRATION_INTENSITY_LOW);
        VibrationScaler scaler = createSystemReadyScaler();
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_HIGH);
        assertEquals(1.95f, scaler.getScaleFactor(USAGE_TOUCH, false), TOLERANCE); // VERY_HIGH

        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_MEDIUM);
        assertEquals(1.4f, scaler.getScaleFactor(USAGE_TOUCH, false), TOLERANCE); // HIGH

        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_LOW);
        assertEquals(1f, scaler.getScaleFactor(USAGE_TOUCH, false), TOLERANCE); // NONE

        mConfigBuilder.setDefaultHapticFeedbackIntensity(VIBRATION_INTENSITY_MEDIUM);
        scaler = createSystemReadyScaler();
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_LOW);
        assertEquals(0.71f, scaler.getScaleFactor(USAGE_TOUCH, false), TOLERANCE); // LOW

        mConfigBuilder.setDefaultHapticFeedbackIntensity(VIBRATION_INTENSITY_HIGH);
        scaler = createSystemReadyScaler();
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_LOW);
        assertEquals(0.51f, scaler.getScaleFactor(USAGE_TOUCH, false), TOLERANCE); // VERY_LOW

        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_OFF);
        // Vibration setting being bypassed will use default setting and not scale.
        assertEquals(1f, scaler.getScaleFactor(USAGE_TOUCH, false), TOLERANCE); // NONE
    }

    @Test
    @EnableFlags(Flags.FLAG_VIBRATION_SCALE_DEVICE_CONFIG_ENABLED)
    public void testGetScaleFactor_withConfig_returnsConfigForCurrentSetting() {
        // Default scale gain will be ignored.
        mConfigBuilder.setDefaultVibrationScaleLevelGain(1.4f);
        mConfigBuilder.setVibrationScaleFactors(new float[] { 0.1f, 0.2f, 0.3f });
        mConfigBuilder.setExternalVibrationScaleFactors(new float[] { 0.4f, 0.5f, 0.6f });
        mConfigBuilder.setDefaultHapticFeedbackIntensity(VIBRATION_INTENSITY_LOW);
        VibrationScaler scaler = createSystemReadyScaler();

        // Default intensity ignored with scale factor device config
        // HIGH
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_HIGH);
        assertEquals(0.3f, scaler.getScaleFactor(USAGE_TOUCH, false), TOLERANCE);
        mConfigBuilder.setDefaultHapticFeedbackIntensity(VIBRATION_INTENSITY_MEDIUM);
        scaler = createSystemReadyScaler();
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_HIGH);
        assertEquals(0.3f, scaler.getScaleFactor(USAGE_TOUCH, false), TOLERANCE);
        mConfigBuilder.setDefaultHapticFeedbackIntensity(VIBRATION_INTENSITY_HIGH);
        scaler = createSystemReadyScaler();
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_HIGH);
        assertEquals(0.3f, scaler.getScaleFactor(USAGE_TOUCH, false), TOLERANCE);
        // MEDIUM
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_MEDIUM);
        assertEquals(0.2f, scaler.getScaleFactor(USAGE_TOUCH, false), TOLERANCE);
        // LOW
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_LOW);
        assertEquals(0.1f, scaler.getScaleFactor(USAGE_TOUCH, false), TOLERANCE);
        // OFF
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_OFF);
        assertEquals(1f, scaler.getScaleFactor(USAGE_TOUCH, false), TOLERANCE);
    }

    @Test
    @EnableFlags(Flags.FLAG_VIBRATION_SCALE_DEVICE_CONFIG_ENABLED)
    public void testGetScaleFactor_withConfigForExternalVibration_returnsConfigForCurrentSetting() {
        // Default scale gain will be ignored.
        mConfigBuilder.setDefaultVibrationScaleLevelGain(1.4f);
        mConfigBuilder.setVibrationScaleFactors(new float[] { 0.1f, 0.2f, 0.3f });
        mConfigBuilder.setExternalVibrationScaleFactors(new float[] { 0.4f, 0.5f, 0.6f });
        mConfigBuilder.setDefaultHapticFeedbackIntensity(VIBRATION_INTENSITY_LOW);
        VibrationScaler scaler = createSystemReadyScaler();

        // Default intensity ignored with scale factor device config
        // HIGH
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_HIGH);
        assertEquals(0.6f, scaler.getScaleFactor(USAGE_TOUCH, true), TOLERANCE);
        mConfigBuilder.setDefaultHapticFeedbackIntensity(VIBRATION_INTENSITY_MEDIUM);
        scaler = createSystemReadyScaler();
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_HIGH);
        assertEquals(0.6f, scaler.getScaleFactor(USAGE_TOUCH, true), TOLERANCE);
        mConfigBuilder.setDefaultHapticFeedbackIntensity(VIBRATION_INTENSITY_HIGH);
        scaler = createSystemReadyScaler();
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_HIGH);
        assertEquals(0.6f, scaler.getScaleFactor(USAGE_TOUCH, true), TOLERANCE);
        // MEDIUM
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_MEDIUM);
        assertEquals(0.5f, scaler.getScaleFactor(USAGE_TOUCH, true), TOLERANCE);
        // LOW
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_LOW);
        assertEquals(0.4f, scaler.getScaleFactor(USAGE_TOUCH, true), TOLERANCE);
        // OFF
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_OFF);
        assertEquals(1f, scaler.getScaleFactor(USAGE_TOUCH, true), TOLERANCE);
    }

    @Test
    public void testAdaptiveHapticsScale_withAdaptiveHapticsAvailable() {
        VibrationScaler scaler = createSystemReadyScaler();
        scaler.updateAdaptiveHapticsScale(USAGE_TOUCH, 0.5f);
        scaler.updateAdaptiveHapticsScale(USAGE_RINGTONE, 0.2f);

        assertEquals(0.5f, scaler.getAdaptiveHapticsScale(USAGE_TOUCH));
        assertEquals(0.2f, scaler.getAdaptiveHapticsScale(USAGE_RINGTONE));
        assertEquals(1f, scaler.getAdaptiveHapticsScale(USAGE_NOTIFICATION));
        assertEquals(0.2f, scaler.getAdaptiveHapticsScale(USAGE_RINGTONE));
    }

    @Test
    public void scale_withPrebakedSegment_setsEffectStrengthBasedOnSettings() {
        mConfigBuilder.setDefaultNotificationVibrationIntensity(VIBRATION_INTENSITY_MEDIUM);
        VibrationScaler scaler = createSystemReadyScaler();
        setUserSetting(Settings.System.NOTIFICATION_VIBRATION_INTENSITY, VIBRATION_INTENSITY_HIGH);

        PrebakedSegment effect = new PrebakedSegment(VibrationEffect.EFFECT_CLICK,
                /* shouldFallback= */ false, VibrationEffect.EFFECT_STRENGTH_MEDIUM);

        PrebakedSegment scaled = scaler.scale(effect, USAGE_NOTIFICATION);
        assertEquals(scaled.getEffectStrength(), VibrationEffect.EFFECT_STRENGTH_STRONG);

        setUserSetting(Settings.System.NOTIFICATION_VIBRATION_INTENSITY,
                VIBRATION_INTENSITY_MEDIUM);
        scaled = scaler.scale(effect, USAGE_NOTIFICATION);
        assertEquals(scaled.getEffectStrength(), VibrationEffect.EFFECT_STRENGTH_MEDIUM);

        setUserSetting(Settings.System.NOTIFICATION_VIBRATION_INTENSITY, VIBRATION_INTENSITY_LOW);
        scaled = scaler.scale(effect, USAGE_NOTIFICATION);
        assertEquals(scaled.getEffectStrength(), VibrationEffect.EFFECT_STRENGTH_LIGHT);

        setUserSetting(Settings.System.NOTIFICATION_VIBRATION_INTENSITY, VIBRATION_INTENSITY_OFF);
        scaled = scaler.scale(effect, USAGE_NOTIFICATION);
        // Vibration setting being bypassed will use default setting.
        assertEquals(scaled.getEffectStrength(), VibrationEffect.EFFECT_STRENGTH_MEDIUM);
    }

    @Test
    public void scale_withPrebakedEffect_setsEffectStrengthBasedOnSettings() {
        mConfigBuilder.setDefaultNotificationVibrationIntensity(VIBRATION_INTENSITY_LOW);
        VibrationScaler scaler = createSystemReadyScaler();
        setUserSetting(Settings.System.NOTIFICATION_VIBRATION_INTENSITY, VIBRATION_INTENSITY_HIGH);

        VibrationEffect effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK);
        PrebakedSegment scaled = getFirstSegment(scaler.scale(effect, USAGE_NOTIFICATION));
        assertEquals(scaled.getEffectStrength(), VibrationEffect.EFFECT_STRENGTH_STRONG);

        setUserSetting(Settings.System.NOTIFICATION_VIBRATION_INTENSITY,
                VIBRATION_INTENSITY_MEDIUM);
        scaled = getFirstSegment(scaler.scale(effect, USAGE_NOTIFICATION));
        assertEquals(scaled.getEffectStrength(), VibrationEffect.EFFECT_STRENGTH_MEDIUM);

        setUserSetting(Settings.System.NOTIFICATION_VIBRATION_INTENSITY, VIBRATION_INTENSITY_LOW);
        scaled = getFirstSegment(scaler.scale(effect, USAGE_NOTIFICATION));
        assertEquals(scaled.getEffectStrength(), VibrationEffect.EFFECT_STRENGTH_LIGHT);

        setUserSetting(Settings.System.NOTIFICATION_VIBRATION_INTENSITY, VIBRATION_INTENSITY_OFF);
        scaled = getFirstSegment(scaler.scale(effect, USAGE_NOTIFICATION));
        // Vibration setting being bypassed will use default setting.
        assertEquals(scaled.getEffectStrength(), VibrationEffect.EFFECT_STRENGTH_LIGHT);
    }

    @Test
    @EnableFlags(android.os.vibrator.Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void scale_withVendorEffect_setsEffectStrengthAndScaleBasedOnSettings() {
        mConfigBuilder.setDefaultNotificationVibrationIntensity(VIBRATION_INTENSITY_MEDIUM);
        VibrationScaler scaler = createSystemReadyScaler();
        setUserSetting(Settings.System.NOTIFICATION_VIBRATION_INTENSITY, VIBRATION_INTENSITY_HIGH);

        PersistableBundle vendorData = new PersistableBundle();
        vendorData.putString("key", "value");
        VibrationEffect effect = VibrationEffect.createVendorEffect(vendorData);

        VibrationEffect.VendorEffect scaled =
                (VibrationEffect.VendorEffect) scaler.scale(effect, USAGE_NOTIFICATION);
        assertEquals(scaled.getEffectStrength(), VibrationEffect.EFFECT_STRENGTH_STRONG);
        // Notification scales up.
        assertTrue(scaled.getScale() > 1);

        setUserSetting(Settings.System.NOTIFICATION_VIBRATION_INTENSITY,
                VIBRATION_INTENSITY_MEDIUM);
        scaled = (VibrationEffect.VendorEffect) scaler.scale(effect, USAGE_NOTIFICATION);
        assertEquals(scaled.getEffectStrength(), VibrationEffect.EFFECT_STRENGTH_MEDIUM);
        // Notification does not scale.
        assertEquals(1, scaled.getScale(), TOLERANCE);

        setUserSetting(Settings.System.NOTIFICATION_VIBRATION_INTENSITY, VIBRATION_INTENSITY_LOW);
        scaled = (VibrationEffect.VendorEffect) scaler.scale(effect, USAGE_NOTIFICATION);
        assertEquals(scaled.getEffectStrength(), VibrationEffect.EFFECT_STRENGTH_LIGHT);
        // Notification scales down.
        assertTrue(scaled.getScale() < 1);

        setUserSetting(Settings.System.NOTIFICATION_VIBRATION_INTENSITY, VIBRATION_INTENSITY_OFF);
        scaled = (VibrationEffect.VendorEffect) scaler.scale(effect, USAGE_NOTIFICATION);
        // Vibration setting being bypassed will use default setting.
        assertEquals(scaled.getEffectStrength(), VibrationEffect.EFFECT_STRENGTH_MEDIUM);
        assertEquals(1, scaled.getScale(), TOLERANCE);
    }

    @Test
    public void scale_withOneShotAndWaveform_resolvesAmplitude() {
        int defaultAmplitude = 100;
        mConfigBuilder.setDefaultVibrationScaleLevelGain(1.4f);
        mConfigBuilder.setDefaultVibrationAmplitude(defaultAmplitude);
        mConfigBuilder.setDefaultRingVibrationIntensity(VIBRATION_INTENSITY_LOW);
        VibrationScaler scaler = createSystemReadyScaler();
        // No scale, default amplitude still resolved
        setUserSetting(Settings.System.RING_VIBRATION_INTENSITY, VIBRATION_INTENSITY_LOW);

        StepSegment resolved = getFirstSegment(scaler.scale(
                VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE),
                USAGE_RINGTONE));
        assertEquals(defaultAmplitude / 255f, resolved.getAmplitude(), TOLERANCE);

        resolved = getFirstSegment(scaler.scale(
                VibrationEffect.createWaveform(new long[]{10},
                        new int[]{VibrationEffect.DEFAULT_AMPLITUDE}, -1),
                USAGE_RINGTONE));
        assertEquals(defaultAmplitude / 255f, resolved.getAmplitude(), TOLERANCE);
    }

    @Test
    public void scale_withOneShotAndWaveform_scalesAmplitude() {
        mConfigBuilder.setDefaultRingVibrationIntensity(VIBRATION_INTENSITY_LOW);
        mConfigBuilder.setDefaultHapticFeedbackIntensity(VIBRATION_INTENSITY_MEDIUM);
        mConfigBuilder.setDefaultNotificationVibrationIntensity(VIBRATION_INTENSITY_HIGH);
        VibrationScaler scaler = createSystemReadyScaler();
        setUserSetting(Settings.System.RING_VIBRATION_INTENSITY, VIBRATION_INTENSITY_HIGH);
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_MEDIUM);
        setUserSetting(Settings.System.NOTIFICATION_VIBRATION_INTENSITY, VIBRATION_INTENSITY_LOW);

        StepSegment scaled = getFirstSegment(scaler.scale(
                VibrationEffect.createOneShot(128, 128), USAGE_RINGTONE));
        // Ringtone scales up.
        assertTrue(scaled.getAmplitude() > 0.5);

        scaled = getFirstSegment(scaler.scale(
                VibrationEffect.createWaveform(new long[]{128}, new int[]{128}, -1),
                USAGE_NOTIFICATION));
        // Notification scales down.
        assertTrue(scaled.getAmplitude() < 0.5);

        scaled = getFirstSegment(scaler.scale(VibrationEffect.createOneShot(128, 128),
                USAGE_TOUCH));
        // Haptic feedback does not scale.
        assertEquals(128f / 255, scaled.getAmplitude(), TOLERANCE);
    }

    @Test
    public void scale_withComposed_scalesPrimitives() {
        mConfigBuilder.setDefaultRingVibrationIntensity(VIBRATION_INTENSITY_LOW);
        mConfigBuilder.setDefaultHapticFeedbackIntensity(VIBRATION_INTENSITY_MEDIUM);
        mConfigBuilder.setDefaultNotificationVibrationIntensity(VIBRATION_INTENSITY_HIGH);
        VibrationScaler scaler = createSystemReadyScaler();
        setUserSetting(Settings.System.RING_VIBRATION_INTENSITY, VIBRATION_INTENSITY_HIGH);
        setUserSetting(Settings.System.NOTIFICATION_VIBRATION_INTENSITY, VIBRATION_INTENSITY_LOW);
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_MEDIUM);

        VibrationEffect composed = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f).compose();

        PrimitiveSegment scaled = getFirstSegment(scaler.scale(composed, USAGE_RINGTONE));
        // Ringtone scales up.
        assertTrue(scaled.getScale() > 0.5f);

        scaled = getFirstSegment(scaler.scale(composed, USAGE_NOTIFICATION));
        // Notification scales down.
        assertTrue(scaled.getScale() < 0.5f);

        scaled = getFirstSegment(scaler.scale(composed, USAGE_TOUCH));
        // Haptic feedback does not scale.
        assertEquals(0.5, scaled.getScale(), TOLERANCE);
    }

    @Test
    @EnableFlags(Flags.FLAG_VIBRATION_SCALE_DEVICE_CONFIG_ENABLED)
    public void scale_withDeviceConfig_usesConfigScales() {
        // Default scale gain will be ignored.
        mConfigBuilder.setDefaultVibrationScaleLevelGain(1.4f);
        mConfigBuilder.setVibrationScaleFactors(new float[] { 0.1f, 0.2f, 0.3f });
        mConfigBuilder.setExternalVibrationScaleFactors(new float[] { 0.4f, 0.5f, 0.6f });
        mConfigBuilder.setDefaultRingVibrationIntensity(VIBRATION_INTENSITY_LOW);
        mConfigBuilder.setDefaultHapticFeedbackIntensity(VIBRATION_INTENSITY_MEDIUM);
        mConfigBuilder.setDefaultNotificationVibrationIntensity(VIBRATION_INTENSITY_HIGH);
        VibrationScaler scaler = createSystemReadyScaler();
        setUserSetting(Settings.System.RING_VIBRATION_INTENSITY, VIBRATION_INTENSITY_HIGH);
        setUserSetting(Settings.System.NOTIFICATION_VIBRATION_INTENSITY, VIBRATION_INTENSITY_LOW);
        setUserSetting(Settings.System.HAPTIC_FEEDBACK_INTENSITY, VIBRATION_INTENSITY_MEDIUM);

        VibrationEffect composed = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.5f).compose();

        PrimitiveSegment scaled = getFirstSegment(scaler.scale(composed, USAGE_RINGTONE));
        // Ringtone is HIGH.
        assertEquals(0.15f, scaled.getScale(), TOLERANCE);

        scaled = getFirstSegment(scaler.scale(composed, USAGE_TOUCH));
        // Haptic feedback is MEDIUM.
        assertEquals(0.1f, scaled.getScale(), TOLERANCE);

        scaled = getFirstSegment(scaler.scale(composed, USAGE_NOTIFICATION));
        // Notification  isLOW.
        assertEquals(0.05f, scaled.getScale(), TOLERANCE);
    }

    @Test
    public void scale_withAdaptiveHaptics_scalesVibrationsCorrectly() {
        mConfigBuilder.setDefaultRingVibrationIntensity(VIBRATION_INTENSITY_HIGH);
        mConfigBuilder.setDefaultNotificationVibrationIntensity(VIBRATION_INTENSITY_HIGH);
        VibrationScaler scaler = createSystemReadyScaler();

        scaler.updateAdaptiveHapticsScale(USAGE_RINGTONE, 0.5f);
        scaler.updateAdaptiveHapticsScale(USAGE_NOTIFICATION, 0.5f);

        StepSegment scaled = getFirstSegment(scaler.scale(
                VibrationEffect.createOneShot(128, 128), USAGE_RINGTONE));
        // Ringtone scales down.
        assertTrue(scaled.getAmplitude() < 0.5);

        scaled = getFirstSegment(scaler.scale(
                VibrationEffect.createWaveform(new long[]{128}, new int[]{128}, -1),
                USAGE_NOTIFICATION));
        // Notification scales down.
        assertTrue(scaled.getAmplitude() < 0.5);
    }

    @Test
    public void scale_removeAdaptiveHapticsScale_removesCachedScale() {
        mConfigBuilder.setDefaultRingVibrationIntensity(VIBRATION_INTENSITY_HIGH);
        mConfigBuilder.setDefaultNotificationVibrationIntensity(VIBRATION_INTENSITY_HIGH);
        VibrationScaler scaler = createSystemReadyScaler();

        scaler.updateAdaptiveHapticsScale(USAGE_RINGTONE, 0.5f);
        scaler.updateAdaptiveHapticsScale(USAGE_NOTIFICATION, 0.5f);
        scaler.removeAdaptiveHapticsScale(USAGE_NOTIFICATION);

        StepSegment scaled = getFirstSegment(scaler.scale(
                VibrationEffect.createOneShot(128, 128), USAGE_RINGTONE));
        // Ringtone scales down.
        assertTrue(scaled.getAmplitude() < 0.5);

        scaled = getFirstSegment(scaler.scale(
                VibrationEffect.createWaveform(new long[]{128}, new int[]{128}, -1),
                USAGE_NOTIFICATION));
        // Notification scales up.
        assertTrue(scaled.getAmplitude() > 0.5);
    }

    @Test
    @EnableFlags(android.os.vibrator.Flags.FLAG_VENDOR_VIBRATION_EFFECTS)
    public void scale_adaptiveHapticsOnVendorEffect_setsAdaptiveScaleParameter() {
        mConfigBuilder.setDefaultRingVibrationIntensity(VIBRATION_INTENSITY_HIGH);
        VibrationScaler scaler = createSystemReadyScaler();

        scaler.updateAdaptiveHapticsScale(USAGE_RINGTONE, 0.5f);

        PersistableBundle vendorData = new PersistableBundle();
        vendorData.putInt("key", 1);
        VibrationEffect effect = VibrationEffect.createVendorEffect(vendorData);

        VibrationEffect.VendorEffect scaled =
                (VibrationEffect.VendorEffect) scaler.scale(effect, USAGE_RINGTONE);
        assertEquals(scaled.getAdaptiveScale(), 0.5f);

        scaler.removeAdaptiveHapticsScale(USAGE_RINGTONE);

        scaled = (VibrationEffect.VendorEffect) scaler.scale(effect, USAGE_RINGTONE);
        assertEquals(scaled.getAdaptiveScale(), 1.0f);
    }

    private VibrationScaler createSystemReadyScaler() {
        mVibrationSettings = new VibrationSettings(
                mContextSpy, new Handler(mTestLooper.getLooper()), mConfigBuilder.build());
        mVibrationSettings.onSystemReady();
        return new VibrationScaler(mConfigBuilder.build(), mVibrationSettings);
    }

    private <T extends VibrationEffectSegment> T getFirstSegment(VibrationEffect effect) {
        assertTrue(effect instanceof VibrationEffect.Composed);
        return (T) ((VibrationEffect.Composed) effect).getSegments().get(0);
    }

    private void setUserSetting(String settingName, int value) {
        Settings.System.putIntForUser(
                mContextSpy.getContentResolver(), settingName, value, UserHandle.USER_CURRENT);
        // FakeSettingsProvider don't support testing triggering ContentObserver yet.
        mVibrationSettings.mSettingObserver.onChange(false);
    }
}
