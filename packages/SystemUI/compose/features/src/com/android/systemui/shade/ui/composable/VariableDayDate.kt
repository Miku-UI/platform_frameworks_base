package com.android.systemui.shade.ui.composable

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VariableDayDate(
    longerDateText: String,
    shorterDateText: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMediumEmphasized,
) {
    Layout(
        content = {
            Text(
                text = longerDateText,
                style = textStyle,
                color = textColor,
                softWrap = false,
                maxLines = 1,
            )
            Text(
                text = shorterDateText,
                style = textStyle,
                color = textColor,
                softWrap = false,
                maxLines = 1,
            )
        },
        modifier = modifier,
    ) { measurables, constraints ->
        check(measurables.size == 2)

        // 1. Measure completely unconstrained to determine the natural desired size of the text.
        val unconstrained = Constraints()

        // We MUST measure both measurables even if the first one fits.
        // Conditional measurement causes issues with LookaheadScope: if logic diverges between the
        // lookahead pass and the approach pass (e.g. during animations), both measurables need to
        // have been measured to avoid crashes or layout bugs (see b/463395847).
        val longer = measurables[0].measure(unconstrained)
        val shorter = measurables[1].measure(unconstrained)

        // 2. Decide which placeable to use.
        val selectedPlaceable =
            when {
                longer.width <= constraints.maxWidth -> longer
                shorter.width <= constraints.maxWidth -> shorter
                else -> null
            }

        if (selectedPlaceable == null) {
            // Neither fits, render nothing.
            layout(0, 0) {}
        } else {
            // Coerce the layout size to strictly respect the parent's constraints.
            val layoutWidth =
                selectedPlaceable.width.coerceIn(constraints.minWidth, constraints.maxWidth)
            val layoutHeight =
                selectedPlaceable.height.coerceIn(constraints.minHeight, constraints.maxHeight)

            // Calculate vertical offset to center the text and avoid asymmetric bottom-clipping.
            val yOffset = (layoutHeight - selectedPlaceable.height) / 2

            layout(
                width = layoutWidth,
                height = layoutHeight,
                alignmentLines =
                    mapOf(
                        FirstBaseline to selectedPlaceable[FirstBaseline] + yOffset,
                        LastBaseline to selectedPlaceable[LastBaseline] + yOffset,
                    ),
            ) {
                selectedPlaceable.placeRelative(0, yOffset)
            }
        }
    }
}
