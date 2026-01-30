/*
 * Copyright (C) 2024 The Android Open Source Project
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

package androidx.window.sidecar;

import static android.hardware.devicestate.DeviceState.PROPERTY_FOLDABLE_HARDWARE_CONFIGURATION_FOLD_IN_HALF_OPEN;

import android.app.Activity;
import android.app.ActivityThread;
import android.hardware.devicestate.DeviceState;
import android.hardware.devicestate.DeviceStateManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.window.extensions.layout.FoldingFeature;
import androidx.window.extensions.layout.WindowLayoutComponent;
import androidx.window.extensions.layout.WindowLayoutInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class SidecarExtensionsImpl implements SidecarInterface {

    private final WindowLayoutComponent mWindowLayoutComponent;
    private SidecarCallback mSidecarCallback;
    private final Map<IBinder, Consumer<WindowLayoutInfo>> mIBinderConsumerMap = new HashMap<>();
    private final DeviceStateManager mDeviceStateManager;
    private final DeviceStateManager.DeviceStateCallback mDeviceStateCallback =
            new DeviceStateCallback();
    private final Handler mMainThread;

    public SidecarExtensionsImpl(WindowLayoutComponent windowLayoutComponent,
            DeviceStateManager deviceStateManager) {
        mWindowLayoutComponent = Objects.requireNonNull(windowLayoutComponent);
        mDeviceStateManager = Objects.requireNonNull(deviceStateManager);
        mMainThread = new Handler(Looper.getMainLooper());
    }

    @Override
    public void setSidecarCallback(@NonNull SidecarCallback callback) {
        mSidecarCallback = callback;
    }

    @NonNull
    @Override
    public SidecarWindowLayoutInfo getWindowLayoutInfo(@NonNull IBinder windowToken) {
        final Activity activity = computeActivity(windowToken);
        if (activity == null) {
            SidecarWindowLayoutInfo sidecarWindowLayoutInfo = new SidecarWindowLayoutInfo();
            sidecarWindowLayoutInfo.displayFeatures = new ArrayList<>();
            return sidecarWindowLayoutInfo;
        }
        final WindowLayoutInfo windowLayoutInfo = mWindowLayoutComponent
                .getCurrentWindowLayoutInfo(activity);
        return computeSidecarWindowLayoutInfo(windowLayoutInfo);
    }

    @Override
    public void onWindowLayoutChangeListenerAdded(@NonNull IBinder windowToken) {
        final Activity activity = computeActivity(windowToken);
        if (activity == null) {
            return;
        }
        final Consumer<WindowLayoutInfo> consumer = new CallbackAdapter(windowToken);
        mIBinderConsumerMap.put(windowToken, consumer);
        mWindowLayoutComponent.addWindowLayoutInfoListener(activity, consumer);
    }

    @Override
    public void onWindowLayoutChangeListenerRemoved(@NonNull IBinder windowToken) {
        final Consumer<WindowLayoutInfo> consumer = mIBinderConsumerMap.remove(windowToken);
        mWindowLayoutComponent.removeWindowLayoutInfoListener(consumer);
    }

    @NonNull
    @Override
    public SidecarDeviceState getDeviceState() {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            SidecarDeviceState state = new SidecarDeviceState();
            state.posture = SidecarDeviceState.POSTURE_OPENED;
            return state;
        }
        final WindowLayoutInfo windowLayoutInfo = mWindowLayoutComponent
                .getCurrentWindowLayoutInfo(activity);
        return computeDeviceState(windowLayoutInfo);
    }

    @Override
    public void onDeviceStateListenersChanged(boolean isEmpty) {
        if (isEmpty) {
            mDeviceStateManager.unregisterCallback(mDeviceStateCallback);
        } else {
            mDeviceStateManager.registerCallback(Runnable::run, mDeviceStateCallback);
        }
    }

    /**
     * Returns a {@link SidecarWindowLayoutInfo} that is translated from {@link WindowLayoutInfo}.
     */
    public static SidecarWindowLayoutInfo computeSidecarWindowLayoutInfo(
            WindowLayoutInfo windowLayoutInfo) {
        final List<SidecarDisplayFeature> displayFeatureList = windowLayoutInfo.getDisplayFeatures()
                .stream()
                .filter((displayFeature -> displayFeature instanceof FoldingFeature))
                .map((displayFeature -> {
                    FoldingFeature feature = (FoldingFeature) displayFeature;
                    int type = feature.getType() == FoldingFeature.TYPE_FOLD
                            ? SidecarDisplayFeature.TYPE_FOLD
                            : SidecarDisplayFeature.TYPE_HINGE;

                    SidecarDisplayFeature sidecarDisplayFeature = new SidecarDisplayFeature();
                    sidecarDisplayFeature.setRect(feature.getBounds());
                    sidecarDisplayFeature.setType(type);
                    return sidecarDisplayFeature;
                }))
                .toList();

        final SidecarWindowLayoutInfo sidecarWindowLayoutInfo = new SidecarWindowLayoutInfo();
        sidecarWindowLayoutInfo.displayFeatures = displayFeatureList;

        return sidecarWindowLayoutInfo;
    }

    /**
     * Returns a {@link SidecarDeviceState} that is extracted from the {@link WindowLayoutInfo}.
     */
    public static SidecarDeviceState computeDeviceState(WindowLayoutInfo windowLayoutInfo) {
        final int posture = windowLayoutInfo.getDisplayFeatures().stream()
                .filter(displayFeature -> displayFeature instanceof FoldingFeature)
                .map(displayFeature ->
                        ((FoldingFeature) displayFeature).getState() == FoldingFeature.STATE_FLAT
                                ? SidecarDeviceState.POSTURE_OPENED
                                : SidecarDeviceState.POSTURE_HALF_OPENED)
                .findFirst().orElse(SidecarDeviceState.POSTURE_OPENED);

        final SidecarDeviceState state = new SidecarDeviceState();
        state.posture = posture;
        return state;
    }

    /**
     * Returns the {@link Activity} associated with the {@link IBinder} token.
     */
    public static Activity computeActivity(@NonNull IBinder token) {
        return ActivityThread.currentActivityThread().getActivity(token);
    }

    /**
     * Returns the last created {@link Activity}.
     */
    private static Activity getCurrentActivity() {
        return ActivityThread.currentActivityThread().getLastCreatedActivity();
    }

    /**
     * A class to relay new values of {@link WindowLayoutInfo} to
     * {@link androidx.window.sidecar.SidecarInterface.SidecarCallback}.
     * @see WindowLayoutComponent#addWindowLayoutInfoListener(Activity, Consumer)
     */
    final class CallbackAdapter implements Consumer<WindowLayoutInfo> {
        private final IBinder mWindowToken;


        CallbackAdapter(IBinder windowToken) {
            mWindowToken = Objects.requireNonNull(windowToken);
        }

        @Override
        public void accept(WindowLayoutInfo windowLayoutInfo) {
            final SidecarCallback callback = mSidecarCallback;
            if (callback == null) {
                return;
            }
            final SidecarWindowLayoutInfo sidecarWindowLayoutInfo =
                    SidecarExtensionsImpl.computeSidecarWindowLayoutInfo(windowLayoutInfo);
            mMainThread.post(() -> mSidecarCallback
                    .onWindowLayoutChanged(mWindowToken, sidecarWindowLayoutInfo));
        }
    }

    /**
     * A class to translate the {@link DeviceState} into {@link SidecarDeviceState} and relays them
     * to the {@link androidx.window.sidecar.SidecarInterface.SidecarCallback}.
     */
    final class DeviceStateCallback implements DeviceStateManager.DeviceStateCallback {

        @Override
        public void onDeviceStateChanged(@NonNull DeviceState state) {
            final SidecarCallback callback = mSidecarCallback;
            if (callback == null) {
                return;
            }
            int posture = SidecarDeviceState.POSTURE_OPENED;
            if (state.hasProperty(PROPERTY_FOLDABLE_HARDWARE_CONFIGURATION_FOLD_IN_HALF_OPEN)) {
                posture = SidecarDeviceState.POSTURE_HALF_OPENED;
            }

            final SidecarDeviceState deviceState = new SidecarDeviceState();
            deviceState.posture = posture;
            mMainThread.post(() -> mSidecarCallback.onDeviceStateChanged(deviceState));
        }
    }
}
