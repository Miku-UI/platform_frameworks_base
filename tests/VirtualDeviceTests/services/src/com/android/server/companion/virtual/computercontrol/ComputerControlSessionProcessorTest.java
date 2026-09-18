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

import static android.companion.virtual.computercontrol.ComputerControlSessionParams.MIN_COMPUTER_CONTROL_VERSION_FOR_ANDROID_17;

import static com.android.server.companion.virtual.computercontrol.ComputerControlSessionProcessor.MAXIMUM_CONCURRENT_SESSIONS;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.AppInteractionAttribution;
import android.app.AppOpsManager;
import android.app.IApplicationThread;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyManagerInternal;
import android.companion.DeviceId;
import android.companion.virtual.CompanionDeviceId;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.audio.AudioCapture;
import android.companion.virtual.audio.AudioInjection;
import android.companion.virtual.audio.VirtualAudioDevice;
import android.companion.virtual.computercontrol.ComputerControlSession;
import android.companion.virtual.computercontrol.ComputerControlSessionParams;
import android.companion.virtual.computercontrol.IComputerControlLifecycleCallback;
import android.companion.virtual.computercontrol.IComputerControlSession;
import android.companion.virtual.computercontrol.IComputerControlSessionCallback;
import android.content.AttributionSource;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.graphics.Color;
import android.hardware.display.VirtualDisplay;
import android.os.Binder;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.security.authenticationpolicy.AuthenticationPolicyManager;
import android.security.authenticationpolicy.IAuthenticationPolicyService;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.LocalServices;
import com.android.server.companion.virtual.VirtualDeviceManagerInternal;
import com.android.server.input.InputManagerInternal;
import com.android.server.notification.NotificationManagerInternal;
import com.android.server.pm.UserManagerInternal;
import com.android.server.wm.ActivityTaskManagerInternal;
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
    private static final int SESSION_CLOSE_TIMEOUT_MS = 100;
    private static final String TARGET_PACKAGE = "com.android.foo";
    private static final String ANOTHER_TARGET_PACKAGE = "com.android.bar";
    private static final int CALLING_USER_ID = UserHandle.USER_SYSTEM;
    private static final int NON_DEFAULT_DEVICE_ID = 7;
    private static final int VIRTUAL_DISPLAY_ID = 123;
    private static final int DEVICE_ID = 42;
    private static final String OWNER_PACKAGE_NAME = "com.package";
    private static final AttributionSource ATTRIBUTION_SOURCE = new AttributionSource(
            UserHandle.getUid(CALLING_USER_ID, 0), OWNER_PACKAGE_NAME, "tag");
    private static final AppInteractionAttribution APP_INTERACTION_ATTRIBUTION =
            new AppInteractionAttribution.Builder(
                    AppInteractionAttribution.INTERACTION_TYPE_USER_QUERY).build();
    private static final DeviceId COMP_DEVICE_ID = new DeviceId.Builder()
            .setCustomId("test")
            .build();
    private static final CompanionDeviceId COMPANION_DEVICE_ID =
            new CompanionDeviceId(COMP_DEVICE_ID);
    private static final String NOTIFICATION_CHANNEL_ID = "TEST_CHANNEL_ID";
    private static final int NOTIFICATION_ID = 5;
    private static final String NOTIFICATION_TAG = "TEST_NOTIFICATION_TAG";
    private static final Notification NOTIFICATION =
            new Notification.Builder(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(),
                    NOTIFICATION_CHANNEL_ID)
                    .setOngoing(true)
                    .setRequestPromotedOngoing(true)
                    .setContentTitle("Hello")
                    .setSmallIcon(android.R.drawable.sym_def_app_icon)
                    .setColor(Color.WHITE)
                    .build();
    private static final ComputerControlSessionParams.NotificationParams NOTIFICATION_PARAMS =
            new ComputerControlSessionParams.NotificationParams.Builder(
                    NOTIFICATION, NOTIFICATION_ID)
                    .setNotificationTag(NOTIFICATION_TAG)
                    .build();
    private static final ComputerControlSessionParams PARAMS =
            new ComputerControlSessionParams.Builder()
                    .setName(ComputerControlSessionImplTest.class.getSimpleName())
                    .setTargetPackageNames(List.of(TARGET_PACKAGE))
                    .setTargetComputerControlVersion(MIN_COMPUTER_CONTROL_VERSION_FOR_ANDROID_17)
                    .setAppInteractionAttribution(APP_INTERACTION_ATTRIBUTION)
                    .setNotificationParams(NOTIFICATION_PARAMS)
                    .build();
    private static final ComputerControlSessionParams CROSS_DEVICE_PARAMS =
            new ComputerControlSessionParams.Builder()
                    .setName(ComputerControlSessionImplTest.class.getSimpleName())
                    .setTargetPackageNames(List.of(TARGET_PACKAGE))
                    .setTargetComputerControlVersion(MIN_COMPUTER_CONTROL_VERSION_FOR_ANDROID_17)
                    .setAppInteractionAttribution(APP_INTERACTION_ATTRIBUTION)
                    .setCompanionDeviceId(COMPANION_DEVICE_ID)
                    .setNotificationParams(NOTIFICATION_PARAMS)
                    .build();

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    private IApplicationThread mAppThread;
    @Mock
    private KeyguardManager mKeyguardManager;
    @Mock
    private IAuthenticationPolicyService mAuthenticationPolicyService;
    @Mock
    private NotificationManager mNotificationManager;
    @Mock
    private AppOpsManager mAppOpsManager;
    @Mock
    private ActivityTaskManagerInternal mActivityTaskManagerInternal;
    @Mock
    private WindowManager mWindowManager;
    @Mock
    private WindowManagerInternal mWindowManagerInternal;
    @Mock
    private UserManagerInternal mUserManagerInternal;
    @Mock
    private UserManager mUserManager;
    @Mock
    private DevicePolicyManagerInternal mDevicePolicyManagerInternal;
    @Mock
    private DevicePolicyManager mDevicePolicyManager;
    @Mock
    private NotificationManagerInternal mNotificationManagerInternal;
    @Mock
    private VirtualDeviceManagerInternal mVirtualDeviceManagerInternal;
    @Mock
    private InputManagerInternal mInputManagerInternal;
    @Mock
    private ComputerControlSessionProcessor.VirtualDeviceFactory mVirtualDeviceFactory;
    @Mock
    private ComputerControlSessionProcessor.PendingIntentFactory mPendingIntentFactory;
    @Mock
    private VirtualDevice mVirtualDevice;
    @Mock
    private VirtualDisplay mVirtualDisplay;
    @Mock
    private Display mDisplay;
    @Mock
    private VirtualAudioDevice mVirtualAudioDevice;
    @Mock
    private AudioInjection mAudioInjection;
    @Mock
    private AudioCapture mAudioCapture;
    @Mock
    private IComputerControlSessionCallback mComputerControlSessionCallback;
    @Mock
    private ComputerControlAllowlistController mAllowlistController;
    @Mock
    private IComputerControlLifecycleCallback mLifecycleCallback;
    @Mock
    private Surface mSurface;
    @Captor
    private ArgumentCaptor<Intent> mIntentArgumentCaptor;
    @Captor
    private ArgumentCaptor<IComputerControlSession> mSessionArgumentCaptor;

    private ComputerControlSessionProcessor mProcessor;
    private AutoCloseable mMockitoSession;

    @Before
    public void setUp() throws RemoteException {
        mMockitoSession = MockitoAnnotations.openMocks(this);

        LocalServices.removeServiceForTest(WindowManagerInternal.class);
        LocalServices.addService(WindowManagerInternal.class, mWindowManagerInternal);

        // Needed only for getMainDisplayAssignedToUser in ComputerControlSessionImpl.
        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.addService(UserManagerInternal.class, mUserManagerInternal);

        LocalServices.removeServiceForTest(DevicePolicyManagerInternal.class);
        LocalServices.addService(DevicePolicyManagerInternal.class, mDevicePolicyManagerInternal);

        LocalServices.removeServiceForTest(InputManagerInternal.class);
        LocalServices.addService(InputManagerInternal.class, mInputManagerInternal);

        LocalServices.removeServiceForTest(ActivityTaskManagerInternal.class);
        LocalServices.addService(ActivityTaskManagerInternal.class, mActivityTaskManagerInternal);

        LocalServices.removeServiceForTest(NotificationManagerInternal.class);
        LocalServices.addService(NotificationManagerInternal.class, mNotificationManagerInternal);

        Context context = spy(new ContextWrapper(
                InstrumentationRegistry.getInstrumentation().getTargetContext()));
        doReturn(context).when(context).createContextAsUser(any(UserHandle.class), anyInt());

        final Context displayContext = spy(new ContextWrapper(
                InstrumentationRegistry.getInstrumentation().getTargetContext()));
        doReturn(displayContext).when(context).createDisplayContext(any());
        doReturn(mWindowManager).when(displayContext).getSystemService(WindowManager.class);

        when(context.getSystemService(Context.KEYGUARD_SERVICE)).thenReturn(mKeyguardManager);
        when(context.getSystemService(Context.AUTHENTICATION_POLICY_SERVICE))
                .thenReturn(new AuthenticationPolicyManager(context, mAuthenticationPolicyService));
        when(context.getSystemService(Context.APP_OPS_SERVICE)).thenReturn(mAppOpsManager);
        when(context.getSystemService(Context.NOTIFICATION_SERVICE))
                .thenReturn(mNotificationManager);
        when(context.getSystemService(DevicePolicyManager.class)).thenReturn(mDevicePolicyManager);
        when(context.getSystemService(UserManager.class)).thenReturn(mUserManager);

        when(mUserManager.getUserInfo(anyInt())).thenReturn(
                new UserInfo(CALLING_USER_ID, "name", 0));
        when(mUserManager.isManagedProfile(anyInt())).thenReturn(false);

        when(mDevicePolicyManager.isOrganizationOwnedDeviceWithManagedProfile()).thenReturn(false);
        when(mDevicePolicyManagerInternal.getDeviceOwnerComponent(anyBoolean())).thenReturn(null);
        when(mDevicePolicyManagerInternal.isUserOrganizationManaged(CALLING_USER_ID))
                .thenReturn(false);

        when(mAppOpsManager.noteOpNoThrow(eq(AppOpsManager.OP_COMPUTER_CONTROL), any(), any()))
                .thenReturn(AppOpsManager.MODE_ALLOWED);
        when(mActivityTaskManagerInternal.isUidForeground(anyInt())).thenReturn(true);

        when(mVirtualDeviceFactory.createVirtualDevice(any(), any(), any()))
                .thenReturn(mVirtualDevice);

        when(mVirtualDevice.createVirtualDisplay(any(), any(), any())).thenReturn(mVirtualDisplay);
        when(mVirtualDisplay.getDisplay()).thenReturn(mDisplay);
        when(mDisplay.getDisplayId()).thenReturn(VIRTUAL_DISPLAY_ID);
        when(mVirtualDevice.getDeviceId()).thenReturn(DEVICE_ID);

        when(mVirtualDevice.createVirtualAudioDevice(any(), any(), any())).thenReturn(
                mVirtualAudioDevice);
        when(mVirtualAudioDevice.startAudioCapture(any())).thenReturn(mAudioCapture);
        when(mVirtualAudioDevice.startAudioInjection(any())).thenReturn(mAudioInjection);

        when(mComputerControlSessionCallback.asBinder()).thenReturn(new Binder());

        when(mAllowlistController.isPackageAllowedToCreateSession(anyString(), any(), any(),
                anyInt())).thenReturn(true);
        when(mAllowlistController.isPackageAutomatable(
                eq(TARGET_PACKAGE), eq(OWNER_PACKAGE_NAME), any())).thenReturn(true);
        when(mAllowlistController.doesAgentHaveConsentToAutomateTargetApp(anyInt(), anyString(),
                anyString())).thenReturn(true);

        when(mAuthenticationPolicyService.isAgentAuthorized(any(), anyInt(), any()))
                .thenReturn(true);

        mProcessor = new ComputerControlSessionProcessor(
                context, mVirtualDeviceManagerInternal, mVirtualDeviceFactory,
                mPendingIntentFactory, mAllowlistController);
    }

    @After
    public void tearDown() throws Exception {
        mMockitoSession.close();
    }

    @Test
    public void initialize_initializesAllowlistController() throws Exception {
        mProcessor.initialize();

        verify(mAllowlistController).initialize();
    }

    @Test
    @EnableFlags(android.companion.Flags.FLAG_SUPPORT_AI_AGENT)
    public void defaultDevice_keyguardLocked_sessionNotCreated() throws Exception {
        when(mKeyguardManager.isDeviceLocked(CALLING_USER_ID, Context.DEVICE_ID_DEFAULT))
                .thenReturn(true);
        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback);

        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreationFailed(ComputerControlSession.ERROR_DEVICE_LOCKED);
    }

    @Test
    @DisableFlags(android.companion.Flags.FLAG_SUPPORT_AI_AGENT)
    public void defaultDevice_keyguardLocked_sessionNotCreated_flagDisabled() throws Exception {
        when(mKeyguardManager.isDeviceLocked(CALLING_USER_ID, Context.DEVICE_ID_DEFAULT))
                .thenReturn(true);

        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback);

        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreationFailed(ComputerControlSession.ERROR_DEVICE_LOCKED);
    }

    @Test
    @EnableFlags(android.companion.virtualdevice.flags.Flags.FLAG_COMPUTER_CONTROL_CROSS_DEVICE)
    @EnableFlags(android.companion.Flags.FLAG_SUPPORT_AI_AGENT)
    public void nonDefaultDevice_uidNotSeenOnDevice_fallbackToDefaultDevice() throws Exception {
        when(mVirtualDeviceManagerInternal.isDeviceIdAssociationValid(
                ATTRIBUTION_SOURCE.getUid(), NON_DEFAULT_DEVICE_ID))
                .thenReturn(false);

        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE.withDeviceId(NON_DEFAULT_DEVICE_ID), PARAMS,
                mComputerControlSessionCallback);

        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreationFailed(ComputerControlSession.ERROR_PERMISSION_DENIED);
    }

    @Test
    @DisableFlags(android.companion.Flags.FLAG_SUPPORT_AI_AGENT)
    public void nonDefaultDevice_uidNotSeenOnDevice_fallbackToDefaultDevice_flagDisabled() throws Exception {
        when(mVirtualDeviceManagerInternal.isDeviceIdAssociationValid(
                ATTRIBUTION_SOURCE.getUid(), NON_DEFAULT_DEVICE_ID))
                .thenReturn(false);
        when(mKeyguardManager.isDeviceLocked(CALLING_USER_ID, Context.DEVICE_ID_DEFAULT))
                .thenReturn(true);

        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE.withDeviceId(NON_DEFAULT_DEVICE_ID), PARAMS,
                mComputerControlSessionCallback);

        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreationFailed(ComputerControlSession.ERROR_DEVICE_LOCKED);
    }

    @Test
    @EnableFlags(android.companion.virtualdevice.flags.Flags.FLAG_COMPUTER_CONTROL_CROSS_DEVICE)
    @EnableFlags(android.companion.Flags.FLAG_SUPPORT_AI_AGENT)
    public void nonDefaultDevice_uidSeenOnDevice_sessionCreated() throws Exception {
        when(mVirtualDeviceManagerInternal.isDeviceIdAssociationValid(
                ATTRIBUTION_SOURCE.getUid(), NON_DEFAULT_DEVICE_ID))
                .thenReturn(true);
        when(mAuthenticationPolicyService.isAgentAuthorized(
                any(), eq(NON_DEFAULT_DEVICE_ID), eq(COMP_DEVICE_ID))).thenReturn(true);

        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE.withDeviceId(NON_DEFAULT_DEVICE_ID), PARAMS,
                mComputerControlSessionCallback);

        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS).times(1))
                .onSessionCreated(anyInt(), any());
    }

    @Test
    @EnableFlags(android.companion.virtualdevice.flags.Flags.FLAG_COMPUTER_CONTROL_CROSS_DEVICE)
    @EnableFlags(android.companion.Flags.FLAG_SUPPORT_AI_AGENT)
    public void sessionCreation_passesCompanionDeviceIdToPolicy() throws Exception {
        when(mVirtualDeviceManagerInternal.isDeviceIdAssociationValid(
                ATTRIBUTION_SOURCE.getUid(), NON_DEFAULT_DEVICE_ID))
                .thenReturn(true);

        mProcessor.processNewSessionRequest(
                mAppThread,
                ATTRIBUTION_SOURCE.withDeviceId(NON_DEFAULT_DEVICE_ID),
                CROSS_DEVICE_PARAMS,
                mComputerControlSessionCallback);

        verify(mAuthenticationPolicyService, timeout(CALLBACK_TIMEOUT_MS)).isAgentAuthorized(
                any(), eq(NON_DEFAULT_DEVICE_ID), eq(COMP_DEVICE_ID));
    }

    @Test
    @DisableFlags(android.companion.Flags.FLAG_SUPPORT_AI_AGENT)
    public void nonDefaultDevice_uidSeenOnDevice_sessionCreated_flagDisabled() throws Exception {
        when(mVirtualDeviceManagerInternal.isDeviceIdAssociationValid(
                ATTRIBUTION_SOURCE.getUid(), NON_DEFAULT_DEVICE_ID))
                .thenReturn(true);
        when(mKeyguardManager.isDeviceLocked(CALLING_USER_ID, NON_DEFAULT_DEVICE_ID))
                .thenReturn(false);

        mProcessor.processNewSessionRequest(
                mAppThread,
                ATTRIBUTION_SOURCE.withDeviceId(NON_DEFAULT_DEVICE_ID),
                PARAMS,
                mComputerControlSessionCallback);

        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS).times(1))
                .onSessionCreated(anyInt(), any());
    }

    @Test
    public void nonDefaultDevice_uidOnDevice_keyguardLocked_sessionNotCreated() throws Exception {
        when(mVirtualDeviceManagerInternal.isDeviceIdAssociationValid(
                        ATTRIBUTION_SOURCE.getUid(), NON_DEFAULT_DEVICE_ID))
                .thenReturn(true);
        when(mKeyguardManager.isDeviceLocked(CALLING_USER_ID, NON_DEFAULT_DEVICE_ID))
                .thenReturn(true);

        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE.withDeviceId(NON_DEFAULT_DEVICE_ID), PARAMS,
                mComputerControlSessionCallback);

        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreationFailed(ComputerControlSession.ERROR_DEVICE_LOCKED);
    }

    @Test
    @EnableFlags(android.companion.virtualdevice.flags.Flags.FLAG_COMPUTER_CONTROL_CROSS_DEVICE)
    @EnableFlags(android.companion.Flags.FLAG_SUPPORT_AI_AGENT)
    public void crossDeviceRequest_authorized_sessionCreated() throws Exception {
        when(mAuthenticationPolicyService.isAgentAuthorized(
                        any(UserHandle.class), eq(Context.DEVICE_ID_DEFAULT), eq(COMP_DEVICE_ID)))
                .thenReturn(true);

        mProcessor.processNewSessionRequest(
                mAppThread,
                ATTRIBUTION_SOURCE,
                CROSS_DEVICE_PARAMS,
                mComputerControlSessionCallback);

        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS).times(1))
                .onSessionCreated(anyInt(), any());
    }

    @Test
    @EnableFlags(android.companion.virtualdevice.flags.Flags.FLAG_COMPUTER_CONTROL_CROSS_DEVICE)
    @EnableFlags(android.companion.Flags.FLAG_SUPPORT_AI_AGENT)
    public void crossDeviceRequest_notAuthorized_sessionNotCreated() throws Exception {
        when(mAuthenticationPolicyService.isAgentAuthorized(
                        any(UserHandle.class), eq(Context.DEVICE_ID_DEFAULT), eq(COMP_DEVICE_ID)))
                .thenReturn(false);

        mProcessor.processNewSessionRequest(
                mAppThread,
                ATTRIBUTION_SOURCE,
                CROSS_DEVICE_PARAMS,
                mComputerControlSessionCallback);

        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreationFailed(ComputerControlSession.ERROR_PERMISSION_DENIED);
    }

    @Test
    @EnableFlags(android.companion.virtualdevice.flags.Flags.FLAG_COMPUTER_CONTROL_CROSS_DEVICE)
    @EnableFlags(android.companion.Flags.FLAG_SUPPORT_AI_AGENT)
    public void crossDeviceRequest_passesCompanionDeviceIdToPolicy() throws Exception {
        mProcessor.processNewSessionRequest(
                mAppThread,
                ATTRIBUTION_SOURCE,
                CROSS_DEVICE_PARAMS,
                mComputerControlSessionCallback);

        verify(mAuthenticationPolicyService, timeout(CALLBACK_TIMEOUT_MS))
                .isAgentAuthorized(
                        any(UserHandle.class), eq(Context.DEVICE_ID_DEFAULT), eq(COMP_DEVICE_ID));
    }

    @Test
    public void callerWithoutVisibleWindow_nonDefaultDevice_sessionNotCreated() throws Exception {
        when(mVirtualDeviceManagerInternal.isDeviceIdAssociationValid(
                        ATTRIBUTION_SOURCE.getUid(), NON_DEFAULT_DEVICE_ID))
                .thenReturn(true);
        when(mActivityTaskManagerInternal.isUidForeground(ATTRIBUTION_SOURCE.getUid()))
                .thenReturn(false);

        mProcessor.processNewSessionRequest(
                mAppThread,
                ATTRIBUTION_SOURCE.withDeviceId(NON_DEFAULT_DEVICE_ID),
                PARAMS,
                mComputerControlSessionCallback);

        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreationFailed(ComputerControlSession.ERROR_PERMISSION_DENIED);
    }

    @Test
    public void consentDeniedInSettings_sessionNotCreated() throws Exception {
        when(mAppOpsManager.noteOpNoThrow(eq(AppOpsManager.OP_COMPUTER_CONTROL), any(), any()))
                .thenReturn(AppOpsManager.MODE_IGNORED);
        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback);

        verify(mComputerControlSessionCallback)
                .onSessionCreationFailed(ComputerControlSession.ERROR_PERMISSION_DENIED);
    }

    @Test
    public void callerWithoutVisibleWindow_sessionNotCreated() throws Exception {
        when(mActivityTaskManagerInternal.isUidForeground(ATTRIBUTION_SOURCE.getUid()))
                .thenReturn(false);

        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback);

        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreationFailed(ComputerControlSession.ERROR_PERMISSION_DENIED);
    }

    @Test
    public void callerNotAllowListed_throwsException() throws Exception {
        when(mAllowlistController.isPackageAllowedToCreateSession(anyString(), any(), any(),
                anyInt())).thenReturn(false);

        assertThrows(SecurityException.class,
                () -> mProcessor.processNewSessionRequest(
                        mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback));
    }

    @Test
    public void anyTargetAppNotAllowListed_throwsException() throws Exception {
        when(mAllowlistController.isPackageAutomatable(
                eq(ANOTHER_TARGET_PACKAGE), eq(OWNER_PACKAGE_NAME), any())).thenReturn(false);

        ComputerControlSessionParams params = new ComputerControlSessionParams.Builder()
                .setName(ComputerControlSessionImplTest.class.getSimpleName())
                .setTargetPackageNames(List.of(TARGET_PACKAGE, ANOTHER_TARGET_PACKAGE))
                .build();
        assertThrows(IllegalArgumentException.class, () -> {
            mProcessor.processNewSessionRequest(
                    mAppThread, ATTRIBUTION_SOURCE, params, mComputerControlSessionCallback);
        });
    }

    @Test
    public void allTargetAppsAllowListed_sessionCreated() throws Exception {
        when(mAllowlistController.isPackageAutomatable(
                eq(ANOTHER_TARGET_PACKAGE), eq(OWNER_PACKAGE_NAME), any())).thenReturn(true);

        ComputerControlSessionParams params = new ComputerControlSessionParams.Builder()
                .setName(ComputerControlSessionImplTest.class.getSimpleName())
                .setTargetPackageNames(List.of(TARGET_PACKAGE, ANOTHER_TARGET_PACKAGE))
                .setAppInteractionAttribution(APP_INTERACTION_ATTRIBUTION)
                .build();
        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE, params, mComputerControlSessionCallback);

        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS).times(1))
                .onSessionCreated(anyInt(), any());
    }

    @Test
    public void maximumNumberOfSessions_isEnforced() throws Exception {
        try {
            for (int i = 0; i < MAXIMUM_CONCURRENT_SESSIONS; ++i) {
                mProcessor.processNewSessionRequest(
                        mAppThread, ATTRIBUTION_SOURCE, generateUniqueParams(i),
                        mComputerControlSessionCallback);
            }
            verify(mComputerControlSessionCallback,
                    timeout(CALLBACK_TIMEOUT_MS).times(MAXIMUM_CONCURRENT_SESSIONS))
                    .onSessionCreated(anyInt(), mSessionArgumentCaptor.capture());

            mProcessor.processNewSessionRequest(
                    mAppThread, ATTRIBUTION_SOURCE, generateUniqueParams(-1),
                    mComputerControlSessionCallback);
            verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                    .onSessionCreationFailed(ComputerControlSession.ERROR_SESSION_LIMIT_REACHED);

            // Close the first session.
            var firstSession = mSessionArgumentCaptor.getAllValues().getFirst();
            firstSession.initialize(mLifecycleCallback, mSurface);
            firstSession.close();
            verify(mLifecycleCallback, timeout(SESSION_CLOSE_TIMEOUT_MS)).onClosed(anyInt());

            mProcessor.processNewSessionRequest(
                    mAppThread, ATTRIBUTION_SOURCE, generateUniqueParams(-1),
                    mComputerControlSessionCallback);
            verify(mComputerControlSessionCallback,
                    timeout(CALLBACK_TIMEOUT_MS).times(MAXIMUM_CONCURRENT_SESSIONS + 1))
                    .onSessionCreated(anyInt(), mSessionArgumentCaptor.capture());
        } finally {
            for (IComputerControlSession session : mSessionArgumentCaptor.getAllValues()) {
                session.close();
            }
        }
    }

    @Test
    public void onSessionPending_consentGranted_sessionCreated() throws Exception {
        when(mAppOpsManager.noteOpNoThrow(eq(AppOpsManager.OP_COMPUTER_CONTROL), any(), any()))
                .thenReturn(AppOpsManager.MODE_DEFAULT);

        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback);
        verify(mPendingIntentFactory).create(any(), anyInt(), mIntentArgumentCaptor.capture());
        verify(mComputerControlSessionCallback).onSessionPending(any());

        ResultReceiver resultReceiver = mIntentArgumentCaptor.getValue().getParcelableExtra(
                Intent.EXTRA_RESULT_RECEIVER, ResultReceiver.class);
        resultReceiver.send(Activity.RESULT_OK, null);
        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreated(anyInt(), any());
    }

    @Test
    public void onSessionPending_consentDenied_sessionCreationFailed() throws Exception {
        when(mAppOpsManager.noteOpNoThrow(eq(AppOpsManager.OP_COMPUTER_CONTROL), any(), any()))
                .thenReturn(AppOpsManager.MODE_DEFAULT);

        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback);
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
        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback);
        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreated(anyInt(), any());
        assertThrows(IllegalArgumentException.class,
                () -> mProcessor.processNewSessionRequest(
                        mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback));
    }

    @Test
    public void validateParams_packageNamesAreValid() throws Exception {
        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback);

        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreated(anyInt(), any());
    }

    @Test
    public void validateParams_deviceOwner_policyAllowed_sessionCreated()
            throws RemoteException {
        when(mDevicePolicyManagerInternal.getDeviceOwnerComponent(anyBoolean()))
                .thenReturn(new ComponentName("com.test.admin", "DeviceOwner"));
        when(mDevicePolicyManager.getNearbyAppStreamingPolicy(anyInt()))
                .thenReturn(DevicePolicyManager.NEARBY_STREAMING_ENABLED);

        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback);

        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreated(anyInt(), any());
    }

    @Test
    public void validateParams_deviceOwner_policyDisallowed_throwsSecurityException() {
        when(mDevicePolicyManagerInternal.getDeviceOwnerComponent(anyBoolean()))
                .thenReturn(new ComponentName("com.test.admin", "DeviceOwner"));
        when(mDevicePolicyManager.getNearbyAppStreamingPolicy(anyInt()))
                .thenReturn(DevicePolicyManager.NEARBY_STREAMING_DISABLED);

        assertThrows(SecurityException.class, () ->
                mProcessor.processNewSessionRequest(
                        mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback));
    }

    @Test
    public void validateParams_managedProfile_throwsSecurityException() {
        when(mUserManager.isManagedProfile(CALLING_USER_ID)).thenReturn(true);

        assertThrows(SecurityException.class, () ->
                mProcessor.processNewSessionRequest(
                        mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback));
    }

    @Test
    public void validateParams_copeDevice_policyAllowed_sessionCreated()
            throws RemoteException {
        when(mDevicePolicyManager.isOrganizationOwnedDeviceWithManagedProfile()).thenReturn(true);
        UserInfo userInfo = new UserInfo(CALLING_USER_ID, "name", UserInfo.FLAG_MANAGED_PROFILE);
        when(mUserManager.getUserInfo(CALLING_USER_ID)).thenReturn(userInfo);
        DevicePolicyManager parentDpm = mock(DevicePolicyManager.class);
        when(mDevicePolicyManager.getParentProfileInstance(userInfo)).thenReturn(parentDpm);
        when(parentDpm.getNearbyAppStreamingPolicy())
                .thenReturn(DevicePolicyManager.NEARBY_STREAMING_ENABLED);

        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback);

        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreated(anyInt(), any());
    }

    @Test
    public void validateParams_copeDevice_policyDisallowed_throwsSecurityException() {
        when(mDevicePolicyManager.isOrganizationOwnedDeviceWithManagedProfile()).thenReturn(true);
        UserInfo userInfo = new UserInfo(CALLING_USER_ID, "name", UserInfo.FLAG_MANAGED_PROFILE);
        when(mUserManager.getUserInfo(CALLING_USER_ID)).thenReturn(userInfo);
        DevicePolicyManager parentDpm = mock(DevicePolicyManager.class);
        when(mDevicePolicyManager.getParentProfileInstance(userInfo)).thenReturn(parentDpm);
        when(parentDpm.getNearbyAppStreamingPolicy())
                .thenReturn(DevicePolicyManager.NEARBY_STREAMING_DISABLED);

        assertThrows(SecurityException.class, () ->
                mProcessor.processNewSessionRequest(
                        mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback));
    }

    @Test
    public void isComputerControlDisplay_returnsTrueForDisplaysWithActiveSession()
            throws Exception {
        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback);
        verify(mComputerControlSessionCallback,
                timeout(CALLBACK_TIMEOUT_MS).times(1))
                .onSessionCreated(anyInt(), mSessionArgumentCaptor.capture());

        assertTrue(mProcessor.isComputerControlDisplay(VIRTUAL_DISPLAY_ID));

        mSessionArgumentCaptor.getValue().close();
        verify(mVirtualDevice, timeout(SESSION_CLOSE_TIMEOUT_MS)).close();
        assertFalse(mProcessor.isComputerControlDisplay(VIRTUAL_DISPLAY_ID));
    }

    @Test
    public void isComputerControlNotification_notificationInfoAttached_returnsTrue()
            throws Exception {
        final int notificationId = 5;
        final String notificationTag = "hello";
        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback);
        verify(mComputerControlSessionCallback,
                timeout(CALLBACK_TIMEOUT_MS).times(1))
                .onSessionCreated(anyInt(), mSessionArgumentCaptor.capture());

        mSessionArgumentCaptor.getValue().attachNotificationInfo(notificationId, notificationTag);

        assertTrue(mProcessor.isComputerControlNotification(notificationId, notificationTag,
                OWNER_PACKAGE_NAME));
        assertFalse(mProcessor.isComputerControlNotification(6, null, OWNER_PACKAGE_NAME));
    }

    @Test
    public void isComputerControlNotification_notificationInfoNotAttached_returnsFalse()
            throws Exception {
        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback);
        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS).times(1))
                .onSessionCreated(anyInt(), any());

        assertFalse(mProcessor.isComputerControlNotification(5, "hello", OWNER_PACKAGE_NAME));
    }

    @Test
    public void isComputerControlSession_returnsCorrectly() throws Exception {
        // Pre-condition: No sessions exist.
        assertFalse(mProcessor.isComputerControlSession(DEVICE_ID));

        // Create a session.
        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback);
        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreated(anyInt(), mSessionArgumentCaptor.capture());

        // Assert session with correct device ID is found.
        assertTrue(mProcessor.isComputerControlSession(DEVICE_ID));
        // Assert session with incorrect device ID is not found.
        assertFalse(mProcessor.isComputerControlSession(DEVICE_ID + 1));

        // Close the session.
        mSessionArgumentCaptor.getValue().close();

        // Assert session is no longer found after closing.
        verify(mVirtualDevice, timeout(SESSION_CLOSE_TIMEOUT_MS)).close();
        assertFalse(mProcessor.isComputerControlSession(DEVICE_ID));
    }

    @Test
    public void closeSessionByUserIntent_sessionExists_closesSession() throws Exception {
        // Create a session.
        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback);
        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreated(anyInt(), any());

        // Verify session exists before closing.
        assertTrue(mProcessor.isComputerControlSession(DEVICE_ID));

        // Close the session via the processor's public API.
        mProcessor.closeSessionByUserIntent(DEVICE_ID);

        // Verify the session is closed and removed.
        verify(mVirtualDevice, timeout(SESSION_CLOSE_TIMEOUT_MS)).close();
        assertFalse(mProcessor.isComputerControlSession(DEVICE_ID));
    }

    @Test
    public void closeSessionByUserIntent_sessionDoesNotExist_doesNothing() throws Exception {
        // Create a session.
        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback);
        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreated(anyInt(), any());

        // Verify session exists before trying to close a different one.
        assertTrue(mProcessor.isComputerControlSession(DEVICE_ID));

        // Attempt to close a session with an unknown device ID.
        mProcessor.closeSessionByUserIntent(DEVICE_ID + 1);

        // Verify the existing session was not closed.
        assertTrue(mProcessor.isComputerControlSession(DEVICE_ID));
        // And the underlying virtual device was not closed.
        verify(mVirtualDevice, never()).close();
    }

    @Test
    public void appInterActionAttribution_notProvided_notEnforced() throws Exception {
        ComputerControlSessionParams params = new ComputerControlSessionParams.Builder()
                .setName(PARAMS.getName())
                .setTargetPackageNames(PARAMS.getTargetPackageNames())
                .build();

        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE, params, mComputerControlSessionCallback);

        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreated(anyInt(), any());
    }

    @Test
    public void removeAppFromAutomatableAppListForAgent_closesSession() throws Exception {
        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback);
        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreated(anyInt(), any());

        mProcessor.removeAppFromAutomatableAppListForAgent(
                ATTRIBUTION_SOURCE.getUid(), OWNER_PACKAGE_NAME, TARGET_PACKAGE);

        verify(mVirtualDevice, timeout(SESSION_CLOSE_TIMEOUT_MS)).close();
    }

    @Test
    public void removeAppFromAutomatableAppListForAgent_differentPackage_doesNotCloseSession()
            throws Exception {
        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback);
        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreated(anyInt(), any());

        mProcessor.removeAppFromAutomatableAppListForAgent(
                ATTRIBUTION_SOURCE.getUid(), OWNER_PACKAGE_NAME, ANOTHER_TARGET_PACKAGE);

        verify(mVirtualDevice, never()).close();
    }

    @Test
    public void clearAutomatableAppListForAgent_closesSession() throws Exception {
        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback);
        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreated(anyInt(), any());

        mProcessor.clearAutomatableAppListForAgent(ATTRIBUTION_SOURCE.getUid(), OWNER_PACKAGE_NAME);

        verify(mVirtualDevice, timeout(SESSION_CLOSE_TIMEOUT_MS)).close();
    }

    @Test
    @EnableFlags(android.companion.virtualdevice.flags.Flags.FLAG_COMPUTER_CONTROL_PER_APP_CONSENT)
    public void processNewSessionRequest_perAppConsentEnabled_notAutomatable_sendsPendingIntent()
            throws Exception {
        when(mAllowlistController.doesAgentHaveConsentToAutomateTargetApp(anyInt(), anyString(),
                anyString())).thenReturn(false);

        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback);

        verify(mPendingIntentFactory).create(any(), anyInt(), mIntentArgumentCaptor.capture());
        verify(mComputerControlSessionCallback).onSessionPending(any());
    }

    @Test
    public void isActiveComputerControlDisplay_sessionActive_returnsTrue() throws Exception {
        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback);
        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreated(anyInt(), mSessionArgumentCaptor.capture());

        IComputerControlSession session = mSessionArgumentCaptor.getValue();
        session.initialize(mLifecycleCallback, mSurface);

        assertTrue(mProcessor.isActiveComputerControlDisplay(VIRTUAL_DISPLAY_ID));
    }

    @Test
    public void isActiveComputerControlDisplay_sessionBlocked_returnsFalse() throws Exception {
        mProcessor.processNewSessionRequest(
                mAppThread, ATTRIBUTION_SOURCE, PARAMS, mComputerControlSessionCallback);
        verify(mComputerControlSessionCallback, timeout(CALLBACK_TIMEOUT_MS))
                .onSessionCreated(anyInt(), mSessionArgumentCaptor.capture());

        IComputerControlSession session = mSessionArgumentCaptor.getValue();
        session.initialize(mLifecycleCallback, mSurface);

        session.notifyBlocked();

        assertFalse(mProcessor.isActiveComputerControlDisplay(VIRTUAL_DISPLAY_ID));
    }

    @Test
    public void isActiveComputerControlDisplay_invalidDisplay_returnsFalse() {
        assertFalse(mProcessor.isActiveComputerControlDisplay(999));
    }

    private ComputerControlSessionParams generateUniqueParams(int index) {
        return new ComputerControlSessionParams.Builder()
                .setName(PARAMS.getName() + index)
                .setTargetPackageNames(PARAMS.getTargetPackageNames())
                .setAppInteractionAttribution(PARAMS.getAppInteractionAttribution())
                .build();
    }
}
