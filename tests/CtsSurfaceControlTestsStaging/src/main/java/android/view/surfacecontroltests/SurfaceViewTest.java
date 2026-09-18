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

package android.view.surfacecontroltests;

import static com.google.common.truth.Truth.assertThat;

import android.view.SurfaceControlViewHost;
import android.view.SurfaceView;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;

import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SurfaceViewTest {
    @Rule
    public ActivityTestRule<SurfaceViewTestActivity> mActivityRule =
            new ActivityTestRule<>(SurfaceViewTestActivity.class);

    private SurfaceViewTestActivity mActivity;
    private SurfaceView mSurfaceView;
    private SurfaceControlViewHost mSurfaceControlViewHost;
    private SurfaceControlViewHost.SurfacePackage mSurfacePackage;

    @Before
    public void setUp() {
        mActivity = mActivityRule.getActivity();
        mSurfaceView = mActivity.getSurfaceView();

        // Create a SurfaceControlViewHost on the main thread providing a minimal view hierarchy.
        mActivity.runOnUiThread(
                () -> {
                    mSurfaceControlViewHost =
                            new SurfaceControlViewHost(
                                    mActivity, mActivity.getDisplay(), mSurfaceView.getHostToken());
                    Button button = new Button(mActivity);
                    button.setText("Embedded button");
                    mSurfaceControlViewHost.setView(button, 10, 10);
                    mSurfacePackage = mSurfaceControlViewHost.getSurfacePackage();
                    mSurfaceView.setChildSurfacePackage(mSurfacePackage);
                });
    }

    @Test
    @UiThreadTest
    public void testAccessibilityHierarchyEmbedding() throws InterruptedException {
        // By default, the accessibility hierarchy is embedded.
        assertThat(mSurfaceView.isAccessibilityHierarchyEmbeddingEnabled()).isTrue();
        AccessibilityNodeInfo rootNodeWithChild = mSurfaceView.createAccessibilityNodeInfo();
        rootNodeWithChild.setQueryFromAppProcessEnabled(mSurfaceView, true);
        assertThat(rootNodeWithChild.getChildCount()).isEqualTo(1);

        // Disable embedding.
        mSurfaceView.setAccessibilityHierarchyEmbeddingEnabled(false);
        assertThat(mSurfaceView.isAccessibilityHierarchyEmbeddingEnabled()).isFalse();

        // The embedded hierarchy should no longer be present.
        AccessibilityNodeInfo rootNodeWithoutChild = mSurfaceView.createAccessibilityNodeInfo();
        rootNodeWithoutChild.setQueryFromAppProcessEnabled(mSurfaceView, true);
        assertThat(mSurfaceView.createAccessibilityNodeInfo().getChildCount()).isEqualTo(0);

        // Re-enable embedding.
        mSurfaceView.setAccessibilityHierarchyEmbeddingEnabled(true);
        assertThat(mSurfaceView.isAccessibilityHierarchyEmbeddingEnabled()).isTrue();

        // The embedded hierarchy should be present again.
        AccessibilityNodeInfo rootNodeAfterReEnabling =
                mSurfaceView.createAccessibilityNodeInfo();
        rootNodeAfterReEnabling.setQueryFromAppProcessEnabled(mSurfaceView, true);
        assertThat(rootNodeAfterReEnabling.getChildCount()).isEqualTo(1);
    }
}
