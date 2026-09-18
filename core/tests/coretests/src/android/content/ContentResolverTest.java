/*
 * Copyright (C) 2010 The Android Open Source Project
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

package android.content;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.net.Uri;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@Presubmit
@RunWith(AndroidJUnit4.class)
public class ContentResolverTest {

    private ContentResolver mResolver;

    @Before
    public void setUp() throws Exception {
        mResolver = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getContentResolver();
    }

    @Test
    public void testGetType_localProvider() {
        // This provider is running in the same process as the test and is already registered with
        // the ContentResolver when the application starts, see
        // ActivityThread#installContentProviders. This allows ContentResolver to follow a
        // streamlined code path.
        String type = mResolver.getType(Uri.parse("content://android.content.FakeProviderLocal"));
        assertEquals("fake/local", type);
    }

    @Test
    public void testGetType_remoteProvider() {
        // This provider is running in a different process, which will need to be started
        // in order to acquire the provider
        String type = mResolver.getType(Uri.parse("content://android.content.FakeProviderRemote"));
        assertEquals("fake/remote", type);
    }

    @Test
    public void testGetType_slowProvider() {
        // This provider is running in a different process and is intentionally slow to start.
        // We are trying to confirm that it does not cause an ANR
        long start = SystemClock.uptimeMillis();
        String type = mResolver.getType(Uri.parse("content://android.content.SlowProvider"));
        long end = SystemClock.uptimeMillis();
        assertEquals("slow", type);
        assertThat(end).isLessThan(start + 5000);
    }

    @Test
    public void testGetType_unknownProvider() {
        // This provider does not exist.
        // We are trying to confirm that getType returns null and does not cause an ANR
        long start = SystemClock.uptimeMillis();
        String type = mResolver.getType(Uri.parse("content://android.content.NonexistentProvider"));
        long end = SystemClock.uptimeMillis();
        assertThat(type).isNull();
        assertThat(end).isLessThan(start + 5000);
    }

    @Test
    public void testGetType_providerException() {
        String type =
                mResolver.getType(Uri.parse("content://android.content.FakeProviderRemote/error"));
        assertThat(type).isNull();
    }

    @Test
    public void testCanonicalize() {
        Uri canonical = mResolver.canonicalize(
                Uri.parse("content://android.content.FakeProviderRemote/something"));
        assertThat(canonical).isEqualTo(
                Uri.parse("content://android.content.FakeProviderRemote/canonical"));
    }

    @Test
    public void testCanonicalize_providerException() {
        try {
            mResolver.canonicalize(
                    Uri.parse("content://android.content.FakeProviderRemote/error"));
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testUncanonicalize() {
        Uri uncanonical = mResolver.uncanonicalize(
                Uri.parse("content://android.content.FakeProviderRemote/something"));
        assertThat(uncanonical).isEqualTo(
                Uri.parse("content://android.content.FakeProviderRemote/uncanonical"));
    }
}
