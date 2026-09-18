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

package com.android.systemui.dreams.ui.compose

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.systemui.dreams.ui.viewmodel.DreamSetupEvent
import com.android.systemui.res.R
import kotlinx.coroutines.launch

private const val TAG = "DreamSetupScreen"

private object DreamSetupDimensions {
    val HorizontalPadding = 24.dp
    val TopPadding = 32.dp
    val BottomPadding = 24.dp
    val TitleToDescriptionSpacing = 16.dp
    val DescriptionToButtonsSpacing = 24.dp
    val ButtonSpacing = 8.dp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DreamSetupScreen(onExit: (DreamSetupEvent) -> Unit) {
    // Guard to prevent double-firing events during animation
    val isExiting = remember { mutableStateOf(false) }

    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { sheetValue ->
                if (sheetValue == SheetValue.Hidden) {
                    // Only allow hiding if we are explicitly exiting.
                    isExiting.value
                } else {
                    true
                }
            },
        )
    val scope = rememberCoroutineScope()

    // Show the sheet on initial composition.
    LaunchedEffect(Unit) { sheetState.show() }

    // Helper to run the hide animation, then exit
    val animateAndExit = { event: DreamSetupEvent ->
        if (!isExiting.value) {
            Log.d(TAG, "Exit requested with event: $event. Starting exit process.")
            isExiting.value = true
            scope
                .launch { sheetState.hide() }
                .invokeOnCompletion {
                    // This block runs after the animation finishes (or is cancelled).
                    // We check isVisible to ensure the sheet is actually hidden before calling
                    // onExit.
                    if (!sheetState.isVisible) {
                        onExit(event)
                    } else {
                        // This case is rare, but could happen if the hide animation is
                        // interrupted. We log it to aid in debugging any future issues.
                        Log.w(TAG, "Exit animation interrupted. Resetting guard.")
                        isExiting.value = false
                    }
                }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            // Dismissal is handled via BackHandler and explicit buttons.
            // Scrim taps are ignored because confirmValueChange vetoes the Hidden state
            // unless isExiting is true.
        },
        sheetState = sheetState,
        scrimColor = Color.Transparent,
        dragHandle = null,
    ) {
        BackHandler { animateAndExit(DreamSetupEvent.Dismiss) }
        DreamSetupContent(onEvent = animateAndExit)
    }
}

@Composable
private fun DreamSetupContent(onEvent: (DreamSetupEvent) -> Unit) {
    val title = stringResource(R.string.dream_setup_title)
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .padding(
                    start = DreamSetupDimensions.HorizontalPadding,
                    end = DreamSetupDimensions.HorizontalPadding,
                    top = DreamSetupDimensions.TopPadding,
                    bottom = DreamSetupDimensions.BottomPadding,
                )
                .navigationBarsPadding()
                .semantics { paneTitle = title },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )

        Spacer(modifier = Modifier.height(DreamSetupDimensions.TitleToDescriptionSpacing))

        Text(
            text = stringResource(R.string.dream_setup_description),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = DreamSetupDimensions.HorizontalPadding),
        )

        Spacer(modifier = Modifier.height(DreamSetupDimensions.DescriptionToButtonsSpacing))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onEvent(DreamSetupEvent.NotNow) }) {
                Text(stringResource(R.string.dream_setup_button_not_now))
            }
            Button(onClick = { onEvent(DreamSetupEvent.SetUp) }) {
                Text(stringResource(R.string.dream_setup_button_setup))
            }
        }
    }
}
