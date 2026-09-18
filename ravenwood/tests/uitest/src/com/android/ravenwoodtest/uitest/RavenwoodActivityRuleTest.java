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
package com.android.ravenwoodtest.uitest;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.platform.test.ravenwood.RavenwoodExperimentalApiChecker;
import android.platform.test.ravenwood.RavenwoodRule;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

/**
 * Simple test using {@link ActivityTestRule}.
 */
public class RavenwoodActivityRuleTest {
    @BeforeClass
    public static void beforeClass() {
        if (RavenwoodRule.isOnRavenwood()) {
            assumeTrue(RavenwoodExperimentalApiChecker.isExperimentalApiEnabled());
        }
    }

    private static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();

    private static final Context sContext = sInstrumentation.getContext();

    @Rule
    public ActivityTestRule<TestActivity> mEmptyActivityRule =
            new ActivityTestRule<>(TestActivity.class);

    @Rule
    public ActivityTestRule<TestViewActivity> mActivityWithViewRule =
            new ActivityTestRule<>(TestViewActivity.class);

    @Test
    public void testActivityResumed() {
        assertThat(mEmptyActivityRule.getActivity().isResumed()).isTrue();
        assertThat(mActivityWithViewRule.getActivity().isResumed()).isTrue();
    }
}
