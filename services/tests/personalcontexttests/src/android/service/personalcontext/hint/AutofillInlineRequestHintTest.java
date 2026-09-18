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

import static android.service.personalcontext.hint.ContextHintTestUtils.assertParcelUnparcel;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.assist.AssistStructure;
import android.content.ComponentName;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import android.service.autofill.FillEventHistory;
import android.util.Size;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.view.autofill.IAugmentedAutofillManagerClient;
import android.view.inputmethod.InlineSuggestionsRequest;
import android.widget.inline.InlinePresentationSpec;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AutofillInlineRequestHintTest {

    private static final InlinePresentationSpec INLINE_PRESENTATION_SPEC =
            new InlinePresentationSpec.Builder(new Size(100, 100), new Size(100, 100)).build();
    private static final Rect VIEW_RECT = new Rect(0, 0, 100, 100);

    @Mock private IAugmentedAutofillManagerClient mAugmentedAutofillManagerClient;
    @Mock private AssistStructure.ViewNodeParcelable mViewNodeParcelable;
    @Mock private AssistStructure.ViewNode mViewNode;

    private final IBinder mFakeBinder =
            new Binder() {
                @Override
                public IInterface queryLocalInterface(@NonNull String descriptor) {
                    return mAugmentedAutofillManagerClient;
                }
            };

    private AutoCloseable mMockCloseable;

    @Before
    public void setUp() throws RemoteException {
        mMockCloseable = MockitoAnnotations.openMocks(this);

        when(mAugmentedAutofillManagerClient.asBinder()).thenReturn(mFakeBinder);

        when(mAugmentedAutofillManagerClient.getViewNodeParcelable(any()))
                .thenReturn(mViewNodeParcelable);
        when(mViewNodeParcelable.getViewNode()).thenReturn(mViewNode);
        when(mAugmentedAutofillManagerClient.getViewCoordinates(any())).thenReturn(VIEW_RECT);
    }

    @After
    public void tearDown() throws Exception {
        mMockCloseable.close();
    }

    @Test
    public void testAutofillInlineRequestHint_parcelUnparcel() {
        final int sessionId = 6;
        final int taskId = 7;
        final Instant requestTimestamp = Instant.ofEpochSecond(500);
        final ComponentName activityComponent = new ComponentName("test_package", "class");
        final AutofillId focusedId = new AutofillId(3);
        final AutofillValue autofillValue = AutofillValue.forText("test");
        final InlineSuggestionsRequest inlineSuggestionsRequest =
                new InlineSuggestionsRequest.Builder(List.of(INLINE_PRESENTATION_SPEC)).build();
        final FillEventHistory fillEventHistory = getFillEventHistory(focusedId, sessionId);

        final AutofillInlineRequestHint hint =
                new AutofillInlineRequestHint.Builder(
                                sessionId,
                                taskId,
                                requestTimestamp,
                                activityComponent,
                                focusedId,
                                autofillValue,
                                inlineSuggestionsRequest,
                                mFakeBinder)
                        .setFillEventHistory(fillEventHistory)
                        .build();

        final ContextHint outputHint = assertParcelUnparcel(hint);
        assertThat(outputHint).isInstanceOf(AutofillInlineRequestHint.class);

        final AutofillInlineRequestHint outputAutofillHint = (AutofillInlineRequestHint) outputHint;
        assertThat(outputAutofillHint.getSessionId()).isEqualTo(sessionId);
        assertThat(outputAutofillHint.getTaskId()).isEqualTo(taskId);
        assertThat(outputAutofillHint.getRequestTimestamp()).isEqualTo(requestTimestamp);
        assertThat(outputAutofillHint.getActivityComponent()).isEqualTo(activityComponent);
        assertThat(outputAutofillHint.getFocusedId()).isEqualTo(focusedId);
        assertThat(outputAutofillHint.getAutofillValue()).isEqualTo(autofillValue);
        assertThat(outputAutofillHint.getInlineSuggestionsRequest())
                .isEqualTo(inlineSuggestionsRequest);
        assertThat(outputAutofillHint.getAugmentedAutofillProxy().asBinder())
                .isEqualTo(mFakeBinder);
        assertThat(outputAutofillHint.getFillEventHistory()).isEqualTo(fillEventHistory);

        assertThat(outputAutofillHint).isEqualTo(hint);
        assertThat(outputAutofillHint.hashCode()).isEqualTo(hint.hashCode());
    }

    @Test
    public void testAutofillInlineRequestHint_getAugmentedAutofillProxy() throws RemoteException {
        final int sessionId = 6;
        final int taskId = 7;
        final Instant requestTimestamp = Instant.ofEpochSecond(500);
        final ComponentName activityComponent = new ComponentName("test_package", "class");
        final AutofillId focusedId = new AutofillId(3);
        final AutofillValue autofillValue = AutofillValue.forText("test");
        final InlineSuggestionsRequest inlineSuggestionsRequest =
                new InlineSuggestionsRequest.Builder(List.of(INLINE_PRESENTATION_SPEC)).build();
        final FillEventHistory fillEventHistory = getFillEventHistory(focusedId, sessionId);

        final AutofillInlineRequestHint hint =
                new AutofillInlineRequestHint.Builder(
                                sessionId,
                                taskId,
                                requestTimestamp,
                                activityComponent,
                                focusedId,
                                autofillValue,
                                inlineSuggestionsRequest,
                                mFakeBinder)
                        .setFillEventHistory(fillEventHistory)
                        .build();

        final ContextHint outputHint = assertParcelUnparcel(hint);
        assertThat(outputHint).isInstanceOf(AutofillInlineRequestHint.class);

        final AutofillInlineRequestHint outputAutofillHint = (AutofillInlineRequestHint) outputHint;

        // Proxy method calls route to the proper calls.
        AssistStructure.ViewNode viewNode =
                outputAutofillHint.getAugmentedAutofillProxy().fetchFocusedViewNode(focusedId);
        assertThat(viewNode).isEqualTo(mViewNode);
        verify(mAugmentedAutofillManagerClient).getViewNodeParcelable(eq(focusedId));

        final Rect rect =
                outputAutofillHint.getAugmentedAutofillProxy().fetchViewCoordinates(focusedId);
        assertThat(rect).isEqualTo(VIEW_RECT);
        verify(mAugmentedAutofillManagerClient).getViewCoordinates(eq(focusedId));

        // Verify null values are handled properly.
        when(mAugmentedAutofillManagerClient.getViewNodeParcelable(any())).thenReturn(null);
        viewNode = outputAutofillHint.getAugmentedAutofillProxy().fetchFocusedViewNode(focusedId);
        assertThat(viewNode).isNull();
    }

    private static FillEventHistory getFillEventHistory(AutofillId focusedId, int sessionId) {
        FillEventHistory.Event event =
                new FillEventHistory.Event.Builder(
                                FillEventHistory.Event.TYPE_AUTHENTICATION_SELECTED)
                        .setFocusedId(focusedId)
                        .build();
        final FillEventHistory fillEventHistory = new FillEventHistory(sessionId, null);
        fillEventHistory.addEvent(event);
        return fillEventHistory;
    }
}
