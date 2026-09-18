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

package androidx.window.extensions.layout;

import static android.view.Display.INVALID_DISPLAY;

import android.content.Context;
import android.content.ContextWrapper;
import android.hardware.display.DisplayManager;
import android.os.StrictMode;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.UiContext;

/**
 * Utility class for UI Context operations.
 */
final class UiContextUtils {

    private static final String TAG = UiContextUtils.class.getSimpleName();

    private UiContextUtils() {
    }

    @NonNull
    @UiContext
    static Context getOrCreateUiContext(@NonNull Context context) {
        if (context.isUiContext()) {
            return context;
        }
        final int displayId = context.getDisplayId();
        final Display display = context.getSystemService(DisplayManager.class)
                .getDisplay(displayId);
        Log.w(TAG, "Requested context is a non-UI Context. Creating a UI-Context with display: "
                + displayId + ". Context: " + dumpAllBaseContextToString(context));
        return context.createWindowContext(
                display, WindowManager.LayoutParams.TYPE_APPLICATION, null /* options */);
    }

    @NonNull
    static String dumpAllBaseContextToString(@NonNull Context context) {
        final StringBuilder builder = new StringBuilder("Context=" + context);
        while ((context instanceof ContextWrapper wrapper) && wrapper.getBaseContext() != null) {
            context = wrapper.getBaseContext();
            builder.append(", of which baseContext=").append(context);
        }
        return builder.toString();
    }

    static void assertUiContext(@NonNull Context context) {
        final IllegalArgumentException exception = new IllegalArgumentException(
                "Context must be a UI Context with display association, which should be "
                        + "an Activity, WindowContext or InputMethodService");
        if (!context.isUiContext()) {
            throw exception;
        }
        if (context.getAssociatedDisplayId() == INVALID_DISPLAY) {
            StrictMode.onIncorrectContextUsed("The given context is a UI context, "
                    + "but it is not associated with any display. "
                    + "This context may not receive WindowLayoutInfo updates and "
                    + "may get an empty WindowLayoutInfo return value. "
                    + dumpAllBaseContextToString(context), exception);
        }
    }
}
