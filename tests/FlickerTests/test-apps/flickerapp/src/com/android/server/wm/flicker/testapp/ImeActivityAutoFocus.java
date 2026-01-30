/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.server.wm.flicker.testapp;

import android.graphics.Insets;
import android.view.WindowInsets;
import android.widget.TextView;

public class ImeActivityAutoFocus extends ImeActivity {

    @Override
    protected void onStart() {
        super.onStart();

        final var editTextField = findViewById(R.id.plain_text_input);
        editTextField.requestFocus();

        final TextView imeBottomInset = findViewById(R.id.ime_bottom_inset);
        getWindow().getDecorView().setOnApplyWindowInsetsListener((v, insets) -> {
            if (v == getWindow().getDecorView()) {
                final var imeInsets = v.getRootWindowInsets().getInsets(WindowInsets.Type.ime());
                if (!imeInsets.equals(Insets.NONE)) {
                    imeBottomInset.setText(imeInsets.bottom + "");
                }
            }
            return insets;
        });
    }
}
