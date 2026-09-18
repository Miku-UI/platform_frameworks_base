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
package com.android.server.autofill.service;

import android.app.assist.AssistStructure;
import android.os.CancellationSignal;
import android.service.autofill.AutofillService;
import android.service.autofill.Dataset;
import android.service.autofill.FillCallback;
import android.service.autofill.FillRequest;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveCallback;
import android.service.autofill.SaveRequest;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;

import com.android.frameworks.autofilltests.R;

/**
 * An instrumented autofill service for testing.
 */
public class InstrumentedAutofillService extends AutofillService {

    /**
     * An interface test can implement to provide fill response
     */
    public interface FillResponseProvider {
        /**
         * Called when a fill request is received on test autofill service
         */
        void onFillRequest(FillRequest request, CancellationSignal cancellationSignal,
                FillCallback callback);
    }

    private static FillResponseProvider sReplier;

    public static void setReplier(FillResponseProvider replier) {
        sReplier = replier;
    }

    @Override
    public void onFillRequest(FillRequest request, CancellationSignal cancellationSignal,
            FillCallback callback) {
        if (sReplier != null) {
            sReplier.onFillRequest(request, cancellationSignal, callback);
            return;
        }
        AssistStructure structure = request.getFillContexts()
                .get(request.getFillContexts().size() - 1).getStructure();

        AutofillId usernameId = findNodeByResourceId(structure, "username");
        if (usernameId == null) {
            callback.onFailure("No username field found");
            return;
        }

        RemoteViews presentation = new RemoteViews(getPackageName(), R.layout.autofill_suggestion);
        presentation.setTextViewText(R.id.suggestion_text, "testuser");

        Dataset dataset = new Dataset.Builder(presentation)
                .setValue(usernameId, AutofillValue.forText("testuser"))
                .build();

        FillResponse response = new FillResponse.Builder()
                .addDataset(dataset)
                .build();

        callback.onSuccess(response);
    }

    @Override
    public void onSaveRequest(SaveRequest saveRequest, SaveCallback saveCallback) {
        System.out.println("debug - onSaveRequest");
    }

    private AssistStructure.ViewNode findNode(AssistStructure.ViewNode node, String resourceId) {
        if (resourceId.equals(node.getIdEntry())) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AssistStructure.ViewNode child = findNode(node.getChildAt(i), resourceId);
            if (child != null) {
                return child;
            }
        }
        return null;
    }

    private AutofillId findNodeByResourceId(AssistStructure structure, String resourceId) {
        for (int i = 0; i < structure.getWindowNodeCount(); i++) {
            AssistStructure.WindowNode windowNode = structure.getWindowNodeAt(i);
            AssistStructure.ViewNode viewNode = findNode(windowNode.getRootViewNode(), resourceId);
            if (viewNode != null) {
                return viewNode.getAutofillId();
            }
        }
        return null;
    }
}
