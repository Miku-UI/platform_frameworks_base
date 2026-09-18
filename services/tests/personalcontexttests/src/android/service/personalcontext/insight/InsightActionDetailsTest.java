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

package android.service.personalcontext.insight;

import static com.google.common.truth.Truth.assertThat;

import android.app.PendingIntent;
import android.app.RemoteAction;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Parcel;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link InsightActionDetails}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class InsightActionDetailsTest {
    @Test
    public void testPendingIntent_ParcelUnparcel() {
        final PendingIntent action = PendingIntent.getBroadcast(
                InstrumentationRegistry.getTargetContext(), 0, new Intent("TESTACTION"),
                PendingIntent.FLAG_IMMUTABLE);

        final InsightActionDetails originalActionDetails =
                new InsightActionDetails.Builder().setPendingIntent(action).build();

        final Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(originalActionDetails, 0);

        parcel.setDataPosition(0);
        InsightActionDetails fromParcel = parcel.readParcelable(null, InsightActionDetails.class);

        assertThat(fromParcel).isEqualTo(originalActionDetails);
    }

    @Test
    public void testRemoteAction_ParcelUnparcel() {
        final Icon icon = Icon.createWithContentUri("content://test");
        final String title = "title";
        final String description = "description";
        final PendingIntent action = PendingIntent.getBroadcast(
                InstrumentationRegistry.getTargetContext(), 0, new Intent("TESTACTION"),
                PendingIntent.FLAG_IMMUTABLE);
        final RemoteAction remoteAction = new RemoteAction(icon, title, description, action);

        final InsightActionDetails originalActionDetails =
                new InsightActionDetails.Builder().setRemoteAction(remoteAction).build();

        final Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(originalActionDetails, 0);

        parcel.setDataPosition(0);
        final InsightActionDetails fromParcel =
                parcel.readParcelable(null, InsightActionDetails.class);

        final RemoteAction parcelRemoteAction = fromParcel.getRemoteAction();

        assertThat(parcelRemoteAction.getIcon().getUri()).isEqualTo(icon.getUri());
        assertThat(parcelRemoteAction.getTitle()).isEqualTo(title);
        assertThat(parcelRemoteAction.getContentDescription()).isEqualTo(description);
        assertThat(parcelRemoteAction.getActionIntent()).isEqualTo(action);
    }
}
