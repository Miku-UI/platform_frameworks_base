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

package com.android.mediaroutertest;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.media.MediaRouter2;
import android.media.SuggestedDeviceInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class MediaRouter2Test {

    private MediaRouter2 mMediaRouter2;

    @Before
    public void setup() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mMediaRouter2 = MediaRouter2.getInstance(context);
    }

    @Test
    public void getDeviceSuggestions_beforeLocalRouterRegistration_returnsEmptyMap() {
        Map<String, List<SuggestedDeviceInfo>> deviceSuggestions =
                mMediaRouter2.getDeviceSuggestions();
        assertThat(deviceSuggestions).isEmpty();
    }
}
