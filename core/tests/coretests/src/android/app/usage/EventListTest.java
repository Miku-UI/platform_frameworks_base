/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.app.usage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.usage.UsageEvents.Event;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class EventListTest {
    private static final String TAG = EventListTest.class.getSimpleName();

    private static Event getUsageEvent(long timeStamp) {
        return getUsageEvent(Event.SYSTEM_INTERACTION, timeStamp);
    }

    private static Event getUsageEvent(int eventType, long timeStamp) {
        final Event event = new Event();
        event.mEventType = eventType;
        event.mTimeStamp = timeStamp;
        event.mPackage = "android";
        return event;
    }

    private static Event getObfuscatedEvent(int eventType, long timeStamp) {
        final Event event = new Event();
        event.mEventType = eventType;
        event.mTimeStamp = timeStamp;
        event.mPackageToken = 1;
        return event;
    }

    private static Event getActivityEvent(int eventType, long timeStamp, int instanceId) {
        final Event event = getUsageEvent(eventType, timeStamp);
        event.mInstanceId = instanceId;
        return event;
    }

    private static Event getFgsEvent(int eventType, long timeStamp, String className) {
        final Event event = getUsageEvent(eventType, timeStamp);
        event.mClass = className;
        return event;
    }

    private static String getListTimeStamps(EventList list) {
        final StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < list.size() - 1; i++) {
            builder.append(list.get(i).mTimeStamp);
            builder.append(", ");
        }
        builder.append(list.get(list.size() - 1).mTimeStamp);
        builder.append("]");
        return builder.toString();
    }

    private static void assertSorted(EventList eventList) {
        for (int i = 1; i < eventList.size(); i++) {
            final long lastTimeStamp = eventList.get(i - 1).mTimeStamp;
            if (eventList.get(i).mTimeStamp < lastTimeStamp) {
                Log.e(TAG, "Unsorted timestamps in list: " + getListTimeStamps(eventList));
                fail("Timestamp " + eventList.get(i).mTimeStamp + " at " + i
                        + " follows larger timestamp " + lastTimeStamp);
            }
        }
    }

    @Test
    public void testInsertsSortedRandom() {
        final Random random = new Random(128);
        final EventList listUnderTest = new EventList();
        for (int i = 0; i < 100; i++) {
            listUnderTest.insert(getUsageEvent(random.nextLong()));
        }
        assertSorted(listUnderTest);
    }

    @Test
    public void testInsertsSortedWithDuplicates() {
        final Random random = new Random(256);
        final EventList listUnderTest = new EventList();
        for (int i = 0; i < 10; i++) {
            final long randomTimeStamp = random.nextLong();
            for (int j = 0; j < 10; j++) {
                listUnderTest.insert(getUsageEvent(randomTimeStamp));
            }
        }
        assertSorted(listUnderTest);
    }

    @Test
    public void testFirstIndexOnOrAfter() {
        final EventList listUnderTest = new EventList();
        listUnderTest.insert(getUsageEvent(2));
        listUnderTest.insert(getUsageEvent(5));
        listUnderTest.insert(getUsageEvent(5));
        listUnderTest.insert(getUsageEvent(5));
        listUnderTest.insert(getUsageEvent(8));
        assertTrue(listUnderTest.firstIndexOnOrAfter(1) == 0);
        assertTrue(listUnderTest.firstIndexOnOrAfter(2) == 0);
        assertTrue(listUnderTest.firstIndexOnOrAfter(3) == 1);
        assertTrue(listUnderTest.firstIndexOnOrAfter(4) == 1);
        assertTrue(listUnderTest.firstIndexOnOrAfter(5) == 1);
        assertTrue(listUnderTest.firstIndexOnOrAfter(6) == 4);
        assertTrue(listUnderTest.firstIndexOnOrAfter(7) == 4);
        assertTrue(listUnderTest.firstIndexOnOrAfter(8) == 4);
        assertTrue(listUnderTest.firstIndexOnOrAfter(9) == listUnderTest.size());
        assertTrue(listUnderTest.firstIndexOnOrAfter(100) == listUnderTest.size());

        listUnderTest.clear();
        assertTrue(listUnderTest.firstIndexOnOrAfter(5) == 0);
        assertTrue(listUnderTest.firstIndexOnOrAfter(100) == 0);
    }

    @Test
    public void testClear() {
        final EventList listUnderTest = new EventList();
        for (int i = 1; i <= 100; i++) {
            listUnderTest.insert(getUsageEvent(i));
        }
        listUnderTest.clear();
        assertEquals(0, listUnderTest.size());
    }

    @Test
    public void testSize() {
        final EventList listUnderTest = new EventList();
        for (int i = 1; i <= 100; i++) {
            listUnderTest.insert(getUsageEvent(i));
        }
        assertEquals(100, listUnderTest.size());
    }

    @Test
    public void testInsert_obfuscatedEvents_overLimit_doesNotDropsInserts() {
        final int numOfEventsToInsert = EventListRateLimiter.GENERAL_EVENTS_COUNT_LIMIT + 100;
        final EventList events = new EventList();
        for (int i = 0; i < numOfEventsToInsert; i++) {
            events.insertObfuscated(getObfuscatedEvent(Event.APP_COMPONENT_USED, 1));
        }
        assertEquals(numOfEventsToInsert, events.size());
    }

    @Test
    public void testInsert_generalEvents_overLimit_dropsInsert() {
        final int numOfEventsToInsert = EventListRateLimiter.GENERAL_EVENTS_COUNT_LIMIT + 100;
        final EventList events = new EventList();
        for (int i = 0; i < numOfEventsToInsert; i++) {
            events.insert(getUsageEvent(Event.APP_COMPONENT_USED, 1));
        }
        assertEquals(EventListRateLimiter.GENERAL_EVENTS_COUNT_LIMIT, events.size());
    }

    @Test
    public void testInsert_generalEvents_overLimit_replacesLastEvent() {
        final EventList events = new EventList();
        for (int i = 0; i < EventListRateLimiter.GENERAL_EVENTS_COUNT_LIMIT; i++) {
            events.insert(getUsageEvent(Event.APP_COMPONENT_USED, 1));
        }
        assertEquals(EventListRateLimiter.GENERAL_EVENTS_COUNT_LIMIT, events.size());
        assertEquals(1, events.get(events.size() - 1).mTimeStamp);

        // This should replace the last event.
        Event finalEvent = getUsageEvent(Event.APP_COMPONENT_USED, 2);
        events.insert(finalEvent);

        assertEquals(EventListRateLimiter.GENERAL_EVENTS_COUNT_LIMIT, events.size());
        // Assert that the last event in the list is now the new event.
        assertEquals(finalEvent.mTimeStamp, events.get(events.size() - 1).mTimeStamp);
    }

    @Test
    public void testInsert_generalEvents_differentTypes_doesNotDropInserts() {
        final int numOfEventsToInsert = EventListRateLimiter.GENERAL_EVENTS_COUNT_LIMIT - 1;
        final EventList events = new EventList();
        for (int i = 0; i < numOfEventsToInsert; i++) {
            events.insert(getUsageEvent(Event.APP_COMPONENT_USED, 1));
        }
        for (int i = 0; i < numOfEventsToInsert; i++) {
            events.insert(getUsageEvent(Event.CONFIGURATION_CHANGE, 2));
        }
        assertEquals(numOfEventsToInsert * 2, events.size());
    }

    @Test
    public void testInsert_generalEvents_afterThreshold_doesNotDropInserts() {
        final int numOfEventsToInsert = EventListRateLimiter.GENERAL_EVENTS_COUNT_LIMIT - 1;
        final long initialTimestamp = EventListRateLimiter.GENERAL_EVENT_THRESHOLD_WINDOW_MS;
        final EventList events = new EventList();
        for (int i = 0; i < numOfEventsToInsert; i++) {
            events.insert(getUsageEvent(Event.APP_COMPONENT_USED, initialTimestamp));
        }

        final long newTimestamp =
                initialTimestamp + EventListRateLimiter.GENERAL_EVENT_THRESHOLD_WINDOW_MS + 1;
        for (int i = 0; i < numOfEventsToInsert; i++) {
            events.insert(getUsageEvent(Event.APP_COMPONENT_USED, newTimestamp));
        }
        assertEquals(numOfEventsToInsert * 2, events.size());
    }

    @Test
    public void testInsert_userEvents_overLimit_dropsInsert() {
        final int numOfEventsToInsert = EventListRateLimiter.USER_EVENTS_COUNT_LIMIT + 10;
        EventList events = new EventList();
        // activity-based events
        for (int i = 0; i < numOfEventsToInsert; i++) {
            events.insert(getActivityEvent(Event.ACTIVITY_RESUMED, 1, 100));
        }
        assertEquals(EventListRateLimiter.USER_EVENTS_COUNT_LIMIT, events.size());

        // fgs-based events
        events = new EventList();
        for (int i = 0; i < numOfEventsToInsert; i++) {
            events.insert(getFgsEvent(Event.FOREGROUND_SERVICE_START, 1, "aClass"));
        }
        assertEquals(EventListRateLimiter.USER_EVENTS_COUNT_LIMIT, events.size());

        // other user-based events
        events = new EventList();
        for (int i = 0; i < numOfEventsToInsert; i++) {
            events.insert(getUsageEvent(Event.USER_INTERACTION, 1));
        }
        assertEquals(EventListRateLimiter.USER_EVENTS_COUNT_LIMIT, events.size());
    }

    @Test
    public void testInsert_userEvents_differentPackages_doesNotDropInserts() {
        final int numOfEventsToInsert = EventListRateLimiter.USER_EVENTS_COUNT_LIMIT - 1;
        final EventList events = new EventList();
        for (int i = 0; i < numOfEventsToInsert; i++) {
            events.insert(getUsageEvent(Event.USER_INTERACTION, 1));
        }
        for (int i = 0; i < numOfEventsToInsert; i++) {
            Event event = getUsageEvent(Event.USER_INTERACTION, 2);
            event.mPackage = "other";
            events.insert(event);
        }
        assertEquals(numOfEventsToInsert * 2, events.size());
    }

    @Test
    public void testInsert_userEvents_afterThreshold_doesNotDropInserts() {
        final int numOfEventsToInsert = EventListRateLimiter.USER_EVENTS_COUNT_LIMIT - 1;
        final long initialTimestamp = EventListRateLimiter.USER_EVENT_THRESHOLD_WINDOW_MS;
        final EventList events = new EventList();
        for (int i = 0; i < numOfEventsToInsert; i++) {
            events.insert(getUsageEvent(Event.USER_INTERACTION, initialTimestamp));
        }

        final long newTimestamp = initialTimestamp
                + EventListRateLimiter.USER_EVENT_THRESHOLD_WINDOW_MS + 1;
        for (int i = 0; i < numOfEventsToInsert; i++) {
            events.insert(getUsageEvent(Event.USER_INTERACTION, newTimestamp));
        }
        assertEquals(numOfEventsToInsert * 2, events.size());
    }

    @Test
    public void testInsert_activityEvents_overLimit_dropsInsert() {
        final Random random = new Random();
        final int numOfEventsToInsert = EventListRateLimiter.USER_EVENTS_COUNT_LIMIT + 10;
        final EventList events = new EventList();
        for (int i = 0; i < numOfEventsToInsert; i++) {
            int eventType = random.nextBoolean() ? Event.ACTIVITY_RESUMED : Event.ACTIVITY_PAUSED;
            events.insert(getActivityEvent(eventType, 1, 100));
        }
        assertEquals(EventListRateLimiter.USER_EVENTS_COUNT_LIMIT, events.size());
    }

    @Test
    public void testInsert_activityEvents_differentInstanceIds_doesNotDropInserts() {
        final int numOfEventsToInsert = EventListRateLimiter.USER_EVENTS_COUNT_LIMIT - 1;
        final EventList events = new EventList();
        for (int i = 0; i < numOfEventsToInsert; i++) {
            events.insert(getActivityEvent(Event.ACTIVITY_RESUMED, 1, 100));
        }
        for (int i = 0; i < numOfEventsToInsert; i++) {
            events.insert(getActivityEvent(Event.ACTIVITY_RESUMED, 1, 101));
        }
        assertEquals(numOfEventsToInsert * 2, events.size());
    }

    @Test
    public void testInsert_activityEvents_afterThreshold_doesNotDropInserts() { //
        final int numOfEventsToInsert = EventListRateLimiter.USER_EVENTS_COUNT_LIMIT - 1;
        final long initialTimestamp = EventListRateLimiter.USER_EVENT_THRESHOLD_WINDOW_MS;
        final EventList events = new EventList();
        for (int i = 0; i < numOfEventsToInsert; i++) {
            events.insert(getActivityEvent(Event.ACTIVITY_RESUMED, initialTimestamp, 100));
        }

        final long newTimestamp = initialTimestamp
                + EventListRateLimiter.USER_EVENT_THRESHOLD_WINDOW_MS + 1;
        for (int i = 0; i < numOfEventsToInsert; i++) {
            events.insert(getActivityEvent(Event.ACTIVITY_RESUMED, newTimestamp, 100));
        }
        assertEquals(numOfEventsToInsert * 2, events.size());
    }

    @Test
    public void testInsert_fgsEvents_overLimit_dropsInsert() {
        final Random random = new Random();
        final int numOfEventsToInsert = EventListRateLimiter.USER_EVENTS_COUNT_LIMIT + 10;
        final EventList events = new EventList();
        for (int i = 0; i < numOfEventsToInsert; i++) {
            int eventType = random.nextBoolean()
                    ? Event.FOREGROUND_SERVICE_START
                    : Event.FOREGROUND_SERVICE_STOP;
            events.insert(getFgsEvent(eventType, 1, "aClass"));
        }
        assertEquals(EventListRateLimiter.USER_EVENTS_COUNT_LIMIT, events.size());
    }

    @Test
    public void testInsert_fgsEvents_differentClasses_doesNotDropInserts() {
        final int numOfEventsToInsert = EventListRateLimiter.USER_EVENTS_COUNT_LIMIT - 1;
        final EventList events = new EventList();
        for (int i = 0; i < numOfEventsToInsert; i++) {
            events.insert(getFgsEvent(Event.FOREGROUND_SERVICE_START, 1, "aClass"));
        }
        for (int i = 0; i < numOfEventsToInsert; i++) {
            events.insert(getFgsEvent(Event.FOREGROUND_SERVICE_START, 1, "bClass"));
        }
        assertEquals(numOfEventsToInsert * 2, events.size());
    }

    @Test
    public void testInsert_fgsEvents_afterThreshold_doesNotDropInserts() {
        final int numOfEventsToInsert = EventListRateLimiter.USER_EVENTS_COUNT_LIMIT - 1;
        final long initialTimestamp = EventListRateLimiter.USER_EVENT_THRESHOLD_WINDOW_MS;
        final EventList events = new EventList();
        for (int i = 0; i < numOfEventsToInsert; i++) {
            events.insert(getFgsEvent(Event.FOREGROUND_SERVICE_START, initialTimestamp, "aClass"));
        }

        final long newTimestamp = initialTimestamp
                + EventListRateLimiter.USER_EVENT_THRESHOLD_WINDOW_MS + 1;
        for (int i = 0; i < numOfEventsToInsert; i++) {
            events.insert(getFgsEvent(Event.FOREGROUND_SERVICE_START, newTimestamp, "aClass"));
        }
        assertEquals(numOfEventsToInsert * 2, events.size());
    }
}
