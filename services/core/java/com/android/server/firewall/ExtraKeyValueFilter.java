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
import static android.security.Flags.enableIntentFirewallExtraKeyValueFilter;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

import com.android.internal.util.XmlUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

class ExtraKeyValueFilter implements Filter {
    private static final String ATTR_NAME = "name";
    private final String mKeyFilter;
    private final StringFilter mValueFilter;

    private ExtraKeyValueFilter(String keyFilter, StringFilter valueFilter) {
        this.mKeyFilter = keyFilter;
        this.mValueFilter = valueFilter;
    }

    @Override
    public boolean matches(IntentFirewall ifw, ComponentName resolvedComponent, Intent intent,
            int callerUid, int callerPid, String resolvedType, int receivingUid) {
        if (!enableIntentFirewallExtraKeyValueFilter()) {
            return false;
        }
        Bundle extras = intent.getExtras();
        if (extras == null) return false;
        return mValueFilter.matchesValue(extras.getString(mKeyFilter));
    }

    public static final FilterFactory FACTORY = new FilterFactory("extra") {
        @Override
        public Filter newFilter(XmlPullParser parser) throws IOException, XmlPullParserException {
            String keyFilter = null;
            StringFilter valueFilter = null;

            final int depth = parser.getDepth();
            while (XmlUtils.nextElementWithin(parser, depth)) {
                String elementName = parser.getName();
                switch (elementName) {
                    case "key" -> {
                        if (keyFilter != null) {
                            throw new XmlPullParserException(
                                    "Multiple key elements found in an extra element");
                        }
                        keyFilter = parser.getAttributeValue(null, ATTR_NAME);
                    }
                    case "value" -> {
                        if (valueFilter != null) {
                            throw new XmlPullParserException(
                                    "Multiple value elements found in an extra element");
                        }
                        valueFilter = StringFilter.readFromXml(null, parser);
                    }
                    default -> throw new XmlPullParserException(
                                "Unknown element in extra rule: " + elementName);
                }
            }

            if (keyFilter == null || valueFilter == null) {
                throw new XmlPullParserException("<extra> must contain both <key> and <value>");
            }

            return new ExtraKeyValueFilter(keyFilter, valueFilter);
        }
    };

}
