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
package com.android.wm.shell.hierarchy.containers

import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Binder
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.View
import android.view.WindowManager
import android.view.WindowlessWindowManager
import android.window.WindowContainerToken
import androidx.core.graphics.toRect
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.hierarchy.utils.HierarchyUtils
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_MODES

typealias RootViewSupplier = (Context, ViewOverlayContainer) -> View

/**
 * A shell-only container in the ContainerHierarchy that be used to display an embedded view
 * hierarchy.
 *
 * Note: The bounds of this container are undefined until {@link #initialize()} is called.
 */
class ViewOverlayContainer(
    token: WindowContainerToken,
    private val rootViewSupplier: RootViewSupplier,
    private val overrideWidth: Int? = null,
    private val overrideHeight: Int? = null,
) : Container(token = token, name = token.asBinder().interfaceDescriptor) {
    // The WWM for the SCVH
    private var windowlessWM: WindowlessWindowManager? = null
    // The view host for this container
    private var viewHost: SurfaceControlViewHost? = null
    // The root view of this container. This should only be used after 'initialize()' is called
    lateinit var rootView: View

    /**
     * Returns the layout params for this windowless window.
     */
    private fun getLayoutParams(parent: Container): WindowManager.LayoutParams {
        val width = overrideWidth ?: parent.props.bounds.width().toInt()
        val height = overrideHeight ?: parent.props.bounds.height().toInt()
        val lp = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSPARENT
        )
        lp.token = Binder()
        lp.title = name
        lp.privateFlags =
            lp.privateFlags or (WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION
                    or WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY)
        return lp
    }

    /**
     * Initializes the overlay container. The container must be in the hierarchy before this is
     * called.
     */
    fun initialize(windowContext: Context) {
        val parent = this.parent!!

        rootView = rootViewSupplier(windowContext, this)

        // Set WM flags, tokens, and sizing
        val lp = getLayoutParams(parent)
        rootView.layoutParams = lp

        // Create a new leash
        val builder = SurfaceControl.Builder()
            .setContainerLayer()
            .setName(name)
            .setCallsite(name)
        builder.setParent(parent.leash)
        leash = builder.build()

        // Create a new viewhost
        windowlessWM = WindowlessWindowManager(parent.props.config, leash, null)
        viewHost = SurfaceControlViewHost(
            windowContext, windowContext.display, windowlessWM!!, name,
        )
        viewHost!!.setView(rootView, lp)

        // Update the container properties to match
        val relBounds = RectF(
            0f,
            0f,
            lp.width.toFloat(),
            lp.height.toFloat()
        )
        surface.updateReferenceFrame(relBounds)
        // Update the configuration for this container to match its parents
        props.config.setTo(parent.props.config)
        props.config.windowConfiguration.setBounds(relBounds.toRect())
        updateSurfaceFromPropertyChanges()
    }

    /** @see Container.updateConfigurationFromParentIfNeeded */
    override fun updateConfigurationFromParentIfNeeded(parentConfig: Configuration) {
        val viewHost = this.viewHost
        if (viewHost == null) {
            return
        }

        // Update the configuration to match the parent container
        if (props.config.diff(parentConfig) != 0) {
            ProtoLog.v(WM_SHELL_MODES, "Updating view overlay from config changes: %s", name)
            props.config.setTo(parentConfig)

            // Update the config of the embedded view hierarchy & relayout to pick up the changes
            windowlessWM!!.setConfiguration(props.config)
            viewHost.relayout(getLayoutParams(parent!!))
            updateSurfaceFromPropertyChanges()
        }
    }

    /**
     * Releases all surfaces & internal state associated with the overlay.
     * The caller must provide a SurfaceControl.Transaction to synchronize the removal of the
     * surface, and the caller must apply the provided transaction after this method is called.
     *
     * FUTURE: We should probably track these for cleanup
     */
    fun release(tx: SurfaceControl.Transaction) {
        if (viewHost != null) {
            viewHost!!.release()
        }
        if (leash != NULL_SURFACE) {
            tx.remove(leash)
            leash = NULL_SURFACE
        }
    }
}