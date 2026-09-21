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

package com.android.systemui.statusbar.chips.ui.compose

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.systemui.res.R
import com.android.systemui.statusbar.chips.ui.model.ColorsModel
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.chips.ui.viewmodel.formatTimeRemainingData
import com.android.systemui.statusbar.chips.ui.viewmodel.rememberChronometerState
import com.android.systemui.statusbar.chips.ui.viewmodel.rememberTimeRemainingState
import com.android.systemui.statusbar.chips.ui.viewmodel.toFormatter
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChipContent(
    viewModel: OngoingActivityChipModel.Content,
    icon: OngoingActivityChipModel.ChipIcon?,
    colors: ColorsModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val locale: Locale? = LocalConfiguration.current.locales[0]
    val textStyle = MaterialTheme.typography.labelLargeEmphasized
    val textColor = Color(colors.text(context))
    val maxTextWidth = dimensionResource(id = R.dimen.ongoing_activity_chip_max_text_width)
    val startPadding =
        if (icon != null && !icon.hasEmbeddedPadding) {
            // Add padding only if this text is next to an icon that doesn't embed its own padding
            dimensionResource(id = R.dimen.ongoing_activity_chip_icon_text_padding)
        } else {
            0.dp
        }
    // Include endPadding in the Text instead of the outer OngoingActivityChip so that if the text
    // is hidden because it's too large, then the remaining icon is still centered
    val endPadding =
        if (icon?.hasEmbeddedPadding == true) {
            dimensionResource(
                id = R.dimen.ongoing_activity_chip_text_end_padding_for_embedded_padding_icon
            )
        } else {
            0.dp
        }
    val textMeasurer = rememberTextMeasurer()
    when (viewModel) {
        is OngoingActivityChipModel.Content.Timer -> {
            val timerState =
                rememberChronometerState(
                    chronometer = viewModel.value,
                    formatter = viewModel.format.toFormatter(),
                    timeSource = viewModel.timeSource,
                )
            timerState.currentTimeText?.let { text ->
                Text(
                    text = text,
                    style = textStyle,
                    color = textColor,
                    softWrap = false,
                    modifier =
                        modifier
                            .hideTextIfDoesNotFit(
                                text = text,
                                textStyle = textStyle,
                                textMeasurer = textMeasurer,
                                maxTextWidth = maxTextWidth,
                                startPadding = startPadding,
                                endPadding = endPadding,
                            )
                            .neverDecreaseWidth(density, locale, text.length),
                )
            }
        }

        is OngoingActivityChipModel.Content.Countdown -> {
            val text = NumberFormat.getIntegerInstance().format(viewModel.secondsUntilStarted)
            Text(
                text = text,
                style = textStyle,
                color = textColor,
                softWrap = false,
                textAlign = TextAlign.Center,
                modifier = modifier.neverDecreaseWidth(density, locale, text.length),
            )
        }

        is OngoingActivityChipModel.Content.Text -> {
            val text = viewModel.text
            if (text.isNotBlank()) {
                Text(
                    text = text,
                    color = textColor,
                    style = textStyle,
                    softWrap = false,
                    modifier =
                        modifier.hideTextIfDoesNotFit(
                            text = text,
                            textStyle = textStyle,
                            textMeasurer = textMeasurer,
                            maxTextWidth = maxTextWidth,
                            startPadding = startPadding,
                            endPadding = endPadding,
                        ),
                )
            }
        }

        is OngoingActivityChipModel.Content.TextVariants -> {
            if (android.app.Flags.metricValueAlternativeStrings()) {
                val textVariants = viewModel.textVariants

                BoxWithConstraints(modifier = modifier) {
                    val horizontalPadding = startPadding + endPadding
                    val maxWidth =
                        minOf(
                                with(density) { maxTextWidth.roundToPx() },
                                (constraints.maxWidth -
                                    with(density) { horizontalPadding.roundToPx() }),
                            )
                            .coerceAtLeast(constraints.minWidth)

                    // Look for a text variant that fits, respecting order of preference. If none
                    // fit, we give up and display no text.
                    val fittingText =
                        textVariants.firstOrNull { variant ->
                            val result =
                                textMeasurer.measure(
                                    text = variant,
                                    style = textStyle,
                                    softWrap = false,
                                )
                            result.size.width <= maxWidth
                        }

                    if (fittingText != null) {
                        Text(
                            text = fittingText,
                            color = textColor,
                            style = textStyle,
                            softWrap = false,
                            modifier = Modifier.padding(start = startPadding, end = endPadding),
                        )
                    }
                }
            } else {
                val text = viewModel.textVariants.first()
                Text(
                    text = text,
                    color = textColor,
                    style = textStyle,
                    softWrap = false,
                    modifier =
                        modifier.hideTextIfDoesNotFit(
                            text = text,
                            textStyle = textStyle,
                            textMeasurer = textMeasurer,
                            maxTextWidth = maxTextWidth,
                            startPadding = startPadding,
                            endPadding = endPadding,
                        ),
                )
            }
        }

        is OngoingActivityChipModel.Content.ShortTimeDelta -> {
            val timeRemainingState =
                rememberTimeRemainingState(
                    futureTimeMillis = viewModel.time,
                    timeSource = viewModel.timeSource,
                )

            timeRemainingState.timeRemainingData?.let {
                val text = formatTimeRemainingData(it)
                Text(
                    text = text,
                    style = textStyle,
                    color = textColor,
                    softWrap = false,
                    modifier =
                        modifier.hideTextIfDoesNotFit(
                            text = text,
                            textStyle = textStyle,
                            textMeasurer = textMeasurer,
                            maxTextWidth = maxTextWidth,
                            startPadding = startPadding,
                            endPadding = endPadding,
                        ),
                )
            }
        }

        is OngoingActivityChipModel.Content.IconOnly -> {
            throw IllegalStateException("ChipContent should only be used if the chip shows text")
        }
    }
}
