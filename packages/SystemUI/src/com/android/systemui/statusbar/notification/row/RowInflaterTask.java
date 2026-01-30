/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.systemui.statusbar.notification.row;

import static com.android.systemui.statusbar.notification.collection.coordinator.BundleCoordinator.debugBundleLog;

import android.content.Context;
import android.os.UserHandle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.asynclayoutinflater.view.AsyncLayoutFactory;

import com.android.systemui.res.R;
import com.android.systemui.settings.UserTracker;
import com.android.systemui.statusbar.InflationTask;
import com.android.systemui.statusbar.notification.collection.BundleEntry;
import com.android.systemui.statusbar.notification.collection.NotificationEntry;
import com.android.systemui.statusbar.notification.collection.PipelineEntry;
import com.android.systemui.statusbar.notification.shared.NotificationBundleUi;
import com.android.systemui.util.time.SystemClock;

import java.util.concurrent.Executor;

import javax.inject.Inject;

/**
 * An inflater task that asynchronously inflates a ExpandableNotificationRow
 */
public class RowInflaterTask implements InflationTask, AsyncRowInflater.OnInflateFinishedListener {

    private static final String TAG = "RowInflaterTask";
    private static final boolean TRACE_ORIGIN = true;
    private RowInflationFinishedListener mListener;
    private NotificationEntry mEntry;
    private BundleEntry mBundleEntry;
    private boolean mCancelled;
    private Throwable mInflateOrigin;
    private final SystemClock mSystemClock;
    private final RowInflaterTaskLogger mLogger;
    private final AsyncRowInflater mAsyncRowInflater;
    private long mInflateStartTimeMs;
    private UserTracker mUserTracker;

    @Inject
    public RowInflaterTask(SystemClock systemClock, RowInflaterTaskLogger logger,
            UserTracker userTracker, AsyncRowInflater asyncRowInflater) {
        mSystemClock = systemClock;
        mLogger = logger;
        mUserTracker = userTracker;
        mAsyncRowInflater = asyncRowInflater;
    }

    /**
     * Inflates a new notificationView asynchronously, calling the {@code listener} on the main
     * thread when done. This should not be called twice on this object.
     */
    public void inflate(Context context, ViewGroup parent, NotificationEntry entry,
            RowInflationFinishedListener listener) {
        inflate(context, parent, entry, null, listener);
    }

    public void inflate(Context context, ViewGroup parent, BundleEntry entry,
            RowInflationFinishedListener listener) {
        inflate(context, parent, entry, null, listener);
    }

    public void inflate(Context context, ViewGroup parent, BundleEntry entry,
            @Nullable Executor listenerExecutor, RowInflationFinishedListener listener) {
        if (TRACE_ORIGIN) {
            mInflateOrigin = new Throwable("inflate requested here");
        }
        mBundleEntry = entry;
        mListener = listener;
        RowAsyncLayoutFactory asyncLayoutFactory = new RowAsyncLayoutFactory(
                entry, mSystemClock, mLogger, mUserTracker.getUserHandle());
        debugBundleLog(TAG,  () -> "mAsyncRowInflater.inflate bundle: " + entry.getKey());
        mAsyncRowInflater.inflate(context, asyncLayoutFactory,
                R.layout.status_bar_notification_row, parent, this);
    }

    /**
     * Inflates a new notificationView asynchronously, calling the {@code listener} on the supplied
     * {@code listenerExecutor} (or the main thread if null) when done. This should not be called
     * twice on this object.
     */
    @VisibleForTesting
    public void inflate(Context context, ViewGroup parent, NotificationEntry entry,
            @Nullable Executor listenerExecutor, RowInflationFinishedListener listener) {
        if (TRACE_ORIGIN) {
            mInflateOrigin = new Throwable("inflate requested here");
        }
        mListener = listener;
        RowAsyncLayoutFactory asyncLayoutFactory = makeRowFactory(entry);
        mEntry = entry;
        entry.setInflationTask(this);

        mLogger.logInflateStart(entry);
        mInflateStartTimeMs = mSystemClock.elapsedRealtime();
        mAsyncRowInflater.inflate(context, asyncLayoutFactory,
                R.layout.status_bar_notification_row, parent, this);
    }

    /**
     * Inflates a new notificationView synchronously.
     * This method is only for testing-purpose.
     */
    @VisibleForTesting
    public ExpandableNotificationRow inflateSynchronously(@NonNull Context context,
            @Nullable ViewGroup parent, @NonNull PipelineEntry entry) {
        final LayoutInflater inflater = new BasicRowInflater(context);
        inflater.setFactory2(makeRowFactory(entry));
        final ExpandableNotificationRow inflate = (ExpandableNotificationRow) inflater.inflate(
                R.layout.status_bar_notification_row,
                parent /* root */,
                false /* attachToRoot */);
        return inflate;
    }

    private RowAsyncLayoutFactory makeRowFactory(PipelineEntry entry) {
        if (entry instanceof NotificationEntry) {
            return new RowAsyncLayoutFactory(
                (NotificationEntry) entry, mSystemClock, mLogger, mUserTracker.getUserHandle());
        } else if (entry instanceof BundleEntry) {
            return new RowAsyncLayoutFactory(
                (BundleEntry) entry, mSystemClock, mLogger, mUserTracker.getUserHandle());
        } else {
            throw new IllegalArgumentException(
                    "Entry must be either NotificationEntry or BundleEntry");
        }
    }

    @VisibleForTesting
    public static class RowAsyncLayoutFactory implements AsyncLayoutFactory {
        private  NotificationEntry mEntry = null;
        private  BundleEntry mBundleEntry = null;
        private final SystemClock mSystemClock;
        private final RowInflaterTaskLogger mLogger;
        private final UserHandle mTargetUser;

        public RowAsyncLayoutFactory(NotificationEntry entry, SystemClock systemClock,
                RowInflaterTaskLogger logger, UserHandle targetUser) {
            mEntry = entry;
            mSystemClock = systemClock;
            mLogger = logger;
            mTargetUser = targetUser;
        }

        public RowAsyncLayoutFactory(BundleEntry entry, SystemClock systemClock,
                RowInflaterTaskLogger logger, UserHandle targetUser) {
            mBundleEntry = entry;
            mSystemClock = systemClock;
            mLogger = logger;
            mTargetUser = targetUser;
        }

        @Nullable
        @Override
        public View onCreateView(@Nullable View parent, @NonNull String name,
                @NonNull Context context, @NonNull AttributeSet attrs) {
            if (!name.equals(ExpandableNotificationRow.class.getName())) {
                return null;
            }

            final long startMs = mSystemClock.elapsedRealtime();
            ExpandableNotificationRow row = null;
            if (NotificationBundleUi.isEnabled()) {
                row = new ExpandableNotificationRow(context, attrs, mTargetUser);
            } else {
                row = new ExpandableNotificationRow(context, attrs, mEntry);
            }
            final long elapsedMs = mSystemClock.elapsedRealtime() - startMs;

            if (mEntry != null) {
                mLogger.logCreatedRow(mEntry, elapsedMs);
            }
            return row;
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull String name, @NonNull Context context,
                @NonNull AttributeSet attrs) {
            return null;
        }
    }

    @Override
    public void abort() {
        mCancelled = true;
    }

    @Override
    public void onInflateFinished(View view, int resid, ViewGroup parent) {
        debugBundleLog(TAG,  () -> "mAsyncRowInflater.inflate onInflateFinished: " + view
                + " parent: " + parent
                + " mEntry: " + mEntry
                + " mBundleEntry: " + mBundleEntry
                + " mCancelled: " + mCancelled);
        final long elapsedMs = mSystemClock.elapsedRealtime() - mInflateStartTimeMs;
        if (mEntry == null) {
            if (mBundleEntry != null) {
                if (!mCancelled) {
                    debugBundleLog(TAG,  () -> "mListener.onInflationFinished for bundle:"
                            + mBundleEntry.getKey());
                    mListener.onInflationFinished((ExpandableNotificationRow) view);
                }
            }
            return;
        }
        mLogger.logInflateFinish(mEntry, elapsedMs, mCancelled);

        if (!mCancelled) {
            try {
                mEntry.onInflationTaskFinished();
                mListener.onInflationFinished((ExpandableNotificationRow) view);
            } catch (Throwable t) {
                if (mInflateOrigin != null) {
                    Log.e(TAG, "Error in inflation finished listener: " + t, mInflateOrigin);
                    t.addSuppressed(mInflateOrigin);
                }
                throw t;
            }
        }
    }

    public interface RowInflationFinishedListener {
        void onInflationFinished(ExpandableNotificationRow row);
    }
}
