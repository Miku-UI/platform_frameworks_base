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
package com.android.server.autofill.integration.dropdown;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.android.server.autofill.helper.TestHelper.findNodeByResourceId;
import static com.android.server.autofill.helper.TestHelper.sUiDevice;

import android.app.assist.AssistStructure;
import android.service.autofill.Dataset;
import android.service.autofill.FillResponse;
import android.view.autofill.AutofillValue;
import android.widget.RemoteViews;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.frameworks.autofilltests.R;
import com.android.server.autofill.MyTestActivity;
import com.android.server.autofill.helper.TestHelper;
import com.android.server.autofill.rules.AutofillActivityTestRule;
import com.android.server.autofill.rules.AutofillServiceRule;
import com.android.server.autofill.rules.PrepareDeviceRule;
import com.android.server.autofill.service.InstrumentedAutofillService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class DropdownSuggestionTest {

    private final AutofillActivityTestRule<MyTestActivity> mActivityRule =
            new AutofillActivityTestRule<>(MyTestActivity.class, false);

    private final PrepareDeviceRule mPrepareDeviceRule = new PrepareDeviceRule();

    private final AutofillServiceRule mServiceRule = new AutofillServiceRule();

    @Rule
    public final RuleChain mLookAllMyRules = RuleChain.outerRule(mServiceRule)
            .around(mPrepareDeviceRule)
            .around(mActivityRule);

    @Before
    public void setUp() {
        InstrumentedAutofillService.setReplier(null);
    }

    @After
    public void tearDown() {
        InstrumentedAutofillService.setReplier(null);
    }

    @Test
    public void testAutofillOneField_suggestionShows_fillWorks() {
        InstrumentedAutofillService.setReplier((request, cancellationSignal, callback) -> {
            AssistStructure structure = request.getFillContexts()
                    .get(request.getFillContexts().size() - 1).getStructure();
            RemoteViews presentation = new RemoteViews(TestHelper.getContext().getPackageName(),
                    R.layout.autofill_suggestion);
            presentation.setTextViewText(R.id.suggestion_text, "mytestuser");

            Dataset dataset = new Dataset.Builder(presentation)
                    .setValue(findNodeByResourceId(structure, "username"),
                            AutofillValue.forText("mytestuser"))
                    .build();

            callback.onSuccess(new FillResponse.Builder().addDataset(dataset).build());
        });

        mActivityRule.launchActivity(null);

        // Click the username field to trigger autofill
        onView(withId(R.id.username)).perform(click());

        // Wait for the suggestion to appear
        sUiDevice.wait(Until.hasObject(By.text("mytestuser")), 5000);

        // Click the suggestion
        UiObject2 suggestion = sUiDevice.findObject(By.text("mytestuser"));
        suggestion.click();

        // Verify the username field is filled
        onView(withId(R.id.username)).check(matches(withText("mytestuser")));
    }
}
