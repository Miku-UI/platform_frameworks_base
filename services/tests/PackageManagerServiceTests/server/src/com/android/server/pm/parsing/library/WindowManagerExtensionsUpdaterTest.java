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

package com.android.server.pm.parsing.library;

import static android.content.pm.ActivityInfo.OVERRIDE_ENABLE_VIRTUAL_GAMEPAD;

import static com.android.server.pm.parsing.library.WindowManagerExtensionsUpdater.LIBRARY_NAME;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.compat.CompatChanges;
import android.app.compat.PackageOverride;
import android.content.pm.PackageManager;
import android.platform.test.annotations.Presubmit;
import android.view.WindowManager;

import androidx.test.filters.SmallTest;

import com.android.internal.pm.parsing.pkg.AndroidPackageInternal;
import com.android.internal.pm.parsing.pkg.PackageImpl;
import com.android.internal.pm.parsing.pkg.ParsedPackage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collections;

/**
 * Test for {@link WindowManagerExtensionsUpdater}
 *
 * Build/Install/Run:
 *  atest PackageManagerServiceServerTests:WindowManagerExtensionsUpdaterTest
 */
@Presubmit
@SmallTest
@RunWith(JUnit4.class)
public class WindowManagerExtensionsUpdaterTest extends PackageSharedLibraryUpdaterTest {
    private ParsedPackage mPackage;

    @Before
    public void setup() {
        mPackage = PackageImpl.forTesting(PACKAGE_NAME).hideAsParsed();
    }

    @After
    public void tearDown() {
        // Clear app compat overrides
        CompatChanges.putPackageOverrides(PACKAGE_NAME, Collections.emptyMap());
    }

    @Test
    public void testUpdatePackage_enabled() {
        assumeTrue(WindowManager.HAS_WINDOW_EXTENSIONS_ON_DEVICE);
        setAppCompatFlag(true /* enabled */);

        // Library added when flags are enabled.
        assertExtensionsAdded(mPackage);
    }

    @Test
    public void testUpdatePackage_compatFlagDisabled() {
        assumeTrue(WindowManager.HAS_WINDOW_EXTENSIONS_ON_DEVICE);
        setAppCompatFlag(false /* enabled */);

        // Library not added when the compat flag is disabled.
        assertExtensionsNotAdded(mPackage);
    }

    @Test
    public void testUpdatePackage_noExtensions() {
        assumeFalse(WindowManager.HAS_WINDOW_EXTENSIONS_ON_DEVICE);
        setAppCompatFlag(true /* enabled */);

        // Library not added when there is no wm extensions.
        assertExtensionsNotAdded(mPackage);
    }

    @Test
    public void testUpdatePackage_optOutProperty() {
        assumeTrue(WindowManager.HAS_WINDOW_EXTENSIONS_ON_DEVICE);
        setAppCompatFlag(true /* enabled */);

        // Opt-out with property
        ((PackageImpl) mPackage).addProperty(
                new PackageManager.Property(
                        WindowManager.PROPERTY_COMPAT_ALLOW_VIRTUAL_GAMEPAD_OVERRIDE,
                        false /* value */, PACKAGE_NAME, null /* className */));

        // Library not added when the app opted out via property.
        assertExtensionsNotAdded(mPackage);
    }

    private static void assertExtensionsAdded(ParsedPackage pkg) {
        final AndroidPackageInternal expected = PackageImpl.forTesting(PACKAGE_NAME)
                .addUsesLibrary(LIBRARY_NAME)
                .hideAsParsed()
                .hideAsFinal();

        checkBackwardsCompatibility(pkg, expected, false /* isSystemApp */,
                WindowManagerExtensionsUpdater::new);
    }

    private static void assertExtensionsNotAdded(ParsedPackage pkg) {
        final AndroidPackageInternal expected = PackageImpl.forTesting(PACKAGE_NAME)
                .hideAsParsed()
                .hideAsFinal();

        checkBackwardsCompatibility(pkg, expected, false /* isSystemApp */,
                WindowManagerExtensionsUpdater::new);
    }

    private static void setAppCompatFlag(boolean enabled) {
        CompatChanges.putPackageOverrides(
                PACKAGE_NAME,
                Collections.singletonMap(
                        OVERRIDE_ENABLE_VIRTUAL_GAMEPAD,
                        new PackageOverride.Builder().setEnabled(enabled).build())
        );
    }
}
