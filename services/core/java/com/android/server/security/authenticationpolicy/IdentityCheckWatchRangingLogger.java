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

package com.android.server.security.authenticationpolicy;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerExecutor;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings;
import android.proximity.IProximityProviderService;
import android.util.Slog;

import androidx.annotation.NonNull;

import java.util.function.Function;

/**
 * A {@link BroadcastReceiver} that is triggered daily by {@link AlarmManager} to
 * check and log the availability of watch ranging. This class interacts with
 * {@code ProximityProviderService} to determine if ranging is supported and updates
 * {@link Settings.Global.WATCH_RANGING_AVAILABLE}.
 *
 * @hide
 */
public class IdentityCheckWatchRangingLogger extends BroadcastReceiver {
    private static final String TAG = "IdentityCheckWatchRangingLogger";
    private static final String PROXIMITY_PROVIDER_SERVICE_BIND_INTENT_ACTION =
            "android.proximity.ProximityProviderService";
    public static final String ACTION_LOG_WATCH_RANGING_STATUS =
            "com.android.server.security.authenticationpolicy.log_watch_ranging_status";
    private static final int ALARM_REQUEST_CODE = 1001;

    private final String mProximityProviderServicePackageName;
    private final String mProximityProviderServiceClassName;
    private final Function<IBinder, IProximityProviderService> mProximityProviderServiceFunction;
    private final Handler mHandler;
    private final Context mContext;
    private ServiceConnection mServiceConnection;

    IdentityCheckWatchRangingLogger(@NonNull Context context,
            @NonNull String proximityProviderServiceClassName,
            @NonNull String proximityProviderServicePackageName,
            @NonNull Handler handler,
            @NonNull Function<IBinder, IProximityProviderService>
                    proximityProviderServiceFunction) {
        mContext = context;
        mProximityProviderServicePackageName = proximityProviderServicePackageName;
        mProximityProviderServiceClassName = proximityProviderServiceClassName;
        mProximityProviderServiceFunction = proximityProviderServiceFunction;
        mHandler = handler;
    }

    /**
     * Register this class to receive any broadcast of {@link ACTION_LOG_WATCH_RANGING_STATUS}.
     */
    public void registerReceiver() {
        final IntentFilter logWatchIntentFilter = new IntentFilter();
        logWatchIntentFilter.addAction(ACTION_LOG_WATCH_RANGING_STATUS);
        mContext.registerReceiver(this, logWatchIntentFilter, Context.RECEIVER_NOT_EXPORTED);
    }

    /**
     * Uses {@link AlarmManager} to schedule a broadcast of {@link ACTION_LOG_WATCH_RANGING_STATUS}
     * daily.
     */
    public void scheduleLogger() {
        AlarmManager alarmManager = mContext.getSystemService(AlarmManager.class);
        if (alarmManager == null) {
            Slog.e(TAG, "AlarmManager not available.");
            return;
        }

        Intent intent = new Intent(ACTION_LOG_WATCH_RANGING_STATUS);
        intent.setPackage(mContext.getPackageName());
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                mContext,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long firstAlarmTime = System.currentTimeMillis();

        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                firstAlarmTime,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
        );
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() != null
                && intent.getAction().equals(ACTION_LOG_WATCH_RANGING_STATUS)) {
            logIfWatchRangingIsAvailable(context);
        }
    }


    private void logIfWatchRangingIsAvailable(Context context) {
        mHandler.post(() -> {
            if (mServiceConnection != null) {
                Slog.d(TAG, "Proximity provider service is already connected.");
                return;
            }
            mServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    if (service == null) {
                        Slog.d(TAG, "No service found for proximity provider.");

                        return;
                    }
                    final IProximityProviderService proximityProviderService =
                            mProximityProviderServiceFunction.apply(service);
                    if (proximityProviderService == null) {
                        Slog.e(TAG, "Proximity provider service is null");
                        return;
                    }
                    try {
                        final boolean isWatchRangingSupported =
                                proximityProviderService.isProximityCheckingSupported();
                        Slog.d(TAG, "Updating watch ranging available value to "
                                + isWatchRangingSupported);
                        Settings.Global.putInt(context.getContentResolver(),
                                Settings.Global.WATCH_RANGING_AVAILABLE,
                                isWatchRangingSupported ? 1 : 0);
                    } catch (RemoteException e) {
                        Slog.e(TAG, "Remote exception thrown");
                    } finally {
                        context.unbindService(mServiceConnection);
                        mServiceConnection = null;
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    Slog.e(TAG, "Proximity provider service disconnected");
                    mServiceConnection = null;
                }
            };

            final Intent intent = new Intent(PROXIMITY_PROVIDER_SERVICE_BIND_INTENT_ACTION)
                    .setClassName(mProximityProviderServicePackageName,
                            mProximityProviderServiceClassName);
            final boolean bindSuccessful =
                    context.bindService(intent,
                            Context.BIND_IMPORTANT | Context.BIND_AUTO_CREATE /* flags */,
                            new HandlerExecutor(mHandler), mServiceConnection);

            if (!bindSuccessful) {
                Slog.d(TAG, "Couldn't find service for ProximityProviderService");
                mServiceConnection = null;
            }
        });
    }
}
