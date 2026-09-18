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

import android.app.HandoffActivityData;
import android.content.ComponentName;
import android.net.Uri;
import android.os.PersistableBundle;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class HandoffRequestResultMessageTest extends ProtoTest<HandoffRequestResultMessage> {

    @Override
    protected Proto.Builder<HandoffRequestResultMessage> newBuilder() {
        return new HandoffRequestResultMessage.Builder();
    }

    @Test
    public void testRoundTripSerialization_works() throws Exception {
        PersistableBundle extras = new PersistableBundle();
        extras.putString("key", "value");
        verifyRoundTrip(
                new HandoffRequestResultMessage(
                        1,
                        1,
                        List.of(
                                new HandoffActivityDataMessage(
                                        new HandoffActivityData.Builder(
                                                        new ComponentName(
                                                                "com.example.app",
                                                                "com.example.app.Activity"))
                                                .setFallbackUri(
                                                        Uri.parse("http://example.com/fallback"))
                                                .setExtras(extras)
                                                .build(),
                                        List.of(new byte[] {1, 2, 3, 4})))));
    }
}
