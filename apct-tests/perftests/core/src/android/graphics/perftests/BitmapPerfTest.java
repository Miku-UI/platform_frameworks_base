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

package android.graphics.perftests;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Parcel;
import android.perftests.utils.BenchmarkState;
import android.perftests.utils.PerfStatusReporter;

import androidx.test.filters.LargeTest;

import org.junit.Rule;
import org.junit.Test;

@LargeTest
public class BitmapPerfTest {
    @Rule
    public PerfStatusReporter mPerfStatusReporter = new PerfStatusReporter();

    @Test
    public void testParcelBitmap() {
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        final Bitmap bitmap = makeBitmap();

        while (state.keepRunning()) {
            Parcel parcel = Parcel.obtain();
            bitmap.writeToParcel(parcel, 0);
            parcel.recycle();
        }

        bitmap.recycle();
    }

    @Test
    public void testBitmapAsShared() {
        BenchmarkState state = mPerfStatusReporter.getBenchmarkState();
        final Bitmap bitmap = makeBitmap();

        while (state.keepRunning()) {
            Bitmap unused = bitmap.asShared();
        }

        bitmap.recycle();
    }

    private Bitmap makeBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        // Paint the canvas purple.
        // Purple is a good color for a benchmark. Purple benchmarks are the best.
        canvas.drawColor(Color.parseColor("purple"));
        return bitmap;
    }
}
