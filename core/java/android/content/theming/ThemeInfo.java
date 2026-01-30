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

import android.annotation.ColorInt;
import android.annotation.FlaggedApi;
import android.annotation.Nullable;
import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 * Represents the core information of a user's theme, including the seed color,
 * style, and contrast level.
 *
 * @hide
 */
@FlaggedApi(android.server.Flags.FLAG_ENABLE_THEME_SERVICE)
public final class ThemeInfo implements Parcelable {
    @Nullable
    @ColorInt
    public final Integer seedColor;
    @Nullable
    @ThemeStyle.Type
    public final Integer style;
    @Nullable
    public final Float contrast;

    private ThemeInfo(@Nullable @ColorInt Integer seedColor,
            @Nullable @ThemeStyle.Type Integer style,
            @Nullable Float contrast) {
        this.seedColor = seedColor;
        this.style = style;
        this.contrast = contrast;
    }

    private ThemeInfo(Parcel in) {
        seedColor = (Integer) in.readValue(Integer.class.getClassLoader());
        style = (Integer) in.readValue(Integer.class.getClassLoader());
        contrast = (Float) in.readValue(Float.class.getClassLoader());
    }

    /**
     * A builder for creating {@link ThemeInfo} instances. Any parameter can be {@code null}
     * to indicate that the current system value for that attribute should be used.
     *
     * @param seedColor The primary color to generate the theme's color palette, or {@code null}.
     * @param style     The theme style (e.g., tonal, vibrant), or {@code null}.
     * @param contrast  The contrast level of the theme, or {@code null}.
     * @return A new {@link ThemeInfo} instance.
     */
    public static ThemeInfo build(@Nullable Color seedColor,
            @Nullable @ThemeStyle.Type Integer style, @Nullable Float contrast) {
        return new ThemeInfo(seedColor == null ? null : seedColor.toArgb(), style, contrast);
    }

    @Override
    @SuppressWarnings("AndroidFrameworkEfficientParcelable")
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeValue(seedColor);
        dest.writeValue(style);
        dest.writeValue(contrast);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ThemeInfo> CREATOR = new Creator<ThemeInfo>() {
        @Override
        public ThemeInfo createFromParcel(Parcel in) {
            return new ThemeInfo(in);
        }

        @Override
        public ThemeInfo[] newArray(int size) {
            return new ThemeInfo[size];
        }
    };
}
