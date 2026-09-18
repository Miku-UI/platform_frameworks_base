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

package android.libcore;

import androidx.benchmark.BenchmarkState;
import androidx.benchmark.junit4.BenchmarkRule;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Random;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class ArraysPerfTest {

    @Rule public BenchmarkRule mBenchmarkRule = new BenchmarkRule();

    private volatile boolean mSink;

    private static final int ARRAY_LENGTH = 1024 * 1024; // 1MB elements roughly

    // Primitives
    private final byte[] byteA = new byte[ARRAY_LENGTH];
    private final byte[] byteB = new byte[ARRAY_LENGTH];
    private final short[] shortA = new short[ARRAY_LENGTH];
    private final short[] shortB = new short[ARRAY_LENGTH];
    private final int[] intA = new int[ARRAY_LENGTH];
    private final int[] intB = new int[ARRAY_LENGTH];
    private final long[] longA = new long[ARRAY_LENGTH];
    private final long[] longB = new long[ARRAY_LENGTH];
    private final char[] charA = new char[ARRAY_LENGTH];
    private final char[] charB = new char[ARRAY_LENGTH];
    private final float[] floatA = new float[ARRAY_LENGTH];
    private final float[] floatB = new float[ARRAY_LENGTH];
    private final double[] doubleA = new double[ARRAY_LENGTH];
    private final double[] doubleB = new double[ARRAY_LENGTH];
    private final boolean[] booleanA = new boolean[ARRAY_LENGTH];
    private final boolean[] booleanB = new boolean[ARRAY_LENGTH];

    public ArraysPerfTest() {
        Random r = new Random(0);
        r.nextBytes(byteA);
        System.arraycopy(byteA, 0, byteB, 0, ARRAY_LENGTH);

        for (int i = 0; i < ARRAY_LENGTH; i++) {
            shortA[i] = (short) r.nextInt();
            intA[i] = r.nextInt();
            longA[i] = r.nextLong();
            charA[i] = (char) r.nextInt();
            floatA[i] = r.nextFloat();
            doubleA[i] = r.nextDouble();
            booleanA[i] = r.nextBoolean();
        }
        System.arraycopy(shortA, 0, shortB, 0, ARRAY_LENGTH);
        System.arraycopy(intA, 0, intB, 0, ARRAY_LENGTH);
        System.arraycopy(longA, 0, longB, 0, ARRAY_LENGTH);
        System.arraycopy(charA, 0, charB, 0, ARRAY_LENGTH);
        System.arraycopy(floatA, 0, floatB, 0, ARRAY_LENGTH);
        System.arraycopy(doubleA, 0, doubleB, 0, ARRAY_LENGTH);
        System.arraycopy(booleanA, 0, booleanB, 0, ARRAY_LENGTH);
    }

    @Test
    public void timeArraysEqualsByte() {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            mSink = Arrays.equals(byteA, byteB);
        }
    }

    @Test
    public void timeArraysEqualsByteRange() {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            mSink = Arrays.equals(byteA, 0, ARRAY_LENGTH, byteB, 0, ARRAY_LENGTH);
        }
    }

    @Test
    public void timeArraysEqualsShort() {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            mSink = Arrays.equals(shortA, shortB);
        }
    }

    @Test
    public void timeArraysEqualsShortRange() {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            mSink = Arrays.equals(shortA, 0, ARRAY_LENGTH, shortB, 0, ARRAY_LENGTH);
        }
    }

    @Test
    public void timeArraysEqualsInt() {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            mSink = Arrays.equals(intA, intB);
        }
    }

    @Test
    public void timeArraysEqualsIntRange() {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            mSink = Arrays.equals(intA, 0, ARRAY_LENGTH, intB, 0, ARRAY_LENGTH);
        }
    }

    @Test
    public void timeArraysEqualsLong() {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            mSink = Arrays.equals(longA, longB);
        }
    }

    @Test
    public void timeArraysEqualsLongRange() {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            mSink = Arrays.equals(longA, 0, ARRAY_LENGTH, longB, 0, ARRAY_LENGTH);
        }
    }

    @Test
    public void timeArraysEqualsChar() {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            mSink = Arrays.equals(charA, charB);
        }
    }

    @Test
    public void timeArraysEqualsCharRange() {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            mSink = Arrays.equals(charA, 0, ARRAY_LENGTH, charB, 0, ARRAY_LENGTH);
        }
    }

    @Test
    public void timeArraysEqualsFloat() {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            mSink = Arrays.equals(floatA, floatB);
        }
    }

    @Test
    public void timeArraysEqualsFloatRange() {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            mSink = Arrays.equals(floatA, 0, ARRAY_LENGTH, floatB, 0, ARRAY_LENGTH);
        }
    }

    @Test
    public void timeArraysEqualsDouble() {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            mSink = Arrays.equals(doubleA, doubleB);
        }
    }

    @Test
    public void timeArraysEqualsDoubleRange() {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            mSink = Arrays.equals(doubleA, 0, ARRAY_LENGTH, doubleB, 0, ARRAY_LENGTH);
        }
    }

    @Test
    public void timeArraysEqualsBoolean() {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            mSink = Arrays.equals(booleanA, booleanB);
        }
    }

    @Test
    public void timeArraysEqualsBooleanRange() {
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            mSink = Arrays.equals(booleanA, 0, ARRAY_LENGTH, booleanB, 0, ARRAY_LENGTH);
        }
    }

    @Test
    public void timeArraysEqualsFloatNaN() {
        // Fill arrays with NaNs to test specialized slow paths
        Arrays.fill(floatA, Float.intBitsToFloat(0x7fc00001));
        Arrays.fill(floatB, Float.intBitsToFloat(0x7fc00002));
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            mSink = Arrays.equals(floatA, floatB);
        }
    }

    @Test
    public void timeArraysEqualsDoubleNaN() {
        // Fill arrays with NaNs to test specialized slow paths
        Arrays.fill(doubleA, Double.longBitsToDouble(0x7ff8000000000001L));
        Arrays.fill(doubleB, Double.longBitsToDouble(0x7ff8000000000002L));
        final BenchmarkState state = mBenchmarkRule.getState();
        while (state.keepRunning()) {
            mSink = Arrays.equals(doubleA, doubleB);
        }
    }
}
