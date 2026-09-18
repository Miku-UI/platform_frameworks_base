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

package android.service.personalcontext.hint;

import static android.app.NotificationManager.IMPORTANCE_DEFAULT;
import static android.service.personalcontext.hint.ContextHintTestUtils.assertParcelUnparcel;

import static com.google.common.truth.Truth.assertThat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.service.personalcontext.hint.NotificationEvent.NotificationEnqueuedEvent;
import android.service.personalcontext.hint.NotificationEvent.NotificationRemovedEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class NotificationHintTest {
    private static final StatusBarNotification STATUS_BAR_NOTIFICATION = new StatusBarNotification(
            "pkg", "opPkg", 0, "tag", 0, 0, new Notification(), UserHandle.CURRENT, null, 0);
    private static final NotificationChannel NOTIFICATION_CHANNEL =
            new NotificationChannel(
                    "TEST_CHANNEL_ID", "NotificationHintTest channel", IMPORTANCE_DEFAULT);
    private static final NotificationListenerService.RankingMap RANKING_MAP =
            new NotificationListenerService.RankingMap(new NotificationListenerService.Ranking[0]);

    @Test
    public void testNotificationHint_enqueuedEvent_parcelUnparcel() {
        final NotificationEnqueuedEvent enqueuedEvent = new NotificationEnqueuedEvent(
                STATUS_BAR_NOTIFICATION, NOTIFICATION_CHANNEL, RANKING_MAP);
        final NotificationHint hint = new NotificationHint.Builder(enqueuedEvent).build();

        final ContextHint outputHint = assertParcelUnparcel(hint);
        assertThat(outputHint).isInstanceOf(NotificationHint.class);
        final NotificationEvent outputEvent =
                ((NotificationHint) outputHint).getNotificationEvent();
        assertThat(outputEvent.getEventType()).isEqualTo(NotificationEvent.EVENT_TYPE_ENQUEUED);
        assertThat(outputEvent).isInstanceOf(NotificationEnqueuedEvent.class);

        final NotificationEnqueuedEvent outputEnqueuedEvent =
                (NotificationEnqueuedEvent) outputEvent;
        assertThat(outputEnqueuedEvent.getStatusBarNotification().getKey()).isEqualTo(
                STATUS_BAR_NOTIFICATION.getKey());
        assertThat(outputEnqueuedEvent.getNotificationChannel().getId()).isEqualTo(
                NOTIFICATION_CHANNEL.getId());
        assertThat(outputEnqueuedEvent.getRankingMap()).isEqualTo(RANKING_MAP);
    }

    @Test
    public void testNotificationHint_removedEvent_parcelUnparcel() {
        final int reason = NotificationListenerService.REASON_LISTENER_CANCEL;
        final NotificationRemovedEvent removedEvent = new NotificationRemovedEvent(
                STATUS_BAR_NOTIFICATION, RANKING_MAP, reason);
        final NotificationHint hint = new NotificationHint.Builder(removedEvent).build();

        final ContextHint outputHint = assertParcelUnparcel(hint);
        assertThat(outputHint).isInstanceOf(NotificationHint.class);
        final NotificationEvent outputEvent =
                ((NotificationHint) outputHint).getNotificationEvent();
        assertThat(outputEvent.getEventType()).isEqualTo(NotificationEvent.EVENT_TYPE_REMOVED);
        assertThat(outputEvent).isInstanceOf(NotificationRemovedEvent.class);

        final NotificationRemovedEvent outputRemovedEvent = (NotificationRemovedEvent) outputEvent;
        assertThat(outputRemovedEvent.getStatusBarNotification().getKey()).isEqualTo(
                STATUS_BAR_NOTIFICATION.getKey());
        assertThat(outputRemovedEvent.getReason()).isEqualTo(reason);
        assertThat(outputRemovedEvent.getRankingMap()).isEqualTo(RANKING_MAP);
    }
}
