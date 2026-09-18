/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.internal.util;


import android.annotation.NonNull;
import android.os.Trace;

/**
 * Utility class that normalizes BigText style Notification content.
 * @hide
 */
public class NotificationBigTextNormalizer {

    // Private constructor to prevent instantiation
    private NotificationBigTextNormalizer() {}

    /**
     * Normalizes the given text by collapsing consecutive new lines into single one and cleaning
     * up each line by removing zero-width characters, invisible formatting characters, and
     * collapsing consecutive whitespace into single space.
     */
    @NonNull
    public static String normalizeBigText(@NonNull String text) {
        try {
            Trace.beginSection("NotifBigTextNormalizer#normalizeBigText");
            return normalizeLines(text);
        } finally {
            Trace.endSection();
        }
    }

    /**
     * Normalizes lines in a text by removing useless zero-width characters, invisible formatting
     * characters, and collapsing consecutive whitespace.
     *
     * <p>This method processes every unicode codepoint of the input text, It:
     *
     * <ul>
     *   <li>Removes useless characters.
     *   <li>Removes any blank lines.
     *   <li>Trims any leading/trailing whitespace.
     *   <li>Merges multiple consecutive whitespace characters into one, even if zero-width
     *       characters break them up.
     * </ul>
     */
    @NonNull
    private static String normalizeLines(@NonNull String text) {
        final StringBuilder sb = new StringBuilder(text.length());
        int cp;
        boolean previousCharWasRenderable = false;
        boolean renderableCharThisLine = false;
        int spaceCharToAddNext = 0;
        for (int j = 0; j < text.length(); j += Character.charCount(cp)) {
            cp = Character.codePointAt(text, j);

            // Skip ZERO WIDTH characters that have no valid use
            if (isUselessZeroWidth(cp)) {
                continue;
            }

            if (isVerticalSpace(cp)) {
                boolean isLastCharacter = j >= text.length() - Character.charCount(cp);
                if (renderableCharThisLine && !isLastCharacter) {
                    sb.append('\n');
                    renderableCharThisLine = false; // Avoids consecutive newlines
                    spaceCharToAddNext = 0; // Avoids trailing spaces
                    previousCharWasRenderable = false; // Avoids leading spaces
                }
                continue;
            }

            if (isHorizontalSpace(cp)) {
                if (previousCharWasRenderable) {
                    spaceCharToAddNext = cp;
                }
                previousCharWasRenderable = false;
            } else {
                if (spaceCharToAddNext != 0) {
                    sb.appendCodePoint(spaceCharToAddNext);
                    spaceCharToAddNext = 0;
                }
                sb.appendCodePoint(cp);
                // Zero width characters don't count when removing whitespace/newlines
                if (isUsefulZeroWidth(cp)) {
                    continue;
                }
                previousCharWasRenderable = true;
                renderableCharThisLine = true;
            }
        }
        return sb.toString();
    }

    private static boolean isUselessZeroWidth(int cp) {
        // Zero Width characters that are deprecated, unused, or do no formatting.
        return cp == '\uFEFF' // Deprecated zero width space
                || (cp >= '\u2061' && cp <= '\u2065') // Invisible math
                // Interlinear annotator symbols: (Unicode discourages use of these)
                || (cp >= '\uFFF9' && cp <= '\uFFFB')
                // Musical Note control characters:
                || cp == 0x1D150
                || cp == 0x1D159
                || (cp >= 0x1D173 && cp <= 0x1D17A);
    }

    private static boolean isUsefulZeroWidth(int cp) {
        // Zero Width characters that do formatting.
        return cp == '\u00AD' // Soft hyphen
                || cp == '\u034F' // Combining Grapheme Joiner
                // Khmer Vowel characters:
                || cp == '\u17B4'
                || cp == '\u17B5'
                // Mongolian vowel separators:
                || (cp >= '\u180B' && cp <= '\u180E')
                || cp == '\uFFFC' // Object replacement character
                || (cp >= '\u200B' && cp <= '\u200D') // Zero width space & joiners
                || cp == '\u2060' // Word joiner (same as zero width space)
                // Characters related to writing direction:
                || cp == '\u200E'
                || cp == '\u200F'
                || cp == '\u061C'
                || (cp >= '\u202A' && cp <= '\u202E')
                || (cp >= '\u206A' && cp <= '\u206F')
                || (cp >= '\u2066' && cp <= '\u2069');
    }

    private static boolean isHorizontalSpace(int cp) {
        return Character.isSpaceChar(cp)
                || cp == '\t'
                || cp == '\u2800' // Braille Pattern Blank
                || cp == '\u3164' // Hangul Filler
                || cp == '\u115F' // Hangul Choseong Filler
                || cp == '\u1160' // Hangul Jungseong Filler
                || cp == '\uFFA0'; // Hangul Halfwidth Filler
    }

    private static boolean isVerticalSpace(int cp) {
        return cp == '\n'
                || cp == '\r'
                || cp == '\f'
                || cp == '\u000B'
                || cp == '\u0085'
                || cp == '\u2028'
                || cp == '\u2029';
    }
}
