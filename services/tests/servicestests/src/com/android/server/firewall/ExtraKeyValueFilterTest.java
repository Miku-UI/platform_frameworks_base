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

package com.android.server.firewall;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;

@RunWith(AndroidJUnit4.class)
public class ExtraKeyValueFilterTest {

    private static final String EQUALS_KEY_FILTER_RULE = """
                <extra>
                  <key name="user_id"/>
                  <value startsWith="admin"/>
                </extra>
            """;

    private ExtraKeyValueFilter parseExtraFilter(String xml) throws Exception {
        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setInput(new StringReader(xml));
        parser.nextTag(); // <extra>
        return (ExtraKeyValueFilter) ExtraKeyValueFilter.FACTORY.newFilter(parser);
    }

    @Test
    public void testKeyFilterMatches_valueFilterMatches_success() throws Exception {
        ExtraKeyValueFilter filter = parseExtraFilter(EQUALS_KEY_FILTER_RULE);

        Intent intent = new Intent();
        intent.putExtra("user_id", "admin123");

        boolean result = filter.matches(null, null, intent, 0, 0, null, 0);
        assertTrue("Parsed ExtraKeyValueFilter should match the intent", result);
    }

    @Test
    public void testKeyFilterMatches_valueFilterDoesNotMatch_fail() throws Exception {
        ExtraKeyValueFilter filter = parseExtraFilter(EQUALS_KEY_FILTER_RULE);
        Intent intent = new Intent();
        intent.putExtra("user_id", "guest");

        boolean result = filter.matches(null, null, intent, 0, 0, null, 0);
        assertFalse("Parsed ExtraKeyValueFilter should not match the intent", result);
    }

    @Test
    public void testKeyFilterDoesNotMatch_valueFilterMatches_fail() throws Exception {
        ExtraKeyValueFilter filter = parseExtraFilter(EQUALS_KEY_FILTER_RULE);

        Intent intent = new Intent();
        intent.putExtra("user_id1", "admin2");

        boolean result = filter.matches(null, null, intent, 0, 0, null, 0);
        assertFalse("Parsed ExtraKeyValueFilter should not match the intent", result);
    }

    @Test
    public void testKeyValueFilterMixMatch_fail() throws Exception {
        ExtraKeyValueFilter filter = parseExtraFilter(EQUALS_KEY_FILTER_RULE);

        Intent intent = new Intent();
        intent.putExtra("user_id", "guest"); // matches key, but not value
        intent.putExtra("user_id_1", "admin123"); // matches value, but not key

        boolean result = filter.matches(null, null, intent, 0, 0, null, 0);
        assertFalse("Parsed ExtraKeyValueFilter should not match the intent", result);
    }

    @Test
    public void testKeyFilterMatches_valueFilterDoesNotMatchNonStringValue_fail()
            throws Exception {
        ExtraKeyValueFilter filter = parseExtraFilter(EQUALS_KEY_FILTER_RULE);
        Intent intent = new Intent();
        intent.putExtra("user_id", 1);

        boolean result = filter.matches(null, null, intent, 0, 0, null, 0);
        assertFalse("Parsed ExtraKeyValueFilter should not match the intent", result);
    }

    @Test(expected = XmlPullParserException.class)
    public void testWrongElementInExtraFilter_parseExtraFilter_fail() throws Exception {
        String xml = """
                    <extra>
                      <key name="user_id"/>
                      <value startsWith="admin"/>
                      <foo name="user_id"/>
                    </extra>
                """;
        parseExtraFilter(xml);
    }

    @Test(expected = XmlPullParserException.class)
    public void testDuplicateKeyElementInExtraFilter_parseExtraFilter_fail() throws Exception {
        String xml = """
                    <extra>
                      <key name="user_id"/>
                      <value startsWith="admin"/>
                      <key name="user_id2"/>
                    </extra>
                """;
        parseExtraFilter(xml);
    }

    @Test(expected = XmlPullParserException.class)
    public void testMissingKeyElementInExtraFilter_parseExtraFilter_fail() throws Exception {
        String xml = """
                    <extra>
                      <value startsWith="admin"/>
                    </extra>
                """;
        parseExtraFilter(xml);
    }
}
