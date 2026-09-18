/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.test.silkfx

import android.app.Activity
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.IBinder
import android.view.MotionEvent
import android.view.SurfaceControl
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.AdapterView
import android.widget.FrameLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.Toolbar

class CompShadersActivity : Activity() {
    private lateinit var container: FrameLayout
    private var fancyButtonSc: SurfaceControl? = null
    private var backgroundSc: SurfaceControl? = null // To attach to
    private var accumulatedX = 100f
    private var accumulatedY = 300f
    private var shaderBinder: IBinder? = null

    private var currentEffect = 2 // 0: None, 1: Blur, 2: Frosted
    private var currentTarget = 0 // 0: Self, 1: Behind (Assuming constants)

    // Water displacement shader
    private val WATER_SHADER = """
        uniform shader inLayer;
        half4 main(float2 xy) {
            float2 wave = float2(sin(xy.y * 0.05), cos(xy.x * 0.05));
            half4 color = inLayer.eval(xy + wave * 10.0);
            color.r *= 2.0;
            return color;
        }
    """

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comp_shaders)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setActionBar(toolbar)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        val surfaceView = SurfaceView(this)
        surfaceView.setZOrderOnTop(true) // Ensure it supports transparency if needed
        surfaceView.holder.setFormat(PixelFormat.TRANSLUCENT)

        val containerFrame = findViewById<android.widget.FrameLayout>(R.id.container)
        containerFrame.addView(surfaceView)

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                createFancyButton(surfaceView.surfaceControl!!)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                fancyButtonSc?.release()
                fancyButtonSc = null
            }
        })

        // Touch handling
        surfaceView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    // Simple drag, center on finger for simplicity or offset
                    accumulatedX = event.x - 250 // Center of 500 width
                    accumulatedY = event.y - 150 // Center of 300 height
                    updatePosition()
                    true
                }
                else -> true
            }
        }

        val spinner = findViewById<Spinner>(R.id.effect_spinner)
        spinner.setSelection(currentEffect)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentEffect = position
                updateEffects()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val targetSwitch = findViewById<Switch>(R.id.target_switch)
        targetSwitch.setOnCheckedChangeListener { _, isChecked ->
            currentTarget = if (isChecked) 1 else 0 // Behind : Self
            targetSwitch.text = if (isChecked) "Behind" else "Self"
            updateEffects()
        }

        // Handle Window Insets
        val root = findViewById<View>(android.R.id.content)
        root.setOnApplyWindowInsetsListener { _, insets ->
            val systemBars = insets.getInsets(android.view.WindowInsets.Type.systemBars())

            // Pad Toolbar
            toolbar.setPadding(0, systemBars.top, 0, 0)
            toolbar.layoutParams.height = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 56f, resources.displayMetrics
            ).toInt() + systemBars.top

            // Pad Controls
            val controls = findViewById<View>(R.id.controls)
            controls.setPadding(
                controls.paddingLeft,
                controls.paddingTop,
                controls.paddingRight,
                controls.paddingBottom + systemBars.bottom
            )

            insets
        }
    }

    private fun createFancyButton(parent: SurfaceControl) {
        val t = SurfaceControl.Transaction()
        fancyButtonSc = SurfaceControl.Builder()
            .setName("FancyButton")
            .setParent(parent)
            .setBufferSize(500, 300)
            .setFormat(PixelFormat.TRANSLUCENT)
            .setOpaque(false)
            .build()

        // Create HardwareBuffer
        val buffer = android.hardware.HardwareBuffer.create(
            500, 300, android.hardware.HardwareBuffer.RGBA_8888, 1,
            android.hardware.HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or
            android.hardware.HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or
            android.hardware.HardwareBuffer.USAGE_COMPOSER_OVERLAY
        )

        val renderer = android.graphics.HardwareBufferRenderer(buffer)
        val renderNode = android.graphics.RenderNode("FancyButtonContent")
        renderNode.setPosition(0, 0, 500, 300)

        val canvas = renderNode.beginRecording()
        val paint = Paint()
        paint.color = 0x800000FF.toInt() // Translucent Blue
        paint.style = Paint.Style.FILL
        canvas.drawARGB(0, 0, 0, 0) // Clear
        // Draw centered with padding
        canvas.drawRoundRect(50f, 50f, 450f, 250f, 50f, 50f, paint)

        paint.color = Color.WHITE
        paint.textSize = 60f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Foo", 250f, 170f, paint)
        renderNode.endRecording()

        renderer.setContentRoot(renderNode)

        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val countDownLatch = java.util.concurrent.CountDownLatch(1)
        renderer.obtainRenderRequest().draw(executor) { _ ->
            countDownLatch.countDown()
        }

        try {
            countDownLatch.await(1, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        t.setBuffer(fancyButtonSc!!, buffer)

        t.setVisibility(fancyButtonSc!!, true)
        t.setPosition(fancyButtonSc!!, accumulatedX, accumulatedY)
        t.setCornerRadius(fancyButtonSc!!, 50f)
        t.apply()

        updateEffects()
    }

    private fun updatePosition() {
        fancyButtonSc?.let { sc ->
            val t = SurfaceControl.Transaction()
            t.setPosition(sc, accumulatedX, accumulatedY)
            t.apply()
        }
    }

    private fun updateEffects() {
        val sc = fancyButtonSc ?: return
        val t = SurfaceControl.Transaction()

        // Reset state
        t.setBackgroundBlurRadius(sc, 0)
        t.setPostProcess(sc, null, null, 0)

        when (currentEffect) {
            1 -> { // Blur
                t.setBackgroundBlurRadius(sc, 20)
            }
            2 -> { // Water
                if (shaderBinder == null) {
                    shaderBinder = SurfaceControl.registerShader("WaterShader", WATER_SHADER)
                }
                t.setPostProcess(sc, shaderBinder, null, currentTarget)
            }
        }
        t.apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        shaderBinder?.let { SurfaceControl.unregisterShader(it) }
    }
}
