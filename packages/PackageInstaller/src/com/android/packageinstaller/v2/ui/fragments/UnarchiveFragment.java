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

package com.android.packageinstaller.v2.ui.fragments;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInstaller;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.Html;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.android.packageinstaller.R;
import com.android.packageinstaller.v2.model.UnarchiveError;
import com.android.packageinstaller.v2.model.UnarchiveStage;
import com.android.packageinstaller.v2.model.UnarchiveUserActionRequired;
import com.android.packageinstaller.v2.ui.UiUtil;
import com.android.packageinstaller.v2.ui.UnarchiveActionListener;
import com.android.packageinstaller.v2.viewmodel.UnarchiveViewModel;

public class UnarchiveFragment extends DialogFragment {

    private static final String LOG_TAG = UnarchiveFragment.class.getSimpleName();

    private Dialog mDialog;
    private UnarchiveActionListener mUnarchiveActionListener;

    private ImageView mAppIcon = null;
    private TextView mAppLabelTextView = null;
    private View mAppSnippet = null;
    private TextView mCustomMessageTextView = null;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mUnarchiveActionListener = (UnarchiveActionListener) context;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final UnarchiveStage unarchiveStage = getCurrentUnarchiveStage();

        Log.i(LOG_TAG, "Creating " + LOG_TAG + "\n" + unarchiveStage);

        // There is no root view here. Ok to pass null view root
        @SuppressWarnings("InflateParams")
        View dialogView = getLayoutInflater().inflate(R.layout.uninstall_fragment_layout, null);
        mAppSnippet = dialogView.requireViewById(R.id.app_snippet);
        mAppIcon = dialogView.requireViewById(R.id.app_icon);
        mAppLabelTextView = dialogView.requireViewById(R.id.app_label);
        mCustomMessageTextView = dialogView.requireViewById(R.id.custom_message);
        mCustomMessageTextView.setVisibility(View.VISIBLE);

        mDialog = UiUtil.getAlertDialog(requireContext(), getString(R.string.title_restore),
                dialogView, R.string.button_restore, R.string.button_cancel,
                (dialog, which) -> mUnarchiveActionListener.beginUnarchive(),
                (dialog, which) -> {});
        return mDialog;
    }

    @Override
    public void onPause() {
        super.onPause();
        Button button = UiUtil.getAlertDialogPositiveButton(mDialog);
        // If the button is not clickable, don't need to set enabled to false
        if (button != null && button.isClickable()) {
            button.setEnabled(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Button button = UiUtil.getAlertDialogPositiveButton(mDialog);
        if (button != null) {
            button.setEnabled(true);
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (isAdded()) {
            requireActivity().finish();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        updateUI();
    }

    private UnarchiveStage getCurrentUnarchiveStage() {
        return new ViewModelProvider(requireActivity()).get(UnarchiveViewModel.class)
                .getCurrentUnarchiveStage().getValue();
    }

    /**
     * Update the UI based on the current unarchive stage
     */
    public void updateUI() {
        if (!isAdded()) {
            return;
        }

        // Get the current unarchive stage
        final UnarchiveStage unarchiveStage = getCurrentUnarchiveStage();

        switch (unarchiveStage.getStageCode()) {
            case UnarchiveStage.STAGE_ERROR -> {
                updateUnarchiveErrorUI(mDialog, (UnarchiveError) unarchiveStage);
            }
            case UnarchiveStage.STAGE_USER_ACTION_REQUIRED -> {
                updateUserActionRequiredUI(mDialog, (UnarchiveUserActionRequired) unarchiveStage);
            }
        }

        UiUtil.updateButtonBarLayoutIfNeeded(requireContext(), mDialog);
    }

    private void updateUnarchiveErrorUI(Dialog dialog, UnarchiveError unarchiveStage) {
        mAppSnippet.setVisibility(View.GONE);

        final int status = unarchiveStage.getUnarchivalStatus();
        final String installerAppTitle = unarchiveStage.getInstallerAppTitle();
        final long requiredBytes = unarchiveStage.getRequiredBytes();

        String title = null;
        CharSequence customMessage = null;
        int positiveBtnTextResId = Resources.ID_NULL;
        int negativeBtnTextResId = R.string.button_close;
        switch (status) {
            case PackageInstaller.UNARCHIVAL_ERROR_USER_ACTION_NEEDED -> {
                title = getString(R.string.title_restore_error_user_action_needed);
                positiveBtnTextResId = R.string.button_continue;
                negativeBtnTextResId = R.string.button_cancel;
                customMessage =
                        getString(R.string.message_restore_error_user_action_needed,
                                installerAppTitle);

                if (unarchiveStage.getAppIcon() != null) {
                    mAppSnippet.setVisibility(View.VISIBLE);
                    mAppIcon.setImageDrawable(unarchiveStage.getAppIcon());
                    mAppLabelTextView.setText(unarchiveStage.getAppLabel());
                }
            }

            case PackageInstaller.UNARCHIVAL_ERROR_INSUFFICIENT_STORAGE -> {
                title = getString(R.string.title_restore_error_less_storage);
                positiveBtnTextResId = R.string.button_manage_apps;
                negativeBtnTextResId = R.string.button_cancel;

                String message = String.format(
                        getString(R.string.message_restore_error_less_storage),
                        Formatter.formatShortFileSize(requireContext(), requiredBytes));
                customMessage = Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY);

                if (unarchiveStage.getAppIcon() != null) {
                    mAppSnippet.setVisibility(View.VISIBLE);
                    mAppIcon.setImageDrawable(unarchiveStage.getAppIcon());
                    mAppLabelTextView.setText(unarchiveStage.getAppLabel());
                }
            }

            case PackageInstaller.UNARCHIVAL_ERROR_NO_CONNECTIVITY -> {
                title = getString(R.string.title_restore_error_offline);
                customMessage = getString(R.string.message_restore_error_offline);
            }

            case PackageInstaller.UNARCHIVAL_ERROR_INSTALLER_DISABLED -> {
                title = String.format(getString(R.string.title_restore_error_installer_disabled),
                        installerAppTitle);
                positiveBtnTextResId = R.string.button_settings;
                negativeBtnTextResId = R.string.button_cancel;
                customMessage = String.format(
                        getString(R.string.message_restore_error_installer_disabled),
                        installerAppTitle);
            }

            case PackageInstaller.UNARCHIVAL_ERROR_INSTALLER_UNINSTALLED -> {
                title = String.format(getString(R.string.title_restore_error_installer_absent),
                        installerAppTitle);
                customMessage = String.format(
                        getString(R.string.message_restore_error_installer_absent),
                        installerAppTitle);
            }

            case PackageInstaller.UNARCHIVAL_GENERIC_ERROR -> {
                title = getString(R.string.title_restore_error_generic);
                customMessage = getString(R.string.message_restore_error_generic);
            }

            default ->
                // This should never happen through normal API usage.
                    throw new IllegalArgumentException("Invalid unarchive status " + status);
        }

        // Set the title and the message
        dialog.setTitle(title);
        mCustomMessageTextView.setText(customMessage);

        // Set the positive button and the listener
        Button positiveButton = UiUtil.getAlertDialogPositiveButton(dialog);
        if (positiveButton != null) {
            if (positiveBtnTextResId == Resources.ID_NULL) {
                positiveButton.setVisibility(View.GONE);
            } else {
                positiveButton.setVisibility(View.VISIBLE);
                positiveButton.setText(positiveBtnTextResId);
                positiveButton.setOnClickListener(view -> {
                    // Set clickable of the button to false to avoid the user clicks it
                    // more than once quickly
                    view.setClickable(false);
                    mUnarchiveActionListener.handleUnarchiveErrorAction(
                            unarchiveStage.getUnarchivalStatus(),
                            unarchiveStage.getInstallerPackageName(),
                            unarchiveStage.getPendingIntent());
                });
            }
        }

        // Set the negative button and the listener
        Button negativeButton = UiUtil.getAlertDialogNegativeButton(dialog);
        if (negativeButton != null) {
            negativeButton.setText(negativeBtnTextResId);
            negativeButton.setOnClickListener(view -> {
                requireActivity().finish();
            });
        }
    }

    private void updateUserActionRequiredUI(Dialog dialog,
            UnarchiveUserActionRequired unarchiveStage) {
        mAppSnippet.setVisibility(View.VISIBLE);

        // Set app icon and label
        mAppIcon.setImageDrawable(unarchiveStage.getAppIcon());
        mAppLabelTextView.setText(unarchiveStage.getAppLabel());

        // Set title
        dialog.setTitle(R.string.title_restore);

        mCustomMessageTextView.setText(getString(R.string.message_restore,
                unarchiveStage.getInstallerTitle(requireContext())));

        // Set the positive button and the listener
        Button positiveButton = UiUtil.getAlertDialogPositiveButton(dialog);
        if (positiveButton != null) {
            positiveButton.setVisibility(View.VISIBLE);
            positiveButton.setText(R.string.button_restore);
            positiveButton.setOnClickListener(view -> {
                // Set clickable of the button to false to avoid the user clicks it
                // more than once quickly
                view.setClickable(false);
                mUnarchiveActionListener.beginUnarchive();
            });
        }

        // Set the negative button and the listener
        Button negativeButton = UiUtil.getAlertDialogNegativeButton(dialog);
        if (negativeButton != null) {
            negativeButton.setText(R.string.button_cancel);
            negativeButton.setOnClickListener(view -> {
                requireActivity().finish();
            });
        }
    }
}
