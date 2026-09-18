/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.annotation.NonNull;
import android.companion.DeviceId;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * DeviceId for a companion device associated via {@link android.companion.CompanionDeviceManager}.
 *
 * @hide
 */
public class CompanionDeviceId implements Parcelable {
    private final DeviceId mDeviceId;

    public CompanionDeviceId(@NonNull DeviceId deviceId) {
        mDeviceId = deviceId;
    }

    private CompanionDeviceId(@NonNull Parcel in) {
        mDeviceId = in.readTypedObject(DeviceId.CREATOR);
    }

    /**
     * @return the {@link DeviceId} for the companion device.
     */
    @NonNull
    public DeviceId getDeviceId() {
        return mDeviceId;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeTypedObject(mDeviceId, flags);
    }

    @NonNull
    public static final Parcelable.Creator<CompanionDeviceId> CREATOR =
            new Parcelable.Creator<CompanionDeviceId>() {
                @Override
                public CompanionDeviceId createFromParcel(Parcel in) {
                    return new CompanionDeviceId(in);
                }

                @Override
                public CompanionDeviceId[] newArray(int size) {
                    return new CompanionDeviceId[size];
                }
            };
}
