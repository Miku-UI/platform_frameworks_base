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

package android.graphics;

import static com.google.common.truth.Truth.assertThat;

import android.os.SystemClock;
import android.platform.test.annotations.DisabledOnRavenwood;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@DisabledOnRavenwood(reason = "HardwareRenderer.nCreateRootRenderNode throws UnsatisfiedLinkError")
@RunWith(AndroidJUnit4.class)
public class HardwareRendererTest {

    @Test
    public void testNotifyExpensiveFrameWithRateLimit() {
        final HardwareRenderer renderer = new HardwareRenderer();

        // Expect receiving the callback from rate limiter after notifying renderer
        int initialNotifyCount = renderer.notifyExpensiveFrameWithRateLimit("testing");
        assertThat(initialNotifyCount).isEqualTo(1);

        // Expect the rate limiter won't allow the burst calls of notifying renderer
        int currentNotifyCount = initialNotifyCount;
        for (int i = 0; i < 1000; i++) {
            currentNotifyCount = renderer.notifyExpensiveFrameWithRateLimit("testing");
        }
        assertThat(currentNotifyCount).isEqualTo(initialNotifyCount);

        // Expect the rate limiter allows the call of notifying renderer after the timeout
        SystemClock.sleep(100);
        currentNotifyCount = renderer.notifyExpensiveFrameWithRateLimit("testing");
        assertThat(currentNotifyCount).isGreaterThan(initialNotifyCount);
    }
}
