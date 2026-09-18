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

package com.android.server.personalcontext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import static java.util.Collections.emptyList;

import android.service.personalcontext.RenderToken;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.ContextHintTestUtils;
import android.service.personalcontext.hint.PublishedContextHint;
import android.service.personalcontext.insight.BundleInsight;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.PublishedContextInsight;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.server.personalcontext.component.Renderer;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

import javax.crypto.spec.SecretKeySpec;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RendererWorkflowTest {
    private static final ScheduledExecutorService INLINE_EXECUTOR;

    static {
        INLINE_EXECUTOR = mock(ScheduledExecutorService.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(INLINE_EXECUTOR).execute(any());
    }

    private static PublishedContextInsight buildPublishedInsight(List<RenderToken> renderTokens,
            SecretKeySpec key, UUID componentid) throws GeneralSecurityException {
        return new PublishedContextInsight(buildInsight(renderTokens, key), componentid);
    }

    private static ContextInsight buildInsight(List<RenderToken> renderTokens, SecretKeySpec key)
            throws GeneralSecurityException {
        return new BundleInsight.Builder()
                .addOriginHint(
                        new PublishedContextHint.Builder(new BundleHint.Builder().build(), key)
                                .addRenderTokens(renderTokens)
                                .build())
                .build();
    }

    @Test
    public void testWorkflowWithNoRenderTokenNoRenderers() throws GeneralSecurityException {
        final UUID understanderComponentId = UUID.randomUUID();
        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final RendererWorkflow.EventListener listener = mock(RendererWorkflow.EventListener.class);
        final RendererWorkflow.ComponentProvider provider =
                mock(RendererWorkflow.ComponentProvider.class);
        final PublishedContextInsight insight = buildPublishedInsight(emptyList(), key,
                understanderComponentId);

        doReturn(Collections.emptySet()).when(provider).getRenderers();

        RendererWorkflow.start(
                provider,
                insight,
                key,
                listener,
                INLINE_EXECUTOR);

        verify(listener).onRendererWorkflowStarted(anyLong(), eq(insight));
        verify(listener).onRendererWorkflowFinished(anyLong());
        verify(listener, never()).onRendererWorkflowError(anyLong(), any());
        verify(provider, never()).getRendererById(any());
    }

    @Test
    public void testWorkflowWithRenderTokenNoRenderer() throws GeneralSecurityException {
        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final RendererWorkflow.EventListener listener = mock(RendererWorkflow.EventListener.class);
        final UUID componentId = UUID.randomUUID();
        final RendererWorkflow.ComponentProvider provider =
                mock(RendererWorkflow.ComponentProvider.class);
        final PublishedContextInsight publishedInsight = buildPublishedInsight(
                List.of(new RenderToken(UUID.randomUUID(), null)),
                key, componentId);

        doReturn(null).when(provider).getRendererById(any());

        RendererWorkflow.start(
                provider,
                publishedInsight,
                key,
                listener,
                INLINE_EXECUTOR);

        verify(listener).onRendererWorkflowStarted(anyLong(), eq(publishedInsight));
        verify(listener).onRendererWorkflowError(anyLong(), any());
        verify(listener).onRendererWorkflowFinished(anyLong());
        verify(provider, never()).getRenderers();
    }

    @Test
    public void testWorkflowWithRenderTokenAndRenderer() throws GeneralSecurityException {
        final UUID understanderComponentId = UUID.randomUUID();
        final UUID componentId = UUID.randomUUID();
        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final Renderer renderer = mock(Renderer.class);
        final RenderToken renderToken = new RenderToken(componentId, null);
        final RendererWorkflow.EventListener listener = mock(RendererWorkflow.EventListener.class);
        final RendererWorkflow.ComponentProvider provider =
                mock(RendererWorkflow.ComponentProvider.class);
        final PublishedContextInsight publishedContextInsight = buildPublishedInsight(
                List.of(renderToken),
                key, understanderComponentId);

        doReturn(componentId).when(renderer).getComponentId();
        doReturn(renderer).when(provider).getRendererById(eq(componentId));

        RendererWorkflow.start(
                provider,
                publishedContextInsight,
                key,
                listener,
                INLINE_EXECUTOR);

        verify(listener).onRendererWorkflowStarted(anyLong(), eq(publishedContextInsight));
        verify(listener).onInsightSentToRenderer(anyLong(), eq(publishedContextInsight),
                eq(renderer));
        verify(listener).onRendererWorkflowFinished(anyLong());
        verify(listener, never()).onRendererWorkflowError(anyLong(), any());
        verify(renderer).render(eq(publishedContextInsight), eq(renderToken));
        verify(renderer, never()).isInterestedInInsight(any());
        verify(provider, never()).getRenderers();
    }

    @Test
    public void testWorkflowWithNoRenderTokenAndRenderers() throws GeneralSecurityException {
        final UUID understanderComponentId = UUID.randomUUID();
        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final Renderer renderer1 = mock(Renderer.class);
        final Renderer renderer2 = mock(Renderer.class);
        final Renderer renderer3 = mock(Renderer.class);
        final RendererWorkflow.EventListener listener = mock(RendererWorkflow.EventListener.class);
        final RendererWorkflow.ComponentProvider provider =
                mock(RendererWorkflow.ComponentProvider.class);
        final PublishedContextInsight publishedContextInsight =
                buildPublishedInsight(emptyList(), key, understanderComponentId);

        doReturn(true).when(renderer1).isInterestedInInsight(any());
        doReturn(true).when(renderer2).isInterestedInInsight(any());
        doReturn(false).when(renderer3).isInterestedInInsight(any());

        doReturn(List.of(renderer1, renderer2, renderer3)).when(provider).getRenderers();

        RendererWorkflow.start(
                provider,
                publishedContextInsight,
                key,
                listener,
                INLINE_EXECUTOR);

        verify(listener).onRendererWorkflowFinished(anyLong());
        verify(listener).onRendererWorkflowFinished(anyLong());
        verify(listener, never()).onRendererWorkflowError(anyLong(), any());
        verify(renderer1).render(eq(publishedContextInsight), isNull());
        verify(renderer2).render(eq(publishedContextInsight), isNull());
        verify(renderer3, never()).render(eq(publishedContextInsight), isNull());
        verify(provider, never()).getRendererById(any());
    }

    @Test
    public void testWorkflowWithInvalidHintSignature() throws GeneralSecurityException {
        final UUID understanderComponentId = UUID.randomUUID();
        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final RendererWorkflow.EventListener listener = mock(RendererWorkflow.EventListener.class);
        final RendererWorkflow.ComponentProvider provider =
                mock(RendererWorkflow.ComponentProvider.class);
        final PublishedContextInsight publishedInsight = buildPublishedInsight(emptyList(), key,
                understanderComponentId);

        RendererWorkflow.start(
                provider,
                publishedInsight,
                ContextHintTestUtils.generateSignedHintKey(),
                listener,
                INLINE_EXECUTOR);

        verify(listener).onRendererWorkflowStarted(anyLong(), eq(publishedInsight));
        verify(listener).onRendererWorkflowError(anyLong(), any());
        verify(listener, never()).onRendererWorkflowFinished(anyLong());
        verify(provider, never()).getRendererById(any());
        verify(provider, never()).getRenderers();
    }

    @Test
    public void testWorkflowWithConflictingRenderTokens() throws GeneralSecurityException {
        final UUID understanderComponentId = UUID.randomUUID();
        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final RendererWorkflow.EventListener listener = mock(RendererWorkflow.EventListener.class);
        final RendererWorkflow.ComponentProvider provider =
                mock(RendererWorkflow.ComponentProvider.class);
        final ContextInsight insight = new BundleInsight.Builder()
                .addOriginHint(
                        new PublishedContextHint.Builder(new BundleHint.Builder().build(), key)
                                .addRenderTokens(List.of(new RenderToken(UUID.randomUUID(), null)))
                                .build())
                .addOriginHint(
                        new PublishedContextHint.Builder(new BundleHint.Builder().build(), key)
                                .addRenderTokens(List.of(new RenderToken(UUID.randomUUID(), null)))
                                .build())
                .build();
        final PublishedContextInsight publishedInsight = new PublishedContextInsight(insight,
                understanderComponentId);

        RendererWorkflow.start(
                provider,
                publishedInsight,
                ContextHintTestUtils.generateSignedHintKey(),
                listener,
                INLINE_EXECUTOR);

        verify(listener).onRendererWorkflowStarted(anyLong(), eq(publishedInsight));
        verify(listener).onRendererWorkflowError(anyLong(), any());
        verify(listener, never()).onRendererWorkflowFinished(anyLong());
        verify(provider, never()).getRendererById(any());
        verify(provider, never()).getRenderers();
    }
}
