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

import static com.android.server.companion.virtual.computercontrol.ComputerControlSessionProcessor.MAXIMUM_CONCURRENT_SESSIONS;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.KeyguardManager;
import android.companion.virtual.IVirtualDevice;
import android.companion.virtual.computercontrol.ComputerControlSession;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.companion.virtual.computercontrol.IComputerControlSession;
import android.companion.virtual.computercontrol.IComputerControlSessionCallback;
import android.companion.virtualdevice.flags.Flags;
import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.ResultReceiver;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.Surface;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.LocalServices;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wm.WindowManagerInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class ComputerControlSessionProcessorTest {

    private static final int CALLBACK_TIMEOUT_MS = 1_000;
    private static final String PACKAGE_NAME_PERMISSION_CONTROLLER = "permission.controller";

    @Rule
    public SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Mock
    private KeyguardManager mKeyguardManager;
    @Mock
    private AppOpsManager mAppOpsManager;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private WindowManagerInternal mWindowManagerInternal;
    @Mock
    private UserManagerInternal mUserManagerInternal;
    @Mock
    private ComputerControlSessionProcessor.VirtualDeviceFactory mVirtualDeviceFactory;
    @Mock
    private ComputerControlSessionProcessor.PendingIntentFactory mPendingIntentFactory;
    @Mock
    private IVirtualDevice mVirtualDevice;
    @Mock
    private IComputerControlSessionCallback mComputerControlSessionCallback;
    @Captor
    private ArgumentCaptor<Intent> mIntentArgumentCaptor;
    @Captor
    private ArgumentCaptor<IComputerControlSession> mSessionArgumentCaptor;

    private final ComputerControlSessionParams mParams = new ComputerControlSessionParams.Builder()
            .setName(ComputerControlSessionTest.class.getSimpleName())
            .setDisplayDpi(100)
            .setDisplayHeightPx(200)
            .setDisplayWidthPx(300)
            .setDisplaySurface(new Surface())
            .setDisplayAlwaysUnlocked(true)
            .build();

    private Context mContext;
    private ComputerControlSessionProcessor mProcessor;

    private AutoCloseable mMockitoSession;

    @Before
    public void setUp() {
        mMockitoSession = MockitoAnnotations.openMocks(this);

        LocalServices.removeServiceForTest(WindowManagerInternal.class);
        LocalServices.addService(WindowManagerInternal.class, mWindowManagerInternal);

        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.addService(UserManagerInternal.class, mUserManagerInternal);

        mContext = spy(new ContextWrapper(
                InstrumentationRegistry.getInstrumentation().getTargetContext()));
        when(mContext.getSystemService(Context.KEYGUARD_SERVICE)).thenReturn(mKeyguardManager);
        when(mContext.getSystemService(Context.APP_OPS_SERVICE)).thenReturn(mAppOpsManager);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);

        when(mAppOpsManager.noteOpNoThrow(eq(AppOpsManager.OP_COMPUTER_CONTROL), any(), any()))
                .thenReturn(AppOpsManager.MODE_ALLOWED);

        when(mPackageManager.getPermissionControllerPackageName())
                .thenReturn("permission.controller");

        when(mVirtualDeviceFactory.createVirtualDevice(any(), any(), any(), any()))
                .thenReturn(mVirtualDevice);
        when(mComputerControlSessionCallback.asBinder()).thenReturn(new Binder());
        mProcessor = new ComputerControlSessionProcessor(
                mContext, mVirtualDeviceFactory, mPendingIntentFactory);
    }

    @After
    public void tearDown() throws Exception {
        mMockitoSession.close();
    }

    @Test
    public void keyguardLocked_sessionNotCreated() throws Exception {
        when(mKeyguardManager.isDeviceLocked()).thenReturn(true);

        mProcessor.processNewSessionRequest(AttributionSource.myAttributionSource(),
                mParams, mComputerControlSessionCallback);
        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreationFailed(ComputerControlSession.ERROR_DEVICE_LOCKED);
    }

    @Test
    public void maximumNumberOfSessions_isEnforced() throws Exception {
        try {
            for (int i = 0; i < MAXIMUM_CONCURRENT_SESSIONS; ++i) {
                mProcessor.processNewSessionRequest(AttributionSource.myAttributionSource(),
                        generateUniqueParams(i), mComputerControlSessionCallback);
            }
            verify(mComputerControlSessionCallback,
                    timeout(CALLBACK_TIMEOUT_MS).times(MAXIMUM_CONCURRENT_SESSIONS))
                    .onSessionCreated(anyInt(), any(), mSessionArgumentCaptor.capture());

            mProcessor.processNewSessionRequest(AttributionSource.myAttributionSource(),
                    generateUniqueParams(-1), mComputerControlSessionCallback);
            verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                    .onSessionCreationFailed(ComputerControlSession.ERROR_SESSION_LIMIT_REACHED);

            // Close the first session.
            mSessionArgumentCaptor.getAllValues().getFirst().close();
            // Closing an already-closed session should be a no-op.
            mSessionArgumentCaptor.getAllValues().getFirst().close();
            verify(mComputerControlSessionCallback, times(1)).onSessionClosed();

            mProcessor.processNewSessionRequest(AttributionSource.myAttributionSource(),
                    generateUniqueParams(-1), mComputerControlSessionCallback);
            verify(mComputerControlSessionCallback,
                    timeout(CALLBACK_TIMEOUT_MS).times(MAXIMUM_CONCURRENT_SESSIONS + 1))
                    .onSessionCreated(anyInt(), any(), mSessionArgumentCaptor.capture());
        } finally {
            for (IComputerControlSession session : mSessionArgumentCaptor.getAllValues()) {
                session.close();
            }
            verify(mComputerControlSessionCallback, times(MAXIMUM_CONCURRENT_SESSIONS + 1))
                    .onSessionClosed();
        }
    }

    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_CONSENT)
    @Test
    public void onSessionPending_consentGranted_sessionCreated() throws Exception {
        when(mAppOpsManager.noteOpNoThrow(eq(AppOpsManager.OP_COMPUTER_CONTROL), any(), any()))
                .thenReturn(AppOpsManager.MODE_IGNORED);

        mProcessor.processNewSessionRequest(AttributionSource.myAttributionSource(),
                mParams, mComputerControlSessionCallback);
        verify(mPendingIntentFactory).create(any(), anyInt(), mIntentArgumentCaptor.capture());
        verify(mComputerControlSessionCallback).onSessionPending(any());

        ResultReceiver resultReceiver = mIntentArgumentCaptor.getValue().getParcelableExtra(
                Intent.EXTRA_RESULT_RECEIVER, ResultReceiver.class);
        resultReceiver.send(Activity.RESULT_OK, null);
        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreated(anyInt(), any(), any());
    }

    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_CONSENT)
    @Test
    public void onSessionPending_consentDenied_sessionCreationFailed() throws Exception {
        when(mAppOpsManager.noteOpNoThrow(eq(AppOpsManager.OP_COMPUTER_CONTROL), any(), any()))
                .thenReturn(AppOpsManager.MODE_IGNORED);

        mProcessor.processNewSessionRequest(AttributionSource.myAttributionSource(),
                mParams, mComputerControlSessionCallback);
        verify(mPendingIntentFactory).create(any(), anyInt(), mIntentArgumentCaptor.capture());
        verify(mComputerControlSessionCallback).onSessionPending(any());

        ResultReceiver resultReceiver = mIntentArgumentCaptor.getValue().getParcelableExtra(
                Intent.EXTRA_RESULT_RECEIVER, ResultReceiver.class);
        resultReceiver.send(Activity.RESULT_CANCELED, null);
        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreationFailed(ComputerControlSession.ERROR_PERMISSION_DENIED);
    }

    @Test
    public void validateParams_sessionNameMustBeUnique() throws Exception {
        mProcessor.processNewSessionRequest(AttributionSource.myAttributionSource(),
                mParams, mComputerControlSessionCallback);
        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreated(anyInt(), any(), any());
        assertThrows(IllegalArgumentException.class,
                () -> mProcessor.processNewSessionRequest(AttributionSource.myAttributionSource(),
                mParams, mComputerControlSessionCallback));
    }

    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_ACTIVITY_POLICY_STRICT)
    @Test
    public void validateParams_packageNamesAreValid() throws Exception {
        String packageName = "package.name";
        ComputerControlSessionParams params = new ComputerControlSessionParams.Builder()
                .setName(ComputerControlSessionTest.class.getSimpleName())
                .setTargetPackageNames(List.of(packageName))
                .build();

        when(mPackageManager.getLaunchIntentForPackage(packageName)).thenReturn(new Intent());

        mProcessor.processNewSessionRequest(AttributionSource.myAttributionSource(),
                params, mComputerControlSessionCallback);

        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreated(anyInt(), any(), any());
    }

    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_ACTIVITY_POLICY_STRICT)
    @Test
    public void validateParams_invalidPackageNames_permissionController() {
        String packageName = PACKAGE_NAME_PERMISSION_CONTROLLER;
        ComputerControlSessionParams params = new ComputerControlSessionParams.Builder()
                .setName(ComputerControlSessionTest.class.getSimpleName())
                .setTargetPackageNames(List.of(packageName))
                .build();

        when(mPackageManager.getPermissionControllerPackageName()).thenReturn(packageName);
        when(mPackageManager.getLaunchIntentForPackage(packageName)).thenReturn(new Intent());

        assertThrows(IllegalArgumentException.class, () -> {
            mProcessor.processNewSessionRequest(AttributionSource.myAttributionSource(),
                    params, mComputerControlSessionCallback);
        });
    }

    @EnableFlags(Flags.FLAG_COMPUTER_CONTROL_ACTIVITY_POLICY_STRICT)
    @Test
    public void validateParams_invalidPackageNames_packageWithoutLauncherIntent() {
        String packageName = "package.name";
        ComputerControlSessionParams params = new ComputerControlSessionParams.Builder()
                .setName(ComputerControlSessionTest.class.getSimpleName())
                .setTargetPackageNames(List.of(packageName))
                .build();

        when(mPackageManager.getLaunchIntentForPackage(packageName)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> {
            mProcessor.processNewSessionRequest(AttributionSource.myAttributionSource(),
                    params, mComputerControlSessionCallback);
        });
    }

    @Test
    public void isComputerControlDisplay_returnsTrueForDisplaysWithActiveSession()
            throws Exception {
        when(mVirtualDevice.createVirtualDisplay(any(), any())).thenReturn(123);
        mProcessor.processNewSessionRequest(AttributionSource.myAttributionSource(),
                mParams, mComputerControlSessionCallback);
        verify(mComputerControlSessionCallback,
                timeout(CALLBACK_TIMEOUT_MS).times(1))
                .onSessionCreated(anyInt(), any(), mSessionArgumentCaptor.capture());

        assertTrue(mProcessor.isComputerControlDisplay(123));

        mSessionArgumentCaptor.getValue().close();
        assertFalse(mProcessor.isComputerControlDisplay(123));
    }

    private ComputerControlSessionParams generateUniqueParams(int index) {
        return new ComputerControlSessionParams.Builder()
                .setName(mParams.getName() + index)
                .setDisplayDpi(mParams.getDisplayDpi())
                .setDisplayHeightPx(mParams.getDisplayHeightPx())
                .setDisplayWidthPx(mParams.getDisplayWidthPx())
                .setDisplaySurface(mParams.getDisplaySurface())
                .setDisplayAlwaysUnlocked(mParams.isDisplayAlwaysUnlocked())
                .build();
    }
}
