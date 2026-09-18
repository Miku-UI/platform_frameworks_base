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

#pragma once

#include <minikin/Buffer.h>
#include <minikin/FontCollection.h>
#include <minikin/SystemFonts.h>
#include <rust/cxx.h>

namespace minikin {

class FontCollection;
using FontCollectionPtrs = std::vector<std::shared_ptr<FontCollection>>;

std::unique_ptr<FontCollectionPtrs> font_collection_read_vector_slice(
        rust::Slice<const uint8_t> data, size_t& bytes_read);
std::shared_ptr<FontCollection> vector_get(const FontCollectionPtrs& collections, size_t index);

void system_fonts_add_font_map(std::shared_ptr<FontCollection> collection);
void system_fonts_register_default(std::shared_ptr<FontCollection> collection);
void system_fonts_register_fallback(const std::string& family_name,
                                    std::shared_ptr<FontCollection> collection);

} // namespace minikin

namespace android {

void minikin_font_skia_factory_init();

} // namespace android
