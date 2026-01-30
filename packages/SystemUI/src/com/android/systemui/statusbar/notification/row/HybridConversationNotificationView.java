/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.systemui.statusbar.notification.row;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Flags;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewStub;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.notification.NotificationFadeAware;
import com.android.systemui.statusbar.notification.row.ui.viewmodel.ConversationAvatar;
import com.android.systemui.statusbar.notification.row.ui.viewmodel.FacePile;
import com.android.systemui.statusbar.notification.row.ui.viewmodel.SingleIcon;

/**
 * A hybrid view which may contain information about one or more conversations.
 */
public class HybridConversationNotificationView extends HybridNotificationView {

    private static final int MAX_SUMMARIZATION_LINES = 1;
    private ImageView mConversationIconView;
    private TextView mConversationSenderName;
    private ViewStub mConversationFacePileStub;
    private View mConversationFacePile;
    private int mSingleAvatarSize;
    private int mFacePileSize;
    private int mFacePileAvatarSize;
    private int mFacePileProtectionWidth;

    public HybridConversationNotificationView(Context context) {
        this(context, null);
    }

    public HybridConversationNotificationView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HybridConversationNotificationView(
            Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public HybridConversationNotificationView(
            Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mConversationIconView = requireViewById(com.android.internal.R.id.conversation_icon);
        mConversationFacePileStub =
                requireViewById(com.android.internal.R.id.conversation_face_pile);
        mConversationSenderName = requireViewById(R.id.conversation_notification_sender);
        applyTextColor(mConversationSenderName, mSecondaryTextColor);
        if (Flags.notificationsRedesignTemplates()) {
            mFacePileSize = getResources()
                    .getDimensionPixelSize(R.dimen.notification_2025_single_line_face_pile_size);
            mFacePileAvatarSize = getResources()
                    .getDimensionPixelSize(
                            R.dimen.notification_2025_single_line_face_pile_avatar_size);
            mSingleAvatarSize = getResources()
                    .getDimensionPixelSize(R.dimen.notification_2025_single_line_avatar_size);
        } else {
            mFacePileSize = getResources()
                    .getDimensionPixelSize(R.dimen.conversation_single_line_face_pile_size);
            mFacePileAvatarSize = getResources()
                    .getDimensionPixelSize(R.dimen.conversation_single_line_face_pile_avatar_size);
            mSingleAvatarSize = getResources()
                    .getDimensionPixelSize(R.dimen.conversation_single_line_avatar_size);
        }
        mFacePileProtectionWidth = getResources().getDimensionPixelSize(
                R.dimen.conversation_single_line_face_pile_protection_width);
        mTransformationHelper.setCustomTransformation(
                new FadeOutAndDownWithTitleTransformation(mConversationSenderName),
                mConversationSenderName.getId());
        mTransformationHelper.addViewTransformingToSimilar(mConversationIconView);
        mTransformationHelper.addTransformedView(mConversationSenderName);
    }

    /**
     * Set the avatar using ConversationAvatar from SingleLineViewModel
     *
     * @param conversationAvatar the icon needed for a single-line conversation view, it should be
     *                           either an instance of SingleIcon or FacePile
     */
    public void setAvatar(@NonNull ConversationAvatar conversationAvatar) {
        if (conversationAvatar instanceof SingleIcon) {
            SingleIcon avatar = (SingleIcon) conversationAvatar;
            if (mConversationFacePile != null) mConversationFacePile.setVisibility(GONE);
            mConversationIconView.setVisibility(VISIBLE);
            mConversationIconView.setImageDrawable(avatar.getIconDrawable());
            setSize(mConversationIconView, mSingleAvatarSize);
            return;
        }

        // If conversationAvatar is not a SingleIcon, it should be a FacePile.
        // Bind the face pile with it.
        FacePile facePileModel = (FacePile) conversationAvatar;
        mConversationIconView.setVisibility(GONE);
        // Inflate mConversationFacePile from ViewStub
        if (mConversationFacePile == null) {
            mConversationFacePile = mConversationFacePileStub.inflate();
        }
        mConversationFacePile.setVisibility(VISIBLE);

        ImageView facePileBottomBg = mConversationFacePile.requireViewById(
                com.android.internal.R.id.conversation_face_pile_bottom_background);
        ImageView facePileBottom = mConversationFacePile.requireViewById(
                com.android.internal.R.id.conversation_face_pile_bottom);
        ImageView facePileTop = mConversationFacePile.requireViewById(
                com.android.internal.R.id.conversation_face_pile_top);

        int bottomBackgroundColor = facePileModel.getBottomBackgroundColor();
        facePileBottomBg.setImageTintList(ColorStateList.valueOf(bottomBackgroundColor));

        facePileBottom.setImageDrawable(facePileModel.getBottomIconDrawable());
        facePileTop.setImageDrawable(facePileModel.getTopIconDrawable());

        setSize(mConversationFacePile, mFacePileSize);
        setSize(facePileBottom, mFacePileAvatarSize);
        setSize(facePileTop, mFacePileAvatarSize);
        setSize(facePileBottomBg, mFacePileAvatarSize + 2 * mFacePileProtectionWidth);

        mTransformationHelper.addViewTransformingToSimilar(facePileTop);
        mTransformationHelper.addViewTransformingToSimilar(facePileBottom);
        mTransformationHelper.addViewTransformingToSimilar(facePileBottomBg);

    }

    /**
     * bind the text views
     */
    public void setText(
            CharSequence titleText,
            CharSequence contentText,
            CharSequence conversationSenderName,
            @Nullable CharSequence summarization
    ) {
        if (!TextUtils.isEmpty(summarization)) {
            mConversationSenderName.setVisibility(GONE);
            contentText = summarization;
            mTextView.setSingleLine(false);
            mTextView.setMaxLines(MAX_SUMMARIZATION_LINES);
            mTextView.setTypeface(Typeface.create("variable-body-medium", Typeface.ITALIC));
        } else {
            mTextView.setSingleLine(true);
            if (conversationSenderName == null) {
                mConversationSenderName.setVisibility(GONE);
            } else {
                mConversationSenderName.setVisibility(VISIBLE);
                mConversationSenderName.setText(conversationSenderName);
            }
        }
        super.bind(/* title = */ titleText, /* text = */ contentText,
                /* stripSpans = */ TextUtils.isEmpty(summarization));
    }

    private static void setSize(View view, int size) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        lp.width = size;
        lp.height = size;
        view.setLayoutParams(lp);
    }

    /**
     * Apply the faded state as a layer type change to the face pile view which needs to have
     * overlapping contents render precisely.
     */
    @Override
    public void setNotificationFaded(boolean faded) {
        super.setNotificationFaded(faded);
        NotificationFadeAware.setLayerTypeForFaded(mConversationFacePile, faded);
    }

    @VisibleForTesting
    TextView getConversationSenderNameView() {
        return mConversationSenderName;
    }
}
