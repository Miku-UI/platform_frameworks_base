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

import static android.service.personalcontext.hint.ContextHintTestUtils.assertParcelUnparcel;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.graphics.Rect;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class UserInputHintTest {
    @Test
    public void testUserInputHint_bundleUnbundle() {
        final UserInputText userInputText =
                new UserInputText.Builder()
                        .setText("hello")
                        .setViewNodeBoundingBox(new Rect(1, 2, 3, 4))
                        .setFieldType(UserInputText.FIELD_TYPE_SEARCH_BOX)
                        .setUserInputTextSource(UserInputText.USER_INPUT_TEXT_SOURCE_TYPED)
                        .build();
        final ComponentName componentName = new ComponentName("packageName", "activityName");
        final UserInputHint hint =
                new UserInputHint.Builder(userInputText)
                        .setSourceAppActivityComponentName(componentName)
                        .build();

        final UserInputHint outputHint = (UserInputHint) assertParcelUnparcel(hint);

        assertThat(outputHint).isNotNull();
        final UserInputText outputUserInputText = outputHint.getUserInputText();
        assertThat(outputUserInputText).isEqualTo(userInputText);
        assertThat(outputHint.getSourceAppActivityComponentName()).isEqualTo(componentName);
    }
}
