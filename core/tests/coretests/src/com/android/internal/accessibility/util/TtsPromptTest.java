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

package com.android.internal.accessibility.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.media.Ringtone;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.testing.TestableContext;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Unit tests for TtsPrompt. */
@RunWith(AndroidJUnit4.class)
public class TtsPromptTest {

    @Rule
    public final TestableContext mContext =
            new TestableContext(InstrumentationRegistry.getInstrumentation().getContext());

    @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

    private @Mock Handler mHandler;
    private @Mock FrameworkObjectProvider mFrameworkObjectProvider;
    private @Mock TextToSpeech mTextToSpeech;
    private @Mock Ringtone mRingtone;
    private @Mock Voice mVoice;

    private TtsPrompt mTtsPrompt;

    @Before
    public void setUp() throws Exception {
        when(mFrameworkObjectProvider.getTextToSpeech(eq(mContext), any()))
                .thenReturn(mTextToSpeech);
        when(mFrameworkObjectProvider.getDefaultAccessibilityNotificationRingtone(eq(mContext)))
                .thenReturn(mRingtone);
        when(mTextToSpeech.getVoice()).thenReturn(mVoice);

        mTtsPrompt = new TtsPrompt(mContext, mHandler, mFrameworkObjectProvider, "text example");
    }

    @After
    public void tearDown() {
        if (mTtsPrompt != null) {
            mTtsPrompt.dismiss();
        }
    }

    @Test
    public void testOnInit_succeed_ttsSpeak() {
        configureHandlerCallbackInvocation();

        mTtsPrompt.onInit(TextToSpeech.SUCCESS);

        verify(mFrameworkObjectProvider).getTextToSpeech(any(), eq(mTtsPrompt));
        verify(mTextToSpeech).speak(any(), eq(TextToSpeech.QUEUE_FLUSH), any(), any());
        verify(mRingtone, times(0)).play();
    }

    @Test
    public void testOnInit_fail_ringtonePlayed() {
        configureHandlerCallbackInvocation();

        mTtsPrompt.onInit(TextToSpeech.ERROR);

        verify(mFrameworkObjectProvider).getTextToSpeech(any(), eq(mTtsPrompt));
        verify(mTextToSpeech, times(0)).speak(any(), anyInt(), any(), any());
        verify(mRingtone).play();
    }

    @Test
    public void testOnInit_retryAndSucceed_ttsSpeak() {
        configureHandlerCallbackInvocation();
        Set<String> features = new HashSet<>();
        features.add(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED);
        doReturn(features, Collections.emptySet()).when(mVoice).getFeatures();
        doReturn(TextToSpeech.LANG_NOT_SUPPORTED, TextToSpeech.LANG_AVAILABLE)
                .when(mTextToSpeech)
                .setLanguage(any());

        mTtsPrompt.onInit(TextToSpeech.SUCCESS);

        verify(mFrameworkObjectProvider).getTextToSpeech(any(), eq(mTtsPrompt));
        verify(mTextToSpeech).speak(any(), eq(TextToSpeech.QUEUE_FLUSH), any(), any());
        verify(mRingtone, times(0)).play();
    }

    private void configureHandlerCallbackInvocation() {
        doAnswer(
                        (InvocationOnMock invocation) -> {
                            Message m = (Message) invocation.getArguments()[0];
                            m.getCallback().run();
                            return true;
                        })
                .when(mHandler)
                .sendMessageAtTime(any(), anyLong());
    }
}
