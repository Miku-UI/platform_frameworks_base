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

package com.android.server.tv.watchdogservice;

import static com.android.server.tv.watchdogservice.TvWatchdogService.RESOURCE_OVERUSE_NOTIFICATION_BASE_ID;
import static com.android.server.tv.watchdogservice.TvWatchdogService.RESOURCE_OVERUSE_NOTIFICATION_MAX_OFFSET;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.HashSet;
import java.util.Set;

/** Tests for {@link TvWatchdogService}. */
@RunWith(AndroidJUnit4.class)
public final class TvWatchdogServiceTest {

    private static final String FAKE_PACKAGE_NAME_1 = "com.example.app1";
    private static final String FAKE_PACKAGE_NAME_2 = "com.example.app2";
    private static final int USER_ID_0 = 0;
    private static final int USER_ID_10 = 10;

    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Mock private Context mMockContext;
    @Mock private NotificationManager mMockNotificationManager;

    @Captor private ArgumentCaptor<BroadcastReceiver> mReceiverCaptor;
    @Captor private ArgumentCaptor<IntentFilter> mFilterCaptor;

    private TvWatchdogService mService;
    private BroadcastReceiver mPackageRemovalReceiver;

    @Before
    public void setUp() {
        when(mMockContext.getSystemServiceName(NotificationManager.class))
                .thenReturn(Context.NOTIFICATION_SERVICE);
        when(mMockContext.getSystemService(Context.NOTIFICATION_SERVICE))
                .thenReturn(mMockNotificationManager);

        // This is the corrected line with anyInt()
        when(mMockContext.registerReceiver(
                mReceiverCaptor.capture(), mFilterCaptor.capture(), anyInt()))
                .thenReturn(null);

        mService = new TvWatchdogService(mMockContext);

        for (IntentFilter filter : mFilterCaptor.getAllValues()) {
            if (filter.hasAction(Intent.ACTION_PACKAGE_REMOVED)) {
                int index = mFilterCaptor.getAllValues().indexOf(filter);
                mPackageRemovalReceiver = mReceiverCaptor.getAllValues().get(index);
                break;
            }
        }
        assertNotNull("Package removal receiver should be registered", mPackageRemovalReceiver);
    }

    @Test
    public void testReserveNotificationSlot_firstReservation_succeeds() {
        String userPackageId =
                IoOveruseHandler.getUserPackageUniqueId(USER_ID_0, FAKE_PACKAGE_NAME_1);
        int notificationId = mService.reserveNotificationSlot(userPackageId);
        assertEquals(RESOURCE_OVERUSE_NOTIFICATION_BASE_ID, notificationId);
    }

    @Test
    public void testReserveNotificationSlot_duplicateRequest_returnsNegativeOne() {
        String userPackageId =
                IoOveruseHandler.getUserPackageUniqueId(USER_ID_0, FAKE_PACKAGE_NAME_1);

        int firstId = mService.reserveNotificationSlot(userPackageId);
        assertNotEquals(-1, firstId);

        int secondId = mService.reserveNotificationSlot(userPackageId);
        assertEquals(-1, secondId);
    }

    @Test
    public void testReserveNotificationSlot_allSlotsFull_returnsNegativeOne() {
        Set<String> reservedPackages = new HashSet<>();
        for (int i = 0; i < RESOURCE_OVERUSE_NOTIFICATION_MAX_OFFSET; i++) {
            String userPackageId =
                    IoOveruseHandler.getUserPackageUniqueId(USER_ID_0, "com.example.app" + i);

            int notificationId = mService.reserveNotificationSlot(userPackageId);

            assertNotEquals(-1, notificationId);
            assertTrue(reservedPackages.add(userPackageId));
        }

        String overflowPackageId =
                IoOveruseHandler.getUserPackageUniqueId(USER_ID_0, "com.example.overflow");
        int overflowId = mService.reserveNotificationSlot(overflowPackageId);
        assertEquals(-1, overflowId);
    }

    @Test
    public void onNotificationDismissed_clearsStateAndAllowsNewReservation() {
        String userPackageId =
                IoOveruseHandler.getUserPackageUniqueId(USER_ID_0, FAKE_PACKAGE_NAME_1);

        int notificationId = mService.reserveNotificationSlot(userPackageId);
        assertNotEquals(-1, notificationId);

        assertEquals(-1, mService.reserveNotificationSlot(userPackageId));

        mService.onNotificationDismissed(notificationId);

        int newNotificationId = mService.reserveNotificationSlot(userPackageId);
        assertNotEquals(-1, newNotificationId);
        assertNotEquals(notificationId, newNotificationId);
    }

    @Test
    public void onPackageRemoved_clearsStateAndCancelsNotification() {
        String userPackageId1 =
                IoOveruseHandler.getUserPackageUniqueId(USER_ID_0, FAKE_PACKAGE_NAME_1);
        int notificationId1 = mService.reserveNotificationSlot(userPackageId1);
        assertNotEquals(-1, notificationId1);

        String userPackageId2 =
                IoOveruseHandler.getUserPackageUniqueId(USER_ID_0, FAKE_PACKAGE_NAME_2);
        int notificationId2 = mService.reserveNotificationSlot(userPackageId2);
        assertNotEquals(-1, notificationId2);

        Intent removalIntent = new Intent(Intent.ACTION_PACKAGE_REMOVED);
        removalIntent.setData(Uri.parse("package:" + FAKE_PACKAGE_NAME_1));
        mPackageRemovalReceiver.onReceive(mMockContext, removalIntent);

        verify(mMockNotificationManager).cancel(notificationId1);
        verify(mMockNotificationManager, never()).cancel(notificationId2);

        int newNotificationId = mService.reserveNotificationSlot(userPackageId1);
        assertNotEquals(-1, newNotificationId);
    }

    @Test
    public void onPackageRemoved_multipleUsers_cleansUpAllNotificationsForPackage() {
        String userPackageIdUser0 =
                IoOveruseHandler.getUserPackageUniqueId(USER_ID_0, FAKE_PACKAGE_NAME_1);
        int notificationIdUser0 = mService.reserveNotificationSlot(userPackageIdUser0);
        String userPackageIdUser10 =
                IoOveruseHandler.getUserPackageUniqueId(USER_ID_10, FAKE_PACKAGE_NAME_1);
        int notificationIdUser10 = mService.reserveNotificationSlot(userPackageIdUser10);

        Intent removalIntent = new Intent(Intent.ACTION_PACKAGE_REMOVED);
        removalIntent.setData(Uri.parse("package:" + FAKE_PACKAGE_NAME_1));
        mPackageRemovalReceiver.onReceive(mMockContext, removalIntent);

        verify(mMockNotificationManager).cancel(notificationIdUser0);
        verify(mMockNotificationManager).cancel(notificationIdUser10);
    }
}
