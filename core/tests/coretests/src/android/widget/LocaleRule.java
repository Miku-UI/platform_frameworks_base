/*
 * Copyright 2025 The Android Open Source Project
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

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.Locale;

/**
 * Sets the device Locale for the duration of the test and restores the original afterwards.
 *
 * <pre>
 * &#64;Rule public LocaleRule rule = new LocaleRule(Locale.JAPANESE);
 * </pre>
 * @hide
 */
public class LocaleRule implements TestRule {
    private final Locale mLocale;

    public LocaleRule(Locale locale) {
        mLocale = locale;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Locale original = Locale.getDefault();
                try {
                    // Set desired locale
                    setLocale(mLocale);
                    base.evaluate();
                } finally {
                    // Restore original locale after test
                    setLocale(original);
                }
            }
        };
    }

    private void setLocale(Locale locale) {
        Locale.setDefault(locale);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Resources resources = context.getResources();
        Configuration config = resources.getConfiguration();
        config.setLocale(locale);
        config.setLayoutDirection(locale); // Good practice for RTL languages (Arabic, Hebrew)
        // Update resources for the current context
        resources.updateConfiguration(config, resources.getDisplayMetrics());
        // Also update the Application Context to ensure background tasks use the right locale
        context.getApplicationContext().getResources()
            .updateConfiguration(config, resources.getDisplayMetrics());
    }
}