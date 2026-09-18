
/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.accounts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.accounts.Account;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;
import android.os.Build;
import android.util.Pair;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Tests for {@link AccountsDb}.
 * <p>Run with:<pre>
 * m FrameworksServicesTests &&
 * adb install \
 * -r out/target/product/marlin/data/app/FrameworksServicesTests/FrameworksServicesTests.apk &&
 * adb shell am instrument -e class com.android.server.accounts.AccountsDbTest \
 * -w com.android.frameworks.servicestests/androidx.test.runner.AndroidJUnitRunner
 * </pre>
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class AccountsDbTest {
    private static final String PREN_DB = "pren.db";
    private static final String DE_DB = "de.db";
    private static final String CE_DB = "ce.db";

    private AccountsDb mAccountsDb;
    private File preNDb;
    private File deDb;
    private File ceDb;

    @Mock private PrintWriter mockWriter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Context context = InstrumentationRegistry.getContext();
        preNDb = new File(context.getCacheDir(), PREN_DB);
        ceDb = new File(context.getCacheDir(), CE_DB);
        deDb = new File(context.getCacheDir(), DE_DB);
        deleteDbFiles();
        mAccountsDb = AccountsDb.create(context, 0, preNDb, deDb);
    }

    @After
    public void tearDown() {
        deleteDbFiles();
    }

    private void deleteDbFiles() {
        AccountsDb.deleteDbFileWarnIfFailed(preNDb);
        AccountsDb.deleteDbFileWarnIfFailed(ceDb);
        AccountsDb.deleteDbFileWarnIfFailed(deDb);
    }

    @Test
    public void testDeAccountInsertFindDelete() {
        Account account = new Account("name", "example.com");
        long accId = 1;
        mAccountsDb.insertDeAccount(account, accId);
        long actualId = mAccountsDb.findDeAccountId(account);
        assertEquals(accId, actualId);
        // Delete and verify that account no longer exists
        mAccountsDb.deleteDeAccount(accId);
        actualId = mAccountsDb.findDeAccountId(account);
        assertEquals(-1, actualId);
    }

    @Test
    public void testCeAccountInsertFindDelete() {
        mAccountsDb.attachCeDatabase(ceDb);
        Account account = new Account("name", "example.com");
        long accId = mAccountsDb.insertCeAccount(account, "password");
        long actualId = mAccountsDb.findCeAccountId(account);
        assertEquals(accId, actualId);
        // Delete and verify that account no longer exists
        mAccountsDb.deleteCeAccount(accId);
        actualId = mAccountsDb.findCeAccountId(account);
        assertEquals(-1, actualId);
    }

    @Test
    public void testInsertOrReplaceAuthToken() {
        mAccountsDb.attachCeDatabase(ceDb);
        Account account = new Account("name", "example.com");
        long accId = mAccountsDb.insertCeAccount(account, "password");
        mAccountsDb.insertDeAccount(account, accId);

        mAccountsDb.insertOrReplaceAuthToken(accId, "type", "token1");
        Map<String, String> tokens = mAccountsDb.findAuthTokensByAccount(account);
        assertEquals("token1", tokens.get("type"));

        mAccountsDb.insertOrReplaceAuthToken(accId, "type", "token2");
        tokens = mAccountsDb.findAuthTokensByAccount(account);
        assertEquals("token2", tokens.get("type"));
        assertEquals(1, tokens.size());
    }

    @Test
    public void testInvalidateAuthToken() {
        mAccountsDb.attachCeDatabase(ceDb);
        Account account1 = new Account("name1", "example.com");
        long accId1 = mAccountsDb.insertCeAccount(account1, "password");
        mAccountsDb.insertDeAccount(account1, accId1);
        mAccountsDb.insertAuthToken(accId1, "type1", "token_to_invalidate");
        mAccountsDb.insertAuthToken(accId1, "type2", "token_to_keep");

        Account account2 = new Account("name2", "example.com");
        long accId2 = mAccountsDb.insertCeAccount(account2, "password");
        mAccountsDb.insertDeAccount(account2, accId2);
        mAccountsDb.insertAuthToken(accId2, "type1", "token_to_invalidate");

        Account account3 = new Account("name3", "another.com");
        long accId3 = mAccountsDb.insertCeAccount(account3, "password");
        mAccountsDb.insertDeAccount(account3, accId3);
        mAccountsDb.insertAuthToken(accId3, "type1", "token_to_invalidate");

        List<Pair<Account, String>> invalidated = mAccountsDb.invalidateAuthToken(
                "example.com", "token_to_invalidate");

        assertEquals(2, invalidated.size());
        assertTrue(invalidated.contains(Pair.create(account1, "type1")));
        assertTrue(invalidated.contains(Pair.create(account2, "type1")));

        // Verify tokens were deleted
        Map<String, String> tokens1 = mAccountsDb.findAuthTokensByAccount(account1);
        assertEquals(1, tokens1.size());
        assertEquals("token_to_keep", tokens1.get("type2"));

        Map<String, String> tokens2 = mAccountsDb.findAuthTokensByAccount(account2);
        assertTrue(tokens2.isEmpty());

        Map<String, String> tokens3 = mAccountsDb.findAuthTokensByAccount(account3);
        assertEquals(1, tokens3.size());
        assertEquals("token_to_invalidate", tokens3.get("type1"));
    }

    @Test
    public void testInsertOrReplaceExtra() {
        mAccountsDb.attachCeDatabase(ceDb);
        Account account = new Account("name", "example.com");
        long accId = mAccountsDb.insertCeAccount(account, "password");
        mAccountsDb.insertDeAccount(account, accId);
        String extraKey = "extra_key";

        mAccountsDb.insertOrReplaceExtra(accId, extraKey, "value1");
        Map<String, String> extras = mAccountsDb.findUserExtrasForAccount(account);
        assertEquals("value1", extras.get(extraKey));

        mAccountsDb.insertOrReplaceExtra(accId, extraKey, "value2");
        extras = mAccountsDb.findUserExtrasForAccount(account);
        assertEquals("value2", extras.get(extraKey));
        assertEquals(1, extras.size());
    }

    @Test
    public void testGrantsInsertFindDelete() {
        mAccountsDb.attachCeDatabase(ceDb);
        Account account = new Account("name", "example.com");
        long accId = mAccountsDb.insertCeAccount(account, "password");
        mAccountsDb.insertDeAccount(account, accId);
        int testUid = 100500;
        long grantId = mAccountsDb.insertGrant(accId, "tokenType", testUid);
        assertTrue(grantId > 0);
        int[] allUidGrants = mAccountsDb.findAllUidGrants();
        int[] expectedUids = new int[]{testUid};
        assertArrayEquals(expectedUids, allUidGrants);

        long matchingGrantsCount = mAccountsDb.findMatchingGrantsCount(
                testUid, "tokenType", account);
        assertEquals(1, matchingGrantsCount);
        // Test nonexistent type
        matchingGrantsCount = mAccountsDb.findMatchingGrantsCount(
                testUid, "noSuchType", account);
        assertEquals(0, matchingGrantsCount);

        matchingGrantsCount = mAccountsDb.findMatchingGrantsCountAnyToken(testUid, account);
        assertEquals(1, matchingGrantsCount);

        Pair<String[], int[]> allAccountGrants = mAccountsDb.findAllAccountGrants();
        assertEquals(1, allAccountGrants.first.length);
        assertEquals(account.name, allAccountGrants.first[0]);
        assertEquals(testUid, allAccountGrants.second[0]);

        mAccountsDb.deleteGrantsByUid(testUid);
        allUidGrants = mAccountsDb.findAllUidGrants();
        assertArrayEquals("Test grants should be removed", new int[]{}, allUidGrants);
    }

    @Test
    public void testSharedAccountsInsertFindDelete() {
        Account account = new Account("name", "example.com");
        long accId = 0;
        mAccountsDb.insertDeAccount(account, accId);
        long sharedAccId = mAccountsDb.insertSharedAccount(account);
        long foundSharedAccountId = mAccountsDb.findSharedAccountId(account);
        assertEquals(sharedAccId, foundSharedAccountId);
        Account[] sharedAccounts = mAccountsDb.getSharedAccounts();
        assertEquals(new Account[] {account}, sharedAccounts);

        // Delete and verify
        mAccountsDb.deleteSharedAccount(account);
        foundSharedAccountId = mAccountsDb.findSharedAccountId(account);
        assertEquals(-1, foundSharedAccountId);
    }

    @Test
    public void testMetaInsertFindDelete() {
        int testUid = 100500;
        String authenticatorType = "authType";
        mAccountsDb.insertOrReplaceMetaAuthTypeAndUid(authenticatorType, testUid);
        Map<String, Integer> metaAuthUid = mAccountsDb.findMetaAuthUid();
        assertEquals(1, metaAuthUid.size());
        assertEquals(testUid, (int)metaAuthUid.get(authenticatorType));

        // Delete and verify
        boolean deleteResult = mAccountsDb.deleteMetaByAuthTypeAndUid(authenticatorType, testUid);
        assertTrue(deleteResult);
        metaAuthUid = mAccountsDb.findMetaAuthUid();
        assertEquals(0, metaAuthUid.size());
    }

    @Test
    public void testUpdateDeAccountLastAuthenticatedTime() {
        Account account = new Account("name", "example.com");
        long accId = 1;
        mAccountsDb.insertDeAccount(account, accId);
        long now = System.currentTimeMillis();
        mAccountsDb.updateAccountLastAuthenticatedTime(account);
        long time = mAccountsDb.findAccountLastAuthenticatedTime(account);
        assertTrue("LastAuthenticatedTime should be current", time >= now);
    }

    @Test
    public void testRenameAccount() {
        mAccountsDb.attachCeDatabase(ceDb);
        Account account = new Account("name", "example.com");
        long accId = mAccountsDb.insertCeAccount(account, "password");
        mAccountsDb.insertDeAccount(account, accId);
        mAccountsDb.renameDeAccount(accId, "newName", "name");
        Account newAccount = mAccountsDb.findAllDeAccounts().get(accId);
        assertEquals("newName", newAccount.name);

        String prevName = mAccountsDb.findDeAccountPreviousName(newAccount);
        assertEquals("name", prevName);
        mAccountsDb.renameCeAccount(accId, "newName");
        long foundAccId = mAccountsDb.findCeAccountId(account);
        assertEquals("Account shouldn't be found under the old name", -1, foundAccId);
        foundAccId = mAccountsDb.findCeAccountId(newAccount);
        assertEquals(accId, foundAccId);
    }

    @Test
    public void testUpdateCeAccountPassword() {
        mAccountsDb.attachCeDatabase(ceDb);
        Account account = new Account("name", "example.com");
        long accId = mAccountsDb.insertCeAccount(account, "password");
        String newPassword = "newPassword";
        mAccountsDb.updateCeAccountPassword(accId, newPassword);
        String actualPassword = mAccountsDb
                .findAccountPasswordByNameAndType(account.name, account.type);
        assertEquals(newPassword, actualPassword);
    }

    @Test
    public void testFindCeAccountsNotInDe() {
        mAccountsDb.attachCeDatabase(ceDb);
        Account account = new Account("name", "example.com");
        long accId = mAccountsDb.insertCeAccount(account, "password");
        mAccountsDb.insertDeAccount(account, accId);

        Account accountNotInDe = new Account("name2", "example.com");
        mAccountsDb.insertCeAccount(accountNotInDe, "password");

        List<Account> ceAccounts = mAccountsDb.findCeAccountsNotInDe();
        List<Account> expectedList = Arrays.asList(accountNotInDe);
        assertEquals(expectedList, ceAccounts);
    }

    @Test
    public void testCrossDbTransactions() {
        mAccountsDb.attachCeDatabase(ceDb);
        mAccountsDb.beginTransactionDe();
        mAccountsDb.beginTransactionCe();
        Account account = new Account("name", "example.com");
        long accId;
        accId = mAccountsDb.insertCeAccount(account, "password");
        accId = mAccountsDb.insertDeAccount(account, accId);
        long actualId = mAccountsDb.findCeAccountId(account);
        assertEquals(accId, actualId);
        actualId = mAccountsDb.findDeAccountId(account);
        assertEquals(accId, actualId);
        mAccountsDb.endTransactionCe();
        mAccountsDb.endTransactionDe();
        // Verify that records were removed
        actualId = mAccountsDb.findCeAccountId(account);
        assertEquals(-1, actualId);
        actualId = mAccountsDb.findDeAccountId(account);
        assertEquals(-1, actualId);
    }

    @Test
    public void testFindDeAccountByAccountId() {
        long accId = 10;
        Account account = new Account("name", "example.com");
        assertNull(mAccountsDb.findDeAccountByAccountId(accId));

        mAccountsDb.insertDeAccount(account, accId);

        Account foundAccount = mAccountsDb.findDeAccountByAccountId(accId);
        assertEquals(account, foundAccount);
    }

    @Test
    public void testVisibilityFindSetDelete() {
        long accId = 10;
        String packageName1 = "com.example.one";
        String packageName2 = "com.example.two";
        Account account = new Account("name", "example.com");
        assertNull(mAccountsDb.findAccountVisibility(account, packageName1));

        mAccountsDb.insertDeAccount(account, accId);
        assertNull(mAccountsDb.findAccountVisibility(account, packageName1));
        assertNull(mAccountsDb.findAccountVisibility(accId, packageName1));

        mAccountsDb.setAccountVisibility(accId, packageName1, 1);
        assertEquals(mAccountsDb.findAccountVisibility(account, packageName1), Integer.valueOf(1));
        assertEquals(mAccountsDb.findAccountVisibility(accId, packageName1), Integer.valueOf(1));

        mAccountsDb.setAccountVisibility(accId, packageName2, 2);
        assertEquals(mAccountsDb.findAccountVisibility(accId, packageName2), Integer.valueOf(2));

        mAccountsDb.setAccountVisibility(accId, packageName2, 3);
        assertEquals(mAccountsDb.findAccountVisibility(accId, packageName2), Integer.valueOf(3));

        Map<String, Integer> vis = mAccountsDb.findAllVisibilityValuesForAccount(account);
        assertEquals(vis.size(), 2);
        assertEquals(vis.get(packageName1), Integer.valueOf(1));
        assertEquals(vis.get(packageName2), Integer.valueOf(3));

        assertTrue(mAccountsDb.deleteAccountVisibilityForPackage(packageName1));
        assertNull(mAccountsDb.findAccountVisibility(accId, packageName1));
        assertFalse(mAccountsDb.deleteAccountVisibilityForPackage(packageName1)); // 2nd attempt.
    }

    @Test
    public void testFindAllVisibilityValues() {
        long accId = 10;
        long accId2 = 11;
        String packageName1 = "com.example.one";
        String packageName2 = "com.example.two";
        Account account = new Account("name", "example.com");
        Account account2 = new Account("name2", "example2.com");
        assertNull(mAccountsDb.findAccountVisibility(account, packageName1));

        mAccountsDb.insertDeAccount(account, accId);
        assertNull(mAccountsDb.findAccountVisibility(account, packageName1));
        assertNull(mAccountsDb.findAccountVisibility(accId, packageName1));
        mAccountsDb.insertDeAccount(account2, accId2);

        mAccountsDb.setAccountVisibility(accId, packageName1, 1);
        mAccountsDb.setAccountVisibility(accId, packageName2, 2);
        mAccountsDb.setAccountVisibility(accId2, packageName1, 1);

        Map<Account, Map<String, Integer>> vis = mAccountsDb.findAllVisibilityValues();
        assertEquals(vis.size(), 2);
        Map<String, Integer> accnt1Visibility = vis.get(account);
        assertEquals(accnt1Visibility.size(), 2);
        assertEquals(accnt1Visibility.get(packageName1), Integer.valueOf(1));
        assertEquals(accnt1Visibility.get(packageName2), Integer.valueOf(2));
        Map<String, Integer> accnt2Visibility = vis.get(account2);
        assertEquals(accnt2Visibility.size(), 1);
        assertEquals(accnt2Visibility.get(packageName1), Integer.valueOf(1));

        mAccountsDb.setAccountVisibility(accId2, packageName2, 3);
        vis = mAccountsDb.findAllVisibilityValues();
        accnt2Visibility = vis.get(account2);
        assertEquals(accnt2Visibility.size(), 2);
        assertEquals(accnt2Visibility.get(packageName2), Integer.valueOf(3));
    }

    @Test
    public void testVisibilityCleanupTrigger() {
        long accId = 10;
        String packageName1 = "com.example.one";
        Account account = new Account("name", "example.com");

        assertNull(mAccountsDb.findAccountVisibility(account, packageName1));
        mAccountsDb.insertDeAccount(account, accId);
        assertNull(mAccountsDb.findAccountVisibility(account, packageName1));

        mAccountsDb.setAccountVisibility(accId, packageName1, 1);
        assertEquals(mAccountsDb.findAccountVisibility(accId, packageName1), Integer.valueOf(1));

        assertTrue(mAccountsDb.deleteDeAccount(accId)); // Trigger should remove visibility.
        assertNull(mAccountsDb.findAccountVisibility(account, packageName1));
    }

    @Test
    public void testDumpDebugTable() {
        long accId = 10;
        long insertionPoint = mAccountsDb.reserveDebugDbInsertionPoint();

        SQLiteStatement logStatement = mAccountsDb.getStatementForLogging();

        logStatement.bindLong(1, accId);
        logStatement.bindString(2, "action");
        logStatement.bindString(3, "date");
        logStatement.bindLong(4, 10);
        logStatement.bindString(5, "table");
        logStatement.bindLong(6, insertionPoint);
        logStatement.execute();

        mAccountsDb.dumpDebugTable(mockWriter);

        verify(mockWriter, times(3)).println(anyString());
    }

    @Test
    public void testReserveDebugDbInsertionPoint() {
        long insertionPoint = mAccountsDb.reserveDebugDbInsertionPoint();
        long insertionPoint2 = mAccountsDb.reserveDebugDbInsertionPoint();

        assertEquals(0, insertionPoint);
        assertEquals(1, insertionPoint2);
    }
}
