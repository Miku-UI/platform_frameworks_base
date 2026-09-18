/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.pm;

import static android.platform.test.flag.junit.SetFlagsRule.DefaultInitValueType.NULL_DEFAULT;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.ArrayMap;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.Random;

// atest PackageManagerServiceVerificationTimeoutTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class PackageManagerServiceVerificationTimeoutTest {
    Handler mHandler;
    PackageManagerServiceInjector mInjector;
    SharedLibrariesImpl mSharedLibraries;

    @Rule
    public final MockSystemRule rule = new MockSystemRule();

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule(NULL_DEFAULT);

    PackageManagerService mPms;
    private IPackageManager mIPackageManager;

    private int mCallingUid;

    @Before
    public void setUp() {
        PackageManagerServiceTestParams mTestParams = new PackageManagerServiceTestParams();
        mTestParams.packages = new ArrayMap<>();
        mInjector = rule.mocks().getInjector();
        mCallingUid = Binder.getCallingUid();
        mSharedLibraries = new SharedLibrariesImpl(null, mInjector);
        mHandler = mock(Handler.class);

        when(mInjector.getHandler()).thenReturn(mHandler);
        when(mInjector.getSharedLibrariesImpl()).thenReturn(mSharedLibraries);
        mPms = new PackageManagerService(mInjector, mTestParams);
        mIPackageManager = mPms.new IPackageManagerImpl();
        doNothing().when(mHandler).removeCallbacksAndMessages(any());
        when(mHandler.obtainMessage(PackageManagerService.PACKAGE_VERIFIED))
                .thenReturn(new Message());
    }

    @Test
    public void testExtendVerificationTimeout_callNotAllowedWhenIdNotAlreadyPendingVerification()
            throws Exception {
        // Use a negative ID to bypass permission check during testing.
        final int id = -1;
        // We first capture runnable from the post call and execute that to verify
        // mHandler.obtainMessage() call.
        Runnable runnable = getRunnableFromHandlerPostCall(id, 1000L);

        // Test.
        runnable.run();

        // When id is not already pending verification, we exit early.
        verify(mHandler, never()).obtainMessage(eq(PackageManagerService.PACKAGE_VERIFIED));
    }

    @Test
    public void testExtendVerificationTimeout_negativeDelayValue() throws Exception {
        // Use a negative ID to bypass permission check during testing.
        final int id = -1;
        PackageVerificationState pvs = new PackageVerificationState(null);
        // Id already pending verification.
        mPms.mPendingVerification.set(-id, pvs);
        // Add callingUid as a required verifier.
        pvs.addRequiredVerifierUid(mCallingUid);
        // We first capture runnable from the post call and execute that to verify
        // mHandler.obtainMessage() call.
        Runnable runnable = getRunnableFromHandlerPostCall(id, -100L);
        ArgumentCaptor<Message> responseCaptor = ArgumentCaptor.forClass(Message.class);

        // Test.
        runnable.run();

        // 0 is used as delay when delay input is negative.
        verify(mHandler, times(1)).obtainMessage(eq(PackageManagerService.PACKAGE_VERIFIED));
        verify(mHandler, times(1)).sendMessageDelayed(responseCaptor.capture(), eq(0L));
        assertEqualMessage(responseCaptor.getValue(), -id, PackageManager.VERIFICATION_REJECT);
    }

    @Test
    @EnableFlags(android.content.pm.Flags.FLAG_EXTEND_VERIFICATION_TIMEOUT_MULTIPLE_TIMES)
    public void testExtendVerificationTimeout_multipleCallsAllowed() throws Exception {
        // Use a negative ID to bypass permission check during testing.
        final int id = -1;
        PackageVerificationState pvs = new PackageVerificationState(null);
        // Id already pending verification.
        mPms.mPendingVerification.set(-id, pvs);
        // Add callingUid as a required verifier.
        pvs.addRequiredVerifierUid(mCallingUid);
        // We first capture runnables from the post call and execute that to verify
        // mHandler.obtainMessage() call.
        Runnable runnable = getRunnableFromHandlerPostCall(id, 1000L);
        Random r = new Random();
        ArgumentCaptor<Message> responseCaptor = ArgumentCaptor.forClass(Message.class);

        // Test.
        runnable.run();
        runnable.run();

        // When flag is enabled, multiple calls to the API are allowed.
        verify(mHandler, times(2)).removeEqualMessages(eq(PackageManagerService.PACKAGE_VERIFIED),
                eq(new PackageVerificationResponse(r.nextInt(), mCallingUid)));
        verify(mHandler, times(2)).obtainMessage(eq(PackageManagerService.PACKAGE_VERIFIED));
        verify(mHandler, times(1)).sendMessageDelayed(responseCaptor.capture(), eq(1000L));
        assertEqualMessage(responseCaptor.getValue(), -id, PackageManager.VERIFICATION_REJECT);
        verify(mHandler, times(1)).sendMessageDelayed(responseCaptor.capture(), eq(2000L));
        assertEqualMessage(responseCaptor.getValue(), -id, PackageManager.VERIFICATION_REJECT);
    }

    @Test
    @EnableFlags(android.content.pm.Flags.FLAG_EXTEND_VERIFICATION_TIMEOUT_MULTIPLE_TIMES)
    public void testExtendVerificationTimeout_delayLimitedToMaxValue() throws Exception {
        // Use a negative ID to bypass permission check during testing.
        final int id = -1;
        PackageVerificationState pvs = new PackageVerificationState(null);
        // Id already pending verification.
        mPms.mPendingVerification.set(-id, pvs);
        // Add callingUid as a required verifier.
        pvs.addRequiredVerifierUid(mCallingUid);
        // We first capture runnables from the post call and execute that to verify
        // mHandler.obtainMessage() call.
        Runnable runnable1 = getRunnableFromHandlerPostCall(id, 58 * 60 * 1000L);
        Runnable runnable2 = getRunnableFromHandlerPostCall(id, 2 * 60 * 1000L);
        Runnable runnable3 = getRunnableFromHandlerPostCall(id, 1000L);
        Random r = new Random();
        ArgumentCaptor<Message> responseCaptor = ArgumentCaptor.forClass(Message.class);

        // Test.
        runnable1.run();
        runnable2.run();
        runnable3.run();

        // When flag is enabled, multiple calls to the API are allowed.
        verify(mHandler, times(3)).removeEqualMessages(eq(PackageManagerService.PACKAGE_VERIFIED),
                eq(new PackageVerificationResponse(r.nextInt(), mCallingUid)));
        verify(mHandler, times(3)).obtainMessage(eq(PackageManagerService.PACKAGE_VERIFIED));
        verify(mHandler, times(1)).sendMessageDelayed(responseCaptor.capture(),
                eq(58 * 60 * 1000L));
        assertEqualMessage(responseCaptor.getValue(), -id, PackageManager.VERIFICATION_REJECT);
        verify(mHandler, times(2)).sendMessageDelayed(responseCaptor.capture(),
                eq(60 * 60 * 1000L));
        assertEqualMessage(responseCaptor.getValue(), -id, PackageManager.VERIFICATION_REJECT);
    }

    @Test
    @DisableFlags(android.content.pm.Flags.FLAG_EXTEND_VERIFICATION_TIMEOUT_MULTIPLE_TIMES)
    public void testExtendVerificationTimeout_onlySingleCallAllowed() throws Exception {
        // Use a negative ID to bypass permission check during testing.
        final int id = -1;
        PackageVerificationState pvs = new PackageVerificationState(null);
        // Id already pending verification.
        mPms.mPendingVerification.set(-id, pvs);
        // Add callingUid as a required verifier.
        pvs.addRequiredVerifierUid(mCallingUid);
        // We first capture runnables from the post call and execute that to verify
        // mHandler.obtainMessage() call.
        Runnable runnable = getRunnableFromHandlerPostCall(id, 1000L);
        ArgumentCaptor<Message> responseCaptor = ArgumentCaptor.forClass(Message.class);

        // Test.
        runnable.run();
        runnable.run();

        // When flag is disabled, we exit early if timeout was already extended once before.
        verify(mHandler, times(1)).obtainMessage(eq(PackageManagerService.PACKAGE_VERIFIED));
        verify(mHandler, times(1)).sendMessageDelayed(responseCaptor.capture(), eq(1000L));
        assertEqualMessage(responseCaptor.getValue(), -id, PackageManager.VERIFICATION_REJECT);
    }

    // We want to verify if handler calls are made in Runnable code in mHandler.post(Runnable) call.
    // For this we first capture the Runnable and then execute it in the test. These self calls are
    // not captured when executing API directly.
    private Runnable getRunnableFromHandlerPostCall(int id, long delayMillis) throws Exception {
        mIPackageManager.extendVerificationTimeout(
                id,
                PackageManager.VERIFICATION_REJECT,
                delayMillis);
        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mHandler, atLeastOnce()).post(runnableCaptor.capture());
        return runnableCaptor.getValue();
    }

    private void assertEqualMessage(Message response, int expectedArg1, int expectedCode) {
        assertEquals(response.arg1, expectedArg1);
        PackageVerificationResponse responseObj = (PackageVerificationResponse) response.obj;
        assertEquals(responseObj.code, expectedCode);
        assertEquals(responseObj.callerUid, mCallingUid);
    }
}
