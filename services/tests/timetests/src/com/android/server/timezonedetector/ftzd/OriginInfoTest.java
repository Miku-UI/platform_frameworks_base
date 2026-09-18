/*
 * Copyright 2025 The Android Open Source Project
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
package com.android.server.timezonedetector.ftzd;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class OriginInfoTest {

    @Test
    public void testConstructor() {
        OriginInfo originInfo = new OriginInfo();
        assertThat(originInfo.getQuality()).isEqualTo(100);
    }

    @Test
    public void testConstructor_explicitTimestamp() {
        long explicitTimestamp = 123456789L;
        OriginInfo originInfo = new OriginInfo(explicitTimestamp);

        assertThat(originInfo.getTimestampCreated()).isNotEqualTo(explicitTimestamp);
        assertThat(originInfo.getTimestampDetected()).isEqualTo(explicitTimestamp);
        assertThat(originInfo.getTimestampLastUpdated()).isEqualTo(explicitTimestamp);
        assertThat(originInfo.getQuality()).isEqualTo(100);
    }

    @Test
    public void testCopyConstructor() {
        OriginInfo original = new OriginInfo().setQuality(50).updateLastUpdated();
        OriginInfo copy = new OriginInfo(original);

        assertThat(copy).isEqualTo(original);
    }

    @Test
    public void testUpdateLastUpdated() {
        OriginInfo originInfo = new OriginInfo();

        assertThat(originInfo.toString()).contains("mUpdates=1");

        originInfo.updateLastUpdated();

        assertThat(originInfo.toString()).contains("mUpdates=2");
    }

    @Test
    public void testSetQuality() {
        OriginInfo originInfo = new OriginInfo();

        assertThat(originInfo.getQuality()).isEqualTo(100);

        originInfo.setQuality(75);

        assertThat(originInfo.getQuality()).isEqualTo(75);
    }
}
