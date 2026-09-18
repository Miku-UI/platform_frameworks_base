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

package com.android.server.personalcontext.component;

import android.annotation.Nullable;
import android.content.ComponentName;
import android.os.Bundle;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.PublishedContextHint;
import android.service.personalcontext.insight.PublishedContextInsight;
import android.service.personalcontext.insight.interaction.InsightEvent;

import androidx.annotation.NonNull;

import com.android.server.personalcontext.RefinerWorkflow;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Interface to abstract away in-process / service-based refiners.
 *
 * @hide
 */
public interface Refiner extends Component {
    /** Gets the ComponentName that this component represents. */
    @Nullable
    default ComponentName getComponentName() {
        return null;
    }

    /** Gets a grouping of hints the refiner is interested in. */
    @Nullable
    Set<Set<PublishedContextHint>> getInterestedHintClusters(
            @NonNull Set<PublishedContextHint> allContextHints,
            @NonNull Set<UUID> seenIDs,
            boolean isFirstRun);

    /** Refines hints into more hints. */
    void refine(
            @NonNull Set<PublishedContextHint> inputHints,
            @NonNull Consumer<Set<ContextHint>> callback,
            @NonNull RefinerWorkflow.InsightConsumer insightCallback);

    /** Reports an event for logging. */
    void handleEvent(@NonNull String packageName, @NonNull InsightEvent event);

    /** Reports user feedback. */
    void handleFeedback(@NonNull PublishedContextInsight insight, @Nullable Bundle feedback);
}
