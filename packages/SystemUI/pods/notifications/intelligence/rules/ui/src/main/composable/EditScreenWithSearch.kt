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

package com.android.systemui.notifications.intelligence.rules.ui.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.android.systemui.res.R
import kotlinx.coroutines.launch

/**
 * A generic composable supporting an edit screen for a particular type [T]. The screen shows the
 * currently-selected set of items *and* lets the user search for items to add or remove them.
 *
 * @param allSearchResults a list of full search results, shown when the user first opens the search
 *   box but hasn't typed a query yet. If null, no default results will be shown.
 * @param fetchSearchResults a function that fetches the relevant search results based on the
 *   current query.
 *
 * See also: [EditScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> EditScreenWithSearch(
    title: String,
    initialSelection: List<T>,
    onSelectionSaved: (List<T>) -> Unit,
    onDismissRequest: () -> Unit,
    allSearchResults: List<T>? = null,
    fetchSearchResults: suspend (query: String) -> List<T>?,
    sortKey: (T) -> String,
    uniqueId: (T) -> Any,
    icon: @Composable (T) -> Unit,
    text: (T) -> String,
) {
    val scope = rememberCoroutineScope()

    val searchBarState = rememberSearchBarState()
    val textFieldState = rememberTextFieldState()
    val searchResults: List<T>? by
        // allSearchResults might change from null to non-null when results are loaded, so
        // `searchResults` should be re-calculated when that value changes.
        produceState<List<T>?>(emptyList(), textFieldState.text, allSearchResults) {
            value = fetchSearchResults(textFieldState.text.toString())
        }
    val inputField =
        @Composable {
            SearchBarDefaults.InputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
                placeholder = {
                    Text(
                        text = stringResource(R.string.notification_rules_search),
                        // Use `clearAndSetSemantics` because `ExpandedFullScreenSearchBar` will
                        // handle accessibility for us.
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                },
            )
        }

    EditScreen(
        title = title,
        initialSelection = initialSelection,
        onSelectionSaved = onSelectionSaved,
        onDismissRequest = onDismissRequest,
        sortKey = sortKey,
        uniqueId = uniqueId,
        icon = icon,
        text = text,
        inputSlot = { SearchBar(state = searchBarState, inputField = inputField) },
    ) { currentSelection, selectionHandler ->
        ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField) {
            Box {
                val currentSearchResults = searchResults
                if (currentSearchResults != null) {
                    SearchResults(
                        searchResults = currentSearchResults,
                        selectionHandler = selectionHandler,
                        currentSelection = currentSelection,
                        uniqueId = uniqueId,
                        icon = icon,
                        text = text,
                    )
                } else {
                    LoadingIcon(Modifier.fillMaxSize())
                }

                // The floating save button has to be included both here and in [EditScreen] because
                // the search results do a fullscreen takeover.
                FloatingSaveButton(
                    currentSelection = currentSelection,
                    onSelectionSaved = onSelectionSaved,
                    sortKey = sortKey,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

/**
 * Renders the current search results with affordances to add or remove items from the selection.
 */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun <T> SearchResults(
    searchResults: List<T>,
    selectionHandler: SelectionHandler<T>,
    currentSelection: List<T>,
    uniqueId: (T) -> Any,
    icon: @Composable (T) -> Unit,
    text: (T) -> String,
) {
    LazyColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
        // fillMaxSize ensures that the FloatingSaveButton is always at the bottom.
        modifier = Modifier.fillMaxSize().padding(top = 8.dp),
    ) {
        item(key = "Search results") {
            Text(
                stringResource(R.string.notification_rules_search_results),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
            )
        }
        items(searchResults, key = uniqueId) {
            Item(
                model = it,
                isSelected = it in currentSelection,
                selectionHandler = selectionHandler,
                icon = icon,
                text = text,
            )
        }
    }
}

@Composable
private fun LoadingIcon(modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 32.dp).size(32.dp),
        )
    }
}
