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

import java.util.Objects;

/** A data class representing a button in {@link BannerPreference}. */
public class ButtonInfo implements Parcelable {
    private final String mLabel;
    private final DeviceSettingAction mAction;
    private final Bundle mExtras;

    ButtonInfo(@NonNull String label, @NonNull DeviceSettingAction action, @NonNull Bundle extras) {
        validate(label, action);
        mLabel = label;
        mAction = action;
        mExtras = extras;
    }

    private static void validate(String label, DeviceSettingAction action) {
        if (Objects.isNull(label)) {
            throw new IllegalArgumentException("Label must be set");
        }
        if (Objects.isNull(action)) {
            throw new IllegalArgumentException("Action must be set");
        }
    }

    /** Read a {@link ButtonInfo} instance from {@link Parcel}. */
    @NonNull
    public static ButtonInfo readFromParcel(@NonNull Parcel in) {
        String label = in.readString();
        DeviceSettingAction action = DeviceSettingAction.readFromParcel(in);
        Bundle extras = in.readBundle(Bundle.class.getClassLoader());
        return new ButtonInfo(label, action, extras);
    }

    public static final Creator<ButtonInfo> CREATOR =
            new Creator<>() {
                @Override
                @NonNull
                public ButtonInfo createFromParcel(@NonNull Parcel in) {
                    return readFromParcel(in);
                }

                @Override
                @NonNull
                public ButtonInfo[] newArray(int size) {
                    return new ButtonInfo[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mLabel);
        mAction.writeToParcel(dest, flags);
        dest.writeBundle(mExtras);
    }

    /** Builder class for {@link ButtonInfo}. */
    public static final class Builder {
        private String mLabel;
        private DeviceSettingAction mAction = DeviceSettingAction.EMPTY_ACTION;
        private Bundle mExtras = Bundle.EMPTY;

        /**
         * Sets the label of the button.
         *
         * @param label The label of the button.
         * @return Returns the Builder object.
         */
        @NonNull
        public Builder setLabel(@NonNull String label) {
            mLabel = label;
            return this;
        }

        /**
         * Sets the action of the button.
         *
         * @param action The action of the button.
         * @return Returns the Builder object.
         */
        @NonNull
        public Builder setAction(@NonNull DeviceSettingAction action) {
            mAction = action;
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
         * Builds the {@link ButtonInfo} object.
         *
         * @return Returns the built {@link ButtonInfo} object.
         */
        @NonNull
        public ButtonInfo build() {
            return new ButtonInfo(mLabel, mAction, mExtras);
        }
    }

    /**
     * Gets the label of the button.
     *
     * @return the label to be shown under the button
     */
    @NonNull
    public String getLabel() {
        return mLabel;
    }

    /**
     * Gets the action of the button.
     *
     * @return the action in button
     */
    @NonNull
    public DeviceSettingAction getAction() {
        return mAction;
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
