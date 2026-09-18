/*
 * Copyright (C) 2026 The Android Open Source Project
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

package android.service.dreams;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.os.Parcel;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SmallTest
@Presubmit
@RunWith(AndroidJUnit4.class)
public class DreamPlaylistTest {

    private static final ComponentName DREAM_1 =
            ComponentName.unflattenFromString("com.android.dream/.Dream1");
    private static final ComponentName DREAM_2 =
            ComponentName.unflattenFromString("com.android.dream/.Dream2");
    private static final ComponentName DREAM_3 =
            ComponentName.unflattenFromString("com.android.dream/.Dream3");

    @Test(expected = NullPointerException.class)
    public void testConstructor_nullElement() {
        List<DreamItem> dreams = Arrays.asList(createDreamItem(DREAM_1), null,
                createDreamItem(DREAM_3));
        new DreamPlaylist(dreams, 0);
    }

    @Test
    public void testEmptyConstant() {
        assertThat(DreamPlaylist.EMPTY.getDreams()).isEmpty();
        assertThat(DreamPlaylist.EMPTY.getActiveIndex())
                .isEqualTo(DreamPlaylist.NO_ACTIVE_DREAM_INDEX);
        assertThat(DreamPlaylist.EMPTY.getActiveDream()).isNull();
    }

    @Test
    public void testGetActiveDream() {
        List<DreamItem> dreams = Arrays.asList(createDreamItem(DREAM_1), createDreamItem(DREAM_2),
                createDreamItem(DREAM_3));
        DreamPlaylist playlist = new DreamPlaylist(dreams, 1);
        assertThat(playlist.getActiveDream().componentName).isEqualTo(DREAM_2);
        assertThat(playlist.getActiveIndex()).isEqualTo(1);
    }

    @Test
    public void testGetActiveDream_noneActive() {
        List<DreamItem> dreams = Arrays.asList(createDreamItem(DREAM_1), createDreamItem(DREAM_2));
        DreamPlaylist playlist = new DreamPlaylist(dreams, DreamPlaylist.NO_ACTIVE_DREAM_INDEX);
        assertThat(playlist.getActiveDream()).isNull();
        assertThat(playlist.getActiveIndex()).isEqualTo(DreamPlaylist.NO_ACTIVE_DREAM_INDEX);
    }

    @Test
    public void testGetActiveDream_empty() {
        DreamPlaylist playlist =
                new DreamPlaylist(Collections.emptyList(), DreamPlaylist.NO_ACTIVE_DREAM_INDEX);
        assertThat(playlist.getActiveDream()).isNull();
        assertThat(playlist.getActiveIndex()).isEqualTo(DreamPlaylist.NO_ACTIVE_DREAM_INDEX);
    }

    @Test
    public void testGetNextDream() {
        List<DreamItem> dreams = Arrays.asList(createDreamItem(DREAM_1), createDreamItem(DREAM_2),
                createDreamItem(DREAM_3));

        // Active is DREAM_1
        DreamPlaylist playlist = new DreamPlaylist(dreams, 0);
        assertThat(playlist.getNextDream().componentName).isEqualTo(DREAM_2);

        // Active is DREAM_2
        playlist = new DreamPlaylist(dreams, 1);
        assertThat(playlist.getNextDream().componentName).isEqualTo(DREAM_3);

        // Active is DREAM_3 (last)
        playlist = new DreamPlaylist(dreams, 2);
        assertThat(playlist.getNextDream().componentName).isEqualTo(DREAM_1);
    }

    @Test
    public void testGetNextDream_empty() {
        DreamPlaylist playlist =
                new DreamPlaylist(Collections.emptyList(), DreamPlaylist.NO_ACTIVE_DREAM_INDEX);
        assertThat(playlist.getNextDream()).isNull();
    }

    @Test
    public void testGetPreviousDream() {
        List<DreamItem> dreams = Arrays.asList(createDreamItem(DREAM_1), createDreamItem(DREAM_2),
                createDreamItem(DREAM_3));

        // Active is DREAM_1 (first)
        DreamPlaylist playlist = new DreamPlaylist(dreams, 0);
        assertThat(playlist.getPreviousDream().componentName).isEqualTo(DREAM_3);

        // Active is DREAM_2
        playlist = new DreamPlaylist(dreams, 1);
        assertThat(playlist.getPreviousDream().componentName).isEqualTo(DREAM_1);

        // Active is DREAM_3
        playlist = new DreamPlaylist(dreams, 2);
        assertThat(playlist.getPreviousDream().componentName).isEqualTo(DREAM_2);
    }

    @Test
    public void testGetPreviousDream_empty() {
        DreamPlaylist playlist =
                new DreamPlaylist(Collections.emptyList(), DreamPlaylist.NO_ACTIVE_DREAM_INDEX);
        assertThat(playlist.getPreviousDream()).isNull();
    }

    @Test
    public void testGetNextDream_noneActive() {
        List<DreamItem> dreams = Arrays.asList(createDreamItem(DREAM_1), createDreamItem(DREAM_2),
                createDreamItem(DREAM_3));
        // Active index -1 means no dream is currently active.
        DreamPlaylist playlist = new DreamPlaylist(dreams, DreamPlaylist.NO_ACTIVE_DREAM_INDEX);
        // If no dream is active, "next" should start at the beginning.
        assertThat(playlist.getNextDream().componentName).isEqualTo(DREAM_1);
    }

    @Test
    public void testGetPreviousDream_noneActive() {
        List<DreamItem> dreams = Arrays.asList(createDreamItem(DREAM_1), createDreamItem(DREAM_2),
                createDreamItem(DREAM_3));
        // Active index -1 means no dream is currently active.
        DreamPlaylist playlist = new DreamPlaylist(dreams, DreamPlaylist.NO_ACTIVE_DREAM_INDEX);
        // If no dream is active, "previous" should wrap to the end.
        assertThat(playlist.getPreviousDream().componentName).isEqualTo(DREAM_3);
    }

    @Test
    public void testParceling() {
        List<DreamItem> dreams = Arrays.asList(createDreamItem(DREAM_1), createDreamItem(DREAM_2));
        DreamPlaylist original = new DreamPlaylist(dreams, 1);

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        DreamPlaylist created = DreamPlaylist.CREATOR.createFromParcel(parcel);

        assertThat(created.getDreams()).containsExactlyElementsIn(dreams).inOrder();
        assertThat(created.getActiveIndex()).isEqualTo(1);
        assertThat(created.getActiveDream().componentName).isEqualTo(DREAM_2);

        parcel.recycle();
    }

    @Test
    public void testEquals() {
        List<DreamItem> dreams = Arrays.asList(createDreamItem(DREAM_1), createDreamItem(DREAM_2));
        DreamPlaylist playlist1 = new DreamPlaylist(dreams, 1);
        DreamPlaylist playlist2 = new DreamPlaylist(dreams, 1);

        assertThat(playlist1).isEqualTo(playlist2);
        assertThat(playlist1.hashCode()).isEqualTo(playlist2.hashCode());
    }

    @Test
    public void testNotEquals_differentIndex() {
        List<DreamItem> dreams = Arrays.asList(createDreamItem(DREAM_1), createDreamItem(DREAM_2));
        DreamPlaylist playlist1 = new DreamPlaylist(dreams, 0);
        DreamPlaylist playlist2 = new DreamPlaylist(dreams, 1);

        assertThat(playlist1).isNotEqualTo(playlist2);
    }

    @Test
    public void testNotEquals_differentDreams() {
        List<DreamItem> dreams1 = Arrays.asList(createDreamItem(DREAM_1), createDreamItem(DREAM_2));
        List<DreamItem> dreams2 = Arrays.asList(createDreamItem(DREAM_1), createDreamItem(DREAM_3));
        DreamPlaylist playlist1 = new DreamPlaylist(dreams1, 0);
        DreamPlaylist playlist2 = new DreamPlaylist(dreams2, 0);

        assertThat(playlist1).isNotEqualTo(playlist2);
    }

    @Test
    public void testNotEquals_differentOrder() {
        List<DreamItem> dreams1 = Arrays.asList(createDreamItem(DREAM_1), createDreamItem(DREAM_2));
        List<DreamItem> dreams2 = Arrays.asList(createDreamItem(DREAM_2), createDreamItem(DREAM_1));
        DreamPlaylist playlist1 = new DreamPlaylist(dreams1, 0);
        DreamPlaylist playlist2 = new DreamPlaylist(dreams2, 0);

        assertThat(playlist1).isNotEqualTo(playlist2);
    }

    @Test
    public void testNotEquals_null() {
        List<DreamItem> dreams = Arrays.asList(createDreamItem(DREAM_1), createDreamItem(DREAM_2));
        DreamPlaylist playlist = new DreamPlaylist(dreams, 0);

        assertThat(playlist).isNotEqualTo(null);
    }

    @Test
    public void testNotEquals_differentType() {
        List<DreamItem> dreams = Arrays.asList(createDreamItem(DREAM_1), createDreamItem(DREAM_2));
        DreamPlaylist playlist = new DreamPlaylist(dreams, 0);

        assertThat(playlist).isNotEqualTo("Not a DreamPlaylist object");
    }

    @Test
    public void testToString() {
        List<DreamItem> dreams = Arrays.asList(createDreamItem(DREAM_1), createDreamItem(DREAM_2));
        DreamPlaylist playlist = new DreamPlaylist(dreams, 1);
        String expected =
                "DreamPlaylist{mDreams=["
                        + createDreamItem(DREAM_1).toString()
                        + ", "
                        + createDreamItem(DREAM_2).toString()
                        + "], mActiveIndex=1}";
        assertThat(playlist.toString()).isEqualTo(expected);
    }

    @Test
    public void testContains() {
        List<DreamItem> dreams = Arrays.asList(createDreamItem(DREAM_1), createDreamItem(DREAM_2));
        DreamPlaylist playlist = new DreamPlaylist(dreams, 1);
        assertThat(playlist.contains(DREAM_1)).isTrue();
        assertThat(playlist.contains(DREAM_2)).isTrue();
        assertThat(playlist.contains(DREAM_3)).isFalse();
        assertThat(playlist.contains(null)).isFalse();
    }

    private DreamItem createDreamItem(ComponentName componentName) {
        return new DreamItem.Builder(componentName).build();
    }
}
