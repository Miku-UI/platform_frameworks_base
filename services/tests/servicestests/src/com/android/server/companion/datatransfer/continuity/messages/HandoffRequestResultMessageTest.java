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

package com.android.server.companion.datatransfer.continuity.messages;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.app.HandoffActivityData;
import android.content.ComponentName;
import android.net.Uri;
import android.os.PersistableBundle;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class HandoffRequestResultMessageTest {

    @Test
    public void testRoundTripSerialization_works() throws IOException {
        int expectedTaskId = 1;
        int expectedStatusCode = 1;
        HandoffActivityData expectedHandoffActivityData = createDummyHandoffActivityData();
        HandoffRequestResultMessage expected = new HandoffRequestResultMessage(
            expectedTaskId,
            expectedStatusCode,
            List.of(expectedHandoffActivityData));

        final ProtoOutputStream pos = new ProtoOutputStream();
        expected.writeToProto(pos);
        pos.flush();
        final ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        final HandoffRequestResultMessage actual = HandoffRequestResultMessage.readFromProto(pis);

        assertThat(actual.taskId()).isEqualTo(expected.taskId());
        assertThat(actual.statusCode()).isEqualTo(expected.statusCode());
        assertThat(actual.activities()).hasSize(expected.activities().size());
        for (int i = 0; i < expected.activities().size(); i++) {
            assertHandoffActivityDataEquals(
                expected.activities().get(i),
                actual.activities().get(i));
        }
    }

    private HandoffActivityData createDummyHandoffActivityData() {
        ComponentName componentName = new ComponentName(
            "com.example.app",
            "com.example.app.Activity");
        Uri fallbackUri = Uri.parse("http://example.com/fallback");
        PersistableBundle extras = new PersistableBundle();
        extras.putString("key", "value");

        return new HandoffActivityData.Builder(componentName)
                .setFallbackUri(fallbackUri)
                .setExtras(extras)
                .build();
    }

    private void assertHandoffActivityDataEquals(
            HandoffActivityData expected, HandoffActivityData actual) {

        assertThat(actual.getComponentName()).isEqualTo(expected.getComponentName());
        assertThat(actual.getFallbackUri()).isEqualTo(expected.getFallbackUri());
        if (expected.getExtras() != null) {
            assertThat(actual.getExtras()).isNotNull();
            assertThat(actual.getExtras().size()).isEqualTo(expected.getExtras().size());
            for (String key : expected.getExtras().keySet()) {
                assertThat(actual.getExtras().getString(key))
                    .isEqualTo(expected.getExtras().getString(key));
            }
        } else {
            assertThat(actual.getExtras()).isNull();
        }
    }
}