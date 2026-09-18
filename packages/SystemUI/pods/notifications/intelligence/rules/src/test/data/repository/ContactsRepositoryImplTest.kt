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

package com.android.systemui.notifications.intelligence.rules.data.repository

import android.content.ContentResolver
import android.content.mockContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.notifications.intelligence.rules.shared.notificationRulesLogBuffer
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import java.sql.SQLException
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class ContactsRepositoryImplTest : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    private val Kosmos.underTest by
        Kosmos.Fixture { ContactsRepositoryImpl(kosmos.testDispatcher, notificationRulesLogBuffer) }

    @Test
    fun lookupContact_nullCursor_returnsNull() =
        kosmos.runTest {
            whenever(mockContentResolver.query(any(), any(), any(), any(), eq(null)))
                .thenReturn(null)

            val result = underTest.lookupContact(LOOKUP_URI, mockContentResolver)

            assertThat(result).isNull()
        }

    @Test
    fun lookupContact_exception_returnsNull() =
        kosmos.runTest {
            doAnswer { _: InvocationOnMock -> throw SQLException() }
                .whenever(mockContentResolver)
                .query(any(), any(), any(), any(), eq(null))

            val result = underTest.lookupContact(LOOKUP_URI, mockContentResolver)

            assertThat(result).isNull()
        }

    @Test
    fun lookupContact_emptyCursor_returnsNull_andCursorClosed() =
        kosmos.runTest {
            val cursor = mock<Cursor>()
            whenever(cursor.moveToNext()).thenReturn(false)
            setUpCursorForSingleLookup(
                lookupUri = LOOKUP_URI,
                mockContentResolver = mockContentResolver,
                cursor = cursor,
            )

            val result = underTest.lookupContact(LOOKUP_URI, mockContentResolver)

            assertThat(result).isNull()
            verify(cursor).close()
        }

    @Test
    fun lookupContact_contactMissingId_returnsNull_andCursorClosed() =
        kosmos.runTest {
            val cursor =
                createCursorWithEntries(
                    entries =
                        listOf(FakeContactEntry(id = null, lookupKey = "lookup", name = "Sys UI"))
                )
            setUpCursorForSingleLookup(
                lookupUri = LOOKUP_URI,
                mockContentResolver = mockContentResolver,
                cursor = cursor,
            )

            val result = underTest.lookupContact(LOOKUP_URI, mockContentResolver)

            assertThat(result).isNull()
            verify(cursor).close()
        }

    @Test
    fun lookupContact_contactMissingLookupKey_returnsNull_andCursorClosed() =
        kosmos.runTest {
            val cursor =
                createCursorWithEntries(
                    entries = listOf(FakeContactEntry(id = 3, lookupKey = null, name = "Sys UI"))
                )
            setUpCursorForSingleLookup(
                lookupUri = LOOKUP_URI,
                mockContentResolver = mockContentResolver,
                cursor = cursor,
            )

            val result = underTest.lookupContact(LOOKUP_URI, mockContentResolver)

            assertThat(result).isNull()
            verify(cursor).close()
        }

    @Test
    fun lookupContact_contactMissingName_returnsNull_andCursorClosed() =
        kosmos.runTest {
            val cursor =
                createCursorWithEntries(
                    entries = listOf(FakeContactEntry(id = 4, lookupKey = "fake", name = null))
                )
            setUpCursorForSingleLookup(
                lookupUri = LOOKUP_URI,
                mockContentResolver = mockContentResolver,
                cursor = cursor,
            )

            val result = underTest.lookupContact(LOOKUP_URI, mockContentResolver)

            assertThat(result).isNull()
            verify(cursor).close()
        }

    @Test
    fun lookupContact_contactMissingPhotoUri_returnsContact_andCursorClosed() =
        kosmos.runTest {
            val cursor =
                createCursorWithEntries(
                    entries =
                        listOf(
                            FakeContactEntry(
                                id = 5,
                                lookupKey = "fake",
                                name = "name",
                                photoUri = null,
                            )
                        )
                )
            setUpCursorForSingleLookup(
                lookupUri = LOOKUP_URI,
                mockContentResolver = mockContentResolver,
                cursor = cursor,
            )

            val result = underTest.lookupContact(LOOKUP_URI, mockContentResolver)

            assertThat(result).isNotNull()
            assertThat(result!!.name).isEqualTo("name")
            verify(cursor).close()
        }

    @Test
    fun lookupContact_multipleContactsInCursor_returnsFirstContact_andCursorClosed() =
        kosmos.runTest {
            val cursor =
                createCursorWithEntries(
                    entries =
                        listOf(
                            FakeContactEntry(id = 1, name = "Sys UI"),
                            FakeContactEntry(id = 2, name = "Frameworks Base"),
                        )
                )
            setUpCursorForSingleLookup(
                lookupUri = LOOKUP_URI,
                mockContentResolver = mockContentResolver,
                cursor = cursor,
            )

            val result = underTest.lookupContact(LOOKUP_URI, mockContentResolver)

            assertThat(result).isNotNull()
            assertThat(result!!.name).isEqualTo("Sys UI")
            verify(cursor).close()
        }

    @Test
    fun fetchContacts_nullCursor_returnsEmptyList() =
        kosmos.runTest {
            whenever(mockContentResolver.query(any(), any(), any(), any(), eq(null)))
                .thenReturn(null)

            val result = underTest.fetchContacts(searchQuery = "s", mockContentResolver)

            assertThat(result).isEmpty()
        }

    @Test
    fun fetchContacts_exception_returnsEmptyList() =
        kosmos.runTest {
            doAnswer { _: InvocationOnMock -> throw SQLException() }
                .whenever(mockContentResolver)
                .query(any(), any(), any(), any(), eq(null))

            val result = underTest.fetchContacts(searchQuery = "s", mockContentResolver)

            assertThat(result).isEmpty()
        }

    @Test
    fun fetchContacts_emptyCursor_returnsEmptyList_andCursorClosed() =
        kosmos.runTest {
            val cursor = mock<Cursor>()
            whenever(cursor.moveToNext()).thenReturn(false)
            setUpCursorForFetchAll(mockContentResolver = mockContentResolver, cursor = cursor)

            val result = underTest.fetchContacts(searchQuery = "s", mockContentResolver)

            assertThat(result).isEmpty()
            verify(cursor).close()
        }

    @Test
    fun fetchContacts_contactMissingId_returnsEmptyList_andCursorClosed() =
        kosmos.runTest {
            val cursor =
                createCursorWithEntries(
                    entries =
                        listOf(FakeContactEntry(id = null, lookupKey = "lookup", name = "Sys UI"))
                )
            setUpCursorForFetchAll(mockContentResolver = mockContentResolver, cursor = cursor)

            val result = underTest.fetchContacts(searchQuery = "s", mockContentResolver)

            assertThat(result).isEmpty()
            verify(cursor).close()
        }

    @Test
    fun fetchContacts_contactMissingLookupKey_returnsEmptyList_andCursorClosed() =
        kosmos.runTest {
            val cursor =
                createCursorWithEntries(
                    entries = listOf(FakeContactEntry(id = 3, lookupKey = null, name = "Sys UI"))
                )
            setUpCursorForFetchAll(mockContentResolver = mockContentResolver, cursor = cursor)

            val result = underTest.fetchContacts(searchQuery = "s", mockContentResolver)

            assertThat(result).isEmpty()
            verify(cursor).close()
        }

    @Test
    fun fetchContacts_contactMissingName_returnsEmptyList_andCursorClosed() =
        kosmos.runTest {
            val cursor =
                createCursorWithEntries(
                    entries = listOf(FakeContactEntry(id = 4, lookupKey = "fake", name = null))
                )
            setUpCursorForFetchAll(mockContentResolver = mockContentResolver, cursor = cursor)

            val result = underTest.fetchContacts(searchQuery = "s", mockContentResolver)

            assertThat(result).isEmpty()
            verify(cursor).close()
        }

    @Test
    fun fetchContacts_contactMissingPhotoUri_returnsContact_andCursorClosed() =
        kosmos.runTest {
            val cursor =
                createCursorWithEntries(
                    entries =
                        listOf(
                            FakeContactEntry(
                                id = 5,
                                lookupKey = "fake",
                                name = "name",
                                photoUri = null,
                            )
                        )
                )
            setUpCursorForFetchAll(mockContentResolver = mockContentResolver, cursor = cursor)

            val result = underTest.fetchContacts(searchQuery = "s", mockContentResolver)

            assertThat(result).hasSize(1)
            verify(cursor).close()
        }

    @Test
    fun fetchContacts_oneContact_withAllInfo_returnsContactWithInfo_andCursorClosed() =
        kosmos.runTest {
            val cursor =
                createCursorWithEntries(
                    entries = listOf(FakeContactEntry(name = "Sys UI", photoUri = "fakeUri"))
                )
            setUpCursorForFetchAll(mockContentResolver = mockContentResolver, cursor = cursor)

            val result = underTest.fetchContacts(searchQuery = "s", mockContentResolver)

            assertThat(result).hasSize(1)
            assertThat(result.first().name).isEqualTo("Sys UI")
            assertThat(result.first().photoUri).isEqualTo("fakeUri".toUri())
            verify(cursor).close()
        }

    @Test
    fun fetchContacts_multipleContacts_returnsAll_andCursorClosed() =
        kosmos.runTest {
            val cursor =
                createCursorWithEntries(
                    entries =
                        listOf(
                            FakeContactEntry(id = 1, name = "Sys UI"),
                            FakeContactEntry(id = 2, name = "Frameworks Base"),
                        )
                )
            setUpCursorForFetchAll(mockContentResolver = mockContentResolver, cursor = cursor)

            val result = underTest.fetchContacts(searchQuery = "s", mockContentResolver)

            assertThat(result).hasSize(2)
            assertThat(result.map { it.name }).containsExactly("Sys UI", "Frameworks Base")
            verify(cursor).close()
        }

    private fun createCursorWithEntries(entries: List<FakeContactEntry>): Cursor {
        val cursor = mock<Cursor>()
        var numEntriesFetched = 0

        whenever(cursor.moveToNext()).thenReturn(numEntriesFetched < entries.size)

        // ID
        whenever(cursor.getColumnIndex(ContactsContract.Contacts._ID)).thenReturn(0)
        doAnswer { _: InvocationOnMock -> entries[numEntriesFetched].id?.toString() }
            .whenever(cursor)
            .getString(0)

        // Lookup key
        whenever(cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)).thenReturn(1)
        doAnswer { _: InvocationOnMock -> entries[numEntriesFetched].lookupKey }
            .whenever(cursor)
            .getString(1)

        // Name
        whenever(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))
            .thenReturn(2)
        doAnswer { _: InvocationOnMock -> entries[numEntriesFetched].name }
            .whenever(cursor)
            .getString(2)

        // URI
        whenever(cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)).thenReturn(3)
        doAnswer { _: InvocationOnMock ->
                val returnValue = entries[numEntriesFetched].photoUri
                // Only increment the value on the last lookup
                numEntriesFetched++
                returnValue
            }
            .whenever(cursor)
            .getString(3)

        return cursor
    }

    private fun setUpCursorForSingleLookup(
        lookupUri: Uri,
        mockContentResolver: ContentResolver,
        cursor: Cursor,
    ) {
        whenever(mockContentResolver.query(eq(lookupUri), any(), eq(null), eq(null), eq(null)))
            .thenReturn(cursor)
    }

    private fun setUpCursorForFetchAll(mockContentResolver: ContentResolver, cursor: Cursor) {
        whenever(mockContentResolver.query(any(), any(), any(), any(), eq(null))).thenReturn(cursor)
    }

    private data class FakeContactEntry(
        val id: Int? = 35,
        val name: String?,
        val lookupKey: String? = "fakeLookupKey",
        val photoUri: String? = null,
    )

    companion object {
        private val LOOKUP_URI = "lookupUri".toUri()
    }
}
