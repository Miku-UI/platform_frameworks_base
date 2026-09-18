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

package com.android.settingslib.widget

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.settingslib.widget.preference.searchbox.R
import com.google.android.material.button.MaterialButton

/**
 * A custom [Preference] that provides an interactive search box.
 *
 * This preference is designed to be used within a [androidx.preference.PreferenceFragmentCompat]
 * to allow users to input search queries. It includes an [EditText] for text input
 * and a [MaterialButton] to clear the input.
 *
 * The search action can be triggered by pressing the "Search" button on the soft keyboard.
 * External code can listen for search events using [setOnSearchClickListener].
 *
 * This preference also handles instance state saving and restoration, ensuring the
 * text in the search box is preserved across configuration changes (like screen rotation).
 *
 * @property context The [Context] this is associated with, through which it can access the current theme, resources, SharedPreferences, etc.
 * @property attrs The attributes of the XML tag that is inflating the preference
 * @property defStyleAttr An attribute in the current theme that contains a reference to a style resource that supplies default values for the view. Can be 0 to not look for defaults.
 * @property defStyleRes A resource identifier of a style resource that supplies default values for the view, used only if defStyleAttr is 0 or can not be found in the theme. Can be 0 to not look for defaults.
 */
class SearchBoxPreference
@JvmOverloads
constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0,
  defStyleRes: Int = 0,
) : Preference(context, attrs, defStyleAttr, defStyleRes), GroupSectionDividerMixin {
  private var text: String? = null
  private var hintText: String? = null
  private var editText: EditText? = null // Keep a reference to the EditText
  private var clearButton: MaterialButton? = null

  private var clearButtonDescription: String? = null

  private var onSearchClickListener: ((String) -> Unit)? = null

  private val textWatcher = object : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
    }
    override fun afterTextChanged(s: Editable?) {
      text = s.toString()
      clearButton?.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
    }
  }

  init {
    layoutResource = R.layout.settingslib_expressive_preference_searchbox
    setIcon(R.drawable.settingslib_expressive_searchbox_search_icon_24dp)
  }

  override fun onBindViewHolder(holder: PreferenceViewHolder) {
    super.onBindViewHolder(holder)

    holder.itemView.isClickable = false

    val searchBoxContainer = holder.findViewById(R.id.searchbox_container)
    editText = holder.findViewById(R.id.search_query) as? EditText
    clearButton = holder.findViewById(R.id.clear_button) as? MaterialButton

    editText?.removeTextChangedListener(textWatcher)

    getText()?.let { text ->
      editText?.setText(text)
    }

    clearButton?.visibility = if (editText?.text.isNullOrEmpty()) View.GONE else View.VISIBLE

    hintText?.let { hint ->
      editText?.setHint(hint)
    }

    searchBoxContainer?.setOnClickListener {
      editText?.requestFocus()
      val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
      imm.showSoftInput(editText, 0)
    }

    editText?.addTextChangedListener(textWatcher)

    clearButton?.setOnClickListener {
      editText?.setText("")
    }
    clearButtonDescription?.let { description ->
      clearButton?.setContentDescription(description)
    }

    editText?.setOnEditorActionListener{ v: TextView?, actionId: Int, event: KeyEvent? ->
      if (actionId == EditorInfo.IME_ACTION_SEARCH) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(v?.windowToken, 0)

        onSearchClickListener?.invoke(text ?: "")
        return@setOnEditorActionListener true
      }
      false
    }
  }



  /**
   * Sets a listener to be invoked when the user triggers a search action.
   *
   * The search action is typically triggered by pressing the "Search" button on the soft keyboard
   * while the [EditText] in the search box has focus.
   *
   * @param listener A lambda function that takes the search query [String] as a parameter.
   *                 This listener will be called with the current text from the search box
   *                 when a search action is performed. Pass `null` to remove the listener.
   */
  fun setOnSearchClickListener(listener: ((String) -> Unit)?) {
    onSearchClickListener = listener
  }

  fun setSearchHint(text: String?) {
    hintText = text
    editText?.setHint(hintText)
  }

  fun setClearButtonDescription(text: String?) {
    clearButtonDescription = text
    clearButton?.setContentDescription(text)
  }

  /**
   * Sets the text for this [SearchBoxPreference].
   *
   * This method updates the internal text state [text] and updates the text displayed in the [EditText] within the search box layout.
   *
   * @param text The [String] to set as the current text. Can be `null` to clear the text.
   */
  fun setText(text: String?) {
    this.text = text
    editText?.setText(text)

    notifyChanged()
  }

  /**
   * Gets the text from the current data storage.
   *
   * @return The current preference value
   */
  fun getText(): String? {
    return text
  }

  override fun onSaveInstanceState(): Parcelable {
    val superState = super.onSaveInstanceState()

    val myState = SavedState(superState)
    myState.mText = getText()
    return myState
  }

  override fun onRestoreInstanceState(state: Parcelable?) {
    if (state == null || state !is SavedState) {
      super.onRestoreInstanceState(state)
      return
    }
    super.onRestoreInstanceState(state.superState)
    setText(state.mText)
  }

  private class SavedState : BaseSavedState {
    var mText: String? = null

    constructor(source: Parcel) : super(source) {
      mText = source.readString()
    }

    constructor(superState: Parcelable?) : super(superState)

    override fun writeToParcel(dest: Parcel, flags: Int) {
      super.writeToParcel(dest, flags)
      dest.writeString(mText)
    }
    companion object {
      @JvmField
      val CREATOR: Parcelable.Creator<SavedState?> = object : Parcelable.Creator<SavedState?> {
        override fun createFromParcel(`in`: Parcel): SavedState {
          return SavedState(`in`)
        }

        override fun newArray(size: Int): Array<SavedState?> {
          return arrayOfNulls(size)
        }
      }
    }}
}
