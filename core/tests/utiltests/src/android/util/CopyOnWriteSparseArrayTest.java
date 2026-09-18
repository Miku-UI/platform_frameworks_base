/*
 * Copyright 2025 The Android Open Source Project
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

package android.util;

import static com.google.common.truth.Truth.assertThat;

import static java.util.concurrent.TimeUnit.SECONDS;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@RunWith(AndroidJUnit4.class)
public class CopyOnWriteSparseArrayTest {
    @Test
    public void testPutNewKey() {
        var array = init();

        // Adding key 100, which does not exist
        var prevContainer = array.getArray();
        array.put(100, "100");
        // Internal container must be copied and increased in size
        assertThat(prevContainer).isNotSameInstanceAs(array.getArray());
        assertThat(array.getArray().size()).isEqualTo(5);
        assertThat(array.get(100)).isEqualTo("100");
    }


    @Test
    public void testPutExistingKey() {
        var array = init();

        // Updating key 0. Which already exists
        assertThat(array.get(0)).isEqualTo("0");
        assertThat(array.getArray().size()).isEqualTo(4);
        var prevContainer = array.getArray();
        array.put(0, "00");
        // Internal container must NOT be copied
        assertThat(prevContainer).isSameInstanceAs(array.getArray());
        assertThat(array.getArray().size()).isEqualTo(4);
        assertThat(array.get(0)).isEqualTo("00");
    }

    @Test
    public void testGet() {
        var array = init();

        assertThat(array.get(100)).isNull();
        assertThat(array.get(0)).isEqualTo("0");
        assertThat(array.get(1)).isEqualTo("1");
        assertThat(array.get(3)).isEqualTo("3");
        assertThat(array.get(10)).isEqualTo("10");
    }

    @Test
    public void testFilterKeys() {
        var array = init();

        var filteredKeys = array.filteredKeys(
                (Integer key, String value) -> value.startsWith("1"));
        assertThat(filteredKeys).isEqualTo(new int[] {1, 10});
    }

    @Test
    public void testRemoveNotExistingKey() {
        var array = init();

        // Removing key 100, which does not exist.
        var prevContainer = array.getArray();
        // Should do nothing
        array.remove(100);
        assertThat(array.getArray().size()).isEqualTo(4);
        // Internal container must NOT be copied
        assertThat(prevContainer).isSameInstanceAs(array.getArray());
    }


    @Test
    public void testRemoveExistingKey() {
        var array = init();

        // Removing key 3, which exist should copy the storage and remove the key.
        var prevContainer = array.getArray();
        assertThat(array.get(3)).isEqualTo("3");
        // Should do nothing
        array.remove(3);
        assertThat(array.getArray().size()).isEqualTo(3);
        assertThat(array.get(3)).isNull();
        // Internal container must NOT be copied
        assertThat(prevContainer).isNotSameInstanceAs(array.getArray());
    }

    @Test
    @android.platform.test.annotations.LargeTest
    public void testConcurrentReadsAndWrites() {
        final var array = init();
        final CountDownLatch latch = new CountDownLatch(5);
        try (ExecutorService executors = Executors.newFixedThreadPool(5)) {
            for (int i = 0; i < 5; i++) {
                executors.submit(() -> {
                    while (true) {
                        var k0 = array.get(0);
                        var k1 = array.get(1);
                        var k3 = array.get(3);
                        var k10 = array.get(10);
                        var k100 = array.get(100);
                        if ("10".equals(k0)
                                && k1 == null
                                && "200".equals(k3)
                                && "10".equals(k10)
                                && "100".equals(k100)) {
                            latch.countDown();
                            return;
                        }
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }
            try {
                Thread.sleep(10);
                array.put(10, "1");
                Thread.sleep(10);
                array.put(0, "1");
                Thread.sleep(10);
                array.put(0, "10");
                Thread.sleep(10);
                array.remove(1);
                Thread.sleep(10);
                array.put(10, "10");
                Thread.sleep(10);
                array.put(100, "100");
                Thread.sleep(10);
                array.put(3, "0");
                Thread.sleep(10);
                array.put(3, "200");

                assertThat(latch.await(5, SECONDS)).isTrue();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private CopyOnWriteSparseArray<String> init() {
        var array = new CopyOnWriteSparseArray<String>();
        array.put(1, "1");
        array.put(3, "3");
        array.put(0, "0");
        array.put(10, "10");
        assertThat(array.size()).isEqualTo(4);
        return array;
    }
}
