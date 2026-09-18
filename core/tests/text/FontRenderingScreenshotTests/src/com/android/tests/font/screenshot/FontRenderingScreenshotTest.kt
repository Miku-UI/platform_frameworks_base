/*
 * Copyright 2026 The Android Open Source Project
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

package com.android.tests.font.screenshot

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.TypefaceSpan
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import kotlin.math.ceil
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import platform.test.screenshot.matchers.MSSIMMatcher

private const val TESTDATA_DIR = "testdata"
private const val JSON_SUFFIX = ".json"

@RunWith(Parameterized::class)
class FontRenderingScreenshotTest(val p: Param) {

    data class Param(val locale: Locale?, val goldenId: String, val testString: String) {
        override fun toString() = goldenId
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun getParams(): List<Param> {
            val assets = InstrumentationRegistry.getInstrumentation().context.assets
            return requireNotNull(assets.list(TESTDATA_DIR))
                .filter { it.endsWith(JSON_SUFFIX) }
                .flatMap { parseJSON(it, assets) }
        }

        /**
         * Parses a JSON file from assets and converts it into a list of [Param] objects.
         *
         * @param jsonFile The name of the JSON file in the assets directory.
         * @param assets The [AssetManager] used to access the file.
         */
        private fun parseJSON(jsonFile: String, assets: AssetManager): List<Param> {
            val filePath = "$TESTDATA_DIR/$jsonFile"
            val json = JSONObject(assets.open(filePath).reader().use { it.readText() })
            val locale = if (json.has("locale")) {
                Locale.forLanguageTag(json.getString("locale"))
            } else {
                null
            }
            val goldenId = json.getString("golden_id")
            val testStrings = json.getJSONObject("test_strings")
            return testStrings.keys().asSequence().map { key ->
                Param(locale, "$goldenId-$key", testStrings.getString(key))
            }.toList()
        }
    }

    @get:Rule
    val screenshotRule = FontScreenshotTestRule()

    private fun populateStrings(): CharSequence {
        val builder = SpannableStringBuilder()

        fun addLine(name: String, weight: Int, italic: Boolean) {
            val start = builder.length
            if (start != 0) {
                builder.append('\n')
            }
            builder.append(p.testString)
            val typeface = Typeface.create(Typeface.create(name, Typeface.NORMAL), weight, italic)
            builder.setSpan(TypefaceSpan(typeface), start, builder.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        }

        // Priority weight variant
        addLine("sans-serif", 300, false)
        addLine("sans-serif", 400, false)
        addLine("sans-serif", 500, false)
        addLine("sans-serif", 700, false)
        addLine("sans-serif", 900, false)

        // Italic
        addLine("sans-serif", 400, true)
        addLine("sans-serif", 700, true)

        // Serif style
        addLine("serif", 400, false)
        addLine("serif", 700, false)
        addLine("serif", 400, true)
        addLine("serif", 700, true)

        // Monospace
        addLine("monospace", 400, false)

        return builder
    }

    @Test
    fun testDraw() {
        val paint = TextPaint().apply {
            color = Color.BLACK
            textSize = 36f
        }
        if (p.locale != null) {
            paint.textLocale = p.locale
        }

        val text = populateStrings()
        val width = ceil(Layout.getDesiredWidth(text, paint)).toInt()
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width).build()

        val bitmap = Bitmap.createBitmap(width, layout.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        layout.draw(canvas)
        screenshotRule.assertBitmapAgainstGolden(bitmap, p.goldenId, MSSIMMatcher())
    }
}
