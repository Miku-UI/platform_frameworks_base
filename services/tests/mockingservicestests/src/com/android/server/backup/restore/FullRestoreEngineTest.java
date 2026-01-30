/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.backup.restore;

import static com.android.server.backup.crossplatform.PlatformConfigParser.PLATFORM_IOS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.backup.BackupAgent;
import android.app.backup.FullBackup.BackupScheme.PlatformSpecificParams;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.platform.test.annotations.Presubmit;
import android.system.OsConstants;

import androidx.test.runner.AndroidJUnit4;

import com.android.server.backup.FileMetadata;
import com.android.server.backup.UserBackupManagerService;
import com.android.server.backup.crossplatform.CrossPlatformManifest;
import com.android.server.backup.utils.BackupEligibilityRules;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.List;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class FullRestoreEngineTest {
    private static final String DEFAULT_PACKAGE_NAME = "package";
    private static final String DEFAULT_DOMAIN_NAME = "domain";
    private static final String NEW_PACKAGE_NAME = "new_package";
    private static final String NEW_DOMAIN_NAME = "new_domain";

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    private FullRestoreEngine mRestoreEngine;

    @Mock private UserBackupManagerService mUserBackupManagerService;
    @Mock private PackageManager mPackageManager;
    @Mock private BackupEligibilityRules mBackupEligibilityRules;
    private PackageInfo mTargetPackage;

    @Before
    public void setUp() throws Exception {
        mTargetPackage = createPackageInfo("com.example.app1");
        when(mPackageManager.getApplicationInfoAsUser(anyString(), anyInt(), anyInt()))
                .thenReturn(mTargetPackage.applicationInfo);
        when(mUserBackupManagerService.getPackageManager()).thenReturn(mPackageManager);
        mRestoreEngine = new FullRestoreEngine(mUserBackupManagerService);
    }

    @Test
    public void shouldSkipReadOnlyDir_skipsAllReadonlyDirsAndTheirChildren() {
        // Create the file tree.
        TestFile[] testFiles =
                new TestFile[] {
                    TestFile.dir("root"),
                    TestFile.file("root/auth_token"),
                    TestFile.dir("root/media"),
                    TestFile.file("root/media/picture1.png"),
                    TestFile.file("root/push_token.txt"),
                    TestFile.dir("root/read-only-dir-1").markReadOnly().expectSkipped(),
                    TestFile.dir("root/read-only-dir-1/writable-subdir").expectSkipped(),
                    TestFile.file("root/read-only-dir-1/writable-subdir/writable-file")
                            .expectSkipped(),
                    TestFile.dir("root/read-only-dir-1/writable-subdir/read-only-subdir-2")
                            .markReadOnly()
                            .expectSkipped(),
                    TestFile.file("root/read-only-dir-1/writable-file").expectSkipped(),
                    TestFile.file("root/random-stuff.txt"),
                    TestFile.dir("root/database"),
                    TestFile.file("root/database/users.db"),
                    TestFile.dir("root/read-only-dir-2").markReadOnly().expectSkipped(),
                    TestFile.file("root/read-only-dir-2/writable-file-1").expectSkipped(),
                    TestFile.file("root/read-only-dir-2/writable-file-2").expectSkipped(),
                };

        assertCorrectItemsAreSkipped(testFiles);
    }

    @Test
    public void shouldSkipReadOnlyDir_onlySkipsChildrenUnderTheSamePackage() {
        TestFile[] testFiles =
                new TestFile[] {
                    TestFile.dir("read-only-dir").markReadOnly().expectSkipped(),
                    TestFile.file("read-only-dir/file").expectSkipped(),
                    TestFile.file("read-only-dir/file-from-different-package")
                            .setPackage(NEW_PACKAGE_NAME),
                };

        assertCorrectItemsAreSkipped(testFiles);
    }

    @Test
    public void shouldSkipReadOnlyDir_onlySkipsChildrenUnderTheSameDomain() {
        TestFile[] testFiles =
                new TestFile[] {
                    TestFile.dir("read-only-dir").markReadOnly().expectSkipped(),
                    TestFile.file("read-only-dir/file").expectSkipped(),
                    TestFile.file("read-only-dir/file-from-different-domain")
                            .setDomain(NEW_DOMAIN_NAME),
                };

        assertCorrectItemsAreSkipped(testFiles);
    }

    @Test
    public void findValidPlatformSpecificParams_nullManifest_returnsNull() {
        PlatformSpecificParams params =
                mRestoreEngine.findValidPlatformSpecificParams(
                        "com.example.app1", /* manifest= */ null, mBackupEligibilityRules);

        assertThat(params).isNull();
    }

    @Test
    public void findValidPlatformSpecificParams_withMatchingParams_returnsMatchingSourceParams()
            throws Exception {
        // Set up source
        PackageInfo sourcePackageInfo = createPackageInfo("com.example.app1");
        List<PlatformSpecificParams> sourceParams = new ArrayList<>();
        sourceParams.add(
                new PlatformSpecificParams("com.example.app1", "team-1", "source-version"));
        sourceParams.add(
                new PlatformSpecificParams("com.example.app2", "team-1", "source-version"));
        CrossPlatformManifest sourceManifest =
                CrossPlatformManifest.create(sourcePackageInfo, PLATFORM_IOS, sourceParams);
        // Set up target eligibility rules
        List<PlatformSpecificParams> targetParams = new ArrayList<>();
        targetParams.add(
                new PlatformSpecificParams("com.example.app1", "team-1", "target-version"));
        when(mBackupEligibilityRules.getPlatformSpecificParams(
                        eq(mTargetPackage.applicationInfo), eq(PLATFORM_IOS)))
                .thenReturn(targetParams);

        PlatformSpecificParams params =
                mRestoreEngine.findValidPlatformSpecificParams(
                        mTargetPackage.packageName, sourceManifest, mBackupEligibilityRules);

        assertThat(params).isNotNull();
        assertThat(params.getBundleId()).isEqualTo("com.example.app1");
        assertThat(params.getTeamId()).isEqualTo("team-1");
        assertThat(params.getContentVersion()).isEqualTo("source-version");
    }

    @Test
    public void findValidPlatformSpecificParams_noMatchingParams_returnsNull() {
        // Set up source
        PackageInfo sourcePackageInfo = createPackageInfo("com.example.app1");
        List<PlatformSpecificParams> sourceParams = new ArrayList<>();
        sourceParams.add(
                new PlatformSpecificParams("com.example.app1", "team-1", "source-version"));
        CrossPlatformManifest sourceManifest =
                CrossPlatformManifest.create(sourcePackageInfo, PLATFORM_IOS, sourceParams);
        // Set up target eligibility rules
        List<PlatformSpecificParams> targetParams = new ArrayList<>();
        targetParams.add(
                new PlatformSpecificParams("com.example.app1", "team-2", "target-version"));
        when(mBackupEligibilityRules.getPlatformSpecificParams(
                        eq(mTargetPackage.applicationInfo), eq(PLATFORM_IOS)))
                .thenReturn(targetParams);

        PlatformSpecificParams params =
                mRestoreEngine.findValidPlatformSpecificParams(
                        mTargetPackage.packageName, sourceManifest, mBackupEligibilityRules);

        assertThat(params).isNull();
    }

    private void assertCorrectItemsAreSkipped(TestFile[] testFiles) {
        // Verify all directories marked with .expectSkipped are skipped.
        for (TestFile testFile : testFiles) {
            boolean actualExcluded = mRestoreEngine.shouldSkipReadOnlyDir(testFile.mMetadata);
            boolean expectedExcluded = testFile.mShouldSkip;
            assertWithMessage(testFile.mMetadata.path)
                    .that(actualExcluded)
                    .isEqualTo(expectedExcluded);
        }
    }

    private static PackageInfo createPackageInfo(String packageName) {
        PackageInfo pkg = new PackageInfo();
        pkg.packageName = packageName;
        pkg.applicationInfo = new ApplicationInfo();
        pkg.applicationInfo.packageName = packageName;
        pkg.signingInfo = mock(SigningInfo.class);
        when(pkg.signingInfo.getApkContentsSigners()).thenReturn(new Signature[] {});
        return pkg;
    }

    private static class TestFile {
        private final FileMetadata mMetadata;
        private boolean mShouldSkip;

        static TestFile dir(String path) {
            return new TestFile(path, BackupAgent.TYPE_DIRECTORY);
        }

        static TestFile file(String path) {
            return new TestFile(path, BackupAgent.TYPE_FILE);
        }

        TestFile markReadOnly() {
            mMetadata.mode = 0;
            return this;
        }

        TestFile expectSkipped() {
            mShouldSkip = true;
            return this;
        }

        TestFile setPackage(String packageName) {
            mMetadata.packageName = packageName;
            return this;
        }

        TestFile setDomain(String domain) {
            mMetadata.domain = domain;
            return this;
        }

        private TestFile(String path, int type) {
            FileMetadata metadata = new FileMetadata();
            metadata.path = path;
            metadata.type = type;
            metadata.packageName = DEFAULT_PACKAGE_NAME;
            metadata.domain = DEFAULT_DOMAIN_NAME;
            metadata.mode = OsConstants.S_IWUSR; // Mark as writable.
            mMetadata = metadata;
        }
    }
}
