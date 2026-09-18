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

package com.android.server.am;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import android.content.Context;
import android.content.IntentFilter;
import android.util.ArraySet;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.compat.PlatformCompat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(MockitoJUnitRunner.class)
public class BroadcastControllerTest {
    private static final int TEST_PID = 123;
    private static final int TEST_UID = 456;
    private static final int TEST_USER_ID = 0;
    private static final String TEST_PKG = "com.example";

    private BroadcastController mBroadcastController;
    private Context mContext;

    @Mock
    private ActivityManagerService mAms;
    @Mock
    private BroadcastQueue mBroadcastQueue;
    @Mock
    PlatformCompat mPlatformCompat;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mBroadcastController = new BroadcastController(mContext,
                mAms, mBroadcastQueue);
    }

    @Test
    public void testBuildTooManyReceiversExceptionMessage() {
        ProcessRecord app = mock(ProcessRecord.class);
        ProcessReceiverRecord receiverRecord = mock(ProcessReceiverRecord.class);
        doReturn(receiverRecord).when(app).getReceivers();

        ArraySet<ReceiverList> receiverLists = new ArraySet<>();
        for (int i = 0; i < 100; i++) {
            ReceiverList rl = mock(ReceiverList.class);
            List<BroadcastFilter> filters = new ArrayList<>();
            for (int j = 0; j < i + 1; j++) {
                IntentFilter intentFilter = new IntentFilter("action" + i);
                BroadcastFilter filter = new BroadcastFilter(
                        /*_filter=*/ intentFilter,
                        /*_receiverList=*/ null,
                        TEST_PKG,
                        /*_featureId=*/ null,
                        /*_receiverId=*/ "receiver" + i,
                        /*_requiredPermission=*/ null,
                        /*_owningUid=*/ 0,
                        /*_userId=*/ 0,
                        /*_instantApp=*/ false,
                        /*_visibleToInstantApp=*/ false,
                        /*_exported=*/ false,
                        /*_applicationInfo=*/ null,
                        mPlatformCompat);
                filters.add(filter);
            }
            doReturn(filters.iterator()).when(rl).iterator();
            receiverLists.add(rl);
        }
        doReturn(receiverLists).when(receiverRecord).getReceiverLists();
        doReturn(receiverLists.size()).when(receiverRecord).numberOfReceivers();

        ReceiverList receiverList = new ReceiverList(
                mAms, app, TEST_PID, TEST_UID, TEST_USER_ID, /*_receiver=*/ null);

        String message = mBroadcastController.buildTooManyReceiversExceptionMessage(
                receiverList, TEST_PKG);

        assertThat(message).contains("Too many receivers, total of 100");
        assertThat(message).contains("Top 10 actions:");
        for (int i = 99; i >= 90; i--) {
            assertThat(message).contains("action" + i + ": " + (i + 1) + " receivers");
        }
        assertThat(message).contains("Top 10 receiver classes:");
        for (int i = 99; i >= 90; i--) {
            assertThat(message).contains("receiver" + i + ": " + (i + 1) + " receivers");
        }
    }
}
