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
package com.android.server.permission.access.util

import java.io.IOException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Iterate through child tags of the current tag.
 *
 * <p>
 * Attributes for the current tag needs to be accessed before this method is called because this
 * method will advance the parser past the start tag of the current tag. The code inspecting each
 * child tag may access the attributes of the child tag, and/or call [forEachTag] recursively to
 * inspect grandchild tags, which will naturally leave the parser at either the start tag or the end
 * tag of the child tag it inspected.
 *
 * @see XmlPullParser.next
 * @see XmlPullParser.getEventType
 * @see XmlPullParser.getDepth
 */
@Throws(IOException::class, XmlPullParserException::class)
inline fun XmlPullParser.forEachTag(block: XmlPullParser.() -> Unit) {
    when (val eventType = eventType) {
        // Document start or start tag of the parent tag.
        XmlPullParser.START_DOCUMENT,
        XmlPullParser.START_TAG -> nextTagOrEnd()
        else -> throw XmlPullParserException("Unexpected event type $eventType")
    }
    while (true) {
        when (val eventType = eventType) {
            // Start tag of a child tag.
            XmlPullParser.START_TAG -> {
                val childDepth = depth
                block()
                // block() should leave the parser at either the start tag (no grandchild tags
                // expected) or the end tag (grandchild tags parsed with forEachTag()) of this
                // child
                // tag.
                val postBlockDepth = depth
                if (postBlockDepth != childDepth) {
                    throw XmlPullParserException(
                        "Unexpected post-block depth $postBlockDepth, expected $childDepth"
                    )
                }
                // Skip the parser to the end tag of this child tag.
                while (true) {
                    when (val childEventType = this.eventType) {
                        // Start tag of either this child tag or a grandchild tag.
                        XmlPullParser.START_TAG -> nextTagOrEnd()
                        XmlPullParser.END_TAG -> {
                            if (depth > childDepth) {
                                // End tag of a grandchild tag.
                                nextTagOrEnd()
                            } else {
                                // End tag of this child tag.
                                break
                            }
                        }
                        else ->
                            throw XmlPullParserException("Unexpected event type $childEventType")
                    }
                }
                // Skip the end tag of this child tag.
                nextTagOrEnd()
            }
            // End tag of the parent tag, or document end.
            XmlPullParser.END_TAG,
            XmlPullParser.END_DOCUMENT -> break
            else -> throw XmlPullParserException("Unexpected event type $eventType")
        }
    }
}

/**
 * Advance the parser until the current event is one of [XmlPullParser.START_TAG],
 * [XmlPullParser.START_TAG] and [XmlPullParser.START_TAG]
 *
 * @see XmlPullParser.next
 */
@Throws(IOException::class, XmlPullParserException::class)
@Suppress("NOTHING_TO_INLINE")
inline fun XmlPullParser.nextTagOrEnd(): Int {
    while (true) {
        when (val eventType = next()) {
            XmlPullParser.START_TAG,
            XmlPullParser.END_TAG,
            XmlPullParser.END_DOCUMENT -> return eventType
            else -> continue
        }
    }
}

/** @see XmlPullParser.getName */
inline val XmlPullParser.tagName: String
    get() = name

@Suppress("NOTHING_TO_INLINE")
@Throws(XmlPullParserException::class)
inline fun XmlPullParser.getAttributeValueOrThrow(name: String): String =
    getAttributeValue(null, name) ?: throw XmlPullParserException("Missing attribute $name")
