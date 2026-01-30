/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.companiondevicemanager;

import static android.companion.AssociationRequest.DEVICE_PROFILE_APP_STREAMING;
import static android.companion.AssociationRequest.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION;
import static android.companion.AssociationRequest.DEVICE_PROFILE_COMPUTER;
import static android.companion.AssociationRequest.DEVICE_PROFILE_FITNESS_TRACKER;
import static android.companion.AssociationRequest.DEVICE_PROFILE_GLASSES;
import static android.companion.AssociationRequest.DEVICE_PROFILE_MEDICAL;
import static android.companion.AssociationRequest.DEVICE_PROFILE_NEARBY_DEVICE_STREAMING;
import static android.companion.AssociationRequest.DEVICE_PROFILE_VIRTUAL_DEVICE;
import static android.companion.AssociationRequest.DEVICE_PROFILE_WATCH;
import static android.companion.AssociationRequest.DEVICE_PROFILE_WEARABLE_SENSING;
import static android.companion.CompanionResources.PERMISSION_ADD_MIRROR_DISPLAY;
import static android.companion.CompanionResources.PERMISSION_ADD_TRUSTED_DISPLAY;
import static android.companion.CompanionResources.PERMISSION_BYPASS_DND;
import static android.companion.CompanionResources.PERMISSION_CALENDAR;
import static android.companion.CompanionResources.PERMISSION_CALL_LOGS;
import static android.companion.CompanionResources.PERMISSION_CHANGE_MEDIA_OUTPUT;
import static android.companion.CompanionResources.PERMISSION_CONTACTS;
import static android.companion.CompanionResources.PERMISSION_CREATE_VIRTUAL_DEVICE;
import static android.companion.CompanionResources.PERMISSION_MICROPHONE;
import static android.companion.CompanionResources.PERMISSION_NEARBY_DEVICES;
import static android.companion.CompanionResources.PERMISSION_NOTIFICATIONS;
import static android.companion.CompanionResources.PERMISSION_NOTIFICATION_LISTENER_ACCESS;
import static android.companion.CompanionResources.PERMISSION_PHONE;
import static android.companion.CompanionResources.PERMISSION_POST_NOTIFICATIONS;
import static android.companion.CompanionResources.PERMISSION_SCHEDULE_EXACT_ALARM;
import static android.companion.CompanionResources.PERMISSION_SMS;
import static android.companion.CompanionResources.PERMISSION_STORAGE;

import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;

import android.util.ArrayMap;
import android.util.ArraySet;

import java.util.Map;
import java.util.Set;

/**
 * A class contains maps that have deviceProfile as the key and resourceId as the value
 * for the corresponding profile.
 */
final class CompanionDeviceResources {

    static final Map<Integer, Integer> PERMISSION_TITLES;
    static {
        final Map<Integer, Integer> map = new ArrayMap<>();
        map.put(PERMISSION_NOTIFICATION_LISTENER_ACCESS, R.string.permission_notifications);
        map.put(PERMISSION_STORAGE, R.string.permission_storage);
        map.put(PERMISSION_PHONE, R.string.permission_phone);
        map.put(PERMISSION_SMS, R.string.permission_sms);
        map.put(PERMISSION_CONTACTS, R.string.permission_contacts);
        map.put(PERMISSION_CALENDAR, R.string.permission_calendar);
        map.put(PERMISSION_NEARBY_DEVICES, R.string.permission_nearby_devices);
        map.put(PERMISSION_MICROPHONE, R.string.permission_microphone);
        map.put(PERMISSION_CALL_LOGS, R.string.permission_call_logs);
        map.put(PERMISSION_NOTIFICATIONS, R.string.permission_notifications);
        map.put(PERMISSION_CHANGE_MEDIA_OUTPUT, R.string.permission_media_routing_control);
        map.put(PERMISSION_POST_NOTIFICATIONS, R.string.permission_notifications);
        map.put(PERMISSION_CREATE_VIRTUAL_DEVICE, R.string.permission_create_virtual_device);
        map.put(PERMISSION_ADD_MIRROR_DISPLAY, R.string.permission_add_mirror_display);
        map.put(PERMISSION_ADD_TRUSTED_DISPLAY, R.string.permission_add_trusted_display);
        map.put(PERMISSION_SCHEDULE_EXACT_ALARM, R.string.permission_schedule_exact_alarm);
        map.put(PERMISSION_BYPASS_DND, R.string.permission_bypass_dnd);
        PERMISSION_TITLES = unmodifiableMap(map);
    }

    static final Map<Integer, Integer> PERMISSION_SUMMARIES;
    static {
        final Map<Integer, Integer> map = new ArrayMap<>();
        map.put(PERMISSION_NOTIFICATION_LISTENER_ACCESS,
                R.string.permission_notification_listener_access_summary);
        map.put(PERMISSION_PHONE, R.string.permission_phone_summary);
        map.put(PERMISSION_SMS, R.string.permission_sms_summary);
        map.put(PERMISSION_CONTACTS, R.string.permission_contacts_summary);
        map.put(PERMISSION_CALENDAR, R.string.permission_calendar_summary);
        map.put(PERMISSION_NEARBY_DEVICES, R.string.permission_nearby_devices_summary);
        map.put(PERMISSION_MICROPHONE, R.string.permission_microphone_summary);
        map.put(PERMISSION_CALL_LOGS, R.string.permission_call_logs_summary);
        map.put(PERMISSION_NOTIFICATIONS, R.string.permission_notifications_summary);
        map.put(PERMISSION_CHANGE_MEDIA_OUTPUT, R.string.permission_media_routing_control_summary);
        map.put(PERMISSION_POST_NOTIFICATIONS, R.string.permission_post_notifications_summary);
        map.put(PERMISSION_CREATE_VIRTUAL_DEVICE,
                R.string.permission_create_virtual_device_summary);
        map.put(PERMISSION_ADD_MIRROR_DISPLAY, R.string.permission_add_mirror_display_summary);
        map.put(PERMISSION_ADD_TRUSTED_DISPLAY, R.string.permission_add_trusted_display_summary);
        map.put(PERMISSION_SCHEDULE_EXACT_ALARM, R.string.permission_schedule_exact_alarm_summary);
        map.put(PERMISSION_BYPASS_DND, R.string.permission_bypass_dnd_summary);
        PERMISSION_SUMMARIES = unmodifiableMap(map);
    }

    static final Map<Integer, Integer> PERMISSION_ICONS;
    static {
        final Map<Integer, Integer> map = new ArrayMap<>();
        map.put(PERMISSION_NOTIFICATION_LISTENER_ACCESS, R.drawable.ic_permission_notifications);
        map.put(PERMISSION_STORAGE, R.drawable.ic_permission_storage);
        map.put(PERMISSION_PHONE, R.drawable.ic_permission_phone);
        map.put(PERMISSION_SMS, R.drawable.ic_permission_sms);
        map.put(PERMISSION_CONTACTS, R.drawable.ic_permission_contacts);
        map.put(PERMISSION_CALENDAR, R.drawable.ic_permission_calendar);
        map.put(PERMISSION_NEARBY_DEVICES, R.drawable.ic_permission_nearby_devices);
        map.put(PERMISSION_MICROPHONE, R.drawable.ic_permission_microphone);
        map.put(PERMISSION_CALL_LOGS, R.drawable.ic_permission_call_logs);
        map.put(PERMISSION_NOTIFICATIONS, R.drawable.ic_permission_notifications);
        map.put(PERMISSION_CHANGE_MEDIA_OUTPUT, R.drawable.ic_permission_media_routing_control);
        map.put(PERMISSION_POST_NOTIFICATIONS, R.drawable.ic_permission_notifications);
        map.put(PERMISSION_CREATE_VIRTUAL_DEVICE, R.drawable.ic_permission_create_virtual_device);
        map.put(PERMISSION_ADD_MIRROR_DISPLAY, R.drawable.ic_permission_add_mirror_display);
        map.put(PERMISSION_ADD_TRUSTED_DISPLAY, R.drawable.ic_permission_add_trusted_display);
        map.put(PERMISSION_SCHEDULE_EXACT_ALARM, R.drawable.ic_permission_schedule_exact_alarm);
        map.put(PERMISSION_BYPASS_DND, R.drawable.ic_permission_bypass_dnd);
        PERMISSION_ICONS = unmodifiableMap(map);
    }

    // Profile resources
    static final Map<String, Integer> PROFILE_TITLES;
    static {
        final Map<String, Integer> map = new ArrayMap<>();
        map.put(DEVICE_PROFILE_APP_STREAMING, R.string.title_app_streaming);
        map.put(DEVICE_PROFILE_AUTOMOTIVE_PROJECTION, R.string.title_automotive_projection);
        map.put(DEVICE_PROFILE_COMPUTER, R.string.title_computer);
        map.put(DEVICE_PROFILE_NEARBY_DEVICE_STREAMING, R.string.title_nearby_device_streaming);
        map.put(DEVICE_PROFILE_VIRTUAL_DEVICE, R.string.title_virtual_device);
        map.put(DEVICE_PROFILE_WATCH, R.string.confirmation_title);
        map.put(DEVICE_PROFILE_FITNESS_TRACKER, R.string.confirmation_title);
        map.put(DEVICE_PROFILE_GLASSES, R.string.confirmation_title_glasses);
        map.put(DEVICE_PROFILE_MEDICAL, R.string.confirmation_title);
        map.put(null, R.string.confirmation_title);

        PROFILE_TITLES = unmodifiableMap(map);
    }

    static final Map<String, Integer> PROFILE_SUMMARIES;
    static {
        final Map<String, Integer> map = new ArrayMap<>();
        map.put(DEVICE_PROFILE_WATCH, R.string.summary_watch);
        map.put(DEVICE_PROFILE_FITNESS_TRACKER, R.string.summary_watch);
        map.put(DEVICE_PROFILE_GLASSES, R.string.summary_glasses);
        map.put(DEVICE_PROFILE_MEDICAL, R.string.summary_glasses);
        if (android.companion.virtualdevice.flags.Flags.itemizedVdmPermissions()) {
            map.put(DEVICE_PROFILE_APP_STREAMING, R.string.summary_app_streaming);
            map.put(DEVICE_PROFILE_NEARBY_DEVICE_STREAMING,
                    R.string.summary_nearby_device_streaming);
        } else {
            map.put(DEVICE_PROFILE_APP_STREAMING, R.string.summary_app_streaming_legacy);
            map.put(DEVICE_PROFILE_NEARBY_DEVICE_STREAMING,
                    R.string.summary_nearby_device_streaming_legacy);
        }
        map.put(DEVICE_PROFILE_VIRTUAL_DEVICE, R.string.summary_virtual_device);
        map.put(null, R.string.summary_generic);

        PROFILE_SUMMARIES = unmodifiableMap(map);
    }

    static final Map<String, Integer> PROFILE_HELPER_SUMMARIES;
    static {
        final Map<String, Integer> map = new ArrayMap<>();
        map.put(DEVICE_PROFILE_APP_STREAMING, R.string.helper_summary_app_streaming);
        map.put(DEVICE_PROFILE_NEARBY_DEVICE_STREAMING,
                R.string.helper_summary_nearby_device_streaming);
        map.put(DEVICE_PROFILE_COMPUTER, R.string.helper_summary_computer);

        PROFILE_HELPER_SUMMARIES = unmodifiableMap(map);
    }

    static final Map<String, Integer> PROFILE_NAMES;
    static {
        final Map<String, Integer> map = new ArrayMap<>();
        map.put(DEVICE_PROFILE_WATCH, R.string.profile_name_watch);
        map.put(DEVICE_PROFILE_FITNESS_TRACKER, R.string.profile_name_fitness_tracker);
        map.put(DEVICE_PROFILE_GLASSES, R.string.profile_name_glasses);
        map.put(DEVICE_PROFILE_MEDICAL, R.string.profile_name_medical);
        map.put(DEVICE_PROFILE_VIRTUAL_DEVICE, R.string.profile_name_generic);
        map.put(null, R.string.profile_name_generic);

        PROFILE_NAMES = unmodifiableMap(map);
    }

    static final Map<String, Integer> PROFILE_ICONS;
    static {
        final Map<String, Integer> map = new ArrayMap<>();
        map.put(DEVICE_PROFILE_WATCH, R.drawable.ic_watch);
        map.put(DEVICE_PROFILE_FITNESS_TRACKER, R.drawable.ic_fitness_tracker);
        map.put(DEVICE_PROFILE_GLASSES, R.drawable.ic_glasses);
        map.put(DEVICE_PROFILE_MEDICAL, R.drawable.ic_medical);
        map.put(DEVICE_PROFILE_VIRTUAL_DEVICE, R.drawable.ic_device_other);
        map.put(null, R.drawable.ic_device_other);

        PROFILE_ICONS = unmodifiableMap(map);
    }

    static final Set<String> SUPPORTED_PROFILES;
    static {
        final Set<String> set = new ArraySet<>();
        set.add(DEVICE_PROFILE_WATCH);
        set.add(DEVICE_PROFILE_FITNESS_TRACKER);
        set.add(DEVICE_PROFILE_GLASSES);
        set.add(DEVICE_PROFILE_MEDICAL);
        set.add(DEVICE_PROFILE_VIRTUAL_DEVICE);
        set.add(null);

        SUPPORTED_PROFILES = unmodifiableSet(set);
    }

    static final Set<String> SUPPORTED_SELF_MANAGED_PROFILES;
    static {
        final Set<String> set = new ArraySet<>();
        set.add(DEVICE_PROFILE_APP_STREAMING);
        set.add(DEVICE_PROFILE_COMPUTER);
        set.add(DEVICE_PROFILE_AUTOMOTIVE_PROJECTION);
        set.add(DEVICE_PROFILE_NEARBY_DEVICE_STREAMING);
        set.add(DEVICE_PROFILE_WEARABLE_SENSING);
        set.add(null);

        SUPPORTED_SELF_MANAGED_PROFILES = unmodifiableSet(set);
    }
}
