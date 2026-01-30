/*
 * Copyright 2019 The Android Open Source Project
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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.media.MediaRoute2Info;
import android.media.MediaRoute2ProviderInfo;
import android.media.MediaRoute2ProviderService.Reason;
import android.media.MediaRouter2;
import android.media.MediaRouter2Utils;
import android.media.RouteDiscoveryPreference;
import android.media.RoutingSessionInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;

import com.android.internal.annotations.GuardedBy;
import com.android.media.flags.Flags;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

abstract class MediaRoute2Provider {
    final ComponentName mComponentName;
    final String mUniqueId;
    final Object mLock = new Object();

    public final boolean mIsSystemRouteProvider;
    private volatile MediaRoute2ProviderInfo mProviderInfo;
    private Callback mCallback;

    @GuardedBy("mLock")
    final List<RoutingSessionInfo> mSessionInfos = new ArrayList<>();

    MediaRoute2Provider(@NonNull ComponentName componentName, boolean isSystemRouteProvider) {
        mComponentName = Objects.requireNonNull(componentName, "Component name must not be null.");
        mUniqueId = componentName.flattenToShortString();
        mIsSystemRouteProvider = isSystemRouteProvider;
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public abstract void requestCreateSession(
            long requestId,
            String packageName,
            String routeOriginalId,
            @Nullable Bundle sessionHints,
            @RoutingSessionInfo.TransferReason int transferReason,
            @NonNull UserHandle transferInitiatorUserHandle,
            @NonNull String transferInitiatorPackageName);

    public abstract void releaseSession(long requestId, String sessionId);

    public abstract void updateDiscoveryPreference(
            Set<String> activelyScanningPackages, RouteDiscoveryPreference discoveryPreference,
            Map<String, RouteDiscoveryPreference> perAppPreferences);

    public abstract void selectRoute(long requestId, String sessionId, String routeId);
    public abstract void deselectRoute(long requestId, String sessionId, String routeId);

    public abstract void transferToRoute(
            long requestId,
            @NonNull UserHandle transferInitiatorUserHandle,
            @NonNull String transferInitiatorPackageName,
            String sessionOriginalId,
            String routeOriginalId,
            @RoutingSessionInfo.TransferReason int transferReason);

    public abstract void setRouteVolume(long requestId, String routeOriginalId, int volume);

    public abstract void setSessionVolume(long requestId, String sessionOriginalId, int volume);

    public abstract void prepareReleaseSession(@NonNull String sessionUniqueId);

    @NonNull
    public String getUniqueId() {
        return mUniqueId;
    }

    @Nullable
    public MediaRoute2ProviderInfo getProviderInfo() {
        return mProviderInfo;
    }

    @NonNull
    public List<RoutingSessionInfo> getSessionInfos() {
        synchronized (mLock) {
            return new ArrayList<>(mSessionInfos);
        }
    }

    void setProviderState(MediaRoute2ProviderInfo providerInfo) {
        if (providerInfo == null) {
            mProviderInfo = null;
            return;
        }

        List<MediaRoute2Info> possiblyUpdatedRoutes = null;
        if (Flags.enableRouteVisibilityControlCompatFixes()) {
            possiblyUpdatedRoutes =
                    getVisibilityUpdatedRoutesIfNeeded(providerInfo.getRoutes(), getSessionInfos());
        }

        if (possiblyUpdatedRoutes != null) {
            setProviderStateWithUpdatedRoutes(providerInfo, possiblyUpdatedRoutes);
        } else {
            mProviderInfo = new MediaRoute2ProviderInfo.Builder(providerInfo)
                    .setUniqueId(mComponentName.getPackageName(), mUniqueId)
                    .setSystemRouteProvider(mIsSystemRouteProvider)
                    .build();
        }
    }

    private void setProviderStateWithUpdatedRoutes(@NonNull MediaRoute2ProviderInfo providerInfo,
            @NonNull List<MediaRoute2Info> updatedRoutes) {
        mProviderInfo = new MediaRoute2ProviderInfo.Builder(providerInfo, new ArrayMap<>())
                .addRoutes(updatedRoutes)
                .setUniqueId(mComponentName.getPackageName(), mUniqueId)
                .setSystemRouteProvider(mIsSystemRouteProvider)
                .build();
    }

    protected boolean haveCallback() {
        return mCallback != null;
    }

    protected void notifyProviderStateChanged() {
        if (mCallback != null) {
            mCallback.onProviderStateChanged(this);
        }
    }

    protected void notifySessionCreated(long requestId, @Nullable RoutingSessionInfo sessionInfo) {
        if (mCallback != null) {
            maybeUpdateProviderStateForRouteVisibility();
            mCallback.onSessionCreated(this, requestId, sessionInfo);
        }
    }

    protected void notifySessionUpdated(
            @NonNull MediaRoute2Provider provider,
            @NonNull RoutingSessionInfo sessionInfo,
            Set<String> packageNamesWithRoutingSessionOverrides,
            boolean shouldShowVolumeSystemUi) {
        if (mCallback != null) {
            maybeUpdateProviderStateForRouteVisibility();
            mCallback.onSessionUpdated(this, sessionInfo,
                    packageNamesWithRoutingSessionOverrides, shouldShowVolumeSystemUi);
        }
    }

    protected void notifySessionReleased(@NonNull RoutingSessionInfo sessionInfo) {
        if (mCallback != null) {
            mCallback.onSessionReleased(this, sessionInfo);
            maybeUpdateProviderStateForRouteVisibility();
        }
    }

    /** Calls {@link Callback#onRequestFailed} with the given id and reason. */
    protected void notifyRequestFailed(long requestId, @Reason int reason) {
        if (mCallback != null) {
            mCallback.onRequestFailed(/* provider= */ this, requestId, reason);
        }
    }

    void setAndNotifyProviderState(MediaRoute2ProviderInfo providerInfo) {
        setProviderState(providerInfo);
        notifyProviderStateChanged();
    }

    public boolean hasComponentName(String packageName, String className) {
        return mComponentName.getPackageName().equals(packageName)
                && mComponentName.getClassName().equals(className);
    }

    public void dump(PrintWriter pw, String prefix) {
        pw.println(prefix + getDebugString());
        prefix += "  ";

        if (mProviderInfo == null) {
            pw.println(prefix + "<provider info not received, yet>");
        } else if (mProviderInfo.getRoutes().isEmpty()) {
            pw.println(prefix + "<provider info has no routes>");
        } else {
            for (MediaRoute2Info route : mProviderInfo.getRoutes()) {
                pw.printf("%s%s | %s\n", prefix, route.getId(), route.getName());
            }
        }

        pw.println(prefix + "Active routing sessions:");
        synchronized (mLock) {
            if (mSessionInfos.isEmpty()) {
                pw.println(prefix + "  <no active routing sessions>");
            } else {
                for (RoutingSessionInfo routingSessionInfo : mSessionInfos) {
                    routingSessionInfo.dump(pw, prefix + "  ");
                }
            }
        }
    }

    @Override
    public String toString() {
        return getDebugString();
    }

    /** Returns a human-readable string describing the instance, for debugging purposes. */
    protected abstract String getDebugString();

    public interface Callback {
        void onProviderStateChanged(@Nullable MediaRoute2Provider provider);
        void onSessionCreated(@NonNull MediaRoute2Provider provider,
                long requestId, @Nullable RoutingSessionInfo sessionInfo);

        /**
         * Called when there's a session info change.
         *
         * <p>If the provided {@code sessionInfo} has a null {@link
         * RoutingSessionInfo#getClientPackageName()}, that means that it's applicable to all
         * packages. We call this type of routing session "global". This is typically used for
         * system provided {@link RoutingSessionInfo}. However, some applications may be exempted
         * from the global routing sessions, because their media is being routed using a session
         * different from the global routing session.
         *
         * @param provider The provider that owns the session that changed.
         * @param sessionInfo The new {@link RoutingSessionInfo}.
         * @param packageNamesWithRoutingSessionOverrides The names of packages that are not
         *     affected by global session changes. This set may only be non-empty when the {@code
         *     sessionInfo} is for the global session, and therefore has no {@link
         *     RoutingSessionInfo#getClientPackageName()}.
         * @param shouldShowVolumeSystemUi Whether a volume UI affordance should be presented as a
         *     result of this session update. For example, this session update may be the result of
         *     a volume change in response to a volume hardware key press, in which case a volume
         *     slider should be presented.
         */
        void onSessionUpdated(
                @NonNull MediaRoute2Provider provider,
                @NonNull RoutingSessionInfo sessionInfo,
                Set<String> packageNamesWithRoutingSessionOverrides,
                boolean shouldShowVolumeSystemUi);

        void onSessionReleased(@NonNull MediaRoute2Provider provider,
                @NonNull RoutingSessionInfo sessionInfo);

        void onRequestFailed(
                @NonNull MediaRoute2Provider provider, long requestId, @Reason int reason);
    }

    /**
     * Holds session creation or transfer initiation information for a transfer in flight.
     *
     * <p>The initiator app is typically also the {@link RoutingSessionInfo#getClientPackageName()
     * client app}, with the exception of the {@link MediaRouter2#getSystemController() system
     * routing session} which is exceptional in that it's shared among all apps.
     *
     * <p>For the system routing session, the initiator app is the one that programmatically
     * triggered the transfer (for example, via {@link MediaRouter2#transferTo}), or the target app
     * of the proxy router that did the transfer.
     *
     * @see MediaRouter2.RoutingController#wasTransferInitiatedBySelf()
     * @see RoutingSessionInfo#getTransferInitiatorPackageName()
     * @see RoutingSessionInfo#getTransferInitiatorUserHandle()
     */
    protected static class SessionCreationOrTransferRequest {

        /**
         * The id of the request, or {@link
         * android.media.MediaRoute2ProviderService#REQUEST_ID_NONE} if unknown.
         */
        public final long mRequestId;

        /** The {@link MediaRoute2Info#getOriginalId()} original id} of the target route. */
        @NonNull public final String mTargetOriginalRouteId;

        @RoutingSessionInfo.TransferReason public final int mTransferReason;

        /** The {@link android.os.UserHandle} on which the initiator app is running. */
        @NonNull public final UserHandle mTransferInitiatorUserHandle;

        @NonNull public final String mTransferInitiatorPackageName;

        SessionCreationOrTransferRequest(
                long requestId,
                @NonNull String targetOriginalRouteId,
                @RoutingSessionInfo.TransferReason int transferReason,
                @NonNull UserHandle transferInitiatorUserHandle,
                @NonNull String transferInitiatorPackageName) {
            mRequestId = requestId;
            mTargetOriginalRouteId = targetOriginalRouteId;
            mTransferReason = transferReason;
            mTransferInitiatorUserHandle = transferInitiatorUserHandle;
            mTransferInitiatorPackageName = transferInitiatorPackageName;
        }

        public boolean isTargetRoute(@Nullable MediaRoute2Info route2Info) {
            return route2Info != null && mTargetOriginalRouteId.equals(route2Info.getOriginalId());
        }

        /**
         * Returns whether the given list of {@link MediaRoute2Info#getOriginalId() original ids}
         * contains the {@link #mTargetOriginalRouteId target route id}.
         */
        public boolean isTargetRouteIdInRouteOriginalIdList(
                @NonNull List<String> originalRouteIdList) {
            return originalRouteIdList.stream().anyMatch(mTargetOriginalRouteId::equals);
        }

        /**
         * Returns whether the given list of {@link MediaRoute2Info#getId() unique ids} contains the
         * {@link #mTargetOriginalRouteId target route id}.
         */
        public boolean isTargetRouteIdInRouteUniqueIdList(@NonNull List<String> uniqueRouteIdList) {
            return uniqueRouteIdList.stream()
                    .map(MediaRouter2Utils::getOriginalId)
                    .anyMatch(mTargetOriginalRouteId::equals);
        }
    }

    private void maybeUpdateProviderStateForRouteVisibility() {
        if (!Flags.enableRouteVisibilityControlCompatFixes()) {
            return;
        }
        if (mProviderInfo == null) {
            return;  // no need to update provider state if we don't have any
        }
        List<MediaRoute2Info> possiblyUpdatedRoutes =
                getVisibilityUpdatedRoutesIfNeeded(mProviderInfo.getRoutes(), mSessionInfos);
        if (possiblyUpdatedRoutes != null) {
            setProviderStateWithUpdatedRoutes(mProviderInfo, possiblyUpdatedRoutes);
            notifyProviderStateChanged();
        }
    }

    /**
     * Returns a copy of routes with any missing visibility added, or null if the existing
     * visibility is sufficient.
     *
     * <p>We consider visibility to be missing when a route is not visible to a given app, but a
     * routing session exists where that app is the {@link #getClientPackageName client} and that
     * route is selected.
     *
     * <p>In summary, this method ensures that all routes which are selected by an app are visible
     * to that app.
     */
    @Nullable
    private List<MediaRoute2Info> getVisibilityUpdatedRoutesIfNeeded(
            Collection<MediaRoute2Info> routes, List<RoutingSessionInfo> sessions) {
        ArrayMap<String, Set<String>> selectedRouteToClient = new ArrayMap<>();
        for (RoutingSessionInfo session : sessions) {
            session.getSelectedRoutes().forEach(routeId -> {
                Set<String> clients =
                        selectedRouteToClient.computeIfAbsent(routeId, k -> new ArraySet<>());
                clients.add(session.getClientPackageName());
            });
        }

        boolean updatedSomeRoute = false;
        ArrayList<MediaRoute2Info> updatedRoutes = new ArrayList<>();
        for (MediaRoute2Info route : routes) {
            String fullId = MediaRouter2Utils.toUniqueId(mUniqueId, route.getOriginalId());
            MediaRoute2Info routeToAdd = route;
            if (!route.isPublic()) {
                Set<String> clients = selectedRouteToClient.getOrDefault(fullId, Set.of());
                if (!clients.equals(route.getTemporaryVisibilityPackages())) {
                    routeToAdd = new MediaRoute2Info.Builder(route)
                            .setTemporaryAllowedPackages(clients)
                            .build();
                    updatedSomeRoute = true;
                }
            }
            updatedRoutes.add(routeToAdd);
        }

        return updatedSomeRoute ? updatedRoutes : null;
    }
}
