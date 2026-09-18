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
package com.android.settingslib.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.withStyledAttributes
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.android.settingslib.widget.preference.segmentedbarchart.R
import kotlin.math.max

/**
 * A custom view that displays a SegmentedBarChart with material colours.
 */
class SegmentedBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val emptySegmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val segmentPath = Path()
    private val segmentRect = RectF()
    private val segmentRadii = FloatArray(8)

    // Cache the click boundaries to avoid allocating in onDraw
    private val segmentBounds = ArrayList<RectF>()

    fun interface OnSegmentClickListener {
        fun onSegmentClick(item: SegmentItem)
    }

    private var listener: OnSegmentClickListener? = null

    private var segments: List<SegmentItem> = emptyList()
    private var maxValue: Float = 100f
    private val colors: IntArray
    private val cornerRadius: Float
    private val segmentCornerRadius: Float
    private val gapBetweenSegments: Float

    private val accessibilityHelper = SegmentedBarAccessibilityHelper(this)

    init {
        context.withStyledAttributes(
            attrs,
            R.styleable.SegmentedBarChartView,
            defStyleAttr,
            0
        ) {
            maxValue = getFloat(R.styleable.SegmentedBarChartView_maxValue, 100f)
            emptySegmentPaint.color = context.getColor(
                com.android.settingslib.widget.theme.R.color.settingslib_materialColorSecondaryContainer
            )
        }
        colors = context.resources.getIntArray(R.array.segmented_bar_chart_colors)
        cornerRadius = context.resources.getDimension(
            com.android.settingslib.widget.theme.R.dimen.settingslib_expressive_radius_large1
        )

        segmentCornerRadius = context.resources.getDimension(
            com.android.settingslib.widget.theme.R.dimen.settingslib_expressive_radius_extrasmall2
        )

        gapBetweenSegments = context.resources.getDimensionPixelSize(
            com.android.settingslib.widget.theme.R.dimen.settingslib_expressive_space_extrasmall1
        ).toFloat()

        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper)
    }

    /**
     * Sets the segments to be displayed in the bar chart.
     *
     * @param items The list of [SegmentItem]s to display.
     * @param newMaxValue An optional new maximum value for the chart. If not provided, the
     *                    existing maxValue is used
     *
     */
    fun setSegments(items: List<SegmentItem>, newMaxValue: Float? = null) {
        segments = items
        newMaxValue?.let { maxValue = it }

        invalidate()
        accessibilityHelper.invalidateRoot()
    }

    fun setOnSegmentClickListener(l: OnSegmentClickListener?) {
        listener = l
        // View must be clickable to receive touch events if a listener is set
        isClickable = (l != null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawSegments(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Only handle if we have a listener or are explicitly clickable
        if (listener == null && !isClickable) return super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_UP -> {
                // Check if the touch point is inside any segment's bounds
                // We only loop through 'segments.indices' to ignore the Remainder bar
                for (i in segments.indices) {
                    if (segmentBounds[i].contains(event.x, event.y)) {
                        performClick() // Standard accessibility hook
                        listener?.onSegmentClick(segments[i])
                        return true
                    }
                }
            }
            // We must return true on DOWN to receive the UP event
            MotionEvent.ACTION_DOWN -> return true
        }
        return super.onTouchEvent(event)
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        return accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun drawSegments(canvas: Canvas) {
        val totalValue = segments.sumOf { it.value.toDouble() }.toFloat()
        val effectiveTotal = if (maxValue > 0) max(maxValue, totalValue) else totalValue
        if (effectiveTotal <= 0f) return

        var currentX = 0f
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL

        // Calculate the "Remainder" (Empty Space)
        val remainingValue = max(0f, effectiveTotal - totalValue)
        val hasRemainder = remainingValue > 0f

        // Total segments to draw = Data Segments + (Maybe 1 Remainder Segment (Empty Space))
        val drawCount = segments.size + (if (hasRemainder) 1 else 0)

        while (segmentBounds.size < drawCount) {
            segmentBounds.add(RectF())
        }

        val totalDrawableWidth = width - (max(0, drawCount - 1) * gapBetweenSegments)
        if (totalDrawableWidth <= 0) return

        segments.forEachIndexed { index, segment ->
            drawSingleSegment(
                canvas, isRtl, currentX,
                value = segment.value,
                total = effectiveTotal,
                widthAvailable = totalDrawableWidth,
                isFirst = (index == 0),
                isLast = (index == drawCount - 1), // Only last if NO remainder
                paint = segmentPaint.apply { color = colors[index % colors.size] },
                rectCacheIndex = index
            )
            currentX += (segment.value / effectiveTotal * totalDrawableWidth) + gapBetweenSegments
        }

        if (hasRemainder) {
            drawSingleSegment(
                canvas, isRtl, currentX,
                value = remainingValue,
                total = effectiveTotal,
                widthAvailable = totalDrawableWidth,
                isFirst = (segments.isEmpty()), // It's first if there was no data
                isLast = true,
                paint = emptySegmentPaint,
                rectCacheIndex = segments.size
            )
        }
    }

    /**
     * Helper to reuse drawing logic for all segments
     */
    private fun drawSingleSegment(
        canvas: Canvas, isRtl: Boolean, x: Float,
        value: Float, total: Float, widthAvailable: Float,
        isFirst: Boolean, isLast: Boolean, paint: Paint, rectCacheIndex: Int
    ) {
        val segmentWidth = (value / total) * widthAvailable
        if (segmentWidth <= 0) return

        val rightEdge = x + segmentWidth
        val left = if (isRtl) width - rightEdge else x
        val right = if (isRtl) width - x else rightEdge

        segmentRect.set(left, 0f, right, height.toFloat())
        if (rectCacheIndex < segmentBounds.size) {
            segmentBounds[rectCacheIndex].set(segmentRect)
        }
        segmentPath.reset()

        // Radius Logic per segment:
        // First segment gets Start Radius.
        // Last segment gets End Radius.
        // Middle segments gets 4dp radius.
        setRadii(isFirst, isLast, isRtl)

        segmentPath.addRoundRect(segmentRect, segmentRadii, Path.Direction.CW)
        canvas.drawPath(segmentPath, paint)
    }

    private fun setRadii(isFirst: Boolean, isLast: Boolean, isRtl: Boolean) {
        segmentRadii.fill(0f)
        val roundStart = if (isFirst) cornerRadius else segmentCornerRadius
        val roundEnd = if (isLast) cornerRadius else segmentCornerRadius

        if (isRtl) {
            segmentRadii[0] = roundEnd; segmentRadii[1] = roundEnd
            segmentRadii[2] = roundStart; segmentRadii[3] = roundStart
            segmentRadii[4] = roundStart; segmentRadii[5] = roundStart
            segmentRadii[6] = roundEnd; segmentRadii[7] = roundEnd
        } else {
            segmentRadii[0] = roundStart; segmentRadii[1] = roundStart
            segmentRadii[2] = roundEnd; segmentRadii[3] = roundEnd
            segmentRadii[4] = roundEnd; segmentRadii[5] = roundEnd
            segmentRadii[6] = roundStart; segmentRadii[7] = roundStart
        }
    }

    private inner class SegmentedBarAccessibilityHelper(val host: View) :
        ExploreByTouchHelper(host) {
        // Temporary Rect to avoid allocs during data transfer
        private val tempRect = Rect()
        private val tempParentLocation = IntArray(2)

        /**
         * 1. Hit Test: Find which ID is under (x, y).
         * We use the List Index as the Virtual View ID.
         */
        override fun getVirtualViewAt(x: Float, y: Float): Int {
            for (i in segments.indices) {
                if (segmentBounds.getOrNull(i)?.contains(x, y) == true) {
                    return i
                }
            }
            return HOST_ID
        }

        /**
         * 2. Listing: Tell TalkBack what IDs exist.
         */
        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            for (i in segments.indices) {
                virtualViewIds.add(i)
            }
        }

        /**
         * 3. Details: Configure the node for a specific ID.
         */
        override fun onPopulateNodeForVirtualView(
            virtualViewId: Int,
            node: AccessibilityNodeInfoCompat
        ) {
            val segment = segments.getOrNull(virtualViewId)
            if (segment == null) {
                node.contentDescription = ""
                node.setBoundsInParent(Rect(0, 0, 0, 0))
                return
            }

            // Set content description
            node.contentDescription = getContentDescription(segment)

            // Set bounds for the focus box
            val fRect = segmentBounds.getOrNull(virtualViewId)
            if (fRect != null) {
                fRect.round(tempRect)
                host.getLocationOnScreen(tempParentLocation)
                tempRect.offset(tempParentLocation[0], tempParentLocation[1])
                node.setBoundsInScreen(tempRect)
            }

            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            node.isClickable = true
            node.className = "android.widget.Button"
        }

        private fun getContentDescription(segment: SegmentItem): String {
            val valueText = if (segment.value % 1.0f == 0f) {
                segment.value.toInt().toString()
            } else {
                segment.value.toString()
            }

            return if (!segment.label.isNullOrEmpty()) {
                "${segment.label}, $valueText"
            } else {
                valueText
            }
        }

        /**
         * 4. Actions: Handle when TalkBack performs a click.
         */
        override fun onPerformActionForVirtualView(
            virtualViewId: Int,
            action: Int,
            arguments: Bundle?
        ): Boolean {
            if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
                val segment = segments.getOrNull(virtualViewId) ?: return false

                listener?.onSegmentClick(segment)

                // Notify the system that the click happened
                invalidateVirtualView(virtualViewId)
                sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
                return true
            }
            return false
        }
    }
}
