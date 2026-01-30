/*
 * Copyright 2025 The Android Open Source Project
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

import static android.media.RouteListingPreference.Item.FLAG_SUGGESTED;
import static android.media.RoutingChangeInfo.ENTRY_POINT_LOCAL_ROUTER_UNSPECIFIED;
import static android.media.RoutingChangeInfo.ENTRY_POINT_PROXY_ROUTER_UNSPECIFIED;
import static android.media.RoutingChangeInfo.ENTRY_POINT_SYSTEM_MEDIA_CONTROLS;
import static android.media.RoutingChangeInfo.ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER;
import static android.media.RoutingChangeInfo.ENTRY_POINT_TV_OUTPUT_SWITCHER;
import static android.media.RoutingSessionInfo.TRANSFER_REASON_APP;
import static android.media.RoutingSessionInfo.TRANSFER_REASON_FALLBACK;
import static android.media.RoutingSessionInfo.TRANSFER_REASON_SYSTEM_REQUEST;

import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_CREATE_SESSION;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_CREATE_SYSTEM_ROUTING_SESSION;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_DESELECT_ROUTE;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_RELEASE_SESSION;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_SCANNING_STARTED;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_SCANNING_STOPPED;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_SELECT_ROUTE;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_TRANSFER_TO_ROUTE;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_UNSPECIFIED;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_FAILED_TO_REROUTE_SYSTEM_MEDIA;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_INVALID_COMMAND;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_NETWORK_ERROR;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_REJECTED;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_ROUTE_NOT_AVAILABLE;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNIMPLEMENTED;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNKNOWN_ERROR;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNSPECIFIED;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_LOCAL_ROUTER;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_PROXY_ROUTER;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_SYSTEM_MEDIA_CONTROLS;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_TV_OUTPUT_SWITCHER;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_UNSPECIFIED;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_APP;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_FALLBACK;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_SYSTEM_REQUEST;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_UNSPECIFIED;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.media.MediaRoute2ProviderService;
import android.media.MediaRouter2;
import android.media.RouteListingPreference;
import android.media.RoutingChangeInfo;
import android.media.RoutingChangeInfo.EntryPoint;
import android.media.RoutingSessionInfo;
import android.media.RoutingSessionInfo.TransferReason;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;
import android.util.Slog;

import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Map;

/**
 * Logs metrics for MediaRouter2.
 *
 * @hide
 */
final class MediaRouterMetricLogger {
    public static final int EVENT_TYPE_UNSPECIFIED = 0;
    public static final int EVENT_TYPE_CREATE_SESSION = 1;
    public static final int EVENT_TYPE_CREATE_SYSTEM_ROUTING_SESSION = 2;
    public static final int EVENT_TYPE_RELEASE_SESSION = 3;
    public static final int EVENT_TYPE_SELECT_ROUTE = 4;
    public static final int EVENT_TYPE_DESELECT_ROUTE = 5;
    public static final int EVENT_TYPE_TRANSFER_TO_ROUTE = 6;
    public static final int EVENT_TYPE_SCANNING_STARTED = 7;
    public static final int EVENT_TYPE_SCANNING_STOPPED = 8;

    @IntDef(
            prefix = "EVENT_TYPE",
            value = {
                    EVENT_TYPE_UNSPECIFIED,
                    EVENT_TYPE_CREATE_SESSION,
                    EVENT_TYPE_CREATE_SYSTEM_ROUTING_SESSION,
                    EVENT_TYPE_RELEASE_SESSION,
                    EVENT_TYPE_SELECT_ROUTE,
                    EVENT_TYPE_DESELECT_ROUTE,
                    EVENT_TYPE_TRANSFER_TO_ROUTE,
                    EVENT_TYPE_SCANNING_STARTED,
                    EVENT_TYPE_SCANNING_STOPPED
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface EventType {}

    private static final String TAG = "MediaRouterMetricLogger";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final int REQUEST_INFO_CACHE_CAPACITY = 100;
    private static final int API_COUNT_CACHE_CAPACITY = 20;

    /** Corresponds to {@link MediaRouter2#setDeviceSuggestions calls } */
    private static final int SUGGESTION_INTERACTION_TYPE_UPDATE = 0;

    /** Corresponds to {@link MediaRouter2#notifyDeviceSuggestionRequested calls } */
    private static final int SUGGESTION_INTERACTION_TYPE_REQUEST = 1;

    @IntDef(
            prefix = "SUGGESTION_INTERACTION_TYPE",
            value = {SUGGESTION_INTERACTION_TYPE_UPDATE, SUGGESTION_INTERACTION_TYPE_REQUEST})
    @Retention(RetentionPolicy.SOURCE)
    private @interface SuggestionInteractionType {}

    /** LRU cache to store request info. */
    private final EvictionCallbackLruCache<Long, RequestInfo> mRequestInfoCache;

    /** LRU cache to store information about the invocation of a routing change. */
    private final EvictionCallbackLruCache<Long, RoutingChangeInfo> mRoutingChangeInfoCache;

    /** LRU cache to store information for an ongoing routing change. */
    private final EvictionCallbackLruCache<String, OngoingRoutingChange> mOngoingRoutingChangeCache;

    /**
     * LRU cache to store the count of RLP API calls per package uid. The key is the package uid and
     * the value is {@link RlpCount}
     */
    private final EvictionCallbackLruCache<Integer, RlpCount> mRlpCountCache;

    /**
     * LRU cache to store counts of device suggestion calls for a target and suggesting package
     * pair.
     */
    private final EvictionCallbackLruCache<PackagePair, DeviceSuggestionsCount>
            mDeviceSuggestionsCountCache;

    /** Constructor for {@link MediaRouterMetricLogger}. */
    public MediaRouterMetricLogger() {
        mRequestInfoCache =
                new EvictionCallbackLruCache<>(
                        REQUEST_INFO_CACHE_CAPACITY, new OnRequestInfoEvictedListener());
        mRoutingChangeInfoCache =
                new EvictionCallbackLruCache<>(
                        REQUEST_INFO_CACHE_CAPACITY, new OnRoutingChangeInfoEvictedListener());
        mOngoingRoutingChangeCache =
                new EvictionCallbackLruCache<>(
                        REQUEST_INFO_CACHE_CAPACITY, new OnOngoingRoutingChangeEvictedListener());
        mRlpCountCache =
                new EvictionCallbackLruCache<>(
                        API_COUNT_CACHE_CAPACITY, new OnRlpSuggestionCountEvictedListener());
        mDeviceSuggestionsCountCache =
                new EvictionCallbackLruCache<>(
                        API_COUNT_CACHE_CAPACITY, new OnDeviceSuggestionsCountEvictedListener());
    }

    /**
     * Converts a reason code from {@link MediaRoute2ProviderService} to a result code for logging.
     *
     * @param reason The reason code from {@link MediaRoute2ProviderService}.
     * @return The result code for logging.
     */
    public static int convertResultFromReason(int reason) {
        switch (reason) {
            case MediaRoute2ProviderService.REASON_UNKNOWN_ERROR:
                return MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNKNOWN_ERROR;
            case MediaRoute2ProviderService.REASON_REJECTED:
                return MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_REJECTED;
            case MediaRoute2ProviderService.REASON_NETWORK_ERROR:
                return MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_NETWORK_ERROR;
            case MediaRoute2ProviderService.REASON_ROUTE_NOT_AVAILABLE:
                return MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_ROUTE_NOT_AVAILABLE;
            case MediaRoute2ProviderService.REASON_INVALID_COMMAND:
                return MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_INVALID_COMMAND;
            case MediaRoute2ProviderService.REASON_UNIMPLEMENTED:
                return MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNIMPLEMENTED;
            case MediaRoute2ProviderService.REASON_FAILED_TO_REROUTE_SYSTEM_MEDIA:
                return MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_FAILED_TO_REROUTE_SYSTEM_MEDIA;
            default:
                return MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNSPECIFIED;
        }
    }

    /**
     * Adds a new request info to the cache.
     *
     * @param uniqueRequestId The unique request id.
     * @param eventType The routing event type.
     * @param routingChangeInfo The routing change request information.
     */
    public void addRequestInfo(
            long uniqueRequestId, @EventType int eventType, RoutingChangeInfo routingChangeInfo) {
        RequestInfo requestInfo = new RequestInfo(uniqueRequestId, eventType, routingChangeInfo);
        mRequestInfoCache.put(requestInfo.mUniqueRequestId, requestInfo);
    }

    /**
     * Removes a request info from the cache.
     *
     * @param uniqueRequestId The unique request id.
     */
    @VisibleForTesting
    /* package */ void removeRequestInfo(long uniqueRequestId) {
        mRequestInfoCache.remove(uniqueRequestId);
    }

    /**
     * Logs an operation failure.
     *
     * @param eventType The routing event type.
     * @param result The result of the operation.
     * @param routingChangeInfo The routing change request information.
     */
    public void logOperationFailure(
            @EventType int eventType, int result, RoutingChangeInfo routingChangeInfo) {
        logMediaRouterEvent(eventType, result, routingChangeInfo);
    }

    /**
     * Logs an operation triggered.
     *
     * @param eventType The routing event type.
     * @param result The result of the operation.
     * @param routingChangeInfo The routing change request information.
     */
    public void logOperationTriggered(
            @EventType int eventType, int result, RoutingChangeInfo routingChangeInfo) {
        logMediaRouterEvent(eventType, result, routingChangeInfo);
    }

    /**
     * Logs the result of a request.
     *
     * @param uniqueRequestId The unique request id.
     * @param result The result of the request.
     */
    public void logRequestResult(long uniqueRequestId, int result) {
        RequestInfo requestInfo = mRequestInfoCache.get(uniqueRequestId);
        if (requestInfo == null) {
            Slog.w(
                    TAG,
                    "logRequestResult: No RequestInfo found for uniqueRequestId="
                            + uniqueRequestId);
            return;
        }

        @EventType int eventType = requestInfo.mEventType;
        logMediaRouterEvent(eventType, result, requestInfo.mRoutingChangeInfo);

        removeRequestInfo(uniqueRequestId);
    }

    /**
     * Logs the overall scanning state.
     *
     * @param isScanning The scanning state for the user.
     */
    public void updateScanningState(boolean isScanning) {
        if (!isScanning) {
            logScanningStopped();
        } else {
            logScanningStarted();
        }
    }

    /**
     * Stores {@link RoutingChangeInfo} when a routing change is requested. This is appended with
     * routing session information when {@link MediaRouterMetricLogger#notifyRoutingChange(long,
     * RoutingSessionInfo, int)} is called.
     *
     * @param uniqueRequestId the unique request id for a routing change.
     * @param routingChangeInfo the routing change request information.
     */
    public void notifyRoutingChangeRequested(
            long uniqueRequestId, RoutingChangeInfo routingChangeInfo) {
        mRoutingChangeInfoCache.put(uniqueRequestId, routingChangeInfo);
    }

    /**
     * Stores an {@link OngoingRoutingChange} when a routing change occurs. This is logged when
     * {@link MediaRouterMetricLogger#notifySessionEnd(String)} is called with the corresponding
     * sessionId.
     *
     * @param uniqueRequestId the unique request id corresponding to the routing change request.
     *     This should be same as the one passed into {@link
     *     MediaRouterMetricLogger#notifyRoutingChangeRequested(long, RoutingChangeInfo)}
     * @param routingSessionInfo information of the media routing session.
     * @param clientPackageUid uid of the client package for which the routing is taking place.
     */
    public void notifyRoutingChange(
            long uniqueRequestId, RoutingSessionInfo routingSessionInfo, int clientPackageUid) {
        RoutingChangeInfo routingChangeInfo = mRoutingChangeInfoCache.get(uniqueRequestId);
        if (routingChangeInfo == null) {
            Slog.e(TAG, "Unable to get routing change info for the specified request id.");
            return;
        }
        OngoingRoutingChange ongoingRoutingChange =
                new OngoingRoutingChange(
                        routingChangeInfo.getEntryPoint(),
                        clientPackageUid,
                        routingSessionInfo.isSystemSession(),
                        routingSessionInfo.getTransferReason(),
                        routingChangeInfo.isSuggested(),
                        routingChangeInfo.isSuggestedByRlp(),
                        routingChangeInfo.isSuggestedByMediaApp(),
                        routingChangeInfo.isSuggestedByAnotherApp(),
                        getElapsedRealTime());
        mOngoingRoutingChangeCache.put(routingSessionInfo.getOriginalId(), ongoingRoutingChange);
        mRoutingChangeInfoCache.remove(uniqueRequestId);
    }

    /**
     * Logs the {@link OngoingRoutingChange} corresponding to the sessionId
     *
     * @param sessionId id of the session which has ended
     */
    public void notifySessionEnd(String sessionId) {
        OngoingRoutingChange ongoingRoutingChange = mOngoingRoutingChangeCache.get(sessionId);
        if (ongoingRoutingChange == null) {
            Slog.e(TAG, "Unable to get routing change logging info for the specified sessionId.");
            return;
        }
        long sessionLengthInMillis = getElapsedRealTime() - ongoingRoutingChange.startTimeInMillis;

        if (DEBUG) {
            Slog.d(
                    TAG,
                    TextUtils.formatSimple(
                            "notifySessionEnd | EntryPoint: %d, ClientPackageUid: %d,"
                                    + " IsSystemSession: %b, TransferReason: %d, IsSuggested: %b,"
                                    + " SuggestionProviders: %s, SessionLengthInMillis: %d",
                            ongoingRoutingChange.entryPoint,
                            ongoingRoutingChange.clientPackageUid,
                            ongoingRoutingChange.isSystemSession,
                            ongoingRoutingChange.transferReason,
                            ongoingRoutingChange.isSuggested,
                            ongoingRoutingChange.getSuggestionProvidersDebugString(),
                            sessionLengthInMillis));
        }

        MediaRouterStatsLog.write(
                MediaRouterStatsLog.ROUTING_CHANGE_REPORTED,
                convertEntryPointForLogging(ongoingRoutingChange.entryPoint),
                ongoingRoutingChange.clientPackageUid,
                ongoingRoutingChange.isSystemSession,
                convertTransferReasonForLogging(ongoingRoutingChange.transferReason),
                ongoingRoutingChange.isSuggested,
                sessionLengthInMillis,
                ongoingRoutingChange.isSuggestedByRlp,
                ongoingRoutingChange.isSuggestedByMediaApp,
                ongoingRoutingChange.isSuggestedByAnotherApp);

        mOngoingRoutingChangeCache.remove(sessionId);
    }

    /**
     * Increments the count of RLP calls and if any of the {@link RouteListingPreference.Item} have
     * {@link FLAG_SUGGESTED} set, increments the count of RLP suggestion calls.
     *
     * @param setterPackageUid Uid of the package which called the API.
     * @param routeListingPreference the route listing preference with which the API was called.
     */
    public void notifyRouteListingPreferenceChanged(
            int setterPackageUid, @Nullable RouteListingPreference routeListingPreference) {
        boolean hasFlagSuggested =
                routeListingPreference != null
                        && (routeListingPreference.getItems().stream()
                                .anyMatch(item -> (item.getFlags() & FLAG_SUGGESTED) != 0));

        long rlpTotalCount = 0, rlpWithSuggestedCount = 0;
        long suggestedCountIncrement = hasFlagSuggested ? 1 : 0;
        RlpCount existingRlpCount = mRlpCountCache.get(setterPackageUid);
        if (existingRlpCount != null) {
            rlpTotalCount = existingRlpCount.rlpTotalCount;
            rlpWithSuggestedCount = existingRlpCount.rlpWithSuggestionCount;
        }
        mRlpCountCache.put(
                setterPackageUid,
                new RlpCount(rlpTotalCount + 1, rlpWithSuggestedCount + suggestedCountIncrement));
    }

    /**
     * Increments the count of device suggestion update calls for the target and suggesting package
     * pair.
     *
     * @param targetPackageUid Uid of the package for which devices are suggested.
     * @param suggestingPackageUid Uid of the package suggesting the devices.
     */
    public void notifyDeviceSuggestionsUpdated(int targetPackageUid, int suggestingPackageUid) {
        notifyDeviceSuggestionInteracted(
                targetPackageUid, suggestingPackageUid, SUGGESTION_INTERACTION_TYPE_UPDATE);
    }

    /**
     * Increments the count of device suggestion request calls for the target and suggesting package
     * pair.
     *
     * @param targetPackageUid Uid of the package for which devices are suggested.
     * @param suggestingPackageUid Uid of the package suggesting the devices.
     */
    public void notifyDeviceSuggestionsRequested(int targetPackageUid, int suggestingPackageUid) {
        notifyDeviceSuggestionInteracted(
                targetPackageUid, suggestingPackageUid, SUGGESTION_INTERACTION_TYPE_REQUEST);
    }

    private void notifyDeviceSuggestionInteracted(
            int targetPackageUid,
            int suggestingPackageUid,
            @SuggestionInteractionType int suggestionInteractionType) {
        PackagePair packagePair = new PackagePair(targetPackageUid, suggestingPackageUid);
        DeviceSuggestionsCount deviceSuggestionsCount =
                mDeviceSuggestionsCountCache.get(packagePair);
        long updateSuggestionsCount = 0;
        long requestSuggestionsCount = 0;
        if (deviceSuggestionsCount != null) {
            updateSuggestionsCount = deviceSuggestionsCount.updateSuggestionsCount;
            requestSuggestionsCount = deviceSuggestionsCount.requestSuggestionsCount;
        }

        switch (suggestionInteractionType) {
            case SUGGESTION_INTERACTION_TYPE_UPDATE:
                updateSuggestionsCount++;
                break;
            case SUGGESTION_INTERACTION_TYPE_REQUEST:
                requestSuggestionsCount++;
                break;
        }
        mDeviceSuggestionsCountCache.put(
                packagePair,
                new DeviceSuggestionsCount(updateSuggestionsCount, requestSuggestionsCount));
    }

    /**
     * This is called when a {@link android.media.MediaRouter2} instance is unregistered. It takes
     * care of logging metrics aggregated over the lifecycle of the {@link
     * android.media.MediaRouter2} instance.
     *
     * @param routerPackageUid the Uid of the package associated with the {@link
     *     android.media.MediaRouter2} instance.
     */
    public void notifyRouterUnregistered(int routerPackageUid) {
        logRlpCountForRouter(routerPackageUid);
        logDeviceSuggestionsCountForRouter(routerPackageUid);
    }

    /**
     * Converts {@link TransferReason} from {@link RoutingSessionInfo} to the transfer reason enum
     * defined for logging.
     *
     * @param transferReason the transfer reason as specified in {@link RoutingSessionInfo}
     * @return the transfer reason as per the enum defined for logging.
     */
    @VisibleForTesting
    /*package*/ static int convertTransferReasonForLogging(@TransferReason int transferReason) {
        return switch (transferReason) {
            case TRANSFER_REASON_FALLBACK ->
                    ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_FALLBACK;
            case TRANSFER_REASON_SYSTEM_REQUEST ->
                    ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_SYSTEM_REQUEST;
            case TRANSFER_REASON_APP ->
                    ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_APP;
            default -> ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_UNSPECIFIED;
        };
    }

    /**
     * Converts {@link EntryPoint} from {@link RoutingChangeInfo} to the entry point enum defined
     * for logging.
     *
     * @param entryPoint the entry point as specified in {@link RoutingChangeInfo}
     * @return the entry point as per the enum defined for logging.
     */
    @VisibleForTesting
    /* package */ static int convertEntryPointForLogging(@EntryPoint int entryPoint) {
        return switch (entryPoint) {
            case ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER ->
                    ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER;
            case ENTRY_POINT_SYSTEM_MEDIA_CONTROLS ->
                    ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_SYSTEM_MEDIA_CONTROLS;
            case ENTRY_POINT_LOCAL_ROUTER_UNSPECIFIED ->
                    ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_LOCAL_ROUTER;
            case ENTRY_POINT_PROXY_ROUTER_UNSPECIFIED ->
                    ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_PROXY_ROUTER;
            case ENTRY_POINT_TV_OUTPUT_SWITCHER ->
                    ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_TV_OUTPUT_SWITCHER;
            default ->
                    throw new IllegalArgumentException(
                            "No mapping found for the given entry point: " + entryPoint);
        };
    }

    /**
     * Converts {@link EventType} to the enum defined for logging.
     *
     * @param eventType the routing event type.
     * @return the event type as per the enum defined for logging.
     */
    @VisibleForTesting
    /* package */ static int convertEventTypeForLogging(@EventType int eventType) {
        return switch (eventType) {
            case EVENT_TYPE_UNSPECIFIED ->
                    MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_UNSPECIFIED;
            case EVENT_TYPE_CREATE_SESSION ->
                    MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_CREATE_SESSION;
            case EVENT_TYPE_CREATE_SYSTEM_ROUTING_SESSION ->
                    MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_CREATE_SYSTEM_ROUTING_SESSION;
            case EVENT_TYPE_RELEASE_SESSION ->
                    MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_RELEASE_SESSION;
            case EVENT_TYPE_SELECT_ROUTE ->
                    MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_SELECT_ROUTE;
            case EVENT_TYPE_DESELECT_ROUTE ->
                    MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_DESELECT_ROUTE;
            case EVENT_TYPE_TRANSFER_TO_ROUTE ->
                    MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_TRANSFER_TO_ROUTE;
            case EVENT_TYPE_SCANNING_STARTED ->
                    MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_SCANNING_STARTED;
            case EVENT_TYPE_SCANNING_STOPPED ->
                    MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_SCANNING_STOPPED;
            default ->
                    throw new IllegalArgumentException(
                            "No mapping found for the given event type: " + eventType);
        };
    }

    @VisibleForTesting
    /* package */ int getRequestInfoCacheCapacity() {
        return mRequestInfoCache.maxSize();
    }

    /**
     * Gets the size of the request info cache.
     *
     * @return The size of the request info cache.
     */
    @VisibleForTesting
    /* package */ int getRequestCacheSize() {
        return mRequestInfoCache.size();
    }

    @VisibleForTesting
    /* package */ int getRoutingChangeInfoCacheSize() {
        return mRoutingChangeInfoCache.size();
    }

    @VisibleForTesting
    /* package */ int getOngoingRoutingChangeCacheSize() {
        return mOngoingRoutingChangeCache.size();
    }

    @VisibleForTesting
    /* package */ long getElapsedRealTime() {
        return SystemClock.elapsedRealtime();
    }

    private void logMediaRouterEvent(
            @EventType int eventType, int result, RoutingChangeInfo routingChangeInfo) {
        int entryPoint =
                routingChangeInfo != null
                        ? convertEntryPointForLogging(routingChangeInfo.getEntryPoint())
                        : ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_UNSPECIFIED;
        boolean isSuggested = routingChangeInfo != null && routingChangeInfo.isSuggested();
        MediaRouterStatsLog.write(
                MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED,
                convertEventTypeForLogging(eventType),
                result,
                entryPoint,
                isSuggested);

        if (DEBUG) {
            Slog.d(TAG, "logMediaRouterEvent: " + eventType + " " + result);
        }
    }

    /** Logs the scanning started event. */
    private void logScanningStarted() {
        logMediaRouterEvent(
                EVENT_TYPE_SCANNING_STARTED,
                MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNSPECIFIED,
                /* routingChangeInfo= */ null);
    }

    /** Logs the scanning stopped event. */
    private void logScanningStopped() {
        logMediaRouterEvent(
                EVENT_TYPE_SCANNING_STOPPED,
                MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNSPECIFIED,
                /* routingChangeInfo= */ null);
    }

    private void logRlpCountForRouter(int routerPackageUid) {
        RlpCount rlpCount = mRlpCountCache.remove(routerPackageUid);
        if (rlpCount == null) {
            // This helps track scenarios where MediaRouter2 is used but not RLP.
            rlpCount = new RlpCount(0, 0);
        }
        logRlpCountForRouter(routerPackageUid, rlpCount);
    }

    private void logRlpCountForRouter(int routerPackageId, RlpCount rlpCount) {
        MediaRouterStatsLog.write(
                MediaRouterStatsLog.ROUTE_LISTING_PREFERENCE_UPDATED,
                routerPackageId,
                rlpCount.rlpTotalCount,
                rlpCount.rlpWithSuggestionCount);
    }

    private void logDeviceSuggestionsCountForRouter(int routerPackageUid) {
        for (Map.Entry<PackagePair, DeviceSuggestionsCount> entry :
                mDeviceSuggestionsCountCache.snapshot().entrySet()) {
            if (entry.getKey().targetPackageUid == routerPackageUid) {
                logDeviceSuggestionsAccessed(entry.getKey(), entry.getValue());
            }
        }
    }

    private void logDeviceSuggestionsAccessed(
            PackagePair packagePair, DeviceSuggestionsCount deviceSuggestionsCount) {
        MediaRouterStatsLog.write(
                MediaRouterStatsLog.DEVICE_SUGGESTIONS_INTERACTION_REPORTED,
                packagePair.targetPackageUid,
                packagePair.operatingPackageUid,
                deviceSuggestionsCount.updateSuggestionsCount,
                deviceSuggestionsCount.requestSuggestionsCount);
    }

    /** Class to store request info. */
    static class RequestInfo {
        public final long mUniqueRequestId;
        public final @EventType int mEventType;
        public final RoutingChangeInfo mRoutingChangeInfo;

        /**
         * Constructor for {@link RequestInfo}.
         *
         * @param uniqueRequestId The unique request id.
         * @param eventType The routing event type.
         * @param routingChangeInfo The routing change request information.
         */
        RequestInfo(
                long uniqueRequestId,
                @EventType int eventType,
                RoutingChangeInfo routingChangeInfo) {
            mUniqueRequestId = uniqueRequestId;
            mEventType = eventType;
            mRoutingChangeInfo = routingChangeInfo;
        }

        /**
         * Dumps the request info.
         *
         * @param pw The print writer.
         * @param prefix The prefix for the output.
         */
        public void dump(@NonNull PrintWriter pw, @NonNull String prefix) {
            pw.println(prefix + "RequestInfo");
            String indent = prefix + "  ";
            pw.println(indent + "mUniqueRequestId=" + mUniqueRequestId);
            pw.println(indent + "mEventType=" + mEventType);
        }
    }

    /**
     * A subclass of {@link LruCache} which takes in an {@link OnEntryEvictedListener} to be invoked
     * on eviction of an entry.
     */
    private static class EvictionCallbackLruCache<K, V> extends LruCache<K, V> {

        private final OnEntryEvictedListener<K, V> mOnEntryEvictedListener;

        EvictionCallbackLruCache(int maxSize, OnEntryEvictedListener<K, V> onEntryEvictedListener) {
            super(maxSize);
            mOnEntryEvictedListener = onEntryEvictedListener;
        }

        @Override
        protected void entryRemoved(boolean evicted, K key, V oldValue, V newValue) {
            if (evicted) {
                mOnEntryEvictedListener.onEntryEvicted(key, oldValue);
            }
        }
    }

    private class OnRequestInfoEvictedListener
            implements OnEntryEvictedListener<Long, RequestInfo> {

        @Override
        public void onEntryEvicted(Long key, RequestInfo value) {
            Slog.d(TAG, "Evicted request info: " + value.mUniqueRequestId);
            logOperationTriggered(
                    value.mEventType,
                    MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNSPECIFIED,
                    value.mRoutingChangeInfo);
        }
    }

    private static class OnRoutingChangeInfoEvictedListener
            implements OnEntryEvictedListener<Long, RoutingChangeInfo> {

        @Override
        public void onEntryEvicted(Long key, RoutingChangeInfo value) {
            Slog.w(TAG, "Routing change info evicted from cache with requestId: " + key);
        }
    }

    private static class OnOngoingRoutingChangeEvictedListener
            implements OnEntryEvictedListener<String, OngoingRoutingChange> {
        @Override
        public void onEntryEvicted(String key, OngoingRoutingChange ongoingRoutingChange) {
            Slog.w(TAG, "Routing change info evicted from cache with sessionId: " + key);
        }
    }

    private class OnRlpSuggestionCountEvictedListener
            implements OnEntryEvictedListener<Integer, RlpCount> {
        @Override
        public void onEntryEvicted(Integer key, RlpCount value) {
            Slog.w(TAG, "Rlp suggestion count evicted from cache");
            logRlpCountForRouter(key, value);
        }
    }

    private class OnDeviceSuggestionsCountEvictedListener
            implements OnEntryEvictedListener<PackagePair, DeviceSuggestionsCount> {
        @Override
        public void onEntryEvicted(PackagePair key, DeviceSuggestionsCount value) {
            Slog.w(TAG, "Device suggestions count evicted from cache");
            logDeviceSuggestionsAccessed(key, value);
        }
    }

    private interface OnEntryEvictedListener<K, V> {
        void onEntryEvicted(K key, V value);
    }

    /** Information about an ongoing routing change. */
    private record OngoingRoutingChange(
            @EntryPoint int entryPoint,
            int clientPackageUid,
            boolean isSystemSession,
            @TransferReason int transferReason,
            boolean isSuggested,
            boolean isSuggestedByRlp,
            boolean isSuggestedByMediaApp,
            boolean isSuggestedByAnotherApp,
            long startTimeInMillis) {

        /**
         * Returns a human-readable representation of the suggestion provider for logging purposes.
         */
        public String getSuggestionProvidersDebugString() {
            var providerStrings = new ArrayList<String>();
            if (isSuggestedByRlp) {
                providerStrings.add("RLP");
            }
            if (isSuggestedByMediaApp) {
                providerStrings.add("MEDIA_APP");
            }
            if (isSuggestedByAnotherApp) {
                providerStrings.add("ANOTHER_APP");
            }
            if (providerStrings.isEmpty()) {
                return "NONE";
            } else {
                return String.join("|", providerStrings);
            }
        }
    }

    /** Tracks the count of changes in route listing preference */
    private record RlpCount(long rlpTotalCount, long rlpWithSuggestionCount) {}

    /** Tracks the count of device suggestion interaction. */
    private record DeviceSuggestionsCount(
            // Count of the number of calls made to update device suggestions.
            long updateSuggestionsCount,
            // Count of the number of calls made to request device suggestions.
            long requestSuggestionsCount) {}

    /**
     * Holds a pair of uids of the target package and the operating package. For example, for device
     * suggestions the target package would be the package for which suggestions are being made and
     * the operating package would be the package making the suggestions.
     */
    private record PackagePair(
            // The uid of the package on which the operations are being performed.
            int targetPackageUid,
            // The uid of the operating package.
            int operatingPackageUid) {}
}
