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
package com.android.server.companion.datatransfer.crossdevicesync.network.advertiser;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.companion.datatransfer.crossdevicesync.network.advertiser.Advertiser.AdvertisingSession;
import com.android.server.companion.datatransfer.crossdevicesync.services.SyncServiceTestBase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AdvertiserImplTest extends SyncServiceTestBase {
    private static final String REQUEST_NAME = "test";

    @Before
    public void setUp() {
        mFakeCompanionActionController.init();
    }

    @Test
    public void startAdvertising_requestSent() {
        AdvertisingSession s = mAdvertiser.startAdvertising(/* associationId= */ 1, REQUEST_NAME);

        assertThat(mFakeCompanionActionController.getAdvertisingFuture(/* associationId= */ 1))
                .isNotNull();
        assertThat(s.isActive()).isTrue();
        assertThat(s.getAssociationId()).isEqualTo(1);
    }

    @Test
    public void startAdvertising_alreadyStarted_noOp() {
        AdvertisingSession s1 = mAdvertiser.startAdvertising(/* associationId= */ 1, REQUEST_NAME);
        AdvertisingSession s2 = mAdvertiser.startAdvertising(/* associationId= */ 1, REQUEST_NAME);

        assertThat(mFakeCompanionActionController.getAdvertisingFuture(/* associationId= */ 1))
                .isNotNull();
        assertThat(s1.isActive()).isTrue();
        assertThat(s1.getAssociationId()).isEqualTo(1);
        assertThat(s2.isActive()).isTrue();
        assertThat(s2.getAssociationId()).isEqualTo(1);
    }

    @Test
    public void stopAdvertising_requestSent() {
        AdvertisingSession s = mAdvertiser.startAdvertising(/* associationId= */ 1, REQUEST_NAME);

        s.close();

        assertThat(s.isActive()).isFalse();
        assertThat(mFakeCompanionActionController.getAdvertisingFuture(/* associationId= */ 1))
                .isNull();
    }

    @Test
    public void stopAdvertising_hasOtherActiveSession_requestSentAfterLastClose() {
        AdvertisingSession s1 = mAdvertiser.startAdvertising(/* associationId= */ 1, REQUEST_NAME);
        AdvertisingSession s2 = mAdvertiser.startAdvertising(/* associationId= */ 1, REQUEST_NAME);

        s1.close();

        assertThat(s1.isActive()).isFalse();
        assertThat(s2.isActive()).isTrue();
        assertThat(mFakeCompanionActionController.getAdvertisingFuture(/* associationId= */ 1))
                .isNotNull();

        s2.close();

        assertThat(s2.isActive()).isFalse();
        assertThat(mFakeCompanionActionController.getAdvertisingFuture(/* associationId= */ 1))
                .isNull();
    }

    @Test
    public void startAdvertising_multipleSessionsForDifferentAssociations() {
        AdvertisingSession s1 = mAdvertiser.startAdvertising(/* associationId= */ 1, REQUEST_NAME);
        AdvertisingSession s2 = mAdvertiser.startAdvertising(/* associationId= */ 2, REQUEST_NAME);

        assertThat(s1.isActive()).isTrue();
        assertThat(s2.isActive()).isTrue();
        assertThat(mFakeCompanionActionController.getAdvertisingFuture(/* associationId= */ 1))
                .isNotNull();
        assertThat(mFakeCompanionActionController.getAdvertisingFuture(/* associationId= */ 2))
                .isNotNull();

        s1.close();

        assertThat(s1.isActive()).isFalse();
        assertThat(s2.isActive()).isTrue();
        assertThat(mFakeCompanionActionController.getAdvertisingFuture(/* associationId= */ 1))
                .isNull();
        assertThat(mFakeCompanionActionController.getAdvertisingFuture(/* associationId= */ 2))
                .isNotNull();

        s2.close();

        assertThat(s2.isActive()).isFalse();
        assertThat(mFakeCompanionActionController.getAdvertisingFuture(/* associationId= */ 2))
                .isNull();
    }

    @Test
    public void closeAllSessions_requestDeactivated() {
        AdvertisingSession s1 = mAdvertiser.startAdvertising(/* associationId= */ 1, REQUEST_NAME);
        AdvertisingSession s2 = mAdvertiser.startAdvertising(/* associationId= */ 2, REQUEST_NAME);

        mAdvertiser.closeAllAdvertisingSessions();

        assertThat(s1.isActive()).isFalse();
        assertThat(s2.isActive()).isFalse();
        assertThat(mFakeCompanionActionController.getAdvertisingFuture(/* associationId= */ 1))
                .isNull();
        assertThat(mFakeCompanionActionController.getAdvertisingFuture(/* associationId= */ 2))
                .isNull();
    }
}
