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

import java.util.Arrays;

public class RavenwoodUnsupportedApiException extends UnsupportedOperationException {

    private int mSkipStackTraces = 0;

    public RavenwoodUnsupportedApiException(String message) {
        super(message);
    }

    public RavenwoodUnsupportedApiException() {
        super("This method is not yet supported under the Ravenwood deviceless testing "
                + "environment; consider requesting support from the API owner or "
                + "consider using Mockito; more details at go/ravenwood");
    }

    /**
     * Sets the number of stack frames to skip when calling {@link #getStackTrace()}.
     */
    public RavenwoodUnsupportedApiException skipStackTraces(int number) {
        mSkipStackTraces = number;
        return this;
    }

    @Override
    public StackTraceElement[] getStackTrace() {
        var traces = super.getStackTrace();
        if (mSkipStackTraces > 0) {
            return Arrays.copyOfRange(traces, mSkipStackTraces, traces.length);
        }
        return traces;
    }
}
