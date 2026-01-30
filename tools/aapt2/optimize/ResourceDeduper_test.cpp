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

#include "optimize/ResourceDeduper.h"

#include "ResourceTable.h"
#include "test/Test.h"

using ::aapt::test::HasValue;
using ::android::ConfigDescription;
using ::testing::Not;

namespace aapt {

TEST(ResourceDeduperTest, SameValuesAreDeduped) {
  std::unique_ptr<IAaptContext> context = test::ContextBuilder().Build();
  const ConfigDescription default_config = {};
  const ConfigDescription ldrtl_config = test::ParseConfigOrDie("ldrtl");
  const ConfigDescription ldrtl_v21_config = test::ParseConfigOrDie("ldrtl-v21");
  const ConfigDescription en_config = test::ParseConfigOrDie("en");
  const ConfigDescription en_v21_config = test::ParseConfigOrDie("en-v21");
  // Chosen because this configuration is compatible with ldrtl/en.
  const ConfigDescription land_config = test::ParseConfigOrDie("land");

  std::unique_ptr<ResourceTable> table =
      test::ResourceTableBuilder()
          .AddString("android:string/dedupe", ResourceId{}, default_config, "dedupe")
          .AddString("android:string/dedupe", ResourceId{}, ldrtl_config, "dedupe")
          .AddString("android:string/dedupe", ResourceId{}, land_config, "dedupe")

          .AddString("android:string/dedupe2", ResourceId{}, default_config, "dedupe")
          .AddString("android:string/dedupe2", ResourceId{}, ldrtl_config, "dedupe")
          .AddString("android:string/dedupe2", ResourceId{}, ldrtl_v21_config, "keep")
          .AddString("android:string/dedupe2", ResourceId{}, land_config, "dedupe")

          .AddString("android:string/dedupe3", ResourceId{}, default_config, "dedupe")
          .AddString("android:string/dedupe3", ResourceId{}, en_config, "dedupe")
          .AddString("android:string/dedupe3", ResourceId{}, en_v21_config, "dedupe")
          .Build();

  ASSERT_TRUE(ResourceDeduper().Consume(context.get(), table.get()));
  EXPECT_THAT(table, HasValue("android:string/dedupe", default_config));
  EXPECT_THAT(table, Not(HasValue("android:string/dedupe", ldrtl_config)));
  EXPECT_THAT(table, Not(HasValue("android:string/dedupe", land_config)));

  EXPECT_THAT(table, HasValue("android:string/dedupe2", default_config));
  EXPECT_THAT(table, HasValue("android:string/dedupe2", ldrtl_v21_config));
  EXPECT_THAT(table, Not(HasValue("android:string/dedupe2", ldrtl_config)));

  EXPECT_THAT(table, HasValue("android:string/dedupe3", default_config));
  EXPECT_THAT(table, HasValue("android:string/dedupe3", en_config));
  EXPECT_THAT(table, Not(HasValue("android:string/dedupe3", en_v21_config)));
}

TEST(ResourceDeduperTest, DifferentValuesAreKept) {
  std::unique_ptr<IAaptContext> context = test::ContextBuilder().Build();
  const ConfigDescription default_config = {};
  const ConfigDescription ldrtl_config = test::ParseConfigOrDie("ldrtl");
  const ConfigDescription ldrtl_v21_config = test::ParseConfigOrDie("ldrtl-v21");
  // Chosen because this configuration is compatible with ldrtl.
  const ConfigDescription land_config = test::ParseConfigOrDie("land");

  std::unique_ptr<ResourceTable> table =
      test::ResourceTableBuilder()
          .AddString("android:string/keep", ResourceId{}, default_config, "keep")
          .AddString("android:string/keep", ResourceId{}, ldrtl_config, "keep")
          .AddString("android:string/keep", ResourceId{}, ldrtl_v21_config, "keep2")
          .AddString("android:string/keep", ResourceId{}, land_config, "keep2")
          .Build();

  ASSERT_TRUE(ResourceDeduper().Consume(context.get(), table.get()));
  EXPECT_THAT(table, HasValue("android:string/keep", default_config));
  EXPECT_THAT(table, HasValue("android:string/keep", ldrtl_config));
  EXPECT_THAT(table, HasValue("android:string/keep", ldrtl_v21_config));
  EXPECT_THAT(table, HasValue("android:string/keep", land_config));
}

TEST(ResourceDeduperTest, SameValuesAreDedupedIncompatibleSiblings) {
  std::unique_ptr<IAaptContext> context = test::ContextBuilder().Build();
  const ConfigDescription default_config = {};
  const ConfigDescription ldrtl_config = test::ParseConfigOrDie("ldrtl");
  const ConfigDescription ldrtl_night_config = test::ParseConfigOrDie("ldrtl-night");
  // Chosen because this configuration is not compatible with ldrtl-night.
  const ConfigDescription ldrtl_notnight_config = test::ParseConfigOrDie("ldrtl-notnight");

  std::unique_ptr<ResourceTable> table =
      test::ResourceTableBuilder()
          .AddString("android:string/keep", ResourceId{}, default_config, "keep")
          .AddString("android:string/keep", ResourceId{}, ldrtl_config, "dedupe")
          .AddString("android:string/keep", ResourceId{}, ldrtl_night_config, "dedupe")
          .AddString("android:string/keep", ResourceId{}, ldrtl_notnight_config, "keep2")
          .Build();

  ASSERT_TRUE(ResourceDeduper().Consume(context.get(), table.get()));
  EXPECT_THAT(table, HasValue("android:string/keep", default_config));
  EXPECT_THAT(table, HasValue("android:string/keep", ldrtl_config));
  EXPECT_THAT(table, Not(HasValue("android:string/keep", ldrtl_night_config)));
  EXPECT_THAT(table, HasValue("android:string/keep", ldrtl_notnight_config));
}

TEST(ResourceDeduperTest, SameValuesAreDedupedCompatibleNonSiblings) {
  std::unique_ptr<IAaptContext> context = test::ContextBuilder().Build();
  const ConfigDescription default_config = {};
  const ConfigDescription ldrtl_config = test::ParseConfigOrDie("ldrtl");
  const ConfigDescription ldrtl_night_config = test::ParseConfigOrDie("ldrtl-night");
  // Chosen because this configuration is compatible with ldrtl.
  const ConfigDescription land_config = test::ParseConfigOrDie("land");

  std::unique_ptr<ResourceTable> table =
      test::ResourceTableBuilder()
          .AddString("android:string/keep", ResourceId{}, default_config, "keep")
          .AddString("android:string/keep", ResourceId{}, ldrtl_config, "dedupe")
          .AddString("android:string/keep", ResourceId{}, ldrtl_night_config, "dedupe")
          .AddString("android:string/keep", ResourceId{}, land_config, "keep2")
          .Build();

  ASSERT_TRUE(ResourceDeduper().Consume(context.get(), table.get()));
  EXPECT_THAT(table, HasValue("android:string/keep", default_config));
  EXPECT_THAT(table, HasValue("android:string/keep", ldrtl_config));
  EXPECT_THAT(table, Not(HasValue("android:string/keep", ldrtl_night_config)));
  EXPECT_THAT(table, HasValue("android:string/keep", land_config));
}

TEST(ResourceDeduperTest, LocalesValuesAreKept) {
  std::unique_ptr<IAaptContext> context = test::ContextBuilder().Build();
  const ConfigDescription default_config = {};
  const ConfigDescription fr_config = test::ParseConfigOrDie("fr");
  const ConfigDescription fr_rCA_config = test::ParseConfigOrDie("fr-rCA");

  std::unique_ptr<ResourceTable> table =
      test::ResourceTableBuilder()
          .AddString("android:string/keep", ResourceId{}, default_config, "keep")
          .AddString("android:string/keep", ResourceId{}, fr_config, "keep")
          .AddString("android:string/keep", ResourceId{}, fr_rCA_config, "keep")
          .Build();

  ASSERT_TRUE(ResourceDeduper().Consume(context.get(), table.get()));
  EXPECT_THAT(table, HasValue("android:string/keep", default_config));
  EXPECT_THAT(table, HasValue("android:string/keep", fr_config));
  EXPECT_THAT(table, HasValue("android:string/keep", fr_rCA_config));
}

TEST(ResourceDeduperTest, MccMncValuesAreKept) {
  std::unique_ptr<IAaptContext> context = test::ContextBuilder().Build();
  const ConfigDescription default_config = {};
  const ConfigDescription mcc_config = test::ParseConfigOrDie("mcc262");
  const ConfigDescription mnc_config = test::ParseConfigOrDie("mnc2");

  std::unique_ptr<ResourceTable> table =
      test::ResourceTableBuilder()
          .AddString("android:string/keep", ResourceId{}, default_config, "keep")
          .AddString("android:string/keep", ResourceId{}, mcc_config, "keep")
          .AddString("android:string/keep", ResourceId{}, mnc_config, "keep")
          .Build();

  ASSERT_TRUE(ResourceDeduper().Consume(context.get(), table.get()));
  EXPECT_THAT(table, HasValue("android:string/keep", default_config));
  EXPECT_THAT(table, HasValue("android:string/keep", mcc_config));
  EXPECT_THAT(table, HasValue("android:string/keep", mnc_config));
}

TEST(ResourceDeduperTest, WidthDpHeightDpValuesAreKept) {
  std::unique_ptr<IAaptContext> context = test::ContextBuilder().Build();
  const ConfigDescription default_config = {};
  const ConfigDescription w600dp_config = test::ParseConfigOrDie("w600dp-h900dp");
  const ConfigDescription w840dp_config = test::ParseConfigOrDie("w840dp-h900dp");
  const ConfigDescription h480dp_config = test::ParseConfigOrDie("w840dp-h480dp");
  const ConfigDescription h900dp_config = test::ParseConfigOrDie("w840dp-h900dp");

  std::unique_ptr<ResourceTable> table =
      test::ResourceTableBuilder()
          .AddString("android:string/keep1", ResourceId{}, default_config, "keep")
          .AddString("android:string/keep1", ResourceId{}, w600dp_config, "keep")
          .AddString("android:string/keep1", ResourceId{}, w840dp_config, "keep")
          .AddString("android:string/keep2", ResourceId{}, default_config, "keep")
          .AddString("android:string/keep2", ResourceId{}, h480dp_config, "keep")
          .AddString("android:string/keep2", ResourceId{}, h900dp_config, "keep")
          .Build();

  ASSERT_TRUE(ResourceDeduper().Consume(context.get(), table.get()));
  EXPECT_THAT(table, HasValue("android:string/keep1", default_config));
  EXPECT_THAT(table, HasValue("android:string/keep1", w600dp_config));
  EXPECT_THAT(table, HasValue("android:string/keep1", w840dp_config));
  EXPECT_THAT(table, HasValue("android:string/keep2", default_config));
  EXPECT_THAT(table, HasValue("android:string/keep2", h480dp_config));
  EXPECT_THAT(table, HasValue("android:string/keep2", h900dp_config));
}

TEST(ResourceDeduperTest, WidthDpHeightDpValuesSameAreDeduped) {
  std::unique_ptr<IAaptContext> context = test::ContextBuilder().Build();
  const ConfigDescription default_config = {};
  const ConfigDescription wh_config = test::ParseConfigOrDie("w600dp-h900dp");
  const ConfigDescription wh_port_config = test::ParseConfigOrDie("w600dp-h900dp-port");

  std::unique_ptr<ResourceTable> table =
      test::ResourceTableBuilder()
          .AddString("android:string/keep", ResourceId{}, default_config, "dedupe")
          .AddString("android:string/keep", ResourceId{}, wh_config, "dedupe")
          .AddString("android:string/keep", ResourceId{}, wh_port_config, "dedupe")
          .Build();

  ASSERT_TRUE(ResourceDeduper().Consume(context.get(), table.get()));
  EXPECT_THAT(table, HasValue("android:string/keep", default_config));
  EXPECT_THAT(table, HasValue("android:string/keep", wh_config));
  EXPECT_THAT(table, Not(HasValue("android:string/keep", wh_port_config)));
}

TEST(ResourceDeduperTest, WidthDpXorHeightDpValuesAreDeduped) {
  std::unique_ptr<IAaptContext> context = test::ContextBuilder().Build();
  const ConfigDescription default_config = {};
  const ConfigDescription w1_config = test::ParseConfigOrDie("w600dp");
  const ConfigDescription w2_config = test::ParseConfigOrDie("w800dp");
  const ConfigDescription h1_config = test::ParseConfigOrDie("h600dp");
  const ConfigDescription h2_config = test::ParseConfigOrDie("h800dp");

  std::unique_ptr<ResourceTable> table =
      test::ResourceTableBuilder()
          .AddString("android:string/keep1", ResourceId{}, default_config, "keep")
          .AddString("android:string/keep1", ResourceId{}, w1_config, "dedupe")
          .AddString("android:string/keep1", ResourceId{}, w2_config, "dedupe")
          .AddString("android:string/keep2", ResourceId{}, default_config, "keep")
          .AddString("android:string/keep2", ResourceId{}, h1_config, "dedupe")
          .AddString("android:string/keep2", ResourceId{}, h2_config, "dedupe")
          .Build();

  ASSERT_TRUE(ResourceDeduper().Consume(context.get(), table.get()));
  EXPECT_THAT(table, HasValue("android:string/keep1", default_config));
  EXPECT_THAT(table, HasValue("android:string/keep1", w1_config));
  EXPECT_THAT(table, Not(HasValue("android:string/keep1", w2_config)));
  EXPECT_THAT(table, HasValue("android:string/keep2", default_config));
  EXPECT_THAT(table, HasValue("android:string/keep2", h1_config));
  EXPECT_THAT(table, Not(HasValue("android:string/keep2", h2_config)));
}

TEST(ResourceDeduperTest, SizeDpComplex) {
  std::unique_ptr<IAaptContext> context = test::ContextBuilder().Build();
  const ConfigDescription default_config = {};
  const ConfigDescription config1 = test::ParseConfigOrDie("w600dp");
  const ConfigDescription config2 = test::ParseConfigOrDie("w800dp");
  const ConfigDescription config3 = test::ParseConfigOrDie("w600dp-h600dp");
  const ConfigDescription config4 = test::ParseConfigOrDie("w800dp-h600dp");
  const ConfigDescription config5 = test::ParseConfigOrDie("w800dp-h800dp-port");
  const ConfigDescription config6 = test::ParseConfigOrDie("w600dp-port");
  const ConfigDescription config7 = test::ParseConfigOrDie("w800dp-port");

  std::unique_ptr<ResourceTable> table =
      test::ResourceTableBuilder()
          .AddString("android:string/keep", ResourceId{}, default_config, "keep")
          .AddString("android:string/keep", ResourceId{}, config1, "dedupe")
          .AddString("android:string/keep", ResourceId{}, config2, "dedupe")
          .AddString("android:string/keep", ResourceId{}, config3, "dedupe")
          .AddString("android:string/keep", ResourceId{}, config4, "dedupe")
          .AddString("android:string/keep", ResourceId{}, config5, "dedupe")
          .AddString("android:string/keep", ResourceId{}, config6, "dedupe")
          .AddString("android:string/keep", ResourceId{}, config7, "dedupe")
          .Build();

  ASSERT_TRUE(ResourceDeduper().Consume(context.get(), table.get()));
  EXPECT_THAT(table, HasValue("android:string/keep", default_config));
  EXPECT_THAT(table, HasValue("android:string/keep", config1));
  EXPECT_THAT(table, Not(HasValue("android:string/keep", config2)));
  EXPECT_THAT(table, HasValue("android:string/keep", config3));
  EXPECT_THAT(table, HasValue("android:string/keep", config4));
  EXPECT_THAT(table, HasValue("android:string/keep", config5));
  EXPECT_THAT(table, Not(HasValue("android:string/keep", config6)));
  EXPECT_THAT(table, Not(HasValue("android:string/keep", config7)));
}

}  // namespace aapt
