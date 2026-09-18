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

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.insight.PublishedContextInsight;

import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Interface to abstract away in-process / service-based refiners.
 *
 * @hide
 */
public interface Renderer extends Component {
    /** @hide */
    @IntDef(flag = true, prefix = { "PROPERTY_" }, value = {
            PROPERTY_CAN_RECEIVE_NOTIFICATION_INSIGHTS,
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface RendererProperty {}

    int PROPERTY_CAN_RECEIVE_NOTIFICATION_INSIGHTS = 1;

    /**
     * Returns whether the {@link Renderer} has the given properties.
     */
    default boolean hasProperties(int properties) {
        return (getProperties() & properties) == properties;
    }

    /**
     * Returns the properties for this {@link Renderer}.
     */
    default int getProperties() {
        return 0;
    }

    /** Gets the insights this renderer is interested in. */
    boolean isInterestedInInsight(PublishedContextInsight insight);

    /** Renders an insight. */
    void render(@NonNull PublishedContextInsight insight, RenderToken renderToken);

    /** Mints a RenderToken for this renderer. */
    default RenderToken mintRenderToken() {
        return new RenderToken(getComponentId(), null);
    }

    /** Mints a RenderToken with given tag for this renderer. */
    default RenderToken mintRenderToken(@Nullable String tag) {
        return new RenderToken(getComponentId(), tag);
    }
}
