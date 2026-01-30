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
import static android.content.theming.FieldColorSource.VALUE_PRESET;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the theme settings for the system.
 * This class holds various properties related to theming, such as color indices, palettes,
 * accent colors, color sources, theme styles, and color combinations.
 * <p>
 * {@code ThemeSettings} is an abstract class, with concrete implementations for
 * wallpaper-based themes ({@link ThemeSettingsWallpaper}) and preset themes
 * ({@link ThemeSettingsPreset}).
 *
 * @hide
 */
@FlaggedApi(android.server.Flags.FLAG_ENABLE_THEME_SERVICE)
public abstract sealed class ThemeSettings implements Parcelable permits ThemeSettingsWallpaper,
        ThemeSettingsPreset {
    public static final String TIMESTAMP = "_applied_timestamp";
    private static final String KEY_PREFIX = "android.theme.customization.";
    public static final String OVERLAY_CATEGORY_ACCENT_COLOR = KEY_PREFIX + "accent_color";
    public static final String OVERLAY_CATEGORY_SYSTEM_PALETTE = KEY_PREFIX + "system_palette";
    public static final String OVERLAY_CATEGORY_THEME_STYLE = KEY_PREFIX + "theme_style";
    public static final String OVERLAY_COLOR_SOURCE = KEY_PREFIX + "color_source";
    public static final String OVERLAY_COLOR_INDEX = KEY_PREFIX + "color_index";
    public static final String OVERLAY_COLOR_BOTH = KEY_PREFIX + "color_both";

    protected final Instant mAppliedTimestamp;
    protected final int mColorIndex;
    protected final int mThemeStyle;

    static final Map<String, ThemeSettingsField<?, ?>> ALL_FIELDS = Map.ofEntries(
            Map.entry(OVERLAY_COLOR_INDEX, new FieldColorIndex()),
            Map.entry(OVERLAY_CATEGORY_SYSTEM_PALETTE, new FieldColor()),
            Map.entry(OVERLAY_CATEGORY_ACCENT_COLOR, new FieldColor()),
            Map.entry(OVERLAY_COLOR_SOURCE, new FieldColorSource()),
            Map.entry(OVERLAY_CATEGORY_THEME_STYLE, new FieldThemeStyle()),
            Map.entry(OVERLAY_COLOR_BOTH, new FieldColorBoth()));

    protected ThemeSettings(Instant appliedTimestamp, int colorIndex,
            @ThemeStyle.Type int themeStyle) {
        this.mAppliedTimestamp = appliedTimestamp;
        this.mColorIndex = colorIndex;
        this.mThemeStyle = themeStyle;
    }

    ThemeSettings(Parcel in) {
        this.mAppliedTimestamp = Instant.ofEpochMilli(in.readLong());
        this.mColorIndex = in.readInt();
        this.mThemeStyle = in.readInt();
    }

    /**
     * Returns the timestamp indicating when these theme settings were applied or generated.
     *
     * @return The timestamp.
     */
    public Instant timeStamp() {
        return mAppliedTimestamp;
    }

    /**
     * Returns the color index associated with this theme.
     * The interpretation of this index may depend on the {@link #colorSource()}.
     *
     * @return The color index.
     */
    public Integer colorIndex() {
        return mColorIndex;
    }

    /**
     * Returns the source of the theme's color.
     * This indicates whether the theme colors are derived from the wallpaper, a preset, etc.
     *
     * @return The color source, as defined by {@link FieldColorSource.Type}.
     */
    @NonNull
    @FieldColorSource.Type
    public abstract String colorSource();

    /**
     * Returns the style of the theme.
     *
     * @return The theme style, as defined by {@link ThemeStyle.Type}.
     */
    @ThemeStyle.Type
    public Integer themeStyle() {
        return mThemeStyle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ThemeSettings that = (ThemeSettings) o;
        return Objects.equals(mAppliedTimestamp, that.mAppliedTimestamp)
                && mThemeStyle == that.mThemeStyle
                && mColorIndex == that.mColorIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAppliedTimestamp, mColorIndex, mThemeStyle);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeLong(mAppliedTimestamp.toEpochMilli());
        dest.writeInt(mColorIndex);
        dest.writeInt(mThemeStyle);
    }

    public static final Creator<ThemeSettings> CREATOR = new Creator<ThemeSettings>() {
        @Override
        public ThemeSettings createFromParcel(Parcel in) {
            // The first string read is the concrete type, used by the sealed class's parceling
            // to delegate to the correct subclass constructor..
            String type = in.readString();
            return switch (type) {
                case VALUE_HOME_WALLPAPER -> new ThemeSettingsWallpaper(in);
                case VALUE_PRESET -> new ThemeSettingsPreset(in);
                case null, default -> throw new IllegalArgumentException(
                        "Invalid type for ThemeSetting: " + type);
            };
        }

        @Override
        public ThemeSettings[] newArray(int size) {
            return new ThemeSettings[size];
        }
    };

    /**
     * Creates a {@link ThemeSettings} object from its JSON string representation.
     *
     * @param jsonString The JSON string representing the theme settings. Must not be null or empty.
     * @return A {@link ThemeSettings} object parsed from the JSON string.
     * @throws JSONException            If the provided string is not a valid JSON or if expected
     *                                  fields are
     *                                  missing or malformed.
     * @throws IllegalArgumentException If the JSON string is null, empty, or contains invalid
     *                                  values for theme settings (e.g., unknown color source,
     *                                  missing required fields, or type mismatches).
     */
    @NonNull
    public static ThemeSettings fromJson(@NonNull String jsonString)
            throws JSONException, IllegalArgumentException {
        if (TextUtils.isEmpty(jsonString)) {
            throw new IllegalArgumentException("JSON string cannot be null or empty.");
        }
        JSONObject json = new JSONObject(jsonString);

        ThemeSettings.Builder builder = ThemeSettings.builder(
                Instant.ofEpochMilli(json.getLong(TIMESTAMP)),
                parseAndValidate(json, OVERLAY_COLOR_INDEX),
                parseAndValidate(json, OVERLAY_CATEGORY_THEME_STYLE));

        String colorSource = parseAndValidate(json, OVERLAY_COLOR_SOURCE);
        return switch (colorSource) {
            case VALUE_HOME_WALLPAPER -> {
                if (json.has(OVERLAY_CATEGORY_SYSTEM_PALETTE) || json.has(
                        OVERLAY_CATEGORY_ACCENT_COLOR)) {
                    throw new IllegalArgumentException(
                            "Wallpaper theme should not contain system_palette or accent_color.");
                }
                yield builder.buildFromWallpaper(parseAndValidate(json, OVERLAY_COLOR_BOTH));
            }
            case VALUE_PRESET -> {
                if (json.has(OVERLAY_COLOR_BOTH)) {
                    throw new IllegalArgumentException(
                            "Preset theme should not contain color_both.");
                }
                yield builder.buildFromPreset(
                        parseAndValidate(json, OVERLAY_CATEGORY_SYSTEM_PALETTE),
                        parseAndValidate(json, OVERLAY_CATEGORY_ACCENT_COLOR));
            }
            default -> throw new IllegalArgumentException("Unknown color_source: " + colorSource);
        };
    }

    private static <T, J> T parseAndValidate(@NonNull JSONObject json, @NonNull String key)
            throws JSONException {
        if (!json.has(key)) {
            throw new IllegalArgumentException("Missing required key: " + key);
        }

        ThemeSettingsField<T, J> handler = (ThemeSettingsField<T, J>) ALL_FIELDS.get(key);
        if (handler == null) {
            throw new IllegalArgumentException("No handler found for key: " + key);
        }
        Object primitive = json.get(key);

        if (!handler.getJsonType().isInstance(primitive)) {
            throw new IllegalArgumentException("Invalid JSON type for key '" + key
                    + "'. Expected " + handler.getJsonType().getSimpleName()
                    + ", got " + primitive.getClass().getSimpleName());
        }

        T parsedValue =
                handler.getFieldType() == handler.getJsonType() ? (T) primitive : handler.parse(
                        (J) primitive);

        if (parsedValue == null || !handler.validate(parsedValue)) {
            throw new IllegalArgumentException("Invalid value for key '" + key + "': " + primitive);
        }

        return parsedValue;
    }

    protected static String toHex(@NonNull Color color) {
        return Integer.toHexString(color.toArgb()).toUpperCase();
    }

    /**
     * Returns a string representation of the theme settings.
     * This is typically the JSON representation used for persistence.
     *
     * @return A non-null string representation of the object.
     */
    @NonNull
    public abstract String toString();


    // --- Builder Logic ---

    /**
     * Creates a new {@link Builder} instance for creating a {@link ThemeSettings} object,
     * allowing a specific timestamp to be set.
     *
     * @param timeStamp  The timestamp for when the theme was applied or generated.
     * @param colorIndex The color index for the theme.
     * @param themeStyle The style of the theme (e.g., tonal, vibrant), as defined in
     *                   {@link ThemeStyle.Type}.
     * @return A new {@link Builder} instance.
     */
    protected static Builder builder(Instant timeStamp, int colorIndex,
            @ThemeStyle.Type int themeStyle) {
        return new Builder(timeStamp, colorIndex, themeStyle);
    }

    /**
     * Creates a new {@link Builder} instance for creating a {@link ThemeSettings} object.
     * The timestamp will be set to the current system time.
     *
     * @param colorIndex The color index for the theme.
     * @param themeStyle The style of the theme (e.g., tonal, vibrant), as defined in
     *                   {@link ThemeStyle.Type}.
     * @return A new {@link Builder} instance.
     */
    public static Builder builder(int colorIndex, @ThemeStyle.Type int themeStyle) {
        return new Builder(Instant.now(), colorIndex, themeStyle);
    }

    /**
     * A builder for creating {@link ThemeSettings} instances by providing common
     * fields to the constructor and then calling a specific factory method
     * (e.g., {@link #buildFromWallpaper(boolean)} or
     * {@link #buildFromPreset(Color, Color)}).
     */
    public static final class Builder {
        private final Instant mTimestamp;
        private final int mColorIndex;
        @ThemeStyle.Type
        private final int mThemeStyle;

        private Builder(Instant timestamp, int colorIndex, @ThemeStyle.Type int themeStyle) {
            // Validate inputs
            if (!new FieldColorIndex().validate(colorIndex)) {
                throw new IllegalArgumentException("Invalid colorIndex: " + colorIndex);
            }
            if (!new FieldThemeStyle().validate(themeStyle)) {
                throw new IllegalArgumentException("Invalid themeStyle: " + themeStyle);
            }

            mTimestamp = timestamp;
            mColorIndex = colorIndex;
            mThemeStyle = themeStyle;
        }

        /**
         * Creates a new {@link ThemeSettingsWallpaper} instance using the common fields
         * provided to the constructor. This represents a theme derived from wallpaper colors.
         *
         * @param colorBoth {@code true} if the theme color should be applied to both home and
         *                  lock screens, {@code false} otherwise.
         * @return A new, non-null {@link ThemeSettingsWallpaper} object.
         */
        @NonNull
        public ThemeSettingsWallpaper buildFromWallpaper(boolean colorBoth) {
            return new ThemeSettingsWallpaper(mTimestamp, mColorIndex, mThemeStyle, colorBoth);
        }

        /**
         * Creates a new {@link ThemeSettingsPreset} instance using the common fields
         * provided to the constructor. This represents a theme based on predefined colors.
         *
         * @param systemPalette The non-null system palette color.
         * @param accentColor   The non-null accent color.
         * @return A new, non-null {@link ThemeSettingsPreset} object.
         * @throws NullPointerException     if {@code systemPalette} or {@code accentColor} is null.
         * @throws IllegalArgumentException if {@code systemPalette} or {@code accentColor} is
         *                                  {@link Color#TRANSPARENT}.
         */
        @NonNull
        public ThemeSettingsPreset buildFromPreset(@NonNull Color systemPalette,
                @NonNull Color accentColor) {
            Objects.requireNonNull(systemPalette, "systemPalette must not be null.");
            Objects.requireNonNull(accentColor, "accentColor must not be null.");

            if (!new FieldColor().validate(systemPalette)) {
                throw new IllegalArgumentException("Invalid systemPalette color (cannot be"
                        + " Color.TRANSPARENT).");
            }
            if (!new FieldColor().validate(accentColor)) {
                throw new IllegalArgumentException("Invalid accentColor color (cannot be"
                        + " Color.TRANSPARENT).");
            }

            return new ThemeSettingsPreset(mTimestamp, mColorIndex, mThemeStyle, systemPalette,
                    accentColor);
        }
    }
}
