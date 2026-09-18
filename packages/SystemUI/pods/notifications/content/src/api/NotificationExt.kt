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

package com.android.systemui.notifications.content

import android.app.Notification
import android.app.Notification.BigPictureStyle
import android.app.Notification.BigTextStyle
import android.app.Notification.CallStyle
import android.app.Notification.EXTRA_BIG_TEXT
import android.app.Notification.EXTRA_CALL_PERSON
import android.app.Notification.EXTRA_CHRONOMETER_COUNT_DOWN
import android.app.Notification.EXTRA_PREFER_SMALL_ICON
import android.app.Notification.EXTRA_SUB_TEXT
import android.app.Notification.EXTRA_TEXT
import android.app.Notification.EXTRA_TITLE
import android.app.Notification.EXTRA_TITLE_BIG
import android.app.Notification.EXTRA_VERIFICATION_ICON
import android.app.Notification.EXTRA_VERIFICATION_TEXT
import android.app.Notification.InboxStyle
import android.app.Notification.MetricStyle
import android.app.Person
import android.graphics.drawable.Icon

private fun Notification.titleExtra(): CharSequence? = getCharSequenceExtraUnlessEmpty(EXTRA_TITLE)

private fun Notification.bigTitleExtra(): CharSequence? =
    getCharSequenceExtraUnlessEmpty(EXTRA_TITLE_BIG)

private fun Notification.callPersonExtra(): Person? =
    extras?.getParcelable(EXTRA_CALL_PERSON, Person::class.java)

public fun Notification.title(expanded: Boolean): CharSequence? {
    // bigTitle is only used in the expanded form of 3 styles.
    return when (notificationStyle) {
        BigTextStyle::class.java,
        BigPictureStyle::class.java,
        InboxStyle::class.java -> if (expanded) bigTitleExtra() else null
        CallStyle::class.java -> callPersonExtra()?.name?.takeUnlessEmpty()
        else -> null
    } ?: titleExtra()
}

private fun Notification.textExtra(): CharSequence? = getCharSequenceExtraUnlessEmpty(EXTRA_TEXT)

private fun Notification.bigTextExtra(): CharSequence? =
    getCharSequenceExtraUnlessEmpty(EXTRA_BIG_TEXT)

public fun Notification.text(expanded: Boolean): CharSequence? {
    val styleClass = notificationStyle
    if (styleClass == MetricStyle::class.java) return null
    return when (styleClass) {
        BigTextStyle::class.java -> if (expanded) bigTextExtra() else null
        else -> null
    } ?: textExtra()
}

public fun Notification.subText(): CharSequence? = getCharSequenceExtraUnlessEmpty(EXTRA_SUB_TEXT)

public fun Notification.verificationText(): CharSequence? =
    extras?.getCharSequence(EXTRA_VERIFICATION_TEXT)?.takeIf {
        it.isNotEmpty() && notificationStyle == CallStyle::class.java
    }

public fun Notification.verificationIcon(): Icon? =
    extras.getParcelable(EXTRA_VERIFICATION_ICON, Icon::class.java)?.takeIf {
        notificationStyle == CallStyle::class.java
    }

public fun Notification.chronometerCountDown(): Boolean =
    extras?.getBoolean(EXTRA_CHRONOMETER_COUNT_DOWN) ?: false

public fun Notification.preferSmallIcon(): Boolean =
    extras?.getBoolean(EXTRA_PREFER_SMALL_ICON) ?: false

private fun Notification.getCharSequenceExtraUnlessEmpty(key: String): CharSequence? =
    extras?.getCharSequence(key)?.takeUnlessEmpty()

private fun <T : CharSequence> T.takeUnlessEmpty(): T? = takeUnless { it.isEmpty() }
