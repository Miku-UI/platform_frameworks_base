/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.UserHandle;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

public class SenderPackageFilter implements Filter {
    private static final String ATTR_NAME = "name";

    public final String mPackageName;

    public SenderPackageFilter(String packageName) {
        mPackageName = packageName;
    }

    @Override
    public boolean matches(IntentFirewall ifw, ComponentName resolvedComponent, Intent intent,
            int callerUid, int callerPid, String resolvedType, int receivingUid) {
        return ifw.getPackageManager().isSameApp(mPackageName,
                PackageManager.MATCH_ANY_USER,
                callerUid,
                UserHandle.USER_SYSTEM);
    }

    public static final FilterFactory FACTORY = new FilterFactory("sender-package") {
        @Override
        public Filter newFilter(XmlPullParser parser)
                throws IOException, XmlPullParserException {
            String packageName = parser.getAttributeValue(null, ATTR_NAME);

            if (packageName == null) {
                throw new XmlPullParserException(
                    "A package name must be specified.", parser, null);
            }

            return new SenderPackageFilter(packageName);
        }
    };
}
