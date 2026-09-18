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

import android.app.Activity;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * A test rule for automatic start activity if needed
 */
public class AutofillActivityTestRule<T extends Activity> implements TestRule {

    private final Class<T> mActivityClass;
    private final boolean mLaunchActivity;
    private ActivityScenario<T> mScenario;
    private T mActivity;

    public AutofillActivityTestRule(Class<T> activityClass, boolean launchActivity) {
        mActivityClass = activityClass;
        mLaunchActivity = launchActivity;
    }

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
        if (mLaunchActivity) {
            launchActivity(null);
        }
    }

    private void after() {
        if (mScenario != null) {
            mScenario.close();
        }
    }

    /**
     * Launch an activity with intent
     *
     * @param startIntent
     * @return the activity instance
     */
    public T launchActivity(Intent startIntent) {
        if (mScenario != null) {
            mScenario.close();
        }
        mScenario = ActivityScenario.launch(mActivityClass);
        mScenario.onActivity(activity -> mActivity = activity);
        return mActivity;
    }

    public T getActivity() {
        return mActivity;
    }
}
