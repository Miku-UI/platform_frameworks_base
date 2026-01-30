/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.settingslib.bluetooth.devicesettings;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/** A data class representing a device setting icon. */
public class DeviceSettingIcon implements Parcelable {
    @DeviceSettingDefaultIcon private final int mDefaultIcon;
    private final Bitmap mCustomizedIcon;
    private final Bundle mExtras;

    DeviceSettingIcon(
            @DeviceSettingDefaultIcon int defaultIcon,
            @Nullable Bitmap customizedIcon,
            @NonNull Bundle extras) {
        mDefaultIcon = defaultIcon;
        mCustomizedIcon = customizedIcon;
        mExtras = extras;
    }

    public static final DeviceSettingIcon NO_ICON = new DeviceSettingIcon(
            DeviceSettingDefaultIcon.DEVICE_SETTING_DEFAULT_ICON_NONE, null, Bundle.EMPTY);

    /** Reads a {@link DeviceSettingIcon} from {@link Parcel}. */
    @NonNull
    public static DeviceSettingIcon readFromParcel(@NonNull Parcel in) {
        int defaultIcon = in.readInt();
        Bitmap customizedIcon = in.readParcelable(Bitmap.class.getClassLoader());
        Bundle extra = in.readBundle(Bundle.class.getClassLoader());
        return new DeviceSettingIcon(defaultIcon, customizedIcon, extra);
    }

    public static final Creator<DeviceSettingIcon> CREATOR =
            new Creator<>() {
                @Override
                @NonNull
                public DeviceSettingIcon createFromParcel(@NonNull Parcel in) {
                    return readFromParcel(in);
                }

                @Override
                @NonNull
                public DeviceSettingIcon[] newArray(int size) {
                    return new DeviceSettingIcon[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    /** Writes the instance to {@link Parcel}. */
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mDefaultIcon);
        dest.writeParcelable(mCustomizedIcon, flags);
        dest.writeBundle(mExtras);
    }

    /** Builder class for {@link DeviceSettingIcon}. */
    public static final class Builder {
        @DeviceSettingDefaultIcon
        private int mDefaultIcon = DeviceSettingDefaultIcon.DEVICE_SETTING_DEFAULT_ICON_NONE;
        private Bitmap mCustomizedIcon = null;
        private Bundle mExtras = Bundle.EMPTY;

        /**
         * Sets the default icon index, as defined by IntDef {@link DeviceSettingDefaultIcon}.
         *
         * @return Returns the Builder object.
         */
        @NonNull
        public DeviceSettingIcon.Builder setDefaultIcon(@DeviceSettingDefaultIcon int defaultIcon) {
            mDefaultIcon = defaultIcon;
            return this;
        }

        /**
         * Sets the customized icon.
         *
         * @return Returns the Builder object.
         */
        @NonNull
        public DeviceSettingIcon.Builder setCustomizedIcon(Bitmap customizedIcon) {
            mCustomizedIcon = customizedIcon;
            return this;
        }

        /**
         * Sets the extras bundle.
         *
         * @return Returns the Builder object.
         */
        @NonNull
        public DeviceSettingIcon.Builder setExtras(@NonNull Bundle extras) {
            mExtras = extras;
            return this;
        }

        /** Build the object. */
        @NonNull
        public DeviceSettingIcon build() {
            return new DeviceSettingIcon(mDefaultIcon, mCustomizedIcon, mExtras);
        }
    }

    /**
     * Gets the default icon index, as defined by IntDef {@link DeviceSettingDefaultIcon}.
     *
     * @return the setting ID.
     */
    @DeviceSettingDefaultIcon
    public int getDefaultIcon() {
        return mDefaultIcon;
    }

    /**
     * Gets the customized icon.
     *
     * @return the customized icon.
     */
    @Nullable
    public Bitmap getCustomizedIcon() {
        return mCustomizedIcon;
    }

    /**
     * Gets the extras Bundle.
     *
     * @return Returns a Bundle object.
     */
    @NonNull
    public Bundle getExtras() {
        return mExtras;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (!(obj instanceof DeviceSettingIcon other)) {
            return false;
        }
        return mDefaultIcon == other.mDefaultIcon
                && Objects.equals(mCustomizedIcon, other.mCustomizedIcon);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mDefaultIcon, mCustomizedIcon);
    }
}
