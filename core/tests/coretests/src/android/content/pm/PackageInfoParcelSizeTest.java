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

package android.content.pm;

import android.app.Activity;
import android.os.Bundle;
import android.os.Parcel;
import android.text.TextUtils;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class PackageInfoParcelSizeTest {

    @Test
    public void testCorrectness() {
        PackageInfo original = createBloatedPackageInfo(true);
        assertParcelingPreservesData(original);

        PackageInfo simulatedGms = createSimulatedGmsCore();
        assertParcelingPreservesData(simulatedGms);
    }

    private void assertParcelingPreservesData(PackageInfo original) {
        Parcel p = Parcel.obtain();
        original.writeToParcel(p, 0);
        p.setDataPosition(0);

        PackageInfo restored = PackageInfo.CREATOR.createFromParcel(p);
        p.recycle();

        // Verify ApplicationInfo match
        assertEquals(original.applicationInfo.packageName, restored.applicationInfo.packageName);

        // Verify ActivityInfo match
        if (original.activities != null && original.activities.length > 0) {
            ActivityInfo origActivity = original.activities[0];
            ActivityInfo restoredActivity = restored.activities[0];

            assertEquals("Name mismatch", origActivity.name, restoredActivity.name);
            assertEquals("Icon mismatch", origActivity.icon, restoredActivity.icon);
            assertEquals("Logo mismatch", origActivity.logo, restoredActivity.logo);
            assertEquals("Banner mismatch", origActivity.banner, restoredActivity.banner);
            assertEquals("LabelRes mismatch", origActivity.labelRes, restoredActivity.labelRes);

            // Verify metadata bundle content equality
            if (origActivity.metaData != null) {
                String key = origActivity.metaData.keySet().iterator().next();
                assertEquals("Metadata mismatch",
                    origActivity.metaData.getString(key),
                    restoredActivity.metaData.getString(key));
            }
        }
    }

    @Test
    public void testParcelSize() {
        PackageInfo pi = createBloatedPackageInfo(true);

        Parcel p = Parcel.obtain();
        pi.writeToParcel(p, 0);
        int size = p.dataSize();
        p.recycle();

        reportSize("bloated", size);
    }

    private void reportSize(String prefix, long currentSize) {
        Bundle status = new Bundle();
        status.putLong(prefix + "_bytes", currentSize);
        InstrumentationRegistry.getInstrumentation().sendStatus(Activity.RESULT_OK, status);
    }

    @Test
    public void testSimulatedGmsCore() {
        PackageInfo pi = createSimulatedGmsCore();

        Parcel p = Parcel.obtain();
        pi.writeToParcel(p, 0);
        int currentSize = p.dataSize();
        p.recycle();

        reportSize("gms_simulated", currentSize);
    }

    @Test
    public void testRealAppParcelSize() throws Exception {
        // This will likely fail on host/ravenwood without the apps, so we wrap in try-catch
        try {
            android.content.Context context =
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                            .getContext();
            PackageManager pm = context.getPackageManager();

            String[] apps = {
                "com.google.android.gms",
                "com.google.android.googlequicksearchbox",
                "com.google.android.youtube"
            };

            for (String packageName : apps) {
                try {
                    PackageInfo pi =
                            pm.getPackageInfo(
                                    packageName,
                                    PackageManager.GET_ACTIVITIES
                                            | PackageManager.GET_SERVICES
                                            | PackageManager.GET_PROVIDERS
                                            | PackageManager.GET_RECEIVERS
                                            | PackageManager.GET_META_DATA
                                            | PackageManager.GET_PERMISSIONS);

                    Parcel p = Parcel.obtain();
                    pi.writeToParcel(p, 0);
                    int size = p.dataSize();
                    p.recycle();

                    reportSize("real_" + packageName.replace(".", "_"), size);

                } catch (PackageManager.NameNotFoundException e) {
                    Log.w("PackageInfoParcelSizeTest", "Package not found: " + packageName);
                }
            }
        } catch (Throwable t) {
            Log.w("PackageInfoParcelSizeTest", "Skipping real app test: " + t.getMessage());
        }
    }

    private PackageInfo createBloatedPackageInfo(boolean shareMetaData) {
        PackageInfo pi = new PackageInfo();
        pi.packageName = "com.example.bloated";
        pi.versionCode = 123;
        pi.versionName = "1.2.3";

        // ApplicationInfo
        pi.applicationInfo = new ApplicationInfo();
        pi.applicationInfo.packageName = pi.packageName;
        pi.applicationInfo.className = "com.example.bloated.MainApp";
        pi.applicationInfo.permission = "com.example.bloated.PERMISSION";
        pi.applicationInfo.labelRes = 123;
        pi.applicationInfo.icon = 456;

        // Bloated Metadata
        android.os.Bundle metaData = new android.os.Bundle();
        for (int i = 0; i < 100; i++) {
            metaData.putString(
                    "key_" + i, "This is a reasonably long string value for metadata item " + i);
        }
        pi.applicationInfo.metaData = metaData;

        // Activities
        int numActivities = 1000;
        pi.activities = new ActivityInfo[numActivities];
        for (int i = 0; i < numActivities; i++) {
            ActivityInfo ai = new ActivityInfo();
            ai.packageName = pi.packageName;
            ai.name = "com.example.bloated.Activity" + i;
            ai.labelRes = i % 2 == 0 ? 123 : i;
            ai.icon = i % 2 == 0 ? 456 : i;
            ai.nonLocalizedLabel = "Activity " + i;
            ai.applicationInfo = pi.applicationInfo;
            if (shareMetaData) {
                ai.metaData = metaData;
            } else {
                ai.metaData = new android.os.Bundle(metaData);
            }
            pi.activities[i] = ai;
        }
        return pi;
    }

    private PackageInfo createSimulatedGmsCore() {
        PackageInfo pi = new PackageInfo();
        pi.packageName = "com.google.android.gms";
        pi.versionCode = 23000000;
        pi.versionName = "23.00.00";

        pi.applicationInfo = new ApplicationInfo();
        pi.applicationInfo.packageName = pi.packageName;
        pi.applicationInfo.labelRes = 101010;
        pi.applicationInfo.icon = 202020;
        pi.applicationInfo.targetSdkVersion = 34;

        // Simulate GMS Core metadata
        android.os.Bundle metaData = new android.os.Bundle();
        metaData.putInt("com.google.android.gms.version", 12345678);
        metaData.putString("com.google.android.gms.car.application", "true");
        // Add some more realistic looking keys
        for (int i = 0; i < 50; i++) {
            metaData.putString("com.google.android.gms.feature_flag_" + i, "value_" + i);
        }
        pi.applicationInfo.metaData = metaData;

        // Activities (~500)
        pi.activities = new ActivityInfo[500];
        for (int i = 0; i < 500; i++) {
            pi.activities[i] = createComponent(i, "Activity", pi.applicationInfo, metaData);
        }

        // Services (~400)
        pi.services = new ServiceInfo[400];
        for (int i = 0; i < 400; i++) {
            pi.services[i] = createService(i, pi.applicationInfo, metaData);
        }

        // Providers (~100)
        pi.providers = new ProviderInfo[100];
        for (int i = 0; i < 100; i++) {
            pi.providers[i] = createProvider(i, pi.applicationInfo, metaData);
        }

        // Receivers (~100)
        pi.receivers = new ActivityInfo[100];
        for (int i = 0; i < 100; i++) {
            pi.receivers[i] = createComponent(i, "Receiver", pi.applicationInfo, metaData);
        }

        return pi;
    }

    private ActivityInfo createComponent(
            int i, String type, ApplicationInfo appInfo, android.os.Bundle sharedMeta) {
        ActivityInfo ai = new ActivityInfo();
        ai.packageName = appInfo.packageName;
        ai.name = appInfo.packageName + "." + type + i;
        ai.applicationInfo = appInfo;
        ai.metaData = sharedMeta;
        ai.labelRes = appInfo.labelRes; // Often shared
        ai.icon = appInfo.icon; // Often shared
        ai.exported = i % 2 == 0;
        ai.enabled = true;
        return ai;
    }

    private ServiceInfo createService(
            int i, ApplicationInfo appInfo, android.os.Bundle sharedMeta) {
        ServiceInfo si = new ServiceInfo();
        si.packageName = appInfo.packageName;
        si.name = appInfo.packageName + ".Service" + i;
        si.applicationInfo = appInfo;
        si.metaData = sharedMeta;
        si.labelRes = 0; // Services often don't have labels
        si.exported = i % 3 == 0;
        return si;
    }

    private ProviderInfo createProvider(
            int i, ApplicationInfo appInfo, android.os.Bundle sharedMeta) {
        ProviderInfo pi = new ProviderInfo();
        pi.packageName = appInfo.packageName;
        pi.name = appInfo.packageName + ".Provider" + i;
        pi.authority = appInfo.packageName + ".provider" + i;
        pi.applicationInfo = appInfo;
        pi.metaData = sharedMeta;
        return pi;
    }
}
