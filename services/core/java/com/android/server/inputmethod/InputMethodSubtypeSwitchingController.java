/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.server.inputmethod;

import static com.android.server.inputmethod.InputMethodUtils.NOT_A_SUBTYPE_INDEX;

import android.annotation.IntDef;
import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Printer;
import android.util.Slog;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * InputMethodSubtypeSwitchingController controls the switching behavior of the subtypes.
 *
 * <p>This class is designed to be used from and only from {@link InputMethodManagerService} by
 * using {@link ImfLock ImfLock.class} as a global lock.
 */
final class InputMethodSubtypeSwitchingController {

    private static final String TAG = InputMethodSubtypeSwitchingController.class.getSimpleName();

    @IntDef(prefix = {"MODE_"}, value = {
            MODE_STATIC,
            MODE_RECENT,
            MODE_AUTO
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface SwitchMode {
    }

    /**
     * Switch using the static order (the order of the given list of input methods and subtypes).
     * This order is only set when given a new list, and never updated.
     */
    static final int MODE_STATIC = 0;

    /**
     * Switch using the recency based order, going from most recent to least recent, updated on
     * {@link #onUserAction user action}.
     */
    static final int MODE_RECENT = 1;

    /**
     * If there was a {@link #onUserAction user action} since the last
     * {@link #onInputMethodSubtypeChanged() switch}, and direction is forward,
     * use {@link #MODE_RECENT}, otherwise use {@link #MODE_STATIC}.
     */
    static final int MODE_AUTO = 2;

    /**
     * List of enabled input methods and subtypes. This is sorted according to
     * {@link ImeSubtypeListItem#compareTo}, and also includes auxiliary subtypes.
     *
     * @see #getEnabledInputMethodsAndSubtypes
     */
    @GuardedBy("ImfLock.class")
    private List<ImeSubtypeListItem> mEnabledItems = Collections.emptyList();

    /** List of input methods and subtypes. */
    @GuardedBy("ImfLock.class")
    @NonNull
    private RotationList mRotationList = new RotationList(Collections.emptyList());

    /**
     * Whether there was a user action since the last input method and subtype switch.
     * Used to determine the switching behaviour for {@link #MODE_AUTO}.
     */
    @GuardedBy("ImfLock.class")
    private boolean mUserActionSinceSwitch;

    InputMethodSubtypeSwitchingController() {
    }

    /**
     * Gets the next input method and subtype, starting from the given ones, in the given direction.
     *
     * <p>If the given input method and subtype are not found, this returns the most recent
     * input method and subtype.
     *
     * @param imi            the input method to find the next value from.
     * @param subtype        the input method subtype to find the next value from, if any.
     * @param onlyCurrentIme whether to consider only subtypes of the current input method.
     * @param forHardware    whether to consider only subtypes
     *                       {@link InputMethodSubtype#isSuitableForPhysicalKeyboardLayoutMapping
     *                       suitable for hardware keyboard}.
     * @param mode           the switching mode.
     * @param forward        whether to search search forwards or backwards in the list.
     * @return the next input method and subtype if found, otherwise {@code null}.
     */
    @GuardedBy("ImfLock.class")
    @Nullable
    ImeSubtypeListItem getNext(@NonNull InputMethodInfo imi, @Nullable InputMethodSubtype subtype,
            boolean onlyCurrentIme, boolean forHardware, @SwitchMode int mode, boolean forward) {
        return mRotationList.next(imi, subtype, onlyCurrentIme, forHardware,
                isRecency(mode, forward), forward);
    }

    /**
     * Gets the list of enabled input methods and subtypes.
     *
     * @param forMenu          whether to filter by items to be shown in the IME Switcher Menu.
     * @param includeAuxiliary whether to include auxiliary subtypes.
     */
    @GuardedBy("ImfLock.class")
    @NonNull
    List<ImeSubtypeListItem> getItems(boolean forMenu, boolean includeAuxiliary) {
        final var res = new ArrayList<ImeSubtypeListItem>();
        for (int i = 0; i < mEnabledItems.size(); i++) {
            final var item = mEnabledItems.get(i);
            if (forMenu && !item.mShowInImeSwitcherMenu) {
                continue;
            }
            if (!includeAuxiliary && item.mIsAuxiliary) {
                continue;
            }
            res.add(item);
        }
        return res;
    }

    /**
     * Updates the list of input methods and subtypes used for switching, from the given context
     * and user specific settings.
     *
     * @param context  the context to update the list from.
     * @param settings the user specific settings to update the list from.
     */
    @GuardedBy("ImfLock.class")
    void update(@NonNull Context context, @NonNull InputMethodSettings settings) {
        mEnabledItems = getEnabledInputMethodsAndSubtypes(context, settings);
        update(getItems(false /* forMenu */, false /* includeAuxiliary */));
    }

    /**
     * Updates the list of input methods and subtypes used for switching. If the given items are
     * equal to the existing ones (regardless of recency order), the update is skipped and the
     * current recency order is kept. Otherwise, the recency order is reset.
     *
     * @param enabledItems the list of enabled input methods and subtypes.
     */
    @GuardedBy("ImfLock.class")
    @VisibleForTesting
    void update(@NonNull List<ImeSubtypeListItem> enabledItems) {
        if (!mRotationList.mItems.equals(enabledItems)) {
            mRotationList = new RotationList(enabledItems);
        }
    }

    /**
     * Called when the user took an action that should update the recency of the current
     * input method and subtype in the switching list.
     *
     * @param imi     the currently selected input method.
     * @param subtype the currently selected input method subtype, if any.
     * @return {@code true} if the recency was updated, otherwise {@code false}.
     * @see android.inputmethodservice.InputMethodServiceInternal#notifyUserActionIfNecessary()
     */
    @GuardedBy("ImfLock.class")
    boolean onUserAction(@NonNull InputMethodInfo imi, @Nullable InputMethodSubtype subtype) {
        final boolean recencyUpdated = mRotationList.setMostRecent(imi, subtype);
        if (recencyUpdated) {
            mUserActionSinceSwitch = true;
        }
        return recencyUpdated;
    }

    /** Called when the input method and subtype was changed. */
    @GuardedBy("ImfLock.class")
    void onInputMethodSubtypeChanged() {
        mUserActionSinceSwitch = false;
    }

    @GuardedBy("ImfLock.class")
    void dump(@NonNull Printer pw, @NonNull String prefix) {
        pw.println(prefix + "mRotationList:");
        mRotationList.dump(pw, prefix + "  ");
        pw.println(prefix + "mEnabledItems:");
        for (int i = 0; i < mEnabledItems.size(); i++) {
            final var item = mEnabledItems.get(i);
            pw.println(prefix + "  i=" + i + " item=" + item);
        }
        pw.println(prefix + "User action since last switch: " + mUserActionSinceSwitch);
    }

    /**
     * Whether the given mode and direction result in recency or static order.
     *
     * <p>{@link #MODE_AUTO} resolves to the recency order for the first forwards switch
     * after an {@link #onUserAction user action}, and otherwise to the static order.
     *
     * @param mode    the switching mode.
     * @param forward the switching direction.
     * @return {@code true} for the recency order, otherwise {@code false}.
     */
    @GuardedBy("ImfLock.class")
    private boolean isRecency(@SwitchMode int mode, boolean forward) {
        return mode == MODE_RECENT || (mode == MODE_AUTO && mUserActionSinceSwitch && forward);
    }

    /**
     * Gets the list of enabled input methods and subtypes. This is sorted according to
     * {@link ImeSubtypeListItem#compareTo}, and also includes auxiliary subtypes. The subtypes are
     * de-duplicated based on the {@link InputMethodSubtype#hashCode}.
     *
     * @param context  the context used to resolve the IME and subtype labels.
     * @param settings the settings used to get the list of enabled input methods and subtypes.
     */
    @GuardedBy("ImfLock.class")
    @NonNull
    private static List<ImeSubtypeListItem> getEnabledInputMethodsAndSubtypes(
            @NonNull Context context, @NonNull InputMethodSettings settings) {
        final int userId = settings.getUserId();
        final Context userAwareContext = context.getUserId() == userId
                ? context : context.createContextAsUser(UserHandle.of(userId), 0 /* flags */);

        final var res = new ArrayList<ImeSubtypeListItem>();
        final var imis = settings.getEnabledInputMethodList();
        if (imis.isEmpty()) {
            Slog.w(TAG, "Enabled input method list is empty.");
            return res;
        }

        for (int i = 0; i < imis.size(); i++) {
            final InputMethodInfo imi = imis.get(i);
            final var imeLabel = imi.loadLabel(userAwareContext.getPackageManager());
            final boolean showInImeSwitcherMenu = imi.shouldShowInInputMethodPicker();
            final var subtypes = settings.getEnabledInputMethodSubtypeList(imi, true);
            if (subtypes.isEmpty()) {
                res.add(new ImeSubtypeListItem(imeLabel, null /* subtypeName */,
                        null /* layoutName */, imi, NOT_A_SUBTYPE_INDEX, showInImeSwitcherMenu,
                        false /* isAuxiliary */, true /* suitableForHardware */));
            } else {
                final var hashCodes = new ArraySet<Integer>();
                for (int j = 0; j < subtypes.size(); j++) {
                    hashCodes.add(subtypes.get(j).hashCode());
                }
                final int subtypeCount = imi.getSubtypeCount();
                for (int j = 0; j < subtypeCount; j++) {
                    final InputMethodSubtype subtype = imi.getSubtypeAt(j);
                    final int hashCode = subtype.hashCode();
                    if (!hashCodes.contains(hashCode)) {
                        continue;
                    }
                    // Remove this subtype from the set to avoid duplicates.
                    hashCodes.remove(hashCode);

                    final var appInfo = imi.getServiceInfo().applicationInfo;
                    final var subtypeLabel = subtype.overridesImplicitlyEnabledSubtype() ? null
                            : subtype.getDisplayName(userAwareContext, imi.getPackageName(),
                                    appInfo);
                    final var layoutName = subtype.overridesImplicitlyEnabledSubtype() ? null
                            : subtype.getLayoutDisplayName(userAwareContext, appInfo);
                    res.add(new ImeSubtypeListItem(imeLabel, subtypeLabel, layoutName,
                            imi, j, showInImeSwitcherMenu, subtype.isAuxiliary(),
                            subtype.isSuitableForPhysicalKeyboardLayoutMapping()));
                }
            }
        }
        Collections.sort(res);
        return res;
    }

    static class ImeSubtypeListItem implements Comparable<ImeSubtypeListItem> {

        /** The input method's name. */
        @NonNull
        final CharSequence mImeName;

        /** The subtype's name, or {@code null} if this item doesn't have a subtype. */
        @Nullable
        final CharSequence mSubtypeName;

        /**
         * The subtype's layout name, or {@code null} if this item doesn't have a subtype,
         * or doesn't specify a layout.
         */
        @Nullable
        final CharSequence mLayoutName;

        /** The info of the Input Method associated with this item. */
        @NonNull
        final InputMethodInfo mImi;

        /**
         * The index of the subtype in the input method's array of subtypes,
         * or {@link InputMethodUtils#NOT_A_SUBTYPE_INDEX} if this item doesn't have a subtype.
         */
        final int mSubtypeIndex;

        /** Whether this item has {@link InputMethodInfo#shouldShowInInputMethodPicker}. */
        final boolean mShowInImeSwitcherMenu;

        /** Whether this item has {@link InputMethodSubtype#isAuxiliary}. */
        final boolean mIsAuxiliary;

        /**
         * Whether this item has
         * {@link InputMethodSubtype#isSuitableForPhysicalKeyboardLayoutMapping}.
         */
        final boolean mSuitableForHardware;

        ImeSubtypeListItem(@NonNull CharSequence imeName, @Nullable CharSequence subtypeName,
                @Nullable CharSequence layoutName, @NonNull InputMethodInfo imi, int subtypeIndex,
                boolean showInImeSwitcherMenu, boolean isAuxiliary, boolean suitableForHardware) {
            mImeName = imeName;
            mSubtypeName = subtypeName;
            mLayoutName = layoutName;
            mImi = imi;
            mSubtypeIndex = subtypeIndex;
            mShowInImeSwitcherMenu = showInImeSwitcherMenu;
            mIsAuxiliary = isAuxiliary;
            mSuitableForHardware = suitableForHardware;
        }

        /**
         * Compares this object with the specified object for order. The fields of this class will
         * be compared in the following order:
         * <ol>
         *   <li>{@link #mImeName}</li>
         *   <li>{@link #mImi} with {@link InputMethodInfo#getId()}</li>
         * </ol>
         *
         * <p>Note: this class has a natural ordering that is inconsistent with
         * {@link #equals(Object)}. This method doesn't compare {@link #mSubtypeIndex} but
         * {@link #equals(Object)} does.
         *
         * @param other the object to be compared.
         * @return a negative integer, zero, or positive integer as this object is less than, equal
         * to, or greater than the specified {@code other} object.
         */
        @Override
        public int compareTo(ImeSubtypeListItem other) {
            final int result = compare(mImeName, other.mImeName);
            if (result != 0) {
                return result;
            }
            // This will not compare by subtype name, however as {@link Collections.sort} is
            // guaranteed to be a stable sorting, this allows sorting by the IME name (and ID),
            // while maintaining the order of subtypes (given by each IME) at the IME level.
            return mImi.getId().compareTo(other.mImi.getId());
        }

        @Override
        public String toString() {
            return "ImeSubtypeListItem{"
                    + "mImeName=" + mImeName
                    + " mSubtypeName=" + mSubtypeName
                    + " mLayoutName=" + mLayoutName
                    + " mSubtypeIndex=" + mSubtypeIndex
                    + " mShowInImeSwitcherMenu=" + mShowInImeSwitcherMenu
                    + " mIsAuxiliary=" + mIsAuxiliary
                    + " mSuitableForHardware=" + mSuitableForHardware
                    + "}";
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof ImeSubtypeListItem that)) {
                return false;
            }
            return TextUtils.equals(mImeName, that.mImeName)
                    && TextUtils.equals(mSubtypeName, that.mSubtypeName)
                    && TextUtils.equals(mLayoutName, that.mLayoutName)
                    && mImi.equals(that.mImi)
                    && mSubtypeIndex == that.mSubtypeIndex
                    && mShowInImeSwitcherMenu == that.mShowInImeSwitcherMenu
                    && mIsAuxiliary == that.mIsAuxiliary
                    && mSuitableForHardware == that.mSuitableForHardware;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mImeName, mSubtypeName, mLayoutName, mImi, mSubtypeIndex,
                    mShowInImeSwitcherMenu, mIsAuxiliary, mSuitableForHardware);
        }

        /**
         * Compares two character sequences lexicographically. For historical reasons, an empty
         * sequence is considered less than a non-empty one.
         *
         * @param cs1 first character sequence to compare.
         * @param cs2 second character sequence to compare.
         * @return the value {@code 0} if the two sequences are equal; a value less than {@code 0}
         * if the first sequence is lexicographically less than the second sequence; and
         * a value greater than {@code 0} if the first sequence is lexicographically
         * greater than the second sequence.
         */
        private static int compare(@NonNull CharSequence cs1, @NonNull CharSequence cs2) {
            // For historical reasons, an empty text needs to put at the last.
            final boolean empty1 = cs1.isEmpty();
            final boolean empty2 = cs2.isEmpty();
            if (empty1 || empty2) {
                return (empty1 ? 1 : 0) - (empty2 ? 1 : 0);
            }
            return CharSequence.compare(cs1, cs2);
        }
    }

    /**
     * List container that allows getting the next item in either forwards or backwards direction,
     * in either static or recency order, and either in the same IME or not.
     */
    private static class RotationList {

        /**
         * List of items in a static order.
         */
        @NonNull
        private final List<ImeSubtypeListItem> mItems;

        /**
         * Mapping of recency index to static index (in {@link #mItems}), with lower indices being
         * more recent.
         */
        @NonNull
        private final int[] mRecencyMap;

        RotationList(@NonNull List<ImeSubtypeListItem> items) {
            mItems = items;
            mRecencyMap = new int[items.size()];
            for (int i = 0; i < mItems.size(); i++) {
                mRecencyMap[i] = i;
            }
        }

        /**
         * Gets the next input method and subtype from the given ones.
         *
         * <p>If the given input method and subtype are not found, this returns the most recent
         * input method and subtype.
         *
         * @param imi            the input method to find the next value from.
         * @param subtype        the input method subtype to find the next value from, if any.
         * @param onlyCurrentIme whether to consider only subtypes of the current input method.
         * @param forHardware    whether to consider only subtypes suitable for hardware keyboard.
         * @param useRecency     whether to use the recency order, or the static order.
         * @param forward        whether to search forwards to backwards in the list.
         * @return the next input method and subtype if found, otherwise {@code null}.
         */
        @Nullable
        ImeSubtypeListItem next(@NonNull InputMethodInfo imi, @Nullable InputMethodSubtype subtype,
                boolean onlyCurrentIme, boolean forHardware, boolean useRecency, boolean forward) {
            if (mItems.isEmpty()) {
                return null;
            }
            final int index = getIndex(imi, subtype, useRecency);
            if (index < 0) {
                Slog.w(TAG, "Trying to switch away from input method: " + imi
                        + " and subtype " + subtype + " which are not in the list,"
                        + " falling back to most recent item in list.");
                return mItems.get(mRecencyMap[0]);
            }

            final int incrementSign = (forward ? 1 : -1);

            final int size = mItems.size();
            for (int i = 1; i < size; i++) {
                final int nextIndex = (index + i * incrementSign + size) % size;
                final int mappedIndex = useRecency ? mRecencyMap[nextIndex] : nextIndex;
                final var nextItem = mItems.get(mappedIndex);
                if ((onlyCurrentIme && !nextItem.mImi.equals(imi))
                        || (forHardware && !nextItem.mSuitableForHardware)) {
                    continue;
                }
                return nextItem;
            }
            return null;
        }

        /**
         * Sets the given input method and subtype as the most recent one.
         *
         * @param imi     the input method to set as the most recent.
         * @param subtype the input method subtype to set as the most recent, if any.
         * @return {@code true} if the recency was updated, otherwise {@code false}.
         */
        boolean setMostRecent(@NonNull InputMethodInfo imi, @Nullable InputMethodSubtype subtype) {
            if (mItems.isEmpty()) {
                return false;
            }

            final int recencyIndex = getIndex(imi, subtype, true /* useRecency */);
            if (recencyIndex <= 0) {
                // Already most recent or not found.
                return false;
            }
            final int staticIndex = mRecencyMap[recencyIndex];
            System.arraycopy(mRecencyMap, 0, mRecencyMap, 1, recencyIndex);
            mRecencyMap[0] = staticIndex;
            return true;
        }

        /**
         * Gets the index of the given input method and subtype, in either recency or static order.
         *
         * @param imi        the input method to get the index of.
         * @param subtype    the input method subtype to get the index of, if any.
         * @param useRecency whether to get the index in the recency or static order.
         * @return an index in either {@link #mItems} or {@link #mRecencyMap}, or {@code -1}
         * if not found.
         */
        @IntRange(from = -1)
        private int getIndex(@NonNull InputMethodInfo imi, @Nullable InputMethodSubtype subtype,
                boolean useRecency) {
            final int subtypeIndex = getSubtypeIndex(imi, subtype);
            for (int i = 0; i < mItems.size(); i++) {
                final int mappedIndex = useRecency ? mRecencyMap[i] : i;
                final var item = mItems.get(mappedIndex);
                if (item.mImi.equals(imi) && item.mSubtypeIndex == subtypeIndex) {
                    return i;
                }
            }
            return -1;
        }

        /**
         * Gets the index of the given subtype in the given IME's array of subtypes.
         *
         * @param imi     the IME whose array of subtypes to check.
         * @param subtype the subtype to get the index of.
         * @return the index of the subtype in the array, or
         * {@link InputMethodUtils#NOT_A_SUBTYPE_INDEX} if the subtype is {@code} null or not found
         * in the IME.
         */
        private static int getSubtypeIndex(@NonNull InputMethodInfo imi,
                @Nullable InputMethodSubtype subtype) {
            return subtype != null
                    ? SubtypeUtils.getSubtypeIndexFromHashCode(imi, subtype.hashCode())
                    : NOT_A_SUBTYPE_INDEX;
        }

        /** Dumps the state of the list into the given printer. */
        private void dump(@NonNull Printer pw, @NonNull String prefix) {
            pw.println(prefix + "Static order:");
            for (int i = 0; i < mItems.size(); i++) {
                final var item = mItems.get(i);
                pw.println(prefix + "  i=" + i + " item=" + item);
            }
            pw.println(prefix + "Recency order:");
            for (int i = 0; i < mRecencyMap.length; i++) {
                final int index = mRecencyMap[i];
                final var item = mItems.get(index);
                pw.println(prefix + "  i=" + i + " item=" + item);
            }
        }
    }
}
