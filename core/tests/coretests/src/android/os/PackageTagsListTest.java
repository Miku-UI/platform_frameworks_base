/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.Presubmit;
import android.util.ArrayMap;
import android.util.ArraySet;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class PackageTagsListTest {

    @Test
    public void testPackageTagsList() {
        PackageTagsList.Builder builder = new PackageTagsList.Builder()
                .add("package1", "attr1")
                .add("package1", "attr2")
                .add("package2")
                .add("package4", Arrays.asList("attr1", "attr2"));
        PackageTagsList list = builder.build();

        assertTrue(list.containsAll(builder.build()));
        assertTrue(list.contains("package1", "attr1"));
        assertTrue(list.contains("package1", "attr2"));
        assertTrue(list.contains("package2", "attr1"));
        assertTrue(list.contains("package2", "attr2"));
        assertTrue(list.contains("package2", "attr3"));
        assertTrue(list.contains("package4", "attr1"));
        assertTrue(list.contains("package4", "attr2"));
        assertTrue(list.containsPackageWithAllTags("package2"));
        assertTrue(list.containsPackage("package1"));
        assertTrue(list.containsPackage("package2"));
        assertFalse(list.contains("package1", "attr3"));
        assertFalse(list.contains("package4", "attr3"));
        assertFalse(list.containsPackageWithAllTags("package1"));
        assertFalse(list.containsPackage("package3"));

        PackageTagsList bigList = builder.add("package3").build();
        assertTrue(bigList.containsAll(builder.build()));
        assertTrue(bigList.containsAll(list));
        assertFalse(list.containsAll(bigList));
    }

    @Test
    public void testPackageTagsList_BuildFromMap() {
        ArrayMap<String, ArraySet<String>> map = new ArrayMap<>();
        map.put("package1", new ArraySet<>(Arrays.asList("attr1", "attr2")));
        map.put("package2", new ArraySet<>());

        PackageTagsList.Builder builder = new PackageTagsList.Builder().addAll(map);
        PackageTagsList list = builder.build();

        assertTrue(list.containsAll(builder.build()));
        assertTrue(list.contains("package1", "attr1"));
        assertTrue(list.contains("package1", "attr2"));
        assertTrue(list.contains("package2", "attr1"));
        assertTrue(list.contains("package2", "attr2"));
        assertTrue(list.contains("package2", "attr3"));
        assertTrue(list.containsPackageWithAllTags("package2"));
        assertTrue(list.containsPackage("package1"));
        assertTrue(list.containsPackage("package2"));
        assertFalse(list.contains("package1", "attr3"));
        assertFalse(list.containsPackageWithAllTags("package1"));
        assertFalse(list.containsPackage("package3"));

        map.put("package3", new ArraySet<>());
        PackageTagsList bigList = builder.addAll(map).build();
        assertTrue(bigList.containsAll(builder.build()));
        assertTrue(bigList.containsAll(list));
        assertFalse(list.containsAll(bigList));
    }

    @Test
    public void testPackageTagsList_Remove() {
        PackageTagsList.Builder builder = new PackageTagsList.Builder()
                .add("package1", "attr1")
                .add("package1", "attr2")
                .add("package2")
                .add("package4", Arrays.asList("attr1", "attr2", "attr3"))
                .add("package3", "attr1")
                .remove("package1", "attr1")
                .remove("package1", "attr2")
                .remove("package2", "attr1")
                .remove("package4", Arrays.asList("attr1", "attr2"))
                .remove("package3");
        PackageTagsList list = builder.build();

        assertTrue(list.containsAll(builder.build()));
        assertFalse(list.contains("package1", "attr1"));
        assertFalse(list.contains("package1", "attr2"));
        assertTrue(list.contains("package2", "attr1"));
        assertTrue(list.contains("package2", "attr2"));
        assertTrue(list.contains("package2", "attr3"));
        assertFalse(list.contains("package3", "attr1"));
        assertFalse(list.contains("package4", "attr1"));
        assertFalse(list.contains("package4", "attr2"));
        assertTrue(list.contains("package4", "attr3"));
        assertTrue(list.containsPackageWithAllTags("package2"));
        assertFalse(list.containsPackage("package1"));
        assertTrue(list.containsPackage("package2"));
        assertFalse(list.containsPackage("package3"));
        assertTrue(list.containsPackage("package4"));
    }

    @Test
    public void testPackageTagsList_EmptyCollections() {
        PackageTagsList.Builder builder = new PackageTagsList.Builder()
                .add("package1", Collections.emptyList())
                .add("package2")
                .remove("package2", Collections.emptyList());
        PackageTagsList list = builder.build();

        assertTrue(list.containsAll(builder.build()));
        assertFalse(list.contains("package1", "attr1"));
        assertTrue(list.contains("package2", "attr2"));
    }

    @Test
    public void testWriteToParcel() {
        PackageTagsList list = new PackageTagsList.Builder()
                .add("package1", "attr1")
                .add("package1", "attr2")
                .add("package2")
                .build();
        Parcel parcel = Parcel.obtain();
        list.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        PackageTagsList newList = PackageTagsList.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals(list, newList);
    }

    @Test
    public void testGetPackages() {
        PackageTagsList list = new PackageTagsList.Builder().build();
        assertTrue(list.getPackages().isEmpty());

        list =
                new PackageTagsList.Builder()
                        .add("package1", "attr1")
                        .add("package1", "attr2")
                        .build();
        assertEquals(Collections.singleton("package1"), list.getPackages());

        list = new PackageTagsList.Builder().add("package1").build();
        assertEquals(Collections.singleton("package1"), list.getPackages());

        list =
                new PackageTagsList.Builder()
                        .add("package1", "attr1")
                        .add("package2", "attr2")
                        .add("package3")
                        .build();
        assertEquals(
                new ArraySet<>(Arrays.asList("package1", "package2", "package3")),
                list.getPackages());
    }
}
