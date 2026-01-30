/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.widget;

import static android.text.format.DateUtils.MINUTE_IN_MILLIS;
import static android.text.format.DateUtils.SECOND_IN_MILLIS;

import static java.util.Objects.requireNonNull;

import android.annotation.ElapsedRealtimeLong;
import android.annotation.NonNull;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.icu.text.MeasureFormat;
import android.icu.text.MeasureFormat.FormatWidth;
import android.icu.util.Measure;
import android.icu.util.MeasureUnit;
import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.inspector.InspectableProperty;
import android.widget.RemoteViews.RemoteView;

import com.android.internal.R;
import com.android.internal.annotations.VisibleForTesting;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * Class that implements a simple timer.
 * <p>
 * You can give it a start time in the {@link SystemClock#elapsedRealtime} timebase,
 * and it counts up from that, or if you don't give it a base time, it will use the
 * time at which you call {@link #start}.
 *
 * <p>The timer can also count downward towards the base time by
 * setting {@link #setCountDown(boolean)} to true.
 *
 *  <p>By default it will display the current
 * timer value in the form "MM:SS" or "H:MM:SS", or you can use {@link #setFormat}
 * to format the timer value into an arbitrary string.
 *
 * @attr ref android.R.styleable#Chronometer_format
 * @attr ref android.R.styleable#Chronometer_countDown
 */
@RemoteView
public class Chronometer extends TextView {
    private static final String TAG = "Chronometer";

    /**
     * In adaptive format, when displaying an elapsed/remaining duration greater than or equal to
     * this number of minutes, seconds will not be shown (which also means the chronometer will tick
     * on the minute instead of on the second).
     */
    private static final int ADAPTIVE_MINUTES_WITHOUT_SECONDS = 3;

    /**
     * A callback that notifies when the chronometer has incremented on its own.
     */
    public interface OnChronometerTickListener {

        /**
         * Notification that the chronometer has changed.
         */
        void onChronometerTick(Chronometer chronometer);

    }

    private final LongSupplier mElapsedRealtimeClock;
    private final InstantSource mSystemClock;

    private long mBase;
    private Instant mBaseInstant;
    private long mNow; // the currently displayed time
    private boolean mVisible;
    private boolean mStarted;
    private boolean mRunning;
    private boolean mLogged;
    private String mFormat;
    private boolean mUseAdaptiveFormat = false;
    private Formatter mFormatter;
    private Locale mFormatterLocale;
    private Object[] mFormatterArgs = new Object[1];
    private StringBuilder mFormatBuilder;
    private OnChronometerTickListener mOnChronometerTickListener;
    private StringBuilder mRecycle = new StringBuilder(8);
    private boolean mCountDown;

    /**
     * Initialize this Chronometer object.
     * Sets the base to the current time.
     */
    public Chronometer(Context context) {
        this(context, null, 0);
    }

    /**
     * Initialize with standard view layout information.
     * Sets the base to the current time.
     */
    public Chronometer(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Initialize with standard view layout information and style.
     * Sets the base to the current time.
     */
    public Chronometer(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public Chronometer(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        this(context, SystemClock::elapsedRealtime, InstantSource.system(), attrs,
                defStyleAttr, defStyleRes);
    }

    /** @hide */
    @VisibleForTesting
    public Chronometer(Context context, LongSupplier elapsedRealtimeClock,
            InstantSource systemClock, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mElapsedRealtimeClock = requireNonNull(elapsedRealtimeClock);
        mSystemClock = requireNonNull(systemClock);

        final TypedArray a = context.obtainStyledAttributes(
                attrs, com.android.internal.R.styleable.Chronometer, defStyleAttr, defStyleRes);
        saveAttributeDataForStyleable(context, com.android.internal.R.styleable.Chronometer,
                attrs, a, defStyleAttr, defStyleRes);
        setFormat(a.getString(R.styleable.Chronometer_format));
        setCountDown(a.getBoolean(R.styleable.Chronometer_countDown, false));
        a.recycle();

        init();
    }

    private void init() {
        mBase = mElapsedRealtimeClock.getAsLong();
        updateText(mBase);
    }

    /**
     * Set this view to count down to the base instead of counting up from it.
     *
     * @param countDown whether this view should count down
     *
     * @see #setBase(long)
     */
    @android.view.RemotableViewMethod
    public void setCountDown(boolean countDown) {
        mCountDown = countDown;
        updateText(mElapsedRealtimeClock.getAsLong());
    }

    /**
     * @return whether this view counts down
     *
     * @see #setCountDown(boolean)
     */
    @InspectableProperty
    public boolean isCountDown() {
        return mCountDown;
    }

    /**
     * @return whether this is the final countdown
     */
    public boolean isTheFinalCountDown() {
        try {
            getContext().startActivity(
                    new Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/9jK-NcRmVcw"))
                            .addCategory(Intent.CATEGORY_BROWSABLE)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                                    | Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Set the time that the count-up timer is in reference to (in the
     * {@link SystemClock#elapsedRealtime} time base).
     */
    @android.view.RemotableViewMethod
    public void setBase(@ElapsedRealtimeLong long base) {
        mBase = base;
        mBaseInstant = null;

        dispatchChronometerTick();
        updateText(mElapsedRealtimeClock.getAsLong());
    }

    /**
     * Set the {@link Instant} that the count-up timer is in reference to.
     *
     * @hide
     */
    @android.view.RemotableViewMethod
    public void setBase(@NonNull Instant base) {
        mBaseInstant = requireNonNull(base);
        mBase = instantToElapsedRealtime(base);

        dispatchChronometerTick();
        updateText(mElapsedRealtimeClock.getAsLong());
    }

    private long instantToElapsedRealtime(Instant instant) {
        return mElapsedRealtimeClock.getAsLong()
                + (instant.toEpochMilli() - mSystemClock.millis());
    }

    /**
     * Pauses the Chronometer (if it was running) and displays the specified {@link Duration}
     * (which can be negative). To do this, {@link #getBase()} will be modified according to the
     * current value of {@link #isCountDown()}.
     *
     * @hide
     */
    @android.view.RemotableViewMethod
    public void setPausedDuration(@NonNull Duration duration) {
        stop();
        long elapsedRealtime = mElapsedRealtimeClock.getAsLong();
        mBase = elapsedRealtime + (isCountDown() ? 1 : -1) * duration.toMillis();
        mBaseInstant = null;
        updateText(elapsedRealtime);
    }

    /**
     * Return the base time as set through {@link #setBase}.
     */
    public long getBase() {
        return mBase;
    }

    /**
     * Sets the format string used for display.  The Chronometer will display
     * this string, with the first "%s" replaced by the current timer value in
     * "MM:SS" or "H:MM:SS" form.
     *
     * If the format string is null, or if you never call setFormat(), the
     * Chronometer will simply display the timer value in "MM:SS" or "H:MM:SS"
     * form.
     *
     * @param format the format string.
     */
    @android.view.RemotableViewMethod
    public void setFormat(String format) {
        mFormat = format;
        if (format != null && mFormatBuilder == null) {
            mFormatBuilder = new StringBuilder(format.length() * 2);
        }
    }

    /**
     * @hide
     */
    public boolean isUseAdaptiveFormat() {
        return mUseAdaptiveFormat;
    }

    /**
     * @hide
     */
    @android.view.RemotableViewMethod
    public void setUseAdaptiveFormat(boolean useAdaptiveFormat) {
        mUseAdaptiveFormat = useAdaptiveFormat;
    }

    /**
     * Returns the current format string as set through {@link #setFormat}.
     */
    @InspectableProperty
    public String getFormat() {
        return mFormat;
    }

    /**
     * Sets the listener to be called when the chronometer changes.
     *
     * @param listener The listener.
     */
    public void setOnChronometerTickListener(OnChronometerTickListener listener) {
        mOnChronometerTickListener = listener;
    }

    /**
     * @return The listener (may be null) that is listening for chronometer change
     *         events.
     */
    public OnChronometerTickListener getOnChronometerTickListener() {
        return mOnChronometerTickListener;
    }

    /**
     * Start counting up.  This does not affect the base as set from {@link #setBase}, just
     * the view display.
     *
     * Chronometer works by regularly scheduling messages to the handler, even when the
     * Widget is not visible.  To make sure resource leaks do not occur, the user should
     * make sure that each start() call has a reciprocal call to {@link #stop}.
     */
    public void start() {
        mStarted = true;
        updateRunning();
    }

    /**
     * Stop counting up.  This does not affect the base as set from {@link #setBase}, just
     * the view display.
     *
     * This stops the messages to the handler, effectively releasing resources that would
     * be held as the chronometer is running, via {@link #start}.
     */
    public void stop() {
        mStarted = false;
        updateRunning();
    }

    /**
     * The same as calling {@link #start} or {@link #stop}.
     * @hide pending API council approval
     */
    @android.view.RemotableViewMethod
    public void setStarted(boolean started) {
        mStarted = started;
        updateRunning();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mVisible = false;
        updateRunning();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        mVisible = visibility == VISIBLE;
        updateRunning();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updateRunning();
    }

    /** @hide */
    @VisibleForTesting
    public void updateText() {
        updateText(mElapsedRealtimeClock.getAsLong());
    }

    private synchronized void updateText(long now) {
        updateBaseTimeIfSystemClockChanged();
        mNow = now;

        long seconds = Math.round((mCountDown ? mBase - now - 499 : now - mBase) / 1000f);
        boolean negative = false;
        if (seconds < 0) {
            seconds = -seconds;
            negative = true;
        }
        String text;
        if (mUseAdaptiveFormat) {
            text = formatTextWithAdaptiveTimeFormat(Duration.ofSeconds(seconds));
        } else {
            text = DateUtils.formatElapsedTime(mRecycle, seconds);
        }

        if (negative) {
            text = getResources().getString(R.string.negative_duration, text);
        }

        if (mFormat != null) {
            Locale loc = Locale.getDefault();
            if (mFormatter == null || !loc.equals(mFormatterLocale)) {
                mFormatterLocale = loc;
                mFormatter = new Formatter(mFormatBuilder, loc);
            }
            mFormatBuilder.setLength(0);
            mFormatterArgs[0] = text;
            try {
                mFormatter.format(mFormat, mFormatterArgs);
                text = mFormatBuilder.toString();
            } catch (IllegalFormatException ex) {
                if (!mLogged) {
                    Log.w(TAG, "Illegal format string: " + mFormat);
                    mLogged = true;
                }
            }
        }

        setText(text);
    }

    private String formatTextWithAdaptiveTimeFormat(Duration duration) {
        final Measure days = new Measure(duration.toDaysPart(), MeasureUnit.DAY);
        final Measure hours = new Measure(duration.toHoursPart(), MeasureUnit.HOUR);
        final Measure minutes = new Measure(duration.toMinutesPart(), MeasureUnit.MINUTE);
        final Measure seconds = new Measure(duration.toSecondsPart(), MeasureUnit.SECOND);
        final MeasureFormat formatter = MeasureFormat.getInstance(Locale.getDefault(),
                FormatWidth.NARROW);

        final ArrayList<Measure> partsList = new ArrayList<>();
        if (days.getNumber().intValue() != 0) {
            partsList.add(days);
            if (hours.getNumber().intValue() != 0) {
                partsList.add(hours);
            }
        } else if (hours.getNumber().intValue() != 0) {
            partsList.add(hours);
            if (minutes.getNumber().intValue() != 0) {
                partsList.add(minutes);
            }
        } else if (minutes.getNumber().intValue() != 0) {
            partsList.add(minutes);
            if (minutes.getNumber().intValue() < ADAPTIVE_MINUTES_WITHOUT_SECONDS) {
              partsList.add(seconds);
            }
        }

        if (partsList.isEmpty()) {
            partsList.add(seconds);
        }

        return formatter.formatMeasures(partsList.toArray(new Measure[0]));
    }

    private static final long SIGNIFICANT_DRIFT_MILLIS = 500;

    private void updateBaseTimeIfSystemClockChanged() {
        if (mBaseInstant == null) {
            return;
        }
        long baseInstantToElapsedRealtime = instantToElapsedRealtime(mBaseInstant);
        long clockChange = Math.abs(mBase - baseInstantToElapsedRealtime);
        if (clockChange > SIGNIFICANT_DRIFT_MILLIS) {
            Log.d(TAG, TextUtils.formatSimple(
                    "Detected system clock change of %s millis; adjusting mBase (%s -> %s)",
                    clockChange, mBase, baseInstantToElapsedRealtime));
            mBase = baseInstantToElapsedRealtime;
        }
    }

    private void updateRunning() {
        boolean running = mVisible && mStarted && isShown();
        if (running != mRunning) {
            if (running) {
                updateText(mElapsedRealtimeClock.getAsLong());
                dispatchChronometerTick();
                postTickOnNextChange();
            } else {
                removeCallbacks(mTickRunnable);
            }
            mRunning = running;
        }
    }

    private final Runnable mTickRunnable = new Runnable() {
        @Override
        public void run() {
            if (mRunning) {
                updateText(mElapsedRealtimeClock.getAsLong());
                dispatchChronometerTick();
                postTickOnNextChange();
            }
        }
    };

    private void postTickOnNextChange() {
        long nowMillis = mNow;

        // In adaptive format, ticks are every 1 minute instead of 1 second, if the time elapsed
        // or remaining is >= 3 minutes. Thus for time > 3 minutes the tick will be "on the minute"
        // and for lower than that it's "on the second".
        long periodInMillis = mUseAdaptiveFormat
                && Math.abs(nowMillis - mBase) > ADAPTIVE_MINUTES_WITHOUT_SECONDS * MINUTE_IN_MILLIS
                        ? MINUTE_IN_MILLIS
                        : SECOND_IN_MILLIS;

        long delayMillis;
        if (mCountDown) {
            delayMillis = (mBase - nowMillis) % periodInMillis;
            if (delayMillis <= 0) {
                delayMillis += periodInMillis;
            }
        } else {
            delayMillis = periodInMillis - (Math.abs(nowMillis - mBase) % periodInMillis);
        }

        // Aim for 3 milliseconds into the next second so we don't update exactly on the second
        delayMillis += 3;
        postDelayed(mTickRunnable, delayMillis);
    }

    void dispatchChronometerTick() {
        if (mOnChronometerTickListener != null) {
            mOnChronometerTickListener.onChronometerTick(this);
        }
    }

    private static final int MIN_IN_SEC = 60;
    private static final int HOUR_IN_SEC = MIN_IN_SEC*60;
    private static String formatDuration(long ms) {
        int duration = (int) (ms / SECOND_IN_MILLIS);
        if (duration < 0) {
            duration = -duration;
        }

        int h = 0;
        int m = 0;

        if (duration >= HOUR_IN_SEC) {
            h = duration / HOUR_IN_SEC;
            duration -= h * HOUR_IN_SEC;
        }
        if (duration >= MIN_IN_SEC) {
            m = duration / MIN_IN_SEC;
            duration -= m * MIN_IN_SEC;
        }
        final int s = duration;

        final ArrayList<Measure> measures = new ArrayList<Measure>();
        if (h > 0) {
            measures.add(new Measure(h, MeasureUnit.HOUR));
        }
        if (m > 0) {
            measures.add(new Measure(m, MeasureUnit.MINUTE));
        }
        measures.add(new Measure(s, MeasureUnit.SECOND));

        return MeasureFormat.getInstance(Locale.getDefault(), FormatWidth.WIDE)
                    .formatMeasures(measures.toArray(new Measure[measures.size()]));
    }

    @Override
    public CharSequence getContentDescription() {
        return formatDuration(mNow - mBase);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return Chronometer.class.getName();
    }
}
