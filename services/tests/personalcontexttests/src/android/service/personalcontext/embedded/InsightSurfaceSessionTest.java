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

package android.service.personalcontext.embedded;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.RemoteException;
import android.service.personalcontext.PersonalContextManager;
import android.view.SurfaceControlViewHost;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InsightSurfaceSessionTest {
    private InsightSurfaceSession mSession;
    @Mock
    private Context mContext;
    @Mock
    private PersonalContextManager mPersonalContextManager;
    @Mock
    private IInsightSurfaceSession mSessionInterface;
    @Mock
    private InsightSurfaceClient mClient;
    @Mock
    private SurfaceControlViewHost.SurfacePackage mSurfacePackage;
    @Mock
    private InsightSurfaceClientUpdate mUpdate;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        mSession = new InsightSurfaceSession(mContext, mClient, mSurfacePackage, mSessionInterface);
        when(mContext.getSystemService(PersonalContextManager.class))
                .thenReturn(mPersonalContextManager);
    }

    @Test
    public void testGetClientTest() {
        assertThat(mSession.getClient()).isEqualTo(mClient);
    }

    @Test
    public void testGetSurfacePackage() {
        assertThat(mSession.getSurfacePackage()).isEqualTo(mSurfacePackage);
    }

    @Test
    public void testOnClientUpdated() throws RemoteException {
        final InsightSurfaceClientInfo oldClientInfo = mock(InsightSurfaceClientInfo.class);
        final InsightSurfaceClientInfo newClientInfo = mock(InsightSurfaceClientInfo.class);
        when(mClient.updateClientInfo(mUpdate)).thenReturn(oldClientInfo);
        when(mClient.getClientInfo()).thenReturn(newClientInfo);

        mSession.update(mUpdate, null);
        verify(mSessionInterface).onClientUpdated(eq(oldClientInfo), eq(newClientInfo), isNull(),
                any());
        verify(mPersonalContextManager).updateEmbeddedClientInfo(oldClientInfo, newClientInfo);
    }
}
