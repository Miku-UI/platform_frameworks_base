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

package com.android.server.personalcontext;

import android.os.Bundle;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.HintFilter;
import android.service.personalcontext.hint.HintInvalidationHint;
import android.service.personalcontext.hint.PublishedContextHint;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.HintInvalidationInsight;
import android.service.personalcontext.insight.PublishedContextInsight;
import android.service.personalcontext.insight.interaction.InsightEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.personalcontext.component.Refiner;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Built in understander for {@link HintInvalidationHint} -> {@link HintInvalidationInsight}.
 *
 * <p>This component is responsible for taking a {@link HintInvalidationHint} and turning it into a
 * corresponding {@link HintInvalidationInsight}. It does not confirm whether the invalidation hint
 * is valid (e.g. came from the correct package). It just handles the transformation and
 * re-transmittal.
 */
public class HintInvalidationUnderstander implements Refiner {
    private static final HintFilter HINT_FILTER = new HintFilter.Builder()
            .addHintType(HintInvalidationHint.class, HintFilter.FILTER_TYPE_REQUIRED)
            .build();

    private final UUID mComponentId = UUID.randomUUID();

    public interface HintInvalidateInsightConsumer {
        /**
         * Invoked to publish a {@link HintInvalidationInsight}
         * @param insight the insight to publish
         * @param componentId the publisher's component id.
         */
        void consume(HintInvalidationInsight insight, UUID componentId);
    }
    private final HintInvalidateInsightConsumer mPublishInsightCallback;

    public HintInvalidationUnderstander(HintInvalidateInsightConsumer publishInsightCallback) {
        mPublishInsightCallback = publishInsightCallback;
    }

    @Nullable
    @Override
    public Set<Set<PublishedContextHint>> getInterestedHintClusters(
            @NonNull Set<PublishedContextHint> allContextHints, @NonNull Set<UUID> seenIDs,
            boolean isFirstRun) {
        final Set<PublishedContextHint> hints = HINT_FILTER.getInterestedHintClusters(
                allContextHints, seenIDs);
        return hints == null || hints.isEmpty() ? null : Set.of(hints);
    }

    @Override
    public void refine(@NonNull Set<PublishedContextHint> inputHints,
            @NonNull Consumer<Set<ContextHint>> callback,
            @NonNull RefinerWorkflow.InsightConsumer insightCallback) {
        final HashSet<ContextInsight> insights = new HashSet<>();
        for (PublishedContextHint hint : inputHints) {
            insights.add(new HintInvalidationInsight.Builder(hint).build());
        }
        insightCallback.accept(mComponentId, insights);
    }

    @Override
    public void handleEvent(@NonNull String packageName, @NonNull InsightEvent event) {
        // Do nothing.
    }

    @Override
    public void handleFeedback(@NonNull PublishedContextInsight insight, Bundle feedback) {
        // Do nothing.
    }

    @Override
    public UUID getComponentId() {
        return mComponentId;
    }

    @Override
    public String toString() {
        return "HintInvalidationUnderstander{" + mComponentId + '}';
    }
}
