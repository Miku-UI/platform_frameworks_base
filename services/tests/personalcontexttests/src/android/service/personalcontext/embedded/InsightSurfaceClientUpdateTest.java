/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.service.personalcontext.embedded;

import static android.view.View.SCROLL_AXIS_HORIZONTAL;
import static android.view.View.SCROLL_AXIS_NONE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.view.View;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InsightSurfaceClientUpdateTest {
    @Test
    public void testCreateUpdate() {
        final int measureSpecWidth = 100;
        final int measureSpecHeight = 200;
        final Color backgroundColor = Color.valueOf(Color.BLUE);
        final int nestedScrollAxes = SCROLL_AXIS_HORIZONTAL;
        final boolean nestedScrollAxisLocked = true;
        final boolean shouldBlur = true;
        final int themeResourceId = 7;
        final Configuration configuration = mock(Configuration.class);

        final InsightSurfaceClientUpdate update =
                new InsightSurfaceClientUpdate.Builder()
                        .setMeasureSpecWidth(measureSpecWidth)
                        .setMeasureSpecHeight(measureSpecHeight)
                        .setBackgroundColor(backgroundColor)
                        .setNestedScrollAxes(nestedScrollAxes)
                        .setNestedScrollAxisLocked(nestedScrollAxisLocked)
                        .setShouldBlur(shouldBlur)
                        .setThemeResourceId(themeResourceId)
                        .setConfiguration(configuration)
                        .build();

        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_MEASURE_SPEC_WIDTH)).isTrue();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_MEASURE_SPEC_HEIGHT)).isTrue();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_BACKGROUND_COLOR)).isTrue();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_NESTED_SCROLL_AXES)).isTrue();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_NESTED_SCROLL_AXIS_LOCKED))
                .isTrue();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_SHOULD_BLUR)).isTrue();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_THEME_RESOURCE_NAME)).isTrue();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_CONFIGURATION)).isTrue();

        assertThat(update.getMeasureSpecWidth()).isEqualTo(measureSpecWidth);
        assertThat(update.getMeasureSpecHeight()).isEqualTo(measureSpecHeight);
        assertThat(update.getNestedScrollAxes()).isEqualTo(nestedScrollAxes);
        assertThat(update.isNestedScrollAxisLocked()).isEqualTo(nestedScrollAxisLocked);
        assertThat(update.shouldBlur()).isEqualTo(shouldBlur);
        assertThat(update.getThemeResourceId()).isEqualTo(themeResourceId);
        assertThat(update.getBackgroundColor()).isEqualTo(backgroundColor);
    }

    @Test
    public void testUpdateDefaults() {
        final InsightSurfaceClientUpdate update = new InsightSurfaceClientUpdate.Builder().build();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_MEASURE_SPEC_WIDTH)).isFalse();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_MEASURE_SPEC_HEIGHT)).isFalse();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_BACKGROUND_COLOR)).isFalse();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_NESTED_SCROLL_AXES)).isFalse();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_NESTED_SCROLL_AXIS_LOCKED))
                .isFalse();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_THEME_RESOURCE_NAME)).isFalse();
        assertThat(update.hasUpdate(InsightSurfaceClientUpdate.KEY_CONFIGURATION)).isFalse();

        assertThat(update.getMeasureSpecWidth()).isEqualTo(View.MeasureSpec.UNSPECIFIED);
        assertThat(update.getMeasureSpecHeight()).isEqualTo(View.MeasureSpec.UNSPECIFIED);
        assertThat(update.getNestedScrollAxes()).isEqualTo(SCROLL_AXIS_NONE);
        assertThat(update.isNestedScrollAxisLocked()).isEqualTo(false);
        assertThat(update.shouldBlur()).isEqualTo(false);
        assertThat(update.getThemeResourceId()).isEqualTo(Resources.ID_NULL);
        assertThat(update.getBackgroundColor()).isNull();
    }
}
