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

import static android.app.Notification.EXTRA_METRICS;
import static android.app.Notification.EXTRA_METRICS_CRITICAL_INDEX;
import static android.app.Notification.FLAG_PROMOTED_ONGOING;
import static android.app.Notification.SEMANTIC_STYLE_CAUTION;

import static com.google.common.truth.Truth.assertThat;

import android.app.Notification.Metric;
import android.app.Notification.Metric.FixedDate;
import android.app.Notification.Metric.FixedFloat;
import android.app.Notification.Metric.FixedInt;
import android.app.Notification.Metric.FixedText;
import android.app.Notification.Metric.FixedTime;
import android.app.Notification.Metric.MetricValue.ValueString;
import android.app.Notification.Metric.TimeDifference;
import android.app.Notification.MetricStyle;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.SystemClock;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.widget.Chronometer;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.R;

import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

@RunWith(AndroidJUnit4.class)
@Presubmit
@EnableFlags(Flags.FLAG_API_METRIC_STYLE)
public class NotificationMetricStyleTest {

    @Rule
    public final Expect expect = Expect.create();

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    // May 30, 2025 14:26:25 UTC
    private static final Instant NOW = Instant.ofEpochMilli(1748615185000L);
    // May 30, 2025
    private static final LocalDate TODAY = LocalDate.of(2025, 5, 30);
    // May 29, 2025 -> less than 4 months have passed
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    // May 31, 2025 -> less than 4 months away
    private static final LocalDate TOMORROW = TODAY.plusDays(1);
    // December 18, 2025 -> more than 4 months have passed
    private static final LocalDate LONG_AGO = LocalDate.of(2025, 1, 1);
    // December 18, 2025 -> more than 4 months away
    private static final LocalDate FAR_AWAY = LocalDate.of(2025, 12, 18);

    private static final long ELAPSED_REALTIME = 300_000;

    private static final String NBSP = "\u00a0";
    private static final String NNBSP = "\u202f";

    private Context mContext;
    private Locale mPreviousLocale;
    private TimeZone mPreviousTimeZone;
    private String mPrevious24HourSetting;

    private Notification.Colors mDefaultColors;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        mDefaultColors = new Notification.Colors();
        boolean nightMode = (mContext.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        mDefaultColors.resolvePalette(mContext, Notification.COLOR_DEFAULT, false, nightMode);

        // Force some values that can depend on device current settings to a known state.
        mPreviousLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
        mPreviousTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Montevideo")); // (not UTC!)
        mPrevious24HourSetting = Settings.System.getString(mContext.getContentResolver(),
                Settings.System.TIME_12_24);
        Settings.System.putString(mContext.getContentResolver(), Settings.System.TIME_12_24, "12");

        Notification.sSystemClock = () -> NOW;
        Notification.sElapsedRealtimeClock = () -> ELAPSED_REALTIME;
    }

    @After
    public void tearDown() {
        if (mPreviousLocale != null) {
            Locale.setDefault(mPreviousLocale);
        }
        if (mPreviousTimeZone != null) {
            TimeZone.setDefault(mPreviousTimeZone);
        }
        Settings.System.putString(mContext.getContentResolver(), Settings.System.TIME_12_24,
                mPrevious24HourSetting);
        Notification.sSystemClock = InstantSource.system();
        Notification.sElapsedRealtimeClock = () -> SystemClock.elapsedRealtime();
    }

    @Test
    public void addExtras_writesExtras() {
        MetricStyle style = new MetricStyle()
                .addMetric(new Metric(new FixedInt(4, "birds"), "4"))
                .addMetric(new Metric(new FixedInt(5, "rings"), "5"))
                .addMetric(new Metric(new FixedInt(6, "geese"), "6"))
                .addMetric(new Metric(new FixedInt(7, "swans"), "7"))
                .addMetric(new Metric(new FixedInt(8, "maids"), "8"))
                .setCriticalMetric(2);

        Bundle bundle = new Bundle();
        style.addExtras(bundle);

        ArrayList<Bundle> storedBundles = bundle.getParcelableArrayList(EXTRA_METRICS,
                Bundle.class);
        assertThat(storedBundles).isNotNull();
        assertThat(storedBundles).hasSize(5);
        assertThat(bundle.getInt(EXTRA_METRICS_CRITICAL_INDEX)).isEqualTo(2);
    }

    @Test
    public void restoreFromExtras_restoresWrittenMetrics() {
        Bundle bundle = new Bundle();
        MetricStyle original = new MetricStyle()
                .addMetric(new Metric(
                        TimeDifference.forTimer(Instant.ofEpochMilli(1),
                                TimeDifference.FORMAT_ADAPTIVE),
                        "Time:"))
                .addMetric(new Metric(
                        TimeDifference.forTimer(123456L,
                                TimeDifference.FORMAT_ADAPTIVE),
                        "Time:"))
                .addMetric(new Metric(
                        TimeDifference.forPausedStopwatch(Duration.ofHours(4),
                                TimeDifference.FORMAT_CHRONOMETER),
                        "Stopwatch:"))
                .addMetric(new Metric(
                        new FixedDate(LocalDate.of(2025, 6, 2), FixedDate.FORMAT_SHORT_DATE),
                        "Event date:"))
                .addMetric(new Metric(
                        new FixedTime(LocalTime.of(10, 30)),
                        "Event time:"))
                .addMetric(new Metric(
                        new FixedInt(12, "drummers"), "Label"))
                .addMetric(new Metric(
                        new FixedInt(42), "Answer"))
                .addMetric(new Metric(
                        new FixedFloat(0.75f), "Readiness"))
                .addMetric(new Metric(
                        new FixedFloat(273f, "°K"),
                        "Temp"))
                .addMetric(new Metric(
                        new FixedFloat(12.345f, null, 0, 3),
                        "Active time"))
                .addMetric(new Metric(
                        new FixedText("A LOT", "things"), "With unit"))
                .addMetric(new Metric(
                        new FixedText("This is the last"), "Last"))
                .setCriticalMetric(5);

        original.addExtras(bundle);
        MetricStyle recovered = new MetricStyle();
        recovered.restoreFromExtras(bundle);

        assertThat(recovered).isEqualTo(original);
    }

    @Test
    public void areNotificationsVisiblyDifferent_sameMetrics_false() {
        MetricStyle style1 = new MetricStyle()
                .addMetric(new Metric(new FixedInt(1), "Cal"))
                .addMetric(new Metric(new FixedInt(2), "Cal"));

        MetricStyle style2 = new MetricStyle()
                .addMetric(new Metric(new FixedInt(1), "Cal"))
                .addMetric(new Metric(new FixedInt(2), "Cal"));

        assertThat(style1.areNotificationsVisiblyDifferent(style2)).isFalse();
        assertThat(style2.areNotificationsVisiblyDifferent(style1)).isFalse();
    }

    @Test
    public void areNotificationsVisiblyDifferent_differentMetrics_true() {
        MetricStyle style1 = new MetricStyle()
                .addMetric(new Metric(new FixedInt(1, "thingies"), "a"))
                .addMetric(new Metric(new FixedInt(2, "widgets"), "b"));

        MetricStyle style2 = new MetricStyle()
                .addMetric(new Metric(new FixedInt(1, "gizmos"), "c"))
                .addMetric(new Metric(new FixedInt(2, "doodads"), "d"));

        assertThat(style1.areNotificationsVisiblyDifferent(style2)).isTrue();
        assertThat(style2.areNotificationsVisiblyDifferent(style1)).isTrue();
    }

    @Test
    public void areNotificationsVisiblyDifferent_differentMetricCounts_true() {
        MetricStyle style1 = new MetricStyle()
                .addMetric(new Metric(new FixedInt(1, "gizmos"), "a"))
                .addMetric(new Metric(new FixedInt(2, "doodads"), "b"));

        MetricStyle style2 = new MetricStyle()
                .addMetric(new Metric(new FixedInt(1, "gizmos"), "a"))
                .addMetric(new Metric(new FixedInt(2, "doodads"), "b"))
                .addMetric(new Metric(new FixedInt(3, "whatsits"), "c"));

        assertThat(style1.areNotificationsVisiblyDifferent(style2)).isTrue();
        assertThat(style2.areNotificationsVisiblyDifferent(style1)).isTrue();
    }

    @Test
    public void areNotificationsVisiblyDifferent_firstThreeEqual_false() {
        MetricStyle style1 = new MetricStyle()
                .addMetric(new Metric(new FixedInt(1), "a"))
                .addMetric(new Metric(new FixedInt(2), "b"))
                .addMetric(new Metric(new FixedInt(3), "c"))
                .addMetric(new Metric(new FixedText("Ignored thing"), "d"));

        MetricStyle style2 = new MetricStyle()
                .addMetric(new Metric(new FixedInt(1), "a"))
                .addMetric(new Metric(new FixedInt(2), "b"))
                .addMetric(new Metric(new FixedInt(3), "c"))
                .addMetric(new Metric(new FixedText("Also ignored"), "d"))
                .addMetric(new Metric(new FixedText("And this too"), "e"));

        assertThat(style1.areNotificationsVisiblyDifferent(style2)).isFalse();
        assertThat(style2.areNotificationsVisiblyDifferent(style1)).isFalse();
    }

    @Test
    public void valueToString_fixedDateAutomatic() {
        FixedDate today = new FixedDate(TODAY, FixedDate.FORMAT_AUTOMATIC);
        expect.that(today.toValueString(mContext)).isEqualTo(new ValueString("5/30"));

        FixedDate tomorrow = new FixedDate(TOMORROW, FixedDate.FORMAT_AUTOMATIC);
        expect.that(tomorrow.toValueString(mContext)).isEqualTo(new ValueString("5/31"));

        FixedDate yesterday = new FixedDate(YESTERDAY, FixedDate.FORMAT_AUTOMATIC);
        expect.that(yesterday.toValueString(mContext)).isEqualTo(new ValueString("5/29"));

        FixedDate farAway = new FixedDate(FAR_AWAY, FixedDate.FORMAT_AUTOMATIC);
        expect.that(farAway.toValueString(mContext)).isEqualTo(new ValueString("12/18/2025"));

        FixedDate longAgo = new FixedDate(LONG_AGO, FixedDate.FORMAT_AUTOMATIC);
        expect.that(longAgo.toValueString(mContext)).isEqualTo(new ValueString("1/1/2025"));

        withLocale(Locale.FRANCE, () -> {
            expect.that(today.toValueString(mContext)).isEqualTo(new ValueString("30/05"));
            expect.that(tomorrow.toValueString(mContext)).isEqualTo(new ValueString("31/05"));
            expect.that(farAway.toValueString(mContext)).isEqualTo(new ValueString("18/12/2025"));
        });
    }

    @Test
    @DisableFlags(Flags.FLAG_METRIC_VALUE_ALTERNATIVE_STRINGS)
    public void valueToString_fixedDateFormats_withoutAlternativeStrings() {
        FixedDate soonAuto = new FixedDate(TOMORROW, FixedDate.FORMAT_AUTOMATIC);
        expect.that(soonAuto.toValueString(mContext)).isEqualTo(new ValueString("5/31"));

        FixedDate soonLong = new FixedDate(TOMORROW, FixedDate.FORMAT_LONG_DATE);
        expect.that(soonLong.toValueString(mContext)).isEqualTo(
                new ValueString("May 31, 2025"));

        FixedDate soonShort = new FixedDate(TOMORROW, FixedDate.FORMAT_SHORT_DATE);
        expect.that(soonShort.toValueString(mContext)).isEqualTo(
                new ValueString("5/31/2025"));

        withLocale(Locale.FRANCE, () -> {
            expect.that(soonAuto.toValueString(mContext)).isEqualTo(new ValueString("31/05"));
            expect.that(soonLong.toValueString(mContext)).isEqualTo(
                    new ValueString("31 mai 2025"));
            expect.that(soonShort.toValueString(mContext)).isEqualTo(
                    new ValueString("31/05/2025"));
        });

        FixedDate farAwayAuto = new FixedDate(FAR_AWAY, FixedDate.FORMAT_AUTOMATIC);
        expect.that(farAwayAuto.toValueString(mContext)).isEqualTo(new ValueString("12/18/2025"));

        FixedDate farAwayLong = new FixedDate(FAR_AWAY, FixedDate.FORMAT_LONG_DATE);
        expect.that(farAwayLong.toValueString(mContext)).isEqualTo(
                new ValueString("Dec 18, 2025"));

        FixedDate farAwayShort = new FixedDate(FAR_AWAY, FixedDate.FORMAT_SHORT_DATE);
        expect.that(farAwayShort.toValueString(mContext)).isEqualTo(
                new ValueString("12/18/2025"));

        withLocale(Locale.FRANCE, () -> {
            expect.that(farAwayAuto.toValueString(mContext)).isEqualTo(
                    new ValueString("18/12/2025"));
            expect.that(farAwayLong.toValueString(mContext)).isEqualTo(
                    new ValueString("18 déc. 2025"));
            expect.that(farAwayShort.toValueString(mContext)).isEqualTo(
                    new ValueString("18/12/2025"));
        });
    }

    @Test
    @EnableFlags(Flags.FLAG_METRIC_VALUE_ALTERNATIVE_STRINGS)
    public void valueToString_fixedDateFormats_withAlternativeStrings() {
        FixedDate soonAuto = new FixedDate(TOMORROW, FixedDate.FORMAT_AUTOMATIC);
        expect.that(soonAuto.toValueString(mContext)).isEqualTo(new ValueString("5/31"));

        FixedDate soonLong = new FixedDate(TOMORROW, FixedDate.FORMAT_LONG_DATE);
        expect.that(soonLong.toValueString(mContext)).isEqualTo(
                new ValueString(List.of("May 31, 2025", "May 31", "5/31"), null));

        FixedDate soonShort = new FixedDate(TOMORROW, FixedDate.FORMAT_SHORT_DATE);
        expect.that(soonShort.toValueString(mContext)).isEqualTo(
                new ValueString(List.of("5/31/2025", "5/31"), null));

        withLocale(Locale.FRANCE, () -> {
            expect.that(soonAuto.toValueString(mContext)).isEqualTo(new ValueString("31/05"));
            expect.that(soonLong.toValueString(mContext)).isEqualTo(
                    new ValueString(List.of("31 mai 2025", "31 mai", "31/05"), null));
            expect.that(soonShort.toValueString(mContext)).isEqualTo(
                    new ValueString(List.of("31/05/2025", "31/05"), null));
        });

        FixedDate farAwayAuto = new FixedDate(FAR_AWAY, FixedDate.FORMAT_AUTOMATIC);
        expect.that(farAwayAuto.toValueString(mContext)).isEqualTo(new ValueString("12/18/2025"));

        FixedDate farAwayLong = new FixedDate(FAR_AWAY, FixedDate.FORMAT_LONG_DATE);
        expect.that(farAwayLong.toValueString(mContext)).isEqualTo(
                new ValueString(List.of("Dec 18, 2025", "12/18/2025"), null));

        FixedDate farAwayShort = new FixedDate(FAR_AWAY, FixedDate.FORMAT_SHORT_DATE);
        expect.that(farAwayShort.toValueString(mContext)).isEqualTo(
                new ValueString("12/18/2025"));

        withLocale(Locale.FRANCE, () -> {
            expect.that(farAwayAuto.toValueString(mContext)).isEqualTo(
                    new ValueString("18/12/2025"));
            expect.that(farAwayLong.toValueString(mContext)).isEqualTo(
                    new ValueString(List.of("18 déc. 2025", "18/12/2025"), null));
            expect.that(farAwayShort.toValueString(mContext)).isEqualTo(
                    new ValueString("18/12/2025"));
        });
    }

    @Test
    public void valueToString_fixedDate_notAffectedByTimeZone() {
        FixedDate today = new FixedDate(TODAY, FixedDate.FORMAT_AUTOMATIC);

        withTimeZone(TimeZone.getTimeZone("Etc/GMT+12"),
                () -> expect.that(today.toValueString(mContext)).isEqualTo(
                        new ValueString("5/30")));

        withTimeZone(TimeZone.getTimeZone("Etc/GMT-14"),
                () -> expect.that(today.toValueString(mContext)).isEqualTo(
                        new ValueString("5/30")));
    }

    @Test
    public void valueToString_fixedTime() {
        FixedTime time = new FixedTime(LocalTime.of(14, 30));
        expect.that(time.toValueString(mContext)).isEqualTo(
                new ValueString("2:30" + NNBSP + "PM"));

        FixedTime secondsIgnored = new FixedTime(LocalTime.of(14, 30, 59));
        expect.that(secondsIgnored.toValueString(mContext)).isEqualTo(
                new ValueString("2:30" + NNBSP + "PM"));

        FixedTime subsecondIgnored = new FixedTime(LocalTime.of(14, 30, 59, 999_999_999));
        expect.that(subsecondIgnored.toValueString(mContext)).isEqualTo(
                new ValueString("2:30" + NNBSP + "PM"));

        FixedTime closeToMidnight = new FixedTime(LocalTime.of(23, 59));
        expect.that(closeToMidnight.toValueString(mContext)).isEqualTo(
                new ValueString("11:59" + NNBSP + "PM"));

        FixedTime afterMidnight = new FixedTime(LocalTime.of(0, 1));
        expect.that(afterMidnight.toValueString(mContext)).isEqualTo(
                new ValueString("12:01" + NNBSP + "AM"));
    }

    @Test
    @EnableFlags(Flags.FLAG_METRIC_VALUE_ALTERNATIVE_STRINGS)
    public void valueToString_fixedTime_withAlternativeStrings() {
        FixedTime time = new FixedTime(LocalTime.of(14, 0));
        expect.that(time.toValueString(mContext)).isEqualTo(new ValueString(
                List.of(
                        "2:00" + NNBSP + "PM",
                        "2" + NNBSP + "PM"),
                null));
    }

    @Test
    public void valueToString_fixedTime_respects24HourFormat() {
        FixedTime time = new FixedTime(LocalTime.of(14, 30));

        withTimeFormat("12", () -> {
            // User's choice wins over locale's default 12/24 setting.
            withLocale(Locale.US, () -> {
                expect.that(time.toValueString(mContext)).isEqualTo(
                        new ValueString("2:30" + NNBSP + "PM"));
            });
            withLocale(Locale.FRANCE, () -> {
                expect.that(time.toValueString(mContext)).isEqualTo(
                        new ValueString("2:30" + NNBSP + "PM"));
            });
        });

        withTimeFormat("24", () -> {
            // User's choice wins over locale's default 12/24 setting.
            withLocale(Locale.US, () -> {
                expect.that(time.toValueString(mContext)).isEqualTo(new ValueString("14:30"));
            });
            withLocale(Locale.FRANCE, () -> {
                expect.that(time.toValueString(mContext)).isEqualTo(new ValueString("14:30"));
            });
        });
    }

    @Test
    public void valueToString_fixedTime_notAffectedByTimeZone() {
        FixedTime time = new FixedTime(LocalTime.of(12, 30));

        withTimeZone(TimeZone.getTimeZone("Etc/GMT+12"),
                () -> expect.that(time.toValueString(mContext)).isEqualTo(
                        new ValueString("12:30" + NNBSP + "PM")));

        withTimeZone(TimeZone.getTimeZone("Etc/GMT-14"),
                () -> expect.that(time.toValueString(mContext)).isEqualTo(
                        new ValueString("12:30" + NNBSP + "PM")));
    }

    @Test
    public void valueToString_fixedInt() {
        FixedInt withUnit = new FixedInt(42, "km");
        expect.that(withUnit.toValueString(mContext)).isEqualTo(new ValueString("42", "km"));

        FixedInt noUnit = new FixedInt(42);
        expect.that(noUnit.toValueString(mContext)).isEqualTo(new ValueString("42", null));
    }

    @Test
    @DisableFlags(Flags.FLAG_METRIC_VALUE_ALTERNATIVE_STRINGS)
    public void valueToString_fixedInt_withoutAlternativeStrings() {
        FixedInt big = new FixedInt(3_499_451); // Population of Uruguay :)
        expect.that(big.toValueString(mContext)).isEqualTo(new ValueString("3,499,451", null));
    }

    @Test
    @EnableFlags(Flags.FLAG_METRIC_VALUE_ALTERNATIVE_STRINGS)
    public void valueToString_fixedInt_withAlternativeStrings() {
        FixedInt big = new FixedInt(3_499_451); // Population of Uruguay :)
        expect.that(big.toValueString(mContext)).isEqualTo(new ValueString(
                List.of(
                        "3,499,451",
                        "3.5M"
                ),
                null));

        withLocale(Locale.of("ro-RO"), () ->
                expect.that(big.toValueString(mContext)).isEqualTo(new ValueString(
                        List.of(
                                "3.499.451",
                                "3,5" + NBSP + "mil."
                        ),
                        null)));
    }

    @Test
    @DisableFlags(Flags.FLAG_METRIC_VALUE_ALTERNATIVE_STRINGS)
    public void valueToString_fixedFloat_withoutAlternativeStrings() {
        FixedFloat defaultDigits = new FixedFloat(1612.3456789f);
        expect.that(defaultDigits.toValueString(mContext)).isEqualTo(
                new ValueString("1,612.35", null));

        FixedFloat minDigits = new FixedFloat(42, "km", 2, 4);
        expect.that(minDigits.toValueString(mContext)).isEqualTo(new ValueString("42.00", "km"));

        FixedFloat maxDigits = new FixedFloat(42.1111111f, "km", 2, 4);
        expect.that(maxDigits.toValueString(mContext)).isEqualTo(new ValueString("42.1111", "km"));

        FixedFloat hugeNumber = new FixedFloat(20_511_110_000_000f);
        expect.that(hugeNumber.toValueString(mContext)).isEqualTo(
                new ValueString("20,511,109,152,768", null)); // Float is not precise :(
    }

    @Test
    @EnableFlags(Flags.FLAG_METRIC_VALUE_ALTERNATIVE_STRINGS)
    public void valueToString_fixedFloat_withAlternativeStrings() {
        FixedFloat defaultDigits = new FixedFloat(1612.3456789f);
        expect.that(defaultDigits.toValueString(mContext)).isEqualTo(new ValueString(
                List.of(
                        "1,612.35", // Default min/max digits
                        "1.6K" // Compact
                ),
                null));

        FixedFloat minDigits = new FixedFloat(42, "km", 2, 4);
        expect.that(minDigits.toValueString(mContext)).isEqualTo(new ValueString(
                List.of(
                        "42.00", // Min 2 fraction digits
                        "42" // Compact
                ),
                "km"));

        FixedFloat maxDigits = new FixedFloat(42.1111111f, "km", 2, 4);
        expect.that(maxDigits.toValueString(mContext)).isEqualTo(new ValueString(
                List.of(
                    "42.1111", // Max 4 fraction digits
                    "42" // Compact
                ),
                "km"));

        FixedFloat hugeNumber = new FixedFloat(20_511_110_000_000f);
        expect.that(hugeNumber.toValueString(mContext)).isEqualTo(new ValueString(
                List.of(
                        "20,511,109,152,768", // Float is not precise :(
                        "21T" // Compact
                ),
                null));

        withLocale(Locale.SIMPLIFIED_CHINESE, () ->
                expect.that(hugeNumber.toValueString(mContext)).isEqualTo(new ValueString(
                        List.of(
                                "20,511,109,152,768",
                                "21万亿"
                        ),
                        null)));
    }

    @Test
    public void valueToString_fixedText() {
        FixedText withUnit = new FixedText("120/80", "mmHg");
        expect.that(withUnit.toValueString(mContext)).isEqualTo(new ValueString("120/80", "mmHg"));

        FixedText noUnit = new FixedText("Boring");
        expect.that(noUnit.toValueString(mContext)).isEqualTo(new ValueString("Boring", null));
    }

    @Test
    public void makeContentView_displaysLabelButNoUnit() {
        Notification.Builder n = new Notification.Builder(mContext, "channel")
                .setStyle(new MetricStyle()
                        .addMetric(new Metric(new FixedInt(42), "Answer"))
                        .addMetric(new Metric(new FixedInt(273, "°K"), "Temp")));

        RemoteViews remoteViews = n.getStyle().makeContentView();
        FrameLayout container = new FrameLayout(mContext);
        container.addView(remoteViews.apply(mContext, container));

        assertThat(((TextView) container.findViewById(R.id.metric_label_0)).getText().toString())
                .isEqualTo("Answer:");
        assertThat(((TextView) container.findViewById(R.id.metric_value_0)).getText().toString())
                .isEqualTo("42");

        assertThat(((TextView) container.findViewById(R.id.metric_label_1)).getText().toString())
                .isEqualTo("Temp:");
        assertThat(((TextView) container.findViewById(R.id.metric_value_1)).getText().toString())
                .isEqualTo("273");
    }

    @Test
    public void makeExpandedContentView_concatenatesLabelAndUnit() {
        Notification.Builder n = new Notification.Builder(mContext, "channel")
                .setStyle(new MetricStyle()
                        .addMetric(new Metric(new FixedInt(42), "Answer"))
                        .addMetric(new Metric(new FixedInt(273, "°K"), "Temp")));

        RemoteViews remoteViews = n.getStyle().makeExpandedContentView();
        FrameLayout container = new FrameLayout(mContext);
        container.addView(remoteViews.apply(mContext, container));

        assertThat(((TextView) container.findViewById(R.id.metric_label_0)).getText().toString())
                .isEqualTo("Answer");
        assertThat(((TextView) container.findViewById(R.id.metric_value_0)).getText().toString())
                .isEqualTo("42");

        assertThat(((TextView) container.findViewById(R.id.metric_label_1)).getText().toString())
                .isEqualTo("Temp (°K)");
        assertThat(((TextView) container.findViewById(R.id.metric_value_1)).getText().toString())
                .isEqualTo("273");
    }

    @Test
    public void makeContentView_pausedTimer_showsDuration() {
        Notification.Builder n = new Notification.Builder(mContext, "channel")
                .setStyle(new MetricStyle()
                        .addMetric(new Metric(
                                TimeDifference.forPausedTimer(Duration.ofMinutes(8),
                                        TimeDifference.FORMAT_CHRONOMETER),
                                "Paused timer")));

        RemoteViews remoteViews = n.getStyle().makeExpandedContentView();
        FrameLayout container = new FrameLayout(mContext);
        container.addView(remoteViews.apply(mContext, container));
        Chronometer chronometer = container.findViewById(R.id.metric_chronometer_0);

        assertThat(chronometer.getText()).isEqualTo("08:00");
        assertThat(chronometer.isCountDown()).isTrue();
    }

    @Test
    public void makeContentView_pausedOverrunTimer_showsNegativeDuration() {
        Notification.Builder n = new Notification.Builder(mContext, "channel")
                .setStyle(new MetricStyle()
                        .addMetric(new Metric(
                                TimeDifference.forPausedTimer(Duration.ofSeconds(-5),
                                        TimeDifference.FORMAT_CHRONOMETER),
                                "Paused overrun timer")));

        RemoteViews remoteViews = n.getStyle().makeExpandedContentView();
        FrameLayout container = new FrameLayout(mContext);
        container.addView(remoteViews.apply(mContext, container));
        Chronometer chronometer = container.findViewById(R.id.metric_chronometer_0);

        assertThat(chronometer.getText()).isEqualTo("−00:05");
        assertThat(chronometer.isCountDown()).isTrue();
    }

    @Test
    public void makeContentView_pausedStopwatch_showsDuration() {
        Notification.Builder n = new Notification.Builder(mContext, "channel")
                .setStyle(new MetricStyle()
                        .addMetric(new Metric(
                                TimeDifference.forPausedStopwatch(Duration.ofSeconds(10),
                                        TimeDifference.FORMAT_CHRONOMETER),
                                "Paused stopwatch")));

        RemoteViews remoteViews = n.getStyle().makeExpandedContentView();
        FrameLayout container = new FrameLayout(mContext);
        container.addView(remoteViews.apply(mContext, container));
        Chronometer chronometer = container.findViewById(R.id.metric_chronometer_0);

        assertThat(chronometer.getText()).isEqualTo("00:10");
        assertThat(chronometer.isCountDown()).isFalse();
    }

    @Test
    public void makeContentViews_emptyMetrics_noException() {
        FrameLayout container = new FrameLayout(mContext);
        Notification.Builder noMetrics = new Notification.Builder(mContext, "channel")
                .setStyle(new MetricStyle());

        RemoteViews basic = noMetrics.getStyle().makeContentView();
        RemoteViews expanded = noMetrics.getStyle().makeExpandedContentView();
        RemoteViews headsUp = noMetrics.getStyle().makeHeadsUpContentView();
        RemoteViews compactHeadsUp = noMetrics.getStyle().makeCompactHeadsUpContentView();

        if (basic != null) {
            container.addView(basic.apply(mContext, container));
        }
        if (expanded != null) {
            container.addView(expanded.apply(mContext, container));
        }
        if (headsUp != null) {
            container.addView(headsUp.apply(mContext, container));
        }
        if (compactHeadsUp != null) {
            container.addView(compactHeadsUp.apply(mContext, container));
        }
        // No crashes.
    }

    @Test
    @EnableFlags(Flags.FLAG_API_NOTIFICATION_SEMANTIC_STYLE)
    public void makeContentView_semanticStyleAndPromoted_appliesColor() {
        Notification.Builder n = new Notification.Builder(mContext, "channel")
                .setStyle(new MetricStyle()
                        .addMetric(new Metric(
                                TimeDifference.forPausedStopwatch(Duration.ofSeconds(10),
                                        TimeDifference.FORMAT_CHRONOMETER),
                                "Paused stopwatch",
                                SEMANTIC_STYLE_CAUTION)))
                .setFlag(FLAG_PROMOTED_ONGOING, true);

        RemoteViews remoteViews = n.getStyle().makeExpandedContentView();
        FrameLayout container = new FrameLayout(mContext);
        container.addView(remoteViews.apply(mContext, container));
        Chronometer chronometer = container.findViewById(R.id.metric_chronometer_0);

        assertThat(chronometer.getTextColors().getColors()[0]).isEqualTo(
                mDefaultColors.getSemanticColor(SEMANTIC_STYLE_CAUTION));
    }

    @Test
    @EnableFlags(Flags.FLAG_API_NOTIFICATION_SEMANTIC_STYLE)
    public void makeContentView_semanticStyleButNotPromoted_doesNotApplyColor() {
        Notification.Builder n = new Notification.Builder(mContext, "channel")
                .setStyle(new MetricStyle()
                        .addMetric(new Metric(
                                TimeDifference.forPausedStopwatch(Duration.ofSeconds(10),
                                        TimeDifference.FORMAT_CHRONOMETER),
                                "Paused stopwatch",
                                SEMANTIC_STYLE_CAUTION)))
                .setFlag(FLAG_PROMOTED_ONGOING, false);

        RemoteViews remoteViews = n.getStyle().makeExpandedContentView();
        FrameLayout container = new FrameLayout(mContext);
        container.addView(remoteViews.apply(mContext, container));
        Chronometer chronometer = container.findViewById(R.id.metric_chronometer_0);

        assertThat(chronometer.getTextColors().getColors()[0]).isNotEqualTo(
                mDefaultColors.getSemanticColor(SEMANTIC_STYLE_CAUTION));
    }

    private void withLocale(Locale locale, Runnable r) {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(locale);
            r.run();
        } finally {
            Locale.setDefault(previous);
        }
    }

    private void withTimeZone(TimeZone tz, Runnable r) {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(tz);
            r.run();
        } finally {
            TimeZone.setDefault(previous);
        }
    }

    private void withTimeFormat(String fmt, Runnable r) {
        ContentResolver cr = mContext.getContentResolver();
        String previous = Settings.System.getString(cr, Settings.System.TIME_12_24);
        try {
            Settings.System.putString(cr, Settings.System.TIME_12_24, fmt);
            r.run();
        } finally {
            Settings.System.putString(cr, Settings.System.TIME_12_24, previous);
        }
    }
}
