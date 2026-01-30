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

package android.util;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import android.os.Handler;
import android.os.Looper;

import androidx.test.filters.SmallTest;

import com.android.internal.annotations.GuardedBy;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;


@SmallTest
public class ListenerGroupTest {

    private Object mInitialObject = new Object();

    private ListenerGroup<Object> mListenerGroup;

    @Before
    public void setUp() throws InterruptedException {
        mListenerGroup = new ListenerGroup<>(mInitialObject, new Handler(Looper.getMainLooper()));
    }

    @Test
    public void test_added_listener_gets_initial_value() {
        final int valueCount = 1;
        final ValueListener valueListener = new ValueListener(valueCount);

        mListenerGroup.addListener(Runnable::run, valueListener);

        final boolean waitCompleted = valueListener.waitForValues();
        List<Object> values = valueListener.getValues();

        assertTrue("waitForValues did not complete.", waitCompleted);
        assertEquals("Value count does not match.", valueCount, values.size());
        assertEquals("First value does not match initial value", mInitialObject,
                valueListener.getValues().getFirst());
    }

    @Test
    public void test_added_listener_gets_receives_updates() {
        int valueCount = 2;
        Object nextValue = new Object();
        ValueListener valueListener = new ValueListener(2);

        mListenerGroup.addListener(Runnable::run, valueListener);
        mListenerGroup.accept(nextValue);

        boolean waitCompleted = valueListener.waitForValues();
        List<Object> values = valueListener.getValues();

        assertTrue("waitForValues did not complete.", waitCompleted);
        assertEquals("Value count does not match.", valueCount, values.size());
        assertEquals("Next value not received", nextValue,
                valueListener.getValues().getLast());
    }

    @Test
    public void test_removed_listener_stops_receiving_updates() {
        final int valueCount = 1;
        Object nextValue = new Object();
        ValueListener valueListener = new ValueListener(valueCount);
        ValueListener stopListener = new ValueListener(valueCount);

        mListenerGroup.addListener(Runnable::run, valueListener);
        mListenerGroup.removeListener(valueListener);

        mListenerGroup.accept(nextValue);
        mListenerGroup.addListener(Runnable::run, stopListener);

        boolean waitCompleted = valueListener.waitForValues() && stopListener.waitForValues();
        List<Object> values = valueListener.getValues();

        assertTrue("waitForValues did not complete.", waitCompleted);
        assertEquals("Value count does not match.", valueCount, values.size());
        assertEquals("Incorrect values received.", mInitialObject,
                values.getFirst());
        assertEquals("stopListener must receive next value", nextValue,
                stopListener.getValues().getFirst());
    }

    private static final class ValueListener implements Consumer<Object> {

        private final Object mLock = new Object();

        @GuardedBy("mLock")
        private final List<Object> mValues = new ArrayList<>();

        private final CountDownLatch mCountDownLatch;

        ValueListener(int valueCount) {
            mCountDownLatch = new CountDownLatch(valueCount);
        }

        @Override
        public void accept(Object o) {
            synchronized (mLock) {
                mValues.add(Objects.requireNonNull(o));
                mCountDownLatch.countDown();
            }
        }

        public boolean waitForValues() {
            try {
                return mCountDownLatch.await(16, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return false;
            }

        }

        public List<Object> getValues() {
            synchronized (mLock) {
                return new ArrayList<>(mValues);
            }
        }
    }
}
