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

import java.lang.reflect.Method;
import java.util.Objects;

public class RavenwoodUnsupportedApiException extends UnsupportedOperationException {

    private String mReason = null;
    private final String mCustomMessage;

    public RavenwoodUnsupportedApiException(String message) {
        mCustomMessage = message;
    }

    public RavenwoodUnsupportedApiException() {
        mCustomMessage = null;
    }

    private String getMessagePrefix() {
        return Objects.requireNonNullElseGet(mCustomMessage, () -> "Method " + getReason());
    }

    @Override
    public String getMessage() {
        return getMessagePrefix() + " is not yet supported under the Ravenwood deviceless testing "
                + "environment; consider requesting support from the API owner or "
                + "consider using Mockito; more details at go/ravenwood";
    }

    /**
     * Set a custom reason for the unsupported API exception.
     */
    public RavenwoodUnsupportedApiException setReason(String reason) {
        mReason = reason;
        return this;
    }

    /**
     * Set a custom reason for the unsupported API exception.
     */
    public RavenwoodUnsupportedApiException setReason(Method method) {
        mReason = method.getDeclaringClass().getName() + "#" + method.getName();
        return this;
    }

    /**
     * Return the API that causes this exception.
     */
    public String getReason() {
        if (mReason != null) return mReason;
        var caller = getStackTrace()[0];
        mReason = caller.getClassName() + "#" + caller.getMethodName();
        return mReason;
    }
}
