/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.server.am;

import static com.android.server.am.MemoryStatUtil.MemoryStat;
import static com.android.server.am.MemoryStatUtil.PAGE_SIZE;
import static com.android.server.am.MemoryStatUtil.parseMemoryStatFromProcfs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.Collections;

/**
 * Build/Install/Run:
 *  atest FrameworksServicesTests:MemoryStatUtilTest
 */
@SmallTest
public class MemoryStatUtilTest {
    private static final String PROC_STAT_CONTENTS = String.join(
            " ",
            "1040",
            "(system_server)",
            "S",
            "544",
            "544",
            "0",
            "0",
            "-1",
            "1077936448",
            "1", // this is pgfault
            "0",
            "2", // this is pgmajfault
            "0",
            "44533",
            "13471",
            "0",
            "0",
            "18",
            "-2",
            "117",
            "0",
            "2222", // this in start time (in ticks per second)
            "1257177088",
            "3", // this is RSS in pages
            "4294967295",
            "2936971264",
            "2936991289",
            "3198888320",
            "3198879848",
            "2903927664",
            "0",
            "4612",
            "0",
            "1073775864",
            "4294967295",
            "0",
            "0",
            "17",
            "0",
            "0",
            "0",
            "0",
            "0",
            "0",
            "2936999088",
            "2936999936",
            "2958692352",
            "3198888595",
            "3198888671",
            "3198888671",
            "3198889956",
            "0");

    @Test
    public void testParseMemoryStatFromProcfs_parsesCorrectValues() {
        MemoryStat stat = parseMemoryStatFromProcfs(PROC_STAT_CONTENTS);
        assertEquals(1, stat.pgfault);
        assertEquals(2, stat.pgmajfault);
        assertEquals(3 * PAGE_SIZE, stat.rssInBytes);
        assertEquals(0, stat.cacheInBytes);
        assertEquals(0, stat.swapInBytes);
    }

    @Test
    public void testParseMemoryStatFromProcfs_emptyContents() {
        MemoryStat stat = parseMemoryStatFromProcfs("");
        assertNull(stat);

        stat = parseMemoryStatFromProcfs(null);
        assertNull(stat);
    }

    @Test
    public void testParseMemoryStatFromProcfs_invalidValue() {
        String contents = String.join(" ", Collections.nCopies(24, "memory"));
        assertNull(parseMemoryStatFromProcfs(contents));
    }
}
