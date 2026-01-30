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

package com.android.systemui.notifications.ui.composable.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.theme.PlatformTheme
import com.android.systemui.SysuiTestCase
import kotlin.test.Test
import org.junit.Rule
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class PrioritizedRowTest : SysuiTestCase() {
    @get:Rule val rule = createComposeRule()

    // Using a fixed density to ensure the width of Text composables is consistent across devices
    val density = Density(density = 2.0f, fontScale = 1.0f)

    private val iconWidth = 24.dp
    private val separatorWidth = 15.dp
    private val importantWidth = 75.dp
    private val shrinkableWidth = 95.dp
    private val hideableWidth = 102.dp
    private val fixedWidth = 25.dp

    private val reducedWidth = 50.dp
    private val hideWidth = 20.dp

    @Test
    fun widthFull_allChildrenAreVisible() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides density) {
                PlatformTheme {
                    PrioritizedRow(modifier = Modifier.testTag("row")) { TestContent() }
                }
            }
        }

        // All the content is displayed
        rule.onNodeWithTag("icon0").assertIsDisplayedWithWidth(iconWidth)
        rule.onNodeWithTag("spacer0").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithTag("icon1").assertIsDisplayedWithWidth(iconWidth)
        rule.onNodeWithTag("spacer1").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithText("High Importance").assertIsDisplayedWithWidth(importantWidth)
        rule.onNodeWithTag("dot0").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithText("Medium (Shrinkable)").assertIsDisplayedWithWidth(shrinkableWidth)
        rule.onNodeWithTag("dot1").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithTag("Low (Hideable)").assertIsDisplayedWithWidth(hideableWidth)
        rule.onNodeWithTag("spacer2").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithText("FIXED").assertIsDisplayedWithWidth(fixedWidth)

        rule.onNodeWithTag("row").assertWidthIsEqualTo(420.dp)
    }

    @Test
    fun width320dp_sameImportance_allShrinkablesShrink() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides density) {
                PlatformTheme {
                    PrioritizedRow(modifier = Modifier.width(320.dp).testTag("row")) {
                        // All children have the same importance (everything else stays the same)
                        TestContent(forceSameImportance = true)
                    }
                }
            }
        }

        rule.onNodeWithTag("icon0").assertIsDisplayedWithWidth(iconWidth)
        rule.onNodeWithTag("spacer0").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithTag("icon1").assertIsDisplayedWithWidth(iconWidth)
        rule.onNodeWithTag("spacer1").assertIsDisplayedWithWidth(separatorWidth)
        // The required space is distributed across all 3 shrinkables, but they can't go below
        // reducedWidth.
        rule.onNodeWithText("High Importance").assertIsDisplayedWithWidth(reducedWidth)
        rule.onNodeWithTag("dot0").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithText("Medium (Shrinkable)").assertIsDisplayedWithWidth(57.5.dp)
        rule.onNodeWithTag("dot1").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithTag("Low (Hideable)").assertIsDisplayedWithWidth(64.5.dp)
        rule.onNodeWithTag("spacer2").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithText("FIXED").assertIsDisplayedWithWidth(fixedWidth)

        rule.onNodeWithTag("row").assertWidthIsEqualTo(320.dp)
    }

    @Test
    fun width400dp_lowPriorityRowShrinks() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides density) {
                PlatformTheme {
                    PrioritizedRow(modifier = Modifier.width(400.dp).testTag("row")) {
                        TestContent()
                    }
                }
            }
        }

        // Icons are not affected yet, as we haven't reached the hiding stage
        rule.onNodeWithTag("icon0").assertIsDisplayedWithWidth(iconWidth)
        rule.onNodeWithTag("spacer0").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithTag("icon1").assertIsDisplayedWithWidth(iconWidth)
        rule.onNodeWithTag("spacer1").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithText("High Importance").assertIsDisplayedWithWidth(importantWidth)
        rule.onNodeWithTag("dot0").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithText("Medium (Shrinkable)").assertIsDisplayedWithWidth(shrinkableWidth)
        rule.onNodeWithTag("dot1").assertIsDisplayedWithWidth(separatorWidth)
        // Hideable row shrinks as much as necessary
        rule.onNodeWithTag("Low (Hideable)").assertIsDisplayedWithWidth(82.dp)
        rule.onNodeWithTag("spacer2").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithText("FIXED").assertIsDisplayedWithWidth(fixedWidth)

        rule.onNodeWithTag("row").assertWidthIsEqualTo(400.dp)
    }

    @Test
    fun width360dp_mediumPriorityTextShrinks() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides density) {
                PlatformTheme {
                    PrioritizedRow(modifier = Modifier.width(360.dp).testTag("row")) {
                        TestContent()
                    }
                }
            }
        }

        rule.onNodeWithTag("icon0").assertIsDisplayedWithWidth(iconWidth)
        rule.onNodeWithTag("spacer0").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithTag("icon1").assertIsDisplayedWithWidth(iconWidth)
        rule.onNodeWithTag("spacer1").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithText("High Importance").assertIsDisplayedWithWidth(importantWidth)
        rule.onNodeWithTag("dot0").assertIsDisplayedWithWidth(separatorWidth)
        // Medium priority text shrinks as much as necessary
        rule.onNodeWithText("Medium (Shrinkable)").assertIsDisplayedWithWidth(87.dp)
        rule.onNodeWithTag("dot1").assertIsDisplayedWithWidth(separatorWidth)
        // Low priority row doesn't shrink beyond reducedWidth
        rule.onNodeWithTag("Low (Hideable)").assertIsDisplayedWithWidth(reducedWidth)
        rule.onNodeWithTag("spacer2").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithText("FIXED").assertIsDisplayedWithWidth(fixedWidth)

        rule.onNodeWithTag("row").assertWidthIsEqualTo(360.dp)
    }

    @Test
    fun width310dp_highPriorityTextShrinks() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides density) {
                PlatformTheme {
                    PrioritizedRow(modifier = Modifier.width(310.dp).testTag("row")) {
                        TestContent()
                    }
                }
            }
        }

        rule.onNodeWithTag("icon0").assertIsDisplayedWithWidth(iconWidth)
        rule.onNodeWithTag("spacer0").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithTag("icon1").assertIsDisplayedWithWidth(iconWidth)
        rule.onNodeWithTag("spacer1").assertIsDisplayedWithWidth(separatorWidth)
        // High priority text shrinks as much as necessary
        rule.onNodeWithText("High Importance").assertIsDisplayedWithWidth(62.dp)
        rule.onNodeWithTag("dot0").assertIsDisplayedWithWidth(separatorWidth)
        // Medium priority text doesn't shrink beyond reducedWidth
        rule.onNodeWithText("Medium (Shrinkable)").assertIsDisplayedWithWidth(reducedWidth)
        rule.onNodeWithTag("dot1").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithTag("Low (Hideable)").assertIsDisplayedWithWidth(reducedWidth)
        rule.onNodeWithTag("spacer2").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithText("FIXED").assertIsDisplayedWithWidth(fixedWidth)

        rule.onNodeWithTag("row").assertWidthIsEqualTo(310.dp)
    }

    @Test
    fun width280dp_startIconsShrink() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides density) {
                PlatformTheme {
                    PrioritizedRow(modifier = Modifier.width(280.dp).testTag("row")) {
                        TestContent()
                    }
                }
            }
        }

        // Start icons have the same importance, so they both start shrinking
        rule.onNodeWithTag("icon0").assertIsDisplayedWithWidth(15.dp)
        rule.onNodeWithTag("spacer0").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithTag("icon1").assertIsDisplayedWithWidth(15.dp)
        rule.onNodeWithTag("spacer1").assertIsDisplayedWithWidth(separatorWidth)
        // High priority text doesn't shrink beyond reducedWidth
        rule.onNodeWithText("High Importance").assertIsDisplayedWithWidth(reducedWidth)
        rule.onNodeWithTag("dot0").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithText("Medium (Shrinkable)").assertIsDisplayedWithWidth(reducedWidth)
        rule.onNodeWithTag("dot1").assertIsDisplayedWithWidth(separatorWidth)
        // Hideable row doesn't start shrinking yet, its priority is higher than the icons
        rule.onNodeWithTag("Low (Hideable)").assertIsDisplayedWithWidth(reducedWidth)
        rule.onNodeWithTag("spacer2").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithText("FIXED").assertIsDisplayedWithWidth(fixedWidth)

        rule.onNodeWithTag("row").assertWidthIsEqualTo(280.dp)
    }

    @Test
    fun width230dp_startIconsHide() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides density) {
                PlatformTheme {
                    PrioritizedRow(modifier = Modifier.width(230.dp).testTag("row")) {
                        TestContent()
                    }
                }
            }
        }

        // Both of the start icons and their spacers disappear
        rule.onNodeWithTag("icon0").assertIsNotDisplayed()
        rule.onNodeWithTag("spacer0").assertIsNotDisplayed()
        rule.onNodeWithTag("icon1").assertIsNotDisplayed()
        rule.onNodeWithTag("spacer1").assertIsNotDisplayed()
        // High importance text gets some space back
        rule.onNodeWithText("High Importance").assertIsDisplayedWithWidth(60.dp)
        rule.onNodeWithTag("dot0").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithText("Medium (Shrinkable)").assertIsDisplayedWithWidth(reducedWidth)
        rule.onNodeWithTag("dot1").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithTag("Low (Hideable)").assertIsDisplayedWithWidth(reducedWidth)
        rule.onNodeWithTag("spacer2").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithText("FIXED").assertIsDisplayedWithWidth(fixedWidth)

        rule.onNodeWithTag("row").assertWidthIsEqualTo(230.dp)
    }

    @Test
    fun width200dp_lowPriorityRowShrinks() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides density) {
                PlatformTheme {
                    PrioritizedRow(modifier = Modifier.width(200.dp).testTag("row")) {
                        TestContent()
                    }
                }
            }
        }

        rule.onNodeWithTag("icon0").assertIsNotDisplayed()
        rule.onNodeWithTag("spacer0").assertIsNotDisplayed()
        rule.onNodeWithTag("icon1").assertIsNotDisplayed()
        rule.onNodeWithTag("spacer1").assertIsNotDisplayed()
        rule.onNodeWithText("High Importance").assertIsDisplayedWithWidth(reducedWidth)
        rule.onNodeWithTag("dot0").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithText("Medium (Shrinkable)").assertIsDisplayedWithWidth(reducedWidth)
        rule.onNodeWithTag("dot1").assertIsDisplayedWithWidth(separatorWidth)
        // Low priority row shrinks further before hiding
        rule.onNodeWithTag("Low (Hideable)").assertIsDisplayedWithWidth(30.dp)
        rule.onNodeWithTag("spacer2").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithText("FIXED").assertIsDisplayedWithWidth(fixedWidth)

        rule.onNodeWithTag("row").assertWidthIsEqualTo(200.dp)
    }

    @Test
    fun width140dp_lowPriorityRowHides() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides density) {
                PlatformTheme {
                    PrioritizedRow(modifier = Modifier.width(140.dp).testTag("row")) {
                        TestContent()
                    }
                }
            }
        }

        // All the hideables and corresponding separators are hidden, all the shrinkables are shrunk
        // to the minimum width.
        rule.onNodeWithTag("icon0").assertIsNotDisplayed()
        rule.onNodeWithTag("spacer0").assertIsNotDisplayed()
        rule.onNodeWithTag("icon1").assertIsNotDisplayed()
        rule.onNodeWithTag("spacer1").assertIsNotDisplayed()
        rule.onNodeWithText("High Importance").assertIsDisplayedWithWidth(reducedWidth)
        rule.onNodeWithTag("dot0").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithText("Medium (Shrinkable)").assertIsDisplayedWithWidth(reducedWidth)
        rule.onNodeWithTag("dot1").assertIsNotDisplayed()
        rule.onNodeWithTag("Low (Hideable)").assertIsNotDisplayed()
        rule.onNodeWithTag("spacer2").assertIsDisplayedWithWidth(separatorWidth)
        rule.onNodeWithText("FIXED").assertIsDisplayedWithWidth(fixedWidth)

        rule.onNodeWithTag("row").assertWidthIsEqualTo(140.dp)
    }

    // Note: This composable is forked in the Compose Gallery app for interactive, manual testing.
    @Composable
    private fun PrioritizedRowScope.TestContent(forceSameImportance: Boolean = false) {
        // Note: This font family & size  (together with the fixed density configuration) means that
        // each character in a text composable will have a width of 5dp.
        val fontFamily = FontFamily.Monospace
        val fontSize = 8.sp

        // These icons are the first to hide, but since there's no reducedWidth set, they aren't
        // affected by the first shrinking stage
        Icon(
            Icons.Default.Star,
            contentDescription = null,
            Modifier.hideable(importance = 0).testTag("icon0"),
        )
        // Note: spacers need a height so that assertIsDisplayed considers them displayed (if the
        // height is 0, even if they do take up space, they're not considered visible)
        Spacer(Modifier.width(separatorWidth).height(1.dp).separator().testTag("spacer0"))
        Icon(
            Icons.Default.Star,
            contentDescription = null,
            Modifier.hideable(importance = 0).testTag("icon1"),
        )
        Spacer(Modifier.width(separatorWidth).height(1.dp).separator().testTag("spacer1"))

        // This text will be the last to shrink
        Text(
            text = "High Importance",
            modifier =
                Modifier.shrinkable(
                    importance = if (forceSameImportance) 0 else 3,
                    minWidth = reducedWidth,
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = fontFamily,
            fontSize = fontSize,
        )
        Text(
            " • ",
            Modifier.separator().testTag("dot0"),
            fontFamily = fontFamily,
            fontSize = fontSize,
        )

        // This text will shrink to its minWidth
        Text(
            text = "Medium (Shrinkable)",
            modifier =
                Modifier.shrinkable(
                    importance = if (forceSameImportance) 0 else 2,
                    minWidth = reducedWidth,
                ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = fontFamily,
            fontSize = fontSize,
        )
        Text(
            " • ",
            Modifier.separator().testTag("dot1"),
            fontFamily = fontFamily,
            fontSize = fontSize,
        )

        // This row can be hidden, but will be shrunk in two stages beforehand
        Row(
            modifier =
                Modifier.hideable(
                        importance = if (forceSameImportance) 0 else 1,
                        reducedWidth = reducedWidth,
                        hideWidth = hideWidth,
                    )
                    .testTag("Low (Hideable)"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Star, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                "Low (Hideable)",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = fontFamily,
                fontSize = fontSize,
            )
        }
        Spacer(Modifier.width(separatorWidth).height(1.dp).separator().testTag("spacer2"))

        // This text never gets shrunk or hidden
        Text(
            "FIXED",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = fontFamily,
            fontSize = fontSize,
        )
    }

    private fun SemanticsNodeInteraction.assertIsDisplayedWithWidth(width: Dp) {
        assertIsDisplayed()
        assertWidthIsEqualTo(width)
    }
}
