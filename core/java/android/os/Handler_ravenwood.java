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
package android.os;

import android.annotation.NonNull;
import android.platform.test.ravenwood.RavenwoodErrorHandler;

public class Handler_ravenwood {
    private Handler_ravenwood() {
    }

    /**
     * Called by {@link Handler#onBeforeEnqueue(MessageQueue, Message, long)}
     */
    public static void onBeforeEnqueue(@NonNull MessageQueue queue, @NonNull Message msg,
            long uptimeMillis) {
        RavenwoodErrorHandler.onBeforeEnqueue(msg);
    }
}
