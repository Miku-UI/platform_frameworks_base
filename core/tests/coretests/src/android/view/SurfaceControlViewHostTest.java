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

package android.view;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.app.Instrumentation;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Binder;
import android.platform.test.annotations.Presubmit;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link SurfaceControlViewHost}. */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class SurfaceControlViewHostTest {
    private static final String TAG = "SurfaceCtrlViewHostTest";

    private Instrumentation mInstrumentation;
    private SurfaceControlViewHost mSurfaceControlViewHost;

    public static class TestView extends View {
        public TestView(Context context) {
            super(context);
        }

        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
        }
    }

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
    }

    /** Test that SurfaceControlViewHost updates its underlying view on configuration change. */
    @Test
    public void testNotifyViewOnConfigurationChanged() {
        final Context context = mInstrumentation.getTargetContext();
        final Context windowContext =
                context.createWindowContext(
                        context.getDisplayNoVerify(),
                        android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        null);

        // Initialize SurfaceControlViewHost.
        mInstrumentation.runOnMainSync(
                () -> {
                    mSurfaceControlViewHost =
                            new SurfaceControlViewHost(
                                    windowContext,
                                    windowContext.getDisplayNoVerify(),
                                    new Binder());
                    try {
                        mSurfaceControlViewHost
                                .getSurfacePackage()
                                .getRemoteInterface()
                                .onInsetsChanged(new InsetsState(), new Rect());
                    } catch (Exception e) {
                        Log.e(TAG, "Could not update Insets.");
                    }
                });
        mInstrumentation.waitForIdleSync();

        // Adding a view to SurfaceControlViewHost.
        final TestView view = spy(new TestView(windowContext));
        mInstrumentation.runOnMainSync(() -> mSurfaceControlViewHost.setView(view, 100, 100));
        mInstrumentation.waitForIdleSync();

        // Update SurfaceControlViewHost's configuration.
        final Configuration newConfiguration =
                new Configuration(windowContext.getResources().getConfiguration());
        newConfiguration.screenWidthDp += 1;
        mInstrumentation.runOnMainSync(
                () ->
                        mSurfaceControlViewHost
                                .getSurfacePackage()
                                .notifyConfigurationChanged(newConfiguration));
        mInstrumentation.waitForIdleSync();

        // Check that the view got the updated configuration.
        verify(view)
                .onConfigurationChanged(
                        argThat(
                                configuration ->
                                        configuration.screenWidthDp
                                                == newConfiguration.screenWidthDp));
    }
}
