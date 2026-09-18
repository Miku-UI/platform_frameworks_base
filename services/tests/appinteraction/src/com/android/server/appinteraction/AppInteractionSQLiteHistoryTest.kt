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

package com.android.server.appinteraction

import android.app.AppInteractionAttribution
import android.app.AppInteractionContract
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppInteractionSQLiteHistoryTest {
    private lateinit var context: Context
    private lateinit var interactionHistory: AppInteractionSQLiteHistory

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        interactionHistory = AppInteractionSQLiteHistory(context, "appinteraction.db")
    }

    @After
    fun tearDown() {
        interactionHistory.deleteAll()
        interactionHistory.close()
    }

    @Test
    fun queryAppInteractionHistoryShouldReturnInsertedHistoriesWithoutAttribution() {
        val sourcePackage = TEST_AGENT_PACKAGE_NAME
        val targetPackage = TEST_TARGET_PACKAGE_NAME
        val accessTime = System.currentTimeMillis()

        val rowId =
            interactionHistory.insertAppInteractionHistory(
                sourcePackage,
                targetPackage,
                /* appInteractionAttribution= */ null,
                accessTime,
            )

        assertThat(rowId).isNotEqualTo(-1)
        interactionHistory.queryAppInteractionHistories(null, null, null, null)?.use { cursor ->
            assertThat(cursor.count).isEqualTo(1)
            cursor.moveToFirst()

            assertThat(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            AppInteractionContract.COLUMN_AGENT_PACKAGE_NAME
                        )
                    )
                )
                .isEqualTo(TEST_AGENT_PACKAGE_NAME)
            assertThat(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            AppInteractionContract.COLUMN_TARGET_PACKAGE_NAME
                        )
                    )
                )
                .isEqualTo(TEST_TARGET_PACKAGE_NAME)
            assertThat(
                    cursor.isNull(
                        cursor.getColumnIndexOrThrow(AppInteractionContract.COLUMN_INTERACTION_TYPE)
                    )
                )
                .isTrue()
            assertThat(
                    cursor.isNull(
                        cursor.getColumnIndexOrThrow(
                            AppInteractionContract.COLUMN_CUSTOM_INTERACTION_TYPE
                        )
                    )
                )
                .isTrue()
            assertThat(
                    cursor.isNull(
                        cursor.getColumnIndexOrThrow(AppInteractionContract.COLUMN_INTERACTION_URI)
                    )
                )
                .isTrue()
            assertThat(
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(AppInteractionContract.COLUMN_ACCESS_TIME)
                    )
                )
                .isEqualTo(accessTime)
        }
    }

    @Test
    fun queryAppInteractionHistoryShouldReturnInsertedHistoriesWithMinimalAttribution() {
        val sourcePackage = TEST_AGENT_PACKAGE_NAME
        val targetPackage = TEST_TARGET_PACKAGE_NAME
        val attribution =
            AppInteractionAttribution.Builder(AppInteractionAttribution.INTERACTION_TYPE_USER_QUERY)
                .build()
        val accessTime = System.currentTimeMillis()

        val rowId =
            interactionHistory.insertAppInteractionHistory(
                sourcePackage,
                targetPackage,
                attribution,
                accessTime,
            )

        assertThat(rowId).isNotEqualTo(-1)
        interactionHistory.queryAppInteractionHistories(null, null, null, null)?.use { cursor ->
            assertThat(cursor.count).isEqualTo(1)
            cursor.moveToFirst()

            assertThat(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            AppInteractionContract.COLUMN_AGENT_PACKAGE_NAME
                        )
                    )
                )
                .isEqualTo(TEST_AGENT_PACKAGE_NAME)

            assertThat(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            AppInteractionContract.COLUMN_TARGET_PACKAGE_NAME
                        )
                    )
                )
                .isEqualTo(TEST_TARGET_PACKAGE_NAME)
            assertThat(
                    cursor.getInt(
                        cursor.getColumnIndexOrThrow(AppInteractionContract.COLUMN_INTERACTION_TYPE)
                    )
                )
                .isEqualTo(AppInteractionAttribution.INTERACTION_TYPE_USER_QUERY)
            assertThat(
                    cursor.isNull(
                        cursor.getColumnIndexOrThrow(
                            AppInteractionContract.COLUMN_CUSTOM_INTERACTION_TYPE
                        )
                    )
                )
                .isTrue()
            assertThat(
                    cursor.isNull(
                        cursor.getColumnIndexOrThrow(AppInteractionContract.COLUMN_INTERACTION_URI)
                    )
                )
                .isTrue()
            assertThat(
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(AppInteractionContract.COLUMN_ACCESS_TIME)
                    )
                )
                .isEqualTo(accessTime)
        }
    }

    @Test
    fun queryAppInteractionHistoryShouldReturnInsertedHistoriesWithFullAttribution() {
        val sourcePackage = TEST_AGENT_PACKAGE_NAME
        val targetPackage = TEST_TARGET_PACKAGE_NAME
        val attribution =
            AppInteractionAttribution.Builder(AppInteractionAttribution.INTERACTION_TYPE_OTHER)
                .setCustomInteractionType(TEST_CUSTOM_INTERACTION_TYPE)
                .setInteractionUri(TEST_INTERACTION_URI)
                .build()
        val accessTime = System.currentTimeMillis()

        val rowId =
            interactionHistory.insertAppInteractionHistory(
                sourcePackage,
                targetPackage,
                attribution,
                accessTime,
            )

        assertThat(rowId).isNotEqualTo(-1)
        interactionHistory.queryAppInteractionHistories(null, null, null, null)?.use { cursor ->
            assertThat(cursor.count).isEqualTo(1)
            cursor.moveToFirst()

            assertThat(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            AppInteractionContract.COLUMN_AGENT_PACKAGE_NAME
                        )
                    )
                )
                .isEqualTo(TEST_AGENT_PACKAGE_NAME)
            assertThat(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            AppInteractionContract.COLUMN_TARGET_PACKAGE_NAME
                        )
                    )
                )
                .isEqualTo(TEST_TARGET_PACKAGE_NAME)
            assertThat(
                    cursor.getInt(
                        cursor.getColumnIndexOrThrow(AppInteractionContract.COLUMN_INTERACTION_TYPE)
                    )
                )
                .isEqualTo(AppInteractionAttribution.INTERACTION_TYPE_OTHER)
            assertThat(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            AppInteractionContract.COLUMN_CUSTOM_INTERACTION_TYPE
                        )
                    )
                )
                .isEqualTo(TEST_CUSTOM_INTERACTION_TYPE)
            assertThat(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(AppInteractionContract.COLUMN_INTERACTION_URI)
                    )
                )
                .isEqualTo(TEST_INTERACTION_URI.toString())
            assertThat(
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(AppInteractionContract.COLUMN_ACCESS_TIME)
                    )
                )
                .isEqualTo(accessTime)
        }
    }

    @Test
    fun deleteExpiredAppInteractionHistoriesShouldClearExpiredHistories() {
        val sourcePackage = TEST_AGENT_PACKAGE_NAME
        val targetPackage = TEST_TARGET_PACKAGE_NAME
        val retentionPeriodMillis = 2000L
        val oldAccessTime = System.currentTimeMillis() - retentionPeriodMillis - 500
        val newAccessTime = System.currentTimeMillis()
        interactionHistory.insertAppInteractionHistory(
            sourcePackage,
            targetPackage,
            /* appInteractionAttribution= */ null,
            oldAccessTime,
        )
        interactionHistory.insertAppInteractionHistory(
            sourcePackage,
            targetPackage,
            /* appInteractionAttribution= */ null,
            newAccessTime,
        )

        interactionHistory.deleteExpiredAppInteractionHistories(retentionPeriodMillis)

        interactionHistory.queryAppInteractionHistories(null, null, null, null)?.use { cursor ->
            assertThat(cursor.count).isEqualTo(1)
            cursor.moveToFirst()
            assertThat(
                    cursor.getLong(
                        cursor.getColumnIndexOrThrow(AppInteractionContract.COLUMN_ACCESS_TIME)
                    )
                )
                .isEqualTo(newAccessTime)
        }
    }

    @Test
    fun deleteAppInteractionHistoriesShouldClearAllHistoryAssociatedWithThePackage() {
        val sourcePackage = TEST_AGENT_PACKAGE_NAME
        val targetPackage = TEST_TARGET_PACKAGE_NAME
        val accessTime = System.currentTimeMillis()
        val otherPackageName = "com.android.test.other"
        interactionHistory.insertAppInteractionHistory(
            sourcePackage,
            targetPackage,
            /* appInteractionAttribution= */ null,
            accessTime,
        )
        // Swap the target/source package
        interactionHistory.insertAppInteractionHistory(
            targetPackage,
            sourcePackage,
            /* appInteractionAttribution= */ null,
            accessTime,
        )
        // Use otherPackageName as target package
        interactionHistory.insertAppInteractionHistory(
            targetPackage,
            otherPackageName,
            /* appInteractionAttribution= */ null,
            accessTime,
        )

        interactionHistory.deleteAppInteractionHistories(TEST_AGENT_PACKAGE_NAME)

        interactionHistory.queryAppInteractionHistories(null, null, null, null)?.use { cursor ->
            assertThat(cursor.count).isEqualTo(1)
            cursor.moveToFirst()
            assertThat(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            AppInteractionContract.COLUMN_AGENT_PACKAGE_NAME
                        )
                    )
                )
                .isEqualTo(TEST_TARGET_PACKAGE_NAME)
            assertThat(
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            AppInteractionContract.COLUMN_TARGET_PACKAGE_NAME
                        )
                    )
                )
                .isEqualTo(otherPackageName)
        }
    }

    companion object {
        private const val TEST_AGENT_PACKAGE_NAME = "com.android.test.agent"
        private const val TEST_TARGET_PACKAGE_NAME = "com.android.test.target"
        private const val TEST_CUSTOM_INTERACTION_TYPE = "MAINTENANCE"
        private val TEST_INTERACTION_URI: Uri = "content://test/interaction".toUri()
    }
}
