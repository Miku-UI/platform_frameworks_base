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

package android.content.res;

import android.platform.test.annotations.Postsubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;

@Postsubmit
@RunWith(AndroidJUnit4.class)
// This test could probably be run on Ravenwood if we include "ResApkBadXml.apk" as a "data".
// But for now, let's just disable it.
@android.platform.test.annotations.DisabledOnRavenwood(bug = 460514978)
public class AssetManagerTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String PUSH_FILE_DIR = "/data/local/tmp/tests/coretests/res/";

    private AssetManager mAm;

    @Before
    public void setUp() throws Exception {
        mAm = new AssetManager.Builder()
                      .addApkAssets(ApkAssets.loadFromPath(PUSH_FILE_DIR + "ResApkBadXml.apk"))
                      .build();
    }

    @Test
    @SmallTest
    @RequiresFlagsEnabled(Flags.FLAG_XML_FILE_SIZE_LIMIT)
    public void testTooLargeXmlLoadingFails() {
        var resources = new Resources(mAm, null, null);

        final var resId = 0x7f010000; // the first and only resource in the APK
        final var resName = "res/xml/huge.xml";

        Assert.assertThrows(Resources.NotFoundException.class, () -> resources.getXml(resId));

        var asset = mAm.getApkAssets()[mAm.getApkAssets().length - 1];
        Assert.assertThrows(FileNotFoundException.class, () -> asset.openXml(resName));
    }
}
