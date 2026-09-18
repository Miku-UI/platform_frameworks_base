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

package com.android.server.personalcontext.textclassifier;

import static java.util.Objects.requireNonNull;

import android.annotation.Nullable;
import android.app.RemoteAction;
import android.service.personalcontext.RenderToken;
import android.service.personalcontext.hint.PublishedContextHint;
import android.service.personalcontext.hint.TextClassificationHint;
import android.service.personalcontext.insight.ActionableInsight;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.InsightTraverser;
import android.service.personalcontext.insight.InsightVisitor;
import android.service.personalcontext.insight.PublishedContextInsight;
import android.util.Log;
import android.util.Slog;
import android.view.textclassifier.TextClassification;

import androidx.annotation.NonNull;

import com.android.server.personalcontext.component.Renderer;
import com.android.server.textclassifier.personalcontext.PersonalContextBridge;
import com.android.server.textclassifier.personalcontext.PersonalContextBridge.TextClassificationKey;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

public class TextClassificationActionRenderer implements Renderer {
    private static final String TAG = "TcActionRenderer";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final UUID mComponentId = UUID.randomUUID();
    private final PersonalContextBridge mTcPersonalContextBridge;

    public TextClassificationActionRenderer(
            @NonNull PersonalContextBridge tcPersonalContextBridge) {
        super();
        requireNonNull(tcPersonalContextBridge);
        mTcPersonalContextBridge = tcPersonalContextBridge;
    }

    /**
     * TextClassificationActionRenderer is only interested in insights with a RenderToken attached
     * so {@link isInterestedInInsight} is passed through. Return false as default
     */
    @Override
    public boolean isInterestedInInsight(PublishedContextInsight insight) {
        return false;
    }

    @Override
    public void render(
            @NonNull PublishedContextInsight publishedContextInsight, RenderToken renderToken) {
        Map<TextClassificationKey, TextClassification.Builder> textClassificationSessionToBuilders =
                new LinkedHashMap<>();
        InsightTraverser.traverse(
                publishedContextInsight.getInsight(),
                new InsightCollector(textClassificationSessionToBuilders));

        if (DEBUG) {
            Slog.w(TAG, textClassificationSessionToBuilders.size() + " valid insights received");
        }

        for (final Entry<TextClassificationKey, TextClassification.Builder> entry :
                textClassificationSessionToBuilders.entrySet()) {
            mTcPersonalContextBridge.merge(entry.getKey(), entry.getValue().build());
        }
    }

    @Override
    public UUID getComponentId() {
        return mComponentId;
    }

    @Nullable
    private static TextClassificationHint getFirstTextClassificationHintIfPresent(
            ContextInsight insight) {
        for (PublishedContextHint hint : insight.getOriginHints()) {
            if (hint.getContextHint() instanceof TextClassificationHint textClassificationHint) {
                return textClassificationHint;
            }
        }
        return null;
    }

    private record InsightCollector(
            Map<TextClassificationKey, TextClassification.Builder> mTextClassificationBuilders)
            implements InsightVisitor {
        private InsightCollector(
                @NonNull
                        Map<TextClassificationKey, TextClassification.Builder>
                                mTextClassificationBuilders) {
            this.mTextClassificationBuilders = mTextClassificationBuilders;
        }

        @Override
        public void visit(@NonNull ActionableInsight insight, int index) {
            final TextClassificationHint textClassificationHint =
                    getFirstTextClassificationHintIfPresent(insight);
            if (textClassificationHint == null) {
                if (DEBUG) {
                    Slog.d(TAG, "TextClassificationHint is not present in insight");
                }
                return;
            }

            RemoteAction remoteAction = insight.getActionDetails().getRemoteAction();
            if (remoteAction == null) {
                if (DEBUG) {
                    Slog.d(TAG, "RemoteAction is not present in insight");
                }
                return;
            }

            TextClassificationKey sessionKey =
                    new TextClassificationKey(
                            textClassificationHint.getSessionId(),
                            textClassificationHint.getTextClassificationRequest());
            final TextClassification.Builder builder = mTextClassificationBuilders.get(sessionKey);
            if (builder != null) {
                builder.addAction(remoteAction);
            } else {
                mTextClassificationBuilders.put(
                        sessionKey, new TextClassification.Builder().addAction(remoteAction));
            }
        }
    }
}
