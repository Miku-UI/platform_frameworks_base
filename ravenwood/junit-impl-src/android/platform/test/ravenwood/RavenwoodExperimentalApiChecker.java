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

import android.annotation.NonNull;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RavenwoodExperimentalApiChecker {
    private RavenwoodExperimentalApiChecker() {
    }

    private static final String TAG_EXP_CALL = "RavenwoodExpCall";

    private static final Object sLock = new Object();

    /**
     * Value from $RAVENWOOD_ENABLE_EXP_API
     *
     * -1: uninitialized, 0: disabled, 1: enabled.
     */
    private static volatile int sExperimentalApiEnabled = -1;

    /**
     * $RAVENWOOD_LOG_EXP_API
     *
     * -1: uninitialized, 0: disabled, 1: enabled, 2: with stack trace
     */
    private static volatile int sLogExperimentalApiCall = -1;

    /**
     * A map with key = "method info", value = "number of times the method was called".
     */
    @GuardedBy("sLock")
    private static final Map<MethodInfo, IntRef> sStats = new HashMap<>();

    /**
     * These tests modules get experimental APIs enabled by default.
     *
     * Only tests owned by the ravenwood team should be listed here.
     */
    private static final List<String> EXP_API_ENABLED_MODULE = Arrays.asList(
            "RavenwoodUiTest_exp"
    );

    private record MethodInfo(Class<?> clazz, String name, String desc) {
        MethodInfo(StackWalker.StackFrame frame) {
            this(frame.getDeclaringClass(), frame.getMethodName(), frame.getDescriptor());
        }

        MethodInfo(Method method) {
            this(method.getDeclaringClass(), method.getName(), Type.getMethodDescriptor(method));
        }

        public String toShortString() {
            return clazz.getName() + "#" + name;
        }

        @Override
        public String toString() {
            return clazz.getName() + "#" + name + desc;
        }
    }

    private static class IntRef {
        int i = 0;
    }

    public static boolean isExperimentalApiEnabled() {
        var enable = sExperimentalApiEnabled;
        if (enable < 0) {
            var def = EXP_API_ENABLED_MODULE.contains(
                    RavenwoodEnvironment.getInstance().getTestModuleName()) ? 1 : 0;
            enable = RavenwoodEnvironment.getInstance().getIntEnvVar(
                    "RAVENWOOD_ENABLE_EXP_API", def);
            sExperimentalApiEnabled = enable;
        }
        return enable == 1;
    }

    private static void maybeLogExperimentalApiCall(@NonNull MethodInfo mi) {
        synchronized (sLock) {
            sStats.computeIfAbsent(mi, k -> new IntRef()).i += 1;
        }
        var enable = sLogExperimentalApiCall;
        if (enable < 0) {
            enable = RavenwoodEnvironment.getInstance().getIntEnvVar("RAVENWOOD_LOG_EXP_API", 0);
            sLogExperimentalApiCall = enable;
        }
        if (enable < 1) {
            return;
        }
        Log.i(TAG_EXP_CALL, mi.toString());
        if (enable < 2) {
            return;
        }
        RavenwoodImplUtils.dumpStack(TAG_EXP_CALL, 3);
    }

    /**
     * Check if experimental APIs are enabled, and if not, throws
     * {@link RavenwoodUnsupportedApiException}.
     */
    public static boolean onExperimentalApiCalled(Class<?> clazz, String method, String desc) {
        var mi = new MethodInfo(clazz, method, desc);
        maybeLogExperimentalApiCall(mi);
        // Even when experimental APIs are disabled, we don't want to throw from <clinit>.
        // because that'd make the class unloadable. Instead, we return false to skip the rest of
        // the code.
        if ("<clinit>".equals(method)) {
            return isExperimentalApiEnabled();
        }
        onExperimentalApiCalledInner(mi);
        return true;
    }

    /**
     * Check if experimental APIs are enabled, and if not, throws
     * {@link RavenwoodUnsupportedApiException}.
     *
     * @param skipStackTraces the number of stack frames to skip to traverse back to
     *                        the "actual" experimental API.
     */
    public static void onExperimentalApiCalled(int skipStackTraces) {
        var walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        var frame = walker.walk(s -> s.skip(skipStackTraces).findFirst().get());
        var mi = new MethodInfo(frame);
        maybeLogExperimentalApiCall(mi);
        onExperimentalApiCalledInner(mi);
    }

    /**
     * Check if experimental APIs are enabled, and if not, throws
     * {@link RavenwoodUnsupportedApiException}.
     */
    public static void onExperimentalApiCalled(Method method) {
        var mi = new MethodInfo(method);
        maybeLogExperimentalApiCall(mi);
        onExperimentalApiCalledInner(mi);
    }

    private static void onExperimentalApiCalledInner(MethodInfo mi) {
        if (!isExperimentalApiEnabled()) {
            throw new RavenwoodUnsupportedApiException().setReason(mi.toShortString());
        }
    }

    /**
     * Print all experimental method call stats.
     */
    public static void dumpExperimentalApiUsage() {
        final String module = RavenwoodEnvironment.getInstance().getTestModuleName();
        final String file = "/tmp/ravenwood-experimental-api-" + module + "-stats.txt";
        try (PrintWriter stats = new PrintWriter(file)) {
            synchronized (sLock) {
                sStats.entrySet().stream()
                        .sorted((a, b) -> b.getValue().i - a.getValue().i)
                        .forEach(e -> stats.printf("%s %d\n", e.getKey(), e.getValue().i));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create file=" + file, e);
        }
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
        sExperimentalApiEnabled = experimentalApiEnabled ? 1 : 0;
    }
}
