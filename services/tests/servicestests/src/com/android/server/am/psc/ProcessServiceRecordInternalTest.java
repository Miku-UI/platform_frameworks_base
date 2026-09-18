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

package com.android.server.am.psc;

import static android.content.Context.BIND_ABOVE_CLIENT;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.platform.test.annotations.Presubmit;

import org.junit.Before;
import org.junit.Test;

@Presubmit
public class ProcessServiceRecordInternalTest {

    private ProcessServiceRecordInternal mPsr;

    @Before
    public void setUp() {
        mPsr = new ProcessServiceRecordInternal(new OomAdjuster.Constants(),
                mock(ProcessServiceRecordInternal.Observer.class));
    }

    @Test
    public void isBindingToSelf_sameProcessInstance_returnsTrue() {
        ConnectionRecordInternal connection = createMockConnectionRecord();
        ProcessRecordInternal proc = mock(ProcessRecordInternal.class);

        when(connection.getClient()).thenReturn(proc);
        when(connection.getService()).thenReturn(mock(ServiceRecordInternal.class));
        when(connection.getService().getHostProcessInternal()).thenReturn(proc);

        assertTrue("Should be true when host == getClient", connection.isBindingToSelf());
    }

    @Test
    public void isBindingToSelf_differentProcessInstance_returnsFalse() {
        ConnectionRecordInternal connection = createMockConnectionRecord();

        when(connection.getClient()).thenReturn(mock(ProcessRecordInternal.class));
        when(connection.getService()).thenReturn(mock(ServiceRecordInternal.class));
        when(connection.getService().getHostProcessInternal()).thenReturn(
                mock(ProcessRecordInternal.class));

        assertFalse("Should be false when host != getClient", connection.isBindingToSelf());
    }

    @Test
    public void bindAboveClient_lifecycle_isAccurate() {
        ConnectionRecordInternal normalConn = createMockConnection(
                /* isSelf= */ false, /* bindAboveClient= */ false);
        ConnectionRecordInternal aboveConn = createMockConnection(
                /* isSelf= */ false, /* bindAboveClient= */ true);

        // 1. Initially false
        assertFalse(mPsr.hasBindAboveClient());

        // 2. addConnection (without bind above flag connection) -> remains false
        mPsr.addConnection(normalConn);
        assertFalse(mPsr.hasBindAboveClient());

        // 3. addConnection (with bind above flag connection) -> true
        mPsr.addConnection(aboveConn);
        assertTrue(mPsr.hasBindAboveClient());

        // 4. removeConnection (without bind above flag) -> remains true
        mPsr.removeConnection(normalConn);
        assertTrue("Flag should remain true while bindAboveClient connection is active",
                mPsr.hasBindAboveClient());

        // 5. another removeConnection (with bind above flag) -> false
        mPsr.removeConnection(aboveConn);
        assertFalse(mPsr.hasBindAboveClient());
    }

    @Test
    public void bindAboveClient_selfBindingAbove_remainsFalse() {
        ConnectionRecordInternal selfAboveConn = createMockConnection(
                /* isSelf= */ true, /* bindAboveClient= */ true);

        mPsr.addConnection(selfAboveConn);

        assertFalse("Should be false for a self-binding even with BIND_ABOVE_CLIENT",
                mPsr.hasBindAboveClient());
    }

    private ConnectionRecordInternal createMockConnectionRecord() {
        return mock(ConnectionRecordInternal.class,
                withSettings().defaultAnswer(CALLS_REAL_METHODS));
    }

    private ConnectionRecordInternal createMockConnection(boolean isSelf, boolean bindAboveClient) {
        ConnectionRecordInternal conn = createMockConnectionRecord();
        ProcessRecordInternal client = mock(ProcessRecordInternal.class);
        ProcessRecordInternal host = isSelf ? client : mock(ProcessRecordInternal.class);
        ServiceRecordInternal service = mock(ServiceRecordInternal.class);

        when(conn.getClient()).thenReturn(client);
        when(conn.getService()).thenReturn(service);
        when(service.getHostProcessInternal()).thenReturn(host);
        when(conn.hasFlag(BIND_ABOVE_CLIENT)).thenReturn(bindAboveClient);
        when(conn.isBindingToSelf()).thenAnswer(inv ->
                conn.getClient() == conn.getService().getHostProcessInternal());

        return conn;
    }
}
