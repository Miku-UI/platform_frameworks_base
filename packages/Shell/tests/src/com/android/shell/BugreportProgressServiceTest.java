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
 * limitations under the License.
 */
package com.android.shell;

import static com.android.shell.BugreportProgressService.findSendToAccount;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static junit.framework.TestCase.assertTrue;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.BugreportParams;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.os.UserManager;
import android.test.mock.MockContext;
import android.util.Pair;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Test for {@link BugreportProgressServiceTest}.
 *
 * Usage: {@code atest BugreportProgressServiceTest}
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public final class BugreportProgressServiceTest {

    public static final String BASE_NAME = "baseName";
    public static final String NAME = "name";
    public static final String BUGREPORT_TXT = "bugreport.txt";
    public static final String BUGREPORT_ZIP = "bugreport.zip";
    public static final String SCREENSHOT_PNG = "screenshot.png";
    public static final String RENAMED_SCREENSHOT_PNG = "renamed_screenshot.png";
    public static final String BASE_BUGREPORT = "bugreport";
    public static final String REPORT_NAME = "my_report";
    public static final String BUGREPORT_TITLE = "My Bug Report";
    public static final String BUGREPORT_DESCRIPTION = "Details of the bug";

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private UserManager mMockUserManager;

    @Mock
    private AccountManager mMockAccountManager;

    private File mBugreportsDir;

    @Before
    public void setUp() throws IOException {
        mBugreportsDir = tempFolder.newFolder("bugreports");
    }

    private final class MyContext extends MockContext {

        @Override
        public Object getSystemService(String name) {
            switch (name) {
                case Context.USER_SERVICE:
                    return mMockUserManager;
                case Context.ACCOUNT_SERVICE:
                    return mMockAccountManager;
                default:
                    return super.getSystemService(name);
            }
        }

        @Override
        public String getSystemServiceName(Class<?> serviceClass) {
            if (UserManager.class.equals(serviceClass)) {
                return Context.USER_SERVICE;
            }
            if (AccountManager.class.equals(serviceClass)) {
                return Context.ACCOUNT_SERVICE;
            }
            return super.getSystemServiceName(serviceClass);
        }
    }

    private final MyContext mTestContext = new MyContext();

    private static <T> List<T> list(T... array) {
        return Arrays.asList(array);
    }

    private static <T> T[] array(T... array) {
        return array;
    }

    private Account account(String email) {
        return new Account(email, "test.com");
    }

    private void checkFindSendToAccount(
            int expectedUserId, String expectedEmail, String preferredDomain) {
        final Pair<UserHandle, Account> actual = findSendToAccount(mTestContext, preferredDomain);
        assertEquals(UserHandle.of(expectedUserId), actual.first);
        assertEquals(account(expectedEmail), actual.second);
    }

    @Test
    public void findSendToAccount_noWorkProfile() {
        when(mMockUserManager.getUserProfiles()).thenReturn(
                list(UserHandle.of(UserHandle.USER_SYSTEM)));

        // No accounts.
        when(mMockAccountManager.getAccountsAsUser(eq(UserHandle.USER_SYSTEM))).thenReturn(
                array());

        assertNull(findSendToAccount(mTestContext, null));
        assertNull(findSendToAccount(mTestContext, ""));
        assertNull(findSendToAccount(mTestContext, "android.com"));
        assertNull(findSendToAccount(mTestContext, "@android.com"));

        // 1 account
        when(mMockAccountManager.getAccountsAsUser(eq(UserHandle.USER_SYSTEM))).thenReturn(
                array(account("abc@gmail.com")));

        checkFindSendToAccount(0, "abc@gmail.com", null);
        checkFindSendToAccount(0, "abc@gmail.com", "");
        checkFindSendToAccount(0, "abc@gmail.com", "android.com");
        checkFindSendToAccount(0, "abc@gmail.com", "@android.com");
        checkFindSendToAccount(0, "abc@gmail.com", "gmail.com");
        checkFindSendToAccount(0, "abc@gmail.com", "@gmail.com");

        // 2 accounts, same domain
        when(mMockAccountManager.getAccountsAsUser(eq(UserHandle.USER_SYSTEM))).thenReturn(
                array(account("abc@gmail.com"), account("def@gmail.com")));

        checkFindSendToAccount(0, "abc@gmail.com", null);
        checkFindSendToAccount(0, "abc@gmail.com", "");
        checkFindSendToAccount(0, "abc@gmail.com", "android.com");
        checkFindSendToAccount(0, "abc@gmail.com", "@android.com");
        checkFindSendToAccount(0, "abc@gmail.com", "gmail.com");
        checkFindSendToAccount(0, "abc@gmail.com", "@gmail.com");

        // 2 accounts, different domains
        when(mMockAccountManager.getAccountsAsUser(eq(UserHandle.USER_SYSTEM))).thenReturn(
                array(account("abc@gmail.com"), account("def@android.com")));

        checkFindSendToAccount(0, "abc@gmail.com", null);
        checkFindSendToAccount(0, "abc@gmail.com", "");
        checkFindSendToAccount(0, "def@android.com", "android.com");
        checkFindSendToAccount(0, "def@android.com", "@android.com");
        checkFindSendToAccount(0, "abc@gmail.com", "gmail.com");
        checkFindSendToAccount(0, "abc@gmail.com", "@gmail.com");

        // Plut an account that doesn't look like an email address.
        when(mMockAccountManager.getAccountsAsUser(eq(UserHandle.USER_SYSTEM))).thenReturn(
                array(account("notemail"), account("abc@gmail.com"), account("def@android.com")));

        checkFindSendToAccount(0, "abc@gmail.com", null);
        checkFindSendToAccount(0, "abc@gmail.com", "");
        checkFindSendToAccount(0, "def@android.com", "android.com");
        checkFindSendToAccount(0, "def@android.com", "@android.com");
        checkFindSendToAccount(0, "abc@gmail.com", "gmail.com");
        checkFindSendToAccount(0, "abc@gmail.com", "@gmail.com");
    }

    /**
     * Same as {@link #findSendToAccount_noWorkProfile()}, but with work profile, which has no
     * accounts.  The expected results are the same as the original.
     */
    @Test
    public void findSendToAccount_withWorkProfile_noAccounts() {
        when(mMockUserManager.getUserProfiles()).thenReturn(
                list(UserHandle.of(UserHandle.USER_SYSTEM), UserHandle.of(10)));

        // Work profile has no accounts
        when(mMockAccountManager.getAccountsAsUser(eq(10))).thenReturn(
                array());

        // No accounts.
        when(mMockAccountManager.getAccountsAsUser(eq(UserHandle.USER_SYSTEM))).thenReturn(
                array());

        assertNull(findSendToAccount(mTestContext, null));
        assertNull(findSendToAccount(mTestContext, ""));
        assertNull(findSendToAccount(mTestContext, "android.com"));
        assertNull(findSendToAccount(mTestContext, "@android.com"));

        // 1 account
        when(mMockAccountManager.getAccountsAsUser(eq(UserHandle.USER_SYSTEM))).thenReturn(
                array(account("abc@gmail.com")));

        checkFindSendToAccount(0, "abc@gmail.com", null);
        checkFindSendToAccount(0, "abc@gmail.com", "");
        checkFindSendToAccount(0, "abc@gmail.com", "android.com");
        checkFindSendToAccount(0, "abc@gmail.com", "@android.com");
        checkFindSendToAccount(0, "abc@gmail.com", "gmail.com");
        checkFindSendToAccount(0, "abc@gmail.com", "@gmail.com");

        // 2 accounts, same domain
        when(mMockAccountManager.getAccountsAsUser(eq(UserHandle.USER_SYSTEM))).thenReturn(
                array(account("abc@gmail.com"), account("def@gmail.com")));

        checkFindSendToAccount(0, "abc@gmail.com", null);
        checkFindSendToAccount(0, "abc@gmail.com", "");
        checkFindSendToAccount(0, "abc@gmail.com", "android.com");
        checkFindSendToAccount(0, "abc@gmail.com", "@android.com");
        checkFindSendToAccount(0, "abc@gmail.com", "gmail.com");
        checkFindSendToAccount(0, "abc@gmail.com", "@gmail.com");

        // 2 accounts, different domains
        when(mMockAccountManager.getAccountsAsUser(eq(UserHandle.USER_SYSTEM))).thenReturn(
                array(account("abc@gmail.com"), account("def@android.com")));

        checkFindSendToAccount(0, "abc@gmail.com", null);
        checkFindSendToAccount(0, "abc@gmail.com", "");
        checkFindSendToAccount(0, "def@android.com", "android.com");
        checkFindSendToAccount(0, "def@android.com", "@android.com");
        checkFindSendToAccount(0, "abc@gmail.com", "gmail.com");
        checkFindSendToAccount(0, "abc@gmail.com", "@gmail.com");

        // Plut an account that doesn't look like an email address.
        when(mMockAccountManager.getAccountsAsUser(eq(UserHandle.USER_SYSTEM))).thenReturn(
                array(account("notemail"), account("abc@gmail.com"), account("def@android.com")));

        checkFindSendToAccount(0, "abc@gmail.com", null);
        checkFindSendToAccount(0, "abc@gmail.com", "");
        checkFindSendToAccount(0, "def@android.com", "android.com");
        checkFindSendToAccount(0, "def@android.com", "@android.com");
        checkFindSendToAccount(0, "abc@gmail.com", "gmail.com");
        checkFindSendToAccount(0, "abc@gmail.com", "@gmail.com");
    }

    /**
     * Same as {@link #findSendToAccount_noWorkProfile()}, but with work profile, which has
     * 1 account.  The expected results are the same as the original, expect for the "no accounts
     * on the primary profile" case.
     */
    @Test
    public void findSendToAccount_withWorkProfile_1account() {
        when(mMockUserManager.getUserProfiles()).thenReturn(
                list(UserHandle.of(UserHandle.USER_SYSTEM), UserHandle.of(10)));

        // Work profile has no accounts
        when(mMockAccountManager.getAccountsAsUser(eq(10))).thenReturn(
                array(account("xyz@gmail.com")));

        // No accounts.
        when(mMockAccountManager.getAccountsAsUser(eq(UserHandle.USER_SYSTEM))).thenReturn(
                array());

        checkFindSendToAccount(10, "xyz@gmail.com", null);
        checkFindSendToAccount(10, "xyz@gmail.com", "");
        checkFindSendToAccount(10, "xyz@gmail.com", "android.com");
        checkFindSendToAccount(10, "xyz@gmail.com", "@android.com");

        // 1 account
        when(mMockAccountManager.getAccountsAsUser(eq(UserHandle.USER_SYSTEM))).thenReturn(
                array(account("abc@gmail.com")));

        checkFindSendToAccount(0, "abc@gmail.com", null);
        checkFindSendToAccount(0, "abc@gmail.com", "");
        checkFindSendToAccount(0, "abc@gmail.com", "android.com");
        checkFindSendToAccount(0, "abc@gmail.com", "@android.com");
        checkFindSendToAccount(0, "abc@gmail.com", "gmail.com");
        checkFindSendToAccount(0, "abc@gmail.com", "@gmail.com");

        // 2 accounts, same domain
        when(mMockAccountManager.getAccountsAsUser(eq(UserHandle.USER_SYSTEM))).thenReturn(
                array(account("abc@gmail.com"), account("def@gmail.com")));

        checkFindSendToAccount(0, "abc@gmail.com", null);
        checkFindSendToAccount(0, "abc@gmail.com", "");
        checkFindSendToAccount(0, "abc@gmail.com", "android.com");
        checkFindSendToAccount(0, "abc@gmail.com", "@android.com");
        checkFindSendToAccount(0, "abc@gmail.com", "gmail.com");
        checkFindSendToAccount(0, "abc@gmail.com", "@gmail.com");

        // 2 accounts, different domains
        when(mMockAccountManager.getAccountsAsUser(eq(UserHandle.USER_SYSTEM))).thenReturn(
                array(account("abc@gmail.com"), account("def@android.com")));

        checkFindSendToAccount(0, "abc@gmail.com", null);
        checkFindSendToAccount(0, "abc@gmail.com", "");
        checkFindSendToAccount(0, "def@android.com", "android.com");
        checkFindSendToAccount(0, "def@android.com", "@android.com");
        checkFindSendToAccount(0, "abc@gmail.com", "gmail.com");
        checkFindSendToAccount(0, "abc@gmail.com", "@gmail.com");

        // Plut an account that doesn't look like an email address.
        when(mMockAccountManager.getAccountsAsUser(eq(UserHandle.USER_SYSTEM))).thenReturn(
                array(account("notemail"), account("abc@gmail.com"), account("def@android.com")));

        checkFindSendToAccount(0, "abc@gmail.com", null);
        checkFindSendToAccount(0, "abc@gmail.com", "");
        checkFindSendToAccount(0, "def@android.com", "android.com");
        checkFindSendToAccount(0, "def@android.com", "@android.com");
        checkFindSendToAccount(0, "abc@gmail.com", "gmail.com");
        checkFindSendToAccount(0, "abc@gmail.com", "@gmail.com");
    }

    /**
     * Same as {@link #findSendToAccount_noWorkProfile()}, but with work profile, with mixed
     * domains.
     */
    @Test
    public void findSendToAccount_withWorkProfile_mixedDomains() {
        when(mMockUserManager.getUserProfiles()).thenReturn(
                list(UserHandle.of(UserHandle.USER_SYSTEM), UserHandle.of(10)));

        // Work profile has no accounts
        when(mMockAccountManager.getAccountsAsUser(eq(10))).thenReturn(
                array(account("xyz@android.com")));

        // No accounts.
        when(mMockAccountManager.getAccountsAsUser(eq(UserHandle.USER_SYSTEM))).thenReturn(
                array());

        checkFindSendToAccount(10, "xyz@android.com", null);
        checkFindSendToAccount(10, "xyz@android.com", "");
        checkFindSendToAccount(10, "xyz@android.com", "android.com");
        checkFindSendToAccount(10, "xyz@android.com", "@android.com");

        // 1 account
        when(mMockAccountManager.getAccountsAsUser(eq(UserHandle.USER_SYSTEM))).thenReturn(
                array(account("abc@gmail.com")));

        checkFindSendToAccount(0, "abc@gmail.com", null);
        checkFindSendToAccount(0, "abc@gmail.com", "");
        checkFindSendToAccount(10, "xyz@android.com", "android.com");
        checkFindSendToAccount(10, "xyz@android.com", "@android.com");
        checkFindSendToAccount(0, "abc@gmail.com", "gmail.com");
        checkFindSendToAccount(0, "abc@gmail.com", "@gmail.com");

        // more accounts.
        when(mMockAccountManager.getAccountsAsUser(eq(UserHandle.USER_SYSTEM))).thenReturn(
                array(account("abc@gmail.com"), account("def@gmail.com")));

        checkFindSendToAccount(0, "abc@gmail.com", null);
        checkFindSendToAccount(0, "abc@gmail.com", "");
        checkFindSendToAccount(10, "xyz@android.com", "android.com");
        checkFindSendToAccount(10, "xyz@android.com", "@android.com");
        checkFindSendToAccount(0, "abc@gmail.com", "gmail.com");
        checkFindSendToAccount(0, "abc@gmail.com", "@gmail.com");
    }

    @Test
    public void testMaybeCreateBugreportFile_withoutUri_createsFile() {
        BugreportProgressService.BugreportLocationInfo info =
                new BugreportProgressService.BugreportLocationInfo(/* bugreportUri= */ null,
                        tempFolder.getRoot(), BASE_NAME, NAME);

        boolean result = info.maybeCreateBugreportFile();

        assertTrue("maybeCreateBugreportFile should return true when file does not exist", result);
        File createdFile = new File(tempFolder.getRoot(), BASE_NAME + "-" + NAME + ".zip");
        assertTrue("Bugreport file should be created", createdFile.exists());
    }


    @Test
    public void testMaybeCreateBugreportFile_withoutUri_doesNotCreateFile() throws IOException {
        BugreportProgressService.BugreportLocationInfo info =
                new BugreportProgressService.BugreportLocationInfo(/* bugreportUri= */ null,
                        tempFolder.getRoot(), BASE_NAME, NAME);

        tempFolder.newFile(BASE_NAME + "-" + NAME + ".zip");

        boolean result = info.maybeCreateBugreportFile();

        assertFalse("maybeCreateBugreportFile should return false when file exist", result);
    }


    @Test
    public void testIsPlainText_withTxtFile() throws IOException {
        File bugreportFile = tempFolder.newFile(BUGREPORT_TXT);

        BugreportProgressService.BugreportLocationInfo info =
                new BugreportProgressService.BugreportLocationInfo(bugreportFile);

        assertTrue("isPlainText should return true for .txt bugreport files", info.isPlainText());

    }

    @Test
    public void testIsPlainText_withZipFile() throws IOException {
        File zipBugreportFile = tempFolder.newFile(BUGREPORT_ZIP);

        BugreportProgressService.BugreportLocationInfo zipInfo =
                new BugreportProgressService.BugreportLocationInfo(zipBugreportFile);

        assertFalse("isPlainText should return false for non-.txt bugreport files",
                zipInfo.isPlainText());
    }

    @Test
    public void testIsFileEmpty_withEmptyFile() throws IOException {
        File bugreportFile = tempFolder.newFile(BUGREPORT_TXT);

        BugreportProgressService.BugreportLocationInfo info =
                new BugreportProgressService.BugreportLocationInfo(bugreportFile);

        assertTrue("isFileEmpty should return true for an empty file",
                info.isFileEmpty(mTestContext));
    }

    @Test
    public void testIsFileEmpty_withNonEmptyFile() throws IOException {
        File bugreportFile = tempFolder.newFile(BUGREPORT_TXT);
        try (FileOutputStream fos = new FileOutputStream(bugreportFile)) {
            fos.write("Bugreport data".getBytes());
        }

        BugreportProgressService.BugreportLocationInfo info =
                new BugreportProgressService.BugreportLocationInfo(bugreportFile);

        assertFalse("isFileEmpty should return false for a non-empty file",
                info.isFileEmpty(mTestContext));
    }


    @Test
    public void testIsValidBugreportResult_withUri() {
        Uri mockUri = Uri.parse("content://com.android.shell/bugreport.txt");
        BugreportProgressService.BugreportLocationInfo info =
                new BugreportProgressService.BugreportLocationInfo(mockUri, mBugreportsDir,
                        BASE_NAME, NAME);

        assertTrue("isValidBugreportResult should always return true for URI case",
                info.isValidBugreportResult());
    }

    @Test
    public void testIsValidBugreportResult_withFile_createdFile_returnsTrue() {
        BugreportProgressService.BugreportLocationInfo info =
                new BugreportProgressService.BugreportLocationInfo(/* bugreportUri= */ null,
                        mBugreportsDir, BASE_NAME, NAME);
        info.createBugreportFile();
        assertTrue("isValidBugreportResult should return true if the file exists and is readable",
                info.isValidBugreportResult());
    }

    @Test
    public void testIsValidBugreportResult_withFile_withoutCreatedFile_return_False() {
        BugreportProgressService.BugreportLocationInfo info =
                new BugreportProgressService.BugreportLocationInfo(/* bugreportUri= */ null,
                        mBugreportsDir, BASE_NAME, NAME);

        assertFalse("isValidBugreportResult should return false if the file does not exist",
                info.isValidBugreportResult());
    }

    @Test
    public void testMaybeDeleteEmptyBugreport_withEmptyFile() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        BugreportProgressService.BugreportLocationInfo info =
                new BugreportProgressService.BugreportLocationInfo(/* bugreportUri= */ null,
                        mBugreportsDir, BASE_NAME, NAME);
        info.createBugreportFile();
        assertNotNull("Bugreport file descriptor should still be accessible",
                info.getBugreportFd(appContext));

        info.maybeDeleteEmptyBugreport();

        assertNull("Bugreport file descriptor should not still be accessible",
                info.getBugreportFd(appContext));
    }

    @Test
    public void testGetBugreportPath_withFile() throws IOException {
        File bugreportFile = tempFolder.newFile(BUGREPORT_TXT);

        BugreportProgressService.BugreportLocationInfo info =
                new BugreportProgressService.BugreportLocationInfo(bugreportFile);

        assertEquals("getBugreportPath should return the absolute file path",
                bugreportFile.getAbsolutePath(), info.getBugreportPath());
    }

    @Test
    public void testGetScreenshotFd_withFile() throws IOException {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File screenshotFile = tempFolder.newFile(SCREENSHOT_PNG);

        BugreportProgressService.ScreenshotLocationInfo info =
                new BugreportProgressService.ScreenshotLocationInfo(null);
        info.mScreenshotFiles.add(screenshotFile);

        ParcelFileDescriptor fd = info.getScreenshotFd(appContext);

        assertNotNull("File descriptor should not be null for file case", fd);
        fd.close();
    }

    @Test
    public void testGetScreenshotPath_withFile() throws IOException {
        File screenshotFile = tempFolder.newFile(SCREENSHOT_PNG);
        screenshotFile.createNewFile();

        BugreportProgressService.ScreenshotLocationInfo info =
                new BugreportProgressService.ScreenshotLocationInfo(null);
        info.mScreenshotFiles.add(screenshotFile);

        assertEquals("getScreenshotPath should return the correct path",
                screenshotFile.getAbsolutePath(),
                info.getScreenshotPath());
    }

    @Test
    public void testRenameScreenshots_withFile() throws IOException {
        File screenshotFile = tempFolder.newFile(SCREENSHOT_PNG);
        screenshotFile.createNewFile();
        try (FileOutputStream fos = new FileOutputStream(screenshotFile)) {
            fos.write(0x00);
        }

        BugreportProgressService.ScreenshotLocationInfo info =
                new BugreportProgressService.ScreenshotLocationInfo(null);
        info.mScreenshotFiles.add(screenshotFile);

        info.renameScreenshots(SCREENSHOT_PNG, RENAMED_SCREENSHOT_PNG);

        assertTrue("Screenshot file should be renamed",
                info.mScreenshotFiles.getFirst().getAbsolutePath()
                        .contains(RENAMED_SCREENSHOT_PNG));
    }

    @Test
    public void testDeleteEmptyScreenshots_withNonEmptyFile() throws IOException {
        File screenshotFile = tempFolder.newFile(SCREENSHOT_PNG);
        screenshotFile.createNewFile();
        try (FileOutputStream fos = new FileOutputStream(screenshotFile)) {
            fos.write(0x00);
        }

        BugreportProgressService.ScreenshotLocationInfo info =
                new BugreportProgressService.ScreenshotLocationInfo(null);
        info.mScreenshotFiles.add(screenshotFile);

        info.deleteEmptyScreenshots();

        assertFalse("Screenshot list should not be empty", info.mScreenshotFiles.isEmpty());
    }

    @Test
    public void testDeleteEmptyScreenshots_withEmptyFile() throws IOException {
        File screenshotFile = tempFolder.newFile(SCREENSHOT_PNG);
        screenshotFile.createNewFile();

        BugreportProgressService.ScreenshotLocationInfo info =
                new BugreportProgressService.ScreenshotLocationInfo(null);
        info.mScreenshotFiles.add(screenshotFile);

        info.deleteEmptyScreenshots();

        assertTrue("Screenshot list should be empty", info.mScreenshotFiles.isEmpty());
    }

    @Test
    public void testGetScreenshotFd_withUri() throws IOException {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        BugreportProgressService.ScreenshotLocationInfo info =
                getScreenshotLocationInfoFromUri(SCREENSHOT_PNG);

        ParcelFileDescriptor fd = info.getScreenshotFd(appContext);

        assertNotNull("File descriptor should not be null for URI case", fd);
        fd.close();
    }

    @Test
    public void testGetScreenshotPath_withUri() throws IOException {
        BugreportProgressService.ScreenshotLocationInfo info =
                getScreenshotLocationInfoFromUri(SCREENSHOT_PNG);

        assertEquals("getScreenshotPath should return the screenshot name when URI is "
                        + "provided", SCREENSHOT_PNG, info.getScreenshotPath());
    }


    @Test
    public void testMaybeCreateBugreportFile_withUriProvided() throws IOException {
        BugreportProgressService.BugreportLocationInfo info = getBugreportLocationInfoFromUri(
                BUGREPORT_TXT);

        boolean result = info.maybeCreateBugreportFile();

        assertTrue("maybeCreateBugreportFile should return true if a URI is provided",
                result);
        assertNull("info.mBugreportFile should be null", info.mBugreportFile);
    }

    @Test
    public void testGetBugreportPath_withUri() throws IOException {
        BugreportProgressService.BugreportLocationInfo info = getBugreportLocationInfoFromUri(
                BUGREPORT_TXT);

        assertEquals("getBugreportPath should return bugreport file name when a URI is "
                        + "provided", BUGREPORT_TXT, info.getBugreportPath());
    }

    @Test
    public void testIsFileEmpty_withUri() throws IOException {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        BugreportProgressService.BugreportLocationInfo info = getBugreportLocationInfoFromUri(
                BUGREPORT_TXT);

        assertTrue("isFileEmpty should return false when URI is provided",
                info.isFileEmpty(appContext));
    }

    @Test
    public void testGetBugreportFd_withUri() throws Exception {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        BugreportProgressService.BugreportLocationInfo info = getBugreportLocationInfoFromUri(
                BUGREPORT_TXT);

        ParcelFileDescriptor fd = info.getBugreportFd(appContext);

        assertNotNull("File descriptor should not be null for URI case", fd);
        fd.close();
    }

    @Test
    public void testIsPlainText_withUri() throws IOException {
        BugreportProgressService.BugreportLocationInfo info = getBugreportLocationInfoFromUri(
                BUGREPORT_TXT);

        assertFalse("isPlainText should return false when URI is used", info.isPlainText());
    }

    @Test
    public void testSetupFilesAndCreateBugreportInfo_withWearBugreport() throws IOException {
        BugreportProgressService service = new BugreportProgressService();

        File fileBugreport = tempFolder.newFile(BUGREPORT_TXT);
        Uri bugreportUri=  Uri.fromFile(fileBugreport);
        File screenshotFile = tempFolder.newFile(SCREENSHOT_PNG);
        Uri screenshotUri = Uri.fromFile(screenshotFile);
        Intent fakeIntent = new Intent();
        ArrayList<Uri> fakeUris = new ArrayList<>();
        fakeUris.add(bugreportUri);
        fakeUris.add(screenshotUri);
        fakeIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, fakeUris);

        long uniqueNonce = System.currentTimeMillis();
        List<Uri> additionalFiles = new ArrayList<>();

        BugreportProgressService.BugreportInfo bugreportInfo =
                service.setupFilesAndCreateBugreportInfo(fakeIntent,
                        BugreportParams.BUGREPORT_MODE_WEAR,
                        BASE_BUGREPORT,
                        REPORT_NAME,
                        BUGREPORT_TITLE,
                        BUGREPORT_DESCRIPTION,
                        uniqueNonce,
                        additionalFiles);

        Assert.assertNotNull("BugreportInfo should not be null", bugreportInfo);
        assertEquals("BugreportLocationInfo should use provided URI",
                fileBugreport.getName(),
                bugreportInfo.bugreportLocationInfo.getBugreportPath());
        assertEquals("ScreenshotLocationInfo should use provided URI",
                screenshotFile.getName(),
                bugreportInfo.screenshotLocationInfo.getScreenshotPath());
    }


    @Test
    public void testSetupFilesAndCreateBugreportInfo_withoutWearBugreport() throws IOException {
        BugreportProgressService service = new BugreportProgressService();

        File fileBugreport = tempFolder.newFile(BUGREPORT_TXT);
        Uri bugreportUri=  Uri.fromFile(fileBugreport);
        File screenshotFile = tempFolder.newFile(SCREENSHOT_PNG);
        Uri screenshotUri = Uri.fromFile(screenshotFile);
        Intent fakeIntent = new Intent();
        ArrayList<Uri> fakeUris = new ArrayList<>();
        fakeUris.add(bugreportUri);
        fakeUris.add(screenshotUri);
        fakeIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, fakeUris);

        long uniqueNonce = System.currentTimeMillis();
        List<Uri> additionalFiles = new ArrayList<>();

        BugreportProgressService.BugreportInfo bugreportInfo =
                service.setupFilesAndCreateBugreportInfo(fakeIntent,
                        BugreportParams.BUGREPORT_MODE_REMOTE,
                        BASE_BUGREPORT,
                        REPORT_NAME,
                        BUGREPORT_TITLE,
                        BUGREPORT_DESCRIPTION,
                        uniqueNonce,
                        additionalFiles);

        Assert.assertNotNull("BugreportInfo should not be null", bugreportInfo);
        assertEquals("BugreportLocationInfo should ignore provided URI",
                "/bugreport-my_report.zip",
                bugreportInfo.bugreportLocationInfo.getBugreportPath());
        assertEquals("ScreenshotLocationInfo should ignore provided URI",
                "/screenshot-my_report-default.png",
                bugreportInfo.screenshotLocationInfo.getScreenshotPath());
    }

    @NotNull
    private BugreportProgressService.BugreportLocationInfo getBugreportLocationInfoFromUri(
            String fileName) throws IOException {
        File file = tempFolder.newFile(fileName);
        Uri uri = Uri.fromFile(file);

        return new BugreportProgressService.BugreportLocationInfo(uri, file.getParentFile(),
                BASE_NAME,
                NAME);
    }


    @NotNull
    private BugreportProgressService.ScreenshotLocationInfo getScreenshotLocationInfoFromUri(
            String fileName) throws IOException {
        File file = tempFolder.newFile(fileName);
        Uri uri = Uri.fromFile(file);
        return new BugreportProgressService.ScreenshotLocationInfo(uri);
    }
}
