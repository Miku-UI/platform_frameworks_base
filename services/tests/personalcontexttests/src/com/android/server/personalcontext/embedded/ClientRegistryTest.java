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

package com.android.server.personalcontext.embedded;

import static com.google.common.truth.Truth.assertThat;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.embedded.IInsightSurfaceClient;
import android.service.personalcontext.embedded.InsightSurfaceClientInfo;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ClientRegistryTest {
    @Mock
    private IInsightSurfaceClient mClient;

    private ClientRegistry mClientRegistry;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mClientRegistry = new ClientRegistry();
    }

    @Test
    public void testAddClient() {
        final RenderToken renderToken = new RenderToken(UUID.randomUUID(), null);
        final InsightSurfaceClientInfo client = createClient();
        mClientRegistry.addClient(client, renderToken);
        assertThat(mClientRegistry.getClients().size()).isEqualTo(1);
        assertThat(mClientRegistry.getClients().get(0)).isEqualTo(client);
    }

    @Test
    public void testRemoveClient() {
        final RenderToken renderToken = new RenderToken(UUID.randomUUID(), null);
        final InsightSurfaceClientInfo client = createClient();
        mClientRegistry.addClient(client, renderToken);
        mClientRegistry.removeClient(client.getId());
        assertThat(mClientRegistry.getClients().size()).isEqualTo(0);
    }

    @Test
    public void testClientForTokenHint_hasClient() {
        final RenderToken renderToken1 = new RenderToken(UUID.randomUUID(), null);
        final RenderToken renderToken2 = new RenderToken(UUID.randomUUID(), null);

        final InsightSurfaceClientInfo client = createClient();
        mClientRegistry.addClient(client, renderToken1);
        assertThat(mClientRegistry.getClientForRenderToken(renderToken1)).isEqualTo(client);
        assertThat(mClientRegistry.getClientForRenderToken(renderToken2)).isNull();
    }

    @Test
    public void testGetClientsContainsClient() {
        final RenderToken renderToken = new RenderToken(UUID.randomUUID(), null);
        final InsightSurfaceClientInfo client = createClient();
        mClientRegistry.addClient(client, renderToken);
        assertThat(mClientRegistry.getClients()).containsExactly(client);
    }

    private InsightSurfaceClientInfo createClient() {
        return new InsightSurfaceClientInfo(
                UUID.randomUUID(),
                1,
                2,
                3,
                Color.valueOf(Color.RED),
                View.SCROLL_AXIS_NONE,
                false,
                false,
                Resources.ID_NULL,
                "package.name",
                new Configuration(),
                mClient);
    }
}
