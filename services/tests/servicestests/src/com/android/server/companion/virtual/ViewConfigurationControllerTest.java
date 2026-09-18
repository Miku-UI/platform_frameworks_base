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

package com.android.server.companion.virtual;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.companion.virtual.ViewConfigurationParams;
import android.companion.virtualdevice.flags.Flags;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.om.OverlayConstraint;
import android.content.om.OverlayManager;
import android.content.om.OverlayManagerTransaction;
import android.content.pm.UserInfo;
import android.os.UserManager;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.testing.AndroidTestingRunner;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class ViewConfigurationControllerTest {

    private static final int DEVICE_ID = 5;
    private static final int USER_ID_1 = 0;
    private static final int USER_ID_2 = 10;

    @Rule
    public SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private ViewConfigurationController mViewConfigurationController;
    private AutoCloseable mMocks;
    @Mock
    private OverlayManager mOverlayManagerMock;
    @Mock
    private UserManager mUserManagerMock;
    @Mock
    private ViewConfigurationController.SettingsWriter mSettingsWriter;
    @Captor
    private ArgumentCaptor<OverlayManagerTransaction> mTransactionArgumentCaptor;
    @Captor
    private ArgumentCaptor<String> mSettingsKeyCaptor;

    @Before
    public void setUp() throws Exception {
        mMocks = MockitoAnnotations.openMocks(this);
        Context context = Mockito.spy(new ContextWrapper(
                InstrumentationRegistry.getInstrumentation().getTargetContext()));
        when(context.getSystemService(OverlayManager.class)).thenReturn(mOverlayManagerMock);
        when(context.getSystemService(UserManager.class)).thenReturn(mUserManagerMock);
        mViewConfigurationController = new ViewConfigurationController(context, mSettingsWriter);
    }

    @After
    public void tearDown() throws Exception {
        mMocks.close();
    }

    @Test
    @DisableFlags(Flags.FLAG_MULTI_USER_VIEW_CONFIGURATION)
    public void applyViewConfigurationParams_enablesResourceOverlay() {
        mViewConfigurationController.applyViewConfigurationParams(DEVICE_ID,
                createParamsRequiringResourceOverlay());

        verify(mOverlayManagerMock).commit(mTransactionArgumentCaptor.capture());
        OverlayManagerTransaction transaction = mTransactionArgumentCaptor.getValue();
        List<OverlayManagerTransaction.Request> requests = new ArrayList<>();
        transaction.getRequests().forEachRemaining(requests::add);
        assertThat(requests).hasSize(2);
        OverlayManagerTransaction.Request request0 = requests.get(0);
        assertEquals(OverlayManagerTransaction.Request.TYPE_REGISTER_FABRICATED, request0.type);
        List<OverlayConstraint> constraints0 = request0.constraints;
        assertThat(constraints0).hasSize(0);
        OverlayManagerTransaction.Request request1 = requests.get(1);
        List<OverlayConstraint> constraints1 = request1.constraints;
        assertEquals(OverlayManagerTransaction.Request.TYPE_SET_ENABLED, request1.type);
        assertThat(constraints1).hasSize(1);
        OverlayConstraint constraint = constraints1.get(0);
        assertEquals(OverlayConstraint.TYPE_DEVICE_ID, constraint.getType());
        assertEquals(DEVICE_ID, constraint.getValue());
    }

    @Test
    @EnableFlags(Flags.FLAG_MULTI_USER_VIEW_CONFIGURATION)
    public void applyViewConfigurationParams_enablesResourceOverlay_forAliveUsers() {
        when(mUserManagerMock.getAliveUsers()).thenReturn(
                List.of(new UserInfo(USER_ID_1, "user1", 0),
                        new UserInfo(USER_ID_2, "user2", 0)));

        mViewConfigurationController.applyViewConfigurationParams(DEVICE_ID,
                createParamsRequiringResourceOverlay());

        verify(mOverlayManagerMock).commit(mTransactionArgumentCaptor.capture());
        OverlayManagerTransaction transaction = mTransactionArgumentCaptor.getValue();
        List<OverlayManagerTransaction.Request> requests = new ArrayList<>();
        transaction.getRequests().forEachRemaining(requests::add);
        assertThat(requests).hasSize(3);
        OverlayManagerTransaction.Request request0 = requests.get(0);
        assertEquals(OverlayManagerTransaction.Request.TYPE_REGISTER_FABRICATED, request0.type);
        List<OverlayConstraint> constraints0 = request0.constraints;
        assertThat(constraints0).hasSize(0);
        OverlayManagerTransaction.Request request1 = requests.get(1);
        List<OverlayConstraint> constraints1 = request1.constraints;
        assertEquals(OverlayManagerTransaction.Request.TYPE_SET_ENABLED, request1.type);
        assertThat(constraints1).hasSize(1);
        OverlayConstraint constraint1 = constraints1.get(0);
        assertEquals(OverlayConstraint.TYPE_DEVICE_ID, constraint1.getType());
        assertEquals(DEVICE_ID, constraint1.getValue());
        assertEquals(USER_ID_1, request1.userId);
        OverlayManagerTransaction.Request request2 = requests.get(2);
        List<OverlayConstraint> constraints2 = request2.constraints;
        assertEquals(OverlayManagerTransaction.Request.TYPE_SET_ENABLED, request2.type);
        assertThat(constraints2).hasSize(1);
        OverlayConstraint constraint2 = constraints2.get(0);
        assertEquals(OverlayConstraint.TYPE_DEVICE_ID, constraint2.getType());
        assertEquals(DEVICE_ID, constraint2.getValue());
        assertEquals(USER_ID_2, request2.userId);
    }

    @Test
    public void close_disablesResourceOverlay() {
        mViewConfigurationController.applyViewConfigurationParams(DEVICE_ID,
                createParamsRequiringResourceOverlay());
        clearInvocations(mOverlayManagerMock);

        mViewConfigurationController.close();

        verify(mOverlayManagerMock).commit(mTransactionArgumentCaptor.capture());
        OverlayManagerTransaction transaction = mTransactionArgumentCaptor.getValue();
        List<OverlayManagerTransaction.Request> requests = new ArrayList<>();
        transaction.getRequests().forEachRemaining(requests::add);
        assertThat(requests).hasSize(1);
        OverlayManagerTransaction.Request request = requests.get(0);
        assertEquals(OverlayManagerTransaction.Request.TYPE_UNREGISTER_FABRICATED, request.type);
        List<OverlayConstraint> constraints = request.constraints;
        assertThat(constraints).hasSize(0);
        verifyNoInteractions(mSettingsWriter);
    }

    @Test
    public void applyViewConfigurationParams_doesNotEnableResourceOverlay() {
        mViewConfigurationController.applyViewConfigurationParams(DEVICE_ID,
                createParamsRequiringSettingsOverride());
        verifyNoInteractions(mOverlayManagerMock);
    }

    @Test
    public void applyViewConfigurationParams_doesNotWriteSettings() {
        mViewConfigurationController.applyViewConfigurationParams(DEVICE_ID,
                createParamsRequiringResourceOverlay());
        verifyNoInteractions(mSettingsWriter);
    }

    @Test
    @DisableFlags(Flags.FLAG_MULTI_USER_VIEW_CONFIGURATION)
    public void applyViewConfigurationParams_writesSettings() {
        mViewConfigurationController.applyViewConfigurationParams(DEVICE_ID,
                createParamsRequiringSettingsOverride());

        verify(mSettingsWriter, times(2)).writeSettings(mSettingsKeyCaptor.capture(),
                anyInt(), eq(DEVICE_ID), anyInt());
        assertThat(mSettingsKeyCaptor.getAllValues()).containsExactly(
                Settings.Secure.LONG_PRESS_TIMEOUT, Settings.Secure.MULTI_PRESS_TIMEOUT);
    }

    @Test
    @EnableFlags(Flags.FLAG_MULTI_USER_VIEW_CONFIGURATION)
    public void applyViewConfigurationParams_writesSettings_forAliveUsers() {
        when(mUserManagerMock.getAliveUsers()).thenReturn(
                List.of(new UserInfo(USER_ID_1, "user1", 0),
                        new UserInfo(USER_ID_2, "user2", 0)));

        mViewConfigurationController.applyViewConfigurationParams(DEVICE_ID,
                createParamsRequiringSettingsOverride());

        verify(mSettingsWriter, times(2)).writeSettings(mSettingsKeyCaptor.capture(),
                anyInt(), eq(DEVICE_ID), eq(USER_ID_1));
        verify(mSettingsWriter, times(2)).writeSettings(mSettingsKeyCaptor.capture(),
                anyInt(), eq(DEVICE_ID), eq(USER_ID_2));
        assertThat(mSettingsKeyCaptor.getAllValues()).containsExactly(
                Settings.Secure.LONG_PRESS_TIMEOUT, Settings.Secure.MULTI_PRESS_TIMEOUT,
                Settings.Secure.LONG_PRESS_TIMEOUT, Settings.Secure.MULTI_PRESS_TIMEOUT);
    }

    @Test
    @DisableFlags(Flags.FLAG_MULTI_USER_VIEW_CONFIGURATION)
    public void applyViewConfigurationParamsForUser_doesNotEnableResourceOverlay_forGivenUser() {
        when(mUserManagerMock.getAliveUsers()).thenReturn(
                List.of(new UserInfo(USER_ID_1, "user1", 0)));
        ViewConfigurationParams params = createParamsRequiringResourceOverlay();
        mViewConfigurationController.applyViewConfigurationParams(DEVICE_ID, params);
        clearInvocations(mOverlayManagerMock);

        mViewConfigurationController.applyViewConfigurationParamsForUser(USER_ID_2, DEVICE_ID,
                params);

        verifyNoInteractions(mOverlayManagerMock);
    }

    @Test
    @EnableFlags(Flags.FLAG_MULTI_USER_VIEW_CONFIGURATION)
    public void applyViewConfigurationParamsForUser_enablesResourceOverlay_forGivenUser() {
        when(mUserManagerMock.getAliveUsers()).thenReturn(
                List.of(new UserInfo(USER_ID_1, "user1", 0)));
        ViewConfigurationParams params = createParamsRequiringResourceOverlay();
        mViewConfigurationController.applyViewConfigurationParams(DEVICE_ID, params);
        clearInvocations(mOverlayManagerMock);

        mViewConfigurationController.applyViewConfigurationParamsForUser(USER_ID_2, DEVICE_ID,
                params);

        verify(mOverlayManagerMock).commit(mTransactionArgumentCaptor.capture());
        OverlayManagerTransaction transaction = mTransactionArgumentCaptor.getValue();
        List<OverlayManagerTransaction.Request> requests = new ArrayList<>();
        transaction.getRequests().forEachRemaining(requests::add);
        assertThat(requests).hasSize(1);
        OverlayManagerTransaction.Request request = requests.get(0);
        assertEquals(OverlayManagerTransaction.Request.TYPE_SET_ENABLED, request.type);
        assertEquals(USER_ID_2, request.userId);
        List<OverlayConstraint> constraints = request.constraints;
        assertThat(constraints).hasSize(1);
        OverlayConstraint constraint = constraints.get(0);
        assertEquals(OverlayConstraint.TYPE_DEVICE_ID, constraint.getType());
        assertEquals(DEVICE_ID, constraint.getValue());
    }

    @Test
    @DisableFlags(Flags.FLAG_MULTI_USER_VIEW_CONFIGURATION)
    public void applyViewConfigurationParamsForUserStarting_doesNotWriteSettings_forGivenUser() {
        when(mUserManagerMock.getAliveUsers()).thenReturn(
                List.of(new UserInfo(USER_ID_1, "user1", 0)));
        ViewConfigurationParams params = createParamsRequiringSettingsOverride();
        mViewConfigurationController.applyViewConfigurationParams(DEVICE_ID, params);
        clearInvocations(mSettingsWriter);

        mViewConfigurationController.applyViewConfigurationParamsForUser(USER_ID_2, DEVICE_ID,
                params);

        verifyNoInteractions(mSettingsWriter);
    }

    @Test
    @EnableFlags(Flags.FLAG_MULTI_USER_VIEW_CONFIGURATION)
    public void applyViewConfigurationParamsForUserStarting_writesSettings_forGivenUser() {
        when(mUserManagerMock.getAliveUsers()).thenReturn(
                List.of(new UserInfo(USER_ID_1, "user1", 0)));
        ViewConfigurationParams params = createParamsRequiringSettingsOverride();
        mViewConfigurationController.applyViewConfigurationParams(DEVICE_ID, params);
        clearInvocations(mSettingsWriter);

        mViewConfigurationController.applyViewConfigurationParamsForUser(USER_ID_2, DEVICE_ID,
                params);

        verify(mSettingsWriter, times(2)).writeSettings(mSettingsKeyCaptor.capture(),
                anyInt(), eq(DEVICE_ID), eq(USER_ID_2));
        assertThat(mSettingsKeyCaptor.getAllValues()).containsExactly(
                Settings.Secure.LONG_PRESS_TIMEOUT, Settings.Secure.MULTI_PRESS_TIMEOUT);
    }

    @Test
    public void applyViewConfigurationParams_afterClose_doesNothing() {
        mViewConfigurationController.close();

        mViewConfigurationController.applyViewConfigurationParams(DEVICE_ID,
                createParamsRequiringResourcesAndSettings());

        verifyNoInteractions(mSettingsWriter);
        verifyNoInteractions(mOverlayManagerMock);
    }

    @Test
    @EnableFlags(Flags.FLAG_MULTI_USER_VIEW_CONFIGURATION)
    public void applyViewConfigurationParamsForUser_afterClose_doesNothing() {
        ViewConfigurationParams params = createParamsRequiringResourcesAndSettings();
        mViewConfigurationController.applyViewConfigurationParams(DEVICE_ID, params);
        mViewConfigurationController.close();
        clearInvocations(mSettingsWriter);
        clearInvocations(mOverlayManagerMock);

        mViewConfigurationController.applyViewConfigurationParamsForUser(USER_ID_2, DEVICE_ID,
                params);

        verifyNoInteractions(mSettingsWriter);
        verifyNoInteractions(mOverlayManagerMock);
    }

    @Test
    public void close_afterClose_doesNothing() {
        mViewConfigurationController.applyViewConfigurationParams(DEVICE_ID,
                createParamsRequiringResourceOverlay());
        mViewConfigurationController.close();
        clearInvocations(mOverlayManagerMock);

        mViewConfigurationController.close();

        verifyNoInteractions(mOverlayManagerMock);
        verifyNoInteractions(mSettingsWriter);
    }

    private static ViewConfigurationParams createParamsRequiringResourceOverlay() {
        return new ViewConfigurationParams.Builder()
                .setTapTimeoutDuration(Duration.ofMillis(10L))
                .setDoubleTapTimeoutDuration(Duration.ofMillis(10L))
                .setDoubleTapMinTimeDuration(Duration.ofMillis(10L))
                .setScrollFriction(10f)
                .setMinimumFlingVelocityPixelsPerSecond(10)
                .setMaximumFlingVelocityPixelsPerSecond(10)
                .setTouchSlopPixels(10)
                .build();
    }

    private static ViewConfigurationParams createParamsRequiringSettingsOverride() {
        return new ViewConfigurationParams.Builder()
                .setLongPressTimeoutDuration(Duration.ofMillis(10L))
                .setMultiPressTimeoutDuration(Duration.ofMillis(20L))
                .build();
    }

    private static ViewConfigurationParams createParamsRequiringResourcesAndSettings() {
        return new ViewConfigurationParams.Builder()
                .setLongPressTimeoutDuration(Duration.ofMillis(10L))
                .setDoubleTapTimeoutDuration(Duration.ofMillis(20L))
                .build();
    }
}
