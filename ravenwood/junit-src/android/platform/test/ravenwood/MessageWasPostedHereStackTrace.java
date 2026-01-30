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

/**
 * Stacktrace that represents where a {@link android.os.Message} was posted.
 */
public class MessageWasPostedHereStackTrace extends Exception {
    private static final String TAG = "MessageWasPostedHereStackTrace";
    private final Thread mPostedThread;

    public MessageWasPostedHereStackTrace() {
        super("Message was posted here");
        mPostedThread = Thread.currentThread();
    }

    /**
     * Inject "this" exception into another exception as the deepest "cause".
     * (Unless it already has a MessageWasPostedHereStackTrace in the cause chain.)
     */
    public void injectAsCause(Throwable other) {
        var th = other;
        for (;;) {
            var c = th.getCause();
            if (c instanceof MessageWasPostedHereStackTrace) {
                // Already has a MessageWasPostedHereStackTrace, so don't modify.
                return;
            }
            if (c == null) {
                // Found the deepest exception. Inject self.
                try {
                    th.initCause(this);
                } catch (Exception couldNotInject) {
                    // If an exception explicitly has null as a cause, we can't inject
                    // another, but unfortunately we can't tell if that's the case or not.
                    // In that case, just print what we know in the log to help debug.
                    Log.e(TAG, "Exception caused by a message."
                            + " Showing the detected exception followed by the stacktrace where"
                            + " the message was posted.");
                    Log.e(TAG, "Detected exception: ", other);
                    Log.e(TAG, "Message was posted here: ", this);
                }
                return;
            }
            th = th.getCause();
        }
    }

    @NonNull
    public Thread getPostedThread() {
        return mPostedThread;
    }
}
