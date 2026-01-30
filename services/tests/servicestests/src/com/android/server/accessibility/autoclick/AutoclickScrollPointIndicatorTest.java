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

package com.android.server.accessibility.autoclick;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.testing.AndroidTestingRunner;
import android.testing.TestableContext;
import android.testing.TestableLooper;
import android.view.WindowManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/** Test cases for {@link AutoclickScrollPointIndicator}. */
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class AutoclickScrollPointIndicatorTest {
    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Rule
    public TestableContext mTestableContext =
            new TestableContext(getInstrumentation().getContext());

    @Mock
    private WindowManager mMockWindowManager;

    private AutoclickScrollPointIndicator mPointIndicator;

    @Before
    public void setUp() {
        mTestableContext.addMockSystemService(Context.WINDOW_SERVICE, mMockWindowManager);
        mPointIndicator = new AutoclickScrollPointIndicator(mTestableContext);
    }

    @Test
    public void initialState_isNotVisible() {
        assertThat(mPointIndicator.isVisible()).isFalse();
    }

    @Test
    public void show_addsViewToWindowManager() {
        float testX = 100.0f;
        float testY = 200.0f;

        mPointIndicator.show(testX, testY);

        // Verify view is added to window manager.
        verify(mMockWindowManager).addView(eq(mPointIndicator),
                any(WindowManager.LayoutParams.class));

        // Verify isVisible reflects correct state.
        assertThat(mPointIndicator.isVisible()).isTrue();
    }

    @Test
    public void show_alreadyVisible_doesNotAddAgain() {
        float testX = 100.0f;
        float testY = 200.0f;

        // Show twice.
        mPointIndicator.show(testX, testY);
        mPointIndicator.show(testX, testY);

        // Verify addView was only called once.
        verify(mMockWindowManager, times(1)).addView(any(), any());
    }

    @Test
    public void hide_removesViewFromWindowManager() {
        float testX = 100.0f;
        float testY = 200.0f;

        // First show the indicator.
        mPointIndicator.show(testX, testY);

        // Then hide it.
        mPointIndicator.hide();

        // Verify view is removed from window manager.
        verify(mMockWindowManager).removeView(eq(mPointIndicator));

        // Verify indicator is hidden.
        assertThat(mPointIndicator.isVisible()).isFalse();
    }
}
