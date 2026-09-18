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

package com.android.server.textclassifier.personalcontext;

import static com.android.server.textclassifier.personalcontext.PersonalContextBridge.DEFAULT_PRIORITY;
import static com.android.server.textclassifier.personalcontext.PersonalContextBridge.PERSONAL_CONTEXT_OVERRIDE;
import static com.android.server.textclassifier.personalcontext.PersonalContextBridge.PERSONAL_CONTEXT_PRIORITY;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import android.app.PendingIntent;
import android.app.RemoteAction;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.personalcontext.Flags;
import android.service.textclassifier.ITextClassifierCallback;
import android.service.textclassifier.TextClassifierService;
import android.view.textclassifier.TextClassification;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.server.textclassifier.personalcontext.PersonalContextBridge.Config;
import com.android.server.textclassifier.personalcontext.PersonalContextBridge.TextClassificationKey;

import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.TimeoutException;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PersonalContextBridgeImplTest {

    private static final long TEST_TIMEOUT_MILLIS = 50;
    private static final String TEST_SESSION_ID = "test-session-id";
    private static final String TEST_TEXT = "test-text";
    private static final TextClassification.Request TEST_REQUEST =
            new TextClassification.Request.Builder(TEST_TEXT, 0, TEST_TEXT.length()).build();
    private static final TextClassificationKey TEST_KEY =
            new TextClassificationKey(TEST_SESSION_ID, TEST_REQUEST);
    private static final String TEST_SYSTEM_ACTION_TITLE = "system-action";
    private static final String TEST_PERSONAL_CONTEXT_ACTION_TITLE = "personal-context-action";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Mock PersonalContextAsyncReceiver mPersonalContextAsyncReceiver;
    @Mock Icon mMockIcon;
    @Mock PendingIntent mMockPendingIntent;
    @Mock ITextClassifierCallback mSystemCallback;
    RemoteAction mSystemRemoteAction;
    RemoteAction mPersonalContextRemoteAction;

    private PersonalContextBridgeImpl mPersonalContextBridgeImpl;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        mSystemRemoteAction = createRemoteAction(TEST_SYSTEM_ACTION_TITLE);
        mPersonalContextRemoteAction = createRemoteAction(TEST_PERSONAL_CONTEXT_ACTION_TITLE);
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE,
        Flags.FLAG_ENABLE_TEXT_CLASSIFIER
    })
    public void testMergedCallback_defaultPriority_putsSystemActionFirst() throws Exception {
        givenPersonalContextActions(Lists.newArrayList(mPersonalContextRemoteAction));
        mPersonalContextBridgeImpl =
                new PersonalContextBridgeImpl(
                        new Config(TEST_TIMEOUT_MILLIS, DEFAULT_PRIORITY),
                        mPersonalContextAsyncReceiver);

        final ITextClassifierCallback mergedCallback =
                mPersonalContextBridgeImpl.wrap(TEST_SESSION_ID, TEST_REQUEST, mSystemCallback);
        mergedCallback.onSuccess(remoteActionsToResult(Lists.newArrayList(mSystemRemoteAction)));

        final Bundle mergedResult = captureMergedResult();
        assertThat(
                        ((TextClassification) TextClassifierService.getResponse(mergedResult))
                                .getActions())
                .containsExactly(mSystemRemoteAction, mPersonalContextRemoteAction)
                .inOrder();
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE,
        Flags.FLAG_ENABLE_TEXT_CLASSIFIER
    })
    public void testMergedCallback_defaultPrioritySameAction_dedupesActions() throws Exception {
        givenPersonalContextActions(Lists.newArrayList(mSystemRemoteAction));
        mPersonalContextBridgeImpl =
                new PersonalContextBridgeImpl(
                        new Config(TEST_TIMEOUT_MILLIS, DEFAULT_PRIORITY),
                        mPersonalContextAsyncReceiver);

        final ITextClassifierCallback mergedCallback =
                mPersonalContextBridgeImpl.wrap(TEST_SESSION_ID, TEST_REQUEST, mSystemCallback);
        mergedCallback.onSuccess(remoteActionsToResult(Lists.newArrayList(mSystemRemoteAction)));

        final Bundle mergedResult = captureMergedResult();
        assertThat(
                        ((TextClassification) TextClassifierService.getResponse(mergedResult))
                                .getActions())
                .containsExactly(mSystemRemoteAction);
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE,
        Flags.FLAG_ENABLE_TEXT_CLASSIFIER
    })
    public void testMergedCallback_personalContextPriority_putsPersonalContextActionFirst()
            throws Exception {
        givenPersonalContextActions(Lists.newArrayList(mPersonalContextRemoteAction));
        mPersonalContextBridgeImpl =
                new PersonalContextBridgeImpl(
                        new Config(TEST_TIMEOUT_MILLIS, PERSONAL_CONTEXT_PRIORITY),
                        mPersonalContextAsyncReceiver);

        final ITextClassifierCallback mergedCallback =
                mPersonalContextBridgeImpl.wrap(TEST_SESSION_ID, TEST_REQUEST, mSystemCallback);
        mergedCallback.onSuccess(remoteActionsToResult(Lists.newArrayList(mSystemRemoteAction)));

        final Bundle mergedResult = captureMergedResult();
        assertThat(
                        ((TextClassification) TextClassifierService.getResponse(mergedResult))
                                .getActions())
                .containsExactly(mPersonalContextRemoteAction, mSystemRemoteAction)
                .inOrder();
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE,
        Flags.FLAG_ENABLE_TEXT_CLASSIFIER
    })
    public void testMergedCallback_personalContextPrioritySameAction_dedupesActions()
            throws Exception {
        givenPersonalContextActions(Lists.newArrayList(mSystemRemoteAction));
        mPersonalContextBridgeImpl =
                new PersonalContextBridgeImpl(
                        new Config(TEST_TIMEOUT_MILLIS, PERSONAL_CONTEXT_PRIORITY),
                        mPersonalContextAsyncReceiver);

        final ITextClassifierCallback mergedCallback =
                mPersonalContextBridgeImpl.wrap(TEST_SESSION_ID, TEST_REQUEST, mSystemCallback);
        mergedCallback.onSuccess(remoteActionsToResult(Lists.newArrayList(mSystemRemoteAction)));

        final Bundle mergedResult = captureMergedResult();
        assertThat(
                        ((TextClassification) TextClassifierService.getResponse(mergedResult))
                                .getActions())
                .containsExactly(mSystemRemoteAction);
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE,
        Flags.FLAG_ENABLE_TEXT_CLASSIFIER
    })
    public void testMergedCallback_personalContextOverride_putsOnlyPersonalContextAction()
            throws Exception {
        givenPersonalContextActions(Lists.newArrayList(mPersonalContextRemoteAction));
        mPersonalContextBridgeImpl =
                new PersonalContextBridgeImpl(
                        new Config(TEST_TIMEOUT_MILLIS, PERSONAL_CONTEXT_OVERRIDE),
                        mPersonalContextAsyncReceiver);

        final ITextClassifierCallback mergedCallback =
                mPersonalContextBridgeImpl.wrap(TEST_SESSION_ID, TEST_REQUEST, mSystemCallback);
        mergedCallback.onSuccess(remoteActionsToResult(Lists.newArrayList(mSystemRemoteAction)));

        final Bundle mergedResult = captureMergedResult();
        assertThat(
                        ((TextClassification) TextClassifierService.getResponse(mergedResult))
                                .getActions())
                .containsExactly(mPersonalContextRemoteAction);
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE,
        Flags.FLAG_ENABLE_TEXT_CLASSIFIER
    })
    public void testMergedCallback_personalContextOverrideNoPersonalContext_putSystemAction()
            throws Exception {
        givenPersonalContextActions(Lists.newArrayList());
        mPersonalContextBridgeImpl =
                new PersonalContextBridgeImpl(
                        new Config(TEST_TIMEOUT_MILLIS, PERSONAL_CONTEXT_OVERRIDE),
                        mPersonalContextAsyncReceiver);

        final ITextClassifierCallback mergedCallback =
                mPersonalContextBridgeImpl.wrap(TEST_SESSION_ID, TEST_REQUEST, mSystemCallback);
        mergedCallback.onSuccess(remoteActionsToResult(Lists.newArrayList(mSystemRemoteAction)));

        final Bundle mergedResult = captureMergedResult();
        assertThat(
                        ((TextClassification) TextClassifierService.getResponse(mergedResult))
                                .getActions())
                .containsExactly(mSystemRemoteAction);
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_PERSONAL_CONTEXT_SERVICE,
        Flags.FLAG_ENABLE_TEXT_CLASSIFIER
    })
    public void testMergedCallback_personalContextOverridePersonalContextErrored_putSystemAction()
            throws Exception {
        givenPersonalContextErrored();
        mPersonalContextBridgeImpl =
                new PersonalContextBridgeImpl(
                        new Config(TEST_TIMEOUT_MILLIS, PERSONAL_CONTEXT_OVERRIDE),
                        mPersonalContextAsyncReceiver);

        final ITextClassifierCallback mergedCallback =
                mPersonalContextBridgeImpl.wrap(TEST_SESSION_ID, TEST_REQUEST, mSystemCallback);
        mergedCallback.onSuccess(remoteActionsToResult(Lists.newArrayList(mSystemRemoteAction)));

        final Bundle mergedResult = captureMergedResult();
        assertThat(
                        ((TextClassification) TextClassifierService.getResponse(mergedResult))
                                .getActions())
                .containsExactly(mSystemRemoteAction);
    }

    @NonNull
    private Bundle captureMergedResult() throws RemoteException {
        ArgumentCaptor<Bundle> mergedResultCaptor = ArgumentCaptor.forClass(Bundle.class);
        verify(mSystemCallback).onSuccess(mergedResultCaptor.capture());
        return mergedResultCaptor.getValue();
    }

    private void givenPersonalContextActions(List<RemoteAction> personalContextRemoteActions) {
        final TextClassification personalContextTextClassification =
                new TextClassification.Builder()
                        .setText(TEST_TEXT)
                        .addActions(personalContextRemoteActions)
                        .build();
        doAnswer(
                        invocation -> {
                            OutcomeReceiver<TextClassification, TimeoutException> receiver =
                                    invocation.getArgument(1);
                            receiver.onResult(personalContextTextClassification);
                            return null;
                        })
                .when(mPersonalContextAsyncReceiver)
                .getAsync(eq(TEST_KEY), any());
    }

    private void givenPersonalContextErrored() {
        doAnswer(
                        invocation -> {
                            OutcomeReceiver<TextClassification, TimeoutException> receiver =
                                    invocation.getArgument(1);
                            receiver.onError(new TimeoutException());
                            return null;
                        })
                .when(mPersonalContextAsyncReceiver)
                .getAsync(eq(TEST_KEY), any());
    }

    @NonNull
    private Bundle remoteActionsToResult(List<RemoteAction> remoteActions) {
        final TextClassification textClassification =
                new TextClassification.Builder()
                        .setText(TEST_TEXT)
                        .addActions(remoteActions)
                        .build();
        Bundle result = new Bundle();
        TextClassifierService.putResponse(result, textClassification);
        return result;
    }

    private RemoteAction createRemoteAction(String title) {
        return new RemoteAction(mMockIcon, title, title, mMockPendingIntent);
    }
}
