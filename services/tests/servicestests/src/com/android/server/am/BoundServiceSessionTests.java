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

package com.android.server.am;

import static com.android.server.am.BoundServiceSession.MAX_UNIQUE_TAGS;
import static com.android.server.am.BoundServiceSession.OVERFLOW_TAG;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.platform.test.annotations.Presubmit;

import org.junit.Test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Test class for {@link BoundServiceSession}.
 *
 * Build/Install/Run:
 *  atest FrameworksServicesTests:BoundServiceSessionTests
 * Or
 *  atest FrameworksServicesTestsRavenwood_ProcessStateController:BoundServiceSessionTests
 */
@Presubmit
public class BoundServiceSessionTests {
    private static final String TEST_DEBUG_NAME = "test_bound_service_session";

    private final ConnectionRecord mEmptyConnectionRecord = new ConnectionRecord(null, null, null,
            0, 0, null, 0, null, null, null);
    private final BiConsumer<ConnectionRecord, Boolean> mMockConsumer = mock(BiConsumer.class);

    private BoundServiceSession getNewBoundServiceSessionForTest() {
        return new BoundServiceSession(mMockConsumer, new WeakReference<>(mEmptyConnectionRecord),
                TEST_DEBUG_NAME);
    }

    private static void assertSessionReset(BoundServiceSession session) {
        assertEquals(0, session.mTotal);
        assertEquals(0, session.mKeyByTag.size());
        assertEquals(0, session.mCountByKey.size());
    }

    @Test
    public void startingState() {
        final BoundServiceSession session = getNewBoundServiceSessionForTest();
        assertSessionReset(session);
    }

    @Test
    public void resetOnUndercount() {
        final BoundServiceSession session = getNewBoundServiceSessionForTest();
        final String[] testTags = {"test0", "test1"};

        session.binderTransactionStarting(testTags[0]);
        session.binderTransactionStarting(testTags[0]);
        session.binderTransactionStarting(testTags[0]);
        session.binderTransactionStarting(testTags[0]);

        final long token1 = session.binderTransactionStarting(testTags[1]);

        final int key0 = session.mKeyByTag.get(testTags[0]);
        final int key1 = session.mKeyByTag.get(testTags[1]);
        assertEquals(0, key0);
        assertEquals(1, key1);

        assertEquals(4, session.mCountByKey.get(key0));
        assertEquals(1, session.mCountByKey.get(key1));
        assertEquals(5, session.mTotal);

        session.binderTransactionCompleted(token1);

        assertEquals(4, session.mCountByKey.get(key0));
        assertEquals(0, session.mCountByKey.get(key1));
        assertEquals(4, session.mTotal);

        session.binderTransactionCompleted(token1);
        assertSessionReset(session);
    }

    @Test
    public void resetOnInvalidToken() {
        final BoundServiceSession session = getNewBoundServiceSessionForTest();
        final String testTag = "test";

        final long validToken = session.binderTransactionStarting(testTag);
        session.binderTransactionStarting(testTag);
        session.binderTransactionStarting(testTag);

        int key = session.mKeyByTag.get(testTag);
        assertEquals(0, key);
        assertEquals(3, session.mTotal);
        assertEquals(3, session.mCountByKey.get(key));

        session.binderTransactionCompleted(validToken + 1);
        assertSessionReset(session);

        session.binderTransactionStarting(testTag);
        session.binderTransactionStarting(testTag);

        key = session.mKeyByTag.get(testTag);
        assertEquals(0, key);
        assertEquals(2, session.mTotal);
        assertEquals(2, session.mCountByKey.get(key));

        session.binderTransactionCompleted(-1);
        assertSessionReset(session);
    }

    @Test
    public void tokenConsistency() {
        final BoundServiceSession session = getNewBoundServiceSessionForTest();

        final String[] testTags = {"test5", "test1", "test2"};

        final long token0 = session.binderTransactionStarting(testTags[0]);
        final long token1 = session.binderTransactionStarting(testTags[1]);
        final long token2 = session.binderTransactionStarting(testTags[2]);

        assertEquals(token0, session.binderTransactionStarting(testTags[0]));
        assertEquals(token1, session.binderTransactionStarting(testTags[1]));
        assertEquals(token2, session.binderTransactionStarting(testTags[2]));
    }

    @Test
    public void tokenDistinctness() {
        final BoundServiceSession session = getNewBoundServiceSessionForTest();

        final String[] tags = {"test3", "test1", "otherTag", "test2"};
        final List<Long> tokens = new ArrayList<>();

        for (String tag: tags) {
            final long token = session.binderTransactionStarting(tag);
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
        final BoundServiceSession session = getNewBoundServiceSessionForTest();
        assertEquals(0, session.mTotal);

        session.binderTransactionStarting("test");
        assertEquals(1, session.mTotal);
        verify(mMockConsumer).accept(mEmptyConnectionRecord, true);

        session.binderTransactionStarting("test");
        session.binderTransactionStarting("test");
        session.binderTransactionStarting("test");

        assertEquals(4, session.mTotal);
        verifyNoMoreInteractions(mMockConsumer);
    }

    @Test
    public void callsConsumerOnChangeFromOneToZero() {
        final BoundServiceSession session = getNewBoundServiceSessionForTest();
        final long token = session.binderTransactionStarting("test");
        assertEquals(1, session.mTotal);
        clearInvocations(mMockConsumer);

        session.binderTransactionStarting("test");
        session.binderTransactionStarting("test");
        assertEquals(3, session.mTotal);
        verify(mMockConsumer, never()).accept(any(ConnectionRecord.class), anyBoolean());

        session.binderTransactionCompleted(token);
        session.binderTransactionCompleted(token);
        assertEquals(1, session.mTotal);
        verify(mMockConsumer, never()).accept(any(ConnectionRecord.class), anyBoolean());

        session.binderTransactionCompleted(token);
        assertEquals(0, session.mTotal);
        verify(mMockConsumer).accept(mEmptyConnectionRecord, false);
    }

    @Test
    public void callsConsumerOnChangeFromManyToZero() {
        final BoundServiceSession session = getNewBoundServiceSessionForTest();
        session.binderTransactionStarting("test");
        clearInvocations(mMockConsumer);

        session.binderTransactionStarting("test");
        session.binderTransactionStarting("test");
        assertEquals(3, session.mTotal);
        verify(mMockConsumer, never()).accept(any(ConnectionRecord.class), anyBoolean());

        session.binderTransactionCompleted(-1);
        assertEquals(0, session.mTotal);
        verify(mMockConsumer).accept(mEmptyConnectionRecord, false);
    }

    @Test
    public void noCallToConsumerOnChangeFromZeroToZero() {
        final BoundServiceSession session = getNewBoundServiceSessionForTest();
        final long token = session.binderTransactionStarting("test");
        session.binderTransactionCompleted(token);

        assertEquals(0, session.mTotal);
        clearInvocations(mMockConsumer);

        session.binderTransactionCompleted(-1);
        assertEquals(0, session.mTotal);
        verify(mMockConsumer, never()).accept(any(ConnectionRecord.class), anyBoolean());
    }

    @Test
    public void keyKeeping() {
        final BoundServiceSession session = getNewBoundServiceSessionForTest();

        final String[] tags = {"test3", "test1", "otherTag", "test2"};
        final List<Integer> keys = new ArrayList<>();

        for (String tag: tags) {
            assertFalse(session.mKeyByTag.containsKey(tag));
            session.binderTransactionStarting(tag);

            final int key = session.mKeyByTag.get(tag);
            final int index = keys.indexOf(key);
            if (index >= 0) {
                fail("Duplicate key " + key + " found for tag " + tag
                        + ". Previously assigned to tag " + tags[index]);
            }
            keys.add(key);
        }

        // Ensure that keys don't change once assigned.
        for (int i = 0; i < tags.length; i++) {
            assertEquals(keys.get(i), session.mKeyByTag.get(tags[i]));
            session.binderTransactionStarting(tags[i]);
            assertEquals(keys.get(i), session.mKeyByTag.get(tags[i]));
        }
    }

    @Test
    public void countKeeping() {
        final BoundServiceSession session = getNewBoundServiceSessionForTest();

        final String[] tags = {"test3", "otherTag", "test0"};
        final List<Long> tokens = new ArrayList<>();
        final List<Integer> keys = new ArrayList<>();

        for (String tag: tags) {
            tokens.add(session.binderTransactionStarting(tag));
            keys.add(session.mKeyByTag.get(tag));
        }

        session.binderTransactionStarting(tags[1]);
        session.binderTransactionStarting(tags[2]);
        session.binderTransactionStarting(tags[2]);

        assertEquals(1, session.mCountByKey.get(keys.get(0)));
        assertEquals(2, session.mCountByKey.get(keys.get(1)));
        assertEquals(3, session.mCountByKey.get(keys.get(2)));
        assertEquals(6, session.mTotal);

        session.binderTransactionCompleted(tokens.get(0));
        session.binderTransactionCompleted(tokens.get(1));
        session.binderTransactionCompleted(tokens.get(2));

        assertEquals(0, session.mCountByKey.get(keys.get(0)));
        assertEquals(1, session.mCountByKey.get(keys.get(1)));
        assertEquals(2, session.mCountByKey.get(keys.get(2)));
        assertEquals(3, session.mTotal);

        session.binderTransactionCompleted(tokens.get(1));
        session.binderTransactionCompleted(tokens.get(2));

        assertEquals(0, session.mCountByKey.get(keys.get(0)));
        assertEquals(0, session.mCountByKey.get(keys.get(1)));
        assertEquals(1, session.mCountByKey.get(keys.get(2)));
        assertEquals(1, session.mTotal);

        session.binderTransactionStarting(tags[0]);
        session.binderTransactionStarting(tags[1]);

        assertEquals(1, session.mCountByKey.get(keys.get(0)));
        assertEquals(1, session.mCountByKey.get(keys.get(1)));
        assertEquals(1, session.mCountByKey.get(keys.get(2)));
        assertEquals(3, session.mTotal);

        session.binderTransactionCompleted(tokens.get(0));
        session.binderTransactionCompleted(tokens.get(1));
        session.binderTransactionCompleted(tokens.get(2));

        assertEquals(0, session.mCountByKey.get(keys.get(0)));
        assertEquals(0, session.mCountByKey.get(keys.get(1)));
        assertEquals(0, session.mCountByKey.get(keys.get(2)));
        assertEquals(0, session.mTotal);
    }

    @Test
    public void overflowTags() {
        final BoundServiceSession session = getNewBoundServiceSessionForTest();

        final List<String> uniqueTags = new ArrayList<>(MAX_UNIQUE_TAGS);
        for (int i = 0; i < MAX_UNIQUE_TAGS; i++) {
            final String tag = "unique_tag" + i;
            uniqueTags.add(tag);
            session.binderTransactionStarting(tag);
            assertEquals(i, (int) session.mKeyByTag.getOrDefault(tag, -1));
            assertEquals(1, session.mCountByKey.get(i));
        }
        assertEquals(MAX_UNIQUE_TAGS, session.mCountByKey.size());
        assertEquals(MAX_UNIQUE_TAGS, session.mKeyByTag.size());
        assertFalse(session.mKeyByTag.containsKey(OVERFLOW_TAG));

        final String[] overflowTags = {"test3", "4overflow", "test0"};

        final long overflowToken0 = session.binderTransactionStarting(overflowTags[0]);
        session.binderTransactionStarting(overflowTags[0]);
        final long overflowToken1 = session.binderTransactionStarting(overflowTags[1]);
        final long overflowToken2 = session.binderTransactionStarting(overflowTags[2]);
        session.binderTransactionStarting(overflowTags[2]);

        assertTrue(session.mKeyByTag.containsKey(OVERFLOW_TAG));
        assertEquals(MAX_UNIQUE_TAGS + 1, session.mCountByKey.size());
        assertEquals(MAX_UNIQUE_TAGS + 1, session.mKeyByTag.size());
        assertEquals(5, session.mCountByKey.get(MAX_UNIQUE_TAGS));
        for (final String overflowTag : overflowTags) {
            assertFalse(session.mKeyByTag.containsKey(overflowTag));
        }

        for (int i = 0; i < MAX_UNIQUE_TAGS; i++) {
            final String tag = uniqueTags.get(i);
            session.binderTransactionStarting(tag);
            assertEquals(2, session.mCountByKey.get(i));
            assertEquals(5, session.mCountByKey.get(MAX_UNIQUE_TAGS));
        }

        session.binderTransactionCompleted(overflowToken0);
        session.binderTransactionCompleted(overflowToken1);
        session.binderTransactionCompleted(overflowToken2);
        assertEquals(2, session.mCountByKey.get(MAX_UNIQUE_TAGS));
        for (int i = 0; i < MAX_UNIQUE_TAGS; i++) {
            assertEquals(2, session.mCountByKey.get(i));
        }
    }
}
