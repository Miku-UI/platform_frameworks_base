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

package com.android.systemui.topwindoweffects.data.repository

import android.content.SharedPreferences
import androidx.core.content.edit
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.topwindoweffects.data.repository.InvocationEffectPreferencesImpl.Companion.DEFAULT_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_PREFERENCE
import com.android.systemui.topwindoweffects.data.repository.InvocationEffectPreferencesImpl.Companion.DEFAULT_INWARD_EFFECT_PADDING_DURATION_MS
import com.android.systemui.topwindoweffects.data.repository.InvocationEffectPreferencesImpl.Companion.DEFAULT_OUTWARD_EFFECT_DURATION_MS
import com.android.systemui.topwindoweffects.data.repository.InvocationEffectPreferencesImpl.Companion.INVOCATION_EFFECT_ANIMATION_IN_DURATION_PADDING_MS
import com.android.systemui.topwindoweffects.data.repository.InvocationEffectPreferencesImpl.Companion.INVOCATION_EFFECT_ANIMATION_OUT_DURATION_MS
import com.android.systemui.topwindoweffects.data.repository.InvocationEffectPreferencesImpl.Companion.IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT
import com.android.systemui.topwindoweffects.data.repository.InvocationEffectPreferencesImpl.Companion.PERSISTED_FOR_ASSISTANT_PREFERENCE
import com.android.systemui.topwindoweffects.data.repository.InvocationEffectPreferencesImpl.Companion.PERSISTED_FOR_USER_PREFERENCE
import java.io.PrintWriter
import kotlinx.coroutines.flow.MutableStateFlow

val Kosmos.fakeInvocationEffectPreferences by Kosmos.Fixture { FakeInvocationEffectPreferences() }

class FakeInvocationEffectPreferences : InvocationEffectPreferences {

    var activeAssistant: String = ""
    var activeUserId: Int = Int.MIN_VALUE

    private var fakeSharedPreferences = FakeSharedPreferences()
    override val isInvocationEffectEnabledByAssistant = MutableStateFlow(true)

    private fun addToPref(f: SharedPreferences.Editor.() -> Unit) {
        fakeSharedPreferences.edit { f() }
    }

    override fun isInvocationEffectEnabledInPreferences(): Boolean {
        return fakeSharedPreferences.getBoolean(
            IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT,
            DEFAULT_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT_PREFERENCE,
        )
    }

    fun clear() {
        fakeSharedPreferences = FakeSharedPreferences()
    }

    fun getSavedAssistant(): String {
        return fakeSharedPreferences.getString(PERSISTED_FOR_ASSISTANT_PREFERENCE, "") ?: ""
    }

    fun getSavedUserId(): Int {
        return fakeSharedPreferences.getInt(PERSISTED_FOR_USER_PREFERENCE, Int.MIN_VALUE)
    }

    override fun getInwardAnimationPaddingDurationMillis(): Long {
        return fakeSharedPreferences.getLong(
            INVOCATION_EFFECT_ANIMATION_IN_DURATION_PADDING_MS,
            DEFAULT_INWARD_EFFECT_PADDING_DURATION_MS,
        )
    }

    override fun getOutwardAnimationDurationMillis(): Long {
        return fakeSharedPreferences.getLong(
            INVOCATION_EFFECT_ANIMATION_OUT_DURATION_MS,
            DEFAULT_OUTWARD_EFFECT_DURATION_MS,
        )
    }

    override fun isCurrentUserAndAssistantPersisted(): Boolean {
        return activeUserId == getSavedUserId() && activeAssistant == getSavedAssistant()
    }

    override fun setInvocationEffectConfig(
        config: InvocationEffectPreferences.Config,
        saveActiveUserAndAssistant: Boolean,
    ) {
        if (saveActiveUserAndAssistant) {
            addToPref {
                putString(PERSISTED_FOR_ASSISTANT_PREFERENCE, activeAssistant)
                putInt(PERSISTED_FOR_USER_PREFERENCE, activeUserId)
            }
        }
        addToPref {
            putLong(INVOCATION_EFFECT_ANIMATION_OUT_DURATION_MS, config.outwardsEffectDuration)
            putLong(
                INVOCATION_EFFECT_ANIMATION_IN_DURATION_PADDING_MS,
                config.inwardsEffectDurationPadding,
            )
            putBoolean(IS_INVOCATION_EFFECT_ENABLED_BY_ASSISTANT, config.isEnabled)
        }
    }

    override fun registerOnChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        fakeSharedPreferences.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun unregisterOnChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener
    ) {
        fakeSharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    override fun dump(pw: PrintWriter, args: Array<out String>) {
        // empty
    }

    fun setInvocationEffectEnabledByAssistant(enabled: Boolean) {
        isInvocationEffectEnabledByAssistant.value = enabled
    }
}

private class FakeSharedPreferences : SharedPreferences {

    private var map = mutableMapOf<String, String>()
    private val editor = Editor(map)

    override fun getAll(): MutableMap<String, *> {
        return map
    }

    override fun getString(key: String?, defValue: String?): String? {
        return map.getOrDefault(key, defValue)
    }

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
        return defValues
    }

    override fun getInt(key: String?, defValue: Int): Int {
        return map[key]?.let { Integer.parseInt(it) } ?: defValue
    }

    override fun getLong(key: String?, defValue: Long): Long {
        return map[key]?.let { Integer.parseInt(it).toLong() } ?: defValue
    }

    override fun getFloat(key: String?, defValue: Float): Float {
        return map[key]?.let { Integer.parseInt(it).toFloat() } ?: defValue
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return map[key]?.let { Integer.parseInt(it) == 1 } ?: defValue
    }

    override fun contains(key: String?): Boolean {
        return map.containsKey(key)
    }

    override fun edit(): SharedPreferences.Editor {
        return editor
    }

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        // empty
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) {
        // empty
    }

    private class Editor(private var map: MutableMap<String, String>) : SharedPreferences.Editor {
        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            map[key!!] = value!!
            return this
        }

        override fun putStringSet(
            key: String?,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor {
            // empty
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            map[key!!] = value.toString()
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            map[key!!] = value.toString()
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            map[key!!] = value.toString()
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            map[key!!] = if (value) "1" else "0"
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            map.remove(key!!)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            map = mutableMapOf()
            return this
        }

        override fun commit(): Boolean {
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
