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

package android.content.theming;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** @hide */
@FlaggedApi(android.server.Flags.FLAG_ENABLE_THEME_SERVICE)
public final class FieldColorList extends ThemeSettingsField<List<Color>, JSONArray> {
    private final FieldColor mColorField = new FieldColor();

    @Override
    @Nullable
    public List<Color> parse(JSONArray primitive) {
        if (primitive == null) {
            return null;
        }

        List<Color> colors = new ArrayList<>();
        try {
            for (int i = 0; i < primitive.length(); i++) {
                Color color = mColorField.parse(primitive.getString(i));
                if (color != null) {
                    colors.add(color);
                }
            }
        } catch (JSONException e) {
            return null;
        }
        return colors.isEmpty() ? null : colors;
    }

    @Override
    public JSONArray serialize(List<Color> value) {
        JSONArray array = new JSONArray();
        for (Color color : value) {
            array.put(mColorField.serialize(color));
        }
        return array;
    }

    @Override
    public boolean validate(@NonNull List<Color> value) {
        Objects.requireNonNull(value);
        if (value.isEmpty()) return false;
        for (Color color : value) {
            if (!mColorField.validate(color)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Class<List<Color>> getFieldType() {
        return (Class<List<Color>>) (Class<?>) List.class;
    }

    @Override
    public Class<JSONArray> getJsonType() {
        return JSONArray.class;
    }
}
