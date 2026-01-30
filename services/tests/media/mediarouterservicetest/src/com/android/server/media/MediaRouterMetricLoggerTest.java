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

import static android.media.MediaRoute2ProviderService.REASON_FAILED_TO_REROUTE_SYSTEM_MEDIA;
import static android.media.MediaRoute2ProviderService.REASON_INVALID_COMMAND;
import static android.media.MediaRoute2ProviderService.REASON_NETWORK_ERROR;
import static android.media.MediaRoute2ProviderService.REASON_REJECTED;
import static android.media.MediaRoute2ProviderService.REASON_ROUTE_NOT_AVAILABLE;
import static android.media.MediaRoute2ProviderService.REASON_UNIMPLEMENTED;
import static android.media.MediaRoute2ProviderService.REASON_UNKNOWN_ERROR;
import static android.media.RoutingSessionInfo.TRANSFER_REASON_APP;
import static android.media.RoutingSessionInfo.TRANSFER_REASON_FALLBACK;
import static android.media.RoutingSessionInfo.TRANSFER_REASON_SYSTEM_REQUEST;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.server.media.MediaRouterMetricLogger.EVENT_TYPE_CREATE_SESSION;
import static com.android.server.media.MediaRouterMetricLogger.EVENT_TYPE_CREATE_SYSTEM_ROUTING_SESSION;
import static com.android.server.media.MediaRouterMetricLogger.EVENT_TYPE_DESELECT_ROUTE;
import static com.android.server.media.MediaRouterMetricLogger.EVENT_TYPE_RELEASE_SESSION;
import static com.android.server.media.MediaRouterMetricLogger.EVENT_TYPE_SCANNING_STARTED;
import static com.android.server.media.MediaRouterMetricLogger.EVENT_TYPE_SCANNING_STOPPED;
import static com.android.server.media.MediaRouterMetricLogger.EVENT_TYPE_SELECT_ROUTE;
import static com.android.server.media.MediaRouterMetricLogger.EVENT_TYPE_TRANSFER_TO_ROUTE;
import static com.android.server.media.MediaRouterMetricLogger.EVENT_TYPE_UNSPECIFIED;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED;
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
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_SUCCESS;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNIMPLEMENTED;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNKNOWN_ERROR;
import static com.android.server.media.MediaRouterStatsLog.MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNSPECIFIED;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_LOCAL_ROUTER;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_PROXY_ROUTER;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_SYSTEM_MEDIA_CONTROLS;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_TV_OUTPUT_SWITCHER;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_APP;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_FALLBACK;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_SYSTEM_REQUEST;
import static com.android.server.media.MediaRouterStatsLog.ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_UNSPECIFIED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;

import android.media.RoutingChangeInfo;
import android.media.RoutingSessionInfo;
import android.os.SystemClock;

import androidx.test.runner.AndroidJUnit4;

import com.android.modules.utils.testing.ExtendedMockitoRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class MediaRouterMetricLoggerTest {
    @Rule
    public final ExtendedMockitoRule mExtendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).mockStatic(MediaRouterStatsLog.class).build();

    private MediaRouterMetricLogger mLogger;

    private final RoutingChangeInfo mRoutingChangeInfo =
            new RoutingChangeInfo(
                    RoutingChangeInfo.ENTRY_POINT_LOCAL_ROUTER_UNSPECIFIED,
                    /* isSuggested= */ true);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLogger = new MediaRouterMetricLogger();
    }

    @Test
    public void addRequestInfo_addsRequestInfoToCache() {
        long requestId = 123;
        int eventType = EVENT_TYPE_CREATE_SESSION;

        mLogger.addRequestInfo(requestId, eventType, mRoutingChangeInfo);

        assertThat(mLogger.getRequestCacheSize()).isEqualTo(1);
    }

    @Test
    public void removeRequestInfo_removesRequestInfoFromCache() {
        long requestId = 123;
        int eventType = EVENT_TYPE_CREATE_SESSION;
        mLogger.addRequestInfo(requestId, eventType, mRoutingChangeInfo);

        mLogger.removeRequestInfo(requestId);

        assertThat(mLogger.getRequestCacheSize()).isEqualTo(0);
    }

    @Test
    public void logOperationFailure_logsOperationFailure() {
        int eventType = EVENT_TYPE_CREATE_SESSION;
        int result = MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_REJECTED;
        mLogger.logOperationFailure(eventType, result, mRoutingChangeInfo);
        verify(
                () ->
                        MediaRouterStatsLog.write( // Use ExtendedMockito.verify and lambda
                                MEDIA_ROUTER_EVENT_REPORTED,
                                MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_CREATE_SESSION,
                                result,
                                mRoutingChangeInfo.getEntryPoint(),
                                mRoutingChangeInfo.isSuggested()));
    }

    @Test
    public void logRequestResult_logsRequestResult() {
        long requestId = 123;
        int eventType = EVENT_TYPE_CREATE_SESSION;
        int result = MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_SUCCESS;
        mLogger.addRequestInfo(requestId, eventType, mRoutingChangeInfo);

        mLogger.logRequestResult(requestId, result);

        assertThat(mLogger.getRequestCacheSize()).isEqualTo(0);
        verify(
                () ->
                        MediaRouterStatsLog.write( // Use ExtendedMockito.verify and lambda
                                MEDIA_ROUTER_EVENT_REPORTED,
                                MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_CREATE_SESSION,
                                result,
                                mRoutingChangeInfo.getEntryPoint(),
                                mRoutingChangeInfo.isSuggested()));
    }

    @Test
    public void convertResultFromReason_returnsCorrectResult() {
        assertThat(MediaRouterMetricLogger.convertResultFromReason(REASON_UNKNOWN_ERROR))
                .isEqualTo(MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNKNOWN_ERROR);
        assertThat(MediaRouterMetricLogger.convertResultFromReason(REASON_REJECTED))
                .isEqualTo(MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_REJECTED);
        assertThat(MediaRouterMetricLogger.convertResultFromReason(REASON_NETWORK_ERROR))
                .isEqualTo(MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_NETWORK_ERROR);
        assertThat(MediaRouterMetricLogger.convertResultFromReason(REASON_ROUTE_NOT_AVAILABLE))
                .isEqualTo(MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_ROUTE_NOT_AVAILABLE);
        assertThat(MediaRouterMetricLogger.convertResultFromReason(REASON_INVALID_COMMAND))
                .isEqualTo(MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_INVALID_COMMAND);
        assertThat(MediaRouterMetricLogger.convertResultFromReason(REASON_UNIMPLEMENTED))
                .isEqualTo(MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNIMPLEMENTED);
        assertThat(
                        MediaRouterMetricLogger.convertResultFromReason(
                                REASON_FAILED_TO_REROUTE_SYSTEM_MEDIA))
                .isEqualTo(
                        MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_FAILED_TO_REROUTE_SYSTEM_MEDIA);
        assertThat(MediaRouterMetricLogger.convertResultFromReason(-1))
                .isEqualTo(MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNSPECIFIED);
    }

    @Test
    public void getRequestCacheSize_returnsCorrectSize() {
        assertThat(mLogger.getRequestCacheSize()).isEqualTo(0);
        mLogger.addRequestInfo(123, EVENT_TYPE_CREATE_SESSION, mRoutingChangeInfo);
        assertThat(mLogger.getRequestCacheSize()).isEqualTo(1);
    }

    @Test
    public void addRequestInfo_whenCacheFull_evictsFromCacheAndLogsUnspecified() {
        assertThat(mLogger.getRequestCacheSize()).isEqualTo(0);

        // Fill the cache to capacity.
        int cacheCapacity = mLogger.getRequestInfoCacheCapacity();
        for (int i = 0; i < cacheCapacity; i++) {
            mLogger.addRequestInfo(
                    /* uniqueRequestId= */ i, EVENT_TYPE_CREATE_SESSION, mRoutingChangeInfo);
        }

        // Add one more request to trigger eviction.
        mLogger.addRequestInfo(
                /* uniqueRequestId= */ cacheCapacity,
                EVENT_TYPE_CREATE_SESSION,
                mRoutingChangeInfo);

        // Verify cache size is correct and generic result gets logged.
        assertThat(mLogger.getRequestCacheSize()).isEqualTo(cacheCapacity);
        verify(
                () ->
                        MediaRouterStatsLog.write(
                                MEDIA_ROUTER_EVENT_REPORTED,
                                MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_CREATE_SESSION,
                                MEDIA_ROUTER_EVENT_REPORTED__RESULT__RESULT_UNSPECIFIED,
                                mRoutingChangeInfo.getEntryPoint(),
                                mRoutingChangeInfo.isSuggested()));
    }

    // Routing change tests.
    @Test
    public void convertEntryPointForLogging_returnsExpectedResults() {
        assertThat(
                        MediaRouterMetricLogger.convertEntryPointForLogging(
                                RoutingChangeInfo.ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER))
                .isEqualTo(
                        ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER);
        assertThat(
                        MediaRouterMetricLogger.convertEntryPointForLogging(
                                RoutingChangeInfo.ENTRY_POINT_SYSTEM_MEDIA_CONTROLS))
                .isEqualTo(ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_SYSTEM_MEDIA_CONTROLS);
        assertThat(
                        MediaRouterMetricLogger.convertEntryPointForLogging(
                                RoutingChangeInfo.ENTRY_POINT_LOCAL_ROUTER_UNSPECIFIED))
                .isEqualTo(ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_LOCAL_ROUTER);
        assertThat(
                        MediaRouterMetricLogger.convertEntryPointForLogging(
                                RoutingChangeInfo.ENTRY_POINT_PROXY_ROUTER_UNSPECIFIED))
                .isEqualTo(ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_PROXY_ROUTER);
        assertThat(
                        MediaRouterMetricLogger.convertEntryPointForLogging(
                                RoutingChangeInfo.ENTRY_POINT_TV_OUTPUT_SWITCHER))
                .isEqualTo(ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_TV_OUTPUT_SWITCHER);
    }

    @Test
    public void convertEntryPointForLogging_invalidEntryPoint_throwsException() {
        // Testing below the lower limit of accepted values.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MediaRouterMetricLogger.convertEntryPointForLogging(
                                RoutingChangeInfo.ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER - 1));
        // Testing above the upper limit of accepted values.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MediaRouterMetricLogger.convertEntryPointForLogging(
                                RoutingChangeInfo.ENTRY_POINT_TV_OUTPUT_SWITCHER + 1));
    }

    @Test
    public void convertTransferReasonForLogging_returnsExpectedResults() {
        assertThat(
                        MediaRouterMetricLogger.convertTransferReasonForLogging(
                                TRANSFER_REASON_FALLBACK))
                .isEqualTo(ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_FALLBACK);
        assertThat(
                        MediaRouterMetricLogger.convertTransferReasonForLogging(
                                TRANSFER_REASON_SYSTEM_REQUEST))
                .isEqualTo(
                        ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_SYSTEM_REQUEST);
        assertThat(MediaRouterMetricLogger.convertTransferReasonForLogging(TRANSFER_REASON_APP))
                .isEqualTo(ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_APP);
        // Testing below the lower limit of accepted values.
        assertThat(
                        MediaRouterMetricLogger.convertTransferReasonForLogging(
                                TRANSFER_REASON_FALLBACK - 1))
                .isEqualTo(ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_UNSPECIFIED);
        // Testing above the upper limit of accepted values.
        assertThat(MediaRouterMetricLogger.convertTransferReasonForLogging(TRANSFER_REASON_APP + 1))
                .isEqualTo(ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_UNSPECIFIED);
    }

    @Test
    public void convertTransferReasonForLogging_invalidEntryPoint_throwsException() {
        // Testing below the lower limit of accepted values.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MediaRouterMetricLogger.convertEntryPointForLogging(
                                RoutingChangeInfo.ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER - 1));
        // Testing above the upper limit of accepted values.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MediaRouterMetricLogger.convertEntryPointForLogging(
                                RoutingChangeInfo.ENTRY_POINT_TV_OUTPUT_SWITCHER + 1));
    }

    @Test
    public void notifyRoutingChangeRequested_addsRoutingChangeInfoToCache() {
        assertThat(mLogger.getRoutingChangeInfoCacheSize()).isEqualTo(0);
        RoutingChangeInfo routingChangeInfo =
                new RoutingChangeInfo(
                        RoutingChangeInfo.ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER,
                        /* isSuggested= */ false);

        mLogger.notifyRoutingChangeRequested(/* uniqueRequestId= */ 0, routingChangeInfo);

        assertThat(mLogger.getRoutingChangeInfoCacheSize()).isEqualTo(1);
    }

    @Test
    public void notifyRoutingChange_addsOngoingRoutingChangeToCache() {
        assertThat(mLogger.getRoutingChangeInfoCacheSize()).isEqualTo(0);
        String originalSessionId = "originalSessionId";
        String clientPackageName = "clientPackageName";
        int clientPackageUid = 123;
        long uniqueRequestId = 0;
        RoutingChangeInfo routingChangeInfo =
                new RoutingChangeInfo(
                        RoutingChangeInfo.ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER,
                        /* isSuggested= */ false);
        RoutingSessionInfo routingSessionInfo =
                new RoutingSessionInfo.Builder(originalSessionId, clientPackageName)
                        .addSelectedRoute(/* routeId= */ "_")
                        .setTransferReason(TRANSFER_REASON_SYSTEM_REQUEST)
                        .build();

        mLogger.notifyRoutingChangeRequested(uniqueRequestId, routingChangeInfo);
        mLogger.notifyRoutingChange(uniqueRequestId, routingSessionInfo, clientPackageUid);

        assertThat(mLogger.getOngoingRoutingChangeCacheSize()).isEqualTo(1);
        assertThat(mLogger.getRoutingChangeInfoCacheSize()).isEqualTo(0);
    }

    @Test
    public void notifyRoutingChange_withIncorrectRequestId_isNoOp() {
        assertThat(mLogger.getRoutingChangeInfoCacheSize()).isEqualTo(0);
        String originalSessionId = "originalSessionId";
        String clientPackageName = "clientPackageName";
        int clientPackageUid = 123;
        long uniqueRequestId = 0;
        RoutingSessionInfo routingSessionInfo =
                new RoutingSessionInfo.Builder(originalSessionId, clientPackageName)
                        .addSelectedRoute(/* routeId= */ "_")
                        .setTransferReason(TRANSFER_REASON_SYSTEM_REQUEST)
                        .build();
        RoutingChangeInfo routingChangeInfo =
                new RoutingChangeInfo(
                        RoutingChangeInfo.ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER,
                        /* isSuggested= */ false);

        mLogger.notifyRoutingChangeRequested(uniqueRequestId, routingChangeInfo);
        mLogger.notifyRoutingChange(uniqueRequestId + 1, routingSessionInfo, clientPackageUid);

        // No entry should be added to the ongoingRoutingChange cache.
        assertThat(mLogger.getOngoingRoutingChangeCacheSize()).isEqualTo(0);
        // Existing entry should not be removed from routingChangeInfo cache.
        assertThat(mLogger.getRoutingChangeInfoCacheSize()).isEqualTo(1);
    }

    @Test
    public void notifySessionEnd_withIncorrectSessionId_isNoOp() {
        assertThat(mLogger.getRoutingChangeInfoCacheSize()).isEqualTo(0);
        String originalSessionId = "originalSessionId";
        String clientPackageName = "clientPackageName";
        int clientPackageUid = 123;
        long uniqueRequestId = 0;
        RoutingSessionInfo routingSessionInfo =
                new RoutingSessionInfo.Builder(originalSessionId, clientPackageName)
                        .addSelectedRoute(/* routeId= */ "_")
                        .setTransferReason(TRANSFER_REASON_SYSTEM_REQUEST)
                        .build();
        RoutingChangeInfo routingChangeInfo =
                new RoutingChangeInfo(
                        RoutingChangeInfo.ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER,
                        /* isSuggested= */ false);

        mLogger.notifyRoutingChangeRequested(uniqueRequestId, routingChangeInfo);
        mLogger.notifyRoutingChange(uniqueRequestId, routingSessionInfo, clientPackageUid);
        mLogger.notifySessionEnd("someOtherSessionId");

        assertThat(mLogger.getOngoingRoutingChangeCacheSize()).isEqualTo(1);
        verify(
                () ->
                        MediaRouterStatsLog.write(
                                anyInt(),
                                anyInt(),
                                anyInt(),
                                anyBoolean(),
                                anyInt(),
                                anyBoolean(),
                                anyLong(),
                                anyBoolean(),
                                anyBoolean(),
                                anyBoolean()),
                never());
    }

    @Test
    public void notifySessionEnd_logsRoutingChangeMetric() {
        MediaRouterMetricLogger spyLogger = spy(mLogger);
        assertThat(spyLogger.getOngoingRoutingChangeCacheSize()).isEqualTo(0);
        String originalSessionId = "originalSessionId";
        String clientPackageName = "clientPackageName";
        int clientPackageUid = 123;
        int sessionTimeInMillis = 3_000;
        long uniqueRequestId = 0;
        long sessionStartTimeInMillis = SystemClock.elapsedRealtime();
        RoutingChangeInfo routingChangeInfo =
                new RoutingChangeInfo(
                        RoutingChangeInfo.ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER,
                        /* isSuggested= */ false);
        RoutingSessionInfo routingSessionInfo =
                new RoutingSessionInfo.Builder(originalSessionId, clientPackageName)
                        .addSelectedRoute(/* routeId= */ "_")
                        .setTransferReason(TRANSFER_REASON_SYSTEM_REQUEST)
                        .build();
        // Returns the start time of the session when called the first time and the session end time
        // the second time.
        doReturn(sessionStartTimeInMillis)
                .doReturn(sessionStartTimeInMillis + sessionTimeInMillis)
                .when(spyLogger)
                .getElapsedRealTime();

        spyLogger.notifyRoutingChangeRequested(uniqueRequestId, routingChangeInfo);
        spyLogger.notifyRoutingChange(uniqueRequestId, routingSessionInfo, clientPackageUid);
        spyLogger.notifySessionEnd(originalSessionId);

        assertThat(spyLogger.getOngoingRoutingChangeCacheSize()).isEqualTo(0);
        ArgumentCaptor<Long> sessionLengthCaptor = ArgumentCaptor.forClass(Long.class);
        verify(
                () ->
                        MediaRouterStatsLog.write(
                                eq(ROUTING_CHANGE_REPORTED),
                                eq(
                                        ROUTING_CHANGE_REPORTED__ENTRY_POINT__ENTRY_POINT_SYSTEM_OUTPUT_SWITCHER),
                                eq(clientPackageUid),
                                eq(/* isSystemRoute= */ false),
                                eq(
                                        ROUTING_CHANGE_REPORTED__TRANSFER_REASON__TRANSFER_REASON_SYSTEM_REQUEST),
                                eq(/* isSuggested= */ false),
                                sessionLengthCaptor.capture(),
                                eq(/* isSuggestedByRlp= */ false),
                                eq(/* isSuggestedByMediaApp= */ false),
                                eq(/* isSuggestedByOtherApp= */ false)));
        assertThat(sessionLengthCaptor.getValue()).isEqualTo(sessionTimeInMillis);
    }

    @Test
    public void convertEventTypeForLogging_returnsExpectedResult() {
        assertThat(MediaRouterMetricLogger.convertEventTypeForLogging(EVENT_TYPE_UNSPECIFIED))
                .isEqualTo(MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_UNSPECIFIED);
        assertThat(MediaRouterMetricLogger.convertEventTypeForLogging(EVENT_TYPE_CREATE_SESSION))
                .isEqualTo(MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_CREATE_SESSION);
        assertThat(
                        MediaRouterMetricLogger.convertEventTypeForLogging(
                                EVENT_TYPE_CREATE_SYSTEM_ROUTING_SESSION))
                .isEqualTo(
                        MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_CREATE_SYSTEM_ROUTING_SESSION);
        assertThat(MediaRouterMetricLogger.convertEventTypeForLogging(EVENT_TYPE_RELEASE_SESSION))
                .isEqualTo(MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_RELEASE_SESSION);
        assertThat(MediaRouterMetricLogger.convertEventTypeForLogging(EVENT_TYPE_SELECT_ROUTE))
                .isEqualTo(MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_SELECT_ROUTE);
        assertThat(MediaRouterMetricLogger.convertEventTypeForLogging(EVENT_TYPE_DESELECT_ROUTE))
                .isEqualTo(MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_DESELECT_ROUTE);
        assertThat(MediaRouterMetricLogger.convertEventTypeForLogging(EVENT_TYPE_TRANSFER_TO_ROUTE))
                .isEqualTo(MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_TRANSFER_TO_ROUTE);
        assertThat(MediaRouterMetricLogger.convertEventTypeForLogging(EVENT_TYPE_SCANNING_STARTED))
                .isEqualTo(MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_SCANNING_STARTED);
        assertThat(MediaRouterMetricLogger.convertEventTypeForLogging(EVENT_TYPE_SCANNING_STOPPED))
                .isEqualTo(MEDIA_ROUTER_EVENT_REPORTED__EVENT_TYPE__EVENT_TYPE_SCANNING_STOPPED);
    }

    @Test
    public void convertEntryPointForLogging_invalidInput_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MediaRouterMetricLogger.convertEntryPointForLogging(
                                EVENT_TYPE_UNSPECIFIED - 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MediaRouterMetricLogger.convertEntryPointForLogging(
                                EVENT_TYPE_SCANNING_STOPPED + 1));
    }
}
