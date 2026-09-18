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

package com.android.test.silkfx.hdr

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.HardwareBufferRenderer
import android.graphics.RenderNode
import android.hardware.DisplayLuts
import android.hardware.HardwareBuffer
import android.hardware.LutProperties
import android.os.Bundle
import android.view.Display
import android.view.SurfaceControl
import android.view.SurfaceView
import android.view.SurfaceHolder
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.widget.RadioGroup
import android.util.Log
import com.android.test.silkfx.R
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.function.Consumer
import kotlin.math.log2
import kotlin.math.roundToInt
import java.io.ByteArrayOutputStream

class LutTestActivity : AppCompatActivity() {

    private lateinit var surfaceView: SurfaceView
    private lateinit var propertiesTextView: TextView
    private var surfaceControl: SurfaceControl? = null
    private var currentBitmap: Bitmap? = null
    private var renderNode = RenderNode("LutRenderNode")
    private var currentLutType: Int = R.id.agtm_rwtmo // Store current LUT type
    private val TAG = "LutTestActivity"
    private val renderExecutor = Executors.newSingleThreadExecutor()

    private val hdrSdrRatioListener = Consumer<Display> { display ->
        if (surfaceControl != null) {
             applyCurrentLut()
        }
    }

    /** Called when the activity is first created. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.lut_test)

        surfaceView = findViewById(R.id.surfaceView)
        propertiesTextView = findViewById(R.id.lut_properties_text)

        val overlayProperties = display.overlaySupport
        val lutProperties = overlayProperties.lutProperties
        val sb = StringBuilder("Supported LUTs:\n")
        if (lutProperties != null) {
            for (prop in lutProperties) {
                sb.append(prop.toString()).append("\n")
            }
        } else {
            sb.append("None")
        }
        propertiesTextView.text = sb.toString()

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                surfaceView.display?.registerHdrSdrRatioChangedListener(mainExecutor, hdrSdrRatioListener)
                createChildSurfaceControl()
                loadImage("gainmaps/lamps.jpg", holder)
                currentBitmap?.let {
                    createAndRenderHardwareBuffer(holder, it, getCurrentLut(), currentLutType == R.id.agtm_rwtmo)
                }
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                surfaceView.display?.unregisterHdrSdrRatioChangedListener(hdrSdrRatioListener)
            }
        })

        var lutOption = findViewById<RadioGroup>(R.id.lut_option)
        // handle RadioGroup selection changes
        lutOption.setOnCheckedChangeListener(
            RadioGroup.OnCheckedChangeListener { _, id ->
                currentLutType = id
                if (surfaceControl != null) {
                    applyCurrentLut()
                }
            }
        )
    }

    private fun applyCurrentLut() {
        currentBitmap?.let {
            createAndRenderHardwareBuffer(surfaceView.holder, it, getCurrentLut(), currentLutType == R.id.agtm_rwtmo)
        }
    }

    private fun getCurrentLut(): DisplayLuts? {
        return when (currentLutType) {
            R.id.lut_1d -> get1DLut()
            R.id.lut_3d -> get3DLut()
            else -> null
        }
    }

    private fun get3DLut(): DisplayLuts {
        var luts = DisplayLuts()
        val entry = DisplayLuts.Entry(
            floatArrayOf(
            // ----------------------------------------------------------------------------------------------------
            // RED Channel Values (125 values: R_out[i][j][k] where k varies fastest, then j, then i)
            //
            // i=0 (Input Red index 0)
            //   j=0 (Input Green index 0)
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f, // (k=0,1,2,3,4) -> R_out values for R[0][0][k]
            //   j=1 (Input Green index 1)
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f, // (k=0,1,2,3,4) -> R_out values for R[0][1][k]
            //   j=2 (Input Green index 2)
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f, // (k=0,1,2,3,4) -> R_out values for R[0][2][k]
            //   j=3 (Input Green index 3)
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f, // (k=0,1,2,3,4) -> R_out values for R[0][3][k]
            //   j=4 (Input Green index 4)
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f, // (k=0,1,2,3,4) -> R_out values for R[0][4][k]

            // i=1 (Input Red index 1)
            //   j=0 (Input Green index 0)
            0.25f, 0.25f, 0.25f, 0.25f, 0.25f, // (k=0,1,2,3,4) -> R_out values for R[1][0][k]
            //   j=1 (Input Green index 1)
            0.25f, 0.25f, 0.25f, 0.25f, 0.25f, // (k=0,1,2,3,4) -> R_out values for R[1][1][k]
            //   j=2 (Input Green index 2)
            0.25f, 0.25f, 0.25f, 0.25f, 0.25f, // (k=0,1,2,3,4) -> R_out values for R[1][2][k]
            //   j=3 (Input Green index 3)
            0.25f, 0.25f, 0.25f, 0.25f, 0.25f, // (k=0,1,2,3,4) -> R_out values for R[1][3][k]
            //   j=4 (Input Green index 4)
            0.25f, 0.25f, 0.25f, 0.25f, 0.25f, // (k=0,1,2,3,4) -> R_out values for R[1][4][k]

            // i=2 (Input Red index 2)
            //   j=0 (Input Green index 0)
            0.5f, 0.5f, 0.5f, 0.5f, 0.5f, // (k=0,1,2,3,4) -> R_out values for R[2][0][k]
            //   j=1 (Input Green index 1)
            0.5f, 0.5f, 0.5f, 0.5f, 0.5f, // (k=0,1,2,3,4) -> R_out values for R[2][1][k]
            //   j=2 (Input Green index 2)
            0.5f, 0.5f, 0.5f, 0.5f, 0.5f, // (k=0,1,2,3,4) -> R_out values for R[2][2][k]
            //   j=3 (Input Green index 3)
            0.5f, 0.5f, 0.5f, 0.5f, 0.5f, // (k=0,1,2,3,4) -> R_out values for R[2][3][k]
            //   j=4 (Input Green index 4)
            0.5f, 0.5f, 0.5f, 0.5f, 0.5f, // (k=0,1,2,3,4) -> R_out values for R[2][4][k]

            // i=3 (Input Red index 3)
            //   j=0 (Input Green index 0)
            0.75f, 0.75f, 0.75f, 0.75f, 0.75f, // (k=0,1,2,3,4) -> R_out values for R[3][0][k]
            //   j=1 (Input Green index 1)
            0.75f, 0.75f, 0.75f, 0.75f, 0.75f, // (k=0,1,2,3,4) -> R_out values for R[3][1][k]
            //   j=2 (Input Green index 2)
            0.75f, 0.75f, 0.75f, 0.75f, 0.75f, // (k=0,1,2,3,4) -> R_out values for R[3][2][k]
            //   j=3 (Input Green index 3)
            0.75f, 0.75f, 0.75f, 0.75f, 0.75f, // (k=0,1,2,3,4) -> R_out values for R[3][3][k]
            //   j=4 (Input Green index 4)
            0.75f, 0.75f, 0.75f, 0.75f, 0.75f, // (k=0,1,2,3,4) -> R_out values for R[3][4][k]

            // i=4 (Input Red index 4)
            //   j=0 (Input Green index 0)
            1.0f, 1.0f, 1.0f, 1.0f, 1.0f, // (k=0,1,2,3,4) -> R_out values for R[4][0][k]
            //   j=1 (Input Green index 1)
            1.0f, 1.0f, 1.0f, 1.0f, 1.0f, // (k=0,1,2,3,4) -> R_out values for R[4][1][k]
            //   j=2 (Input Green index 2)
            1.0f, 1.0f, 1.0f, 1.0f, 1.0f, // (k=0,1,2,3,4) -> R_out values for R[4][2][k]
            //   j=3 (Input Green index 3)
            1.0f, 1.0f, 1.0f, 1.0f, 1.0f, // (k=0,1,2,3,4) -> R_out values for R[4][3][k]
            //   j=4 (Input Green index 4)
            1.0f, 1.0f, 1.0f, 1.0f, 1.0f, // (k=0,1,2,3,4) -> R_out values for R[4][4][k]

            // ----------------------------------------------------------------------------------------------------
            // GREEN Channel Values (125 values: G_out[i][j][k])
            // Starts at index 125 in the 1D array.
            // The G_out value depends on 'j' index, irrespective of 'i' or 'k'.
            //
            // i=0 (Input Red index 0)
            //   j=0 (Input Green index 0)
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f, // (k=0,1,2,3,4) -> G_out values for G[0][0][k]
            //   j=1 (Input Green index 1)
            0.25f, 0.25f, 0.25f, 0.25f, 0.25f, // (k=0,1,2,3,4) -> G_out values for G[0][1][k]
            //   j=2 (Input Green index 2)
            0.5f, 0.5f, 0.5f, 0.5f, 0.5f, // (k=0,1,2,3,4) -> G_out values for G[0][2][k]
            //   j=3 (Input Green index 3)
            0.75f, 0.75f, 0.75f, 0.75f, 0.75f, // (k=0,1,2,3,4) -> G_out values for G[0][3][k]
            //   j=4 (Input Green index 4)
            1.0f, 1.0f, 1.0f, 1.0f, 1.0f, // (k=0,1,2,3,4) -> G_out values for G[0][4][k]

            // i=1 (Input Red index 1)
            //   j=0 (Input Green index 0)
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
            //   j=1 (Input Green index 1)
            0.25f, 0.25f, 0.25f, 0.25f, 0.25f,
            //   j=2 (Input Green index 2)
            0.5f, 0.5f, 0.5f, 0.5f, 0.5f,
            //   j=3 (Input Green index 3)
            0.75f, 0.75f, 0.75f, 0.75f, 0.75f,
            //   j=4 (Input Green index 4)
            1.0f, 1.0f, 1.0f, 1.0f, 1.0f,

            // i=2 (Input Red index 2)
            //   j=0 (Input Green index 0)
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
            //   j=1 (Input Green index 1)
            0.25f, 0.25f, 0.25f, 0.25f, 0.25f,
            //   j=2 (Input Green index 2)
            0.5f, 0.5f, 0.5f, 0.5f, 0.5f,
            //   j=3 (Input Green index 3)
            0.75f, 0.75f, 0.75f, 0.75f, 0.75f,
            //   j=4 (Input Green index 4)
            1.0f, 1.0f, 1.0f, 1.0f, 1.0f,

            // i=3 (Input Red index 3)
            //   j=0 (Input Green index 0)
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
            //   j=1 (Input Green index 1)
            0.25f, 0.25f, 0.25f, 0.25f, 0.25f,
            //   j=2 (Input Green index 2)
            0.5f, 0.5f, 0.5f, 0.5f, 0.5f,
            //   j=3 (Input Green index 3)
            0.75f, 0.75f, 0.75f, 0.75f, 0.75f,
            //   j=4 (Input Green index 4)
            1.0f, 1.0f, 1.0f, 1.0f, 1.0f,

            // i=4 (Input Red index 4)
            //   j=0 (Input Green index 0)
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
            //   j=1 (Input Green index 1)
            0.25f, 0.25f, 0.25f, 0.25f, 0.25f,
            //   j=2 (Input Green index 2)
            0.5f, 0.5f, 0.5f, 0.5f, 0.5f,
            //   j=3 (Input Green index 3)
            0.75f, 0.75f, 0.75f, 0.75f, 0.75f,
            //   j=4 (Input Green index 4)
            1.0f, 1.0f, 1.0f, 1.0f, 1.0f,

            // ----------------------------------------------------------------------------------------------------
            // BLUE Channel Values (125 values, B_out[i][j][k])
            // Starts at index 250 in the 1D array.
            // The B_out value depends on 'k' index, irrespective of 'i' or 'j'.
            //
            // i=0 (Input Red index 0)
            //   j=0 (Input Green index 0)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f, // (k=0,1,2,3,4) -> B_out values for B[0][0][k]
            //   j=1 (Input Green index 1)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f, // (k=0,1,2,3,4) -> B_out values for B[0][1][k]
            //   j=2 (Input Green index 2)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f, // (k=0,1,2,3,4) -> B_out values for B[0][2][k]
            //   j=3 (Input Green index 3)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f, // (k=0,1,2,3,4) -> B_out values for B[0][3][k]
            //   j=4 (Input Green index 4)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f, // (k=0,1,2,3,4) -> B_out values for B[0][4][k]

            // i=1 (Input Red index 1)
            //   j=0 (Input Green index 0)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f,
            //   j=1 (Input Green index 1)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f,
            //   j=2 (Input Green index 2)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f,
            //   j=3 (Input Green index 3)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f,
            //   j=4 (Input Green index 4)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f,

            // i=2 (Input Red index 2)
            //   j=0 (Input Green index 0)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f,
            //   j=1 (Input Green index 1)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f,
            //   j=2 (Input Green index 2)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f,
            //   j=3 (Input Green index 3)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f,
            //   j=4 (Input Green index 4)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f,

            // i=3 (Input Red index 3)
            //   j=0 (Input Green index 0)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f,
            //   j=1 (Input Green index 1)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f,
            //   j=2 (Input Green index 2)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f,
            //   j=3 (Input Green index 3)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f,
            //   j=4 (Input Green index 4)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f,

            // i=4 (Input Red index 4)
            //   j=0 (Input Green index 0)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f,
            //   j=1 (Input Green index 1)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f,
            //   j=2 (Input Green index 2)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f,
            //   j=3 (Input Green index 3)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f,
            //   j=4 (Input Green index 4)
            0.0f, 0.25f, 0.5f, 0.75f, 1.0f),
            LutProperties.THREE_DIMENSION,
            LutProperties.SAMPLING_KEY_RGB
        )
        luts.set(entry)
        return luts
    }

    private fun get1DLut(): DisplayLuts {
        var luts = DisplayLuts()
        val entry = DisplayLuts.Entry(
            floatArrayOf(0.778345f, 0.778345f, 0.79628f, 0.806962f, 0.814628f, 0.820624f, 0.825556f, 0.829749f, 0.833398f, 0.83663f, 0.839532f, 0.842166f, 0.844578f, 0.846803f, 0.848868f, 0.850795f, 0.852601f, 0.854302f, 0.855908f, 0.85743f, 0.858877f, 0.860255f, 0.861571f, 0.862831f, 0.864039f, 0.865199f, 0.866315f, 0.86739f, 0.868427f, 0.869429f, 0.870399f, 0.871337f, 0.872247f, 0.873129f, 0.873986f, 0.874819f, 0.87563f, 0.876419f, 0.877187f, 0.877936f, 0.878667f, 0.87938f, 0.880077f, 0.880758f, 0.881424f, 0.882075f, 0.882712f, 0.883336f, 0.883948f, 0.884547f, 0.885135f, 0.885711f, 0.886276f, 0.886831f, 0.887376f, 0.887912f, 0.888438f, 0.888955f, 0.889463f, 0.889963f, 0.890454f, 0.890938f, 0.891414f, 0.891883f, 0.892345f, 0.8928f, 0.893248f, 0.89369f, 0.894125f, 0.894554f,
                         0.894977f, 0.895394f, 0.895806f, 0.896212f, 0.896613f, 0.897009f, 0.897399f, 0.897785f, 0.898166f, 0.898542f, 0.898913f, 0.89928f, 0.899643f, 0.900002f, 0.900356f, 0.900706f, 0.901052f, 0.901395f, 0.901733f, 0.902068f, 0.9024f, 0.902727f, 0.903052f, 0.903373f, 0.90369f, 0.904005f, 0.904316f, 0.904624f, 0.904929f, 0.905231f, 0.90553f, 0.905826f, 0.906119f, 0.90641f, 0.906698f, 0.906983f, 0.907266f, 0.907546f, 0.907823f, 0.908098f, 0.908371f, 0.908641f,
                         0.998874f, 0.998891f, 0.998907f, 0.998924f, 0.998941f, 0.998957f, 0.998974f, 0.99899f, 0.999007f, 0.999023f, 0.99904f, 0.999056f, 0.999073f, 0.999089f, 0.999106f, 0.999122f, 0.999139f, 0.999155f, 0.999172f, 0.999188f, 0.999204f, 0.999221f, 0.999237f, 0.999254f, 0.99927f, 0.999286f, 0.999303f, 0.999319f, 0.999336f, 0.999352f, 0.999368f, 0.999385f, 0.999401f, 0.999417f, 0.999434f, 0.99945f, 0.999466f, 0.999483f, 0.999499f, 0.999515f, 0.999531f, 0.999548f, 0.999564f, 0.99958f, 0.999596f, 0.999613f, 0.999629f, 0.999645f, 0.999661f, 0.999678f, 0.999694f, 0.99971f, 0.999726f, 0.999742f, 0.999758f, 0.999775f, 0.999791f, 0.999807f, 0.999823f, 0.999839f, 0.999855f, 0.999871f, 0.999888f, 0.999904f, 0.99992f, 0.999936f, 0.999952f, 0.999968f, 0.999984f, 1.0f),
            LutProperties.ONE_DIMENSION,
            LutProperties.SAMPLING_KEY_MAX_RGB
        )
        luts.set(entry)
        return luts
    }

    private fun createChildSurfaceControl() {
        surfaceView.surfaceControl?.let { parentSC ->
            surfaceControl = SurfaceControl.Builder()
                .setParent(parentSC)
                .setBufferSize(surfaceView.width, surfaceView.height)
                .setName("LutTestSurfaceControl")
                .setHidden(false)
                .build()
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

    private fun createAndRenderHardwareBuffer(holder: SurfaceHolder, bitmap: Bitmap, luts: DisplayLuts?, applyAgtm: Boolean) {

        val surfaceWidth = holder.surfaceFrame.width()
        val surfaceHeight = holder.surfaceFrame.height()

        val buffer = HardwareBuffer.create(
            surfaceWidth,
            surfaceHeight,
            HardwareBuffer.RGBA_1010102,
            1, // layers
            HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
        )

        // Apply AGTM (Adaptive Global Tonemapping)
        if (applyAgtm) {
            val agtmBlob = createAgtmBlob(log2(bitmap.gainmap!!.displayRatioForFullHdr))
            buffer.setSmpte2094_50(agtmBlob, 0, agtmBlob.size)
        } else {
            buffer.clearSmpte2094_50()
        }

        val renderer = HardwareBufferRenderer(buffer)
        renderNode.setPosition(0, 0, buffer.width, buffer.height)
        renderer.setContentRoot(renderNode)

        val canvas = renderNode.beginRecording()

        // Scale from a large bitmap down to the surface destination
        val scaleX = surfaceWidth.toFloat() / bitmap.width.toFloat()
        val scaleY = surfaceHeight.toFloat() / bitmap.height.toFloat()
        val scale = minOf(scaleX, scaleY)

        val matrix = Matrix().apply{ postScale(scale, scale) }
        canvas.drawBitmap(bitmap, matrix, null)
        renderNode.endRecording()

        val colorSpace = ColorSpace.get(ColorSpace.Named.BT2020_PQ)
        val latch = CountDownLatch(1)
        renderer.obtainRenderRequest().setColorSpace(colorSpace).draw(renderExecutor) { renderResult ->
            surfaceControl?.let {
                SurfaceControl.Transaction().setBuffer(it, buffer, renderResult.fence)
                        .setDataSpace(it, colorSpace.getDataSpace())
                        .setLuts(it, luts).apply()
            }
            latch.countDown()
        }
        latch.await() // Wait for the fence to complete.
        buffer.close()
    }

    private fun createAgtmBlob(baselineHdrHeadroom: Float, hdrReferenceWhite: Float = 203f): ByteArray {
        val stream = ByteArrayOutputStream()

        // Application Version (0)
        stream.write(0)

        // Color Volume Transform
        val writer = BitfieldWriter(stream)
        val hasCustomWhite = hdrReferenceWhite != 203f
        val hasAdaptive = true

        writer.writeBits(if (hasCustomWhite) 1 else 0, 1)
        writer.writeBits(if (hasAdaptive) 1 else 0, 1)
        writer.padAndWriteToStream()

        if (hasCustomWhite) {
            writeU16BE(stream, floatToUint16(hdrReferenceWhite, 1, 50000, 0, 5.0f))
        }

        // Adaptive Tone Map
        writeU16BE(stream, floatToUint16(baselineHdrHeadroom, 0, 60000, 0, 10000.0f))
        val adaptiveWriter = BitfieldWriter(stream)
        adaptiveWriter.writeBits(1, 1) // use_reference_white_tone_mapping_flag
        adaptiveWriter.padAndWriteToStream()

        return stream.toByteArray()
    }

    private fun floatToUint16(v: Float, min: Int, max: Int, offset: Int, scale: Float): Int {
        val converted = ((v * scale).roundToInt() + offset)
        return converted.coerceIn(min, max)
    }

    private fun writeU16BE(stream: ByteArrayOutputStream, value: Int) {
        stream.write((value ushr 8) and 0xFF)
        stream.write(value and 0xFF)
    }

    private class BitfieldWriter(val stream: ByteArrayOutputStream) {
        var fBits = 0
        var fBitsWritten = 0

        fun writeBits(value: Int, bits: Int) {
            fBits = (fBits shl bits) or (value and ((1 shl bits) - 1))
            fBitsWritten += bits
        }

        fun padAndWriteToStream() {
            fBits = fBits shl (8 - fBitsWritten)
            stream.write(fBits)
            fBits = 0
            fBitsWritten = 0
        }
    }
}
