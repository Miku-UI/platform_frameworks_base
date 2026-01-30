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

package com.android.server.inputmethod;

import static com.android.server.inputmethod.InputMethodSubtypeSwitchingController.MODE_AUTO;
import static com.android.server.inputmethod.InputMethodSubtypeSwitchingController.MODE_RECENT;
import static com.android.server.inputmethod.InputMethodSubtypeSwitchingController.MODE_STATIC;
import static com.android.server.inputmethod.InputMethodSubtypeSwitchingController.SwitchMode;
import static com.android.server.inputmethod.InputMethodUtils.NOT_A_SUBTYPE_INDEX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodSubtype;
import android.view.inputmethod.InputMethodSubtype.InputMethodSubtypeBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.inputmethod.InputMethodSubtypeSwitchingController.ImeSubtypeListItem;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class InputMethodSubtypeSwitchingControllerTest {
    private static final String TEST_PACKAGE_NAME = "test package name";
    private static final String TEST_SETTING_ACTIVITY_NAME = "";
    private static final boolean TEST_IS_AUX_IME = false;
    private static final boolean TEST_FORCE_DEFAULT = false;
    private static final boolean TEST_IS_VR_IME = false;
    private static final int TEST_IS_DEFAULT_RES_ID = 0;
    private static final String SYSTEM_LOCALE = "en_US";

    /** Verifies the static mode. */
    @Test
    public void testModeStatic() {
        final var items = new ArrayList<ImeSubtypeListItem>();
        createTestItems(items, "LatinIme", List.of("en", "fr", "it"),
                false /* suitableForHardware */);
        createTestItems(items, "SimpleIme", null, false /* suitableForHardware */);
        final var latinIme = new ArrayList<>(items.subList(0, 3));
        final var simpleIme = new ArrayList<>(items.subList(3, 4));

        final var hardItems = new ArrayList<ImeSubtypeListItem>();
        createTestItems(hardItems, "HardwareLatinIme", List.of("ja", "ko", "ru"),
                true /* suitableForHardware */);
        createTestItems(hardItems, "HardSimpleIme", null, true /* suitableForHardware */);
        final var hardIme = new ArrayList<>(hardItems.subList(0, 3));
        final var hardSimpleIme = new ArrayList<>(hardItems.subList(3, 4));

        items.addAll(hardItems);
        final var controller = new InputMethodSubtypeSwitchingController();
        controller.update(items);

        final int mode = MODE_STATIC;

        // Static mode matches the given items order.
        assertNextOrder(controller, false /* forHardware */, mode, items,
                List.of(latinIme, simpleIme, hardIme, hardSimpleIme));
        assertNextOrder(controller, true /* forHardware */, mode, hardItems,
                List.of(hardIme, hardSimpleIme));

        final var french = items.get(1);
        assertTrue("Recency updated for french IME", onUserAction(controller, french));
        // Static mode is not influenced by recency updates on non-hardware item.
        assertNextOrder(controller, false /* forHardware */, mode,
                items, List.of(latinIme, simpleIme, hardIme, hardSimpleIme));
        assertNextOrder(controller, true /* forHardware */, mode,
                hardItems, List.of(hardIme, hardSimpleIme));

        final var korean = hardItems.get(1);
        assertTrue("Recency updated for korean IME", onUserAction(controller, korean));
        // Static mode is not influenced by recency updates on hardware item.
        assertNextOrder(controller, false /* forHardware */, mode, items,
                List.of(latinIme, simpleIme, hardIme, hardSimpleIme));
        assertNextOrder(controller, true /* forHardware */, mode, hardItems,
                List.of(hardIme, hardSimpleIme));
    }

    /** Verifies the recency mode. */
    @Test
    public void testModeRecent() {
        final var items = new ArrayList<ImeSubtypeListItem>();
        createTestItems(items, "LatinIme", List.of("en", "fr", "it"),
                false /* suitableForHardware */);
        createTestItems(items, "SoftSimpleIme", null, false /* suitableForHardware */);
        final var latinIme = new ArrayList<>(items.subList(0, 3));
        final var softSimpleIme = new ArrayList<>(items.subList(3, 4));

        final var hardItems = new ArrayList<ImeSubtypeListItem>();
        createTestItems(hardItems, "HardwareIme", List.of("ja", "ko", "ru"),
                true /* suitableForHardware */);
        createTestItems(hardItems, "HardSimpleIme", null, true /* suitableForHardware */);
        final var hardIme = new ArrayList<>(hardItems.subList(0, 3));
        final var hardSimpleIme = new ArrayList<>(hardItems.subList(3, 4));

        items.addAll(hardItems);
        final var controller = new InputMethodSubtypeSwitchingController();
        controller.update(items);

        final int mode = MODE_RECENT;

        // Recency order is initialized to static order.
        assertNextOrder(controller, false /* forHardware */, mode, items,
                List.of(latinIme, softSimpleIme, hardIme, hardSimpleIme));
        assertNextOrder(controller, true /* forHardware */, mode, hardItems,
                List.of(hardIme, hardSimpleIme));

        final var french = items.get(1);
        assertTrue("Recency updated for french IME", onUserAction(controller, french));
        final var recencyItems = new ArrayList<>(items);
        final var recencyLatinIme = new ArrayList<>(latinIme);
        moveItemToFront(recencyItems, french);
        moveItemToFront(recencyLatinIme, french);

        // The order of non-hardware items is updated.
        assertNextOrder(controller, false /* forHardware */, mode, recencyItems,
                List.of(recencyLatinIme, softSimpleIme, hardIme, hardSimpleIme));

        // The order of hardware items remains unchanged for an action on a non-hardware item.
        assertNextOrder(controller, true /* forHardware */, mode, hardItems,
                List.of(hardIme, hardSimpleIme));

        assertFalse("Recency not updated again for same IME", onUserAction(controller, french));
        // The order remains unchanged.
        assertNextOrder(controller, false /* forHardware */, mode, recencyItems,
                List.of(recencyLatinIme, softSimpleIme, hardIme, hardSimpleIme));
        assertNextOrder(controller, true /* forHardware */, mode, hardItems,
                List.of(hardIme, hardSimpleIme));

        final var korean = hardItems.get(1);
        assertTrue("Recency updated for korean IME", onUserAction(controller, korean));
        final var recencyHardItems = new ArrayList<>(hardItems);
        final var recencyHardIme = new ArrayList<>(hardIme);
        moveItemToFront(recencyItems, korean);
        moveItemToFront(recencyHardItems, korean);
        moveItemToFront(recencyHardIme, korean);

        // The order of non-hardware items is unchanged.
        assertNextOrder(controller, false /* forHardware */, mode, recencyItems,
                List.of(recencyLatinIme, softSimpleIme, recencyHardIme, hardSimpleIme));

        // The order of hardware items is updated.
        assertNextOrder(controller, true /* forHardware */, mode, recencyHardItems,
                List.of(recencyHardIme, hardSimpleIme));
    }

    /** Verifies the auto mode. */
    @Test
    public void testModeAuto() {
        final var items = new ArrayList<ImeSubtypeListItem>();
        createTestItems(items, "LatinIme", List.of("en", "fr", "it"),
                false /* suitableForHardware */);
        createTestItems(items, "SoftSimpleIme", null, false /* suitableForHardware */);
        final var latinIme = new ArrayList<>(items.subList(0, 3));
        final var softSimpleIme = new ArrayList<>(items.subList(3, 4));

        final var hardItems = new ArrayList<ImeSubtypeListItem>();
        createTestItems(hardItems, "HardwareIme", List.of("ja", "ko", "ru"),
                true /* suitableForHardware */);
        createTestItems(hardItems, "HardSimpleIme", null, true /* suitableForHardware */);
        final var hardIme = new ArrayList<>(hardItems.subList(0, 3));
        final var hardSimpleIme = new ArrayList<>(hardItems.subList(3, 4));

        items.addAll(hardItems);
        final var controller = new InputMethodSubtypeSwitchingController();
        controller.update(items);

        final int mode = MODE_AUTO;

        // Auto mode resolves to static order initially.
        assertNextOrder(controller, false /* forHardware */, mode, items,
                List.of(latinIme, softSimpleIme, hardIme, hardSimpleIme));
        assertNextOrder(controller, true /* forHardware */, mode, hardItems,
                List.of(hardIme, hardSimpleIme));

        final var french = items.get(1);
        assertTrue("Recency updated for french IME", onUserAction(controller, french));
        final var recencyItems = new ArrayList<>(items);
        final var recencyLatinIme = new ArrayList<>(latinIme);
        moveItemToFront(recencyItems, french);
        moveItemToFront(recencyLatinIme, french);

        // Auto mode resolves to recency order for the first forward after user action, and to
        // static order for the backwards direction.
        assertNextOrder(controller, false /* forHardware */, mode, true /* forward */, recencyItems,
                List.of(recencyLatinIme, softSimpleIme, hardIme, hardSimpleIme));
        assertNextOrder(controller, false /* forHardware */, mode, false /* forward */,
                items.reversed(), List.of(latinIme.reversed(), softSimpleIme.reversed(),
                        hardIme.reversed(), hardSimpleIme.reversed()));

        // Auto mode resolves to recency order for the first forward after user action,
        // but the recency was not updated for hardware items, so it's equivalent to static order.
        assertNextOrder(controller, true /* forHardware */, mode, hardItems,
                List.of(hardIme, hardSimpleIme));

        // Change IME, reset user action having happened.
        controller.onInputMethodSubtypeChanged();

        // Auto mode resolves to static order as there was no user action since changing IMEs.
        assertNextOrder(controller, false /* forHardware */, mode, items,
                List.of(latinIme, softSimpleIme, hardIme, hardSimpleIme));

        assertNextOrder(controller, true /* forHardware */, mode, hardItems,
                List.of(hardIme, hardSimpleIme));

        // User action on french IME again.
        assertFalse("Recency not updated again for same IME", onUserAction(controller, french));
        // Auto mode still resolves to static order, as a user action on the currently most
        // recent IME has no effect.
        assertNextOrder(controller, false /* forHardware */, mode, items,
                List.of(latinIme, softSimpleIme, hardIme, hardSimpleIme));

        assertNextOrder(controller, true /* forHardware */, mode, hardItems,
                List.of(hardIme, hardSimpleIme));

        final var korean = hardItems.get(1);
        assertTrue("Recency updated for korean IME", onUserAction(controller, korean));
        final var recencyHardItems = new ArrayList<>(hardItems);
        final var recencyHardIme = new ArrayList<>(hardIme);
        moveItemToFront(recencyItems, korean);
        moveItemToFront(recencyHardItems, korean);
        moveItemToFront(recencyHardIme, korean);

        // Auto mode resolves to recency order for the first forward direction after a user action
        // on a hardware IME, and to static order for the backwards direction.
        assertNextOrder(controller, false /* forHardware */, mode, true /* forward */, recencyItems,
                List.of(recencyLatinIme, softSimpleIme, recencyHardIme, hardSimpleIme));
        assertNextOrder(controller, false /* forHardware */, mode, false /* forward */,
                items.reversed(), List.of(latinIme.reversed(), softSimpleIme.reversed(),
                        hardIme.reversed(), hardSimpleIme.reversed()));

        assertNextOrder(controller, true /* forHardware */, mode, true /* forward */,
                recencyHardItems, List.of(recencyHardIme, hardSimpleIme));
        assertNextOrder(controller, true /* forHardware */, mode, false /* forward */,
                hardItems.reversed(),
                List.of(hardIme.reversed(), hardSimpleIme.reversed()));
    }

    /**
     * Verifies that the recency order is preserved only when updating with an equal list of items.
     */
    @Test
    public void testUpdateList() {
        final var items = new ArrayList<ImeSubtypeListItem>();
        createTestItems(items, "LatinIme", List.of("en", "fr", "it"),
                false /* suitableForHardware */);
        createTestItems(items, "SimpleIme", null, false /* suitableForHardware */);
        final var latinIme = new ArrayList<>(items.subList(0, 3));
        final var simpleIme = new ArrayList<>(items.subList(3, 4));

        final var hardItems = new ArrayList<ImeSubtypeListItem>();
        createTestItems(hardItems, "HardwareIme", List.of("ja", "ko", "ru"),
                true /* suitableForHardware */);
        createTestItems(hardItems, "HardSimpleIme", null, true /* suitableForHardware */);
        final var hardIme = new ArrayList<>(hardItems.subList(0, 3));
        final var hardSimpleIme = new ArrayList<>(hardItems.subList(3, 4));

        items.addAll(hardItems);
        final var controller = new InputMethodSubtypeSwitchingController();
        controller.update(items);

        final int mode = MODE_RECENT;

        // Recency order is initialized to static order.
        assertNextOrder(controller, false /* forHardware */, mode, items,
                List.of(latinIme, simpleIme, hardIme, hardSimpleIme));
        assertNextOrder(controller, true /* forHardware */, mode, hardItems,
                List.of(hardIme, hardSimpleIme));

        final var french = items.get(1);
        assertTrue("Recency updated for french IME", onUserAction(controller, french));
        final var recencyItems = new ArrayList<>(items);
        final var recencyLatinIme = new ArrayList<>(latinIme);
        moveItemToFront(recencyItems, french);
        moveItemToFront(recencyLatinIme, french);

        controller.update(new ArrayList<>(items));

        // The order of non-hardware items remains unchanged when updated with equal items.
        assertNextOrder(controller, false /* forHardware */, mode, recencyItems,
                List.of(recencyLatinIme, simpleIme, hardIme, hardSimpleIme));
        // The order of hardware items remains unchanged when only non-hardware items are updated.
        assertNextOrder(controller, true /* forHardware */, mode, hardItems,
                List.of(hardIme, hardSimpleIme));

        final var otherItems = new ArrayList<>(items);
        otherItems.remove(simpleIme.getFirst());
        controller.update(otherItems);

        // The order of non-hardware items is reset when updated with other items.
        assertNextOrder(controller, false /* forHardware */, mode, otherItems,
                List.of(latinIme, hardIme, hardSimpleIme));
        // The order of hardware items is also reset when non-hardware items are updated.
        assertNextOrder(controller, true /* forHardware */, mode, hardItems,
                List.of(hardIme, hardSimpleIme));

        final var korean = hardItems.get(1);
        assertTrue("Recency updated for korean IME", onUserAction(controller, korean));
        final var recencyOtherItems = new ArrayList<>(otherItems);
        final var recencyHardItems = new ArrayList<>(hardItems);
        final var recencyHardIme = new ArrayList<>(hardIme);
        moveItemToFront(recencyOtherItems, korean);
        moveItemToFront(recencyHardItems, korean);
        moveItemToFront(recencyHardIme, korean);

        controller.update(new ArrayList<>(otherItems));

        // The order of non-hardware items remains unchanged when updated with equal items.
        assertNextOrder(controller, false /* forHardware */, mode, recencyOtherItems,
                List.of(latinIme, recencyHardIme, hardSimpleIme));
        // The order of hardware items remains unchanged when updated with equal items.
        assertNextOrder(controller, true /* forHardware */, mode, recencyHardItems,
                List.of(recencyHardIme, hardSimpleIme));

        final var otherHardwareItems = new ArrayList<>(otherItems);
        otherHardwareItems.remove(hardSimpleIme.getFirst());
        controller.update(otherHardwareItems);

        // The order of non-hardware items remains unchanged when only hardware items are updated.
        assertNextOrder(controller, false /* forHardware */, mode, otherHardwareItems,
                List.of(latinIme, hardIme));
        // The order of hardware items is reset when updated with other items.
        assertNextOrder(controller, true /* forHardware */, mode, hardIme,
                List.of(hardIme));
    }

    /** Verifies that items are filtered correctly. */
    @Test
    public void testGetItems() throws Exception {
        final var controller = new InputMethodSubtypeSwitchingController();

        final var normalItem = createTestItem("Normal", true /* showInImeSwitcherMenu */,
                false /* isAuxiliary */);
        final var hiddenItem = createTestItem("Hidden", false /* showInImeSwitcherMenu */,
                false /* isAuxiliary */);
        final var auxItem = createTestItem("Aux", true /* showInImeSwitcherMenu */,
                true /* isAuxiliary */);
        final var hiddenAuxItem = createTestItem("HiddenAux", false /* showInImeSwitcherMenu */,
                true /* isAuxiliary */);

        final var allItems = List.of(normalItem, hiddenItem, auxItem, hiddenAuxItem);

        // Use reflection to set the private mEnabledItems field for testing.
        final Field enabledItemsField =
                InputMethodSubtypeSwitchingController.class.getDeclaredField("mEnabledItems");
        enabledItemsField.setAccessible(true);
        enabledItemsField.set(controller, allItems);

        // Test case 1: getItems(forMenu=false, includeAuxiliary=false)
        // Should return non-auxiliary items.
        var result = controller.getItems(false /* forMenu */, false /* includeAuxiliary */);
        assertEquals(List.of(normalItem, hiddenItem), result);

        // Test case 2: getItems(forMenu=true, includeAuxiliary=false)
        // Should return non-auxiliary items that are shown in the menu.
        result = controller.getItems(true /* forMenu */, false /* includeAuxiliary */);
        assertEquals(List.of(normalItem), result);

        // Test case 3: getItems(forMenu=false, includeAuxiliary=true)
        // Should return all items.
        result = controller.getItems(false /* forMenu */, true /* includeAuxiliary */);
        assertEquals(allItems, result);

        // Test case 4: getItems(forMenu=true, includeAuxiliary=true)
        // Should return all items that are shown in the menu.
        result = controller.getItems(true /* forMenu */, true /* includeAuxiliary */);
        assertEquals(List.of(normalItem, auxItem), result);
    }

    /** Verifies that switch aware and switch unaware IMEs are combined together. */
    @Test
    public void testSwitchAwareAndUnawareCombined() {
        final var items = new ArrayList<ImeSubtypeListItem>();
        createTestItems(items, "switchAware", null, false /* suitableForHardware */,
                true /* supportsSwitchingToNextInputMethod*/);
        createTestItems(items, "switchUnaware", null, false /* suitableForHardware */,
                false /* supportsSwitchingToNextInputMethod*/);

        final var hardItems = new ArrayList<ImeSubtypeListItem>();
        createTestItems(hardItems, "hardwareSwitchAware", null,
                true /* suitableForHardware */, true /* supportsSwitchingToNextInputMethod*/);
        createTestItems(hardItems, "hardwareSwitchUnaware", null,
                true /* suitableForHardware */, false /* supportsSwitchingToNextInputMethod*/);

        final var controller = new InputMethodSubtypeSwitchingController();
        items.addAll(hardItems);
        controller.update(items);

        for (int mode = MODE_STATIC; mode <= MODE_AUTO; mode++) {
            assertNextOrder(controller, false /* onlyCurrentIme */, false /* forHardware */, mode,
                    true /* forward */, items);
            assertNextOrder(controller, false /* onlyCurrentIme */, false /* forHardware */, mode,
                    false /* forward */, items.reversed());

            assertNextOrder(controller, false /* onlyCurrentIme */, true /* forHardware */, mode,
                    true /* forward */, hardItems);
            assertNextOrder(controller, false /* onlyCurrentIme */, true /* forHardware */, mode,
                    false /* forward */, hardItems.reversed());
        }
    }

    /** Verifies that an empty controller can't take any actions. */
    @Test
    public void testEmptyList() {
        final var items = new ArrayList<ImeSubtypeListItem>();
        createTestItems(items, "LatinIme", List.of("en", "fr"), false /* suitableForHardware */);

        final var hardItems = new ArrayList<ImeSubtypeListItem>();
        createTestItems(hardItems, "HardwareIme", List.of("ja", "ko"),
                true /* suitableForHardware */);

        final var controller = new InputMethodSubtypeSwitchingController();

        assertNextItemNoAction(controller, false /* forHardware */, items, null /* expectedNext */);
        assertNextItemNoAction(controller, true /* forHardware */, items, null /* expectedNext */);
    }

    /**
     * Verifies that a controller with a single item can't update the recency, and cannot switch
     * away from the item, but allows switching from unknown items to the single item.
     */
    @Test
    public void testSingleItemList() {
        final var items = new ArrayList<ImeSubtypeListItem>();
        createTestItems(items, "LatinIme", null, false /* suitableForHardware */);
        final var unknownItems = new ArrayList<ImeSubtypeListItem>();
        createTestItems(unknownItems, "UnknownIme", List.of("en", "fr", "it"),
                false /* suitableForHardware */);

        final var hardItems = new ArrayList<ImeSubtypeListItem>();
        createTestItems(hardItems, "HardwareIme", null, true /* suitableForHardware */);
        final var unknownHardItems = new ArrayList<ImeSubtypeListItem>();
        createTestItems(unknownHardItems, "HardwareUnknownIme", List.of("ja", "ko", "ru"),
                true /* suitableForHardware */);

        final var controller = new InputMethodSubtypeSwitchingController();
        controller.update(items);

        assertNextItemNoAction(controller, false /* forHardware */, items, null /* expectedNext */);
        assertNextItemNoAction(controller, false /* forHardware */, unknownItems, items.getFirst());
        assertNextItemNoAction(controller, true /* forHardware */, items, null /* expectedNext */);
        assertNextItemNoAction(controller, true /* forHardware */, unknownItems, items.getFirst());
    }

    /**
     * Verifies that the recency cannot be updated for unknown items, but switching from unknown
     * items reaches the most recent known item.
     */
    @Test
    public void testUnknownItems() {
        final var items = new ArrayList<ImeSubtypeListItem>();
        createTestItems(items, "LatinIme", List.of("en", "fr", "it"),
                false /* suitableForHardware */);
        final var latinIme = new ArrayList<>(items.subList(0, 3));

        final var unknownItems = new ArrayList<ImeSubtypeListItem>();
        createTestItems(unknownItems, "UnknownIme", List.of("en", "fr", "it"),
                false /* suitableForHardware */);

        final var hardItems = new ArrayList<ImeSubtypeListItem>();
        createTestItems(hardItems, "HardwareIme", List.of("ja", "ko", "ru"),
                true /* suitableForHardware */);

        final var unknownHardItems = new ArrayList<ImeSubtypeListItem>();
        createTestItems(unknownHardItems, "HardwareUnknownIme", List.of("ja", "ko", "ru"),
                true /* suitableForHardware */);

        final var controller = new InputMethodSubtypeSwitchingController();
        items.addAll(hardItems);
        controller.update(items);

        final var french = items.get(1);
        assertTrue("Recency updated for french IME", onUserAction(controller, french));
        final var recencyItems = new ArrayList<>(items);
        final var recencyLatinIme = new ArrayList<>(latinIme);
        moveItemToFront(recencyItems, french);
        moveItemToFront(recencyLatinIme, french);

        assertNextItemNoAction(controller, false /* forHardware */, unknownItems, french);
        assertNextItemNoAction(controller, true /* forHardware */, unknownHardItems, french);

        // Known items must not be able to switch to unknown items.
        assertNextOrder(controller, false /* forHardware */, MODE_STATIC, items,
                List.of(latinIme, hardItems));
        assertNextOrder(controller, false /* forHardware */, MODE_RECENT, recencyItems,
                List.of(recencyLatinIme, hardItems));
        assertNextOrder(controller, false /* forHardware */, MODE_AUTO, true /* forward */,
                recencyItems, List.of(recencyLatinIme, hardItems));
        assertNextOrder(controller, false /* forHardware */, MODE_AUTO, false /* forward */,
                items.reversed(), List.of(latinIme.reversed(), hardItems.reversed()));

        assertNextOrder(controller, true /* forHardware */, MODE_STATIC, hardItems,
                List.of(hardItems));
        assertNextOrder(controller, true /* forHardware */, MODE_RECENT, hardItems,
                List.of(hardItems));
        assertNextOrder(controller, true /* forHardware */, MODE_AUTO, hardItems,
                List.of(hardItems));
    }

    /** Verifies that the IME name does influence the comparison order. */
    @Test
    public void testCompareImeName() {
        final var component = new ComponentName("com.example.ime", "Ime");
        final var imeX = createTestItem(component, "ImeX", "A", SYSTEM_LOCALE, 0);
        final var imeY = createTestItem(component, "ImeY", "A", SYSTEM_LOCALE, 0);

        assertTrue("Smaller IME name should be smaller.", imeX.compareTo(imeY) < 0);
        assertTrue("Larger IME name should be larger.", imeY.compareTo(imeX) > 0);
    }

    /** Verifies that the IME ID does influence the comparison order. */
    @Test
    public void testCompareImeId() {
        final var component1 = new ComponentName("com.example.ime1", "Ime");
        final var component2 = new ComponentName("com.example.ime2", "Ime");
        final var ime1 = createTestItem(component1, "Ime", "A", SYSTEM_LOCALE, 0);
        final var ime2 = createTestItem(component2, "Ime", "A", SYSTEM_LOCALE, 0);

        assertTrue("Smaller IME ID should be smaller.", ime1.compareTo(ime2) < 0);
        assertTrue("Larger IME ID should be larger.", ime2.compareTo(ime1) > 0);
    }

    /** Verifies that comparison on self returns an equal order. */
    @SuppressWarnings("SelfComparison")
    @Test
    public void testCompareSelf() {
        final var component = new ComponentName("com.example.ime", "Ime");
        final var item = createTestItem(component, "Ime", "A", SYSTEM_LOCALE, 0);

        assertEquals("Item should have the same order to itself.", 0, item.compareTo(item));
    }

    /** Verifies that comparison on an equivalent item returns an equal order. */
    @Test
    public void testCompareEquivalent() {
        final var component = new ComponentName("com.example.ime", "Ime");
        final var item = createTestItem(component, "Ime", "A", SYSTEM_LOCALE, 0);
        final var equivalent = createTestItem(component, "Ime", "A", SYSTEM_LOCALE, 0);

        assertEquals("Equivalent items should have the same order.", 0, item.compareTo(equivalent));
    }

    /** Verifies that the subtype name does not influence the comparison order. */
    @Test
    public void testCompareSubtypeName() {
        final var component = new ComponentName("com.example.ime", "Ime");
        final var subtypeA = createTestItem(component, "Ime", "A", SYSTEM_LOCALE, 0);
        final var subtypeB = createTestItem(component, "Ime", "B", SYSTEM_LOCALE, 0);

        assertEquals("Subtype name shouldn't influence comparison.",
                0, subtypeA.compareTo(subtypeB));
    }

    /** Verifies that the subtype index does not influence the comparison order. */
    @Test
    public void testCompareSubtypeIndex() {
        final var component = new ComponentName("com.example.ime", "Ime");
        final var subtype0 = createTestItem(component, "Ime1", "A", SYSTEM_LOCALE, 0);
        final var subtype1 = createTestItem(component, "Ime1", "A", SYSTEM_LOCALE, 1);

        assertEquals("Subtype index shouldn't influence comparison.",
                0, subtype0.compareTo(subtype1));
    }

    /** Verifies the {@code ImeSubtypeListItem.equals} and {@code ImeSubtypeListItem.hashCode}. */
    @Test
    public void testEqualsAndHashCode() {
        final var component1 = new ComponentName("com.example.ime1", "Ime");
        final var subtypes1 = new ArrayList<InputMethodSubtype>();
        subtypes1.add(createTestSubtype(SYSTEM_LOCALE));
        final var imi1 =
                createTestImi(component1, subtypes1, true /* supportsSwitchingToNextInputMethod */);

        final var item1 =
                new ImeSubtypeListItem(
                        "Ime", "Subtype", "Layout", imi1, 0 /* subtypeIndex */,
                        true /* showInImeSwitcherMenu */, false /* isAuxiliary */,
                        true /* suitableForHardware */);

        // Test equality with null.
        assertNotNull("Item should not be null.", item1);

        // Test equals and hashCode with an equivalent item.
        final var item2 =
                new ImeSubtypeListItem(
                        "Ime", "Subtype", "Layout", imi1, 0 /* subtypeIndex */,
                        true /* showInImeSwitcherMenu */, false /* isAuxiliary */,
                        true /* suitableForHardware */);
        assertEquals("Equivalent items should be equal.", item1, item2);
        assertEquals("Hash code of equivalent items should be equal.",
                item1.hashCode(), item2.hashCode());

        // Test equals with different items.
        final var diffImeName =
                new ImeSubtypeListItem(
                        "OtherIme", "Subtype", "Layout", imi1, 0 /* subtypeIndex */,
                        true /* showInImeSwitcherMenu */, false /* isAuxiliary */,
                        true /* suitableForHardware */);
        assertNotEquals("Items with different IME names should not be equal.",
                item1, diffImeName);

        final var diffSubtypeName =
                new ImeSubtypeListItem(
                        "Ime", "OtherSubtype", "Layout", imi1, 0 /* subtypeIndex */,
                        true /* showInImeSwitcherMenu */, false /* isAuxiliary */,
                        true /* suitableForHardware */);
        assertNotEquals("Items with different subtype names should not be equal.",
                item1, diffSubtypeName);

        final var diffLayoutName =
                new ImeSubtypeListItem(
                        "Ime", "Subtype", "OtherLayout", imi1, 0 /* subtypeIndex */,
                        true /* showInImeSwitcherMenu */, false /* isAuxiliary */,
                        true /* suitableForHardware */);
        assertNotEquals("Items with different layout names should not be equal.",
                item1, diffLayoutName);

        final var component2 = new ComponentName("com.example.ime2", "Ime");
        final var subtypes2 = new ArrayList<InputMethodSubtype>();
        subtypes2.add(createTestSubtype(SYSTEM_LOCALE));
        final var imi2 =
                createTestImi(component2, subtypes2, true /* supportsSwitchingToNextInputMethod */);
        final var diffImi =
                new ImeSubtypeListItem(
                        "Ime", "Subtype", "Layout", imi2, 0 /* subtypeIndex */,
                        true /* showInImeSwitcherMenu */, false /* isAuxiliary */,
                        true /* suitableForHardware */);
        assertNotEquals("Items with different IMEs should not be equal.", item1, diffImi);

        final var diffSubtypeIndex =
                new ImeSubtypeListItem(
                        "Ime", "Subtype", "Layout", imi1, 1 /* subtypeIndex */,
                        true /* showInImeSwitcherMenu */, false /* isAuxiliary */,
                        true /* suitableForHardware */);
        assertNotEquals("Items with different subtype indices should not be equal.",
                item1, diffSubtypeIndex);

        final var diffShowInMenu =
                new ImeSubtypeListItem(
                        "Ime", "Subtype", "Layout", imi1, 0 /* subtypeIndex */,
                        false /* showInImeSwitcherMenu */, false /* isAuxiliary */,
                        true /* suitableForHardware */);
        assertNotEquals("Items with different show in menu flags should not be equal.",
                item1, diffShowInMenu);

        final var diffAuxiliary =
                new ImeSubtypeListItem(
                        "Ime", "Subtype", "Layout", imi1, 0 /* subtypeIndex */,
                        true /* showInImeSwitcherMenu */, true /* isAuxiliary */,
                        true /* suitableForHardware */);
        assertNotEquals("Items with different auxiliary flags should not be equal.",
                item1, diffAuxiliary);

        final var diffSuitableForHardware =
                new ImeSubtypeListItem(
                        "Ime", "Subtype", "Layout", imi1, 0 /* subtypeIndex */,
                        true /* showInImeSwitcherMenu */, false /* isAuxiliary */,
                        false /* suitableForHardware */);
        assertNotEquals("Items with different suitable for hardware flags should not be equal.",
                item1, diffSuitableForHardware);
    }

    /**
     * Moves the given item from its current position to the front of the given list.
     *
     * @param list the list to move the item in.
     * @param item the item to move.
     */
    private static void moveItemToFront(@NonNull List<ImeSubtypeListItem> list,
            @NonNull ImeSubtypeListItem item) {
        list.remove(item);
        list.addFirst(item);
    }

    /**
     * Same as {@link #createTestItems} below, with {@code suitableForHardware} and
     * {@code supportsSwitchingToNextInputMethod} set to {@code true}.
     *
     * @param items          the list to add the created items to.
     * @param imeName        the name of the IME.
     * @param subtypeLocales the list of subtype locales, or {@code null} for no subtypes.
     */
    static void createTestItems(@NonNull List<ImeSubtypeListItem> items,
            @NonNull String imeName, @Nullable List<String> subtypeLocales) {
        createTestItems(items, imeName, subtypeLocales, true /* suitableForHardware */,
                true /* supportsSwitchingToNextInputMethod*/);
    }

    /**
     * Same as {@link #createTestItems} below, with {@code supportsSwitchingToNextInputMethod} set
     * to {@code true}.
     *
     * @param items               the list to add the created items to.
     * @param imeName             the name of the IME.
     * @param subtypeLocales      the list of subtype locales, or {@code null} for no subtypes.
     * @param suitableForHardware whether the item is suitable for hardware keyboards.
     */
    private static void createTestItems(@NonNull List<ImeSubtypeListItem> items,
            @NonNull String imeName, @Nullable List<String> subtypeLocales,
            boolean suitableForHardware) {
        createTestItems(items, imeName, subtypeLocales, suitableForHardware,
                true /* supportsSwitchingToNextInputMethod*/);
    }

    /**
     * Creates test {@link ImeSubtypeListItem} based on the given IME name, label and subtype
     * locales, and adds them to the given list.
     *
     * @param items                              the list to add the created items to.
     * @param imeName                            the name of the IME.
     * @param subtypeLocales                     the list of subtype locales, or {@code null}
     *                                           for no subtypes.
     * @param suitableForHardware                whether the item is suitable for hardware
     *                                           keyboards.
     * @param supportsSwitchingToNextInputMethod whether the item supports switching to the next.
     */
    private static void createTestItems(@NonNull List<ImeSubtypeListItem> items,
            @NonNull String imeName, @Nullable List<String> subtypeLocales,
            boolean suitableForHardware, boolean supportsSwitchingToNextInputMethod) {
        final List<InputMethodSubtype> subtypes;
        if (subtypeLocales != null) {
            subtypes = new ArrayList<>();
            for (String subtypeLocale : subtypeLocales) {
                subtypes.add(createTestSubtype(subtypeLocale));
            }
        } else {
            subtypes = null;
        }
        final var componentName = new ComponentName(TEST_PACKAGE_NAME, imeName);
        final var imi = createTestImi(componentName, subtypes, supportsSwitchingToNextInputMethod);
        if (subtypes != null) {
            for (int i = 0; i < subtypes.size(); ++i) {
                final String subtypeLocale = subtypeLocales.get(i);
                items.add(new ImeSubtypeListItem(imeName, subtypeLocale, null /* layoutName */,
                        imi, i, true /* showInImeSwitcherMenu */, false /* isAuxiliary */,
                        suitableForHardware));
            }
        } else {
            items.add(new ImeSubtypeListItem(imeName, null /* subtypeName */, null /* layoutName */,
                    imi, NOT_A_SUBTYPE_INDEX, false /* showInImeSwitcherMenu */,
                    false /* isAuxiliary */, suitableForHardware));
        }
    }

    /**
     * Creates a single test {@link ImeSubtypeListItem}, based on the given IME and IME subtype
     * parameters.
     *
     * @param componentName the component name of the IME.
     * @param imeName       the name of the IME.
     * @param subtypeName   the name of the subtype.
     * @param subtypeLocale the locale of the subtype.
     * @param subtypeIndex  the index in the array of subtypes of the IME.
     */
    @NonNull
    private static ImeSubtypeListItem createTestItem(@NonNull ComponentName componentName,
            @NonNull String imeName, @NonNull String subtypeName, @NonNull String subtypeLocale,
            int subtypeIndex) {
        final var subtypes = new ArrayList<InputMethodSubtype>();
        subtypes.add(createTestSubtype(subtypeLocale));
        final var imi = createTestImi(componentName, subtypes,
                true /* supportsSwitchingToNextInputMethod */);
        return new ImeSubtypeListItem(imeName, subtypeName, null /* layoutName */,
                imi, subtypeIndex, true /* showInImeSwitcherMenu */, false /* isAuxiliary */,
                true /* suitableForHardware */);
    }

    private static ImeSubtypeListItem createTestItem(@NonNull String imeName,
            boolean showInImeSwitcherMenu, boolean isAuxiliary) {
        final var componentName = new ComponentName(TEST_PACKAGE_NAME, imeName);
        final var imi = createTestImi(componentName, null /* subtypes */,
                true /* supportsSwitchingToNextInputMethod */);
        return new ImeSubtypeListItem(imeName, null /* subtypeName */, null /* layoutName */,
                imi, NOT_A_SUBTYPE_INDEX, showInImeSwitcherMenu, isAuxiliary,
                false /* suitableForHardware */);
    }

    /**
     * Creates a test {@link InputMethodInfo} from the component name and subtypes.
     *
     * @param componentName                      the component name of the IME.
     * @param subtypes                           the list of subtypes of the IME.
     * @param supportsSwitchingToNextInputMethod whether the item supports switching to the next.
     */
    @NonNull
    private static InputMethodInfo createTestImi(@NonNull ComponentName componentName,
            @Nullable List<InputMethodSubtype> subtypes,
            boolean supportsSwitchingToNextInputMethod) {
        final var ai = new ApplicationInfo();
        ai.packageName = componentName.getPackageName();
        ai.enabled = true;
        final var si = new ServiceInfo();
        si.applicationInfo = ai;
        si.enabled = true;
        si.packageName = componentName.getPackageName();
        si.name = componentName.getClassName();
        si.exported = true;
        si.nonLocalizedLabel = componentName.getClassName();
        final var ri = new ResolveInfo();
        ri.serviceInfo = si;

        return new InputMethodInfo(ri, TEST_IS_AUX_IME,
                TEST_SETTING_ACTIVITY_NAME, subtypes, TEST_IS_DEFAULT_RES_ID,
                TEST_FORCE_DEFAULT, supportsSwitchingToNextInputMethod, TEST_IS_VR_IME);
    }

    /**
     * Creates a test {@link InputMethodSubtype} from the given locale.
     *
     * @param locale the locale to create a test subtype for.
     */
    @NonNull
    private static InputMethodSubtype createTestSubtype(@NonNull String locale) {
        return new InputMethodSubtypeBuilder()
                .setSubtypeLocale(locale)
                .setIsAsciiCapable(true)
                .build();
    }

    private static boolean onUserAction(@NonNull InputMethodSubtypeSwitchingController controller,
            @NonNull ImeSubtypeListItem item) {
        final var subtype = item.mSubtypeIndex != NOT_A_SUBTYPE_INDEX
                ? item.mImi.getSubtypeAt(item.mSubtypeIndex) : null;
        return controller.onUserAction(item.mImi, subtype);
    }

    /**
     * Verifies that the controller's next item order matches the given one, and cycles back at
     * the end, both across all IMEs, and also per each IME. If a single item is given, verifies
     * that no next item is returned.
     *
     * @param controller  the controller to use for finding the next items.
     * @param forHardware whether to find the next hardware item, or software item.
     * @param mode        the switching mode.
     * @param forward     whether to search forwards or backwards in the list.
     * @param allItems    the list of items across all IMEs.
     * @param perImeItems the list of lists of items per IME.
     */
    private static void assertNextOrder(@NonNull InputMethodSubtypeSwitchingController controller,
            boolean forHardware, @SwitchMode int mode, boolean forward,
            @NonNull List<ImeSubtypeListItem> allItems,
            @NonNull List<List<ImeSubtypeListItem>> perImeItems) {
        assertNextOrder(controller, false /* onlyCurrentIme */, forHardware, mode, forward,
                allItems);

        for (var imeItems : perImeItems) {
            assertNextOrder(controller, true /* onlyCurrentIme */, forHardware, mode, forward,
                    imeItems);
        }
    }

    /**
     * Verifies that the controller's next item order matches the given one, and cycles back at
     * the end, both across all IMEs, and also per each IME. This checks the forward direction
     * with the given items, and the backwards order with the items reversed. If a single item is
     * given, verifies that no next item is returned.
     *
     * @param controller  the controller to use for finding the next items.
     * @param forHardware whether to find the next hardware item, or software item.
     * @param mode        the switching mode.
     * @param allItems    the list of items across all IMEs.
     * @param perImeItems the list of lists of items per IME.
     */
    private static void assertNextOrder(@NonNull InputMethodSubtypeSwitchingController controller,
            boolean forHardware, @SwitchMode int mode, @NonNull List<ImeSubtypeListItem> allItems,
            @NonNull List<List<ImeSubtypeListItem>> perImeItems) {
        assertNextOrder(controller, false /* onlyCurrentIme */, forHardware, mode,
                true /* forward */, allItems);
        assertNextOrder(controller, false /* onlyCurrentIme */, forHardware, mode,
                false /* forward */, allItems.reversed());

        for (var imeItems : perImeItems) {
            assertNextOrder(controller, true /* onlyCurrentIme */, forHardware, mode,
                    true /* forward */, imeItems);
            assertNextOrder(controller, true /* onlyCurrentIme */, forHardware, mode,
                    false /* forward */, imeItems.reversed());
        }
    }

    /**
     * Verifies that the controller's next item order (starting from the first one in {@code items}
     * matches the given on, and cycles back at the end. If a single item is given, verifies that
     * no next item is returned.
     *
     * @param controller     the controller to use for finding the next items.
     * @param onlyCurrentIme whether to consider only subtypes of the current input method.
     * @param forHardware    whether to find the next hardware item, or software item.
     * @param mode           the switching mode.
     * @param forward        whether to search forwards or backwards in the list.
     * @param items          the list of items to verify, in the expected order.
     */
    private static void assertNextOrder(@NonNull InputMethodSubtypeSwitchingController controller,
            boolean onlyCurrentIme, boolean forHardware, @SwitchMode int mode, boolean forward,
            @NonNull List<ImeSubtypeListItem> items) {
        final int numItems = items.size();
        if (numItems == 0) {
            return;
        } else if (numItems == 1) {
            // Single item controllers should never return a next item.
            assertNextItem(controller, onlyCurrentIme, forHardware, mode, forward, items.getFirst(),
                    null /* expectedNext*/);
            return;
        }

        var item = items.getFirst();

        final var expectedNextItems = new ArrayList<>(items);
        // Add first item in the last position of expected order, to ensure the order is cyclic.
        expectedNextItems.add(item);

        final var nextItems = new ArrayList<>();
        // Add first item in the first position of actual order, to ensure the order is cyclic.
        nextItems.add(item);

        // Compute the nextItems starting from the first given item, and compare the order.
        for (int i = 0; i < numItems; i++) {
            item = getNextItem(controller, onlyCurrentIme, forHardware, mode, forward, item);
            assertNotNull("Next item shouldn't be null.", item);
            nextItems.add(item);
        }

        assertEquals("Rotation order doesn't match.", expectedNextItems, nextItems);
    }

    /**
     * Verifies that the controller gets the expected next value from the given item.
     *
     * @param controller     the controller to sue for finding the next value.
     * @param onlyCurrentIme whether to consider only subtypes of the current input method.
     * @param forHardware    whether to find the next hardware item, or software item.
     * @param mode           the switching mode.
     * @param forward        whether to search forwards or backwards in the list.
     * @param item           the item to find the next value from.
     * @param expectedNext   the expected next value.
     */
    private static void assertNextItem(@NonNull InputMethodSubtypeSwitchingController controller,
            boolean onlyCurrentIme, boolean forHardware, @SwitchMode int mode, boolean forward,
            @NonNull ImeSubtypeListItem item, @Nullable ImeSubtypeListItem expectedNext) {
        final var next = getNextItem(controller, onlyCurrentIme, forHardware, mode, forward, item);
        assertEquals("Next item doesn't match.", expectedNext, next);
    }

    /**
     * Gets the next value from the given item.
     *
     * @param controller     the controller to use for finding the next value.
     * @param onlyCurrentIme whether to consider only subtypes of the current input method.
     * @param forHardware    whether to find the next hardware item, or software item.
     * @param mode           the switching mode.
     * @param forward        whether to search forwards or backwards in the list.
     * @param item           the item to find the next value from.
     * @return the next item found, otherwise {@code null}.
     */
    @Nullable
    private static ImeSubtypeListItem getNextItem(
            @NonNull InputMethodSubtypeSwitchingController controller, boolean onlyCurrentIme,
            boolean forHardware, @SwitchMode int mode, boolean forward,
            @NonNull ImeSubtypeListItem item) {
        final var subtype = item.mSubtypeIndex != NOT_A_SUBTYPE_INDEX
                ? item.mImi.getSubtypeAt(item.mSubtypeIndex) : null;
        return controller.getNext(item.mImi, subtype, onlyCurrentIme, forHardware, mode, forward);
    }

    /**
     * Verifies that the expected next item is returned, and the recency cannot be updated for the
     * given items.
     *
     * @param controller   the controller to verify the items on.
     * @param forHardware  whether to try finding the next hardware item, or software item.
     * @param items        the list of items to verify.
     * @param expectedNext the expected next item.
     */
    private static void assertNextItemNoAction(
            @NonNull InputMethodSubtypeSwitchingController controller, boolean forHardware,
            @NonNull List<ImeSubtypeListItem> items, @Nullable ImeSubtypeListItem expectedNext) {
        for (var item : items) {
            for (int mode = MODE_STATIC; mode <= MODE_AUTO; mode++) {
                assertNextItem(controller, false /* onlyCurrentIme */, forHardware, mode,
                        false /* forward */, item, expectedNext);
                assertNextItem(controller, false /* onlyCurrentIme */, forHardware, mode,
                        true /* forward */, item, expectedNext);
                assertNextItem(controller, true /* onlyCurrentIme */, forHardware, mode,
                        false /* forward */, item, expectedNext);
                assertNextItem(controller, true /* onlyCurrentIme */, forHardware, mode,
                        true /* forward */, item, expectedNext);
            }

            assertFalse("User action shouldn't have updated the recency.",
                    onUserAction(controller, item));
        }
    }
}
