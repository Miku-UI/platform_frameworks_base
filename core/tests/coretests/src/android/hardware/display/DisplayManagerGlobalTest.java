/*
 * Copyright 2021 The Android Open Source Project
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

package android.hardware.display;

import static android.hardware.display.DisplayManagerGlobal.EVENT_DISPLAY_STATE_CHANGED;
import static android.hardware.display.DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_REFRESH_RATE;
import static android.hardware.display.DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_STATE;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.hardware.display.DisplayManagerGlobal.DisplayIdsCache;
import android.os.Handler;
import android.os.RemoteException;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.Display;
import android.view.DisplayInfo;

import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.server.display.feature.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Tests for {@link DisplayManagerGlobal}.
 *
 * Build/Install/Run:
 *  atest FrameworksCoreTests:DisplayManagerGlobalTest
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class DisplayManagerGlobalTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private static final long DISPLAY_CHANGE_EVENTS =
            DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_BASIC_CHANGED
            | DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_REFRESH_RATE;

    private static final long ALL_DISPLAY_EVENTS =
            DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_ADDED
            | DISPLAY_CHANGE_EVENTS
            | DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_REMOVED;

    @Mock
    private IDisplayManager mDisplayManager;

    @Mock
    private DisplayManager.DisplayListener mDisplayListener;

    @Mock
    private DisplayManager.DisplayListener mDisplayListener2;

    @Mock
    private Consumer<DisplayTopology> mTopologyListener;

    @Captor
    private ArgumentCaptor<IDisplayManagerCallback> mCallbackCaptor;

    private Context mContext;
    private DisplayManagerGlobal mDisplayManagerGlobal;
    private Handler mHandler;
    private Executor mExecutor;

    @Before
    public void setUp() throws RemoteException {
        MockitoAnnotations.initMocks(this);
        Mockito.when(mDisplayManager.getPreferredWideGamutColorSpaceId()).thenReturn(0);
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mHandler = mContext.getMainThreadHandler();
        mExecutor = mContext.getMainExecutor();
        mDisplayManagerGlobal = new DisplayManagerGlobal(mDisplayManager);
    }

    @Test
    public void testDisplayListenerIsCalled_WhenDisplayEventOccurs() throws RemoteException {
        mDisplayManagerGlobal.disableLocalDisplayInfoCaches();
        mDisplayManagerGlobal.registerDisplayListener(mDisplayListener, mHandler,
                ALL_DISPLAY_EVENTS,
                /* packageName= */ null,
                /* isEventFilterExplicit */ true);
        Mockito.verify(mDisplayManager)
                .registerCallbackWithEventMask(mCallbackCaptor.capture(), anyLong());
        IDisplayManagerCallback callback = mCallbackCaptor.getValue();

        var defaultDisplaySnapshot = new int[] { Display.DEFAULT_DISPLAY };
        callback.onDisplaySnapshot(defaultDisplaySnapshot, defaultDisplaySnapshot);

        int displayId = 1;
        callback.onDisplayEvent(displayId, DisplayManagerGlobal.EVENT_DISPLAY_ADDED);
        waitForHandler();
        Mockito.verify(mDisplayListener).onDisplayAdded(eq(displayId));
        Mockito.verifyNoMoreInteractions(mDisplayListener);

        Mockito.reset(mDisplayListener);
        // Mock IDisplayManager to return a different display info to trigger display change.
        final DisplayInfo newDisplayInfo = new DisplayInfo();
        newDisplayInfo.rotation++;
        doReturn(newDisplayInfo).when(mDisplayManager).getDisplayInfo(displayId);
        callback.onDisplayEvent(displayId, DisplayManagerGlobal.EVENT_DISPLAY_BASIC_CHANGED);
        waitForHandler();
        Mockito.verify(mDisplayListener).onDisplayChanged(eq(displayId));
        Mockito.verifyNoMoreInteractions(mDisplayListener);

        Mockito.reset(mDisplayListener);
        callback.onDisplayEvent(displayId, DisplayManagerGlobal.EVENT_DISPLAY_REMOVED);
        waitForHandler();
        Mockito.verify(mDisplayListener).onDisplayRemoved(eq(displayId));
        Mockito.verifyNoMoreInteractions(mDisplayListener);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DISPLAY_LISTENER_PERFORMANCE_IMPROVEMENTS)
    public void testDisplayListenerIsCalled_WhenDisplayPropertyChangeEventOccurs()
            throws RemoteException {
        mDisplayManagerGlobal.registerDisplayListener(mDisplayListener, mHandler,
                INTERNAL_EVENT_FLAG_DISPLAY_REFRESH_RATE
                        | INTERNAL_EVENT_FLAG_DISPLAY_STATE,
                null, /* isEventFilterExplicit */ true);
        Mockito.verify(mDisplayManager)
                .registerCallbackWithEventMask(mCallbackCaptor.capture(), anyLong());
        IDisplayManagerCallback callback = mCallbackCaptor.getValue();

        int displayId = 1;

        Mockito.reset(mDisplayListener);
        callback.onDisplayEvent(displayId, DisplayManagerGlobal.EVENT_DISPLAY_REFRESH_RATE_CHANGED);
        waitForHandler();
        Mockito.verify(mDisplayListener).onDisplayChanged(eq(displayId));
        Mockito.verifyNoMoreInteractions(mDisplayListener);

        Mockito.reset(mDisplayListener);
        callback.onDisplayEvent(displayId, EVENT_DISPLAY_STATE_CHANGED);
        waitForHandler();
        Mockito.verify(mDisplayListener).onDisplayChanged(eq(displayId));
        Mockito.verifyNoMoreInteractions(mDisplayListener);
    }

    @Test
    @RequiresFlagsEnabled({
            Flags.FLAG_DISPLAY_LISTENER_PERFORMANCE_IMPROVEMENTS,
            Flags.FLAG_COMMITTED_STATE_SEPARATE_EVENT
    })
    public void testDisplayEventsAreHandledInCorrectOrder() throws RemoteException {
        testDisplayEventsAreHandledInCorrectOrderInternal();
    }

    private void testDisplayEventsAreHandledInCorrectOrderInternal() throws RemoteException {
        // Register a listener for all possible events.
        long allInternalEvents =
                DisplayManagerGlobal.INTERNAL_EVENT_FLAG_TOPOLOGY_UPDATED
                        | DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_CONNECTION_CHANGED
                        | DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_ADDED
                        | DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_BASIC_CHANGED
                        | DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_REFRESH_RATE
                        | DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_STATE
                        | DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_COMMITTED_STATE_CHANGED
                        | DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_HDR_SDR_RATIO_CHANGED
                        | DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_BRIGHTNESS_CHANGED
                        | DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_REMOVED;

        mDisplayManagerGlobal.registerDisplayListener(mDisplayListener, mHandler,
                allInternalEvents, /* packageName= */ null,
                /* isEventFilterExplicit */ true);
        Mockito.verify(mDisplayManager)
                .registerCallbackWithEventMask(mCallbackCaptor.capture(), anyLong());
        IDisplayManagerCallback callback = mCallbackCaptor.getValue();

        int displayId = 1;
        // Mock IDisplayManager to return a different display info to trigger display change.
        final DisplayInfo newDisplayInfo = new DisplayInfo();
        newDisplayInfo.rotation++;
        doReturn(newDisplayInfo).when(mDisplayManager).getDisplayInfo(displayId);

        int allEventsMask =
                DisplayManagerGlobal.EVENT_DISPLAY_CONNECTED
                        | DisplayManagerGlobal.EVENT_DISPLAY_ADDED
                        | DisplayManagerGlobal.EVENT_DISPLAY_BASIC_CHANGED
                        | DisplayManagerGlobal.EVENT_DISPLAY_REFRESH_RATE_CHANGED
                        | DisplayManagerGlobal.EVENT_DISPLAY_STATE_CHANGED
                        | DisplayManagerGlobal.EVENT_DISPLAY_COMMITTED_STATE_CHANGED
                        | DisplayManagerGlobal.EVENT_DISPLAY_HDR_SDR_RATIO_CHANGED
                        | DisplayManagerGlobal.EVENT_DISPLAY_BRIGHTNESS_CHANGED
                        | DisplayManagerGlobal.EVENT_DISPLAY_REMOVED
                        | DisplayManagerGlobal.EVENT_DISPLAY_DISCONNECTED;

        // Trigger the event.
        callback.onDisplayEvent(displayId, allEventsMask);
        waitForHandler();

        // Verify the order of callbacks. The order should be based on the event's integer value,
        // not the order they were OR'd into the mask.
        InOrder inOrder = inOrder(mDisplayListener);
        inOrder.verify(mDisplayListener).onDisplayConnected(eq(displayId));
        inOrder.verify(mDisplayListener).onDisplayAdded(eq(displayId));
        // BASIC_CHANGED, REFRESH_RATE_CHANGED, STATE_CHANGED, COMMITTED_STATE_CHANGED
        // HDR_SDR_RATIO_CHANGED, BRIGHTNESS_CHANGED
        inOrder.verify(mDisplayListener, times(6)).onDisplayChanged(eq(displayId));
        inOrder.verify(mDisplayListener).onDisplayRemoved(eq(displayId));
        inOrder.verify(mDisplayListener).onDisplayDisconnected(eq(displayId));

        Mockito.verifyNoMoreInteractions(mDisplayListener);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DELAY_IMPLICIT_RR_REGISTRATION_UNTIL_RR_ACCESSED)
    public void test_refreshRateRegistration_implicitRRCallbacksEnabled()
            throws RemoteException {
        DisplayManager.DisplayListener displayListener1 =
                Mockito.mock(DisplayManager.DisplayListener.class);
        // Subscription without supplied events doesn't subscribe to RR events
        mDisplayManagerGlobal.registerDisplayListener(displayListener1, mHandler,
                ALL_DISPLAY_EVENTS, /* packageName= */ null,
                /* isEventFilterExplicit */ false);
        Mockito.verify(mDisplayManager)
                .registerCallbackWithEventMask(mCallbackCaptor.capture(), eq(ALL_DISPLAY_EVENTS));

        // After registering to refresh rate changes, subscription without supplied events subscribe
        // to RR events
        mDisplayManagerGlobal.registerForRefreshRateChanges();
        DisplayManager.DisplayListener displayListener2 =
                Mockito.mock(DisplayManager.DisplayListener.class);
        mDisplayManagerGlobal.registerDisplayListener(displayListener2, mHandler,
                ALL_DISPLAY_EVENTS, /* packageName= */ null,
                /* isEventFilterExplicit */ false);
        Mockito.verify(mDisplayManager)
                .registerCallbackWithEventMask(mCallbackCaptor.capture(), eq(ALL_DISPLAY_EVENTS
                        | INTERNAL_EVENT_FLAG_DISPLAY_REFRESH_RATE));

        // Assert all the existing listeners are also subscribed to RR events
        CopyOnWriteArrayList<DisplayManagerGlobal.DisplayListenerDelegate> delegates =
                mDisplayManagerGlobal.getDisplayListeners();
        for (DisplayManagerGlobal.DisplayListenerDelegate delegate: delegates) {
            assertEquals(ALL_DISPLAY_EVENTS | INTERNAL_EVENT_FLAG_DISPLAY_REFRESH_RATE,
                    delegate.internalEventFlagsMask);
        }

        // Subscription to RR when events are supplied doesn't happen
        DisplayManager.DisplayListener displayListener3 =
                Mockito.mock(DisplayManager.DisplayListener.class);
        mDisplayManagerGlobal.registerDisplayListener(displayListener3, mHandler,
                ALL_DISPLAY_EVENTS, /* packageName= */ null,
                /* isEventFilterExplicit */ true);
        Mockito.verify(mDisplayManager)
                .registerCallbackWithEventMask(mCallbackCaptor.capture(), eq(ALL_DISPLAY_EVENTS));

        // Assert one listeners are not subscribed to RR events
        delegates =  mDisplayManagerGlobal.getDisplayListeners();
        int subscribedListenersCount = 0;
        int nonSubscribedListenersCount = 0;
        for (DisplayManagerGlobal.DisplayListenerDelegate delegate: delegates) {
            if (delegate.isEventFilterExplicit()) {
                assertEquals(ALL_DISPLAY_EVENTS, delegate.internalEventFlagsMask);
                nonSubscribedListenersCount++;
            } else {
                assertEquals(ALL_DISPLAY_EVENTS | INTERNAL_EVENT_FLAG_DISPLAY_REFRESH_RATE,
                        delegate.internalEventFlagsMask);
                subscribedListenersCount++;
            }
        }

        assertEquals(2, subscribedListenersCount);
        assertEquals(1, nonSubscribedListenersCount);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_DELAY_IMPLICIT_RR_REGISTRATION_UNTIL_RR_ACCESSED)
    public void test_refreshRateRegistration_implicitRRCallbacksDisabled()
            throws RemoteException {
        mDisplayManagerGlobal.registerDisplayListener(mDisplayListener, mHandler,
                ALL_DISPLAY_EVENTS, /* packageName= */ null,
                /* isEventFilterExplicit */ false);
        Mockito.verify(mDisplayManager)
                .registerCallbackWithEventMask(mCallbackCaptor.capture(), eq(ALL_DISPLAY_EVENTS
                        | INTERNAL_EVENT_FLAG_DISPLAY_REFRESH_RATE));
    }

    @Test
    public void testDisplayListenerIsNotCalled_WhenClientIsNotSubscribed() throws RemoteException {
        // First we subscribe to all events in order to test that the subsequent calls to
        // registerDisplayListener will update the event mask.
        mDisplayManagerGlobal.registerDisplayListener(mDisplayListener, mHandler,
                ALL_DISPLAY_EVENTS, /* packageName= */ null,
                /* isEventFilterExplicit */ true);
        Mockito.verify(mDisplayManager)
                .registerCallbackWithEventMask(mCallbackCaptor.capture(), anyLong());
        IDisplayManagerCallback callback = mCallbackCaptor.getValue();

        int displayId = 1;
        mDisplayManagerGlobal.registerDisplayListener(mDisplayListener, mHandler,
                ALL_DISPLAY_EVENTS
                        & ~DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_ADDED, null,
                        /* isEventFilterExplicit */ true);
        callback.onDisplayEvent(displayId, DisplayManagerGlobal.EVENT_DISPLAY_ADDED);
        waitForHandler();
        Mockito.verifyNoMoreInteractions(mDisplayListener);

        mDisplayManagerGlobal.registerDisplayListener(mDisplayListener, mHandler,
                ALL_DISPLAY_EVENTS
                        & ~DISPLAY_CHANGE_EVENTS, null,
                        /* isEventFilterExplicit */ true);
        callback.onDisplayEvent(displayId, DisplayManagerGlobal.EVENT_DISPLAY_BASIC_CHANGED);
        waitForHandler();
        Mockito.verifyNoMoreInteractions(mDisplayListener);

        mDisplayManagerGlobal.registerDisplayListener(mDisplayListener, mHandler,
                ALL_DISPLAY_EVENTS
                        & ~DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_REMOVED, null,
                        /* isEventFilterExplicit */ true);
        callback.onDisplayEvent(displayId, DisplayManagerGlobal.EVENT_DISPLAY_REMOVED);
        waitForHandler();
        Mockito.verifyNoMoreInteractions(mDisplayListener);
    }

    @Test
    public void testDisplayManagerGlobalRegistersWithDisplayManager_WhenThereAreNoOtherListeners()
            throws RemoteException {
        mDisplayManagerGlobal.registerNativeChoreographerForRefreshRateCallbacks();
        Mockito.verify(mDisplayManager)
                .registerCallbackWithEventMask(mCallbackCaptor.capture(), eq(ALL_DISPLAY_EVENTS));

        mDisplayManagerGlobal.unregisterNativeChoreographerForRefreshRateCallbacks();
        Mockito.verify(mDisplayManager)
                .registerCallbackWithEventMask(mCallbackCaptor.capture(), eq(0L));

    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DELAY_IMPLICIT_RR_REGISTRATION_UNTIL_RR_ACCESSED)
    public void test_registerNativeRefreshRateCallbacks_enablesRRImplicitRegistrations()
            throws RemoteException {
        // Registering the display listener without supplied events doesn't subscribe to RR events
        mDisplayManagerGlobal.registerDisplayListener(mDisplayListener, mHandler,
                ALL_DISPLAY_EVENTS, /* packageName= */ null,
                /* isEventFilterExplicit */ false);
        Mockito.verify(mDisplayManager)
                .registerCallbackWithEventMask(mCallbackCaptor.capture(), eq(ALL_DISPLAY_EVENTS));

        // Native subscription for refresh rates is done
        mDisplayManagerGlobal.registerNativeChoreographerForRefreshRateCallbacks();

        // Registering the display listener without supplied events subscribe to RR events
        mDisplayManagerGlobal.registerDisplayListener(mDisplayListener, mHandler,
                ALL_DISPLAY_EVENTS, /* packageName= */ null,
                /* isEventFilterExplicit */ false);
        Mockito.verify(mDisplayManager)
                .registerCallbackWithEventMask(mCallbackCaptor.capture(), eq(ALL_DISPLAY_EVENTS
                        | INTERNAL_EVENT_FLAG_DISPLAY_REFRESH_RATE));
    }

    @Test
    public void testDisplayManagerGlobalRegistersWithDisplayManager_WhenThereAreListeners()
            throws RemoteException {
        mDisplayManagerGlobal.registerDisplayListener(mDisplayListener, mHandler,
                DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_BRIGHTNESS_CHANGED,
                null, /* isEventFilterExplicit */ true);
        InOrder inOrder = Mockito.inOrder(mDisplayManager);

        inOrder.verify(mDisplayManager)
                .registerCallbackWithEventMask(mCallbackCaptor.capture(),
                        eq(DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_BRIGHTNESS_CHANGED));

        mDisplayManagerGlobal.registerNativeChoreographerForRefreshRateCallbacks();
        inOrder.verify(mDisplayManager)
                .registerCallbackWithEventMask(mCallbackCaptor.capture(),
                        eq(ALL_DISPLAY_EVENTS
                                | DisplayManagerGlobal
                                .INTERNAL_EVENT_FLAG_DISPLAY_BRIGHTNESS_CHANGED));

        mDisplayManagerGlobal.unregisterNativeChoreographerForRefreshRateCallbacks();
        inOrder.verify(mDisplayManager)
                .registerCallbackWithEventMask(mCallbackCaptor.capture(),
                        eq(DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_BRIGHTNESS_CHANGED));

        mDisplayManagerGlobal.unregisterDisplayListener(mDisplayListener);
        inOrder.verify(mDisplayManager)
                .registerCallbackWithEventMask(mCallbackCaptor.capture(), eq(0L));
    }

    @Test
    public void testHandleDisplayChangeFromWindowManager() throws RemoteException {
        // Mock IDisplayManager to return a display info to trigger display change.
        final DisplayInfo newDisplayInfo = new DisplayInfo();
        doReturn(newDisplayInfo).when(mDisplayManager).getDisplayInfo(123);
        doReturn(newDisplayInfo).when(mDisplayManager).getDisplayInfo(321);

        // Nothing happens when there is no listener.
        mDisplayManagerGlobal.handleDisplayChangeFromWindowManager(123);

        // One listener listens on add/remove, and the other one listens on change.
        mDisplayManagerGlobal.registerDisplayListener(mDisplayListener, mHandler,
                DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_ADDED
                        | DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_REMOVED,
                null /* packageName */, /* isEventFilterExplicit */ true);
        mDisplayManagerGlobal.registerDisplayListener(mDisplayListener2, mHandler,
                DISPLAY_CHANGE_EVENTS, null /* packageName */,
                /* isEventFilterExplicit */ true);

        mDisplayManagerGlobal.handleDisplayChangeFromWindowManager(321);
        waitForHandler();

        verify(mDisplayListener, never()).onDisplayChanged(anyInt());
        verify(mDisplayListener2).onDisplayChanged(321);

        // Trigger the callback again even if the display info is not changed.
        clearInvocations(mDisplayListener2);
        mDisplayManagerGlobal.handleDisplayChangeFromWindowManager(321);
        waitForHandler();

        verify(mDisplayListener2).onDisplayChanged(321);

        // No callback for non-existing display (no display info returned from IDisplayManager).
        clearInvocations(mDisplayListener2);
        mDisplayManagerGlobal.handleDisplayChangeFromWindowManager(456);
        waitForHandler();

        verify(mDisplayListener2, never()).onDisplayChanged(anyInt());
    }

    @Test
    public void testTopologyListenerIsCalled_WhenTopologyUpdateOccurs() throws RemoteException {
        mDisplayManagerGlobal.registerTopologyListener(mExecutor, mTopologyListener,
                /* packageName= */ null);
        Mockito.verify(mDisplayManager).registerCallbackWithEventMask(mCallbackCaptor.capture(),
                eq(DisplayManagerGlobal.INTERNAL_EVENT_FLAG_TOPOLOGY_UPDATED));
        IDisplayManagerCallback callback = mCallbackCaptor.getValue();

        DisplayTopology topology = new DisplayTopology();
        callback.onTopologyChanged(topology);
        waitForHandler();
        Mockito.verify(mTopologyListener).accept(topology);
        Mockito.verifyNoMoreInteractions(mTopologyListener);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_DISPLAY_LISTENER_PERFORMANCE_IMPROVEMENTS)
    public void testMapFiltersToInternalEventFlag() {
        // Test public flags mapping
        assertEquals(DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_ADDED,
                mDisplayManagerGlobal
                        .mapFiltersToInternalEventFlag(DisplayManager.EVENT_TYPE_DISPLAY_ADDED, 0));
        assertEquals(DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_BASIC_CHANGED,
                mDisplayManagerGlobal
                        .mapFiltersToInternalEventFlag(DisplayManager.EVENT_TYPE_DISPLAY_CHANGED,
                                0));
        assertEquals(DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_REMOVED,
                mDisplayManagerGlobal.mapFiltersToInternalEventFlag(
                        DisplayManager.EVENT_TYPE_DISPLAY_REMOVED, 0));
        assertEquals(INTERNAL_EVENT_FLAG_DISPLAY_REFRESH_RATE,
                mDisplayManagerGlobal
                        .mapFiltersToInternalEventFlag(
                                DisplayManager.EVENT_TYPE_DISPLAY_REFRESH_RATE,
                                0));
        assertEquals(DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_STATE,
                mDisplayManagerGlobal
                        .mapFiltersToInternalEventFlag(
                                DisplayManager.EVENT_TYPE_DISPLAY_STATE,
                                0));
        assertEquals(DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_BRIGHTNESS_CHANGED,
                mDisplayManagerGlobal
                        .mapFiltersToInternalEventFlag(
                                DisplayManager.EVENT_TYPE_DISPLAY_BRIGHTNESS,
                                0));

        // test private flags mapping
        assertEquals(DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_CONNECTION_CHANGED,
                mDisplayManagerGlobal
                        .mapFiltersToInternalEventFlag(0,
                                DisplayManager.PRIVATE_EVENT_TYPE_DISPLAY_CONNECTION_CHANGED));
        assertEquals(DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_HDR_SDR_RATIO_CHANGED,
                mDisplayManagerGlobal
                        .mapFiltersToInternalEventFlag(0,
                                DisplayManager.PRIVATE_EVENT_TYPE_HDR_SDR_RATIO_CHANGED));

        // Test both public and private flags mapping
        assertEquals(DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_COMMITTED_STATE_CHANGED
                        | INTERNAL_EVENT_FLAG_DISPLAY_REFRESH_RATE,
                mDisplayManagerGlobal
                        .mapFiltersToInternalEventFlag(
                                DisplayManager.EVENT_TYPE_DISPLAY_REFRESH_RATE,
                                DisplayManager.PRIVATE_EVENT_TYPE_DISPLAY_COMMITTED_STATE_CHANGED));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_COMMITTED_STATE_SEPARATE_EVENT)
    public void test_mapPrivateEventCommittedStateChanged_flagEnabled() {
        // Test public flags mapping
        assertEquals(DisplayManagerGlobal.INTERNAL_EVENT_FLAG_DISPLAY_COMMITTED_STATE_CHANGED,
                mDisplayManagerGlobal
                        .mapFiltersToInternalEventFlag(0,
                                DisplayManager.PRIVATE_EVENT_TYPE_DISPLAY_COMMITTED_STATE_CHANGED));
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_COMMITTED_STATE_SEPARATE_EVENT)
    public void test_mapPrivateEventCommittedStateChanged_flagDisabled() {
        // Test public flags mapping
        assertEquals(0,
                mDisplayManagerGlobal
                        .mapFiltersToInternalEventFlag(0,
                                DisplayManager.PRIVATE_EVENT_TYPE_DISPLAY_COMMITTED_STATE_CHANGED));
    }

    @Test
    public void testUpdateDisplayIdsCache() {
        var cache = initDisplayIdsCache(/*enableConnectedCache=*/ true, /*enableAddedCache=*/ true);
        assertExpectedCache(cache, new int[] { 0, 1 }, new int[] { 0 });

        cache.updateCacheLocked(1, DisplayManagerGlobal.EVENT_DISPLAY_ADDED);
        assertExpectedCache(cache, new int[] { 0, 1 }, new int[] { 0, 1 });

        cache.updateCacheLocked(1, DisplayManagerGlobal.EVENT_DISPLAY_REMOVED
                        | DisplayManagerGlobal.EVENT_DISPLAY_DISCONNECTED);
        assertExpectedCache(cache, new int[] { 0 }, new int[] { 0 });

        cache.updateCacheLocked(2, DisplayManagerGlobal.EVENT_DISPLAY_ADDED
                        | DisplayManagerGlobal.EVENT_DISPLAY_CONNECTED);
        assertExpectedCache(cache, new int[] { 0, 2 }, new int[] { 0, 2 });
    }

    @Test
    @EnableFlags(Flags.FLAG_DISPLAY_IDS_CACHE_VALIDATION)
    public void testConnectedEventForLocallyControlledDisplay_doesNotStopLocalControl() {
        var cache = initDisplayIdsCache(/*enableConnectedCache=*/ true, /*enableAddedCache=*/ true);
        cache.injectLocked(2);
        var mask = cache.updateCacheLocked(2, DisplayManagerGlobal.EVENT_DISPLAY_CONNECTED);
        assertEquals("Out mask must contain CONNECTED",
                DisplayManagerGlobal.EVENT_DISPLAY_CONNECTED, mask);

        assertExpectedCache(cache, new int[] { 0, 1, 2 }, new int[] { 0, 2 });
        mask = cache.updateCacheLocked(2, DisplayManagerGlobal.EVENT_DISPLAY_ADDED);
        assertEquals("Out mask must contain ADDED",
                DisplayManagerGlobal.EVENT_DISPLAY_ADDED, mask);
        assertExpectedCache(cache, new int[] { 0, 1, 2 }, new int[] { 0, 2 });
    }

    @Test
    public void testUpdateDisplayIdsCache_skipRemovedAndDisconnectedForUnknownDisplayId() {
        var cache = initDisplayIdsCache(/*enableConnectedCache=*/ true, /*enableAddedCache=*/ true);
        var mask = cache.updateCacheLocked(1, DisplayManagerGlobal.EVENT_DISPLAY_REMOVED
                | DisplayManagerGlobal.EVENT_DISPLAY_DISCONNECTED);
        assertEquals("Out mask must contain REMOVED AND DISCONNECTED",
                DisplayManagerGlobal.EVENT_DISPLAY_REMOVED
                        | DisplayManagerGlobal.EVENT_DISPLAY_DISCONNECTED, mask);

        mask = cache.updateCacheLocked(2, DisplayManagerGlobal.EVENT_DISPLAY_REMOVED
                | DisplayManagerGlobal.EVENT_DISPLAY_DISCONNECTED);
        assertEquals("Out mask should not contain REMOVED AND DISCONNECTED", 0, mask);
    }

    @Test
    public void testInvalidateDisplayIdsCache() {
        var cache = initDisplayIdsCache(/*enableConnectedCache=*/ true, /*enableAddedCache=*/ true);
        assertExpectedCache(cache, new int[] { 0, 1 }, new int[] { 0 });

        cache.setAddedCachingEnabledLocked(false);
        assertExpectedCache(cache, new int[] { 0, 1 }, null);

        cache.setConnectedCachingEnabledLocked(false);
        assertExpectedCache(cache, null, null);

        cache.setConnectedCachingEnabledLocked(true);
        assertExpectedCache(cache, null, null);

        cache.setAddedCachingEnabledLocked(true);
        assertExpectedCache(cache, null, null);

        cache.updateCacheLocked(new int[] { 0 }, new int[] {});
        assertExpectedCache(cache, new int[] { 0 }, null);

        cache.updateCacheLocked(new int[] { 0, 1 }, new int[] { 0 });
        assertExpectedCache(cache, new int[] { 0, 1 }, new int[] { 0 });

        cache.updateCacheLocked(new int[] { 0, 1, 2 }, new int[] {});
        assertExpectedCache(cache, new int[] { 0, 1, 2 }, new int[] { 0 });

        cache.updateCacheLocked(new int[] {}, new int[] { 0, 1 });
        assertExpectedCache(cache, new int[] { 0, 1, 2 }, new int[] { 0, 1 });
    }

    @Test
    public void testInjectLocalDisplayId() {
        var cache = initDisplayIdsCache(/*enableConnectedCache=*/ true, /*enableAddedCache=*/ true);
        cache.injectLocked(2);
        assertExpectedCache(cache, new int[] { 0, 1, 2 }, new int[] { 0, 2 });
        cache.evictLocked(2);
        assertExpectedCache(cache, new int[] { 0, 1 }, new int[] { 0 });
    }

    @Test
    public void testInjectLocalDisplayIdAddedOnly() {
        var cache = initDisplayIdsCache(/*enableConnectedCache=*/ false,
                /*enableAddedCache=*/ true);
        assertExpectedCache(cache, null, new int[] { 0 });
        cache.injectLocked(2);
        assertExpectedCache(cache, null, new int[] { 0, 2 });
        cache.evictLocked(2);
        assertExpectedCache(cache, null, new int[] { 0 });
    }

    @Test
    public void testInjectLocalDisplayIdConnectedOnly() {
        var cache = initDisplayIdsCache(/*enableConnectedCache=*/ true,
                /*enableAddedCache=*/ false);
        assertExpectedCache(cache, new int[] { 0, 1 }, null);
        cache.injectLocked(2);
        assertExpectedCache(cache, new int[] { 0, 1, 2 }, null);
        cache.evictLocked(1);
        assertExpectedCache(cache, new int[] { 0, 2 }, null);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_NULL_DISPLAY_INFO_CACHE)
    public void getDisplayInfo_cachesNullResultsFromService() throws Exception {
        int testDisplayId = 123;

        doReturn(null).when(mDisplayManager).getDisplayInfo(testDisplayId);

        // First call, triggers the binder call.
        DisplayInfo info1 = mDisplayManagerGlobal.getDisplayInfo(testDisplayId);
        assertNull("First call should return null as mocked", info1);
        verify(mDisplayManager, times(1)).getDisplayInfo(testDisplayId);

        // Second call to getDisplayInfo, should retrieve the cached null value.
        DisplayInfo info2 = mDisplayManagerGlobal.getDisplayInfo(testDisplayId);
        assertNull("Second call should also return null", info2);
        // no additional binder calls to getDisplayInfo
        verify(mDisplayManager, times(1)).getDisplayInfo(testDisplayId);
    }

    private void assertExpectedCache(DisplayIdsCache cache, @Nullable int[] connectedIds,
            @Nullable int[] addedIds) {
        var connected = cache.getConnectedLocked();
        var cacheConnected = connected != null ? Arrays.stream(connected).sorted().toArray() : null;
        assertArrayEquals("Connected ids must be equal", connectedIds, cacheConnected);
        var added = cache.getAddedLocked();
        var cacheAdded = added != null ? Arrays.stream(added).sorted().toArray() : null;
        assertArrayEquals("Added ids must be equal", addedIds, cacheAdded);
    }

    private DisplayIdsCache initDisplayIdsCache(boolean enableConnectedCache,
            boolean enableAddedCache) {
        var cache = new DisplayIdsCache();
        cache.setConnectedCachingEnabledLocked(enableConnectedCache);
        cache.setAddedCachingEnabledLocked(enableAddedCache);
        var connectedSnapshot = new int[] { 0, 1 };
        var addedSnapshot = new int[] { 0 };

        assertNull(cache.getAddedLocked());
        assertNull(cache.getConnectedLocked());

        cache.updateCacheLocked(connectedSnapshot, addedSnapshot);

        return cache;
    }

    private void waitForHandler() {
        mHandler.runWithScissors(() -> {
        }, 0);
    }
}
