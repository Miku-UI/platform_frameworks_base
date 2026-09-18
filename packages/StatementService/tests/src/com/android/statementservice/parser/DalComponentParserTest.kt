/*
 * Copyright (C) 2024 The Android Open Source Project
 *
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
package com.android.statementservice.parser

import android.os.PatternMatcher
import android.os.PatternMatcher.PATTERN_LITERAL
import android.os.PatternMatcher.PATTERN_PREFIX
import android.os.PatternMatcher.PATTERN_SIMPLE_GLOB
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DalComponentParserTest {

  @Test
  fun parseExpressions() {
    validateParsedExpression(
        "foobar",
        PATTERN_LITERAL,
        "foobar",
        listOf("foobar"),
        listOf("foo.bar", "foo\\.bar")
    )
    validateParsedExpression(
        "foo.bar",
        PATTERN_LITERAL,
        "foo.bar",
        listOf("foo.bar"),
        listOf("foobar", "foo\\.bar", "foodbar")
    )
    validateParsedExpression(
        "foo*",
        PATTERN_PREFIX,
        "foo",
        listOf("foo", "foobar", "foo.bar", "foo\\.bar"),
        listOf("fo", "barfoo")
    )
    validateParsedExpression(
        "foo.*",
        PATTERN_PREFIX,
        "foo.",
        listOf("foo.", "foo.bar", "foo..."),
        listOf("foo", "fo.", "foobar")
    )
    validateParsedExpression(
        "*bar",
        PATTERN_SIMPLE_GLOB,
        ".*bar",
        listOf("foobar", "foo.bar", "bar", ".bar"),
        listOf("foo", "barfoo", "barfoobar", "abcbar")
    )
    validateParsedExpression(
        "*.bar",
        PATTERN_SIMPLE_GLOB,
        ".*\\.bar",
        listOf(".bar", "foo.bar"),
        listOf("bar", "foobar", ".bar.bar", "foo.b.bar")
    )
    validateParsedExpression(
        "foo*bar",
        PATTERN_SIMPLE_GLOB,
        "foo.*bar",
        listOf("foobar", "foo.bar", "foofoobar"),
        listOf("foobarbar", "f.bar")
    )
    validateParsedExpression(
        "foo\\*bar",
        PATTERN_SIMPLE_GLOB,
        "foo\\\\.*bar",
        listOf("foo\\bar", "foo\\.bar", "foo\\foobar"),
        listOf("foobarbar", "f.bar")
    )
    validateParsedExpression(
        "foo.*bar",
        PATTERN_SIMPLE_GLOB,
        "foo\\..*bar",
        listOf("foo.bar", "foo.foobar"),
        listOf("foobar", "foo.barbar")
    )
    // In this case "foo.bar.bar" does not match because the PATTERN_SIMPLE_GLOB matcher stops
    // consuming characters when wildcard matching if that character matches the character
    // immediately after the wildcard.
    validateParsedExpression(
        "foo*.bar",
        PATTERN_SIMPLE_GLOB,
        "foo.*\\.bar",
        listOf("foo.bar", "foobar.bar"),
        listOf("foobar", "foo.bar.bar")
    )
    validateParsedExpression(
        "*foo*bar",
        PATTERN_SIMPLE_GLOB,
        ".*foo.*bar",
        listOf(".foo.bar", "xfoozbar", "foobar", "foo.bar"),
        listOf("barfoo", "foobbar", "ffoobar")
    )
    validateParsedExpression(
        "foo?bar",
        PATTERN_SIMPLE_GLOB,
        "foo.bar",
        listOf("foo.bar", "foobbar"),
        listOf("foobar")
    )
    validateParsedExpression(
        "foo.?bar",
        PATTERN_SIMPLE_GLOB,
        "foo\\..bar",
        listOf("foo..bar", "foo.bbar"),
        listOf("foobar", "foo.bar")
    )
    validateParsedExpression(
        "foo?.bar",
        PATTERN_SIMPLE_GLOB,
        "foo.\\.bar",
        listOf("foo..bar", "fooo.bar"),
        listOf("foobar", "foo.bar")
    )
    validateParsedExpression(
        "?bar",
        PATTERN_SIMPLE_GLOB,
        ".bar",
        listOf(".bar", "fbar"),
        listOf("bar", "foobar")
    )
    validateParsedExpression(
        "foo?",
        PATTERN_SIMPLE_GLOB,
        "foo.",
        listOf("foo.", "foob"),
        listOf("foo", "foobar")
    )
    validateParsedExpression(
        "fo?b*r",
        PATTERN_SIMPLE_GLOB,
        "fo.b.*r",
        listOf("foobar", "fo.br", "fobbbbr"),
        listOf("foobrr", "fobar")
    )
    // In PATTERN_SIMPLE_GLOB it appears that a '.' following '.*' is treated as a literal instead
    // of a wildcard. This is likely a bug in PatternMatcher
    validateParsedExpression(
        "foo*?bar",
        PATTERN_SIMPLE_GLOB,
        "foo.*.bar",
        listOf("foo.bar", "foofoo.bar", "foobar.bar"),
        listOf("foobar", "foobarbar"))
    validateParsedExpression(
        "^fo+b*r",
        PATTERN_SIMPLE_GLOB,
        "^fo+b.*r",
        listOf("^fo+bar", "^fo+br", "^fo+bbbbr"),
        listOf("foobar", "fobr")
    )

    validateParsedExpression(
        "?*bar",
        PATTERN_SIMPLE_GLOB,
        "..*bar",
        listOf("bbar", ".bar", "foo.bar"),
        listOf("bar")
    )
    validateParsedExpression(
        "foo?*bar",
        PATTERN_SIMPLE_GLOB,
        "foo..*bar",
        listOf("foo.bar", "foodbar", "foofoobar", "foobarbar"),
        listOf("foobar", "foo.bar.bar")
    )
    validateParsedExpression(
        "foo?*bar*",
        PATTERN_SIMPLE_GLOB,
        "foo..*bar.*",
        listOf("foo.bar", "foodbar", "foo..bar", "foobarbar", "foo.bar.bar.foo"),
        listOf("foobar", "foo.ba.bar")
    )
    validateParsedExpression(
        "foo?*bar.",
        PATTERN_SIMPLE_GLOB,
        "foo..*bar\\.",
        listOf("foo.bar.", "foo..bar."),
        listOf("foobar.", "foo.bar\\.")
    )
    validateParsedExpression(
        "foo?*bar.*",
        PATTERN_SIMPLE_GLOB,
        "foo..*bar\\..*",
        listOf("foo.bar.", "foo..bar.", "foodbar.bar"),
        listOf("foobar.", "foo.bar")
    )
    validateParsedExpression(
        "bar?*",
        PATTERN_SIMPLE_GLOB,
        "bar..*",
        listOf("barfoo", "bar."),
        listOf("bar", "foobar")
    )

    // set matches are not supported in DAL
    validateParsedExpression(
        "foo[a-z]",
        PATTERN_LITERAL,
        "foo[a-z]",
        listOf("foo[a-z]"),
        listOf("fooa")
    )
    validateParsedExpression(
        "foo[a-z]+",
        PATTERN_LITERAL,
        "foo[a-z]+",
        listOf("foo[a-z]+"),
        listOf("foo[a-z]", "foozz")
    )
    validateParsedExpression(
        "foo[a-z]*",
        PATTERN_PREFIX,
        "foo[a-z]",
        listOf("foo[a-z]", "foo[a-z]bar"),
        listOf("foo", "fooz")
    )
    validateParsedExpression(
        "[a-z]*bar",
        PATTERN_SIMPLE_GLOB,
        "[a-z].*bar",
        listOf("[a-z].bar", "[a-z]bar"),
        listOf("abar")
    )
    validateParsedExpression(
        "foo[a-z]?bar",
        PATTERN_SIMPLE_GLOB,
        "foo[a-z].bar",
        listOf("foo[a-z].bar", "foo[a-z]bbar"),
        listOf("fooz.bar")
    )
    validateParsedExpression(
        "foo[a-z]?*",
        PATTERN_SIMPLE_GLOB,
        "foo[a-z]..*",
        listOf("foo[a-z]bar"),
        listOf("foodbar")
    )
    validateParsedExpression(
        "^fo+[a-z]?*",
        PATTERN_SIMPLE_GLOB,
        "^fo+[a-z]..*",
        listOf("^fo+[a-z]bar"),
        listOf("foobar", "foabar")
    )

    // range matches are not supported in DAL
    validateParsedExpression(
        "fo{2}",
        PATTERN_LITERAL,
        "fo{2}",
        listOf("fo{2}"),
        listOf("foo")
    )
    validateParsedExpression(
        "fo{2}+",
        PATTERN_LITERAL,
        "fo{2}+",
        listOf("fo{2}+"),
        listOf("foo")
    )
    validateParsedExpression(
        "fo{2}*",
        PATTERN_PREFIX,
        "fo{2}",
        listOf("fo{2}", "fo{2}bar"),
        listOf("foobar")
    )
    validateParsedExpression(
        "fo{2}*bar",
        PATTERN_SIMPLE_GLOB,
        "fo{2}.*bar",
        listOf("fo{2}.bar"),
        listOf("foo.bar")
    )
    validateParsedExpression(
        "fo{2}?*",
        PATTERN_SIMPLE_GLOB,
        "fo{2}..*",
        listOf("fo{2}.", "fo{2}bar"),
        listOf("foobar")
    )
  }

  @Test(expected = IllegalArgumentException::class)
  fun parseEmptyExpression() {
    parseMatchingExpression("")
  }

  private fun validateParsedExpression(
      given: String,
      expectedType: Int,
      expectedFilter: String,
      matchingPatterns: List<String>,
      nonMatchingPatterns: List<String>,
  ) {
    val (type, filter) = parseMatchingExpression(given)
    assertThat(filter).isEqualTo(expectedFilter)
    assertThat(type).isEqualTo(expectedType)
    assertThat(filter).isEqualTo(expectedFilter)
    val patternMatcher = PatternMatcher(filter, type)
    for (pattern in matchingPatterns) {
      assertWithMessage("PatternMacher for \"%s\" should pass", pattern)
          .that(patternMatcher.match(pattern))
          .isTrue()
    }
    for (pattern in nonMatchingPatterns) {
      assertWithMessage("PatternMacher for \"%s\" should fail", pattern)
          .that(patternMatcher.match(pattern))
          .isFalse()
    }
  }
}
