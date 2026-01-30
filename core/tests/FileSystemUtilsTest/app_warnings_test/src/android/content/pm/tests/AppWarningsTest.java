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

package android.content.pm.tests;

import android.Manifest;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class AppWarningsTest {

    private static final String TEST_APK_PATH =
            "/data/local/tmp/pagesizewarnings/app_with_4kb_elf_no_override_debuggable.apk";
    private static final String PACKAGE_INSTALLED_ACTION =
            "com.example.android.testing.PACKAGE_INSTALLED";
    private static final String WARNING_TEXT = "Android App Compatibility";
    private static final long TIMEOUT = 5000;
    private static final String TEST_APP_PACKAGE = "android.test.pagesizecompat";

    private static final long INSTALL_TIMEOUT_SECONDS = 60;
    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();

    private void enablePermissions() throws Exception {
        mInstrumentation
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.INSTALL_PACKAGES,
                        Manifest.permission.REQUEST_INSTALL_PACKAGES,
                        Manifest.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION,
                        Manifest.permission.DELETE_PACKAGES);
    }

    @Test
    public void installAppViaSession() throws Exception {
        enablePermissions();

        Context context = mInstrumentation.getContext();
        PackageManager packageManager = context.getPackageManager();
        PackageInstaller packageInstaller = packageManager.getPackageInstaller();

        // 1. Create a PackageInstaller session
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int sessionId = packageInstaller.createSession(params);
        PackageInstaller.Session session = packageInstaller.openSession(sessionId);

        // 2. Write the APK to the session
        try (OutputStream out = session.openWrite("test_apk", 0, -1);
                InputStream in = new FileInputStream(new File(TEST_APK_PATH))) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) != -1) {
                out.write(buffer, 0, length);
            }
            session.fsync(out);
        }

        // 3. Create a PendingIntent for the installation result
        CountDownLatch latch = new CountDownLatch(1);
        IntentFilter intentFilter = new IntentFilter(PACKAGE_INSTALLED_ACTION);
        BroadcastReceiver receiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        int status =
                                intent.getIntExtra(
                                        PackageInstaller.EXTRA_STATUS,
                                        PackageInstaller.STATUS_FAILURE);
                        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                            packageInstaller.setPermissionsResult(sessionId, true);
                        } else {
                            Assert.assertEquals(PackageInstaller.STATUS_SUCCESS, status);
                            latch.countDown();
                        }
                    }
                };
        context.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED);

        Intent intent = new Intent(PACKAGE_INSTALLED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);

        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        context,
                        sessionId,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                                | PendingIntent.FLAG_MUTABLE
                                | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT);

        // 4. Commit the session
        session.commit(pendingIntent.getIntentSender());
        session.close();

        // 5. Wait for the installation to complete
        if (!latch.await(INSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            // Fail the test due to timeout
            throw new Exception("Time out occurred while installing app.");
        }

        context.unregisterReceiver(receiver);
    }

    private void launchTestApp() throws Exception {
        Context context = mInstrumentation.getContext();

        Intent launchIntent =
                context.getPackageManager().getLaunchIntentForPackage(TEST_APP_PACKAGE);
        context.startActivity(launchIntent);

        UiDevice device = UiDevice.getInstance(mInstrumentation);
        device.waitForWindowUpdate(null, TIMEOUT);
    }

    private UiObject2 findCompatWaring() {
        UiDevice device = UiDevice.getInstance(mInstrumentation);
        device.waitForWindowUpdate(null, TIMEOUT);
        UiObject2 object = device.wait(Until.findObject(By.text(WARNING_TEXT)), TIMEOUT);
        return object;
    }

    @Test
    public void testWarnings() throws Exception {
        // install this using target prerparer
        launchTestApp();
        Assert.assertTrue(findCompatWaring() != null);
    }

    @Test
    public void testNoWarnings() throws Exception {
        launchTestApp();
        // verify there is no warning dialog
        Assert.assertTrue(findCompatWaring() == null);
    }
}
