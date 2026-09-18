/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.bubbles;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.LayoutInflater;
import android.view.WindowManager;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.R;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.bubbles.bar.BubbleBarExpandedView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link com.android.wm.shell.bubbles.BubbleOverflow}.
 */
@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class BubbleOverflowTest extends ShellTestCase {

    private TestableBubblePositioner mPositioner;
    private BubbleOverflow mOverflow;
    private BubbleExpandedViewManager mExpandedViewManager;

    @Mock
    private BubbleController mBubbleController;
    @Mock
    private BubbleStackView mBubbleStackView;
    @Mock
    private BubbleExpandedView mMockExpandedView;
    @Mock
    private BubbleBarExpandedView mMockBubbleBarExpandedView;
    @Mock
    private LayoutInflater mMockInflater;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mExpandedViewManager = BubbleExpandedViewManager.fromBubbleController(mBubbleController);
        mPositioner = new TestableBubblePositioner(mContext,
                mContext.getSystemService(WindowManager.class));
        when(mBubbleController.getPositioner()).thenReturn(mPositioner);
        when(mBubbleController.getStackView()).thenReturn(mBubbleStackView);

        mOverflow = new BubbleOverflow(mContext, mPositioner);
    }

    @Test
    public void test_initialize_forStack() {
        assertThat(mOverflow.getExpandedView()).isNull();

        mOverflow.initialize(mExpandedViewManager, mBubbleStackView, mPositioner);

        assertThat(mOverflow.getExpandedView()).isNotNull();
        assertThat(mOverflow.getExpandedView().getBubbleKey()).isEqualTo(BubbleOverflow.KEY);
        assertThat(mOverflow.getBubbleBarExpandedView()).isNull();
    }

    @Test
    public void test_initialize_forBubbleBar() {
        mOverflow.initializeForBubbleBar(mExpandedViewManager, mPositioner);

        assertThat(mOverflow.getBubbleBarExpandedView()).isNotNull();
        assertThat(mOverflow.getExpandedView()).isNull();
    }

    @Test
    public void test_cleanUpExpandedState() {
        mOverflow.initialize(mExpandedViewManager, mBubbleStackView, mPositioner);
        assertThat(mOverflow.getExpandedView()).isNotNull();

        mOverflow.cleanUpExpandedState();
        assertThat(mOverflow.getExpandedView()).isNull();
    }

    @Test
    public void testUpdateFontSize_delegatesToViews() {
        // GIVEN both expanded views are mocked and initialized
        initializeOverflowWithMockViews();

        // WHEN updateFontSize is called
        mOverflow.updateFontSize();

        // THEN the call is delegated to both views
        verify(mMockExpandedView).updateFontSize();
        verify(mMockBubbleBarExpandedView).updateFontSize();
    }

    @Test
    public void testUpdateLocale_delegatesToViews() {
        // GIVEN both expanded views are mocked and initialized
        initializeOverflowWithMockViews();

        // WHEN updateLocale is called
        mOverflow.updateLocale();

        // THEN the call is delegated to both views
        verify(mMockExpandedView).updateLocale();
        verify(mMockBubbleBarExpandedView).updateLocale();
    }

    @Test
    public void testUpdateTheme_delegatesToViews() {
        // GIVEN both expanded views are mocked and initialized
        initializeOverflowWithMockViews();

        // WHEN updateTheme is called
        mOverflow.updateTheme();

        // THEN the call is delegated to both views
        verify(mMockExpandedView).updateTheme();
        verify(mMockBubbleBarExpandedView).updateTheme();
    }

    /**
     * Verifies that calling the update methods on {@link BubbleOverflow} before the expanded views
     * have been initialized does not cause a crash.
     */
    @Test
    public void testUpdates_noViews_doesNotCrash() {
        // In setUp, mOverflow is created but not initialized, so views are null.
        mOverflow.updateFontSize();
        mOverflow.updateLocale();
        mOverflow.updateTheme();
        // No crash is the assertion. Test passes if no exception is thrown.
    }

    /**
     * Helper method to set up the BubbleOverflow instance with mocked expanded views.
     * This involves spying the context to inject a mock LayoutInflater, which in turn provides
     * mock views when inflated.
     *
     * <p>NOTE: In production, only one of these views (stack or bubble bar) would be initialized at
     * a time. For this test, both are initialized to verify that update calls are correctly
     * propagated regardless of which view is active.
     */
    private void initializeOverflowWithMockViews() {
        Context spiedContext = spy(mContext);
        when(spiedContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                .thenReturn(mMockInflater);
        mOverflow = new BubbleOverflow(spiedContext, mPositioner);

        when(mMockInflater.inflate(R.layout.bubble_expanded_view, null, false))
                .thenReturn(mMockExpandedView);
        mOverflow.initialize(mExpandedViewManager, mBubbleStackView, mPositioner);

        when(mMockInflater.inflate(R.layout.bubble_bar_expanded_view, null, false))
                .thenReturn(mMockBubbleBarExpandedView);
        mOverflow.initializeForBubbleBar(mExpandedViewManager, mPositioner);
    }
}
