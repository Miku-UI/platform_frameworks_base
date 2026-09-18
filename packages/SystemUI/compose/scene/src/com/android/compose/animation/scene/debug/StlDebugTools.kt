/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.compose.animation.scene.debug

import android.util.Log
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.Key
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.Scale
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.SceneTransitionLayoutImpl
import com.android.compose.animation.scene.SceneTransitionLayoutState
import com.android.compose.animation.scene.TransitionKey
import com.android.compose.animation.scene.ValueKey
import com.android.compose.animation.scene.content.Content
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.compose.animation.scene.debug.StlDebugConfig.elementFilterList
import com.android.compose.animation.scene.debug.StlDebugConfig.excludeStlsList
import com.android.compose.animation.scene.getAllNestedTransitionStates
import java.util.Locale
import kotlin.collections.mutableMapOf
import kotlin.math.absoluteValue

/**
 * ONLY USED DURING DEBUGGING Stores the contents where this element was placed in for this frame.
 */
internal val placedInContents = mutableMapOf<ElementKey, MutableSet<ContentKey>>()

internal fun Modifier.debugElement(key: ElementKey, content: Content): Modifier {
    return composed {
        val textMeasurer = rememberTextMeasurer()

        drawWithCache {
            val seed = key.hashCode()
            val color = generateDebugColor(seed)
            val strokeWidth = 1.dp
            val strokePx = strokeWidth.toPx()

            // We offset the track of the border randomly to one of 3 tracks to reduce overlapping
            // borders
            val trackIndex = (seed % 3).absoluteValue
            val inset = trackIndex * strokePx

            // Random dash pattern to further distinguish overlapping borders
            val dashIndex = (seed % 8).absoluteValue
            val dashSize = (dashIndex + 1) * 5f
            val pathEffect =
                PathEffect.dashPathEffect(
                    intervals = floatArrayOf(dashSize, dashSize),
                    phase = (dashSize * 2).toFloat(),
                )

            onDrawWithContent {
                // Draw Content first (so border is on top)
                drawContent()

                if (filterOutElement(key)) return@onDrawWithContent
                if (StlDebugConfig.showElementBorders()) {
                    drawDebugBorder(
                        color = color,
                        strokeWidth = strokePx,
                        pathEffect = pathEffect,
                        inset = inset,
                    )
                }

                if (StlDebugConfig.showElementLabels()) {
                    val text =
                        listOf(
                            "E:" + key.debugName.truncateText(),
                            "In:" + content.key.debugName.truncateText(),
                        )
                    drawDebugLabel(
                        textMeasurer = textMeasurer,
                        text = text,
                        color = color,
                        position = StlDebugConfig.elementLabelPosition,
                        verticalOffset = 0f,
                        textSize = 12.sp,
                    )
                }
            }
        }
    }
}

internal fun Modifier.debugContent(key: ContentKey): Modifier {
    return composed {
        val textMeasurer = rememberTextMeasurer()

        drawWithCache {
            val seed = key.hashCode()
            val color = generateDebugColor(seed)
            val strokeWidth = 2.dp.toPx()

            onDrawWithContent {
                if (StlDebugConfig.showContentBorders()) {
                    // 1. Draw Border FIRST (so it ends up behind the content)
                    drawDebugBorder(
                        color = color,
                        strokeWidth = strokeWidth,
                        pathEffect = null, // Solid
                        inset = 0f,
                    )
                }

                // 2. Draw Content
                drawContent()

                if (StlDebugConfig.showContentLabels()) {
                    // 3. Draw Label (Always on top)
                    drawDebugLabel(
                        textMeasurer = textMeasurer,
                        text = listOf(key.toShortDebugString()),
                        color = color,
                        position = StlDebugConfig.contentLabelPosition,
                        textSize = 12.sp,
                    )
                }
            }
        }
    }
}

internal fun Modifier.debugStl(
    state: WithoutReadObservation<SceneTransitionLayoutState>,
    debugName: String,
    nestingLevel: Int,
): Modifier {
    return composed {
        val textMeasurer = rememberTextMeasurer()

        drawWithCache {
            val textSize = 12.sp
            val seed = debugName.hashCode()
            val color = generateDebugColor(seed)
            val strokeWidth = 2.dp.toPx()
            val labelPadding = 8f
            val inset = nestingLevel * (textSize.toPx() + labelPadding) * 2

            onDrawWithContent {
                if (StlDebugConfig.showStlBorders() && !excludeStlFromDebugging(debugName)) {
                    // 1. Draw Border
                    drawDebugBorder(
                        color = color,
                        strokeWidth = strokeWidth,
                        pathEffect = null,
                        inset = 0f,
                    )
                }

                // 2. Draw Content
                drawContent()

                val isNested = nestingLevel > 0
                val typeLabel = if (isNested) "[Nested($nestingLevel)]" else ""

                val stateText =
                    state.read {
                        when (val currentState = transitionState) {
                            is TransitionState.Idle ->
                                "Idle(${currentState.currentScene.debugName.truncateText(14)})"
                            is TransitionState.Transition ->
                                "[${currentState.fromContent.debugName.truncateText(14)} -> ${currentState.toContent.debugName.truncateText(14)}]"
                        }
                    }

                if (StlDebugConfig.showStlLabels() && !excludeStlFromDebugging(debugName)) {
                    // 3. Draw Label
                    drawDebugLabel(
                        textMeasurer = textMeasurer,
                        text = listOf("${debugName.truncateText()}$typeLabel:", stateText),
                        color = color,
                        position = StlDebugConfig.stlLabelPosition,
                        textSize = textSize,
                        verticalOffset = inset,
                    )
                }
            }
        }
    }
}

/** Generates a random, stable, high-contrast color based on a seed. */
private fun generateDebugColor(seed: Int): Color {
    val rnd = kotlin.random.Random(seed)
    val hue = rnd.nextInt(360).toFloat()
    val saturation = 0.6f + (rnd.nextFloat() * 0.4f)
    val value = 0.8f + (rnd.nextFloat() * 0.2f)

    return Color.hsv(hue, saturation, value)
}

private fun DrawScope.drawDebugBorder(
    color: Color,
    strokeWidth: Float,
    pathEffect: PathEffect?,
    inset: Float,
) {
    val topLeft = Offset(inset, inset)
    val size = Size(width = size.width - (inset * 2), height = size.height - (inset * 2))

    drawRect(
        color = color,
        topLeft = topLeft,
        size = size,
        style = Stroke(width = strokeWidth, pathEffect = pathEffect),
    )
}

private fun DrawScope.drawDebugLabel(
    textMeasurer: TextMeasurer,
    text: List<String>,
    color: Color,
    position: DebugLabelPosition,
    verticalOffset: Float = 0f,
    textSize: TextUnit = 12.sp,
    padding: Float = 8f,
) {
    val textLayout =
        textMeasurer.measure(
            text = AnnotatedString(text.joinToString("\n")),
            style =
                TextStyle(
                    color = Color.Black,
                    fontSize = textSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = getTextAlignment(position),
                ),
        )
    val backgroundSize =
        Size(
            width = textLayout.size.width + (padding * 2),
            height = textLayout.size.height + (padding * 2),
        )

    val topLeft = calculateLabelPosition(size, backgroundSize, position, verticalOffset)

    drawRect(color = color, topLeft = topLeft, size = backgroundSize)
    drawText(textLayoutResult = textLayout, topLeft = topLeft + Offset(padding, padding))
}

private fun getTextAlignment(position: DebugLabelPosition): TextAlign {
    return when (position) {
        DebugLabelPosition.TopLeft,
        DebugLabelPosition.BottomLeft -> TextAlign.Left

        DebugLabelPosition.TopRight,
        DebugLabelPosition.BottomRight -> TextAlign.Right

        else -> TextAlign.Center
    }
}

private fun calculateLabelPosition(
    containerSize: Size,
    boxSize: Size,
    position: DebugLabelPosition,
    verticalOffset: Float,
): Offset {
    val x =
        when (position) {
            DebugLabelPosition.TopLeft,
            DebugLabelPosition.BottomLeft -> 0f
            DebugLabelPosition.TopRight,
            DebugLabelPosition.BottomRight -> containerSize.width - boxSize.width
            else -> (containerSize.width - boxSize.width) / 2f
        }

    val y =
        verticalOffset +
            when (position) {
                DebugLabelPosition.TopLeft,
                DebugLabelPosition.TopRight,
                DebugLabelPosition.CenterTop -> 0f
                DebugLabelPosition.BottomLeft,
                DebugLabelPosition.BottomRight,
                DebugLabelPosition.CenterBottom -> containerSize.height - boxSize.height
                DebugLabelPosition.Center -> (containerSize.height - boxSize.height) / 2f
                DebugLabelPosition.CenterHigh -> (containerSize.height / 3f) - (boxSize.height / 2f)
                DebugLabelPosition.CenterLow ->
                    (containerSize.height * 2f / 3f) - (boxSize.height / 2f)
            }

    return Offset(x, y)
}

internal fun Modifier.logElementsOnTransitionChange(
    layoutImpl: WithoutReadObservation<SceneTransitionLayoutImpl>,
    logger: StateLogger,
): Modifier =
    this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)

        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)

            if (StlDebugConfig.logElements()) {
                layoutImpl.read { logger.logOnNewTransition(this, state.transitionState) }
            }
        }
    }

internal class StateLogger {
    private var lastState: TransitionState? = null

    internal fun logOnNewTransition(
        layoutImpl: SceneTransitionLayoutImpl,
        currentState: TransitionState,
    ) {
        if (currentState != lastState) {
            performLog(layoutImpl, currentState, lastState)
            lastState = currentState
        }
    }

    private fun performLog(
        layoutImpl: SceneTransitionLayoutImpl,
        current: TransitionState,
        previous: TransitionState?,
    ) {
        val sb = StringBuilder()

        sb.appendLine(
            "\n$================================================================================================================================="
        )
        if (previous == null) {
            sb.appendLine("┌──┤ STL(${layoutImpl.debugName}) ENTERED COMPOSITION")
        } else {
            sb.appendLine("┌──┤ STL(${layoutImpl.debugName}) STATE CHANGE")
        }

        if (previous != null) sb.appendLine("├──< Before: ${previous.debugString()}")
        sb.appendLine("└──> Now:    ${current.debugString()}")
        val allTransitionStates = getAllNestedTransitionStates(layoutImpl)
        if (allTransitionStates.any { it.any { it != current } }) {
            sb.appendLine("All current transition states: $allTransitionStates")
        }

        if (layoutImpl.ancestors.isNotEmpty()) {
            sb.append("STL is a NestedSTL nested within: ")
            layoutImpl.ancestors.fastForEach {
                sb.append("STL(${it.layoutImpl.debugName}) in Content: ${it.inContent.debugName}")
            }
            sb.appendLine()
        }

        sb.appendLine(
            "=================================================================================================================================="
        )
        sb.append("Total Elements in this STL: ${layoutImpl.elements.size}")
        if (elementFilterList.isNotEmpty()) {
            sb.append(" - Filtered for Elements containing \"${elementFilterList}\"")
        }
        sb.appendLine()

        val headerFormat = "| %-23s | %-20s | %-7s | %-6s | %-13s | %-18s | %-5s | %-12s"
        sb.appendLine(
            String.format(
                Locale.ENGLISH,
                headerFormat,
                "Element",
                "Content",
                "Placed",
                "State",
                "Size",
                "Offset",
                "Alpha",
                "Scale",
            )
        )
        sb.appendLine("-" + "-".repeat(128))

        layoutImpl.elements.forEach { (key, element) ->
            if (filterOutElement(key)) return@forEach

            val elementName = key.debugName.truncateText(23)
            val placedInContents = placedInContents[key] ?: emptySet()

            // Sort by: local content > placed content > alphabetical
            val allContents =
                element.stateByContent.keys.sortedWith(
                    Comparator { a, b ->
                        val aLocal = layoutImpl.isLocalContent(a)
                        val bLocal = layoutImpl.isLocalContent(b)
                        if (aLocal && !bLocal) return@Comparator -1
                        if (!aLocal && bLocal) return@Comparator 1

                        val aPlaced = placedInContents.contains(a)
                        val bPlaced = placedInContents.contains(b)
                        if (aPlaced && !bPlaced) return@Comparator -1
                        if (!aPlaced && bPlaced) return@Comparator 1

                        return@Comparator a.debugName.compareTo(b.debugName)
                    }
                )

            allContents.forEachIndexed { index, contentKey ->
                val state = element.stateByContent[contentKey]!!
                val isPlaced = placedInContents.contains(contentKey)
                val isLocal = layoutImpl.isLocalContent(contentKey)

                val prefix = if (index == 0) elementName else ""

                sb.appendRow(
                    elementName = prefix,
                    contentName = contentKey.debugName,
                    icon = if (isPlaced) "✅" else "❌",
                    placedText = if (isPlaced) "Yes" else "No",
                    stateLabel = "Idle",
                    size = state.targetSize,
                    offset = state.targetOffset,
                    alpha = 1.0f,
                    scale = Scale(1f, 1f),
                )

                val isInterpolatedStateDifferent =
                    state.lastSize != state.targetSize || state.lastOffset != state.targetOffset
                if (isInterpolatedStateDifferent) {
                    sb.appendRow(
                        elementName = "",
                        contentName = "",
                        icon = "🔄",
                        placedText = "",
                        stateLabel = "Interp",
                        size = state.lastSize,
                        offset = state.lastOffset,
                        alpha = state.lastAlpha,
                        scale = state.lastScale,
                        isEmptyCells = true,
                    )
                }

                if (!isLocal) {
                    val context = getContentInfo(layoutImpl, contentKey)
                    sb.appendLine(String.format("| %-23s   ↳ %s", "", context))
                }
            }
            sb.appendLine("-" + "-".repeat(128))
        }

        Log.i(TAG, sb.toString())
    }

    private fun StringBuilder.appendRow(
        elementName: String,
        contentName: String,
        icon: String,
        placedText: String,
        stateLabel: String,
        size: IntSize,
        offset: Offset,
        alpha: Float,
        scale: Scale,
        isEmptyCells: Boolean = false,
    ) {

        val placedColWidth = 6
        val rawString = "$icon $placedText".trim()
        val adjustment = if (isEmptyCells) 1 else 0

        val paddingNeeded = (placedColWidth - rawString.length + adjustment).coerceAtLeast(0)
        val placedColStr = rawString + " ".repeat(paddingNeeded)

        val rowFormat = "| %-23s | %-20s | %s | %-6s | %-13s | %-18s | %-5s | %-12s"

        val contentStr = if (isEmptyCells) "" else contentName.truncateText(20)

        val sizeStr = "[${formatInt(size.width)}x${formatInt(size.height)}]"
        val offsetStr = "(${formatFloat(offset.x)}, ${formatFloat(offset.y)})"
        val alphaStr = formatFloat(alpha)
        val scaleStr = "(${formatFloat(scale.scaleX)}, ${formatFloat(scale.scaleY)})"

        appendLine(
            String.format(
                Locale.ENGLISH,
                rowFormat,
                elementName,
                contentStr,
                placedColStr,
                stateLabel,
                sizeStr,
                offsetStr,
                alphaStr,
                scaleStr,
            )
        )
    }

    private fun getContentInfo(layoutImpl: SceneTransitionLayoutImpl, content: ContentKey): String {
        val parent = layoutImpl.ancestors.firstOrNull { it.layoutImpl.isLocalContent(content) }
        return if (parent != null) {
            "is part of a parent STL(${parent.layoutImpl.debugName})"
        } else {
            "Is part of a child or sibling STL"
        }
    }

    private fun TransitionState.debugString(): String =
        when (this) {
            is TransitionState.Idle -> "Idle(${currentScene.debugName})"
            is TransitionState.Transition ->
                "Transition(${fromContent.debugName} -> ${toContent.debugName})"
        }
}

internal fun Modifier.logElementState(
    layoutImpl: SceneTransitionLayoutImpl,
    key: ElementKey,
    content: Content,
): Modifier =
    this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)

        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)

            if (filterOutElementExclusive(key)) return@layout
            Snapshot.withoutReadObservation {
                val element = layoutImpl.elements[key]
                val placedInContents = placedInContents[key] ?: emptyList()
                val state = element?.stateByContent?.get(content.key)
                if (state != null && placedInContents.contains(content.key)) {
                    val transition = element.lastTransition
                    val progress = transition?.progress ?: 0f
                    val offset = state.lastOffset

                    val logMsg =
                        String.format(
                            Locale.ENGLISH,
                            "Size: %4d x %4d | Off: %6.1f, %6.1f | A: %4s | S: %4s, %4s | %3.0f%% | During: %s ",
                            placeable.width,
                            placeable.height,
                            offset.x,
                            offset.y,
                            formatFloat(state.lastAlpha),
                            formatFloat(state.lastScale.scaleX),
                            formatFloat(state.lastScale.scaleY),
                            progress * 100,
                            transition.toString(),
                        )

                    Log.d(TAG, getContextString(content.key, element.key) + logMsg)
                }
            }
        }
    }

private fun formatFloat(value: Float): String {
    if (value.isNaN()) return "NaN"
    if (value == Float.MAX_VALUE || value == Float.MIN_VALUE) return "Unsp"
    return String.format(Locale.ENGLISH, "%.2f", value)
}

private fun formatInt(value: Int): String {
    if (value == Int.MAX_VALUE || value == Int.MIN_VALUE) return "Unsp"
    return value.toString()
}

/**
 * performLog is separate from the inlined log function such that this logic is not copied
 * everywhere which bloats code and increases instruction cache pressure.
 */
internal fun performLog(elementKey: ElementKey, contentKey: ContentKey? = null, msg: String) {
    Snapshot.withoutReadObservation {
        val context = getContextString(contentKey, elementKey)
        Log.v(TAG, context + msg)
    }
}

private fun getContextString(contentKey: ContentKey?, elementKey: ElementKey): String {
    val contentStr = if (contentKey == null) "" else "[${contentKey.toShortDebugString()}]"
    return "[${elementKey.toShortDebugString()}]$contentStr "
}

/**
 * Shortens the text to the [maxLength] if it exceeds it. The text is represented as: "<first m/2
 * chars>..<last m/2 chars>"
 */
private fun String.truncateText(maxLength: Int = 18): String {
    if (this.length <= maxLength) return this
    val split = (maxLength - 2) / 2
    val first = this.take(split)
    val last = this.takeLast(split)
    return "$first..$last"
}

private fun Key.toShortDebugString(): String {
    return when (this) {
        is SceneKey -> "S:${debugName.truncateText()}"
        is OverlayKey -> "O:${debugName.truncateText()}"
        is ElementKey -> "E:${debugName.truncateText()}"
        is TransitionKey -> "T:${debugName.truncateText()}"
        is ValueKey -> "V:${debugName.truncateText()}"
    }
}

/**
 * Returns true if the Element should be filtered out. It is filtered out when none of the filters
 * match the debugName (case insensitive). If an empty filter is set, filter out everything (always
 * returns true).
 */
internal fun filterOutElementExclusive(key: ElementKey): Boolean {
    return elementFilterList.all { token -> !key.debugName.equals(token, ignoreCase = true) }
}

/**
 * Returns true if the Element should be filtered out. It is filtered out when none of the filters
 * match the debugName (case insensitive). If an empty filter is set, nothing is filtered out
 * (always returns false).
 */
internal fun filterOutElement(key: ElementKey): Boolean {
    if (elementFilterList.isEmpty()) return false
    return filterOutElementExclusive(key)
}

/** Returns true if the STL with [debugName] should be excluded from debugging. */
internal fun excludeStlFromDebugging(debugName: String): Boolean {
    return excludeStlsList.any { debugName.equals(it, ignoreCase = true) }
}

internal const val TAG = "StlDebug"
