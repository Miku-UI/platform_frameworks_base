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
package com.android.server.pm;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.util.DebugUtils;
import android.util.IndentingPrintWriter;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.ArrayUtils;
import com.android.server.utils.Slogf;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// TODO(b/412176703): currently public because NMS uses a constant and the
// UserNotificationsAllowlist class doesn't exist yet - once it does, make it package protected.
/**
 * Base class responsible for managing generic allowlists.
 *
 * <p>The allowlist is divided in 2 lists:
 *
 * <ol>
 *   <li>A permanent allowlist (typically defined by an array config resource).
 *   <li>A temporary allowlist (that can be set programmatically and lasts until reset or restart).
 * </ol>
 *
 * <p>This class is thread safe.
 *
 * @param <E> type of the element being allowlisted.
 */
public abstract class GenericAllowlist<E> {

    @VisibleForTesting
    static final boolean DEBUG = Log.isLoggable(GenericAllowlist.class.getSimpleName(), Log.DEBUG);

    @VisibleForTesting
    static final String ALLOWED_BY_OVERRIDDEN_STATUS_MESSAGE_TEMPLATE =
            "getAllowlistStatus(%s): should return %d (%s), but returning %d (%s) as set by "
            + "overrideDisallowedStatus()";

    /** Allowlist is disabled because it was set with an invalid mode. */
    public static final int ALLOWLIST_MODE_INVALID = -1;
    /** Allowlist is disabled. */
    public static final int ALLOWLIST_MODE_DISABLED = 0;
    /** Allowlist is enabled. */
    public static final int ALLOWLIST_MODE_ENABLED = 1;

    @IntDef(prefix = { "ALLOWLIST_MODE_" }, value = {
            ALLOWLIST_MODE_INVALID,
            ALLOWLIST_MODE_DISABLED,
            ALLOWLIST_MODE_ENABLED,
            })
    public @interface AllowlistMode {}

    // Values for {@code getAllowlistStatus()}. When adding new values, follow the rules:
    //
    // 1. Disallowed values are < STATUS_UNKNOWN
    // 2. Allowed values are > STATUS_UNKNOWN
    // 3. It's ok to rename the constants, but never change their values (as they will be used on
    //    metrics.
    // 4. When adding a new one:
    //    4.1 Update LAST_STATUS_ALLOWED
    //    4.2 Add new assertion on GenericAllowlistTestCase.testAllowlistStatusToString()
    //    4.3 Add new assertion on GenericAllowlistTestCase.testIsAllowed()

    /**
      * Element is not allowed because the status is unknown.
      *
      * <p>Typically used when initializing a status.
      */
    public static final int STATUS_UNKNOWN = 0;

    /** Element is not allowed because the temporary allowlist is set and it's not in it. */
    public static final int STATUS_DISALLOWED_NOT_IN_TEMPORARY_LIST = -1;
    /** Element is not allowed because it's not included in the permanent allowlist. */
    public static final int STATUS_DISALLOWED_NOT_IN_PERMANENT_LIST = -2;
    /** Element is not allowed because the feature being allowlisted is disabled. */
    public static final int STATUS_DISALLOWED_FEATURE_DISABLED = -3;

    /** Element is allowed because the allowlist mode is invalid. */
    public static final int STATUS_ALLOWED_INVALID_MODE = 1;
    /** Element is allowed because allowlist is disabled. */
    public static final int STATUS_ALLOWED_DISABLED_MODE = 2;

    /** Element is allowed because the temporary allowlist is set and is empty. */
    public static final int STATUS_ALLOWED_TEMPORARY_LIST_EMPTY = 3;
    /** Element is allowed because the temporary allowlist is set and it's in it. */
    public static final int STATUS_ALLOWED_BY_TEMPORARY_LIST = 4;

    /** Element is allowed because the permanent allowlist is empty. */
    public static final int STATUS_ALLOWED_PERMANENT_LIST_EMPTY = 5;
    /** Element is allowed because the it's in the permanent allowlist. */
    public static final int STATUS_ALLOWED_BY_PERMANENT_LIST = 6;

    /** Element is allowed because allowlisting was temporary disabled by cmd user. */
    public static final int STATUS_ALLOWED_ALLOWLISTING_DISABLED_BY_SHELL_CMD = 7;

    /**
     * Element is allowed because allowlisting is disabled while the device is being provisioned.
     */
    public static final int STATUS_ALLOWED_ALLOWLISTING_DISABLED_WHILE_DEVICE_IS_PROVISIONING = 8;

    @VisibleForTesting
    static final int LAST_STATUS_ALLOWED =
            STATUS_ALLOWED_ALLOWLISTING_DISABLED_WHILE_DEVICE_IS_PROVISIONING;

    // TODO(b/414326600): this class is public because WM uses @AllowlistStatus. It might be cleaner
    // to create a new class for it.
    @IntDef(prefix = { "STATUS_" }, value = {
            STATUS_UNKNOWN,
            STATUS_ALLOWED_INVALID_MODE,
            STATUS_ALLOWED_DISABLED_MODE,
            STATUS_ALLOWED_TEMPORARY_LIST_EMPTY,
            STATUS_ALLOWED_BY_TEMPORARY_LIST,
            STATUS_DISALLOWED_NOT_IN_TEMPORARY_LIST,
            STATUS_ALLOWED_PERMANENT_LIST_EMPTY,
            STATUS_ALLOWED_BY_PERMANENT_LIST,
            STATUS_DISALLOWED_NOT_IN_PERMANENT_LIST,
            STATUS_ALLOWED_ALLOWLISTING_DISABLED_BY_SHELL_CMD,
            STATUS_ALLOWED_ALLOWLISTING_DISABLED_WHILE_DEVICE_IS_PROVISIONING,
            STATUS_DISALLOWED_FEATURE_DISABLED
            })
    public @interface AllowlistStatus {}

    @VisibleForTesting
    final String mTag = getClass().getSimpleName();

    /** Used to set mId (on constructor). */
    private static int sNextId;

    /**
     * Used on {@link #toString()}  only, so the allowlist can be uniquely identified (on
     * {@link #dump(IndentingPrintWriter, String)}).
     */
    @VisibleForTesting
    final int mId;

    /**
     * List of elements that are permanently allowed (i.e., they survive reboots).
     *
     * <p>If empty, allowlist is disabled (and all elements are allowed).
     *
     * <p><b>NOTE:</b> for now it's an array as they're just read from config, but should change to
     * {@code Set} once it supports APIs to change it (for example, from DPM).
     */
    private final String[] mPermanentAllowlist;

    /** Mode of the allowlist - see {@link AllowlistMode} */
    private final @AllowlistMode AtomicInteger mMode;

    // Content is null when not overridden
    private final AtomicReference<Integer> mOverriddenDisallowedStatus = new AtomicReference<>();

    /**
     * List of elements that are temporarily allowed (i.e., until reboot or set back to
     * {@code null}).
     *
     * <p>When set (i.e., not {@code null}), it will override the value of
     * {@code mPermanentAllowlist}. If empty, allowlist is disabled (and all elements are allowed).
     */
    @Nullable
    private volatile CopyOnWriteArrayList<String> mTemporaryAllowlist;

    private final String mSingularName;
    private final String mPluralName;

    protected GenericAllowlist(@AllowlistMode int mode, String singularName, String pluralName,
            String[] permanentNormalizedNames) {
        mId = ++sNextId;
        int validatedMode = ALLOWLIST_MODE_INVALID;
        try {
            validatedMode = validateMode(mode);
        } catch (Exception e) {
            Slogf.wtf(mTag, e, "Invalid mode (%d) on constructor; using ALLOWLIST_MODE_INVALID (%d)"
                    + " instead", mode, ALLOWLIST_MODE_INVALID);
        }
        mMode = new AtomicInteger(validatedMode);
        mSingularName = singularName;
        mPluralName = pluralName;
        mPermanentAllowlist = getValidElements(permanentNormalizedNames);
    }

    /** Converts the given string to an element. */
    protected abstract @Nullable E fromNormalizedName(String name);

    /** Converts the given element to a String. */
    protected abstract String toNormalizedName(E element);

    public final @AllowlistMode int getMode() {
        return mMode.get();
    }

    final void setMode(@AllowlistMode int mode) {
        int newMode = validateMode(mode);
        int oldMode = mMode.getAndSet(newMode);
        if (DEBUG) {
            Slogf.d(mTag, "setMode(): changed from %d (%s) to %d (%s)",
                    oldMode, allowlistModeToString(oldMode),
                    newMode, allowlistModeToString(newMode));
        }
    }

    /**
     * Used to temporarily disable allowlisting.
     *
     * @param status status to be returned when an activity is not allowed, or {@code null} to reset
     * this behavior.
     *
     * @throws IllegalArgumentException if the {@code status} is not one of those that starts with
     * {@code STATUS_ALLOWED_ALLOWLISTING_DISABLED_BY}.
     */
    final void overrideDisallowedStatus(@Nullable @AllowlistStatus Integer status) {
        if (status == null) {
            resetOverriddenDisallowedStatus();
            return;
        }
        if (!isOverridingDisallowedStatus(status)) {
            throw new IllegalArgumentException("Invalid overriding status: " + status + "("
                    + allowlistStatusToString(status) + ")");
        }
        setOverriddenDisallowedStatus(status);
    }

    /**
     * Returns whether the given {@code status} is a valid value for
     * {@link #overrideDisallowedStatus(Integer)}.
     */
    protected abstract boolean isOverridingDisallowedStatus(@AllowlistStatus int status);

    private void resetOverriddenDisallowedStatus() {
        Integer previousStatus = mOverriddenDisallowedStatus.getAndSet(null);
        if (previousStatus != null) {
            Slogf.i(mTag, "overrideDisallowedStatus(): reset from %d (%s)", previousStatus,
                    allowlistStatusToString(previousStatus));
        }
    }

    private void setOverriddenDisallowedStatus(@AllowlistStatus int status) {
        Integer previousStatus = mOverriddenDisallowedStatus.getAndSet(status);
        if (previousStatus == null) {
            Slogf.i(mTag, "overrideDisallowedStatus(): set to %d (%s)", status,
                    allowlistStatusToString(status));
            return;
        }
        Slogf.i(mTag, "overrideDisallowedStatus(): changed from %d (%s) to %d (%s)",
                previousStatus, allowlistStatusToString(previousStatus),
                status, allowlistStatusToString(status));
    }

    @VisibleForTesting
    @Nullable
    Integer getOverriddenDisallowedStatus() {
        return mOverriddenDisallowedStatus.get();
    }

    // NOTE: only called by 'cmd user' (which needs to "build" the temporary allowlist based on
    // incremental actions, like add or remove an element) and unit tests, so we don't have to
    // worry about performance (like caching the result or not using streams)
    final List<E> getEffectiveAllowlist() {
        Stream<String> stream = mTemporaryAllowlist != null
                ? mTemporaryAllowlist.stream()
                : Arrays.stream(mPermanentAllowlist);
        return stream.map(e -> fromNormalizedName(e))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Returns whether the given element is allowed.
     *
     * <p> It will check the temporary list first (if set), then the permanent one. If the checked
     * list is empty, then allowlisting is disabled and all elements (except {@code null}) are
     * allowed.
     *
     * <p>NOTE: this method should only be used when the result is not logged afterwards (for
     * example, on shell commands); most usages should call {@link #getAllowlistStatus(Object)} and
     * {@link #isAllowed(int)} instead.
     */
    public final boolean isAllowed(E element) {
        return isAllowed(getAllowlistStatus(element));
    }

    /**
     * Returns the allowlist status of the given element.
     *
     * <p>It will check the temporary list first (if set), then the permanent one. If the checked
     * list is empty, then allowlisting is disabled and all elements (except {@code null}) are
     * allowed.
     */
    public final @AllowlistStatus int getAllowlistStatus(E element) {
        Objects.requireNonNull(element, "element cannot be null");
        String normalizedName = toNormalizedName(element);

        int mode = mMode.get();
        if (mode == ALLOWLIST_MODE_DISABLED || mode == ALLOWLIST_MODE_INVALID) {
            int status = mode == ALLOWLIST_MODE_DISABLED
                    ? STATUS_ALLOWED_DISABLED_MODE
                    : STATUS_ALLOWED_INVALID_MODE;
            if (DEBUG) {
                Slogf.d(mTag, "getAllowlistStatus(%s): returning %d (%s) because mode is (%d) %s",
                        normalizedName, status, allowlistStatusToString(status), mode,
                        allowlistModeToString(mode));
            }
            return status;
        }

        // Checks the temporary list first...
        CopyOnWriteArrayList<String> temporaryList = mTemporaryAllowlist;
        if (temporaryList != null) {
            if (temporaryList.isEmpty()) {
                if (DEBUG) {
                    Slogf.d(mTag, "isAllowed(%s): returning true because temporary "
                            + "allowlist overrides permanent allowlist and is empty, so any "
                            + "%s is allowed", normalizedName, mSingularName);
                }
                return STATUS_ALLOWED_TEMPORARY_LIST_EMPTY;
            }
            if (DEBUG) {
                Slogf.d(mTag, "isAllowed(%s): checking temporary list (%s)",
                        normalizedName, temporaryList);
            }
            boolean allowed = temporaryList.contains(normalizedName);
            return checkModeAndLog(normalizedName, /* permanentAllowlist= */ false, allowed);
        }

        // ...then the permanent one.
        if (mPermanentAllowlist.length == 0) {
            if (DEBUG) {
                Slogf.d(mTag, "isAllowed(%s): returning true because permanent allowlist"
                        + "is empty, so any %s is allowed", normalizedName, mSingularName);
            }
            return STATUS_ALLOWED_PERMANENT_LIST_EMPTY;
        }
        if (DEBUG) {
            Slogf.d(mTag, "isAllowed(%s): checking permanent list (%s)", normalizedName,
                    Arrays.toString(mPermanentAllowlist));
        }
        boolean allowed = ArrayUtils.contains(mPermanentAllowlist, normalizedName);
        return checkModeAndLog(normalizedName, /* permanentAllowlist= */ true, allowed);
    }

    /** Checks whether the given status represents allowed or disallowed. */
    public static boolean isAllowed(@AllowlistStatus int status) {
        return status > STATUS_UNKNOWN && status <= LAST_STATUS_ALLOWED;
    }

    private @AllowlistStatus int checkModeAndLog(String normalizedName, boolean permanentAllowlist,
            boolean allowed) {
        if (allowed) {
            int status = permanentAllowlist
                    ? STATUS_ALLOWED_BY_PERMANENT_LIST
                    : STATUS_ALLOWED_BY_TEMPORARY_LIST;
            if (DEBUG) {
                Slogf.d(mTag, "getAllowlistStatus(%s, allowed=%b): returning %s", normalizedName,
                        allowed, allowlistStatusToString(status));
            }
            return status;
        }
        int status = permanentAllowlist
                ? STATUS_DISALLOWED_NOT_IN_PERMANENT_LIST
                : STATUS_DISALLOWED_NOT_IN_TEMPORARY_LIST;
        Integer overriddenDisallowedStatus = mOverriddenDisallowedStatus.get();
        if (overriddenDisallowedStatus != null) {
            Slogf.w(mTag, ALLOWED_BY_OVERRIDDEN_STATUS_MESSAGE_TEMPLATE, normalizedName,
                    status,
                    allowlistStatusToString(status),
                    overriddenDisallowedStatus,
                    allowlistStatusToString(overriddenDisallowedStatus));
            return overriddenDisallowedStatus;
        }

        if (DEBUG) {
            Slogf.d(mTag, "getAllowlistStatus(%s, allowed=%b): returning %s", normalizedName,
                    allowed, allowlistStatusToString(status));
        }
        return status;
    }

    /** Sets the temporary allowlist (or resets it when passed with {@code null}. */
    final void setTemporaryAllowlist(@Nullable Collection<E> elements) {
        if (DEBUG) {
            Slogf.d(mTag, "setTemporaryAllowList(%s)", elements);
        }
        if (elements == null) {
            mTemporaryAllowlist = null;
            return;
        }
        List<String> tempList = new ArrayList<>(elements.size());
        for (E element : elements) {
            tempList.add(toNormalizedName(element));
        }
        mTemporaryAllowlist = new CopyOnWriteArrayList<>(tempList);

        if (DEBUG) {
            Slogf.d(mTag, "setTemporaryAllowList(): set as %s", mTemporaryAllowlist);
        }
    }

    @Override
    public final String toString() {
        return getClass().getSimpleName() + "#" + mId;
    }

    /** Gets a user-friendly representation of the given {@code mode}. */
    public static String allowlistModeToString(@AllowlistMode int mode) {
        return DebugUtils.constantToString(GenericAllowlist.class, "ALLOWLIST_MODE_", mode);
    }

    /** Gets a user-friendly representation of the given {@code status}. */
    public static String allowlistStatusToString(@AllowlistStatus int status) {
        return DebugUtils.constantToString(GenericAllowlist.class, "STATUS_", status);
    }

    final void dump(PrintWriter writer, String prefix, String header) {
        dump(new IndentingPrintWriter(writer, /* singleIndent=*/ "  ", prefix), header);
    }

    final void dump(IndentingPrintWriter writer, String header) {
        writer.printf("%s:\n", header);
        writer.increaseIndent();

        writer.printf("id: %s\n", toString());
        int mode = mMode.get();
        writer.printf("mode: %d (%s)\n", mode, allowlistModeToString(mode));
        writer.printf("DEBUG: %b\n", DEBUG);

        dumpEffectiveAllowlistStatus(writer, mode);
        dumpPermanentAllowlist(writer);
        dumpTemporaryAllowlist(writer);

        writer.decreaseIndent();
    }

    private void dumpEffectiveAllowlistStatus(IndentingPrintWriter writer,
            @AllowlistMode int mode) {
        writer.printf("%s allowlist status: ", mPluralName);

        switch (mode) {
            case ALLOWLIST_MODE_DISABLED -> {
                writer.println("disabled (by config)");
                return;
            }
        }

        Integer overriddenDisallowedStatus = mOverriddenDisallowedStatus.get();
        if (overriddenDisallowedStatus != null) {
            writer.printf("temporarily disabled (reason: %s)\n",
                    allowlistStatusToString(overriddenDisallowedStatus));
            return;
        }

        CopyOnWriteArrayList<String> temporaryList = mTemporaryAllowlist;
        if (temporaryList != null) {
            if (temporaryList.isEmpty()) {
                writer.println("disabled (empty temporary list)");
            } else {
                writer.println("using temporary allowlist");
            }
            return;
        }
        if (mPermanentAllowlist.length == 0) {
            writer.println("disabled (empty permanent list)");
        } else {
            writer.println("using permanent allowlist");
        }
    }

    private void dumpPermanentAllowlist(IndentingPrintWriter writer) {
        int size = mPermanentAllowlist.length;

        // Header / number of elements
        dumpAllowlistSize(writer, "permanent", size);

        // Body / elements
        writer.increaseIndent();
        for (String item : mPermanentAllowlist) {
            writer.println(item);
        }
        writer.decreaseIndent();
    }

    private void dumpTemporaryAllowlist(IndentingPrintWriter writer) {
        CopyOnWriteArrayList<String> temporaryList = mTemporaryAllowlist;

        // Header / number of elements
        if (temporaryList == null) {
            writer.printf("temporary %s allowlist is not set.\n", mPluralName);
            return;
        }
        int size = temporaryList.size();
        dumpAllowlistSize(writer, "temporary", size);

        // Body / elements
        writer.increaseIndent();
        for (int i = 0; i < size; i++) {
            writer.println(temporaryList.get(i));
        }
        writer.decreaseIndent();
    }

    private void dumpAllowlistSize(IndentingPrintWriter writer, String name,
            int size) {
        if (size == 0) {
            writer.printf("%s %s allowlist is empty.\n", name, mPluralName);
            return;
        }
        String suffix = size > 1 ? mPluralName : mSingularName;
        writer.printf("%s %s allowlist has %d %s:\n", name, mPluralName, size, suffix);
    }

    private String[] getValidElements(String[] elements) {
        // NOTE: must use LinkedHashSet to preserve order, otherwise test case would fail and dump()
        // wouldn't show the same order as the config.xml
        LinkedHashSet<String> set = new LinkedHashSet<>(elements.length);
        for (String element : elements) {
            E validElement = fromNormalizedName(element);
            if (validElement == null) {
                Slogf.w(mTag, "Invalid %s from config: %s", mSingularName, element);
                continue;
            }
            // Must "normalize" the component into the flattened format, as the class part could
            // have been expressed as FQCN (Fully-Qualified Class Name).
            String normalizedName = toNormalizedName(validElement);
            if (set.contains(normalizedName)) {
                Slogf.w(mTag, "%s %s already added (as %s)", mSingularName, element,
                        normalizedName);
            }
            set.add(normalizedName);
        }
        String[] valid = new String[set.size()];
        set.toArray(valid);
        if (DEBUG) {
            Slogf.d(mTag, "Valid %s from config: %s", mPluralName, Arrays.toString(valid));
        }
        return valid;
    }

    private static @AllowlistMode int validateMode(@AllowlistMode int mode) {
        return switch (mode) {
            case ALLOWLIST_MODE_ENABLED, ALLOWLIST_MODE_DISABLED -> mode;
            default -> throw new IllegalArgumentException("invalid mode: " + mode);
        };
    }
}
