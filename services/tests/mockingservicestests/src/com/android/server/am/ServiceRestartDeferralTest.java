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

package com.android.server.am;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.IApplicationThread;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.ArrayMap;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.server.am.psc.ProcessStateController;
import com.android.server.am.psc.ServiceRecordInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ServiceRestartDeferralTest {
    private static final String PACKAGE_NAME = "com.android.test";
    private static final int TEST_UID = 10001;
    private static final int TEST_USER_ID = 0;
    private static final String TEST_SERVICE = "TestService";

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private MockitoSession mMockingSession;
    private ActivityManagerService mAms;
    private ActiveServices mActiveServices;

    @Before
    public void setUp() {
        mMockingSession = mockitoSession()
                .initMocks(this)
                .strictness(Strictness.LENIENT)
                .startMocking();

        mAms = mock(ActivityManagerService.class);
        mAms.mProcessStateController = mock(ProcessStateController.class);
        final Context mockContext = mock(Context.class);
        final Resources mockResources = initMockResources();
        doReturn(mockResources).when(mockContext).getResources();

        final ActivityManagerConstants constants = new ActivityManagerConstants(
                mockContext, mAms, mock(Handler.class));
        setFieldValue(ActivityManagerService.class, mAms, "mConstants", constants);
        setFieldValue(ActivityManagerService.class, mAms, "mProcLock",
                mock(ActivityManagerGlobalLock.class));

        mActiveServices = spy(new ActiveServices(mAms));
        setFieldValue(ActiveServices.class, mActiveServices, "mAm", mAms);
    }

    @After
    public void tearDown() {
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
    }

    @NonNull
    private Resources initMockResources() {
        final Resources mockResources = mock(Resources.class);
        when(mockResources.getBoolean(anyInt())).thenReturn(false);
        when(mockResources.getInteger(anyInt())).thenReturn(0);
        doReturn(new String[0]).when(mockResources).getStringArray(anyInt());
        doReturn(new int[0]).when(mockResources).getIntArray(anyInt());
        doReturn(0f).when(mockResources).getFloat(anyInt());
        return mockResources;
    }

    @Test
    public void testIsNeededLocked_notNeeded() {
        final ServiceRecord r = createMockServiceRecord();
        doCallRealMethod().when(r).getNeededReasonsLocked(anyBoolean(), anyBoolean());
        doCallRealMethod().when(r).isNeededLocked(anyBoolean(), anyBoolean());

        doReturn(false).when(r).isStartRequested();
        doReturn(false).when(r).hasAutoCreateConnections();
        assertEquals(ServiceRecord.NEEDED_NONE, r.getNeededReasonsLocked(false, false));
        assertThat(r.isNeededLocked(false, false)).isFalse();
    }

    @Test
    public void testIsNeededLocked_neededByStart() {
        final ServiceRecord r = createMockServiceRecord();
        doCallRealMethod().when(r).getNeededReasonsLocked(anyBoolean(), anyBoolean());
        doCallRealMethod().when(r).isNeededLocked(anyBoolean(), anyBoolean());

        doReturn(true).when(r).isStartRequested();
        doReturn(false).when(r).hasAutoCreateConnections();
        assertEquals(ServiceRecord.NEEDED_BY_START, r.getNeededReasonsLocked(false, false));
        assertThat(r.isNeededLocked(false, false)).isTrue();
    }

    @Test
    public void testIsNeededLocked_neededByAutoCreate() {
        final ServiceRecord r = createMockServiceRecord();
        doCallRealMethod().when(r).getNeededReasonsLocked(anyBoolean(), anyBoolean());
        doCallRealMethod().when(r).isNeededLocked(anyBoolean(), anyBoolean());

        doReturn(false).when(r).isStartRequested();
        doReturn(true).when(r).hasAutoCreateConnections();
        assertEquals(ServiceRecord.NEEDED_BY_AUTO_CREATE, r.getNeededReasonsLocked(false, false));
        assertThat(r.isNeededLocked(false, false)).isTrue();
    }

    @Test
    public void testIsNeededLocked_neededByStartAndAutoCreate() {
        final ServiceRecord r = createMockServiceRecord();
        doCallRealMethod().when(r).getNeededReasonsLocked(anyBoolean(), anyBoolean());
        doCallRealMethod().when(r).isNeededLocked(anyBoolean(), anyBoolean());

        doReturn(true).when(r).isStartRequested();
        doReturn(true).when(r).hasAutoCreateConnections();
        assertEquals(ServiceRecord.NEEDED_BY_START | ServiceRecord.NEEDED_BY_AUTO_CREATE,
                r.getNeededReasonsLocked(false, false));
        assertThat(r.isNeededLocked(false, false)).isTrue();
    }

    @Test
    public void testHasNonFrozenAutoCreateConnections() {
        final ServiceRecord r = createMockServiceRecord();
        doCallRealMethod().when(r).hasNonFrozenAutoCreateConnections();

        final ArrayMap<IBinder, ArrayList<ConnectionRecord>> connections = new ArrayMap<>();
        doReturn(connections).when(r).getConnections();

        final ArrayList<ConnectionRecord> clist = new ArrayList<>();
        connections.put(mock(IBinder.class), clist);

        final ProcessRecord clientProc = initMockProcessRecord();
        final ConnectionRecord cr = initMockConnectionRecord(clientProc);
        clist.add(cr);

        // Client is not frozen
        doReturn(false).when(clientProc).isFrozen();
        assertThat(r.hasNonFrozenAutoCreateConnections()).isTrue();

        // Client is frozen
        doReturn(true).when(clientProc).isFrozen();
        assertThat(r.hasNonFrozenAutoCreateConnections()).isFalse();

        final ProcessRecord clientProc2 = initMockProcessRecord();
        final ConnectionRecord cr2 = initMockConnectionRecord(clientProc2);
        clist.add(cr2);

        // Multiple clients, one is not frozen
        doReturn(true).when(clientProc).isFrozen();
        doReturn(false).when(clientProc2).isFrozen();
        assertThat(r.hasNonFrozenAutoCreateConnections()).isTrue();

        // Multiple clients, both are frozen
        doReturn(true).when(clientProc).isFrozen();
        doReturn(true).when(clientProc2).isFrozen();
        assertThat(r.hasNonFrozenAutoCreateConnections()).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_DEFER_SERVICE_RESTART_WHEN_FROZEN)
    public void testPerformServiceRestartLocked_defersWithFrozenClient() throws Exception {
        final ServiceRecord r = createMockServiceRecord();
        doCallRealMethod().when(r).getNeededReasonsLocked(anyBoolean(), anyBoolean());
        doCallRealMethod().when(r).isNeededLocked(anyBoolean(), anyBoolean());
        doCallRealMethod().when(r).isStartRequested();

        final ArrayList<ServiceRecord> restartingServices = new ArrayList<>();
        restartingServices.add(r);
        setFieldValue(ActiveServices.class, mActiveServices, "mRestartingServices",
                restartingServices);

        // Needed by only auto-create connection
        doReturn(false).when(r).isStartRequested();
        doReturn(true).when(r).hasAutoCreateConnections();
        // Client is frozen
        doReturn(false).when(r).hasNonFrozenAutoCreateConnections();

        mActiveServices.performServiceRestartLocked(r);

        // verify bringUpServiceLocked was not called
        verify(mActiveServices, never()).bringUpServiceLocked(any(), anyInt(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyInt());
    }

    @Test
    @DisableFlags(Flags.FLAG_DEFER_SERVICE_RESTART_WHEN_FROZEN)
    public void testPerformServiceRestartLocked_doesNotDeferWithFrozenClient_flagDisabled()
            throws Exception {
        final ServiceRecord r = createMockServiceRecord();
        doCallRealMethod().when(r).getNeededReasonsLocked(anyBoolean(), anyBoolean());
        doCallRealMethod().when(r).isNeededLocked(anyBoolean(), anyBoolean());
        doCallRealMethod().when(r).isStartRequested();

        final ArrayList<ServiceRecord> restartingServices = new ArrayList<>();
        restartingServices.add(r);
        setFieldValue(ActiveServices.class, mActiveServices, "mRestartingServices",
                restartingServices);

        // Needed by only auto-create connection
        doReturn(false).when(r).isStartRequested();
        doReturn(true).when(r).hasAutoCreateConnections();
        // Client is frozen
        doReturn(false).when(r).hasNonFrozenAutoCreateConnections();

        // We need to mock bringUpServiceLocked
        doReturn(null).when(mActiveServices).bringUpServiceLocked(any(), anyInt(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyInt());

        mActiveServices.performServiceRestartLocked(r);

        // verify bringUpServiceLocked was called because the flag is disabled
        verify(mActiveServices).bringUpServiceLocked(any(), anyInt(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyInt());
    }

    @Test
    @EnableFlags(Flags.FLAG_DEFER_SERVICE_RESTART_WHEN_FROZEN)
    public void testPerformServiceRestartLocked_doesNotDeferWithUnFrozenClient() throws Exception {
        final ServiceRecord r = createMockServiceRecord();
        doCallRealMethod().when(r).getNeededReasonsLocked(anyBoolean(), anyBoolean());
        doCallRealMethod().when(r).isNeededLocked(anyBoolean(), anyBoolean());
        doCallRealMethod().when(r).isStartRequested();

        final ArrayList<ServiceRecord> restartingServices = new ArrayList<>();
        restartingServices.add(r);
        setFieldValue(ActiveServices.class, mActiveServices, "mRestartingServices",
                restartingServices);

        // Needed by only auto-create connection
        doReturn(false).when(r).isStartRequested();
        doReturn(true).when(r).hasAutoCreateConnections();
        // Client is not frozen
        doReturn(true).when(r).hasNonFrozenAutoCreateConnections();

        // We need to mock bringUpServiceLocked because it does a lot of things
        doReturn(null).when(mActiveServices).bringUpServiceLocked(any(), anyInt(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyInt());

        mActiveServices.performServiceRestartLocked(r);

        // verify bringUpServiceLocked was called because it's needed by auto-create connection and
        // client isss not frozen
        verify(mActiveServices).bringUpServiceLocked(any(), anyInt(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyInt());
    }

    @Test
    @EnableFlags(Flags.FLAG_DEFER_SERVICE_RESTART_WHEN_FROZEN)
    public void testPerformServiceRestartLocked_doesNotDeferWhenStarted() throws Exception {
        final ServiceRecord r = createMockServiceRecord();
        doCallRealMethod().when(r).getNeededReasonsLocked(anyBoolean(), anyBoolean());
        doCallRealMethod().when(r).isNeededLocked(anyBoolean(), anyBoolean());
        doCallRealMethod().when(r).isStartRequested();

        final ArrayList<ServiceRecord> restartingServices = new ArrayList<>();
        restartingServices.add(r);
        setFieldValue(ActiveServices.class, mActiveServices, "mRestartingServices",
                restartingServices);

        // Needed by both start and auto-create connection
        doReturn(true).when(r).isStartRequested();
        doReturn(true).when(r).hasAutoCreateConnections();
        doReturn(false).when(r).hasNonFrozenAutoCreateConnections();

        // We need to mock bringUpServiceLocked because it does a lot of things
        doReturn(null).when(mActiveServices).bringUpServiceLocked(any(), anyInt(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyInt());

        mActiveServices.performServiceRestartLocked(r);

        // verify bringUpServiceLocked was called because it's needed by start
        verify(mActiveServices).bringUpServiceLocked(any(), anyInt(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyInt());
    }

    @Test
    @EnableFlags(Flags.FLAG_DEFER_SERVICE_RESTART_WHEN_FROZEN)
    public void testOnProcessUnfrozenLocked() {
        final ArrayList<ServiceRecord> restartingServices = new ArrayList<>();
        setFieldValue(ActiveServices.class, mActiveServices, "mRestartingServices",
                restartingServices);

        final ServiceRecord sr = createMockServiceRecord();
        final ConnectionRecord cr = initMockConnectionRecord(sr);
        restartingServices.add(sr);

        final ServiceRecord sr2 = createMockServiceRecord();
        final ConnectionRecord cr2 = initMockConnectionRecord(sr2);

        final ProcessServiceRecord psr = initMockProcessServiceRecord(cr, cr2);
        // Mock performServiceRestartLocked to see if it's called
        doNothing().when(mActiveServices).performServiceRestartLocked(sr);
        doNothing().when(mActiveServices).performServiceRestartLocked(sr2);

        mActiveServices.onProcessUnfrozenLocked(psr);

        verify(mActiveServices, times(1)).performServiceRestartLocked(sr);
        verify(mActiveServices, never()).performServiceRestartLocked(sr2);
    }

    @Test
    @DisableFlags(Flags.FLAG_DEFER_SERVICE_RESTART_WHEN_FROZEN)
    public void testOnProcessUnfrozenLocked_flagDisabled() {
        final ArrayList<ServiceRecord> restartingServices = new ArrayList<>();
        setFieldValue(ActiveServices.class, mActiveServices, "mRestartingServices",
                restartingServices);

        final ServiceRecord sr = createMockServiceRecord();
        final ConnectionRecord cr = initMockConnectionRecord(sr);
        restartingServices.add(sr);

        final ProcessServiceRecord psr = initMockProcessServiceRecord(cr);

        mActiveServices.onProcessUnfrozenLocked(psr);

        // Should return early and not call performServiceRestartLocked
        verify(mActiveServices, never()).performServiceRestartLocked(any());
    }

    @Test
    @EnableFlags(Flags.FLAG_DEFER_SERVICE_RESTART_WHEN_FROZEN)
    public void testOnProcessUnfrozenLocked_respectsNextRestartTime() {
        final ArrayList<ServiceRecord> restartingServices = new ArrayList<>();
        setFieldValue(ActiveServices.class, mActiveServices, "mRestartingServices",
                restartingServices);

        final long now = SystemClock.uptimeMillis();
        final ServiceRecord sr = createMockServiceRecord();
        final ConnectionRecord cr = initMockConnectionRecord(sr);
        restartingServices.add(sr);

        final ServiceRecord sr2 = createMockServiceRecord();
        final ConnectionRecord cr2 = initMockConnectionRecord(sr2);
        restartingServices.add(sr2);

        // nextRestartTime has passed
        sr.nextRestartTime = now - 1000;
        // nextRestartTime is in the future
        sr2.nextRestartTime = now + 1000;

        final ProcessServiceRecord psr = initMockProcessServiceRecord(cr, cr2);
        doNothing().when(mActiveServices).performServiceRestartLocked(any());

        mActiveServices.onProcessUnfrozenLocked(psr);

        // sr should be restarted, as nextRestartTime has passed
        verify(mActiveServices, times(1)).performServiceRestartLocked(sr);
        // sr2 should not be restarted, as nextRestartTime is in the future
        verify(mActiveServices, never()).performServiceRestartLocked(sr2);
    }

    @NonNull
    private ProcessServiceRecord initMockProcessServiceRecord(ConnectionRecord... connections) {
        final ProcessServiceRecord psr = mock(ProcessServiceRecord.class);
        doReturn(connections.length).when(psr).numberOfConnections();
        for (int i = 0; i < connections.length; i++) {
            doReturn(connections[i]).when(psr).getConnectionAt(i);
        }
        return psr;
    }

    @NonNull
    private ConnectionRecord initMockConnectionRecord(@NonNull ServiceRecord record) {
        final ConnectionRecord cr = mock(ConnectionRecord.class);
        doReturn(true).when(cr).hasFlag(Context.BIND_AUTO_CREATE);
        doReturn(record).when(cr).getService();
        return cr;
    }

    @NonNull
    private ProcessRecord initMockProcessRecord() {
        final ProcessRecord record = mock(ProcessRecord.class);
        doReturn(mock(IApplicationThread.class)).when(record).getThread();
        return record;
    }

    @NonNull
    private ConnectionRecord initMockConnectionRecord(@NonNull ProcessRecord record) {
        final ConnectionRecord cr = mock(ConnectionRecord.class);
        doReturn(true).when(cr).hasFlag(Context.BIND_AUTO_CREATE);
        doReturn(record).when(cr).getClient();
        return cr;
    }

    @NonNull
    private ServiceRecord createMockServiceRecord() {
        final ServiceRecord r = mock(ServiceRecord.class);
        r.appInfo = new ApplicationInfo();
        r.appInfo.uid = TEST_UID;
        final ServiceInfo si = new ServiceInfo();
        si.applicationInfo = r.appInfo;
        setFieldValue(ServiceRecord.class, r, "serviceInfo", si);
        setFieldValue(ServiceRecord.class, r, "packageName", PACKAGE_NAME);
        setFieldValue(ServiceRecord.class, r, "intent", new Intent.FilterComparison(new Intent()));
        setFieldValue(ServiceRecord.class, r, "ams", mAms);
        setFieldValue(ServiceRecord.class, r, "userId", TEST_USER_ID);
        setFieldValue(ServiceRecordInternal.class, r, "instanceName",
                new ComponentName(PACKAGE_NAME, TEST_SERVICE));
        return r;
    }

    /**
     * Sets the value of a field in an object using reflection. This is useful for setting private
     * or final fields in tests.
     *
     * @param clazz The class of the object.
     * @param obj The object whose field to set.
     * @param fieldName The name of the field to set.
     * @param val The new value for the field.
     * @param <T> The type of the field value.
     * @throws RuntimeException if the field does not exist or cannot be accessed.
     */
    private static <T> void setFieldValue(@NonNull Class<?> clazz, @NonNull Object obj,
            @NonNull String fieldName, T val) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            // Remove the 'final' modifier to allow re-assignment.
            Field mfield = Field.class.getDeclaredField("accessFlags");
            mfield.setAccessible(true);
            mfield.setInt(field, mfield.getInt(field) & ~(Modifier.FINAL | Modifier.PRIVATE));
            field.set(obj, val);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
