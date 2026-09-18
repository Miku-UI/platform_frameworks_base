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
package com.android.server.companion.datatransfer.crossdevicesync.network.scanner;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.companion.datatransfer.crossdevicesync.network.scanner.Scanner.ScanningSession;
import com.android.server.companion.datatransfer.crossdevicesync.services.SyncServiceTestBase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ScannerImplTest extends SyncServiceTestBase {
    private static final String REQUEST_NAME = "test";

    @Before
    public void setUp() {
        mFakeCompanionActionController.init();
    }

    @Test
    public void startScanning_requestSent() {
        ScanningSession s = mScanner.startScanning(/* associationId= */ 1, REQUEST_NAME);

        assertThat(mFakeCompanionActionController.getScanningFuture(/* associationId= */ 1))
                .isNotNull();
        assertThat(s.isActive()).isTrue();
        assertThat(s.getAssociationId()).isEqualTo(1);
    }

    @Test
    public void startScanning_alreadyStarted_noOp() {
        ScanningSession s1 = mScanner.startScanning(/* associationId= */ 1, REQUEST_NAME);
        ScanningSession s2 = mScanner.startScanning(/* associationId= */ 1, REQUEST_NAME);

        assertThat(mFakeCompanionActionController.getScanningFuture(/* associationId= */ 1))
                .isNotNull();
        assertThat(s1.isActive()).isTrue();
        assertThat(s1.getAssociationId()).isEqualTo(1);
        assertThat(s2.isActive()).isTrue();
        assertThat(s2.getAssociationId()).isEqualTo(1);
    }

    @Test
    public void stopScanning_requestSent() {
        ScanningSession s = mScanner.startScanning(/* associationId= */ 1, REQUEST_NAME);

        s.close();

        assertThat(s.isActive()).isFalse();
        assertThat(mFakeCompanionActionController.getScanningFuture(/* associationId= */ 1))
                .isNull();
    }

    @Test
    public void stopScanning_hasOtherActiveSession_requestSentAfterLastClose() {
        ScanningSession s1 = mScanner.startScanning(/* associationId= */ 1, REQUEST_NAME);
        ScanningSession s2 = mScanner.startScanning(/* associationId= */ 1, REQUEST_NAME);

        s1.close();

        assertThat(s1.isActive()).isFalse();
        assertThat(s2.isActive()).isTrue();
        assertThat(mFakeCompanionActionController.getScanningFuture(/* associationId= */ 1))
                .isNotNull();

        s2.close();

        assertThat(s2.isActive()).isFalse();
        assertThat(mFakeCompanionActionController.getScanningFuture(/* associationId= */ 1))
                .isNull();
    }

    @Test
    public void startScanning_multipleSessionsForDifferentAssociations() {
        ScanningSession s1 = mScanner.startScanning(/* associationId= */ 1, REQUEST_NAME);
        ScanningSession s2 = mScanner.startScanning(/* associationId= */ 2, REQUEST_NAME);

        assertThat(s1.isActive()).isTrue();
        assertThat(s2.isActive()).isTrue();
        assertThat(mFakeCompanionActionController.getScanningFuture(/* associationId= */ 1))
                .isNotNull();
        assertThat(mFakeCompanionActionController.getScanningFuture(/* associationId= */ 2))
                .isNotNull();

        s1.close();

        assertThat(s1.isActive()).isFalse();
        assertThat(s2.isActive()).isTrue();
        assertThat(mFakeCompanionActionController.getScanningFuture(/* associationId= */ 1))
                .isNull();
        assertThat(mFakeCompanionActionController.getScanningFuture(/* associationId= */ 2))
                .isNotNull();

        s2.close();

        assertThat(s2.isActive()).isFalse();
        assertThat(mFakeCompanionActionController.getScanningFuture(/* associationId= */ 2))
                .isNull();
    }

    @Test
    public void closeAllSessions_requestDeactivated() {
        ScanningSession s1 = mScanner.startScanning(/* associationId= */ 1, REQUEST_NAME);
        ScanningSession s2 = mScanner.startScanning(/* associationId= */ 2, REQUEST_NAME);

        mScanner.closeAllScanningSessions();

        assertThat(s1.isActive()).isFalse();
        assertThat(s2.isActive()).isFalse();
        assertThat(mFakeCompanionActionController.getScanningFuture(/* associationId= */ 1))
                .isNull();
        assertThat(mFakeCompanionActionController.getScanningFuture(/* associationId= */ 2))
                .isNull();
    }
}
