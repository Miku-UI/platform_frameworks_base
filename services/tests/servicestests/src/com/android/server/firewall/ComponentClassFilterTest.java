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

package com.android.server.firewall;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManagerInternal;
import android.os.Handler;
import android.os.Looper;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.server.LocalServices;
import com.android.server.devicepolicy.DpmTestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;

@RunWith(AndroidJUnit4.class)
public class ComponentClassFilterTest {
    private static final String TEST_PACKAGE_NAME = "TestPackageName";
    private static final String ACTION_BLOCKED = "ACTION_BLOCKED";

    private static final String EMPTY_ATTR_VALUE_XML = """
                <rules >
                    < activity log = "true" block = "true" >
                        < component-class-filter startsWith = "" / >
                        < action equals = "ACTION_BLOCKED" / >
                    < / activity >
                < / rules >
            """;

    private static final String TWO_ATTRS_XML = """
                <rules >
                    < activity log = "true" block = "true" >
                        <component-class-filter startsWith = "com.test.StartsWith" endsWith = "EndsWithClass" />
                        < action equals = "ACTION_BLOCKED" / >
                    < / activity >
                < / rules >
            """;

    private static final String INVALID_ATTR_NAME_XML = """
                <rules >
                    < activity log = "true" block = "true" >
                        <component-class-filter invalid-name = "com.test.SomeClass" />
                        < action equals = "ACTION_BLOCKED" / >
                    < / activity >
                < / rules >
            """;

    private final Context mContext = InstrumentationRegistry.getTargetContext();
    private final File mIfwDir = new File(mContext.getFilesDir(), "ifw");

    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Mock
    private PackageManagerInternal mPackageManagerInternal;

    @Before
    public void setUp() {
        LocalServices.removeServiceForTest(PackageManagerInternal.class);
        LocalServices.addService(PackageManagerInternal.class, mPackageManagerInternal);

        when(mPackageManagerInternal.snapshot()).thenReturn(null);
    }

    @After
    public void tearDown() {
        LocalServices.removeServiceForTest(PackageManagerInternal.class);
    }

    private synchronized Handler createHandler() {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        return new Handler(Looper.myLooper());
    }

    private void copyAssetToIfwFolder(String assetFileName, File ifwFolder) throws IOException {
        String assetPath = "IfwTest/component-class-filter/" + assetFileName;
        String xmlContent = DpmTestUtils.readAsset(mContext, assetPath);
        File ifwFile = new File(ifwFolder, "ifw.xml");
        DpmTestUtils.writeToFile(ifwFile, xmlContent);
    }

    private static void parseRuleXml(String xml) throws XmlPullParserException, IOException {
        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setInput(new StringReader(xml));
        IntentFirewall.Rule rule = new IntentFirewall.Rule();
        rule.setRuleType(0); // Activity type
        rule.readFromXml(parser);
    }

    @Test(expected = XmlPullParserException.class)
    public void testParsingEmptyAttributeValue() throws XmlPullParserException, IOException {
        parseRuleXml(EMPTY_ATTR_VALUE_XML);
    }

    @Test(expected = XmlPullParserException.class)
    public void testParsingTwoAttributes() throws XmlPullParserException, IOException {
        parseRuleXml(TWO_ATTRS_XML);
    }

    @Test(expected = XmlPullParserException.class)
    public void testParsingInvalidAttributeName() throws XmlPullParserException, IOException {
        parseRuleXml(INVALID_ATTR_NAME_XML);
    }

    @Test
    public void testEqualsComponentClassFilter() throws IOException {
        File ifwFolder = new File(mIfwDir, "equals");
        copyAssetToIfwFolder("ifw-equals.xml", ifwFolder);
        IntentFirewall ifw = new IntentFirewall(new MockAMSInterface(), createHandler(), ifwFolder);
        Intent intent = new Intent(ACTION_BLOCKED).setClassName(TEST_PACKAGE_NAME,
                "com.test.ExactMatchClass");
        boolean passed = ifw.checkStartActivity(intent, 0, 0, null, new ApplicationInfo());
        assertFalse("Exact matching component class should be blocked.", passed);

        intent = new Intent(ACTION_BLOCKED).setClassName(TEST_PACKAGE_NAME,
                "com.test.NotExactMatchClass");
        passed = ifw.checkStartActivity(intent, 0, 0, null, new ApplicationInfo());
        assertTrue("Not Exact matching component class should be passed.", passed);
    }

    @Test
    public void testStartsWithComponentClassFilter() throws IOException {
        File ifwFolder = new File(mIfwDir, "starts-with");
        copyAssetToIfwFolder("ifw-starts-with.xml", ifwFolder);
        IntentFirewall ifw = new IntentFirewall(new MockAMSInterface(), createHandler(), ifwFolder);

        Intent intent = new Intent(ACTION_BLOCKED).setClassName(TEST_PACKAGE_NAME,
                "com.test.StartsWithSomethingClass");
        boolean passed = ifw.checkStartActivity(intent, 0, 0, null, new ApplicationInfo());
        assertFalse("Starts with component class should be blocked.", passed);

        intent = new Intent(ACTION_BLOCKED).setClassName(TEST_PACKAGE_NAME,
                "com.test.NotStartsWithSomethingClass");
        passed = ifw.checkStartActivity(intent, 0, 0, null, new ApplicationInfo());
        assertTrue("Not start with component class should be passed.", passed);
    }

    @Test
    public void testEndsWithComponentClassFilter() throws IOException {
        File ifwFolder = new File(mIfwDir, "ends-with");
        copyAssetToIfwFolder("ifw-ends-with.xml", ifwFolder);
        IntentFirewall ifw = new IntentFirewall(new MockAMSInterface(), createHandler(), ifwFolder);

        Intent intent = new Intent(ACTION_BLOCKED).setClassName(TEST_PACKAGE_NAME,
                "com.test.SomethingEndsWithClass");
        boolean passed = ifw.checkStartActivity(intent, 0, 0, null, new ApplicationInfo());
        assertFalse("Ends with component class should be blocked.", passed);

        intent = new Intent(ACTION_BLOCKED).setClassName(TEST_PACKAGE_NAME,
                "com.test.SomethingEndsWithNotClass");
        passed = ifw.checkStartActivity(intent, 0, 0, null, new ApplicationInfo());
        assertTrue("Not ends with component class should be passed.", passed);
    }

    @Test
    public void testPatternComponentClassFilter() throws IOException {
        File ifwFolder = new File(mIfwDir, "pattern");
        copyAssetToIfwFolder("ifw-pattern.xml", ifwFolder);
        IntentFirewall ifw = new IntentFirewall(new MockAMSInterface(), createHandler(), ifwFolder);

        Intent intent = new Intent(ACTION_BLOCKED).setClassName(TEST_PACKAGE_NAME,
                "com.test.SomethingContainsClass");
        boolean passed = ifw.checkStartActivity(intent, 0, 0, null, new ApplicationInfo());
        assertFalse("Component class that matches pattern should be blocked.", passed);

        intent = new Intent(ACTION_BLOCKED).setClassName(TEST_PACKAGE_NAME,
                "com.test.SomethingClass");
        passed = ifw.checkStartActivity(intent, 0, 0, null, new ApplicationInfo());
        assertTrue("Component class that does not match pattern should be passed.", passed);
    }

    @Test
    public void testAdvancedPatternComponentClassFilter() throws IOException {
        File ifwFolder = new File(mIfwDir, "advanced-pattern");
        copyAssetToIfwFolder("ifw-advanced-pattern.xml", ifwFolder);
        IntentFirewall ifw = new IntentFirewall(new MockAMSInterface(), createHandler(), ifwFolder);

        Intent intent = new Intent(ACTION_BLOCKED).setClassName(TEST_PACKAGE_NAME,
                "com.test.ASomethingClass");
        boolean passed = ifw.checkStartActivity(intent, 0, 0, null, new ApplicationInfo());
        assertFalse("Component class that matches advanced pattern should be blocked.", passed);

        intent = new Intent(ACTION_BLOCKED).setClassName(TEST_PACKAGE_NAME,
                "com.test.BSomethingClass");
        passed = ifw.checkStartActivity(intent, 0, 0, null, new ApplicationInfo());
        assertFalse("Component class that matches advanced pattern should be blocked.", passed);

        intent = new Intent(ACTION_BLOCKED).setClassName(TEST_PACKAGE_NAME,
                "com.test.CSomethingClass");
        passed = ifw.checkStartActivity(intent, 0, 0, null, new ApplicationInfo());
        assertFalse("Component class that matches advanced pattern should be blocked.", passed);

        intent = new Intent(ACTION_BLOCKED).setClassName(TEST_PACKAGE_NAME,
                "com.test.DSomethingClass");
        passed = ifw.checkStartActivity(intent, 0, 0, null, new ApplicationInfo());
        assertTrue("PComponent class that does not match advanced pattern should be passed.",
                passed);
    }

    @Test
    public void testAdvancedPatternWithErrorComponentClassFilter() throws IOException {
        File ifwFolder = new File(mIfwDir, "advanced-pattern-error");
        copyAssetToIfwFolder("ifw-advanced-pattern-error.xml", ifwFolder);
        IntentFirewall ifw = new IntentFirewall(new MockAMSInterface(), createHandler(), ifwFolder);

        Intent intent = new Intent(ACTION_BLOCKED).setClassName(TEST_PACKAGE_NAME,
                "com.test.ASomethingClass");
        boolean passed = ifw.checkStartActivity(intent, 0, 0, null, new ApplicationInfo());
        assertTrue(
                "Component class filter with invalid advanced pattern should not be able to block"
                        + " anything.",
                passed);
    }

    @Test
    public void testMultipleComponentClassFilter() throws IOException {
        File ifwFolder = new File(mIfwDir, "multiple");
        copyAssetToIfwFolder("ifw-multiple-filters.xml", ifwFolder);
        IntentFirewall ifw = new IntentFirewall(new MockAMSInterface(), createHandler(), ifwFolder);

        Intent intent = new Intent(ACTION_BLOCKED).setClassName(TEST_PACKAGE_NAME,
                "com.test.ExactMatchClass");
        boolean passed = ifw.checkStartActivity(intent, 0, 0, null, new ApplicationInfo());
        assertFalse("Exact matching component class should be blocked.", passed);

        intent = new Intent(ACTION_BLOCKED).setClassName(TEST_PACKAGE_NAME,
                "com.test.StartsWithSomethingClass");
        passed = ifw.checkStartActivity(intent, 0, 0, null, new ApplicationInfo());
        assertFalse("Starts with component class should be blocked.", passed);

        intent = new Intent(ACTION_BLOCKED).setClassName(TEST_PACKAGE_NAME,
                "com.test.SomethingEndsWithClass");
        passed = ifw.checkStartActivity(intent, 0, 0, null, new ApplicationInfo());
        assertFalse("Ends with component class should be blocked.", passed);

        intent = new Intent(ACTION_BLOCKED).setClassName(TEST_PACKAGE_NAME,
                "com.test.SomethingRandomClass");
        passed = ifw.checkStartActivity(intent, 0, 0, null, new ApplicationInfo());
        assertTrue("Other component class should be passed.", passed);
    }

    @Test
    public void testServiceBroadcastNotEffective() throws IOException {
        File ifwFolder = new File(mIfwDir, "service-broadcast");
        copyAssetToIfwFolder("ifw-service-broadcast.xml", ifwFolder);
        IntentFirewall ifw = new IntentFirewall(new MockAMSInterface(), createHandler(), ifwFolder);

        Intent intent = new Intent(ACTION_BLOCKED).setClassName(TEST_PACKAGE_NAME,
                "com.test.StartsWithSomethingClass");
        boolean passed = ifw.checkService(
                new ComponentName(TEST_PACKAGE_NAME, "com.test.StartsWithSomethingClass"), intent,
                0, 0, null, new ApplicationInfo());
        assertTrue("Starts with component class should be blocked.", passed);

        /*intent = new Intent(ACTION_BLOCKED).setClassName(TEST_PACKAGE_NAME,
                "com.test.NotStartsWithSomethingClass");*/
        passed = ifw.checkBroadcast(intent, 0, 0, null, 0);
        assertTrue("Not start with component class should be passed.", passed);
    }

    private static class MockAMSInterface implements IntentFirewall.AMSInterface {

        @Override
        public int checkComponentPermission(String permission, int pid, int uid, int owningUid,
                boolean exported) {
            return 0; // permission granted.
        }

        @Override
        public Object getAMSLock() {
            return MockAMSInterface.this;
        }
    }
}
