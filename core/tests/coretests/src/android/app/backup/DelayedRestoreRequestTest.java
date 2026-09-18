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

package android.app.backup;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;


@Presubmit
@RunWith(AndroidJUnit4.class)
public class DelayedRestoreRequestTest {
    @Test
    public void createRequest_invalidType_doesNotBuild() {
        DelayedRestoreRequest.Builder builder = new DelayedRestoreRequest.Builder(999);

        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    public void createRequest_dependsOnPackage_dependencyNotSet_doesNotBuild() {
        DelayedRestoreRequest.Builder builder = new DelayedRestoreRequest.Builder(
                DelayedRestoreRequest.TYPE_APP_INSTALL);

        assertThrows(IllegalStateException.class, builder::build);
    }

    @Test
    public void createRequest_doesNotDependOnPackage_dependencySet_doesNotBuild() {
        DelayedRestoreRequest.Builder builder = new DelayedRestoreRequest.Builder(
                DelayedRestoreRequest.TYPE_SETUP_COMPLETE);

        assertThrows(IllegalStateException.class, () -> builder.setPackageName("com.some.package"));
    }

    @Test
    public void createRequest_mandatoryFieldsSet_builds() {
        String packageName = "com.other.package";
        DelayedRestoreRequest.Builder builder = new DelayedRestoreRequest.Builder(
                DelayedRestoreRequest.TYPE_APP_INSTALL);

        builder.setPackageName(packageName);
        DelayedRestoreRequest request = builder.build();

        assertThat(request.getType()).isEqualTo(DelayedRestoreRequest.TYPE_APP_INSTALL);
        assertThat(request.getPackageName()).isEqualTo(packageName);
    }
}
