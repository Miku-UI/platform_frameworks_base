/*
 * Copyright (C) 2026 The Android Open Source Project.
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

package android.libcore;

import androidx.benchmark.BenchmarkState;
import androidx.benchmark.junit4.BenchmarkRule;

import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class StringPerfTest {
    @Rule
    public BenchmarkRule mBenchmarkRule = new BenchmarkRule();

    // To avoid constant folding.
    private final String mShortString = "hello, world!";
    private final String mLongString;
    private final String mLongStringNonAscii;
    private final char mCharW = 'w';
    private final char mChar1 = '1';
    private final char mCharA = 'a';

    private volatile int mSideEffectSink;

    public StringPerfTest() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; ++i) {
            sb.append("0123456789");
        }
        mLongString = sb.toString();

        StringBuilder sb2 = new StringBuilder();
        for (int i = 0; i < 1000; ++i) {
            sb2.append("\u05D0\u05D1\u05D2\u05D3\u05D4\u05D5\u05D6\u05D7\u05D8\u05D9");
        }
        mLongStringNonAscii = sb2.toString();
    }

    @Test
    public void timeIndexOfShort() {
        final BenchmarkState state = mBenchmarkRule.getState();
        int ret = 0;
        while (state.keepRunning()) {
            ret += mShortString.indexOf(mCharW);
        }
        mSideEffectSink += ret;
    }

    @Test
    public void timeIndexOfLongStart() {
        final BenchmarkState state = mBenchmarkRule.getState();
        int ret = 0;
        while (state.keepRunning()) {
            ret += mLongString.indexOf(mChar1);
        }
        mSideEffectSink += ret;
    }

    @Test
    public void timeIndexOfLongMiss() {
        final BenchmarkState state = mBenchmarkRule.getState();
        int ret = 0;
        while (state.keepRunning()) {
            ret += mLongString.indexOf(mCharA);
        }
        mSideEffectSink += ret;
    }

    @Test
    public void timeIndexOfLongNonAsciiMiss() {
        final BenchmarkState state = mBenchmarkRule.getState();
        int ret = 0;
        while (state.keepRunning()) {
            ret += mLongStringNonAscii.indexOf(mCharA);
        }
        mSideEffectSink += ret;
    }
}
