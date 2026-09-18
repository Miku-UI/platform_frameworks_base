/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.biometrics;

import static android.Manifest.permission.ACCESS_BIOMETRIC_SENSOR_STRENGTHS;
import static android.Manifest.permission.MANAGE_BIOMETRIC;
import static android.Manifest.permission.SET_BIOMETRIC_DIALOG_ADVANCED;
import static android.Manifest.permission.TEST_BIOMETRIC;
import static android.Manifest.permission.USE_BIOMETRIC;
import static android.Manifest.permission.USE_BIOMETRIC_INTERNAL;
import static android.Manifest.permission.USE_FINGERPRINT;
import static android.hardware.biometrics.BiometricAuthenticator.TYPE_NONE;
import static android.hardware.biometrics.BiometricConstants.BIOMETRIC_ERROR_CANCELED;
import static android.hardware.biometrics.BiometricConstants.BIOMETRIC_SUCCESS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.UserIdInt;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.biometrics.AuthenticationStateListener;
import android.hardware.biometrics.BiometricEnrollmentStatusInternal;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.IBiometricEnabledOnKeyguardCallback;
import android.hardware.biometrics.IBiometricService;
import android.hardware.biometrics.IBiometricServiceReceiver;
import android.hardware.biometrics.PromptInfo;
import android.hardware.biometrics.SensorProperties;
import android.hardware.biometrics.StrongSensorStrengthInternal;
import android.hardware.biometrics.fingerprint.SensorProps;
import android.hardware.face.FaceSensorConfigurations;
import android.hardware.face.FaceSensorProperties;
import android.hardware.face.FaceSensorPropertiesInternal;
import android.hardware.face.IFaceService;
import android.hardware.fingerprint.FingerprintSensorConfigurations;
import android.hardware.fingerprint.FingerprintSensorProperties;
import android.hardware.fingerprint.FingerprintSensorPropertiesInternal;
import android.hardware.fingerprint.IFingerprintService;
import android.hardware.iris.IIrisService;
import android.os.Binder;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.test.TestLooper;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.Display;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.android.internal.R;
import com.android.server.LocalServices;
import com.android.server.companion.virtual.VirtualDeviceManagerInternal;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Stubber;

import java.util.ArrayList;
import java.util.List;

@Presubmit
@SmallTest
public class AuthServiceTest {

    private static final String TEST_OP_PACKAGE_NAME = "test_package";
    private static final int DISPLAY_ID = Display.DEFAULT_DISPLAY;

    private final @UserIdInt int mUserId = UserHandle.getCallingUserId();

    private AuthService mAuthService;

    @Rule
    public MockitoRule mockitorule = MockitoJUnit.rule();
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    private Context mContext;
    @Mock
    private Resources mResources;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    IBiometricServiceReceiver mReceiver;
    @Mock
    AuthService.Injector mInjector;
    @Mock
    IBiometricService mBiometricService;
    @Mock
    IFingerprintService mFingerprintService;
    @Mock
    IIrisService mIrisService;
    @Mock
    IFaceService mFaceService;
    @Mock
    AppOpsManager mAppOpsManager;
    @Mock
    private VirtualDeviceManagerInternal mVdmInternal;
    @Mock
    private BiometricHandlerProvider mBiometricHandlerProvider;

    @Captor
    private ArgumentCaptor<List<FingerprintSensorPropertiesInternal>> mFingerprintPropsCaptor;
    @Captor
    private ArgumentCaptor<FingerprintSensorConfigurations> mFingerprintSensorConfigurationsCaptor;
    @Captor
    private ArgumentCaptor<FaceSensorConfigurations> mFaceSensorConfigurationsCaptor;
    @Captor
    private ArgumentCaptor<List<FaceSensorPropertiesInternal>> mFacePropsCaptor;

    private final TestLooper mFingerprintLooper = new TestLooper();
    private final TestLooper mFaceLooper = new TestLooper();

    @Before
    public void setUp() {
        // Placeholder test config
        final String[] config = {
                "0:2:15", // ID0:Fingerprint:Strong
                "1:4:15", // ID1:Iris:Strong
                "2:8:15", // ID2:Face:Strong
        };
        LocalServices.removeServiceForTest(VirtualDeviceManagerInternal.class);
        LocalServices.addService(VirtualDeviceManagerInternal.class, mVdmInternal);

        when(mResources.getIntArray(eq(R.array.config_udfps_sensor_props))).thenReturn(new int[0]);
        when(mResources.getBoolean(eq(R.bool.config_is_powerbutton_fps))).thenReturn(false);
        when(mResources.getInteger(eq(R.integer.config_fingerprintMaxTemplatesPerUser))).thenReturn(
                1);
        when(mResources.getBoolean(eq(R.bool.config_faceAuthSupportsSelfIllumination))).thenReturn(
                false);
        when(mResources.getInteger(eq(R.integer.config_faceMaxTemplatesPerUser))).thenReturn(1);

        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.getResources()).thenReturn(mResources);

        when(mInjector.getBiometricService()).thenReturn(mBiometricService);
        when(mInjector.getConfiguration(any())).thenReturn(config);
        when(mInjector.getFaceConfiguration(any())).thenReturn(config);
        when(mInjector.getFingerprintConfiguration(any())).thenReturn(config);
        when(mInjector.getIrisConfiguration(any())).thenReturn(config);
        when(mInjector.getFingerprintService()).thenReturn(mFingerprintService);
        when(mInjector.getFaceService()).thenReturn(mFaceService);
        when(mInjector.getIrisService()).thenReturn(mIrisService);
        when(mInjector.getAppOps(any())).thenReturn(mAppOpsManager);
        when(mInjector.isHidlDisabled(any())).thenReturn(false);
        when(mInjector.getBiometricHandlerProvider()).thenReturn(mBiometricHandlerProvider);
        when(mBiometricHandlerProvider.getFingerprintHandler()).thenReturn(
                new Handler(mFingerprintLooper.getLooper()));
        when(mBiometricHandlerProvider.getFaceHandler()).thenReturn(
                new Handler(mFaceLooper.getLooper()));
        when(mInjector.isForeground(anyInt(), anyInt())).thenReturn(true);

        setInternalAndTestBiometricPermissions(mContext, false /* hasPermission */);

        // By default, set `USE_BIOMETRIC` as permission granted, since it is the basic
        // permission for an app to use device-supported biometric modalities.
        setPermission(mContext, USE_BIOMETRIC, true);

        // `USE_FINGERPRINT` is deprecated, thus setting it as permission denied to prevent
        // `AuthService.checkPermission` from not truly checking `USE_BIOMETRIC` under the hood.
        setPermission(mContext, USE_FINGERPRINT, false);
    }

    @Test
    public void testRegisterNullService_doesNotRegister() throws Exception {
        setInternalAndTestBiometricPermissions(mContext, true /* hasPermission */);

        // Config contains Fingerprint, Iris, Face, but services are all null

        when(mInjector.getFingerprintService()).thenReturn(null);
        when(mInjector.getFaceService()).thenReturn(null);
        when(mInjector.getIrisService()).thenReturn(null);

        mAuthService = new AuthService(mContext, mInjector);
        mAuthService.onStart();

        verify(mBiometricService, never()).registerAuthenticator(
                anyInt(),
                anyInt(),
                anyInt(),
                any());
    }

    @Test
    public void testRegisterAuthenticator_registerAuthenticators() throws RemoteException {
        final int fingerprintId = 0;
        final int fingerprintStrength = 15;

        final int faceId = 1;
        final int faceStrength = 4095;

        final String[] config = {
                // ID0:Fingerprint:Strong
                String.format("%d:2:%d", fingerprintId, fingerprintStrength),
                // ID2:Face:Convenience
                String.format("%d:8:%d", faceId, faceStrength)
        };

        when(mInjector.getFingerprintConfiguration(any())).thenReturn(config);
        when(mInjector.getFaceConfiguration(any())).thenReturn(config);
        when(mInjector.getFingerprintAidlInstances()).thenReturn(new String[]{});
        when(mInjector.getFaceAidlInstances()).thenReturn(new String[]{});

        mAuthService = new AuthService(mContext, mInjector);
        mAuthService.onStart();

        mFingerprintLooper.dispatchAll();
        mFaceLooper.dispatchAll();

        verify(mFingerprintService).registerAuthenticators(
                mFingerprintSensorConfigurationsCaptor.capture());

        final SensorProps[] fingerprintProp = mFingerprintSensorConfigurationsCaptor.getValue()
                .getSensorPropForInstance("defaultHIDL");

        assertEquals(fingerprintProp[0].commonProps.sensorId, fingerprintId);
        assertEquals(fingerprintProp[0].commonProps.sensorStrength,
                Utils.authenticatorStrengthToPropertyStrength(fingerprintStrength));

        verify(mFaceService).registerAuthenticators(mFaceSensorConfigurationsCaptor.capture());

        final android.hardware.biometrics.face.SensorProps[] faceProp =
                mFaceSensorConfigurationsCaptor.getValue()
                        .getSensorPropForInstance("defaultHIDL");

        assertEquals(faceProp[0].commonProps.sensorId, faceId);
        assertEquals(faceProp[0].commonProps.sensorStrength,
                Utils.authenticatorStrengthToPropertyStrength(faceStrength));
    }

    // TODO(b/141025588): Check that an exception is thrown when the userId != callingUserId
    @Test
    public void testAuthenticate_appOpsOk_callsBiometricServiceAuthenticate() throws Exception {
        when(mAppOpsManager.noteOp(eq(AppOpsManager.OP_USE_BIOMETRIC), anyInt(), any(), any(),
                any())).thenReturn(AppOpsManager.MODE_ALLOWED);
        mAuthService = new AuthService(mContext, mInjector);
        mAuthService.onStart();

        final Binder token = new Binder();
        final PromptInfo promptInfo = new PromptInfo();
        final long sessionId = 0;

        mAuthService.mImpl.authenticate(
                token,
                sessionId,
                mUserId,
                mReceiver,
                TEST_OP_PACKAGE_NAME,
                promptInfo);
        waitForIdle();
        verify(mBiometricService).authenticate(
                eq(token),
                eq(sessionId),
                eq(mUserId),
                eq(mReceiver),
                eq(TEST_OP_PACKAGE_NAME),
                eq(promptInfo));
    }

    @Test
    public void testAuthenticate_appOpsDenied_doesNotCallBiometricService() throws Exception {
        when(mAppOpsManager.noteOp(eq(AppOpsManager.OP_USE_BIOMETRIC), anyInt(), any(), any(),
                any())).thenReturn(AppOpsManager.MODE_ERRORED);
        mAuthService = new AuthService(mContext, mInjector);
        mAuthService.onStart();

        final Binder token = new Binder();
        final PromptInfo promptInfo = new PromptInfo();
        final long sessionId = 0;

        mAuthService.mImpl.authenticate(
                token,
                sessionId,
                mUserId,
                mReceiver,
                TEST_OP_PACKAGE_NAME,
                promptInfo);
        waitForIdle();
        verify(mBiometricService, never()).authenticate(
                eq(token),
                eq(sessionId),
                eq(mUserId),
                eq(mReceiver),
                eq(TEST_OP_PACKAGE_NAME),
                eq(promptInfo));
        verify(mReceiver).onError(eq(TYPE_NONE), eq(BIOMETRIC_ERROR_CANCELED), anyInt());
    }

    @Test
    public void testAuthenticate_missingRequiredParam() throws Exception {
        mAuthService = new AuthService(mContext, mInjector);
        mAuthService.onStart();

        final PromptInfo promptInfo = new PromptInfo();
        final long sessionId = 0;

        mAuthService.mImpl.authenticate(
                null /* token */,
                sessionId,
                mUserId,
                mReceiver,
                TEST_OP_PACKAGE_NAME,
                promptInfo);
        waitForIdle();
        verify(mReceiver).onError(eq(TYPE_NONE), eq(BIOMETRIC_ERROR_CANCELED), anyInt());
    }

    @Test
    public void testAuthenticate_noVdmInternalService_noCrash() throws Exception {
        LocalServices.removeServiceForTest(VirtualDeviceManagerInternal.class);
        mAuthService = new AuthService(mContext, mInjector);
        mAuthService.onStart();

        final Binder token = new Binder();

        // This should not crash
        mAuthService.mImpl.authenticate(
                token,
                0, /* sessionId */
                mUserId,
                mReceiver,
                TEST_OP_PACKAGE_NAME,
                new PromptInfo());
        waitForIdle();
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_BP_COMPUTER_CONTROLLED)
    public void testAuthenticate_callsVirtualDeviceManagerOnAuthenticationPrompt()
            throws Exception {
        mAuthService = new AuthService(mContext, mInjector);
        mAuthService.onStart();

        final Binder token = new Binder();

        mAuthService.mImpl.authenticate(
                token,
                0, /* sessionId */
                mUserId,
                mReceiver,
                TEST_OP_PACKAGE_NAME,
                new PromptInfo());
        waitForIdle();

        ArgumentCaptor<Integer> uidCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mVdmInternal).onAuthenticationPrompt(uidCaptor.capture());
        assertEquals((int) (uidCaptor.getValue()), Binder.getCallingUid());
    }

    @Test
    public void testAuthenticate_throwsWhenUsingTestApis() {
        final PromptInfo promptInfo = mock(PromptInfo.class);
        when(promptInfo.requiresInternalPermission()).thenReturn(false);
        when(promptInfo.requiresTestOrInternalPermission()).thenReturn(true);

        testAuthenticate_throwsSecurityException(promptInfo);
    }

    @Test
    public void testAuthenticate_throwsWhenUsingPrivateApis() {
        final PromptInfo promptInfo = mock(PromptInfo.class);
        when(promptInfo.requiresInternalPermission()).thenReturn(true);
        when(promptInfo.requiresTestOrInternalPermission()).thenReturn(false);

        testAuthenticate_throwsSecurityException(promptInfo);
    }

    @Test
    public void testAuthenticate_throwsWhenUsingAdvancedApis() {
        final PromptInfo promptInfo = mock(PromptInfo.class);
        when(promptInfo.requiresAdvancedPermission()).thenReturn(true);

        testAuthenticate_throwsSecurityException(promptInfo);
    }

    private void testAuthenticate_throwsSecurityException(PromptInfo promptInfo) {
        mAuthService = new AuthService(mContext, mInjector);
        mAuthService.onStart();

        assertThrows(SecurityException.class, () -> {
            mAuthService.mImpl.authenticate(
                    null /* token */,
                    10 /* sessionId */,
                    2 /* userId */,
                    mReceiver,
                    TEST_OP_PACKAGE_NAME,
                    promptInfo);
            waitForIdle();
        });
    }

    @Test
    public void testCanAuthenticate_callsBiometricServiceCanAuthenticate() throws Exception {
        mAuthService = new AuthService(mContext, mInjector);
        mAuthService.onStart();

        final int expectedResult = BIOMETRIC_SUCCESS;
        final int authenticators = 0;
        when(mBiometricService.canAuthenticate(anyString(), anyInt(), anyInt(), anyInt(), anyInt()))
                .thenReturn(expectedResult);

        final int result = mAuthService.mImpl
                .canAuthenticate(TEST_OP_PACKAGE_NAME, mUserId, authenticators, DISPLAY_ID);

        assertEquals(expectedResult, result);
        waitForIdle();
        verify(mBiometricService).canAuthenticate(
                eq(TEST_OP_PACKAGE_NAME),
                eq(mUserId),
                eq(UserHandle.getCallingUserId()),
                eq(authenticators),
                eq(DISPLAY_ID));
    }

    @Test
    public void testHasEnrolledBiometrics_callsBiometricServiceHasEnrolledBiometrics()
            throws Exception {
        setInternalAndTestBiometricPermissions(mContext, true /* hasPermission */);

        mAuthService = new AuthService(mContext, mInjector);
        mAuthService.onStart();

        final boolean expectedResult = true;
        when(mBiometricService.hasEnrolledBiometrics(anyInt(), anyString())).thenReturn(
                expectedResult);

        final boolean result = mAuthService.mImpl.hasEnrolledBiometrics(mUserId,
                TEST_OP_PACKAGE_NAME);

        assertEquals(expectedResult, result);
        waitForIdle();
        verify(mBiometricService).hasEnrolledBiometrics(
                eq(mUserId),
                eq(TEST_OP_PACKAGE_NAME));
    }

    @Test
    public void testRegisterAuthenticationStateListener_callsFingerprintService()
            throws Exception {
        setInternalAndTestBiometricPermissions(mContext, true /* hasPermission */);

        mAuthService = new AuthService(mContext, mInjector);
        mAuthService.onStart();

        final AuthenticationStateListener listener = mock(AuthenticationStateListener.class);

        mAuthService.mImpl.registerAuthenticationStateListener(listener);

        waitForIdle();
        verify(mFingerprintService).registerAuthenticationStateListener(
                eq(listener));
    }

    @Test
    public void testRegisterAuthenticationStateListener_callsFaceService() throws Exception {
        setInternalAndTestBiometricPermissions(mContext, true /* hasPermission */);

        mAuthService = new AuthService(mContext, mInjector);
        mAuthService.onStart();

        final AuthenticationStateListener listener = mock(AuthenticationStateListener.class);

        mAuthService.mImpl.registerAuthenticationStateListener(listener);

        waitForIdle();
        verify(mFaceService).registerAuthenticationStateListener(eq(listener));
    }

    @Test
    public void testRegisterKeyguardCallback_callsBiometricServiceRegisterKeyguardCallback()
            throws Exception {
        setInternalAndTestBiometricPermissions(mContext, true /* hasPermission */);

        mAuthService = new AuthService(mContext, mInjector);
        mAuthService.onStart();

        final IBiometricEnabledOnKeyguardCallback callback =
                new IBiometricEnabledOnKeyguardCallback.Default();

        mAuthService.mImpl.registerEnabledOnKeyguardCallback(callback);

        waitForIdle();
        verify(mBiometricService).registerEnabledOnKeyguardCallback(
                eq(callback));
    }

    @Test
    public void testGetLastAuthenticationTime_callsBiometricService()
            throws Exception {
        setInternalAndTestBiometricPermissions(mContext, true /* hasPermission */);

        mAuthService = new AuthService(mContext, mInjector);
        mAuthService.onStart();

        final int authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG;

        mAuthService.mImpl.getLastAuthenticationTime(mUserId, authenticators);

        waitForIdle();
        verify(mBiometricService).getLastAuthenticationTime(eq(mUserId), eq(authenticators));
    }

    @Test
    public void testGetEnrollmentStatus_throwsSecurityException() throws Exception {
        setInternalAndTestBiometricPermissions(mContext, false /* hasPermission */);
        setSensorProperties();
        mAuthService = new AuthService(mContext, mInjector);
        mAuthService.onStart();

        assertThrows(SecurityException.class, () -> {
            mAuthService.mImpl.getEnrollmentStatusList(mUserId, TEST_OP_PACKAGE_NAME);
        });
    }

    @Test
    public void testGetEnrollmentStatus_callsFingerprintAndFaceService() throws Exception {
        setInternalAndTestBiometricPermissions(mContext, true /* hasPermission */);
        setSensorProperties();
        mAuthService = new AuthService(mContext, mInjector);
        mAuthService.onStart();

        final List<BiometricEnrollmentStatusInternal> statusList =
                mAuthService.mImpl.getEnrollmentStatusList(mUserId, TEST_OP_PACKAGE_NAME);

        waitForIdle();
        assertEquals(BiometricManager.Authenticators.BIOMETRIC_STRONG, statusList.get(
                0).getStatus().getStrength());
        assertEquals(BiometricManager.Authenticators.BIOMETRIC_CONVENIENCE, statusList.get(
                1).getStatus().getStrength());

        verify(mFaceService).getEnrolledFaces(eq(0), eq(mUserId), eq(TEST_OP_PACKAGE_NAME));
        verify(mFingerprintService).getEnrolledFingerprints(eq(mUserId), eq(TEST_OP_PACKAGE_NAME),
                eq("tag"));
    }

    @Test
    public void testGetEnrollmentStatus_withNonCallingId_throwsSecurityException()
            throws Exception {
        setInternalAndTestBiometricPermissions(mContext, true /* hasPermission */);
        setPermission(mContext, USE_BIOMETRIC_INTERNAL, false /* hasPermission */);

        final @UserIdInt int nonCallingId = UserHandle.MIN_SECONDARY_USER_ID + 1;
        setSensorProperties();
        mAuthService = new AuthService(mContext, mInjector);
        mAuthService.onStart();

        assertThrows(SecurityException.class, () -> {
            mAuthService.mImpl.getEnrollmentStatusList(nonCallingId, TEST_OP_PACKAGE_NAME);
        });
    }

    @Test
    public void testGetEnrollmentStatus_withNonCallingId_callsFingerprintAndFaceService()
            throws Exception {
        setInternalAndTestBiometricPermissions(mContext, true /* hasPermission */);
        final @UserIdInt int nonCallingId = UserHandle.MIN_SECONDARY_USER_ID + 1;
        setSensorProperties();
        mAuthService = new AuthService(mContext, mInjector);
        mAuthService.onStart();

        final List<BiometricEnrollmentStatusInternal> statusList =
                mAuthService.mImpl.getEnrollmentStatusList(nonCallingId, TEST_OP_PACKAGE_NAME);

        waitForIdle();
        assertEquals(BiometricManager.Authenticators.BIOMETRIC_STRONG, statusList.get(
                0).getStatus().getStrength());
        assertEquals(BiometricManager.Authenticators.BIOMETRIC_CONVENIENCE, statusList.get(
                1).getStatus().getStrength());

        verify(mFaceService, times(0)).getEnrolledFaces(eq(0), eq(mUserId),
                eq(TEST_OP_PACKAGE_NAME));
        verify(mFingerprintService, times(0)).getEnrolledFingerprints(eq(mUserId),
                eq(TEST_OP_PACKAGE_NAME), eq("tag"));
        verify(mFaceService).getEnrolledFaces(eq(0), eq(nonCallingId),
                eq(TEST_OP_PACKAGE_NAME));
        verify(mFingerprintService).getEnrolledFingerprints(eq(nonCallingId),
                eq(TEST_OP_PACKAGE_NAME), eq("tag"));
    }

    @Test
    public void testGetBiometricSensorStrengths_allowedRoleHolder() throws Exception {
        setPermission(mContext, ACCESS_BIOMETRIC_SENSOR_STRENGTHS, true);
        setSensorProperties();

        mAuthService = new AuthService(mContext, mInjector);
        mAuthService.onStart();
        final List<StrongSensorStrengthInternal> actualSensorStrengths =
                mAuthService.mImpl.getBiometricSensorStrengths(TEST_OP_PACKAGE_NAME);

        assertEquals(2, actualSensorStrengths.size());
        long actualFingerprintCount = actualSensorStrengths.stream()
                .filter(s -> s.getModality() == BiometricManager.TYPE_FINGERPRINT)
                .filter(s -> s.getStrength() == BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .count();
        assertEquals("Exactly one Class-3 fingerprint sensor is expected to be retrieved.", 1,
                actualFingerprintCount);
        long actualFaceCount = actualSensorStrengths.stream()
                .filter(s -> s.getModality() == BiometricManager.TYPE_FACE)
                .filter(s -> s.getStrength() == BiometricManager.Authenticators.LESS_THAN_STRONG)
                .count();
        assertEquals("Exactly one unknown/unexposed face sensor is expected to be retrieved.", 1,
                actualFaceCount);
    }

    @Test
    public void testGetBiometricSensorStrengths_rolePermissionDenied_throwsSecurityException()
            throws Exception {
        // From an end-to-end perspective, this could be due to not declaring
        // `ACCESS_BIOMETRIC_SENSOR_STRENGTHS`, or declaring it but not holding the required roles.
        setPermission(mContext, ACCESS_BIOMETRIC_SENSOR_STRENGTHS, false);

        mAuthService = new AuthService(mContext, mInjector);
        mAuthService.onStart();

        assertThrows(SecurityException.class, () -> {
            mAuthService.mImpl.getBiometricSensorStrengths(TEST_OP_PACKAGE_NAME);
        });
    }

    @Test
    public void testGetBiometricSensorStrengths_withoutBioPermission_throwsSecurityException()
            throws Exception {
        setPermission(mContext, USE_BIOMETRIC, false);
        setPermission(mContext, USE_BIOMETRIC_INTERNAL, true);

        mAuthService = new AuthService(mContext, mInjector);
        mAuthService.onStart();

        assertThrows(SecurityException.class, () -> {
            mAuthService.mImpl.getBiometricSensorStrengths(TEST_OP_PACKAGE_NAME);
        });
    }

    @Test
    public void testGetBiometricSensorStrengths_backgroundCaller_throwsSecurityException()
            throws Exception {
        setPermission(mContext, ACCESS_BIOMETRIC_SENSOR_STRENGTHS, true);
        when(mInjector.isForeground(anyInt(), anyInt())).thenReturn(false);

        mAuthService = new AuthService(mContext, mInjector);
        mAuthService.onStart();

        assertThrows(SecurityException.class, () -> {
            mAuthService.mImpl.getBiometricSensorStrengths(TEST_OP_PACKAGE_NAME);
        });
    }

    private void setSensorProperties() throws Exception {
        List<FaceSensorPropertiesInternal> faceProps = List.of(new FaceSensorPropertiesInternal(
                0 /* id */,
                FaceSensorProperties.STRENGTH_CONVENIENCE,
                1 /* maxTemplatesAllowed */,
                new ArrayList<>() /* componentInfo */,
                FaceSensorProperties.TYPE_UNKNOWN,
                true /* supportsFaceDetection */,
                true /* supportsSelfIllumination */,
                false /* resetLockoutRequiresChallenge */));
        List<FingerprintSensorPropertiesInternal> fpProps = List.of(
                new FingerprintSensorPropertiesInternal(1 /* id */,
                        SensorProperties.STRENGTH_STRONG,
                        5 /* maxEnrollmentsPerUser */,
                        new ArrayList<>() /* componentInfo */,
                        FingerprintSensorProperties.TYPE_UDFPS_OPTICAL,
                        false /* resetLockoutRequiresHardwareAuthToken */));
        when(mFaceService.getSensorPropertiesInternal(eq(TEST_OP_PACKAGE_NAME))).thenReturn(
                faceProps);
        when(mFingerprintService.getSensorPropertiesInternal(eq(TEST_OP_PACKAGE_NAME))).thenReturn(
                fpProps);
        when(mContext.getAttributionTag()).thenReturn("tag");
    }

    private static void setInternalAndTestBiometricPermissions(
            Context context, boolean hasPermission) {
        for (String p : List.of(TEST_BIOMETRIC, MANAGE_BIOMETRIC, USE_BIOMETRIC_INTERNAL,
                SET_BIOMETRIC_DIALOG_ADVANCED)) {
            setPermission(context, p, hasPermission);
        }
    }

    private static void setPermission(Context context, String permission, boolean hasPermission) {
        when(context.checkCallingPermission(eq(permission))).thenReturn(hasPermission
                ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED);
        when(context.checkCallingOrSelfPermission(eq(permission))).thenReturn(hasPermission
                ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED);
        final Stubber doPermCheck =
                hasPermission ? doNothing() : doThrow(SecurityException.class);
        doPermCheck.when(context).enforceCallingPermission(eq(permission), any());
        doPermCheck.when(context).enforceCallingOrSelfPermission(eq(permission), any());
    }

    private static void waitForIdle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
}
