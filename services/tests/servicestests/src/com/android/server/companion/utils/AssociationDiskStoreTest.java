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
package com.android.server.companion.utils;

import static org.junit.Assert.assertEquals;

import android.companion.AssociationInfo;
import android.os.PersistableBundle;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.frameworks.servicestests.R;
import com.android.server.companion.association.AssociationDiskStore;
import com.android.server.companion.association.Associations;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class AssociationDiskStoreTest {

    @Test
    public void readLegacyFileByNewLogic() throws XmlPullParserException, IOException {
        InputStream legacyXmlStream = InstrumentationRegistry.getInstrumentation().getContext()
                .getResources().openRawResource(R.raw.companion_android14_associations);

        Associations associations = AssociationDiskStore.readAssociationsFromInputStream(
                0, legacyXmlStream, "state");
        assertEquals(2, associations.getAssociations().size());
    }

    @Test
    public void readAssociationsFromFile_v1Schema() throws XmlPullParserException, IOException {
        InputStream xmlStream = InstrumentationRegistry.getInstrumentation().getContext()
                .getResources().openRawResource(R.raw.companion_associations_v1);

        Associations associations = AssociationDiskStore.readAssociationsFromInputStream(
                0, xmlStream, "state");

        // Assert overall XML file fields.
        assertEquals(2, associations.getAssociations().size());
        assertEquals(3, associations.getMaxId());

        // Assert individual fields for the second association.
        AssociationInfo association = associations.getAssociations().get(1);
        assertEquals(3, association.getId());
        assertEquals("com.sample.companion.another.app", association.getPackageName());
        assertEquals("John's Watch", association.getDisplayName());
        assertEquals(true, association.isSelfManaged());
        assertEquals(false, association.isNotifyOnDeviceNearby());
        assertEquals(false, association.isRevoked());
        assertEquals(1634641160229L, association.getTimeApprovedMs());
        assertEquals(1634641160229L, association.getLastTimeConnectedMs());
        assertEquals(1, association.getSystemDataSyncFlags());
        assertEquals(0, association.getTransportFlags());
        assertEquals("1234", association.getDeviceId().getCustomId());
        assertEquals(2, association.getPackagesToNotify().size());

        // Assert metadata fields.
        PersistableBundle metadata = association.getMetadata();
        assertEquals(2, metadata.size());
        assertEquals(1, metadata.getPersistableBundle("feature1").getInt("version"));
        assertEquals("test", metadata.getPersistableBundle("feature1").getString("data"));
        assertEquals("test", metadata.getPersistableBundle("feature2").getString("data"));
    }
}
