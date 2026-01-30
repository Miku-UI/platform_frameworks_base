/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settingslib.spa.framework.util

import android.content.ComponentName
import android.content.Intent
import androidx.annotation.VisibleForTesting
import com.android.settingslib.spa.framework.common.SettingsEntry
import com.android.settingslib.spa.framework.common.SettingsPage
import com.android.settingslib.spa.framework.common.SpaEnvironmentFactory

const val SESSION_UNKNOWN = "unknown"
const val SESSION_BROWSE = "browse"
const val SESSION_SEARCH = "search"
const val SESSION_EXTERNAL = "external"

const val KEY_DESTINATION = "spaActivityDestination"
@VisibleForTesting const val KEY_HIGHLIGHT_ITEM_KEY = "highlightKey"
internal const val KEY_SESSION_SOURCE_NAME = "sessionSource"

private fun createBaseIntent(): Intent? {
    val context = SpaEnvironmentFactory.instance.appContext
    val browseActivityClass = SpaEnvironmentFactory.instance.browseActivityClass ?: return null
    return Intent().setComponent(ComponentName(context, browseActivityClass))
}

internal fun SettingsPage.createIntent(sessionName: String? = null): Intent? {
    if (!isBrowsable()) return null
    return createBaseIntent()?.appendSpaParams(
        destination = buildRoute(),
        sessionName = sessionName
    )
}

internal fun SettingsEntry.createIntent(sessionName: String? = null): Intent? =
    containerPage().createIntent(sessionName)

fun Intent.appendSpaParams(
    destination: String? = null,
    highlightItemKey: String? = null,
    sessionName: String? = null,
): Intent = apply {
    if (destination != null) putExtra(KEY_DESTINATION, destination)
    if (highlightItemKey != null) putExtra(KEY_HIGHLIGHT_ITEM_KEY, highlightItemKey)
    if (sessionName != null) putExtra(KEY_SESSION_SOURCE_NAME, sessionName)
}

internal fun Intent.getDestination(): String? = getStringExtra(KEY_DESTINATION)

internal val Intent.highlightItemKey: String?
    get() = getStringExtra(KEY_HIGHLIGHT_ITEM_KEY)

internal fun Intent.getSessionName(): String? = getStringExtra(KEY_SESSION_SOURCE_NAME)
