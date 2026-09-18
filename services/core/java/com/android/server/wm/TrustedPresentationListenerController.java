/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.server.wm;

import static android.companion.virtualdevice.flags.Flags.computerControlAccess;
import static android.graphics.Matrix.MSCALE_X;
import static android.graphics.Matrix.MSCALE_Y;
import static android.graphics.Matrix.MSKEW_X;
import static android.graphics.Matrix.MSKEW_Y;
import static android.view.Display.INVALID_DISPLAY;

import static com.android.internal.protolog.WmProtoLogGroups.WM_DEBUG_TPL;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.InputConfig;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.IntArray;
import android.util.Pair;
import android.util.Size;
import android.util.SparseArray;
import android.view.InputWindowHandle;
import android.window.ITrustedPresentationListener;
import android.window.TrustedPresentationThresholds;
import android.window.WindowInfosListener;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.protolog.ProtoLog;
import com.android.server.LocalServices;
import com.android.server.companion.virtual.VirtualDeviceManagerInternal;
import com.android.server.wm.utils.RegionUtils;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Class to handle TrustedPresentationListener registrations in a thread safe manner. This class
 * also takes care of cleaning up listeners when the remote process dies.
 */
public class TrustedPresentationListenerController {

    interface WindowInfosListenerProvider {
        WindowInfosListener get();
    }

    private final WindowInfosListenerProvider mWindowInfosListenerProvider;
    public TrustedPresentationListenerController() {
        mWindowInfosListenerProvider = () -> new WindowInfosListener() {
            @Override
            public void onWindowInfosChanged(InputWindowHandle[] windowHandles,
                    WindowInfosListener.DisplayInfo[] displayInfos) {
                TrustedPresentationListenerController.this.onWindowInfosChanged(windowHandles,
                        displayInfos);
            }
        };
    }

    @VisibleForTesting
    TrustedPresentationListenerController(WindowInfosListenerProvider provider) {
        mWindowInfosListenerProvider = provider;
    }

    // Should only be accessed by the posting to the handler
    class Listeners {
        private final class ListenerDeathRecipient implements IBinder.DeathRecipient {
            IBinder mListenerBinder;
            int mInstances;

            ListenerDeathRecipient(IBinder listenerBinder) {
                mListenerBinder = listenerBinder;
                mInstances = 0;
                try {
                    mListenerBinder.linkToDeath(this, 0);
                } catch (RemoteException ignore) {
                }
            }

            void addInstance() {
                mInstances++;
            }

            // return true if there are no instances alive
            boolean removeInstance() {
                mInstances--;
                if (mInstances > 0) {
                    return false;
                }
                mListenerBinder.unlinkToDeath(this, 0);
                return true;
            }

            public void binderDied() {
                mHandler.post(() -> {
                    mUniqueListeners.remove(mListenerBinder);
                    removeListeners(mListenerBinder, Optional.empty());
                });
            }
        }

        // tracks binder deaths for cleanup
        ArrayMap<IBinder, ListenerDeathRecipient> mUniqueListeners = new ArrayMap<>();
        ArrayMap<IBinder /*window*/, ArrayList<TrustedPresentationInfo>> mWindowToListeners =
                new ArrayMap<>();

        void register(IBinder window, ITrustedPresentationListener listener,
                TrustedPresentationThresholds thresholds, int id) {
            var listenersForWindow = mWindowToListeners.computeIfAbsent(window,
                    iBinder -> new ArrayList<>());
            listenersForWindow.add(new TrustedPresentationInfo(thresholds, id, listener));

            // register death listener
            var listenerBinder = listener.asBinder();
            var deathRecipient = mUniqueListeners.computeIfAbsent(listenerBinder,
                    ListenerDeathRecipient::new);
            deathRecipient.addInstance();
        }

        void unregister(ITrustedPresentationListener trustedPresentationListener, int id) {
            var listenerBinder = trustedPresentationListener.asBinder();
            var deathRecipient = mUniqueListeners.get(listenerBinder);
            if (deathRecipient == null) {
                ProtoLog.e(WM_DEBUG_TPL, "unregister failed, couldn't find"
                        + " deathRecipient for %s with id=%d", trustedPresentationListener, id);
                return;
            }

            if (deathRecipient.removeInstance()) {
                mUniqueListeners.remove(listenerBinder);
            }
            removeListeners(listenerBinder, Optional.of(id));
        }

        boolean isEmpty() {
            return mWindowToListeners.isEmpty();
        }

        ArrayList<TrustedPresentationInfo> get(IBinder windowToken) {
            return mWindowToListeners.get(windowToken);
        }

        private void removeListeners(IBinder listenerBinder, Optional<Integer> id) {
            for (int i = mWindowToListeners.size() - 1; i >= 0; i--) {
                var listeners = mWindowToListeners.valueAt(i);
                for (int j = listeners.size() - 1; j >= 0; j--) {
                    var listener = listeners.get(j);
                    if (listener.mListener.asBinder() == listenerBinder && (id.isEmpty()
                            || listener.mId == id.get())) {
                        listeners.remove(j);
                    }
                }
                if (listeners.isEmpty()) {
                    mWindowToListeners.removeAt(i);
                }
            }
        }
    }

    private final Object mHandlerThreadLock = new Object();
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private VirtualDeviceManagerInternal mVdmInternal;

    private WindowInfosListener mWindowInfosListener;

    Listeners mRegisteredListeners = new Listeners();

    private Pair<InputWindowHandle[], WindowInfosListener.DisplayInfo[]> mLastWindowHandles;

    private void startHandlerThreadIfNeeded() {
        synchronized (mHandlerThreadLock) {
            if (mHandler == null) {
                if (computerControlAccess()) {
                    mVdmInternal = LocalServices.getService(VirtualDeviceManagerInternal.class);
                }
                mHandlerThread = new HandlerThread("WindowInfosListenerForTpl");
                mHandlerThread.start();
                mHandler = new Handler(mHandlerThread.getLooper());
            }
        }
    }

    void registerListener(IBinder window, ITrustedPresentationListener listener,
            TrustedPresentationThresholds thresholds, int id) {
        startHandlerThreadIfNeeded();
        mHandler.post(() -> {
            ProtoLog.d(WM_DEBUG_TPL, "Registering listener=%s with id=%d for window=%s with %s",
                    listener, id, window, thresholds);

            mRegisteredListeners.register(window, listener, thresholds, id);
            registerWindowInfosListener();
            // Update the initial state for the new registered listener
            computeTpl(mLastWindowHandles);
        });
    }

    void unregisterListener(ITrustedPresentationListener listener, int id) {
        startHandlerThreadIfNeeded();
        mHandler.post(() -> {
            ProtoLog.d(WM_DEBUG_TPL, "Unregistering listener=%s with id=%d",
                    listener, id);

            mRegisteredListeners.unregister(listener, id);
            if (mRegisteredListeners.isEmpty()) {
                unregisterWindowInfosListener();
            }
        });
    }

    void dump(PrintWriter pw) {
        final String innerPrefix = "  ";
        pw.println("TrustedPresentationListenerController:");
        pw.println(innerPrefix + "Active unique listeners ("
                + mRegisteredListeners.mUniqueListeners.size() + "):");

        if (mHandler == null) {
            return;
        }

        mHandler.runWithScissors(() -> {
            for (int i = 0; i < mRegisteredListeners.mWindowToListeners.size(); i++) {
                IBinder windowToken = mRegisteredListeners.mWindowToListeners.keyAt(i);
                String windowName = "unknown";
                if (mLastWindowHandles != null && mLastWindowHandles.first != null) {
                    for (int k = 0; k < mLastWindowHandles.first.length; ++k) {
                        InputWindowHandle handle = mLastWindowHandles.first[k];
                        if (handle.getWindowToken() != null
                                && handle.getWindowToken().equals(windowToken)) {
                            windowName = handle.name;
                            break;
                        }
                    }
                }
                pw.print(innerPrefix + "  window=" + windowToken + " name=" + windowName);
                final var listeners = mRegisteredListeners.mWindowToListeners.valueAt(i);
                for (int j = 0; j < listeners.size(); j++) {
                    final var listener = listeners.get(j);
                    pw.print(" listener=" + listener.mListener.asBinder()
                            + " id=" + listener.mId
                            + " thresholds=" + listener.mThresholds);
                    pw.print(" state(current=" + listener.mLastComputedTrustedPresentationState
                            + " reported=" + listener.mLastReportedTrustedPresentationState + ")");
                    pw.println(" alpha=" + listener.mLastAlpha
                            + " fraction=" + listener.mLastFractionRendered);
                }
            }
            pw.println(innerPrefix + "Window Infos:");
            if (mLastWindowHandles != null && mLastWindowHandles.first != null) {
                for (int k = 0; k < mLastWindowHandles.first.length; ++k) {
                    InputWindowHandle handle = mLastWindowHandles.first[k];
                    pw.print(innerPrefix + innerPrefix + "name=" + handle.name);
                    pw.print(" displayId=" + handle.displayId);
                    pw.print(" token=" + handle.getWindowToken());
                    pw.print(" frame=" + handle.frame);
                    pw.print(" alpha=" + handle.alpha);
                    pw.print(" canOcclude=" + handle.canOccludePresentation);
                    pw.print(" type=" + handle.layoutParamsType);
                    pw.println(" flags=" + handle.layoutParamsFlags);
                }
            } else {
                pw.println(innerPrefix + innerPrefix + "none");
            }
            pw.println(innerPrefix + "Display Infos:");
            if (mLastWindowHandles != null && mLastWindowHandles.second != null) {
                for (int k = 0; k < mLastWindowHandles.second.length; ++k) {
                    WindowInfosListener.DisplayInfo handle = mLastWindowHandles.second[k];
                    pw.print(innerPrefix + innerPrefix + "displayId=" + handle.mDisplayId);
                    pw.print(" logicalSize=" + handle.mLogicalSize);
                    pw.println(" transform=" + handle.mTransform);
                }
            } else {
                pw.println(innerPrefix + innerPrefix + "none");
            }
        }, 0 /* timeout */);
    }

    @VisibleForTesting
    void onWindowInfosChanged(InputWindowHandle[] windowHandles,
            WindowInfosListener.DisplayInfo[] displayInfos) {
        mHandler.post(() -> computeTpl(new Pair<>(windowHandles, displayInfos)));
    }

    private void registerWindowInfosListener() {
        if (mWindowInfosListener != null) {
            return;
        }

        mWindowInfosListener = mWindowInfosListenerProvider.get();
        mLastWindowHandles = mWindowInfosListener.register();
    }

    private void unregisterWindowInfosListener() {
        if (mWindowInfosListener == null) {
            return;
        }

        mWindowInfosListener.unregister();
        mWindowInfosListener = null;
        mLastWindowHandles = null;
    }

    private void computeTpl(
            Pair<InputWindowHandle[], WindowInfosListener.DisplayInfo[]> windowHandles) {
        mLastWindowHandles = windowHandles;
        if (mLastWindowHandles == null || mLastWindowHandles.first.length == 0
                || mRegisteredListeners.isEmpty()) {
            return;
        }

        Rect tmpRect = new Rect();
        RectF tmpRectF = new RectF();
        Rect tmpLogicalDisplaySize = new Rect();
        Matrix tmpInverseMatrix = new Matrix();
        float[] tmpMatrix = new float[9];
        SparseArray<Region> coveredRegionsAboveByDisplay = new SparseArray<>();
        long currTimeMs = System.currentTimeMillis();
        ProtoLog.v(WM_DEBUG_TPL, "Checking %d windows", mLastWindowHandles.first.length);

        ArrayMap<ITrustedPresentationListener, Pair<IntArray, IntArray>> listenerUpdates =
                new ArrayMap<>();

        ArrayMap<IBinder, ArrayList<Pair<Float, Float>>> windowStates = new ArrayMap<>();

        for (int i = 0; i < mLastWindowHandles.first.length; ++i) {
            InputWindowHandle windowHandle = mLastWindowHandles.first[i];
            var isInvisible = ((windowHandle.inputConfig & InputConfig.NOT_VISIBLE)
                    == InputConfig.NOT_VISIBLE);
            if (!windowHandle.canOccludePresentation || isInvisible) {
                continue;
            }
            int displayId = INVALID_DISPLAY;
            tmpRectF.set(windowHandle.frame);
            for (int j = 0; j < mLastWindowHandles.second.length; ++j) {
                var displayHandle = mLastWindowHandles.second[j];
                if (displayHandle.mDisplayId == windowHandle.displayId) {
                    // Transform the window frame into display logical space and then
                    // crop by the logical display size
                    displayHandle.mTransform.mapRect(tmpRectF);
                    tmpRectF.round(tmpRect);
                    tmpLogicalDisplaySize.set(0, 0, displayHandle.mLogicalSize.getWidth(),
                            displayHandle.mLogicalSize.getHeight());
                    tmpRect.intersect(tmpLogicalDisplaySize);
                    displayId = displayHandle.mDisplayId;
                    break;
                }
            }

            if (displayId == INVALID_DISPLAY) {
                ProtoLog.v(WM_DEBUG_TPL, "Skipping %s, no associated display %d", windowHandle.name,
                        windowHandle.displayId);
                continue;
            }

            if (computerControlAccess()
                    && mVdmInternal != null
                    && mVdmInternal.isComputerControlDisplay(displayId)) {
                // TODO(b/459548175, b/459545101): Remove this once we have a better way to handle
                // virtual displays and mirrored content. For now, we just skip Computer Control
                // displays and consider only the mirrored hierarchy (if the ComputerControl
                // MirrorView is visible) for computing trusted presentation.
                continue;
            }

            Region coveredRegionsAbove = coveredRegionsAboveByDisplay.get(displayId, new Region());
            IBinder windowToken = windowHandle.getWindowToken();
            var listeners = mRegisteredListeners.get(windowToken);
            if (listeners != null) {
                Region region = new Region();
                region.op(tmpRect, coveredRegionsAbove, Region.Op.DIFFERENCE);
                windowHandle.transform.invert(tmpInverseMatrix);
                tmpInverseMatrix.getValues(tmpMatrix);
                float scaleX = (float) Math.sqrt(tmpMatrix[MSCALE_X] * tmpMatrix[MSCALE_X]
                        + tmpMatrix[MSKEW_X] * tmpMatrix[MSKEW_X]);
                float scaleY = (float) Math.sqrt(tmpMatrix[MSCALE_Y] * tmpMatrix[MSCALE_Y]
                        + tmpMatrix[MSKEW_Y] * tmpMatrix[MSKEW_Y]);

                float fractionRendered = computeFractionRendered(region, new RectF(tmpRect),
                        windowHandle.contentSize,
                        scaleX, scaleY);

                windowStates.computeIfAbsent(windowToken, k -> new ArrayList<>())
                        .add(new Pair<>(fractionRendered, windowHandle.alpha));
            }

            coveredRegionsAbove.op(tmpRect, Region.Op.UNION);
            coveredRegionsAboveByDisplay.put(displayId, coveredRegionsAbove);
            ProtoLog.v(WM_DEBUG_TPL, "coveredRegionsAbove updated with %s frame:%s region:%s",
                    windowHandle.name, tmpRect.toShortString(), coveredRegionsAbove);
        }

        for (int i = 0; i < mRegisteredListeners.mWindowToListeners.size(); i++) {
            IBinder windowToken = mRegisteredListeners.mWindowToListeners.keyAt(i);
            var listeners = mRegisteredListeners.mWindowToListeners.valueAt(i);
            checkIfInThreshold(listeners, listenerUpdates,
                    windowStates.get(windowToken),
                    currTimeMs);
        }

        for (int i = 0; i < listenerUpdates.size(); i++) {
            var updates = listenerUpdates.valueAt(i);
            var listener = listenerUpdates.keyAt(i);
            try {
                listener.onTrustedPresentationChanged(updates.first.toArray(),
                        updates.second.toArray());
            } catch (RemoteException ignore) {
            }
        }
    }

    private void addListenerUpdate(
            ArrayMap<ITrustedPresentationListener, Pair<IntArray, IntArray>> listenerUpdates,
            ITrustedPresentationListener listener, int id, boolean presentationState) {
        var updates = listenerUpdates.get(listener);
        if (updates == null) {
            updates = new Pair<>(new IntArray(), new IntArray());
            listenerUpdates.put(listener, updates);
        }
        if (presentationState) {
            updates.first.add(id);
        } else {
            updates.second.add(id);
        }
    }


    private void checkIfInThreshold(
            ArrayList<TrustedPresentationInfo> listeners,
            ArrayMap<ITrustedPresentationListener, Pair<IntArray, IntArray>> listenerUpdates,
            ArrayList<Pair<Float, Float>> states, long currTimeMs) {
        if (states == null) {
            states = new ArrayList<>();
        }
        for (int i = 0; i < listeners.size(); i++) {
            var trustedPresentationInfo = listeners.get(i);
            boolean newState = false;
            float bestAlpha = 0f;
            float bestFraction = 0f;
            for (Pair<Float, Float> state : states) {
                float fractionRendered = state.first;
                float alpha = state.second;
                boolean meetsThreshold =
                        (alpha >= trustedPresentationInfo.mThresholds.getMinAlpha())
                        && (fractionRendered >= trustedPresentationInfo.mThresholds
                                .getMinFractionRendered());
                if (meetsThreshold) {
                    newState = true;
                    bestAlpha = alpha;
                    bestFraction = fractionRendered;
                    break;
                }
                // Track best non-meeting state for debugging/dump
                if (fractionRendered > bestFraction) {
                    bestFraction = fractionRendered;
                    bestAlpha = alpha;
                }
            }

            trustedPresentationInfo.mLastAlpha = bestAlpha;
            trustedPresentationInfo.mLastFractionRendered = bestFraction;
            boolean lastState = trustedPresentationInfo.mLastComputedTrustedPresentationState;
            trustedPresentationInfo.mLastComputedTrustedPresentationState = newState;

            ProtoLog.v(WM_DEBUG_TPL,
                    "lastState=%s newState=%s alpha=%f minAlpha=%f fractionRendered=%f "
                            + "minFractionRendered=%f",
                    lastState, newState, bestAlpha,
                    trustedPresentationInfo.mThresholds.getMinAlpha(), bestFraction,
                    trustedPresentationInfo.mThresholds.getMinFractionRendered());

            if (lastState && !newState) {
                // We were in the trusted presentation state, but now we left it,
                // emit the callback if needed
                if (trustedPresentationInfo.mLastReportedTrustedPresentationState) {
                    trustedPresentationInfo.mLastReportedTrustedPresentationState = false;
                    addListenerUpdate(listenerUpdates, trustedPresentationInfo.mListener,
                            trustedPresentationInfo.mId, /*presentationState*/ false);
                    ProtoLog.d(WM_DEBUG_TPL, "Adding untrusted state listener=%s with id=%d",
                            trustedPresentationInfo.mListener, trustedPresentationInfo.mId);
                }
                // Reset the timer
                trustedPresentationInfo.mEnteredTrustedPresentationStateTime = -1;
            } else if (!lastState && newState) {
                // We were not in the trusted presentation state, but we entered it, begin the timer
                // and make sure this gets called at least once more!
                trustedPresentationInfo.mEnteredTrustedPresentationStateTime = currTimeMs;
                mHandler.postDelayed(() -> {
                    computeTpl(mLastWindowHandles);
                }, (long) (trustedPresentationInfo.mThresholds
                            .getStabilityRequirementMillis() * 1.5));
            }

            // Has the timer elapsed, but we are still in the state? Emit a callback if needed
            if (!trustedPresentationInfo.mLastReportedTrustedPresentationState && newState && (
                    currTimeMs - trustedPresentationInfo.mEnteredTrustedPresentationStateTime
                            > trustedPresentationInfo.mThresholds
                                        .getStabilityRequirementMillis())) {
                trustedPresentationInfo.mLastReportedTrustedPresentationState = true;
                addListenerUpdate(listenerUpdates, trustedPresentationInfo.mListener,
                        trustedPresentationInfo.mId, /*presentationState*/ true);
                ProtoLog.d(WM_DEBUG_TPL, "Adding trusted state listener=%s with id=%d",
                        trustedPresentationInfo.mListener, trustedPresentationInfo.mId);
            }
        }
    }

    private float computeFractionRendered(Region visibleRegion, RectF screenBounds,
            Size contentSize,
            float sx, float sy) {
        ProtoLog.v(WM_DEBUG_TPL,
                "computeFractionRendered: visibleRegion=%s screenBounds=%s contentSize=%s "
                        + "scale=%f,%f",
                visibleRegion, screenBounds, contentSize, sx, sy);

        if (contentSize.getWidth() == 0 || contentSize.getHeight() == 0) {
            return -1;
        }
        if (screenBounds.width() == 0 || screenBounds.height() == 0) {
            return -1;
        }

        float fractionRendered = Math.min(sx * sy, 1.0f);
        ProtoLog.v(WM_DEBUG_TPL, "fractionRendered scale=%f", fractionRendered);

        float boundsOverSourceW = screenBounds.width() / (float) contentSize.getWidth();
        float boundsOverSourceH = screenBounds.height() / (float) contentSize.getHeight();
        fractionRendered *= boundsOverSourceW * boundsOverSourceH;
        ProtoLog.v(WM_DEBUG_TPL, "fractionRendered boundsOverSource=%f", fractionRendered);
        // Compute the size of all the rects since they may be disconnected.
        float[] visibleSize = new float[1];
        RegionUtils.forEachRect(visibleRegion, rect -> {
            float size = rect.width() * rect.height();
            visibleSize[0] += size;
        });

        fractionRendered *= visibleSize[0] / (screenBounds.width() * screenBounds.height());
        return fractionRendered;
    }

    private static class TrustedPresentationInfo {
        float mLastAlpha = -1;
        float mLastFractionRendered = -1;
        boolean mLastComputedTrustedPresentationState = false;
        boolean mLastReportedTrustedPresentationState = false;
        long mEnteredTrustedPresentationStateTime = -1;
        final TrustedPresentationThresholds mThresholds;

        final ITrustedPresentationListener mListener;
        final int mId;

        private TrustedPresentationInfo(TrustedPresentationThresholds thresholds, int id,
                ITrustedPresentationListener listener) {
            mThresholds = thresholds;
            mId = id;
            mListener = listener;
        }
    }
}
