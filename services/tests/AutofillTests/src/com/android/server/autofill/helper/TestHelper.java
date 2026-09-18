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
package com.android.server.autofill.helper;

import android.app.assist.AssistStructure;
import android.content.Context;
import android.view.autofill.AutofillId;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import java.io.IOException;

public class TestHelper {

    public static final UiDevice sUiDevice = UiDevice.getInstance(
            InstrumentationRegistry.getInstrumentation());

    /*
     * Returns the application context.
     */
    public static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    /**
     * Executes a shell command.
     */
    public static void executeShellCommand(String command) {
        try {
            sUiDevice.executeShellCommand(command);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Finds a node by its resource id.
     */
    public static AutofillId findNodeByResourceId(AssistStructure structure, String resourceId) {
        for (int i = 0; i < structure.getWindowNodeCount(); i++) {
            AssistStructure.WindowNode windowNode = structure.getWindowNodeAt(i);
            AssistStructure.ViewNode viewNode = findNode(windowNode.getRootViewNode(), resourceId);
            if (viewNode != null) {
                return viewNode.getAutofillId();
            }
        }
        return null;
    }

    /**
     * Finds a AssistStructure.ViewNode by its resource id.
     */
    public static AssistStructure.ViewNode findNode(AssistStructure.ViewNode node,
            String resourceId) {
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
}
