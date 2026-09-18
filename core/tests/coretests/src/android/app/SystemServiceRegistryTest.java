/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.app;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class SystemServiceRegistryTest {

    private boolean mOriginalEnableServiceNotFoundWtf;

    @Before
    public void setUp() {
        mOriginalEnableServiceNotFoundWtf = SystemServiceRegistry.sEnableServiceNotFoundWtf;
    }

    @After
    public void tearDown() {
        SystemServiceRegistry.sEnableServiceNotFoundWtf = mOriginalEnableServiceNotFoundWtf;
    }

    /**
     * Regression test for b/494483230. Ensures that if the DROPBOX_SERVICE is missing, we don't
     * trigger a WTF, which could otherwise lead to an infinite loop in AMS error reporting.
     */
    @Test
    public void testGetSystemService_DropboxMissing_DoesNotWtf() throws Exception {
        SystemServiceRegistry.sEnableServiceNotFoundWtf = true;

        Field fetchersField =
                SystemServiceRegistry.class.getDeclaredField("SYSTEM_SERVICE_FETCHERS");
        fetchersField.setAccessible(true);
        Map<String, Object> fetchers = (Map<String, Object>) fetchersField.get(null);

        Object originalFetcher = fetchers.get(Context.DROPBOX_SERVICE);
        // We use the fetcher for a service that is known to be null on the current device.
        // We can use APP_PREDICTION_SERVICE or CONTENT_CAPTURE_MANAGER_SERVICE.
        // These are excluded from WTF normally, so their fetchers return null without issues.
        Object dummyFetcher = fetchers.get(Context.APP_PREDICTION_SERVICE);

        boolean wtfCalled[] = new boolean[1];

        Log.TerribleFailureHandler oldHandler =
                Log.setWtfHandler(
                        (tag, what, system) -> {
                            wtfCalled[0] = true;
                        });

        try {
            // Temporarily replace the fetcher
            fetchers.put(Context.DROPBOX_SERVICE, dummyFetcher);

            Context ctx = InstrumentationRegistry.getContext();
            Object service = ctx.getSystemService(Context.DROPBOX_SERVICE);

            assertNull("Service should be null", service);
            assertTrue("WTF should NOT be called for DROPBOX_SERVICE", !wtfCalled[0]);

        } finally {
            Log.setWtfHandler(oldHandler);
            if (originalFetcher != null) {
                fetchers.put(Context.DROPBOX_SERVICE, originalFetcher);
            }
        }
    }
}
