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

package com.android.server.personalcontext.notifications;

import static android.app.NotificationManager.IMPORTANCE_DEFAULT;

import static com.android.server.personalcontext.util.InsightUtils.fakePublishInsight;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.app.RemoteAction;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Icon;
import android.service.notification.Adjustment;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.service.personalcontext.hint.ContextHintTestUtils;
import android.service.personalcontext.hint.NotificationEvent.NotificationEnqueuedEvent;
import android.service.personalcontext.hint.NotificationHint;
import android.service.personalcontext.hint.PublishedContextHint;
import android.service.personalcontext.insight.ActionableInsight;
import android.service.personalcontext.insight.BundleInsight;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.DisplayInsight;
import android.service.personalcontext.insight.InsightActionDetails;
import android.service.personalcontext.insight.InsightCollection;
import android.service.personalcontext.insight.InsightDisplayDetails;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.notification.NotificationManagerInternal;
import com.android.server.personalcontext.notifications.ContextActionResolver.ActionType;
import com.android.server.personalcontext.notifications.ContextActionResolver.ResolutionResult;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class NotificationActionRendererTest {
    private Context mContext;
    private NotificationActionRenderer mRenderer;
    private StatusBarNotification mSbn1;
    private StatusBarNotification mSbn2;

    @Mock private NotificationManagerInternal mNotificationManagerInternal;
    @Mock private NotificationActionFactory mNotificationActionFactory;

    private static final NotificationChannel NOTIFICATION_CHANNEL =
            new NotificationChannel("id", "name", IMPORTANCE_DEFAULT);
    private static final NotificationListenerService.RankingMap RANKING_MAP =
            new NotificationListenerService.RankingMap(new NotificationListenerService.Ranking[0]);

    private static final String TEST_APP_NAME = "Test App";
    private static final int TEST_APP_RESOURCE_ID = 1234;

    private static final Icon FAKE_ICON = Icon.createWithResource("pkg", 123);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mRenderer =
                new NotificationActionRenderer(
                        mNotificationManagerInternal, mNotificationActionFactory);
        mSbn1 = createStatusBarNotification("pkg", 0, "tag");
        mSbn2 = createStatusBarNotification("pkg2", 1, "tag2");
    }

    @Test
    public void testRenderer_hasCanReceiveNotificationInsightsProperty() {
        assertThat(mRenderer.hasProperties(
                NotificationActionRenderer.PROPERTY_CAN_RECEIVE_NOTIFICATION_INSIGHTS))
                .isTrue();
    }

    @Test
    public void testRender_notActionableInsight_noAction() {
        ContextInsight insight = new BundleInsight.Builder().build();

        mRenderer.render(fakePublishInsight(insight), null);

        verify(mNotificationManagerInternal, never()).requestSystemAdjustments(any());
    }

    @Test
    public void testRender_actionableInsightWithNoNotificationHint_noAction() {
        InsightDisplayDetails displayDetails =
                new InsightDisplayDetails.Builder("title", FAKE_ICON).build();
        InsightActionDetails actionDetails =
                new InsightActionDetails.Builder().setPendingIntent(mock(PendingIntent.class))
                        .build();
        ActionableInsight insight =
                new ActionableInsight.Builder(actionDetails, displayDetails).build();

        mRenderer.render(fakePublishInsight(insight), null);

        verify(mNotificationManagerInternal, never()).requestSystemAdjustments(any());
    }

    @Test
    public void testRender_actionableInsightWithNotificationHint_requestsAdjustment()
            throws GeneralSecurityException {
        mockActionFactoryCreatesAction();
        ActionableInsight insight = createActionableInsight(mSbn1, "Test Title", FAKE_ICON);
        List<Adjustment> adjustments = renderAndCaptureAdjustments(insight);

        assertThat(adjustments).hasSize(1);
        ArrayList<Notification.Action> actions = getActionsFromAdjustment(adjustments.get(0));
        assertThat(actions).hasSize(1);
        Notification.Action action = actions.get(0);

        assertThat(action.title.toString()).isEqualTo("Test Title");
    }

    @Test
    public void testRender_actionableInsightWithRemoteAction_requestsAdjustment()
            throws GeneralSecurityException {
        mockActionFactoryCreatesAction();
        ActionableInsight insight =
                createActionableInsightWithRemoteAction("Test Title", FAKE_ICON);
        List<Adjustment> adjustments = renderAndCaptureAdjustments(insight);

        assertThat(adjustments).hasSize(1);
        ArrayList<Notification.Action> actions = getActionsFromAdjustment(adjustments.get(0));
        assertThat(actions).hasSize(1);
        Notification.Action action = actions.get(0);

        assertThat(action.title.toString()).isEqualTo("Test Title");
    }

    @Test
    public void testRender_displayInsight_requestsAdjustmentWithTextReplies() throws Exception {
        DisplayInsight insight = createDisplayInsight(mSbn1, "Test Reply", FAKE_ICON);
        List<Adjustment> adjustments = renderAndCaptureAdjustments(insight);

        assertThat(adjustments).hasSize(1);
        ArrayList<CharSequence> replies = getRepliesFromAdjustment(adjustments.get(0));
        assertThat(replies).isNotNull();
        assertThat(replies).hasSize(1);
        assertThat(replies.get(0).toString()).isEqualTo("Test Reply");
        ArrayList<Notification.Action> actions = getActionsFromAdjustment(adjustments.get(0));
        assertThat(actions).isNull();
    }

    @Test
    public void testRender_tooManyActionsForOneNotification_dropsExtraActions()
            throws GeneralSecurityException {
        mockActionFactoryCreatesAction();
        InsightCollection.Builder builder = new InsightCollection.Builder();
        for (int i = 0; i < NotificationActionRenderer.MAX_NOTIFICATION_ACTIONS + 100; i++) {
            builder.addInsight(createActionableInsight(mSbn1, "Title " + i, FAKE_ICON));
        }
        List<Adjustment> adjustments = renderAndCaptureAdjustments(builder.build());

        assertThat(adjustments).hasSize(1);
        ArrayList<Notification.Action> actions = getActionsFromAdjustment(adjustments.get(0));
        assertThat(actions).hasSize(NotificationActionRenderer.MAX_NOTIFICATION_ACTIONS);
        verify(
                        mNotificationActionFactory,
                        times(NotificationActionRenderer.MAX_NOTIFICATION_ACTIONS))
                .createNotificationAction(any(ActionableInsight.class));
    }

    @Test
    public void testRender_tooManyReplies_dropsExtraReplies() throws Exception {
        InsightCollection.Builder builder = new InsightCollection.Builder();
        for (int i = 0; i < NotificationActionRenderer.MAX_TEXT_REPLIES + 1; i++) {
            builder.addInsight(createDisplayInsight(mSbn1, "Reply " + i, null));
        }
        List<Adjustment> adjustments = renderAndCaptureAdjustments(builder.build());

        assertThat(adjustments).hasSize(1);
        ArrayList<CharSequence> replies = getRepliesFromAdjustment(adjustments.get(0));
        assertThat(replies).hasSize(NotificationActionRenderer.MAX_TEXT_REPLIES);
    }

    @Test
    public void testRender_noTitleInsight_usesDefaultTitle() throws GeneralSecurityException {
        mockActionFactoryCreatesAction();
        ActionableInsight insight = createActionableInsight(mSbn1, "Default Title", FAKE_ICON);
        List<Adjustment> adjustments = renderAndCaptureAdjustments(insight);

        assertThat(adjustments).hasSize(1);
        ArrayList<Notification.Action> actions = getActionsFromAdjustment(adjustments.get(0));
        assertThat(actions).hasSize(1);
        Notification.Action action = actions.get(0);

        assertThat(action.title.toString()).isEqualTo("Default Title");
    }

    @Test
    public void testRender_cannotResolveIntent_noAction() throws GeneralSecurityException {
        ActionableInsight insight = createActionableInsight(mSbn1, "Test Title", FAKE_ICON);
        when(mNotificationActionFactory.createNotificationAction(any(ActionableInsight.class)))
                .thenReturn(null);

        mRenderer.render(fakePublishInsight(insight), null);

        verify(mNotificationManagerInternal, never()).requestSystemAdjustments(any());
    }

    @Test
    public void testRender_insightCollectionWithSingleActionableInsight_requestsAdjustment()
            throws GeneralSecurityException {
        mockActionFactoryCreatesAction();
        ActionableInsight insight = createActionableInsight(mSbn1, "Test Title", FAKE_ICON);
        InsightCollection collection = new InsightCollection.Builder().addInsight(insight).build();
        List<Adjustment> adjustments = renderAndCaptureAdjustments(collection);

        assertThat(adjustments).hasSize(1);
        ArrayList<Notification.Action> actions = getActionsFromAdjustment(adjustments.get(0));
        assertThat(actions).hasSize(1);
    }

    @Test
    public void
            testRender_insightCollectionWithMultipleActionableInsightsForSameNotification_requestsSingleAdjustment()
                    throws GeneralSecurityException {
        mockActionFactoryCreatesAction();
        ActionableInsight insight1 = createActionableInsight(mSbn1, "Title 1", FAKE_ICON);
        ActionableInsight insight2 = createActionableInsight(mSbn1, "Title 2", FAKE_ICON);
        InsightCollection collection =
                new InsightCollection.Builder().addInsight(insight1).addInsight(insight2).build();
        List<Adjustment> adjustments = renderAndCaptureAdjustments(collection);

        assertThat(adjustments).hasSize(1);
        ArrayList<Notification.Action> actions = getActionsFromAdjustment(adjustments.get(0));
        assertThat(actions).hasSize(2);
    }

    @Test
    public void
            testRender_insightCollectionWithMultipleActionableInsightsForDifferentNotifications_requestsMultipleAdjustments()
                    throws GeneralSecurityException {
        mockActionFactoryCreatesAction();
        ActionableInsight insight1 = createActionableInsight(mSbn1, "Title 1", FAKE_ICON);
        ActionableInsight insight2 = createActionableInsight(mSbn2, "Title 2", FAKE_ICON);
        InsightCollection collection =
                new InsightCollection.Builder().addInsight(insight1).addInsight(insight2).build();
        List<Adjustment> adjustments = renderAndCaptureAdjustments(collection);

        assertThat(adjustments).hasSize(2);

        Adjustment adjustment1 = findAdjustment(adjustments, mSbn1.getKey());
        assertThat(adjustment1).isNotNull();
        assertThat(getActionsFromAdjustment(adjustment1)).hasSize(1);

        Adjustment adjustment2 = findAdjustment(adjustments, mSbn2.getKey());
        assertThat(adjustment2).isNotNull();
        assertThat(getActionsFromAdjustment(adjustment2)).hasSize(1);
    }

    @Test
    public void testRender_insightCollectionWithDisplayInsight_requestsAdjustment()
            throws Exception {
        DisplayInsight insight = createDisplayInsight(mSbn1, "Test Reply", FAKE_ICON);
        InsightCollection collection = new InsightCollection.Builder().addInsight(insight).build();
        List<Adjustment> adjustments = renderAndCaptureAdjustments(collection);

        assertThat(adjustments).hasSize(1);
        ArrayList<CharSequence> replies = getRepliesFromAdjustment(adjustments.get(0));
        assertThat(replies).isNotNull();
        assertThat(replies).hasSize(1);
        assertThat(replies.get(0).toString()).isEqualTo("Test Reply");
    }

    @Test
    public void
            testRender_mixedInsightsSameNotification_requestsSingleAdjustmentWithActionsAndReplies()
                    throws Exception {
        mockActionFactoryCreatesAction();
        ActionableInsight actionableInsight =
                createActionableInsight(mSbn1, "Test Title", FAKE_ICON);
        DisplayInsight displayInsight = createDisplayInsight(mSbn1, "Test Reply", null);
        InsightCollection collection =
                new InsightCollection.Builder()
                        .addInsight(actionableInsight)
                        .addInsight(displayInsight)
                        .build();

        List<Adjustment> adjustments = renderAndCaptureAdjustments(collection);

        assertThat(adjustments).hasSize(1);
        Adjustment adjustment = adjustments.get(0);

        ArrayList<Notification.Action> actions = getActionsFromAdjustment(adjustment);
        assertThat(actions).isNotNull();
        assertThat(actions).hasSize(1);
        assertThat(actions.get(0).title.toString()).isEqualTo("Test Title");

        ArrayList<CharSequence> replies = getRepliesFromAdjustment(adjustment);
        assertThat(replies).isNotNull();
        assertThat(replies).hasSize(1);
        assertThat(replies.get(0).toString()).isEqualTo("Test Reply");
    }

    @Test
    public void testRender_mixedInsightsDifferentNotifications_requestsMultipleAdjustments()
            throws Exception {
        mockActionFactoryCreatesAction();
        ActionableInsight actionableInsight =
                createActionableInsight(mSbn1, "Test Title", FAKE_ICON);
        DisplayInsight displayInsight = createDisplayInsight(mSbn2, "Test Reply", null);
        InsightCollection collection =
                new InsightCollection.Builder()
                        .addInsight(actionableInsight)
                        .addInsight(displayInsight)
                        .build();

        List<Adjustment> adjustments = renderAndCaptureAdjustments(collection);
        assertThat(adjustments).hasSize(2);

        Adjustment adjustment1 = findAdjustment(adjustments, mSbn1.getKey());
        assertThat(adjustment1).isNotNull();
        assertThat(getActionsFromAdjustment(adjustment1)).hasSize(1);
        assertThat(getRepliesFromAdjustment(adjustment1)).isNull();

        Adjustment adjustment2 = findAdjustment(adjustments, mSbn2.getKey());
        assertThat(adjustment2).isNotNull();
        assertThat(getActionsFromAdjustment(adjustment2)).isNull();
        assertThat(getRepliesFromAdjustment(adjustment2)).hasSize(1);
    }

    @Test
    public void testRender_insightCollectionWithNoActionableInsights_noAction() {
        InsightCollection collection =
                new InsightCollection.Builder()
                        .addInsight(new BundleInsight.Builder().build())
                        .build();
        mRenderer.render(fakePublishInsight(collection), null);
        verify(mNotificationManagerInternal, never()).requestSystemAdjustments(any());
    }

    @Test
    public void testRender_nestedInsightCollection_requestsSingleAdjustment()
            throws GeneralSecurityException {
        mockActionFactoryCreatesAction();
        ActionableInsight insight = createActionableInsight(mSbn1, "Test Title", FAKE_ICON);
        InsightCollection innerCollection =
                new InsightCollection.Builder().addInsight(insight).build();
        InsightCollection outerCollection =
                new InsightCollection.Builder().addInsight(innerCollection).build();
        List<Adjustment> adjustments = renderAndCaptureAdjustments(outerCollection);

        assertThat(adjustments).hasSize(1);
        ArrayList<Notification.Action> actions = getActionsFromAdjustment(adjustments.get(0));
        assertThat(actions).hasSize(1);
        Notification.Action action = actions.get(0);
        assertThat(action.title.toString()).isEqualTo("Test Title");
    }

    private StatusBarNotification createStatusBarNotification(String pkg, int id, String tag) {
        return new StatusBarNotification(
                pkg,
                "opPkg",
                id,
                tag,
                /* uid= */ 0,
                /* initialPid= */ 0,
                new Notification(),
                mContext.getUser(),
                /* overrideGroupKey= */ null,
                /* postTime= */ 0);
    }

    private ActionableInsight createActionableInsight(
            StatusBarNotification sbn, @Nullable String title, @Nullable Icon icon)
            throws GeneralSecurityException {
        InsightActionDetails actionDetails =
                new InsightActionDetails.Builder().setPendingIntent(mock(PendingIntent.class))
                        .build();
        return createActionableInsight(sbn, title, icon, actionDetails);
    }

    private ActionableInsight createActionableInsightWithRemoteAction(
            @Nullable String title, @Nullable Icon icon) throws GeneralSecurityException {
        InsightActionDetails actionDetails =
                new InsightActionDetails.Builder()
                        .setRemoteAction(
                                new RemoteAction(
                                        icon,
                                        title,
                                        "contentDescription",
                                        PendingIntent.getActivity(
                                                mContext,
                                                /* requestCode= */ 0,
                                                new Intent("ACTION"),
                                                PendingIntent.FLAG_IMMUTABLE)))
                        .build();
        return createActionableInsight(mSbn1, title, icon, actionDetails);
    }

    private ActionableInsight createActionableInsight(
            StatusBarNotification sbn,
            @Nullable String title,
            @Nullable Icon icon,
            InsightActionDetails actionDetails)
            throws GeneralSecurityException {
        NotificationHint hint =
                new NotificationHint.Builder(
                                new NotificationEnqueuedEvent(
                                        sbn, NOTIFICATION_CHANNEL, RANKING_MAP))
                        .build();
        PublishedContextHint signedHint =
                new PublishedContextHint.Builder(
                                hint, ContextHintTestUtils.generateSignedHintKey())
                        .build();
        InsightDisplayDetails.Builder displayDetailsBuilder;
        if (title != null && icon != null) {
            displayDetailsBuilder = new InsightDisplayDetails.Builder(title, icon);
        } else if (title != null) {
            displayDetailsBuilder = new InsightDisplayDetails.Builder(title);
        } else {
            displayDetailsBuilder = new InsightDisplayDetails.Builder(icon);
        }
        InsightDisplayDetails displayDetails = displayDetailsBuilder.build();
        return new ActionableInsight.Builder(actionDetails, displayDetails)
                .addOriginHint(signedHint)
                .build();
    }

    private DisplayInsight createDisplayInsight(
            StatusBarNotification sbn, @Nullable String title, @Nullable Icon icon)
            throws GeneralSecurityException {
        InsightDisplayDetails.Builder displayDetailsBuilder;
        if (title != null && icon != null) {
            displayDetailsBuilder = new InsightDisplayDetails.Builder(title, icon);
        } else if (title != null) {
            displayDetailsBuilder = new InsightDisplayDetails.Builder(title);
        } else {
            displayDetailsBuilder = new InsightDisplayDetails.Builder(icon);
        }
        InsightDisplayDetails displayDetails = displayDetailsBuilder.build();

        NotificationHint hint =
                new NotificationHint.Builder(
                                new NotificationEnqueuedEvent(
                                        sbn, NOTIFICATION_CHANNEL, RANKING_MAP))
                        .build();
        PublishedContextHint signedHint =
                new PublishedContextHint.Builder(
                                hint, ContextHintTestUtils.generateSignedHintKey())
                        .build();

        return new DisplayInsight.Builder(displayDetails).addOriginHint(signedHint).build();
    }

    private List<Adjustment> renderAndCaptureAdjustments(ContextInsight insight) {
        mRenderer.render(fakePublishInsight(insight), null);

        ArgumentCaptor<List<Adjustment>> captor = ArgumentCaptor.forClass(List.class);
        verify(mNotificationManagerInternal).requestSystemAdjustments(captor.capture());
        return captor.getValue();
    }

    private ArrayList<Notification.Action> getActionsFromAdjustment(Adjustment adjustment) {
        return adjustment
                .getSignals()
                .getParcelableArrayList(
                        Adjustment.KEY_CONTEXTUAL_ACTIONS, Notification.Action.class);
    }

    private ArrayList<CharSequence> getRepliesFromAdjustment(Adjustment adjustment) {
        return adjustment.getSignals().getCharSequenceArrayList(Adjustment.KEY_TEXT_REPLIES);
    }

    @Nullable
    private Adjustment findAdjustment(List<Adjustment> adjustments, String key) {
        for (Adjustment adjustment : adjustments) {
            if (adjustment.getKey().equals(key)) {
                return adjustment;
            }
        }
        return null;
    }

    private void mockActionFactoryCreatesAction() {
        when(mNotificationActionFactory.createNotificationAction(any(ActionableInsight.class)))
                .thenAnswer(
                        invocation -> {
                            ActionableInsight insight = invocation.getArgument(0);
                            return new Notification.Action.Builder(
                                            insight.getDisplayDetails().getIcon(),
                                            insight.getDisplayDetails().getTitle(),
                                            PendingIntent.getActivity(
                                                    mContext,
                                                    0,
                                                    new Intent("ACTION"),
                                                    PendingIntent.FLAG_IMMUTABLE))
                                    .build();
                        });
    }

    private void mockActionResolverResolvesIntent() {
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = "pkg";
        activityInfo.name = "TestActivity";
        activityInfo.applicationInfo = new ApplicationInfo();
        activityInfo.applicationInfo.packageName = "pkg";
        activityInfo.icon = TEST_APP_RESOURCE_ID;

        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = activityInfo;

        ResolutionResult result =
                new ResolutionResult(
                        PendingIntent.getActivity(
                                mContext, 0, new Intent("ACTION"), PendingIntent.FLAG_IMMUTABLE),
                        resolveInfo,
                        ActionType.ACTIVITY);

        when(mNotificationActionFactory.createNotificationAction(any(ActionableInsight.class)))
                .thenAnswer(
                        invocation -> {
                            ActionableInsight insight = invocation.getArgument(0);
                            return new Notification.Action.Builder(
                                            insight.getDisplayDetails().getIcon(),
                                            insight.getDisplayDetails().getTitle(),
                                            PendingIntent.getActivity(
                                                    mContext,
                                                    0,
                                                    new Intent("ACTION"),
                                                    PendingIntent.FLAG_IMMUTABLE))
                                    .build();
                        });
    }
}
