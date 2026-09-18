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

package android.content.theming;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.Size;
import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents the core information of a user's theme, including the seed colors,
 * style, and contrast level.
 *
 * @hide
 */
@FlaggedApi(android.server.Flags.FLAG_ENABLE_THEME_SERVICE)
public final class ThemeInfo implements Parcelable {
    @Nullable
    @Size(min = 1)
    public final List<Color> seedColors;
    @Nullable
    @ThemeStyle.Type
    public final Integer style;
    @Nullable
    public final Float contrast;
    @Nullable
    public final String specVersion;
    @Nullable
    public final String platform;

    private ThemeInfo(@Nullable List<Color> seedColors, @Nullable Integer style,
            @Nullable Float contrast) {
        this.seedColors = seedColors == null ? null : Collections.unmodifiableList(seedColors);
        this.style = style;
        this.contrast = contrast;
        this.specVersion = null;
        this.platform = null;
    }

    /**
     * @hide
     */
    @VisibleForTesting
    public ThemeInfo(@NonNull List<Color> seedColors, @NonNull Integer style,
            @NonNull Float contrast, @NonNull String specVersion, @NonNull String platform) {
        this.seedColors = Collections.unmodifiableList(new ArrayList<>(seedColors));
        this.style = style;
        this.contrast = contrast;
        this.specVersion = specVersion;
        this.platform = platform;
    }

    private ThemeInfo(Parcel in) {
        int size = in.readInt();
        if (size < 0) {
            this.seedColors = null;
        } else {
            List<Color> colors = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                colors.add(Color.valueOf(in.readInt()));
            }
            this.seedColors = Collections.unmodifiableList(colors);
        }
        this.style = (Integer) in.readValue(Integer.class.getClassLoader());
        this.contrast = (Float) in.readValue(Float.class.getClassLoader());
        this.specVersion = in.readString8();
        this.platform = in.readString8();
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        if (seedColors == null) {
            dest.writeInt(-1);
        } else {
            dest.writeInt(seedColors.size());
            for (Color color : seedColors) {
                dest.writeInt(color.toArgb());
            }
        }
        dest.writeValue(style);
        dest.writeValue(contrast);
        dest.writeString8(specVersion);
        dest.writeString8(platform);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final @NonNull Parcelable.Creator<ThemeInfo> CREATOR =
            new Parcelable.Creator<ThemeInfo>() {
                @Override
                public ThemeInfo createFromParcel(Parcel in) {
                    return new ThemeInfo(in);
                }

                @Override
                public ThemeInfo[] newArray(int size) {
                    return new ThemeInfo[size];
                }
            };

    /**
     * A builder for creating {@link ThemeInfo} instances.  Any missing parameter indicates that
     * the current system value for that attribute should be used.
     */
    public static class Builder {
        private List<Color> mSeedColors;
        private Integer mStyle;
        private Float mContrast;

        public Builder() {
        }

        /**
         * Sets the seed colors for the theme.
         */
        public Builder setSeedColors(@Nullable List<Color> seedColors) {
            mSeedColors = seedColors == null ? null : new ArrayList<>(seedColors);
            return this;
        }

        /**
         * Sets the seed colors for the theme.
         */
        public Builder setSeedColors(@Nullable Color... seedColors) {
            mSeedColors = seedColors == null ? null : List.of(seedColors);
            return this;
        }

        /**
         * Sets the theme style.
         */
        public Builder setStyle(@Nullable Integer style) {
            mStyle = style;
            return this;
        }

        /**
         * Sets the theme contrast.
         */
        public Builder setContrast(@Nullable Float contrast) {
            mContrast = contrast;
            return this;
        }

        /**
         * Builds the {@link ThemeInfo} instance.
         */
        public ThemeInfo build() {
            return new ThemeInfo(mSeedColors, mStyle, mContrast);
        }
    }
}
