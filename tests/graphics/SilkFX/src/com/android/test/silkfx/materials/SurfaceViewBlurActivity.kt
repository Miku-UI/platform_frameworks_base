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

package com.android.test.silkfx.materials

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.SurfaceView
import android.view.BlurRegion
import android.view.RoundedRectBlurRegion
import android.view.SurfaceHolder
import androidx.appcompat.app.AppCompatActivity
import android.widget.RadioGroup
import android.util.Log
import com.android.test.silkfx.R
import java.io.IOException

class SurfaceViewBlurActivity : AppCompatActivity() {

    private lateinit var surfaceView: SurfaceView
    private lateinit var paint: Paint
    private var currentBitmap: Bitmap? = null
    private var currentBlur: Int = R.id.disable_blur
    private val TAG = "SurfaceViewBlurActivity"
    private var regionsOne: ArrayList<BlurRegion> = ArrayList()
    private var regionsTwo: ArrayList<BlurRegion> = ArrayList()
    private var blurOne: BlurRegion? = null
    private var blurTwo: BlurRegion? = null
    private var blurThree: BlurRegion? = null
    /** Called when the activity is first created. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.blur_test)
        paint = Paint()
        surfaceView = findViewById(R.id.surfaceView)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                loadImage("gainmaps/lamps.jpg", holder)
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                val cornerRadius = FloatArray(8)
                regionsOne.clear()
                regionsTwo.clear()
                // Create a blur region that covers the entire surface.
                var rect =
                    RectF(0.0f, 0.0f, surfaceView.width.toFloat(), surfaceView.height.toFloat())
                blurOne = RoundedRectBlurRegion(rect, cornerRadius, 1.0f, 10.0f)
                regionsOne.add(blurOne!!)
                // Create a blur region that covers the top left part of the surface.
                rect = RectF(0.0f, 0.0f, surfaceView.width / 2.0f, surfaceView.height / 2.0f)
                blurTwo = RoundedRectBlurRegion(rect, cornerRadius, 1.0f, 10.0f)
                // Create a blur region that covers the bottom right part of the surface.
                rect = RectF(surfaceView.width / 2.0f, surfaceView.height / 2.0f, surfaceView.width.toFloat(), surfaceView.height.toFloat())
                blurThree = RoundedRectBlurRegion(rect, cornerRadius, 1.0f, 10.0f)
                regionsTwo.add(blurTwo!!)
                regionsTwo.add(blurThree!!)
                applyBlur()
                drawStaticContent(holder)
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
            }
        })

        var blurOption = findViewById<RadioGroup>(R.id.blur_option)
        // handle RadioGroup selection changes
        blurOption.setOnCheckedChangeListener(
            RadioGroup.OnCheckedChangeListener { _, id ->
                currentBlur = id
                applyBlur()
            }
        )
    }

    private fun drawStaticContent(holder: SurfaceHolder) {
        var canvas: Canvas? = null
        try {
            canvas = holder.lockHardwareCanvas()
            if (canvas != null) {
                currentBitmap?.let { bitmap ->
                    val canvasWidth = canvas.width
                    val canvasHeight = canvas.height
                    val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                    val destRect = Rect(0, 0, canvasWidth, canvasHeight)
                    canvas.drawBitmap(bitmap, srcRect, destRect, paint)
                }
            }
        } finally {
            if (canvas != null) {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }
    private fun loadImage(assetPath: String, holder: SurfaceHolder) {
        try {
            val source = ImageDecoder.createSource(assets, assetPath)
            currentBitmap = ImageDecoder.decodeBitmap(source) { decoder, info, source ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error loading image: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun applyBlur() {
        when (currentBlur) {
            R.id.blur_all -> {
                surfaceView.setBlurRegions(regionsOne)
            }
            R.id.blur_partial -> {
                surfaceView.setBlurRegions(regionsTwo)
            }
            R.id.disable_blur -> {
                surfaceView.setBlurRegions(null)
            }
        }
    }

    private fun getCurrentBlur(): Int {
        return currentBlur
    }
}
