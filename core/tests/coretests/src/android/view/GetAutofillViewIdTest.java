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

package android.view;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.flag.junit.SetFlagsRule;
import android.service.autofill.Flags;
import android.view.autofill.AutofillManager;
import android.view.autofill.AutofillTestActivity;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class GetAutofillViewIdTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();
    @Rule
    public ActivityScenarioRule<AutofillTestActivity> mScenarioRule =
            new ActivityScenarioRule<>(AutofillTestActivity.class);

    private Context mApplicationContext = ApplicationProvider.getApplicationContext();
    private View mTestView;
    private InjectAfmContext mInjectAfmContext;

    @Before
    public void setUp() throws Exception {
        mInjectAfmContext = new InjectAfmContext(mApplicationContext);
        mScenarioRule.getScenario().onActivity(activity -> {
            AutofillManager afm = activity.getSystemService(AutofillManager.class);
            mInjectAfmContext.setInjectedAfm(afm);
        });
        mTestView = new View(mInjectAfmContext);
    }

    @Test
    @DisableFlags(Flags.FLAG_GET_AUTOFILL_VIEW_ID_FROM_AUTOFILL_MANAGER)
    public void getAutofillViewId_whenFlagDisabled_returnsIdFromApplicationContext() {
        assertTrue(mTestView.getAutofillViewId() < View.LAST_APP_AUTOFILL_ID);
    }

    @Test
    @EnableFlags(Flags.FLAG_GET_AUTOFILL_VIEW_ID_FROM_AUTOFILL_MANAGER)
    public void getAutofillViewId_whenFlagEnabled_returnsIdFromManager() {
        assertTrue(mTestView.getAutofillViewId() >= View.LAST_APP_AUTOFILL_ID);
    }

    private class InjectAfmContext extends ContextWrapper {
        private AutofillManager mInjectedAfm;

        InjectAfmContext(Context base) {
            super(base);
        }

        @Override
        public Object getSystemService(String serviceName) {
            if (serviceName != null && serviceName.equals(Context.AUTOFILL_SERVICE)
                    && mInjectedAfm != null) {
                return mInjectedAfm;
            }
            return super.getSystemService(serviceName);
        }

        public void setInjectedAfm(AutofillManager afm) {
            mInjectedAfm = afm;
        }
    }
}
