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

import static junit.framework.Assert.assertEquals;


import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.ravenwood.RavenwoodRule;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test for {@link NotificationBigTextNormalizer}
 * @hide
 */
@DisabledOnRavenwood(blockedBy = NotificationBigTextNormalizer.class)
@RunWith(AndroidJUnit4.class)
public class NotificationBigTextNormalizerTest {

    @Rule
    public final RavenwoodRule mRavenwood = new RavenwoodRule();


    @Test
    public void testEmptyInput() {
        assertEquals("", NotificationBigTextNormalizer.normalizeBigText(""));
    }

    @Test
    public void testSingleNewline() {
        assertEquals("", NotificationBigTextNormalizer.normalizeBigText("\n"));
    }

    @Test
    public void testMultipleConsecutiveNewlines() {
        assertEquals("", NotificationBigTextNormalizer.normalizeBigText("\n\n\n\n\n"));
    }

    @Test
    public void testNewlinesWithSpacesAndZeroWidths() {
        String input = "\n\u202D\u2800\n\u2060\n.\n";
        String expected = "\u202D\u2060.";
        assertEquals(
                printCodes(expected),
                printCodes(NotificationBigTextNormalizer.normalizeBigText(input)));
    }

    @Test
    public void testMixOfWhitespaceAndZeroWidthSpecialCharacters() {
        // We keep zero-width characters that have a valid use for formatting text.
        String input =
                "\u2800\u200D\u3164\u200C\u115F\u200B\u00A0\u2060\u2002\u200D\u2003\u200C"
                        + "\u2004\u200B\u2009\u2060\u3000";
        String expected = "\u200D\u200C\u200B\u2060\u200D\u200C\u200B\u2060";
        assertEquals(
                printCodes(expected),
                printCodes(NotificationBigTextNormalizer.normalizeBigText(input)));
    }

    @Test
    public void allOfInvisibleCharacters() {
        // Everything from http://invisible-characters.com that aren't illegal to be input as java
        // strings.
        String input =
                "\u0009\u000B\u000C \u00A0\u00AD\u034F\u061C\u115F\u1160\u1680\u17B4"
                        + "\u17B5\u180B\u180C\u180D\u180E\u2000\u2001\u2002\u2003\u2004"
                        + "\u2005\u2006\u2007\u2008\u2009\u200A\u200B\u200C\u200D\u200E"
                        + "\u200F\u202A\u202B\u202C\u202D\u202F\u205F\u2060\u2061\u2062"
                        + "\u2063\u2064\u2065\u2066\u2067\u2068\u2069\u206A\u206B\u206C"
                        + "\u206D\u206E\u206F\u2800\u3000\u3164\uFEFF\uFFA0\uFFF9\uFFFA"
                        + "\uFFFB\uFFFC\uD80C\uDFFC\uD834\uDD50\u202E";
        // We keep zero-width characters that have a valid use for formatting text.
        String expected =
                "\u00AD\u034F\u061C\u17B4\u17B5\u180B\u180C\u180D\u180E\u200B\u200C\u200D"
                        + "\u200E\u200F\u202A\u202B\u202C\u202D\u2060\u2066\u2067\u2068\u2069\u206A"
                        + "\u206B\u206C\u206D\u206E\u206F\uFFFC\uD80C\uDFFC\u202E";
        assertEquals(
                printCodes(expected),
                printCodes(NotificationBigTextNormalizer.normalizeBigText(input)));
    }

    @Test
    public void testNewlinesWithSpacesAndTabs() {
        String input = "Line 1\n  \n \t \n\tLine 2";
        // Adjusted expected output to include the tab character
        String expected = "Line 1\nLine 2";
        assertEquals(expected, NotificationBigTextNormalizer.normalizeBigText(input));
    }

    @Test
    public void testMixedNewlineCharacters() {
        String input = "Line 1\r\nLine 2\u000BLine 3\fLine 4\u2028Line 5\u2029Line 6";
        String expected = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5\nLine 6";
        assertEquals(expected, NotificationBigTextNormalizer.normalizeBigText(input));
    }

    @Test
    public void testConsecutiveSpaces() {
        // Only spaces
        assertEquals("This is a test.", NotificationBigTextNormalizer.normalizeBigText("This"
                + "              is   a                         test."));
        // Zero width characters between spaces. the not-useful \uFEFF should be removed.
        assertEquals(
                "This is a test.",
                NotificationBigTextNormalizer.normalizeBigText(
                        "This \uFEFF \uFEFF \uFEFF \uFEFF"
                                + " \uFEFF \uFEFF \uFEFF \uFEFFis a"
                                + " \uFEFF \uFEFF \uFEFF \uFEFF \uFEFF \uFEFFtest."));

        // Invisible formatting characters bw spaces.
        assertEquals(
                "This is\u206E \u206E\u206E\u206Ea test.",
                NotificationBigTextNormalizer.normalizeBigText(
                        "This\u2061 \u2061 \u2061 \u2061 \u2061 \u2061 \u2061 \u2061is\u206E \u206E"
                            + " \u206E \u206Ea \uFFFB \uFFFB \uFFFB \uFFFB \uFFFB \uFFFBtest."));
        // Non breakable spaces
        assertEquals(
                "This\u00A0is a\u2005test.",
                NotificationBigTextNormalizer.normalizeBigText(
                        "This"
                                + "\u00A0\u00A0\u00A0\u00A0\u00A0\u00A0is \u2005 \u2005"
                                + " \u2005a\u2005\u2005\u2005 \u2005\u2005\u2005test."));
        // Whitespace characters handled by java.lang.Character
        assertEquals(
                "This\u1680is a test.",
                NotificationBigTextNormalizer.normalizeBigText(
                        "This" + "\u1680 \u202F \u205Fis a test."));
    }

    @Test
    public void testStandardWhitespaceCharacters() {
        // Whitespace characters represented as '\h' in the standard java regex
        String input =
                "\t\u00A0\u1680\u180e\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008"
                        + "\u2009\u200a\u202f\u205f\u3000";
        String expected = "";
        assertEquals(expected, NotificationBigTextNormalizer.normalizeBigText(input));
    }

    @Test
    public void testRetainsSpecialSpaces() {
        // Keeps special spaces (OGHAM SPACE MARK \u1680)
        String text = "᚛ᚑᚌᚐᚋ ᚔᚄ ᚐᚅᚔᚋ᚜";
        assertEquals(text, NotificationBigTextNormalizer.normalizeBigText(text));
    }

    @Test
    public void testZeroWidthCharRemoval() {
        // Test each character individually
        int[] zeroWidthCodePoints = {'\uFEFF', 0x1D150, 0x1D159, 0x1D173, 0x1D17A};
        for (int c : zeroWidthCodePoints) {
            StringBuilder builder = new StringBuilder();
            builder.append("Test");
            builder.appendCodePoint(c);
            builder.append("string");
            String expected = "Teststring";
            assertEquals(
                    expected, NotificationBigTextNormalizer.normalizeBigText(builder.toString()));
        }
    }

    @Test
    public void testWhitespaceRetained() {
        assertEquals(
                "This\ttext\thas\thorizontal\twhitespace.",
                NotificationBigTextNormalizer.normalizeBigText(
                        "This\ttext\thas\thorizontal\twhitespace."));
        assertEquals(
                "This text has mixed\u2009whitespace.",
                NotificationBigTextNormalizer.normalizeBigText(
                        "This  text  has \u00A0 mixed\u2009whitespace."));
        assertEquals("This text has leading and trailing whitespace.",
                NotificationBigTextNormalizer.normalizeBigText(
                        "\t This text has leading and trailing whitespace. \n"));
    }

    private String printCodes(String string) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            builder.append("\\u");
            builder.append(String.format("%04X", ((int) c)));
        }
        return builder.toString();
    }
}
