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

#include "link/NoDefaultResourceRemover.h"

#include "test/Test.h"

namespace aapt {

TEST(NoDefaultResourceRemoverTest, RemoveEntryWithNoDefaultAndOnlyLocales) {
  std::unique_ptr<IAaptContext> context = test::ContextBuilder().SetPackageId(0x01).Build();
  std::unique_ptr<ResourceTable> table =
      test::ResourceTableBuilder()
          .AddSimple("android:string/foo")
          .AddSimple("android:string/foo", test::ParseConfigOrDie("en-rGB"))
          .AddSimple("android:string/foo", test::ParseConfigOrDie("fr-rFR"))
          .AddSimple("android:string/bar", test::ParseConfigOrDie("en-rGB"))
          .AddSimple("android:string/bar", test::ParseConfigOrDie("fr-rFR"))
          .AddSimple("android:string/bat", test::ParseConfigOrDie("en-rGB-xhdpi"))
          .AddSimple("android:string/bat", test::ParseConfigOrDie("fr-rFR-hdpi"))
          .AddSimple("android:string/baz", test::ParseConfigOrDie("en-rGB"))
          .AddSimple("android:string/baz", test::ParseConfigOrDie("fr-rFR"))
          .SetSymbolState("android:string/baz", ResourceId(0x01020002), Visibility::Level::kPublic)
          .Build();

  NoDefaultResourceRemover remover;
  ASSERT_TRUE(remover.Consume(context.get(), table.get()));

  EXPECT_TRUE(table->FindResource(test::ParseNameOrDie("android:string/foo")));
  EXPECT_FALSE(table->FindResource(test::ParseNameOrDie("android:string/bar")));
  EXPECT_TRUE(table->FindResource(test::ParseNameOrDie("android:string/bat")));
  EXPECT_TRUE(table->FindResource(test::ParseNameOrDie("android:string/baz")));
}

TEST(NoDefaultResourceRemoverTest, KeepEntryWithLocalesAndDensities) {
  std::unique_ptr<IAaptContext> context =
      test::ContextBuilder().SetPackageId(0x01).SetMinSdkVersion(26).Build();
  std::unique_ptr<ResourceTable> table =
      test::ResourceTableBuilder()
          .AddSimple("android:drawable/keep1", test::ParseConfigOrDie("mdpi")) // v4
          .AddSimple("android:drawable/keep1", test::ParseConfigOrDie("en-rGB"))
          .AddSimple("android:drawable/keep1", test::ParseConfigOrDie("fr-rFR"))
          .AddSimple("android:drawable/keep2", test::ParseConfigOrDie("fr-rFR"))
          .AddSimple("android:drawable/keep2", test::ParseConfigOrDie("en-rGB"))
          .AddSimple("android:drawable/keep2", test::ParseConfigOrDie("xxxhdpi")) // v4
          .AddSimple("android:drawable/remove1", test::ParseConfigOrDie("fr-rFR"))
          .AddSimple("android:drawable/remove1", test::ParseConfigOrDie("en-rGB"))
          .AddSimple("android:drawable/remove1", test::ParseConfigOrDie("w600dp-xhdpi")) // v13
          .Build();

  NoDefaultResourceRemover remover;
  ASSERT_TRUE(remover.Consume(context.get(), table.get()));

  EXPECT_TRUE(table->FindResource(test::ParseNameOrDie("android:drawable/keep1")));
  EXPECT_TRUE(table->FindResource(test::ParseNameOrDie("android:drawable/keep2")));
  EXPECT_FALSE(table->FindResource(test::ParseNameOrDie("android:drawable/remove1")));
}

TEST(NoDefaultResourceRemoverTest, RemoveEntryWithLocalesAndDensitiesLowVersion) {
  std::unique_ptr<IAaptContext> context =
      test::ContextBuilder().SetPackageId(0x01).SetMinSdkVersion(3).Build();
  std::unique_ptr<ResourceTable> table =
      test::ResourceTableBuilder()
          .AddSimple("android:drawable/remove1", test::ParseConfigOrDie("mdpi")) // v4
          .AddSimple("android:drawable/remove1", test::ParseConfigOrDie("en-rGB"))
          .AddSimple("android:drawable/remove1", test::ParseConfigOrDie("fr-rFR"))
          .Build();

  NoDefaultResourceRemover remover;
  ASSERT_TRUE(remover.Consume(context.get(), table.get()));

  EXPECT_FALSE(table->FindResource(test::ParseNameOrDie("android:drawable/remove1")));
}

TEST(NoDefaultResourceRemoverTest, KeepEntryWithVersion) {
  std::unique_ptr<IAaptContext> context =
      test::ContextBuilder().SetPackageId(0x01).SetMinSdkVersion(8).Build();
  std::unique_ptr<ResourceTable> table =
      test::ResourceTableBuilder()
          .AddSimple("android:drawable/keep1", test::ParseConfigOrDie("v8"))
          .AddSimple("android:drawable/keep1", test::ParseConfigOrDie("en-rGB"))
          .AddSimple("android:drawable/keep1", test::ParseConfigOrDie("fr-rFR"))
          .AddSimple("android:drawable/remove1", test::ParseConfigOrDie("v9"))
          .AddSimple("android:drawable/remove1", test::ParseConfigOrDie("en-rGB"))
          .AddSimple("android:drawable/remove1", test::ParseConfigOrDie("fr-rFR"))
          .Build();

  NoDefaultResourceRemover remover;
  ASSERT_TRUE(remover.Consume(context.get(), table.get()));

  EXPECT_TRUE(table->FindResource(test::ParseNameOrDie("android:drawable/keep1")));
  EXPECT_FALSE(table->FindResource(test::ParseNameOrDie("android:drawable/remove1")));
}

}  // namespace aapt
