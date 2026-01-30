/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.settingslib.collapsingtoolbar;

import android.app.ActionBar;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toolbar;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import com.android.settingslib.collapsingtoolbar.widget.ScrollableToolbarItemLayout;
import com.android.settingslib.widget.SettingsThemeHelper;
import com.android.settingslib.widget.SetupWizardHelper;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;

import java.util.List;

/**
 * A base Activity that has a collapsing toolbar layout is used for the activities intending to
 * enable the collapsing toolbar function.
 */
public class CollapsingToolbarBaseActivity extends FragmentActivity implements
        FloatingToolbarHandler {

    private class DelegateCallback implements CollapsingToolbarDelegate.HostCallback {
        @Nullable
        @Override
        public ActionBar setActionBar(Toolbar toolbar) {
            CollapsingToolbarBaseActivity.super.setActionBar(toolbar);
            return CollapsingToolbarBaseActivity.super.getActionBar();
        }

        @Override
        public void setOuterTitle(CharSequence title) {
            CollapsingToolbarBaseActivity.super.setTitle(title);
        }
    }

    private CollapsingToolbarDelegate mToolbardelegate;

    private int mCustomizeLayoutResId = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdgeUtils.enable(this);
        super.onCreate(savedInstanceState);

        getToolbarDelegate().registerToolbarCollapseBehavior(this);

        if (SettingsThemeHelper.isExpressiveTheme(this)) {
            setTheme(com.android.settingslib.widget.theme.R.style.Theme_SubSettingsBase_Expressive);
        }

        // for backward compatibility on R devices or wearable devices due to small device size.
        if (mCustomizeLayoutResId > 0 && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || isWatch())) {
            super.setContentView(mCustomizeLayoutResId);
            return;
        }

        View view = getToolbarDelegate().onCreateView(getLayoutInflater(), null, this);
        super.setContentView(view);

        if (SetupWizardHelper.isAnySetupWizard(getIntent())) {
            findViewById(R.id.content_parent).setFitsSystemWindows(false);
        }
    }

    @Override
    public void setContentView(int layoutResID) {
        final ViewGroup parent = (mToolbardelegate == null) ? findViewById(R.id.content_frame)
                : mToolbardelegate.getContentFrameLayout();
        if (parent != null) {
            parent.removeAllViews();
        }
        LayoutInflater.from(this).inflate(layoutResID, parent);
    }

    @Override
    public void setContentView(View view) {
        final ViewGroup parent = (mToolbardelegate == null) ? findViewById(R.id.content_frame)
                : mToolbardelegate.getContentFrameLayout();
        if (parent != null) {
            parent.addView(view);
        }
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        final ViewGroup parent = (mToolbardelegate == null) ? findViewById(R.id.content_frame)
                : mToolbardelegate.getContentFrameLayout();
        if (parent != null) {
            parent.addView(view, params);
        }
    }

    /**
     * This method allows an activity to replace the default layout with a customize layout. Notice
     * that it will no longer apply the features being provided by this class when this method
     * gets called.
     */
    protected void setCustomizeContentView(int layoutResId) {
        mCustomizeLayoutResId = layoutResId;
    }

    @Override
    public void setTitle(CharSequence title) {
        getToolbarDelegate().setTitle(title);
    }

    @Override
    public void setTitle(int titleId) {
        setTitle(getText(titleId));
    }

    /**
     * Show/Hide the primary button on the Toolbar.
     * @param enabled true to show the button, otherwise it's hidden.
     */
    public void setPrimaryButtonEnabled(boolean enabled) {
        getToolbarDelegate().setPrimaryButtonEnabled(enabled);
    }

    /** Set the icon to the primary button */
    public void setPrimaryButtonIcon(@DrawableRes int drawableRes) {
        getToolbarDelegate().setPrimaryButtonIcon(this, drawableRes);
    }

    /** Set the OnClick listener to the primary button. */
    public void setPrimaryButtonOnClickListener(@Nullable View.OnClickListener listener) {
        getToolbarDelegate().setPrimaryButtonOnClickListener(listener);
    }

    /** Set the content description to the primary button */
    public void setPrimaryButtonContentDescription(@Nullable CharSequence contentDescription) {
        getToolbarDelegate().setPrimaryButtonContentDescription(contentDescription);
    }

    /**
     * Show/Hide the secondary button on the Toolbar.
     * @param enabled true to show the button, otherwise it's hidden.
     */
    public void setSecondaryButtonEnabled(boolean enabled) {
        getToolbarDelegate().setSecondaryButtonEnabled(enabled);
    }

    /** Set the icon to the secondary button */
    public void setSecondaryButtonIcon(@DrawableRes int drawableRes) {
        getToolbarDelegate().setSecondaryButtonIcon(this, drawableRes);
    }

    /** Set the OnClick listener to the secondary button */
    public void setSecondaryButtonOnClickListener(@Nullable View.OnClickListener listener) {
        getToolbarDelegate().setSecondaryButtonOnClickListener(listener);
    }

    /** Set the content description to the secondary button */
    public void setSecondaryButtonContentDescription(@Nullable CharSequence contentDescription) {
        getToolbarDelegate().setSecondaryButtonContentDescription(contentDescription);
    }

    /**
     * Show/Hide the action button on the Toolbar.
     * @param enabled true to show the button, otherwise it's hidden.
     */
    public void setActionButtonEnabled(boolean enabled) {
        getToolbarDelegate().setActionButtonEnabled(enabled);
    }

    /**
     * Enable/Disable the action button on the Toolbar (being clickable or not).
     * @param clickable true to enable the button, otherwise it's disabled.
     */
    public void setActionButtonClickable(boolean clickable) {
        getToolbarDelegate().setActionButtonClickable(clickable);
    }

    /** Set the icon to the action button */
    public void setActionButtonIcon(@DrawableRes int drawableRes) {
        getToolbarDelegate().setActionButtonIcon(this, drawableRes);
    }

    /** Set the text to the action button */
    public void setActionButtonText(@Nullable CharSequence text) {
        getToolbarDelegate().setActionButtonText(text);
    }

    /** Set the OnClick listener to the action button */
    public void setActionButtonListener(@Nullable View.OnClickListener listener) {
        getToolbarDelegate().setActionButtonOnClickListener(listener);
    }

    /** Set the content description to the action button */
    public void setActionButtonContentDescription(@Nullable CharSequence contentDescription) {
        getToolbarDelegate().setActionButtonContentDescription(contentDescription);
    }

    @Override
    public void setFloatingToolbarVisibility(boolean visible) {
        getToolbarDelegate().setFloatingToolbarVisibility(visible);
    }

    @Override
    public void setToolbarItems(@NonNull List<ScrollableToolbarItemLayout.ToolbarItem> items) {
        getToolbarDelegate().setToolbarItems(items);
    }

    @Override
    public void setOnItemSelectedListener(
            @NonNull ScrollableToolbarItemLayout.OnItemSelectedListener listener) {
        getToolbarDelegate().setOnItemSelectedListener(listener);
    }

    @Override
    public void removeOnItemSelectedListener() {
        getToolbarDelegate().removeOnItemSelectedListener();
    }

    @Override
    public boolean onNavigateUp() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            getSupportFragmentManager().popBackStackImmediate();
        }

        // Closes the activity if there is no fragment inside the stack. Otherwise the activity will
        // has a blank screen since there is no any fragment.
        if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
            finishAfterTransition();
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        // Closes the activity if there is no fragment inside the stack. Otherwise the activity will
        // has a blank screen since there is no any fragment. onBackPressed() in Activity.java only
        // handles popBackStackImmediate(). This will close activity to avoid a blank screen.
        if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
            finishAfterTransition();
        }
    }

    /**
     * Returns an instance of collapsing toolbar.
     */
    @Nullable
    public CollapsingToolbarLayout getCollapsingToolbarLayout() {
        return getToolbarDelegate().getCollapsingToolbarLayout();
    }

    /**
     * Return an instance of app bar.
     */
    @Nullable
    public AppBarLayout getAppBarLayout() {
        return getToolbarDelegate().getAppBarLayout();
    }

    private boolean isWatch() {
        PackageManager packageManager = getPackageManager();
        if (packageManager == null) {
            return false;
        }
        return packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

    private CollapsingToolbarDelegate getToolbarDelegate() {
        if (mToolbardelegate == null) {
            mToolbardelegate = new CollapsingToolbarDelegate(new DelegateCallback(), true);
        }
        return mToolbardelegate;
    }

    /** Returns the current theme and checks if needing to apply expressive theme. */
    @Override
    public Resources.Theme getTheme() {
        Resources.Theme theme = super.getTheme();
        if (SettingsThemeHelper.isExpressiveTheme(this)) {
            theme.applyStyle(
                    com.android.settingslib.widget.theme.R.style.Theme_SubSettingsBase_Expressive,
                    true);
        }
        return theme;
    }
}
