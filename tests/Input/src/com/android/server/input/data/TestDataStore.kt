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

package com.android.server.input.data

import android.util.AtomicFile
import java.io.File

class TestDataStore {

    private val fakeFileInjector = FakeFileInjector()
    private val inputDataStore = InputDataStore(fakeFileInjector)

    private class FakeFileInjector : InputDataStore.FileInjector() {
        private val fileMap: MutableMap<String, AtomicFile> = mutableMapOf()

        override fun getAtomicFileForUserId(userId: Int, fileName: String): AtomicFile {
            val key = userId.toString() + "_" + fileName
            if (!fileMap.containsKey(key)) {
                fileMap[key] = AtomicFile(File.createTempFile(fileName, ".xml"))
            }
            return fileMap[key]!!
        }

        fun clear() {
            for (file in fileMap.values) {
                file.delete()
            }
            fileMap.clear()
        }
    }

    fun getDataStore(): InputDataStore {
        return inputDataStore
    }

    fun clear() {
        fakeFileInjector.clear()
    }
}
