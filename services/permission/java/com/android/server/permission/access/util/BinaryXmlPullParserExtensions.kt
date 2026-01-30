/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.permission.access.util

import com.android.modules.utils.BinaryXmlPullParser
import java.io.IOException
import java.io.InputStream
import org.xmlpull.v1.XmlPullParserException

/** Parse content from [InputStream] with [BinaryXmlPullParser]. */
@Throws(IOException::class, XmlPullParserException::class)
inline fun InputStream.parseBinaryXml(block: BinaryXmlPullParser.() -> Unit) {
    BinaryXmlPullParser().apply {
        setInput(this@parseBinaryXml, null)
        block()
    }
}

/** @see BinaryXmlPullParser.getName */
inline val BinaryXmlPullParser.tagName: String
    get() = name

/** Check whether an attribute exists for the current tag. */
@Suppress("NOTHING_TO_INLINE")
inline fun BinaryXmlPullParser.hasAttribute(name: String): Boolean = getAttributeIndex(name) != -1

/** @see BinaryXmlPullParser.getAttributeIndex */
@Suppress("NOTHING_TO_INLINE")
inline fun BinaryXmlPullParser.getAttributeIndex(name: String): Int = getAttributeIndex(null, name)

/** @see BinaryXmlPullParser.getAttributeIndexOrThrow */
@Suppress("NOTHING_TO_INLINE")
@Throws(XmlPullParserException::class)
inline fun BinaryXmlPullParser.getAttributeIndexOrThrow(name: String): Int =
    getAttributeIndexOrThrow(null, name)

/** @see BinaryXmlPullParser.getAttributeValue */
@Suppress("NOTHING_TO_INLINE")
@Throws(XmlPullParserException::class)
inline fun BinaryXmlPullParser.getAttributeValue(name: String): String? =
    getAttributeValue(null, name)

/** @see BinaryXmlPullParser.getAttributeValue */
@Suppress("NOTHING_TO_INLINE")
@Throws(XmlPullParserException::class)
inline fun BinaryXmlPullParser.getAttributeValueOrThrow(name: String): String =
    getAttributeValue(getAttributeIndexOrThrow(name))

/** @see BinaryXmlPullParser.getAttributeBytesHex */
@Suppress("NOTHING_TO_INLINE")
inline fun BinaryXmlPullParser.getAttributeBytesHex(name: String): ByteArray? =
    getAttributeBytesHex(null, name, null)

/** @see BinaryXmlPullParser.getAttributeBytesHex */
@Suppress("NOTHING_TO_INLINE")
@Throws(XmlPullParserException::class)
inline fun BinaryXmlPullParser.getAttributeBytesHexOrThrow(name: String): ByteArray =
    getAttributeBytesHex(null, name)

/** @see BinaryXmlPullParser.getAttributeBytesBase64 */
@Suppress("NOTHING_TO_INLINE")
inline fun BinaryXmlPullParser.getAttributeBytesBase64(name: String): ByteArray? =
    getAttributeBytesBase64(null, name, null)

/** @see BinaryXmlPullParser.getAttributeBytesBase64 */
@Suppress("NOTHING_TO_INLINE")
@Throws(XmlPullParserException::class)
inline fun BinaryXmlPullParser.getAttributeBytesBase64OrThrow(name: String): ByteArray =
    getAttributeBytesBase64(null, name)

/** @see BinaryXmlPullParser.getAttributeInt */
@Suppress("NOTHING_TO_INLINE")
inline fun BinaryXmlPullParser.getAttributeIntOrDefault(name: String, defaultValue: Int): Int =
    getAttributeInt(null, name, defaultValue)

/** @see BinaryXmlPullParser.getAttributeInt */
@Suppress("NOTHING_TO_INLINE")
@Throws(XmlPullParserException::class)
inline fun BinaryXmlPullParser.getAttributeIntOrThrow(name: String): Int =
    getAttributeInt(null, name)

/** @see BinaryXmlPullParser.getAttributeIntHex */
@Suppress("NOTHING_TO_INLINE")
inline fun BinaryXmlPullParser.getAttributeIntHexOrDefault(name: String, defaultValue: Int): Int =
    getAttributeIntHex(null, name, defaultValue)

/** @see BinaryXmlPullParser.getAttributeIntHex */
@Suppress("NOTHING_TO_INLINE")
@Throws(XmlPullParserException::class)
inline fun BinaryXmlPullParser.getAttributeIntHexOrThrow(name: String): Int =
    getAttributeIntHex(null, name)

/** @see BinaryXmlPullParser.getAttributeLong */
@Suppress("NOTHING_TO_INLINE")
inline fun BinaryXmlPullParser.getAttributeLongOrDefault(name: String, defaultValue: Long): Long =
    getAttributeLong(null, name, defaultValue)

/** @see BinaryXmlPullParser.getAttributeLong */
@Suppress("NOTHING_TO_INLINE")
@Throws(XmlPullParserException::class)
inline fun BinaryXmlPullParser.getAttributeLongOrThrow(name: String): Long =
    getAttributeLong(null, name)

/** @see BinaryXmlPullParser.getAttributeLongHex */
@Suppress("NOTHING_TO_INLINE")
inline fun BinaryXmlPullParser.getAttributeLongHexOrDefault(
    name: String,
    defaultValue: Long,
): Long = getAttributeLongHex(null, name, defaultValue)

/** @see BinaryXmlPullParser.getAttributeLongHex */
@Suppress("NOTHING_TO_INLINE")
@Throws(XmlPullParserException::class)
inline fun BinaryXmlPullParser.getAttributeLongHexOrThrow(name: String): Long =
    getAttributeLongHex(null, name)

/** @see BinaryXmlPullParser.getAttributeFloat */
@Suppress("NOTHING_TO_INLINE")
inline fun BinaryXmlPullParser.getAttributeFloatOrDefault(
    name: String,
    defaultValue: Float,
): Float = getAttributeFloat(null, name, defaultValue)

/** @see BinaryXmlPullParser.getAttributeFloat */
@Suppress("NOTHING_TO_INLINE")
@Throws(XmlPullParserException::class)
inline fun BinaryXmlPullParser.getAttributeFloatOrThrow(name: String): Float =
    getAttributeFloat(null, name)

/** @see BinaryXmlPullParser.getAttributeDouble */
@Suppress("NOTHING_TO_INLINE")
inline fun BinaryXmlPullParser.getAttributeDoubleOrDefault(
    name: String,
    defaultValue: Double,
): Double = getAttributeDouble(null, name, defaultValue)

/** @see BinaryXmlPullParser.getAttributeDouble */
@Suppress("NOTHING_TO_INLINE")
@Throws(XmlPullParserException::class)
inline fun BinaryXmlPullParser.getAttributeDoubleOrThrow(name: String): Double =
    getAttributeDouble(null, name)

/** @see BinaryXmlPullParser.getAttributeBoolean */
@Suppress("NOTHING_TO_INLINE")
inline fun BinaryXmlPullParser.getAttributeBooleanOrDefault(
    name: String,
    defaultValue: Boolean,
): Boolean = getAttributeBoolean(null, name, defaultValue)

/** @see BinaryXmlPullParser.getAttributeBoolean */
@Suppress("NOTHING_TO_INLINE")
@Throws(XmlPullParserException::class)
inline fun BinaryXmlPullParser.getAttributeBooleanOrThrow(name: String): Boolean =
    getAttributeBoolean(null, name)
