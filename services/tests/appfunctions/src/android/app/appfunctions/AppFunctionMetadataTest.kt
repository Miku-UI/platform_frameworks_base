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

package android.app.appfunctions

import android.app.appfunctions.AppFunctionMetadata.PROPERTY_SCHEMA_CATEGORY
import android.app.appfunctions.AppFunctionMetadata.PROPERTY_SCHEMA_NAME
import android.app.appfunctions.AppFunctionMetadata.PROPERTY_SCHEMA_VERSION
import android.app.appfunctions.AppFunctionMetadata.PROPERTY_SCOPE
import android.app.appfunctions.AppFunctionMetadata.PROPERTY_VALUE_SCOPE_ACTIVITY
import android.app.appfunctions.AppFunctionMetadata.PROPERTY_VALUE_SCOPE_GLOBAL
import android.app.appfunctions.AppFunctionMetadata.SCOPE_GLOBAL
import android.app.appfunctions.flags.Flags
import android.app.appsearch.GenericDocument
import android.os.Parcel
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
class AppFunctionMetadataTest {
    @get:Rule
    val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    fun testEqualsAndHashcode() {
        val baseDocument = BASE_AF_DOC_BUILDER
            .setPropertyString(PROPERTY_SCHEMA_NAME, "originalName")
            .build()

        val base = AppFunctionMetadata.Builder(baseDocument, TEST_PACKAGE_METADATA).build()

        // 1. Identical instance
        val identical = AppFunctionMetadata.Builder(baseDocument, TEST_PACKAGE_METADATA).build()
        assertThat(base).isEqualTo(identical)
        assertThat(base.hashCode()).isEqualTo(identical.hashCode())

        // 2. Different ID (Part of the GenericDocument)
        val diffId = AppFunctionMetadata.Builder(
            BASE_AF_DOC_BUILDER.setId("different/id").build(),
            TEST_PACKAGE_METADATA
        ).build()
        assertThat(base).isNotEqualTo(diffId)

        // 3. Different Schema Property
        val diffSchema = AppFunctionMetadata.Builder(
            BASE_AF_DOC_BUILDER.setPropertyString(PROPERTY_SCHEMA_NAME, "newName").build(),
            TEST_PACKAGE_METADATA
        ).build()
        assertThat(base).isNotEqualTo(diffSchema)

        // 4. Different Scope
        val diffScope = AppFunctionMetadata.Builder(
            BASE_AF_DOC_BUILDER.setPropertyString(PROPERTY_SCOPE, PROPERTY_VALUE_SCOPE_ACTIVITY)
                .build(),
            TEST_PACKAGE_METADATA
        ).build()
        assertThat(base).isNotEqualTo(diffScope)

        // 5. Different Package Metadata
        val diffPackage = AppFunctionMetadata.Builder(
            baseDocument,
            AppFunctionPackageMetadata.create("different.package", emptyList())
        ).build()
        assertThat(base).isNotEqualTo(diffPackage)
    }

    @Test
    fun testConstructor_propertiesCorrectlyMapped() {
        val metadata = AppFunctionMetadata.Builder(TEST_FULL_AF_DOC, TEST_PACKAGE_METADATA).build()

        assertThat(metadata.name).isEqualTo(AppFunctionName("testPackage", "testFunctionId"))
        assertThat(metadata.schemaMetadata).isEqualTo(
            AppFunctionSchemaMetadata(
                "testCategory",
                "testName",
                1L
            )
        )
        assertThat(metadata.packageMetadata).isEqualTo(TEST_PACKAGE_METADATA)
        assertThat(metadata.scope).isEqualTo(SCOPE_GLOBAL)
    }

    @Test
    fun testParcelAndUnparcel_allFieldsSet() {
        val original = AppFunctionMetadata.Builder(TEST_FULL_AF_DOC, TEST_PACKAGE_METADATA).build()
        val restored = parcelAndUnparcel(original)

        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun testParcelAndUnparcel_noSchema_minFieldSet() {
        val original =
            AppFunctionMetadata.Builder(TEST_AF_DOC_NO_SCHEMA, TEST_PACKAGE_METADATA).build()
        val restored = parcelAndUnparcel(original)

        assertThat(restored).isEqualTo(original)
    }

    private fun parcelAndUnparcel(original: AppFunctionMetadata): AppFunctionMetadata {
        val parcel = Parcel.obtain()
        return try {
            original.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            AppFunctionMetadata.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }

    private companion object {
        val BASE_AF_DOC_BUILDER = GenericDocument.Builder<GenericDocument.Builder<*>>(
            "", "testPackage/testFunctionId", ""
        )
            .setPropertyBoolean(
                AppFunctionStaticMetadataHelper.STATIC_PROPERTY_ENABLED_BY_DEFAULT,
                true
            )
            .setPropertyString(PROPERTY_SCOPE, PROPERTY_VALUE_SCOPE_GLOBAL)

        val TEST_FULL_AF_DOC = BASE_AF_DOC_BUILDER
            .setPropertyString(PROPERTY_SCHEMA_CATEGORY, "testCategory")
            .setPropertyString(PROPERTY_SCHEMA_NAME, "testName")
            .setPropertyLong(PROPERTY_SCHEMA_VERSION, 1L)
            .build()

        val TEST_AF_DOC_NO_SCHEMA = BASE_AF_DOC_BUILDER.build()

        val TEST_PACKAGE_METADATA = AppFunctionPackageMetadata.create(
            "testPackage",
            listOf(
                GenericDocument.Builder<GenericDocument.Builder<*>>("", "", "")
                    .setPropertyString("exampleProperty", "exampleValue")
                    .build()
            )
        )
    }
}