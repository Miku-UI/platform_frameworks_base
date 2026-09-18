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

package android.service.personalcontext.hint;

import static com.google.common.truth.Truth.assertThat;

import android.view.textclassifier.TextClassification;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TextClassificationHintTest {

    @Test
    public void testToBundleImpl_parcelUnparcel() {
        String inputSessionId = "session-id";
        String inputValue = "test-text";
        TextClassificationHint hint =
                new TextClassificationHint.Builder(
                                new TextClassification.Request.Builder(inputValue, 0, 4).build(),
                                inputSessionId)
                        .build();

        final ContextHint outputHint = ContextHintTestUtils.assertParcelUnparcel(hint);
        assertThat(outputHint).isInstanceOf(TextClassificationHint.class);
        String outputValue =
                ((TextClassificationHint) outputHint)
                        .getTextClassificationRequest()
                        .getText()
                        .toString();
        assertThat(outputValue).isEqualTo(inputValue);
        String outputSessionId = ((TextClassificationHint) outputHint).getSessionId();
        assertThat(outputSessionId).isEqualTo(inputSessionId);
    }
}
