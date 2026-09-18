/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.statusbar.chips.notification.ui.viewmodel

import android.app.Flags.FLAG_METRIC_VALUE_ALTERNATIVE_STRINGS
import android.app.Notification
import android.app.Notification.Metric.TimeDifference
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import androidx.test.filters.SmallTest
import com.android.internal.logging.InstanceId
import com.android.systemui.Flags.FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT
import com.android.systemui.SysuiTestCase
import com.android.systemui.activity.data.repository.activityManagerRepository
import com.android.systemui.activity.data.repository.fake
import com.android.systemui.animation.ActivityTransitionAnimator
import com.android.systemui.animation.Expandable
import com.android.systemui.common.shared.model.ContentDescription.Companion.loadContentDescription
import com.android.systemui.keyguard.data.repository.FakeKeyguardTransitionRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.statusbar.chips.StatusBarChipsReturnAnimations
import com.android.systemui.statusbar.chips.notification.domain.interactor.statusBarNotificationChipsInteractor
import com.android.systemui.statusbar.chips.ui.model.Chronometer
import com.android.systemui.statusbar.chips.ui.model.ColorsModel
import com.android.systemui.statusbar.chips.ui.model.EventTime
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel
import com.android.systemui.statusbar.notification.data.model.activeNotificationModel
import com.android.systemui.statusbar.notification.data.repository.ActiveNotificationsStore
import com.android.systemui.statusbar.notification.data.repository.UnconfinedFakeHeadsUpRowRepository
import com.android.systemui.statusbar.notification.data.repository.activeNotificationListRepository
import com.android.systemui.statusbar.notification.data.repository.addNotif
import com.android.systemui.statusbar.notification.data.repository.removeNotif
import com.android.systemui.statusbar.notification.headsup.PinnedStatus
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentBuilder
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel.When
import com.android.systemui.statusbar.notification.shared.ActiveNotificationModel
import com.android.systemui.statusbar.notification.shared.Metric
import com.android.systemui.statusbar.notification.shared.NotificationChipFromCompactContent
import com.android.systemui.statusbar.notification.stack.data.repository.headsUpNotificationRepository
import com.android.systemui.testKosmos
import com.android.systemui.util.time.fakeSystemClock
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@EnableFlags(
    android.app.Flags.FLAG_API_METRIC_STYLE,
    android.app.Flags.FLAG_API_NOTIFICATION_SEMANTIC_STYLE,
)
class NotifChipsViewModelTest(flags: FlagsParameterization) : SysuiTestCase() {
    @get:Rule val mockito: MockitoRule = MockitoJUnit.rule()

    @Mock private lateinit var pendingIntent: PendingIntent

    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().apply {
            // Don't be in lockscreen so that HUNs are allowed
            fakeKeyguardTransitionRepository =
                FakeKeyguardTransitionRepository(initInLockscreen = false, testScope = testScope)
        }
    private val activeNotificationListRepository = kosmos.activeNotificationListRepository

    private val underTest by lazy { kosmos.notifChipsViewModel }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Before
    fun setUp() {
        kosmos.statusBarNotificationChipsInteractor.start()

        whenever(pendingIntent.intent).thenReturn(Intent.makeMainActivity(COMPONENT))
    }

    @Test
    fun chips_noNotifs_empty() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            setNotifs(emptyList())

            assertThat(latest).isEmpty()
        }

    @Test
    fun chips_screenShareNotificationTrue_FilledIn() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            val key = "test"
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = key,
                        isScreenShareNotification = true,
                        promotedContent = newPromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )

            val chip = latest!!.single()
            assertThat(chip.key).isEqualTo(key)
            assertThat(chip.isScreenShareNotification).isTrue()
        }

    @Test
    fun chips_screenShareNotificationFalse_FilledIn() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            val key = "test"
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = key,
                        isScreenShareNotification = false,
                        promotedContent = newPromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )

            val chip = latest!!.single()
            assertThat(chip.key).isEqualTo(key)
            assertThat(chip.isScreenShareNotification).isFalse()
        }

    @Test
    fun chips_onePromotedNotif_keysAndIntentFilledIn() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = newPromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].key).isEqualTo("notif")
            assertThat(latest!![0].notificationKey).isEqualTo("notif")
        }

    @Test
    fun chips_notifMissingStatusBarChipIconView_notEmpty() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = newPromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )

            assertThat(latest).isNotEmpty()
        }

    @Test
    fun chips_onePromotedNotif_connectedDisplaysFlagDisabled_statusBarIconViewMatches() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        appName = "Fake App Name",
                        promotedContent = newPromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            val chip = latest!![0]
            assertIsNotifChip(
                chip,
                context,
                expectedNotificationKey = "notif",
                expectedContentDescriptionSubstrings = listOf("Ongoing", "Fake App Name"),
            )
        }

    @Test
    fun chips_onePromotedNotif_statusBarIconMatches() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            val notifKey = "notif"
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = notifKey,
                        appName = "Fake App Name",
                        statusBarChipIcon = null,
                        promotedContent = newPromotedNotificationContentBuilder(notifKey).build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            val chip = latest!![0]
            assertIsNotifChip(
                chip,
                context,
                expectedNotificationKey = "notif",
                expectedContentDescriptionSubstrings = listOf("Ongoing", "Fake App Name"),
            )
        }

    @Test
    fun chips_onePromotedNotif_colorIsSystemThemed() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.colors =
                        PromotedNotificationContentModel.Colors(
                            backgroundColor = 56,
                            textColor = 89,
                        )
                }
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].colors).isEqualTo(ColorsModel.SystemThemed)
        }

    @Test
    @EnableFlags(FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT)
    fun chips_notifWithSemanticStyle_chipTextHasSemanticColor() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.compactContent =
                        Notification.ResolvedBasicCompactContent(
                            COMPACT_ICON,
                            Notification.Metric.FixedText("Safe!"),
                            Notification.SEMANTIC_STYLE_SAFE,
                        )
                    // Notification colors should be IGNORED -> used in notification, not in chip.
                    this.colors =
                        PromotedNotificationContentModel.Colors(
                            backgroundColor = 56,
                            textColor = 89,
                        )
                }
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].colors.background(context).defaultColor)
                .isEqualTo(ColorsModel.SystemThemed.background(context).defaultColor)
            assertThat((latest!![0].colors as ColorsModel.SystemThemedWithOverride).textRes)
                .isEqualTo(Notification.semanticStyleToColorRes(Notification.SEMANTIC_STYLE_SAFE))
            assertThat(latest!![0].colors.outline(context))
                .isEqualTo(ColorsModel.SystemThemed.outline(context))
        }

    @Test
    @EnableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun chips_onePromotedNotif_returnAnimFlagEnabled_hasTransitionManager() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.colors =
                        PromotedNotificationContentModel.Colors(
                            backgroundColor = 56,
                            textColor = 89,
                        )
                }
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        contentIntent = pendingIntent,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].transitionManager).isNotNull()
        }

    @Test
    @DisableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun chips_onePromotedNotif_returnAnimFlagDisabled_noTransitionManager() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.colors =
                        PromotedNotificationContentModel.Colors(
                            backgroundColor = 56,
                            textColor = 89,
                        )
                }
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        contentIntent = pendingIntent,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].transitionManager).isNull()
        }

    @Test
    fun chips_onlyForPromotedNotifs() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif1",
                        packageName = "notif1",
                        promotedContent = newPromotedNotificationContentBuilder("notif1").build(),
                    ),
                    activeNotificationModel(
                        key = "notif2",
                        packageName = "notif2",
                        promotedContent = newPromotedNotificationContentBuilder("notif2").build(),
                    ),
                    activeNotificationModel(key = "notif3", packageName = "notif3"),
                )
            )

            assertThat(latest).hasSize(2)
            assertIsNotifChip(latest!![0], context, "notif1")
            assertIsNotifChip(latest!![1], context, "notif2")
        }

    @Test
    fun chips_twoChips_samePackage_differentUids_onlyLaterOneIncluded() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            fakeSystemClock.setCurrentTimeMillis(1000)
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "notif1",
                    packageName = "samePackage",
                    uid = 10,
                    statusBarChipIcon = null,
                    promotedContent = newPromotedNotificationContentBuilder("notif1").build(),
                )
            )

            fakeSystemClock.advanceTime(1000)
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "notif2",
                    packageName = "samePackage",
                    uid = 20,
                    statusBarChipIcon = null,
                    promotedContent = newPromotedNotificationContentBuilder("notif2").build(),
                )
            )

            // Notif added later takes priority and is the only one
            assertThat(latest!!.map { it.key }).containsExactly("notif2").inOrder()
        }

    @Test
    fun chips_twoChips_sameUid_differentPackages_bothIncluded() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            fakeSystemClock.setCurrentTimeMillis(1000)
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "notif1",
                    packageName = "onePackage",
                    uid = 10,
                    statusBarChipIcon = null,
                    promotedContent = newPromotedNotificationContentBuilder("notif1").build(),
                )
            )

            fakeSystemClock.advanceTime(1000)
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "notif2",
                    packageName = "anotherPackage",
                    uid = 10,
                    statusBarChipIcon = null,
                    promotedContent = newPromotedNotificationContentBuilder("notif2").build(),
                )
            )

            // Notif added later takes priority
            assertThat(latest!!.map { it.key }).containsExactly("notif2", "notif1").inOrder()
        }

    @Test
    fun chips_twoChips_samePackage_andSameUid_onlyLaterOneIncluded() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            fakeSystemClock.setCurrentTimeMillis(1000)
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "notif1",
                    packageName = "samePackage",
                    uid = 3,
                    statusBarChipIcon = null,
                    promotedContent = newPromotedNotificationContentBuilder("notif1").build(),
                )
            )

            fakeSystemClock.advanceTime(1000)
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "notif2",
                    packageName = "samePackage",
                    uid = 3,
                    statusBarChipIcon = null,
                    promotedContent = newPromotedNotificationContentBuilder("notif2").build(),
                )
            )

            // Notif added later takes priority and is the only one
            assertThat(latest!!.map { it.key }).containsExactly("notif2").inOrder()
        }

    @Test
    fun chips_multipleChipsFromMultiplePackagesAndUids_higherPriorityOfEachIncluded() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            // Two notifs from "firstPackage"
            fakeSystemClock.setCurrentTimeMillis(1000)
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "firstPackage.1",
                    packageName = "firstPackage",
                    uid = 1,
                    statusBarChipIcon = null,
                    promotedContent =
                        newPromotedNotificationContentBuilder("firstPackage.1").build(),
                )
            )

            fakeSystemClock.advanceTime(1000)
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "firstPackage.2",
                    packageName = "firstPackage",
                    uid = 1,
                    statusBarChipIcon = null,
                    promotedContent =
                        newPromotedNotificationContentBuilder("firstPackage.2").build(),
                )
            )

            // Three notifs from "secondPackage"
            fakeSystemClock.advanceTime(1000)
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "secondPackage.1",
                    packageName = "secondPackage",
                    uid = 2,
                    statusBarChipIcon = null,
                    promotedContent =
                        newPromotedNotificationContentBuilder("secondPackage.1").build(),
                )
            )

            fakeSystemClock.advanceTime(1000)
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "secondPackage.2",
                    packageName = "secondPackage",
                    uid = 20,
                    statusBarChipIcon = null,
                    promotedContent =
                        newPromotedNotificationContentBuilder("secondPackage.2").build(),
                )
            )

            fakeSystemClock.advanceTime(1000)
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = "secondPackage.3",
                    packageName = "secondPackage",
                    uid = 200,
                    statusBarChipIcon = null,
                    promotedContent =
                        newPromotedNotificationContentBuilder("secondPackage.3").build(),
                )
            )

            // Notifs added later take priority
            assertThat(latest!!.map { it.key })
                .containsExactly("secondPackage.3", "firstPackage.2")
                .inOrder()
        }

    @Test
    fun chips_notifTimeAndSystemTimeBothUpdated_modelNotRecreated() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            val currentTime = 3.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentTime)

            val oldPromotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.time = When.Time(currentTime)
                }
            val icon = null
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = icon,
                        promotedContent = oldPromotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0]).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            val oldModel = latest!![0]

            // WHEN the system time advances and the promoted content updates to that new time also
            val newTime = currentTime + 2.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(newTime)
            val newPromotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.time = When.Time(newTime)
                }
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = icon,
                        promotedContent = newPromotedContentBuilder.build(),
                    )
                )
            )

            // THEN we don't re-create the model because we still won't show the time
            assertThat(latest).hasSize(1)
            assertThat(latest!![0]).isSameInstanceAs(oldModel)
        }

    @Test
    fun chips_irrelevantPromotedContentUpdated_modelNotRecreated() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            val oldPromotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.subText = "Old subtext"
                }
            val icon = null
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = icon,
                        promotedContent = oldPromotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0]).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            val oldModel = latest!![0]

            // WHEN promoted content updates with an irrelevant field
            val newPromotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.subText = "New subtext"
                }
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = icon,
                        promotedContent = newPromotedContentBuilder.build(),
                    )
                )
            )

            // THEN we don't re-create the model
            assertThat(latest).hasSize(1)
            assertThat(latest!![0]).isSameInstanceAs(oldModel)
        }

    @Test
    fun chips_appStartsAsVisible_isHiddenTrue() =
        kosmos.runTest {
            activityManagerRepository.fake.startingIsAppVisibleValue = true

            val latest by collectLastValue(underTest.chips)

            val uid = 433
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        uid = uid,
                        statusBarChipIcon = null,
                        promotedContent = newPromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].isHidden).isTrue()
        }

    @Test
    fun chips_appStartsAsNotVisible_isHiddenFalse() =
        kosmos.runTest {
            activityManagerRepository.fake.startingIsAppVisibleValue = false

            val latest by collectLastValue(underTest.chips)

            val uid = 433
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        uid = uid,
                        statusBarChipIcon = null,
                        promotedContent = newPromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].isHidden).isFalse()
        }

    @Test
    fun chips_isHidden_changesBasedOnAppVisibility() =
        kosmos.runTest {
            activityManagerRepository.fake.startingIsAppVisibleValue = false

            val latest by collectLastValue(underTest.chips)

            val uid = 433
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        uid = uid,
                        statusBarChipIcon = null,
                        promotedContent = newPromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )

            activityManagerRepository.fake.setIsAppVisible(uid, isAppVisible = false)
            assertThat(latest).hasSize(1)
            assertThat(latest!![0].isHidden).isFalse()

            activityManagerRepository.fake.setIsAppVisible(uid, isAppVisible = true)
            assertThat(latest).hasSize(1)
            assertThat(latest!![0].isHidden).isTrue()

            activityManagerRepository.fake.setIsAppVisible(uid, isAppVisible = false)
            assertThat(latest).hasSize(1)
            assertThat(latest!![0].isHidden).isFalse()
        }

    @Test
    @DisableFlags(FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT)
    fun chips_hasShortCriticalText_usesTextInsteadOfTimeOrMetric() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentTime = 30.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentTime)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.shortCriticalText = "Arrived"
                    this.time = When.Time(currentTime + 30.minutes.inWholeMilliseconds)
                    this.metrics =
                        listOf(Metric.Text(textVariants = listOf("1000m"), label = "distance"))
                }
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.Text::class.java)
            assertThat((latest!![0].content as OngoingActivityChipModel.Content.Text).text)
                .isEqualTo("Arrived")
        }

    @Test
    @DisableFlags(FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT)
    fun chips_useMetricInsteadOfTime() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentTime = 30.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentTime)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.time = When.Time(currentTime + 30.minutes.inWholeMilliseconds)
                    this.metrics =
                        listOf(Metric.Text(textVariants = listOf("Arrived"), label = "status"))
                }
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.Text::class.java)
            assertThat((latest!![0].content as OngoingActivityChipModel.Content.Text).text)
                .isEqualTo("Arrived")
        }

    @Test
    fun chips_shortCriticalText_usesInstanceId() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.shortCriticalText = "Arrived"
                }
            val instanceId = InstanceId.fakeInstanceId(30)
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                        instanceId = instanceId,
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0]).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat(latest!![0].instanceId).isEqualTo(instanceId)
        }

    @Test
    fun chips_noTime_isIconOnly() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared { this.time = null }
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
        }

    @Test
    @DisableFlags(FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT)
    fun chips_basicTime_timeInFuture_isShortTimeDelta() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentTime = 3.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentTime)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.time = When.Time(currentTime + 13.minutes.inWholeMilliseconds)
                }

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.ShortTimeDelta::class.java)
        }

    @Test
    fun chips_basicTime_respectsIsAppVisible() =
        kosmos.runTest {
            activityManagerRepository.fake.startingIsAppVisibleValue = false

            val latest by collectLastValue(underTest.chips)
            val currentTime = 3.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentTime)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    if (NotificationChipFromCompactContent.isEnabled) {
                        this.compactContent =
                            Notification.ResolvedBasicCompactContent(
                                COMPACT_ICON,
                                TimeDifference.forTimer(
                                    Instant.ofEpochMilli(
                                        currentTime + 13.minutes.inWholeMilliseconds
                                    ),
                                    TimeDifference.FORMAT_CHRONOMETER,
                                ),
                                Notification.SEMANTIC_STYLE_UNSPECIFIED,
                            )
                    } else {
                        this.time = When.Time(currentTime + 13.minutes.inWholeMilliseconds)
                    }
                }
            val uid = 3

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        uid = 3,
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(
                    if (NotificationChipFromCompactContent.isEnabled)
                        OngoingActivityChipModel.Content.Timer::class.java
                    else OngoingActivityChipModel.Content.ShortTimeDelta::class.java
                )
            assertThat(latest!![0].isHidden).isFalse()

            activityManagerRepository.fake.setIsAppVisible(uid = uid, isAppVisible = true)

            assertThat(latest!![0].isHidden).isTrue()
        }

    @Test
    @DisableFlags(FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT)
    fun chips_basicTime_timeLessThanOneMinInFuture_isIconOnly() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentTime = 3.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentTime)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.time = When.Time(currentTime + 500)
                }

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
        }

    @Test
    fun chips_basicTime_timeIsNow_isIconOnly() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentTime = 62.seconds.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentTime)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.time = When.Time(currentTime)
                }

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
        }

    @Test
    fun chips_basicTime_timeInPast_isIconOnly() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentTime = 62.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentTime)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.time = When.Time(currentTime - 2.minutes.inWholeMilliseconds)
                }

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
        }

    // Not necessarily the behavior we *want* to have, but it's the currently implemented behavior.
    @Test
    @DisableFlags(FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT)
    fun chips_basicTime_timeIsInFuture_thenTimeAdvances_stillShortTimeDelta() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentTime = 30.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentTime)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.time = When.Time(currentTime + 3.minutes.inWholeMilliseconds)
                }

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.ShortTimeDelta::class.java)

            fakeSystemClock.advanceTime(5.minutes.inWholeMilliseconds)

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.ShortTimeDelta::class.java)
        }

    @Test
    @DisableFlags(FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT)
    fun chips_countUpTime_isTimer() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentTime = 30.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentTime)

            val currentElapsed =
                currentTime + fakeSystemClock.elapsedRealtime() -
                    fakeSystemClock.currentTimeMillis()

            val whenElapsed = currentElapsed - 1.minutes.inWholeMilliseconds

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.time =
                        When.Chronometer(elapsedRealtimeMillis = whenElapsed, isCountDown = false)
                }
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
            assertThat((latest!![0].content as OngoingActivityChipModel.Content.Timer).value)
                .isEqualTo(
                    Chronometer.Running(EventTime.ElapsedRealtime(whenElapsed), isCountdown = false)
                )
        }

    @Test
    fun chips_countUpTime_respectsIsAppVisible() =
        kosmos.runTest {
            activityManagerRepository.fake.startingIsAppVisibleValue = true

            val latest by collectLastValue(underTest.chips)
            val currentTime = 30.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentTime)

            val currentElapsed =
                currentTime + fakeSystemClock.elapsedRealtime() -
                    fakeSystemClock.currentTimeMillis()

            val whenElapsed = currentElapsed - 1.minutes.inWholeMilliseconds

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    if (NotificationChipFromCompactContent.isEnabled) {
                        this.compactContent =
                            Notification.ResolvedBasicCompactContent(
                                COMPACT_ICON,
                                TimeDifference.forStopwatch(
                                    whenElapsed,
                                    TimeDifference.FORMAT_CHRONOMETER,
                                ),
                                Notification.SEMANTIC_STYLE_UNSPECIFIED,
                            )
                    } else {
                        this.time =
                            When.Chronometer(
                                elapsedRealtimeMillis = whenElapsed,
                                isCountDown = false,
                            )
                    }
                }
            val uid = 6
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        uid = uid,
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
            assertThat(latest!![0].isHidden).isTrue()

            activityManagerRepository.fake.setIsAppVisible(uid, isAppVisible = false)

            assertThat(latest!![0].isHidden).isFalse()
        }

    @Test
    fun chips_countDownTime_isTimer() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentTime = 30.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentTime)

            val currentElapsed =
                currentTime + fakeSystemClock.elapsedRealtime() -
                    fakeSystemClock.currentTimeMillis()

            val whenElapsed = currentElapsed + 10.minutes.inWholeMilliseconds

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    if (NotificationChipFromCompactContent.isEnabled) {
                        this.compactContent =
                            Notification.ResolvedBasicCompactContent(
                                COMPACT_ICON,
                                TimeDifference.forTimer(
                                    whenElapsed,
                                    TimeDifference.FORMAT_CHRONOMETER,
                                ),
                                Notification.SEMANTIC_STYLE_UNSPECIFIED,
                            )
                    } else {
                        this.time =
                            When.Chronometer(
                                elapsedRealtimeMillis = whenElapsed,
                                isCountDown = true,
                            )
                    }
                }
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)

            assertThat((latest!![0].content as OngoingActivityChipModel.Content.Timer).value)
                .isEqualTo(
                    Chronometer.Running(EventTime.ElapsedRealtime(whenElapsed), isCountdown = true)
                )
        }

    @Test
    fun chips_countDownTime_usesInstanceId() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            val instanceId = InstanceId.fakeInstanceId(20)
            val currentTime = 30.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentTime)

            val currentElapsed =
                currentTime + fakeSystemClock.elapsedRealtime() -
                    fakeSystemClock.currentTimeMillis()
            val whenElapsed = currentElapsed + 10.minutes.inWholeMilliseconds
            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.time =
                        When.Chronometer(elapsedRealtimeMillis = whenElapsed, isCountDown = true)
                }
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                        instanceId = instanceId,
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0]).isInstanceOf(OngoingActivityChipModel.Active::class.java)
            assertThat((latest!![0]).instanceId).isEqualTo(instanceId)
        }

    @Test
    @DisableFlags(FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT)
    fun chips_adaptiveTimerMetric_systemClock_isShortTimeDelta() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentSystemTime = 40.minutes.inWholeMilliseconds
            val currentElapsedTime = 3.minutes.inWholeMilliseconds
            val timerLength = 12.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentSystemTime)
            fakeSystemClock.setElapsedRealtime(currentElapsedTime)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.time = When.Time(currentSystemTime)
                    this.metrics =
                        listOf(
                            Metric.TimeDifference.Instant(
                                zeroTime = Instant.ofEpochMilli(currentSystemTime + timerLength),
                                isTimer = true,
                                useAdaptiveFormat = true,
                                label = "timer",
                            )
                        )
                }

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.ShortTimeDelta::class.java)
            val timeDelta = latest!![0].content as OngoingActivityChipModel.Content.ShortTimeDelta
            assertThat(timeDelta.time).isEqualTo(52.minutes.inWholeMilliseconds)
        }

    @Test
    @EnableFlags(FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT)
    fun chips_compactContentAdaptiveTimer_systemClock_isCountdownTimer() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentSystemTime = 40.minutes.inWholeMilliseconds
            val currentElapsedTime = 3.minutes.inWholeMilliseconds
            val timerLength = 12.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentSystemTime)
            fakeSystemClock.setElapsedRealtime(currentElapsedTime)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.time = When.Time(currentSystemTime)
                    this.compactContent =
                        Notification.ResolvedBasicCompactContent(
                            COMPACT_ICON,
                            TimeDifference.forTimer(
                                Instant.ofEpochMilli(currentSystemTime + timerLength),
                                TimeDifference.FORMAT_ADAPTIVE,
                            ),
                            Notification.SEMANTIC_STYLE_UNSPECIFIED,
                        )
                }

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
            val timer = latest!![0].content as OngoingActivityChipModel.Content.Timer
            assertThat(timer.value)
                .isEqualTo(
                    Chronometer.Running(
                        EventTime.ClockTime(Instant.ofEpochMilli(currentSystemTime + timerLength)),
                        isCountdown = true,
                    )
                )
            assertThat(timer.format)
                .isEqualTo(OngoingActivityChipModel.Content.Timer.Format.ADAPTIVE)
        }

    @Test
    @DisableFlags(FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT)
    fun chips_adaptiveTimerMetric_realtimeClock_isShortTimeDelta() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentSystemTime = 40.minutes.inWholeMilliseconds
            val currentElapsedTime = 3.minutes.inWholeMilliseconds
            val timerLength = 12.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentSystemTime)
            fakeSystemClock.setElapsedRealtime(currentElapsedTime)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.time = When.Time(currentSystemTime)
                    this.metrics =
                        listOf(
                            Metric.TimeDifference.ElapsedRealtime(
                                zeroElapsedRealtime = currentElapsedTime + timerLength,
                                isTimer = true,
                                useAdaptiveFormat = true,
                                label = "timer",
                            )
                        )
                }

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.ShortTimeDelta::class.java)
            val timeDelta = latest!![0].content as OngoingActivityChipModel.Content.ShortTimeDelta
            assertThat(timeDelta.time).isEqualTo(52.minutes.inWholeMilliseconds)
        }

    @Test
    @EnableFlags(FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT, FLAG_METRIC_VALUE_ALTERNATIVE_STRINGS)
    fun chips_compactContentMetricWithTextVariants_hasTextVariants() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.compactContent =
                        Notification.ResolvedBasicCompactContent(
                            COMPACT_ICON,
                            Notification.Metric.FixedInt(123_456),
                            Notification.SEMANTIC_STYLE_UNSPECIFIED,
                        )
                }

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.TextVariants::class.java)
            assertThat(
                    (latest!![0].content as OngoingActivityChipModel.Content.TextVariants)
                        .textVariants
                )
                .containsExactly("123,456", "123K")
                .inOrder()
        }

    @Test
    @EnableFlags(FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT)
    fun chips_compactContentAdaptiveTimer_realtimeClock_isCountdown() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentSystemTime = 40.minutes.inWholeMilliseconds
            val currentElapsedTime = 3.minutes.inWholeMilliseconds
            val timerLength = 12.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentSystemTime)
            fakeSystemClock.setElapsedRealtime(currentElapsedTime)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.time = When.Time(currentSystemTime)
                    this.compactContent =
                        Notification.ResolvedBasicCompactContent(
                            COMPACT_ICON,
                            TimeDifference.forTimer(
                                currentElapsedTime + timerLength,
                                TimeDifference.FORMAT_ADAPTIVE,
                            ),
                            Notification.SEMANTIC_STYLE_UNSPECIFIED,
                        )
                }

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
            val timer = latest!![0].content as OngoingActivityChipModel.Content.Timer
            assertThat(timer.value)
                .isEqualTo(
                    Chronometer.Running(
                        EventTime.ElapsedRealtime(currentElapsedTime + timerLength),
                        isCountdown = true,
                    )
                )
            assertThat(timer.format)
                .isEqualTo(OngoingActivityChipModel.Content.Timer.Format.ADAPTIVE)
        }

    @Test
    @DisableFlags(FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT)
    fun chips_chronometerTimerMetric_systemClock_isTimer() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentSystemTime = 40.minutes.inWholeMilliseconds
            val currentElapsedTime = 3.minutes.inWholeMilliseconds
            val timerLength = 12.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentSystemTime)
            fakeSystemClock.setElapsedRealtime(currentElapsedTime)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.time = When.Time(currentSystemTime)
                    this.metrics =
                        listOf(
                            Metric.TimeDifference.Instant(
                                zeroTime = Instant.ofEpochMilli(currentSystemTime + timerLength),
                                isTimer = true,
                                useAdaptiveFormat = false,
                                label = "timer",
                            )
                        )
                }

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
            val timer = latest!![0].content as OngoingActivityChipModel.Content.Timer
            assertThat(timer.value)
                .isEqualTo(
                    Chronometer.Running(
                        EventTime.ElapsedRealtime(15.minutes.inWholeMilliseconds),
                        isCountdown = true,
                    )
                )
        }

    @Test
    @EnableFlags(FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT)
    fun chips_compactContentChronometerTimer_systemClock_isTimer() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentSystemTime = 40.minutes.inWholeMilliseconds
            val currentElapsedTime = 3.minutes.inWholeMilliseconds
            val timerLength = 12.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentSystemTime)
            fakeSystemClock.setElapsedRealtime(currentElapsedTime)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.time = When.Time(currentSystemTime)
                    this.compactContent =
                        Notification.ResolvedBasicCompactContent(
                            COMPACT_ICON,
                            TimeDifference.forTimer(
                                Instant.ofEpochMilli(currentSystemTime + timerLength),
                                TimeDifference.FORMAT_CHRONOMETER,
                            ),
                            Notification.SEMANTIC_STYLE_UNSPECIFIED,
                        )
                }

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
            val timer = latest!![0].content as OngoingActivityChipModel.Content.Timer
            assertThat(timer.value)
                .isEqualTo(
                    Chronometer.Running(
                        EventTime.ClockTime(Instant.ofEpochMilli(52.minutes.inWholeMilliseconds)),
                        isCountdown = true,
                    )
                )
            assertThat(timer.format)
                .isEqualTo(OngoingActivityChipModel.Content.Timer.Format.CHRONOMETER)
        }

    @Test
    @DisableFlags(FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT)
    fun chips_chronometerTimerMetric_realtimeClock_isTimer() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentSystemTime = 40.minutes.inWholeMilliseconds
            val currentElapsedTime = 3.minutes.inWholeMilliseconds
            val timerLength = 12.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentSystemTime)
            fakeSystemClock.setElapsedRealtime(currentElapsedTime)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.time = When.Time(currentSystemTime)
                    this.metrics =
                        listOf(
                            Metric.TimeDifference.ElapsedRealtime(
                                zeroElapsedRealtime = currentElapsedTime + timerLength,
                                isTimer = true,
                                useAdaptiveFormat = false,
                                label = "timer",
                            )
                        )
                }

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
            val timer = latest!![0].content as OngoingActivityChipModel.Content.Timer
            assertThat(timer.value)
                .isEqualTo(
                    Chronometer.Running(
                        EventTime.ElapsedRealtime(15.minutes.inWholeMilliseconds),
                        isCountdown = true,
                    )
                )
        }

    @Test
    @EnableFlags(FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT)
    fun chips_compactContentChronometerTimer_realtimeClock_isTimer() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentSystemTime = 40.minutes.inWholeMilliseconds
            val currentElapsedTime = 3.minutes.inWholeMilliseconds
            val timerLength = 12.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentSystemTime)
            fakeSystemClock.setElapsedRealtime(currentElapsedTime)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.time = When.Time(currentSystemTime)
                    this.compactContent =
                        Notification.ResolvedBasicCompactContent(
                            COMPACT_ICON,
                            TimeDifference.forTimer(
                                currentElapsedTime + timerLength,
                                TimeDifference.FORMAT_CHRONOMETER,
                            ),
                            Notification.SEMANTIC_STYLE_UNSPECIFIED,
                        )
                }

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
            val timer = latest!![0].content as OngoingActivityChipModel.Content.Timer
            assertThat(timer.value)
                .isEqualTo(
                    Chronometer.Running(
                        EventTime.ElapsedRealtime(15.minutes.inWholeMilliseconds),
                        isCountdown = true,
                    )
                )
            assertThat(timer.format)
                .isEqualTo(OngoingActivityChipModel.Content.Timer.Format.CHRONOMETER)
        }

    @Test
    @DisableFlags(FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT)
    fun chips_chronometerStopwatchMetric_systemClock_isTimer() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentSystemTime = 40.minutes.inWholeMilliseconds
            val currentElapsedTime = 3.minutes.inWholeMilliseconds
            val stopwatchValue = 2.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentSystemTime)
            fakeSystemClock.setElapsedRealtime(currentElapsedTime)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.time = When.Time(currentSystemTime)
                    this.metrics =
                        listOf(
                            Metric.TimeDifference.Instant(
                                zeroTime = Instant.ofEpochMilli(currentSystemTime - stopwatchValue),
                                isTimer = true,
                                useAdaptiveFormat = false,
                                label = "stopwatch",
                            )
                        )
                }

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
            val timer = latest!![0].content as OngoingActivityChipModel.Content.Timer
            assertThat(timer.value)
                .isEqualTo(
                    Chronometer.Running(
                        EventTime.ElapsedRealtime(1.minutes.inWholeMilliseconds),
                        isCountdown = true,
                    )
                )
        }

    @Test
    @EnableFlags(FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT)
    fun chips_compactContentChronometerStopwatch_systemClock_isTimer() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentSystemTime = 40.minutes.inWholeMilliseconds
            val currentElapsedTime = 3.minutes.inWholeMilliseconds
            val stopwatchValue = 2.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentSystemTime)
            fakeSystemClock.setElapsedRealtime(currentElapsedTime)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.time = When.Time(currentSystemTime)
                    this.compactContent =
                        Notification.ResolvedBasicCompactContent(
                            COMPACT_ICON,
                            TimeDifference.forTimer(
                                Instant.ofEpochMilli(currentSystemTime - stopwatchValue),
                                TimeDifference.FORMAT_CHRONOMETER,
                            ),
                            Notification.SEMANTIC_STYLE_UNSPECIFIED,
                        )
                }

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
            val timer = latest!![0].content as OngoingActivityChipModel.Content.Timer
            assertThat(timer.value)
                .isEqualTo(
                    Chronometer.Running(
                        EventTime.ClockTime(Instant.ofEpochMilli(38.minutes.inWholeMilliseconds)),
                        isCountdown = true,
                    )
                )
            assertThat(timer.format)
                .isEqualTo(OngoingActivityChipModel.Content.Timer.Format.CHRONOMETER)
        }

    @Test
    @DisableFlags(FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT)
    fun chips_chronometerStopwatchMetric_realtimeClock_isTimer() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentSystemTime = 40.minutes.inWholeMilliseconds
            val currentElapsedTime = 3.minutes.inWholeMilliseconds
            val stopwatchValue = 2.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentSystemTime)
            fakeSystemClock.setElapsedRealtime(currentElapsedTime)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.time = When.Time(currentSystemTime)
                    this.metrics =
                        listOf(
                            Metric.TimeDifference.ElapsedRealtime(
                                zeroElapsedRealtime = currentElapsedTime - stopwatchValue,
                                isTimer = false,
                                useAdaptiveFormat = false,
                                label = "stopwatch",
                            )
                        )
                }

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
            val timer = latest!![0].content as OngoingActivityChipModel.Content.Timer
            assertThat(timer.value)
                .isEqualTo(
                    Chronometer.Running(
                        EventTime.ElapsedRealtime(1.minutes.inWholeMilliseconds),
                        isCountdown = false,
                    )
                )
        }

    @Test
    @EnableFlags(FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT)
    fun chips_compactContentChronometerStopwatch_realtimeClock_isTimer() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentSystemTime = 40.minutes.inWholeMilliseconds
            val currentElapsedTime = 3.minutes.inWholeMilliseconds
            val stopwatchValue = 2.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentSystemTime)
            fakeSystemClock.setElapsedRealtime(currentElapsedTime)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.time = When.Time(currentSystemTime)
                    this.compactContent =
                        Notification.ResolvedBasicCompactContent(
                            COMPACT_ICON,
                            TimeDifference.forStopwatch(
                                currentElapsedTime - stopwatchValue,
                                TimeDifference.FORMAT_CHRONOMETER,
                            ),
                            Notification.SEMANTIC_STYLE_UNSPECIFIED,
                        )
                }

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            assertThat(latest).hasSize(1)
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.Timer::class.java)
            val timer = latest!![0].content as OngoingActivityChipModel.Content.Timer
            assertThat(timer.value)
                .isEqualTo(
                    Chronometer.Running(
                        EventTime.ElapsedRealtime(1.minutes.inWholeMilliseconds),
                        isCountdown = false,
                    )
                )
            assertThat(timer.format)
                .isEqualTo(OngoingActivityChipModel.Content.Timer.Format.CHRONOMETER)
        }

    @Test
    fun chips_noHeadsUp_showsTime() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentTime = 30.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentTime)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    if (NotificationChipFromCompactContent.isEnabled) {
                        this.compactContent =
                            Notification.ResolvedBasicCompactContent(
                                COMPACT_ICON,
                                TimeDifference.forTimer(
                                    Instant.ofEpochMilli(
                                        currentTime + 10.minutes.inWholeMilliseconds
                                    ),
                                    TimeDifference.FORMAT_CHRONOMETER,
                                ),
                                Notification.SEMANTIC_STYLE_UNSPECIFIED,
                            )
                    } else {
                        this.time = When.Time(currentTime + 10.minutes.inWholeMilliseconds)
                    }
                }
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            // WHEN there's no HUN
            kosmos.headsUpNotificationRepository.setNotifications(emptyList())

            // THEN the chip shows the time
            assertThat(latest!![0].content)
                .isInstanceOf(
                    if (NotificationChipFromCompactContent.isEnabled)
                        OngoingActivityChipModel.Content.Timer::class.java
                    else OngoingActivityChipModel.Content.ShortTimeDelta::class.java
                )
        }

    @Test
    fun chips_hasHeadsUpBySystem_showsTime() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentTime = 30.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentTime)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    if (NotificationChipFromCompactContent.isEnabled) {
                        this.compactContent =
                            Notification.ResolvedBasicCompactContent(
                                COMPACT_ICON,
                                TimeDifference.forTimer(
                                    Instant.ofEpochMilli(
                                        currentTime + 10.minutes.inWholeMilliseconds
                                    ),
                                    TimeDifference.FORMAT_CHRONOMETER,
                                ),
                                Notification.SEMANTIC_STYLE_UNSPECIFIED,
                            )
                    } else {
                        this.time = When.Time(currentTime + 10.minutes.inWholeMilliseconds)
                    }
                }
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            // WHEN there's a HUN pinned by the system
            kosmos.headsUpNotificationRepository.setNotifications(
                UnconfinedFakeHeadsUpRowRepository(
                    key = "notif",
                    pinnedStatus = MutableStateFlow(PinnedStatus.PinnedBySystem),
                )
            )

            // THEN the chip keeps showing time
            // (In real life the chip won't show at all, but that's handled in a different part of
            // the system. What we know here is that the chip shouldn't shrink to icon only.)
            assertThat(latest!![0].content)
                .isInstanceOf(
                    if (NotificationChipFromCompactContent.isEnabled)
                        OngoingActivityChipModel.Content.Timer::class.java
                    else OngoingActivityChipModel.Content.ShortTimeDelta::class.java
                )
        }

    @Test
    fun chips_hasHeadsUpByUser_forOtherNotif_showsTime() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentTime = 30.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentTime)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    if (NotificationChipFromCompactContent.isEnabled) {
                        this.compactContent =
                            Notification.ResolvedBasicCompactContent(
                                COMPACT_ICON,
                                TimeDifference.forTimer(
                                    Instant.ofEpochMilli(
                                        currentTime + 10.minutes.inWholeMilliseconds
                                    ),
                                    TimeDifference.FORMAT_CHRONOMETER,
                                ),
                                Notification.SEMANTIC_STYLE_UNSPECIFIED,
                            )
                    } else {
                        this.time = When.Time(currentTime + 10.minutes.inWholeMilliseconds)
                    }
                }
            val otherPromotedContentBuilder =
                newPromotedNotificationContentBuilder("other notif").applyToShared {
                    if (NotificationChipFromCompactContent.isEnabled) {
                        this.compactContent =
                            Notification.ResolvedBasicCompactContent(
                                COMPACT_ICON,
                                TimeDifference.forTimer(
                                    Instant.ofEpochMilli(
                                        currentTime + 10.minutes.inWholeMilliseconds
                                    ),
                                    TimeDifference.FORMAT_CHRONOMETER,
                                ),
                                Notification.SEMANTIC_STYLE_UNSPECIFIED,
                            )
                    } else {
                        this.time = When.Time(currentTime + 10.minutes.inWholeMilliseconds)
                    }
                }
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        promotedContent = promotedContentBuilder.build(),
                    ),
                    activeNotificationModel(
                        key = "other notif",
                        promotedContent = otherPromotedContentBuilder.build(),
                    ),
                )
            )

            // WHEN there's a HUN pinned for the "other notif" chip
            kosmos.headsUpNotificationRepository.setNotifications(
                UnconfinedFakeHeadsUpRowRepository(
                    key = "other notif",
                    pinnedStatus = MutableStateFlow(PinnedStatus.PinnedByUser),
                )
            )

            // THEN the "notif" chip keeps showing time
            val chip = latest!![0]
            assertThat(latest!![0].content)
                .isInstanceOf(
                    if (NotificationChipFromCompactContent.isEnabled)
                        OngoingActivityChipModel.Content.Timer::class.java
                    else OngoingActivityChipModel.Content.ShortTimeDelta::class.java
                )
            assertIsNotifChip(chip, context, "notif")
        }

    @Test
    fun chips_hasHeadsUpByUser_forThisNotif_onlyShowsIcon() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val currentTime = 30.minutes.inWholeMilliseconds
            fakeSystemClock.setCurrentTimeMillis(currentTime)

            val promotedContentBuilder =
                newPromotedNotificationContentBuilder("notif").applyToShared {
                    this.time = When.Time(currentTime + 10.minutes.inWholeMilliseconds)
                }
            setNotifs(
                listOf(
                    activeNotificationModel(
                        key = "notif",
                        statusBarChipIcon = null,
                        promotedContent = promotedContentBuilder.build(),
                    )
                )
            )

            // WHEN this notification is pinned by the user
            kosmos.headsUpNotificationRepository.setNotifications(
                UnconfinedFakeHeadsUpRowRepository(
                    key = "notif",
                    pinnedStatus = MutableStateFlow(PinnedStatus.PinnedByUser),
                )
            )

            // THEN the chip shrinks to icon only
            assertThat(latest!![0].content)
                .isInstanceOf(OngoingActivityChipModel.Content.IconOnly::class.java)
        }

    @Test
    fun chips_clickingChipNotifiesInteractor() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)
            val latestChipTapKey by
                collectLastValue(
                    kosmos.statusBarNotificationChipsInteractor.promotedNotificationChipTapEvent
                )
            val key = "clickTest"

            setNotifs(
                listOf(
                    activeNotificationModel(
                        key,
                        statusBarChipIcon = null,
                        promotedContent = newPromotedNotificationContentBuilder(key).build(),
                    )
                )
            )
            val chip = latest!![0]

            assertThat(chip.clickBehavior)
                .isInstanceOf(
                    OngoingActivityChipModel.ClickBehavior.ShowHeadsUpNotification::class.java
                )

            (chip.clickBehavior as OngoingActivityChipModel.ClickBehavior.ShowHeadsUpNotification)
                .onClick()

            assertThat(latestChipTapKey).isEqualTo(key)
        }

    @Test
    fun chips_noHun_clickBehaviorIsShowHun() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            setNotifs(
                listOf(
                    activeNotificationModel(
                        "notif",
                        statusBarChipIcon = null,
                        promotedContent = newPromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )

            headsUpNotificationRepository.setNotifications(emptyList())

            assertThat(latest!![0].clickBehavior)
                .isInstanceOf(
                    OngoingActivityChipModel.ClickBehavior.ShowHeadsUpNotification::class.java
                )
        }

    @Test
    fun chip_hun_pinnedBySystem_clickBehaviorIsShowHun() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            setNotifs(
                listOf(
                    activeNotificationModel(
                        "notif",
                        statusBarChipIcon = null,
                        promotedContent = newPromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )

            headsUpNotificationRepository.setNotifications(
                UnconfinedFakeHeadsUpRowRepository(
                    key = "systemNotif",
                    pinnedStatus = MutableStateFlow(PinnedStatus.PinnedBySystem),
                )
            )

            assertThat(latest!![0].clickBehavior)
                .isInstanceOf(
                    OngoingActivityChipModel.ClickBehavior.ShowHeadsUpNotification::class.java
                )
        }

    @Test
    fun chip_hun_pinnedByUser_forDifferentChip_clickBehaviorIsShowHun() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            setNotifs(
                listOf(
                    activeNotificationModel(
                        "notif",
                        statusBarChipIcon = null,
                        promotedContent = newPromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )

            headsUpNotificationRepository.setNotifications(
                UnconfinedFakeHeadsUpRowRepository(
                    key = "otherNotifPinnedByUser",
                    pinnedStatus = MutableStateFlow(PinnedStatus.PinnedByUser),
                )
            )

            assertThat(latest!![0].clickBehavior)
                .isInstanceOf(
                    OngoingActivityChipModel.ClickBehavior.ShowHeadsUpNotification::class.java
                )
        }

    @Test
    fun chip_hun_pinnedByUser_forThisChip_clickBehaviorIsHideHun() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.chips)

            setNotifs(
                listOf(
                    activeNotificationModel(
                        "notif",
                        statusBarChipIcon = null,
                        promotedContent = newPromotedNotificationContentBuilder("notif").build(),
                    )
                )
            )

            headsUpNotificationRepository.setNotifications(
                UnconfinedFakeHeadsUpRowRepository(
                    key = "notif",
                    pinnedStatus = MutableStateFlow(PinnedStatus.PinnedByUser),
                )
            )

            assertThat(latest!![0].clickBehavior)
                .isInstanceOf(
                    OngoingActivityChipModel.ClickBehavior.HideHeadsUpNotification::class.java
                )
        }

    @Test
    @EnableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun chipWithReturnAnimation_updatesCorrectly_withStateAndTransitionState() =
        kosmos.runTest {
            val notifKey = "notif"
            val uid = 20
            val expandable = mock<Expandable>()
            val activityController = mock<ActivityTransitionAnimator.Controller>()
            whenever(
                    expandable.activityTransitionController(
                        anyOrNull(),
                        anyOrNull(),
                        any(),
                        anyOrNull(),
                        any(),
                    )
                )
                .thenReturn(activityController)

            val latest by collectLastValue(underTest.chips)

            // Start off with no notifs.
            assertThat(latest).isEmpty()

            // Notif appears [isAppVisible=true, NoTransition].
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = notifKey,
                    packageName = notifKey,
                    uid = uid,
                    statusBarChipIcon = null,
                    contentIntent = pendingIntent,
                    promotedContent = newPromotedNotificationContentBuilder(notifKey).build(),
                )
            )
            activityManagerRepository.fake.setIsAppVisible(uid, isAppVisible = true)
            assertThat(latest!!).hasSize(1)
            assertThat((latest!![0]).isHidden).isTrue()
            assertThat(latest!![0].transitionManager!!.hideChipForTransition).isFalse()
            val factory = latest!![0].transitionManager!!.controllerFactory
            assertThat(factory!!.component).isEqualTo(COMPONENT)

            // Request a return transition [isAppVisible=true, NoTransition -> ReturnRequested].
            factory.onCompose(expandable)
            var controller = factory.createController(forLaunch = false)
            assertThat(latest!!).hasSize(1)
            assertThat((latest!![0]).isHidden).isFalse()
            assertThat(latest!![0].transitionManager!!.controllerFactory).isEqualTo(factory)
            assertThat(latest!![0].transitionManager!!.hideChipForTransition).isTrue()

            // Start the return transition [isAppVisible=true, ReturnRequested -> Returning].
            controller.onTransitionAnimationStart(isExpandingFullyAbove = false)
            assertThat(latest!!).hasSize(1)
            assertThat((latest!![0]).isHidden).isFalse()
            assertThat(latest!![0].transitionManager!!.controllerFactory).isEqualTo(factory)
            assertThat(latest!![0].transitionManager!!.hideChipForTransition).isFalse()

            // End the return transition [isAppVisible=true, Returning -> NoTransition].
            controller.onTransitionAnimationEnd(isExpandingFullyAbove = false)
            assertThat(latest!!).hasSize(1)
            assertThat((latest!![0]).isHidden).isFalse()
            assertThat(latest!![0].transitionManager!!.controllerFactory).isEqualTo(factory)
            assertThat(latest!![0].transitionManager!!.hideChipForTransition).isFalse()

            // Settle the return transition [isAppVisible=true -> isAppVisible=false, NoTransition].
            kosmos.activityManagerRepository.fake.setIsAppVisible(uid, false)
            assertThat(latest!!).hasSize(1)
            assertThat((latest!![0]).isHidden).isFalse()
            assertThat(latest!![0].transitionManager!!.controllerFactory).isEqualTo(factory)
            assertThat(latest!![0].transitionManager!!.hideChipForTransition).isFalse()

            // End the call [isAppVisible=false -> no notif, NoTransition].
            activeNotificationListRepository.removeNotif(notifKey)
            assertThat(latest).isEmpty()

            // End the call with app visible [isAppVisible=true -> no notif, NoTransition].
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = notifKey,
                    packageName = notifKey,
                    uid = uid,
                    statusBarChipIcon = null,
                    contentIntent = pendingIntent,
                    promotedContent = newPromotedNotificationContentBuilder(notifKey).build(),
                )
            )
            kosmos.activityManagerRepository.fake.setIsAppVisible(uid, true)
            activeNotificationListRepository.removeNotif(notifKey)
            assertThat(latest).isEmpty()
        }

    @Test
    @EnableFlags(StatusBarChipsReturnAnimations.FLAG_NAME)
    fun chipWithReturnAnimation_updatesCorrectly_whenAppIsLaunchedAndClosedWithoutAnimation() =
        kosmos.runTest {
            val notifKey = "notif"
            val uid = 20
            val expandable = mock<Expandable>()
            val activityController = mock<ActivityTransitionAnimator.Controller>()
            whenever(
                    expandable.activityTransitionController(
                        anyOrNull(),
                        anyOrNull(),
                        any(),
                        anyOrNull(),
                        any(),
                    )
                )
                .thenReturn(activityController)

            val latest by collectLastValue(underTest.chips)

            // Start off with one notif with visible app.
            activeNotificationListRepository.addNotif(
                activeNotificationModel(
                    key = notifKey,
                    packageName = notifKey,
                    uid = uid,
                    statusBarChipIcon = null,
                    contentIntent = pendingIntent,
                    promotedContent = newPromotedNotificationContentBuilder(notifKey).build(),
                )
            )
            activityManagerRepository.fake.setIsAppVisible(uid, isAppVisible = true)
            assertThat(latest!!).hasSize(1)
            assertThat(latest!![0].isHidden).isTrue()
            assertThat(latest!![0].transitionManager!!.hideChipForTransition).isFalse()
            val factory = latest!![0].transitionManager!!.controllerFactory
            assertThat(factory!!.component).isEqualTo(COMPONENT)

            // Close the app without a return transition (e.g. swap to a different app)
            // [isAppVisible=true -> isAppVisible=false, NoTransition].
            kosmos.activityManagerRepository.fake.setIsAppVisible(uid, false)
            assertThat(latest!!).hasSize(1)
            assertThat(latest!![0].isHidden).isFalse()
            assertThat(latest!![0].transitionManager!!.controllerFactory).isEqualTo(factory)
            assertThat(latest!![0].transitionManager!!.hideChipForTransition).isFalse()

            // Launch the app from another source (e.g. the app icon) [isAppVisible=true ->
            // isAppVisible=false, NoTransition].
            kosmos.activityManagerRepository.fake.setIsAppVisible(uid, true)
            assertThat(latest!!).hasSize(1)
            assertThat(latest!![0].isHidden).isTrue()
            assertThat(latest!![0].transitionManager!!.controllerFactory).isEqualTo(factory)
            assertThat(latest!![0].transitionManager!!.hideChipForTransition).isFalse()
        }

    private fun setNotifs(notifs: List<ActiveNotificationModel>) {
        activeNotificationListRepository.activeNotifications.value =
            ActiveNotificationsStore.Builder()
                .apply { notifs.forEach { addIndividualNotif(it) } }
                .build()
    }

    private fun newPromotedNotificationContentBuilder(
        key: String
    ): PromotedNotificationContentBuilder {
        val builder = PromotedNotificationContentBuilder(key)
        if (NotificationChipFromCompactContent.isEnabled) {
            builder.applyToShared {
                // If NOTIFICATION_CHIP_FROM_COMPACT_CONTENT is active, then
                // PromotedNotificationContentModel must have SOME compactContent, otherwise
                // toPrunedModel() will throw. We provide a default here. Tests that want to check
                // chip icon/text should set an explicit one.
                this.compactContent =
                    Notification.ResolvedBasicCompactContent(
                        COMPACT_ICON,
                        null,
                        Notification.SEMANTIC_STYLE_UNSPECIFIED,
                    )
            }
        }

        return builder
    }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.progressionOf(
                FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT,
                android.app.Flags.FLAG_API_NOTIFICATION_CHIP,
            )
        }

        private val COMPONENT = ComponentName("package", "class")

        private val COMPACT_ICON =
            Notification.ResolvedCompactIcon(
                Notification.ResolvedCompactIcon.SOURCE_SMALL_ICON,
                null,
            )

        fun assertIsNotifChip(
            latest: OngoingActivityChipModel?,
            context: Context,
            expectedNotificationKey: String,
            expectedContentDescriptionSubstrings: List<String> = emptyList(),
        ) {
            val active = latest as OngoingActivityChipModel.Active
            assertThat(active.isImportantForPrivacy).isFalse()
            assertThat(active.icon)
                .isInstanceOf(
                    OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon::class.java
                )
            val icon = active.icon as OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon

            assertThat(icon.notificationKey).isEqualTo(expectedNotificationKey)
            expectedContentDescriptionSubstrings.forEach {
                assertThat(icon.contentDescription.loadContentDescription(context)).contains(it)
            }
        }

        fun assertIsNotifKey(latest: OngoingActivityChipModel?, expectedKey: String) {
            assertThat(
                    ((latest as OngoingActivityChipModel.Active).icon
                            as OngoingActivityChipModel.ChipIcon.StatusBarNotificationIcon)
                        .notificationKey
                )
                .isEqualTo(expectedKey)
        }
    }
}
