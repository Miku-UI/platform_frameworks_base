/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.mtp;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.os.StrictMode;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class MtpDatabaseDevicePropertyTest {
    private Context mContext;
    private MtpDatabase mDatabase;

    @Before
    public void setUp() {
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectIncorrectContextUse()
                .penaltyLog()
                .penaltyDeath()
                .build());

        mContext = ApplicationProvider.getApplicationContext();
        // MtpDatabase is usually created with MtpService context in the bug scenario.
        // Application context is also a non-visual context.
        mDatabase = new MtpDatabase(mContext, null);
    }

    @After
    public void tearDown() {
        if (mDatabase != null) {
            mDatabase.close();
            mDatabase = null;
        }
    }

    @Test
    public void testGetDevicePropertyImageSize_NonVisualContext() throws Exception {
        // Access the private getDeviceProperty method via reflection
        Method getDevicePropertyMethod = MtpDatabase.class.getDeclaredMethod(
                "getDeviceProperty", int.class, long[].class, char[].class);
        getDevicePropertyMethod.setAccessible(true);

        long[] outIntValue = new long[2];
        char[] outStringValue = new char[256];

        // This should not throw IllegalAccessException now that we use DisplayManager
        int result = (int) getDevicePropertyMethod.invoke(
                mDatabase, MtpConstants.DEVICE_PROPERTY_IMAGE_SIZE, outIntValue, outStringValue);

        assertEquals(MtpConstants.RESPONSE_OK, result);

        // Verify the format "WxH"
        int len = 0;
        while (len < outStringValue.length && outStringValue[len] != 0) {
            len++;
        }
        String resultString = new String(outStringValue, 0, len);
        Assert.assertTrue("Image size string format should be WxH, but was: " + resultString,
                resultString.matches("\\d+x\\d+"));

        String[] dimensions = resultString.split("x");
        int width = Integer.parseInt(dimensions[0]);
        int height = Integer.parseInt(dimensions[1]);
        Assert.assertTrue("Width should be > 0", width > 0);
        Assert.assertTrue("Height should be > 0", height > 0);
    }
}
