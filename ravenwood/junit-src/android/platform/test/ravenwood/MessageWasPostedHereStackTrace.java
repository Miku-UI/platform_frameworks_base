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
package android.platform.test.ravenwood;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ravenwood.common.RavenwoodInternalUtils;
import com.android.ravenwood.common.StackTrace;

import org.junit.Assert;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stacktrace that represents where a {@link android.os.Message} was posted.
 */
public class MessageWasPostedHereStackTrace extends StackTrace {
    private static final String TAG = "MessageWasPostedHereStackTrace";

    private static final AtomicLong sNextId = RavenwoodInternalUtils.getNextIdGenerator();

    private final Thread mPostedThread;
    private final int mNestLevel;
    private final long mId;

    /**
     * Constructor without a "parent" message poster.
     */
    public MessageWasPostedHereStackTrace() {
        this(null);
    }

    /**
     * Constructor with an optional "parent" message poster, meaning if a message is posted
     * while handling another message.
     */
    public MessageWasPostedHereStackTrace(@Nullable MessageWasPostedHereStackTrace parentPoster) {
        this(parentPoster, sNextId.getAndIncrement());
    }

    private MessageWasPostedHereStackTrace(
            @Nullable MessageWasPostedHereStackTrace parentPoster,
            long id
    ) {
        this("Message was posted here [post id=#" + id  + "]",
                ParentExtractor.extractNestParent(parentPoster),
                getParentNestLevel(parentPoster) + 1, id);
    }

    private MessageWasPostedHereStackTrace(
            @NonNull String message,
            @Nullable MessageWasPostedHereStackTrace parentPoster,
            int nestLevel,
            long id
    ) {
        super(message, parentPoster);
        mPostedThread = Thread.currentThread();
        mNestLevel = nestLevel;
        mId = id;
    }

    /**
     * Even if we skip nests, we always increment the nest level.
     */
    private static int getParentNestLevel(
            @Nullable MessageWasPostedHereStackTrace parentPoster) {
        return parentPoster == null ? 0 : parentPoster.getNestLevel();
    }

    public int getNestLevel() {
        return mNestLevel;
    }

    public long getPostId() {
        return mId;
    }

    @Override
    public String getMessage() {
        var msg = super.getMessage();
        if (mNestLevel <= 1) {
            return msg + " (root message)";
        } else {
            return msg + " (nest level " + mNestLevel + ")";
        }
    }

    /**
     * Inject "this" exception into another exception as the deepest "cause".
     * (Unless it already has a MessageWasPostedHereStackTrace in the cause chain.)
     */
    public void injectAsCause(Throwable other) {
        var th = other;
        for (;;) {
            var c = th.getCause();
            if (c instanceof MessageWasPostedHereStackTrace) {
                // Already has a MessageWasPostedHereStackTrace, so don't modify.
                return;
            }
            if (c == null) {
                // Found the deepest exception. Inject self.
                try {
                    th.initCause(this);
                } catch (Exception couldNotInject) {
                    // If an exception explicitly has null as a cause, we can't inject
                    // another, but unfortunately we can't tell if that's the case or not.
                    // In that case, just print what we know in the log to help debug.
                    Log.e(TAG, "Exception caused by a message."
                            + " Showing the detected exception followed by the stacktrace where"
                            + " the message was posted.");
                    Log.e(TAG, "Detected exception: ", other);
                    Log.e(TAG, "Message was posted here: ", this);
                }
                return;
            }
            th = th.getCause();
        }
    }

    @NonNull
    public Thread getPostedThread() {
        return mPostedThread;
    }

    /**
     * Contains a helper function to get a "parent poster", which is a parent exception chain
     * without redundant instances.
     *
     * What it does:
     * {@link MessageWasPostedHereStackTrace} is a throwable to record where a
     * {@link android.os.Message} is posted. If a message is posted while handling another
     * {@link android.os.Message}, which often happens in UI code, then it'd be useful if we can
     * detect where the later message was posted, so we "chain" them using inner exceptions, and
     * this can go on and on.
     *
     * The problem with it is it's possible a message is posted repeatedly using postDelayed(),
     * and in that case, this chain can be very very long.
     *
     * To mitigate it, we try to skip redundant stack traces using the logic here, and here's how
     * it works:
     * - Every time we see a newly posted message (called "MC"), we get the "current" message,
     *   which is null when we're not on a handler, but non-null when we're on a handler handling
     *   a message ("MP").
     * - For each Message, we should already have a MessageWasPostedHereStackTrace, so we get it.
     * - This MessageWasPostedHereStackTrace can have nested MessageWasPostedHereStackTrace's
     *   if a Message was posted from another message.
     *
     * - Now, we need to generate MessageWasPostedHereStackTrace for "MC". If "MP" is non-null,
     *   then we get its MessageWasPostedHereStackTrace. Again, MessageWasPostedHereStackTrace
     *   can contain any number of nested MessageWasPostedHereStackTrace's.
     *
     * - Let's say the current MessageWasPostedHereStackTrace is like this:
     *   - Root MessageWasPostedHereStackTrace ("nest level=1") with stack trace S1
     *     which is wrapped by...
     *   - MessageWasPostedHereStackTrace ("nest level=2") with stack trace S2.
     *     which is wrapped by...
     *   - MessageWasPostedHereStackTrace ("nest level=3") with stack trace S3.
     *     which is wrapped by...
     *   - MessageWasPostedHereStackTrace ("nest level=4") with stack trace S3.
     *     -> This is the "current" MessageWasPostedHereStackTrace.
     *
     *    Note the last two instances contain the same stacktraces.
     *
     *  - In this case, instead of using the "nest level 4" instance as a parent, we ignore that
     *    and use "nest level=3" one, skipping (== "peeling off") the outer most one.
     *
     *  - The logic is written such that it can detect non-repeating cases too, so even if
     *    "nest level=4"'s stack trace is S1 or S2 instead, we would be able to peel it off.
     */
    @VisibleForTesting
    public static class ParentExtractor {
        /**
         * In tests, we use this class to inject stack traces and parents.
         */
        public static class ClassForTest extends MessageWasPostedHereStackTrace {
            public final StackTraceElement[] mStackTraceElements;

            public ClassForTest(@Nullable MessageWasPostedHereStackTrace parentPoster,
                    @NonNull StackTraceElement[] stack) {
                super(parentPoster);
                mStackTraceElements = stack;
            }


            @Override
            public StackTraceElement[] getStackTrace() {
                return mStackTraceElements;
            }
        }

        /**
         * Class to wrap StackTraceElement[] to use it as a hash key.
         */
        private static class StackTraceElementArray {
            private final StackTraceElement[] mElements;

            StackTraceElementArray(StackTraceElement[] elements) {
                mElements = elements;
            }

            @Override
            public boolean equals(Object o) {
                if (!(o instanceof StackTraceElementArray that)) return false;
                return Objects.deepEquals(mElements, that.mElements);
            }

            @Override
            public int hashCode() {
                return Arrays.hashCode(mElements);
            }
        }

        /**
         * Return a parent chain without redundant instances. See the class javadoc for details.
         */
        @Nullable
        public static MessageWasPostedHereStackTrace extractNestParent(
                @Nullable MessageWasPostedHereStackTrace parent) {
            if (parent == null) {
                return null;
            }

            // Iterate over the chain and count the # of unique frame arrays.
            var stackSeenCounts = new HashMap<StackTraceElementArray, Integer>();

            var current = parent;
            for (; ; ) {
                var currentStack = new StackTraceElementArray(current.getStackTrace());
                stackSeenCounts.put(currentStack,
                        stackSeenCounts.getOrDefault(currentStack, 0) + 1);
                if (current.getCause() instanceof MessageWasPostedHereStackTrace p) {
                    current = p;
                } else {
                    break;
                }
            }

            // Now, we have a count of each unique stack trace.
            // Do the loop again, and skip as the stack is identical to one of any inner
            // stacks.
            current = parent;
            for (; ; ) {
                var currentStack = new StackTraceElementArray(current.getStackTrace());
                var count = stackSeenCounts.get(currentStack);
                if (count == 0) {
                    Assert.fail("Shouldn't reach here");
                }
                if (count == 1) {
                    // Found a unique stack, so return this one.
                    return current;
                }

                // Decrement the count
                stackSeenCounts.put(currentStack, count - 1);

                if (current.getCause() instanceof MessageWasPostedHereStackTrace p) {
                    current = p;
                } else {
                    return current;
                }
            }
        }
    }
}
