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

package android.app.test;

import android.app.PropertyInvalidatedCache;

import org.junit.rules.ExternalResource;

/**
 * An {@link ExternalResource} that sets {@link PropertyInvalidatedCache} to test mode before the
 * test and back to normal mode after the test. This is needed by unit tests that try to invalidate
 * the cache and do not have permission to write to the corresponding system property.
 */
public class PropertyInvalidatedCacheTestRule extends ExternalResource {

    @Override
    public void before() {
        PropertyInvalidatedCache.setTestMode(true);
    }

    @Override
    public void after() {
        PropertyInvalidatedCache.setTestMode(false);
    }
}
