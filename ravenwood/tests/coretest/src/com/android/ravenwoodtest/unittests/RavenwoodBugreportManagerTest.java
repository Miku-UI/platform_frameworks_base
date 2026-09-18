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
package com.android.ravenwoodtest.unittests;

import static com.google.common.truth.Truth.assertThat;

import android.os.Looper;
import android.platform.test.ravenwood.RavenwoodBugreportManager;

import org.junit.Test;

public class RavenwoodBugreportManagerTest {
    @Test
    public void testBugreport() throws Exception {
        RavenwoodBugreportManager.resetLastBugreportFile();
        // Make sure it's callable.
        var title = "NOT A BUG, JUST TESTING BUGREPORT";
        RavenwoodBugreportManager.doBugreport(
                title,
                Thread.currentThread(),
                new Exception("Test exception"),
                /* killself=*/ false);

        var bugreport = RavenwoodBugreportManager.readLastBugreportFile();

        // Make sure some basic contents are included.
        assertThat(bugreport).contains("Description: " + title);
        assertThat(bugreport).contains("= BUGREPORT END =");
    }

    @Test
    public void testBugreportWithPendingSyncBarriers() throws Exception {
        RavenwoodBugreportManager.resetLastBugreportFile();

        int t1;
        int t2;
        t1 = Looper.getMainLooper().getQueue().postSyncBarrier();
        try {
            t2 = Looper.getMainLooper().getQueue().postSyncBarrier();
            try {
                RavenwoodBugreportManager.doBugreport(
                        "Test: making sure sync barriers are included",
                        null, null,
                        /* killself=*/ false);
            } finally {
                Looper.getMainLooper().getQueue().removeSyncBarrier(t2);
            }
        } finally {
            Looper.getMainLooper().getQueue().removeSyncBarrier(t1);
        }

        // Take another bugreport, and make sure these tokens are not set.
        RavenwoodBugreportManager.resetLastBugreportFile();
        {
            RavenwoodBugreportManager.doBugreport(
                    "Test: making sure sync barriers are _not_ included",
                    null, null,
                    /* killself=*/ false);

            var bugreport = RavenwoodBugreportManager.readLastBugreportFile();
            assertThat(bugreport).doesNotContain("syncBarrierToken=[" + t1 + "]");
            assertThat(bugreport).doesNotContain("syncBarrierToken=[" + t2 + "]");
        }
    }
}
