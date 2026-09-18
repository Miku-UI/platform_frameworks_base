/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_CAMERA;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_SENSORS;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.companion.virtualdevice.flags.Flags;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Details of a particular virtual device.
 *
 * <p>Read-only device representation exposing the properties of an existing virtual device.
 *
 * @see VirtualDeviceManager#registerVirtualDeviceListener
 */
public final class VirtualDevice implements Parcelable {

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "DEVICE_PROFILE_", value = {
            DEVICE_PROFILE_UNKNOWN,
            DEVICE_PROFILE_SHELL,
            DEVICE_PROFILE_COMPUTER_CONTROL,
            DEVICE_PROFILE_AUTOMOTIVE_PROJECTION,
            DEVICE_PROFILE_APP_STREAMING,
            DEVICE_PROFILE_NEARBY_DEVICE_STREAMING,
            DEVICE_PROFILE_VIRTUAL_DEVICE})
    @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
    public @interface DeviceProfile {}

    /**
     * An unknown virtual device profile, indicating an error.
     */
    @FlaggedApi(Flags.FLAG_PUBLIC_DEVICE_PROFILE)
    public static final int DEVICE_PROFILE_UNKNOWN = -1;

    /**
     * A virtual device that has been created by Shell, typically for development / testing.
     */
    @FlaggedApi(Flags.FLAG_PUBLIC_DEVICE_PROFILE)
    public static final int DEVICE_PROFILE_SHELL = 0;

    /**
     * A virtual device that is used for app automation.
     */
    // TODO(b/493126008): Link to some public doc once available.
    @FlaggedApi(Flags.FLAG_PUBLIC_DEVICE_PROFILE)
    public static final int DEVICE_PROFILE_COMPUTER_CONTROL = 1;

    /**
     * @see android.companion.AssociationRequest#DEVICE_PROFILE_AUTOMOTIVE_PROJECTION
     */
    @FlaggedApi(Flags.FLAG_PUBLIC_DEVICE_PROFILE)
    public static final int DEVICE_PROFILE_AUTOMOTIVE_PROJECTION = 101;
    /**
     * @see android.companion.AssociationRequest#DEVICE_PROFILE_APP_STREAMING
     */
    @FlaggedApi(Flags.FLAG_PUBLIC_DEVICE_PROFILE)
    public static final int DEVICE_PROFILE_APP_STREAMING = 102;
    /**
     * @see android.companion.AssociationRequest#DEVICE_PROFILE_NEARBY_DEVICE_STREAMING
     */
    @FlaggedApi(Flags.FLAG_PUBLIC_DEVICE_PROFILE)
    public static final int DEVICE_PROFILE_NEARBY_DEVICE_STREAMING = 103;
    /**
     * @see android.companion.AssociationRequest#DEVICE_PROFILE_VIRTUAL_DEVICE
     */
    @FlaggedApi(Flags.FLAG_ENABLE_LIMITED_VDM_ROLE)
    public static final int DEVICE_PROFILE_VIRTUAL_DEVICE = 104;

    private final @NonNull IVirtualDevice mVirtualDevice;
    private final int mId;
    private final @DeviceProfile int mProfile;
    private final @Nullable String mPersistentId;
    private final @Nullable String mName;
    private final @Nullable CharSequence mDisplayName;

    /**
     * Creates a new instance of {@link VirtualDevice}. Only to be used by the
     * VirtualDeviceManagerService.
     *
     * @hide
     */
    public VirtualDevice(@NonNull IVirtualDevice virtualDevice, int id, @DeviceProfile int profile,
            @Nullable String persistentId, @Nullable String name,
            @Nullable CharSequence displayName) {
        if (id <= Context.DEVICE_ID_DEFAULT) {
            throw new IllegalArgumentException("VirtualDevice ID must be greater than "
                    + Context.DEVICE_ID_DEFAULT);
        }
        mVirtualDevice = virtualDevice;
        mId = id;
        mProfile = profile;
        mPersistentId = persistentId;
        mName = name;
        mDisplayName = displayName;
    }

    private VirtualDevice(@NonNull Parcel parcel) {
        mVirtualDevice = IVirtualDevice.Stub.asInterface(parcel.readStrongBinder());
        mId = parcel.readInt();
        mProfile = parcel.readInt();
        mPersistentId = parcel.readString8();
        mName = parcel.readString8();
        mDisplayName = parcel.readCharSequence();
    }

    /**
     * Returns the unique ID of the virtual device.
     *
     * <p>This identifier corresponds to {@link Context#getDeviceId()} and can be used to access
     * device-specific system capabilities.
     *
     * <p class="note">This identifier is ephemeral and should not be used for persisting any data
     * per device.
     *
     * @see Context#createDeviceContext
     * @see #getPersistentDeviceId()
     */
    public int getDeviceId() {
        return mId;
    }

    /**
     * Returns the persistent identifier of this virtual device, if any.
     *
     * <p> If there is no stable identifier for this virtual device, then this returns {@code null}.

     * <p>This identifier may correspond to a physical device. In that case it remains valid for as
     * long as that physical device is associated with the host device and may be used to persist
     * data per device.
     *
     * <p class="note">This identifier may not be unique across virtual devices, in case there are
     * more than one virtual devices corresponding to the same physical device.
     */
    public @Nullable String getPersistentDeviceId() {
        return mPersistentId;
    }

    /**
     * Returns the device profile of the virtual device.
     *
     * <p>The device profile indicates the intended use and capabilities of the device.</p>
     */
    @FlaggedApi(Flags.FLAG_PUBLIC_DEVICE_PROFILE)
    @DeviceProfile
    public int getDeviceProfile() {
        return mProfile;
    }

    /**
     * Returns the name of the virtual device (optionally) provided during its creation.
     */
    public @Nullable String getName() {
        return mName;
    }

    /**
     * Returns the human-readable name of the virtual device, if defined, which is suitable to be
     * shown in UI.
     */
    public @Nullable CharSequence getDisplayName() {
        return mDisplayName;
    }

    /**
     * Returns the IDs of all virtual displays that belong to this device, if any.
     *
     * <p>The actual {@link android.view.Display} objects can be obtained by passing the returned
     * IDs to {@link android.hardware.display.DisplayManager#getDisplay(int)}.</p>
     */
    public @NonNull int[] getDisplayIds() {
        try {
            return mVirtualDevice.getDisplayIds();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns whether this device may have custom sensors.
     *
     * <p>Returning {@code true} does not necessarily mean that this device has sensors, it only
     * means that a {@link android.hardware.SensorManager} instance created from a {@link Context}
     * associated with this device will return this device's sensors, if any.</p>
     *
     * @see Context#getDeviceId()
     * @see Context#createDeviceContext(int)
     */
    public boolean hasCustomSensorSupport() {
        try {
            return mVirtualDevice.getDevicePolicy(POLICY_TYPE_SENSORS) == DEVICE_POLICY_CUSTOM;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns whether this device may have custom audio input device.
     *
     * @hide
     */
    @SystemApi
    public boolean hasCustomAudioInputSupport() {
        try {
            return mVirtualDevice.hasCustomAudioInputSupport();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns whether this device may have custom cameras.
     *
     * <p>Returning {@code true} does not necessarily mean that this device has cameras, it only
     * means that a {@link android.hardware.camera2.CameraManager} instance created from a
     * {@link Context} associated with this device will return this device's cameras, if any.</p>
     *
     * @see Context#getDeviceId()
     * @see Context#createDeviceContext(int)
     *
     * @hide
     */
    @SystemApi
    public boolean hasCustomCameraSupport() {
        try {
            return mVirtualDevice.getDevicePolicy(POLICY_TYPE_CAMERA) == DEVICE_POLICY_CUSTOM;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeStrongBinder(mVirtualDevice.asBinder());
        dest.writeInt(mId);
        dest.writeInt(mProfile);
        dest.writeString8(mPersistentId);
        dest.writeString8(mName);
        dest.writeCharSequence(mDisplayName);
    }

    @Override
    @NonNull
    public String toString() {
        return "VirtualDevice("
                + " mId=" + mId
                + " mProfile=" + mProfile
                + " mPersistentId=" + mPersistentId
                + " mName=" + mName
                + " mDisplayName=" + mDisplayName
                + ")";
    }

    @NonNull
    public static final Parcelable.Creator<VirtualDevice> CREATOR =
            new Parcelable.Creator<VirtualDevice>() {
                public VirtualDevice createFromParcel(Parcel in) {
                    return new VirtualDevice(in);
                }

                public VirtualDevice[] newArray(int size) {
                    return new VirtualDevice[size];
                }
            };
}
