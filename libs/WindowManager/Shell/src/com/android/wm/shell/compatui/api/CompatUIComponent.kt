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

package com.android.wm.shell.compatui.api

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Binder
import android.view.IWindow
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.View
import android.view.WindowManager
import android.view.WindowlessWindowManager
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.common.SyncTransactionQueue

/** The component created after a {@link CompatUISpec} definition */
class CompatUIComponent(
    private val spec: CompatUISpec,
    private val id: String,
    private var context: Context,
    private val sharedStateRepository: CompatUISharedStateRepository,
    private val componentUIComponentRepository: CompatUIComponentRepository,
    private var compatUIInfo: CompatUIInfo,
    private val syncQueue: SyncTransactionQueue,
    private var displayLayout: DisplayLayout?,
) :
    WindowlessWindowManager(
        compatUIInfo.taskInfo.configuration,
        /* rootSurface */
        null,
        /* hostInputToken */
        null,
    ) {

    private val tag
        get() = "CompatUI {id = $id}"

    private var leash: SurfaceControl? = null

    private var layout: View? = null

    /** Utility class for adding and releasing a View hierarchy for this [ ] to `mLeash`. */
    protected var viewHost: SurfaceControlViewHost? = null

    override fun setConfiguration(configuration: Configuration?) {
        super.setConfiguration(configuration)
        configuration?.let { context = context.createConfigurationContext(it) }
    }

    /**
     * Invoked every time a new CompatUIInfo comes from core
     *
     * @param newInfo The new CompatUIInfo object
     */
    fun update(newInfo: CompatUIInfo) {
        compatUIInfo = newInfo
        updateUI()
    }

    fun release() {
        spec.log("$tag releasing.....")
        // Implementation empty
        // Hiding before releasing to avoid flickering when transitioning to the Home screen.
        layout?.visibility = View.GONE
        layout = null
        spec.layout.viewReleaser()
        spec.log("$tag layout releaser invoked!")
        viewHost?.release()
        viewHost = null
        leash?.run {
            val localLeash: SurfaceControl = this
            syncQueue.runInSync { t: SurfaceControl.Transaction -> t.remove(localLeash) }
            leash = null
            spec.log("${this@CompatUIComponent.tag} leash removed")
        }
        spec.log("$tag released")
    }

    override fun getParentSurface(
        window: IWindow,
        attrs: WindowManager.LayoutParams,
    ): SurfaceControl? {
        val className = javaClass.simpleName
        val builder =
            SurfaceControl.Builder()
                .setContainerLayer()
                .setName(className + "Leash")
                .setHidden(false)
                .setCallsite("$className#attachToParentSurface")
        attachToParentSurface(builder)
        leash = builder.build()
        initSurface(leash)
        return leash
    }

    fun attachToParentSurface(builder: SurfaceControl.Builder) {
        compatUIInfo.listener?.attachChildSurfaceToTask(compatUIInfo.taskInfo.taskId, builder)
    }

    fun initLayout(newCompatUIInfo: CompatUIInfo) {
        compatUIInfo = newCompatUIInfo
        spec.log("$tag updating...")
        check(viewHost == null) { "A UI has already been created with this window manager." }
        val componentState: CompatUIComponentState? =
            componentUIComponentRepository.stateForComponent(id)
        spec.log("$tag state: $componentState")
        // We inflate the layout
        layout = spec.layout.viewBuilder(context, compatUIInfo, componentState)
        layout?.let {
            spec.log("$tag adding view $it to created host")
            viewHost = createSurfaceViewHost().apply { setView(it, getWindowLayoutParams()) }
            updateSurfacePosition()
        }
    }

    /** Creates a [SurfaceControlViewHost] for this window manager. */
    fun createSurfaceViewHost(): SurfaceControlViewHost =
        SurfaceControlViewHost(context, context.display, this, javaClass.simpleName)

    fun relayout() {
        spec.log("$tag relayout... ob viewHost $viewHost")
        viewHost?.run {
            relayout(getWindowLayoutParams())
            updateSurfacePosition()
        }
    }

    protected fun updateSurfacePosition() {
        val positionFactory = spec.layout.positionFactory
        val componentTag = tag
        if (positionFactory == null) {
            spec.log("$componentTag positionFactory missing for component ${spec.name}")
            return
        }
        if (layout == null) {
            spec.log("$componentTag Layout not available")
            return
        }
        val sharedState = sharedStateRepository.find(compatUIInfo.taskId)
        if (sharedState == null) {
            spec.log("$componentTag SharedState not available for component ${spec.name}")
            return
        }
        layout?.let {
            val newPosition =
                positionFactory(
                    it,
                    compatUIInfo,
                    sharedState,
                    componentUIComponentRepository.stateForComponent(id),
                )
            spec.log("$componentTag updateSurfacePosition($newPosition) on layout $layout")
            updateSurfacePosition(newPosition)
        }
    }

    protected fun getWindowLayoutParams(width: Int, height: Int): WindowManager.LayoutParams {
        // Cannot be wrap_content as this determines the actual window size
        val winParams =
            WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                spec.layout.layoutParamFlags,
                PixelFormat.TRANSLUCENT,
            )
        winParams.token = Binder()
        winParams.title = javaClass.simpleName + compatUIInfo.taskInfo.taskId
        winParams.privateFlags =
            winParams.privateFlags or
                (WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION or
                    WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY)
        spec.log("$tag getWindowLayoutParams $winParams")
        return winParams
    }

    /** Gets the layout params. */
    protected fun getWindowLayoutParams(): WindowManager.LayoutParams =
        layout?.let { componentView ->
            sharedState?.let { state ->
                val componentSize =
                    spec.layout.sizeFactory(componentView, compatUIInfo, state, componentState)
                return getWindowLayoutParams(componentSize.width, componentSize.height)
            }
            return WindowManager.LayoutParams()
        } ?: WindowManager.LayoutParams()

    protected fun updateSurfacePosition(position: Point) {
        spec.log("$tag updateSurfacePosition on leash $leash")
        leash?.run {
            syncQueue.runInSync { t: SurfaceControl.Transaction ->
                if (!isValid) {
                    spec.log("${this@CompatUIComponent.tag} The leash has been released.")
                    return@runInSync
                }
                spec.log("${this@CompatUIComponent.tag} settings position  $position")
                t.setPosition(this, position.x.toFloat(), position.y.toFloat())
            }
        }
    }

    private fun updateUI() {
        spec.log("$tag updating ui")
        setConfiguration(compatUIInfo.taskInfo.configuration)
        layout?.run {
            spec.log("${this@CompatUIComponent.tag} viewBinder execution...")
            sharedStateRepository.find(compatUIInfo.taskId)?.let { sharedState ->
                spec.layout.viewBinder(this, compatUIInfo, sharedState, componentState)
                relayout()
            }
        }
    }

    private fun initSurface(leash: SurfaceControl?) {
        syncQueue.runInSync { t: SurfaceControl.Transaction ->
            if (leash == null || !leash.isValid) {
                spec.log("${this@CompatUIComponent.tag} The leash has been released.")
                return@runInSync
            }
            t.setLayer(leash, spec.layout.zOrder)
        }
    }

    private val componentState: CompatUIComponentState?
        get() = componentUIComponentRepository.stateForComponent(id)

    private val sharedState: CompatUISharedState?
        get() = sharedStateRepository.find(compatUIInfo.taskId)

    private val taskId: Int
        get() = compatUIInfo.taskId
}
