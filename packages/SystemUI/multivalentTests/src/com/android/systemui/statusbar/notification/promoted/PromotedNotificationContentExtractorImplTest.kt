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

package com.android.systemui.statusbar.notification.promoted

import android.app.Flags.FLAG_API_METRIC_STYLE
import android.app.Flags.FLAG_API_NOTIFICATION_SEMANTIC_STYLE
import android.app.Flags.FLAG_METRIC_VALUE_ALTERNATIVE_STRINGS
import android.app.Notification
import android.app.Notification.BigTextStyle
import android.app.Notification.CallStyle
import android.app.Notification.FLAG_PROMOTED_ONGOING
import android.app.Notification.MessagingStyle
import android.app.Notification.ProgressStyle
import android.app.Notification.ProgressStyle.Segment
import android.app.PendingIntent
import android.app.Person
import android.content.Intent
import android.graphics.drawable.Icon
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.statusbar.NotificationLockscreenUserManager.REDACTION_TYPE_NONE
import com.android.systemui.statusbar.NotificationLockscreenUserManager.REDACTION_TYPE_PUBLIC
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.NotificationEntryBuilder
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel.Style
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModel.When
import com.android.systemui.statusbar.notification.promoted.shared.model.PromotedNotificationContentModels
import com.android.systemui.statusbar.notification.row.RowImageInflater
import com.android.systemui.statusbar.notification.shared.Metric
import com.android.systemui.testKosmos
import com.android.systemui.util.time.fakeSystemClock
import com.android.systemui.util.time.systemClock
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertNotNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class PromotedNotificationContentExtractorImplTest : SysuiTestCase() {
    private val kosmos = testKosmos().apply { systemClock = fakeSystemClock }

    private val Kosmos.underTest by Kosmos.Fixture { promotedNotificationContentExtractor }
    private val Kosmos.rowImageInflater by
        Kosmos.Fixture { RowImageInflater.newInstance(previousIndex = null, reinflating = false) }
    private val Kosmos.imageModelProvider by
        Kosmos.Fixture { rowImageInflater.useForContentModel() }

    @Test
    fun shouldExtract() =
        kosmos.runTest {
            val entry = createEntry()
            val content = extractContent(entry)
            assertThat(content).isNotNull()
        }

    @Test
    fun shouldNotExtract_becauseNotPromoted() =
        kosmos.runTest {
            val entry = createEntry(promoted = false)
            val content = extractContent(entry)
            assertThat(content).isNull()
        }

    @Test
    fun extractsContent_commonFields() =
        kosmos.runTest {
            val entry = createEntry {
                setSubText(TEST_SUB_TEXT)
                setContentTitle(TEST_CONTENT_TITLE)
                setContentText(TEST_CONTENT_TEXT)
            }

            val content = requireContent(entry)

            content.privateVersion.apply {
                assertThat(subText).isEqualTo(TEST_SUB_TEXT)
                assertThat(title).isEqualTo(TEST_CONTENT_TITLE)
                assertThat(text).isEqualTo(TEST_CONTENT_TEXT)
            }

            content.publicVersion.apply {
                assertThat(subText).isNull()
                assertThat(title).isNull()
                assertThat(text).isNull()
            }
        }

    @Test
    fun extractsContent_commonFields_noRedaction() =
        kosmos.runTest {
            val entry = createEntry {
                setSubText(TEST_SUB_TEXT)
                setContentTitle(TEST_CONTENT_TITLE)
                setContentText(TEST_CONTENT_TEXT)
            }

            val content = requireContent(entry, redactionType = REDACTION_TYPE_NONE)

            content.privateVersion.apply {
                assertThat(subText).isEqualTo(TEST_SUB_TEXT)
                assertThat(title).isEqualTo(TEST_CONTENT_TITLE)
                assertThat(text).isEqualTo(TEST_CONTENT_TEXT)
            }

            content.publicVersion.apply {
                assertThat(subText).isEqualTo(TEST_SUB_TEXT)
                assertThat(title).isEqualTo(TEST_CONTENT_TITLE)
                assertThat(text).isEqualTo(TEST_CONTENT_TEXT)
            }
        }

    @Test
    @DisableFlags(FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT)
    fun extractContent_chipFlagOff_shortCriticalTextExtracted() =
        kosmos.runTest {
            val entry = createEntry { setShortCriticalText(TEST_SHORT_CRITICAL_TEXT) }

            val content = requireContent(entry).privateVersion

            assertThat(content.shortCriticalText).isEqualTo(TEST_SHORT_CRITICAL_TEXT)
            assertThat(content.compactContent).isNull()
        }

    @Test
    @EnableFlags(
        FLAG_NOTIFICATION_CHIP_FROM_COMPACT_CONTENT,
        FLAG_API_METRIC_STYLE,
        FLAG_API_NOTIFICATION_SEMANTIC_STYLE,
        FLAG_METRIC_VALUE_ALTERNATIVE_STRINGS,
    )
    fun extractContent_compactContent_compactContentExtracted() =
        kosmos.runTest {
            val entry = createEntry {
                setShortCriticalText("Won't be used")
                // There are extensive CompactContent->ResolvedCompactContent tests in CTS, so we
                // just try a simple example here.
                setCompactContent(
                    Notification.BasicCompactContent(
                        Notification.CompactText.fromMetricValue(Notification.Metric.FixedInt(598))
                    )
                )
            }

            val content = requireContent(entry).privateVersion
            assertThat(content.compactContent)
                .isInstanceOf(Notification.ResolvedBasicCompactContent::class.java)
            assertThat(content.shortCriticalText).isNull()
        }

    @Test
    fun extractContent_noShortCriticalTextSet_textIsNull() =
        kosmos.runTest {
            val entry = createEntry { setShortCriticalText(null) }

            val content = requireContent(entry).privateVersion

            assertThat(content.shortCriticalText).isNull()
        }

    @Test
    fun extractTime_none() =
        kosmos.runTest {
            assertExtractedTime(
                hasTime = false,
                hasChronometer = false,
                expected = ExpectedTime.Null,
            )
        }

    @Test
    fun extractTime_basicTimeZero() =
        kosmos.runTest {
            assertExtractedTime(
                hasTime = true,
                hasChronometer = false,
                provided = ProvidedTime.Value(0L),
                expected = ExpectedTime.Time,
            )
        }

    @Test
    fun extractTime_basicTimeNow() =
        kosmos.runTest {
            assertExtractedTime(
                hasTime = true,
                hasChronometer = false,
                provided = ProvidedTime.Offset(Duration.ZERO),
                expected = ExpectedTime.Time,
            )
        }

    @Test
    fun extractTime_basicTimePast() =
        kosmos.runTest {
            assertExtractedTime(
                hasTime = true,
                hasChronometer = false,
                provided = ProvidedTime.Offset((-5).minutes),
                expected = ExpectedTime.Time,
            )
        }

    @Test
    fun extractTime_basicTimeFuture() =
        kosmos.runTest {
            assertExtractedTime(
                hasTime = true,
                hasChronometer = false,
                provided = ProvidedTime.Offset(5.minutes),
                expected = ExpectedTime.Time,
            )
        }

    @Test
    fun extractTime_countUpZero() =
        kosmos.runTest {
            assertExtractedTime(
                hasTime = false,
                hasChronometer = true,
                isCountDown = false,
                provided = ProvidedTime.Value(0L),
                expected = ExpectedTime.CountUp,
            )
        }

    @Test
    fun extractTime_countUpNow() =
        kosmos.runTest {
            assertExtractedTime(
                hasTime = false,
                hasChronometer = true,
                isCountDown = false,
                provided = ProvidedTime.Offset(Duration.ZERO),
                expected = ExpectedTime.CountUp,
            )
        }

    @Test
    fun extractTime_countUpPast() =
        kosmos.runTest {
            assertExtractedTime(
                hasTime = false,
                hasChronometer = true,
                isCountDown = false,
                provided = ProvidedTime.Offset((-5).minutes),
                expected = ExpectedTime.CountUp,
            )
        }

    @Test
    fun extractTime_countUpFuture() =
        kosmos.runTest {
            assertExtractedTime(
                hasTime = false,
                hasChronometer = true,
                isCountDown = false,
                provided = ProvidedTime.Offset(5.minutes),
                expected = ExpectedTime.CountUp,
            )
        }

    @Test
    fun extractTime_countDownZero() =
        kosmos.runTest {
            assertExtractedTime(
                hasTime = false,
                hasChronometer = true,
                isCountDown = true,
                provided = ProvidedTime.Value(0L),
                expected = ExpectedTime.CountDown,
            )
        }

    @Test
    fun extractTime_countDownNow() =
        kosmos.runTest {
            assertExtractedTime(
                hasTime = false,
                hasChronometer = true,
                isCountDown = true,
                provided = ProvidedTime.Offset(Duration.ZERO),
                expected = ExpectedTime.CountDown,
            )
        }

    @Test
    fun extractTime_countDownPast() =
        kosmos.runTest {
            assertExtractedTime(
                hasTime = false,
                hasChronometer = true,
                isCountDown = true,
                provided = ProvidedTime.Offset((-5).minutes),
                expected = ExpectedTime.CountDown,
            )
        }

    @Test
    fun extractTime_countDownFuture() =
        kosmos.runTest {
            assertExtractedTime(
                hasTime = false,
                hasChronometer = true,
                isCountDown = true,
                provided = ProvidedTime.Offset(5.minutes),
                expected = ExpectedTime.CountDown,
            )
        }

    @Test
    fun extractTime_prefersChronometerToWhen() =
        kosmos.runTest {
            assertExtractedTime(
                hasTime = true,
                hasChronometer = true,
                expected = ExpectedTime.CountUp,
            )
        }

    private sealed class ProvidedTime {
        data class Value(val value: Long) : ProvidedTime()

        data class Offset(val offset: Duration = Duration.ZERO) : ProvidedTime()
    }

    private enum class ExpectedTime {
        Null,
        Time,
        CountUp,
        CountDown,
    }

    private fun Kosmos.assertExtractedTime(
        hasTime: Boolean = false,
        hasChronometer: Boolean = false,
        isCountDown: Boolean = false,
        provided: ProvidedTime = ProvidedTime.Offset(),
        expected: ExpectedTime,
    ) {
        // Set the two timebases to different (arbitrary) numbers, so we can verify whether the
        // extractor is doing the timebase adjustment correctly.
        fakeSystemClock.setCurrentTimeMillis(1_739_570_992_579L)
        fakeSystemClock.setElapsedRealtime(1_380_967_080L)

        val providedCurrentTime =
            when (provided) {
                is ProvidedTime.Value -> provided.value
                is ProvidedTime.Offset ->
                    systemClock.currentTimeMillis() + provided.offset.inWholeMilliseconds
            }

        val expectedCurrentTime =
            when (providedCurrentTime) {
                0L -> systemClock.currentTimeMillis()
                else -> providedCurrentTime
            }

        val entry = createEntry {
            setShowWhen(hasTime)
            setUsesChronometer(hasChronometer)
            setChronometerCountDown(isCountDown)
            setWhen(providedCurrentTime)
        }

        val content = requireContent(entry).privateVersion

        when (expected) {
            ExpectedTime.Null -> assertThat(content.time).isNull()

            ExpectedTime.Time -> {
                val actual = assertNotNull(content.time as? When.Time)
                assertThat(actual.currentTimeMillis).isEqualTo(expectedCurrentTime)
            }

            ExpectedTime.CountDown,
            ExpectedTime.CountUp -> {
                val expectedElapsedRealtime =
                    expectedCurrentTime + systemClock.elapsedRealtime() -
                        systemClock.currentTimeMillis()

                val actual = assertNotNull(content.time as? When.Chronometer)
                assertThat(actual.elapsedRealtimeMillis).isEqualTo(expectedElapsedRealtime)
                assertThat(actual.isCountDown).isEqualTo(expected == ExpectedTime.CountDown)
            }
        }
    }

    // TODO: Add tests for the style of the publicVersion once we implement that

    @Test
    fun extractContent_fromBaseStyle() =
        kosmos.runTest {
            val entry = createEntry { setStyle(null) }

            val content = requireContent(entry)

            assertThat(content.privateVersion.style).isEqualTo(Style.Base)
            assertThat(content.publicVersion.style).isEqualTo(Style.Base)
        }

    @Test
    fun extractContent_fromBigTextStyle() =
        kosmos.runTest {
            val entry = createEntry {
                setContentTitle(TEST_CONTENT_TITLE)
                setContentText(TEST_CONTENT_TEXT)
                setStyle(
                    BigTextStyle()
                        .bigText(TEST_BIG_TEXT)
                        .setBigContentTitle(TEST_BIG_CONTENT_TITLE)
                        .setSummaryText(TEST_SUMMARY_TEXT)
                )
            }

            val content = requireContent(entry)

            assertThat(content.privateVersion.style).isEqualTo(Style.BigText)
            assertThat(content.privateVersion.title).isEqualTo(TEST_BIG_CONTENT_TITLE)
            assertThat(content.privateVersion.text).isEqualTo(TEST_BIG_TEXT)

            assertThat(content.publicVersion.style).isEqualTo(Style.Base)
            assertThat(content.publicVersion.title).isNull()
            assertThat(content.publicVersion.text).isNull()
        }

    @Test
    fun extractContent_fromBigTextStyle_fallbackToContentTitle() =
        kosmos.runTest {
            val entry = createEntry {
                setContentTitle(TEST_CONTENT_TITLE)
                setContentText(TEST_CONTENT_TEXT)
                setStyle(
                    BigTextStyle()
                        .bigText(TEST_BIG_TEXT)
                        // bigContentTitle unset
                        .setSummaryText(TEST_SUMMARY_TEXT)
                )
            }

            val content = requireContent(entry)

            assertThat(content.privateVersion.style).isEqualTo(Style.BigText)
            assertThat(content.privateVersion.title).isEqualTo(TEST_CONTENT_TITLE)
            assertThat(content.privateVersion.text).isEqualTo(TEST_BIG_TEXT)

            assertThat(content.publicVersion.style).isEqualTo(Style.Base)
            assertThat(content.publicVersion.title).isNull()
            assertThat(content.publicVersion.text).isNull()
        }

    @Test
    fun extractContent_fromBigTextStyle_fallbackToContentText() =
        kosmos.runTest {
            val entry = createEntry {
                setContentTitle(TEST_CONTENT_TITLE)
                setContentText(TEST_CONTENT_TEXT)
                setStyle(
                    BigTextStyle()
                        // bigText unset
                        .setBigContentTitle(TEST_BIG_CONTENT_TITLE)
                        .setSummaryText(TEST_SUMMARY_TEXT)
                )
            }

            val content = requireContent(entry)

            assertThat(content.privateVersion.style).isEqualTo(Style.BigText)
            assertThat(content.privateVersion.title).isEqualTo(TEST_BIG_CONTENT_TITLE)
            assertThat(content.privateVersion.text).isEqualTo(TEST_CONTENT_TEXT)

            assertThat(content.publicVersion.style).isEqualTo(Style.Base)
            assertThat(content.publicVersion.title).isNull()
            assertThat(content.publicVersion.text).isNull()
        }

    @Test
    fun extractContent_fromCallStyle() =
        kosmos.runTest {
            val hangUpIntent =
                PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent("hangup_action"),
                    PendingIntent.FLAG_IMMUTABLE,
                )
            val entry = createEntry {
                setStyle(CallStyle.forOngoingCall(TEST_PERSON, hangUpIntent))
            }

            val content = requireContent(entry)

            assertThat(content.privateVersion.style).isEqualTo(Style.Call)
            assertThat(content.privateVersion.title).isEqualTo(TEST_PERSON_NAME)

            assertThat(content.publicVersion.style).isEqualTo(Style.Base)
            assertThat(content.publicVersion.title).isNull()
            assertThat(content.publicVersion.text).isNull()
        }

    @Test
    fun extractContent_fromProgressStyle() =
        kosmos.runTest {
            val entry = createEntry {
                setStyle(ProgressStyle().addProgressSegment(Segment(100)).setProgress(75))
            }

            val content = requireContent(entry)

            assertThat(content.privateVersion.style).isEqualTo(Style.Progress)
            val newProgress = assertNotNull(content.privateVersion.newProgress)
            assertThat(newProgress.progress).isEqualTo(75)
            assertThat(newProgress.progressMax).isEqualTo(100)

            assertThat(content.publicVersion.style).isEqualTo(Style.Base)
            assertThat(content.publicVersion.title).isNull()
            assertThat(content.publicVersion.text).isNull()
            assertThat(content.publicVersion.newProgress).isNull()
        }

    @Test
    fun extractContent_fromIneligibleStyle() =
        kosmos.runTest {
            val entry = createEntry {
                setStyle(MessagingStyle(TEST_PERSON).addMessage("message text", 0L, TEST_PERSON))
            }

            val content = requireContent(entry)

            assertThat(content.privateVersion.style).isEqualTo(Style.Ineligible)

            assertThat(content.publicVersion.style).isEqualTo(Style.Ineligible)
        }

    @Test
    fun extractContent_fromOldProgressDeterminate() =
        kosmos.runTest {
            val entry = createEntry {
                setProgress(TEST_PROGRESS_MAX, TEST_PROGRESS, /* indeterminate= */ false)
            }

            val content = requireContent(entry)

            val oldProgress = assertNotNull(content.privateVersion.oldProgress)

            assertThat(oldProgress.progress).isEqualTo(TEST_PROGRESS)
            assertThat(oldProgress.max).isEqualTo(TEST_PROGRESS_MAX)
            assertThat(oldProgress.isIndeterminate).isFalse()
        }

    @Test
    fun extractContent_fromOldProgressIndeterminate() =
        kosmos.runTest {
            val entry = createEntry {
                setProgress(TEST_PROGRESS_MAX, TEST_PROGRESS, /* indeterminate= */ true)
            }

            val content = requireContent(entry)
            val oldProgress = assertNotNull(content.privateVersion.oldProgress)

            assertThat(oldProgress.max).isEqualTo(TEST_PROGRESS_MAX)
            assertThat(oldProgress.isIndeterminate).isTrue()
        }

    @Test
    @EnableFlags(FLAG_API_METRIC_STYLE, FLAG_METRIC_VALUE_ALTERNATIVE_STRINGS)
    fun extractContent_fromMetricStyle_singleTextValue() =
        kosmos.runTest {
            val entry = createEntry {
                setStyle(
                    Notification.MetricStyle()
                        .addMetric(
                            Notification.Metric(
                                Notification.Metric.FixedText("Value1", "unit"),
                                "Label1",
                            )
                        )
                )
            }

            val content = requireContent(entry)
            val privateVersion = content.privateVersion

            assertThat(privateVersion.style).isEqualTo(Style.MetricSingle)
            assertThat(privateVersion.metrics).hasSize(1)
            val metric = privateVersion.metrics?.get(0) as Metric.Text
            assertThat(metric.label).isEqualTo("Label1 (unit)")
            assertThat(metric.textVariants).containsExactly("Value1")
        }

    @Test
    @EnableFlags(FLAG_API_METRIC_STYLE, FLAG_METRIC_VALUE_ALTERNATIVE_STRINGS)
    fun extractContent_fromMetricStyle_singleIntegerValue() =
        kosmos.runTest {
            val entry = createEntry {
                setStyle(
                    Notification.MetricStyle()
                        .addMetric(
                            Notification.Metric(
                                Notification.Metric.FixedInt(12345, "unit"),
                                "LabelInt",
                            )
                        )
                )
            }

            val content = requireContent(entry)
            val privateVersion = content.privateVersion

            assertThat(privateVersion.style).isEqualTo(Style.MetricSingle)
            assertThat(privateVersion.metrics).hasSize(1)
            val metric = privateVersion.metrics?.get(0) as Metric.Text
            assertThat(metric.label).isEqualTo("LabelInt (unit)")
            assertThat(metric.textVariants).containsExactly("12,345", "12K")
        }

    @Test
    @EnableFlags(FLAG_API_METRIC_STYLE, FLAG_METRIC_VALUE_ALTERNATIVE_STRINGS)
    fun extractContent_fromMetricStyle_timeDifferenceWithInstant() =
        kosmos.runTest {
            val zeroTime = java.time.Instant.ofEpochMilli(100)
            val entry = createEntry {
                setStyle(
                    Notification.MetricStyle()
                        .addMetric(
                            Notification.Metric(
                                Notification.Metric.TimeDifference.forStopwatch(
                                    zeroTime,
                                    Notification.Metric.TimeDifference.FORMAT_CHRONOMETER,
                                ),
                                "Time Label",
                            )
                        )
                )
            }

            val content = requireContent(entry)
            val privateVersion = content.privateVersion

            assertThat(privateVersion.style).isEqualTo(Style.MetricSingle)
            assertThat(privateVersion.metrics).hasSize(1)
            val metric = privateVersion.metrics?.get(0) as Metric.TimeDifference.Instant
            assertThat(metric.label).isEqualTo("Time Label")
            assertThat(metric.zeroTime).isEqualTo(zeroTime)
            assertThat(metric.isTimer).isFalse()
            assertThat(metric.useAdaptiveFormat).isFalse()
        }

    @Test
    @EnableFlags(FLAG_API_METRIC_STYLE, FLAG_METRIC_VALUE_ALTERNATIVE_STRINGS)
    fun extractContent_fromMetricStyle_timeDifferenceWithPausedDuration() =
        kosmos.runTest {
            val pausedDuration = java.time.Duration.ofSeconds(30)
            val entry = createEntry {
                setStyle(
                    Notification.MetricStyle()
                        .addMetric(
                            Notification.Metric(
                                Notification.Metric.TimeDifference.forPausedTimer(
                                    pausedDuration,
                                    Notification.Metric.TimeDifference.FORMAT_ADAPTIVE,
                                ),
                                "Paused Timer",
                            )
                        )
                )
            }

            val content = requireContent(entry)
            val privateVersion = content.privateVersion
            val metric = privateVersion.metrics?.get(0) as Metric.TimeDifference.Paused
            assertThat(metric.label).isEqualTo("Paused Timer")
            assertThat(metric.pausedDuration).isEqualTo(pausedDuration)
            assertThat(metric.isTimer).isTrue()
        }

    @Test
    @EnableFlags(FLAG_API_METRIC_STYLE, FLAG_METRIC_VALUE_ALTERNATIVE_STRINGS)
    fun extractContent_fromMetricStyle_multipleMetrics() =
        kosmos.runTest {
            val entry = createEntry {
                setStyle(
                    Notification.MetricStyle()
                        .addMetric(
                            Notification.Metric(Notification.Metric.FixedText("Value1"), "Label1")
                        )
                        .addMetric(
                            Notification.Metric(
                                Notification.Metric.TimeDifference.forTimer(
                                    12345L,
                                    Notification.Metric.TimeDifference.FORMAT_CHRONOMETER,
                                ),
                                "Timer",
                            )
                        )
                        .addMetric(Notification.Metric(Notification.Metric.FixedInt(42), "Label3"))
                )
            }

            val content = requireContent(entry)
            val privateVersion = content.privateVersion

            assertThat(privateVersion.style).isEqualTo(Style.Metric)
            assertThat(privateVersion.metrics).hasSize(3)

            val metric1 = privateVersion.metrics?.get(0) as Metric.Text
            assertThat(metric1.label).isEqualTo("Label1")
            assertThat(metric1.textVariants).containsExactly("Value1")

            val metric2 = privateVersion.metrics?.get(1) as Metric.TimeDifference.ElapsedRealtime
            assertThat(metric2.label).isEqualTo("Timer")
            assertThat(metric2.zeroElapsedRealtime).isEqualTo(12345L)
            assertThat(metric2.isTimer).isTrue()
            assertThat(metric2.useAdaptiveFormat).isFalse()

            val metric3 = privateVersion.metrics?.get(2) as Metric.Text
            assertThat(metric3.label).isEqualTo("Label3")
            assertThat(metric3.textVariants).containsExactly("42")
        }

    @Test
    @EnableFlags(FLAG_API_METRIC_STYLE, FLAG_METRIC_VALUE_ALTERNATIVE_STRINGS)
    fun extractContent_fromMetricStyle_tooManyMetrics() =
        kosmos.runTest {
            val entry = createEntry {
                setStyle(
                    Notification.MetricStyle()
                        .addMetric(
                            Notification.Metric(Notification.Metric.FixedText("Value1"), "Label1")
                        )
                        .addMetric(
                            Notification.Metric(Notification.Metric.FixedText("Value2"), "Label2")
                        )
                        .addMetric(
                            Notification.Metric(Notification.Metric.FixedText("Value3"), "Label3")
                        )
                        .addMetric(
                            Notification.Metric(Notification.Metric.FixedText("Value4"), "Label4")
                        )
                )
            }

            val content = requireContent(entry)
            val privateVersion = content.privateVersion

            assertThat(privateVersion.style).isEqualTo(Style.Metric)
            val metric1 = privateVersion.metrics?.get(0) as Metric.Text
            assertThat(metric1.label).isEqualTo("Label1")
            assertThat(metric1.textVariants).containsExactly("Value1")
            val metric2 = privateVersion.metrics?.get(1) as Metric.Text
            assertThat(metric2.label).isEqualTo("Label2")
            assertThat(metric2.textVariants).containsExactly("Value2")
            val metric3 = privateVersion.metrics?.get(2) as Metric.Text
            assertThat(metric3.label).isEqualTo("Label3")
            assertThat(metric3.textVariants).containsExactly("Value3")
            val metric4 = privateVersion.metrics?.get(3) as Metric.Text
            assertThat(metric4.label).isEqualTo("Label4")
            assertThat(metric4.textVariants).containsExactly("Value4")
        }

    private fun Kosmos.requireContent(
        entry: NotificationEntry,
        redactionType: Int = REDACTION_TYPE_PUBLIC,
    ): PromotedNotificationContentModels = assertNotNull(extractContent(entry, redactionType))

    private fun Kosmos.extractContent(
        entry: NotificationEntry,
        redactionType: Int = REDACTION_TYPE_PUBLIC,
    ): PromotedNotificationContentModels? {
        val recoveredBuilder = Notification.Builder(context, entry.sbn.notification)
        return underTest.extractContent(
            entry,
            recoveredBuilder,
            redactionType,
            imageModelProvider,
            context,
            context,
        )
    }

    private fun Kosmos.createEntry(
        promoted: Boolean = true,
        builderBlock: Notification.Builder.() -> Unit = {},
    ): NotificationEntry {
        val notif =
            Notification.Builder(context, "channel")
                .setSmallIcon(Icon.createWithContentUri("content://foo/bar"))
                .also(builderBlock)
                .build()
        if (promoted) {
            notif.flags = FLAG_PROMOTED_ONGOING
        }
        // Notification uses System.currentTimeMillis() to initialize creationTime; overwrite that
        // with the value from our mock clock.
        if (notif.creationTime != 0L) {
            notif.creationTime = systemClock.currentTimeMillis()
        }
        return NotificationEntryBuilder()
            .setPkg("com.android.systemui") // use a real package name, since we're fetching icons
            .setNotification(notif)
            .build()
    }

    companion object {
        private const val TEST_SUB_TEXT = "sub text"
        private const val TEST_CONTENT_TITLE = "content title"
        private const val TEST_CONTENT_TEXT = "content text"
        private const val TEST_SHORT_CRITICAL_TEXT = "short"

        private const val TEST_BIG_CONTENT_TITLE = "big content title"
        private const val TEST_BIG_TEXT = "big text"
        private const val TEST_SUMMARY_TEXT = "summary text"

        private const val TEST_PROGRESS = 50
        private const val TEST_PROGRESS_MAX = 100

        private const val TEST_PERSON_NAME = "person name"
        private const val TEST_PERSON_KEY = "person key"
        private val TEST_PERSON =
            Person.Builder().setKey(TEST_PERSON_KEY).setName(TEST_PERSON_NAME).build()
    }
}
