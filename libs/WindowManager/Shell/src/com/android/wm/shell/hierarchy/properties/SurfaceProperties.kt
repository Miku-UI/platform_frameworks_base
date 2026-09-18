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
package com.android.wm.shell.hierarchy.properties

import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.view.SurfaceControl
import android.window.WindowContainerToken
import com.android.wm.shell.hierarchy.containers.Container
import com.android.wm.shell.protolog.ShellProtoLogGroup

private val EMPTY_RECT = Rect(0, 0, -1, -1)

/**
 * Persisted properties for a container's surface.
 *
 * This is a bit of a thin wrapper over SurfaceControl.Transaction, but we need to persist the state
 * for handoff between modes, and we currently need to rely on explicitly updating these properties
 * because opaque Transactions can be passed to Shell via various channels (from which we can not
 * extract these properties).
 */
class SurfaceProperties(
    private val container: Container
) {
    // These persisted properties are generally read-only and should be set via the setters below.

    // The reference frame is the bounds used to calculate the transform to the requested relative
    // bounds. It's often easier to think about transformations between rects and these bounds are
    // relative because the transform is also relative to the parent in SF. This frame is generally
    // the same as the container property bounds.
    var referenceFrame = RectF()
        private set
    var relBounds = RectF()
        private set

    // Matrix relative to the parent surface, this is only used for updating the surface control
    private var tmpMatrix = Matrix(Matrix.IDENTITY_MATRIX)
    private var tmpMatrixValues = FloatArray(9)

    var alpha = 1f
        private set
    var crop = Rect(EMPTY_RECT)
    var cornerRadius = floatArrayOf(0f, 0f, 0f, 0f)
        private set

    // Only valid if relative to another surface
    var relativeToContainer: WindowContainerToken? = null
        private set

    // Only valid if not relative layer to another surface
    var layer = 0
        private set
    var backgroundBlurRadius = 0
        private set
    var backgroundBlurScale = 1f
        private set
    var visibleRequested = false
        private set

    /**
     * Sets the reference frame to calculate the matrix to apply the requested bounds.
     */
    fun updateReferenceFrame(newReferenceFrame: RectF) {
        if (referenceFrame != newReferenceFrame) {
            if (relBounds.isEmpty) {
                relBounds.set(newReferenceFrame)
                return
            }
            // If we are changing the new original bounds, then make the last set relative bounds
            // relative to the new original bounds by default, and the mode associated with the
            // container can then decide if it wants to clobber or pick up from the previous state
            val offsetX = relBounds.left - referenceFrame.left
            val offsetY = relBounds.top - referenceFrame.top
            referenceFrame.set(newReferenceFrame)
            relBounds.offsetTo(
                newReferenceFrame.left + offsetX,
                newReferenceFrame.top + offsetY
            )
        }
    }

    /**
     * Updates the transform of this surface based on the reference frame for this surface.
     * These bounds are always relative to the parent surface.
     */
    fun setBounds(
        tx: SurfaceControl.Transaction,
        newRelBounds: RectF
    ): SurfaceProperties {
        if (WARN_ON_OUT_OF_BOUNDS && referenceFrame.contains(newRelBounds)) {
            Log.w(ShellProtoLogGroup.WM_SHELL_MODES.tag,
                "Non-contained bounds requested for ${container.name}: " +
                        "ref=$referenceFrame bounds=$newRelBounds")
        }
        val refBounds = RectF(referenceFrame)
        refBounds.offsetTo(0f, 0f)
        val targetBounds = RectF(newRelBounds)
        targetBounds.offsetTo(0f, 0f)
        tmpMatrix.setRectToRect(refBounds, targetBounds, Matrix.ScaleToFit.FILL)
        tmpMatrix.postTranslate(newRelBounds.left, newRelBounds.top)
        tx.setMatrix(
            container.leash,
            tmpMatrix,
            tmpMatrixValues
        )
        relBounds.set(newRelBounds)
        return this
    }

    /** @see SurfaceControl.Transaction.setAlpha */
    fun setAlpha(tx: SurfaceControl.Transaction, alpha: Float): SurfaceProperties {
        tx.setAlpha(container.leash, alpha)
        this.alpha = alpha
        return this
    }

    /** @see SurfaceControl.Transaction.setCrop */
    fun setCrop(tx: SurfaceControl.Transaction, crop: Rect = EMPTY_RECT): SurfaceProperties {
        tx.setCrop(container.leash, crop)
        this.crop.set(crop)
        return this
    }

    /** @see SurfaceControl.Transaction.setCornerRadius */
    fun setCornerRadius(
        tx: SurfaceControl.Transaction,
        r1: Float,
        r2: Float,
        r3: Float,
        r4: Float
    ): SurfaceProperties {
        tx.setCornerRadius(
            container.leash,
            r1,
            r2,
            r3,
            r4
        )
        this.cornerRadius[0] = r1
        this.cornerRadius[1] = r2
        this.cornerRadius[2] = r3
        this.cornerRadius[3] = r4
        return this
    }

    /** @see SurfaceControl.Transaction.setRelativeLayer */
    fun setRelativeToContainer(
        tx: SurfaceControl.Transaction,
        otherContainer: Container,
        layer: Int
    ): SurfaceProperties {
        tx.setRelativeLayer(
            container.leash,
            otherContainer.leash,
            layer
        )
        this.relativeToContainer = otherContainer.token
        return this
    }

    /** @see SurfaceControl.Transaction.setLayer */
    fun setLayer(tx: SurfaceControl.Transaction, layer: Int): SurfaceProperties {
        tx.setLayer(container.leash, layer)
        this.layer = layer
        return this
    }

    /**
     * @see SurfaceControl.Transaction.setBackgroundBlurRadius
     * @see SurfaceControl.Transaction.setBackgroundBlurScale
     */
    fun setBackgroundBlur(
        tx: SurfaceControl.Transaction,
        radius: Int,
        scale: Float
    ): SurfaceProperties {
        tx.setBackgroundBlurRadius(
            container.leash,
            radius
        )
        tx.setBackgroundBlurScale(
            container.leash,
            scale
        )
        this.backgroundBlurRadius = radius
        this.backgroundBlurScale = scale
        return this
    }

    /** @see SurfaceControl.Transaction.show */
    fun show(tx: SurfaceControl.Transaction): SurfaceProperties {
        tx.show(container.leash)
        this.visibleRequested = true
        return this
    }

    /** @see SurfaceControl.Transaction.hide */
    fun hide(tx: SurfaceControl.Transaction): SurfaceProperties {
        tx.hide(container.leash)
        this.visibleRequested = false
        return this
    }

    /**
     * Returns a copy of the surface container properties.
     */
    fun copy(): SurfaceProperties {
        val props = SurfaceProperties(container)
        props.alpha = alpha
        props.crop.set(crop)
        props.cornerRadius = cornerRadius.copyOf()
        props.relativeToContainer = relativeToContainer
        props.layer = layer
        props.backgroundBlurRadius = backgroundBlurRadius
        props.backgroundBlurScale = backgroundBlurScale
        props.visibleRequested = visibleRequested
        return props
    }

    override fun toString(): String {
        val props = "layer=$layer alpha=$alpha crop=${crop.toShortString()}" +
                " refFrame=${referenceFrame.toShortString()} bounds=${relBounds.toShortString()}" +
                " bgBlur=$backgroundBlurRadius"
        return "s {$props}"
    }

    companion object {
        // Enables debug logging whenever newly set relative surface bounds are not contained within
        // the reference bounds. This can lead to false negatives, but it also can also catch issues
        // where we unintentionally set absolute bounds instead of relative surface bounds
        const val WARN_ON_OUT_OF_BOUNDS = true
    }
}