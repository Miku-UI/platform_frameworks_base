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

package com.android.server.adb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;

@RunWith(JUnit4.class)
public final class AdbdMessageTests {

    @Test
    public void testType() {
        final String typeStr = "TS";
        byte[] typeBytes = typeStr.getBytes();
        AdbdMessage msg = new AdbdMessage(typeBytes);
        Optional<String> type = msg.readType();

        assertTrue(type.isPresent());
        assertEquals("Failed to read type", typeStr, type.get());
    }

    @Test
    public void testTypeFailure() {
        final String typeStr = "T";
        byte[] typeBytes = typeStr.getBytes();
        AdbdMessage msg = new AdbdMessage(typeBytes);
        Optional<String> type = msg.readType();
        assertTrue(type.isEmpty());
    }


    @Test
    public void testOptionals() {
        byte[] message = new byte[0];
        AdbdMessage msg = new AdbdMessage(message);
        assertTrue(msg.readType().isEmpty());
        assertTrue(msg.readU8String().isEmpty());
        assertTrue(msg.readU16().isEmpty());
    }

    @Test
    public void testReadU8StringSuccess() {
        // Arrange: Length 5 (0x05), followed by "HELLO"
        byte[] buffer = {0x05, 'H', 'E', 'L', 'L', 'O'};
        AdbdMessage message = new AdbdMessage(buffer);
        Optional<String> str = message.readU8String();
        assertTrue(str.isPresent());
        assertEquals("HELLO", str.get());
    }

    @Test
    public void testReadRegisterMessage() {
        byte[] buffer = {'T', 'S', 0x05, 'H', 'E', 'L', 'L', 'O', 0x03, 'F' , 'O', 'O'};
        AdbdMessage message = new AdbdMessage(buffer);

        Optional<String> type = message.readType();
        assertTrue(type.isPresent());
        assertEquals("TS", type.get());

        Optional<String> instance = message.readU8String();
        assertTrue(instance.isPresent());
        assertEquals("HELLO", instance.get());

        Optional<String> service = message.readU8String();
        assertTrue(service.isPresent());
        assertEquals("FOO", service.get());
    }

    @Test
    public void testReadU8StringMissingStringContent() {
        byte[] buffer = {0x03, 'A', 'B'};
        AdbdMessage message = new AdbdMessage(buffer);
        Optional<String> str = message.readU8String();
        assertTrue(str.isEmpty());
    }

    @Test
    public void testReadU16Success() {
        // Arrange: 0x0102 (258) in Little Endian is {0x02, 0x01}
        byte[] buffer = {0x02, 0x01};
        AdbdMessage message = new AdbdMessage(buffer);
        Optional<Integer> u16 = message.readU16();
        assertTrue(u16.isPresent());
        assertEquals(258, u16.get().intValue());
    }

    @Test
    public void testReadU16BufferTooShortOneByteLeft() {
        byte[] buffer = {0x01};
        AdbdMessage message = new AdbdMessage(buffer);
        Optional<Integer> u16 = message.readU16();
        assertTrue(u16.isEmpty());
    }


    @Test
    public void testAllMethods() {
        ByteBuffer bb = ByteBuffer.allocate(9);
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.put((byte) 'U').put((byte) 'S');
        bb.put((byte) 0x04);
        bb.put("Data".getBytes());

        // port
        bb.put((byte) 0xD2).put((byte) 0x04); // 1234 (0x04D2) in LE
        byte[] buffer = bb.array();

        AdbdMessage message = new AdbdMessage(buffer);

        Optional<String> type = message.readType();
        assertTrue(type.isPresent());
        assertEquals("US", type.get());

        Optional<String> str = message.readU8String();
        assertTrue(str.isPresent());
        assertEquals("Data", str.get());

        Optional<Integer> u16 = message.readU16();
        assertTrue(u16.isPresent());
        assertEquals(1234, u16.get().intValue());

        assertTrue(message.readType().isEmpty());
    }
}
