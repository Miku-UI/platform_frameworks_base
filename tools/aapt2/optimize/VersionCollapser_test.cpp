/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "optimize/VersionCollapser.h"

#include "test/Test.h"

using ::android::ConfigDescription;
using ::android::StringPiece;
using ::testing::IsNull;
using ::testing::NotNull;
using ::testing::StrEq;

namespace aapt {

static std::unique_ptr<ResourceTable> BuildTableWithConfigs(
    StringPiece name, std::initializer_list<std::string> configs) {
  test::ResourceTableBuilder builder;
  for (const std::string& config : configs) {
    // Use the config as the string value, so we can verify the correct config was renamed.
    builder.AddString(name, {}, test::ParseConfigOrDie(config), config);
  }
  return builder.Build();
}

static constexpr auto kResName = "@android:string/foo";

static void ExpectConfigValueIsNull(ResourceTable* table, StringPiece config_str) {
  EXPECT_THAT(test::GetValueForConfig<String>(table, kResName, test::ParseConfigOrDie(config_str)),
              IsNull());
}

static void ExpectConfigValueIs(ResourceTable* table, const ConfigDescription& config,
                                StringPiece expected_value_str) {
  auto value = test::GetValueForConfig<String>(table, kResName, config);
  ASSERT_THAT(value, NotNull());
  EXPECT_THAT(*value->value, StrEq(expected_value_str));
}

static void ExpectConfigValueIs(ResourceTable* table, StringPiece config_str,
                                StringPiece expected_value_str) {
  ExpectConfigValueIs(table, test::ParseConfigOrDie(config_str), expected_value_str);
}

TEST(VersionCollapserTest, CollapseVersions) {
  std::unique_ptr<IAaptContext> context = test::ContextBuilder().SetMinSdkVersion(7).Build();
  std::unique_ptr<ResourceTable> table = BuildTableWithConfigs(
      kResName, {"land-v4", "land-v5", "sw600dp", "land-v6", "land-v14", "land-v21"});
  VersionCollapser collapser;
  ASSERT_TRUE(collapser.Consume(context.get(), table.get()));

  // These should be removed.
  ExpectConfigValueIsNull(table.get(), "land-v4");
  ExpectConfigValueIsNull(table.get(), "land-v5");

  // land-v6 should have been converted to land.
  ExpectConfigValueIsNull(table.get(), "land-v6");
  ExpectConfigValueIs(table.get(), "land", "land-v6");

  // These should remain.
  ExpectConfigValueIs(table.get(), "sw600dp", "sw600dp");
  ExpectConfigValueIs(table.get(), "land-v14", "land-v14");
  ExpectConfigValueIs(table.get(), "land-v21", "land-v21");
}

TEST(VersionCollapserTest, CollapseVersionsWhenMinSdkMatchesConfigVersionExactly) {
  std::unique_ptr<IAaptContext> context = test::ContextBuilder().SetMinSdkVersion(14).Build();
  std::unique_ptr<ResourceTable> table = BuildTableWithConfigs(
      kResName, {"land-v4", "land-v5", "sw600dp", "land-v6", "land-v14", "land-v21", "land-v22"});
  VersionCollapser collapser;
  ASSERT_TRUE(collapser.Consume(context.get(), table.get()));

  // These should all be removed.
  ExpectConfigValueIsNull(table.get(), "land-v4");
  ExpectConfigValueIsNull(table.get(), "land-v5");
  ExpectConfigValueIsNull(table.get(), "land-v6");

  // land-v14 should have been converted to land.
  ExpectConfigValueIsNull(table.get(), "land-v14");
  ExpectConfigValueIs(table.get(), "land", "land-v14");

  // These should remain.
  ExpectConfigValueIs(table.get(), test::ParseConfigOrDie("sw600dp").CopyWithoutSdkVersion(),
                      "sw600dp");
  ExpectConfigValueIs(table.get(), "land-v21", "land-v21");
  ExpectConfigValueIs(table.get(), "land-v22", "land-v22");
}

TEST(VersionCollapserTest, CollapseVersionsWhenMinSdkIsHighest) {
  std::unique_ptr<IAaptContext> context = test::ContextBuilder().SetMinSdkVersion(21).Build();
  std::unique_ptr<ResourceTable> table = BuildTableWithConfigs(
      kResName, {"land-v4", "land-v5", "sw600dp", "land-v6", "land-v14", "land-v21"});
  VersionCollapser collapser;
  ASSERT_TRUE(collapser.Consume(context.get(), table.get()));

  // These should all be removed.
  ExpectConfigValueIsNull(table.get(), "land-v4");
  ExpectConfigValueIsNull(table.get(), "land-v5");
  ExpectConfigValueIsNull(table.get(), "land-v6");
  ExpectConfigValueIsNull(table.get(), "land-v14");

  // land-v21 should have been converted to land.
  ExpectConfigValueIsNull(table.get(), "land-v21");
  ExpectConfigValueIs(table.get(), "land", "land-v21");

  // These should remain.
  ExpectConfigValueIs(table.get(), test::ParseConfigOrDie("sw600dp").CopyWithoutSdkVersion(),
                      "sw600dp");
}

TEST(VersionCollapserTest, CollapseVersionsWithMinorVersion) {
  std::unique_ptr<IAaptContext> context = test::ContextBuilder().SetMinSdkVersion(47).Build();
  std::unique_ptr<ResourceTable> table =
      BuildTableWithConfigs(kResName, {"land-v4", "land-v45.5", "sw600dp", "land-v46", "land-v46.1",
                                       "land-v54.5", "land-v61", "land-v63.2"});
  VersionCollapser collapser;
  ASSERT_TRUE(collapser.Consume(context.get(), table.get()));

  // These should be removed.
  ExpectConfigValueIsNull(table.get(), "land-v4");
  ExpectConfigValueIsNull(table.get(), "land-v45.5");
  ExpectConfigValueIsNull(table.get(), "land-v46");

  // land-v46.1 should have been converted to land.
  ExpectConfigValueIsNull(table.get(), "land-v46.1");
  ExpectConfigValueIs(table.get(), "land", "land-v46.1");

  // These should remain.
  ExpectConfigValueIs(table.get(), test::ParseConfigOrDie("sw600dp").CopyWithoutSdkVersion(),
                      "sw600dp");
  ExpectConfigValueIs(table.get(), "land-v54.5", "land-v54.5");
  ExpectConfigValueIs(table.get(), "land-v61", "land-v61");
  ExpectConfigValueIs(table.get(), "land-v63.2", "land-v63.2");
}

TEST(VersionCollapserTest, CollapseVersionsWithMinorVersionAndMinSdkMatchesConfigVersionExactly) {
  std::unique_ptr<IAaptContext> context = test::ContextBuilder().SetMinSdkVersion(46).Build();
  std::unique_ptr<ResourceTable> table =
      BuildTableWithConfigs(kResName, {"land-v4", "land-v45.5", "sw600dp", "land-v46", "land-v46.1",
                                       "land-v54.5", "land-v61", "land-v63.2"});
  VersionCollapser collapser;
  ASSERT_TRUE(collapser.Consume(context.get(), table.get()));

  // These should be removed.
  ExpectConfigValueIsNull(table.get(), "land-v4");
  ExpectConfigValueIsNull(table.get(), "land-v45.5");

  // land-v46 should have been converted to land.
  ExpectConfigValueIsNull(table.get(), "land-v46");
  ExpectConfigValueIs(table.get(), "land", "land-v46");

  // These should remain.
  ExpectConfigValueIs(table.get(), test::ParseConfigOrDie("sw600dp").CopyWithoutSdkVersion(),
                      "sw600dp");
  ExpectConfigValueIs(table.get(), "land-v46.1", "land-v46.1");
  ExpectConfigValueIs(table.get(), "land-v54.5", "land-v54.5");
  ExpectConfigValueIs(table.get(), "land-v61", "land-v61");
  ExpectConfigValueIs(table.get(), "land-v63.2", "land-v63.2");
}

TEST(VersionCollapserTest, CollapseVersionsWithMinorVersionAndMinSdkIsHighest) {
  std::unique_ptr<IAaptContext> context = test::ContextBuilder().SetMinSdkVersion(64).Build();
  std::unique_ptr<ResourceTable> table =
      BuildTableWithConfigs(kResName, {"land-v4", "land-v45.5", "sw600dp", "land-v46", "land-v46.1",
                                       "land-v54.5", "land-v61", "land-v63.2"});

  VersionCollapser collapser;
  ASSERT_TRUE(collapser.Consume(context.get(), table.get()));

  // These should be removed.
  ExpectConfigValueIsNull(table.get(), "land-v4");
  ExpectConfigValueIsNull(table.get(), "land-v45.5");
  ExpectConfigValueIsNull(table.get(), "land-v46");
  ExpectConfigValueIsNull(table.get(), "land-v46.1");
  ExpectConfigValueIsNull(table.get(), "land-v54.5");
  ExpectConfigValueIsNull(table.get(), "land-v61");

  // land-v63.2 should have been converted to land.
  ExpectConfigValueIsNull(table.get(), "land-v63.2");
  ExpectConfigValueIs(table.get(), "land", "land-v63.2");

  // These should remain.
  ExpectConfigValueIs(table.get(), test::ParseConfigOrDie("sw600dp").CopyWithoutSdkVersion(),
                      "sw600dp");
}

}  // namespace aapt
