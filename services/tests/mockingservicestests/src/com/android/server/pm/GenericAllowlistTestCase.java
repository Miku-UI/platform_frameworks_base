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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.server.pm.GenericAllowlist.allowlistModeToString;
import static com.android.server.pm.GenericAllowlist.allowlistStatusToString;
import static com.android.server.pm.GenericAllowlist.isAllowed;
import static com.android.server.pm.GenericAllowlist.AllowlistMode;
import static com.android.server.pm.GenericAllowlist.DEBUG;
import static com.android.server.pm.GenericAllowlist.ALLOWED_BY_OVERRIDDEN_STATUS_MESSAGE_TEMPLATE;
import static com.android.server.pm.GenericAllowlist.ALLOWLIST_MODE_DISABLED;
import static com.android.server.pm.GenericAllowlist.ALLOWLIST_MODE_ENABLED;
import static com.android.server.pm.GenericAllowlist.ALLOWLIST_MODE_INVALID;
import static com.android.server.pm.GenericAllowlist.LAST_STATUS_ALLOWED;
import static com.android.server.pm.GenericAllowlist.STATUS_ALLOWED_ALLOWLISTING_DISABLED_BY_SHELL_CMD;
import static com.android.server.pm.GenericAllowlist.STATUS_ALLOWED_ALLOWLISTING_DISABLED_WHILE_DEVICE_IS_PROVISIONING;
import static com.android.server.pm.GenericAllowlist.STATUS_ALLOWED_BY_PERMANENT_LIST;
import static com.android.server.pm.GenericAllowlist.STATUS_ALLOWED_BY_TEMPORARY_LIST;
import static com.android.server.pm.GenericAllowlist.STATUS_ALLOWED_DISABLED_MODE;
import static com.android.server.pm.GenericAllowlist.STATUS_ALLOWED_INVALID_MODE;
import static com.android.server.pm.GenericAllowlist.STATUS_ALLOWED_PERMANENT_LIST_EMPTY;
import static com.android.server.pm.GenericAllowlist.STATUS_ALLOWED_TEMPORARY_LIST_EMPTY;
import static com.android.server.pm.GenericAllowlist.STATUS_DISALLOWED_FEATURE_DISABLED;
import static com.android.server.pm.GenericAllowlist.STATUS_DISALLOWED_NOT_IN_PERMANENT_LIST;
import static com.android.server.pm.GenericAllowlist.STATUS_DISALLOWED_NOT_IN_TEMPORARY_LIST;
import static com.android.server.pm.GenericAllowlist.STATUS_UNKNOWN;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.spy;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

import android.content.Context;
import android.content.res.Resources;
import android.util.Slog;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;
import com.android.server.ExpectableTestCase;
import com.android.server.pm.GenericAllowlist.AllowlistMode;
import com.android.server.pm.GenericAllowlist.AllowlistStatus;

import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;

/**
 * Not a test per se, but the base class for classes testing subclasses of {@link GenericAllowlist}.
 *
 * @param <A> type of the class being test.
 * @param <E> type of the allowlist element.
 */
abstract class GenericAllowlistTestCase<A extends GenericAllowlist<E>, E>
        extends ExpectableTestCase {

    private static final int UNKNOWN_MODE = 666;

    @Rule
    public final ExtendedMockitoRule extendedMockito =
            new ExtendedMockitoRule.Builder(this).build();

    private final Context mRealContext =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .getTargetContext();

    /** Need to spy the real context otherwise we'd have to mock resources, which is a PITA... */
    @SuppressWarnings("MockNotUsedInProduction")
    private final Context mSpiedContext = spy(mRealContext);

    @Mock
    private Resources mMockResources;

    private final String mSingular;
    private final String mPlural;

    protected GenericAllowlistTestCase(String singularName, String pluralName) {
        mSingular = singularName;
        mPlural = pluralName;
    }

    protected abstract A createAllowlist(@AllowlistMode int mode, String... configAllowlist);

    /** Gets the statuses that can be passed to {@code overrideDisallowedStatus()}. */
    protected abstract ImmutableSet<Integer> getOverridingStatuses();

    protected abstract String getPermanentName1();
    protected abstract String getPermanentName2();
    protected abstract String getPermanentName3();
    protected abstract E getPermanentElement1();
    protected abstract E getPermanentElement2();
    protected abstract E getPermanentElement3();

    protected abstract String getTemporaryName1();
    protected abstract String getTemporaryName2();
    protected abstract String getTemporaryName3();
    protected abstract E getTemporaryElement1();
    protected abstract E getTemporaryElement2();
    protected abstract E getTemporaryElement3();

    protected abstract String getInvalidName();

    protected abstract E getNotAllowlistedElement();
    protected abstract String getNotAllowlistedName();

    // TODO(b/455912167): methods below are used to check activities that could be represented
    // by ether a full or "flattened" name. They might not apply to other subclass (like
    // notification), in which case we'd do add a new method (like assumeSupportShortName()) to
    // handle them.

    protected abstract String getShortName();
    protected abstract String getFullName();
    protected abstract E getElementWithShortName();
    protected abstract E getElementWithFullName();

    @Before
    public void setFixtures() {
        doReturn(mMockResources).when(mSpiedContext).getResources();
    }

    @Before
    public void assertFullAndShortElementsAreTheSame() {
        expectWithMessage("getElementWithFullName()")
                .that(getElementWithFullName()).isEqualTo(getElementWithShortName());
        expectWithMessage("getElementWithShortName()")
                .that(getElementWithShortName()).isEqualTo(getElementWithFullName());
    }

    @Test
    public final void testIsAllowed_null() throws Exception {
        var allowlist = createAllowlist();

        assertThrows(NullPointerException.class, () -> allowlist.isAllowed(null));
    }

    @Test
    public final void testGetAllowlistStatus_null() throws Exception {
        var allowlist = createAllowlist();

        assertThrows(NullPointerException.class, () -> allowlist.getAllowlistStatus(null));
    }

    @Test
    public final void testIsAllowed_emptyConfig() throws Exception {
        var allowlist = createAllowlist();

        expectAllowed(allowlist, STATUS_ALLOWED_PERMANENT_LIST_EMPTY, getPermanentElement1());
        expectEffectiveAllowlist(allowlist);
    }

    @Test
    public final void testIsAllowed_configWithOne() throws Exception {
        var allowlist = createAllowlist(getPermanentName1());

        expectNotAllowed(allowlist, getNotAllowlistedElement());
        expectAllowed(allowlist, getPermanentElement1());
        expectEffectiveAllowlist(allowlist, getPermanentElement1());
    }

    @Test
    public final void testIsAllowed_configWithTwo() throws Exception {
        var allowlist = createAllowlist(getPermanentName1(), getPermanentName2());

        expectNotAllowed(allowlist, getNotAllowlistedElement());
        expectAllowed(allowlist, getPermanentElement1(), getPermanentElement2());
        expectEffectiveAllowlist(allowlist, getPermanentElement1(), getPermanentElement2());
    }

    @Test
    public final void testIsAllowed_configWithThree() throws Exception {
        var allowlist =
                createAllowlist(getPermanentName1(), getPermanentName2(), getPermanentName3());

        expectNotAllowed(allowlist, getNotAllowlistedElement());
        expectAllowed(allowlist,
                getPermanentElement1(), getPermanentElement2(), getPermanentElement3());
        expectEffectiveAllowlist(allowlist,
                getPermanentElement1(), getPermanentElement2(), getPermanentElement3());
    }

    @Test
    public final void testIsAllowed_configWithShortFullAndInvalidNames() throws Exception {
        var allowlist = createAllowlist(getShortName(), getFullName(), getInvalidName());

        expectNotAllowed(allowlist, getNotAllowlistedElement());
        expectAllowed(allowlist, getElementWithFullName());
        expectEffectiveAllowlist(allowlist, getElementWithFullName());
    }

    @Test
    public final void testIsAllowed_modeDisabled() throws Exception {
        var allowlist = createAllowlist(ALLOWLIST_MODE_DISABLED, getPermanentName1());

        expectAllowed(allowlist, STATUS_ALLOWED_DISABLED_MODE,
                getNotAllowlistedElement(), getPermanentElement1(),
                getPermanentElement2(), getPermanentElement3());
        expectEffectiveAllowlist(allowlist, getPermanentElement1());
    }

    @Test
    public final void testIsAllowed_invalidModes() throws Exception {
        testIsAllowedWhenConstructorWithInvalidMode(UNKNOWN_MODE);
        testIsAllowedWhenConstructorWithInvalidMode(ALLOWLIST_MODE_INVALID);
    }

    private void testIsAllowedWhenConstructorWithInvalidMode(int mode) throws Exception {
        var allowlist = createAllowlist(mode, getPermanentName1());

        expectWithMessage("getMode()")
                .that(allowlist.getMode())
                .isEqualTo(ALLOWLIST_MODE_INVALID);

        expectAllowed(allowlist, STATUS_ALLOWED_INVALID_MODE,
                getNotAllowlistedElement(), getPermanentElement1(),
                getPermanentElement2(), getPermanentElement3());
        expectEffectiveAllowlist(allowlist, getPermanentElement1());
    }

    @Test
    public final void testSetTemporaryAllowlist_fromEmptyConfig() throws Exception {
        var allowlist = createAllowlist();

        testSetAndResetTemporaryList(allowlist, () -> {
            expectAllowed(allowlist, STATUS_ALLOWED_PERMANENT_LIST_EMPTY,
                    getNotAllowlistedElement());
            expectEffectiveAllowlist(allowlist);
        });
    }

    @Test
    public final void testSetTemporaryAllowlist_fromConfigWithOne() throws Exception {
        var allowlist = createAllowlist(getPermanentName1());

        testSetAndResetTemporaryList(allowlist, () -> {
            expectNotAllowed(allowlist, getNotAllowlistedElement());
            expectAllowed(allowlist, getPermanentElement1());
            expectEffectiveAllowlist(allowlist, getPermanentElement1());
        });
    }

    @Test
    public final void testSetTemporaryAllowlist_fromConfigWithTwo() throws Exception {
        var allowlist = createAllowlist(getPermanentName1(), getPermanentName2());

        testSetAndResetTemporaryList(allowlist, () -> {
            expectNotAllowed(allowlist, getNotAllowlistedElement());
            expectAllowed(allowlist, getPermanentElement1(), getPermanentElement2());
            expectEffectiveAllowlist(allowlist, getPermanentElement1(), getPermanentElement2());
        });
    }

    @Test
    public final void testSetTemporaryAllowlist_fromConfigWithMultiple() throws Exception {
        var allowlist =
                createAllowlist(getPermanentName1(), getPermanentName2(), getPermanentName3());

        testSetAndResetTemporaryList(allowlist, () -> {
            expectNotAllowed(allowlist, getNotAllowlistedElement());
            expectAllowed(allowlist,
                    getPermanentElement1(), getPermanentElement2(), getPermanentElement3());
            expectEffectiveAllowlist(allowlist,
                    getPermanentElement1(), getPermanentElement2(), getPermanentElement3());
        });
    }

    private void testSetAndResetTemporaryList(A allowlist,
            Runnable permanentAllowListStateChecker) {
        // Check permanent state before
        permanentAllowListStateChecker.run();


        // Set as empty - everything should be allowed
        List<E> emptyList = emptyList();

        allowlist.setTemporaryAllowlist(emptyList);

        expectAllowedAfterSettingTemporaryList(allowlist, emptyList,
                STATUS_ALLOWED_TEMPORARY_LIST_EMPTY,
                getNotAllowlistedElement(),
                getTemporaryElement1(), getTemporaryElement2(), getTemporaryElement3(),
                getPermanentElement1(), getPermanentElement2(), getPermanentElement3()
        );
        expectEffectiveAllowlist(allowlist);

        // One temporary element
        List<E> listWithOne = asList(getTemporaryElement1());

        allowlist.setTemporaryAllowlist(listWithOne);

        expectAllowedAfterSettingTemporaryList(allowlist, listWithOne,
                getTemporaryElement1());
        expectNotAllowedAfterSettingTemporaryList(allowlist, listWithOne,
                getNotAllowlistedElement(), getTemporaryElement2(), getTemporaryElement3());
        expectDefaultPermanentElementsNotAllowed(allowlist);
        expectEffectiveAllowlist(allowlist, getTemporaryElement1());


        // Two temporary elements
        List<E> listWithOneAndTwo = asList(getTemporaryElement1(), getTemporaryElement2());

        allowlist.setTemporaryAllowlist(listWithOneAndTwo);

        expectAllowedAfterSettingTemporaryList(allowlist, listWithOneAndTwo,
                getTemporaryElement1(), getTemporaryElement2());
        expectNotAllowedAfterSettingTemporaryList(allowlist, listWithOneAndTwo,
                getNotAllowlistedElement(), getTemporaryElement3());
        expectDefaultPermanentElementsNotAllowed(allowlist);
        expectEffectiveAllowlist(allowlist, getTemporaryElement1(), getTemporaryElement2());


        // Three temporary elements
        List<E> listWithThree =
                asList(getTemporaryElement1(), getTemporaryElement2(), getTemporaryElement3());

        allowlist.setTemporaryAllowlist(listWithThree);

        expectAllowedAfterSettingTemporaryList(allowlist, listWithThree,
                getTemporaryElement1(), getTemporaryElement2(), getTemporaryElement3());
        expectNotAllowedAfterSettingTemporaryList(allowlist, listWithThree,
                getNotAllowlistedElement());
        expectDefaultPermanentElementsNotAllowed(allowlist);
        expectEffectiveAllowlist(allowlist,
                getTemporaryElement1(), getTemporaryElement2(), getTemporaryElement3());

        // Reset the list
        allowlist.setTemporaryAllowlist(null);

        permanentAllowListStateChecker.run();
    }

    @Test
    public final void testDump_emptyConfig() throws Exception {
        var allowlist = createAllowlist();

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        id: %s
                        mode: 1 (ENABLED)
                        DEBUG: %b
                        %s allowlist status: disabled (empty permanent list)
                        permanent %s allowlist is empty.
                        temporary %s allowlist is not set.
                           """,
                           allowlist, DEBUG, mPlural, mPlural, mPlural));
    }

    @Test
    public final void testDump_configWithOneElement() throws Exception {
        var name1 = getPermanentName1();
        var allowlist = createAllowlist(name1);

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        id: %s
                        mode: 1 (ENABLED)
                        DEBUG: %b
                        %s allowlist status: using permanent allowlist
                        permanent %s allowlist has 1 %s:
                          %s
                        temporary %s allowlist is not set.
                           """,
                           allowlist, DEBUG, mPlural, mPlural, mSingular, name1, mPlural));
    }

    @Test
    public final void testDump_configWithOneElement_overrideDisallowedStatus() throws Exception {
        var name1 = getPermanentName1();
        var allowlist = createAllowlist(name1);
        allowlist.overrideDisallowedStatus(STATUS_ALLOWED_ALLOWLISTING_DISABLED_BY_SHELL_CMD);

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        id: %s
                        mode: 1 (ENABLED)
                        DEBUG: %b
                        %s allowlist status: temporarily disabled (reason: ALLOWED_ALLOWLISTING_DISABLED_BY_SHELL_CMD)
                        permanent %s allowlist has 1 %s:
                          %s
                        temporary %s allowlist is not set.
                           """,
                           allowlist, DEBUG, mPlural, mPlural, mSingular, name1, mPlural));
    }

    @Test
    public final void testDump_configWithOneElement_disabled() throws Exception {
        var name1 = getPermanentName1();
        var allowlist = createAllowlist(ALLOWLIST_MODE_DISABLED, name1);

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        id: %s
                        mode: 0 (DISABLED)
                        DEBUG: %b
                        %s allowlist status: disabled (by config)
                        permanent %s allowlist has 1 %s:
                          %s
                        temporary %s allowlist is not set.
                           """,
                           allowlist, DEBUG, mPlural, mPlural, mSingular, name1, mPlural));
    }

    @Test
    public final void testDump_configWithMultipleElements() throws Exception {
        var name1 = getPermanentName1();
        var name2 = getPermanentName2();
        var allowlist = createAllowlist(name1, name2);

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        id: %s
                        mode: 1 (ENABLED)
                        DEBUG: %b
                        %s allowlist status: using permanent allowlist
                        permanent %s allowlist has 2 %s:
                          %s
                          %s
                        temporary %s allowlist is not set.
                          """,
                          allowlist, DEBUG, mPlural, mPlural, mPlural, name1, name2, mPlural));
    }

    @Test
    public final void testDump_configWithOneInvalidElement() throws Exception {
        var allowlist = createAllowlist(getInvalidName());

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                        Dumpo:
                          id: %s
                          mode: 1 (ENABLED)
                          DEBUG: %b
                          %s allowlist status: disabled (empty permanent list)
                          permanent %s allowlist is empty.
                          temporary %s allowlist is not set.
                             """,
                             allowlist, DEBUG, mPlural, mPlural, mPlural));
    }

    @Test
    public final void testDump_configWithValidAndInvalidElements() throws Exception {
        var name1 = getPermanentName1();
        var name2 = getPermanentName2();
        var allowlist = createAllowlist(name1, name2, getInvalidName());

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        id: %s
                        mode: 1 (ENABLED)
                        DEBUG: %b
                        %s allowlist status: using permanent allowlist
                        permanent %s allowlist has 2 %s:
                          %s
                          %s
                        temporary %s allowlist is not set.
                          """,
                          allowlist, DEBUG, mPlural, mPlural, mPlural, name1, name2, mPlural));
    }

    @Test
    public final void testDump_configWithFullName() throws Exception {
        // It's created with full name, but should dump the "normalized" short name
        var allowlist = createAllowlist(getFullName());

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        id: %s
                        mode: 1 (ENABLED)
                        DEBUG: %b
                        %s allowlist status: using permanent allowlist
                        permanent %s allowlist has 1 %s:
                          %s
                        temporary %s allowlist is not set.
                          """,
                          allowlist, DEBUG, mPlural, mPlural, mSingular, getShortName(), mPlural));
    }


    @Test
    public final void testDump_configWithShortAndFullNames() throws Exception {
        // Both are equivalent, so dump should only display the "normalized" one
        var allowlist = createAllowlist(getShortName(), getFullName());

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        id: %s
                        mode: 1 (ENABLED)
                        DEBUG: %b
                        %s allowlist status: using permanent allowlist
                        permanent %s allowlist has 1 %s:
                          %s
                        temporary %s allowlist is not set.
                          """,
                          allowlist, DEBUG, mPlural, mPlural, mSingular, getShortName(), mPlural));
    }

    @Test
    public final void testDump_temporaryAllowlistEmpty_emptyConfig() throws Exception {
        var allowlist = createAllowlist();
        allowlist.setTemporaryAllowlist(emptyList());

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        id: %s
                        mode: 1 (ENABLED)
                        DEBUG: %b
                        %s allowlist status: disabled (empty temporary list)
                        permanent %s allowlist is empty.
                        temporary %s allowlist is empty.
                           """,
                           allowlist, DEBUG, mPlural, mPlural, mPlural));
    }

    @Test
    public final void testDump_temporaryAllowlistEmpty_nonEmptyConfig() throws Exception {
        var name1 = getPermanentName1();
        var name2 = getPermanentName2();
        var allowlist = createAllowlist(name1, name2);
        allowlist.setTemporaryAllowlist(emptyList());

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        id: %s
                        mode: 1 (ENABLED)
                        DEBUG: %b
                        %s allowlist status: disabled (empty temporary list)
                        permanent %s allowlist has 2 %s:
                          %s
                          %s
                        temporary %s allowlist is empty.
                           """,
                           allowlist, DEBUG, mPlural, mPlural, mPlural, name1, name2, mPlural));
    }

    @Test
    public final void testDump_temporaryAllowlistWithOne_emptyConfig() throws Exception {
        var allowlist = createAllowlist();
        var name1 = getPermanentName1();
        allowlist.setTemporaryAllowlist(asList(allowlist.fromNormalizedName(name1)));

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        id: %s
                        mode: 1 (ENABLED)
                        DEBUG: %b
                        %s allowlist status: using temporary allowlist
                        permanent %s allowlist is empty.
                        temporary %s allowlist has 1 %s:
                          %s
                           """,
                           allowlist, DEBUG, mPlural, mPlural, mPlural, mSingular, name1));
    }

    @Test
    public final void testDump_temporaryAllowlistWithMultiple_nonEmptyConfig() throws Exception {
        var name1 = getPermanentName1();
        var name2 = getPermanentName2();
        var tmpName1 = getPermanentName1();
        var tmpName2 = getTemporaryName2();
        var allowlist = createAllowlist(name1, name2);
        allowlist.setTemporaryAllowlist(asList(
                allowlist.fromNormalizedName(tmpName1),
                allowlist.fromNormalizedName(tmpName2)));

        String dump = dump(allowlist);

        expectWithMessage("dump()").that(dump)
                .isEqualTo(String.format("""
                      Dumpo:
                        id: %s
                        mode: 1 (ENABLED)
                        DEBUG: %b
                        %s allowlist status: using temporary allowlist
                        permanent %s allowlist has 2 %s:
                          %s
                          %s
                        temporary %s allowlist has 2 %s:
                          %s
                          %s
                           """,
                           allowlist, DEBUG, mPlural, mPlural, mPlural, name1, name2, mPlural,
                           mPlural, tmpName1, tmpName2));
    }

    @Test
    public void testToString() {
        var allowlist1 = createAllowlist();
        var allowlist2 = createAllowlist();
        String toString1 = allowlist1.toString();
        String toString2 = allowlist2.toString();

        expectWithMessage("allowlist1.toString()").that(toString1).isNotEqualTo(toString2);
        expectWithMessage("allowlist1.toString()").that(toString1).contains("" + allowlist1.mId);
        expectWithMessage("allowlist2.toString()").that(toString2).contains("" + allowlist2.mId);
    }

    @Test
    public final void testNameToElementConversions() {
        testNormalizeAndBack(getPermanentName1(), getPermanentElement1());
        testNormalizeAndBack(getPermanentName2(), getPermanentElement2());
        testNormalizeAndBack(getPermanentName3(), getPermanentElement3());

        testNormalizeAndBack(getTemporaryName1(), getTemporaryElement1());
        testNormalizeAndBack(getTemporaryName2(), getTemporaryElement2());
        testNormalizeAndBack(getTemporaryName3(), getTemporaryElement3());

        testNormalizeAndBack(getShortName(), getElementWithShortName());
        // Cannot use testNormalizeAndBack on full name as they're normalized to short
        testConversion(getElementWithFullName(), getShortName());
        testConversion(getFullName(), getElementWithShortName());
    }

    @Test
    public final void testAllowlistModeToString() {
        expectWithMessage("allowlistModeToString(ALLOWLIST_MODE_DISABLED)")
                .that(allowlistModeToString(ALLOWLIST_MODE_DISABLED)).isEqualTo("DISABLED");
        expectWithMessage("allowlistModeToString(ALLOWLIST_MODE_ENABLED)")
                .that(allowlistModeToString(ALLOWLIST_MODE_ENABLED)).isEqualTo("ENABLED");
        expectWithMessage("allowlistModeToString(ALLOWLIST_MODE_INVALID)")
                .that(allowlistModeToString(ALLOWLIST_MODE_INVALID)).isEqualTo("INVALID");
        expectWithMessage("allowlistModeToString(%s, which is an unknown value)", UNKNOWN_MODE)
                .that(allowlistModeToString(UNKNOWN_MODE))
                .isEqualTo("ALLOWLIST_MODE_" + UNKNOWN_MODE);
    }


    @Test
    public final void testStatusIsAllowed() {
        expectStatusNotAllowed(STATUS_UNKNOWN);
        expectStatusNotAllowed(STATUS_DISALLOWED_NOT_IN_PERMANENT_LIST);
        expectStatusNotAllowed(STATUS_DISALLOWED_NOT_IN_TEMPORARY_LIST);

        expectStatusAllowed(STATUS_ALLOWED_INVALID_MODE);
        expectStatusAllowed(STATUS_ALLOWED_DISABLED_MODE);
        expectStatusAllowed(STATUS_ALLOWED_TEMPORARY_LIST_EMPTY);
        expectStatusAllowed(STATUS_ALLOWED_BY_TEMPORARY_LIST);
        expectStatusAllowed(STATUS_ALLOWED_PERMANENT_LIST_EMPTY);
        expectStatusAllowed(STATUS_ALLOWED_BY_PERMANENT_LIST);
    }

    private void expectStatusAllowed(@AllowlistStatus int status) {
        expectWithMessage("GenericAllowlist.isAllowed(%s)", allowlistStatusToString(status))
                .that(isAllowed(status))
                .isTrue();
    }

    private void expectStatusNotAllowed(@AllowlistStatus int status) {
        expectWithMessage("GenericAllowlist.isAllowed(%s)", allowlistStatusToString(status))
                .that(isAllowed(status))
                .isFalse();
    }

    @Test
    public final void testAllowlistStatusToString() {
        expectWithMessage("allowlistStatusToString(DISALLOWED_NOT_IN_TEMPORARY_LIST)")
                .that(allowlistStatusToString(STATUS_DISALLOWED_NOT_IN_TEMPORARY_LIST))
                .isEqualTo("DISALLOWED_NOT_IN_TEMPORARY_LIST");
        expectWithMessage("allowlistStatusToString(DISALLOWED_NOT_IN_PERMANENT_LIST)")
                .that(allowlistStatusToString(STATUS_DISALLOWED_NOT_IN_PERMANENT_LIST))
                .isEqualTo("DISALLOWED_NOT_IN_PERMANENT_LIST");
        expectWithMessage("allowlistStatusToString(ALLOWED_INVALID_MODE)")
                .that(allowlistStatusToString(STATUS_ALLOWED_INVALID_MODE))
                .isEqualTo("ALLOWED_INVALID_MODE");
        expectWithMessage("allowlistStatusToString(ALLOWED_DISABLED_MODE)")
                .that(allowlistStatusToString(STATUS_ALLOWED_DISABLED_MODE))
                .isEqualTo("ALLOWED_DISABLED_MODE");
        expectWithMessage("allowlistStatusToString(ALLOWED_TEMPORARY_LIST_EMPTY)")
                .that(allowlistStatusToString(STATUS_ALLOWED_TEMPORARY_LIST_EMPTY))
                .isEqualTo("ALLOWED_TEMPORARY_LIST_EMPTY");
        expectWithMessage("allowlistStatusToString(ALLOWED_BY_TEMPORARY_LIST)")
                .that(allowlistStatusToString(STATUS_ALLOWED_BY_TEMPORARY_LIST))
                .isEqualTo("ALLOWED_BY_TEMPORARY_LIST");
        expectWithMessage("allowlistStatusToString(ALLOWED_PERMANENT_LIST_EMPTY)")
                .that(allowlistStatusToString(STATUS_ALLOWED_PERMANENT_LIST_EMPTY))
                .isEqualTo("ALLOWED_PERMANENT_LIST_EMPTY");
        expectWithMessage("allowlistStatusToString(ALLOWED_BY_PERMANENT_LIST)")
                .that(allowlistStatusToString(STATUS_ALLOWED_BY_PERMANENT_LIST))
                .isEqualTo("ALLOWED_BY_PERMANENT_LIST");
        expectWithMessage("allowlistStatusToString(ALLOWED_ALLOWLISTING_DISABLED_BY_SHELL_CMD)")
                .that(allowlistStatusToString(STATUS_ALLOWED_ALLOWLISTING_DISABLED_BY_SHELL_CMD))
                .isEqualTo("ALLOWED_ALLOWLISTING_DISABLED_BY_SHELL_CMD");
        expectWithMessage("allowlistStatusToString("
                + "ALLOWED_ALLOWLISTING_DISABLED_WHILE_DEVICE_IS_PROVISIONING)")
                        .that(allowlistStatusToString(
                                STATUS_ALLOWED_ALLOWLISTING_DISABLED_WHILE_DEVICE_IS_PROVISIONING))
                        .isEqualTo("ALLOWED_ALLOWLISTING_DISABLED_WHILE_DEVICE_IS_PROVISIONING");
        expectWithMessage("allowlistStatusToString("
                + "STATUS_DISALLOWED_FEATURE_DISABLED)")
                        .that(allowlistStatusToString(
                                STATUS_DISALLOWED_FEATURE_DISABLED))
                        .isEqualTo("DISALLOWED_FEATURE_DISABLED");
        expectWithMessage("allowlistStatusToString(666)")
                .that(allowlistStatusToString(666))
                .isEqualTo("STATUS_666");
    }

    @Test
    public final void testSetGetMode() {
        var allowlist = createAllowlist(ALLOWLIST_MODE_ENABLED);
        expectWithMessage("getMode() after constructor")
                .that(allowlist.getMode())
                .isEqualTo(ALLOWLIST_MODE_ENABLED);

        setAndAssertMode(allowlist, ALLOWLIST_MODE_DISABLED);
        setAndAssertMode(allowlist, ALLOWLIST_MODE_ENABLED);
    }

    private void setAndAssertMode(A allowlist, @AllowlistMode int mode) {
        allowlist.setMode(mode);
        expectWithMessage("getMode() after setMode(%s=%s)", mode, allowlistModeToString(mode))
                .that(allowlist.getMode())
                .isEqualTo(mode);
    }

    @Test
    public final void testSetMode_invalid() {
        var allowlist = createAllowlist(ALLOWLIST_MODE_ENABLED);

        assertThrows(IllegalArgumentException.class, () -> allowlist.setMode(UNKNOWN_MODE));

        expectWithMessage("getMode() after setMode() with invalid value")
                .that(allowlist.getMode())
                .isEqualTo(ALLOWLIST_MODE_ENABLED);
    }

    @Test
    public final void testOverrideDisallowedStatus_invalidStatus() {
        var allowlist = createAllowlist(ALLOWLIST_MODE_ENABLED);
        var overridableStatuses = getOverridingStatuses();

        for (int status = STATUS_UNKNOWN; status <= LAST_STATUS_ALLOWED; status++) {
            if (overridableStatuses.contains(status)) {
                continue;
            }
            testOverrideDisallowedStatusWithInvalidStatus(allowlist, status);
        }
    }

    private void testOverrideDisallowedStatusWithInvalidStatus(A allowlist,
            @AllowlistStatus int status) {
        var statusBefore = allowlist.getOverriddenDisallowedStatus();

        var thrown = assertThrows(IllegalArgumentException.class,
                () -> allowlist.overrideDisallowedStatus(status));

        expectWithMessage("exception message").that(thrown)
                .hasMessageThat()
                .contains(status + "(" + allowlistStatusToString(status) + ")");

        expectWithMessage(
                "getOverriddenDisallowedStatus() after trying to set with invalid status (%s)",
                status).that(allowlist.getOverriddenDisallowedStatus())
                        .isEqualTo(statusBefore);
    }

    @Test
    @SpyStatic(Slog.class)
    public final void testOverrideDisallowedStatus_validStatus() {
        var allowlist = createAllowlist(getPermanentName1());
        var overridableStatuses = getOverridingStatuses();
        var originalStatus = STATUS_DISALLOWED_NOT_IN_PERMANENT_LIST;
        var element = getNotAllowlistedElement();

        for (var overriddenStatus : overridableStatuses) {
            allowlist.overrideDisallowedStatus(overriddenStatus);
            expectWithMessage("getOverriddenDisallowedStatus() after setting it")
                    .that(allowlist.getOverriddenDisallowedStatus()).isEqualTo(overriddenStatus);
            expectGetallowlistStatus(allowlist,
                    " after calling overrideDisallowedStatus(" + overriddenStatus + ") ", element,
                    overriddenStatus);

            verifyLogged(allowlist, originalStatus, overriddenStatus, getNotAllowlistedName());

            allowlist.overrideDisallowedStatus(null);
            expectWithMessage("getOverriddenDisallowedStatus() after resetting it")
                    .that(allowlist.getOverriddenDisallowedStatus()).isNull();
            expectGetallowlistStatus(allowlist,
                    " after calling overrideDisallowedStatus(null) ", element,
                    originalStatus);
        }
    }

    // Asserts that originalElement is converted into expectedConvertedName (and returns the latter)
    private String testConversion(E originalElement, String expectedConvertedName) {
        A allowlist = createAllowlist();

        String convertedName = allowlist.toNormalizedName(originalElement);
        expectWithMessage("toNormalizedName(%s)", originalElement)
                .that(convertedName)
                .isEqualTo(expectedConvertedName);

        return convertedName;
    }

    // Asserts that originalName is converted into expectedConvertedElement (and returns the latter)
    private E testConversion(String originalName, E expectedConvertedElement) {
        A allowlist = createAllowlist();

        E convertedElement = allowlist.fromNormalizedName(originalName);
        expectWithMessage("fromNormalizedName(%s)", originalName)
                .that(convertedElement)
                .isEqualTo(expectedConvertedElement);

        return convertedElement;
    }

    // Asserts that originalName is converted into originalElement and vice versa
    private void testNormalizeAndBack(String originalName, E originalElement) {
        String convertedName = testConversion(originalElement, originalName);
        E convertedElement = testConversion(originalName, originalElement);

        // convert back
        testConversion(convertedElement, originalName);
        testConversion(convertedName, originalElement);
    }

    private A createAllowlist(String... configAllowlist) {
        return createAllowlist(ALLOWLIST_MODE_ENABLED, configAllowlist);
    }


    @SafeVarargs
    private void expectNotAllowed(A allowlist, @AllowlistStatus int status, E... elements) {
        for (var element : elements) {
            expectWithMessage("isAllowed(%s)", element)
                    .that(allowlist.isAllowed(element))
                    .isFalse();
            expectGetallowlistStatus(allowlist, element, status);
        }
    }

    @SafeVarargs
    private void expectNotAllowed(A allowlist, E... elements) {
        // Most common case
        expectNotAllowed(allowlist, STATUS_DISALLOWED_NOT_IN_PERMANENT_LIST, elements);
    }

    @SafeVarargs
    private void expectAllowed(A allowlist, @AllowlistStatus int status, E... elements) {
        for (var element : elements) {
            expectWithMessage("isAllowed(%s)", element)
                    .that(allowlist.isAllowed(element))
                    .isTrue();
            expectGetallowlistStatus(allowlist, element, status);
        }
    }

    @SafeVarargs
    private void expectAllowed(A allowlist, E... elements) {
        // Most common case
        expectAllowed(allowlist, STATUS_ALLOWED_BY_PERMANENT_LIST, elements);
    }

    private void expectGetallowlistStatus(A allowlist, E element, @AllowlistStatus int expected) {
        expectGetallowlistStatus(allowlist, /* when= */ "", element, expected);
    }

    private void expectGetallowlistStatus(A allowlist, String when, E element,
            @AllowlistStatus int expected) {
        int actual = allowlist.getAllowlistStatus(element);
        expectWithMessage("getAllowlistStatus(%s)%s(where %s=%s and %s=%s)", element, when,
                expected, allowlistStatusToString(expected),
                actual, allowlistStatusToString(actual))
                .that(actual)
                .isEqualTo(expected);
    }

    @SafeVarargs
    private void expectNotAllowedAfterSettingTemporaryList(A allowlist,
            Collection<E> temporaryAllowList, E...elements) {
        for (var element: elements) {
            expectWithMessage("isAllowed(%s) after SetTemporaryAllowlist(%s)",
                    element, temporaryAllowList).that(allowlist.isAllowed(element)).isFalse();
            expectGetallowlistStatus(allowlist, element, STATUS_DISALLOWED_NOT_IN_TEMPORARY_LIST);
        }
    }

    @SafeVarargs
    private void expectEffectiveAllowlist(A allowlist, E... elements) {
        expectWithMessage("getEffectiveAllowlist()")
                .that(allowlist.getEffectiveAllowlist())
                .containsExactlyElementsIn(elements);
    }

    @SafeVarargs
    private void expectAllowedAfterSettingTemporaryList(A allowlist,
            Collection<E> temporaryAllowList, @AllowlistStatus int status, E... elements) {
        for (var element: elements) {
            expectWithMessage("isAllowed(%s) after SetTemporaryAllowlist(%s)",
                    element, temporaryAllowList).that(allowlist.isAllowed(element)).isTrue();
            expectGetallowlistStatus(allowlist, element, status);
        }
    }

    @SafeVarargs
    private void expectAllowedAfterSettingTemporaryList(A allowlist,
            Collection<E> temporaryAllowList, E... elements) {
        // Most common case
        expectAllowedAfterSettingTemporaryList(allowlist, temporaryAllowList,
                STATUS_ALLOWED_BY_TEMPORARY_LIST, elements);
    }
    private void expectDefaultPermanentElementsNotAllowed(A allowlist) {
        expectNotAllowed(allowlist, STATUS_DISALLOWED_NOT_IN_TEMPORARY_LIST,
                getPermanentElement1(), getPermanentElement2(), getPermanentElement3());
    }

    // NOTE: caller must be annotated with: @SpyStatic(Slog.class)
    private void verifyLogged(A allowlist, @AllowlistStatus int originalStatus,
            @AllowlistStatus int overriddenStatus, String elementName) {
        String message = String.format(ALLOWED_BY_OVERRIDDEN_STATUS_MESSAGE_TEMPLATE, elementName,
                originalStatus, allowlistStatusToString(originalStatus),
                overriddenStatus, allowlistStatusToString(overriddenStatus));

        verify(() -> Slog.w(allowlist.mTag, message));
    }

    private String dump(A allowlist) throws IOException {
        try (StringWriter sw = new StringWriter()) {
            allowlist.dump(new PrintWriter(sw), /* prefix= */ "", /* header= */ "Dumpo");
            return sw.toString();
        }
    }
}
