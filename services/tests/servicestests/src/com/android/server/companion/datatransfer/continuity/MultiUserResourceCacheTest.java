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

package com.android.server.companion.datatransfer.continuity;

import static com.google.common.truth.Truth.assertThat;

import android.testing.AndroidTestingRunner;
import android.platform.test.annotations.Presubmit;
import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class MultiUserResourceCacheTest {

    @Mock private TaskContinuityMessenger mMockTaskContinuityMessenger;

    private class FakeMultiUserResourceCache extends MultiUserResourceCache<FakeFeatureController> {

        public int mCreateFeatureControllerForUserCallCount = 0;

        @Override
        protected FakeFeatureController createResourceForUser(int userId) {
            mCreateFeatureControllerForUserCallCount++;
            return new FakeFeatureController(userId, mMockTaskContinuityMessenger);
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testgetOrCreateResource_onlyCreatesOneFeatureControllerPerUser() {
        int userId = 1;
        FakeMultiUserResourceCache cache = new FakeMultiUserResourceCache();
        FakeFeatureController featureController1 = cache.getOrCreateResource(userId);
        FakeFeatureController featureController2 = cache.getOrCreateResource(userId);
        assertThat(featureController1).isSameInstanceAs(featureController2);
        assertThat(cache.mCreateFeatureControllerForUserCallCount).isEqualTo(1);
    }

    @Test
    public void testgetOrCreateResource_createsFeatureControllerForDifferentUsers() {
        int userId1 = 1;
        int userId2 = 2;
        FakeMultiUserResourceCache cache = new FakeMultiUserResourceCache();
        FakeFeatureController featureController1 = cache.getOrCreateResource(userId1);
        FakeFeatureController featureController2 = cache.getOrCreateResource(userId2);
        assertThat(featureController1).isNotSameInstanceAs(featureController2);
        assertThat(cache.mCreateFeatureControllerForUserCallCount).isEqualTo(2);
    }
}
