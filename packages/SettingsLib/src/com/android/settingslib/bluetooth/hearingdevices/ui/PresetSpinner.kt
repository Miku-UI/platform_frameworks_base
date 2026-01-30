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

package com.android.settingslib.bluetooth.hearingdevices.ui

import android.bluetooth.BluetoothHapClient
import android.bluetooth.BluetoothHapPresetInfo
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView

import com.android.settingslib.R

/**
 * A custom view that combines a title and a [Spinner] to display and manage a list of hearing aid
 * presets.
 *
 * <p>This view handles the display logic, populating the spinner with preset names and notifying a
 * listener when a new preset is selected.
 */
class PresetSpinner
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0,
    private val spinnerAdapter: HearingDevicesSpinnerAdapter = HearingDevicesSpinnerAdapter(context),
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {

    /** Listener for handling value changes in the spinner. */
    fun interface OnChangeListener {
        /**
         * Called when the spinner's selected value changes.
         *
         * @param spinner The [PresetSpinner] that triggered the event.
         * @param presetIndex The index of the newly selected preset.
         */
        fun onValueChange(spinner: PresetSpinner, presetIndex: Int)
    }

    init {
        inflate(context, R.layout.hearing_device_preset_spinner, this)
    }

    private val titleView: TextView = findViewById(R.id.preset_title)
    private val spinnerView: Spinner = findViewById(R.id.preset_spinner)
    private var onChangeListener: OnChangeListener? = null
    private var presetInfos: List<BluetoothHapPresetInfo>? = null

    init {
        spinnerView.adapter = spinnerAdapter
        spinnerView.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    spinnerAdapter.setSelected(position)
                    presetInfos?.let { infos ->
                        onChangeListener?.onValueChange(this@PresetSpinner, infos[position].index)
                    }
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    // Do nothing
                }
            }
    }

    override fun setEnabled(enabled: Boolean) {
        spinnerView.isEnabled = enabled
        alpha = if (enabled) ENABLED_ALPHA else DISABLED_ALPHA
    }

    override fun isEnabled(): Boolean {
        return spinnerView.isEnabled
    }

    /**
     * Sets the title text of the control.
     *
     * @param title The CharSequence to be set as the title.
     */
    fun setTitle(title: CharSequence) {
        titleView.text = title
        titleView.visibility = if (title.isEmpty()) GONE else VISIBLE
    }

    /**
     * Gets the current title text of the control.
     *
     * @return The current title as a CharSequence.
     */
    fun getTitle(): CharSequence = titleView.text

    /**
     * Sets a listener to be notified when the selected preset changes.
     *
     * @param listener The listener to be set.
     */
    fun setOnChangeListener(listener: OnChangeListener) {
        onChangeListener = listener
    }

    /**
     * Sets the list of presets to be displayed in the spinner.
     *
     * @param infos The list of [BluetoothHapPresetInfo] to populate the spinner with.
     */
    fun setList(infos: List<BluetoothHapPresetInfo>) {
        presetInfos = infos

        spinnerAdapter.clear()
        spinnerAdapter.addAll(infos.map { info -> info.name }.toList())
    }

    /**
     * Retrieves the list of presets currently being displayed in the spinner.
     *
     * @return The list of [BluetoothHapPresetInfo], or {@code null} if the list has not been set.
     */
    fun getList(): List<BluetoothHapPresetInfo>? {
        return presetInfos
    }

    /**
     * Sets the spinner's selection based on a given preset index.
     *
     * @param index The preset index to select.
     */
    fun setValue(index: Int) {
        presetInfos?.apply {
            val position = indexOfFirst { it.index == index }
            if (position != -1) {
                spinnerView.setSelection(position, false)
                spinnerAdapter.setSelected(position)
            }
        }
    }

    /**
     * Gets the index of the currently selected preset.
     *
     * @return The index of the selected preset, or [BluetoothHapClient.PRESET_INDEX_UNAVAILABLE] if
     *   no preset is selected or the list is null.
     */
    fun getValue(): Int {
        return presetInfos?.getOrNull(spinnerView.selectedItemPosition)?.index
            ?: BluetoothHapClient.PRESET_INDEX_UNAVAILABLE
    }

    /**
     * Gets the name of the currently selected preset.
     *
     * @return The name of the selected preset, or an empty string ("") if no preset is selected
     * or the list is null.
     */
    fun getValueName(): CharSequence {
        return presetInfos?.getOrNull(spinnerView.selectedItemPosition)?.name ?: ""
    }

    companion object {
        private const val ENABLED_ALPHA = 1f
        private const val DISABLED_ALPHA = 0.38f
    }
}
