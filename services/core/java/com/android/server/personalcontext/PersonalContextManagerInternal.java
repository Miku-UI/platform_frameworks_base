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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.NotificationEvent;
import android.view.textclassifier.TextClassification;

import java.util.Set;

/**
 * Personal context manager local system service interface.
 *
 * @hide Only for use within the system server.
 */
public abstract class PersonalContextManagerInternal {
    /**
     * Called when a notification event occurs.
     *
     * @param event The notification event.
     */
    public abstract void onNotificationEvent(@NonNull NotificationEvent event);

    /**
     * Called when a classify text request occurs.
     *
     * @param userId The userId that triggered this
     * @param sessionId The TextClassification sessionId value for this request
     * @param request The TextClassification request
     */
    public abstract void onTextClassifyRequest(
            int userId, String sessionId, @NonNull TextClassification.Request request);

    /**
     * Triggers a Personal Context service flow with raw data in the form of hints.
     *
     * @param hints raw data to be injected into the context flow
     * @param renderToken optional token(s) indicating which renderer should be used to render
     *     results of this flow to the user; if {@code null} then this flow can be rendered by any
     *     Personal Context renderer
     * @param userId to run the flow as
     */
    public abstract void publishTriggeringHint(
            @NonNull Set<ContextHint> hints, @Nullable Set<RenderToken> renderToken, int userId);

    /**
     * Checks to see if the personal context service is enabled for a given package name and user.
     *
     * @param packageName package to check
     * @param userId user to check
     */
    public abstract boolean isPersonalContextServiceEnabledForPackage(
            String packageName, @UserIdInt int userId);
}
