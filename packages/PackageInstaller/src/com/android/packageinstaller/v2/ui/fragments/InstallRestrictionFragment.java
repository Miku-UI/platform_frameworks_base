/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.packageinstaller.v2.model.PackageUtil.ARGS_MESSAGE;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.android.packageinstaller.R;
import com.android.packageinstaller.v2.model.InstallStage;
import com.android.packageinstaller.v2.ui.InstallActionListener;
import com.android.packageinstaller.v2.ui.UiUtil;

public class InstallRestrictionFragment extends DialogFragment {

    private static final String LOG_TAG = InstallRestrictionFragment.class.getSimpleName();
    private int mMessageResId;
    private InstallActionListener mInstallActionListener;

    public InstallRestrictionFragment() {
        // Required for DialogFragment
    }

    /**
     * Create a new instance of this fragment with necessary data set as fragment arguments
     *
     * @return an instance of the fragment
     */
    public static InstallRestrictionFragment newInstance(int messageResId) {
        Bundle args = new Bundle();
        args.putInt(ARGS_MESSAGE, messageResId);

        InstallRestrictionFragment fragment = new InstallRestrictionFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mInstallActionListener = (InstallActionListener) context;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mMessageResId = requireArguments().getInt(ARGS_MESSAGE);
        final String message = getString(mMessageResId);

        Log.i(LOG_TAG, "Creating " + LOG_TAG + "\n" + "Dialog message: " + message);

        // There is no root view here. Ok to pass null view root
        @SuppressWarnings("InflateParams")
        View dialogView = getLayoutInflater().inflate(
                UiUtil.getInstallationLayoutResId(requireContext()), null);
        TextView customMessage = dialogView.requireViewById(R.id.custom_message);
        customMessage.setText(message);
        customMessage.setVisibility(View.VISIBLE);
        customMessage.setGravity(Gravity.CENTER);

        View titleView = getLayoutInflater()
                .inflate(R.layout.install_blocked_custom_title_layout, null);

        return UiUtil.getAlertDialog(requireContext(), titleView, dialogView, R.string.button_close,
                (dialog, which) ->
                        mInstallActionListener.onNegativeResponse(InstallStage.STAGE_ABORTED));
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        mInstallActionListener.onNegativeResponse(InstallStage.STAGE_ABORTED);
    }
}
