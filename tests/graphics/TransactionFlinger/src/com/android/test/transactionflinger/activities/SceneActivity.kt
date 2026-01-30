/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.test.transactionflinger.activities

import android.os.Bundle
import android.view.Choreographer
import android.view.Choreographer.VsyncCallback
import android.view.SurfaceControl
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import com.android.test.transactionflinger.Scene

/**
 * Base implementation for an activity containing a Scene
 */
abstract class SceneActivity : ComponentActivity(), SurfaceHolder.Callback, VsyncCallback {
    private lateinit var surfaceView: SurfaceView
    private lateinit var choreographer: Choreographer
    private lateinit var scene: Scene
    private var width = 0
    private var height = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hide the system bars. Ain't dealing with this when we actually start setting up a scene
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.hide(WindowInsets.Type.systemBars())
        actionBar?.hide()

        choreographer = Choreographer.getInstance()
        scene = obtainScene()

        surfaceView = SurfaceView(this).apply {
            holder.addCallback(this@SceneActivity)
        }

        setContentView(surfaceView)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        this@SceneActivity.width = width
        this@SceneActivity.height = height
        SurfaceControl.Transaction()
            .reparent(scene.surfaceControl, surfaceView.surfaceControl).apply()
        choreographer.postVsyncCallback(this)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        width = 0
        height = 0
        SurfaceControl.Transaction()
            .reparent(scene.surfaceControl, null)
    }

    override fun onVsync(data: Choreographer.FrameData) {
        if (width == 0 || height == 0) {
            return
        }
        scene.drawAndSubmit(data, width, height)
        choreographer.postVsyncCallback(this@SceneActivity)
    }

    abstract fun obtainScene(): Scene
}