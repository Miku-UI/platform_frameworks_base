/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.bouncer.ui.composable

import androidx.test.filters.SmallTest
import androidx.window.core.layout.WindowSizeClass
import com.android.compose.layout.ContainerLayout
import com.android.systemui.SysuiTestCase
import com.android.systemui.bouncer.ui.composable.BouncerOverlayLayout.BELOW_USER_SWITCHER
import com.android.systemui.bouncer.ui.composable.BouncerOverlayLayout.BESIDE_USER_SWITCHER
import com.android.systemui.bouncer.ui.composable.BouncerOverlayLayout.SPLIT_BOUNCER
import com.android.systemui.bouncer.ui.composable.BouncerOverlayLayout.STANDARD_BOUNCER
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.Parameter
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class BouncerOverlayLayoutTest : SysuiTestCase() {

    data object Phone :
        Device(
            name = "phone",
            width = Dimensions.COMPACT_WIDTH_COMPACT_HEIGHT,
            height = Dimensions.EXPANDED_WIDTH_EXPANDED_HEIGHT,
            naturallyHeld = Vertically,
        )

    data object Tablet :
        Device(
            name = "tablet",
            width = Dimensions.EXPANDED_WIDTH_EXPANDED_HEIGHT,
            height = Dimensions.MEDIUM_WIDTH_MEDIUM_HEIGHT,
            naturallyHeld = Horizontally,
        )

    data object Folded :
        Device(
            name = "folded",
            width = Dimensions.COMPACT_WIDTH_COMPACT_HEIGHT,
            height = Dimensions.MEDIUM_WIDTH_MEDIUM_HEIGHT,
            naturallyHeld = Vertically,
        )

    data object Unfolded :
        Device(
            name = "unfolded",
            width = Dimensions.EXPANDED_WIDTH_MEDIUM_HEIGHT,
            height = Dimensions.MEDIUM_WIDTH_MEDIUM_HEIGHT,
            naturallyHeld = Vertically,
        )

    data object TallerFolded :
        Device(
            name = "taller folded",
            width = Dimensions.COMPACT_WIDTH_COMPACT_HEIGHT,
            height = Dimensions.EXPANDED_WIDTH_EXPANDED_HEIGHT,
            naturallyHeld = Vertically,
        )

    data object TallerUnfolded :
        Device(
            name = "taller unfolded",
            width = Dimensions.EXPANDED_WIDTH_EXPANDED_HEIGHT,
            height = Dimensions.EXPANDED_WIDTH_EXPANDED_HEIGHT,
            naturallyHeld = Vertically,
        )

    data object LaptopScreen :
        Device(
            name = "laptop screen",
            width = Dimensions.LARGE_WIDTH_EXPANDED_HEIGHT,
            height = Dimensions.MEDIUM_WIDTH_MEDIUM_HEIGHT,
            naturallyHeld = Horizontally,
        )

    data object ExternalScreen :
        Device(
            name = "external screen",
            width = Dimensions.EXTRA_LARGE_WIDTH_EXTRA_LARGE_HEIGHT,
            height = Dimensions.EXPANDED_WIDTH_EXPANDED_HEIGHT,
            naturallyHeld = Horizontally,
        )

    data object ExtraHighExternalScreen :
        Device(
            name = "extra high external screen",
            width = Dimensions.EXTRA_LARGE_WIDTH_EXTRA_LARGE_HEIGHT,
            height = Dimensions.EXTRA_LARGE_WIDTH_EXTRA_LARGE_HEIGHT,
            naturallyHeld = Horizontally,
        )

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun testCases() =
            listOf(
                    Phone to
                        Expected(
                            whenNaturallyHeld = STANDARD_BOUNCER,
                            whenUnnaturallyHeld = SPLIT_BOUNCER,
                        ),
                    Tablet to
                        Expected(
                            whenNaturallyHeld = BESIDE_USER_SWITCHER,
                            whenUnnaturallyHeld = BELOW_USER_SWITCHER,
                        ),
                    Folded to
                        Expected(
                            whenNaturallyHeld = STANDARD_BOUNCER,
                            whenUnnaturallyHeld = SPLIT_BOUNCER,
                        ),
                    Unfolded to
                        Expected(
                            whenNaturallyHeld = BESIDE_USER_SWITCHER,
                            whenUnnaturallyHeld = STANDARD_BOUNCER,
                        ),
                    TallerFolded to
                        Expected(
                            whenNaturallyHeld = STANDARD_BOUNCER,
                            whenUnnaturallyHeld = SPLIT_BOUNCER,
                        ),
                    TallerUnfolded to
                        Expected(
                            whenNaturallyHeld = BESIDE_USER_SWITCHER,
                            whenUnnaturallyHeld = BESIDE_USER_SWITCHER,
                        ),
                    LaptopScreen to
                        Expected(
                            whenNaturallyHeld = BESIDE_USER_SWITCHER,
                            whenUnnaturallyHeld = BELOW_USER_SWITCHER,
                        ),
                    ExternalScreen to
                        Expected(
                            whenNaturallyHeld = BESIDE_USER_SWITCHER,
                            containerLayoutWhenNaturallyHeld = ContainerLayout.HORIZONTAL,
                            whenUnnaturallyHeld = BELOW_USER_SWITCHER,
                            containerLayoutWhenUnnaturallyHeld = ContainerLayout.VERTICAL,
                        ),
                    ExtraHighExternalScreen to
                        Expected(
                            whenNaturallyHeld = BESIDE_USER_SWITCHER,
                            containerLayoutWhenNaturallyHeld = ContainerLayout.HORIZONTAL,
                            whenUnnaturallyHeld = BESIDE_USER_SWITCHER,
                            containerLayoutWhenUnnaturallyHeld = ContainerLayout.HORIZONTAL,
                        ),
                )
                .flatMap { (device, expected) ->
                    buildList {
                        // Holding the device in its natural orientation (vertical or horizontal):
                        add(
                            TestCase(
                                device = device,
                                held = device.naturallyHeld,
                                expectedLayout = expected.layout(heldNaturally = true),
                                expectedContainerLayout =
                                    expected.containerLayout(heldNaturally = true),
                            )
                        )

                        if (expected.whenNaturallyHeld == BESIDE_USER_SWITCHER) {
                            add(
                                TestCase(
                                    device = device,
                                    held = device.naturallyHeld,
                                    isOneHandedModeSupported = false,
                                    expectedLayout = STANDARD_BOUNCER,
                                    expectedContainerLayout =
                                        expected.containerLayout(heldNaturally = true),
                                )
                            )
                        }

                        // Holding the device the other way:
                        add(
                            TestCase(
                                device = device,
                                held = device.naturallyHeld.flip(),
                                expectedLayout = expected.layout(heldNaturally = false),
                                expectedContainerLayout =
                                    expected.containerLayout(heldNaturally = false),
                            )
                        )

                        if (expected.whenUnnaturallyHeld == BESIDE_USER_SWITCHER) {
                            add(
                                TestCase(
                                    device = device,
                                    held = device.naturallyHeld.flip(),
                                    isOneHandedModeSupported = false,
                                    expectedLayout = STANDARD_BOUNCER,
                                    expectedContainerLayout =
                                        expected.containerLayout(heldNaturally = false),
                                )
                            )
                        }
                    }
                }
    }

    @Parameter @JvmField var testCase: TestCase? = null

    @Test
    fun calculateLayout() {
        testCase?.let { nonNullTestCase ->
            with(nonNullTestCase) {
                val windowSizeClass = device.sizeClass(whenHeld = held)
                assertThat(
                        calculateLayoutInternal(
                            windowSizeClass = windowSizeClass,
                            isOneHandedModeSupported = isOneHandedModeSupported,
                        )
                    )
                    .isEqualTo(expectedLayout)
                assertThat(calculateContainerLayoutInternal(windowSizeClass))
                    .isEqualTo(expectedContainerLayout)
            }
        }
    }

    data class TestCase(
        val device: Device,
        val held: Held,
        val expectedLayout: BouncerOverlayLayout,
        val expectedContainerLayout: ContainerLayout?,
        val isOneHandedModeSupported: Boolean = true,
    ) {
        override fun toString(): String {
            return buildString {
                append(device.name)
                append(" width: ${device.width(held)}.dp")
                append(" height: ${device.height(held)}.dp")
                append(" when held $held")
                if (!isOneHandedModeSupported) {
                    append(" (one-handed-mode not supported)")
                }
            }
        }
    }

    data class Expected(
        val whenNaturallyHeld: BouncerOverlayLayout,
        val whenUnnaturallyHeld: BouncerOverlayLayout,
        val containerLayoutWhenNaturallyHeld: ContainerLayout? = null,
        val containerLayoutWhenUnnaturallyHeld: ContainerLayout? = null,
    ) {
        fun layout(heldNaturally: Boolean): BouncerOverlayLayout {
            return if (heldNaturally) {
                whenNaturallyHeld
            } else {
                whenUnnaturallyHeld
            }
        }

        fun containerLayout(heldNaturally: Boolean): ContainerLayout? {
            return if (heldNaturally) {
                containerLayoutWhenNaturallyHeld
            } else {
                containerLayoutWhenUnnaturallyHeld
            }
        }
    }

    sealed class Device(
        val name: String,
        private val width: Int,
        private val height: Int,
        val naturallyHeld: Held,
    ) {
        fun sizeClass(whenHeld: Held): WindowSizeClass {
            return if (isHeldNaturally(whenHeld)) {
                WindowSizeClass(width, height)
            } else {
                WindowSizeClass(height, width)
            }
        }

        fun width(whenHeld: Held): Int = if (isHeldNaturally(whenHeld)) width else height

        fun height(whenHeld: Held): Int = if (isHeldNaturally(whenHeld)) height else width

        private fun isHeldNaturally(whenHeld: Held): Boolean {
            return whenHeld == naturallyHeld
        }
    }

    sealed class Held {
        abstract fun flip(): Held
    }

    data object Vertically : Held() {
        override fun flip(): Held {
            return Horizontally
        }
    }

    data object Horizontally : Held() {
        override fun flip(): Held {
            return Vertically
        }
    }

    object Dimensions {
        const val COMPACT_WIDTH_COMPACT_HEIGHT = WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND - 1
        const val MEDIUM_WIDTH_MEDIUM_HEIGHT = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
        const val EXPANDED_WIDTH_MEDIUM_HEIGHT = WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
        const val EXPANDED_WIDTH_EXPANDED_HEIGHT = WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND
        const val LARGE_WIDTH_EXPANDED_HEIGHT = WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND
        const val EXTRA_LARGE_WIDTH_EXTRA_LARGE_HEIGHT =
            WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND
    }
}
