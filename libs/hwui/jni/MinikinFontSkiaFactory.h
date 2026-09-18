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

#include <cutils/compiler.h>
#include <minikin/FontCollection.h>
#include <minikin/MinikinFontFactory.h>

#include <memory>

namespace android {

class MinikinFontSkiaFactory : minikin::MinikinFontFactory {
private:
    MinikinFontSkiaFactory();

public:
    static ANDROID_API void init();
    void skip(minikin::BufferReader* reader) const override;
    std::shared_ptr<minikin::MinikinFont> create(minikin::BufferReader reader) const override;
    void write(minikin::BufferWriter* writer, const minikin::MinikinFont* typeface) const override;
};

}  // namespace android
