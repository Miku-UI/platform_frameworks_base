/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.inputmethod;

import static android.view.WindowManager.LayoutParams.TYPE_INPUT_METHOD;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.Display;
import android.view.inputmethod.Flags;
import android.view.inputmethod.InputMethodInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.inputmethod.InputBindResult;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class InputMethodBindingControllerTest extends InputMethodManagerServiceTestBase {

    private static final String PACKAGE_NAME = "com.android.frameworks.inputmethodtests";
    private static final String TEST_SERVICE_NAME =
            "com.android.server.inputmethod.InputMethodBindingControllerTest"
                    + "$EmptyInputMethodService";
    private static final String TEST_IME_ID = PACKAGE_NAME + "/" + TEST_SERVICE_NAME;
    private static final long TIMEOUT_IN_SECONDS = 5L * Build.HW_TIMEOUT_MULTIPLIER;
    private static final long NEGATIVE_TIMEOUT_IN_SECONDS = 2L;

    private InputMethodBindingController mBindingController;
    private Instrumentation mInstrumentation;
    private final int mImeConnectionBindFlags =
            InputMethodBindingController.IME_CONNECTION_BIND_FLAGS
                    & ~Context.BIND_SCHEDULE_LIKE_TOP_APP
                    & ~Context.BIND_ALMOST_PERCEPTIBLE;
    private final long mImeBackgroundBindFlags =
            InputMethodBindingController.IME_BACKGROUND_BIND_FLAGS & ~Context.BIND_ALLOW_FREEZE;

    private final DeviceFlagsValueProvider mFlagsValueProvider = new DeviceFlagsValueProvider();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = new CheckFlagsRule(mFlagsValueProvider);

    public static class EmptyInputMethodService extends InputMethodService {}

    @Before
    public void setUp() throws RemoteException {
        super.setUp();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        // Remove flag Context.BIND_SCHEDULE_LIKE_TOP_APP and Context.BIND_ALLOW_FREEZE because in
        // tests we are not calling from system.
        synchronized (ImfLock.class) {
            mBindingController = new InputMethodBindingController(mUserId,
                    mInputMethodManagerService, mImeConnectionBindFlags, mImeBackgroundBindFlags);
        }
    }

    @After
    public void tearDown() {
        super.tearDown();
        synchronized (ImfLock.class) {
            mBindingController.unbindIme();
        }
    }

    @Test
    public void testBindIme_selectedImeIdNull() {
        synchronized (ImfLock.class) {
            mBindingController.setSelectedImeId(null /* imeId */);
            assertThat(mBindingController.bindIme()).isEqualTo(InputBindResult.NO_IME);
        }
    }

    @Test
    public void testBindIme_selectedImeIdUnknown() {
        synchronized (ImfLock.class) {
            mBindingController.setSelectedImeId("unknown ime id");
        }
        assertThrows(IllegalArgumentException.class, () -> {
            synchronized (ImfLock.class) {
                mBindingController.bindIme();
            }
        });
    }

    @Test
    public void testBindIme_notConnected() {
        synchronized (ImfLock.class) {
            mBindingController.setSelectedImeId(TEST_IME_ID);
            doReturn(false)
                    .when(mContext)
                    .bindServiceAsUser(
                            any(Intent.class),
                            any(ServiceConnection.class),
                            anyInt(),
                            any(UserHandle.class));
            doNothing().when(mContext).unbindService(any(ServiceConnection.class));

            assertThat(mBindingController.bindIme()).isEqualTo(InputBindResult.IME_NOT_CONNECTED);
            verify(mContext, times(1)).unbindService(any(ServiceConnection.class));
        }
    }

    /**
     * Verifies the controller state after binding both the main visible connections, and then
     * unbinding both.
     */
    @Test
    public void testBindImeAndUnbindIme() throws Exception {
        // Bind with main connection
        testBindIme(false /* wasBound */);

        // Bind with visible connection
        testSetImeVisibleOrReconnect();

        // Unbind both main and visible connections
        testUnbindIme(true /* isVisible */);
    }

    /**
     * Verifies the controller state after binding both the main and visible connections,
     * then setting it inactive, and then setting it active again.
     */
    @RequiresFlagsEnabled(Flags.FLAG_WARM_WORK_PROFILE_IME)
    @Test
    public void testBindAndSetInactiveAndSetActive() throws Exception {
        // Bind with main connection
        testBindIme(false /* wasBound */);

        // Bind with visible connection
        testSetImeVisibleOrReconnect();

        verifySetInactiveWhileBound(true /* isVisible */);

        verifySetActiveWhileBound(true /* wasBoundBeforeInactive */);
    }

    /**
     * Verifies the controller state after setting it inactive, then setting it active, and then
     * binding the main connection.
     */
    @RequiresFlagsEnabled(Flags.FLAG_WARM_WORK_PROFILE_IME)
    @Test
    public void testSetInactiveAndSetActiveAndBind() throws Exception {
        verifySetInactiveWhileNotBound();

        verifySetActiveWhileNotBound(false /* wasUnbound */);

        testBindIme(false /* wasBound */);
    }

    /**
     * Verifies the controller state after setting it inactive, then binding the main connection,
     * and then setting it active
     */
    @RequiresFlagsEnabled(Flags.FLAG_WARM_WORK_PROFILE_IME)
    @Test
    public void testSetInactiveAndBindAndSetActive() throws Exception {
        verifySetInactiveWhileNotBound();

        testBindWhileNotActive();

        verifySetActiveWhileNotBound(false /* wasUnbound */);

        testBindIme(false /* wasBound */);
    }

    /**
     * Verifies the controller state after binding the main connection, setting it inactive,
     * unbinding the main connection, then setting it active, and then binding the main connection
     * again.
     */
    @RequiresFlagsEnabled(Flags.FLAG_WARM_WORK_PROFILE_IME)
    @Test
    public void testBindAndSetInactiveAndUnbindAndSetActiveAndBind() throws Exception {
        testBindIme(false /* wasBound */);

        verifySetInactiveWhileBound(false /* isVisible */);

        testUnbindIme(false /* isVisible */);

        verifySetActiveWhileNotBound(true /* wasUnbound */);

        testBindIme(true /* wasBound */);
    }

    /**
     * Verifies the controller state calling bindIme again after bindIme without calling unbindIme.
     * This shouldn't happen in production, but verifies for safety just in case.
     */
    @Test
    public void testBindImeAgainIgnored() throws Exception {
        // Bind with main connection
        testBindIme(false /* wasBound */);

        // Bind with visible connection
        testSetImeVisibleOrReconnect();

        final IBinder curToken;
        synchronized (ImfLock.class) {
            curToken = mBindingController.getCurToken();
        }

        final var latch = new CountDownLatch(1);
        final InputBindResult result = callOnMainSync(() -> {
            synchronized (ImfLock.class) {
                mBindingController.setLatchForTesting(latch);
                return mBindingController.bindIme();
            }
        });

        assertThat(result).isNotNull();
        assertThat(result.result).isEqualTo(InputBindResult.ResultCode.ERROR_IME_NOT_CONNECTED);
        assertThat(result.id).isNull();

        // onServiceConnected() should not happen.
        boolean completed = latch.await(NEGATIVE_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        if (completed) {
            fail("onServiceConnected() should not be called again");
        }

        // Verify the connection and token are kept.
        synchronized (ImfLock.class) {
            if (mFlagsValueProvider.getBoolean(Flags.FLAG_WARM_WORK_PROFILE_IME)) {
                assertThat(mBindingController.hasBackgroundConnection()).isTrue();
            }
            assertThat(mBindingController.getCurImeIntent()).isNotNull();
            assertThat(mBindingController.hasMainConnection()).isTrue();
            assertThat(mBindingController.getCurToken()).isEqualTo(curToken);
        }
    }

    /**
     * Verifies the state after setting the controller inactive while it has the main connection.
     *
     * @param isVisible whether the controller has the visible connection.
     */
    private void verifySetInactiveWhileBound(boolean isVisible) {
        mInstrumentation.runOnMainSync(() -> {
            synchronized (ImfLock.class) {
                mBindingController.setInactive();
            }
        });

        synchronized (ImfLock.class) {
            // Set inactive but keep inactive binding.
            assertThat(mBindingController.hasBackgroundConnection()).isTrue();
            assertThat(mBindingController.hasMainConnection()).isFalse();
            assertThat(mBindingController.hasVisibleConnection()).isFalse();
            assertThat(mBindingController.isActive()).isFalse();
            assertThat(mBindingController.getCurToken()).isNotNull();
            final int curDisplayId = mBindingController.getCurDisplayId();
            assertThat(curDisplayId).isEqualTo(Display.DEFAULT_DISPLAY);
            // Unbind visible connection and main connection.
            if (isVisible) {
                verify(mContext, times(2)).unbindService(any(ServiceConnection.class));
            } else {
                verify(mContext, times(1)).unbindService(any(ServiceConnection.class));
            }
            verify(mMockWindowManagerInternal, times(1)).setImeWindowToken(isNull() /* token */,
                    eq(curDisplayId));
            assertThat(mBindingController.getCurImeId()).isNotNull();
            assertThat(mBindingController.getCurIme()).isNotNull();
            assertThat(mBindingController.getCurImeUid()).isNotEqualTo(Process.INVALID_UID);
            verify(mInputMethodManagerService).onImeDisconnected(eq(mUserId));
        }
    }

    /**
     * Verifies the state after setting the controller inactive while it does not have the main
     * connection.
     */
    private void verifySetInactiveWhileNotBound() {
        mInstrumentation.runOnMainSync(() -> {
            synchronized (ImfLock.class) {
                mBindingController.setInactive();
            }
        });

        synchronized (ImfLock.class) {
            assertThat(mBindingController.hasBackgroundConnection()).isFalse();
            assertThat(mBindingController.hasMainConnection()).isFalse();
            assertThat(mBindingController.hasVisibleConnection()).isFalse();
            assertThat(mBindingController.isActive()).isFalse();
            verify(mContext, never()).unbindService(any(ServiceConnection.class));
            verify(mMockWindowManagerInternal, never()).setImeWindowToken(
                    any(IBinder.class) /* token */, anyInt() /* displayId */);
            assertThat(mBindingController.getCurToken()).isNull();
            assertThat(mBindingController.getCurImeId()).isNull();
            assertThat(mBindingController.getCurIme()).isNull();
            assertThat(mBindingController.getCurImeUid()).isEqualTo(Process.INVALID_UID);
            verify(mInputMethodManagerService, never()).onImeDisconnected(eq(mUserId));
        }
    }

    /**
     * Verifies the state after setting the controller active while it has the main connection.
     *
     * @param wasBoundBeforeInactive whether the controller previously had the main connection
     *                               before it was made inactive.
     */
    private void verifySetActiveWhileBound(boolean wasBoundBeforeInactive) {
        final InputMethodInfo info;
        synchronized (ImfLock.class) {
            info = InputMethodSettingsRepository.get(mUserId).getMethodMap().get(TEST_IME_ID);
        }
        assertThat(info).isNotNull();
        mInstrumentation.runOnMainSync(() -> {
            synchronized (ImfLock.class) {
                mBindingController.setActive();
            }
        });

        synchronized (ImfLock.class) {
            assertThat(mBindingController.hasBackgroundConnection()).isTrue();
            assertThat(mBindingController.hasMainConnection()).isTrue();
            assertThat(mBindingController.hasVisibleConnection()).isFalse();
            assertThat(mBindingController.isActive()).isTrue();
            final var curToken = mBindingController.getCurToken();
            assertThat(curToken).isNotNull();
            if (wasBoundBeforeInactive) {
                // No further unbinds (just two from previous setInactive).
                verify(mContext, times(2)).unbindService(any(ServiceConnection.class));
                // Binds main connection again.
                verify(mContext, times(2)).bindServiceAsUser(
                        any(Intent.class) /* service */, any(ServiceConnection.class) /* conn */,
                        eq(mImeConnectionBindFlags) /* flags */, any(UserHandle.class) /* user */);
                // ImeToken is first set when bound, and set again when made active.
                verify(mMockWindowManagerInternal, times(2)).setImeWindowToken(
                        eq(curToken), eq(mBindingController.getCurDisplayId()));
            } else {
                verify(mContext, never()).unbindService(any(ServiceConnection.class));
                // No further binds from setActive (just one from previous binding).
                verify(mContext, times(1)).bindServiceAsUser(
                        any(Intent.class) /* service */, any(ServiceConnection.class) /* conn */,
                        eq(mImeConnectionBindFlags) /* flags */, any(UserHandle.class) /* user */);
                verify(mMockWindowManagerInternal, times(1)).setImeWindowToken(
                        eq(curToken), eq(mBindingController.getCurDisplayId()));
            }
            final var curIme = mBindingController.getCurIme();
            assertThat(mBindingController.getCurImeId()).isNotNull();
            assertThat(curIme).isNotNull();
            final int curImeUid = mBindingController.getCurImeUid();
            assertThat(curImeUid).isNotEqualTo(Process.INVALID_UID);
            verify(mInputMethodManagerService, times(2)).onImeConnected(eq(info.getId()),
                    eq(curImeUid), eq(mUserId));
            verify(mInputMethodManagerService, times(1)).initializeImeLocked(eq(curIme),
                    eq(curToken), eq(mUserId));
        }
    }

    /**
     * Verifies the state after setting the controller active while it does not have the main
     * connection.
     *
     * @param wasUnbound whether the controller previously had the main connection, and then had it
     *                   unbound.
     */
    private void verifySetActiveWhileNotBound(boolean wasUnbound) {
        mInstrumentation.runOnMainSync(() -> {
            synchronized (ImfLock.class) {
                mBindingController.setActive();
            }
        });

        synchronized (ImfLock.class) {
            assertThat(mBindingController.hasMainConnection()).isFalse();
            assertThat(mBindingController.hasVisibleConnection()).isFalse();
            assertThat(mBindingController.isActive()).isTrue();
            if (wasUnbound) {
                // Unbound main connection and background connection.
                verify(mContext, times(2)).unbindService(any(ServiceConnection.class));
            } else {
                verify(mContext, never()).unbindService(any(ServiceConnection.class));
                verify(mContext, never()).bindServiceAsUser(
                        any(Intent.class) /* service */, any(ServiceConnection.class) /* conn */,
                        anyInt() /* flags */, any(UserHandle.class) /* user */);
                verify(mMockWindowManagerInternal, never()).setImeWindowToken(
                        any(IBinder.class) /* token */, anyInt() /* displayId */);
            }
            assertThat(mBindingController.getCurToken()).isNull();
            assertThat(mBindingController.getCurImeId()).isNull();
            assertThat(mBindingController.getCurIme()).isNull();
            assertThat(mBindingController.getCurImeUid()).isEqualTo(Process.INVALID_UID);
            final int numConnect = wasUnbound ? 1 : 0;
            verify(mInputMethodManagerService, times(numConnect)).onImeConnected(
                    anyString() /* imeId */, anyInt() /* imeUid */, anyInt() /* userId */);
            verify(mInputMethodManagerService, times(numConnect)).initializeImeLocked(
                    any(IInputMethodInvoker.class) /* ime */, any(IBinder.class) /* token */,
                    anyInt() /* userId */);
        }
    }

    private void testBindIme(boolean wasBound) throws Exception {
        final InputMethodInfo info;
        synchronized (ImfLock.class) {
            mBindingController.setSelectedDisplayId(Display.DEFAULT_DISPLAY);
            mBindingController.setSelectedImeId(TEST_IME_ID);
            info = InputMethodSettingsRepository.get(mUserId).getMethodMap().get(TEST_IME_ID);
        }
        assertThat(info).isNotNull();
        assertThat(info.getId()).isEqualTo(TEST_IME_ID);
        assertThat(info.getServiceName()).isEqualTo(TEST_SERVICE_NAME);

        // Bind input method with main connection. It is called on another thread because we should
        // wait for onServiceConnected() to finish.
        final var latch = new CountDownLatch(1);
        InputBindResult result = callOnMainSync(() -> {
            synchronized (ImfLock.class) {
                mBindingController.setLatchForTesting(latch);
                return mBindingController.bindIme();
            }
        });

        final int numBinds = wasBound ? 2 : 1;
        verify(mContext, times(numBinds)).bindServiceAsUser(any(Intent.class) /* service */,
                any(ServiceConnection.class) /* conn */, eq(mImeConnectionBindFlags) /* flags */,
                any(UserHandle.class) /* user */);
        assertThat(result).isNotNull();
        assertThat(result.result).isEqualTo(InputBindResult.ResultCode.SUCCESS_WAITING_IME_BINDING);
        assertThat(result.id).isEqualTo(info.getId());
        final IBinder curToken;
        synchronized (ImfLock.class) {
            if (mFlagsValueProvider.getBoolean(Flags.FLAG_WARM_WORK_PROFILE_IME)) {
                assertThat(mBindingController.hasBackgroundConnection()).isTrue();
            }
            assertThat(mBindingController.getCurImeIntent()).isNotNull();
            assertThat(mBindingController.hasMainConnection()).isTrue();
            assertThat(mBindingController.getCurImeId()).isEqualTo(info.getId());
            curToken = mBindingController.getCurToken();
            final int curDisplayId = mBindingController.getCurDisplayId();
            assertThat(curToken).isNotNull();
            assertThat(curDisplayId).isEqualTo(Display.DEFAULT_DISPLAY);
            if (mFlagsValueProvider.getBoolean(Flags.FLAG_WARM_WORK_PROFILE_IME)) {
                verify(mMockWindowManagerInternal, times(1)).addImeWindowToken(eq(curToken),
                        eq(curDisplayId), eq(mBindingController.getUserId()),
                        isNull() /* options */);
                verify(mMockWindowManagerInternal, times(1)).setImeWindowToken(eq(curToken),
                        eq(curDisplayId));
            } else {
                verify(mMockWindowManagerInternal, times(1)).addWindowToken(eq(curToken),
                        eq(TYPE_INPUT_METHOD), eq(curDisplayId), isNull() /* options */);
            }
        }
        // Wait for onServiceConnected()
        boolean completed = latch.await(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        if (!completed) {
            fail("Timed out waiting for onServiceConnected()");
        }

        // Verify onServiceConnected() is called and bound successfully.
        synchronized (ImfLock.class) {
            final var curIme = mBindingController.getCurIme();
            assertThat(curIme).isNotNull();
            final int curImeUid = mBindingController.getCurImeUid();
            assertThat(curImeUid).isNotEqualTo(Process.INVALID_UID);
            verify(mInputMethodManagerService, times(numBinds)).onImeConnected(eq(info.getId()),
                    eq(curImeUid), eq(mUserId));
            verify(mInputMethodManagerService, times(1)).initializeImeLocked(eq(curIme),
                    eq(curToken), eq(mUserId));
        }
    }

    private void testBindWhileNotActive() throws Exception {
        final InputMethodInfo info;
        synchronized (ImfLock.class) {
            mBindingController.setSelectedDisplayId(Display.DEFAULT_DISPLAY);
            mBindingController.setSelectedImeId(TEST_IME_ID);
            info = InputMethodSettingsRepository.get(mUserId).getMethodMap().get(TEST_IME_ID);
        }
        assertThat(info).isNotNull();
        assertThat(info.getId()).isEqualTo(TEST_IME_ID);
        assertThat(info.getServiceName()).isEqualTo(TEST_SERVICE_NAME);

        // Bind input method with main connection. It is called on another thread because we should
        // wait for onServiceConnected() to finish.
        final var latch = new CountDownLatch(1);
        InputBindResult result = callOnMainSync(() -> {
            synchronized (ImfLock.class) {
                mBindingController.setLatchForTesting(latch);
                return mBindingController.bindIme();
            }
        });

        verify(mContext, never()).bindServiceAsUser(
                any(Intent.class) /* service */, any(ServiceConnection.class) /* conn */,
                eq(mImeConnectionBindFlags) /* flags */, any(UserHandle.class) /* user */);
        assertThat(result.result).isEqualTo(InputBindResult.ResultCode.ERROR_NO_IME);
        assertThat(result.id).isNull();
        synchronized (ImfLock.class) {
            assertThat(mBindingController.hasBackgroundConnection()).isFalse();
            assertThat(mBindingController.hasMainConnection()).isFalse();
            assertThat(mBindingController.getCurImeId()).isNull();
            assertThat(mBindingController.getCurToken()).isNull();
            assertThat(mBindingController.getCurDisplayId()).isEqualTo(Display.INVALID_DISPLAY);
            verify(mMockWindowManagerInternal, never()).addWindowToken(
                    any(IBinder.class) /* token */, anyInt() /* type */, anyInt() /* displayId */,
                    any(Bundle.class) /* options */);
            verify(mMockWindowManagerInternal, never()).setImeWindowToken(
                    any(IBinder.class) /* token */, anyInt() /* displayId */);
        }
        // Wait for onServiceConnected()
        boolean completed = latch.await(NEGATIVE_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
        if (completed) {
            fail("onServiceConnected() should not be received for inactive bindings");
        }

        synchronized (ImfLock.class) {
            assertThat(mBindingController.getCurIme()).isNull();
            final int curImeUid = mBindingController.getCurImeUid();
            assertThat(curImeUid).isEqualTo(Process.INVALID_UID);
            verify(mInputMethodManagerService, never()).onImeConnected(anyString() /* imeId */,
                    anyInt() /* imeUid */, anyInt() /* userId */);
            verify(mInputMethodManagerService, never()).initializeImeLocked(
                    any(IInputMethodInvoker.class) /* ime */, any(IBinder.class) /* token */,
                    anyInt() /* userId */);
        }
    }

    private void testSetImeVisibleOrReconnect() {
        mInstrumentation.runOnMainSync(() -> {
            synchronized (ImfLock.class) {
                mBindingController.setImeVisibleOrReconnect();
            }
        });
        // Bind input method with visible connection
        verify(mContext, times(1)).bindServiceAsUser(
                any(Intent.class) /* service */, any(ServiceConnection.class) /* conn */,
                eq(InputMethodBindingController.IME_VISIBLE_BIND_FLAGS) /* flags */,
                any(UserHandle.class) /* user */);
        synchronized (ImfLock.class) {
            assertThat(mBindingController.hasVisibleConnection()).isTrue();
        }
    }

    private void testUnbindIme(boolean isVisible) {
        final IBinder curToken;
        final int curDisplayId;
        synchronized (ImfLock.class) {
            curToken = mBindingController.getCurToken();
            curDisplayId = mBindingController.getCurDisplayId();
        }
        mInstrumentation.runOnMainSync(() -> {
            synchronized (ImfLock.class) {
                mBindingController.unbindIme();
            }
        });

        synchronized (ImfLock.class) {
            // Unbind both main connection and visible connection
            if (mFlagsValueProvider.getBoolean(Flags.FLAG_WARM_WORK_PROFILE_IME)) {
                assertThat(mBindingController.hasBackgroundConnection()).isFalse();
            }
            assertThat(mBindingController.hasMainConnection()).isFalse();
            assertThat(mBindingController.hasVisibleConnection()).isFalse();
            final int backgroundConnection =
                    mFlagsValueProvider.getBoolean(Flags.FLAG_WARM_WORK_PROFILE_IME) ? 1 : 0;
            if (isVisible) {
                verify(mContext, times(2 + backgroundConnection))
                        .unbindService(any(ServiceConnection.class));
            } else {
                verify(mContext, times(1 + backgroundConnection))
                        .unbindService(any(ServiceConnection.class));
            }
            verify(mMockWindowManagerInternal, times(1)).removeWindowToken(eq(curToken),
                    eq(true) /* removeWindows */, eq(false)/* animateExit */, eq(curDisplayId));
            assertThat(mBindingController.getCurImeIntent()).isNull();
            assertThat(mBindingController.getCurImeId()).isNull();
            assertThat(mBindingController.getCurToken()).isNull();
            assertThat(mBindingController.getCurDisplayId()).isEqualTo(Display.INVALID_DISPLAY);
            assertThat(mBindingController.getCurIme()).isNull();
            assertThat(mBindingController.getCurImeUid()).isEqualTo(Process.INVALID_UID);
            verify(mInputMethodManagerService).onImeDisconnected(eq(mUserId));
        }
    }

    @Test
    public void testStaleConnectionDoesNotTriggerInitialize() {
        final List<ServiceConnection> capturedConnections = new ArrayList<>();
        // Capture all ServiceConnections passed to bindServiceAsUser for mImeConnectionBindFlags
        doAnswer(invocation -> {
            capturedConnections.add(invocation.getArgument(1));
            return true;
        }).when(mContext).bindServiceAsUser(any(Intent.class), any(ServiceConnection.class),
                eq(mImeConnectionBindFlags), any(UserHandle.class));
        // Also mock unbind if we captured the invocation above.
        doAnswer(invocation -> {
            if (capturedConnections.contains(invocation.getArgument(0))) {
                return true;
            }
            return invocation.callRealMethod();
        }).when(mContext).unbindService(any(ServiceConnection.class));

        // First bind attempt
        synchronized (ImfLock.class) {
            mBindingController.setSelectedDisplayId(Display.DEFAULT_DISPLAY);
            mBindingController.setSelectedImeId(TEST_IME_ID);

            mBindingController.bindIme();
        }
        assertThat(capturedConnections).hasSize(1);
        ServiceConnection conn1 = capturedConnections.get(0);

        // Unbind, and second bind attempt
        synchronized (ImfLock.class) {
            mBindingController.unbindIme();
            mBindingController.bindIme();
        }

        // bindIme() should have created a new main connection.
        assertThat(capturedConnections).hasSize(2);
        ServiceConnection conn2 = capturedConnections.get(1);
        assertThat(conn1).isNotSameInstanceAs(conn2);

        // Simulate a stale onServiceConnected from the first attempt arriving now.
        ComponentName componentName = new ComponentName(PACKAGE_NAME, TEST_SERVICE_NAME);
        conn1.onServiceConnected(componentName, mMockInputMethodBinder);

        // Verify that initializeImeLocked was NOT called.
        synchronized (ImfLock.class) {
            verify(mInputMethodManagerService, never()).initializeImeLocked(any(), any(), anyInt());
        }

        // Simulate a onServiceConnected from the second attempt arriving now.
        conn2.onServiceConnected(componentName, mMockInputMethodBinder);

        synchronized (ImfLock.class) {
            verify(mInputMethodManagerService, times(1))
                    .initializeImeLocked(any(), any(), anyInt());
        }
    }

    private static <V> V callOnMainSync(Callable<V> callable) {
        AtomicReference<V> result = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation()
                .runOnMainSync(
                        () -> {
                            try {
                                result.set(callable.call());
                            } catch (Exception e) {
                                throw new RuntimeException("Exception was thrown", e);
                            }
                        });
        return result.get();
    }
}
