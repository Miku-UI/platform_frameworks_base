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

package com.android.server.media;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.media.AudioManager;
import android.media.IAudioService;
import android.media.MediaRoute2Info;
import android.media.RoutingSessionInfo;
import android.media.audiopolicy.AudioProductStrategy;
import android.os.Looper;
import android.os.ServiceManager;
import android.os.UserHandle;

import com.android.media.flags.Flags;

import java.util.List;

/**
 * Controls device routes.
 *
 * <p>A device route is a system wired route, for example, built-in speaker, wired
 * headsets and headphones, dock, hdmi, or usb devices.
 *
 * @see SystemMediaRoute2Provider
 */
/* package */ interface DeviceRouteController {

    /** Returns a new instance of {@link DeviceRouteController}. */
    @RequiresPermission(Manifest.permission.MODIFY_AUDIO_ROUTING)
    /* package */ static DeviceRouteController createInstance(
            @NonNull Context context,
            @NonNull Looper looper,
            @NonNull EventListener eventListener) {
        AudioManager audioManager = context.getSystemService(AudioManager.class);
        AudioProductStrategy strategyForMedia = AudioRoutingUtils.getMediaAudioProductStrategy();

        BluetoothManager bluetoothManager = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter btAdapter =
                bluetoothManager != null ? bluetoothManager.getAdapter() : null;

        // TODO: b/305199571 - Make the audio policies implementation work without the need for a
        // bluetooth adapter or a strategy for media. If no strategy for media is available we can
        // disallow media router transfers, and without a bluetooth adapter we can remove support
        // for transfers to inactive bluetooth routes.
        if (strategyForMedia != null && btAdapter != null) {
            AudioManagerRouteController controller =
                    AudioManagerRouteController.getInstance(
                            context, audioManager, looper, strategyForMedia, btAdapter);
            controller.registerRouteChangeListener(eventListener);
            return controller;
        } else {
            IAudioService audioService =
                    IAudioService.Stub.asInterface(
                            ServiceManager.getService(Context.AUDIO_SERVICE));
            return new LegacyDeviceRouteController(
                    context, audioManager, audioService, eventListener);
        }
    }

    /** Returns device route availability status. */
    @MediaRoute2Info.SuitabilityStatus
    static int getBuiltInSpeakerSuitabilityStatus(@NonNull Context context) {
        if (!Flags.enableBuiltInSpeakerRouteSuitabilityStatuses()) {
            // Route is always suitable if the flag is disabled.
            return MediaRoute2Info.SUITABILITY_STATUS_SUITABLE_FOR_DEFAULT_TRANSFER;
        }

        int availabilityStatus =
                context.getResources()
                        .getInteger(
                                com.android.internal.R.integer
                                        .config_mediaRouter_builtInSpeakerSuitability);

        switch (availabilityStatus) {
            case MediaRoute2Info.SUITABILITY_STATUS_SUITABLE_FOR_DEFAULT_TRANSFER:
            case MediaRoute2Info.SUITABILITY_STATUS_SUITABLE_FOR_MANUAL_TRANSFER:
            case MediaRoute2Info.SUITABILITY_STATUS_NOT_SUITABLE_FOR_TRANSFER:
                return availabilityStatus;
            default:
                return MediaRoute2Info.SUITABILITY_STATUS_SUITABLE_FOR_DEFAULT_TRANSFER;
        }
    }

    /** Returns a list of currently selected devices (built-in, wired or bt) route. */
    @NonNull
    List<MediaRoute2Info> getSelectedRoutes();

    /**
     * Returns all available routes.
     *
     * <p>Note that this method returns available routes including the selected route because (a)
     * this interface doesn't guarantee that the internal state of the controller won't change
     * between calls to {@link #getSelectedRoutes()} and this method and (b) {@link
     * #getSelectedRoutes()} may be treated as a transferable route (not a selected route) if the
     * selected route is from {@link BluetoothRouteController}.
     */
    List<MediaRoute2Info> getAvailableRoutes();

    /**
     * Returns a list of currently selectable routes.
     *
     * <p>For example, BLE devices can be grouped and will show up here.
     */
    @NonNull
    List<MediaRoute2Info> getSelectableRoutes();

    /**
     * Returns a list of currently deselectable routes.
     *
     * <p>For example, selected BLE devices in a broadcast session.
     */
    @NonNull
    List<MediaRoute2Info> getDeselectableRoutes();

    /**
     * Transfers device output to the given route.
     *
     * <p>If the route is {@code null} then active route will be deactivated.
     *
     * @param requestId Identifies the request.
     * @param routeId To switch to or {@code null} to unset the active device.
     */
    void transferTo(long requestId, @Nullable String routeId);

    /**
     * Adds the route with the given id to the current selected routes, making playback occur on all
     * selected routes simultaneously.
     *
     * @param routeId route that audio would be playing on.
     */
    void selectRoute(long requestId, @NonNull String routeId);

    /**
     * Removes the route with the given id from the current selected routes, playback will be
     * stopped for the related device.
     *
     * @param routeId route that audio would be stopped playing on.
     */
    void deselectRoute(long requestId, @NonNull String routeId);

    /**
     * Updates device route volume.
     *
     * @param volume specifies a volume for the device route or 0 for unknown.
     * @return {@code true} if updated successfully and {@code false} otherwise.
     */
    boolean updateVolume(int volume);

    /**
     * Sets device route volume.
     *
     * @param requestId identifies the request.
     * @param routeId to set the volume.
     * @param volume specifies a volume for the device route.
     */
    void setVolume(long requestId, @NonNull String routeId, int volume);

    /**
     * Starts listening for changes in the system to keep an up to date view of available and
     * selected devices.
     */
    void start(UserHandle mUser);

    /**
     * Stops keeping the internal state up to date with the system, releasing any resources acquired
     * in {@link #start}
     */
    void stop();

    /** Returns the release type of current session. */
    @RoutingSessionInfo.ReleaseType
    int getSessionReleaseType();

    /** Releases the routing session. */
    void releaseRoutingSession();

    /** Interface for receiving route events. */
    interface EventListener {

        /** Called when device route has changed. */
        void onDeviceRouteChanged();

        /** Called when device route request is failed. */
        void onDeviceRouteRequestFailed(long requestId, int reason);
    }

}
