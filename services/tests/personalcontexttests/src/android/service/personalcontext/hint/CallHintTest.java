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

package android.service.personalcontext.hint;

import static android.service.personalcontext.hint.ContextHintTestUtils.assertParcelUnparcel;

import static com.google.common.truth.Truth.assertThat;

import android.app.Person;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

@SmallTest
@RunWith(AndroidJUnit4.class)public class CallHintTest {
    @Test
    public void testCallHint_participants() {
        final String address = "tel:123-456-7890";
        final Set<Person> participants =
                Set.of(new Person.Builder().setName("Tom Brown").setUri(address).build());
        final CallHint hint = new CallHint.Builder(CallHint.MODALITY_AUDIO, participants).build();

        final CallHint outputHint = (CallHint) assertParcelUnparcel(hint);
        assertThat(outputHint).isEqualTo(hint);

        assertThat(outputHint.getParticipants()).isEqualTo(participants);

        final Person participant = outputHint.getParticipants().stream().findFirst().get();
        assertThat(participant.getUri()).isEqualTo(address);
    }

    @Test
    public void testCallHint_modality() {
        final int modality = CallHint.MODALITY_VIDEO;
        final Set<Person> participants = Set.of(new Person.Builder().setName("Tom Brown").build());
        final CallHint hint = new CallHint.Builder(modality, participants).build();

        final CallHint outputHint = (CallHint) assertParcelUnparcel(hint);
        assertThat(outputHint).isEqualTo(hint);

        assertThat(outputHint.getModality()).isEqualTo(modality);
    }
}
