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

package com.android.settingslib.widget

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.children
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.settingslib.widget.preference.statusbanner.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator

class StatusBannerPreference
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
) : Preference(context, attrs, defStyleAttr, defStyleRes), GroupSectionDividerMixin {

    enum class BannerStatus {
        GENERIC,
        LOW,
        MEDIUM,
        HIGH,
        OFF,
        LOADING_DETERMINATE, // The loading progress is set by the caller.
        LOADING_INDETERMINATE // No loading progress. Just loading animation
    }

    private var isDefaultIcon: Boolean = true
    private var currentState: IconState =
        IconState(getIconDrawable(BannerStatus.GENERIC), BannerStatus.GENERIC)
    private var previousState: IconState? = null

    private var shouldAnimate: Boolean = false
    private var isFirstBind = true
    private var isAnimationPending = false

    /**
     * Gets or sets the status level for the banner icon.
     * When setting, if a default icon is being used, the icon will automatically update
     * to match the new level. If a custom icon is set, the icon remains the same.
     */
    var iconLevel: BannerStatus
        get() = currentState.level
        set(value) {
            // If we are using default icons, the icon is determined by the level.
            // Otherwise, we keep the existing custom icon but update its level.
            val icon = if (isDefaultIcon) getIconDrawable(value) else currentState.icon
            updateState(icon, value)
        }

    /**
     * Sets a custom icon drawable for the banner.
     * This will override the default icon. To revert to the default, pass null.
     * The current icon level is preserved.
     *
     * @param icon The custom drawable to display, or null to restore the default icon.
     */
    override fun setIcon(icon: Drawable?) {
        isDefaultIcon = (icon == null)
        // If we are reverting to default, get the correct icon for the current level.
        val newIcon = if (isDefaultIcon) getIconDrawable(currentState.level) else icon
        updateState(newIcon, currentState.level)
    }

    override fun setIcon(iconResId: Int) {
        val icon = if (iconResId != 0) ContextCompat.getDrawable(context, iconResId) else null
        setIcon(icon)
    }

    /**
     * A public API to set both a custom icon and a status level in a single operation.
     * This is the most direct way to configure the banner's visual state.
     *
     * @param icon The custom drawable to display, or null to use the default for the given level.
     * @param level The new banner status level.
     */
    fun setIconAndStatusLevel(icon: Drawable?, level: BannerStatus) {
        isDefaultIcon = (icon == null)
        val newIcon = if (isDefaultIcon) getIconDrawable(level) else icon
        updateState(newIcon, level)
    }

    /**
     * The single, centralized private method for updating the component's state.
     * All public setters delegate to this function to ensure logic is consistent.
     * It handles state transitions, tinting, and triggers the animation flow.
     */
    private fun updateState(icon: Drawable?, level: BannerStatus) {
        // Create a mutable copy of the drawable to prevent sharing its state across instances.
        val newIcon = icon?.constantState?.newDrawable(context.resources)?.mutate()

        // If a custom icon is being used, its tint color must always match the status level.
        if (!isDefaultIcon) {
            updateIconTint(newIcon, level)
        }

        val newState = IconState(newIcon, level)

        if (newState == currentState) {
            return
        }

        // Only snapshot the "from" state if an animation is NOT already pending.
        // This preserves the true origin state during rapid updates.
        if (!isAnimationPending) {
            previousState = currentState
        }
        currentState = newState

        if (!isFirstBind) {
            shouldAnimate = true
            // Set the flag to lock the previousState until the animation runs.
            isAnimationPending = true
        }
        notifyChanged()
    }

    var applyIconTint: Boolean = true
    var showIconBackground: Boolean = true
    var playIconAnimationInLoop: Boolean = false

    private var previousButtonLevel: BannerStatus? = null
    var buttonLevel: BannerStatus = BannerStatus.GENERIC
        set(value) {
            previousButtonLevel = field
            field = value
            notifyChanged()
        }

    var isButtonEnabled: Boolean = true
        set(value) {
            field = value
            notifyChanged()
        }
    private var buttonText: String = ""
        set(value) {
            field = value
            notifyChanged()
        }

    private var listener: View.OnClickListener? = null

    private var circularProgressIndicator: CircularProgressIndicator? = null
    private var backgroundView: ImageView? = null

    init {
        layoutResource = R.layout.settingslib_expressive_preference_statusbanner

        initAttributes(context, attrs, defStyleAttr)
    }

    private fun initAttributes(context: Context, attrs: AttributeSet?, defStyleAttr: Int) {
        context.obtainStyledAttributes(attrs, R.styleable.StatusBanner, defStyleAttr, 0).apply {
            val initialLevel = getInteger(R.styleable.StatusBanner_iconLevel, 0).toBannerStatus()

            setIconAndStatusLevel(icon, initialLevel)

            buttonLevel = getInteger(R.styleable.StatusBanner_buttonLevel, 0).toBannerStatus()
            buttonText = getString(R.styleable.StatusBanner_buttonText) ?: ""
            recycle()
        }
    }

    private fun Int.toBannerStatus(): BannerStatus =
        when (this) {
            1 -> BannerStatus.LOW
            2 -> BannerStatus.MEDIUM
            3 -> BannerStatus.HIGH
            4 -> BannerStatus.OFF
            5 -> BannerStatus.LOADING_DETERMINATE
            6 -> BannerStatus.LOADING_INDETERMINATE
            else -> BannerStatus.GENERIC
        }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.isDividerAllowedBelow = false
        holder.isDividerAllowedAbove = false
        if (onPreferenceClickListener == null) {
            holder.itemView.isClickable = false
        }

        // Use the view's tag to retrieve or create our enhanced ViewHolder.
        val views = (holder.itemView.tag as? ViewHolder) ?: ViewHolder(holder.itemView).also {
            holder.itemView.tag = it
        }
        views.cleanup()

        // Store references to views that are needed by other methods in the class.
        backgroundView = views.backgroundView
        circularProgressIndicator = views.circularProgressIndicator

        setupIconFrameVisibility(views.iconFrame)
        updateIconState(views)
        setupButton(views.button, buttonLevel)

        isFirstBind = false
        shouldAnimate = false
        previousState = null
        isAnimationPending = false
        previousButtonLevel = null
    }

    /**
     * Determines and sets the visibility of the main icon container.
     */
    private fun setupIconFrameVisibility(iconFrame: View?) {
        iconFrame?.visibility = if (
            currentState.icon != null ||
            currentState.level == BannerStatus.LOADING_DETERMINATE ||
            currentState.level == BannerStatus.LOADING_INDETERMINATE
        ) View.VISIBLE else View.GONE
    }

    /**
     * Decides whether to animate the icon transition or set the view state directly.
     */
    private fun updateIconState(views: ViewHolder) {
        val animationScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1.0f
        )
        val animationsEnabled = animationScale > 0f

        if (animationsEnabled && shouldAnimate && !isFirstBind) {
            // We have a pending state change and animations are enabled, so run the transition.
            handleIconTransition(views)
        } else {
            // No animation needed; set the final state of the views directly.
            setupDirectView(currentState, views)
        }
    }

    /**
     * Handles the complex logic for animating between different icon states.
     */
    private fun handleIconTransition(views: ViewHolder) {
        val fromState = previousState ?: currentState
        val toState = currentState

        val fromView = getViewForLevel(
            fromState.level,
            views.iconView,
            views.circularProgressIndicator,
            views.loadingIndicator
        )
        val toView = getViewForLevel(
            toState.level,
            views.iconView,
            views.circularProgressIndicator,
            views.loadingIndicator
        )

        val fromColor = getBackgroundColor(fromState.level)
        val toColor = getBackgroundColor(toState.level)

        val fromBackground = getBackgroundDrawable(fromState.level)
        val toBackground = getBackgroundDrawable(toState.level)

        if (fromBackground != null && toBackground != null) {
            // Create a TransitionDrawable to cross-fade between the two backgrounds.
            val transitionDrawable = TransitionDrawable(arrayOf(fromBackground, toBackground))
            backgroundView?.setImageDrawable(transitionDrawable)

            transitionDrawable.startTransition(BACKGROUND_TRANSITION_SPEED)
        } else {
            // Fallback for cases where one of the drawables is null.
            backgroundView?.setImageDrawable(toBackground)
        }

        // Hide all potential target views initially to prepare for the animation.
        views.iconView.visibility = View.GONE
        views.circularProgressIndicator?.visibility = View.GONE
        views.loadingIndicator?.visibility = View.GONE

        if (fromView == toView && fromView == views.iconView) {
            // Transitioning from one icon to another...
            // We create a temporary ImageView for the outgoing icon to animate it out.
            val parent = views.iconView.parent as? ViewGroup
            if (parent != null && fromState.icon != null) {
                val tempImageView = ImageView(context).apply {
                    layoutParams = views.iconView.layoutParams
                    scaleType = views.iconView.scaleType
                    setImageDrawable(fromState.icon)
                    id = View.NO_ID
                }
                parent.addView(tempImageView, parent.indexOfChild(views.iconView))

                setIconDrawable(views, toState.icon)
                views.maybeStartIconAnimationOnBackgroundView(playIconAnimationInLoop)
                animateTransition(tempImageView, views.iconView, fromColor, toColor, views)
            } else {
                if (fromState.level == BannerStatus.GENERIC && isDefaultIcon) {
                    views.iconView.setImageDrawable(toState.icon)
                    fromView.visibility = View.VISIBLE

                    toView?.let {
                        it.prepareForRevealAnimation()
                        startRevealAnimation(it, views)
                    }
                } else {
                    // Fallback if we can't create the temporary view.
                    setupDirectView(toState, views)
                }
            }
        } else if (fromView != toView) {
            // Transitioning between different types of views (e.g., icon to progress indicator).
            if (toView == views.iconView) {
                setIconDrawable(views, toState.icon)
                views.maybeStartIconAnimationOnBackgroundView(playIconAnimationInLoop)
            }
            if (fromView == views.iconView) views.iconView.setImageDrawable(fromState.icon)
            fromView?.visibility = View.VISIBLE
            animateTransition(fromView, toView, fromColor, toColor, views)
        } else {
            // The views are the same but are not the main icon view (e.g., progress indicator state hasn't changed).
            setupDirectView(toState, views)
        }
    }

    /**
     * Configures the button's appearance, text, and click listener.
     */
    private fun setupButton(button: MaterialButton?, level: BannerStatus) {
        button?.apply {
            val fromLevel = previousButtonLevel ?: level
            val fromColor =
                getBackgroundColor(if (fromLevel == BannerStatus.OFF) BannerStatus.GENERIC else fromLevel)
            val toColor =
                getBackgroundColor(if (level == BannerStatus.OFF) BannerStatus.GENERIC else level)

            // Set up text color with disabled state
            val currentTextColors = textColors
            val enabledTextColor = currentTextColors.getColorForState(
                intArrayOf(android.R.attr.state_enabled), currentTextColors.defaultColor
            )
            val disabledTextColor = ColorUtils.setAlphaComponent(enabledTextColor, DISABLED_BUTTON_TEXT_ALPHA)
            setTextColor(ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_enabled),
                    intArrayOf(-android.R.attr.state_enabled),
                ),
                intArrayOf(enabledTextColor, disabledTextColor)
            ))

            val backgroundStates = arrayOf(
                intArrayOf(android.R.attr.state_enabled),
                intArrayOf(-android.R.attr.state_enabled),
            )

            if (shouldAnimate && fromColor != toColor) {
                val colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor)
                colorAnimator.duration = BACKGROUND_TRANSITION_SPEED.toLong()
                colorAnimator.addUpdateListener { animator ->
                    val animatedColor = animator.animatedValue as Int
                    val disabledAnimatedColor =
                        ColorUtils.setAlphaComponent(animatedColor, DISABLED_BUTTON_ALPHA)
                    backgroundTintList = ColorStateList(
                        backgroundStates,
                        intArrayOf(animatedColor, disabledAnimatedColor)
                    )
                }
                colorAnimator.start()
            } else {
                val disabledToColor = ColorUtils.setAlphaComponent(toColor, DISABLED_BUTTON_ALPHA)
                backgroundTintList = ColorStateList(
                    backgroundStates, intArrayOf(toColor, disabledToColor)
                )
            }
            text = buttonText
            setOnClickListener(listener)
            visibility = if (listener != null) View.VISIBLE else View.GONE
            isEnabled = isButtonEnabled
        }
    }

    /**
     * Display without any transition
     */
    private fun setupDirectView(state: IconState, views: ViewHolder) {
        val targetView = getViewForLevel(
            state.level, views.iconView,
            views.circularProgressIndicator,
            views.loadingIndicator
        )

        views.iconView.visibility = if (targetView == views.iconView) View.VISIBLE else View.GONE
        views.circularProgressIndicator?.visibility =
            if (targetView == views.circularProgressIndicator) View.VISIBLE else View.GONE
        views.loadingIndicator?.visibility =
            if (targetView == views.loadingIndicator) View.VISIBLE else View.GONE
        backgroundView?.setImageDrawable(getBackgroundDrawable(state.level))

        if (targetView == views.iconView) {
            setIconDrawable(views, state.icon)

            // Reset scale/alpha for non-animation cases.
            views.iconView.scaleX = 1f
            views.iconView.scaleY = 1f
            views.iconView.alpha = 1f
            views.maybeStartIconAnimationOnBackgroundView(playIconAnimationInLoop)
        }
    }

    private fun setIconDrawable(views: ViewHolder, icon: Drawable?) {
        if (showIconBackground) {
            views.iconView.setImageDrawable(icon)
        } else {
            // When `showIconBackground` is false, the provided icon is assumed to contain
            // its own background. It is placed in the `backgroundView` to ensure
            // correct sizing, and the foreground `iconView` is cleared.
            backgroundView?.setImageDrawable(icon)
            views.iconView.setImageDrawable(null)
        }
    }

    /**
     * Helper to map level to the correct View instance
     */
    private fun getViewForLevel(
        level: BannerStatus,
        iconView: ImageView,
        circularProgressIndicator: CircularProgressIndicator?,
        loadingIndicator: View?,
    ): View? {
        return when (level) {
            BannerStatus.LOADING_DETERMINATE -> circularProgressIndicator
            BannerStatus.LOADING_INDETERMINATE -> loadingIndicator
            else -> {
                iconView
            }
        }
    }

    fun getProgressIndicator(): CircularProgressIndicator? {
        return circularProgressIndicator
    }

    /** Sets the text to be displayed in button. */
    fun setButtonText(@StringRes textResId: Int) {
        buttonText = context.getString(textResId)
    }

    /**
     * Register a callback to be invoked when positive button is clicked. If null is passed as the
     * callback, the button will be hidden.
     */
    fun setButtonOnClickListener(listener: View.OnClickListener?) {
        this.listener = listener
        notifyChanged()
    }

    private fun getBackgroundColor(level: BannerStatus): Int {
        return when (level) {
            BannerStatus.LOW ->
                ContextCompat.getColor(
                    context,
                    R.color.settingslib_expressive_color_status_level_low
                )

            BannerStatus.MEDIUM ->
                ContextCompat.getColor(
                    context,
                    R.color.settingslib_expressive_color_status_level_medium
                )

            BannerStatus.HIGH ->
                ContextCompat.getColor(
                    context,
                    R.color.settingslib_expressive_color_status_level_high
                )

            BannerStatus.OFF ->
                ContextCompat.getColor(
                    context,
                    R.color.settingslib_expressive_color_status_level_off
                )

            else ->
                ContextCompat.getColor(
                    context,
                    com.android.settingslib.widget.theme.R.color.settingslib_materialColorPrimary,
                )
        }
    }

    private fun getIconDrawable(level: BannerStatus): Drawable? {
        return when (level) {
            BannerStatus.LOW ->
                ContextCompat.getDrawable(
                    context,
                    R.drawable.settingslib_expressive_icon_status_level_low
                )

            BannerStatus.MEDIUM ->
                ContextCompat.getDrawable(
                    context,
                    R.drawable.settingslib_expressive_icon_status_level_medium,
                )

            BannerStatus.HIGH ->
                ContextCompat.getDrawable(
                    context,
                    R.drawable.settingslib_expressive_icon_status_level_high
                )

            BannerStatus.OFF ->
                ContextCompat.getDrawable(
                    context,
                    R.drawable.settingslib_expressive_icon_status_level_off
                )

            else -> null
        }
    }

    private fun getBackgroundDrawable(level: BannerStatus): Drawable? {
        if (!showIconBackground) {
            return null
        }
        return when (level) {
            BannerStatus.LOW ->
                ContextCompat.getDrawable(
                    context,
                    R.drawable.settingslib_expressive_background_level_low
                )

            BannerStatus.MEDIUM ->
                ContextCompat.getDrawable(
                    context,
                    R.drawable.settingslib_expressive_background_level_medium,
                )

            BannerStatus.HIGH ->
                ContextCompat.getDrawable(
                    context,
                    R.drawable.settingslib_expressive_background_level_high
                )

            // Using the same background drawable for other levels.
            else ->
                ContextCompat.getDrawable(
                    context,
                    R.drawable.settingslib_expressive_background_generic
                )
        }
    }

    /**
     * Function to implement the transition between fromView and toView, built with the corresponding
     * IconState object
     */
    private fun animateTransition(
        fromView: View?,
        toView: View?,
        fromColor: Int,
        toColor: Int,
        views: ViewHolder,
        stiffness: Float = DEFAULT_STIFFNESS,
    ) {
        if (fromView == toView) {
            toView?.visibility = View.VISIBLE
            return
        }

        toView?.prepareForRevealAnimation()

        if (fromView == null) {
            // No fromView, just reveal toView immediately
            toView?.let { startRevealAnimation(it, views, stiffness) }
            return
        }

        // Start the "dismiss" animation for the outgoing view
        fromView.let { view ->
            view.visibility = View.VISIBLE // Make sure the view is visible before animating
            // Color transition for ImageView
            if (view is ImageView) {
                ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor).apply {
                    duration = COLOR_TRANSITION_DURATION_MS
                    interpolator = android.view.animation.LinearInterpolator() // Linear easing
                    addUpdateListener { animator -> view.setColorFilter(animator.animatedValue as Int) }
                    start()
                }
            }

            // Spring animation for scale
            val scaleXAnim =
                SpringAnimation(view, DynamicAnimation.SCALE_X, 0f).apply {
                    spring.dampingRatio = DEFAULT_DAMPING_RATIO
                    spring.stiffness = stiffness
                }
            val scaleYAnim =
                SpringAnimation(view, DynamicAnimation.SCALE_Y, 0f).apply {
                    spring.dampingRatio = DEFAULT_DAMPING_RATIO
                    spring.stiffness = stiffness
                }

            views.trackAnimation(scaleXAnim)
            views.trackAnimation(scaleYAnim)

            // "Snappy" effect: Hide the view when it reaches 40% scale
            val updateListener =
                object : DynamicAnimation.OnAnimationUpdateListener {
                    var revealTriggered = false

                    override fun onAnimationUpdate(
                        animation: DynamicAnimation<out DynamicAnimation<*>>,
                        value: Float,
                        velocity: Float,
                    ) {
                        if (!revealTriggered && value <= DISMISS_HIDE_SCALE_THRESHOLD) {
                            view.visibility = View.INVISIBLE
                            animation.removeUpdateListener(this)

                            toView?.let { startRevealAnimation(it, views, stiffness) }
                            revealTriggered = true
                        }
                    }
                }
            scaleYAnim.addUpdateListener(updateListener)

            // Hide the view and clean up when the animation is complete
            scaleYAnim.addEndListener { _, _, _, _ ->
                view.visibility = View.GONE
                view.scaleX = 1f
                view.scaleY = 1f
                if (view is ImageView) {
                    view.clearColorFilter()
                }

                // If it's a temporary view, remove it from the parent.
                if (view.id == View.NO_ID && view.parent is ViewGroup) {
                    (view.parent as ViewGroup).removeView(view)
                }
            }

            scaleXAnim.start()
            scaleYAnim.start()
        }
    }

    /**
     * Separate function to encapsulate reveal logic
     */
    private fun startRevealAnimation(
        view: View,
        views: ViewHolder,
        stiffness: Float = DEFAULT_STIFFNESS
    ) {
        view.visibility = View.VISIBLE

        val scaleXAnim = SpringAnimation(view, DynamicAnimation.SCALE_X, 1f).apply {
            spring.dampingRatio = DEFAULT_DAMPING_RATIO
            spring.stiffness = stiffness
        }
        val scaleYAnim = SpringAnimation(view, DynamicAnimation.SCALE_Y, 1f).apply {
            spring.dampingRatio = DEFAULT_DAMPING_RATIO
            spring.stiffness = stiffness
        }

        views.trackAnimation(scaleXAnim)
        views.trackAnimation(scaleYAnim)

        scaleXAnim.start()
        scaleYAnim.start()
    }

    private fun View.prepareForRevealAnimation() {
        alpha = 1f
        scaleX = REVEAL_INITIAL_SCALE
        scaleY = REVEAL_INITIAL_SCALE
        visibility = View.INVISIBLE
    }

    private companion object AnimationConstants {
        const val REVEAL_INITIAL_SCALE = 0.4f
        const val DISMISS_HIDE_SCALE_THRESHOLD = 0.4f
        const val COLOR_TRANSITION_DURATION_MS = 48L
        const val DEFAULT_DAMPING_RATIO = 0.7f
        const val DEFAULT_STIFFNESS = 800f
        const val BACKGROUND_TRANSITION_SPEED = 150
        const val DISABLED_BUTTON_ALPHA = 97
        const val DISABLED_BUTTON_TEXT_ALPHA = 230
    }

    /**
     * A helper class to hold references to the views within the preference layout.
     * This avoids repeated calls to findViewById and cleans up the onBindViewHolder method.
     */
    private class ViewHolder(itemView: View) {
        val iconFrame: View? = itemView.findViewById(android.R.id.icon_frame)
        val backgroundView: ImageView? = itemView.findViewById(R.id.icon_background)
        val iconView: ImageView = itemView.findViewById(android.R.id.icon)
        val circularProgressIndicator: CircularProgressIndicator? =
            itemView.findViewById(R.id.progress_indicator)
        val loadingIndicator: View? = itemView.findViewById(R.id.loading_indicator)
        val button: MaterialButton? = itemView.findViewById(R.id.status_banner_button)

        private val runningAnimations = mutableListOf<DynamicAnimation<*>>()

        /**
         * Tracks a new animation. This should be called immediately after an animation is created.
         */
        fun trackAnimation(animation: DynamicAnimation<*>) {
            runningAnimations.add(animation)
        }

        /**
         * The definitive cleanup method. This must be called at the start of onBindViewHolder.
         * It performs three critical actions:
         * 1. Explicitly cancels all tracked animations to stop them from modifying views.
         * 2. Removes any temporary, orphaned ImageViews from previous animations.
         * 3. Resets the visual properties of all animatable views to a known, clean state.
         * 4. Stops any running AnimatedVectorDrawables and clears callbacks on the iconView and backgroundView.
         */
        fun cleanup() {
            // 1.
            runningAnimations.forEach { it.cancel() }
            runningAnimations.clear()

            // 2.
            (iconView.parent as? ViewGroup)?.let { parent ->
                for (child in parent.children.toList()) {
                    if (child is ImageView && child.id == View.NO_ID) {
                        parent.removeView(child)
                    }
                }
            }

            // 3.
            val viewsToReset = listOfNotNull(iconView, circularProgressIndicator, loadingIndicator)
            for (view in viewsToReset) {
                view.scaleX = 1f
                view.scaleY = 1f
                view.alpha = 1f
                view.visibility = View.VISIBLE
            }

            // 4.
            val iconViewDrawable = iconView.drawable
            if (iconViewDrawable is AnimatedVectorDrawable) {
                iconViewDrawable.stop()
                iconViewDrawable.clearAnimationCallbacks()
            }
            val backgroundViewDrawable = backgroundView?.drawable
            if (backgroundViewDrawable is AnimatedVectorDrawable) {
                backgroundViewDrawable.stop()
                backgroundViewDrawable.clearAnimationCallbacks()
            }
        }

        /**
         * Starts the drawable on the backgroundView if it is an AnimatedVectorDrawable and sets it to loop.
         */
        fun maybeStartIconAnimationOnBackgroundView(playIconAnimationInLoop: Boolean) {
            val drawable = backgroundView?.drawable
            if (drawable is AnimatedVectorDrawable) {
                val avd = drawable
                avd.clearAnimationCallbacks()
                if (playIconAnimationInLoop) {
                    avd.registerAnimationCallback(object : Animatable2.AnimationCallback() {
                        override fun onAnimationEnd(drawable: Drawable) {
                            if (backgroundView.drawable === avd) {
                                avd.start()
                            }
                        }
                    })
                }
                avd.start()
            }
        }
    }

    private fun updateIconTint(newIcon: Drawable?, iconLevel: BannerStatus) {
        if (!applyIconTint) {
            return
        }
        newIcon?.setTintList(ColorStateList.valueOf(getBackgroundColor(iconLevel)))
    }

    /**
     * Helper data class that stores icon and iconLevel
     */
    private data class IconState(val icon: Drawable?, val level: BannerStatus)
}
