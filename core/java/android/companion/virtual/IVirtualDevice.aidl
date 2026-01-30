/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.companion.virtual;

import android.app.PendingIntent;
import android.companion.virtual.ActivityPolicyExemption;
import android.companion.virtual.IVirtualDeviceActivityListener;
import android.companion.virtual.IVirtualDeviceIntentInterceptor;
import android.companion.virtual.IVirtualDeviceSoundEffectListener;
import android.companion.virtual.audio.IAudioConfigChangedCallback;
import android.companion.virtual.audio.IAudioRoutingCallback;
import android.companion.virtual.sensor.VirtualSensor;
import android.companion.virtual.sensor.VirtualSensorAdditionalInfo;
import android.companion.virtual.sensor.VirtualSensorConfig;
import android.companion.virtual.sensor.VirtualSensorEvent;
import android.companion.virtual.camera.VirtualCameraConfig;
import android.content.ComponentName;
import android.content.IntentFilter;
import android.graphics.Point;
import android.hardware.display.IVirtualDisplayCallback;
import android.hardware.display.VirtualDisplayConfig;
import android.hardware.input.IVirtualInputDevice;
import android.hardware.input.VirtualDpadConfig;
import android.hardware.input.VirtualKeyboardConfig;
import android.hardware.input.VirtualMouseConfig;
import android.hardware.input.VirtualRotaryEncoderConfig;
import android.hardware.input.VirtualStylusConfig;
import android.hardware.input.VirtualTouchscreenConfig;
import android.hardware.input.VirtualNavigationTouchpadConfig;
import android.os.ResultReceiver;

/**
 * Interface for a virtual device for communication between the system server and the process of
 * the owner of the virtual device.
 *
 * @hide
 */
interface IVirtualDevice {

    /**
     * Returns the CDM association ID of this virtual device.
     *
     * @see AssociationInfo#getId()
     */
    int getAssociationId();

    /**
     * Returns the unique ID of this virtual device.
     */
    int getDeviceId();

    /**
     * Returns the persistent ID of this virtual device.
     */
    String getPersistentDeviceId();

    /**
     * Returns the IDs of all virtual displays of this device.
     */
    int[] getDisplayIds();

    /**
     * Returns the device policy for the given policy type.
     */
    int getDevicePolicy(int policyType);

    /**
     * Returns whether the device has a valid microphone.
     */
    boolean hasCustomAudioInputSupport();

    /**
     * Returns whether this device is allowed to create mirror displays.
     */
    boolean canCreateMirrorDisplays();

    /*
    /*
     * Turns off all trusted non-mirror displays of the virtual device.
     */
    void goToSleep();

    /**
     * Turns on all trusted non-mirror displays of the virtual device.
     */
    void wakeUp();

    /**
     * Closes the virtual device and frees all associated resources.
     */
    void close();

    /**
     * Specifies a policy for this virtual device.
     */
    void setDevicePolicy(int policyType, int devicePolicy);

    /**
     * Adds an exemption to the default activity launch policy.
     */
    void addActivityPolicyExemption(in ActivityPolicyExemption exemption);

    /**
     * Removes an exemption to the default activity launch policy.
     */
    void removeActivityPolicyExemption(in ActivityPolicyExemption exemption);

    /**
     * Specifies a policy for this virtual device on the given display.
     */
    void setDevicePolicyForDisplay(int displayId, int policyType, int devicePolicy);

    /**
     * Notifies that an audio session being started.
     */
    void onAudioSessionStarting(int displayId, IAudioRoutingCallback routingCallback,
            IAudioConfigChangedCallback configChangedCallback);

    /**
     * Notifies that an audio session has ended.
     */
    void onAudioSessionEnded();

    /**
     * Creates a virtual display and registers it with the display framework.
     */
    int createVirtualDisplay(in VirtualDisplayConfig virtualDisplayConfig,
            in IVirtualDisplayCallback callback);

    /**
     * Creates a new dpad and registers it with the input framework with the given token.
     */
    IVirtualInputDevice createVirtualDpad(in VirtualDpadConfig config, IBinder token);

    /**
     * Creates a new keyboard and registers it with the input framework with the given token.
     */
    IVirtualInputDevice createVirtualKeyboard(in VirtualKeyboardConfig config, IBinder token);

    /**
     * Creates a new mouse and registers it with the input framework with the given token.
     */
    IVirtualInputDevice createVirtualMouse(in VirtualMouseConfig config, IBinder token);

    /**
     * Creates a new touchscreen and registers it with the input framework with the given token.
     */
    IVirtualInputDevice createVirtualTouchscreen(in VirtualTouchscreenConfig config, IBinder token);

    /**
     * Creates a new navigation touchpad and registers it with the input framework with the given
     * token.
     */
    IVirtualInputDevice createVirtualNavigationTouchpad(in VirtualNavigationTouchpadConfig config,
            IBinder token);

    /**
     * Creates a new stylus and registers it with the input framework with the given token.
     */
    IVirtualInputDevice createVirtualStylus(in VirtualStylusConfig config, IBinder token);

    /**
     * Creates a new rotary encoder and registers it with the input framework with the given token.
     */
    IVirtualInputDevice createVirtualRotaryEncoder(in VirtualRotaryEncoderConfig config,
            IBinder token);

    /**
     * Returns all virtual sensors created for this device.
     */
    List<VirtualSensor> getVirtualSensorList();

    /**
     * Sends an event to the virtual sensor corresponding to the given token.
     */
    boolean sendSensorEvent(IBinder token, in VirtualSensorEvent event);

    /**
     * Sends additional information about the virtual sensor corresponding to the given token.
     */
    boolean sendSensorAdditionalInfo(IBinder token, in VirtualSensorAdditionalInfo info);

    /**
     * Launches a pending intent on the given display that is owned by this virtual device.
     */
    void launchPendingIntent(int displayId, in PendingIntent pendingIntent,
            in ResultReceiver resultReceiver);

    /** Sets whether to show or hide the cursor while this virtual device is active. */
    void setShowPointerIcon(boolean showPointerIcon);

    /** Sets an IME policy for the given display. */
    void setDisplayImePolicy(int displayId, int policy);

    /** Sets the UI mode for the given display. */
    void setDisplayUiMode(int displayId, int uiMode);

    /**
     * Registers an intent interceptor that will intercept an intent attempting to launch
     * when matching the provided IntentFilter and calls the callback with the intercepted
     * intent.
     */
    void registerIntentInterceptor(in IVirtualDeviceIntentInterceptor intentInterceptor,
            in IntentFilter filter);

    /**
     * Unregisters a previously registered intent interceptor.
     */
    void unregisterIntentInterceptor(in IVirtualDeviceIntentInterceptor intentInterceptor);

    /**
     * Creates a new virtual camera and registers it with the virtual camera service.
     */
    void registerVirtualCamera(in VirtualCameraConfig camera);

    /**
     * Destroys the virtual camera with given config and unregisters it from the virtual camera
     * service.
     */
    void unregisterVirtualCamera(in VirtualCameraConfig camera);

    /**
     * Returns the id of the virtual camera with given config.
     */
    String getVirtualCameraId(in VirtualCameraConfig camera);

    /**
     * Setter for listeners that live in the client process, namely in
     * {@link android.companion.virtual.VirtualDeviceInternal}.
     *
     * This is needed for virtual devices that are created by the system, as the VirtualDeviceImpl
     * object is created before the returned VirtualDeviceInternal one.
     */
    void setListeners(in IVirtualDeviceActivityListener activityListener,
            in IVirtualDeviceSoundEffectListener soundEffectListener);
}
