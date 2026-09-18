/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.service.messaging;

import android.net.Uri;
import android.service.messaging.IMessageUpgradeCallback;

/**
 * AlternativeMessageTransportService (AMTS) allows the default SMS app to handle the sending of a
 * message over a different transport (e.g. RCS etc.) other than the SMS or MMS. When an SMS or MMS
 * is being sent, the Telephony framework checks if the default SMS app has implemented this
 * service. If so, it binds to the service and requests the SMS app to send it over it's preferred
 * transport.
 *
 * <p>If the service accepts the upgrade request, the default SMS app is solely responsible for
 * sending the message to the recipient and moving it from the OUTBOX to SENT or FAILED folder.</p>
 *
 * @hide
 */
oneway interface IAlternativeMessageTransportService {
    /**
     * Enable the default SMS app to upgrade SMS/MMS messages. SMS app will have limited time to
     * respond to the upgrade request with either
     * {@link AlternativeMessageTransportService#UPGRADE_STATUS_ACCEPTED} or
     * {@link AlternativeMessageTransportService#UPGRADE_STATUS_REJECTED}. This service is not
     * expected to keep running while the message is being sent.
     *
     * @param contentUri the content URI of the SMS/MMS message.
     * @param callback the callback to be invoked with the upgrade status.
     */
    void upgradeMessage(in Uri contentUri, in IMessageUpgradeCallback callback);
}
