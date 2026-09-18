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
package com.android.server.autofill.rules;

import android.provider.Settings;

import com.android.server.autofill.helper.TestHelper;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public class AutofillServiceRule implements TestRule {

    private static final String SERVICE_NAME = "com.android.frameworks.autofilltests/"
            + "com.android.server.autofill.service.InstrumentedAutofillService";

    private String mOriginalService;

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                before();
                try {
                    base.evaluate();
                } finally {
                    after();
                }
            }
        };
    }

    private void before() {
        mOriginalService = getAutofillService();
        setAutofillService(SERVICE_NAME);

        String currentService = getAutofillService();
        if (!SERVICE_NAME.equals(currentService)) {
            throw new IllegalStateException("Autofill service failed to set correctly during test. "
                    + "Expected " + SERVICE_NAME + ", got " + currentService);
        }
    }

    private void after() {
        setAutofillService(mOriginalService);
    }

    private String getAutofillService() {
        return Settings.Secure.getString(TestHelper.getContext().getContentResolver(),
                Settings.Secure.AUTOFILL_SERVICE);
    }

    private void setAutofillService(String service) {
        TestHelper.executeShellCommand("settings put secure autofill_service " + service);
    }
}
