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

import java.util.Objects;

/** @hide */
@FlaggedApi(android.server.Flags.FLAG_ENABLE_THEME_SERVICE)
public final class FieldColorIndex extends ThemeSettingsField<Integer, String> {
    @Override
    public Integer parse(String primitive) {
        if (primitive == null) {
            return null;
        }
        try {
            return Integer.parseInt(primitive);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String serialize(Integer typedValue) {
        return typedValue.toString();
    }

    @Override
    public boolean validate(Integer value) {
        Objects.requireNonNull(value);
        return value >= -1;
    }

    @Override
    public Class<Integer> getFieldType() {
        return Integer.class;
    }

    @Override
    public Class<String> getJsonType() {
        return String.class;
    }
}
