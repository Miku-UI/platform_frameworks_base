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

package com.android.internal.inputmethod;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.annotation.NonNull;
import android.os.Parcel;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

@SmallTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class ImeSwitcherMenuItemSafeListTest {

    @NonNull
    private static IImeSwitcherMenu.Item createFakeImeSwitcherMenuItem(@NonNull String imeName,
            @NonNull String subtypeName) {
        final IImeSwitcherMenu.Item item = new IImeSwitcherMenu.Item();
        item.imeName = imeName;
        item.subtypeName = subtypeName;
        item.layoutName = null;
        item.imeId = "";
        item.subtypeIndex = -1;
        return item;
    }

    @NonNull
    private static List<IImeSwitcherMenu.Item> createTestImeSwitcherMenuItemList() {
        final ArrayList<IImeSwitcherMenu.Item> list = new ArrayList<>();
        list.add(createFakeImeSwitcherMenuItem("TestIme1", "Subtype1"));
        list.add(createFakeImeSwitcherMenuItem("TestIme1", "Subtype2"));
        list.add(createFakeImeSwitcherMenuItem("TestIme2", "EmojiSubtype"));
        return list;
    }

    @Test
    public void testCreate() {
        assertNotNull(ImeSwitcherMenuItemSafeList.create(createTestImeSwitcherMenuItemList()));
    }

    @Test
    public void testExtract() {
        assertItemsAfterExtract(createTestImeSwitcherMenuItemList(),
                ImeSwitcherMenuItemSafeList::create);
    }

    @Test
    public void testExtractAfterParceling() {
        assertItemsAfterExtract(createTestImeSwitcherMenuItemList(),
                originals -> cloneViaParcel(ImeSwitcherMenuItemSafeList.create(originals)));
    }

    @Test
    public void testExtractEmptyList() {
        assertItemsAfterExtract(Collections.emptyList(), ImeSwitcherMenuItemSafeList::create);
    }

    @Test
    public void testExtractAfterParcelingEmptyList() {
        assertItemsAfterExtract(Collections.emptyList(),
                originals -> cloneViaParcel(ImeSwitcherMenuItemSafeList.create(originals)));
    }

    private static void assertItemsAfterExtract(@NonNull List<IImeSwitcherMenu.Item> originals,
            @NonNull Function<List<IImeSwitcherMenu.Item>, ImeSwitcherMenuItemSafeList> factory) {
        final ImeSwitcherMenuItemSafeList list = factory.apply(originals);
        final List<IImeSwitcherMenu.Item> extracted = ImeSwitcherMenuItemSafeList.extractFrom(list);
        assertEquals(originals.size(), extracted.size());
        for (int i = 0; i < originals.size(); ++i) {
            assertNotSame("ImeSwitcherMenuItemSafeList.extractFrom() must clone each instance",
                    originals.get(i), extracted.get(i));
            assertEquals("Verify the cloned instances have the equal value",
                    originals.get(i), extracted.get(i));
        }

        // Subsequent calls of ImeSwitcherMenuItemSafeList.extractFrom() return an empty list.
        final List<IImeSwitcherMenu.Item> extracted2 =
                ImeSwitcherMenuItemSafeList.extractFrom(list);
        assertTrue(extracted2.isEmpty());
    }

    @NonNull
    private static ImeSwitcherMenuItemSafeList cloneViaParcel(
            @NonNull ImeSwitcherMenuItemSafeList original) {
        Parcel parcel = null;
        try {
            parcel = Parcel.obtain();
            original.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            final ImeSwitcherMenuItemSafeList newInstance =
                    ImeSwitcherMenuItemSafeList.CREATOR.createFromParcel(parcel);
            assertNotNull(newInstance);
            return newInstance;
        } finally {
            if (parcel != null) {
                parcel.recycle();
            }
        }
    }
}
