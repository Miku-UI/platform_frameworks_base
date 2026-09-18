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

package android.app;

import static android.app.NotificationManager.INTERRUPTION_FILTER_ALL;
import static android.app.NotificationSystemUtil.runAsSystemUi;
import static android.app.NotificationSystemUtil.toggleNotificationPolicyAccess;
import static android.service.notification.Condition.STATE_FALSE;
import static android.service.notification.Condition.STATE_TRUE;

import static com.android.server.notification.Flags.FLAG_STRICT_ZEN_RULE_COMPONENT_VALIDATION;
import static android.service.notification.Flags.FLAG_SPLIT_SOUND_VIBRATION_FOR_NOTIFICATION_BREAKTHROUGH;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.ComponentName;
import android.content.Context;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Binder;
import android.os.SystemClock;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.notification.Condition;
import android.service.notification.ZenModeConfig;
import android.service.notification.ZenPolicy;

import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class NotificationManagerZenTest {

    private static final Uri CONDITION_ID = Uri.parse("test://NotificationManagerZenTest");
    private static final ComponentName CONDITION_PROVIDER_SERVICE = new ComponentName(
            "com.android.frameworks.tests.uiservices",
            "android.app.ExampleConditionProviderService");
    private static final ComponentName CONFIGURATION_ACTIVITY = new ComponentName(
            "com.android.frameworks.tests.uiservices",
            "android.app.ExampleActivity");

    private Context mContext;
    private NotificationManager mNotificationManager;
    private AppOpsManager mAppOpsManager;
    @Nullable
    private AudioOpChangedListener mSoundOpListener;
    @Nullable
    private AudioOpChangedListener mVibrationOpListener;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
        mNotificationManager = mContext.getSystemService(NotificationManager.class);
        mAppOpsManager = mContext.getSystemService(AppOpsManager.class);

        toggleNotificationPolicyAccess(mContext, mContext.getPackageName(), true);
        runAsSystemUi(() -> mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_ALL));
        removeAutomaticZenRules();
    }

    @After
    public void tearDown() {
        runAsSystemUi(() -> mNotificationManager.setInterruptionFilter(INTERRUPTION_FILTER_ALL));
        removeAutomaticZenRules();

        if (mSoundOpListener != null) {
            mAppOpsManager.stopWatchingMode(mSoundOpListener);
        }
        if (mVibrationOpListener != null) {
            mAppOpsManager.stopWatchingMode(mVibrationOpListener);
        }
    }

    private void removeAutomaticZenRules() {
        // Delete AZRs created by this test (query "as app", then delete "as system" so they are
        // not preserved to be restored later).
        Map<String, AutomaticZenRule> rules = mNotificationManager.getAutomaticZenRules();
        runAsSystemUi(() -> {
            for (String ruleId : rules.keySet()) {
                mNotificationManager.removeAutomaticZenRule(ruleId);
            }
        });
    }

    @Test
    public void addAutomaticZenRule_validCpsAndConfigActivity_accepted() {
        AutomaticZenRule azr = new AutomaticZenRule.Builder("CPS & Activity", CONDITION_ID)
                .setOwner(CONDITION_PROVIDER_SERVICE)
                .setConfigurationActivity(CONFIGURATION_ACTIVITY)
                .build();

        String ruleId = mNotificationManager.addAutomaticZenRule(azr);

        AutomaticZenRule savedAzr = mNotificationManager.getAutomaticZenRule(ruleId);
        assertThat(savedAzr).isNotNull();
        assertThat(savedAzr.getOwner()).isEqualTo(CONDITION_PROVIDER_SERVICE);
        assertThat(savedAzr.getConfigurationActivity()).isEqualTo(CONFIGURATION_ACTIVITY);
        assertThat(savedAzr.getPackageName()).isEqualTo(mContext.getPackageName());
    }

    @Test
    public void addAutomaticZenRule_validCpsAndNoConfigActivity_accepted() {
        AutomaticZenRule azr = new AutomaticZenRule.Builder("Valid CPS", CONDITION_ID)
                .setOwner(CONDITION_PROVIDER_SERVICE)
                .setConfigurationActivity(null)
                .build();

        String ruleId = mNotificationManager.addAutomaticZenRule(azr);

        AutomaticZenRule savedAzr = mNotificationManager.getAutomaticZenRule(ruleId);
        assertThat(savedAzr).isNotNull();
        assertThat(savedAzr.getOwner()).isEqualTo(CONDITION_PROVIDER_SERVICE);
        assertThat(savedAzr.getPackageName()).isEqualTo(mContext.getPackageName());
    }

    @Test
    public void addAutomaticZenRule_noCpsAndValidConfigActivity_accepted() {
        AutomaticZenRule azr = new AutomaticZenRule.Builder("Valid Activity", CONDITION_ID)
                .setOwner(null)
                .setConfigurationActivity(CONFIGURATION_ACTIVITY)
                .build();

        String ruleId = mNotificationManager.addAutomaticZenRule(azr);

        AutomaticZenRule savedAzr = mNotificationManager.getAutomaticZenRule(ruleId);
        assertThat(savedAzr).isNotNull();
        assertThat(savedAzr.getConfigurationActivity()).isEqualTo(CONFIGURATION_ACTIVITY);
        assertThat(savedAzr.getPackageName()).isEqualTo(mContext.getPackageName());
    }

    @Test
    public void addAutomaticZenRule_noCpsNorConfigActivity_rejected() {
        AutomaticZenRule azr = new AutomaticZenRule.Builder("None", CONDITION_ID)
                .setOwner(null)
                .setConfigurationActivity(null)
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> mNotificationManager.addAutomaticZenRule(azr));
    }

    @Test
    public void addAutomaticZenRule_invalidCpsAndNoConfigActivity_rejected() {
        AutomaticZenRule azr = new AutomaticZenRule.Builder("Invalid CPS", CONDITION_ID)
                .setOwner(new ComponentName(mContext, "android.app.NonExistentCps"))
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> mNotificationManager.addAutomaticZenRule(azr));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_STRICT_ZEN_RULE_COMPONENT_VALIDATION)
    public void addAutomaticZenRule_invalidCpsButValidConfigActivity_cpsRemoved() {
        AutomaticZenRule azr = new AutomaticZenRule.Builder("Invalid CPS", CONDITION_ID)
                .setOwner(new ComponentName(mContext, "android.app.NonExistentCps"))
                .setConfigurationActivity(CONFIGURATION_ACTIVITY)
                .build();

        String ruleId = mNotificationManager.addAutomaticZenRule(azr);

        AutomaticZenRule savedAzr = mNotificationManager.getAutomaticZenRule(ruleId);
        assertThat(savedAzr).isNotNull();
        assertThat(savedAzr.getOwner()).isNull();
        assertThat(savedAzr.getConfigurationActivity()).isEqualTo(CONFIGURATION_ACTIVITY);
        assertThat(savedAzr.getPackageName()).isEqualTo(mContext.getPackageName());
    }

    @Test
    public void addAutomaticZenRule_invalidConfigActivityAndNoCps_rejected() {
        AutomaticZenRule azr = new AutomaticZenRule.Builder("Invalid CPS", CONDITION_ID)
                .setConfigurationActivity(
                        new ComponentName(mContext, "android.app.NonExistentActivity"))
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> mNotificationManager.addAutomaticZenRule(azr));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_STRICT_ZEN_RULE_COMPONENT_VALIDATION)
    public void addAutomaticZenRule_invalidConfigActivityButValidCps_configActivityRemoved() {
        AutomaticZenRule azr = new AutomaticZenRule.Builder("Invalid CPS", CONDITION_ID)
                .setOwner(CONDITION_PROVIDER_SERVICE)
                .setConfigurationActivity(
                        new ComponentName(mContext, "android.app.NonExistentActivity"))
                .build();

        String ruleId = mNotificationManager.addAutomaticZenRule(azr);

        AutomaticZenRule savedAzr = mNotificationManager.getAutomaticZenRule(ruleId);
        assertThat(savedAzr).isNotNull();
        assertThat(savedAzr.getOwner()).isEqualTo(CONDITION_PROVIDER_SERVICE);
        assertThat(savedAzr.getConfigurationActivity()).isNull();
        assertThat(savedAzr.getPackageName()).isEqualTo(mContext.getPackageName());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SPLIT_SOUND_VIBRATION_FOR_NOTIFICATION_BREAKTHROUGH)
    public void addAutomaticZenRule_splitSoundVibration() {
        ZenPolicy policy = new ZenPolicy.Builder().allowAlarms(true, false).build();
        AutomaticZenRule azr = new AutomaticZenRule.Builder("split sound rule", CONDITION_ID)
                .setOwner(CONDITION_PROVIDER_SERVICE)
                .setZenPolicy(policy)
                .build();

        String ruleId = mNotificationManager.addAutomaticZenRule(azr);

        AutomaticZenRule savedAzr = mNotificationManager.getAutomaticZenRule(ruleId);
        assertThat(savedAzr.getZenPolicy()).isNotNull();
        assertThat(savedAzr.getZenPolicy().getPriorityCategoryAlarms())
                .isEqualTo(ZenPolicy.STATE_ALLOW);
        assertThat(savedAzr.getZenPolicy().getInterruptionTypeAlarms())
                .isEqualTo(ZenPolicy.ALLOWED_INTERRUPTION_TYPE_SOUND_ONLY);
    }

    @Test
    public void addAutomaticZenRule_cpsInDifferentPackage_rejected() {
        AutomaticZenRule azr = new AutomaticZenRule.Builder("System CPS !!", CONDITION_ID)
                .setOwner(ZenModeConfig.getScheduleConditionProvider())
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> mNotificationManager.addAutomaticZenRule(azr));
    }

    @Test
    public void addAutomaticZenRule_configActivityInDifferentPackage_rejected() {
        AutomaticZenRule azr = new AutomaticZenRule.Builder("Other Activity !!", CONDITION_ID)
                .setConfigurationActivity(
                        new ComponentName("com.android.settings", "Settings$ModesSettingsActivity"))
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> mNotificationManager.addAutomaticZenRule(azr));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_STRICT_ZEN_RULE_COMPONENT_VALIDATION)
    public void updateAutomaticZenRule_switchToInvalidCps_rejected() {
        AutomaticZenRule original = new AutomaticZenRule.Builder("OK so far", CONDITION_ID)
                .setOwner(CONDITION_PROVIDER_SERVICE)
                .build();

        String ruleId = mNotificationManager.addAutomaticZenRule(original);

        AutomaticZenRule sneaky = new AutomaticZenRule.Builder(original)
                .setOwner(ZenModeConfig.getScheduleConditionProvider())
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> mNotificationManager.updateAutomaticZenRule(ruleId, sneaky));
    }

    @Test
    public void updateAutomaticZenRule_switchToInvalidCpsButWithValidConfigActivity_cpsReverts() {
        AutomaticZenRule original = new AutomaticZenRule.Builder("OK so far", CONDITION_ID)
                .setOwner(CONDITION_PROVIDER_SERVICE)
                .setConfigurationActivity(CONFIGURATION_ACTIVITY)
                .build();
        String ruleId = mNotificationManager.addAutomaticZenRule(original);

        AutomaticZenRule sneaky = new AutomaticZenRule.Builder(original)
                .setOwner(ZenModeConfig.getScheduleConditionProvider())
                .build();
        mNotificationManager.updateAutomaticZenRule(ruleId, sneaky);

        // Unlike for configurationActivity, an app cannot modify the CPS associated to an AZR.
        AutomaticZenRule savedAzr = mNotificationManager.getAutomaticZenRule(ruleId);
        assertThat(savedAzr).isNotNull();
        assertThat(savedAzr.getOwner()).isEqualTo(CONDITION_PROVIDER_SERVICE);
        assertThat(savedAzr.getConfigurationActivity()).isEqualTo(CONFIGURATION_ACTIVITY);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_STRICT_ZEN_RULE_COMPONENT_VALIDATION)
    public void updateAutomaticZenRule_switchToInvalidConfigActivity_rejected() {
        AutomaticZenRule original = new AutomaticZenRule.Builder("OK so far", CONDITION_ID)
                .setConfigurationActivity(CONFIGURATION_ACTIVITY)
                .build();

        String ruleId = mNotificationManager.addAutomaticZenRule(original);

        AutomaticZenRule sneaky = new AutomaticZenRule.Builder(original)
                .setConfigurationActivity(
                        new ComponentName("com.android.settings", "Settings$ModesSettingsActivity"))
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> mNotificationManager.updateAutomaticZenRule(ruleId, sneaky));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_STRICT_ZEN_RULE_COMPONENT_VALIDATION)
    public void updateAutomaticZenRule_switchToInvalidConfigActivityButWithValidCps_activityGone() {
        AutomaticZenRule original = new AutomaticZenRule.Builder("OK so far", CONDITION_ID)
                .setOwner(CONDITION_PROVIDER_SERVICE)
                .setConfigurationActivity(CONFIGURATION_ACTIVITY)
                .build();
        String ruleId = mNotificationManager.addAutomaticZenRule(original);

        AutomaticZenRule sneaky = new AutomaticZenRule.Builder(original)
                .setConfigurationActivity(
                        new ComponentName("com.android.settings", "Settings$ModesSettingsActivity"))
                .build();
        mNotificationManager.updateAutomaticZenRule(ruleId, sneaky);

        AutomaticZenRule savedAzr = mNotificationManager.getAutomaticZenRule(ruleId);
        assertThat(savedAzr).isNotNull();
        assertThat(savedAzr.getConfigurationActivity()).isNull();
        assertThat(savedAzr.getOwner()).isEqualTo(CONDITION_PROVIDER_SERVICE);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SPLIT_SOUND_VIBRATION_FOR_NOTIFICATION_BREAKTHROUGH)
    public void updateAutomaticZenRule_withSplitSoundVibrationPolicy_isSaved() {
        AutomaticZenRule original = new AutomaticZenRule.Builder("Original", CONDITION_ID)
                .setOwner(CONDITION_PROVIDER_SERVICE)
                .setConfigurationActivity(CONFIGURATION_ACTIVITY)
                .setZenPolicy(new ZenPolicy.Builder().allowAlarms(true).build())
                .build();
        String ruleId = mNotificationManager.addAutomaticZenRule(original);

        ZenPolicy updatedPolicy = new ZenPolicy.Builder().allowAlarms(false, true).build();
        AutomaticZenRule updated = new AutomaticZenRule.Builder(original)
                .setZenPolicy(updatedPolicy)
                .build();
        mNotificationManager.updateAutomaticZenRule(ruleId, updated);

        AutomaticZenRule savedAzr = mNotificationManager.getAutomaticZenRule(ruleId);
        assertThat(savedAzr.getZenPolicy()).isNotNull();
        assertThat(savedAzr.getZenPolicy().getPriorityCategoryAlarms())
                .isEqualTo(ZenPolicy.STATE_ALLOW);
        assertThat(savedAzr.getZenPolicy().getInterruptionTypeAlarms())
                .isEqualTo(ZenPolicy.ALLOWED_INTERRUPTION_TYPE_VIBRATION_ONLY);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SPLIT_SOUND_VIBRATION_FOR_NOTIFICATION_BREAKTHROUGH)
    public void
    updateAutomaticZenRule_withSplitSoundVibrationPolicy_merge_originalSettingsPreserved() {
        AutomaticZenRule original = new AutomaticZenRule.Builder("Original", CONDITION_ID)
                .setOwner(CONDITION_PROVIDER_SERVICE)
                .setConfigurationActivity(CONFIGURATION_ACTIVITY)
                .setZenPolicy(new ZenPolicy.Builder().allowAlarms(false, true).build())
                .build();
        String ruleId = mNotificationManager.addAutomaticZenRule(original);

        ZenPolicy updatedPolicy = new ZenPolicy.Builder().allowAlarms(true).build();
        AutomaticZenRule updated = new AutomaticZenRule.Builder(original)
                .setZenPolicy(updatedPolicy)
                .build();
        mNotificationManager.updateAutomaticZenRule(ruleId, updated);

        AutomaticZenRule savedAzr = mNotificationManager.getAutomaticZenRule(ruleId);
        assertThat(savedAzr.getZenPolicy()).isNotNull();
        assertThat(savedAzr.getZenPolicy().getPriorityCategoryAlarms())
                .isEqualTo(ZenPolicy.STATE_ALLOW);
        assertThat(savedAzr.getZenPolicy().getInterruptionTypeAlarms())
                .isEqualTo(ZenPolicy.ALLOWED_INTERRUPTION_TYPE_VIBRATION_ONLY);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SPLIT_SOUND_VIBRATION_FOR_NOTIFICATION_BREAKTHROUGH)
    public void
    updateAutomaticZenRule_withSplitSoundVibrationPolicy_merge_originalSettingsDiscarded() {
        AutomaticZenRule original = new AutomaticZenRule.Builder("Original", CONDITION_ID)
                .setOwner(CONDITION_PROVIDER_SERVICE)
                .setConfigurationActivity(CONFIGURATION_ACTIVITY)
                .setZenPolicy(new ZenPolicy.Builder().allowAlarms(false, true).build())
                .build();
        String ruleId = mNotificationManager.addAutomaticZenRule(original);

        ZenPolicy updatedPolicy = new ZenPolicy.Builder().allowAlarms(false).build();
        AutomaticZenRule updated = new AutomaticZenRule.Builder(original)
                .setZenPolicy(updatedPolicy)
                .build();
        mNotificationManager.updateAutomaticZenRule(ruleId, updated);

        AutomaticZenRule savedAzr = mNotificationManager.getAutomaticZenRule(ruleId);
        assertThat(savedAzr.getZenPolicy()).isNotNull();
        assertThat(savedAzr.getZenPolicy().getPriorityCategoryAlarms())
                .isEqualTo(ZenPolicy.STATE_DISALLOW);
        assertThat(savedAzr.getZenPolicy().getInterruptionTypeAlarms())
                .isEqualTo(ZenPolicy.ALLOWED_INTERRUPTION_TYPE_UNSET);
    }


    @Test
    public void addAutomaticZenRule_fromPackage_forcesOwnerPackage() {
        AutomaticZenRule azr = new AutomaticZenRule.Builder("Set wrong package", CONDITION_ID)
                .setOwner(CONDITION_PROVIDER_SERVICE)
                .setConfigurationActivity(CONFIGURATION_ACTIVITY)
                .setPackage("com.something.something")
                .build();

        String ruleId = mNotificationManager.addAutomaticZenRule(azr);

        AutomaticZenRule savedAzr = mNotificationManager.getAutomaticZenRule(ruleId);
        assertThat(savedAzr).isNotNull();
        assertThat(savedAzr.getPackageName()).isEqualTo(mContext.getPackageName());
    }

    @Test
    public void setAutomaticZenRuleState_manualActivation() {
        AutomaticZenRule ruleToCreate = createZenRule("rule");
        String ruleId = mNotificationManager.addAutomaticZenRule(ruleToCreate);
        Condition manualActivate = new Condition(ruleToCreate.getConditionId(), "manual-on",
                STATE_TRUE, Condition.SOURCE_USER_ACTION);
        Condition manualDeactivate = new Condition(ruleToCreate.getConditionId(), "manual-off",
                STATE_FALSE, Condition.SOURCE_USER_ACTION);
        Condition autoActivate = new Condition(ruleToCreate.getConditionId(), "auto-on",
                STATE_TRUE);
        Condition autoDeactivate = new Condition(ruleToCreate.getConditionId(), "auto-off",
                STATE_FALSE);

        // User manually activates -> it's active.
        runAsSystemUi(
                () -> mNotificationManager.setAutomaticZenRuleState(ruleId, manualActivate));
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_TRUE);

        // User manually deactivates -> it's inactive.
        runAsSystemUi(
                () -> mNotificationManager.setAutomaticZenRuleState(ruleId, manualDeactivate));
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_FALSE);

        // And app can activate and deactivate.
        mNotificationManager.setAutomaticZenRuleState(ruleId, autoActivate);
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_TRUE);
        mNotificationManager.setAutomaticZenRuleState(ruleId, autoDeactivate);
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_FALSE);
    }

    @Test
    public void setAutomaticZenRuleState_manualDeactivation() {
        AutomaticZenRule ruleToCreate = createZenRule("rule");
        String ruleId = mNotificationManager.addAutomaticZenRule(ruleToCreate);
        Condition manualActivate = new Condition(ruleToCreate.getConditionId(), "manual-on",
                STATE_TRUE, Condition.SOURCE_USER_ACTION);
        Condition manualDeactivate = new Condition(ruleToCreate.getConditionId(), "manual-off",
                STATE_FALSE, Condition.SOURCE_USER_ACTION);
        Condition autoActivate = new Condition(ruleToCreate.getConditionId(), "auto-on",
                STATE_TRUE);
        Condition autoDeactivate = new Condition(ruleToCreate.getConditionId(), "auto-off",
                STATE_FALSE);

        // App activates rule.
        mNotificationManager.setAutomaticZenRuleState(ruleId, autoActivate);
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_TRUE);

        // User manually deactivates -> it's inactive.
        runAsSystemUi(
                () -> mNotificationManager.setAutomaticZenRuleState(ruleId, manualDeactivate));
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_FALSE);

        // User manually reactivates -> it's active.
        runAsSystemUi(
                () -> mNotificationManager.setAutomaticZenRuleState(ruleId, manualActivate));
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_TRUE);

        // That manual activation removed the override-deactivate, but didn't put an
        // override-activate, so app can deactivate when its natural schedule ends.
        mNotificationManager.setAutomaticZenRuleState(ruleId, autoDeactivate);
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_FALSE);
    }

    @Test
    public void setAutomaticZenRuleState_respectsManuallyActivated() {
        AutomaticZenRule ruleToCreate = createZenRule("rule");
        String ruleId = mNotificationManager.addAutomaticZenRule(ruleToCreate);
        Condition manualActivate = new Condition(ruleToCreate.getConditionId(), "manual-on",
                STATE_TRUE, Condition.SOURCE_USER_ACTION);
        Condition autoActivate = new Condition(ruleToCreate.getConditionId(), "auto-on",
                STATE_TRUE);
        Condition autoDeactivate = new Condition(ruleToCreate.getConditionId(), "auto-off",
                STATE_FALSE);

        // App thinks rule should be inactive.
        mNotificationManager.setAutomaticZenRuleState(ruleId, autoDeactivate);
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_FALSE);

        // Manually activate -> it's active.
        runAsSystemUi(() -> mNotificationManager.setAutomaticZenRuleState(ruleId, manualActivate));
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_TRUE);

        // App says it should be inactive, but it's ignored.
        mNotificationManager.setAutomaticZenRuleState(ruleId, autoDeactivate);
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_TRUE);

        // App says it should be active. No change now...
        mNotificationManager.setAutomaticZenRuleState(ruleId, autoActivate);
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_TRUE);

        // ... but when the app wants to deactivate next time, it works.
        mNotificationManager.setAutomaticZenRuleState(ruleId, autoDeactivate);
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_FALSE);
    }

    @Test
    public void setAutomaticZenRuleState_respectsManuallyDeactivated() {
        AutomaticZenRule ruleToCreate = createZenRule("rule");
        String ruleId = mNotificationManager.addAutomaticZenRule(ruleToCreate);
        Condition manualDeactivate = new Condition(ruleToCreate.getConditionId(), "manual-off",
                STATE_FALSE, Condition.SOURCE_USER_ACTION);
        Condition autoActivate = new Condition(ruleToCreate.getConditionId(), "auto-on",
                STATE_TRUE);
        Condition autoDeactivate = new Condition(ruleToCreate.getConditionId(), "auto-off",
                STATE_FALSE);

        // App activates rule.
        mNotificationManager.setAutomaticZenRuleState(ruleId, autoActivate);
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_TRUE);

        // User manually deactivates -> it's inactive.
        runAsSystemUi(
                () -> mNotificationManager.setAutomaticZenRuleState(ruleId, manualDeactivate));
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_FALSE);

        // App says it should be active, but it's ignored.
        mNotificationManager.setAutomaticZenRuleState(ruleId, autoActivate);
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_FALSE);

        // App says it should be inactive. No change now...
        mNotificationManager.setAutomaticZenRuleState(ruleId, autoDeactivate);
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_FALSE);

        // ... but when the app wants to activate next time, it works.
        mNotificationManager.setAutomaticZenRuleState(ruleId, autoActivate);
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_TRUE);
    }

    @Test
    public void setAutomaticZenRuleState_manualActivationFromApp() {
        AutomaticZenRule ruleToCreate = createZenRule("rule");
        String ruleId = mNotificationManager.addAutomaticZenRule(ruleToCreate);
        Condition manualActivate = new Condition(ruleToCreate.getConditionId(), "manual-off",
                STATE_TRUE, Condition.SOURCE_USER_ACTION);
        Condition manualDeactivate = new Condition(ruleToCreate.getConditionId(), "manual-off",
                STATE_FALSE, Condition.SOURCE_USER_ACTION);
        Condition autoActivate = new Condition(ruleToCreate.getConditionId(), "auto-on",
                STATE_TRUE);
        Condition autoDeactivate = new Condition(ruleToCreate.getConditionId(), "auto-off",
                STATE_FALSE);

        // App activates rule.
        mNotificationManager.setAutomaticZenRuleState(ruleId, autoActivate);
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_TRUE);

        // User manually deactivates from SysUI -> it's inactive.
        runAsSystemUi(
                () -> mNotificationManager.setAutomaticZenRuleState(ruleId, manualDeactivate));
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_FALSE);

        // User manually activates from App -> it's active.
        mNotificationManager.setAutomaticZenRuleState(ruleId, manualActivate);
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_TRUE);

        // And app can automatically deactivate it later.
        mNotificationManager.setAutomaticZenRuleState(ruleId, autoDeactivate);
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_FALSE);
    }

    @Test
    public void setAutomaticZenRuleState_manualDeactivationFromApp() {
        AutomaticZenRule ruleToCreate = createZenRule("rule");
        String ruleId = mNotificationManager.addAutomaticZenRule(ruleToCreate);
        Condition manualActivate = new Condition(ruleToCreate.getConditionId(), "manual-off",
                STATE_TRUE, Condition.SOURCE_USER_ACTION);
        Condition manualDeactivate = new Condition(ruleToCreate.getConditionId(), "manual-off",
                STATE_FALSE, Condition.SOURCE_USER_ACTION);
        Condition autoActivate = new Condition(ruleToCreate.getConditionId(), "auto-on",
                STATE_TRUE);

        // User manually activates from SysUI -> it's active.
        runAsSystemUi(
                () -> mNotificationManager.setAutomaticZenRuleState(ruleId, manualActivate));
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_TRUE);

        // User manually deactivates from App -> it's inactive.
        mNotificationManager.setAutomaticZenRuleState(ruleId, manualDeactivate);
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_FALSE);

        // And app can automatically activate it later.
        mNotificationManager.setAutomaticZenRuleState(ruleId, autoActivate);
        assertThat(mNotificationManager.getAutomaticZenRuleState(ruleId)).isEqualTo(STATE_TRUE);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SPLIT_SOUND_VIBRATION_FOR_NOTIFICATION_BREAKTHROUGH)
    public void testConsolidatedPolicy_mergesSplitSoundVibration() {
        // Rule that only allows sound for alarms
        AutomaticZenRule ruleSound = new AutomaticZenRule.Builder("Sound Only", CONDITION_ID)
                .setOwner(CONDITION_PROVIDER_SERVICE)
                .setConfigurationActivity(CONFIGURATION_ACTIVITY)
                .setZenPolicy(new ZenPolicy.Builder().allowAlarms(true, false).build())
                .build();
        String idSound = mNotificationManager.addAutomaticZenRule(ruleSound);

        // Rule that only allows vibration for alarms
        AutomaticZenRule ruleVib = new AutomaticZenRule.Builder("Vib Only", CONDITION_ID)
                .setOwner(CONDITION_PROVIDER_SERVICE)
                .setConfigurationActivity(CONFIGURATION_ACTIVITY)
                .setZenPolicy(new ZenPolicy.Builder().allowAlarms(false, true).build())
                .build();
        String idVib = mNotificationManager.addAutomaticZenRule(ruleVib);

        // Activate both rules
        Condition on = new Condition(CONDITION_ID, "on", STATE_TRUE);
        mNotificationManager.setAutomaticZenRuleState(idSound, on);
        mNotificationManager.setAutomaticZenRuleState(idVib, on);

        // Verify the consolidated policy disables both sound and vibration for alarms
        NotificationManager.Policy consolidatedPolicy =
                mNotificationManager.getConsolidatedNotificationPolicy();

        // Check general category
        assertThat((consolidatedPolicy.priorityCategories
                & NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS))
                    .isEqualTo(0);

        // Check granular permissions (sound from Rule 1, vibration from Rule 2)
        assertThat(consolidatedPolicy.allowSoundFor(
                NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS)).isFalse();
        assertThat(consolidatedPolicy.allowVibrationFor(
                NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS)).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SPLIT_SOUND_VIBRATION_FOR_NOTIFICATION_BREAKTHROUGH)
    public void testAppliedSounds_reflectsSplitSoundVibration() {
        String packageName = mContext.getPackageName();
        int uid = Binder.getCallingUid();
        mSoundOpListener = new AudioOpChangedListener(packageName, uid, mAppOpsManager);
        mVibrationOpListener = new AudioOpChangedListener(packageName, uid, mAppOpsManager);
        mAppOpsManager.startWatchingMode(AppOpsManager.OP_PLAY_AUDIO, packageName,
                mSoundOpListener);
        mAppOpsManager.startWatchingMode(AppOpsManager.OP_VIBRATE, packageName,
                mVibrationOpListener);

        // Vibration-only rule
        AutomaticZenRule ruleVibOnly = new AutomaticZenRule.Builder("Vib Only", CONDITION_ID)
                .setOwner(CONDITION_PROVIDER_SERVICE)
                .setConfigurationActivity(CONFIGURATION_ACTIVITY)
                .setZenPolicy(new ZenPolicy.Builder().allowAlarms(false, true).build())
                .build();
        String id = mNotificationManager.addAutomaticZenRule(ruleVibOnly);

        Condition on = new Condition(CONDITION_ID, "on", STATE_TRUE);
        mNotificationManager.setAutomaticZenRuleState(id, on);

        assertThat(mSoundOpListener.waitFor(AppOpsManager.MODE_IGNORED, AudioAttributes.USAGE_ALARM,
                5, TimeUnit.SECONDS)).isTrue();
        assertThat(mVibrationOpListener.waitFor(AppOpsManager.MODE_ALLOWED,
                AudioAttributes.USAGE_ALARM, 5, TimeUnit.SECONDS)).isTrue();

        // Update Rule to allow sound only
        AutomaticZenRule ruleSoundOnly = new AutomaticZenRule.Builder(ruleVibOnly)
                .setZenPolicy(new ZenPolicy.Builder().allowAlarms(true, false).build())
                .build();
        mNotificationManager.updateAutomaticZenRule(id, ruleSoundOnly);
        mNotificationManager.setAutomaticZenRuleState(id, on);

        assertThat(mSoundOpListener.waitFor(AppOpsManager.MODE_ALLOWED, AudioAttributes.USAGE_ALARM,
                5, TimeUnit.SECONDS)).isTrue();
        assertThat(mVibrationOpListener.waitFor(AppOpsManager.MODE_IGNORED,
                AudioAttributes.USAGE_ALARM, 5, TimeUnit.SECONDS)).isTrue();
    }

    private AutomaticZenRule createZenRule(String name) {
        return createZenRule(name, NotificationManager.INTERRUPTION_FILTER_PRIORITY);
    }

    private AutomaticZenRule createZenRule(String name, int filter) {
        return new AutomaticZenRule(name, null,
                new ComponentName(mContext, ExampleActivity.class),
                new Uri.Builder().scheme("scheme")
                        .appendPath("path")
                        .appendQueryParameter("fake_rule", "fake_value")
                        .build(), null, filter, true);
    }

    private static class AudioOpChangedListener implements AppOpsManager.OnOpChangedListener {
        private final BlockingQueue<String> mQueue = new LinkedBlockingQueue<>();
        private final String mPackageName;
        private final int mUid;
        private final AppOpsManager mAppOpsManager;

        private AudioOpChangedListener(String packageName, int uid, AppOpsManager appOpsManager) {
            this.mPackageName = packageName;
            this.mUid = uid;
            this.mAppOpsManager = appOpsManager;
        }

        @Override
        public void onOpChanged(String op, String packageName) {
            mQueue.add(op);
        }

        @Nullable
        String getNext(long timeout, TimeUnit unit) {
            try {
                return mQueue.poll(timeout, unit);
            } catch (InterruptedException e) {
                return null;
            }
        }

        boolean waitFor(int mode, int stream, long timeout, TimeUnit unit) {
            long timeoutMs = unit.toMillis(timeout);
            long start = SystemClock.elapsedRealtime();
            while (true) {
                String strOp = getNext(timeoutMs, TimeUnit.MILLISECONDS);
                if (strOp == null) {
                    // Timeout.
                    return false;
                }
                int newMode = mAppOpsManager.checkAudioOpNoThrow(AppOpsManager.strOpToOp(strOp),
                        stream, mUid, mPackageName);
                if (newMode == mode) {
                    return true;
                }
                timeoutMs -= (SystemClock.elapsedRealtime() - start);
                if (timeoutMs <= 0) {
                    // Timeout.
                    return false;
                }
            }
        }
    }
}
