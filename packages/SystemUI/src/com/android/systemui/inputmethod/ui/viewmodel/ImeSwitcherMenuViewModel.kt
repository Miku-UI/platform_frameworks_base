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

package com.android.systemui.inputmethod.ui.viewmodel

import android.annotation.IntRange
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.android.internal.inputmethod.IImeSwitcherMenu
import com.android.systemui.Dumpable
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.shared.model.asIcon
import com.android.systemui.dump.DumpManager
import com.android.systemui.inputmethod.domain.interactor.ImeSwitcherMenuInteractor
import com.android.systemui.inputmethod.shared.model.ImeSwitcherMenuModel
import com.android.systemui.inputmethod.shared.model.ImeSwitcherMenuModel.Companion.NOT_A_SUBTYPE_INDEX
import com.android.systemui.inputmethod.shared.model.ModelChangeListener
import com.android.systemui.lifecycle.Activatable
import com.android.systemui.util.asIndenting
import com.android.systemui.util.printCollection
import com.android.systemui.util.printSection
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.io.PrintWriter
import kotlinx.coroutines.awaitCancellation

/**
 * The ViewModel for the IME Switcher Menu. This is created per-user via [Factory], when requested
 * to show the menu.
 */
class ImeSwitcherMenuViewModel
@AssistedInject
constructor(
    @Assisted private val context: Context,
    @Assisted private val userId: Int,
    private val interactor: ImeSwitcherMenuInteractor,
    private val dumpManager: DumpManager,
) : Activatable, Dumpable {

    /**
     * The action to be invoked when the Settings button is clicked. If `null`, the button should
     * not be displayed.
     */
    val settingsButtonAction = mutableStateOf<(() -> Unit)?>(null)

    /** The list of items to be displayed in the menu. */
    val menuItems = mutableStateListOf<MenuItem>()

    /** The index of the currently selected item in the list, of `-1` if nothing is selected. */
    val selectedIndex = mutableIntStateOf(-1)

    /** The unique name to register this view model for state dumping. */
    private val dumpName = "${TAG}_u$userId"

    init {
        // Activate is called with a slight delay after the UI is shown. Query the initial model and
        // set it as soon as the ViewModel is created.
        val initial = interactor.getModel(userId)
        updateState(initial)
    }

    override suspend fun activate(): Nothing {
        // Query the model again in case it changed since init.
        val current = interactor.getModel(userId)
        updateState(current)

        val listener =
            object : ModelChangeListener {
                override fun onChanged(userId: Int, model: ImeSwitcherMenuModel?) {
                    if (userId == this@ImeSwitcherMenuViewModel.userId) {
                        updateState(model)
                    }
                }
            }

        try {
            interactor.registerModelChangeListener(listener)
            onVisibilityChanged(true)
            dumpManager.registerNormalDumpable(name = dumpName, this@ImeSwitcherMenuViewModel)
            awaitCancellation()
        } finally {
            interactor.unregisterModelChangeListener(listener)
            onVisibilityChanged(false)
            dumpManager.unregisterDumpable(name = dumpName)
        }
    }

    override fun dump(pw: PrintWriter, args: Array<out String?>) {
        pw.asIndenting().run {
            printSection("$TAG (userId: $userId displayId: ${context.displayId})") {
                println("selectedIndex: ${selectedIndex.intValue}")
                printCollection("menuItems", menuItems)
            }
        }
    }

    /**
     * Updates the state of this view model with the given data model.
     *
     * @param model the model of the data to update the state.
     */
    fun updateState(model: ImeSwitcherMenuModel?) {
        if (model == null) {
            return
        }

        val newItems = getMenuItems(context, model.items)
        menuItems.clear()
        menuItems.addAll(newItems)

        selectedIndex.intValue =
            getSelectedIndex(newItems, model.selectedImeId, model.selectedSubtypeIndex)

        updateSettingsButtonAction(model.selectedImeSettingsIntent, model.isScreenLocked, userId)
    }

    /**
     * Notifies the interactor when an IME and subtype in the UI.
     *
     * @param imeId the ID of the selected IME.
     * @param subtypeIndex the selected subtype, as an index in the IME's array of subtypes, or
     *   [NOT_A_SUBTYPE_INDEX] if the system should decide the most sensible subtype.
     */
    fun onImeAndSubtypeSelected(imeId: String, subtypeIndex: Int) =
        interactor.onImeAndSubtypeSelected(imeId, subtypeIndex, userId)

    /**
     * Notifies the interactor when the visibility of the UI changed.
     *
     * @param visible the new visibility of the UI.
     */
    fun onVisibilityChanged(visible: Boolean) =
        interactor.onVisibilityChanged(visible, context.displayId, userId)

    /**
     * Updates the settings button action.
     *
     * If the given intent is non-`null`, the screen is unlocked, and the device is provisioned,
     * this sets the action to a runnable that starts the activity from the intent. Otherwise, it
     * sets the action to `null`, indicating the button should be hidden.
     *
     * @param settingsIntent the intent for the settings activity of the selected IME, or `null` if
     *   no IME is selected, or the selected IME does not have a settings activity.
     * @param isScreenLocked whether the screen is currently locked.
     * @param userId the ID of the user to start the intent for.
     */
    private fun updateSettingsButtonAction(
        settingsIntent: Intent?,
        isScreenLocked: Boolean,
        userId: Int,
    ) {
        val hasButton =
            settingsIntent != null && !isScreenLocked && interactor.shouldShowSettingsButton(userId)
        if (hasButton) {
            settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            settingsButtonAction.value = {
                context.startActivityAsUser(settingsIntent, UserHandle.of(userId))
            }
        } else {
            settingsButtonAction.value = null
        }
    }

    /**
     * Item to be displayed in the menu, containing an IME and optionally an IME subtype.
     *
     * @param imeName the name of the IME.
     * @param subtypeName the name of the IME subtype, or `null` if this item doesn't have a
     *   subtype.
     * @param layoutName the name of the subtype's layout, or `null` if this item doesn't have a
     *   subtype, or doesn't specify a layout.
     * @param imeId the ID of the IME.
     * @param subtypeIndex the index of the subtype in the IME's array of subtypes, or
     *   [NOT_A_SUBTYPE_INDEX] if this item doesn't have a subtype.
     * @param hasDivider whether this item has a divider, displayed before the header.
     * @param hasHeader whether this item has a header.
     */
    data class MenuItem(
        val imeName: CharSequence,
        val subtypeName: CharSequence?,
        val subtypeShortLabel: CharSequence?,
        val subtypeIcon: Icon?,
        val layoutName: CharSequence?,
        val imeId: String,
        @param:IntRange(from = NOT_A_SUBTYPE_INDEX.toLong()) val subtypeIndex: Int,
        val hasDivider: Boolean,
        val hasHeader: Boolean,
    )

    @AssistedFactory
    interface Factory {

        /**
         * Creates a new view model.
         *
         * @param context the context to create the view model with.
         * @param userId the ID of the user to create the view model for.
         */
        fun create(context: Context, userId: Int): ImeSwitcherMenuViewModel
    }

    companion object {

        private const val TAG = "ImeSwitcherMenuViewModel"

        /**
         * Creates the list of menu items from the given list of IMEs and subtypes. This handles
         * adding headers and dividers between groups of items from different IMEs as follows:
         * * If there is only one group, no divider or header will be added.
         * * A divider is added before each group, except the first one.
         * * A header is added before each group (after the divider, if it exists) if the group has
         *   at least two items, or a single item with a subtype name.
         *
         * @param context the context to use for loading icon resources.
         * @param items the list of IME and subtype items.
         */
        private fun getMenuItems(
            context: Context,
            items: List<IImeSwitcherMenu.Item>,
        ): List<MenuItem> {
            val menuItems = mutableListOf<MenuItem>()
            if (items.isEmpty()) {
                return menuItems
            }

            // Initialize to the last IME id to avoid headers if there is only a single IME.
            var prevImeId = items.last().imeId
            var firstGroup = true
            for (i in items.indices) {
                val item = items[i]

                var hasDivider = false
                var hasHeader = false

                val groupChange = item.imeId != prevImeId
                if (groupChange) {
                    hasDivider = !firstGroup
                    // Has a header if we have at least two items, or a single item with a subtype
                    // name.
                    val nextItemId: String? = items.getOrNull(i + 1)?.imeId
                    hasHeader = item.subtypeName != null || item.imeId == nextItemId
                    firstGroup = false
                    prevImeId = item.imeId
                }

                val icon =
                    if (item.subtypeIconResId != 0 && !item.imePackageName.isNullOrEmpty()) {
                        android.graphics.drawable.Icon.createWithResource(
                                item.imePackageName,
                                item.subtypeIconResId,
                            )
                            .loadDrawable(context)
                            ?.asIcon()
                    } else {
                        null
                    }
                menuItems.add(
                    MenuItem(
                        item.imeName,
                        item.subtypeName,
                        item.subtypeShortLabel,
                        icon,
                        item.layoutName,
                        item.imeId,
                        item.subtypeIndex,
                        hasDivider,
                        hasHeader,
                    )
                )
            }

            return menuItems
        }

        /**
         * Gets the index of the selected item.
         *
         * @param items the list of menu items.
         * @param selectedImeId the ID of the selected IME.
         * @param selectedSubtypeIndex the index of the selected subtype in the IME's array of
         *   subtypes, or [NOT_A_SUBTYPE_INDEX] if no subtype is selected.
         * @return the index of the selected item, or `-1` if no item is selected.
         */
        @IntRange(from = -1)
        private fun getSelectedIndex(
            items: List<MenuItem>,
            selectedImeId: String?,
            selectedSubtypeIndex: Int,
        ): Int {
            // Returns -1 if there is no selected IME, or the selected subtype is enabled but not in
            // the list. This can happen if an implicit subtype is selected, but we got a list of
            // explicit subtypes. In this case, the implicit subtype will no longer be included in
            // the list.
            return items.indexOfFirst { item ->
                item.imeId == selectedImeId &&
                    // Match if:
                    // - no subtype is selected (match first subtype) or
                    ((item.subtypeIndex == 0 && selectedSubtypeIndex == NOT_A_SUBTYPE_INDEX) ||
                        // - item has no subtype or
                        item.subtypeIndex == NOT_A_SUBTYPE_INDEX ||
                        // - exact subtype match
                        item.subtypeIndex == selectedSubtypeIndex)
            }
        }
    }
}
