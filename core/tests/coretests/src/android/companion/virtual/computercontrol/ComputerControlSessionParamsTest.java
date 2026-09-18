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

package android.companion.virtual.computercontrol;

import static android.companion.virtual.computercontrol.ComputerControlSessionParams.MIN_COMPUTER_CONTROL_VERSION_FOR_ANDROID_17;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.AppInteractionAttribution;
import android.app.Notification;
import android.app.PendingIntent;
import android.companion.DeviceId;
import android.companion.virtual.CompanionDeviceId;
import android.content.Intent;
import android.graphics.Color;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ComputerControlSessionParamsTest {

    private static final String SESSION_NAME = "ComputerControlSessionName";
    private static final String TARGET_PACKAGE_1 = "com.android.foo";
    private static final String TARGET_PACKAGE_2 = "com.android.bar";
    private static final List<String> TARGET_PACKAGE_NAMES =
            List.of(TARGET_PACKAGE_1, TARGET_PACKAGE_2);
    private static final AppInteractionAttribution APP_INTERACTION_ATTRIBUTION =
            new AppInteractionAttribution.Builder(
                    AppInteractionAttribution.INTERACTION_TYPE_USER_QUERY)
                    .build();
    private static final DeviceId DEVICE_ID = new DeviceId.Builder()
            .setCustomId(SESSION_NAME)
            .build();
    private static final CompanionDeviceId COMPANION_DEVICE_ID = new CompanionDeviceId(DEVICE_ID);
    private static final String NOTIFICATION_CHANNEL_ID = "TEST_CHANNEL_ID";
    private static final int NOTIFICATION_ID = 5;
    private static final String NOTIFICATION_TAG = "TEST_NOTIFICATION_TAG";
    private static final ComputerControlSessionParams.NotificationParams NOTIFICATION_PARAMS =
            new ComputerControlSessionParams.NotificationParams.Builder(
                    new Notification.Builder(getApplicationContext(), NOTIFICATION_CHANNEL_ID)
                            .setOngoing(true)
                            .setContentTitle("Hello")
                            .setRequestPromotedOngoing(true)
                            .setSmallIcon(android.R.drawable.sym_def_app_icon)
                            .setColor(Color.WHITE)
                            .build(), NOTIFICATION_ID)
                    .setNotificationTag(NOTIFICATION_TAG)
                    .build();

    @Test
    public void parcelable_shouldRecreateSuccessfully() {
        PendingIntent previewIntent =
                PendingIntent.getActivity(
                        getApplicationContext(),
                        0,
                        new Intent("PREVIEW"),
                        PendingIntent.FLAG_IMMUTABLE);

        ComputerControlSessionParams originalParams =
                new ComputerControlSessionParams.Builder()
                        .setName(SESSION_NAME)
                        .setTargetComputerControlVersion(
                                MIN_COMPUTER_CONTROL_VERSION_FOR_ANDROID_17)
                        .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                        .setPreviewIntent(previewIntent)
                        .setAppInteractionAttribution(APP_INTERACTION_ATTRIBUTION)
                        .setCompanionDeviceId(COMPANION_DEVICE_ID)
                        .setNotificationParams(NOTIFICATION_PARAMS)
                        .build();
        Parcel parcel = Parcel.obtain();
        originalParams.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ComputerControlSessionParams params =
                ComputerControlSessionParams.CREATOR.createFromParcel(parcel);
        assertThat(params.getName()).isEqualTo(SESSION_NAME);
        assertThat(params.getTargetComputerControlVersion())
                .isEqualTo(MIN_COMPUTER_CONTROL_VERSION_FOR_ANDROID_17);
        assertThat(params.getTargetPackageNames()).containsExactlyElementsIn(TARGET_PACKAGE_NAMES);
        assertThat(params.getPreviewIntent()).isEqualTo(previewIntent);
        assertThat(params.getAppInteractionAttribution()).isEqualTo(APP_INTERACTION_ATTRIBUTION);
        assertThat(params.getCompanionDeviceId().getDeviceId()).isEqualTo(DEVICE_ID);
        ComputerControlSessionParams.NotificationParams notificationParams =
                params.getNotificationParams();
        assertThat(notificationParams.getNotificationId()).isEqualTo(NOTIFICATION_ID);
        assertThat(notificationParams.getNotificationTag()).isEqualTo(NOTIFICATION_TAG);
        Notification notification = notificationParams.getNotification();
        assertThat(notification.getChannelId()).isEqualTo(NOTIFICATION_CHANNEL_ID);
        assertThat(notification.hasPromotableCharacteristics()).isTrue();
    }

    @Test
    public void parcelable_unsetPreviewIntent_shouldRecreateSuccessfully() {
        ComputerControlSessionParams originalParams =
                new ComputerControlSessionParams.Builder()
                        .setName(SESSION_NAME)
                        .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                        .setAppInteractionAttribution(APP_INTERACTION_ATTRIBUTION)
                        .build();
        Parcel parcel = Parcel.obtain();
        originalParams.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ComputerControlSessionParams params =
                ComputerControlSessionParams.CREATOR.createFromParcel(parcel);
        assertThat(params.getName()).isEqualTo(SESSION_NAME);
        assertThat(params.getTargetPackageNames()).containsExactlyElementsIn(TARGET_PACKAGE_NAMES);
        assertThat(params.getPreviewIntent()).isNull();
        assertThat(params.getCompanionDeviceId()).isNull();
    }

    @Test
    public void parcelable_unsetAppInteractionAttribution_shouldRecreateSuccessfully() {
        PendingIntent previewIntent =
                PendingIntent.getActivity(
                        getApplicationContext(),
                        0,
                        new Intent("PREVIEW"),
                        PendingIntent.FLAG_IMMUTABLE);
        ComputerControlSessionParams originalParams =
                new ComputerControlSessionParams.Builder()
                        .setName(SESSION_NAME)
                        .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                        .setPreviewIntent(previewIntent)
                        .setAppInteractionAttribution(null)
                        .build();
        Parcel parcel = Parcel.obtain();
        originalParams.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ComputerControlSessionParams params =
                ComputerControlSessionParams.CREATOR.createFromParcel(parcel);
        assertThat(params.getName()).isEqualTo(SESSION_NAME);
        assertThat(params.getTargetPackageNames()).containsExactlyElementsIn(TARGET_PACKAGE_NAMES);
        assertThat(params.getPreviewIntent()).isEqualTo(previewIntent);
        assertThat(params.getAppInteractionAttribution()).isNull();
    }

    @Test
    public void parcelable_unsetNotificationParams_shouldRecreateSuccessfully() {
        PendingIntent previewIntent =
                PendingIntent.getActivity(
                        getApplicationContext(),
                        0,
                        new Intent("PREVIEW"),
                        PendingIntent.FLAG_IMMUTABLE);
        ComputerControlSessionParams originalParams =
                new ComputerControlSessionParams.Builder()
                        .setName(SESSION_NAME)
                        .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                        .setPreviewIntent(previewIntent)
                        .build();
        Parcel parcel = Parcel.obtain();
        originalParams.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        ComputerControlSessionParams params =
                ComputerControlSessionParams.CREATOR.createFromParcel(parcel);
        assertThat(params.getName()).isEqualTo(SESSION_NAME);
        assertThat(params.getTargetPackageNames()).containsExactlyElementsIn(TARGET_PACKAGE_NAMES);
        assertThat(params.getPreviewIntent()).isEqualTo(previewIntent);
        assertThat(params.getNotificationParams()).isNull();
    }

    @Test
    public void setTooManyTargetPackageNames_targetComputerControlVersion5_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ComputerControlSessionParams.Builder()
                        .setName(SESSION_NAME)
                        .setTargetComputerControlVersion(
                                MIN_COMPUTER_CONTROL_VERSION_FOR_ANDROID_17)
                        .setNotificationParams(NOTIFICATION_PARAMS)
                        .setAppInteractionAttribution(APP_INTERACTION_ATTRIBUTION)
                        .setTargetPackageNames(
                                List.of("com.app1", "com.app2", "com.app3", "com.app4", "com.app5",
                                        "com.app6", "com.app7"))
                        .build());
    }

    @Test
    public void setTooManyTargetPackageNames_targetComputerControlUnderVersion5_doesNotThrow() {
        new ComputerControlSessionParams.Builder()
                .setName(SESSION_NAME)
                .setTargetComputerControlVersion(
                        MIN_COMPUTER_CONTROL_VERSION_FOR_ANDROID_17 - 1)
                .setTargetPackageNames(
                        List.of("com.app1", "com.app2", "com.app3", "com.app4", "com.app5",
                                "com.app6", "com.app7"))
                .build();
    }

    @Test
    public void build_unsetName_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ComputerControlSessionParams.Builder()
                                .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                                .build());
    }

    @Test
    public void build_unsetTargetPackageNames_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ComputerControlSessionParams.Builder().setName(SESSION_NAME).build());
    }

    @Test
    public void build_withNullPreviewIntent_setsPreviewIntentToNull() {
        ComputerControlSessionParams params =
                new ComputerControlSessionParams.Builder()
                        .setName(SESSION_NAME)
                        .setTargetComputerControlVersion(
                                MIN_COMPUTER_CONTROL_VERSION_FOR_ANDROID_17)
                        .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                        .setAppInteractionAttribution(APP_INTERACTION_ATTRIBUTION)
                        .setNotificationParams(NOTIFICATION_PARAMS)
                        .setPreviewIntent(null)
                        .build();

        assertThat(params.getPreviewIntent()).isNull();
    }

    @Test
    public void build_withNullAppInteractionAttribution_setsAppInteractionAttributionToNull() {
        ComputerControlSessionParams params =
                new ComputerControlSessionParams.Builder()
                        .setName(SESSION_NAME)
                        .setTargetComputerControlVersion(
                                MIN_COMPUTER_CONTROL_VERSION_FOR_ANDROID_17 - 1)
                        .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                        .setAppInteractionAttribution(null)
                        .build();

        // Ensure this doesn't throw for backwards compatibility with v4
        assertThat(params.getAppInteractionAttribution()).isNull();
    }

    @Test
    public void build_withNullCompanionDeviceId_setsCompanionDeviceIdToNull() {
        ComputerControlSessionParams params =
                new ComputerControlSessionParams.Builder()
                        .setName(SESSION_NAME)
                        .setTargetComputerControlVersion(
                                MIN_COMPUTER_CONTROL_VERSION_FOR_ANDROID_17)
                        .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                        .setCompanionDeviceId(null)
                        .setNotificationParams(NOTIFICATION_PARAMS)
                        .setAppInteractionAttribution(APP_INTERACTION_ATTRIBUTION)
                        .build();

        assertThat(params.getCompanionDeviceId()).isNull();
    }

    @Test
    public void build_withNullNotificationParams_setsNotificationParamsToNull() {
        ComputerControlSessionParams params = new ComputerControlSessionParams.Builder()
                .setName(SESSION_NAME)
                .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                .setAppInteractionAttribution(APP_INTERACTION_ATTRIBUTION)
                .setNotificationParams(null)
                .setTargetComputerControlVersion(MIN_COMPUTER_CONTROL_VERSION_FOR_ANDROID_17 - 1)
                .build();
        assertThat(params.getNotificationParams()).isNull();
    }

    @Test
    public void build_targetComputerControlVersion5_unsetAppInteractionAttribution_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ComputerControlSessionParams.Builder()
                                .setName(SESSION_NAME)
                                .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                                .setNotificationParams(NOTIFICATION_PARAMS)
                                .setTargetComputerControlVersion(
                                        MIN_COMPUTER_CONTROL_VERSION_FOR_ANDROID_17)
                                .build());
    }

    @Test
    public void build_targetComputerControlVersion5_unsetNotificationParams_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ComputerControlSessionParams.Builder()
                                .setName(SESSION_NAME)
                                .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                                .setAppInteractionAttribution(APP_INTERACTION_ATTRIBUTION)
                                .setTargetComputerControlVersion(
                                        MIN_COMPUTER_CONTROL_VERSION_FOR_ANDROID_17)
                                .build());
    }

    @Test
    public void build_withCompanionDeviceId_targetComputerControlVersionUnder5_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ComputerControlSessionParams.Builder()
                                .setName(SESSION_NAME)
                                .setTargetPackageNames(TARGET_PACKAGE_NAMES)
                                .setTargetComputerControlVersion(
                                        MIN_COMPUTER_CONTROL_VERSION_FOR_ANDROID_17 - 1)
                                .setCompanionDeviceId(COMPANION_DEVICE_ID)
                                .build());
    }

    @Test
    public void notificationParams_withoutPromotionalCharacteristics_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ComputerControlSessionParams.NotificationParams.Builder(
                                new Notification.Builder(getApplicationContext(),
                                        NOTIFICATION_CHANNEL_ID)
                                        .setSmallIcon(android.R.drawable.sym_def_app_icon)
                                        .setColor(Color.WHITE)
                                        .build(), NOTIFICATION_ID));
    }

    @Test
    public void notificationParams_withNullNotification_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new ComputerControlSessionParams.NotificationParams.Builder(null,
                                NOTIFICATION_ID));
    }
}
