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

package com.android.server.devicepolicy;

import android.text.TextUtils;
import android.util.IndentingPrintWriter;

import com.android.internal.util.XmlUtils;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;
import com.android.server.utils.Slogf;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * An admin record contains non-policy information associated with an admin app.
 *
 * <p>Admin records are identified by the package name of the admin app.
 */
class AdminRecord {
    private static final String TAG_ORGANIZATION_ID = "organization-id";
    private static final String TAG_ENROLLMENT_SPECIFIC_ID = "enrollment-specific-id";
    private static final String LOG_TAG = DevicePolicyManagerService.LOG_TAG;

    final String mPackageName;

    String mOrganizationId;
    String mEnrollmentSpecificId;

    AdminRecord(String packageName) {
        mPackageName = packageName;
    }

    void dump(IndentingPrintWriter pw) {
        pw.println();
        pw.println("AdminRecord (package: " + mPackageName + "):");
        pw.increaseIndent();
        pw.print("mOrganizationId=");
        pw.println(mOrganizationId);
        pw.print("mEnrollmentSpecificId=");
        pw.println(mEnrollmentSpecificId);
        pw.decreaseIndent();
    }

    void writeTextToXml(TypedXmlSerializer out, String tag, String text) throws IOException {
        out.startTag(null, tag);
        out.text(text);
        out.endTag(null, tag);
    }

    void writeToXml(TypedXmlSerializer serializer) throws IOException {
        if (!TextUtils.isEmpty(mOrganizationId)) {
            writeTextToXml(serializer, TAG_ORGANIZATION_ID, mOrganizationId);
        }
        if (!TextUtils.isEmpty(mEnrollmentSpecificId)) {
            writeTextToXml(serializer, TAG_ENROLLMENT_SPECIFIC_ID, mEnrollmentSpecificId);
        }
    }

    void readFromXml(TypedXmlPullParser parser) throws XmlPullParserException, IOException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }
            String tag = parser.getName();
            if (TAG_ORGANIZATION_ID.equals(tag)) {
                type = parser.next();
                if (type == TypedXmlPullParser.TEXT) {
                    mOrganizationId = parser.getText();
                } else {
                    Slogf.wtf(LOG_TAG, "Invalid Organization ID for admin %s.", mPackageName);
                }
            } else if (TAG_ENROLLMENT_SPECIFIC_ID.equals(tag)) {
                type = parser.next();
                if (type == TypedXmlPullParser.TEXT) {
                    mEnrollmentSpecificId = parser.getText();
                } else {
                    Slogf.wtf(
                            LOG_TAG, "Invalid Enrollment-specific ID for admin %s.", mPackageName);
                }
            } else {
                Slogf.wtf(LOG_TAG, "Unknown AdminRecord tag for admin %s: %s", mPackageName, tag);
                XmlUtils.skipCurrentTag(parser);
            }
        }
    }
}
