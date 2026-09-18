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

package com.android.systemui.keyguard.ui.composable.elements

import android.annotation.DrawableRes
import android.content.Context
import android.content.res.Resources
import android.view.ViewConfiguration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.animation.Expandable
import com.android.compose.animation.rememberExpandableController
import com.android.compose.animation.scene.ElementContentScope
import com.android.compose.animation.scene.ElementKey
import com.android.compose.theme.LocalAndroidColorScheme
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.keyguard.logging.KeyguardQuickAffordancesLogger
import com.android.systemui.Flags.enableLockscreenBlur
import com.android.systemui.animation.Expandable
import com.android.systemui.common.shared.model.ContentDescription.Companion.loadContentDescription
import com.android.systemui.common.shared.model.Icon as SysUiIcon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.ui.binder.KeyguardBottomAreaVibrations
import com.android.systemui.keyguard.ui.viewmodel.KeyguardQuickAffordanceHapticViewModel
import com.android.systemui.keyguard.ui.viewmodel.KeyguardQuickAffordanceViewModel
import com.android.systemui.keyguard.ui.viewmodel.KeyguardQuickAffordancesCombinedViewModel
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.keyguard.ui.composable.elements.BaseLockscreenElement.ElementSource
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElement
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementKeys.Shortcuts
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenElementProvider
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenScope
import com.android.systemui.res.R
import com.android.systemui.shade.ShadeDisplayAware
import com.android.systemui.statusbar.KeyguardIndicationController
import com.android.systemui.statusbar.VibratorHelper
import com.android.systemui.window.domain.interactor.WindowRootViewBlurInteractor
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

@SysUISingleton
class ShortcutElementProvider
@Inject
constructor(
    @ShadeDisplayAware private val context: Context,
    private val viewModel: KeyguardQuickAffordancesCombinedViewModel,
    private val indicationController: KeyguardIndicationController,
    private val falsingManager: FalsingManager,
    private val vibratorHelper: VibratorHelper,
    private val hapticsViewModelFactory: KeyguardQuickAffordanceHapticViewModel.Factory,
    private val windowRootViewBlurInteractor: WindowRootViewBlurInteractor,
    private val logger: KeyguardQuickAffordancesLogger,
) : LockscreenElementProvider {

    override val elements: List<LockscreenElement> by lazy {
        listOf(
            ShortcutElement(Shortcuts.Start, isStart = true),
            ShortcutElement(Shortcuts.End, isStart = false),
        )
    }

    private inner class ShortcutElement(
        override val key: ElementKey,
        private val isStart: Boolean,
    ) : LockscreenElement {
        override val context = this@ShortcutElementProvider.context
        override val source = ElementSource.STANDARD

        @Composable
        override fun LockscreenScope<ElementContentScope>.LockscreenElement() {
            KeyguardShortcut(
                viewModel = if (isStart) viewModel.startButton else viewModel.endButton,
                alphaFlow = viewModel.transitionAlpha,
                messageDisplayer = { indicationController.showTransientIndication(it) },
            )
        }
    }

    // TODO b/450929769 convert flows here to hydratedState
    @Composable
    private fun KeyguardShortcut(
        viewModel: Flow<KeyguardQuickAffordanceViewModel>,
        alphaFlow: Flow<Float>,
        messageDisplayer: (Int) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        val hapticsViewModel = remember { hapticsViewModelFactory.create() }
        val quickAffordanceViewModel by
            viewModel.collectAsStateWithLifecycle(
                initialValue = KeyguardQuickAffordanceViewModel(slotId = "")
            )

        // If this shortcut is NOT visible, render a placeholder to retain
        // symmetry for things relying on shortcuts for layout. This includes indication area.
        if (!quickAffordanceViewModel.isVisible) {
            Box(modifier)
            return
        }

        onViewModelUpdated(quickAffordanceViewModel, hapticsViewModel)

        // Responsible for the x positioning during the "shake" animation
        val xAnimation = remember { Animatable(Offset(0f, 0f), Offset.VectorConverter) }

        // Used to trigger the shake animation after a failed trigger
        val (triggerFailed, setTriggerFailed) = remember { mutableStateOf(false) }

        val (pressed, setPressed) = remember { mutableStateOf(false) }

        // Tint of the foreground drawable
        val foregroundTint =
            if (quickAffordanceViewModel.isActivated) {
                MaterialTheme.colorScheme.onPrimaryFixed
            } else {
                MaterialTheme.colorScheme.onSurface
            }

        failureAnimation(triggerFailed, setTriggerFailed, hapticsViewModel, xAnimation)

        val scale by
            animateFloatAsState(
                targetValue =
                    when {
                        pressed -> PRESSED_SCALE
                        quickAffordanceViewModel.isSelected -> SCALE_SELECTED_BUTTON
                        else -> 1f
                    },
                animationSpec =
                    tween(
                        durationMillis = ViewConfiguration.get(context).longPressTimeoutMillis,
                        easing = LinearEasing,
                    ),
                label = "",
            )

        val alpha by alphaFlow.collectAsStateWithLifecycle(initialValue = 1f)

        val painter =
            painterResource(
                icon = quickAffordanceViewModel.icon,
                atEnd = quickAffordanceViewModel.isActivated,
            )

        if (painter == null) return

        Expandable(
            controller =
                rememberExpandableController(color = Color.Transparent, shape = RectangleShape)
        ) { expandable ->
            Icon(
                painter = painter,
                contentDescription =
                    quickAffordanceViewModel.icon.contentDescription?.loadContentDescription(
                        context
                    ),
                modifier =
                    modifier
                        .semantics(mergeDescendants = true) {
                            // This only overrides onClick for Accessibility
                            onClick {
                                triggerQuickAffordance(quickAffordanceViewModel, expandable)
                                true
                            }
                        }
                        .size(
                            height = dimensionResource(R.dimen.keyguard_affordance_fixed_height),
                            width = dimensionResource(R.dimen.keyguard_affordance_fixed_width),
                        )
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            alpha = if (quickAffordanceViewModel.isDimmed) DIM_ALPHA else alpha,
                            translationX = xAnimation.value.x,
                        )
                        .drawBlurBehind(quickAffordanceViewModel, alpha)
                        .shortcutBackground(quickAffordanceViewModel)
                        .clickableShortcut(
                            expandable,
                            quickAffordanceViewModel,
                            setPressed,
                            setTriggerFailed,
                            messageDisplayer,
                            hapticsViewModel,
                        )
                        .wrapContentSize(unbounded = true),
                tint = foregroundTint,
            )
        }
    }

    @Composable
    private fun failureAnimation(
        triggerFailed: Boolean,
        setTriggerFailed: (Boolean) -> Unit,
        hapticsViewModel: KeyguardQuickAffordanceHapticViewModel,
        xAnimation: Animatable<Offset, AnimationVector2D>,
    ) {
        val shakeAmplitude = dimensionResource(R.dimen.keyguard_affordance_shake_amplitude)

        LaunchedEffect(triggerFailed) {
            if (triggerFailed) {
                val cycles = KeyguardBottomAreaVibrations.ShakeAnimationCycles.toInt()
                val totalDuration =
                    KeyguardBottomAreaVibrations.ShakeAnimationDuration.inWholeMilliseconds.toInt()
                // Number of cycles, double it for back & forth, add one for the return to center
                val segments = cycles * 2 + 1
                val durationPerSegmentMillis = totalDuration / segments
                hapticsViewModel.onQuickAffordanceClick()

                repeat(cycles) {
                    xAnimation.animateTo(
                        targetValue = Offset(shakeAmplitude.value * 2, 0f),
                        animationSpec = tween(durationPerSegmentMillis),
                    )
                    xAnimation.animateTo(
                        targetValue = Offset(-shakeAmplitude.value * 2, 0f),
                        animationSpec = tween(durationPerSegmentMillis),
                    )
                }
                xAnimation.animateTo(
                    targetValue = Offset.Zero,
                    animationSpec = tween(durationPerSegmentMillis),
                )
                setTriggerFailed(false)
            }
        }
    }

    @Composable
    private fun Modifier.drawBlurBehind(
        viewModel: KeyguardQuickAffordanceViewModel,
        alpha: Float,
    ): Modifier {
        if (enableLockscreenBlur()) {
            val viewRootImpl = LocalView.current.viewRootImpl
            val drawable = remember {
                viewRootImpl.createBackgroundBlurDrawable().apply { setBlurRadius(0) }
            }
            val cornerRadius = dimensionResource(R.dimen.keyguard_affordance_fixed_height)
            val blurRadius = dimensionResource(R.dimen.keyguard_shortcuts_blur_radius)
            val isBlurSupported by
                windowRootViewBlurInteractor.isBlurCurrentlySupported.collectAsStateWithLifecycle()

            if (isBlurSupported) {
                return drawBehind {
                    drawable.apply {
                        setBlurRadius(blurRadius.roundToPx())
                        setCornerRadius(cornerRadius.toPx())
                        val backgroundAlpha = if (viewModel.isDimmed) DIM_ALPHA else alpha
                        setAlpha((backgroundAlpha * 255).toInt())
                    }
                    drawIntoCanvas { canvas ->
                        drawable.setBounds(0, 0, size.width.toInt(), size.height.toInt())
                        drawable.draw(canvas.nativeCanvas)
                    }
                }
            } else {
                SideEffect {
                    drawable.apply {
                        // Stop BackgroundBlurDrawable from dispatching blur regions
                        // to SF since now blur radius is set to 0.
                        setBlurRadius(0)
                    }
                }
            }
        }
        return this
    }

    @Composable
    private fun Modifier.shortcutBackground(viewModel: KeyguardQuickAffordanceViewModel): Modifier {
        if (!viewModel.isSelected) {
            val isBlurSupported by
                windowRootViewBlurInteractor.isBlurCurrentlySupported.collectAsStateWithLifecycle()
            return this.background(
                color =
                    if (viewModel.isActivated) {
                        MaterialTheme.colorScheme.primaryFixed
                    } else if (enableLockscreenBlur() && isBlurSupported) {
                        LocalAndroidColorScheme.current.surfaceEffect1
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                shape = CircleShape,
            )
        }
        return this
    }

    @OptIn(ExperimentalAnimationGraphicsApi::class)
    @Composable
    private fun painterResource(icon: SysUiIcon, atEnd: Boolean): Painter? {
        return when (icon) {
            is SysUiIcon.Loaded -> {
                rememberDrawablePainter(icon.drawable)
            }
            is SysUiIcon.Resource -> {
                val res = icon.resId
                if (res == 0) {
                    return null
                }
                if (isAnimatedVectorDrawable(res, LocalContext.current.resources)) {
                    rememberAnimatedVectorPainter(
                        AnimatedImageVector.animatedVectorResource(res),
                        !atEnd,
                    )
                } else {
                    painterResource(res)
                }
            }
        }
    }

    private fun triggerQuickAffordance(
        viewModel: KeyguardQuickAffordanceViewModel,
        expandable: Expandable,
    ) {
        if (viewModel.configKey != null) {
            viewModel.onClicked(
                KeyguardQuickAffordanceViewModel.OnClickedParameters(
                    configKey = viewModel.configKey,
                    expandable = expandable,
                    slotId = viewModel.slotId,
                )
            )
        }
    }

    @Composable
    private fun onViewModelUpdated(
        viewModel: KeyguardQuickAffordanceViewModel,
        hapticsViewModel: KeyguardQuickAffordanceHapticViewModel,
    ) {
        SideEffect {
            hapticsViewModel.updateActivatedHistory(viewModel.isActivated)
            logger.logUpdate(viewModel)
        }
    }

    @Composable
    private fun Modifier.clickableShortcut(
        expandable: Expandable,
        quickAffordanceViewModel: KeyguardQuickAffordanceViewModel,
        setPressed: (Boolean) -> Unit,
        setTriggerFailed: (Boolean) -> Unit,
        messageDisplayer: (Int) -> Unit,
        hapticsViewModel: KeyguardQuickAffordanceHapticViewModel,
    ): Modifier {
        var isTouchInput = true

        return if (quickAffordanceViewModel.useLongPress) {
            this.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (!falsingManager.isFalseTap(FalsingManager.LOW_PENALTY)) {
                                val change = event.changes.firstOrNull()
                                isTouchInput = change?.type == PointerType.Touch
                                when (event.type) {
                                    PointerEventType.Press -> {
                                        setPressed(true)
                                    }
                                    PointerEventType.Release -> {
                                        // Trigger quick affordance if the pointer type is
                                        // anything besides touch
                                        if (change?.type != PointerType.Touch) {
                                            hapticsViewModel.onQuickAffordanceLongPress(
                                                quickAffordanceViewModel.isActivated
                                            )
                                            triggerQuickAffordance(
                                                quickAffordanceViewModel,
                                                expandable,
                                            )
                                        }

                                        setPressed(false)
                                    }
                                    PointerEventType.Exit,
                                    PointerEventType.Scroll -> {
                                        setPressed(false)
                                    }
                                }
                            }
                        }
                    }
                }
                .pointerInput(quickAffordanceViewModel.isClickable) {
                    if (!quickAffordanceViewModel.isClickable) return@pointerInput

                    detectTapGestures(
                        onTap = {
                            // Intentionally do not check falsing here. If this action happens
                            // repeatedly, we want falsing to eventually ignore touches.
                            messageDisplayer(R.string.keyguard_affordance_press_too_short)

                            setTriggerFailed(true)

                            logger.logQuickAffordanceTapped(quickAffordanceViewModel.configKey)
                        },
                        onLongPress = {
                            if (
                                !falsingManager.isFalseLongTap(FalsingManager.MODERATE_PENALTY) &&
                                    isTouchInput
                            ) {
                                hapticsViewModel.onQuickAffordanceLongPress(
                                    quickAffordanceViewModel.isActivated
                                )
                                triggerQuickAffordance(quickAffordanceViewModel, expandable)
                                setPressed(false)
                            }
                        },
                    )
                }
        } else {
            this.clickable {
                if (!falsingManager.isFalseTap(FalsingManager.LOW_PENALTY)) {
                    triggerQuickAffordance(quickAffordanceViewModel, expandable)
                }
            }
        }
    }

    companion object {
        private const val DIM_ALPHA = 0.3f
        private const val SCALE_SELECTED_BUTTON = 1.23f
        private const val PRESSED_SCALE = 1.5f

        private fun isAnimatedVectorDrawable(@DrawableRes res: Int, resources: Resources): Boolean {
            /**
             * Helper method to seek to the first tag within the VectorDrawable XML asset.
             *
             * From XmlVectorParser.android.kt
             */
            @Throws(XmlPullParserException::class)
            fun XmlPullParser.seekToStartTag(): XmlPullParser {
                var type = next()
                while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {
                    // Empty loop
                    type = next()
                }
                if (type != XmlPullParser.START_TAG) {
                    throw XmlPullParserException("No start tag found")
                }
                return this
            }

            return try {
                @Suppress("ResourceType") val parser: XmlPullParser = resources.getXml(res)
                parser.seekToStartTag().name == "animated-vector"
            } catch (_: Exception) {
                false
            }
        }
    }
}
