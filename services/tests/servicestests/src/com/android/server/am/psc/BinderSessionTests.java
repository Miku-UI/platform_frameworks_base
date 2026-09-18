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

package com.android.server.am.psc;

import static com.android.server.am.psc.BinderSession.MAX_UNIQUE_TAGS;
import static com.android.server.am.psc.BinderSession.OVERFLOW_TAG;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.platform.test.annotations.Presubmit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Test class for {@link BinderSession}.
 * <p>
 * Build/Install/Run:
 *  atest FrameworksServicesTests:BinderSessionTests
 * Or
 *  atest FrameworksServicesTestsRavenwood_ProcessStateController:BinderSessionTests
 */
@Presubmit
@RunWith(MockitoJUnitRunner.class)
public class BinderSessionTests {
    @Rule
    public final MockitoRule mocks = MockitoJUnit.rule();
    private static final String TAG_0 = "test0";
    private static final String TAG_1 = "test1";
    private static final String TAG_2 = "test2";
    private static final String TAG_3 = "test3";
    private static final String TAG_4 = "test4";
    private static final String TAG_5 = "test5";
    private static final String OTHER_TAG = "otherTag";
    private static final String TEST_TAG = "test";

    private final Object mAnyObject = new Object();
    @Mock
    private BiConsumer<Object, Boolean> mMockConsumer;

    private BinderSession<?> mSession;

    @Before
    public void setUp() {
        mSession = new TestBinderSession(mMockConsumer, mAnyObject);
    }

    @Test
    public void startingState() {
        assertSessionReset(mSession);
    }

    @Test
    public void resetOnUndercount() {
        final String[] testTags = {TAG_0, TAG_1};

        mSession.binderTransactionStarting(testTags[0]);
        mSession.binderTransactionStarting(testTags[0]);
        mSession.binderTransactionStarting(testTags[0]);
        mSession.binderTransactionStarting(testTags[0]);

        final long token1 = mSession.binderTransactionStarting(testTags[1]);

        final int key0 = mSession.mKeyByTag.get(testTags[0]);
        final int key1 = mSession.mKeyByTag.get(testTags[1]);
        assertThat(key0).isEqualTo(0);
        assertThat(key1).isEqualTo(1);

        assertThat(mSession.mCountByKey.get(key0)).isEqualTo(4);
        assertThat(mSession.mCountByKey.get(key1)).isEqualTo(1);
        assertThat(mSession.mTotal).isEqualTo(5);

        mSession.binderTransactionCompleted(token1);

        assertThat(mSession.mCountByKey.get(key0)).isEqualTo(4);
        assertThat(mSession.mCountByKey.get(key1)).isEqualTo(0);
        assertThat(mSession.mTotal).isEqualTo(4);

        mSession.binderTransactionCompleted(token1);
        assertSessionReset(mSession);
    }

    @Test
    public void resetOnInvalidToken() {
        final String testTag = TEST_TAG;

        final long validToken = mSession.binderTransactionStarting(testTag);
        mSession.binderTransactionStarting(testTag);
        mSession.binderTransactionStarting(testTag);

        int key = mSession.mKeyByTag.get(testTag);
        assertThat(key).isEqualTo(0);
        assertThat(mSession.mTotal).isEqualTo(3);
        assertThat(mSession.mCountByKey.get(key)).isEqualTo(3);

        mSession.binderTransactionCompleted(validToken + 1);
        assertSessionReset(mSession);

        mSession.binderTransactionStarting(testTag);
        mSession.binderTransactionStarting(testTag);

        key = mSession.mKeyByTag.get(testTag);
        assertThat(key).isEqualTo(0);
        assertThat(mSession.mTotal).isEqualTo(2);
        assertThat(mSession.mCountByKey.get(key)).isEqualTo(2);

        mSession.binderTransactionCompleted(-1);
        assertSessionReset(mSession);
    }

    @Test
    public void tokenConsistency() {
        final String[] testTags = {TAG_5, TAG_1, TAG_2};

        final long token0 = mSession.binderTransactionStarting(testTags[0]);
        final long token1 = mSession.binderTransactionStarting(testTags[1]);
        final long token2 = mSession.binderTransactionStarting(testTags[2]);

        assertThat(mSession.binderTransactionStarting(testTags[0])).isEqualTo(token0);
        assertThat(mSession.binderTransactionStarting(testTags[1])).isEqualTo(token1);
        assertThat(mSession.binderTransactionStarting(testTags[2])).isEqualTo(token2);
    }

    @Test
    public void tokenDistinctness() {
        final String[] tags = {TAG_3, TAG_1, OTHER_TAG, TAG_2};
        final List<Long> tokens = new ArrayList<>();

        for (String tag: tags) {
            final long token = mSession.binderTransactionStarting(tag);
            final int index = tokens.indexOf(token);
            if (index >= 0) {
                fail("Duplicate token " + token + " found for tag " + tag
                        + ". Previously assigned to tag " + tags[index]);
            }
            tokens.add(token);
        }
    }

    @Test
    public void callsConsumerOnChangeFromZero() {
        assertThat(mSession.mTotal).isEqualTo(0);

        mSession.binderTransactionStarting(TEST_TAG);
        assertThat(mSession.mTotal).isEqualTo(1);
        verify(mMockConsumer).accept(mAnyObject, true);

        mSession.binderTransactionStarting(TEST_TAG);
        mSession.binderTransactionStarting(TEST_TAG);
        mSession.binderTransactionStarting(TEST_TAG);

        assertThat(mSession.mTotal).isEqualTo(4);
        verifyNoMoreInteractions(mMockConsumer);
    }

    @Test
    public void callsConsumerOnChangeFromOneToZero() {
        final long token = mSession.binderTransactionStarting(TEST_TAG);
        assertThat(mSession.mTotal).isEqualTo(1);
        clearInvocations(mMockConsumer);

        mSession.binderTransactionStarting(TEST_TAG);
        mSession.binderTransactionStarting(TEST_TAG);
        assertThat(mSession.mTotal).isEqualTo(3);
        verify(mMockConsumer, never()).accept(any(Object.class), anyBoolean());

        mSession.binderTransactionCompleted(token);
        mSession.binderTransactionCompleted(token);
        assertThat(mSession.mTotal).isEqualTo(1);
        verify(mMockConsumer, never()).accept(any(Object.class), anyBoolean());

        mSession.binderTransactionCompleted(token);
        assertThat(mSession.mTotal).isEqualTo(0);
        verify(mMockConsumer).accept(mAnyObject, false);
    }

    @Test
    public void callsConsumerOnChangeFromManyToZero() {
        mSession.binderTransactionStarting(TEST_TAG);
        clearInvocations(mMockConsumer);

        mSession.binderTransactionStarting(TEST_TAG);
        mSession.binderTransactionStarting(TEST_TAG);
        assertThat(mSession.mTotal).isEqualTo(3);
        verify(mMockConsumer, never()).accept(any(Object.class), anyBoolean());

        mSession.binderTransactionCompleted(-1);
        assertThat(mSession.mTotal).isEqualTo(0);
        verify(mMockConsumer).accept(mAnyObject, false);
    }

    @Test
    public void noCallToConsumerOnChangeFromZeroToZero() {
        final long token = mSession.binderTransactionStarting(TEST_TAG);
        mSession.binderTransactionCompleted(token);

        assertThat(mSession.mTotal).isEqualTo(0);
        clearInvocations(mMockConsumer);

        mSession.binderTransactionCompleted(-1);
        assertThat(mSession.mTotal).isEqualTo(0);
        verify(mMockConsumer, never()).accept(any(Object.class), anyBoolean());
    }

    @Test
    public void keyKeeping() {
        final String[] tags = {TAG_3, TAG_1, OTHER_TAG, TAG_2};
        final List<Integer> keys = new ArrayList<>();

        for (String tag: tags) {
            assertThat(mSession.mKeyByTag).doesNotContainKey(tag);
            mSession.binderTransactionStarting(tag);

            final int key = mSession.mKeyByTag.get(tag);
            final int index = keys.indexOf(key);
            if (index >= 0) {
                fail("Duplicate key " + key + " found for tag " + tag
                        + ". Previously assigned to tag " + tags[index]);
            }
            keys.add(key);
        }

        // Ensure that keys don't change once assigned.
        for (int i = 0; i < tags.length; i++) {
            assertThat(mSession.mKeyByTag.get(tags[i])).isEqualTo(keys.get(i));
            mSession.binderTransactionStarting(tags[i]);
            assertThat(mSession.mKeyByTag.get(tags[i])).isEqualTo(keys.get(i));
        }
    }

    @Test
    public void countKeeping() {
        final String[] tags = {TAG_3, OTHER_TAG, TAG_0};
        final List<Long> tokens = new ArrayList<>();
        final List<Integer> keys = new ArrayList<>();

        for (String tag: tags) {
            tokens.add(mSession.binderTransactionStarting(tag));
            keys.add(mSession.mKeyByTag.get(tag));
        }

        mSession.binderTransactionStarting(tags[1]);
        mSession.binderTransactionStarting(tags[2]);
        mSession.binderTransactionStarting(tags[2]);

        assertSessionCounts(keys, 1, 2, 3);
        assertThat(mSession.mTotal).isEqualTo(6);

        mSession.binderTransactionCompleted(tokens.get(0));
        mSession.binderTransactionCompleted(tokens.get(1));
        mSession.binderTransactionCompleted(tokens.get(2));

        assertSessionCounts(keys, 0, 1, 2);
        assertThat(mSession.mTotal).isEqualTo(3);

        mSession.binderTransactionCompleted(tokens.get(1));
        mSession.binderTransactionCompleted(tokens.get(2));

        assertSessionCounts(keys, 0, 0, 1);
        assertThat(mSession.mTotal).isEqualTo(1);

        mSession.binderTransactionStarting(tags[0]);
        mSession.binderTransactionStarting(tags[1]);

        assertSessionCounts(keys, 1, 1, 1);
        assertThat(mSession.mTotal).isEqualTo(3);

        mSession.binderTransactionCompleted(tokens.get(0));
        mSession.binderTransactionCompleted(tokens.get(1));
        mSession.binderTransactionCompleted(tokens.get(2));

        assertSessionCounts(keys, 0, 0, 0);
        assertThat(mSession.mTotal).isEqualTo(0);
    }

    @Test
    public void overflowTags() {
        final List<String> uniqueTags = new ArrayList<>(MAX_UNIQUE_TAGS);
        for (int i = 0; i < MAX_UNIQUE_TAGS; i++) {
            final String tag = "unique_tag" + i;
            uniqueTags.add(tag);
            mSession.binderTransactionStarting(tag);
            assertThat(mSession.mKeyByTag.get(tag).intValue()).isEqualTo(i);
            assertThat(mSession.mCountByKey.get(i)).isEqualTo(1);
        }
        assertThat(mSession.mCountByKey.size()).isEqualTo(MAX_UNIQUE_TAGS);
        assertThat(mSession.mKeyByTag).hasSize(MAX_UNIQUE_TAGS);
        assertThat(mSession.mKeyByTag).doesNotContainKey(OVERFLOW_TAG);

        final String[] overflowTags = {TAG_3, TAG_4, TAG_0};

        final long overflowToken0 = mSession.binderTransactionStarting(overflowTags[0]);
        mSession.binderTransactionStarting(overflowTags[0]);
        final long overflowToken1 = mSession.binderTransactionStarting(overflowTags[1]);
        final long overflowToken2 = mSession.binderTransactionStarting(overflowTags[2]);
        mSession.binderTransactionStarting(overflowTags[2]);

        assertThat(mSession.mKeyByTag).containsKey(OVERFLOW_TAG);
        assertThat(mSession.mCountByKey.size()).isEqualTo(MAX_UNIQUE_TAGS + 1);
        assertThat(mSession.mKeyByTag).hasSize(MAX_UNIQUE_TAGS + 1);
        assertThat(mSession.mCountByKey.get(MAX_UNIQUE_TAGS)).isEqualTo(5);
        for (final String overflowTag : overflowTags) {
            assertThat(mSession.mKeyByTag).doesNotContainKey(overflowTag);
        }

        for (int i = 0; i < MAX_UNIQUE_TAGS; i++) {
            final String tag = uniqueTags.get(i);
            mSession.binderTransactionStarting(tag);
            assertThat(mSession.mCountByKey.get(i)).isEqualTo(2);
            assertThat(mSession.mCountByKey.get(MAX_UNIQUE_TAGS)).isEqualTo(5);
        }

        mSession.binderTransactionCompleted(overflowToken0);
        mSession.binderTransactionCompleted(overflowToken1);
        mSession.binderTransactionCompleted(overflowToken2);
        assertThat(mSession.mCountByKey.get(MAX_UNIQUE_TAGS)).isEqualTo(2);
        for (int i = 0; i < MAX_UNIQUE_TAGS; i++) {
            assertThat(mSession.mCountByKey.get(i)).isEqualTo(2);
        }
    }

    /**
     * Asserts that the internal state of the session is reset.
     */
    private static void assertSessionReset(BinderSession<?> session) {
        assertThat(session.mTotal).isEqualTo(0);
        assertThat(session.mKeyByTag).isEmpty();
        assertThat(session.mCountByKey.size()).isEqualTo(0);
    }

    /**
     * Asserts that the internal counts of the session are as expected.
     *
     * @param keys                      A list of keys to check the counts for.
     * @param expectedCountsForEachKey  The expected counts for each key in {@code keys}.
     */
    private void assertSessionCounts(List<Integer> keys, int... expectedCountsForEachKey) {
        assertThat(expectedCountsForEachKey.length).isEqualTo(keys.size());
        for (int i = 0; i < expectedCountsForEachKey.length; i++) {
            assertThat(mSession.mCountByKey.get(keys.get(i))).isEqualTo(
                    expectedCountsForEachKey[i]);
        }
    }

    /** Minimal wrapper over abstract BinderSession */
    static class TestBinderSession extends BinderSession<Object> {
        TestBinderSession(BiConsumer<Object, Boolean> processStateUpdater, Object connection) {
            super(processStateUpdater, new WeakReference<>(connection), "test_binder_session");
        }

        @Override
        protected String getTraceTrack() {
            return "unused";
        }
    }
}
