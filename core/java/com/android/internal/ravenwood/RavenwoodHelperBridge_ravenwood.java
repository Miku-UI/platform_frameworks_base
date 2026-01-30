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

import com.android.ravenwood.OpenJdkWorkaround;
import com.android.ravenwood.common.RavenwoodInternalUtils;

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
}
