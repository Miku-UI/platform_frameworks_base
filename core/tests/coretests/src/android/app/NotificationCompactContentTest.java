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

package android.app;

import static com.google.common.truth.Truth.assertThat;

import android.app.Notification.BasicCompactContent;
import android.app.Notification.CompactIcon;
import android.app.Notification.CompactText;
import android.app.Notification.Metric.FixedText;
import android.app.Notification.Metric.MetricValue;
import android.app.Notification.ResolvedBasicCompactContent;
import android.app.Notification.ResolvedCompactContent;
import android.content.Context;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@Presubmit
@EnableFlags(Flags.FLAG_API_METRIC_STYLE)
public class NotificationCompactContentTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getContext();

    @Test
    @DisableFlags(Flags.FLAG_API_NOTIFICATION_CHIP)
    public void resolveCompactContent_withFlagOff_doesNotUseCompactContent() {

        Notification n = new Notification.Builder(mContext, "channel")
                .setShortCriticalText("should use this")
                .setCompactContent(
                        new BasicCompactContent(
                                CompactIcon.auto(),
                                CompactText.fromMetricValue(new FixedText("and not this"))))
                .build();
        ResolvedCompactContent resolved = n.resolveCompactContent(mContext);

        assertThat(resolved).isInstanceOf(ResolvedBasicCompactContent.class);
        MetricValue resolvedText = ((ResolvedBasicCompactContent) resolved).getText();
        assertThat(resolvedText).isInstanceOf(FixedText.class);
        assertThat(((FixedText) resolvedText).getValue().toString()).isEqualTo("should use this");
    }

    @Test
    @EnableFlags(Flags.FLAG_API_NOTIFICATION_CHIP)
    public void resolveCompactContent_withFlagOn_usesCompactContent() {

        Notification n = new Notification.Builder(mContext, "channel")
                .setCompactContent(
                        new BasicCompactContent(
                                CompactIcon.auto(),
                                CompactText.fromMetricValue(new FixedText("should use this"))))
                .setShortCriticalText("and not this")
                .build();
        ResolvedCompactContent resolved = n.resolveCompactContent(mContext);

        assertThat(resolved).isInstanceOf(ResolvedBasicCompactContent.class);
        MetricValue resolvedText = ((ResolvedBasicCompactContent) resolved).getText();
        assertThat(resolvedText).isInstanceOf(FixedText.class);
        assertThat(((FixedText) resolvedText).getValue().toString()).isEqualTo("should use this");
    }

    @Test
    @EnableFlags(Flags.FLAG_API_NOTIFICATION_CHIP)
    public void resolveCompactContent_trashInBasicCompactContent_noCrashAndEmptyChip()
            throws Exception {
        CompactIcon trashyIcon = CompactIcon.useSmallIcon();
        java.lang.reflect.Field field = trashyIcon.getClass().getDeclaredField("mWhich");
        field.setAccessible(true);
        field.setInt(trashyIcon, 3);

        Notification n = new Notification.Builder(mContext, "channel")
                .setWhen(System.currentTimeMillis())
                .setShortCriticalText("fallback")
                .setCompactContent(
                        new BasicCompactContent(trashyIcon, CompactText.useWhenAsTimeRemaining()))
                .build();
        ResolvedCompactContent resolved = n.resolveCompactContent(mContext);

        // No crash, and using short critical text although trashy had asked for "when".
        assertThat(resolved).isInstanceOf(ResolvedBasicCompactContent.class);
        MetricValue resolvedText = ((ResolvedBasicCompactContent) resolved).getText();
        assertThat(resolvedText).isInstanceOf(FixedText.class);
        assertThat(((FixedText) resolvedText).getValue().toString()).isEqualTo("fallback");
    }
}
