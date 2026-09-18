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

package com.android.wm.shell.repository

/**
 * Iterates on the [Item]s which makes the provided predicate as [true]. The default [predicate]
 * returns all the items. The order depends on the [GenericRepository] implementation.
 */
fun <Key, Item> GenericRepository<Key, Item>.iterate(
    predicate: (Key, Item) -> Boolean = { _, _ -> true },
    fn: (Item) -> Unit,
) = find(predicate).forEach(fn)
