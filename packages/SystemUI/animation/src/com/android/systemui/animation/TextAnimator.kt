/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.fonts.FontVariationAxis
import android.text.Layout
import android.util.Log
import android.util.LruCache
import androidx.annotation.VisibleForTesting
import com.android.app.animation.Interpolators

interface TypefaceVariantCache {
    val fontCache: FontCache
    val animationFrameCount: Int

    fun getTypefaceForVariant(fvar: String?): Typeface?

    companion object {
        @JvmStatic
        fun createVariantTypeface(baseTypeface: Typeface, fVar: String?): Typeface {
            if (fVar.isNullOrEmpty()) {
                return baseTypeface
            }

            val axes =
                FontVariationAxis.fromFontVariationSettings(fVar)?.toMutableList()
                    ?: mutableListOf()
            axes.removeIf { !baseTypeface.isSupportedAxes(it.openTypeTagValue) }

            return if (axes.isEmpty()) {
                baseTypeface
            } else {
                Typeface.createFromTypefaceWithVariation(baseTypeface, axes)
            }
        }
    }
}

class TypefaceVariantCacheImpl(
    private var baseTypeface: Typeface,
    override val animationFrameCount: Int,
) : TypefaceVariantCache {
    private val cache = LruCache<String, Typeface>(TYPEFACE_CACHE_MAX_ENTRIES)
    override val fontCache = FontCacheImpl(animationFrameCount)

    override fun getTypefaceForVariant(fvar: String?): Typeface {
        if (fvar == null) {
            return baseTypeface
        }
        cache[fvar]?.let {
            return it
        }

        return TypefaceVariantCache.createVariantTypeface(baseTypeface, fvar).also {
            cache.put(fvar, it)
        }
    }

    companion object {
        private const val TYPEFACE_CACHE_MAX_ENTRIES = 5
    }
}

interface TextAnimatorListener : TextInterpolatorListener {
    fun onInvalidate() {}
}

/**
 * This class provides text animation between two styles.
 *
 * Currently this class can provide text style animation for text weight and text size. For example
 * the simple view that draws text with animating text size is like as follows:
 * <pre> <code>
 * ```
 *     class SimpleTextAnimation : View {
 *         @JvmOverloads constructor(...)
 *
 *         private val layout: Layout = ... // Text layout, e.g. StaticLayout.
 *
 *         // TextAnimator tells us when needs to be invalidate.
 *         private val animator = TextAnimator(layout) { invalidate() }
 *
 *         override fun onDraw(canvas: Canvas) = animator.draw(canvas)
 *
 *         // Change the text size with animation.
 *         fun setTextSize(sizePx: Float, animate: Boolean) {
 *             animator.setTextStyle("" /* unchanged fvar... */, sizePx, animate)
 *         }
 *     }
 * ```
 * </code> </pre>
 */
class TextAnimator(
    layout: Layout,
    private val typefaceCache: TypefaceVariantCache,
    private val listener: TextAnimatorListener? = null,
) {
    var textInterpolator = TextInterpolator(layout, typefaceCache, listener)
    @VisibleForTesting var createAnimator: () -> ValueAnimator = { ValueAnimator.ofFloat(1f) }

    var animator: ValueAnimator? = null

    val progress: Float
        get() = textInterpolator.progress

    val linearProgress: Float
        get() = textInterpolator.linearProgress

    fun updateLayout(layout: Layout, textSize: Float = -1f) {
        textInterpolator.layout = layout

        if (textSize >= 0) {
            textInterpolator.targetPaint.textSize = textSize
            textInterpolator.basePaint.textSize = textSize
            textInterpolator.onTargetPaintModified()
            textInterpolator.onBasePaintModified()
        }
    }

    val isRunning: Boolean
        get() = animator?.isRunning ?: false

    fun draw(c: Canvas) = textInterpolator.draw(c)

    /** Style spec to use when rendering the font */
    data class Style(
        val fVar: String? = null,
        val textSize: Float? = null,
        val color: Int? = null,
        val strokeWidth: Float? = null,
    )

    /** Animation Spec for use when style changes should be animated */
    data class Animation(
        val animate: Boolean = true,
        val startDelay: Long = 0,
        val duration: Long = DEFAULT_ANIMATION_DURATION,
        val interpolator: TimeInterpolator = Interpolators.LINEAR,
        val onAnimationEnd: Runnable? = null,
    ) {
        fun configureAnimator(animator: Animator) {
            animator.startDelay = startDelay
            animator.duration = duration
            animator.interpolator = interpolator
            if (onAnimationEnd != null) {
                animator.addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            onAnimationEnd.run()
                        }
                    }
                )
            }
        }

        companion object {
            val DISABLED = Animation(animate = false)
        }
    }

    /** Sets the text style, optionally with animation */
    fun setTextStyle(style: Style, animation: Animation = Animation.DISABLED) {
        animator?.cancel()
        setTextStyleInternal(style, rebase = animation.animate)

        if (animation.animate) {
            animator = buildAnimator(animation).apply { start() }
        } else {
            textInterpolator.progress = 1f
            textInterpolator.linearProgress = 1f
            textInterpolator.rebase()
            listener?.onInvalidate()
        }
    }

    /** Builds a ValueAnimator from the specified animation parameters */
    private fun buildAnimator(animation: Animation): ValueAnimator {
        return createAnimator().apply {
            duration = DEFAULT_ANIMATION_DURATION
            animation.configureAnimator(this)

            addUpdateListener {
                textInterpolator.progress = it.animatedValue as Float
                textInterpolator.linearProgress = it.currentPlayTime / it.duration.toFloat()
                listener?.onInvalidate()
            }

            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animator: Animator) = textInterpolator.rebase()

                    override fun onAnimationCancel(animator: Animator) = textInterpolator.rebase()
                }
            )
        }
    }

    private fun setTextStyleInternal(
        style: Style,
        rebase: Boolean,
        updateLayoutOnFailure: Boolean = true,
    ) {
        try {
            if (rebase) textInterpolator.rebase()
            style.color?.let { textInterpolator.targetPaint.color = it }
            style.textSize?.let { textInterpolator.targetPaint.textSize = it }
            style.strokeWidth?.let { textInterpolator.targetPaint.strokeWidth = it }
            style.fVar?.let {
                textInterpolator.targetPaint.typeface = typefaceCache.getTypefaceForVariant(it)
            }
            textInterpolator.onTargetPaintModified()
        } catch (ex: IllegalArgumentException) {
            if (updateLayoutOnFailure) {
                Log.e(
                    TAG,
                    "setTextStyleInternal: Exception caught but retrying. This is usually" +
                        " due to the layout having changed unexpectedly without being notified.",
                    ex,
                )

                updateLayout(textInterpolator.layout)
                setTextStyleInternal(style, rebase, updateLayoutOnFailure = false)
            } else {
                throw ex
            }
        }
    }

    companion object {
        private val TAG = TextAnimator::class.simpleName!!
        const val DEFAULT_ANIMATION_DURATION = 300L
    }
}
