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

package com.android.server.uilatencystats;

import static android.Manifest.permission.REPORT_UI_LATENCY_STATS;

import android.annotation.EnforcePermission;
import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.content.Context;
import android.os.Handler;
import android.os.UserHandle;
import android.uilatencystats.Event;
import android.uilatencystats.EventType;
import android.uilatencystats.IUiLatencyStats;
import android.uilatencystats.UiLatencyEventListener;
import android.uilatencystats.UiLatencyStatsManager;
import android.uilatencystats.UiLatencyStatsManagerInternal;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.IoThread;
import com.android.server.LocalServices;
import com.android.server.SystemService;
import com.android.server.SystemServiceManager;
import com.android.server.uilatencystats.listeners.BootStatsListener;
import com.android.server.uilatencystats.listeners.SignInLatencyEventListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service to record and report UI latency related statistics.
 *
 * @hide
 */
public class UiLatencyStatsService extends SystemService {
    private static final String TAG = "UiLatencyStatsService";

    private final Handler mHandler;
    private final Injector mInjector;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final SparseArray<CopyOnWriteArrayList<UiLatencyEventListener>> mEventListeners =
            new SparseArray<>();

    public UiLatencyStatsService(Context context) {
        this(context, new Injector());
    }

    @VisibleForTesting
    public UiLatencyStatsService(Context context, Injector injector) {
        super(context);
        mHandler = new Handler(IoThread.get().getLooper());
        mInjector = injector;
    }

    @Override
    public void onStart() {
        publishBinderService(Context.UI_LATENCY_STATS_SERVICE, new BinderService());
        publishLocalService(UiLatencyStatsManagerInternal.class, new LocalService());
        mInjector.registerBootstrapListeners(this);
    }

    @Override
    public void onUserSwitching(@NonNull TargetUser from, @NonNull TargetUser to) {
        final EventType eventType = new EventType.UserSwitch(to.getUserIdentifier());
        final Event event = new Event(eventType, from.getUserIdentifier());
        publishEvent(event);
    }

    /** Registers a UiLatencyEventListener */
    public void registerUiLatencyEventListener(UiLatencyEventListener listener) {
        for (int eventTypeId : listener.getEventIdsToListen()) {
            synchronized (mLock) {
                var currentListeners = mEventListeners.get(eventTypeId);
                if (currentListeners == null) {
                    currentListeners = new CopyOnWriteArrayList<>();
                }
                currentListeners.add(listener);
                mEventListeners.put(eventTypeId, currentListeners);
            }
        }
    }

    private void publishEvent(Event event) {
        final int id = event.getType().getId();
        final List<UiLatencyEventListener> listeners;
        synchronized (mLock) {
            listeners = mEventListeners.get(id);
        }
        if (listeners != null) {
            for (UiLatencyEventListener listener : listeners) {
                listener.onEvent(event);
            }
        }
    }

    private void performReportEvent(int eventId, @UserIdInt int userId, long timestamp) {
        final EventType type = UiLatencyStatsManager.getEventType(eventId);
        if (type == null) {
            Slog.w(TAG, "Unknown event type: " + eventId);
            return;
        }
        final Event event = new Event(type, userId, timestamp);
        publishEvent(event);
    }

    private final class BinderService extends IUiLatencyStats.Stub {
        @Override
        @EnforcePermission(REPORT_UI_LATENCY_STATS)
        public void reportEvent(int event, long timestamp) {
            reportEvent_enforcePermission();
            int userId = UserHandle.getCallingUserId();
            mHandler.post(
                    () -> {
                        UiLatencyStatsService.this.performReportEvent(event, userId, timestamp);
                    });
        }
    }

    private final class LocalService extends UiLatencyStatsManagerInternal {
        @Override
        public void registerUiLatencyEventListener(UiLatencyEventListener listener) {
            UiLatencyStatsService.this.registerUiLatencyEventListener(listener);
        }

        @Override
        public void publishEvent(Event event) {
            UiLatencyStatsService.this.publishEvent(event);
        }
    }

    @VisibleForTesting
    public static class Injector {
        /**
         * Registers the bootstrap listeners that are registered at the system server startup.
         */
        public void registerBootstrapListeners(UiLatencyStatsService service) {
            service.registerUiLatencyEventListener(
                    new BootStatsListener(LocalServices.getService(SystemServiceManager.class)));
            service.registerUiLatencyEventListener(new SignInLatencyEventListener());
        }
    }
}
