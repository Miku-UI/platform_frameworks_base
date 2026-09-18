/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ParcelDedupHelperTest {

    @Test
    fun testDedupBinderHelper_Optimized_ReducesSizeAndPreservesData() {
        val binderA = Binder()
        val binderB = Binder()
        val inputList = listOf(binderA, binderB, binderA, binderA, binderB)

        // Measure size without the helper
        val standardParcel = Parcel.obtain()
        val standardSize: Int
        try {
            standardParcel.writeBinderList(inputList)
            standardSize = standardParcel.dataSize()
        } finally {
            standardParcel.recycle()
        }

        // Measure size with the helper (Enabled)
        val optimizedParcel = Parcel.obtain()
        val writerHelper = ParcelDedupHelper.Builder().dedupBinders(true).build()

        try {
            optimizedParcel.setReadWriteHelper(writerHelper)
            optimizedParcel.writeBinderList(inputList)
            val optimizedSize = optimizedParcel.dataSize()

            // Verify optimization occurred
            assertThat(optimizedSize).isLessThan(standardSize)

            // Verify data integrity
            optimizedParcel.setDataPosition(0)
            val readerHelper = ParcelDedupHelper.Builder().dedupBinders(true).build()
            optimizedParcel.setReadWriteHelper(readerHelper)

            val outputList = optimizedParcel.createBinderArrayList()
            requireNotNull(outputList)

            assertThat(outputList).containsExactlyElementsIn(inputList).inOrder()
        } finally {
            optimizedParcel.recycle()
        }
    }

    @Test
    fun testDedupString16Helper_Optimized_ReducesSizeAndPreservesData() {
        val strA = "A long string to ensure deduplication savings are significant."
        val strB = "Short."
        val inputList = listOf(strA, strB, strA, strA, strB)

        // Measure size without the helper
        val standardParcel = Parcel.obtain()
        val standardSize: Int
        try {
            // Explicitly use writeString16 to test the 16-bit cache
            inputList.forEach { standardParcel.writeString16(it) }
            standardSize = standardParcel.dataSize()
        } finally {
            standardParcel.recycle()
        }

        // Measure size with the helper (Enabled for String16)
        val optimizedParcel = Parcel.obtain()
        val writerHelper = ParcelDedupHelper.Builder().dedupString16(true).build()

        try {
            optimizedParcel.setReadWriteHelper(writerHelper)
            inputList.forEach { optimizedParcel.writeString16(it) }
            val optimizedSize = optimizedParcel.dataSize()

            // Verify optimization occurred
            assertThat(optimizedSize).isLessThan(standardSize)

            // Verify data integrity
            optimizedParcel.setDataPosition(0)
            val readerHelper = ParcelDedupHelper.Builder().dedupString16(true).build()
            optimizedParcel.setReadWriteHelper(readerHelper)

            val outputList = ArrayList<String>()
            repeat(inputList.size) { outputList.add(optimizedParcel.readString16()!!) }

            assertThat(outputList).containsExactlyElementsIn(inputList).inOrder()
        } finally {
            optimizedParcel.recycle()
        }
    }

    @Test
    fun testDedupString8Helper_Optimized_ReducesSizeAndPreservesData() {
        val strA = "A long string to ensure deduplication savings are significant."
        val strB = "Short."
        val inputList = listOf(strA, strB, strA, strA, strB)

        // Measure size without the helper
        val standardParcel = Parcel.obtain()
        val standardSize: Int
        try {
            // Explicitly use writeString8 to test the 8-bit cache
            inputList.forEach { standardParcel.writeString8(it) }
            standardSize = standardParcel.dataSize()
        } finally {
            standardParcel.recycle()
        }

        // Measure size with the helper (Enabled for String8)
        val optimizedParcel = Parcel.obtain()
        val writerHelper = ParcelDedupHelper.Builder().dedupString8(true).build()

        try {
            optimizedParcel.setReadWriteHelper(writerHelper)
            inputList.forEach { optimizedParcel.writeString8(it) }
            val optimizedSize = optimizedParcel.dataSize()

            // Verify optimization occurred
            assertThat(optimizedSize).isLessThan(standardSize)

            // Verify data integrity
            optimizedParcel.setDataPosition(0)
            val readerHelper = ParcelDedupHelper.Builder().dedupString8(true).build()
            optimizedParcel.setReadWriteHelper(readerHelper)

            val outputList = ArrayList<String>()
            repeat(inputList.size) { outputList.add(optimizedParcel.readString8()!!) }

            assertThat(outputList).containsExactlyElementsIn(inputList).inOrder()
        } finally {
            optimizedParcel.recycle()
        }
    }

    @Test
    fun testDedupDisabled_BehavesLikeStandardParcel() {
        // Setup data with duplicates that WOULD be optimized if enabled
        val strA = "Duplicate String Value"
        val binderA = Binder()
        val strings = listOf(strA, strA, strA)
        val binders = listOf(binderA, binderA, binderA)

        // Measure Standard Size (Baseline)
        val standardParcel = Parcel.obtain()
        val standardSize: Int
        try {
            // Mix of types to ensure we check all paths
            standardParcel.writeBinderList(binders)
            strings.forEach { standardParcel.writeString8(it) }
            strings.forEach { standardParcel.writeString16(it) }
            standardSize = standardParcel.dataSize()
        } finally {
            standardParcel.recycle()
        }

        // Measure Helper Size
        val unoptimizedParcel = Parcel.obtain()
        val writerHelper =
            ParcelDedupHelper.Builder()
                .dedupBinders(false)
                .dedupString8(false)
                .dedupString16(false)
                .build()

        try {
            unoptimizedParcel.setReadWriteHelper(writerHelper)
            unoptimizedParcel.writeBinderList(binders)
            strings.forEach { unoptimizedParcel.writeString8(it) }
            strings.forEach { unoptimizedParcel.writeString16(it) }
            val unoptimizedSize = unoptimizedParcel.dataSize()

            // Verify no optimization occurred
            assertThat(unoptimizedSize).isEqualTo(standardSize)

            // Verify data integrity
            unoptimizedParcel.setDataPosition(0)
            val readerHelper =
                ParcelDedupHelper.Builder()
                    .dedupBinders(false)
                    .dedupString8(false)
                    .dedupString16(false)
                    .build()
            unoptimizedParcel.setReadWriteHelper(readerHelper)

            val outputBinders = unoptimizedParcel.createBinderArrayList()

            val outputString8s = ArrayList<String>()
            repeat(strings.size) { outputString8s.add(unoptimizedParcel.readString8()!!) }

            val outputString16s = ArrayList<String>()
            repeat(strings.size) { outputString16s.add(unoptimizedParcel.readString16()!!) }

            assertThat(outputBinders).containsExactlyElementsIn(binders).inOrder()
            assertThat(outputString8s).containsExactlyElementsIn(strings).inOrder()
            assertThat(outputString16s).containsExactlyElementsIn(strings).inOrder()
        } finally {
            unoptimizedParcel.recycle()
        }
    }
}
