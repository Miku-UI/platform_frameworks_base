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

import static com.google.common.truth.Truth.assertThat;

import android.app.assist.ActivityId;
import android.content.ComponentName;
import android.os.Binder;
import android.service.personalcontext.hint.ContentCaptureConversationEvent.ConversationEnterEvent;
import android.service.personalcontext.hint.ContentCaptureConversationEvent.ConversationExitEvent;
import android.service.personalcontext.hint.ContentCaptureConversationEvent.ConversationProcessingEvent;
import android.service.personalcontext.hint.ContentCaptureConversationEvent.ConversationUpdateEvent;
import android.view.autofill.AutofillId;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ContentCaptureConversationHintTest {
    private static final String CONVERSATION_SESSION_ID = "session_id";
    private static final AutofillId AUTOFILL_ID = new AutofillId(1);
    private static final Instant REFERENCE_TIME = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    private static final Instant CLIENT_EVENT_TIMESTAMP = REFERENCE_TIME.minusSeconds(1);
    private static final ChatMessageContentCaptureData CHAT_MESSAGE_CONTENT_CAPTURE_DATA =
            new ChatMessageContentCaptureData.Builder()
                    .setAutofillId(AUTOFILL_ID)
                    .setRawTimeString("12:00 PM")
                    .setRawDateString("Today")
                    .build();
    private static final ChatMessageData CHAT_MESSAGE_DATA =
            new ChatMessageData.Builder()
                    .setOutgoingMessage(true)
                    .setText("text")
                    .setAuthor("author")
                    .setReferenceTime(REFERENCE_TIME)
                    .setContentCaptureData(CHAT_MESSAGE_CONTENT_CAPTURE_DATA)
                    .build();

    @Test
    public void testConversationHint_enterEvent_parcelUnparcel() {
        final Instant enterTimestamp = REFERENCE_TIME;
        final Instant clientEventTimestamp = CLIENT_EVENT_TIMESTAMP;
        final ConversationEnterEvent enterEvent =
                new ConversationEnterEvent(
                        CONVERSATION_SESSION_ID, clientEventTimestamp, enterTimestamp);
        final ContentCaptureConversationHint hint =
                new ContentCaptureConversationHint.Builder(enterEvent).build();

        final ContextHint outputHint = ContextHintTestUtils.assertParcelUnparcel(hint);
        assertThat(outputHint).isInstanceOf(ContentCaptureConversationHint.class);
        final ContentCaptureConversationEvent outputEvent =
                ((ContentCaptureConversationHint) outputHint).getConversationEvent();
        assertThat(outputEvent).isInstanceOf(ConversationEnterEvent.class);

        final ConversationEnterEvent outputEnterEvent = (ConversationEnterEvent) outputEvent;
        assertThat(outputEnterEvent.getConversationSessionId()).isEqualTo(CONVERSATION_SESSION_ID);
        assertThat(outputEnterEvent.getTimestamp()).isEqualTo(enterTimestamp);
        assertThat(outputEnterEvent.getClientEventTimestamp()).isEqualTo(clientEventTimestamp);
    }

    @Test
    public void testConversationHint_exitEvent_parcelUnparcel() {
        final Instant exitTimestamp = REFERENCE_TIME;
        final Instant clientEventTimestamp = CLIENT_EVENT_TIMESTAMP;
        final ConversationExitEvent exitEvent =
                new ConversationExitEvent(
                        CONVERSATION_SESSION_ID, clientEventTimestamp, exitTimestamp);
        final ContentCaptureConversationHint hint =
                new ContentCaptureConversationHint.Builder(exitEvent).build();

        final ContextHint outputHint = ContextHintTestUtils.assertParcelUnparcel(hint);
        assertThat(outputHint).isInstanceOf(ContentCaptureConversationHint.class);
        final ContentCaptureConversationEvent outputEvent =
                ((ContentCaptureConversationHint) outputHint).getConversationEvent();
        assertThat(outputEvent).isInstanceOf(ConversationExitEvent.class);

        final ConversationExitEvent outputExitEvent = (ConversationExitEvent) outputEvent;
        assertThat(outputExitEvent.getConversationSessionId()).isEqualTo(CONVERSATION_SESSION_ID);
        assertThat(outputExitEvent.getTimestamp()).isEqualTo(exitTimestamp);
        assertThat(outputExitEvent.getClientEventTimestamp()).isEqualTo(clientEventTimestamp);
    }

    @Test
    public void testConversationHint_processingEvent_parcelUnparcel() {
        final Instant processingTimestamp = REFERENCE_TIME;
        final Instant clientEventTimestamp = CLIENT_EVENT_TIMESTAMP;
        final AutofillId messageAutofillId = new AutofillId(2);
        final ConversationProcessingEvent processingEvent =
                new ConversationProcessingEvent(
                        CONVERSATION_SESSION_ID,
                        clientEventTimestamp,
                        processingTimestamp,
                        messageAutofillId);
        final ContentCaptureConversationHint hint =
                new ContentCaptureConversationHint.Builder(processingEvent).build();

        final ContextHint outputHint = ContextHintTestUtils.assertParcelUnparcel(hint);
        assertThat(outputHint).isInstanceOf(ContentCaptureConversationHint.class);
        final ContentCaptureConversationEvent outputEvent =
                ((ContentCaptureConversationHint) outputHint).getConversationEvent();
        assertThat(outputEvent).isInstanceOf(ConversationProcessingEvent.class);

        final ConversationProcessingEvent outputProcessingEvent =
                (ConversationProcessingEvent) outputEvent;
        assertThat(outputProcessingEvent.getTimestamp())
                .isEqualTo(processingTimestamp);
        assertThat(outputProcessingEvent.getMessageAutofillId()).isEqualTo(messageAutofillId);
        assertThat(outputProcessingEvent.getConversationSessionId())
                .isEqualTo(CONVERSATION_SESSION_ID);
        assertThat(outputProcessingEvent.getClientEventTimestamp()).isEqualTo(clientEventTimestamp);
    }

    @Test
    public void testConversationHint_updateEvent_parcelUnparcel() {
        final ActivityId activityId = new ActivityId(1, null);
        final Instant processingStartTimestamp = CLIENT_EVENT_TIMESTAMP;
        final Instant processingEndTimestamp = REFERENCE_TIME;
        final Instant clientEventTimestamp = CLIENT_EVENT_TIMESTAMP;
        final Instant updateTimestamp = REFERENCE_TIME;
        final ComponentName componentName = new ComponentName("pkg", "cls");
        final AutofillId inputBoxAutofillId = new AutofillId(1);
        final ConversationData conversationData =
                new ConversationData.Builder()
                        .setKeyboardShown(true)
                        .setLastMessageFromTheUser(false)
                        .setProcessingStartTimestamp(processingStartTimestamp)
                        .setProcessingEndTimestamp(processingEndTimestamp)
                        .setComponentName(componentName)
                        .setInputBoxAutofillId(inputBoxAutofillId)
                        .setInputBoxText("inputBoxText")
                        .setConversationTitle("title")
                        .setHasNewMessage(true)
                        .setChatMessages(List.of(CHAT_MESSAGE_DATA))
                        .setActivityId(activityId)
                        .build();
        final ConversationUpdateEvent updateEvent =
                new ConversationUpdateEvent(
                        CONVERSATION_SESSION_ID,
                        clientEventTimestamp,
                        updateTimestamp,
                        conversationData);
        final ContentCaptureConversationHint hint =
                new ContentCaptureConversationHint.Builder(updateEvent).build();

        final ContextHint outputHint = ContextHintTestUtils.assertParcelUnparcel(hint);
        assertThat(outputHint).isInstanceOf(ContentCaptureConversationHint.class);
        final ContentCaptureConversationEvent outputEvent =
                ((ContentCaptureConversationHint) outputHint).getConversationEvent();
        assertThat(outputEvent).isInstanceOf(ConversationUpdateEvent.class);

        final ConversationUpdateEvent outputUpdateEvent = (ConversationUpdateEvent) outputEvent;
        assertThat(outputUpdateEvent.getConversationData()).isEqualTo(conversationData);
        assertThat(outputUpdateEvent.getTimestamp()).isEqualTo(REFERENCE_TIME);
        assertThat(outputUpdateEvent.getConversationSessionId()).isEqualTo(CONVERSATION_SESSION_ID);
        assertThat(outputUpdateEvent.getClientEventTimestamp()).isEqualTo(clientEventTimestamp);
    }

    @Test
    public void testHintSignature() throws GeneralSecurityException {
        final ActivityId activityId = new ActivityId(1, new Binder());
        final Instant processingStartTimestamp = CLIENT_EVENT_TIMESTAMP;
        final Instant processingEndTimestamp = REFERENCE_TIME;
        final Instant clientEventTimestamp = CLIENT_EVENT_TIMESTAMP;
        final Instant updateTimestamp = REFERENCE_TIME;
        final ComponentName componentName = new ComponentName("pkg", "cls");
        final AutofillId inputBoxAutofillId = new AutofillId(1);
        final ConversationData conversationData =
                new ConversationData.Builder()
                        .setKeyboardShown(true)
                        .setLastMessageFromTheUser(false)
                        .setProcessingStartTimestamp(processingStartTimestamp)
                        .setProcessingEndTimestamp(processingEndTimestamp)
                        .setComponentName(componentName)
                        .setInputBoxAutofillId(inputBoxAutofillId)
                        .setInputBoxText("inputBoxText")
                        .setConversationTitle("title")
                        .setChatMessages(List.of(CHAT_MESSAGE_DATA))
                        .setActivityId(activityId)
                        .setHasNewMessage(true)
                        .build();
        final ConversationUpdateEvent updateEvent =
                new ConversationUpdateEvent(
                        CONVERSATION_SESSION_ID,
                        clientEventTimestamp,
                        updateTimestamp,
                        conversationData);
        final ContentCaptureConversationHint hint =
                new ContentCaptureConversationHint.Builder(updateEvent).build();

        final SecretKeySpec key = ContextHintTestUtils.generateSignedHintKey();
        final PublishedContextHint hintWithSignature =
                new PublishedContextHint.Builder(hint, key).build();

        assertThat(hintWithSignature.getContextHint()).isEqualTo(hint);
    }
}
