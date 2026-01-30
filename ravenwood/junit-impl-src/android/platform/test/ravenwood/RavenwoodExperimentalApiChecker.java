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
package android.platform.test.ravenwood;

import com.android.internal.annotations.GuardedBy;

public class RavenwoodExperimentalApiChecker {
    private RavenwoodExperimentalApiChecker() {
    }

    private static final Object sLock = new Object();

    @GuardedBy("sLock")
    private static boolean sInitialized;

    @GuardedBy("sLock")
    private static boolean sExperimentalApiEnabled;

    public static boolean isExperimentalApiEnabled() {
        synchronized (sLock) {
            if (!sInitialized) {
                sExperimentalApiEnabled = RavenwoodEnvironment.getInstance()
                        .getBoolEnvVar("RAVENWOOD_ENABLE_EXP_API");
                sInitialized = true;
            }
            return sExperimentalApiEnabled;
        }
    }

    /**
     * Check if experimental APIs are enabled, and if not, throws
     * {@link RavenwoodUnsupportedApiException}.
     */
    public static boolean onExperimentalApiCalled(Class<?> clazz, String method, String desc) {
        // Even when experimental APIs are disabled, we don't want to throw from <clinit>.
        // because that'd make the class unloadable. Instead, we return false to skip the rest of
        // the code.
        if ("<clinit>".equals(method)) {
            return isExperimentalApiEnabled();
        }
        onExperimentalApiCalled(2);
        return true;
    }

    /**
     * Check if experimental APIs are enabled, and if not, throws
     * {@link RavenwoodUnsupportedApiException}.
     *
     * @param skipStackTraces the thrown {@link RavenwoodUnsupportedApiException} will skip
     * this many stack frames to make it look like it's thrown from the "real" missing API.
     */
    public static void onExperimentalApiCalled(int skipStackTraces) {
        if (isExperimentalApiEnabled()) {
            return;
        }
        throw new RavenwoodUnsupportedApiException().skipStackTraces(skipStackTraces);
    }


    private static void ensureCoreTest() {
        if (RavenwoodEnvironment.getInstance().getTestModuleName().equals("RavenwoodCoreTest")) {
            // Okay
            return;
        }
        throw new IllegalStateException("This method is only for internal testing");
    }

    /**
     * Override {@link #sExperimentalApiEnabled}. Only use it in internal testing.
     */
    public static void setExperimentalApiEnabledOnlyForTesting(boolean experimentalApiEnabled) {
        ensureCoreTest();
        sExperimentalApiEnabled = experimentalApiEnabled;
    }
}
