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

package com.android.internal.app;

import static android.content.Intent.ACTION_SEND;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Intent;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChooserActivityCrossProfileIntentsTest {
    @Test
    public void test_sanitizePayloadIntents() {
        final Intent intentOne = new Intent(ACTION_SEND);
        intentOne.setPackage("org.test.example");
        final Intent intentTwo = new Intent(ACTION_SEND);
        intentTwo.setComponent(ComponentName.unflattenFromString("org.test.example/.TestActivity"));
        final Intent intentThree = new Intent(ACTION_SEND);
        intentThree.setSelector(new Intent(intentOne));
        final Intent intentFour = new Intent(ACTION_SEND);
        intentFour.setSelector(new Intent(intentTwo));
        ArrayList<Intent> intents = new ArrayList<>();
        Collections.addAll(intents, intentOne, intentTwo, intentThree, intentFour);

        List<Intent> sanitizedIntents = ChooserActivity.sanitizePayloadIntents(intents);

        assertThat(sanitizedIntents).hasSize(intents.size());
        for (Intent intent : sanitizedIntents) {
            assertThat(intent.getPackage()).isNull();
            assertThat(intent.getComponent()).isNull();
            Intent selector = intent.getSelector();
            if (selector != null) {
                assertThat(selector.getPackage()).isNull();
                assertThat(selector.getComponent()).isNull();
            }
        }
    }
}
