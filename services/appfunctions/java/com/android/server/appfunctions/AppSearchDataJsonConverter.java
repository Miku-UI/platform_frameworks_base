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
package com.android.server.appfunctions;

import android.app.appsearch.GenericDocument;
import android.app.appsearch.SearchResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class AppSearchDataJsonConverter {

    private AppSearchDataJsonConverter() {}

    /**
     * Converts a JSON string to a {@link GenericDocument}.
     *
     * <p>This method parses the provided JSON string and creates a {@link GenericDocument}
     * representation. It extracts the 'id', 'namespace', and 'schemaType' fields from the top-level
     * JSON object to initialize the {@code GenericDocument}. It then iterates through the remaining
     * keys in the JSON object and adds them as properties to the {@code GenericDocument}.
     *
     * <p>Example Input:
     *
     * <pre>{@code
     * {"createNoteParams":{"title":"My title"}}
     * }</pre>
     */
    public static GenericDocument convertJsonToGenericDocument(String jsonString)
            throws JSONException {
        JSONObject json = new JSONObject(jsonString);

        String id = json.optString("id", "");
        String namespace = json.optString("namespace", "");
        String schemaType = json.optString("schemaType", "");

        GenericDocument.Builder builder = new GenericDocument.Builder(namespace, id, schemaType);

        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.get(key);

            if (value instanceof String) {
                builder.setPropertyString(key, (String) value);
            } else if (value instanceof Integer || value instanceof Long) {
                builder.setPropertyLong(key, ((Number) value).longValue());
            } else if (value instanceof Double || value instanceof Float) {
                builder.setPropertyDouble(key, ((Number) value).doubleValue());
            } else if (value instanceof Boolean) {
                builder.setPropertyBoolean(key, (Boolean) value);
            } else if (value instanceof JSONObject) {
                GenericDocument nestedDocument = convertJsonToGenericDocument(value.toString());
                builder.setPropertyDocument(key, nestedDocument);
            } else if (value instanceof JSONArray) {
                JSONArray array = (JSONArray) value;
                if (array.length() == 0) {
                    continue;
                }

                Object first = array.get(0);
                if (first instanceof String) {
                    String[] arr = new String[array.length()];
                    for (int i = 0; i < array.length(); i++) {
                        arr[i] = array.optString(i, null);
                    }
                    builder.setPropertyString(key, arr);
                } else if (first instanceof Integer || first instanceof Long) {
                    long[] arr = new long[array.length()];
                    for (int i = 0; i < array.length(); i++) {
                        arr[i] = array.getLong(i);
                    }
                    builder.setPropertyLong(key, arr);
                } else if (first instanceof Double || first instanceof Float) {
                    double[] arr = new double[array.length()];
                    for (int i = 0; i < array.length(); i++) {
                        arr[i] = array.getDouble(i);
                    }
                    builder.setPropertyDouble(key, arr);
                } else if (first instanceof Boolean) {
                    boolean[] arr = new boolean[array.length()];
                    for (int i = 0; i < array.length(); i++) {
                        arr[i] = array.getBoolean(i);
                    }
                    builder.setPropertyBoolean(key, arr);
                } else if (first instanceof JSONObject) {
                    GenericDocument[] documentArray = new GenericDocument[array.length()];
                    for (int i = 0; i < array.length(); i++) {
                        documentArray[i] =
                                convertJsonToGenericDocument(array.getJSONObject(i).toString());
                    }
                    builder.setPropertyDocument(key, documentArray);
                }
            }
        }
        return builder.build();
    }

    /**
     * Converts a single {@link GenericDocument} into a {@link JSONObject}.
     *
     * <p>This method iterates over all properties of the given {@code GenericDocument}. All
     * properties, regardless of whether they are single or repeated, are converted into a {@link
     * JSONArray}.
     *
     * @param genericDocument The {@link GenericDocument} to convert.
     * @return The {@link JSONObject} representation of the document.
     * @throws JSONException if there is an error during JSON conversion.
     */
    public static JSONObject convertGenericDocumentToJson(GenericDocument genericDocument)
            throws JSONException {
        JSONObject jsonObject = new JSONObject();
        Set<String> propertyNames = genericDocument.getPropertyNames();

        for (String propertyName : propertyNames) {
            Object propertyValue = genericDocument.getProperty(propertyName);
            if (propertyValue == null) {
                jsonObject.put(propertyName, JSONObject.NULL);
                continue;
            }

            // HACK: GenericDocument doesn't tell whether a property is singular or repeated.
            // Here, we always convert a property into an array.
            if (propertyValue instanceof GenericDocument[]) {
                GenericDocument[] documentValues = (GenericDocument[]) propertyValue;
                JSONArray jsonArray = new JSONArray();
                for (GenericDocument doc : documentValues) {
                    jsonArray.put(convertGenericDocumentToJson(doc));
                }
                jsonObject.put(propertyName, jsonArray);
            } else if (propertyValue.getClass().isArray()) {
                JSONArray jsonArray = new JSONArray();
                int propertyArrLength = Array.getLength(propertyValue);
                for (int i = 0; i < propertyArrLength; i++) {
                    Object propertyElement = Array.get(propertyValue, i);
                    jsonArray.put(propertyElement);
                }
                jsonObject.put(propertyName, jsonArray);
            }
        }
        return jsonObject;
    }

    /**
     * Converts a {@link SearchResult}, including any nested joined results, into a single {@link
     * JSONObject}.
     *
     * <p>The resulting JSON object uses the schema type of each document as the key for its JSON
     * representation.
     *
     * @param searchResult The {@link SearchResult} to convert.
     * @return A {@link JSONObject} representing the nested structure of the search result.
     * @throws JSONException if there is an error during JSON conversion.
     */
    public static JSONObject searchResultToJsonObject(SearchResult searchResult)
            throws JSONException {
        JSONObject jsonObject = new JSONObject();
        GenericDocument genericDocument = searchResult.getGenericDocument();
        jsonObject.put(
                genericDocument.getSchemaType(),
                AppSearchDataJsonConverter.convertGenericDocumentToJson(genericDocument));

        List<SearchResult> joinedResults = searchResult.getJoinedResults();
        for (SearchResult joinedResult : joinedResults) {
            jsonObject.put(
                    joinedResult.getGenericDocument().getSchemaType(),
                    searchResultToJsonObject(joinedResult));
        }
        return jsonObject;
    }
}
