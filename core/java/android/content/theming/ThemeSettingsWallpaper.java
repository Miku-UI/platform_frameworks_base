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

import static android.content.theming.FieldColorSource.VALUE_HOME_WALLPAPER;

import android.annotation.FlaggedApi;
import android.os.Parcel;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents theme settings that are derived from the device's wallpaper.
 * This class extends {@link ThemeSettings} and includes specific properties for
 * wallpaper-based themes, such as whether the theme color applies to both home and
 * lock screens.
 *
 * @hide
 */
@FlaggedApi(android.server.Flags.FLAG_ENABLE_THEME_SERVICE)
public final class ThemeSettingsWallpaper extends ThemeSettings {

    @NonNull
    private final Boolean mColorBoth;

    ThemeSettingsWallpaper(Instant appliedTimestamp, int colorIndex,
            @ThemeStyle.Type int themeStyle, boolean colorBoth) {
        super(appliedTimestamp, colorIndex, themeStyle);
        this.mColorBoth = colorBoth;
    }

    ThemeSettingsWallpaper(@NonNull Parcel in) {
        super(in);
        mColorBoth = in.readBoolean();
    }

    /**
     * Flattens this object in to a Parcel.
     *
     * @param dest  The Parcel in which the object should be written.
     * @param flags Additional flags about how the object should be written.
     *              May be 0 or {@link #PARCELABLE_WRITE_RETURN_VALUE}.
     */
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(VALUE_HOME_WALLPAPER); // Identify the concrete type for CREATOR
        super.writeToParcel(dest, flags);
        dest.writeBoolean(mColorBoth);
    }

    /**
     * Returns the source of the theme's color. For wallpaper-based themes, this will always be
     * {@link FieldColorSource#VALUE_HOME_WALLPAPER}.
     *
     * @return The color source, which is {@link FieldColorSource#VALUE_HOME_WALLPAPER}.
     */
    @NonNull
    @Override
    public String colorSource() {
        return VALUE_HOME_WALLPAPER;
    }

    /**
     * Indicates whether the theme color derived from the wallpaper should be applied
     * to both the home screen and the lock screen.
     *
     * @return {@code true} if the color applies to both screens, {@code false} otherwise.
     */
    public boolean colorBoth() {
        return mColorBoth;
    }

    /**
     * Returns a JSON string representation of this wallpaper theme settings.
     * This representation includes the timestamp, color index, color source,
     * theme style, and whether the color applies to both screens.
     *
     * @return A non-null JSON string representing the object.
     * @throws RuntimeException if there is an error creating the JSON object.
     */
    @NonNull
    @Override
    public String toString() {
        try {
            JSONObject json = new JSONObject();
            json.put(TIMESTAMP, mAppliedTimestamp.toEpochMilli());
            json.put(OVERLAY_COLOR_INDEX, String.valueOf(mColorIndex));
            json.put(OVERLAY_COLOR_SOURCE, colorSource());
            json.put(OVERLAY_CATEGORY_THEME_STYLE, ThemeStyle.toString(mThemeStyle));
            json.put(OVERLAY_COLOR_BOTH, mColorBoth ? "1" : "0");
            return json.toString();
        } catch (JSONException e) {
            // This should not happen with fixed keys and valid values
            throw new RuntimeException("Error creating JSON for WallpaperThemeSetting", e);
        }
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     * This method compares the superclass fields (timestamp, color index, theme style)
     * and also the {@code colorBoth} flag specific to this wallpaper theme.
     *
     * @param o The reference object with which to compare.
     * @return {@code true} if this object is the same as the o argument;
     * {@code false} otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ThemeSettingsWallpaper that = (ThemeSettingsWallpaper) o;
        return mColorBoth.equals(that.mColorBoth);
    }

    /**
     * Returns a hash code value for the object. This method is
     * supported for the benefit of hash tables such as those provided by
     * {@link java.util.HashMap}.
     * <p>
     * The hash code is generated based on the hash codes of the superclass fields
     * and the {@code colorBoth} flag.
     *
     * @return A hash code value for this object.
     */
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mColorBoth);
    }
}
