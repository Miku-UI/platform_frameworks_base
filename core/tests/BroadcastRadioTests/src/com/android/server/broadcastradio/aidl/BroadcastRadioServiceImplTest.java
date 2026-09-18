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
package com.android.server.broadcastradio.aidl;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import android.hardware.broadcastradio.IBroadcastRadio;
import android.hardware.radio.Announcement;
import android.hardware.radio.IAnnouncementListener;
import android.hardware.radio.ICloseHandle;
import android.hardware.radio.ITuner;
import android.hardware.radio.ITunerCallback;
import android.hardware.radio.RadioManager;
import android.hardware.radio.RadioTuner;
import android.os.IBinder;
import android.os.IInterface;
import android.os.IServiceCallback;
import android.os.RemoteException;
import android.os.ServiceManager;

import com.android.dx.mockito.inline.extended.StaticMockitoSessionBuilder;
import com.android.server.broadcastradio.ExtendedRadioMockitoTestCase;
import com.android.server.broadcastradio.RadioServiceUserController;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class BroadcastRadioServiceImplTest extends ExtendedRadioMockitoTestCase {

    private static final String SERVICE_NAME_AMFM = "amfm_mock_service";
    private static final int FM_RADIO_MODULE_ID = 0;
    private static final int DAB_RADIO_MODULE_ID = 1;
    private static final ArrayList<String> SERVICE_LIST =
            new ArrayList<>(Arrays.asList("FmService", "DabService"));
    private static final int[] TEST_ENABLED_TYPES = new int[]{Announcement.TYPE_TRAFFIC};

    private BroadcastRadioServiceImpl mBroadcastRadioService;
    private IBinder.DeathRecipient mFmDeathRecipient;
    private IServiceCallback mServiceCallback;
    @Mock
    private ThreadFactory mThreadFactoryMock;
    @Mock
    private Thread mThreadMock;

    @Mock
    private RadioManager.ModuleProperties mFmModuleMock;
    @Mock
    private RadioManager.ModuleProperties mDabModuleMock;
    @Mock
    private RadioModule mFmRadioModuleMock;
    @Mock
    private RadioModule mDabRadioModuleMock;
    @Mock
    private IBroadcastRadio mFmHalServiceMock;
    @Mock
    private IBroadcastRadio mDabHalServiceMock;
    @Mock
    private IBinder mFmBinderMock;
    @Mock
    private IBinder mDabBinderMock;
    @Mock
    private TunerSession mFmTunerSessionMock;
    @Mock
    private ITunerCallback mTunerCallbackMock;
    @Mock
    private ICloseHandle mFmCloseHandleMock;
    @Mock
    private ICloseHandle mDabCloseHandleMock;
    @Mock
    private IAnnouncementListener mAnnouncementListenerMock;
    @Mock
    private IBinder mListenerBinderMock;
    @Mock
    private RadioServiceUserController mUserControllerMock;

    @Override
    protected void initializeSession(StaticMockitoSessionBuilder builder) {
        builder.spyStatic(ServiceManager.class)
                .spyStatic(RadioModule.class)
                .spyStatic(Executors.class);
    }

    @Before
    public void setUp() throws RemoteException {
        when(Executors.defaultThreadFactory()).thenReturn(mThreadFactoryMock);
        doAnswer(invocation -> {
            Runnable r = invocation.getArgument(0);
            when(mThreadMock.getName()).thenReturn("TestThread");
            doAnswer(t -> {
                r.run();
                return null;
            }).when(mThreadMock).start();
            return mThreadMock;
        }).when(mThreadFactoryMock).newThread(any(Runnable.class));

        when(mUserControllerMock.isCurrentOrSystemUser()).thenReturn(true);
        mockServiceManager();
        mBroadcastRadioService = new BroadcastRadioServiceImpl(SERVICE_LIST, mUserControllerMock,
                mThreadFactoryMock);
    }

    @Test
    public void onRegistration_executesTask() throws Exception {
        IBinder mockBinder = mock(IBinder.class);
        when(mockBinder.queryLocalInterface(anyString())).thenReturn(mock(IInterface.class));

        mServiceCallback.onRegistration(SERVICE_NAME_AMFM, mockBinder);

        verify(mThreadFactoryMock).newThread(any(Runnable.class));
        verify(mThreadMock).start();
    }

    @Test
    public void onRegistration_withNullBinder_doesNotExecuteTask() throws Exception {
        mServiceCallback.onRegistration(SERVICE_NAME_AMFM, null);

        verify(mThreadFactoryMock, never()).newThread(any(Runnable.class));
        verify(mThreadMock, never()).start();
    }

    @Test
    public void processRegistration_linksToDeath() throws Exception {
        IBinder mockBinder = mock(IBinder.class);
        IInterface mockIInterface = mock(IInterface.class);
        when(mockBinder.queryLocalInterface(anyString())).thenReturn(mockIInterface);
        when(mockBinder.isBinderAlive()).thenReturn(true);

        android.hardware.broadcastradio.IBroadcastRadio mockBroadcastRadio =
                mock(android.hardware.broadcastradio.IBroadcastRadio.class);
        when(mockBinder.queryLocalInterface(anyString())).thenReturn(mockBroadcastRadio);
        doReturn(mFmRadioModuleMock).when(() -> RadioModule.tryLoadingModule(
                anyInt(), anyString(), eq(mockBinder), any()));
        when(mFmRadioModuleMock.getService()).thenReturn(mockBroadcastRadio);
        when(mockBroadcastRadio.asBinder()).thenReturn(mockBinder);

        mServiceCallback.onRegistration(SERVICE_NAME_AMFM, mockBinder);

        verify(mockBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());
    }

    @Test
    public void listModules_withMultipleServiceNames() throws Exception {
        registerService("FmService", mFmBinderMock);
        registerService("DabService", mDabBinderMock);

        assertWithMessage("Radio modules in AIDL broadcast radio HAL client")
                .that(mBroadcastRadioService.listModules())
                .containsExactly(mFmModuleMock, mDabModuleMock);
    }

    @Test
    public void hasModules_withIdFoundInModules() throws Exception {
        registerService("FmService", mFmBinderMock);
        registerService("DabService", mDabBinderMock);

        assertWithMessage("DAB radio module in AIDL broadcast radio HAL client")
                .that(mBroadcastRadioService.hasModule(DAB_RADIO_MODULE_ID)).isTrue();
    }

    @Test
    public void hasModules_withIdNotFoundInModules() throws Exception {
        registerService("FmService", mFmBinderMock);
        registerService("DabService", mDabBinderMock);

        assertWithMessage("Radio module of id not found in AIDL broadcast radio HAL client")
                .that(mBroadcastRadioService.hasModule(DAB_RADIO_MODULE_ID + 1)).isFalse();
    }

    @Test
    public void hasAnyModules_withModulesExist() throws Exception {
        registerService("FmService", mFmBinderMock);

        assertWithMessage("Any radio module in AIDL broadcast radio HAL client")
                .that(mBroadcastRadioService.hasAnyModules()).isTrue();
    }

    @Test
    public void openSession_withIdFound() throws Exception {
        registerService("FmService", mFmBinderMock);

        ITuner session = mBroadcastRadioService.openSession(FM_RADIO_MODULE_ID,
                /* legacyConfig= */ null, /* withAudio= */ true, mTunerCallbackMock);

        assertWithMessage("Session opened in FM radio module")
                .that(session).isEqualTo(mFmTunerSessionMock);
    }

    @Test
    public void openSession_withIdNotFound() throws Exception {
        registerService("FmService", mFmBinderMock);

        ITuner session = mBroadcastRadioService.openSession(DAB_RADIO_MODULE_ID + 1,
                /* legacyConfig= */ null, /* withAudio= */ true, mTunerCallbackMock);

        assertWithMessage("Session opened with id not found").that(session).isNull();
    }

    @Test
    public void openSession_forNonCurrentUser_throwsException() {
        doReturn(false).when(mUserControllerMock).isCurrentOrSystemUser();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> mBroadcastRadioService.openSession(FM_RADIO_MODULE_ID,
                        /* legacyConfig= */ null, /* withAudio= */ true, mTunerCallbackMock));

        assertWithMessage("Exception for opening session by non-current user")
                .that(thrown).hasMessageThat().contains("Cannot open session for non-current user");
    }

    @Test
    public void openSession_withoutAudio_fails() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> mBroadcastRadioService.openSession(FM_RADIO_MODULE_ID,
                        /* legacyConfig= */ null, /* withAudio= */ false, mTunerCallbackMock));

        assertWithMessage("Exception for opening session without audio")
                .that(thrown).hasMessageThat().contains("not supported");
    }

    @Test
    public void binderDied_forDeathRecipient() throws Exception {
        registerService("FmService", mFmBinderMock);

        mFmDeathRecipient.binderDied();

        verify(mFmRadioModuleMock).closeSessions(eq(RadioTuner.ERROR_HARDWARE_FAILURE));
        assertWithMessage("FM radio module after FM broadcast radio HAL service died")
                .that(mBroadcastRadioService.hasModule(FM_RADIO_MODULE_ID)).isFalse();
    }

    @Test
    public void addAnnouncementListener_addsOnAllRadioModules() throws Exception {
        registerService("FmService", mFmBinderMock);
        registerService("DabService", mDabBinderMock);
        when(mAnnouncementListenerMock.asBinder()).thenReturn(mListenerBinderMock);
        when(mFmRadioModuleMock.addAnnouncementListener(any(), any()))
                .thenReturn(mFmCloseHandleMock);
        when(mDabRadioModuleMock.addAnnouncementListener(any(), any()))
                .thenReturn(mDabCloseHandleMock);

        mBroadcastRadioService.addAnnouncementListener(TEST_ENABLED_TYPES,
                mAnnouncementListenerMock);

        verify(mFmRadioModuleMock).addAnnouncementListener(any(), any());
        verify(mDabRadioModuleMock).addAnnouncementListener(any(), any());
    }

    private void registerService(String serviceName, IBinder binder) throws RemoteException {
        mServiceCallback.onRegistration(serviceName, binder);
    }

    private void mockServiceManager() throws RemoteException {
        doAnswer((Answer<Void>) invocation -> {
            mServiceCallback = (IServiceCallback) invocation.getArguments()[1];
            return null;
        }).when(() -> ServiceManager.registerForNotifications(anyString(),
                any(IServiceCallback.class)));

        doReturn(mFmRadioModuleMock).when(() -> RadioModule.tryLoadingModule(
                eq(FM_RADIO_MODULE_ID), anyString(), any(IBinder.class), any()));
        doReturn(mDabRadioModuleMock).when(() -> RadioModule.tryLoadingModule(
                        eq(DAB_RADIO_MODULE_ID), anyString(), any(IBinder.class), any()));

        when(mFmRadioModuleMock.getProperties()).thenReturn(mFmModuleMock);
        when(mDabRadioModuleMock.getProperties()).thenReturn(mDabModuleMock);

        when(mFmRadioModuleMock.getService()).thenReturn(mFmHalServiceMock);
        when(mDabRadioModuleMock.getService()).thenReturn(mDabHalServiceMock);

        when(mFmHalServiceMock.asBinder()).thenReturn(mFmBinderMock);
        when(mDabHalServiceMock.asBinder()).thenReturn(mDabBinderMock);

        doAnswer(invocation -> {
            mFmDeathRecipient = (IBinder.DeathRecipient) invocation.getArguments()[0];
            return null;
        }).when(mFmBinderMock).linkToDeath(any(), anyInt());

        when(mFmRadioModuleMock.openSession(mTunerCallbackMock)).thenReturn(mFmTunerSessionMock);
    }
}
