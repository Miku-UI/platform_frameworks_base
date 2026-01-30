/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.crashrecovery;


import static android.aconfig.Aconfig.flag_metadata.flag_storage_backend.DEVICE_CONFIG;
import static android.aconfig.Aconfig.flag_metadata.flag_storage_backend.UNSPECIFIED;

import static com.android.server.crashrecovery.CrashRecoveryUtils.NETWORK_STACK_CONNECTOR_CLASS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.LENIENT;

import android.aconfig.Aconfig.flag_metadata;
import android.aconfig.Aconfig.parsed_flag;
import android.aconfig.Aconfig.parsed_flags;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Environment;
import android.util.IndentingPrintWriter;
import android.util.Log;
import android.util.SparseArray;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;


/**
 * Test CrashRecovery Utils.
 */
@RunWith(AndroidJUnit4.class)
public class CrashRecoveryUtilsTest {

    private MockitoSession mStaticMockSession;
    private final String mLogMsg = "Logging from test";
    private final String mCrashrecoveryEventTag = "CrashRecovery Events: ";
    private File mCacheDir;
    private String mOriginalApexDirValue;
    private Field mApexDirField;
    private String mTempApexDir;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Mock
    private Context mMockContext;
    @Mock
    private PackageManager mMockPackageManager;

    @Before
    public void setup() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        mCacheDir = context.getCacheDir();
        mStaticMockSession = ExtendedMockito.mockitoSession()
                .spyStatic(Environment.class)
                .strictness(LENIENT)
                .startMocking();
        ExtendedMockito.doReturn(mCacheDir).when(() -> Environment.getDataDirectory());

        MockitoAnnotations.initMocks(this);

        // APEX_DIR override
        mTempApexDir = new File(tempFolder.getRoot(), "apex").getAbsolutePath();
        new File(mTempApexDir).mkdirs(); // Ensure the base temp apex dir exists

        mApexDirField = CrashRecoveryUtils.class.getDeclaredField("sApexDir");
        mApexDirField.setAccessible(true);
        mOriginalApexDirValue = (String) mApexDirField.get(null);
        mApexDirField.set(null, mTempApexDir);

        Mockito.when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);

        createCrashRecoveryEventsTempDir();
        createKeyValuesTempDir();
    }

    @After
    public void tearDown() throws Exception {
        mStaticMockSession.finishMocking();

        // Revert the value of APEX_DIR
        if (mApexDirField != null && mOriginalApexDirValue != null) {
            // Ensure the field is still accessible before setting it back
            mApexDirField.setAccessible(true);
            mApexDirField.set(null, mOriginalApexDirValue);
        }

        deleteCrashRecoveryEventsTempFile();
        deleteKeyValuesTempFile();
    }

    @Test
    public void testCrashRecoveryUtils() {
        testLogCrashRecoveryEvent();
        testDumpCrashRecoveryEvents();
    }

    @Test
    public void testDumpCrashRecoveryEventsWithoutAnyLogs() {
        assertThat(getCrashRecoveryEventsTempFile().exists()).isFalse();
        StringWriter sw = new StringWriter();
        IndentingPrintWriter ipw = new IndentingPrintWriter(sw, "  ");
        CrashRecoveryUtils.dumpCrashRecoveryEvents(ipw);
        ipw.close();

        String dump = sw.getBuffer().toString();
        assertThat(dump).contains(mCrashrecoveryEventTag);
        assertThat(dump).doesNotContain(mLogMsg);
    }

    @Test
    public void testKeyValuesUtils() throws IOException {
        SparseArray<String> keyValues = new SparseArray<>();
        keyValues.put(123, "com.android.foo");
        keyValues.put(456, "com.android.bar");
        keyValues.put(789, "com.android.zoo");

        testPutKeyValue(keyValues);
        testReadAllKeyValues(keyValues);

        SparseArray<String> newKeyValues = new SparseArray<>();
        newKeyValues.put(123, "com.android.foo2");
        newKeyValues.put(222, "com.android.bar2");

        testWriteAllKeyValues(newKeyValues);
        testReadAllKeyValues(newKeyValues);
    }

    @Test
    public void getFlagNamespacesInModules_noApexDir() {
        File apexDirInTemp = new File(mTempApexDir);
        apexDirInTemp.delete();

        Set<String> namespaces = CrashRecoveryUtils.getFlagNamespacesInModules();
        assertThat(namespaces).hasSize(2);
        assertThat(namespaces).containsExactly("com_android_networkstack",
                "com_android_captiveportallogin");
    }

    @Test
    public void getFlagNamespacesInModules_emptyApexDir() {
        File apexDir = new File(mTempApexDir);
        if (apexDir.exists()) {
            for (File f : apexDir.listFiles()) {
                if (f.isDirectory()) {
                    for (File sf : f.listFiles()) {
                        sf.delete();
                    }
                }
                f.delete();
            }
        } else {
            apexDir.mkdirs();
        }

        Set<String> namespaces = CrashRecoveryUtils.getFlagNamespacesInModules();
        assertThat(namespaces).hasSize(2);
        assertThat(namespaces).containsExactly("com_android_networkstack",
                "com_android_captiveportallogin");
    }

    @Test
    public void getFlagNamespacesInModules_apexWithNoProtoFile() throws IOException {
        createApexModule("com.android.foomodule", null);

        Set<String> namespaces = CrashRecoveryUtils.getFlagNamespacesInModules();
        assertThat(namespaces).hasSize(2);
        assertThat(namespaces).containsExactly("com_android_networkstack",
                "com_android_captiveportallogin");
    }

    @Test
    public void getFlagNamespacesInModules_malformedProtoFile() throws IOException {
        byte[] malformedBytes = "this is not a protobuf".getBytes();
        createApexModule("com.android.badproto", malformedBytes);

        Set<String> namespaces = CrashRecoveryUtils.getFlagNamespacesInModules();
        assertThat(namespaces).hasSize(2);
        assertThat(namespaces).containsExactly("com_android_networkstack",
                "com_android_captiveportallogin");
    }

    @Test
    public void getFlagNamespacesInModules_emptyProtoFile() throws IOException {
        byte[] emptyProtoBytes = parsed_flags.newBuilder().build().toByteArray();
        createApexModule("com.android.emptyproto", emptyProtoBytes);

        Set<String> namespaces = CrashRecoveryUtils.getFlagNamespacesInModules();
        assertThat(namespaces).hasSize(2);
        assertThat(namespaces).containsExactly("com_android_networkstack",
                "com_android_captiveportallogin");
    }

    @Test
    public void getFlagNamespacesInModules_flagWithoutDeviceConfigStorage_isIgnored()
            throws IOException {
        byte[] pbContent = createAconfigPb(Map.of("namespace.ignored", UNSPECIFIED));
        createApexModule("com.android.ignoredstorage", pbContent);

        Set<String> namespaces = CrashRecoveryUtils.getFlagNamespacesInModules();
        assertThat(namespaces).hasSize(2);
        assertThat(namespaces).containsExactly("com_android_networkstack",
                "com_android_captiveportallogin");
    }

    @Test
    public void getFlagNamespacesInModules_mixedStorageFlags() throws IOException {
        Map<String, flag_metadata.flag_storage_backend> flagData = new LinkedHashMap<>();
        flagData.put("namespace.device_config", DEVICE_CONFIG);
        flagData.put("namespace.unspecified_storage", UNSPECIFIED);
        // A null value in the map means the metadata field will not be set for that flag
        flagData.put("namespace.no_metadata_storage", null);

        byte[] pbContent = createAconfigPb(flagData);
        createApexModule("com.android.mixedstorage", pbContent);

        Set<String> namespaces = CrashRecoveryUtils.getFlagNamespacesInModules();
        assertThat(namespaces).hasSize(3);
        assertThat(namespaces).containsExactly("com_android_networkstack",
                "com_android_captiveportallogin", "namespace.device_config");
    }

    @Test
    public void getFlagNamespacesInModules_apexWithVersionedDir_shouldBeSkipped()
            throws IOException {
        createApexModule("com.android.foomodule@12345",
                createAconfigPb("ns.versioned"));
        createApexModule("com.android.foomodule",
                createAconfigPb("ns.good"));

        Set<String> namespaces = CrashRecoveryUtils.getFlagNamespacesInModules();
        assertThat(namespaces).hasSize(3);
        assertThat(namespaces).containsExactly("com_android_networkstack",
                "com_android_captiveportallogin", "ns.good");
    }

    @Test
    public void getFlagNamespacesInModules_singleApexWithOneNamespace() throws IOException {
        createApexModule("com.android.foomodule",
                createAconfigPb("namespace.one"));

        Set<String> namespaces = CrashRecoveryUtils.getFlagNamespacesInModules();
        assertThat(namespaces).hasSize(3);
        assertThat(namespaces).containsExactly("com_android_networkstack",
                "com_android_captiveportallogin", "namespace.one");
    }

    @Test
    public void getFlagNamespacesInModules_singleApexWithMultipleNamespaces() throws IOException {
        createApexModule("com.android.foomodule",
                createAconfigPb("namespace.a", "namespace.b"));

        Set<String> namespaces = CrashRecoveryUtils.getFlagNamespacesInModules();
        assertThat(namespaces).hasSize(4);
        assertThat(namespaces).containsExactly("com_android_networkstack",
                "com_android_captiveportallogin", "namespace.a", "namespace.b");
    }

    @Test
    public void getFlagNamespacesInModules_multipleApexesWithDistinctNamespaces()
            throws IOException {
        createApexModule("com.android.moduleA",
                createAconfigPb("namespace.A1", "namespace.A2"));
        createApexModule("com.android.moduleB",
                createAconfigPb("namespace.B1"));

        Set<String> namespaces = CrashRecoveryUtils.getFlagNamespacesInModules();
        assertThat(namespaces).hasSize(5);
        assertThat(namespaces).containsExactly("com_android_networkstack",
                "com_android_captiveportallogin", "namespace.A1", "namespace.A2", "namespace.B1");
    }

    @Test
    public void getFlagNamespacesInModules_multipleApexesWithOverlappingNamespaces()
            throws IOException {
        createApexModule("com.android.moduleA",
                createAconfigPb("common.ns", "unique.A"));
        createApexModule("com.android.moduleB",
                createAconfigPb("common.ns", "unique.B"));

        Set<String> namespaces = CrashRecoveryUtils.getFlagNamespacesInModules();
        assertThat(namespaces).hasSize(5);
        assertThat(namespaces).containsExactly("com_android_networkstack",
                "com_android_captiveportallogin", "common.ns", "unique.A", "unique.B");
    }

    @Test
    public void getNetworkStackPackageName_noServiceFound() {
        Intent expectedIntent = new Intent(NETWORK_STACK_CONNECTOR_CLASS);
        when(mMockPackageManager.queryIntentServices(any(Intent.class), anyInt()))
                .thenAnswer(invocation -> {
                    Intent intentArg = invocation.getArgument(0);
                    assertThat(intentArg.getAction()).isEqualTo(expectedIntent.getAction());
                    return Collections.emptyList();
                });

        String packageName = CrashRecoveryUtils.getNetworkStackPackageName(mMockContext);
        assertThat(packageName).isNull();
    }

    @Test
    public void getNetworkStackPackageName_oneNonSystemServiceFound() {
        ResolveInfo nonSystemRI = createMockResolveInfo("com.android.nonsystem",
                "NonSystemService", false);
        List<ResolveInfo> results = Collections.singletonList(nonSystemRI);

        when(mMockPackageManager.queryIntentServices(any(Intent.class), anyInt()))
                .thenReturn(results);

        String packageName = CrashRecoveryUtils.getNetworkStackPackageName(mMockContext);
        assertThat(packageName).isNull();
    }

    @Test
    public void getNetworkStackPackageName_oneSystemServiceFound() {
        String expectedPackage = "com.android.systemservice";
        String expectedService = "MySystemService";
        ResolveInfo systemRI = createMockResolveInfo(expectedPackage, expectedService, true);
        List<ResolveInfo> results = Collections.singletonList(systemRI);

        when(mMockPackageManager.queryIntentServices(any(Intent.class), anyInt()))
                .thenReturn(results);

        String packageName = CrashRecoveryUtils.getNetworkStackPackageName(mMockContext);
        assertThat(packageName).isEqualTo(expectedPackage);
    }

    @Test
    public void getNetworkStackPackageName_multipleSystemServicesFound() {
        ResolveInfo systemRI1 = createMockResolveInfo("com.android.system1", "Service1", true);
        ResolveInfo systemRI2 = createMockResolveInfo("com.android.system2", "Service2", true);
        List<ResolveInfo> results = Arrays.asList(systemRI1, systemRI2);

        when(mMockPackageManager.queryIntentServices(any(Intent.class), anyInt()))
                .thenReturn(results);

        String packageName = CrashRecoveryUtils.getNetworkStackPackageName(mMockContext);
        assertThat(packageName).isNull();
    }

    @Test
    public void getNetworkStackPackageName_systemAndNonSystemServicesFound_returnsSystem() {
        String expectedPackage = "com.android.system";
        ResolveInfo nonSystemRI = createMockResolveInfo("com.android.non", "NonService", false);
        ResolveInfo systemRI = createMockResolveInfo(expectedPackage, "SystemService", true);
        List<ResolveInfo> results = Arrays.asList(nonSystemRI, systemRI);

        when(mMockPackageManager.queryIntentServices(any(Intent.class), anyInt()))
                .thenReturn(results);

        String packageName = CrashRecoveryUtils.getNetworkStackPackageName(mMockContext);
        assertThat(packageName).isEqualTo(expectedPackage);
    }

    private void testLogCrashRecoveryEvent() {
        assertThat(getCrashRecoveryEventsTempFile().exists()).isFalse();
        CrashRecoveryUtils.logCrashRecoveryEvent(Log.WARN, mLogMsg);

        assertThat(getCrashRecoveryEventsTempFile().exists()).isTrue();
        String fileContent = null;
        try {
            File file = getCrashRecoveryEventsTempFile();
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            fis.close();
            fileContent = new String(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            fail("Unable to read the events file");
        }
        assertThat(fileContent).contains(mLogMsg);
    }

    private void testDumpCrashRecoveryEvents() {
        StringWriter sw = new StringWriter();
        IndentingPrintWriter ipw = new IndentingPrintWriter(sw, "  ");
        CrashRecoveryUtils.dumpCrashRecoveryEvents(ipw);
        ipw.close();

        String dump = sw.getBuffer().toString();
        assertThat(dump).contains(mCrashrecoveryEventTag);
        assertThat(dump).contains(mLogMsg);
    }

    private void testPutKeyValue(SparseArray<String> keyValues) throws IOException {
        final File keyValuesTempFile = getKeyValuesTempFile();
        assertThat(keyValuesTempFile.exists()).isFalse();

        if (keyValues.size() == 0) {
            return;
        }

        for (int i = 0; i < keyValues.size(); i++) {
            int key = keyValues.keyAt(i);
            String value = keyValues.get(key);
            CrashRecoveryUtils.putKeyValue(keyValuesTempFile, key, value);
        }

        final Path keyValuesTempFilePath = Paths.get(keyValuesTempFile.getAbsolutePath());
        try (Stream<String> lines = Files.lines(keyValuesTempFilePath, StandardCharsets.UTF_8)) {
            assertThat(lines.count()).isEqualTo(keyValues.size());
        }
    }

    private void testWriteAllKeyValues(SparseArray<String> keyValues) throws IOException {
        final File keyValuesTempFile = getKeyValuesTempFile();
        CrashRecoveryUtils.writeAllKeyValues(keyValuesTempFile, keyValues);

        final Path keyValuesTempFilePath = Paths.get(keyValuesTempFile.getAbsolutePath());
        try (Stream<String> lines = Files.lines(keyValuesTempFilePath, StandardCharsets.UTF_8)) {
            assertThat(lines.count()).isEqualTo(keyValues.size());
        }
    }

    private void testReadAllKeyValues(SparseArray<String> keyValues) {
        final File keyValuesTempFile = getKeyValuesTempFile();
        SparseArray<String> readKeyValues = CrashRecoveryUtils.readAllKeyValues(keyValuesTempFile);
        assertThat(readKeyValues.size()).isEqualTo(keyValues.size());
        for (int i = 0; i < keyValues.size(); i++) {
            int key = keyValues.keyAt(i);
            assertThat(readKeyValues.get(key)).isEqualTo(keyValues.get(key));
        }
    }

    private void createCrashRecoveryEventsTempDir() throws IOException {
        Files.deleteIfExists(getCrashRecoveryEventsTempFile().toPath());
        File mMockDirectory = new File(mCacheDir, "system");
        if (!mMockDirectory.exists()) {
            assertThat(mMockDirectory.mkdir()).isTrue();
        }
    }

    private void deleteCrashRecoveryEventsTempFile() throws IOException {
        Files.deleteIfExists(getCrashRecoveryEventsTempFile().toPath());
    }

    private File getCrashRecoveryEventsTempFile() {
        File systemTempDir = new File(mCacheDir, "system");
        return new File(systemTempDir, "crashrecovery-events.txt");
    }

    private void createKeyValuesTempDir() throws IOException {
        deleteKeyValuesTempFile();
        File mockDirectory = new File(mCacheDir, "rollback-observer");
        if (!mockDirectory.exists()) {
            assertThat(mockDirectory.mkdir()).isTrue();
        }
    }

    private void deleteKeyValuesTempFile() throws IOException {
        Files.deleteIfExists(getKeyValuesTempFile().toPath());
    }

    private File getKeyValuesTempFile() {
        File keyValuesTempDir = new File(mCacheDir, "rollback-observer");
        return new File(keyValuesTempDir, "key-values");
    }

    /**
     * Helper to create aconfig protobuf content with DEVICE_CONFIG storage.
     */
    private byte[] createAconfigPb(String... namespaces) {
        Map<String, flag_metadata.flag_storage_backend> flagData = new LinkedHashMap<>();
        for (String ns : namespaces) {
            flagData.put(ns, DEVICE_CONFIG);
        }
        return createAconfigPb(flagData);
    }

    /**
     * Flexible helper to create aconfig protobuf content from a map of namespaces to storage types
     * A null storage type indicates that the metadata field should not be set for that flag.
     */
    private byte[] createAconfigPb(Map<String, flag_metadata.flag_storage_backend> flagData) {
        parsed_flags.Builder flagsBuilder = parsed_flags.newBuilder();
        for (Map.Entry<String, flag_metadata.flag_storage_backend> entry : flagData.entrySet()) {
            String ns = entry.getKey();
            flag_metadata.flag_storage_backend storage = entry.getValue();

            parsed_flag.Builder flagBuilder = parsed_flag.newBuilder()
                    .setNamespace(ns)
                    .setName("test_flag_for_" + ns)
                    .setPackage("test.pkg");

            if (storage != null) {
                flagBuilder.setMetadata(
                        flag_metadata.newBuilder().setStorage(storage).build());
            }
            flagsBuilder.addParsedFlag(flagBuilder.build());
        }
        return flagsBuilder.build().toByteArray();
    }

    private void createApexModule(String moduleName, byte[] aconfigPbContent) throws IOException {
        File moduleDir = new File(new File(mTempApexDir), moduleName);
        moduleDir.mkdirs();
        File etcDir = new File(moduleDir, "etc");
        etcDir.mkdirs();
        File aconfigFile = new File(etcDir, "aconfig_flags.pb");

        if (aconfigPbContent != null) {
            try (FileOutputStream fos = new FileOutputStream(aconfigFile)) {
                fos.write(aconfigPbContent);
            }
        } else {
            // If content is null, ensure file doesn't exist for "no proto" tests
            if (aconfigFile.exists()) {
                aconfigFile.delete();
            }
        }
    }

    private ResolveInfo createMockResolveInfo(String packageName, String serviceName,
            boolean isSystem) {
        ResolveInfo ri = new ResolveInfo();
        ri.serviceInfo = new ServiceInfo();
        ri.serviceInfo.applicationInfo = new ApplicationInfo();
        ri.serviceInfo.applicationInfo.packageName = packageName;
        ri.serviceInfo.name = serviceName;
        if (isSystem) {
            ri.serviceInfo.applicationInfo.flags = ApplicationInfo.FLAG_SYSTEM;
        } else {
            ri.serviceInfo.applicationInfo.flags = 0;
        }
        return ri;
    }
}
