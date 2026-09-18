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

package com.android.systemui.dream.ui.composable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.compose.ui.graphics.painter.rememberDrawablePainter
import com.android.systemui.dreams.ui.model.DreamItemUiModel
import com.android.systemui.dreams.ui.viewmodel.DreamDialogController
import com.android.systemui.dreams.ui.viewmodel.DreamSwitcherDialogViewModel
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.res.R
import kotlinx.coroutines.launch

private object Dimensions {
    val DialogPaddingHorizontal = 18.dp
    val DialogSpacing = 2.dp

    val ContainerCornerRadiusTop = 26.dp
    val ContainerCornerRadiusBottom = 4.dp
    val ContainerPadding = 16.dp
    val ContainerSpacerHeight = 24.dp

    val IconSize = 24.dp
    val IconSpacing = 8.dp

    val ActionRowMinHeight = 56.dp
    val ActionRowPadding = 16.dp

    val CardSize = 80.dp
    val CardSpacing = 8.dp
    val CardCornerRadius = 12.dp
    val CardBorderWidth = 3.dp
    val CardSelectedIconSize = 36.dp
    val CardAddIconSize = 48.dp

    val ScrollArrowSize = 48.dp
    val ScrollArrowIconSize = 32.dp
    val ScrollArrowIconPadding = 4.dp
    val ScrollArrowPaddingHorizontal = 4.dp
}

@Composable
fun DreamSwitcherDialog(
    viewModelFactory: DreamSwitcherDialogViewModel.Factory,
    dialogController: DreamDialogController,
) {
    val viewModel =
        rememberViewModel(traceName = "DreamSwitcherDialog") {
            viewModelFactory.create(dialogController)
        }

    DreamSelectionDialog(
        dreamInfos = viewModel.dreamItems,
        onDreamSelected = viewModel::onDreamSelected,
        onEditClicked = viewModel::onEditDreamClicked,
        onSettingsClicked = viewModel::onOpenSettingsClicked,
    )
}

/** Stateless core UI composable for the dream switcher dialog. */
@Composable
fun DreamSelectionDialog(
    dreamInfos: List<DreamItemUiModel>,
    onDreamSelected: (DreamItemUiModel) -> Unit,
    onEditClicked: (DreamItemUiModel) -> Unit,
    onSettingsClicked: () -> Unit,
) {
    val paneTitleString = stringResource(R.string.dream_switcher_choose_screensaver)
    Column(
        modifier =
            Modifier.wrapContentSize()
                .padding(horizontal = Dimensions.DialogPaddingHorizontal)
                .systemGestureExclusion()
                .semantics { paneTitle = paneTitleString },
        verticalArrangement = Arrangement.spacedBy(Dimensions.DialogSpacing),
    ) {
        if (dreamInfos.isEmpty()) {
            Text(
                stringResource(R.string.dream_switcher_select_dream_title),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            return@Column
        }

        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(
                        RoundedCornerShape(
                            topStart = Dimensions.ContainerCornerRadiusTop,
                            topEnd = Dimensions.ContainerCornerRadiusTop,
                            bottomStart = Dimensions.ContainerCornerRadiusBottom,
                            bottomEnd = Dimensions.ContainerCornerRadiusBottom,
                        )
                    )
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(Dimensions.ContainerPadding)
                    .wrapContentHeight()
        ) {
            DreamCarousel(
                dreamInfos = dreamInfos,
                onDreamSelected = onDreamSelected,
                onSettingsClicked = onSettingsClicked,
            )

            Spacer(modifier = Modifier.height(Dimensions.ContainerSpacerHeight))

            Row(
                modifier =
                    Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                        hideFromAccessibility()
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_palette),
                    contentDescription = null,
                    modifier = Modifier.size(Dimensions.IconSize),
                )
                Spacer(modifier = Modifier.width(Dimensions.IconSpacing))
                Text(stringResource(R.string.dream_switcher_choose_screensaver))
            }
        }

        val selectedUiDreamInfo = dreamInfos.firstOrNull { it.active }

        Box(modifier = Modifier.defaultMinSize(minHeight = Dimensions.ActionRowMinHeight)) {
            if (selectedUiDreamInfo != null) {
                when {
                    selectedUiDreamInfo.settingsActivity != null &&
                        selectedUiDreamInfo.title != null -> {
                        DreamActionRow(
                            iconRes = R.drawable.ic_edit_square,
                            text =
                                stringResource(
                                    R.string.dream_switcher_edit_button_text,
                                    selectedUiDreamInfo.title,
                                ),
                            onClick = { onEditClicked(selectedUiDreamInfo) },
                        )
                    }

                    selectedUiDreamInfo.appInfo?.launchIntent != null -> {
                        DreamActionRow(
                            iconRes = R.drawable.ic_open_in_new,
                            text =
                                stringResource(
                                    R.string.dream_switcher_go_to_app_button_text,
                                    selectedUiDreamInfo.appInfo.appName,
                                ),
                            onClick = { onEditClicked(selectedUiDreamInfo) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DreamCarousel(
    dreamInfos: List<DreamItemUiModel>,
    onDreamSelected: (DreamItemUiModel) -> Unit,
    onSettingsClicked: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        contentAlignment = Alignment.Center,
    ) {
        val lazyListState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()
        val density = LocalDensity.current
        val cardWidthPx = with(density) { Dimensions.CardSize.toPx() }
        val spacingPx = with(density) { Dimensions.CardSpacing.toPx() }
        val scrollAmount = cardWidthPx + spacingPx

        LazyRow(
            state = lazyListState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.CardSpacing),
        ) {
            items(items = dreamInfos, key = { it.componentName.flattenToString() }) { dreamInfo ->
                DreamCard(uiDreamInfo = dreamInfo, onClick = { onDreamSelected(dreamInfo) })
            }
            item(key = "open_settings") { OpenSettingsCard(onClick = onSettingsClicked) }
        }

        val selectedIndex = dreamInfos.indexOfFirst { it.active }

        // Auto-scroll effect (only trigger on initial load with valid data)
        LaunchedEffect(selectedIndex > 0) {
            if (selectedIndex == -1) return@LaunchedEffect

            val viewportWidth = lazyListState.layoutInfo.viewportSize.width
            if (viewportWidth == 0) {
                lazyListState.animateScrollToItem(selectedIndex)
            } else {
                val scrollOffset = (viewportWidth / 2 - cardWidthPx / 2).toInt()
                lazyListState.animateScrollToItem(selectedIndex, scrollOffset = -scrollOffset)
            }
        }

        // Scroll Arrows
        val showBackwardArrow by remember {
            derivedStateOf {
                lazyListState.firstVisibleItemIndex > 0 ||
                    lazyListState.firstVisibleItemScrollOffset >= cardWidthPx / 2
            }
        }

        val showForwardArrow by remember {
            derivedStateOf {
                val layoutInfo = lazyListState.layoutInfo
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                when {
                    lastVisibleItem == null -> false
                    lastVisibleItem.index < layoutInfo.totalItemsCount - 1 -> true
                    else -> {
                        val hiddenAmount =
                            (lastVisibleItem.offset + lastVisibleItem.size) -
                                layoutInfo.viewportEndOffset
                        hiddenAmount >= cardWidthPx / 2
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showBackwardArrow,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            ScrollHintArrow(
                onClick = {
                    coroutineScope.launch { lazyListState.animateScrollBy(-scrollAmount) }
                },
                modifier =
                    Modifier.padding(horizontal = Dimensions.ScrollArrowPaddingHorizontal)
                        .rotate(180f),
            )
        }

        AnimatedVisibility(
            visible = showForwardArrow,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            ScrollHintArrow(
                onClick = { coroutineScope.launch { lazyListState.animateScrollBy(scrollAmount) } },
                modifier = Modifier.padding(horizontal = Dimensions.ScrollArrowPaddingHorizontal),
            )
        }
    }
}

@Composable
private fun DreamCard(uiDreamInfo: DreamItemUiModel, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier.size(Dimensions.CardSize)
                .clip(RoundedCornerShape(Dimensions.CardCornerRadius))
                .selectable(
                    selected = uiDreamInfo.active,
                    onClick = onClick,
                    role = Role.RadioButton,
                )
                .then(
                    if (uiDreamInfo.active) {
                        Modifier.border(
                            Dimensions.CardBorderWidth,
                            MaterialTheme.colorScheme.tertiary,
                            RoundedCornerShape(Dimensions.CardCornerRadius),
                        )
                    } else {
                        Modifier
                    }
                )
                .semantics(mergeDescendants = true) {
                    uiDreamInfo.title?.let { contentDescription = it.toString() }
                }
    ) {
        val painter = rememberDrawablePainter(uiDreamInfo.previewImage ?: uiDreamInfo.icon)

        Image(
            painter = painter,
            contentDescription = null,
            contentScale =
                if (uiDreamInfo.previewImage != null) ContentScale.Crop else ContentScale.Inside,
            modifier = Modifier.fillMaxSize(),
        )

        if (uiDreamInfo.active) {
            Box(
                modifier =
                    Modifier.size(Dimensions.CardSelectedIconSize)
                        .align(Alignment.Center)
                        .background(MaterialTheme.colorScheme.tertiary, CircleShape)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_small),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    tint = MaterialTheme.colorScheme.onTertiary,
                )
            }
        }
    }
}

@Composable
private fun OpenSettingsCard(onClick: () -> Unit) {
    val moreScreensaversDesc = stringResource(R.string.dream_switcher_more_screensavers)
    Box(
        modifier =
            Modifier.size(Dimensions.CardSize)
                .clip(RoundedCornerShape(Dimensions.CardCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceBright)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = moreScreensaversDesc },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = null,
            modifier = Modifier.size(Dimensions.CardAddIconSize),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DreamActionRow(iconRes: Int, text: String, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        topStart = Dimensions.ContainerCornerRadiusBottom,
                        topEnd = Dimensions.ContainerCornerRadiusBottom,
                        bottomStart = Dimensions.ContainerCornerRadiusTop,
                        bottomEnd = Dimensions.ContainerCornerRadiusTop,
                    )
                )
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(Dimensions.ActionRowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(Dimensions.IconSize),
        )
        Spacer(modifier = Modifier.width(Dimensions.IconSpacing))
        Text(text)
    }
}

@Composable
private fun ScrollHintArrow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(Dimensions.ScrollArrowSize)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = null,
            modifier =
                Modifier.size(Dimensions.ScrollArrowIconSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f))
                    .padding(Dimensions.ScrollArrowIconPadding),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}
