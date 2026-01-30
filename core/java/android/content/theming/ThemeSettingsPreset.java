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

import static android.content.theming.FieldColorSource.VALUE_PRESET;

import android.annotation.FlaggedApi;
import android.graphics.Color;
import android.os.Parcel;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents theme settings that are derived from a predefined set of colors.
 * This class extends {@link ThemeSettings} and includes specific properties for
 * preset themes, such as the system palette and accent color.
 *
 * @hide
 */
@FlaggedApi(android.server.Flags.FLAG_ENABLE_THEME_SERVICE)
public final class ThemeSettingsPreset extends ThemeSettings {

    @NonNull
    private final Color mSystemPalette;
    @NonNull
    private final Color mAccentColor;

    ThemeSettingsPreset(Instant appliedTimestamp, int colorIndex,
            @ThemeStyle.Type int themeStyle, @NonNull Color systemPalette,
            @NonNull Color accentColor) {
        super(appliedTimestamp, colorIndex, themeStyle);
        this.mSystemPalette = systemPalette;
        this.mAccentColor = accentColor;
    }

    ThemeSettingsPreset(@NonNull Parcel in) {
        super(in);
        mSystemPalette = Color.valueOf(in.readLong());
        mAccentColor = Color.valueOf(in.readLong());
    }

    /**
     * Returns the source of the theme's color. For preset themes, this will always be
     * {@link FieldColorSource#VALUE_PRESET}.
     *
     * @return The color source, which is {@link FieldColorSource#VALUE_PRESET}.
     */
    @NonNull
    @Override
    public String colorSource() {
        return VALUE_PRESET;
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
        dest.writeString(VALUE_PRESET); // Identify the concrete type for CREATOR
        super.writeToParcel(dest, flags);
        dest.writeLong(mSystemPalette.pack());
        dest.writeLong(mAccentColor.pack());
    }

    /**
     * Returns the system palette color for this preset theme.
     *
     * @return The non-null system palette {@link Color}.
     */
    @NonNull
    public Color systemPalette() {
        return mSystemPalette;
    }

    /**
     * Returns the accent color for this preset theme.
     *
     * @return The non-null accent {@link Color}.
     */
    @NonNull
    public Color accentColor() {
        return mAccentColor;
    }

    /**
     * Returns a JSON string representation of this preset theme settings.
     * This representation includes the timestamp, color index, system palette,
     * accent color, color source, and theme style.
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
            json.put(OVERLAY_CATEGORY_SYSTEM_PALETTE, toHex(mSystemPalette));
            json.put(OVERLAY_CATEGORY_ACCENT_COLOR, toHex(mAccentColor));
            json.put(OVERLAY_COLOR_SOURCE, colorSource());
            json.put(OVERLAY_CATEGORY_THEME_STYLE, ThemeStyle.toString(mThemeStyle));
            return json.toString();
        } catch (JSONException e) {
            // This should not happen with fixed keys and valid values
            throw new RuntimeException("Error creating JSON for PresetThemeSetting", e);
        }
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     * This method compares the superclass fields (timestamp, color index, theme style)
     * and also the system palette and accent color specific to this preset theme.
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
        ThemeSettingsPreset that = (ThemeSettingsPreset) o;
        return mSystemPalette.equals(that.mSystemPalette)
                && mAccentColor.equals(that.mAccentColor);
    }

    /**
     * Returns a hash code value for the object. This method is
     * supported for the benefit of hash tables such as those provided by
     * {@link java.util.HashMap}.
     * <p>
     * The hash code is generated based on the hash codes of the superclass fields
     * and the system palette and accent color.
     *
     * @return A hash code value for this object.
     */
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mSystemPalette, mAccentColor);
    }
}
