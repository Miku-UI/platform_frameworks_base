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
package com.android.server.am;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;

import android.app.IServiceConnection;
import android.content.Context;
import android.platform.test.annotations.Presubmit;

import com.android.server.wm.ActivityServiceConnectionsHolder;

import org.junit.Test;

@Presubmit
public class ProcessStateControllerTest {

    private static ProcessStateController createProcessStateController() {
        final ActivityManagerService ams = mock(ActivityManagerService.class);
        ams.mAppProfiler = mock(AppProfiler.class);
        ams.mConstants = mock(ActivityManagerConstants.class);
        final ActiveUids au = new ActiveUids(null);
        final OomAdjuster.Callback callback = mock(OomAdjuster.Callback.class);
        return new ProcessStateController.Builder(ams, ams.mProcessList, au, callback).build();
    }

    private static ConnectionRecord createConnectionRecord(long flags) {
        final ProcessRecord pr = mock(ProcessRecord.class);
        final ServiceRecord sr = mock(ServiceRecord.class);
        final IntentBindRecord ibr = mock(IntentBindRecord.class);
        final AppBindRecord abr = new AppBindRecord(sr, ibr, pr, pr);
        return new ConnectionRecord(abr, mock(ActivityServiceConnectionsHolder.class),
                mock(IServiceConnection.class), flags,
                0, null, 0, null, null, null);

    }

    @Test
    public void bindAllowFreezeReturnsBoundServiceSession() {
        final ProcessStateController psc = createProcessStateController();
        final ConnectionRecord cr = createConnectionRecord(Context.BIND_ALLOW_FREEZE);
        assertThat(psc.getBoundServiceSessionFor(cr)).isNotNull();
    }

    @Test
    public void bindSimulateAllowFreezeReturnsBoundServiceSession() {
        final ProcessStateController psc = createProcessStateController();
        final ConnectionRecord cr = createConnectionRecord(Context.BIND_SIMULATE_ALLOW_FREEZE);
        assertThat(psc.getBoundServiceSessionFor(cr)).isNotNull();
    }

    @Test
    public void noAllowFreezeReturnsNullBoundServiceSession() {
        final ProcessStateController psc = createProcessStateController();
        final ConnectionRecord cr = createConnectionRecord(0);
        assertThat(psc.getBoundServiceSessionFor(cr)).isNull();
    }
}
