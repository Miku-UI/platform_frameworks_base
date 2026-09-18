/**
 * Copyright (c) 2025, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.service.personalcontext.refiner;

import android.os.Bundle;
import android.os.ParcelUuid;
import android.service.personalcontext.IOpCallback;
import android.service.personalcontext.hint.PublishedContextHintWrapper;
import android.service.personalcontext.insight.PublishedContextInsightWrapper;
import android.service.personalcontext.insight.interaction.InsightEvent;
import android.service.personalcontext.refiner.IGetFilterCallback;
import android.service.personalcontext.refiner.IRefineCallback;

/**
 * Interface for refiner services.
 *
 * @hide
 */
oneway interface IRefiner {
    /**
     * Requests that a set of new hints be refined. All of the hints in inputHints will be
     * hints that this refiner hasn't seen before. The callback may be called exactly once,
     * with a new set of hints.
     */
    void refine(in ParcelUuid componentId, in List<PublishedContextHintWrapper> inputHints,
        in IRefineCallback callback, in IOpCallback opCallback);

    /** Gets a filter to be used when deciding whether to send an insight to this refiner. */
    void getFilter(in ParcelUuid componentId, in IGetFilterCallback callback,
        in IOpCallback opCallback);

    /** Reports an insight event back to the understander. */
    void handleEvent(in ParcelUuid componentId, String packageName, in InsightEvent event,
        in IOpCallback opCallback);

    /** Reports user feedback back to the understander. */
    void handleFeedback(in ParcelUuid componentId, in PublishedContextInsightWrapper insight,
        in Bundle feedback, in IOpCallback opCallback);
}