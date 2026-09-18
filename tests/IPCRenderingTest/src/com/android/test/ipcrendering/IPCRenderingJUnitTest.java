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

package com.android.test.ipcrendering;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.HardwareRenderer;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RenderNode;
import android.view.View;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class IPCRenderingJUnitTest {

    @Rule
    public ActivityTestRule<IPCRenderingTest> mActivityRule =
            new ActivityTestRule<>(IPCRenderingTest.class);

    @Test
    public void testIPCRendering() {
        final IPCRenderingTest activity = mActivityRule.getActivity();
        final CountDownLatch latch = new CountDownLatch(1);
        activity.setRenderer(new IPCRenderingTest.Renderer() {
            @Override
            public void onSurfaceCreated(IPCRenderingTest.IpcCanvasContext context) {
                Canvas c = context.lockCanvas(512, 512);
                c.drawColor(0xFFB0E0E6, PorterDuff.Mode.SRC);

                Paint paint = new Paint();
                paint.setAntiAlias(true);
                paint.setColor(0xFFE55B13);
                c.drawRect(0, 0, 256, 256, paint);

                Paint circlePaint = new Paint();
                circlePaint.setAntiAlias(true);
                circlePaint.setColor(0xFF556B2F);
                c.drawCircle(512, 512, 256, circlePaint);

                // Create fractal bitmap
                Bitmap bitmap = createFractalBitmap(256, 256);
                // Upload to hardware buffer (
                Bitmap hwBitmap = bitmap.copy(Bitmap.Config.HARDWARE, false);

                Paint bitmapPaint = new Paint();
                bitmapPaint.setAlpha(128); // 50% alpha
                c.drawBitmap(hwBitmap, 128, 128, bitmapPaint);

                context.unlockAndPost(c);
                latch.countDown();
            }
        });

        try {
            boolean result = latch.await(5, TimeUnit.SECONDS);
            if (!result) {
                throw new RuntimeException("Timed out waiting for surface creation");
            }
            Thread.sleep(5000);
        } catch (InterruptedException e) {
        }
    }

    @Test
    public void testLayerRendering() {
        final IPCRenderingTest activity = mActivityRule.getActivity();
        final CountDownLatch latch = new CountDownLatch(1);
        activity.setRenderer(new IPCRenderingTest.Renderer() {
            @Override
            public void onSurfaceCreated(IPCRenderingTest.IpcCanvasContext context) {
                RenderNode layerNode = RenderNode.create("layer", null);
                layerNode.setLayerType(View.LAYER_TYPE_HARDWARE);
                layerNode.setAlpha(0.5f);

                for (int i = 0; i < 200; i++) {
                    // Oscillate color
                    Canvas lc = layerNode.beginRecording(200, 200);
                    int red = (int) ((Math.sin(i * 0.1) + 1) * 127.5);
                    int green = (int) ((Math.cos(i * 0.1) + 1) * 127.5);
                    lc.drawColor(Color.rgb(red, green, 0));
                    layerNode.endRecording();
                    Canvas c = context.lockCanvas(512, 512);
                    c.drawColor(0xFFFFFFFF);

                    // Opaque blue rect under the node
                    Paint p = new Paint();
                    p.setColor(0xFF0000FF);
                    c.drawRect(100, 100, 400, 400, p);

                    // Animate back and forth
                    int offset = (int) (Math.sin(i * 0.05) * 100);
                    layerNode.setPosition(156 + offset, 156, 356 + offset, 356);

                    c.drawRenderNode(layerNode);

                    context.unlockAndPost(c);
                    try {
                        Thread.sleep(16);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                latch.countDown();
            }
        });

        try {
            boolean result = latch.await(10, TimeUnit.SECONDS);
            if (!result) {
                throw new RuntimeException("Timed out waiting for layer rendering");
            }
        } catch (InterruptedException e) {
        }
    }

    private Bitmap createFractalBitmap(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[width * height];

        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                float real = (x - width / 2.0f) * 4.0f / width;
                float imag = (y - height / 2.0f) * 4.0f / height;
                float cr = real;
                float ci = imag;
                int n;
                for (n = 0; n < 255; n++) {
                    float nr = real * real - imag * imag + cr;
                    float ni = 2 * real * imag + ci;
                    if (nr * nr + ni * ni > 4.0f)
                        break;
                    real = nr;
                    imag = ni;
                }
                int color = n;
                // Grayscale fractal pattern
                pixels[y * width + x] = 0xFF000000 | (color << 16) | (color << 8) | color;
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }
}
