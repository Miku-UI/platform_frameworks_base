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

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/** A data class representing a banner preference. */
public class BannerPreference extends DeviceSettingPreference implements Parcelable {
    private final String mTitle;
    private final String mMessage;
    private final DeviceSettingIcon mIcon;
    private final ButtonInfo mPositiveButtonInfo;
    private final ButtonInfo mNegativeButtonInfo;
    private final Bundle mExtras;

    BannerPreference(
            @NonNull String title,
            @NonNull String message,
            @Nullable DeviceSettingIcon icon,
            @Nullable ButtonInfo positiveButtonInfo,
            @Nullable ButtonInfo negativeButtonInfo,
            Bundle extras) {
        super(DeviceSettingType.DEVICE_SETTING_TYPE_BANNER);
        validate(title, message);
        mTitle = title;
        mMessage = message;
        mIcon = Objects.requireNonNullElseGet(icon, () -> DeviceSettingIcon.NO_ICON);
        mPositiveButtonInfo = positiveButtonInfo;
        mNegativeButtonInfo = negativeButtonInfo;
        mExtras = extras;
    }

    private static void validate(String title, String message) {
        if (Objects.isNull(title)) {
            throw new IllegalArgumentException("Title must be set");
        }
        if (Objects.isNull(message)) {
            throw new IllegalArgumentException("Message must be set");
        }
    }

    /** Read a {@link BannerPreference} from {@link Parcel}. */
    @NonNull
    public static BannerPreference readFromParcel(@NonNull Parcel in) {
        String title = in.readString();
        String message = in.readString();
        DeviceSettingIcon icon = DeviceSettingIcon.readFromParcel(in);
        ButtonInfo positiveButtonInfo = in.readParcelable(ButtonInfo.class.getClassLoader());
        ButtonInfo negativeButtonInfo = in.readParcelable(ButtonInfo.class.getClassLoader());
        Bundle extras = in.readBundle(Bundle.class.getClassLoader());
        return new BannerPreference(
                title,
                message,
                icon,
                positiveButtonInfo,
                negativeButtonInfo,
                extras);
    }

    public static final Creator<BannerPreference> CREATOR =
            new Creator<>() {
                @Override
                @NonNull
                public BannerPreference createFromParcel(@NonNull Parcel in) {
                    in.readInt();
                    return readFromParcel(in);
                }

                @Override
                @NonNull
                public BannerPreference[] newArray(int size) {
                    return new BannerPreference[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(mTitle);
        dest.writeString(mMessage);
        mIcon.writeToParcel(dest, flags);
        dest.writeParcelable(mPositiveButtonInfo, flags);
        dest.writeParcelable(mNegativeButtonInfo, flags);
        dest.writeBundle(mExtras);
    }

    /** Builder class for {@link BannerPreference}. */
    public static final class Builder {
        private String mTitle;
        private String mMessage;
        private DeviceSettingIcon mIcon;
        private ButtonInfo mPositiveButtonInfo;
        private ButtonInfo mNegativeButtonInfo;
        private Bundle mExtras = Bundle.EMPTY;

        /**
         * Sets the title of the preference.
         *
         * @param title The title of the preference.
         * @return Returns the Builder object.
         */
        @NonNull
        public Builder setTitle(@NonNull String title) {
            mTitle = title;
            return this;
        }

        /**
         * Sets the message of the preference.
         *
         * @param message The title of the preference.
         * @return Returns the Builder object.
         */
        @NonNull
        public Builder setMessage(@NonNull String message) {
            mMessage = message;
            return this;
        }

        /**
         * Sets the icon of the preference.
         *
         * @param icon The icon.
         * @return Returns the Builder object.
         */
        @NonNull
        public Builder setIcon(DeviceSettingIcon icon) {
            mIcon = icon;
            return this;
        }

        /**
         * Sets the positive button in the preference.
         *
         * @param buttonInfo The button to set.
         * @return Returns the Builder object.
         */
        @NonNull
        public Builder setPositiveButtonInfo(@NonNull ButtonInfo buttonInfo) {
            mPositiveButtonInfo = buttonInfo;
            return this;
        }

        /**
         * Sets the negative button in the preference.
         *
         * @param buttonInfo The button to set.
         * @return Returns the Builder object.
         */
        @NonNull
        public Builder setNegativeButtonInfo(@NonNull ButtonInfo buttonInfo) {
            mNegativeButtonInfo = buttonInfo;
            return this;
        }

        /**
         * Sets the extras bundle.
         *
         * @return Returns the Builder object.
         */
        @NonNull
        public Builder setExtras(@NonNull Bundle extras) {
            mExtras = extras;
            return this;
        }

        /**
         * Builds the {@link BannerPreference} object.
         *
         * @return Returns the built {@link BannerPreference} object.
         */
        @NonNull
        public BannerPreference build() {
            return new BannerPreference(
                    mTitle,
                    mMessage,
                    mIcon,
                    mPositiveButtonInfo,
                    mNegativeButtonInfo,
                    mExtras);
        }
    }

    /**
     * Gets the title of the preference.
     *
     * @return The title.
     */
    @NonNull
    public String getTitle() {
        return mTitle;
    }

    /**
     * Gets the message of the preference.
     *
     * @return The message.
     */
    @NonNull
    public String getMessage() {
        return mMessage;
    }

    /**
     * Gets the icon of the {@link BannerPreference}.
     *
     * @return Returns the index of the icon.
     */
    public DeviceSettingIcon getIcon() {
        return mIcon;
    }

    /**
     * Gets the positive button in the preference.
     *
     * @return the positive button.
     */
    @Nullable
    public ButtonInfo getPositiveButtonInfo() {
        return mPositiveButtonInfo;
    }

    /**
     * Gets the negative button in the preference.
     *
     * @return the negative button.
     */
    @Nullable
    public ButtonInfo getNegativeButtonInfo() {
        return mNegativeButtonInfo;
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
}
