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
package android.util;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import com.google.common.primitives.Ints;
import com.google.common.testing.EqualsTester;
import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;

public final class ImmutableIntArrayTest {

    @Rule
    public final Expect expect = Expect.create();

    @Test
    public void testFrom_null() {
        assertThrows(NullPointerException.class, () -> ImmutableIntArray.from((int[]) null));
        assertThrows(NullPointerException.class, () -> ImmutableIntArray.from((IntArray) null));
    }

    @Test
    public void testFromPrimitiveArray_emptyArray() {
        ImmutableIntArray fixture = ImmutableIntArray.from(new int[0]);

        assertWithMessage("from(...)").that(fixture).isNotNull();
        assertWithMessage("size()").that(fixture.size()).isEqualTo(0);
    }

    @Test
    public void testAlmostEverything_fromPrimitiveArray() {
        int[] source = new int[] {4, 8, 15, 16, 23, 42 };

        ImmutableIntArray immutableArray = ImmutableIntArray.from(source);

        testAlmostEverything(source, "from()", immutableArray);
    }

    @Test
    public void testAlmostEverything_clone() {
        int[] source = new int[] {4, 8, 15, 16, 23, 42 };
        ImmutableIntArray immutableArray = ImmutableIntArray.from(source);

        ImmutableIntArray clone = immutableArray.clone();

        testAlmostEverything(source, "clone()", clone);
    }

    @Test
    public void testFromIntArray_emptyArray() {
        ImmutableIntArray fixture = ImmutableIntArray.from(new IntArray());

        assertWithMessage("from(...)").that(fixture).isNotNull();
        assertWithMessage("size()").that(fixture.size()).isEqualTo(0);
    }

    @Test
    public void testAlmostEverything_fromIntArray() {
        IntArray source = IntArray.wrap(new int[] {4, 8, 15, 16, 23, 42 });

        ImmutableIntArray immutableArray = ImmutableIntArray.from(source);

        testAlmostEverything(source, "from(IntArray)", immutableArray);
    }

    // Tests almost everything (except equals() and hashCode())
    private void testAlmostEverything(int[] source, String method,
            ImmutableIntArray immutableArray) {
        assertWithMessage("%s", method).that(immutableArray).isNotNull();

        int size = source.length;
        assertWithMessage("size()").that(immutableArray.size()).isEqualTo(size);

        assertThrows(ArrayIndexOutOfBoundsException.class, () -> immutableArray.get(-1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> immutableArray.get(size));
        for (int i = 0; i < source.length; i++) {
            expect.withMessage("get(%s)", i).that(immutableArray.get(i)).isEqualTo(source[i]);
        }

        expect.withMessage("toString()").that(immutableArray.toString())
                .isEqualTo(Arrays.toString(source));

        int[] toArray1 = immutableArray.toArray();
        assertWithMessage("toArray()").that(toArray1).isNotNull();
        expect.withMessage("toArray()").that(toArray1).asList()
                .containsExactlyElementsIn(Ints.asList(source));

        int[] toArray2 = immutableArray.toArray();
        expect.withMessage("2nd call toArray()").that(toArray2).isNotSameInstanceAs(toArray1);

        // Make sure it's immutable
        for (int i = 0; i < size; i++) {
            int valueBefore = source[i];
            source[i] += 108;
            expect.withMessage("get(%s) after changing source", i).that(immutableArray.get(i))
                    .isEqualTo(valueBefore);
        }
    }

    // Tests almost everything (except equals() and hashCode())
    private void testAlmostEverything(IntArray source, String method,
            ImmutableIntArray immutableArray) {
        assertWithMessage("%s", method).that(immutableArray).isNotNull();

        int size = source.size();
        assertWithMessage("size()").that(immutableArray.size()).isEqualTo(size);

        assertThrows(ArrayIndexOutOfBoundsException.class, () -> immutableArray.get(-1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> immutableArray.get(size));
        for (int i = 0; i < source.size(); i++) {
            expect.withMessage("get(%s)", i).that(immutableArray.get(i)).isEqualTo(source.get(i));
        }

        expect.withMessage("toString()").that(immutableArray.toString())
                .isEqualTo(Arrays.toString(source.toArray()));

        int[] toArray1 = immutableArray.toArray();
        assertWithMessage("toArray()").that(toArray1).isNotNull();
        expect.withMessage("toArray()").that(toArray1).asList()
                .containsExactlyElementsIn(Ints.asList(source.toArray()));

        int[] toArray2 = immutableArray.toArray();
        expect.withMessage("2nd call toArray()").that(toArray2).isNotSameInstanceAs(toArray1);

        // Make sure it's immutable
        for (int i = 0; i < size; i++) {
            int valueBefore = source.get(i);
            source.set(i, valueBefore + 108);
            expect.withMessage("get(%s) after changing source", i).that(immutableArray.get(i))
                    .isEqualTo(valueBefore);
        }
    }

    @Test
    public void testEqualsHashcode() {
        int[] array = new int[] {4, 8, 15, 16, 23, 42 };
        ImmutableIntArray fromArray1 = ImmutableIntArray.from(array);
        ImmutableIntArray fromArray2 = ImmutableIntArray.from(array);
        ImmutableIntArray fromArray3 =
                ImmutableIntArray.from(new int[] {4, 8, 15, 16, 23, 42 });
        IntArray intArray = IntArray.wrap(new int[] {4, 8, 15, 16, 23, 42 });
        ImmutableIntArray fromIntArray1 = ImmutableIntArray.from(intArray);
        ImmutableIntArray fromIntArray2 = ImmutableIntArray.from(intArray);
        ImmutableIntArray fromIntArray3 =
                ImmutableIntArray.from(IntArray.wrap(new int[] {4, 8, 15, 16, 23, 42 }));


        int[] emptyArray = new int[0];
        ImmutableIntArray empty1 = ImmutableIntArray.from(emptyArray);
        ImmutableIntArray empty2 = ImmutableIntArray.from(new int[0]);

        new EqualsTester()
                .addEqualityGroup(fromArray1, fromArray2, fromArray3,
                        fromIntArray1, fromIntArray2, fromIntArray3)
                .addEqualityGroup(empty1, empty2)
                .testEquals();
    }
}
