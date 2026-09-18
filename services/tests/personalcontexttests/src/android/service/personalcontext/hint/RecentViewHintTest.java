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

import java.time.Instant;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class RecentViewHintTest {
    @Test
    public void testRecentViewHint_bundleUnbundle() {
        final CapturedText capturedText =
                new CapturedText.Builder()
                        .setViewNodeText("text")
                        .setViewNodeDescription("description")
                        .setViewId("viewId")
                        .setViewNodeBoundingBox(new Rect(1, 2, 3, 4))
                        .setResourceId("resourceId")
                        .setViewNodeLastUpdated(Instant.ofEpochMilli(12345L))
                        .build();
        final ComponentName componentName = new ComponentName("packageName", "activityName");
        final RecentViewHint hint =
                new RecentViewHint.Builder()
                        .addCapturedText(capturedText)
                        .setLocusId("locusId")
                        .setSourceAppActivityComponentName(componentName)
                        .build();

        final RecentViewHint outputRecentViewHint = (RecentViewHint) assertParcelUnparcel(hint);

        assertThat(outputRecentViewHint).isNotNull();
        assertThat(outputRecentViewHint.getCapturedTexts()).containsExactly(capturedText);
        assertThat(outputRecentViewHint.getLocusId()).isEqualTo("locusId");
        assertThat(outputRecentViewHint.getSourceAppActivityComponentName())
                .isEqualTo(componentName);
        assertThat(outputRecentViewHint.getCapturedTexts().get(0).getViewNodeLastSeen()).isNull();
    }
}
