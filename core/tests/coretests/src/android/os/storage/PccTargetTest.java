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

package android.os.storage;

import static com.google.common.truth.Truth.assertThat;

import android.os.Environment;
import android.os.storage.operations.targets.OperationTargetWrapper;
import android.os.storage.operations.targets.PccTarget;
import android.platform.test.annotations.Presubmit;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class PccTargetTest {

    @Test
    public void testParceling() {
        PccTarget target = new PccTarget("subdir/logs");

        OperationTargetWrapper wrapper = new OperationTargetWrapper(target);
        android.os.Parcel parcel = android.os.Parcel.obtain();
        wrapper.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        OperationTargetWrapper unparceledWrapper =
                OperationTargetWrapper.CREATOR.createFromParcel(parcel);
        PccTarget unparceledTarget = (PccTarget) unparceledWrapper.getWrappedTarget();

        assertThat(unparceledTarget.toString()).isEqualTo(target.toString());
    }

    @Test
    public void testIsValid_validPrefixes() {
        // Empty prefix
        assertThat(new PccTarget().isValid()).isTrue();
        assertThat(new PccTarget("").isValid()).isTrue();

        // Simple relative paths
        assertThat(new PccTarget("files").isValid()).isTrue();
        assertThat(new PccTarget("images/thumbnails").isValid()).isTrue();
        assertThat(new PccTarget("a/b/c/d").isValid()).isTrue();
    }

    @Test
    public void testIsValid_invalidPrefixes_absolute() {
        // Absolute paths must be rejected as they ignore the root
        assertThat(new PccTarget("/etc/passwd").isValid()).isFalse();
        assertThat(new PccTarget("/data/data/com.example/files").isValid()).isFalse();
        assertThat(new PccTarget("/").isValid()).isFalse();
    }

    @Test
    public void testIsValid_invalidPrefixes_pathTraversal() {
        // ".." component
        assertThat(new PccTarget("files/../secrets").isValid()).isFalse();
        assertThat(new PccTarget("../parent").isValid()).isFalse();

        // Ending with ".."
        assertThat(new PccTarget("files/..").isValid()).isFalse();

        // "file..name" is valid
        assertThat(new PccTarget("file..name").isValid()).isTrue();
    }

    @Test
    public void testIsValid_invalidPrefixes_unsafeCharacters() {
        // Newline
        assertThat(new PccTarget("files\n").isValid()).isFalse();

        // Tab
        assertThat(new PccTarget("files\t").isValid()).isFalse();

        // Zero Width Space
        assertThat(new PccTarget("files\u200B").isValid()).isFalse();
    }

    @Test
    public void testGetTargetPath() {
        PccTarget target = new PccTarget("subdir");
        String pkg = "com.example.app";
        int userId = 0;

        // CE on internal
        assertThat(target.getTargetPath(null, false, userId, pkg).getAbsolutePath())
                .isEqualTo(
                        new File(
                                        Environment.getPccDataUserCePackageDirectory(
                                                null, userId, pkg),
                                        "subdir")
                                .getAbsolutePath());

        // DE on internal
        assertThat(target.getTargetPath(null, true, userId, pkg).getAbsolutePath())
                .isEqualTo(
                        new File(
                                        Environment.getPccDataUserDePackageDirectory(
                                                null, userId, pkg),
                                        "subdir")
                                .getAbsolutePath());

        // CE on external volume
        assertThat(target.getTargetPath("vol1", false, userId, pkg).getAbsolutePath())
                .isEqualTo(
                        new File(
                                        Environment.getPccDataUserCePackageDirectory(
                                                "vol1", userId, pkg),
                                        "subdir")
                                .getAbsolutePath());
    }
}
