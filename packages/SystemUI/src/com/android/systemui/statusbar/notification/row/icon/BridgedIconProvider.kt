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

package com.android.systemui.statusbar.notification.row.icon

import android.app.Notification.BridgedNotificationMetadata
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import com.android.internal.R
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject
import kotlin.math.acos
import kotlin.math.sqrt

private const val TAG = "BridgedIconProvider"

/** Provides drawables for bridged notifications. */
interface BridgedIconProvider {
    /** Returns a drawable that represents a bridged notification. */
    fun getBridgedIcon(context: Context, bridgedMetadata: BridgedNotificationMetadata): Drawable?
}

@SysUISingleton
class BridgedIconProviderImpl @Inject constructor() : BridgedIconProvider {
    override fun getBridgedIcon(
        context: Context,
        bridgedMetadata: BridgedNotificationMetadata,
    ): Drawable? {
        val baseIcon = bridgedMetadata.appIcon
        val baseDrawable = baseIcon.loadDrawable(context) ?: return null
        val layers = mutableListOf<Drawable>(baseDrawable, createPlateOverlayDrawable(context))
        layers.add(createPositionedPhoneIconDrawable(context))
        return LayerDrawable(layers.toTypedArray())
    }

    private fun createPlateOverlayDrawable(context: Context): Drawable {
        return object : Drawable() {
            private val paint =
                Paint().apply {
                    color = context.getColor(R.color.white)
                    alpha = (255 * 0.85).toInt()
                    isAntiAlias = true
                    style = Paint.Style.FILL
                }

            override fun draw(canvas: Canvas) {
                val bounds = bounds
                val ovalRect = RectF(bounds)
                val path = Path()
                val radius = bounds.width() / 2f
                val centerX = bounds.centerX().toFloat()
                val centerY = bounds.centerY().toFloat()

                // The plate overlay covers the bottom third of the icon.
                val verticalOffset = radius / 3f
                val plateTopY = centerY + verticalOffset

                // To draw the plate, we first calculate the x-coordinates of where the
                // horizontal line (chord) and the circle intersect using the Pythagorean
                // theorem: (a^2 + b^2 = c^2).
                // In this case, 'c' is the radius, 'a' is the verticalOffset, and 'b' is
                // the horizontal distance from the center to the intersection point (the
                // value of 'x').
                val x =
                    sqrt((radius * radius - verticalOffset * verticalOffset).toDouble()).toFloat()

                // We calculate the angle of the arc that forms the bottom of the plate by
                // constructing a right-angle triangle with the radius as the hypotenuse
                // and the verticalOffset as the adjacent side. Then, basic trigonometry
                // is used to calculate the angle of the arc:
                // angle = acos(adjacent / hypotenuse)

                val angle = Math.toDegrees(acos(verticalOffset / radius.toDouble())).toFloat()

                // The start angle for the arc is 90 degrees (the bottom of the circle in
                // Android's coordinate system) minus the calculated angle. The sweep angle
                // is twice the calculated angle, covering the entire arc of the segment.
                val startAngle = 90 - angle
                val sweepAngle = 2 * angle

                // We then construct the path.
                path.reset()

                // Move to the top-left corner of the plate.
                path.moveTo(centerX - x, plateTopY)

                // Draw a line to the top-right corner of the plate.
                path.lineTo(centerX + x, plateTopY)

                // Draw the arc for the bottom of the plate.
                path.arcTo(ovalRect, startAngle, sweepAngle, false)

                // Close the path to create a filled shape.
                path.close()
                canvas.drawPath(path, paint)
            }

            override fun setAlpha(alpha: Int) {
                paint.alpha = alpha
            }

            override fun setColorFilter(colorFilter: ColorFilter?) {
                paint.colorFilter = colorFilter
            }

            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }
    }

    private fun createPositionedPhoneIconDrawable(context: Context): Drawable {
        val phoneIcon = context.getDrawable(R.drawable.ic_mobile_phone)?.mutate()
        return object : Drawable() {
            init {
                phoneIcon?.setTint(context.getColor(R.color.system_on_surface_variant_light))
            }

            override fun draw(canvas: Canvas) {
                if (phoneIcon == null) return
                val bounds = bounds
                val r = bounds.width() / 2f

                // The phone icon is centered within the plate.
                // First, we calculate the dimensions of the plate.
                val verticalOffset = r / 3f
                val segmentHeight = r - verticalOffset

                // The icon size is a fraction of the plate's height to ensure it fits
                // comfortably within the plate.
                val iconSize = (segmentHeight * 0.8f).toInt()

                // Now we can calculate the position of the icon.
                val cx = bounds.centerX()
                val plateTopY = (bounds.centerY() + verticalOffset).toInt()
                val plateBottomY = bounds.bottom
                val plateCenterY = (plateTopY + plateBottomY) / 2
                val iconLeft = cx - iconSize / 2
                val iconTop = plateCenterY - iconSize / 2

                // The bounds of the phone icon are set and the icon is drawn.
                phoneIcon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                phoneIcon.draw(canvas)
            }

            override fun setTintList(tint: android.content.res.ColorStateList?) {}

            override fun setAlpha(alpha: Int) {
                phoneIcon?.alpha = alpha
            }

            override fun setColorFilter(colorFilter: ColorFilter?) {}

            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }
    }
}
