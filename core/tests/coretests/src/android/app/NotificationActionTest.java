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

import static com.google.common.truth.Truth.assertThat;

import static java.util.Objects.requireNonNull;

import android.app.Notification.Action;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.FlagsParameterization;
import android.platform.test.flag.junit.SetFlagsRule;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.R;
import com.android.internal.widget.EmphasizedNotificationButton;
import com.android.internal.widget.NotificationActionListLayout;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

import java.util.List;

@RunWith(ParameterizedAndroidJunit4.class)
@Presubmit
public class NotificationActionTest {

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private Context mContext;
    private Notification.Colors mDefaultColors;

    private Icon mIcon;
    private PendingIntent mPendingIntent;
    private Person mPerson;

    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return FlagsParameterization.allCombinationsOf(Flags.FLAG_API_NOTIFICATION_ACTION_CUSTOM);
    }

    public NotificationActionTest(FlagsParameterization flags) {
        mSetFlagsRule.setFlagsParameterization(flags);
    }

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        // Default values for notifications.
        mIcon = Icon.createWithResource(mContext, R.drawable.sym_def_app_icon);
        mPendingIntent = PendingIntent.getActivity(mContext, 0, new Intent("test1"),
                PendingIntent.FLAG_IMMUTABLE);
        mPerson = new Person.Builder().setName("Someone").build();

        mDefaultColors = new Notification.Colors();
        boolean nightMode = (mContext.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        mDefaultColors.resolvePalette(mContext, Notification.COLOR_DEFAULT, false, nightMode);
    }

    @Test
    public void makeExpandedContentView_limitsActionsToThree() {
        Notification.Builder n = new Notification.Builder(mContext, "channel")
                .addAction(new Action.Builder(mIcon, "Action 1", mPendingIntent).build())
                .addAction(new Action.Builder(mIcon, "Action 2", mPendingIntent).build())
                .addAction(new Action.Builder(mIcon, "Action 3", mPendingIntent).build())
                .addAction(new Action.Builder(mIcon, "Action 4", mPendingIntent).build());

        NotificationActionListLayout actionsLayout = makeActionsLayout(n);

        assertThat(actionsLayout.getChildCount()).isEqualTo(3);
    }

    @Test
    public void makeExpandedContentView_actionsHaveIndexTag() {
        Notification.Builder n = new Notification.Builder(mContext, "channel")
                .addAction(new Action.Builder(mIcon, "Action 1", mPendingIntent).build())
                .addAction(new Action.Builder(mIcon, "Action 2", mPendingIntent).build());

        NotificationActionListLayout actionsLayout = makeActionsLayout(n);
        assertThat(actionsLayout.getChildCount()).isEqualTo(2);
        assertThat(actionsLayout.getChildAt(0).getTag(R.id.notification_action_index_tag))
                .isEqualTo(0);
        assertThat(actionsLayout.getChildAt(1).getTag(R.id.notification_action_index_tag))
                .isEqualTo(1);
    }

    @Test
    public void makeExpandedContentView_skipsContextualActions() {
        Notification.Builder n = new Notification.Builder(mContext, "channel")
                .addAction(new Action.Builder(mIcon, "Normal 1", mPendingIntent).build())
                .addAction(new Action.Builder(mIcon, "Contextual A", mPendingIntent)
                        .setContextual(true).build())
                .addAction(new Action.Builder(mIcon, "Contextual B", mPendingIntent)
                        .setContextual(true).build())
                .addAction(new Action.Builder(mIcon, "Normal 2", mPendingIntent).build());

        NotificationActionListLayout actionsLayout = makeActionsLayout(n);
        assertThat(actionsLayout.getChildCount()).isEqualTo(2);
        assertThat(((Button) actionsLayout.getChildAt(0)).getText().toString())
                .isEqualTo("Normal 1");
        assertThat(actionsLayout.getChildAt(0).getTag(R.id.notification_action_index_tag))
                .isEqualTo(0);
        assertThat(((Button) actionsLayout.getChildAt(1)).getText().toString())
                .isEqualTo("Normal 2");
        assertThat(actionsLayout.getChildAt(1).getTag(R.id.notification_action_index_tag))
                .isEqualTo(3); // Skipped Contextual A & B.
    }

    @Test
    public void makeExpandedContentView_callStyle_hasDefaultActionsAndIndexTags() {
        Notification.Builder n = new Notification.Builder(mContext, "channel")
                .addAction(new Action.Builder(mIcon, "Something", mPendingIntent).build())
                .setStyle(Notification.CallStyle.forIncomingCall(mPerson, mPendingIntent,
                        mPendingIntent));

        NotificationActionListLayout actionsLayout = makeActionsLayout(n);

        // Button order is: Decline, <something>, Answer. Icons are glued before text.
        assertThat(actionsLayout.getChildCount()).isEqualTo(3);
        assertThat(((Button) actionsLayout.getChildAt(0)).getText().toString())
                .contains("Decline");
        assertThat(actionsLayout.getChildAt(0).getTag(R.id.notification_action_index_tag))
                .isEqualTo(0);
        assertThat(((Button) actionsLayout.getChildAt(1)).getText().toString())
                .contains("Something");
        assertThat(actionsLayout.getChildAt(1).getTag(R.id.notification_action_index_tag))
                .isEqualTo(1);
        assertThat(((Button) actionsLayout.getChildAt(2)).getText().toString())
                .contains("Answer");
        assertThat(actionsLayout.getChildAt(2).getTag(R.id.notification_action_index_tag))
                .isEqualTo(2);
    }

    @Test
    @EnableFlags(Flags.FLAG_API_NOTIFICATION_ACTION_CUSTOM)
    public void makeExpandedContentView_actionWithNullText_skipped() {
        Notification.Builder n = new Notification.Builder(mContext, "channel")
                .addAction(new Action.Builder(mIcon, "Action 1", mPendingIntent).build())
                .addAction(new Action.Builder(mIcon, null, mPendingIntent).build())
                .addAction(new Action.Builder(mIcon, "Action 3", mPendingIntent).build());

        NotificationActionListLayout actionsLayout = makeActionsLayout(n);

        assertThat(actionsLayout.getChildCount()).isEqualTo(2);
        assertThat(((Button) actionsLayout.getChildAt(0)).getText().toString())
                .isEqualTo("Action 1");
        assertThat(((Button) actionsLayout.getChildAt(1)).getText().toString())
                .isEqualTo("Action 3");
    }

    @Test
    @EnableFlags(Flags.FLAG_API_NOTIFICATION_ACTION_CUSTOM)
    public void makeExpandedContentView_mediaStyleActionWithNullText_kept() {
        Notification.Builder n = new Notification.Builder(mContext, "channel")
                .setStyle(new Notification.MediaStyle())
                .addAction(new Action.Builder(mIcon, "Action 1", mPendingIntent).build())
                .addAction(new Action.Builder(mIcon, null, mPendingIntent).build())
                .addAction(new Action.Builder(mIcon, "Action 3", mPendingIntent).build());

        View mediaLayout = makeLayout(n);

        assertThat(mediaLayout.findViewById(R.id.action0).getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mediaLayout.findViewById(R.id.action1).getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mediaLayout.findViewById(R.id.action2).getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mediaLayout.findViewById(R.id.action3).getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    @EnableFlags(Flags.FLAG_API_NOTIFICATION_ACTION_CUSTOM)
    public void makeExpandedContentView_actionWithEmptyText_skipped() {
        Notification.Builder n = new Notification.Builder(mContext, "channel")
                .addAction(new Action.Builder(mIcon, "Action 1", mPendingIntent).build())
                .addAction(new Action.Builder(mIcon, "  ", mPendingIntent).build())
                .addAction(new Action.Builder(mIcon, "Action 3", mPendingIntent).build());

        NotificationActionListLayout actionsLayout = makeActionsLayout(n);

        assertThat(actionsLayout.getChildCount()).isEqualTo(2);
        assertThat(((Button) actionsLayout.getChildAt(0)).getText().toString())
                .isEqualTo("Action 1");
        assertThat(((Button) actionsLayout.getChildAt(1)).getText().toString())
                .isEqualTo("Action 3");
    }

    @Test
    @EnableFlags(Flags.FLAG_API_NOTIFICATION_ACTION_CUSTOM)
    public void makeExpandedContentView_noncontextualActionsSkipped_noMoreAreAllowed() {
        Notification.Builder n = new Notification.Builder(mContext, "channel")
                .setFlag(Notification.FLAG_PROMOTED_ONGOING, true)
                // Skipped for null title
                .addAction(new Action.Builder(mIcon, null, mPendingIntent).build())
                // Skipped for RemoteInput in promoted-ongoing
                .addAction(new Action.Builder(mIcon, "Remote", mPendingIntent)
                        .addRemoteInput(new RemoteInput.Builder("x").build()).build())
                // Valid and fits
                .addAction(new Action.Builder(mIcon, "Valid 1", mPendingIntent).build())
                // Valid and would fit, but won't be used
                .addAction(new Action.Builder(mIcon, "Valid 2", mPendingIntent).build());

        NotificationActionListLayout actionsLayout = makeActionsLayout(n);

        assertThat(actionsLayout.getChildCount()).isEqualTo(1);

        assertThat(((Button) actionsLayout.getChildAt(0)).getText().toString())
                .isEqualTo("Valid 1");
    }

    @Test
    @EnableFlags(Flags.FLAG_API_NOTIFICATION_ACTION_CUSTOM)
    public void makeExpandedContentView_actionWithIconOnly_showsOnlyIcon() {
        Notification.Builder n = new Notification.Builder(mContext, "channel")
                .setFlag(Notification.FLAG_PROMOTED_ONGOING, true)
                .addAction(
                        new Action.Builder(mIcon, "Hidden text", mPendingIntent)
                                .setStyleHint(Action.STYLE_ICON_ONLY)
                                .build());

        NotificationActionListLayout actionsLayout = makeActionsLayout(n);
        Spanned buttonText = (Spanned) ((Button) actionsLayout.getChildAt(0)).getText();

        assertThat(buttonText.getSpans(0, buttonText.length(), ImageSpan.class)).hasLength(1);
        assertThat(buttonText.toString()).isEqualTo("\ufffd"); // Replacement character
    }

    @Test
    @EnableFlags(Flags.FLAG_API_NOTIFICATION_ACTION_CUSTOM)
    public void makeExpandedContentView_actionWithIconOnlyButNoIcon_showsText() {
        Notification.Builder n = new Notification.Builder(mContext, "channel")
                .setFlag(Notification.FLAG_PROMOTED_ONGOING, true)
                .addAction(
                        new Action.Builder(/* icon= */ null, "Hidden text?", mPendingIntent)
                                .setStyleHint(Action.STYLE_ICON_ONLY)
                                .build());

        NotificationActionListLayout actionsLayout = makeActionsLayout(n);

        assertThat(((Button) actionsLayout.getChildAt(0)).getText().toString())
                .isEqualTo("Hidden text?");
    }

    @Test
    @EnableFlags(Flags.FLAG_API_NOTIFICATION_ACTION_CUSTOM)
    public void makeExpandedContentView_callStyle_usesCustomActionColor() {
        CharSequence redText = new SpannableStringBuilder().append("Red action",
                new ForegroundColorSpan(Color.RED), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        Notification.Builder n = new Notification.Builder(mContext, "channel")
                .setStyle(Notification.CallStyle.forIncomingCall(mPerson, mPendingIntent,
                        mPendingIntent))
                .addAction(
                        new Action.Builder(/* icon= */ null, redText, mPendingIntent)
                                .setEmphasisHint(Action.EMPHASIS_PRIMARY)
                                .build());

        NotificationActionListLayout actionsLayout = makeActionsLayout(n);

        EmphasizedNotificationButton primaryButton =
                (EmphasizedNotificationButton) actionsLayout.getChildAt(1);
        assertThat(primaryButton.getText().toString()).contains("Red action");
        assertThat(primaryButton.getButtonBackground().getDefaultColor()).isEqualTo(Color.RED);
    }

    @Test
    @EnableFlags(Flags.FLAG_API_NOTIFICATION_ACTION_CUSTOM)
    public void makeExpandedContentView_promotedOngoing_doesNotUseCustomActionColor() {
        CharSequence redText = new SpannableStringBuilder().append("Not red action",
                new ForegroundColorSpan(Color.RED), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        Notification.Builder n = new Notification.Builder(mContext, "channel")
                .setFlag(Notification.FLAG_PROMOTED_ONGOING, true)
                .addAction(
                        new Action.Builder(/* icon= */ null, redText, mPendingIntent)
                                .setEmphasisHint(Action.EMPHASIS_PRIMARY)
                                .build())
                // Needs a second action so that the first can be primary.
                .addAction(new Action.Builder(null, "Another", mPendingIntent).build());

        NotificationActionListLayout actionsLayout = makeActionsLayout(n);

        EmphasizedNotificationButton primaryButton =
                (EmphasizedNotificationButton) actionsLayout.getChildAt(0);
        assertThat(primaryButton.getText().toString()).contains("Not red action");
        assertThat(primaryButton.getButtonBackground().getDefaultColor())
                .isEqualTo(mDefaultColors.getPrimaryEmphasisBackground());
    }

    private NotificationActionListLayout makeActionsLayout(Notification.Builder builder) {
        return requireNonNull(makeLayout(builder).findViewById(R.id.actions));
    }

    private View makeLayout(Notification.Builder builder) {
        RemoteViews remoteViews = builder.getStyle() != null
                ? builder.getStyle().makeExpandedContentView()
                : builder.createBigContentView();

        FrameLayout container = new FrameLayout(mContext);
        container.addView(remoteViews.apply(mContext, container));
        container.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        return requireNonNull(container.getChildAt(0));
    }
}
