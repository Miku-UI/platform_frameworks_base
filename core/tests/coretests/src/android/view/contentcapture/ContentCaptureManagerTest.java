/*
 * Copyright (C) 2019 The Android Open Source Project
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
package android.view.contentcapture;

import static android.view.contentcapture.ContentCaptureEvent.TYPE_SESSION_STARTED;

import static com.google.common.truth.Truth.assertThat;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;
import static org.testng.Assert.assertThrows;

import android.annotation.SuppressLint;
import android.content.ContentCaptureOptions;
import android.content.Context;
import android.os.DeadObjectException;
import android.os.DeadSystemRuntimeException;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.WindowManager;

import com.android.internal.util.RingBuffer;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Unit test for {@link ContentCaptureManager}.
 *
 * <p>To run it:
 * {@code atest FrameworksCoreTests:android.view.contentcapture.ContentCaptureManagerTest}
 */
@RunWith(MockitoJUnitRunner.class)
@SuppressLint("VisibleForTests") // Suppress "method should only be accessed from tests"
public class ContentCaptureManagerTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final int BUFFER_SIZE = 100;

    private static final String PACKAGE1 = "com.test.package.1";

    private static final String PACKAGE2 = "com.test.package.2";

    private static final ContentCaptureOptions EMPTY_OPTIONS = new ContentCaptureOptions(null);

    @Mock
    private Context mMockContext;

    @Mock private IContentCaptureManager mMockContentCaptureManager;

    @Test
    public void testConstructor_invalidParametersThrowsException() {
        assertThrows(NullPointerException.class,
                () -> new ContentCaptureManager(mMockContext, /* service= */ null, /* options= */
                        null));
    }

    @Test
    public void testConstructor_contentProtection_default_bufferNotCreated() {
        ContentCaptureManager manager =
                new ContentCaptureManager(mMockContext, mMockContentCaptureManager, EMPTY_OPTIONS);

        assertThat(manager.getContentProtectionEventBuffer()).isNull();
    }

    @Test
    public void testConstructor_contentProtection_disabled_bufferNotCreated() {
        ContentCaptureOptions options =
                createOptions(
                        new ContentCaptureOptions.ContentProtectionOptions(
                                /* enableReceiver= */ false,
                                BUFFER_SIZE,
                                /* requiredGroups= */ Collections.emptyList(),
                                /* optionalGroups= */ Collections.emptyList(),
                                /* optionalGroupsThreshold= */ 0));

        ContentCaptureManager manager =
                new ContentCaptureManager(mMockContext, mMockContentCaptureManager, options);

        assertThat(manager.getContentProtectionEventBuffer()).isNull();
    }

    @Test
    public void testConstructor_contentProtection_invalidBufferSize_bufferNotCreated() {
        ContentCaptureOptions options =
                createOptions(
                        new ContentCaptureOptions.ContentProtectionOptions(
                                /* enableReceiver= */ true,
                                /* bufferSize= */ 0,
                                /* requiredGroups= */ Collections.emptyList(),
                                /* optionalGroups= */ Collections.emptyList(),
                                /* optionalGroupsThreshold= */ 0));

        ContentCaptureManager manager =
                new ContentCaptureManager(mMockContext, mMockContentCaptureManager, options);

        assertThat(manager.getContentProtectionEventBuffer()).isNull();
    }

    @Test
    public void testConstructor_contentProtection_enabled_bufferCreated() {
        ContentCaptureOptions options =
                createOptions(
                        new ContentCaptureOptions.ContentProtectionOptions(
                                /* enableReceiver= */ true,
                                BUFFER_SIZE,
                                /* requiredGroups= */ Collections.emptyList(),
                                /* optionalGroups= */ Collections.emptyList(),
                                /* optionalGroupsThreshold= */ 0));

        ContentCaptureManager manager =
                new ContentCaptureManager(mMockContext, mMockContentCaptureManager, options);
        RingBuffer<ContentCaptureEvent> buffer = manager.getContentProtectionEventBuffer();

        assertThat(buffer).isNotNull();
        ContentCaptureEvent[] expected = new ContentCaptureEvent[BUFFER_SIZE];
        int offset = 3;
        for (int i = 0; i < BUFFER_SIZE + offset; i++) {
            ContentCaptureEvent event = new ContentCaptureEvent(i, TYPE_SESSION_STARTED);
            buffer.append(event);
            expected[(i + BUFFER_SIZE - offset) % BUFFER_SIZE] = event;
        }
        assertThat(buffer.toArray()).isEqualTo(expected);
    }

    @Test
    public void testRemoveData_invalidParametersThrowsException() {
        final ContentCaptureManager manager =
                new ContentCaptureManager(mMockContext, mMockContentCaptureManager, EMPTY_OPTIONS);

        assertThrows(NullPointerException.class, () -> manager.removeData(null));
    }

    @Test
    public void testUpdateWindowAttribute_setFlagSecure() {
        final ContentCaptureManager manager =
                new ContentCaptureManager(mMockContext, mMockContentCaptureManager, EMPTY_OPTIONS);
        // Ensure main session is created.
        final ContentCaptureSession unused = manager.getMainContentCaptureSession();
        final WindowManager.LayoutParams initialParam = new WindowManager.LayoutParams();
        initialParam.flags |= WindowManager.LayoutParams.FLAG_SECURE;

        manager.updateWindowAttributes(initialParam);

        assertThat(manager.isContentCaptureEnabled()).isFalse();
    }

    @Test
    public void testUpdateWindowAttribute_clearFlagSecure() {
        final ContentCaptureManager manager =
                new ContentCaptureManager(mMockContext, mMockContentCaptureManager, EMPTY_OPTIONS);
        // Ensure main session is created.
        final ContentCaptureSession unused = manager.getMainContentCaptureSession();
        final WindowManager.LayoutParams initialParam = new WindowManager.LayoutParams();
        initialParam.flags |= WindowManager.LayoutParams.FLAG_SECURE;
        // Default param does not have FLAG_SECURE set.
        final WindowManager.LayoutParams resetParam = new WindowManager.LayoutParams();

        manager.updateWindowAttributes(initialParam);
        manager.updateWindowAttributes(resetParam);

        assertThat(manager.isContentCaptureEnabled()).isTrue();
    }

    @Test
    @RequiresFlagsDisabled(
        "android.view.contentcapture.flags.deprecate_set_content_capture_enabled")
    public void testUpdateWindowAttribute_clearFlagSecureAfterDisabledByApp() {
        final ContentCaptureManager manager =
                new ContentCaptureManager(mMockContext, mMockContentCaptureManager, EMPTY_OPTIONS);
        // Ensure main session is created.
        final ContentCaptureSession unused = manager.getMainContentCaptureSession();
        // Default param does not have FLAG_SECURE set.
        final WindowManager.LayoutParams resetParam = new WindowManager.LayoutParams();

        manager.setContentCaptureEnabled(false);
        manager.updateWindowAttributes(resetParam);

        assertThat(manager.isContentCaptureEnabled()).isFalse();
    }

    @Test
    @SuppressLint("MissingPermission") // Unit test
    public void testSetContentProtectionAllowlist_invalidParametersThrowsException() {
        ContentCaptureManager manager =
                new ContentCaptureManager(mMockContext, mMockContentCaptureManager, EMPTY_OPTIONS);

        assertThrows(NullPointerException.class, () -> manager.setContentProtectionAllowlist(null));
    }

    @Test
    @SuppressLint("MissingPermission") // Unit test
    public void testSetContentProtectionAllowlist_systemServerThrowsException() throws Exception {
        doThrow(new DeadObjectException())
                .when(mMockContentCaptureManager).setContentProtectionAllowlist(List.of(PACKAGE1));
        ContentCaptureManager manager =
                new ContentCaptureManager(mMockContext, mMockContentCaptureManager, EMPTY_OPTIONS);

        assertThrows(DeadSystemRuntimeException.class,
                () -> manager.setContentProtectionAllowlist(Set.of(PACKAGE1)));
    }

    @Test
    @SuppressLint("MissingPermission") // Unit test
    public void testSetContentProtectionAllowlist_empty() throws Exception {
        ContentCaptureManager manager =
                new ContentCaptureManager(mMockContext, mMockContentCaptureManager, EMPTY_OPTIONS);

        manager.setContentProtectionAllowlist(Set.of());

        verify(mMockContentCaptureManager).setContentProtectionAllowlist(List.of());
        verifyNoMoreInteractions(mMockContentCaptureManager);
    }

    @Test
    @SuppressLint("MissingPermission") // Unit test
    public void testSetContentProtectionAllowlist_notEmpty() throws Exception {
        ContentCaptureManager manager =
                new ContentCaptureManager(mMockContext, mMockContentCaptureManager, EMPTY_OPTIONS);

        manager.setContentProtectionAllowlist(Set.of(PACKAGE1, PACKAGE2));

        verify(mMockContentCaptureManager).setContentProtectionAllowlist(
                (List<String>) argThat(containsInAnyOrder(PACKAGE1, PACKAGE2)));
        verifyNoMoreInteractions(mMockContentCaptureManager);
    }

    private ContentCaptureOptions createOptions(
            ContentCaptureOptions.ContentProtectionOptions contentProtectionOptions) {
        return new ContentCaptureOptions(
                /* loggingLevel= */ 0,
                /* maxBufferSize= */ 0,
                /* idleFlushingFrequencyMs= */ 0,
                /* textChangeFlushingFrequencyMs= */ 0,
                /* logHistorySize= */ 0,
                /* enableReceiver= */ true,
                contentProtectionOptions,
                /* whitelistedComponents= */ null);
    }
}
