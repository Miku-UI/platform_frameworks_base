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
import static org.testng.Assert.expectThrows;

import android.app.HandoffActivityData;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.PersistableBundle;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;
import android.util.proto.ProtoParseException;

import com.android.server.companion.datatransfer.continuity.messages.HandoffActivityDataSerializer;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class HandoffActivityDataSerializerTest {

    static final ComponentName COMPONENT_NAME =
            new ComponentName("com.example.package", "com.example.package.MyActivity");
    static final Uri FALLBACK_URI = Uri.parse("https://www.google.com");

    @Test
    public void testRoundTripSerializationWorks() throws IOException {
        HandoffActivityData onlyComponentName =
            new HandoffActivityData.Builder(COMPONENT_NAME).build();
        confirmRoundTripSerializationDoesNotModifyData(onlyComponentName);

        HandoffActivityData withFallbackUri = new HandoffActivityData.Builder(COMPONENT_NAME)
            .setFallbackUri(FALLBACK_URI)
            .build();
        confirmRoundTripSerializationDoesNotModifyData(withFallbackUri);

        PersistableBundle extras = new PersistableBundle();
        extras.putString("key", "value");
        HandoffActivityData withExtras = new HandoffActivityData.Builder(COMPONENT_NAME)
            .setExtras(extras)
            .build();
        confirmRoundTripSerializationDoesNotModifyData(withExtras);

        HandoffActivityData withAllFields = new HandoffActivityData.Builder(COMPONENT_NAME)
            .setFallbackUri(FALLBACK_URI)
            .setExtras(extras)
            .build();
        confirmRoundTripSerializationDoesNotModifyData(withAllFields);
    }

    @Test
    public void testReadFromProto_noComponentName_throwsException() {
        ProtoOutputStream pos = new ProtoOutputStream();
        pos.flush();

        assertThrows(IOException.class, () -> HandoffActivityDataSerializer.readFromProto(
                new ProtoInputStream(pos.getBytes())));
    }

    @Test
    public void testReadFromProto_invalidComponentName_throwsException() {
        ProtoOutputStream pos = new ProtoOutputStream();
        pos.writeString(android.companion.HandoffActivityData.COMPONENT_NAME, "invalid");
        pos.flush();

        assertThrows(IOException.class, () -> HandoffActivityDataSerializer.readFromProto(
                new ProtoInputStream(pos.getBytes())));
    }

    private void confirmRoundTripSerializationDoesNotModifyData(HandoffActivityData expected)
        throws IOException {

        // Write to a ProtoOutputStream.
        ProtoOutputStream pos = new ProtoOutputStream();
        HandoffActivityDataSerializer.writeToProto(expected, pos);

        // Read from a ProtoInputStream.
        ProtoInputStream pis = new ProtoInputStream(pos.getBytes());
        HandoffActivityData actual = HandoffActivityDataSerializer.readFromProto(pis);

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