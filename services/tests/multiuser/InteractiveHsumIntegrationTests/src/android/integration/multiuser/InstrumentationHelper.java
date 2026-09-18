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
package android.integration.multiuser;

import android.annotation.Nullable;
import android.app.Instrumentation;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.io.IOException;

/** Helper for instrumentation-related operations (like running shell command). */
public final class InstrumentationHelper {

    private static final String TAG = InstrumentationHelper.class.getSimpleName();

    /** Gets the package's {@link Instrumentation}. */
    public static Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }

    /** Runs the given {@code shell} command. */
    @FormatMethod
    public static String runShellCommand(@FormatString String cmdFmt,
            @Nullable Object... cmdArgs) throws IOException {
        String cmd = String.format(cmdFmt, cmdArgs);
        Log.d(TAG, "Running '" + cmd + "'");

        String result = UiDevice.getInstance(getInstrumentation()).executeShellCommand(cmd).trim();
        Log.d(TAG, "Result: " + result);
        return result;
    }

    /** Checks if {@code adb} is running as root. */
    public static boolean isAdbRoot() throws IOException {
        String adbUser = runShellCommand("id -u");
        Log.d(TAG, "adb user: " + adbUser);
        return adbUser.equals("0");
    }

    private InstrumentationHelper() {
        throw new UnsupportedOperationException("contains only static methods");
    }

}
