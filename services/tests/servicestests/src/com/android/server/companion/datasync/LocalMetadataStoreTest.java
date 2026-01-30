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
package com.android.server.companion.datasync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.os.PersistableBundle;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.frameworks.servicestests.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class LocalMetadataStoreTest {

    @Test
    public void readMetadataFromFile() throws XmlPullParserException, IOException {
        InputStream xmlStream = InstrumentationRegistry.getInstrumentation().getContext()
                .getResources().openRawResource(R.raw.companion_local_metadata);

        PersistableBundle metadata = PersistableBundle.readFromStream(xmlStream);
        assertNotNull(metadata);
        assertEquals(2, metadata.size());

        PersistableBundle feature1 = metadata.getPersistableBundle("feature1");
        assertNotNull(feature1);
        assertEquals(1, feature1.getInt("version"));
        assertEquals("hello", feature1.getString("data"));

        PersistableBundle feature2 = metadata.getPersistableBundle("feature2");
        assertNotNull(feature2);
        assertEquals(1, feature2.getInt("version"));
        assertEquals("world", feature2.getString("data"));
    }
}
