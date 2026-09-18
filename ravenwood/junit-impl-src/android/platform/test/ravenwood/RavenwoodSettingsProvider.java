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

import static android.platform.test.ravenwood.RavenwoodProxyHelper.sNotImplementedHandler;

import android.os.Bundle;
import android.testing.TestableSettingsProviderBase;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * Minimum implementation to support "empty" settings provider.
 * See b/457840588 for the details.
 */
public class RavenwoodSettingsProvider implements InvocationHandler {

    private final MockSettingsProvider mTestableProvider = new MockSettingsProvider();

    private static class MockSettingsProvider extends TestableSettingsProviderBase {
        @Override
        protected Bundle onMissingValue(String method, String arg, Bundle extras) {
            return Bundle.EMPTY;
        }

        private void clear() {
            mValues.clear();
        }
    }

    public void reset() {
        MockSettingsProvider.clearSettingsProvider();
        mTestableProvider.clear();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("call".equals(method.getName())) {
            return mTestableProvider.call(
                    (String) args[1],
                    (String) args[2],
                    (String) args[3],
                    (Bundle) args[4]
            );
        } else {
            return sNotImplementedHandler.invoke(proxy, method, args);
        }
    }
}
