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

package android.util.proto;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;


/**
 * Unit tests for {@link android.util.proto.ProtoFieldFilter}.
 *
 *  Build/Install/Run:
 *  atest FrameworksCoreTests:ProtoFieldFilterTest
 *
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ProtoFieldFilterTest {

    private static final class FieldTypes {
        static final long INT64 = ProtoStream.FIELD_TYPE_INT64 | ProtoStream.FIELD_COUNT_SINGLE;
        static final long FIXED64 = ProtoStream.FIELD_TYPE_FIXED64 | ProtoStream.FIELD_COUNT_SINGLE;
        static final long BYTES = ProtoStream.FIELD_TYPE_BYTES | ProtoStream.FIELD_COUNT_SINGLE;
        static final long FIXED32 = ProtoStream.FIELD_TYPE_FIXED32 | ProtoStream.FIELD_COUNT_SINGLE;
        static final long MESSAGE = ProtoStream.FIELD_TYPE_MESSAGE | ProtoStream.FIELD_COUNT_SINGLE;
        static final long INT32 = ProtoStream.FIELD_TYPE_INT32 | ProtoStream.FIELD_COUNT_SINGLE;
    }

    private static ProtoOutputStream createBasicTestProto() {
        ProtoOutputStream out = new ProtoOutputStream();

        out.writeInt64(ProtoStream.makeFieldId(1, FieldTypes.INT64), 12345L);
        out.writeFixed64(ProtoStream.makeFieldId(2, FieldTypes.FIXED64), 0x1234567890ABCDEFL);
        out.writeBytes(ProtoStream.makeFieldId(3, FieldTypes.BYTES), new byte[]{1, 2, 3, 4, 5});
        out.writeFixed32(ProtoStream.makeFieldId(4, FieldTypes.FIXED32), 0xDEADBEEF);

        return out;
    }

    private static byte[] filterProto(byte[] input, ProtoFieldFilter filter) throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(input);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        filter.filter(inputStream, outputStream);
        return outputStream.toByteArray();
    }

    @Test
    public void testNoFieldsFiltered() throws IOException {
        byte[] input = createBasicTestProto().getBytes();
        byte[] output = filterProto(input, new ProtoFieldFilter(fieldNumber -> true));
        assertArrayEquals("No fields should be filtered out", input, output);
    }

    @Test
    public void testAllFieldsFiltered() throws IOException {
        byte[] input = createBasicTestProto().getBytes();
        byte[] output = filterProto(input, new ProtoFieldFilter(fieldNumber -> false));

        assertEquals("All fields should be filtered out", 0, output.length);
    }

    @Test
    public void testSpecificFieldsFiltered() throws IOException {

        ProtoOutputStream out = createBasicTestProto();
        byte[] output = filterProto(out.getBytes(), new ProtoFieldFilter(n -> n != 2));

        ProtoInputStream in = new ProtoInputStream(output);
        boolean[] fieldsFound = new boolean[5];

        int fieldNumber;
        while ((fieldNumber = in.nextField()) != ProtoInputStream.NO_MORE_FIELDS) {
            fieldsFound[fieldNumber] = true;
            switch (fieldNumber) {
                case 1:
                    assertEquals(12345L, in.readLong(ProtoStream.makeFieldId(1, FieldTypes.INT64)));
                    break;
                case 2:
                    fail("Field 2 should be filtered out");
                    break;
                case 3:
                    assertArrayEquals(new byte[]{1, 2, 3, 4, 5},
                            in.readBytes(ProtoStream.makeFieldId(3, FieldTypes.BYTES)));
                    break;
                case 4:
                    assertEquals(0xDEADBEEF,
                            in.readInt(ProtoStream.makeFieldId(4, FieldTypes.FIXED32)));
                    break;
                default:
                    fail("Unexpected field number: " + fieldNumber);
            }
        }

        assertTrue("Field 1 should be present", fieldsFound[1]);
        assertFalse("Field 2 should be filtered", fieldsFound[2]);
        assertTrue("Field 3 should be present", fieldsFound[3]);
        assertTrue("Field 4 should be present", fieldsFound[4]);
    }

    @Test
    public void testDifferentWireTypes() throws IOException {
        ProtoOutputStream out = new ProtoOutputStream();

        out.writeInt64(ProtoStream.makeFieldId(1, FieldTypes.INT64), 12345L);
        out.writeFixed64(ProtoStream.makeFieldId(2, FieldTypes.FIXED64), 0x1234567890ABCDEFL);
        out.writeBytes(ProtoStream.makeFieldId(3, FieldTypes.BYTES), new byte[]{10, 20, 30});

        long token = out.start(ProtoStream.makeFieldId(4, FieldTypes.MESSAGE));
        out.writeInt32(ProtoStream.makeFieldId(1, FieldTypes.INT32), 42);
        out.end(token);

        out.writeFixed32(ProtoStream.makeFieldId(5, FieldTypes.FIXED32), 0xDEADBEEF);

        byte[] output = filterProto(out.getBytes(), new ProtoFieldFilter(fieldNumber -> true));

        ProtoInputStream in = new ProtoInputStream(output);
        boolean[] fieldsFound = new boolean[6];

        int fieldNumber;
        while ((fieldNumber = in.nextField()) != ProtoInputStream.NO_MORE_FIELDS) {
            fieldsFound[fieldNumber] = true;
            switch (fieldNumber) {
                case 1:
                    assertEquals(12345L, in.readLong(ProtoStream.makeFieldId(1, FieldTypes.INT64)));
                    break;
                case 2:
                    assertEquals(0x1234567890ABCDEFL,
                            in.readLong(ProtoStream.makeFieldId(2, FieldTypes.FIXED64)));
                    break;
                case 3:
                    assertArrayEquals(new byte[]{10, 20, 30},
                            in.readBytes(ProtoStream.makeFieldId(3, FieldTypes.BYTES)));
                    break;
                case 4:
                    token = in.start(ProtoStream.makeFieldId(4, FieldTypes.MESSAGE));
                    assertTrue(in.nextField() == 1);
                    assertEquals(42, in.readInt(ProtoStream.makeFieldId(1, FieldTypes.INT32)));
                    assertTrue(in.nextField() == ProtoInputStream.NO_MORE_FIELDS);
                    in.end(token);
                    break;
                case 5:
                    assertEquals(0xDEADBEEF,
                            in.readInt(ProtoStream.makeFieldId(5, FieldTypes.FIXED32)));
                    break;
                default:
                    fail("Unexpected field number: " + fieldNumber);
            }
        }

        assertTrue("All fields should be present",
                fieldsFound[1] && fieldsFound[2] && fieldsFound[3]
                && fieldsFound[4] && fieldsFound[5]);
    }
    @Test
    public void testNestedMessagesUnfiltered() throws IOException {
        ProtoOutputStream out = new ProtoOutputStream();

        out.writeInt64(ProtoStream.makeFieldId(1, FieldTypes.INT64), 12345L);

        long token = out.start(ProtoStream.makeFieldId(2, FieldTypes.MESSAGE));
        out.writeInt32(ProtoStream.makeFieldId(1, FieldTypes.INT32), 6789);
        out.writeFixed32(ProtoStream.makeFieldId(2, FieldTypes.FIXED32), 0xCAFEBABE);
        out.end(token);

        byte[] output = filterProto(out.getBytes(), new ProtoFieldFilter(n -> n != 2));

        // Verify output
        ProtoInputStream in = new ProtoInputStream(output);
        boolean[] fieldsFound = new boolean[3];

        int fieldNumber;
        while ((fieldNumber = in.nextField()) != ProtoInputStream.NO_MORE_FIELDS) {
            fieldsFound[fieldNumber] = true;
            if (fieldNumber == 1) {
                assertEquals(12345L, in.readLong(ProtoStream.makeFieldId(1, FieldTypes.INT64)));
            } else {
                fail("Unexpected field number: " + fieldNumber);
            }
        }

        assertTrue("Field 1 should be present", fieldsFound[1]);
        assertFalse("Field 2 should be filtered out", fieldsFound[2]);
    }

    @Test
    public void testRepeatedFields() throws IOException {

        ProtoOutputStream out = new ProtoOutputStream();
        long fieldId = ProtoStream.makeFieldId(1,
                ProtoStream.FIELD_TYPE_INT32 | ProtoStream.FIELD_COUNT_REPEATED);

        out.writeRepeatedInt32(fieldId, 100);
        out.writeRepeatedInt32(fieldId, 200);
        out.writeRepeatedInt32(fieldId, 300);

        byte[] input = out.getBytes();

        byte[] output = filterProto(input, new ProtoFieldFilter(fieldNumber -> true));

        assertArrayEquals("Repeated fields should be preserved", input, output);
    }

    @Test
    public void testRepeatedFields_selectivelyFiltered() throws IOException {
        ProtoOutputStream out = new ProtoOutputStream();
        long fieldId1 = ProtoStream.makeFieldId(1,
                ProtoStream.FIELD_TYPE_INT32 | ProtoStream.FIELD_COUNT_REPEATED);
        long fieldId2 = ProtoStream.makeFieldId(2,
                ProtoStream.FIELD_TYPE_BYTES | ProtoStream.FIELD_COUNT_REPEATED);

        out.writeRepeatedInt32(fieldId1, 100);
        out.writeRepeatedInt32(fieldId1, 200);
        out.writeRepeatedBytes(fieldId2, "hello".getBytes());
        out.writeRepeatedBytes(fieldId2, "world".getBytes());

        byte[] output = filterProto(out.getBytes(), new ProtoFieldFilter(n -> n == 2));

        ProtoInputStream in = new ProtoInputStream(output);

        assertTrue(in.nextField() == 2);
        assertArrayEquals("hello".getBytes(), in.readBytes(fieldId2));
        assertTrue(in.nextField() == 2);
        assertArrayEquals("world".getBytes(), in.readBytes(fieldId2));
        assertEquals(ProtoInputStream.NO_MORE_FIELDS, in.nextField());
    }

    @Test
    public void testSmallBuffer() throws IOException {
        byte[] data = new byte[100];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }

        ProtoOutputStream out = new ProtoOutputStream();
        out.writeBytes(ProtoStream.makeFieldId(1, FieldTypes.BYTES), data);
        out.writeInt32(ProtoStream.makeFieldId(2, FieldTypes.INT32), 123);

        byte[] input = out.getBytes();

        // Use a small buffer, smaller than the byte array field.
        ProtoFieldFilter filter = new ProtoFieldFilter(n -> true, 16);
        byte[] output = filterProto(input, filter);

        assertArrayEquals("Output should match input with small buffer", input, output);
    }

    @Test(expected = IOException.class)
    public void testMalformedVarint_tooLong() throws IOException {
        // 11-byte varint (invalid)
        byte[] input = new byte[]{
                (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81,
                (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, (byte) 0x81, 0x01
        };
        filterProto(input, new ProtoFieldFilter(n -> true));
    }

    @Test(expected = IOException.class)
    public void testMalformedVarint_incomplete() throws IOException {
        // Incomplete varint at the end of the stream
        byte[] input = new byte[]{(byte) 0x81};
        filterProto(input, new ProtoFieldFilter(n -> true));
    }

    @Test(expected = IOException.class)
    public void testEofInFixed32() throws IOException {
        ProtoOutputStream out = new ProtoOutputStream();
        out.writeFixed32(ProtoStream.makeFieldId(1, FieldTypes.FIXED32), 0xDEADBEEF);
        byte[] full = out.getBytes();
        // Truncate the input to cause an EOF
        byte[] truncated = new byte[full.length - 1];
        System.arraycopy(full, 0, truncated, 0, truncated.length);

        filterProto(truncated, new ProtoFieldFilter(n -> true));
    }

    @Test(expected = IOException.class)
    public void testInvalidLengthDelimitedLength() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x0A); // field 1, wire type 2
        // varint for 2^31, which is > Integer.MAX_VALUE
        baos.write(new byte[]{(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x08});
        byte[] input = baos.toByteArray();

        filterProto(input, new ProtoFieldFilter(n -> true));
    }

    @Test(expected = IOException.class)
    public void testUnknownWireType() throws IOException {
        byte[] input = new byte[]{(byte) 0x0E}; // field 1, wire type 6
        filterProto(input, new ProtoFieldFilter(n -> true));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBufferSizeZero() {
        new ProtoFieldFilter(n -> true, 0);
    }

    @Test(expected = IOException.class)
    public void testEofWhileSkippingBytes() throws IOException {
        // This test specifically covers the bug in b/463808544 where a stream
        // returning -1 from read() could cause an infinite loop in skipBytes.

        ProtoOutputStream out = new ProtoOutputStream();
        out.writeBytes(ProtoStream.makeFieldId(1, FieldTypes.BYTES), new byte[50]);
        byte[] full = out.getBytes();

        // Truncate the input so we hit EOF while skipping the body.
        // We leave enough for the tag and length, but not the full body.
        byte[] truncated = new byte[full.length - 10];
        System.arraycopy(full, 0, truncated, 0, truncated.length);

        // Use a stream that forces the fallback path in ProtoFieldFilter.skipBytes
        // by returning 0 from skip().
        InputStream in = new ByteArrayInputStream(truncated) {
            @Override
            public long skip(long n) {
                return 0;
            }
        };
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        ProtoFieldFilter filter = new ProtoFieldFilter(n -> false);
        filter.filter(in, outputStream);
    }
}
