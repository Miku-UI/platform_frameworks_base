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

package com.android.server.companion.datatransfer.continuity.settings;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;
import android.util.AtomicFile;
import android.test.AndroidTestCase;
import androidx.test.InstrumentationRegistry;
import android.testing.AndroidTestingRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class HandoffPreferenceStoreTest extends AndroidTestCase {

    @Test
    public void testSetPreferences_writesToFileAndPersistWhenSerialized() {
        int userId = 10;
        int otherUserId = 11;
        File file =
                new File(
                        new File(InstrumentationRegistry.getContext().getFilesDir(), "system"),
                        "handoff_preferences");
        AtomicFile atomicFile = new AtomicFile(file);
        HandoffPreferenceStore firstPreferencesFile = new HandoffPreferenceStore(atomicFile);
        assertThat(firstPreferencesFile.isHandoffEnabledForUser(userId)).isTrue();
        firstPreferencesFile.setHandoffEnabledForUser(userId, false);
        assertThat(firstPreferencesFile.isHandoffEnabledForUser(userId)).isFalse();
        firstPreferencesFile.setHandoffEnabledForUser(otherUserId, false);
        assertThat(firstPreferencesFile.isHandoffEnabledForUser(otherUserId)).isFalse();

        HandoffPreferenceStore secondPreferencesFile =
                new HandoffPreferenceStore(new AtomicFile(file));
        assertThat(secondPreferencesFile.isHandoffEnabledForUser(userId)).isFalse();
        assertThat(secondPreferencesFile.isHandoffEnabledForUser(otherUserId)).isFalse();
    }
}
