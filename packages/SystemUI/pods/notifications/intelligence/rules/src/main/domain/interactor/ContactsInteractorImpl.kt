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

package com.android.systemui.notifications.intelligence.rules.domain.interactor

import android.annotation.Px
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.graphics.ImageLoader
import com.android.systemui.notifications.intelligence.rules.data.repository.ContactsRepository
import com.android.systemui.notifications.intelligence.rules.shared.model.PersonModel
import javax.inject.Inject

@SysUISingleton
class ContactsInteractorImpl
@Inject
constructor(
    private val contactsRepository: ContactsRepository,
    private val imageLoader: ImageLoader,
) : ContactsInteractor {
    override suspend fun fetchContacts(
        searchQuery: String,
        contentResolver: ContentResolver,
    ): List<PersonModel.Contact> {
        return contactsRepository.fetchContacts(searchQuery, contentResolver)
    }

    override suspend fun loadBitmapFromUri(
        uri: Uri,
        userContext: Context,
        @Px sizePx: Int,
    ): Bitmap? {
        val source = ImageLoader.Uri(uri, userContext)
        return imageLoader.loadBitmap(source, maxWidth = sizePx, maxHeight = sizePx)
    }
}
