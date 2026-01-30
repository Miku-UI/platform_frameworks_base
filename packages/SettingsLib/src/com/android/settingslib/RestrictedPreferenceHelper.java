/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.settingslib;

import static android.app.admin.DevicePolicyResources.Strings.Settings.CONTROLLED_BY_ADMIN_SUMMARY;

import static com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;

import android.app.admin.DevicePolicyIdentifiers;
import android.app.admin.DevicePolicyManager;
import android.app.admin.EnforcingAdmin;
import android.app.admin.PolicyEnforcementInfo;
import android.app.admin.UnknownAuthority;
import android.app.admin.flags.Flags;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import java.util.Objects;

/**
 * Helper class for managing settings preferences that can be disabled
 * by device admins via user restrictions.
 */
public class RestrictedPreferenceHelper {

    /**
     * Represents a restriction that a RestrictedPreference tracks. A restriction can either
     * be a user restriction represented by a constant from {@link android.os.UserManager} or
     * a policy represented by a constant from {@link DevicePolicyIdentifiers}.
     */
    private static class Restriction {
        private final String mRestriction;
        private final boolean mIsUserRestriction;

        private Restriction(String restriction, boolean isUserRestriction) {
            mRestriction = restriction;
            mIsUserRestriction = isUserRestriction;
        }

        /**
         * Creates a user restriction. {@code userRestriction} must be a user restriction
         * constant from {@link android.os.UserManager}.
         */
        static Restriction ofUserRestriction(@NonNull String userRestriction) {
            return new Restriction(userRestriction, /* isUserRestriction= */true);
        }

        /**
         * Creates a restriction that represents an admin policy. {@code policyIdentifier} must be a
         * constant from {@link DevicePolicyIdentifiers}.
         */
        static Restriction ofPolicyIdentifier(@NonNull String policyIdentifier) {
            return new Restriction(policyIdentifier, /* isUserRestriction= */false);
        }

        String getRestriction() {
            return mRestriction;
        }

        boolean isUserRestriction() {
            return mIsUserRestriction;
        }

        /**
         * Returns the device policy identifier of the restriction.
         */
        String getDevicePolicyIdentifier() {
            if (mIsUserRestriction) {
                return DevicePolicyIdentifiers.getIdentifierForUserRestriction(mRestriction);
            }
            return mRestriction;
        }
    }

    private static final String TAG = "RestrictedPreferenceHelper";

    private static final String REASON_PHONE_STATE = "phone_state";
    // For the admin components that don't have any package name defined, we use default one as
    // empty string.
    private static final String DEFAULT_ADMIN_PACKAGE_NAME = "";

    private final Context mContext;
    private final Preference mPreference;
    String packageName;

    /**
     * @deprecated TODO(b/308921175): This will be deleted with the
     * {@link android.security.Flags#extendEcmToAllSettings} feature flag. Do not use for any new
     * code.
     */
    int uid;

    private boolean mDisabledByAdmin;
    private EnforcingAdmin mEnforcingAdmin;
    @VisibleForTesting
    // TODO(b/414733570): Remove when feature is enabled and all calls have moved to use
    //  mEnforcingAdmin.
    EnforcedAdmin mEnforcedAdmin;
    private Restriction mRestriction = null;
    private boolean mDisabledSummary = false;

    private boolean mDisabledByEcm;
    private Intent mDisabledByEcmIntent = null;

    public RestrictedPreferenceHelper(Context context, Preference preference,
            AttributeSet attrs, String packageName, int uid) {
        mContext = context;
        mPreference = preference;
        this.packageName = packageName;
        this.uid = uid;

        if (attrs != null) {
            final TypedArray attributes = context.obtainStyledAttributes(attrs,
                    R.styleable.RestrictedPreference);
            final TypedValue userRestriction =
                    attributes.peekValue(R.styleable.RestrictedPreference_userRestriction);
            CharSequence data = null;
            if (userRestriction != null && userRestriction.type == TypedValue.TYPE_STRING) {
                if (userRestriction.resourceId != 0) {
                    data = context.getText(userRestriction.resourceId);
                } else {
                    data = userRestriction.string;
                }
            }
            if (data == null) {
                return;
            }
            if (!hasBaseUserRestriction(data.toString())) {
                mRestriction = Restriction.ofUserRestriction(data.toString());
            }

            final TypedValue useAdminDisabledSummary =
                    attributes.peekValue(R.styleable.RestrictedPreference_useAdminDisabledSummary);
            if (useAdminDisabledSummary != null) {
                mDisabledSummary =
                        (useAdminDisabledSummary.type == TypedValue.TYPE_INT_BOOLEAN
                                && useAdminDisabledSummary.data != 0);
            }
        }
    }

    public RestrictedPreferenceHelper(Context context, Preference preference,
            AttributeSet attrs) {
        this(context, preference, attrs, null, android.os.Process.INVALID_UID);
    }

    /**
     * Modify PreferenceViewHolder to add padlock if restriction is disabled.
     */
    public void onBindViewHolder(PreferenceViewHolder holder) {
        if (mDisabledByAdmin || mDisabledByEcm) {
            holder.itemView.setEnabled(true);
        }
        if (mDisabledSummary) {
            final TextView summaryView = (TextView) holder.findViewById(android.R.id.summary);
            if (summaryView != null) {
                final CharSequence disabledText = getDisabledByAdminSummaryString();
                if (mDisabledByAdmin && disabledText != null) {
                    summaryView.setText(disabledText);
                } else if (mDisabledByEcm) {
                    summaryView.setText(getEcmTextResId());
                } else if (TextUtils.equals(disabledText, summaryView.getText())) {
                    // It's previously set to disabled text, clear it.
                    summaryView.setText(null);
                }
            }
        }
    }

    private @Nullable String getDisabledByAdminSummaryString() {
        if (isRestrictionEnforcedByAdvancedProtection()) {
            // Advanced Protection doesn't set the summary string, it keeps the current summary.
            return null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return mContext.getSystemService(DevicePolicyManager.class).getResources().getString(
                    CONTROLLED_BY_ADMIN_SUMMARY,
                    () -> mContext.getString(R.string.disabled_by_admin_summary_text));
        }
        return mContext.getString(R.string.disabled_by_admin_summary_text);
    }

    public boolean isRestrictionEnforcedByAdvancedProtection() {
        if (Flags.policyTransparencyRefactorEnabled()) {
            if (mRestriction == null) {
                return false;
            }
            return mEnforcingAdmin != null
                    && RestrictedLockUtilsInternal.isPolicyEnforcedByAdvancedProtection(mContext,
                    // When the feature is enabled and we're using mEnforcingAdmin, the user
                    // restriction is always stored on mRestriction.
                    mRestriction.getRestriction(), UserHandle.myUserId());
        } else {
            return mEnforcedAdmin != null
                    && RestrictedLockUtilsInternal.isPolicyEnforcedByAdvancedProtection(mContext,
                    mEnforcedAdmin.enforcedRestriction, UserHandle.myUserId());
        }
    }

    /**
     * Configures the user restriction that this preference will track and updates the disabled
     * state. This is equivalent to specifying
     * {@link R.styleable#RestrictedPreference_userRestriction} in XML and allows
     * configuring user restriction at runtime. Note that this will overwrite the policy value if
     * it's set previously set by {@link #setAdminPolicyRestriction(String)}.
     */
    public void setUserRestriction(@Nullable String userRestriction) {
        if (Flags.policyTransparencyRefactorEnabled()) {
            if (userRestriction == null) {
                return;
            }
            // We don't need to check for base restriction here because {@link
            // checkAdminRestrictionEnforced} method considers it.
            mRestriction = Restriction.ofUserRestriction(userRestriction);
            setDisabledByEnforcingAdmin(checkAdminRestrictionEnforced());
            return;
        }
        mRestriction =
                userRestriction == null || RestrictedLockUtilsInternal.hasBaseUserRestriction(
                        mContext, userRestriction, UserHandle.myUserId()) ? null
                        : Restriction.ofUserRestriction(userRestriction);
        setDisabledByAdmin(checkRestrictionEnforced());
    }

    /**
     * Configures the admin policy that this preference will track and updates the disabled state.
     * This option is for setting the policy identifier. For user restrictions, see
     * {@link #setUserRestriction(String)}. Note that setting this value will overwrite the user
     * restriction value that was previously set.
     */
    public void setAdminPolicyRestriction(@NonNull String adminPolicyIdentifier) {
        Objects.requireNonNull(adminPolicyIdentifier);
        mRestriction = Restriction.ofPolicyIdentifier(adminPolicyIdentifier);
        if (Flags.policyTransparencyRefactorEnabled()) {
            setDisabledByEnforcingAdmin(checkAdminRestrictionEnforced());
            return;
        }
        setDisabledByAdmin(checkRestrictionEnforced());
    }

    public void useAdminDisabledSummary(boolean useSummary) {
        mDisabledSummary = useSummary;
    }

    /**
     * Check if the preference is disabled if so handle the click by informing the user.
     *
     * @return true if the method handled the click.
     */
    @SuppressWarnings("NewApi")
    public boolean performClick() {
        if (mDisabledByAdmin) {
            if (Flags.policyTransparencyRefactorEnabled() && mRestriction != null) {
                RestrictedLockUtils.sendShowAdminSupportDetailsIntent(
                        mContext, mEnforcingAdmin, mRestriction.getRestriction());
            } else {
                RestrictedLockUtils.sendShowAdminSupportDetailsIntent(mContext, mEnforcedAdmin);
            }
            return true;
        }
        if (mDisabledByEcm) {
            if (android.permission.flags.Flags.enhancedConfirmationModeApisEnabled()
                    && android.security.Flags.extendEcmToAllSettings()) {
                mContext.startActivity(mDisabledByEcmIntent);
                return true;
            } else {
                RestrictedLockUtilsInternal.sendShowRestrictedSettingDialogIntent(mContext,
                        packageName, uid);
                return true;
            }
        }
        return false;
    }

    /**
     * Disable / enable if we have been passed the restriction in the xml.
     */
    public void onAttachedToHierarchy() {
        // mRestriction will be set as a user restriction if it's passed in xml.
        if (mRestriction != null && mRestriction.isUserRestriction()) {
            checkRestrictionAndSetDisabled(mRestriction.getRestriction(), UserHandle.myUserId());
        }
    }

    /**
     * Checks if the user restriction is enforced by the admin and updates the disabled state.
     *
     * @param userRestriction constant from {@link android.os.UserManager}
     * @param userId user to check the restriction for.
     */
    public void checkRestrictionAndSetDisabled(@NonNull String userRestriction, int userId) {
        Objects.requireNonNull(userRestriction);
        if (Flags.policyTransparencyRefactorEnabled()) {
            setDisabledByEnforcingAdmin(
                    checkAdminRestrictionEnforced(Restriction.ofUserRestriction(userRestriction),
                            userId));
            return;
        }
        EnforcedAdmin admin = RestrictedLockUtilsInternal.checkIfRestrictionEnforced(mContext,
                userRestriction, userId);
        setDisabledByAdmin(admin);
    }

    /**
     * Checks if the admin policy is enforced by the admin and updates the disabled state.
     *
     * @param policyIdentifier constant from {@link DevicePolicyIdentifiers}
     * @param userId user to check the policy for.
     */
    public void checkPolicyAndSetDisabled(@NonNull String policyIdentifier, int userId) {
        Objects.requireNonNull(policyIdentifier);
        setDisabledByEnforcingAdmin(
                checkAdminRestrictionEnforced(Restriction.ofPolicyIdentifier(policyIdentifier),
                        userId));
    }

    /**
     * Checks if the given setting is subject to Enhanced Confirmation Mode restrictions for this
     * package. Marks the preference as disabled if so.
     * @param settingIdentifier The key identifying the setting
     * @param packageName the package to check the settingIdentifier for
     * @param settingEnabled Whether the setting in question is enabled
     */
    public void checkEcmRestrictionAndSetDisabled(@NonNull String settingIdentifier,
            @NonNull String packageName, boolean settingEnabled) {
        updatePackageDetails(packageName, android.os.Process.INVALID_UID);
        if (settingEnabled) {
            setDisabledByEcm(null);
            return;
        }
        Intent intent = RestrictedLockUtilsInternal.checkIfRequiresEnhancedConfirmation(
                mContext, settingIdentifier, packageName);
        setDisabledByEcm(intent);
    }

    /**
     * Checks if the given setting is subject to Enhanced Confirmation Mode restrictions for this
     * package. Marks the preference as disabled if so.
     * TODO b/390196024: remove this and update all callers to use the "settingEnabled" version
     * @param settingIdentifier The key identifying the setting
     * @param packageName the package to check the settingIdentifier for
     */
    public void checkEcmRestrictionAndSetDisabled(@NonNull String settingIdentifier,
            @NonNull String packageName) {
        checkEcmRestrictionAndSetDisabled(settingIdentifier, packageName, false);
    }

    /**
     * @return EnforcedAdmin if we have been passed the restriction in the xml.
     */
    public EnforcedAdmin checkRestrictionEnforced() {
        if (mRestriction == null) {
            return null;
        }
        if (Flags.policyTransparencyRefactorEnabled()) {
            // Use new DPM API if the flag is enabled.
            EnforcingAdmin enforcingAdmin = checkAdminRestrictionEnforced();
            if (enforcingAdmin == null) {
                return null;
            }
            return new EnforcedAdmin(enforcingAdmin.getComponentName(),
                    mRestriction.getRestriction(), enforcingAdmin.getUserHandle());
        }
        return RestrictedLockUtilsInternal.checkIfRestrictionEnforced(mContext,
                mRestriction.getRestriction(), UserHandle.myUserId());
    }

    /**
     * Checks if the restriction represented by this preference is enforced by an admin on the
     * current running user and returns that admin. If not, returns null. The restriction should be
     * set by {@link #setUserRestriction(String)} or {@link #setAdminPolicyRestriction(String)} or
     * through XML attribute {@link R.styleable#RestrictedPreference_userRestriction} before calling
     * this.
     */
    @Nullable
    public EnforcingAdmin checkAdminRestrictionEnforced() {
        if (mRestriction == null) {
            return null;
        }
        return checkAdminRestrictionEnforced(mRestriction, UserHandle.myUserId());
    }

    private EnforcingAdmin checkAdminRestrictionEnforced(Restriction restriction, int userId) {
        final DevicePolicyManager dpm = mContext.getSystemService(DevicePolicyManager.class);
        if (dpm == null) {
            return null;
        }
        PolicyEnforcementInfo policyEnforcementInfo = dpm.getEnforcingAdminsForPolicy(
                restriction.getDevicePolicyIdentifier(),
                userId);
        // Don't set it disabled by admin if only system is enforcing a restriction.
        if (policyEnforcementInfo.isOnlyEnforcedBySystem()) {
            return null;
        }
        return policyEnforcementInfo.getMostImportantEnforcingAdmin();
    }

    /**
     * Disable this preference based on the enforce admin.
     *
     * @param admin details of the admin who enforced the restriction. If it is {@code null}, then
     *     this preference will be enabled. Otherwise, it will be disabled. Only gray out the
     *     preference which is not {@link RestrictedTopLevelPreference}.
     * @return true if the disabled state was changed.
     */
    public boolean setDisabledByAdmin(EnforcedAdmin admin) {
        if (Flags.policyTransparencyRefactorEnabled()) {
            EnforcingAdmin enforcingAdmin = getEnforcingAdminFromEnforcedAdmin(admin);
            // Ensure that mAttrUserRestriction is set to the value passed in admin if it's unset.
            // If it's already set, we don't need to update the value.
            if (admin != null && mRestriction == null) {
                mRestriction = Restriction.ofUserRestriction(admin.enforcedRestriction);
            }
            return setDisabledByEnforcingAdmin(enforcingAdmin);
        }
        // TODO(b/414733570): Cleanup when Flags.policyTransparencyRefactorEnabled() is fully
        //  rolled out.
        boolean disabled = false;
        boolean changed = false;
        EnforcedAdmin previousAdmin = mEnforcedAdmin;
        mEnforcedAdmin = null;
        if (admin != null) {
            disabled = true;
            // Copy the received instance to prevent pass be reference being overwritten.
            mEnforcedAdmin = new EnforcedAdmin(admin);
            if (android.security.Flags.aapmApi()) {
                changed = previousAdmin == null || !previousAdmin.equals(admin);
            }
        }

        if (mDisabledByAdmin != disabled) {
            mDisabledByAdmin = disabled;
            changed = true;
        }

        if (changed) {
            updateDisabledState();
        }

        return changed;
    }

    /**
     * Disable this preference based on the enforcing admin. Note that this doesn't set the
     * restriction that this preference tracks. To set the restriction, call
     * {@link #setUserRestriction} or set it through XML attribute
     * {@link R.styleable#RestrictedPreference_userRestriction}.
     *
     * @param admin details of the admin who enforced the restriction. If it is {@code null}, then
     *              this preference will be enabled. Otherwise, it will be disabled. Only gray out
     *              the preference which is not {@link RestrictedTopLevelPreference}.
     * @return true if the disabled state was changed.
     */
    public boolean setDisabledByEnforcingAdmin(@Nullable EnforcingAdmin admin) {
        final boolean disabled = (admin != null);
        final boolean adminChanged = !Objects.equals(mEnforcingAdmin, admin);
        final boolean disabledStateChanged = mDisabledByAdmin != disabled;

        mEnforcingAdmin = admin;
        mDisabledByAdmin = disabled;

        final boolean changed = adminChanged || disabledStateChanged;
        if (changed) {
            updateDisabledState();
        }

        return changed;
    }

    /**
     * Disable the preference based on the passed in Intent
     * @param disabledIntent The intent which is started when the user clicks the disabled
     * preference. If it is {@code null}, then this preference will be enabled. Otherwise, it will
     * be disabled.
     * @return true if the disabled state was changed.
     */
    public boolean setDisabledByEcm(@Nullable Intent disabledIntent) {
        boolean disabled = disabledIntent != null;
        boolean changed = false;
        if (mDisabledByEcm != disabled) {
            mDisabledByEcmIntent = disabledIntent;
            mDisabledByEcm = disabled;
            changed = true;
            updateDisabledState();
        }

        return changed;
    }

    public boolean isDisabledByAdmin() {
        return mDisabledByAdmin;
    }

    public boolean isDisabledByEcm() {
        return mDisabledByEcm;
    }

    public void updatePackageDetails(String packageName, int uid) {
        this.packageName = packageName;
        this.uid = uid;
    }

    private void updateDisabledState() {
        boolean isEnabled = !(mDisabledByAdmin || mDisabledByEcm);
        if (!(mPreference instanceof RestrictedTopLevelPreference)) {
            mPreference.setEnabled(isEnabled);
        }

        if (mPreference instanceof PrimarySwitchPreference) {
            ((PrimarySwitchPreference) mPreference).setSwitchEnabled(isEnabled);
        }

        if (android.security.Flags.aapmApi() && mDisabledByAdmin) {
            String summary = getDisabledByAdminSummaryString();
            if (summary != null) {
                mPreference.setSummary(summary);
            }
        } else if (mDisabledByEcm) {
            mPreference.setSummary(getEcmTextResId());
        }
    }

    private int getEcmTextResId() {
        if (mDisabledByEcmIntent != null && REASON_PHONE_STATE.equals(
                mDisabledByEcmIntent.getStringExtra(Intent.EXTRA_REASON))) {
            return R.string.disabled_in_phone_call_text;
        } else {
            return R.string.disabled_by_app_ops_text;
        }
    }


    /**
     * @deprecated TODO(b/308921175): This will be deleted with the
     * {@link android.security.Flags#extendEcmToAllSettings} feature flag. Do not use for any new
     * code.
     */
    @Deprecated
    public boolean setDisabledByAppOps(boolean disabled) {
        boolean changed = false;
        if (mDisabledByEcm != disabled) {
            mDisabledByEcm = disabled;
            changed = true;
            updateDisabledState();
        }

        return changed;
    }

    private EnforcingAdmin getEnforcingAdminFromEnforcedAdmin(EnforcedAdmin admin) {
        // Check the nullable fields of EnforcedAdmin and replace with appropriate values for null
        // as EnforcingAdmin has non-null fields.
        return admin == null ? null : new EnforcingAdmin(
                admin.component == null ? DEFAULT_ADMIN_PACKAGE_NAME
                        : admin.component.getPackageName(),
                UnknownAuthority.UNKNOWN_AUTHORITY,
                admin.user == null ? UserHandle.of(UserHandle.myUserId()) : admin.user,
                admin.component);
    }

    private boolean hasBaseUserRestriction(String userRestriction) {
        if (Flags.policyTransparencyRefactorEnabled()) {
            DevicePolicyManager dpm = mContext.getSystemService(DevicePolicyManager.class);
            if (dpm == null) {
                return false;
            }
            PolicyEnforcementInfo policyEnforcementInfo = dpm.getEnforcingAdminsForPolicy(
                    DevicePolicyIdentifiers.getIdentifierForUserRestriction(userRestriction),
                            UserHandle.myUserId());
            return policyEnforcementInfo.isOnlyEnforcedBySystem();
        }
        return RestrictedLockUtilsInternal.hasBaseUserRestriction(mContext, userRestriction,
                UserHandle.myUserId());
    }
}
