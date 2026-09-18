/*
 * Copyright 2017 The Android Open Source Project
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

package android.view.accessibility;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.os.Parcel;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.Display;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * AccessibilityEvent is public, so CTS covers it pretty well. Verifying hidden methods here.
 */
@RunWith(AndroidJUnit4.class)
public class AccessibilityEventTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    // The number of fields tested in the corresponding CTS AccessibilityEventTest:
    // See the CTS tests for AccessibilityRecord:
    // fullyPopulateAccessibilityEvent, assertEqualsAccessiblityEvent,
    // and assertAccessibilityEventCleared

    /** The number of properties of the {@link AccessibilityEvent} class. */
    private static final int A11Y_EVENT_NON_STATIC_FIELD_COUNT = 35;

    // The number of fields tested in the corresponding CTS AccessibilityRecordTest:
    // assertAccessibilityRecordCleared, fullyPopulateAccessibilityRecord,
    // and assertEqualAccessibilityRecord

    /** The number of properties of the {@link AccessibilityRecord} class. */
    private static final int A11Y_RECORD_NON_STATIC_FIELD_COUNT = 24;

    @Test
    public void testImportantForAccessibiity_getSetWorkAcrossParceling() {
        AccessibilityEvent event = new AccessibilityEvent();
        event.setImportantForAccessibility(true);
        assertTrue(copyEventViaParcel(event).isImportantForAccessibility());

        event.setImportantForAccessibility(false);
        assertFalse(copyEventViaParcel(event).isImportantForAccessibility());
    }

    @Test
    public void testSouceNodeId_getSetWorkAcrossParceling() {
        final long sourceNodeId = 0x1234567890ABCDEFL;
        AccessibilityEvent event = new AccessibilityEvent();
        event.setSourceNodeId(sourceNodeId);
        assertEquals(sourceNodeId, copyEventViaParcel(event).getSourceNodeId());
    }

    @Test
    public void testSourceDisplayId_getSetWorkAcrossParceling() {
        final int sourceDisplayId = Display.DEFAULT_DISPLAY;
        AccessibilityEvent event = new AccessibilityEvent();
        event.setDisplayId(sourceDisplayId);
        assertEquals(sourceDisplayId, copyEventViaParcel(event).getDisplayId());
    }

    @Test
    public void testWindowChanges_getSetWorkAcrossParceling() {
        final int windowChanges = AccessibilityEvent.WINDOWS_CHANGE_TITLE
                | AccessibilityEvent.WINDOWS_CHANGE_ACTIVE
                | AccessibilityEvent.WINDOWS_CHANGE_FOCUSED;
        AccessibilityEvent event = new AccessibilityEvent();
        event.setWindowChanges(windowChanges);
        assertEquals(windowChanges, copyEventViaParcel(event).getWindowChanges());
    }

    @Test
    public void testContentChangeTypes_getSetWorkAcrossParceling() {
        final int contentChangeTypes = AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
                | AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT;
        AccessibilityEvent event = new AccessibilityEvent();
        event.setContentChangeTypes(contentChangeTypes);
        assertEquals(contentChangeTypes, copyEventViaParcel(event).getContentChangeTypes());
    }

    @Test
    public void testSpeechStateChangeTypes_getSetWorkAcrossParceling() {
        final int speechStateChangeTypes = AccessibilityEvent.SPEECH_STATE_SPEAKING_START
                | AccessibilityEvent.SPEECH_STATE_LISTENING_START;
        AccessibilityEvent event = new AccessibilityEvent();
        event.setSpeechStateChangeTypes(speechStateChangeTypes);
        assertEquals(speechStateChangeTypes, copyEventViaParcel(event).getSpeechStateChangeTypes());
    }

    @Test
    @EnableFlags(Flags.FLAG_A11Y_TEXT_CHANGE_TYPES_API)
    public void testTextChangeTypes_getSetWorkAcrossParceling() {
        final int textChangeTypes = AccessibilityEvent.TEXT_CHANGE_TYPE_IN_COMPOSITION
                | AccessibilityEvent.TEXT_CHANGE_TYPE_CONVERSION_SUGGESTION_SELECTED_BY_IME;
        AccessibilityEvent event = new AccessibilityEvent();
        event.setTextChangeTypes(textChangeTypes);
        assertEquals(textChangeTypes, copyEventViaParcel(event).getTextChangeTypes());
    }

    @Test
    public void dontForgetToUpdateA11yRecordCtsParcelingTestWhenYouAddNewFields() {
        AccessibilityEventTest.assertNoNewNonStaticFieldsAdded(
                AccessibilityRecord.class, A11Y_RECORD_NON_STATIC_FIELD_COUNT);
    }

    @Test
    public void dontForgetToUpdateA11yEventCtsParcelingTestWhenYouAddNewFields() {
        AccessibilityEventTest.assertNoNewNonStaticFieldsAdded(
                AccessibilityEvent.class, A11Y_EVENT_NON_STATIC_FIELD_COUNT);
    }

    @Test
    public void testAppendRecord_nonNull() throws Exception {
        AccessibilityEvent event = new AccessibilityEvent();
        AccessibilityRecord record = new AccessibilityRecord();
        event.appendRecord(record);
        assertEquals(1, event.getRecordCount());
        assertEquals(record, event.getRecord(0));
    }

    @Test
    public void testAppendRecord_nullRecord() throws Exception {
        AccessibilityEvent event = new AccessibilityEvent();
        event.appendRecord(null);

        // A null record should not be appended.
        assertEquals(0, event.getRecordCount());

        // Verify with an existing list
        AccessibilityRecord nonNullRecord = new AccessibilityRecord();
        event.appendRecord(nonNullRecord);
        event.appendRecord(null);

        assertEquals(1, event.getRecordCount());
        assertEquals(nonNullRecord, event.getRecord(0));
    }

    private AccessibilityEvent copyEventViaParcel(AccessibilityEvent event) {
        Parcel parcel = Parcel.obtain();
        event.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        return AccessibilityEvent.CREATOR.createFromParcel(parcel);
    }

    /**
     * Asserts that no new fields have been added, so we are testing marshaling
     * of all such.
     */
    static void assertNoNewNonStaticFieldsAdded(Class<?> clazz, int expectedCount) {
        int nonStaticFieldCount = 0;

        while (clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if ((field.getModifiers() & Modifier.STATIC) == 0) {
                    nonStaticFieldCount++;
                }
            }
            clazz = clazz.getSuperclass();
        }

        String message = "New fields have been added, so add code to test marshaling them.";
        assertEquals(message, expectedCount, nonStaticFieldCount);
    }
}
