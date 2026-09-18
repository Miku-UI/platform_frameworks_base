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

package com.android.systemui.screencapture.ui

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Region
import android.view.ViewTreeObserver.InternalInsetsInfo
import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.AndroidEmbeddedExternalSurface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeGestures
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.android.compose.modifiers.thenIf
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ScreenCaptureScope
import com.android.systemui.screencapture.record.camera.ui.viewmodel.ScreenCaptureCameraTransformationViewModel
import com.android.systemui.screencapture.record.camera.ui.viewmodel.ScreenCaptureCameraViewModel
import com.android.systemui.screencapture.ui.viewmodel.ScreenCaptureOverlayUiDialogViewModel
import com.android.systemui.statusbar.phone.EdgeToEdgeDialogDelegate
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import com.android.systemui.util.view.listenToComputeInternalInsets
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Sample duration for the [android.view.ViewTreeObserver.OnComputeInternalInsetsListener] region
 * changes. Updating it every time causes an underlying binder to overflow the cache and cause a
 * crash.
 */
private val outsideRegionUpdateSampleDuration = 200.milliseconds

@ScreenCaptureScope
class ScreenCaptureOverlayUi
@Inject
constructor(
    @Application context: Context,
    dialogFactory: SystemUIDialogFactory,
    dialogViewModel: ScreenCaptureOverlayUiDialogViewModel,
    private val cameraViewModelFactory: ScreenCaptureCameraViewModel.Factory,
    private val cameraTransformationViewModel: ScreenCaptureCameraTransformationViewModel.Factory,
) {

    private val dialog =
        dialogFactory
            .create(
                context = context,
                theme = R.style.Theme_SystemUI_Dialog_ScreenCapture,
                dialogDelegate =
                    EdgeToEdgeDialogDelegate(
                        touchEvent = { _, event -> dialogViewModel.onTouchEvent(event) }
                    ),
                dismissOnDeviceLock = false,
                isTransient = true,
            ) {
                LaunchedEffect(dialogViewModel) { dialogViewModel.activate() }
                DialogContent(it.window!!)
            }
            .apply {
                setupWindow(window!!)
                setCancelable(false)
                setCanceledOnTouchOutside(false)
            }

    private fun setupWindow(window: Window) {
        window.attributes =
            window.attributes.apply {
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                title = "ScreenCaptureOverlayUi"
                format = PixelFormat.TRANSLUCENT
            }
        with(window) {
            addFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
            addPrivateFlags(WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY)
            setWindowAnimations(-1)
        }
    }

    @Composable
    private fun DialogContent(window: Window) {
        val drawingTouchableRegion = remember { Region() }
        val cameraTouchableRegion = remember { Region() }
        LaunchedEffect(window.decorView.viewTreeObserver) {
            window.decorView.viewTreeObserver.listenToComputeInternalInsets {
                setTouchableInsets(InternalInsetsInfo.TOUCHABLE_INSETS_REGION)
                touchableRegion.op(drawingTouchableRegion, Region.Op.UNION)
                touchableRegion.op(cameraTouchableRegion, Region.Op.UNION)
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            Camera(outTouchableRegion = cameraTouchableRegion, modifier = Modifier)
        }
    }

    @Composable
    private fun Camera(outTouchableRegion: Region, modifier: Modifier = Modifier) {
        val display = LocalContext.current.display
        val viewModel =
            rememberViewModel("ScreenCaptureCameraViewModel") { cameraViewModelFactory.create() }
        LightLaunchedEffect(display, viewModel) { viewModel.onDisplayReady(display) }
        val surfaceSize =
            viewModel.surfaceSize?.let { IntSize(width = it.width, height = it.height) }
        val shouldShowCamera = viewModel.shouldShowCamera
        ConditionalLaunchedEffect(shouldShowCamera != true || surfaceSize == null) {
            outTouchableRegion.setEmpty()
        }
        if (shouldShowCamera != null && surfaceSize != null) {
            val transformationViewModel =
                rememberViewModel("ScreenCaptureCameraTransformationViewModel") {
                    cameraTransformationViewModel.create()
                }
            var windowBounds: Rect by remember { mutableStateOf(Rect.Zero) }
            transformationViewModel.onUiBoundsChangedWithInsets(windowBounds)
            transformationViewModel.fillTouchableRegionInto(outTouchableRegion)

            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier =
                    modifier
                        .fillMaxSize()
                        .onGloballyPositioned { layoutCoordinates ->
                            windowBounds = layoutCoordinates.boundsInWindow()
                        }
                        .selfieTransformableModifier(
                            viewModel = transformationViewModel,
                            isEverywhere = true,
                        )
                        .showDebugIfNeeded(transformationViewModel),
            ) {
                val surfaceAlpha by
                    animateFloatAsState(
                        targetValue = if (shouldShowCamera) 1f else 0f,
                        animationSpec = spring(stiffness = Spring.StiffnessHigh),
                    )
                val sizeModifier =
                    Modifier.fillMaxSmallDimension(windowBounds)
                        .aspectRatio(
                            viewModel.outputStreamSize?.run { width.toFloat() / height } ?: 1f
                        )
                AndroidEmbeddedExternalSurface(
                    surfaceSize = surfaceSize,
                    isOpaque = false,
                    transform = transformationViewModel.surfaceTransformation,
                    modifier = sizeModifier.graphicsLayer { alpha = surfaceAlpha },
                ) {
                    onSurface { surface, width, height ->
                        viewModel.onSurfaceReady(surface = surface, width = width, height = height)
                        surface.onChanged { newWidth, newHeight ->
                            viewModel.onSurfaceReady(
                                surface = surface,
                                width = newWidth,
                                height = newHeight,
                            )
                        }
                        surface.onDestroyed { viewModel.onSurfaceDestroyed() }
                    }
                }
                Spacer(
                    modifier =
                        sizeModifier
                            .onGloballyPositioned { layoutCoordinates ->
                                val boundsInWindow = layoutCoordinates.boundsInWindow(false)
                                transformationViewModel.onSurfaceScreenBoundsUpdated(boundsInWindow)
                            }
                            .graphicsLayer {
                                translationX = transformationViewModel.offsetX
                                translationY = transformationViewModel.offsetY
                                rotationZ = transformationViewModel.rotation
                                scaleX = transformationViewModel.scale
                                scaleY = transformationViewModel.scale
                            }
                            .selfieTransformableModifier(
                                viewModel = transformationViewModel,
                                isEverywhere = false,
                            )
                            .thenIf(viewModel.areTapsSupported) {
                                Modifier.clickable(
                                    interactionSource = null,
                                    indication = null,
                                    onClick = { viewModel.onSurfaceClicked() },
                                )
                            }
                            .thenIf(transformationViewModel.shouldShowTouchBounds) {
                                Modifier.background(Color.Green.copy(alpha = 0.5f))
                            }
                )
            }
        }
    }

    private fun Modifier.fillMaxSmallDimension(bounds: Rect): Modifier {
        return widthIn(max = 414.dp)
            .then(if (bounds.width > bounds.height) fillMaxHeight() else fillMaxWidth())
    }

    private fun Modifier.selfieTransformableModifier(
        viewModel: ScreenCaptureCameraTransformationViewModel,
        isEverywhere: Boolean,
    ): Modifier {
        return if (isEverywhere == viewModel.transformableByTouchAnywhere) {
            transformable(viewModel.transformableState)
        } else {
            this
        }
    }

    /**
     * Shows the UI and suspends until it's dismissed. Cancelling the suspension dismisses the UI
     */
    suspend fun show(): Unit = suspendCancellableCoroutine { invocation ->
        dialog.setOnDismissListener {
            if (invocation.isActive) {
                invocation.resume(Unit)
            }
        }
        dialog.show()
        invocation.invokeOnCancellation { hide() }
    }

    private fun hide() {
        dialog.dismissWithoutAnimation()
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun ScreenCaptureCameraTransformationViewModel.fillTouchableRegionInto(outRegion: Region) {
    LaunchedEffect(this, outRegion) {
        snapshotFlow { touchableRegion }
            .sample(outsideRegionUpdateSampleDuration)
            .onEach { outRegion.set(it) }
            .launchIn(this)
    }
}

@Composable
private fun ScreenCaptureCameraTransformationViewModel.onUiBoundsChangedWithInsets(
    windowBounds: Rect
) {
    val insets: WindowInsets = WindowInsets.safeGestures
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    LaunchedEffect(this, density, layoutDirection, windowBounds, insets) {
        onUiBoundsChanged(
            uiBounds = windowBounds,
            safeGestureBounds =
                Rect(
                    left = windowBounds.left + insets.getLeft(density, layoutDirection),
                    top = windowBounds.top + insets.getTop(density),
                    right = windowBounds.right - insets.getRight(density, layoutDirection),
                    bottom = windowBounds.bottom - insets.getBottom(density),
                ),
        )
    }
}

private fun Modifier.showDebugIfNeeded(vm: ScreenCaptureCameraTransformationViewModel): Modifier {
    return if (vm.shouldShowTouchBounds) {
        drawWithContent {
            drawContent()
            val path = vm.touchableRegion.boundaryPath.asComposePath()
            drawPath(path = path, color = Color.Red, alpha = 0.2f)
        }
    } else {
        this
    }
}

/** Runs [action] if the [condition] is met. */
@Composable
private fun ConditionalLaunchedEffect(condition: Boolean, action: suspend () -> Unit) {
    LaunchedEffect(condition) {
        if (condition) {
            action()
        }
    }
}

@Composable
private fun LightLaunchedEffect(key1: Any?, key2: Any?, action: () -> Unit) {
    DisposableEffect(key1, key2) {
        action()
        onDispose {}
    }
}
