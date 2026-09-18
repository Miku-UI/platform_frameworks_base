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
package com.android.settingslib.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.widget.ImageView
import androidx.core.content.withStyledAttributes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.settingslib.widget.preference.switcher.R

/**
 * A custom preference that displays a list of items in a dialog for selection.
 *
 * This preference manages its state, including the list of items and the currently selected item,
 * and persists the selection. It updates its own title, summary, and icon to reflect the current
 * selection. It is designed to work with multiple instances on the same screen without conflicts.
 *
 * @see DialogListSwitcherBaseFragment
 */
class ListDialogSwitcherPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs), FragmentAttachablePreference {

    /**
     * The list of items to be displayed in the selection dialog.
     *
     * Setting this property populates the dialog and attempts to restore the previously
     * selected item based on the persisted ID.
     */
    var items: List<ListDialogItemInfo> = emptyList()
        set(value) {
            field = value
            restoreSelectedItem(getPersistedString(null))
        }

    /**
     * The currently selected item.
     *
     * This property is read-only from outside the class. It is updated internally when a user
     * makes a selection, and the preference's view is automatically updated to match.
     */
    var currentItem: ListDialogItemInfo? = null
        private set(value) {
            if (field != value) {
                field = value
                updatePreferenceView()
            }
        }

    /**
     * An optional listener that is invoked when a user selects an item from the dialog.
     *
     * The preference's state is updated automatically before this listener is called.
     */
    var onItemSelectedListener: ((ListDialogItemInfo) -> Unit)? = null

    private var dialogButtonIcon: Drawable? = null
    private var onDialogButtonClicked: (() -> Unit)? = null
    private lateinit var viewModel: ListSwitcherViewModel

    private var dialogTitle: String? = null
    private var dialogButtonText: String? = null
    private var defaultTitle: String? = null
    private var defaultSummary: String? = null
    private var defaultIcon: Drawable? = null

    init {
        layoutResource =
            if (SettingsThemeHelper.isExpressiveTheme(context)) {
                com.android.settingslib.widget.theme.R.layout.settingslib_expressive_preference
            } else {
                com.android.settingslib.widget.theme.R.layout.settingslib_preference
            }
        isPersistent = true

        context.withStyledAttributes(attrs, R.styleable.ListDialogSwitcherPreference) {
            dialogTitle = getString(R.styleable.ListDialogSwitcherPreference_dialogTitle)
            dialogButtonText = getString(R.styleable.ListDialogSwitcherPreference_dialogButtonText)
            defaultTitle = getString(R.styleable.ListDialogSwitcherPreference_defaultTitle)
            defaultSummary = getString(R.styleable.ListDialogSwitcherPreference_defaultSummary)
            defaultIcon = getDrawable(R.styleable.ListDialogSwitcherPreference_defaultIcon)
        }

        updatePreferenceView()
    }

    /**
     * Initializes the preference and connects it to the shared [ListSwitcherViewModel].
     *
     * This must be called from the hosting fragment (e.g., in `onViewCreated`) to set up the
     * necessary observers for handling dialog events.
     *
     * @param lifecycleOwner The [LifecycleOwner] of the hosting fragment, used for observing LiveData.
     */
    fun initialize(lifecycleOwner: LifecycleOwner) {
        val activity = context as? FragmentActivity
            ?: throw IllegalStateException("ListDialogSwitcherPreference must be used within a FragmentActivity context.")

        viewModel = ViewModelProvider(activity).get(ListSwitcherViewModel::class.java)

        viewModel.onItemSelected.observe(lifecycleOwner) { (key, item) ->
            if (key == this.key) {
                onItemSelected(item)
                onItemSelectedListener?.invoke(item)
            }
        }
        onDialogButtonClicked?.let {
            viewModel.registerOnDialogButtonClickedCallback(key, it)
        }
    }

    override fun onDetached() {
        super.onDetached()
        if (this::viewModel.isInitialized) {
            viewModel.unregisterOnDialogButtonClickedCallback(key)
        }
    }

    override fun onGetDefaultValue(a: android.content.res.TypedArray, index: Int): Any? {
        return a.getString(index)
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        restoreSelectedItem(getPersistedString(defaultValue as? String))
    }

    private fun restoreSelectedItem(persistedId: String?) {
        currentItem = if (!persistedId.isNullOrEmpty()) {
            items.firstOrNull { it.id == persistedId }
        } else {
            null
        }
        updatePreferenceView()
    }

    private fun updatePreferenceView() {
        title = currentItem?.title ?: defaultTitle
        summary = currentItem?.summary ?: defaultSummary
        icon = if (currentItem != null) {
            currentItem?.icon
        } else {
            defaultIcon
        }
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val iconView = holder.itemView.findViewById<ImageView>(android.R.id.icon)
        if (iconView?.drawable != null) {
            iconView.setCircularIcon(iconView.drawable)
        }
    }

    override fun onFragmentViewCreated(lifecycleOwner: LifecycleOwner) {
        // This is the logic you had in DialogListSwitcherBaseFragment.onViewCreated
        initialize(lifecycleOwner)
    }

    override fun displayCustomDialog(fragment: Fragment, preferenceKey: String) {
        // This is the logic you had in DialogListSwitcherBaseFragment.onDisplayPreferenceDialog
        val dialogFragment = ListSwitcherDialogFragment.newInstance(preferenceKey)
        dialogFragment.show(fragment.parentFragmentManager, preferenceKey)
    }

    override fun onClick() {
        val activity = context as? FragmentActivity
            ?: throw IllegalStateException("ListDialogSwitcherPreference must be used within a FragmentActivity context to show the dialog.")
        val viewModel = ViewModelProvider(activity).get(ListSwitcherViewModel::class.java)

        viewModel.items = this.items
        viewModel.dialogTitle = this.dialogTitle
        viewModel.dialogButtonText = this.dialogButtonText
        viewModel.dialogButtonIcon = this.dialogButtonIcon
        viewModel.preferenceKey = this.key

        preferenceManager.showDialog(this)
    }

    /**
     * Sets the title to be displayed at the top of the selection dialog.
     *
     * @param title The title text.
     */
    fun setDialogTitle(title: String) {
        this.dialogTitle = title
    }

    /**
     * Sets the properties for the optional action button at the bottom of the dialog.
     *
     * @param text The text to display on the button.
     * @param icon An optional icon to display to the left of the text.
     * @param listener The lambda to be executed when the button is clicked.
     */
    fun setDialogButton(text: String, icon: Drawable? = null, listener: () -> Unit) {
        this.dialogButtonText = text
        this.dialogButtonIcon = icon
        this.onDialogButtonClicked = listener
    }

    /**
     * Sets the action for the optional button at the bottom of the dialog.
     *
     * This overload is useful when the button's text is set via XML and only the click
     * behavior needs to be defined programmatically.
     *
     * @param listener The lambda to be executed when the button is clicked.
     */
    fun setDialogButton(listener: () -> Unit) {
        this.onDialogButtonClicked = listener
    }

    /**
     * Updates the preference's state with the newly selected item.
     *
     * This method is called internally when an item is selected in the dialog.
     *
     * @param item The item that was selected.
     */
    fun onItemSelected(item: ListDialogItemInfo) {
        this.currentItem = item
        persistString(item.id)
    }
}
