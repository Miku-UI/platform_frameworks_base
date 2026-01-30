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
package com.android.server.appfunctions

import android.app.appsearch.GenericDocument
import android.app.appsearch.SearchResult
import com.google.common.truth.Truth.assertThat
import org.json.JSONException
import org.junit.Test

class AppSearchDataJsonConverterTest {

    @Test
    fun convertJsonToGenericDocument_withAllScalarTypes_succeeds() {
        val jsonString = """
        {
          "id": "doc1",
          "namespace": "ns1",
          "schemaType": "TestSchema",
          "stringProp": "hello world",
          "longProp": 1234567890,
          "doubleProp": 98.76,
          "boolProp": true
        }
        """.trimIndent()

        val doc = AppSearchDataJsonConverter.convertJsonToGenericDocument(jsonString)

        assertThat(doc.id).isEqualTo("doc1")
        assertThat(doc.namespace).isEqualTo("ns1")
        assertThat(doc.schemaType).isEqualTo("TestSchema")
        assertThat(doc.getPropertyString("stringProp")).isEqualTo("hello world")
        assertThat(doc.getPropertyLong("longProp")).isEqualTo(1234567890L)
        assertThat(doc.getPropertyDouble("doubleProp")).isEqualTo(98.76)
        assertThat(doc.getPropertyBoolean("boolProp")).isTrue()
    }

    @Test
    fun convertJsonToGenericDocument_withAllArrayTypes_succeeds() {
        val jsonString = """
        {
          "id": "doc2",
          "namespace": "ns1",
          "schemaType": "ArraySchema",
          "stringArray": ["a", "b", "c"],
          "longArray": [1, 2, 3],
          "doubleArray": [1.1, 2.2, 3.3],
          "boolArray": [true, false, true]
        }
        """.trimIndent()

        val doc = AppSearchDataJsonConverter.convertJsonToGenericDocument(jsonString)

        assertThat(doc.id).isEqualTo("doc2")
        assertThat(doc.getPropertyStringArray("stringArray")).isEqualTo(arrayOf("a", "b", "c"))
        assertThat(doc.getPropertyLongArray("longArray")).isEqualTo(longArrayOf(1, 2, 3))
        assertThat(doc.getPropertyDoubleArray("doubleArray")).isEqualTo(
            doubleArrayOf(
                1.1,
                2.2,
                3.3
            )
        )
        assertThat(doc.getPropertyBooleanArray("boolArray")).isEqualTo(
            booleanArrayOf(
                true,
                false,
                true
            )
        )
    }

    @Test
    fun convertJsonToGenericDocument_withNestedDocument_succeeds() {
        val jsonString = """
        {
          "id": "outerDoc",
          "namespace": "outerNs",
          "schemaType": "Outer",
          "nestedDoc": {
            "id": "innerDoc",
            "namespace": "innerNs",
            "schemaType": "Inner",
            "innerProp": "I am nested"
          }
        }
        """.trimIndent()

        val doc = AppSearchDataJsonConverter.convertJsonToGenericDocument(jsonString)
        val nestedDoc = doc.getPropertyDocument("nestedDoc")

        assertThat(nestedDoc).isNotNull()
        assertThat(nestedDoc!!.id).isEqualTo("innerDoc")
        assertThat(nestedDoc.namespace).isEqualTo("innerNs")
        assertThat(nestedDoc.schemaType).isEqualTo("Inner")
        assertThat(nestedDoc.getPropertyString("innerProp")).isEqualTo("I am nested")
    }

    @Test
    fun convertJsonToGenericDocument_withArrayOfNestedDocuments_succeeds() {
        val jsonString = """
        {
            "id": "main",
            "schemaType": "Main",
            "docArray": [
                {"schemaType": "Nested", "prop": "first"},
                {"schemaType": "Nested", "prop": "second"}
            ]
        }
        """.trimIndent()

        val doc = AppSearchDataJsonConverter.convertJsonToGenericDocument(jsonString)
        val docArray = doc.getPropertyDocumentArray("docArray")

        assertThat(docArray).isNotNull()
        assertThat(docArray!!.size).isEqualTo(2)
        assertThat(docArray[0].schemaType).isEqualTo("Nested")
        assertThat(docArray[0].getPropertyString("prop")).isEqualTo("first")
        assertThat(docArray[1].schemaType).isEqualTo("Nested")
        assertThat(docArray[1].getPropertyString("prop")).isEqualTo("second")
    }

    @Test(expected = JSONException::class)
    fun convertJsonToGenericDocument_withInvalidJson_throwsException() {
        val invalidJson = """{"key": "value",}"""
        AppSearchDataJsonConverter.convertJsonToGenericDocument(invalidJson)
    }

    @Test
    fun convertJsonToGenericDocument_withEmptyJson_createsEmptyDocument() {
        val jsonString = "{}"
        val doc = AppSearchDataJsonConverter.convertJsonToGenericDocument(jsonString)

        assertThat(doc.id).isEmpty()
        assertThat(doc.namespace).isEmpty()
        assertThat(doc.schemaType).isEmpty()
        assertThat(doc.propertyNames).isEmpty()
    }

    @Test
    fun convertJsonToGenericDocument_withEmptyArrays_ignoresProperties() {
        val jsonString = """{"emptyStringArray": [], "emptyLongArray": []}"""
        val doc = AppSearchDataJsonConverter.convertJsonToGenericDocument(jsonString)

        assertThat(doc.propertyNames).isEmpty()
    }

    @Test
    fun convertGenericDocumentToJson_withAllTypes_succeeds() {
        val nestedDoc =
            GenericDocument.Builder<GenericDocument.Builder<*>>("nestedId", "ns", "Nested")
                .setPropertyString("nestedProp", "value")
                .build()

        val mainDoc = GenericDocument.Builder<GenericDocument.Builder<*>>("docId", "ns", "Main")
            .setPropertyString("stringProp", "hello")
            .setPropertyLong("longProp", 100L)
            .setPropertyDouble("doubleProp", 1.23)
            .setPropertyBoolean("boolProp", true)
            .setPropertyString("stringArrProp", "a", "b")
            .setPropertyDocument("docProp", nestedDoc)
            .build()

        val jsonObject = AppSearchDataJsonConverter.convertGenericDocumentToJson(mainDoc)

        assertThat(jsonObject.getJSONArray("stringProp").getString(0)).isEqualTo("hello")
        assertThat(jsonObject.getJSONArray("longProp").getLong(0)).isEqualTo(100L)
        assertThat(jsonObject.getJSONArray("doubleProp").getDouble(0)).isEqualTo(1.23)
        assertThat(jsonObject.getJSONArray("boolProp").getBoolean(0)).isTrue()
        assertThat(jsonObject.getJSONArray("stringArrProp").length()).isEqualTo(2)
        assertThat(jsonObject.getJSONArray("stringArrProp").getString(1)).isEqualTo("b")

        val nestedJson = jsonObject.getJSONArray("docProp").getJSONObject(0)
        assertThat(nestedJson.getJSONArray("nestedProp").getString(0)).isEqualTo("value")
    }

    @Test
    fun convertGenericDocumentToJson_withEmptyDocument_returnsEmptyObject() {
        val emptyDoc =
            GenericDocument.Builder<GenericDocument.Builder<*>>("id", "ns", "Schema").build()
        val jsonObject = AppSearchDataJsonConverter.convertGenericDocumentToJson(emptyDoc)
        assertThat(jsonObject.length()).isEqualTo(0)
    }

    @Test
    fun convertGenericDocumentsToJsonArray_succeeds() {
        val doc1 = GenericDocument.Builder<GenericDocument.Builder<*>>("id1", "ns", "MySchema")
            .setPropertyString("prop", "val1")
            .build()
        val doc2 = GenericDocument.Builder<GenericDocument.Builder<*>>("id2", "ns", "MySchema")
            .setPropertyString("prop", "val2")
            .build()

        assertThat(AppSearchDataJsonConverter.convertGenericDocumentToJson(doc1).getJSONArray("prop").getString(0)).isEqualTo("val1")
        assertThat(AppSearchDataJsonConverter.convertGenericDocumentToJson(doc2).getJSONArray("prop").getString(0)).isEqualTo("val2")
    }

    @Test
    fun searchResultToJsonObject_singleResultNoJoins_succeeds() {
        val document = GenericDocument.Builder<GenericDocument.Builder<*>>("id1", "ns1", "Note")
            .setPropertyString("content", "This is a note.")
            .build()

        val searchResult = SearchResult.Builder("com.example.pkg", "db")
            .setGenericDocument(document)
            .build()

        val resultJson = AppSearchDataJsonConverter.searchResultToJsonObject(searchResult)

        assertThat(resultJson.has("Note")).isTrue()
        val noteJson = resultJson.getJSONObject("Note")
        assertThat(noteJson.getJSONArray("content").getString(0)).isEqualTo("This is a note.")
    }

    @Test
    fun searchResultToJsonObject_withOneLevelOfJoinedResults_succeeds() {
        val tripDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("trip1", "ns", "Trip")
                .setPropertyString("destination", "Hawaii")
                .build()
        val flightDocument =
            GenericDocument.Builder<GenericDocument.Builder<*>>("flight1", "ns", "Flight")
                .setPropertyString("flightNumber", "UA123")
                .build()

        val joinedSearchResult = SearchResult.Builder("com.example.pkg", "db")
            .setGenericDocument(flightDocument)
            .build()

        val mainSearchResult = SearchResult.Builder("com.example.pkg", "db")
            .setGenericDocument(tripDocument)
            .addJoinedResult(joinedSearchResult)
            .build()

        val resultJson = AppSearchDataJsonConverter.searchResultToJsonObject(mainSearchResult)

        assertThat(resultJson.has("Trip")).isTrue()
        assertThat(resultJson.has("Flight")).isTrue()

        val tripJson = resultJson.getJSONObject("Trip")
        assertThat(tripJson.getJSONArray("destination").getString(0)).isEqualTo("Hawaii")

        val flightJson = resultJson.getJSONObject("Flight").getJSONObject("Flight")
        assertThat(flightJson.getJSONArray("flightNumber").getString(0)).isEqualTo("UA123")
    }
}
