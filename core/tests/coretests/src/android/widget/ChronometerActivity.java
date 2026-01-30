/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.widget;

import android.app.Activity;
import android.app.KeyguardManager;
import android.os.Bundle;

import com.android.frameworks.coretests.R;

/**
 * A minimal activity for ChronometerTest.
 */
public class ChronometerActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        turnOnScreenAndDismissKeyguard();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.chronometer_layout);
    }

    private void turnOnScreenAndDismissKeyguard() {
        setTurnScreenOn(true);
        setShowWhenLocked(true);
        KeyguardManager keyguardManager = getSystemService(KeyguardManager.class);
        keyguardManager.requestDismissKeyguard(this, null);
    }
}
