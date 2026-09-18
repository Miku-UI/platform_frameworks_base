/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.app.appfunctions

import android.app.appfunctions.flags.Flags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DYNAMIC_APP_FUNCTIONS)
@RunWith(JUnit4::class)
class AppFunctionSearchSpecTest {
    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    fun testGetStaticMetadataAppSearchQuery() {
        assertThat(SEARCH_SPEC_WITH_ALL_PROPERTIES.getStaticMetadataAppSearchQuery())
            .isEqualTo(
                "packageName:(\"testPackage1\" OR \"testPackage2\") " +
                    "schemaCategory:\"testCategory\" " +
                    "schemaName:\"testName\" " +
                    "schemaVersion>=1 " +
                    "scope:(\"activity\" OR \"global\")"
            )
    }

    @Test
    fun testGetStaticMetadataAppSearchQuery_nullPackageNames_skipsPackageNamesFilter() {
        assertThat(SEARCH_SPEC_WITHOUT_PACKAGE_NAMES.getStaticMetadataAppSearchQuery())
            .isEqualTo(
                "schemaCategory:\"testCategory\" " + "schemaName:\"testName\" " + "schemaVersion>=1"
            )
    }

    @Test
    fun getStaticMetadataAppSearchQuery_withAllProperties_generatesFullQuery() {
        val query = SEARCH_SPEC_WITH_ALL_PROPERTIES.getStaticMetadataAppSearchQuery()

        assertThat(query)
            .isEqualTo(
                "packageName:(\"testPackage1\" OR \"testPackage2\") " +
                    "schemaCategory:\"testCategory\" " +
                    "schemaName:\"testName\" " +
                    "schemaVersion>=1 " +
                    "scope:(\"activity\" OR \"global\")"
            )
    }

    @Test
    fun getStaticMetadataAppSearchQuery_withoutPackageNames_skipsPackageFilter() {
        val query = SEARCH_SPEC_WITHOUT_PACKAGE_NAMES.getStaticMetadataAppSearchQuery()

        assertThat(query)
            .isEqualTo(
                "schemaCategory:\"testCategory\" " + "schemaName:\"testName\" " + "schemaVersion>=1"
            )
    }

    @Test
    fun getStaticMetadataAppSearchQuery_withVersionZero_skipsVersionFilter() {
        val specWithVersionZero =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(setOf("testPackage1"))
                .setMinSchemaVersion(0L)
                .build()

        val query = specWithVersionZero.getStaticMetadataAppSearchQuery()

        assertThat(query).isEqualTo("packageName:(\"testPackage1\")")
    }

    @Test
    fun getQualifiedIdsFilter_logic() {
        assertThat(
                AppFunctionSearchSpecTest.Companion.SEARCH_SPEC_WITH_ALL_PROPERTIES
                    .getQualifiedIdsFilter()
            )
            .containsExactly("testPackage1/id1", "testPackage2/id2")

        assertThat(SEARCH_SPEC_WITHOUT_FUNCTION_NAMES.getQualifiedIdsFilter()).isEmpty()
    }

    @Test
    fun observedPackageNames_intersectsPackageAndFunctionFilters() {
        val spec =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(setOf("testPackage1"))
                .setFunctionNames(
                    setOf(
                        AppFunctionName("testPackage1", "id1"),
                        AppFunctionName("testPackage2", "id2"),
                    )
                )
                .build()

        assertThat(spec.observedPackageNames).containsExactly("testPackage1")
    }

    @Test
    fun observedAppFunctions_noFunctionFilter_returnsNull() {
        val spec =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(setOf("testPackage1"))
                .setFunctionNames(
                    setOf(
                        AppFunctionName("testPackage1", "id1"),
                        AppFunctionName("testPackage2", "id2"),
                    )
                )
                .build()

        assertThat(spec.observedAppFunctions)
            .containsExactly(AppFunctionName("testPackage1", "id1"))

        assertThat(SEARCH_SPEC_WITHOUT_FUNCTION_NAMES.observedAppFunctions).isNull()
    }

    companion object {
        val SEARCH_SPEC_WITH_ALL_PROPERTIES =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(setOf("testPackage1", "testPackage2"))
                .setFunctionNames(
                    setOf(
                        AppFunctionName("testPackage1", "id1"),
                        AppFunctionName("testPackage2", "id2"),
                    )
                )
                .setSchemaCategory("testCategory")
                .setSchemaName("testName")
                .setMinSchemaVersion(1L)
                .setScopes(
                    setOf(AppFunctionMetadata.SCOPE_GLOBAL, AppFunctionMetadata.SCOPE_ACTIVITY)
                )
                .build()

        val SEARCH_SPEC_WITHOUT_PACKAGE_NAMES =
            AppFunctionSearchSpec.Builder()
                .setFunctionNames(
                    setOf(
                        AppFunctionName("testPackage1", "id1"),
                        AppFunctionName("testPackage2", "id2"),
                    )
                )
                .setSchemaCategory("testCategory")
                .setSchemaName("testName")
                .setMinSchemaVersion(1L)
                .build()

        val SEARCH_SPEC_WITHOUT_FUNCTION_NAMES =
            AppFunctionSearchSpec.Builder()
                .setPackageNames(setOf("testPackage1", "testPackage2"))
                .setSchemaCategory("testCategory")
                .setSchemaName("testName")
                .setMinSchemaVersion(1L)
                .build()
    }
}
