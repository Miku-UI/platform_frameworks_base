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
import android.text.TextUtils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts AppSearch {@link GenericDocument} objects into human-readable YAML strings.
 * This class uses a minimal, dependency-free YAML generator to avoid binary size increases.
 */
public class AppSearchDataYamlConverter {

    private AppSearchDataYamlConverter() {}

    /**
     * Converts an array of {@link GenericDocument} objects into a YAML string. This method provides
     * options to control the output.
     *
     * @param document The {@link GenericDocument} to convert.
     * @param keepEmptyValues If false, properties with empty values (empty strings, empty array)
     * will be excluded.
     * @param keepNullValues If false, properties with null values will be excluded.
     * @param keepGenericDocumentProperties If false, document metadata (id, namespace, etc.) will
     *     not be included in the output.
     * @return A YAML string representing a list of documents.
     */
    public static String convertGenericDocumentToYaml(
            GenericDocument document,
            boolean keepEmptyValues,
            boolean keepNullValues,
            boolean keepGenericDocumentProperties) {
        return MinimalYamlGenerator.dump(genericDocumentToMap(
                document,
                keepEmptyValues,
                keepNullValues,
                keepGenericDocumentProperties));
    }

    /**
     * Recursively converts a {@link GenericDocument} into a {@link Map}, filtering based on flags.
     *
     * @return A {@link Map} representing the document's properties.
     */
    private static Map<String, Object> genericDocumentToMap(
            GenericDocument doc,
            boolean keepEmptyValues,
            boolean keepNullValues,
            boolean keepGenericDocumentProperties) {
        Map<String, Object> map = new LinkedHashMap<>();

        if (keepGenericDocumentProperties) {
            map.put("id", doc.getId());
            map.put("namespace", doc.getNamespace());
            map.put("schemaType", doc.getSchemaType());
            map.put("creationTimestampMillis", doc.getCreationTimestampMillis());
            map.put("score", doc.getScore());
        }

        Set<String> propertyNames = doc.getPropertyNames();

        for (String propName : propertyNames) {
            Object propValue = doc.getProperty(propName);

            if (!keepNullValues && propValue == null) {
                continue;
            }

            if (propValue == null) {
                map.put(propName, null);
                continue;
            }

            // HACK: GenericDocument doesn't tell whether a property is singular or
            // repeated. Here, we always convert a property into an array.
            if (propValue instanceof GenericDocument[]) {
                List<Map<String, Object>> list = new ArrayList<>();
                for (GenericDocument nestedDoc : (GenericDocument[]) propValue) {
                    list.add(
                            genericDocumentToMap(
                                nestedDoc,
                                keepEmptyValues,
                                keepNullValues,
                                keepGenericDocumentProperties));
                }

                if (!keepEmptyValues && list.isEmpty()) {
                    continue;
                }

                if (list.size() == 1) {
                    map.put(propName, list.get(0));
                } else {
                    map.put(propName, list);
                }
            } else if (propValue.getClass().isArray()) {
                int length = Array.getLength(propValue);

                List<Object> list = new ArrayList<>();
                for (int i = 0; i < length; i++) {
                    Object singleValue = Array.get(propValue, i);

                    if (!keepEmptyValues && isEmptyValue(singleValue)) {
                        continue;
                    }

                    list.add(singleValue);
                }

                if (!keepEmptyValues && list.isEmpty()) {
                    continue;
                }

                if (list.size() == 1) {
                    map.put(propName, list.get(0));
                } else {
                    map.put(propName, list);
                }
            }
        }

        return map;
    }

    /**
     * Checks if a given value is an empty value (empty string, or empty array).
     *
     * @param value The value to check.
     * @return true if the value is empty, false otherwise.
     */
    private static boolean isEmptyValue(Object value) {
        if (value == null) {
            return false;
        }

        if (value instanceof String && ((String) value).isEmpty()) {
            return true;
        }

        if (value.getClass().isArray() && Array.getLength(value) == 0) {
            return true;
        }

        return false;
    }

    /**
     * A minimal YAML generator to avoid a heavy library dependency. This supports Maps, Lists, and
     * primitive types, producing a readable YAML string.
     */
    public static class MinimalYamlGenerator {
        private static final String INDENT = "  ";

        public static String dump(Object data) {
            StringBuilder sb = new StringBuilder();
            dumpObject(data, sb, 0, true);
            return sb.toString();
        }

        private static void dumpObject(
                Object data,
                StringBuilder sb,
                int indentLevel,
                boolean indentFirst) {
            if (data instanceof Map) {
                dumpMap((Map<?, ?>) data, sb, indentLevel, indentFirst);
            } else if (data instanceof List) {
                dumpList((List<?>) data, sb, indentLevel);
            } else {
                sb.append(formatPrimitive(data));
            }
        }

        private static void dumpMap(
                Map<?, ?> map,
                StringBuilder sb,
                int indentLevel,
                boolean indentFirst) {
            String indent = INDENT.repeat(indentLevel);
            boolean isFirst = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (isFirst) {
                    if (indentFirst) {
                        sb.append(indent);
                    }
                } else {
                    sb.append("\n").append(indent);
                }

                sb.append(formatPrimitive(entry.getKey())).append(":");

                Object value = entry.getValue();

                if (isComplex(value)) {
                    sb.append("\n");
                    dumpObject(value, sb, indentLevel + 1, true);
                } else {
                    sb.append(" ");
                    dumpObject(value, sb, 0, true);
                }

                isFirst = false;
            }
        }

        private static void dumpList(List<?> list, StringBuilder sb, int indentLevel) {
            String indent = INDENT.repeat(indentLevel);
            boolean isFirst = true;
            for (Object item : list) {
                if (!isFirst) {
                    sb.append("\n");

                }
                sb.append(indent).append("- ");
                if (isComplex(item)) {
                    dumpObject(item, sb, indentLevel + 1, false);
                } else {
                    dumpObject(item, sb, 0, false);
                }
                isFirst = false;
            }
        }

        private static boolean isComplex(Object obj) {
            return obj instanceof Map || obj instanceof List;
        }

        /**
         * Formats a primitive value, ensuring strings are properly quoted and escaped if necessary.
         */
        private static String formatPrimitive(Object primitive) {
            if (primitive == null) {
                return "null";
            }

            if (primitive instanceof String) {
                String str = (String) primitive;
                if (shouldQuote(str)) {
                    return doubleQuoteAndEscape(str);
                }
                return str;
            }

            return primitive.toString();
        }

        /**
         * Determines if a string requires quoting in YAML.
         * Checks for special characters, empty strings, or strings that look like other types.
         */
        private static boolean shouldQuote(String str) {
            if (str.isEmpty()) return true;

            // Check for "look-alike" types
            if ("true".equals(str) || "false".equals(str) || "null".equals(str)
                    || "~".equals(str)) {
                return true;
            }

            // Check for characters that might confuse a YAML parser or require escaping
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                // These characters often require quoting if they appear in specific contexts,
                // but it's safer to quote if they appear anywhere for a minimal generator.
                if (c == ':' || c == '#' || c == '[' || c == ']' || c == '{' || c == '}'
                        || c == ',' || c == '*' || c == '&' || c == '!' || c == '|' || c == '>'
                        || c == '\'' || c == '"' || c == '%' || c == '@' || c == '`'
                        || c == '\n' || c == '\r' || c == '\t' || c == '\\') {
                    return true;
                }
            }

            // Also quote if it looks strictly like a boolean or number to preserve type if needed,
            // though for human-readability this might be optional.
            // Safest to not quote simple alphanumerics.
            // A simple heuristic: if it doesn't start with an alphanumeric, quote it.
            char first = str.charAt(0);
            if (first == '-' || first == '?' || Character.isWhitespace(first)) {
                 return true;
            }

            // Ensure it doesn't contain ": " which is a key-value separator
            if (str.contains(": ")) return true;

            return false;
        }

        /**
         * Double-quotes a string and escapes common control characters and backslashes.
         */
        private static String doubleQuoteAndEscape(String str) {
            StringBuilder sb = new StringBuilder("\"");
            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                switch (c) {
                    case '\\': sb.append("\\\\"); break;
                    case '"': sb.append("\\\""); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    default:
                        // For a minimal generator, we might not need full unicode escaping,
                        // but handling basic control chars is good practice.
                        if (c < 32) {
                             sb.append(TextUtils.formatSimple("\\u%04x", (int) c));
                        } else {
                             sb.append(c);
                        }
                }
            }
            sb.append("\"");
            return sb.toString();
        }
    }
}