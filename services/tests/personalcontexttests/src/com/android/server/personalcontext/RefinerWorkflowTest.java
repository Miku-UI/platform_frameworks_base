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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import static java.util.Collections.emptySet;

import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintTestUtils;
import android.service.personalcontext.hint.PublishedContextHint;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.server.personalcontext.component.Refiner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import javax.crypto.spec.SecretKeySpec;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RefinerWorkflowTest {
    private static final ScheduledExecutorService INLINE_EXECUTOR;

    static {
        INLINE_EXECUTOR = mock(ScheduledExecutorService.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(INLINE_EXECUTOR).execute(any());
    }

    private static Refiner buildMockSeriesRefiner() {
        Refiner refiner = mock(Refiner.class);

        doAnswer(invocation -> {
            final Set<PublishedContextHint> hints = invocation.getArgument(0, Set.class);
            final Set<UUID> seenIds = invocation.getArgument(1, Set.class);
            final Set<Set<PublishedContextHint>> result = new HashSet<>();
            for (PublishedContextHint hint : hints) {
                if (!seenIds.contains(hint.getContextHint().getHintId())) {
                    result.add(Set.of(hint));
                }
            }
            return result;
        })
                .when(refiner).getInterestedHintClusters(any(), any(), anyBoolean());

        return refiner;
    }

    private static Refiner buildMockParallelRefiner() {
        Refiner refiner = mock(Refiner.class);

        doAnswer(invocation -> {
            final Set<PublishedContextHint> hints = invocation.getArgument(0, Set.class);
            final Set<UUID> seenIds = invocation.getArgument(1, Set.class);
            final Set<PublishedContextHint> result = new HashSet<>();
            for (PublishedContextHint hint : hints) {
                if (!seenIds.contains(hint.getContextHint().getHintId())) {
                    result.add(hint);
                }
            }
            if (result.isEmpty()) {
                return emptySet();
            } else {
                return Set.of(result);
            }
        })
                .when(refiner).getInterestedHintClusters(any(), any(), anyBoolean());

        return refiner;
    }

    @Test
    public void testWorkflowWithNoRefiners() throws GeneralSecurityException {
        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final RefinerWorkflow.EventListener listener = mock(RefinerWorkflow.EventListener.class);
        final RefinerWorkflow.ComponentProvider provider =
                mock(RefinerWorkflow.ComponentProvider.class);

        doReturn(emptySet()).when(provider).getRefiners();

        RefinerWorkflow.start(
                provider,
                Set.of(
                        new PublishedContextHint.Builder(new BundleHint.Builder().build(), key)
                                .build(),
                        new PublishedContextHint.Builder(new BundleHint.Builder().build(), key)
                                .build()),
                /* renderToken= */ emptySet(),
                key,
                listener,
                INLINE_EXECUTOR,
                mock(RefinerWorkflow.InsightConsumer.class));

        verify(listener).onRefinerWorkflowStarted(anyLong(), any());
        verify(listener).onRefinerWorkflowFinished(anyLong());
        verify(listener, never()).onRefinerWorkflowError(anyLong(), any());
    }

    @Test
    public void testWorkflowWithSingleRefinerHintsInParallel() throws GeneralSecurityException {
        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final RefinerWorkflow.EventListener listener = mock(RefinerWorkflow.EventListener.class);
        final RefinerWorkflow.ComponentProvider provider =
                mock(RefinerWorkflow.ComponentProvider.class);
        final Refiner refiner = buildMockParallelRefiner();

        doReturn(Set.of(refiner)).when(provider).getRefiners();
        doAnswer(invocation -> {
            invocation.getArgument(1, Consumer.class).accept(emptySet());
            return null;
        })
                .when(refiner).refine(any(), any(), any());

        RefinerWorkflow.start(
                provider,
                Set.of(
                        new PublishedContextHint.Builder(new BundleHint.Builder().build(), key)
                                .build(),
                        new PublishedContextHint.Builder(new BundleHint.Builder().build(), key)
                                .build()),
                /* renderToken= */ emptySet(),
                key,
                listener,
                INLINE_EXECUTOR,
                mock(RefinerWorkflow.InsightConsumer.class));

        verify(listener).onRefinerWorkflowStarted(anyLong(), any());
        verify(listener).onHintsSentToRefiner(anyLong(), any(), eq(refiner));
        verify(listener).onRefinerWorkflowFinished(anyLong());
        verify(listener, never()).onRefinerWorkflowError(anyLong(), any());
    }

    @Test
    public void testWorkflowWithSingleRefinerHintsInSeries() throws GeneralSecurityException {
        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final RefinerWorkflow.EventListener listener = mock(RefinerWorkflow.EventListener.class);
        final RefinerWorkflow.ComponentProvider provider =
                mock(RefinerWorkflow.ComponentProvider.class);
        final Refiner refiner = buildMockSeriesRefiner();

        doReturn(Set.of(refiner)).when(provider).getRefiners();
        doAnswer(invocation -> {
            invocation.getArgument(1, Consumer.class).accept(emptySet());
            return null;
        })
                .when(refiner).refine(any(), any(), any());

        RefinerWorkflow.start(
                provider,
                Set.of(
                        new PublishedContextHint.Builder(new BundleHint.Builder().build(), key)
                                .build(),
                        new PublishedContextHint.Builder(new BundleHint.Builder().build(), key)
                                .build()),
                /* renderToken= */ emptySet(),
                key,
                listener,
                INLINE_EXECUTOR,
                mock(RefinerWorkflow.InsightConsumer.class));

        verify(listener).onRefinerWorkflowStarted(anyLong(), any());
        verify(listener, times(2)).onHintsSentToRefiner(anyLong(), any(), eq(refiner));
        verify(listener).onRefinerWorkflowFinished(anyLong());
        verify(listener, never()).onRefinerWorkflowError(anyLong(), any());
    }

    @Test
    public void testWorkflowWithSeriesRefiners() throws GeneralSecurityException {
        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final BundleHint hint1 = new BundleHint.Builder().build();
        final BundleHint hint2 = new BundleHint.Builder().build();

        final RefinerWorkflow.EventListener listener = mock(RefinerWorkflow.EventListener.class);
        final RefinerWorkflow.ComponentProvider provider =
                mock(RefinerWorkflow.ComponentProvider.class);

        final Refiner refiner1 = buildMockParallelRefiner();
        final Refiner refiner2 = mock(Refiner.class);

        doAnswer(invocation -> {
            final Set<ContextHint> hints = PublishedContextHint.unwrapInto(
                    invocation.getArgument(0, Set.class), new HashSet<>());
            final Consumer<Set<ContextHint>> callback = invocation.getArgument(1, Consumer.class);
            if (hints.contains(hint2)) {
                callback.accept(emptySet());
            } else if (hints.contains(hint1)) {
                callback.accept(Set.of(hint2));
            } else {
                throw new RuntimeException("Unexpected situation: " + hints);
            }
            return null;
        })
                .when(refiner1).refine(any(), any(), any());

        doAnswer(invocation -> {
            final Set<PublishedContextHint> hints = invocation.getArgument(0, Set.class);
            final Set<UUID> seenIds = invocation.getArgument(1, Set.class);
            if (hints.size() == 2 && seenIds.isEmpty()) {
                return Set.of(hints);
            } else {
                return null;
            }
        })
                .when(refiner2).getInterestedHintClusters(any(), any(), anyBoolean());

        doAnswer(invocation -> {
            final Consumer<Set<ContextHint>> callback = invocation.getArgument(1, Consumer.class);
            callback.accept(emptySet());
            return null;
        })
                .when(refiner2).refine(any(), any(), any());

        doReturn(Set.of(refiner1, refiner2)).when(provider).getRefiners();

        RefinerWorkflow.start(
                provider,
                Set.of(new PublishedContextHint.Builder(hint1, key).build()),
                /* renderToken= */ emptySet(),
                ContextHintTestUtils.generateSignedHintKey(),
                listener,
                INLINE_EXECUTOR,
                mock(RefinerWorkflow.InsightConsumer.class));

        verify(listener).onRefinerWorkflowStarted(anyLong(), any());
        verify(listener).onHintsSentToRefiner(anyLong(), any(), eq(refiner1));
        verify(listener).onHintsSentToRefiner(anyLong(), any(), eq(refiner2));
        verify(listener).onHintsReceivedFromRefiner(anyLong(), any(), eq(refiner1));
        verify(listener).onRefinerWorkflowFinished(anyLong());
        verify(listener, never()).onRefinerWorkflowError(anyLong(), any());
    }

    @Test
    public void testNoWorkExpireWaitsForSlowRefiner() throws GeneralSecurityException {
        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final RefinerWorkflow.EventListener listener = mock(RefinerWorkflow.EventListener.class);
        final RefinerWorkflow.ComponentProvider provider =
                mock(RefinerWorkflow.ComponentProvider.class);
        final Refiner refiner = buildMockParallelRefiner();

        doReturn(Set.of(refiner)).when(provider).getRefiners();

        // Refiner does not do anything in render() call to simulate a badly written refiner.

        final RefinerWorkflow workflow = RefinerWorkflow.start(
                provider,
                Set.of(
                        new PublishedContextHint.Builder(new BundleHint.Builder().build(), key)
                                .build(),
                        new PublishedContextHint.Builder(new BundleHint.Builder().build(), key)
                                .build()),
                /* renderToken= */ emptySet(),
                key,
                listener,
                INLINE_EXECUTOR,
                mock(RefinerWorkflow.InsightConsumer.class));

        verify(listener).onRefinerWorkflowStarted(anyLong(), any());
        verify(listener).onHintsSentToRefiner(anyLong(), any(), eq(refiner));
        verify(listener, never()).onHintsReceivedFromRefiner(anyLong(), any(), eq(refiner));
        verify(listener, never()).onRefinerWorkflowFinished(anyLong());

        // Now expire the workflow and make sure it's been marked as finished.
        workflow.expire();
        verify(listener).onRefinerWorkflowFinished(anyLong());
        verify(listener, never()).onRefinerWorkflowError(anyLong(), any());
    }
}
