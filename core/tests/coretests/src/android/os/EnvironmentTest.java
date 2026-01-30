/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.os;

import static android.os.Environment.HAS_ANDROID;
import static android.os.Environment.HAS_DCIM;
import static android.os.Environment.HAS_DOWNLOADS;
import static android.os.Environment.HAS_OTHER;
import static android.os.Environment.classifyExternalStorageDirectory;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.os.storage.StorageManager;
import android.platform.test.annotations.DisabledOnRavenwood;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.BiFunction;

@RunWith(AndroidJUnit4.class)
public class EnvironmentTest {
    private static final Context sContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    private File dir;

    private static Context getContext() {
        return sContext;
    }

    private static void assertAbsolute(String path) {
        assertThat(path).startsWith("/");
    }

    private static void assertAbsolute(File path) {
        assertAbsolute(path.getPath());
    }

    @Before
    public void setUp() throws Exception {
        dir = getContext().getDir("testing", Context.MODE_PRIVATE);
        FileUtils.deleteContents(dir);
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.deleteContents(dir);
    }

    @Test
    public void testClassify_empty() {
        assertEquals(0, classifyExternalStorageDirectory(dir));
    }

    @Test
    public void testClassify_emptyDirs() {
        Environment.buildPath(dir, "DCIM").mkdirs();
        Environment.buildPath(dir, "DCIM", "January").mkdirs();
        Environment.buildPath(dir, "Downloads").mkdirs();
        Environment.buildPath(dir, "LOST.DIR").mkdirs();
        assertEquals(0, classifyExternalStorageDirectory(dir));
    }

    @Test
    public void testClassify_emptyFactory() throws Exception {
        Environment.buildPath(dir, "autorun.inf").createNewFile();
        Environment.buildPath(dir, "LaunchU3.exe").createNewFile();
        Environment.buildPath(dir, "LaunchPad.zip").createNewFile();
        assertEquals(0, classifyExternalStorageDirectory(dir));
    }

    @Test
    public void testClassify_photos() throws Exception {
        Environment.buildPath(dir, "DCIM").mkdirs();
        Environment.buildPath(dir, "DCIM", "IMG_1024.JPG").createNewFile();
        Environment.buildPath(dir, "Download").mkdirs();
        Environment.buildPath(dir, "Download", "foobar.pdf").createNewFile();
        assertEquals(HAS_DCIM | HAS_DOWNLOADS, classifyExternalStorageDirectory(dir));
    }

    @Test
    public void testClassify_other() throws Exception {
        Environment.buildPath(dir, "Android").mkdirs();
        Environment.buildPath(dir, "Android", "com.example").mkdirs();
        Environment.buildPath(dir, "Android", "com.example", "internal.dat").createNewFile();
        Environment.buildPath(dir, "Linux").mkdirs();
        Environment.buildPath(dir, "Linux", "install-amd64-minimal-20170907.iso").createNewFile();
        assertEquals(HAS_ANDROID | HAS_OTHER, classifyExternalStorageDirectory(dir));
    }

    @Test
    public void testClassify_otherRoot() throws Exception {
        Environment.buildPath(dir, "Taxes.pdf").createNewFile();
        assertEquals(HAS_OTHER, classifyExternalStorageDirectory(dir));
    }

    @Test
    @DisabledOnRavenwood(blockedBy = StorageManager.class)
    public void testDataCePackageDirectoryForUser() {
        testDataPackageDirectoryForUser(
                (uuid, userHandle) -> Environment.getDataCePackageDirectoryForUser(
                        uuid, userHandle, getContext().getPackageName()),
                (uuid, user) -> Environment.getDataUserCePackageDirectory(
                        uuid, user, getContext().getPackageName())
        );
    }

    @Test
    @DisabledOnRavenwood(blockedBy = StorageManager.class)
    public void testDataDePackageDirectoryForUser() {
        testDataPackageDirectoryForUser(
                (uuid, userHandle) -> Environment.getDataDePackageDirectoryForUser(
                        uuid, userHandle, getContext().getPackageName()),
                (uuid, user) -> Environment.getDataUserDePackageDirectory(
                        uuid, user, getContext().getPackageName())
        );
    }

    private void testDataPackageDirectoryForUser(
            BiFunction<UUID, UserHandle, File> publicApi,
            BiFunction<String, Integer, File> hideApi) {
        var uuids = new ArrayList<String>();
        uuids.add(null); // Private internal
        uuids.add("primary_physical");
        uuids.add("system");
        uuids.add("3939-3939"); // FAT Volume
        uuids.add("57554103-df3e-4475-ae7a-8feba49353ac"); // Random valid UUID
        var userHandle = UserHandle.of(0);

        // Check that the @hide method is consistent with the public API
        for (String uuid : uuids) {
            assertThat(publicApi.apply(StorageManager.convert(uuid), userHandle))
                    .isEqualTo(hideApi.apply(uuid, 0));
        }
    }

    // In the following tests, we just make sure the APIs don't throw,
    // and returned paths are absolute.

    @Test
    public void testGetUserSystemDirectory() {
        assertAbsolute(Environment.getUserSystemDirectory(UserHandle.USER_SYSTEM));
    }

    @Test
    public void testGetMetadataDirectory() {
        assertAbsolute(Environment.getMetadataDirectory());
    }

    @Test
    public void testGetUserConfigDirectory() {
        assertAbsolute(Environment.getUserConfigDirectory(UserHandle.USER_SYSTEM));
    }

    @Test
    public void testGetDataDirectory_withVolumeUuid() {
        String volumeUuid = null; // For default internal st"xxx"e
        assertAbsolute(Environment.getDataDirectory(volumeUuid));
    }

    @Test
    public void testGetDataDirectoryPath_withVolumeUuid() {
        String volumeUuid = null; // For default internal st"xxx"e
        assertAbsolute(Environment.getDataDirectoryPath(volumeUuid));
    }

    @Test
    public void testGetExpandDirectory() {
        assertAbsolute(Environment.getExpandDirectory());
    }

    @Test
    public void testGetDataSystemDirectory() {
        assertAbsolute(Environment.getDataSystemDirectory());
    }

    @Test
    public void testGetDataSystemCeDirectory_noArgs() {
        // This specific overload might be deprecated or less common.
        // The one with UserHandle.USER_SYSTEM is generally preferred.
        assertAbsolute(Environment.getDataSystemCeDirectory());
    }

    @Test
    public void testGetDataSystemCeDirectory_withUserId() {
        assertAbsolute(Environment.getDataSystemCeDirectory(UserHandle.USER_SYSTEM));
    }

    @Test
    public void testGetDataSystemDeDirectory_withUserId() {
        assertAbsolute(Environment.getDataSystemDeDirectory(UserHandle.USER_SYSTEM));
    }

    @Test
    public void testGetDataMiscDirectory() {
        assertAbsolute(Environment.getDataMiscDirectory());
    }

    @Test
    public void testGetDataMiscCeDirectory_noArgs() {
        assertAbsolute(Environment.getDataMiscCeDirectory());
    }

    @Test
    public void testGetDataMiscCeDirectory_withUserId() {
        assertAbsolute(Environment.getDataMiscCeDirectory(UserHandle.USER_SYSTEM));
    }

    @Test
    public void testGetDataMiscCeSharedSdkSandboxDirectory() {
        String volumeUuid = "xxx";
        String packageName = sContext.getPackageName();
        assertAbsolute(Environment.getDataMiscCeSharedSdkSandboxDirectory(
                volumeUuid, UserHandle.USER_SYSTEM, packageName));
    }

    @Test
    public void testGetDataMiscDeDirectory_withUserId() {
        assertAbsolute(Environment.getDataMiscDeDirectory(UserHandle.USER_SYSTEM));
    }

    @Test
    public void testGetDataMiscDeSharedSdkSandboxDirectory() {
        String volumeUuid = "xxx";
        String packageName = sContext.getPackageName();
        assertAbsolute(Environment.getDataMiscDeSharedSdkSandboxDirectory(
                volumeUuid, UserHandle.USER_SYSTEM, packageName));
    }

    @Test
    public void testGetDataVendorCeDirectory() {
        assertAbsolute(Environment.getDataVendorCeDirectory(UserHandle.USER_SYSTEM));
    }

    @Test
    public void testGetDataVendorDeDirectory() {
        assertAbsolute(Environment.getDataVendorDeDirectory(UserHandle.USER_SYSTEM));
    }

    @Test
    public void testGetDataRefProfilesDePackageDirectory() {
        String packageName = sContext.getPackageName();
        assertAbsolute(Environment.getDataRefProfilesDePackageDirectory(packageName));
    }

    @Test
    public void testGetDataProfilesDePackageDirectory() {
        String packageName = sContext.getPackageName();
        assertAbsolute(Environment.getDataProfilesDePackageDirectory(
                UserHandle.USER_SYSTEM, packageName));
    }

    @Test
    public void testGetDataAppDirectory() {
        String volumeUuid = "xxx";
        assertAbsolute(Environment.getDataAppDirectory(volumeUuid));
    }

    @Test
    public void testGetDataStagingDirectory() {
        String volumeUuid = "xxx";
        assertAbsolute(Environment.getDataStagingDirectory(volumeUuid));
    }

    @Test
    public void testGetDataUserCeDirectory_withVolumeUuid() {
        String volumeUuid = "xxx";
        assertAbsolute(Environment.getDataUserCeDirectory(volumeUuid));
    }

    @Test
    public void testGetDataUserCeDirectory_withVolumeUuidAndUserId() {
        String volumeUuid = "xxx";
        assertAbsolute(Environment.getDataUserCeDirectory(volumeUuid, UserHandle.USER_SYSTEM));
    }

    @Test
    public void testGetDataUserCePackageDirectory() {
        String volumeUuid = "xxx";
        String packageName = sContext.getPackageName();
        assertAbsolute(Environment.getDataUserCePackageDirectory(
                volumeUuid, UserHandle.USER_SYSTEM, packageName));
    }

    @Test
    public void testGetDataUserDeDirectory_withVolumeUuid() {
        String volumeUuid = "xxx";
        assertAbsolute(Environment.getDataUserDeDirectory(volumeUuid));
    }

    @Test
    public void testGetDataUserDeDirectory_withVolumeUuidAndUserId() {
        String volumeUuid = "xxx";
        assertAbsolute(Environment.getDataUserDeDirectory(volumeUuid, UserHandle.USER_SYSTEM));
    }

    @Test
    public void testGetDataUserDePackageDirectory() {
        String volumeUuid = "xxx";
        String packageName = sContext.getPackageName();
        assertAbsolute(Environment.getDataUserDePackageDirectory(
                volumeUuid, UserHandle.USER_SYSTEM, packageName));
    }

    @Test
    public void testGetDataPreloadsDirectory() {
        assertAbsolute(Environment.getDataPreloadsDirectory());
    }

    @Test
    public void testGetDataPreloadsDemoDirectory() {
        assertAbsolute(Environment.getDataPreloadsDemoDirectory());
    }

    @Test
    public void testGetDataPreloadsAppsDirectory() {
        assertAbsolute(Environment.getDataPreloadsAppsDirectory());
    }

    @Test
    public void testGetDataPreloadsMediaDirectory() {
        assertAbsolute(Environment.getDataPreloadsMediaDirectory());
    }

    @Test
    public void testGetDataPreloadsFileCacheDirectory_withPackageName() {
        String packageName = sContext.getPackageName();
        assertAbsolute(Environment.getDataPreloadsFileCacheDirectory(packageName));
    }

    @Test
    public void testGetDataPreloadsFileCacheDirectory_noArgs() {
        assertAbsolute(Environment.getDataPreloadsFileCacheDirectory());
    }

    @Test
    public void testGetPackageCacheDirectory() {
        assertAbsolute(Environment.getPackageCacheDirectory());
    }
}
