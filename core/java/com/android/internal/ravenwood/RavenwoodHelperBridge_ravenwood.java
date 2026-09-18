/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.internal.ravenwood;

import android.platform.test.ravenwood.RavenwoodImplUtils;
import android.util.Log;

import com.android.ravenwood.OpenJdkWorkaround;
import com.android.ravenwood.common.RavenwoodInternalUtils;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

public class RavenwoodHelperBridge_ravenwood {
    private RavenwoodHelperBridge_ravenwood() {
    }

    /**
     * Called from {@link RavenwoodHelperBridge#getRavenwoodRuntimePath()}.
     */
    public static String getRavenwoodRuntimePath(RavenwoodHelperBridge env) {
        return RavenwoodInternalUtils.getRavenwoodRuntimePath();
    }

    /**
     * Called from {@link RavenwoodHelperBridge#fromAddress(long)}.
     */
    public static <T> T fromAddress(RavenwoodHelperBridge env, long address) {
        return OpenJdkWorkaround.fromAddress(address);
    }

    static Throwable getStackTrace(String message) {
        return RavenwoodImplUtils.getStackTrace(
                message,
                RavenwoodHelperBridge.class,
                /*removeMatchingFrameToo=*/ true);
    }

    /**
     * Number used to easily find a matching enter/exit pair of method log. We start with this
     * arbitrary number to so we can easily identify them.
     */
    private static final AtomicLong sMethodLogToken = RavenwoodInternalUtils.getNextIdGenerator();

    private static final String CALL_TAG = "Ravenwood-call";

    static long enterMethod(Class<?> clazz, Object obj, String method, String desc, Object[] args) {

        var token = sMethodLogToken.getAndIncrement();
        var trace = RavenwoodImplUtils.getStackTrace(clazz.getSimpleName() + "."
                + method + "() was called here", clazz, /*removeMatchingFrameToo=*/ false);

        Log.v(CALL_TAG, clazz.getName() + "." + method + desc + ": enter["  + token + "]: args="
                + Arrays.toString(args), trace);
        return token;
    }

    static void exitMethod(long token, Class<?> clazz, Object obj, String method, String desc,
            Object ret) {
        Log.v(CALL_TAG,
                clazz.getName() + "." + method + desc + ": exit[" + token + "] : ret=" + ret);
    }
}
