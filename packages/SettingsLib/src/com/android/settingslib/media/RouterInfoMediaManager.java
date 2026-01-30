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

package com.android.settingslib.media;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaRoute2Info;
import android.media.MediaRouter2;
import android.media.MediaRouter2.DeviceSuggestionsUpdatesCallback;
import android.media.MediaRouter2.RoutingController;
import android.media.MediaRouter2Manager;
import android.media.RouteDiscoveryPreference;
import android.media.RouteListingPreference;
import android.media.RoutingChangeInfo;
import android.media.RoutingSessionInfo;
import android.media.SuggestedDeviceInfo;
import android.media.session.MediaController;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.media.flags.Flags;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Implements {@link InfoMediaManager} using {@link MediaRouter2}. */
@SuppressLint("MissingPermission")
public final class RouterInfoMediaManager extends InfoMediaManager {

    private static final String TAG = "RouterInfoMediaManager";

    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final MediaRouter2 mRouter;
    @VisibleForTesting
    MediaRouter2Manager mRouterManager;

    @VisibleForTesting
    final RouteCallback mRouteCallback = new RouteCallback();
    @VisibleForTesting
    final TransferCallback mTransferCallback = new TransferCallback();
    @VisibleForTesting
    final ControllerCallback mControllerCallback = new ControllerCallback();
    @VisibleForTesting
    final Consumer<RouteListingPreference> mRouteListingPreferenceCallback =
            (preference) -> {
                if (DEBUG) {
                    Log.d(TAG,
                            "onRouteListingPreferenceUpdated(), hasRLP: " + (preference != null));
                }
                notifyRouteListingPreferenceUpdated(preference);
                refreshDevices();
            };

    private final DeviceSuggestionsUpdatesCallback mDeviceSuggestionsUpdatesCallback =
            new DeviceSuggestionsUpdatesCallback() {
                @Override
                public void onSuggestionsUpdated(
                        String suggestingPackageName,
                        List<SuggestedDeviceInfo> suggestedDeviceInfo) {
                    Log.i(TAG, "onSuggestionsUpdated(), packageName: " + suggestingPackageName
                            + ", deviceInfo: " + suggestedDeviceInfo);
                    notifyDeviceSuggestionUpdated(suggestingPackageName, suggestedDeviceInfo);
                }

                @Override
                public void onSuggestionsCleared(String suggestingPackageName) {
                    Log.i(TAG, "onSuggestionsCleared(), packageName: " + suggestingPackageName);
                    notifyDeviceSuggestionUpdated(suggestingPackageName, null);
                }

                @Override
                public void onSuggestionsRequested() {} // no-op
            };

    @GuardedBy("InfoMediaManager.this.fieldName")
    @Nullable
    private MediaRouter2.ScanToken mScanToken;

    // TODO (b/321969740): Plumb target UserHandle between UMO and RouterInfoMediaManager.
    /* package */ RouterInfoMediaManager(
            Context context,
            @NonNull String packageName,
            @NonNull UserHandle userHandle,
            LocalBluetoothManager localBluetoothManager,
            @Nullable MediaController mediaController)
            throws PackageNotAvailableException {
        super(context, packageName, userHandle, localBluetoothManager, mediaController);

        MediaRouter2 router = null;

        if (Flags.enableCrossUserRoutingInMediaRouter2()) {
            try {
                router = MediaRouter2.getInstance(context, packageName, userHandle);
            } catch (IllegalArgumentException ex) {
                // Do nothing
            }
        } else {
            router = MediaRouter2.getInstance(context, packageName);
        }
        if (router == null) {
            throw new PackageNotAvailableException(
                    "Package name " + packageName + " does not exist.");
        }
        // We have to defer initialization because mRouter is final.
        mRouter = router;

        mRouterManager = MediaRouter2Manager.getInstance(context);
    }

    @VisibleForTesting
    RouterInfoMediaManager(
            Context context,
            @NonNull String packageName,
            @NonNull UserHandle userHandle,
            LocalBluetoothManager localBluetoothManager,
            @Nullable MediaController mediaController,
            MediaRouter2 mediaRouter2,
            MediaRouter2Manager mediaRouter2Manager) {
        super(context, packageName, userHandle, localBluetoothManager, mediaController);
        mRouter = mediaRouter2;
        mRouterManager = mediaRouter2Manager;
    }

    @Override
    protected void startScanOnRouter() {
        if (Flags.enableScreenOffScanning()) {
            synchronized (super.mLock) {
                if (mScanToken == null) {
                    MediaRouter2.ScanRequest request =
                            new MediaRouter2.ScanRequest.Builder().build();
                    mScanToken = mRouter.requestScan(request);
                }
            }
        } else {
            mRouter.startScan();
        }
    }

    @Override
    protected void registerRouter(Executor executor) {
        mRouter.registerRouteCallback(executor, mRouteCallback, RouteDiscoveryPreference.EMPTY);
        mRouter.registerRouteListingPreferenceUpdatedCallback(
                executor, mRouteListingPreferenceCallback);
        mRouter.registerDeviceSuggestionsUpdatesCallback(
                executor, mDeviceSuggestionsUpdatesCallback);
        if (Flags.enableSuggestedDeviceApi()) {
            for (Map.Entry<String, List<SuggestedDeviceInfo>> entry :
                    mRouter.getDeviceSuggestions().entrySet()) {
                notifyDeviceSuggestionUpdated(entry.getKey(), entry.getValue());
            }
        }
        mRouter.registerTransferCallback(executor, mTransferCallback);
        mRouter.registerControllerCallback(executor, mControllerCallback);
    }

    @Override
    protected void stopScanOnRouter() {
        if (Flags.enableScreenOffScanning()) {
            synchronized (super.mLock) {
                if (mScanToken != null) {
                    mRouter.cancelScanRequest(mScanToken);
                    mScanToken = null;
                }
            }
        } else {
            mRouter.stopScan();
        }
    }

    @Override
    protected void unregisterRouter() {
        mRouter.unregisterControllerCallback(mControllerCallback);
        mRouter.unregisterTransferCallback(mTransferCallback);
        mRouter.unregisterRouteListingPreferenceUpdatedCallback(mRouteListingPreferenceCallback);
        mRouter.unregisterDeviceSuggestionsUpdatesCallback(mDeviceSuggestionsUpdatesCallback);
        mRouter.unregisterRouteCallback(mRouteCallback);
    }

    @Override
    protected void transferToRoute(
            @NonNull MediaRoute2Info route, @NonNull RoutingChangeInfo routingChangeInfo) {
        mRouter.transferTo(route, routingChangeInfo);
    }

    @Override
    protected void selectRoute(
            @NonNull MediaRoute2Info route,
            @NonNull RoutingSessionInfo info,
            @NonNull RoutingChangeInfo routingChangeInfo) {
        RoutingController controller = getControllerForSession(info);
        if (controller != null) {
            controller.selectRoute(route, routingChangeInfo);
        }
    }

    @Override
    protected void deselectRoute(
            @NonNull MediaRoute2Info route,
            @NonNull RoutingSessionInfo info,
            @NonNull RoutingChangeInfo routingChangeInfo) {
        RoutingController controller = getControllerForSession(info);
        if (controller != null) {
            controller.deselectRoute(route, routingChangeInfo);
        }
    }

    @Override
    protected void releaseSession(@NonNull RoutingSessionInfo sessionInfo) {
        RoutingController controller = getControllerForSession(sessionInfo);
        if (controller != null) {
            controller.release();
        }
    }

    @NonNull
    @Override
    protected List<MediaRoute2Info> getSelectableRoutes(@NonNull RoutingSessionInfo info) {
        RoutingController controller = getControllerForSession(info);
        if (controller == null) {
            return Collections.emptyList();
        }

        // Filter out selected routes.
        List<String> selectedRouteIds = controller.getRoutingSessionInfo().getSelectedRoutes();
        return controller.getSelectableRoutes().stream()
                .filter(route -> !selectedRouteIds.contains(route.getId()))
                .collect(Collectors.toList());
    }

    @NonNull
    @Override
    protected List<MediaRoute2Info> getDeselectableRoutes(@NonNull RoutingSessionInfo info) {
        RoutingController controller = getControllerForSession(info);
        if (controller == null) {
            return Collections.emptyList();
        }

        return controller.getDeselectableRoutes();
    }

    @NonNull
    @Override
    protected List<MediaRoute2Info> getTransferableRoutes(@NonNull RoutingSessionInfo info) {
        RoutingController controller = getControllerForSession(info);
        return getTransferableRoutes(controller);
    }

    @NonNull
    @Override
    protected List<MediaRoute2Info> getSelectedRoutes(@NonNull RoutingSessionInfo info) {
        RoutingController controller = getControllerForSession(info);
        if (controller == null) {
            return Collections.emptyList();
        }
        return controller.getSelectedRoutes();
    }

    @Override
    protected void setSessionVolume(@NonNull RoutingSessionInfo info, int volume) {
        // TODO: b/291277292 - Implement MediaRouter2-based solution. Keeping MR2Manager call as
        //      MR2 filters information by package name.
        mRouterManager.setSessionVolume(info, volume);
    }

    @Override
    protected void setRouteVolume(@NonNull MediaRoute2Info route, int volume) {
        mRouter.setRouteVolume(route, volume);
    }

    @Nullable
    @Override
    protected RouteListingPreference getRouteListingPreference() {
        return mRouter.getRouteListingPreference();
    }

    @NonNull
    @Override
    protected List<RoutingSessionInfo> getRemoteSessions() {
        // TODO: b/291277292 - Implement MediaRouter2-based solution. Keeping MR2Manager call as
        //      MR2 filters information by package name.
        return mRouterManager.getRemoteSessions();
    }

    @NonNull
    @Override
    protected List<RoutingSessionInfo> getRoutingSessionsForPackage() {
        return mRouter.getControllers().stream()
                .map(RoutingController::getRoutingSessionInfo)
                .collect(Collectors.toList());
    }

    @Nullable
    @Override
    protected RoutingSessionInfo getRoutingSessionById(@NonNull String sessionId) {
        // TODO: b/291277292 - Implement MediaRouter2-based solution. Keeping MR2Manager calls as
        //      MR2 filters information by package name.

        for (RoutingSessionInfo sessionInfo : getRemoteSessions()) {
            if (TextUtils.equals(sessionInfo.getId(), sessionId)) {
                return sessionInfo;
            }
        }

        RoutingSessionInfo systemSession = mRouterManager.getSystemRoutingSession(null);
        return TextUtils.equals(systemSession.getId(), sessionId) ? systemSession : null;
    }

    @NonNull
    @Override
    protected List<MediaRoute2Info> getAvailableRoutesFromRouter() {
        return mRouter.getRoutes();
    }

    @NonNull
    @Override
    protected List<MediaRoute2Info> getTransferableRoutes(@NonNull String packageName) {
        List<RoutingController> controllers = mRouter.getControllers();
        RoutingController activeController = controllers.get(controllers.size() - 1);
        return getTransferableRoutes(activeController);
    }

    @Override
    public void requestDeviceSuggestion() {
        Log.i(TAG, "requestDeviceSuggestion()");
        mRouter.notifyDeviceSuggestionRequested();
    }

    @NonNull
    private List<MediaRoute2Info> getTransferableRoutes(@Nullable RoutingController controller) {
        HashMap<String, MediaRoute2Info> transferableRoutes = new HashMap<>();
        if (controller != null) {
            controller
                    .getTransferableRoutes()
                    .forEach(route -> transferableRoutes.put(route.getId(), route));

            if (controller.getRoutingSessionInfo().isSystemSession()) {
                mRouter.getRoutes().stream()
                        .filter(route -> !route.isSystemRoute())
                        .forEach(route -> transferableRoutes.put(route.getId(), route));
            } else {
                mRouter.getRoutes().stream()
                        .filter(route -> route.isSystemRoute())
                        .forEach(route -> transferableRoutes.put(route.getId(), route));
            }
        }
        return new ArrayList<>(transferableRoutes.values());
    }

    @Nullable
    private RoutingController getControllerForSession(@NonNull RoutingSessionInfo sessionInfo) {
        return mRouter.getController(sessionInfo.getId());
    }

    @VisibleForTesting
    final class RouteCallback extends MediaRouter2.RouteCallback {
        @Override
        public void onRoutesUpdated(@NonNull List<MediaRoute2Info> routes) {
            if (DEBUG) {
                Log.d(TAG, "onRoutesUpdated()");
                for (MediaRoute2Info route : routes) {
                    Log.d(TAG, route.toString());
                }
            }
            refreshDevices();
        }

        @Override
        public void onPreferredFeaturesChanged(@NonNull List<String> preferredFeatures) {
            Log.i(TAG, "onPreferredFeaturesChanged(): [" + TextUtils.join(",", preferredFeatures)
                    + "]");
            refreshDevices();
        }
    }

    @VisibleForTesting
    final class TransferCallback extends MediaRouter2.TransferCallback {
        @Override
        public void onTransfer(
                @NonNull RoutingController oldController,
                @NonNull RoutingController newController) {
            Log.i(TAG, "onTransfer(), oldId: " + oldController.getId() + ", newId: "
                    + newController.getId());
            rebuildDeviceList();
            notifyCurrentConnectedDeviceChanged();
        }

        @Override
        public void onTransferFailure(@NonNull MediaRoute2Info requestedRoute) {
            Log.w(TAG, "onTransferFailure(), route: " + requestedRoute.getId());
            // Do nothing.
        }

        @Override
        public void onStop(@NonNull RoutingController controller) {
            Log.i(TAG, "onStop(), id: " + controller.getId());
            refreshDevices();
        }

        @Override
        public void onRequestFailed(int reason) {
            Log.w(TAG, "onRequestFailed(), reason: " + reason);
            dispatchOnRequestFailed(reason);
        }
    }

    @VisibleForTesting
    final class ControllerCallback extends MediaRouter2.ControllerCallback {
        @Override
        public void onControllerUpdated(@NonNull RoutingController controller) {
            if (DEBUG) {
                Log.d(TAG, "onControllerUpdated(), id: " + controller.getId());
            }
            refreshDevices();
        }
    }
}
