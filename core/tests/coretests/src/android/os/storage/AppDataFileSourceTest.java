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

import android.os.storage.operations.sources.AppDataFileSource;
import android.os.storage.operations.sources.OperationSourceWrapper;
import android.platform.test.annotations.Presubmit;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class AppDataFileSourceTest {

    @Test
    public void testParceling() {
        File file = new File("/data/user/0/com.example/files/test.txt");
        AppDataFileSource source = new AppDataFileSource(file);

        OperationSourceWrapper wrapper = new OperationSourceWrapper(source);
        android.os.Parcel parcel = android.os.Parcel.obtain();
        wrapper.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        OperationSourceWrapper unparceledWrapper =
                OperationSourceWrapper.CREATOR.createFromParcel(parcel);
        AppDataFileSource unparceledSource =
                (AppDataFileSource) unparceledWrapper.getWrappedSource();

        assertThat(unparceledSource.getFile().getAbsolutePath()).isEqualTo(file.getAbsolutePath());
    }

    @Test
    public void testIsValid_validPaths() {
        // Internal storage (CE)
        AppDataFileSource ceStorage =
                new AppDataFileSource(new File("/data/user/0/com.example/files/test.txt"));
        assertThat(ceStorage.isValid()).isTrue();

        // Internal storage (DE)
        AppDataFileSource deStorage =
                new AppDataFileSource(new File("/data/user_de/0/com.example/databases/db"));
        assertThat(deStorage.isValid()).isTrue();

        // Package root (with and without trailing slash)
        assertThat(new AppDataFileSource(new File("/data/user/0/com.example")).isValid()).isTrue();
        assertThat(new AppDataFileSource(new File("/data/user/0/com.example/")).isValid()).isTrue();

        // Files in package root
        assertThat(new AppDataFileSource(new File("/data/user/0/com.example/my_file")).isValid())
                .isTrue();
        // Subdirs in package root
        assertThat(new AppDataFileSource(new File("/data/user/0/com.example/my_dir/")).isValid())
                .isTrue();
    }

    @Test
    public void testIsValid_invalidPaths_unsupportedLocation() {
        // system/sdcard/tmp
        assertThat(new AppDataFileSource(new File("/system/etc/hosts")).isValid()).isFalse();
        assertThat(new AppDataFileSource(new File("/sdcard/Download/virus.exe")).isValid())
                .isFalse();
        assertThat(new AppDataFileSource(new File("/tmp/test")).isValid()).isFalse();

        // /data/data/ is NOT supported
        AppDataFileSource textSource =
                new AppDataFileSource(new File("/data/data/com.example/files/test.txt"));
        assertThat(textSource.isValid()).isFalse();

        // External storage is NOT supported
        AppDataFileSource documentSource =
                new AppDataFileSource(
                        new File("/storage/emulated/0/Android/data/com.example/files/doc.pdf"));
        assertThat(documentSource.isValid()).isFalse();

        // Expanded storage is NOT supported
        AppDataFileSource mountSource =
                new AppDataFileSource(new File("/mnt/expand/uuid/user/0/com.example/files/test"));
        assertThat(mountSource.isValid()).isFalse();

        // Raw user roots
        assertThat(new AppDataFileSource(new File("/data/user/0")).isValid()).isFalse();
        assertThat(new AppDataFileSource(new File("/data/user/0/")).isValid()).isFalse();
        assertThat(new AppDataFileSource(new File("/data/user_de/10")).isValid()).isFalse();
    }

    @Test
    public void testIsValid_invalidPaths_pathTraversal() {
        // ".." component
        AppDataFileSource traverseUp =
                new AppDataFileSource(new File("/data/user/0/com.example/../com.other/files"));
        assertThat(traverseUp.isValid()).isFalse();

        // Ending with ".."
        assertThat(new AppDataFileSource(new File("/data/user/0/com.example/files/..")).isValid())
                .isFalse();

        // But ".." in filename should be valid
        // So "file..name.txt" should be valid if it starts with correct prefix.
        AppDataFileSource weirdFileName =
                new AppDataFileSource(new File("/data/user/0/com.example/files/report..final.txt"));
        assertThat(weirdFileName.isValid()).isTrue();
    }

    @Test
    public void testIsValid_invalidPaths_unsafeCharacters() {
        // Newline
        AppDataFileSource newLine =
                new AppDataFileSource(new File("/data/user/0/com.example/files/test\n.txt"));
        assertThat(newLine.isValid()).isFalse();

        // Tab
        AppDataFileSource tab =
                new AppDataFileSource(new File("/data/user/0/com.example/files/test\t.txt"));
        assertThat(tab.isValid()).isFalse();

        // Zero Width Space (\u200B)
        AppDataFileSource zeroWidth =
                new AppDataFileSource(new File("/data/user/0/com.example/files/test\u200B.txt"));
        assertThat(zeroWidth.isValid()).isFalse();
    }
}
