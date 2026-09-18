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

package com.android.systemui.notifications.intelligence.rules.shared.model

import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.net.Uri

/** Represents a person, like a contact or someone the user has had a conversation with. */
sealed interface PersonModel {
    /** A unique identifier for the person. */
    val id: String
    /** The display label for the person. */
    val displayLabel: String

    /** A model for a contact entry. */
    data class Contact(
        /** A URI associated with this contact. Can be used as a unique identifier. */
        val lookupUri: Uri,
        /** The display name for the contact. */
        val name: String,
        /** The URI for fetching the photo of the contact. */
        val photoUri: Uri?,
    ) : PersonModel {
        override val displayLabel: String = name
        override val id: String = lookupUri.toString()
    }

    /** A model for long-lived conversation with a person in a particular app. */
    data class ConversationPartner(
        override val id: String,
        override val displayLabel: String,
        /** The main icon representing this conversation partner. */
        val avatarIcon: Icon,
        /** The icon representing which app this conversation is in. */
        val appBadgeIcon: Drawable?,
    ) : PersonModel
}

/**
 * Represents the list of people inside a rule filter.
 *
 * @param people must be non-empty.
 */
data class PeopleModel(val people: List<PersonModel>) {
    init {
        require(people.isNotEmpty()) { "People list cannot be empty" }
    }
}
